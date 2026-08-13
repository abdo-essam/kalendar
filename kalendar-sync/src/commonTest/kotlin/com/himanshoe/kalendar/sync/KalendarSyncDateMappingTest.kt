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

package com.himanshoe.kalendar.sync

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class KalendarSyncDateMappingTest {

    @Test
    fun allDayStartUtcMillis_isUtcMidnightRegardlessOfSystemZone() {
        // 2026-01-10T00:00:00Z = 20,463 days after the epoch.
        assertEquals(20_463L * 24 * 60 * 60 * 1000, allDayStartUtcMillis(LocalDate(2026, 1, 10)))
    }

    @Test
    fun allDayEndExclusiveUtcMillis_singleDay_isNextUtcMidnight() {
        assertEquals(
            allDayStartUtcMillis(LocalDate(2026, 1, 11)),
            allDayEndExclusiveUtcMillis(startDate = LocalDate(2026, 1, 10), endDateInclusive = null),
        )
    }

    @Test
    fun allDayEndExclusiveUtcMillis_multiDay_isMidnightAfterInclusiveEnd() {
        assertEquals(
            allDayStartUtcMillis(LocalDate(2026, 1, 13)),
            allDayEndExclusiveUtcMillis(
                startDate = LocalDate(2026, 1, 10),
                endDateInclusive = LocalDate(2026, 1, 12),
            ),
        )
    }

    @Test
    fun allDayDateFromUtcMillis_roundTripsAllDayStartUtcMillis() {
        val date = LocalDate(2026, 8, 12)
        assertEquals(date, allDayDateFromUtcMillis(allDayStartUtcMillis(date)))
    }

    @Test
    fun allDayInclusiveEndDate_nextMidnightConvention_resolvesToPreviousDay() {
        assertEquals(
            LocalDate(2026, 7, 3),
            allDayInclusiveEndDate(
                startDate = LocalDate(2026, 7, 1),
                endDateTime = LocalDateTime(date = LocalDate(2026, 7, 4), time = LocalTime(0, 0)),
            ),
        )
    }

    @Test
    fun allDayInclusiveEndDate_endOfDayConvention_alreadyNamesLastDay() {
        assertEquals(
            LocalDate(2026, 7, 3),
            allDayInclusiveEndDate(
                startDate = LocalDate(2026, 7, 1),
                endDateTime = LocalDateTime(date = LocalDate(2026, 7, 3), time = LocalTime(23, 59, 59)),
            ),
        )
    }

    @Test
    fun allDayInclusiveEndDate_singleDayNextMidnight_returnsNull() {
        assertNull(
            allDayInclusiveEndDate(
                startDate = LocalDate(2026, 7, 1),
                endDateTime = LocalDateTime(date = LocalDate(2026, 7, 2), time = LocalTime(0, 0)),
            ),
        )
    }

    @Test
    fun allDayInclusiveEndDate_singleDayEndOfDay_returnsNull() {
        assertNull(
            allDayInclusiveEndDate(
                startDate = LocalDate(2026, 7, 1),
                endDateTime = LocalDateTime(date = LocalDate(2026, 7, 1), time = LocalTime(23, 59, 59)),
            ),
        )
    }
}
