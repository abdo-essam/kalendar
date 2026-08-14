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

package com.himanshoe.sample.playground

import androidx.compose.ui.graphics.Color
import com.himanshoe.kalendar.foundation.event.BasicKalendarEvent
import com.himanshoe.kalendar.foundation.schedule.KalendarResource
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.minus
import kotlinx.datetime.plus

@Suppress("MagicNumber")
private object EventColors {
    val Purple = Color(0xFF7E57C2)
    val Teal = Color(0xFF26A69A)
    val Orange = Color(0xFFEF6C00)
    val Pink = Color(0xFFC2185B)
    val Blue = Color(0xFF1E88E5)
    val Green = Color(0xFF43A047)
    val Indigo = Color(0xFF3949AB)
    val Brown = Color(0xFF6D4C41)
}

/** The rooms the resource view lanes events into, matched to events by `calendarId`. */
@Suppress("MagicNumber")
internal fun demoResources(): List<KalendarResource> = listOf(
    KalendarResource(id = "atrium", title = "Atrium", color = Color(0xFF3949AB), order = 0),
    KalendarResource(id = "studio", title = "Studio", color = Color(0xFF26A69A), order = 1),
    KalendarResource(id = "annex", title = "Annex", color = Color(0xFFEF6C00), order = 2),
)

/**
 * A working month, not a fixture.
 *
 * The playground's whole claim is that these views hold a real calendar, and a lone event on one day
 * proves nothing: the Month grid needs enough days carrying enough events to reach the indicator
 * cap, the Agenda needs a list long enough to scroll, the Schedule and Week grid need overlaps deep
 * enough to pack into columns, and the resource view needs every lane occupied at once. So this
 * covers roughly five weeks around [today] — a recurring standup, a stack of overlapping mornings, a
 * multi-day conference, all-day markers, and evening events that only show up if you scroll the hour
 * grid past its initial hour.
 *
 * Every timed event carries a `calendarId`, which is the field
 * [com.himanshoe.kalendar.KalendarResourceView] reads by default to pick a lane.
 */
@Suppress("LongMethod")
internal fun demoEvents(today: LocalDate): List<BasicKalendarEvent> = buildList {
    add(
        BasicKalendarEvent(
            id = "conference",
            date = today.shift(1),
            endDate = today.shift(3),
            eventName = "Droidcon Berlin",
            eventDescription = "Three days, two tracks, one very long queue for coffee.",
            eventColor = EventColors.Purple,
        ),
    )
    add(
        BasicKalendarEvent(
            id = "release",
            date = today,
            eventName = "Kalendar 2.0 ships",
            eventDescription = "Tag, publish, then do not touch anything.",
            eventColor = EventColors.Blue,
        ),
    )
    add(
        BasicKalendarEvent(
            id = "leave",
            date = today.shift(LeaveStartOffset),
            endDate = today.shift(LeaveEndOffset),
            eventName = "Maya on leave",
            eventColor = EventColors.Brown,
        ),
    )

    // A standup on every weekday of the visible span, which is what makes the Month grid look like
    // a calendar someone uses rather than a grid with three dots in it.
    for (offset in StandupSpanStart..StandupSpanEnd) {
        val day = today.shift(offset)
        if (day.isWeekday()) {
            add(
                BasicKalendarEvent(
                    id = "standup-$offset",
                    date = day,
                    eventName = "Standup",
                    startTime = day.at(hour = StandupHour, minute = StandupMinute),
                    endTime = day.at(hour = StandupHour, minute = StandupEndMinute),
                    eventColor = EventColors.Teal,
                    calendarId = "studio",
                ),
            )
        }
    }

    addAll(timedEvents(today))
}

/**
 * The named meetings, laid out so that today packs three overlapping blocks — a schedule view with
 * nothing to overlap never shows the column packing it exists to do.
 */
private fun timedEvents(today: LocalDate): List<BasicKalendarEvent> = listOf(
    Meeting(day = 0, name = "Design review", hour = 9, minute = 15, minutes = 60, room = "atrium"),
    Meeting(day = 0, name = "API council", hour = 9, minute = 45, minutes = 45, room = "annex"),
    Meeting(day = 0, name = "Lunch and learn", hour = 12, minute = 30, minutes = 60, room = "atrium"),
    Meeting(day = 0, name = "1:1 with Priya", hour = 14, minute = 0, minutes = 45, room = "studio"),
    Meeting(day = 0, name = "Release retro", hour = 17, minute = 30, minutes = 60, room = "atrium"),
    Meeting(day = 1, name = "Focus block", hour = 11, minute = 0, minutes = 120, room = "annex"),
    Meeting(day = 1, name = "Roadmap sync", hour = 15, minute = 0, minutes = 60, room = "atrium"),
    Meeting(day = 2, name = "Pairing: paging", hour = 10, minute = 0, minutes = 90, room = "studio"),
    Meeting(day = 2, name = "Support rotation", hour = 13, minute = 0, minutes = 180, room = "annex"),
    Meeting(day = 3, name = "Accessibility audit", hour = 10, minute = 30, minutes = 90, room = "atrium"),
    Meeting(day = 4, name = "Sprint review", hour = 16, minute = 0, minutes = 60, room = "atrium"),
    Meeting(day = 4, name = "Retro", hour = 17, minute = 0, minutes = 45, room = "studio"),
    Meeting(day = 6, name = "Long run", hour = 7, minute = 30, minutes = 90, room = "annex"),
    Meeting(day = -1, name = "Release rehearsal", hour = 15, minute = 30, minutes = 90, room = "atrium"),
    Meeting(day = -2, name = "Interview loop", hour = 11, minute = 0, minutes = 120, room = "studio"),
    Meeting(day = -4, name = "Budget planning", hour = 14, minute = 0, minutes = 60, room = "annex"),
    Meeting(day = 7, name = "Quarterly offsite", hour = 9, minute = 0, minutes = 300, room = "atrium"),
    Meeting(day = 8, name = "Docs sprint", hour = 13, minute = 30, minutes = 150, room = "studio"),
    Meeting(day = 11, name = "Community call", hour = 18, minute = 0, minutes = 60, room = "annex"),
    Meeting(day = 14, name = "Perf deep dive", hour = 10, minute = 0, minutes = 120, room = "studio"),
).mapIndexed { index, meeting -> meeting.toEvent(today = today, index = index) }

/** One meeting, described the way a calendar row would describe it. */
private class Meeting(
    val day: Int,
    val name: String,
    val hour: Int,
    val minute: Int,
    val minutes: Int,
    val room: String,
) {
    fun toEvent(today: LocalDate, index: Int): BasicKalendarEvent {
        val date = today.shift(day)
        val start = date.at(hour = hour, minute = minute)
        return BasicKalendarEvent(
            id = "meeting-$index",
            date = date,
            eventName = name,
            startTime = start,
            endTime = start.plusMinutes(minutes),
            eventColor = roomColors.getValue(room),
            calendarId = room,
        )
    }
}

private val roomColors = mapOf(
    "atrium" to EventColors.Indigo,
    "studio" to EventColors.Green,
    "annex" to EventColors.Orange,
)

private const val LeaveStartOffset = 9
private const val LeaveEndOffset = 13
private const val StandupHour = 9
private const val StandupMinute = 30
private const val StandupEndMinute = 45
private const val StandupSpanStart = -7
private const val StandupSpanEnd = 21
private const val MinutesPerHour = 60
private const val HoursPerDay = 24

private fun LocalDate.shift(days: Int): LocalDate = if (days < 0) {
    minus(value = -days, unit = DateTimeUnit.DAY)
} else {
    plus(value = days, unit = DateTimeUnit.DAY)
}

/** True for Monday through Friday. [DayOfWeek.ordinal] counts from Monday at zero. */
private fun LocalDate.isWeekday(): Boolean = dayOfWeek.ordinal < WeekdayCount

private const val WeekdayCount = 5

private fun LocalDate.at(hour: Int, minute: Int = 0): LocalDateTime =
    LocalDateTime(date = this, time = LocalTime(hour = hour, minute = minute))

/**
 * Adds [minutes] without letting an end time run past midnight — a block the hour grid could not
 * draw. The demo's longest meeting starts mid-afternoon, so clamping never actually fires; it is
 * here so that editing the fixture cannot silently produce one.
 */
private fun LocalDateTime.plusMinutes(minutes: Int): LocalDateTime {
    val endMinute = hour * MinutesPerHour + minute + minutes
    val cap = HoursPerDay * MinutesPerHour - 1
    val clamped = endMinute.coerceAtMost(cap)
    return LocalDateTime(
        date = date,
        time = LocalTime(hour = clamped / MinutesPerHour, minute = clamped % MinutesPerHour),
    )
}
