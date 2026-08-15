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

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/*
 * Every daylight-saving date here is in the past on purpose. Future transitions are projections of
 * the current tzdb rules and move when a legislature changes its mind; a boundary that has already
 * happened is frozen, so these assertions cannot start failing because of a tzdb update.
 */
class KalendarZonedTimeTest {

    private val berlin = TimeZone.of("Europe/Berlin")
    private val tokyo = TimeZone.of("Asia/Tokyo")
    private val london = TimeZone.of("Europe/London")
    private val newYork = TimeZone.of("America/New_York")
    private val lordHowe = TimeZone.of("Australia/Lord_Howe")

    /** Furthest east and furthest west that share a wall clock hour — a full day apart. */
    private val kiritimati = TimeZone.of("Pacific/Kiritimati")
    private val honolulu = TimeZone.of("Pacific/Honolulu")

    @Test
    fun dateIn_acrossTheDateLine_answersTwoDifferentDates() {
        val newYearMorning = Instant.parse("2026-01-01T00:30:00Z")
        assertEquals(LocalDate(2026, 1, 1), newYearMorning.dateIn(kiritimati))
        assertEquals(LocalDate(2025, 12, 31), newYearMorning.dateIn(honolulu))
    }

    @Test
    fun dateIn_lateEvening_isTheSameDayInLondonAndTheNextInTokyo() {
        val lateShift = Instant.parse("2026-01-15T23:00:00Z")
        assertEquals(LocalDate(2026, 1, 15), lateShift.dateIn(london))
        assertEquals(LocalDate(2026, 1, 16), lateShift.dateIn(tokyo))
    }

    @Test
    fun sameInstantIn_movesTheClockFaceAndTheDateWithIt() {
        val lateShift = LocalDateTime(2026, 1, 15, 23, 0)
        assertEquals(lateShift, lateShift.sameInstantIn(source = TimeZone.UTC, target = london))
        assertEquals(
            LocalDateTime(2026, 1, 16, 8, 0),
            lateShift.sameInstantIn(source = TimeZone.UTC, target = tokyo),
        )
    }

    @Test
    fun sameInstantIn_officeZone_keepsTheMeetingAtItsOwnHour() {
        val standUp = LocalDateTime(2026, 1, 15, 9, 0)
        assertEquals(standUp, standUp.sameInstantIn(source = berlin, target = berlin))
        assertEquals(
            LocalDateTime(2026, 1, 15, 17, 0),
            standUp.sameInstantIn(source = berlin, target = tokyo),
        )
    }

    @Test
    fun sameInstantIn_wallClockTimeTheSpringForwardSkipped_stillResolves() {
        val skipped = LocalDateTime(2024, 3, 31, 2, 30)
        assertEquals(
            LocalDateTime(2024, 3, 31, 1, 30),
            skipped.sameInstantIn(source = berlin, target = TimeZone.UTC),
        )
    }

    @Test
    fun dayLengthIn_ordinaryDay_isTwentyFourHours() {
        assertEquals(24.hours, LocalDate(2024, 6, 15).dayLengthIn(berlin))
        assertEquals(24.hours, LocalDate(2024, 6, 15).dayLengthIn(tokyo))
    }

    @Test
    fun dayLengthIn_springForward_isTwentyThreeHours() {
        assertEquals(23.hours, LocalDate(2024, 3, 31).dayLengthIn(berlin))
        assertEquals(23.hours, LocalDate(2024, 3, 10).dayLengthIn(newYork))
    }

    @Test
    fun dayLengthIn_fallBack_isTwentyFiveHours() {
        assertEquals(25.hours, LocalDate(2024, 10, 27).dayLengthIn(berlin))
        assertEquals(25.hours, LocalDate(2024, 11, 3).dayLengthIn(newYork))
    }

    @Test
    fun dayLengthIn_halfHourTransition_isNotAWholeNumberOfHours() {
        assertEquals(23.hours + 30.minutes, LocalDate(2024, 10, 6).dayLengthIn(lordHowe))
        assertEquals(24.hours + 30.minutes, LocalDate(2024, 4, 7).dayLengthIn(lordHowe))
    }

    @Test
    fun dayLengthIn_zoneWithoutDaylightSaving_isNeverAnythingButTwentyFour() {
        assertEquals(24.hours, LocalDate(2024, 3, 31).dayLengthIn(tokyo))
        assertEquals(24.hours, LocalDate(2024, 10, 27).dayLengthIn(tokyo))
    }

    @Test
    fun datesOfSpanIn_eventInsideOneDay_isThatOneDate() {
        val start = LocalDateTime(2026, 1, 15, 9, 0).toInstant(berlin)
        assertEquals(
            listOf(LocalDate(2026, 1, 15)),
            datesOfSpanIn(start = start, end = start + 2.hours, timeZone = berlin),
        )
    }

    @Test
    fun datesOfSpanIn_eventCrossingMidnight_isBothDates() {
        val start = LocalDateTime(2026, 1, 15, 23, 0).toInstant(berlin)
        assertEquals(
            listOf(LocalDate(2026, 1, 15), LocalDate(2026, 1, 16)),
            datesOfSpanIn(start = start, end = start + 2.hours, timeZone = berlin),
        )
    }

    @Test
    fun datesOfSpanIn_eventEndingExactlyAtMidnight_stopsAtTheDayItFills() {
        val start = LocalDateTime(2026, 1, 15, 9, 0).toInstant(berlin)
        val end = LocalDateTime(2026, 1, 16, 0, 0).toInstant(berlin)
        assertEquals(
            listOf(LocalDate(2026, 1, 15)),
            datesOfSpanIn(start = start, end = end, timeZone = berlin),
        )
    }

    @Test
    fun datesOfSpanIn_sameSpanInTwoZones_landsOnDifferentDates() {
        val start = Instant.parse("2026-01-15T23:00:00Z")
        val end = start + 2.hours
        assertEquals(
            listOf(LocalDate(2026, 1, 15), LocalDate(2026, 1, 16)),
            datesOfSpanIn(start = start, end = end, timeZone = london),
        )
        assertEquals(
            listOf(LocalDate(2026, 1, 16)),
            datesOfSpanIn(start = start, end = end, timeZone = tokyo),
        )
    }

    /**
     * 23 real hours, and still two dates. A span counted by dividing its duration by 24 hours calls
     * this one day and drops the second date entirely.
     */
    @Test
    fun datesOfSpanIn_acrossSpringForward_countsTheShortDayAsAWholeDay() {
        val start = LocalDateTime(2024, 3, 30, 23, 0).toInstant(berlin)
        val end = LocalDateTime(2024, 3, 31, 23, 0).toInstant(berlin)
        assertEquals(23.hours, end - start)
        assertEquals(
            listOf(LocalDate(2024, 3, 30), LocalDate(2024, 3, 31)),
            datesOfSpanIn(start = start, end = end, timeZone = berlin),
        )
    }

    /**
     * 25 real hours, and still one date. Dividing by 24 hours finds a whole day and invents a second.
     */
    @Test
    fun datesOfSpanIn_acrossFallBack_countsTheLongDayAsOneDay() {
        val start = LocalDateTime(2024, 10, 27, 0, 0).toInstant(berlin)
        val end = LocalDateTime(2024, 10, 28, 0, 0).toInstant(berlin)
        assertEquals(25.hours, end - start)
        assertEquals(
            listOf(LocalDate(2024, 10, 27)),
            datesOfSpanIn(start = start, end = end, timeZone = berlin),
        )
    }

    @Test
    fun datesOfSpanIn_endBeforeStart_isTheStartDateAlone() {
        val start = LocalDateTime(2026, 1, 15, 9, 0).toInstant(berlin)
        assertEquals(
            listOf(LocalDate(2026, 1, 15)),
            datesOfSpanIn(start = start, end = start - 5.hours, timeZone = berlin),
        )
    }

    @Test
    fun datesOfSpanIn_runawayEnd_stopsAtTheSpanCap() {
        val startDate = LocalDate(2026, 1, 15)
        val start = startDate.atStartOfDayIn(berlin)
        val dates = datesOfSpanIn(start = start, end = start + 3650.days, timeZone = berlin)
        assertEquals(MAX_SPAN_DAYS + 1, dates.size)
        assertEquals(startDate.plus(MAX_SPAN_DAYS, DateTimeUnit.DAY), dates.last())
    }
}
