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

package com.himanshoe.kalendar.testing

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import com.himanshoe.kalendar.foundation.format.KalendarFormatters
import com.himanshoe.kalendar.theme.KalendarStrings
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month

/**
 * Matches the day cell for [date], by the accessibility description the library publishes for it.
 *
 * This is the same description a screen reader hears — built by `kalendarDaySemantics` from
 * [KalendarStrings.dayAccessibilityLabel] plus the today/has-events suffixes — so the matcher
 * finds the built-in cells and any custom `dayContent` that applied `kalendarDaySemantics`, and
 * matches whether or not the suffixes happen to be present. Compose's stock semantics assertions
 * then cover the cell's published state: `assertIsSelected()`/`assertIsNotSelected()` for
 * selection, `assertIsNotEnabled()` for a date `KalendarViewConfig.disabledDates` rejected,
 * `performClick()` to tap it.
 *
 * ```kotlin
 * rule.onNode(hasKalendarDay(LocalDate(2026, 8, 15))).assertIsSelected()
 * ```
 *
 * A calendar drawn with non-default strings or month names is matched by passing the same values
 * the calendar was themed with:
 *
 * ```kotlin
 * rule.onNode(hasKalendarDay(date = date, strings = germanStrings, monthNameFormatter = germanMonths))
 * ```
 *
 * @param date The date whose cell to match.
 * @param strings The strings the calendar under test renders with. Defaults to the library's
 *   defaults, which is what a test that did not theme strings is showing.
 * @param monthNameFormatter The month-name formatter the calendar's accessibility descriptions
 *   use — `KalendarViewConfig.monthNameFormatter` when the test set one. Defaults to
 *   [KalendarFormatters.monthName], matching the views' default.
 */
public fun hasKalendarDay(
    date: LocalDate,
    strings: KalendarStrings = KalendarStrings(),
    monthNameFormatter: (Month) -> String = KalendarFormatters.monthName,
): SemanticsMatcher {
    val base = strings.dayAccessibilityLabel(date, monthNameFormatter(date.month))
    val described = setOf(
        base,
        base + strings.todaySuffix,
        base + strings.hasEventsSuffix,
        base + strings.todaySuffix + strings.hasEventsSuffix,
    )
    return SemanticsMatcher("is the Kalendar day cell for $date") { node ->
        node.config.getOrNull(SemanticsProperties.ContentDescription)
            ?.any { description -> description in described } == true
    }
}

/**
 * Matches a day cell announcing itself as today — the cell carrying
 * [KalendarStrings.todaySuffix] in its description, which is the state the time-source seam
 * exists to make testable.
 *
 * ```kotlin
 * rule.onKalendarDay(LocalDate(2026, 3, 10)).assert(isKalendarToday())
 * rule.onKalendarDay(LocalDate(2026, 3, 11)).assert(isKalendarToday().not())
 * ```
 *
 * @param strings The strings the calendar under test renders with; only
 *   [KalendarStrings.todaySuffix] is read.
 */
public fun isKalendarToday(
    strings: KalendarStrings = KalendarStrings(),
): SemanticsMatcher =
    SemanticsMatcher("announces \"${strings.todaySuffix}\"") { node ->
        node.config.getOrNull(SemanticsProperties.ContentDescription)
            ?.any { description -> description.contains(strings.todaySuffix) } == true
    }

/**
 * Matches a day cell announcing that its date has events — the cell carrying
 * [KalendarStrings.hasEventsSuffix] in its description.
 *
 * This is the published has-events signal, so it holds however the events are drawn: dots, span
 * bars, or collapsed into the `+N` overflow label.
 *
 * ```kotlin
 * rule.onKalendarDay(august15).assert(hasKalendarEvents())
 * ```
 *
 * @param strings The strings the calendar under test renders with; only
 *   [KalendarStrings.hasEventsSuffix] is read.
 */
public fun hasKalendarEvents(
    strings: KalendarStrings = KalendarStrings(),
): SemanticsMatcher =
    SemanticsMatcher("announces \"${strings.hasEventsSuffix}\"") { node ->
        node.config.getOrNull(SemanticsProperties.ContentDescription)
            ?.any { description -> description.contains(strings.hasEventsSuffix) } == true
    }

/**
 * The day cell for [date] — [hasKalendarDay] as a finder, for the overwhelmingly common case of
 * one calendar showing one cell per date.
 *
 * ```kotlin
 * rule.onKalendarDay(LocalDate(2026, 8, 15)).assertIsSelected()
 * rule.onKalendarDay(LocalDate(2026, 8, 16)).performClick()
 * ```
 *
 * Like every single-node finder this fails the test if the date matches more than one cell —
 * possible when several views are composed at once, or a timeline shows a date twice in adjacent
 * whole-week month grids. Fall back to `onAllNodes(hasKalendarDay(date))` there.
 *
 * @param date The date whose cell to find.
 * @param strings The strings the calendar under test renders with. See [hasKalendarDay].
 * @param monthNameFormatter The month-name formatter the calendar uses. See [hasKalendarDay].
 */
public fun SemanticsNodeInteractionsProvider.onKalendarDay(
    date: LocalDate,
    strings: KalendarStrings = KalendarStrings(),
    monthNameFormatter: (Month) -> String = KalendarFormatters.monthName,
): SemanticsNodeInteraction =
    onNode(hasKalendarDay(date = date, strings = strings, monthNameFormatter = monthNameFormatter))
