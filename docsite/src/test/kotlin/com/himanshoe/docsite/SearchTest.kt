package com.himanshoe.docsite

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The search index is the one part of the site a reader never sees and always uses. What it contains
 * decides what can be found at all, so the rules that shape it — sections not pages, prose not code,
 * words split the way a reader half-remembers them — are pinned down here rather than left to be
 * discovered by someone typing a query that ought to have worked.
 */
class SearchTest {
    @Test
    fun `a page is indexed one section per heading`() {
        val index = indexOf("# Grid\n\nDates a grid draws.\n\n## Rotation\n\nRotate then filter.\n")

        assertEquals(listOf("Grid", "Rotation"), index.sections.map { section -> section.heading })
        assertEquals(listOf(0, 0), index.sections.map { section -> section.page })
    }

    /** The opening section is the page. A result for it should land at the top, not past the lead. */
    @Test
    fun `the section opening a page has no anchor and the rest carry theirs`() {
        val index = indexOf("# Grid\n\nText.\n\n## Rotation order\n\nMore.\n")

        assertEquals("", index.sections.first().anchor)
        assertEquals("rotation-order", index.sections.last().anchor)
    }

    @Test
    fun `a section is found by the prose under its heading`() {
        val index = indexOf("# Grid\n\n## Rotation\n\nThe columns stay under their labels.\n")

        assertTrue("columns" in words(index, "Rotation"))
        assertTrue("labels" in words(index, "Rotation"))
    }

    /**
     * Every snippet in these docs imports something and declares a `val`. Indexing that makes every
     * page match a query for either, which is the same as no page matching anything.
     */
    @Test
    fun `code blocks are not indexed`() {
        val index = indexOf("# Grid\n\nProse.\n\n```kotlin\nimport com.himanshoe.kalendar.grid.unrelatedToken\n```\n")

        assertTrue("prose" in words(index, "Grid"))
        assertFalse("unrelatedtoken" in words(index, "Grid"))
        assertFalse("import" in words(index, "Grid"))
    }

    /**
     * A reader looking for `monthGridDates` types "month grid" as often as they type the name. The
     * words of an identifier are indexed beside the whole of it so both work.
     */
    @Test
    fun `an identifier is indexed whole and in its parts`() {
        val index = indexOf("# Grid\n\nCall `monthGridDates` for a page.\n")
        val found = words(index, "Grid")

        assertTrue("monthgriddates" in found)
        assertTrue("month" in found)
        assertTrue("grid" in found)
        assertTrue("dates" in found)
    }

    @Test
    fun `a word is stored once however often a section says it`() {
        val index = indexOf("# Grid\n\nGrid grid grid, and a grid.\n")

        assertEquals(1, words(index, "Grid").count { word -> word == "grid" })
    }

    /**
     * Stop words are dropped from the index and shipped with it, because the script has to drop them
     * from a query too — an index without "the" and a query still requiring it finds nothing at all.
     */
    @Test
    fun `stop words are dropped from the index and named in it`() {
        val index = indexOf("# Grid\n\nThe dates that the grid draws.\n")

        assertFalse("the" in words(index, "Grid"))
        assertTrue("dates" in words(index, "Grid"))
        assertTrue("the" in index.stopWords)
        assertTrue(index.toJson().contains(""""x":""""))
    }

    /**
     * Half the reference is parameter tables. Without a boundary between cells they run together into
     * one word — `selectedDate` and `LocalDate` become `selectedDateLocalDate`, and the parameter a
     * reader searched for by name is in the index under a name nobody will ever type.
     */
    @Test
    fun `cells and list items are words apart, not one long word`() {
        val table = "# Grid\n\n| Parameter | Type |\n|---|---|\n| `selectedDate` | `LocalDate` |\n"
        val index = indexOf(table)

        assertTrue("selecteddate" in words(index, "Grid"))
        assertTrue("localdate" in words(index, "Grid"))
        assertFalse("selecteddatelocaldate" in words(index, "Grid"))
        assertTrue(index.sections.single().snippet.contains("Parameter Type"))
    }

    @Test
    fun `a section carries the opening prose as its snippet`() {
        val index = indexOf("# Grid\n\nWhich dates a calendar grid draws.\n")

        assertEquals("Which dates a calendar grid draws.", index.sections.single().snippet)
    }

    @Test
    fun `the index names each page once and its sections point back at it`() {
        val index = indexOf(listOf(source("a.md", "# A\n\n## One\n"), source("b.md", "# B\n")))

        assertEquals(listOf("a.html", "b.html"), index.pages.map { page -> page.url })
        assertEquals(listOf(0, 0, 1), index.sections.map { section -> section.page })
    }

    /** The JSON is parsed by a browser, so a quote or a backslash in the docs may not end a string. */
    @Test
    fun `text is escaped for the JSON the browser parses`() {
        val index = indexOf("""# Grid${"\n"}${"\n"}A "quoted" word and a \ backslash.${"\n"}""")

        assertTrue(index.toJson().contains("""\"quoted\""""))
        assertTrue(index.toJson().contains("""\\ backslash"""))
    }

    private fun indexOf(markdown: String): SearchIndex = indexOf(listOf(source("grid.md", markdown)))

    private fun indexOf(pages: List<DocSource>): SearchIndex =
        buildSearchIndex(pages = pages, rendered = pages.associateWith { page -> renderMarkdown(page.markdown) })

    private fun words(
        index: SearchIndex,
        heading: String
    ): List<String> = index.sections.first { section -> section.heading == heading }.words

    private fun source(
        path: String,
        markdown: String
    ): DocSource =
        DocSource(
            path = path,
            url = path.toSiteUrl(),
            directory = path.substringBeforeLast('/', ""),
            markdown = markdown,
            title = path
        )
}
