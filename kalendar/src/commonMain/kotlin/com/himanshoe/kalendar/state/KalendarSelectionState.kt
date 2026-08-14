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

package com.himanshoe.kalendar.state

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.himanshoe.kalendar.foundation.selection.KalendarSelection
import com.himanshoe.kalendar.foundation.selection.KalendarSelectionMode
import kotlinx.datetime.LocalDate

/**
 * Hoisted selection state for [KalendarWeek]/[KalendarMonth]/[KalendarYear]/[KalendarTimeline]:
 * [KalendarSelection]'s tap reduction in a snapshot-backed, saveable holder, so you don't have to
 * wire one up yourself.
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
 * dates from ending a range — drive [KalendarSelection] yourself and pass its
 * [dates][KalendarSelection.dates] as `selectedDates`; it's a plain `Set<LocalDate>` on every view.
 */
@Stable
public class KalendarSelectionState internal constructor(
    private val mode: KalendarSelectionMode,
    initialSelection: Set<LocalDate>,
) {
    private var selection: KalendarSelection by mutableStateOf(KalendarSelection(dates = initialSelection))

    /** The currently selected dates, per [mode]'s tap behaviour. */
    public val selectedDates: Set<LocalDate> get() = selection.dates

    /** Call from `onDateClick` to apply [mode]'s tap behaviour for [date]. */
    public fun onDateClick(date: LocalDate) {
        selection = selection.afterClick(mode = mode, date = date)
    }

    /**
     * Call from `onDateRangeSelect` (e.g. [KalendarWeek]/[KalendarMonth]'s press-and-drag
     * range-select) to live-update the selection to every date from [start] to [current],
     * inclusive, regardless of order. Intended for [KalendarSelectionMode.Range]; in the other
     * modes this simply overwrites the selection with the dragged span.
     */
    public fun onRangeDrag(start: LocalDate, current: LocalDate) {
        selection = selection.afterRangeDrag(start = start, current = current)
    }

    /**
     * Call from `onDateRangeSelectEnd` when a drag started by [onRangeDrag] finishes, so the next
     * tap starts a fresh range instead of trying to complete the just-finished one.
     */
    public fun onRangeDragEnd() {
        selection = selection.afterRangeDragEnd()
    }

    /** Clears the current selection and any in-progress range. */
    public fun clear() {
        selection = selection.cleared()
    }

    internal companion object {
        /**
         * Saves the selection and any in-progress range start as epoch-day numbers, so the state
         * survives configuration changes and process death. [mode] is not saved — it's a
         * [rememberKalendarSelectionState] key, so changing it intentionally resets the state.
         */
        fun saver(mode: KalendarSelectionMode): Saver<KalendarSelectionState, Any> = listSaver(
            save = { state ->
                listOf(
                    state.selection.dates.map { it.toEpochDays() },
                    state.selection.pendingRangeStart?.toEpochDays(),
                )
            },
            restore = { saved ->
                @Suppress("UNCHECKED_CAST")
                val selectedDays = saved[0] as List<Long>
                val pendingDay = saved[1] as Long?
                KalendarSelectionState(
                    mode = mode,
                    initialSelection = selectedDays.map(LocalDate::fromEpochDays).toSet(),
                ).also { state ->
                    state.selection = state.selection.copy(
                        pendingRangeStart = pendingDay?.let(LocalDate::fromEpochDays),
                    )
                }
            },
        )
    }
}

/**
 * Creates and remembers a [KalendarSelectionState] for the given [mode], surviving configuration
 * changes and process death via [rememberSaveable] (dates are persisted as epoch-day numbers).
 * Changing [mode] resets the state.
 *
 * @param mode How tapping a date affects the selection. Defaults to [KalendarSelectionMode.Single].
 * @param initialSelection The initially selected dates. Defaults to none.
 */
@Composable
public fun rememberKalendarSelectionState(
    mode: KalendarSelectionMode = KalendarSelectionMode.Single,
    initialSelection: Set<LocalDate> = emptySet(),
): KalendarSelectionState = rememberSaveable(mode, saver = KalendarSelectionState.saver(mode)) {
    KalendarSelectionState(mode, initialSelection)
}
