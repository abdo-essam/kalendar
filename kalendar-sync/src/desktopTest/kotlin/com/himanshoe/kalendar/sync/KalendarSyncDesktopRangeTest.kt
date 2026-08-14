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

package com.himanshoe.kalendar.sync

import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.io.path.createTempFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Covers the range predicate of the Desktop provider's `fetchEvents`: an event must come back
 * from every window it overlaps, not only from the window containing its first day.
 */
class KalendarSyncDesktopRangeTest {

    /** A fresh, not-yet-existing `.ics` path, cleaned up automatically when the JVM exits. */
    private fun tempIcsPath(): String {
        val file = createTempFile("kalendar-sync-range-test", ".ics").toFile()
        file.delete()
        file.deleteOnExit()
        return file.absolutePath
    }

    @Test
    fun fetchEvents_multiDayEventCrossingMonthBoundary_returnedByBothMonths() = runBlocking {
        val sync = KalendarSync(icsFilePath = tempIcsPath())
        sync.insertEvent(
            BasicKalendarSyncEvent(
                date = LocalDate(2026, 8, 28),
                endDate = LocalDate(2026, 9, 5),
                eventName = "Conference",
            ),
        )

        val august = sync.fetchEvents(LocalDate(2026, 8, 1), LocalDate(2026, 8, 31))
        assertIs<KalendarSyncResult.Success<List<KalendarSyncEvent>>>(august)
        assertEquals("Conference", august.data.single().eventName)

        val september = sync.fetchEvents(LocalDate(2026, 9, 1), LocalDate(2026, 9, 30))
        assertIs<KalendarSyncResult.Success<List<KalendarSyncEvent>>>(september)
        assertEquals("Conference", september.data.single().eventName)
    }

    @Test
    fun fetchEvents_rangeEntirelyInsideMultiDayEvent_returnsEvent() = runBlocking {
        val sync = KalendarSync(icsFilePath = tempIcsPath())
        sync.insertEvent(
            BasicKalendarSyncEvent(
                date = LocalDate(2026, 8, 1),
                endDate = LocalDate(2026, 8, 14),
                eventName = "Sabbatical",
            ),
        )

        val middleWeek = sync.fetchEvents(LocalDate(2026, 8, 5), LocalDate(2026, 8, 11))
        assertIs<KalendarSyncResult.Success<List<KalendarSyncEvent>>>(middleWeek)
        assertEquals("Sabbatical", middleWeek.data.single().eventName)
    }

    @Test
    fun fetchEvents_singleDayEventsAtRangeEdges_areReturned() = runBlocking {
        val sync = KalendarSync(icsFilePath = tempIcsPath())
        sync.insertEvent(BasicKalendarSyncEvent(date = LocalDate(2026, 8, 1), eventName = "First"))
        sync.insertEvent(BasicKalendarSyncEvent(date = LocalDate(2026, 8, 31), eventName = "Last"))

        val august = sync.fetchEvents(LocalDate(2026, 8, 1), LocalDate(2026, 8, 31))
        assertIs<KalendarSyncResult.Success<List<KalendarSyncEvent>>>(august)
        assertEquals(setOf("First", "Last"), august.data.map { it.eventName }.toSet())
    }

    @Test
    fun fetchEvents_timedEventsAtRangeEdges_areReturned() = runBlocking {
        val sync = KalendarSync(icsFilePath = tempIcsPath())
        sync.insertEvent(
            BasicKalendarSyncEvent(
                date = LocalDate(2026, 8, 1),
                eventName = "Early standup",
                startTime = LocalTime(9, 0),
                endTime = LocalTime(9, 30),
            ),
        )
        sync.insertEvent(
            BasicKalendarSyncEvent(
                date = LocalDate(2026, 8, 31),
                eventName = "Late retro",
                startTime = LocalTime(17, 0),
                endTime = LocalTime(18, 0),
            ),
        )

        val august = sync.fetchEvents(LocalDate(2026, 8, 1), LocalDate(2026, 8, 31))
        assertIs<KalendarSyncResult.Success<List<KalendarSyncEvent>>>(august)
        assertEquals(setOf("Early standup", "Late retro"), august.data.map { it.eventName }.toSet())
    }

    @Test
    fun fetchEvents_eventEntirelyOutsideRange_isNotReturned() = runBlocking {
        val sync = KalendarSync(icsFilePath = tempIcsPath())
        sync.insertEvent(
            BasicKalendarSyncEvent(
                date = LocalDate(2026, 6, 1),
                endDate = LocalDate(2026, 6, 10),
                eventName = "Old news",
            ),
        )
        sync.insertEvent(BasicKalendarSyncEvent(date = LocalDate(2026, 10, 1), eventName = "Future"))

        val august = sync.fetchEvents(LocalDate(2026, 8, 1), LocalDate(2026, 8, 31))
        assertIs<KalendarSyncResult.Success<List<KalendarSyncEvent>>>(august)
        assertTrue(august.data.isEmpty())
    }

    @Test
    fun fetchEvents_recurringSeriesStartingBeforeWindow_isStillReturned() = runBlocking {
        val sync = KalendarSync(icsFilePath = tempIcsPath())
        sync.insertEvent(
            BasicKalendarSyncEvent(
                date = LocalDate(2026, 1, 5),
                eventName = "Weekly standup",
                recurrenceRule = KalendarRule(frequency = KalendarRecurrenceFrequency.WEEKLY),
            ),
        )

        // The stored template is the only row that exists, so dropping it for falling outside
        // the window would hide every occurrence inside it. Android already keeps these.
        val march = sync.fetchEvents(LocalDate(2026, 3, 1), LocalDate(2026, 3, 31))
        assertIs<KalendarSyncResult.Success<List<KalendarSyncEvent>>>(march)
        assertEquals("Weekly standup", march.data.single().eventName)
    }

    @Test
    fun fetchEvents_recurringSeriesStartingAfterWindow_isNotReturned() = runBlocking {
        val sync = KalendarSync(icsFilePath = tempIcsPath())
        sync.insertEvent(
            BasicKalendarSyncEvent(
                date = LocalDate(2026, 6, 1),
                eventName = "Future series",
                recurrenceRule = KalendarRule(frequency = KalendarRecurrenceFrequency.WEEKLY),
            ),
        )

        val march = sync.fetchEvents(LocalDate(2026, 3, 1), LocalDate(2026, 3, 31))
        assertIs<KalendarSyncResult.Success<List<KalendarSyncEvent>>>(march)
        assertTrue(march.data.isEmpty(), "a series starting after the window must not be returned")
    }
}
