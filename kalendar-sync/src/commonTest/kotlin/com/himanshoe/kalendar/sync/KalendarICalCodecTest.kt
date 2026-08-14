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

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class KalendarICalCodecTest {

    @Test
    fun encode_timedEvent_containsFloatingDtStartAndDtEnd() {
        val event = BasicKalendarSyncEvent(
            date = LocalDate(2026, 8, 12),
            eventName = "Standup",
            startTime = LocalTime(9, 0),
            endTime = LocalTime(9, 30),
        )
        val ics = KalendarICalCodec.encode(listOf(event))
        assertTrue(ics.contains("DTSTART:20260812T090000\n"))
        assertTrue(ics.contains("DTEND:20260812T093000\n"))
        assertTrue(ics.contains("SUMMARY:Standup"))
    }

    @Test
    fun encode_timedEvent_neverEmitsUtcSuffixOnWallClockTimes() {
        val event = BasicKalendarSyncEvent(
            date = LocalDate(2026, 8, 12),
            eventName = "Standup",
            startTime = LocalTime(9, 0),
            endTime = LocalTime(9, 30),
        )
        val ics = KalendarICalCodec.encode(listOf(event))
        assertTrue(!ics.contains("090000Z") && !ics.contains("093000Z"))
    }

    @Test
    fun decode_legacyZSuffixedDateTimes_treatedAsFloatingLocalTime() {
        val ics = """
            BEGIN:VCALENDAR
            BEGIN:VEVENT
            SUMMARY:Legacy
            DTSTART:20260812T090000Z
            DTEND:20260812T093000Z
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()
        val decoded = KalendarICalCodec.decode(ics).single()
        assertEquals(LocalDate(2026, 8, 12), decoded.date)
        assertEquals(LocalTime(9, 0), decoded.startTime)
        assertEquals(LocalTime(9, 30), decoded.endTime)
    }

    @Test
    fun encode_singleDayAllDayEvent_dtEndIsNextDay() {
        val event = BasicKalendarSyncEvent(date = LocalDate(2026, 8, 12), eventName = "Holiday")
        val ics = KalendarICalCodec.encode(listOf(event))
        assertTrue(ics.contains("DTSTART;VALUE=DATE:20260812"))
        assertTrue(ics.contains("DTEND;VALUE=DATE:20260813"))
    }

    @Test
    fun encode_multiDayAllDayEvent_dtEndIsDayAfterEndDate() {
        val event = BasicKalendarSyncEvent(
            date = LocalDate(2026, 8, 12),
            eventName = "Conference",
            endDate = LocalDate(2026, 8, 14),
        )
        val ics = KalendarICalCodec.encode(listOf(event))
        assertTrue(ics.contains("DTSTART;VALUE=DATE:20260812"))
        assertTrue(ics.contains("DTEND;VALUE=DATE:20260815"))
    }

    @Test
    fun decode_singleDayAllDayEvent_endDateIsNull() {
        val ics = KalendarICalCodec.encode(
            listOf(BasicKalendarSyncEvent(date = LocalDate(2026, 8, 12), eventName = "Holiday")),
        )
        val decoded = KalendarICalCodec.decode(ics).single()
        assertEquals(LocalDate(2026, 8, 12), decoded.date)
        assertNull(decoded.endDate)
    }

    @Test
    fun decode_multiDayAllDayEvent_endDateIsInclusiveLastDay() {
        val ics = KalendarICalCodec.encode(
            listOf(
                BasicKalendarSyncEvent(
                    date = LocalDate(2026, 8, 12),
                    eventName = "Conference",
                    endDate = LocalDate(2026, 8, 14),
                ),
            ),
        )
        val decoded = KalendarICalCodec.decode(ics).single()
        assertEquals(LocalDate(2026, 8, 12), decoded.date)
        assertEquals(LocalDate(2026, 8, 14), decoded.endDate)
    }

    @Test
    fun decode_timedEvent_endDateIsNull() {
        val ics = KalendarICalCodec.encode(
            listOf(
                BasicKalendarSyncEvent(
                    date = LocalDate(2026, 8, 12),
                    eventName = "Standup",
                    startTime = LocalTime(9, 0),
                    endTime = LocalTime(9, 30),
                ),
            ),
        )
        val decoded = KalendarICalCodec.decode(ics).single()
        assertNull(decoded.endDate)
        assertEquals(LocalTime(9, 0), decoded.startTime)
        assertEquals(LocalTime(9, 30), decoded.endTime)
    }

    @Test
    fun roundTrip_preservesNameDescriptionAndDates() {
        val original = BasicKalendarSyncEvent(
            date = LocalDate(2026, 3, 1),
            eventName = "Team Offsite",
            eventDescription = "Quarterly planning session",
            endDate = LocalDate(2026, 3, 3),
        )
        val decoded = KalendarICalCodec.decode(KalendarICalCodec.encode(listOf(original))).single()
        assertEquals(original.date, decoded.date)
        assertEquals(original.eventName, decoded.eventName)
        assertEquals(original.eventDescription, decoded.eventDescription)
        assertEquals(original.endDate, decoded.endDate)
    }

    @Test
    fun roundTrip_escapesAndUnescapesSpecialCharacters() {
        val original = BasicKalendarSyncEvent(
            date = LocalDate(2026, 8, 12),
            eventName = "Comma, semicolon; backslash\\",
            eventDescription = "Line one\nLine two",
        )
        val decoded = KalendarICalCodec.decode(KalendarICalCodec.encode(listOf(original))).single()
        assertEquals(original.eventName, decoded.eventName)
        assertEquals(original.eventDescription, decoded.eventDescription)
    }

    @Test
    fun roundTrip_preservesRecurrenceRule() {
        val rule = KalendarRule(
            frequency = KalendarRecurrenceFrequency.WEEKLY,
            interval = 2,
            byDay = listOf(KalendarWeekDay.MONDAY, KalendarWeekDay.WEDNESDAY),
        )
        val original = BasicKalendarSyncEvent(
            date = LocalDate(2026, 1, 5),
            eventName = "Sync",
            recurrenceRule = rule,
        )
        val decoded = KalendarICalCodec.decode(KalendarICalCodec.encode(listOf(original))).single()
        assertEquals(rule, decoded.recurrenceRule)
    }

    @Test
    fun encode_recurringEventWithExceptions_includesExdateLine() {
        val original = BasicKalendarSyncEvent(
            date = LocalDate(2026, 1, 5),
            eventName = "Standup",
            recurrenceRule = KalendarRule(frequency = KalendarRecurrenceFrequency.DAILY),
            exceptionDates = listOf(LocalDate(2026, 1, 7), LocalDate(2026, 1, 9)),
        )
        val ics = KalendarICalCodec.encode(listOf(original))
        assertTrue(ics.contains("EXDATE;VALUE=DATE:20260107,20260109"))
    }

    @Test
    fun encode_timedRecurringEventWithExceptions_emitsDateTimeExdateMatchingDtStart() {
        val original = BasicKalendarSyncEvent(
            date = LocalDate(2026, 1, 5),
            eventName = "Standup",
            startTime = LocalTime(9, 0),
            endTime = LocalTime(9, 30),
            recurrenceRule = KalendarRule(frequency = KalendarRecurrenceFrequency.DAILY),
            exceptionDates = listOf(LocalDate(2026, 1, 7), LocalDate(2026, 1, 9)),
        )
        val ics = KalendarICalCodec.encode(listOf(original))
        assertTrue(ics.contains("EXDATE:20260107T090000,20260109T090000"))
        assertTrue(!ics.contains("EXDATE;VALUE=DATE"))
    }

    @Test
    fun roundTrip_timedRecurringEventWithExceptions_preservesExceptionDates() {
        val original = BasicKalendarSyncEvent(
            date = LocalDate(2026, 1, 5),
            eventName = "Standup",
            startTime = LocalTime(9, 0),
            endTime = LocalTime(9, 30),
            recurrenceRule = KalendarRule(frequency = KalendarRecurrenceFrequency.DAILY),
            exceptionDates = listOf(LocalDate(2026, 1, 7), LocalDate(2026, 1, 9)),
        )
        val decoded = KalendarICalCodec.decode(KalendarICalCodec.encode(listOf(original))).single()
        assertEquals(listOf(LocalDate(2026, 1, 7), LocalDate(2026, 1, 9)), decoded.exceptionDates)
    }

    @Test
    fun roundTrip_preservesExceptionDates() {
        val original = BasicKalendarSyncEvent(
            date = LocalDate(2026, 1, 5),
            eventName = "Standup",
            recurrenceRule = KalendarRule(frequency = KalendarRecurrenceFrequency.DAILY),
            exceptionDates = listOf(LocalDate(2026, 1, 7), LocalDate(2026, 1, 9)),
        )
        val decoded = KalendarICalCodec.decode(KalendarICalCodec.encode(listOf(original))).single()
        assertEquals(listOf(LocalDate(2026, 1, 7), LocalDate(2026, 1, 9)), decoded.exceptionDates)
    }

    @Test
    fun roundTrip_noExceptionDates_decodesEmptyList() {
        val original = BasicKalendarSyncEvent(date = LocalDate(2026, 1, 5), eventName = "One-off")
        val decoded = KalendarICalCodec.decode(KalendarICalCodec.encode(listOf(original))).single()
        assertTrue(decoded.exceptionDates.isEmpty())
    }

    @Test
    fun encode_multipleEvents_decodesSameCount() {
        val events = listOf(
            BasicKalendarSyncEvent(date = LocalDate(2026, 1, 1), eventName = "One"),
            BasicKalendarSyncEvent(date = LocalDate(2026, 1, 2), eventName = "Two"),
            BasicKalendarSyncEvent(date = LocalDate(2026, 1, 3), eventName = "Three"),
        )
        val decoded = KalendarICalCodec.decode(KalendarICalCodec.encode(events))
        assertEquals(3, decoded.size)
    }

    @Test
    fun decode_emptyCalendar_returnsEmptyList() {
        val ics = KalendarICalCodec.encode(emptyList())
        assertTrue(KalendarICalCodec.decode(ics).isEmpty())
    }

    @Test
    fun encode_reminder_emitsValarmBlockWithMinuteTrigger() {
        val event = BasicKalendarSyncEvent(
            date = LocalDate(2026, 8, 12),
            eventName = "Standup",
            reminders = listOf(KalendarReminder(minutesBefore = 30)),
        )
        val ics = KalendarICalCodec.encode(listOf(event))
        assertTrue(ics.contains("BEGIN:VALARM"))
        assertTrue(ics.contains("ACTION:DISPLAY"))
        assertTrue(ics.contains("TRIGGER:-PT30M"))
        assertTrue(ics.contains("END:VALARM"))
    }

    @Test
    fun roundTrip_preservesMultipleReminders() {
        val original = BasicKalendarSyncEvent(
            date = LocalDate(2026, 8, 12),
            eventName = "Standup",
            reminders = listOf(KalendarReminder(minutesBefore = 10), KalendarReminder(minutesBefore = 90)),
        )
        val decoded = KalendarICalCodec.decode(KalendarICalCodec.encode(listOf(original))).single()
        assertEquals(original.reminders, decoded.reminders)
    }

    @Test
    fun decode_hourAndMixedTriggers_convertToMinutes() {
        val ics = """
            BEGIN:VCALENDAR
            BEGIN:VEVENT
            SUMMARY:Alarmed
            DTSTART;VALUE=DATE:20260812
            BEGIN:VALARM
            ACTION:DISPLAY
            TRIGGER:-PT1H
            END:VALARM
            BEGIN:VALARM
            ACTION:DISPLAY
            TRIGGER:-PT1H30M
            END:VALARM
            BEGIN:VALARM
            ACTION:DISPLAY
            TRIGGER:NOT_A_DURATION
            END:VALARM
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()
        val decoded = KalendarICalCodec.decode(ics).single()
        assertEquals(listOf(60, 90), decoded.reminders.map { it.minutesBefore })
    }

    @Test
    fun decode_dayBasedAndSecondsBearingTriggers_convertToMinutes() {
        val ics = """
            BEGIN:VCALENDAR
            BEGIN:VEVENT
            SUMMARY:Alarmed
            DTSTART;VALUE=DATE:20260812
            BEGIN:VALARM
            ACTION:DISPLAY
            TRIGGER:-P1D
            END:VALARM
            BEGIN:VALARM
            ACTION:DISPLAY
            TRIGGER:-P1DT2H30M
            END:VALARM
            BEGIN:VALARM
            ACTION:DISPLAY
            TRIGGER:-PT30M0S
            END:VALARM
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()
        val decoded = KalendarICalCodec.decode(ics).single()
        assertEquals(listOf(1440, 1590, 30), decoded.reminders.map { it.minutesBefore })
    }

    @Test
    fun decode_positiveOrAbsoluteTriggers_areDropped() {
        val ics = """
            BEGIN:VCALENDAR
            BEGIN:VEVENT
            SUMMARY:Alarmed
            DTSTART;VALUE=DATE:20260812
            BEGIN:VALARM
            ACTION:DISPLAY
            TRIGGER:PT15M
            END:VALARM
            BEGIN:VALARM
            ACTION:DISPLAY
            TRIGGER;VALUE=DATE-TIME:20260812T083000Z
            END:VALARM
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()
        val decoded = KalendarICalCodec.decode(ics).single()
        assertTrue(decoded.reminders.isEmpty())
    }

    @Test
    fun roundTrip_attendeeWithName_preservesNameAndEmail() {
        val original = BasicKalendarSyncEvent(
            date = LocalDate(2026, 8, 12),
            eventName = "Planning",
            attendees = listOf(KalendarAttendee(email = "jane@example.com", name = "Jane Doe")),
        )
        val ics = KalendarICalCodec.encode(listOf(original))
        assertTrue(ics.contains("ATTENDEE;CN=Jane Doe:mailto:jane@example.com"))
        val decoded = KalendarICalCodec.decode(ics).single()
        assertEquals(original.attendees, decoded.attendees)
    }

    @Test
    fun roundTrip_attendeeWithoutName_omitsCnParameter() {
        val original = BasicKalendarSyncEvent(
            date = LocalDate(2026, 8, 12),
            eventName = "Planning",
            attendees = listOf(KalendarAttendee(email = "anon@example.com")),
        )
        val ics = KalendarICalCodec.encode(listOf(original))
        assertTrue(ics.contains("ATTENDEE:mailto:anon@example.com"))
        val decoded = KalendarICalCodec.decode(ics).single()
        assertEquals(original.attendees, decoded.attendees)
    }

    @Test
    fun roundTrip_attendeeNameWithComma_escapesAndUnescapes() {
        val original = BasicKalendarSyncEvent(
            date = LocalDate(2026, 8, 12),
            eventName = "Planning",
            attendees = listOf(KalendarAttendee(email = "doe@example.com", name = "Doe, Jane")),
        )
        val ics = KalendarICalCodec.encode(listOf(original))
        assertTrue(ics.contains("ATTENDEE;CN=Doe\\, Jane:mailto:doe@example.com"))
        val decoded = KalendarICalCodec.decode(ics).single()
        assertEquals(original.attendees, decoded.attendees)
    }

    @Test
    fun decode_uppercaseMailtoPrefix_isStripped() {
        val ics = """
            BEGIN:VCALENDAR
            BEGIN:VEVENT
            SUMMARY:Planning
            DTSTART;VALUE=DATE:20260812
            ATTENDEE;CN=Jane:MAILTO:jane@example.com
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()
        val decoded = KalendarICalCodec.decode(ics).single()
        assertEquals(listOf(KalendarAttendee(email = "jane@example.com", name = "Jane")), decoded.attendees)
    }

    @Test
    fun roundTrip_noRemindersOrAttendees_decodesEmptyLists() {
        val original = BasicKalendarSyncEvent(date = LocalDate(2026, 8, 12), eventName = "Plain")
        val decoded = KalendarICalCodec.decode(KalendarICalCodec.encode(listOf(original))).single()
        assertTrue(decoded.reminders.isEmpty())
        assertTrue(decoded.attendees.isEmpty())
    }

    @Test
    fun decode_malformedContent_ignoresGarbageLines() {
        val ics = """
            BEGIN:VCALENDAR
            NOT_A_VALID_LINE_WITHOUT_COLON
            BEGIN:VEVENT
            UID:abc123
            SUMMARY:Valid Event
            DTSTART;VALUE=DATE:20260812
            DTEND;VALUE=DATE:20260813
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()
        val decoded = KalendarICalCodec.decode(ics)
        assertEquals(1, decoded.size)
        assertEquals("Valid Event", decoded.single().eventName)
    }

    @Test
    fun encode_timedEventWithEndDate_dtEndCarriesTheEndDate() {
        val ics = KalendarICalCodec.encode(
            listOf(
                BasicKalendarSyncEvent(
                    date = LocalDate(2026, 5, 1),
                    eventName = "Night shift",
                    startTime = LocalTime(22, 0),
                    endTime = LocalTime(6, 0),
                    endDate = LocalDate(2026, 5, 2),
                ),
            ),
        )
        assertTrue(ics.contains("DTSTART:20260501T220000"), ics)
        assertTrue(ics.contains("DTEND:20260502T060000"), ics)
    }

    @Test
    fun encode_timedEventEndingBeforeItStarts_rollsDtEndToNextDay() {
        val ics = KalendarICalCodec.encode(
            listOf(
                BasicKalendarSyncEvent(
                    date = LocalDate(2026, 5, 1),
                    eventName = "Night shift",
                    startTime = LocalTime(22, 0),
                    endTime = LocalTime(6, 0),
                ),
            ),
        )
        assertTrue(ics.contains("DTSTART:20260501T220000"), ics)
        assertTrue(ics.contains("DTEND:20260502T060000"), ics)
    }

    @Test
    fun roundTrip_timedEventCrossingMidnight_preservesEndDate() {
        val event = BasicKalendarSyncEvent(
            date = LocalDate(2026, 5, 1),
            eventName = "Night shift",
            startTime = LocalTime(22, 0),
            endTime = LocalTime(6, 0),
            endDate = LocalDate(2026, 5, 2),
        )
        val decoded = KalendarICalCodec.decode(KalendarICalCodec.encode(listOf(event))).single()
        assertEquals(LocalDate(2026, 5, 1), decoded.date)
        assertEquals(LocalDate(2026, 5, 2), decoded.endDate)
        assertEquals(LocalTime(22, 0), decoded.startTime)
        assertEquals(LocalTime(6, 0), decoded.endTime)
    }

    @Test
    fun roundTrip_multiDayTimedEvent_preservesFullSpan() {
        val event = BasicKalendarSyncEvent(
            date = LocalDate(2026, 5, 1),
            eventName = "Conference",
            startTime = LocalTime(9, 0),
            endTime = LocalTime(17, 0),
            endDate = LocalDate(2026, 5, 4),
        )
        val decoded = KalendarICalCodec.decode(KalendarICalCodec.encode(listOf(event))).single()
        assertEquals(LocalDate(2026, 5, 4), decoded.endDate)
        assertEquals(LocalTime(17, 0), decoded.endTime)
    }
}
