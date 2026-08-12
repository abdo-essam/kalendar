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

import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

internal const val KALENDAR_PAGER_CENTER_PAGE: Int = Int.MAX_VALUE / 2

/**
 * Hoisted navigation state for [KalendarWeek], [KalendarMonth], and [KalendarYear].
 *
 * Wraps a [PagerState] and exposes it in date terms: [visibleDate] is the start of the page
 * currently on screen, [canScrollBackward]/[canScrollForward] report whether stepping one page in
 * either direction stays within [minDate]/[maxDate], and [animateScrollTo] moves to any date.
 * Obtain an instance via [rememberKalendarWeekState], [rememberKalendarMonthState], or
 * [rememberKalendarYearState] — each knows how to translate its own page unit (week/month/year)
 * to and from a [LocalDate].
 */
@Stable
class KalendarViewState internal constructor(
    internal val pagerState: PagerState,
    internal val pageToDate: (Int) -> LocalDate,
    internal val dateToPage: (LocalDate) -> Int,
    val startDayOfWeek: DayOfWeek,
    val minDate: LocalDate?,
    val maxDate: LocalDate?,
) {
    /** The start date of the page currently visible in the pager. */
    val visibleDate: LocalDate by derivedStateOf { pageToDate(pagerState.currentPage) }

    /** Whether stepping one page backward stays at or after [minDate]. */
    val canScrollBackward: Boolean by derivedStateOf {
        minDate == null || pageToDate(pagerState.currentPage - 1) >= minDate
    }

    /** Whether stepping one page forward stays at or before [maxDate]. */
    val canScrollForward: Boolean by derivedStateOf {
        maxDate == null || pageToDate(pagerState.currentPage + 1) <= maxDate
    }

    /** Animates the pager so the page containing [date] is visible. */
    suspend fun animateScrollTo(date: LocalDate) {
        pagerState.animateScrollToPage(dateToPage(date))
    }

    /** Animates the pager back to the page containing today's date. */
    suspend fun animateScrollToToday() {
        animateScrollTo(todayInSystemTimeZone())
    }
}

/**
 * Creates and [remember]s a [KalendarViewState] for [KalendarWeek], paging by week.
 *
 * @param initialDate The date whose week is initially visible. Defaults to today.
 * @param startDayOfWeek The first day of each week column. Defaults to [DayOfWeek.MONDAY].
 * @param minDate When set, [KalendarViewState.canScrollBackward] turns `false` once the visible
 *   week would start before the week containing this date.
 * @param maxDate When set, [KalendarViewState.canScrollForward] turns `false` once the visible
 *   week would start after the week containing this date.
 */
@Composable
fun rememberKalendarWeekState(
    initialDate: LocalDate = todayInSystemTimeZone(),
    startDayOfWeek: DayOfWeek = DayOfWeek.MONDAY,
    minDate: LocalDate? = null,
    maxDate: LocalDate? = null,
): KalendarViewState {
    val anchor = remember(initialDate, startDayOfWeek) { initialDate.startOfWeek(startDayOfWeek) }
    val pagerState = rememberPagerState(
        initialPage = KALENDAR_PAGER_CENTER_PAGE,
        pageCount = { Int.MAX_VALUE },
    )
    return remember(pagerState, anchor, startDayOfWeek, minDate, maxDate) {
        KalendarViewState(
            pagerState = pagerState,
            pageToDate = { page -> anchor.plus((page - KALENDAR_PAGER_CENTER_PAGE) * 7, DateTimeUnit.DAY) },
            dateToPage = { date ->
                KALENDAR_PAGER_CENTER_PAGE + weeksBetween(from = anchor, to = date, startDayOfWeek = startDayOfWeek)
            },
            startDayOfWeek = startDayOfWeek,
            minDate = minDate,
            maxDate = maxDate,
        )
    }
}

/**
 * Creates and [remember]s a [KalendarViewState] for [KalendarMonth], paging by month.
 *
 * @param initialDate The date whose month is initially visible. Defaults to today.
 * @param startDayOfWeek The first day of each week column. Defaults to [DayOfWeek.MONDAY].
 * @param minDate When set, [KalendarViewState.canScrollBackward] turns `false` once the visible
 *   month would start before the month containing this date.
 * @param maxDate When set, [KalendarViewState.canScrollForward] turns `false` once the visible
 *   month would start after the month containing this date.
 */
@Composable
fun rememberKalendarMonthState(
    initialDate: LocalDate = todayInSystemTimeZone(),
    startDayOfWeek: DayOfWeek = DayOfWeek.MONDAY,
    minDate: LocalDate? = null,
    maxDate: LocalDate? = null,
): KalendarViewState {
    val anchor = remember(initialDate) { initialDate.startOfMonth() }
    val pagerState = rememberPagerState(
        initialPage = KALENDAR_PAGER_CENTER_PAGE,
        pageCount = { Int.MAX_VALUE },
    )
    return remember(pagerState, anchor, startDayOfWeek, minDate, maxDate) {
        KalendarViewState(
            pagerState = pagerState,
            pageToDate = { page -> anchor.plus(page - KALENDAR_PAGER_CENTER_PAGE, DateTimeUnit.MONTH) },
            dateToPage = { date -> KALENDAR_PAGER_CENTER_PAGE + monthsBetween(from = anchor, to = date) },
            startDayOfWeek = startDayOfWeek,
            minDate = minDate,
            maxDate = maxDate,
        )
    }
}

/**
 * Creates and [remember]s a [KalendarViewState] for [KalendarYear], paging by year.
 *
 * @param initialDate The date whose year is initially visible. Defaults to today.
 * @param startDayOfWeek The first day of each week column. Defaults to [DayOfWeek.MONDAY].
 * @param minDate When set, [KalendarViewState.canScrollBackward] turns `false` once the visible
 *   year would start before the year containing this date.
 * @param maxDate When set, [KalendarViewState.canScrollForward] turns `false` once the visible
 *   year would start after the year containing this date.
 */
@Composable
fun rememberKalendarYearState(
    initialDate: LocalDate = todayInSystemTimeZone(),
    startDayOfWeek: DayOfWeek = DayOfWeek.MONDAY,
    minDate: LocalDate? = null,
    maxDate: LocalDate? = null,
): KalendarViewState {
    val anchor = remember(initialDate) { initialDate.startOfYear() }
    val pagerState = rememberPagerState(
        initialPage = KALENDAR_PAGER_CENTER_PAGE,
        pageCount = { Int.MAX_VALUE },
    )
    return remember(pagerState, anchor, startDayOfWeek, minDate, maxDate) {
        KalendarViewState(
            pagerState = pagerState,
            pageToDate = { page -> anchor.plus(page - KALENDAR_PAGER_CENTER_PAGE, DateTimeUnit.YEAR) },
            dateToPage = { date -> KALENDAR_PAGER_CENTER_PAGE + yearsBetween(from = anchor, to = date) },
            startDayOfWeek = startDayOfWeek,
            minDate = minDate,
            maxDate = maxDate,
        )
    }
}
