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
 * @param monthNameFormatter Formats a month name for titles, headers, and accessibility
 *   descriptions. kotlinx-datetime has no locale-aware formatting, so this defaults to plain
 *   English (e.g. "August") — pass a formatter backed by a platform date API (`java.time`,
 *   `NSDateFormatter`, `Intl`, ...) for a localized name.
 * @param dayOfWeekLabelFormatter Formats the day-of-week column headers. Defaults to the day
 *   name's first letter (e.g. "M" for Monday).
 * @param background Background brush for the calendar container. Defaults to solid white.
 */
@Immutable
public data class KalendarViewConfig(
    val disabledDates: (LocalDate) -> Boolean = { false },
    val showNavigationArrows: Boolean = true,
    val showAdjacentMonthDates: Boolean = true,
    val showTodayButton: Boolean = true,
    val monthNameFormatter: (Month) -> String = defaultMonthNameFormatter,
    val dayOfWeekLabelFormatter: (DayOfWeek) -> String = defaultDayOfWeekLabelFormatter,
    val background: Brush = SolidColor(Color.White),
)
