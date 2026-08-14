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
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
    maxEventIndicators: Int = DEFAULT_EVENT_INDICATOR_CAP,
) {
    val dimensions = kalendarDimensions()
    if (!isCurrentMonth && !showAdjacentMonthDates) {
        Box(modifier = Modifier.aspectRatio(dimensions.dayCellAspectRatio))
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
            maxEventIndicators = maxEventIndicators,
        )
    }
}

/**
 * Default day-cell rendering, used by [KalendarWeek]/[KalendarMonth]/[KalendarYear] when no
 * `dayContent` slot is supplied.
 */
public object KalendarDayCellDefaults {

    /**
     * Renders [scope] as a date cell shaped by [KalendarShapes.dayCell]: the day number switches to
     * [KalendarTypography.dayNumberEmphasized] when the cell is selected or is today, a filled
     * background animates in for the selected state, a brief [KalendarAnimations.pressScale] gives
     * tactile feedback, [KalendarColors.disabledContentAlpha] and no click handling apply when
     * disabled, and per-event indicator dots fade in below the day number — one dot per event
     * (coloured by
     * [KalendarEvent.eventColor][com.himanshoe.kalendar.foundation.event.KalendarEvent.eventColor],
     * falling back to [KalendarColors.eventIndicator]), capped at [maxEventIndicators] with a
     * compact `+N` overflow label. Every colour, text style, shape, dimension, and motion spec
     * resolves through the ambient [KalendarTheme]; with [KalendarAnimations.enabled] set to
     * `false` the selection fill, the press-scale, and the indicator row all change instantly.
     *
     * @param showSelectionBackground Whether this cell draws its own selection fill. Set to
     *   `false` when a separate sliding indicator (e.g. [KalendarGrid]'s) already renders the
     *   selection visual behind this cell — [KalendarDayScope.isSelected] still drives the text
     *   color/weight either way.
     * @param monthNameFormatter Formats the month name used in this cell's accessibility
     *   description (e.g. "August 12, 2026"). See [KalendarViewConfig.monthNameFormatter].
     * @param maxEventIndicators How many event dots this cell draws before the remainder collapses
     *   into the `+N` overflow label. Defaults to 3; see [KalendarViewConfig.eventIndicatorCap],
     *   which is what the calendar views pass in.
     */
    @Composable
    public fun Cell(
        scope: KalendarDayScope,
        onClick: () -> Unit,
        modifier: Modifier = Modifier,
        showSelectionBackground: Boolean = true,
        monthNameFormatter: (Month) -> String = defaultMonthNameFormatter,
        maxEventIndicators: Int = DEFAULT_EVENT_INDICATOR_CAP,
    ) {
        val colors = kalendarColors()
        val typography = kalendarTypography()
        val shapes = kalendarShapes()
        val dimensions = kalendarDimensions()
        val animations = kalendarAnimations()
        val selectionSpec: AnimationSpec<Color> = if (animations.enabled) {
            animations.selectionColorSpec
        } else {
            snap()
        }
        val scaleSpec: AnimationSpec<Float> = if (animations.enabled) {
            animations.pressScaleSpec
        } else {
            snap()
        }
        val background by animateColorAsState(
            targetValue = if (scope.isSelected && showSelectionBackground) {
                colors.selectionBackground
            } else {
                Color.Transparent
            },
            animationSpec = selectionSpec,
            label = "KalendarDayCellSelectionBackground",
        )
        val textColor = when {
            scope.isSelected -> colors.onSelectionBackground
            scope.isToday -> colors.todayContent
            else -> colors.dayContent
        }
        val textStyle =
            if (scope.isSelected || scope.isToday) {
                typography.dayNumberEmphasized
            } else {
                typography.dayNumber
            }

        val interactionSource = remember { MutableInteractionSource() }
        val isPressed by interactionSource.collectIsPressedAsState()
        val scale by animateFloatAsState(
            targetValue = if (isPressed) {
                animations.pressScale
            } else {
                1f
            },
            animationSpec = scaleSpec,
            label = "KalendarDayCellPressScale",
        )
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
                .aspectRatio(dimensions.dayCellAspectRatio)
                .scale(scale)
                .alpha(
                    if (scope.isDisabled) {
                        colors.disabledContentAlpha
                    } else {
                        1f
                    }
                )
                .padding(dimensions.dayCellPadding)
                .clip(shapes.dayCell)
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
                    style = textStyle,
                )
                val hasEvents = scope.events.isNotEmpty()
                if (animations.enabled) {
                    AnimatedVisibility(visible = hasEvents) {
                        EventIndicatorRow(
                            scope = scope,
                            maxEventIndicators = maxEventIndicators,
                            textColor = textColor,
                        )
                    }
                } else if (hasEvents) {
                    EventIndicatorRow(
                        scope = scope,
                        maxEventIndicators = maxEventIndicators,
                        textColor = textColor,
                    )
                }
            }
        }
    }
}

/**
 * The row of per-event indicator dots below a day number, plus the compact `+N` label for events
 * past [maxEventIndicators]. [textColor] is the day number's colour, reused for the dots and the
 * overflow label on a selected cell so they stay legible against the selection fill.
 */
@Composable
private fun EventIndicatorRow(
    scope: KalendarDayScope,
    maxEventIndicators: Int,
    textColor: Color,
) {
    val colors = kalendarColors()
    val typography = kalendarTypography()
    val shapes = kalendarShapes()
    val dimensions = kalendarDimensions()

    Row(
        modifier = Modifier.padding(top = dimensions.eventIndicatorTopPadding),
        horizontalArrangement = Arrangement.spacedBy(dimensions.eventIndicatorSpacing),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        scope.events.take(maxEventIndicators).forEach { event ->
            Box(
                modifier = Modifier
                    .size(dimensions.eventIndicatorSize)
                    .clip(shapes.eventIndicator)
                    .background(
                        when {
                            scope.isSelected -> textColor
                            else -> event.eventColor ?: colors.eventIndicator
                        }
                    ),
            )
        }
        if (scope.events.size > maxEventIndicators) {
            Text(
                text = "+${scope.events.size - maxEventIndicators}",
                style = typography.eventOverflowLabel,
                color = if (scope.isSelected) textColor else colors.eventIndicator,
            )
        }
    }
}

/** Formats this date for screen readers, e.g. "August 12, 2026". */
private fun LocalDate.toAccessibleDescription(monthNameFormatter: (Month) -> String): String =
    "${monthNameFormatter(month)} $dayOfMonth, $year"
