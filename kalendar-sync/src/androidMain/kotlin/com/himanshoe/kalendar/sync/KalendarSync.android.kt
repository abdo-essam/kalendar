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
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlinx.datetime.plus
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

private const val END_OF_DAY_HOUR = 23
private const val END_OF_DAY_MINUTE = 59

private class AndroidKalendarSync(private val context: Context) : KalendarSyncProvider {

    private val tz get() = TimeZone.currentSystemDefault()

    override suspend fun hasPermission(): Boolean {
        val read = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR)
        val write = ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR)
        return read == PackageManager.PERMISSION_GRANTED && write == PackageManager.PERMISSION_GRANTED
    }

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
                )
                val selection =
                    "${CalendarContract.Events.DTSTART} >= ? AND ${CalendarContract.Events.DTSTART} <= ? " +
                        "AND ${CalendarContract.Events.DELETED} != 1"
                val selectionArgs = arrayOf(startMillis.toString(), endMillis.toString())

                val events = mutableListOf<KalendarSyncEvent>()
                context.contentResolver.query(
                    CalendarContract.Events.CONTENT_URI,
                    projection,
                    selection,
                    selectionArgs,
                    "${CalendarContract.Events.DTSTART} ASC",
                )?.use { cursor ->
                    val idIdx = cursor.getColumnIndex(CalendarContract.Events._ID)
                    val titleIdx = cursor.getColumnIndex(CalendarContract.Events.TITLE)
                    val descIdx = cursor.getColumnIndex(CalendarContract.Events.DESCRIPTION)
                    val startIdx = cursor.getColumnIndex(CalendarContract.Events.DTSTART)
                    val endIdx = cursor.getColumnIndex(CalendarContract.Events.DTEND)
                    val allDayIdx = cursor.getColumnIndex(CalendarContract.Events.ALL_DAY)

                    while (cursor.moveToNext()) {
                        val allDay = cursor.getInt(allDayIdx) != 0
                        val startMs = cursor.getLong(startIdx)
                        val endMs = cursor.getLong(endIdx)
                        val date = millisToLocalDate(startMs, timeZone)
                        val endDate = millisToLocalDate(endMs, timeZone)
                            .minus(1, DateTimeUnit.DAY)
                            .takeIf { allDay && it > date }
                        val eventRowId = cursor.getLong(idIdx)
                        events += BasicKalendarSyncEvent(
                            id = eventRowId.toString(),
                            date = date,
                            eventName = cursor.getString(titleIdx) ?: "",
                            eventDescription = cursor.getString(descIdx)?.takeIf { it.isNotBlank() },
                            startTime = if (allDay) null else millisToLocalTime(startMs, timeZone),
                            endTime = if (allDay) null else millisToLocalTime(endMs, timeZone),
                            endDate = endDate,
                            reminders = readReminders(eventRowId),
                            attendees = readAttendees(eventRowId),
                        )
                    }
                }
                KalendarSyncResult.Success(events)
            }.getOrElse { KalendarSyncResult.Error("Failed to fetch events", it) }
        }
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
                    event.date.atStartOfDayIn(tz).toEpochMilliseconds()
                } else {
                    localDateTimeToMillis(event.date, event.startTime ?: LocalTime(0, 0))
                }
                val endMs = if (event.isAllDay) {
                    (event.endDate ?: event.date).plus(1, DateTimeUnit.DAY).atStartOfDayIn(tz).toEpochMilliseconds()
                } else {
                    localDateTimeToMillis(event.date, event.endTime ?: LocalTime(END_OF_DAY_HOUR, END_OF_DAY_MINUTE))
                }

                val values = ContentValues().apply {
                    put(CalendarContract.Events.CALENDAR_ID, targetCalendarId)
                    put(CalendarContract.Events.TITLE, event.eventName)
                    put(CalendarContract.Events.DESCRIPTION, event.eventDescription ?: "")
                    put(CalendarContract.Events.DTSTART, startMs)
                    put(CalendarContract.Events.DTEND, endMs)
                    put(CalendarContract.Events.ALL_DAY, if (event.isAllDay) 1 else 0)
                    put(CalendarContract.Events.EVENT_TIMEZONE, tz.id)
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
                    event.date.atStartOfDayIn(tz).toEpochMilliseconds()
                } else {
                    localDateTimeToMillis(event.date, event.startTime ?: LocalTime(0, 0))
                }
                val endMs = if (event.isAllDay) {
                    (event.endDate ?: event.date).plus(1, DateTimeUnit.DAY).atStartOfDayIn(tz).toEpochMilliseconds()
                } else {
                    localDateTimeToMillis(event.date, event.endTime ?: LocalTime(END_OF_DAY_HOUR, END_OF_DAY_MINUTE))
                }
                val uri = ContentUris.withAppendedId(
                    CalendarContract.Events.CONTENT_URI,
                    eventId.toLong(),
                )
                val values = ContentValues().apply {
                    put(CalendarContract.Events.TITLE, event.eventName)
                    put(CalendarContract.Events.DESCRIPTION, event.eventDescription ?: "")
                    put(CalendarContract.Events.DTSTART, startMs)
                    put(CalendarContract.Events.DTEND, endMs)
                    put(CalendarContract.Events.ALL_DAY, if (event.isAllDay) 1 else 0)
                    put(CalendarContract.Events.EVENT_TIMEZONE, tz.id)
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
    }
}
