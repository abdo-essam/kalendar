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
