package com.himanshoe.docsite

/** One page in the search index, named once so its sections do not each repeat its title. */
data class SearchPage(
    /** The page's site path. */
    val url: String,
    /** Its title, shown above the matching section in a result. */
    val title: String
)

/** One section of one page: the unit a search result points at. */
data class SearchSection(
    /** Position of the owning page in [SearchIndex.pages]. */
    val page: Int,
    /** The section's heading, or the page's title for the section that opens it. */
    val heading: String,
    /** The heading's anchor, empty for the section that opens a page. */
    val anchor: String,
    /** The section's first words, shown under the heading in a result. */
    val snippet: String,
    /** Every distinct word in the section, in the order they first appear, for matching. */
    val words: List<String>
)

/** Everything the browser needs to search the site, and nothing it does not. */
data class SearchIndex(
    /** Every indexed page, in reading order. */
    val pages: List<SearchPage>,
    /** Every indexed section, in reading order. */
    val sections: List<SearchSection>,
    /** The words dropped from [SearchSection.words], so the browser can drop them from a query too. */
    val stopWords: List<String>
)

private const val SNIPPET_LENGTH = 150
private const val MINIMUM_WORD_LENGTH = 2
private const val INDEX_FORMAT_VERSION = 1
private const val CONTROL_CHARACTER_LIMIT = 0x20

private val anchoredHeadingPattern = Regex("""<h([1-6]) id="([^"]+)">([\s\S]*?)</h\1>""")
private val codeRegionPattern = Regex("""<pre[\s\S]*?</pre>|<div class="code-chrome">[\s\S]*?</div>""")

/**
 * Where one piece of text ends and the next begins.
 *
 * [plainText] drops tags without putting anything in their place, which is right for the inline
 * markup it was written for and wrong here: half the reference is parameter tables, and a table whose
 * cells are simply concatenated indexes `selectedDate` and `LocalDate` as the single word
 * `selectedDateLocalDate` — the parameter is then in the index under a name nobody will ever type.
 */
private val blockBoundaryPattern = Regex("""</(?:p|li|td|th|tr|h[1-6]|div|blockquote|dt|dd)>|<br\s*/?>""")
private val wordPattern = Regex("""[A-Za-z0-9_]+""")
private val camelBoundary = Regex("""(?<=[a-z0-9])(?=[A-Z])|_""")

/**
 * Words carried by every page, dropped from the index.
 *
 * They are the bulk of what full text costs and none of what it finds: a query for "the" matching
 * every section is the same as matching nothing. Shipped inside the index rather than written twice,
 * once here and once in the search script — a query still containing "the" while the index no longer
 * does is a query that matches nothing at all, and it is the kind of drift nobody notices until
 * someone types a whole sentence into the box.
 */
private val stopWords: List<String> =
    (
        "a an and any are as at be been being but by can could did do does doing done each for from " +
            "had has have he her here his how if in into is it its may might more most no not of on once " +
            "only or other our own same she should so some such than that the their then there these they " +
            "this those to too very was we were what when where which who whom why will with without would you your"
        ).split(' ')

/**
 * Every section of every page, as the browser will search it.
 *
 * A section rather than a page is the unit, because a documentation page is long and a reader who
 * searched for a term wants the paragraph that uses it rather than the top of the twelve-screen page
 * that mentions it once.
 */
fun buildSearchIndex(
    pages: List<DocSource>,
    rendered: Map<DocSource, RenderedPage>
): SearchIndex {
    val sections = mutableListOf<SearchSection>()
    val indexed =
        pages.mapIndexed { index, source ->
            val page = rendered.getValue(source)
            sections.addAll(sectionsOf(page = page, pageIndex = index))
            SearchPage(url = source.url, title = page.title)
        }
    return SearchIndex(pages = indexed, sections = sections, stopWords = stopWords)
}

/**
 * Splits a rendered page at its headings.
 *
 * The rendered HTML rather than the markdown it came from, so the index holds what a reader can
 * actually see: a page's anchors, its tables and its expanded content are all here, and none of its
 * markup is. Code blocks are cut out first — a snippet is where the words `import`, `val` and
 * `println` live, and indexing them makes every page match a query for any of them.
 */
private fun sectionsOf(
    page: RenderedPage,
    pageIndex: Int
): List<SearchSection> {
    val matches = anchoredHeadingPattern.findAll(page.html).toList()
    if (matches.isEmpty()) {
        return listOf(section(pageIndex = pageIndex, heading = page.title, anchor = "", body = page.html))
    }
    return matches.mapIndexed { index, match ->
        val end = matches.getOrNull(index + 1)?.range?.first ?: page.html.length
        val level = match.groupValues[1].toInt()
        section(
            pageIndex = pageIndex,
            heading = plainText(match.groupValues[3]).removeSuffix("#").trim(),
            // The page's own title is not a section of it: a result for the opening section should
            // land at the top of the page, not at an anchor that scrolls past the first paragraph.
            anchor = if (level == 1) "" else match.groupValues[2],
            body = page.html.substring(match.range.last + 1, end)
        )
    }
}

private fun section(
    pageIndex: Int,
    heading: String,
    anchor: String,
    body: String
): SearchSection {
    val text = plainText(blockBoundaryPattern.replace(codeRegionPattern.replace(body, " "), " "))
    return SearchSection(
        page = pageIndex,
        heading = heading,
        anchor = anchor,
        snippet = snippet(text),
        words = words("$heading $text")
    )
}

private fun snippet(text: String): String =
    if (text.length <= SNIPPET_LENGTH) {
        text
    } else {
        text.take(SNIPPET_LENGTH).substringBeforeLast(' ') + "…"
    }

/**
 * The distinct words of a section, lowercased, in the order they first appear.
 *
 * Distinct because a section that says "event" eleven times is no more about events than one that
 * says it twice — the index answers "does this section talk about X", and the count only inflates
 * the payload. `monthGridDates` is indexed whole *and* as `month`, `grid` and `dates`, so a reader
 * who half-remembers a name can find it by the words in it; that is also what makes a two-word
 * query like "month grid" land on the function rather than on nothing.
 */
private fun words(text: String): List<String> {
    val seen = mutableSetOf<String>()
    val collected = mutableListOf<String>()
    wordPattern.findAll(text).forEach { match ->
        val raw = match.value
        val parts = camelBoundary.split(raw)
        (listOf(raw) + parts).forEach { part ->
            val word = part.lowercase()
            if (word.length >= MINIMUM_WORD_LENGTH && word !in stopWords && seen.add(word)) {
                collected.add(word)
            }
        }
    }
    return collected
}

/**
 * The index as the JSON the site ships.
 *
 * Positional arrays rather than named objects: the shape is read by exactly one script, which is
 * generated from the same build, and repeating five keys across every section of the site costs more
 * than the whole stop-word list saves. `v` is the format version, so a stale cached index served to
 * a new script can be recognised rather than misread.
 */
fun SearchIndex.toJson(): String =
    buildString {
        append("""{"v":$INDEX_FORMAT_VERSION,"p":[""")
        pages.forEachIndexed { index, page ->
            if (index > 0) {
                append(',')
            }
            append('[').append(quote(page.url)).append(',').append(quote(page.title)).append(']')
        }
        append("""],"s":[""")
        sections.forEachIndexed { index, item ->
            if (index > 0) {
                append(',')
            }
            append('[').append(item.page).append(',')
            append(quote(item.heading)).append(',')
            append(quote(item.anchor)).append(',')
            append(quote(item.snippet)).append(',')
            append(quote(item.words.joinToString(" ")))
            append(']')
        }
        append("""],"x":""").append(quote(stopWords.joinToString(" ")))
        append('}')
    }

private fun quote(value: String): String =
    buildString {
        append('"')
        value.forEach { character ->
            when {
                character == '"' -> append("\\\"")
                character == '\\' -> append("\\\\")
                character == '\n' -> append("\\n")
                character.code < CONTROL_CHARACTER_LIMIT -> append("\\u%04x".format(character.code))
                else -> append(character)
            }
        }
        append('"')
    }
