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
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class KalendarBookingConstraintsTest {

    private val august = { day: Int -> LocalDate(2026, 8, day) }

    @Test
    fun nightsBetween_countsCheckoutExclusive() {
        assertEquals(3, nightsBetween(checkIn = august(12), checkOut = august(15)))
        assertEquals(0, nightsBetween(checkIn = august(12), checkOut = august(12)))
        assertEquals(-3, nightsBetween(checkIn = august(15), checkOut = august(12)))
    }

    @Test
    fun isBlackout_isTrueExactlyInsideTheRanges() {
        val constraints = KalendarBookingConstraints(
            blackoutRanges = listOf(august(18)..august(20), august(25)..august(25)),
        )
        assertTrue(!constraints.isBlackout(august(17)))
        assertTrue(constraints.isBlackout(august(18)))
        assertTrue(constraints.isBlackout(august(19)))
        assertTrue(constraints.isBlackout(august(20)))
        assertTrue(!constraints.isBlackout(august(21)))
        assertTrue(constraints.isBlackout(august(25)))
    }

    @Test
    fun isCheckoutOnly_reflectsTheSet() {
        val constraints = KalendarBookingConstraints(checkoutOnlyDates = setOf(august(18)))
        assertTrue(constraints.isCheckoutOnly(august(18)))
        assertTrue(!constraints.isCheckoutOnly(august(19)))
    }

    @Test
    fun checkInViolation_refusesBlackoutAndCheckoutOnlyStarts() {
        val constraints = KalendarBookingConstraints(
            blackoutRanges = listOf(august(18)..august(20)),
            checkoutOnlyDates = setOf(august(10)),
        )
        assertEquals(
            KalendarBookingViolation.Blackout(august(19)),
            constraints.checkInViolation(august(19)),
        )
        assertEquals(
            KalendarBookingViolation.CheckoutOnlyStart(august(10)),
            constraints.checkInViolation(august(10)),
        )
        assertNull(constraints.checkInViolation(august(12)))
    }

    @Test
    fun checkInViolation_blackoutWinsWhenADateIsBoth() {
        val constraints = KalendarBookingConstraints(
            blackoutRanges = listOf(august(18)..august(18)),
            checkoutOnlyDates = setOf(august(18)),
        )
        assertIs<KalendarBookingViolation.Blackout>(constraints.checkInViolation(august(18)))
    }

    @Test
    fun proposeStay_acceptsAValidSpanInEitherTapOrder() {
        val constraints = KalendarBookingConstraints()
        val forward = constraints.proposeStay(anchor = august(12), candidate = august(15))
        val backward = constraints.proposeStay(anchor = august(15), candidate = august(12))

        val expected = KalendarStay(checkIn = august(12), checkOut = august(15))
        assertEquals(KalendarStayProposal.Valid(expected), forward)
        assertEquals(KalendarStayProposal.Valid(expected), backward)
        assertEquals(3, (forward as KalendarStayProposal.Valid).stay.nights)
    }

    @Test
    fun proposeStay_refusesASpanCrossingABlackout_withTheEarliestBlockedDate() {
        val constraints = KalendarBookingConstraints(
            blackoutRanges = listOf(august(22)..august(23), august(18)..august(20)),
        )
        val proposal = constraints.proposeStay(anchor = august(15), candidate = august(24))
        assertIs<KalendarStayProposal.Invalid>(proposal)
        assertEquals(KalendarBookingViolation.Blackout(august(18)), proposal.violation)
        assertEquals(august(15), proposal.checkIn)
        assertEquals(august(24), proposal.checkOut)
    }

    @Test
    fun proposeStay_refusesEndingOnABlackoutDate() {
        // Blackout means the date cannot be in the range at all — unlike a checkout-only date, it
        // may not even be the checkout.
        val constraints = KalendarBookingConstraints(blackoutRanges = listOf(august(18)..august(20)))
        val proposal = constraints.proposeStay(anchor = august(15), candidate = august(18))
        assertIs<KalendarStayProposal.Invalid>(proposal)
        assertEquals(KalendarBookingViolation.Blackout(august(18)), proposal.violation)
    }

    @Test
    fun proposeStay_refusesStartingOnABlackoutDate() {
        val constraints = KalendarBookingConstraints(blackoutRanges = listOf(august(18)..august(20)))
        val proposal = constraints.proposeStay(anchor = august(20), candidate = august(24))
        assertIs<KalendarStayProposal.Invalid>(proposal)
        assertEquals(KalendarBookingViolation.Blackout(august(20)), proposal.violation)
    }

    @Test
    fun proposeStay_allowsACheckoutOnlyDateToEndTheStay() {
        val constraints = KalendarBookingConstraints(checkoutOnlyDates = setOf(august(18)))
        val proposal = constraints.proposeStay(anchor = august(15), candidate = august(18))
        assertIs<KalendarStayProposal.Valid>(proposal)
        assertEquals(august(18), proposal.stay.checkOut)
    }

    @Test
    fun proposeStay_refusesACheckoutOnlyDateStartingTheStay() {
        val constraints = KalendarBookingConstraints(checkoutOnlyDates = setOf(august(18)))
        val proposal = constraints.proposeStay(anchor = august(18), candidate = august(21))
        assertIs<KalendarStayProposal.Invalid>(proposal)
        assertEquals(KalendarBookingViolation.CheckoutOnlyStart(august(18)), proposal.violation)
    }

    @Test
    fun proposeStay_refusesACheckoutOnlyDateStrictlyInside() {
        val constraints = KalendarBookingConstraints(checkoutOnlyDates = setOf(august(17), august(16)))
        val proposal = constraints.proposeStay(anchor = august(15), candidate = august(21))
        assertIs<KalendarStayProposal.Invalid>(proposal)
        assertEquals(KalendarBookingViolation.CheckoutOnlyInterior(august(16)), proposal.violation)
    }

    @Test
    fun proposeStay_enforcesMinNightsInclusively() {
        val constraints = KalendarBookingConstraints(minNights = 3)
        assertIs<KalendarStayProposal.Valid>(
            constraints.proposeStay(anchor = august(12), candidate = august(15)),
        )
        val tooShort = constraints.proposeStay(anchor = august(12), candidate = august(14))
        assertIs<KalendarStayProposal.Invalid>(tooShort)
        assertEquals(
            KalendarBookingViolation.StayTooShort(nights = 2, minNights = 3),
            tooShort.violation,
        )
    }

    @Test
    fun proposeStay_enforcesMaxNightsInclusively() {
        val constraints = KalendarBookingConstraints(maxNights = 3)
        assertIs<KalendarStayProposal.Valid>(
            constraints.proposeStay(anchor = august(12), candidate = august(15)),
        )
        val tooLong = constraints.proposeStay(anchor = august(12), candidate = august(16))
        assertIs<KalendarStayProposal.Invalid>(tooLong)
        assertEquals(
            KalendarBookingViolation.StayTooLong(nights = 4, maxNights = 3),
            tooLong.violation,
        )
    }

    @Test
    fun proposeStay_treatsTappingTheAnchorAsAZeroNightStay() {
        val proposal = KalendarBookingConstraints().proposeStay(anchor = august(12), candidate = august(12))
        assertIs<KalendarStayProposal.Invalid>(proposal)
        assertEquals(
            KalendarBookingViolation.StayTooShort(nights = 0, minNights = 1),
            proposal.violation,
        )
    }

    @Test
    fun proposeStay_reportsBlackoutBeforeNightBounds() {
        // A span both too short and crossing a blackout: the blackout is the structural problem and
        // is documented to win, so a caller's toast wording can rely on the order.
        val constraints = KalendarBookingConstraints(
            blackoutRanges = listOf(august(13)..august(13)),
            minNights = 5,
        )
        val proposal = constraints.proposeStay(anchor = august(12), candidate = august(14))
        assertIs<KalendarStayProposal.Invalid>(proposal)
        assertEquals(KalendarBookingViolation.Blackout(august(13)), proposal.violation)
    }

    @Test
    fun stayViolation_acceptsAStayBetweenTwoBlackoutRuns() {
        val constraints = KalendarBookingConstraints(
            blackoutRanges = listOf(august(10)..august(11), august(20)..august(21)),
        )
        assertNull(constraints.stayViolation(checkIn = august(12), checkOut = august(19)))
    }

    @Test
    fun stayViolation_refusesAReversedPairOutright() {
        assertFailsWith<IllegalArgumentException> {
            KalendarBookingConstraints().stayViolation(checkIn = august(15), checkOut = august(12))
        }
    }

    @Test
    fun invalidConstraints_areRefusedAtConstruction() {
        assertFailsWith<IllegalArgumentException> { KalendarBookingConstraints(minNights = 0) }
        assertFailsWith<IllegalArgumentException> {
            KalendarBookingConstraints(minNights = 3, maxNights = 2)
        }
        assertFailsWith<IllegalArgumentException> {
            KalendarBookingConstraints(blackoutRanges = listOf(august(15)..august(12)))
        }
    }

    @Test
    fun stay_exposesNightsAndInclusiveDates() {
        val stay = KalendarStay(checkIn = august(12), checkOut = august(15))
        assertEquals(3, stay.nights)
        assertEquals(setOf(august(12), august(13), august(14), august(15)), stay.dates())
        assertFailsWith<IllegalArgumentException> {
            KalendarStay(checkIn = august(12), checkOut = august(12))
        }
    }
}
