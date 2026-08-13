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
 * - [KalendarRule.count] is always consumed from the series start, not from [rangeStart]: a
 *   `DAILY COUNT=3` series starting January 1st has exhausted its three occurrences by January
 *   4th, so expanding any later window yields an empty list rather than phantom occurrences.
 * - Dates in [KalendarSyncEvent.exceptionDates] are skipped — they still count towards
 *   [KalendarRule.count], matching how DTSTART+RRULE+EXDATE interact in most calendar apps (EXDATE
 *   removes specific occurrences from an already-determined series rather than shortening it).
 * - A YEARLY rule with BYMONTH (and no BYDAY/BYMONTHDAY) fires in each listed month on the start
 *   date's day-of-month; a MONTHLY rule with BYDAY fires on every matching weekday of each
 *   aligned month, per RFC 5545.
 * - [KalendarRule.interval] composes with the BY-filters: a WEEKLY BYDAY rule with `interval = 2`
 *   emits only in weeks an even number of weeks from the start date's week (weeks delimited per
 *   RFC 5545's default Monday week start), a MONTHLY BYDAY or BYMONTHDAY rule with `interval = 2`
 *   emits only in months an even number of months from the start month, and a filtered YEARLY
 *   rule with `interval = 2` emits only in years an even number of years from the start year.
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
        if (current.matchesFilters(rule = rule, seriesStart = date) && current.alignsWithInterval(rule, date)) {
            if (current >= rangeStart && current !in exceptionDates) {
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
 *
 * A YEARLY rule with BYMONTH but neither BYDAY nor BYMONTHDAY additionally requires the
 * candidate's day-of-month to equal [seriesStart]'s — per RFC 5545, BYMONTH overrides only the
 * month while the day still comes from DTSTART.
 */
private fun LocalDate.matchesFilters(rule: KalendarRule, seriesStart: LocalDate): Boolean {
    val monthFilterOnlyYearly = rule.frequency == KalendarRecurrenceFrequency.YEARLY &&
        rule.byMonth.isNotEmpty() && rule.byDay.isEmpty() && rule.byMonthDay.isEmpty()
    return (rule.byMonth.isEmpty() || monthNumber in rule.byMonth) &&
        (rule.byMonthDay.isEmpty() || dayOfMonth in rule.byMonthDay) &&
        (rule.byDay.isEmpty() || dayOfWeek.toKalendarWeekDay() in rule.byDay) &&
        (!monthFilterOnlyYearly || dayOfMonth == seriesStart.dayOfMonth)
}

/**
 * Returns `true` when [rule] must enumerate candidate dates one day at a time so that every
 * qualifying day inside each period is tested: WEEKLY with BYDAY, MONTHLY with BYDAY or
 * BYMONTHDAY, and YEARLY with any BY-filter. Filter-less rules advance by whole interval units
 * instead — see [advance].
 */
private fun KalendarRule.enumeratesDaily(): Boolean = when (frequency) {
    KalendarRecurrenceFrequency.DAILY -> false
    KalendarRecurrenceFrequency.WEEKLY -> byDay.isNotEmpty()
    KalendarRecurrenceFrequency.MONTHLY -> byDay.isNotEmpty() || byMonthDay.isNotEmpty()
    KalendarRecurrenceFrequency.YEARLY -> byMonth.isNotEmpty() || byDay.isNotEmpty() || byMonthDay.isNotEmpty()
}

/**
 * Returns `true` if this date falls in a period that is a whole multiple of [KalendarRule.interval]
 * periods away from [seriesStart]'s period. Only day-enumerating rules (see [enumeratesDaily])
 * need this: they step one day at a time, so the interval must be enforced as a filter rather
 * than a step size. Weeks are delimited by RFC 5545's default Monday week start; months and
 * years align on calendar month/year distance from the series start.
 */
private fun LocalDate.alignsWithInterval(rule: KalendarRule, seriesStart: LocalDate): Boolean {
    if (rule.interval == 1 || !rule.enumeratesDaily()) {
        return true
    }
    return when (rule.frequency) {
        KalendarRecurrenceFrequency.DAILY ->
            true

        KalendarRecurrenceFrequency.WEEKLY ->
            weeksBetween(from = seriesStart, to = this) % rule.interval == 0

        KalendarRecurrenceFrequency.MONTHLY ->
            monthsBetween(from = seriesStart, to = this) % rule.interval == 0

        KalendarRecurrenceFrequency.YEARLY ->
            (year - seriesStart.year) % rule.interval == 0
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
 * Day-enumerating rules (see [enumeratesDaily]) advance one day at a time so that every
 * qualifying day within each period is tested — [alignsWithInterval] then rejects candidates in
 * skipped periods. All other combinations advance by one full interval unit.
 */
private fun LocalDate.advance(rule: KalendarRule): LocalDate =
    if (rule.enumeratesDaily()) {
        plus(1, DateTimeUnit.DAY)
    } else {
        when (rule.frequency) {
            KalendarRecurrenceFrequency.DAILY -> plus(rule.interval, DateTimeUnit.DAY)
            KalendarRecurrenceFrequency.WEEKLY -> plus(rule.interval * DAYS_PER_WEEK, DateTimeUnit.DAY)
            KalendarRecurrenceFrequency.MONTHLY -> plus(rule.interval, DateTimeUnit.MONTH)
            KalendarRecurrenceFrequency.YEARLY -> plus(rule.interval, DateTimeUnit.YEAR)
        }
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
 * [KalendarSyncEvent.id] cleared — representing a single materialised occurrence. The template's
 * [KalendarSyncEvent.recurrenceRule] and [KalendarSyncEvent.exceptionDates] are both carried
 * through, so persisting or re-expanding an occurrence keeps the full series semantics instead
 * of resurrecting excepted dates.
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
        exceptionDates = exceptionDates,
        reminders = reminders,
        attendees = attendees,
    )
}

private fun daysBetween(from: LocalDate, to: LocalDate): Int =
    (to.toEpochDays() - from.toEpochDays()).toInt()
