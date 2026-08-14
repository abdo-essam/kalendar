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

package com.himanshoe.kalendar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.himanshoe.kalendar.component.KalendarHeaderDefaults
import com.himanshoe.kalendar.component.KalendarMonthGridCell
import com.himanshoe.kalendar.component.KalendarStaticGrid
import com.himanshoe.kalendar.component.rememberKalendarHeaderScope
import com.himanshoe.kalendar.foundation.datetime.datesUntil
import com.himanshoe.kalendar.foundation.event.KalendarEvent
import com.himanshoe.kalendar.foundation.event.KalendarEvents
import com.himanshoe.kalendar.foundation.event.byDateExpandingSpans
import com.himanshoe.kalendar.foundation.grid.monthGridDates
import com.himanshoe.kalendar.slot.KalendarDayScope
import com.himanshoe.kalendar.slot.KalendarHeaderScope
import com.himanshoe.kalendar.state.KalendarViewState
import com.himanshoe.kalendar.state.rememberKalendarToday
import com.himanshoe.kalendar.state.rememberKalendarYearState
import com.himanshoe.kalendar.theme.kalendarColors
import com.himanshoe.kalendar.theme.kalendarDimensions
import com.himanshoe.kalendar.theme.kalendarTypography
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month

/** Inset either side of a month grid on the year page, so twelve grids don't run edge to edge. */
private val YEAR_MONTH_GRID_PADDING = 8.dp

/**
 * Test tag on the year page's scrolling list of months.
 *
 * The list is lazy, so a month below the viewport has no node for a test to find, let alone scroll
 * to; a test reaches one by scrolling *this* node until the month it wants composes.
 */
internal const val YEAR_MONTH_LIST_TAG = "KalendarYearMonthList"

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
 * @param config Shared visual/behavioural settings — disabled dates, arrow visibility, background,
 *   and [KalendarViewConfig.visibleDaysOfWeek], applied to all twelve month grids alike.
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

    Column(
        modifier = modifier.background(config.background ?: SolidColor(colors.background)),
        verticalArrangement = Arrangement.spacedBy(dimensions.headerContentSpacing),
    ) {
        val title = remember(state.visibleDate) { state.visibleDate.year.toString() }
        header(rememberKalendarHeaderScope(state = state, title = title))
        HorizontalPager(
            state = state.pagerState,
            modifier = Modifier.fillMaxWidth(),
        ) { page ->
            val yearStart = state.pager.dateAt(page)
            // Lazy, one item per month: a year is twelve month grids of up to 42 cells, and a
            // non-lazy scrolling Column composed all ~404 of them before the first frame even
            // though one or two months are ever on screen. The *inner* grid stays eager
            // ([KalendarStaticGrid]) on purpose — a LazyVerticalGrid nested in a scrolling parent
            // is measured with an unbounded height and crashes.
            LazyColumn(
                modifier = Modifier.fillMaxWidth().testTag(YEAR_MONTH_LIST_TAG),
                verticalArrangement = Arrangement.spacedBy(dimensions.monthSpacing),
            ) {
                items(items = Month.entries, key = { month -> month.ordinal }) { month ->
                    val monthStart = LocalDate(yearStart.year, month, 1)
                    val dates = remember(monthStart, state.startDayOfWeek, config.visibleDaysOfWeek) {
                        monthGridDates(
                            monthStart = monthStart,
                            startDayOfWeek = state.startDayOfWeek,
                            visibleDaysOfWeek = config.visibleDaysOfWeek,
                        )
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
                            modifier = Modifier.padding(horizontal = YEAR_MONTH_GRID_PADDING),
                            visibleDaysOfWeek = config.visibleDaysOfWeek,
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
