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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import com.himanshoe.kalendar.foundation.datetime.KalendarTimeSource
import com.himanshoe.kalendar.foundation.event.BasicKalendarEvent
import com.himanshoe.kalendar.foundation.schedule.KalendarHourWindow
import com.himanshoe.kalendar.foundation.schedule.KalendarResource
import com.himanshoe.kalendar.state.ProvideKalendarTimeSource
import com.himanshoe.kalendar.state.rememberKalendarScheduleState
import com.himanshoe.kalendar.state.rememberKalendarWeekState
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * An hour grid restricted to business hours — the single most common shape a day/week calendar is
 * asked for, and one the views could not express at all while they were hardwired to 24 hours.
 *
 * Every assertion here is about something *derived* from the day: the gutter, which events exist,
 * where a drag can put one, and whether the now-indicator is drawn. Those are the four places a
 * half-applied window shows up as a grid that looks right and behaves wrongly.
 */
class KalendarBusinessHoursUiTest {

    @get:Rule
    val rule = createComposeRule()

    private val day = LocalDate(YEAR, MONTH, DAY)

    private val businessHours = KalendarViewConfig(
        scheduleVisibleHours = KalendarHourWindow(startHour = OPENING_HOUR, endHour = CLOSING_HOUR),
    )

    private fun event(name: String, startHour: Int, endHour: Int) = BasicKalendarEvent(
        date = day,
        eventName = name,
        startTime = LocalDateTime(YEAR, MONTH, DAY, startHour, 0),
        endTime = LocalDateTime(YEAR, MONTH, DAY, endHour, 0),
    )

    private fun timeSourceAt(hour: Int, minute: Int) = KalendarTimeSource(
        timeZone = TimeZone.UTC,
        clock = object : Clock {
            override fun now(): Instant =
                LocalDateTime(YEAR, MONTH, DAY, hour, minute).toInstant(TimeZone.UTC)
        },
    )

    @Test
    fun theGutterLabelsOnlyTheVisibleHours() {
        rule.setContent {
            KalendarSchedule(
                state = rememberKalendarScheduleState(initialDate = day),
                config = businessHours,
            )
        }

        rule.onNodeWithText("08:00").assertExists()
        rule.onNodeWithText("19:00").assertExists()
        rule.onNodeWithText("03:00").assertDoesNotExist()
        // The window's end hour is exclusive: 20:00 is the grid's bottom edge, not a row.
        rule.onNodeWithText("20:00").assertDoesNotExist()
        rule.onNodeWithText("23:00").assertDoesNotExist()
    }

    @Test
    fun anEventOutsideTheWindowIsNotDrawnButOneOverlappingItIs() {
        rule.setContent {
            KalendarSchedule(
                state = rememberKalendarScheduleState(initialDate = day),
                config = businessHours,
                events = listOf(
                    event(name = "Standup", startHour = 9, endHour = 10),
                    event(name = "Breakfast", startHour = 6, endHour = 9),
                    event(name = "Night shift", startHour = 22, endHour = 23),
                ),
            )
        }

        rule.onNodeWithText("Standup").assertExists()
        rule.onNodeWithText("Breakfast").assertExists("an event running into the window must clip, not vanish")
        rule.onNodeWithText("Night shift").assertDoesNotExist()
    }

    @Test
    fun draggingABlockUpwardStopsAtTheTopOfTheWindowRatherThanAtMidnight() {
        var reportedStart: LocalDateTime? = null
        rule.setContent {
            KalendarSchedule(
                state = rememberKalendarScheduleState(initialDate = day),
                config = businessHours,
                events = listOf(event(name = "Sync", startHour = 9, endHour = 10)),
                onEventTimeChange = { _, newStart, _ -> reportedStart = newStart },
            )
        }

        // Far enough up to clear midnight several times over on a full-day grid; the window is the
        // only thing that can stop it.
        rule.onNodeWithText("Sync").performTouchInput {
            down(center)
            advanceEventTime(LONG_PRESS_MS)
            moveBy(Offset(0f, -LONG_DRAG_PX))
            up()
        }
        rule.waitForIdle()

        assertEquals(
            LocalDateTime(YEAR, MONTH, DAY, OPENING_HOUR, 0),
            reportedStart,
            "a drag must clamp to the visible window, not to 00:00 where the user could not follow it",
        )
    }

    @Test
    fun theNowIndicatorIsHiddenWhenTheClockIsOutsideTheWindow() {
        rule.setContent {
            ProvideKalendarTimeSource(timeSourceAt(hour = 22, minute = 0)) {
                KalendarSchedule(
                    config = businessHours,
                    nowIndicator = { BasicText(text = NOW_MARKER) },
                )
            }
        }

        rule.onNodeWithText(NOW_MARKER).assertDoesNotExist()
    }

    @Test
    fun theNowIndicatorIsDrawnWhenTheClockIsInsideTheWindow() {
        rule.setContent {
            ProvideKalendarTimeSource(timeSourceAt(hour = 9, minute = 30)) {
                KalendarSchedule(
                    config = businessHours,
                    nowIndicator = { BasicText(text = NOW_MARKER) },
                )
            }
        }

        rule.onNodeWithText(NOW_MARKER).assertExists()
    }

    @Test
    fun theWeekGridHonoursTheWindowToo() {
        rule.setContent {
            KalendarScheduleWeek(
                state = rememberKalendarWeekState(initialDate = day),
                config = businessHours,
                events = listOf(
                    event(name = "Standup", startHour = 9, endHour = 10),
                    event(name = "Night shift", startHour = 22, endHour = 23),
                ),
            )
        }

        rule.onNodeWithText("08:00").assertExists()
        rule.onNodeWithText("03:00").assertDoesNotExist()
        rule.onNodeWithText("Standup").assertExists()
        rule.onNodeWithText("Night shift").assertDoesNotExist()
    }

    @Test
    fun theResourceGridHonoursTheWindowToo() {
        rule.setContent {
            KalendarResourceView(
                resources = listOf(KalendarResource(id = ROOM_ID, title = "Room A")),
                state = rememberKalendarScheduleState(initialDate = day),
                config = businessHours,
                events = listOf(
                    event(name = "Standup", startHour = 9, endHour = 10).copy(calendarId = ROOM_ID),
                    event(name = "Night shift", startHour = 22, endHour = 23).copy(calendarId = ROOM_ID),
                ),
            )
        }

        rule.onNodeWithText("08:00").assertExists()
        rule.onNodeWithText("03:00").assertDoesNotExist()
        rule.onNodeWithText("Standup").assertExists()
        rule.onNodeWithText("Night shift").assertDoesNotExist()
    }

    private companion object {
        const val YEAR = 2026
        const val MONTH = 3
        const val DAY = 10
        const val OPENING_HOUR = 8
        const val CLOSING_HOUR = 20
        const val LONG_PRESS_MS = 1_000L
        const val LONG_DRAG_PX = 4_000f
        const val NOW_MARKER = "now-marker"
        const val ROOM_ID = "room-a"
    }
}
