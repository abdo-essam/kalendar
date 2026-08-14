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
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.himanshoe.kalendar.component.SELECTION_INDICATOR_TAG
import kotlinx.datetime.LocalDate
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The two widths the relayout test measures at, and the height it holds constant. */
private val WIDE = 360.dp
private val NARROW = 260.dp
private val TALL = 480.dp

/**
 * How far, in pixels, the indicator's centre may sit from its cell's.
 *
 * The two nodes differ by the day-cell padding at their edges but share a centre exactly, so this is
 * rounding slack rather than a budget. `boundsInRoot` is in pixels, not dp — hence a raw float.
 */
private const val CENTRE_TOLERANCE_PX = 1f

/**
 * Guards the sliding selection indicator against the ways it has gone wrong: reappearing at the
 * origin, disappearing on a single-pass render, and being left behind when the grid moves under it.
 *
 * The golden tests can see neither. They render after layout has settled, so a frame drawn at
 * [androidx.compose.ui.geometry.Offset.Zero] is already gone by the time the bitmap is captured —
 * that one was only ever visible in a browser — and an indicator that waits on a coroutine still
 * arrives eventually when the clock runs freely. Stopping the clock is what makes the first frame
 * observable.
 */
class KalendarSelectionIndicatorTest {

    @get:Rule
    val rule = createComposeRule()

    @Suppress("MagicNumber")
    private val march = LocalDate(2026, 3, 10)

    @Suppress("MagicNumber")
    private val laterInMarch = LocalDate(2026, 3, 17)

    /**
     * Where the indicator sits, and how big it is, are decided in the layout phase from the cell
     * coordinates the grid tracks. With the clock stopped — before any coroutine has been given a
     * chance to run — it is therefore already on the selected cell.
     *
     * Both halves of that are load-bearing and pull against each other. Drawing the animator's value
     * before it has been snapped to a real position put the indicator in the top-left corner for a
     * frame; but gating the indicator's *existence* on the coroutine that snaps it would be worse,
     * because a single-pass render — which is every golden test, and every screenshot a consumer
     * takes — would then omit the selection entirely. Resolving position and size in layout is what
     * satisfies both, and this test fails if either drifts back into composition or into an effect.
     */
    @Test
    fun indicator_isOnTheSelectedCellBeforeTheClockHasRun() {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            KalendarMonth(selectedDate = march)
        }

        val indicator = rule.onNodeWithTag(SELECTION_INDICATOR_TAG).fetchSemanticsNode().boundsInRoot
        val cell = rule.onNodeWithContentDescription("March 10, 2026").fetchSemanticsNode().boundsInRoot

        assertTrue(
            indicator.left > 0f || indicator.top > 0f,
            "indicator drew at the origin on its first frame instead of on the selected cell, was $indicator",
        )
        assertTrue(
            indicator.overlaps(cell),
            "indicator at $indicator should already cover the selected cell at $cell on the first " +
                "frame; it is waiting for a coroutine or a second composition to find out where to go",
        )
    }

    @Test
    fun indicator_tracksTheSelectedCellOnceSettled() {
        rule.setContent {
            KalendarMonth(selectedDate = march)
        }
        rule.waitForIdle()

        val indicator = rule.onNodeWithTag(SELECTION_INDICATOR_TAG).fetchSemanticsNode().boundsInRoot
        val cell = rule.onNodeWithContentDescription("March 10, 2026").fetchSemanticsNode().boundsInRoot

        assertTrue(
            indicator.overlaps(cell),
            "indicator at $indicator should sit over the selected cell at $cell",
        )
    }

    /**
     * The indicator must follow its cell when the *grid* moves under it, not only when the
     * selection moves.
     *
     * Nothing recomposes when a calendar is merely re-laid out — a resize, a rotation, a switch to a
     * work week — so an indicator whose position was worked out in composition, or from a coroutine
     * woken only by the selection changing, is left behind on the pixels the selected cell used to
     * occupy. Both the shape this grid had before and the first shape that replaced it failed this,
     * in the second case with the right size in the wrong place, which is the kind of wrong that
     * looks deliberate in a screenshot. The cell's tracked placement is what makes it re-target.
     */
    @Test
    fun indicator_followsTheSelectedCellWhenTheGridIsRelaidOut() {
        var width by mutableStateOf(WIDE)
        rule.setContent {
            Box(modifier = Modifier.size(width = width, height = TALL)) {
                KalendarMonth(selectedDate = march)
            }
        }
        rule.waitForIdle()
        val before = rule.onNodeWithContentDescription("March 10, 2026").fetchSemanticsNode().boundsInRoot

        rule.runOnIdle { width = NARROW }
        rule.waitForIdle()

        val cell = rule.onNodeWithContentDescription("March 10, 2026").fetchSemanticsNode().boundsInRoot
        val indicator = rule.onNodeWithTag(SELECTION_INDICATOR_TAG).fetchSemanticsNode().boundsInRoot
        // Vacuity guard: unless the narrower grid actually moved the cell, an indicator that never
        // re-targeted would pass this on the strength of the layout it was already on.
        assertTrue(before.left != cell.left, "the resize did not move the selected cell, still at $cell")
        // Centres rather than `overlaps`: a stale indicator one cell-width out still clips the
        // corner of the cell it should be centred on, and overlapping would call that a pass.
        assertTrue(
            (indicator.center - cell.center).getDistance() <= CENTRE_TOLERANCE_PX,
            "indicator stayed at $indicator after the grid was re-laid out; the selected cell is now at $cell",
        )
    }

    /**
     * A multi-select calendar has no single date for the indicator to slide to, and must therefore
     * draw no indicator at all — every selected cell fills itself instead.
     *
     * Worth its own test now that the indicator is composed unconditionally and measured away to
     * nothing when it has no cell to cover: "not drawn" is a layout-phase decision like the rest of
     * it, and this is what stops that turning into a stray full-size overlay on a view that never
     * asked for one.
     */
    @Test
    fun indicator_isAbsentWhenNoSingleDateIsSelected() {
        rule.setContent {
            KalendarMonth(selectedDate = march, selectedDates = setOf(march, laterInMarch))
        }
        rule.waitForIdle()

        assertEquals(
            0,
            rule.onAllNodesWithTag(SELECTION_INDICATOR_TAG).fetchSemanticsNodes().size,
            "a multi-select month drew a sliding selection indicator",
        )
    }
}
