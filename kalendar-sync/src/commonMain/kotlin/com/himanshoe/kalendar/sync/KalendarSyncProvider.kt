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
import kotlinx.datetime.TimeZone

/**
 * Reads, writes, and exports events from the device's built-in calendar without any
 * third-party API or cloud account.
 *
 * - **Android** — backed by [CalendarContract][android.provider.CalendarContract].
 *   Requires `READ_CALENDAR` and `WRITE_CALENDAR` permissions in your manifest.
 * - **iOS** — backed by [EventKit](https://developer.apple.com/documentation/eventkit).
 *   Requires `NSCalendarUsageDescription` in your `Info.plist`.
 * - **Desktop / Web** — [fetchEvents], [insertEvent], [deleteEvent] return
 *   [KalendarSyncResult.NotSupported]. Use the top-level [exportToIcs] and [importFromIcs]
 *   functions from `KalendarIcs.kt` for ICS serialization on all platforms.
 *
 * Obtain a platform instance via the `KalendarSync` factory function:
 * ```kotlin
 * // Android (inside Activity / ViewModel)
 * val sync = KalendarSync(context)
 *
 * // iOS / Desktop / Web
 * val sync = KalendarSync()
 * ```
 */
public interface KalendarSyncProvider {

    /**
     * Checks whether the app currently holds the necessary calendar permission. Every other
     * operation on this provider begins with the same check and returns
     * [KalendarSyncResult.PermissionDenied] when it fails, so calling this first is optional.
     *
     * - **Android** — a synchronous check for `READ_CALENDAR` and `WRITE_CALENDAR` that never
     *   prompts; request them from an `Activity` with `ActivityCompat.requestPermissions`.
     * - **iOS** — requests full calendar access, so the system dialog appears the first time this
     *   is called on a device that has not yet been asked.
     * - **Desktop** — `true` when the provider was built with an `icsFilePath`, since a file store
     *   needs no OS permission.
     * - **Web** — always `false`; browsers have no device-calendar API.
     *
     * @return `true` if permission is held, `false` otherwise.
     */
    public suspend fun hasPermission(): Boolean

    /**
     * Returns all events from the device calendar that **overlap** [[startDate], [endDate]]
     * (both inclusive).
     *
     * Overlap, not containment: a multi-day event is returned by every window it touches, so an
     * event running from August 28th to September 5th comes back from a query for August *and*
     * from a query for September, and from a query for any window inside it. Consumers paging
     * month by month therefore see such an event on every page it spans, rather than only on the
     * page containing its first day. Returned events keep their real
     * [KalendarSyncEvent.date]/[KalendarSyncEvent.endDate] — they are not clipped to the window.
     *
     * Recurring series are returned as unexpanded templates carrying a
     * [KalendarSyncEvent.recurrenceRule]; materialise their occurrences with [expandOccurrences].
     *
     * @param timeZone The time zone used to interpret [startDate] and [endDate] when converting
     *   to native calendar query parameters. Defaults to the device's current system time zone.
     */
    public suspend fun fetchEvents(
        startDate: LocalDate,
        endDate: LocalDate,
        timeZone: TimeZone = TimeZone.currentSystemDefault(),
    ): KalendarSyncResult<List<KalendarSyncEvent>>

    /**
     * Lists the calendars on this device that events can be read from or written into.
     *
     * - **Android** — visible calendars with contributor access or higher, including their
     *   display colour.
     * - **iOS** — all event calendars; [KalendarSyncCalendar.colorArgb] is always `null`
     *   because EventKit exposes calendar colours only as `CGColor`.
     * - **Desktop** — a single calendar representing the backing `.ics` file when
     *   `icsFilePath` is set, [KalendarSyncResult.NotSupported] otherwise.
     * - **Web** — [KalendarSyncResult.NotSupported].
     *
     * @return [KalendarSyncResult.Success] carrying the available calendars.
     */
    public suspend fun listCalendars(): KalendarSyncResult<List<KalendarSyncCalendar>>

    /**
     * Inserts [event] into a device calendar.
     *
     * @param calendarId Identifier of the target calendar, as returned in
     *   [KalendarSyncCalendar.id] by [listCalendars]. Pass `null` (the default) to insert
     *   into the platform's default calendar.
     * @return [KalendarSyncResult.Success] carrying the new event's device-assigned ID.
     */
    public suspend fun insertEvent(
        event: KalendarSyncEvent,
        calendarId: String? = null,
    ): KalendarSyncResult<String>

    /**
     * Updates the device-calendar event identified by [eventId] with the fields from [event].
     *
     * [eventId] must be the value returned by [insertEvent] or read from
     * [KalendarSyncEvent.id] via [fetchEvents]. The event's own [KalendarSyncEvent.id] field
     * is ignored — only [eventId] is used to locate the record.
     *
     * @return [KalendarSyncResult.Success] with [Unit] on success.
     */
    public suspend fun updateEvent(eventId: String, event: KalendarSyncEvent): KalendarSyncResult<Unit>

    /**
     * Deletes the event identified by [eventId] from the device calendar.
     *
     * [eventId] must be the value returned by [insertEvent] or read from
     * [KalendarSyncEvent.id] via [fetchEvents].
     */
    public suspend fun deleteEvent(eventId: String): KalendarSyncResult<Unit>
}
