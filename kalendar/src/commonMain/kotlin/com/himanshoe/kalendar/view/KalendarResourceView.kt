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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
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
import androidx.compose.ui.semantics.CollectionInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.collectionInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.himanshoe.kalendar.foundation.event.KalendarEvent
import com.himanshoe.kalendar.foundation.event.KalendarEvents
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.Month
import kotlin.math.roundToInt
import kotlin.time.Duration

/**
 * An hourly time-grid calendar whose columns are **resources** rather than days — the view a room
 * booking system, a clinic, a staff roster, or a dispatch board needs. FullCalendar calls the
 * equivalent `resourceTimeGrid`.
 *
 * One day is on screen at a time. Each lane from [resources] is a column, time runs vertically down
 * a shared hour gutter, and each lane packs its own overlapping events side by side exactly as
 * [KalendarScheduleWeek] packs a day's. The resource header row stays put while the grid scrolls
 * vertically, and the hour gutter stays put while the lanes scroll horizontally — which they do
 * only once there are more lanes than fit at [minResourceColumnWidth]; below that the lanes divide
 * the width evenly.
 *
 * Events reach their lane through [resourceIdOf], an accessor rather than a member of
 * [KalendarEvent] — that interface is shipped and cannot grow one. It maps an event to **at most
 * one** lane: a booking occupies one room, and a single answer is what makes dragging a block
 * between lanes unambiguous.
 *
 * ```kotlin
 * KalendarResourceView(
 *     resources = listOf(
 *         KalendarResource(id = "room-a", title = "Room A"),
 *         KalendarResource(id = "room-b", title = "Room B"),
 *     ),
 *     events = bookings,
 *     resourceIdOf = { event -> (event as Booking).roomId },
 *     onEventChange = { event, start, end, resourceId -> move(event, start, end, resourceId) },
 * )
 * ```
 *
 * Untimed events — those with no [KalendarEvent.startTime] — are not drawn: a lane grid has no
 * all-day row to pin them to.
 *
 * @param resources The lanes to show, left to right by [KalendarResource.order]. Empty renders
 *   [emptyState] in place of the grid.
 * @param modifier [Modifier] applied to the outermost container.
 * @param state Navigation state from [rememberKalendarScheduleState] — pages by single day. Unlike
 *   the Schedule views this one does not swipe between pages, because a horizontal swipe belongs to
 *   the lanes; the header's arrows, today button and jump picker move between days.
 * @param events Events for the calendar; only timed ones on the visible day and in a known lane are
 *   rendered.
 * @param resourceIdOf Maps an event to the [KalendarResource.id] of its lane, or `null` for none.
 *   Defaults to [KalendarEvent.calendarId], which is already the interface's "which source does
 *   this belong to" field; pass your own accessor when your events carry the link elsewhere.
 * @param onEventClick Called when an event block is tapped.
 * @param onEventChange When non-null, blocks become draggable: press-and-hold then drag to move an
 *   event in time (snapped to [KalendarViewConfig.scheduleDragSnapMinutes]) and across lanes, or
 *   press-and-hold the block's bottom edge and drag to change its duration. Called on release with
 *   the event, its new start/end, and the id of the lane it landed in — update your event source to
 *   complete the change; the calendar never mutates events itself.
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
 * @param eventContent Slot for one timed event block, given a [KalendarResourceEventScope] with the
 *   event, its start and end on the rendered day, its duration, its overlap column and its lane. The
 *   grid measures and positions the slot for you and keeps the tap and drag gestures on the cell
 *   around it, so a replacement never loses them. Defaults to
 *   [KalendarResourceDefaults.EventBlock].
 * @param hourLabel Slot for one hour-gutter label, given the hour as `0..23`. Defaults to
 *   [KalendarResourceDefaults.HourLabel] formatted with [KalendarViewConfig.hourLabelFormatter].
 * @param nowIndicator Slot for the current-time line. The grid draws it only when the visible day
 *   is today and owns its vertical offset, so the slot renders the marker alone. Defaults to
 *   [KalendarResourceDefaults.NowIndicator].
 * @param emptyState Slot shown centred in place of the grid when [resources] is empty. Defaults to
 *   [KalendarResourceDefaults.EmptyState].
 */
@Composable
public fun KalendarResourceView(
    resources: List<KalendarResource>,
    modifier: Modifier = Modifier,
    state: KalendarViewState = rememberKalendarScheduleState(),
    events: KalendarEvents = emptyList(),
    resourceIdOf: (KalendarEvent) -> String? = defaultResourceIdOf,
    onEventClick: (KalendarEvent) -> Unit = {},
    onEventChange: (
        (event: KalendarEvent, newStart: LocalDateTime, newEnd: LocalDateTime, newResourceId: String) -> Unit
    )? = null,
    config: KalendarViewConfig = KalendarViewConfig(),
    hourHeight: Dp = KalendarTheme.dimensions.hourHeight,
    minResourceColumnWidth: Dp = KalendarTheme.dimensions.resourceColumnMinWidth,
    nowIndicatorTick: Duration = DEFAULT_NOW_TICK,
    resourceHeader: @Composable (resource: KalendarResource) -> Unit = {
        KalendarResourceDefaults.ResourceHeader(resource = it)
    },
    eventContent: @Composable (KalendarResourceEventScope) -> Unit = {
        KalendarResourceDefaults.EventBlock(scope = it)
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
    val colors = kalendarColors()
    val now = rememberKalendarNowState(timeSource = state.timeSource, tick = nowIndicatorTick)
    val today by remember(now) { derivedStateOf { now.value.date } }
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
        }
    }
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
    public fun EventBlock(scope: KalendarResourceEventScope, modifier: Modifier = Modifier) {
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
     * @param hour The hour to label, `0..23`.
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
    scope: KalendarResourceEventScope,
): String = strings.resourceEventAccessibilityLabel(
    scope.event,
    scope.start,
    scope.end,
    scope.resource.title,
)

/** [KalendarEvent.calendarId] as a lane link — the default `resourceIdOf`. */
private val defaultResourceIdOf: (KalendarEvent) -> String? = { it.calendarId }

private const val RESOURCE_EVENT_LABEL_MAX_LINES = 2
private const val RESOURCE_HEADER_ACCENT_FRACTION = 0.6f
private val RESOURCE_HEADER_ACCENT_GAP = 2.dp

@Composable
private fun ResourceGrid(
    day: LocalDate,
    isToday: Boolean,
    now: State<LocalDateTime>,
    lanes: List<KalendarResource>,
    events: KalendarEvents,
    resourceIdOf: (KalendarEvent) -> String?,
    onEventClick: (KalendarEvent) -> Unit,
    onEventChange: (
        (event: KalendarEvent, newStart: LocalDateTime, newEnd: LocalDateTime, newResourceId: String) -> Unit
    )?,
    config: KalendarViewConfig,
    hourHeight: Dp,
    minResourceColumnWidth: Dp,
    resourceHeader: @Composable (resource: KalendarResource) -> Unit,
    hourLabel: @Composable (Int) -> Unit,
    nowIndicator: @Composable () -> Unit,
    eventContent: @Composable (KalendarResourceEventScope) -> Unit,
) {
    val dimensions = kalendarDimensions()
    val density = LocalDensity.current
    val verticalScrollState = rememberScrollState()
    // One scroll state drives both the header row and the grid body, so the two can never disagree
    // about which lane is under which column.
    val horizontalScrollState = rememberScrollState()
    val eventsByDate = remember(events) { events.byDateExpandingSpans() }
    val blocksByResource = remember(eventsByDate, lanes, resourceIdOf, day) {
        resourceBlocks(
            events = eventsByDate[day] ?: emptyList(),
            resources = lanes,
            resourceIdOf = resourceIdOf,
            date = day,
        )
    }

    LaunchedEffect(day) {
        verticalScrollState.scrollTo(with(density) { (hourHeight * config.scheduleInitialScrollHour).roundToPx() })
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
                    collectionInfo = CollectionInfo(rowCount = HOURS_PER_DAY, columnCount = lanes.size)
                },
        ) {
            Row(modifier = Modifier.fillMaxWidth()) {
                Box(modifier = Modifier.width(dimensions.hourGutterWidth))
                // Offset by the shared state but not itself draggable: the header follows the lanes
                // without registering a second horizontal scrollable over the same pixels.
                Row(modifier = Modifier.horizontalScroll(state = horizontalScrollState, enabled = false)) {
                    lanes.forEach { resource ->
                        Box(modifier = Modifier.width(columnWidth), propagateMinConstraints = true) {
                            resourceHeader(resource)
                        }
                    }
                }
            }
            ResourceGridLine()
            Row(modifier = Modifier.fillMaxWidth().verticalScroll(verticalScrollState)) {
                ResourceHourGutter(hourHeight = hourHeight, hourLabel = hourLabel)
                Row(modifier = Modifier.horizontalScroll(horizontalScrollState)) {
                    lanes.forEachIndexed { index, resource ->
                        Box(modifier = Modifier.width(columnWidth).height(hourHeight * HOURS_PER_DAY)) {
                            ResourceHourLines(hourHeight = hourHeight)
                            ResourceColumnDivider()
                            ResourceBlockLayout(
                                blocks = blocksByResource[resource.id] ?: emptyList(),
                                lanes = lanes,
                                laneIndex = index,
                                hourHeight = hourHeight,
                                onEventClick = onEventClick,
                                date = day,
                                snapMinutes = config.scheduleDragSnapMinutes,
                                onEventChange = onEventChange,
                                eventContent = eventContent,
                            )
                            ResourceNowIndicator(
                                isToday = isToday,
                                now = now,
                                hourHeight = hourHeight,
                                content = nowIndicator,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ResourceHourGutter(hourHeight: Dp, hourLabel: @Composable (Int) -> Unit) {
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
private fun ResourceHourLines(hourHeight: Dp) {
    val gridLineThickness = kalendarDimensions().gridLineThickness
    Column(modifier = Modifier.fillMaxSize()) {
        repeat(HOURS_PER_DAY) {
            ResourceGridLine()
            Box(modifier = Modifier.height(hourHeight - gridLineThickness))
        }
    }
}

/** The hour lines and separators the lane grid rules across itself. */
@Composable
private fun ResourceGridLine(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(kalendarDimensions().gridLineThickness)
            .background(kalendarColors().gridLine),
    )
}

/** [ResourceGridLine]'s vertical twin, separating one lane column from the next. */
@Composable
private fun ResourceColumnDivider(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxHeight()
            .width(kalendarDimensions().gridLineThickness)
            .background(kalendarColors().gridLine),
    )
}

/**
 * Live drag state for one block being moved or resized: raw pixel deltas accumulated from the
 * gesture, converted to snapped minute deltas and whole lane shifts at render and release time.
 */
private data class ResourceDragState(
    val index: Int,
    val resizing: Boolean,
    val rawDeltaYPx: Float = 0f,
    val rawDeltaXPx: Float = 0f,
)

@Composable
private fun ResourceBlockLayout(
    blocks: List<KalendarScheduleBlock>,
    lanes: List<KalendarResource>,
    laneIndex: Int,
    hourHeight: Dp,
    onEventClick: (KalendarEvent) -> Unit,
    date: LocalDate,
    snapMinutes: Int,
    onEventChange: (
        (event: KalendarEvent, newStart: LocalDateTime, newEnd: LocalDateTime, newResourceId: String) -> Unit
    )?,
    eventContent: @Composable (KalendarResourceEventScope) -> Unit,
) {
    if (blocks.isEmpty()) {
        return
    }
    var dragState by remember { mutableStateOf<ResourceDragState?>(null) }
    LaunchedEffect(blocks) { dragState = null }

    val density = LocalDensity.current
    val minutePx = with(density) { hourHeight.toPx() } / MINUTES_PER_HOUR
    val resizeHandleHeight = kalendarDimensions().eventBlockResizeHandleHeight

    // A pointerInput coroutine captures the lambdas it was first composed with and never refreshes
    // them, so everything the gesture reads goes through rememberUpdatedState.
    val latestBlocks = rememberUpdatedState(blocks)
    val latestLanes = rememberUpdatedState(lanes)
    val latestLaneIndex = rememberUpdatedState(laneIndex)
    val latestDate = rememberUpdatedState(date)
    val latestSnapMinutes = rememberUpdatedState(snapMinutes)
    val latestOnEventChange = rememberUpdatedState(onEventChange)
    // The *column's* measured width, never the dragged block's: a block that overlaps another is a
    // fraction of the column wide, and dividing by that over-shifts the lane.
    var laneColumnWidthPx by remember { mutableStateOf(0) }
    val latestLaneColumnWidthPx = rememberUpdatedState(laneColumnWidthPx)

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

    fun laneShift(index: Int): Int {
        val drag = dragState?.takeIf { it.index == index && !it.resizing } ?: return 0
        val shift = resourceShiftForDrag(
            rawDeltaXPx = drag.rawDeltaXPx,
            resourceColumnWidthPx = latestLaneColumnWidthPx.value,
            resourceCount = latestLanes.value.size,
        )
        return shiftedResourceIndex(
            index = latestLaneIndex.value,
            shift = shift,
            resourceCount = latestLanes.value.size,
        ) - latestLaneIndex.value
    }

    Layout(
        content = {
            blocks.forEachIndexed { index, block ->
                val isDragging = dragState?.index == index
                val rendered = adjusted(index) ?: block
                val hoveredLane = lanes[shiftedResourceIndex(laneIndex, laneShift(index), lanes.size)]
                val scope = remember(rendered, date, isDragging, hoveredLane) {
                    resourceEventScope(
                        block = rendered,
                        date = date,
                        isDragging = isDragging,
                        resource = hoveredLane,
                    )
                }
                val dragModifier = if (onEventChange == null) {
                    Modifier
                } else {
                    Modifier.resourceBlockDragGestures(
                        index = index,
                        resizeHandleHeight = resizeHandleHeight,
                        onDragStateChange = { dragState = it },
                        currentState = { dragState },
                        onRelease = { state ->
                            val result = adjusted(state.index)
                            val handler = latestOnEventChange.value
                            if (result != null && handler != null) {
                                val targetLane = latestLanes.value[
                                    shiftedResourceIndex(
                                        index = latestLaneIndex.value,
                                        shift = laneShift(state.index),
                                        resourceCount = latestLanes.value.size,
                                    ),
                                ]
                                val (newStart, newEnd) = dragReleaseTimes(
                                    block = result,
                                    targetDate = latestDate.value,
                                )
                                handler(result.event, newStart, newEnd, targetLane.id)
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
        modifier = Modifier.fillMaxSize().onSizeChanged { laneColumnWidthPx = it.width },
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
                // A lane shift in flight slides the block over its neighbours so the drag is visible
                // before release; the grid never re-parents it.
                placeable.place(
                    x = block.column * columnWidth + laneShift(index) * constraints.maxWidth,
                    y = (block.startMinutes * minuteHeightPx).roundToInt(),
                )
            }
        }
    }
}

/**
 * The scope handed to the `eventContent` slot for [block] as currently rendered — drag adjustments
 * and lane shifts included — on the day it is drawn.
 */
private fun resourceEventScope(
    block: KalendarScheduleBlock,
    date: LocalDate,
    isDragging: Boolean,
    resource: KalendarResource,
): KalendarResourceEventScope {
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
private class ResourceEventScope(
    private val block: KalendarScheduleBlock,
    override val start: LocalDateTime,
    override val end: LocalDateTime,
    override val isDragging: Boolean,
    override val resource: KalendarResource,
) : KalendarResourceEventScope {

    override val event: KalendarEvent get() = block.event

    override val durationMinutes: Int get() = block.endMinutes - block.startMinutes

    override val overlapColumn: Int get() = block.column

    override val overlapColumns: Int get() = block.columns
}

/**
 * Long-press-then-drag on a block: a press starting within [resizeHandleHeight] of the block's
 * bottom edge resizes (moves the end time), anywhere else moves the whole block in time and across
 * lanes. Long-press is required so plain drags still scroll the surrounding grid in either
 * direction.
 *
 * The lambdas passed here are captured once when the pointer-input coroutine first launches and
 * never refreshed, so callers must route them through [rememberUpdatedState]-backed reads — see
 * [ResourceBlockLayout].
 */
private fun Modifier.resourceBlockDragGestures(
    index: Int,
    resizeHandleHeight: Dp,
    onDragStateChange: (ResourceDragState?) -> Unit,
    currentState: () -> ResourceDragState?,
    onRelease: (state: ResourceDragState) -> Unit,
): Modifier = pointerInput(index, resizeHandleHeight) {
    detectDragGesturesAfterLongPress(
        onDragStart = { offset ->
            val resizeStripPx = resizeHandleHeight.toPx()
            val resizing = offset.y > size.height - resizeStripPx
            onDragStateChange(ResourceDragState(index = index, resizing = resizing))
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
 * The current-time line, drawn in every lane when the visible day is today.
 *
 * [now] is read inside [Modifier.offset]'s lambda rather than in composition, so a minute passing
 * re-places the lines instead of recomposing the grid and everything the read would invalidate.
 */
@Composable
private fun ResourceNowIndicator(
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

private fun resourceDayTitle(
    day: LocalDate,
    shortMonthNameFormatter: (Month) -> String,
    dayOfWeekNameFormatter: (DayOfWeek) -> String,
): String {
    val weekday = dayOfWeekNameFormatter(day.dayOfWeek)
    val month = shortMonthNameFormatter(day.month)
    return "$weekday, $month ${day.dayOfMonth}, ${day.year}"
}
