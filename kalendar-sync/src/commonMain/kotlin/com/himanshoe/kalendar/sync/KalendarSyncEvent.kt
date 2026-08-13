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

/**
 * A calendar event read from or written to the device's system calendar.
 *
 * Implement this interface for custom event types, or use [BasicKalendarSyncEvent]
 * for the built-in implementation.
 */
public interface KalendarSyncEvent {
    /** Stable identifier assigned by the device calendar. `null` for events not yet persisted. */
    public val id: String?

    /** The calendar date this event falls on, or the first date for a multi-day event. */
    public val date: LocalDate

    /**
     * The last date this event occupies, inclusive, for a multi-day all-day event (e.g. a
     * vacation spanning several days). `null` means the event is confined to [date]. Defaults
     * to `null`.
     */
    public val endDate: LocalDate?
        get() = null

    /** Display title of the event. */
    public val eventName: String

    /** Optional longer description or notes. */
    public val eventDescription: String?

    /**
     * Start time for timed events. `null` for all-day events.
     * Combined with [date] to form the full start date-time.
     */
    public val startTime: LocalTime?

    /**
     * End time for timed events. `null` for all-day events.
     * Combined with [date] to form the full end date-time.
     */
    public val endTime: LocalTime?

    /** `true` if this occupies the entire day with no specific start/end time. */
    public val isAllDay: Boolean
        get() = startTime == null && endTime == null

    /**
     * Optional RFC 5545 recurrence rule. When non-null, this event is a template for a
     * recurring series; use [expandOccurrences] to materialise individual occurrences. `null`
     * for non-recurring (one-off) events.
     */
    public val recurrenceRule: KalendarRule?
        get() = null

    /**
     * Dates to skip when [recurrenceRule] is expanded via [expandOccurrences] — the RFC 5545
     * EXDATE equivalent, for "delete just this one occurrence" without breaking the series.
     * Ignored for non-recurring events. Defaults to empty.
     */
    public val exceptionDates: List<LocalDate>
        get() = emptyList()
}

/**
 * Default implementation of [KalendarSyncEvent].
 *
 * @param id Identifier assigned by the device calendar after insertion. Pass `null`
 *   when creating a new event before it has been saved.
 * @param date The date the event falls on.
 * @param eventName Display title.
 * @param eventDescription Optional notes or description.
 * @param startTime Start time for timed events; `null` for all-day.
 * @param endTime End time for timed events; `null` for all-day.
 * @param recurrenceRule Optional RFC 5545 recurrence rule. `null` for non-recurring events.
 * @param endDate The last date this event occupies, inclusive, for a multi-day all-day event.
 *   Defaults to `null` (single-day event).
 * @param exceptionDates Dates to skip when [recurrenceRule] is expanded. Defaults to empty.
 */
public data class BasicKalendarSyncEvent(
    override val id: String? = null,
    override val date: LocalDate,
    override val eventName: String,
    override val eventDescription: String? = null,
    override val startTime: LocalTime? = null,
    override val endTime: LocalTime? = null,
    override val recurrenceRule: KalendarRule? = null,
    override val endDate: LocalDate? = null,
    override val exceptionDates: List<LocalDate> = emptyList(),
) : KalendarSyncEvent
