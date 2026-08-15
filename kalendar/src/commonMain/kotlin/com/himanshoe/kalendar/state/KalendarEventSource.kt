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

package com.himanshoe.kalendar.state

import com.himanshoe.kalendar.foundation.event.KalendarEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Where a calendar gets the events for one visible range.
 *
 * This is the other half of [rememberKalendarEventLoader]: the loader works out which ranges are on
 * screen or about to be, and asks this for each of them. A source is called once per range and its
 * flow is collected for as long as that range is held, so a database query that re-emits keeps the
 * calendar current without the calendar polling anything:
 *
 * ```kotlin
 * val loader = rememberKalendarEventLoader(state = monthState) { range ->
 *     bookingDao.observeBetween(from = range.start, to = range.endInclusive)
 * }
 * ```
 *
 * For a one-shot load — a network call, a `suspend` DAO — use [suspending], which wraps the call in
 * a flow that emits once:
 *
 * ```kotlin
 * val loader = rememberKalendarEventLoader(
 *     state = monthState,
 *     source = KalendarEventSource.suspending { range -> api.bookings(range.start, range.endInclusive) },
 * )
 * ```
 *
 * Cancellation is the loader's job, not the source's: when the user swipes past a range, the
 * collection of that range's flow is cancelled, so a suspending call inside it is cancelled too.
 * A source therefore needs no idea of what is on screen.
 *
 * [E] is `out` because a source only ever produces events: a `KalendarEventSource<Booking>` is
 * usable anywhere a `KalendarEventSource<KalendarEvent>` is asked for.
 */
public fun interface KalendarEventSource<out E : KalendarEvent> {

    /**
     * The events falling anywhere in [range], re-emitted whenever they change.
     *
     * Return every event that *touches* the range, including a multi-day event that starts before
     * it — the views expand spans across the dates they occupy, and can only do so for events they
     * were given. An event returned for two adjacent ranges is de-duplicated by
     * [KalendarEvent.id], so give events an id when a range query can hand the same one back twice.
     */
    public fun eventsIn(range: KalendarDateRange): Flow<List<E>>

    public companion object {

        /**
         * A source backed by a one-shot suspending load — the shape of a network call or a
         * `suspend` query, which produces a range's events once rather than watching them.
         *
         * ```kotlin
         * KalendarEventSource.suspending { range -> api.bookings(range.start, range.endInclusive) }
         * ```
         */
        public fun <E : KalendarEvent> suspending(
            load: suspend (range: KalendarDateRange) -> List<E>,
        ): KalendarEventSource<E> = KalendarEventSource { range -> flow { emit(load(range)) } }
    }
}
