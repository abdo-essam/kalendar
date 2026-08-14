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

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class KalendarSelectionTest {

    @Test
    fun single_click_replacesSelection() {
        val selection = KalendarSelection.Empty
            .afterClick(KalendarSelectionMode.Single, LocalDate(2026, 8, 12))
            .afterClick(KalendarSelectionMode.Single, LocalDate(2026, 8, 15))
        assertEquals(setOf(LocalDate(2026, 8, 15)), selection.dates)
    }

    @Test
    fun multiple_click_addsThenRemoves() {
        val first = LocalDate(2026, 8, 12)
        val second = LocalDate(2026, 8, 15)

        val both = KalendarSelection.Empty
            .afterClick(KalendarSelectionMode.Multiple, first)
            .afterClick(KalendarSelectionMode.Multiple, second)
        assertEquals(setOf(first, second), both.dates)

        val removed = both.afterClick(KalendarSelectionMode.Multiple, first)
        assertEquals(setOf(second), removed.dates)
    }

    @Test
    fun range_firstClick_selectsOnlyThatDateAndOpensARange() {
        val selection = KalendarSelection.Empty
            .afterClick(KalendarSelectionMode.Range, LocalDate(2026, 8, 12))
        assertEquals(setOf(LocalDate(2026, 8, 12)), selection.dates)
        assertEquals(LocalDate(2026, 8, 12), selection.pendingRangeStart)
    }

    @Test
    fun range_secondClick_selectsInclusiveSpanAndClosesTheRange() {
        val selection = KalendarSelection.Empty
            .afterClick(KalendarSelectionMode.Range, LocalDate(2026, 8, 12))
            .afterClick(KalendarSelectionMode.Range, LocalDate(2026, 8, 15))
        assertEquals(AUGUST_12_TO_15, selection.dates)
        assertNull(selection.pendingRangeStart)
    }

    @Test
    fun range_secondClickBeforeFirst_stillSelectsInclusiveSpan() {
        val selection = KalendarSelection.Empty
            .afterClick(KalendarSelectionMode.Range, LocalDate(2026, 8, 15))
            .afterClick(KalendarSelectionMode.Range, LocalDate(2026, 8, 12))
        assertEquals(AUGUST_12_TO_15, selection.dates)
    }

    @Test
    fun range_thirdClick_startsFreshRange() {
        val selection = KalendarSelection.Empty
            .afterClick(KalendarSelectionMode.Range, LocalDate(2026, 8, 12))
            .afterClick(KalendarSelectionMode.Range, LocalDate(2026, 8, 15))
            .afterClick(KalendarSelectionMode.Range, LocalDate(2026, 9, 1))
        assertEquals(setOf(LocalDate(2026, 9, 1)), selection.dates)
    }

    @Test
    fun cleared_resetsSelectionAndPendingRange() {
        val cleared = KalendarSelection.Empty
            .afterClick(KalendarSelectionMode.Range, LocalDate(2026, 8, 12))
            .cleared()
        assertTrue(cleared.dates.isEmpty())

        val next = cleared.afterClick(KalendarSelectionMode.Range, LocalDate(2026, 9, 1))
        assertEquals(setOf(LocalDate(2026, 9, 1)), next.dates)
    }

    @Test
    fun initialSelection_isRespected() {
        val initial = setOf(LocalDate(2026, 1, 1))
        assertEquals(initial, KalendarSelection(dates = initial).dates)
    }

    @Test
    fun afterRangeDrag_liveUpdatesSelectionToInclusiveSpan() {
        val short = KalendarSelection.Empty
            .afterRangeDrag(LocalDate(2026, 8, 12), LocalDate(2026, 8, 13))
        assertEquals(setOf(LocalDate(2026, 8, 12), LocalDate(2026, 8, 13)), short.dates)

        val long = short.afterRangeDrag(LocalDate(2026, 8, 12), LocalDate(2026, 8, 15))
        assertEquals(AUGUST_12_TO_15, long.dates)
    }

    @Test
    fun afterRangeDragEnd_nextClickStartsFreshRangeInsteadOfCompletingOldOne() {
        val selection = KalendarSelection.Empty
            .afterClick(KalendarSelectionMode.Range, LocalDate(2026, 8, 12))
            .afterRangeDrag(LocalDate(2026, 8, 12), LocalDate(2026, 8, 15))
            .afterRangeDragEnd()
            .afterClick(KalendarSelectionMode.Range, LocalDate(2026, 9, 1))

        assertEquals(setOf(LocalDate(2026, 9, 1)), selection.dates)
    }

    @Test
    fun equality_isStructuralOverBothFields() {
        val one = KalendarSelection(dates = setOf(LocalDate(2026, 8, 12)), pendingRangeStart = LocalDate(2026, 8, 12))
        val same = KalendarSelection(dates = setOf(LocalDate(2026, 8, 12)), pendingRangeStart = LocalDate(2026, 8, 12))
        val differentPending = one.copy(pendingRangeStart = null)

        assertEquals(one, same)
        assertEquals(one.hashCode(), same.hashCode())
        assertTrue(one != differentPending, "a half-finished range is part of the selection's identity")
    }

    private companion object {
        val AUGUST_12_TO_15 = setOf(
            LocalDate(2026, 8, 12),
            LocalDate(2026, 8, 13),
            LocalDate(2026, 8, 14),
            LocalDate(2026, 8, 15),
        )
    }
}
