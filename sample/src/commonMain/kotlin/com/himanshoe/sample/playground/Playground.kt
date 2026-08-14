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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
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
import com.himanshoe.kalendar.state.rememberKalendarSelectionState
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn
import kotlin.time.Clock

internal val TwoPaneMinWidth = 900.dp
private val CalendarPaneWidth = 440.dp
private val ControlsMaxWidth = 640.dp
private val NarrowCalendarHeight = 620.dp
private val PaneSpacing = 20.dp
private val ColumnSpacing = 18.dp

/**
 * The Kalendar playground: a live calendar, the controls that restyle it, and the Kotlin those
 * controls add up to.
 *
 * ## Where the scrolling lives
 *
 * The calendar is never put inside a scroll of the demo's making. Every Kalendar view is built on a
 * lazy layout, and a lazy layout measured against an unbounded height throws, so the calendar always
 * gets a bounded height and does its own scrolling — which is also the only way its long-press drag
 * gestures survive, since a scrollable wrapped around them would consume the drag first.
 *
 * That leaves the demo one vertical scroll per layout. Wide, it is the right-hand column, carrying
 * the controls, the snippet and the footnote while the calendar sits in a fixed-size device frame
 * beside it. Narrow, it is the single page column, with the calendar capped inside it. The snippet
 * pane scrolls only horizontally, so nothing ever competes with either of them.
 *
 * @param modifier [Modifier] for the playground's outermost container. In a scrolling page this
 *   needs a bounded height, since the control column scrolls inside it.
 * @param clipboard How the copy button reaches the system clipboard. Defaults to Compose's own,
 *   which every target except the browser is happy with.
 * @param header Optional content shown above the playground when it stacks into one column, so a
 *   landing page can keep its hero on narrow viewports without nesting two scrolling containers.
 * @param footer Optional content closing the scrolling column, wide or narrow.
 * @param calendarFrame Wraps the live calendar — the GitHub Pages build puts a phone frame here.
 */
@Composable
internal fun Playground(
    modifier: Modifier = Modifier,
    clipboard: PlaygroundClipboard? = null,
    header: @Composable () -> Unit = {},
    footer: @Composable () -> Unit = {},
    calendarFrame: @Composable (content: @Composable () -> Unit) -> Unit = { content ->
        DefaultCalendarFrame(content = content)
    },
) {
    val today = remember { Clock.System.todayIn(TimeZone.currentSystemDefault()) }
    var config by remember { mutableStateOf(PlaygroundConfig()) }
    var events by remember { mutableStateOf(demoEvents(today)) }
    val resources = remember { demoResources() }
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
        val calendar: @Composable (screenModifier: Modifier) -> Unit = { screenModifier ->
            calendarFrame {
                PlaygroundCalendar(
                    config = config,
                    today = today,
                    events = events,
                    resources = resources,
                    selection = selection,
                    onEventTimeChange = onEventTimeChange,
                    onEventDrop = onEventDrop,
                    modifier = screenModifier,
                )
            }
        }
        if (maxWidth >= TwoPaneMinWidth) {
            WidePlayground(
                config = config,
                onConfigChange = { updated -> config = updated },
                code = code,
                clipboard = resolvedClipboard,
                footer = footer,
                calendar = calendar,
            )
        } else {
            NarrowPlayground(
                config = config,
                onConfigChange = { updated -> config = updated },
                code = code,
                clipboard = resolvedClipboard,
                header = header,
                footer = footer,
                calendar = calendar,
            )
        }
    }
}

/**
 * Two panes: the device on the left at a fixed size, everything else scrolling on the right.
 *
 * The snippet used to sit under both panes in a box of its own, which cost the calendar the height
 * it needed to be phone-shaped — the frame came out 250dp wide, narrow enough that the library's own
 * header ran out of room and ellipsized "August 2026" to "A…". Moving the snippet into the right
 * column gives the whole pane height back to the device and leaves the page with one scrollbar.
 */
@Composable
private fun WidePlayground(
    config: PlaygroundConfig,
    onConfigChange: (PlaygroundConfig) -> Unit,
    code: String,
    clipboard: PlaygroundClipboard,
    footer: @Composable () -> Unit,
    calendar: @Composable (screenModifier: Modifier) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxSize().padding(PaneSpacing),
        horizontalArrangement = Arrangement.spacedBy(PaneSpacing),
    ) {
        Box(
            modifier = Modifier.width(CalendarPaneWidth).fillMaxHeight(),
            contentAlignment = Alignment.Center,
        ) {
            // Fills the device screen whatever the view: the calendar's own themed background is
            // painted on this node, so a view that only claimed its natural height would leave the
            // bottom of the phone showing bezel instead of the app.
            calendar(Modifier.fillMaxSize())
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(ColumnSpacing),
            // Centred rather than start-aligned: the cards are capped at a readable measure, and on
            // a wide monitor that cap otherwise left the column hugging the phone with a third of
            // the window empty beside it.
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            PlaygroundControls(
                config = config,
                onConfigChange = onConfigChange,
                modifier = Modifier.widthIn(max = ControlsMaxWidth),
            )
            PlaygroundCodePane(
                code = code,
                clipboard = clipboard,
                modifier = Modifier.widthIn(max = ControlsMaxWidth).padding(horizontal = ControlsInset),
            )
            footer()
        }
    }
}

/**
 * One column, and one scroll.
 *
 * The calendar is capped rather than fixed. `heightIn(max = …)` is what makes both kinds of view
 * behave: the maximum is a real number, so the lazy grid inside every view has something finite to
 * measure against instead of the scrolling column's infinity, while the minimum stays zero, so
 * Month, Week and the date picker come in at the height their width implies rather than sitting in
 * a box two hundred points taller than they are.
 */
@Composable
private fun NarrowPlayground(
    config: PlaygroundConfig,
    onConfigChange: (PlaygroundConfig) -> Unit,
    code: String,
    clipboard: PlaygroundClipboard,
    header: @Composable () -> Unit,
    footer: @Composable () -> Unit,
    calendar: @Composable (screenModifier: Modifier) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(ColumnSpacing),
    ) {
        header()
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = NarrowCalendarHeight)
                .padding(horizontal = ControlsInset),
        ) {
            calendar(
                if (config.view.scrollsInternally) Modifier.fillMaxSize() else Modifier.fillMaxWidth(),
            )
        }
        PlaygroundControls(config = config, onConfigChange = onConfigChange)
        PlaygroundCodePane(
            code = code,
            clipboard = clipboard,
            modifier = Modifier.fillMaxWidth().padding(horizontal = ControlsInset),
        )
        footer()
        Spacer(modifier = Modifier.height(PaneSpacing))
    }
}

@Composable
private fun DefaultCalendarFrame(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .border(width = 1.dp, color = PlaygroundChrome.Border, shape = RoundedCornerShape(20.dp)),
    ) {
        content()
    }
}

/** The line closing the playground's scrolling column, wide or narrow. */
@Composable
internal fun PlaygroundFootnote(text: String) {
    Text(
        text = text,
        color = PlaygroundChrome.DimText,
        style = PlaygroundType.Footnote,
        modifier = Modifier.fillMaxWidth().padding(horizontal = ControlsInset, vertical = 8.dp),
    )
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
