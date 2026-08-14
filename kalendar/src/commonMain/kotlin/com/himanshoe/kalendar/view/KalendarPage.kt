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

import com.himanshoe.kalendar.foundation.event.KalendarEvent
import com.himanshoe.kalendar.foundation.event.KalendarEvents
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.Month
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlin.math.roundToInt

/**
 * Default [KalendarViewConfig.monthNameFormatter]: kotlinx-datetime has no locale-aware
 * formatting, so this is plain English (e.g. "August") — pass your own formatter (backed by
 * `java.time`, `NSDateFormatter`, `Intl`, etc.) for anything else.
 */
internal val defaultMonthNameFormatter: (Month) -> String = {
    it.name.lowercase().replaceFirstChar { char -> char.titlecase() }
}

/**
 * Default [KalendarViewConfig.shortMonthNameFormatter]: the plain English month name's first three
 * letters. Truncating is only safe here because the *default* name is known to be English — which is
 * precisely why this is a hook rather than a `take(3)` applied to whatever a caller supplied.
 */
internal val defaultShortMonthNameFormatter: (Month) -> String = {
    defaultMonthNameFormatter(it).take(SHORT_NAME_LENGTH)
}

/** Default [KalendarViewConfig.dayOfWeekNameFormatter]: the plain English day name's first three letters. */
internal val defaultDayOfWeekNameFormatter: (DayOfWeek) -> String = { dayOfWeek ->
    dayOfWeek.name.take(SHORT_NAME_LENGTH).lowercase().replaceFirstChar { it.titlecase() }
}

/** Default [KalendarViewConfig.dayOfWeekLabelFormatter]: the day name's first letter. */
internal val defaultDayOfWeekLabelFormatter: (DayOfWeek) -> String = { it.name.take(1) }

private const val SHORT_NAME_LENGTH = 3

/** Default [KalendarViewConfig.hourLabelFormatter]: 24-hour "HH:00". */
internal val defaultHourLabelFormatter: (Int) -> String = { "${it.toString().padStart(2, '0')}:00" }

/**
 * Multi-day events are expanded across at most this many days, guarding against a
 * caller-supplied `endDate` far in the future turning into an unbounded loop.
 */
private const val MAX_EVENT_SPAN_DAYS = 366

internal fun LocalDate.startOfWeek(startDayOfWeek: DayOfWeek): LocalDate =
    minus((dayOfWeek.ordinal - startDayOfWeek.ordinal + DAYS_PER_WEEK) % DAYS_PER_WEEK, DateTimeUnit.DAY)

internal fun LocalDate.startOfMonth(): LocalDate = minus(dayOfMonth - 1, DateTimeUnit.DAY)

internal fun LocalDate.startOfYear(): LocalDate = LocalDate(year, 1, 1)

internal fun weeksBetween(from: LocalDate, to: LocalDate, startDayOfWeek: DayOfWeek): Int {
    val fromWeekStart = from.startOfWeek(startDayOfWeek)
    val toWeekStart = to.startOfWeek(startDayOfWeek)
    return ((toWeekStart.toEpochDays() - fromWeekStart.toEpochDays()) / 7).toInt()
}

internal fun monthsBetween(from: LocalDate, to: LocalDate): Int {
    val fromMonthStart = from.startOfMonth()
    val toMonthStart = to.startOfMonth()
    return (toMonthStart.year - fromMonthStart.year) * 12 + (toMonthStart.monthNumber - fromMonthStart.monthNumber)
}

internal fun yearsBetween(from: LocalDate, to: LocalDate): Int = to.year - from.year

internal fun monthTitle(
    monthStart: LocalDate,
    monthNameFormatter: (Month) -> String = defaultMonthNameFormatter,
): String = "${monthNameFormatter(monthStart.month)} ${monthStart.year}"

internal const val MINUTES_PER_HOUR = 60
internal const val HOURS_PER_DAY = 24
internal const val MINUTES_PER_DAY = MINUTES_PER_HOUR * HOURS_PER_DAY

/** Shortest rendered duration for a schedule block, so zero/negative spans stay tappable. */
internal const val MIN_SCHEDULE_BLOCK_MINUTES = 15

/**
 * A timed event positioned on [KalendarSchedule]'s hour grid: its minute range within the day and
 * the column slot it occupies when overlapping events share the horizontal space.
 *
 * @property column Zero-based slot within the event's overlap cluster.
 * @property columns Total slots in the cluster — every member of a cluster divides the width by
 *   the same count so side-by-side blocks align.
 */
internal data class KalendarScheduleBlock(
    val event: KalendarEvent,
    val startMinutes: Int,
    val endMinutes: Int,
    val column: Int,
    val columns: Int,
)

/**
 * Lays out the timed events (those with a non-null [KalendarEvent.startTime]) of [date] as
 * schedule blocks: minute ranges clamped to the day (events spanning midnight are truncated at
 * the day's edges), a missing [KalendarEvent.endTime] defaulting to one hour, and overlapping
 * events packed greedily into side-by-side columns per overlap cluster.
 *
 * Blocks keep their true minute range even when [window] shows less than the whole day — clipping is
 * a drawing concern ([blockPlacement]), and truncating here would make a drag report the clipped
 * time rather than the event's own. Events that do not intersect [window] at all are dropped, so the
 * result is exactly what the grid draws and every block the caller is handed has a hit target.
 */
internal fun scheduleBlocks(
    events: List<KalendarEvent>,
    date: LocalDate,
    window: KalendarHourWindow = KalendarHourWindow.FullDay,
): List<KalendarScheduleBlock> {
    data class Working(val event: KalendarEvent, val start: Int, val end: Int) {
        var column: Int = 0
        var columns: Int = 1
    }

    val timed = events.mapNotNull { event ->
        val startTime = event.startTime ?: return@mapNotNull null
        val start = if (startTime.date < date) {
            0
        } else {
            startTime.hour * MINUTES_PER_HOUR + startTime.minute
        }
        val requestedEnd = event.endTime?.let { endTime ->
            if (endTime.date > date) {
                MINUTES_PER_DAY
            } else {
                endTime.hour * MINUTES_PER_HOUR + endTime.minute
            }
        } ?: (start + MINUTES_PER_HOUR)
        val clampedStart = start.coerceIn(0, MINUTES_PER_DAY - MIN_SCHEDULE_BLOCK_MINUTES)
        val end = requestedEnd.coerceIn(clampedStart + MIN_SCHEDULE_BLOCK_MINUTES, MINUTES_PER_DAY)
        Working(event = event, start = clampedStart, end = end)
            .takeIf { it.end > window.startMinutes && it.start < window.endMinutes }
    }.sortedWith(compareBy({ it.start }, { it.end }))

    val active = mutableListOf<Working>()
    val cluster = mutableListOf<Working>()
    for (candidate in timed) {
        active.removeAll { it.end <= candidate.start }
        if (active.isEmpty() && cluster.isNotEmpty()) {
            val width = cluster.maxOf { it.column } + 1
            cluster.forEach { it.columns = width }
            cluster.clear()
        }
        val occupied = active.map { it.column }.toSet()
        candidate.column = generateSequence(0) { it + 1 }.first { it !in occupied }
        active += candidate
        cluster += candidate
    }
    if (cluster.isNotEmpty()) {
        val width = cluster.maxOf { it.column } + 1
        cluster.forEach { it.columns = width }
    }

    return timed.map {
        KalendarScheduleBlock(
            event = it.event,
            startMinutes = it.start,
            endMinutes = it.end,
            column = it.column,
            columns = it.columns,
        )
    }
}

/**
 * Converts an accumulated raw drag distance into a snapped minute delta in a single rounding
 * step ([rawPx] → snap units directly), avoiding the drift a two-stage px→minutes→snap rounding
 * introduces for snap values that don't divide 60.
 */
internal fun snappedDragMinutes(rawPx: Float, minutePx: Float, snapMinutes: Int): Int {
    if (minutePx <= 0f || snapMinutes <= 0) {
        return 0
    }
    return (rawPx / (minutePx * snapMinutes)).roundToInt() * snapMinutes
}

/**
 * Applies a drag [deltaMinutes] to [block]: resizing moves only the end (clamped to at least
 * [MIN_SCHEDULE_BLOCK_MINUTES] long and at most the end of [window]), moving shifts the whole block
 * within [window] while preserving its duration.
 *
 * The clamp is the visible window rather than the day, so a drag cannot push a block off the top or
 * bottom of a business-hours grid into space the user cannot scroll to and would have no way to
 * drag it back from.
 */
internal fun adjustedScheduleBlock(
    block: KalendarScheduleBlock,
    deltaMinutes: Int,
    resizing: Boolean,
    window: KalendarHourWindow = KalendarHourWindow.FullDay,
): KalendarScheduleBlock = if (resizing) {
    val newEnd = (block.endMinutes + deltaMinutes)
        .coerceWithin(block.startMinutes + MIN_SCHEDULE_BLOCK_MINUTES, window.endMinutes)
    block.copy(endMinutes = newEnd)
} else {
    val duration = block.endMinutes - block.startMinutes
    val newStart = (block.startMinutes + deltaMinutes)
        .coerceWithin(window.startMinutes, window.endMinutes - duration)
    block.copy(startMinutes = newStart, endMinutes = newStart + duration)
}

/**
 * Converts a horizontal drag distance into whole day columns on the week grid, dividing by the full
 * day-column width and clamped to one grid width in either direction.
 *
 * [columnCount] is the number of day columns actually drawn, which is not always seven — a Mon–Fri
 * week has five, and clamping to six would let a drag land the block a column past the grid's edge.
 * The result counts *columns*, so a caller on a filtered week converts it to a date with
 * [visibleDateStep] rather than by adding days.
 */
internal fun dayShiftForDrag(
    rawDeltaXPx: Float,
    dayColumnWidthPx: Int,
    columnCount: Int = DAYS_PER_WEEK,
): Int {
    if (dayColumnWidthPx <= 0) {
        return 0
    }
    val maximumShift = (columnCount - 1).coerceAtLeast(0)
    return (rawDeltaXPx / dayColumnWidthPx).roundToInt().coerceIn(-maximumShift, maximumShift)
}

/**
 * The (start, end) date-times reported for a released drag. An end at exactly [MINUTES_PER_DAY]
 * is midnight of the following day — [kotlinx.datetime.LocalTime] cannot express 24:00, so it
 * must not be silently truncated to 23:59.
 */
internal fun dragReleaseTimes(
    block: KalendarScheduleBlock,
    targetDate: LocalDate,
): Pair<LocalDateTime, LocalDateTime> {
    val start = LocalDateTime(targetDate, minutesToLocalTime(block.startMinutes))
    val end = if (block.endMinutes >= MINUTES_PER_DAY) {
        LocalDateTime(targetDate.plus(1, DateTimeUnit.DAY), LocalTime(hour = 0, minute = 0))
    } else {
        LocalDateTime(targetDate, minutesToLocalTime(block.endMinutes))
    }
    return start to end
}

internal fun minutesToLocalTime(minutes: Int): LocalTime {
    val clamped = minutes.coerceIn(0, MINUTES_PER_DAY - 1)
    return LocalTime(hour = clamped / MINUTES_PER_HOUR, minute = clamped % MINUTES_PER_HOUR)
}

/**
 * Groups events by every date they occupy — a multi-day event (one with
 * [KalendarEvent.endDate] set) appears under each date from [KalendarEvent.date] through
 * [KalendarEvent.endDate], inclusive, capped at [MAX_EVENT_SPAN_DAYS] days.
 */
internal fun KalendarEvents.byDateExpandingSpans(): Map<LocalDate, List<KalendarEvent>> {
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
