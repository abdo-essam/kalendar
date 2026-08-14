package com.himanshoe.docsite

/** Where a page sits in the site, used to build its links back out to everything else. */
data class PageContext(
    /** The page's path on the site, relative to its root. */
    val url: String,
    /** How many directories deep it sits, for building relative links back to the root. */
    val depth: Int,
    /**
     * The first page of the documentation proper, which the top bar's "Docs" link points at.
     *
     * Taken from the navigation rather than named here, so the link cannot outlive the page it
     * refers to when the docs are reorganised.
     */
    val entryUrl: String
)

/** `../` repeated enough times to climb back to the site root from [depth]. */
fun rootPrefix(depth: Int): String = "../".repeat(depth)

/** The site path a markdown file is published at. */
fun String.toSiteUrl(): String = removeSuffix(".md") + ".html"

/** How deep in the site a path sits, for building its relative links back to the root. */
fun String.urlDepth(): Int = count { character -> character == '/' }

/** HTML-escapes text destined for an element's content. */
fun escapeText(value: String): String =
    value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")

/** HTML-escapes text destined for an attribute value, where a quote would end the attribute. */
fun escapeAttribute(value: String): String = escapeText(value).replace("\"", "&quot;")
