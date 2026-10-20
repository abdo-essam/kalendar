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

/** The shortest stay [KalendarBookingConstraints] permits by default: one night. */
public const val DEFAULT_MIN_NIGHTS: Int = 1

/**
 * Nights spent in a stay running from [checkIn] to [checkOut] — the checkout day exclusive, because
 * a guest leaving on the 15th does not sleep there on the night of the 15th. This is how every
 * booking domain counts a range's length, and it is the unit
 * [KalendarBookingConstraints.minNights]/[KalendarBookingConstraints.maxNights] clamp.
 *
 * Negative when [checkOut] precedes [checkIn], zero when they are the same day.
 *
 * ```kotlin
 * nightsBetween(checkIn = LocalDate(2026, 8, 12), checkOut = LocalDate(2026, 8, 15)) // 3
 * ```
 */
public fun nightsBetween(checkIn: LocalDate, checkOut: LocalDate): Int =
    (checkOut.toEpochDays() - checkIn.toEpochDays()).toInt()

/**
 * The availability rules of a booking calendar — which dates cannot be part of a stay, which can
 * only end one, and how many nights a stay must and may span.
 *
 * This is the pure half of a date range picker: no UI, no state, just the answer to "is this range
 * bookable, and if not, why not". [KalendarBookingSelection] reduces taps against it, and the
 * `KalendarRangePicker` composable consumes both; drive your own UI from exactly these calls to get
 * the same behaviour.
 *
 * ```kotlin
 * val constraints = KalendarBookingConstraints(
 *     blackoutRanges = listOf(LocalDate(2026, 8, 18)..LocalDate(2026, 8, 20)),
 *     checkoutOnlyDates = setOf(LocalDate(2026, 8, 18)),
 *     minNights = 2,
 * )
 * constraints.proposeStay(anchor = LocalDate(2026, 8, 12), candidate = LocalDate(2026, 8, 15))
 * // KalendarStayProposal.Valid, three nights
 * ```
 *
 * @property blackoutRanges Closed date ranges that cannot appear anywhere in a stay — not as an
 *   endpoint and not between them. The property is shut on those dates: maintenance, an owner
 *   block, an existing booking whose turnover day the guest cannot use either. A single date is the
 *   one-day range `date..date`. Ranges may overlap; none may be empty.
 * @property checkoutOnlyDates Dates that may **end** a stay but not begin one or fall strictly
 *   inside one — the turnover day of an existing booking, whose night is taken but whose morning is
 *   free for the previous guest to leave. A date in both this set and a blackout range is treated
 *   as blackout, the stricter rule.
 * @property minNights The fewest nights a stay may span, counted with [nightsBetween] (checkout day
 *   exclusive). At least 1: a zero-night "stay" is the same day twice, which is a single-date
 *   picker's job, not this one's. Defaults to [DEFAULT_MIN_NIGHTS].
 * @property maxNights The most nights a stay may span, or `null` for no ceiling. When set, it must
 *   be at least [minNights]. Defaults to `null`.
 */
@Immutable
public class KalendarBookingConstraints(
    public val blackoutRanges: List<ClosedRange<LocalDate>> = emptyList(),
    public val checkoutOnlyDates: Set<LocalDate> = emptySet(),
    public val minNights: Int = DEFAULT_MIN_NIGHTS,
    public val maxNights: Int? = null,
) {

    init {
        require(minNights >= DEFAULT_MIN_NIGHTS) {
            "minNights must be at least 1, was $minNights; a zero-night stay is a single date, not a range"
        }
        require(maxNights == null || maxNights >= minNights) {
            "maxNights ($maxNights) must be at least minNights ($minNights)"
        }
        blackoutRanges.forEach { range ->
            require(!range.isEmpty()) {
                "Blackout range ${range.start}..${range.endInclusive} is empty; " +
                    "a single blocked date is the range date..date"
            }
        }
    }

    /**
     * Whether [date] falls inside any of [blackoutRanges], and so can appear nowhere in a stay.
     *
     * ```kotlin
     * val constraints = KalendarBookingConstraints(
     *     blackoutRanges = listOf(LocalDate(2026, 8, 18)..LocalDate(2026, 8, 20)),
     * )
     * constraints.isBlackout(LocalDate(2026, 8, 19)) // true
     * ```
     */
    public fun isBlackout(date: LocalDate): Boolean = blackoutRanges.any { range -> date in range }

    /**
     * Whether [date] is in [checkoutOnlyDates], and so may end a stay but not begin one or fall
     * strictly inside one.
     *
     * ```kotlin
     * val constraints = KalendarBookingConstraints(checkoutOnlyDates = setOf(LocalDate(2026, 8, 18)))
     * constraints.isCheckoutOnly(LocalDate(2026, 8, 18)) // true
     * ```
     */
    public fun isCheckoutOnly(date: LocalDate): Boolean = date in checkoutOnlyDates

    /**
     * Why [date] cannot **begin** a stay, or `null` when it can — the check a picker runs on the
     * first tap of a range, before any candidate checkout exists.
     *
     * A blackout date answers [KalendarBookingViolation.Blackout]; a checkout-only date answers
     * [KalendarBookingViolation.CheckoutOnlyStart]; blackout wins when a date is both.
     *
     * ```kotlin
     * val constraints = KalendarBookingConstraints(checkoutOnlyDates = setOf(LocalDate(2026, 8, 18)))
     * constraints.checkInViolation(LocalDate(2026, 8, 18))
     * // KalendarBookingViolation.CheckoutOnlyStart(LocalDate(2026, 8, 18))
     * ```
     */
    public fun checkInViolation(date: LocalDate): KalendarBookingViolation? = when {
        isBlackout(date) -> KalendarBookingViolation.Blackout(date)
        isCheckoutOnly(date) -> KalendarBookingViolation.CheckoutOnlyStart(date)
        else -> null
    }

    /**
     * Why the stay from [checkIn] to [checkOut] is not bookable, or `null` when it is.
     *
     * [checkIn] must not be after [checkOut]; use [proposeStay] for a pair of taps whose order is
     * not known. The checks run in a fixed order, so the violation reported for a range that breaks
     * several rules is deterministic:
     *
     * 1. [KalendarBookingViolation.Blackout] — the earliest blackout date in `checkIn..checkOut`,
     *    endpoints included: the stay may not touch a blackout date at all.
     * 2. [KalendarBookingViolation.CheckoutOnlyStart] — [checkIn] is checkout-only.
     * 3. [KalendarBookingViolation.CheckoutOnlyInterior] — the earliest checkout-only date strictly
     *    between the endpoints. [checkOut] itself being checkout-only is exactly what such a date
     *    permits, so it violates nothing.
     * 4. [KalendarBookingViolation.StayTooShort] / [KalendarBookingViolation.StayTooLong] — the
     *    night count against [minNights] and [maxNights].
     *
     * ```kotlin
     * val constraints = KalendarBookingConstraints(minNights = 2)
     * constraints.stayViolation(checkIn = LocalDate(2026, 8, 12), checkOut = LocalDate(2026, 8, 13))
     * // KalendarBookingViolation.StayTooShort(nights = 1, minNights = 2)
     * ```
     */
    @Suppress("ReturnCount")
    public fun stayViolation(checkIn: LocalDate, checkOut: LocalDate): KalendarBookingViolation? {
        require(checkIn <= checkOut) {
            "checkIn ($checkIn) must not be after checkOut ($checkOut); use proposeStay for unordered taps"
        }
        earliestBlackoutWithin(start = checkIn, end = checkOut)?.let { date ->
            return KalendarBookingViolation.Blackout(date)
        }
        if (isCheckoutOnly(checkIn)) {
            return KalendarBookingViolation.CheckoutOnlyStart(checkIn)
        }
        earliestCheckoutOnlyStrictlyInside(checkIn = checkIn, checkOut = checkOut)?.let { date ->
            return KalendarBookingViolation.CheckoutOnlyInterior(date)
        }
        val nights = nightsBetween(checkIn = checkIn, checkOut = checkOut)
        if (nights < minNights) {
            return KalendarBookingViolation.StayTooShort(nights = nights, minNights = minNights)
        }
        val nightCeiling = maxNights
        if (nightCeiling != null && nights > nightCeiling) {
            return KalendarBookingViolation.StayTooLong(nights = nights, maxNights = nightCeiling)
        }
        return null
    }

    /**
     * The earliest blackout date in the closed interval `start..end`, or `null` when the interval
     * clears every range. Walks [blackoutRanges] rather than the interval's days, so a season-long
     * candidate span costs the number of ranges, not the number of nights.
     */
    private fun earliestBlackoutWithin(start: LocalDate, end: LocalDate): LocalDate? {
        var earliest: LocalDate? = null
        for (range in blackoutRanges) {
            if (range.start <= end && range.endInclusive >= start) {
                val firstBlocked = maxOf(range.start, start)
                if (earliest == null || firstBlocked < earliest) {
                    earliest = firstBlocked
                }
            }
        }
        return earliest
    }

    /**
     * The earliest of [checkoutOnlyDates] strictly between the endpoints, or `null` when none is.
     * The endpoints themselves are judged by their own rules: the check-in by
     * [KalendarBookingViolation.CheckoutOnlyStart], the check-out not at all.
     */
    private fun earliestCheckoutOnlyStrictlyInside(checkIn: LocalDate, checkOut: LocalDate): LocalDate? {
        var earliest: LocalDate? = null
        for (date in checkoutOnlyDates) {
            val isInterior = date > checkIn && date < checkOut
            if (isInterior && (earliest == null || date < earliest)) {
                earliest = date
            }
        }
        return earliest
    }

    /**
     * The reduction at the heart of a range picker: given the [anchor] date a range started from
     * and the [candidate] date under the second tap (or the hovering pointer), the valid stay the
     * two describe — or the reason they describe none.
     *
     * Order-independent: the earlier of the two becomes the check-in, so a picker never has to sort
     * its taps before asking. Tapping the anchor itself proposes a zero-night stay, which
     * [minNights] rejects as [KalendarBookingViolation.StayTooShort]. An invalid proposal still
     * carries the normalized endpoints, so a hover preview can show the span being refused.
     *
     * ```kotlin
     * val constraints = KalendarBookingConstraints(
     *     blackoutRanges = listOf(LocalDate(2026, 8, 18)..LocalDate(2026, 8, 20)),
     * )
     * when (val proposal = constraints.proposeStay(anchor = august15, candidate = august22)) {
     *     is KalendarStayProposal.Valid -> book(proposal.stay)
     *     is KalendarStayProposal.Invalid -> toast(proposal.violation) // Blackout(august18)
     * }
     * ```
     */
    public fun proposeStay(anchor: LocalDate, candidate: LocalDate): KalendarStayProposal {
        val checkIn = minOf(anchor, candidate)
        val checkOut = maxOf(anchor, candidate)
        val violation = stayViolation(checkIn = checkIn, checkOut = checkOut)
        return if (violation == null) {
            KalendarStayProposal.Valid(KalendarStay(checkIn = checkIn, checkOut = checkOut))
        } else {
            KalendarStayProposal.Invalid(checkIn = checkIn, checkOut = checkOut, violation = violation)
        }
    }

    /** Returns a duplicate of these constraints with only the rules passed here replaced. */
    public fun copy(
        blackoutRanges: List<ClosedRange<LocalDate>> = this.blackoutRanges,
        checkoutOnlyDates: Set<LocalDate> = this.checkoutOnlyDates,
        minNights: Int = this.minNights,
        maxNights: Int? = this.maxNights,
    ): KalendarBookingConstraints = KalendarBookingConstraints(
        blackoutRanges = blackoutRanges,
        checkoutOnlyDates = checkoutOnlyDates,
        minNights = minNights,
        maxNights = maxNights,
    )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is KalendarBookingConstraints) return false
        if (blackoutRanges != other.blackoutRanges) return false
        if (checkoutOnlyDates != other.checkoutOnlyDates) return false
        if (minNights != other.minNights) return false
        if (maxNights != other.maxNights) return false
        return true
    }

    override fun hashCode(): Int {
        var result = blackoutRanges.hashCode()
        result = 31 * result + checkoutOnlyDates.hashCode()
        result = 31 * result + minNights
        result = 31 * result + (maxNights ?: 0)
        return result
    }

    override fun toString(): String = "KalendarBookingConstraints(" +
        "blackoutRanges=$blackoutRanges, " +
        "checkoutOnlyDates=$checkoutOnlyDates, " +
        "minNights=$minNights, " +
        "maxNights=$maxNights" +
        ")"

    public companion object {

        /** No blackouts, no checkout-only days, any length of stay from one night up. */
        public val Unconstrained: KalendarBookingConstraints = KalendarBookingConstraints()
    }
}

/**
 * A bookable stay: a check-in date and a later check-out date that
 * [KalendarBookingConstraints.proposeStay] accepted, or that a caller asserts is valid.
 *
 * ```kotlin
 * val stay = KalendarStay(checkIn = LocalDate(2026, 8, 12), checkOut = LocalDate(2026, 8, 15))
 * stay.nights // 3
 * ```
 *
 * @property checkIn The first day of the stay — the first night slept.
 * @property checkOut The day the stay ends. Exclusive as a *night* — see [nightsBetween] — but part
 *   of the stay as a *date*: it is the last day the guest is present, so it belongs in [dates] and
 *   a picker highlights it.
 */
@Immutable
public class KalendarStay(
    public val checkIn: LocalDate,
    public val checkOut: LocalDate,
) {

    init {
        require(checkOut > checkIn) {
            "checkOut ($checkOut) must be after checkIn ($checkIn); a stay is at least one night"
        }
    }

    /** How many nights the stay spans — [nightsBetween] the two endpoints. Always at least 1. */
    public val nights: Int = nightsBetween(checkIn = checkIn, checkOut = checkOut)

    /**
     * Every date of the stay, [checkIn] through [checkOut] inclusive — the set to hand a calendar
     * view's `selectedDates`. A function rather than a property because it builds a fresh set of
     * `nights + 1` dates on every call.
     *
     * ```kotlin
     * KalendarStay(checkIn = august12, checkOut = august15).dates() // 12th, 13th, 14th, 15th
     * ```
     */
    public fun dates(): Set<LocalDate> = checkIn.datesUntil(checkOut)

    /** Returns a duplicate of this stay with only the endpoints passed here replaced. */
    public fun copy(
        checkIn: LocalDate = this.checkIn,
        checkOut: LocalDate = this.checkOut,
    ): KalendarStay = KalendarStay(checkIn = checkIn, checkOut = checkOut)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is KalendarStay) return false
        if (checkIn != other.checkIn) return false
        if (checkOut != other.checkOut) return false
        return true
    }

    override fun hashCode(): Int = 31 * checkIn.hashCode() + checkOut.hashCode()

    override fun toString(): String = "KalendarStay(checkIn=$checkIn, checkOut=$checkOut)"
}

/**
 * What [KalendarBookingConstraints.proposeStay] concluded about a pair of dates: a [Valid] stay, or
 * an [Invalid] span carrying the reason — because a picker that can only say "no" cannot explain
 * "minimum 2 nights" or "crosses an unavailable date" to the person tapping.
 *
 * ```kotlin
 * when (val proposal = constraints.proposeStay(anchor = anchor, candidate = tapped)) {
 *     is KalendarStayProposal.Valid -> select(proposal.stay)
 *     is KalendarStayProposal.Invalid -> explain(proposal.violation)
 * }
 * ```
 *
 * @property checkIn The earlier of the two dates proposed, whatever the tap order was.
 * @property checkOut The later of the two dates proposed.
 */
@Immutable
public sealed interface KalendarStayProposal {

    public val checkIn: LocalDate

    public val checkOut: LocalDate

    /**
     * The proposed dates satisfy every constraint; [stay] is the resulting range.
     *
     * @property stay The bookable stay the two dates describe.
     */
    @Immutable
    public class Valid(public val stay: KalendarStay) : KalendarStayProposal {

        override val checkIn: LocalDate get() = stay.checkIn

        override val checkOut: LocalDate get() = stay.checkOut

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Valid) return false
            return stay == other.stay
        }

        override fun hashCode(): Int = stay.hashCode()

        override fun toString(): String = "KalendarStayProposal.Valid(stay=$stay)"
    }

    /**
     * The proposed dates break a constraint. The endpoints are still carried — normalized, earlier
     * first — so a hover preview can show the refused span while announcing [violation].
     *
     * @property violation Why the span was refused, per [KalendarBookingConstraints.stayViolation]'s
     *   check order.
     */
    @Immutable
    public class Invalid(
        override val checkIn: LocalDate,
        override val checkOut: LocalDate,
        public val violation: KalendarBookingViolation,
    ) : KalendarStayProposal {

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Invalid) return false
            if (checkIn != other.checkIn) return false
            if (checkOut != other.checkOut) return false
            if (violation != other.violation) return false
            return true
        }

        override fun hashCode(): Int {
            var result = checkIn.hashCode()
            result = 31 * result + checkOut.hashCode()
            result = 31 * result + violation.hashCode()
            return result
        }

        override fun toString(): String =
            "KalendarStayProposal.Invalid(checkIn=$checkIn, checkOut=$checkOut, violation=$violation)"
    }
}

/**
 * A reason a proposed stay was refused — the machine-readable form of "minimum 2 nights" or
 * "crosses an unavailable date", so an app can toast, announce, or draw whichever wording it wants.
 *
 * Each variant carries the facts the sentence needs: the offending date, or the night counts on
 * both sides of the comparison.
 *
 * ```kotlin
 * val message = when (violation) {
 *     is KalendarBookingViolation.Blackout -> "Includes an unavailable date"
 *     is KalendarBookingViolation.CheckoutOnlyStart -> "That day is checkout-only"
 *     is KalendarBookingViolation.CheckoutOnlyInterior -> "Crosses a checkout-only day"
 *     is KalendarBookingViolation.StayTooShort -> "Minimum ${violation.minNights} nights"
 *     is KalendarBookingViolation.StayTooLong -> "Maximum ${violation.maxNights} nights"
 * }
 * ```
 */
@Immutable
public sealed interface KalendarBookingViolation {

    /**
     * The stay would include a blackout date — as an endpoint or between them, either is refused.
     *
     * @property date The earliest blackout date the stay would include.
     */
    @Immutable
    public class Blackout(public val date: LocalDate) : KalendarBookingViolation {

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Blackout) return false
            return date == other.date
        }

        override fun hashCode(): Int = date.hashCode()

        override fun toString(): String = "KalendarBookingViolation.Blackout(date=$date)"
    }

    /**
     * The stay would begin on a checkout-only date, whose night is already taken.
     *
     * @property date The checkout-only date the stay tried to begin on.
     */
    @Immutable
    public class CheckoutOnlyStart(public val date: LocalDate) : KalendarBookingViolation {

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is CheckoutOnlyStart) return false
            return date == other.date
        }

        override fun hashCode(): Int = date.hashCode()

        override fun toString(): String = "KalendarBookingViolation.CheckoutOnlyStart(date=$date)"
    }

    /**
     * The stay would pass over a checkout-only date, which may only ever end one.
     *
     * @property date The earliest checkout-only date strictly inside the stay.
     */
    @Immutable
    public class CheckoutOnlyInterior(public val date: LocalDate) : KalendarBookingViolation {

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is CheckoutOnlyInterior) return false
            return date == other.date
        }

        override fun hashCode(): Int = date.hashCode()

        override fun toString(): String = "KalendarBookingViolation.CheckoutOnlyInterior(date=$date)"
    }

    /**
     * The stay spans fewer nights than the constraints require — the "minimum 2 nights" toast.
     *
     * @property nights How many nights the proposal spanned. Zero when both taps were the same day.
     * @property minNights The floor it fell short of, from [KalendarBookingConstraints.minNights].
     */
    @Immutable
    public class StayTooShort(
        public val nights: Int,
        public val minNights: Int,
    ) : KalendarBookingViolation {

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is StayTooShort) return false
            if (nights != other.nights) return false
            if (minNights != other.minNights) return false
            return true
        }

        override fun hashCode(): Int = 31 * nights + minNights

        override fun toString(): String =
            "KalendarBookingViolation.StayTooShort(nights=$nights, minNights=$minNights)"
    }

    /**
     * The stay spans more nights than the constraints allow.
     *
     * @property nights How many nights the proposal spanned.
     * @property maxNights The ceiling it exceeded, from [KalendarBookingConstraints.maxNights].
     */
    @Immutable
    public class StayTooLong(
        public val nights: Int,
        public val maxNights: Int,
    ) : KalendarBookingViolation {

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is StayTooLong) return false
            if (nights != other.nights) return false
            if (maxNights != other.maxNights) return false
            return true
        }

        override fun hashCode(): Int = 31 * nights + maxNights

        override fun toString(): String =
            "KalendarBookingViolation.StayTooLong(nights=$nights, maxNights=$maxNights)"
    }
}
