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
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Editing one instance of a recurring series, at each of the three scopes. Assertions are made
 * against the **expanded occurrence list** rather than against the RRULE string, because a rule
 * that reads correctly can still expand wrong — which is how the three recurrence bugs this module
 * has already had got in.
 */
class KalendarRecurrenceEditTest {

    @Test
    fun editOccurrence_thisOccurrence_seriesSkipsTheDateAndDetachedEventCoversIt() {
        val series = dailySeries(count = 5)
        val change = series.editOccurrence(
            occurrenceDate = LocalDate(2026, 3, 4),
            edited = editedOccurrence(LocalDate(2026, 3, 4)),
            scope = KalendarRecurrenceScope.THIS_OCCURRENCE,
        )
        val kept = change.updatedSeries!!.occurrenceDates()
        assertEquals(
            listOf(
                LocalDate(2026, 3, 2),
                LocalDate(2026, 3, 3),
                LocalDate(2026, 3, 5),
                LocalDate(2026, 3, 6),
            ),
            kept,
        )
        assertEquals(listOf(LocalDate(2026, 3, 4)), change.newEvents.single().occurrenceDates())
    }

    @Test
    fun editOccurrence_thisOccurrence_exclusionDoesNotShortenTheSeries() {
        val series = dailySeries(count = 5)
        val change = series.editOccurrence(
            occurrenceDate = LocalDate(2026, 3, 4),
            edited = editedOccurrence(LocalDate(2026, 3, 4)),
            scope = KalendarRecurrenceScope.THIS_OCCURRENCE,
        )
        assertEquals(LocalDate(2026, 3, 6), change.updatedSeries!!.occurrenceDates().last())
    }

    @Test
    fun editOccurrence_thisOccurrence_detachedEventDoesNotInheritTheRule() {
        // The edit starts from a materialised occurrence, which is what a consumer has in hand and
        // which still carries the series' rule and exception list — both have to be dropped, or
        // the detached copy expands into a second, unbounded series of its own.
        val series = dailySeries(exceptionDates = listOf(LocalDate(2026, 3, 3)))
        val occurrence = series
            .expandOccurrences(rangeStart = WindowStart, rangeEnd = WindowEnd)
            .first { it.date == LocalDate(2026, 3, 4) }
        assertEquals(series.recurrenceRule, occurrence.recurrenceRule)

        val change = series.editOccurrence(
            occurrenceDate = LocalDate(2026, 3, 4),
            edited = occurrence.copyAsBasic().copy(eventName = "Standup (extended)", endTime = LocalTime(10, 0)),
            scope = KalendarRecurrenceScope.THIS_OCCURRENCE,
        )
        val detached = change.newEvents.single()
        assertNull(detached.recurrenceRule)
        assertEquals(emptyList(), detached.exceptionDates)
        assertNull(detached.id)
        assertEquals("Standup (extended)", detached.eventName)
        assertEquals(LocalTime(10, 0), detached.endTime)
        assertEquals(listOf(LocalDate(2026, 3, 4)), detached.occurrenceDates())
    }

    @Test
    fun editOccurrence_ruleComesFromTheSeriesAndNotFromTheEditedEvent() {
        val series = dailySeries(count = 4)
        val editedWithForeignRule = BasicKalendarSyncEvent(
            date = LocalDate(2026, 3, 4),
            eventName = "Standup",
            startTime = LocalTime(9, 0),
            endTime = LocalTime(9, 15),
            recurrenceRule = KalendarRule(frequency = KalendarRecurrenceFrequency.YEARLY),
        )
        val updated = series.editOccurrence(
            occurrenceDate = LocalDate(2026, 3, 4),
            edited = editedWithForeignRule,
            scope = KalendarRecurrenceScope.ENTIRE_SERIES,
        ).updatedSeries!!
        assertEquals(series.recurrenceRule, updated.recurrenceRule)
        assertEquals(series.occurrenceDates(), updated.occurrenceDates())
    }

    @Test
    fun editOccurrence_thisOccurrence_detachedEventKeepsRemindersAndSpan() {
        val series = dailySeries()
        val edited = BasicKalendarSyncEvent(
            date = LocalDate(2026, 3, 4),
            eventName = "Overnight shift",
            startTime = LocalTime(22, 0),
            endTime = LocalTime(6, 0),
            endDate = LocalDate(2026, 3, 5),
            reminders = listOf(KalendarReminder(minutesBefore = 30)),
            attendees = listOf(KalendarAttendee(email = "sam@example.com", name = "Sam")),
        )
        val detached = series.editOccurrence(
            occurrenceDate = LocalDate(2026, 3, 4),
            edited = edited,
            scope = KalendarRecurrenceScope.THIS_OCCURRENCE,
        ).newEvents.single()
        assertEquals(LocalDate(2026, 3, 5), detached.endDate)
        assertEquals(listOf(KalendarReminder(minutesBefore = 30)), detached.reminders)
        assertEquals(listOf(KalendarAttendee(email = "sam@example.com", name = "Sam")), detached.attendees)
    }

    @Test
    fun editOccurrence_thisOccurrence_ofTheSeriesStart_stillDetaches() {
        val series = dailySeries(count = 3)
        val change = series.editOccurrence(
            occurrenceDate = LocalDate(2026, 3, 2),
            edited = editedOccurrence(LocalDate(2026, 3, 2)),
            scope = KalendarRecurrenceScope.THIS_OCCURRENCE,
        )
        assertEquals(
            listOf(LocalDate(2026, 3, 3), LocalDate(2026, 3, 4)),
            change.updatedSeries!!.occurrenceDates(),
        )
        assertEquals(listOf(LocalDate(2026, 3, 2)), change.newEvents.single().occurrenceDates())
    }

    @Test
    fun editOccurrence_thisOccurrence_movedToAnotherDate_leavesTheOriginalDateEmpty() {
        val series = dailySeries(count = 4)
        val change = series.editOccurrence(
            occurrenceDate = LocalDate(2026, 3, 4),
            edited = editedOccurrence(LocalDate(2026, 3, 21)),
            scope = KalendarRecurrenceScope.THIS_OCCURRENCE,
        )
        assertTrue(LocalDate(2026, 3, 4) !in change.updatedSeries!!.occurrenceDates())
        assertEquals(listOf(LocalDate(2026, 3, 21)), change.newEvents.single().occurrenceDates())
    }

    @Test
    fun editOccurrence_thisAndFollowing_multiDaySeries_keepsTheSpanOnBothHalves() {
        val series = BasicKalendarSyncEvent(
            id = "series-5",
            date = LocalDate(2026, 3, 2),
            eventName = "Workshop",
            endDate = LocalDate(2026, 3, 4),
            recurrenceRule = KalendarRule(frequency = KalendarRecurrenceFrequency.WEEKLY),
        )
        val occurrence = series
            .expandOccurrences(rangeStart = WindowStart, rangeEnd = WindowEnd)
            .first { it.date == LocalDate(2026, 3, 23) }
        val change = series.editOccurrence(
            occurrenceDate = LocalDate(2026, 3, 23),
            edited = occurrence,
            scope = KalendarRecurrenceScope.THIS_AND_FOLLOWING,
        )
        val head = change.updatedSeries!!
        val continuation = change.newEvents.single()
        assertEquals(LocalDate(2026, 3, 4), head.endDate)
        assertEquals(LocalDate(2026, 3, 23), continuation.date)
        assertEquals(LocalDate(2026, 3, 25), continuation.endDate)
        val spansBefore = series.expandOccurrences(rangeStart = WindowStart, rangeEnd = WindowEnd)
        val spansAfter = head.expandOccurrences(rangeStart = WindowStart, rangeEnd = WindowEnd) +
            continuation.expandOccurrences(rangeStart = WindowStart, rangeEnd = WindowEnd)
        assertEquals(spansBefore.map { it.endDate }, spansAfter.map { it.endDate })
    }

    @Test
    fun editOccurrence_thisAndFollowing_originalKeepsTheOccurrenceBeforeTheSplitAndLosesTheOneAtIt() {
        val series = dailySeries()
        val change = series.editOccurrence(
            occurrenceDate = LocalDate(2026, 3, 10),
            edited = editedOccurrence(LocalDate(2026, 3, 10)),
            scope = KalendarRecurrenceScope.THIS_AND_FOLLOWING,
        )
        val head = change.updatedSeries!!.occurrenceDates()
        assertEquals(LocalDate(2026, 3, 9), head.last())
        assertTrue(LocalDate(2026, 3, 10) !in head)
        assertEquals(LocalDate(2026, 3, 10), change.newEvents.single().occurrenceDates().first())
    }

    @Test
    fun editOccurrence_thisAndFollowing_untilBoundaryLandsOnTheDayBeforeTheOccurrence() {
        val series = dailySeries()
        val change = series.editOccurrence(
            occurrenceDate = LocalDate(2026, 3, 10),
            edited = editedOccurrence(LocalDate(2026, 3, 10)),
            scope = KalendarRecurrenceScope.THIS_AND_FOLLOWING,
        )
        assertEquals(LocalDate(2026, 3, 9), change.updatedSeries!!.recurrenceRule!!.until)
    }

    @Test
    fun editOccurrence_thisAndFollowing_coversEveryOriginalDateExactlyOnce() {
        val series = dailySeries()
        val before = series.occurrenceDates()
        val change = series.editOccurrence(
            occurrenceDate = LocalDate(2026, 3, 10),
            edited = editedOccurrence(LocalDate(2026, 3, 10), name = "Standup"),
            scope = KalendarRecurrenceScope.THIS_AND_FOLLOWING,
        )
        assertEquals(before, change.allOccurrenceDates())
    }

    @Test
    fun editOccurrence_thisAndFollowing_weeklyByDaySplitKeepsTheWeekdaysAndTheIntervalAlignment() {
        val series = BasicKalendarSyncEvent(
            id = "series-2",
            date = LocalDate(2026, 3, 2),
            eventName = "Sync",
            startTime = LocalTime(9, 0),
            endTime = LocalTime(9, 30),
            recurrenceRule = KalendarRule(
                frequency = KalendarRecurrenceFrequency.WEEKLY,
                interval = 2,
                byDay = listOf(KalendarWeekDay.MONDAY, KalendarWeekDay.WEDNESDAY),
            ),
        )
        val before = series.occurrenceDates()
        val splitDate = before.first { it > LocalDate(2026, 4, 1) }
        val change = series.editOccurrence(
            occurrenceDate = splitDate,
            edited = editedOccurrence(splitDate, name = "Sync", endTime = LocalTime(9, 30)),
            scope = KalendarRecurrenceScope.THIS_AND_FOLLOWING,
        )
        assertEquals(before, change.allOccurrenceDates())
    }

    @Test
    fun editOccurrence_thisAndFollowing_monthlySeriesThatSkipsShortMonthsSplitsWithoutDrift() {
        val series = BasicKalendarSyncEvent(
            id = "series-3",
            date = LocalDate(2026, 1, 31),
            eventName = "Payday",
            recurrenceRule = KalendarRule(frequency = KalendarRecurrenceFrequency.MONTHLY),
        )
        val before = series.occurrenceDates()
        val change = series.editOccurrence(
            occurrenceDate = LocalDate(2026, 7, 31),
            edited = BasicKalendarSyncEvent(date = LocalDate(2026, 7, 31), eventName = "Payday"),
            scope = KalendarRecurrenceScope.THIS_AND_FOLLOWING,
        )
        assertEquals(before, change.allOccurrenceDates())
        assertTrue(LocalDate(2026, 2, 28) !in change.allOccurrenceDates())
    }

    @Test
    fun editOccurrence_thisAndFollowing_countIsSplitBetweenTheTwoSeries() {
        val series = dailySeries(count = 10)
        val change = series.editOccurrence(
            occurrenceDate = LocalDate(2026, 3, 6),
            edited = editedOccurrence(LocalDate(2026, 3, 6), name = "Standup"),
            scope = KalendarRecurrenceScope.THIS_AND_FOLLOWING,
        )
        assertEquals(4, change.updatedSeries!!.occurrenceDates().size)
        assertEquals(6, change.newEvents.single().occurrenceDates().size)
        assertEquals(series.occurrenceDates(), change.allOccurrenceDates())
    }

    @Test
    fun editOccurrence_thisAndFollowing_countSplitCountsExcludedOccurrencesToo() {
        val series = dailySeries(count = 10, exceptionDates = listOf(LocalDate(2026, 3, 3)))
        val change = series.editOccurrence(
            occurrenceDate = LocalDate(2026, 3, 6),
            edited = editedOccurrence(LocalDate(2026, 3, 6), name = "Standup"),
            scope = KalendarRecurrenceScope.THIS_AND_FOLLOWING,
        )
        assertEquals(6, change.newEvents.single().occurrenceDates().size)
        assertEquals(series.occurrenceDates(), change.allOccurrenceDates())
    }

    @Test
    fun editOccurrence_thisAndFollowing_exceptionDatesAreDividedAtTheBoundary() {
        val series = dailySeries(
            exceptionDates = listOf(LocalDate(2026, 3, 4), LocalDate(2026, 3, 12)),
        )
        val occurrence = series
            .expandOccurrences(rangeStart = WindowStart, rangeEnd = WindowEnd)
            .first { it.date == LocalDate(2026, 3, 10) }
        val change = series.editOccurrence(
            occurrenceDate = LocalDate(2026, 3, 10),
            edited = occurrence,
            scope = KalendarRecurrenceScope.THIS_AND_FOLLOWING,
        )
        assertEquals(listOf(LocalDate(2026, 3, 4)), change.updatedSeries!!.exceptionDates)
        assertEquals(listOf(LocalDate(2026, 3, 12)), change.newEvents.single().exceptionDates)
        assertEquals(series.occurrenceDates(), change.allOccurrenceDates())
    }

    @Test
    fun editOccurrence_thisAndFollowing_keepsAnEarlierSeriesEndInsteadOfExtendingIt() {
        val series = dailySeries(until = LocalDate(2026, 3, 5))
        val change = series.editOccurrence(
            occurrenceDate = LocalDate(2026, 3, 20),
            edited = editedOccurrence(LocalDate(2026, 3, 20)),
            scope = KalendarRecurrenceScope.THIS_AND_FOLLOWING,
        )
        assertEquals(LocalDate(2026, 3, 5), change.updatedSeries!!.recurrenceRule!!.until)
        assertEquals(emptyList(), change.newEvents)
        assertEquals(series.occurrenceDates(), change.allOccurrenceDates())
    }

    @Test
    fun editOccurrence_thisAndFollowing_atTheFirstOccurrence_movesTheWholeSeriesInPlace() {
        val series = dailySeries(count = 4)
        val change = series.editOccurrence(
            occurrenceDate = LocalDate(2026, 3, 2),
            edited = editedOccurrence(LocalDate(2026, 3, 2)),
            scope = KalendarRecurrenceScope.THIS_AND_FOLLOWING,
        )
        val updated = change.updatedSeries!!
        assertEquals(emptyList(), change.newEvents)
        assertEquals("series-1", updated.id)
        assertEquals(series.occurrenceDates(), updated.occurrenceDates())
        assertEquals("Standup (extended)", updated.eventName)
    }

    @Test
    fun editOccurrence_thisAndFollowing_movingTheOccurrenceReanchorsOnlyTheNewSeries() {
        val series = dailySeries()
        val change = series.editOccurrence(
            occurrenceDate = LocalDate(2026, 3, 10),
            edited = editedOccurrence(LocalDate(2026, 3, 11), name = "Standup"),
            scope = KalendarRecurrenceScope.THIS_AND_FOLLOWING,
        )
        assertEquals(LocalDate(2026, 3, 9), change.updatedSeries!!.occurrenceDates().last())
        assertEquals(LocalDate(2026, 3, 11), change.newEvents.single().date)
    }

    @Test
    fun editOccurrence_entireSeries_rewritesInPlaceKeepingRuleAndExceptions() {
        val series = dailySeries(count = 5, exceptionDates = listOf(LocalDate(2026, 3, 4)))
        val change = series.editOccurrence(
            occurrenceDate = LocalDate(2026, 3, 4),
            edited = editedOccurrence(LocalDate(2026, 3, 4)),
            scope = KalendarRecurrenceScope.ENTIRE_SERIES,
        )
        val updated = change.updatedSeries!!
        assertEquals(emptyList(), change.newEvents)
        assertEquals("series-1", updated.id)
        assertEquals("Standup (extended)", updated.eventName)
        assertEquals(LocalTime(10, 0), updated.endTime)
        assertEquals(series.recurrenceRule, updated.recurrenceRule)
        assertEquals(series.occurrenceDates(), updated.occurrenceDates())
    }

    @Test
    fun editOccurrence_entireSeries_movingAnOccurrenceShiftsTheWholeSeriesAndItsExceptions() {
        val series = dailySeries(count = 4, exceptionDates = listOf(LocalDate(2026, 3, 4)))
        val updated = series.editOccurrence(
            occurrenceDate = LocalDate(2026, 3, 4),
            edited = editedOccurrence(LocalDate(2026, 3, 6), name = "Standup"),
            scope = KalendarRecurrenceScope.ENTIRE_SERIES,
        ).updatedSeries!!
        assertEquals(LocalDate(2026, 3, 4), updated.date)
        assertEquals(listOf(LocalDate(2026, 3, 6)), updated.exceptionDates)
        assertEquals(
            listOf(LocalDate(2026, 3, 4), LocalDate(2026, 3, 5), LocalDate(2026, 3, 7)),
            updated.occurrenceDates(),
        )
    }

    @Test
    fun editOccurrence_nonRecurringEvent_isRewrittenInPlaceUnderEveryScope() {
        val oneOff = BasicKalendarSyncEvent(
            id = "one-off",
            date = LocalDate(2026, 3, 4),
            eventName = "Dentist",
            startTime = LocalTime(9, 0),
            endTime = LocalTime(9, 30),
        )
        for (scope in KalendarRecurrenceScope.entries) {
            val change = oneOff.editOccurrence(
                occurrenceDate = LocalDate(2026, 3, 4),
                edited = editedOccurrence(LocalDate(2026, 3, 4), name = "Dentist (moved)"),
                scope = scope,
            )
            val updated = change.updatedSeries!!
            assertEquals(emptyList(), change.newEvents, "scope $scope")
            assertEquals("one-off", updated.id, "scope $scope")
            assertEquals("Dentist (moved)", updated.eventName, "scope $scope")
            assertNull(updated.recurrenceRule, "scope $scope")
        }
    }
}
