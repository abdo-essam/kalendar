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

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.himanshoe.kalendar.foundation.event.KalendarEvent
import kotlinx.datetime.LocalDate
import kotlin.math.roundToInt

/**
 * The lanes in the order [KalendarResourceView] draws them: ascending [KalendarResource.order], ties
 * keeping the order of the list as given, so a caller that leaves `order` at its default gets its
 * own ordering back untouched.
 */
internal fun orderedResources(resources: List<KalendarResource>): List<KalendarResource> =
    resources.sortedBy { it.order }

/**
 * Lays out [events] — already narrowed to the day being drawn — as one list of schedule blocks per
 * lane, keyed by [KalendarResource.id].
 *
 * Each lane is packed independently by [scheduleBlocks], so overlap columns are per lane: two
 * bookings that clash inside one room split that room's column and leave every other room full
 * width. Events whose [resourceIdOf] is `null` or names no known lane are dropped, as are untimed
 * ones — [scheduleBlocks] keeps only events with a [KalendarEvent.startTime].
 *
 * @param events The day's events.
 * @param resources The lanes to bucket into; only their [KalendarResource.id]s are read.
 * @param resourceIdOf Maps an event to the id of the lane it belongs in, or `null` for none.
 * @param date The day being drawn, used to clamp blocks that start or end outside it.
 * @param window The visible slice of the day; events outside it are dropped, so a lane's overlap
 *   clusters are packed from what is actually on screen.
 */
internal fun resourceBlocks(
    events: List<KalendarEvent>,
    resources: List<KalendarResource>,
    resourceIdOf: (KalendarEvent) -> String?,
    date: LocalDate,
    window: KalendarHourWindow = KalendarHourWindow.FullDay,
): Map<String, List<KalendarScheduleBlock>> {
    val known = resources.mapTo(mutableSetOf()) { it.id }
    val byResource = mutableMapOf<String, MutableList<KalendarEvent>>()
    for (event in events) {
        val id = resourceIdOf(event)?.takeIf { it in known } ?: continue
        byResource.getOrPut(id) { mutableListOf() }.add(event)
    }
    return byResource.mapValues { (_, laneEvents) ->
        scheduleBlocks(events = laneEvents, date = date, window = window)
    }
}

/**
 * Converts a horizontal drag distance into whole lane columns, dividing by the full column width —
 * never the dragged block's own width, which is a fraction of the column whenever events overlap —
 * and clamped to the number of lanes that exist.
 *
 * @param rawDeltaXPx Accumulated horizontal drag distance in pixels.
 * @param resourceColumnWidthPx The measured width of one lane column. `0` (nothing measured yet)
 *   yields no shift rather than a division by zero.
 * @param resourceCount How many lanes are on screen; the shift cannot exceed moving from the first
 *   lane to the last.
 */
internal fun resourceShiftForDrag(
    rawDeltaXPx: Float,
    resourceColumnWidthPx: Int,
    resourceCount: Int,
): Int {
    if (resourceColumnWidthPx <= 0 || resourceCount <= 1) {
        return 0
    }
    val maxShift = resourceCount - 1
    return (rawDeltaXPx / resourceColumnWidthPx).roundToInt().coerceIn(-maxShift, maxShift)
}

/**
 * Where a lane column index lands after [shift] columns of drag, clamped to the ends of the list so
 * dragging past the first or last lane parks the event there instead of reporting a lane that does
 * not exist.
 */
internal fun shiftedResourceIndex(index: Int, shift: Int, resourceCount: Int): Int {
    if (resourceCount <= 0) {
        return 0
    }
    return (index + shift).coerceIn(0, resourceCount - 1)
}

/**
 * The width of one lane column: the space left over after the hour gutter divided evenly between
 * the lanes, but never below [minColumnWidth] — past that point the columns keep their width and
 * the grid scrolls horizontally instead of squeezing every lane into an unreadable sliver.
 *
 * @param availableWidth Total width the grid has to work with.
 * @param hourGutterWidth Width of the shared hour gutter, which does not scroll with the lanes.
 * @param resourceCount How many lanes to fit.
 * @param minColumnWidth Narrowest a lane column may become before horizontal scrolling takes over.
 */
internal fun resourceColumnWidth(
    availableWidth: Dp,
    hourGutterWidth: Dp,
    resourceCount: Int,
    minColumnWidth: Dp,
): Dp {
    if (resourceCount <= 0) {
        return minColumnWidth
    }
    val usableWidth = availableWidth - hourGutterWidth
    if (usableWidth <= 0.dp) {
        return minColumnWidth
    }
    return maxOf(usableWidth / resourceCount, minColumnWidth)
}
