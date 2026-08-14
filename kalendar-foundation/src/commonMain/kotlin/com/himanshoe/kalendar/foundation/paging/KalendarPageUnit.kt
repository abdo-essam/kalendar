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

package com.himanshoe.kalendar.foundation.paging

import com.himanshoe.kalendar.foundation.datetime.DAYS_PER_WEEK
import com.himanshoe.kalendar.foundation.datetime.monthsBetween
import com.himanshoe.kalendar.foundation.datetime.startOfMonth
import com.himanshoe.kalendar.foundation.datetime.startOfWeek
import com.himanshoe.kalendar.foundation.datetime.startOfYear
import com.himanshoe.kalendar.foundation.datetime.weeksBetween
import com.himanshoe.kalendar.foundation.datetime.yearsBetween
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

/**
 * How much of the calendar one page shows, and so what "the next page" means: a day, a week, a month
 * or a year.
 *
 * This is the whole per-view difference between a day schedule, a week strip, a month grid and a
 * year overview — everything else about paging is the same arithmetic. [KalendarPager] is built on
 * exactly these three operations; reach for the unit directly when you want the step without the
 * page-index bookkeeping.
 *
 * ```kotlin
 * val unit = KalendarPageUnit.Month
 * val thisPage = unit.startOf(LocalDate(2026, 8, 15))          // 2026-08-01
 * val nextPage = unit.plusPages(thisPage, 1)                   // 2026-09-01
 * unit.pagesBetween(from = thisPage, to = LocalDate(2027, 2, 3)) // 6
 * ```
 */
public enum class KalendarPageUnit {

    /** One page is one day — a schedule's single-day view. */
    Day,

    /** One page is one week, beginning at the caller's `startDayOfWeek`. */
    Week,

    /** One page is one calendar month. */
    Month,

    /** One page is one calendar year. */
    Year;

    /**
     * The date that keys the page containing [date] — the first day of its day/week/month/year.
     *
     * Two dates on the same page always give the same answer, which is what makes it safe to use as
     * a page identity.
     *
     * ```kotlin
     * KalendarPageUnit.Week.startOf(LocalDate(2026, 6, 10), DayOfWeek.MONDAY) // 2026-06-08
     * ```
     *
     * @param startDayOfWeek Which weekday a week begins on. Read by [Week] only; ignored otherwise.
     */
    public fun startOf(date: LocalDate, startDayOfWeek: DayOfWeek = DayOfWeek.MONDAY): LocalDate = when (this) {
        Day -> date
        Week -> date.startOfWeek(startDayOfWeek)
        Month -> date.startOfMonth()
        Year -> date.startOfYear()
    }

    /**
     * [date] moved [pages] pages forward, or backward for a negative count. The day of the month is
     * preserved where the unit allows it, exactly as `kotlinx-datetime`'s own `plus` does.
     *
     * ```kotlin
     * KalendarPageUnit.Month.plusPages(LocalDate(2026, 8, 1), -2) // 2026-06-01
     * ```
     */
    public fun plusPages(date: LocalDate, pages: Int): LocalDate = when (this) {
        Day -> date.plus(pages, DateTimeUnit.DAY)
        Week -> date.plus(pages * DAYS_PER_WEEK, DateTimeUnit.DAY)
        Month -> date.plus(pages, DateTimeUnit.MONTH)
        Year -> date.plus(pages, DateTimeUnit.YEAR)
    }

    /**
     * Whole pages from [from] to [to], counted between the two dates' pages — so two dates on the
     * same page are zero pages apart whatever days of the period they fall on. Negative when [to]
     * precedes [from].
     *
     * ```kotlin
     * KalendarPageUnit.Month.pagesBetween(from = LocalDate(2026, 8, 31), to = LocalDate(2026, 9, 1)) // 1
     * ```
     *
     * @param startDayOfWeek Which weekday a week begins on. Read by [Week] only; ignored otherwise.
     */
    public fun pagesBetween(
        from: LocalDate,
        to: LocalDate,
        startDayOfWeek: DayOfWeek = DayOfWeek.MONDAY,
    ): Int = when (this) {
        Day -> (to.toEpochDays() - from.toEpochDays()).toInt()
        Week -> weeksBetween(from = from, to = to, startDayOfWeek = startDayOfWeek)
        Month -> monthsBetween(from = from, to = to)
        Year -> yearsBetween(from = from, to = to)
    }
}
