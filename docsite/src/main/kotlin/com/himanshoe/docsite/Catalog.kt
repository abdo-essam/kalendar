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

/**
 * The generated KDoc for the calendar views, assembled in beside the docs by `assembleSite`.
 *
 * Named here rather than spelled out at each link, because the same path is linked from the top bar,
 * the sidebar and the footer, and three copies of a path is three chances to move one of them.
 */
const val KDOC_CALENDAR_URL = "api/kalendar/index.html"

/**
 * The generated KDoc for the engine.
 *
 * Linked beside the calendar's own, not folded into it: `kalendar-foundation` is a library someone
 * can build against without ever calling a Kalendar composable, and a reader doing that has no
 * reason to go looking for it under the views' API.
 */
const val KDOC_ENGINE_URL = "api/kalendar-foundation/index.html"

/** The landing page's primary call to action — reading docs beats poking a demo. */
const val GET_STARTED_URL = "getting-started.html"

/**
 * Sidebar sections that also get a tile grid on the landing page, by [sectionOrder] key.
 *
 * Deliberately not every section: a full site map on a landing page is long enough to bury the
 * thing a visitor came for. These two are the fork in the road the release is about — take the
 * calendars as they come, or build your own on the engine — and a visitor deciding whether this
 * library fits is deciding exactly that, so they earn the space. Everything else is one click away
 * behind the hero's primary action and is listed in the sidebar of every other page.
 *
 * Keyed rather than titled: a section's heading is copy, and matching on copy means renaming a
 * heading silently empties the landing page.
 */
val HOME_TILE_SECTIONS: Set<String> = setOf("views", "engine")

/** Where recurring sponsorship goes. Verified live against the author's GitHub profile. */
const val SPONSORS_URL = "https://github.com/sponsors/hi-manshu"

/** The one-off equivalent, for readers who would rather not start a subscription. */
const val COFFEE_URL = "https://buymeacoffee.com/himanshoe"

/**
 * The heading each section gets in the sidebar.
 *
 * A section is keyed either by the directory its pages sit in or, for the pages loose at the top of
 * `docs/`, by the group [rootSections] puts them in. One map covers both, so a page moving into a
 * directory of its own does not change which heading it appears under.
 *
 * A key with no title falls back to the key made readable — a `device-sync` directory nobody
 * declared becomes "Device Sync" rather than a heading that still looks like a path.
 */
val sectionTitles: Map<String, String> =
    mapOf(
        "start" to "Start here",
        "views" to "Views",
        "using" to "Using the calendar",
        "engine" to "Build your own",
        "reference" to "API reference",
        "project" to "Project",
        "guide" to "Guide"
    )

/**
 * Which section each page at the top of `docs/` belongs to.
 *
 * The docs have two audiences and this is where the sidebar says so: a reader can take the calendars
 * as they come — start, pick a view, style it, feed it events — or skip all of that and build their
 * own UI on the engine. The engine's pages therefore get a section of their own rather than a line
 * in a single undifferentiated "Guide", because a reader who wants the engine has no way to tell
 * from a flat list that the engine is a thing they can have.
 *
 * A page that is not listed lands in [DEFAULT_ROOT_SECTION] rather than disappearing, and a page
 * listed here that does not exist is simply never matched — so this can run ahead of the docs while
 * they are being written. Only the pages that sit loose at the top of `docs/` need placing: a page
 * inside a directory takes that directory as its section, which is how `docs/reference/` becomes the
 * API reference without a line here.
 */
val rootSections: Map<String, String> =
    mapOf(
        "getting-started.md" to "start",
        "configuration.md" to "start",
        "theming.md" to "using",
        "customization.md" to "using",
        "events.md" to "using",
        "localization.md" to "using",
        "sync.md" to "using",
        "performance.md" to "using",
        "engine.md" to "engine",
        "migration.md" to "project",
        "changelog.md" to "project"
    )

/** The section a page at the top of `docs/` falls into when [rootSections] does not place it. */
const val DEFAULT_ROOT_SECTION = "guide"

/**
 * The order sections appear in the sidebar. Anything not listed follows, alphabetically.
 *
 * The sidebar is otherwise derived from the files, which keeps it from drifting out of step with
 * them — but alphabetical order alone would open the docs on whatever page happens to start with an
 * "a", so the sequence a reader should actually meet is stated here.
 *
 * [DEFAULT_ROOT_SECTION] sits second on purpose. It only ever holds a page nobody has placed yet,
 * which is a page that has just been written, and burying it under the changelog is how a new page
 * stays unread.
 */
val sectionOrder: List<String> =
    listOf(
        "start",
        DEFAULT_ROOT_SECTION,
        "views",
        "using",
        "engine",
        "reference",
        "project"
    )

/**
 * The order pages appear within their section. Anything not listed follows, alphabetically, and a
 * directory's own `index.md` always comes first regardless.
 *
 * Listing a page that does not exist is harmless — it simply never matches — so this list can run
 * ahead of the docs while they are being written.
 *
 * The reference's packages are in the order its own index lists them rather than alphabetically:
 * the engine's packages first, in the order the engine guide introduces them — an event before the
 * grid that lays events out, a grid before the pager that scrolls it — and the calendar's after,
 * from the composables inwards. Alphabetical would open the engine on its date arithmetic.
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
        "performance.md",
        "engine.md",
        "migration.md",
        "changelog.md",
        "views/week.md",
        "views/month.md",
        "views/year.md",
        "views/timeline.md",
        "views/schedule.md",
        "views/schedule-week.md",
        "views/agenda.md",
        "reference/foundation-event.md",
        "reference/foundation-datetime.md",
        "reference/foundation-grid.md",
        "reference/foundation-paging.md",
        "reference/foundation-schedule.md",
        "reference/foundation-selection.md",
        "reference/foundation-format.md",
        "reference/kalendar-views.md",
        "reference/kalendar-state.md",
        "reference/kalendar-theme.md",
        "reference/kalendar-slot.md",
        "reference/kalendar-component.md"
    )

/** The snippet the home page opens with, so the first thing a reader sees is the thing they need. */
val installSnippet: String =
    """
    dependencies {
        implementation("com.himanshoe:kalendar:2.0.0-RC3")
    }
    """.trimIndent()
