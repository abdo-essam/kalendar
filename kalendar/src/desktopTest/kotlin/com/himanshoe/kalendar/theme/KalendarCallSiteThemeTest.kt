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
import androidx.compose.runtime.NonRestartableComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Rule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * The per-call-site override contract: `KalendarTheme(tokens = …)`.
 *
 * Every assertion here reads the *individual* ambient accessors rather than the bundle that was
 * provided, because that is what the views and `component/` read — 147 call sites of
 * `KalendarTheme.colors`, `kalendarDimensions()` and friends. Asserting on the bundle would pass
 * even if an accessor had been wired to something else.
 */
class KalendarCallSiteThemeTest {

    @get:Rule
    val rule = createComposeRule()

    /**
     * The headline case: restyle one calendar without restating the other five token sets, and
     * without losing the surrounding theme.
     */
    @Test
    fun callSiteOverride_replacesOneTokenSet_andInheritsTheRestFromTheAmbientTheme() {
        var inner: KalendarThemeTokens? = null

        rule.setContent {
            KalendarTheme(colors = brandColors(), strings = brandStrings()) {
                KalendarTheme(tokens = KalendarTheme.tokens.copy(dimensions = compactDimensions())) {
                    AmbientProbe { inner = it }
                }
            }
        }
        rule.waitForIdle()

        assertEquals(
            COMPACT_HOUR_HEIGHT,
            inner.required().dimensions.hourHeight,
            "the overridden token set must win at the call site",
        )
        assertEquals(
            BRAND_SELECTION,
            inner.required().colors.selectionBackground,
            "colors were not overridden, so the ambient theme's must survive",
        )
        assertEquals(
            BRAND_TODAY,
            inner.required().strings.today,
            "strings were not overridden, so the ambient theme's must survive",
        )
    }

    /** An override is scoped to its own content, not to everything after it. */
    @Test
    fun callSiteOverride_doesNotLeakToSiblings() {
        var overridden: KalendarThemeTokens? = null
        var sibling: KalendarThemeTokens? = null

        rule.setContent {
            KalendarTheme(colors = brandColors()) {
                KalendarTheme(tokens = KalendarTheme.tokens.copy(dimensions = compactDimensions())) {
                    AmbientProbe { overridden = it }
                }
                AmbientProbe { sibling = it }
            }
        }
        rule.waitForIdle()

        assertEquals(COMPACT_HOUR_HEIGHT, overridden.required().dimensions.hourHeight)
        assertEquals(
            KalendarThemeDefaults.dimensions().hourHeight,
            sibling.required().dimensions.hourHeight,
            "a sibling of an overridden calendar must still see the ambient theme",
        )
        assertEquals(
            BRAND_SELECTION,
            sibling.required().colors.selectionBackground,
            "the sibling must still see the ambient theme's colours",
        )
    }

    /**
     * `null` means "inherit", and inherits by identity — this is the state a view's `theme`
     * parameter sits in for every caller that does not override, so it has to cost nothing and
     * change nothing.
     */
    @Test
    fun callSiteOverride_null_passesTheAmbientThemeThroughUnchanged() {
        var outer: KalendarThemeTokens? = null
        var inner: KalendarThemeTokens? = null
        var outerBundle: KalendarThemeTokens? = null
        var innerBundle: KalendarThemeTokens? = null

        rule.setContent {
            KalendarTheme(colors = brandColors(), dimensions = compactDimensions()) {
                outerBundle = KalendarTheme.tokens
                AmbientProbe { outer = it }
                KalendarTheme(tokens = null) {
                    innerBundle = KalendarTheme.tokens
                    AmbientProbe { inner = it }
                }
            }
        }
        rule.waitForIdle()

        assertEquals(
            outer.required(),
            inner.required(),
            "a null override must resolve to exactly the ambient theme",
        )
        assertSame(
            outerBundle.required(),
            innerBundle.required(),
            "a null override must pass the ambient bundle through without re-wrapping it",
        )
    }

    /** Overrides compose: the innermost one wins, and the ones above it still fill the gaps. */
    @Test
    fun callSiteOverride_nested_innermostWins() {
        var inner: KalendarThemeTokens? = null

        rule.setContent {
            KalendarTheme(colors = brandColors()) {
                KalendarTheme(
                    tokens = KalendarTheme.tokens.copy(
                        dimensions = compactDimensions(),
                        strings = brandStrings(),
                    ),
                ) {
                    KalendarTheme(tokens = KalendarTheme.tokens.copy(dimensions = roomyDimensions())) {
                        AmbientProbe { inner = it }
                    }
                }
            }
        }
        rule.waitForIdle()

        assertEquals(
            ROOMY_HOUR_HEIGHT,
            inner.required().dimensions.hourHeight,
            "the innermost override must win over the outer one",
        )
        assertEquals(
            BRAND_TODAY,
            inner.required().strings.today,
            "the middle override must still supply what the innermost did not touch",
        )
        assertEquals(
            BRAND_SELECTION,
            inner.required().colors.selectionBackground,
            "the outermost theme must still supply what neither override touched",
        )
    }

    /** An override with no `KalendarTheme` above it still gets defaults for everything else. */
    @Test
    fun callSiteOverride_outsideAnyTheme_suppliesDefaultsForEverythingNotOverridden() {
        var probed: KalendarThemeTokens? = null

        rule.setContent {
            KalendarTheme(tokens = KalendarTheme.tokens.copy(dimensions = compactDimensions())) {
                AmbientProbe { probed = it }
            }
        }
        rule.waitForIdle()

        assertEquals(COMPACT_HOUR_HEIGHT, probed.required().dimensions.hourHeight)
        assertSame(
            KalendarThemeDefaults.strings(),
            probed.required().strings,
            "an override outside a theme must fall back to the built-in strings",
        )
        assertSame(
            KalendarThemeDefaults.animations(),
            probed.required().animations,
            "an override outside a theme must fall back to the built-in animations",
        )
        assertEquals(
            KalendarThemeDefaults.shapes(),
            probed.required().shapes,
            "an override outside a theme must fall back to the built-in shapes",
        )
    }

    /**
     * The two entry points must be interchangeable, or the wrapper and the call-site parameter
     * would drift into meaning different things.
     */
    @Test
    fun callSiteOverride_andTheWrapper_produceTheSameAmbientTokens() {
        var viaWrapper: KalendarThemeTokens? = null
        var viaTokens: KalendarThemeTokens? = null

        rule.setContent {
            KalendarTheme(colors = brandColors(), dimensions = compactDimensions()) {
                AmbientProbe { viaWrapper = it }
            }
            KalendarTheme(
                tokens = KalendarTheme.tokens.copy(
                    colors = brandColors(),
                    dimensions = compactDimensions(),
                ),
            ) {
                AmbientProbe { viaTokens = it }
            }
        }
        rule.waitForIdle()

        assertEquals(viaWrapper.required(), viaTokens.required())
    }

    /**
     * The override has to survive a recomposition driven by unrelated state. A provider rebuilt
     * from a value that compares unequal each pass would re-provide a static local every frame.
     */
    @Test
    fun callSiteOverride_survivesRecomposition() {
        val seen = mutableListOf<KalendarThemeTokens>()
        var trigger by mutableStateOf(0)

        rule.setContent {
            KalendarTheme(colors = brandColors()) {
                KalendarTheme(tokens = KalendarTheme.tokens.copy(dimensions = compactDimensions())) {
                    trigger.let { AmbientProbe { probed -> seen += probed } }
                }
            }
        }
        rule.runOnIdle { trigger = 1 }
        rule.waitForIdle()

        assertTrue(seen.size >= 2, "the probe must have recomposed at least once")
        assertEquals(COMPACT_HOUR_HEIGHT, seen.last().dimensions.hourHeight)
        assertEquals(BRAND_SELECTION, seen.last().colors.selectionBackground)
    }

    /** Guards the probe itself: it must be able to tell the two dimension sets apart. */
    @Test
    fun probeFixture_distinguishesTheTokenSetsUnderTest() {
        assertNotEquals(COMPACT_HOUR_HEIGHT, KalendarThemeDefaults.dimensions().hourHeight)
        assertNotEquals(ROOMY_HOUR_HEIGHT, COMPACT_HOUR_HEIGHT)
        assertNotEquals(BRAND_TODAY, KalendarThemeDefaults.strings().today)
    }

    @Test
    fun ambientTokens_outsideATheme_areTheSameInstanceAcrossRecompositions() {
        val seen = mutableListOf<KalendarThemeTokens>()
        var trigger by mutableStateOf(0)

        rule.setContent {
            trigger.let { seen += KalendarTheme.tokens }
        }
        rule.runOnIdle { trigger = 1 }
        rule.waitForIdle()

        assertTrue(seen.size >= 2, "the probe must have recomposed at least once")
        assertSame(
            seen.first(),
            seen.last(),
            "KalendarTheme.tokens must not allocate a fresh bundle on every recomposition",
        )
    }

    @Test
    fun ambientTokens_outsideATheme_reuseTheSharedDefaults() {
        var tokens: KalendarThemeTokens? = null
        rule.setContent { tokens = KalendarTheme.tokens }
        rule.waitForIdle()

        assertSame(
            KalendarThemeDefaults.dimensions(),
            tokens.required().dimensions,
            "the default bundle must reuse KalendarThemeDefaults.dimensions()",
        )
        assertSame(
            KalendarThemeDefaults.animations(),
            tokens.required().animations,
            "the default bundle must reuse KalendarThemeDefaults.animations()",
        )
        assertSame(
            KalendarThemeDefaults.strings(),
            tokens.required().strings,
            "the default bundle must reuse KalendarThemeDefaults.strings()",
        )
    }

    private fun brandColors(): KalendarColors =
        KalendarThemeDefaults.lightColors().copy(selectionBackground = BRAND_SELECTION)

    private fun brandStrings(): KalendarStrings =
        KalendarThemeDefaults.strings().copy(today = BRAND_TODAY)

    private fun compactDimensions(): KalendarDimensions =
        KalendarThemeDefaults.dimensions().copy(hourHeight = COMPACT_HOUR_HEIGHT)

    private fun roomyDimensions(): KalendarDimensions =
        KalendarThemeDefaults.dimensions().copy(hourHeight = ROOMY_HOUR_HEIGHT)

    private companion object {
        val BRAND_SELECTION = Color.Magenta
        const val BRAND_TODAY = "Heute"
        val COMPACT_HOUR_HEIGHT = 21.dp
        val ROOMY_HOUR_HEIGHT = 137.dp
    }
}

/**
 * Fails loudly rather than passing vacuously if the probe never ran — a captured `null` compared
 * against another captured `null` would otherwise satisfy every assertion in this file.
 */
private fun KalendarThemeTokens?.required(): KalendarThemeTokens =
    checkNotNull(this) { "the probe never composed, so there is nothing to assert on" }

/**
 * Reads each ambient accessor separately and bundles the six results, so an assertion on the bundle
 * is an assertion on every accessor a Kalendar view actually calls.
 *
 * Non-restartable on purpose: a probe that skipped would report the tokens from whichever pass last
 * bothered to run it, which is precisely what the recomposition test is trying to measure.
 */
@Composable
@NonRestartableComposable
private fun AmbientProbe(onResolved: (KalendarThemeTokens) -> Unit) {
    onResolved(
        KalendarThemeTokens(
            colors = KalendarTheme.colors,
            typography = KalendarTheme.typography,
            shapes = KalendarTheme.shapes,
            dimensions = KalendarTheme.dimensions,
            animations = KalendarTheme.animations,
            strings = KalendarTheme.strings,
        ),
    )
}
