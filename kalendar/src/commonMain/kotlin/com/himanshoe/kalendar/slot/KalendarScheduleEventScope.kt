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

package com.himanshoe.kalendar.slot

import androidx.compose.runtime.Stable
import com.himanshoe.kalendar.KalendarSchedule
import com.himanshoe.kalendar.KalendarScheduleWeek
import com.himanshoe.kalendar.foundation.event.KalendarEvent
import kotlinx.datetime.LocalDateTime

/**
 * One timed event as the Schedule views lay it out, for the `eventContent` slot on
 * [KalendarSchedule] and [KalendarScheduleWeek].
 *
 * The slot is given a box already positioned and sized for the event's duration and its share of
 * any overlap, so an implementation only draws — it never positions:
 *
 * ```kotlin
 * KalendarSchedule(
 *     state = rememberKalendarScheduleState(initialDate = today),
 *     events = events,
 * ) { scope ->
 *     MyEventCard(
 *         title = scope.event.eventName,
 *         subtitle = "${scope.start.time} – ${scope.end.time}",
 *         dimmed = scope.isDragging,
 *     )
 * }
 * ```
 *
 * [E] is the calendar's own event type, so [event] is the caller's domain type rather than
 * [KalendarEvent]. It is `out` so a block renderer written once against
 * `KalendarScheduleEventScope<KalendarEvent>` still serves a schedule of any event type.
 *
 * @see KalendarHeaderScope for why this is an interface.
 */
@Stable
public interface KalendarScheduleEventScope<out E : KalendarEvent> {

    /** The event being rendered. */
    public val event: E

    /**
     * The event's start, clamped to the day being rendered when the event began earlier.
     *
     * While a drag is in flight this is where the block currently *would* land, not where the event
     * still is — including the day, on [KalendarScheduleWeek], once the drag has crossed into
     * another column. The event itself is untouched until the view's change callback is honoured.
     */
    public val start: LocalDateTime

    /** The event's end, clamped to the day being rendered when the event runs past midnight. */
    public val end: LocalDateTime

    /** How many minutes of the rendered day this block covers, after clamping. */
    public val durationMinutes: Int

    /** Whether the user is currently dragging or resizing this block. */
    public val isDragging: Boolean

    /** Zero-based column among events that overlap this one; `0` when nothing overlaps. */
    public val overlapColumn: Int

    /** How many events share this block's time span, including itself. `1` when nothing overlaps. */
    public val overlapColumns: Int
}
