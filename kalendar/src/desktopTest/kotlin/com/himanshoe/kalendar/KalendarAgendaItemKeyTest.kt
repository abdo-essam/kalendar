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
import androidx.compose.ui.test.onNodeWithText
import com.himanshoe.kalendar.foundation.event.BasicKalendarEvent
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import org.junit.Rule
import org.junit.Test

/**
 * [KalendarAgenda] keys its list items by event id, falling back to the item's index when an event
 * has none. Those two fallbacks have to occupy separate value spaces.
 */
class KalendarAgendaItemKeyTest {

    @get:Rule
    val rule = createComposeRule()

    @Suppress("MagicNumber")
    private val day = LocalDate(2026, 3, 10)

    /**
     * Regression: an event carrying `id = "1"` beside an id-less event sitting at index 1 produced
     * the same list key, because a null id fell back to the index in the very space the ids live
     * in. A `LazyColumn` throws on a duplicate key, so the whole agenda crashed rather than
     * degrading.
     *
     * The mix is ordinary rather than exotic — events synced from a device arrive with ids, locally
     * drafted ones do not — so any consumer doing both on one day hit it.
     */
    @Test
    fun agenda_dayMixingIdentifiedAndUnidentifiedEvents_rendersBoth() {
        rule.setContent {
            KalendarAgenda(
                events = listOf(
                    BasicKalendarEvent(
                        date = day,
                        eventName = "Synced, has an id",
                        startTime = LocalDateTime(2026, 3, 10, 9, 0),
                        id = "1",
                    ),
                    BasicKalendarEvent(
                        date = day,
                        eventName = "Local draft, no id",
                        startTime = LocalDateTime(2026, 3, 10, 11, 0),
                    ),
                ),
            )
        }
        rule.waitForIdle()

        rule.onNodeWithText("Synced, has an id").assertExists()
        rule.onNodeWithText("Local draft, no id").assertExists()
    }

    /** The same collision one step along: an id of "2" against an id-less event at index 2. */
    @Test
    fun agenda_identifierMatchingALaterIndex_stillRendersEveryEvent() {
        rule.setContent {
            KalendarAgenda(
                events = listOf(
                    BasicKalendarEvent(
                        date = day,
                        eventName = "First",
                        startTime = LocalDateTime(2026, 3, 10, 8, 0),
                    ),
                    BasicKalendarEvent(
                        date = day,
                        eventName = "Second",
                        startTime = LocalDateTime(2026, 3, 10, 9, 0),
                    ),
                    BasicKalendarEvent(
                        date = day,
                        eventName = "Third, id collides with index two",
                        startTime = LocalDateTime(2026, 3, 10, 10, 0),
                        id = "2",
                    ),
                ),
            )
        }
        rule.waitForIdle()

        rule.onNodeWithText("First").assertExists()
        rule.onNodeWithText("Second").assertExists()
        rule.onNodeWithText("Third, id collides with index two").assertExists()
    }
}
