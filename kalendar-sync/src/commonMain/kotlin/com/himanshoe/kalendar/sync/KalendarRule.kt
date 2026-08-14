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

/**
 * How frequently a recurring event repeats, corresponding to the FREQ part of an RFC 5545 RRULE.
 */
public enum class KalendarRecurrenceFrequency {
    /** Repeat every N days. */
    DAILY,

    /** Repeat every N weeks, optionally restricted to specific days via [KalendarRule.byDay]. */
    WEEKLY,

    /**
     * Repeat every N months, optionally restricted to specific days of the month via
     * [KalendarRule.byMonthDay].
     */
    MONTHLY,

    /**
     * Repeat every N years, optionally restricted to specific months via
     * [KalendarRule.byMonth].
     */
    YEARLY,
}

/**
 * A day-of-week value for use in the BYDAY clause of a [KalendarRule].
 *
 * Use these values to specify which days of the week a WEEKLY rule should repeat on:
 * ```kotlin
 * KalendarRule(
 *     frequency = KalendarRecurrenceFrequency.WEEKLY,
 *     byDay = listOf(KalendarWeekDay.MONDAY, KalendarWeekDay.WEDNESDAY, KalendarWeekDay.FRIDAY),
 * )
 * ```
 */
public enum class KalendarWeekDay {
    MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY, SATURDAY, SUNDAY
}

/**
 * An RFC 5545 RRULE (recurrence rule) describing when a recurring event repeats.
 * Pass an instance to [KalendarSyncEvent.recurrenceRule]; the [KalendarRecurrenceExpander]
 * expands it into concrete [kotlinx.datetime.LocalDate] occurrences.
 *
 * ```kotlin
 * // Every weekday
 * KalendarRule(
 *     frequency = KalendarRecurrenceFrequency.WEEKLY,
 *     byDay = listOf(
 *         KalendarWeekDay.MONDAY, KalendarWeekDay.TUESDAY, KalendarWeekDay.WEDNESDAY,
 *         KalendarWeekDay.THURSDAY, KalendarWeekDay.FRIDAY,
 *     ),
 * )
 *
 * // Monthly on the 1st and 15th, ten times
 * KalendarRule(
 *     frequency = KalendarRecurrenceFrequency.MONTHLY,
 *     count = 10,
 *     byMonthDay = listOf(1, 15),
 * )
 * ```
 *
 * @param frequency How often the event repeats. Required.
 * @param interval How many [frequency] units between each occurrence. Must be ≥ 1. Defaults to 1.
 * @param count Total number of occurrences including the first. Mutually exclusive with [until].
 * @param until The last date on which an occurrence is allowed. Mutually exclusive with [count].
 * @param byDay For WEEKLY rules, restricts occurrences to these days of the week.
 *   May also be set for MONTHLY/YEARLY rules to select specific weekdays within the period.
 * @param byMonthDay For MONTHLY rules, restricts occurrences to these day-of-month values (1–31).
 * @param byMonth For YEARLY rules, restricts occurrences to these month values (1–12).
 *
 * This is deliberately a plain `class` with a hand-written [copy], [equals], and [hashCode]
 * rather than a `data class`, so that adding a BY-part in a later release does not change the
 * compiled signature of `copy` and break already compiled consumers.
 */
public class KalendarRule(
    public val frequency: KalendarRecurrenceFrequency,
    public val interval: Int = 1,
    public val count: Int? = null,
    public val until: LocalDate? = null,
    public val byDay: List<KalendarWeekDay> = emptyList(),
    public val byMonthDay: List<Int> = emptyList(),
    public val byMonth: List<Int> = emptyList(),
) {
    init {
        require(interval >= 1) { "interval must be >= 1, was $interval" }
        require(count == null || count >= 1) { "count must be >= 1, was $count" }
        require(count == null || until == null) { "count and until are mutually exclusive" }
    }

    /**
     * Returns a copy of this rule with the given properties replaced and all others carried over
     * unchanged. The constructor's validation applies to the result, so a copy that sets both
     * [count] and [until] throws just as the constructor would.
     */
    public fun copy(
        frequency: KalendarRecurrenceFrequency = this.frequency,
        interval: Int = this.interval,
        count: Int? = this.count,
        until: LocalDate? = this.until,
        byDay: List<KalendarWeekDay> = this.byDay,
        byMonthDay: List<Int> = this.byMonthDay,
        byMonth: List<Int> = this.byMonth,
    ): KalendarRule = KalendarRule(
        frequency = frequency,
        interval = interval,
        count = count,
        until = until,
        byDay = byDay,
        byMonthDay = byMonthDay,
        byMonth = byMonth,
    )

    /** Two rules are equal when every property matches. */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is KalendarRule) return false
        if (frequency != other.frequency) return false
        if (interval != other.interval) return false
        if (count != other.count) return false
        if (until != other.until) return false
        if (byDay != other.byDay) return false
        if (byMonthDay != other.byMonthDay) return false
        return byMonth == other.byMonth
    }

    override fun hashCode(): Int {
        var result = frequency.hashCode()
        result = HASH_FACTOR * result + interval
        result = HASH_FACTOR * result + (count ?: 0)
        result = HASH_FACTOR * result + (until?.hashCode() ?: 0)
        result = HASH_FACTOR * result + byDay.hashCode()
        result = HASH_FACTOR * result + byMonthDay.hashCode()
        result = HASH_FACTOR * result + byMonth.hashCode()
        return result
    }

    override fun toString(): String =
        "KalendarRule(frequency=$frequency, interval=$interval, count=$count, until=$until, " +
            "byDay=$byDay, byMonthDay=$byMonthDay, byMonth=$byMonth)"
}

private const val HASH_FACTOR = 31
