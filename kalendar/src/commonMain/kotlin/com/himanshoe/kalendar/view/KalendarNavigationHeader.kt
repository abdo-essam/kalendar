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

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

private const val TITLE_CROSSFADE_MS = 150

/**
 * One [IconButton]'s touch target — deliberately tight (no extra gap padding) since this is
 * subtracted from the title's available width on narrow phones (~320.dp) too; a title long enough
 * to need the full width already has a visible gap from the icon just by not extending into its
 * reserved slot, and [TextOverflow.Ellipsis] covers anything that still doesn't fit.
 */
private val ICON_SLOT_WIDTH = 48.dp

/**
 * Fresh header used by [KalendarWeek], [KalendarMonth], and [KalendarYear]: a title plus
 * previous/next arrow buttons wired to [state]'s pager, and an optional today button.
 *
 * The title is centered via [Box] alignment rather than [Row]'s `SpaceBetween` (which centers in
 * the leftover space between two side groups — the moment those groups are different widths, e.g.
 * a today button on one side and nothing on the other, the title visibly drifts off-center). Its
 * available width is then constrained to exactly how much space is actually reserved for icons on
 * each side, so it can never render underneath one; [TextOverflow.Ellipsis] is the fallback for a
 * title still too long for what's left (e.g. a long locale-formatted month name).
 */
@Composable
internal fun KalendarNavigationHeader(
    title: String,
    state: KalendarViewState,
    showArrows: Boolean,
    modifier: Modifier = Modifier,
    showTodayButton: Boolean = false,
) {
    val scope = rememberCoroutineScope()
    val leadingReserved = if (showArrows) ICON_SLOT_WIDTH else 0.dp
    val trailingReserved = (if (showTodayButton) ICON_SLOT_WIDTH else 0.dp) +
        (if (showArrows) ICON_SLOT_WIDTH else 0.dp)

    Box(
        modifier = modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().padding(start = leadingReserved, end = trailingReserved),
            contentAlignment = Alignment.Center,
        ) {
            AnimatedContent(
                targetState = title,
                transitionSpec = {
                    fadeIn(tween(TITLE_CROSSFADE_MS)) togetherWith fadeOut(tween(TITLE_CROSSFADE_MS))
                },
            ) { animatedTitle ->
                Text(
                    text = animatedTitle,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        if (showArrows) {
            IconButton(
                enabled = state.canScrollBackward,
                onClick = {
                    scope.launch {
                        state.pagerState.animateScrollToPage(state.pagerState.currentPage - 1)
                    }
                },
                modifier = Modifier.align(Alignment.CenterStart),
            ) {
                Icon(imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous")
            }
        }

        Row(modifier = Modifier.align(Alignment.CenterEnd), verticalAlignment = Alignment.CenterVertically) {
            if (showTodayButton) {
                IconButton(onClick = { scope.launch { state.animateScrollToToday() } }) {
                    Icon(imageVector = Icons.Filled.Today, contentDescription = "Today")
                }
            }
            if (showArrows) {
                IconButton(
                    enabled = state.canScrollForward,
                    onClick = {
                        scope.launch {
                            state.pagerState.animateScrollToPage(state.pagerState.currentPage + 1)
                        }
                    },
                ) {
                    Icon(imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next")
                }
            }
        }
    }
}
