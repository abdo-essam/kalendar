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

package com.himanshoe.kalendar.testing

import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performClick
import com.himanshoe.kalendar.KalendarMonth
import com.himanshoe.kalendar.KalendarViewConfig
import com.himanshoe.kalendar.state.ProvideKalendarTimeSource
import com.himanshoe.kalendar.theme.KalendarStrings
import com.himanshoe.kalendar.theme.KalendarTheme
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import org.junit.Rule
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The matchers against a real [KalendarMonth] — every assertion here goes through the semantics
 * the library publishes for screen readers, which is the module's core promise: no internal test
 * tags, so anything these matchers find, an end user's test can find too.
 */
class KalendarSemanticsMatchersUiTest {

    @get:Rule
    val rule = createComposeRule()

    private val august15 = LocalDate(2026, 8, 15)

    @Test
    fun findsADayCellByItsDateAndOnlyItsDate() {
        rule.setContent {
            KalendarMonth(selectedDate = august15)
        }

        rule.onKalendarDay(august15).assertExists()
        rule.onKalendarDay(LocalDate(2026, 1, 15)).assertDoesNotExist()
    }

    @Test
    fun stockSelectionAssertionsComposeWithTheFinder() {
        rule.setContent {
            KalendarMonth(selectedDate = august15)
        }

        rule.onKalendarDay(august15).assertIsSelected()
        rule.onKalendarDay(LocalDate(2026, 8, 16)).assertIsNotSelected()
    }

    @Test
    fun aDisabledDateReadsAsNotEnabled() {
        rule.setContent {
            KalendarMonth(
                selectedDate = august15,
                config = KalendarViewConfig(disabledDates = { date -> date == LocalDate(2026, 8, 20) }),
            )
        }

        rule.onKalendarDay(LocalDate(2026, 8, 20)).assertIsNotEnabled()
    }

    @Test
    fun todayAndHasEventsMatchTheSuffixedDescriptionsTheLibraryPublishes() {
        rule.setContent {
            ProvideKalendarTimeSource(fixedKalendarTimeSource(now = LocalDateTime(2026, 8, 15, 9, 0))) {
                KalendarMonth(
                    selectedDate = august15,
                    events = listOf(kalendarEvent(date = august15, id = "1")),
                )
            }
        }

        // Both suffixes are appended to this cell's description, and the finder still matches it.
        rule.onKalendarDay(august15)
            .assert(isKalendarToday())
            .assert(hasKalendarEvents())
        // SemanticsMatcher has no negation, so "the 16th is neither" is an absence assertion.
        val august16 = LocalDate(2026, 8, 16)
        rule.onNode(hasKalendarDay(august16) and isKalendarToday()).assertDoesNotExist()
        rule.onNode(hasKalendarDay(august16) and hasKalendarEvents()).assertDoesNotExist()
    }

    @Test
    fun clickingThroughTheFinderReportsTheDate() {
        var clicked: LocalDate? = null
        rule.setContent {
            KalendarMonth(
                selectedDate = august15,
                onDateClick = { date, _ -> clicked = date },
            )
        }

        rule.onKalendarDay(LocalDate(2026, 8, 21)).performClick()

        rule.runOnIdle { assertEquals(LocalDate(2026, 8, 21), clicked) }
    }

    @Test
    fun customStringsMatchWhenTheTestPassesTheSameStrings() {
        val german = KalendarStrings(
            todaySuffix = ", heute",
            dayAccessibilityLabel = { date, monthName -> "${date.day}. $monthName ${date.year}" },
        )
        rule.setContent {
            ProvideKalendarTimeSource(fixedKalendarTimeSource(now = LocalDateTime(2026, 8, 15, 9, 0))) {
                KalendarTheme(strings = german) {
                    KalendarMonth(selectedDate = august15)
                }
            }
        }

        rule.onKalendarDay(august15, strings = german).assert(isKalendarToday(strings = german))
        rule.onKalendarDay(august15).assertDoesNotExist()
    }
}
