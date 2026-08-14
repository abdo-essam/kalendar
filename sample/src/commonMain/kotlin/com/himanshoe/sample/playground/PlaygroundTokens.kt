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

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.himanshoe.kalendar.KalendarViewConfig
import com.himanshoe.kalendar.theme.KalendarAnimations
import com.himanshoe.kalendar.theme.KalendarDimensions
import com.himanshoe.kalendar.theme.KalendarShapes
import com.himanshoe.kalendar.theme.KalendarThemeDefaults
import com.himanshoe.kalendar.theme.KalendarTypography
import kotlin.math.roundToInt

/** A shape paired with the Kotlin source that recreates it, so the generator never has to guess. */
@Immutable
internal data class ShapeChoice(val shape: Shape, val source: String)

/** Renders a [Dp] the way it would be written in source, e.g. `12.dp`. */
internal fun dpSource(value: Dp): String = "${value.value.roundToInt()}.dp"

/**
 * Renders a [Dp] the way a person reads it, e.g. `12 dp`.
 *
 * Deliberately not [dpSource]: a readout that says `12.dp` is a debug panel quoting its own
 * variable. The Kotlin spelling belongs in the generated snippet, which is where a visitor goes to
 * copy it.
 */
internal fun dpLabel(value: Dp): String = "${value.value.roundToInt()} dp"

/** Renders an hour of the day as a zero-padded 24-hour clock reading, e.g. `07:00`. */
internal fun hourLabel(hour: Int): String = "${hour.toString().padStart(length = 2, padChar = '0')}:00"

/**
 * Renders a ratio to two places, in both the readout and the generated snippet — `0.75f` in source
 * and `0.75` on screen, so the number a visitor drags to is the number they end up copying.
 */
internal fun ratioLabel(value: Float): String {
    val hundredths = (value * RatioScale).roundToInt()
    return "${hundredths / RatioScale}.${(hundredths % RatioScale).toString().padStart(2, '0')}"
}

/** The Kotlin literal for a ratio, e.g. `0.75f`. */
internal fun ratioSource(value: Float): String = "${ratioLabel(value)}f"

private const val RatioScale = 100

/** Whether the calendar should resolve its dark palette right now. */
@Composable
internal fun PlaygroundConfig.isDark(): Boolean = when (appearance) {
    PlaygroundAppearance.Light -> false
    PlaygroundAppearance.Dark -> true
    PlaygroundAppearance.System -> isSystemInDarkTheme()
}

internal fun PlaygroundConfig.dayCellShape(): ShapeChoice = when (cellShape) {
    PlaygroundCellShape.Circle -> CircleChoice
    PlaygroundCellShape.Rounded -> roundedChoice(RoundedCellRadius)
    PlaygroundCellShape.Square -> RectangleChoice
}

internal fun PlaygroundConfig.eventIndicatorShape(): ShapeChoice = when (skin) {
    PlaygroundSkin.Brutalist -> RectangleChoice
    else -> CircleChoice
}

internal fun PlaygroundConfig.eventBlockShape(): ShapeChoice = when (skin) {
    PlaygroundSkin.Brutalist -> RectangleChoice
    PlaygroundSkin.Minimal -> roundedChoice(MinimalBlockRadius)
    PlaygroundSkin.Playful -> roundedChoice(PlayfulBlockRadius)
    PlaygroundSkin.Material -> roundedChoice(DefaultBlockRadius)
}

internal fun PlaygroundConfig.popupShape(): ShapeChoice = when (skin) {
    PlaygroundSkin.Brutalist -> RectangleChoice
    PlaygroundSkin.Playful -> roundedChoice(PlayfulPopupRadius)
    else -> roundedChoice(DefaultPopupRadius)
}

internal fun PlaygroundConfig.agendaCardShape(): ShapeChoice = when (skin) {
    PlaygroundSkin.Brutalist -> RectangleChoice
    PlaygroundSkin.Minimal -> roundedChoice(MinimalBlockRadius)
    PlaygroundSkin.Playful -> roundedChoice(PlayfulPopupRadius)
    PlaygroundSkin.Material -> roundedChoice(DefaultAgendaCardRadius)
}

internal fun PlaygroundConfig.resolveShapes(): KalendarShapes = KalendarThemeDefaults.shapes().copy(
    dayCell = dayCellShape().shape,
    eventIndicator = eventIndicatorShape().shape,
    eventBlock = eventBlockShape().shape,
    popup = popupShape().shape,
    agendaCard = agendaCardShape().shape,
)

internal fun PlaygroundConfig.resolveDimensions(): KalendarDimensions = KalendarDimensions(
    dayCellPadding = dayCellPadding,
    dayCellAspectRatio = dayCellAspectRatio,
    eventIndicatorSize = eventIndicatorSize,
    gridLineThickness = gridLineThickness,
    hourHeight = hourHeight,
)

internal fun PlaygroundConfig.resolveAnimations(): KalendarAnimations = when {
    !animationsEnabled -> KalendarAnimations(enabled = false)
    bouncyMotion -> KalendarAnimations(
        selectionColorSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        selectionIndicatorSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow,
        ),
        pressScale = BouncyPressScale,
    )

    else -> KalendarAnimations()
}

internal fun PlaygroundConfig.resolveViewConfig(): KalendarViewConfig = KalendarViewConfig(
    showNavigationArrows = showNavigationArrows,
    showAdjacentMonthDates = showAdjacentMonthDates,
    showTodayButton = showTodayButton,
    showJumpPicker = showJumpPicker,
    showSelectionIndicator = showSelectionIndicator,
    scheduleInitialScrollHour = scheduleInitialScrollHour,
    scheduleDragSnapMinutes = scheduleDragSnapMinutes,
    eventIndicatorCap = eventIndicatorCap,
)

/**
 * The type scale for the current configuration. Only the Brutalist look departs from Material's,
 * and it does so by thickening the stock scale rather than replacing it, which is the pattern the
 * generated snippet teaches.
 */
@Composable
internal fun PlaygroundConfig.resolveTypography(): KalendarTypography {
    val base = KalendarThemeDefaults.typography()
    if (!heavyType) {
        return base
    }
    return base.copy(
        headerTitle = base.headerTitle.copy(fontWeight = FontWeight.Black),
        monthLabel = base.monthLabel.copy(fontWeight = FontWeight.Black),
        dayOfWeekLabel = base.dayOfWeekLabel.copy(
            fontWeight = FontWeight.Black,
            letterSpacing = HeavyLabelSpacing,
        ),
        dayNumber = base.dayNumber.copy(fontWeight = FontWeight.Bold),
        dayNumberEmphasized = base.dayNumberEmphasized.copy(fontWeight = FontWeight.Black),
        weekDayNumber = base.weekDayNumber.copy(fontWeight = FontWeight.Black),
        eventLabel = base.eventLabel.copy(fontWeight = FontWeight.Bold),
        hourLabel = base.hourLabel.copy(fontWeight = FontWeight.Bold),
    )
}

private fun roundedChoice(radius: Dp): ShapeChoice = ShapeChoice(
    shape = RoundedCornerShape(radius),
    source = "RoundedCornerShape(${dpSource(radius)})",
)

private val CircleChoice = ShapeChoice(shape = CircleShape, source = "CircleShape")
private val RectangleChoice = ShapeChoice(shape = RectangleShape, source = "RectangleShape")

internal val RoundedCellRadius = 10.dp
internal val DefaultBlockRadius = 4.dp
internal val DefaultPopupRadius = 12.dp
internal val DefaultAgendaCardRadius = 12.dp
private val MinimalBlockRadius = 2.dp
private val PlayfulBlockRadius = 14.dp
private val PlayfulPopupRadius = 24.dp
internal const val BouncyPressScale = 0.82f
private val HeavyLabelSpacing = 1.5.sp
