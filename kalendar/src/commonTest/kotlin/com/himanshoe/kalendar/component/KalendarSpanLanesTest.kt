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

package com.himanshoe.kalendar.component

import com.himanshoe.kalendar.foundation.event.BasicKalendarEvent
import com.himanshoe.kalendar.foundation.event.KalendarEvent
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

/**
 * Lane packing for multi-day events.
 *
 * The property every one of these is circling is that a lane belongs to the *event*, for its whole
 * run — that is what makes a three-day conference one bar rather than three dots at three heights.
 * A lane assignment made per day would be shorter code and would break the moment a span underneath
 * another one ended first.
 */
class KalendarSpanLanesTest {

    @Test
    fun singleDayEvent_isNotASpan() {
        assertNull(event(day = 9).spanEndOrNull())
        assertNull(event(day = 9, endDay = 9).spanEndOrNull())
    }

    /** A backwards `endDate` is what `byDateExpandingSpans` also treats as a single day. */
    @Test
    fun endDateBeforeStart_isNotASpan() {
        assertNull(event(day = 9, endDay = 8).spanEndOrNull())
    }

    @Test
    fun multiDayEvent_reportsItsLastDay() {
        assertEquals(march(11), event(day = 9, endDay = 11).spanEndOrNull())
    }

    /**
     * A span longer than the expansion guard is cut to it. Without this one malformed `endDate` in
     * the far future occupies a lane on every date there is, and every later span is pushed into a
     * lane no cell has room for.
     */
    @Test
    fun absurdlyLongSpan_isTruncatedToTheExpansionGuard() {
        val event = BasicKalendarEvent(
            date = march(9),
            endDate = LocalDate(9999, 1, 1),
            eventName = "Forever",
        )
        assertEquals(march(9).plus(MAX_SPAN_DAYS, DateTimeUnit.DAY), event.spanEndOrNull())
    }

    @Test
    fun overlappingSpans_takeDifferentLanes() {
        val first = event(day = 9, endDay = 13)
        val second = event(day = 11, endDay = 18)
        val lanes = KalendarSpanLanes.of(events = listOf(first, second), maxLanes = LANES)

        assertEquals(0, lanes.laneOf(first))
        assertEquals(1, lanes.laneOf(second))
        assertEquals(2, lanes.laneCount)
    }

    /**
     * The regression this whole indirection exists for.
     *
     * On the 12th both spans are on screen, on the 14th only the second is. Reading a lane off the
     * day's own event list would put the second span at index 1 on the 12th and index 0 on the
     * 14th, and its bar would step up a row halfway through. The lane is asked of the event, so
     * there is only ever one answer.
     */
    @Test
    fun aSpanKeepsOneLaneOnEveryDayItCovers() {
        val ending = event(day = 9, endDay = 13)
        val continuing = event(day = 11, endDay = 18)
        val lanes = KalendarSpanLanes.of(events = listOf(ending, continuing), maxLanes = LANES)

        val whileBothRun = lanes.laneOf(continuing)
        val afterTheOtherEnded = lanes.laneOf(continuing)
        assertEquals(whileBothRun, afterTheOtherEnded)
        assertNotEquals(lanes.laneOf(ending), lanes.laneOf(continuing))
    }

    @Test
    fun spansThatNeverOverlap_shareTheTopLane() {
        val first = event(day = 9, endDay = 11)
        val second = event(day = 12, endDay = 14)
        val lanes = KalendarSpanLanes.of(events = listOf(first, second), maxLanes = LANES)

        assertEquals(0, lanes.laneOf(first))
        assertEquals(0, lanes.laneOf(second))
        assertEquals(1, lanes.laneCount)
    }

    /** Touching on one day is overlapping: both need a bar on that day. */
    @Test
    fun spansMeetingOnOneDay_doNotShareALane() {
        val first = event(day = 9, endDay = 11)
        val second = event(day = 11, endDay = 14)
        val lanes = KalendarSpanLanes.of(events = listOf(first, second), maxLanes = LANES)

        assertNotEquals(lanes.laneOf(first), lanes.laneOf(second))
    }

    @Test
    fun theLongerSpan_takesTheHigherLaneWhenTwoStartTogether() {
        val shorter = event(day = 9, endDay = 10)
        val longer = event(day = 9, endDay = 20)
        val lanes = KalendarSpanLanes.of(events = listOf(shorter, longer), maxLanes = LANES)

        assertEquals(0, lanes.laneOf(longer))
        assertEquals(1, lanes.laneOf(shorter))
    }

    /**
     * Past the budget an event gets no lane at all, which is how the day cell knows to draw it as
     * the indicator dot it drew before span bars existed rather than to drop it.
     */
    @Test
    fun spansPastTheLaneBudget_getNoLane() {
        val first = event(day = 9, endDay = 20)
        val second = event(day = 10, endDay = 20)
        val third = event(day = 11, endDay = 20)
        val lanes = KalendarSpanLanes.of(events = listOf(first, second, third), maxLanes = LANES)

        assertEquals(0, lanes.laneOf(first))
        assertEquals(1, lanes.laneOf(second))
        assertNull(lanes.laneOf(third))
        assertEquals(LANES, lanes.laneCount)
    }

    /**
     * A lane the budget has no room for must not be counted into [KalendarSpanLanes.laneCount]
     * either: that number is the height every cell in the grid reserves under its day number, and
     * reserving space for a bar nothing will draw shrinks all 42 day numbers for nothing.
     */
    @Test
    fun laneCount_isTheDeepestOverlapActuallyDrawn() {
        val spread = listOf(
            event(day = 9, endDay = 10),
            event(day = 12, endDay = 13),
            event(day = 16, endDay = 17),
        )
        assertEquals(1, KalendarSpanLanes.of(events = spread, maxLanes = LANES).laneCount)
    }

    @Test
    fun noSpans_reserveNoLanes() {
        val singles = listOf(event(day = 9), event(day = 10))
        assertEquals(0, KalendarSpanLanes.of(events = singles, maxLanes = LANES).laneCount)
        assertEquals(0, KalendarSpanLanes.of(events = emptyList(), maxLanes = LANES).laneCount)
    }

    @Test
    fun aZeroBudget_turnsSpanBarsOff() {
        val spans = listOf(event(day = 9, endDay = 13))
        val lanes = KalendarSpanLanes.of(events = spans, maxLanes = 0)

        assertEquals(0, lanes.laneCount)
        assertNull(lanes.laneOf(spans.first()))
    }

    /**
     * Lanes may not depend on where an event happened to sit in the list, or a calendar that sorts
     * its events differently gets a different picture of the same month.
     */
    @Test
    fun theOrderOfTheEventList_doesNotMoveALane() {
        val first = event(day = 9, endDay = 13)
        val second = event(day = 11, endDay = 18)
        val third = event(day = 20, endDay = 22)
        val forwards = KalendarSpanLanes.of(events = listOf(first, second, third), maxLanes = LANES)
        val backwards = KalendarSpanLanes.of(events = listOf(third, second, first), maxLanes = LANES)

        listOf(first, second, third).forEach { event ->
            assertEquals(forwards.laneOf(event), backwards.laneOf(event), event.eventName)
        }
    }

    private fun event(day: Int, endDay: Int? = null): KalendarEvent = BasicKalendarEvent(
        date = march(day),
        endDate = endDay?.let { march(it) },
        eventName = "day $day to $endDay",
    )

    private fun march(day: Int): LocalDate = LocalDate(YEAR, MONTH, day)

    private companion object {
        const val YEAR = 2026
        const val MONTH = 3
        const val LANES = 2

        /** Mirrors the expansion guard in `byDateExpandingSpans`. */
        const val MAX_SPAN_DAYS = 366
    }
}
