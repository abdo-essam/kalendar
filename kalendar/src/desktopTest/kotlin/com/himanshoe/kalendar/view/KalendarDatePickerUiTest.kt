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

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * The date every [KalendarDatePicker] test below treats as today, pinned so the suite asserts the
 * same thing on every calendar day it is ever run.
 */
@Suppress("MagicNumber")
private val TODAY = LocalDate(2026, 8, 14)

private val FROZEN_CLOCK = object : Clock {
    override fun now(): Instant = Instant.parse("2026-08-14T09:00:00Z")
}

/**
 * A month state anchored on [TODAY] and starting weeks on Sunday, matching the Su–Sa grid the
 * picker is designed around. `minDate`/`maxDate` live here rather than on the picker, which is the
 * point several of these tests exist to pin down.
 */
@Composable
private fun pickerState(
    initialDate: LocalDate = TODAY,
    minDate: LocalDate? = null,
    maxDate: LocalDate? = null,
): KalendarViewState = rememberKalendarMonthState(
    initialDate = initialDate,
    startDayOfWeek = DayOfWeek.SUNDAY,
    minDate = minDate,
    maxDate = maxDate,
)

/**
 * Hosts [content] against the frozen clock, with animations off so an assertion never races a pager
 * settling, at the width the picker is drawn for.
 */
private fun ComposeContentTestRule.setPicker(content: @Composable () -> Unit) {
    setContent {
        ProvideKalendarTimeSource(KalendarTimeSource(timeZone = TimeZone.UTC, clock = FROZEN_CLOCK)) {
            KalendarTheme(animations = KalendarAnimations(enabled = false)) {
                Row(modifier = Modifier.width(KalendarDatePickerDefaults.Width)) {
                    content()
                }
            }
        }
    }
}

/**
 * What the picker does with a tap: which dates report, which refuse, and how each
 * [KalendarSelectionMode] paints the result.
 */
@Suppress("MagicNumber")
class KalendarDatePickerUiTest {

    @get:Rule
    val rule = createComposeRule()

    private val today = TODAY

    @Test
    fun tappingASelectableDate_reportsThatDate() {
        var clicked: LocalDate? = null
        rule.setPicker {
            KalendarDatePicker(
                selectedDates = emptySet(),
                onDateClick = { clicked = it },
                state = pickerState(),
            )
        }
        rule.onNodeWithContentDescription("August 20, 2026").performClick()
        assertEquals(LocalDate(2026, 8, 20), clicked)
    }

    @Test
    fun tappingADateAfterMaxDate_reportsNothing() {
        var clicked: LocalDate? = null
        rule.setPicker {
            KalendarDatePicker(
                selectedDates = emptySet(),
                onDateClick = { clicked = it },
                state = pickerState(maxDate = today),
            )
        }
        rule.onNodeWithContentDescription("August 20, 2026").performClick()
        assertNull(clicked)
    }

    @Test
    fun tappingADateBeforeMinDate_reportsNothing() {
        var clicked: LocalDate? = null
        rule.setPicker {
            KalendarDatePicker(
                selectedDates = emptySet(),
                onDateClick = { clicked = it },
                state = pickerState(minDate = today),
            )
        }
        rule.onNodeWithContentDescription("August 3, 2026").performClick()
        assertNull(clicked)
    }

    @Test
    fun aDateInsideTheBounds_staysSelectable() {
        var clicked: LocalDate? = null
        rule.setPicker {
            KalendarDatePicker(
                selectedDates = emptySet(),
                onDateClick = { clicked = it },
                state = pickerState(minDate = LocalDate(2026, 8, 10), maxDate = today),
            )
        }
        rule.onNodeWithContentDescription("August 12, 2026").performClick()
        assertEquals(LocalDate(2026, 8, 12), clicked)
    }

    @Test
    fun tappingADisabledDate_reportsNothing() {
        var clicked: LocalDate? = null
        rule.setPicker {
            KalendarDatePicker(
                selectedDates = emptySet(),
                onDateClick = { clicked = it },
                state = pickerState(),
                config = KalendarViewConfig(disabledDates = { it.dayOfWeek == DayOfWeek.SUNDAY }),
            )
        }
        rule.onNodeWithContentDescription("August 16, 2026").performClick()
        assertNull(clicked)
    }

    @Test
    fun rangeMode_marksEveryDateBetweenTheEndpointsSelected() {
        rule.setPicker {
            KalendarDatePicker(
                // Only the two endpoints: the picker is expected to fill the span itself.
                selectedDates = setOf(LocalDate(2026, 8, 11), LocalDate(2026, 8, 20)),
                onDateClick = {},
                state = pickerState(),
                selectionMode = KalendarSelectionMode.Range,
            )
        }
        rule.onNodeWithContentDescription("August 11, 2026").assertIsSelected()
        rule.onNodeWithContentDescription("August 15, 2026").assertIsSelected()
        rule.onNodeWithContentDescription("August 20, 2026").assertIsSelected()
        rule.onNodeWithContentDescription("August 21, 2026").assertIsNotSelected()
        rule.onNodeWithContentDescription("August 10, 2026").assertIsNotSelected()
    }

    @Test
    fun multipleMode_marksOnlyTheGivenDatesSelected() {
        rule.setPicker {
            KalendarDatePicker(
                selectedDates = setOf(LocalDate(2026, 8, 11), LocalDate(2026, 8, 20)),
                onDateClick = {},
                state = pickerState(),
                selectionMode = KalendarSelectionMode.Multiple,
            )
        }
        rule.onNodeWithContentDescription("August 11, 2026").assertIsSelected()
        rule.onNodeWithContentDescription("August 15, 2026").assertIsNotSelected()
        rule.onNodeWithContentDescription("August 20, 2026").assertIsSelected()
    }

    /**
     * The leading padding days a month grid borrows from the previous month are dimmed but still
     * live — a picker that showed the 31st and then refused it would be asking for a page turn that
     * changes nothing. (There is no trailing padding to test: `monthGridDates` stops at the month's
     * last day.)
     */
    @Test
    fun adjacentMonthDatesRemainSelectable() {
        var clicked: LocalDate? = null
        rule.setPicker {
            KalendarDatePicker(
                selectedDates = emptySet(),
                onDateClick = { clicked = it },
                state = pickerState(),
            )
        }
        rule.onNodeWithContentDescription("July 31, 2026").performClick()
        assertEquals(LocalDate(2026, 7, 31), clicked)
    }
}

/**
 * What the picker draws around the grid: the action rail, the month and year dropdowns, and the
 * arrows the calendar's bounds are expected to stop.
 */
@Suppress("MagicNumber")
class KalendarDatePickerChromeUiTest {

    @get:Rule
    val rule = createComposeRule()

    private val today = TODAY

    @Test
    fun clearAction_firesAndIsHiddenWithoutAHandler() {
        var cleared = false
        rule.setPicker {
            KalendarDatePicker(
                selectedDates = setOf(today),
                onDateClick = {},
                state = pickerState(),
                onClear = { cleared = true },
            )
        }
        rule.onNodeWithContentDescription("Clear").performClick()
        assertEquals(true, cleared)
    }

    @Test
    fun todayAction_scrollsBackAndReportsToday() {
        var reported: LocalDate? = null
        val state = mutableStateOf<KalendarViewState?>(null)
        rule.setPicker {
            val pickerState = pickerState(initialDate = LocalDate(2026, 11, 1))
            state.value = pickerState
            KalendarDatePicker(
                selectedDates = emptySet(),
                onDateClick = {},
                state = pickerState,
                onTodayClick = { reported = it },
            )
        }
        rule.onNodeWithContentDescription("Today").performClick()
        rule.waitForIdle()
        assertEquals(today, reported)
        assertEquals(8, state.value?.visibleDate?.monthNumber)
    }

    @Test
    fun withoutHandlers_neitherActionIsDrawn() {
        rule.setPicker {
            KalendarDatePicker(
                selectedDates = emptySet(),
                onDateClick = {},
                state = pickerState(),
            )
        }
        rule.onNodeWithContentDescription("Clear").assertDoesNotExist()
        rule.onNodeWithContentDescription("Today").assertDoesNotExist()
    }

    @Test
    fun clearIsHideableIndependentlyOfToday() {
        rule.setPicker {
            KalendarDatePicker(
                selectedDates = emptySet(),
                onDateClick = {},
                state = pickerState(),
                onTodayClick = {},
            )
        }
        rule.onNodeWithContentDescription("Today").assertIsDisplayed()
        rule.onNodeWithContentDescription("Clear").assertDoesNotExist()
    }

    @Test
    fun monthPicker_changesTheVisiblePage() {
        val state = mutableStateOf<KalendarViewState?>(null)
        rule.setPicker {
            val pickerState = pickerState()
            state.value = pickerState
            KalendarDatePicker(
                selectedDates = emptySet(),
                onDateClick = {},
                state = pickerState,
            )
        }
        rule.onNodeWithContentDescription("August, Choose month and year").performClick()
        rule.onNodeWithContentDescription("December").performClick()
        rule.waitForIdle()
        assertEquals(12, state.value?.visibleDate?.monthNumber)
        rule.onNodeWithContentDescription("December 25, 2026").assertIsDisplayed()
    }

    @Test
    fun yearPicker_changesTheVisiblePage() {
        val state = mutableStateOf<KalendarViewState?>(null)
        rule.setPicker {
            val pickerState = pickerState()
            state.value = pickerState
            KalendarDatePicker(
                selectedDates = emptySet(),
                onDateClick = {},
                state = pickerState,
                yearRange = 2024..2030,
            )
        }
        rule.onNodeWithContentDescription("2026, Choose month and year").performClick()
        rule.onNodeWithContentDescription("2029").performClick()
        rule.waitForIdle()
        assertEquals(2029, state.value?.visibleDate?.year)
        rule.onNodeWithContentDescription("August 14, 2029").assertIsDisplayed()
    }

    @Test
    fun nextArrowIsDisabledAtMaxDate_andPreviousAtMinDate() {
        rule.setPicker {
            KalendarDatePicker(
                selectedDates = emptySet(),
                onDateClick = {},
                state = pickerState(minDate = LocalDate(2026, 8, 1), maxDate = LocalDate(2026, 8, 31)),
            )
        }
        rule.onNodeWithContentDescription("Next").assertIsNotEnabled()
        rule.onNodeWithContentDescription("Previous").assertIsNotEnabled()
    }

    @Test
    fun disabledDateAnnouncesItselfAsDisabled() {
        rule.setPicker {
            KalendarDatePicker(
                selectedDates = emptySet(),
                onDateClick = {},
                state = pickerState(maxDate = today),
            )
        }
        rule.onNodeWithContentDescription("August 20, 2026").assertIsNotEnabled()
        rule.onNodeWithContentDescription("August 12, 2026").assertIsEnabled()
    }
}

/**
 * Pixel-level proof that "today" and "selected" are two separable marks on the same cell.
 *
 * Semantics cannot express this — a screen reader hears "selected", never "underlined" — and the
 * golden suite is not the place for it either, since what matters is not the exact pixels but that
 * four states of *the same date* produce four different images. Rendering one date four ways keeps
 * the digits identical, so any difference found is the marks and nothing else.
 */
@Suppress("MagicNumber")
class KalendarDatePickerTodayAndSelectionTest {

    @get:Rule
    val rule = createComposeRule()

    private val date = LocalDate(2026, 8, 14)

    @Test
    fun todayAndSelectedAreDistinctMarksThatCompose() {
        rule.setContent {
            KalendarTheme(animations = KalendarAnimations(enabled = false)) {
                Row {
                    Cell(tag = "plain", isToday = false, isSelected = false)
                    Cell(tag = "todayOnly", isToday = true, isSelected = false)
                    Cell(tag = "selectedOnly", isToday = false, isSelected = true)
                    Cell(tag = "both", isToday = true, isSelected = true)
                }
            }
        }

        val plain = pixels("plain")
        val todayOnly = pixels("todayOnly")
        val selectedOnly = pixels("selectedOnly")
        val both = pixels("both")

        assertNotEqualPixels("today adds a mark of its own", plain, todayOnly)
        assertNotEqualPixels("selection adds a mark of its own", plain, selectedOnly)
        assertNotEqualPixels("today and selected are not the same mark", todayOnly, selectedOnly)
        assertNotEqualPixels("a selected cell that is today shows more than selection", selectedOnly, both)
        assertNotEqualPixels("a today cell that is selected shows more than today", todayOnly, both)
    }

    @Composable
    private fun Cell(tag: String, isToday: Boolean, isSelected: Boolean) {
        KalendarDatePickerDefaults.DayCell(
            scope = KalendarDatePickerDayScope(
                day = KalendarDayScope(
                    date = date,
                    isSelected = isSelected,
                    isToday = isToday,
                    isDisabled = false,
                    events = emptyList(),
                ),
            ),
            onClick = {},
            modifier = Modifier.testTag(tag).width(48.dp),
        )
    }

    private fun pixels(tag: String): IntArray {
        val image = rule.onNodeWithTag(tag).captureToImage().toPixelMap()
        return IntArray(image.width * image.height) { index ->
            image[index % image.width, index / image.width].toArgb()
        }
    }

    private fun assertNotEqualPixels(message: String, a: IntArray, b: IntArray) {
        assertTrue(!a.contentEquals(b), message)
    }
}
