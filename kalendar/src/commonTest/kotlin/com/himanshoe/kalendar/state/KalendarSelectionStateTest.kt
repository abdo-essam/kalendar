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

import com.himanshoe.kalendar.foundation.selection.KalendarSelectionMode
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The holder around the reduction: that a tap reaches it, and that the saver round-trips both the
 * selection and a half-finished range. The reduction's own behaviour is tested in
 * `kalendar-foundation`.
 */
class KalendarSelectionStateTest {

    @Test
    fun onDateClick_appliesTheModesReduction() {
        val state = KalendarSelectionState(KalendarSelectionMode.Multiple, emptySet())
        state.onDateClick(LocalDate(2026, 8, 12))
        state.onDateClick(LocalDate(2026, 8, 15))

        assertEquals(setOf(LocalDate(2026, 8, 12), LocalDate(2026, 8, 15)), state.selectedDates)
    }

    @Test
    fun initialSelection_isRespected() {
        val initial = setOf(LocalDate(2026, 1, 1))
        val state = KalendarSelectionState(KalendarSelectionMode.Single, initial)
        assertEquals(initial, state.selectedDates)
    }

    @Test
    fun clear_emptiesTheSelection() {
        val state = KalendarSelectionState(KalendarSelectionMode.Range, emptySet())
        state.onDateClick(LocalDate(2026, 8, 12))
        state.clear()

        assertEquals(emptySet(), state.selectedDates)
    }

    @Test
    fun saver_roundTrip_restoresSelection() {
        val original = KalendarSelectionState(KalendarSelectionMode.Multiple, emptySet())
        original.onDateClick(LocalDate(2026, 8, 12))
        original.onDateClick(LocalDate(2026, 8, 15))

        val restored = saveAndRestore(original, KalendarSelectionMode.Multiple)
        assertEquals(original.selectedDates, restored.selectedDates)
    }

    @Test
    fun saver_roundTrip_restoresPendingRangeStart() {
        val original = KalendarSelectionState(KalendarSelectionMode.Range, emptySet())
        original.onDateClick(LocalDate(2026, 8, 12))

        val restored = saveAndRestore(original, KalendarSelectionMode.Range)
        restored.onDateClick(LocalDate(2026, 8, 15))
        assertEquals(
            setOf(
                LocalDate(2026, 8, 12),
                LocalDate(2026, 8, 13),
                LocalDate(2026, 8, 14),
                LocalDate(2026, 8, 15),
            ),
            restored.selectedDates,
        )
    }

    private fun saveAndRestore(
        state: KalendarSelectionState,
        mode: KalendarSelectionMode,
    ): KalendarSelectionState {
        val saver = KalendarSelectionState.saver(mode)
        val saved = with(saver) { androidx.compose.runtime.saveable.SaverScope { true }.save(state) }
        checkNotNull(saved) { "saver produced null" }
        return checkNotNull(saver.restore(saved))
    }
}
