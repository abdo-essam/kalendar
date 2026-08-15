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

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * Creates an Android [KalendarSyncProvider] backed by [CalendarContract].
 *
 * **Required permissions** — add to your `AndroidManifest.xml`:
 * ```xml
 * <uses-permission android:name="android.permission.READ_CALENDAR" />
 * <uses-permission android:name="android.permission.WRITE_CALENDAR" />
 * ```
 * Then request them at runtime with `ActivityCompat.requestPermissions` before
 * calling any sync function.
 *
 * @param context An application or activity context used to access the ContentResolver.
 */
public fun KalendarSync(context: Context): KalendarSyncProvider = AndroidKalendarSync(context)

private class AndroidKalendarSync(private val context: Context) : KalendarSyncProvider {

    private val tz get() = TimeZone.currentSystemDefault()

    /**
     * Returns `true` only when **both** `READ_CALENDAR` and `WRITE_CALENDAR` are already granted.
     *
     * This is a synchronous check that never shows a prompt — Android permissions must be
     * requested from an `Activity`, so ask with `ActivityCompat.requestPermissions` first. Every
     * other operation on this provider short-circuits to [KalendarSyncResult.PermissionDenied]
     * while this returns `false`.
     */
    override suspend fun hasPermission(): Boolean {
        val read = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR)
        val write = ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR)
        return read == PackageManager.PERMISSION_GRANTED && write == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Returns one-off events that **overlap** the window, plus every recurring series template
     * whose series starts on or before the window end — a recurring row's `DTSTART` is the series
     * start, so a window-only filter would hide any series created earlier. Callers materialise
     * the occurrences that actually fall inside the window by expanding returned templates with
     * [expandOccurrences].
     *
     * The one-off clause is the SQL form of [overlapsRange]: `DTSTART <= windowEnd` paired with
     * `DTEND > windowStart`. It queries [CalendarContract.Events] rather than
     * [CalendarContract.Instances] because this provider deliberately returns unexpanded series
     * templates; `Instances` would expand them, which is [expandOccurrences]'s job. `DTEND` is
     * exclusive in `CalendarContract` — an all-day event ending on the 31st stores the next
     * midnight — so the comparison is strict, and rows with no usable `DTEND` (null, or a
     * degenerate value at or before `DTSTART`) fall back to the start-in-window test, matching
     * [overlapsRange]'s `eventEnd` fallback. Recurring rows legitimately carry a null `DTEND` and
     * are covered by the second clause instead.
     */
    override suspend fun fetchEvents(
        startDate: LocalDate,
        endDate: LocalDate,
        timeZone: TimeZone,
    ): KalendarSyncResult<List<KalendarSyncEvent>> {
        if (!hasPermission()) return KalendarSyncResult.PermissionDenied
        return withContext(Dispatchers.IO) {
            runCatching {
                val startMillis = startDate.atStartOfDayIn(timeZone).toEpochMilliseconds()
                val endMillis = endDate.atStartOfDayIn(timeZone).toEpochMilliseconds() + DAY_MILLIS - 1

                val projection = arrayOf(
                    CalendarContract.Events._ID,
                    CalendarContract.Events.TITLE,
                    CalendarContract.Events.DESCRIPTION,
                    CalendarContract.Events.DTSTART,
                    CalendarContract.Events.DTEND,
                    CalendarContract.Events.ALL_DAY,
                    CalendarContract.Events.RRULE,
                    CalendarContract.Events.DURATION,
                    CalendarContract.Events.EXDATE,
                )
                val selection = overlapSelection()
                val selectionArgs = arrayOf(
                    endMillis.toString(),
                    startMillis.toString(),
                    startMillis.toString(),
                    endMillis.toString(),
                    endMillis.toString(),
                )

                val events = mutableListOf<KalendarSyncEvent>()
                context.contentResolver.query(
                    CalendarContract.Events.CONTENT_URI,
                    projection,
                    selection,
                    selectionArgs,
                    "${CalendarContract.Events.DTSTART} ASC",
                )?.use { cursor ->
                    while (cursor.moveToNext()) {
                        events += readEventRow(cursor = cursor, timeZone = timeZone)
                    }
                }
                KalendarSyncResult.Success(events)
            }.getOrElse { KalendarSyncResult.Error("Failed to fetch events", it) }
        }
    }

    /**
     * Builds the `fetchEvents` selection. Placeholders bind, in order: window end, window start,
     * window start, window end, window end.
     *
     * The first branch is a one-off event with a usable exclusive `DTEND` — it overlaps when it
     * starts at or before the window end and ends strictly after the window start. The second
     * covers rows whose `DTEND` is missing or degenerate, where the end collapses onto the start.
     * The third keeps every recurring template that could still produce an occurrence in the
     * window, since a series row's `DTSTART` is the series start rather than any occurrence.
     */
    private fun overlapSelection(): String {
        val dtStart = CalendarContract.Events.DTSTART
        val dtEnd = CalendarContract.Events.DTEND
        val overlapsWithEnd = "($dtEnd IS NOT NULL AND $dtEnd > $dtStart AND $dtStart <= ? AND $dtEnd > ?)"
        val startsInWindow = "(($dtEnd IS NULL OR $dtEnd <= $dtStart) AND $dtStart >= ? AND $dtStart <= ?)"
        val recurringTemplate = "(${CalendarContract.Events.RRULE} IS NOT NULL AND $dtStart <= ?)"
        return "($overlapsWithEnd OR $startsInWindow OR $recurringTemplate) " +
            "AND ${CalendarContract.Events.DELETED} != 1"
    }

    /** Maps the [cursor]'s current row to a [KalendarSyncEvent], interpreting instants in [timeZone]. */
    private fun readEventRow(cursor: Cursor, timeZone: TimeZone): KalendarSyncEvent {
        val allDay = cursor.getInt(cursor.getColumnIndex(CalendarContract.Events.ALL_DAY)) != 0
        val startMs = cursor.getLong(cursor.getColumnIndex(CalendarContract.Events.DTSTART))
        val endIdx = cursor.getColumnIndex(CalendarContract.Events.DTEND)
        val rule = cursor.getString(cursor.getColumnIndex(CalendarContract.Events.RRULE))
            ?.takeIf { it.isNotBlank() }
            ?.let { parseRRuleString(value = it, timeZone = timeZone) }
        val endMs = when {
            !cursor.isNull(endIdx) -> cursor.getLong(endIdx)
            rule != null -> durationEndMillis(
                startMs = startMs,
                durationValue = cursor.getString(cursor.getColumnIndex(CalendarContract.Events.DURATION)),
            )
            else -> null
        }
        val date = if (allDay) {
            allDayDateFromUtcMillis(startMs)
        } else {
            millisToLocalDate(startMs, timeZone)
        }
        val endDate = if (allDay) {
            endMs?.let {
                allDayInclusiveEndDate(
                    startDate = date,
                    endDateTime = Instant.fromEpochMilliseconds(it).toLocalDateTime(TimeZone.UTC),
                )
            }
        } else {
            endMs?.let { millisToLocalDate(it, timeZone) }?.takeIf { it > date }
        }
        val eventRowId = cursor.getLong(cursor.getColumnIndex(CalendarContract.Events._ID))
        return BasicKalendarSyncEvent(
            id = eventRowId.toString(),
            date = date,
            eventName = cursor.getString(cursor.getColumnIndex(CalendarContract.Events.TITLE)) ?: "",
            eventDescription = cursor.getString(cursor.getColumnIndex(CalendarContract.Events.DESCRIPTION))
                ?.takeIf { it.isNotBlank() },
            startTime = if (allDay) null else millisToLocalTime(startMs, timeZone),
            endTime = if (allDay || endMs == null) null else millisToLocalTime(endMs, timeZone),
            recurrenceRule = rule,
            endDate = endDate,
            exceptionDates = parseExdateColumn(
                raw = cursor.getString(cursor.getColumnIndex(CalendarContract.Events.EXDATE)),
                timeZone = timeZone,
            ),
            reminders = readReminders(eventRowId),
            attendees = readAttendees(eventRowId),
        )
    }

    override suspend fun listCalendars(): KalendarSyncResult<List<KalendarSyncCalendar>> {
        if (!hasPermission()) return KalendarSyncResult.PermissionDenied
        return withContext(Dispatchers.IO) {
            runCatching {
                val projection = arrayOf(
                    CalendarContract.Calendars._ID,
                    CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
                    CalendarContract.Calendars.CALENDAR_COLOR,
                )
                val selection = "${CalendarContract.Calendars.VISIBLE} = 1 AND " +
                    "${CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL} >= " +
                    "${CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR}"

                val calendars = mutableListOf<KalendarSyncCalendar>()
                context.contentResolver.query(
                    CalendarContract.Calendars.CONTENT_URI,
                    projection,
                    selection,
                    null,
                    "${CalendarContract.Calendars.CALENDAR_DISPLAY_NAME} ASC",
                )?.use { cursor ->
                    val idIdx = cursor.getColumnIndex(CalendarContract.Calendars._ID)
                    val nameIdx = cursor.getColumnIndex(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME)
                    val colorIdx = cursor.getColumnIndex(CalendarContract.Calendars.CALENDAR_COLOR)
                    while (cursor.moveToNext()) {
                        calendars += KalendarSyncCalendar(
                            id = cursor.getLong(idIdx).toString(),
                            name = cursor.getString(nameIdx) ?: "",
                            colorArgb = if (cursor.isNull(colorIdx)) null else cursor.getInt(colorIdx).toUInt().toLong(),
                        )
                    }
                }
                KalendarSyncResult.Success(calendars.toList())
            }.getOrElse { KalendarSyncResult.Error(message = "Failed to list calendars", cause = it) }
        }
    }

    override suspend fun insertEvent(event: KalendarSyncEvent, calendarId: String?): KalendarSyncResult<String> {
        if (!hasPermission()) return KalendarSyncResult.PermissionDenied
        return withContext(Dispatchers.IO) {
            runCatching {
                val targetCalendarId = resolveCalendarId(calendarId)
                    ?: return@withContext KalendarSyncResult.Error("No writable calendar found on device")

                val startMs = if (event.isAllDay) {
                    allDayStartUtcMillis(event.date)
                } else {
                    localDateTimeToMillis(event.date, event.resolvedStartTime())
                }
                val endMs = if (event.isAllDay) {
                    allDayEndExclusiveUtcMillis(startDate = event.date, endDateInclusive = event.endDate)
                } else {
                    localDateTimeToMillis(event.timedEndDate(), event.resolvedEndTime())
                }

                val values = ContentValues().apply {
                    put(CalendarContract.Events.CALENDAR_ID, targetCalendarId)
                    put(CalendarContract.Events.TITLE, event.eventName)
                    put(CalendarContract.Events.DESCRIPTION, event.eventDescription ?: "")
                    put(CalendarContract.Events.DTSTART, startMs)
                    put(CalendarContract.Events.ALL_DAY, if (event.isAllDay) 1 else 0)
                    put(CalendarContract.Events.EVENT_TIMEZONE, if (event.isAllDay) TimeZone.UTC.id else tz.id)
                    applyRecurrenceOrEnd(event = event, endMs = endMs, clearStaleFields = false)
                }

                val uri = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
                    ?: return@withContext KalendarSyncResult.Error("Insert returned a null URI")
                val newEventId = uri.lastPathSegment ?: ""
                newEventId.toLongOrNull()?.let { rowId ->
                    writeReminders(rowId, event.reminders)
                    writeAttendees(rowId, event.attendees)
                }
                KalendarSyncResult.Success(newEventId)
            }.getOrElse { KalendarSyncResult.Error("Failed to insert event", it) }
        }
    }

    override suspend fun updateEvent(eventId: String, event: KalendarSyncEvent): KalendarSyncResult<Unit> {
        if (!hasPermission()) return KalendarSyncResult.PermissionDenied
        return withContext(Dispatchers.IO) {
            runCatching {
                val startMs = if (event.isAllDay) {
                    allDayStartUtcMillis(event.date)
                } else {
                    localDateTimeToMillis(event.date, event.resolvedStartTime())
                }
                val endMs = if (event.isAllDay) {
                    allDayEndExclusiveUtcMillis(startDate = event.date, endDateInclusive = event.endDate)
                } else {
                    localDateTimeToMillis(event.timedEndDate(), event.resolvedEndTime())
                }
                val uri = ContentUris.withAppendedId(
                    CalendarContract.Events.CONTENT_URI,
                    eventId.toLong(),
                )
                val values = ContentValues().apply {
                    put(CalendarContract.Events.TITLE, event.eventName)
                    put(CalendarContract.Events.DESCRIPTION, event.eventDescription ?: "")
                    put(CalendarContract.Events.DTSTART, startMs)
                    put(CalendarContract.Events.ALL_DAY, if (event.isAllDay) 1 else 0)
                    put(CalendarContract.Events.EVENT_TIMEZONE, if (event.isAllDay) TimeZone.UTC.id else tz.id)
                    applyRecurrenceOrEnd(event = event, endMs = endMs, clearStaleFields = true)
                }
                val updated = context.contentResolver.update(uri, values, null, null)
                if (updated > 0) {
                    eventId.toLongOrNull()?.let { rowId ->
                        replaceReminders(rowId, event.reminders)
                        replaceAttendees(rowId, event.attendees)
                    }
                    KalendarSyncResult.Success(Unit)
                } else {
                    KalendarSyncResult.Error("Event with id=$eventId not found")
                }
            }.getOrElse { KalendarSyncResult.Error("Failed to update event", it) }
        }
    }

    override suspend fun deleteEvent(eventId: String): KalendarSyncResult<Unit> {
        if (!hasPermission()) return KalendarSyncResult.PermissionDenied
        return withContext(Dispatchers.IO) {
            runCatching {
                val uri = ContentUris.withAppendedId(
                    CalendarContract.Events.CONTENT_URI,
                    eventId.toLong(),
                )
                val deleted = context.contentResolver.delete(uri, null, null)
                if (deleted > 0) {
                    KalendarSyncResult.Success(Unit)
                } else {
                    KalendarSyncResult.Error("Event with id=$eventId not found")
                }
            }.getOrElse { KalendarSyncResult.Error("Failed to delete event", it) }
        }
    }

    /**
     * Writes the mutually exclusive recurrence/end fields into these [ContentValues].
     *
     * [CalendarContract] requires recurring events to carry an RFC 2445 `DURATION` and a
     * **null** `DTEND` — inserting a recurring event with `DTEND` set is rejected or dropped
     * by providers. So a non-null [KalendarSyncEvent.recurrenceRule] writes
     * `RRULE`/`DURATION` (plus `EXDATE` when the event has exception dates), while a null
     * rule writes `DTEND` as before. With [clearStaleFields] (used on update) the unused
     * side is explicitly nulled so switching an event between recurring and one-off doesn't
     * leave stale values behind.
     */
    private fun ContentValues.applyRecurrenceOrEnd(event: KalendarSyncEvent, endMs: Long, clearStaleFields: Boolean) {
        val rule = event.recurrenceRule
        if (rule != null) {
            val until = utcUntilValue(rule = rule, isAllDay = event.isAllDay, timeZone = tz)
            put(CalendarContract.Events.RRULE, rule.toRRuleString(untilValue = until))
            put(CalendarContract.Events.DURATION, recurrenceDurationString(event))
            val exdate = recurrenceExdateString(event = event, timeZone = tz)
            if (exdate != null) {
                put(CalendarContract.Events.EXDATE, exdate)
            } else if (clearStaleFields) {
                putNull(CalendarContract.Events.EXDATE)
            }
            if (clearStaleFields) {
                putNull(CalendarContract.Events.DTEND)
            }
        } else {
            put(CalendarContract.Events.DTEND, endMs)
            if (clearStaleFields) {
                putNull(CalendarContract.Events.RRULE)
                putNull(CalendarContract.Events.DURATION)
                putNull(CalendarContract.Events.EXDATE)
            }
        }
    }

    /**
     * Reconstructs the end instant of a recurring row from its RFC 2445 `DURATION` column
     * (recurring rows have a null `DTEND`). Returns `null` when the duration is absent or
     * unparseable.
     */
    private fun durationEndMillis(startMs: Long, durationValue: String?): Long? =
        durationValue
            ?.let { parseRecurrenceDurationSeconds(it) }
            ?.let { startMs + it * MILLIS_PER_SECOND }

    /**
     * Parses the comma-joined iCal values in an `EXDATE` column into [LocalDate]s, ignoring
     * unparseable entries. UTC date-time values (`yyyyMMdd'T'HHmmss'Z'` — the form written for
     * timed events) are converted back through [timeZone] so exclusions land on the correct
     * local day; plain date values map directly.
     */
    private fun parseExdateColumn(raw: String?, timeZone: TimeZone): List<LocalDate> =
        raw?.split(",")?.mapNotNull { parseIcalDateValue(value = it, timeZone = timeZone) }.orEmpty()

    /**
     * Resolves the calendar to insert into: the caller-supplied [calendarId] when given
     * (parsed to the numeric row ID [CalendarContract] uses), otherwise [defaultCalendarId].
     */
    private fun resolveCalendarId(calendarId: String?): Long? =
        if (calendarId != null) calendarId.toLongOrNull() else defaultCalendarId()

    /**
     * Reads the [CalendarContract.Reminders] rows for [eventId], skipping rows whose minutes
     * value is negative (the provider's "default reminder" sentinel).
     */
    private fun readReminders(eventId: Long): List<KalendarReminder> {
        val reminders = mutableListOf<KalendarReminder>()
        context.contentResolver.query(
            CalendarContract.Reminders.CONTENT_URI,
            arrayOf(CalendarContract.Reminders.MINUTES),
            "${CalendarContract.Reminders.EVENT_ID} = ?",
            arrayOf(eventId.toString()),
            null,
        )?.use { cursor ->
            val minutesIdx = cursor.getColumnIndex(CalendarContract.Reminders.MINUTES)
            while (cursor.moveToNext()) {
                val minutes = cursor.getInt(minutesIdx)
                if (minutes >= 0) {
                    reminders += KalendarReminder(minutesBefore = minutes)
                }
            }
        }
        return reminders
    }

    /** Reads the [CalendarContract.Attendees] rows for [eventId], skipping rows without an email. */
    private fun readAttendees(eventId: Long): List<KalendarAttendee> {
        val attendees = mutableListOf<KalendarAttendee>()
        context.contentResolver.query(
            CalendarContract.Attendees.CONTENT_URI,
            arrayOf(CalendarContract.Attendees.ATTENDEE_NAME, CalendarContract.Attendees.ATTENDEE_EMAIL),
            "${CalendarContract.Attendees.EVENT_ID} = ?",
            arrayOf(eventId.toString()),
            null,
        )?.use { cursor ->
            val nameIdx = cursor.getColumnIndex(CalendarContract.Attendees.ATTENDEE_NAME)
            val emailIdx = cursor.getColumnIndex(CalendarContract.Attendees.ATTENDEE_EMAIL)
            while (cursor.moveToNext()) {
                val email = cursor.getString(emailIdx)?.takeIf { it.isNotBlank() }
                if (email != null) {
                    attendees += KalendarAttendee(
                        email = email,
                        name = cursor.getString(nameIdx)?.takeIf { it.isNotBlank() },
                    )
                }
            }
        }
        return attendees
    }

    private fun writeReminders(eventId: Long, reminders: List<KalendarReminder>) {
        for (reminder in reminders) {
            val values = ContentValues().apply {
                put(CalendarContract.Reminders.EVENT_ID, eventId)
                put(CalendarContract.Reminders.MINUTES, reminder.minutesBefore)
                put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
            }
            context.contentResolver.insert(CalendarContract.Reminders.CONTENT_URI, values)
        }
    }

    private fun writeAttendees(eventId: Long, attendees: List<KalendarAttendee>) {
        for (attendee in attendees) {
            val values = ContentValues().apply {
                put(CalendarContract.Attendees.EVENT_ID, eventId)
                put(CalendarContract.Attendees.ATTENDEE_NAME, attendee.name ?: "")
                put(CalendarContract.Attendees.ATTENDEE_EMAIL, attendee.email)
                put(CalendarContract.Attendees.ATTENDEE_RELATIONSHIP, CalendarContract.Attendees.RELATIONSHIP_ATTENDEE)
                put(CalendarContract.Attendees.ATTENDEE_TYPE, CalendarContract.Attendees.TYPE_NONE)
                put(CalendarContract.Attendees.ATTENDEE_STATUS, CalendarContract.Attendees.ATTENDEE_STATUS_INVITED)
            }
            context.contentResolver.insert(CalendarContract.Attendees.CONTENT_URI, values)
        }
    }

    private fun replaceReminders(eventId: Long, reminders: List<KalendarReminder>) {
        context.contentResolver.delete(
            CalendarContract.Reminders.CONTENT_URI,
            "${CalendarContract.Reminders.EVENT_ID} = ?",
            arrayOf(eventId.toString()),
        )
        writeReminders(eventId, reminders)
    }

    private fun replaceAttendees(eventId: Long, attendees: List<KalendarAttendee>) {
        context.contentResolver.delete(
            CalendarContract.Attendees.CONTENT_URI,
            "${CalendarContract.Attendees.EVENT_ID} = ?",
            arrayOf(eventId.toString()),
        )
        writeAttendees(eventId, attendees)
    }

    /**
     * Queries [CalendarContract.Calendars] for the primary writable calendar ID,
     * falling back to any contributor-level calendar if no primary is flagged.
     * Returns `null` when no writable calendar exists on the device.
     */
    private fun defaultCalendarId(): Long? {
        val projection = arrayOf(CalendarContract.Calendars._ID, CalendarContract.Calendars.IS_PRIMARY)
        val selection = "${CalendarContract.Calendars.VISIBLE} = 1 AND " +
            "${CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL} >= ${CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR}"
        return context.contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            projection,
            selection,
            null,
            "${CalendarContract.Calendars.IS_PRIMARY} DESC",
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                cursor.getLong(cursor.getColumnIndex(CalendarContract.Calendars._ID))
            } else {
                null
            }
        }
    }

    private fun millisToLocalDate(millis: Long, timeZone: TimeZone = tz): LocalDate =
        Instant.fromEpochMilliseconds(millis).toLocalDateTime(timeZone).date

    private fun millisToLocalTime(millis: Long, timeZone: TimeZone = tz): LocalTime =
        Instant.fromEpochMilliseconds(millis).toLocalDateTime(timeZone).time

    private fun localDateTimeToMillis(date: LocalDate, time: LocalTime): Long =
        kotlinx.datetime.LocalDateTime(date, time).toInstant(tz).toEpochMilliseconds()

    private companion object {
        const val DAY_MILLIS = 24L * 60 * 60 * 1000
        const val MILLIS_PER_SECOND = 1000L
    }
}
