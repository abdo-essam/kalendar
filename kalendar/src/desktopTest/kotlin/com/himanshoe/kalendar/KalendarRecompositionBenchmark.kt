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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.dp
import com.himanshoe.kalendar.component.KEYBOARD_FOCUS_RING_TAG
import com.himanshoe.kalendar.component.KalendarDayCellDefaults
import com.himanshoe.kalendar.foundation.datetime.KalendarTimeSource
import com.himanshoe.kalendar.foundation.event.BasicKalendarEvent
import com.himanshoe.kalendar.foundation.event.KalendarEvent
import com.himanshoe.kalendar.foundation.event.KalendarEvents
import com.himanshoe.kalendar.foundation.schedule.KalendarHourWindow
import com.himanshoe.kalendar.foundation.schedule.KalendarResource
import com.himanshoe.kalendar.slot.KalendarDayScope
import com.himanshoe.kalendar.slot.KalendarHeaderScope
import com.himanshoe.kalendar.slot.KalendarScheduleEventScope
import com.himanshoe.kalendar.state.KalendarViewState
import com.himanshoe.kalendar.state.ProvideKalendarTimeSource
import com.himanshoe.kalendar.state.rememberKalendarScheduleState
import com.himanshoe.kalendar.state.rememberKalendarWeekState
import com.himanshoe.kalendar.theme.KalendarAnimations
import com.himanshoe.kalendar.theme.KalendarTheme
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
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
private val BLOCKS = Tally()

/** Runs of [KalendarDayCellDefaults.Cell]'s own body — see [CellBodyTallyingEvents]. */
private val CELL_BODIES = Tally()

/**
 * Walks of the event list handed to a view — one walk is one `byDateExpandingSpans()` build.
 *
 * A count rather than a timing, for the same reason as everything else here. The map build is
 * linear in the caller's whole event list, a number the library does not control, so "how many
 * times per page turn" is the entire finding.
 */
private val EVENT_WALKS = Tally()

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
private val DAY_PROBE: @Composable (KalendarDayScope<*>) -> Unit = {
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

/**
 * The `eventContent` slot handed to the hour-grid views under test.
 *
 * It fills its slot rather than drawing the built-in block, so a touch injected at the node's centre
 * is the centre of the *block* — which is what puts the press in the move region instead of on a
 * resize edge. Contravariance makes the same probe serve
 * [KalendarResourceView]'s [KalendarResourceEventScope] slot.
 */
private val BLOCK_PROBE: @Composable (KalendarScheduleEventScope<*>) -> Unit = { scope ->
    BLOCKS.count++
    Box(modifier = Modifier.fillMaxSize().testTag(scope.event.eventName))
}

/**
 * A [KalendarEvents] that tallies every iteration over itself into [EVENT_WALKS].
 *
 * `byDateExpandingSpans()` is the only thing either Schedule view does with the list it is handed,
 * and it is a `for (event in this)` — so one `iterator()` call is one index build, and no timing is
 * needed to see how often the index is rebuilt.
 *
 * `equals` short-circuits on identity so that Compose comparing this parameter against its own slot
 * value cannot walk the list and inflate the count. Without that the measurement would report the
 * comparisons as if they were rebuilds.
 */
private class WalkTallyingEvents(private val events: KalendarEvents) : KalendarEvents by events {

    override fun iterator(): Iterator<KalendarEvent> {
        EVENT_WALKS.count++
        return events.iterator()
    }

    override fun equals(other: Any?): Boolean =
        this === other || (other is WalkTallyingEvents && events == other.events)

    override fun hashCode(): Int = events.hashCode()
}

/**
 * A day's event list that tallies how often the day cell runs its own body into [CELL_BODIES].
 *
 * The built-in cell is the composable *under* test rather than a container, so unlike every other
 * measurement here there is no slot to hang a probe on. The count is taken instead from the one
 * question the cell's body asks its event list exactly once per run — `events.isNotEmpty()`, read
 * while building the accessibility description — which makes one tally one composition of the cell.
 *
 * `equals` short-circuits on identity for the same reason [WalkTallyingEvents]'s does: Compose
 * compares this list against its own slot value, and a walk there would be counted as work the cell
 * did not do.
 */
private class CellBodyTallyingEvents(private val events: KalendarEvents) : KalendarEvents by events {

    override fun isEmpty(): Boolean {
        CELL_BODIES.count++
        return events.isEmpty()
    }

    override fun equals(other: Any?): Boolean =
        this === other || (other is CellBodyTallyingEvents && events == other.events)

    override fun hashCode(): Int = events.hashCode()
}

private const val FIXTURE_YEAR = 2026
private const val FIXTURE_MONTH = 3
private const val FIXTURE_DAY = 10

/**
 * What twelve eagerly composed months of 28..42 cells used to cost on the first frame, before the
 * year page's month column became lazy. Kept as the number the fix is measured against.
 */
private const val YEAR_EAGER_CELL_FLOOR = 400

/**
 * First-frame ceiling for the lazy year page, measured with [DAY_PROBE]'s deliberately tiny cell.
 *
 * The probe is [PROBE_SIZE] tall, so a month grid here is a few tens of dp and seven of the twelve
 * months fit in the test window at once — a lazy list has to compose all of them, and the measured
 * cost is 235. That is the *worst* case for this test, not a target: with a day cell of any realistic
 * height (the built-in one is an order of magnitude taller) far fewer months are on screen and the
 * count falls with them, which is the whole point of the container being lazy. The budget is
 * therefore "materially below the eager cost", not a tight fit.
 */
private const val YEAR_LAZY_CELL_CEILING = 300

/** A month grid is 4 to 6 rows of 7, depending on where the 1st falls and how long the month is. */
private const val MONTH_GRID_MIN_CELLS = 28
private const val MONTH_GRID_MAX_CELLS = 42

/**
 * One block per hour, none of them overlapping, all inside [BENCH_WINDOW] with its first hour left
 * free for [BENCH_INSERTED_HOUR].
 */
private const val BLOCK_COUNT = 8
private const val BENCH_FIRST_HOUR = 9
private const val BENCH_INSERTED_HOUR = 8
private val BENCH_WINDOW = KalendarHourWindow(
    startHour = BENCH_INSERTED_HOUR,
    endHour = BENCH_FIRST_HOUR + BLOCK_COUNT,
)
private val BENCH_CONFIG = KalendarViewConfig(
    scheduleVisibleHours = BENCH_WINDOW,
    scheduleInitialScrollHour = BENCH_WINDOW.startHour,
)
private const val BENCH_ROOM_ID = "room-a"
private val BENCH_ROOMS = listOf(KalendarResource(id = BENCH_ROOM_ID, title = "Room A"))

private const val LONG_PRESS_MS = 1_000L
private const val FRAME_MS = 16L

/** Comfortably past [DEFAULT_NOW_TICK], so the indicator's timer has certainly fired. */
private const val TICK_ADVANCE_MS = 90_000L
private const val TICK_MINUTES = 1
private const val NOW_TAG = "now-indicator"

/** Three pointer moves of a quarter-hour each, so every one of them changes the snapped delta. */
private const val DRAG_STEPS = 3
private const val DRAG_STEP_FRACTION = 0.25f

/**
 * Slot compositions allowed when one earlier event is inserted: the new block, and nothing else.
 *
 * Unkeyed, the loop body is memoized by position, so an insertion at the top of the day re-associates
 * every slot below it with a different event — [BLOCK_COUNT] + 1 compositions, and any state a
 * caller's card holds lands on the wrong block. Keyed by the event, the existing subtrees move
 * instead of being rebuilt and only the newcomer composes. The budget is 2 rather than 1 because the
 * grid legitimately re-lays the column once the block count changes.
 */
private const val INSERT_SLOT_BUDGET = 2

/** Page turns per paging measurement — enough that a per-page rebuild cannot read as noise. */
private const val PAGE_TURNS = 4

/** Days a [KalendarSchedule] page covers, and days a [KalendarScheduleWeek] page covers. */
private const val SCHEDULE_PAGE_DAYS = 1
private const val WEEK_PAGE_DAYS = 7

/**
 * Slot compositions allowed for a whole drag gesture.
 *
 * Only the block under the finger changes, and it changes once per snap step: the grab, [DRAG_STEPS]
 * moves and the release. Anything approaching [BLOCK_COUNT] per move means the drag is being read in
 * the `Layout` content lambda, which invalidates every block in the column.
 */
private const val DRAG_SLOT_BUDGET = BLOCK_COUNT

/**
 * Compositions allowed for a whole press of a day cell: one when it goes down, one when it comes up.
 *
 * The press-scale animation is a spring, so it produces frames for as long as it takes to settle —
 * around a dozen at 16ms each. Read with `by` in the cell's body every one of those recomposed the
 * whole cell; read inside a `graphicsLayer` block only the layer is invalidated.
 */
private const val PRESS_COMPOSITION_BUDGET = 2

/**
 * Compositions allowed when a day cell becomes selected: the one its changed parameters earn it.
 *
 * The selection fill animates from a spring too. It belongs to the draw phase, and the cell has no
 * reason to run again for any frame of it.
 */
private const val SELECTION_COMPOSITION_BUDGET = 1

/** A realistic day cell, big enough that a touch at its centre is unambiguously inside it. */
private val DAY_CELL_SIZE = 64.dp
private const val DAY_CELL_TAG = "day-cell"

/**
 * One event, so the cell's indicator row is drawn and the probe below has something to count.
 *
 * Hoisted rather than rebuilt per scope: the probe is the list, and a fresh one per composition
 * would tally its own allocation instead of the cell's work.
 */
private val DAY_CELL_EVENTS = CellBodyTallyingEvents(
    listOf(BasicKalendarEvent(date = LocalDate(FIXTURE_YEAR, FIXTURE_MONTH, FIXTURE_DAY), eventName = "Standup")),
)

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
     * Laying a month grid out must not write snapshot state.
     *
     * The grid records where each cell landed from `onGloballyPositioned`, which runs in the layout
     * phase. Held in a `mutableStateMapOf` and read from the grid's own body — to work out where to
     * put the selection indicator — those 42 writes invalidated the grid on every layout pass, so
     * every first frame composed it twice: once to lay it out, and once because laying it out had
     * written state that composition was reading. The map is a plain one now and every reader is a
     * layout lambda or a `snapshotFlow`.
     *
     * This counts the *cause* rather than the symptom, because the symptom is out of reach: the
     * scope that recomposed is inside the grid, and the day cells never moved — a lazy grid memoizes
     * its item content by key, so the second composition of the grid walked no cells and
     * [CELLS] cannot see it. The cause is anyway the thing worth forbidding.
     */
    @Test
    fun layingOutAMonthGridWritesNoSnapshotMap() {
        reset()
        val canary = mutableStateMapOf<Int, Int>()
        var writes = 0
        val handle = Snapshot.registerGlobalWriteObserver { written ->
            if (written is SnapshotStateMap<*, *>) {
                writes++
            }
        }
        val observed: Int
        try {
            canary[0] = 0
            observed = writes
            writes = 0
            rule.setContent {
                Harness(tick = 0) { KalendarMonth(selectedDate = FIXTURE_DATE, dayContent = DAY_PROBE) }
            }
            rule.waitForIdle()
        } finally {
            handle.dispose()
        }

        println("BENCH month layout: snapshotMapWrites=$writes cells=${CELLS.count}")
        // Vacuity guard: unless the observer saw the deliberate write above it is not watching, and
        // the zero below would keep passing after a real regression.
        assertTrue(observed > 0, "the snapshot write observer never fired for a deliberate map write")
        assertEquals(
            0,
            writes,
            "laying out a month grid wrote $writes snapshot map(s); the grid is tracking its cell " +
                "coordinates in snapshot state again, which recomposes it after every layout pass",
        )
    }

    /**
     * A month grid is 7 columns by up to 6 rows, none of it lazy in the vertical sense because the
     * whole grid is on screen at once. This is the floor, not a regression.
     */
    @Test
    fun monthComposesOneCellPerGridSlot() {
        val cells = countFirstComposition { KalendarMonth(selectedDate = FIXTURE_DATE, dayContent = DAY_PROBE) }
        assertTrue(
            cells in MONTH_GRID_MIN_CELLS..MONTH_GRID_MAX_CELLS,
            "KalendarMonth composed $cells day cells; a month grid is " +
                "$MONTH_GRID_MIN_CELLS..$MONTH_GRID_MAX_CELLS slots",
        )
    }

    /**
     * The year page composes the months on screen, not all twelve.
     *
     * Its month column used to be a non-lazy `verticalScroll` `Column`, which composed every cell of
     * every month before the first frame whether it was visible or not — 404 cells here, against 37
     * for a month and 7 for a week. It is now a `LazyColumn`, and this is the guard on that: a
     * regression to an eager container puts the number straight back over
     * [YEAR_EAGER_CELL_FLOOR].
     *
     * The month grid *inside* each item is still eager ([KalendarStaticGrid]) and must stay that
     * way — a `LazyVerticalGrid` nested in a scrolling parent is measured with an unbounded height
     * and crashes. Laziness belongs to the outer container only.
     */
    @Test
    fun yearComposesOnlyTheMonthsOnScreen() {
        val cells = countFirstComposition { KalendarYear(selectedDate = FIXTURE_DATE, dayContent = DAY_PROBE) }
        assertTrue(
            cells < YEAR_LAZY_CELL_CEILING,
            "KalendarYear composed $cells day cells on its first frame; the month column is composing " +
                "more than the viewport needs (budget < $YEAR_LAZY_CELL_CEILING, eager cost was " +
                "$YEAR_EAGER_CELL_FLOOR+)",
        )
        assertTrue(cells >= MONTH_GRID_MIN_CELLS, "KalendarYear composed $cells day cells; at least one month should")
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

    /**
     * Dragging one block must not recompose the blocks beside it.
     *
     * The live drag is a snapshot state shared by a whole column. Read in the `Layout` content
     * lambda it invalidates all of it, so a single pointer move re-runs every caller's
     * `eventContent` — the exact shape of the focused-date regression above, on the hour grids. The
     * measure policy recomputes the positions from the same state anyway, so composition has no
     * reason to read it.
     */
    @Test
    fun draggingOneScheduleBlockLeavesTheOtherBlocksAlone() {
        val slots = countDuringDrag {
            KalendarSchedule(
                state = rememberKalendarScheduleState(initialDate = FIXTURE_DATE),
                events = benchBlocks(),
                config = BENCH_CONFIG,
                onEventTimeChange = { _, _, _ -> },
                eventContent = BLOCK_PROBE,
            )
        }
        assertTrue(
            slots < DRAG_SLOT_BUDGET,
            "a drag on KalendarSchedule recomposed $slots event slots; the drag state is being read " +
                "in the Layout content lambda rather than in measure (budget < $DRAG_SLOT_BUDGET)",
        )
    }

    /** [draggingOneScheduleBlockLeavesTheOtherBlocksAlone] for the lane grid. */
    @Test
    fun draggingOneResourceBlockLeavesTheOtherBlocksAlone() {
        val slots = countDuringDrag {
            KalendarResourceView(
                resources = BENCH_ROOMS,
                state = rememberKalendarScheduleState(initialDate = FIXTURE_DATE),
                events = benchBlocks(),
                config = BENCH_CONFIG,
                onEventChange = { _, _, _, _ -> },
                eventContent = BLOCK_PROBE,
            )
        }
        assertTrue(
            slots < DRAG_SLOT_BUDGET,
            "a drag on KalendarResourceView recomposed $slots event slots; the drag state is being " +
                "read in the Layout content lambda rather than in measure (budget < $DRAG_SLOT_BUDGET)",
        )
    }

    /**
     * A minute passing must not recompose a single event block.
     *
     * The now-indicator's reading is a snapshot state that changes once a minute for as long as a
     * Schedule view is on screen, so it is the one value in these views guaranteed to churn. It is
     * handed down as a lambda and only ever invoked inside the indicator's own composable — read
     * anywhere above that, every block on the grid would recompose sixty times an hour.
     */
    @Test
    fun aNowIndicatorTickRecomposesNoScheduleBlock() {
        reset()
        val clock = TickingClock()
        rule.setContent {
            ProvideKalendarTimeSource(KalendarTimeSource(timeZone = TimeZone.UTC, clock = clock)) {
                MaterialTheme(colorScheme = STABLE_SCHEME) {
                    KalendarTheme(animations = STILL_ANIMATIONS) {
                        KalendarSchedule(
                            state = rememberKalendarScheduleState(initialDate = FIXTURE_DATE),
                            events = benchBlocks(),
                            config = BENCH_CONFIG,
                            nowIndicator = { Box(modifier = Modifier.size(PROBE_SIZE).testTag(NOW_TAG)) },
                            eventContent = BLOCK_PROBE,
                        )
                    }
                }
            }
        }
        rule.waitForIdle()
        val before = BLOCKS.count
        val lineBefore = rule.onNodeWithTag(NOW_TAG, useUnmergedTree = true).getUnclippedBoundsInRoot().top

        clock.instant += TICK_MINUTES.minutes
        rule.mainClock.advanceTimeBy(TICK_ADVANCE_MS)
        rule.waitForIdle()

        val lineAfter = rule.onNodeWithTag(NOW_TAG, useUnmergedTree = true).getUnclippedBoundsInRoot().top
        println("BENCH after a now tick: slotsAfter=${BLOCKS.count - before} line=$lineBefore->$lineAfter")
        // Vacuity guard: unless the line actually moved, the timer never fired and the count below
        // would be a zero that keeps passing after a real regression.
        assertTrue(
            lineAfter > lineBefore,
            "the now-indicator did not move ($lineBefore to $lineAfter); the tick never reached the view",
        )
        assertEquals(
            0,
            BLOCKS.count - before,
            "a minute tick recomposed ${BLOCKS.count - before} event slots; the now reading is being " +
                "made in a composable body instead of inside the indicator's own layout lambda",
        )
    }

    /** A clock the test moves by hand, so the now-indicator's timer can be made to fire on cue. */
    private class TickingClock : Clock {
        var instant: Instant = Instant.parse("2026-03-10T09:00:00Z")

        override fun now(): Instant = instant
    }

    /**
     * Inserting an event at the top of the day must not re-associate the blocks below it.
     *
     * This is the per-block keying guard. Unkeyed the loop body is memoized positionally, so every
     * later slot is handed a different event — and with it any state a caller's card holds, an
     * expand animation or a text-field cursor, jumps to the wrong block. The count is the visible
     * half of that: the whole column recomposes.
     */
    @Test
    fun insertingAnEarlierEventRecomposesOnlyTheNewScheduleBlock() {
        val slots = countAfterInsertingAnEarlierEvent { events ->
            KalendarSchedule(
                state = rememberKalendarScheduleState(initialDate = FIXTURE_DATE),
                events = events,
                config = BENCH_CONFIG,
                eventContent = BLOCK_PROBE,
            )
        }
        assertTrue(
            slots <= INSERT_SLOT_BUDGET,
            "inserting one earlier event recomposed $slots KalendarSchedule blocks; the block loop is " +
                "memoized by position rather than keyed by its event (budget <= $INSERT_SLOT_BUDGET)",
        )
    }

    /** [insertingAnEarlierEventRecomposesOnlyTheNewScheduleBlock] for the lane grid. */
    @Test
    fun insertingAnEarlierEventRecomposesOnlyTheNewResourceBlock() {
        val slots = countAfterInsertingAnEarlierEvent { events ->
            KalendarResourceView(
                resources = BENCH_ROOMS,
                state = rememberKalendarScheduleState(initialDate = FIXTURE_DATE),
                events = events,
                config = BENCH_CONFIG,
                eventContent = BLOCK_PROBE,
            )
        }
        assertTrue(
            slots <= INSERT_SLOT_BUDGET,
            "inserting one earlier event recomposed $slots KalendarResourceView blocks; the block loop " +
                "is memoized by position rather than keyed by its event (budget <= $INSERT_SLOT_BUDGET)",
        )
    }

    /**
     * Turning a [KalendarSchedule] page must not rebuild the date index.
     *
     * `byDateExpandingSpans()` is linear in the caller's *whole* event list, not in the visible
     * day's — so called below the pager it is per-swipe work that scales with an event count the
     * library does not control. Built above the pager and keyed on the list, a page turn reuses it.
     */
    @Test
    fun turningSchedulePagesRebuildsNoEventIndex() {
        val builds = countIndexBuildsWhileTurningPages(
            daysPerPage = SCHEDULE_PAGE_DAYS,
            rememberState = { rememberKalendarScheduleState(initialDate = FIXTURE_DATE) },
        ) { events, state ->
            KalendarSchedule(
                state = state,
                events = events,
                config = BENCH_CONFIG,
                eventContent = BLOCK_PROBE,
            )
        }
        assertEquals(
            0,
            builds,
            "$PAGE_TURNS KalendarSchedule page turns rebuilt the event index $builds times; " +
                "byDateExpandingSpans() is being called below the pager",
        )
    }

    /** [turningSchedulePagesRebuildsNoEventIndex] for the week grid. */
    @Test
    fun turningScheduleWeekPagesRebuildsNoEventIndex() {
        val builds = countIndexBuildsWhileTurningPages(
            daysPerPage = WEEK_PAGE_DAYS,
            rememberState = { rememberKalendarWeekState(initialDate = FIXTURE_DATE) },
        ) { events, state ->
            KalendarScheduleWeek(
                state = state,
                events = events,
                config = BENCH_CONFIG,
                eventContent = BLOCK_PROBE,
            )
        }
        assertEquals(
            0,
            builds,
            "$PAGE_TURNS KalendarScheduleWeek page turns rebuilt the event index $builds times; " +
                "byDateExpandingSpans() is being called below the pager",
        )
    }

    /**
     * Composes [view] over a [WalkTallyingEvents] list, turns [PAGE_TURNS] pages one at a time, and
     * returns how many times the date index was rebuilt on the way.
     *
     * Pages are turned through the state rather than by a swipe: a fling coasts an unpredictable
     * number of pages, and the count would then depend on the injector's timing rather than on the
     * library.
     */
    private fun countIndexBuildsWhileTurningPages(
        daysPerPage: Int,
        rememberState: @Composable () -> KalendarViewState,
        view: @Composable (KalendarEvents, KalendarViewState) -> Unit,
    ): Int {
        reset()
        val events = WalkTallyingEvents(benchBlocks())
        var target by mutableStateOf<LocalDate?>(null)
        var captured: KalendarViewState? = null
        rule.setContent {
            Harness(tick = 0) {
                val state = rememberState()
                captured = state
                LaunchedEffect(target) { target?.let { state.animateScrollTo(it) } }
                view(events, state)
            }
        }
        rule.waitForIdle()
        val firstFrame = EVENT_WALKS.count
        val startedOn = checkNotNull(captured).visibleDate
        repeat(PAGE_TURNS) { turn ->
            target = startedOn.plus((turn + 1) * daysPerPage, DateTimeUnit.DAY)
            rule.waitForIdle()
        }
        val builds = EVENT_WALKS.count - firstFrame
        val landedOn = checkNotNull(captured).visibleDate
        println("BENCH after $PAGE_TURNS page turns: indexBuildsFirstFrame=$firstFrame indexBuildsPaging=$builds")
        // Vacuity guards. Unless the tally saw the first build the wrapper is not being walked at
        // all, and unless the pager actually moved there was nothing to measure — either way the
        // count below would be a zero that keeps passing after a real regression.
        assertEquals(1, firstFrame, "the event index was built $firstFrame times on the first frame, not once")
        assertEquals(
            startedOn.plus(PAGE_TURNS * daysPerPage, DateTimeUnit.DAY),
            landedOn,
            "the pager did not turn $PAGE_TURNS pages, so this measurement proves nothing",
        )
        return builds
    }

    /** [BLOCK_COUNT] back-to-back one-hour events inside [BENCH_WINDOW], all in [BENCH_ROOM_ID]. */
    private fun benchBlocks(): KalendarEvents = List(BLOCK_COUNT) { index ->
        benchBlock(hour = BENCH_FIRST_HOUR + index, name = "Block $index")
    }

    /**
     * One block, deliberately without an [com.himanshoe.kalendar.foundation.event.KalendarEvent.id].
     *
     * The easy id-keyed path is not the one that regresses — the fallback is, and a fixture that
     * supplied ids everywhere would never exercise it.
     */
    private fun benchBlock(hour: Int, name: String): BasicKalendarEvent = BasicKalendarEvent(
        date = FIXTURE_DATE,
        eventName = name,
        startTime = LocalDateTime(FIXTURE_DATE, LocalTime(hour = hour, minute = 0)),
        endTime = LocalDateTime(FIXTURE_DATE, LocalTime(hour = hour + 1, minute = 0)),
        calendarId = BENCH_ROOM_ID,
    )

    /**
     * Composes [view], long-press-drags its first block down the grid, and returns how many
     * `eventContent` slots that gesture composed.
     */
    private fun countDuringDrag(view: @Composable () -> Unit): Int {
        reset()
        rule.setContent { Harness(tick = 0) { view() } }
        rule.waitForIdle()
        val before = BLOCKS.count
        // Unmerged: the block's clickable wrapper merges its descendants, so the tag the probe puts
        // on the slot itself is only reachable in the unmerged tree.
        rule.onNodeWithTag("Block 0", useUnmergedTree = true).performTouchInput {
            down(center)
            advanceEventTime(LONG_PRESS_MS)
            // A frame between moves, or the injector dispatches all three under one timestamp and
            // Compose collapses them into a single recomposition — which would hide the regression.
            repeat(DRAG_STEPS) {
                advanceEventTime(FRAME_MS)
                moveBy(Offset(x = 0f, y = height * DRAG_STEP_FRACTION))
            }
            up()
        }
        rule.waitForIdle()
        val delta = BLOCKS.count - before
        println("BENCH after drag: slotsFirst=$before slotsDuringDrag=$delta blocks=$BLOCK_COUNT")
        return delta
    }

    /**
     * Composes [view] over [benchBlocks], prepends one earlier event, and returns how many
     * `eventContent` slots the resulting recomposition walked.
     */
    private fun countAfterInsertingAnEarlierEvent(view: @Composable (KalendarEvents) -> Unit): Int {
        reset()
        val events = mutableStateOf(benchBlocks())
        rule.setContent { Harness(tick = 0) { view(events.value) } }
        rule.waitForIdle()
        val before = BLOCKS.count
        rule.runOnIdle {
            events.value = listOf(benchBlock(hour = BENCH_INSERTED_HOUR, name = "Inserted")) + events.value
        }
        rule.waitForIdle()
        val delta = BLOCKS.count - before
        println("BENCH after inserting an earlier event: slotsFirst=$before slotsAfter=$delta blocks=$BLOCK_COUNT")
        return delta
    }

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
        BLOCKS.count = 0
        CALL_SITES.count = 0
        EVENT_WALKS.count = 0
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

/**
 * Recomposition budgets for [KalendarDayCellDefaults.Cell] itself.
 *
 * A separate class from [KalendarRecompositionBenchmark] because it measures a different thing.
 * Every budget there is on a *view*, counted through a probe handed to one of its slots; these two
 * compose the built-in day cell directly and count runs of its own body, which needs the probe to be
 * the cell's event list rather than a slot — see [CellBodyTallyingEvents]. Same harness, same
 * reasons, one level down.
 */
class KalendarDayCellRecompositionBenchmark {

    @get:Rule
    val rule = createComposeRule()

    /**
     * Pressing a day cell must not recompose it once per frame of the press-scale animation.
     *
     * `animateFloatAsState` hands back a [androidx.compose.runtime.State]. Unwrapped with `by` in
     * the cell's body, the read is a composition-phase subscription and every frame the spring
     * produces re-runs the whole cell — modifiers, lambdas, indicator row and all — for a value only
     * the graphics layer needed. Read inside the layer block instead, the animation never leaves the
     * phase it belongs to.
     */
    @Test
    fun pressingADayCellRecomposesItOncePerPressRatherThanPerFrame() {
        CELL_BODIES.count = 0
        val scope = dayCellScope(isSelected = false)
        rule.setContent { DayCellHarness { KalendarDayCellDefaults.Cell(scope = scope, onClick = {}) } }
        rule.waitForIdle()

        val firstFrame = CELL_BODIES.count
        rule.onNodeWithTag(DAY_CELL_TAG, useUnmergedTree = true).performTouchInput { down(center) }
        rule.waitForIdle()
        val onPress = CELL_BODIES.count - firstFrame
        rule.onNodeWithTag(DAY_CELL_TAG, useUnmergedTree = true).performTouchInput { up() }
        rule.waitForIdle()
        val onRelease = CELL_BODIES.count - firstFrame - onPress

        println("BENCH day cell press: firstFrame=$firstFrame press=$onPress release=$onRelease")
        // Vacuity guard: unless the touch actually landed on the cell it never changed the pressed
        // state, and the budget below would be a zero that keeps passing after a real regression.
        assertTrue(onPress > 0, "the press never reached the cell, so this measurement proves nothing")
        assertTrue(
            onPress + onRelease <= PRESS_COMPOSITION_BUDGET,
            "a press recomposed the day cell ${onPress + onRelease} times; the press-scale animation " +
                "is being read in the cell's body instead of in a graphicsLayer block " +
                "(budget <= $PRESS_COMPOSITION_BUDGET)",
        )
    }

    /** [pressingADayCellRecomposesItOncePerPressRatherThanPerFrame] for the selection fill. */
    @Test
    fun selectingADayCellRecomposesItOnceRatherThanPerFrame() {
        CELL_BODIES.count = 0
        val selected = mutableStateOf(false)
        rule.setContent {
            DayCellHarness {
                KalendarDayCellDefaults.Cell(scope = dayCellScope(isSelected = selected.value), onClick = {})
            }
        }
        rule.waitForIdle()

        val firstFrame = CELL_BODIES.count
        rule.runOnIdle { selected.value = true }
        rule.waitForIdle()
        val onSelect = CELL_BODIES.count - firstFrame

        println("BENCH day cell selection: firstFrame=$firstFrame select=$onSelect")
        // Vacuity guard, as above: a selection the cell never saw is not a measurement.
        assertTrue(onSelect > 0, "the selection never reached the cell, so this measurement proves nothing")
        assertTrue(
            onSelect <= SELECTION_COMPOSITION_BUDGET,
            "selecting a day recomposed its cell $onSelect times; the selection colour is being read " +
                "in the cell's body instead of in a draw lambda (budget <= $SELECTION_COMPOSITION_BUDGET)",
        )
    }

    /**
     * One day's scope over a [CellBodyTallyingEvents] list, rebuilt per call so a selection change
     * genuinely re-offers the cell a different value.
     */
    private fun dayCellScope(isSelected: Boolean): KalendarDayScope<KalendarEvent> = KalendarDayScope(
        date = FIXTURE_DATE,
        isSelected = isSelected,
        isToday = false,
        isDisabled = false,
        events = DAY_CELL_EVENTS,
    )

    /**
     * The day cell under a theme whose animations are *on* — the opposite of the harness the
     * view budgets use, and the
     * whole point: these two measurements are of what the animations cost.
     */
    @Composable
    private fun DayCellHarness(content: @Composable () -> Unit) {
        MaterialTheme(colorScheme = STABLE_SCHEME) {
            KalendarTheme {
                Box(modifier = Modifier.size(DAY_CELL_SIZE).testTag(DAY_CELL_TAG)) {
                    content()
                }
            }
        }
    }
}

/**
 * [KalendarRecompositionBenchmark.arrowKeyMovesFocusWithoutRecomposingAnyCell] for the two calendars
 * whose grids live inside a scrolling container.
 *
 * A separate class from the budgets above only because there are already fourteen of those; the
 * harness, the probes and the frozen clock are the file's and are shared verbatim.
 *
 * These two are the ones with somewhere new to go wrong. [KalendarMonth] draws its ring over a grid
 * that cannot scroll, so a cell's position changes only when the whole grid is re-laid-out; a year's
 * month column and a timeline's month list both move their cells under the ring continuously, and
 * the ring has to follow. Following by *recomposing* would be the easy way to do it and would cost a
 * month of cells per arrow press — so the position is tracked in a plain map written from
 * `onGloballyPositioned`, read only from the ring's own layout lambdas, and this is what holds that
 * line.
 */
@OptIn(ExperimentalTestApi::class)
class KalendarScrollingKeyboardRecompositionBenchmark {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun arrowKeyMovesFocusWithoutRecomposingAnyCellInYear() {
        assertArrowKeyRecomposesNoCell("KalendarYear") {
            KalendarYear(selectedDate = FIXTURE_DATE, header = HEADER_PROBE, dayContent = DAY_PROBE)
        }
    }

    @Test
    fun arrowKeyMovesFocusWithoutRecomposingAnyCellInTimeline() {
        assertArrowKeyRecomposesNoCell("KalendarTimeline") {
            KalendarTimeline(selectedDate = FIXTURE_DATE, header = HEADER_PROBE, dayContent = DAY_PROBE)
        }
    }

    /**
     * Tabs into [view], presses one arrow, and asserts that no day cell composed on the way.
     *
     * The ring's bounds are read either side of the press as the vacuity guard, and it is not
     * optional: a Tab that never reached the calendar, or an arrow the calendar ignored, would leave
     * the cell count at zero and the assertion below would keep passing through any regression. The
     * arrow used is a step *within* the visible month, which is the case that must cost nothing at
     * all — one that crosses a month legitimately scrolls, and a scroll composes the cells it
     * reveals.
     */
    private fun assertArrowKeyRecomposesNoCell(name: String, view: @Composable () -> Unit) {
        CELLS.count = 0
        HEADERS.count = 0
        rule.setContent {
            ProvideKalendarTimeSource(FROZEN_TIME_SOURCE) {
                MaterialTheme(colorScheme = STABLE_SCHEME) {
                    KalendarTheme(animations = STILL_ANIMATIONS) {
                        view()
                    }
                }
            }
        }
        rule.waitForIdle()
        rule.onRoot().performKeyInput { pressKey(Key.Tab) }
        rule.waitForIdle()
        val ringOnEntry = focusRingBounds()

        val before = CELLS.count
        rule.onRoot().performKeyInput { pressKey(Key.DirectionRight) }
        rule.waitForIdle()
        val ringAfterArrow = focusRingBounds()

        println("BENCH $name after arrow key: cellsBefore=$before cellsAfter=${CELLS.count}")
        assertTrue(ringOnEntry.width > 0f, "Tab never focused $name, so this measurement proves nothing")
        assertTrue(
            ringAfterArrow.left > ringOnEntry.left,
            "the arrow did not move $name's focus ring ($ringOnEntry to $ringAfterArrow), " +
                "so this measurement proves nothing",
        )
        assertEquals(
            0,
            CELLS.count - before,
            "an arrow press recomposed ${CELLS.count - before} of $name's day cells; the focused date " +
                "is being read in a composable body instead of in a layout or semantics lambda",
        )
    }

    /** The bounds of whichever focus ring is drawn, or [Rect.Zero] when none is. */
    private fun focusRingBounds(): Rect = rule.onAllNodesWithTag(KEYBOARD_FOCUS_RING_TAG)
        .fetchSemanticsNodes()
        .map { it.boundsInRoot }
        .firstOrNull { it.width > 0f }
        ?: Rect.Zero
}
