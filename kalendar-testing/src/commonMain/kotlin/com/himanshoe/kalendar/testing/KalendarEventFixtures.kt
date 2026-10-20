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

package com.himanshoe.kalendar.testing

import androidx.compose.ui.graphics.Color
import com.himanshoe.kalendar.foundation.event.BasicKalendarEvent
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours

/** What every fixture calls its event when the test does not care what it is called. */
private const val DEFAULT_EVENT_NAME = "Test event"

/** How long a [timedKalendarEvent] runs when the test only cares that it is timed. */
private val DEFAULT_EVENT_DURATION: Duration = 1.hours

/** How often [recurringKalendarEvents] repeats when the test does not say: weekly. */
private val DEFAULT_RECURRENCE_STEP: DatePeriod = DatePeriod(days = 7)

/**
 * A plain all-day-style event on [date] — the fixture for every test where an event's existence is
 * the point and its details are not.
 *
 * ```kotlin
 * val standup = kalendarEvent(date = LocalDate(2026, 8, 15), name = "standup", id = "1")
 * ```
 *
 * @param date The single date the event falls on.
 * @param name The event's name. Defaults to `"Test event"`.
 * @param id Stable identifier, used by the views as a list key and by
 *   [com.himanshoe.kalendar.state.KalendarEventLoader] for de-duplication. Defaults to `null`,
 *   matching [BasicKalendarEvent]'s own default.
 * @param color Indicator-dot colour. Defaults to `null` (the theme's default indicator colour).
 * @param description Longer description. Defaults to `null`.
 */
public fun kalendarEvent(
    date: LocalDate,
    name: String = DEFAULT_EVENT_NAME,
    id: String? = null,
    color: Color? = null,
    description: String? = null,
): BasicKalendarEvent = BasicKalendarEvent(
    date = date,
    eventName = name,
    eventDescription = description,
    eventColor = color,
    id = id,
)

/**
 * An event with a start and end time on [date], for the Schedule and Agenda surfaces where
 * within-day placement is what is under test.
 *
 * The end is [start] plus [duration] in pure wall-clock arithmetic, so an event starting at 23:30
 * with the default one-hour duration ends at 00:30 *the next day* — [BasicKalendarEvent.endTime]
 * carries the date, so nothing is lost. The event still occupies only [date]
 * ([BasicKalendarEvent.endDate] stays `null`); use [multiDayKalendarEvent] when a span across day
 * cells is the thing being tested.
 *
 * ```kotlin
 * val standup = timedKalendarEvent(date = august15, start = LocalTime(9, 0))          // 09:00–10:00
 * val review = timedKalendarEvent(date = august15, start = LocalTime(14, 0), duration = 30.minutes)
 * ```
 *
 * @param date The date the event starts on.
 * @param start The wall-clock time the event starts.
 * @param duration How long it runs. Defaults to one hour.
 * @param name The event's name. Defaults to `"Test event"`.
 * @param id Stable identifier. Defaults to `null`.
 * @param color Indicator colour. Defaults to `null`.
 */
public fun timedKalendarEvent(
    date: LocalDate,
    start: LocalTime,
    duration: Duration = DEFAULT_EVENT_DURATION,
    name: String = DEFAULT_EVENT_NAME,
    id: String? = null,
    color: Color? = null,
): BasicKalendarEvent {
    val startTime = LocalDateTime(date, start)
    // UTC has no clock changes, so instant arithmetic there IS wall-clock arithmetic — the only
    // zone where "plus one hour" always moves the clock face by exactly one hour.
    val endTime = (startTime.toInstant(TimeZone.UTC) + duration).toLocalDateTime(TimeZone.UTC)
    return BasicKalendarEvent(
        date = date,
        eventName = name,
        startTime = startTime,
        endTime = endTime,
        eventColor = color,
        id = id,
    )
}

/**
 * An event occupying every date from [start] through [end] inclusive — what the month and timeline
 * grids draw as a continuous span bar.
 *
 * ```kotlin
 * val conference = multiDayKalendarEvent(
 *     start = LocalDate(2026, 8, 12),
 *     end = LocalDate(2026, 8, 14),
 *     id = "conf-1",
 * )
 * ```
 *
 * Give spans an id in loader tests: a span straddling two loaded ranges is legitimately returned
 * by both, and the id is what tells the loader the two answers are one event.
 *
 * @param start The first date the event occupies.
 * @param end The last date it occupies, inclusive. Must not be before [start].
 * @param name The event's name. Defaults to `"Test event"`.
 * @param id Stable identifier. Defaults to `null`.
 * @param color Span-bar colour. Defaults to `null`.
 */
public fun multiDayKalendarEvent(
    start: LocalDate,
    end: LocalDate,
    name: String = DEFAULT_EVENT_NAME,
    id: String? = null,
    color: Color? = null,
): BasicKalendarEvent {
    require(start <= end) { "start must not be after end, was $start..$end" }
    return BasicKalendarEvent(
        date = start,
        endDate = end,
        eventName = name,
        eventColor = color,
        id = id,
    )
}

/**
 * The shape of a recurring event as the calendar views see one: [count] single-day occurrences of
 * the same name, [every] apart, starting at [start].
 *
 * Kalendar's UI takes expanded occurrences rather than recurrence rules (expansion lives in
 * `kalendar-sync`), so this is the fixture for "a weekly standup on the visible month". Each
 * occurrence gets its own id — `"<idPrefix>-0"`, `"<idPrefix>-1"`, ... — because occurrences
 * sharing one id would be collapsed to a single event by the loader's de-duplication, which is
 * never what a recurrence test means.
 *
 * Occurrence *n* falls on [start] plus *n* times [every], each computed from [start] rather than
 * from the previous occurrence — so a monthly series from 31 January lands on 28 February and then
 * back on 31 March, instead of drifting to the 28th forever after the first short month.
 *
 * ```kotlin
 * val standups = recurringKalendarEvents(start = LocalDate(2026, 8, 3), count = 4, name = "standup")
 * val rent = recurringKalendarEvents(
 *     start = LocalDate(2026, 1, 1),
 *     count = 12,
 *     every = DatePeriod(months = 1),
 * )
 * ```
 *
 * @param start The first occurrence's date.
 * @param count How many occurrences to produce. Must not be negative; `0` gives an empty list.
 * @param every The step between occurrences. Defaults to weekly.
 * @param name Every occurrence's name. Defaults to `"Test event"`.
 * @param idPrefix Prefix of each occurrence's id. Defaults to [name].
 * @param color Indicator colour for every occurrence. Defaults to `null`.
 */
public fun recurringKalendarEvents(
    start: LocalDate,
    count: Int,
    every: DatePeriod = DEFAULT_RECURRENCE_STEP,
    name: String = DEFAULT_EVENT_NAME,
    idPrefix: String = name,
    color: Color? = null,
): List<BasicKalendarEvent> {
    require(count >= 0) { "count must not be negative, was $count" }
    return List(count) { index ->
        val step = DatePeriod(
            years = every.years * index,
            months = every.months * index,
            days = every.days * index,
        )
        kalendarEvent(date = start.plus(step), name = name, id = "$idPrefix-$index", color = color)
    }
}
