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

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KalendarSelectionStateTest {

    @Test
    fun single_click_replacesSelection() {
        val state = KalendarSelectionState(KalendarSelectionMode.Single, emptySet())
        state.onDateClick(LocalDate(2026, 8, 12))
        state.onDateClick(LocalDate(2026, 8, 15))
        assertEquals(setOf(LocalDate(2026, 8, 15)), state.selectedDates)
    }

    @Test
    fun multiple_click_addsThenRemoves() {
        val state = KalendarSelectionState(KalendarSelectionMode.Multiple, emptySet())
        val first = LocalDate(2026, 8, 12)
        val second = LocalDate(2026, 8, 15)

        state.onDateClick(first)
        state.onDateClick(second)
        assertEquals(setOf(first, second), state.selectedDates)

        state.onDateClick(first)
        assertEquals(setOf(second), state.selectedDates)
    }

    @Test
    fun range_firstClick_selectsOnlyThatDate() {
        val state = KalendarSelectionState(KalendarSelectionMode.Range, emptySet())
        state.onDateClick(LocalDate(2026, 8, 12))
        assertEquals(setOf(LocalDate(2026, 8, 12)), state.selectedDates)
    }

    @Test
    fun range_secondClick_selectsInclusiveSpan() {
        val state = KalendarSelectionState(KalendarSelectionMode.Range, emptySet())
        state.onDateClick(LocalDate(2026, 8, 12))
        state.onDateClick(LocalDate(2026, 8, 15))
        assertEquals(
            setOf(
                LocalDate(2026, 8, 12),
                LocalDate(2026, 8, 13),
                LocalDate(2026, 8, 14),
                LocalDate(2026, 8, 15),
            ),
            state.selectedDates,
        )
    }

    @Test
    fun range_secondClickBeforeFirst_stillSelectsInclusiveSpan() {
        val state = KalendarSelectionState(KalendarSelectionMode.Range, emptySet())
        state.onDateClick(LocalDate(2026, 8, 15))
        state.onDateClick(LocalDate(2026, 8, 12))
        assertEquals(
            setOf(
                LocalDate(2026, 8, 12),
                LocalDate(2026, 8, 13),
                LocalDate(2026, 8, 14),
                LocalDate(2026, 8, 15),
            ),
            state.selectedDates,
        )
    }

    @Test
    fun range_thirdClick_startsFreshRange() {
        val state = KalendarSelectionState(KalendarSelectionMode.Range, emptySet())
        state.onDateClick(LocalDate(2026, 8, 12))
        state.onDateClick(LocalDate(2026, 8, 15))
        state.onDateClick(LocalDate(2026, 9, 1))
        assertEquals(setOf(LocalDate(2026, 9, 1)), state.selectedDates)
    }

    @Test
    fun clear_resetsSelectionAndPendingRange() {
        val state = KalendarSelectionState(KalendarSelectionMode.Range, emptySet())
        state.onDateClick(LocalDate(2026, 8, 12))
        state.clear()
        assertTrue(state.selectedDates.isEmpty())

        state.onDateClick(LocalDate(2026, 9, 1))
        assertEquals(setOf(LocalDate(2026, 9, 1)), state.selectedDates)
    }

    @Test
    fun initialSelection_isRespected() {
        val initial = setOf(LocalDate(2026, 1, 1))
        val state = KalendarSelectionState(KalendarSelectionMode.Single, initial)
        assertEquals(initial, state.selectedDates)
    }

    @Test
    fun onRangeDrag_liveUpdatesSelectionToInclusiveSpan() {
        val state = KalendarSelectionState(KalendarSelectionMode.Range, emptySet())
        state.onRangeDrag(LocalDate(2026, 8, 12), LocalDate(2026, 8, 13))
        assertEquals(
            setOf(LocalDate(2026, 8, 12), LocalDate(2026, 8, 13)),
            state.selectedDates,
        )

        state.onRangeDrag(LocalDate(2026, 8, 12), LocalDate(2026, 8, 15))
        assertEquals(
            setOf(
                LocalDate(2026, 8, 12),
                LocalDate(2026, 8, 13),
                LocalDate(2026, 8, 14),
                LocalDate(2026, 8, 15),
            ),
            state.selectedDates,
        )
    }

    @Test
    fun onRangeDragEnd_nextClickStartsFreshRangeInsteadOfCompletingOldOne() {
        val state = KalendarSelectionState(KalendarSelectionMode.Range, emptySet())
        state.onDateClick(LocalDate(2026, 8, 12))
        state.onRangeDrag(LocalDate(2026, 8, 12), LocalDate(2026, 8, 15))
        state.onRangeDragEnd()

        state.onDateClick(LocalDate(2026, 9, 1))
        assertEquals(setOf(LocalDate(2026, 9, 1)), state.selectedDates)
    }
}
