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

import androidx.compose.ui.graphics.Color

/**
 * The playground's own chrome — the panels, chips and code block around the calendar.
 *
 * Strictly monochrome, and mirroring the documentation site's tokens so the two read as one
 * product. The rule the palette exists to enforce: the only colour on the page belongs to the
 * calendar being demonstrated. A violet "selected" chip would be the loudest thing on screen and
 * would compete with the very output the playground is there to show, so selection is expressed as
 * a near-white fill instead.
 *
 * The accent swatch row is the deliberate exception: those circles *are* the library's palette.
 */
@Suppress("MagicNumber")
internal object PlaygroundChrome {
    val Background = Color(0xFF000000)
    val Surface = Color(0xFF0A0A0A)
    val Border = Color(0x14FFFFFF)
    val BorderStrong = Color(0x29FFFFFF)
    val Text = Color(0xFFEDEDED)
    val MutedText = Color(0xFFA1A1A1)
    val DimText = Color(0xFF707070)
    val Accent = Color(0xFFEDEDED)
    val OnAccent = Color(0xFF0A0A0A)
    val CodeBackground = Color(0xFF0A0A0A)
    val CodeText = Color(0xFFD4D4D4)
}
