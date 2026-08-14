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

package com.himanshoe.kalendar.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.snap
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CollectionInfo
import androidx.compose.ui.semantics.CollectionItemInfo
import androidx.compose.ui.semantics.collectionInfo
import androidx.compose.ui.semantics.collectionItemInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.toSize
import com.himanshoe.kalendar.foundation.event.KalendarEvent
import com.himanshoe.kalendar.foundation.event.KalendarEvents
import com.himanshoe.kalendar.foundation.grid.ALL_DAYS_OF_WEEK
import com.himanshoe.kalendar.foundation.grid.daysOfWeekStartingAt
import com.himanshoe.kalendar.theme.kalendarAnimations
import com.himanshoe.kalendar.theme.kalendarColors
import com.himanshoe.kalendar.theme.kalendarDimensions
import com.himanshoe.kalendar.theme.kalendarShapes
import kotlinx.coroutines.flow.collectLatest
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

/**
 * Test tag on the sliding selection indicator, so a test can assert it is never placed at the
 * origin. A one-frame flash there is invisible to the golden tests, which render after layout.
 */
internal const val SELECTION_INDICATOR_TAG = "KalendarSelectionIndicator"

/**
 * Test tag on the roving keyboard focus ring, so a test can assert which cell it has landed on
 * without depending on the pixels it draws.
 */
internal const val KEYBOARD_FOCUS_RING_TAG = "KalendarKeyboardFocusRing"

/**
 * Longest run of days one multi-day event is laid out across.
 *
 * The same guard `byDateExpandingSpans` applies when it expands a span into dates, repeated here
 * so the two agree: an event the grid never draws past day 366 must not hold a lane past day 366
 * either, or one malformed `endDate` in the year 9999 reserves a lane in every cell forever.
 */
private const val MAX_EVENT_SPAN_DAYS = 366

/**
 * The last date this event occupies as a **continuous span**, or `null` if it is not one.
 *
 * An event is a span only when it covers more than one day. An `endDate` equal to or before
 * [KalendarEvent.date] is not a span — it is the single-day event `byDateExpandingSpans` already
 * treats it as, and it draws the indicator dot it always did.
 */
internal fun KalendarEvent.spanEndOrNull(): LocalDate? {
    val end = endDate ?: return null
    if (end <= date) return null
    return minOf(end, date.plus(MAX_EVENT_SPAN_DAYS, DateTimeUnit.DAY))
}

/**
 * Which lane of a day cell's span-bar stack each multi-day event was given, and how many lanes the
 * grid therefore reserves in every one of its cells.
 *
 * A lane has to be a property of the *event*, not of the day: the same conference has to sit at the
 * same height on all three of its days or its bar is a staircase rather than a bar. That cannot be
 * decided from one day's event list, because a lane freed by an event that ended yesterday would be
 * reused today and the event above it would drop a row. So it is decided once for the whole event
 * list and read back per cell — which is also why [laneCount] is uniform across the grid, and why
 * the strip a day cell reserves under its number is the same height whatever that day holds.
 *
 * An event with no lane — one past `spanBarMaxLanes` — is *not* hidden. It falls back to the
 * indicator dot it drew before span bars existed, so it stays visible and still reaches the `+N`
 * overflow when the dots run out.
 */
@Immutable
internal class KalendarSpanLanes private constructor(
    private val lanes: Map<KalendarEvent, Int>,
    val laneCount: Int,
) {

    /** The lane [event] occupies, or `null` if it is not a span or did not fit the lane budget. */
    fun laneOf(event: KalendarEvent): Int? = lanes[event]

    internal companion object {

        /** No spans anywhere: every cell reserves no lanes and every event draws as a dot. */
        val Empty: KalendarSpanLanes = KalendarSpanLanes(lanes = emptyMap(), laneCount = 0)

        /**
         * Packs every multi-day event in [events] into the lowest lane free for its whole run,
         * taking at most [maxLanes] of them.
         *
         * Sweeping the spans in start order and tracking one "occupied until" date per lane is the
         * standard interval-graph greedy, and it is optimal here: it never uses more lanes than the
         * deepest overlap actually needs, so a calendar whose spans never overlap reserves exactly
         * one lane and the day numbers keep almost all of the cell.
         *
         * Ties are broken towards the longer span so the bar that runs furthest sits highest, and
         * `sortedWith` is stable, so two spans alike in both dates keep their order in [events] and
         * the lanes come out the same on every recomposition.
         */
        fun of(events: KalendarEvents, maxLanes: Int): KalendarSpanLanes {
            if (maxLanes < 1) return Empty
            val spans = events
                .mapNotNull { event -> event.spanEndOrNull()?.let { end -> event to end } }
                .distinct()
            if (spans.isEmpty()) return Empty

            val ordered = spans.sortedWith(
                compareBy<Pair<KalendarEvent, LocalDate>> { it.first.date }
                    .thenByDescending { it.second },
            )
            val laneEnds = mutableListOf<LocalDate>()
            val lanes = HashMap<KalendarEvent, Int>(ordered.size)
            for ((event, end) in ordered) {
                val free = laneEnds.indexOfFirst { it < event.date }
                when {
                    free >= 0 -> {
                        laneEnds[free] = end
                        lanes[event] = free
                    }
                    laneEnds.size < maxLanes -> {
                        lanes[event] = laneEnds.size
                        laneEnds.add(end)
                    }
                }
            }
            return KalendarSpanLanes(lanes = lanes, laneCount = laneEnds.size)
        }
    }
}

/**
 * The span-bar lanes in force for the surrounding grid, which is the only thing a day cell cannot
 * work out for itself.
 *
 * Provided by [KalendarGrid] and [KalendarStaticGrid] from the whole event list they were given,
 * and read by [KalendarDayCellDefaults.Cell]. It travels as a composition local rather than a cell
 * parameter because the cell is reached through a caller-supplied `cell`/`dayContent` lambda, whose
 * signature belongs to the calling app; a [KalendarDayCellDefaults.Cell] composed outside any grid
 * finds [KalendarSpanLanes.Empty] here and renders multi-day events exactly as it did before span
 * bars existed.
 */
internal val LocalKalendarSpanLanes = staticCompositionLocalOf { KalendarSpanLanes.Empty }

/**
 * Grid engine backing [KalendarWeek] and [KalendarMonth], one column per *visible* day of the week.
 *
 * When [selectedDate] is non-null, a single indicator shaped by [KalendarShapes.dayCell] is drawn
 * behind the grid and animates its position to that date's cell whenever [selectedDate] changes —
 * snapping instantly the first time a position is known (e.g. when a new page is composed) and
 * sliding smoothly on every change after that, so tapping a different date in the same grid looks
 * like the selection moving rather than each cell fading independently. The slide follows
 * [KalendarAnimations.selectionIndicatorSpec], and snaps outright when [KalendarAnimations.enabled]
 * is `false`. Pass `null` to disable the indicator (e.g. for multi-select, where more than one cell
 * can be highlighted at once).
 *
 * When [onDateRangeDrag] is non-null, press-and-hold on a cell then drag to select a range: it's
 * called continuously with (the date under the initial press, the date under the pointer now) as
 * the drag moves, and [onDateRangeDragEnd] once when the drag ends. A long-press is required to
 * start (not a plain drag) so this never conflicts with each cell's own tap-to-select `clickable`.
 *
 * When [onEventDrop] is non-null and the long-pressed cell has events ([hasEvents] returns
 * `true` for it), the same gesture becomes an event move instead of a range selection: a ghost
 * highlight follows the cell under the pointer, and releasing over a different date calls
 * [onEventDrop] with (pressed date, drop date). Cells without events still fall through to
 * range selection.
 *
 * [events] is the whole event list, not one day's: it is what the grid packs into span-bar lanes
 * for the multi-day events in it, and a lane can only be assigned by looking at every span at once
 * (see [KalendarSpanLanes]). The result reaches the cells through [LocalKalendarSpanLanes]. Leave
 * it empty on a grid that draws no events — a date picker — and nothing about the cells changes.
 *
 * [dayOfWeekLabel] supplies the *content* of each weekday column header; the grid itself still
 * measures and places one per column, in the same track as the day cells, so a replacement label can
 * never knock the header row out of alignment with the dates below it. The column count comes from
 * [visibleDaysOfWeek] — [dates] is expected to have been filtered by the same set, so a Mon–Fri
 * calendar is five columns of five labels and five dates per row.
 *
 * When [keyboard] is non-null the grid takes part in keyboard navigation: it draws the roving focus
 * ring at [KalendarGridKeyboardState.focusedDate], reports itself to a screen reader as a grid of
 * dated cells, and — crucially — *deactivates* focus on the cells themselves, so the calendar is one
 * tab stop rather than 42. The keys are handled by [Modifier.kalendarGridKeyboard], applied by the
 * view above the pager; see it for the full key map. A consequence worth knowing: a `+N` overflow
 * trigger inside a cell is deactivated with the rest of the subtree, so a day's full event list is
 * reached from the keyboard by selecting the day, which reports its events, rather than by tabbing
 * to the label.
 */
@Composable
internal fun KalendarGrid(
    dates: List<LocalDate>,
    startDayOfWeek: DayOfWeek,
    selectedDate: LocalDate?,
    modifier: Modifier = Modifier,
    keyboard: KalendarGridKeyboardState? = null,
    onDateRangeDrag: ((start: LocalDate, current: LocalDate) -> Unit)? = null,
    onDateRangeDragEnd: (() -> Unit)? = null,
    hasEvents: (LocalDate) -> Boolean = { false },
    onEventDrop: ((from: LocalDate, to: LocalDate) -> Unit)? = null,
    events: KalendarEvents = emptyList(),
    visibleDaysOfWeek: Set<DayOfWeek> = ALL_DAYS_OF_WEEK,
    dayOfWeekLabel: @Composable (DayOfWeek) -> Unit = { dayOfWeek ->
        KalendarHeaderDefaults.DayOfWeekLabel(dayOfWeek = dayOfWeek)
    },
    cell: @Composable (LocalDate) -> Unit,
) {
    val colors = kalendarColors()
    val shapes = kalendarShapes()
    val dimensions = kalendarDimensions()
    val animations = kalendarAnimations()
    val labelDays = remember(startDayOfWeek, visibleDaysOfWeek) {
        daysOfWeekStartingAt(startDayOfWeek = startDayOfWeek, visibleDaysOfWeek = visibleDaysOfWeek)
    }
    val columns = labelDays.size
    var containerCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }

    /**
     * Where each day cell sits, written from [Modifier.onGloballyPositioned] — the layout phase.
     *
     * A plain map rather than a `mutableStateMapOf`, because nothing in composition reads it and
     * nothing may: the gesture code reads it from a pointer callback, the three overlays below read
     * it from their layout lambdas, and the indicator's position flow reads it from a coroutine. As
     * snapshot state, every write invalidated whichever composable scope had read it — so the pass
     * that first placed the cells, one write per cell and up to 42 of them in a month grid, always
     * recomposed the grid on top of the layout that had caused it. Measured on a March 2026 month
     * that was 37 writes and one whole extra composition of this function per page composed.
     */
    val cellCoordinates = remember { mutableMapOf<LocalDate, KalendarCellPlacement>() }

    /**
     * Bumped whenever a cell reports a placement it did not have before.
     *
     * This is the one thing [cellCoordinates] gives up by being a plain map — the ability to tell a
     * reader that the cells have moved — handed back as a single [Int]. Reading it inside a layout
     * lambda re-runs that lambda when they do; reading it inside a [snapshotFlow] re-evaluates the
     * flow. Writing it only on a *change* is what makes that safe: a write on every callback would
     * invalidate the placement that produced the callback, and the grid would lay itself out
     * forever. On a real change it settles after one extra pass, because the pass that write
     * triggers reports the same placements straight back.
     */
    var cellLayoutRevision by remember { mutableIntStateOf(0) }

    /**
     * Where [date]'s cell sits in this grid's own coordinate space, or `null` when there is no such
     * cell to point at yet.
     *
     * **Never call this from composition.** Everything it reads is written during layout, so a
     * composable body that called it would be invalidated by every layout pass — which is exactly
     * the recomposition-per-layout this grid used to pay for its selection indicator. Layout lambdas
     * and coroutines only.
     */
    fun cellBounds(date: LocalDate?): Rect? {
        // Read first and unconditionally: this is the subscription that makes a layout lambda, or the
        // flow below, re-run once cells are positioned — and a revision of zero genuinely does mean
        // that no cell has ever reported a position.
        if (cellLayoutRevision == 0) return null
        val root = containerCoordinates ?: return null
        val cell = date?.let(cellCoordinates::get)?.coordinates?.takeIf { it.isAttached } ?: return null
        return Rect(offset = root.localPositionOf(cell, Offset.Zero), size = cell.size.toSize())
    }

    val indicatorOffset = remember { Animatable(Offset.Zero, Offset.VectorConverter) }

    /**
     * Whether [indicatorOffset] has been given a real cell position yet.
     *
     * Until it has, the indicator is drawn straight at the cell `cellBounds` reports rather than at
     * the animator's value, which still reads [Offset.Zero] until the flow below snaps it — drawing
     * the animator's value flashed the indicator in the top-left corner. Gating the whole indicator
     * on this flag instead would be worse: the flag is set from a coroutine, so a single-pass render
     * would omit the indicator altogether. Everything the indicator needs in order to *appear* is
     * therefore resolved in the layout phase, and the coroutine decides only how it travels.
     */
    var hasSnappedIndicator by remember { mutableStateOf(false) }
    val indicatorSpec: AnimationSpec<Offset> = remember(animations) {
        if (animations.enabled) {
            animations.selectionIndicatorSpec
        } else {
            snap()
        }
    }

    /**
     * [selectedDate] is a parameter, not snapshot state, so the flow below cannot observe it
     * directly: a flow lambda is captured once and would hold the first composition's date forever,
     * stranding the indicator on whichever day was selected when the grid appeared.
     */
    val latestSelectedDate = rememberUpdatedState(selectedDate)

    // collectLatest, not collect: a plain collector stays suspended for the whole of animateTo, so a
    // second date tapped mid-slide would queue behind the first and the indicator would finish
    // travelling to a cell nobody is looking at any more. Cancelling the previous animation instead
    // hands its position and velocity to the next one, which is what makes the slide redirect under
    // a fast tapper rather than restart.
    LaunchedEffect(indicatorSpec) {
        snapshotFlow { cellBounds(latestSelectedDate.value)?.topLeft }
            .collectLatest { position ->
                if (position == null) {
                    return@collectLatest
                }
                if (hasSnappedIndicator) {
                    indicatorOffset.animateTo(position, indicatorSpec)
                } else {
                    indicatorOffset.snapTo(position)
                    hasSnappedIndicator = true
                }
            }
    }

    var eventDragTarget by remember { mutableStateOf<LocalDate?>(null) }

    val latestHasEvents = rememberUpdatedState(hasEvents)
    val latestOnEventDrop = rememberUpdatedState(onEventDrop)
    val latestOnDateRangeDrag = rememberUpdatedState(onDateRangeDrag)
    val latestOnDateRangeDragEnd = rememberUpdatedState(onDateRangeDragEnd)

    val dragModifier = if (onDateRangeDrag != null || onEventDrop != null) {
        Modifier.pointerInput(dates) {
            var dragStartDate: LocalDate? = null
            var movingEvents = false
            detectDragGesturesAfterLongPress(
                onDragStart = { offset ->
                    val start = dateAtOffset(offset, containerCoordinates, cellCoordinates)
                    dragStartDate = start
                    movingEvents = latestOnEventDrop.value != null && start != null && latestHasEvents.value(start)
                    if (movingEvents) {
                        eventDragTarget = start
                    }
                },
                onDragEnd = {
                    val start = dragStartDate
                    val target = eventDragTarget
                    if (movingEvents) {
                        if (start != null && target != null && target != start) {
                            latestOnEventDrop.value?.invoke(start, target)
                        }
                    } else {
                        latestOnDateRangeDragEnd.value?.invoke()
                    }
                    dragStartDate = null
                    movingEvents = false
                    eventDragTarget = null
                },
                onDragCancel = {
                    if (!movingEvents) {
                        latestOnDateRangeDragEnd.value?.invoke()
                    }
                    dragStartDate = null
                    movingEvents = false
                    eventDragTarget = null
                },
                onDrag = { change, _ ->
                    val start = dragStartDate ?: return@detectDragGesturesAfterLongPress
                    val current = dateAtOffset(change.position, containerCoordinates, cellCoordinates)
                        ?: return@detectDragGesturesAfterLongPress
                    if (movingEvents) {
                        eventDragTarget = current
                    } else {
                        latestOnDateRangeDrag.value?.invoke(start, current)
                    }
                },
            )
        }
    } else {
        Modifier
    }

    CompositionLocalProvider(
        LocalKalendarSpanLanes provides rememberKalendarSpanLanes(
            events = events,
            maxLanes = dimensions.spanBarMaxLanes,
        ),
    ) {
        Box(modifier = modifier.onGloballyPositioned { containerCoordinates = it }.then(dragModifier)) {
            if (selectedDate != null) {
                Box(
                    modifier = Modifier
                        .overlayCell(
                            bounds = { cellBounds(selectedDate) },
                            drawnAt = { cell ->
                                if (hasSnappedIndicator) indicatorOffset.value else cell.topLeft
                            },
                        )
                        // After the offset, so the tagged node reports where the indicator actually
                        // lands rather than where it would sit unoffset.
                        .testTag(SELECTION_INDICATOR_TAG)
                        .padding(dimensions.dayCellPadding)
                        .clip(shapes.dayCell)
                        .background(colors.selectionBackground),
                )
            }

            if (onEventDrop != null) {
                Box(
                    modifier = Modifier
                        .overlayCell(bounds = { cellBounds(eventDragTarget) })
                        .padding(dimensions.dayCellPadding)
                        .clip(shapes.dayCell)
                        .background(colors.dragGhostBackground.copy(alpha = colors.dragGhostAlpha)),
                )
            }

            if (keyboard != null) {
                KeyboardFocusRing(keyboard = keyboard, cellBounds = ::cellBounds)
            }

            val rowCount = (dates.size + columns - 1) / columns
            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { collectionInfo = CollectionInfo(rowCount = rowCount, columnCount = columns) },
                horizontalArrangement = Arrangement.Center,
                userScrollEnabled = false,
            ) {
                items(labelDays) { day ->
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        dayOfWeekLabel(day)
                    }
                }
                itemsIndexed(items = dates, key = { _, date -> date.toEpochDays() }) { index, date ->
                    Box(
                        modifier = Modifier
                            .onGloballyPositioned { coordinates ->
                                val known = cellCoordinates[date]
                                if (known == null || !known.stillDescribes(coordinates)) {
                                    cellCoordinates[date] = KalendarCellPlacement(coordinates)
                                    cellLayoutRevision++
                                }
                            }
                            // Deactivates the cell's own focusability — and that of anything it
                            // draws, such as the `+N` trigger — so the grid is one tab stop, not
                            // 42. It has to sit on a node with no focus target of its own in its
                            // chain, which is why it is here and not on the LazyVerticalGrid: there
                            // it would only deactivate the lazy layout's internal focus group and
                            // leave the cells reachable.
                            .then(if (keyboard == null) Modifier else Modifier.focusProperties { canFocus = false })
                            .semantics {
                                collectionItemInfo = CollectionItemInfo(
                                    rowIndex = index / columns,
                                    rowSpan = 1,
                                    columnIndex = index % columns,
                                    columnSpan = 1,
                                )
                            },
                    ) {
                        cell(date)
                    }
                }
            }
        }
    }
}

/**
 * Packs [events]'s multi-day spans into lanes, recomputing only when the event list or the lane
 * budget changes.
 *
 * The pass is O(spans log spans) and runs on every grid, which for [KalendarYear] means once per
 * month on screen over the same list. That is deliberate: hoisting it into the calendar views would
 * put a grid's private layout decision into four public composables' bodies, and the cost is a sort
 * of the multi-day events alone — not the whole list — behind a `remember`.
 */
@Composable
private fun rememberKalendarSpanLanes(events: KalendarEvents, maxLanes: Int): KalendarSpanLanes =
    remember(events, maxLanes) { KalendarSpanLanes.of(events = events, maxLanes = maxLanes) }

/**
 * Sizes and positions one of the grid's three overlays — the selection indicator, the drag ghost,
 * the keyboard focus ring — over whichever cell [bounds] names, or measures it away to nothing when
 * [bounds] names none.
 *
 * Both reads happen inside the `offset` and `layout` lambdas, which run in the layout phase. An
 * arrow press, a pointer crossing into the next day, or the first frame's cells being positioned
 * therefore re-lay-out one overlay node and recompose nothing — reading a cell's coordinates in a
 * composable body instead invalidates the whole grid on every layout pass, which is what the
 * recomposition benchmark exists to catch.
 *
 * Measuring an absent overlay to zero rather than leaving it out of the composition is deliberate:
 * whether an overlay is *shown* is then a layout-phase decision like everything else about it, and
 * a zero-sized node draws nothing at all. It is also what lets the selection indicator exist from
 * the first composition, before any coroutine has run — see `hasSnappedIndicator`.
 *
 * @param bounds The cell to cover, in the grid's coordinate space.
 * @param drawnAt Where to actually place the overlay, given that cell. Defaults to the cell's own
 *   top-left; the selection indicator overrides it to follow its animator instead.
 */
private fun Modifier.overlayCell(
    bounds: () -> Rect?,
    drawnAt: (Rect) -> Offset = { it.topLeft },
): Modifier = this
    .offset {
        val cell = bounds() ?: return@offset IntOffset.Zero
        val position = drawnAt(cell)
        IntOffset(position.x.toInt(), position.y.toInt())
    }
    .layout { measurable, _ ->
        val size = bounds()?.size ?: Size.Zero
        val placeable = measurable.measure(Constraints.fixed(size.width.toInt(), size.height.toInt()))
        layout(placeable.width, placeable.height) { placeable.place(0, 0) }
    }

/**
 * The roving keyboard focus ring: a [KalendarColors.focusIndicator] outline around whichever cell
 * [keyboard] currently points at, in [KalendarShapes.dayCell] and at the day cell's own
 * [FOCUS_RING_WIDTH], so it is indistinguishable from the ring a cell draws for itself.
 *
 * Both state reads — the focused date and the grid's focus — happen inside [Modifier.overlayCell]'s
 * layout lambdas, so an arrow press recomposes nothing at all.
 */
@Composable
private fun KeyboardFocusRing(
    keyboard: KalendarGridKeyboardState,
    cellBounds: (LocalDate?) -> Rect?,
) {
    val colors = kalendarColors()
    val shapes = kalendarShapes()
    val dimensions = kalendarDimensions()

    Box(
        modifier = Modifier
            .overlayCell(
                bounds = { if (keyboard.isFocused) cellBounds(keyboard.focusedDate) else null },
            )
            .testTag(KEYBOARD_FOCUS_RING_TAG)
            .padding(dimensions.dayCellPadding)
            .border(width = FOCUS_RING_WIDTH, color = colors.focusIndicator, shape = shapes.dayCell),
    )
}

/**
 * One day cell as its last [Modifier.onGloballyPositioned] callback described it.
 *
 * [coordinates] is what every reader actually uses; the other two are only ever compared, and they
 * are what tells the grid that a layout pass *moved* a cell rather than merely re-reporting one it
 * already knew about. The coordinates object cannot answer that itself — it is the same instance
 * from one pass to the next, and a live view rather than a snapshot, so putting it back into the map
 * is a no-op even when the cell has moved half the screen. Resizing the window, switching to a work
 * week and changing the first day of the week all land here, and all three used to leave the
 * selection indicator behind on the cell the selected date used to occupy.
 *
 * The placement compared is the one inside the cell's own parent, deliberately not its position in
 * the root: a pager translating a whole month across the screen moves every cell in the root's terms
 * on every frame of the swipe, and re-measuring the overlays sixty times a second to tell them what
 * they already know is exactly the cost this file exists to avoid.
 */
private class KalendarCellPlacement(val coordinates: LayoutCoordinates) {

    private val positionInParent: Offset = coordinates.positionInParent()
    private val size: IntSize = coordinates.size

    /** Whether [candidate] is the same node, still exactly where this placement recorded it. */
    fun stillDescribes(candidate: LayoutCoordinates): Boolean =
        candidate === coordinates &&
            candidate.size == size &&
            candidate.positionInParent() == positionInParent
}

/**
 * Finds the date whose tracked cell bounds (in [container]'s local coordinate space) contain
 * [offset], or `null` if none match — e.g. the pointer is over the day-of-week label row.
 */
private fun dateAtOffset(
    offset: Offset,
    container: LayoutCoordinates?,
    cells: Map<LocalDate, KalendarCellPlacement>,
): LocalDate? {
    if (container == null) return null
    for ((date, placement) in cells) {
        val coordinates = placement.coordinates
        if (!coordinates.isAttached) continue
        val topLeft = container.localPositionOf(coordinates, Offset.Zero)
        val size = Size(coordinates.size.width.toFloat(), coordinates.size.height.toFloat())
        if (Rect(topLeft, size).contains(offset)) return date
    }
    return null
}

/**
 * Eager (non-lazy) variant of [KalendarGrid] for use inside another scrollable container — e.g.
 * [KalendarTimeline]'s and [KalendarYear]'s `LazyColumn` items — where a nested `LazyVerticalGrid`
 * would be measured with an unbounded height and crash. A month grid is at most 42 cells, cheap
 * enough to compose eagerly once the container around it only asks for the months on screen.
 *
 * As in [KalendarGrid], [dayOfWeekLabel] supplies only the content of each weekday column header —
 * this grid keeps the `weight(1f)` column placement, so alignment with the day cells is preserved —
 * [visibleDaysOfWeek] sets the column count, [dates] being expected to carry the same filter, and
 * [events] is the whole event list, from which the grid packs span-bar lanes for its cells.
 */
@Composable
internal fun KalendarStaticGrid(
    dates: List<LocalDate>,
    startDayOfWeek: DayOfWeek,
    modifier: Modifier = Modifier,
    events: KalendarEvents = emptyList(),
    visibleDaysOfWeek: Set<DayOfWeek> = ALL_DAYS_OF_WEEK,
    dayOfWeekLabel: @Composable (DayOfWeek) -> Unit = { dayOfWeek ->
        KalendarHeaderDefaults.DayOfWeekLabel(dayOfWeek = dayOfWeek)
    },
    cell: @Composable (LocalDate) -> Unit,
) {
    val labelDays = remember(startDayOfWeek, visibleDaysOfWeek) {
        daysOfWeekStartingAt(startDayOfWeek = startDayOfWeek, visibleDaysOfWeek = visibleDaysOfWeek)
    }
    val columns = labelDays.size

    CompositionLocalProvider(
        LocalKalendarSpanLanes provides rememberKalendarSpanLanes(
            events = events,
            maxLanes = kalendarDimensions().spanBarMaxLanes,
        ),
    ) {
        Column(modifier = modifier.fillMaxWidth()) {
            Row(modifier = Modifier.fillMaxWidth()) {
                labelDays.forEach { day ->
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        dayOfWeekLabel(day)
                    }
                }
            }
            dates.chunked(columns).forEach { week ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    for (dayIndex in 0 until columns) {
                        Box(modifier = Modifier.weight(1f)) {
                            val date = week.getOrNull(dayIndex)
                            if (date != null) {
                                cell(date)
                            }
                        }
                    }
                }
            }
        }
    }
}
