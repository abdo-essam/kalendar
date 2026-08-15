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
import com.himanshoe.kalendar.component.KalendarDayCellDefaults
import com.himanshoe.kalendar.foundation.event.KalendarEvent
import kotlinx.datetime.LocalDate

/**
 * What one day's overflow popover is given to draw: the date, that date's events, and the callbacks
 * that report a tap and close the popover.
 *
 * Passed to the `overflowPopup` slot on [KalendarDayCellDefaults.Cell], and to
 * [KalendarDayCellDefaults.OverflowPopup] when the slot is left at its default. [events] is the
 * date's *whole* event list rather than only the hidden tail: a popover opened from a `+2` label
 * that showed the fourth and fifth event without the first three would be a list with no beginning,
 * so the count that was hidden is reported separately as [hiddenEventCount] for a popover that wants
 * to mark the cut-off.
 *
 * This is an interface rather than a data class so later releases can add information to it without
 * breaking every existing `overflowPopup` lambda.
 *
 * [E] is the calendar's own event type, so a replacement popover lists the caller's domain type and
 * reports it back unchanged. It is `out` for the reason [KalendarDayScope]'s is.
 */
@Stable
public interface KalendarDayOverflowScope<out E : KalendarEvent> {

    /** The date whose events the popover is listing. */
    public val date: LocalDate

    /** Every event on [date], in the order the day cell received them — not just the hidden ones. */
    public val events: List<E>

    /**
     * How many of [events] the cell's indicator dots did not represent, i.e. the `N` in its `+N`
     * label. Always at least 1 while the popover is open, since nothing else opens it.
     */
    public val hiddenEventCount: Int

    /**
     * Reports [event] to the cell's `onEventClick` and closes the popover, in that order. A popover
     * that wants to stay open should call the callback it was built with instead of this.
     *
     * [event] is the one `in` position [E] has, and is sound for the same reason
     * [KalendarDayScope.copy]'s is: the only events a popover has to report are the ones in [events].
     */
    public fun onEventClick(event: @UnsafeVariance E)

    /**
     * Closes the popover. Tapping outside it and pressing escape already do this; call it for a
     * close button of your own.
     */
    public fun dismiss()
}
