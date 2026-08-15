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

package com.himanshoe.kalendar.foundation.datetime

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration
import kotlin.time.Instant

/**
 * Longest run of days one span is expanded across by [datesOfSpanIn].
 *
 * A calendar's event list is caller-supplied and an end far in the future is a data bug, not a
 * three-thousand-year meeting: without a cap one malformed span turns into an unbounded list, and
 * every consumer of that list — a date index, a lane packer, a grid — inherits the unboundedness.
 *
 * ```kotlin
 * val cappedEnd = start.plus(MAX_SPAN_DAYS, DateTimeUnit.DAY)
 * ```
 */
public const val MAX_SPAN_DAYS: Int = 366

/**
 * Which calendar date [this] instant falls on when the calendar is read in [timeZone].
 *
 * There is no zone-free answer. The same moment is two different dates either side of the date line,
 * so a calendar that pins itself to an office's zone must ask this rather than the device:
 *
 * ```kotlin
 * val newYearMorning = Instant.parse("2026-01-01T00:30:00Z")
 * newYearMorning.dateIn(TimeZone.of("Pacific/Kiritimati")) // 2026-01-01, already 14:30 there
 * newYearMorning.dateIn(TimeZone.of("Pacific/Honolulu"))   // 2025-12-31, still the night before
 * ```
 */
public fun Instant.dateIn(timeZone: TimeZone): LocalDate = toLocalDateTime(timeZone).date

/**
 * The same moment as [this] wall-clock time in [source], read off a clock in [target].
 *
 * This is what makes an event survive being looked at from somewhere else. A 09:00 stand-up recorded
 * in Berlin stays 09:00 for anyone whose calendar is pinned to Berlin, whatever zone their device is
 * in — and shifts, date included, for a calendar read anywhere else:
 *
 * ```kotlin
 * val utc = TimeZone.UTC
 * val lateShift = LocalDateTime(2026, 1, 15, 23, 0)
 * lateShift.sameInstantIn(source = utc, target = TimeZone.of("Europe/London")) // 2026-01-15T23:00
 * lateShift.sameInstantIn(source = utc, target = TimeZone.of("Asia/Tokyo"))    // 2026-01-16T08:00
 * ```
 *
 * A wall-clock time that a daylight-saving jump skipped in [source] has no instant of its own; it
 * resolves as though the pre-transition offset were still in force, which is the only reading that
 * keeps the function total.
 */
public fun LocalDateTime.sameInstantIn(source: TimeZone, target: TimeZone): LocalDateTime =
    toInstant(source).toLocalDateTime(target)

/**
 * How long the calendar day [this] actually lasts in [timeZone] — 23, 24 or 25 hours, and neither
 * whole nor round in the zones whose daylight-saving step is half an hour.
 *
 * ```kotlin
 * val berlin = TimeZone.of("Europe/Berlin")
 * LocalDate(2026, 3, 29).dayLengthIn(berlin) // 23h, the clocks went forward
 * LocalDate(2026, 10, 25).dayLengthIn(berlin) // 25h, they went back
 * ```
 *
 * Anything that measures a day by adding 24 hours to its midnight is wrong twice a year in most of
 * the world. Ask this instead — for a midnight rollover, for a progress bar across the day, for the
 * real gap between two dates.
 *
 * It is deliberately *not* what an hour grid divides its height by. Those grids are ruled in wall
 * clock, and wall clock reaches 24:00 on every date there is; a 23-hour day simply has an hour no
 * event can occupy, and a 25-hour day an hour two events can share.
 */
public fun LocalDate.dayLengthIn(timeZone: TimeZone): Duration =
    plus(1, DateTimeUnit.DAY).atStartOfDayIn(timeZone) - atStartOfDayIn(timeZone)

/**
 * Every date the moment range [start] to [end] occupies when read in [timeZone], in order.
 *
 * This is the zone-dependent half of multi-day expansion: which days a span lands on is a question
 * about where the day boundaries fall, and those move with the zone.
 *
 * ```kotlin
 * val tokyo = TimeZone.of("Asia/Tokyo")
 * val overnight = Instant.parse("2026-01-15T23:00:00Z") // 2026-01-16T08:00 in Tokyo
 * datesOfSpanIn(start = overnight, end = overnight + 2.hours, timeZone = tokyo)
 * // [2026-01-16] — one Tokyo date, though it is two dates in UTC
 * ```
 *
 * An [end] landing exactly on a midnight belongs to the day that ends there, not the one that
 * begins: an event booked 09:00 until 00:00 occupies one date, and a back-to-back pair does not
 * make both of them look busy. An [end] at or before [start] is a single date, and a span running
 * past [MAX_SPAN_DAYS] is truncated there.
 *
 * The days are walked as calendar dates rather than counted from the elapsed duration, which is the
 * only way to get a daylight-saving day right: the 23 hours from 23:00 to 23:00 across a spring
 * forward are still two dates, and the 25 hours from midnight to midnight across an autumn fall
 * back are still one.
 */
public fun datesOfSpanIn(start: Instant, end: Instant, timeZone: TimeZone): List<LocalDate> {
    val firstDate = start.dateIn(timeZone)
    if (end <= start) {
        return listOf(firstDate)
    }
    val endDate = end.dateIn(timeZone)
    val lastDate = if (endDate > firstDate && end == endDate.atStartOfDayIn(timeZone)) {
        endDate.minus(1, DateTimeUnit.DAY)
    } else {
        endDate
    }
    val cappedLast = minOf(lastDate, firstDate.plus(MAX_SPAN_DAYS, DateTimeUnit.DAY))
    return buildList {
        var current = firstDate
        while (current <= cappedLast) {
            add(current)
            current = current.plus(1, DateTimeUnit.DAY)
        }
    }
}
