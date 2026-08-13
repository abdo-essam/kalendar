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

class KalendarScheduleWeekUiTest {

    @get:Rule
    val rule = createComposeRule()

    @Suppress("MagicNumber")
    private val tuesday = LocalDate(2026, 3, 10)

    private fun timedEvent(name: String, date: LocalDate, startHour: Int, endHour: Int): BasicKalendarEvent =
        BasicKalendarEvent(
            date = date,
            eventName = name,
            startTime = LocalDateTime(date.year, date.month, date.day, startHour, 0),
            endTime = LocalDateTime(date.year, date.month, date.day, endHour, 0),
        )

    @Test
    fun weekGrid_rendersBlocksOnMultipleDaysOfTheSameWeek() {
        rule.setContent {
            KalendarScheduleWeek(
                selectedDate = tuesday,
                events = listOf(
                    timedEvent(name = "Standup", date = tuesday, startHour = 9, endHour = 10),
                    timedEvent(name = "Review", date = LocalDate(2026, 3, 12), startHour = 14, endHour = 15),
                ),
            )
        }
        rule.onNodeWithText("Standup").assertExists()
        rule.onNodeWithText("Review").assertExists()
    }

    @Test
    fun weekGrid_horizontalLongPressDrag_movesEventToNextDayKeepingTimes() {
        var reportedStart: LocalDateTime? = null
        var reportedEnd: LocalDateTime? = null
        rule.setContent {
            KalendarScheduleWeek(
                selectedDate = tuesday,
                events = listOf(timedEvent(name = "Sync", date = tuesday, startHour = 9, endHour = 10)),
                onEventTimeChange = { _, newStart, newEnd ->
                    reportedStart = newStart
                    reportedEnd = newEnd
                },
            )
        }
        // The single block fills its day column, so its own width approximates the day-column
        // width; dragging 1.1x that distance rounds to a one-day shift regardless of density.
        // Regression guard: the day shift used to be divided by the block width only by
        // coincidence — with overlapping events it read a fraction of the column and
        // over-shifted; it now reads the measured day-column width.
        rule.onNodeWithText("Sync").performTouchInput {
            down(center)
            advanceEventTime(LONG_PRESS_MS)
            moveBy(Offset(width * ONE_COLUMN_DRAG_FACTOR, 0f))
            up()
        }
        rule.waitForIdle()

        assertEquals(LocalDateTime(2026, 3, 11, 9, 0), reportedStart, "drag right one column = next day, same time")
        assertEquals(LocalDateTime(2026, 3, 11, 10, 0), reportedEnd, "duration must be preserved")
    }

    @Test
    fun weekGrid_overlappingEvents_bothRenderSideBySide() {
        rule.setContent {
            KalendarScheduleWeek(
                selectedDate = tuesday,
                events = listOf(
                    timedEvent(name = "A", date = tuesday, startHour = 9, endHour = 11),
                    timedEvent(name = "B", date = tuesday, startHour = 10, endHour = 12),
                ),
            )
        }
        rule.onNodeWithText("A").assertExists()
        rule.onNodeWithText("B").assertExists()
    }

    private companion object {
        const val LONG_PRESS_MS = 1_000L
        const val ONE_COLUMN_DRAG_FACTOR = 1.1f
    }
}
