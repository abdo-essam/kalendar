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

package com.himanshoe.kalendar

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import com.himanshoe.kalendar.foundation.event.BasicKalendarEvent
import com.himanshoe.kalendar.state.KalendarDateRange
import com.himanshoe.kalendar.state.KalendarEventSource
import com.himanshoe.kalendar.state.KalendarLoadStatus
import com.himanshoe.kalendar.state.KalendarViewState
import com.himanshoe.kalendar.state.rememberKalendarEventLoader
import com.himanshoe.kalendar.state.rememberKalendarMonthState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * What a calendar looks like while its events are still coming, which is the half a screenshot of a
 * finished calendar never shows.
 *
 * The interesting cases are all the ones where "nothing is drawn" is ambiguous: a month with no
 * events and a month whose events have not arrived are the same picture until the placeholder
 * separates them, and a month whose load failed is a third thing again.
 */
@Suppress("MagicNumber")
class KalendarLoadingUiTest {

    @get:Rule
    val rule = createComposeRule()

    private val august = LocalDate(2026, 8, 15)

    /**
     * A source that answers only when a test tells it to, so "asked and waiting" is a state the test
     * can hold the calendar in and look at.
     */
    private class ControlledSource : KalendarEventSource<BasicKalendarEvent> {

        private val gates = mutableMapOf<KalendarDateRange, MutableStateFlow<List<BasicKalendarEvent>?>>()

        override fun eventsIn(range: KalendarDateRange): Flow<List<BasicKalendarEvent>> =
            gates.getOrPut(range) { MutableStateFlow(null) }.filterNotNull()

        /** Answers every range asked for so far, which is the window and nothing else. */
        fun deliver(events: List<BasicKalendarEvent> = emptyList()) {
            gates.values.forEach { gate -> gate.value = events }
        }
    }

    private fun placeholders() = rule.onAllNodesWithTag(KALENDAR_LOADING_TAG).fetchSemanticsNodes().size

    @Test
    fun aMonthWaitingOnItsEventsIsNotDrawnLikeAMonthThatHasNone() {
        val source = ControlledSource()
        rule.setContent {
            val state = rememberKalendarMonthState(initialDate = august)
            KalendarMonth(
                selectedDate = august,
                state = state,
                eventLoader = rememberKalendarEventLoader(state = state, source = source),
            )
        }

        assertEquals(1, placeholders(), "the visible month is waiting on its events")

        rule.runOnIdle { source.deliver() }
        rule.waitForIdle()

        assertEquals(0, placeholders(), "an empty answer is an answer, and draws as an empty month")
    }

    /**
     * The guarantee the slot's contract rests on. The placeholder is a match-parent-size overlay, so
     * it is measured after the grid has decided the page's size and cannot contribute to it — the
     * cell that holds the 15th is in exactly the same place before and after its events land.
     */
    @Test
    fun theGridDoesNotMoveWhenTheEventsLand() {
        val source = ControlledSource()
        rule.setContent {
            val state = rememberKalendarMonthState(initialDate = august)
            KalendarMonth(
                selectedDate = august,
                state = state,
                eventLoader = rememberKalendarEventLoader(state = state, source = source),
            )
        }

        assertEquals(1, placeholders(), "there is a placeholder to move things, if it is going to")
        val whileLoading = rule.onAllNodesWithText("15").onFirst().getUnclippedBoundsInRoot()

        rule.runOnIdle {
            source.deliver(listOf(BasicKalendarEvent(date = august, eventName = "standup", id = "1")))
        }
        rule.waitForIdle()

        val whenLoaded = rule.onAllNodesWithText("15").onFirst().getUnclippedBoundsInRoot()
        assertEquals(whileLoading.left, whenLoaded.left)
        assertEquals(whileLoading.top, whenLoaded.top)
        assertEquals(whileLoading.right, whenLoaded.right)
        assertEquals(whileLoading.bottom, whenLoaded.bottom)
    }

    @Test
    fun aFailedRangeReachesTheSlotWithItsFailureAndRetryAsksAgain() {
        var attempts = 0
        val failure = IllegalStateException("offline")
        val source = KalendarEventSource<BasicKalendarEvent> {
            flow {
                attempts += 1
                throw failure
            }
        }
        var status: KalendarLoadStatus? = null
        var reported: Throwable? = null
        var retry: (() -> Unit)? = null

        rule.setContent {
            val state = rememberKalendarMonthState(initialDate = august)
            KalendarMonth(
                selectedDate = august,
                state = state,
                eventLoader = rememberKalendarEventLoader(state = state, source = source),
                loadingContent = { scope ->
                    status = scope.status
                    reported = scope.error
                    retry = scope.retry
                    BasicText(text = "could not load")
                },
            )
        }

        rule.onNodeWithText("could not load").assertExists()
        assertEquals(KalendarLoadStatus.Failed, status)
        assertEquals(failure, reported)
        val firstRound = attempts
        assertTrue(firstRound > 0)

        rule.runOnIdle { assertNotNull(retry).invoke() }
        rule.waitForIdle()

        assertTrue(attempts > firstRound, "retry asked the source again")
    }

    /**
     * A swipe onto a page that was prefetched shows the page, not a placeholder — which is the whole
     * reason the window is wider than what is on screen.
     */
    @Test
    fun swipingOntoAPrefetchedMonthShowsNoPlaceholder() {
        val source = ControlledSource()
        var state: KalendarViewState? = null
        var scope: CoroutineScope? = null
        rule.setContent {
            val monthState = rememberKalendarMonthState(initialDate = august)
            state = monthState
            scope = rememberCoroutineScope()
            KalendarMonth(
                selectedDate = august,
                state = monthState,
                eventLoader = rememberKalendarEventLoader(state = monthState, source = source),
            )
        }

        assertEquals(1, placeholders(), "August is waiting, so a placeholder is a thing this can draw")
        rule.runOnIdle { source.deliver() }
        rule.waitForIdle()
        assertEquals(0, placeholders())

        rule.runOnIdle {
            assertNotNull(scope).launch {
                assertNotNull(state).animateScrollTo(august.plus(1, DateTimeUnit.MONTH))
            }
        }
        rule.waitForIdle()

        assertEquals(0, placeholders(), "September was already loaded before it was on screen")
    }

    @Test
    fun anAgendaWithNothingLoadedYetSaysSoRatherThanSayingEmpty() {
        val source = ControlledSource()
        rule.setContent {
            val state = rememberKalendarMonthState(initialDate = august)
            val agendaLoader = rememberKalendarEventLoader(state = state, source = source)
            KalendarAgenda(
                events = agendaLoader.events,
                eventLoader = agendaLoader,
                emptyState = { BasicText(text = "no events") },
                loadingContent = { BasicText(text = "still loading") },
            )
        }

        rule.onNodeWithText("still loading").assertExists()
        rule.onAllNodesWithText("no events").assertCountEquals(0)

        rule.runOnIdle { source.deliver() }
        rule.waitForIdle()

        rule.onNodeWithText("no events").assertExists()
        rule.onAllNodesWithText("still loading").assertCountEquals(0)
    }
}
