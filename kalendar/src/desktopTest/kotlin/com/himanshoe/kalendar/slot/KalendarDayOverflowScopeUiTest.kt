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

package com.himanshoe.kalendar.slot

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.himanshoe.kalendar.component.KalendarDayCellDefaults
import com.himanshoe.kalendar.foundation.event.KalendarEvent
import com.himanshoe.kalendar.theme.KalendarAnimations
import com.himanshoe.kalendar.theme.KalendarTheme
import kotlinx.datetime.LocalDate
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

/**
 * That a popover reports the *caller's* event type back, and that one popover can still be written
 * for calendars of every event type.
 *
 * [KalendarDayOverflowScope] is invariant in its event type, which is the only shape under which
 * [KalendarDayOverflowScope.onEventClick] is sound. Declared `out E` it typechecked and then failed
 * at runtime: a popover holding the scope as `KalendarDayOverflowScope<KalendarEvent>` — a
 * conversion covariance permits — could pass it a `BasicKalendarEvent`, and the cell's own
 * `onEventClick`, which is a `(Booking) -> Unit`, received it and threw `ClassCastException` on the
 * synthetic cast at its entry.
 *
 * Invariance costs a shared popover the ability to be a single monomorphic value. What replaces it
 * is a generic composable, which these tests are the guard for: [Booking] here is a domain type the
 * library has never heard of, and both the built-in popover and a hand-written one have to hand it
 * back with [Booking.roomId] intact.
 */
class KalendarDayOverflowScopeUiTest {

    @get:Rule
    val rule = createComposeRule()

    @Suppress("MagicNumber")
    private val date = LocalDate(2026, 3, 12)

    /** A consumer's own event type — the case `out E` existed to serve and then mishandled. */
    private class Booking(
        override val date: LocalDate,
        override val eventName: String,
        val roomId: String,
    ) : KalendarEvent {
        override val eventDescription: String? = null
    }

    private val bookings = listOf(
        Booking(date = date, eventName = "Alpha", roomId = "room-a"),
        Booking(date = date, eventName = "Bravo", roomId = "room-b"),
        Booking(date = date, eventName = "Charlie", roomId = "room-c"),
        Booking(date = date, eventName = "Delta", roomId = "room-d"),
    )

    private fun scope() = KalendarDayScope(
        date = date,
        isSelected = false,
        isToday = false,
        isDisabled = false,
        events = bookings,
    )

    /**
     * A popover written once for every event type, the way invariance requires: generic in [E]
     * rather than pinned to `KalendarEvent`, so [KalendarDayOverflowScope.onEventClick] can only be
     * reached with an event of the calendar's own type.
     */
    @Composable
    private fun <E : KalendarEvent> SharedPopup(scope: KalendarDayOverflowScope<E>) {
        Column {
            scope.events.forEach { event ->
                BasicText(
                    text = event.eventName,
                    modifier = Modifier.clickable { scope.onEventClick(event) },
                )
            }
        }
    }

    @Test
    fun builtInPopup_rowTapped_reportsTheCallersOwnEventType() {
        var clicked: Booking? = null
        rule.setContent {
            KalendarTheme(animations = KalendarAnimations(enabled = false)) {
                KalendarDayCellDefaults.Cell(
                    scope = scope(),
                    onClick = {},
                    onEventClick = { booking -> clicked = booking },
                )
            }
        }

        rule.onNodeWithContentDescription(OVERFLOW_TRIGGER_DESCRIPTION).performClick()
        rule.onNodeWithText("Charlie").performClick()

        assertEquals("room-c", clicked?.roomId, "the built-in popover reports a Booking, not a KalendarEvent")
    }

    @Test
    fun genericPopup_servesACalendarOfAnyEventTypeAndReportsThatType() {
        var clicked: Booking? = null
        rule.setContent {
            KalendarTheme(animations = KalendarAnimations(enabled = false)) {
                KalendarDayCellDefaults.Cell(
                    scope = scope(),
                    onClick = {},
                    onEventClick = { booking -> clicked = booking },
                    overflowPopup = { overflowScope -> SharedPopup(scope = overflowScope) },
                )
            }
        }

        rule.onNodeWithContentDescription(OVERFLOW_TRIGGER_DESCRIPTION).performClick()
        rule.onNodeWithText("Bravo").performClick()

        assertEquals("room-b", clicked?.roomId, "a popover written once still reaches the caller's type")
    }

    private companion object {
        const val OVERFLOW_TRIGGER_DESCRIPTION = "Show all events"
    }
}
