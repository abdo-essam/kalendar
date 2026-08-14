package com.himanshoe.docsite

/** The library's name, as the top bar and every page title spell it. */
const val SITE_NAME = "Kalendar"

/** The one-line description under the hero, and the fallback meta description. */
const val SITE_TAGLINE =
    "Seven standalone calendar composables for Compose Multiplatform — Android, iOS, desktop and the web, " +
        "from one API."

/** Where the site is published. Used for canonical links, Open Graph and the sitemap. */
const val SITE_BASE_URL = "https://hi-manshu.github.io/Kalendar/"

/**
 * The path the site is served from.
 *
 * Every page links relatively, which is what lets the site be served from a project path rather than
 * a domain root. `404.html` is the one exception: GitHub Pages serves it for a URL at any depth, so
 * a relative asset link in it resolves against whatever the reader mistyped.
 */
const val SITE_BASE_PATH = "/Kalendar/"

/** The project's repository, linked from the top bar and the footer. */
const val REPO_URL = "https://github.com/hi-manshu/Kalendar"

/** The Wasm playground, assembled in beside the docs by `assembleSite`. */
const val PLAYGROUND_URL = "playground/index.html"

/** The landing page's primary call to action — reading docs beats poking a demo. */
const val GET_STARTED_URL = "getting-started.html"

/** Where recurring sponsorship goes. Verified live against the author's GitHub profile. */
const val SPONSORS_URL = "https://github.com/sponsors/hi-manshu"

/** The one-off equivalent, for readers who would rather not start a subscription. */
const val COFFEE_URL = "https://buymeacoffee.com/himanshoe"

/** The heading a directory of pages gets in the sidebar, where the directory name is not enough. */
val sectionTitles: Map<String, String> =
    mapOf(
        "views" to "Views",
        "sync" to "Device sync"
    )

/** The heading the pages that sit loose at the top of `docs/` are gathered under. */
const val ROOT_SECTION_TITLE = "Guide"

/**
 * The order sections appear in the sidebar. Anything not listed follows, alphabetically.
 *
 * The sidebar is otherwise derived from the directory tree, which keeps it from drifting out of step
 * with the files — but alphabetical order alone would open the docs on whatever page happens to
 * start with an "a", so the sequence a reader should actually meet is stated here.
 */
val sectionOrder: List<String> = listOf("views")

/**
 * The order pages appear within their section. Anything not listed follows, alphabetically, and a
 * directory's own `index.md` always comes first regardless.
 *
 * Listing a page that does not exist is harmless — it simply never matches — so this list can run
 * ahead of the docs while they are being written.
 */
val pageOrder: List<String> =
    listOf(
        "getting-started.md",
        "configuration.md",
        "theming.md",
        "customization.md",
        "events.md",
        "localization.md",
        "sync.md",
        "migration.md",
        "changelog.md",
        "views/week.md",
        "views/month.md",
        "views/year.md",
        "views/timeline.md",
        "views/schedule.md",
        "views/schedule-week.md",
        "views/agenda.md"
    )

/** The snippet the home page opens with, so the first thing a reader sees is the thing they need. */
val installSnippet: String =
    """
    dependencies {
        implementation("com.himanshoe:kalendar:2.0.0-RC3")
    }
    """.trimIndent()
