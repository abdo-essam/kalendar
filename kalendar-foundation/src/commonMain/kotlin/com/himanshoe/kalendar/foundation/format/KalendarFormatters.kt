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

package com.himanshoe.kalendar.foundation.format

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month

private const val SHORT_NAME_LENGTH = 3

/**
 * The fallback labels a calendar draws when its caller supplies no formatter of its own.
 *
 * kotlinx-datetime carries no locale data, so every one of these is **plain English**. They exist so
 * a calendar renders something readable out of the box, and so each label is a separate hook: pass
 * your own function backed by a platform date API (`java.time`, `NSDateFormatter`, `Intl`, ...) for
 * anything localized.
 *
 * ```kotlin
 * KalendarFormatters.monthName(Month.AUGUST)          // "August"
 * KalendarFormatters.shortMonthName(Month.AUGUST)     // "Aug"
 * KalendarFormatters.dayOfWeekName(DayOfWeek.MONDAY)  // "Mon"
 * KalendarFormatters.dayOfWeekLabel(DayOfWeek.MONDAY) // "M"
 * KalendarFormatters.hourLabel(9)                     // "09:00"
 * ```
 */
public object KalendarFormatters {

    /** The month's plain English name, e.g. `"August"`. */
    public val monthName: (Month) -> String = {
        it.name.lowercase().replaceFirstChar { char -> char.titlecase() }
    }

    /**
     * The plain English month name's first three letters, e.g. `"Aug"`.
     *
     * Truncating is only safe here because the name is known to be English — which is precisely why
     * this is its own hook rather than a `take(3)` applied to whatever a caller supplied. Blind
     * truncation turns Finnish "toukokuu" into "tou" and can cut a Devanagari name after a virama.
     */
    public val shortMonthName: (Month) -> String = { monthName(it).take(SHORT_NAME_LENGTH) }

    /** The plain English day name's first three letters, e.g. `"Mon"`. */
    public val dayOfWeekName: (DayOfWeek) -> String = { dayOfWeek ->
        dayOfWeek.name.take(SHORT_NAME_LENGTH).lowercase().replaceFirstChar { it.titlecase() }
    }

    /** The day name's first letter, e.g. `"M"` — the narrowest a column header gets. */
    public val dayOfWeekLabel: (DayOfWeek) -> String = { it.name.take(1) }

    /** An hour of the day as 24-hour `"HH:00"`, e.g. `"09:00"`. Supply your own for 12-hour AM/PM. */
    public val hourLabel: (Int) -> String = { "${it.toString().padStart(2, '0')}:00" }
}

/**
 * A month page's title: the month's name followed by its year. The day of [monthStart] is ignored,
 * so any date in the month gives the same title.
 *
 * ```kotlin
 * monthTitle(LocalDate(2026, 8, 15))                       // "August 2026"
 * monthTitle(LocalDate(2026, 8, 1), localizedMonthName)    // whatever your formatter returns
 * ```
 *
 * @param monthStart Any date in the month being titled.
 * @param monthNameFormatter How to name the month. Defaults to [KalendarFormatters.monthName].
 */
public fun monthTitle(
    monthStart: LocalDate,
    monthNameFormatter: (Month) -> String = KalendarFormatters.monthName,
): String = "${monthNameFormatter(monthStart.month)} ${monthStart.year}"
