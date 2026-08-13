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
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
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
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.himanshoe.kalendar.foundation.event.KalendarEvent
import com.himanshoe.kalendar.foundation.event.KalendarEvents
import kotlinx.coroutines.delay
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.math.roundToInt
import kotlin.time.Clock

private const val NOW_TICK_MS = 60_000L
private const val EVENT_BLOCK_BACKGROUND_ALPHA = 0.18f
private const val EVENT_BLOCK_DRAGGING_ALPHA = 0.45f
private const val RESIZE_STRIP_DP = 12

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
 * @param onEventTimeChange When non-null, blocks become draggable: press-and-hold then drag to
 *   move an event (snapped to 15-minute steps), or press-and-hold the block's bottom edge and
 *   drag to change its duration. Called on release with the event and its new start/end —
 *   update your event source to complete the change; the calendar never mutates events itself.
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
    onEventTimeChange: ((event: KalendarEvent, newStart: LocalDateTime, newEnd: LocalDateTime) -> Unit)? = null,
    config: KalendarViewConfig = KalendarViewConfig(),
    hourHeight: Dp = 64.dp,
) {
    Column(modifier = modifier.background(config.background)) {
        KalendarNavigationHeader(
            title = remember(state.visibleDate) { dayTitle(state.visibleDate, config.monthNameFormatter) },
            state = state,
            showArrows = config.showNavigationArrows,
            showTodayButton = config.showTodayButton,
            showJumpPicker = config.showJumpPicker,
            monthNameFormatter = config.monthNameFormatter,
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
                onEventTimeChange = onEventTimeChange,
                config = config,
                hourHeight = hourHeight,
            )
        }
    }
}

/**
 * A 7-day hourly time-grid calendar — [KalendarSchedule]'s week variant, navigable by both
 * arrow-click and swipe with one week per page.
 *
 * Seven day columns share a single hour gutter: each column renders that day's timed events as
 * duration-sized blocks (overlaps packed side by side, exactly as in [KalendarSchedule]), a
 * header row shows each day's label and date with today emphasised, all-day events pin as chips
 * above the grid prefixed with their day of month, and the now-indicator line appears on today's
 * column.
 *
 * ```kotlin
 * KalendarScheduleWeek(
 *     selectedDate = today,
 *     events = events,
 *     onEventClick = { event -> openDetails(event) },
 * )
 * ```
 *
 * @param selectedDate The date whose week is initially visible when [state] is left at default.
 * @param modifier [Modifier] applied to the outermost container.
 * @param state Navigation state from [rememberKalendarWeekState] — pages by week.
 * @param events Events for the calendar; only those in the visible week are rendered.
 * @param onEventClick Called when an event block or all-day chip is tapped.
 * @param config Shared visual/behavioural settings. [KalendarViewConfig.disabledDates] and
 *   [KalendarViewConfig.showAdjacentMonthDates] have no effect here.
 * @param hourHeight Vertical space for one hour of the grid; block heights scale with it.
 */
@Composable
public fun KalendarScheduleWeek(
    selectedDate: LocalDate,
    modifier: Modifier = Modifier,
    state: KalendarViewState = rememberKalendarWeekState(selectedDate),
    events: KalendarEvents = emptyList(),
    onEventClick: (KalendarEvent) -> Unit = {},
    onEventTimeChange: ((event: KalendarEvent, newStart: LocalDateTime, newEnd: LocalDateTime) -> Unit)? = null,
    config: KalendarViewConfig = KalendarViewConfig(),
    hourHeight: Dp = 64.dp,
) {
    Column(modifier = modifier.background(config.background)) {
        KalendarNavigationHeader(
            title = remember(state.visibleDate) { weekTitle(state.visibleDate, config.monthNameFormatter) },
            state = state,
            showArrows = config.showNavigationArrows,
            showTodayButton = config.showTodayButton,
            showJumpPicker = config.showJumpPicker,
            monthNameFormatter = config.monthNameFormatter,
        )
        HorizontalPager(
            state = state.pagerState,
            modifier = Modifier.fillMaxWidth(),
        ) { page ->
            val weekStart = state.pageToDate(page)
            ScheduleWeekPage(
                weekStart = weekStart,
                events = events,
                onEventClick = onEventClick,
                onEventTimeChange = onEventTimeChange,
                config = config,
                hourHeight = hourHeight,
            )
        }
    }
}

@Composable
private fun ScheduleWeekPage(
    weekStart: LocalDate,
    events: KalendarEvents,
    onEventClick: (KalendarEvent) -> Unit,
    onEventTimeChange: ((event: KalendarEvent, newStart: LocalDateTime, newEnd: LocalDateTime) -> Unit)?,
    config: KalendarViewConfig,
    hourHeight: Dp,
) {
    val today = remember { todayInSystemTimeZone() }
    val days = remember(weekStart) { weekDates(weekStart) }
    val eventsByDate = remember(events) { events.byDateExpandingSpans() }
    val allDayEvents = remember(days, eventsByDate) {
        days.flatMap { day ->
            (eventsByDate[day] ?: emptyList())
                .filter { it.startTime == null }
                .map { day to it }
        }
    }
    val scrollState = rememberScrollState()
    val density = LocalDensity.current

    LaunchedEffect(weekStart) {
        scrollState.scrollTo(with(density) { (hourHeight * config.scheduleInitialScrollHour).roundToPx() })
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Box(modifier = Modifier.width(48.dp))
            days.forEach { day ->
                WeekDayHeader(
                    day = day,
                    isToday = day == today,
                    config = config,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        if (allDayEvents.isNotEmpty()) {
            WeekAllDayRow(entries = allDayEvents, onEventClick = onEventClick)
        }
        HorizontalDivider()
        Row(modifier = Modifier.fillMaxWidth().verticalScroll(scrollState)) {
            HourGutter(hourHeight = hourHeight, hourLabelFormatter = config.hourLabelFormatter)
            days.forEach { day ->
                val blocks = remember(eventsByDate, day) {
                    scheduleBlocks(events = eventsByDate[day] ?: emptyList(), date = day)
                }
                Box(modifier = Modifier.weight(1f).height(hourHeight * HOURS_PER_DAY)) {
                    HourLines(hourHeight = hourHeight)
                    VerticalDivider(color = kalendarColors().gridLine)
                    ScheduleBlockLayout(
                        blocks = blocks,
                        hourHeight = hourHeight,
                        onEventClick = onEventClick,
                        date = day,
                        allowDayShift = true,
                        snapMinutes = config.scheduleDragSnapMinutes,
                        onEventTimeChange = onEventTimeChange,
                    )
                    NowIndicator(day = day, hourHeight = hourHeight)
                }
            }
        }
    }
}

@Composable
private fun WeekDayHeader(
    day: LocalDate,
    isToday: Boolean,
    config: KalendarViewConfig,
    modifier: Modifier = Modifier,
) {
    val colors = kalendarColors()
    Column(
        modifier = modifier.padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = config.dayOfWeekLabelFormatter(day.dayOfWeek),
            style = MaterialTheme.typography.labelSmall,
            color = colors.dayLabelContent,
        )
        Text(
            text = day.dayOfMonth.toString(),
            style = MaterialTheme.typography.labelLarge,
            color = if (isToday) colors.todayContent else colors.dayContent,
        )
    }
}

@Composable
private fun WeekAllDayRow(
    entries: List<Pair<LocalDate, KalendarEvent>>,
    onEventClick: (KalendarEvent) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        entries.forEach { (day, event) ->
            val accent = event.eventColor ?: kalendarColors().eventIndicator
            Text(
                text = "${day.dayOfMonth} · ${event.eventName}",
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
private fun SchedulePage(
    day: LocalDate,
    events: KalendarEvents,
    onEventClick: (KalendarEvent) -> Unit,
    onEventTimeChange: ((event: KalendarEvent, newStart: LocalDateTime, newEnd: LocalDateTime) -> Unit)?,
    config: KalendarViewConfig,
    hourHeight: Dp,
) {
    val eventsByDate = remember(events) { events.byDateExpandingSpans() }
    val dayEvents = eventsByDate[day] ?: emptyList()
    val allDayEvents = remember(dayEvents) { dayEvents.filter { it.startTime == null } }
    val blocks = remember(dayEvents, day) { scheduleBlocks(events = dayEvents, date = day) }
    val scrollState = rememberScrollState()
    val density = LocalDensity.current

    LaunchedEffect(day) {
        scrollState.scrollTo(with(density) { (hourHeight * config.scheduleInitialScrollHour).roundToPx() })
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (allDayEvents.isNotEmpty()) {
            AllDayRow(events = allDayEvents, onEventClick = onEventClick)
            HorizontalDivider()
        }
        Row(modifier = Modifier.fillMaxWidth().verticalScroll(scrollState)) {
            HourGutter(hourHeight = hourHeight, hourLabelFormatter = config.hourLabelFormatter)
            Box(modifier = Modifier.weight(1f).height(hourHeight * HOURS_PER_DAY)) {
                HourLines(hourHeight = hourHeight)
                ScheduleBlockLayout(
                    blocks = blocks,
                    hourHeight = hourHeight,
                    onEventClick = onEventClick,
                    date = day,
                    snapMinutes = config.scheduleDragSnapMinutes,
                    onEventTimeChange = onEventTimeChange,
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
            val accent = event.eventColor ?: kalendarColors().eventIndicator
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
private fun HourGutter(hourHeight: Dp, hourLabelFormatter: (Int) -> String) {
    Column(modifier = Modifier.width(48.dp)) {
        repeat(HOURS_PER_DAY) { hour ->
            Box(modifier = Modifier.height(hourHeight).fillMaxWidth()) {
                Text(
                    text = hourLabelFormatter(hour),
                    style = MaterialTheme.typography.labelSmall,
                    color = kalendarColors().dayLabelContent,
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
            HorizontalDivider(color = kalendarColors().gridLine)
            Box(modifier = Modifier.height(hourHeight - 1.dp))
        }
    }
}

/**
 * Live drag state for one block being moved or resized: raw pixel deltas accumulated from the
 * gesture, converted to snapped minute/day deltas at render and release time.
 */
private data class BlockDragState(
    val index: Int,
    val resizing: Boolean,
    val rawDeltaYPx: Float = 0f,
    val rawDeltaXPx: Float = 0f,
)

@Composable
private fun ScheduleBlockLayout(
    blocks: List<KalendarScheduleBlock>,
    hourHeight: Dp,
    onEventClick: (KalendarEvent) -> Unit,
    date: LocalDate = todayInSystemTimeZone(),
    allowDayShift: Boolean = false,
    snapMinutes: Int = DEFAULT_SCHEDULE_SNAP_MINUTES,
    onEventTimeChange: ((event: KalendarEvent, newStart: LocalDateTime, newEnd: LocalDateTime) -> Unit)? = null,
) {
    if (blocks.isEmpty()) {
        return
    }
    var dragState by remember(blocks) { mutableStateOf<BlockDragState?>(null) }
    val density = LocalDensity.current
    val minutePx = with(density) { hourHeight.toPx() } / MINUTES_PER_HOUR

    fun snappedMinutes(rawPx: Float): Int {
        val minutes = (rawPx / minutePx).roundToInt()
        return (minutes.toFloat() / snapMinutes).roundToInt() * snapMinutes
    }

    fun adjusted(index: Int): KalendarScheduleBlock {
        val block = blocks[index]
        val drag = dragState?.takeIf { it.index == index } ?: return block
        val delta = snappedMinutes(drag.rawDeltaYPx)
        return if (drag.resizing) {
            val newEnd = (block.endMinutes + delta)
                .coerceIn(block.startMinutes + MIN_SCHEDULE_BLOCK_MINUTES, MINUTES_PER_DAY)
            block.copy(endMinutes = newEnd)
        } else {
            val duration = block.endMinutes - block.startMinutes
            val newStart = (block.startMinutes + delta).coerceIn(0, MINUTES_PER_DAY - duration)
            block.copy(startMinutes = newStart, endMinutes = newStart + duration)
        }
    }

    Layout(
        content = {
            blocks.forEachIndexed { index, block ->
                ScheduleEventBlock(
                    block = block,
                    onEventClick = onEventClick,
                    isDragging = dragState?.index == index,
                    dragModifier = if (onEventTimeChange == null) {
                        Modifier
                    } else {
                        Modifier.blockDragGestures(
                            index = index,
                            onDragStateChange = { dragState = it },
                            currentState = { dragState },
                            onRelease = { state, layoutWidthPx ->
                                val result = adjusted(state.index)
                                val dayShift = if (allowDayShift && layoutWidthPx > 0f) {
                                    (state.rawDeltaXPx / layoutWidthPx).roundToInt()
                                } else {
                                    0
                                }
                                val targetDate = date.plus(dayShift, DateTimeUnit.DAY)
                                onEventTimeChange(
                                    result.event,
                                    LocalDateTime(targetDate, minutesToLocalTime(result.startMinutes)),
                                    LocalDateTime(targetDate, minutesToLocalTime(result.endMinutes)),
                                )
                                dragState = null
                            },
                        )
                    },
                )
            }
        },
        modifier = Modifier.fillMaxSize(),
    ) { measurables, constraints ->
        val minuteHeightPx = hourHeight.toPx() / MINUTES_PER_HOUR
        val rendered = blocks.indices.map { adjusted(it) }
        val placeables = measurables.mapIndexed { index, measurable ->
            val block = rendered[index]
            val columnWidth = constraints.maxWidth / block.columns
            val blockHeight = ((block.endMinutes - block.startMinutes) * minuteHeightPx).roundToInt()
            measurable.measure(Constraints.fixed(width = columnWidth, height = blockHeight))
        }
        layout(constraints.maxWidth, (minuteHeightPx * MINUTES_PER_DAY).roundToInt()) {
            placeables.forEachIndexed { index, placeable ->
                val block = rendered[index]
                val columnWidth = constraints.maxWidth / block.columns
                placeable.place(
                    x = block.column * columnWidth,
                    y = (block.startMinutes * minuteHeightPx).roundToInt(),
                )
            }
        }
    }
}

/**
 * Long-press-then-drag on a block: a press starting in the bottom [RESIZE_STRIP_DP] of the block
 * resizes (moves the end time), anywhere else moves the whole block. Long-press is required so
 * plain drags still scroll the surrounding grid.
 */
private fun Modifier.blockDragGestures(
    index: Int,
    onDragStateChange: (BlockDragState?) -> Unit,
    currentState: () -> BlockDragState?,
    onRelease: (state: BlockDragState, layoutWidthPx: Float) -> Unit,
): Modifier = pointerInput(index) {
    detectDragGesturesAfterLongPress(
        onDragStart = { offset ->
            val resizeStripPx = RESIZE_STRIP_DP.dp.toPx()
            val resizing = offset.y > size.height - resizeStripPx
            onDragStateChange(BlockDragState(index = index, resizing = resizing))
        },
        onDragEnd = {
            currentState()?.let { onRelease(it, size.width.toFloat()) }
        },
        onDragCancel = {
            onDragStateChange(null)
        },
        onDrag = { change, dragAmount ->
            change.consume()
            currentState()?.let { state ->
                onDragStateChange(
                    state.copy(
                        rawDeltaYPx = state.rawDeltaYPx + dragAmount.y,
                        rawDeltaXPx = state.rawDeltaXPx + dragAmount.x,
                    ),
                )
            }
        },
    )
}

private fun minutesToLocalTime(minutes: Int): LocalTime {
    val clamped = minutes.coerceIn(0, MINUTES_PER_DAY - 1)
    return LocalTime(hour = clamped / MINUTES_PER_HOUR, minute = clamped % MINUTES_PER_HOUR)
}

@Composable
private fun ScheduleEventBlock(
    block: KalendarScheduleBlock,
    onEventClick: (KalendarEvent) -> Unit,
    isDragging: Boolean = false,
    dragModifier: Modifier = Modifier,
) {
    val accent = block.event.eventColor ?: kalendarColors().eventIndicator
    val backgroundAlpha = if (isDragging) EVENT_BLOCK_DRAGGING_ALPHA else EVENT_BLOCK_BACKGROUND_ALPHA
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 1.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(accent.copy(alpha = backgroundAlpha))
            .then(dragModifier)
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
        color = kalendarColors().nowIndicator,
        modifier = Modifier.padding(top = offset),
    )
}

private fun currentLocalDateTime() =
    Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())

private fun dayTitle(day: LocalDate, monthNameFormatter: (kotlinx.datetime.Month) -> String): String {
    val weekday = day.dayOfWeek.name.take(3).lowercase().replaceFirstChar { it.titlecase() }
    val month = monthNameFormatter(day.month).take(3)
    return "$weekday, $month ${day.dayOfMonth}, ${day.year}"
}
