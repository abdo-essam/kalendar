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

package com.himanshoe.kalendar.component

import androidx.compose.ui.input.key.Key
import com.himanshoe.kalendar.foundation.format.KalendarFormatters
import com.himanshoe.kalendar.foundation.grid.monthDates
import com.himanshoe.kalendar.theme.KalendarStrings
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The key-to-date mapping behind keyboard navigation, tested without a composition.
 *
 * The UI test drives real key events through a real grid; this covers the arithmetic, including the
 * cases a UI test cannot reach cheaply — a `Home` on an empty period, a month step that has to clamp
 * to a shorter month.
 */
class KalendarKeyboardNavigationTest {

    private val march10 = LocalDate(YEAR, MARCH, 10)
    private val marchDates = monthDates(LocalDate(YEAR, MARCH, 1))

    private val monthStep: (LocalDate, Int) -> LocalDate =
        { date, pages -> date.plus(pages, DateTimeUnit.MONTH) }

    private fun target(key: Key, focused: LocalDate = march10): LocalDate? =
        keyboardTargetDate(key = key, focused = focused, periodDates = marchDates, pageStep = monthStep)

    @Test
    fun arrowsStepOneDayAndOneRow() {
        assertEquals(LocalDate(YEAR, MARCH, 11), target(Key.DirectionRight))
        assertEquals(LocalDate(YEAR, MARCH, 9), target(Key.DirectionLeft))
        assertEquals(LocalDate(YEAR, MARCH, 17), target(Key.DirectionDown))
        assertEquals(LocalDate(YEAR, MARCH, 3), target(Key.DirectionUp))
    }

    @Test
    fun arrowsCrossMonthBoundariesRatherThanClampingToTheMonth() {
        assertEquals(
            LocalDate(YEAR, APRIL, 1),
            target(Key.DirectionRight, focused = LocalDate(YEAR, MARCH, 31)),
            "a target outside the period is what tells the caller to page",
        )
        assertEquals(LocalDate(YEAR, FEBRUARY, 28), target(Key.DirectionLeft, focused = LocalDate(YEAR, MARCH, 1)))
    }

    @Test
    fun homeAndEndLandOnThePeriodNotTheGrid() {
        assertEquals(LocalDate(YEAR, MARCH, 1), target(Key.MoveHome))
        assertEquals(LocalDate(YEAR, MARCH, 31), target(Key.MoveEnd))
    }

    @Test
    fun homeAndEndOnAnEmptyPeriodMoveNowhere() {
        val empty = keyboardTargetDate(
            key = Key.MoveHome,
            focused = march10,
            periodDates = emptyList(),
            pageStep = monthStep,
        )
        assertNull(empty)
    }

    @Test
    fun pageKeysDelegateToTheViewsOwnUnit() {
        assertEquals(LocalDate(YEAR, APRIL, 10), target(Key.PageDown))
        assertEquals(LocalDate(YEAR, FEBRUARY, 10), target(Key.PageUp))
    }

    @Test
    fun pageDownFromADayThatTheNextMonthDoesNotHaveClampsToItsLastDay() {
        assertEquals(
            LocalDate(YEAR, APRIL, 30),
            target(Key.PageDown, focused = LocalDate(YEAR, MARCH, 31)),
            "April has no 31st; the step must land on a real date rather than overflow",
        )
    }

    @Test
    fun nonNavigationKeysMoveNothing() {
        listOf(Key.Enter, Key.Spacebar, Key.Tab, Key.Escape, Key.A).forEach { key ->
            assertNull(target(key), "$key should not move focus")
        }
    }

    @Test
    fun selectKeysAreEnterAndSpaceOnly() {
        listOf(Key.Enter, Key.NumPadEnter, Key.Spacebar).forEach { key ->
            assertTrue(isKeyboardSelectKey(key), "$key should select")
        }
        listOf(Key.Tab, Key.Escape, Key.DirectionRight).forEach { key ->
            assertTrue(!isKeyboardSelectKey(key), "$key should not select")
        }
    }

    @Test
    fun monthDatesExcludesTheGridsPaddingDays() {
        assertEquals(31, marchDates.size)
        assertEquals(LocalDate(YEAR, MARCH, 1), marchDates.first())
        assertEquals(LocalDate(YEAR, MARCH, 31), marchDates.last())
    }

    @Test
    fun focusAnnouncementMatchesTheDayCellsOwnDescription() {
        val strings = KalendarStrings()
        assertEquals(
            "March 10, 2026",
            keyboardFocusAnnouncement(
                date = march10,
                strings = strings,
                monthNameFormatter = KalendarFormatters.monthName,
                isToday = false,
            ),
        )
        assertEquals(
            "March 10, 2026, today",
            keyboardFocusAnnouncement(
                date = march10,
                strings = strings,
                monthNameFormatter = KalendarFormatters.monthName,
                isToday = true,
            ),
        )
    }

    private companion object {
        const val YEAR = 2026
        const val FEBRUARY = 2
        const val MARCH = 3
        const val APRIL = 4
    }
}
