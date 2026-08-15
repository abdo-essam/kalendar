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

import com.himanshoe.kalendar.foundation.event.BasicKalendarEvent
import com.himanshoe.kalendar.foundation.paging.KalendarPageUnit
import com.himanshoe.kalendar.foundation.paging.KalendarPager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The loading rules a calendar backed by a database or a network has to obey.
 *
 * All of them are about *when* the source is asked and when it is not — a load that works is the
 * easy half. The window is driven by a plain flow rather than by a pager here, which is what
 * [KalendarEventLoader.keepLoaded] exists separately for: a swipe, a fling and a jump all reduce to
 * "these pages are wanted now", and that is the thing worth pinning.
 *
 * The driver runs in `backgroundScope` and each step is settled with `runCurrent`, not
 * `advanceUntilIdle`: the latter stops as soon as only background work is left, so a loader launched
 * there would never run and every one of these tests would pass by doing nothing.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class KalendarEventLoaderTest {

    private val march = LocalDate(2026, 3, 15)

    /**
     * A source that hands out one flow per range and only emits when a test says so, so that
     * "requested", "still in flight", "arrived" and "cancelled" are four distinguishable states.
     */
    private class TestEventSource : KalendarEventSource<BasicKalendarEvent> {

        val requested = mutableListOf<KalendarDateRange>()
        val cancelled = mutableListOf<KalendarDateRange>()

        private val gates = mutableMapOf<KalendarDateRange, MutableSharedFlow<Result<List<BasicKalendarEvent>>>>()

        private fun gate(range: KalendarDateRange) = gates.getOrPut(range) { MutableSharedFlow(replay = 1) }

        override fun eventsIn(range: KalendarDateRange): Flow<List<BasicKalendarEvent>> {
            val gate = gate(range)
            return flow {
                requested += range
                emitAll(gate.map { result -> result.getOrThrow() })
            }.onCompletion { cause ->
                if (cause != null) {
                    cancelled += range
                }
            }
        }

        suspend fun deliver(range: KalendarDateRange, events: List<BasicKalendarEvent>) {
            gate(range).emit(Result.success(events))
        }

        suspend fun fail(range: KalendarDateRange, failure: Throwable) {
            gate(range).emit(Result.failure(failure))
        }
    }

    private fun monthPager(): KalendarPager =
        KalendarPager.endless(unit = KalendarPageUnit.Month, initialDate = march)

    private fun event(date: LocalDate, name: String, id: String? = null) =
        BasicKalendarEvent(date = date, eventName = name, id = id)

    @Test
    fun asksForTheVisiblePageAndItsNeighboursExactlyOnce() = runTest {
        val pager = monthPager()
        val source = TestEventSource()
        val loader = KalendarEventLoader(pager = pager, source = source)
        val visible = pager.pageOf(march)
        val windows = MutableStateFlow(setOf(visible - 1, visible, visible + 1))

        backgroundScope.launch { loader.keepLoaded(windows) }
        runCurrent()

        assertEquals(3, source.requested.size, "one request per page in the window")
        assertEquals(source.requested.toSet().size, source.requested.size, "no page asked for twice")
        assertTrue(loader.rangeOfPage(visible) in source.requested)
    }

    /**
     * The range a month page reports is the *grid* it draws, not the month it is named after: March
     * 2026 starts on a Sunday, so its grid opens on 23 February and closes on 5 April. A source asked
     * for the month alone would leave those two rows blank until a neighbouring page happened to load.
     */
    @Test
    fun aMonthPageAsksForTheWholeGridItDraws() = runTest {
        val pager = monthPager()
        val loader = KalendarEventLoader(pager = pager, source = TestEventSource())

        val range = loader.rangeOfPage(pager.pageOf(march))

        assertEquals(LocalDate(2026, 2, 23), range.start)
        assertEquals(LocalDate(2026, 4, 5), range.endInclusive)
    }

    @Test
    fun slidingTheWindowAsksOnlyForThePageThatEnteredIt() = runTest {
        val pager = monthPager()
        val source = TestEventSource()
        val loader = KalendarEventLoader(pager = pager, source = source)
        val visible = pager.pageOf(march)
        val windows = MutableStateFlow(setOf(visible - 1, visible, visible + 1))

        backgroundScope.launch { loader.keepLoaded(windows) }
        runCurrent()
        windows.value = setOf(visible, visible + 1, visible + 2)
        runCurrent()

        assertEquals(4, source.requested.size, "only the page that entered the window is asked for")
        assertEquals(loader.rangeOfPage(visible + 2), source.requested.last())
    }

    @Test
    fun cancelsTheRangeTheUserHasSwipedPast() = runTest {
        val pager = monthPager()
        val source = TestEventSource()
        val loader = KalendarEventLoader(pager = pager, source = source)
        val visible = pager.pageOf(march)
        val windows = MutableStateFlow(setOf(visible - 1, visible, visible + 1))

        backgroundScope.launch { loader.keepLoaded(windows) }
        runCurrent()
        windows.value = setOf(visible, visible + 1, visible + 2)
        runCurrent()

        assertEquals(listOf(loader.rangeOfPage(visible - 1)), source.cancelled)
    }

    /**
     * The failure mode a naive loader has: a slow range answers after the user has moved on, and its
     * events appear on a calendar that is no longer showing them — or, worse, its "loaded" overwrites
     * the state of the page that is.
     */
    @Test
    fun dropsAnAnswerThatArrivesAfterTheUserHasSwipedAway() = runTest {
        val pager = monthPager()
        val source = TestEventSource()
        val loader = KalendarEventLoader(pager = pager, source = source)
        val visible = pager.pageOf(march)
        val left = visible - 1
        val windows = MutableStateFlow(setOf(left, visible, visible + 1))

        backgroundScope.launch { loader.keepLoaded(windows) }
        runCurrent()
        windows.value = setOf(visible, visible + 1, visible + 2)
        runCurrent()
        source.deliver(loader.rangeOfPage(left), listOf(event(date = LocalDate(2026, 2, 10), name = "late")))
        runCurrent()

        assertTrue(loader.events.isEmpty(), "a range nobody is looking at cannot deliver events")
    }

    @Test
    fun anEmptyResultIsLoadedRatherThanStillLoading() = runTest {
        val pager = monthPager()
        val source = TestEventSource()
        val loader = KalendarEventLoader(pager = pager, source = source)
        val windows = MutableStateFlow(setOf(pager.pageOf(march)))

        backgroundScope.launch { loader.keepLoaded(windows) }
        runCurrent()
        assertEquals(KalendarLoadStatus.Loading, loader.statusAt(march), "nothing has arrived yet")

        source.deliver(loader.rangeOfPage(pager.pageOf(march)), emptyList())
        runCurrent()

        assertEquals(KalendarLoadStatus.Loaded, loader.statusAt(march), "an empty month is an answer")
        assertTrue(loader.events.isEmpty())
    }

    /**
     * A source that is down and then is not, which is the shape of every offline-then-online app:
     * the first collection throws, the second answers.
     */
    @Test
    fun aFailedRangeCarriesItsFailureAndRetryRecoversFromIt() = runTest {
        val pager = monthPager()
        val failure = IllegalStateException("offline")
        var attempts = 0
        val source = KalendarEventSource<BasicKalendarEvent> {
            flow {
                attempts += 1
                if (attempts == 1) {
                    throw failure
                }
                emit(listOf(event(date = march, name = "standup")))
            }
        }
        val loader = KalendarEventLoader(pager = pager, source = source)
        val windows = MutableStateFlow(setOf(pager.pageOf(march)))

        backgroundScope.launch { loader.keepLoaded(windows) }
        runCurrent()

        assertEquals(KalendarLoadStatus.Failed, loader.statusAt(march))
        assertEquals(failure, loader.errorAt(march))
        assertEquals(1, attempts)

        loader.retry()
        runCurrent()

        assertEquals(2, attempts, "retry asks the failed range again")
        assertEquals(KalendarLoadStatus.Loaded, loader.statusAt(march))
        assertNull(loader.errorAt(march), "the failure is gone once the range has answered")
        assertEquals(1, loader.events.size)
    }

    @Test
    fun retryLeavesTheRangesThatSucceededAlone() = runTest {
        val pager = monthPager()
        val source = TestEventSource()
        val loader = KalendarEventLoader(pager = pager, source = source)
        val page = pager.pageOf(march)
        val windows = MutableStateFlow(setOf(page, page + 1))

        backgroundScope.launch { loader.keepLoaded(windows) }
        runCurrent()
        source.deliver(loader.rangeOfPage(page), listOf(event(date = march, name = "standup")))
        source.fail(loader.rangeOfPage(page + 1), IllegalStateException("offline"))
        runCurrent()

        loader.retry()
        runCurrent()

        assertEquals(
            listOf(loader.rangeOfPage(page), loader.rangeOfPage(page + 1), loader.rangeOfPage(page + 1)),
            source.requested,
            "only the failed range is asked again",
        )
        assertEquals(KalendarLoadStatus.Loaded, loader.statusAt(march), "the range that worked is untouched")
        assertEquals(1, loader.events.size)
    }

    /**
     * A multi-day event straddling two ranges is legitimately returned by both queries, and the views
     * would draw it twice. [com.himanshoe.kalendar.foundation.event.KalendarEvent.id] is what says
     * the two are one event.
     */
    @Test
    fun deduplicatesAnEventThatTwoAdjacentRangesBothReturn() = runTest {
        val pager = monthPager()
        val source = TestEventSource()
        val loader = KalendarEventLoader(pager = pager, source = source)
        val page = pager.pageOf(march)
        val windows = MutableStateFlow(setOf(page, page + 1))
        val straddling = event(date = LocalDate(2026, 3, 30), name = "conference", id = "conf-1")

        backgroundScope.launch { loader.keepLoaded(windows) }
        runCurrent()
        source.deliver(loader.rangeOfPage(page), listOf(straddling))
        source.deliver(loader.rangeOfPage(page + 1), listOf(straddling))
        runCurrent()

        assertEquals(1, loader.events.size, "one event, returned twice, is still one event")
    }

    @Test
    fun holdsNothingForAPageThatLeftTheWindow() = runTest {
        val pager = monthPager()
        val source = TestEventSource()
        val loader = KalendarEventLoader(pager = pager, source = source)
        val page = pager.pageOf(march)
        val windows = MutableStateFlow(setOf(page))

        backgroundScope.launch { loader.keepLoaded(windows) }
        runCurrent()
        source.deliver(loader.rangeOfPage(page), listOf(event(date = march, name = "standup")))
        runCurrent()
        assertEquals(1, loader.events.size)

        windows.value = setOf(page + 4)
        runCurrent()

        assertTrue(loader.events.isEmpty(), "memory is bounded by the window, not by how far it moved")
        assertEquals(KalendarLoadStatus.Loading, loader.statusAt(march))
        assertNull(loader.errorAt(march))
    }
}
