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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
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
import com.himanshoe.kalendar.state.KalendarTimelineState
import com.himanshoe.kalendar.state.rememberKalendarTimelineState
import com.himanshoe.kalendar.theme.KalendarAnimations
import com.himanshoe.kalendar.theme.KalendarTheme
import kotlinx.datetime.LocalDate
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Keyboard navigation for [KalendarTimeline], the one view with no pages at all.
 *
 * Two things separate it from the paged grids and both are tested here: the list has to follow the
 * ring rather than the other way round, and the month the ring sits on can be recycled out of the
 * `LazyColumn` entirely — after which focus has to still be where the user left it.
 *
 * `showAdjacentMonthDates` is off for the reason it is off in the year's tests: consecutive months
 * in one list would otherwise draw April 1st twice.
 */
@OptIn(ExperimentalTestApi::class)
class KalendarTimelineKeyboardUiTest {

    @get:Rule
    val rule = createComposeRule()

    private val march10 = march(TENTH)

    private val chromeless = KalendarViewConfig(
        showTodayButton = false,
        showAdjacentMonthDates = false,
    )

    private val before = FocusRequester()
    private var afterFocused = false

    /** A scroll target the test writes and the composition acts on, so scrolling is not a fling. */
    private val scrollTarget = mutableStateOf<LocalDate?>(null)

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

    /** Composes a timeline and returns a reader for the month currently topmost in the list. */
    private fun setTimeline(
        selectedDate: LocalDate = march10,
        config: KalendarViewConfig = chromeless,
        onDateClick: (LocalDate, List<KalendarEvent>) -> Unit = { _, _ -> },
    ): () -> LocalDate {
        var captured: KalendarTimelineState? = null
        rule.setContent {
            KalendarTheme(animations = KalendarAnimations(enabled = false)) {
                val state = rememberKalendarTimelineState(selectedDate)
                captured = state
                LaunchedEffect(scrollTarget.value) {
                    scrollTarget.value?.let { state.animateScrollTo(it) }
                }
                Harness {
                    KalendarTimeline(
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
        return { rule.runOnIdle { checkNotNull(captured).visibleDate } }
    }

    private fun tab() {
        rule.onRoot().performKeyInput { pressKey(Key.Tab) }
        rule.waitForIdle()
    }

    private fun press(key: Key) {
        rule.onRoot().performKeyInput { pressKey(key) }
        rule.waitForIdle()
    }

    private fun scrollTo(date: LocalDate) {
        rule.runOnIdle { scrollTarget.value = date }
        rule.waitForIdle()
    }

    private fun cell(date: String): SemanticsNodeInteraction =
        rule.onNode(hasContentDescription(date) and hasClickAction())

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
    fun tab_entersTheTimelineOnceAndLeavesItOnce() {
        setTimeline()

        assertEquals(0f, ringBounds().width, "the ring should be invisible before the timeline is focused")

        tab()
        assertTrue(ringBounds().width > 0f, "one Tab should put focus in the timeline")
        assertEquals(false, afterFocused, "Tab reached past the timeline on the first press")

        tab()
        assertTrue(afterFocused, "a second Tab should leave the timeline, not step to the next day cell")
        assertEquals(0f, ringBounds().width, "the ring should disappear once the timeline loses focus")
    }

    @Test
    fun arrowKeysMoveFocusOneDayAndOneRow() {
        setTimeline()
        tab()

        press(Key.DirectionRight)
        assertRingOn("March 11, 2026")

        press(Key.DirectionDown)
        assertRingOn("March 18, 2026")

        press(Key.DirectionUp)
        assertRingOn("March 11, 2026")

        press(Key.DirectionLeft)
        assertRingOn("March 10, 2026")
    }

    /** There is no page to turn, so the list itself has to bring the next month up. */
    @Test
    fun rightArrowPastTheEndOfAMonthScrollsTheNextMonthIntoView() {
        val visibleMonth = setTimeline(selectedDate = march(THIRTY_FIRST))
        tab()

        press(Key.DirectionRight)

        assertEquals(LocalDate(FIXTURE_YEAR, APRIL, 1), visibleMonth())
        assertRingOn("April 1, 2026")
    }

    @Test
    fun pageDownAndPageUpMoveOneMonthAndKeepTheRingVisible() {
        val visibleMonth = setTimeline()
        tab()

        press(Key.PageDown)
        assertEquals(LocalDate(FIXTURE_YEAR, APRIL, 1), visibleMonth())
        assertRingOn("April 10, 2026")

        press(Key.PageUp)
        assertEquals(LocalDate(FIXTURE_YEAR, MARCH, 1), visibleMonth())
        assertRingOn("March 10, 2026")
    }

    @Test
    fun homeAndEndJumpToTheEdgesOfTheMonthOnScreen() {
        setTimeline()
        tab()

        press(Key.MoveHome)
        assertRingOn("March 1, 2026")

        press(Key.MoveEnd)
        assertRingOn("March 31, 2026")
    }

    @Test
    fun enterSelectsTheFocusedDate() {
        var clicked: LocalDate? = null
        setTimeline(onDateClick = { date, _ -> clicked = date })
        tab()

        press(Key.DirectionRight)
        press(Key.Enter)

        assertEquals(march(ELEVENTH), clicked)
    }

    /**
     * The whole point of hoisting the focused date above the list: scroll far enough and the month
     * holding it is disposed, so there is no node left to carry the state. Coming back has to find
     * focus exactly where it was left, and the ring has to reappear on it.
     */
    @Test
    fun focusSurvivesTheFocusedMonthBeingRecycled() {
        setTimeline()
        tab()
        assertRingOn("March 10, 2026")

        scrollTo(LocalDate(FIXTURE_YEAR + 1, JUNE, 1))
        assertEquals(
            0f,
            ringBounds().width,
            "the ring should vanish with the recycled month rather than being drawn at a stale position",
        )

        press(Key.DirectionRight)

        assertRingOn("March 11, 2026")
    }

    @Test
    fun movingFocusAnnouncesTheNewlyFocusedDate() {
        setTimeline()
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
        const val APRIL = 4
        const val JUNE = 6
        const val TENTH = 10
        const val ELEVENTH = 11
        const val THIRTY_FIRST = 31
        val PROBE_SIZE = 8.dp

        val CALENDAR_WIDTH = 420.dp
        val CALENDAR_HEIGHT = 560.dp
    }
}
