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

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The generated snippet is the playground's main promise: what it prints has to be the code that
 * reproduces what is on screen, and nothing more.
 */
class PlaygroundCodeGenTest {

    private fun code(config: PlaygroundConfig, dark: Boolean = false): String =
        generatePlaygroundCode(config = config, dark = dark)

    @Test
    fun stockConfiguration_emitsNoThemeWrapperAtAll() {
        val generated = code(PlaygroundConfig())
        assertFalse(generated.contains("KalendarTheme("), "stock config should not wrap in a theme:\n$generated")
        assertContains(generated, "KalendarMonth(")
        assertContains(generated, "selectedDates = selection.selectedDates")
    }

    @Test
    fun changingOnlyTheAccent_emitsOnlyTheAccentColourTokens() {
        val generated = code(PlaygroundConfig(accent = PlaygroundAccent.Rose))
        assertContains(generated, "colors = KalendarThemeDefaults.lightColors().copy(")
        assertContains(generated, "todayContent = Color(0xFFC2185B)")
        assertContains(generated, "eventIndicator = Color(0xFFC2185B)")
        assertFalse(generated.contains("background = Color"), "accent alone must not restate the background")
        assertFalse(generated.contains("gridLine = Color"), "accent alone must not restate the grid line")
    }

    @Test
    fun darkAppearance_copiesTheDarkPalette() {
        val config = PlaygroundConfig(appearance = PlaygroundAppearance.Dark, accent = PlaygroundAccent.Teal)
        val generated = code(config = config, dark = true)
        assertContains(generated, "colors = KalendarThemeDefaults.darkColors().copy(")
        assertContains(generated, "todayContent = Color(0xFF4DB6AC)")
    }

    @Test
    fun systemAppearance_copiesTheSystemPalette() {
        val config = PlaygroundConfig(appearance = PlaygroundAppearance.System, accent = PlaygroundAccent.Amber)
        assertContains(code(config), "colors = KalendarThemeDefaults.systemColors().copy(")
    }

    @Test
    fun disablingAnimations_emitsTheReducedMotionToken() {
        val generated = code(PlaygroundConfig(animationsEnabled = false))
        assertContains(generated, "animations = KalendarAnimations(enabled = false)")
    }

    @Test
    fun brutalistPreset_emitsSquareShapesHeavyTypeAndThickGridLines() {
        val generated = code(PlaygroundPreset.Brutalist.applyTo(PlaygroundConfig()))
        assertContains(generated, "dayCell = RectangleShape")
        assertContains(generated, "eventIndicator = RectangleShape")
        assertContains(generated, "eventBlock = RectangleShape")
        assertContains(generated, "gridLineThickness = 3.dp")
        assertContains(generated, "FontWeight.Black")
        assertContains(generated, "animations = KalendarAnimations(enabled = false)")
    }

    @Test
    fun playfulPreset_emitsRoundedCellsBigDotsAndABouncySpring() {
        val generated = code(PlaygroundPreset.Playful.applyTo(PlaygroundConfig()))
        assertContains(generated, "dayCell = RoundedCornerShape(10.dp)")
        assertContains(generated, "eventIndicatorSize = 9.dp")
        assertContains(generated, "Spring.DampingRatioMediumBouncy")
        assertContains(generated, "pressScale = ${BouncyPressScale}f")
    }

    @Test
    fun minimalPreset_turnsOffTheSelectionIndicatorAndTheJumpPicker() {
        val generated = code(PlaygroundPreset.Minimal.applyTo(PlaygroundConfig()))
        assertContains(generated, "config = KalendarViewConfig(")
        assertContains(generated, "showSelectionIndicator = false")
        assertContains(generated, "showJumpPicker = false")
        assertContains(generated, "showAdjacentMonthDates = false")
        assertContains(generated, "eventIndicatorCap = 1")
    }

    @Test
    fun sliderValues_landInTheDimensionsBlock() {
        val config = PlaygroundConfig(dayCellPadding = 8.dp, hourHeight = 96.dp)
        val generated = code(config)
        assertContains(generated, "dimensions = KalendarDimensions(")
        assertContains(generated, "dayCellPadding = 8.dp")
        assertContains(generated, "hourHeight = 96.dp")
    }

    @Test
    fun rangeSelection_emitsTheDragCallbacks() {
        val generated = code(PlaygroundConfig(selection = PlaygroundSelection.Range))
        assertContains(generated, "rememberKalendarSelectionState(mode = KalendarSelectionMode.Range)")
        assertContains(generated, "onDateRangeSelect = ")
        assertContains(generated, "onDateRangeSelectEnd = ")
    }
}

/** How the snippet adapts to the view on screen, which decides both its arguments and its slots. */
class PlaygroundCodeGenViewTest {

    private fun code(config: PlaygroundConfig): String =
        generatePlaygroundCode(config = config, dark = false)

    @Test
    fun customSlots_appearInTheSnippetWithTheAccessibleCell() {
        val generated = code(PlaygroundConfig(customCell = true, customHeader = true))
        assertContains(generated, "header = { scope -> BrandHeader(scope = scope) }")
        assertContains(generated, "dayContent = { scope ->")
        assertContains(generated, "BrandDayCell(")
    }

    @Test
    fun scheduleView_emitsEventCallbacksInsteadOfDaySelection() {
        val generated = code(PlaygroundConfig(view = PlaygroundView.Schedule))
        assertContains(generated, "KalendarSchedule(")
        assertContains(generated, "onEventTimeChange = ")
        assertFalse(generated.contains("selectedDates"), "the Schedule view has no day selection:\n$generated")
    }

    @Test
    fun scheduleView_ignoresTheDayCellSlotsThatDoNotApplyToIt() {
        val config = PlaygroundConfig(view = PlaygroundView.Schedule, customCell = true, customHeader = true)
        val generated = code(config)
        assertFalse(generated.contains("dayContent"), "Schedule has no dayContent slot:\n$generated")
        assertFalse(generated.contains("BrandHeader"), "Schedule has no header slot:\n$generated")
    }

    @Test
    fun agendaView_isThemedAndTakesNoDaySelection() {
        val generated = code(PlaygroundConfig(view = PlaygroundView.Agenda, animationsEnabled = false))
        assertContains(generated, "KalendarAgenda(")
        assertContains(generated, "onEventClick = ")
        assertContains(generated, "animations = KalendarAnimations(enabled = false)")
        assertFalse(generated.contains("selectedDate"), "the Agenda view has no selected date:\n$generated")
        assertFalse(generated.contains("rememberKalendarSelectionState"), "no selection state:\n$generated")
    }

    @Test
    fun agendaCardShape_followsThePresetsShapeLanguage() {
        val generated = code(PlaygroundPreset.Brutalist.applyTo(PlaygroundConfig(view = PlaygroundView.Agenda)))
        assertContains(generated, "agendaCard = RectangleShape")
    }

    @Test
    fun everyPresetAndView_generatesBalancedParentheses() {
        PlaygroundPreset.entries.forEach { preset ->
            PlaygroundView.entries.forEach { view ->
                val generated = code(preset.applyTo(PlaygroundConfig(view = view)))
                val opened = generated.count { it == '(' }
                val closed = generated.count { it == ')' }
                assertTrue(
                    opened == closed,
                    "unbalanced parentheses for $preset/$view ($opened vs $closed):\n$generated",
                )
            }
        }
    }
}
