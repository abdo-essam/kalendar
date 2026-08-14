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
import kotlin.test.assertTrue

/**
 * Covers how [expandOccurrences] steps whole calendar periods: skipping months and years that
 * lack the series start's day-of-month rather than sliding occurrences earlier, anchoring a
 * YEARLY rule's unspecified components to the series start, and jumping an uncounted series
 * straight to the query window.
 */
class KalendarRecurrencePeriodStepTest {

    @Test
    fun expandOccurrences_monthlyOnThirtyFirst_skipsShortMonthsInsteadOfDrifting() {
        val event = BasicKalendarSyncEvent(
            date = LocalDate(2026, 1, 31),
            eventName = "Month-end report",
            recurrenceRule = KalendarRule(frequency = KalendarRecurrenceFrequency.MONTHLY),
        )
        val occurrences = event.expandOccurrences(
            rangeStart = LocalDate(2026, 1, 1),
            rangeEnd = LocalDate(2026, 12, 31),
        )
        assertEquals(
            listOf(1, 3, 5, 7, 8, 10, 12).map { LocalDate(2026, it, 31) },
            occurrences.map { it.date },
        )
    }

    @Test
    fun expandOccurrences_monthlyOnThirtyFirst_doesNotDragLaterOccurrencesEarlier() {
        val event = BasicKalendarSyncEvent(
            date = LocalDate(2026, 1, 31),
            eventName = "Month-end report",
            recurrenceRule = KalendarRule(frequency = KalendarRecurrenceFrequency.MONTHLY),
        )
        val occurrences = event.expandOccurrences(
            rangeStart = LocalDate(2026, 3, 1),
            rangeEnd = LocalDate(2026, 3, 31),
        )
        assertEquals(listOf(LocalDate(2026, 3, 31)), occurrences.map { it.date })
    }

    @Test
    fun expandOccurrences_monthlyOnThirtyFirst_clampedMonthDoesNotConsumeCount() {
        val event = BasicKalendarSyncEvent(
            date = LocalDate(2026, 1, 31),
            eventName = "Month-end report",
            recurrenceRule = KalendarRule(frequency = KalendarRecurrenceFrequency.MONTHLY, count = 3),
        )
        val occurrences = event.expandOccurrences(
            rangeStart = LocalDate(2026, 1, 1),
            rangeEnd = LocalDate(2026, 12, 31),
        )
        assertEquals(
            listOf(LocalDate(2026, 1, 31), LocalDate(2026, 3, 31), LocalDate(2026, 5, 31)),
            occurrences.map { it.date },
        )
    }

    @Test
    fun expandOccurrences_yearlyOnLeapDay_firesOnlyInLeapYears() {
        val event = BasicKalendarSyncEvent(
            date = LocalDate(2024, 2, 29),
            eventName = "Leap day",
            recurrenceRule = KalendarRule(frequency = KalendarRecurrenceFrequency.YEARLY),
        )
        val occurrences = event.expandOccurrences(
            rangeStart = LocalDate(2024, 1, 1),
            rangeEnd = LocalDate(2032, 12, 31),
        )
        assertEquals(
            listOf(2024, 2028, 2032).map { LocalDate(it, 2, 29) },
            occurrences.map { it.date },
        )
    }

    @Test
    fun expandOccurrences_yearlyByMonthDayWithoutByMonth_staysInSeriesStartMonth() {
        val event = BasicKalendarSyncEvent(
            date = LocalDate(2026, 3, 15),
            eventName = "Annual review",
            recurrenceRule = KalendarRule(
                frequency = KalendarRecurrenceFrequency.YEARLY,
                byMonthDay = listOf(15),
            ),
        )
        val occurrences = event.expandOccurrences(
            rangeStart = LocalDate(2026, 1, 1),
            rangeEnd = LocalDate(2027, 12, 31),
        )
        assertEquals(
            listOf(LocalDate(2026, 3, 15), LocalDate(2027, 3, 15)),
            occurrences.map { it.date },
        )
    }

    @Test
    fun expandOccurrences_yearlyByDayWithoutByMonth_staysInSeriesStartMonth() {
        val event = BasicKalendarSyncEvent(
            date = LocalDate(2026, 3, 2),
            eventName = "March Mondays",
            recurrenceRule = KalendarRule(
                frequency = KalendarRecurrenceFrequency.YEARLY,
                byDay = listOf(KalendarWeekDay.MONDAY),
            ),
        )
        val occurrences = event.expandOccurrences(
            rangeStart = LocalDate(2026, 1, 1),
            rangeEnd = LocalDate(2026, 12, 31),
        )
        assertEquals(
            listOf(2, 9, 16, 23, 30).map { LocalDate(2026, 3, it) },
            occurrences.map { it.date },
        )
    }

    /**
     * A series with no COUNT skips straight to the query window instead of walking every elapsed
     * period. Re-running the identical series under a COUNT far larger than the series can reach
     * forces the full walk from the series start, so the two must agree occurrence for occurrence.
     */
    private fun assertSkipAheadMatchesFullWalk(
        seriesStart: LocalDate,
        rule: KalendarRule,
        rangeStart: LocalDate,
        rangeEnd: LocalDate,
        endDate: LocalDate? = null,
    ) {
        val template = BasicKalendarSyncEvent(
            date = seriesStart,
            eventName = "Long-running series",
            endDate = endDate,
        )
        val skippedAhead = BasicKalendarSyncEvent(
            date = template.date,
            eventName = template.eventName,
            recurrenceRule = rule,
            endDate = endDate,
        ).expandOccurrences(rangeStart = rangeStart, rangeEnd = rangeEnd)
        val fullWalk = BasicKalendarSyncEvent(
            date = template.date,
            eventName = template.eventName,
            recurrenceRule = KalendarRule(
                frequency = rule.frequency,
                interval = rule.interval,
                count = UNREACHABLE_COUNT,
                byDay = rule.byDay,
                byMonthDay = rule.byMonthDay,
                byMonth = rule.byMonth,
            ),
            endDate = endDate,
        ).expandOccurrences(rangeStart = rangeStart, rangeEnd = rangeEnd)

        assertTrue(skippedAhead.isNotEmpty(), "expected occurrences inside the window")
        assertEquals(fullWalk.map { it.date }, skippedAhead.map { it.date })
    }

    @Test
    fun expandOccurrences_uncountedDailyStartingLongBeforeWindow_matchesFullWalk() {
        assertSkipAheadMatchesFullWalk(
            seriesStart = LocalDate(1970, 1, 1),
            rule = KalendarRule(frequency = KalendarRecurrenceFrequency.DAILY, interval = 3),
            rangeStart = LocalDate(2026, 6, 1),
            rangeEnd = LocalDate(2026, 6, 10),
        )
    }

    @Test
    fun expandOccurrences_uncountedWeeklyByDayStartingLongBeforeWindow_matchesFullWalk() {
        assertSkipAheadMatchesFullWalk(
            seriesStart = LocalDate(2020, 1, 6),
            rule = KalendarRule(
                frequency = KalendarRecurrenceFrequency.WEEKLY,
                interval = 2,
                byDay = listOf(KalendarWeekDay.MONDAY),
            ),
            rangeStart = LocalDate(2026, 6, 1),
            rangeEnd = LocalDate(2026, 6, 30),
        )
    }

    @Test
    fun expandOccurrences_uncountedMonthlyStartingLongBeforeWindow_matchesFullWalk() {
        assertSkipAheadMatchesFullWalk(
            seriesStart = LocalDate(2001, 1, 31),
            rule = KalendarRule(frequency = KalendarRecurrenceFrequency.MONTHLY, interval = 2),
            rangeStart = LocalDate(2026, 1, 1),
            rangeEnd = LocalDate(2026, 12, 31),
        )
    }

    @Test
    fun expandOccurrences_uncountedYearlyStartingLongBeforeWindow_matchesFullWalk() {
        assertSkipAheadMatchesFullWalk(
            seriesStart = LocalDate(1996, 2, 29),
            rule = KalendarRule(frequency = KalendarRecurrenceFrequency.YEARLY),
            rangeStart = LocalDate(2020, 1, 1),
            rangeEnd = LocalDate(2032, 12, 31),
        )
    }

    @Test
    fun expandOccurrences_uncountedMultiDaySpanStartingLongBeforeWindow_matchesFullWalk() {
        assertSkipAheadMatchesFullWalk(
            seriesStart = LocalDate(2000, 1, 1),
            rule = KalendarRule(frequency = KalendarRecurrenceFrequency.MONTHLY),
            rangeStart = LocalDate(2026, 6, 3),
            rangeEnd = LocalDate(2026, 6, 20),
            endDate = LocalDate(2000, 1, 6),
        )
    }
}

private const val UNREACHABLE_COUNT = 1_000_000
