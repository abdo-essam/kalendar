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
import kotlinx.datetime.LocalDate

/**
 * A booking picker's selection — a check-in, then maybe a check-out — and the tap reduction that
 * moves between them without ever committing a range the constraints forbid.
 *
 * Like [KalendarSelection], every operation returns a new instance, so this can live in whatever
 * state container a caller already has. Unlike it, [afterClick] answers with a
 * [KalendarBookingClickResult] rather than a bare selection, because a refused tap has to say *why*
 * — the violation is what an app toasts and a picker announces.
 *
 * What a tap does, given [KalendarBookingConstraints]:
 * - **Nothing selected, or a complete stay selected**: the tap tries to open a fresh stay at that
 *   date. A date that cannot begin one ([KalendarBookingConstraints.checkInViolation]) leaves the
 *   selection as it was and reports the reason.
 * - **A check-in is pending**: tapping it again clears the selection; tapping an *earlier* date
 *   moves the pending check-in there (subject to the same check-in rules); tapping a later date
 *   proposes the stay via [KalendarBookingConstraints.proposeStay] — completing it when valid,
 *   otherwise keeping the pending check-in and reporting the violation.
 *
 * Whatever the click sequence, a selection reduced only through [afterClick] therefore never
 * includes a blackout date, never starts on a checkout-only date, and never completes outside the
 * night bounds.
 *
 * ```kotlin
 * var selection = KalendarBookingSelection.Empty
 * val result = selection.afterClick(constraints, tappedDate)
 * selection = result.selection
 * result.violation?.let(::toast)
 * ```
 *
 * @property checkIn The stay's first day, or `null` when nothing is selected. Non-null with a null
 *   [checkOut] means a range is half-built and the next valid later tap completes it.
 * @property checkOut The stay's last day, or `null` while the stay is incomplete. Never non-null
 *   without [checkIn], and always after it.
 */
@Immutable
public class KalendarBookingSelection(
    public val checkIn: LocalDate? = null,
    public val checkOut: LocalDate? = null,
) {

    init {
        require(checkOut == null || checkIn != null) {
            "checkOut ($checkOut) without a checkIn is not a selection state a tap can reach"
        }
        require(checkOut == null || checkIn == null || checkOut > checkIn) {
            "checkOut ($checkOut) must be after checkIn ($checkIn)"
        }
    }

    /**
     * The selected stay, or `null` until both ends are chosen.
     *
     * ```kotlin
     * KalendarBookingSelection(checkIn = august12, checkOut = august15).stay?.nights // 3
     * ```
     */
    public val stay: KalendarStay? = if (checkIn != null && checkOut != null) {
        KalendarStay(checkIn = checkIn, checkOut = checkOut)
    } else {
        null
    }

    /**
     * The selection after [date] is tapped under [constraints]: the reduced selection plus the
     * violation explaining a refused or partially-refused tap, `null` when nothing was refused.
     *
     * ```kotlin
     * val result = KalendarBookingSelection.Empty
     *     .afterClick(constraints, LocalDate(2026, 8, 12)).selection
     *     .afterClick(constraints, LocalDate(2026, 8, 15))
     * result.selection.stay?.nights // 3, when nothing in between is blocked
     * ```
     */
    public fun afterClick(
        constraints: KalendarBookingConstraints,
        date: LocalDate,
    ): KalendarBookingClickResult {
        val pendingCheckIn = checkIn
        if (pendingCheckIn == null || checkOut != null) {
            return freshCheckInAt(constraints = constraints, date = date)
        }
        if (date == pendingCheckIn) {
            return KalendarBookingClickResult(selection = Empty, violation = null)
        }
        if (date < pendingCheckIn) {
            return freshCheckInAt(constraints = constraints, date = date)
        }
        return when (val proposal = constraints.proposeStay(anchor = pendingCheckIn, candidate = date)) {
            is KalendarStayProposal.Valid -> KalendarBookingClickResult(
                selection = KalendarBookingSelection(
                    checkIn = proposal.checkIn,
                    checkOut = proposal.checkOut,
                ),
                violation = null,
            )

            is KalendarStayProposal.Invalid -> KalendarBookingClickResult(
                selection = this,
                violation = proposal.violation,
            )
        }
    }

    /**
     * Opens a stay at [date] when the constraints let one begin there; otherwise keeps this
     * selection — a completed stay is not thrown away for a tap that selected nothing — and
     * reports why.
     */
    private fun freshCheckInAt(
        constraints: KalendarBookingConstraints,
        date: LocalDate,
    ): KalendarBookingClickResult {
        val violation = constraints.checkInViolation(date)
        return if (violation == null) {
            KalendarBookingClickResult(selection = KalendarBookingSelection(checkIn = date), violation = null)
        } else {
            KalendarBookingClickResult(selection = this, violation = violation)
        }
    }

    /** An empty selection with no stay in progress. */
    public fun cleared(): KalendarBookingSelection = Empty

    /** Returns a duplicate of this selection with only the values passed here replaced. */
    public fun copy(
        checkIn: LocalDate? = this.checkIn,
        checkOut: LocalDate? = this.checkOut,
    ): KalendarBookingSelection = KalendarBookingSelection(checkIn = checkIn, checkOut = checkOut)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is KalendarBookingSelection) return false
        if (checkIn != other.checkIn) return false
        if (checkOut != other.checkOut) return false
        return true
    }

    override fun hashCode(): Int = 31 * checkIn.hashCode() + checkOut.hashCode()

    override fun toString(): String = "KalendarBookingSelection(checkIn=$checkIn, checkOut=$checkOut)"

    public companion object {

        /** Nothing selected and no stay in progress — where a booking picker starts. */
        public val Empty: KalendarBookingSelection = KalendarBookingSelection()
    }
}

/**
 * What one tap on a booking picker produced: the [selection] to keep, and the [violation] to
 * explain — either may be all a tap achieved.
 *
 * A valid tap moves [selection] and leaves [violation] `null`; a refused one leaves [selection]
 * exactly as it was and says why. There is no combination where both are `null`-ish surprises: the
 * selection is always the current truth, the violation always the current complaint.
 *
 * ```kotlin
 * val result = selection.afterClick(constraints, date)
 * selection = result.selection
 * result.violation?.let { showSnackbar(messageFor(it)) }
 * ```
 *
 * @property selection The selection after the tap — possibly the same instance, when the tap was
 *   refused outright.
 * @property violation Why the tap could not do what it tried, or `null` when it succeeded.
 */
@Immutable
public class KalendarBookingClickResult(
    public val selection: KalendarBookingSelection,
    public val violation: KalendarBookingViolation?,
) {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is KalendarBookingClickResult) return false
        if (selection != other.selection) return false
        if (violation != other.violation) return false
        return true
    }

    override fun hashCode(): Int = 31 * selection.hashCode() + violation.hashCode()

    override fun toString(): String =
        "KalendarBookingClickResult(selection=$selection, violation=$violation)"
}
