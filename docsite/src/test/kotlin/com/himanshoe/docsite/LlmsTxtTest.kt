package com.himanshoe.docsite

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * `llms.txt` exists to stop an assistant answering from a training set that predates 2.0.0. That only
 * works while it is complete and absolute: a page missing from it is a page an assistant will not
 * find, and a relative link in it is a link that resolves against wherever the file was fetched from.
 */
class LlmsTxtTest {
    private val pages =
        listOf(
            source("getting-started.md", "# Getting started\n\nAdd the dependency.\n"),
            source("views/month.md", "# Month\n\nA month grid.\n")
        )
    private val text =
        renderLlmsTxt(
            sections = buildNavigation(pages),
            rendered = pages.associateWith { page -> renderMarkdown(page.markdown) },
            version = LIBRARY_VERSION
        )

    @Test
    fun `every page in the navigation is listed`() {
        pages.forEach { page ->
            assertTrue(text.contains("$SITE_BASE_URL${page.url}"), "${page.url} is missing from llms.txt")
        }
    }

    @Test
    fun `a page is listed under its own title and opening line`() {
        assertTrue(text.contains("[Getting started]"))
        assertTrue(text.contains("Add the dependency."))
        assertTrue(text.contains("[Month]"))
    }

    @Test
    fun `the sections are the ones the sidebar shows`() {
        assertTrue(text.contains("## Start here"))
        assertTrue(text.contains("## Views"))
    }

    /** A link that only works when the file is read from the site root is a link that mostly does not. */
    @Test
    fun `every documentation link is absolute`() {
        val links = Regex("""\]\(([^)]+)\)""").findAll(text).map { match -> match.groupValues[1] }

        assertTrue(links.all { link -> link.startsWith("https://") }, "llms.txt has a relative link")
    }

    @Test
    fun `it names the version and the artifacts rather than leaving them to be guessed`() {
        assertTrue(text.contains(LIBRARY_VERSION))
        assertTrue(text.contains("kalendar-foundation"))
        assertTrue(text.contains("kalendar-sync"))
    }

    /** The one fact an assistant is most likely to get wrong, stated where it cannot be missed. */
    @Test
    fun `it says the 1x package no longer exists`() {
        assertTrue(text.contains("com.himanshoe.kalendar.view"))
    }

    /**
     * Every reference page opens with its package name on a line of its own, which is a heading in
     * all but name — describing the page as "com.himanshoe.kalendar.foundation.grid" tells a reader,
     * a search engine and an assistant nothing they did not already have from the link.
     */
    @Test
    fun `a page whose first line is only its package name is described by the sentence after it`() {
        val markdown = "# `foundation.grid`\n\n`com.x.grid`\n\nWhich dates a grid draws.\n"

        assertEquals("Which dates a grid draws.", renderMarkdown(markdown).summary)
    }

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
