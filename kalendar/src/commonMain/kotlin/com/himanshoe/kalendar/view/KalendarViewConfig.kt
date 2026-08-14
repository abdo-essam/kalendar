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
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month

/**
 * Shared visual and behavioural knobs for every standalone view — [KalendarWeek],
 * [KalendarMonth], [KalendarYear], [KalendarTimeline], [KalendarSchedule], and
 * [KalendarScheduleWeek]. Options that only apply to some views say so.
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
 * @param showSelectionIndicator Whether a single shared indicator slides between selected day
 *   cells instead of each cell drawing its own selection fill. Applies to [KalendarWeek] and
 *   [KalendarMonth], and only while exactly one date is selected — there is nothing to slide
 *   between for multi-select or range selection. Works with a custom `dayContent` too: the
 *   indicator is drawn behind the cell, so your own cell keeps the animation. Defaults to `true`.
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
 * @param background Background brush for the calendar container. Defaults to `null`, meaning
 *   [KalendarColors.background] — which follows light/dark mode. Set it only when you want
 *   something a plain colour can't express, such as a gradient.
 */
@Immutable
public class KalendarViewConfig(
    public val disabledDates: (LocalDate) -> Boolean = { false },
    public val showNavigationArrows: Boolean = true,
    public val showAdjacentMonthDates: Boolean = true,
    public val showTodayButton: Boolean = true,
    public val showJumpPicker: Boolean = true,
    public val showSelectionIndicator: Boolean = true,
    public val monthNameFormatter: (Month) -> String = defaultMonthNameFormatter,
    public val dayOfWeekLabelFormatter: (DayOfWeek) -> String = defaultDayOfWeekLabelFormatter,
    public val hourLabelFormatter: (Int) -> String = defaultHourLabelFormatter,
    public val scheduleInitialScrollHour: Int = DEFAULT_SCHEDULE_SCROLL_HOUR,
    public val scheduleDragSnapMinutes: Int = DEFAULT_SCHEDULE_SNAP_MINUTES,
    public val eventIndicatorCap: Int = DEFAULT_EVENT_INDICATOR_CAP,
    public val background: Brush? = null,
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

    /**
     * Returns a duplicate of this config with only the options passed here replaced. The same
     * validation the constructor performs applies to the result.
     */
    public fun copy(
        disabledDates: (LocalDate) -> Boolean = this.disabledDates,
        showNavigationArrows: Boolean = this.showNavigationArrows,
        showAdjacentMonthDates: Boolean = this.showAdjacentMonthDates,
        showTodayButton: Boolean = this.showTodayButton,
        showJumpPicker: Boolean = this.showJumpPicker,
        showSelectionIndicator: Boolean = this.showSelectionIndicator,
        monthNameFormatter: (Month) -> String = this.monthNameFormatter,
        dayOfWeekLabelFormatter: (DayOfWeek) -> String = this.dayOfWeekLabelFormatter,
        hourLabelFormatter: (Int) -> String = this.hourLabelFormatter,
        scheduleInitialScrollHour: Int = this.scheduleInitialScrollHour,
        scheduleDragSnapMinutes: Int = this.scheduleDragSnapMinutes,
        eventIndicatorCap: Int = this.eventIndicatorCap,
        background: Brush? = this.background,
    ): KalendarViewConfig = KalendarViewConfig(
        disabledDates = disabledDates,
        showNavigationArrows = showNavigationArrows,
        showAdjacentMonthDates = showAdjacentMonthDates,
        showTodayButton = showTodayButton,
        showJumpPicker = showJumpPicker,
        showSelectionIndicator = showSelectionIndicator,
        monthNameFormatter = monthNameFormatter,
        dayOfWeekLabelFormatter = dayOfWeekLabelFormatter,
        hourLabelFormatter = hourLabelFormatter,
        scheduleInitialScrollHour = scheduleInitialScrollHour,
        scheduleDragSnapMinutes = scheduleDragSnapMinutes,
        eventIndicatorCap = eventIndicatorCap,
        background = background,
    )

    @Suppress("CyclomaticComplexMethod")
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is KalendarViewConfig) return false
        if (disabledDates != other.disabledDates) return false
        if (showNavigationArrows != other.showNavigationArrows) return false
        if (showAdjacentMonthDates != other.showAdjacentMonthDates) return false
        if (showTodayButton != other.showTodayButton) return false
        if (showJumpPicker != other.showJumpPicker) return false
        if (showSelectionIndicator != other.showSelectionIndicator) return false
        if (monthNameFormatter != other.monthNameFormatter) return false
        if (dayOfWeekLabelFormatter != other.dayOfWeekLabelFormatter) return false
        if (hourLabelFormatter != other.hourLabelFormatter) return false
        if (scheduleInitialScrollHour != other.scheduleInitialScrollHour) return false
        if (scheduleDragSnapMinutes != other.scheduleDragSnapMinutes) return false
        if (eventIndicatorCap != other.eventIndicatorCap) return false
        if (background != other.background) return false
        return true
    }

    override fun hashCode(): Int {
        var result = disabledDates.hashCode()
        result = 31 * result + showNavigationArrows.hashCode()
        result = 31 * result + showAdjacentMonthDates.hashCode()
        result = 31 * result + showTodayButton.hashCode()
        result = 31 * result + showJumpPicker.hashCode()
        result = 31 * result + showSelectionIndicator.hashCode()
        result = 31 * result + monthNameFormatter.hashCode()
        result = 31 * result + dayOfWeekLabelFormatter.hashCode()
        result = 31 * result + hourLabelFormatter.hashCode()
        result = 31 * result + scheduleInitialScrollHour.hashCode()
        result = 31 * result + scheduleDragSnapMinutes.hashCode()
        result = 31 * result + eventIndicatorCap.hashCode()
        result = 31 * result + background.hashCode()
        return result
    }

    override fun toString(): String = "KalendarViewConfig(" +
        "disabledDates=$disabledDates, " +
        "showNavigationArrows=$showNavigationArrows, " +
        "showAdjacentMonthDates=$showAdjacentMonthDates, " +
        "showTodayButton=$showTodayButton, " +
        "showJumpPicker=$showJumpPicker, " +
        "showSelectionIndicator=$showSelectionIndicator, " +
        "monthNameFormatter=$monthNameFormatter, " +
        "dayOfWeekLabelFormatter=$dayOfWeekLabelFormatter, " +
        "hourLabelFormatter=$hourLabelFormatter, " +
        "scheduleInitialScrollHour=$scheduleInitialScrollHour, " +
        "scheduleDragSnapMinutes=$scheduleDragSnapMinutes, " +
        "eventIndicatorCap=$eventIndicatorCap, " +
        "background=${background ?: "null"}" +
        ")"
}

internal const val DEFAULT_SCHEDULE_SCROLL_HOUR = 7
internal const val DEFAULT_SCHEDULE_SNAP_MINUTES = 15
internal const val DEFAULT_EVENT_INDICATOR_CAP = 3
