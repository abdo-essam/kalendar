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

package com.himanshoe.sample.playground

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The browser cannot be driven through a Compose canvas, so the controls are exercised here
 * instead: click the real chips and switches and assert the generated snippet followed.
 */
class PlaygroundUiTest {

    @get:Rule
    val rule = createComposeRule()

    private fun startPlayground() {
        rule.setContent {
            Playground(
                modifier = Modifier.fillMaxSize(),
                clipboard = PlaygroundClipboard { _, onResult -> onResult(true) },
            )
        }
    }

    private fun generatedCode(): String = rule.onNodeWithTag("generated-code")
        .fetchSemanticsNode()
        .config
        .getOrNull(SemanticsProperties.Text)
        .orEmpty()
        .joinToString(separator = "") { it.text }

    private fun tap(tag: String) {
        rule.onNodeWithTag(tag).performScrollTo().performClick()
        rule.waitForIdle()
    }

    @Test
    fun playground_startsOnTheMonthViewWithNoThemeOverrides() {
        startPlayground()
        val code = generatedCode()
        assertContains(code, "KalendarMonth(")
        assertFalse(code.contains("KalendarTheme("), "a freshly opened playground overrides nothing:\n$code")
    }

    @Test
    fun choosingABrutalistPreset_rewritesTheSnippet() {
        startPlayground()
        tap("preset-Brutalist")
        val code = generatedCode()
        assertContains(code, "dayCell = RectangleShape")
        assertContains(code, "gridLineThickness = 3.dp")
        assertContains(code, "FontWeight.Black")
    }

    @Test
    fun switchingBetweenPresets_replacesRatherThanAccumulates() {
        startPlayground()
        tap("preset-Brutalist")
        tap("preset-Playful")
        val code = generatedCode()
        assertContains(code, "dayCell = RoundedCornerShape(10.dp)")
        assertFalse(code.contains("RectangleShape"), "the Brutalist shapes should be gone:\n$code")
        assertFalse(code.contains("KalendarAnimations(enabled = false)"), "Playful animates:\n$code")
    }

    @Test
    fun turningAnimationsOff_emitsTheReducedMotionToken() {
        startPlayground()
        assertFalse(generatedCode().contains("KalendarAnimations"), "motion is on by default")
        tap("toggle-animations")
        assertContains(generatedCode(), "animations = KalendarAnimations(enabled = false)")
    }

    @Test
    fun choosingASquareDayCell_updatesTheShapeToken() {
        startPlayground()
        tap("shape-Square")
        assertContains(generatedCode(), "dayCell = RectangleShape")
    }

    @Test
    fun choosingAnAccent_updatesOnlyTheColourTokens() {
        startPlayground()
        tap("accent-Rose")
        val code = generatedCode()
        assertContains(code, "todayContent = Color(0xFFC2185B)")
        assertFalse(code.contains("dimensions = "), "an accent is not a dimension:\n$code")
    }

    @Test
    fun switchingViews_changesTheComposableThatIsCalled() {
        startPlayground()
        tap("view-Timeline")
        assertContains(generatedCode(), "KalendarTimeline(")
        tap("view-Schedule")
        assertContains(generatedCode(), "KalendarSchedule(")
        tap("view-Year")
        assertContains(generatedCode(), "KalendarYear(")
    }

    @Test
    fun switchingSelectionMode_changesTheRememberedState() {
        startPlayground()
        tap("selection-Range")
        val code = generatedCode()
        assertContains(code, "rememberKalendarSelectionState(mode = KalendarSelectionMode.Range)")
        assertContains(code, "onDateRangeSelect = ")
    }

    @Test
    fun turningOnTheCustomCell_addsTheAccessibleDayContentSlot() {
        startPlayground()
        tap("toggle-customCell")
        val code = generatedCode()
        assertContains(code, "dayContent = { scope ->")
        assertContains(code, "BrandDayCell(")
    }

    @Test
    fun turningOnTheCustomHeader_addsTheHeaderSlot() {
        startPlayground()
        tap("toggle-customHeader")
        assertContains(generatedCode(), "header = { scope -> BrandHeader(scope = scope) }")
    }

    @Test
    fun turningOffABehaviourFlag_addsItToTheViewConfig() {
        startPlayground()
        tap("toggle-showTodayButton")
        val code = generatedCode()
        assertContains(code, "config = KalendarViewConfig(")
        assertContains(code, "showTodayButton = false")
    }

    @Test
    fun switchingToDarkAppearance_switchesTheBasePalette() {
        startPlayground()
        tap("appearance-Dark")
        tap("accent-Teal")
        assertContains(generatedCode(), "KalendarThemeDefaults.darkColors().copy(")
    }

    @Test
    fun everyView_rendersWithoutFailing() {
        startPlayground()
        PlaygroundView.entries.forEach { view ->
            tap("view-${view.name}")
            assertTrue(generatedCode().isNotEmpty(), "no snippet generated for $view")
        }
    }
}
