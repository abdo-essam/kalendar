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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.util.fastForEachIndexed
import androidx.compose.ui.util.fastMap
import androidx.compose.ui.util.fastMapIndexed
import com.himanshoe.kalendar.foundation.event.KalendarEvent
import kotlin.math.roundToInt

/*
 * The one block layout behind every hour-grid column — KalendarSchedule's day, KalendarScheduleWeek's
 * seven, KalendarResourceView's lanes — which used to be two copies that had already drifted apart:
 * the lane grid's could only resize a block's *end*, because it was forked from a version of the
 * schedule grid that predated the top handle.
 *
 * The two views still differ in what a sideways drag means — another day, another lane — so that, and
 * only that, is parameterised: see KalendarBlockDragAxis.
 */

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
 * What the horizontal half of a drag means to the column being dragged in.
 *
 * The vertical half is the same everywhere — it is time — but sideways is a different day on
 * [KalendarScheduleWeek] and a different lane on [KalendarResourceView], and on [KalendarSchedule]
 * it is nothing at all. Implementations are compared structurally, so an axis rebuilt from unchanged
 * inputs does not invalidate the blocks that read it.
 */
@Stable
internal interface KalendarBlockDragAxis<T> {

    /**
     * What a block dragged [rawDeltaXPx] sideways across columns [columnWidthPx] wide has landed on.
     *
     * Called with `0f` for every block that is not being dragged, so an implementation must answer
     * with the column the block already belongs to when the delta is zero.
     */
    fun targetOf(rawDeltaXPx: Float, columnWidthPx: Int): T

    /**
     * Whole columns to slide the dragged block by while the gesture is still live, so a cross-column
     * drag is visible before it is released. `0` pins the block inside its own column — which is what
     * the Schedule views want, since their columns are siblings in a `Row` and a block placed past
     * its own edge would be drawn over pixels a neighbour owns.
     */
    fun placementShift(rawDeltaXPx: Float, columnWidthPx: Int): Int
}

/**
 * Live drag state for one block being moved or resized: the grabbed edge plus raw pixel deltas
 * accumulated from the gesture, converted to snapped minute and whole-column deltas at render and
 * release time.
 */
private data class KalendarBlockDrag(
    val index: Int,
    val mode: ScheduleDragMode,
    val rawDeltaYPx: Float = 0f,
    val rawDeltaXPx: Float = 0f,
)

/**
 * One block as it is drawn this frame: the live drag already applied to its minute range, whether
 * that drag is this block's, and where a sideways drag has carried it.
 *
 * A data class on purpose. It is produced inside a [derivedStateOf], which compares its result
 * structurally — so a pointer move that changes nothing for a block leaves that block's slot
 * untouched instead of recomposing the whole column.
 */
private data class KalendarRenderedBlock<T>(
    val block: KalendarScheduleBlock,
    val isDragging: Boolean,
    val target: T,
    val shiftColumns: Int,
)

/**
 * Positions one column's [blocks] on the hour grid and owns their move/resize gestures.
 *
 * @param blocks The column's blocks, in the order the packer produced them. An empty list draws
 *   nothing at all.
 * @param hourHeight Vertical space for one hour; block heights and every pixel-to-minute conversion
 *   scale with it.
 * @param window The visible slice of the day. Positions are measured from its top edge and every
 *   drag clamps to it, so a block can never be dragged out of reach.
 * @param snapMinutes Step a vertical drag snaps to.
 * @param horizontalShift What a sideways drag means here — see [KalendarBlockDragAxis].
 * @param onEventClick Called when a block is tapped.
 * @param onRelease Called when a drag ends, with the block as adjusted and the column it landed in.
 *   `null` makes the blocks undraggable, which is how a view disables editing.
 * @param eventContent Slot for one block, given the block as adjusted, whether it is the one being
 *   dragged, and the column it is currently over. Measured and positioned by this layout, with the
 *   tap and drag gestures on the cell around it.
 */
@Composable
internal fun <T> KalendarBlockLayout(
    blocks: List<KalendarScheduleBlock>,
    hourHeight: Dp,
    window: KalendarHourWindow,
    snapMinutes: Int,
    horizontalShift: KalendarBlockDragAxis<T>,
    onEventClick: (KalendarEvent) -> Unit,
    onRelease: ((block: KalendarScheduleBlock, target: T) -> Unit)?,
    eventContent: @Composable (block: KalendarScheduleBlock, isDragging: Boolean, target: T) -> Unit,
) {
    if (blocks.isEmpty()) {
        return
    }
    val drag = remember { mutableStateOf<KalendarBlockDrag?>(null) }
    LaunchedEffect(blocks) { drag.value = null }

    val minutePx = with(LocalDensity.current) { hourHeight.toPx() } / MINUTES_PER_HOUR
    val resizeHandleHeight = kalendarDimensions().eventBlockResizeHandleHeight
    // The *column's* measured width, never the dragged block's: a block that overlaps another is a
    // fraction of the column wide, and dividing by that over-shifts the column count.
    var columnWidthPx by remember { mutableStateOf(0) }

    // A pointerInput coroutine captures the lambdas it was first composed with and never refreshes
    // them, and renderedAt below is likewise captured once per block. Everything either of them
    // reads therefore goes through a State whose identity outlives the recomposition.
    val latestBlocks = rememberUpdatedState(blocks)
    val latestWindow = rememberUpdatedState(window)
    val latestSnapMinutes = rememberUpdatedState(snapMinutes)
    val latestMinutePx = rememberUpdatedState(minutePx)
    val latestAxis = rememberUpdatedState(horizontalShift)
    val latestColumnWidthPx = rememberUpdatedState(columnWidthPx)
    val latestOnRelease = rememberUpdatedState(onRelease)

    fun restingBlock(source: KalendarScheduleBlock): KalendarRenderedBlock<T> = KalendarRenderedBlock(
        block = source,
        isDragging = false,
        target = latestAxis.value.targetOf(rawDeltaXPx = 0f, columnWidthPx = latestColumnWidthPx.value),
        shiftColumns = 0,
    )

    /**
     * Reads the live drag, so it must only ever be called from a measure lambda or from inside a
     * [derivedStateOf]. Called straight from composition it makes every block in the column a reader
     * of the drag, and one pointer move then recomposes all of them.
     */
    fun renderedAt(index: Int): KalendarRenderedBlock<T>? {
        val source = latestBlocks.value.getOrNull(index) ?: return null
        val live = drag.value?.takeIf { it.index == index } ?: return restingBlock(source)
        // Only a whole-block move travels sideways: dragging an edge changes the time and must leave
        // the block in the day or lane it started in.
        val rawDeltaXPx = if (live.mode == ScheduleDragMode.Move) live.rawDeltaXPx else 0f
        val columnWidth = latestColumnWidthPx.value
        return KalendarRenderedBlock(
            block = adjustedScheduleBlockForDrag(
                block = source,
                deltaMinutes = snappedDragMinutes(
                    rawPx = live.rawDeltaYPx,
                    minutePx = latestMinutePx.value,
                    snapMinutes = latestSnapMinutes.value,
                ),
                mode = live.mode,
                window = latestWindow.value,
            ),
            isDragging = true,
            target = latestAxis.value.targetOf(rawDeltaXPx = rawDeltaXPx, columnWidthPx = columnWidth),
            shiftColumns = latestAxis.value.placementShift(rawDeltaXPx = rawDeltaXPx, columnWidthPx = columnWidth),
        )
    }

    Layout(
        content = {
            blocks.fastForEachIndexed { index, block ->
                key(blockKey(block)) {
                    KalendarBlockCell(
                        index = index,
                        resizeHandleHeight = resizeHandleHeight,
                        dragEnabled = onRelease != null,
                        renderedAt = ::renderedAt,
                        currentDrag = drag::value,
                        onDragChange = { drag.value = it },
                        onDragRelease = { released ->
                            val result = renderedAt(released.index)
                            val handler = latestOnRelease.value
                            if (result != null && handler != null) {
                                handler(result.block, result.target)
                            }
                            drag.value = null
                        },
                        onEventClick = onEventClick,
                        eventContent = eventContent,
                    )
                }
            }
        },
        modifier = Modifier.fillMaxSize().onSizeChanged { columnWidthPx = it.width },
    ) { measurables, constraints ->
        val minuteHeightPx = hourHeight.toPx() / MINUTES_PER_HOUR
        val rendered = blocks.fastMapIndexed { index, block -> renderedAt(index) ?: restingBlock(block) }
        // Clipped to the window, so an event that starts before it is drawn with its head cut off at
        // the top edge instead of being pushed above the grid or dropped.
        val placements = rendered.fastMap { blockPlacement(block = it.block, window = window) }
        val placeables = measurables.fastMapIndexed { index, measurable ->
            measurable.measure(
                Constraints.fixed(
                    width = constraints.maxWidth / rendered[index].block.columns,
                    height = (placements[index].heightMinutes * minuteHeightPx).roundToInt(),
                ),
            )
        }
        layout(constraints.maxWidth, (minuteHeightPx * window.spanMinutes).roundToInt()) {
            placeables.fastForEachIndexed { index, placeable ->
                val slotWidth = constraints.maxWidth / rendered[index].block.columns
                // A column shift in flight slides the block over its neighbours so the drag is
                // visible before release; the grid never re-parents it.
                placeable.place(
                    x = rendered[index].block.column * slotWidth +
                        rendered[index].shiftColumns * constraints.maxWidth,
                    y = (placements[index].topMinutes * minuteHeightPx).roundToInt(),
                )
            }
        }
    }
}

/**
 * One block's cell: the tap and drag gestures, and the caller's slot inside them.
 *
 * Its own composable so it has a recompose scope of its own. [renderedAt] reads the live drag, and
 * routing that read through a [derivedStateOf] is what keeps a pointer move to the one cell whose
 * answer actually changed — the derived value is compared structurally, so the other cells' readers
 * are never invalidated.
 */
@Composable
private fun <T> KalendarBlockCell(
    index: Int,
    resizeHandleHeight: Dp,
    dragEnabled: Boolean,
    renderedAt: (Int) -> KalendarRenderedBlock<T>?,
    currentDrag: () -> KalendarBlockDrag?,
    onDragChange: (KalendarBlockDrag?) -> Unit,
    onDragRelease: (KalendarBlockDrag) -> Unit,
    onEventClick: (KalendarEvent) -> Unit,
    eventContent: @Composable (block: KalendarScheduleBlock, isDragging: Boolean, target: T) -> Unit,
) {
    val rendered by remember(index) { derivedStateOf { renderedAt(index) } }
    val cell = rendered ?: return
    val dragModifier = if (dragEnabled) {
        Modifier.blockDragGestures(
            index = index,
            resizeHandleHeight = resizeHandleHeight,
            onDragStateChange = onDragChange,
            currentState = currentDrag,
            onRelease = onDragRelease,
        )
    } else {
        Modifier
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(dragModifier)
            .clickable(role = Role.Button) { onEventClick(cell.block.event) },
    ) {
        eventContent(cell.block, cell.isDragging, cell.target)
    }
}

/**
 * The identity a block's slot subtree is keyed by, so whatever state a caller's `eventContent` holds
 * — an expand animation, a text-field cursor — follows its event instead of its position in the day.
 *
 * [KalendarEvent.id] is nullable and list position is exactly the wrong fallback: inserting an
 * earlier event shifts every later index and hands each slot a different event. A block's own minute
 * range and overlap column survives that insertion, and is unique within one column, because two
 * blocks that start together necessarily sit in different columns.
 */
private fun blockKey(block: KalendarScheduleBlock): Any =
    block.event.id ?: "${block.startMinutes}-${block.endMinutes}-${block.column}"

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
 * [KalendarBlockLayout].
 */
private fun Modifier.blockDragGestures(
    index: Int,
    resizeHandleHeight: Dp,
    onDragStateChange: (KalendarBlockDrag?) -> Unit,
    currentState: () -> KalendarBlockDrag?,
    onRelease: (state: KalendarBlockDrag) -> Unit,
): Modifier = pointerInput(index, resizeHandleHeight) {
    detectDragGesturesAfterLongPress(
        onDragStart = { offset ->
            onDragStateChange(
                KalendarBlockDrag(
                    index = index,
                    mode = scheduleDragMode(
                        pressYPx = offset.y,
                        blockHeightPx = size.height.toFloat(),
                        resizeHandlePx = resizeHandleHeight.toPx(),
                    ),
                ),
            )
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
