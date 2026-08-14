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

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import com.himanshoe.kalendar.KalendarTimeline
import com.himanshoe.kalendar.foundation.format.KalendarFormatters
import com.himanshoe.kalendar.foundation.format.monthTitle
import kotlinx.datetime.LocalDate
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KalendarTimelineUiTest {

    @get:Rule
    val rule = createComposeRule()

    @Suppress("MagicNumber")
    private val march = LocalDate(2026, 3, 10)

    private fun assertMonthTitleShown(title: String) {
        assertTrue(
            rule.onAllNodesWithText(title).fetchSemanticsNodes().isNotEmpty(),
            "expected month title '$title' on screen",
        )
    }

    @Test
    fun timeline_tapMidMonthDate_firesOnDateClick() {
        var clicked: LocalDate? = null
        rule.setContent {
            KalendarTimeline(
                selectedDate = march,
                onDateClick = { date, _ -> clicked = date },
            )
        }
        rule.onNodeWithContentDescription("March 18, 2026").performClick()
        assertEquals(LocalDate(2026, 3, 18), clicked)
    }

    @Test
    fun timeline_initialDateBeforeBoundedRange_clampsToFirstMonthWithoutCrash() {
        // Regression: an initialDate outside minDate/maxDate used to produce a negative
        // (or wrapped) list index and crash the lazy list on first composition.
        rule.setContent {
            val state = rememberKalendarTimelineState(
                initialDate = LocalDate(2026, 1, 5),
                minDate = LocalDate(2026, 3, 1),
                maxDate = LocalDate(2026, 4, 30),
            )
            KalendarTimeline(selectedDate = march, state = state)
        }
        rule.waitForIdle()
        assertMonthTitleShown("March 2026")
    }

    @Test
    fun timeline_scrollToBeyondMaxDate_clampsToLastMonthWithoutCrash() {
        var target by mutableStateOf<LocalDate?>(null)
        rule.setContent {
            val state = rememberKalendarTimelineState(
                initialDate = march,
                minDate = LocalDate(2026, 3, 1),
                maxDate = LocalDate(2026, 4, 30),
            )
            LaunchedEffect(target) {
                target?.let { state.animateScrollTo(it) }
            }
            KalendarTimeline(selectedDate = march, state = state)
        }
        rule.waitForIdle()

        target = LocalDate(2026, 12, 25)
        rule.waitForIdle()
        assertMonthTitleShown("April 2026")
    }

    @Test
    fun timeline_swipeUp_advancesVisibleMonthAndStickyTitle() {
        var state: KalendarTimelineState? = null
        rule.setContent {
            val timelineState = rememberKalendarTimelineState(initialDate = march)
            state = timelineState
            KalendarTimeline(selectedDate = march, state = timelineState)
        }
        rule.onNodeWithContentDescription("March 18, 2026").assertExists()

        rule.onRoot().performTouchInput { swipeUp() }
        rule.waitForIdle()

        // A fling coasts several months, so assert progression rather than a fixed target.
        val visible = checkNotNull(state).visibleDate
        assertTrue(visible > LocalDate(2026, 3, 1), "swipe up should scroll into later months, was $visible")
        assertMonthTitleShown(monthTitle(visible, KalendarFormatters.monthName))
    }
}
