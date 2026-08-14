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

private const val YEAR_END_IDX = 4
private const val MONTH_END_IDX = 6
private const val SECONDS_PER_MINUTE = 60L

/**
 * Pure Kotlin iCalendar (RFC 5545) encoder and decoder.
 * No platform APIs — works on Android, iOS, Desktop, and wasmJS.
 *
 * Timed events are written and read as **floating local times** (RFC 5545 section 3.3.5 form 1:
 * no `Z` suffix, no `TZID`): every consumer interprets them in its own local zone. `TZID`
 * parameters are not supported and are ignored on decode; for backwards compatibility with
 * files this library previously wrote, a trailing `Z` on `DTSTART`/`DTEND`/`EXDATE` values is
 * also accepted and treated as floating.
 */
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
        for (attendee in event.attendees) {
            val cnParam = attendee.name?.let { ";CN=${icalEscape(it)}" } ?: ""
            appendLine("ATTENDEE$cnParam:mailto:${attendee.email}")
        }

        if (event.isAllDay) {
            appendLine("DTSTART;VALUE=DATE:${event.date.toIcalDateString()}")
            val endExclusive = (event.endDate ?: event.date).plusDays(1)
            appendLine("DTEND;VALUE=DATE:${endExclusive.toIcalDateString()}")
        } else {
            appendLine("DTSTART:${LocalDateTime(event.date, event.resolvedStartTime()).toIcalDateTime()}")
            appendLine("DTEND:${LocalDateTime(event.timedEndDate(), event.resolvedEndTime()).toIcalDateTime()}")
        }
        event.recurrenceRule?.let { appendLine("RRULE:${it.toRRuleString()}") }
        if (event.exceptionDates.isNotEmpty()) {
            appendLine(exdateLine(event))
        }
        for (reminder in event.reminders) {
            appendLine("BEGIN:VALARM")
            appendLine("ACTION:DISPLAY")
            appendLine("DESCRIPTION:Reminder")
            appendLine("TRIGGER:-PT${reminder.minutesBefore}M")
            appendLine("END:VALARM")
        }
        appendLine("END:VEVENT")
    }

    /**
     * Builds the EXDATE property with a value type matching DTSTART's, as RFC 5545 requires:
     * `VALUE=DATE` dates for all-day events, and floating DATE-TIME values at the event's start
     * time for timed events (strict consumers match exclusions against the occurrence's
     * DATE-TIME start and ignore mismatched value types).
     */
    private fun exdateLine(event: KalendarSyncEvent): String {
        if (event.isAllDay) {
            return "EXDATE;VALUE=DATE:${event.exceptionDates.joinToString(",") { it.toIcalDateString() }}"
        }
        val startTime = event.resolvedStartTime()
        val values = event.exceptionDates.joinToString(",") { exceptionDate ->
            LocalDateTime(date = exceptionDate, time = startTime).toIcalDateTime()
        }
        return "EXDATE:$values"
    }

    /**
     * Parses an iCalendar (.ics) string and returns the contained events as
     * [BasicKalendarSyncEvent] instances.
     */
    fun decode(icsContent: String): List<KalendarSyncEvent> {
        val events = mutableListOf<KalendarSyncEvent>()
        val lines = unfoldLines(icsContent)
        var inEvent = false
        var inAlarm = false
        var props = VEventProps()
        for (line in lines) {
            when {
                line == "BEGIN:VEVENT" -> {
                    inEvent = true
                    inAlarm = false
                    props = VEventProps()
                }
                line == "BEGIN:VALARM" && inEvent -> inAlarm = true
                line == "END:VALARM" && inAlarm -> inAlarm = false
                inAlarm -> parseAlarmTriggerMinutes(line)?.let {
                    props = props.copy(reminderMinutes = props.reminderMinutes + it)
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
        val reminderMinutes: List<Int> = emptyList(),
        val attendees: List<KalendarAttendee> = emptyList(),
    ) {
        fun updated(key: String, params: Set<String>, value: String): VEventProps = when (key) {
            "UID" -> copy(uid = value)
            "SUMMARY" -> copy(summary = value)
            "DESCRIPTION" -> copy(description = value)
            "DTSTART" -> copy(dtStart = value, isAllDay = params.contains("VALUE=DATE"))
            "DTEND" -> copy(dtEnd = value)
            "RRULE" -> copy(rruleRaw = value)
            "EXDATE" -> copy(exDatesRaw = exDatesRaw + value)
            "ATTENDEE" -> copy(attendees = attendees + parseAttendee(params, value))
            else -> this
        }
    }

    private fun buildEventOrNull(props: VEventProps): KalendarSyncEvent? {
        val startRaw = props.dtStart ?: return null
        val date = parseIcalDateString(startRaw) ?: return null
        val (startTime, endTime, endDate) = if (props.isAllDay) {
            val endExclusive = props.dtEnd?.let { parseIcalDateString(it) }
            val inclusiveEnd = endExclusive?.minusDays(1)?.takeIf { it > date }
            Triple(null, null, inclusiveEnd)
        } else {
            Triple(
                parseIcalTime(startRaw),
                props.dtEnd?.let { parseIcalTime(it) },
                props.dtEnd?.let { parseIcalDateString(it) }?.takeIf { it > date },
            )
        }
        val exceptionDates = props.exDatesRaw.flatMap { raw ->
            raw.split(",").mapNotNull { parseIcalDateString(it) }
        }
        return BasicKalendarSyncEvent(
            id = props.uid,
            date = date,
            eventName = icalUnescape(props.summary ?: ""),
            eventDescription = props.description?.let { icalUnescape(it) },
            startTime = startTime,
            endTime = endTime,
            recurrenceRule = props.rruleRaw?.let { parseRRuleString(it) },
            endDate = endDate,
            exceptionDates = exceptionDates,
            reminders = props.reminderMinutes.map { KalendarReminder(minutesBefore = it) },
            attendees = props.attendees,
        )
    }

    /**
     * Parses a `TRIGGER` line inside a `VALARM` block into minutes-before-start, handling any
     * negative RFC 2445 duration — week, day, hour, minute, and second components in any
     * combination (`-PT30M`, `-P1D`, `-P1DT2H30M`, `-PT30M0S`, ...). Returns `null` for any
     * other line in the alarm block, and for triggers that are absolute, positive (after the
     * event), or otherwise unparseable.
     */
    private fun parseAlarmTriggerMinutes(line: String): Int? {
        val parsed = parseLine(line)
        if (parsed == null || parsed.first != "TRIGGER") {
            return null
        }
        val raw = parsed.third.trim()
        if (!raw.startsWith("-")) {
            return null
        }
        val seconds = parseRecurrenceDurationSeconds(raw.removePrefix("-")) ?: return null
        return (seconds / SECONDS_PER_MINUTE).toInt()
    }

    /**
     * Parses an `ATTENDEE` property into a [KalendarAttendee]: the optional `CN=` parameter
     * carries the display name, and the value is the email address with a case-insensitive
     * `mailto:` prefix stripped.
     */
    private fun parseAttendee(params: Set<String>, value: String): KalendarAttendee {
        val name = params.firstOrNull { it.startsWith("CN=") }
            ?.removePrefix("CN=")
            ?.let { icalUnescape(it) }
        return KalendarAttendee(email = value.removeMailtoPrefix(), name = name)
    }

    private fun String.removeMailtoPrefix(): String =
        if (startsWith(MAILTO_PREFIX, ignoreCase = true)) substring(MAILTO_PREFIX.length) else this

    private const val MAILTO_PREFIX = "mailto:"

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

    /**
     * Formats a floating local DATE-TIME (`yyyyMMdd'T'HHmmss`, no `Z`): the value is wall-clock
     * time interpreted in each consumer's own zone, per RFC 5545 section 3.3.5 form 1.
     */
    private fun LocalDateTime.toIcalDateTime(): String =
        "${date.toIcalDateString()}T${time.toIcalTimeString()}"

    /**
     * Parses the time portion of a DATE-TIME value as floating local time. A trailing `Z` is
     * stripped and ignored — files this library used to write carried a spurious `Z` on
     * wall-clock values, so both forms decode identically.
     */
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
}
