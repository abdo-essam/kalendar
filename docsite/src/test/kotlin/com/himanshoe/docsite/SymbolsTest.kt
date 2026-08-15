package com.himanshoe.docsite

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The auto-linker turns a mention of a public symbol into a link to its reference entry, and it has
 * exactly two ways to be wrong: linking a name that no longer exists, and linking a name in a place
 * where a link is noise. Both are pinned down here, because neither shows up as a build failure —
 * a wrong link renders perfectly.
 */
class SymbolsTest {
    private val gridHref = "reference/foundation-grid.html#monthgriddates"

    private val dump =
        """
        public final class com/himanshoe/kalendar/foundation/grid/KalendarGridKt {
        ${'\t'}public static final fun getALL_DAYS_OF_WEEK ()Ljava/util/Set;
        ${'\t'}public static final fun monthGridDates (Lkotlinx/datetime/LocalDate;)Ljava/util/List;
        ${'\t'}public static final fun weekDates (Lkotlinx/datetime/LocalDate;)Ljava/util/List;
        }

        public final class com/himanshoe/kalendar/KalendarViewsKt {
        ${'\t'}public static final fun KalendarSchedule-neCDxuE (Landroidx/compose/ui/Modifier;)V
        ${'\t'}public static synthetic fun KalendarSchedule-neCDxuE${'$'}default (Landroidx/compose/ui/Modifier;)V
        }

        public final class com/himanshoe/kalendar/foundation/paging/KalendarPager {
        ${'\t'}public final fun plusPages (I)Lcom/himanshoe/kalendar/foundation/paging/KalendarPager;
        }

        public final class com/himanshoe/kalendar/foundation/paging/KalendarPager${'$'}Companion {
        ${'\t'}public final fun endless ()Lcom/himanshoe/kalendar/foundation/paging/KalendarPager;
        }
        """.trimIndent()

    private val symbols = symbolsFrom(dump)

    @Test
    fun `a top-level function is a symbol and a mangled name is read without its suffix`() {
        assertTrue("monthGridDates" in symbols)
        assertTrue("KalendarSchedule" in symbols)
        assertFalse(symbols.any { symbol -> symbol.contains('-') })
    }

    /**
     * A top-level `val` reaches the dump as its getter. Both readings are kept because there is no
     * way to tell `getALL_DAYS_OF_WEEK` from a function genuinely called `getOrNull`, and the
     * reference decides which one is real by having an entry for it.
     */
    @Test
    fun `a property is a symbol under its own name as well as its getter`() {
        assertTrue("ALL_DAYS_OF_WEEK" in symbols)
        assertTrue("getALL_DAYS_OF_WEEK" in symbols)
    }

    @Test
    fun `a class is a symbol but its file facade and its generated members are not`() {
        assertTrue("KalendarPager" in symbols)
        assertFalse("KalendarGridKt" in symbols)
        assertFalse("Companion" in symbols)
        assertFalse("KalendarPager.Companion" in symbols)
        assertFalse(symbols.any { symbol -> symbol.contains('$') })
    }

    /** A class member is not linkable: there is no reference entry per `equals` or `plusPages`. */
    @Test
    fun `a member of a class is not a symbol`() {
        assertTrue("monthGridDates" in symbols, "the top-level functions beside them are")
        assertFalse("plusPages" in symbols)
        assertFalse("endless" in symbols)
    }

    @Test
    fun `a documented symbol links to its reference entry and an undocumented one does not`() {
        val index = index(reference("reference/foundation-grid.md", "monthGridDates", "KalendarNothing"))

        val target = SymbolTarget(url = "reference/foundation-grid.html", anchor = "monthgriddates")

        assertEquals(target, index["monthGridDates"])
        assertFalse("KalendarNothing" in index)
    }

    /**
     * The dumps decide what exists. A reference entry left behind by a rename must stop being a link
     * target in the same commit that renames the symbol — that is the whole reason the list is read
     * from the ABI rather than from the prose describing it.
     */
    @Test
    fun `an entry for a symbol the dumps no longer contain is not linkable`() {
        val index =
            buildSymbolIndex(
                pages = reference("reference/foundation-grid.md", "monthGridDates"),
                apiSymbols = emptySet()
            )

        assertTrue(index.isEmpty())
    }

    @Test
    fun `an entry titled with its receiver is reachable with and without it`() {
        val index = index(reference("reference/foundation-datetime.md", "LocalDate.weekDates"))

        assertEquals(index["LocalDate.weekDates"], index["weekDates"])
        assertEquals("localdateweekdates", index.getValue("weekDates").anchor)
    }

    /** Two entries claiming one name is a question with no answer; guessing is worse than leaving it. */
    @Test
    fun `a name two reference pages both claim is left alone`() {
        val pages =
            reference("reference/foundation-grid.md", "weekDates", "monthGridDates") +
                reference("reference/kalendar-views.md", "weekDates")
        val built = index(pages)

        assertTrue("monthGridDates" in built, "a name only one page claims still links")
        assertFalse("weekDates" in built)
    }

    /** Only the reference documents symbols; a heading elsewhere that happens to match is not an entry. */
    @Test
    fun `a heading outside the reference is not a link target`() {
        val pages =
            reference("engine.md", "monthGridDates") +
                reference("reference/foundation-grid.md", "weekDates")

        assertEquals(setOf("weekDates"), index(pages).keys)
    }

    @Test
    fun `a mention in prose becomes a link to the reference`() {
        val linked = linkify("<p>Call <code>monthGridDates</code> for a page.</p>", currentUrl = "engine.html")

        assertEquals(
            """<p>Call <code><a class="api-link" href="reference/foundation-grid.html#monthgriddates">""" +
                """monthGridDates</a></code> for a page.</p>""",
            linked
        )
    }

    @Test
    fun `a mention on a page below the root links back up to it`() {
        val linked = linkify("<p><code>monthGridDates</code></p>", currentUrl = "views/month.html", depth = 1)

        assertTrue(linked.contains("""href="../reference/foundation-grid.html#monthgriddates""""))
    }

    /**
     * A snippet where every other identifier is underlined is a snippet nobody can read.
     *
     * Each of these pairs the protected place with an unprotected one in the same document, so the
     * test says "here and not there" rather than the much weaker "not here" — which a linker that
     * had stopped working altogether would also satisfy.
     */
    @Test
    fun `a mention inside a code block is left alone`() {
        val block = """<pre><code class="language-kotlin">val x = monthGridDates()</code></pre>"""

        val linked = linkify("$block<p><code>monthGridDates</code></p>", currentUrl = "engine.html")

        assertTrue(linked.startsWith(block))
        assertTrue(linked.endsWith("""<p><code><a class="api-link" href="$gridHref">monthGridDates</a></code></p>"""))
    }

    @Test
    fun `a mention the author already linked keeps the link they chose`() {
        val chosen = """<a href="somewhere.html"><code>monthGridDates</code></a>"""

        val linked = linkify("<p>$chosen and <code>monthGridDates</code></p>", currentUrl = "engine.html")

        assertTrue(linked.contains(chosen))
        assertTrue(linked.contains("""<code><a class="api-link" href="$gridHref">monthGridDates</a></code>"""))
    }

    @Test
    fun `a mention in a heading is left alone`() {
        val heading = """<h2 id="x"><code>monthGridDates</code><a class="anchor" href="#x">#</a></h2>"""

        val linked = linkify("$heading<p><code>monthGridDates</code></p>", currentUrl = "engine.html")

        assertTrue(linked.startsWith(heading))
        assertTrue(linked.contains("""class="api-link""""))
    }

    /** A link that scrolls a reader to something already on screen reads as a broken link. */
    @Test
    fun `a symbol is not linked to the page it is already on`() {
        val html = "<p><code>monthGridDates</code></p>"

        assertEquals(html, linkify(html, currentUrl = "reference/foundation-grid.html", depth = 1))
        assertTrue(linkify(html, currentUrl = "reference/kalendar-views.html", depth = 1).contains("api-link"))
    }

    @Test
    fun `an unknown code span is not touched`() {
        val html = "<p><code>somethingElse</code> and <code>Set&lt;DayOfWeek&gt;</code></p>"

        assertEquals(html, linkify(html, currentUrl = "engine.html"))
        assertTrue(linkify("<p><code>monthGridDates</code></p>", currentUrl = "engine.html").contains("api-link"))
    }

    private fun symbolsFrom(text: String): Set<String> {
        val root = Files.createTempDirectory("kalendar-api").toFile()
        root.deleteOnExit()
        val module = root.resolve("kalendar/api/desktop")
        module.mkdirs()
        module.resolve("kalendar.api").writeText(text)
        return readApiSymbols(root)
    }

    private fun reference(
        path: String,
        vararg headings: String
    ): Map<DocSource, RenderedPage> {
        val markdown = headings.joinToString("\n\n") { heading -> "## `$heading`" }
        val source =
            DocSource(
                path = path,
                url = path.toSiteUrl(),
                directory = path.substringBeforeLast('/', ""),
                markdown = markdown,
                title = path
            )
        return mapOf(source to renderMarkdown(markdown))
    }

    private fun index(pages: Map<DocSource, RenderedPage>): Map<String, SymbolTarget> =
        buildSymbolIndex(pages = pages, apiSymbols = symbols)

    private fun linkify(
        html: String,
        currentUrl: String,
        depth: Int = 0
    ): String =
        linkifySymbols(
            html = html,
            currentUrl = currentUrl,
            depth = depth,
            symbols = index(reference("reference/foundation-grid.md", "monthGridDates"))
        )
}
