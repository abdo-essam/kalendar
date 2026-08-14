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
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.withKeyDown
import androidx.compose.ui.unit.dp
import com.himanshoe.kalendar.component.KEYBOARD_FOCUS_RING_TAG
import com.himanshoe.kalendar.foundation.event.KalendarEvent
import com.himanshoe.kalendar.state.KalendarViewState
import com.himanshoe.kalendar.state.rememberKalendarMonthState
import com.himanshoe.kalendar.theme.KalendarAnimations
import com.himanshoe.kalendar.theme.KalendarTheme
import kotlinx.datetime.LocalDate
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Keyboard navigation and focus for the paged grid views, driven by real key events.
 *
 * Every assertion about *where* focus is goes through the roving focus ring's bounds rather than
 * through a semantics description, because the ring is the thing a sighted keyboard user actually
 * sees. Comparing it against the cell's own bounds proves both halves at once: the state moved and
 * the indicator followed.
 *
 * The header is switched off throughout so the calendar has exactly one focusable node, which is
 * what makes the Tab-in/Tab-out assertions unambiguous.
 */
@OptIn(ExperimentalTestApi::class)
class KalendarKeyboardNavigationUiTest {

    @get:Rule
    val rule = createComposeRule()

    /** March 2026 starts on a Sunday, so the Monday-first grid opens with six padding cells. */
    private val march10 = march(TENTH)

    private val chromeless = KalendarViewConfig(
        showNavigationArrows = false,
        showTodayButton = false,
        showJumpPicker = false,
    )

    private val before = FocusRequester()
    private var afterFocused = false
    private var beforeFocused = false

    /**
     * Wraps [content] between two bare focusables, so Tab order either side of the calendar is
     * observable. Focus starts on the leading one; one Tab should therefore land on the grid and
     * the next should leave it entirely.
     */
    @Composable
    private fun Harness(content: @Composable () -> Unit) {
        Column {
            Box(
                modifier = Modifier
                    .size(PROBE_SIZE)
                    .focusRequester(before)
                    .onFocusChanged { beforeFocused = it.isFocused }
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

    private fun setMonth(
        selectedDate: LocalDate = march10,
        config: KalendarViewConfig = chromeless,
        state: KalendarViewState? = null,
        onDateClick: (LocalDate, List<KalendarEvent>) -> Unit = { _, _ -> },
        onDateRangeSelect: ((LocalDate, LocalDate) -> Unit)? = null,
        onDateRangeSelectEnd: (() -> Unit)? = null,
    ) {
        rule.setContent {
            KalendarTheme(animations = KalendarAnimations(enabled = false)) {
                Harness {
                    KalendarMonth(
                        selectedDate = selectedDate,
                        state = state ?: rememberKalendarMonthState(selectedDate),
                        config = config,
                        onDateClick = onDateClick,
                        onDateRangeSelect = onDateRangeSelect,
                        onDateRangeSelectEnd = onDateRangeSelectEnd,
                    )
                }
            }
        }
        rule.runOnIdle { before.requestFocus() }
        rule.waitForIdle()
    }

    private fun tab() {
        rule.onRoot().performKeyInput { pressKey(Key.Tab) }
        rule.waitForIdle()
    }

    private fun press(key: Key) {
        rule.onRoot().performKeyInput { pressKey(key) }
        rule.waitForIdle()
    }

    private fun shiftPress(key: Key) {
        rule.onRoot().performKeyInput { withKeyDown(Key.ShiftLeft) { pressKey(key) } }
        rule.waitForIdle()
    }

    /** The day cell for [date] — matched by its own announcement, and by the click a cell carries. */
    private fun cell(date: String): SemanticsNodeInteraction =
        rule.onNode(hasContentDescription(date) and hasClickAction())

    private fun ringBounds(): Rect =
        rule.onNodeWithTag(KEYBOARD_FOCUS_RING_TAG).fetchSemanticsNode().boundsInRoot

    private fun assertRingOn(date: String) {
        val ring = ringBounds()
        val target = cell(date).fetchSemanticsNode().boundsInRoot
        assertTrue(
            ring.overlaps(target),
            "focus ring at $ring should sit over the cell for $date at $target",
        )
    }

    @Test
    fun tab_entersTheGridOnceAndLeavesItOnce() {
        setMonth()

        assertTrue(ringBounds().width == 0f, "the ring should be invisible before the grid is focused")

        tab()
        assertTrue(ringBounds().width > 0f, "one Tab should put focus in the grid")
        assertEquals(false, afterFocused, "Tab reached past the grid on the first press")

        tab()
        assertTrue(afterFocused, "a second Tab should leave the grid, not step to the next day cell")
        assertTrue(ringBounds().width == 0f, "the ring should disappear once the grid loses focus")
    }

    @Test
    fun tabEntersAtTheSelectedDate() {
        setMonth()
        tab()

        assertRingOn("March 10, 2026")
    }

    /**
     * The grid announces the focused date, so a screen reader says where focus went. The assertion
     * looks for a *non-clickable* node carrying the date's description: the day cell itself carries
     * the same words and is clickable, so this distinguishes the grid's live announcement from it.
     */
    @Test
    fun movingFocusAnnouncesTheNewlyFocusedDate() {
        setMonth()
        tab()

        rule.onNode(hasContentDescription("March 10, 2026") and !hasClickAction()).assertExists()
        rule.onNode(hasContentDescription("March 11, 2026") and !hasClickAction()).assertDoesNotExist()

        press(Key.DirectionRight)

        rule.onNode(hasContentDescription("March 11, 2026") and !hasClickAction()).assertExists()
        rule.onNode(hasContentDescription("March 10, 2026") and !hasClickAction()).assertDoesNotExist()
    }

    @Test
    fun arrowKeysMoveFocusOneDayAndOneRow() {
        setMonth()
        tab()

        press(Key.DirectionRight)
        assertRingOn("March 11, 2026")

        press(Key.DirectionLeft)
        assertRingOn("March 10, 2026")

        press(Key.DirectionDown)
        assertRingOn("March 17, 2026")

        press(Key.DirectionUp)
        assertRingOn("March 10, 2026")
    }

    /** March 15th 2026 is a Sunday: the last cell of a Monday-first week row. */
    @Test
    fun rightArrowWrapsFromTheEndOfOneWeekToTheStartOfTheNext() {
        setMonth(selectedDate = march(FIFTEENTH))
        tab()

        press(Key.DirectionRight)

        assertRingOn("March 16, 2026")
    }

    @Test
    fun enterSelectsTheFocusedDate() {
        var clicked: LocalDate? = null
        setMonth(onDateClick = { date, _ -> clicked = date })
        tab()

        press(Key.DirectionRight)
        press(Key.Enter)

        assertEquals(march(ELEVENTH), clicked)
    }

    @Test
    fun spaceSelectsTheFocusedDate() {
        var clicked: LocalDate? = null
        setMonth(onDateClick = { date, _ -> clicked = date })
        tab()

        press(Key.Spacebar)

        assertEquals(march10, clicked)
    }

    @Test
    fun disabledDatesAreFocusableButNotSelectable() {
        val disabled = march(ELEVENTH)
        var clicked: LocalDate? = null
        setMonth(
            config = chromeless.copy(disabledDates = { it == disabled }),
            onDateClick = { date, _ -> clicked = date },
        )
        tab()

        press(Key.DirectionRight)
        assertRingOn("March 11, 2026")

        press(Key.Enter)
        assertNull(clicked, "Enter on a disabled date must not select it")

        press(Key.DirectionRight)
        press(Key.Enter)
        assertEquals(
            march(TWELFTH),
            clicked,
            "focus should have moved through the disabled date rather than stopping on it",
        )
    }

    @Test
    fun shiftArrowExtendsARangeAndReleasingShiftEndsIt() {
        val reported = mutableListOf<Pair<LocalDate, LocalDate>>()
        var ended = 0
        setMonth(
            onDateRangeSelect = { start, current -> reported += start to current },
            onDateRangeSelectEnd = { ended += 1 },
        )
        tab()

        rule.onRoot().performKeyInput {
            withKeyDown(Key.ShiftLeft) {
                pressKey(Key.DirectionRight)
                pressKey(Key.DirectionRight)
            }
        }
        rule.waitForIdle()

        assertEquals(
            listOf(
                march10 to march(ELEVENTH),
                march10 to march(TWELFTH),
            ),
            reported,
            "both extensions should report the same anchor",
        )
        assertEquals(1, ended, "releasing shift should end the range exactly once")
        assertRingOn("March 12, 2026")
    }

    @Test
    fun shiftArrowAfterAnEndedRangeStartsAFreshAnchor() {
        val reported = mutableListOf<Pair<LocalDate, LocalDate>>()
        setMonth(
            onDateRangeSelect = { start, current -> reported += start to current },
            onDateRangeSelectEnd = {},
        )
        tab()

        shiftPress(Key.DirectionRight)
        shiftPress(Key.DirectionRight)

        assertEquals(
            listOf(
                march10 to march(ELEVENTH),
                march(ELEVENTH) to march(TWELFTH),
            ),
            reported,
            "a released shift closes the range, so the next extension anchors where focus now is",
        )
    }

    @Test
    fun homeAndEndJumpToTheEdgesOfTheVisibleMonth() {
        setMonth()
        tab()

        press(Key.MoveHome)
        assertRingOn("March 1, 2026")

        press(Key.MoveEnd)
        assertRingOn("March 31, 2026")
    }

    /**
     * Composes a month view whose [KalendarViewState] the test keeps, focuses the grid, and returns
     * a reader for the month currently paged into view.
     */
    private fun setMonthReadingVisibleMonth(initialDate: LocalDate): () -> Int {
        var captured: KalendarViewState? = null
        rule.setContent {
            KalendarTheme(animations = KalendarAnimations(enabled = false)) {
                val state = rememberKalendarMonthState(initialDate)
                captured = state
                Harness {
                    KalendarMonth(selectedDate = initialDate, state = state, config = chromeless)
                }
            }
        }
        rule.runOnIdle { before.requestFocus() }
        rule.waitForIdle()
        tab()
        return { rule.runOnIdle { checkNotNull(captured).visibleDate.monthNumber } }
    }

    @Test
    fun rightArrowPastTheEndOfTheMonthPagesForward() {
        val visibleMonth = setMonthReadingVisibleMonth(march(THIRTY_FIRST))

        press(Key.DirectionRight)

        assertEquals(APRIL, visibleMonth(), "the pager should have moved to April")
        assertRingOn("April 1, 2026")
    }

    @Test
    fun pageDownAndPageUpMoveOneMonth() {
        val visibleMonth = setMonthReadingVisibleMonth(march10)

        press(Key.PageDown)
        assertEquals(APRIL, visibleMonth())
        assertRingOn("April 10, 2026")

        press(Key.PageUp)
        assertEquals(FIXTURE_MONTH, visibleMonth())
        assertRingOn("March 10, 2026")
    }

    @Test
    fun weekViewIsAlsoASingleTabStopAndArrowsMoveWithinIt() {
        var clicked: LocalDate? = null
        rule.setContent {
            KalendarTheme(animations = KalendarAnimations(enabled = false)) {
                Harness {
                    KalendarWeek(
                        selectedDate = march10,
                        config = chromeless,
                        onDateClick = { date, _ -> clicked = date },
                    )
                }
            }
        }
        rule.runOnIdle { before.requestFocus() }
        rule.waitForIdle()

        tab()
        assertTrue(ringBounds().width > 0f, "one Tab should put focus in the week row")

        press(Key.DirectionRight)
        press(Key.Enter)
        assertEquals(march(ELEVENTH), clicked)

        tab()
        assertTrue(afterFocused, "a second Tab should leave the week row")
    }

    private fun march(day: Int): LocalDate = LocalDate(FIXTURE_YEAR, FIXTURE_MONTH, day)

    private companion object {
        const val FIXTURE_YEAR = 2026
        const val FIXTURE_MONTH = 3
        const val APRIL = 4
        const val ELEVENTH = 11
        const val TWELFTH = 12
        const val FIFTEENTH = 15
        const val TENTH = 10
        const val THIRTY_FIRST = 31
        val PROBE_SIZE = 8.dp

        /**
         * Big enough for all six rows of a month grid to be laid out. The grid is a
         * `LazyVerticalGrid`: a row past the bottom of the viewport is never composed, so a cell
         * outside it has no bounds for the focus ring to be compared against.
         */
        val CALENDAR_WIDTH = 420.dp
        val CALENDAR_HEIGHT = 480.dp
    }
}
