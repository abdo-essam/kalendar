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

package com.himanshoe.kalendar.foundation.event

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KalendarEventsByDateTest {

    @Test
    fun byDateExpandingSpans_singleDayEvent_appearsOnlyOnItsDate() {
        val event = BasicKalendarEvent(date = LocalDate(2026, 8, 12), eventName = "Standup")
        val byDate = listOf(event).byDateExpandingSpans()
        assertEquals(setOf(LocalDate(2026, 8, 12)), byDate.keys)
        assertEquals(listOf(event), byDate.getValue(LocalDate(2026, 8, 12)))
    }

    @Test
    fun byDateExpandingSpans_multiDayEvent_appearsOnEveryDateInclusive() {
        val event = BasicKalendarEvent(
            date = LocalDate(2026, 8, 12),
            eventName = "Conference",
            endDate = LocalDate(2026, 8, 14),
        )
        val byDate = listOf(event).byDateExpandingSpans()
        assertEquals(
            setOf(LocalDate(2026, 8, 12), LocalDate(2026, 8, 13), LocalDate(2026, 8, 14)),
            byDate.keys,
        )
        assertTrue(byDate.values.all { it == listOf(event) })
    }

    @Test
    fun byDateExpandingSpans_endDateBeforeDate_treatedAsSingleDay() {
        val event = BasicKalendarEvent(
            date = LocalDate(2026, 8, 12),
            eventName = "Malformed",
            endDate = LocalDate(2026, 8, 10),
        )
        val byDate = listOf(event).byDateExpandingSpans()
        assertEquals(setOf(LocalDate(2026, 8, 12)), byDate.keys)
    }

    @Test
    fun byDateExpandingSpans_multipleEventsSameDate_bothPresent() {
        val first = BasicKalendarEvent(date = LocalDate(2026, 8, 12), eventName = "Standup")
        val second = BasicKalendarEvent(date = LocalDate(2026, 8, 12), eventName = "Lunch")
        val byDate = listOf(first, second).byDateExpandingSpans()
        assertEquals(listOf(first, second), byDate.getValue(LocalDate(2026, 8, 12)))
    }

    @Test
    fun byDateExpandingSpans_excessiveEndDate_isCapped() {
        val event = BasicKalendarEvent(
            date = LocalDate(2026, 8, 12),
            eventName = "Runaway",
            endDate = LocalDate(2099, 1, 1),
        )
        val byDate = listOf(event).byDateExpandingSpans()
        assertEquals(367, byDate.size)
    }
}
