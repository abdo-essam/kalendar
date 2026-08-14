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

package com.himanshoe.kalendar.foundation.paging

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class KalendarPageUnitTest {

    @Test
    fun startOf_keysEachUnitByItsFirstDay() {
        val date = LocalDate(2026, 6, 10)

        assertEquals(date, KalendarPageUnit.Day.startOf(date))
        assertEquals(LocalDate(2026, 6, 8), KalendarPageUnit.Week.startOf(date, DayOfWeek.MONDAY))
        assertEquals(LocalDate(2026, 6, 1), KalendarPageUnit.Month.startOf(date))
        assertEquals(LocalDate(2026, 1, 1), KalendarPageUnit.Year.startOf(date))
    }

    @Test
    fun startOf_readsStartDayOfWeekForWeeksOnly() {
        val date = LocalDate(2026, 6, 10)

        assertEquals(LocalDate(2026, 6, 7), KalendarPageUnit.Week.startOf(date, DayOfWeek.SUNDAY))
        assertEquals(
            KalendarPageUnit.Month.startOf(date, DayOfWeek.MONDAY),
            KalendarPageUnit.Month.startOf(date, DayOfWeek.SUNDAY),
        )
    }

    @Test
    fun plusPages_stepsByOnePeriodPerPage() {
        val date = LocalDate(2026, 6, 8)

        assertEquals(LocalDate(2026, 6, 10), KalendarPageUnit.Day.plusPages(date, 2))
        assertEquals(LocalDate(2026, 6, 22), KalendarPageUnit.Week.plusPages(date, 2))
        assertEquals(LocalDate(2026, 8, 8), KalendarPageUnit.Month.plusPages(date, 2))
        assertEquals(LocalDate(2028, 6, 8), KalendarPageUnit.Year.plusPages(date, 2))
    }

    @Test
    fun plusPages_negativeCountStepsBackward() {
        assertEquals(
            LocalDate(2026, 6, 1),
            KalendarPageUnit.Month.plusPages(LocalDate(2026, 8, 1), -2),
        )
    }

    @Test
    fun pagesBetween_countsPagesNotDays() {
        assertEquals(
            1,
            KalendarPageUnit.Month.pagesBetween(from = LocalDate(2026, 8, 31), to = LocalDate(2026, 9, 1)),
            "the last of August and the first of September are one page apart, not one day",
        )
        assertEquals(
            0,
            KalendarPageUnit.Week.pagesBetween(
                from = LocalDate(2026, 6, 8),
                to = LocalDate(2026, 6, 14),
                startDayOfWeek = DayOfWeek.MONDAY,
            ),
            "Monday and the Sunday that closes its week are the same page",
        )
    }

    @Test
    fun pagesBetween_isTheInverseOfPlusPages() {
        val from = LocalDate(2026, 3, 10)
        for (unit in KalendarPageUnit.entries) {
            for (pages in -5..5) {
                val to = unit.plusPages(from, pages)
                assertEquals(pages, unit.pagesBetween(from = from, to = to), "$unit by $pages")
            }
        }
    }

    @Test
    fun pagesBetween_isNegativeWhenTargetPrecedesSource() {
        assertEquals(
            -3,
            KalendarPageUnit.Month.pagesBetween(from = LocalDate(2027, 2, 1), to = LocalDate(2026, 11, 1)),
        )
    }
}
