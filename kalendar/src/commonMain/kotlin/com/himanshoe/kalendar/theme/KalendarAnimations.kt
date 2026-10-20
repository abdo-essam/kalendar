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
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color

/**
 * Motion settings for every Kalendar view.
 *
 * A platform reduced-motion preference needs nothing from you: [KalendarThemeDefaults.animations],
 * the default every themed and unthemed view lands on, already answers it by switching [enabled]
 * off. Constructing this class yourself replaces that default — whatever you pass for [enabled] is
 * final, so `enabled = true` animates even under reduced motion, and `enabled = false` makes every
 * transition instant, which is what deterministic screenshot tests want:
 *
 * ```kotlin
 * KalendarTheme(animations = KalendarAnimations(enabled = false)) { ... }
 * ```
 *
 * Every spec below is stated in full rather than left to `spring()`'s or `tween()`'s defaults, so
 * that retuning one is a matter of changing a number here and not of discovering what the default
 * was. Two rules of thumb ran through the choices: motion under a finger is springy and
 * interruptible, motion that merely swaps information is a fixed, short, asymmetric fade — quick to
 * leave, unhurried to arrive.
 *
 * @property enabled Master switch. When `false`, every animation below snaps to its target and
 *   [pressScale] is ignored. Constructed directly it defaults to `true`; the theme's default
 *   instance instead follows the platform reduced-motion setting.
 * @property selectionColorSpec Drives the day cell's selection fill fading in and out. Fully damped
 *   and slower than the indicator that slides over it: a fill that overshot would flash a colour
 *   the theme does not contain.
 * @property selectionIndicatorSpec Drives the indicator that slides between selected day cells.
 *   Deliberately not stiffer than this — a spring that settles in much under a tenth of a second
 *   reads as the selection teleporting rather than moving, and the point of a single sliding
 *   indicator is that the eye can follow it from the old cell to the new one.
 * @property pressScale Scale a day cell shrinks to while pressed. `1f` disables the effect.
 * @property pressScaleSpec Drives the [pressScale] animation. The one spec here that bounces: a
 *   press is the only motion a finger is actually resting on, and the small overshoot on release is
 *   what makes the cell feel like a physical button rather than a fading rectangle.
 * @property eventIndicatorFadeSpec Drives the row of event dots below a day number fading in and
 *   out as a day gains or loses events. A spring rather than a duration because events often arrive
 *   from a network in bursts, and a spring interrupted half way carries its velocity into the next
 *   change instead of restarting.
 * @property titleTransitionMillis Total duration of the header/sticky-title fade, out and back in.
 *   Split evenly between the two halves, so each is half of this.
 * @property titleFadeOutEasing Easing of the half that takes the old title away. Accelerating, so
 *   the outgoing title leaves promptly rather than lingering at readable opacity.
 * @property titleFadeInEasing Easing of the half that brings the new title back. Decelerating, the
 *   mirror of [titleFadeOutEasing]: the arriving month name settles rather than snapping in.
 */
@Immutable
@Suppress("LongParameterList")
public class KalendarAnimations(
    public val enabled: Boolean = true,
    public val selectionColorSpec: AnimationSpec<Color> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow,
    ),
    public val selectionIndicatorSpec: AnimationSpec<Offset> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMedium,
    ),
    public val pressScale: Float = DEFAULT_PRESS_SCALE,
    public val pressScaleSpec: AnimationSpec<Float> = spring(
        dampingRatio = Spring.DampingRatioLowBouncy,
        stiffness = Spring.StiffnessMedium,
    ),
    public val eventIndicatorFadeSpec: FiniteAnimationSpec<Float> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow,
    ),
    public val titleTransitionMillis: Int = DEFAULT_TITLE_TRANSITION_MS,
    public val titleFadeOutEasing: Easing = FastOutLinearInEasing,
    public val titleFadeInEasing: Easing = LinearOutSlowInEasing,
) {

    /** Returns a duplicate of these motion settings with only the values passed here replaced. */
    @Suppress("LongParameterList")
    public fun copy(
        enabled: Boolean = this.enabled,
        selectionColorSpec: AnimationSpec<Color> = this.selectionColorSpec,
        selectionIndicatorSpec: AnimationSpec<Offset> = this.selectionIndicatorSpec,
        pressScale: Float = this.pressScale,
        pressScaleSpec: AnimationSpec<Float> = this.pressScaleSpec,
        eventIndicatorFadeSpec: FiniteAnimationSpec<Float> = this.eventIndicatorFadeSpec,
        titleTransitionMillis: Int = this.titleTransitionMillis,
        titleFadeOutEasing: Easing = this.titleFadeOutEasing,
        titleFadeInEasing: Easing = this.titleFadeInEasing,
    ): KalendarAnimations = KalendarAnimations(
        enabled = enabled,
        selectionColorSpec = selectionColorSpec,
        selectionIndicatorSpec = selectionIndicatorSpec,
        pressScale = pressScale,
        pressScaleSpec = pressScaleSpec,
        eventIndicatorFadeSpec = eventIndicatorFadeSpec,
        titleTransitionMillis = titleTransitionMillis,
        titleFadeOutEasing = titleFadeOutEasing,
        titleFadeInEasing = titleFadeInEasing,
    )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is KalendarAnimations) return false
        if (enabled != other.enabled) return false
        if (selectionColorSpec != other.selectionColorSpec) return false
        if (selectionIndicatorSpec != other.selectionIndicatorSpec) return false
        if (pressScale != other.pressScale) return false
        if (pressScaleSpec != other.pressScaleSpec) return false
        if (eventIndicatorFadeSpec != other.eventIndicatorFadeSpec) return false
        if (titleTransitionMillis != other.titleTransitionMillis) return false
        if (titleFadeOutEasing != other.titleFadeOutEasing) return false
        if (titleFadeInEasing != other.titleFadeInEasing) return false
        return true
    }

    override fun hashCode(): Int {
        var result = enabled.hashCode()
        result = 31 * result + selectionColorSpec.hashCode()
        result = 31 * result + selectionIndicatorSpec.hashCode()
        result = 31 * result + pressScale.hashCode()
        result = 31 * result + pressScaleSpec.hashCode()
        result = 31 * result + eventIndicatorFadeSpec.hashCode()
        result = 31 * result + titleTransitionMillis.hashCode()
        result = 31 * result + titleFadeOutEasing.hashCode()
        result = 31 * result + titleFadeInEasing.hashCode()
        return result
    }

    override fun toString(): String = "KalendarAnimations(" +
        "enabled=$enabled, " +
        "selectionColorSpec=$selectionColorSpec, " +
        "selectionIndicatorSpec=$selectionIndicatorSpec, " +
        "pressScale=$pressScale, " +
        "pressScaleSpec=$pressScaleSpec, " +
        "eventIndicatorFadeSpec=$eventIndicatorFadeSpec, " +
        "titleTransitionMillis=$titleTransitionMillis, " +
        "titleFadeOutEasing=$titleFadeOutEasing, " +
        "titleFadeInEasing=$titleFadeInEasing" +
        ")"
}

private const val DEFAULT_PRESS_SCALE = 0.92f

/**
 * Long enough that each half of the fade is a fade rather than a blink.
 *
 * The previous 150ms split into two 75ms halves, which is under the point at which a change in
 * opacity registers as motion at all — the title simply appeared to cut.
 */
private const val DEFAULT_TITLE_TRANSITION_MS = 200
