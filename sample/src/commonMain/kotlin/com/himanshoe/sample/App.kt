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

package com.himanshoe.sample


import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.himanshoe.kalendar.Kalendar
import com.himanshoe.kalendar.KalendarType
import com.himanshoe.kalendar.foundation.component.config.KalendarConfig
import com.himanshoe.kalendar.foundation.event.BasicKalendarEvent
import com.himanshoe.kalendar.foundation.event.KalendarEvent
import com.himanshoe.kalendar.view.KalendarMonth
import com.himanshoe.kalendar.view.KalendarSchedule
import com.himanshoe.kalendar.view.KalendarScheduleWeek
import com.himanshoe.kalendar.view.KalendarSelectionMode
import com.himanshoe.kalendar.view.KalendarTimeline
import com.himanshoe.kalendar.view.KalendarWeek
import com.himanshoe.kalendar.view.KalendarYear
import com.himanshoe.kalendar.view.rememberKalendarMonthState
import com.himanshoe.kalendar.view.rememberKalendarSelectionState
import com.himanshoe.kalendar.view.rememberKalendarTimelineState
import com.himanshoe.kalendar.view.rememberKalendarWeekState
import com.himanshoe.kalendar.view.rememberKalendarYearState
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn
import kotlin.time.Clock

private enum class SampleView(val label: String) {
    Week("Week"),
    Month("Month"),
    Year("Year"),
    Timeline("Timeline"),
    Schedule("Schedule"),
    ScheduleWeek("Week grid"),
    Agenda("Agenda"),
}

private val selectionModes = listOf(
    "Single" to KalendarSelectionMode.Single,
    "Multiple" to KalendarSelectionMode.Multiple,
    "Range" to KalendarSelectionMode.Range,
)

@Composable
fun App() {
    val today = remember { Clock.System.todayIn(TimeZone.currentSystemDefault()) }
    var selectedView by remember { mutableStateOf(SampleView.Timeline) }
    var selectionMode by remember { mutableStateOf<KalendarSelectionMode>(KalendarSelectionMode.Single) }
    val selection = rememberKalendarSelectionState(mode = selectionMode, initialSelection = setOf(today))
    var events by remember {
        mutableStateOf(
            listOf(
            BasicKalendarEvent(
                date = today,
                endDate = today.plus(2, DateTimeUnit.DAY),
                eventName = "Conference",
                eventColor = Color(0xFF7E57C2),
            ),
            BasicKalendarEvent(
                date = today,
                eventName = "Standup",
                startTime = LocalDateTime(today.year, today.monthNumber, today.dayOfMonth, 9, 0),
                endTime = LocalDateTime(today.year, today.monthNumber, today.dayOfMonth, 9, 30),
                eventColor = Color(0xFF26A69A),
            ),
            BasicKalendarEvent(
                date = today,
                eventName = "Design review",
                startTime = LocalDateTime(today.year, today.monthNumber, today.dayOfMonth, 9, 15),
                endTime = LocalDateTime(today.year, today.monthNumber, today.dayOfMonth, 10, 15),
                eventColor = Color(0xFFEF6C00),
            ),
            BasicKalendarEvent(
                date = today,
                eventName = "1:1",
                startTime = LocalDateTime(today.year, today.monthNumber, today.dayOfMonth, 14, 0),
                endTime = LocalDateTime(today.year, today.monthNumber, today.dayOfMonth, 14, 45),
                eventColor = Color(0xFFC2185B),
            ),
        ),
        )
    }

    val onDateClick = { date: LocalDate, _: List<*> -> selection.onDateClick(date) }
    val selectedDates = selection.selectedDates
    val onDateRangeSelect = if (selectionMode == KalendarSelectionMode.Range) {
        { start: LocalDate, current: LocalDate -> selection.onRangeDrag(start, current) }
    } else {
        null
    }
    val onDateRangeSelectEnd = if (selectionMode == KalendarSelectionMode.Range) {
        { selection.onRangeDragEnd() }
    } else {
        null
    }
    val onEventTimeChange = { event: KalendarEvent, newStart: LocalDateTime, newEnd: LocalDateTime ->
        events = events.map { candidate ->
            if (candidate == event) {
                candidate.copy(date = newStart.date, startTime = newStart, endTime = newEnd)
            } else {
                candidate
            }
        }
    }
    val onEventDrop = { dropped: List<KalendarEvent>, newDate: LocalDate ->
        events = events.map { event ->
            if (event in dropped) {
                val shiftDays = (newDate.toEpochDays() - event.date.toEpochDays()).toInt()
                event.copy(
                    date = newDate,
                    endDate = event.endDate?.plus(shiftDays, DateTimeUnit.DAY),
                )
            } else {
                event
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        LazyRow(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(SampleView.entries) { view ->
                FilterChip(
                    selected = view == selectedView,
                    onClick = { selectedView = view },
                    label = { Text(view.label) },
                )
            }
        }
        LazyRow(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(selectionModes) { (label, mode) ->
                FilterChip(
                    selected = mode == selectionMode,
                    onClick = { selectionMode = mode },
                    label = { Text(label) },
                )
            }
        }

        when (selectedView) {
            SampleView.Week -> KalendarWeek(
                selectedDate = today,
                modifier = Modifier.fillMaxSize(),
                state = rememberKalendarWeekState(initialDate = today),
                events = events,
                selectedDates = selectedDates,
                onDateClick = onDateClick,
                onDateRangeSelect = onDateRangeSelect,
                onDateRangeSelectEnd = onDateRangeSelectEnd,
            )

            SampleView.Month -> KalendarMonth(
                selectedDate = today,
                modifier = Modifier.fillMaxSize(),
                state = rememberKalendarMonthState(initialDate = today),
                events = events,
                selectedDates = selectedDates,
                onDateClick = onDateClick,
                onDateRangeSelect = onDateRangeSelect,
                onDateRangeSelectEnd = onDateRangeSelectEnd,
                onEventDrop = onEventDrop,
            )

            SampleView.Year -> KalendarYear(
                selectedDate = today,
                modifier = Modifier.fillMaxSize(),
                state = rememberKalendarYearState(initialDate = today),
                events = events,
                selectedDates = selectedDates,
                onDateClick = onDateClick,
            )

            SampleView.Timeline -> KalendarTimeline(
                selectedDate = today,
                modifier = Modifier.fillMaxSize(),
                state = rememberKalendarTimelineState(
                    initialDate = today,
                    minDate = today.minus(1, DateTimeUnit.YEAR),
                    maxDate = today.plus(1, DateTimeUnit.YEAR),
                ),
                events = events,
                selectedDates = selectedDates,
                onDateClick = onDateClick,
            )

            SampleView.Schedule -> KalendarSchedule(
                selectedDate = today,
                modifier = Modifier.fillMaxSize(),
                events = events,
                onEventClick = { event -> println("Tapped event: ${event.eventName}") },
                onEventTimeChange = onEventTimeChange,
            )

            SampleView.ScheduleWeek -> KalendarScheduleWeek(
                selectedDate = today,
                modifier = Modifier.fillMaxSize(),
                events = events,
                onEventClick = { event -> println("Tapped event: ${event.eventName}") },
                onEventTimeChange = onEventTimeChange,
            )

            SampleView.Agenda -> Kalendar(
                type = KalendarType.Agenda,
                modifier = Modifier.fillMaxSize(),
                config = KalendarConfig(),
            )
        }
    }
}
