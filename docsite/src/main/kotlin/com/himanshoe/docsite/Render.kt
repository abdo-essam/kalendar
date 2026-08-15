package com.himanshoe.docsite

import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.html.HtmlGenerator
import org.intellij.markdown.parser.CancellationToken
import org.intellij.markdown.parser.MarkdownParser

/** A heading a reader can jump to, for the on-page contents. */
data class Heading(
    /** The anchor this heading can be linked to. */
    val id: String,
    /** The heading's visible text, markup stripped. */
    val text: String,
    /** Heading depth: 1 for the page title, 2 and 3 for its sections. */
    val level: Int
)

/** A markdown file, rendered. */
data class RenderedPage(
    /** The page's own first-level heading. */
    val title: String,
    /** The rendered body, ready to drop into the content column. */
    val html: String,
    /** Every heading on the page, for the on-page contents and for link checking. */
    val headings: List<Heading>,
    /** The page's opening prose as plain words, for the meta description and the home page tiles. */
    val summary: String
)

private val flavour = GFMFlavourDescriptor()

private val headingPattern = Regex("""<h([1-6])>(.*?)</h\1>""", RegexOption.DOT_MATCHES_ALL)
private val codeBlockPattern = Regex("""<pre>(<code[^>]*>)([\s\S]*?)</code></pre>""")
private val linkPattern = Regex("""href="([^"]+)"""")
private val imagePattern = Regex("""(<img\b[^>]*?)/?>""")
private val markdownImagePattern = Regex("""!\[([^\[\]]*)]\(\s*([^)\s]+)""")
private val srcPattern = Regex("""\bsrc="([^"]*)"""")
private val altPattern = Regex("""\balt="[^"]*"""")
private val tablePattern = Regex("""<table>([\s\S]*?)</table>""")
private val paragraphPattern = Regex("""<p>([\s\S]*?)</p>""")
private val codeOnlyParagraph = Regex("""<code>[^<]*</code>""")
private val tagPattern = Regex("""<[^>]+>""")

private const val SUMMARY_LENGTH = 180
private const val TOC_MAX_LEVEL = 3

/** Renders one markdown document into the HTML the page template drops into its content column. */
fun renderMarkdown(markdown: String): RenderedPage {
    val body = markdownToHtml(markdown)

    val headings = mutableListOf<Heading>()
    val withAlt = restoreImageAlt(html = body, markdown = markdown)
    val withAnchors = addHeadingAnchors(html = withAlt, collected = headings)
    val withLinks = rewriteLinks(withAnchors)
    val withTables = wrapTables(withLinks)
    val withCode = renderCodeBlocks(withTables)

    return RenderedPage(
        title = headings.firstOrNull { heading -> heading.level == 1 }?.text ?: SITE_NAME,
        html = withCode,
        headings = headings,
        summary = firstParagraph(body)
    )
}

/** CommonMark plus GitHub's tables and strikethrough, rendered to a fragment rather than a document. */
fun markdownToHtml(markdown: String): String {
    val parser = MarkdownParser(flavour, true, CancellationToken.NonCancellable)
    val tree = parser.buildMarkdownTreeFromString(markdown as CharSequence)
    return HtmlGenerator(markdown, tree, flavour)
        .generateHtml()
        .removePrefix("<body>")
        .removeSuffix("</body>")
}

/**
 * Gives every heading an id and records it, so the on-page contents can link to it and so a heading
 * can be linked to from anywhere else.
 *
 * The id comes from the heading's own text rather than a counter, because a counter changes meaning
 * the moment a heading is inserted above it, and every link that pointed at the old anchor then
 * silently lands somewhere else.
 */
private fun addHeadingAnchors(
    html: String,
    collected: MutableList<Heading>
): String {
    val used = mutableSetOf<String>()
    return headingPattern.replace(html) { match ->
        val level = match.groupValues[1].toInt()
        val inner = match.groupValues[2]
        val text = plainText(inner).trim()
        val id = uniqueSlug(text = text, used = used)
        collected.add(Heading(id = id, text = text, level = level))
        """<h$level id="$id">$inner""" +
            """<a class="anchor" href="#$id" aria-label="Link to this section">#</a></h$level>"""
    }
}

/**
 * A URL-safe form of a heading, made unique within the page by suffixing repeats.
 *
 * Punctuation is dropped rather than turned into a separator, so `iCal import/export` becomes
 * `ical-importexport`. That is GitHub's rule, and it is the one anyone writing `[...](page.md#...)`
 * by hand will have in their fingers — a tidier scheme of our own would silently break every anchor
 * link already written against it.
 */
private fun uniqueSlug(
    text: String,
    used: MutableSet<String>
): String {
    val base =
        text
            .lowercase()
            .replace(Regex("""[^a-z0-9 _-]+"""), "")
            .trim()
            .replace(Regex("""[\s_]+"""), "-")
            .trim('-')
            .ifEmpty { "section" }
    var candidate = base
    var suffix = 2
    while (!used.add(candidate)) {
        candidate = "$base-$suffix"
        suffix++
    }
    return candidate
}

/**
 * Points links at the generated pages.
 *
 * The site mirrors the docs directory, so a relative link between two documents already resolves
 * correctly and only its extension is wrong. That is the whole rewrite — no path rebuilding, which
 * is the part that usually breaks when a page moves. External links and bare anchors are left alone.
 */
private fun rewriteLinks(html: String): String =
    linkPattern.replace(html) { match ->
        val href = match.groupValues[1]
        val rewritten =
            if (isExternal(href) || href.startsWith("#")) {
                href
            } else {
                href.substringBefore('#').toSiteUrl() + href.substringAfter('#', "").let { anchor ->
                    if (anchor.isEmpty()) "" else "#$anchor"
                }
            }
        """href="$rewritten""""
    }

/** True for a target this generator neither writes nor resolves. */
fun isExternal(href: String): Boolean =
    href.startsWith("http://") ||
        href.startsWith("https://") ||
        href.startsWith("mailto:") ||
        href.startsWith("data:") ||
        href.startsWith("//")

/**
 * Gives every table its own scroll container.
 *
 * A wide table is the one thing on a documentation page that reliably breaks a phone layout: without
 * this it widens the body, and every other paragraph on the page starts scrolling sideways with it.
 */
private fun wrapTables(html: String): String =
    tablePattern.replace(html) { match ->
        """<div class="table-wrap"><table>${match.groupValues[1]}</table></div>"""
    }

/**
 * Puts the alt text an author actually wrote back onto each image.
 *
 * The markdown renderer builds an image's `alt` by concatenating the text leaves of its label, and
 * punctuation is not a text leaf: `![The 13th shows a "+1" badge, in bold.](x.png)` reaches the page
 * as `alt="The 13th shows a 1 badge in bold"`. Nobody notices, because the one audience for alt text
 * is the one audience that cannot see the picture — which is exactly why this is worth repairing
 * rather than working around by asking authors to write raw `<img>` tags.
 *
 * The source is matched on the image's destination, so an image is repaired wherever it appears and
 * one written twice on a page with two different descriptions keeps the first. Only inline images
 * (`![...](...)`) are matched; a reference-style image keeps whatever the renderer gave it.
 */
private fun restoreImageAlt(
    html: String,
    markdown: String
): String {
    val authored = mutableMapOf<String, String>()
    markdownImagePattern.findAll(markdown).forEach { match ->
        authored.putIfAbsent(match.groupValues[2], match.groupValues[1])
    }
    if (authored.isEmpty()) {
        return html
    }
    return imagePattern.replace(html) { match ->
        val tag = match.groupValues[1]
        val source = srcPattern.find(tag)?.groupValues?.get(1)
        val alt = source?.let { destination -> authored[destination] }
        if (alt == null) {
            match.value
        } else {
            withAlt(tag = tag, alt = alt) + ">"
        }
    }
}

/** [tag] with its `alt` replaced, or given one if the renderer left it off entirely. */
private fun withAlt(
    tag: String,
    alt: String
): String {
    val attribute = """alt="${escapeAttribute(alt)}""""
    return if (altPattern.containsMatchIn(tag)) {
        altPattern.replace(tag) { attribute }
    } else {
        "$tag $attribute"
    }
}

/**
 * Marks images as lazily loaded and keeps them inside the content column.
 *
 * An image that already says how it loads is left alone: [expandThemedImages] writes its own pair of
 * tags complete with loading hints, and marking them again would leave two `loading` attributes on
 * one element.
 */
fun markImages(html: String): String =
    imagePattern.replace(html) { match ->
        if (match.value.contains("loading=")) {
            match.value
        } else {
            """${match.groupValues[1]} loading="lazy" decoding="async">"""
        }
    }

/** Colours the Kotlin in every fenced block and hangs a copy button off it. */
private fun renderCodeBlocks(html: String): String =
    codeBlockPattern.replace(html) { match ->
        val openTag = match.groupValues[1]
        val language = openTag.substringAfter("language-", "").substringBefore('"')
        renderCodeWindow(language = language, code = match.groupValues[2].trimEnd('\n'))
    }

/**
 * Wraps a snippet in its frame: the language, the copy button, and the code itself.
 *
 * The frame is doing real work rather than decoration. It marks where a snippet begins and ends on a
 * page that is otherwise one dark column, and it gives the copy button somewhere to live that is
 * always visible — hovering to discover a control only works for readers who already suspect it is
 * there.
 */
fun renderCodeWindow(
    language: String,
    code: String,
    reveal: Boolean = true
): String {
    val normalized = normalizeQuotes(code)
    val painted =
        if (language == "kotlin") {
            highlightKotlin(normalized)
        } else {
            normalized
        }
    val label =
        if (language.isEmpty()) {
            "text"
        } else {
            language
        }
    val revealed =
        if (reveal) {
            " data-reveal"
        } else {
            ""
        }
    return """<div class="code-block"$revealed>""" +
        """<div class="code-chrome"><span class="code-lang">${escapeText(label)}</span>""" +
        """<button class="copy" type="button" aria-label="Copy code to clipboard">""" +
        """<span class="copy-label">Copy</span></button></div>""" +
        """<pre><code class="language-${escapeAttribute(label)}">$painted</code></pre></div>"""
}

/**
 * Puts quotes back to the characters they are, so one canonical escaping reaches the highlighter.
 *
 * The markdown generator escapes `"` and `'` inside a fence; the landing page's own snippets are
 * escaped by [escapeText], which does not. Without this the tokenizer sees `&quot;` where it expects
 * `"`, and every string literal in the docs is left uncoloured while the words inside it are
 * highlighted as if they were code — the failure looks like a theme problem, not a parsing one.
 *
 * Safe because a quote needs no escaping in element content: only `&`, `<` and `>` do, and those are
 * left exactly as they were. Source that genuinely contained the text `&quot;` arrives as
 * `&amp;quot;`, which does not match.
 */
private fun normalizeQuotes(code: String): String = code.replace("&quot;", "\"").replace("&#39;", "'")

/**
 * A standalone Kotlin snippet, drawn exactly like one that came from a markdown fence.
 *
 * It does not take part in the scroll reveal: the only caller is the hero, where the snippet is
 * above the fold and fading it in delays the one line a reader came for.
 */
fun renderKotlinBlock(code: String): String =
    renderCodeWindow(language = "kotlin", code = escapeText(code), reveal = false)

/**
 * The visible words of a fragment of HTML, for titles and meta descriptions.
 *
 * Tags are removed rather than replaced with a space. The fragments this is asked about — a heading,
 * an opening paragraph — contain only inline markup, where every real space is already in the text:
 * padding the gaps instead produces `standalone composables .` wherever a sentence ends in `code`.
 */
fun plainText(html: String): String =
    tagPattern
        .replace(html, "")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&amp;", "&")
        .replace(Regex("""\s+"""), " ")
        .trim()

/**
 * The page's opening prose, trimmed to something a search result can show whole.
 *
 * A paragraph that is nothing but one code span is skipped. Every reference page opens by naming its
 * package on a line of its own, and taking that literally makes the page's description — its meta
 * tag, its entry in `llms.txt` — read "com.himanshoe.kalendar.foundation.grid", which is the one
 * thing a reader already knew from the link they were deciding whether to follow.
 */
private fun firstParagraph(html: String): String {
    val text =
        paragraphPattern
            .findAll(html)
            .map { match -> match.groupValues[1] }
            .filterNot { paragraph -> codeOnlyParagraph.matches(paragraph.trim()) }
            .map(::plainText)
            .firstOrNull { paragraph -> paragraph.isNotBlank() }
            .orEmpty()
    return if (text.length <= SUMMARY_LENGTH) {
        text
    } else {
        text.take(SUMMARY_LENGTH).substringBeforeLast(' ') + "…"
    }
}

/** The headings the on-page contents lists: the sections of the page, not its title. */
fun tocHeadings(headings: List<Heading>): List<Heading> =
    headings.filter { heading -> heading.level in 2..TOC_MAX_LEVEL }
