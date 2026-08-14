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

package com.himanshoe.kalendar.foundation.schedule

import com.himanshoe.kalendar.foundation.event.BasicKalendarEvent
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KalendarScheduleTest {

    private val day = LocalDate(2026, 8, 12)

    private fun timed(name: String, startHour: Int, startMinute: Int = 0, endHour: Int? = null): BasicKalendarEvent =
        BasicKalendarEvent(
            date = day,
            eventName = name,
            startTime = LocalDateTime(day.year, day.monthNumber, day.dayOfMonth, startHour, startMinute),
            endTime = endHour?.let { LocalDateTime(day.year, day.monthNumber, day.dayOfMonth, it, 0) },
        )

    @Test
    fun scheduleBlocks_allDayEvent_isExcluded() {
        val allDay = BasicKalendarEvent(date = day, eventName = "Holiday")
        assertTrue(scheduleBlocks(events = listOf(allDay), date = day).isEmpty())
    }

    @Test
    fun scheduleBlocks_minutesComputedFromStartAndEnd() {
        val block = scheduleBlocks(
            events = listOf(timed(name = "Standup", startHour = 9, startMinute = 30, endHour = 10)),
            date = day,
        ).single()
        assertEquals(9 * 60 + 30, block.startMinutes)
        assertEquals(10 * 60, block.endMinutes)
    }

    @Test
    fun scheduleBlocks_missingEndTime_defaultsToOneHour() {
        val block = scheduleBlocks(
            events = listOf(timed(name = "Open-ended", startHour = 14)),
            date = day,
        ).single()
        assertEquals(14 * 60, block.startMinutes)
        assertEquals(15 * 60, block.endMinutes)
    }

    @Test
    fun scheduleBlocks_nonOverlapping_eachGetsFullWidth() {
        val blocks = scheduleBlocks(
            events = listOf(
                timed(name = "Morning", startHour = 9, endHour = 10),
                timed(name = "Afternoon", startHour = 14, endHour = 15),
            ),
            date = day,
        )
        assertTrue(blocks.all { it.columns == 1 && it.column == 0 })
    }

    @Test
    fun scheduleBlocks_overlapping_shareColumnsSideBySide() {
        val blocks = scheduleBlocks(
            events = listOf(
                timed(name = "A", startHour = 9, endHour = 11),
                timed(name = "B", startHour = 10, endHour = 12),
            ),
            date = day,
        )
        assertTrue(blocks.all { it.columns == 2 })
        assertEquals(setOf(0, 1), blocks.map { it.column }.toSet())
    }

    @Test
    fun scheduleBlocks_secondClusterAfterGap_resetsToFullWidth() {
        val blocks = scheduleBlocks(
            events = listOf(
                timed(name = "A", startHour = 9, endHour = 11),
                timed(name = "B", startHour = 10, endHour = 12),
                timed(name = "Solo", startHour = 15, endHour = 16),
            ),
            date = day,
        )
        val solo = blocks.single { it.event.eventName == "Solo" }
        assertEquals(1, solo.columns)
        assertEquals(0, solo.column)
    }

    @Test
    fun scheduleBlocks_spanningMidnightFromPreviousDay_clampsToDayStart() {
        val overnight = BasicKalendarEvent(
            date = day.minusOne(),
            endDate = day,
            eventName = "Red-eye",
            startTime = LocalDateTime(2026, 8, 11, 22, 0),
            endTime = LocalDateTime(2026, 8, 12, 6, 0),
        )
        val block = scheduleBlocks(events = listOf(overnight), date = day).single()
        assertEquals(0, block.startMinutes)
        assertEquals(6 * 60, block.endMinutes)
    }

    @Test
    fun snappedDragMinutes_singleRounding_noDriftForOddSnaps() {
        // 100px at 2px/min = 50min; nearest multiple of 45 is 45 — the old two-stage rounding
        // (50 -> 45 via 50/45=1.11 rounds to 1) agrees here, but 34px=17min must snap to 0,
        // not round 17 -> 15 via intermediate minute rounding drift.
        assertEquals(45, snappedDragMinutes(rawPx = 100f, minutePx = 2f, snapMinutes = 45))
        assertEquals(0, snappedDragMinutes(rawPx = 34f, minutePx = 2f, snapMinutes = 45))
        assertEquals(-15, snappedDragMinutes(rawPx = -16f, minutePx = 1f, snapMinutes = 15))
        assertEquals(0, snappedDragMinutes(rawPx = 100f, minutePx = 0f, snapMinutes = 15))
    }

    @Test
    fun adjustedScheduleBlock_move_preservesDurationAndClampsToDay() {
        val block = KalendarScheduleBlock(
            event = timed(name = "A", startHour = 9, endHour = 10),
            startMinutes = 9 * 60,
            endMinutes = 10 * 60,
            column = 0,
            columns = 1,
        )
        val moved = adjustedScheduleBlock(block = block, deltaMinutes = 60, resizing = false)
        assertEquals(10 * 60, moved.startMinutes)
        assertEquals(11 * 60, moved.endMinutes)

        val clamped = adjustedScheduleBlock(block = block, deltaMinutes = 100_000, resizing = false)
        assertEquals(MINUTES_PER_DAY - 60, clamped.startMinutes)
        assertEquals(MINUTES_PER_DAY, clamped.endMinutes)
    }

    @Test
    fun adjustedScheduleBlock_resize_neverShrinksBelowMinimum() {
        val block = KalendarScheduleBlock(
            event = timed(name = "A", startHour = 9, endHour = 10),
            startMinutes = 9 * 60,
            endMinutes = 10 * 60,
            column = 0,
            columns = 1,
        )
        val shrunk = adjustedScheduleBlock(block = block, deltaMinutes = -100_000, resizing = true)
        assertEquals(9 * 60 + MIN_SCHEDULE_BLOCK_MINUTES, shrunk.endMinutes)
        assertEquals(9 * 60, shrunk.startMinutes)
    }

    @Test
    fun dayShiftForDrag_dividesByDayColumnWidthAndClamps() {
        assertEquals(1, dayShiftForDrag(rawDeltaXPx = 110f, dayColumnWidthPx = 100))
        assertEquals(0, dayShiftForDrag(rawDeltaXPx = 40f, dayColumnWidthPx = 100))
        assertEquals(-2, dayShiftForDrag(rawDeltaXPx = -190f, dayColumnWidthPx = 100))
        assertEquals(6, dayShiftForDrag(rawDeltaXPx = 100_000f, dayColumnWidthPx = 100))
        assertEquals(0, dayShiftForDrag(rawDeltaXPx = 500f, dayColumnWidthPx = 0))
    }

    @Test
    fun dragReleaseTimes_endAtMidnight_reportsNextDayMidnightNot2359() {
        val block = KalendarScheduleBlock(
            event = timed(name = "A", startHour = 23, endHour = null),
            startMinutes = 23 * 60,
            endMinutes = MINUTES_PER_DAY,
            column = 0,
            columns = 1,
        )
        val (start, end) = dragReleaseTimes(block = block, targetDate = day)
        assertEquals(LocalDateTime(2026, 8, 12, 23, 0), start)
        assertEquals(LocalDateTime(2026, 8, 13, 0, 0), end)
    }

    @Test
    fun minutesToLocalTime_clampsWithinDay() {
        assertEquals(9, minutesToLocalTime(9 * 60 + 30).hour)
        assertEquals(30, minutesToLocalTime(9 * 60 + 30).minute)
        assertEquals(23, minutesToLocalTime(MINUTES_PER_DAY - 1).hour)
        assertEquals(59, minutesToLocalTime(MINUTES_PER_DAY - 1).minute)
    }

    @Test
    fun dayShiftForDrag_cannotLeaveAFiveColumnGrid() {
        assertEquals(
            4,
            dayShiftForDrag(rawDeltaXPx = 1_000f, dayColumnWidthPx = 10, columnCount = 5),
            "a five-column week allows at most four columns of travel",
        )
    }

    @Test
    fun scheduleBlocks_zeroDuration_getsMinimumHeight() {
        val instant = BasicKalendarEvent(
            date = day,
            eventName = "Ping",
            startTime = LocalDateTime(2026, 8, 12, 9, 0),
            endTime = LocalDateTime(2026, 8, 12, 9, 0),
        )
        val block = scheduleBlocks(events = listOf(instant), date = day).single()
        assertEquals(MIN_SCHEDULE_BLOCK_MINUTES, block.endMinutes - block.startMinutes)
    }
}

private fun LocalDate.minusOne(): LocalDate = LocalDate(year, monthNumber, dayOfMonth - 1)
