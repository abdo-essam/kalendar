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

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.himanshoe.kalendar.foundation.event.BasicKalendarEvent
import com.himanshoe.kalendar.foundation.event.KalendarEvent
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.plus
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

@Suppress("MagicNumber")
class KalendarAgendaUiTest {

    @get:Rule
    val rule = createComposeRule()

    private val day = LocalDate(2026, 3, 10)

    /**
     * The regression the 1.x Agenda died on: its list keyed items by
     * `"date_eventName_startTime"`, so two all-day events sharing a name on one date produced
     * duplicate keys and `SaveableStateHolder` threw. The stable sort put the colliding pair next
     * to each other, so it fired every time.
     */
    @Test
    fun agenda_twoAllDayEventsWithSameNameOnOneDate_rendersWithoutThrowing() {
        val duplicates = listOf(
            BasicKalendarEvent(date = day, eventName = "Public holiday"),
            BasicKalendarEvent(date = day, eventName = "Public holiday"),
        )
        rule.setContent {
            KalendarAgenda(events = duplicates)
        }
        assertEquals(2, rule.onAllNodesWithText("Public holiday").fetchSemanticsNodes().size)
    }

    @Test
    fun agenda_sameEventIdOnTwoDates_rendersBothOccurrences() {
        val vacation = BasicKalendarEvent(
            date = day,
            eventName = "Vacation",
            endDate = day.plus(1, DateTimeUnit.DAY),
            id = "vacation-1",
        )
        rule.setContent {
            KalendarAgenda(events = listOf(vacation))
        }
        assertEquals(2, rule.onAllNodesWithText("Vacation").fetchSemanticsNodes().size)
    }

    @Test
    fun agenda_emptyEvents_showsEmptyState() {
        rule.setContent {
            KalendarAgenda(events = emptyList())
        }
        rule.onNodeWithText("No events").assertExists()
    }

    @Test
    fun agenda_tapEventRow_firesOnEventClick() {
        var clicked: KalendarEvent? = null
        val standup = BasicKalendarEvent(
            date = day,
            eventName = "Standup",
            startTime = LocalDateTime(2026, 3, 10, 9, 0),
        )
        rule.setContent {
            KalendarAgenda(events = listOf(standup), onEventClick = { clicked = it })
        }
        rule.onNodeWithText("Standup").performClick()
        assertEquals("Standup", clicked?.eventName)
    }

    @Test
    fun agenda_dateHeader_usesMonthNameFormatterFromConfig() {
        rule.setContent {
            KalendarAgenda(
                events = listOf(BasicKalendarEvent(date = day, eventName = "Standup")),
                config = KalendarViewConfig(monthNameFormatter = { "mars" }),
            )
        }
        rule.onNodeWithText("10 mars 2026").assertExists()
    }

    @Test
    fun agenda_timedEvent_showsItsTimeRange() {
        val lunch = BasicKalendarEvent(
            date = day,
            eventName = "Lunch",
            startTime = LocalDateTime(2026, 3, 10, 12, 30),
            endTime = LocalDateTime(2026, 3, 10, 13, 5),
        )
        rule.setContent {
            KalendarAgenda(events = listOf(lunch))
        }
        rule.onNodeWithText("12:30 – 13:05").assertExists()
    }
}
