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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import com.himanshoe.kalendar.foundation.event.KalendarEvent
import com.himanshoe.kalendar.foundation.event.KalendarEvents
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month

/**
 * A year-overview calendar (all 12 months) navigable by both arrow-click and swipe.
 *
 * A single [KalendarViewState] backs both navigation paths — the arrow buttons in the header and
 * a horizontal swipe move the same underlying pager, so they always stay in sync. Each page shows
 * one year as a vertically scrollable column of month grids.
 *
 * ```kotlin
 * val state = rememberKalendarYearState(initialDate = today)
 * var selected by remember { mutableStateOf(setOf(today)) }
 *
 * KalendarYear(
 *     selectedDate = today,
 *     state = state,
 *     selectedDates = selected,
 *     onDateClick = { date, _ -> selected = setOf(date) },
 * )
 * ```
 *
 * @param selectedDate The date used as the default single-selection highlight and, when [state]
 *   is left at its default, the year initially visible. Superseded by [selectedDates] when that
 *   is supplied explicitly.
 * @param modifier [Modifier] applied to the outermost container.
 * @param state Navigation state from [rememberKalendarYearState]. Own it yourself to read
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
 * @param config Shared visual/behavioural settings — disabled dates, arrow visibility, background.
 * @param header Replaces the navigation header above the year's months. Receives a
 *   [KalendarHeaderScope] carrying the formatted title and the navigation callbacks, so your own
 *   chrome keeps working arrows and today button. Defaults to [KalendarHeaderDefaults.Header]
 *   driven by [config].
 * @param dayOfWeekLabel Replaces the content of each weekday column header, on every month grid.
 *   The grid still places one per column, so a replacement stays aligned with the day cells.
 *   Defaults to [KalendarHeaderDefaults.DayOfWeekLabel] formatted by [config].
 * @param dayContent Replaces the default day-cell rendering. Receives a [KalendarDayScope]
 *   describing the date being rendered.
 */
@Composable
public fun KalendarYear(
    selectedDate: LocalDate,
    modifier: Modifier = Modifier,
    state: KalendarViewState = rememberKalendarYearState(selectedDate),
    events: KalendarEvents = emptyList(),
    selectedDates: Set<LocalDate> = setOf(selectedDate),
    onDateClick: (date: LocalDate, events: List<KalendarEvent>) -> Unit = { _, _ -> },
    onDayEventClick: ((event: KalendarEvent) -> Unit)? = null,
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
    val dimensions = kalendarDimensions()
    val today = rememberKalendarToday(state.timeSource)
    val eventsByDate = remember(events) { events.byDateExpandingSpans() }
    // Hoisted out of the cell lambda: `?:` on a non-capturing literal yields the same singleton
    // every recomposition, so the cell's `onEventClick` parameter stays equal and skippable.
    val dayEventClick = onDayEventClick ?: {}

    Column(modifier = modifier.background(config.background ?: SolidColor(colors.background))) {
        val title = remember(state.visibleDate) { state.visibleDate.year.toString() }
        header(rememberKalendarHeaderScope(state = state, title = title))
        HorizontalPager(
            state = state.pagerState,
            modifier = Modifier.fillMaxWidth(),
        ) { page ->
            val yearStart = state.pageToDate(page)
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(dimensions.monthSpacing),
            ) {
                Month.entries.forEach { month ->
                    val monthStart = LocalDate(yearStart.year, month, 1)
                    val dates = remember(monthStart, state.startDayOfWeek) {
                        monthGridDates(monthStart = monthStart, startDayOfWeek = state.startDayOfWeek)
                    }
                    Column {
                        BasicText(
                            text = config.monthNameFormatter(monthStart.month),
                            style = kalendarTypography().monthLabel.copy(color = colors.headerContent),
                            modifier = Modifier.padding(dimensions.sectionPadding),
                        )
                        KalendarStaticGrid(
                            dates = dates,
                            startDayOfWeek = state.startDayOfWeek,
                            modifier = Modifier.padding(horizontal = 8.dp),
                            dayOfWeekLabel = dayOfWeekLabel,
                        ) { date ->
                            val dateEvents = eventsByDate[date] ?: emptyList()
                            val isCurrentMonth = date.month == month
                            val scope = KalendarDayScope(
                                date = date,
                                isSelected = date in selectedDates,
                                isToday = date == today,
                                isDisabled = config.disabledDates(date) || !isCurrentMonth,
                                events = dateEvents,
                            )
                            KalendarMonthGridCell(
                                scope = scope,
                                isCurrentMonth = isCurrentMonth,
                                showAdjacentMonthDates = config.showAdjacentMonthDates,
                                onClick = { onDateClick(date, dateEvents) },
                                dayContent = dayContent,
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
}
