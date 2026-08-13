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

class KalendarRRuleTest {

    @Test
    fun toRRuleString_weeklyWithIntervalAndByDay() {
        val rule = KalendarRule(
            frequency = KalendarRecurrenceFrequency.WEEKLY,
            interval = 2,
            byDay = listOf(KalendarWeekDay.MONDAY, KalendarWeekDay.WEDNESDAY),
        )
        assertEquals("FREQ=WEEKLY;INTERVAL=2;BYDAY=MO,WE", rule.toRRuleString())
    }

    @Test
    fun toRRuleString_monthlyWithCountAndByMonthDay() {
        val rule = KalendarRule(
            frequency = KalendarRecurrenceFrequency.MONTHLY,
            count = 10,
            byMonthDay = listOf(1, 15),
        )
        assertEquals("FREQ=MONTHLY;COUNT=10;BYMONTHDAY=1,15", rule.toRRuleString())
    }

    @Test
    fun toRRuleString_yearlyWithUntilAndByMonth() {
        val rule = KalendarRule(
            frequency = KalendarRecurrenceFrequency.YEARLY,
            until = LocalDate(2026, 12, 31),
            byMonth = listOf(6),
        )
        assertEquals("FREQ=YEARLY;UNTIL=20261231;BYMONTH=6", rule.toRRuleString())
    }

    @Test
    fun parseRRuleString_roundTripsWeeklyRule() {
        val rule = KalendarRule(
            frequency = KalendarRecurrenceFrequency.WEEKLY,
            interval = 2,
            byDay = listOf(KalendarWeekDay.MONDAY, KalendarWeekDay.WEDNESDAY),
        )
        assertEquals(rule, parseRRuleString(rule.toRRuleString()))
    }

    @Test
    fun parseRRuleString_roundTripsMonthlyRule() {
        val rule = KalendarRule(
            frequency = KalendarRecurrenceFrequency.MONTHLY,
            count = 10,
            byMonthDay = listOf(1, 15),
        )
        assertEquals(rule, parseRRuleString(rule.toRRuleString()))
    }

    @Test
    fun parseRRuleString_roundTripsYearlyRule() {
        val rule = KalendarRule(
            frequency = KalendarRecurrenceFrequency.YEARLY,
            until = LocalDate(2026, 12, 31),
            byMonth = listOf(6),
        )
        assertEquals(rule, parseRRuleString(rule.toRRuleString()))
    }

    @Test
    fun parseRRuleString_missingFreq_returnsNull() {
        assertNull(parseRRuleString("INTERVAL=2;BYDAY=MO"))
    }

    @Test
    fun parseRRuleString_unknownFreq_returnsNull() {
        assertNull(parseRRuleString("FREQ=HOURLY"))
    }

    @Test
    fun recurrenceDurationString_allDaySingleDay_isOneDay() {
        val event = BasicKalendarSyncEvent(date = LocalDate(2026, 1, 5), eventName = "Holiday")
        assertEquals("P1D", recurrenceDurationString(event))
    }

    @Test
    fun recurrenceDurationString_allDayMultiDay_spansInclusiveEndDate() {
        val event = BasicKalendarSyncEvent(
            date = LocalDate(2026, 1, 5),
            eventName = "Conference",
            endDate = LocalDate(2026, 1, 7),
        )
        assertEquals("P3D", recurrenceDurationString(event))
    }

    @Test
    fun recurrenceDurationString_timedNinetyMinutes_isSecondsValue() {
        val event = BasicKalendarSyncEvent(
            date = LocalDate(2026, 1, 5),
            eventName = "Sync",
            startTime = LocalTime(9, 0),
            endTime = LocalTime(10, 30),
        )
        assertEquals("PT5400S", recurrenceDurationString(event))
    }

    @Test
    fun recurrenceDurationString_timedWithoutEndTime_defaultsToOneHour() {
        val event = BasicKalendarSyncEvent(
            date = LocalDate(2026, 1, 5),
            eventName = "Sync",
            startTime = LocalTime(9, 0),
        )
        assertEquals("PT3600S", recurrenceDurationString(event))
    }

    @Test
    fun recurrenceExdateString_allDay_joinsPlainDates() {
        val event = BasicKalendarSyncEvent(
            date = LocalDate(2026, 1, 5),
            eventName = "Standup",
            recurrenceRule = KalendarRule(frequency = KalendarRecurrenceFrequency.DAILY),
            exceptionDates = listOf(LocalDate(2026, 1, 7), LocalDate(2026, 1, 9)),
        )
        assertEquals("20260107,20260109", recurrenceExdateString(event))
    }

    @Test
    fun recurrenceExdateString_timed_appendsStartTimeSuffix() {
        val event = BasicKalendarSyncEvent(
            date = LocalDate(2026, 1, 5),
            eventName = "Standup",
            startTime = LocalTime(9, 0),
            endTime = LocalTime(9, 30),
            recurrenceRule = KalendarRule(frequency = KalendarRecurrenceFrequency.DAILY),
            exceptionDates = listOf(LocalDate(2026, 1, 7), LocalDate(2026, 1, 9)),
        )
        assertEquals("20260107T090000Z,20260109T090000Z", recurrenceExdateString(event))
    }

    @Test
    fun recurrenceExdateString_noExceptions_returnsNull() {
        val event = BasicKalendarSyncEvent(
            date = LocalDate(2026, 1, 5),
            eventName = "Standup",
            recurrenceRule = KalendarRule(frequency = KalendarRecurrenceFrequency.DAILY),
        )
        assertNull(recurrenceExdateString(event))
    }

    @Test
    fun parseRecurrenceDurationSeconds_parsesSecondsMinutesHoursAndDays() {
        assertEquals(3600L, parseRecurrenceDurationSeconds("PT3600S"))
        assertEquals(5400L, parseRecurrenceDurationSeconds("PT90M"))
        assertEquals(7200L, parseRecurrenceDurationSeconds("PT2H"))
        assertEquals(172_800L, parseRecurrenceDurationSeconds("P2D"))
        assertEquals(129_600L, parseRecurrenceDurationSeconds("P1DT12H"))
    }

    @Test
    fun parseRecurrenceDurationSeconds_invalidValues_returnNull() {
        assertNull(parseRecurrenceDurationSeconds("P"))
        assertNull(parseRecurrenceDurationSeconds("PT"))
        assertNull(parseRecurrenceDurationSeconds("nonsense"))
        assertNull(parseRecurrenceDurationSeconds(""))
    }
}
