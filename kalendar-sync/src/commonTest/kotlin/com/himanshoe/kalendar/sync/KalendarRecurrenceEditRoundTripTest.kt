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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Scoped edits and deletions written out as an `.ics` file and read back, which is the trip that
 * turns a rule into text and turns a consumer's parse of that text back into occurrences. A split
 * that lands correctly in memory can still be lost in the encoding — an UNTIL rendered in a value
 * type the DTSTART does not share, or an EXDATE whose time does not match the occurrence it is
 * meant to remove — and only expanding what came back out shows it.
 */
class KalendarRecurrenceEditRoundTripTest {

    @Test
    fun editOccurrence_thisOccurrence_roundTripsThroughTheIcalCodec() {
        val series = dailySeries(count = 5)
        val change = series.editOccurrence(
            occurrenceDate = LocalDate(2026, 3, 4),
            edited = editedOccurrence(LocalDate(2026, 3, 4)),
            scope = KalendarRecurrenceScope.THIS_OCCURRENCE,
        )
        val decoded = roundTrip(change)
        assertEquals(
            listOf(
                LocalDate(2026, 3, 2),
                LocalDate(2026, 3, 3),
                LocalDate(2026, 3, 4),
                LocalDate(2026, 3, 5),
                LocalDate(2026, 3, 6),
            ),
            decoded.flatMap { it.occurrenceDates() }.sorted(),
        )
        val onSplitDate = decoded.flatMap { event ->
            event.expandOccurrences(rangeStart = WindowStart, rangeEnd = WindowEnd)
        }.filter { it.date == LocalDate(2026, 3, 4) }
        assertEquals(listOf("Standup (extended)"), onSplitDate.map { it.eventName })
    }

    @Test
    fun editOccurrence_thisOccurrence_detachedEventGetsItsOwnUidInTheEncodedFile() {
        val series = dailySeries()
        val change = series.editOccurrence(
            occurrenceDate = series.date,
            edited = editedOccurrence(series.date, name = "Standup"),
            scope = KalendarRecurrenceScope.THIS_OCCURRENCE,
        )
        val uids = exportToIcs(listOfNotNull(change.updatedSeries?.copyAsBasic(id = null)) + change.newEvents)
            .lines()
            .filter { it.startsWith("UID:") }
        assertEquals(2, uids.size)
        assertNotEquals(uids[0], uids[1])
    }

    @Test
    fun editOccurrence_thisAndFollowing_timedSeriesRoundTripsThroughTheIcalCodecWithItsBoundaryIntact() {
        val series = dailySeries()
        val change = series.editOccurrence(
            occurrenceDate = LocalDate(2026, 3, 10),
            edited = editedOccurrence(LocalDate(2026, 3, 10), name = "Standup"),
            scope = KalendarRecurrenceScope.THIS_AND_FOLLOWING,
        )
        val decoded = roundTrip(change)
        assertEquals(
            series.expandOccurrences(rangeStart = WindowStart, rangeEnd = LocalDate(2026, 3, 31)).map { it.date },
            decoded.flatMap { it.expandOccurrences(rangeStart = WindowStart, rangeEnd = LocalDate(2026, 3, 31)) }
                .map { it.date }
                .sorted(),
        )
    }

    @Test
    fun editOccurrence_thisAndFollowing_allDaySeriesRoundTripsThroughTheIcalCodecWithItsBoundaryIntact() {
        val series = BasicKalendarSyncEvent(
            id = "series-4",
            date = LocalDate(2026, 3, 2),
            eventName = "Sprint day",
            recurrenceRule = KalendarRule(frequency = KalendarRecurrenceFrequency.DAILY),
        )
        val change = series.editOccurrence(
            occurrenceDate = LocalDate(2026, 3, 10),
            edited = BasicKalendarSyncEvent(date = LocalDate(2026, 3, 10), eventName = "Sprint day"),
            scope = KalendarRecurrenceScope.THIS_AND_FOLLOWING,
        )
        val decoded = roundTrip(change)
        assertEquals(
            series.expandOccurrences(rangeStart = WindowStart, rangeEnd = LocalDate(2026, 3, 31)).map { it.date },
            decoded.flatMap { it.expandOccurrences(rangeStart = WindowStart, rangeEnd = LocalDate(2026, 3, 31)) }
                .map { it.date }
                .sorted(),
        )
    }

    @Test
    fun deleteOccurrence_thisAndFollowing_roundTripsThroughTheIcalCodecWithoutResurrectingTheOccurrence() {
        val series = dailySeries()
        val change = series.deleteOccurrence(
            occurrenceDate = LocalDate(2026, 3, 10),
            scope = KalendarRecurrenceScope.THIS_AND_FOLLOWING,
        )
        val kept = roundTrip(change).single().occurrenceDates()
        assertEquals(LocalDate(2026, 3, 9), kept.last())
        assertTrue(LocalDate(2026, 3, 10) !in kept)
    }

    @Test
    fun deleteOccurrence_thisOccurrence_roundTripsThroughTheIcalCodecOnATimedSeries() {
        val series = dailySeries(count = 5)
        val change = series.deleteOccurrence(
            occurrenceDate = LocalDate(2026, 3, 4),
            scope = KalendarRecurrenceScope.THIS_OCCURRENCE,
        )
        assertEquals(
            listOf(
                LocalDate(2026, 3, 2),
                LocalDate(2026, 3, 3),
                LocalDate(2026, 3, 5),
                LocalDate(2026, 3, 6),
            ),
            roundTrip(change).single().occurrenceDates(),
        )
    }
}
