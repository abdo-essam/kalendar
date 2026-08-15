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

package com.himanshoe.kalendar

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CollectionInfo
import androidx.compose.ui.semantics.collectionInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEach
import androidx.compose.ui.util.fastForEachIndexed
import com.himanshoe.kalendar.component.KalendarBlockDragAxis
import com.himanshoe.kalendar.component.KalendarBlockLayout
import com.himanshoe.kalendar.component.KalendarHourColumnDivider
import com.himanshoe.kalendar.component.KalendarHourGridLine
import com.himanshoe.kalendar.component.KalendarHourGutter
import com.himanshoe.kalendar.component.KalendarHourLines
import com.himanshoe.kalendar.component.KalendarNavigationHeader
import com.himanshoe.kalendar.component.KalendarNowLine
import com.himanshoe.kalendar.component.resourceColumnWidth
import com.himanshoe.kalendar.foundation.event.KalendarEvent
import com.himanshoe.kalendar.foundation.event.byDateExpandingSpans
import com.himanshoe.kalendar.foundation.schedule.KalendarHourWindow
import com.himanshoe.kalendar.foundation.schedule.KalendarResource
import com.himanshoe.kalendar.foundation.schedule.KalendarScheduleBlock
import com.himanshoe.kalendar.foundation.schedule.dragReleaseTimes
import com.himanshoe.kalendar.foundation.schedule.initialScrollHourOffset
import com.himanshoe.kalendar.foundation.schedule.orderedResources
import com.himanshoe.kalendar.foundation.schedule.resourceBlocks
import com.himanshoe.kalendar.foundation.schedule.resourceShiftForDrag
import com.himanshoe.kalendar.foundation.schedule.shiftedResourceIndex
import com.himanshoe.kalendar.slot.KalendarResourceEventScope
import com.himanshoe.kalendar.state.DEFAULT_NOW_TICK
import com.himanshoe.kalendar.state.KalendarEventLoader
import com.himanshoe.kalendar.state.KalendarLoadStatus
import com.himanshoe.kalendar.state.KalendarViewState
import com.himanshoe.kalendar.state.rememberKalendarEventLoader
import com.himanshoe.kalendar.state.rememberKalendarNowState
import com.himanshoe.kalendar.state.rememberKalendarScheduleState
import com.himanshoe.kalendar.theme.KalendarStrings
import com.himanshoe.kalendar.theme.KalendarTheme
import com.himanshoe.kalendar.theme.kalendarColors
import com.himanshoe.kalendar.theme.kalendarDimensions
import com.himanshoe.kalendar.theme.kalendarShapes
import com.himanshoe.kalendar.theme.kalendarStrings
import com.himanshoe.kalendar.theme.kalendarTypography
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.Month
import kotlin.time.Duration

/**
 * An hourly time-grid calendar whose columns are **resources** rather than days — the view a room
 * booking system, a clinic, a staff roster, or a dispatch board needs. FullCalendar calls the
 * equivalent `resourceTimeGrid`.
 *
 * One day is on screen at a time. Each lane from [resources] is a column, time runs vertically down
 * a shared hour gutter spanning [KalendarViewConfig.scheduleVisibleHours] — the whole day unless a
 * caller narrows it to opening hours — and each lane packs its own overlapping events side by side exactly as
 * [KalendarScheduleWeek] packs a day's. The resource header row stays put while the grid scrolls
 * vertically, and the hour gutter stays put while the lanes scroll horizontally — which they do
 * only once there are more lanes than fit at [minResourceColumnWidth]; below that the lanes divide
 * the width evenly.
 *
 * Events reach their lane through [resourceIdOf], an accessor rather than a member of
 * [KalendarEvent] — that interface is shipped and cannot grow one. It maps an event to **at most
 * one** lane: a booking occupies one room, and a single answer is what makes dragging a block
 * between lanes unambiguous. The accessor is handed the calendar's own event type, so it reads the
 * link straight off the caller's domain type — the `as Booking` this example used to need was the
 * clearest sign the view was missing a type parameter:
 *
 * ```kotlin
 * KalendarResourceView(
 *     resources = listOf(
 *         KalendarResource(id = "room-a", title = "Room A"),
 *         KalendarResource(id = "room-b", title = "Room B"),
 *     ),
 *     events = bookings,
 *     resourceIdOf = { booking -> booking.roomId },
 *     onEventChange = { booking, start, end, resourceId -> move(booking, start, end, resourceId) },
 * )
 * ```
 *
 * The grid is generic in [E], the caller's own event type, inferred from [events]. Use the event-free
 * overload when there are no events to infer from — it is the lane chrome on its own, which is what a
 * board with nothing booked yet still has to draw.
 *
 * Untimed events — those with no [KalendarEvent.startTime] — are not drawn: a lane grid has no
 * all-day row to pin them to.
 *
 * @param E The event type this calendar holds. Any [KalendarEvent] implementation, including a
 *   caller's own domain type.
 * @param resources The lanes to show, left to right by [KalendarResource.order]. Empty renders
 *   [emptyState] in place of the grid.
 * @param modifier [Modifier] applied to the outermost container.
 * @param state Navigation state from [rememberKalendarScheduleState] — pages by single day. Unlike
 *   the Schedule views this one does not swipe between pages, because a horizontal swipe belongs to
 *   the lanes; the header's arrows, today button and jump picker move between days.
 * @param eventLoader Loads events one visible day at a time instead of taking them all up front —
 *   see [rememberKalendarEventLoader], and give it the same [state] this board has. `null`, the
 *   default, is the eager list. See [KalendarMonth] for the full contract.
 * @param events Events for the calendar; only timed ones on the visible day and in a known lane are
 *   rendered. Defaults to the events [eventLoader] holds, or to none.
 * @param resourceIdOf Maps an event to the [KalendarResource.id] of its lane, or `null` for none.
 *   Defaults to [KalendarEvent.calendarId], which is already the interface's "which source does
 *   this belong to" field; pass your own accessor when your events carry the link elsewhere.
 * @param onEventClick Called when an event block is tapped.
 * @param onEventChange When non-null, blocks become draggable: press-and-hold then drag to move an
 *   event in time (snapped to [KalendarViewConfig.scheduleDragSnapMinutes]) and across lanes, or
 *   press-and-hold either the block's top or its bottom edge — a
 *   [KalendarDimensions.eventBlockResizeHandleHeight] strip, narrowed on short blocks so the middle
 *   stays grabbable — and drag to move that edge alone, changing the start or the end while the
 *   other stays put. Called on release with the event, its new start/end, and the id of the lane it
 *   landed in — update your event source to complete the change; the calendar never mutates events
 *   itself.
 * @param config Shared visual/behavioural settings. [KalendarViewConfig.disabledDates] and
 *   [KalendarViewConfig.showAdjacentMonthDates] have no effect here.
 * @param hourHeight Vertical space for one hour of the grid; block heights scale with it. Defaults
 *   to [KalendarDimensions.hourHeight].
 * @param minResourceColumnWidth Narrowest a lane column may become before the lanes stop dividing
 *   the width evenly and start scrolling horizontally. Defaults to
 *   [KalendarDimensions.resourceColumnMinWidth].
 * @param nowIndicatorTick How often the now-indicator's position is recomputed. One timer serves
 *   every lane, and the reading is consumed during layout, so a tick re-places the lines rather
 *   than recomposing the grid.
 * @param resourceHeader Slot for one lane's column header, given the resource. Filled to the
 *   column's width. Defaults to [KalendarResourceDefaults.ResourceHeader].
 * @param hourLabel Slot for one hour-gutter label, given the absolute hour it labels — `0..23`, and
 *   only the hours inside [KalendarViewConfig.scheduleVisibleHours]. Defaults to
 *   [KalendarResourceDefaults.HourLabel] formatted with [KalendarViewConfig.hourLabelFormatter].
 * @param nowIndicator Slot for the current-time line. The grid draws it only when the visible day
 *   is today and owns its vertical offset, so the slot renders the marker alone. Defaults to
 *   [KalendarResourceDefaults.NowIndicator].
 * @param emptyState Slot shown centred in place of the grid when [resources] is empty. Defaults to
 *   [KalendarResourceDefaults.EmptyState].
 * @param loadingContent Slot drawn over the lane grid while the visible day's events have not
 *   arrived yet, given a [KalendarLoadingScope] with the range, its [KalendarLoadStatus] and the
 *   failure if it failed. Overlaid at the grid's own size, so it cannot move the lanes under it.
 *   Defaults to [KalendarLoadingDefaults.Placeholder]. Never drawn when [eventLoader] is `null`,
 *   and never in place of [emptyState] — a board with no lanes has nothing to load events into.
 * @param eventContent Slot for one timed event block, given a [KalendarResourceEventScope] with the
 *   event, its start and end on the rendered day, its duration, its overlap column and its lane. The
 *   grid measures and positions the slot for you and keeps the tap and drag gestures on the cell
 *   around it, so a replacement never loses them. Last, so trailing-lambda syntax lands on it —
 *   this is the view's primary slot. Defaults to [KalendarResourceDefaults.EventBlock].
 */
@Composable
public fun <E : KalendarEvent> KalendarResourceView(
    resources: List<KalendarResource>,
    modifier: Modifier = Modifier,
    state: KalendarViewState = rememberKalendarScheduleState(),
    eventLoader: KalendarEventLoader<E>? = null,
    events: List<E> = eventLoader?.events ?: emptyList(),
    resourceIdOf: (E) -> String? = defaultResourceIdOf,
    onEventClick: (E) -> Unit = {},
    onEventChange: (
        (event: E, newStart: LocalDateTime, newEnd: LocalDateTime, newResourceId: String) -> Unit
    )? = null,
    config: KalendarViewConfig = KalendarViewConfig(),
    hourHeight: Dp = KalendarTheme.dimensions.hourHeight,
    minResourceColumnWidth: Dp = KalendarTheme.dimensions.resourceColumnMinWidth,
    nowIndicatorTick: Duration = DEFAULT_NOW_TICK,
    resourceHeader: @Composable (resource: KalendarResource) -> Unit = {
        KalendarResourceDefaults.ResourceHeader(resource = it)
    },
    hourLabel: @Composable (Int) -> Unit = {
        KalendarResourceDefaults.HourLabel(hour = it, formatter = config.hourLabelFormatter)
    },
    nowIndicator: @Composable () -> Unit = {
        KalendarResourceDefaults.NowIndicator()
    },
    emptyState: @Composable () -> Unit = {
        KalendarResourceDefaults.EmptyState()
    },
    loadingContent: @Composable (KalendarLoadingScope) -> Unit = { loadingScope ->
        KalendarLoadingDefaults.Placeholder(scope = loadingScope)
    },
    eventContent: @Composable (KalendarResourceEventScope<E>) -> Unit = {
        KalendarResourceDefaults.EventBlock(scope = it)
    },
) {
    val colors = kalendarColors()
    val nowState = rememberKalendarNowState(timeSource = state.timeSource, tick = nowIndicatorTick)
    // Remembered so the identity is stable: an inline lambda would be a fresh parameter every
    // recomposition and would stop the grid below from ever skipping.
    val now: () -> LocalDateTime = remember(nowState) { { nowState.value } }
    val today by remember(nowState) { derivedStateOf { nowState.value.date } }
    val lanes = remember(resources) { orderedResources(resources) }
    Column(modifier = modifier.background(config.background ?: SolidColor(colors.background))) {
        KalendarNavigationHeader(
            title = remember(state.visibleDate, config) {
                resourceDayTitle(
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
        if (lanes.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                emptyState()
            }
        } else {
            Box(propagateMinConstraints = true) {
                ResourceGrid(
                    day = state.visibleDate,
                    isToday = state.visibleDate == today,
                    now = now,
                    lanes = lanes,
                    events = events,
                    resourceIdOf = resourceIdOf,
                    onEventClick = onEventClick,
                    onEventChange = onEventChange,
                    config = config,
                    hourHeight = hourHeight,
                    minResourceColumnWidth = minResourceColumnWidth,
                    resourceHeader = resourceHeader,
                    hourLabel = hourLabel,
                    nowIndicator = nowIndicator,
                    eventContent = eventContent,
                )
                KalendarPageLoading(
                    loader = eventLoader,
                    date = state.visibleDate,
                    content = loadingContent,
                )
            }
        }
    }
}

/**
 * A lane grid with no events — the resource columns, the hour gutter and the now-indicator alone,
 * which is what a booking board with nothing booked yet still has to draw.
 *
 * ```kotlin
 * KalendarResourceView(resources = listOf(KalendarResource(id = "room-a", title = "Room A")))
 * ```
 *
 * This overload exists for inference, not for behaviour — see [KalendarMonth]'s event-free overload
 * for why a default value cannot stand in for it. `resourceIdOf`, `onEventClick`, `onEventChange` and
 * `eventContent` are absent rather than defaulted, because a grid with no events can neither route,
 * fire nor draw any of them.
 *
 * @param resources See [KalendarResourceView].
 * @param modifier See [KalendarResourceView].
 * @param state See [KalendarResourceView].
 * @param config See [KalendarResourceView].
 * @param hourHeight See [KalendarResourceView].
 * @param minResourceColumnWidth See [KalendarResourceView].
 * @param nowIndicatorTick See [KalendarResourceView].
 * @param resourceHeader See [KalendarResourceView].
 * @param hourLabel See [KalendarResourceView].
 * @param nowIndicator See [KalendarResourceView].
 * @param emptyState See [KalendarResourceView].
 */
@Composable
public fun KalendarResourceView(
    resources: List<KalendarResource>,
    modifier: Modifier = Modifier,
    state: KalendarViewState = rememberKalendarScheduleState(),
    config: KalendarViewConfig = KalendarViewConfig(),
    hourHeight: Dp = KalendarTheme.dimensions.hourHeight,
    minResourceColumnWidth: Dp = KalendarTheme.dimensions.resourceColumnMinWidth,
    nowIndicatorTick: Duration = DEFAULT_NOW_TICK,
    resourceHeader: @Composable (resource: KalendarResource) -> Unit = {
        KalendarResourceDefaults.ResourceHeader(resource = it)
    },
    hourLabel: @Composable (Int) -> Unit = {
        KalendarResourceDefaults.HourLabel(hour = it, formatter = config.hourLabelFormatter)
    },
    nowIndicator: @Composable () -> Unit = {
        KalendarResourceDefaults.NowIndicator()
    },
    emptyState: @Composable () -> Unit = {
        KalendarResourceDefaults.EmptyState()
    },
) {
    KalendarResourceView<KalendarEvent>(
        resources = resources,
        modifier = modifier,
        state = state,
        config = config,
        hourHeight = hourHeight,
        minResourceColumnWidth = minResourceColumnWidth,
        nowIndicatorTick = nowIndicatorTick,
        resourceHeader = resourceHeader,
        hourLabel = hourLabel,
        nowIndicator = nowIndicator,
        emptyState = emptyState,
    )
}

/**
 * The built-in renderings behind [KalendarResourceView]'s content slots.
 *
 * Every slot defaults to one of these, which also makes them the pieces to reach for when you want
 * to keep the built-in look and only add to it — wrap one in your own container, or fall back to it
 * for the lanes and events you don't render specially:
 *
 * ```kotlin
 * KalendarResourceView(
 *     resources = rooms,
 *     events = bookings,
 *     resourceHeader = { resource ->
 *         Column {
 *             KalendarResourceDefaults.ResourceHeader(resource = resource)
 *             MyOccupancyBar(resource = resource)
 *         }
 *     },
 * )
 * ```
 *
 * All of them draw only: the grid owns positioning, clicks and drag-to-reschedule.
 */
public object KalendarResourceDefaults {

    /**
     * The built-in lane header: the resource's title over a rule in its
     * [KalendarResource.color], centred in the column.
     *
     * @param resource The lane to label.
     * @param modifier [Modifier] applied to the header.
     */
    @Composable
    public fun ResourceHeader(resource: KalendarResource, modifier: Modifier = Modifier) {
        val colors = kalendarColors()
        val dimensions = kalendarDimensions()
        val accent = resource.color ?: colors.eventIndicator
        Column(
            modifier = modifier.padding(dimensions.resourceHeaderPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BasicText(
                text = resource.title,
                style = kalendarTypography().resourceTitle.copy(color = colors.dayContent),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Box(
                modifier = Modifier
                    .padding(top = RESOURCE_HEADER_ACCENT_GAP)
                    .fillMaxWidth(fraction = RESOURCE_HEADER_ACCENT_FRACTION)
                    .height(dimensions.resourceHeaderAccentHeight)
                    .clip(kalendarShapes().eventIndicator)
                    .background(accent),
            )
        }
    }

    /**
     * The built-in timed-event block: the accent stripe beside the event's name, over a fill of the
     * same accent at [KalendarColors.eventBlockAlpha] — or [KalendarColors.eventBlockDraggingAlpha]
     * while [KalendarScheduleEventScope.isDragging]. The accent is the event's own
     * [KalendarEvent.eventColor], falling back to its lane's [KalendarResource.color].
     *
     * The block announces its event, its time range **and its lane**, because a position in a
     * two-dimensional grid says nothing to a screen reader on its own: reading "Standup, 09:00 to
     * 10:00" without "Room A" loses half the information the view exists to show.
     *
     * @param scope The event to draw, as handed to the `eventContent` slot.
     * @param modifier [Modifier] applied to the block.
     */
    @Composable
    public fun EventBlock(scope: KalendarResourceEventScope<*>, modifier: Modifier = Modifier) {
        val colors = kalendarColors()
        val dimensions = kalendarDimensions()
        val strings = kalendarStrings()
        val accent = scope.event.eventColor ?: scope.resource.color ?: colors.eventIndicator
        val backgroundAlpha = if (scope.isDragging) colors.eventBlockDraggingAlpha else colors.eventBlockAlpha
        Row(
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = dimensions.eventBlockGap)
                .clip(kalendarShapes().eventBlock)
                .background(accent.copy(alpha = backgroundAlpha)),
        ) {
            Box(
                modifier = Modifier
                    .width(dimensions.eventBlockAccentWidth)
                    .fillMaxSize()
                    .background(accent),
            )
            BasicText(
                text = scope.event.eventName,
                style = kalendarTypography().eventLabel.copy(color = colors.dayContent),
                maxLines = RESOURCE_EVENT_LABEL_MAX_LINES,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .padding(dimensions.eventBlockPadding)
                    .semantics {
                        contentDescription = resourceEventDescription(
                            strings = strings,
                            scope = scope,
                        )
                    },
            )
        }
    }

    /**
     * The built-in hour-gutter label, shared with the Schedule views. The gutter aligns it to the
     * top of its hour row.
     *
     * @param hour The absolute hour to label, `0..23` — only hours inside
     *   [KalendarViewConfig.scheduleVisibleHours] are ever asked for.
     * @param formatter Turns [hour] into text — [KalendarViewConfig.hourLabelFormatter] by default.
     * @param modifier [Modifier] applied to the label.
     */
    @Composable
    public fun HourLabel(hour: Int, formatter: (Int) -> String, modifier: Modifier = Modifier) {
        KalendarScheduleDefaults.HourLabel(hour = hour, formatter = formatter, modifier = modifier)
    }

    /**
     * The built-in current-time line, shared with the Schedule views: a rule in
     * [KalendarColors.nowIndicator] across a lane. The grid decides whether the visible day is today
     * and where the line sits, so this draws the marker only.
     *
     * @param modifier [Modifier] applied to the line.
     */
    @Composable
    public fun NowIndicator(modifier: Modifier = Modifier) {
        KalendarScheduleDefaults.NowIndicator(modifier = modifier)
    }

    /**
     * The built-in empty state, shown when there are no resources to lay out:
     * [KalendarStrings.resourceEmptyState], which says the grid has no *lanes* rather than
     * [KalendarStrings.agendaEmptyState]'s "no events" — an empty lane grid is a different fact.
     *
     * @param modifier [Modifier] applied to the message.
     */
    @Composable
    public fun EmptyState(modifier: Modifier = Modifier) {
        BasicText(
            text = kalendarStrings().resourceEmptyState,
            style = kalendarTypography().agendaEventTitle.copy(color = kalendarColors().dayLabelContent),
            modifier = modifier,
        )
    }
}

/**
 * What a screen reader announces for a block in the lane grid: the Schedule views' event
 * announcement — name and time range — followed by the lane, so the second dimension of the grid is
 * spoken rather than left to the eye.
 */
private fun resourceEventDescription(
    strings: KalendarStrings,
    scope: KalendarResourceEventScope<*>,
): String = strings.resourceEventAccessibilityLabel(
    scope.event,
    scope.start,
    scope.end,
    scope.resource.title,
)

/**
 * [KalendarEvent.calendarId] as a lane link — the default `resourceIdOf`.
 *
 * Typed on [KalendarEvent] rather than on the view's `E`, and usable as the default for `(E) -> String?`
 * because a function that accepts every event accepts a narrower one — so one shared instance serves
 * every calendar instead of a fresh lambda per event type.
 */
private val defaultResourceIdOf: (KalendarEvent) -> String? = { it.calendarId }

private const val RESOURCE_EVENT_LABEL_MAX_LINES = 2
private const val RESOURCE_HEADER_ACCENT_FRACTION = 0.6f
private val RESOURCE_HEADER_ACCENT_GAP = 2.dp

@Composable
private fun <E : KalendarEvent> ResourceGrid(
    day: LocalDate,
    isToday: Boolean,
    now: () -> LocalDateTime,
    lanes: List<KalendarResource>,
    events: List<E>,
    resourceIdOf: (E) -> String?,
    onEventClick: (E) -> Unit,
    onEventChange: (
        (event: E, newStart: LocalDateTime, newEnd: LocalDateTime, newResourceId: String) -> Unit
    )?,
    config: KalendarViewConfig,
    hourHeight: Dp,
    minResourceColumnWidth: Dp,
    resourceHeader: @Composable (resource: KalendarResource) -> Unit,
    hourLabel: @Composable (Int) -> Unit,
    nowIndicator: @Composable () -> Unit,
    eventContent: @Composable (KalendarResourceEventScope<E>) -> Unit,
) {
    val dimensions = kalendarDimensions()
    val density = LocalDensity.current
    val verticalScrollState = rememberScrollState()
    // One scroll state drives both the header row and the grid body, so the two can never disagree
    // about which lane is under which column.
    val horizontalScrollState = rememberScrollState()
    val eventsByDate = remember(events) { events.byDateExpandingSpans() }
    val window = config.scheduleVisibleHours
    val blocksByResource = remember(eventsByDate, lanes, resourceIdOf, day, window) {
        // The engine buckets `List<KalendarEvent>`, so its accessor has to accept one; every event it
        // is asked about came out of `eventsByDate`, which is the view's own `List<E>`. See
        // asCalendarEvent in KalendarEventTypeBridge.kt.
        resourceBlocks(
            events = eventsByDate[day] ?: emptyList(),
            resources = lanes,
            resourceIdOf = { event -> resourceIdOf(asCalendarEvent(event)) },
            date = day,
            window = window,
        )
    }

    LaunchedEffect(day, window, config.scheduleInitialScrollHour) {
        val hours = initialScrollHourOffset(
            initialScrollHour = config.scheduleInitialScrollHour,
            window = window,
        )
        verticalScrollState.scrollTo(with(density) { (hourHeight * hours).roundToPx() })
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val columnWidth = resourceColumnWidth(
            availableWidth = maxWidth,
            hourGutterWidth = dimensions.hourGutterWidth,
            resourceCount = lanes.size,
            minColumnWidth = minResourceColumnWidth,
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .semantics {
                    collectionInfo = CollectionInfo(rowCount = window.hourCount, columnCount = lanes.size)
                },
        ) {
            Row(modifier = Modifier.fillMaxWidth()) {
                Box(modifier = Modifier.width(dimensions.hourGutterWidth))
                // Offset by the shared state but not itself draggable: the header follows the lanes
                // without registering a second horizontal scrollable over the same pixels.
                Row(modifier = Modifier.horizontalScroll(state = horizontalScrollState, enabled = false)) {
                    lanes.fastForEach { resource ->
                        Box(modifier = Modifier.width(columnWidth), propagateMinConstraints = true) {
                            resourceHeader(resource)
                        }
                    }
                }
            }
            KalendarHourGridLine()
            Row(modifier = Modifier.fillMaxWidth().verticalScroll(verticalScrollState)) {
                KalendarHourGutter(hourHeight = hourHeight, window = window, hourLabel = hourLabel)
                Row(modifier = Modifier.horizontalScroll(horizontalScrollState)) {
                    lanes.fastForEachIndexed { index, resource ->
                        Box(modifier = Modifier.width(columnWidth).height(hourHeight * window.hourCount)) {
                            KalendarHourLines(hourHeight = hourHeight, window = window)
                            KalendarHourColumnDivider()
                            ResourceBlockLayout(
                                blocks = blocksByResource[resource.id] ?: emptyList(),
                                lanes = lanes,
                                laneIndex = index,
                                hourHeight = hourHeight,
                                onEventClick = onEventClick,
                                date = day,
                                window = window,
                                snapMinutes = config.scheduleDragSnapMinutes,
                                onEventChange = onEventChange,
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
        }
    }
}

/**
 * Positions one lane's [blocks] and owns their move/resize gestures — [KalendarBlockLayout] with a
 * lane's answer to a sideways drag.
 */
@Composable
private fun <E : KalendarEvent> ResourceBlockLayout(
    blocks: List<KalendarScheduleBlock>,
    lanes: List<KalendarResource>,
    laneIndex: Int,
    hourHeight: Dp,
    onEventClick: (E) -> Unit,
    date: LocalDate,
    window: KalendarHourWindow,
    snapMinutes: Int,
    onEventChange: (
        (event: E, newStart: LocalDateTime, newEnd: LocalDateTime, newResourceId: String) -> Unit
    )?,
    eventContent: @Composable (KalendarResourceEventScope<E>) -> Unit,
) {
    // Remembered so the identity is stable: an inline adapter would be a fresh parameter every
    // recomposition and would stop every block below from ever skipping.
    val blockClick = remember(onEventClick) { blockEventClickAdapter(onEventClick) }
    KalendarBlockLayout(
        blocks = blocks,
        hourHeight = hourHeight,
        window = window,
        snapMinutes = snapMinutes,
        horizontalShift = ResourceLaneAxis(lanes = lanes, laneIndex = laneIndex),
        onEventClick = blockClick,
        onRelease = onEventChange?.let { handler -> resourceReleaseHandler(date = date, handler = handler) },
        eventContent = { block, isDragging, lane ->
            val scope = remember(block, isDragging, lane) {
                resourceEventScope<E>(block = block, date = date, isDragging = isDragging, resource = lane)
            }
            eventContent(scope)
        },
    )
}

/**
 * Adapts the block layout's release callback to [KalendarResourceView]'s: the layout reports the
 * adjusted block and the lane it landed in, the caller is owed the event, wall-clock times and a
 * lane id. [date] is the visible day, which a lane drag never changes.
 */
private fun <E : KalendarEvent> resourceReleaseHandler(
    date: LocalDate,
    handler: (event: E, newStart: LocalDateTime, newEnd: LocalDateTime, newResourceId: String) -> Unit,
): (KalendarScheduleBlock, KalendarResource) -> Unit = { block, lane ->
    val (newStart, newEnd) = dragReleaseTimes(block = block, targetDate = date)
    handler(asCalendarEvent(block.event), newStart, newEnd, lane.id)
}

/**
 * A sideways drag on a lane column moves the block to a neighbouring lane, and the block slides with
 * it so the drag is visible before it is released.
 *
 * Dragging past either end parks the block in the last lane rather than reporting one that does not
 * exist — see [shiftedResourceIndex].
 */
private data class ResourceLaneAxis(
    val lanes: List<KalendarResource>,
    val laneIndex: Int,
) : KalendarBlockDragAxis<KalendarResource> {

    override fun targetOf(rawDeltaXPx: Float, columnWidthPx: Int): KalendarResource =
        lanes[laneIndex + placementShift(rawDeltaXPx = rawDeltaXPx, columnWidthPx = columnWidthPx)]

    override fun placementShift(rawDeltaXPx: Float, columnWidthPx: Int): Int = shiftedResourceIndex(
        index = laneIndex,
        shift = resourceShiftForDrag(
            rawDeltaXPx = rawDeltaXPx,
            resourceColumnWidthPx = columnWidthPx,
            resourceCount = lanes.size,
        ),
        resourceCount = lanes.size,
    ) - laneIndex
}

/**
 * The scope handed to the `eventContent` slot for [block] as currently rendered — drag adjustments
 * and lane shifts included — on the day it is drawn.
 */
private fun <E : KalendarEvent> resourceEventScope(
    block: KalendarScheduleBlock,
    date: LocalDate,
    isDragging: Boolean,
    resource: KalendarResource,
): KalendarResourceEventScope<E> {
    val (start, end) = dragReleaseTimes(block = block, targetDate = date)
    return ResourceEventScope(
        block = block,
        start = start,
        end = end,
        isDragging = isDragging,
        resource = resource,
    )
}

/**
 * Reads the geometry straight off the block it wraps rather than copying it field by field, so a
 * later addition to [KalendarScheduleBlock] cannot be silently dropped on the way to the slot.
 */
@Immutable
private class ResourceEventScope<out E : KalendarEvent>(
    private val block: KalendarScheduleBlock,
    override val start: LocalDateTime,
    override val end: LocalDateTime,
    override val isDragging: Boolean,
    override val resource: KalendarResource,
) : KalendarResourceEventScope<E> {

    override val event: E get() = asCalendarEvent(block.event)

    override val durationMinutes: Int get() = block.endMinutes - block.startMinutes

    override val overlapColumn: Int get() = block.column

    override val overlapColumns: Int get() = block.columns
}

private fun resourceDayTitle(
    day: LocalDate,
    shortMonthNameFormatter: (Month) -> String,
    dayOfWeekNameFormatter: (DayOfWeek) -> String,
): String {
    val weekday = dayOfWeekNameFormatter(day.dayOfWeek)
    val month = shortMonthNameFormatter(day.month)
    return "$weekday, $month ${day.day}, ${day.year}"
}
