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

import com.himanshoe.kalendar.foundation.event.BasicKalendarEvent
import com.himanshoe.kalendar.state.KalendarDateRange
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * The source's whole contract, exercised the way the event loader exercises it: collect a range's
 * flow, and observe that nothing happens until the test says so.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class KalendarTestEventSourceTest {

    private val march = KalendarDateRange(start = LocalDate(2026, 3, 1), endInclusive = LocalDate(2026, 3, 31))
    private val april = KalendarDateRange(start = LocalDate(2026, 4, 1), endInclusive = LocalDate(2026, 4, 30))

    @Test
    fun staysSilentUntilTheTestDelivers() = runTest {
        val source = KalendarTestEventSource<BasicKalendarEvent>()
        val emissions = mutableListOf<List<BasicKalendarEvent>>()

        backgroundScope.launch { source.eventsIn(march).collect { events -> emissions += events } }
        runCurrent()
        assertTrue(emissions.isEmpty(), "asked is not answered")

        val standup = kalendarEvent(date = LocalDate(2026, 3, 15), id = "1")
        source.deliver(range = march, events = listOf(standup))
        runCurrent()

        assertEquals(listOf(listOf(standup)), emissions)
    }

    @Test
    fun recordsEveryCollectionInRequestOrder() = runTest {
        val source = KalendarTestEventSource<BasicKalendarEvent>()

        backgroundScope.launch { source.eventsIn(march).collect {} }
        backgroundScope.launch { source.eventsIn(april).collect {} }
        backgroundScope.launch { source.eventsIn(march).collect {} }
        runCurrent()

        assertEquals(listOf(march, april, march), source.requestedRanges, "a re-collection counts again")
    }

    @Test
    fun deliverAllAnswersEveryRequestedRangeAtOnce() = runTest {
        val source = KalendarTestEventSource<BasicKalendarEvent>()
        val answered = mutableListOf<KalendarDateRange>()

        backgroundScope.launch { source.eventsIn(march).collect { answered += march } }
        backgroundScope.launch { source.eventsIn(april).collect { answered += april } }
        runCurrent()
        source.deliverAll()
        runCurrent()

        assertEquals(setOf(march, april), answered.toSet())
    }

    @Test
    fun failMakesThatRangesFlowThrowTheGivenFailure() = runTest {
        val source = KalendarTestEventSource<BasicKalendarEvent>()
        val failure = IllegalStateException("offline")
        var caught: Throwable? = null

        backgroundScope.launch {
            try {
                source.eventsIn(march).collect {}
            } catch (thrown: IllegalStateException) {
                caught = thrown
            }
        }
        runCurrent()
        source.fail(range = march, failure = failure)
        runCurrent()

        assertSame(failure, caught, "the loader receives the exact failure the test scripted")
        assertTrue(source.cancelledRanges.isEmpty(), "a failure is not a cancellation")
    }

    @Test
    fun anAnswerPrimedBeforeTheRequestArrivesImmediately() = runTest {
        val source = KalendarTestEventSource<BasicKalendarEvent>()
        val standup = kalendarEvent(date = LocalDate(2026, 3, 15), id = "1")
        source.deliver(range = march, events = listOf(standup))
        val emissions = mutableListOf<List<BasicKalendarEvent>>()

        backgroundScope.launch { source.eventsIn(march).collect { events -> emissions += events } }
        runCurrent()

        assertEquals(listOf(listOf(standup)), emissions, "a pre-loaded source answers in the same turn")
    }

    @Test
    fun aSecondDifferentDeliveryReEmitsThroughTheStillCollectedFlow() = runTest {
        val source = KalendarTestEventSource<BasicKalendarEvent>()
        val emissions = mutableListOf<Int>()

        backgroundScope.launch { source.eventsIn(march).collect { events -> emissions += events.size } }
        runCurrent()
        source.deliver(range = march)
        runCurrent()
        source.deliver(range = march, events = listOf(kalendarEvent(date = LocalDate(2026, 3, 15), id = "1")))
        runCurrent()

        assertEquals(listOf(0, 1), emissions, "the source behaves like a query that re-emits on change")
    }

    @Test
    fun recordsACancelledCollectionAsCancelled() = runTest {
        val source = KalendarTestEventSource<BasicKalendarEvent>()

        val collection = backgroundScope.launch { source.eventsIn(march).collect {} }
        runCurrent()
        assertTrue(source.cancelledRanges.isEmpty())

        collection.cancelAndJoin()

        assertEquals(listOf(march), source.cancelledRanges)
    }
}
