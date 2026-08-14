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

import androidx.compose.animation.core.SpringSpec
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.RectangleShape
import com.himanshoe.kalendar.theme.KalendarThemeDefaults
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * The presets are the playground's headline claim — that one library can look genuinely different.
 * These lock in that they really do resolve to different tokens, and that hand-tuning a preset is
 * reported honestly rather than leaving the preset chip lit.
 */
class PlaygroundPresetTest {

    @Test
    fun everyPreset_isRecognisedByPresetOf() {
        PlaygroundPreset.entries.forEach { preset ->
            assertEquals(preset, presetOf(preset.applyTo(PlaygroundConfig())), "preset $preset did not round-trip")
        }
    }

    @Test
    fun theStockConfiguration_isTheMaterialPreset() {
        assertEquals(PlaygroundPreset.Material, presetOf(PlaygroundConfig()))
    }

    @Test
    fun handTuningAPreset_clearsTheActivePreset() {
        val tuned = PlaygroundPreset.Brutalist.applyTo(PlaygroundConfig())
            .copy(cellShape = PlaygroundCellShape.Circle)
        assertEquals(null, presetOf(tuned))
    }

    @Test
    fun presets_preserveTheViewSelectionAppearanceAndAccent() {
        val chosen = PlaygroundConfig(
            view = PlaygroundView.Timeline,
            selection = PlaygroundSelection.Range,
            appearance = PlaygroundAppearance.Dark,
            accent = PlaygroundAccent.Forest,
            customCell = true,
        )
        val brutalist = PlaygroundPreset.Brutalist.applyTo(chosen)
        assertEquals(PlaygroundView.Timeline, brutalist.view)
        assertEquals(PlaygroundSelection.Range, brutalist.selection)
        assertEquals(PlaygroundAppearance.Dark, brutalist.appearance)
        assertEquals(PlaygroundAccent.Forest, brutalist.accent)
        assertTrue(brutalist.customCell)
    }

    @Test
    fun everyPairOfPresets_resolvesToADifferentPalette() {
        val palettes = PlaygroundPreset.entries.associateWith { preset ->
            preset.applyTo(PlaygroundConfig()).resolveColors(dark = false)
        }
        PlaygroundPreset.entries.forEach { left ->
            PlaygroundPreset.entries.filter { it != left }.forEach { right ->
                assertNotEquals(palettes.getValue(left), palettes.getValue(right), "$left and $right look alike")
            }
        }
    }

    @Test
    fun materialPresetWithTheDefaultAccent_isExactlyTheStockPalette() {
        val resolved = PlaygroundConfig().resolveColors(dark = false)
        assertEquals(KalendarThemeDefaults.lightColors(), resolved)
        assertEquals(KalendarThemeDefaults.darkColors(), PlaygroundConfig().resolveColors(dark = true))
    }

    @Test
    fun cellShapeControl_mapsOntoTheDayCellToken() {
        val circle = PlaygroundConfig(cellShape = PlaygroundCellShape.Circle).resolveShapes()
        val rounded = PlaygroundConfig(cellShape = PlaygroundCellShape.Rounded).resolveShapes()
        val square = PlaygroundConfig(cellShape = PlaygroundCellShape.Square).resolveShapes()
        assertEquals(CircleShape, circle.dayCell)
        assertEquals(RoundedCornerShape(RoundedCellRadius), rounded.dayCell)
        assertEquals(RectangleShape, square.dayCell)
    }

    @Test
    fun animationsToggle_flipsTheEnabledFlagAndNothingElse() {
        assertTrue(PlaygroundConfig().resolveAnimations().enabled)
        assertFalse(PlaygroundConfig(animationsEnabled = false).resolveAnimations().enabled)
    }

    @Test
    fun playfulPreset_resolvesToABouncySpring() {
        val animations = PlaygroundPreset.Playful.applyTo(PlaygroundConfig()).resolveAnimations()
        assertTrue(animations.enabled)
        assertEquals(BouncyPressScale, animations.pressScale)
        assertIs<SpringSpec<*>>(animations.selectionColorSpec)
    }

    @Test
    fun disablingAnimations_winsOverAPresetsOwnMotion() {
        val playfulWithoutMotion = PlaygroundPreset.Playful.applyTo(PlaygroundConfig())
            .copy(animationsEnabled = false)
        assertFalse(playfulWithoutMotion.resolveAnimations().enabled)
    }

    @Test
    fun behaviourToggles_reachTheViewConfig() {
        val config = PlaygroundConfig(
            showNavigationArrows = false,
            showTodayButton = false,
            showJumpPicker = false,
            showSelectionIndicator = false,
            showAdjacentMonthDates = false,
            eventIndicatorCap = 5,
            scheduleInitialScrollHour = 6,
            scheduleDragSnapMinutes = 30,
        )
        val viewConfig = config.resolveViewConfig()
        assertFalse(viewConfig.showNavigationArrows)
        assertFalse(viewConfig.showTodayButton)
        assertFalse(viewConfig.showJumpPicker)
        assertFalse(viewConfig.showSelectionIndicator)
        assertFalse(viewConfig.showAdjacentMonthDates)
        assertEquals(5, viewConfig.eventIndicatorCap)
        assertEquals(6, viewConfig.scheduleInitialScrollHour)
        assertEquals(30, viewConfig.scheduleDragSnapMinutes)
    }

    @Test
    fun sliderTokens_reachTheDimensions() {
        val config = PlaygroundPreset.Brutalist.applyTo(PlaygroundConfig())
        val dimensions = config.resolveDimensions()
        assertEquals(config.dayCellPadding, dimensions.dayCellPadding)
        assertEquals(config.eventIndicatorSize, dimensions.eventIndicatorSize)
        assertEquals(config.gridLineThickness, dimensions.gridLineThickness)
        assertEquals(config.hourHeight, dimensions.hourHeight)
    }

    @Test
    fun everyAccent_changesTheSelectionColourItAdvertises() {
        val swatches = PlaygroundAccent.entries.map { it.swatchColor(dark = false) }
        assertEquals(PlaygroundAccent.entries.size, swatches.toSet().size, "two accents share a swatch colour")
        PlaygroundAccent.entries.forEach { accent ->
            val colors = PlaygroundConfig(accent = accent).resolveColors(dark = false)
            assertEquals(accent.swatchColor(dark = false), colors.todayContent)
        }
    }
}
