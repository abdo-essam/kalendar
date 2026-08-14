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
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

private const val END_OF_DAY_HOUR = 23
private const val END_OF_DAY_MINUTE = 59

/**
 * The start time a timed event is stored with, defaulting to midnight when
 * [KalendarSyncEvent.startTime] is absent.
 */
internal fun KalendarSyncEvent.resolvedStartTime(): LocalTime = startTime ?: LocalTime(hour = 0, minute = 0)

/**
 * The end time a timed event is stored with, defaulting to 23:59 when [KalendarSyncEvent.endTime]
 * is absent.
 */
internal fun KalendarSyncEvent.resolvedEndTime(): LocalTime =
    endTime ?: LocalTime(hour = END_OF_DAY_HOUR, minute = END_OF_DAY_MINUTE)

/**
 * The calendar date a **timed** event's end instant falls on — the date half of the `DTEND` that
 * pairs with a `DTSTART` on [KalendarSyncEvent.date].
 *
 * Prefers an explicit [KalendarSyncEvent.endDate], which is how a timed event spanning more than
 * one day states its span. Otherwise the event ends on its start date, except where that would put
 * the end at or before the start: an event running 22:00 to 06:00 with no end date can only mean
 * the following morning, and RFC 5545 requires `DTEND` to be strictly later than `DTSTART`, so the
 * end rolls to the next day rather than emitting a backwards interval.
 */
internal fun KalendarSyncEvent.timedEndDate(): LocalDate {
    val base = endDate?.coerceAtLeast(date) ?: date
    return if (base == date && resolvedEndTime() <= resolvedStartTime()) {
        base.plus(1, DateTimeUnit.DAY)
    } else {
        base
    }
}

/**
 * Returns `true` when an event occupying [eventStart]..[eventEndInclusive] **overlaps** the closed
 * range [[rangeStart], [rangeEnd]] — that is, `eventStart <= rangeEnd && eventEnd >= rangeStart`.
 *
 * This is the canonical range predicate for the whole module. Filtering on the start date alone
 * drops any multi-day event that begins before the window, so a span from August 28th to
 * September 5th would vanish from a September query; the symmetric end-date-only filter drops the
 * same span from an August query. Both get a window wholly *inside* a long event wrong.
 *
 * [eventEndInclusive] is the **inclusive** last date the event occupies, matching
 * [KalendarSyncEvent.endDate]; `null` means a single-day event, so the end falls back to
 * [eventStart]. Callers holding an exclusive next-midnight end (the all-day storage convention on
 * Android and in `.ics`) should convert it with [allDayInclusiveEndDate] first. An end earlier
 * than the start is treated as a single-day event rather than as an empty interval.
 */
internal fun overlapsRange(
    eventStart: LocalDate,
    eventEndInclusive: LocalDate?,
    rangeStart: LocalDate,
    rangeEnd: LocalDate,
): Boolean {
    val eventEnd = (eventEndInclusive ?: eventStart).coerceAtLeast(eventStart)
    return eventStart <= rangeEnd && eventEnd >= rangeStart
}

/**
 * Epoch millis of UTC midnight on [date] — the `DTSTART` basis Android's `CalendarContract`
 * requires for all-day events. Storing local midnight instead shifts the event by one day in any
 * zone with a non-zero UTC offset, because the provider expands all-day instances in UTC.
 */
internal fun allDayStartUtcMillis(date: LocalDate): Long =
    date.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()

/**
 * Epoch millis of UTC midnight on the day after [endDateInclusive] (or after [startDate] for a
 * single-day event) — the exclusive `DTEND`/duration anchor pairing [allDayStartUtcMillis].
 */
internal fun allDayEndExclusiveUtcMillis(startDate: LocalDate, endDateInclusive: LocalDate?): Long =
    (endDateInclusive ?: startDate)
        .plus(1, DateTimeUnit.DAY)
        .atStartOfDayIn(TimeZone.UTC)
        .toEpochMilliseconds()

/**
 * Maps an all-day start stored as epoch millis back to its calendar date using UTC — the inverse
 * of [allDayStartUtcMillis]. Converting through the local zone instead resolves a correctly
 * stored UTC-midnight value to the previous date in every UTC-negative zone.
 */
internal fun allDayDateFromUtcMillis(millis: Long): LocalDate =
    Instant.fromEpochMilliseconds(millis).toLocalDateTime(TimeZone.UTC).date

/**
 * Reconstructs the inclusive last date of an all-day event from its stored end date-time,
 * tolerating both storage conventions: an exclusive next-midnight end (what this library and
 * Android's `CalendarContract` write) resolves to the previous day, while an end anywhere inside
 * the last day (EventKit-native all-day events end at 23:59:59) already names the last day.
 * Returns `null` for a single-day event, matching [KalendarSyncEvent.endDate]'s contract.
 */
internal fun allDayInclusiveEndDate(startDate: LocalDate, endDateTime: LocalDateTime): LocalDate? {
    val inclusive = if (endDateTime.time == LocalTime(hour = 0, minute = 0)) {
        endDateTime.date.minus(1, DateTimeUnit.DAY)
    } else {
        endDateTime.date
    }
    return inclusive.takeIf { it > startDate }
}
