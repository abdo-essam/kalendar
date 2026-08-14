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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf

internal val LocalKalendarTokens = staticCompositionLocalOf<KalendarThemeTokens?> { null }

/**
 * Accessors for the ambient Kalendar tokens, mirroring how `MaterialTheme.colorScheme` works.
 *
 * Outside a [KalendarTheme] these fall back to [KalendarThemeDefaults], so reading them is always
 * safe.
 */
public object KalendarTheme {

    /** The ambient [KalendarColors]. */
    public val colors: KalendarColors
        @Composable get() = kalendarColors()

    /** The ambient [KalendarTypography]. */
    public val typography: KalendarTypography
        @Composable get() = kalendarTypography()

    /** The ambient [KalendarShapes]. */
    public val shapes: KalendarShapes
        @Composable get() = kalendarShapes()

    /** The ambient [KalendarDimensions]. */
    public val dimensions: KalendarDimensions
        @Composable get() = kalendarDimensions()

    /** The ambient [KalendarAnimations]. */
    public val animations: KalendarAnimations
        @Composable get() = kalendarAnimations()

    /** The ambient [KalendarStrings]. */
    public val strings: KalendarStrings
        @Composable get() = kalendarStrings()

    /** All ambient token sets bundled together, ready to `copy` into a view's `theme` parameter. */
    public val tokens: KalendarThemeTokens
        @Composable get() = LocalKalendarTokens.current ?: KalendarThemeTokens(
            colors = KalendarThemeDefaults.colors(),
            typography = KalendarThemeDefaults.typography(),
            shapes = KalendarThemeDefaults.shapes(),
            dimensions = KalendarDimensions(),
            animations = KalendarAnimations(),
            strings = KalendarStrings(),
        )
}

/**
 * Provides colours, type, shapes, sizing, and motion to every Kalendar view inside [content].
 *
 * Wrapping is optional — views used outside a [KalendarTheme] fall back to [KalendarThemeDefaults]
 * — and each token set is independent, so overriding one leaves the rest at their defaults:
 *
 * ```kotlin
 * KalendarTheme(
 *     colors = KalendarThemeDefaults.colors().copy(selectionBackground = BrandPurple),
 *     shapes = KalendarShapes(dayCell = RoundedCornerShape(8.dp), ...),
 *     dimensions = KalendarDimensions(hourHeight = 96.dp),
 * ) {
 *     KalendarMonth(selectedDate = today)
 * }
 * ```
 *
 * @param colors Colour tokens. Defaults to [KalendarThemeDefaults.colors], which follows the
 *   ambient Material theme; pass [KalendarThemeDefaults.systemColors] in a non-Material app.
 * @param typography Text styles. Defaults to [KalendarThemeDefaults.typography].
 * @param shapes Corner shapes. Defaults to [KalendarThemeDefaults.shapes].
 * @param dimensions Sizing and spacing. Defaults to [KalendarDimensions]'s own defaults.
 * @param animations Motion settings. Pass `KalendarAnimations(enabled = false)` for reduced
 *   motion or deterministic screenshot tests.
 * @param content The calendar views this theme applies to.
 */
@Composable
public fun KalendarTheme(
    colors: KalendarColors = KalendarTheme.colors,
    typography: KalendarTypography = KalendarTheme.typography,
    shapes: KalendarShapes = KalendarTheme.shapes,
    dimensions: KalendarDimensions = KalendarTheme.dimensions,
    animations: KalendarAnimations = KalendarTheme.animations,
    strings: KalendarStrings = KalendarTheme.strings,
    content: @Composable () -> Unit,
) {
    val tokens = remember(colors, typography, shapes, dimensions, animations, strings) {
        KalendarThemeTokens(
            colors = colors,
            typography = typography,
            shapes = shapes,
            dimensions = dimensions,
            animations = animations,
            strings = strings,
        )
    }
    CompositionLocalProvider(LocalKalendarTokens provides tokens, content = content)
}

/** The effective [KalendarColors]: the ambient [KalendarTheme]'s, or Material-derived defaults. */
@Composable
internal fun kalendarColors(): KalendarColors =
    LocalKalendarTokens.current?.colors ?: KalendarThemeDefaults.colors()

/** The effective [KalendarTypography]: the ambient [KalendarTheme]'s, or Material-derived defaults. */
@Composable
internal fun kalendarTypography(): KalendarTypography =
    LocalKalendarTokens.current?.typography ?: KalendarThemeDefaults.typography()

/** The effective [KalendarShapes]: the ambient [KalendarTheme]'s, or Kalendar's built-in shapes. */
@Composable
@ReadOnlyComposable
internal fun kalendarShapes(): KalendarShapes =
    LocalKalendarTokens.current?.shapes ?: KalendarThemeDefaults.shapes()

/** The effective [KalendarDimensions]: the ambient [KalendarTheme]'s, or the built-in defaults. */
@Composable
@ReadOnlyComposable
internal fun kalendarDimensions(): KalendarDimensions =
    LocalKalendarTokens.current?.dimensions ?: KalendarThemeDefaults.dimensions()

/** The effective [KalendarAnimations]: the ambient [KalendarTheme]'s, or the built-in defaults. */
@Composable
@ReadOnlyComposable
internal fun kalendarAnimations(): KalendarAnimations =
    LocalKalendarTokens.current?.animations ?: KalendarThemeDefaults.animations()

/** The effective [KalendarStrings]: the ambient [KalendarTheme]'s, or the English defaults. */
@Composable
@ReadOnlyComposable
internal fun kalendarStrings(): KalendarStrings =
    LocalKalendarTokens.current?.strings ?: KalendarThemeDefaults.strings()
