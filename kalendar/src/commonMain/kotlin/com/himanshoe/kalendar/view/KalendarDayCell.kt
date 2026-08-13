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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.himanshoe.kalendar.foundation.event.KalendarEvent
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month

/**
 * The state of a single date cell inside [KalendarWeek], [KalendarMonth], or [KalendarYear].
 *
 * Passed to a custom `dayContent` slot, or rendered by [KalendarDayCellDefaults.Cell] when no
 * slot is provided. Delivered as a scope rather than positional lambda parameters so future
 * fields can be added without a breaking change to every `dayContent` call site.
 *
 * @property date The date this cell represents.
 * @property isSelected Whether [date] is in the calendar's `selectedDates`.
 * @property isToday Whether [date] is today, in the system time zone.
 * @property isDisabled Whether [date] matched `KalendarViewConfig.disabledDates`.
 * @property events The events occurring on [date].
 */
@Immutable
public data class KalendarDayScope(
    val date: LocalDate,
    val isSelected: Boolean,
    val isToday: Boolean,
    val isDisabled: Boolean,
    val events: List<KalendarEvent>,
)

/**
 * Renders one grid cell for [KalendarMonth], [KalendarYear], and [KalendarTimeline]: the real day
 * cell for dates in the current month, or — when [isCurrentMonth] is `false` and
 * [showAdjacentMonthDates] is `false` — an empty placeholder that preserves the grid's alignment.
 */
@Composable
internal fun KalendarMonthGridCell(
    scope: KalendarDayScope,
    isCurrentMonth: Boolean,
    showAdjacentMonthDates: Boolean,
    onClick: () -> Unit,
    dayContent: (@Composable (KalendarDayScope) -> Unit)?,
    showSelectionBackground: Boolean = true,
    monthNameFormatter: (Month) -> String = defaultMonthNameFormatter,
) {
    if (!isCurrentMonth && !showAdjacentMonthDates) {
        Box(modifier = Modifier.aspectRatio(1f))
        return
    }
    if (dayContent != null) {
        dayContent(scope)
    } else {
        KalendarDayCellDefaults.Cell(
            scope = scope,
            onClick = onClick,
            showSelectionBackground = showSelectionBackground,
            monthNameFormatter = monthNameFormatter,
        )
    }
}

/**
 * Default day-cell rendering, used by [KalendarWeek]/[KalendarMonth]/[KalendarYear] when no
 * `dayContent` slot is supplied.
 */
public object KalendarDayCellDefaults {

    /**
     * Renders [scope] as a circular date cell: bold text for today, a filled background that
     * animates in for the selected state, a brief press-scale for tactile feedback, reduced
     * opacity and no click handling when disabled, and a small dot that fades in below the day
     * number when [KalendarDayScope.events] is non-empty.
     *
     * @param showSelectionBackground Whether this cell draws its own selection fill. Set to
     *   `false` when a separate sliding indicator (e.g. [KalendarGrid]'s) already renders the
     *   selection visual behind this cell — [KalendarDayScope.isSelected] still drives the text
     *   color/weight either way.
     * @param monthNameFormatter Formats the month name used in this cell's accessibility
     *   description (e.g. "August 12, 2026"). See [KalendarViewConfig.monthNameFormatter].
     */
    @Composable
    public fun Cell(
        scope: KalendarDayScope,
        onClick: () -> Unit,
        modifier: Modifier = Modifier,
        showSelectionBackground: Boolean = true,
        monthNameFormatter: (Month) -> String = defaultMonthNameFormatter,
    ) {
        val colors = MaterialTheme.colorScheme
        val background by animateColorAsState(
            targetValue = if (scope.isSelected && showSelectionBackground) {
                colors.primaryContainer
            } else {
                Color.Transparent
            },
        )
        val textColor = when {
            scope.isSelected -> colors.onPrimaryContainer
            scope.isToday -> colors.primary
            else -> colors.onSurface
        }
        val fontWeight =
            if (scope.isSelected || scope.isToday) {
                FontWeight.Bold
            } else {
                FontWeight.Normal
            }

        val interactionSource = remember { MutableInteractionSource() }
        val isPressed by interactionSource.collectIsPressedAsState()
        val scale by animateFloatAsState(targetValue = if (isPressed) 0.92f else 1f)
        val contentDescription = remember(scope.date, scope.isToday, scope.events.isNotEmpty()) {
            buildString {
                append(scope.date.toAccessibleDescription(monthNameFormatter))
                if (scope.isToday) {
                    append(", today")
                }
                if (scope.events.isNotEmpty()) {
                    append(", has events")
                }
            }
        }

        Box(
            contentAlignment = Alignment.Center,
            modifier = modifier
                .aspectRatio(1f)
                .scale(scale)
                .alpha(
                    if (scope.isDisabled) {
                        0.38f
                    } else {
                        1f
                    }
                )
                .padding(2.dp)
                .clip(CircleShape)
                .background(background)
                .semantics {
                    this.contentDescription = contentDescription
                    this.selected = scope.isSelected
                }
                .clickable(
                    enabled = !scope.isDisabled,
                    interactionSource = interactionSource,
                    indication = null,
                    role = Role.Button,
                    onClick = onClick,
                ),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = scope.date.dayOfMonth.toString(),
                    color = textColor,
                    fontWeight = fontWeight,
                )
                AnimatedVisibility(visible = scope.events.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .padding(top = 3.dp)
                            .size(4.dp)
                            .clip(CircleShape)
                            .background(
                                if (scope.isSelected) {
                                    textColor
                                } else {
                                    colors.primary
                                }
                            ),
                    )
                }
            }
        }
    }
}

/** Formats this date for screen readers, e.g. "August 12, 2026". */
private fun LocalDate.toAccessibleDescription(monthNameFormatter: (Month) -> String): String =
    "${monthNameFormatter(month)} $dayOfMonth, $year"
