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

package com.himanshoe.kalendar.view

import androidx.compose.runtime.Composable
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import androidx.compose.ui.use
import kotlinx.datetime.LocalDate
import org.jetbrains.skia.EncodedImageFormat
import org.junit.Rule
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The scenario the library claims to support after dropping Material widgets from the views: a
 * calendar composed with **no `MaterialTheme` anywhere** and a fully custom [KalendarTheme].
 *
 * Every other UI and golden test in this module wraps its content in a `MaterialTheme`, so nothing
 * else would notice a view quietly falling back to `LocalContentColor`, `LocalTextStyle`, or a
 * Material default — all of which resolve to *something* under a Material theme and would keep
 * those tests green. These tests therefore assert on rendered pixels rather than on semantics
 * alone: a token that failed to reach the renderer shows up as the wrong colour on screen.
 */
class KalendarNoMaterialThemeTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun noMaterialTheme_customColorsPaintTheCalendar() {
        val pixels = renderMonth(typography = brandTypography(dayNumberSize = DAY_NUMBER_SIZE))

        assertEquals(
            BRAND_BACKGROUND.toArgbInt(),
            pixels.colorAt(x = CORNER_PROBE, y = CORNER_PROBE),
            "the calendar's fill must come from KalendarColors.background",
        )
        assertTrue(
            pixels.count(BRAND_DAY_CONTENT) > 0,
            "day numbers must be drawn in KalendarColors.dayContent",
        )
        assertTrue(
            pixels.count(BRAND_SELECTION) > 0,
            "the selected day's fill must come from KalendarColors.selectionBackground",
        )
        assertTrue(
            pixels.count(BRAND_DAY_LABEL) > 0,
            "the day-of-week labels must be drawn in KalendarColors.dayLabelContent",
        )
        assertTrue(
            pixels.count(BRAND_HEADER_CONTENT) > 0,
            "the header title and its drawn icons must use KalendarColors.headerContent",
        )
    }

    /**
     * The negative half of the assertion above: no Material colour may appear on screen. Without
     * this, a view that ignored its token and fell back to a Material default would still paint
     * *some* colour and the positive assertions alone could not tell the difference.
     */
    @Test
    fun noMaterialTheme_noMaterialDefaultColorLeaksIntoTheRender() {
        val pixels = renderMonth(typography = brandTypography(dayNumberSize = DAY_NUMBER_SIZE))

        assertEquals(
            0,
            pixels.count(MATERIAL_BASELINE_SURFACE),
            "Material's baseline surface colour must never be painted without a MaterialTheme",
        )
        assertEquals(
            0,
            pixels.count(MATERIAL_BASELINE_PRIMARY),
            "Material's baseline primary colour must never be painted without a MaterialTheme",
        )
        assertEquals(
            0,
            pixels.count(MATERIAL_LOCAL_CONTENT_COLOR),
            "text must never fall through to LocalContentColor, which defaults to opaque black",
        )
    }

    /**
     * [KalendarTypography] has to reach the renderer, not just the token holder: the same calendar
     * drawn with a larger `dayNumber` must put visibly more ink on screen. Counting pixels of the
     * custom `dayContent` colour measures exactly that, and is immune to font-metric differences
     * between platforms in a way that asserting an absolute glyph size would not be.
     */
    @Test
    fun noMaterialTheme_customTypographyChangesRenderedText() {
        val small = renderMonth(typography = brandTypography(dayNumberSize = SMALL_DAY_NUMBER_SIZE))
        val large = renderMonth(typography = brandTypography(dayNumberSize = LARGE_DAY_NUMBER_SIZE))

        val smallInk = small.count(BRAND_DAY_CONTENT)
        val largeInk = large.count(BRAND_DAY_CONTENT)
        assertTrue(smallInk > 0, "day numbers must render at the small size too")
        assertTrue(
            largeInk > smallInk * INK_GROWTH_FACTOR,
            "a larger KalendarTypography.dayNumber must draw substantially more ink: " +
                "small=$smallInk large=$largeInk",
        )
    }

    /**
     * The header's accessibility labels come from [KalendarStrings], so a non-English app can
     * localize them. Before this wiring the header hardcoded English regardless of the token.
     */
    @Test
    fun noMaterialTheme_customStringsLabelTheHeaderControls() {
        rule.setContent {
            KalendarTheme(
                colors = brandColors(),
                typography = brandTypography(dayNumberSize = DAY_NUMBER_SIZE),
                animations = KalendarAnimations(enabled = false),
                strings = KalendarStrings(
                    previousPage = DUTCH_PREVIOUS,
                    nextPage = DUTCH_NEXT,
                    today = DUTCH_TODAY,
                    previousYear = DUTCH_PREVIOUS_YEAR,
                    openJumpPicker = DUTCH_OPEN_PICKER,
                ),
            ) {
                KalendarMonth(selectedDate = MARCH_TENTH)
            }
        }

        rule.onNodeWithContentDescription(DUTCH_PREVIOUS).assertIsDisplayed()
        rule.onNodeWithContentDescription(DUTCH_NEXT).assertIsDisplayed()
        rule.onNodeWithContentDescription(DUTCH_TODAY).assertIsDisplayed()

        rule.onNodeWithText(MARCH_TITLE).performClick()
        rule.onNodeWithContentDescription(DUTCH_PREVIOUS_YEAR).assertIsDisplayed()
    }

    /** The calendar still behaves — not merely paints — with no Material theme in the tree. */
    @Test
    fun noMaterialTheme_calendarStillNavigates() {
        rule.setContent {
            KalendarTheme(
                colors = brandColors(),
                typography = brandTypography(dayNumberSize = DAY_NUMBER_SIZE),
                animations = KalendarAnimations(enabled = false),
            ) {
                KalendarMonth(selectedDate = MARCH_TENTH)
            }
        }

        rule.onNodeWithText(MARCH_TITLE).assertIsDisplayed()
        rule.onNodeWithContentDescription(DEFAULT_NEXT_LABEL).performClick()
        rule.waitForIdle()

        rule.onNodeWithText(APRIL_TITLE).assertIsDisplayed()
    }

    private fun renderMonth(typography: KalendarTypography): RenderedPixels = renderToPixels {
        KalendarTheme(
            colors = brandColors(),
            typography = typography,
            animations = KalendarAnimations(enabled = false),
        ) {
            KalendarMonth(selectedDate = MARCH_TENTH)
        }
    }

    private fun renderToPixels(content: @Composable () -> Unit): RenderedPixels {
        val png = ImageComposeScene(width = WIDTH, height = HEIGHT, density = Density(1f)) {
            content()
        }.use { scene ->
            scene.render(nanoTime = 0L)
            val settled = scene.render(nanoTime = SETTLE_FRAME_NANOS)
            checkNotNull(settled.encodeToData(EncodedImageFormat.PNG)) { "PNG encode failed" }.bytes
        }
        val image = ImageIO.read(png.inputStream())
        val argb = IntArray(image.width * image.height)
        for (y in 0 until image.height) {
            for (x in 0 until image.width) {
                argb[y * image.width + x] = image.getRGB(x, y)
            }
        }
        return RenderedPixels(argb = argb, width = image.width)
    }

    /** The rendered frame as ARGB ints, so a test can ask which colours actually reached the screen. */
    private class RenderedPixels(private val argb: IntArray, private val width: Int) {

        fun colorAt(x: Int, y: Int): Int = argb[y * width + x]

        /** How many pixels are [color], within [CHANNEL_TOLERANCE] per channel for antialiasing. */
        fun count(color: Color): Int {
            val target = color.toArgbInt()
            return argb.count { pixel ->
                CHANNEL_SHIFTS.all { shift ->
                    val a = (pixel shr shift) and CHANNEL_MASK
                    val b = (target shr shift) and CHANNEL_MASK
                    kotlin.math.abs(a - b) <= CHANNEL_TOLERANCE
                }
            }
        }
    }

    private fun brandColors(): KalendarColors = KalendarColors(
        background = BRAND_BACKGROUND,
        selectionBackground = BRAND_SELECTION,
        onSelectionBackground = BRAND_ON_SELECTION,
        todayContent = BRAND_TODAY,
        dayContent = BRAND_DAY_CONTENT,
        dayLabelContent = BRAND_DAY_LABEL,
        headerContent = BRAND_HEADER_CONTENT,
        eventIndicator = BRAND_EVENT_INDICATOR,
        nowIndicator = BRAND_NOW_INDICATOR,
        gridLine = BRAND_GRID_LINE,
        dragGhostBackground = BRAND_DRAG_GHOST,
        popupBackground = BRAND_POPUP_BACKGROUND,
        popupBorder = BRAND_POPUP_BORDER,
    )

    /**
     * A type scale built from [TextStyle] alone — no `MaterialTheme.typography` in sight, which is
     * the whole point: a design system that has never heard of Material can supply one.
     */
    private fun brandTypography(dayNumberSize: androidx.compose.ui.unit.TextUnit): KalendarTypography =
        KalendarTypography(
            headerTitle = TextStyle(fontSize = HEADER_TITLE_SIZE),
            monthLabel = TextStyle(fontSize = MONTH_LABEL_SIZE),
            dayOfWeekLabel = TextStyle(fontSize = DAY_LABEL_SIZE),
            dayNumber = TextStyle(fontSize = dayNumberSize),
            dayNumberEmphasized = TextStyle(fontSize = dayNumberSize),
            weekDayNumber = TextStyle(fontSize = dayNumberSize),
            eventLabel = TextStyle(fontSize = EVENT_LABEL_SIZE),
            eventOverflowLabel = TextStyle(fontSize = EVENT_LABEL_SIZE),
            hourLabel = TextStyle(fontSize = EVENT_LABEL_SIZE),
        )

    private companion object {
        val MARCH_TENTH = LocalDate(2026, 3, 10)
        const val MARCH_TITLE = "March 2026"
        const val APRIL_TITLE = "April 2026"
        const val DEFAULT_NEXT_LABEL = "Next"

        const val DUTCH_PREVIOUS = "Vorige maand"
        const val DUTCH_NEXT = "Volgende maand"
        const val DUTCH_TODAY = "Vandaag"
        const val DUTCH_PREVIOUS_YEAR = "Vorig jaar"
        const val DUTCH_OPEN_PICKER = "Kies maand en jaar"

        const val WIDTH = 420
        const val HEIGHT = 480
        const val CORNER_PROBE = 2
        const val SETTLE_FRAME_NANOS = 1_000_000_000L
        const val CHANNEL_TOLERANCE = 6
        const val CHANNEL_MASK = 0xFF
        val CHANNEL_SHIFTS = intArrayOf(0, 8, 16, 24)

        /** A larger day number must put at least this many times more ink on screen. */
        const val INK_GROWTH_FACTOR = 2

        val DAY_NUMBER_SIZE = 20.sp
        val SMALL_DAY_NUMBER_SIZE = 9.sp
        val LARGE_DAY_NUMBER_SIZE = 26.sp
        val HEADER_TITLE_SIZE = 18.sp
        val MONTH_LABEL_SIZE = 14.sp
        val DAY_LABEL_SIZE = 12.sp
        val EVENT_LABEL_SIZE = 10.sp

        // Saturated, mutually distinguishable colours so a pixel count cannot be ambiguous about
        // which token produced it.
        val BRAND_BACKGROUND = Color(0xFF002B36)
        val BRAND_SELECTION = Color(0xFFB58900)
        val BRAND_ON_SELECTION = Color(0xFF002B36)
        val BRAND_TODAY = Color(0xFFD33682)
        val BRAND_DAY_CONTENT = Color(0xFF00FF00)
        val BRAND_DAY_LABEL = Color(0xFF2AA198)
        val BRAND_HEADER_CONTENT = Color(0xFFFF00FF)
        val BRAND_EVENT_INDICATOR = Color(0xFFCB4B16)
        val BRAND_NOW_INDICATOR = Color(0xFFDC322F)
        val BRAND_GRID_LINE = Color(0xFF073642)
        val BRAND_DRAG_GHOST = Color(0xFF586E75)
        val BRAND_POPUP_BACKGROUND = Color(0xFF073642)
        val BRAND_POPUP_BORDER = Color(0xFF586E75)

        val MATERIAL_BASELINE_SURFACE = Color(0xFFFFFBFE)
        val MATERIAL_BASELINE_PRIMARY = Color(0xFF6750A4)
        val MATERIAL_LOCAL_CONTENT_COLOR = Color(0xFF000000)

        fun Color.toArgbInt(): Int {
            val alpha = (alpha * MAX_CHANNEL).toInt()
            val red = (red * MAX_CHANNEL).toInt()
            val green = (green * MAX_CHANNEL).toInt()
            val blue = (blue * MAX_CHANNEL).toInt()
            return (alpha shl ALPHA_SHIFT) or (red shl RED_SHIFT) or (green shl GREEN_SHIFT) or blue
        }

        const val MAX_CHANNEL = 255f
        const val ALPHA_SHIFT = 24
        const val RED_SHIFT = 16
        const val GREEN_SHIFT = 8
    }
}
