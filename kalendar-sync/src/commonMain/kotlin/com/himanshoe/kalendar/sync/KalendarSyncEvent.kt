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
     * The last date this event occupies, inclusive, for an event spanning more than one day —
     * a vacation covering several all-day dates, or a timed shift running past midnight.
     * `null` means the event is confined to [date]. Defaults to `null`.
     *
     * For a timed event this pairs with [endTime] to form the full end date-time, so a shift
     * from 22:00 on the 1st to 06:00 on the 2nd sets `date = 1st`, `startTime = 22:00`,
     * `endDate = 2nd`, `endTime = 06:00`. A timed event whose [endTime] is at or before its
     * [startTime] is treated as crossing a single midnight even when this is `null`, since an
     * end at or before the start cannot be represented otherwise.
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
     *
     * Written to and read from the device calendar on Android (`CalendarContract`'s `RRULE`
     * column, paired with the `DURATION` the provider requires for recurring events) and iOS
     * (`EKRecurrenceRule`), and encoded as an `RRULE` line by the iCal codec.
     */
    public val recurrenceRule: KalendarRule?
        get() = null

    /**
     * Dates to skip when [recurrenceRule] is expanded via [expandOccurrences] — the RFC 5545
     * EXDATE equivalent, for "delete just this one occurrence" without breaking the series.
     * Ignored for non-recurring events. Defaults to empty.
     *
     * Written and read on Android (`CalendarContract`'s `EXDATE` column) and encoded as an
     * `EXDATE` line by the iCal codec. **On iOS exception dates are not written** — EventKit
     * has no per-rule EXDATE API; delete the individual occurrence there instead.
     */
    public val exceptionDates: List<LocalDate>
        get() = emptyList()

    /**
     * Reminders attached to this event, each firing a fixed number of minutes before the
     * event starts. Defaults to empty.
     *
     * Written and read on Android (`CalendarContract.Reminders`), iOS (`EKAlarm`), and the
     * Desktop `.ics` provider; encoded as `VALARM` blocks by the iCal codec.
     */
    public val reminders: List<KalendarReminder>
        get() = emptyList()

    /**
     * People invited to this event. Defaults to empty.
     *
     * Written and read on Android (`CalendarContract.Attendees`) and the Desktop `.ics`
     * provider; encoded as `ATTENDEE` lines by the iCal codec. **On iOS attendees are
     * read-only** — EventKit does not allow adding attendees programmatically, so writes are
     * ignored there; fetched events still populate this list.
     */
    public val attendees: List<KalendarAttendee>
        get() = emptyList()
}

/**
 * Default implementation of [KalendarSyncEvent].
 *
 * This is deliberately a plain `class` with a hand-written [copy], [equals], and [hashCode]
 * rather than a `data class`. A `data class` compiles its `copy` and `componentN` accessors into
 * the ABI, so adding one property later changes `copy`'s signature and breaks every already
 * compiled consumer; writing them by hand lets new properties arrive as source-compatible
 * defaults instead.
 *
 * @param id Identifier assigned by the device calendar after insertion. Pass `null`
 *   when creating a new event before it has been saved.
 * @param date The date the event falls on.
 * @param eventName Display title.
 * @param eventDescription Optional notes or description.
 * @param startTime Start time for timed events; `null` for all-day.
 * @param endTime End time for timed events; `null` for all-day.
 * @param recurrenceRule Optional RFC 5545 recurrence rule. `null` for non-recurring events.
 * @param endDate The last date this event occupies, inclusive, for an event spanning more than
 *   one day. Defaults to `null` (single-day event).
 * @param exceptionDates Dates to skip when [recurrenceRule] is expanded. Defaults to empty.
 * @param reminders Reminders firing a fixed number of minutes before the event starts.
 *   Defaults to empty.
 * @param attendees People invited to this event. Defaults to empty. Read-only on iOS —
 *   see [KalendarSyncEvent.attendees].
 */
public class BasicKalendarSyncEvent(
    override val id: String? = null,
    override val date: LocalDate,
    override val eventName: String,
    override val eventDescription: String? = null,
    override val startTime: LocalTime? = null,
    override val endTime: LocalTime? = null,
    override val recurrenceRule: KalendarRule? = null,
    override val endDate: LocalDate? = null,
    override val exceptionDates: List<LocalDate> = emptyList(),
    override val reminders: List<KalendarReminder> = emptyList(),
    override val attendees: List<KalendarAttendee> = emptyList(),
) : KalendarSyncEvent {

    /**
     * Returns a copy of this event with the given properties replaced and all others carried
     * over unchanged.
     */
    public fun copy(
        id: String? = this.id,
        date: LocalDate = this.date,
        eventName: String = this.eventName,
        eventDescription: String? = this.eventDescription,
        startTime: LocalTime? = this.startTime,
        endTime: LocalTime? = this.endTime,
        recurrenceRule: KalendarRule? = this.recurrenceRule,
        endDate: LocalDate? = this.endDate,
        exceptionDates: List<LocalDate> = this.exceptionDates,
        reminders: List<KalendarReminder> = this.reminders,
        attendees: List<KalendarAttendee> = this.attendees,
    ): BasicKalendarSyncEvent = BasicKalendarSyncEvent(
        id = id,
        date = date,
        eventName = eventName,
        eventDescription = eventDescription,
        startTime = startTime,
        endTime = endTime,
        recurrenceRule = recurrenceRule,
        endDate = endDate,
        exceptionDates = exceptionDates,
        reminders = reminders,
        attendees = attendees,
    )

    /** Two events are equal when every property matches. */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is BasicKalendarSyncEvent) return false
        if (id != other.id) return false
        if (date != other.date) return false
        if (eventName != other.eventName) return false
        if (eventDescription != other.eventDescription) return false
        if (startTime != other.startTime) return false
        if (endTime != other.endTime) return false
        if (recurrenceRule != other.recurrenceRule) return false
        if (endDate != other.endDate) return false
        if (exceptionDates != other.exceptionDates) return false
        if (reminders != other.reminders) return false
        return attendees == other.attendees
    }

    override fun hashCode(): Int {
        var result = id?.hashCode() ?: 0
        result = HASH_FACTOR * result + date.hashCode()
        result = HASH_FACTOR * result + eventName.hashCode()
        result = HASH_FACTOR * result + (eventDescription?.hashCode() ?: 0)
        result = HASH_FACTOR * result + (startTime?.hashCode() ?: 0)
        result = HASH_FACTOR * result + (endTime?.hashCode() ?: 0)
        result = HASH_FACTOR * result + (recurrenceRule?.hashCode() ?: 0)
        result = HASH_FACTOR * result + (endDate?.hashCode() ?: 0)
        result = HASH_FACTOR * result + exceptionDates.hashCode()
        result = HASH_FACTOR * result + reminders.hashCode()
        result = HASH_FACTOR * result + attendees.hashCode()
        return result
    }

    override fun toString(): String =
        "BasicKalendarSyncEvent(id=$id, date=$date, eventName=$eventName, " +
            "eventDescription=$eventDescription, startTime=$startTime, endTime=$endTime, " +
            "recurrenceRule=$recurrenceRule, endDate=$endDate, exceptionDates=$exceptionDates, " +
            "reminders=$reminders, attendees=$attendees)"
}

/**
 * Rebuilds any [KalendarSyncEvent] as a [BasicKalendarSyncEvent], optionally replacing the
 * identity and span fields the callers of this function need to change.
 *
 * Every field of the source event is carried across in exactly one place, so a property added to
 * [KalendarSyncEvent] later cannot be silently dropped by one of the several call sites that
 * re-materialise events — the device providers when they stamp a new id onto an event they are
 * persisting, and the recurrence expander when it materialises an occurrence.
 */
internal fun KalendarSyncEvent.copyAsBasic(
    id: String? = this.id,
    date: LocalDate = this.date,
    endDate: LocalDate? = this.endDate,
): BasicKalendarSyncEvent = BasicKalendarSyncEvent(
    id = id,
    date = date,
    eventName = eventName,
    eventDescription = eventDescription,
    startTime = startTime,
    endTime = endTime,
    recurrenceRule = recurrenceRule,
    endDate = endDate,
    exceptionDates = exceptionDates,
    reminders = reminders,
    attendees = attendees,
)

private const val HASH_FACTOR = 31
