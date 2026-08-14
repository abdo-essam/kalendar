package com.himanshoe.docsite

private val keywords =
    setOf(
        "abstract", "actual", "annotation", "as", "break", "by", "catch", "class", "companion",
        "const", "constructor", "continue", "crossinline", "data", "do", "else", "enum", "expect",
        "external", "false", "final", "finally", "for", "fun", "get", "if", "import", "in",
        "infix", "init", "inline", "inner", "interface", "internal", "is", "it", "lateinit",
        "noinline", "null", "object", "open", "operator", "out", "override", "package", "private",
        "protected", "public", "reified", "return", "sealed", "set", "super", "suspend", "tailrec",
        "this", "throw", "true", "try", "typealias", "val", "value", "var", "vararg", "when",
        "where", "while"
    )

/**
 * One run of source text and what it should be coloured as.
 *
 * The rules are tried in order at each position and the first match wins, which is what keeps a
 * keyword inside a string from being coloured: the string rule is tried first and consumes the whole
 * literal before the keyword rule ever sees it.
 */
private data class Rule(
    val cssClass: String,
    val pattern: Regex
)

private const val CLASS_KEYWORD = "tok-kw"
private const val CLASS_TYPE = "tok-type"
private const val CLASS_PLAIN = ""

private val rules =
    listOf(
        // Entities first, so nothing downstream can take `lt` out of `&lt;` and leave broken markup.
        Rule(cssClass = CLASS_PLAIN, pattern = Regex("""&(?:amp|lt|gt|quot|#\d+);""")),
        Rule(cssClass = "tok-comment", pattern = Regex("""//[^\n]*|/\*[\s\S]*?\*/""")),
        Rule(cssClass = "tok-str", pattern = Regex("\"\"\"[\\s\\S]*?\"\"\"")),
        Rule(cssClass = "tok-str", pattern = Regex(""""(?:[^"\\\n]|\\.)*"""")),
        Rule(cssClass = "tok-str", pattern = Regex("""'(?:[^'\\\n]|\\.)*'""")),
        Rule(cssClass = "tok-anno", pattern = Regex("""@\w+""")),
        Rule(cssClass = "tok-num", pattern = Regex("""\b\d[\d_]*(?:\.\d+)?(?:[eE][+-]?\d+)?[fFLuU]*\b""")),
        Rule(cssClass = CLASS_KEYWORD, pattern = Regex("""\b[a-zA-Z_]\w*\b"""))
    )

/**
 * Colours a Kotlin snippet that has **already been HTML-escaped**.
 *
 * Highlighting escaped text rather than raw source is deliberate. Escaping afterwards would have to
 * escape the markup this function just produced; escaping first means the only thing left to do is
 * wrap runs in spans, and no snippet can smuggle a tag through into the page. The entity rule above
 * is the other half of that bargain — once `<` is `&lt;`, the tokenizer has to be told to leave the
 * entity alone.
 */
fun highlightKotlin(escaped: String): String {
    val output = StringBuilder(escaped.length)
    var index = 0
    while (index < escaped.length) {
        val match = firstMatchAt(escaped = escaped, index = index)
        if (match == null) {
            output.append(escaped[index])
            index++
        } else {
            output.append(renderToken(rule = match.first, text = match.second))
            index += match.second.length
        }
    }
    return output.toString()
}

/** The first rule that matches exactly at [index], with the text it consumed. */
private fun firstMatchAt(
    escaped: String,
    index: Int
): Pair<Rule, String>? =
    rules.firstNotNullOfOrNull { rule ->
        rule.pattern
            .matchAt(escaped, index)
            ?.value
            ?.takeIf { value -> value.isNotEmpty() }
            ?.let { value -> rule to value }
    }

/**
 * Wraps a token, except for identifiers that turned out not to be keywords.
 *
 * The keyword rule matches every word so that a word is consumed whole — otherwise `internal` would
 * match `in` and leave `ternal` behind to be matched again. Words that are not keywords are emitted
 * plain, and a capitalised one is treated as a type, which is close enough to right in Kotlin to be
 * worth the colour and never actively misleading.
 */
private fun renderToken(
    rule: Rule,
    text: String
): String {
    val effectiveClass =
        when {
            rule.cssClass != CLASS_KEYWORD -> rule.cssClass
            text in keywords -> CLASS_KEYWORD
            text.first().isUpperCase() -> CLASS_TYPE
            else -> CLASS_PLAIN
        }
    return if (effectiveClass.isEmpty()) {
        text
    } else {
        """<span class="$effectiveClass">$text</span>"""
    }
}
