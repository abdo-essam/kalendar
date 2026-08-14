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
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.plus

@Suppress("MagicNumber")
private object EventColors {
    val ConferencePurple = Color(0xFF7E57C2)
    val StandupTeal = Color(0xFF26A69A)
    val ReviewOrange = Color(0xFFEF6C00)
    val OneOnOnePink = Color(0xFFC2185B)
    val ReleaseBlue = Color(0xFF1E88E5)
    val FocusGreen = Color(0xFF43A047)
}

/**
 * The fixture the playground renders: overlapping timed events so the Schedule views have
 * something to lay out, a multi-day event and an all-day event so those code paths are visible,
 * and events on neighbouring days so the Month and Year grids show indicator dots.
 */
internal fun demoEvents(today: LocalDate): List<BasicKalendarEvent> = listOf(
    BasicKalendarEvent(
        date = today,
        endDate = today.plus(value = 2, unit = DateTimeUnit.DAY),
        eventName = "Conference",
        eventColor = EventColors.ConferencePurple,
    ),
    BasicKalendarEvent(
        date = today,
        eventName = "Release day",
        eventColor = EventColors.ReleaseBlue,
    ),
    BasicKalendarEvent(
        date = today,
        eventName = "Standup",
        startTime = today.at(hour = 9),
        endTime = today.at(hour = 9, minute = 30),
        eventColor = EventColors.StandupTeal,
    ),
    BasicKalendarEvent(
        date = today,
        eventName = "Design review",
        startTime = today.at(hour = 9, minute = 15),
        endTime = today.at(hour = 10, minute = 15),
        eventColor = EventColors.ReviewOrange,
    ),
    BasicKalendarEvent(
        date = today,
        eventName = "1:1",
        startTime = today.at(hour = 14),
        endTime = today.at(hour = 14, minute = 45),
        eventColor = EventColors.OneOnOnePink,
    ),
    BasicKalendarEvent(
        date = today.plus(value = 1, unit = DateTimeUnit.DAY),
        eventName = "Focus block",
        startTime = today.plus(value = 1, unit = DateTimeUnit.DAY).at(hour = 11),
        endTime = today.plus(value = 1, unit = DateTimeUnit.DAY).at(hour = 13),
        eventColor = EventColors.FocusGreen,
    ),
    BasicKalendarEvent(
        date = today.plus(value = 4, unit = DateTimeUnit.DAY),
        eventName = "Retro",
        startTime = today.plus(value = 4, unit = DateTimeUnit.DAY).at(hour = 16),
        endTime = today.plus(value = 4, unit = DateTimeUnit.DAY).at(hour = 17),
        eventColor = EventColors.StandupTeal,
    ),
)

private fun LocalDate.at(hour: Int, minute: Int = 0): LocalDateTime =
    LocalDateTime(date = this, time = LocalTime(hour = hour, minute = minute))
