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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import com.himanshoe.kalendar.foundation.event.KalendarEvent
import com.himanshoe.kalendar.foundation.event.KalendarEvents
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.plus

/**
 * A single-week-row calendar navigable by both arrow-click and swipe.
 *
 * A single [KalendarViewState] backs both navigation paths — the arrow buttons in the header and
 * a horizontal swipe on the row move the same underlying pager, so they always stay in sync.
 *
 *
 * The grid is keyboard-navigable and is a **single tab stop**: `Tab` enters it at the selected date
 * (or today), arrows move a focus ring one day or one row — paging to the next or previous week
 * when they run off the edge — `Home`/`End` jump to the first and last day of the visible week,
 * `PageUp`/`PageDown` move a whole week, `Enter`/`Space` select, and `Tab` again leaves. Holding
 * `Shift` while arrowing extends a range through `onDateRangeSelect`/`onDateRangeSelectEnd`, which
 * is otherwise reachable by pointer only. A date [KalendarViewConfig.disabledDates] rejects can be
 * focused but not selected, so it is announced rather than silently skipped.
 *
 * ```kotlin
 * val state = rememberKalendarWeekState(initialDate = today)
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
 * @param onDayEventClick Called when a row of a day cell's `+N` overflow popover is tapped, with
 *   the event that row represents; the popover closes itself afterwards. Defaults to `null`, which
 *   leaves the popover a read-only list of what the cell could not show. Has no effect when
 *   [dayContent] replaces the built-in cell — a replacement cell owns its own popover, if any.
 * @param onDateRangeSelect When non-null, press-and-hold on a date then drag to select a range —
 *   called continuously with (the date under the initial press, the date under the pointer now)
 *   as the drag moves. Pair with [onDateRangeSelectEnd] and, for the common case, wire both to
 *   [KalendarSelectionState.onRangeDrag]/[KalendarSelectionState.onRangeDragEnd].
 * @param onDateRangeSelectEnd Called once when a drag started by [onDateRangeSelect] ends.
 * @param config Shared visual/behavioural settings — disabled dates, arrow visibility, background,
 *   and [KalendarViewConfig.visibleDaysOfWeek], which drops a weekday's column from the row, its
 *   header and the keyboard's path through it — a Mon–Fri week is five columns, not seven.
 * @param header Replaces the navigation header above the week row. Receives a [KalendarHeaderScope]
 *   carrying the formatted title and the navigation callbacks, so your own chrome keeps working
 *   arrows and today button. Defaults to [KalendarHeaderDefaults.Header] driven by [config].
 * @param dayOfWeekLabel Replaces the content of each weekday column header. The grid still places
 *   one per column, so a replacement stays aligned with the day cells. Defaults to
 *   [KalendarHeaderDefaults.DayOfWeekLabel] formatted by [config].
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
    onDayEventClick: ((event: KalendarEvent) -> Unit)? = null,
    onDateRangeSelect: ((start: LocalDate, current: LocalDate) -> Unit)? = null,
    onDateRangeSelectEnd: (() -> Unit)? = null,
    config: KalendarViewConfig = KalendarViewConfig(),
    header: @Composable (KalendarHeaderScope) -> Unit = { headerScope ->
        KalendarHeaderDefaults.Header(
            scope = headerScope,
            showNavigationArrows = config.showNavigationArrows,
            showTodayButton = config.showTodayButton,
            showJumpPicker = config.showJumpPicker,
            monthNameFormatter = config.monthNameFormatter,
            shortMonthNameFormatter = config.shortMonthNameFormatter,
        )
    },
    dayOfWeekLabel: @Composable (DayOfWeek) -> Unit = { dayOfWeek ->
        KalendarHeaderDefaults.DayOfWeekLabel(
            dayOfWeek = dayOfWeek,
            formatter = config.dayOfWeekLabelFormatter,
        )
    },
    dayContent: (@Composable (scope: KalendarDayScope) -> Unit)? = null,
) {
    val colors = kalendarColors()
    val today = rememberKalendarToday(state.timeSource)
    val eventsByDate = remember(events) { events.byDateExpandingSpans() }
    val slidingSelection = if (config.showSelectionIndicator) selectedDates.singleOrNull() else null
    // Hoisted out of the cell lambda: `?:` on a non-capturing literal yields the same singleton
    // every recomposition, so the cell's `onEventClick` parameter stays equal and skippable.
    val dayEventClick = onDayEventClick ?: {}

    val keyboard = rememberKalendarGridKeyboardState()
    val coroutineScope = rememberCoroutineScope()
    val visibleWeekStart = state.visibleDate.startOfWeek(state.startDayOfWeek)
    val periodDates = remember(visibleWeekStart, config.visibleDaysOfWeek) {
        weekDates(weekStart = visibleWeekStart, visibleDaysOfWeek = config.visibleDaysOfWeek)
    }

    Column(modifier = modifier.background(config.background ?: SolidColor(colors.background))) {
        val title = remember(state.visibleDate, config) {
            weekTitle(state.visibleDate, config.shortMonthNameFormatter)
        }
        header(rememberKalendarHeaderScope(state = state, title = title))
        Box(
            modifier = Modifier.kalendarGridKeyboard(
                keyboard = keyboard,
                periodDates = periodDates,
                today = today,
                selectedDate = selectedDates.singleOrNull(),
                onSelect = { date -> onDateClick(date, eventsByDate[date] ?: emptyList()) },
                onScrollTo = { date -> coroutineScope.launch { state.animateScrollTo(date) } },
                pageStep = { date, pages -> date.plus(pages * DAYS_PER_WEEK, DateTimeUnit.DAY) },
                isDateEnabled = { date -> !config.disabledDates(date) },
                onRangeDrag = onDateRangeSelect,
                onRangeDragEnd = onDateRangeSelectEnd,
                monthNameFormatter = config.monthNameFormatter,
                visibleDaysOfWeek = config.visibleDaysOfWeek,
            )
        ) {
            HorizontalPager(
                state = state.pagerState,
                modifier = Modifier.fillMaxWidth(),
            ) { page ->
                val weekStart = state.pageToDate(page)
                val dates = remember(weekStart, config.visibleDaysOfWeek) {
                    weekDates(weekStart = weekStart, visibleDaysOfWeek = config.visibleDaysOfWeek)
                }
                KalendarGrid(
                    dates = dates,
                    startDayOfWeek = state.startDayOfWeek,
                    selectedDate = slidingSelection,
                    keyboard = keyboard,
                    onDateRangeDrag = onDateRangeSelect,
                    onDateRangeDragEnd = onDateRangeSelectEnd,
                    visibleDaysOfWeek = config.visibleDaysOfWeek,
                    dayOfWeekLabel = dayOfWeekLabel,
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
                            maxEventIndicators = config.eventIndicatorCap,
                            onEventClick = dayEventClick,
                        )
                    }
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
internal fun weekTitle(weekStart: LocalDate, shortMonthNameFormatter: (Month) -> String): String {
    val weekEnd = weekStart.plus(6, DateTimeUnit.DAY)
    val monthName = shortMonthNameFormatter(weekStart.month)
    return when {
        weekStart.year != weekEnd.year -> {
            val startLabel = shortDateLabel(weekStart, shortMonthNameFormatter)
            val endLabel = shortDateLabel(weekEnd, shortMonthNameFormatter)
            "$startLabel, ${weekStart.year} – $endLabel, ${weekEnd.year}"
        }
        weekStart.month != weekEnd.month -> {
            val startLabel = shortDateLabel(weekStart, shortMonthNameFormatter)
            val endLabel = shortDateLabel(weekEnd, shortMonthNameFormatter)
            "$startLabel – $endLabel, ${weekEnd.year}"
        }
        else -> "$monthName ${weekStart.dayOfMonth}–${weekEnd.dayOfMonth}, ${weekEnd.year}"
    }
}

private fun shortDateLabel(date: LocalDate, shortMonthNameFormatter: (Month) -> String): String =
    "${shortMonthNameFormatter(date.month)} ${date.dayOfMonth}"
