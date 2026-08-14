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

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.dp
import com.himanshoe.kalendar.component.KEYBOARD_FOCUS_RING_TAG
import com.himanshoe.kalendar.state.rememberKalendarWeekState
import com.himanshoe.kalendar.theme.KalendarAnimations
import com.himanshoe.kalendar.theme.KalendarTheme
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertTrue

/**
 * A Mon–Fri work-week calendar: the grid views with [KalendarViewConfig.visibleDaysOfWeek] narrowed.
 *
 * Hiding a column is not a matter of not drawing two cells — it changes the column count, and with
 * it the header row, the month grid's leading padding, and what an arrow key means. Each of those is
 * asserted separately, because a partial implementation renders a plausible-looking grid whose dates
 * sit under the wrong labels.
 */
@OptIn(ExperimentalTestApi::class)
class KalendarWorkWeekUiTest {

    @get:Rule
    val rule = createComposeRule()

    private val workWeek = KalendarViewConfig(
        showNavigationArrows = false,
        showTodayButton = false,
        showJumpPicker = false,
        visibleDaysOfWeek = setOf(
            DayOfWeek.MONDAY,
            DayOfWeek.TUESDAY,
            DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY,
            DayOfWeek.FRIDAY,
        ),
    )

    /** 2026-03-13 is a Friday; the 14th and 15th are the weekend either side of it. */
    private val friday = LocalDate(YEAR, MONTH, FRIDAY_13TH)

    private val focusEntry = FocusRequester()

    @Composable
    private fun Harness(content: @Composable () -> Unit) {
        Column {
            Box(modifier = Modifier.size(PROBE_SIZE).focusRequester(focusEntry).focusable())
            Box(modifier = Modifier.size(width = CALENDAR_WIDTH, height = CALENDAR_HEIGHT)) {
                content()
            }
        }
    }

    private fun setWeek() {
        rule.setContent {
            KalendarTheme(animations = KalendarAnimations(enabled = false)) {
                Harness {
                    KalendarWeek(
                        selectedDate = friday,
                        state = rememberKalendarWeekState(initialDate = friday),
                        config = workWeek,
                    )
                }
            }
        }
        rule.runOnIdle { focusEntry.requestFocus() }
        rule.waitForIdle()
    }

    private fun press(key: Key) {
        rule.onRoot().performKeyInput { pressKey(key) }
        rule.waitForIdle()
    }

    private fun ringBounds(): Rect =
        rule.onNodeWithTag(KEYBOARD_FOCUS_RING_TAG).fetchSemanticsNode().boundsInRoot

    private fun assertRingOn(date: String) {
        val ring = ringBounds()
        val target = rule.onNode(hasContentDescription(date) and hasClickAction())
            .fetchSemanticsNode()
            .boundsInRoot
        assertTrue(ring.overlaps(target), "focus ring at $ring should sit over the cell for $date at $target")
    }

    @Test
    fun theWeekRowDropsTheWeekendCells() {
        setWeek()

        rule.onNodeWithContentDescription("March 13, 2026").assertExists()
        rule.onNodeWithContentDescription("March 9, 2026").assertExists()
        // The 14th is a Saturday and the 15th a Sunday: neither has a column.
        rule.onNodeWithContentDescription("March 14, 2026").assertDoesNotExist()
        rule.onNodeWithContentDescription("March 15, 2026").assertDoesNotExist()
    }

    @Test
    fun theDayOfWeekHeaderRowDropsTheWeekendLabels() {
        setWeek()

        // The default label formatter is the day name's first letter, so "S" belongs to Saturday and
        // Sunday alone: if either label survived the filter, this finds it.
        rule.onNodeWithText("M").assertExists()
        rule.onNodeWithText("F").assertExists()
        rule.onNodeWithText("S").assertDoesNotExist()
    }

    @Test
    fun arrowingRightOffFridayLandsOnMondayRatherThanSaturday() {
        setWeek()
        press(Key.Tab)
        assertRingOn("March 13, 2026")

        press(Key.DirectionRight)

        assertRingOn("March 16, 2026")
    }

    @Test
    fun arrowingLeftOffMondayLandsOnThepreviousFriday() {
        setWeek()
        press(Key.Tab)
        press(Key.DirectionRight)
        assertRingOn("March 16, 2026")

        press(Key.DirectionLeft)

        assertRingOn("March 13, 2026")
    }

    @Test
    fun theMonthGridKeepsItsDatesUnderTheRightColumns() {
        rule.setContent {
            KalendarTheme(animations = KalendarAnimations(enabled = false)) {
                Box(modifier = Modifier.size(width = CALENDAR_WIDTH, height = CALENDAR_HEIGHT)) {
                    KalendarMonth(selectedDate = friday, config = workWeek)
                }
            }
        }

        val mondayLabel = rule.onNodeWithText("M").fetchSemanticsNode().boundsInRoot
        val mondayCell = rule.onNode(hasContentDescription("March 9, 2026") and hasClickAction())
            .fetchSemanticsNode()
            .boundsInRoot

        rule.onNodeWithContentDescription("March 14, 2026").assertDoesNotExist()
        assertTrue(
            mondayCell.left >= mondayLabel.left - COLUMN_TOLERANCE_PX &&
                mondayCell.right <= mondayLabel.right + COLUMN_TOLERANCE_PX,
            "the 9th is a Monday, so its cell at $mondayCell must sit in the M column at $mondayLabel",
        )
    }

    @Test
    fun theScheduleWeekGridDropsItsWeekendColumns() {
        rule.setContent {
            KalendarScheduleWeek(
                state = rememberKalendarWeekState(initialDate = friday),
                config = workWeek,
            )
        }

        // The day-column headers show the day of month; the weekend's numbers must be absent.
        rule.onNodeWithText("13").assertExists()
        rule.onNodeWithText("14").assertDoesNotExist()
        rule.onNodeWithText("15").assertDoesNotExist()
    }

    private companion object {
        const val YEAR = 2026
        const val MONTH = 3
        const val FRIDAY_13TH = 13
        const val COLUMN_TOLERANCE_PX = 1f
        val PROBE_SIZE = 8.dp
        val CALENDAR_WIDTH = 420.dp
        val CALENDAR_HEIGHT = 480.dp
    }
}
