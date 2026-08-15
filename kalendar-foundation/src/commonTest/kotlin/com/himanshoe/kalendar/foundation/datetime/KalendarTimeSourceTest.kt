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

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/** A clock frozen at [instant], so every date this test derives is the same one forever. */
private fun clockAt(instant: Instant): Clock = object : Clock {
    override fun now(): Instant = instant
}

class KalendarTimeSourceTest {

    private val berlin = TimeZone.of("Europe/Berlin")
    private val tokyo = TimeZone.of("Asia/Tokyo")
    private val london = TimeZone.of("Europe/London")

    @Test
    fun today_sameInstantInTwoZones_isTwoDifferentDates() {
        val clock = clockAt(Instant.parse("2026-01-15T23:00:00Z"))
        assertEquals(LocalDate(2026, 1, 15), KalendarTimeSource(timeZone = london, clock = clock).today())
        assertEquals(LocalDate(2026, 1, 16), KalendarTimeSource(timeZone = tokyo, clock = clock).today())
    }

    @Test
    fun now_readsTheClockFaceOfItsOwnZoneNotTheDevices() {
        val clock = clockAt(Instant.parse("2026-01-15T08:00:00Z"))
        assertEquals(
            LocalDateTime(2026, 1, 15, 9, 0),
            KalendarTimeSource(timeZone = berlin, clock = clock).now(),
        )
        assertEquals(
            LocalDateTime(2026, 1, 15, 17, 0),
            KalendarTimeSource(timeZone = tokyo, clock = clock).now(),
        )
    }

    @Test
    fun dateOf_andTimeOf_agreeWithTodayAndNow() {
        val instant = Instant.parse("2026-01-15T23:00:00Z")
        val source = KalendarTimeSource(timeZone = tokyo, clock = clockAt(instant))
        assertEquals(source.today(), source.dateOf(instant))
        assertEquals(source.now(), source.timeOf(instant))
        assertEquals(source.dateOf(instant), source.timeOf(instant).date)
    }

    @Test
    fun untilNextDay_ordinaryDay_isWhatIsLeftOfTwentyFourHours() {
        val source = KalendarTimeSource(
            timeZone = berlin,
            clock = clockAt(LocalDateTime(2026, 1, 15, 9, 0).toInstant(berlin)),
        )
        assertEquals(15.hours, source.untilNextDay())
    }

    /**
     * Midnight on the day the clocks go forward is 23 hours away, not 24. A rollover that subtracts
     * the time of day from a fixed 24 hours fires an hour after the day has already turned.
     */
    @Test
    fun untilNextDay_springForward_isOneHourShorter() {
        val source = KalendarTimeSource(
            timeZone = berlin,
            clock = clockAt(LocalDateTime(2024, 3, 31, 0, 0).toInstant(berlin)),
        )
        assertEquals(23.hours, source.untilNextDay())
    }

    /** And 25 on the day they go back, where the same arithmetic fires an hour early. */
    @Test
    fun untilNextDay_fallBack_isOneHourLonger() {
        val source = KalendarTimeSource(
            timeZone = berlin,
            clock = clockAt(LocalDateTime(2024, 10, 27, 0, 0).toInstant(berlin)),
        )
        assertEquals(25.hours, source.untilNextDay())
    }

    @Test
    fun untilNextDay_matchesTheDayItIsActuallyIn() {
        val date = LocalDate(2024, 10, 27)
        val source = KalendarTimeSource(
            timeZone = berlin,
            clock = clockAt(date.atStartOfDayIn(berlin)),
        )
        assertEquals(date.dayLengthIn(berlin), source.untilNextDay())
    }

    @Test
    fun equality_distinguishesZones() {
        val clock = clockAt(Instant.parse("2026-01-15T09:00:00Z"))
        assertNotEquals(
            KalendarTimeSource(timeZone = berlin, clock = clock),
            KalendarTimeSource(timeZone = tokyo, clock = clock),
        )
        assertEquals(
            KalendarTimeSource(timeZone = berlin, clock = clock),
            KalendarTimeSource(timeZone = berlin, clock = clock).copy(),
        )
    }
}
