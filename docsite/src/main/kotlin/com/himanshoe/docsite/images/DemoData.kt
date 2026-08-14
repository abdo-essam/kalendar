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

package com.himanshoe.docsite.images

import androidx.compose.ui.graphics.Color
import com.himanshoe.kalendar.foundation.datetime.KalendarTimeSource
import com.himanshoe.kalendar.foundation.event.BasicKalendarEvent
import com.himanshoe.kalendar.foundation.event.KalendarEvents
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * The week a reader sees in every documentation image, and the events on it.
 *
 * March 2026 rather than the real date, for two reasons. The obvious one is that a picture generated
 * from `Clock.System.now()` changes every day, so the same build produces a different byte stream
 * every time it runs and no two images in the gallery agree with each other. The subtler one is that
 * the views style *today* differently, so a floating date moves the emphasis around the grid from one
 * regeneration to the next and the picture stops meaning what its caption says.
 *
 * March 2026 starts on a Sunday, which makes it a good month to photograph: with Monday-start weeks
 * the grid has a full leading row of adjacent-month dates, so the adjacent-date treatment is visible
 * rather than something a reader has to take on faith.
 *
 * The clock is pinned too, not just the dates — see [timeSource]. Nothing here is faked: the views
 * are handed a fixed clock through the seam they already have for it, so what they draw is what they
 * would draw for a real user standing at that moment.
 */
@Suppress("MagicNumber") // A fixture is literal dates and literal colours; naming each one hides it.
internal object Demo {

    /** The year every image is dated in. */
    const val YEAR: Int = 2026

    /** The month every image is dated in — March, whose 1st falls on a Sunday in [YEAR]. */
    const val MONTH: Int = 3

    /**
     * The clock every picture is drawn against: 10:20 on Tuesday 10 March 2026, in UTC.
     *
     * The views read "today" through a [KalendarTimeSource] rather than the system clock, so pinning
     * one here is what makes the pictures both *honest* and *stable*. Honest, because the Schedule
     * views draw their now-indicator only on today's page — with a real clock a picture of March
     * would have no red line in it at all, and the docs would be illustrating the feature with its
     * absence. Stable, because a real clock also moves the "today" emphasis around the grid between
     * one regeneration and the next.
     *
     * 10:20 rather than a round hour so the line lands *inside* the morning's meetings instead of
     * hiding along an hour rule.
     */
    val timeSource: KalendarTimeSource = KalendarTimeSource(
        timeZone = TimeZone.UTC,
        clock = object : Clock {
            override fun now(): Instant = Instant.parse("2026-03-10T10:20:00Z")
        },
    )

    /** The day drawn as selected: a Thursday, mid-month, mid-week. */
    val selectedDate: LocalDate = march(12)

    /** The day the Schedule view shows: the busiest one, with two overlapping meetings. */
    val scheduleDate: LocalDate = march(10)

    /**
     * The day selected in the Year picture.
     *
     * Twelve month grids at a readable cell size are far taller than any documentation image, so that
     * picture is a crop of the top of the column — and a crop that opens on March would be a crop
     * with nothing selected in it. Selecting a January date puts the selection ring where the crop
     * can actually show it, in the same year as every other picture.
     */
    val yearSelectedDate: LocalDate = LocalDate(YEAR, 1, 15)

    private val indigo = Color(0xFF5B8DEF)
    private val teal = Color(0xFF26A69A)
    private val amber = Color(0xFFEF6C00)
    private val magenta = Color(0xFFC2185B)
    private val violet = Color(0xFF7E57C2)

    /**
     * One realistic fortnight, shared by every view so the same data is recognisable from picture to
     * picture: timed meetings that overlap, a day carrying more events than the indicator cap, a
     * multi-day span, and an all-day event.
     */
    val events: KalendarEvents = listOf(
        timed(
            day = 9,
            name = "Sprint planning",
            fromHour = 9,
            fromMinute = 0,
            toHour = 10,
            toMinute = 30,
            color = indigo,
            note = "Room 4 · whole team",
        ),
        timed(
            day = 10,
            name = "Design review",
            fromHour = 9,
            fromMinute = 30,
            toHour = 11,
            toMinute = 0,
            color = teal,
            note = "Calendar views, second pass",
        ),
        timed(
            day = 10,
            name = "1:1 with Sam",
            fromHour = 10,
            fromMinute = 0,
            toHour = 10,
            toMinute = 45,
            color = amber,
        ),
        timed(
            day = 10,
            name = "Retro",
            fromHour = 15,
            fromMinute = 0,
            toHour = 16,
            toMinute = 0,
            color = magenta,
        ),
        allDay(day = 10, name = "Release day", color = violet, note = "2.0 to the store"),
        timed(
            day = 11,
            name = "Team lunch",
            fromHour = 12,
            fromMinute = 30,
            toHour = 13,
            toMinute = 30,
            color = magenta,
        ),
        timed(
            day = 12,
            name = "Roadmap sync",
            fromHour = 11,
            fromMinute = 0,
            toHour = 12,
            toMinute = 0,
            color = indigo,
        ),
        span(fromDay = 12, toDay = 14, name = "Compose conference", color = violet, note = "Berlin"),
        /*
         * The 13th carries four events against an indicator cap of three, which is what puts the
         * `+1` overflow badge in the month and week pictures. They are timed rather than all-day so
         * the Schedule week's shared all-day row stays inside its width: that row does not wrap, and
         * a picture whose top strip ends in a half-drawn chip reads as a bug in the library.
         */
        timed(
            day = 13,
            name = "Docs freeze",
            fromHour = 14,
            fromMinute = 0,
            toHour = 15,
            toMinute = 0,
            color = teal,
        ),
        timed(
            day = 13,
            name = "Interview panel",
            fromHour = 15,
            fromMinute = 0,
            toHour = 16,
            toMinute = 0,
            color = amber,
        ),
        timed(
            day = 13,
            name = "Vendor call",
            fromHour = 16,
            fromMinute = 30,
            toHour = 17,
            toMinute = 0,
            color = indigo,
        ),
        timed(
            day = 16,
            name = "Kickoff",
            fromHour = 9,
            fromMinute = 0,
            toHour = 10,
            toMinute = 0,
            color = teal,
        ),
        allDay(day = 24, name = "Quarterly review", color = amber),
        /*
         * Earlier and later months, so the Year and Timeline pictures — which show months either
         * side of March — have dots in them rather than a grid of bare numbers.
         */
        dated(month = 1, day = 8, name = "Kick-off week", color = teal),
        dated(month = 1, day = 15, name = "Planning offsite", color = indigo),
        dated(month = 1, day = 27, name = "Design crit", color = magenta),
        BasicKalendarEvent(
            date = LocalDate(YEAR, 2, 9),
            endDate = LocalDate(YEAR, 2, 11),
            eventName = "Research sprint",
            eventColor = violet,
        ),
        dated(month = 2, day = 20, name = "All hands", color = amber),
        BasicKalendarEvent(
            date = LocalDate(YEAR, MONTH + 1, 2),
            eventName = "Offsite",
            eventColor = indigo,
        ),
        BasicKalendarEvent(
            date = LocalDate(YEAR, MONTH + 1, 7),
            eventName = "Release 2.0",
            eventColor = violet,
        ),
    )

    /**
     * The slice the Agenda picture lists.
     *
     * That view skips empty dates and opens at the earliest event it is given, so handing it the
     * whole fixture would open the picture on January — months away from the week every other image
     * shows. Narrowing it to March opens it on the same days as the rest of the gallery.
     */
    val agendaEvents: KalendarEvents = events.filter { event -> event.date >= march(9) && event.date < march(20) }

    private fun march(day: Int): LocalDate = LocalDate(YEAR, MONTH, day)

    private fun dated(
        month: Int,
        day: Int,
        name: String,
        color: Color,
    ): BasicKalendarEvent = BasicKalendarEvent(
        date = LocalDate(YEAR, month, day),
        eventName = name,
        eventColor = color,
    )

    @Suppress("LongParameterList")
    private fun timed(
        day: Int,
        name: String,
        fromHour: Int,
        fromMinute: Int,
        toHour: Int,
        toMinute: Int,
        color: Color,
        note: String? = null,
    ): BasicKalendarEvent = BasicKalendarEvent(
        date = march(day),
        eventName = name,
        eventDescription = note,
        startTime = LocalDateTime(YEAR, MONTH, day, fromHour, fromMinute),
        endTime = LocalDateTime(YEAR, MONTH, day, toHour, toMinute),
        eventColor = color,
    )

    private fun allDay(
        day: Int,
        name: String,
        color: Color,
        note: String? = null,
    ): BasicKalendarEvent = BasicKalendarEvent(
        date = march(day),
        eventName = name,
        eventDescription = note,
        eventColor = color,
    )

    private fun span(
        fromDay: Int,
        toDay: Int,
        name: String,
        color: Color,
        note: String? = null,
    ): BasicKalendarEvent = BasicKalendarEvent(
        date = march(fromDay),
        endDate = march(toDay),
        eventName = name,
        eventDescription = note,
        eventColor = color,
    )
}
