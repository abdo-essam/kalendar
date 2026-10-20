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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * The reduced-motion contract: [KalendarThemeDefaults.animations] defaults
 * [KalendarAnimations.enabled] to "not reduced motion", while an explicitly constructed
 * [KalendarAnimations] is never second-guessed.
 *
 * Desktop has no platform signal, so every reduced-motion case here drives the seam the platforms
 * feed — [LocalKalendarReducedMotionOverride] — rather than the platform reader itself.
 */
class KalendarReducedMotionTest {

    @get:Rule
    val rule = createComposeRule()

    /** The headline case: the OS asked for reduced motion, so the default animations are off. */
    @Test
    fun defaultAnimations_underReducedMotion_areDisabled() {
        var animations: KalendarAnimations? = null

        rule.setContent {
            ReducedMotion(value = true) {
                animations = KalendarThemeDefaults.animations()
            }
        }
        rule.waitForIdle()

        assertFalse(
            animations.required().enabled,
            "the default animations must honour the platform reduced-motion setting",
        )
    }

    /** Reduced motion flips the one switch the non-animated path reads; it retunes nothing. */
    @Test
    fun defaultAnimations_underReducedMotion_changeOnlyTheEnabledSwitch() {
        var reduced: KalendarAnimations? = null
        var full: KalendarAnimations? = null

        rule.setContent {
            ReducedMotion(value = true) {
                reduced = KalendarThemeDefaults.animations()
            }
            ReducedMotion(value = false) {
                full = KalendarThemeDefaults.animations()
            }
        }
        rule.waitForIdle()

        assertEquals(full.required().pressScale, reduced.required().pressScale)
        assertEquals(full.required().titleTransitionMillis, reduced.required().titleTransitionMillis)
        assertSame(full.required().selectionColorSpec, reduced.required().selectionColorSpec)
        assertSame(full.required().selectionIndicatorSpec, reduced.required().selectionIndicatorSpec)
        assertSame(full.required().pressScaleSpec, reduced.required().pressScaleSpec)
        assertSame(full.required().eventIndicatorFadeSpec, reduced.required().eventIndicatorFadeSpec)
    }

    /** No reduced-motion request means the animated defaults, unchanged. */
    @Test
    fun defaultAnimations_withoutReducedMotion_stayEnabled() {
        var animations: KalendarAnimations? = null

        rule.setContent {
            ReducedMotion(value = false) {
                animations = KalendarThemeDefaults.animations()
            }
        }
        rule.waitForIdle()

        assertTrue(animations.required().enabled)
    }

    /**
     * Desktop's platform reader reports no preference — there is no reliable JVM signal — so with
     * nothing overridden the defaults animate. This pins the documented desktop behaviour.
     */
    @Test
    fun defaultAnimations_onDesktop_treatTheMissingSignalAsMotionAllowed() {
        var animations: KalendarAnimations? = null

        rule.setContent {
            animations = KalendarThemeDefaults.animations()
        }
        rule.waitForIdle()

        assertTrue(
            animations.required().enabled,
            "desktop has no reduced-motion signal, so the default must keep animating",
        )
    }

    /**
     * The precedence rule: the system setting is a default, not a veto. A consumer who constructs
     * `KalendarAnimations(enabled = true)` keeps animations even under reduced motion.
     */
    @Test
    fun explicitlyEnabledAnimations_underReducedMotion_stillWin() {
        var resolved: KalendarAnimations? = null

        rule.setContent {
            ReducedMotion(value = true) {
                KalendarTheme(animations = KalendarAnimations(enabled = true)) {
                    resolved = KalendarTheme.animations
                }
            }
        }
        rule.waitForIdle()

        assertTrue(
            resolved.required().enabled,
            "an explicitly constructed KalendarAnimations must not be vetoed by the system setting",
        )
    }

    /** The default reaches views the two ways they read it: through a theme, and outside any. */
    @Test
    fun reducedMotionDefault_flowsToViews_insideAndOutsideATheme() {
        var themed: KalendarAnimations? = null
        var unthemed: KalendarAnimations? = null

        rule.setContent {
            ReducedMotion(value = true) {
                KalendarTheme {
                    themed = KalendarTheme.animations
                }
                unthemed = KalendarTheme.animations
            }
        }
        rule.waitForIdle()

        assertFalse(
            themed.required().enabled,
            "a theme built on defaults must inherit the reduced-motion answer",
        )
        assertFalse(
            unthemed.required().enabled,
            "a view outside any theme must inherit the reduced-motion answer",
        )
    }

    /**
     * The Android and iOS readers feed snapshot state, so the setting flipping mid-run recomposes
     * the default. The seam feeds the same resolver, which is what this exercises.
     */
    @Test
    fun reducedMotionFlip_whileComposed_updatesTheDefault() {
        val seen = mutableListOf<Boolean>()
        var reducedMotion by mutableStateOf(true)

        rule.setContent {
            ReducedMotion(value = reducedMotion) {
                seen += KalendarThemeDefaults.animations().enabled
            }
        }
        rule.waitForIdle()
        rule.runOnIdle { reducedMotion = false }
        rule.waitForIdle()

        assertEquals(false, seen.first(), "the first pass must see reduced motion")
        assertEquals(true, seen.last(), "flipping the setting off must re-enable the default")
    }

    /**
     * Both answers must resolve to shared instances: the default bundle is remembered keyed on the
     * animations, and a fresh allocation per read would rebuild it every pass.
     */
    @Test
    fun defaultAnimations_areSharedInstances_acrossReads() {
        var first: KalendarAnimations? = null
        var second: KalendarAnimations? = null

        rule.setContent {
            ReducedMotion(value = true) {
                first = KalendarThemeDefaults.animations()
                second = KalendarThemeDefaults.animations()
            }
        }
        rule.waitForIdle()

        assertSame(
            first.required(),
            second.required(),
            "the reduced-motion default must be one shared instance, not a fresh copy per read",
        )
    }

    @Composable
    private fun ReducedMotion(value: Boolean, content: @Composable () -> Unit) {
        CompositionLocalProvider(
            LocalKalendarReducedMotionOverride provides value,
            content = content,
        )
    }
}

/**
 * Fails loudly rather than passing vacuously if a probe never ran — a captured `null` would
 * otherwise satisfy `assertFalse` comparisons built on safe calls.
 */
private fun KalendarAnimations?.required(): KalendarAnimations =
    checkNotNull(this) { "the probe never composed, so there is nothing to assert on" }
