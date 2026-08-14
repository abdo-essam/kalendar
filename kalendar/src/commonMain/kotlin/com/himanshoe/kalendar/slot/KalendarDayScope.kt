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

package com.himanshoe.kalendar.slot

import androidx.compose.runtime.Immutable
import com.himanshoe.kalendar.KalendarMonth
import com.himanshoe.kalendar.KalendarWeek
import com.himanshoe.kalendar.KalendarYear
import com.himanshoe.kalendar.component.KalendarDayCellDefaults
import com.himanshoe.kalendar.foundation.event.KalendarEvent
import kotlinx.datetime.LocalDate

/**
 * The state of a single date cell inside [KalendarWeek], [KalendarMonth], or [KalendarYear].
 *
 * Passed to a custom `dayContent` slot, or rendered by [KalendarDayCellDefaults.Cell] when no
 * slot is provided. Delivered as a scope rather than positional lambda parameters so future
 * fields can be added without a breaking change to every `dayContent` call site.
 *
 * @property date The date this cell represents.
 * @property isSelected Whether [date] is in the calendar's `selectedDates`.
 * @property isToday Whether [date] is today, in the system time zone.
 * @property isDisabled Whether [date] matched `KalendarViewConfig.disabledDates`.
 * @property events The events occurring on [date].
 */
@Immutable
public class KalendarDayScope(
    public val date: LocalDate,
    public val isSelected: Boolean,
    public val isToday: Boolean,
    public val isDisabled: Boolean,
    public val events: List<KalendarEvent>,
) {

    /** Returns a duplicate of this scope with only the fields passed here replaced. */
    public fun copy(
        date: LocalDate = this.date,
        isSelected: Boolean = this.isSelected,
        isToday: Boolean = this.isToday,
        isDisabled: Boolean = this.isDisabled,
        events: List<KalendarEvent> = this.events,
    ): KalendarDayScope = KalendarDayScope(
        date = date,
        isSelected = isSelected,
        isToday = isToday,
        isDisabled = isDisabled,
        events = events,
    )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is KalendarDayScope) return false
        if (date != other.date) return false
        if (isSelected != other.isSelected) return false
        if (isToday != other.isToday) return false
        if (isDisabled != other.isDisabled) return false
        if (events != other.events) return false
        return true
    }

    override fun hashCode(): Int {
        var result = date.hashCode()
        result = 31 * result + isSelected.hashCode()
        result = 31 * result + isToday.hashCode()
        result = 31 * result + isDisabled.hashCode()
        result = 31 * result + events.hashCode()
        return result
    }

    override fun toString(): String = "KalendarDayScope(" +
        "date=$date, " +
        "isSelected=$isSelected, " +
        "isToday=$isToday, " +
        "isDisabled=$isDisabled, " +
        "events=$events" +
        ")"
}
