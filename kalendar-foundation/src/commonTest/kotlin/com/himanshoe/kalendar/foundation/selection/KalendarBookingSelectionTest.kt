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
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class KalendarBookingSelectionTest {

    private val august = { day: Int -> LocalDate(2026, 8, day) }

    @Test
    fun firstTapOpensAStay_secondCompletesIt() {
        val constraints = KalendarBookingConstraints()
        val first = KalendarBookingSelection.Empty.afterClick(constraints, august(12))
        assertEquals(august(12), first.selection.checkIn)
        assertNull(first.selection.checkOut)
        assertNull(first.violation)

        val second = first.selection.afterClick(constraints, august(15))
        assertEquals(KalendarStay(checkIn = august(12), checkOut = august(15)), second.selection.stay)
        assertNull(second.violation)
    }

    @Test
    fun tappingThePendingCheckInAgain_clearsTheSelection() {
        val constraints = KalendarBookingConstraints()
        val pending = KalendarBookingSelection.Empty.afterClick(constraints, august(12)).selection
        val result = pending.afterClick(constraints, august(12))
        assertEquals(KalendarBookingSelection.Empty, result.selection)
        assertNull(result.violation)
    }

    @Test
    fun tappingAnEarlierDate_movesThePendingCheckIn() {
        val constraints = KalendarBookingConstraints()
        val pending = KalendarBookingSelection.Empty.afterClick(constraints, august(12)).selection
        val result = pending.afterClick(constraints, august(8))
        assertEquals(august(8), result.selection.checkIn)
        assertNull(result.selection.checkOut)
        assertNull(result.violation)
    }

    @Test
    fun startingOnACheckoutOnlyDate_isRefusedWithTheReason() {
        val constraints = KalendarBookingConstraints(checkoutOnlyDates = setOf(august(12)))
        val result = KalendarBookingSelection.Empty.afterClick(constraints, august(12))
        assertEquals(KalendarBookingSelection.Empty, result.selection)
        assertEquals(KalendarBookingViolation.CheckoutOnlyStart(august(12)), result.violation)
    }

    @Test
    fun startingOnABlackoutDate_isRefusedWithTheReason() {
        val constraints = KalendarBookingConstraints(blackoutRanges = listOf(august(12)..august(12)))
        val result = KalendarBookingSelection.Empty.afterClick(constraints, august(12))
        assertEquals(KalendarBookingSelection.Empty, result.selection)
        assertEquals(KalendarBookingViolation.Blackout(august(12)), result.violation)
    }

    @Test
    fun anInvalidCompletion_keepsThePendingCheckInAndReports() {
        val constraints = KalendarBookingConstraints(blackoutRanges = listOf(august(14)..august(14)))
        val pending = KalendarBookingSelection.Empty.afterClick(constraints, august(12)).selection
        val result = pending.afterClick(constraints, august(16))
        assertEquals(pending, result.selection)
        assertEquals(KalendarBookingViolation.Blackout(august(14)), result.violation)
    }

    @Test
    fun aTooShortCompletion_keepsThePendingCheckInAndReportsTheBound() {
        val constraints = KalendarBookingConstraints(minNights = 3)
        val pending = KalendarBookingSelection.Empty.afterClick(constraints, august(12)).selection
        val result = pending.afterClick(constraints, august(13))
        assertEquals(pending, result.selection)
        assertEquals(
            KalendarBookingViolation.StayTooShort(nights = 1, minNights = 3),
            result.violation,
        )
    }

    @Test
    fun tappingAfterACompleteStay_startsAFreshOne() {
        val constraints = KalendarBookingConstraints()
        val complete = KalendarBookingSelection(checkIn = august(12), checkOut = august(15))
        val result = complete.afterClick(constraints, august(20))
        assertEquals(august(20), result.selection.checkIn)
        assertNull(result.selection.checkOut)
        assertNull(result.violation)
    }

    @Test
    fun aRefusedTapAfterACompleteStay_keepsTheStay() {
        val constraints = KalendarBookingConstraints(blackoutRanges = listOf(august(20)..august(20)))
        val complete = KalendarBookingSelection(checkIn = august(12), checkOut = august(15))
        val result = complete.afterClick(constraints, august(20))
        assertEquals(complete, result.selection)
        assertIs<KalendarBookingViolation.Blackout>(result.violation)
    }

    @Test
    fun movingThePendingCheckInToARefusedEarlierDate_keepsThePendingOne() {
        val constraints = KalendarBookingConstraints(checkoutOnlyDates = setOf(august(8)))
        val pending = KalendarBookingSelection.Empty.afterClick(constraints, august(12)).selection
        val result = pending.afterClick(constraints, august(8))
        assertEquals(pending, result.selection)
        assertEquals(KalendarBookingViolation.CheckoutOnlyStart(august(8)), result.violation)
    }

    /**
     * The property the whole reduction exists to hold: no click sequence, valid or hostile, can
     * drive the selection into a state the constraints forbid.
     *
     * Several seeded constraint sets, several hundred random taps each, and after every single tap:
     * no blackout date at or between the endpoints, no checkout-only check-in, no checkout-only
     * date strictly inside, and a completed stay always within the night bounds. A violation being
     * reported is fine; an invariant-breaking *selection* is the bug.
     */
    @Test
    fun randomClickSequences_neverProduceAForbiddenSelection() {
        val random = Random(seed = 20260831)
        val start = LocalDate(2026, 8, 1)
        val daySpan = 120
        val clicksPerRun = 250

        repeat(20) { run ->
            val constraints = randomConstraints(random = random, start = start, daySpan = daySpan)
            var selection = KalendarBookingSelection.Empty
            repeat(clicksPerRun) { click ->
                val date = start.plusDays(random.nextInt(daySpan))
                selection = selection.afterClick(constraints, date).selection
                assertSelectionHonours(
                    constraints = constraints,
                    selection = selection,
                    context = "run $run click $click on $date",
                )
            }
        }
    }

    private fun randomConstraints(random: Random, start: LocalDate, daySpan: Int): KalendarBookingConstraints {
        val blackoutRanges = List(random.nextInt(4)) {
            val first = start.plusDays(random.nextInt(daySpan))
            first..first.plusDays(random.nextInt(5))
        }
        val checkoutOnlyDates = buildSet {
            repeat(random.nextInt(6)) { add(start.plusDays(random.nextInt(daySpan))) }
        }
        val minNights = 1 + random.nextInt(4)
        val maxNights = if (random.nextBoolean()) minNights + random.nextInt(10) else null
        return KalendarBookingConstraints(
            blackoutRanges = blackoutRanges,
            checkoutOnlyDates = checkoutOnlyDates,
            minNights = minNights,
            maxNights = maxNights,
        )
    }

    private fun assertSelectionHonours(
        constraints: KalendarBookingConstraints,
        selection: KalendarBookingSelection,
        context: String,
    ) {
        val checkIn = selection.checkIn ?: return
        assertTrue(!constraints.isBlackout(checkIn), "$context: check-in $checkIn is blackout")
        assertTrue(!constraints.isCheckoutOnly(checkIn), "$context: check-in $checkIn is checkout-only")

        val stay = selection.stay ?: return
        var date = stay.checkIn
        while (date <= stay.checkOut) {
            assertTrue(!constraints.isBlackout(date), "$context: stay contains blackout $date")
            if (date > stay.checkIn && date < stay.checkOut) {
                assertTrue(
                    !constraints.isCheckoutOnly(date),
                    "$context: stay crosses checkout-only $date",
                )
            }
            date = date.plusDays(1)
        }
        assertTrue(
            stay.nights >= constraints.minNights,
            "$context: ${stay.nights} nights under minimum ${constraints.minNights}",
        )
        val maxNights = constraints.maxNights
        if (maxNights != null) {
            assertTrue(
                stay.nights <= maxNights,
                "$context: ${stay.nights} nights over maximum $maxNights",
            )
        }
    }
}

private fun LocalDate.plusDays(days: Int): LocalDate = LocalDate.fromEpochDays(toEpochDays() + days)
