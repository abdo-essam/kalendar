package com.himanshoe.docsite

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The highlighter runs over text that has already been HTML-escaped, which is the arrangement that
 * makes it impossible for a snippet to inject markup — and also the arrangement that breaks if the
 * tokenizer ever treats an entity as ordinary characters. These are the cases that would let either
 * half of that bargain fail silently.
 */
class HighlightTest {
    @Test
    fun `keywords are coloured and ordinary identifiers are not`() {
        val painted = highlightKotlin(escapeText("val today = current"))
        assertEquals("""<span class="tok-kw">val</span> today = current""", painted)
    }

    @Test
    fun `capitalised identifiers are treated as types`() {
        val painted = highlightKotlin(escapeText("KalendarMonth(selectedDate)"))
        assertEquals("""<span class="tok-type">KalendarMonth</span>(selectedDate)""", painted)
    }

    @Test
    fun `a keyword inside a string stays part of the string`() {
        val painted = highlightKotlin(escapeText(""""val is a keyword""""))
        assertEquals("""<span class="tok-str">"val is a keyword"</span>""", painted)
    }

    @Test
    fun `a keyword inside a comment stays part of the comment`() {
        val painted = highlightKotlin(escapeText("// return null"))
        assertEquals("""<span class="tok-comment">// return null</span>""", painted)
    }

    @Test
    fun `annotations and numbers are recognised`() {
        assertTrue(highlightKotlin(escapeText("@Composable")).contains("""<span class="tok-anno">@Composable</span>"""))
        assertTrue(highlightKotlin(escapeText("24")).contains("""<span class="tok-num">24</span>"""))
    }

    @Test
    fun `generics survive escaping intact`() {
        val painted = highlightKotlin(escapeText("List<String>"))
        assertEquals("""<span class="tok-type">List</span>&lt;<span class="tok-type">String</span>&gt;""", painted)
    }

    @Test
    fun `an ampersand is escaped once and never re-tokenised`() {
        val painted = highlightKotlin(escapeText("a && b"))
        assertEquals("a &amp;&amp; b", painted)
        assertFalse(painted.contains("&amp;amp;"))
    }

    @Test
    fun `markup in a snippet cannot escape into the page`() {
        val painted = highlightKotlin(escapeText("""val x = "<script>alert(1)</script>""""))
        assertFalse(painted.contains("<script"))
        assertTrue(painted.contains("&lt;script&gt;"))
    }

    /**
     * The markdown parser escapes quotes inside a fence and [escapeText] does not, so a code window
     * has to cope with both. When it did not, every string literal in the docs went uncoloured and
     * the words inside it were highlighted as code.
     */
    @Test
    fun `a string escaped by the markdown parser is still a string`() {
        val window = renderCodeWindow(language = "kotlin", code = "val a = &quot;value&quot;")
        assertTrue(window.contains("""<span class="tok-str">"value"</span>"""), window)
    }

    @Test
    fun `a string escaped by escapeText is still a string`() {
        val window = renderCodeWindow(language = "kotlin", code = escapeText("""val a = "value""""))
        assertTrue(window.contains("""<span class="tok-str">"value"</span>"""), window)
    }

    @Test
    fun `an escaped ampersand is not mistaken for a quote entity`() {
        val window = renderCodeWindow(language = "kotlin", code = escapeText("""val a = "&quot;""""))
        assertTrue(window.contains("&amp;quot;"), window)
    }

    @Test
    fun `every character of the input survives`() {
        val source = "fun f(): Map<String, Int> = mapOf(\"a\" to 1) // done"
        val stripped =
            highlightKotlin(escapeText(source))
                .replace(Regex("""</?span[^>]*>"""), "")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&amp;", "&")
        assertEquals(source, stripped)
    }
}
