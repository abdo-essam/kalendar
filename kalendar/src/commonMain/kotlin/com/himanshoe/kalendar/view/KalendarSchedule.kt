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
import kotlinx.datetime.LocalTime
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
 * Each page is one day: a scrollable hour grid with timed events (those with a non-null
 * [KalendarEvent.startTime]) rendered as positioned blocks sized by their duration, overlapping
 * events packed side by side, all-day events (no `startTime`) pinned as chips above the grid, and
 * a now-indicator line drawn across the current time on today's page. On first composition the
 * grid starts scrolled to early morning rather than midnight.
 *
 * The grid shows the whole day unless [KalendarViewConfig.scheduleVisibleHours] narrows it — set
 * that for a business-hours calendar, and the gutter, the rules, the block positions, the
 * now-indicator and every drag follow the window rather than midnight.
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
 *   press-and-hold either the block's top or its bottom edge — a
 *   [KalendarDimensions.eventBlockResizeHandleHeight] strip, narrowed on short blocks so the
 *   middle stays grabbable — and drag to move that edge alone, changing the start or the end while
 *   the other stays put. Called on release with the event and its new start/end — update your
 *   event source to complete the change; the calendar never mutates events itself.
 * @param onEventCreate When non-null, press-and-hold on empty grid space then drag vertically to
 *   sweep out a new time range, previewed as you drag and snapped to
 *   [KalendarViewConfig.scheduleDragSnapMinutes] steps. Dragging in either direction works — the
 *   range spans press to release — and it is never shorter than the grid's minimum block nor outside
 *   [KalendarViewConfig.scheduleVisibleHours]. Called on release with the swept start/end; a press
 *   that lands on an existing block moves or resizes that block instead. `null`, the default,
 *   disables the gesture.
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
 * @param hourLabel Slot for one hour-gutter label, given the absolute hour it labels — `0..23`, and
 *   only the hours inside [KalendarViewConfig.scheduleVisibleHours]. Defaults to
 *   [KalendarScheduleDefaults.HourLabel] formatted with [KalendarViewConfig.hourLabelFormatter].
 * @param nowIndicator Slot for the current-time line. The grid draws it only on today's page, and
 *   only while the clock is inside [KalendarViewConfig.scheduleVisibleHours], and owns its vertical
 *   offset — so the slot renders the marker alone. Defaults to
 *   [KalendarScheduleDefaults.NowIndicator].
 */
@Composable
public fun KalendarSchedule(
    modifier: Modifier = Modifier,
    state: KalendarViewState = rememberKalendarScheduleState(),
    events: KalendarEvents = emptyList(),
    onEventClick: (KalendarEvent) -> Unit = {},
    onEventTimeChange: ((event: KalendarEvent, newStart: LocalDateTime, newEnd: LocalDateTime) -> Unit)? = null,
    onEventCreate: ((start: LocalDateTime, end: LocalDateTime) -> Unit)? = null,
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
                onEventCreate = onEventCreate,
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
 * One column per visible day of the week — seven by default, five when
 * [KalendarViewConfig.visibleDaysOfWeek] hides the weekend — sharing a single hour gutter, itself
 * spanning [KalendarViewConfig.scheduleVisibleHours]. Each column renders that day's timed events as
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
 *   [KalendarSchedule] — including the top and bottom resize edges — and a horizontal drag
 *   additionally moves the event across day columns; the release callback's new start/end carry
 *   the shifted date.
 * @param onEventCreate When non-null, press-and-hold on empty grid space then drag vertically to
 *   sweep out a new time range, exactly as on [KalendarSchedule]. The range belongs to whichever
 *   day column the press landed in; the gesture does not cross columns. `null`, the default,
 *   disables the gesture.
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
 * @param hourLabel Slot for one hour-gutter label, given the absolute hour it labels — `0..23`, and
 *   only the hours inside [KalendarViewConfig.scheduleVisibleHours]. Defaults to
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
    onEventCreate: ((start: LocalDateTime, end: LocalDateTime) -> Unit)? = null,
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
                onEventCreate = onEventCreate,
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
     * @param hour The absolute hour to label, `0..23` — the gutter only ever asks for hours inside
     *   [KalendarViewConfig.scheduleVisibleHours], so a formatter never has to know where the grid
     *   starts.
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
    onEventCreate: ((start: LocalDateTime, end: LocalDateTime) -> Unit)?,
    config: KalendarViewConfig,
    hourHeight: Dp,
    allDayEventContent: @Composable (KalendarEvent) -> Unit,
    hourLabel: @Composable (Int) -> Unit,
    nowIndicator: @Composable () -> Unit,
    dayHeader: @Composable (date: LocalDate, isToday: Boolean) -> Unit,
    eventContent: @Composable (KalendarScheduleEventScope) -> Unit,
) {
    val days = remember(weekStart, config.visibleDaysOfWeek) {
        weekDates(weekStart = weekStart, visibleDaysOfWeek = config.visibleDaysOfWeek)
    }
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
    val window = config.scheduleVisibleHours

    LaunchedEffect(weekStart, window, config.scheduleInitialScrollHour) {
        val hours = initialScrollHourOffset(
            initialScrollHour = config.scheduleInitialScrollHour,
            window = window,
        )
        scrollState.scrollTo(with(density) { (hourHeight * hours).roundToPx() })
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
        KalendarHourGridLine()
        Row(modifier = Modifier.fillMaxWidth().verticalScroll(scrollState)) {
            KalendarHourGutter(hourHeight = hourHeight, window = window, hourLabel = hourLabel)
            days.forEach { day ->
                val blocks = remember(eventsByDate, day, window) {
                    scheduleBlocks(
                        events = eventsByDate[day] ?: emptyList(),
                        date = day,
                        window = window,
                    )
                }
                Box(modifier = Modifier.weight(1f).height(hourHeight * window.hourCount)) {
                    KalendarHourLines(hourHeight = hourHeight, window = window)
                    KalendarHourColumnDivider()
                    ScheduleCreateLayer(
                        blocks = blocks,
                        date = day,
                        hourHeight = hourHeight,
                        snapMinutes = config.scheduleDragSnapMinutes,
                        window = window,
                        onEventCreate = onEventCreate,
                    )
                    ScheduleBlockLayout(
                        blocks = blocks,
                        hourHeight = hourHeight,
                        onEventClick = onEventClick,
                        date = day,
                        window = window,
                        dayColumns = config.visibleDaysOfWeek,
                        snapMinutes = config.scheduleDragSnapMinutes,
                        onEventTimeChange = onEventTimeChange,
                        eventContent = eventContent,
                    )
                    KalendarNowLine(
                        isToday = day == today,
                        now = now,
                        hourHeight = hourHeight,
                        window = window,
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
    onEventCreate: ((start: LocalDateTime, end: LocalDateTime) -> Unit)?,
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
    val window = config.scheduleVisibleHours
    val blocks = remember(dayEvents, day, window) {
        scheduleBlocks(events = dayEvents, date = day, window = window)
    }
    val scrollState = rememberScrollState()
    val density = LocalDensity.current

    LaunchedEffect(day, window, config.scheduleInitialScrollHour) {
        val hours = initialScrollHourOffset(
            initialScrollHour = config.scheduleInitialScrollHour,
            window = window,
        )
        scrollState.scrollTo(with(density) { (hourHeight * hours).roundToPx() })
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (allDayEvents.isNotEmpty()) {
            AllDayRow(
                events = allDayEvents,
                onEventClick = onEventClick,
                chipContent = allDayEventContent,
            )
            KalendarHourGridLine()
        }
        Row(modifier = Modifier.fillMaxWidth().verticalScroll(scrollState)) {
            KalendarHourGutter(hourHeight = hourHeight, window = window, hourLabel = hourLabel)
            Box(modifier = Modifier.weight(1f).height(hourHeight * window.hourCount)) {
                KalendarHourLines(hourHeight = hourHeight, window = window)
                ScheduleCreateLayer(
                    blocks = blocks,
                    date = day,
                    hourHeight = hourHeight,
                    snapMinutes = config.scheduleDragSnapMinutes,
                    window = window,
                    onEventCreate = onEventCreate,
                )
                ScheduleBlockLayout(
                    blocks = blocks,
                    hourHeight = hourHeight,
                    onEventClick = onEventClick,
                    date = day,
                    window = window,
                    snapMinutes = config.scheduleDragSnapMinutes,
                    onEventTimeChange = onEventTimeChange,
                    eventContent = eventContent,
                )
                KalendarNowLine(
                    isToday = isToday,
                    now = now,
                    hourHeight = hourHeight,
                    window = window,
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

/**
 * Which part of a block a long-press grabbed, and so what a drag from there does: [Move] shifts the
 * whole block, [ResizeStart] moves its top edge with the end pinned, [ResizeEnd] the reverse.
 */
internal enum class ScheduleDragMode {
    Move,
    ResizeStart,
    ResizeEnd,
}

/**
 * Live drag state for one block being moved or resized: the grabbed edge plus raw pixel deltas
 * accumulated from the gesture, converted to snapped minute/day deltas at render and release time.
 */
private data class BlockDragState(
    val index: Int,
    val mode: ScheduleDragMode,
    val rawDeltaYPx: Float = 0f,
    val rawDeltaXPx: Float = 0f,
)

/**
 * A block's resize strips never take more than this fraction of its height, so a short block keeps
 * a grabbable middle that still moves rather than resizes.
 */
private const val RESIZE_HANDLE_HEIGHT_DIVISOR = 3f

/**
 * Which edge — if either — a press at [pressYPx] within a block [blockHeightPx] tall grabbed, given
 * a nominal [resizeHandlePx] strip at each edge. The strip is narrowed to
 * `blockHeightPx / [RESIZE_HANDLE_HEIGHT_DIVISOR]` when the block is too short for two full strips
 * plus a middle, so the top strip, the bottom strip and the move region never collide.
 */
internal fun scheduleDragMode(pressYPx: Float, blockHeightPx: Float, resizeHandlePx: Float): ScheduleDragMode {
    if (blockHeightPx <= 0f) {
        return ScheduleDragMode.Move
    }
    val handle = resizeHandlePx.coerceAtMost(blockHeightPx / RESIZE_HANDLE_HEIGHT_DIVISOR)
    return when {
        pressYPx > blockHeightPx - handle -> ScheduleDragMode.ResizeEnd
        pressYPx < handle -> ScheduleDragMode.ResizeStart
        else -> ScheduleDragMode.Move
    }
}

/**
 * Applies a snapped drag [deltaMinutes] to [block] for the grabbed [mode].
 *
 * [ScheduleDragMode.Move] and [ScheduleDragMode.ResizeEnd] are exactly [adjustedScheduleBlock]'s
 * two behaviours; [ScheduleDragMode.ResizeStart] is their mirror — the start moves, the end stays
 * put, and the start can never cross to within less than [MIN_SCHEDULE_BLOCK_MINUTES] of the end
 * nor run past the top of [window].
 */
internal fun adjustedScheduleBlockForDrag(
    block: KalendarScheduleBlock,
    deltaMinutes: Int,
    mode: ScheduleDragMode,
    window: KalendarHourWindow = KalendarHourWindow.FullDay,
): KalendarScheduleBlock = when (mode) {
    ScheduleDragMode.Move -> adjustedScheduleBlock(
        block = block,
        deltaMinutes = deltaMinutes,
        resizing = false,
        window = window,
    )

    ScheduleDragMode.ResizeEnd -> adjustedScheduleBlock(
        block = block,
        deltaMinutes = deltaMinutes,
        resizing = true,
        window = window,
    )

    ScheduleDragMode.ResizeStart -> block.copy(
        startMinutes = (block.startMinutes + deltaMinutes)
            .coerceIn(
                window.startMinutes,
                maxOf(window.startMinutes, block.endMinutes - MIN_SCHEDULE_BLOCK_MINUTES),
            ),
    )
}

/**
 * Positions one day column's [blocks] and owns their move/resize gestures.
 *
 * @param window The visible slice of the day. Positions are measured from its top edge and every
 *   drag clamps to it, so a block can never be dragged out of reach.
 * @param dayColumns The days that have a column, when a horizontal drag may move a block to another
 *   of them — [KalendarScheduleWeek]. `null`, which [KalendarSchedule] passes, means the view shows
 *   one day and a horizontal drag changes nothing.
 */
@Composable
private fun ScheduleBlockLayout(
    blocks: List<KalendarScheduleBlock>,
    hourHeight: Dp,
    onEventClick: (KalendarEvent) -> Unit,
    date: LocalDate,
    window: KalendarHourWindow,
    dayColumns: Set<DayOfWeek>? = null,
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
    val latestWindow = rememberUpdatedState(window)
    val latestDayColumns = rememberUpdatedState(dayColumns)
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
        return adjustedScheduleBlockForDrag(
            block = block,
            deltaMinutes = delta,
            mode = drag.mode,
            window = latestWindow.value,
        )
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
                                val columns = latestDayColumns.value
                                // Columns, not days: on a Mon–Fri week the column right of Friday
                                // is Monday, so the shift is applied in visible-day steps.
                                val targetDate = if (columns == null) {
                                    latestDate.value
                                } else {
                                    visibleDateStep(
                                        from = latestDate.value,
                                        step = dayShiftForDrag(
                                            rawDeltaXPx = state.rawDeltaXPx,
                                            dayColumnWidthPx = latestDayColumnWidthPx.value,
                                            columnCount = columns.size,
                                        ),
                                        visibleDaysOfWeek = columns,
                                    )
                                }
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
        // Clipped to the window, so an event that starts before it is drawn with its head cut off
        // at the top edge instead of being pushed above the grid or dropped.
        val placements = rendered.map { blockPlacement(block = it, window = window) }
        val placeables = measurables.mapIndexed { index, measurable ->
            val columnWidth = constraints.maxWidth / rendered[index].columns
            val blockHeight = (placements[index].heightMinutes * minuteHeightPx).roundToInt()
            measurable.measure(Constraints.fixed(width = columnWidth, height = blockHeight))
        }
        layout(constraints.maxWidth, (minuteHeightPx * window.spanMinutes).roundToInt()) {
            placeables.forEachIndexed { index, placeable ->
                val columnWidth = constraints.maxWidth / rendered[index].columns
                placeable.place(
                    x = rendered[index].column * columnWidth,
                    y = (placements[index].topMinutes * minuteHeightPx).roundToInt(),
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
 * Long-press-then-drag on a block: a press starting within [resizeHandleHeight] of the block's top
 * or bottom edge moves that edge alone, anywhere else moves the whole block — see
 * [scheduleDragMode]. Long-press is required so plain drags still scroll the surrounding grid.
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
            val mode = scheduleDragMode(
                pressYPx = offset.y,
                blockHeightPx = size.height.toFloat(),
                resizeHandlePx = resizeHandleHeight.toPx(),
            )
            onDragStateChange(BlockDragState(index = index, mode = mode))
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
 * A time range swept out on the grid, in minutes from the day's midnight. [endMinutes] may be
 * exactly [MINUTES_PER_DAY], meaning midnight at the end of the day.
 *
 * @property startMinutes Inclusive start of the range.
 * @property endMinutes Exclusive end of the range, always at least [MIN_SCHEDULE_BLOCK_MINUTES]
 *   after [startMinutes].
 */
internal data class ScheduleCreateRange(val startMinutes: Int, val endMinutes: Int)

/**
 * [snappedDragMinutes] read as an absolute wall-clock minute rather than a delta.
 *
 * [px] is measured from the top of the *grid*, which is [KalendarHourWindow.startHour] and not
 * necessarily midnight, so the window's origin is added back before clamping.
 */
private fun snappedWindowMinute(
    px: Float,
    minutePx: Float,
    snapMinutes: Int,
    window: KalendarHourWindow,
): Int = window.clamp(
    window.startMinutes + snappedDragMinutes(rawPx = px, minutePx = minutePx, snapMinutes = snapMinutes),
)

/**
 * The range a create-drag from [anchorPx] to [currentPx] sweeps out: both ends snapped to
 * [snapMinutes] and clamped to [window], ordered so an upward drag reads the same as a downward one,
 * then grown to [MIN_SCHEDULE_BLOCK_MINUTES] if the sweep was shorter. A minimum-length range that
 * would spill past the bottom of the window is pushed back up against it instead of overflowing.
 */
internal fun scheduleCreateRange(
    anchorPx: Float,
    currentPx: Float,
    minutePx: Float,
    snapMinutes: Int,
    window: KalendarHourWindow = KalendarHourWindow.FullDay,
): ScheduleCreateRange {
    val anchor = snappedWindowMinute(
        px = anchorPx,
        minutePx = minutePx,
        snapMinutes = snapMinutes,
        window = window,
    )
    val current = snappedWindowMinute(
        px = currentPx,
        minutePx = minutePx,
        snapMinutes = snapMinutes,
        window = window,
    )
    val start = minOf(anchor, current)
    val end = maxOf(maxOf(anchor, current), start + MIN_SCHEDULE_BLOCK_MINUTES)
    return if (end > window.endMinutes) {
        ScheduleCreateRange(
            startMinutes = window.endMinutes - MIN_SCHEDULE_BLOCK_MINUTES,
            endMinutes = window.endMinutes,
        )
    } else {
        ScheduleCreateRange(startMinutes = start, endMinutes = end)
    }
}

/**
 * The (start, end) date-times reported for a released create-drag on [date]. As in
 * [dragReleaseTimes], an end at exactly [MINUTES_PER_DAY] is midnight of the following day rather
 * than a 23:59 truncation.
 */
internal fun scheduleCreateTimes(
    range: ScheduleCreateRange,
    date: LocalDate,
): Pair<LocalDateTime, LocalDateTime> {
    val start = LocalDateTime(date, minutesToLocalTime(range.startMinutes))
    val end = if (range.endMinutes >= MINUTES_PER_DAY) {
        LocalDateTime(date.plus(1, DateTimeUnit.DAY), LocalTime(hour = 0, minute = 0))
    } else {
        LocalDateTime(date, minutesToLocalTime(range.endMinutes))
    }
    return start to end
}

/**
 * Whether a press at ([xPx], [yPx]) in a day column [widthPx] wide landed on one of [blocks], using
 * the same column-splitting and minute-to-pixel geometry [ScheduleBlockLayout] places them with —
 * [yPx] included, which is measured from the top of [window] rather than from midnight.
 *
 * The create-drag layer sits beneath the blocks and both see the press, so it asks this before
 * arming itself: a long-press that starts on a block belongs to that block's move/resize gesture.
 */
internal fun scheduleBlockAt(
    blocks: List<KalendarScheduleBlock>,
    xPx: Float,
    yPx: Float,
    widthPx: Int,
    minutePx: Float,
    window: KalendarHourWindow = KalendarHourWindow.FullDay,
): KalendarScheduleBlock? {
    if (widthPx <= 0 || minutePx <= 0f) {
        return null
    }
    return blocks.firstOrNull { block ->
        val columnWidth = widthPx.toFloat() / block.columns
        val left = block.column * columnWidth
        val placement = blockPlacement(block = block, window = window)
        xPx >= left &&
            xPx < left + columnWidth &&
            yPx >= placement.topMinutes * minutePx &&
            yPx < (placement.topMinutes + placement.heightMinutes) * minutePx
    }
}

/** Live state for a create-drag: the pressed and current pointer positions down the column. */
private data class CreateDragState(val anchorPx: Float, val currentPx: Float)

/**
 * The empty-space create gesture for one day column, drawn beneath that column's blocks so a press
 * on a block reaches the block first.
 *
 * The pointer-input coroutine launches once and captures its lambdas permanently, so everything it
 * reads at gesture time goes through [rememberUpdatedState] — the same discipline
 * [ScheduleBlockLayout] uses, and for the same stale-capture reason.
 */
@Composable
private fun ScheduleCreateLayer(
    blocks: List<KalendarScheduleBlock>,
    date: LocalDate,
    hourHeight: Dp,
    snapMinutes: Int,
    window: KalendarHourWindow,
    onEventCreate: ((start: LocalDateTime, end: LocalDateTime) -> Unit)?,
) {
    if (onEventCreate == null) {
        return
    }
    var dragState by remember { mutableStateOf<CreateDragState?>(null) }
    val minutePx = with(LocalDensity.current) { hourHeight.toPx() } / MINUTES_PER_HOUR

    val latestBlocks = rememberUpdatedState(blocks)
    val latestDate = rememberUpdatedState(date)
    val latestSnapMinutes = rememberUpdatedState(snapMinutes)
    val latestMinutePx = rememberUpdatedState(minutePx)
    val latestWindow = rememberUpdatedState(window)
    val latestOnEventCreate = rememberUpdatedState(onEventCreate)

    fun rangeOf(state: CreateDragState): ScheduleCreateRange = scheduleCreateRange(
        anchorPx = state.anchorPx,
        currentPx = state.currentPx,
        minutePx = latestMinutePx.value,
        snapMinutes = latestSnapMinutes.value,
        window = latestWindow.value,
    )

    Box(
        modifier = Modifier.fillMaxSize().pointerInput(Unit) {
            detectDragGesturesAfterLongPress(
                onDragStart = { offset ->
                    val onBlock = scheduleBlockAt(
                        blocks = latestBlocks.value,
                        xPx = offset.x,
                        yPx = offset.y,
                        widthPx = size.width,
                        minutePx = latestMinutePx.value,
                        window = latestWindow.value,
                    ) != null
                    dragState = if (onBlock) {
                        null
                    } else {
                        CreateDragState(anchorPx = offset.y, currentPx = offset.y)
                    }
                },
                onDragEnd = {
                    dragState?.let { state ->
                        val (start, end) = scheduleCreateTimes(
                            range = rangeOf(state),
                            date = latestDate.value,
                        )
                        latestOnEventCreate.value(start, end)
                    }
                    dragState = null
                },
                onDragCancel = {
                    dragState = null
                },
                onDrag = { change, dragAmount ->
                    dragState?.let { state ->
                        change.consume()
                        dragState = state.copy(currentPx = state.currentPx + dragAmount.y)
                    }
                },
            )
        },
    ) {
        dragState?.let { state ->
            ScheduleCreatePreview(
                range = rangeOf(state),
                hourHeight = hourHeight,
                minutePx = minutePx,
                window = window,
            )
        }
    }
}

/** The block sketched in place while a create-drag is in progress, positioned within [window]. */
@Composable
private fun ScheduleCreatePreview(
    range: ScheduleCreateRange,
    hourHeight: Dp,
    minutePx: Float,
    window: KalendarHourWindow,
) {
    val colors = kalendarColors()
    val durationMinutes = (range.endMinutes - range.startMinutes).toFloat()
    val topMinutes = range.startMinutes - window.startMinutes
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .offset { IntOffset(x = 0, y = (topMinutes * minutePx).roundToInt()) }
            .height(hourHeight * (durationMinutes / MINUTES_PER_HOUR))
            .padding(horizontal = kalendarDimensions().eventBlockGap)
            .clip(kalendarShapes().eventBlock)
            .background(colors.eventIndicator.copy(alpha = colors.eventBlockDraggingAlpha)),
    )
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
