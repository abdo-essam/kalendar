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

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
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
 * Hoisted scroll state for [KalendarTimeline].
 *
 * Wraps a [LazyListState] and exposes it in date terms: [visibleDate] is the start of the month
 * currently topmost on screen, and [animateScrollTo] animates to any date. Obtain an instance via
 * [rememberKalendarTimelineState].
 *
 * The index-to-date arithmetic is [KalendarPager], built here with [KalendarPager.bounded] so that
 * index `0` is a real month and [KalendarPager.pageCount] is an item count a lazy list can be sized
 * from — the same type [KalendarViewState] pages with, anchored differently.
 *
 * As on [KalendarViewState] there is no `today` property — read it with
 * `rememberKalendarToday(state.timeSource)`, which stays snapshot-observable across midnight.
 */
@Stable
public class KalendarTimelineState internal constructor(
    internal val lazyListState: LazyListState,
    internal val pager: KalendarPager,
    timeSource: KalendarTimeSource,
) {
    /** Where this state reads "today" from. See [KalendarViewState.timeSource]. */
    public var timeSource: KalendarTimeSource by mutableStateOf(timeSource)

    /** The first day of each week column, as passed to [rememberKalendarTimelineState]. */
    public val startDayOfWeek: DayOfWeek get() = pager.startDayOfWeek

    /** The earliest date this timeline may scroll to, or `null` when unbounded. */
    public val minDate: LocalDate? get() = pager.minDate

    /** The latest date this timeline may scroll to, or `null` when unbounded. */
    public val maxDate: LocalDate? get() = pager.maxDate

    /** The start date of the month currently topmost in the list. */
    public val visibleDate: LocalDate by derivedStateOf { pager.dateAt(lazyListState.firstVisibleItemIndex) }

    /**
     * Animates the list so the month containing [date] scrolls to the top.
     *
     * Named for what it does: this animates rather than jumps, matching
     * [KalendarViewState.animateScrollTo] and every `animateScrollTo*` in AndroidX.
     */
    public suspend fun animateScrollTo(date: LocalDate) {
        lazyListState.animateScrollToItem(pager.pageOf(date))
    }

    /** Animates the list back to the month containing today's date. */
    public suspend fun animateScrollToToday() {
        animateScrollTo(timeSource.today())
    }
}

/**
 * Creates and [remember]s a [KalendarTimelineState] for [KalendarTimeline].
 *
 * @param initialDate The date whose month is initially at the top of the list. Defaults to today.
 * @param startDayOfWeek The first day of each week column. Defaults to [DayOfWeek.MONDAY].
 * @param minDate When set, the list cannot scroll to a month before the month containing this
 *   date. Defaults to `null` — unlimited scroll into the past.
 * @param maxDate When set, the list cannot scroll to a month after the month containing this
 *   date. Defaults to `null` — unlimited scroll into the future.
 * @param timeSource Where the state reads "today" from. Defaults to the ambient
 *   [KalendarTimeSource]; see [KalendarViewState.timeSource].
 */
@Composable
public fun rememberKalendarTimelineState(
    initialDate: LocalDate = rememberInitialKalendarDate(),
    startDayOfWeek: DayOfWeek = DayOfWeek.MONDAY,
    minDate: LocalDate? = null,
    maxDate: LocalDate? = null,
    timeSource: KalendarTimeSource = rememberKalendarTimeSource(),
): KalendarTimelineState {
    val pager = remember(initialDate, startDayOfWeek, minDate, maxDate) {
        KalendarPager.bounded(
            unit = KalendarPageUnit.Month,
            initialDate = initialDate,
            startDayOfWeek = startDayOfWeek,
            minDate = minDate,
            maxDate = maxDate,
        )
    }
    val lazyListState = rememberLazyListState(initialFirstVisibleItemIndex = pager.initialPage)

    val state = remember(lazyListState, pager) {
        KalendarTimelineState(
            lazyListState = lazyListState,
            pager = pager,
            timeSource = timeSource,
        )
    }
    // Guarded exactly as KalendarViewState's writeback is, and for the same reason.
    SideEffect {
        if (state.timeSource != timeSource) {
            state.timeSource = timeSource
        }
    }
    return state
}
