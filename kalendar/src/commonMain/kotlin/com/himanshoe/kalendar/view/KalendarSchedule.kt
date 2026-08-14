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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.himanshoe.kalendar.foundation.event.KalendarEvent
import com.himanshoe.kalendar.foundation.event.KalendarEvents
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.Month
import kotlinx.datetime.plus
import kotlin.math.roundToInt
import kotlin.time.Duration

/**
 * The day-of-month prefix [KalendarScheduleWeek]'s shared all-day row puts in front of each chip's
 * name, published to the row's content slot so the default chip can keep showing which day an
 * all-day event belongs to. `null` — the value [KalendarSchedule]'s single-day row leaves in place
 * — means "no prefix".
 */
private val LocalScheduleAllDayPrefix = compositionLocalOf<String?> { null }

/**
 * An hourly time-grid calendar for a single day, navigable by both arrow-click and swipe.
 *
 * Each page is one day: a scrollable 24-hour grid with timed events (those with a non-null
 * [KalendarEvent.startTime]) rendered as positioned blocks sized by their duration, overlapping
 * events packed side by side, all-day events (no `startTime`) pinned as chips above the grid, and
 * a now-indicator line drawn across the current time on today's page. On first composition the
 * grid starts scrolled to early morning rather than midnight.
 *
 * Every drawn piece is a slot: [eventContent], [allDayEventContent], [hourLabel] and
 * [nowIndicator] each default to the matching [KalendarScheduleDefaults] rendering, so you can
 * replace one — an event card showing times, attendees or a status — and leave the rest built-in.
 * Layout, clicks and drag-to-reschedule stay with the grid, so custom content keeps them.
 *
 * ```kotlin
 * KalendarSchedule(
 *     selectedDate = today,
 *     events = events,
 *     onEventClick = { event -> openDetails(event) },
 *     eventContent = { scope -> MyEventCard(title = scope.event.eventName, start = scope.start) },
 * )
 * ```
 *
 * @param modifier [Modifier] applied to the outermost container.
 * @param state Navigation state from [rememberKalendarScheduleState] — pages by single day. Pass
 *   `rememberKalendarScheduleState(initialDate = ...)` to choose the day first shown.
 * @param events Events for the calendar; only those on the visible day are rendered.
 * @param onEventClick Called when an event block or all-day chip is tapped.
 * @param onEventTimeChange When non-null, blocks become draggable: press-and-hold then drag to
 *   move an event (snapped to [KalendarViewConfig.scheduleDragSnapMinutes] steps), or
 *   press-and-hold the block's bottom edge and drag to change its duration. Called on release
 *   with the event and its new start/end — update your event source to complete the change; the
 *   calendar never mutates events itself.
 * @param config Shared visual/behavioural settings. [KalendarViewConfig.disabledDates] and
 *   [KalendarViewConfig.showAdjacentMonthDates] have no effect here; arrows, the today button,
 *   month-name formatting, and background apply as usual.
 * @param hourHeight Vertical space for one hour of the grid; block heights scale with it. Defaults
 *   to [KalendarDimensions.hourHeight].
 * @param nowIndicatorTick How often the now-indicator's position is recomputed. It lives here
 *   rather than on [KalendarViewConfig] because five of the six views never draw one. The reading
 *   is consumed during layout, so a tick costs a re-placement of the line and no recomposition.
 * @param eventContent Slot for one timed event block, given a [KalendarScheduleEventScope] with
 *   the event, its start and end on the rendered day, its duration and its overlap column. The
 *   grid measures and positions the slot for you and keeps the tap and drag gestures on the cell
 *   around it, so a replacement never loses them. Defaults to [KalendarScheduleDefaults.EventBlock].
 * @param allDayEventContent Slot for one all-day chip in the row above the grid; the row applies
 *   [onEventClick]. Defaults to [KalendarScheduleDefaults.AllDayChip].
 * @param hourLabel Slot for one hour-gutter label, given the hour as `0..23`. Defaults to
 *   [KalendarScheduleDefaults.HourLabel] formatted with [KalendarViewConfig.hourLabelFormatter].
 * @param nowIndicator Slot for the current-time line. The grid draws it only on today's page and
 *   owns its vertical offset, so the slot renders the marker alone. Defaults to
 *   [KalendarScheduleDefaults.NowIndicator].
 */
@Composable
public fun KalendarSchedule(
    modifier: Modifier = Modifier,
    state: KalendarViewState = rememberKalendarScheduleState(),
    events: KalendarEvents = emptyList(),
    onEventClick: (KalendarEvent) -> Unit = {},
    onEventTimeChange: ((event: KalendarEvent, newStart: LocalDateTime, newEnd: LocalDateTime) -> Unit)? = null,
    config: KalendarViewConfig = KalendarViewConfig(),
    hourHeight: Dp = KalendarTheme.dimensions.hourHeight,
    nowIndicatorTick: Duration = DEFAULT_NOW_TICK,
    eventContent: @Composable (KalendarScheduleEventScope) -> Unit = {
        KalendarScheduleDefaults.EventBlock(scope = it)
    },
    allDayEventContent: @Composable (KalendarEvent) -> Unit = {
        KalendarScheduleDefaults.AllDayChip(event = it)
    },
    hourLabel: @Composable (Int) -> Unit = {
        KalendarScheduleDefaults.HourLabel(hour = it, formatter = config.hourLabelFormatter)
    },
    nowIndicator: @Composable () -> Unit = {
        KalendarScheduleDefaults.NowIndicator()
    },
) {
    val colors = kalendarColors()
    val now = rememberKalendarNowState(timeSource = state.timeSource, tick = nowIndicatorTick)
    val today by remember(now) { derivedStateOf { now.value.date } }
    Column(modifier = modifier.background(config.background ?: SolidColor(colors.background))) {
        KalendarNavigationHeader(
            title = remember(state.visibleDate, config) {
                dayTitle(
                    day = state.visibleDate,
                    shortMonthNameFormatter = config.shortMonthNameFormatter,
                    dayOfWeekNameFormatter = config.dayOfWeekNameFormatter,
                )
            },
            state = state,
            showArrows = config.showNavigationArrows,
            showTodayButton = config.showTodayButton,
            showJumpPicker = config.showJumpPicker,
            monthNameFormatter = config.monthNameFormatter,
            shortMonthNameFormatter = config.shortMonthNameFormatter,
        )
        HorizontalPager(
            state = state.pagerState,
            modifier = Modifier.fillMaxWidth(),
        ) { page ->
            val day = state.pageToDate(page)
            SchedulePage(
                day = day,
                isToday = day == today,
                now = now,
                events = events,
                onEventClick = onEventClick,
                onEventTimeChange = onEventTimeChange,
                config = config,
                hourHeight = hourHeight,
                allDayEventContent = allDayEventContent,
                hourLabel = hourLabel,
                nowIndicator = nowIndicator,
                eventContent = eventContent,
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
 * Every drawn piece is a slot: [eventContent], [allDayEventContent], [hourLabel], [nowIndicator]
 * and [dayHeader] each default to the matching [KalendarScheduleDefaults] rendering, so you can
 * replace one — an event card showing times, attendees or a status — and leave the rest built-in.
 * Layout, clicks and drag-to-reschedule stay with the grid, so custom content keeps them.
 *
 * ```kotlin
 * KalendarScheduleWeek(
 *     selectedDate = today,
 *     events = events,
 *     onEventClick = { event -> openDetails(event) },
 *     eventContent = { scope -> MyEventCard(title = scope.event.eventName, start = scope.start) },
 * )
 * ```
 *
 * @param modifier [Modifier] applied to the outermost container.
 * @param state Navigation state from [rememberKalendarWeekState] — pages by week. Pass
 *   `rememberKalendarWeekState(initialDate = ...)` to choose the week first shown.
 * @param events Events for the calendar; only those in the visible week are rendered.
 * @param onEventClick Called when an event block or all-day chip is tapped.
 * @param onEventTimeChange When non-null, blocks become draggable exactly as on
 *   [KalendarSchedule], and a horizontal drag additionally moves the event across day columns —
 *   the release callback's new start/end carry the shifted date.
 * @param config Shared visual/behavioural settings. [KalendarViewConfig.disabledDates] and
 *   [KalendarViewConfig.showAdjacentMonthDates] have no effect here.
 * @param hourHeight Vertical space for one hour of the grid; block heights scale with it. Defaults
 *   to [KalendarDimensions.hourHeight].
 * @param nowIndicatorTick How often the now-indicator's position is recomputed. One timer serves
 *   all seven columns, and only the column that is actually today reads it.
 * @param eventContent Slot for one timed event block, given a [KalendarScheduleEventScope] with
 *   the event, its start and end on the rendered day, its duration and its overlap column. The
 *   grid measures and positions the slot for you and keeps the tap and drag gestures on the cell
 *   around it, so a replacement never loses them. Defaults to [KalendarScheduleDefaults.EventBlock].
 * @param allDayEventContent Slot for one all-day chip in the row above the grid; the row applies
 *   [onEventClick]. Defaults to [KalendarScheduleDefaults.AllDayChip] prefixed with the day of
 *   month the chip belongs to, since one row is shared by all seven days.
 * @param hourLabel Slot for one hour-gutter label, given the hour as `0..23`. Defaults to
 *   [KalendarScheduleDefaults.HourLabel] formatted with [KalendarViewConfig.hourLabelFormatter].
 * @param nowIndicator Slot for the current-time line. The grid draws it only on today's column and
 *   owns its vertical offset, so the slot renders the marker alone. Defaults to
 *   [KalendarScheduleDefaults.NowIndicator].
 * @param dayHeader Slot for one day column's header, given the column's date and whether it is
 *   today. Filled to the column's width. Defaults to [KalendarScheduleDefaults.WeekDayHeader]
 *   labelled with [KalendarViewConfig.dayOfWeekLabelFormatter].
 */
@Composable
public fun KalendarScheduleWeek(
    modifier: Modifier = Modifier,
    state: KalendarViewState = rememberKalendarWeekState(),
    events: KalendarEvents = emptyList(),
    onEventClick: (KalendarEvent) -> Unit = {},
    onEventTimeChange: ((event: KalendarEvent, newStart: LocalDateTime, newEnd: LocalDateTime) -> Unit)? = null,
    config: KalendarViewConfig = KalendarViewConfig(),
    hourHeight: Dp = KalendarTheme.dimensions.hourHeight,
    nowIndicatorTick: Duration = DEFAULT_NOW_TICK,
    eventContent: @Composable (KalendarScheduleEventScope) -> Unit = {
        KalendarScheduleDefaults.EventBlock(scope = it)
    },
    allDayEventContent: @Composable (KalendarEvent) -> Unit = {
        KalendarScheduleDefaults.AllDayChip(event = it, prefix = LocalScheduleAllDayPrefix.current)
    },
    hourLabel: @Composable (Int) -> Unit = {
        KalendarScheduleDefaults.HourLabel(hour = it, formatter = config.hourLabelFormatter)
    },
    nowIndicator: @Composable () -> Unit = {
        KalendarScheduleDefaults.NowIndicator()
    },
    dayHeader: @Composable (date: LocalDate, isToday: Boolean) -> Unit = { date, isToday ->
        KalendarScheduleDefaults.WeekDayHeader(
            date = date,
            isToday = isToday,
            dayOfWeekLabelFormatter = config.dayOfWeekLabelFormatter,
        )
    },
) {
    val colors = kalendarColors()
    // One timer for the whole week, and only the "which column is today" answer is read here — a
    // column recomposes when the *date* rolls over, not once a minute per column.
    val now = rememberKalendarNowState(timeSource = state.timeSource, tick = nowIndicatorTick)
    val today by remember(now) { derivedStateOf { now.value.date } }
    Column(modifier = modifier.background(config.background ?: SolidColor(colors.background))) {
        KalendarNavigationHeader(
            title = remember(state.visibleDate, config) {
                weekTitle(state.visibleDate, config.shortMonthNameFormatter)
            },
            state = state,
            showArrows = config.showNavigationArrows,
            showTodayButton = config.showTodayButton,
            showJumpPicker = config.showJumpPicker,
            monthNameFormatter = config.monthNameFormatter,
            shortMonthNameFormatter = config.shortMonthNameFormatter,
        )
        HorizontalPager(
            state = state.pagerState,
            modifier = Modifier.fillMaxWidth(),
        ) { page ->
            val weekStart = state.pageToDate(page)
            ScheduleWeekPage(
                weekStart = weekStart,
                today = today,
                now = now,
                events = events,
                onEventClick = onEventClick,
                onEventTimeChange = onEventTimeChange,
                config = config,
                hourHeight = hourHeight,
                allDayEventContent = allDayEventContent,
                hourLabel = hourLabel,
                nowIndicator = nowIndicator,
                dayHeader = dayHeader,
                eventContent = eventContent,
            )
        }
    }
}

/**
 * The built-in renderings behind [KalendarSchedule]'s and [KalendarScheduleWeek]'s content slots.
 *
 * Every slot defaults to one of these, which also makes them the pieces to reach for when you want
 * to keep the built-in look and only add to it — wrap one in your own container, or fall back to it
 * for the events you don't render specially:
 *
 * ```kotlin
 * KalendarSchedule(
 *     selectedDate = today,
 *     events = events,
 *     eventContent = { scope ->
 *         if (scope.durationMinutes >= MinutesForRichCard) {
 *             MyRichEventCard(scope)
 *         } else {
 *             KalendarScheduleDefaults.EventBlock(scope = scope)
 *         }
 *     },
 * )
 * ```
 *
 * All of them draw only: the grid owns positioning, clicks and drag-to-reschedule.
 */
public object KalendarScheduleDefaults {

    /**
     * The built-in timed-event block: the event's accent stripe beside its name, over a fill of
     * the same accent at [KalendarColors.eventBlockAlpha] — or
     * [KalendarColors.eventBlockDraggingAlpha] while [KalendarScheduleEventScope.isDragging].
     *
     * The block's label carries [KalendarStrings.eventAccessibilityLabel] as its description, so a
     * screen reader announces the event's time range rather than reading the name off the grid with
     * no indication of when it is.
     *
     * @param scope The event to draw, as handed to the `eventContent` slot.
     * @param modifier [Modifier] applied to the block.
     */
    @Composable
    public fun EventBlock(scope: KalendarScheduleEventScope, modifier: Modifier = Modifier) {
        val colors = kalendarColors()
        val dimensions = kalendarDimensions()
        val strings = kalendarStrings()
        val accent = scope.event.eventColor ?: colors.eventIndicator
        val backgroundAlpha = if (scope.isDragging) colors.eventBlockDraggingAlpha else colors.eventBlockAlpha
        Row(
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = dimensions.eventBlockGap)
                .clip(kalendarShapes().eventBlock)
                .background(accent.copy(alpha = backgroundAlpha)),
        ) {
            Box(modifier = Modifier.width(dimensions.eventBlockAccentWidth).fillMaxSize().background(accent))
            BasicText(
                text = scope.event.eventName,
                style = kalendarTypography().eventLabel.copy(color = colors.dayContent),
                maxLines = EVENT_LABEL_MAX_LINES,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .padding(dimensions.eventBlockPadding)
                    .semantics {
                        contentDescription =
                            strings.eventAccessibilityLabel(scope.event, scope.start, scope.end)
                    },
            )
        }
    }

    /**
     * The built-in all-day chip: the event's name on a rounded fill of its accent colour at
     * [KalendarColors.eventBlockAlpha].
     *
     * @param event The all-day event to label.
     * @param modifier [Modifier] applied to the chip.
     * @param prefix Shown before the name, separated by a middot — [KalendarScheduleWeek] passes
     *   the day of month because its seven columns share one all-day row. `null`, the default and
     *   what [KalendarSchedule] uses, shows the name alone.
     */
    @Composable
    public fun AllDayChip(event: KalendarEvent, modifier: Modifier = Modifier, prefix: String? = null) {
        val colors = kalendarColors()
        val strings = kalendarStrings()
        val accent = event.eventColor ?: colors.eventIndicator
        BasicText(
            text = prefix?.let { "$it · ${event.eventName}" } ?: event.eventName,
            style = kalendarTypography().eventLabel.copy(color = colors.dayContent),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = modifier
                .clip(kalendarShapes().eventBlock)
                .background(accent.copy(alpha = colors.eventBlockAlpha))
                .padding(kalendarDimensions().allDayChipPadding)
                .semantics { contentDescription = strings.allDayEventAccessibilityLabel(event) },
        )
    }

    /**
     * The built-in hour-gutter label, in [KalendarTypography.hourLabel]. The gutter aligns it to
     * the top of its hour row.
     *
     * @param hour The hour to label, `0..23`.
     * @param formatter Turns [hour] into text — [KalendarViewConfig.hourLabelFormatter] by default
     *   on both Schedule views.
     * @param modifier [Modifier] applied to the label.
     */
    @Composable
    public fun HourLabel(hour: Int, formatter: (Int) -> String, modifier: Modifier = Modifier) {
        BasicText(
            text = formatter(hour),
            style = kalendarTypography().hourLabel.copy(color = kalendarColors().dayLabelContent),
            modifier = modifier,
        )
    }

    /**
     * The built-in day-column header for [KalendarScheduleWeek]: the day-of-week label above the
     * date, the date drawn in [KalendarColors.todayContent] when it is today.
     *
     * @param date The date the column shows.
     * @param isToday Whether [date] is today, which emphasises its number.
     * @param dayOfWeekLabelFormatter Turns [date]'s day of week into the label above the number —
     *   [KalendarViewConfig.dayOfWeekLabelFormatter] by default.
     * @param modifier [Modifier] applied to the header.
     */
    @Composable
    public fun WeekDayHeader(
        date: LocalDate,
        isToday: Boolean,
        dayOfWeekLabelFormatter: (DayOfWeek) -> String,
        modifier: Modifier = Modifier,
    ) {
        val colors = kalendarColors()
        val typography = kalendarTypography()
        Column(
            modifier = modifier.padding(vertical = WEEK_DAY_HEADER_PADDING),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BasicText(
                text = dayOfWeekLabelFormatter(date.dayOfWeek),
                style = typography.dayOfWeekLabel.copy(color = colors.dayLabelContent),
            )
            BasicText(
                text = date.dayOfMonth.toString(),
                style = typography.weekDayNumber.copy(
                    color = if (isToday) colors.todayContent else colors.dayContent,
                ),
            )
        }
    }

    /**
     * The built-in current-time line: a rule in [KalendarColors.nowIndicator] across the day's
     * column. The grid decides whether the day is today and where the line sits, so this draws the
     * marker only.
     *
     * @param modifier [Modifier] applied to the line.
     */
    @Composable
    public fun NowIndicator(modifier: Modifier = Modifier) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(kalendarDimensions().nowIndicatorThickness)
                .background(kalendarColors().nowIndicator),
        )
    }
}

/** The hour lines and separators the Schedule views rule across the grid. */
@Composable
private fun ScheduleGridLine(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(kalendarDimensions().gridLineThickness)
            .background(kalendarColors().gridLine),
    )
}

/** [ScheduleGridLine]'s vertical twin, separating one day column from the next. */
@Composable
private fun ScheduleColumnDivider(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxHeight()
            .width(kalendarDimensions().gridLineThickness)
            .background(kalendarColors().gridLine),
    )
}

private const val EVENT_LABEL_MAX_LINES = 2
private val WEEK_DAY_HEADER_PADDING = 4.dp

@Composable
private fun ScheduleWeekPage(
    weekStart: LocalDate,
    today: LocalDate,
    now: State<LocalDateTime>,
    events: KalendarEvents,
    onEventClick: (KalendarEvent) -> Unit,
    onEventTimeChange: ((event: KalendarEvent, newStart: LocalDateTime, newEnd: LocalDateTime) -> Unit)?,
    config: KalendarViewConfig,
    hourHeight: Dp,
    allDayEventContent: @Composable (KalendarEvent) -> Unit,
    hourLabel: @Composable (Int) -> Unit,
    nowIndicator: @Composable () -> Unit,
    dayHeader: @Composable (date: LocalDate, isToday: Boolean) -> Unit,
    eventContent: @Composable (KalendarScheduleEventScope) -> Unit,
) {
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
    val dimensions = kalendarDimensions()

    LaunchedEffect(weekStart) {
        scrollState.scrollTo(with(density) { (hourHeight * config.scheduleInitialScrollHour).roundToPx() })
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Box(modifier = Modifier.width(dimensions.hourGutterWidth))
            days.forEach { day ->
                Box(modifier = Modifier.weight(1f), propagateMinConstraints = true) {
                    dayHeader(day, day == today)
                }
            }
        }
        if (allDayEvents.isNotEmpty()) {
            WeekAllDayRow(
                entries = allDayEvents,
                onEventClick = onEventClick,
                chipContent = allDayEventContent,
            )
        }
        ScheduleGridLine()
        Row(modifier = Modifier.fillMaxWidth().verticalScroll(scrollState)) {
            HourGutter(hourHeight = hourHeight, hourLabel = hourLabel)
            days.forEach { day ->
                val blocks = remember(eventsByDate, day) {
                    scheduleBlocks(events = eventsByDate[day] ?: emptyList(), date = day)
                }
                Box(modifier = Modifier.weight(1f).height(hourHeight * HOURS_PER_DAY)) {
                    HourLines(hourHeight = hourHeight)
                    ScheduleColumnDivider()
                    ScheduleBlockLayout(
                        blocks = blocks,
                        hourHeight = hourHeight,
                        onEventClick = onEventClick,
                        date = day,
                        allowDayShift = true,
                        snapMinutes = config.scheduleDragSnapMinutes,
                        onEventTimeChange = onEventTimeChange,
                        eventContent = eventContent,
                    )
                    ScheduleNowIndicator(
                        isToday = day == today,
                        now = now,
                        hourHeight = hourHeight,
                        content = nowIndicator,
                    )
                }
            }
        }
    }
}

@Composable
private fun WeekAllDayRow(
    entries: List<Pair<LocalDate, KalendarEvent>>,
    onEventClick: (KalendarEvent) -> Unit,
    chipContent: @Composable (KalendarEvent) -> Unit,
) {
    val dimensions = kalendarDimensions()
    Row(
        modifier = Modifier.fillMaxWidth().padding(dimensions.allDayRowPadding),
        horizontalArrangement = Arrangement.spacedBy(dimensions.allDayChipSpacing),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        entries.forEach { (day, event) ->
            CompositionLocalProvider(LocalScheduleAllDayPrefix provides day.dayOfMonth.toString()) {
                Box(modifier = Modifier.clickable(role = Role.Button) { onEventClick(event) }) {
                    chipContent(event)
                }
            }
        }
    }
}

@Composable
private fun SchedulePage(
    day: LocalDate,
    isToday: Boolean,
    now: State<LocalDateTime>,
    events: KalendarEvents,
    onEventClick: (KalendarEvent) -> Unit,
    onEventTimeChange: ((event: KalendarEvent, newStart: LocalDateTime, newEnd: LocalDateTime) -> Unit)?,
    config: KalendarViewConfig,
    hourHeight: Dp,
    allDayEventContent: @Composable (KalendarEvent) -> Unit,
    hourLabel: @Composable (Int) -> Unit,
    nowIndicator: @Composable () -> Unit,
    eventContent: @Composable (KalendarScheduleEventScope) -> Unit,
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
            AllDayRow(
                events = allDayEvents,
                onEventClick = onEventClick,
                chipContent = allDayEventContent,
            )
            ScheduleGridLine()
        }
        Row(modifier = Modifier.fillMaxWidth().verticalScroll(scrollState)) {
            HourGutter(hourHeight = hourHeight, hourLabel = hourLabel)
            Box(modifier = Modifier.weight(1f).height(hourHeight * HOURS_PER_DAY)) {
                HourLines(hourHeight = hourHeight)
                ScheduleBlockLayout(
                    blocks = blocks,
                    hourHeight = hourHeight,
                    onEventClick = onEventClick,
                    date = day,
                    snapMinutes = config.scheduleDragSnapMinutes,
                    onEventTimeChange = onEventTimeChange,
                    eventContent = eventContent,
                )
                ScheduleNowIndicator(
                    isToday = isToday,
                    now = now,
                    hourHeight = hourHeight,
                    content = nowIndicator,
                )
            }
        }
    }
}

@Composable
private fun AllDayRow(
    events: List<KalendarEvent>,
    onEventClick: (KalendarEvent) -> Unit,
    chipContent: @Composable (KalendarEvent) -> Unit,
) {
    val dimensions = kalendarDimensions()
    Row(
        modifier = Modifier.fillMaxWidth().padding(dimensions.allDayRowPadding),
        horizontalArrangement = Arrangement.spacedBy(dimensions.allDayChipSpacing),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        events.forEach { event ->
            Box(modifier = Modifier.clickable(role = Role.Button) { onEventClick(event) }) {
                chipContent(event)
            }
        }
    }
}

@Composable
private fun HourGutter(hourHeight: Dp, hourLabel: @Composable (Int) -> Unit) {
    Column(modifier = Modifier.width(kalendarDimensions().hourGutterWidth)) {
        repeat(HOURS_PER_DAY) { hour ->
            Box(
                modifier = Modifier.height(hourHeight).fillMaxWidth(),
                contentAlignment = Alignment.TopCenter,
            ) {
                hourLabel(hour)
            }
        }
    }
}

@Composable
private fun HourLines(hourHeight: Dp) {
    val gridLineThickness = kalendarDimensions().gridLineThickness
    Column(modifier = Modifier.fillMaxSize()) {
        repeat(HOURS_PER_DAY) {
            ScheduleGridLine()
            Box(modifier = Modifier.height(hourHeight - gridLineThickness))
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
    date: LocalDate,
    allowDayShift: Boolean = false,
    snapMinutes: Int = DEFAULT_SCHEDULE_SNAP_MINUTES,
    onEventTimeChange: ((event: KalendarEvent, newStart: LocalDateTime, newEnd: LocalDateTime) -> Unit)? = null,
    eventContent: @Composable (KalendarScheduleEventScope) -> Unit,
) {
    if (blocks.isEmpty()) {
        return
    }
    var dragState by remember { mutableStateOf<BlockDragState?>(null) }
    LaunchedEffect(blocks) { dragState = null }

    val density = LocalDensity.current
    val minutePx = with(density) { hourHeight.toPx() } / MINUTES_PER_HOUR
    val resizeHandleHeight = kalendarDimensions().eventBlockResizeHandleHeight

    val latestBlocks = rememberUpdatedState(blocks)
    val latestDate = rememberUpdatedState(date)
    val latestSnapMinutes = rememberUpdatedState(snapMinutes)
    val latestOnEventTimeChange = rememberUpdatedState(onEventTimeChange)
    var dayColumnWidthPx by remember { mutableStateOf(0) }
    val latestDayColumnWidthPx = rememberUpdatedState(dayColumnWidthPx)

    fun adjusted(index: Int): KalendarScheduleBlock? {
        val block = latestBlocks.value.getOrNull(index) ?: return null
        val drag = dragState?.takeIf { it.index == index } ?: return block
        val delta = snappedDragMinutes(
            rawPx = drag.rawDeltaYPx,
            minutePx = minutePx,
            snapMinutes = latestSnapMinutes.value,
        )
        return adjustedScheduleBlock(block = block, deltaMinutes = delta, resizing = drag.resizing)
    }

    Layout(
        content = {
            blocks.forEachIndexed { index, block ->
                val isDragging = dragState?.index == index
                val rendered = adjusted(index) ?: block
                val scope = remember(rendered, date, isDragging) {
                    scheduleEventScope(block = rendered, date = date, isDragging = isDragging)
                }
                val dragModifier = if (onEventTimeChange == null) {
                    Modifier
                } else {
                    Modifier.blockDragGestures(
                        index = index,
                        resizeHandleHeight = resizeHandleHeight,
                        onDragStateChange = { dragState = it },
                        currentState = { dragState },
                        onRelease = { state ->
                            val result = adjusted(state.index)
                            val handler = latestOnEventTimeChange.value
                            if (result != null && handler != null) {
                                val dayShift = if (allowDayShift) {
                                    dayShiftForDrag(
                                        rawDeltaXPx = state.rawDeltaXPx,
                                        dayColumnWidthPx = latestDayColumnWidthPx.value,
                                    )
                                } else {
                                    0
                                }
                                val targetDate = latestDate.value.plus(dayShift, DateTimeUnit.DAY)
                                val (newStart, newEnd) = dragReleaseTimes(
                                    block = result,
                                    targetDate = targetDate,
                                )
                                handler(result.event, newStart, newEnd)
                            }
                            dragState = null
                        },
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .then(dragModifier)
                        .clickable(role = Role.Button) { onEventClick(block.event) },
                ) {
                    eventContent(scope)
                }
            }
        },
        modifier = Modifier.fillMaxSize().onSizeChanged { dayColumnWidthPx = it.width },
    ) { measurables, constraints ->
        val minuteHeightPx = hourHeight.toPx() / MINUTES_PER_HOUR
        val rendered = blocks.indices.map { adjusted(it) ?: blocks[it] }
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
 * The scope handed to the `eventContent` slot for [block] as currently rendered — drag adjustments
 * included — on the day it is drawn.
 *
 * [dragReleaseTimes] does the minute-range-to-date-time conversion, so an end at midnight becomes
 * 00:00 on the following day here too instead of being truncated to 23:59.
 */
private fun scheduleEventScope(
    block: KalendarScheduleBlock,
    date: LocalDate,
    isDragging: Boolean,
): KalendarScheduleEventScope {
    val (start, end) = dragReleaseTimes(block = block, targetDate = date)
    return ScheduleEventScope(
        event = block.event,
        start = start,
        end = end,
        durationMinutes = block.endMinutes - block.startMinutes,
        isDragging = isDragging,
        overlapColumn = block.column,
        overlapColumns = block.columns,
    )
}

@Immutable
private class ScheduleEventScope(
    override val event: KalendarEvent,
    override val start: LocalDateTime,
    override val end: LocalDateTime,
    override val durationMinutes: Int,
    override val isDragging: Boolean,
    override val overlapColumn: Int,
    override val overlapColumns: Int,
) : KalendarScheduleEventScope

/**
 * Long-press-then-drag on a block: a press starting within [resizeHandleHeight] of the block's
 * bottom edge resizes (moves the end time), anywhere else moves the whole block. Long-press is
 * required so plain drags still scroll the surrounding grid.
 *
 * This is not a composable, so [resizeHandleHeight] is resolved from
 * [KalendarDimensions.eventBlockResizeHandleHeight] by the caller and passed in; it keys the
 * pointer input so a theme change re-arms the detector rather than leaving a stale strip height.
 *
 * The lambdas passed here are captured once when the pointer-input coroutine first launches and
 * never refreshed, so callers must route them through [rememberUpdatedState]-backed reads — see
 * [ScheduleBlockLayout].
 */
private fun Modifier.blockDragGestures(
    index: Int,
    resizeHandleHeight: Dp,
    onDragStateChange: (BlockDragState?) -> Unit,
    currentState: () -> BlockDragState?,
    onRelease: (state: BlockDragState) -> Unit,
): Modifier = pointerInput(index, resizeHandleHeight) {
    detectDragGesturesAfterLongPress(
        onDragStart = { offset ->
            val resizeStripPx = resizeHandleHeight.toPx()
            val resizing = offset.y > size.height - resizeStripPx
            onDragStateChange(BlockDragState(index = index, resizing = resizing))
        },
        onDragEnd = {
            currentState()?.let(onRelease)
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

/**
 * The current-time line on the column that is today.
 *
 * [now] is deliberately read inside [Modifier.offset]'s lambda rather than in composition: the
 * position is then recomputed during placement, so a minute passing re-places one line instead of
 * recomposing this composable, its slot content, and everything the read would invalidate above it.
 */
@Composable
private fun ScheduleNowIndicator(
    isToday: Boolean,
    now: State<LocalDateTime>,
    hourHeight: Dp,
    content: @Composable () -> Unit,
) {
    if (!isToday) {
        return
    }
    Box(
        modifier = Modifier.fillMaxWidth().offset {
            val minutes = now.value.hour * MINUTES_PER_HOUR + now.value.minute
            IntOffset(x = 0, y = (hourHeight.toPx() * minutes / MINUTES_PER_HOUR).roundToInt())
        },
    ) {
        content()
    }
}

private fun dayTitle(
    day: LocalDate,
    shortMonthNameFormatter: (Month) -> String,
    dayOfWeekNameFormatter: (DayOfWeek) -> String,
): String {
    val weekday = dayOfWeekNameFormatter(day.dayOfWeek)
    val month = shortMonthNameFormatter(day.month)
    return "$weekday, $month ${day.dayOfMonth}, ${day.year}"
}
