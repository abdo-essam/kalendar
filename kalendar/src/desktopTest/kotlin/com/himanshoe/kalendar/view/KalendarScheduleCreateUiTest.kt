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

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import com.himanshoe.kalendar.foundation.event.BasicKalendarEvent
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Drives the two schedule editing gestures end to end: a long-press drag across empty grid space
 * creates a range, and a long-press drag on a block's top edge moves its start.
 *
 * Coordinates are taken relative to the anchor event's own block node, whose height is exactly its
 * 60-minute duration — so an offset of one block height is one hour on the grid regardless of
 * density or theme.
 */
@Suppress("MagicNumber")
class KalendarScheduleCreateUiTest {

    @get:Rule
    val rule = createComposeRule()

    private val tuesday = LocalDate(2026, 3, 10)
    private val wednesday = LocalDate(2026, 3, 11)

    /** Keeps the hours around the anchor event on screen so out-of-viewport presses cannot miss. */
    private val config = KalendarViewConfig(scheduleInitialScrollHour = 8)

    private fun anchorEvent(date: LocalDate) = BasicKalendarEvent(
        date = date,
        eventName = "Sync",
        startTime = LocalDateTime(date.year, date.month, date.day, 9, 0),
        endTime = LocalDateTime(date.year, date.month, date.day, 10, 0),
    )

    @Test
    fun scheduleView_longPressDragEmptySpace_reportsTheSweptRange() {
        var reported: Pair<LocalDateTime, LocalDateTime>? = null
        rule.setContent {
            KalendarSchedule(
                state = rememberKalendarScheduleState(initialDate = tuesday),
                events = listOf(anchorEvent(tuesday)),
                onEventCreate = { start, end -> reported = start to end },
                config = config,
            )
        }
        rule.onNodeWithText("Sync").performTouchInput {
            down(Offset(center.x, height * 1.5f))
            advanceEventTime(LONG_PRESS_MS)
            moveBy(Offset(0f, height.toFloat()))
            up()
        }
        rule.waitForIdle()

        assertEquals(
            LocalDateTime(2026, 3, 10, 10, 30) to LocalDateTime(2026, 3, 10, 11, 30),
            reported,
            "a press 90min below the 09:00 block dragged down one hour should create 10:30-11:30",
        )
    }

    @Test
    fun scheduleView_longPressDragEmptySpaceUpward_reportsTheSameRange() {
        var reported: Pair<LocalDateTime, LocalDateTime>? = null
        rule.setContent {
            KalendarSchedule(
                state = rememberKalendarScheduleState(initialDate = tuesday),
                events = listOf(anchorEvent(tuesday)),
                onEventCreate = { start, end -> reported = start to end },
                config = config,
            )
        }
        rule.onNodeWithText("Sync").performTouchInput {
            down(Offset(center.x, height * 2.5f))
            advanceEventTime(LONG_PRESS_MS)
            moveBy(Offset(0f, -height.toFloat()))
            up()
        }
        rule.waitForIdle()

        assertEquals(
            LocalDateTime(2026, 3, 10, 10, 30) to LocalDateTime(2026, 3, 10, 11, 30),
            reported,
            "dragging upward must sweep the same range as dragging down over it",
        )
    }

    @Test
    fun scheduleView_longPressWithBarelyAnyDrag_reportsTheMinimumBlock() {
        var reported: Pair<LocalDateTime, LocalDateTime>? = null
        rule.setContent {
            KalendarSchedule(
                state = rememberKalendarScheduleState(initialDate = tuesday),
                events = listOf(anchorEvent(tuesday)),
                onEventCreate = { start, end -> reported = start to end },
                config = config,
            )
        }
        rule.onNodeWithText("Sync").performTouchInput {
            down(Offset(center.x, height * 1.5f))
            advanceEventTime(LONG_PRESS_MS)
            moveBy(Offset(0f, 2f))
            up()
        }
        rule.waitForIdle()

        assertEquals(
            LocalDateTime(2026, 3, 10, 10, 30) to LocalDateTime(2026, 3, 10, 10, 45),
            reported,
            "a sweep below the minimum duration should still create a $MIN_SCHEDULE_BLOCK_MINUTES-minute block",
        )
    }

    @Test
    fun scheduleView_longPressOnAnExistingBlock_movesItInsteadOfCreating() {
        var created: Pair<LocalDateTime, LocalDateTime>? = null
        var movedStart: LocalDateTime? = null
        rule.setContent {
            KalendarSchedule(
                state = rememberKalendarScheduleState(initialDate = tuesday),
                events = listOf(anchorEvent(tuesday)),
                onEventTimeChange = { _, newStart, _ -> movedStart = newStart },
                onEventCreate = { start, end -> created = start to end },
                config = config,
            )
        }
        rule.onNodeWithText("Sync").performTouchInput {
            down(center)
            advanceEventTime(LONG_PRESS_MS)
            moveBy(Offset(0f, height.toFloat()))
            up()
        }
        rule.waitForIdle()

        assertEquals(LocalDateTime(2026, 3, 10, 10, 0), movedStart, "the block should have moved one hour later")
        assertNull(created, "a long-press starting on a block must never create an event")
    }

    @Test
    fun scheduleView_longPressDragTopStrip_movesTheStartAndKeepsTheEnd() {
        var reportedStart: LocalDateTime? = null
        var reportedEnd: LocalDateTime? = null
        rule.setContent {
            KalendarSchedule(
                state = rememberKalendarScheduleState(initialDate = tuesday),
                events = listOf(anchorEvent(tuesday)),
                onEventTimeChange = { _, newStart, newEnd ->
                    reportedStart = newStart
                    reportedEnd = newEnd
                },
                config = config,
            )
        }
        rule.onNodeWithText("Sync").performTouchInput {
            down(Offset(center.x, height * TOP_STRIP_GRAB_FRACTION))
            advanceEventTime(LONG_PRESS_MS)
            moveBy(Offset(0f, -height.toFloat()))
            up()
        }
        rule.waitForIdle()

        assertEquals(LocalDateTime(2026, 3, 10, 8, 0), reportedStart, "the top edge should follow the drag")
        assertEquals(LocalDateTime(2026, 3, 10, 10, 0), reportedEnd, "a top resize must not move the end")
    }

    @Test
    fun scheduleView_longPressDragTopStripPastTheEnd_stopsAtTheMinimumDuration() {
        var reportedStart: LocalDateTime? = null
        var reportedEnd: LocalDateTime? = null
        rule.setContent {
            KalendarSchedule(
                state = rememberKalendarScheduleState(initialDate = tuesday),
                events = listOf(anchorEvent(tuesday)),
                onEventTimeChange = { _, newStart, newEnd ->
                    reportedStart = newStart
                    reportedEnd = newEnd
                },
                config = config,
            )
        }
        rule.onNodeWithText("Sync").performTouchInput {
            down(Offset(center.x, height * TOP_STRIP_GRAB_FRACTION))
            advanceEventTime(LONG_PRESS_MS)
            moveBy(Offset(0f, height * 3f))
            up()
        }
        rule.waitForIdle()

        assertEquals(LocalDateTime(2026, 3, 10, 9, 45), reportedStart, "the start may not cross the end")
        assertEquals(LocalDateTime(2026, 3, 10, 10, 0), reportedEnd)
    }

    @Test
    fun weekGrid_longPressDragEmptySpace_createsOnThePressedDayColumn() {
        var reported: Pair<LocalDateTime, LocalDateTime>? = null
        rule.setContent {
            KalendarScheduleWeek(
                state = rememberKalendarWeekState(initialDate = tuesday),
                events = listOf(anchorEvent(wednesday)),
                onEventCreate = { start, end -> reported = start to end },
                config = config,
            )
        }
        rule.onNodeWithText("Sync").performTouchInput {
            down(Offset(center.x, height * 1.5f))
            advanceEventTime(LONG_PRESS_MS)
            moveBy(Offset(0f, height.toFloat()))
            up()
        }
        rule.waitForIdle()

        assertEquals(
            LocalDateTime(2026, 3, 11, 10, 30) to LocalDateTime(2026, 3, 11, 11, 30),
            reported,
            "the created range belongs to the day column the press landed in",
        )
    }

    private companion object {
        const val LONG_PRESS_MS = 1_000L

        /** 5% of the block height from the top — well inside the 12dp resize strip. */
        const val TOP_STRIP_GRAB_FRACTION = 0.05f
    }
}
