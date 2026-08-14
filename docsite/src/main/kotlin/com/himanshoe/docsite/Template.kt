package com.himanshoe.docsite

private const val FAVICON =
    "data:image/svg+xml,<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 32 32'>" +
        "<rect width='32' height='32' rx='8' fill='%23000'/>" +
        "<rect x='7' y='9.5' width='18' height='15' rx='3' fill='none' stroke='%23ededed' stroke-width='2'/>" +
        "<path d='M7 15h18' stroke='%23ededed' stroke-width='2'/>" +
        "<path d='M12 6.5v5M20 6.5v5' stroke='%23ededed' stroke-width='2' stroke-linecap='round'/>" +
        "<rect x='11' y='18' width='4' height='4' rx='1' fill='%23a78bfa'/></svg>"

/**
 * The Kalendar mark: a month grid with one day picked out.
 *
 * Drawn in `currentColor` rather than a fixed colour, so it is white on the dark theme and black on
 * the light one without a second asset — which is also what stops it going invisible if the palette
 * ever changes underneath it.
 */
private const val LOGO_MARK =
    """<svg class="logo-mark" viewBox="0 0 28 28" width="22" height="22" aria-hidden="true">""" +
        """<rect x="3" y="5.5" width="22" height="19" rx="4" fill="none" stroke="currentColor" stroke-width="2"/>""" +
        """<path d="M3 12h22" stroke="currentColor" stroke-width="2"/>""" +
        """<path d="M9 2.5v5M19 2.5v5" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>""" +
        """<rect x="7.5" y="15" width="4.5" height="4.5" rx="1.4" fill="currentColor"/></svg>"""

private const val THEME_ICONS =
    """<svg class="icon icon--sun" viewBox="0 0 24 24" aria-hidden="true"><circle cx="12" cy="12" r="4.2"/>""" +
        """<path d="M12 2.2v2.6M12 19.2v2.6M2.2 12h2.6M19.2 12h2.6M5 5l1.9 1.9M17.1 17.1L19 19M19 5l-1.9 1.9""" +
        """M6.9 17.1L5 19"/></svg>""" +
        """<svg class="icon icon--moon" viewBox="0 0 24 24" aria-hidden="true">""" +
        """<path d="M20.5 14.8A8.8 8.8 0 0 1 9.2 3.5a8.8 8.8 0 1 0 11.3 11.3z"/></svg>"""

/**
 * Applied before the first paint.
 *
 * The theme is read here rather than from the deferred script because reading it later paints the
 * default and then corrects it, which is the flash of wrong colours that makes a docs site feel
 * cheap on every navigation. The `js` class is set in the same breath, and it is what arms the
 * scroll reveal: the reveal starts things at zero opacity, so without a marker that scripting is
 * actually running, a reader with JavaScript off would get a page of invisible sections.
 */
private const val THEME_BOOTSTRAP =
    """(function(){var e=document.documentElement;e.className+=' js';""" +
        """try{var s=localStorage.getItem('kalendar-theme');""" +
        """e.dataset.theme=s==='light'?'light':'dark';}""" +
        """catch(x){e.dataset.theme='dark';}})();"""

/**
 * The shell every page shares: head, top bar, sidebar, content column, on-page contents, footer.
 *
 * Every link is written relative to the page rather than absolute from the site root. The site is
 * served from a project path (`/Kalendar/`), not a domain root, so an absolute `/assets/docs.css`
 * would resolve to the wrong place — and would work locally while failing only once deployed, which
 * is the worst way for a link to be wrong.
 */
fun renderPage(
    title: String,
    description: String,
    context: PageContext,
    navigation: String,
    contents: String,
    body: String,
    landing: Boolean = false
): String {
    val root = rootPrefix(context.depth)
    return """<!DOCTYPE html>
<html lang="en" data-theme="dark">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>${escapeText(title)}</title>
<meta name="description" content="${escapeAttribute(description)}">
<link rel="canonical" href="$SITE_BASE_URL${context.url}">
<meta property="og:type" content="website">
<meta property="og:site_name" content="$SITE_NAME">
<meta property="og:title" content="${escapeAttribute(title)}">
<meta property="og:description" content="${escapeAttribute(description)}">
<meta property="og:url" content="$SITE_BASE_URL${context.url}">
<meta name="twitter:card" content="summary">
<meta name="twitter:title" content="${escapeAttribute(title)}">
<meta name="twitter:description" content="${escapeAttribute(description)}">
<meta name="theme-color" content="#000000">
<link rel="icon" href="$FAVICON">
<link rel="stylesheet" href="${root}assets/docs.css">
<script>$THEME_BOOTSTRAP</script>
</head>
<body>
<a class="skip" href="#content">Skip to content</a>
${renderTopBar(root = root, landing = landing, entryUrl = context.entryUrl)}
${renderShell(landing = landing, navigation = navigation, contents = contents, body = body)}
${renderFooter(root = root, entryUrl = context.entryUrl)}
<script src="${root}assets/docs.js" defer></script>
</body>
</html>
"""
}

/**
 * The sticky top bar.
 *
 * The theme control is a real `<button>` rather than a styled `<div>`, so it is reachable by keyboard
 * and announced as a control without a line of ARIA papering over the wrong element.
 */
private fun renderTopBar(
    root: String,
    landing: Boolean,
    entryUrl: String
): String {
    val menu =
        if (landing) {
            ""
        } else {
            """<button class="menu-toggle" type="button" aria-label="Open navigation" """ +
                """aria-expanded="false" aria-controls="sidebar"><span aria-hidden="true"></span></button>"""
        }
    return """<header class="topbar">
  <div class="topbar-inner">
    $menu
    <a class="brand" href="${root}index.html">$LOGO_MARK<span class="brand-name">$SITE_NAME</span></a>
    <nav class="topbar-links" aria-label="Site">
      <a href="$root$entryUrl">Docs</a>
      <a href="$root$PLAYGROUND_URL">Playground</a>
      <a href="$root$KDOC_CALENDAR_URL">KDoc</a>
      <a href="$REPO_URL" rel="noopener">GitHub</a>
    </nav>
    <button class="theme-toggle" type="button" aria-label="Switch between light and dark theme">$THEME_ICONS</button>
  </div>
</header>"""
}

/**
 * The page's body: a documentation page keeps its sidebar and contents column, a landing page has
 * neither and runs the full width.
 *
 * A landing page carrying a docs sidebar is the thing that makes a project's front door read as an
 * internal page someone forgot to design. The two really are different layouts, so they are written
 * as two rather than as one with parts hidden.
 */
private fun renderShell(
    landing: Boolean,
    navigation: String,
    contents: String,
    body: String
): String =
    if (landing) {
        """<main class="landing" id="content">$body</main>"""
    } else {
        """<div class="layout">
  <div class="scrim" aria-hidden="true"></div>
  <aside class="sidebar" id="sidebar">$navigation</aside>
  <main class="content" id="content"><article class="prose">$body</article></main>
  $contents
</div>"""
    }

/**
 * The sidebar, with the current page marked.
 *
 * `aria-current="page"` rather than a class alone: the highlight is the only thing telling a reader
 * where they are, and a colour is not information a screen reader can read out.
 *
 * The two generated references are named for the artifact they document rather than both being
 * called "API reference". The docs now contain a written reference of their own, and two links a
 * reader has to click to tell apart are two links they will click the wrong one of.
 */
fun renderNavigation(
    sections: List<NavSection>,
    currentUrl: String,
    depth: Int
): String {
    val root = rootPrefix(depth)
    val html = StringBuilder()
    html.append("""<nav class="nav" aria-label="Documentation">""")
    html.append("""<div class="nav-quick">""")
    html.append(navLink(href = "${root}index.html", label = "Overview", active = currentUrl == "index.html"))
    html.append(navLink(href = "$root$PLAYGROUND_URL", label = "Playground", active = false))
    html.append(navLink(href = "$root$KDOC_CALENDAR_URL", label = "Calendar KDoc", active = false))
    html.append(navLink(href = "$root$KDOC_ENGINE_URL", label = "Engine KDoc", active = false))
    html.append("</div>")
    sections.forEach { section ->
        html.append("""<div class="nav-section"><h2 class="nav-heading">${escapeText(section.title)}</h2><ul>""")
        section.entries.forEach { entry ->
            html.append("<li>")
            html.append(navLink(href = "$root${entry.url}", label = entry.title, active = entry.url == currentUrl))
            html.append("</li>")
        }
        html.append("</ul></div>")
    }
    html.append("</nav>")
    return html.toString()
}

private fun navLink(
    href: String,
    label: String,
    active: Boolean
): String {
    val marker =
        if (active) {
            """ class="nav-link is-active" aria-current="page""""
        } else {
            """ class="nav-link""""
        }
    return """<a$marker" href="$href">${escapeText(label)}</a>"""
}

/** The on-page contents, omitted entirely when a page has too few headings to need one. */
fun renderContents(headings: List<Heading>): String {
    val listed = tocHeadings(headings)
    if (listed.size < 2) {
        return ""
    }
    val links =
        listed.joinToString("") { heading ->
            """<li class="toc-l${heading.level}"><a href="#${heading.id}">${escapeText(heading.text)}</a></li>"""
        }
    return """<aside class="toc"><nav aria-label="On this page">""" +
        """<h2 class="toc-title">On this page</h2><ul>$links</ul></nav></aside>"""
}

/**
 * The pair of cards that close a documentation page, pointing at the pages either side of it.
 *
 * Reading documentation straight through is a real way to use it, and without these a reader who
 * finishes a page has to go back to the sidebar and work out where they were.
 */
fun renderPagination(
    previous: DocSource?,
    next: DocSource?,
    depth: Int
): String {
    if (previous == null && next == null) {
        return ""
    }
    val root = rootPrefix(depth)
    val previousCard = paginationCard(page = previous, root = root, label = "Previous", next = false)
    val nextCard = paginationCard(page = next, root = root, label = "Next", next = true)
    return """<nav class="page-nav" aria-label="Previous and next page">$previousCard$nextCard</nav>"""
}

private fun paginationCard(
    page: DocSource?,
    root: String,
    label: String,
    next: Boolean
): String {
    if (page == null) {
        return "<span></span>"
    }
    val modifier =
        if (next) {
            " page-nav-card--next"
        } else {
            ""
        }
    return """<a class="page-nav-card$modifier" href="$root${page.url}">""" +
        """<span class="page-nav-label">$label</span>""" +
        """<span class="page-nav-title">${escapeText(page.title)}</span></a>"""
}

private fun renderFooter(
    root: String,
    entryUrl: String
): String =
    """<footer class="footer">
  <div class="footer-inner">
    <div class="footer-brand">$LOGO_MARK<strong>$SITE_NAME</strong></div>
    <p class="footer-note">Calendars for Compose Multiplatform, by
      <a href="https://github.com/hi-manshu" rel="noopener">Himanshu Singh</a>. Apache 2.0.</p>
    <nav class="footer-links" aria-label="Footer">
      <a href="$root$entryUrl">Getting started</a>
      <a href="$root$PLAYGROUND_URL">Playground</a>
      <a href="$root$KDOC_CALENDAR_URL">Calendar KDoc</a>
      <a href="$root$KDOC_ENGINE_URL">Engine KDoc</a>
      <a href="$REPO_URL" rel="noopener">GitHub</a>
      <a href="$SPONSORS_URL" rel="noopener">Sponsor</a>
      <a href="$COFFEE_URL" rel="noopener">Buy me a coffee</a>
    </nav>
  </div>
</footer>"""
