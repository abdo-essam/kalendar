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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CollectionInfo
import androidx.compose.ui.semantics.CollectionItemInfo
import androidx.compose.ui.semantics.collectionInfo
import androidx.compose.ui.semantics.collectionItemInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate

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

/** Columns in every calendar grid — one per day of the week. */
private const val GRID_COLUMNS = 7

/**
 * Fresh 7-column grid engine backing [KalendarWeek] and [KalendarMonth].
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
 * [dayOfWeekLabel] supplies the *content* of each weekday column header; the grid itself still
 * measures and places one per column, in the same 7-column track as the day cells, so a replacement
 * label can never knock the header row out of alignment with the dates below it.
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
    dayOfWeekLabel: @Composable (DayOfWeek) -> Unit = { dayOfWeek ->
        KalendarHeaderDefaults.DayOfWeekLabel(dayOfWeek = dayOfWeek)
    },
    cell: @Composable (LocalDate) -> Unit,
) {
    val colors = kalendarColors()
    val shapes = kalendarShapes()
    val dimensions = kalendarDimensions()
    val animations = kalendarAnimations()
    val labelDays = remember(startDayOfWeek) { daysOfWeekStartingAt(startDayOfWeek) }
    var containerCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val cellCoordinates = remember { mutableStateMapOf<LocalDate, LayoutCoordinates>() }

    val container = containerCoordinates
    val targetCoordinates = selectedDate?.let { cellCoordinates[it] }
    val targetPosition = if (container != null && targetCoordinates != null && targetCoordinates.isAttached) {
        container.localPositionOf(targetCoordinates, Offset.Zero)
    } else {
        null
    }
    val targetSize = targetCoordinates?.size ?: IntSize.Zero

    val indicatorOffset = remember { Animatable(Offset.Zero, Offset.VectorConverter) }

    /**
     * Whether [indicatorOffset] has been given a real cell position yet.
     *
     * Until it has, the indicator is drawn straight at `targetPosition` rather than at the
     * animator's value, which still reads [Offset.Zero] on that first frame — drawing the
     * animator's value flashed the indicator in the top-left corner. Gating the whole indicator
     * on this flag instead would be worse: the flag is set from a `LaunchedEffect`, so a
     * single-pass render would omit the indicator altogether.
     */
    var hasSnappedIndicator by remember { mutableStateOf(false) }
    val indicatorSpec: AnimationSpec<Offset> = remember(animations) {
        if (animations.enabled) {
            animations.selectionIndicatorSpec
        } else {
            snap()
        }
    }

    LaunchedEffect(targetPosition) {
        val position = targetPosition ?: return@LaunchedEffect
        if (hasSnappedIndicator) {
            indicatorOffset.animateTo(position, indicatorSpec)
        } else {
            indicatorOffset.snapTo(position)
            hasSnappedIndicator = true
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

    Box(modifier = modifier.onGloballyPositioned { containerCoordinates = it }.then(dragModifier)) {
        if (targetPosition != null) {
            Box(
                modifier = Modifier
                    .offset {
                        val drawn = if (hasSnappedIndicator) indicatorOffset.value else targetPosition
                        IntOffset(drawn.x.toInt(), drawn.y.toInt())
                    }
                    .layout { measurable, _ ->
                        val placeable = measurable.measure(Constraints.fixed(targetSize.width, targetSize.height))
                        layout(placeable.width, placeable.height) { placeable.place(0, 0) }
                    }
                    // After the offset, so the tagged node reports where the indicator actually
                    // lands rather than where it would sit unoffset.
                    .testTag(SELECTION_INDICATOR_TAG)
                    .padding(dimensions.dayCellPadding)
                    .clip(shapes.dayCell)
                    .background(colors.selectionBackground),
            )
        }

        val ghostCoordinates = eventDragTarget?.let { cellCoordinates[it] }
        if (container != null && ghostCoordinates != null && ghostCoordinates.isAttached) {
            val ghostPosition = container.localPositionOf(ghostCoordinates, Offset.Zero)
            Box(
                modifier = Modifier
                    .offset { IntOffset(ghostPosition.x.toInt(), ghostPosition.y.toInt()) }
                    .layout { measurable, _ ->
                        val placeable = measurable.measure(
                            Constraints.fixed(ghostCoordinates.size.width, ghostCoordinates.size.height)
                        )
                        layout(placeable.width, placeable.height) { placeable.place(0, 0) }
                    }
                    .padding(dimensions.dayCellPadding)
                    .clip(shapes.dayCell)
                    .background(colors.dragGhostBackground.copy(alpha = colors.dragGhostAlpha)),
            )
        }

        if (keyboard != null) {
            KeyboardFocusRing(
                keyboard = keyboard,
                container = { containerCoordinates },
                cellCoordinates = cellCoordinates,
            )
        }

        val rowCount = (dates.size + GRID_COLUMNS - 1) / GRID_COLUMNS
        LazyVerticalGrid(
            columns = GridCells.Fixed(GRID_COLUMNS),
            modifier = Modifier
                .fillMaxWidth()
                .semantics { collectionInfo = CollectionInfo(rowCount = rowCount, columnCount = GRID_COLUMNS) },
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
                        .onGloballyPositioned { cellCoordinates[date] = it }
                        // Deactivates the cell's own focusability — and that of anything it draws,
                        // such as the `+N` trigger — so the grid is one tab stop, not 42. It has to
                        // sit on a node with no focus target of its own in its chain, which is why
                        // it is here and not on the LazyVerticalGrid: there it would only deactivate
                        // the lazy layout's internal focus group and leave the cells reachable.
                        .then(if (keyboard == null) Modifier else Modifier.focusProperties { canFocus = false })
                        .semantics {
                            collectionItemInfo = CollectionItemInfo(
                                rowIndex = index / GRID_COLUMNS,
                                rowSpan = 1,
                                columnIndex = index % GRID_COLUMNS,
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

/**
 * The roving keyboard focus ring: a [KalendarColors.focusIndicator] outline around whichever cell
 * [keyboard] currently points at, in [KalendarShapes.dayCell] and at the day cell's own
 * [FOCUS_RING_WIDTH], so it is indistinguishable from the ring a cell draws for itself.
 *
 * Both state reads — the focused date and the grid's focus — happen inside the `offset`/`layout`
 * lambdas, which run in the layout phase. An arrow press therefore re-lays-out one overlay node and
 * recomposes nothing; reading either value in a composable body instead would recompose every cell
 * in the grid on every arrow press, which is exactly what the recomposition benchmark exists to
 * catch. [container] is a getter for the same reason.
 */
@Composable
private fun KeyboardFocusRing(
    keyboard: KalendarGridKeyboardState,
    container: () -> LayoutCoordinates?,
    cellCoordinates: Map<LocalDate, LayoutCoordinates>,
) {
    val colors = kalendarColors()
    val shapes = kalendarShapes()
    val dimensions = kalendarDimensions()

    fun focusedCoordinates(): LayoutCoordinates? {
        if (!keyboard.isFocused) return null
        return keyboard.focusedDate?.let { cellCoordinates[it] }?.takeIf { it.isAttached }
    }

    Box(
        modifier = Modifier
            .offset {
                val coordinates = focusedCoordinates() ?: return@offset IntOffset.Zero
                val root = container() ?: return@offset IntOffset.Zero
                val position = root.localPositionOf(coordinates, Offset.Zero)
                IntOffset(position.x.toInt(), position.y.toInt())
            }
            .layout { measurable, _ ->
                val size = focusedCoordinates()?.size?.takeIf { container() != null } ?: IntSize.Zero
                val placeable = measurable.measure(Constraints.fixed(size.width, size.height))
                layout(placeable.width, placeable.height) { placeable.place(0, 0) }
            }
            .testTag(KEYBOARD_FOCUS_RING_TAG)
            .padding(dimensions.dayCellPadding)
            .border(width = FOCUS_RING_WIDTH, color = colors.focusIndicator, shape = shapes.dayCell),
    )
}

/**
 * Finds the date whose tracked cell bounds (in [container]'s local coordinate space) contain
 * [offset], or `null` if none match — e.g. the pointer is over the day-of-week label row.
 */
private fun dateAtOffset(
    offset: Offset,
    container: LayoutCoordinates?,
    cells: Map<LocalDate, LayoutCoordinates>,
): LocalDate? {
    if (container == null) return null
    for ((date, coordinates) in cells) {
        if (!coordinates.isAttached) continue
        val topLeft = container.localPositionOf(coordinates, Offset.Zero)
        val size = Size(coordinates.size.width.toFloat(), coordinates.size.height.toFloat())
        if (Rect(topLeft, size).contains(offset)) return date
    }
    return null
}

/**
 * Eager (non-lazy) variant of [KalendarGrid] for use inside another scrollable container — e.g.
 * [KalendarTimeline]'s `LazyColumn` items — where a nested `LazyVerticalGrid` would be measured
 * with an unbounded height and crash. A month grid is at most 42 cells, cheap enough to compose
 * eagerly.
 *
 * As in [KalendarGrid], [dayOfWeekLabel] supplies only the content of each weekday column header —
 * this grid keeps the `weight(1f)` column placement, so alignment with the day cells is preserved.
 */
@Composable
internal fun KalendarStaticGrid(
    dates: List<LocalDate>,
    startDayOfWeek: DayOfWeek,
    modifier: Modifier = Modifier,
    dayOfWeekLabel: @Composable (DayOfWeek) -> Unit = { dayOfWeek ->
        KalendarHeaderDefaults.DayOfWeekLabel(dayOfWeek = dayOfWeek)
    },
    cell: @Composable (LocalDate) -> Unit,
) {
    val labelDays = remember(startDayOfWeek) { daysOfWeekStartingAt(startDayOfWeek) }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth()) {
            labelDays.forEach { day ->
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    dayOfWeekLabel(day)
                }
            }
        }
        dates.chunked(7).forEach { week ->
            Row(modifier = Modifier.fillMaxWidth()) {
                for (dayIndex in 0 until 7) {
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
