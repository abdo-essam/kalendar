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

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import platform.EventKit.EKAlarm
import platform.EventKit.EKAuthorizationStatusFullAccess
import platform.EventKit.EKCalendar
import platform.EventKit.EKEntityType
import platform.EventKit.EKEvent
import platform.EventKit.EKEventStore
import platform.EventKit.EKParticipant
import platform.EventKit.EKRecurrenceDayOfWeek
import platform.EventKit.EKRecurrenceEnd
import platform.EventKit.EKRecurrenceFrequency
import platform.EventKit.EKRecurrenceRule
import platform.EventKit.EKSpan
import platform.EventKit.EKWeekday
import platform.EventKit.EKWeekdayFriday
import platform.EventKit.EKWeekdayMonday
import platform.EventKit.EKWeekdaySaturday
import platform.EventKit.EKWeekdaySunday
import platform.EventKit.EKWeekdayThursday
import platform.EventKit.EKWeekdayTuesday
import platform.EventKit.EKWeekdayWednesday
import platform.Foundation.NSDate
import platform.Foundation.NSNumber
import platform.Foundation.NSTimeIntervalSince1970
import platform.Foundation.timeIntervalSince1970
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.time.Instant

/**
 * Creates an iOS [KalendarSyncProvider] backed by EventKit.
 *
 * **Required permission** — add to your `Info.plist`:
 * ```xml
 * <key>NSCalendarUsageDescription</key>
 * <string>This app needs calendar access to display and manage your events.</string>
 * ```
 */
public fun KalendarSync(): KalendarSyncProvider = IosKalendarSync()

private const val END_OF_DAY_HOUR = 23
private const val END_OF_DAY_MINUTE = 59
private const val SECONDS_PER_DAY = 86_400L
private const val SECONDS_PER_MINUTE = 60.0
private const val MAILTO_PREFIX = "mailto:"

@OptIn(ExperimentalForeignApi::class)
private class IosKalendarSync : KalendarSyncProvider {

    private val eventStore = EKEventStore()
    private val tz get() = TimeZone.currentSystemDefault()

    override suspend fun hasPermission(): Boolean {
        val status = EKEventStore.authorizationStatusForEntityType(EKEntityType.EKEntityTypeEvent)
        if (status == EKAuthorizationStatusFullAccess) return true

        return suspendCoroutine { cont ->
            eventStore.requestAccessToEntityType(EKEntityType.EKEntityTypeEvent) { granted, _ ->
                cont.resume(granted)
            }
        }
    }

    override suspend fun fetchEvents(
        startDate: LocalDate,
        endDate: LocalDate,
        timeZone: TimeZone,
    ): KalendarSyncResult<List<KalendarSyncEvent>> {
        if (!hasPermission()) return KalendarSyncResult.PermissionDenied
        return runCatching {
            val start = startDate.atStartOfDayIn(timeZone).toNSDate()
            val end = endDate.atStartOfDayIn(timeZone).toNSDate()
            val calendars = eventStore.calendarsForEntityType(EKEntityType.EKEntityTypeEvent)

            @Suppress("UNCHECKED_CAST")
            val ekEvents = eventStore.eventsMatchingPredicate(
                eventStore.predicateForEventsWithStartDate(start, end, calendars)
            ) as? List<EKEvent> ?: emptyList()

            KalendarSyncResult.Success(ekEvents.map { it.toKalendarSyncEvent() })
        }.getOrElse { KalendarSyncResult.Error("Failed to fetch events", it) }
    }

    /**
     * Lists all event calendars in the store. [KalendarSyncCalendar.colorArgb] is always
     * `null` here: EventKit exposes calendar colours only as `CGColor`, whose colour-space
     * handling has no reliable ARGB mapping, so no conversion is attempted.
     */
    override suspend fun listCalendars(): KalendarSyncResult<List<KalendarSyncCalendar>> {
        if (!hasPermission()) return KalendarSyncResult.PermissionDenied
        return runCatching {
            val calendars = eventStore.calendarsForEntityType(EKEntityType.EKEntityTypeEvent)
                ?.filterIsInstance<EKCalendar>()
                .orEmpty()
            KalendarSyncResult.Success(
                calendars.map {
                    KalendarSyncCalendar(id = it.calendarIdentifier, name = it.title, colorArgb = null)
                },
            )
        }.getOrElse { KalendarSyncResult.Error(message = "Failed to list calendars", cause = it) }
    }

    /**
     * Inserts [event] into the calendar identified by [calendarId], or the store's default
     * calendar when `null` or not found. [KalendarSyncEvent.attendees] is ignored: EventKit
     * does not allow adding attendees programmatically. A non-null
     * [KalendarSyncEvent.recurrenceRule] is written as an [EKRecurrenceRule], but
     * [KalendarSyncEvent.exceptionDates] is ignored: EventKit has no per-rule EXDATE API —
     * exceptions are made by deleting individual occurrences with `EKSpanThisEvent`.
     */
    override suspend fun insertEvent(event: KalendarSyncEvent, calendarId: String?): KalendarSyncResult<String> {
        if (!hasPermission()) return KalendarSyncResult.PermissionDenied
        return runCatching {
            val ekEvent = EKEvent.eventWithEventStore(eventStore).apply {
                title = event.eventName
                notes = event.eventDescription
                allDay = event.isAllDay
                startDate = if (event.isAllDay) {
                    event.date.atStartOfDayIn(tz).toNSDate()
                } else {
                    localTimeToNSDate(event.date, event.startTime ?: LocalTime(0, 0))
                }
                endDate = if (event.isAllDay) {
                    (event.endDate ?: event.date).atStartOfDayIn(tz).plusDayNSDate()
                } else {
                    localTimeToNSDate(event.date, event.endTime ?: LocalTime(END_OF_DAY_HOUR, END_OF_DAY_MINUTE))
                }
                calendar = calendarId?.let { eventStore.calendarWithIdentifier(it) }
                    ?: eventStore.defaultCalendarForNewEvents
                event.recurrenceRule?.let { rule ->
                    recurrenceRules = listOf(rule.toEKRecurrenceRule())
                }
                event.reminders.forEach { reminder ->
                    addAlarm(EKAlarm.alarmWithRelativeOffset(-reminder.minutesBefore * SECONDS_PER_MINUTE))
                }
            }

            suspendCoroutine<KalendarSyncResult<String>> { cont ->
                val saved = eventStore.saveEvent(ekEvent, EKSpan.EKSpanThisEvent, true, null)
                if (saved) {
                    cont.resume(KalendarSyncResult.Success(ekEvent.eventIdentifier ?: ""))
                } else {
                    cont.resume(KalendarSyncResult.Error("EventKit saveEvent returned false"))
                }
            }
        }.getOrElse { KalendarSyncResult.Error("Failed to insert event", it) }
    }

    /**
     * Updates the event identified by [eventId] with the fields of [event].
     * [KalendarSyncEvent.recurrenceRule] is written as an [EKRecurrenceRule] (a null rule
     * clears any existing rules); [KalendarSyncEvent.exceptionDates] is ignored — see
     * [insertEvent]. When the update adds, changes, or removes a recurrence rule it is saved
     * with `EKSpanFutureEvents`, since EventKit does not apply rule changes to a single
     * occurrence; non-recurring updates keep using `EKSpanThisEvent`.
     */
    override suspend fun updateEvent(eventId: String, event: KalendarSyncEvent): KalendarSyncResult<Unit> {
        if (!hasPermission()) return KalendarSyncResult.PermissionDenied
        return runCatching {
            val ekEvent = eventStore.eventWithIdentifier(eventId)
                ?: return KalendarSyncResult.Error("Event with id=$eventId not found")
            val wasRecurring = ekEvent.recurrenceRules?.isNotEmpty() == true
            ekEvent.title = event.eventName
            ekEvent.notes = event.eventDescription
            ekEvent.allDay = event.isAllDay
            ekEvent.startDate = if (event.isAllDay) {
                event.date.atStartOfDayIn(tz).toNSDate()
            } else {
                localTimeToNSDate(event.date, event.startTime ?: LocalTime(0, 0))
            }
            ekEvent.endDate = if (event.isAllDay) {
                (event.endDate ?: event.date).atStartOfDayIn(tz).plusDayNSDate()
            } else {
                localTimeToNSDate(event.date, event.endTime ?: LocalTime(END_OF_DAY_HOUR, END_OF_DAY_MINUTE))
            }
            ekEvent.recurrenceRules = event.recurrenceRule?.let { listOf(it.toEKRecurrenceRule()) }
            ekEvent.alarms?.filterIsInstance<EKAlarm>()?.forEach { ekEvent.removeAlarm(it) }
            event.reminders.forEach { reminder ->
                ekEvent.addAlarm(EKAlarm.alarmWithRelativeOffset(-reminder.minutesBefore * SECONDS_PER_MINUTE))
            }
            val span = if (wasRecurring || event.recurrenceRule != null) {
                EKSpan.EKSpanFutureEvents
            } else {
                EKSpan.EKSpanThisEvent
            }
            val saved = eventStore.saveEvent(ekEvent, span, true, null)
            if (saved) {
                KalendarSyncResult.Success(Unit)
            } else {
                KalendarSyncResult.Error("EventKit saveEvent returned false for update")
            }
        }.getOrElse { KalendarSyncResult.Error("Failed to update event", it) }
    }

    override suspend fun deleteEvent(eventId: String): KalendarSyncResult<Unit> {
        if (!hasPermission()) return KalendarSyncResult.PermissionDenied
        return runCatching {
            val ekEvent = eventStore.eventWithIdentifier(eventId)
                ?: return KalendarSyncResult.Error("Event with id=$eventId not found")
            val deleted = eventStore.removeEvent(ekEvent, EKSpan.EKSpanThisEvent, true, null)
            if (deleted) {
                KalendarSyncResult.Success(Unit)
            } else {
                KalendarSyncResult.Error("EventKit removeEvent returned false")
            }
        }.getOrElse { KalendarSyncResult.Error("Failed to delete event", it) }
    }

    /**
     * Converts an [EKEvent] to a [BasicKalendarSyncEvent], using today's date as a fallback
     * when [EKEvent.startDate] is null.
     *
     * Events fetched from a recurring series are materialised occurrences, but EventKit still
     * exposes the series' [EKRecurrenceRule] on each occurrence — so every occurrence carries
     * the mapped [KalendarSyncEvent.recurrenceRule] of its series.
     */
    private fun EKEvent.toKalendarSyncEvent(): KalendarSyncEvent {
        val date = startDate?.toLocalDate() ?: return BasicKalendarSyncEvent(
            id = eventIdentifier,
            date = kotlin.time.Clock.System.now().toLocalDateTime(tz).date,
            eventName = title ?: "",
            recurrenceRule = toKalendarRuleOrNull(),
            reminders = toReminders(),
            attendees = toAttendees(),
        )
        val spanEndDate = endDate?.toLocalDate()
            ?.let { if (allDay) it.minus(1, DateTimeUnit.DAY) else it }
            ?.takeIf { allDay && it > date }
        return BasicKalendarSyncEvent(
            id = eventIdentifier,
            date = date,
            eventName = title ?: "",
            eventDescription = notes?.takeIf { it.isNotBlank() },
            startTime = if (allDay) null else startDate?.toLocalTime(),
            endTime = if (allDay) null else endDate?.toLocalTime(),
            recurrenceRule = toKalendarRuleOrNull(),
            endDate = spanEndDate,
            reminders = toReminders(),
            attendees = toAttendees(),
        )
    }

    /**
     * Maps this rule to EventKit's [EKRecurrenceRule]: frequency/interval directly,
     * [KalendarRule.count] or [KalendarRule.until] to an [EKRecurrenceEnd] (until as the end
     * of that day so occurrences on the last date are kept), and the
     * byDay/byMonthDay/byMonth filters to their `daysOfTheWeek`/`daysOfTheMonth`/
     * `monthsOfTheYear` equivalents.
     */
    private fun KalendarRule.toEKRecurrenceRule(): EKRecurrenceRule {
        val recurrenceEnd = when {
            count != null -> EKRecurrenceEnd.recurrenceEndWithOccurrenceCount(count.toULong())
            until != null -> EKRecurrenceEnd.recurrenceEndWithEndDate(
                localTimeToNSDate(date = until, time = LocalTime(hour = END_OF_DAY_HOUR, minute = END_OF_DAY_MINUTE)),
            )
            else -> null
        }
        return EKRecurrenceRule(
            recurrenceWithFrequency = frequency.toEKFrequency(),
            interval = interval.toLong(),
            daysOfTheWeek = byDay
                .map { EKRecurrenceDayOfWeek.dayOfWeek(it.toEKWeekday()) }
                .takeIf { it.isNotEmpty() },
            daysOfTheMonth = byMonthDay.map { NSNumber(int = it) }.takeIf { it.isNotEmpty() },
            monthsOfTheYear = byMonth.map { NSNumber(int = it) }.takeIf { it.isNotEmpty() },
            weeksOfTheYear = null,
            daysOfTheYear = null,
            setPositions = null,
            end = recurrenceEnd,
        )
    }

    /**
     * Maps the first [EKRecurrenceRule] of this event back to a [KalendarRule], or `null`
     * for non-recurring events. `occurrenceCount` maps to
     * [KalendarRule.count] and a recurrence end date to [KalendarRule.until] (never both,
     * matching their mutual exclusivity).
     */
    private fun EKEvent.toKalendarRuleOrNull(): KalendarRule? {
        val ekRule = recurrenceRules?.firstOrNull() as? EKRecurrenceRule ?: return null
        val frequency = ekRule.frequency.toKalendarFrequency()
        val recurrenceEnd = ekRule.recurrenceEnd
        val count = recurrenceEnd?.occurrenceCount?.takeIf { it > 0uL }?.toInt()
        val until = if (count == null) recurrenceEnd?.endDate?.toLocalDate() else null
        return KalendarRule(
            frequency = frequency,
            interval = ekRule.interval.toInt().coerceAtLeast(1),
            count = count,
            until = until,
            byDay = ekRule.daysOfTheWeek
                ?.mapNotNull { entry -> (entry as? EKRecurrenceDayOfWeek)?.dayOfTheWeek?.toKalendarWeekDayOrNull() }
                .orEmpty(),
            byMonthDay = ekRule.daysOfTheMonth?.mapNotNull { (it as? NSNumber)?.intValue }.orEmpty(),
            byMonth = ekRule.monthsOfTheYear?.mapNotNull { (it as? NSNumber)?.intValue }.orEmpty(),
        )
    }

    /** Maps a [KalendarRecurrenceFrequency] to its [EKRecurrenceFrequency] constant. */
    private fun KalendarRecurrenceFrequency.toEKFrequency(): EKRecurrenceFrequency = when (this) {
        KalendarRecurrenceFrequency.DAILY -> EKRecurrenceFrequency.EKRecurrenceFrequencyDaily
        KalendarRecurrenceFrequency.WEEKLY -> EKRecurrenceFrequency.EKRecurrenceFrequencyWeekly
        KalendarRecurrenceFrequency.MONTHLY -> EKRecurrenceFrequency.EKRecurrenceFrequencyMonthly
        KalendarRecurrenceFrequency.YEARLY -> EKRecurrenceFrequency.EKRecurrenceFrequencyYearly
    }

    /** Maps an [EKRecurrenceFrequency] back to a [KalendarRecurrenceFrequency]. */
    private fun EKRecurrenceFrequency.toKalendarFrequency(): KalendarRecurrenceFrequency = when (this) {
        EKRecurrenceFrequency.EKRecurrenceFrequencyDaily -> KalendarRecurrenceFrequency.DAILY
        EKRecurrenceFrequency.EKRecurrenceFrequencyWeekly -> KalendarRecurrenceFrequency.WEEKLY
        EKRecurrenceFrequency.EKRecurrenceFrequencyMonthly -> KalendarRecurrenceFrequency.MONTHLY
        EKRecurrenceFrequency.EKRecurrenceFrequencyYearly -> KalendarRecurrenceFrequency.YEARLY
    }

    /** Maps a [KalendarWeekDay] to its [EKWeekday] constant. */
    private fun KalendarWeekDay.toEKWeekday(): EKWeekday = when (this) {
        KalendarWeekDay.MONDAY -> EKWeekdayMonday
        KalendarWeekDay.TUESDAY -> EKWeekdayTuesday
        KalendarWeekDay.WEDNESDAY -> EKWeekdayWednesday
        KalendarWeekDay.THURSDAY -> EKWeekdayThursday
        KalendarWeekDay.FRIDAY -> EKWeekdayFriday
        KalendarWeekDay.SATURDAY -> EKWeekdaySaturday
        KalendarWeekDay.SUNDAY -> EKWeekdaySunday
    }

    /** Maps an [EKWeekday] back to a [KalendarWeekDay], or `null` if unknown. */
    private fun EKWeekday.toKalendarWeekDayOrNull(): KalendarWeekDay? = when (this) {
        EKWeekdayMonday -> KalendarWeekDay.MONDAY
        EKWeekdayTuesday -> KalendarWeekDay.TUESDAY
        EKWeekdayWednesday -> KalendarWeekDay.WEDNESDAY
        EKWeekdayThursday -> KalendarWeekDay.THURSDAY
        EKWeekdayFriday -> KalendarWeekDay.FRIDAY
        EKWeekdaySaturday -> KalendarWeekDay.SATURDAY
        EKWeekdaySunday -> KalendarWeekDay.SUNDAY
        else -> null
    }

    /**
     * Maps this event's alarms back to [KalendarReminder]s. Only relative offsets at or before
     * the event start translate to a minutes-before value; alarms after the start are dropped.
     */
    private fun EKEvent.toReminders(): List<KalendarReminder> =
        alarms?.filterIsInstance<EKAlarm>()?.mapNotNull { alarm ->
            val offset = alarm.relativeOffset
            if (offset <= 0.0) {
                KalendarReminder(minutesBefore = (-offset / SECONDS_PER_MINUTE).toInt())
            } else {
                null
            }
        }.orEmpty()

    /**
     * Maps this event's participants to [KalendarAttendee]s, stripping the `mailto:` prefix
     * from each participant URL. EventKit only ever reads attendees — see
     * [KalendarSyncEvent.attendees].
     */
    private fun EKEvent.toAttendees(): List<KalendarAttendee> =
        attendees?.filterIsInstance<EKParticipant>()?.mapNotNull { participant ->
            participant.URL.absoluteString
                ?.removeMailtoPrefix()
                ?.takeIf { it.isNotBlank() }
                ?.let { email -> KalendarAttendee(email = email, name = participant.name) }
        }.orEmpty()

    private fun String.removeMailtoPrefix(): String =
        if (startsWith(MAILTO_PREFIX, ignoreCase = true)) substring(MAILTO_PREFIX.length) else this

    /** Converts a [kotlinx.datetime.Instant] to an [NSDate] for use with EventKit APIs. */
    private fun Instant.toNSDate(): NSDate = epochSecondsToNSDate(epochSeconds.toDouble())

    /** Converts an [NSDate] to a [LocalDate] using the device's current time zone. */
    private fun NSDate.toLocalDate(): LocalDate =
        Instant.fromEpochSeconds(timeIntervalSince1970.toLong())
            .toLocalDateTime(tz).date

    /** Converts an [NSDate] to a [LocalTime] using the device's current time zone. */
    private fun NSDate.toLocalTime(): LocalTime =
        Instant.fromEpochSeconds(timeIntervalSince1970.toLong())
            .toLocalDateTime(tz).time

    /** Combines a [LocalDate] and [LocalTime] into an [NSDate] for use with EventKit APIs. */
    private fun localTimeToNSDate(date: LocalDate, time: LocalTime): NSDate {
        val instant = kotlinx.datetime.LocalDateTime(date, time).toInstant(tz)
        return epochSecondsToNSDate(instant.epochSeconds.toDouble())
    }

    /** Returns the [Instant] at midnight of this [LocalDate] in the given [TimeZone]. */
    private fun LocalDate.atStartOfDayIn(tz: TimeZone): Instant =
        kotlinx.datetime.LocalDateTime(this, LocalTime(0, 0)).toInstant(tz)

    /** Returns an [NSDate] exactly 24 hours after this [Instant], used for all-day event end dates. */
    private fun Instant.plusDayNSDate(): NSDate =
        epochSecondsToNSDate((epochSeconds + SECONDS_PER_DAY).toDouble())

    /**
     * [NSDate]'s designated initializer counts seconds since its own reference date
     * (2001-01-01), not the Unix epoch — [NSTimeIntervalSince1970] is the fixed offset
     * between the two.
     */
    private fun epochSecondsToNSDate(epochSeconds: Double): NSDate =
        NSDate(timeIntervalSinceReferenceDate = epochSeconds - NSTimeIntervalSince1970)
}
