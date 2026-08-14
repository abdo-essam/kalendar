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

import androidx.compose.runtime.Immutable
import kotlinx.datetime.LocalDateTime

/**
 * The slice of the day the hour-grid views draw: [KalendarSchedule], [KalendarScheduleWeek] and
 * [KalendarResourceView] render [startHour] through [endHour] and nothing else.
 *
 * A business-hours calendar is `KalendarHourWindow(startHour = 8, endHour = 20)`. Everything the
 * grid derives from the day follows the window rather than midnight: the hour gutter labels only
 * these hours, the rules are drawn only between them, blocks are positioned relative to [startHour],
 * the now-indicator disappears when the clock is outside the window, and dragging, resizing and
 * sweeping out a new event clamp here instead of to `00:00..24:00`.
 *
 * Events are **not** filtered by the window, they are clipped by it: a 07:00–09:00 meeting in an
 * 08:00–20:00 window is drawn from the top of the grid to 09:00, with its start cut off, so the
 * window never silently hides that something is booked. Only an event lying entirely outside the
 * window is dropped, because it has no pixels to occupy.
 *
 * The window is a display range, not a business rule. It does not stop a caller's own events from
 * starting at 03:00, and callbacks always report real wall-clock times.
 *
 * @param startHour First hour drawn, `0..23`. Defaults to `0`, midnight.
 * @param endHour Hour the grid stops at, exclusive, `startHour + 1..24`. Defaults to `24`, so the
 *   window is the whole day. `20` means the grid's last labelled row is 19:00 and its bottom edge is
 *   20:00.
 * @throws IllegalArgumentException if the hours are out of range or do not describe a non-empty
 *   window.
 */
@Immutable
public class KalendarHourWindow(
    public val startHour: Int = 0,
    public val endHour: Int = HOURS_PER_DAY,
) {
    init {
        require(startHour in 0 until HOURS_PER_DAY) {
            "startHour must be in 0..${HOURS_PER_DAY - 1}, was $startHour"
        }
        require(endHour in (startHour + 1)..HOURS_PER_DAY) {
            "endHour must be in ${startHour + 1}..$HOURS_PER_DAY, was $endHour"
        }
    }

    /** How many hour rows the grid draws — `endHour - startHour`, always at least 1. */
    public val hourCount: Int get() = endHour - startHour

    /** [startHour] as minutes from midnight, the origin every block position is measured from. */
    internal val startMinutes: Int get() = startHour * MINUTES_PER_HOUR

    /** [endHour] as minutes from midnight; `1440` for a full-day window. */
    internal val endMinutes: Int get() = endHour * MINUTES_PER_HOUR

    /** The window's height in minutes, which is also the grid's scrollable extent. */
    internal val spanMinutes: Int get() = endMinutes - startMinutes

    /** The hours to label, in order, top to bottom. */
    internal val hours: IntRange get() = startHour until endHour

    /** Whether [minutesFromMidnight] falls inside the window, its exclusive end excluded. */
    internal operator fun contains(minutesFromMidnight: Int): Boolean =
        minutesFromMidnight >= startMinutes && minutesFromMidnight < endMinutes

    /**
     * [minutesFromMidnight] clamped into the window. Callers that then add a duration must clamp
     * again — this only guarantees a single instant is inside.
     */
    internal fun clamp(minutesFromMidnight: Int): Int = minutesFromMidnight.coerceIn(startMinutes, endMinutes)

    /**
     * Returns a duplicate of this window with only the hours passed here replaced. The same
     * validation the constructor performs applies to the result.
     */
    public fun copy(
        startHour: Int = this.startHour,
        endHour: Int = this.endHour,
    ): KalendarHourWindow = KalendarHourWindow(startHour = startHour, endHour = endHour)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is KalendarHourWindow) return false
        return startHour == other.startHour && endHour == other.endHour
    }

    override fun hashCode(): Int = 31 * startHour + endHour

    override fun toString(): String = "KalendarHourWindow(startHour=$startHour, endHour=$endHour)"

    /** Ready-made windows. */
    public companion object {

        /**
         * Midnight to midnight — the default, and what every hour-grid view rendered before the
         * window existed.
         */
        public val FullDay: KalendarHourWindow = KalendarHourWindow()
    }
}

/**
 * Where a block sits in the grid's viewport: both figures are minutes measured from the top of the
 * visible window, not from midnight.
 *
 * @property topMinutes Distance from the top of the grid to the block's drawn top edge.
 * @property heightMinutes Drawn height. Zero when the block lies entirely outside the window.
 */
internal data class KalendarBlockPlacement(val topMinutes: Int, val heightMinutes: Int)

/**
 * Where [block] is drawn inside [window], clipped to it.
 *
 * A block that starts before the window is drawn from the window's top edge with its head cut off,
 * and one that ends after it is cut off at the bottom — the alternative, dropping either, would let
 * a business-hours calendar quietly stop showing the meeting that runs into lunch. A block wholly
 * outside the window gets a zero height, which draws nothing.
 */
internal fun blockPlacement(
    block: KalendarScheduleBlock,
    window: KalendarHourWindow,
): KalendarBlockPlacement {
    val top = window.clamp(block.startMinutes)
    val bottom = window.clamp(block.endMinutes)
    return KalendarBlockPlacement(
        topMinutes = top - window.startMinutes,
        heightMinutes = (bottom - top).coerceAtLeast(0),
    )
}

/**
 * How far down the grid a page opens, in hours, given the caller's
 * [KalendarViewConfig.scheduleInitialScrollHour] and the visible [window].
 *
 * The configured hour is absolute (7 means 07:00), so on an 08:00–20:00 window it is already above
 * the top of the grid and the answer is 0 rather than a negative scroll. Below the window it clamps
 * to the bottom.
 */
internal fun initialScrollHourOffset(initialScrollHour: Int, window: KalendarHourWindow): Int =
    (initialScrollHour - window.startHour).coerceIn(0, window.hourCount)

/** A wall-clock time as minutes since midnight, which is the unit the hour grids position in. */
internal fun LocalDateTime.minutesFromMidnight(): Int = hour * MINUTES_PER_HOUR + minute

/**
 * [coerceIn] that cannot throw when the bounds cross.
 *
 * They do cross legitimately once the grid shows less than the whole day: an event longer than an
 * 08:00–20:00 window has no start that keeps it wholly inside, so `windowEnd - duration` falls below
 * `windowStart`. Pinning to [minimum] is the right answer there — the block sits at the top of the
 * grid and overflows the bottom, which [blockPlacement] then clips.
 */
internal fun Int.coerceWithin(minimum: Int, maximum: Int): Int = coerceIn(minimum, maxOf(minimum, maximum))
