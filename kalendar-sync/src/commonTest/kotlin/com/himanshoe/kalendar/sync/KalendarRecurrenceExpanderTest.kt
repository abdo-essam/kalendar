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

class KalendarRecurrenceExpanderTest {

    @Test
    fun expandOccurrences_noRule_singleOccurrenceInRange() {
        val event = BasicKalendarSyncEvent(date = LocalDate(2026, 6, 15), eventName = "One-off")
        val occurrences = event.expandOccurrences(
            rangeStart = LocalDate(2026, 6, 1),
            rangeEnd = LocalDate(2026, 6, 30),
        )
        assertEquals(listOf(LocalDate(2026, 6, 15)), occurrences.map { it.date })
    }

    @Test
    fun expandOccurrences_noRule_outsideRange_returnsEmpty() {
        val event = BasicKalendarSyncEvent(date = LocalDate(2026, 6, 15), eventName = "One-off")
        val occurrences = event.expandOccurrences(
            rangeStart = LocalDate(2026, 7, 1),
            rangeEnd = LocalDate(2026, 7, 31),
        )
        assertTrue(occurrences.isEmpty())
    }

    @Test
    fun expandOccurrences_daily_everyDayInRange() {
        val event = BasicKalendarSyncEvent(
            date = LocalDate(2026, 1, 1),
            eventName = "Daily",
            recurrenceRule = KalendarRule(frequency = KalendarRecurrenceFrequency.DAILY),
        )
        val occurrences = event.expandOccurrences(
            rangeStart = LocalDate(2026, 1, 1),
            rangeEnd = LocalDate(2026, 1, 5),
        )
        assertEquals(
            listOf(1, 2, 3, 4, 5).map { LocalDate(2026, 1, it) },
            occurrences.map { it.date },
        )
    }

    @Test
    fun expandOccurrences_dailyWithInterval_skipsDays() {
        val event = BasicKalendarSyncEvent(
            date = LocalDate(2026, 1, 1),
            eventName = "Every other day",
            recurrenceRule = KalendarRule(frequency = KalendarRecurrenceFrequency.DAILY, interval = 2),
        )
        val occurrences = event.expandOccurrences(
            rangeStart = LocalDate(2026, 1, 1),
            rangeEnd = LocalDate(2026, 1, 7),
        )
        assertEquals(
            listOf(1, 3, 5, 7).map { LocalDate(2026, 1, it) },
            occurrences.map { it.date },
        )
    }

    @Test
    fun expandOccurrences_weeklyWithByDay_onlyMatchingWeekdays() {
        // 2026-01-05 is a Monday.
        val event = BasicKalendarSyncEvent(
            date = LocalDate(2026, 1, 5),
            eventName = "MWF",
            recurrenceRule = KalendarRule(
                frequency = KalendarRecurrenceFrequency.WEEKLY,
                byDay = listOf(KalendarWeekDay.MONDAY, KalendarWeekDay.WEDNESDAY, KalendarWeekDay.FRIDAY),
            ),
        )
        val occurrences = event.expandOccurrences(
            rangeStart = LocalDate(2026, 1, 5),
            rangeEnd = LocalDate(2026, 1, 11),
        )
        assertEquals(
            listOf(LocalDate(2026, 1, 5), LocalDate(2026, 1, 7), LocalDate(2026, 1, 9)),
            occurrences.map { it.date },
        )
    }

    @Test
    fun expandOccurrences_monthlyWithByMonthDay_specificDaysOfMonth() {
        val event = BasicKalendarSyncEvent(
            date = LocalDate(2026, 1, 1),
            eventName = "Payday",
            recurrenceRule = KalendarRule(
                frequency = KalendarRecurrenceFrequency.MONTHLY,
                byMonthDay = listOf(1, 15),
            ),
        )
        val occurrences = event.expandOccurrences(
            rangeStart = LocalDate(2026, 1, 1),
            rangeEnd = LocalDate(2026, 3, 31),
        )
        assertEquals(
            listOf(
                LocalDate(2026, 1, 1),
                LocalDate(2026, 1, 15),
                LocalDate(2026, 2, 1),
                LocalDate(2026, 2, 15),
                LocalDate(2026, 3, 1),
                LocalDate(2026, 3, 15),
            ),
            occurrences.map { it.date },
        )
    }

    @Test
    fun expandOccurrences_yearlyWithByMonth_specificMonthOnly() {
        val event = BasicKalendarSyncEvent(
            date = LocalDate(2026, 3, 1),
            eventName = "Anniversary",
            recurrenceRule = KalendarRule(
                frequency = KalendarRecurrenceFrequency.YEARLY,
                byMonth = listOf(3),
            ),
        )
        val occurrences = event.expandOccurrences(
            rangeStart = LocalDate(2026, 1, 1),
            rangeEnd = LocalDate(2028, 12, 31),
        )
        assertEquals(
            listOf(LocalDate(2026, 3, 1), LocalDate(2027, 3, 1), LocalDate(2028, 3, 1)),
            occurrences.map { it.date },
        )
    }

    @Test
    fun expandOccurrences_count_stopsAfterNOccurrences() {
        val event = BasicKalendarSyncEvent(
            date = LocalDate(2026, 1, 1),
            eventName = "Limited",
            recurrenceRule = KalendarRule(frequency = KalendarRecurrenceFrequency.DAILY, count = 3),
        )
        val occurrences = event.expandOccurrences(
            rangeStart = LocalDate(2026, 1, 1),
            rangeEnd = LocalDate(2026, 12, 31),
        )
        assertEquals(3, occurrences.size)
    }

    @Test
    fun expandOccurrences_monthlyByMonthDayWithInterval_skipsUnalignedMonths() {
        val event = BasicKalendarSyncEvent(
            date = LocalDate(2026, 1, 1),
            eventName = "Bi-monthly payday",
            recurrenceRule = KalendarRule(
                frequency = KalendarRecurrenceFrequency.MONTHLY,
                interval = 2,
                byMonthDay = listOf(1, 15),
            ),
        )
        val occurrences = event.expandOccurrences(
            rangeStart = LocalDate(2026, 1, 1),
            rangeEnd = LocalDate(2026, 5, 31),
        )
        assertEquals(
            listOf(
                LocalDate(2026, 1, 1),
                LocalDate(2026, 1, 15),
                LocalDate(2026, 3, 1),
                LocalDate(2026, 3, 15),
                LocalDate(2026, 5, 1),
                LocalDate(2026, 5, 15),
            ),
            occurrences.map { it.date },
        )
    }

    @Test
    fun expandOccurrences_weeklyByDayWithInterval_skipsUnalignedWeeks() {
        // 2026-01-05 is a Monday; interval=2 -> Mon/Wed of every other week.
        val event = BasicKalendarSyncEvent(
            date = LocalDate(2026, 1, 5),
            eventName = "Fortnightly",
            recurrenceRule = KalendarRule(
                frequency = KalendarRecurrenceFrequency.WEEKLY,
                interval = 2,
                byDay = listOf(KalendarWeekDay.MONDAY, KalendarWeekDay.WEDNESDAY),
            ),
        )
        val occurrences = event.expandOccurrences(
            rangeStart = LocalDate(2026, 1, 5),
            rangeEnd = LocalDate(2026, 2, 1),
        )
        assertEquals(
            listOf(
                LocalDate(2026, 1, 5),
                LocalDate(2026, 1, 7),
                LocalDate(2026, 1, 19),
                LocalDate(2026, 1, 21),
            ),
            occurrences.map { it.date },
        )
    }

    @Test
    fun expandOccurrences_exceptionDate_isSkipped() {
        val event = BasicKalendarSyncEvent(
            date = LocalDate(2026, 1, 1),
            eventName = "Daily",
            recurrenceRule = KalendarRule(frequency = KalendarRecurrenceFrequency.DAILY),
            exceptionDates = listOf(LocalDate(2026, 1, 3)),
        )
        val occurrences = event.expandOccurrences(
            rangeStart = LocalDate(2026, 1, 1),
            rangeEnd = LocalDate(2026, 1, 5),
        )
        assertEquals(
            listOf(
                LocalDate(2026, 1, 1),
                LocalDate(2026, 1, 2),
                LocalDate(2026, 1, 4),
                LocalDate(2026, 1, 5),
            ),
            occurrences.map { it.date },
        )
    }

    @Test
    fun expandOccurrences_exceptionDate_stillCountsTowardCount() {
        val event = BasicKalendarSyncEvent(
            date = LocalDate(2026, 1, 1),
            eventName = "Limited",
            recurrenceRule = KalendarRule(frequency = KalendarRecurrenceFrequency.DAILY, count = 3),
            exceptionDates = listOf(LocalDate(2026, 1, 2)),
        )
        val occurrences = event.expandOccurrences(
            rangeStart = LocalDate(2026, 1, 1),
            rangeEnd = LocalDate(2026, 12, 31),
        )
        // COUNT=3 generates Jan 1/2/3; Jan 2 is excepted, so only 2 occurrences are returned —
        // COUNT does not "reflow" to compensate for the skipped date.
        assertEquals(
            listOf(LocalDate(2026, 1, 1), LocalDate(2026, 1, 3)),
            occurrences.map { it.date },
        )
    }

    @Test
    fun expandOccurrences_until_stopsAtBoundaryDate() {
        val event = BasicKalendarSyncEvent(
            date = LocalDate(2026, 1, 1),
            eventName = "Bounded",
            recurrenceRule = KalendarRule(
                frequency = KalendarRecurrenceFrequency.DAILY,
                until = LocalDate(2026, 1, 3),
            ),
        )
        val occurrences = event.expandOccurrences(
            rangeStart = LocalDate(2026, 1, 1),
            rangeEnd = LocalDate(2026, 12, 31),
        )
        assertEquals(
            listOf(LocalDate(2026, 1, 1), LocalDate(2026, 1, 2), LocalDate(2026, 1, 3)),
            occurrences.map { it.date },
        )
    }

    @Test
    fun expandOccurrences_clearsIdOnEveryOccurrence() {
        val event = BasicKalendarSyncEvent(
            id = "device-id-123",
            date = LocalDate(2026, 1, 1),
            eventName = "Daily",
            recurrenceRule = KalendarRule(frequency = KalendarRecurrenceFrequency.DAILY, count = 2),
        )
        val occurrences = event.expandOccurrences(
            rangeStart = LocalDate(2026, 1, 1),
            rangeEnd = LocalDate(2026, 1, 31),
        )
        assertTrue(occurrences.all { it.id == null })
    }

    @Test
    fun expandOccurrences_singleDayEvent_occurrencesHaveNullEndDate() {
        val event = BasicKalendarSyncEvent(
            date = LocalDate(2026, 1, 1),
            eventName = "Daily",
            recurrenceRule = KalendarRule(frequency = KalendarRecurrenceFrequency.DAILY, count = 3),
        )
        val occurrences = event.expandOccurrences(
            rangeStart = LocalDate(2026, 1, 1),
            rangeEnd = LocalDate(2026, 1, 31),
        )
        assertTrue(occurrences.all { it.endDate == null })
    }

    @Test
    fun expandOccurrences_multiDayEvent_preservesSpanOnEveryOccurrence() {
        // A 3-day retreat that repeats yearly — each occurrence should keep its 3-day span.
        val event = BasicKalendarSyncEvent(
            date = LocalDate(2026, 3, 1),
            endDate = LocalDate(2026, 3, 3),
            eventName = "Retreat",
            recurrenceRule = KalendarRule(frequency = KalendarRecurrenceFrequency.YEARLY),
        )
        val occurrences = event.expandOccurrences(
            rangeStart = LocalDate(2026, 1, 1),
            rangeEnd = LocalDate(2027, 12, 31),
        )
        assertEquals(2, occurrences.size)
        assertEquals(LocalDate(2026, 3, 1), occurrences[0].date)
        assertEquals(LocalDate(2026, 3, 3), occurrences[0].endDate)
        assertEquals(LocalDate(2027, 3, 1), occurrences[1].date)
        assertEquals(LocalDate(2027, 3, 3), occurrences[1].endDate)
    }

    @Test
    fun expandOccurrences_rangeStartMidway_onlyIncludesOccurrencesFromRangeStart() {
        val event = BasicKalendarSyncEvent(
            date = LocalDate(2026, 1, 1),
            eventName = "Daily",
            recurrenceRule = KalendarRule(frequency = KalendarRecurrenceFrequency.DAILY),
        )
        val occurrences = event.expandOccurrences(
            rangeStart = LocalDate(2026, 1, 3),
            rangeEnd = LocalDate(2026, 1, 5),
        )
        assertEquals(
            listOf(LocalDate(2026, 1, 3), LocalDate(2026, 1, 4), LocalDate(2026, 1, 5)),
            occurrences.map { it.date },
        )
    }

    @Test
    fun kalendarRule_countAndUntilBothSet_throws() {
        assertTrue(
            runCatching {
                KalendarRule(
                    frequency = KalendarRecurrenceFrequency.DAILY,
                    count = 5,
                    until = LocalDate(2026, 1, 1),
                )
            }.isFailure,
        )
    }

    @Test
    fun kalendarRule_intervalZero_throws() {
        assertTrue(
            runCatching { KalendarRule(frequency = KalendarRecurrenceFrequency.DAILY, interval = 0) }.isFailure,
        )
    }

    @Test
    fun kalendarRule_defaults_areValid() {
        val rule = KalendarRule(frequency = KalendarRecurrenceFrequency.WEEKLY)
        assertEquals(1, rule.interval)
        assertNull(rule.count)
        assertNull(rule.until)
        assertTrue(rule.byDay.isEmpty())
    }
}
