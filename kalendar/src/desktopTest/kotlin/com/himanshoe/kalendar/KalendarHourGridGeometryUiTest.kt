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

package com.himanshoe.kalendar

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.himanshoe.kalendar.foundation.event.BasicKalendarEvent
import com.himanshoe.kalendar.foundation.schedule.KalendarHourWindow
import com.himanshoe.kalendar.slot.KalendarScheduleEventScope
import com.himanshoe.kalendar.state.rememberKalendarScheduleState
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import org.junit.Rule
import org.junit.Test
import kotlin.math.abs
import kotlin.test.assertTrue

/**
 * The hour grid's vertical scale, asserted directly rather than through a golden.
 *
 * The gutter, the hour rules and the block layout all measure from the same origin and the same
 * minutes-per-pixel, but only one of them — the block layout — computes it in a measure policy. The
 * other two build it out of a stack of fixed-height boxes, which is the kind of thing a refactor can
 * silently shift by a row. A one-row shift is a fraction of a percent of the pixels in a golden, so
 * the golden would not catch it; these assertions would.
 */
@Suppress("MagicNumber")
class KalendarHourGridGeometryUiTest {

    @get:Rule
    val rule = createComposeRule()

    private val day = LocalDate(2026, 3, 10)
    private val hourHeight = 64.dp
    private val businessHours = KalendarViewConfig(
        scheduleVisibleHours = KalendarHourWindow(startHour = OPENING_HOUR, endHour = CLOSING_HOUR),
        scheduleInitialScrollHour = OPENING_HOUR,
    )

    /** Fills its slot, so the measured node is the block the grid positioned. */
    private val blockProbe: @Composable (KalendarScheduleEventScope) -> Unit = { scope ->
        Box(modifier = Modifier.fillMaxSize().testTag(scope.event.eventName))
    }

    @Test
    fun gutterRowsAreOneHourHighAndEvenlySpaced() {
        showSchedule()

        val first = labelTop("08:00")
        val second = labelTop("09:00")
        val third = labelTop("10:00")

        assertClose(hourHeight, second - first, "the gutter's first row is not one hour tall")
        assertClose(second - first, third - second, "the gutter's rows are not evenly spaced")
    }

    /**
     * The gutter and the grid share an origin: a 09:00 block on an 08:00 grid sits exactly one hour
     * below the 08:00 label, and is exactly one hour tall.
     *
     * This is the assertion that fails if the hour rules and the block layout ever disagree about
     * where the top of the window is — a grid whose rules are drawn one row off from its events.
     */
    @Test
    fun aBlockSitsAtItsOwnHourOnTheSameScaleAsTheGutter() {
        showSchedule()

        val gridTop = labelTop("08:00")
        val block = rule.onNodeWithTag("Standup", useUnmergedTree = true).getUnclippedBoundsInRoot()

        assertClose(hourHeight, block.top - gridTop, "a 09:00 block is not one hour below the grid's top")
        assertClose(hourHeight, block.bottom - block.top, "a one-hour block is not one hour tall")
    }

    private fun showSchedule() {
        rule.setContent {
            KalendarSchedule(
                state = rememberKalendarScheduleState(initialDate = day),
                events = listOf(
                    BasicKalendarEvent(
                        date = day,
                        eventName = "Standup",
                        startTime = LocalDateTime(day.year, day.month, day.day, BLOCK_HOUR, 0),
                        endTime = LocalDateTime(day.year, day.month, day.day, BLOCK_HOUR + 1, 0),
                    ),
                ),
                config = businessHours,
                hourHeight = hourHeight,
                eventContent = blockProbe,
            )
        }
        rule.waitForIdle()
    }

    private fun labelTop(text: String): Dp = rule.onNodeWithText(text).getUnclippedBoundsInRoot().top

    private fun assertClose(expected: Dp, actual: Dp, message: String) {
        assertTrue(abs((expected - actual).value) <= TOLERANCE, "$message: expected $expected, was $actual")
    }

    private companion object {
        const val OPENING_HOUR = 8
        const val CLOSING_HOUR = 20
        const val BLOCK_HOUR = 9

        /** One device pixel of rounding at the densities these tests run at. */
        const val TOLERANCE = 1f
    }
}
