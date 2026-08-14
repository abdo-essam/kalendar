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

package com.himanshoe.kalendar.foundation.schedule

import com.himanshoe.kalendar.foundation.event.BasicKalendarEvent
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

/**
 * Pure-maths cover for the create-drag range, the top-edge resize and the grabbed-edge decision —
 * the parts of the two schedule editing gestures that can be checked without a compose rule.
 */
@Suppress("MagicNumber")
class KalendarScheduleCreateTest {

    private val date = LocalDate(2026, 3, 10)

    /** One pixel per minute, so px inputs read directly as minutes. */
    private val minutePx = 1f

    private fun block(startMinutes: Int, endMinutes: Int, column: Int = 0, columns: Int = 1) =
        KalendarScheduleBlock(
            event = BasicKalendarEvent(date = date, eventName = "Sync"),
            startMinutes = startMinutes,
            endMinutes = endMinutes,
            column = column,
            columns = columns,
        )

    @Test
    fun createRange_downwardDrag_snapsBothEndsToTheStep() {
        val range = scheduleCreateRange(
            anchorPx = 547f,
            currentPx = 622f,
            minutePx = minutePx,
            snapMinutes = 15,
        )
        assertEquals(KalendarScheduleCreateRange(startMinutes = 540, endMinutes = 615), range)
    }

    @Test
    fun createRange_upwardDrag_readsIdenticallyToTheSameSweepDownward() {
        val upward = scheduleCreateRange(
            anchorPx = 622f,
            currentPx = 547f,
            minutePx = minutePx,
            snapMinutes = 15,
        )
        val downward = scheduleCreateRange(
            anchorPx = 547f,
            currentPx = 622f,
            minutePx = minutePx,
            snapMinutes = 15,
        )
        assertEquals(downward, upward, "the range is between press and current in either order")
    }

    @Test
    fun createRange_sweepShorterThanTheMinimum_growsToTheMinimum() {
        val range = scheduleCreateRange(
            anchorPx = 540f,
            currentPx = 545f,
            minutePx = minutePx,
            snapMinutes = 15,
        )
        assertEquals(540, range.startMinutes)
        assertEquals(540 + MIN_SCHEDULE_BLOCK_MINUTES, range.endMinutes)
    }

    @Test
    fun createRange_pressAtTheVeryEndOfTheDay_staysInsideTheDay() {
        val range = scheduleCreateRange(
            anchorPx = MINUTES_PER_DAY.toFloat(),
            currentPx = MINUTES_PER_DAY + 500f,
            minutePx = minutePx,
            snapMinutes = 15,
        )
        assertEquals(MINUTES_PER_DAY - MIN_SCHEDULE_BLOCK_MINUTES, range.startMinutes)
        assertEquals(MINUTES_PER_DAY, range.endMinutes, "a range may end at midnight but not past it")
    }

    @Test
    fun createRange_dragAboveTheTopOfTheGrid_clampsToMidnight() {
        val range = scheduleCreateRange(
            anchorPx = 120f,
            currentPx = -900f,
            minutePx = minutePx,
            snapMinutes = 15,
        )
        assertEquals(KalendarScheduleCreateRange(startMinutes = 0, endMinutes = 120), range)
    }

    @Test
    fun createTimes_endAtMidnight_reportsTheFollowingDayRatherThanTruncating() {
        val (start, end) = scheduleCreateTimes(
            range = KalendarScheduleCreateRange(startMinutes = MINUTES_PER_DAY - 30, endMinutes = MINUTES_PER_DAY),
            date = date,
        )
        assertEquals(LocalDateTime(2026, 3, 10, 23, 30), start)
        assertEquals(LocalDateTime(2026, 3, 11, 0, 0), end)
    }

    @Test
    fun createTimes_ordinaryRange_mapsMinutesOntoTheDay() {
        val (start, end) = scheduleCreateTimes(
            range = KalendarScheduleCreateRange(startMinutes = 540, endMinutes = 615),
            date = date,
        )
        assertEquals(LocalDateTime(2026, 3, 10, 9, 0), start)
        assertEquals(LocalDateTime(2026, 3, 10, 10, 15), end)
    }

    @Test
    fun dragMode_pressesNearEachEdge_pickThatEdgeAndTheMiddleMoves() {
        assertEquals(
            KalendarScheduleDragMode.ResizeStart,
            scheduleDragMode(pressYPx = 3f, blockHeightPx = 60f, resizeHandlePx = 12f),
        )
        assertEquals(
            KalendarScheduleDragMode.ResizeEnd,
            scheduleDragMode(pressYPx = 57f, blockHeightPx = 60f, resizeHandlePx = 12f),
        )
        assertEquals(
            KalendarScheduleDragMode.Move,
            scheduleDragMode(pressYPx = 30f, blockHeightPx = 60f, resizeHandlePx = 12f),
        )
    }

    @Test
    fun dragMode_blockShorterThanTwoHandles_keepsAGrabbableMiddle() {
        val mode = scheduleDragMode(pressYPx = 9f, blockHeightPx = 18f, resizeHandlePx = 12f)
        assertEquals(KalendarScheduleDragMode.Move, mode, "the handles must narrow rather than swallow the block")
    }

    @Test
    fun topResize_movesTheStartAndPinsTheEnd() {
        val adjusted = adjustedScheduleBlockForDrag(
            block = block(startMinutes = 540, endMinutes = 600),
            deltaMinutes = -30,
            mode = KalendarScheduleDragMode.ResizeStart,
        )
        assertEquals(510, adjusted.startMinutes)
        assertEquals(600, adjusted.endMinutes, "a top resize must not move the end")
    }

    @Test
    fun topResize_draggedBeyondEitherLimit_clampsToTheEndAndToMidnight() {
        val pastTheEnd = adjustedScheduleBlockForDrag(
            block = block(startMinutes = 540, endMinutes = 600),
            deltaMinutes = 300,
            mode = KalendarScheduleDragMode.ResizeStart,
        )
        assertEquals(600 - MIN_SCHEDULE_BLOCK_MINUTES, pastTheEnd.startMinutes)
        assertEquals(600, pastTheEnd.endMinutes)

        val aboveMidnight = adjustedScheduleBlockForDrag(
            block = block(startMinutes = 60, endMinutes = 120),
            deltaMinutes = -300,
            mode = KalendarScheduleDragMode.ResizeStart,
        )
        assertEquals(0, aboveMidnight.startMinutes)
        assertEquals(120, aboveMidnight.endMinutes)
    }

    @Test
    fun dragModes_moveAndBottomResize_keepTheirExistingBehaviour() {
        val source = block(startMinutes = 540, endMinutes = 600)
        assertEquals(
            adjustedScheduleBlock(block = source, deltaMinutes = 30, resizing = false),
            adjustedScheduleBlockForDrag(block = source, deltaMinutes = 30, mode = KalendarScheduleDragMode.Move),
        )
        assertEquals(
            adjustedScheduleBlock(block = source, deltaMinutes = 30, resizing = true),
            adjustedScheduleBlockForDrag(block = source, deltaMinutes = 30, mode = KalendarScheduleDragMode.ResizeEnd),
        )
    }

    @Test
    fun blockAt_findsAPressInsideABlockAndNothingInEmptySpace() {
        val target = block(startMinutes = 540, endMinutes = 600)
        val blocks = listOf(target)
        assertSame(
            target,
            scheduleBlockAt(blocks = blocks, xPx = 50f, yPx = 570f, widthPx = 100, minutePx = minutePx),
        )
        assertNull(
            scheduleBlockAt(blocks = blocks, xPx = 50f, yPx = 300f, widthPx = 100, minutePx = minutePx),
            "above the block is empty space",
        )
        assertNull(
            scheduleBlockAt(blocks = blocks, xPx = 50f, yPx = 600f, widthPx = 100, minutePx = minutePx),
            "the end minute is exclusive",
        )
    }

    @Test
    fun blockAt_sideBySideBlocks_respectTheirOverlapColumns() {
        val left = block(startMinutes = 540, endMinutes = 600, column = 0, columns = 2)
        val right = block(startMinutes = 560, endMinutes = 620, column = 1, columns = 2)
        val blocks = listOf(left, right)
        assertSame(
            right,
            scheduleBlockAt(blocks = blocks, xPx = 75f, yPx = 610f, widthPx = 100, minutePx = minutePx),
        )
        assertNull(
            scheduleBlockAt(blocks = blocks, xPx = 25f, yPx = 610f, widthPx = 100, minutePx = minutePx),
            "the left column is already over by 610",
        )
    }
}
