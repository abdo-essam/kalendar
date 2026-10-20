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

package com.himanshoe.kalendar.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Defaults for [KalendarTheme].
 *
 * This is the only place in Kalendar that reads Material at all, and it reads it to *derive token
 * values*, never to render: every calendar view draws with Compose Foundation, so an app with its
 * own design system — or no Material 3 at all — gets a calendar whose appearance is decided
 * entirely by the tokens on [KalendarThemeTokens].
 *
 * Three palettes are available, and which one you want depends on your app:
 *
 * - [colors] (the default) derives from the ambient [MaterialTheme.colorScheme], so a Material 3
 *   app gets its own brand colours and its own light/dark scheme for free.
 * - [systemColors] returns Kalendar's own palette following the system light/dark setting, with no
 *   dependency on a Material theme being present — use it if your app doesn't use Material 3.
 * - [lightColors] and [darkColors] are those two palettes, chosen explicitly, for apps that drive
 *   their own theme switch instead of following the system.
 */
public object KalendarThemeDefaults {

    /**
     * [KalendarColors] resolved from the ambient [MaterialTheme.colorScheme] — the default, and
     * correct in both light and dark mode for any app wrapped in a Material 3 theme.
     *
     * Compose Material's colour scheme falls back to its baseline *light* scheme when no
     * `MaterialTheme` is present, so an app that doesn't use Material 3 should pass
     * [systemColors] instead, which tracks dark mode on its own.
     */
    @Composable
    public fun colors(): KalendarColors = MaterialTheme.colorScheme.let { scheme ->
        remember(scheme) { materialColors(scheme) }
    }

    /** Kalendar's built-in [KalendarDimensions]. */
    public fun dimensions(): KalendarDimensions = DefaultDimensions

    /**
     * Kalendar's built-in [KalendarAnimations], with [KalendarAnimations.enabled] answering the
     * platform's reduced-motion setting instead of being hard-coded to `true`.
     *
     * When the person using the device has asked their OS to minimise motion — Android's "Remove
     * animations" or a zero animator duration scale, iOS's Reduce Motion switch, the web's
     * `prefers-reduced-motion` — every transition snaps instead of animating. Desktop JVMs expose
     * no such signal, so there the default always animates. The setting is a default, not a veto:
     * constructing [KalendarAnimations] yourself decides `enabled` explicitly and is never
     * second-guessed.
     *
     * ```kotlin
     * // Follows the platform reduced-motion setting:
     * KalendarTheme { KalendarMonth(selectedDate = today) }
     * // Overrides it — animations stay on regardless:
     * KalendarTheme(animations = KalendarAnimations(enabled = true)) { ... }
     * ```
     */
    @Composable
    public fun animations(): KalendarAnimations =
        if (kalendarPrefersReducedMotion()) ReducedMotionAnimations else DefaultAnimations

    /** Kalendar's built-in [KalendarStrings] — English, and the thing to override to localize. */
    public fun strings(): KalendarStrings = DefaultStrings

    private fun materialColors(scheme: ColorScheme): KalendarColors = KalendarColors(
        background = scheme.surface,
        selectionBackground = scheme.primaryContainer,
        onSelectionBackground = scheme.onPrimaryContainer,
        todayContent = scheme.primary,
        dayContent = scheme.onSurface,
        dayLabelContent = scheme.onSurfaceVariant,
        headerContent = scheme.onSurface,
        eventIndicator = scheme.primary,
        nowIndicator = scheme.error,
        gridLine = scheme.outlineVariant,
        dragGhostBackground = scheme.secondaryContainer,
        popupBackground = scheme.surfaceContainerHigh,
        popupBorder = scheme.outlineVariant,
        agendaCard = scheme.surfaceContainerLow,
    )

    /**
     * Kalendar's own palette for the current system light/dark setting, independent of Material.
     *
     * ```kotlin
     * KalendarTheme(colors = KalendarThemeDefaults.systemColors()) {
     *     KalendarMonth(selectedDate = today)
     * }
     * ```
     */
    @Composable
    public fun systemColors(): KalendarColors =
        if (isSystemInDarkTheme()) {
            darkColors()
        } else {
            lightColors()
        }

    /** Kalendar's built-in light palette. Material-free, so it works in any Compose app. */
    @Suppress("MagicNumber")
    public fun lightColors(): KalendarColors = KalendarColors(
        background = Color(0xFFFFFBFE),
        selectionBackground = Color(0xFFE8DEF8),
        onSelectionBackground = Color(0xFF1D192B),
        todayContent = Color(0xFF6750A4),
        dayContent = Color(0xFF1C1B1F),
        dayLabelContent = Color(0xFF49454F),
        headerContent = Color(0xFF1C1B1F),
        eventIndicator = Color(0xFF6750A4),
        nowIndicator = Color(0xFFB3261E),
        gridLine = Color(0xFFCAC4D0),
        dragGhostBackground = Color(0xFFE8DEF8),
        popupBackground = Color(0xFFECE6F0),
        popupBorder = Color(0xFFCAC4D0),
        agendaCard = Color(0xFFF7F2FA),
    )

    /** Kalendar's built-in dark palette. Material-free, so it works in any Compose app. */
    @Suppress("MagicNumber")
    public fun darkColors(): KalendarColors = KalendarColors(
        background = Color(0xFF1C1B1F),
        selectionBackground = Color(0xFF4F378B),
        onSelectionBackground = Color(0xFFEADDFF),
        todayContent = Color(0xFFD0BCFF),
        dayContent = Color(0xFFE6E1E5),
        dayLabelContent = Color(0xFFCAC4D0),
        headerContent = Color(0xFFE6E1E5),
        eventIndicator = Color(0xFFD0BCFF),
        nowIndicator = Color(0xFFF2B8B5),
        gridLine = Color(0xFF49454F),
        dragGhostBackground = Color(0xFF4A4458),
        popupBackground = Color(0xFF2B2930),
        popupBorder = Color(0xFF49454F),
        agendaCard = Color(0xFF26242A),
    )

    /** [KalendarTypography] resolved from the ambient [MaterialTheme.typography]. */
    @Composable
    public fun typography(): KalendarTypography = MaterialTheme.typography.let { scale ->
        remember(scale) { materialTypography(scale) }
    }

    private fun materialTypography(scale: Typography): KalendarTypography = KalendarTypography(
        headerTitle = scale.titleMedium,
        monthLabel = scale.labelLarge,
        dayOfWeekLabel = scale.labelSmall,
        dayNumber = scale.bodyLarge,
        dayNumberEmphasized = scale.bodyLarge.copy(fontWeight = FontWeight.Bold),
        weekDayNumber = scale.labelLarge,
        resourceTitle = scale.labelLarge,
        eventLabel = scale.labelSmall,
        eventOverflowLabel = scale.labelSmall.copy(fontSize = EVENT_OVERFLOW_FONT_SIZE),
        hourLabel = scale.labelSmall,
        agendaEventTitle = scale.titleSmall,
        agendaEventSubtitle = scale.bodySmall,
    )

    /** Kalendar's built-in [KalendarShapes] — circular day cells, lightly rounded event blocks. */
    public fun shapes(): KalendarShapes = DefaultShapes
}

private val EVENT_OVERFLOW_FONT_SIZE = 9.sp
private val EVENT_BLOCK_CORNER_RADIUS = 4.dp
private val POPUP_CORNER_RADIUS = 12.dp
private val AGENDA_CARD_CORNER_RADIUS = 12.dp

private val DefaultShapes = KalendarShapes(
    dayCell = CircleShape,
    eventIndicator = CircleShape,
    eventBlock = RoundedCornerShape(EVENT_BLOCK_CORNER_RADIUS),
    popup = RoundedCornerShape(POPUP_CORNER_RADIUS),
    agendaCard = RoundedCornerShape(AGENDA_CARD_CORNER_RADIUS),
)
private val DefaultDimensions = KalendarDimensions()
private val DefaultAnimations = KalendarAnimations()

/**
 * [DefaultAnimations] with motion switched off — the instance every default-themed calendar runs
 * on while the platform reports reduced motion. A shared value rather than a per-read `copy`, so
 * the `remember` keyed on the animation tokens sees the same instance on every pass.
 */
private val ReducedMotionAnimations = DefaultAnimations.copy(enabled = false)
private val DefaultStrings = KalendarStrings()
