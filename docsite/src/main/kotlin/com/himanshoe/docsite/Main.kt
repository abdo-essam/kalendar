package com.himanshoe.docsite

import java.io.File

private const val EXPECTED_ARGUMENTS = 3
private const val BYTES_PER_KILOBYTE = 1024
private val assets = listOf("docs.css", "docs.js")

/**
 * Renders the documentation into a static site.
 *
 * Usage: `generateDocsSite <docsDir> <outputDir> <rootDir>`, where `rootDir` is the repository root
 * holding each module's `api` directory of binary-compatibility dumps — the symbol list the
 * reference auto-linker is built from.
 */
fun main(args: Array<String>) {
    require(args.size == EXPECTED_ARGUMENTS) {
        "Expected <docsDir> <outputDir> <rootDir>, got ${args.size} argument(s)"
    }
    val docsDir = File(args[0])
    val outputDir = File(args[1])
    val rootDir = File(args[2])

    outputDir.deleteRecursively()
    outputDir.mkdirs()

    val sources = discoverPages(docsDir)
    val sections = buildNavigation(sources)
    val order = readingOrder(sections)
    val parsed = sources.associateWith { source -> renderMarkdown(source.markdown) }
    val symbols = buildSymbolIndex(pages = parsed, apiSymbols = readApiSymbols(rootDir))
    val rendered = parsed.mapValues { (source, page) -> page.withSymbolLinks(source = source, symbols = symbols) }
    val entryUrl = order.firstOrNull()?.url ?: "index.html"

    order.forEachIndexed { index, source ->
        writeDocPage(
            outputDir = outputDir,
            docsDir = docsDir,
            source = source,
            page = rendered.getValue(source),
            sections = sections,
            neighbours = order.getOrNull(index - 1) to order.getOrNull(index + 1),
            entryUrl = entryUrl
        )
    }

    val home = sources.first { source -> source.path == "index.md" }
    writeHome(
        outputDir = outputDir,
        docsDir = docsDir,
        page = rendered.getValue(home),
        sections = sections,
        rendered = rendered,
        entryUrl = entryUrl
    )
    writeNotFound(outputDir = outputDir, entryUrl = entryUrl)
    writeSitemap(outputDir = outputDir, urls = listOf("index.html") + order.map { source -> source.url })
    writeSearchIndex(outputDir = outputDir, pages = listOf(home) + order, rendered = rendered)
    outputDir
        .resolve("llms.txt")
        .writeText(renderLlmsTxt(sections = sections, rendered = rendered, version = LIBRARY_VERSION))
    copyStaticFiles(docsDir = docsDir, outputDir = outputDir)
    copyAssets(outputDir)

    /*
     * Strict by default: a dead link stops the build rather than reaching a reader. `-Pdocsite.links=warn`
     * downgrades it to a report, which is what you want while the docs are half written and a link
     * legitimately points at a page nobody has typed yet.
     */
    verifyLinks(outputDir = outputDir, strict = System.getProperty("docsite.links") != "warn")

    println("Wrote ${order.size + 1} pages to ${outputDir.absolutePath}")
    println("Auto-linked ${symbols.size} public symbols to the API reference")
}

/**
 * The page with every mention of a documented public symbol turned into a link to its entry.
 *
 * Applied to the rendered HTML rather than to the markdown, because the rules are about where a
 * mention sits — in a code block, inside an existing link, in a heading — and none of those places
 * is distinguishable before the markdown has been rendered.
 */
private fun RenderedPage.withSymbolLinks(
    source: DocSource,
    symbols: Map<String, SymbolTarget>
): RenderedPage =
    copy(
        html =
            linkifySymbols(
                html = html,
                currentUrl = source.url,
                depth = source.url.urlDepth(),
                symbols = symbols
            )
    )

/**
 * Writes the index the search box reads, and says how big it is.
 *
 * The size is printed on every build because it is the number that decides whether search stays
 * honest: it grows with the docs, silently, and a payload nobody has looked at since it was written
 * is how a documentation site ends up shipping a megabyte to answer a two-word query.
 */
private fun writeSearchIndex(
    outputDir: File,
    pages: List<DocSource>,
    rendered: Map<DocSource, RenderedPage>
) {
    val index = buildSearchIndex(pages = pages, rendered = rendered)
    val json = index.toJson()
    val target = outputDir.resolve(SEARCH_INDEX_PATH)
    target.parentFile.mkdirs()
    target.writeText(json)
    val kilobytes = json.toByteArray().size / BYTES_PER_KILOBYTE
    println("Search index: ${index.sections.size} sections across ${index.pages.size} pages, $kilobytes KB")
}

private fun writeDocPage(
    outputDir: File,
    docsDir: File,
    source: DocSource,
    page: RenderedPage,
    sections: List<NavSection>,
    neighbours: Pair<DocSource?, DocSource?>,
    entryUrl: String
) {
    val depth = source.url.urlDepth()
    val body = markImages(expandThemedImages(html = page.html, docsDir = docsDir, pagePath = source.path))
    val html =
        renderPage(
            title = "${page.title} · $SITE_NAME",
            description = page.summary.ifBlank { SITE_TAGLINE },
            context = PageContext(url = source.url, depth = depth, entryUrl = entryUrl),
            navigation = renderNavigation(sections = sections, currentUrl = source.url, depth = depth),
            contents = renderContents(page.headings),
            body = body + renderPagination(previous = neighbours.first, next = neighbours.second, depth = depth)
        )
    val target = outputDir.resolve(source.url)
    target.parentFile.mkdirs()
    target.writeText(html)
}

private fun writeHome(
    outputDir: File,
    docsDir: File,
    page: RenderedPage,
    sections: List<NavSection>,
    rendered: Map<DocSource, RenderedPage>,
    entryUrl: String
) {
    val groups =
        sections.filter { section -> section.key in HOME_TILE_SECTIONS }.map { section ->
            HomeGroup(
                title = section.title,
                tiles =
                    section.entries.map { entry ->
                        HomeTile(
                            title = rendered.getValue(entry).title,
                            summary = rendered.getValue(entry).summary,
                            url = entry.url
                        )
                    }
            )
        }
    val html =
        renderPage(
            title = "$SITE_NAME · Calendars for Compose Multiplatform",
            description = page.summary.ifBlank { SITE_TAGLINE },
            context = PageContext(url = "index.html", depth = 0, entryUrl = entryUrl),
            navigation = "",
            contents = "",
            body = markImages(
                expandThemedImages(
                    html = renderHome(page = page, groups = groups),
                    docsDir = docsDir,
                    pagePath = "index.md"
                )
            ),
            landing = true
        )
    outputDir.resolve("index.html").writeText(html)
}

/**
 * The page GitHub Pages serves for anything it cannot find.
 *
 * Its own links are absolute from [SITE_BASE_PATH] rather than relative like every other page's,
 * because this is the one page served under a URL it was not written for: a relative stylesheet
 * would resolve against whatever the reader mistyped.
 */
private fun writeNotFound(
    outputDir: File,
    entryUrl: String
) {
    val body =
        """<div class="notfound">
  <p class="notfound-code">404</p>
  <h1>This page does not exist</h1>
  <p>The link that brought you here is out of date, or the page has moved.</p>
  <div class="hero-actions">
    <a class="btn btn--primary" href="$SITE_BASE_PATH">Back to the docs</a>
    <a class="btn" href="$REPO_URL/issues" rel="noopener">Report a broken link</a>
  </div>
</div>"""
    val html =
        renderPage(
            title = "Not found · $SITE_NAME",
            description = "The page you are looking for does not exist.",
            context = PageContext(url = "404.html", depth = 0, entryUrl = entryUrl),
            navigation = "",
            contents = "",
            body = body,
            landing = true
        )
    val absolute =
        html
            .replace("""="assets/""", """="$SITE_BASE_PATH""" + "assets/")
            .replace("data-search-root=\"\"", "data-search-root=\"$SITE_BASE_PATH\"")
    outputDir.resolve("404.html").writeText(absolute)
}

private fun writeSitemap(
    outputDir: File,
    urls: List<String>
) {
    val entries = urls.joinToString("\n") { url -> "  <url><loc>$SITE_BASE_URL$url</loc></url>" }
    outputDir.resolve("sitemap.xml").writeText(
        """<?xml version="1.0" encoding="UTF-8"?>
<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">
$entries
</urlset>
"""
    )
}

/** Copies everything in `docs/` that is not markdown — images, mostly — keeping its layout. */
private fun copyStaticFiles(
    docsDir: File,
    outputDir: File
) {
    docsDir
        .walkTopDown()
        .filter { file -> file.isFile && file.extension != "md" }
        .forEach { file ->
            val target = outputDir.resolve(file.relativeTo(docsDir).invariantSeparatorsPath)
            target.parentFile.mkdirs()
            file.copyTo(target = target, overwrite = true)
        }
}

/**
 * Copies the stylesheet and the script out of the generator's own resources.
 *
 * They live as real files rather than Kotlin strings so an editor treats them as CSS and JavaScript
 * — highlighting, formatting and errors all work, which they do not inside a string literal.
 */
private fun copyAssets(outputDir: File) {
    val directory = outputDir.resolve("assets")
    directory.mkdirs()
    assets.forEach { name ->
        val stream =
            checkNotNull(object {}.javaClass.getResourceAsStream("/$name")) {
                "Missing generator resource: $name"
            }
        directory.resolve(name).writeBytes(stream.use { input -> input.readBytes() })
    }
}
