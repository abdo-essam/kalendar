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

package com.himanshoe.kalendar.testing

import com.himanshoe.kalendar.foundation.event.KalendarEvent
import com.himanshoe.kalendar.state.KalendarDateRange
import com.himanshoe.kalendar.state.KalendarEventSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion

/**
 * A [KalendarEventSource] that answers only when the test tells it to, so "asked and still
 * waiting", "answered", "failed" and "cancelled" are four states a test can hold a calendar in and
 * look at.
 *
 * Hand it to [com.himanshoe.kalendar.state.rememberKalendarEventLoader] exactly like a real
 * source. Each range asked for gets its own gate: the flow stays silent until [deliver] or [fail]
 * opens it, and a later [deliver] for the same range re-emits through the still-collected flow —
 * the shape of a database query that watches its result.
 *
 * ```kotlin
 * val source = KalendarTestEventSource<BasicKalendarEvent>()
 * rule.setContent {
 *     val state = rememberKalendarMonthState(initialDate = august)
 *     KalendarMonth(
 *         selectedDate = august,
 *         state = state,
 *         eventLoader = rememberKalendarEventLoader(state = state, source = source),
 *     )
 * }
 * // the month is now Loading — assert on the placeholder, then:
 * rule.runOnIdle { source.deliverAll(listOf(kalendarEvent(date = august, id = "1"))) }
 * rule.waitForIdle()
 * ```
 *
 * Everything here is synchronous and snapshot-free: [deliver] and [fail] set a
 * [MutableStateFlow], so they are safe from `runOnIdle`, from a `runTest` body, or from the test
 * thread directly. Two things follow from the [MutableStateFlow] gates:
 *
 * - **An answer can be primed before it is asked for.** Delivering for a range nothing has
 *   requested parks the answer at the gate, and a collection arriving later gets it immediately —
 *   a pre-loaded source, when waiting states are not what the test is about.
 * - **Delivering the same answer twice coalesces.** A state flow does not re-emit an equal value;
 *   deliver a different list when the test needs to observe a second emission.
 *
 * The recording lists make loader behaviour assertable from outside: [requestedRanges] says which
 * ranges were asked for and how many times, [cancelledRanges] which collections were cancelled
 * (a range the user swiped past, a calendar that left composition). Failures pushed through
 * [fail] complete that range's flow, which is exactly how a real source's exception reaches the
 * loader — the loader reports it via `statusAt`/`errorAt`, and its `retry()` collects the range
 * afresh from a new gate state.
 *
 * Not thread-safe across simultaneous writers; drive it from the test thread (or `runOnIdle`),
 * which is where every compose and coroutine test already runs.
 *
 * @param E The event type handed back, matching the loader under test.
 */
public class KalendarTestEventSource<E : KalendarEvent> : KalendarEventSource<E> {

    private val gates = mutableMapOf<KalendarDateRange, MutableStateFlow<Result<List<E>>?>>()
    private val requests = mutableListOf<KalendarDateRange>()
    private val cancellations = mutableListOf<KalendarDateRange>()

    /**
     * Every range asked for so far, in request order, one entry per collection — a range collected
     * twice (a retry, a page re-entering the window) appears twice.
     */
    public val requestedRanges: List<KalendarDateRange>
        get() = requests.toList()

    /**
     * Every range whose collection was cancelled while still running, in cancellation order — the
     * observable trace of "the loader stopped asking about a page nobody is looking at".
     */
    public val cancelledRanges: List<KalendarDateRange>
        get() = cancellations.toList()

    override fun eventsIn(range: KalendarDateRange): Flow<List<E>> {
        val gate = gate(range)
        return flow {
            requests += range
            emitAll(gate.filterNotNull().map { answer -> answer.getOrThrow() })
        }.onCompletion { cause ->
            if (cause is CancellationException) {
                cancellations += range
            }
        }
    }

    /**
     * Answers [range] with [events]. An empty list is a real answer — the range genuinely has no
     * events — and moves the range to Loaded, not back to Loading.
     */
    public fun deliver(range: KalendarDateRange, events: List<E> = emptyList()) {
        gate(range).value = Result.success(events)
    }

    /**
     * Answers every range requested so far with the same [events] — the window and nothing else.
     * The usual way to move a whole calendar from Loading to Loaded in one line.
     */
    public fun deliverAll(events: List<E> = emptyList()) {
        requests.forEach { range -> deliver(range = range, events = events) }
    }

    /**
     * Fails [range] with [failure]: the range's flow throws, and the loader under test reports the
     * range as Failed with this failure on `errorAt`. A retry collects the range again — deliver
     * (or fail) again to script what the second attempt finds.
     */
    public fun fail(range: KalendarDateRange, failure: Throwable) {
        gate(range).value = Result.failure(failure)
    }

    /** [fail], for every range requested so far — the whole source going down at once. */
    public fun failAll(failure: Throwable) {
        requests.forEach { range -> fail(range = range, failure = failure) }
    }

    private fun gate(range: KalendarDateRange): MutableStateFlow<Result<List<E>>?> =
        gates.getOrPut(range) { MutableStateFlow(null) }

    override fun toString(): String =
        "KalendarTestEventSource(requested=${requests.size}, cancelled=${cancellations.size})"
}
