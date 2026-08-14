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

package com.himanshoe.kalendar.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.use
import com.himanshoe.kalendar.KalendarMonth
import com.himanshoe.kalendar.KalendarSchedule
import com.himanshoe.kalendar.KalendarWeek
import com.himanshoe.kalendar.foundation.event.BasicKalendarEvent
import com.himanshoe.kalendar.state.rememberKalendarScheduleState
import com.himanshoe.kalendar.state.rememberKalendarWeekState
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import org.jetbrains.skia.EncodedImageFormat
import org.junit.Rule
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * What the calendar *says* rather than what it shows: the announcements a screen reader reads, and
 * the one visual state that exists only to be read as "you cannot press this".
 *
 * Every assertion here covers a case where the drawn text and the useful text differ — a single
 * letter standing for a weekday, an event block whose time is implied by its position on a grid,
 * an arrow whose disabled-ness was previously invisible.
 */
class KalendarAccessibilityUiTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun dayOfWeekHeader_announcesTheFullDayNameNotItsInitial() {
        rule.setContent {
            KalendarMonth(selectedDate = MARCH_TENTH)
        }

        // The column header draws "M". Without a description that is what a screen reader says.
        rule.onNodeWithText("M").assertExists()
        rule.onNodeWithContentDescription("Monday").assertExists()
    }

    @Test
    fun dayOfWeekHeader_accessibilityLabelIsLocalizable() {
        rule.setContent {
            KalendarTheme(strings = KalendarStrings(dayOfWeekAccessibilityLabel = { "maanadag-${it.ordinal}" })) {
                KalendarMonth(selectedDate = MARCH_TENTH)
            }
        }

        rule.onNodeWithContentDescription("maanadag-0").assertExists()
    }

    @Test
    fun timedEventBlock_announcesItsTimeRange() {
        rule.setContent {
            KalendarSchedule(
                state = rememberKalendarScheduleState(initialDate = MARCH_TENTH),
                events = listOf(
                    BasicKalendarEvent(
                        date = MARCH_TENTH,
                        eventName = "Design review",
                        startTime = LocalDateTime(2026, 3, 10, 9, 0),
                        endTime = LocalDateTime(2026, 3, 10, 10, 30),
                    ),
                ),
            )
        }

        rule.onNodeWithContentDescription("Design review, 09:00 to 10:30").assertExists()
    }

    @Test
    fun allDayChip_announcesThatItIsAllDay() {
        rule.setContent {
            KalendarSchedule(
                state = rememberKalendarScheduleState(initialDate = MARCH_TENTH),
                events = listOf(BasicKalendarEvent(date = MARCH_TENTH, eventName = "Holiday")),
            )
        }

        rule.onNodeWithContentDescription("Holiday, all day").assertExists()
    }

    /**
     * A bounded calendar's end arrow used to be indistinguishable from a live one — `enabled` gated
     * the click and nothing else, so the glyph kept drawing in [KalendarColors.headerContent].
     *
     * Asserted on pixels rather than semantics because the defect was purely visual: a token that
     * never reached the renderer would leave every semantics assertion green.
     */
    @Test
    fun boundedCalendar_disabledArrowsDrawInHeaderContentDisabled() {
        val bounded = renderWeek(minDate = MARCH_TENTH, maxDate = MARCH_TENTH)
        val unbounded = renderWeek(minDate = null, maxDate = null)

        assertTrue(
            bounded.count(DISABLED_TINT) > 0,
            "both arrows are at a bound, so they must be drawn in KalendarColors.headerContentDisabled",
        )
        assertEquals(
            0,
            unbounded.count(DISABLED_TINT),
            "an unbounded calendar has no disabled arrow, so the disabled tint must never be painted",
        )
        assertTrue(
            unbounded.count(ENABLED_TINT) > bounded.count(ENABLED_TINT),
            "disabling both arrows must take their ink out of KalendarColors.headerContent",
        )
    }

    private fun renderWeek(minDate: LocalDate?, maxDate: LocalDate?): RenderedPixels = render {
        KalendarTheme(colors = probeColors()) {
            KalendarWeek(
                selectedDate = MARCH_TENTH,
                state = rememberKalendarWeekState(
                    initialDate = MARCH_TENTH,
                    minDate = minDate,
                    maxDate = maxDate,
                ),
            )
        }
    }

    /**
     * A palette whose every token is a distinct, saturated colour, so counting pixels of one names
     * exactly which token painted them. Only the three the assertions read are given meaningful
     * values; the rest are pushed away from those so they cannot be confused for them.
     */
    private fun probeColors(): KalendarColors = KalendarThemeDefaults.lightColors().copy(
        background = Color.White,
        headerContent = ENABLED_TINT,
        headerContentDisabled = DISABLED_TINT,
        dayContent = NEUTRAL_INK,
        dayLabelContent = NEUTRAL_INK,
        todayContent = NEUTRAL_INK,
    )

    private fun render(content: @Composable () -> Unit): RenderedPixels {
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
        return RenderedPixels(argb)
    }

    private class RenderedPixels(private val argb: IntArray) {

        fun count(color: Color): Int = argb.count { pixel ->
            val target = color.toArgb()
            CHANNEL_SHIFTS.all { shift ->
                val a = (pixel shr shift) and CHANNEL_MASK
                val b = (target shr shift) and CHANNEL_MASK
                kotlin.math.abs(a - b) <= CHANNEL_TOLERANCE
            }
        }
    }

    private companion object {
        val MARCH_TENTH = LocalDate(2026, 3, 10)

        val ENABLED_TINT = Color(0xFF0000FF)
        val DISABLED_TINT = Color(0xFFFF0000)

        /** Everything the assertions do not read, kept well away from the two tints above. */
        val NEUTRAL_INK = Color(0xFF102030)

        const val WIDTH = 420
        const val HEIGHT = 200
        const val SETTLE_FRAME_NANOS = 1_000_000_000L
        const val CHANNEL_TOLERANCE = 6
        const val CHANNEL_MASK = 0xFF
        val CHANNEL_SHIFTS = intArrayOf(0, 8, 16, 24)
    }
}
