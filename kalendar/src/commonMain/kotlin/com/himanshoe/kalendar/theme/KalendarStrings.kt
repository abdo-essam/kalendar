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

package com.himanshoe.kalendar.theme

import androidx.compose.runtime.Immutable
import com.himanshoe.kalendar.foundation.event.KalendarEvent
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime

/**
 * Every string a Kalendar view speaks or shows, so the library can be localized.
 *
 * Kalendar has no resource system of its own — it is a Compose Multiplatform library and cannot
 * reach your app's strings — so localization works by handing it your already-resolved strings:
 *
 * ```kotlin
 * KalendarTheme(
 *     strings = KalendarStrings(
 *         previousPage = stringResource(R.string.previous),
 *         nextPage = stringResource(R.string.next),
 *         today = stringResource(R.string.today),
 *         todaySuffix = stringResource(R.string.a11y_today_suffix),
 *     ),
 * ) {
 *     KalendarMonth(selectedDate = today)
 * }
 * ```
 *
 * Everything here is either an accessibility announcement or a short label; the *date* formatting
 * hooks (month names, day-of-week labels, hour labels) live on [KalendarViewConfig] instead,
 * because they are per-view formatting rather than fixed text.
 *
 * @property previousPage Accessibility label for the header's previous-page arrow.
 * @property nextPage Accessibility label for the header's next-page arrow.
 * @property today Accessibility label for the today button.
 * @property previousYear Accessibility label for the jump picker's previous-year button.
 * @property nextYear Accessibility label for the jump picker's next-year button.
 * @property openJumpPicker Accessibility label for the header title when tapping it opens the
 *   month/year jump picker.
 * @property todaySuffix Appended to a day cell's accessibility description when that date is
 *   today — e.g. "August 12, 2026, today". Include the leading separator.
 * @property hasEventsSuffix Appended to a day cell's accessibility description when that date has
 *   at least one event. Include the leading separator. See [dayAccessibilityLabel] to replace the
 *   whole description instead.
 * @property agendaEmptyState Shown in the middle of [KalendarAgenda] when it has no events at all.
 * @property resourceEmptyState Shown in the middle of [KalendarResourceView] when it was given no
 *   resources. Deliberately not [agendaEmptyState]: that grid is blank because it has no *lanes*,
 *   which is a different sentence from "no events" in every language.
 * @property showAllDayEvents Accessibility label for the `+N` overflow trigger on a day cell — the
 *   button that opens the popover listing every event on that day.
 * @property eventOverflowLabel Formats the compact label shown when a day has more events than
 *   [KalendarViewConfig.eventIndicatorCap] dots, given the number of events not shown.
 * @property weekNumberAccessibilityLabel What a screen reader announces for a week-number gutter
 *   cell, given the ISO-8601 week number it shows. The gutter *shows* the bare number, which read
 *   aloud is just an unexplained count, so this supplies the sentence behind it. Defaults to plain
 *   English ("Week 36").
 * @property dayAccessibilityLabel Builds a day cell's base accessibility description from its date
 *   and its already-formatted month name. Defaults to US-style `"August 12, 2026"`; override it
 *   for locales that order or punctuate dates differently. [todaySuffix] and [hasEventsSuffix] are
 *   appended to whatever this returns.
 * @property dayOfWeekAccessibilityLabel The full day name a screen reader announces for a
 *   day-of-week column header. The header *shows*
 *   [KalendarViewConfig.dayOfWeekLabelFormatter]'s short form — a bare "M" or "T", which reads as
 *   an initial rather than a weekday — so this supplies the name behind it. Defaults to plain
 *   English ("Monday").
 * @property eventAccessibilityLabel What a screen reader announces for a timed event block, given
 *   the event and its start/end on the day being rendered. Defaults to the event's name followed by
 *   its 24-hour time range; override it for a localized time format. Only the built-in
 *   [KalendarScheduleDefaults.EventBlock] applies it — a replacement `eventContent` slot draws its
 *   own semantics.
 * @property resourceEventAccessibilityLabel What a screen reader announces for an event block in
 *   [KalendarResourceView], given the event, its start/end on the rendered day, and the title of the
 *   lane it sits in. The lane has to be spoken: an event's position in a two-dimensional grid
 *   conveys nothing to a screen reader, so "Standup, 09:00 to 10:00" without "Room A" loses half of
 *   what the view exists to show. Defaults to [eventAccessibilityLabel]'s default followed by the
 *   lane title; note it is a separate hook, so overriding [eventAccessibilityLabel] alone does not
 *   change what the lane grid announces — override both.
 * @property allDayEventAccessibilityLabel What a screen reader announces for an all-day chip.
 *   Defaults to the event's name followed by "all day". See [eventAccessibilityLabel] for how it
 *   interacts with a custom `allDayEventContent` slot.
 */
@Immutable
public class KalendarStrings(
    public val previousPage: String = "Previous",
    public val nextPage: String = "Next",
    public val today: String = "Today",
    public val previousYear: String = "Previous year",
    public val nextYear: String = "Next year",
    public val openJumpPicker: String = "Choose month and year",
    public val todaySuffix: String = ", today",
    public val hasEventsSuffix: String = ", has events",
    public val agendaEmptyState: String = "No events",
    public val resourceEmptyState: String = "No resources",
    public val showAllDayEvents: String = "Show all events",
    public val eventOverflowLabel: (hiddenEventCount: Int) -> String = { "+$it" },
    public val weekNumberAccessibilityLabel: (weekNumber: Int) -> String = { "Week $it" },
    public val dayAccessibilityLabel: (date: LocalDate, monthName: String) -> String = defaultDayAccessibilityLabel,
    public val dayOfWeekAccessibilityLabel: (DayOfWeek) -> String = defaultDayOfWeekAccessibilityLabel,
    public val eventAccessibilityLabel: (event: KalendarEvent, start: LocalDateTime, end: LocalDateTime) -> String =
        defaultEventAccessibilityLabel,
    public val resourceEventAccessibilityLabel: (
        event: KalendarEvent,
        start: LocalDateTime,
        end: LocalDateTime,
        resourceTitle: String,
    ) -> String = defaultResourceEventAccessibilityLabel,
    public val allDayEventAccessibilityLabel: (event: KalendarEvent) -> String = defaultAllDayEventAccessibilityLabel,
) {

    /** Returns a duplicate of these strings with only the ones passed here replaced. */
    public fun copy(
        previousPage: String = this.previousPage,
        nextPage: String = this.nextPage,
        today: String = this.today,
        previousYear: String = this.previousYear,
        nextYear: String = this.nextYear,
        openJumpPicker: String = this.openJumpPicker,
        todaySuffix: String = this.todaySuffix,
        hasEventsSuffix: String = this.hasEventsSuffix,
        agendaEmptyState: String = this.agendaEmptyState,
        resourceEmptyState: String = this.resourceEmptyState,
        showAllDayEvents: String = this.showAllDayEvents,
        eventOverflowLabel: (hiddenEventCount: Int) -> String = this.eventOverflowLabel,
        weekNumberAccessibilityLabel: (weekNumber: Int) -> String = this.weekNumberAccessibilityLabel,
        dayAccessibilityLabel: (date: LocalDate, monthName: String) -> String = this.dayAccessibilityLabel,
        dayOfWeekAccessibilityLabel: (DayOfWeek) -> String = this.dayOfWeekAccessibilityLabel,
        eventAccessibilityLabel: (event: KalendarEvent, start: LocalDateTime, end: LocalDateTime) -> String =
            this.eventAccessibilityLabel,
        resourceEventAccessibilityLabel: (
            event: KalendarEvent,
            start: LocalDateTime,
            end: LocalDateTime,
            resourceTitle: String,
        ) -> String = this.resourceEventAccessibilityLabel,
        allDayEventAccessibilityLabel: (event: KalendarEvent) -> String = this.allDayEventAccessibilityLabel,
    ): KalendarStrings = KalendarStrings(
        previousPage = previousPage,
        nextPage = nextPage,
        today = today,
        previousYear = previousYear,
        nextYear = nextYear,
        openJumpPicker = openJumpPicker,
        todaySuffix = todaySuffix,
        hasEventsSuffix = hasEventsSuffix,
        agendaEmptyState = agendaEmptyState,
        resourceEmptyState = resourceEmptyState,
        showAllDayEvents = showAllDayEvents,
        eventOverflowLabel = eventOverflowLabel,
        weekNumberAccessibilityLabel = weekNumberAccessibilityLabel,
        dayAccessibilityLabel = dayAccessibilityLabel,
        dayOfWeekAccessibilityLabel = dayOfWeekAccessibilityLabel,
        eventAccessibilityLabel = eventAccessibilityLabel,
        resourceEventAccessibilityLabel = resourceEventAccessibilityLabel,
        allDayEventAccessibilityLabel = allDayEventAccessibilityLabel,
    )

    @Suppress("CyclomaticComplexMethod")
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is KalendarStrings) return false
        if (previousPage != other.previousPage) return false
        if (nextPage != other.nextPage) return false
        if (today != other.today) return false
        if (previousYear != other.previousYear) return false
        if (nextYear != other.nextYear) return false
        if (openJumpPicker != other.openJumpPicker) return false
        if (todaySuffix != other.todaySuffix) return false
        if (hasEventsSuffix != other.hasEventsSuffix) return false
        if (agendaEmptyState != other.agendaEmptyState) return false
        if (resourceEmptyState != other.resourceEmptyState) return false
        if (showAllDayEvents != other.showAllDayEvents) return false
        if (eventOverflowLabel != other.eventOverflowLabel) return false
        if (weekNumberAccessibilityLabel != other.weekNumberAccessibilityLabel) return false
        if (dayAccessibilityLabel != other.dayAccessibilityLabel) return false
        if (dayOfWeekAccessibilityLabel != other.dayOfWeekAccessibilityLabel) return false
        if (eventAccessibilityLabel != other.eventAccessibilityLabel) return false
        if (resourceEventAccessibilityLabel != other.resourceEventAccessibilityLabel) return false
        if (allDayEventAccessibilityLabel != other.allDayEventAccessibilityLabel) return false
        return true
    }

    override fun hashCode(): Int {
        var result = previousPage.hashCode()
        result = 31 * result + nextPage.hashCode()
        result = 31 * result + today.hashCode()
        result = 31 * result + previousYear.hashCode()
        result = 31 * result + nextYear.hashCode()
        result = 31 * result + openJumpPicker.hashCode()
        result = 31 * result + todaySuffix.hashCode()
        result = 31 * result + hasEventsSuffix.hashCode()
        result = 31 * result + agendaEmptyState.hashCode()
        result = 31 * result + resourceEmptyState.hashCode()
        result = 31 * result + showAllDayEvents.hashCode()
        result = 31 * result + eventOverflowLabel.hashCode()
        result = 31 * result + weekNumberAccessibilityLabel.hashCode()
        result = 31 * result + dayAccessibilityLabel.hashCode()
        result = 31 * result + dayOfWeekAccessibilityLabel.hashCode()
        result = 31 * result + eventAccessibilityLabel.hashCode()
        result = 31 * result + resourceEventAccessibilityLabel.hashCode()
        result = 31 * result + allDayEventAccessibilityLabel.hashCode()
        return result
    }

    override fun toString(): String = "KalendarStrings(" +
        "previousPage=$previousPage, " +
        "nextPage=$nextPage, " +
        "today=$today, " +
        "previousYear=$previousYear, " +
        "nextYear=$nextYear, " +
        "openJumpPicker=$openJumpPicker, " +
        "todaySuffix=$todaySuffix, " +
        "hasEventsSuffix=$hasEventsSuffix, " +
        "agendaEmptyState=$agendaEmptyState, " +
        "resourceEmptyState=$resourceEmptyState, " +
        "showAllDayEvents=$showAllDayEvents, " +
        "eventOverflowLabel=$eventOverflowLabel, " +
        "weekNumberAccessibilityLabel=$weekNumberAccessibilityLabel, " +
        "dayAccessibilityLabel=$dayAccessibilityLabel, " +
        "dayOfWeekAccessibilityLabel=$dayOfWeekAccessibilityLabel, " +
        "eventAccessibilityLabel=$eventAccessibilityLabel, " +
        "resourceEventAccessibilityLabel=$resourceEventAccessibilityLabel, " +
        "allDayEventAccessibilityLabel=$allDayEventAccessibilityLabel" +
        ")"
}

private val defaultDayAccessibilityLabel: (LocalDate, String) -> String =
    { date, monthName -> "$monthName ${date.day}, ${date.year}" }

private val defaultDayOfWeekAccessibilityLabel: (DayOfWeek) -> String = { dayOfWeek ->
    dayOfWeek.name.lowercase().replaceFirstChar { it.titlecase() }
}

private val defaultEventAccessibilityLabel: (KalendarEvent, LocalDateTime, LocalDateTime) -> String =
    { event, start, end -> "${event.eventName}, ${clockLabel(start)} to ${clockLabel(end)}" }

private val defaultResourceEventAccessibilityLabel:
    (KalendarEvent, LocalDateTime, LocalDateTime, String) -> String =
    { event, start, end, resourceTitle ->
        "${event.eventName}, ${clockLabel(start)} to ${clockLabel(end)}, $resourceTitle"
    }

private val defaultAllDayEventAccessibilityLabel: (KalendarEvent) -> String = { "${it.eventName}, all day" }

private fun clockLabel(dateTime: LocalDateTime): String =
    "${dateTime.hour.toString().padStart(2, '0')}:${dateTime.minute.toString().padStart(2, '0')}"
