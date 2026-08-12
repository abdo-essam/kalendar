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
import androidx.compose.ui.unit.dp
import com.himanshoe.kalendar.Kalendar
import com.himanshoe.kalendar.KalendarType
import com.himanshoe.kalendar.foundation.component.config.KalendarConfig
import com.himanshoe.kalendar.foundation.event.KalendarEvent
import com.himanshoe.kalendar.view.KalendarMonth
import com.himanshoe.kalendar.view.KalendarTimeline
import com.himanshoe.kalendar.view.KalendarWeek
import com.himanshoe.kalendar.view.KalendarYear
import com.himanshoe.kalendar.view.rememberKalendarMonthState
import com.himanshoe.kalendar.view.rememberKalendarTimelineState
import com.himanshoe.kalendar.view.rememberKalendarWeekState
import com.himanshoe.kalendar.view.rememberKalendarYearState
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
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
    Agenda("Agenda"),
}

@Composable
fun App() {
    val today = remember { Clock.System.todayIn(TimeZone.currentSystemDefault()) }
    var selectedDates by remember { mutableStateOf(setOf(today)) }
    var selectedView by remember { mutableStateOf(SampleView.Timeline) }

    val onDateClick: (date: LocalDate, events: List<KalendarEvent>) -> Unit = { date, events ->
        selectedDates = setOf(date)
        println("Selected Date: $date with events: $events")
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

        when (selectedView) {
            SampleView.Week -> KalendarWeek(
                selectedDate = today,
                modifier = Modifier.fillMaxSize(),
                state = rememberKalendarWeekState(initialDate = today),
                selectedDates = selectedDates,
                onDateClick = onDateClick,
            )

            SampleView.Month -> KalendarMonth(
                selectedDate = today,
                modifier = Modifier.fillMaxSize(),
                state = rememberKalendarMonthState(initialDate = today),
                selectedDates = selectedDates,
                onDateClick = onDateClick,
            )

            SampleView.Year -> KalendarYear(
                selectedDate = today,
                modifier = Modifier.fillMaxSize(),
                state = rememberKalendarYearState(initialDate = today),
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
                selectedDates = selectedDates,
                onDateClick = onDateClick,
            )

            SampleView.Agenda -> Kalendar(
                type = KalendarType.Agenda,
                modifier = Modifier.fillMaxSize(),
                config = KalendarConfig(),
            )
        }
    }
}
