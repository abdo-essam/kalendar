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
import kotlinx.datetime.plus

/**
 * The ISO-8601 week number of this date, `1..53`.
 *
 * ISO weeks run Monday to Sunday, and week 1 is the week containing the year's first Thursday.
 * Around New Year the number therefore belongs to [isoWeekYear] rather than to [LocalDate.year] —
 * the two can disagree for up to three days at either end of a year, which is why the pair is
 * meaningless apart:
 *
 * ```kotlin
 * LocalDate(2021, 1, 1).isoWeekNumber()   // 53 — week 53 of ISO year 2020
 * LocalDate(2019, 12, 30).isoWeekNumber() // 1  — week 1 of ISO year 2020
 * LocalDate(2026, 12, 31).isoWeekNumber() // 53 — 2026 is a 53-week year
 * ```
 */
public fun LocalDate.isoWeekNumber(): Int = (isoWeekThursday().dayOfYear + DAYS_PER_WEEK - 1) / DAYS_PER_WEEK

/**
 * The ISO-8601 week-based year this date's week belongs to — the year that owns the week's
 * Thursday. Equal to [LocalDate.year] for all but the first and last few days of a year:
 *
 * ```kotlin
 * LocalDate(2021, 1, 1).isoWeekYear()   // 2020
 * LocalDate(2019, 12, 30).isoWeekYear() // 2020
 * LocalDate(2021, 6, 15).isoWeekYear()  // 2021
 * ```
 */
public fun LocalDate.isoWeekYear(): Int = isoWeekThursday().year

/**
 * The Monday inside the displayed week containing this date, where the displayed week begins on
 * [startDayOfWeek] — the date whose [isoWeekNumber] labels that week's row.
 *
 * ISO weeks are Monday-based, but a calendar row need not be: a Sunday-started row spans two ISO
 * weeks, its leading Sunday belonging to the earlier one. The other six days of the row share one
 * ISO week — the week of the row's Monday — and that is the number a European user expects beside
 * a Sunday-started row, so a week-number gutter labels a row by this date rather than by the row's
 * first.
 *
 * ```kotlin
 * val sunday = LocalDate(2026, 8, 30)
 * sunday.mondayOfWeek(DayOfWeek.SUNDAY) // 2026-08-31 — the Monday its row runs into
 * sunday.mondayOfWeek(DayOfWeek.MONDAY) // 2026-08-24 — the Monday its row started on
 * ```
 */
public fun LocalDate.mondayOfWeek(startDayOfWeek: DayOfWeek): LocalDate = startOfWeek(startDayOfWeek)
    .plus((DayOfWeek.MONDAY.ordinal - startDayOfWeek.ordinal + DAYS_PER_WEEK) % DAYS_PER_WEEK, DateTimeUnit.DAY)

/**
 * The Thursday of this date's Monday-based ISO week — the day ISO 8601 pivots on: a week belongs
 * to whichever calendar year its Thursday falls in, which is what makes both the week number and
 * the week-based year a question about this one date.
 */
private fun LocalDate.isoWeekThursday(): LocalDate =
    startOfWeek(DayOfWeek.MONDAY).plus(DAYS_FROM_MONDAY_TO_THURSDAY, DateTimeUnit.DAY)

/** How far a week's pivot day sits from its Monday; see [isoWeekThursday]. */
private const val DAYS_FROM_MONDAY_TO_THURSDAY = 3
