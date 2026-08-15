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
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Deleting one instance of a recurring series, at each of the three scopes. Assertions are made
 * against the **expanded occurrence list** rather than against the RRULE string, because a rule
 * that reads correctly can still expand wrong — which is how the three recurrence bugs this module
 * has already had got in.
 */
class KalendarRecurrenceDeleteTest {

    @Test
    fun deleteOccurrence_thisOccurrence_removesOnlyThatDate() {
        val series = dailySeries(count = 5)
        val change = series.deleteOccurrence(
            occurrenceDate = LocalDate(2026, 3, 4),
            scope = KalendarRecurrenceScope.THIS_OCCURRENCE,
        )
        assertEquals(emptyList(), change.newEvents)
        assertTrue(!change.deletesSeries)
        assertEquals(
            listOf(
                LocalDate(2026, 3, 2),
                LocalDate(2026, 3, 3),
                LocalDate(2026, 3, 5),
                LocalDate(2026, 3, 6),
            ),
            change.updatedSeries!!.occurrenceDates(),
        )
    }

    @Test
    fun deleteOccurrence_thisOccurrence_isIdempotent() {
        val series = dailySeries(count = 5)
        val once = series.deleteOccurrence(
            occurrenceDate = LocalDate(2026, 3, 4),
            scope = KalendarRecurrenceScope.THIS_OCCURRENCE,
        ).updatedSeries!!
        val twice = once.deleteOccurrence(
            occurrenceDate = LocalDate(2026, 3, 4),
            scope = KalendarRecurrenceScope.THIS_OCCURRENCE,
        ).updatedSeries!!
        assertEquals(listOf(LocalDate(2026, 3, 4)), twice.exceptionDates)
        assertEquals(once.occurrenceDates(), twice.occurrenceDates())
    }

    @Test
    fun deleteOccurrence_thisAndFollowing_endsTheSeriesBeforeTheOccurrence() {
        val series = dailySeries()
        val change = series.deleteOccurrence(
            occurrenceDate = LocalDate(2026, 3, 10),
            scope = KalendarRecurrenceScope.THIS_AND_FOLLOWING,
        )
        assertEquals(emptyList(), change.newEvents)
        val kept = change.updatedSeries!!.occurrenceDates()
        assertEquals(LocalDate(2026, 3, 9), kept.last())
        assertEquals(LocalDate(2026, 3, 2), kept.first())
    }

    @Test
    fun deleteOccurrence_thisAndFollowing_atTheFirstOccurrence_deletesTheWholeSeries() {
        val series = dailySeries()
        val change = series.deleteOccurrence(
            occurrenceDate = LocalDate(2026, 3, 2),
            scope = KalendarRecurrenceScope.THIS_AND_FOLLOWING,
        )
        assertTrue(change.deletesSeries)
        assertNull(change.updatedSeries)
        assertEquals(emptyList(), change.newEvents)
    }

    @Test
    fun deleteOccurrence_thisAndFollowing_beforeTheSeriesEvenStarts_deletesTheWholeSeries() {
        val series = dailySeries()
        val change = series.deleteOccurrence(
            occurrenceDate = LocalDate(2026, 2, 1),
            scope = KalendarRecurrenceScope.THIS_AND_FOLLOWING,
        )
        assertTrue(change.deletesSeries)
    }

    @Test
    fun deleteOccurrence_entireSeries_deletesTheSeriesRow() {
        val series = dailySeries()
        val change = series.deleteOccurrence(
            occurrenceDate = LocalDate(2026, 3, 10),
            scope = KalendarRecurrenceScope.ENTIRE_SERIES,
        )
        assertTrue(change.deletesSeries)
        assertEquals(emptyList(), change.newEvents)
    }

    @Test
    fun deleteOccurrence_nonRecurringEvent_deletesItUnderEveryScope() {
        val oneOff = BasicKalendarSyncEvent(
            id = "one-off",
            date = LocalDate(2026, 3, 4),
            eventName = "Dentist",
        )
        for (scope in KalendarRecurrenceScope.entries) {
            assertTrue(
                oneOff.deleteOccurrence(occurrenceDate = LocalDate(2026, 3, 4), scope = scope).deletesSeries,
                "scope $scope",
            )
        }
    }

    @Test
    fun everyScopedChange_eitherRewritesTheSeriesOrRemovesItWithNothingToInsert() {
        val series = listOf(dailySeries(), dailySeries(count = 6), dailySeries(until = LocalDate(2026, 3, 8)))
        val dates = listOf(LocalDate(2026, 2, 20), LocalDate(2026, 3, 2), LocalDate(2026, 3, 5), LocalDate(2026, 4, 30))
        for (event in series) {
            for (date in dates) {
                for (scope in KalendarRecurrenceScope.entries) {
                    val changes = listOf(
                        event.deleteOccurrence(occurrenceDate = date, scope = scope),
                        event.editOccurrence(
                            occurrenceDate = date,
                            edited = editedOccurrence(date, name = "Standup"),
                            scope = scope,
                        ),
                    )
                    for (change in changes) {
                        assertTrue(
                            !change.deletesSeries || change.newEvents.isEmpty(),
                            "a change that deletes the series must insert nothing: $date/$scope",
                        )
                        assertTrue(
                            change.newEvents.all { it.id == null },
                            "inserted events must carry no id: $date/$scope",
                        )
                    }
                }
            }
        }
    }
}
