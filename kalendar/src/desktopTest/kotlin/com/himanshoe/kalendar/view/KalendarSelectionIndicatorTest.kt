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
import androidx.compose.ui.test.onNodeWithTag
import kotlinx.datetime.LocalDate
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertTrue

/**
 * Guards the sliding selection indicator against reappearing at the origin.
 *
 * The golden tests cannot see this: they render once, after layout has settled, so a single
 * frame drawn at [androidx.compose.ui.geometry.Offset.Zero] is already gone by the time the
 * bitmap is captured. It was only ever visible in a browser. Driving the clock manually is what
 * makes the first frame observable.
 */
class KalendarSelectionIndicatorTest {

    @get:Rule
    val rule = createComposeRule()

    @Suppress("MagicNumber")
    private val march = LocalDate(2026, 3, 10)

    @Test
    fun indicator_isNotDrawnBeforeItKnowsWhereTheSelectedCellIs() {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            KalendarMonth(selectedDate = march)
        }

        // A cell's position is only known once onGloballyPositioned has run, a frame after the
        // grid first composes. Drawing before that put the indicator in the top-left corner.
        rule.onNodeWithTag(SELECTION_INDICATOR_TAG).assertDoesNotExist()

        rule.mainClock.autoAdvance = true
        rule.waitForIdle()

        val bounds = rule.onNodeWithTag(SELECTION_INDICATOR_TAG).fetchSemanticsNode().boundsInRoot
        assertTrue(
            bounds.left > 0f || bounds.top > 0f,
            "indicator settled at the origin instead of on the selected cell, was $bounds",
        )
    }

    @Test
    fun indicator_tracksTheSelectedCellOnceSettled() {
        rule.setContent {
            KalendarMonth(selectedDate = march)
        }
        rule.waitForIdle()

        val indicator = rule.onNodeWithTag(SELECTION_INDICATOR_TAG).fetchSemanticsNode().boundsInRoot
        val cell = rule.onNodeWithContentDescription("March 10, 2026").fetchSemanticsNode().boundsInRoot

        assertTrue(
            indicator.overlaps(cell),
            "indicator at $indicator should sit over the selected cell at $cell",
        )
    }
}
