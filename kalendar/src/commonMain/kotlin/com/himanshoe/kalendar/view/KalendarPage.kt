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

package com.himanshoe.kalendar.view

import com.himanshoe.kalendar.foundation.event.KalendarEvent
import com.himanshoe.kalendar.foundation.event.KalendarEvents
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn
import kotlin.time.Clock

/**
 * Default [KalendarViewConfig.monthNameFormatter]: kotlinx-datetime has no locale-aware
 * formatting, so this is plain English (e.g. "August") — pass your own formatter (backed by
 * `java.time`, `NSDateFormatter`, `Intl`, etc.) for anything else.
 */
internal val defaultMonthNameFormatter: (Month) -> String = {
    it.name.lowercase().replaceFirstChar { char -> char.titlecase() }
}

/** Default [KalendarViewConfig.dayOfWeekLabelFormatter]: the day name's first letter. */
internal val defaultDayOfWeekLabelFormatter: (DayOfWeek) -> String = { it.name.take(1) }

/**
 * Multi-day events are expanded across at most this many days, guarding against a
 * caller-supplied `endDate` far in the future turning into an unbounded loop.
 */
private const val MAX_EVENT_SPAN_DAYS = 366

internal fun todayInSystemTimeZone(): LocalDate = Clock.System.todayIn(TimeZone.currentSystemDefault())

internal fun LocalDate.startOfWeek(startDayOfWeek: DayOfWeek): LocalDate =
    minus((dayOfWeek.ordinal - startDayOfWeek.ordinal + 7) % 7, DateTimeUnit.DAY)

internal fun LocalDate.startOfMonth(): LocalDate = minus(dayOfMonth - 1, DateTimeUnit.DAY)

internal fun LocalDate.startOfYear(): LocalDate = LocalDate(year, 1, 1)

internal fun weekDates(weekStart: LocalDate): List<LocalDate> =
    (0..6).map { weekStart.plus(it, DateTimeUnit.DAY) }

internal fun monthGridDates(monthStart: LocalDate, startDayOfWeek: DayOfWeek): List<LocalDate> {
    val lastDayOfMonth = monthStart.plus(1, DateTimeUnit.MONTH).minus(1, DateTimeUnit.DAY)
    val leadingPaddingDays = (monthStart.dayOfWeek.ordinal - startDayOfWeek.ordinal + 7) % 7
    return (-leadingPaddingDays until lastDayOfMonth.dayOfMonth).map {
        monthStart.plus(it.toLong(), DateTimeUnit.DAY)
    }
}

internal fun weeksBetween(from: LocalDate, to: LocalDate, startDayOfWeek: DayOfWeek): Int {
    val fromWeekStart = from.startOfWeek(startDayOfWeek)
    val toWeekStart = to.startOfWeek(startDayOfWeek)
    return ((toWeekStart.toEpochDays() - fromWeekStart.toEpochDays()) / 7).toInt()
}

internal fun monthsBetween(from: LocalDate, to: LocalDate): Int {
    val fromMonthStart = from.startOfMonth()
    val toMonthStart = to.startOfMonth()
    return (toMonthStart.year - fromMonthStart.year) * 12 + (toMonthStart.monthNumber - fromMonthStart.monthNumber)
}

internal fun yearsBetween(from: LocalDate, to: LocalDate): Int = to.year - from.year

internal fun daysOfWeekStartingAt(startDayOfWeek: DayOfWeek): List<DayOfWeek> {
    val all = DayOfWeek.entries
    return all.drop(startDayOfWeek.ordinal) + all.take(startDayOfWeek.ordinal)
}

internal fun monthTitle(
    monthStart: LocalDate,
    monthNameFormatter: (Month) -> String = defaultMonthNameFormatter,
): String = "${monthNameFormatter(monthStart.month)} ${monthStart.year}"

/**
 * Groups events by every date they occupy — a multi-day event (one with
 * [KalendarEvent.endDate] set) appears under each date from [KalendarEvent.date] through
 * [KalendarEvent.endDate], inclusive, capped at [MAX_EVENT_SPAN_DAYS] days.
 */
internal fun KalendarEvents.byDateExpandingSpans(): Map<LocalDate, List<KalendarEvent>> {
    val result = mutableMapOf<LocalDate, MutableList<KalendarEvent>>()
    for (event in this) {
        val start = event.date
        val requestedEnd = event.endDate ?: start
        val end = if (requestedEnd > start) {
            minOf(requestedEnd, start.plus(MAX_EVENT_SPAN_DAYS, DateTimeUnit.DAY))
        } else {
            start
        }
        var current = start
        while (current <= end) {
            result.getOrPut(current) { mutableListOf() }.add(event)
            current = current.plus(1, DateTimeUnit.DAY)
        }
    }
    return result
}
