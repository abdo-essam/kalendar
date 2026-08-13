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

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus

private const val DAYS_PER_WEEK = 7
private const val MONTHS_PER_YEAR = 12

/**
 * Expands this recurring event into all concrete occurrences within the closed date range
 * [[rangeStart], [rangeEnd]], respecting the event's [KalendarSyncEvent.recurrenceRule].
 *
 * Supports all four [KalendarRecurrenceFrequency] values and the BYDAY, BYMONTHDAY, and BYMONTH
 * filters from RFC 5545. Both UNTIL and COUNT termination conditions are honoured.
 *
 * - Events without a [KalendarRule] are treated as single-occurrence: `this` is returned in a
 *   one-element list when its date is within the range, or an empty list otherwise.
 * - Each generated occurrence is a copy of the receiver with [KalendarSyncEvent.date] replaced by
 *   the occurrence date and [KalendarSyncEvent.id] cleared; [KalendarSyncEvent.endDate], if set,
 *   shifts by the same number of days so multi-day spans are preserved on every occurrence.
 * - Dates in [KalendarSyncEvent.exceptionDates] are skipped — they still count towards
 *   [KalendarRule.count], matching how DTSTART+RRULE+EXDATE interact in most calendar apps (EXDATE
 *   removes specific occurrences from an already-determined series rather than shortening it).
 * - [KalendarRule.interval] composes with the BY-filters: a WEEKLY BYDAY rule with `interval = 2`
 *   emits only in weeks an even number of weeks from the start date's week (weeks delimited per
 *   RFC 5545's default Monday week start), and a MONTHLY BYMONTHDAY rule with `interval = 2`
 *   emits only in months an even number of months from the start month.
 *
 * ```kotlin
 * val template = BasicKalendarSyncEvent(
 *     date = LocalDate(2026, 1, 6),
 *     eventName = "Weekly team sync",
 *     recurrenceRule = KalendarRule(
 *         frequency = KalendarRecurrenceFrequency.WEEKLY,
 *         byDay = listOf(KalendarWeekDay.MONDAY),
 *     ),
 * )
 * val occurrences = template.expandOccurrences(
 *     rangeStart = LocalDate(2026, 1, 1),
 *     rangeEnd   = LocalDate(2026, 3, 31),
 * )
 * ```
 *
 * @param rangeStart Start of the expansion window (inclusive).
 * @param rangeEnd End of the expansion window (inclusive).
 */
public fun KalendarSyncEvent.expandOccurrences(
    rangeStart: LocalDate,
    rangeEnd: LocalDate,
): List<KalendarSyncEvent> {
    val rule = recurrenceRule
        ?: return if (date in rangeStart..rangeEnd) listOf(this) else emptyList()

    val occurrences = mutableListOf<KalendarSyncEvent>()
    var occurrenceCount = 0
    var current = date

    while (withinBounds(rule, occurrenceCount, current, rangeEnd)) {
        if (current >= rangeStart && current.matchesFilters(rule) && current.alignsWithInterval(rule, date)) {
            if (current !in exceptionDates) {
                occurrences += copyWithDate(current)
            }
            occurrenceCount++
        }
        current = current.advance(rule)
    }
    return occurrences
}

private fun withinBounds(rule: KalendarRule, count: Int, current: LocalDate, rangeEnd: LocalDate): Boolean =
    (rule.count == null || count < rule.count) &&
        (rule.until == null || current <= rule.until) &&
        current <= rangeEnd

/**
 * Returns `true` if this date satisfies all BYMONTH, BYMONTHDAY, and BYDAY constraints in [rule].
 */
private fun LocalDate.matchesFilters(rule: KalendarRule): Boolean {
    if (rule.byMonth.isNotEmpty() && monthNumber !in rule.byMonth) return false
    if (rule.byMonthDay.isNotEmpty() && dayOfMonth !in rule.byMonthDay) return false
    if (rule.byDay.isNotEmpty() && dayOfWeek.toKalendarWeekDay() !in rule.byDay) return false
    return true
}

/**
 * Returns `true` if this date falls in a period that is a whole multiple of [KalendarRule.interval]
 * periods away from [seriesStart]'s period. Only WEEKLY-with-BYDAY and MONTHLY-with-BYMONTHDAY
 * rules need this: they enumerate candidates one day at a time (see [advance]), so the interval
 * must be enforced as a filter rather than a step size. Weeks are delimited by RFC 5545's default
 * Monday week start; DAILY and YEARLY (and filter-less WEEKLY/MONTHLY) already advance by whole
 * interval units.
 */
private fun LocalDate.alignsWithInterval(rule: KalendarRule, seriesStart: LocalDate): Boolean {
    if (rule.interval == 1) return true
    return when (rule.frequency) {
        KalendarRecurrenceFrequency.WEEKLY ->
            rule.byDay.isEmpty() || weeksBetween(seriesStart, this) % rule.interval == 0

        KalendarRecurrenceFrequency.MONTHLY ->
            rule.byMonthDay.isEmpty() || monthsBetween(seriesStart, this) % rule.interval == 0

        else -> true
    }
}

private fun weeksBetween(from: LocalDate, to: LocalDate): Int {
    val fromWeekStart = from.minus(from.dayOfWeek.ordinal, DateTimeUnit.DAY)
    val toWeekStart = to.minus(to.dayOfWeek.ordinal, DateTimeUnit.DAY)
    return ((toWeekStart.toEpochDays() - fromWeekStart.toEpochDays()) / DAYS_PER_WEEK).toInt()
}

private fun monthsBetween(from: LocalDate, to: LocalDate): Int =
    (to.year - from.year) * MONTHS_PER_YEAR + (to.monthNumber - from.monthNumber)

/**
 * Returns the next candidate date after this one, based on [rule]'s frequency and BY-filter
 * strategy.
 *
 * WEEKLY rules with a BYDAY filter and MONTHLY rules with a BYMONTHDAY filter advance one day at
 * a time so that every qualifying day within each period is tested — [alignsWithInterval] then
 * rejects candidates in skipped periods. All other combinations advance by one full interval unit.
 */
private fun LocalDate.advance(rule: KalendarRule): LocalDate =
    when (rule.frequency) {
        KalendarRecurrenceFrequency.DAILY ->
            plus(rule.interval, DateTimeUnit.DAY)

        KalendarRecurrenceFrequency.WEEKLY ->
            if (rule.byDay.isEmpty()) {
                plus(rule.interval * 7, DateTimeUnit.DAY)
            } else {
                plus(1, DateTimeUnit.DAY)
            }

        KalendarRecurrenceFrequency.MONTHLY ->
            if (rule.byMonthDay.isEmpty()) {
                plus(rule.interval, DateTimeUnit.MONTH)
            } else {
                plus(1, DateTimeUnit.DAY)
            }

        KalendarRecurrenceFrequency.YEARLY ->
            plus(rule.interval, DateTimeUnit.YEAR)
    }

/**
 * Maps a [DayOfWeek] to the corresponding [KalendarWeekDay] entry.
 */
private fun DayOfWeek.toKalendarWeekDay(): KalendarWeekDay = when (this) {
    DayOfWeek.MONDAY -> KalendarWeekDay.MONDAY
    DayOfWeek.TUESDAY -> KalendarWeekDay.TUESDAY
    DayOfWeek.WEDNESDAY -> KalendarWeekDay.WEDNESDAY
    DayOfWeek.THURSDAY -> KalendarWeekDay.THURSDAY
    DayOfWeek.FRIDAY -> KalendarWeekDay.FRIDAY
    DayOfWeek.SATURDAY -> KalendarWeekDay.SATURDAY
    else -> KalendarWeekDay.SUNDAY
}

/**
 * Returns a copy of this event with [KalendarSyncEvent.date] replaced by [date] and
 * [KalendarSyncEvent.id] cleared — representing a single materialised occurrence.
 */
private fun KalendarSyncEvent.copyWithDate(date: LocalDate): KalendarSyncEvent {
    val span = endDate?.let { date.plus(daysBetween(this.date, it), DateTimeUnit.DAY) }
    return BasicKalendarSyncEvent(
        id = null,
        date = date,
        eventName = eventName,
        eventDescription = eventDescription,
        startTime = startTime,
        endTime = endTime,
        recurrenceRule = recurrenceRule,
        endDate = span,
        reminders = reminders,
        attendees = attendees,
    )
}

private fun daysBetween(from: LocalDate, to: LocalDate): Int =
    (to.toEpochDays() - from.toEpochDays()).toInt()
