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

import com.himanshoe.kalendar.foundation.event.BasicKalendarEvent
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The maths behind [KalendarHourWindow]: what a grid showing less than a full day does to block
 * geometry, to the events it lays out, to a drag, and to where a page opens.
 */
class KalendarHourWindowTest {

    private val day = LocalDate(YEAR, MONTH, DAY)
    private val businessHours = KalendarHourWindow(startHour = 8, endHour = 20)

    private fun event(name: String, startHour: Int, endHour: Int) = BasicKalendarEvent(
        date = day,
        eventName = name,
        startTime = LocalDateTime(YEAR, MONTH, DAY, startHour, 0),
        endTime = LocalDateTime(YEAR, MONTH, DAY, endHour, 0),
    )

    private fun block(startMinutes: Int, endMinutes: Int) = KalendarScheduleBlock(
        event = event(name = "Block", startHour = 0, endHour = 1),
        startMinutes = startMinutes,
        endMinutes = endMinutes,
        column = 0,
        columns = 1,
    )

    @Test
    fun fullDayIsTheDefaultAndSpansMidnightToMidnight() {
        assertEquals(0, KalendarHourWindow.FullDay.startMinutes)
        assertEquals(MINUTES_PER_DAY, KalendarHourWindow.FullDay.endMinutes)
        assertEquals(HOURS_PER_DAY, KalendarHourWindow.FullDay.hourCount)
    }

    @Test
    fun businessHoursWindowCountsOnlyItsOwnHours() {
        assertEquals(12, businessHours.hourCount)
        assertEquals(8 * MINUTES_PER_HOUR, businessHours.startMinutes)
        assertEquals(20 * MINUTES_PER_HOUR, businessHours.endMinutes)
        assertEquals(12 * MINUTES_PER_HOUR, businessHours.spanMinutes)
    }

    @Test
    fun anEmptyOrInvertedWindowIsRejected() {
        assertFailsWith<IllegalArgumentException> { KalendarHourWindow(startHour = 9, endHour = 9) }
        assertFailsWith<IllegalArgumentException> { KalendarHourWindow(startHour = 20, endHour = 8) }
        assertFailsWith<IllegalArgumentException> { KalendarHourWindow(startHour = -1, endHour = 8) }
        assertFailsWith<IllegalArgumentException> { KalendarHourWindow(startHour = 8, endHour = 25) }
    }

    @Test
    fun blockInsideTheWindowIsPositionedRelativeToItsTopEdge() {
        val placement = blockPlacement(
            block = block(startMinutes = 9 * MINUTES_PER_HOUR, endMinutes = 10 * MINUTES_PER_HOUR),
            window = businessHours,
        )

        assertEquals(MINUTES_PER_HOUR, placement.topMinutes, "09:00 is one hour below an 08:00 window top")
        assertEquals(MINUTES_PER_HOUR, placement.heightMinutes)
    }

    @Test
    fun blockStartingBeforeTheWindowIsClippedAtTheTopRatherThanDropped() {
        val placement = blockPlacement(
            block = block(startMinutes = 7 * MINUTES_PER_HOUR, endMinutes = 9 * MINUTES_PER_HOUR),
            window = businessHours,
        )

        assertEquals(0, placement.topMinutes, "an event starting before the window pins to the top edge")
        assertEquals(MINUTES_PER_HOUR, placement.heightMinutes, "only the 08:00-09:00 hour is on screen")
    }

    @Test
    fun blockEndingAfterTheWindowIsClippedAtTheBottom() {
        val placement = blockPlacement(
            block = block(startMinutes = 19 * MINUTES_PER_HOUR, endMinutes = 23 * MINUTES_PER_HOUR),
            window = businessHours,
        )

        assertEquals(11 * MINUTES_PER_HOUR, placement.topMinutes)
        assertEquals(MINUTES_PER_HOUR, placement.heightMinutes, "only the 19:00-20:00 hour is on screen")
    }

    @Test
    fun blockEntirelyOutsideTheWindowHasNoHeight() {
        val placement = blockPlacement(
            block = block(startMinutes = 21 * MINUTES_PER_HOUR, endMinutes = 22 * MINUTES_PER_HOUR),
            window = businessHours,
        )

        assertEquals(0, placement.heightMinutes)
    }

    @Test
    fun eventsOutsideTheWindowAreNotLaidOutAtAll() {
        val blocks = scheduleBlocks(
            events = listOf(
                event(name = "Standup", startHour = 9, endHour = 10),
                event(name = "Night shift", startHour = 22, endHour = 23),
            ),
            date = day,
            window = businessHours,
        )

        assertEquals(listOf("Standup"), blocks.map { it.event.eventName })
    }

    @Test
    fun aClippedEventKeepsItsRealTimesSoADragCannotTruncateIt() {
        val blocks = scheduleBlocks(
            events = listOf(event(name = "Early", startHour = 6, endHour = 9)),
            date = day,
            window = businessHours,
        )

        assertEquals(6 * MINUTES_PER_HOUR, blocks.single().startMinutes, "clipping is a drawing concern only")
    }

    @Test
    fun anEventTouchingOnlyTheWindowsEdgeIsOutsideIt() {
        val blocks = scheduleBlocks(
            events = listOf(
                event(name = "Before", startHour = 7, endHour = 8),
                event(name = "After", startHour = 20, endHour = 21),
            ),
            date = day,
            window = businessHours,
        )

        assertTrue(blocks.isEmpty(), "abutting the window is not intersecting it; neither has a pixel to draw")
    }

    @Test
    fun movingABlockClampsToTheWindowRatherThanToMidnight() {
        val moved = adjustedScheduleBlock(
            block = block(startMinutes = 9 * MINUTES_PER_HOUR, endMinutes = 10 * MINUTES_PER_HOUR),
            deltaMinutes = -MINUTES_PER_DAY,
            resizing = false,
            window = businessHours,
        )

        assertEquals(businessHours.startMinutes, moved.startMinutes)
        assertEquals(businessHours.startMinutes + MINUTES_PER_HOUR, moved.endMinutes, "duration is preserved")
    }

    @Test
    fun resizingABlockStopsAtTheBottomOfTheWindow() {
        val resized = adjustedScheduleBlock(
            block = block(startMinutes = 19 * MINUTES_PER_HOUR, endMinutes = 19 * MINUTES_PER_HOUR + 30),
            deltaMinutes = MINUTES_PER_DAY,
            resizing = true,
            window = businessHours,
        )

        assertEquals(businessHours.endMinutes, resized.endMinutes)
    }

    @Test
    fun draggingTheTopEdgeStopsAtTheTopOfTheWindow() {
        val resized = adjustedScheduleBlockForDrag(
            block = block(startMinutes = 9 * MINUTES_PER_HOUR, endMinutes = 11 * MINUTES_PER_HOUR),
            deltaMinutes = -MINUTES_PER_DAY,
            mode = ScheduleDragMode.ResizeStart,
            window = businessHours,
        )

        assertEquals(businessHours.startMinutes, resized.startMinutes)
        assertEquals(11 * MINUTES_PER_HOUR, resized.endMinutes, "the end stays put while the start moves")
    }

    @Test
    fun aBlockLongerThanTheWindowPinsToItsTopInsteadOfThrowing() {
        val window = KalendarHourWindow(startHour = 9, endHour = 10)
        val moved = adjustedScheduleBlock(
            block = block(startMinutes = 0, endMinutes = 8 * MINUTES_PER_HOUR),
            deltaMinutes = 30,
            resizing = false,
            window = window,
        )

        assertEquals(window.startMinutes, moved.startMinutes)
    }

    @Test
    fun aCreateDragIsMeasuredFromTheWindowTopNotFromMidnight() {
        val range = scheduleCreateRange(
            anchorPx = MINUTES_PER_HOUR.toFloat(),
            currentPx = 2f * MINUTES_PER_HOUR,
            minutePx = 1f,
            snapMinutes = 15,
            window = businessHours,
        )

        assertEquals(9 * MINUTES_PER_HOUR, range.startMinutes, "one hour down an 08:00 grid is 09:00")
        assertEquals(10 * MINUTES_PER_HOUR, range.endMinutes)
    }

    @Test
    fun aCreateDragPastTheBottomOfTheWindowIsPushedBackAgainstIt() {
        val range = scheduleCreateRange(
            anchorPx = 1_000f * MINUTES_PER_HOUR,
            currentPx = 2_000f * MINUTES_PER_HOUR,
            minutePx = 1f,
            snapMinutes = 15,
            window = businessHours,
        )

        assertEquals(businessHours.endMinutes - MIN_SCHEDULE_BLOCK_MINUTES, range.startMinutes)
        assertEquals(businessHours.endMinutes, range.endMinutes)
    }

    @Test
    fun hitTestingAPressUsesWindowRelativePixels() {
        val blocks = listOf(block(startMinutes = 9 * MINUTES_PER_HOUR, endMinutes = 10 * MINUTES_PER_HOUR))

        val onBlock = scheduleBlockAt(
            blocks = blocks,
            xPx = 1f,
            yPx = 90f,
            widthPx = 100,
            minutePx = 1f,
            window = businessHours,
        )
        val aboveBlock = scheduleBlockAt(
            blocks = blocks,
            xPx = 1f,
            yPx = 10f,
            widthPx = 100,
            minutePx = 1f,
            window = businessHours,
        )

        assertTrue(onBlock != null, "90px below an 08:00 grid top is 09:30, inside the block")
        assertNull(aboveBlock, "10px below an 08:00 grid top is 08:10, above the block")
    }

    @Test
    fun aPageOpensAtTheTopWhenTheConfiguredScrollHourIsAboveTheWindow() {
        assertEquals(0, initialScrollHourOffset(initialScrollHour = 7, window = businessHours))
    }

    @Test
    fun aPageOpensAtTheConfiguredHourMeasuredFromTheWindowTop() {
        assertEquals(1, initialScrollHourOffset(initialScrollHour = 9, window = businessHours))
        assertEquals(7, initialScrollHourOffset(initialScrollHour = 7, window = KalendarHourWindow.FullDay))
    }

    @Test
    fun aNowReadingIsInsideTheWindowOnlyBetweenItsEdges() {
        assertTrue(LocalDateTime(YEAR, MONTH, DAY, 9, 30).minutesFromMidnight() in businessHours)
        assertTrue(LocalDateTime(YEAR, MONTH, DAY, 7, 59).minutesFromMidnight() !in businessHours)
        assertTrue(LocalDateTime(YEAR, MONTH, DAY, 20, 0).minutesFromMidnight() !in businessHours)
    }

    private companion object {
        const val YEAR = 2026
        const val MONTH = 3
        const val DAY = 10
    }
}
