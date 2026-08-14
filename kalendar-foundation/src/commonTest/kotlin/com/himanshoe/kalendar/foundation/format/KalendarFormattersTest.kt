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

package com.himanshoe.kalendar.foundation.format

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlin.test.Test
import kotlin.test.assertEquals

class KalendarFormattersTest {

    @Test
    fun monthTitle_formatsMonthNameAndYear() {
        assertEquals("August 2026", monthTitle(LocalDate(2026, 8, 1)))
    }

    @Test
    fun monthTitle_ignoresDayOfMonth() {
        assertEquals(monthTitle(LocalDate(2026, 8, 1)), monthTitle(LocalDate(2026, 8, 15)))
    }

    @Test
    fun monthTitle_customFormatter_isUsedInsteadOfDefault() {
        val title = monthTitle(LocalDate(2026, 8, 1)) { month -> "Mois-${month.ordinal + 1}" }
        assertEquals("Mois-8 2026", title)
    }

    @Test
    fun defaults_areTitleCasedEnglishAndZeroPaddedHours() {
        assertEquals("August", KalendarFormatters.monthName(Month.AUGUST))
        assertEquals("Aug", KalendarFormatters.shortMonthName(Month.AUGUST))
        assertEquals("Mon", KalendarFormatters.dayOfWeekName(DayOfWeek.MONDAY))
        assertEquals("M", KalendarFormatters.dayOfWeekLabel(DayOfWeek.MONDAY))
        assertEquals("09:00", KalendarFormatters.hourLabel(9))
        assertEquals("23:00", KalendarFormatters.hourLabel(23))
    }
}
