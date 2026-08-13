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

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month

/**
 * Shared visual and behavioural knobs for [KalendarWeek], [KalendarMonth], and [KalendarYear].
 *
 * @param disabledDates A predicate called for each date. When it returns `true` the date is
 *   rendered at reduced opacity and taps are ignored. Defaults to `{ false }`.
 * @param showNavigationArrows Whether to show previous/next arrow buttons in the header. Swiping
 *   the calendar works regardless of this setting. Defaults to `true`.
 * @param showAdjacentMonthDates Whether the leading/trailing dates from the previous/next month
 *   (the padding needed to complete a month grid's first/last week) are rendered at reduced
 *   opacity, or left blank. Has no effect on [KalendarWeek], which has no such padding. Defaults
 *   to `true`.
 * @param showTodayButton Whether to show a button in the header that animates back to today's
 *   date. Defaults to `true`.
 * @param showJumpPicker Whether tapping the header title opens a month/year jump picker that
 *   scrolls straight to the chosen month. Defaults to `true`.
 * @param monthNameFormatter Formats a month name for titles, headers, and accessibility
 *   descriptions. kotlinx-datetime has no locale-aware formatting, so this defaults to plain
 *   English (e.g. "August") — pass a formatter backed by a platform date API (`java.time`,
 *   `NSDateFormatter`, `Intl`, ...) for a localized name.
 * @param dayOfWeekLabelFormatter Formats the day-of-week column headers. Defaults to the day
 *   name's first letter (e.g. "M" for Monday).
 * @param hourLabelFormatter Formats the hour-gutter labels on [KalendarSchedule] and
 *   [KalendarScheduleWeek]. Defaults to 24-hour `"HH:00"`; supply your own for 12-hour AM/PM.
 * @param scheduleInitialScrollHour The hour the Schedule views scroll to when a page first
 *   appears (0–23). Defaults to 7.
 * @param scheduleDragSnapMinutes Snap step, in minutes, for dragging/resizing event blocks on
 *   the Schedule views. Must be at least 1. Defaults to 15.
 * @param eventIndicatorCap Maximum per-event indicator dots below a day number before collapsing
 *   into a `+N` overflow label. Must be at least 1. Defaults to 3.
 * @param background Background brush for the calendar container. Defaults to solid white.
 */
@Immutable
public data class KalendarViewConfig(
    val disabledDates: (LocalDate) -> Boolean = { false },
    val showNavigationArrows: Boolean = true,
    val showAdjacentMonthDates: Boolean = true,
    val showTodayButton: Boolean = true,
    val showJumpPicker: Boolean = true,
    val monthNameFormatter: (Month) -> String = defaultMonthNameFormatter,
    val dayOfWeekLabelFormatter: (DayOfWeek) -> String = defaultDayOfWeekLabelFormatter,
    val hourLabelFormatter: (Int) -> String = defaultHourLabelFormatter,
    val scheduleInitialScrollHour: Int = DEFAULT_SCHEDULE_SCROLL_HOUR,
    val scheduleDragSnapMinutes: Int = DEFAULT_SCHEDULE_SNAP_MINUTES,
    val eventIndicatorCap: Int = DEFAULT_EVENT_INDICATOR_CAP,
    val background: Brush = SolidColor(Color.White),
) {
    init {
        require(scheduleInitialScrollHour in 0 until HOURS_PER_DAY) {
            "scheduleInitialScrollHour must be in 0..23, was $scheduleInitialScrollHour"
        }
        require(scheduleDragSnapMinutes >= 1) {
            "scheduleDragSnapMinutes must be >= 1, was $scheduleDragSnapMinutes"
        }
        require(eventIndicatorCap >= 1) {
            "eventIndicatorCap must be >= 1, was $eventIndicatorCap"
        }
    }
}

internal const val DEFAULT_SCHEDULE_SCROLL_HOUR = 7
internal const val DEFAULT_SCHEDULE_SNAP_MINUTES = 15
internal const val DEFAULT_EVENT_INDICATOR_CAP = 3
