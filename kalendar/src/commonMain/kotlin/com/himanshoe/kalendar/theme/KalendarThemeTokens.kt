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

import androidx.compose.runtime.Immutable

/**
 * Every Kalendar token set in one value, so a whole look can be passed around, `copy`-ed, and
 * applied to a single calendar rather than to a whole app.
 *
 * Read the ambient instance with [KalendarTheme.tokens] and apply a modified copy with the
 * `KalendarTheme(tokens = …)` overload to restyle just one calendar. `copy` is what keeps the
 * override to the tokens you actually changed — the other five sets come through from the
 * surrounding theme:
 *
 * ```kotlin
 * KalendarTheme(
 *     tokens = KalendarTheme.tokens.copy(
 *         colors = KalendarTheme.colors.copy(selectionBackground = BrandPurple),
 *     ),
 * ) {
 *     KalendarMonth(selectedDate = today)
 * }
 * ```
 *
 * @property colors Colour tokens. See [KalendarColors].
 * @property typography Text styles. See [KalendarTypography].
 * @property shapes Corner shapes. See [KalendarShapes].
 * @property dimensions Sizing and spacing. See [KalendarDimensions].
 * @property animations Motion settings. See [KalendarAnimations].
 * @property strings Localizable text and accessibility labels. See [KalendarStrings].
 */
@Immutable
public class KalendarThemeTokens(
    public val colors: KalendarColors,
    public val typography: KalendarTypography,
    public val shapes: KalendarShapes,
    public val dimensions: KalendarDimensions,
    public val animations: KalendarAnimations,
    public val strings: KalendarStrings,
) {

    /** Returns a duplicate of this token set with only the token sets passed here replaced. */
    public fun copy(
        colors: KalendarColors = this.colors,
        typography: KalendarTypography = this.typography,
        shapes: KalendarShapes = this.shapes,
        dimensions: KalendarDimensions = this.dimensions,
        animations: KalendarAnimations = this.animations,
        strings: KalendarStrings = this.strings,
    ): KalendarThemeTokens = KalendarThemeTokens(
        colors = colors,
        typography = typography,
        shapes = shapes,
        dimensions = dimensions,
        animations = animations,
        strings = strings,
    )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is KalendarThemeTokens) return false
        if (colors != other.colors) return false
        if (typography != other.typography) return false
        if (shapes != other.shapes) return false
        if (dimensions != other.dimensions) return false
        if (animations != other.animations) return false
        if (strings != other.strings) return false
        return true
    }

    override fun hashCode(): Int {
        var result = colors.hashCode()
        result = 31 * result + typography.hashCode()
        result = 31 * result + shapes.hashCode()
        result = 31 * result + dimensions.hashCode()
        result = 31 * result + animations.hashCode()
        result = 31 * result + strings.hashCode()
        return result
    }

    override fun toString(): String = "KalendarThemeTokens(" +
        "colors=$colors, " +
        "typography=$typography, " +
        "shapes=$shapes, " +
        "dimensions=$dimensions, " +
        "animations=$animations, " +
        "strings=$strings" +
        ")"
}
