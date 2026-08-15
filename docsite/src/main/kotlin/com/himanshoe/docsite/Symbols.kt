package com.himanshoe.docsite

import java.io.File

/** Where a public symbol is documented: a reference page, and the heading on it. */
data class SymbolTarget(
    /** The reference page's site path. */
    val url: String,
    /** The anchor of the heading that documents the symbol. */
    val anchor: String
)

private const val API_DIRECTORY = "api"
private const val API_EXTENSION = "api"
private const val FACADE_SUFFIX = "Kt"
private const val GETTER_PREFIX = "get"
private const val GENERATED_PREFIX = "ComposableSingletons"

/**
 * Nested names the compiler puts in a dump that nobody writes in prose. `Companion` and
 * `DefaultImpls` are the two that would otherwise become linkable symbols called after a Kotlin
 * keyword rather than after anything in this library.
 */
private val generatedNesting = setOf("Companion", "DefaultImpls", "WhenMappings", "Entries")

private val declarationPattern = Regex("""^public\s+[\w ]*class\s+([\w/$]+)""")
private val facadeMemberPattern = Regex("""^\s+public\s+static\s+final\s+(?:fun|field)\s+([\w$\-]+)""")
private val symbolNamePattern = Regex("""^[A-Za-z_][A-Za-z0-9_]*(?:\.[A-Za-z_][A-Za-z0-9_]*)*$""")

/**
 * Regions of a rendered page that must survive untouched, and the inline code spans everywhere else.
 *
 * One pattern rather than a pass per rule, because the rules are about *where* a match is, and a
 * regex that has already consumed a `<pre>` cannot match inside it. Order matters: the protected
 * alternatives come first, so at any position a code block, an existing link or a heading wins over
 * the code span that would otherwise be rewritten inside it.
 */
private val linkablePattern =
    Regex("""<pre[\s\S]*?</pre>|<a\b[\s\S]*?</a>|<h[1-6]\b[\s\S]*?</h[1-6]>|<code>([A-Za-z_][\w.]*)</code>""")

/**
 * Every public symbol name the library actually ships, read from the binary-compatibility dumps.
 *
 * The dumps rather than the reference prose, because the dumps are what `apiDump` regenerates and
 * what `apiCheck` fails on: a symbol that is renamed or removed leaves this set in the same commit
 * that renames it, and the auto-linker stops linking it. Deriving the list from the documentation
 * instead would let a link outlive the thing it points at, which is the one failure this is here to
 * prevent.
 *
 * Both platform dumps are read and unioned. They are identical apart from compiler-generated names,
 * and reading one would make the symbol list depend on which platform happened to be dumped last.
 *
 * Class members are deliberately absent. A dump lists every `getFoo`, `equals` and `copy`, and a
 * reference page has no per-member entry to point at — the names worth linking are the top-level
 * ones a reader writes in prose.
 */
fun readApiSymbols(rootDir: File): Set<String> {
    val dumps =
        rootDir
            .listFiles()
            .orEmpty()
            .map { module -> module.resolve(API_DIRECTORY) }
            .filter { directory -> directory.isDirectory }
            .flatMap { directory -> directory.walkTopDown().filter { file -> file.extension == API_EXTENSION } }
    require(dumps.isNotEmpty()) { "No api/*.api dumps under ${rootDir.absolutePath}; run ./gradlew apiDump" }
    return dumps.flatMapTo(mutableSetOf()) { dump -> symbolsIn(dump.readText()) }
}

private fun symbolsIn(dump: String): Set<String> {
    val symbols = mutableSetOf<String>()
    var facade = false
    dump.lineSequence().forEach { line ->
        val declared = declarationPattern.find(line)?.groupValues?.get(1)
        when {
            declared != null -> {
                val name = declared.substringAfterLast('/')
                facade = name.endsWith(FACADE_SUFFIX)
                symbols.addAll(classSymbols(name))
            }

            facade -> facadeMemberPattern.find(line)?.groupValues?.get(1)?.let { member ->
                symbols.addAll(memberSymbols(member))
            }
        }
    }
    return symbols
}

/**
 * The names a declared class can be written as: its own, and — for a nested one — its outer name
 * qualifying it, which is how the reference titles `KalendarSelectionMode.Range`.
 *
 * A file facade is not a symbol: nobody writes `KalendarDateArithmeticKt`, and its members are
 * collected separately as the top-level declarations they came from.
 */
private fun classSymbols(name: String): Set<String> {
    if (name.endsWith(FACADE_SUFFIX) || name.startsWith(GENERATED_PREFIX)) {
        return emptySet()
    }
    val segments = name.split('$')
    val simple = segments.last()
    if (simple in generatedNesting || simple.toIntOrNull() != null) {
        return emptySet()
    }
    return setOf(simple, segments.joinToString("."))
}

/**
 * The names one facade member can be written as.
 *
 * Two things in a dump are not the name anyone types. A Compose function that takes an inline class
 * carries a mangling suffix — `KalendarSchedule-neCDxuE` — and a top-level `val` is a static getter,
 * so `ALL_DAYS_OF_WEEK` arrives as `getALL_DAYS_OF_WEEK`. Both candidates are kept rather than one
 * being chosen: `getOrNull` is a real function whose name merely starts with `get`, and guessing
 * between the two readings would either lose it or invent `OrNull`. The reference decides — only a
 * name a reference heading actually documents is ever linked.
 */
private fun memberSymbols(member: String): Set<String> {
    val name = member.substringBefore('-')
    if (name.contains('$')) {
        return emptySet()
    }
    val property = name.removePrefix(GETTER_PREFIX)
    return if (name.startsWith(GETTER_PREFIX) && property.firstOrNull()?.isUpperCase() == true) {
        setOf(name, property)
    } else {
        setOf(name)
    }
}

/**
 * Which reference entry each public symbol has, for the auto-linker.
 *
 * The two halves come from different places on purpose. [apiSymbols] says what exists — it is
 * regenerated from the compiled ABI. The reference headings say what is documented. A name is
 * linkable only when both agree, so a heading for a symbol that has been deleted stops being a link
 * target, and a symbol nobody has written an entry for is left as plain code rather than linked to
 * a page that does not describe it.
 *
 * Both the entry's own title and its last dotted segment are registered, so `LocalDate.startOfWeek`
 * is reachable from prose that writes the receiver and from prose that does not. A name two entries
 * both claim is dropped: there is no way to guess which one a reader meant, and picking one at
 * random is worse than leaving the code span alone.
 */
fun buildSymbolIndex(
    pages: Map<DocSource, RenderedPage>,
    apiSymbols: Set<String>
): Map<String, SymbolTarget> {
    val claimed = mutableMapOf<String, MutableSet<SymbolTarget>>()
    pages
        .filterKeys { source -> source.directory == REFERENCE_DIRECTORY }
        .forEach { (source, page) ->
            page.headings
                .filter { heading -> symbolNamePattern.matches(heading.text) }
                .forEach { heading ->
                    val target = SymbolTarget(url = source.url, anchor = heading.id)
                    val name = heading.text
                    claimed.getOrPut(name) { mutableSetOf() }.add(target)
                    claimed.getOrPut(name.substringAfterLast('.')) { mutableSetOf() }.add(target)
                }
        }
    return claimed
        .filterKeys { name -> name.substringAfterLast('.') in apiSymbols }
        .mapNotNull { (name, targets) -> targets.singleOrNull()?.let { target -> name to target } }
        .toMap()
}

/**
 * Turns every mention of a documented symbol into a link to its reference entry.
 *
 * Only an inline code span is rewritten, and only when its whole text is a symbol name. A prose
 * mention of a Kotlin declaration is written in backticks by anyone writing these docs, so matching
 * on that costs nothing and rules out the alternative — matching bare words — which would linkify
 * the word "Header" in a sentence about page headers.
 *
 * Three places are left alone. Inside a code block, because a snippet where a third of the
 * identifiers are underlined is a snippet nobody can read. Inside an existing link, because the
 * author already chose where that mention goes. And inside a heading, where the only link is the
 * anchor to the section itself. A symbol is not linked to the page it is already on either: a link
 * that scrolls a reader somewhere they can see is a link that reads as broken.
 */
fun linkifySymbols(
    html: String,
    currentUrl: String,
    depth: Int,
    symbols: Map<String, SymbolTarget>
): String {
    if (symbols.isEmpty()) {
        return html
    }
    val root = rootPrefix(depth)
    return linkablePattern.replace(html) { match ->
        val name = match.groupValues[1]
        val target = symbols[name]
        if (name.isEmpty() || target == null || target.url == currentUrl) {
            match.value
        } else {
            """<code><a class="api-link" href="$root${target.url}#${target.anchor}">$name</a></code>"""
        }
    }
}
