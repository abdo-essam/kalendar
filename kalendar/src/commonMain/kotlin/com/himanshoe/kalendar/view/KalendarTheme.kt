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

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Colour tokens for every Kalendar view, replacing the built-in Material-derived palette.
 *
 * Obtain a Material-matching instance from [KalendarThemeDefaults.colors] and `copy` the tokens
 * you want to change, or construct one from scratch for non-Material apps:
 *
 * ```kotlin
 * KalendarTheme(
 *     colors = KalendarThemeDefaults.colors().copy(
 *         selectionBackground = BrandPurple,
 *         nowIndicator = BrandRed,
 *     ),
 * ) {
 *     KalendarMonth(selectedDate = today)
 * }
 * ```
 *
 * @property selectionBackground Fill of the selected day's circle and the sliding indicator.
 * @property onSelectionBackground Content colour on top of [selectionBackground].
 * @property todayContent Day-number colour for today's cell when it isn't selected.
 * @property dayContent Day-number colour for ordinary cells.
 * @property dayLabelContent Colour of the day-of-week column labels and hour-gutter labels.
 * @property eventIndicator Default event dot/block accent when
 *   [KalendarEvent.eventColor][com.himanshoe.kalendar.foundation.event.KalendarEvent.eventColor]
 *   is `null`.
 * @property nowIndicator The current-time line on [KalendarSchedule].
 * @property gridLine Hour lines on [KalendarSchedule].
 * @property dragGhostBackground Fill of the drop-target ghost while dragging an event.
 */
@Immutable
public data class KalendarColors(
    val selectionBackground: Color,
    val onSelectionBackground: Color,
    val todayContent: Color,
    val dayContent: Color,
    val dayLabelContent: Color,
    val eventIndicator: Color,
    val nowIndicator: Color,
    val gridLine: Color,
    val dragGhostBackground: Color,
)

/** Defaults for [KalendarTheme]. */
public object KalendarThemeDefaults {

    /** [KalendarColors] resolved from the ambient [MaterialTheme.colorScheme]. */
    @Composable
    public fun colors(): KalendarColors = MaterialTheme.colorScheme.let { scheme ->
        KalendarColors(
            selectionBackground = scheme.primaryContainer,
            onSelectionBackground = scheme.onPrimaryContainer,
            todayContent = scheme.primary,
            dayContent = scheme.onSurface,
            dayLabelContent = scheme.onSurfaceVariant,
            eventIndicator = scheme.primary,
            nowIndicator = scheme.error,
            gridLine = scheme.outlineVariant,
            dragGhostBackground = scheme.secondaryContainer,
        )
    }
}

internal val LocalKalendarColors = compositionLocalOf<KalendarColors?> { null }

/**
 * Provides [colors] to every Kalendar view inside [content], overriding the Material-derived
 * defaults. Wrapping is optional — views used outside a [KalendarTheme] fall back to
 * [KalendarThemeDefaults.colors].
 */
@Composable
public fun KalendarTheme(
    colors: KalendarColors = KalendarThemeDefaults.colors(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalKalendarColors provides colors, content = content)
}

/** The effective [KalendarColors]: the ambient [KalendarTheme]'s, or Material-derived defaults. */
@Composable
internal fun kalendarColors(): KalendarColors = LocalKalendarColors.current ?: KalendarThemeDefaults.colors()
