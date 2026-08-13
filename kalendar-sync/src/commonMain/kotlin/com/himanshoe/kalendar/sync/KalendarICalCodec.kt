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
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/**
 * Pure Kotlin iCalendar (RFC 5545) encoder and decoder.
 * No platform APIs — works on Android, iOS, Desktop, and wasmJS.
 */
private const val END_OF_DAY_HOUR = 23
private const val END_OF_DAY_MINUTE = 59
private const val ICS_DATE_LENGTH = 8
private const val YEAR_END_IDX = 4
private const val MONTH_END_IDX = 6
private const val YEAR_DIGIT_WIDTH = 4
private const val TWO_DIGIT_WIDTH = 2

internal object KalendarICalCodec {

    /**
     * Encodes a list of [KalendarSyncEvent] into a complete iCalendar (.ics) string.
     */
    fun encode(events: List<KalendarSyncEvent>): String = buildString {
        appendLine("BEGIN:VCALENDAR")
        appendLine("VERSION:2.0")
        appendLine("PRODID:-//Kalendar//KalendarSync//EN")
        appendLine("CALSCALE:GREGORIAN")
        appendLine("METHOD:PUBLISH")
        for (event in events) {
            encodeEvent(event)
        }
        append("END:VCALENDAR")
    }

    private fun StringBuilder.encodeEvent(event: KalendarSyncEvent) {
        appendLine("BEGIN:VEVENT")
        appendLine("UID:${event.id ?: generateUid(event)}")
        appendLine("SUMMARY:${icalEscape(event.eventName)}")
        event.eventDescription?.let { appendLine("DESCRIPTION:${icalEscape(it)}") }

        if (event.isAllDay) {
            appendLine("DTSTART;VALUE=DATE:${event.date.toIcalDate()}")
            val endExclusive = (event.endDate ?: event.date).plusDays(1)
            appendLine("DTEND;VALUE=DATE:${endExclusive.toIcalDate()}")
        } else {
            val startTime = event.startTime ?: LocalTime(0, 0)
            val endTime = event.endTime ?: LocalTime(END_OF_DAY_HOUR, END_OF_DAY_MINUTE)
            appendLine("DTSTART:${LocalDateTime(event.date, startTime).toIcalDateTime()}")
            appendLine("DTEND:${LocalDateTime(event.date, endTime).toIcalDateTime()}")
        }
        event.recurrenceRule?.let { appendLine("RRULE:${it.toIcal()}") }
        if (event.exceptionDates.isNotEmpty()) {
            appendLine("EXDATE;VALUE=DATE:${event.exceptionDates.joinToString(",") { it.toIcalDate() }}")
        }
        appendLine("END:VEVENT")
    }

    /**
     * Parses an iCalendar (.ics) string and returns the contained events as
     * [BasicKalendarSyncEvent] instances.
     */
    fun decode(icsContent: String): List<KalendarSyncEvent> {
        val events = mutableListOf<KalendarSyncEvent>()
        val lines = unfoldLines(icsContent)
        var inEvent = false
        var props = VEventProps()
        for (line in lines) {
            when {
                line == "BEGIN:VEVENT" -> {
                    inEvent = true
                    props = VEventProps()
                }
                line == "END:VEVENT" && inEvent -> {
                    inEvent = false
                    buildEventOrNull(props)?.let { events += it }
                }
                inEvent -> parseLine(line)?.let { (key, params, value) ->
                    props = props.updated(key, params, value)
                }
            }
        }
        return events
    }

    private data class VEventProps(
        val uid: String? = null,
        val summary: String? = null,
        val description: String? = null,
        val dtStart: String? = null,
        val dtEnd: String? = null,
        val isAllDay: Boolean = false,
        val rruleRaw: String? = null,
        val exDatesRaw: List<String> = emptyList(),
    ) {
        fun updated(key: String, params: Set<String>, value: String): VEventProps = when (key) {
            "UID" -> copy(uid = value)
            "SUMMARY" -> copy(summary = value)
            "DESCRIPTION" -> copy(description = value)
            "DTSTART" -> copy(dtStart = value, isAllDay = params.contains("VALUE=DATE"))
            "DTEND" -> copy(dtEnd = value)
            "RRULE" -> copy(rruleRaw = value)
            "EXDATE" -> copy(exDatesRaw = exDatesRaw + value)
            else -> this
        }
    }

    private fun buildEventOrNull(props: VEventProps): KalendarSyncEvent? {
        val startRaw = props.dtStart ?: return null
        val date = parseIcalDate(startRaw) ?: return null
        val (startTime, endTime, endDate) = if (props.isAllDay) {
            val endExclusive = props.dtEnd?.let { parseIcalDate(it) }
            val inclusiveEnd = endExclusive?.minusDays(1)?.takeIf { it > date }
            Triple(null, null, inclusiveEnd)
        } else {
            Triple(parseIcalTime(startRaw), props.dtEnd?.let { parseIcalTime(it) }, null)
        }
        val exceptionDates = props.exDatesRaw.flatMap { raw ->
            raw.split(",").mapNotNull { parseIcalDate(it) }
        }
        return BasicKalendarSyncEvent(
            id = props.uid,
            date = date,
            eventName = icalUnescape(props.summary ?: ""),
            eventDescription = props.description?.let { icalUnescape(it) },
            startTime = startTime,
            endTime = endTime,
            recurrenceRule = props.rruleRaw?.let { parseRRule(it) },
            endDate = endDate,
            exceptionDates = exceptionDates,
        )
    }

    /**
     * Unfolds RFC 5545 line continuations (CRLF or LF followed by whitespace)
     * into single logical lines, then strips blank lines.
     */
    private fun unfoldLines(raw: String): List<String> {
        return raw.replace("\r\n ", "").replace("\r\n\t", "")
            .replace("\n ", "").replace("\n\t", "")
            .lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }

    /**
     * Parses a single iCalendar property line in `KEY;PARAM=VAL:value` form into a
     * `(key, params, value)` triple. Returns `null` if the line has no colon separator.
     */
    private fun parseLine(line: String): Triple<String, Set<String>, String>? {
        val colonIdx = line.indexOf(':')
        if (colonIdx < 0) return null
        val keyPart = line.substring(0, colonIdx)
        val value = line.substring(colonIdx + 1)
        val parts = keyPart.split(';')
        val key = parts.first()
        val params = parts.drop(1).toSet()
        return Triple(key, params, value)
    }

    private fun LocalDate.toIcalDate(): String =
        "${year.zeroPadded(YEAR_DIGIT_WIDTH)}${monthNumber.zeroPadded(TWO_DIGIT_WIDTH)}" +
            dayOfMonth.zeroPadded(TWO_DIGIT_WIDTH)

    private fun LocalDateTime.toIcalDateTime(): String =
        "${date.toIcalDate()}T${hour.zeroPadded(TWO_DIGIT_WIDTH)}" +
            "${minute.zeroPadded(TWO_DIGIT_WIDTH)}${second.zeroPadded(TWO_DIGIT_WIDTH)}Z"

    private fun Int.zeroPadded(width: Int): String = toString().padStart(width, '0')

    private fun parseIcalDate(value: String): LocalDate? = runCatching {
        val v = value.substringBefore('T').filter { it.isDigit() }
        if (v.length < ICS_DATE_LENGTH) return null
        LocalDate(
            v.substring(0, YEAR_END_IDX).toInt(),
            v.substring(YEAR_END_IDX, MONTH_END_IDX).toInt(),
            v.substring(MONTH_END_IDX, ICS_DATE_LENGTH).toInt(),
        )
    }.getOrNull()

    private fun parseIcalTime(value: String): LocalTime? = runCatching {
        val timePart = value.substringAfter('T', "").trimEnd('Z')
        if (timePart.length < MONTH_END_IDX) return null
        LocalTime(
            timePart.substring(0, 2).toInt(),
            timePart.substring(2, YEAR_END_IDX).toInt(),
            timePart.substring(YEAR_END_IDX, MONTH_END_IDX).toInt(),
        )
    }.getOrNull()

    private fun LocalDate.plusDays(days: Int): LocalDate = this.plus(days, DateTimeUnit.DAY)

    private fun LocalDate.minusDays(days: Int): LocalDate = this.minus(days, DateTimeUnit.DAY)

    private fun icalEscape(text: String): String =
        text.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,").replace("\n", "\\n")

    private fun icalUnescape(text: String): String =
        text.replace("\\n", "\n").replace("\\,", ",").replace("\\;", ";").replace("\\\\", "\\")

    private fun generateUid(event: KalendarSyncEvent): String =
        "${event.date}-${event.eventName.hashCode().toUInt()}-kalendar@himanshoe.com"

    /**
     * Serialises a [KalendarRule] to the value portion of an RFC 5545 RRULE property,
     * e.g. `FREQ=WEEKLY;INTERVAL=2;BYDAY=MO,WE,FR`.
     */
    private fun KalendarRule.toIcal(): String = buildString {
        append("FREQ=${frequency.name}")
        if (interval != 1) append(";INTERVAL=$interval")
        count?.let { append(";COUNT=$it") }
        until?.let { append(";UNTIL=${it.toIcalDate()}") }
        if (byDay.isNotEmpty()) append(";BYDAY=${byDay.joinToString(",") { it.toIcalString() }}")
        if (byMonthDay.isNotEmpty()) append(";BYMONTHDAY=${byMonthDay.joinToString(",")}")
        if (byMonth.isNotEmpty()) append(";BYMONTH=${byMonth.joinToString(",")}")
    }

    /**
     * Parses the value portion of an RFC 5545 RRULE property into a [KalendarRule].
     * Returns `null` if the FREQ component is missing or unrecognised.
     */
    private fun parseRRule(value: String): KalendarRule? = runCatching {
        val parts = value.split(";").mapNotNull { segment ->
            val eq = segment.indexOf('=')
            if (eq < 0) null else segment.substring(0, eq) to segment.substring(eq + 1)
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
            until = parts["UNTIL"]?.let { parseIcalDate(it) },
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

    private fun KalendarWeekDay.toIcalString(): String = when (this) {
        KalendarWeekDay.MONDAY -> "MO"
        KalendarWeekDay.TUESDAY -> "TU"
        KalendarWeekDay.WEDNESDAY -> "WE"
        KalendarWeekDay.THURSDAY -> "TH"
        KalendarWeekDay.FRIDAY -> "FR"
        KalendarWeekDay.SATURDAY -> "SA"
        KalendarWeekDay.SUNDAY -> "SU"
    }

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
}
