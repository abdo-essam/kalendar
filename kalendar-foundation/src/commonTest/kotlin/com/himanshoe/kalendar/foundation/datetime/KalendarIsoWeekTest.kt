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

package com.himanshoe.kalendar.foundation.datetime

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The ISO-8601 week arithmetic, tested where it is actually hard: the year boundaries. Every named
 * date below is a documented ISO-8601 trap — a January day owned by the previous ISO year, a
 * December day owned by the next, a 53-week year beside a 52-week one — because an implementation
 * that merely counts sevens from 1 January gets all of the easy dates right and all of these wrong.
 */
class KalendarIsoWeekTest {

    // ---- January days that belong to the PREVIOUS ISO year ----

    @Test
    fun january1st2021_isWeek53OfIsoYear2020() {
        val date = LocalDate(2021, 1, 1)
        assertEquals(53, date.isoWeekNumber())
        assertEquals(2020, date.isoWeekYear())
    }

    @Test
    fun january3rd2021_theSundayEndingThatWeek_isStillWeek53Of2020() {
        val date = LocalDate(2021, 1, 3)
        assertEquals(53, date.isoWeekNumber())
        assertEquals(2020, date.isoWeekYear())
    }

    @Test
    fun january4th2021_theMondayAfter_opensWeek1Of2021() {
        val date = LocalDate(2021, 1, 4)
        assertEquals(1, date.isoWeekNumber())
        assertEquals(2021, date.isoWeekYear())
    }

    @Test
    fun january1st2016_isWeek53OfIsoYear2015() {
        val date = LocalDate(2016, 1, 1)
        assertEquals(53, date.isoWeekNumber())
        assertEquals(2015, date.isoWeekYear())
    }

    // ---- December days that belong to the NEXT ISO year ----

    @Test
    fun december30th2019_isWeek1OfIsoYear2020() {
        val date = LocalDate(2019, 12, 30)
        assertEquals(1, date.isoWeekNumber())
        assertEquals(2020, date.isoWeekYear())
    }

    @Test
    fun december31st2019_isWeek1OfIsoYear2020() {
        val date = LocalDate(2019, 12, 31)
        assertEquals(1, date.isoWeekNumber())
        assertEquals(2020, date.isoWeekYear())
    }

    @Test
    fun december29th2019_theSundayBefore_isStillWeek52Of2019() {
        val date = LocalDate(2019, 12, 29)
        assertEquals(52, date.isoWeekNumber())
        assertEquals(2019, date.isoWeekYear())
    }

    // ---- 53-week years versus 52-week years ----

    /**
     * 28 December always sits in its year's last ISO week — it is the latest date that can never
     * spill into week 1 of the next year — so its week number *is* the year's week count.
     */
    private fun weekCountOf(year: Int): Int = LocalDate(year, 12, 28).isoWeekNumber()

    @Test
    fun the53WeekYears_2015_2020_2026_haveFiftyThreeWeeks() {
        assertEquals(53, weekCountOf(2015))
        assertEquals(53, weekCountOf(2020))
        assertEquals(53, weekCountOf(2026))
    }

    @Test
    fun the52WeekYears_2019_2021_2024_haveFiftyTwoWeeks() {
        assertEquals(52, weekCountOf(2019))
        assertEquals(52, weekCountOf(2021))
        assertEquals(52, weekCountOf(2024))
    }

    @Test
    fun december31st2020_aThursday_isWeek53OfItsOwnYear() {
        val date = LocalDate(2020, 12, 31)
        assertEquals(53, date.isoWeekNumber())
        assertEquals(2020, date.isoWeekYear())
    }

    @Test
    fun january3rd2027_closes53WeekYear2026_andJanuary4thOpens2027() {
        val lastSundayOf2026 = LocalDate(2027, 1, 3)
        assertEquals(53, lastSundayOf2026.isoWeekNumber())
        assertEquals(2026, lastSundayOf2026.isoWeekYear())

        val firstMondayOf2027 = LocalDate(2027, 1, 4)
        assertEquals(1, firstMondayOf2027.isoWeekNumber())
        assertEquals(2027, firstMondayOf2027.isoWeekYear())
    }

    // ---- An unremarkable mid-year date, so the boundary handling cannot overfit ----

    @Test
    fun august31st2026_aMidYearMonday_isWeek36Of2026() {
        val date = LocalDate(2026, 8, 31)
        assertEquals(36, date.isoWeekNumber())
        assertEquals(2026, date.isoWeekYear())
    }

    // ---- The round-trip property ----

    /**
     * Walks every date from 2014 through 2027 — spanning three 53-week years — and checks each
     * against its predecessor: inside a Monday-based week the pair must not move, and across a
     * Sunday-to-Monday boundary the week must either increment within the same ISO year or wrap to
     * week 1 of the next. Any off-by-one anywhere in the fourteen years breaks the chain.
     */
    @Test
    fun everyDateAgreesWithItsNeighbour_acrossFourteenYearsOfSundayMondayBoundaries() {
        var date = LocalDate(2014, 1, 1)
        val end = LocalDate(2027, 12, 31)
        var week = date.isoWeekNumber()
        var weekYear = date.isoWeekYear()
        while (date < end) {
            val next = date.plus(1, DateTimeUnit.DAY)
            val nextWeek = next.isoWeekNumber()
            val nextWeekYear = next.isoWeekYear()
            assertTrue(nextWeek in 1..53, "$next reported ISO week $nextWeek, outside 1..53")
            if (date.dayOfWeek == DayOfWeek.SUNDAY) {
                val incremented = nextWeekYear == weekYear && nextWeek == week + 1
                val wrapped = nextWeekYear == weekYear + 1 && nextWeek == 1 && week >= 52
                assertTrue(
                    incremented || wrapped,
                    "crossing $date -> $next moved week $week of $weekYear to $nextWeek of $nextWeekYear, " +
                        "which is neither the next week nor week 1 of the next ISO year",
                )
            } else {
                assertEquals(
                    week to weekYear,
                    nextWeek to nextWeekYear,
                    "$date and $next share a Monday-based week but reported different ISO weeks",
                )
            }
            date = next
            week = nextWeek
            weekYear = nextWeekYear
        }
    }

    // ---- mondayOfWeek: the date a display row is labeled by ----

    @Test
    fun mondayOfWeek_mondayStart_isTheRowsFirstDate() {
        val wednesday = LocalDate(2026, 9, 2)
        assertEquals(LocalDate(2026, 8, 31), wednesday.mondayOfWeek(DayOfWeek.MONDAY))

        val monday = LocalDate(2026, 8, 31)
        assertEquals(monday, monday.mondayOfWeek(DayOfWeek.MONDAY))
    }

    @Test
    fun mondayOfWeek_sundayStart_aSundayIsLabeledByTheMondayAfterIt() {
        val sunday = LocalDate(2026, 8, 30)
        assertEquals(LocalDate(2026, 8, 31), sunday.mondayOfWeek(DayOfWeek.SUNDAY))
    }

    @Test
    fun mondayOfWeek_sundayStart_theRestOfTheRowSharesThatMonday() {
        val monday = LocalDate(2026, 8, 31)
        val saturday = LocalDate(2026, 9, 5)
        assertEquals(monday, monday.mondayOfWeek(DayOfWeek.SUNDAY))
        assertEquals(monday, saturday.mondayOfWeek(DayOfWeek.SUNDAY))
    }

    @Test
    fun mondayOfWeek_saturdayStart_skipsTwoDaysIn() {
        val saturday = LocalDate(2026, 8, 29)
        assertEquals(LocalDate(2026, 8, 31), saturday.mondayOfWeek(DayOfWeek.SATURDAY))
    }

    /**
     * For every start-of-week choice and every day of a four-week span, the labeling Monday must be
     * an actual Monday and must lie inside the seven-day display week containing the date.
     */
    @Test
    fun mondayOfWeek_alwaysLandsOnAMondayInsideTheDisplayWeek() {
        val fourWeeks = 28
        for (startDayOfWeek in DayOfWeek.entries) {
            var date = LocalDate(2026, 2, 9)
            repeat(fourWeeks) {
                val monday = date.mondayOfWeek(startDayOfWeek)
                val rowStart = date.startOfWeek(startDayOfWeek)
                val rowEnd = rowStart.plus(DAYS_PER_WEEK - 1, DateTimeUnit.DAY)
                assertEquals(DayOfWeek.MONDAY, monday.dayOfWeek, "$date with $startDayOfWeek start")
                assertTrue(
                    monday in rowStart..rowEnd,
                    "$date with $startDayOfWeek start was labeled by $monday, outside its row $rowStart..$rowEnd",
                )
                date = date.plus(1, DateTimeUnit.DAY)
            }
        }
    }

    @Test
    fun sundayStartedRow_isLabeledWithTheEuropeanWeekNumber() {
        // The row 2026-08-30 (Sunday) .. 2026-09-05 (Saturday): its Sunday belongs to ISO week 35,
        // but its Monday-to-Saturday block is ISO week 36 — the number a European user expects.
        val rowFirstDate = LocalDate(2026, 8, 30)
        val labelingMonday = rowFirstDate.mondayOfWeek(DayOfWeek.SUNDAY)
        assertEquals(36, labelingMonday.isoWeekNumber())
        assertEquals(2026, labelingMonday.isoWeekYear())
    }
}
