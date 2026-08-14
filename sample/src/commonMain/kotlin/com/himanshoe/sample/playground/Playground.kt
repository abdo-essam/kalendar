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

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.himanshoe.kalendar.foundation.event.BasicKalendarEvent
import com.himanshoe.kalendar.foundation.event.KalendarEvent
import com.himanshoe.kalendar.view.rememberKalendarSelectionState
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn
import kotlin.time.Clock

internal val TwoPaneMinWidth = 900.dp
internal val CodePaneHeight = 260.dp
private val CalendarPaneWidth = 420.dp
private val ControlsMaxWidth = 620.dp
private val NarrowCalendarHeight = 620.dp
private val PaneSpacing = 16.dp

/**
 * The Kalendar playground: a live calendar, the controls that restyle it, and the Kotlin those
 * controls add up to.
 *
 * @param modifier [Modifier] for the playground's outermost container. In a scrolling page this
 *   needs a bounded height, since the control column scrolls inside it.
 * @param clipboard How the copy button reaches the system clipboard. Defaults to Compose's own,
 *   which every target except the browser is happy with.
 * @param header Optional content shown above the playground when it stacks into one column, so a
 *   landing page can keep its hero on narrow viewports without nesting two scrolling containers.
 * @param calendarFrame Wraps the live calendar — the GitHub Pages build puts a phone frame here.
 */
@Composable
internal fun Playground(
    modifier: Modifier = Modifier,
    clipboard: PlaygroundClipboard? = null,
    header: @Composable () -> Unit = {},
    calendarFrame: @Composable (content: @Composable () -> Unit) -> Unit = { content ->
        DefaultCalendarFrame(content = content)
    },
) {
    val today = remember { Clock.System.todayIn(TimeZone.currentSystemDefault()) }
    var config by remember { mutableStateOf(PlaygroundConfig()) }
    var events by remember { mutableStateOf(demoEvents(today)) }
    val selection = rememberKalendarSelectionState(
        mode = config.selection.mode,
        initialSelection = setOf(today),
    )
    val resolvedClipboard = clipboard ?: rememberDefaultClipboard()
    val dark = config.isDark()
    val code = remember(config, dark) { generatePlaygroundCode(config = config, dark = dark) }
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
        events = events.map { event -> if (event in dropped) event.movedTo(newDate) else event }
    }

    BoxWithConstraints(modifier = modifier.background(PlaygroundChrome.Background)) {
        val calendar: @Composable () -> Unit = {
            calendarFrame {
                PlaygroundCalendar(
                    config = config,
                    today = today,
                    events = events,
                    selection = selection,
                    onEventTimeChange = onEventTimeChange,
                    onEventDrop = onEventDrop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        if (maxWidth >= TwoPaneMinWidth) {
            WidePlayground(
                config = config,
                onConfigChange = { updated -> config = updated },
                code = code,
                clipboard = resolvedClipboard,
                calendar = calendar,
            )
        } else {
            NarrowPlayground(
                config = config,
                onConfigChange = { updated -> config = updated },
                code = code,
                clipboard = resolvedClipboard,
                header = header,
                calendar = calendar,
            )
        }
    }
}

@Composable
private fun WidePlayground(
    config: PlaygroundConfig,
    onConfigChange: (PlaygroundConfig) -> Unit,
    code: String,
    clipboard: PlaygroundClipboard,
    calendar: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(PaneSpacing),
        verticalArrangement = Arrangement.spacedBy(PaneSpacing),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().weight(1f),
            horizontalArrangement = Arrangement.spacedBy(PaneSpacing),
        ) {
            Box(
                modifier = Modifier.width(CalendarPaneWidth).fillMaxHeight(),
                contentAlignment = Alignment.Center,
            ) {
                calendar()
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState()),
            ) {
                PlaygroundControls(
                    config = config,
                    onConfigChange = onConfigChange,
                    modifier = Modifier.widthIn(max = ControlsMaxWidth),
                )
            }
        }
        PlaygroundCodePane(
            code = code,
            clipboard = clipboard,
            modifier = Modifier.fillMaxWidth().height(CodePaneHeight),
        )
    }
}

@Composable
private fun NarrowPlayground(
    config: PlaygroundConfig,
    onConfigChange: (PlaygroundConfig) -> Unit,
    code: String,
    clipboard: PlaygroundClipboard,
    header: @Composable () -> Unit,
    calendar: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(PaneSpacing),
    ) {
        header()
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(NarrowCalendarHeight)
                .padding(horizontal = PaneSpacing),
        ) {
            calendar()
        }
        PlaygroundControls(config = config, onConfigChange = onConfigChange)
        PlaygroundCodePane(
            code = code,
            clipboard = clipboard,
            modifier = Modifier
                .fillMaxWidth()
                .height(CodePaneHeight)
                .padding(horizontal = PaneSpacing),
        )
        Spacer(modifier = Modifier.height(PaneSpacing))
    }
}

@Composable
private fun DefaultCalendarFrame(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(20.dp))
            .border(width = 1.dp, color = PlaygroundChrome.Border, shape = RoundedCornerShape(20.dp)),
    ) {
        content()
    }
}

private fun BasicKalendarEvent.movedTo(newDate: LocalDate): BasicKalendarEvent {
    val shiftDays = (newDate.toEpochDays() - date.toEpochDays()).toInt()
    return copy(
        date = newDate,
        endDate = endDate?.plus(value = shiftDays, unit = DateTimeUnit.DAY),
        startTime = startTime?.shiftedBy(shiftDays),
        endTime = endTime?.shiftedBy(shiftDays),
    )
}

private fun LocalDateTime.shiftedBy(days: Int): LocalDateTime =
    LocalDateTime(date.plus(value = days, unit = DateTimeUnit.DAY), time)
