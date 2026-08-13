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
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate

/**
 * Fresh 7-column grid engine backing [KalendarWeek] and [KalendarMonth].
 *
 * When [selectedDate] is non-null, a single circular indicator is drawn behind the grid and
 * animates its position to that date's cell whenever [selectedDate] changes — snapping instantly
 * the first time a position is known (e.g. when a new page is composed) and sliding smoothly on
 * every change after that, so tapping a different date in the same grid looks like the selection
 * moving rather than each cell fading independently. Pass `null` to disable the indicator (e.g.
 * for multi-select, where more than one cell can be highlighted at once).
 *
 * When [onDateRangeDrag] is non-null, press-and-hold on a cell then drag to select a range: it's
 * called continuously with (the date under the initial press, the date under the pointer now) as
 * the drag moves, and [onDateRangeDragEnd] once when the drag ends. A long-press is required to
 * start (not a plain drag) so this never conflicts with each cell's own tap-to-select `clickable`.
 */
@Composable
internal fun KalendarGrid(
    dates: List<LocalDate>,
    startDayOfWeek: DayOfWeek,
    selectedDate: LocalDate?,
    modifier: Modifier = Modifier,
    dayLabelFormatter: (DayOfWeek) -> String = defaultDayOfWeekLabelFormatter,
    onDateRangeDrag: ((start: LocalDate, current: LocalDate) -> Unit)? = null,
    onDateRangeDragEnd: (() -> Unit)? = null,
    cell: @Composable (LocalDate) -> Unit,
) {
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
    var hasSnappedIndicator by remember { mutableStateOf(false) }
    val indicatorSpec = remember {
        spring<Offset>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessHigh)
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

    val dragModifier = if (onDateRangeDrag != null) {
        Modifier.pointerInput(dates) {
            var dragStartDate: LocalDate? = null
            detectDragGesturesAfterLongPress(
                onDragStart = { offset ->
                    dragStartDate = dateAtOffset(offset, containerCoordinates, cellCoordinates)
                },
                onDragEnd = {
                    dragStartDate = null
                    onDateRangeDragEnd?.invoke()
                },
                onDragCancel = {
                    dragStartDate = null
                    onDateRangeDragEnd?.invoke()
                },
                onDrag = { change, _ ->
                    val start = dragStartDate ?: return@detectDragGesturesAfterLongPress
                    val current = dateAtOffset(change.position, containerCoordinates, cellCoordinates)
                        ?: return@detectDragGesturesAfterLongPress
                    onDateRangeDrag(start, current)
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
                    .offset { IntOffset(indicatorOffset.value.x.toInt(), indicatorOffset.value.y.toInt()) }
                    .layout { measurable, _ ->
                        val placeable = measurable.measure(Constraints.fixed(targetSize.width, targetSize.height))
                        layout(placeable.width, placeable.height) { placeable.place(0, 0) }
                    }
                    .padding(2.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
            )
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(7),
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
        ) {
            items(labelDays) { day ->
                Text(
                    text = dayLabelFormatter(day),
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().alpha(0.6f),
                )
            }
            items(items = dates, key = { it.toEpochDays() }) { date ->
                Box(modifier = Modifier.onGloballyPositioned { cellCoordinates[date] = it }) {
                    cell(date)
                }
            }
        }
    }
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
 */
@Composable
internal fun KalendarStaticGrid(
    dates: List<LocalDate>,
    startDayOfWeek: DayOfWeek,
    modifier: Modifier = Modifier,
    dayLabelFormatter: (DayOfWeek) -> String = defaultDayOfWeekLabelFormatter,
    cell: @Composable (LocalDate) -> Unit,
) {
    val labelDays = remember(startDayOfWeek) { daysOfWeekStartingAt(startDayOfWeek) }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth()) {
            labelDays.forEach { day ->
                Text(
                    text = dayLabelFormatter(day),
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f).alpha(0.6f),
                )
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
