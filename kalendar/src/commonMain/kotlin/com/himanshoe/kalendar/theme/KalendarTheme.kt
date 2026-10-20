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

    /**
     * All ambient token sets bundled together — the value to `copy` when overriding a single
     * calendar via `KalendarTheme(tokens = …)`.
     *
     * Reading this outside a [KalendarTheme] yields [KalendarThemeDefaults], and yields the *same*
     * bundle on every recomposition, so it is safe to use as the base of a `copy` in a hot path.
     */
    public val tokens: KalendarThemeTokens
        @Composable get() {
            val ambient = LocalKalendarTokens.current
            return if (ambient != null) {
                ambient
            } else {
                defaultKalendarTokens()
            }
        }
}

/**
 * The bundle a calendar composed outside any [KalendarTheme] runs on.
 *
 * Remembered rather than rebuilt: this is the default that every unthemed call site lands on, and
 * an unstable bundle there would recompose the calendar under it on every pass. Every set comes
 * from [KalendarThemeDefaults], which returns shared instances, so only the two Material-derived
 * sets and the reduced-motion answer inside the animations can invalidate it.
 */
@Composable
private fun defaultKalendarTokens(): KalendarThemeTokens {
    val colors = KalendarThemeDefaults.colors()
    val typography = KalendarThemeDefaults.typography()
    val animations = KalendarThemeDefaults.animations()
    return remember(colors, typography, animations) {
        KalendarThemeTokens(
            colors = colors,
            typography = typography,
            shapes = KalendarThemeDefaults.shapes(),
            dimensions = KalendarThemeDefaults.dimensions(),
            animations = animations,
            strings = KalendarThemeDefaults.strings(),
        )
    }
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
 * Each parameter defaults to the ambient value, so this nests: an inner `KalendarTheme` overriding
 * `dimensions` keeps the outer one's colours. To restyle a single calendar from a bundle you
 * already hold, use the `KalendarTheme(tokens = …)` overload instead.
 *
 * @param colors Colour tokens. Defaults to [KalendarThemeDefaults.colors], which follows the
 *   ambient Material theme; pass [KalendarThemeDefaults.systemColors] in a non-Material app.
 * @param typography Text styles. Defaults to [KalendarThemeDefaults.typography].
 * @param shapes Corner shapes. Defaults to [KalendarThemeDefaults.shapes].
 * @param dimensions Sizing and spacing. Defaults to [KalendarDimensions]'s own defaults.
 * @param animations Motion settings. The default follows the platform's reduced-motion setting
 *   ([KalendarThemeDefaults.animations]); constructing [KalendarAnimations] yourself overrides
 *   that — pass `KalendarAnimations(enabled = false)` for deterministic screenshot tests.
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
    KalendarTheme(tokens = tokens, content = content)
}

/**
 * Applies a prepared [KalendarThemeTokens] to [content] — the per-call-site override.
 *
 * Where the [KalendarTheme] overload above is for dressing a whole app, this one is for "this one
 * calendar is different": build the bundle from [KalendarTheme.tokens] and `copy` only what
 * changes, and everything else keeps coming from the surrounding theme.
 *
 * ```kotlin
 * KalendarTheme(colors = appCalendarColors) {
 *     KalendarMonth(selectedDate = today)
 *     // Compact, but still in the app's colours.
 *     KalendarTheme(tokens = KalendarTheme.tokens.copy(dimensions = KalendarDimensions(hourHeight = 32.dp))) {
 *         KalendarDatePicker(selectedDates = picked, onDateClick = ::pick)
 *     }
 * }
 * ```
 *
 * The override is scoped to [content], so it wins over the enclosing theme and is invisible to
 * siblings. Nesting composes in the obvious direction: the innermost `copy` wins, and each level
 * above it supplies whatever that level did not touch.
 *
 * @param tokens The bundle to apply, or `null` to inherit the surrounding theme untouched. `null`
 *   passes the ambient bundle through by identity, which is what makes it the right default for a
 *   `theme`-style parameter on a view: not overriding costs nothing and changes nothing.
 * @param content The calendar views these tokens apply to.
 */
@Composable
public fun KalendarTheme(
    tokens: KalendarThemeTokens?,
    content: @Composable () -> Unit,
) {
    // Provided unconditionally, even for a null override. Wrapping conditionally would give the
    // content a different group shape in the two cases, so a caller toggling an override on and off
    // would discard the calendar's remembered state along with it.
    CompositionLocalProvider(
        LocalKalendarTokens provides (tokens ?: LocalKalendarTokens.current),
        content = content,
    )
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

/**
 * The effective [KalendarAnimations]: the ambient [KalendarTheme]'s, or the built-in defaults.
 *
 * Not `@ReadOnlyComposable`, unlike its siblings: the default branch reads the platform
 * reduced-motion setting, which some platforms observe with effects.
 */
@Composable
internal fun kalendarAnimations(): KalendarAnimations =
    LocalKalendarTokens.current?.animations ?: KalendarThemeDefaults.animations()

/** The effective [KalendarStrings]: the ambient [KalendarTheme]'s, or the English defaults. */
@Composable
@ReadOnlyComposable
internal fun kalendarStrings(): KalendarStrings =
    LocalKalendarTokens.current?.strings ?: KalendarThemeDefaults.strings()
