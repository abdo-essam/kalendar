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

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class KalendarDateArithmeticTest {

    @Test
    fun startOfWeek_wednesday_mondayStart_returnsMonday() {
        val wednesday = LocalDate(2026, 6, 10)
        assertEquals(LocalDate(2026, 6, 8), wednesday.startOfWeek(DayOfWeek.MONDAY))
    }

    @Test
    fun startOfWeek_wednesday_sundayStart_returnsSunday() {
        val wednesday = LocalDate(2026, 6, 10)
        assertEquals(LocalDate(2026, 6, 7), wednesday.startOfWeek(DayOfWeek.SUNDAY))
    }

    @Test
    fun startOfWeek_alreadyStartOfWeek_returnsSameDate() {
        val monday = LocalDate(2026, 6, 8)
        assertEquals(monday, monday.startOfWeek(DayOfWeek.MONDAY))
    }

    @Test
    fun startOfMonth_midMonth_returnsFirstOfMonth() {
        val date = LocalDate(2026, 6, 15)
        assertEquals(LocalDate(2026, 6, 1), date.startOfMonth())
    }

    @Test
    fun startOfMonth_alreadyFirstOfMonth_returnsSameDate() {
        val date = LocalDate(2026, 6, 1)
        assertEquals(date, date.startOfMonth())
    }

    @Test
    fun startOfYear_midYear_returnsJanuaryFirst() {
        val date = LocalDate(2026, 6, 15)
        assertEquals(LocalDate(2026, 1, 1), date.startOfYear())
    }

    @Test
    fun weeksBetween_sameWeek_returnsZero() {
        val monday = LocalDate(2026, 6, 8)
        val sunday = LocalDate(2026, 6, 14)
        assertEquals(0, weeksBetween(monday, sunday, DayOfWeek.MONDAY))
    }

    @Test
    fun weeksBetween_nextWeek_returnsOne() {
        val from = LocalDate(2026, 6, 8)
        val to = LocalDate(2026, 6, 15)
        assertEquals(1, weeksBetween(from, to, DayOfWeek.MONDAY))
    }

    @Test
    fun weeksBetween_previousWeek_returnsNegativeOne() {
        val from = LocalDate(2026, 6, 15)
        val to = LocalDate(2026, 6, 8)
        assertEquals(-1, weeksBetween(from, to, DayOfWeek.MONDAY))
    }

    @Test
    fun monthsBetween_sameMonth_returnsZero() {
        val early = LocalDate(2026, 6, 1)
        val late = LocalDate(2026, 6, 28)
        assertEquals(0, monthsBetween(early, late))
    }

    @Test
    fun monthsBetween_nextMonth_returnsOne() {
        val from = LocalDate(2026, 6, 15)
        val to = LocalDate(2026, 7, 1)
        assertEquals(1, monthsBetween(from, to))
    }

    @Test
    fun monthsBetween_acrossYearBoundary_countsCorrectly() {
        val from = LocalDate(2026, 11, 1)
        val to = LocalDate(2027, 2, 1)
        assertEquals(3, monthsBetween(from, to))
    }

    @Test
    fun yearsBetween_sameYear_returnsZero() {
        val from = LocalDate(2026, 1, 1)
        val to = LocalDate(2026, 12, 31)
        assertEquals(0, yearsBetween(from, to))
    }

    @Test
    fun yearsBetween_nextYear_returnsOne() {
        val from = LocalDate(2026, 6, 1)
        val to = LocalDate(2027, 1, 1)
        assertEquals(1, yearsBetween(from, to))
    }
}
