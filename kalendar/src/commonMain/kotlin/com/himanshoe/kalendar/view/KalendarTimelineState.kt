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
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

private const val KALENDAR_TIMELINE_CENTER_INDEX: Int = Int.MAX_VALUE / 2

/**
 * Hoisted scroll state for [KalendarTimeline].
 *
 * Wraps a [LazyListState] and exposes it in date terms: [visibleDate] is the start of the month
 * currently topmost on screen, and [scrollTo] animates to any date. Obtain an instance via
 * [rememberKalendarTimelineState].
 */
@Stable
public class KalendarTimelineState internal constructor(
    internal val lazyListState: LazyListState,
    internal val itemCount: Int,
    internal val indexToDate: (Int) -> LocalDate,
    private val dateToIndex: (LocalDate) -> Int,
    public val startDayOfWeek: DayOfWeek,
    public val minDate: LocalDate?,
    public val maxDate: LocalDate?,
) {
    /** The start date of the month currently topmost in the list. */
    public val visibleDate: LocalDate by derivedStateOf { indexToDate(lazyListState.firstVisibleItemIndex) }

    /** Animates the list so the month containing [date] scrolls to the top. */
    public suspend fun scrollTo(date: LocalDate) {
        lazyListState.animateScrollToItem(dateToIndex(date))
    }

    /** Animates the list back to the month containing today's date. */
    public suspend fun scrollToToday() {
        scrollTo(todayInSystemTimeZone())
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
 */
@Composable
public fun rememberKalendarTimelineState(
    initialDate: LocalDate = todayInSystemTimeZone(),
    startDayOfWeek: DayOfWeek = DayOfWeek.MONDAY,
    minDate: LocalDate? = null,
    maxDate: LocalDate? = null,
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
    val initialIndex = remember(indexAnchor, baseIndex, anchor) {
        baseIndex + monthsBetween(from = indexAnchor, to = anchor)
    }
    val lazyListState = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)

    return remember(lazyListState, itemCount, indexAnchor, baseIndex, startDayOfWeek, minDate, maxDate) {
        KalendarTimelineState(
            lazyListState = lazyListState,
            itemCount = itemCount,
            indexToDate = { index -> indexAnchor.plus(index - baseIndex, DateTimeUnit.MONTH) },
            dateToIndex = { date -> baseIndex + monthsBetween(from = indexAnchor, to = date) },
            startDayOfWeek = startDayOfWeek,
            minDate = minDate,
            maxDate = maxDate,
        )
    }
}
