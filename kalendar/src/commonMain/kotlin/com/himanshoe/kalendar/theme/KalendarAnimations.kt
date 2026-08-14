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

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color

/**
 * Motion settings for every Kalendar view.
 *
 * Set [enabled] to `false` to make every transition instant — the right response to a platform
 * reduced-motion preference, and what screenshot tests want:
 *
 * ```kotlin
 * KalendarTheme(animations = KalendarAnimations(enabled = false)) { ... }
 * ```
 *
 * @property enabled Master switch. When `false`, every animation below snaps to its target and
 *   [pressScale] is ignored.
 * @property selectionColorSpec Drives the day cell's selection fill fading in and out.
 * @property selectionIndicatorSpec Drives the indicator that slides between selected day cells.
 * @property pressScale Scale a day cell shrinks to while pressed. `1f` disables the effect.
 * @property pressScaleSpec Drives the [pressScale] animation.
 * @property titleTransitionMillis Duration of the header/sticky-title crossfade.
 */
@Immutable
public class KalendarAnimations(
    public val enabled: Boolean = true,
    public val selectionColorSpec: AnimationSpec<Color> = spring(),
    public val selectionIndicatorSpec: AnimationSpec<Offset> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessHigh,
    ),
    public val pressScale: Float = DEFAULT_PRESS_SCALE,
    public val pressScaleSpec: AnimationSpec<Float> = spring(),
    public val titleTransitionMillis: Int = DEFAULT_TITLE_TRANSITION_MS,
) {

    /** Returns a duplicate of these motion settings with only the values passed here replaced. */
    public fun copy(
        enabled: Boolean = this.enabled,
        selectionColorSpec: AnimationSpec<Color> = this.selectionColorSpec,
        selectionIndicatorSpec: AnimationSpec<Offset> = this.selectionIndicatorSpec,
        pressScale: Float = this.pressScale,
        pressScaleSpec: AnimationSpec<Float> = this.pressScaleSpec,
        titleTransitionMillis: Int = this.titleTransitionMillis,
    ): KalendarAnimations = KalendarAnimations(
        enabled = enabled,
        selectionColorSpec = selectionColorSpec,
        selectionIndicatorSpec = selectionIndicatorSpec,
        pressScale = pressScale,
        pressScaleSpec = pressScaleSpec,
        titleTransitionMillis = titleTransitionMillis,
    )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is KalendarAnimations) return false
        if (enabled != other.enabled) return false
        if (selectionColorSpec != other.selectionColorSpec) return false
        if (selectionIndicatorSpec != other.selectionIndicatorSpec) return false
        if (pressScale != other.pressScale) return false
        if (pressScaleSpec != other.pressScaleSpec) return false
        if (titleTransitionMillis != other.titleTransitionMillis) return false
        return true
    }

    override fun hashCode(): Int {
        var result = enabled.hashCode()
        result = 31 * result + selectionColorSpec.hashCode()
        result = 31 * result + selectionIndicatorSpec.hashCode()
        result = 31 * result + pressScale.hashCode()
        result = 31 * result + pressScaleSpec.hashCode()
        result = 31 * result + titleTransitionMillis.hashCode()
        return result
    }

    override fun toString(): String = "KalendarAnimations(" +
        "enabled=$enabled, " +
        "selectionColorSpec=$selectionColorSpec, " +
        "selectionIndicatorSpec=$selectionIndicatorSpec, " +
        "pressScale=$pressScale, " +
        "pressScaleSpec=$pressScaleSpec, " +
        "titleTransitionMillis=$titleTransitionMillis" +
        ")"
}

private const val DEFAULT_PRESS_SCALE = 0.92f
private const val DEFAULT_TITLE_TRANSITION_MS = 150
