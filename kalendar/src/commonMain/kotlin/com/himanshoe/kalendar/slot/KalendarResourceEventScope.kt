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
import com.himanshoe.kalendar.KalendarResourceView
import com.himanshoe.kalendar.KalendarSchedule
import com.himanshoe.kalendar.KalendarScheduleWeek
import com.himanshoe.kalendar.foundation.event.KalendarEvent
import com.himanshoe.kalendar.foundation.schedule.KalendarResource

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
public interface KalendarResourceEventScope<out E : KalendarEvent> : KalendarScheduleEventScope<E> {

    /**
     * The lane this block is drawn in — while a cross-lane drag is in flight, the lane the block is
     * currently hovering over rather than the one it started in.
     */
    public val resource: KalendarResource
}
