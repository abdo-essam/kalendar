package com.himanshoe.docsite

/** One card on the landing page, standing for a documentation page. */
data class HomeTile(
    /** The page's title. */
    val title: String,
    /** Its opening line, so a reader can tell the pages apart without opening them. */
    val summary: String,
    /** Where it lives on the site. */
    val url: String
)

/** A group of landing-page cards, matching a section of the sidebar. */
data class HomeGroup(
    /** The section heading. */
    val title: String,
    /** Its pages. */
    val tiles: List<HomeTile>
)

private val firstHeadingPattern = Regex("""<h1\b[^>]*>[\s\S]*?</h1>""")
private val firstParagraphPattern = Regex("""<p>([\s\S]*?)</p>""")

/**
 * The landing page: a hero, a directory of everything the docs contain, then the rest of
 * `docs/index.md`.
 *
 * The hero's title and strapline are lifted out of `index.md` rather than written here, so the front
 * page says whatever the person editing the docs wrote — a hero with its own hard-coded copy is a
 * second place to update and therefore the first place to go stale.
 */
fun renderHome(
    page: RenderedPage,
    groups: List<HomeGroup>
): String {
    val tagline = firstParagraphPattern.find(page.html)?.groupValues?.get(1)
    val remainder = stripLead(page.html)
    return renderHero(title = page.title, tagline = tagline) +
        groups.joinToString("") { group -> renderGroup(group) } +
        renderRemainder(remainder)
}

private fun renderHero(
    title: String,
    tagline: String?
): String =
    """<section class="hero">
  <div class="aurora" aria-hidden="true"><span></span><span></span><span></span></div>
  <div class="hero-inner">
    <p class="hero-eyebrow">Compose Multiplatform</p>
    <h1 class="hero-title">${escapeText(title)}</h1>
    <p class="hero-tagline">${tagline ?: escapeText(SITE_TAGLINE)}</p>
    <div class="hero-actions">
      <a class="btn btn--primary" href="$PLAYGROUND_URL">Open the playground</a>
      <a class="btn" href="$REPO_URL" rel="noopener">View on GitHub</a>
    </div>
    <div class="hero-install">${renderKotlinBlock(installSnippet)}</div>
    <p class="hero-platforms">Android<span>·</span>iOS<span>·</span>Desktop<span>·</span>Web</p>
  </div>
</section>"""

private fun renderGroup(group: HomeGroup): String {
    if (group.tiles.isEmpty()) {
        return ""
    }
    val tiles =
        group.tiles.joinToString("") { tile ->
            """<a class="tile" href="${tile.url}" data-reveal>""" +
                """<h3 class="tile-title">${escapeText(tile.title)}</h3>""" +
                """<p class="tile-summary">${escapeText(tile.summary)}</p>""" +
                """<span class="tile-arrow" aria-hidden="true">→</span></a>"""
        }
    return """<section class="home-section">""" +
        """<h2 class="home-heading" data-reveal>${escapeText(group.title)}</h2>""" +
        """<div class="tiles">$tiles</div></section>"""
}

private fun renderRemainder(html: String): String =
    if (html.isBlank()) {
        ""
    } else {
        """<section class="home-section"><div class="prose home-prose">$html</div></section>"""
    }

/**
 * Drops the page's own title and opening paragraph, which the hero has already said.
 *
 * Only the first of each, and only when it sits at the very top: a page that opens with something
 * else keeps all of its content rather than quietly losing a paragraph.
 */
private fun stripLead(html: String): String {
    val withoutHeading = firstHeadingPattern.replaceFirst(html, "").trimStart()
    val paragraph = firstParagraphPattern.find(withoutHeading)
    return if (paragraph != null && paragraph.range.first == 0) {
        withoutHeading.removeRange(paragraph.range).trimStart()
    } else {
        withoutHeading
    }
}
