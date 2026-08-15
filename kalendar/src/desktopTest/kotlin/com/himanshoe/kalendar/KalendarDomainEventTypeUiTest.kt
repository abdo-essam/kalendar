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

import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.himanshoe.kalendar.foundation.event.KalendarEvent
import com.himanshoe.kalendar.foundation.event.byDateExpandingSpans
import com.himanshoe.kalendar.foundation.schedule.KalendarResource
import com.himanshoe.kalendar.state.rememberKalendarScheduleState
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

/**
 * That a consumer's own event type survives a round trip through a calendar.
 *
 * Every assertion here is really about the *compiler*: `booking.roomId` only resolves if the view
 * handed the callback a [Booking] rather than a [KalendarEvent], so this file would not compile
 * against a non-generic Kalendar at all — which is the strongest form the failure can take, and how
 * it was checked before the views were parameterised. What the runtime assertions add on top is the
 * part a signature cannot promise: the Schedule and Resource views re-attach [E][Booking] on the way
 * out of a block engine that is typed on [KalendarEvent], so a wrong event reaching a callback there
 * would be a `ClassCastException` rather than a type error, and only running it can rule that out.
 */
@Suppress("MagicNumber")
class KalendarDomainEventTypeUiTest {

    @get:Rule
    val rule = createComposeRule()

    /**
     * A consumer's domain type: a [KalendarEvent] with a field of its own that no Kalendar signature
     * knows about, which is what makes every `booking.roomId` below a real test of the type parameter.
     */
    private class Booking(
        override val date: LocalDate,
        override val eventName: String,
        val roomId: String,
        override val startTime: LocalDateTime? = null,
        override val endTime: LocalDateTime? = null,
    ) : KalendarEvent {
        override val eventDescription: String? = null
        override val eventColor: Color? = null
    }

    private val day = LocalDate(2026, 3, 10)

    private fun booking(name: String, room: String, startHour: Int? = null) = Booking(
        date = day,
        eventName = name,
        roomId = room,
        startTime = startHour?.let { LocalDateTime(day.year, day.month, day.day, it, 0) },
        endTime = startHour?.let { LocalDateTime(day.year, day.month, day.day, it + 1, 0) },
    )

    @Test
    fun agendaHandsItsOwnEventTypeToBothTheRowSlotAndTheClick() {
        var clickedRoom: String? = null
        rule.setContent {
            KalendarAgenda(
                events = listOf(booking(name = "Standup", room = "room-a")),
                onEventClick = { booking -> clickedRoom = booking.roomId },
            ) { booking ->
                BasicText(text = "row:${booking.roomId}")
            }
        }

        rule.onNodeWithText("row:room-a").performClick()
        rule.waitForIdle()

        assertEquals("room-a", clickedRoom, "the agenda's click should hand back the Booking it was given")
    }

    @Test
    fun monthHandsItsOwnEventTypeToTheDateClick() {
        var clickedRoom: String? = null
        rule.setContent {
            KalendarMonth(
                selectedDate = day,
                events = listOf(booking(name = "Standup", room = "room-b")),
                onDateClick = { _, bookings -> clickedRoom = bookings.firstOrNull()?.roomId },
            )
        }

        rule.onNodeWithText("10").performClick()
        rule.waitForIdle()

        assertEquals("room-b", clickedRoom, "the month's date click should hand back the Bookings on that date")
    }

    @Test
    fun monthHandsItsOwnEventTypeToTheDayCellSlot() {
        rule.setContent {
            KalendarMonth(
                selectedDate = day,
                events = listOf(booking(name = "Standup", room = "room-g")),
                dayContent = { scope ->
                    BasicText(text = "cell:${scope.date.day}:${scope.events.joinToString { it.roomId }}")
                },
            )
        }

        rule.onNodeWithText("cell:10:room-g").assertExists()
    }

    @Test
    fun scheduleReattachesItsOwnEventTypeOnTheWayOutOfTheBlockEngine() {
        var clickedRoom: String? = null
        rule.setContent {
            KalendarSchedule(
                state = rememberKalendarScheduleState(initialDate = day),
                events = listOf(booking(name = "Standup", room = "room-c", startHour = 9)),
                onEventClick = { booking -> clickedRoom = booking.roomId },
            ) { scope ->
                BasicText(text = "block:${scope.event.roomId}")
            }
        }

        rule.onNodeWithText("block:room-c").performClick()
        rule.waitForIdle()

        assertEquals("room-c", clickedRoom, "the schedule's click should hand back the Booking, not a KalendarEvent")
    }

    @Test
    fun resourceViewReadsTheLaneLinkOffTheDomainTypeWithoutACast() {
        var clickedRoom: String? = null
        rule.setContent {
            KalendarResourceView(
                resources = listOf(KalendarResource(id = "room-d", title = "Room D")),
                state = rememberKalendarScheduleState(initialDate = day),
                events = listOf(booking(name = "Standup", room = "room-d", startHour = 9)),
                resourceIdOf = { booking -> booking.roomId },
                onEventClick = { booking -> clickedRoom = booking.roomId },
            ) { scope ->
                BasicText(text = "lane:${scope.resource.id}:${scope.event.roomId}")
            }
        }

        rule.onNodeWithText("lane:room-d:room-d").performClick()
        rule.waitForIdle()

        assertEquals("room-d", clickedRoom, "the lane grid's click should hand back the Booking it laid out")
    }

    @Test
    fun theDateIndexKeepsTheElementTypeItWasGiven() {
        val bookings = listOf(
            booking(name = "Standup", room = "room-e"),
            booking(name = "Retro", room = "room-f"),
        )

        val rooms: List<String> = bookings.byDateExpandingSpans().getValue(day).map { it.roomId }

        assertEquals(listOf("room-e", "room-f"), rooms, "the index should hand back Bookings, not KalendarEvents")
    }

    @Test
    fun theEventFreeOverloadsStillCompileAndRenderWithNothingToInferFrom() {
        rule.setContent {
            KalendarMonth(selectedDate = day, onDateClick = { _, _ -> })
        }

        rule.onNodeWithText("10").assertExists()
    }
}
