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

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import com.himanshoe.kalendar.foundation.event.BasicKalendarEvent
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

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

    @Test
    fun monthView_jumpPicker_tapTitleThenMonth_navigatesAcrossYears() {
        rule.setContent {
            KalendarMonth(selectedDate = march)
        }
        rule.onNodeWithText("March 2026").performClick()
        rule.onNodeWithContentDescription("Previous year").performClick()
        rule.onNodeWithText("Jun").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("June 2025").assertExists()
    }

    @Test
    fun scheduleView_longPressDragBlock_reportsSnappedLaterTimesWithSameDuration() {
        var reportedStart: LocalDateTime? = null
        var reportedEnd: LocalDateTime? = null
        rule.setContent {
            KalendarSchedule(
                selectedDate = march,
                events = listOf(
                    BasicKalendarEvent(
                        date = march,
                        eventName = "Sync",
                        startTime = LocalDateTime(2026, 3, 10, 9, 0),
                        endTime = LocalDateTime(2026, 3, 10, 10, 0),
                    ),
                ),
                onEventTimeChange = { _, newStart, newEnd ->
                    reportedStart = newStart
                    reportedEnd = newEnd
                },
            )
        }
        rule.onNodeWithText("Sync").performTouchInput {
            down(center)
            advanceEventTime(LONG_PRESS_MS)
            moveBy(Offset(0f, DRAG_DISTANCE_PX))
            up()
        }
        rule.waitForIdle()

        val start = assertNotNull(reportedStart, "onEventTimeChange never fired")
        val end = assertNotNull(reportedEnd)
        assertTrue(start > LocalDateTime(2026, 3, 10, 9, 0), "drag down should move the event later, was $start")
        assertEquals(0, start.minute % SNAP_MINUTES, "start should snap to $SNAP_MINUTES-minute steps, was $start")
        assertEquals(ONE_HOUR_MINUTES, durationMinutes(start, end), "move must preserve duration")
    }

    @Test
    fun scheduleView_secondDragAfterEventsUpdate_usesUpdatedBlockNotFirstComposition() {
        val starts = mutableListOf<LocalDateTime>()
        rule.setContent {
            var events by remember {
                mutableStateOf(
                    listOf(
                        BasicKalendarEvent(
                            date = march,
                            eventName = "Sync",
                            startTime = LocalDateTime(2026, 3, 10, 9, 0),
                            endTime = LocalDateTime(2026, 3, 10, 10, 0),
                        ),
                    ),
                )
            }
            KalendarSchedule(
                selectedDate = march,
                events = events,
                onEventTimeChange = { event, newStart, newEnd ->
                    starts += newStart
                    events = listOf(
                        BasicKalendarEvent(
                            date = newStart.date,
                            eventName = event.eventName,
                            startTime = newStart,
                            endTime = newEnd,
                        ),
                    )
                },
            )
        }

        repeat(2) {
            rule.onNodeWithText("Sync").performTouchInput {
                down(center)
                advanceEventTime(LONG_PRESS_MS)
                moveBy(Offset(0f, DRAG_DISTANCE_PX))
                up()
            }
            rule.waitForIdle()
        }

        assertEquals(2, starts.size, "both drags should report a time change")
        // Regression guard for the stale-capture bug: the gesture handler used to keep the
        // first composition's block list, so the second drag re-reported the first drag's
        // result instead of moving the already-moved event further down.
        assertTrue(
            starts[1] > starts[0],
            "second drag must start from the updated event time (got ${starts[0]} then ${starts[1]})",
        )
    }

    @Test
    fun scheduleView_longPressDragBottomStrip_resizesEndTimeOnly() {
        var reportedStart: LocalDateTime? = null
        var reportedEnd: LocalDateTime? = null
        rule.setContent {
            KalendarSchedule(
                selectedDate = march,
                events = listOf(
                    BasicKalendarEvent(
                        date = march,
                        eventName = "Sync",
                        startTime = LocalDateTime(2026, 3, 10, 9, 0),
                        endTime = LocalDateTime(2026, 3, 10, 10, 0),
                    ),
                ),
                onEventTimeChange = { _, newStart, newEnd ->
                    reportedStart = newStart
                    reportedEnd = newEnd
                },
            )
        }
        // The merged block node spans the whole event block; a press in its bottom strip
        // resizes instead of moving. Dragging down by exactly one block height (= the
        // event's 60min duration) doubles the duration regardless of density.
        rule.onNodeWithText("Sync").performTouchInput {
            down(Offset(center.x, height - height * BOTTOM_STRIP_GRAB_FRACTION))
            advanceEventTime(LONG_PRESS_MS)
            moveBy(Offset(0f, height.toFloat()))
            up()
        }
        rule.waitForIdle()

        assertEquals(LocalDateTime(2026, 3, 10, 9, 0), reportedStart, "resize must not move the start")
        assertEquals(LocalDateTime(2026, 3, 10, 11, 0), reportedEnd, "dragging one block height should add 60min")
    }

    private fun durationMinutes(start: LocalDateTime, end: LocalDateTime): Int {
        val startTotal = start.hour * MINUTES_PER_HOUR + start.minute
        val endTotal = end.hour * MINUTES_PER_HOUR + end.minute
        val dayCarry = if (end.date > start.date) MINUTES_PER_DAY else 0
        return endTotal + dayCarry - startTotal
    }

    private companion object {
        const val LONG_PRESS_MS = 1_000L
        const val DRAG_DISTANCE_PX = 96f
        const val SNAP_MINUTES = 15
        const val ONE_HOUR_MINUTES = 60

        /** 5% of the block height from the bottom — well inside the 12dp resize strip. */
        const val BOTTOM_STRIP_GRAB_FRACTION = 0.05f
    }
}
