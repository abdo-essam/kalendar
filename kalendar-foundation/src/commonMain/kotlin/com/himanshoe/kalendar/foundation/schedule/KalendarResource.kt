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
import androidx.compose.ui.graphics.Color
import com.himanshoe.kalendar.foundation.event.KalendarEvent
import kotlinx.datetime.LocalDate
import kotlin.math.roundToInt

private const val HASH_FACTOR = 31

/**
 * One schedulable lane in a resource view — a room, a chair, a vehicle, a member of staff.
 *
 * A resource is identity plus presentation and nothing else: it owns no events. Events are matched
 * to it by a `resourceIdOf` accessor returning this resource's [id], which keeps [KalendarEvent] — a
 * shipped interface Kalendar cannot add members to — out of the picture:
 *
 * ```kotlin
 * val rooms = listOf(
 *     KalendarResource(id = "room-a", title = "Room A", color = Color(0xFF3B6EF3)),
 *     KalendarResource(id = "room-b", title = "Room B", order = 1),
 * )
 *
 * val lanes = resourceBlocks(
 *     events = bookings,
 *     resources = orderedResources(rooms),
 *     resourceIdOf = { event -> (event as Booking).roomId },
 *     date = today,
 * )
 * ```
 *
 * @property id Stable identifier, unique across the list handed to a view. Columns are keyed by it
 *   and drag results report it, so it must survive edits and reordering.
 * @property title Human-readable name shown in the column header and announced by screen readers.
 * @property color Accent for this lane — the header's underline, and the fallback tint for blocks
 *   whose event has no [KalendarEvent.eventColor] of its own. `null`, the default, leaves the choice
 *   to whatever is drawing the lane.
 * @property order Sort hint. [orderedResources] lays lanes out by ascending order, ties keeping the
 *   order of the list as given. Defaults to `0`, which leaves the list's own order untouched.
 */
@Immutable
public class KalendarResource(
    public val id: String,
    public val title: String,
    public val color: Color? = null,
    public val order: Int = 0,
) {

    /** Returns a duplicate of this resource with only the values passed here replaced. */
    public fun copy(
        id: String = this.id,
        title: String = this.title,
        color: Color? = this.color,
        order: Int = this.order,
    ): KalendarResource = KalendarResource(
        id = id,
        title = title,
        color = color,
        order = order,
    )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is KalendarResource) return false
        if (id != other.id) return false
        if (title != other.title) return false
        if (color != other.color) return false
        if (order != other.order) return false
        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = HASH_FACTOR * result + title.hashCode()
        result = HASH_FACTOR * result + color.hashCode()
        result = HASH_FACTOR * result + order
        return result
    }

    override fun toString(): String = "KalendarResource(id=$id, title=$title, color=$color, order=$order)"
}

/**
 * The lanes in the order they should be drawn: ascending [KalendarResource.order], ties keeping the
 * order of the list as given, so a caller that leaves `order` at its default gets its own ordering
 * back untouched.
 *
 * ```kotlin
 * val lanes = orderedResources(rooms)
 * ```
 */
public fun orderedResources(resources: List<KalendarResource>): List<KalendarResource> =
    resources.sortedBy { it.order }

/**
 * Lays out [events] — already narrowed to the day being drawn — as one list of schedule blocks per
 * lane, keyed by [KalendarResource.id].
 *
 * ```kotlin
 * val lanes = orderedResources(rooms)
 * val blocksByLane = resourceBlocks(
 *     events = todaysBookings,
 *     resources = lanes,
 *     resourceIdOf = { it.calendarId },
 *     date = today,
 * )
 * lanes.forEach { lane -> drawLane(lane, blocksByLane[lane.id].orEmpty()) }
 * ```
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
public fun resourceBlocks(
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
 * ```kotlin
 * val shift = resourceShiftForDrag(rawDeltaXPx = dragX, resourceColumnWidthPx = laneWidth, resourceCount = lanes.size)
 * val landedIn = lanes[shiftedResourceIndex(index = startIndex, shift = shift, resourceCount = lanes.size)]
 * ```
 *
 * @param rawDeltaXPx Accumulated horizontal drag distance in pixels.
 * @param resourceColumnWidthPx The measured width of one lane column. `0` (nothing measured yet)
 *   yields no shift rather than a division by zero.
 * @param resourceCount How many lanes are on screen; the shift cannot exceed moving from the first
 *   lane to the last.
 */
public fun resourceShiftForDrag(
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
 *
 * ```kotlin
 * shiftedResourceIndex(index = 1, shift = 4, resourceCount = 3) // 2, the last lane
 * ```
 */
public fun shiftedResourceIndex(index: Int, shift: Int, resourceCount: Int): Int {
    if (resourceCount <= 0) {
        return 0
    }
    return (index + shift).coerceIn(0, resourceCount - 1)
}
