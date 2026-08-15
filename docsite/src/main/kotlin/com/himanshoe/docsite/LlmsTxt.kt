package com.himanshoe.docsite

/**
 * The site's machine-readable index, written to `/llms.txt`.
 *
 * An assistant asked about Kalendar answers from whatever it absorbed at training time, which for a
 * library that has just renamed every package is a confident answer about an API that no longer
 * exists. This file is the cheapest possible correction: one fetch, at a conventional path, listing
 * what the library is and where each page of the current documentation lives.
 *
 * Everything here is generated from the same navigation the sidebar is built from, so a page cannot
 * be added to the docs and left out of this, and the one-line description of each page is the page's
 * own opening sentence rather than a second summary written here that would drift from it.
 *
 * Short on purpose. The point is to route a reader to the right page, not to be the documentation:
 * a long file is one that gets truncated, and the part that survives truncation should be the map.
 */
fun renderLlmsTxt(
    sections: List<NavSection>,
    rendered: Map<DocSource, RenderedPage>,
    version: String
): String =
    buildString {
        appendLine("# $SITE_NAME")
        appendLine()
        appendLine("> $SITE_TAGLINE")
        appendLine()
        appendLine(
            "$SITE_NAME $version is published to Maven Central as `com.himanshoe:kalendar`. It ships three " +
                "artifacts: `kalendar`, the Compose UI; `kalendar-foundation`, a headless calendar engine " +
                "with no Compose UI dependency that can be used on its own; and `kalendar-sync`, device " +
                "calendar access, recurrence expansion and iCal, with no Compose dependency at all."
        )
        appendLine()
        appendLine(
            "This file indexes the current documentation. Anything an older source says about the " +
                "package `com.himanshoe.kalendar.view` describes 1.x and no longer applies: in 2.0.0 the " +
                "views moved to `com.himanshoe.kalendar` and the engine to `com.himanshoe.kalendar.foundation.*`."
        )
        appendLine()
        sections.forEach { section ->
            appendLine("## ${section.title}")
            appendLine()
            section.entries.forEach { entry ->
                appendLine(entryLine(entry = entry, rendered = rendered))
            }
            appendLine()
        }
        appendLine("## Optional")
        appendLine()
        appendLine(
            "- [Generated KDoc — kalendar]($SITE_BASE_URL$KDOC_CALENDAR_URL): every signature in the UI artifact."
        )
        appendLine(
            "- [Generated KDoc — kalendar-foundation]($SITE_BASE_URL$KDOC_ENGINE_URL): every signature in the engine."
        )
        appendLine("- [Playground]($SITE_BASE_URL$PLAYGROUND_URL): the calendars running in the browser.")
        appendLine("- [Source]($REPO_URL): issues, changelog and the modules themselves.")
        appendLine(
            "- [Search index]($SITE_BASE_URL$SEARCH_INDEX_PATH): every section of this site as JSON — " +
                "`p` is the pages, `s` is `[page, heading, anchor, snippet, words]` per section."
        )
    }

private fun entryLine(
    entry: DocSource,
    rendered: Map<DocSource, RenderedPage>
): String {
    val page = rendered[entry]
    val summary = page?.summary.orEmpty()
    val title = page?.title ?: entry.title
    val description =
        if (summary.isBlank()) {
            ""
        } else {
            ": $summary"
        }
    return "- [$title]($SITE_BASE_URL${entry.url})$description"
}
