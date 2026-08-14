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

package com.himanshoe.kalendar.foundation

import com.himanshoe.kalendar.foundation.datetime.KalendarTimeSource
import com.himanshoe.kalendar.foundation.event.BasicKalendarEvent
import com.himanshoe.kalendar.foundation.event.byDateExpandingSpans
import com.himanshoe.kalendar.foundation.format.KalendarFormatters
import com.himanshoe.kalendar.foundation.format.monthTitle
import com.himanshoe.kalendar.foundation.grid.ALL_DAYS_OF_WEEK
import com.himanshoe.kalendar.foundation.grid.daysOfWeekStartingAt
import com.himanshoe.kalendar.foundation.grid.monthGridDates
import com.himanshoe.kalendar.foundation.paging.KalendarPageUnit
import com.himanshoe.kalendar.foundation.paging.KalendarPager
import com.himanshoe.kalendar.foundation.schedule.KalendarHourWindow
import com.himanshoe.kalendar.foundation.schedule.MINUTES_PER_HOUR
import com.himanshoe.kalendar.foundation.schedule.blockPlacement
import com.himanshoe.kalendar.foundation.schedule.dragReleaseTimes
import com.himanshoe.kalendar.foundation.schedule.scheduleBlockAt
import com.himanshoe.kalendar.foundation.schedule.scheduleBlocks
import com.himanshoe.kalendar.foundation.selection.KalendarSelection
import com.himanshoe.kalendar.foundation.selection.KalendarSelectionMode
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * The module's contract in one place: a month grid and a day view can be driven end to end from this
 * module alone.
 *
 * Nothing here imports a Compose UI type, and the module has no `compose.foundation` on its
 * classpath — so if these compile and pass, someone driving their own `LazyRow`, `ViewPager` or
 * gesture surface can compute exactly what the shipped views compute.
 */
class KalendarHeadlessEngineTest {

    private val timeSource = KalendarTimeSource(
        timeZone = TimeZone.UTC,
        clock = object : Clock {
            override fun now(): Instant = Instant.parse("2026-08-12T09:30:00Z")
        },
    )

    @Test
    fun aMonthGridCanBeBuiltForAnyPageOfAPager() {
        val pager = KalendarPager.endless(
            unit = KalendarPageUnit.Month,
            initialDate = timeSource.today(),
            startDayOfWeek = DayOfWeek.MONDAY,
        )
        val headers = daysOfWeekStartingAt(DayOfWeek.MONDAY)

        val nextMonth = pager.dateAt(pager.initialPage + 1)
        val rows = monthGridDates(
            monthStart = nextMonth,
            startDayOfWeek = DayOfWeek.MONDAY,
            visibleDaysOfWeek = ALL_DAYS_OF_WEEK,
        ).chunked(headers.size)

        assertEquals("September 2026", monthTitle(nextMonth, KalendarFormatters.monthName))
        assertTrue(
            rows.dropLast(1).all { it.size == headers.size },
            "only the final row may be short — the grid pads its start, not its end",
        )
        rows.forEach { row ->
            row.forEachIndexed { column, date ->
                assertEquals(headers[column], date.dayOfWeek, "column $column sits under the wrong header")
            }
        }
    }

    @Test
    fun aDayViewCanBeLaidOutHitTestedAndDraggedWithoutCompose() {
        val day = timeSource.today()
        val window = KalendarHourWindow(startHour = 8, endHour = 20)
        val minutePx = 1f

        val standup = BasicKalendarEvent(
            date = day,
            eventName = "Standup",
            startTime = LocalDateTime(2026, 8, 12, 9, 0),
            endTime = LocalDateTime(2026, 8, 12, 10, 0),
        )
        val holiday = BasicKalendarEvent(date = day, eventName = "Holiday")

        val byDate = listOf(standup, holiday).byDateExpandingSpans()
        val blocks = scheduleBlocks(events = byDate.getValue(day), date = day, window = window)

        val block = blocks.single()
        assertEquals("Standup", block.event.eventName, "the all-day event has no place on an hour grid")

        val placement = blockPlacement(block = block, window = window)
        assertEquals(MINUTES_PER_HOUR, placement.topMinutes, "09:00 is one hour below an 08:00 window top")
        assertEquals(MINUTES_PER_HOUR, placement.heightMinutes)

        val pressed = scheduleBlockAt(
            blocks = blocks,
            xPx = 10f,
            yPx = (placement.topMinutes + 30) * minutePx,
            widthPx = 200,
            minutePx = minutePx,
            window = window,
        )
        assertSame(block, pressed, "a press inside the drawn rectangle finds the block")

        val (start, end) = dragReleaseTimes(block = block, targetDate = day)
        assertEquals(LocalDateTime(2026, 8, 12, 9, 0), start)
        assertEquals(LocalDateTime(2026, 8, 12, 10, 0), end)
    }

    @Test
    fun aRangeSelectionSurvivesAPageChange() {
        val pager = KalendarPager.endless(unit = KalendarPageUnit.Month, initialDate = timeSource.today())

        val selection = KalendarSelection.Empty
            .afterClick(KalendarSelectionMode.Range, LocalDate(2026, 8, 30))
            .afterClick(KalendarSelectionMode.Range, pager.dateAt(pager.initialPage + 1))

        assertEquals(
            setOf(
                LocalDate(2026, 8, 30),
                LocalDate(2026, 8, 31),
                LocalDate(2026, 9, 1),
            ),
            selection.dates,
            "a range that spans two pages is still one set of dates",
        )
    }
}
