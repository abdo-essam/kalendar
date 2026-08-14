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

package com.himanshoe.kalendar.foundation.event

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

/**
 * Multi-day events are expanded across at most this many days, guarding against a
 * caller-supplied `endDate` far in the future turning into an unbounded loop.
 */
private const val MAX_EVENT_SPAN_DAYS = 366

/**
 * Groups events by every date they occupy — a multi-day event (one with [KalendarEvent.endDate] set)
 * appears under each date from [KalendarEvent.date] through [KalendarEvent.endDate], inclusive.
 *
 * This is the index a grid wants: build it once per event list and each cell is a map lookup, rather
 * than a scan of every event per cell.
 *
 * ```kotlin
 * val byDate = events.byDateExpandingSpans()
 * monthGridDates(monthStart, DayOfWeek.MONDAY).forEach { date ->
 *     DayCell(date = date, events = byDate[date].orEmpty())
 * }
 * ```
 *
 * A span longer than [MAX_EVENT_SPAN_DAYS] days is truncated, so a malformed `endDate` far in the
 * future cannot turn one event into an unbounded map. An `endDate` before [KalendarEvent.date] is
 * treated as a single-day event.
 */
public fun KalendarEvents.byDateExpandingSpans(): Map<LocalDate, List<KalendarEvent>> {
    val result = mutableMapOf<LocalDate, MutableList<KalendarEvent>>()
    for (event in this) {
        val start = event.date
        val requestedEnd = event.endDate ?: start
        val end = if (requestedEnd > start) {
            minOf(requestedEnd, start.plus(MAX_EVENT_SPAN_DAYS, DateTimeUnit.DAY))
        } else {
            start
        }
        var current = start
        while (current <= end) {
            result.getOrPut(current) { mutableListOf() }.add(event)
            current = current.plus(1, DateTimeUnit.DAY)
        }
    }
    return result
}
