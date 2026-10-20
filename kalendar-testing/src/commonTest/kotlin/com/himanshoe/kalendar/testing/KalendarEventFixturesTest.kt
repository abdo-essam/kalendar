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

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

/**
 * What the fixtures promise: defaults a test can lean on without reading them, and the date/time
 * arithmetic — durations, spans, recurrence steps — done right at the edges.
 */
class KalendarEventFixturesTest {

    private val august15 = LocalDate(2026, 8, 15)

    @Test
    fun plainEventCarriesItsDateAndNothingItWasNotGiven() {
        val event = kalendarEvent(date = august15)

        assertEquals(august15, event.date)
        assertEquals("Test event", event.eventName)
        assertNull(event.id)
        assertNull(event.startTime)
        assertNull(event.endTime)
        assertNull(event.endDate)
        assertNull(event.eventColor)
    }

    @Test
    fun timedEventEndsDurationAfterItStarts() {
        val event = timedKalendarEvent(date = august15, start = LocalTime(9, 0))

        assertEquals(LocalDateTime(2026, 8, 15, 9, 0), event.startTime)
        assertEquals(LocalDateTime(2026, 8, 15, 10, 0), event.endTime, "one hour by default")

        val short = timedKalendarEvent(date = august15, start = LocalTime(14, 0), duration = 30.minutes)
        assertEquals(LocalDateTime(2026, 8, 15, 14, 30), short.endTime)
    }

    @Test
    fun timedEventCrossingMidnightEndsOnTheNextDateButStaysSingleDay() {
        val event = timedKalendarEvent(date = august15, start = LocalTime(23, 30))

        assertEquals(LocalDateTime(2026, 8, 16, 0, 30), event.endTime)
        assertNull(event.endDate, "a timed event is not a span; multiDayKalendarEvent is")
    }

    @Test
    fun multiDayEventOccupiesItsWholeSpan() {
        val event = multiDayKalendarEvent(start = august15, end = LocalDate(2026, 8, 17), id = "conf")

        assertEquals(august15, event.date)
        assertEquals(LocalDate(2026, 8, 17), event.endDate)
        assertEquals("conf", event.id)
    }

    @Test
    fun multiDayEventRejectsAReversedSpan() {
        assertFailsWith<IllegalArgumentException> {
            multiDayKalendarEvent(start = august15, end = LocalDate(2026, 8, 14))
        }
    }

    @Test
    fun recurringEventsAreWeeklyByDefaultWithDistinctIds() {
        val standups = recurringKalendarEvents(start = LocalDate(2026, 8, 3), count = 4, name = "standup")

        assertEquals(
            listOf(
                LocalDate(2026, 8, 3),
                LocalDate(2026, 8, 10),
                LocalDate(2026, 8, 17),
                LocalDate(2026, 8, 24),
            ),
            standups.map { occurrence -> occurrence.date },
        )
        assertTrue(standups.all { occurrence -> occurrence.eventName == "standup" })
        assertEquals(
            listOf("standup-0", "standup-1", "standup-2", "standup-3"),
            standups.map { occurrence -> occurrence.id },
            "shared ids would be de-duplicated into one event by the loader",
        )
    }

    @Test
    fun recurringEventsFollowTheGivenStep() {
        val monthly = recurringKalendarEvents(
            start = LocalDate(2026, 1, 31),
            count = 3,
            every = DatePeriod(months = 1),
        )

        assertEquals(
            listOf(LocalDate(2026, 1, 31), LocalDate(2026, 2, 28), LocalDate(2026, 3, 31)),
            monthly.map { occurrence -> occurrence.date },
            "each occurrence steps from the start, so February's clamp does not drift the rest",
        )
    }

    @Test
    fun recurringEventsWithZeroCountAreEmptyAndNegativeIsRejected() {
        assertTrue(recurringKalendarEvents(start = august15, count = 0).isEmpty())
        assertFailsWith<IllegalArgumentException> {
            recurringKalendarEvents(start = august15, count = -1)
        }
    }
}
