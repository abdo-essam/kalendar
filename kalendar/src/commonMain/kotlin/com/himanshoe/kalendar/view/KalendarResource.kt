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
import androidx.compose.runtime.Stable
import androidx.compose.ui.graphics.Color
import com.himanshoe.kalendar.foundation.event.KalendarEvent

/**
 * One schedulable lane in [KalendarResourceView] — a room, a chair, a vehicle, a member of staff.
 *
 * A resource is identity plus presentation and nothing else: it owns no events. Events are matched
 * to it by the view's `resourceIdOf` accessor returning this resource's [id], which keeps
 * [KalendarEvent] — a shipped interface Kalendar cannot add members to — out of the picture:
 *
 * ```kotlin
 * val rooms = listOf(
 *     KalendarResource(id = "room-a", title = "Room A", color = Color(0xFF3B6EF3)),
 *     KalendarResource(id = "room-b", title = "Room B", order = 1),
 * )
 *
 * KalendarResourceView(
 *     resources = rooms,
 *     events = bookings,
 *     resourceIdOf = { event -> (event as Booking).roomId },
 * )
 * ```
 *
 * @property id Stable identifier, unique across the list handed to a view. Columns are keyed by it
 *   and drag results report it, so it must survive edits and reordering.
 * @property title Human-readable name shown in the column header and announced by screen readers.
 * @property color Accent for this lane — the header's underline, and the fallback tint for blocks
 *   whose event has no [KalendarEvent.eventColor] of its own. `null`, the default, falls back to
 *   [KalendarColors.eventIndicator].
 * @property order Sort hint. Columns are laid out by ascending order, ties keeping the order of the
 *   list as given. Defaults to `0`, which leaves the list's own order untouched.
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

private const val HASH_FACTOR = 31

/**
 * One timed event as [KalendarResourceView] lays it out, for its `eventContent` slot.
 *
 * It is [KalendarScheduleEventScope] plus the lane the block landed in, so an `eventContent` written
 * for [KalendarSchedule] or [KalendarScheduleWeek] can be reused here unchanged and one written for
 * this view can additionally tint or label itself per resource:
 *
 * ```kotlin
 * KalendarResourceView(resources = rooms, events = bookings) { scope ->
 *     MyBookingCard(
 *         title = scope.event.eventName,
 *         room = scope.resource.title,
 *         dimmed = scope.isDragging,
 *     )
 * }
 * ```
 *
 * @see KalendarHeaderScope for why this is an interface rather than a data class.
 */
@Stable
public interface KalendarResourceEventScope : KalendarScheduleEventScope {

    /**
     * The lane this block is drawn in — while a cross-lane drag is in flight, the lane the block is
     * currently hovering over rather than the one it started in.
     */
    public val resource: KalendarResource
}
