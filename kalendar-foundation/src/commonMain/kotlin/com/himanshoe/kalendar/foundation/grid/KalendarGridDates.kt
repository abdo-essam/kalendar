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

package com.himanshoe.kalendar.foundation.grid

import com.himanshoe.kalendar.foundation.datetime.DAYS_PER_WEEK
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/*
 * The date arithmetic behind a calendar grid — which weekdays get a column, and what that does to a
 * month grid's padding, to a week's dates, to the header row, and to what an arrow key means.
 *
 * Every function here carries the same obligation: produce a run of dates whose length is a whole
 * number of grid rows, in the same weekday rotation the header uses. Getting that wrong misaligns
 * every column at once, so it is worth being able to read all of it on one screen.
 */

/**
 * Every day of the week, and so the default for every `visibleDaysOfWeek` parameter here: a calendar
 * shows the whole week unless a caller asks for less.
 *
 * ```kotlin
 * val workWeek = ALL_DAYS_OF_WEEK - setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
 * ```
 */
public val ALL_DAYS_OF_WEEK: Set<DayOfWeek> = DayOfWeek.entries.toSet()

/**
 * The dates of the week beginning at [weekStart], keeping only those whose day of week is in
 * [visibleDaysOfWeek] — a Mon–Fri configuration yields five dates rather than seven, and so five
 * grid columns.
 *
 * Order is preserved, so the result still runs left to right from [weekStart]; hiding a day removes
 * a column rather than shifting the rest onto different weekdays.
 *
 * ```kotlin
 * val weekStart = LocalDate(2026, 6, 10).startOfWeek(DayOfWeek.MONDAY)
 * weekDates(weekStart).forEach { date -> DayCell(date) }
 * ```
 */
public fun weekDates(
    weekStart: LocalDate,
    visibleDaysOfWeek: Set<DayOfWeek> = ALL_DAYS_OF_WEEK,
): List<LocalDate> = (0 until DAYS_PER_WEEK)
    .map { weekStart.plus(it, DateTimeUnit.DAY) }
    .filter { it.dayOfWeek in visibleDaysOfWeek }

/**
 * The dates a month grid renders: the month's own days plus the leading padding needed to align the
 * first row on [startDayOfWeek], filtered to [visibleDaysOfWeek].
 *
 * Chunk the result by `visibleDaysOfWeek.size` and you have the grid, row by row, every column under
 * the header [daysOfWeekStartingAt] produces for the same arguments:
 *
 * ```kotlin
 * val headers = daysOfWeekStartingAt(DayOfWeek.MONDAY)
 * val rows = monthGridDates(
 *     monthStart = LocalDate(2026, 8, 1),
 *     startDayOfWeek = DayOfWeek.MONDAY,
 * ).chunked(headers.size)
 * // rows[0][0] is a Monday; rows.last().last() is the final cell of the grid
 * ```
 *
 * The padding is measured from the month's first *visible* day rather than from its 1st. With every
 * day visible the two are the same date and the result is unchanged; with weekends hidden and a
 * month that opens on a Saturday they differ, and measuring from the 1st would emit a whole leading
 * row belonging entirely to the previous month.
 *
 * Because the run starts on a [startDayOfWeek] boundary, each seven consecutive dates contain
 * exactly one of every weekday — so after filtering, every `visibleDaysOfWeek.size` dates form one
 * grid row, in the same rotation as [daysOfWeekStartingAt] produces for the header. That identity is
 * what keeps the columns under their labels; it is not an accident of the arithmetic.
 */
public fun monthGridDates(
    monthStart: LocalDate,
    startDayOfWeek: DayOfWeek,
    visibleDaysOfWeek: Set<DayOfWeek> = ALL_DAYS_OF_WEEK,
): List<LocalDate> {
    val lastDayOfMonth = monthStart.plus(1, DateTimeUnit.MONTH).minus(1, DateTimeUnit.DAY)
    val firstVisible = visibleDateOnOrAfter(date = monthStart, visibleDaysOfWeek = visibleDaysOfWeek)
    val leadingPaddingDays = (firstVisible.dayOfWeek.ordinal - startDayOfWeek.ordinal + DAYS_PER_WEEK) % DAYS_PER_WEEK
    val gridStart = firstVisible.minus(leadingPaddingDays, DateTimeUnit.DAY)
    val span = (lastDayOfMonth.toEpochDays() - gridStart.toEpochDays()).toInt()
    return (0..span)
        .map { gridStart.plus(it, DateTimeUnit.DAY) }
        .filter { it.dayOfWeek in visibleDaysOfWeek }
}

/**
 * A month's own days, without the leading/trailing padding [monthGridDates] adds, and filtered to
 * [visibleDaysOfWeek]. This is the *period* a keyboard user navigates: `Home` belongs on the 1st,
 * not on the previous month's 26th — and not on a Saturday that is not on screen.
 *
 * ```kotlin
 * val august = monthDates(LocalDate(2026, 8, 1))
 * val home = august.first() // 2026-08-01
 * val end = august.last()   // 2026-08-31
 * ```
 */
public fun monthDates(
    monthStart: LocalDate,
    visibleDaysOfWeek: Set<DayOfWeek> = ALL_DAYS_OF_WEEK,
): List<LocalDate> {
    val lastDayOfMonth = monthStart.plus(1, DateTimeUnit.MONTH).minus(1, DateTimeUnit.DAY)
    return (0 until lastDayOfMonth.day)
        .map { monthStart.plus(it, DateTimeUnit.DAY) }
        .filter { it.dayOfWeek in visibleDaysOfWeek }
}

/**
 * [date] itself when its day of week is visible, otherwise the next date that is.
 *
 * [visibleDaysOfWeek] must be non-empty, so one of the next seven days always qualifies and this
 * cannot fail. It is how a jump that does not preserve the weekday — a month step keeps the day of
 * *month* — lands on a cell that exists.
 *
 * ```kotlin
 * val workWeek = ALL_DAYS_OF_WEEK - setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
 * // 2026-08-01 is a Saturday
 * visibleDateOnOrAfter(LocalDate(2026, 8, 1), workWeek) // 2026-08-03, the Monday
 * ```
 */
public fun visibleDateOnOrAfter(
    date: LocalDate,
    visibleDaysOfWeek: Set<DayOfWeek> = ALL_DAYS_OF_WEEK,
): LocalDate = (0 until DAYS_PER_WEEK)
    .map { date.plus(it, DateTimeUnit.DAY) }
    .first { it.dayOfWeek in visibleDaysOfWeek }

/**
 * The nearest visible date [step] visible days away from [from] — `+1` for the next one, `-1` for
 * the previous — skipping hidden days entirely.
 *
 * This is what a left/right arrow means once a day can be missing from the grid: on a Mon–Fri week,
 * `Right` from Friday lands on Monday, because Saturday is not a cell the ring can sit on.
 *
 * ```kotlin
 * val workWeek = ALL_DAYS_OF_WEEK - setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
 * val friday = LocalDate(2026, 3, 13)
 * visibleDateStep(from = friday, step = 1, visibleDaysOfWeek = workWeek) // 2026-03-16, the Monday
 * ```
 */
public fun visibleDateStep(
    from: LocalDate,
    step: Int,
    visibleDaysOfWeek: Set<DayOfWeek> = ALL_DAYS_OF_WEEK,
): LocalDate {
    val direction = if (step < 0) -1 else 1
    var current = from
    repeat(step * direction) {
        // At most a week of hidden days can separate two visible ones, so this terminates.
        do {
            current = current.plus(direction, DateTimeUnit.DAY)
        } while (current.dayOfWeek !in visibleDaysOfWeek)
    }
    return current
}

/**
 * The weekday column headers, rotated to begin at [startDayOfWeek] and reduced to
 * [visibleDaysOfWeek].
 *
 * The same rotate-then-filter order [monthGridDates] and [weekDates] use, which is what guarantees
 * the labels line up one-for-one with the columns of dates beneath them.
 *
 * ```kotlin
 * daysOfWeekStartingAt(DayOfWeek.SUNDAY) // [SUNDAY, MONDAY, ..., SATURDAY]
 * ```
 */
public fun daysOfWeekStartingAt(
    startDayOfWeek: DayOfWeek,
    visibleDaysOfWeek: Set<DayOfWeek> = ALL_DAYS_OF_WEEK,
): List<DayOfWeek> {
    val all = DayOfWeek.entries
    return (all.drop(startDayOfWeek.ordinal) + all.take(startDayOfWeek.ordinal))
        .filter { it in visibleDaysOfWeek }
}
