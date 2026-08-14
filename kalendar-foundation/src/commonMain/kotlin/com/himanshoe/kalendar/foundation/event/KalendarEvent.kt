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

package com.himanshoe.kalendar.foundation.event

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.ui.graphics.Color
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime

/**
 * Represents a calendar event tied to a specific date.
 *
 * Implementing classes may optionally provide [startTime] and [endTime] to enable
 * time-based ordering within a day, and [eventColor] to render a per-event colour dot.
 * All three properties default to `null` so that existing implementations remain
 * source-compatible without change.
 */
@Stable
public interface KalendarEvent {
    /**
     * A stable identifier for this event, unique across the whole event list handed to a calendar.
     *
     * Views that keep per-item state — [KalendarAgenda][com.himanshoe.kalendar.view.KalendarAgenda]'s
     * list, for one — use it as their item key, so supplying it keeps that state attached to the
     * right event across reorders and edits. Consumers can also use it to look an event back up
     * when a click callback hands them one. `null`, the default, makes such views fall back to the
     * event's position in the list.
     */
    public val id: String? get() = null

    /**
     * The date on which this event falls, or — for a multi-day event — the first date it
     * occupies.
     */
    public val date: LocalDate

    /**
     * The last date this event occupies, inclusive, for a multi-day event (e.g. a vacation or
     * conference spanning several days). `null` means the event is confined to [date]. When
     * non-null, calendar views show the event on every date from [date] through [endDate].
     * Defaults to `null`.
     */
    public val endDate: LocalDate? get() = null

    /**
     * A short, human-readable name for the event (e.g. "Team standup").
     */
    public val eventName: String

    /**
     * An optional longer description for the event. Defaults to `null`.
     */
    public val eventDescription: String?

    /**
     * The date and time at which the event begins. When non-null, events on the same
     * day are sorted by this value in ascending order (e.g. in the Agenda view).
     * Defaults to `null`.
     */
    public val startTime: LocalDateTime? get() = null

    /**
     * The date and time at which the event ends. Displayed alongside [startTime] wherever
     * duration information is shown (e.g. in the Agenda view). Defaults to `null`.
     */
    public val endTime: LocalDateTime? get() = null

    /**
     * An optional colour used to tint the event's indicator dot on the day cell.
     * When `null`, the calendar falls back to a default indicator colour.
     * Defaults to `null`.
     */
    public val eventColor: Color? get() = null

    /**
     * An optional identifier that links this event to a named calendar source (e.g. "work",
     * "personal"). Carried as metadata so consumers can group or tint events per source; the
     * built-in views colour indicators from [eventColor] only. Defaults to `null`.
     */
    public val calendarId: String? get() = null
}

/**
 * A ready-to-use implementation of [KalendarEvent].
 *
 * @property date The date on which the event falls, or the first date for a multi-day event.
 * @property eventName A short, human-readable name for the event.
 * @property eventDescription An optional longer description. Defaults to `null`.
 * @property startTime Optional start date-time used for within-day ordering. Defaults to `null`.
 * @property endTime Optional end date-time shown alongside [startTime]. Defaults to `null`.
 * @property eventColor Optional colour for the event's indicator dot. Defaults to `null`
 *   (a default indicator colour is used).
 * @property calendarId Optional source calendar identifier, carried as metadata for consumers
 *   that group or tint events per source. Defaults to `null`.
 * @property endDate The last date this event occupies, inclusive, for a multi-day event.
 *   Defaults to `null` (single-day event).
 * @property id A stable identifier, unique across the event list, used as the list item key by
 *   views that keep per-item state. Defaults to `null` (those views fall back to list position).
 */
@Immutable
public data class BasicKalendarEvent(
    override val date: LocalDate,
    override val eventName: String,
    override val eventDescription: String? = null,
    override val startTime: LocalDateTime? = null,
    override val endTime: LocalDateTime? = null,
    override val eventColor: Color? = null,
    override val calendarId: String? = null,
    override val endDate: LocalDate? = null,
    override val id: String? = null,
) : KalendarEvent

/**
 * A list of [KalendarEvent] instances.
 *
 * Use standard Kotlin collection builders to create one:
 * ```kotlin
 * val events: KalendarEvents = listOf(
 *     BasicKalendarEvent(
 *         date = today,
 *         eventName = "Team standup",
 *         startTime = LocalDateTime(today, LocalTime(9, 0)),
 *     ),
 * )
 * ```
 */
public typealias KalendarEvents = List<KalendarEvent>
