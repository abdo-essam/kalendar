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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.dp
import com.himanshoe.kalendar.component.KalendarDayCellDefaults
import com.himanshoe.kalendar.foundation.event.BasicKalendarEvent
import com.himanshoe.kalendar.foundation.event.KalendarEvent
import com.himanshoe.kalendar.slot.KalendarDayOverflowScope
import com.himanshoe.kalendar.slot.KalendarDayScope
import com.himanshoe.kalendar.theme.KalendarAnimations
import com.himanshoe.kalendar.theme.KalendarTheme
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Behaviour of the day cell's `+N` overflow trigger and the popover it opens.
 *
 * Animations are switched off throughout so the indicator row is present on the first frame rather
 * than fading in, which is what lets a click land on the trigger deterministically.
 */
class KalendarDayCellOverflowUiTest {

    @get:Rule
    val rule = createComposeRule()

    @Suppress("MagicNumber")
    private val date = LocalDate(2026, 3, 12)

    private val fourEvents = listOf(
        BasicKalendarEvent(date = date, eventName = "Alpha"),
        BasicKalendarEvent(date = date, eventName = "Bravo"),
        BasicKalendarEvent(date = date, eventName = "Charlie"),
        BasicKalendarEvent(date = date, eventName = "Delta"),
    )

    private fun scope(events: List<KalendarEvent>) = KalendarDayScope(
        date = date,
        isSelected = false,
        isToday = false,
        isDisabled = false,
        events = events,
    )

    private fun setCell(
        events: List<KalendarEvent> = fourEvents,
        onClick: () -> Unit = {},
        onEventClick: (KalendarEvent) -> Unit = {},
        overflowPopup: (@Composable (KalendarDayOverflowScope) -> Unit)? = null,
    ) {
        rule.setContent {
            KalendarTheme(animations = KalendarAnimations(enabled = false)) {
                if (overflowPopup == null) {
                    KalendarDayCellDefaults.Cell(
                        scope = scope(events),
                        onClick = onClick,
                        onEventClick = onEventClick,
                    )
                } else {
                    KalendarDayCellDefaults.Cell(
                        scope = scope(events),
                        onClick = onClick,
                        onEventClick = onEventClick,
                        overflowPopup = overflowPopup,
                    )
                }
            }
        }
    }

    @Test
    fun overflowLabel_tapped_opensPopupListingEveryEventForTheDay() {
        setCell()

        rule.onNodeWithText("Alpha").assertDoesNotExist()
        rule.onNodeWithContentDescription(OVERFLOW_TRIGGER_DESCRIPTION).performClick()

        listOf("Alpha", "Bravo", "Charlie", "Delta").forEach { name ->
            rule.onNodeWithText(name).assertIsDisplayed()
        }
    }

    @Test
    fun overflowPopup_eventRowTapped_reportsThatEventAndCloses() {
        var clicked: KalendarEvent? = null
        setCell(onEventClick = { clicked = it })

        rule.onNodeWithContentDescription(OVERFLOW_TRIGGER_DESCRIPTION).performClick()
        rule.onNodeWithText("Charlie").performClick()

        assertEquals("Charlie", clicked?.eventName)
        rule.onNodeWithText("Charlie").assertDoesNotExist()
    }

    @Test
    fun overflowPopup_dismissed_closesWithoutReportingAnEvent() {
        var clicked: KalendarEvent? = null
        setCell(
            onEventClick = { clicked = it },
            overflowPopup = { overflowScope ->
                BasicText(
                    text = "Close",
                    modifier = Modifier.clickable { overflowScope.dismiss() },
                )
            },
        )

        rule.onNodeWithContentDescription(OVERFLOW_TRIGGER_DESCRIPTION).performClick()
        rule.onNodeWithText("Close").assertIsDisplayed()
        rule.onNodeWithText("Close").performClick()

        rule.onNodeWithText("Close").assertDoesNotExist()
        assertNull(clicked)
    }

    @Test
    fun overflowPopup_slotReceivesEveryEventAndTheHiddenCount() {
        var seenEvents: List<KalendarEvent>? = null
        var seenHidden: Int? = null
        setCell(
            overflowPopup = { overflowScope ->
                seenEvents = overflowScope.events
                seenHidden = overflowScope.hiddenEventCount
                BasicText(text = "slot")
            },
        )

        rule.onNodeWithContentDescription(OVERFLOW_TRIGGER_DESCRIPTION).performClick()
        rule.onNodeWithText("slot").assertIsDisplayed()

        assertEquals(fourEvents, seenEvents)
        assertEquals(1, seenHidden)
    }

    @Test
    fun overflowLabel_tapped_doesNotSelectTheDay() {
        var dayClicked = false
        setCell(onClick = { dayClicked = true })

        rule.onNodeWithContentDescription(OVERFLOW_TRIGGER_DESCRIPTION).performClick()

        assertEquals(false, dayClicked)
    }

    @Test
    fun cellWithoutOverflow_hasNoTriggerAndStillFiresOnDateClick() {
        var dayClicked = false
        setCell(events = fourEvents.take(2), onClick = { dayClicked = true })

        rule.onNodeWithContentDescription(OVERFLOW_TRIGGER_DESCRIPTION).assertDoesNotExist()
        rule.onNodeWithContentDescription(DAY_DESCRIPTION).performClick()

        assertEquals(true, dayClicked)
    }

    @Test
    fun cellWithOverflow_tappedAnywhereElse_stillFiresOnDateClick() {
        var dayClicked = false
        setCell(onClick = { dayClicked = true })

        rule.onNodeWithContentDescription(DAY_DESCRIPTION).performClick()

        assertEquals(true, dayClicked)
        rule.onNodeWithText("Alpha").assertDoesNotExist()
    }

    /**
     * Opens the first `+N` trigger on screen and taps "Charlie" in the popover it shows, returning
     * the event the view reported.
     *
     * The `onAllNodes...[0]` is deliberate: [KalendarYear] and [KalendarTimeline] render the same
     * date on more than one month grid, so more than one trigger can exist. Any of them opens a
     * popover listing the same day.
     *
     * [scrollingListTag] is for the views whose months stack in a **lazy** scrolling list. There the
     * trigger has no node at all until its month composes, so the list itself is scrolled until the
     * trigger appears; asking the trigger to scroll itself into view would fail on a node that does
     * not exist yet.
     */
    private fun tapPopupRow(
        scrollingListTag: String? = null,
        view: @Composable ((KalendarEvent) -> Unit) -> Unit,
    ): KalendarEvent? {
        var clicked: KalendarEvent? = null
        rule.setContent {
            KalendarTheme(animations = KalendarAnimations(enabled = false)) {
                view { clicked = it }
            }
        }
        if (scrollingListTag != null) {
            rule.onNodeWithTag(scrollingListTag)
                .performScrollToNode(hasContentDescription(OVERFLOW_TRIGGER_DESCRIPTION))
        }
        rule.onAllNodesWithContentDescription(OVERFLOW_TRIGGER_DESCRIPTION)[0].performClick()
        rule.onAllNodesWithText("Charlie")[0].performClick()
        return clicked
    }

    @Test
    fun monthView_overflowPopupRowTapped_reachesOnDayEventClick() {
        val clicked = tapPopupRow { onEvent ->
            KalendarMonth(selectedDate = date, events = fourEvents, onDayEventClick = onEvent)
        }
        assertEquals("Charlie", clicked?.eventName)
    }

    @Test
    fun weekView_overflowPopupRowTapped_reachesOnDayEventClick() {
        val clicked = tapPopupRow { onEvent ->
            KalendarWeek(selectedDate = date, events = fourEvents, onDayEventClick = onEvent)
        }
        assertEquals("Charlie", clicked?.eventName)
    }

    @Test
    fun yearView_overflowPopupRowTapped_reachesOnDayEventClick() {
        val clicked = tapPopupRow(scrollingListTag = YEAR_MONTH_LIST_TAG) { onEvent ->
            KalendarYear(selectedDate = date, events = fourEvents, onDayEventClick = onEvent)
        }
        assertEquals("Charlie", clicked?.eventName)
    }

    @Test
    fun timelineView_overflowPopupRowTapped_reachesOnDayEventClick() {
        val clicked = tapPopupRow { onEvent ->
            KalendarTimeline(selectedDate = date, events = fourEvents, onDayEventClick = onEvent)
        }
        assertEquals("Charlie", clicked?.eventName)
    }

    /**
     * The other half of the day cell's lane-budget bug: an event the cell had no room to draw as a
     * span bar was not counted into `+N` either, so a cell could hold four events, show three, and
     * admit to none of them.
     *
     * [NO_LANE_MONTH_WIDTH] leaves a square cell tall enough for the dot strip and too short for
     * even one span lane above it, so the conference falls back to a dot — a fourth dot on a day
     * whose budget is three. The event count is the same one
     * [KalendarSpanBarUiTest.aBarDoesNotSpendADotSlot] renders at full width, where the bar *is*
     * drawn and there is deliberately no label; the two together say the label follows what the cell
     * actually drew rather than what the grid hoped it would.
     */
    @Test
    fun monthView_aSpanTheCellCannotBar_isCountedInTheOverflowLabel() {
        val events = listOf(
            BasicKalendarEvent(date = date, endDate = date.plus(SPAN_DAYS, DateTimeUnit.DAY), eventName = "Conference"),
        ) + fourEvents.take(DEFAULT_DOT_BUDGET)
        rule.setContent {
            KalendarTheme(animations = KalendarAnimations(enabled = false)) {
                Box(modifier = Modifier.width(NO_LANE_MONTH_WIDTH)) {
                    KalendarMonth(selectedDate = date.plus(SELECTED_DAY_OFFSET, DateTimeUnit.DAY), events = events)
                }
            }
        }

        rule.onNodeWithText(ONE_HIDDEN_LABEL).assertIsDisplayed()
    }

    @Test
    fun monthView_overflowLabel_opensPopupOverTheGrid() {
        rule.setContent {
            KalendarTheme(animations = KalendarAnimations(enabled = false)) {
                KalendarMonth(selectedDate = date, events = fourEvents)
            }
        }

        rule.onNodeWithContentDescription(OVERFLOW_TRIGGER_DESCRIPTION).performClick()

        rule.onNodeWithText("Delta").assertIsDisplayed()
    }

    private companion object {
        const val OVERFLOW_TRIGGER_DESCRIPTION = "Show all events"

        /** The day cell's own announcement — the date plus [KalendarStrings.hasEventsSuffix]. */
        const val DAY_DESCRIPTION = "March 12, 2026, has events"

        const val ONE_HIDDEN_LABEL = "+1"

        /** [com.himanshoe.kalendar.component.KalendarDayCellDefaults.Cell]'s default dot budget. */
        const val DEFAULT_DOT_BUDGET = 3

        /**
         * Seven columns of a cell too short for a span lane: the strip fits, one 6dp lane above it
         * does not. A cell is square, so its height is this over seven less the day-cell padding.
         */
        val NO_LANE_MONTH_WIDTH = 280.dp

        /** Long enough to be a span, short enough to stay inside one week of the fixture month. */
        const val SPAN_DAYS = 2

        /** Selects a day well clear of the span, so no cell under test draws in selection colours. */
        const val SELECTED_DAY_OFFSET = 12
    }
}
