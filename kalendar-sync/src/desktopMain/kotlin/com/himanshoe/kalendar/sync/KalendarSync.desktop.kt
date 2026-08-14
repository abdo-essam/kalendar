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

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import java.io.File

/**
 * Creates a Desktop JVM [KalendarSyncProvider] backed by an `.ics` file at [icsFilePath].
 *
 * When [icsFilePath] is provided:
 * - [KalendarSyncProvider.hasPermission] returns `true` immediately (file-based, no OS permission).
 * - [KalendarSyncProvider.fetchEvents] reads and parses the file, returning every event that
 *   overlaps the requested range.
 * - [KalendarSyncProvider.insertEvent] appends a new event and rewrites the file.
 * - [KalendarSyncProvider.updateEvent] replaces the event matching [eventId] and rewrites the file.
 * - [KalendarSyncProvider.deleteEvent] removes the event matching [eventId] and rewrites the file.
 *
 * When [icsFilePath] is `null`, all operations return [KalendarSyncResult.NotSupported].
 * Use the top-level [exportToIcs] and [importFromIcs] functions for pure in-memory ICS
 * serialisation on all platforms.
 *
 * ```kotlin
 * val sync = KalendarSync(icsFilePath = "/Users/me/calendars/work.ics")
 * val result = sync.fetchEvents(startDate, endDate)
 * ```
 *
 * @param icsFilePath Absolute path to the `.ics` file used for storage. Pass `null` to get a
 *   no-op stub that returns [KalendarSyncResult.NotSupported] for every operation.
 */
public fun KalendarSync(icsFilePath: String? = null): KalendarSyncProvider =
    DesktopKalendarSync(icsFilePath)

private class DesktopKalendarSync(private val icsFilePath: String?) : KalendarSyncProvider {

    override suspend fun hasPermission(): Boolean = icsFilePath != null

    /**
     * Returns a single calendar representing the backing `.ics` file (id `"default"`, named
     * after the file), or [KalendarSyncResult.NotSupported] when no `icsFilePath` was given.
     */
    override suspend fun listCalendars(): KalendarSyncResult<List<KalendarSyncCalendar>> {
        val path = icsFilePath ?: return KalendarSyncResult.NotSupported
        return KalendarSyncResult.Success(
            listOf(KalendarSyncCalendar(id = "default", name = File(path).name)),
        )
    }

    /**
     * Returns every event in the file that **overlaps** [[startDate], [endDate]], including
     * multi-day events that begin before the window or end after it — see [overlapsRange].
     *
     * Recurring events are also returned whenever the series *starts* on or before [endDate],
     * even if the stored template itself falls outside the window: the template is the only row
     * that exists, so dropping it would hide every occurrence in the window. This matches the
     * Android provider, which keeps such rows with `RRULE IS NOT NULL AND DTSTART <= windowEnd`.
     * As on Android, templates come back unexpanded — call
     * [expandOccurrences][com.himanshoe.kalendar.sync.expandOccurrences] to materialise them.
     */
    override suspend fun fetchEvents(
        startDate: LocalDate,
        endDate: LocalDate,
        timeZone: TimeZone,
    ): KalendarSyncResult<List<KalendarSyncEvent>> {
        val path = icsFilePath ?: return KalendarSyncResult.NotSupported
        return withContext(Dispatchers.IO) {
            runCatching {
                val events = readAllEvents(path).filter { event ->
                    val overlaps = overlapsRange(
                        eventStart = event.date,
                        eventEndInclusive = event.endDate,
                        rangeStart = startDate,
                        rangeEnd = endDate,
                    )
                    overlaps || (event.recurrenceRule != null && event.date <= endDate)
                }
                KalendarSyncResult.Success(events)
            }.getOrElse { KalendarSyncResult.Error("Failed to read ICS file: ${it.message}", it) }
        }
    }

    /**
     * The single calendar backing this provider — the `.ics` file itself. [calendarId] is
     * accepted for interface compatibility and ignored, since a file store has exactly one
     * calendar.
     */
    override suspend fun insertEvent(event: KalendarSyncEvent, calendarId: String?): KalendarSyncResult<String> {
        val path = icsFilePath ?: return KalendarSyncResult.NotSupported
        return withContext(Dispatchers.IO) {
            runCatching {
                val uid = "${event.date}-${event.eventName.hashCode().toUInt()}-${System.currentTimeMillis()}"
                val updated = readAllEvents(path) + event.copyAsBasic(id = uid)
                File(path).writeText(exportToIcs(updated))
                KalendarSyncResult.Success(uid)
            }.getOrElse { KalendarSyncResult.Error("Failed to write ICS file: ${it.message}", it) }
        }
    }

    override suspend fun updateEvent(
        eventId: String,
        event: KalendarSyncEvent,
    ): KalendarSyncResult<Unit> {
        val path = icsFilePath ?: return KalendarSyncResult.NotSupported
        return withContext(Dispatchers.IO) {
            runCatching {
                val replaced = event.copyAsBasic(id = eventId)
                val updated = readAllEvents(path).map { if (it.id == eventId) replaced else it }
                File(path).writeText(exportToIcs(updated))
                KalendarSyncResult.Success(Unit)
            }.getOrElse { KalendarSyncResult.Error("Failed to update ICS file: ${it.message}", it) }
        }
    }

    override suspend fun deleteEvent(eventId: String): KalendarSyncResult<Unit> {
        val path = icsFilePath ?: return KalendarSyncResult.NotSupported
        return withContext(Dispatchers.IO) {
            runCatching {
                val updated = readAllEvents(path).filter { it.id != eventId }
                File(path).writeText(exportToIcs(updated))
                KalendarSyncResult.Success(Unit)
            }.getOrElse { KalendarSyncResult.Error("Failed to delete from ICS file: ${it.message}", it) }
        }
    }

    /** Reads and parses the whole `.ics` file at [path], or returns empty if it does not exist yet. */
    private fun readAllEvents(path: String): List<KalendarSyncEvent> {
        val file = File(path)
        return if (file.exists()) importFromIcs(file.readText()) else emptyList()
    }
}
