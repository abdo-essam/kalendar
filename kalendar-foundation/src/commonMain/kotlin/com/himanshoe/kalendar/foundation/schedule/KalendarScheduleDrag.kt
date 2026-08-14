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

package com.himanshoe.kalendar.foundation.schedule

import com.himanshoe.kalendar.foundation.datetime.DAYS_PER_WEEK
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.plus
import kotlin.math.roundToInt

/**
 * A block's resize strips never take more than this fraction of its height, so a short block keeps
 * a grabbable middle that still moves rather than resizes.
 */
private const val RESIZE_HANDLE_HEIGHT_DIVISOR = 3f

/**
 * Which part of a block a press grabbed, and so what a drag from there does.
 *
 * ```kotlin
 * val mode = scheduleDragMode(pressYPx = y, blockHeightPx = height, resizeHandlePx = handlePx)
 * val preview = adjustedScheduleBlockForDrag(block = block, deltaMinutes = delta, mode = mode)
 * ```
 */
public enum class KalendarScheduleDragMode {

    /** Shift the whole block, preserving its duration. */
    Move,

    /** Move the block's top edge, pinning its end. */
    ResizeStart,

    /** Move the block's bottom edge, pinning its start. */
    ResizeEnd,
}

/**
 * Converts an accumulated raw drag distance into a snapped minute delta in a single rounding
 * step ([rawPx] → snap units directly), avoiding the drift a two-stage px→minutes→snap rounding
 * introduces for snap values that don't divide 60.
 *
 * ```kotlin
 * val delta = snappedDragMinutes(rawPx = dragY, minutePx = hourHeightPx / MINUTES_PER_HOUR, snapMinutes = 15)
 * ```
 *
 * @param rawPx Accumulated drag distance in pixels, positive downwards.
 * @param minutePx Pixels one minute occupies. `0` or less yields no movement rather than a division
 *   by zero — which is what a grid that has not been measured yet reports.
 * @param snapMinutes Step to snap to. Must be positive; anything else yields no movement.
 */
public fun snappedDragMinutes(rawPx: Float, minutePx: Float, snapMinutes: Int): Int {
    if (minutePx <= 0f || snapMinutes <= 0) {
        return 0
    }
    return (rawPx / (minutePx * snapMinutes)).roundToInt() * snapMinutes
}

/**
 * Applies a drag [deltaMinutes] to [block]: resizing moves only the end (clamped to at least
 * [MIN_SCHEDULE_BLOCK_MINUTES] long and at most the end of [window]), moving shifts the whole block
 * within [window] while preserving its duration.
 *
 * ```kotlin
 * val moved = adjustedScheduleBlock(block = block, deltaMinutes = 60, resizing = false)
 * ```
 *
 * The clamp is the visible window rather than the day, so a drag cannot push a block off the top or
 * bottom of a business-hours grid into space the user cannot scroll to and would have no way to
 * drag it back from.
 */
public fun adjustedScheduleBlock(
    block: KalendarScheduleBlock,
    deltaMinutes: Int,
    resizing: Boolean,
    window: KalendarHourWindow = KalendarHourWindow.FullDay,
): KalendarScheduleBlock = if (resizing) {
    val newEnd = (block.endMinutes + deltaMinutes)
        .coerceWithin(block.startMinutes + MIN_SCHEDULE_BLOCK_MINUTES, window.endMinutes)
    block.copy(endMinutes = newEnd)
} else {
    val duration = block.endMinutes - block.startMinutes
    val newStart = (block.startMinutes + deltaMinutes)
        .coerceWithin(window.startMinutes, window.endMinutes - duration)
    block.copy(startMinutes = newStart, endMinutes = newStart + duration)
}

/**
 * Which edge — if either — a press at [pressYPx] within a block [blockHeightPx] tall grabbed, given
 * a nominal [resizeHandlePx] strip at each edge.
 *
 * The strip is narrowed to `blockHeightPx / 3` when the block is too short for two full strips plus
 * a middle, so the top strip, the bottom strip and the move region never collide — a 15-minute block
 * is still movable rather than being all handle.
 *
 * ```kotlin
 * scheduleDragMode(pressYPx = 4f, blockHeightPx = 120f, resizeHandlePx = 12f) // ResizeStart
 * ```
 */
public fun scheduleDragMode(
    pressYPx: Float,
    blockHeightPx: Float,
    resizeHandlePx: Float,
): KalendarScheduleDragMode {
    if (blockHeightPx <= 0f) {
        return KalendarScheduleDragMode.Move
    }
    val handle = resizeHandlePx.coerceAtMost(blockHeightPx / RESIZE_HANDLE_HEIGHT_DIVISOR)
    return when {
        pressYPx > blockHeightPx - handle -> KalendarScheduleDragMode.ResizeEnd
        pressYPx < handle -> KalendarScheduleDragMode.ResizeStart
        else -> KalendarScheduleDragMode.Move
    }
}

/**
 * Applies a snapped drag [deltaMinutes] to [block] for the grabbed [mode].
 *
 * [KalendarScheduleDragMode.Move] and [KalendarScheduleDragMode.ResizeEnd] are exactly
 * [adjustedScheduleBlock]'s two behaviours; [KalendarScheduleDragMode.ResizeStart] is their mirror —
 * the start moves, the end stays put, and the start can never cross to within less than
 * [MIN_SCHEDULE_BLOCK_MINUTES] of the end nor run past the top of [window].
 *
 * ```kotlin
 * val preview = adjustedScheduleBlockForDrag(
 *     block = block,
 *     deltaMinutes = snappedDragMinutes(rawPx = dragY, minutePx = minutePx, snapMinutes = 15),
 *     mode = KalendarScheduleDragMode.ResizeStart,
 * )
 * ```
 */
public fun adjustedScheduleBlockForDrag(
    block: KalendarScheduleBlock,
    deltaMinutes: Int,
    mode: KalendarScheduleDragMode,
    window: KalendarHourWindow = KalendarHourWindow.FullDay,
): KalendarScheduleBlock = when (mode) {
    KalendarScheduleDragMode.Move -> adjustedScheduleBlock(
        block = block,
        deltaMinutes = deltaMinutes,
        resizing = false,
        window = window,
    )

    KalendarScheduleDragMode.ResizeEnd -> adjustedScheduleBlock(
        block = block,
        deltaMinutes = deltaMinutes,
        resizing = true,
        window = window,
    )

    KalendarScheduleDragMode.ResizeStart -> block.copy(
        startMinutes = (block.startMinutes + deltaMinutes)
            .coerceIn(
                window.startMinutes,
                maxOf(window.startMinutes, block.endMinutes - MIN_SCHEDULE_BLOCK_MINUTES),
            ),
    )
}

/**
 * Converts a horizontal drag distance into whole day columns on a week grid, dividing by the full
 * day-column width and clamped to one grid width in either direction.
 *
 * ```kotlin
 * val columns = weekDates(anchor, startDayOfWeek, workWeek) // the dates the grid draws, in order
 * val column = columns.indexOf(block.date)
 * val shift = dayShiftForDrag(rawDeltaXPx = dragX, dayColumnWidthPx = columnWidth, columnCount = columns.size)
 * val landedOn = columns[(column + shift).coerceIn(0, columns.lastIndex)]
 * ```
 *
 * [columnCount] is the number of day columns actually drawn, which is not always seven — a Mon–Fri
 * week has five, and clamping to six would let a drag land the block a column past the grid's edge.
 *
 * The result counts *columns*, and the clamp here is symmetric about zero rather than about the
 * block's own column, so it bounds the shift's magnitude and nothing else: from a midweek column a
 * full-width shift still points past the grid's edge. Resolve it by **indexing into the dates the
 * grid draws** and clamping that index, as above — a relative walk from the block's date, whether by
 * adding days or by stepping visible ones with
 * [visibleDateStep][com.himanshoe.kalendar.foundation.grid.visibleDateStep], lands a flung drag in a
 * neighbouring week instead of stopping at the edge.
 */
public fun dayShiftForDrag(
    rawDeltaXPx: Float,
    dayColumnWidthPx: Int,
    columnCount: Int = DAYS_PER_WEEK,
): Int {
    if (dayColumnWidthPx <= 0) {
        return 0
    }
    val maximumShift = (columnCount - 1).coerceAtLeast(0)
    return (rawDeltaXPx / dayColumnWidthPx).roundToInt().coerceIn(-maximumShift, maximumShift)
}

/**
 * The (start, end) date-times to report for a released drag of [block] onto [targetDate].
 *
 * ```kotlin
 * val (start, end) = dragReleaseTimes(block = adjusted, targetDate = day)
 * onEventMoved(block.event, start, end)
 * ```
 *
 * An end at exactly [MINUTES_PER_DAY] is midnight of the following day — [LocalTime] cannot express
 * 24:00, so it must not be silently truncated to 23:59.
 */
public fun dragReleaseTimes(
    block: KalendarScheduleBlock,
    targetDate: LocalDate,
): Pair<LocalDateTime, LocalDateTime> {
    val start = LocalDateTime(targetDate, minutesToLocalTime(block.startMinutes))
    val end = if (block.endMinutes >= MINUTES_PER_DAY) {
        LocalDateTime(targetDate.plus(1, DateTimeUnit.DAY), LocalTime(hour = 0, minute = 0))
    } else {
        LocalDateTime(targetDate, minutesToLocalTime(block.endMinutes))
    }
    return start to end
}

/**
 * Minutes from midnight as a wall-clock time, clamped into the day.
 *
 * ```kotlin
 * minutesToLocalTime(9 * MINUTES_PER_HOUR + 30) // 09:30
 * ```
 *
 * [MINUTES_PER_DAY] itself clamps to 23:59 rather than throwing, because [LocalTime] has no 24:00 —
 * a caller that needs the distinction should use [dragReleaseTimes], which rolls to the next day.
 */
public fun minutesToLocalTime(minutes: Int): LocalTime {
    val clamped = minutes.coerceIn(0, MINUTES_PER_DAY - 1)
    return LocalTime(hour = clamped / MINUTES_PER_HOUR, minute = clamped % MINUTES_PER_HOUR)
}
