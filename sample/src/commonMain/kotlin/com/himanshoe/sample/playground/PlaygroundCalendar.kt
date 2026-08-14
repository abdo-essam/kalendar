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

package com.himanshoe.sample.playground

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.himanshoe.kalendar.foundation.event.KalendarEvent
import com.himanshoe.kalendar.view.KalendarAgenda
import com.himanshoe.kalendar.view.KalendarDayScope
import com.himanshoe.kalendar.view.KalendarHeaderDefaults
import com.himanshoe.kalendar.view.KalendarHeaderScope
import com.himanshoe.kalendar.view.KalendarMonth
import com.himanshoe.kalendar.view.KalendarSchedule
import com.himanshoe.kalendar.view.KalendarScheduleWeek
import com.himanshoe.kalendar.view.KalendarSelectionState
import com.himanshoe.kalendar.view.KalendarTheme
import com.himanshoe.kalendar.view.KalendarTimeline
import com.himanshoe.kalendar.view.KalendarWeek
import com.himanshoe.kalendar.view.KalendarYear
import com.himanshoe.kalendar.view.rememberKalendarMonthState
import com.himanshoe.kalendar.view.rememberKalendarTimelineState
import com.himanshoe.kalendar.view.rememberKalendarWeekState
import com.himanshoe.kalendar.view.rememberKalendarYearState
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.minus
import kotlinx.datetime.plus

private const val TimelineBoundYears = 1

/**
 * The live calendar: the view [config] selects, wrapped in the [KalendarTheme] [config] resolves to.
 *
 * Everything the playground's controls touch flows through here, which is also what makes the
 * playground a standing test that the tokens really do control every view.
 */
@Composable
internal fun PlaygroundCalendar(
    config: PlaygroundConfig,
    today: LocalDate,
    events: List<KalendarEvent>,
    selection: KalendarSelectionState,
    onEventTimeChange: (event: KalendarEvent, newStart: LocalDateTime, newEnd: LocalDateTime) -> Unit,
    onEventDrop: (events: List<KalendarEvent>, newDate: LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    KalendarTheme(
        colors = config.resolveColors(dark = config.isDark()),
        typography = config.resolveTypography(),
        shapes = config.resolveShapes(),
        dimensions = config.resolveDimensions(),
        animations = config.resolveAnimations(),
    ) {
        val viewConfig = remember(config) { config.resolveViewConfig() }
        val rangeDrag: ((LocalDate, LocalDate) -> Unit)? = if (config.selection == PlaygroundSelection.Range) {
            { start, current -> selection.onRangeDrag(start, current) }
        } else {
            null
        }
        val rangeDragEnd: (() -> Unit)? = if (config.selection == PlaygroundSelection.Range) {
            { selection.onRangeDragEnd() }
        } else {
            null
        }
        val dayContent: (@Composable (KalendarDayScope) -> Unit)? = if (config.customCell) {
            { scope -> BrandDayCell(scope = scope, onClick = { selection.onDateClick(scope.date) }) }
        } else {
            null
        }
        val header: @Composable (KalendarHeaderScope) -> Unit = { scope ->
            if (config.customHeader) {
                BrandHeader(scope = scope)
            } else {
                KalendarHeaderDefaults.Header(
                    scope = scope,
                    showNavigationArrows = viewConfig.showNavigationArrows,
                    showTodayButton = viewConfig.showTodayButton,
                    showJumpPicker = viewConfig.showJumpPicker,
                    monthNameFormatter = viewConfig.monthNameFormatter,
                )
            }
        }
        val timelineHeader: @Composable (KalendarHeaderScope) -> Unit = { scope ->
            if (config.customHeader) {
                BrandHeader(scope = scope)
            } else {
                KalendarHeaderDefaults.TimelineHeader(
                    scope = scope,
                    showTodayButton = viewConfig.showTodayButton,
                    background = viewConfig.background,
                )
            }
        }
        when (config.view) {
            PlaygroundView.Week -> KalendarWeek(
                selectedDate = today,
                modifier = modifier,
                state = rememberKalendarWeekState(initialDate = today),
                events = events,
                selectedDates = selection.selectedDates,
                onDateClick = { date, _ -> selection.onDateClick(date) },
                onDateRangeSelect = rangeDrag,
                onDateRangeSelectEnd = rangeDragEnd,
                config = viewConfig,
                header = header,
                dayContent = dayContent,
            )

            PlaygroundView.Month -> KalendarMonth(
                selectedDate = today,
                modifier = modifier,
                state = rememberKalendarMonthState(initialDate = today),
                events = events,
                selectedDates = selection.selectedDates,
                onDateClick = { date, _ -> selection.onDateClick(date) },
                onDateRangeSelect = rangeDrag,
                onDateRangeSelectEnd = rangeDragEnd,
                onEventDrop = onEventDrop,
                config = viewConfig,
                header = header,
                dayContent = dayContent,
            )

            PlaygroundView.Year -> KalendarYear(
                selectedDate = today,
                modifier = modifier,
                state = rememberKalendarYearState(initialDate = today),
                events = events,
                selectedDates = selection.selectedDates,
                onDateClick = { date, _ -> selection.onDateClick(date) },
                config = viewConfig,
                header = header,
                dayContent = dayContent,
            )

            PlaygroundView.Timeline -> KalendarTimeline(
                selectedDate = today,
                modifier = modifier,
                state = rememberKalendarTimelineState(
                    initialDate = today,
                    minDate = today.minus(value = TimelineBoundYears, unit = DateTimeUnit.YEAR),
                    maxDate = today.plus(value = TimelineBoundYears, unit = DateTimeUnit.YEAR),
                ),
                events = events,
                selectedDates = selection.selectedDates,
                onDateClick = { date, _ -> selection.onDateClick(date) },
                config = viewConfig,
                header = timelineHeader,
                dayContent = dayContent,
            )

            PlaygroundView.Schedule -> KalendarSchedule(
                selectedDate = today,
                modifier = modifier,
                events = events,
                onEventClick = {},
                onEventTimeChange = onEventTimeChange,
                config = viewConfig,
            )

            PlaygroundView.ScheduleWeek -> KalendarScheduleWeek(
                selectedDate = today,
                modifier = modifier,
                events = events,
                onEventClick = {},
                onEventTimeChange = onEventTimeChange,
                config = viewConfig,
            )

            PlaygroundView.Agenda -> KalendarAgenda(
                events = events,
                modifier = modifier.fillMaxSize(),
                onEventClick = {},
                config = viewConfig,
            )
        }
    }
}
