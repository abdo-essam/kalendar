package com.himanshoe.docsite

import java.io.File

/** One markdown file found under `docs/`, with everything the nav needs to place it. */
data class DocSource(
    /** Path to the markdown file, relative to the docs root, with `/` separators. */
    val path: String,
    /** Where the rendered page lands on the site. */
    val url: String,
    /** The directory the page sits in, or the empty string for the top level. */
    val directory: String,
    /** The file's contents. */
    val markdown: String,
    /** The page's own first-level heading, which is also what the sidebar calls it. */
    val title: String
)

/** A titled group of pages in the sidebar. */
data class NavSection(
    /** The group heading shown in the sidebar. */
    val title: String,
    /** The pages in this group, in reading order. */
    val entries: List<DocSource>
)

private const val MAX_NESTING = 1
private val titlePattern = Regex("""^#\s+(.+)$""", RegexOption.MULTILINE)

/**
 * Finds every page under [docsDir].
 *
 * Discovered rather than declared, so a page cannot be written and then be invisible because nobody
 * remembered to add it to a list. The cost is that the order has to come from somewhere, which is
 * what [pageOrder] is for.
 */
fun discoverPages(docsDir: File): List<DocSource> {
    require(docsDir.isDirectory) { "No docs directory at ${docsDir.absolutePath}" }
    val pages =
        docsDir
            .walkTopDown()
            .filter { file -> file.isFile && file.extension == "md" }
            .map { file -> toDocSource(docsDir = docsDir, file = file) }
            .toList()
    require(pages.any { page -> page.path == "index.md" }) {
        "docs/index.md is missing — it is the site's landing page"
    }
    return pages
}

private fun toDocSource(
    docsDir: File,
    file: File
): DocSource {
    val path = file.relativeTo(docsDir).invariantSeparatorsPath
    require(path.count { character -> character == '/' } <= MAX_NESTING) {
        "$path nests deeper than the sidebar can show; keep docs at most one directory deep"
    }
    val markdown = file.readText()
    val heading = titlePattern.find(markdown)?.groupValues?.get(1)?.trim()
    return DocSource(
        path = path,
        url = path.toSiteUrl(),
        directory = path.substringBeforeLast('/', ""),
        markdown = markdown,
        title = heading ?: path.substringAfterLast('/').removeSuffix(".md")
    )
}

/**
 * Groups the discovered pages into the sidebar: a directory is a section, a file is a page.
 *
 * `docs/index.md` is left out — it is the landing page, and the sidebar links to it separately as
 * the way home rather than as one entry among the guides.
 */
fun buildNavigation(pages: List<DocSource>): List<NavSection> {
    val grouped = pages.filterNot { page -> page.path == "index.md" }.groupBy { page -> page.directory }
    val root =
        grouped[""]
            ?.sortedWith(pageComparator)
            ?.let { entries -> listOf(NavSection(title = ROOT_SECTION_TITLE, entries = entries)) }
            .orEmpty()
    val sections =
        grouped
            .filterKeys { directory -> directory.isNotEmpty() }
            .toList()
            .sortedWith(sectionComparator)
            .map { (directory, entries) ->
                NavSection(title = sectionTitle(directory), entries = entries.sortedWith(pageComparator))
            }
    return root + sections
}

/** Every page in sidebar order, which is also the order the previous/next links walk. */
fun readingOrder(sections: List<NavSection>): List<DocSource> = sections.flatMap { section -> section.entries }

private val sectionComparator =
    compareBy<Pair<String, List<DocSource>>>(
        { (directory, _) -> orderIndex(sectionOrder, directory) },
        { (directory, _) -> directory }
    )

private val pageComparator =
    compareBy<DocSource>(
        { page -> if (page.path.substringAfterLast('/') == "index.md") 0 else 1 },
        { page -> orderIndex(pageOrder, page.path) },
        { page -> page.path }
    )

private fun orderIndex(
    order: List<String>,
    value: String
): Int = order.indexOf(value).takeIf { index -> index >= 0 } ?: order.size

/** A directory's sidebar heading: the declared one, or the directory name made readable. */
private fun sectionTitle(directory: String): String =
    sectionTitles[directory]
        ?: directory
            .split('-', '_')
            .joinToString(" ") { word -> word.replaceFirstChar { character -> character.uppercaseChar() } }
