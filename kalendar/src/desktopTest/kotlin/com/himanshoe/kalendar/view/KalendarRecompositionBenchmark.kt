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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.dp
import com.himanshoe.kalendar.foundation.event.BasicKalendarEvent
import com.himanshoe.kalendar.foundation.event.KalendarEvents
import kotlinx.datetime.LocalDate
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * A composition tally.
 *
 * `@Stable`, and held in a top-level `val` on purpose. A probe that captured a plain mutable local
 * would itself be an unstable parameter, which stops the view holding it from ever skipping — the
 * benchmark would then measure its own interference instead of the view's behaviour.
 */
@Stable
private class Tally {
    var count: Int = 0
}

private val CELLS = Tally()
private val HEADERS = Tally()

/**
 * Counts runs of the lambda that *calls* the view under test.
 *
 * This is the benchmark's control, and it is not optional. Compose absorbs an invalidation at the
 * first composable above the calendar whose parameters have not changed, so a naive harness that
 * flips some state at the top of the tree never re-offers the calendar its parameters at all —
 * and then every "recomposed nothing" count below is a vacuous zero that would keep passing after
 * a genuine regression.
 */
private val CALL_SITES = Tally()

private val PROBE_SIZE = 8.dp

/**
 * The day-cell slot handed to every view under test.
 *
 * It emits a real (if tiny) node deliberately: a composable that emits nothing has its group
 * optimised away by the compiler and can never be observed skipping, which would again turn the
 * assertions into tautologies.
 */
private val DAY_PROBE: @Composable (KalendarDayScope) -> Unit = {
    CELLS.count++
    Box(modifier = Modifier.size(PROBE_SIZE))
}

/**
 * A probe on the navigation-header slot, invoked by the view's own body rather than by its grid.
 *
 * Note it is a lower bound only: this lambda takes a [KalendarHeaderScope] and so may skip on its
 * own even when the view around it recomposed. [CELLS] plus [CALL_SITES] are the load-bearing
 * numbers; this one is corroboration.
 */
private val HEADER_PROBE: @Composable (KalendarHeaderScope) -> Unit = {
    HEADERS.count++
    Box(modifier = Modifier.size(PROBE_SIZE))
}

private const val FIXTURE_YEAR = 2026
private const val FIXTURE_MONTH = 3
private const val FIXTURE_DAY = 10

/** Twelve months of 28..42 cells. Loose on purpose: the point is the order of magnitude. */
private const val YEAR_EAGER_CELL_FLOOR = 400

private val FIXTURE_DATE = LocalDate(FIXTURE_YEAR, FIXTURE_MONTH, FIXTURE_DAY)

/**
 * Hoisted so the harness cannot perturb what it measures.
 *
 * Material 3's [androidx.compose.material3.ColorScheme] has no structural `equals`, so a
 * `MaterialTheme(colorScheme = lightColorScheme())` written inline re-provides a *different*
 * instance on every recomposition and force-recomposes its entire subtree. Every "recomposes
 * nothing" number below is meaningless unless this is hoisted.
 */
private val STABLE_SCHEME = lightColorScheme()
private val STILL_ANIMATIONS = KalendarAnimations(enabled = false)

/** A clock frozen on the fixture date, so no now-indicator timer can fire mid-measurement. */
private val FROZEN_TIME_SOURCE = KalendarTimeSource(
    clock = object : Clock {
        override fun now(): Instant = Instant.parse("2026-03-10T09:00:00Z")
    },
)

/**
 * Recomposition budgets for the calendar views, in composition counts rather than milliseconds —
 * a composition count is reproducible across machines, a timing is not.
 *
 * These budgets are the measured baseline recorded in `docs/performance.md`. A failure here means
 * a stability regression — a view that lost `restartable skippable`, or a token class whose
 * `equals` went identity-based — not a slow machine.
 */
@OptIn(ExperimentalTestApi::class)
class KalendarRecompositionBenchmark {

    @get:Rule
    val rule = createComposeRule()

    /**
     * Moving keyboard focus must not recompose a single cell.
     *
     * The focused date is a snapshot state shared by the whole grid, so it is exactly the sort of
     * value that recomposes 42 cells per arrow press if it is read in a composable body. It is read
     * only inside layout and semantics lambdas; this is what holds that line.
     */
    @Test
    fun arrowKeyMovesFocusWithoutRecomposingAnyCell() {
        reset()
        rule.setContent {
            Harness(tick = 0) {
                KalendarMonth(selectedDate = FIXTURE_DATE, header = HEADER_PROBE, dayContent = DAY_PROBE)
            }
        }
        rule.waitForIdle()
        rule.onRoot().performKeyInput { pressKey(Key.Tab) }
        rule.waitForIdle()

        val before = CELLS.count
        rule.onRoot().performKeyInput { pressKey(Key.DirectionRight) }
        rule.waitForIdle()

        println("BENCH after arrow key: cellsBefore=$before cellsAfter=${CELLS.count}")
        assertEquals(
            0,
            CELLS.count - before,
            "an arrow press recomposed ${CELLS.count - before} day cells; the focused date is being " +
                "read in a composable body instead of in a layout or semantics lambda",
        )
    }

    /**
     * A month grid is 7 columns by up to 6 rows, none of it lazy in the vertical sense because the
     * whole grid is on screen at once. This is the floor, not a regression.
     */
    @Test
    fun monthComposesOneCellPerGridSlot() {
        val cells = countFirstComposition { KalendarMonth(selectedDate = FIXTURE_DATE, dayContent = DAY_PROBE) }
        assertTrue(cells in 28..42, "KalendarMonth composed $cells day cells; a month grid is 28..42 slots")
    }

    /**
     * Twelve month grids inside a non-lazy `verticalScroll` `Column`: every cell of every month is
     * composed on the first frame, on screen or not. This pins that cost, so a future move to a
     * lazy container shows up as a deliberate, visible drop rather than an unnoticed one.
     */
    @Test
    fun yearComposesEveryMonthEagerly() {
        val cells = countFirstComposition { KalendarYear(selectedDate = FIXTURE_DATE, dayContent = DAY_PROBE) }
        assertTrue(
            cells >= YEAR_EAGER_CELL_FLOOR,
            "KalendarYear composed only $cells day cells; expected all twelve months eagerly " +
                "(>= $YEAR_EAGER_CELL_FLOOR)",
        )
    }

    /**
     * The skippability guard.
     *
     * The lambda that calls the calendar recomposes, offering every view its parameters again. All
     * of them compare equal, so neither the view nor its grid may run a second time. This is what
     * breaks — silently, and everywhere at once — if a token class's `equals` is ever made
     * identity-based or a view loses `restartable skippable`.
     */
    @Test
    fun callSiteRecompositionRecomposesNothingInMonth() {
        val cells = countAfterCallSiteRecomposition { tick ->
            CALL_SITES.count = tick + 1
            KalendarMonth(selectedDate = FIXTURE_DATE, header = HEADER_PROBE, dayContent = DAY_PROBE)
        }
        assertEquals(0, cells, "KalendarMonth recomposed $cells day cells")
    }

    /** [callSiteRecompositionRecomposesNothingInMonth] for the Week view. */
    @Test
    fun callSiteRecompositionRecomposesNothingInWeek() {
        val cells = countAfterCallSiteRecomposition { tick ->
            CALL_SITES.count = tick + 1
            KalendarWeek(selectedDate = FIXTURE_DATE, header = HEADER_PROBE, dayContent = DAY_PROBE)
        }
        assertEquals(0, cells, "KalendarWeek recomposed $cells day cells")
    }

    /**
     * The same recomposition, but the caller rebuilds `selectedDate` and `events` inline every
     * time instead of hoisting them — which is what most of the KDoc samples show.
     *
     * `kotlinx.datetime.LocalDate` and `List` are both reported `unstable` by the stability
     * report. This test is the evidence that "unstable" does not mean "does not skip": under
     * strong skipping Compose still compares those parameters with `equals`, so a value-equal but
     * freshly allocated instance skips anyway. If that ever stops being true, this fails and
     * `docs/performance.md` explains the fix (a stability configuration file).
     */
    @Test
    fun freshlyAllocatedEqualParametersRecomposeNothing() {
        val cells = countAfterCallSiteRecomposition { tick ->
            CALL_SITES.count = tick + 1
            KalendarMonth(
                selectedDate = freshFixtureDate(tick),
                events = freshEvents(tick),
                header = HEADER_PROBE,
                dayContent = DAY_PROBE,
            )
        }
        assertEquals(0, cells, "Freshly allocated equal parameters recomposed $cells day cells")
    }

    /**
     * A [LocalDate] equal to [FIXTURE_DATE] but allocated afresh on every call.
     *
     * The offset is always zero, but it is *derived from* [tick], which is what stops the Compose
     * compiler hoisting the whole expression into a `remember` as a compile-time constant. Written
     * as `LocalDate(2026, 3, 10)` the call is hoisted, the same instance comes back every
     * recomposition, and the test silently stops testing anything.
     */
    private fun freshFixtureDate(tick: Int): LocalDate =
        LocalDate.fromEpochDays(FIXTURE_DATE.toEpochDays() + tick.coerceAtMost(0))

    /** A value-equal but freshly allocated event list, for the same reason as [freshFixtureDate]. */
    private fun freshEvents(tick: Int): KalendarEvents = listOf(
        BasicKalendarEvent(
            date = freshFixtureDate(tick),
            eventName = "Standup",
            id = "standup",
        ),
    )

    /** Composes [view] once and returns how many day cells that first composition walked. */
    private fun countFirstComposition(view: @Composable () -> Unit): Int {
        reset()
        rule.setContent { Harness(tick = 0) { view() } }
        rule.waitForIdle()
        println("BENCH first composition: cells=${CELLS.count}")
        return CELLS.count
    }

    /**
     * Composes [view], forces the lambda that *calls* it to recompose, and returns how many day
     * cells the resulting recomposition walked.
     */
    private fun countAfterCallSiteRecomposition(view: @Composable (Int) -> Unit): Int {
        reset()
        val tick = mutableStateOf(0)
        rule.setContent { Harness(tick = tick.value, view = view) }
        rule.waitForIdle()
        val cellsFirst = CELLS.count
        rule.runOnIdle { tick.value += 1 }
        rule.waitForIdle()
        val delta = CELLS.count - cellsFirst
        println(
            "BENCH after call-site recomposition: cellsFirst=$cellsFirst cellsSecond=$delta " +
                "headerRuns=${HEADERS.count} callSiteRuns=${CALL_SITES.count}",
        )
        assertEquals(
            2,
            CALL_SITES.count,
            "Harness control failed: the calling lambda ran ${CALL_SITES.count} times, not 2. The " +
                "invalidation never reached the view, so this measurement proves nothing.",
        )
        return delta
    }

    private fun reset() {
        CELLS.count = 0
        HEADERS.count = 0
        CALL_SITES.count = 0
    }

    /**
     * [tick] is handed to [view] as a parameter rather than read here.
     *
     * Read anywhere above [view], the invalidation is absorbed by the first skippable composable
     * in between — `KalendarTheme`, or [view] itself, which captures nothing and would skip — and
     * the calendar is never re-offered its parameters. Passing it in is what guarantees the lambda
     * that allocates those parameters actually runs a second time.
     */
    @Composable
    private fun Harness(tick: Int, view: @Composable (Int) -> Unit) {
        ProvideKalendarTimeSource(FROZEN_TIME_SOURCE) {
            MaterialTheme(colorScheme = STABLE_SCHEME) {
                KalendarTheme(animations = STILL_ANIMATIONS) {
                    view(tick)
                }
            }
        }
    }
}
