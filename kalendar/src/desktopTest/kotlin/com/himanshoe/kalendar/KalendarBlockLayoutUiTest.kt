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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import com.himanshoe.kalendar.foundation.event.BasicKalendarEvent
import com.himanshoe.kalendar.foundation.event.KalendarEvents
import com.himanshoe.kalendar.foundation.schedule.KalendarResource
import com.himanshoe.kalendar.slot.KalendarResourceEventScope
import com.himanshoe.kalendar.state.rememberKalendarScheduleState
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

/**
 * Cover for the one block layout both hour grids now share.
 *
 * The lane grid used to carry its own fork of it, which had been copied from a schedule grid that
 * predated the top resize handle and the narrowed strip — so a booking's start edge could not be
 * dragged at all, and a short booking was all resize handle and could not be moved. Those two are the
 * first tests here; the last is the per-block keying that the shared layout also brought with it.
 */
@Suppress("MagicNumber")
class KalendarBlockLayoutUiTest {

    @get:Rule
    val rule = createComposeRule()

    private val tuesday = LocalDate(2026, 3, 10)

    private val rooms = listOf(
        KalendarResource(id = "room-a", title = "Room A"),
        KalendarResource(id = "room-b", title = "Room B"),
    )

    private fun booking(name: String, start: LocalTime, end: LocalTime): BasicKalendarEvent =
        BasicKalendarEvent(
            date = tuesday,
            eventName = name,
            startTime = LocalDateTime(tuesday, start),
            endTime = LocalDateTime(tuesday, end),
            calendarId = "room-a",
        )

    /**
     * An `eventContent` that fills its slot, so a touch offset expressed as a fraction of the node is
     * a fraction of the *block* — which is what makes "near the top edge" mean the resize strip.
     */
    private val blockProbe: @Composable (KalendarResourceEventScope) -> Unit = { scope ->
        Box(modifier = Modifier.fillMaxSize().testTag(scope.event.eventName))
    }

    @Test
    fun resourceBlock_draggingTheTopEdge_movesTheStartAndLeavesTheEndAlone() {
        var reportedStart: LocalDateTime? = null
        var reportedEnd: LocalDateTime? = null
        rule.setContent {
            KalendarResourceView(
                resources = rooms,
                state = rememberKalendarScheduleState(initialDate = tuesday),
                events = listOf(booking(name = "Sync", start = LocalTime(9, 0), end = LocalTime(11, 0))),
                onEventChange = { _, newStart, newEnd, _ ->
                    reportedStart = newStart
                    reportedEnd = newEnd
                },
                eventContent = blockProbe,
            )
        }

        rule.onNodeWithTag("Sync", useUnmergedTree = true).performTouchInput {
            down(Offset(x = width / 2f, y = height * EDGE_FRACTION))
            advanceEventTime(LONG_PRESS_MS)
            moveBy(Offset(x = 0f, y = -height * HALF_HOUR_FRACTION))
            up()
        }
        rule.waitForIdle()

        assertEquals(
            LocalDateTime(2026, 3, 10, 8, 30),
            reportedStart,
            "the lane grid must be able to drag a booking's start edge, not only its end",
        )
        assertEquals(LocalDateTime(2026, 3, 10, 11, 0), reportedEnd, "a top resize must not move the end")
    }

    @Test
    fun resourceBlock_draggingTheBottomEdge_stillMovesTheEndAlone() {
        var reportedStart: LocalDateTime? = null
        var reportedEnd: LocalDateTime? = null
        rule.setContent {
            KalendarResourceView(
                resources = rooms,
                state = rememberKalendarScheduleState(initialDate = tuesday),
                events = listOf(booking(name = "Sync", start = LocalTime(9, 0), end = LocalTime(11, 0))),
                onEventChange = { _, newStart, newEnd, _ ->
                    reportedStart = newStart
                    reportedEnd = newEnd
                },
                eventContent = blockProbe,
            )
        }

        rule.onNodeWithTag("Sync", useUnmergedTree = true).performTouchInput {
            down(Offset(x = width / 2f, y = height * (1f - EDGE_FRACTION)))
            advanceEventTime(LONG_PRESS_MS)
            moveBy(Offset(x = 0f, y = height * HALF_HOUR_FRACTION))
            up()
        }
        rule.waitForIdle()

        assertEquals(LocalDateTime(2026, 3, 10, 9, 0), reportedStart, "a bottom resize must not move the start")
        assertEquals(LocalDateTime(2026, 3, 10, 11, 30), reportedEnd)
    }

    /**
     * A resize is a time edit, never a lane change — the grid must ignore whatever sideways travel
     * the finger picks up while dragging an edge.
     */
    @Test
    fun resourceBlock_draggingTheTopEdgeSideways_staysInItsOwnLane() {
        var reportedResourceId: String? = null
        rule.setContent {
            KalendarResourceView(
                resources = rooms,
                state = rememberKalendarScheduleState(initialDate = tuesday),
                events = listOf(booking(name = "Sync", start = LocalTime(9, 0), end = LocalTime(11, 0))),
                onEventChange = { _, _, _, newResourceId -> reportedResourceId = newResourceId },
                eventContent = blockProbe,
            )
        }

        rule.onNodeWithTag("Sync", useUnmergedTree = true).performTouchInput {
            down(Offset(x = width / 2f, y = height * EDGE_FRACTION))
            advanceEventTime(LONG_PRESS_MS)
            moveBy(Offset(x = width * ONE_COLUMN_DRAG_FACTOR, y = -height * HALF_HOUR_FRACTION))
            up()
        }
        rule.waitForIdle()

        assertEquals("room-a", reportedResourceId, "dragging an edge must not carry the booking to another lane")
    }

    /**
     * A block shorter than two resize strips has to keep a grabbable middle.
     *
     * At the built-in 64dp hour a 15-minute booking is 16dp tall and the strip is 12dp, so a press
     * anywhere in the lower three quarters used to read as a resize and the booking could not be
     * moved at all. The strip narrows with the block instead.
     */
    @Test
    fun resourceBlock_shortBlock_movesRatherThanResizes() {
        var reportedStart: LocalDateTime? = null
        var reportedEnd: LocalDateTime? = null
        rule.setContent {
            KalendarResourceView(
                resources = rooms,
                state = rememberKalendarScheduleState(initialDate = tuesday),
                events = listOf(booking(name = "Quick", start = LocalTime(9, 0), end = LocalTime(9, 15))),
                onEventChange = { _, newStart, newEnd, _ ->
                    reportedStart = newStart
                    reportedEnd = newEnd
                },
                eventContent = blockProbe,
            )
        }

        // The block is a quarter-hour tall, so four of its own heights is exactly one hour.
        rule.onNodeWithTag("Quick", useUnmergedTree = true).performTouchInput {
            down(center)
            advanceEventTime(LONG_PRESS_MS)
            moveBy(Offset(x = 0f, y = height * SHORT_BLOCK_HOUR_FACTOR))
            up()
        }
        rule.waitForIdle()

        assertEquals(
            LocalDateTime(2026, 3, 10, 10, 0),
            reportedStart,
            "a press in the middle of a short block must move it, not resize it",
        )
        assertEquals(
            LocalDateTime(2026, 3, 10, 10, 15),
            reportedEnd,
            "a move preserves the duration",
        )
    }

    /**
     * Slot state must follow its event, not its position in the day.
     *
     * Each block's slot records the event it first composed with. Keyed by position, inserting an
     * earlier event slides every block into the slot below and each one keeps the *previous*
     * neighbour's recorded name — which is what a caller's expand animation or text-field cursor does
     * too. Keyed by the event, the subtrees move with their block.
     */
    @Test
    fun scheduleBlock_insertingAnEarlierEvent_keepsSlotStateWithItsOwnEvent() {
        val later: KalendarEvents = listOf(
            booking(name = "B", start = LocalTime(10, 0), end = LocalTime(11, 0)),
            booking(name = "C", start = LocalTime(11, 0), end = LocalTime(12, 0)),
        )
        val events = mutableStateOf(later)
        rule.setContent {
            KalendarSchedule(
                state = rememberKalendarScheduleState(initialDate = tuesday),
                events = events.value,
            ) { scope ->
                val firstSeen = remember { scope.event.eventName }
                BasicText(text = "${scope.event.eventName}|$firstSeen")
            }
        }
        rule.waitForIdle()

        rule.runOnIdle {
            events.value = listOf(booking(name = "A", start = LocalTime(9, 0), end = LocalTime(10, 0))) + later
        }
        rule.waitForIdle()

        rule.onNodeWithText("A|A").assertExists()
        rule.onNodeWithText("B|B").assertExists()
        rule.onNodeWithText("C|C").assertExists()
    }

    private companion object {
        const val LONG_PRESS_MS = 1_000L

        /** Inside the resize strip at either edge for any block taller than about three strips. */
        const val EDGE_FRACTION = 0.05f

        /** Half an hour, for the two-hour blocks above: a quarter of the block's own height. */
        const val HALF_HOUR_FRACTION = 0.25f

        /** One hour, for the quarter-hour block: four of its own heights. */
        const val SHORT_BLOCK_HOUR_FACTOR = 4f

        const val ONE_COLUMN_DRAG_FACTOR = 1.1f
    }
}
