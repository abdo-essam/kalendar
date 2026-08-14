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

import androidx.compose.runtime.Immutable
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.plus

/**
 * A time range swept out on the grid, in minutes from the day's midnight.
 *
 * @property startMinutes Inclusive start of the range.
 * @property endMinutes Exclusive end of the range, always at least [MIN_SCHEDULE_BLOCK_MINUTES]
 *   after [startMinutes]. May be exactly [MINUTES_PER_DAY], meaning midnight at the end of the day.
 */
@Immutable
public class KalendarScheduleCreateRange(
    public val startMinutes: Int,
    public val endMinutes: Int,
) {

    /** Returns a duplicate of this range with only the values passed here replaced. */
    public fun copy(
        startMinutes: Int = this.startMinutes,
        endMinutes: Int = this.endMinutes,
    ): KalendarScheduleCreateRange = KalendarScheduleCreateRange(
        startMinutes = startMinutes,
        endMinutes = endMinutes,
    )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is KalendarScheduleCreateRange) return false
        return startMinutes == other.startMinutes && endMinutes == other.endMinutes
    }

    override fun hashCode(): Int = 31 * startMinutes + endMinutes

    override fun toString(): String =
        "KalendarScheduleCreateRange(startMinutes=$startMinutes, endMinutes=$endMinutes)"
}

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
 * then grown to [MIN_SCHEDULE_BLOCK_MINUTES] if the sweep was shorter.
 *
 * ```kotlin
 * val range = scheduleCreateRange(
 *     anchorPx = pressY,
 *     currentPx = dragY,
 *     minutePx = hourHeightPx / MINUTES_PER_HOUR,
 *     snapMinutes = 15,
 *     window = window,
 * )
 * ```
 *
 * Both pixel positions are measured from the top of the drawn grid, not from midnight. A
 * minimum-length range that would spill past the bottom of the window is pushed back up against it
 * instead of overflowing.
 */
public fun scheduleCreateRange(
    anchorPx: Float,
    currentPx: Float,
    minutePx: Float,
    snapMinutes: Int,
    window: KalendarHourWindow = KalendarHourWindow.FullDay,
): KalendarScheduleCreateRange {
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
        KalendarScheduleCreateRange(
            startMinutes = window.endMinutes - MIN_SCHEDULE_BLOCK_MINUTES,
            endMinutes = window.endMinutes,
        )
    } else {
        KalendarScheduleCreateRange(startMinutes = start, endMinutes = end)
    }
}

/**
 * The (start, end) date-times to report for a released create-drag on [date].
 *
 * ```kotlin
 * val (start, end) = scheduleCreateTimes(range = range, date = day)
 * onCreateEvent(start, end)
 * ```
 *
 * As in [dragReleaseTimes], an end at exactly [MINUTES_PER_DAY] is midnight of the following day
 * rather than a 23:59 truncation.
 */
public fun scheduleCreateTimes(
    range: KalendarScheduleCreateRange,
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
 * Which of [blocks] a press at ([xPx], [yPx]) in a day column [widthPx] wide landed on, or `null`
 * for empty space.
 *
 * It uses the same column-splitting and minute-to-pixel geometry [scheduleBlocks] and
 * [blockPlacement] lay blocks out with — [yPx] included, which is measured from the top of [window]
 * rather than from midnight.
 *
 * ```kotlin
 * val grabbed = scheduleBlockAt(
 *     blocks = blocks,
 *     xPx = press.x,
 *     yPx = press.y,
 *     widthPx = columnWidth,
 *     minutePx = minutePx,
 *     window = window,
 * )
 * if (grabbed == null) startCreateDrag(press) else startMoveDrag(grabbed)
 * ```
 */
public fun scheduleBlockAt(
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
