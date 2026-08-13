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

package com.himanshoe.kalendar.view

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.himanshoe.kalendar.foundation.event.KalendarEvent
import com.himanshoe.kalendar.foundation.event.KalendarEvents
import kotlinx.coroutines.delay
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.math.roundToInt
import kotlin.time.Clock

private const val NOW_TICK_MS = 60_000L
private const val EVENT_BLOCK_BACKGROUND_ALPHA = 0.18f
private const val MORNING_SCROLL_TARGET_HOUR = 7

/**
 * An hourly time-grid calendar for a single day, navigable by both arrow-click and swipe.
 *
 * Each page is one day: a scrollable 24-hour grid with timed events (those with a non-null
 * [KalendarEvent.startTime]) rendered as positioned blocks sized by their duration, overlapping
 * events packed side by side, all-day events (no `startTime`) pinned as chips above the grid, and
 * a now-indicator line drawn across the current time on today's page. On first composition the
 * grid starts scrolled to early morning rather than midnight.
 *
 * ```kotlin
 * KalendarSchedule(
 *     selectedDate = today,
 *     events = events,
 *     onEventClick = { event -> openDetails(event) },
 * )
 * ```
 *
 * @param selectedDate The day initially visible when [state] is left at its default.
 * @param modifier [Modifier] applied to the outermost container.
 * @param state Navigation state from [rememberKalendarScheduleState] — pages by single day.
 * @param events Events for the calendar; only those on the visible day are rendered.
 * @param onEventClick Called when an event block or all-day chip is tapped.
 * @param config Shared visual/behavioural settings. [KalendarViewConfig.disabledDates] and
 *   [KalendarViewConfig.showAdjacentMonthDates] have no effect here; arrows, the today button,
 *   month-name formatting, and background apply as usual.
 * @param hourHeight Vertical space for one hour of the grid; block heights scale with it.
 */
@Composable
public fun KalendarSchedule(
    selectedDate: LocalDate,
    modifier: Modifier = Modifier,
    state: KalendarViewState = rememberKalendarScheduleState(selectedDate),
    events: KalendarEvents = emptyList(),
    onEventClick: (KalendarEvent) -> Unit = {},
    config: KalendarViewConfig = KalendarViewConfig(),
    hourHeight: Dp = 64.dp,
) {
    Column(modifier = modifier.background(config.background)) {
        KalendarNavigationHeader(
            title = remember(state.visibleDate) { dayTitle(state.visibleDate, config.monthNameFormatter) },
            state = state,
            showArrows = config.showNavigationArrows,
            showTodayButton = config.showTodayButton,
        )
        HorizontalPager(
            state = state.pagerState,
            modifier = Modifier.fillMaxWidth(),
        ) { page ->
            val day = state.pageToDate(page)
            SchedulePage(
                day = day,
                events = events,
                onEventClick = onEventClick,
                hourHeight = hourHeight,
            )
        }
    }
}

@Composable
private fun SchedulePage(
    day: LocalDate,
    events: KalendarEvents,
    onEventClick: (KalendarEvent) -> Unit,
    hourHeight: Dp,
) {
    val eventsByDate = remember(events) { events.byDateExpandingSpans() }
    val dayEvents = eventsByDate[day] ?: emptyList()
    val allDayEvents = remember(dayEvents) { dayEvents.filter { it.startTime == null } }
    val blocks = remember(dayEvents, day) { scheduleBlocks(events = dayEvents, date = day) }
    val scrollState = rememberScrollState()
    val density = LocalDensity.current

    LaunchedEffect(day) {
        scrollState.scrollTo(with(density) { (hourHeight * MORNING_SCROLL_TARGET_HOUR).roundToPx() })
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (allDayEvents.isNotEmpty()) {
            AllDayRow(events = allDayEvents, onEventClick = onEventClick)
            HorizontalDivider()
        }
        Row(modifier = Modifier.fillMaxWidth().verticalScroll(scrollState)) {
            HourGutter(hourHeight = hourHeight)
            Box(modifier = Modifier.weight(1f).height(hourHeight * HOURS_PER_DAY)) {
                HourLines(hourHeight = hourHeight)
                ScheduleBlockLayout(
                    blocks = blocks,
                    hourHeight = hourHeight,
                    onEventClick = onEventClick,
                )
                NowIndicator(day = day, hourHeight = hourHeight)
            }
        }
    }
}

@Composable
private fun AllDayRow(events: List<KalendarEvent>, onEventClick: (KalendarEvent) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        events.forEach { event ->
            val accent = event.eventColor ?: MaterialTheme.colorScheme.primary
            Text(
                text = event.eventName,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(accent.copy(alpha = EVENT_BLOCK_BACKGROUND_ALPHA))
                    .clickable { onEventClick(event) }
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
    }
}

@Composable
private fun HourGutter(hourHeight: Dp) {
    Column(modifier = Modifier.width(48.dp)) {
        repeat(HOURS_PER_DAY) { hour ->
            Box(modifier = Modifier.height(hourHeight).fillMaxWidth()) {
                Text(
                    text = hourLabel(hour),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.TopCenter),
                )
            }
        }
    }
}

@Composable
private fun HourLines(hourHeight: Dp) {
    Column(modifier = Modifier.fillMaxSize()) {
        repeat(HOURS_PER_DAY) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Box(modifier = Modifier.height(hourHeight - 1.dp))
        }
    }
}

@Composable
private fun ScheduleBlockLayout(
    blocks: List<KalendarScheduleBlock>,
    hourHeight: Dp,
    onEventClick: (KalendarEvent) -> Unit,
) {
    if (blocks.isEmpty()) {
        return
    }
    Layout(
        content = {
            blocks.forEach { block ->
                ScheduleEventBlock(block = block, onEventClick = onEventClick)
            }
        },
        modifier = Modifier.fillMaxSize(),
    ) { measurables, constraints ->
        val minuteHeightPx = hourHeight.toPx() / MINUTES_PER_HOUR
        val placeables = measurables.mapIndexed { index, measurable ->
            val block = blocks[index]
            val columnWidth = constraints.maxWidth / block.columns
            val blockHeight = ((block.endMinutes - block.startMinutes) * minuteHeightPx).roundToInt()
            measurable.measure(Constraints.fixed(width = columnWidth, height = blockHeight))
        }
        layout(constraints.maxWidth, (minuteHeightPx * MINUTES_PER_DAY).roundToInt()) {
            placeables.forEachIndexed { index, placeable ->
                val block = blocks[index]
                val columnWidth = constraints.maxWidth / block.columns
                placeable.place(
                    x = block.column * columnWidth,
                    y = (block.startMinutes * minuteHeightPx).roundToInt(),
                )
            }
        }
    }
}

@Composable
private fun ScheduleEventBlock(block: KalendarScheduleBlock, onEventClick: (KalendarEvent) -> Unit) {
    val accent = block.event.eventColor ?: MaterialTheme.colorScheme.primary
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 1.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(accent.copy(alpha = EVENT_BLOCK_BACKGROUND_ALPHA))
            .clickable { onEventClick(block.event) },
    ) {
        Box(modifier = Modifier.width(3.dp).fillMaxSize().background(accent))
        Text(
            text = block.event.eventName,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun NowIndicator(day: LocalDate, hourHeight: Dp) {
    var now by remember { mutableStateOf(currentLocalDateTime()) }
    LaunchedEffect(day) {
        while (true) {
            delay(NOW_TICK_MS)
            now = currentLocalDateTime()
        }
    }
    if (now.date != day) {
        return
    }
    val minutes = now.hour * MINUTES_PER_HOUR + now.minute
    val offset = hourHeight * (minutes.toFloat() / MINUTES_PER_HOUR)
    HorizontalDivider(
        thickness = 2.dp,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier.padding(top = offset),
    )
}

private fun currentLocalDateTime() =
    Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())

private fun hourLabel(hour: Int): String = "${hour.toString().padStart(2, '0')}:00"

private fun dayTitle(day: LocalDate, monthNameFormatter: (kotlinx.datetime.Month) -> String): String {
    val weekday = day.dayOfWeek.name.take(3).lowercase().replaceFirstChar { it.titlecase() }
    val month = monthNameFormatter(day.month).take(3)
    return "$weekday, $month ${day.dayOfMonth}, ${day.year}"
}
