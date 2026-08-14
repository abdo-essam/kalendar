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

import androidx.compose.runtime.Stable
import com.himanshoe.kalendar.foundation.event.KalendarEvent
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime

/**
 * Everything a replacement calendar header needs: what to show, whether navigation is possible,
 * and how to navigate.
 *
 * Passed to the `header` slot on [KalendarWeek], [KalendarMonth], [KalendarYear],
 * [KalendarSchedule], and [KalendarScheduleWeek], so you can render your own title, your own
 * icons, or an entirely different chrome without losing navigation:
 *
 * ```kotlin
 * KalendarMonth(
 *     selectedDate = today,
 *     header = { scope ->
 *         Row(verticalAlignment = Alignment.CenterVertically) {
 *             MyIconButton(
 *                 icon = MyIcons.ChevronLeft,
 *                 enabled = scope.canScrollBackward,
 *                 onClick = scope::goToPreviousPage,
 *             )
 *             Text(text = scope.title, style = MyType.heading)
 *             MyIconButton(icon = MyIcons.ChevronRight, onClick = scope::goToNextPage)
 *         }
 *     },
 * )
 * ```
 *
 * This is an interface rather than a data class so later releases can add information to it
 * without breaking every existing `header` lambda.
 */
@Stable
public interface KalendarHeaderScope {

    /** The title the built-in header would show, e.g. `"August 2026"`, already formatted. */
    public val title: String

    /** The start date of the page currently on screen. */
    public val visibleDate: LocalDate

    /** Whether a previous page exists within the calendar's `minDate` bound. */
    public val canScrollBackward: Boolean

    /** Whether a next page exists within the calendar's `maxDate` bound. */
    public val canScrollForward: Boolean

    /** Animates to the previous week/month/year/day. A no-op when [canScrollBackward] is false. */
    public fun goToPreviousPage()

    /** Animates to the next week/month/year/day. A no-op when [canScrollForward] is false. */
    public fun goToNextPage()

    /** Animates back to the page containing today, clamped to the calendar's bounds. */
    public fun goToToday()

    /** Animates to the page containing [date], clamped to the calendar's bounds. */
    public fun goTo(date: LocalDate)
}

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
 * @see KalendarHeaderScope for why this is an interface.
 */
@Stable
public interface KalendarScheduleEventScope {

    /** The event being rendered. */
    public val event: KalendarEvent

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
