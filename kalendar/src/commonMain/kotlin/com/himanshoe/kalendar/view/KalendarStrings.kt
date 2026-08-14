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
import kotlinx.datetime.LocalDate

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
 * @property eventOverflowLabel Formats the compact label shown when a day has more events than
 *   [KalendarViewConfig.eventIndicatorCap] dots, given the number of events not shown.
 * @property dayAccessibilityLabel Builds a day cell's base accessibility description from its date
 *   and its already-formatted month name. Defaults to US-style `"August 12, 2026"`; override it
 *   for locales that order or punctuate dates differently. [todaySuffix] and [hasEventsSuffix] are
 *   appended to whatever this returns.
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
    public val eventOverflowLabel: (hiddenEventCount: Int) -> String = { "+$it" },
    public val dayAccessibilityLabel: (date: LocalDate, monthName: String) -> String = defaultDayAccessibilityLabel,
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
        eventOverflowLabel: (hiddenEventCount: Int) -> String = this.eventOverflowLabel,
        dayAccessibilityLabel: (date: LocalDate, monthName: String) -> String = this.dayAccessibilityLabel,
    ): KalendarStrings = KalendarStrings(
        previousPage = previousPage,
        nextPage = nextPage,
        today = today,
        previousYear = previousYear,
        nextYear = nextYear,
        openJumpPicker = openJumpPicker,
        todaySuffix = todaySuffix,
        hasEventsSuffix = hasEventsSuffix,
        eventOverflowLabel = eventOverflowLabel,
        dayAccessibilityLabel = dayAccessibilityLabel,
    )

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
        if (eventOverflowLabel != other.eventOverflowLabel) return false
        if (dayAccessibilityLabel != other.dayAccessibilityLabel) return false
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
        result = 31 * result + eventOverflowLabel.hashCode()
        result = 31 * result + dayAccessibilityLabel.hashCode()
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
        "eventOverflowLabel=$eventOverflowLabel, " +
        "dayAccessibilityLabel=$dayAccessibilityLabel" +
        ")"
}

private val defaultDayAccessibilityLabel: (LocalDate, String) -> String =
    { date, monthName -> "$monthName ${date.dayOfMonth}, ${date.year}" }
