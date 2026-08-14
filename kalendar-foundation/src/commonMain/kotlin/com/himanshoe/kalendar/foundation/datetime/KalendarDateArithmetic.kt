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

package com.himanshoe.kalendar.foundation.datetime

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus

/**
 * Days in one grid row, and so how far one row of a calendar moves in date terms.
 *
 * ```kotlin
 * val nextRow = date.plus(DAYS_PER_WEEK, DateTimeUnit.DAY)
 * ```
 */
public const val DAYS_PER_WEEK: Int = 7

/**
 * Months in one year, the conversion [monthsBetween] uses to turn a year difference into months.
 *
 * ```kotlin
 * val monthsInADecade = 10 * MONTHS_PER_YEAR
 * ```
 */
public const val MONTHS_PER_YEAR: Int = 12

/**
 * The first day of the week containing this date, given the weekday that week starts on.
 *
 * This is the date a week page is keyed by, and the date a month grid's first row is aligned to.
 *
 * ```kotlin
 * val wednesday = LocalDate(2026, 6, 10)
 * wednesday.startOfWeek(DayOfWeek.MONDAY) // 2026-06-08
 * wednesday.startOfWeek(DayOfWeek.SUNDAY) // 2026-06-07
 * ```
 */
public fun LocalDate.startOfWeek(startDayOfWeek: DayOfWeek): LocalDate =
    minus((dayOfWeek.ordinal - startDayOfWeek.ordinal + DAYS_PER_WEEK) % DAYS_PER_WEEK, DateTimeUnit.DAY)

/**
 * The first day of this date's month — the date a month page is keyed by.
 *
 * ```kotlin
 * LocalDate(2026, 6, 15).startOfMonth() // 2026-06-01
 * ```
 */
public fun LocalDate.startOfMonth(): LocalDate = minus(day - 1, DateTimeUnit.DAY)

/**
 * The first day of this date's year — the date a year page is keyed by.
 *
 * ```kotlin
 * LocalDate(2026, 6, 15).startOfYear() // 2026-01-01
 * ```
 */
public fun LocalDate.startOfYear(): LocalDate = LocalDate(year, 1, 1)

/**
 * Whole weeks from [from] to [to], counted between the two dates' week starts, so any two dates in
 * the same week are zero weeks apart regardless of which weekdays they fall on. Negative when [to]
 * precedes [from].
 *
 * ```kotlin
 * val monday = LocalDate(2026, 6, 8)
 * weeksBetween(from = monday, to = LocalDate(2026, 6, 14), startDayOfWeek = DayOfWeek.MONDAY) // 0
 * weeksBetween(from = monday, to = LocalDate(2026, 6, 15), startDayOfWeek = DayOfWeek.MONDAY) // 1
 * ```
 */
public fun weeksBetween(from: LocalDate, to: LocalDate, startDayOfWeek: DayOfWeek): Int {
    val fromWeekStart = from.startOfWeek(startDayOfWeek)
    val toWeekStart = to.startOfWeek(startDayOfWeek)
    return ((toWeekStart.toEpochDays() - fromWeekStart.toEpochDays()) / DAYS_PER_WEEK).toInt()
}

/**
 * Whole months from [from] to [to], counted between the two dates' month starts, so any two dates in
 * the same month are zero months apart. Negative when [to] precedes [from].
 *
 * ```kotlin
 * monthsBetween(from = LocalDate(2026, 6, 15), to = LocalDate(2026, 7, 1))  // 1
 * monthsBetween(from = LocalDate(2026, 11, 1), to = LocalDate(2027, 2, 1))  // 3
 * ```
 */
public fun monthsBetween(from: LocalDate, to: LocalDate): Int {
    val fromMonthStart = from.startOfMonth()
    val toMonthStart = to.startOfMonth()
    return (toMonthStart.year - fromMonthStart.year) * MONTHS_PER_YEAR +
        (toMonthStart.month.ordinal - fromMonthStart.month.ordinal)
}

/**
 * Whole years from [from] to [to] — a plain difference of calendar years, so two dates in the same
 * year are zero years apart. Negative when [to] precedes [from].
 *
 * ```kotlin
 * yearsBetween(from = LocalDate(2026, 6, 1), to = LocalDate(2027, 1, 1)) // 1
 * ```
 */
public fun yearsBetween(from: LocalDate, to: LocalDate): Int = to.year - from.year
