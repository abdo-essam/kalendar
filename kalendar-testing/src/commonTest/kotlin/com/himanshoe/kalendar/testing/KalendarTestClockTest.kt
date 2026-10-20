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

package com.himanshoe.kalendar.testing

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.minutes

/**
 * The two promises the time-control API makes: a pinned clock never moves, and an advanced clock
 * moves exactly as far as the test said — including across the midnights and zone boundaries that
 * are the whole reason calendars need a controllable clock.
 */
class KalendarTestClockTest {

    @Test
    fun fixedTimeSourcePinsTodayAndNow() {
        val source = fixedKalendarTimeSource(now = LocalDateTime(2026, 3, 10, 9, 0))

        assertEquals(LocalDate(2026, 3, 10), source.today())
        assertEquals(LocalDateTime(2026, 3, 10, 9, 0), source.now())
        assertEquals(LocalDate(2026, 3, 10), source.today(), "asking again moves nothing")
    }

    @Test
    fun fixedTimeSourceResolvesTheWallClockInTheGivenZone() {
        val tokyo = TimeZone.of("Asia/Tokyo")
        val source = fixedKalendarTimeSource(now = LocalDateTime(2026, 3, 10, 9, 0), timeZone = tokyo)

        assertEquals(LocalDate(2026, 3, 10), source.today())
        assertEquals(
            LocalDateTime(2026, 3, 10, 9, 0).toInstant(tokyo),
            source.clock.now(),
            "09:00 in Tokyo is the pinned instant, not 09:00 UTC",
        )
    }

    @Test
    fun advanceByCrossesMidnightAndTodayFollows() {
        val clock = KalendarTestClock(start = LocalDateTime(2026, 3, 10, 23, 59))
        val source = clock.asTimeSource()
        assertEquals(LocalDate(2026, 3, 10), source.today())

        clock.advanceBy(2.minutes)

        assertEquals(LocalDate(2026, 3, 11), source.today())
        assertEquals(LocalDateTime(2026, 3, 11, 0, 1), source.now())
    }

    @Test
    fun assigningTheInstantJumpsTheClockBackwardsToo() {
        val clock = KalendarTestClock(start = LocalDateTime(2026, 3, 10, 9, 0))
        val source = clock.asTimeSource()

        clock.instant = LocalDateTime(2025, 12, 31, 23, 0).toInstant(TimeZone.UTC)

        assertEquals(LocalDate(2025, 12, 31), source.today())
    }

    @Test
    fun oneClockBacksTwoZonesThatDisagreeAboutToday() {
        val clock = KalendarTestClock(start = LocalDateTime(2026, 1, 15, 23, 0))
        val utc = clock.asTimeSource()
        val tokyo = clock.asTimeSource(timeZone = TimeZone.of("Asia/Tokyo"))

        assertEquals(LocalDate(2026, 1, 15), utc.today())
        assertEquals(LocalDate(2026, 1, 16), tokyo.today(), "already tomorrow in Tokyo")
    }
}
