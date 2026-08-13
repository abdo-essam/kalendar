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

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.himanshoe.kalendar.foundation.event.KalendarEvent
import com.himanshoe.kalendar.foundation.event.KalendarEvents
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

private const val STICKY_TITLE_CROSSFADE_MS = 150

/**
 * A continuous, vertically-scrolling calendar spanning many months, forward and backward from
 * [selectedDate], with no page boundaries — scroll from August straight into September into
 * October. Only the months near the current scroll position are ever composed.
 *
 * Unlike [KalendarWeek]/[KalendarMonth]/[KalendarYear], there are no arrow buttons — the scroll
 * itself is the only navigation, so [KalendarViewConfig.showNavigationArrows] has no effect here.
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
 *   Arrow visibility has no effect (see above).
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
    dayContent: (@Composable (scope: KalendarDayScope) -> Unit)? = null,
) {
    val today = remember { todayInSystemTimeZone() }
    val eventsByDate = remember(events) { events.byDateExpandingSpans() }
    val scope = rememberCoroutineScope()

    Box(modifier = modifier) {
        LazyColumn(
            state = state.lazyListState,
            modifier = Modifier.fillMaxWidth().background(config.background),
        ) {
            items(count = state.itemCount, key = { index -> index }) { index ->
                val monthStart = state.indexToDate(index)
                val dates = remember(monthStart, state.startDayOfWeek) {
                    monthGridDates(monthStart = monthStart, startDayOfWeek = state.startDayOfWeek)
                }
                Column {
                    Text(
                        text = monthTitle(monthStart, config.monthNameFormatter),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                    KalendarStaticGrid(
                        dates = dates,
                        startDayOfWeek = state.startDayOfWeek,
                        dayLabelFormatter = config.dayOfWeekLabelFormatter,
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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(config.background)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                val stickyTitle = remember(state.visibleDate) {
                    monthTitle(state.visibleDate, config.monthNameFormatter)
                }
                AnimatedContent(
                    targetState = stickyTitle,
                    transitionSpec = {
                        fadeIn(tween(STICKY_TITLE_CROSSFADE_MS)) togetherWith fadeOut(tween(STICKY_TITLE_CROSSFADE_MS))
                    },
                ) { animatedTitle ->
                    Text(text = animatedTitle, style = MaterialTheme.typography.titleMedium)
                }
                if (config.showTodayButton) {
                    IconButton(onClick = { scope.launch { state.scrollToToday() } }) {
                        Icon(imageVector = Icons.Filled.Today, contentDescription = "Today")
                    }
                }
            }
            HorizontalDivider()
        }
    }
}
