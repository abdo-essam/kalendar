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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month

private const val TITLE_CROSSFADE_MS = 150
private const val JUMP_PICKER_MONTH_COLUMNS = 4

/**
 * One [IconButton]'s touch target — deliberately tight (no extra gap padding) since this is
 * subtracted from the title's available width on narrow phones (~320.dp) too; a title long enough
 * to need the full width already has a visible gap from the icon just by not extending into its
 * reserved slot, and [TextOverflow.Ellipsis] covers anything that still doesn't fit.
 */
private val ICON_SLOT_WIDTH = 48.dp

/**
 * Fresh header used by [KalendarWeek], [KalendarMonth], [KalendarYear], [KalendarSchedule],
 * and [KalendarScheduleWeek]: a title plus
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
    showJumpPicker: Boolean = false,
    monthNameFormatter: (Month) -> String = defaultMonthNameFormatter,
) {
    val scope = rememberCoroutineScope()
    var pickerOpen by remember { mutableStateOf(false) }
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
                    modifier = if (showJumpPicker) {
                        Modifier.clickable { pickerOpen = true }
                    } else {
                        Modifier
                    },
                )
            }
        }

        if (pickerOpen) {
            Popup(
                alignment = Alignment.TopCenter,
                onDismissRequest = { pickerOpen = false },
            ) {
                JumpPicker(
                    initialYear = state.visibleDate.year,
                    monthNameFormatter = monthNameFormatter,
                    onMonthSelected = { year, month ->
                        pickerOpen = false
                        scope.launch { state.animateScrollTo(LocalDate(year, month, 1)) }
                    },
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

/**
 * The month/year jump popup opened by tapping the header title: a year stepper above a 4x3 grid
 * of month names; choosing a month closes the popup and scrolls the calendar to that month.
 */
@Composable
private fun JumpPicker(
    initialYear: Int,
    monthNameFormatter: (Month) -> String,
    onMonthSelected: (year: Int, month: Month) -> Unit,
) {
    var year by remember { mutableIntStateOf(initialYear) }
    Surface(
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 3.dp,
        shadowElevation = 6.dp,
    ) {
        Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { year-- }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        contentDescription = "Previous year",
                    )
                }
                Text(
                    text = year.toString(),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
                IconButton(onClick = { year++ }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "Next year",
                    )
                }
            }
            Month.entries.chunked(JUMP_PICKER_MONTH_COLUMNS).forEach { rowMonths ->
                Row {
                    rowMonths.forEach { month ->
                        TextButton(
                            onClick = { onMonthSelected(year, month) },
                            modifier = Modifier.width(72.dp),
                        ) {
                            Text(text = monthNameFormatter(month).take(3))
                        }
                    }
                }
            }
        }
    }
}
