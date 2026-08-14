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

package com.himanshoe.kalendar

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@Suppress("MagicNumber")
class KalendarDatePickerYearRangeTest {

    @Test
    fun unbounded_spansACenturyEitherSide() {
        val range = datePickerYearRange(anchorYear = 2026)
        assertEquals(1926..2126, range)
    }

    @Test
    fun unbounded_reachesATypicalBirthYearWithoutStepping() {
        assertTrue(1950 in datePickerYearRange(anchorYear = 2026))
    }

    @Test
    fun minDate_replacesTheLowerBoundSoNoOfferedYearIsRefused() {
        val range = datePickerYearRange(anchorYear = 2026, minDate = LocalDate(2020, 6, 1))
        assertEquals(2020..2126, range)
    }

    @Test
    fun maxDate_replacesTheUpperBound() {
        val range = datePickerYearRange(anchorYear = 2026, maxDate = LocalDate(2030, 6, 1))
        assertEquals(1926..2030, range)
    }

    @Test
    fun bothBounds_narrowToExactlyTheAllowedYears() {
        val range = datePickerYearRange(
            anchorYear = 2026,
            minDate = LocalDate(2024, 1, 1),
            maxDate = LocalDate(2028, 12, 31),
        )
        assertEquals(2024..2028, range)
    }

    @Test
    fun invertedBounds_collapseToTheLowerYearRatherThanEmptyingTheControl() {
        val range = datePickerYearRange(
            anchorYear = 2026,
            minDate = LocalDate(2030, 1, 1),
            maxDate = LocalDate(2020, 1, 1),
        )
        assertEquals(2030..2030, range)
    }
}
