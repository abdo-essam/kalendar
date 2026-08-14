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

import androidx.compose.ui.input.key.Key
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * The maths behind [KalendarViewConfig.visibleDaysOfWeek]: a work-week calendar has to keep every
 * grid row the same width as its header, or every column ends up under the wrong label.
 */
class KalendarVisibleDaysTest {

    private val workWeek = setOf(
        DayOfWeek.MONDAY,
        DayOfWeek.TUESDAY,
        DayOfWeek.WEDNESDAY,
        DayOfWeek.THURSDAY,
        DayOfWeek.FRIDAY,
    )

    @Test
    fun aWeekWithWeekendsHiddenIsFiveDatesStartingMonday() {
        val monday = LocalDate(YEAR, MARCH, MONDAY_9TH)
        val dates = weekDates(weekStart = monday, visibleDaysOfWeek = workWeek)

        assertEquals(workWeek.size, dates.size)
        assertEquals(monday, dates.first())
        assertEquals(LocalDate(YEAR, MARCH, MONDAY_9TH + 4), dates.last(), "Friday, not Sunday")
        assertTrue(dates.none { it.dayOfWeek == DayOfWeek.SATURDAY || it.dayOfWeek == DayOfWeek.SUNDAY })
    }

    @Test
    fun aWeekWithEveryDayVisibleIsUnchanged() {
        val monday = LocalDate(YEAR, MARCH, MONDAY_9TH)

        assertEquals(weekDates(monday), weekDates(weekStart = monday, visibleDaysOfWeek = ALL_DAYS_OF_WEEK))
    }

    @Test
    fun theHeaderRowIsRotatedThenFiltered() {
        val labels = daysOfWeekStartingAt(startDayOfWeek = DayOfWeek.SUNDAY, visibleDaysOfWeek = workWeek)

        assertEquals(
            listOf(
                DayOfWeek.MONDAY,
                DayOfWeek.TUESDAY,
                DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY,
                DayOfWeek.FRIDAY,
            ),
            labels,
            "a Sunday-start week whose weekend is hidden still begins its columns at Monday",
        )
    }

    @Test
    fun everyMonthGridRowHasExactlyOneColumnPerVisibleDay() {
        val columns = workWeek.size
        for (month in 1..MONTHS_IN_YEAR) {
            val dates = monthGridDates(
                monthStart = LocalDate(YEAR, month, 1),
                startDayOfWeek = DayOfWeek.MONDAY,
                visibleDaysOfWeek = workWeek,
            )
            val labels = daysOfWeekStartingAt(
                startDayOfWeek = DayOfWeek.MONDAY,
                visibleDaysOfWeek = workWeek,
            )
            dates.chunked(columns).forEachIndexed { row, week ->
                week.forEachIndexed { column, date ->
                    assertEquals(
                        labels[column],
                        date.dayOfWeek,
                        "month $month row $row column $column sits under the wrong header",
                    )
                }
            }
        }
    }

    @Test
    fun aMonthGridContainsEveryVisibleDayOfItsOwnMonthAndNoHiddenOne() {
        val dates = monthGridDates(
            monthStart = LocalDate(YEAR, MARCH, 1),
            startDayOfWeek = DayOfWeek.MONDAY,
            visibleDaysOfWeek = workWeek,
        )

        val ownVisibleDays = monthDates(
            monthStart = LocalDate(YEAR, MARCH, 1),
            visibleDaysOfWeek = workWeek,
        )
        assertTrue(dates.containsAll(ownVisibleDays), "every weekday of March must have a cell")
        assertTrue(dates.all { it.dayOfWeek in workWeek })
    }

    /**
     * August 2026 opens on a Saturday. Measuring the leading padding from the 1st would emit a first
     * row made entirely of July, because the 1st and 2nd are both hidden.
     */
    @Test
    fun aMonthOpeningOnAHiddenDayDoesNotGetALeadingRowOfOnlyTheOtherMonth() {
        val dates = monthGridDates(
            monthStart = LocalDate(YEAR, AUGUST, 1),
            startDayOfWeek = DayOfWeek.MONDAY,
            visibleDaysOfWeek = workWeek,
        )

        assertEquals(
            LocalDate(YEAR, AUGUST, 3),
            dates.first(),
            "the grid should open on August's first visible day, which is also a Monday",
        )
    }

    @Test
    fun aFullyVisibleWeekGivesTheSameMonthGridAsBefore() {
        val monthStart = LocalDate(YEAR, MARCH, 1)

        assertEquals(
            monthGridDates(monthStart = monthStart, startDayOfWeek = DayOfWeek.MONDAY),
            monthGridDates(
                monthStart = monthStart,
                startDayOfWeek = DayOfWeek.MONDAY,
                visibleDaysOfWeek = ALL_DAYS_OF_WEEK,
            ),
        )
    }

    @Test
    fun steppingRightFromFridaySkipsTheWeekend() {
        val friday = LocalDate(YEAR, MARCH, MONDAY_9TH + 4)

        assertEquals(
            LocalDate(YEAR, MARCH, MONDAY_9TH + 7),
            visibleDateStep(from = friday, step = 1, visibleDaysOfWeek = workWeek),
            "the column right of Friday is Monday",
        )
    }

    @Test
    fun steppingLeftFromMondaySkipsTheWeekend() {
        val monday = LocalDate(YEAR, MARCH, MONDAY_9TH)

        assertEquals(
            LocalDate(YEAR, MARCH, MONDAY_9TH - 3),
            visibleDateStep(from = monday, step = -1, visibleDaysOfWeek = workWeek),
            "the column left of Monday is the previous Friday",
        )
    }

    @Test
    fun arrowKeysSkipHiddenDaysWhileUpAndDownStillMoveAWholeWeek() {
        val friday = LocalDate(YEAR, MARCH, MONDAY_9TH + 4)
        val period = monthDates(monthStart = LocalDate(YEAR, MARCH, 1), visibleDaysOfWeek = workWeek)

        val right = keyboardTargetDate(
            key = Key.DirectionRight,
            focused = friday,
            periodDates = period,
            pageStep = { date, _ -> date },
            visibleDaysOfWeek = workWeek,
        )
        val down = keyboardTargetDate(
            key = Key.DirectionDown,
            focused = friday,
            periodDates = period,
            pageStep = { date, _ -> date },
            visibleDaysOfWeek = workWeek,
        )

        assertEquals(LocalDate(YEAR, MARCH, MONDAY_9TH + 7), right, "Right off Friday lands on Monday")
        assertEquals(
            LocalDate(YEAR, MARCH, MONDAY_9TH + DAYS_PER_WEEK + 4),
            down,
            "Down stays in the Friday column a row lower",
        )
    }

    @Test
    fun aPageStepOntoAHiddenDayIsNudgedToTheNextVisibleOne() {
        // 2026-08-01 is a Saturday; a month step from 2026-07-01 lands there.
        val target = keyboardTargetDate(
            key = Key.PageDown,
            focused = LocalDate(YEAR, JULY, 1),
            periodDates = monthDates(monthStart = LocalDate(YEAR, JULY, 1), visibleDaysOfWeek = workWeek),
            pageStep = { _, _ -> LocalDate(YEAR, AUGUST, 1) },
            visibleDaysOfWeek = workWeek,
        )

        assertEquals(LocalDate(YEAR, AUGUST, 3), target, "Saturday has no cell, so focus moves to Monday")
    }

    @Test
    fun homeAndEndLandOnTheFirstAndLastVisibleDayOfThePeriod() {
        val period = monthDates(monthStart = LocalDate(YEAR, AUGUST, 1), visibleDaysOfWeek = workWeek)

        val home = keyboardTargetDate(
            key = Key.MoveHome,
            focused = LocalDate(YEAR, AUGUST, 12),
            periodDates = period,
            pageStep = { date, _ -> date },
            visibleDaysOfWeek = workWeek,
        )

        assertEquals(LocalDate(YEAR, AUGUST, 3), home, "August opens on a Saturday, so Home is the 3rd")
    }

    @Test
    fun aConfigWithNoVisibleDaysIsRejected() {
        assertFailsWith<IllegalArgumentException> { KalendarViewConfig(visibleDaysOfWeek = emptySet()) }
    }

    @Test
    fun aDayColumnShiftCannotLeaveAFiveColumnGrid() {
        assertEquals(
            4,
            dayShiftForDrag(rawDeltaXPx = 1_000f, dayColumnWidthPx = 10, columnCount = 5),
            "a five-column week allows at most four columns of travel",
        )
    }

    private companion object {
        const val YEAR = 2026
        const val MARCH = 3
        const val JULY = 7
        const val AUGUST = 8
        const val MONDAY_9TH = 9
        const val MONTHS_IN_YEAR = 12
    }
}
