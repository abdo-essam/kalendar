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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.himanshoe.kalendar.foundation.event.BasicKalendarEvent
import com.himanshoe.kalendar.foundation.event.KalendarEvents
import com.himanshoe.kalendar.state.KalendarViewState
import com.himanshoe.kalendar.state.rememberKalendarScheduleState
import com.himanshoe.kalendar.state.rememberKalendarWeekState
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

/**
 * What each page of the two Schedule views is allowed to see.
 *
 * The date index behind both views — `byDateExpandingSpans()` — is built once for the whole event
 * list and read per page. That saving is only sound if a page still sees exactly the events of its
 * own day, span expansion included, so these tests pin it per page rather than trusting that a
 * shared map and a per-page map must agree.
 *
 * The multi-day span is the interesting case: it is the only event that appears under more than one
 * key, so it is the one an index built in the wrong place would drop or smear.
 */
@Suppress("MagicNumber")
class KalendarScheduleEventIndexUiTest {

    @get:Rule
    val rule = createComposeRule()

    private val monday = LocalDate(2026, 3, 9)
    private val tuesday = LocalDate(2026, 3, 10)
    private val wednesday = LocalDate(2026, 3, 11)
    private val thursday = LocalDate(2026, 3, 12)

    /** The date the pager is being sent to, so [goTo] can turn one page at a time. */
    private var target by mutableStateOf<LocalDate?>(null)

    @Test
    fun schedule_eachPageShowsOnlyItsOwnDayIncludingSpanExpansion() {
        val events = dayFixture()
        val state = paged({ rememberKalendarScheduleState(initialDate = monday) }) {
            KalendarSchedule(state = it, events = events)
        }

        assertShown("MondayBlock")
        assertAbsent("TuesdayBlock")
        assertShown("Span")

        goTo(state, tuesday)
        assertShown("TuesdayBlock")
        assertAbsent("MondayBlock")
        assertShown("Span")

        goTo(state, wednesday)
        assertAbsent("MondayBlock")
        assertAbsent("TuesdayBlock")
        assertShown("Span")

        goTo(state, thursday)
        assertAbsent("Span")
    }

    /**
     * The week view's all-day row prefixes each chip with the day of month it belongs to, so a span
     * crossing the page boundary is also an assertion about *which* days of the week each page
     * expanded it onto.
     */
    @Test
    fun scheduleWeek_eachPageShowsOnlyItsOwnWeekIncludingSpanExpansion() {
        val saturday = LocalDate(2026, 3, 14)
        val sunday = LocalDate(2026, 3, 15)
        val nextMonday = LocalDate(2026, 3, 16)
        val nextTuesday = LocalDate(2026, 3, 17)
        val events = listOf(
            timed(name = "ThisWeek", date = tuesday, startHour = 9),
            timed(name = "NextWeek", date = nextTuesday, startHour = 9),
            span(name = "Span", from = saturday, to = nextTuesday),
        )
        val state = paged({ rememberKalendarWeekState(initialDate = monday) }) {
            KalendarScheduleWeek(state = it, events = events)
        }

        assertShown("ThisWeek")
        assertAbsent("NextWeek")
        assertShown(chip(saturday))
        assertShown(chip(sunday))
        assertAbsent(chip(nextMonday))

        goTo(state, nextMonday)
        assertShown("NextWeek")
        assertAbsent("ThisWeek")
        assertShown(chip(nextMonday))
        assertShown(chip(nextTuesday))
        assertAbsent(chip(saturday))
    }

    /**
     * The index is keyed on the event list, so a new list must rebuild it.
     *
     * Without this the hoist would be indistinguishable from caching the first list forever: every
     * "each page sees its own day" assertion above would still pass while the calendar quietly
     * ignored every update its caller made.
     */
    @Test
    fun schedule_replacingTheEventListRebuildsTheIndex() {
        var events by mutableStateOf(dayFixture())
        rule.setContent {
            KalendarSchedule(state = rememberKalendarScheduleState(initialDate = monday), events = events)
        }
        rule.waitForIdle()
        assertShown("MondayBlock")
        assertAbsent("Rescheduled")

        events = listOf(timed(name = "Rescheduled", date = monday, startHour = 14))
        rule.waitForIdle()

        assertShown("Rescheduled")
        assertAbsent("MondayBlock")
        assertAbsent("Span")
    }

    /** [schedule_replacingTheEventListRebuildsTheIndex] for the week view. */
    @Test
    fun scheduleWeek_replacingTheEventListRebuildsTheIndex() {
        var events by mutableStateOf(dayFixture())
        rule.setContent {
            KalendarScheduleWeek(state = rememberKalendarWeekState(initialDate = monday), events = events)
        }
        rule.waitForIdle()
        assertShown("TuesdayBlock")

        events = listOf(timed(name = "Rescheduled", date = tuesday, startHour = 14))
        rule.waitForIdle()

        assertShown("Rescheduled")
        assertAbsent("TuesdayBlock")
    }

    /**
     * Composes [view] and hands back its navigation state, with a scroll target wired up so [goTo]
     * can turn pages one at a time.
     *
     * Paging is driven through the state rather than by a swipe on purpose: a fling coasts an
     * unpredictable number of pages, and which page an assertion lands on is the whole point here.
     */
    private fun paged(
        rememberState: @Composable () -> KalendarViewState,
        view: @Composable (KalendarViewState) -> Unit,
    ): KalendarViewState {
        var captured: KalendarViewState? = null
        rule.setContent {
            val viewState = rememberState()
            captured = viewState
            LaunchedEffect(target) { target?.let { viewState.animateScrollTo(it) } }
            view(viewState)
        }
        rule.waitForIdle()
        return checkNotNull(captured)
    }

    private fun goTo(state: KalendarViewState, date: LocalDate) {
        target = date
        rule.waitForIdle()
        assertEquals(date, state.visibleDate, "the pager did not land on $date")
    }

    /** Monday and Tuesday each have their own block; the span covers Monday through Wednesday. */
    private fun dayFixture(): KalendarEvents = listOf(
        timed(name = "MondayBlock", date = monday, startHour = 9),
        timed(name = "TuesdayBlock", date = tuesday, startHour = 9),
        span(name = "Span", from = monday, to = wednesday),
    )

    private fun timed(name: String, date: LocalDate, startHour: Int): BasicKalendarEvent =
        BasicKalendarEvent(
            date = date,
            eventName = name,
            startTime = LocalDateTime(date.year, date.month, date.day, startHour, 0),
            endTime = LocalDateTime(date.year, date.month, date.day, startHour + 1, 0),
        )

    private fun span(name: String, from: LocalDate, to: LocalDate): BasicKalendarEvent =
        BasicKalendarEvent(date = from, eventName = name, endDate = to)

    /** The week view's all-day chip label for the shared span on [day]. */
    private fun chip(day: LocalDate): String = "${day.day} · Span"

    private fun assertShown(text: String) {
        rule.onNodeWithText(text).assertIsDisplayed()
    }

    private fun assertAbsent(text: String) {
        rule.onNodeWithText(text).assertDoesNotExist()
    }
}
