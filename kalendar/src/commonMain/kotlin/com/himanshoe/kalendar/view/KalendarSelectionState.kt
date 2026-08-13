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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.datetime.LocalDate

/**
 * How [KalendarSelectionState] responds to a date being tapped.
 */
public sealed interface KalendarSelectionMode {

    /** Tapping a date replaces the current selection with just that date. */
    public data object Single : KalendarSelectionMode

    /** Tapping a date adds it to the selection, or removes it if already selected. */
    public data object Multiple : KalendarSelectionMode

    /**
     * Tapping a date starts a new range; tapping a second date completes it, selecting every date
     * between the two (inclusive, regardless of tap order) via [datesUntil]. The next tap after a
     * range is complete starts a fresh range.
     */
    public data object Range : KalendarSelectionMode
}

/**
 * Hoisted selection state for [KalendarWeek]/[KalendarMonth]/[KalendarYear]/[KalendarTimeline],
 * implementing [KalendarSelectionMode]'s tap behaviour so you don't have to hand-roll it.
 *
 * ```kotlin
 * val selection = rememberKalendarSelectionState(mode = KalendarSelectionMode.Range)
 *
 * KalendarMonth(
 *     selectedDate = today,
 *     selectedDates = selection.selectedDates,
 *     onDateClick = { date, _ -> selection.onDateClick(date) },
 * )
 * ```
 *
 * For anything this doesn't cover — e.g. a minimum/maximum range length, or disallowing certain
 * dates from ending a range — manage `selectedDates` yourself instead; it's a plain `Set<LocalDate>`
 * on every view.
 */
@Stable
public class KalendarSelectionState internal constructor(
    private val mode: KalendarSelectionMode,
    initialSelection: Set<LocalDate>,
) {
    /** The currently selected dates, per [mode]'s tap behaviour. */
    public var selectedDates: Set<LocalDate> by mutableStateOf(initialSelection)
        private set

    private var pendingRangeStart: LocalDate? = null

    /** Call from `onDateClick` to apply [mode]'s tap behaviour for [date]. */
    public fun onDateClick(date: LocalDate) {
        selectedDates = when (mode) {
            KalendarSelectionMode.Single -> setOf(date)
            KalendarSelectionMode.Multiple -> {
                if (date in selectedDates) selectedDates - date else selectedDates + date
            }
            KalendarSelectionMode.Range -> {
                val start = pendingRangeStart
                if (start == null) {
                    pendingRangeStart = date
                    setOf(date)
                } else {
                    pendingRangeStart = null
                    start.datesUntil(date)
                }
            }
        }
    }

    /**
     * Call from `onDateRangeSelect` (e.g. [KalendarWeek]/[KalendarMonth]'s press-and-drag
     * range-select) to live-update the selection to every date from [start] to [current],
     * inclusive, regardless of order. Intended for [KalendarSelectionMode.Range]; in the other
     * modes this simply overwrites the selection with the dragged span.
     */
    public fun onRangeDrag(start: LocalDate, current: LocalDate) {
        selectedDates = start.datesUntil(current)
    }

    /**
     * Call from `onDateRangeSelectEnd` when a drag started by [onRangeDrag] finishes, so the next
     * tap starts a fresh range instead of trying to complete the just-finished one.
     */
    public fun onRangeDragEnd() {
        pendingRangeStart = null
    }

    /** Clears the current selection and any in-progress range. */
    public fun clear() {
        pendingRangeStart = null
        selectedDates = emptySet()
    }
}

/**
 * Creates and [remember]s a [KalendarSelectionState] for the given [mode].
 *
 * @param mode How tapping a date affects the selection. Defaults to [KalendarSelectionMode.Single].
 * @param initialSelection The initially selected dates. Defaults to none.
 */
@Composable
public fun rememberKalendarSelectionState(
    mode: KalendarSelectionMode = KalendarSelectionMode.Single,
    initialSelection: Set<LocalDate> = emptySet(),
): KalendarSelectionState = remember(mode) { KalendarSelectionState(mode, initialSelection) }
