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

/**
 * Start of the window every fixture expands over. The scoped edit, delete, and round-trip suites
 * exercise one code path from three sides — a deletion is an edit with nothing to write back — so
 * they have to agree on both the series they cut up and the window they inspect it through, or
 * "the dates before and after match" stops meaning anything.
 */
internal val WindowStart = LocalDate(2026, 1, 1)

/** End of the window every fixture expands over, wide enough to reach past any split under test. */
internal val WindowEnd = LocalDate(2026, 12, 31)

/** The dates this event occupies across the whole fixture window. */
internal fun KalendarSyncEvent.occurrenceDates(): List<LocalDate> =
    expandOccurrences(rangeStart = WindowStart, rangeEnd = WindowEnd).map { it.date }

/**
 * Every date the change still covers once applied — the rewritten series plus whatever it created.
 * A scoped edit that changes no field must leave this identical to the original series' dates.
 */
internal fun KalendarSeriesChange.allOccurrenceDates(): List<LocalDate> =
    (listOfNotNull(updatedSeries) + newEvents).flatMap { it.occurrenceDates() }.sorted()

/** Daily standup, 09:00–09:15, every day from Monday 2026-03-02 onwards. */
internal fun dailySeries(
    count: Int? = null,
    until: LocalDate? = null,
    exceptionDates: List<LocalDate> = emptyList(),
) = BasicKalendarSyncEvent(
    id = "series-1",
    date = LocalDate(2026, 3, 2),
    eventName = "Standup",
    startTime = LocalTime(9, 0),
    endTime = LocalTime(9, 15),
    recurrenceRule = KalendarRule(
        frequency = KalendarRecurrenceFrequency.DAILY,
        count = count,
        until = until,
    ),
    exceptionDates = exceptionDates,
)

/** The occurrence-shaped event a consumer hands back from an edit form. */
internal fun editedOccurrence(
    date: LocalDate,
    name: String = "Standup (extended)",
    startTime: LocalTime? = LocalTime(9, 0),
    endTime: LocalTime? = LocalTime(10, 0),
) = BasicKalendarSyncEvent(
    date = date,
    eventName = name,
    startTime = startTime,
    endTime = endTime,
)

/**
 * Writes the change out as an `.ics` file and reads it back, which is what a consumer's calendar
 * does to it. The series keeps its id — a UID survives the round trip — while the created events
 * are inserted without one, exactly as a provider would take them.
 */
internal fun roundTrip(change: KalendarSeriesChange): List<KalendarSyncEvent> =
    importFromIcs(exportToIcs(listOfNotNull(change.updatedSeries) + change.newEvents))
