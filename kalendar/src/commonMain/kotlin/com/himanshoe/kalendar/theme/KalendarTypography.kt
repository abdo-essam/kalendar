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
import androidx.compose.ui.text.TextStyle
import com.himanshoe.kalendar.KalendarAgenda
import com.himanshoe.kalendar.KalendarResourceView
import com.himanshoe.kalendar.KalendarScheduleWeek
import com.himanshoe.kalendar.KalendarTimeline
import com.himanshoe.kalendar.KalendarViewConfig
import com.himanshoe.kalendar.KalendarYear

/**
 * Text styles for every piece of text a Kalendar view draws, replacing the built-in
 * Material-derived scale.
 *
 * Obtain a Material-matching instance from [KalendarThemeDefaults.typography] and `copy` the
 * tokens you want to change, or construct one from scratch to plug in a non-Material type scale:
 *
 * ```kotlin
 * KalendarTheme(
 *     typography = KalendarThemeDefaults.typography().copy(
 *         headerTitle = MyDesignSystem.type.heading3,
 *         dayNumber = MyDesignSystem.type.numeric,
 *     ),
 * ) {
 *     KalendarMonth(selectedDate = today)
 * }
 * ```
 *
 * @property headerTitle The navigation header's title, [KalendarTimeline]'s month titles, and the
 *   jump picker's year.
 * @property monthLabel Month names inside [KalendarYear]'s month grid.
 * @property dayOfWeekLabel The day-of-week column headers.
 * @property dayNumber The day number inside an ordinary day cell.
 * @property dayNumberEmphasized The day number when the cell is selected or is today. Defaults to
 *   [dayNumber] in bold; set it equal to [dayNumber] to drop the emphasis entirely.
 * @property weekDayNumber The date number in [KalendarScheduleWeek]'s day-column headers.
 * @property resourceTitle A lane's name in [KalendarResourceView]'s column headers. Its own token
 *   rather than [weekDayNumber], because a room name is not a day number: it is prose, it can be
 *   long, and a design system will usually want a different style for it. Defaults to the same
 *   scale step [weekDayNumber] uses, so the built-in look is unchanged.
 * @property eventLabel Event block titles and all-day chip titles.
 * @property eventOverflowLabel The compact `+N` label shown when a day has more events than
 *   [KalendarViewConfig.eventIndicatorCap].
 * @property hourLabel The hour-gutter labels on the Schedule views.
 * @property agendaEventTitle An event's name in [KalendarAgenda]'s rows.
 * @property agendaEventSubtitle An event's description and time range in [KalendarAgenda]'s rows,
 *   and its empty-state message.
 */
@Immutable
@Suppress("LongParameterList")
public class KalendarTypography(
    public val headerTitle: TextStyle,
    public val monthLabel: TextStyle,
    public val dayOfWeekLabel: TextStyle,
    public val dayNumber: TextStyle,
    public val dayNumberEmphasized: TextStyle,
    public val weekDayNumber: TextStyle,
    public val resourceTitle: TextStyle,
    public val eventLabel: TextStyle,
    public val eventOverflowLabel: TextStyle,
    public val hourLabel: TextStyle,
    public val agendaEventTitle: TextStyle,
    public val agendaEventSubtitle: TextStyle,
) {

    /** Returns a duplicate of this type scale with only the styles passed here replaced. */
    public fun copy(
        headerTitle: TextStyle = this.headerTitle,
        monthLabel: TextStyle = this.monthLabel,
        dayOfWeekLabel: TextStyle = this.dayOfWeekLabel,
        dayNumber: TextStyle = this.dayNumber,
        dayNumberEmphasized: TextStyle = this.dayNumberEmphasized,
        weekDayNumber: TextStyle = this.weekDayNumber,
        resourceTitle: TextStyle = this.resourceTitle,
        eventLabel: TextStyle = this.eventLabel,
        eventOverflowLabel: TextStyle = this.eventOverflowLabel,
        hourLabel: TextStyle = this.hourLabel,
        agendaEventTitle: TextStyle = this.agendaEventTitle,
        agendaEventSubtitle: TextStyle = this.agendaEventSubtitle,
    ): KalendarTypography = KalendarTypography(
        headerTitle = headerTitle,
        monthLabel = monthLabel,
        dayOfWeekLabel = dayOfWeekLabel,
        dayNumber = dayNumber,
        dayNumberEmphasized = dayNumberEmphasized,
        weekDayNumber = weekDayNumber,
        resourceTitle = resourceTitle,
        eventLabel = eventLabel,
        eventOverflowLabel = eventOverflowLabel,
        hourLabel = hourLabel,
        agendaEventTitle = agendaEventTitle,
        agendaEventSubtitle = agendaEventSubtitle,
    )

    @Suppress("CyclomaticComplexMethod")
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is KalendarTypography) return false
        if (headerTitle != other.headerTitle) return false
        if (monthLabel != other.monthLabel) return false
        if (dayOfWeekLabel != other.dayOfWeekLabel) return false
        if (dayNumber != other.dayNumber) return false
        if (dayNumberEmphasized != other.dayNumberEmphasized) return false
        if (weekDayNumber != other.weekDayNumber) return false
        if (resourceTitle != other.resourceTitle) return false
        if (eventLabel != other.eventLabel) return false
        if (eventOverflowLabel != other.eventOverflowLabel) return false
        if (hourLabel != other.hourLabel) return false
        if (agendaEventTitle != other.agendaEventTitle) return false
        if (agendaEventSubtitle != other.agendaEventSubtitle) return false
        return true
    }

    override fun hashCode(): Int {
        var result = headerTitle.hashCode()
        result = 31 * result + monthLabel.hashCode()
        result = 31 * result + dayOfWeekLabel.hashCode()
        result = 31 * result + dayNumber.hashCode()
        result = 31 * result + dayNumberEmphasized.hashCode()
        result = 31 * result + weekDayNumber.hashCode()
        result = 31 * result + resourceTitle.hashCode()
        result = 31 * result + eventLabel.hashCode()
        result = 31 * result + eventOverflowLabel.hashCode()
        result = 31 * result + hourLabel.hashCode()
        result = 31 * result + agendaEventTitle.hashCode()
        result = 31 * result + agendaEventSubtitle.hashCode()
        return result
    }

    override fun toString(): String = "KalendarTypography(" +
        "headerTitle=$headerTitle, " +
        "monthLabel=$monthLabel, " +
        "dayOfWeekLabel=$dayOfWeekLabel, " +
        "dayNumber=$dayNumber, " +
        "dayNumberEmphasized=$dayNumberEmphasized, " +
        "weekDayNumber=$weekDayNumber, " +
        "resourceTitle=$resourceTitle, " +
        "eventLabel=$eventLabel, " +
        "eventOverflowLabel=$eventOverflowLabel, " +
        "hourLabel=$hourLabel, " +
        "agendaEventTitle=$agendaEventTitle, " +
        "agendaEventSubtitle=$agendaEventSubtitle" +
        ")"
}
