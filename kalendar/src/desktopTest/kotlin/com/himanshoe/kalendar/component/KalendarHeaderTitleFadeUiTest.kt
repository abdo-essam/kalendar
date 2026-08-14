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

package com.himanshoe.kalendar.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.himanshoe.kalendar.slot.KalendarHeaderScope
import com.himanshoe.kalendar.theme.KalendarTheme
import kotlinx.datetime.LocalDate
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertTrue

private const val HEADER_TAG = "header"
private val HEADER_WIDTH = 300.dp

private const val FIRST_TITLE = "August 2026"
private const val SECOND_TITLE = "September 2026"

@Suppress("MagicNumber")
private val FIXTURE_MONTH_START = LocalDate(2026, 8, 1)

/** Comfortably inside the 100ms fade-out half, and far enough in for the alpha drop to be obvious. */
private const val MID_FADE_MS = 80L

/**
 * How much lighter the title must be mid-fade for the interruption to have proved anything.
 *
 * Measured at 14 (opaque) against 48 at the moment of the interruption, on a 0..127 scale, so this
 * has most of that gap as headroom while still being far outside antialiasing noise.
 */
private const val FADE_EVIDENCE = 20

/** Antialiasing slack when comparing the settled title against the same title never faded. */
private const val SETTLED_TOLERANCE = 8

/**
 * The header title's fade must always end at full opacity — including when the title changes and
 * changes straight back while the fade-out is still running.
 *
 * That case has a bug shaped exactly like a missing `else`: the fade is a two-step
 * `LaunchedEffect(title)`, so changing the title back restarts the effect, which then finds it has
 * nothing to swap and returns — leaving the alpha wherever the cancelled fade-out had dropped it,
 * with nothing that would ever raise it again. The header kept a permanently dimmed month name, and
 * only until the *next* title change hid it. A pager fling that overshoots and settles back is
 * enough to trigger it.
 *
 * Measured in pixels rather than through semantics because alpha is a `graphicsLayer` property and
 * leaves no trace in the semantics tree at all — the node is present and reports its text either
 * way, which is why nothing caught this.
 */
class KalendarHeaderTitleFadeUiTest {

    @get:Rule
    val rule = createComposeRule()

    private class FixedHeaderScope(private val titleOf: () -> String) : KalendarHeaderScope {
        override val title: String get() = titleOf()
        override val visibleDate: LocalDate = FIXTURE_MONTH_START
        override val canScrollBackward: Boolean = true
        override val canScrollForward: Boolean = true
        override fun goToPreviousPage() = Unit
        override fun goToNextPage() = Unit
        override fun goToToday() = Unit
        override fun goTo(date: LocalDate) = Unit
    }

    @Test
    fun title_returnsToFullOpacityWhenItChangesBackMidFade() {
        var title by mutableStateOf(FIRST_TITLE)
        rule.mainClock.autoAdvance = false
        rule.setContent {
            KalendarTheme {
                // White behind it, and no arrows or today button, so the darkest pixel in the
                // captured region is the title's own ink and nothing else's.
                Box(
                    modifier = Modifier
                        .width(HEADER_WIDTH)
                        .background(Color.White)
                        .testTag(HEADER_TAG),
                ) {
                    KalendarHeaderDefaults.Header(
                        scope = FixedHeaderScope { title },
                        showNavigationArrows = false,
                        showTodayButton = false,
                        showJumpPicker = false,
                    )
                }
            }
        }
        rule.mainClock.advanceTimeBy(MID_FADE_MS)
        val opaque = darkestPixel()

        title = SECOND_TITLE
        rule.mainClock.advanceTimeBy(MID_FADE_MS)
        val midFade = darkestPixel()

        title = FIRST_TITLE
        rule.mainClock.autoAdvance = true
        rule.waitForIdle()
        val settled = darkestPixel()

        // Vacuity guard. Unless the title really was faded down at the moment it changed back, the
        // assertion below is about a fade that never happened and would keep passing after the fix
        // was removed.
        assertTrue(
            midFade > opaque + FADE_EVIDENCE,
            "the title was not visibly faded ${MID_FADE_MS}ms into its fade-out (darkest pixel " +
                "$midFade against $opaque at rest), so changing it back proves nothing",
        )
        assertTrue(
            settled <= opaque + SETTLED_TOLERANCE,
            "the title settled at darkest pixel $settled instead of $opaque: the fade-out was " +
                "cancelled by the title changing back and its alpha was never restored",
        )
    }

    /**
     * The luminance of the darkest pixel in the header — the blackest bit of the title's ink.
     *
     * Lower is more opaque. Fading the title towards transparent lifts every one of its pixels
     * towards the white behind it, so this single number tracks the alpha faithfully without
     * depending on which glyph happens to be drawn.
     */
    private fun darkestPixel(): Int {
        val pixels = rule.onNodeWithTag(HEADER_TAG).captureToImage().toPixelMap()
        var darkest = Int.MAX_VALUE
        for (y in 0 until pixels.height) {
            for (x in 0 until pixels.width) {
                val color = pixels[x, y]
                val luminance = ((color.red + color.green + color.blue) / 3f * Byte.MAX_VALUE).toInt()
                if (luminance < darkest) {
                    darkest = luminance
                }
            }
        }
        return darkest
    }
}
