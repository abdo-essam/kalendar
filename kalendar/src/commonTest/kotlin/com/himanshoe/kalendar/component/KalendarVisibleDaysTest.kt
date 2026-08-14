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
import com.himanshoe.kalendar.KalendarViewConfig
import com.himanshoe.kalendar.foundation.datetime.DAYS_PER_WEEK
import com.himanshoe.kalendar.foundation.grid.monthDates
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * What [KalendarViewConfig.visibleDaysOfWeek] does above the grid maths itself — to an arrow key, to
 * a page step, and to what the config will accept. The grid arithmetic it rests on is tested in
 * `kalendar-foundation`.
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

    private companion object {
        const val YEAR = 2026
        const val MARCH = 3
        const val JULY = 7
        const val AUGUST = 8
        const val MONDAY_9TH = 9
    }
}
