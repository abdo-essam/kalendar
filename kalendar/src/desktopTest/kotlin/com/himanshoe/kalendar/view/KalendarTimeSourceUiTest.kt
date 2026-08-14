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

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import org.junit.Rule
import kotlin.test.Test
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * The two things a hoisted time source is for: a calendar whose "today" a test can pin, and a
 * calendar whose "today" moves on its own when the day does.
 *
 * Both were impossible before — every view read `Clock.System` from inside an unkeyed `remember`,
 * so "today" was whatever the machine said at first composition and stayed that way until the
 * composable left the tree.
 */
class KalendarTimeSourceUiTest {

    @get:Rule
    val rule = createComposeRule()

    /** A clock a test can wind forward, standing in for the passage of real time. */
    private class MutableClock(var instant: Instant) : Clock {
        override fun now(): Instant = instant
    }

    private fun instantAt(dateTime: LocalDateTime): Instant = dateTime.toInstant(ZONE)

    @Test
    fun fixedClock_makesTodayDeterministic() {
        val timeSource = KalendarTimeSource(
            timeZone = ZONE,
            clock = MutableClock(instantAt(LocalDateTime(2026, 3, 10, 9, 0))),
        )
        rule.setContent {
            ProvideKalendarTimeSource(timeSource) {
                KalendarMonth(selectedDate = MARCH_FIRST)
            }
        }

        rule.onNodeWithContentDescription("March 10, 2026, today").assertExists()
        rule.onNodeWithContentDescription("March 11, 2026, today").assertDoesNotExist()
    }

    @Test
    fun clockCrossingMidnight_movesTheTodayHighlightToTheNewDay() {
        val clock = MutableClock(instantAt(LocalDateTime(2026, 3, 10, 23, 59)))
        val timeSource = KalendarTimeSource(timeZone = ZONE, clock = clock)
        rule.setContent {
            ProvideKalendarTimeSource(timeSource) {
                KalendarMonth(selectedDate = MARCH_FIRST)
            }
        }
        rule.onNodeWithContentDescription("March 10, 2026, today").assertExists()

        clock.instant = instantAt(LocalDateTime(2026, 3, 11, 0, 1))
        rule.mainClock.advanceTimeBy(TWO_MINUTES_MS)
        rule.waitForIdle()

        // Regression guard for the bug this seam removes: a calendar left open overnight used to
        // keep highlighting yesterday, because "today" was captured in an unkeyed remember.
        rule.onNodeWithContentDescription("March 11, 2026, today").assertExists()
        rule.onNodeWithContentDescription("March 10, 2026, today").assertDoesNotExist()
    }

    private companion object {
        val ZONE = TimeZone.UTC
        val MARCH_FIRST = LocalDate(2026, 3, 1)
        const val TWO_MINUTES_MS = 120_000L
    }
}
