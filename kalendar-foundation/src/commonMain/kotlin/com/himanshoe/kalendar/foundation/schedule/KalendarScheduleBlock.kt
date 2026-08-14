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
import com.himanshoe.kalendar.foundation.event.KalendarEvent
import kotlinx.datetime.LocalDate

/**
 * A timed event positioned on an hour grid: its minute range within the day and the column slot it
 * occupies when overlapping events share the horizontal space.
 *
 * A block is geometry, not an event — it carries the [event] it came from so a caller can draw and
 * report it, but its own identity is the rectangle. Produce them with [scheduleBlocks]; turn one
 * into a drawing rectangle with [blockPlacement].
 *
 * ```kotlin
 * val columnWidth = dayWidth / block.columns
 * val left = block.column * columnWidth
 * ```
 *
 * @property event The event this block draws.
 * @property startMinutes Inclusive start of the block, in minutes from the day's midnight.
 * @property endMinutes Exclusive end, at least [MIN_SCHEDULE_BLOCK_MINUTES] after [startMinutes] and
 *   at most [MINUTES_PER_DAY] — where it means midnight of the following day.
 * @property column Zero-based slot within the event's overlap cluster.
 * @property columns Total slots in the cluster — every member of a cluster divides the width by
 *   the same count so side-by-side blocks align.
 */
@Immutable
public class KalendarScheduleBlock(
    public val event: KalendarEvent,
    public val startMinutes: Int,
    public val endMinutes: Int,
    public val column: Int,
    public val columns: Int,
) {

    /** Returns a duplicate of this block with only the values passed here replaced. */
    public fun copy(
        event: KalendarEvent = this.event,
        startMinutes: Int = this.startMinutes,
        endMinutes: Int = this.endMinutes,
        column: Int = this.column,
        columns: Int = this.columns,
    ): KalendarScheduleBlock = KalendarScheduleBlock(
        event = event,
        startMinutes = startMinutes,
        endMinutes = endMinutes,
        column = column,
        columns = columns,
    )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is KalendarScheduleBlock) return false
        if (event != other.event) return false
        if (startMinutes != other.startMinutes) return false
        if (endMinutes != other.endMinutes) return false
        if (column != other.column) return false
        if (columns != other.columns) return false
        return true
    }

    override fun hashCode(): Int {
        var result = event.hashCode()
        result = 31 * result + startMinutes
        result = 31 * result + endMinutes
        result = 31 * result + column
        result = 31 * result + columns
        return result
    }

    override fun toString(): String = "KalendarScheduleBlock(" +
        "event=$event, " +
        "startMinutes=$startMinutes, " +
        "endMinutes=$endMinutes, " +
        "column=$column, " +
        "columns=$columns" +
        ")"
}

/**
 * Lays out the timed events (those with a non-null [KalendarEvent.startTime]) of [date] as
 * schedule blocks: minute ranges clamped to the day (events spanning midnight are truncated at
 * the day's edges), a missing [KalendarEvent.endTime] defaulting to one hour, and overlapping
 * events packed greedily into side-by-side columns per overlap cluster.
 *
 * This is the whole of a day view's layout. Everything after it is arithmetic on the result:
 *
 * ```kotlin
 * val window = KalendarHourWindow(startHour = 8, endHour = 20)
 * scheduleBlocks(events = todaysEvents, date = today, window = window).forEach { block ->
 *     val placement = blockPlacement(block = block, window = window)
 *     val width = dayWidth / block.columns
 *     drawBlock(
 *         x = block.column * width,
 *         y = placement.topMinutes * minutePx,
 *         width = width,
 *         height = placement.heightMinutes * minutePx,
 *     )
 * }
 * ```
 *
 * Blocks keep their true minute range even when [window] shows less than the whole day — clipping is
 * a drawing concern ([blockPlacement]), and truncating here would make a drag report the clipped
 * time rather than the event's own. Events that do not intersect [window] at all are dropped, so the
 * result is exactly what the grid draws and every block the caller is handed has a hit target.
 */
public fun scheduleBlocks(
    events: List<KalendarEvent>,
    date: LocalDate,
    window: KalendarHourWindow = KalendarHourWindow.FullDay,
): List<KalendarScheduleBlock> {
    data class Working(val event: KalendarEvent, val start: Int, val end: Int) {
        var column: Int = 0
        var columns: Int = 1
    }

    val timed = events.mapNotNull { event ->
        val startTime = event.startTime ?: return@mapNotNull null
        val start = if (startTime.date < date) {
            0
        } else {
            startTime.hour * MINUTES_PER_HOUR + startTime.minute
        }
        val requestedEnd = event.endTime?.let { endTime ->
            if (endTime.date > date) {
                MINUTES_PER_DAY
            } else {
                endTime.hour * MINUTES_PER_HOUR + endTime.minute
            }
        } ?: (start + MINUTES_PER_HOUR)
        val clampedStart = start.coerceIn(0, MINUTES_PER_DAY - MIN_SCHEDULE_BLOCK_MINUTES)
        val end = requestedEnd.coerceIn(clampedStart + MIN_SCHEDULE_BLOCK_MINUTES, MINUTES_PER_DAY)
        Working(event = event, start = clampedStart, end = end)
            .takeIf { it.end > window.startMinutes && it.start < window.endMinutes }
    }.sortedWith(compareBy({ it.start }, { it.end }))

    val active = mutableListOf<Working>()
    val cluster = mutableListOf<Working>()
    for (candidate in timed) {
        active.removeAll { it.end <= candidate.start }
        if (active.isEmpty() && cluster.isNotEmpty()) {
            val width = cluster.maxOf { it.column } + 1
            cluster.forEach { it.columns = width }
            cluster.clear()
        }
        val occupied = active.map { it.column }.toSet()
        candidate.column = generateSequence(0) { it + 1 }.first { it !in occupied }
        active += candidate
        cluster += candidate
    }
    if (cluster.isNotEmpty()) {
        val width = cluster.maxOf { it.column } + 1
        cluster.forEach { it.columns = width }
    }

    return timed.map {
        KalendarScheduleBlock(
            event = it.event,
            startMinutes = it.start,
            endMinutes = it.end,
            column = it.column,
            columns = it.columns,
        )
    }
}
