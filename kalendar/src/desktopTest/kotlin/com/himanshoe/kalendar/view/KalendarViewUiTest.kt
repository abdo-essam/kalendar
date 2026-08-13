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

import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.himanshoe.kalendar.foundation.event.BasicKalendarEvent
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class KalendarViewUiTest {

    @get:Rule
    val rule = createComposeRule()

    @Suppress("MagicNumber")
    private val march = LocalDate(2026, 3, 10)

    @Test
    fun monthView_tapDayCell_firesOnDateClickWithThatDate() {
        var clicked: LocalDate? = null
        rule.setContent {
            KalendarMonth(
                selectedDate = march,
                onDateClick = { date, _ -> clicked = date },
            )
        }
        rule.onNodeWithContentDescription("March 15, 2026").performClick()
        assertEquals(LocalDate(2026, 3, 15), clicked)
    }

    @Test
    fun monthView_tapDisabledDate_doesNotFireOnDateClick() {
        var clicked: LocalDate? = null
        rule.setContent {
            KalendarMonth(
                selectedDate = march,
                onDateClick = { date, _ -> clicked = date },
                config = KalendarViewConfig(disabledDates = { it == LocalDate(2026, 3, 15) }),
            )
        }
        rule.onNodeWithContentDescription("March 15, 2026").performClick()
        assertNull(clicked)
    }

    @Test
    fun monthView_selectedDate_hasSelectedSemantics() {
        rule.setContent {
            KalendarMonth(selectedDate = march)
        }
        rule.onNodeWithContentDescription("March 10, 2026").assertIsSelected()
    }

    @Test
    fun monthView_dateWithEvents_describesThemForScreenReaders() {
        rule.setContent {
            KalendarMonth(
                selectedDate = march,
                events = listOf(BasicKalendarEvent(date = LocalDate(2026, 3, 12), eventName = "Standup")),
            )
        }
        rule.onNodeWithContentDescription("March 12, 2026, has events").assertExists()
    }

    @Test
    fun monthView_nextArrow_movesTitleToNextMonth() {
        rule.setContent {
            KalendarMonth(selectedDate = march)
        }
        rule.onNodeWithText("March 2026").assertExists()
        rule.onNodeWithContentDescription("Next").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("April 2026").assertExists()
    }

    @Test
    fun monthView_customMonthNameFormatter_reachesTitle() {
        rule.setContent {
            KalendarMonth(
                selectedDate = march,
                config = KalendarViewConfig(monthNameFormatter = { "Mois-${it.ordinal + 1}" }),
            )
        }
        rule.onNodeWithText("Mois-3 2026").assertExists()
    }

    @Test
    fun scheduleView_rendersTimedEventBlock() {
        rule.setContent {
            KalendarSchedule(
                selectedDate = march,
                events = listOf(
                    BasicKalendarEvent(
                        date = march,
                        eventName = "Design review",
                        startTime = LocalDateTime(2026, 3, 10, 9, 0),
                        endTime = LocalDateTime(2026, 3, 10, 10, 0),
                    ),
                ),
            )
        }
        rule.onNodeWithText("Design review").assertExists()
    }

    @Test
    fun weekView_tapDayCell_firesOnDateClick() {
        var clicked: LocalDate? = null
        rule.setContent {
            KalendarWeek(
                selectedDate = march,
                onDateClick = { date, _ -> clicked = date },
            )
        }
        rule.onNodeWithContentDescription("March 11, 2026").performClick()
        assertEquals(LocalDate(2026, 3, 11), clicked)
    }
}
