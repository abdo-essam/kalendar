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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.dp
import com.himanshoe.kalendar.foundation.event.BasicKalendarEvent
import com.himanshoe.kalendar.foundation.event.KalendarEvents
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
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

/**
 * The `eventContent` slot handed to the hour-grid views under test.
 *
 * It fills its slot rather than drawing the built-in block, so a touch injected at the node's centre
 * is the centre of the *block* — which is what puts the press in the move region instead of on a
 * resize edge. Contravariance makes the same probe serve
 * [KalendarResourceView]'s [KalendarResourceEventScope] slot.
 */
private val BLOCK_PROBE: @Composable (KalendarScheduleEventScope) -> Unit = { scope ->
    BLOCKS.count++
    Box(modifier = Modifier.fillMaxSize().testTag(scope.event.eventName))
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

/**
 * Slot compositions allowed for a whole drag gesture.
 *
 * Only the block under the finger changes, and it changes once per snap step: the grab, [DRAG_STEPS]
 * moves and the release. Anything approaching [BLOCK_COUNT] per move means the drag is being read in
 * the `Layout` content lambda, which invalidates every block in the column.
 */
private const val DRAG_SLOT_BUDGET = BLOCK_COUNT

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
