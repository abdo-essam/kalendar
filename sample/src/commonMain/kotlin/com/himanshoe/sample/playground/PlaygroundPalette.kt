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

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import com.himanshoe.kalendar.theme.KalendarColors
import com.himanshoe.kalendar.theme.KalendarThemeDefaults

/** One accent, expressed the way Material expresses one: a core plus a container pair. */
@Immutable
internal data class AccentTone(
    val core: Color,
    val container: Color,
    val onContainer: Color,
)

/**
 * The six accent swatches offered by the playground.
 *
 * [Indigo] is deliberately identical to Kalendar's stock palette in both modes, so the Material
 * preset with the default accent generates no colour override at all.
 */
internal enum class PlaygroundAccent(
    val label: String,
    private val lightTone: AccentTone,
    private val darkTone: AccentTone,
) {
    Indigo(label = "Indigo", lightTone = Tones.IndigoLight, darkTone = Tones.IndigoDark),
    Teal(label = "Teal", lightTone = Tones.TealLight, darkTone = Tones.TealDark),
    Amber(label = "Amber", lightTone = Tones.AmberLight, darkTone = Tones.AmberDark),
    Rose(label = "Rose", lightTone = Tones.RoseLight, darkTone = Tones.RoseDark),
    Forest(label = "Forest", lightTone = Tones.ForestLight, darkTone = Tones.ForestDark),
    Slate(label = "Slate", lightTone = Tones.SlateLight, darkTone = Tones.SlateDark),
    ;

    fun tone(dark: Boolean): AccentTone = if (dark) darkTone else lightTone
}

/**
 * Resolves the whole [KalendarColors] token set for a configuration.
 *
 * Pure on purpose: the code generator diffs the result against the stock palette to work out which
 * tokens the generated snippet has to mention.
 */
internal fun PlaygroundConfig.resolveColors(dark: Boolean): KalendarColors {
    val base = if (dark) KalendarThemeDefaults.darkColors() else KalendarThemeDefaults.lightColors()
    val tone = accent.tone(dark)
    return when (skin) {
        PlaygroundSkin.Material -> materialColors(base = base, tone = tone)
        PlaygroundSkin.Minimal -> minimalColors(base = base, tone = tone, dark = dark)
        PlaygroundSkin.Brutalist -> brutalistColors(base = base, tone = tone, dark = dark)
        PlaygroundSkin.Playful -> playfulColors(base = base, tone = tone, dark = dark)
    }
}

/** The colour the accent swatch row paints for this accent in the current mode. */
internal fun PlaygroundAccent.swatchColor(dark: Boolean): Color = tone(dark).core

private fun materialColors(base: KalendarColors, tone: AccentTone): KalendarColors = base.copy(
    selectionBackground = tone.container,
    onSelectionBackground = tone.onContainer,
    todayContent = tone.core,
    eventIndicator = tone.core,
)

private fun minimalColors(base: KalendarColors, tone: AccentTone, dark: Boolean): KalendarColors = base.copy(
    background = if (dark) Tones.MinimalDarkBackground else Tones.MinimalLightBackground,
    selectionBackground = tone.core.copy(alpha = MinimalSelectionAlpha),
    onSelectionBackground = if (dark) Tones.MinimalDarkHeader else Tones.MinimalLightHeader,
    todayContent = tone.core,
    dayContent = if (dark) Tones.MinimalDarkDay else Tones.MinimalLightDay,
    dayLabelContent = if (dark) Tones.MinimalDarkLabel else Tones.MinimalLightLabel,
    headerContent = if (dark) Tones.MinimalDarkHeader else Tones.MinimalLightHeader,
    eventIndicator = if (dark) Tones.MinimalDarkIndicator else Tones.MinimalLightIndicator,
    nowIndicator = tone.core,
    gridLine = if (dark) Tones.MinimalDarkGrid else Tones.MinimalLightGrid,
    dragGhostBackground = tone.core.copy(alpha = MinimalSelectionAlpha),
)

private fun brutalistColors(base: KalendarColors, tone: AccentTone, dark: Boolean): KalendarColors {
    val ink = if (dark) Tones.BrutalistPaper else Tones.BrutalistInk
    val paper = if (dark) Tones.BrutalistInk else Tones.BrutalistPaper
    return base.copy(
        background = paper,
        selectionBackground = tone.core,
        onSelectionBackground = paper,
        todayContent = tone.core,
        dayContent = ink,
        dayLabelContent = ink,
        headerContent = ink,
        eventIndicator = ink,
        nowIndicator = Tones.BrutalistAlarm,
        gridLine = ink,
        dragGhostBackground = tone.core,
        eventBlockAlpha = BrutalistEventBlockAlpha,
    )
}

private fun playfulColors(base: KalendarColors, tone: AccentTone, dark: Boolean): KalendarColors = base.copy(
    background = if (dark) Tones.PlayfulDarkBackground else Tones.PlayfulLightBackground,
    selectionBackground = tone.core,
    onSelectionBackground = if (dark) Tones.PlayfulDarkBackground else Tones.PlayfulLightOnSelection,
    todayContent = tone.core,
    dayContent = if (dark) Tones.PlayfulDarkDay else Tones.PlayfulLightDay,
    dayLabelContent = if (dark) Tones.PlayfulDarkLabel else Tones.PlayfulLightLabel,
    headerContent = tone.core,
    eventIndicator = tone.core,
    nowIndicator = Tones.PlayfulNow,
    gridLine = tone.core.copy(alpha = PlayfulGridAlpha),
    dragGhostBackground = tone.container,
    eventBlockAlpha = PlayfulEventBlockAlpha,
)

private const val MinimalSelectionAlpha = 0.14f
private const val BrutalistEventBlockAlpha = 0.35f
private const val PlayfulGridAlpha = 0.2f
private const val PlayfulEventBlockAlpha = 0.28f

/** Every raw colour the playground's looks are built from. */
@Suppress("MagicNumber")
private object Tones {
    val IndigoLight = AccentTone(
        core = Color(0xFF6750A4),
        container = Color(0xFFE8DEF8),
        onContainer = Color(0xFF1D192B),
    )
    val IndigoDark = AccentTone(
        core = Color(0xFFD0BCFF),
        container = Color(0xFF4F378B),
        onContainer = Color(0xFFEADDFF),
    )
    val TealLight = AccentTone(
        core = Color(0xFF00695C),
        container = Color(0xFFB2DFDB),
        onContainer = Color(0xFF00251F),
    )
    val TealDark = AccentTone(
        core = Color(0xFF4DB6AC),
        container = Color(0xFF00504A),
        onContainer = Color(0xFFB2DFDB),
    )
    val AmberLight = AccentTone(
        core = Color(0xFFB26A00),
        container = Color(0xFFFFE0B2),
        onContainer = Color(0xFF3E2600),
    )
    val AmberDark = AccentTone(
        core = Color(0xFFFFB74D),
        container = Color(0xFF5C3A00),
        onContainer = Color(0xFFFFE0B2),
    )
    val RoseLight = AccentTone(
        core = Color(0xFFC2185B),
        container = Color(0xFFF8BBD0),
        onContainer = Color(0xFF3F0018),
    )
    val RoseDark = AccentTone(
        core = Color(0xFFF48FB1),
        container = Color(0xFF6D0034),
        onContainer = Color(0xFFF8BBD0),
    )
    val ForestLight = AccentTone(
        core = Color(0xFF2E7D32),
        container = Color(0xFFC8E6C9),
        onContainer = Color(0xFF0B2E0D),
    )
    val ForestDark = AccentTone(
        core = Color(0xFF81C784),
        container = Color(0xFF1B5E20),
        onContainer = Color(0xFFC8E6C9),
    )
    val SlateLight = AccentTone(
        core = Color(0xFF37474F),
        container = Color(0xFFCFD8DC),
        onContainer = Color(0xFF101A1F),
    )
    val SlateDark = AccentTone(
        core = Color(0xFFB0BEC5),
        container = Color(0xFF37474F),
        onContainer = Color(0xFFCFD8DC),
    )

    val MinimalLightBackground = Color(0xFFFAFAFA)
    val MinimalDarkBackground = Color(0xFF141414)
    val MinimalLightDay = Color(0xFF5B5B5B)
    val MinimalDarkDay = Color(0xFFBDBDBD)
    val MinimalLightLabel = Color(0xFFA0A0A0)
    val MinimalDarkLabel = Color(0xFF7A7A7A)
    val MinimalLightHeader = Color(0xFF2E2E2E)
    val MinimalDarkHeader = Color(0xFFE8E8E8)
    val MinimalLightIndicator = Color(0xFFC4C4C4)
    val MinimalDarkIndicator = Color(0xFF5C5C5C)
    val MinimalLightGrid = Color(0xFFEBEBEB)
    val MinimalDarkGrid = Color(0xFF2A2A2A)

    val BrutalistPaper = Color(0xFFFFFFFF)
    val BrutalistInk = Color(0xFF000000)
    val BrutalistAlarm = Color(0xFFFF2D2D)

    val PlayfulLightBackground = Color(0xFFFFF8F3)
    val PlayfulDarkBackground = Color(0xFF1A1424)
    val PlayfulLightDay = Color(0xFF4A3B55)
    val PlayfulDarkDay = Color(0xFFEDE3F5)
    val PlayfulLightLabel = Color(0xFF9C8AA8)
    val PlayfulDarkLabel = Color(0xFF9E8CB0)
    val PlayfulLightOnSelection = Color(0xFFFFFFFF)
    val PlayfulNow = Color(0xFFFF5A8A)
}
