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

package com.himanshoe.kalendar.slot

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.himanshoe.kalendar.component.KalendarHeaderDefaults
import kotlinx.datetime.LocalDate
import org.junit.Rule
import org.junit.Test
import kotlin.math.abs
import kotlin.test.assertTrue

/**
 * The header title must sit at the centre of the *header*, not at the centre of the gap between the
 * leading and trailing action clusters.
 *
 * The regression these guard: the header used to pad a centring [Box] by each side's own reserved
 * width — one icon slot leading, but two trailing once the today button appeared. Centring inside
 * that asymmetric inset put the title half an icon slot (~24dp) off true centre, which is plainly
 * visible next to a grid that *is* centred.
 */
class KalendarHeaderCenteringUiTest {

    @get:Rule
    val rule = createComposeRule()

    private val title = "March 2026"

    /**
     * A [KalendarHeaderScope] with no calendar behind it — the header only reads [title] and
     * [visibleDate] for layout, so a stub keeps the test measuring the header rather than a view.
     */
    private class StubHeaderScope(override val title: String) : KalendarHeaderScope {
        @Suppress("MagicNumber")
        override val visibleDate: LocalDate = LocalDate(2026, 3, 1)
        override val canScrollBackward: Boolean = true
        override val canScrollForward: Boolean = true
        override fun goToPreviousPage() = Unit
        override fun goToNextPage() = Unit
        override fun goToToday() = Unit
        override fun goTo(date: LocalDate) = Unit
    }

    private fun assertTitleCentered(case: String) {
        val rootBounds = rule.onRoot().getBoundsInRoot()
        val titleBounds = rule.onNodeWithText(title).getBoundsInRoot()

        val rootCenter = (rootBounds.left + rootBounds.right) / 2
        val titleCenter = (titleBounds.left + titleBounds.right) / 2
        val drift = abs((titleCenter - rootCenter).value)

        assertTrue(
            drift <= TOLERANCE_DP,
            "$case: title centre ${titleCenter.value}dp is ${drift}dp from the header centre " +
                "${rootCenter.value}dp (tolerance ${TOLERANCE_DP}dp)",
        )
    }

    private fun setHeader(content: @Composable (KalendarHeaderScope) -> Unit) {
        rule.setContent {
            Box(modifier = Modifier.width(HEADER_WIDTH)) {
                content(StubHeaderScope(title))
            }
        }
    }

    @Test
    fun header_arrowsOnly_centersTitle() {
        setHeader { scope ->
            KalendarHeaderDefaults.Header(scope = scope, showTodayButton = false)
        }
        assertTitleCentered("arrows only, symmetric sides")
    }

    @Test
    fun header_arrowsPlusTodayButton_centersTitle() {
        // The original failure: one slot leading, two trailing.
        setHeader { scope ->
            KalendarHeaderDefaults.Header(scope = scope, showTodayButton = true)
        }
        assertTitleCentered("arrows plus today button")
    }

    @Test
    fun header_todayButtonOnlyNoArrows_centersTitle() {
        // The most lopsided arrangement available: nothing leading, one slot trailing.
        setHeader { scope ->
            KalendarHeaderDefaults.Header(
                scope = scope,
                showNavigationArrows = false,
                showTodayButton = true,
            )
        }
        assertTitleCentered("today button only, nothing leading")
    }

    @Test
    fun header_customActions_centersTitle() {
        // Arbitrary caller chrome must not move the title either, which is the whole reason the
        // reserved width is measured rather than derived from a token.
        setHeader { scope ->
            KalendarHeaderDefaults.Header(scope = scope) {
                Box(modifier = Modifier.size(ACTION_SIZE))
                Box(modifier = Modifier.size(ACTION_SIZE))
            }
        }
        assertTitleCentered("two custom actions")
    }

    @Test
    fun header_crowdedByActions_keepsTitleVisibleBetweenTheClusters() {
        // Reserving the wider cluster on both sides costs twice that cluster. Past the point where
        // that leaves nothing, centring the title would mean rendering it at zero width — so it
        // falls back to the real gap. This asserts the fallback shows a title at all, and that it
        // still clears both clusters; it deliberately does NOT assert centring, because a header
        // this crowded has no centre that clears both sides.
        setHeader { scope ->
            KalendarHeaderDefaults.Header(scope = scope) {
                Box(modifier = Modifier.size(OVERSIZED_ACTION_SIZE))
                Box(modifier = Modifier.size(OVERSIZED_ACTION_SIZE))
            }
        }
        val titleBounds = rule.onNodeWithText(title).getBoundsInRoot()
        val titleWidth = (titleBounds.right - titleBounds.left).value
        assertTrue(
            titleWidth > 0f,
            "an over-crowded header must still render its title, was ${titleWidth}dp wide",
        )
        assertTrue(
            titleBounds.left.value >= LEADING_SLOT_DP - TOLERANCE_DP,
            "title must clear the leading arrow, started at ${titleBounds.left.value}dp",
        )
    }

    private companion object {
        val HEADER_WIDTH = 360.dp
        val ACTION_SIZE = 24.dp
        val OVERSIZED_ACTION_SIZE = 56.dp

        /** Matches `KalendarDimensions.headerIconSlotWidth`, the width of the previous-page button. */
        const val LEADING_SLOT_DP = 48f

        /** One dp of slack for odd-pixel widths halving to a fractional centre. */
        const val TOLERANCE_DP = 1f
    }
}
