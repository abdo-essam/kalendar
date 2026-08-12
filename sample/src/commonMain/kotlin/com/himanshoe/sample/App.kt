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


import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.himanshoe.kalendar.view.KalendarTimeline
import com.himanshoe.kalendar.view.rememberKalendarTimelineState
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn
import kotlin.time.Clock

@Composable
fun App() {
    val today = remember { Clock.System.todayIn(TimeZone.currentSystemDefault()) }
    var selectedDates by remember { mutableStateOf(setOf(today)) }
    val state = rememberKalendarTimelineState(
        initialDate = today,
        minDate = today.minus(1, DateTimeUnit.YEAR),
        maxDate = today.plus(1, DateTimeUnit.YEAR),
    )

    Column(modifier = Modifier.fillMaxSize().background(Color.White)) {
        KalendarTimeline(
            selectedDate = today,
            modifier = Modifier.fillMaxSize(),
            state = state,
            selectedDates = selectedDates,
            onDateClick = { date, events ->
                selectedDates = setOf(date)
                println("Selected Date: $date with events: $events")
            },
        )
    }
}
