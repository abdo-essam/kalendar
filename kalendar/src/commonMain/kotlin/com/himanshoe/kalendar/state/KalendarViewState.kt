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

package com.himanshoe.kalendar.state

import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.himanshoe.kalendar.foundation.datetime.KalendarTimeSource
import com.himanshoe.kalendar.foundation.paging.KalendarPageUnit
import com.himanshoe.kalendar.foundation.paging.KalendarPager
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate

/**
 * Hoisted navigation state for [KalendarWeek], [KalendarMonth], and [KalendarYear].
 *
 * Wraps a [PagerState] and exposes it in date terms: [visibleDate] is the start of the page
 * currently on screen, [canScrollBackward]/[canScrollForward] report whether stepping one page in
 * either direction stays within [minDate]/[maxDate], and [animateScrollTo] moves to any date.
 * Obtain an instance via [rememberKalendarWeekState], [rememberKalendarMonthState], or
 * [rememberKalendarYearState] — each knows how to translate its own page unit (week/month/year)
 * to and from a [LocalDate].
 *
 * The page-to-date arithmetic itself is [KalendarPager], which is public and has no Compose in it:
 * drive your own `LazyRow` or `ViewPager` from one to get exactly the paging this state gives the
 * built-in views.
 *
 * There is deliberately no `today` property here. A clock-derived value on a `@Stable` class
 * notifies nobody when the day rolls over, so it would be stale by the next midnight — read today
 * with [rememberKalendarToday] (`rememberKalendarToday(state.timeSource)`) instead, which is
 * snapshot-observable. [animateScrollToToday] may read the clock directly because it is `suspend`
 * and therefore reads it at the moment it is called.
 */
@Stable
public class KalendarViewState internal constructor(
    internal val pagerState: PagerState,
    internal val pager: KalendarPager,
    timeSource: KalendarTimeSource,
) {
    /**
     * Where this state reads "today" from. Snapshot-backed, so the `remember*State` factory that
     * owns this instance can keep it in step with the ambient [KalendarTimeSource] without
     * recreating the state (and losing the pager's scroll position).
     */
    public var timeSource: KalendarTimeSource by mutableStateOf(timeSource)

    /** The first day of each week column, as passed to the factory that built this state. */
    public val startDayOfWeek: DayOfWeek get() = pager.startDayOfWeek

    /** The earliest date this calendar may scroll to, or `null` when unbounded. */
    public val minDate: LocalDate? get() = pager.minDate

    /** The latest date this calendar may scroll to, or `null` when unbounded. */
    public val maxDate: LocalDate? get() = pager.maxDate

    /** The start date of the page currently visible in the pager. */
    public val visibleDate: LocalDate by derivedStateOf { pager.dateAt(pagerState.currentPage) }

    /**
     * Whether stepping one page backward stays within [minDate]'s page. Compared in page space so
     * a mid-period [minDate] (e.g. the 15th of a month) still allows reaching the page that
     * contains it.
     */
    public val canScrollBackward: Boolean by derivedStateOf {
        pagerState.currentPage - 1 >= pager.firstPage
    }

    /** Whether stepping one page forward stays within [maxDate]'s page. */
    public val canScrollForward: Boolean by derivedStateOf {
        pagerState.currentPage + 1 <= pager.lastPage
    }

    /**
     * Animates the pager so the page containing [date] is visible, clamped to the pages
     * containing [minDate]/[maxDate] when bounds are set — the today button and the header's
     * jump picker route through here, so out-of-bounds targets land on the nearest bound.
     */
    public suspend fun animateScrollTo(date: LocalDate) {
        pagerState.animateScrollToPage(pager.pageOf(date))
    }

    /** Animates the pager back to the page containing today's date, clamped to the bounds. */
    public suspend fun animateScrollToToday() {
        animateScrollTo(timeSource.today())
    }

    /**
     * Animates one page backward — the previous week, month, year, or day depending on which
     * `remember*State` created this instance. Clamped to [minDate], so calling it when
     * [canScrollBackward] is `false` is a no-op rather than an out-of-range scroll.
     */
    public suspend fun animateToPreviousPage() {
        pagerState.animateScrollToPage(pager.coercePage(pagerState.currentPage - 1))
    }

    /** Animates one page forward, clamped to [maxDate]. See [animateToPreviousPage]. */
    public suspend fun animateToNextPage() {
        pagerState.animateScrollToPage(pager.coercePage(pagerState.currentPage + 1))
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
 * @param timeSource Where the state reads "today" from. Defaults to the ambient
 *   [KalendarTimeSource]; see [KalendarViewState.timeSource].
 */
@Composable
public fun rememberKalendarWeekState(
    initialDate: LocalDate = rememberInitialKalendarDate(),
    startDayOfWeek: DayOfWeek = DayOfWeek.MONDAY,
    minDate: LocalDate? = null,
    maxDate: LocalDate? = null,
    timeSource: KalendarTimeSource = rememberKalendarTimeSource(),
): KalendarViewState = rememberKalendarPagedState(
    unit = KalendarPageUnit.Week,
    initialDate = initialDate,
    startDayOfWeek = startDayOfWeek,
    minDate = minDate,
    maxDate = maxDate,
    timeSource = timeSource,
)

/**
 * Creates and [remember]s a [KalendarViewState] for [KalendarMonth], paging by month.
 *
 * @param initialDate The date whose month is initially visible. Defaults to today.
 * @param startDayOfWeek The first day of each week column. Defaults to [DayOfWeek.MONDAY].
 * @param minDate When set, [KalendarViewState.canScrollBackward] turns `false` once the visible
 *   month would start before the month containing this date.
 * @param maxDate When set, [KalendarViewState.canScrollForward] turns `false` once the visible
 *   month would start after the month containing this date.
 * @param timeSource Where the state reads "today" from. Defaults to the ambient
 *   [KalendarTimeSource]; see [KalendarViewState.timeSource].
 */
@Composable
public fun rememberKalendarMonthState(
    initialDate: LocalDate = rememberInitialKalendarDate(),
    startDayOfWeek: DayOfWeek = DayOfWeek.MONDAY,
    minDate: LocalDate? = null,
    maxDate: LocalDate? = null,
    timeSource: KalendarTimeSource = rememberKalendarTimeSource(),
): KalendarViewState = rememberKalendarPagedState(
    unit = KalendarPageUnit.Month,
    initialDate = initialDate,
    startDayOfWeek = startDayOfWeek,
    minDate = minDate,
    maxDate = maxDate,
    timeSource = timeSource,
)

/**
 * Creates and [remember]s a [KalendarViewState] for [KalendarSchedule], paging by single day.
 *
 * @param initialDate The day initially visible. Defaults to today.
 * @param minDate When set, [KalendarViewState.canScrollBackward] turns `false` at this date.
 * @param maxDate When set, [KalendarViewState.canScrollForward] turns `false` at this date.
 * @param timeSource Where the state reads "today" from. Defaults to the ambient
 *   [KalendarTimeSource]; see [KalendarViewState.timeSource].
 */
@Composable
public fun rememberKalendarScheduleState(
    initialDate: LocalDate = rememberInitialKalendarDate(),
    minDate: LocalDate? = null,
    maxDate: LocalDate? = null,
    timeSource: KalendarTimeSource = rememberKalendarTimeSource(),
): KalendarViewState = rememberKalendarPagedState(
    unit = KalendarPageUnit.Day,
    initialDate = initialDate,
    startDayOfWeek = DayOfWeek.MONDAY,
    minDate = minDate,
    maxDate = maxDate,
    timeSource = timeSource,
)

/**
 * Creates and [remember]s a [KalendarViewState] for [KalendarYear], paging by year.
 *
 * @param initialDate The date whose year is initially visible. Defaults to today.
 * @param startDayOfWeek The first day of each week column. Defaults to [DayOfWeek.MONDAY].
 * @param minDate When set, [KalendarViewState.canScrollBackward] turns `false` once the visible
 *   year would start before the year containing this date.
 * @param maxDate When set, [KalendarViewState.canScrollForward] turns `false` once the visible
 *   year would start after the year containing this date.
 * @param timeSource Where the state reads "today" from. Defaults to the ambient
 *   [KalendarTimeSource]; see [KalendarViewState.timeSource].
 */
@Composable
public fun rememberKalendarYearState(
    initialDate: LocalDate = rememberInitialKalendarDate(),
    startDayOfWeek: DayOfWeek = DayOfWeek.MONDAY,
    minDate: LocalDate? = null,
    maxDate: LocalDate? = null,
    timeSource: KalendarTimeSource = rememberKalendarTimeSource(),
): KalendarViewState = rememberKalendarPagedState(
    unit = KalendarPageUnit.Year,
    initialDate = initialDate,
    startDayOfWeek = startDayOfWeek,
    minDate = minDate,
    maxDate = maxDate,
    timeSource = timeSource,
)

/**
 * The one body behind every `rememberKalendar*State`: a [KalendarPager] for [unit] bound to a
 * [PagerState] that spans it.
 *
 * The pager is [KalendarPager.endless] rather than [KalendarPager.bounded] because a swipeable
 * calendar should let the user swipe back from wherever they opened it; [minDate]/[maxDate] gate the
 * arrows and every programmatic jump through [KalendarPager.firstPage]/[KalendarPager.lastPage].
 */
@Composable
private fun rememberKalendarPagedState(
    unit: KalendarPageUnit,
    initialDate: LocalDate,
    startDayOfWeek: DayOfWeek,
    minDate: LocalDate?,
    maxDate: LocalDate?,
    timeSource: KalendarTimeSource,
): KalendarViewState {
    val pager = remember(unit, initialDate, startDayOfWeek, minDate, maxDate) {
        KalendarPager.endless(
            unit = unit,
            initialDate = initialDate,
            startDayOfWeek = startDayOfWeek,
            minDate = minDate,
            maxDate = maxDate,
        )
    }
    val pagerState = rememberPagerState(
        initialPage = pager.initialPage,
        pageCount = { pager.pageCount },
    )
    val state = remember(pagerState, pager) {
        KalendarViewState(pagerState = pagerState, pager = pager, timeSource = timeSource)
    }
    state.syncTimeSource(timeSource)
    return state
}

/**
 * Keeps [KalendarViewState.timeSource] in step with the value the factory was handed, without
 * recreating the state.
 *
 * The equality guard is load-bearing rather than an optimisation: a caller passing a time source
 * built inline — an object-expression [kotlin.time.Clock], say, which has identity equality — would
 * otherwise write a new value into a snapshot state on every pass and recompose forever. It also
 * means a time source must be *remembered* to be honoured at all; see [KalendarTimeSource].
 */
@Composable
private fun KalendarViewState.syncTimeSource(timeSource: KalendarTimeSource) {
    SideEffect {
        if (this.timeSource != timeSource) {
            this.timeSource = timeSource
        }
    }
}
