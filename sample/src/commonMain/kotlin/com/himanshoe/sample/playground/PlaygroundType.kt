/*
 *
 *  * Copyright 2026 Kalendar Contributors (https://www.himanshoe.com). All rights reserved.
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  * http://www.apache.org/licenses/LICENSE-2.0
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *  *
 *
 */

package com.himanshoe.sample.playground

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp

/**
 * The demo shell's own type scale — six roles, each one step apart, so the page reads as a product
 * rather than as a debug panel.
 *
 * The panel used to set every string at 11–12sp and let colour alone carry the hierarchy, which put
 * a section heading, its caption, a group label and a slider's readout at the same size. Here size,
 * weight and colour move together: an eyebrow is small, tracked and muted; a caption is the same
 * size but untracked, unemphasised and dimmer; the things a visitor actually reads and clicks —
 * control labels and chips — are the largest text in the panel.
 *
 * Every style names its own `lineHeight`. Left to the default, a multi-line label such as a wrapped
 * switch caption set its own leading from the font's metrics and came out cramped next to a
 * neighbouring single-line row.
 *
 * These are the *chrome's* styles. Nothing here reaches the calendar: that is themed by
 * [com.himanshoe.kalendar.theme.KalendarTheme] and is exactly what the controls exist to change.
 */
internal object PlaygroundType {

    /**
     * All-caps section headings. Tracked at 0.11em: an uppercase run set solid reads as a single
     * word, and the 0.145em the panel used before had the letters drifting apart instead.
     */
    val Eyebrow = TextStyle(
        fontSize = 12.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.3.sp,
        lineHeightStyle = TrimmedLineHeight,
    )

    /** One sentence under a section heading, saying what the section is for. */
    val Caption = TextStyle(
        fontSize = 12.sp,
        lineHeight = 17.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.sp,
        lineHeightStyle = TrimmedLineHeight,
    )

    /** Names a group of controls inside a section — "Selection", "Accent", "Day cell shape". */
    val GroupLabel = TextStyle(
        fontSize = 13.sp,
        lineHeight = 18.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.sp,
        lineHeightStyle = TrimmedLineHeight,
    )

    /** The label on a single slider or switch: the most-read text in the panel. */
    val ControlLabel = TextStyle(
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.sp,
        lineHeightStyle = TrimmedLineHeight,
    )

    /**
     * The API name a control maps onto, under its label. Monospaced because it is source, which is
     * also what stops it being mistaken for more prose.
     */
    val ControlHint = TextStyle(
        fontSize = 11.5.sp,
        lineHeight = 15.sp,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.sp,
        lineHeightStyle = TrimmedLineHeight,
    )

    /** A slider's current value, read against its label across the row. */
    val Value = TextStyle(
        fontSize = 13.sp,
        lineHeight = 18.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.sp,
        lineHeightStyle = TrimmedLineHeight,
    )

    /**
     * A chip in a choice row. One weight for every state: swapping to a heavier face on selection
     * changed the chip's width, and in a [androidx.compose.foundation.layout.FlowRow] that re-wrapped
     * the whole row on every click.
     */
    val Chip = TextStyle(
        fontSize = 13.sp,
        lineHeight = 17.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.sp,
        lineHeightStyle = TrimmedLineHeight,
    )

    /** The page wordmark. Tightened, as display sizes want less tracking than body sizes. */
    val Wordmark = TextStyle(
        fontSize = 23.sp,
        lineHeight = 28.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.4).sp,
        lineHeightStyle = TrimmedLineHeight,
    )

    /** The line under the wordmark that says what this page is. */
    val Tagline = TextStyle(
        fontSize = 13.sp,
        lineHeight = 18.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.sp,
        lineHeightStyle = TrimmedLineHeight,
    )

    /** A link chip in the page header. */
    val Link = TextStyle(
        fontSize = 13.sp,
        lineHeight = 17.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.sp,
        lineHeightStyle = TrimmedLineHeight,
    )

    /** The generated Kotlin, and the copy button beside it. */
    val Code = TextStyle(
        fontSize = 12.sp,
        lineHeight = 19.sp,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.sp,
        lineHeightStyle = TrimmedLineHeight,
    )

    /** A small button that reads as an action — "Copy", "Copied". */
    val Action = TextStyle(
        fontSize = 13.sp,
        lineHeight = 17.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.sp,
        lineHeightStyle = TrimmedLineHeight,
    )

    /** The footnote closing the page. */
    val Footnote = TextStyle(
        fontSize = 12.sp,
        lineHeight = 18.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.sp,
        lineHeightStyle = TrimmedLineHeight,
    )
}

/**
 * Leading distributed evenly above and below, and trimmed off the first and last lines.
 *
 * Without it a declared `lineHeight` pads the top of a one-line label, so a chip's text sits low in
 * its pill and a switch's label sits above its switch. With it the declared line height only ever
 * separates lines from each other, which is the only thing it is here to do.
 */
private val TrimmedLineHeight = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.Both,
)
