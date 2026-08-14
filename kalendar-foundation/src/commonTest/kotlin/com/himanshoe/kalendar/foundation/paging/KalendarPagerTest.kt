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
import kotlin.test.assertTrue

class KalendarPagerTest {

    private val today = LocalDate(2026, 3, 10)

    @Test
    fun endless_opensAtTheCentreOfTheIndexSpace() {
        val pager = KalendarPager.endless(unit = KalendarPageUnit.Month, initialDate = today)

        assertEquals(KalendarPager.CENTER_PAGE, pager.initialPage)
        assertEquals(Int.MAX_VALUE, pager.pageCount)
        assertEquals(LocalDate(2026, 3, 1), pager.dateAt(pager.initialPage))
    }

    @Test
    fun endless_pageAndDateAreInverses() {
        val pager = KalendarPager.endless(unit = KalendarPageUnit.Month, initialDate = today)

        for (offset in -24..24) {
            val page = pager.initialPage + offset
            assertEquals(page, pager.pageOf(pager.dateAt(page)), "offset $offset")
        }
    }

    @Test
    fun endless_weekPagesAreKeyedByTheirStartDayOfWeek() {
        val pager = KalendarPager.endless(
            unit = KalendarPageUnit.Week,
            initialDate = today,
            startDayOfWeek = DayOfWeek.SUNDAY,
        )

        assertEquals(LocalDate(2026, 3, 8), pager.dateAt(pager.initialPage), "the Sunday of today's week")
        assertEquals(LocalDate(2026, 3, 15), pager.dateAt(pager.initialPage + 1))
    }

    @Test
    fun endless_boundsNarrowTheReachableRangeWithoutShorteningTheAxis() {
        val pager = KalendarPager.endless(
            unit = KalendarPageUnit.Month,
            initialDate = today,
            minDate = LocalDate(2026, 1, 15),
            maxDate = LocalDate(2026, 5, 20),
        )

        assertEquals(Int.MAX_VALUE, pager.pageCount, "a swipeable pager still spans the whole index space")
        assertEquals(LocalDate(2026, 1, 1), pager.dateAt(pager.firstPage), "a mid-month bound still yields its month")
        assertEquals(LocalDate(2026, 5, 1), pager.dateAt(pager.lastPage))
    }

    @Test
    fun endless_anInitialDateOutsideTheBoundsOpensOnTheNearestBound() {
        val pager = KalendarPager.endless(
            unit = KalendarPageUnit.Month,
            initialDate = LocalDate(2026, 1, 10),
            minDate = LocalDate(2026, 4, 1),
        )

        assertEquals(pager.firstPage, pager.initialPage)
        assertEquals(LocalDate(2026, 4, 1), pager.dateAt(pager.initialPage))
    }

    @Test
    fun bounded_bothBoundsGiveAnExactPageCountStartingAtZero() {
        val pager = KalendarPager.bounded(
            unit = KalendarPageUnit.Month,
            initialDate = today,
            minDate = LocalDate(2026, 1, 1),
            maxDate = LocalDate(2026, 12, 31),
        )

        assertEquals(12, pager.pageCount)
        assertEquals(0, pager.firstPage)
        assertEquals(11, pager.lastPage)
        assertEquals(LocalDate(2026, 1, 1), pager.dateAt(0))
        assertEquals(LocalDate(2026, 12, 1), pager.dateAt(11))
        assertEquals(2, pager.initialPage, "March is the third month")
    }

    @Test
    fun bounded_minDateOnlyAnchorsPageZeroAtTheBound() {
        val pager = KalendarPager.bounded(
            unit = KalendarPageUnit.Month,
            initialDate = today,
            minDate = LocalDate(2026, 1, 1),
        )

        assertEquals(Int.MAX_VALUE, pager.pageCount)
        assertEquals(0, pager.firstPage)
        assertEquals(LocalDate(2026, 1, 1), pager.dateAt(0))
    }

    @Test
    fun bounded_maxDateOnlyAnchorsTheEndOfTheAxisAtTheBound() {
        val pager = KalendarPager.bounded(
            unit = KalendarPageUnit.Month,
            initialDate = today,
            maxDate = LocalDate(2026, 4, 30),
        )

        assertEquals(Int.MAX_VALUE, pager.pageCount)
        assertEquals(Int.MAX_VALUE - 1, pager.lastPage)
        assertEquals(LocalDate(2026, 4, 1), pager.dateAt(pager.lastPage))
    }

    @Test
    fun bounded_maxDateOnlyOverflow_doesNotWrapNegative() {
        // The origin sits one below Int.MAX_VALUE, so a date past the bound overflows Int arithmetic.
        val pager = KalendarPager.bounded(
            unit = KalendarPageUnit.Month,
            initialDate = today,
            maxDate = LocalDate(2026, 4, 30),
        )

        assertEquals(Int.MAX_VALUE - 1, pager.pageOf(LocalDate(2030, 1, 1)))
    }

    @Test
    fun bounded_anInitialDateOutsideTheBoundsIsClampedIntoThem() {
        val pager = KalendarPager.bounded(
            unit = KalendarPageUnit.Month,
            initialDate = LocalDate(2025, 1, 1),
            minDate = LocalDate(2026, 3, 1),
            maxDate = LocalDate(2026, 4, 30),
        )

        assertEquals(0, pager.initialPage)
        assertTrue(pager.initialPage in 0 until pager.pageCount)
    }

    @Test
    fun pageOf_clampsBeforeTheFirstPageAndPastTheLast() {
        val pager = KalendarPager.bounded(
            unit = KalendarPageUnit.Month,
            initialDate = today,
            minDate = LocalDate(2026, 1, 1),
            maxDate = LocalDate(2026, 12, 31),
        )

        assertEquals(0, pager.pageOf(LocalDate(2020, 5, 1)))
        assertEquals(11, pager.pageOf(LocalDate(2030, 5, 1)))
        assertEquals(5, pager.pageOf(LocalDate(2026, 6, 15)))
    }

    @Test
    fun coercePage_pullsAComputedIndexBackIntoTheAxis() {
        val pager = KalendarPager.bounded(
            unit = KalendarPageUnit.Month,
            initialDate = today,
            minDate = LocalDate(2026, 1, 1),
            maxDate = LocalDate(2026, 12, 31),
        )

        assertEquals(0, pager.coercePage(-1))
        assertEquals(11, pager.coercePage(40))
        assertEquals(5, pager.coercePage(5))
    }

    @Test
    fun dayPagesStepOneDayAtATime() {
        val pager = KalendarPager.endless(unit = KalendarPageUnit.Day, initialDate = today)

        assertEquals(today, pager.dateAt(pager.initialPage))
        assertEquals(LocalDate(2026, 3, 11), pager.dateAt(pager.initialPage + 1))
        assertEquals(pager.initialPage + 1, pager.pageOf(LocalDate(2026, 3, 11)))
    }

    @Test
    fun yearPagesStepOneYearAtATime() {
        val pager = KalendarPager.endless(unit = KalendarPageUnit.Year, initialDate = today)

        assertEquals(LocalDate(2026, 1, 1), pager.dateAt(pager.initialPage))
        assertEquals(LocalDate(2027, 1, 1), pager.dateAt(pager.initialPage + 1))
    }

    @Test
    fun aSingleDayOfRangeIsStillOnePage() {
        val pager = KalendarPager.bounded(
            unit = KalendarPageUnit.Week,
            initialDate = today,
            minDate = today,
            maxDate = today,
        )

        assertEquals(1, pager.pageCount)
        assertEquals(0, pager.firstPage)
        assertEquals(0, pager.lastPage)
    }

    @Test
    fun crossedBoundsStillYieldAnAxisWithAPageOnIt() {
        val pager = KalendarPager.bounded(
            unit = KalendarPageUnit.Month,
            initialDate = today,
            minDate = LocalDate(2030, 1, 1),
            maxDate = LocalDate(2020, 1, 1),
        )

        assertEquals(1, pager.pageCount)
        assertEquals(0, pager.pageOf(today), "no index a lazy list would reject")
    }

    @Test
    fun equality_isStructuralOverTheAxisItDescribes() {
        val one = KalendarPager.endless(unit = KalendarPageUnit.Month, initialDate = today)
        val same = KalendarPager.endless(unit = KalendarPageUnit.Month, initialDate = LocalDate(2026, 3, 20))
        val other = KalendarPager.endless(unit = KalendarPageUnit.Week, initialDate = today)

        assertEquals(one, same, "two dates in the same month describe the same month axis")
        assertEquals(one.hashCode(), same.hashCode())
        assertTrue(one != other)
    }
}
