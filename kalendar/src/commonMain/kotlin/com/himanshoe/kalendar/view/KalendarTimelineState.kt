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
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

private const val KALENDAR_TIMELINE_CENTER_INDEX: Int = Int.MAX_VALUE / 2

/**
 * Converts a months-from-anchor offset into a valid list index, clamping to `[0, itemCount)` with
 * overflow-safe [Long] math — with only `maxDate` set the base index sits near [Int.MAX_VALUE],
 * so an out-of-bounds date can otherwise wrap negative and crash the lazy list.
 */
internal fun clampedTimelineIndex(baseIndex: Int, monthsFromAnchor: Int, itemCount: Int): Int {
    val raw = baseIndex.toLong() + monthsFromAnchor.toLong()
    return raw.coerceIn(0L, (itemCount - 1).toLong()).toInt()
}

/**
 * Hoisted scroll state for [KalendarTimeline].
 *
 * Wraps a [LazyListState] and exposes it in date terms: [visibleDate] is the start of the month
 * currently topmost on screen, and [animateScrollTo] animates to any date. Obtain an instance via
 * [rememberKalendarTimelineState].
 *
 * As on [KalendarViewState] there is no `today` property — read it with
 * `rememberKalendarToday(state.timeSource)`, which stays snapshot-observable across midnight.
 */
// The constructor takes one parameter over detekt's threshold. Every one of them is a distinct
// coordinate of the same list (its state, its extent, its two index/date mappings, its bounds) with
// no sub-grouping that would read as a type of its own, so bundling would only add indirection.
@Suppress("LongParameterList")
@Stable
public class KalendarTimelineState internal constructor(
    internal val lazyListState: LazyListState,
    internal val itemCount: Int,
    internal val indexToDate: (Int) -> LocalDate,
    private val dateToIndex: (LocalDate) -> Int,
    public val startDayOfWeek: DayOfWeek,
    public val minDate: LocalDate?,
    public val maxDate: LocalDate?,
    timeSource: KalendarTimeSource,
) {
    /** Where this state reads "today" from. See [KalendarViewState.timeSource]. */
    public var timeSource: KalendarTimeSource by mutableStateOf(timeSource)

    /** The start date of the month currently topmost in the list. */
    public val visibleDate: LocalDate by derivedStateOf { indexToDate(lazyListState.firstVisibleItemIndex) }

    /**
     * Animates the list so the month containing [date] scrolls to the top.
     *
     * Named for what it does: this animates rather than jumps, matching
     * [KalendarViewState.animateScrollTo] and every `animateScrollTo*` in AndroidX.
     */
    public suspend fun animateScrollTo(date: LocalDate) {
        lazyListState.animateScrollToItem(dateToIndex(date))
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
    val anchor = remember(initialDate) { initialDate.startOfMonth() }
    val minMonth = remember(minDate) { minDate?.startOfMonth() }
    val maxMonth = remember(maxDate) { maxDate?.startOfMonth() }

    val indexAnchor = remember(minMonth, maxMonth, anchor) {
        minMonth ?: maxMonth ?: anchor
    }
    val baseIndex = remember(minMonth, maxMonth) {
        when {
            minMonth != null -> 0
            maxMonth != null -> Int.MAX_VALUE - 1
            else -> KALENDAR_TIMELINE_CENTER_INDEX
        }
    }
    val itemCount = remember(minMonth, maxMonth) {
        if (minMonth != null && maxMonth != null) {
            monthsBetween(from = minMonth, to = maxMonth) + 1
        } else {
            Int.MAX_VALUE
        }
    }
    val initialIndex = remember(indexAnchor, baseIndex, anchor, itemCount) {
        clampedTimelineIndex(
            baseIndex = baseIndex,
            monthsFromAnchor = monthsBetween(from = indexAnchor, to = anchor),
            itemCount = itemCount,
        )
    }
    val lazyListState = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)

    val state = remember(lazyListState, itemCount, indexAnchor, baseIndex, startDayOfWeek, minDate, maxDate) {
        KalendarTimelineState(
            lazyListState = lazyListState,
            itemCount = itemCount,
            indexToDate = { index -> indexAnchor.plus(index - baseIndex, DateTimeUnit.MONTH) },
            dateToIndex = { date ->
                clampedTimelineIndex(
                    baseIndex = baseIndex,
                    monthsFromAnchor = monthsBetween(from = indexAnchor, to = date),
                    itemCount = itemCount,
                )
            },
            startDayOfWeek = startDayOfWeek,
            minDate = minDate,
            maxDate = maxDate,
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
