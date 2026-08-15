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
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.number
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

private const val ICAL_DATE_LENGTH = 8
private const val ICAL_TIME_LENGTH = 6
private const val YEAR_END_IDX = 4
private const val MONTH_END_IDX = 6
private const val YEAR_DIGIT_WIDTH = 4
private const val TWO_DIGIT_WIDTH = 2
private const val MINUTE_END_IDX = 4
private const val SECONDS_PER_MINUTE = 60L
private const val SECONDS_PER_HOUR = 3_600L
private const val SECONDS_PER_DAY = 86_400L
private const val SECONDS_PER_WEEK = 604_800L
private const val DEFAULT_TIMED_DURATION_SECONDS = 3_600L

/**
 * Serialises this rule to the value portion of an RFC 5545 RRULE property,
 * e.g. `FREQ=WEEKLY;INTERVAL=2;BYDAY=MO,WE,FR`. Shared by the iCal codec and the
 * Android `CalendarContract` provider, which stores the same value format in its
 * `Events.RRULE` column.
 *
 * @param untilValue The already-rendered UNTIL value to emit in place of the plain `yyyyMMdd`
 *   form. RFC 5545 requires UNTIL to carry the same value type as DTSTART, so a timed event has
 *   to pass a DATE-TIME here — [floatingUntilValue] for the floating DTSTART the iCal codec
 *   writes, [utcUntilValue] where DTSTART is anchored to a time zone, as on Android. `null`
 *   emits the DATE form, which is what an all-day event's `DTSTART;VALUE=DATE` requires.
 *   Ignored when the rule has no [KalendarRule.until].
 */
internal fun KalendarRule.toRRuleString(untilValue: String? = null): String = buildString {
    append("FREQ=${frequency.name}")
    if (interval != 1) {
        append(";INTERVAL=$interval")
    }
    count?.let { append(";COUNT=$it") }
    until?.let { append(";UNTIL=${untilValue ?: it.toIcalDateString()}") }
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
 *
 * @param timeZone The zone a UTC UNTIL value (`yyyyMMdd'T'HHmmss'Z'`) is resolved back through to
 *   reach the local date [KalendarRule.until] holds. This has to be the zone the event's
 *   occurrences are anchored in — the offset routinely pushes the UTC digits onto the
 *   neighbouring day, and a boundary read one day late resurrects the occurrence an UNTIL was
 *   written to drop. Defaults to [TimeZone.UTC], which leaves the digits as written and suits the
 *   zone-free floating values the iCal codec produces.
 */
internal fun parseRRuleString(value: String, timeZone: TimeZone = TimeZone.UTC): KalendarRule? = runCatching {
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
        until = parts["UNTIL"]?.let { parseIcalDateValue(value = it, timeZone = timeZone) },
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
 * `yyyyMMdd'T'HHmmss'Z'` values for timed events. Timed values are true UTC instants: the
 * excluded occurrence's local start (`exceptionDate` at the event's start time) is resolved
 * through [timeZone] — the zone the provider stores in `EVENT_TIMEZONE` — so the provider's
 * instant-based EXDATE matching finds the occurrence. Returns `null` when the event has no
 * [KalendarSyncEvent.exceptionDates].
 *
 * @param timeZone The zone the event's occurrences are anchored in on the target device.
 */
internal fun recurrenceExdateString(event: KalendarSyncEvent, timeZone: TimeZone): String? {
    if (event.exceptionDates.isEmpty()) {
        return null
    }
    return if (event.isAllDay) {
        event.exceptionDates.joinToString(",") { it.toIcalDateString() }
    } else {
        val time = event.startTime ?: LocalTime(hour = 0, minute = 0)
        event.exceptionDates.joinToString(",") { exceptionDate ->
            val utc = LocalDateTime(date = exceptionDate, time = time)
                .toInstant(timeZone)
                .toLocalDateTime(TimeZone.UTC)
            "${utc.date.toIcalDateString()}T${utc.time.toIcalTimeString()}Z"
        }
    }
}

/**
 * Renders [KalendarRule.until] as the floating DATE-TIME an RRULE needs when DTSTART is itself a
 * floating DATE-TIME, which is the only timed form the iCal codec writes. RFC 5545 requires the
 * two value types to match, and forbids a UTC UNTIL against a DTSTART in local time.
 *
 * The value sits at the very end of the day because [KalendarRule.until] names the last *date* an
 * occurrence may fall on and UNTIL is inclusive: an occurrence starting at any time that day is
 * still bounded by it, whatever hour the series runs at.
 *
 * Returns `null` — meaning "emit the plain DATE form" — for an all-day event, whose
 * `DTSTART;VALUE=DATE` requires exactly that, and for a rule with no [KalendarRule.until].
 */
internal fun floatingUntilValue(rule: KalendarRule, isAllDay: Boolean): String? {
    val until = rule.until
    if (isAllDay || until == null) {
        return null
    }
    return "${until.toIcalDateString()}T${END_OF_DAY.toIcalTimeString()}"
}

/**
 * Renders [KalendarRule.until] as the UTC DATE-TIME an RRULE needs when DTSTART is anchored to a
 * time zone rather than floating — the Android `CalendarContract` case, where the row carries an
 * `EVENT_TIMEZONE`. RFC 5545 requires UNTIL to be in UTC for such a DTSTART.
 *
 * The end of the last permitted local day in [timeZone] is converted to the instant it names, so
 * the boundary keeps meaning the same moment in the zone the occurrences are anchored in. Writing
 * the plain DATE form here instead is the classic way to lose the final occurrence of a timed
 * series: a consumer reads it as midnight, and every occurrence later in that day falls outside.
 *
 * Returns `null` for an all-day event and for a rule with no [KalendarRule.until], leaving the
 * DATE form in place.
 */
internal fun utcUntilValue(rule: KalendarRule, isAllDay: Boolean, timeZone: TimeZone): String? {
    val until = rule.until
    if (isAllDay || until == null) {
        return null
    }
    val utc = LocalDateTime(date = until, time = END_OF_DAY).toInstant(timeZone).toLocalDateTime(TimeZone.UTC)
    return "${utc.date.toIcalDateString()}T${utc.time.toIcalTimeString()}Z"
}

private val END_OF_DAY = LocalTime(hour = 23, minute = 59, second = 59)

/**
 * Parses an iCal DATE or DATE-TIME property value back to the local date it falls on in
 * [timeZone] — the form EXDATE entries and a timed rule's UNTIL both arrive in. Accepts the plain
 * date form (`yyyyMMdd`), the UTC date-time form this library writes (`yyyyMMdd'T'HHmmss'Z'`,
 * converted from UTC back to [timeZone] so a value written across a midnight-crossing offset
 * lands on the right local day), and tolerates floating date-times or trailing junk by falling
 * back to the leading date digits. Returns `null` for unparseable input.
 */
internal fun parseIcalDateValue(value: String, timeZone: TimeZone): LocalDate? {
    val trimmed = value.trim()
    val date = parseIcalDateString(trimmed) ?: return null
    val utcTime = if (trimmed.endsWith("Z")) parseIcalUtcTimePart(trimmed) else null
    return if (utcTime == null) {
        date
    } else {
        LocalDateTime(date = date, time = utcTime)
            .toInstant(TimeZone.UTC)
            .toLocalDateTime(timeZone)
            .date
    }
}

/** Parses the `HHmmss` digits after the `T` of a UTC date-time value, or `null` if malformed. */
private fun parseIcalUtcTimePart(value: String): LocalTime? {
    val timePart = value.substringAfter('T', "").trimEnd('Z')
    if (timePart.length < ICAL_TIME_LENGTH) {
        return null
    }
    return runCatching {
        LocalTime(
            hour = timePart.substring(0, TWO_DIGIT_WIDTH).toInt(),
            minute = timePart.substring(TWO_DIGIT_WIDTH, MINUTE_END_IDX).toInt(),
            second = timePart.substring(MINUTE_END_IDX, ICAL_TIME_LENGTH).toInt(),
        )
    }.getOrNull()
}

/**
 * Parses an RFC 2445 DURATION value (`P#W`, `P#D`, `PT#H`, `PT#M`, `PT#S`, or combinations such
 * as `P1DT12H`) into total seconds. Returns `null` for anything else, including an empty
 * duration.
 */
internal fun parseRecurrenceDurationSeconds(value: String): Long? {
    val match = DURATION_REGEX.matchEntire(value.trim()) ?: return null
    val components = match.groupValues.drop(1)
    if (components.all { it.isEmpty() }) {
        return null
    }
    val unitSeconds = listOf(SECONDS_PER_WEEK, SECONDS_PER_DAY, SECONDS_PER_HOUR, SECONDS_PER_MINUTE, 1L)
    return components.zip(unitSeconds) { component, unit -> (component.toLongOrNull() ?: 0L) * unit }.sum()
}

private val DURATION_REGEX = Regex("""P(?:(\d+)W)?(?:(\d+)D)?(?:T(?:(\d+)H)?(?:(\d+)M)?(?:(\d+)S)?)?""")

/** Formats this date in the iCal basic form `yyyyMMdd`. */
internal fun LocalDate.toIcalDateString(): String =
    "${year.zeroPadded(YEAR_DIGIT_WIDTH)}${month.number.zeroPadded(TWO_DIGIT_WIDTH)}" +
        day.zeroPadded(TWO_DIGIT_WIDTH)

/** Formats this time in the iCal basic form `HHmmss`. */
internal fun LocalTime.toIcalTimeString(): String =
    "${hour.zeroPadded(TWO_DIGIT_WIDTH)}${minute.zeroPadded(TWO_DIGIT_WIDTH)}" +
        second.zeroPadded(TWO_DIGIT_WIDTH)

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
