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

import androidx.compose.ui.unit.dp
import com.himanshoe.kalendar.foundation.event.BasicKalendarEvent
import com.himanshoe.kalendar.foundation.event.KalendarEvent
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@Suppress("MagicNumber")
class KalendarResourceStateTest {

    private val date = LocalDate(2026, 3, 10)

    private val roomA = KalendarResource(id = "a", title = "Room A")
    private val roomB = KalendarResource(id = "b", title = "Room B")

    private fun booking(
        name: String,
        startHour: Int,
        endHour: Int,
        resourceId: String?,
    ): BasicKalendarEvent = BasicKalendarEvent(
        date = date,
        eventName = name,
        startTime = LocalDateTime(date.year, date.month, date.dayOfMonth, startHour, 0),
        endTime = LocalDateTime(date.year, date.month, date.dayOfMonth, endHour, 0),
        calendarId = resourceId,
    )

    private val resourceIdOf: (KalendarEvent) -> String? = { it.calendarId }

    @Test
    fun orderedResources_sortsByOrderKeepingListOrderForTies() {
        val ordered = orderedResources(
            listOf(
                KalendarResource(id = "c", title = "C", order = 2),
                KalendarResource(id = "a", title = "A"),
                KalendarResource(id = "b", title = "B"),
            ),
        )
        assertEquals(listOf("a", "b", "c"), ordered.map { it.id })
    }

    @Test
    fun resourceBlocks_bucketsEventsByLane() {
        val blocks = resourceBlocks(
            events = listOf(
                booking(name = "Standup", startHour = 9, endHour = 10, resourceId = "a"),
                booking(name = "Review", startHour = 14, endHour = 15, resourceId = "b"),
            ),
            resources = listOf(roomA, roomB),
            resourceIdOf = resourceIdOf,
            date = date,
        )
        assertEquals(listOf("Standup"), blocks.getValue("a").map { it.event.eventName })
        assertEquals(listOf("Review"), blocks.getValue("b").map { it.event.eventName })
    }

    @Test
    fun resourceBlocks_dropsEventsWithNoLaneOrAnUnknownLane() {
        val blocks = resourceBlocks(
            events = listOf(
                booking(name = "Unassigned", startHour = 9, endHour = 10, resourceId = null),
                booking(name = "Ghost room", startHour = 9, endHour = 10, resourceId = "nope"),
            ),
            resources = listOf(roomA, roomB),
            resourceIdOf = resourceIdOf,
            date = date,
        )
        assertTrue(blocks.isEmpty(), "events pointing at no known lane must not be laid out anywhere")
    }

    @Test
    fun resourceBlocks_packsOverlapsPerLaneNotAcrossLanes() {
        val blocks = resourceBlocks(
            events = listOf(
                booking(name = "A1", startHour = 9, endHour = 11, resourceId = "a"),
                booking(name = "A2", startHour = 10, endHour = 12, resourceId = "a"),
                booking(name = "B1", startHour = 9, endHour = 11, resourceId = "b"),
            ),
            resources = listOf(roomA, roomB),
            resourceIdOf = resourceIdOf,
            date = date,
        )
        assertEquals(listOf(2, 2), blocks.getValue("a").map { it.columns }, "clashing bookings split their lane")
        assertEquals(listOf(0, 1), blocks.getValue("a").map { it.column })
        assertEquals(listOf(1), blocks.getValue("b").map { it.columns }, "a clash in one lane must not narrow another")
    }

    @Test
    fun resourceShiftForDrag_dividesByTheColumnWidth() {
        assertEquals(1, resourceShiftForDrag(rawDeltaXPx = 110f, resourceColumnWidthPx = 100, resourceCount = 3))
        assertEquals(-2, resourceShiftForDrag(rawDeltaXPx = -190f, resourceColumnWidthPx = 100, resourceCount = 3))
        assertEquals(0, resourceShiftForDrag(rawDeltaXPx = 40f, resourceColumnWidthPx = 100, resourceCount = 3))
    }

    @Test
    fun resourceShiftForDrag_clampsToTheLanesThatExist() {
        assertEquals(2, resourceShiftForDrag(rawDeltaXPx = 900f, resourceColumnWidthPx = 100, resourceCount = 3))
        assertEquals(0, resourceShiftForDrag(rawDeltaXPx = 900f, resourceColumnWidthPx = 100, resourceCount = 1))
    }

    @Test
    fun resourceShiftForDrag_withNothingMeasuredYetDoesNotDivideByZero() {
        assertEquals(0, resourceShiftForDrag(rawDeltaXPx = 900f, resourceColumnWidthPx = 0, resourceCount = 3))
    }

    @Test
    fun shiftedResourceIndex_clampsToTheEndsOfTheList() {
        assertEquals(2, shiftedResourceIndex(index = 1, shift = 1, resourceCount = 3))
        assertEquals(0, shiftedResourceIndex(index = 1, shift = -4, resourceCount = 3))
        assertEquals(2, shiftedResourceIndex(index = 1, shift = 4, resourceCount = 3))
    }

    @Test
    fun resourceColumnWidth_dividesTheSpaceLeftAfterTheGutter() {
        val width = resourceColumnWidth(
            availableWidth = 448.dp,
            hourGutterWidth = 48.dp,
            resourceCount = 4,
            minColumnWidth = 96.dp,
        )
        assertEquals(100.dp, width)
    }

    @Test
    fun resourceColumnWidth_stopsAtTheMinimumSoTheGridScrollsInstead() {
        val width = resourceColumnWidth(
            availableWidth = 448.dp,
            hourGutterWidth = 48.dp,
            resourceCount = 10,
            minColumnWidth = 96.dp,
        )
        assertEquals(96.dp, width)
    }

    @Test
    fun resourceColumnWidth_withNoSpaceOrNoLanesFallsBackToTheMinimum() {
        assertEquals(
            96.dp,
            resourceColumnWidth(
                availableWidth = 20.dp,
                hourGutterWidth = 48.dp,
                resourceCount = 2,
                minColumnWidth = 96.dp,
            ),
        )
        assertEquals(
            96.dp,
            resourceColumnWidth(
                availableWidth = 448.dp,
                hourGutterWidth = 48.dp,
                resourceCount = 0,
                minColumnWidth = 96.dp,
            ),
        )
    }
}
