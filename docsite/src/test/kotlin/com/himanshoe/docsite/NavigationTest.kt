package com.himanshoe.docsite

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The sidebar is assembled from the files that exist, ordered by what [Catalog] declares. That is a
 * deliberate split, and it has one failure mode worth pinning down: a page that nobody declared, or
 * a declaration for a page nobody has written yet. Both happen constantly while docs are being
 * written, and neither may take the site down or hide a page.
 */
class NavigationTest {
    @Test
    fun `a page keeps its declared section whether it is a file or a directory`() {
        val fromFile = buildNavigation(listOf(page("engine.md")))
        val fromDirectory = buildNavigation(listOf(page("engine/index.md")))

        assertEquals(listOf("engine"), fromFile.map { section -> section.key })
        assertEquals(listOf("engine"), fromDirectory.map { section -> section.key })
        assertEquals("Build your own", fromFile.single().title)
    }

    /**
     * The engine's documentation may arrive as one page or as a directory of them, and while it is
     * being written it can briefly be both. One section either way, never two under one heading.
     */
    @Test
    fun `a file and a directory declared under one key share a section`() {
        val sections = buildNavigation(listOf(page("engine/paging.md"), page("engine.md")))

        assertEquals(1, sections.size)
        assertEquals(
            listOf("engine.md", "engine/paging.md"),
            sections.single().entries.map { page -> page.path }
        )
    }

    @Test
    fun `an undeclared page is visible rather than lost`() {
        val sections = buildNavigation(listOf(page("troubleshooting.md")))

        assertEquals(DEFAULT_ROOT_SECTION, sections.single().key)
        assertEquals(listOf("troubleshooting.md"), sections.single().entries.map { page -> page.path })
    }

    /** A section is its pages. Declaring one whose pages do not exist yet must not print a heading. */
    @Test
    fun `a declared section with no pages does not appear`() {
        val sections = buildNavigation(listOf(page("getting-started.md")))

        assertEquals(listOf("start"), sections.map { section -> section.key })
    }

    @Test
    fun `sections and their pages follow the declared order`() {
        val pages =
            listOf(
                page("changelog.md"),
                page("views/month.md"),
                page("views/index.md"),
                page("configuration.md"),
                page("engine.md"),
                page("getting-started.md")
            )

        val order = readingOrder(buildNavigation(pages)).map { page -> page.path }

        assertEquals(
            listOf(
                "getting-started.md",
                "configuration.md",
                "views/index.md",
                "views/month.md",
                "engine.md",
                "changelog.md"
            ),
            order
        )
    }

    /** The landing page's tiles are chosen by section key, so they cannot be emptied by a rename. */
    @Test
    fun `the home page tiles name sections that are declared`() {
        assertTrue(HOME_TILE_SECTIONS.all { key -> key in sectionOrder })
    }

    @Test
    fun `every declared root section is a section the sidebar knows how to order`() {
        assertTrue(rootSections.values.all { key -> key in sectionOrder })
        assertTrue(DEFAULT_ROOT_SECTION in sectionOrder)
    }

    private fun page(path: String): DocSource =
        DocSource(
            path = path,
            url = path.toSiteUrl(),
            directory = path.substringBeforeLast('/', ""),
            markdown = "",
            title = path
        )
}
