package com.himanshoe.docsite

import java.io.File

/**
 * The suffix that turns one image reference into a theme-aware pair.
 *
 * A markdown author writes the image once:
 *
 * ```markdown
 * ![The month grid, with the 12th selected](../images/month-theme.png)
 * ```
 *
 * and this expands it into the two files `:docsite:generateDocsImages` actually produced —
 * `month-light.png` and `month-dark.png` — one of which the stylesheet shows depending on the theme
 * the site is being read in. `month-theme.png` itself never exists on disk; it is the request, not
 * the file.
 *
 * The convention lives here rather than in the markdown because the alternative is asking every
 * author to hand-write two `<img>` tags, their classes, their dimensions and their shared `alt` text,
 * and to keep all of it in step — which is four chances to write a paragraph of markup that renders a
 * white screenshot onto a black page.
 */
private const val THEME_SUFFIX = "-theme.png"

private const val LIGHT_SUFFIX = "-light.png"
private const val DARK_SUFFIX = "-dark.png"

private val themedImagePattern = Regex("""<img\b([^>]*?)/?>""")
private val attributePattern = Regex("""(\w[\w-]*)="([^"]*)"""")

/** PNG signature plus the length and type of the IHDR chunk, after which the dimensions start. */
private const val IHDR_WIDTH_OFFSET = 16
private const val PNG_HEADER_BYTES = 24
private const val BYTES_PER_DIMENSION = 4
private const val BITS_PER_BYTE = 8
private const val BYTE_MASK = 0xFF

/**
 * The pictures are drawn at twice the size they are shown at, so they stay sharp on a display with
 * twice the pixels. The `width`/`height` attributes have to be the size the page lays out, not the
 * size of the file.
 */
private const val PIXEL_RATIO = 2

/**
 * Expands every `-theme.png` reference on a page into its light and dark pair.
 *
 * [pagePath] is the page's path inside `docs/` — `views/month.md` — and is what the image's own
 * relative `src` is resolved against, exactly as a browser will resolve it against the rendered page.
 * That resolution is the only reason this needs the docs directory at all: the files are measured so
 * each `<img>` can carry real `width` and `height` attributes and reserve its space before it loads.
 * Without them a page of screenshots jumps under the reader as each one arrives.
 */
fun expandThemedImages(
    html: String,
    docsDir: File,
    pagePath: String
): String {
    var seen = 0
    return themedImagePattern.replace(html) { match ->
        val attributes = attributesOf(match.groupValues[1])
        val source = attributes["src"].orEmpty()
        if (!source.endsWith(THEME_SUFFIX)) {
            match.value
        } else {
            seen++
            themedPair(
                source = source,
                alt = attributes["alt"].orEmpty(),
                pageDirectory = pageDirectory(docsDir = docsDir, pagePath = pagePath),
                eager = seen == 1
            )
        }
    }
}

private fun attributesOf(tag: String): Map<String, String> =
    attributePattern
        .findAll(tag)
        .associate { match -> match.groupValues[1] to match.groupValues[2] }

private fun pageDirectory(
    docsDir: File,
    pagePath: String
): File = docsDir.resolve(pagePath).parentFile ?: docsDir

/**
 * The two `<img>` elements, sharing one `alt`.
 *
 * Both are in the document at all times and the stylesheet hides one — see `.shot` in `docs.css`.
 * Doing it in CSS rather than in script is what makes the right picture correct on the very first
 * paint, including for a reader whose stored preference the inline bootstrap has just applied, and it
 * keeps working if the deferred script never loads.
 *
 * Both carry the same `alt`, and neither is marked `aria-hidden`: which of the two is showing is not
 * known until the page is in front of a reader, and `display: none` already takes the other out of
 * the accessibility tree, so the screenshot is described exactly once either way.
 */
private fun themedPair(
    source: String,
    alt: String,
    pageDirectory: File,
    eager: Boolean
): String {
    val base = source.removeSuffix(THEME_SUFFIX)
    val light = base + LIGHT_SUFFIX
    val dark = base + DARK_SUFFIX
    val size = pngSize(pageDirectory.resolve(light).normalize())
    return shot(source = light, alt = alt, variant = "light", size = size, eager = eager) +
        shot(source = dark, alt = alt, variant = "dark", size = size, eager = eager)
}

/**
 * One `<img>`.
 *
 * [eager] is set for the first picture on a page and nothing else. A view's page opens on its
 * screenshot, and `loading="lazy"` on the thing a reader is already looking at is the one place the
 * attribute makes a page worse: the browser defers the fetch, and the reader watches an empty
 * bordered box where the calendar should be. Everything further down keeps the lazy load, which is
 * what it is for.
 *
 * Both halves of a pair carry the same hint even though only one is displayed. `display: none` is
 * already enough for a browser to skip fetching the hidden one, so this costs nothing and means the
 * pair stays correct whichever way the theme toggle is pointing when the page loads.
 */
private fun shot(
    source: String,
    alt: String,
    variant: String,
    size: Pair<Int, Int>?,
    eager: Boolean
): String {
    val dimensions = size?.let { (width, height) -> """ width="$width" height="$height"""" }.orEmpty()
    val loading =
        if (eager) {
            """ loading="eager" fetchpriority="high""""
        } else {
            """ loading="lazy""""
        }
    /*
     * [source] and [alt] are copied out of an `<img>` that is already in the page, so they are
     * already attribute-escaped — escaping them again is what turns an alt reading `a "+1" badge`
     * into `a &amp;quot;+1&amp;quot; badge`, which is then read out literally.
     */
    return """<img class="shot shot--$variant" src="$source" """ +
        """alt="$alt"$dimensions$loading decoding="async">"""
}

/**
 * The laid-out size of a PNG, read from its header.
 *
 * Sixteen bytes in, a PNG carries its width and height as two big-endian 32-bit integers, and that is
 * the whole of what is needed here — decoding the image to ask an `ImageIO` reader the same question
 * would pull the entire bitmap into memory for eight bytes of it.
 *
 * `null` for a file that is missing or too short to be a PNG: the dead-image verifier reports a
 * missing screenshot far better than a crash inside a dimension helper does.
 */
private fun pngSize(file: File): Pair<Int, Int>? {
    if (!file.isFile) {
        return null
    }
    val header = file.inputStream().use { stream -> stream.readNBytes(PNG_HEADER_BYTES) }
    if (header.size < PNG_HEADER_BYTES) {
        return null
    }
    val width = bigEndianInt(header, IHDR_WIDTH_OFFSET)
    val height = bigEndianInt(header, IHDR_WIDTH_OFFSET + BYTES_PER_DIMENSION)
    return width / PIXEL_RATIO to height / PIXEL_RATIO
}

private fun bigEndianInt(
    bytes: ByteArray,
    offset: Int
): Int =
    (0 until BYTES_PER_DIMENSION).fold(0) { accumulated, index ->
        (accumulated shl BITS_PER_BYTE) or (bytes[offset + index].toInt() and BYTE_MASK)
    }
