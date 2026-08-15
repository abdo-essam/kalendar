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

import com.himanshoe.kalendar.foundation.datetime.KalendarTimeSource
import com.himanshoe.kalendar.foundation.schedule.KalendarHourWindow
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * The now-indicator's offset, driven from a real [KalendarTimeSource] rather than a hand-built
 * [kotlinx.datetime.LocalDateTime], so what these assert is the whole path from an instant to a
 * position on the grid — the zone included.
 *
 * The daylight-saving dates are historical on purpose: a future transition is a projection of the
 * current tzdb rules and moves when a legislature changes its mind.
 */
class KalendarNowLineTest {

    private val berlin = TimeZone.of("Europe/Berlin")
    private val tokyo = TimeZone.of("Asia/Tokyo")
    private val london = TimeZone.of("Europe/London")
    private val businessHours = KalendarHourWindow(startHour = 8, endHour = 20)

    private fun minutesAt(instant: String, timeZone: TimeZone, window: KalendarHourWindow): Int {
        val source = KalendarTimeSource(
            timeZone = timeZone,
            clock = object : Clock {
                override fun now(): Instant = Instant.parse(instant)
            },
        )
        return nowLineMinutes(now = source.now(), window = window)
    }

    private fun dateAt(instant: String, timeZone: TimeZone): LocalDate =
        KalendarTimeSource(
            timeZone = timeZone,
            clock = object : Clock {
                override fun now(): Instant = Instant.parse(instant)
            },
        ).today()

    @Test
    fun nowLineMinutes_fullDayWindow_isMinutesSinceTheWallClockMidnight() {
        assertEquals(
            540,
            minutesAt("2026-01-15T08:00:00Z", berlin, KalendarHourWindow.FullDay),
        )
    }

    @Test
    fun nowLineMinutes_businessHoursWindow_isMeasuredFromTheWindowsTopEdge() {
        assertEquals(90, minutesAt("2026-01-15T08:30:00Z", berlin, businessHours))
    }

    @Test
    fun nowLineMinutes_beforeAndAfterTheWindow_fallsOutsideItsSpan() {
        val beforeOpening = minutesAt("2026-01-15T06:00:00Z", berlin, businessHours)
        val afterClosing = minutesAt("2026-01-15T20:00:00Z", berlin, businessHours)
        assertFalse(beforeOpening in 0 until businessHours.spanMinutes, "07:00 is above an 08:00 grid")
        assertFalse(afterClosing in 0 until businessHours.spanMinutes, "21:00 is below a 20:00 grid")
    }

    @Test
    fun nowLineMinutes_atTheWindowsEdges_opensInclusiveAndClosesExclusive() {
        val opening = minutesAt("2026-01-15T07:00:00Z", berlin, businessHours)
        val closing = minutesAt("2026-01-15T19:00:00Z", berlin, businessHours)
        assertTrue(opening in 0 until businessHours.spanMinutes, "08:00 is the grid's first row")
        assertFalse(closing in 0 until businessHours.spanMinutes, "20:00 is the grid's bottom edge")
    }

    /**
     * One instant, three calendars. The line sits where the calendar's own zone puts it, which is the
     * whole point of pinning a calendar to a zone the device is not in.
     */
    @Test
    fun nowLineMinutes_oneInstantInThreeZones_landsInThreeDifferentPlaces() {
        val instant = "2026-01-15T23:00:00Z"
        val window = KalendarHourWindow.FullDay
        assertEquals(1380, minutesAt(instant, london, window))
        assertEquals(0, minutesAt(instant, berlin, window))
        assertEquals(480, minutesAt(instant, tokyo, window))
    }

    @Test
    fun today_oneInstantInTwoZones_putsTheLineOnTwoDifferentColumns() {
        val instant = "2026-01-15T23:00:00Z"
        assertEquals(LocalDate(2026, 1, 15), dateAt(instant, london))
        assertEquals(LocalDate(2026, 1, 16), dateAt(instant, tokyo))
    }

    /**
     * The grid is ruled in wall clock, so the line follows the wall clock: on the morning the German
     * clocks go forward it is at 01:59 and then, a minute of real time later, at 03:00 — skipping the
     * 02:00 row along with the hour that never happened.
     *
     * Positioning by elapsed time instead puts 03:00 two hours down the grid, an hour above every
     * block drawn for that hour, and one hour of the grid would draw nothing all day.
     */
    @Test
    fun nowLineMinutes_acrossSpringForward_followsTheWallClockOverTheSkippedHour() {
        val window = KalendarHourWindow.FullDay
        assertEquals(119, minutesAt("2024-03-31T00:59:00Z", berlin, window))
        assertEquals(180, minutesAt("2024-03-31T01:00:00Z", berlin, window))
    }

    /**
     * And back down it on the morning they go back: 02:30 happens twice, an hour of real time apart,
     * and the line is in the same place both times because the blocks drawn around it are too.
     */
    @Test
    fun nowLineMinutes_acrossFallBack_repeatsThePositionOfTheRepeatedHour() {
        val window = KalendarHourWindow.FullDay
        assertEquals(150, minutesAt("2024-10-27T00:30:00Z", berlin, window))
        assertEquals(150, minutesAt("2024-10-27T01:30:00Z", berlin, window))
        assertEquals(210, minutesAt("2024-10-27T02:30:00Z", berlin, window))
    }

    /**
     * The bottom of a full-day grid is 24 hours below its top on every date, including the two that
     * are not 24 hours long — which is what lets the same minute figure position a block and the
     * line.
     */
    @Test
    fun nowLineMinutes_lastMinuteOfADaylightSavingDay_isStillTheLastMinuteOfTheGrid() {
        val window = KalendarHourWindow.FullDay
        assertEquals(1439, minutesAt("2024-03-31T21:59:00Z", berlin, window))
        assertEquals(1439, minutesAt("2024-10-27T22:59:00Z", berlin, window))
    }
}
