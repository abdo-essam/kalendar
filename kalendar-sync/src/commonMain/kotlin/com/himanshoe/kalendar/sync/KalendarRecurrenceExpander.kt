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
 * - An occurrence is included when it **overlaps** the range, not merely when it starts inside it:
 *   a multi-day occurrence beginning before [rangeStart] but reaching into the window is returned,
 *   still carrying its original [KalendarSyncEvent.date]. Callers can therefore page window by
 *   window without spans dropping out at the seams.
 * - Events without a [KalendarRule] are treated as single-occurrence: `this` is returned in a
 *   one-element list when it overlaps the range, or an empty list otherwise.
 * - Each generated occurrence is a copy of the receiver with [KalendarSyncEvent.date] replaced by
 *   the occurrence date and [KalendarSyncEvent.id] cleared; [KalendarSyncEvent.endDate], if set,
 *   shifts by the same number of days so multi-day spans are preserved on every occurrence.
 * - [KalendarRule.count] is always consumed from the series start, not from [rangeStart]: a
 *   `DAILY COUNT=3` series starting January 1st has exhausted its three occurrences by January
 *   4th, so expanding any later window yields an empty list rather than phantom occurrences.
 * - Dates in [KalendarSyncEvent.exceptionDates] are skipped — they still count towards
 *   [KalendarRule.count], matching how DTSTART+RRULE+EXDATE interact in most calendar apps (EXDATE
 *   removes specific occurrences from an already-determined series rather than shortening it).
 * - A YEARLY rule anchors to the series start whatever its BY-parts leave unspecified, per RFC
 *   5545: with no BYMONTH the month comes from the start date, and with neither BYMONTHDAY nor
 *   BYDAY the day-of-month does too. So `YEARLY BYMONTH=[3, 9]` fires in March and September on
 *   the start's day-of-month, while `YEARLY BYMONTHDAY=[15]` fires only in the start's month.
 * - A MONTHLY rule with BYDAY fires on every matching weekday of each aligned month, per RFC 5545.
 * - MONTHLY and YEARLY rules without BY-filters **skip periods that lack the start's day-of-month**
 *   rather than sliding the occurrence earlier: a MONTHLY series starting January 31st fires on
 *   March 31st, not February 28th, and a YEARLY series starting February 29th fires only in leap
 *   years.
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
    val spanDays = endDate?.let { daysBetween(date, it) }?.coerceAtLeast(0) ?: 0
    val rule = recurrenceRule
        ?: return if (overlapsWindow(date, spanDays, rangeStart, rangeEnd)) listOf(this) else emptyList()

    val occurrences = mutableListOf<KalendarSyncEvent>()
    var occurrenceCount = 0
    var periodIndex = rule.firstRelevantPeriod(
        seriesStart = date,
        windowFloor = rangeStart.minus(spanDays, DateTimeUnit.DAY),
    )
    var current = if (rule.enumeratesDaily()) {
        maxOf(date, rule.walkFloor(seriesStart = date, rangeStart = rangeStart, spanDays = spanDays))
    } else {
        date.plusPeriods(rule, periodIndex)
    }

    while (withinBounds(rule, occurrenceCount, current, rangeEnd)) {
        if (current.isOccurrenceOf(rule, seriesStart = date)) {
            if (overlapsWindow(current, spanDays, rangeStart, rangeEnd) && current !in exceptionDates) {
                occurrences += copyWithDate(current)
            }
            occurrenceCount++
        }
        current = if (rule.enumeratesDaily()) {
            current.plus(1, DateTimeUnit.DAY)
        } else {
            date.plusPeriods(rule, ++periodIndex)
        }
    }
    return occurrences
}

/**
 * Returns `true` when [this] is a real occurrence of [rule] for a series starting on [seriesStart]
 * — it satisfies every BY-filter, sits in a period the interval selects, keeps whatever RFC 5545
 * anchors to the series start, and did not land on a clamped day-of-month.
 */
private fun LocalDate.isOccurrenceOf(rule: KalendarRule, seriesStart: LocalDate): Boolean =
    matchesFilters(rule = rule, seriesStart = seriesStart) &&
        alignsWithInterval(rule, seriesStart) &&
        preservesPeriodDayOfMonth(rule = rule, seriesStart = seriesStart)

/**
 * Returns the index of the first period that could still produce an occurrence overlapping a
 * window floored at [windowFloor], for the period-stepped rules that [plusPeriods] drives.
 *
 * Occurrences before the window only matter when [KalendarRule.count] is set, since they consume
 * it; with no count the walk can start at the window instead of at [seriesStart], which keeps a
 * long-running series from costing one iteration per elapsed period on every query. Integer
 * division floors, so the returned index is a lower bound and never skips a live occurrence.
 */
private fun KalendarRule.firstRelevantPeriod(seriesStart: LocalDate, windowFloor: LocalDate): Int {
    if (count != null || enumeratesDaily() || windowFloor <= seriesStart) {
        return 0
    }
    val periods = when (frequency) {
        KalendarRecurrenceFrequency.DAILY ->
            daysBetween(seriesStart, windowFloor) / interval

        KalendarRecurrenceFrequency.WEEKLY ->
            daysBetween(seriesStart, windowFloor) / (interval * DAYS_PER_WEEK)

        KalendarRecurrenceFrequency.MONTHLY ->
            monthsBetween(from = seriesStart, to = windowFloor) / interval

        KalendarRecurrenceFrequency.YEARLY ->
            (windowFloor.year - seriesStart.year) / interval
    }
    return periods.coerceAtLeast(0)
}

/**
 * Returns the earliest date a day-at-a-time walk needs to visit. Mirrors [firstRelevantPeriod] for
 * the day-enumerating rules: they filter on absolute alignment with [seriesStart] rather than on
 * step count, so when no [KalendarRule.count] has to be consumed the walk can jump straight to the
 * window instead of testing every intervening day.
 */
private fun KalendarRule.walkFloor(seriesStart: LocalDate, rangeStart: LocalDate, spanDays: Int): LocalDate =
    if (count != null) seriesStart else rangeStart.minus(spanDays, DateTimeUnit.DAY)

/**
 * Returns the date [periods] whole intervals after this series start. Period-stepped rules are
 * always recomputed from the series start rather than from the previous candidate, so a month that
 * clamps (January 31st + 1 month is February 28th) cannot drag every later occurrence back with it.
 */
private fun LocalDate.plusPeriods(rule: KalendarRule, periods: Int): LocalDate {
    val steps = periods.toLong() * rule.interval
    return when (rule.frequency) {
        KalendarRecurrenceFrequency.DAILY -> plus(steps, DateTimeUnit.DAY)
        KalendarRecurrenceFrequency.WEEKLY -> plus(steps * DAYS_PER_WEEK, DateTimeUnit.DAY)
        KalendarRecurrenceFrequency.MONTHLY -> plus(steps, DateTimeUnit.MONTH)
        KalendarRecurrenceFrequency.YEARLY -> plus(steps, DateTimeUnit.YEAR)
    }
}

/**
 * Returns `false` when a period-stepped MONTHLY or YEARLY candidate landed on a different
 * day-of-month than the series start, which happens only where the calendar clamped a short period
 * — January 31st plus one month is February 28th, February 29th plus one year is February 28th.
 * RFC 5545 skips periods that do not contain the start's day-of-month instead of moving the
 * occurrence earlier, so those candidates are not occurrences at all and must not consume
 * [KalendarRule.count].
 */
private fun LocalDate.preservesPeriodDayOfMonth(rule: KalendarRule, seriesStart: LocalDate): Boolean {
    if (rule.enumeratesDaily()) {
        return true
    }
    return when (rule.frequency) {
        KalendarRecurrenceFrequency.MONTHLY, KalendarRecurrenceFrequency.YEARLY ->
            dayOfMonth == seriesStart.dayOfMonth

        KalendarRecurrenceFrequency.DAILY, KalendarRecurrenceFrequency.WEEKLY ->
            true
    }
}

/**
 * Returns `true` when an occurrence starting on [occurrenceStart] and lasting [spanDays] further
 * days overlaps [[rangeStart], [rangeEnd]].
 *
 * This is the emit filter, deliberately separate from [withinBounds]'s loop bound: an occurrence
 * that starts before [rangeStart] but whose multi-day span reaches into the window must still be
 * emitted, which a bare `occurrenceStart >= rangeStart` guard would drop. Occurrences are still
 * enumerated — and [KalendarRule.count] still consumed — from the series start, so widening what
 * is *emitted* never resurrects a series whose count was exhausted before the window.
 */
private fun overlapsWindow(
    occurrenceStart: LocalDate,
    spanDays: Int,
    rangeStart: LocalDate,
    rangeEnd: LocalDate,
): Boolean = overlapsRange(
    eventStart = occurrenceStart,
    eventEndInclusive = occurrenceStart.plus(spanDays, DateTimeUnit.DAY),
    rangeStart = rangeStart,
    rangeEnd = rangeEnd,
)

private fun withinBounds(rule: KalendarRule, count: Int, current: LocalDate, rangeEnd: LocalDate): Boolean =
    (rule.count == null || count < rule.count) &&
        (rule.until == null || current <= rule.until) &&
        current <= rangeEnd

/**
 * Returns `true` if this date satisfies all BYMONTH, BYMONTHDAY, and BYDAY constraints in [rule],
 * plus the YEARLY series-start anchoring of [matchesYearlyAnchor].
 */
private fun LocalDate.matchesFilters(rule: KalendarRule, seriesStart: LocalDate): Boolean =
    (rule.byMonth.isEmpty() || monthNumber in rule.byMonth) &&
        (rule.byMonthDay.isEmpty() || dayOfMonth in rule.byMonthDay) &&
        (rule.byDay.isEmpty() || dayOfWeek.toKalendarWeekDay() in rule.byDay) &&
        matchesYearlyAnchor(rule = rule, seriesStart = seriesStart)

/**
 * Applies RFC 5545's rule that a YEARLY occurrence takes from DTSTART every component its
 * BY-parts leave unspecified: with no BYMONTH the month comes from [seriesStart], and with
 * neither BYMONTHDAY nor BYDAY the day-of-month does too.
 *
 * Without the month anchor a `FREQ=YEARLY;BYMONTHDAY=15` rule would fire on the 15th of all twelve
 * months instead of once a year, because BYMONTHDAY alone never constrains the month. Non-YEARLY
 * frequencies get their month or day from the period they step through, so this returns `true`
 * for them.
 */
private fun LocalDate.matchesYearlyAnchor(rule: KalendarRule, seriesStart: LocalDate): Boolean {
    if (rule.frequency != KalendarRecurrenceFrequency.YEARLY) {
        return true
    }
    val monthAnchored = rule.byMonth.isNotEmpty() || monthNumber == seriesStart.monthNumber
    val dayAnchored = rule.byMonthDay.isNotEmpty() ||
        rule.byDay.isNotEmpty() ||
        dayOfMonth == seriesStart.dayOfMonth
    return monthAnchored && dayAnchored
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
private fun KalendarSyncEvent.copyWithDate(date: LocalDate): KalendarSyncEvent = copyAsBasic(
    id = null,
    date = date,
    endDate = endDate?.let { date.plus(daysBetween(this.date, it), DateTimeUnit.DAY) },
)

private fun daysBetween(from: LocalDate, to: LocalDate): Int =
    (to.toEpochDays() - from.toEpochDays()).toInt()
