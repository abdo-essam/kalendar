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
import kotlinx.datetime.daysUntil
import kotlinx.datetime.number

private const val ICAL_DATE_LENGTH = 8
private const val YEAR_END_IDX = 4
private const val MONTH_END_IDX = 6
private const val YEAR_DIGIT_WIDTH = 4
private const val TWO_DIGIT_WIDTH = 2
private const val SECONDS_PER_MINUTE = 60L
private const val SECONDS_PER_HOUR = 3_600L
private const val SECONDS_PER_DAY = 86_400L
private const val DEFAULT_TIMED_DURATION_SECONDS = 3_600L

/**
 * Serialises this rule to the value portion of an RFC 5545 RRULE property,
 * e.g. `FREQ=WEEKLY;INTERVAL=2;BYDAY=MO,WE,FR`. Shared by the iCal codec and the
 * Android `CalendarContract` provider, which stores the same value format in its
 * `Events.RRULE` column.
 */
internal fun KalendarRule.toRRuleString(): String = buildString {
    append("FREQ=${frequency.name}")
    if (interval != 1) {
        append(";INTERVAL=$interval")
    }
    count?.let { append(";COUNT=$it") }
    until?.let { append(";UNTIL=${it.toIcalDateString()}") }
    if (byDay.isNotEmpty()) {
        append(";BYDAY=${byDay.joinToString(",") { it.toIcalWeekDayString() }}")
    }
    if (byMonthDay.isNotEmpty()) {
        append(";BYMONTHDAY=${byMonthDay.joinToString(",")}")
    }
    if (byMonth.isNotEmpty()) {
        append(";BYMONTH=${byMonth.joinToString(",")}")
    }
}

/**
 * Parses the value portion of an RFC 5545 RRULE property into a [KalendarRule].
 * Returns `null` if the FREQ component is missing or unrecognised.
 */
internal fun parseRRuleString(value: String): KalendarRule? = runCatching {
    val parts = value.split(";").mapNotNull { segment ->
        val eq = segment.indexOf('=')
        if (eq < 0) {
            null
        } else {
            segment.substring(0, eq) to segment.substring(eq + 1)
        }
    }.toMap()

    val frequency = when (parts["FREQ"]) {
        "DAILY" -> KalendarRecurrenceFrequency.DAILY
        "WEEKLY" -> KalendarRecurrenceFrequency.WEEKLY
        "MONTHLY" -> KalendarRecurrenceFrequency.MONTHLY
        "YEARLY" -> KalendarRecurrenceFrequency.YEARLY
        else -> return null
    }
    KalendarRule(
        frequency = frequency,
        interval = parts["INTERVAL"]?.toIntOrNull() ?: 1,
        count = parts["COUNT"]?.toIntOrNull(),
        until = parts["UNTIL"]?.let { parseIcalDateString(it) },
        byDay = parts["BYDAY"]
            ?.split(",")
            ?.mapNotNull { it.trim().toKalendarWeekDay() }
            ?: emptyList(),
        byMonthDay = parts["BYMONTHDAY"]
            ?.split(",")
            ?.mapNotNull { it.trim().toIntOrNull() }
            ?: emptyList(),
        byMonth = parts["BYMONTH"]
            ?.split(",")
            ?.mapNotNull { it.trim().toIntOrNull() }
            ?: emptyList(),
    )
}.getOrNull()

/**
 * Builds the RFC 2445 DURATION value that Android's `CalendarContract` requires in place of
 * `DTEND` for recurring events: `P{n}D` spanning [KalendarSyncEvent.date] through
 * [KalendarSyncEvent.endDate] inclusive for all-day events (`P1D` for a single day), or
 * `PT{seconds}S` from the start/end time difference for timed events (one hour when the end
 * time is missing or not after the start).
 */
internal fun recurrenceDurationString(event: KalendarSyncEvent): String {
    return if (event.isAllDay) {
        val days = event.endDate?.let { event.date.daysUntil(it) + 1 } ?: 1
        "P${days.coerceAtLeast(1)}D"
    } else {
        val start = event.startTime
        val end = event.endTime
        val seconds = if (start != null && end != null) {
            (end.toSecondOfDay() - start.toSecondOfDay()).toLong()
        } else {
            DEFAULT_TIMED_DURATION_SECONDS
        }
        "PT${if (seconds > 0) seconds else DEFAULT_TIMED_DURATION_SECONDS}S"
    }
}

/**
 * Builds the iCal EXDATE value list Android's `CalendarContract` stores in its `Events.EXDATE`
 * column: comma-joined `yyyyMMdd` values for all-day events, or comma-joined
 * `yyyyMMdd'T'HHmmss'Z'` values (at the event's start time) for timed events.
 * Returns `null` when the event has no [KalendarSyncEvent.exceptionDates].
 */
internal fun recurrenceExdateString(event: KalendarSyncEvent): String? {
    if (event.exceptionDates.isEmpty()) {
        return null
    }
    return if (event.isAllDay) {
        event.exceptionDates.joinToString(",") { it.toIcalDateString() }
    } else {
        val time = event.startTime ?: LocalTime(hour = 0, minute = 0)
        val suffix = "T${time.hour.zeroPadded(TWO_DIGIT_WIDTH)}${time.minute.zeroPadded(TWO_DIGIT_WIDTH)}" +
            "${time.second.zeroPadded(TWO_DIGIT_WIDTH)}Z"
        event.exceptionDates.joinToString(",") { "${it.toIcalDateString()}$suffix" }
    }
}

/**
 * Parses an RFC 2445 DURATION value (`P#D`, `PT#H`, `PT#M`, `PT#S`, or combinations such as
 * `P1DT12H`) into total seconds. Returns `null` for anything else, including an empty duration.
 */
internal fun parseRecurrenceDurationSeconds(value: String): Long? {
    val match = DURATION_REGEX.matchEntire(value.trim()) ?: return null
    val components = match.groupValues.drop(1)
    if (components.all { it.isEmpty() }) {
        return null
    }
    val unitSeconds = listOf(SECONDS_PER_DAY, SECONDS_PER_HOUR, SECONDS_PER_MINUTE, 1L)
    return components.zip(unitSeconds) { component, unit -> (component.toLongOrNull() ?: 0L) * unit }.sum()
}

private val DURATION_REGEX = Regex("""P(?:(\d+)D)?(?:T(?:(\d+)H)?(?:(\d+)M)?(?:(\d+)S)?)?""")

/** Formats this date in the iCal basic form `yyyyMMdd`. */
internal fun LocalDate.toIcalDateString(): String =
    "${year.zeroPadded(YEAR_DIGIT_WIDTH)}${month.number.zeroPadded(TWO_DIGIT_WIDTH)}" +
        day.zeroPadded(TWO_DIGIT_WIDTH)

/**
 * Parses an iCal basic date (`yyyyMMdd`, with an optional `T...` time suffix that is ignored)
 * into a [LocalDate]. Returns `null` for unparseable input.
 */
internal fun parseIcalDateString(value: String): LocalDate? = runCatching {
    val v = value.substringBefore('T').filter { it.isDigit() }
    if (v.length < ICAL_DATE_LENGTH) {
        return null
    }
    LocalDate(
        year = v.substring(0, YEAR_END_IDX).toInt(),
        month = v.substring(YEAR_END_IDX, MONTH_END_IDX).toInt(),
        day = v.substring(MONTH_END_IDX, ICAL_DATE_LENGTH).toInt(),
    )
}.getOrNull()

/** Zero-pads this value to [width] digits. */
internal fun Int.zeroPadded(width: Int): String = toString().padStart(width, '0')

/** Maps a [KalendarWeekDay] to its two-letter RFC 5545 BYDAY code. */
private fun KalendarWeekDay.toIcalWeekDayString(): String = when (this) {
    KalendarWeekDay.MONDAY -> "MO"
    KalendarWeekDay.TUESDAY -> "TU"
    KalendarWeekDay.WEDNESDAY -> "WE"
    KalendarWeekDay.THURSDAY -> "TH"
    KalendarWeekDay.FRIDAY -> "FR"
    KalendarWeekDay.SATURDAY -> "SA"
    KalendarWeekDay.SUNDAY -> "SU"
}

/** Maps a two-letter RFC 5545 BYDAY code back to a [KalendarWeekDay], or `null` if unknown. */
private fun String.toKalendarWeekDay(): KalendarWeekDay? = when (this) {
    "MO" -> KalendarWeekDay.MONDAY
    "TU" -> KalendarWeekDay.TUESDAY
    "WE" -> KalendarWeekDay.WEDNESDAY
    "TH" -> KalendarWeekDay.THURSDAY
    "FR" -> KalendarWeekDay.FRIDAY
    "SA" -> KalendarWeekDay.SATURDAY
    "SU" -> KalendarWeekDay.SUNDAY
    else -> null
}
