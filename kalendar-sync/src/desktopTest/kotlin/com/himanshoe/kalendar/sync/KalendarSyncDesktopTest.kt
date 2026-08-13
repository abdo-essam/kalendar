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
import kotlin.io.path.createTempFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class KalendarSyncDesktopTest {

    /** A fresh, not-yet-existing `.ics` path, cleaned up automatically when the JVM exits. */
    private fun tempIcsPath(): String {
        val file = createTempFile("kalendar-sync-test", ".ics").toFile()
        file.delete()
        file.deleteOnExit()
        return file.absolutePath
    }

    @Test
    fun noPath_everyOperationReturnsNotSupported() = runBlocking {
        val sync = KalendarSync(icsFilePath = null)
        assertEquals(KalendarSyncResult.NotSupported, sync.fetchEvents(LocalDate(2026, 1, 1), LocalDate(2026, 1, 31)))
        assertEquals(
            KalendarSyncResult.NotSupported,
            sync.insertEvent(BasicKalendarSyncEvent(date = LocalDate(2026, 1, 1), eventName = "X")),
        )
    }

    @Test
    fun insertEvent_thenFetch_returnsInsertedEvent() = runBlocking {
        val sync = KalendarSync(icsFilePath = tempIcsPath())
        val insertResult = sync.insertEvent(
            BasicKalendarSyncEvent(date = LocalDate(2026, 6, 15), eventName = "Dentist"),
        )
        assertIs<KalendarSyncResult.Success<String>>(insertResult)

        val fetchResult = sync.fetchEvents(LocalDate(2026, 6, 1), LocalDate(2026, 6, 30))
        assertIs<KalendarSyncResult.Success<List<KalendarSyncEvent>>>(fetchResult)
        assertEquals(1, fetchResult.data.size)
        assertEquals("Dentist", fetchResult.data.single().eventName)
    }

    @Test
    fun insertEvent_multiDayEvent_preservesEndDateOnFetch() = runBlocking {
        val sync = KalendarSync(icsFilePath = tempIcsPath())
        sync.insertEvent(
            BasicKalendarSyncEvent(
                date = LocalDate(2026, 6, 15),
                endDate = LocalDate(2026, 6, 17),
                eventName = "Vacation",
            ),
        )

        val fetchResult = sync.fetchEvents(LocalDate(2026, 6, 1), LocalDate(2026, 6, 30))
        assertIs<KalendarSyncResult.Success<List<KalendarSyncEvent>>>(fetchResult)
        assertEquals(LocalDate(2026, 6, 17), fetchResult.data.single().endDate)
    }

    @Test
    fun updateEvent_preservesEndDate() = runBlocking {
        val sync = KalendarSync(icsFilePath = tempIcsPath())
        val id = (
            sync.insertEvent(
                BasicKalendarSyncEvent(
                    date = LocalDate(2026, 6, 15),
                    endDate = LocalDate(2026, 6, 16),
                    eventName = "Trip",
                ),
            ) as KalendarSyncResult.Success<String>
            ).data

        sync.updateEvent(
            id,
            BasicKalendarSyncEvent(
                date = LocalDate(2026, 6, 15),
                endDate = LocalDate(2026, 6, 18),
                eventName = "Longer Trip",
            ),
        )

        val fetchResult = sync.fetchEvents(LocalDate(2026, 6, 1), LocalDate(2026, 6, 30))
        assertIs<KalendarSyncResult.Success<List<KalendarSyncEvent>>>(fetchResult)
        val updated = fetchResult.data.single()
        assertEquals("Longer Trip", updated.eventName)
        assertEquals(LocalDate(2026, 6, 18), updated.endDate)
    }

    @Test
    fun deleteEvent_removesFromFile() = runBlocking {
        val sync = KalendarSync(icsFilePath = tempIcsPath())
        val id = (
            sync.insertEvent(BasicKalendarSyncEvent(date = LocalDate(2026, 6, 15), eventName = "Gone")) as
                KalendarSyncResult.Success<String>
            ).data

        sync.deleteEvent(id)

        val fetchResult = sync.fetchEvents(LocalDate(2026, 6, 1), LocalDate(2026, 6, 30))
        assertIs<KalendarSyncResult.Success<List<KalendarSyncEvent>>>(fetchResult)
        assertTrue(fetchResult.data.isEmpty())
    }

    @Test
    fun fetchEvents_noFileYet_returnsEmptySuccess() = runBlocking {
        val sync = KalendarSync(icsFilePath = tempIcsPath())
        val fetchResult = sync.fetchEvents(LocalDate(2026, 1, 1), LocalDate(2026, 12, 31))
        assertIs<KalendarSyncResult.Success<List<KalendarSyncEvent>>>(fetchResult)
        assertTrue(fetchResult.data.isEmpty())
    }

    @Test
    fun insertEvent_withRemindersAndAttendees_returnsThemOnFetch() = runBlocking {
        val sync = KalendarSync(icsFilePath = tempIcsPath())
        val reminders = listOf(KalendarReminder(minutesBefore = 30), KalendarReminder(minutesBefore = 90))
        val attendees = listOf(
            KalendarAttendee(email = "jane@example.com", name = "Jane Doe"),
            KalendarAttendee(email = "anon@example.com"),
        )
        sync.insertEvent(
            BasicKalendarSyncEvent(
                date = LocalDate(2026, 6, 15),
                eventName = "Kickoff",
                reminders = reminders,
                attendees = attendees,
            ),
        )

        val fetchResult = sync.fetchEvents(LocalDate(2026, 6, 1), LocalDate(2026, 6, 30))
        assertIs<KalendarSyncResult.Success<List<KalendarSyncEvent>>>(fetchResult)
        val fetched = fetchResult.data.single()
        assertEquals(reminders, fetched.reminders)
        assertEquals(attendees, fetched.attendees)
    }

    @Test
    fun updateEvent_preservesRemindersAndAttendees() = runBlocking {
        val sync = KalendarSync(icsFilePath = tempIcsPath())
        val id = (
            sync.insertEvent(BasicKalendarSyncEvent(date = LocalDate(2026, 6, 15), eventName = "Kickoff")) as
                KalendarSyncResult.Success<String>
            ).data

        sync.updateEvent(
            id,
            BasicKalendarSyncEvent(
                date = LocalDate(2026, 6, 15),
                eventName = "Kickoff v2",
                reminders = listOf(KalendarReminder(minutesBefore = 5)),
                attendees = listOf(KalendarAttendee(email = "lead@example.com", name = "Lead")),
            ),
        )

        val fetchResult = sync.fetchEvents(LocalDate(2026, 6, 1), LocalDate(2026, 6, 30))
        assertIs<KalendarSyncResult.Success<List<KalendarSyncEvent>>>(fetchResult)
        val updated = fetchResult.data.single()
        assertEquals(listOf(KalendarReminder(minutesBefore = 5)), updated.reminders)
        assertEquals(listOf(KalendarAttendee(email = "lead@example.com", name = "Lead")), updated.attendees)
    }

    @Test
    fun listCalendars_withPath_returnsSingleCalendarNamedAfterFile() = runBlocking {
        val path = tempIcsPath()
        val result = KalendarSync(icsFilePath = path).listCalendars()
        assertIs<KalendarSyncResult.Success<List<KalendarSyncCalendar>>>(result)
        val calendar = result.data.single()
        assertEquals("default", calendar.id)
        assertEquals(path.substringAfterLast('/'), calendar.name)
        assertNull(calendar.colorArgb)
    }

    @Test
    fun listCalendars_withoutPath_returnsNotSupported() = runBlocking {
        assertEquals(KalendarSyncResult.NotSupported, KalendarSync(icsFilePath = null).listCalendars())
    }

    @Test
    fun hasPermission_withPath_isTrue() = runBlocking {
        assertTrue(KalendarSync(icsFilePath = tempIcsPath()).hasPermission())
    }

    @Test
    fun hasPermission_withoutPath_isFalse() = runBlocking {
        assertEquals(false, KalendarSync(icsFilePath = null).hasPermission())
    }

    @Test
    fun singleDayEvent_endDateStaysNullAfterRoundTrip() = runBlocking {
        val sync = KalendarSync(icsFilePath = tempIcsPath())
        sync.insertEvent(BasicKalendarSyncEvent(date = LocalDate(2026, 6, 15), eventName = "Single"))

        val fetchResult = sync.fetchEvents(LocalDate(2026, 6, 1), LocalDate(2026, 6, 30))
        assertIs<KalendarSyncResult.Success<List<KalendarSyncEvent>>>(fetchResult)
        assertNull(fetchResult.data.single().endDate)
    }
}
