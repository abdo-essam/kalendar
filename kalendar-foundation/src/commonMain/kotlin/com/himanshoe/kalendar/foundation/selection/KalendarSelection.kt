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

package com.himanshoe.kalendar.foundation.selection

import androidx.compose.runtime.Immutable
import com.himanshoe.kalendar.foundation.datetime.datesUntil
import kotlinx.datetime.LocalDate

/**
 * How a selection responds to a date being tapped.
 *
 * ```kotlin
 * var selection = KalendarSelection.Empty
 * selection = selection.afterClick(KalendarSelectionMode.Range, LocalDate(2026, 8, 12))
 * selection = selection.afterClick(KalendarSelectionMode.Range, LocalDate(2026, 8, 15))
 * selection.dates // the 12th through the 15th
 * ```
 */
public sealed interface KalendarSelectionMode {

    /** Tapping a date replaces the current selection with just that date. */
    public data object Single : KalendarSelectionMode

    /** Tapping a date adds it to the selection, or removes it if already selected. */
    public data object Multiple : KalendarSelectionMode

    /**
     * Tapping a date starts a new range; tapping a second date completes it, selecting every date
     * between the two (inclusive, regardless of tap order). The next tap after a range is complete
     * starts a fresh range.
     */
    public data object Range : KalendarSelectionMode
}

/**
 * A calendar's selection and the half-finished range behind it — the whole state a tap reduces, with
 * no UI attached.
 *
 * Every operation returns a new instance, so this can be held in whatever state container a caller
 * already has: a `MutableState`, a `StateFlow`, a `savedInstanceState` bundle. The built-in views'
 * selection state holder is a thin wrapper over exactly these calls.
 *
 * ```kotlin
 * var selection by remember { mutableStateOf(KalendarSelection.Empty) }
 *
 * DayCell(
 *     isSelected = date in selection.dates,
 *     onClick = { selection = selection.afterClick(KalendarSelectionMode.Multiple, date) },
 * )
 * ```
 *
 * @property dates The currently selected dates.
 * @property pendingRangeStart The first date of a [KalendarSelectionMode.Range] range whose second
 *   tap has not arrived yet, or `null` when no range is in progress. Persist it alongside [dates] to
 *   let a half-finished range survive process death.
 */
@Immutable
public class KalendarSelection(
    public val dates: Set<LocalDate> = emptySet(),
    public val pendingRangeStart: LocalDate? = null,
) {

    /**
     * The selection after [date] is tapped under [mode].
     *
     * ```kotlin
     * KalendarSelection.Empty
     *     .afterClick(KalendarSelectionMode.Multiple, LocalDate(2026, 8, 12))
     *     .afterClick(KalendarSelectionMode.Multiple, LocalDate(2026, 8, 15))
     *     .dates // both dates
     * ```
     */
    public fun afterClick(mode: KalendarSelectionMode, date: LocalDate): KalendarSelection = when (mode) {
        KalendarSelectionMode.Single -> KalendarSelection(
            dates = setOf(date),
            pendingRangeStart = pendingRangeStart,
        )

        KalendarSelectionMode.Multiple -> KalendarSelection(
            dates = if (date in dates) dates - date else dates + date,
            pendingRangeStart = pendingRangeStart,
        )

        KalendarSelectionMode.Range -> {
            val start = pendingRangeStart
            if (start == null) {
                KalendarSelection(dates = setOf(date), pendingRangeStart = date)
            } else {
                KalendarSelection(dates = start.datesUntil(date), pendingRangeStart = null)
            }
        }
    }

    /**
     * The selection after a press-and-drag range gesture has swept from [start] to [current]: every
     * date between the two, inclusive, regardless of order.
     *
     * Call it on every pointer move to get a live-updating highlight. It overwrites [dates] outright,
     * so it is a range-mode operation whatever [KalendarSelectionMode] the caller is otherwise in.
     */
    public fun afterRangeDrag(start: LocalDate, current: LocalDate): KalendarSelection =
        KalendarSelection(dates = start.datesUntil(current), pendingRangeStart = pendingRangeStart)

    /**
     * The selection after a drag started by [afterRangeDrag] finishes, so the next tap starts a
     * fresh range instead of trying to complete the just-finished one.
     */
    public fun afterRangeDragEnd(): KalendarSelection =
        KalendarSelection(dates = dates, pendingRangeStart = null)

    /** An empty selection with no range in progress. */
    public fun cleared(): KalendarSelection = Empty

    /** Returns a duplicate of this selection with only the values passed here replaced. */
    public fun copy(
        dates: Set<LocalDate> = this.dates,
        pendingRangeStart: LocalDate? = this.pendingRangeStart,
    ): KalendarSelection = KalendarSelection(dates = dates, pendingRangeStart = pendingRangeStart)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is KalendarSelection) return false
        if (dates != other.dates) return false
        if (pendingRangeStart != other.pendingRangeStart) return false
        return true
    }

    override fun hashCode(): Int = 31 * dates.hashCode() + pendingRangeStart.hashCode()

    override fun toString(): String =
        "KalendarSelection(dates=$dates, pendingRangeStart=$pendingRangeStart)"

    public companion object {

        /** Nothing selected and no range in progress — where a calendar starts. */
        public val Empty: KalendarSelection = KalendarSelection()
    }
}
