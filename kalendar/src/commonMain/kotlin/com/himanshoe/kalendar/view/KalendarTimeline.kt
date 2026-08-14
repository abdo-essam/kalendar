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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import com.himanshoe.kalendar.foundation.event.KalendarEvent
import com.himanshoe.kalendar.foundation.event.KalendarEvents
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate

/**
 * A continuous, vertically-scrolling calendar spanning many months, forward and backward from
 * [selectedDate], with no page boundaries — scroll from August straight into September into
 * October. Only the months near the current scroll position are ever composed.
 *
 * Unlike [KalendarWeek]/[KalendarMonth]/[KalendarYear], there are no arrow buttons — the scroll
 * itself is the only navigation, so [KalendarViewConfig.showNavigationArrows] has no effect here.
 * There is no month/year jump picker either — the sticky header's title is not tappable — so
 * [KalendarViewConfig.showJumpPicker] is likewise ignored.
 * A sticky header showing the topmost visible month (and, when [KalendarViewConfig.showTodayButton]
 * is enabled, a button that scrolls back to today) stays pinned above the scrolling content. How
 * far the list scrolls is controlled by [rememberKalendarTimelineState]'s `minDate`/`maxDate` —
 * unlimited in either direction when left unset (the default).
 *
 * ```kotlin
 * val state = rememberKalendarTimelineState(initialDate = today)
 * var selected by remember { mutableStateOf(setOf(today)) }
 *
 * KalendarTimeline(
 *     selectedDate = today,
 *     state = state,
 *     selectedDates = selected,
 *     onDateClick = { date, _ -> selected = setOf(date) },
 * )
 * ```
 *
 * @param selectedDate The date used as the default single-selection highlight and, when [state]
 *   is left at its default, the month initially at the top of the list. Superseded by
 *   [selectedDates] when that is supplied explicitly.
 * @param modifier [Modifier] applied to the outermost container.
 * @param state Scroll state from [rememberKalendarTimelineState]. Own it yourself to read
 *   [KalendarTimelineState.visibleDate] (e.g. for a screen title) or call
 *   [KalendarTimelineState.scrollTo] from a button.
 * @param events Events shown as indicator dots on day cells.
 * @param selectedDates The set of highlighted dates. Defaults to a single-element set containing
 *   [selectedDate]; pass your own hoisted set for multi-select or range selection (see
 *   [datesUntil] for a range-selection helper). Hoist/`remember` the same instance across
 *   recompositions rather than building a fresh [Set] every time.
 * @param onDateClick Called when a non-disabled date is tapped, with the events on that date.
 * @param config Shared visual/behavioural settings — disabled dates, background, today button.
 *   Arrow visibility and the jump picker have no effect (see above).
 * @param header Replaces the sticky overlay pinned above the scrolling months. Receives a
 *   [KalendarHeaderScope] whose page vocabulary is mapped onto months, since a timeline has no
 *   pages: `canScrollBackward`/`canScrollForward` are always `true` (the scroll itself is clamped
 *   to `minDate`/`maxDate`), and `goToPreviousPage`/`goToNextPage` scroll one month back/forward
 *   from the topmost visible month. Defaults to [KalendarHeaderDefaults.TimelineHeader] driven by
 *   [config].
 * @param dayOfWeekLabel Replaces the content of each weekday column header, on every month grid.
 *   The grid still places one per column, so a replacement stays aligned with the day cells.
 *   Defaults to [KalendarHeaderDefaults.DayOfWeekLabel] formatted by [config].
 * @param dayContent Replaces the default day-cell rendering. Receives a [KalendarDayScope]
 *   describing the date being rendered.
 */
@Composable
public fun KalendarTimeline(
    selectedDate: LocalDate,
    modifier: Modifier = Modifier,
    state: KalendarTimelineState = rememberKalendarTimelineState(selectedDate),
    events: KalendarEvents = emptyList(),
    selectedDates: Set<LocalDate> = setOf(selectedDate),
    onDateClick: (date: LocalDate, events: List<KalendarEvent>) -> Unit = { _, _ -> },
    config: KalendarViewConfig = KalendarViewConfig(),
    header: @Composable (KalendarHeaderScope) -> Unit = { headerScope ->
        KalendarHeaderDefaults.TimelineHeader(
            scope = headerScope,
            showTodayButton = config.showTodayButton,
            background = config.background,
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
    val typography = kalendarTypography()
    val dimensions = kalendarDimensions()
    val today = remember { todayInSystemTimeZone() }
    val eventsByDate = remember(events) { events.byDateExpandingSpans() }
    val background = config.background ?: SolidColor(colors.background)

    Box(modifier = modifier) {
        LazyColumn(
            state = state.lazyListState,
            modifier = Modifier.fillMaxWidth().background(background),
        ) {
            items(count = state.itemCount, key = { index -> index }) { index ->
                val monthStart = state.indexToDate(index)
                val dates = remember(monthStart, state.startDayOfWeek) {
                    monthGridDates(monthStart = monthStart, startDayOfWeek = state.startDayOfWeek)
                }
                Column {
                    BasicText(
                        text = monthTitle(monthStart, config.monthNameFormatter),
                        style = typography.headerTitle.copy(color = colors.headerContent),
                        modifier = Modifier.padding(dimensions.sectionPadding),
                    )
                    KalendarStaticGrid(
                        dates = dates,
                        startDayOfWeek = state.startDayOfWeek,
                        dayOfWeekLabel = dayOfWeekLabel,
                    ) { date ->
                        val dateEvents = eventsByDate[date] ?: emptyList()
                        val isCurrentMonth = date.month == monthStart.month
                        val cellScope = KalendarDayScope(
                            date = date,
                            isSelected = date in selectedDates,
                            isToday = date == today,
                            isDisabled = config.disabledDates(date) || !isCurrentMonth,
                            events = dateEvents,
                        )
                        KalendarMonthGridCell(
                            scope = cellScope,
                            isCurrentMonth = isCurrentMonth,
                            showAdjacentMonthDates = config.showAdjacentMonthDates,
                            onClick = { onDateClick(date, dateEvents) },
                            dayContent = dayContent,
                            monthNameFormatter = config.monthNameFormatter,
                            maxEventIndicators = config.eventIndicatorCap,
                        )
                    }
                }
            }
        }

        Column(modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth()) {
            val stickyTitle = remember(state.visibleDate) {
                monthTitle(state.visibleDate, config.monthNameFormatter)
            }
            header(rememberKalendarTimelineHeaderScope(state = state, title = stickyTitle))
        }
    }
}
