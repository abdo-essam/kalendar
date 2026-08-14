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

package com.himanshoe.kalendar.foundation.grid

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KalendarGridDatesTest {

    @Test
    fun weekDates_alwaysReturnsSeven() {
        val weekStart = LocalDate(2026, 6, 8)
        assertEquals(7, weekDates(weekStart).size)
    }

    @Test
    fun weekDates_startsAtGivenDate() {
        val weekStart = LocalDate(2026, 6, 8)
        assertEquals(weekStart, weekDates(weekStart).first())
    }

    @Test
    fun weekDates_datesAreConsecutive() {
        val weekStart = LocalDate(2026, 6, 8)
        val dates = weekDates(weekStart)
        for (i in 1 until dates.size) {
            assertEquals(dates[i - 1].toEpochDays() + 1, dates[i].toEpochDays())
        }
    }

    @Test
    fun monthGridDates_june2026_mondayStart_allDaysPresent() {
        val june = LocalDate(2026, 6, 1)
        val dates = monthGridDates(june, DayOfWeek.MONDAY)
        val juneDates = dates.filter { it.month == Month.JUNE }
        assertEquals(30, juneDates.size)
    }

    @Test
    fun monthGridDates_startsOnConfiguredStartDayOfWeek() {
        val may = LocalDate(2026, 5, 1)
        val dates = monthGridDates(may, DayOfWeek.MONDAY)
        assertEquals(DayOfWeek.MONDAY, dates.first().dayOfWeek)
    }

    @Test
    fun monthGridDates_datesAreConsecutive() {
        val march = LocalDate(2026, 3, 1)
        val dates = monthGridDates(march, DayOfWeek.MONDAY)
        for (i in 1 until dates.size) {
            assertEquals(dates[i - 1].toEpochDays() + 1, dates[i].toEpochDays())
        }
    }

    @Test
    fun monthGridDates_monthAlreadyStartsOnStartDay_noLeadingPadding() {
        val juneStartingMonday = LocalDate(2026, 6, 1)
        assertTrue(juneStartingMonday.dayOfWeek == DayOfWeek.MONDAY)
        val dates = monthGridDates(juneStartingMonday, DayOfWeek.MONDAY)
        assertEquals(juneStartingMonday, dates.first())
    }

    @Test
    fun daysOfWeekStartingAt_monday_startsWithMonday() {
        assertEquals(DayOfWeek.MONDAY, daysOfWeekStartingAt(DayOfWeek.MONDAY).first())
    }

    @Test
    fun daysOfWeekStartingAt_sunday_startsWithSunday() {
        assertEquals(DayOfWeek.SUNDAY, daysOfWeekStartingAt(DayOfWeek.SUNDAY).first())
    }

    @Test
    fun daysOfWeekStartingAt_preservesAllSevenDays() {
        val rotated = daysOfWeekStartingAt(DayOfWeek.WEDNESDAY)
        assertEquals(DayOfWeek.entries.toSet(), rotated.toSet())
        assertEquals(7, rotated.size)
    }
}
