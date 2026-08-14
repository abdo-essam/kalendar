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

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Dp
import com.himanshoe.kalendar.foundation.event.BasicKalendarEvent
import com.himanshoe.kalendar.foundation.event.KalendarEvent
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@Suppress("MagicNumber")
class KalendarResourceViewUiTest {

    @get:Rule
    val rule = createComposeRule()

    private val tuesday = LocalDate(2026, 3, 10)

    private val rooms = listOf(
        KalendarResource(id = "room-a", title = "Room A"),
        KalendarResource(id = "room-b", title = "Room B"),
    )

    private fun booking(
        name: String,
        startHour: Int,
        endHour: Int,
        resourceId: String,
    ): BasicKalendarEvent = BasicKalendarEvent(
        date = tuesday,
        eventName = name,
        startTime = LocalDateTime(tuesday.year, tuesday.month, tuesday.day, startHour, 0),
        endTime = LocalDateTime(tuesday.year, tuesday.month, tuesday.day, endHour, 0),
        calendarId = resourceId,
    )

    private fun centerX(left: Dp, right: Dp): Dp = (left + right) / 2f

    @Test
    fun resourceGrid_rendersEveryLaneHeaderAndItsBookings() {
        rule.setContent {
            KalendarResourceView(
                resources = rooms,
                state = rememberKalendarScheduleState(initialDate = tuesday),
                events = listOf(
                    booking(name = "Standup", startHour = 9, endHour = 10, resourceId = "room-a"),
                    booking(name = "Review", startHour = 10, endHour = 11, resourceId = "room-b"),
                ),
            )
        }
        rule.onNodeWithText("Room A").assertExists()
        rule.onNodeWithText("Room B").assertExists()
        rule.onNodeWithText("Standup").assertExists()
        rule.onNodeWithText("Review").assertExists()
    }

    @Test
    fun resourceGrid_blocksLandInTheColumnOfTheirOwnResource() {
        rule.setContent {
            KalendarResourceView(
                resources = rooms,
                state = rememberKalendarScheduleState(initialDate = tuesday),
                events = listOf(
                    booking(name = "Standup", startHour = 9, endHour = 10, resourceId = "room-a"),
                    booking(name = "Review", startHour = 9, endHour = 10, resourceId = "room-b"),
                ),
            )
        }
        val laneA = rule.onNodeWithText("Room A").getUnclippedBoundsInRoot()
        val laneB = rule.onNodeWithText("Room B").getUnclippedBoundsInRoot()
        val standup = rule.onNodeWithText("Standup").getUnclippedBoundsInRoot()
        val review = rule.onNodeWithText("Review").getUnclippedBoundsInRoot()

        assertTrue(
            standup.left <= centerX(laneA.left, laneA.right) && centerX(laneA.left, laneA.right) <= standup.right,
            "the room-a booking must sit under the Room A header, was $standup against $laneA",
        )
        assertTrue(
            review.left <= centerX(laneB.left, laneB.right) && centerX(laneB.left, laneB.right) <= review.right,
            "the room-b booking must sit under the Room B header, was $review against $laneB",
        )
        assertTrue(standup.right <= review.left, "lane columns must not overlap, was $standup and $review")
    }

    @Test
    fun resourceGrid_clashingBookingsSplitTheirOwnLaneOnly() {
        rule.setContent {
            KalendarResourceView(
                resources = rooms,
                state = rememberKalendarScheduleState(initialDate = tuesday),
                events = listOf(
                    booking(name = "Early", startHour = 9, endHour = 11, resourceId = "room-a"),
                    booking(name = "Late", startHour = 10, endHour = 12, resourceId = "room-a"),
                    booking(name = "Alone", startHour = 9, endHour = 11, resourceId = "room-b"),
                ),
            )
        }
        val early = rule.onNodeWithText("Early").getUnclippedBoundsInRoot()
        val late = rule.onNodeWithText("Late").getUnclippedBoundsInRoot()
        val alone = rule.onNodeWithText("Alone").getUnclippedBoundsInRoot()

        assertTrue(early.right <= late.left, "clashing bookings share their lane side by side")
        assertTrue(late.right <= alone.left, "the split must stay inside room-a's column")
        val aloneWidth = alone.right - alone.left
        val earlyWidth = early.right - early.left
        assertTrue(
            aloneWidth > earlyWidth,
            "a clash in one lane must not narrow another, was $aloneWidth against $earlyWidth",
        )
    }

    @Test
    fun resourceGrid_longPressDragSideways_movesTheBookingToTheNextResource() {
        var reportedResourceId: String? = null
        var reportedStart: LocalDateTime? = null
        var reportedEnd: LocalDateTime? = null
        rule.setContent {
            KalendarResourceView(
                resources = rooms,
                state = rememberKalendarScheduleState(initialDate = tuesday),
                events = listOf(booking(name = "Sync", startHour = 9, endHour = 10, resourceId = "room-a")),
                onEventChange = { _, newStart, newEnd, newResourceId ->
                    reportedStart = newStart
                    reportedEnd = newEnd
                    reportedResourceId = newResourceId
                },
            )
        }
        // The lone block fills its lane column, so its own width approximates the column width;
        // dragging 1.1x that distance rounds to a one-lane shift regardless of density.
        rule.onNodeWithText("Sync").performTouchInput {
            down(center)
            advanceEventTime(LONG_PRESS_MS)
            moveBy(Offset(width * ONE_COLUMN_DRAG_FACTOR, 0f))
            up()
        }
        rule.waitForIdle()

        assertEquals("room-b", reportedResourceId, "a sideways drag moves the booking to the next lane")
        assertEquals(LocalDateTime(2026, 3, 10, 9, 0), reportedStart, "the time must survive a lane change")
        assertEquals(LocalDateTime(2026, 3, 10, 10, 0), reportedEnd, "duration must be preserved")
    }

    @Test
    fun resourceGrid_dragPastTheLastResource_parksTheBookingInIt() {
        var reportedResourceId: String? = null
        rule.setContent {
            KalendarResourceView(
                resources = rooms,
                state = rememberKalendarScheduleState(initialDate = tuesday),
                events = listOf(booking(name = "Sync", startHour = 9, endHour = 10, resourceId = "room-a")),
                onEventChange = { _, _, _, newResourceId -> reportedResourceId = newResourceId },
            )
        }
        rule.onNodeWithText("Sync").performTouchInput {
            down(center)
            advanceEventTime(LONG_PRESS_MS)
            moveBy(Offset(width * PAST_THE_END_DRAG_FACTOR, 0f))
            up()
        }
        rule.waitForIdle()

        assertEquals("room-b", reportedResourceId, "dragging past the last lane clamps to it")
    }

    @Test
    fun resourceGrid_customAccessorMapsEventsToLanes() {
        val idOf: (KalendarEvent) -> String? = { event ->
            if (event.eventName.startsWith("A")) "room-a" else "room-b"
        }
        rule.setContent {
            KalendarResourceView(
                resources = rooms,
                state = rememberKalendarScheduleState(initialDate = tuesday),
                events = listOf(
                    BasicKalendarEvent(
                        date = tuesday,
                        eventName = "Anaesthesia",
                        startTime = LocalDateTime(2026, 3, 10, 9, 0),
                        endTime = LocalDateTime(2026, 3, 10, 10, 0),
                    ),
                ),
                resourceIdOf = idOf,
            )
        }
        val laneA = rule.onNodeWithText("Room A").getUnclippedBoundsInRoot()
        val block = rule.onNodeWithText("Anaesthesia").getUnclippedBoundsInRoot()

        assertTrue(
            block.left <= centerX(laneA.left, laneA.right) && centerX(laneA.left, laneA.right) <= block.right,
            "the accessor's lane wins even when calendarId is null, was $block against $laneA",
        )
    }

    @Test
    fun resourceGrid_withNoResources_showsTheEmptyState() {
        rule.setContent {
            KalendarResourceView(
                resources = emptyList(),
                state = rememberKalendarScheduleState(initialDate = tuesday),
                events = listOf(booking(name = "Orphan", startHour = 9, endHour = 10, resourceId = "room-a")),
                emptyState = { KalendarResourceDefaults.EmptyState() },
            )
        }
        rule.onNodeWithText("No resources").assertExists()
        rule.onNodeWithText("Orphan").assertDoesNotExist()
    }

    @Test
    fun resourceGrid_emptyStateDefaultIsNotDrawnOverAGrid() {
        rule.setContent {
            KalendarResourceView(
                resources = rooms,
                state = rememberKalendarScheduleState(initialDate = tuesday),
                events = emptyList(),
            )
        }
        rule.onNodeWithText("No resources").assertDoesNotExist()
        rule.onNodeWithText("Room A").assertExists()
    }

    private companion object {
        const val LONG_PRESS_MS = 1_000L
        const val ONE_COLUMN_DRAG_FACTOR = 1.1f
        const val PAST_THE_END_DRAG_FACTOR = 4.5f
    }
}
