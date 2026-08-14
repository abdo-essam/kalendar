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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.himanshoe.kalendar.foundation.event.BasicKalendarEvent
import com.himanshoe.kalendar.foundation.event.KalendarEvent
import kotlinx.datetime.LocalDate
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
    }
}
