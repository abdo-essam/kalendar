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

import androidx.compose.foundation.focusable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.plus

/** Days in one grid row, which is also how far an up/down arrow moves. */
internal const val DAYS_PER_WEEK = 7

/**
 * Where keyboard focus currently sits inside a paged calendar, and whether a `Shift`-extended range
 * is in progress.
 *
 * Hoisted above the pager rather than kept per page, so that stepping off the edge of one month into
 * the next carries the focus with it. It is a `@Stable` holder rather than a pair of hoisted
 * `State`s for one reason that the recomposition benchmark cares about: passing the holder down lets
 * [KalendarGrid] receive the same instance on every recomposition and still skip, while every
 * *read* of [focusedDate] happens inside a layout or semantics lambda, where a change re-lays-out or
 * re-announces without recomposing the 42 cells.
 */
@Stable
internal class KalendarGridKeyboardState {

    /** The date the focus ring is drawn around, or `null` before the grid has ever held focus. */
    var focusedDate: LocalDate? by mutableStateOf(null)

    /** Whether the grid itself — not a descendant, and not the header — is the focus target. */
    var isFocused: Boolean by mutableStateOf(false)

    /**
     * The date a `Shift`-extended range started from, or `null` when no range is in progress. The
     * range being reported is always (this, [focusedDate]).
     */
    var rangeAnchor: LocalDate? = null
}

/** Creates and [remember]s the [KalendarGridKeyboardState] shared by a paged view and its grids. */
@Composable
internal fun rememberKalendarGridKeyboardState(): KalendarGridKeyboardState =
    remember { KalendarGridKeyboardState() }

/**
 * The date a navigation key moves focus to from [focused], or `null` if [key] does not navigate.
 *
 * Arrows step by one day and one row, so wrapping from the end of a week to the start of the next
 * falls out of date arithmetic rather than index arithmetic — the caller decides whether the result
 * still lies on the visible page. `Home`/`End` land on the first and last day of [periodDates], the
 * *period* rather than the grid, so `Home` in a month view reaches the 1st and not the leading
 * padding day from the previous month. `PageUp`/`PageDown` delegate to [pageStep], which is the
 * view's own unit: a week for [KalendarWeek], a month for [KalendarMonth].
 */
@Suppress("ReturnCount")
internal fun keyboardTargetDate(
    key: Key,
    focused: LocalDate,
    periodDates: List<LocalDate>,
    pageStep: (date: LocalDate, pages: Int) -> LocalDate,
): LocalDate? = when (key) {
    Key.DirectionLeft -> focused.plus(-1, DateTimeUnit.DAY)
    Key.DirectionRight -> focused.plus(1, DateTimeUnit.DAY)
    Key.DirectionUp -> focused.plus(-DAYS_PER_WEEK, DateTimeUnit.DAY)
    Key.DirectionDown -> focused.plus(DAYS_PER_WEEK, DateTimeUnit.DAY)
    Key.PageUp -> pageStep(focused, -1)
    Key.PageDown -> pageStep(focused, 1)
    Key.MoveHome -> periodDates.firstOrNull()
    Key.MoveEnd -> periodDates.lastOrNull()
    else -> null
}

/** Whether [key] is one of the keys that commits the focused date as a selection. */
internal fun isKeyboardSelectKey(key: Key): Boolean =
    key == Key.Enter || key == Key.NumPadEnter || key == Key.Spacebar

/** Whether [key] is a shift key, whose release ends a `Shift`-extended range. */
private fun isShiftKey(key: Key): Boolean = key == Key.ShiftLeft || key == Key.ShiftRight

/**
 * Makes the node this is applied to the calendar grid's single tab stop, and drives
 * [keyboard] from the keyboard.
 *
 * Apply it to a container wrapping the pager — not to a day cell. The whole grid is one tab stop,
 * the roving-tabindex pattern every native date picker uses: `Tab` enters the grid once, arrows move
 * a focus ring between days without leaving it, and `Tab` again leaves. Reaching 42 cells by
 * pressing `Tab` 42 times is not navigation, it is a tab trap with extra steps, which is why
 * [KalendarGrid] deactivates focus on the cells themselves.
 *
 * What each key does:
 * - **Arrows** move one day or one row. Off the end of the visible page, [onScrollTo] pages and the
 *   focus lands on the same date on the new page, so a month boundary is invisible to the user.
 * - **Enter/Space** select via [onSelect], which is the same callback a tap goes through.
 * - **PageUp/PageDown** move by [pageStep] — one page of the view's own unit.
 * - **Home/End** move to the first and last day of [periodDates].
 * - **Shift + arrow** extends a range from the date focus was on when `Shift` was first held,
 *   reporting through [onRangeDrag] exactly as the press-and-hold drag gesture does; releasing
 *   `Shift` calls [onRangeDragEnd]. This is the whole point of the exercise: without it
 *   `onDateRangeSelect` is reachable by pointer only, which is a WCAG 2.1.1 failure.
 *
 * **Disabled dates are focusable but not selectable.** Focus lands on a date
 * [isDateEnabled] rejects — a keyboard user is told the date exists and is unavailable, rather than
 * having it silently skipped and a hole appear in the grid — but `Enter`/`Space` on it does nothing,
 * exactly as the cell's own `clickable(enabled = false)` behaves for a tap. Range extension does not
 * consult [isDateEnabled] at all, matching the drag gesture, which never has either.
 *
 * @param keyboard The focus state shared with every [KalendarGrid] under this node.
 * @param periodDates The visible period's own dates — a month's days rather than its 42-cell grid,
 *   so the trailing padding cells belonging to the next month are never focused in place of that
 *   month's real ones. It drives `Home`/`End`, and a navigation target outside it is what triggers
 *   [onScrollTo].
 * @param today Today's date, used as the entry point when the grid is focused for the first time and
 *   nothing is selected.
 * @param selectedDate The date focus enters on, preferred over [today]. Pass the single selected
 *   date, or `null` for none.
 * @param onSelect Called by `Enter`/`Space` with the focused date, when [isDateEnabled] allows it.
 * @param onScrollTo Called with a date on another page, to bring that page into view.
 * @param pageStep Returns the date `pages` pages away from `date`, in the view's own unit.
 * @param isDateEnabled Whether a date may be selected — [KalendarViewConfig.disabledDates] inverted.
 * @param onRangeDrag Reports a `Shift`-extended range as (anchor, focus). `null` disables range
 *   extension, matching a view whose `onDateRangeSelect` was left unset.
 * @param onRangeDragEnd Called once when `Shift` is released after a range extension.
 * @param monthNameFormatter Formats the month name inside the announcement of the newly focused
 *   date.
 */
@Composable
@Suppress("CyclomaticComplexMethod", "LongMethod")
internal fun Modifier.kalendarGridKeyboard(
    keyboard: KalendarGridKeyboardState,
    periodDates: List<LocalDate>,
    today: LocalDate,
    selectedDate: LocalDate?,
    onSelect: (LocalDate) -> Unit,
    onScrollTo: (LocalDate) -> Unit,
    pageStep: (date: LocalDate, pages: Int) -> LocalDate,
    isDateEnabled: (LocalDate) -> Boolean,
    onRangeDrag: ((start: LocalDate, current: LocalDate) -> Unit)?,
    onRangeDragEnd: (() -> Unit)?,
    monthNameFormatter: (Month) -> String,
): Modifier {
    val strings = kalendarStrings()

    // Every callback and every list the handler needs is read through rememberUpdatedState, so the
    // handler lambda itself never has to be recreated — recreating it would tear down and rebuild
    // the key-input modifier node on each recomposition, and the pointer gesture code next door
    // keeps the same discipline for the same reason.
    val latestPeriodDates = rememberUpdatedState(periodDates)
    val latestToday = rememberUpdatedState(today)
    val latestSelectedDate = rememberUpdatedState(selectedDate)
    val latestOnSelect = rememberUpdatedState(onSelect)
    val latestOnScrollTo = rememberUpdatedState(onScrollTo)
    val latestPageStep = rememberUpdatedState(pageStep)
    val latestIsDateEnabled = rememberUpdatedState(isDateEnabled)
    val latestOnRangeDrag = rememberUpdatedState(onRangeDrag)
    val latestOnRangeDragEnd = rememberUpdatedState(onRangeDragEnd)
    val latestMonthNameFormatter = rememberUpdatedState(monthNameFormatter)

    return this
        .onFocusChanged { focusState ->
            keyboard.isFocused = focusState.isFocused
            if (focusState.isFocused) {
                if (keyboard.focusedDate == null) {
                    keyboard.focusedDate = latestSelectedDate.value
                        ?.takeIf { it in latestPeriodDates.value }
                        ?: latestToday.value.takeIf { it in latestPeriodDates.value }
                        ?: latestPeriodDates.value.firstOrNull()
                }
            } else if (keyboard.rangeAnchor != null) {
                keyboard.rangeAnchor = null
                latestOnRangeDragEnd.value?.invoke()
            }
        }
        .onPreviewKeyEvent { event ->
            if (!keyboard.isFocused) {
                return@onPreviewKeyEvent false
            }
            if (event.type == KeyEventType.KeyUp) {
                return@onPreviewKeyEvent endRangeOnShiftRelease(
                    event = event,
                    keyboard = keyboard,
                    onRangeDragEnd = latestOnRangeDragEnd.value,
                )
            }
            if (event.type != KeyEventType.KeyDown) {
                return@onPreviewKeyEvent false
            }
            val focused = keyboard.focusedDate ?: return@onPreviewKeyEvent false
            if (isKeyboardSelectKey(event.key)) {
                if (latestIsDateEnabled.value(focused)) {
                    latestOnSelect.value(focused)
                }
                return@onPreviewKeyEvent true
            }
            val target = keyboardTargetDate(
                key = event.key,
                focused = focused,
                periodDates = latestPeriodDates.value,
                pageStep = latestPageStep.value,
            ) ?: return@onPreviewKeyEvent false

            val extending = event.isShiftPressed && latestOnRangeDrag.value != null
            if (extending) {
                val anchor = keyboard.rangeAnchor ?: focused
                keyboard.rangeAnchor = anchor
                keyboard.focusedDate = target
                latestOnRangeDrag.value?.invoke(anchor, target)
            } else {
                keyboard.rangeAnchor = null
                keyboard.focusedDate = target
            }
            if (target !in latestPeriodDates.value) {
                latestOnScrollTo.value(target)
            }
            true
        }
        .focusable()
        .semantics {
            // Read inside the semantics lambda on purpose: a change re-runs *this* lambda and
            // re-announces, without recomposing the grid's cells.
            val focused = keyboard.focusedDate
            if (focused != null && keyboard.isFocused) {
                contentDescription = keyboardFocusAnnouncement(
                    date = focused,
                    strings = strings,
                    monthNameFormatter = latestMonthNameFormatter.value,
                    isToday = focused == latestToday.value,
                )
                liveRegion = LiveRegionMode.Polite
            }
        }
}

/**
 * Ends an in-progress `Shift`-extended range when the shift key itself comes up. Returns `false`
 * either way: releasing shift is not a key the grid consumes, it just closes the range the way
 * lifting a finger closes the drag gesture.
 */
private fun endRangeOnShiftRelease(
    event: KeyEvent,
    keyboard: KalendarGridKeyboardState,
    onRangeDragEnd: (() -> Unit)?,
): Boolean {
    if (isShiftKey(event.key) && keyboard.rangeAnchor != null) {
        keyboard.rangeAnchor = null
        onRangeDragEnd?.invoke()
    }
    return false
}

/**
 * What a screen reader says when focus lands on [date]: the same sentence the day cell itself
 * announces, so moving by keyboard and moving by touch exploration sound alike.
 */
internal fun keyboardFocusAnnouncement(
    date: LocalDate,
    strings: KalendarStrings,
    monthNameFormatter: (Month) -> String,
    isToday: Boolean,
): String = buildString {
    append(strings.dayAccessibilityLabel(date, monthNameFormatter(date.month)))
    if (isToday) {
        append(strings.todaySuffix)
    }
}
