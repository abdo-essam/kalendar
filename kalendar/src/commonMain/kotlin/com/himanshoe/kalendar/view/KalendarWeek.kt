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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.himanshoe.kalendar.foundation.event.KalendarEvent
import com.himanshoe.kalendar.foundation.event.KalendarEvents
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.plus

/**
 * A single-week-row calendar navigable by both arrow-click and swipe.
 *
 * A single [KalendarViewState] backs both navigation paths — the arrow buttons in the header and
 * a horizontal swipe on the row move the same underlying pager, so they always stay in sync.
 *
 * ```kotlin
 * val state = rememberKalendarWeekState(selectedDate = today)
 * var selected by remember { mutableStateOf(setOf(today)) }
 *
 * KalendarWeek(
 *     selectedDate = today,
 *     state = state,
 *     selectedDates = selected,
 *     onDateClick = { date, _ -> selected = setOf(date) },
 * )
 * ```
 *
 * @param selectedDate The date used as the default single-selection highlight and, when [state]
 *   is left at its default, the week initially visible. Superseded by [selectedDates] when that
 *   is supplied explicitly.
 * @param modifier [Modifier] applied to the outermost container.
 * @param state Navigation state from [rememberKalendarWeekState]. Own it yourself to read
 *   [KalendarViewState.visibleDate] (e.g. for a screen title) or call
 *   [KalendarViewState.animateScrollTo] from a button.
 * @param events Events shown as indicator dots on day cells.
 * @param selectedDates The set of highlighted dates. Defaults to a single-element set containing
 *   [selectedDate]; pass your own hoisted set for multi-select or range selection (see
 *   [datesUntil] for a range-selection helper). Hoist/`remember` the same instance across
 *   recompositions rather than building a fresh [Set] every time.
 * @param onDateClick Called when a non-disabled date is tapped, with the events on that date.
 * @param onDateRangeSelect When non-null, press-and-hold on a date then drag to select a range —
 *   called continuously with (the date under the initial press, the date under the pointer now)
 *   as the drag moves. Pair with [onDateRangeSelectEnd] and, for the common case, wire both to
 *   [KalendarSelectionState.onRangeDrag]/[KalendarSelectionState.onRangeDragEnd].
 * @param onDateRangeSelectEnd Called once when a drag started by [onDateRangeSelect] ends.
 * @param config Shared visual/behavioural settings — disabled dates, arrow visibility, background.
 * @param dayContent Replaces the default day-cell rendering. Receives a [KalendarDayScope]
 *   describing the date being rendered.
 */
@Composable
public fun KalendarWeek(
    selectedDate: LocalDate,
    modifier: Modifier = Modifier,
    state: KalendarViewState = rememberKalendarWeekState(selectedDate),
    events: KalendarEvents = emptyList(),
    selectedDates: Set<LocalDate> = setOf(selectedDate),
    onDateClick: (date: LocalDate, events: List<KalendarEvent>) -> Unit = { _, _ -> },
    onDateRangeSelect: ((start: LocalDate, current: LocalDate) -> Unit)? = null,
    onDateRangeSelectEnd: (() -> Unit)? = null,
    config: KalendarViewConfig = KalendarViewConfig(),
    dayContent: (@Composable (scope: KalendarDayScope) -> Unit)? = null,
) {
    val today = remember { todayInSystemTimeZone() }
    val eventsByDate = remember(events) { events.byDateExpandingSpans() }
    val slidingSelection = if (dayContent == null) selectedDates.singleOrNull() else null

    Column(modifier = modifier.background(config.background)) {
        KalendarNavigationHeader(
            title = remember(state.visibleDate) { weekTitle(state.visibleDate, config.monthNameFormatter) },
            state = state,
            showArrows = config.showNavigationArrows,
            showTodayButton = config.showTodayButton,
        )
        HorizontalPager(
            state = state.pagerState,
            modifier = Modifier.fillMaxWidth(),
        ) { page ->
            val weekStart = state.pageToDate(page)
            val dates = remember(weekStart) { weekDates(weekStart) }
            KalendarGrid(
                dates = dates,
                startDayOfWeek = state.startDayOfWeek,
                selectedDate = slidingSelection,
                dayLabelFormatter = config.dayOfWeekLabelFormatter,
                onDateRangeDrag = onDateRangeSelect,
                onDateRangeDragEnd = onDateRangeSelectEnd,
            ) { date ->
                val dateEvents = eventsByDate[date] ?: emptyList()
                val scope = KalendarDayScope(
                    date = date,
                    isSelected = date in selectedDates,
                    isToday = date == today,
                    isDisabled = config.disabledDates(date),
                    events = dateEvents,
                )
                if (dayContent != null) {
                    dayContent(scope)
                } else {
                    KalendarDayCellDefaults.Cell(
                        scope = scope,
                        onClick = { onDateClick(date, dateEvents) },
                        showSelectionBackground = date != slidingSelection,
                        monthNameFormatter = config.monthNameFormatter,
                    )
                }
            }
        }
    }
}

/**
 * A week's title, kept as short as the header has room for: "Aug 10–16, 2026" when the whole week
 * falls in one month, "Aug 28 – Sep 3, 2026" across a month boundary, or
 * "Dec 29, 2026 – Jan 4, 2027" across a year boundary — repeating the month/year only where it
 * actually changes, rather than spelling out both ends in full every time.
 */
private fun weekTitle(weekStart: LocalDate, monthNameFormatter: (Month) -> String): String {
    val weekEnd = weekStart.plus(6, DateTimeUnit.DAY)
    val monthName = monthNameFormatter(weekStart.month).take(3)
    return when {
        weekStart.year != weekEnd.year -> {
            val startLabel = shortDateLabel(weekStart, monthNameFormatter)
            val endLabel = shortDateLabel(weekEnd, monthNameFormatter)
            "$startLabel, ${weekStart.year} – $endLabel, ${weekEnd.year}"
        }
        weekStart.month != weekEnd.month -> {
            val startLabel = shortDateLabel(weekStart, monthNameFormatter)
            val endLabel = shortDateLabel(weekEnd, monthNameFormatter)
            "$startLabel – $endLabel, ${weekEnd.year}"
        }
        else -> "$monthName ${weekStart.dayOfMonth}–${weekEnd.dayOfMonth}, ${weekEnd.year}"
    }
}

private fun shortDateLabel(date: LocalDate, monthNameFormatter: (Month) -> String): String =
    "${monthNameFormatter(date.month).take(3)} ${date.dayOfMonth}"
