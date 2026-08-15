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

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.dp
import com.himanshoe.kalendar.component.KEYBOARD_FOCUS_RING_TAG
import com.himanshoe.kalendar.foundation.event.KalendarEvent
import com.himanshoe.kalendar.state.KalendarViewState
import com.himanshoe.kalendar.state.rememberKalendarYearState
import com.himanshoe.kalendar.theme.KalendarAnimations
import com.himanshoe.kalendar.theme.KalendarTheme
import kotlinx.datetime.LocalDate
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Keyboard navigation for [KalendarYear], whose twelve month grids scroll inside each year page.
 *
 * The interesting cases are the seams: the end of a week row, the end of a month grid, and the end of
 * December. All three are the same arithmetic — a date one day later — and this pins that they read
 * as the next row, the next grid, and the next year page respectively.
 *
 * `showAdjacentMonthDates` is off throughout. It is not cosmetic here: with it on, April 1st is drawn
 * twice on one page — once as March's trailing padding and once as April's own cell — and an
 * assertion that looks a date up by its announcement could not say which one it found.
 */
@OptIn(ExperimentalTestApi::class)
class KalendarYearKeyboardUiTest {

    @get:Rule
    val rule = createComposeRule()

    private val chromeless = KalendarViewConfig(
        showNavigationArrows = false,
        showTodayButton = false,
        showJumpPicker = false,
        showAdjacentMonthDates = false,
    )

    private val before = FocusRequester()
    private var afterFocused = false

    /** Wraps [content] between two bare focusables, so Tab order either side of the year is observable. */
    @Composable
    private fun Harness(content: @Composable () -> Unit) {
        Column {
            Box(
                modifier = Modifier
                    .size(PROBE_SIZE)
                    .focusRequester(before)
                    .focusable(),
            )
            Box(modifier = Modifier.size(width = CALENDAR_WIDTH, height = CALENDAR_HEIGHT)) {
                content()
            }
            Box(
                modifier = Modifier
                    .size(PROBE_SIZE)
                    .onFocusChanged { afterFocused = it.isFocused }
                    .focusable(),
            )
        }
    }

    /** Composes a year view and returns a reader for the year currently paged into view. */
    private fun setYear(
        selectedDate: LocalDate = march(TENTH),
        config: KalendarViewConfig = chromeless,
        onDateClick: (LocalDate, List<KalendarEvent>) -> Unit = { _, _ -> },
    ): () -> Int {
        var captured: KalendarViewState? = null
        rule.setContent {
            KalendarTheme(animations = KalendarAnimations(enabled = false)) {
                val state = rememberKalendarYearState(selectedDate)
                captured = state
                Harness {
                    KalendarYear(
                        selectedDate = selectedDate,
                        state = state,
                        config = config,
                        onDateClick = onDateClick,
                    )
                }
            }
        }
        rule.runOnIdle { before.requestFocus() }
        rule.waitForIdle()
        return { rule.runOnIdle { checkNotNull(captured).visibleDate.year } }
    }

    private fun tab() {
        rule.onRoot().performKeyInput { pressKey(Key.Tab) }
        rule.waitForIdle()
    }

    private fun press(key: Key) {
        rule.onRoot().performKeyInput { pressKey(key) }
        rule.waitForIdle()
    }

    private fun cell(date: String): SemanticsNodeInteraction =
        rule.onNode(hasContentDescription(date) and hasClickAction())

    /**
     * The bounds of the focus ring that is actually drawn, or [Rect.Zero] when none is.
     *
     * Every year page carries a ring node of its own, and a page mid-swipe is briefly composed
     * alongside the one being left; only the page holding the focused date ever measures its ring to
     * a non-zero size, so picking the sized one is what makes this unambiguous.
     */
    private fun ringBounds(): Rect = rule.onAllNodesWithTag(KEYBOARD_FOCUS_RING_TAG)
        .fetchSemanticsNodes()
        .map { it.boundsInRoot }
        .firstOrNull { it.width > 0f }
        ?: Rect.Zero

    private fun assertRingOn(date: String) {
        val ring = ringBounds()
        val target = cell(date).fetchSemanticsNode().boundsInRoot
        assertTrue(
            ring.overlaps(target),
            "focus ring at $ring should sit over the cell for $date at $target",
        )
    }

    @Test
    fun tab_entersTheYearOnceAndLeavesItOnce() {
        setYear()

        assertEquals(0f, ringBounds().width, "the ring should be invisible before the year is focused")

        tab()
        assertTrue(ringBounds().width > 0f, "one Tab should put focus in the year")
        assertEquals(false, afterFocused, "Tab reached past the year on the first press")

        tab()
        assertTrue(afterFocused, "a second Tab should leave the year, not step to the next day cell")
        assertEquals(0f, ringBounds().width, "the ring should disappear once the year loses focus")
    }

    /**
     * A year page opens on January whatever the selected date is, so entering the grid has to bring
     * the focused month down the list before there is a cell for the ring to sit on at all.
     */
    @Test
    fun tabEntersAtTheSelectedDateAndScrollsItsMonthIntoView() {
        setYear()
        tab()

        assertRingOn("March 10, 2026")
    }

    /** March 15th 2026 is a Sunday: the last cell of a Monday-first week row. */
    @Test
    fun rightArrowWrapsFromTheEndOfOneWeekRowToTheStartOfTheNext() {
        setYear(selectedDate = march(FIFTEENTH))
        tab()

        press(Key.DirectionRight)

        assertRingOn("March 16, 2026")
    }

    /**
     * Off the end of a month grid the ring moves into the *next month's* grid rather than stopping,
     * and the month column scrolls to show it.
     */
    @Test
    fun rightArrowPastTheEndOfAMonthMovesIntoTheNextMonthsGrid() {
        val visibleYear = setYear(selectedDate = march(THIRTY_FIRST))
        tab()

        press(Key.DirectionRight)

        assertEquals(FIXTURE_YEAR, visibleYear(), "crossing a month must not page the year")
        assertRingOn("April 1, 2026")
    }

    /** Past December there is no next grid, so the *page* moves and the ring lands on the 1st. */
    @Test
    fun rightArrowPastDecemberPagesTheYear() {
        val visibleYear = setYear(selectedDate = LocalDate(FIXTURE_YEAR, DECEMBER, THIRTY_FIRST))
        tab()
        assertRingOn("December 31, 2026")

        press(Key.DirectionRight)

        assertEquals(FIXTURE_YEAR + 1, visibleYear(), "the pager should have moved to the next year")
        assertRingOn("January 1, 2027")
    }

    @Test
    fun homeAndEndJumpToTheEdgesOfTheVisibleYear() {
        setYear()
        tab()

        press(Key.MoveHome)
        assertRingOn("January 1, 2026")

        press(Key.MoveEnd)
        assertRingOn("December 31, 2026")
    }

    @Test
    fun pageDownAndPageUpMoveOneYear() {
        val visibleYear = setYear()
        tab()

        press(Key.PageDown)
        assertEquals(FIXTURE_YEAR + 1, visibleYear())
        assertRingOn("March 10, 2027")

        press(Key.PageUp)
        assertEquals(FIXTURE_YEAR, visibleYear())
        assertRingOn("March 10, 2026")
    }

    @Test
    fun enterSelectsTheFocusedDate() {
        var clicked: LocalDate? = null
        setYear(onDateClick = { date, _ -> clicked = date })
        tab()

        press(Key.DirectionRight)
        press(Key.Enter)

        assertEquals(march(ELEVENTH), clicked)
    }

    @Test
    fun disabledDatesAreFocusableButNotSelectable() {
        val disabled = march(ELEVENTH)
        var clicked: LocalDate? = null
        setYear(
            config = chromeless.copy(disabledDates = { it == disabled }),
            onDateClick = { date, _ -> clicked = date },
        )
        tab()

        press(Key.DirectionRight)
        assertRingOn("March 11, 2026")

        press(Key.Enter)
        assertNull(clicked, "Enter on a disabled date must not select it")
    }

    /**
     * The live region announces where focus went. The assertion looks for a *non-clickable* node
     * carrying the date's description, which is what tells the year's announcement apart from the day
     * cell that carries the same words and is clickable.
     */
    @Test
    fun movingFocusAnnouncesTheNewlyFocusedDate() {
        setYear()
        tab()

        rule.onNode(hasContentDescription("March 10, 2026") and !hasClickAction()).assertExists()

        press(Key.DirectionRight)

        rule.onNode(hasContentDescription("March 11, 2026") and !hasClickAction()).assertExists()
        rule.onNode(hasContentDescription("March 10, 2026") and !hasClickAction()).assertDoesNotExist()
    }

    private fun march(day: Int): LocalDate = LocalDate(FIXTURE_YEAR, MARCH, day)

    private companion object {
        const val FIXTURE_YEAR = 2026
        const val MARCH = 3
        const val DECEMBER = 12
        const val TENTH = 10
        const val ELEVENTH = 11
        const val FIFTEENTH = 15
        const val THIRTY_FIRST = 31
        val PROBE_SIZE = 8.dp

        /** Wide enough for seven readable columns, tall enough for a whole month grid to be on screen. */
        val CALENDAR_WIDTH = 420.dp
        val CALENDAR_HEIGHT = 560.dp
    }
}
