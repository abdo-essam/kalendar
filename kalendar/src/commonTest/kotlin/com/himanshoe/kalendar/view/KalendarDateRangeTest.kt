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

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class KalendarDateRangeTest {

    @Test
    fun datesUntil_sameDate_returnsSingleDate() {
        val date = LocalDate(2026, 6, 15)
        assertEquals(setOf(date), date.datesUntil(date))
    }

    @Test
    fun datesUntil_forwardRange_includesBothEndpoints() {
        val start = LocalDate(2026, 6, 1)
        val end = LocalDate(2026, 6, 3)
        val range = start.datesUntil(end)
        assertEquals(
            setOf(LocalDate(2026, 6, 1), LocalDate(2026, 6, 2), LocalDate(2026, 6, 3)),
            range,
        )
    }

    @Test
    fun datesUntil_reversedArguments_returnsSameRangeAsForward() {
        val start = LocalDate(2026, 6, 1)
        val end = LocalDate(2026, 6, 3)
        assertEquals(start.datesUntil(end), end.datesUntil(start))
    }

    @Test
    fun datesUntil_acrossMonthBoundary_countsCorrectly() {
        val start = LocalDate(2026, 5, 30)
        val end = LocalDate(2026, 6, 2)
        assertEquals(4, start.datesUntil(end).size)
    }
}
