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

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn
import kotlin.time.Clock

internal fun todayInSystemTimeZone(): LocalDate = Clock.System.todayIn(TimeZone.currentSystemDefault())

internal fun LocalDate.startOfWeek(startDayOfWeek: DayOfWeek): LocalDate =
    minus((dayOfWeek.ordinal - startDayOfWeek.ordinal + 7) % 7, DateTimeUnit.DAY)

internal fun LocalDate.startOfMonth(): LocalDate = minus(dayOfMonth - 1, DateTimeUnit.DAY)

internal fun LocalDate.startOfYear(): LocalDate = LocalDate(year, 1, 1)

internal fun weekDates(weekStart: LocalDate): List<LocalDate> =
    (0..6).map { weekStart.plus(it, DateTimeUnit.DAY) }

internal fun monthGridDates(monthStart: LocalDate, startDayOfWeek: DayOfWeek): List<LocalDate> {
    val lastDayOfMonth = monthStart.plus(1, DateTimeUnit.MONTH).minus(1, DateTimeUnit.DAY)
    val leadingPaddingDays = (monthStart.dayOfWeek.ordinal - startDayOfWeek.ordinal + 7) % 7
    return (-leadingPaddingDays until lastDayOfMonth.dayOfMonth).map {
        monthStart.plus(it.toLong(), DateTimeUnit.DAY)
    }
}

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

internal fun daysOfWeekStartingAt(startDayOfWeek: DayOfWeek): List<DayOfWeek> {
    val all = DayOfWeek.entries
    return all.drop(startDayOfWeek.ordinal) + all.take(startDayOfWeek.ordinal)
}

internal fun monthTitle(monthStart: LocalDate): String {
    val monthName = monthStart.month.name.lowercase().replaceFirstChar { it.titlecase() }
    return "$monthName ${monthStart.year}"
}
