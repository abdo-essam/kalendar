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
