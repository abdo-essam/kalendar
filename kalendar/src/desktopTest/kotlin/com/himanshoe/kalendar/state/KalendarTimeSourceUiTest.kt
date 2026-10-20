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

import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.createComposeRule
import com.himanshoe.kalendar.KalendarMonth
import com.himanshoe.kalendar.testing.KalendarTestClock
import com.himanshoe.kalendar.testing.fixedKalendarTimeSource
import com.himanshoe.kalendar.testing.hasKalendarDay
import com.himanshoe.kalendar.testing.isKalendarToday
import com.himanshoe.kalendar.testing.onKalendarDay
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import org.junit.Rule
import kotlin.test.Test
import kotlin.time.Duration.Companion.minutes

/**
 * The two things a hoisted time source is for: a calendar whose "today" a test can pin, and a
 * calendar whose "today" moves on its own when the day does.
 *
 * Both were impossible before — every view read `Clock.System` from inside an unkeyed `remember`,
 * so "today" was whatever the machine said at first composition and stayed that way until the
 * composable left the tree.
 *
 * Written against `kalendar-testing`, which is also this test's point: the pinned clock and the
 * today assertion are exactly what that module hands a consumer.
 */
class KalendarTimeSourceUiTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun fixedClock_makesTodayDeterministic() {
        rule.setContent {
            ProvideKalendarTimeSource(fixedKalendarTimeSource(now = LocalDateTime(2026, 3, 10, 9, 0))) {
                KalendarMonth(selectedDate = MARCH_FIRST)
            }
        }

        rule.onKalendarDay(LocalDate(2026, 3, 10)).assert(isKalendarToday())
        rule.onNode(hasKalendarDay(LocalDate(2026, 3, 11)) and isKalendarToday()).assertDoesNotExist()
    }

    @Test
    fun clockCrossingMidnight_movesTheTodayHighlightToTheNewDay() {
        val clock = KalendarTestClock(start = LocalDateTime(2026, 3, 10, 23, 59))
        rule.setContent {
            ProvideKalendarTimeSource(clock.asTimeSource()) {
                KalendarMonth(selectedDate = MARCH_FIRST)
            }
        }
        rule.onKalendarDay(LocalDate(2026, 3, 10)).assert(isKalendarToday())

        clock.advanceBy(2.minutes)
        rule.mainClock.advanceTimeBy(TWO_MINUTES_MS)
        rule.waitForIdle()

        // Regression guard for the bug this seam removes: a calendar left open overnight used to
        // keep highlighting yesterday, because "today" was captured in an unkeyed remember.
        rule.onKalendarDay(LocalDate(2026, 3, 11)).assert(isKalendarToday())
        rule.onNode(hasKalendarDay(LocalDate(2026, 3, 10)) and isKalendarToday()).assertDoesNotExist()
    }

    private companion object {
        val MARCH_FIRST = LocalDate(2026, 3, 1)
        const val TWO_MINUTES_MS = 120_000L
    }
}
