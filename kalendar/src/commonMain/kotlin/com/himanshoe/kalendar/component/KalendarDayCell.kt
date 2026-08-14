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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastForEach
import androidx.compose.ui.util.fastMap
import androidx.compose.ui.util.fastMaxOfOrNull
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.himanshoe.kalendar.DEFAULT_EVENT_INDICATOR_CAP
import com.himanshoe.kalendar.KalendarMonth
import com.himanshoe.kalendar.KalendarTimeline
import com.himanshoe.kalendar.KalendarViewConfig
import com.himanshoe.kalendar.KalendarWeek
import com.himanshoe.kalendar.KalendarYear
import com.himanshoe.kalendar.foundation.event.KalendarEvent
import com.himanshoe.kalendar.foundation.format.KalendarFormatters
import com.himanshoe.kalendar.slot.KalendarDayOverflowScope
import com.himanshoe.kalendar.slot.KalendarDayScope
import com.himanshoe.kalendar.theme.KalendarAnimations
import com.himanshoe.kalendar.theme.KalendarColors
import com.himanshoe.kalendar.theme.KalendarShapes
import com.himanshoe.kalendar.theme.KalendarStrings
import com.himanshoe.kalendar.theme.KalendarTheme
import com.himanshoe.kalendar.theme.KalendarTypography
import com.himanshoe.kalendar.theme.kalendarAnimations
import com.himanshoe.kalendar.theme.kalendarColors
import com.himanshoe.kalendar.theme.kalendarDimensions
import com.himanshoe.kalendar.theme.kalendarShapes
import com.himanshoe.kalendar.theme.kalendarStrings
import com.himanshoe.kalendar.theme.kalendarTypography
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month

/**
 * Thickness of the ring drawn around a keyboard-focused cell. [KalendarDayCellDefaults.Cell] always
 * draws it — transparent when unfocused — so gaining focus never changes the cell's size, and
 * [KalendarGrid]'s roving focus ring reuses the same width so the two are indistinguishable.
 */
internal val FOCUS_RING_WIDTH = 2.dp

/** Width bounds of [KalendarDayCellDefaults.OverflowPopup], which sizes itself to its longest row. */
private val OVERFLOW_POPUP_MIN_WIDTH = 160.dp
private val OVERFLOW_POPUP_MAX_WIDTH = 280.dp

/** Height ceiling of [KalendarDayCellDefaults.OverflowPopup]; past it the list scrolls. */
private val OVERFLOW_POPUP_MAX_HEIGHT = 240.dp

/** Inset above and below [KalendarDayCellDefaults.OverflowPopup]'s rows. */
private val OVERFLOW_POPUP_PADDING = 4.dp

/**
 * How far the day number is allowed to shrink, as a fraction of
 * [KalendarTypography.dayNumber]'s size, before a cell too narrow for it clips instead.
 *
 * A calendar's cells are sized by their container, not by their content — twelve month grids in a
 * year view make cells a few dp across — so a fixed font size guarantees that some layout somewhere
 * renders "1" where it means "16". Shrinking to fit is the only behaviour that stays correct at
 * every size and every accessibility font scale.
 */
private const val DAY_NUMBER_MIN_FONT_SCALE = 0.5f

/** Granularity of the day number's fit search. Finer than this buys nothing a reader can see. */
private val DAY_NUMBER_FONT_STEP = 0.5.sp

/**
 * The [KalendarDayOverflowScope] [KalendarDayCellDefaults.Cell] hands its `overflowPopup` slot.
 *
 * [onEventClick] dismisses after reporting rather than before, so a handler that reads the popover's
 * open state sees it open — the cell is the thing that owns that state, and it has not been asked to
 * close yet at the moment the callback runs.
 */
private class KalendarDayOverflowScopeImpl(
    override val date: LocalDate,
    override val events: List<KalendarEvent>,
    override val hiddenEventCount: Int,
    private val reportEventClick: (KalendarEvent) -> Unit,
    private val close: () -> Unit,
) : KalendarDayOverflowScope {

    override fun onEventClick(event: KalendarEvent) {
        reportEventClick(event)
        close()
    }

    override fun dismiss() {
        close()
    }
}

/**
 * Makes a day cell accessible and clickable: a screen-reader description built from the date plus
 * [KalendarStrings.todaySuffix]/[KalendarStrings.hasEventsSuffix], the `selected` state, a
 * [Role.Button] role, and a click that is ignored when the date is disabled.
 *
 * A custom `dayContent` replaces the built-in cell entirely, which would otherwise mean replacing
 * its accessibility too. Apply this to your own cell's outermost modifier and it stays accessible
 * — and localizable, since the announcements come from [KalendarStrings]:
 *
 * ```kotlin
 * KalendarMonth(
 *     selectedDate = today,
 *     onDateClick = { date, _ -> select(date) },
 *     dayContent = { scope ->
 *         MyDayCell(
 *             day = scope.date.day,
 *             modifier = Modifier.kalendarDaySemantics(
 *                 scope = scope,
 *                 onClick = { select(scope.date) },
 *             ),
 *         )
 *     },
 * )
 * ```
 *
 * @param scope The cell's state, as handed to the `dayContent` slot.
 * @param onClick Called when the cell is tapped and [KalendarDayScope.isDisabled] is `false`.
 * @param monthNameFormatter Formats the month name inside the announcement. Defaults to the same
 *   English formatter the views use; pass [KalendarViewConfig.monthNameFormatter] to match a
 *   localized calendar.
 * @param interactionSource Interaction source for the click, when you want to observe press state.
 */
@Composable
public fun Modifier.kalendarDaySemantics(
    scope: KalendarDayScope,
    onClick: () -> Unit,
    monthNameFormatter: (Month) -> String = KalendarFormatters.monthName,
    interactionSource: MutableInteractionSource? = null,
): Modifier {
    val strings = kalendarStrings()
    val description = remember(scope.date, scope.isToday, scope.events.isNotEmpty(), strings) {
        buildString {
            append(strings.dayAccessibilityLabel(scope.date, monthNameFormatter(scope.date.month)))
            if (scope.isToday) {
                append(strings.todaySuffix)
            }
            if (scope.events.isNotEmpty()) {
                append(strings.hasEventsSuffix)
            }
        }
    }
    return this
        .semantics {
            this.contentDescription = description
            this.selected = scope.isSelected
        }
        .clickable(
            enabled = !scope.isDisabled,
            interactionSource = interactionSource,
            indication = null,
            role = Role.Button,
            onClick = onClick,
        )
}

/**
 * Renders one grid cell for [KalendarMonth], [KalendarYear], and [KalendarTimeline]: the real day
 * cell for dates in the current month, or — when [isCurrentMonth] is `false` and
 * [showAdjacentMonthDates] is `false` — an empty placeholder that preserves the grid's alignment.
 *
 * [onEventClick] is forwarded to [KalendarDayCellDefaults.Cell]'s overflow popover. Without it the
 * popover opens on a `+N` tap and every row in it is inert, which is the one thing a list of events
 * must not be.
 */
@Composable
@Suppress("LongParameterList")
internal fun KalendarMonthGridCell(
    scope: KalendarDayScope,
    isCurrentMonth: Boolean,
    showAdjacentMonthDates: Boolean,
    onClick: () -> Unit,
    dayContent: (@Composable (KalendarDayScope) -> Unit)?,
    showSelectionBackground: Boolean = true,
    monthNameFormatter: (Month) -> String = KalendarFormatters.monthName,
    maxEventIndicators: Int = DEFAULT_EVENT_INDICATOR_CAP,
    onEventClick: (KalendarEvent) -> Unit = {},
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
            onEventClick = onEventClick,
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
     * That `+N` label is a button: tapping it opens a popover listing the day's events, so the cell
     * no longer announces that events exist and then withholds them. The *label* is the trigger
     * rather than the cell, because the cell's own tap already means "select this date" — routing
     * the whole cell to the popover would take selection away from exactly the days that have the
     * most going on. A cell with no overflow has no label, and so behaves as it always did.
     *
     * Every cell is also a tab stop, so keyboard focus draws a [KalendarColors.focusIndicator] ring
     * around it and a hovering pointer washes it with [KalendarColors.hoverBackground]. A custom
     * `dayContent` slot replaces this cell entirely, and with it both affordances — apply
     * [kalendarDaySemantics] with your own `interactionSource` to rebuild them.
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
     * @param onEventClick Called when a row of the overflow popover is tapped, with the event that
     *   row represents. The popover closes itself afterwards. Defaults to doing nothing, which
     *   leaves the popover a read-only list of what the cell could not show.
     * @param showOverflowPopup Whether tapping the `+N` label opens [OverflowPopup]. Set it to
     *   `false` when your app surfaces a day's full event list some other way — the label then draws
     *   exactly as before but is no longer interactive.
     * @param overflowPopup Slot for what the popover shows, given the day's events and the callbacks
     *   that report a tap and close it. Defaults to [OverflowPopup]. The popover window itself — its
     *   placement, its focus, and dismissing it on an outside tap or escape — belongs to the cell, so
     *   a replacement only draws.
     */
    @Composable
    @Suppress("LongParameterList")
    public fun Cell(
        scope: KalendarDayScope,
        onClick: () -> Unit,
        modifier: Modifier = Modifier,
        showSelectionBackground: Boolean = true,
        monthNameFormatter: (Month) -> String = KalendarFormatters.monthName,
        maxEventIndicators: Int = DEFAULT_EVENT_INDICATOR_CAP,
        onEventClick: (KalendarEvent) -> Unit = {},
        showOverflowPopup: Boolean = true,
        overflowPopup: @Composable (KalendarDayOverflowScope) -> Unit = { overflowScope ->
            OverflowPopup(scope = overflowScope)
        },
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

        val hiddenEventCount = (scope.events.size - maxEventIndicators).coerceAtLeast(0)
        var overflowOpen by remember { mutableStateOf(false) }
        val interactionSource = remember { MutableInteractionSource() }
        val isPressed by interactionSource.collectIsPressedAsState()
        val isFocused by interactionSource.collectIsFocusedAsState()
        val isHovered by interactionSource.collectIsHoveredAsState()
        val scale by animateFloatAsState(
            targetValue = if (isPressed) {
                animations.pressScale
            } else {
                1f
            },
            animationSpec = scaleSpec,
            label = "KalendarDayCellPressScale",
        )
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
                .background(if (isHovered) colors.hoverBackground else Color.Transparent)
                .border(
                    width = FOCUS_RING_WIDTH,
                    color = if (isFocused) colors.focusIndicator else Color.Transparent,
                    shape = shapes.dayCell,
                )
                .kalendarDaySemantics(
                    scope = scope,
                    onClick = onClick,
                    monthNameFormatter = monthNameFormatter,
                    interactionSource = interactionSource,
                ),
        ) {
            val hasEvents = scope.events.isNotEmpty()
            val onOverflowClick = if (showOverflowPopup && hiddenEventCount > 0) {
                { overflowOpen = true }
            } else {
                null
            }
            KalendarDayCellLayout(
                indicatorStripHeight = dimensions.eventIndicatorStripHeight,
                dayNumber = {
                    BasicText(
                        text = scope.date.day.toString(),
                        style = textStyle.copy(color = textColor),
                        maxLines = 1,
                        autoSize = TextAutoSize.StepBased(
                            minFontSize = textStyle.fontSize * DAY_NUMBER_MIN_FONT_SCALE,
                            maxFontSize = textStyle.fontSize,
                            stepSize = DAY_NUMBER_FONT_STEP,
                        ),
                    )
                },
                indicators = {
                    if (animations.enabled) {
                        AnimatedVisibility(visible = hasEvents) {
                            EventIndicatorRow(
                                scope = scope,
                                maxEventIndicators = maxEventIndicators,
                                textColor = textColor,
                                onOverflowClick = onOverflowClick,
                            )
                        }
                    } else if (hasEvents) {
                        EventIndicatorRow(
                            scope = scope,
                            maxEventIndicators = maxEventIndicators,
                            textColor = textColor,
                            onOverflowClick = onOverflowClick,
                        )
                    }
                },
            )

            if (overflowOpen) {
                val overflowScope = remember(scope.date, scope.events, hiddenEventCount, onEventClick) {
                    KalendarDayOverflowScopeImpl(
                        date = scope.date,
                        events = scope.events,
                        hiddenEventCount = hiddenEventCount,
                        reportEventClick = onEventClick,
                        close = { overflowOpen = false },
                    )
                }
                Popup(
                    alignment = Alignment.BottomCenter,
                    onDismissRequest = { overflowOpen = false },
                    properties = PopupProperties(focusable = true),
                ) {
                    overflowPopup(overflowScope)
                }
            }
        }
    }

    /**
     * The built-in overflow popover: every event on the day, one tappable row each, on a
     * [KalendarColors.popupBackground] surface clipped to [KalendarShapes.popup] and outlined with
     * [KalendarColors.popupBorder] so it stays distinguishable from the grid beneath it.
     *
     * Each row is an event's accent colour —
     * [KalendarEvent.eventColor][com.himanshoe.kalendar.foundation.event.KalendarEvent.eventColor],
     * falling back to [KalendarColors.eventIndicator] — beside its name in
     * [KalendarTypography.agendaEventTitle], and reports to
     * [KalendarDayOverflowScope.onEventClick], which closes the popover. The list is width-bounded
     * and scrolls once it is taller than its ceiling, so a day with thirty events does not produce a
     * popover taller than the screen.
     *
     * @param scope The day's events and the popover's callbacks. Inside an `overflowPopup` slot this
     *   is the value the slot was handed.
     * @param modifier [Modifier] applied to the popover's surface.
     */
    @Composable
    public fun OverflowPopup(
        scope: KalendarDayOverflowScope,
        modifier: Modifier = Modifier,
    ) {
        val colors = kalendarColors()
        val shapes = kalendarShapes()
        val dimensions = kalendarDimensions()
        Column(
            modifier = modifier
                .widthIn(min = OVERFLOW_POPUP_MIN_WIDTH, max = OVERFLOW_POPUP_MAX_WIDTH)
                .clip(shapes.popup)
                .background(colors.popupBackground)
                .border(
                    width = dimensions.gridLineThickness,
                    color = colors.popupBorder,
                    shape = shapes.popup,
                )
                .heightIn(max = OVERFLOW_POPUP_MAX_HEIGHT)
                .verticalScroll(rememberScrollState())
                .padding(vertical = OVERFLOW_POPUP_PADDING),
        ) {
            scope.events.forEach { event ->
                OverflowEventRow(
                    event = event,
                    onClick = { scope.onEventClick(event) },
                )
            }
        }
    }
}

/**
 * One row of [KalendarDayCellDefaults.OverflowPopup]: the event's accent dot beside its name, the
 * whole row carrying the click and the [Role.Button] role.
 *
 * The name is drawn on a single ellipsized line rather than wrapping, so a long event title widens
 * the popover only up to [OVERFLOW_POPUP_MAX_WIDTH] and never turns one row into four.
 */
@Composable
private fun OverflowEventRow(
    event: KalendarEvent,
    onClick: () -> Unit,
) {
    val colors = kalendarColors()
    val typography = kalendarTypography()
    val shapes = kalendarShapes()
    val dimensions = kalendarDimensions()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(dimensions.agendaRowPadding),
        horizontalArrangement = Arrangement.spacedBy(dimensions.agendaRowGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(dimensions.agendaDotSize)
                .clip(shapes.eventIndicator)
                .background(event.eventColor ?: colors.eventIndicator),
        )
        BasicText(
            text = event.eventName,
            style = typography.agendaEventTitle.copy(color = colors.dayContent),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * The row of per-event indicator dots below a day number, plus the compact `+N` label for events
 * past [maxEventIndicators]. [textColor] is the day number's colour, reused for the dots and the
 * overflow label on a selected cell so they stay legible against the selection fill.
 *
 * [onOverflowClick] turns the `+N` label into the popover trigger. It is applied to the label alone
 * and adds no padding or minimum size, both because the day cell has no room to spare and because
 * the cell's three golden images pin this row's geometry exactly — the label's hit area is therefore
 * as small as the label is, which is the one thing about this trigger that a
 * [KalendarDimensions.eventIndicatorSize] tuned much smaller would make unusable.
 *
 * The label is a merging semantics boundary once it is clickable, so it stays a separate node from
 * the cell around it: a screen reader reaches the date first and the "show all events" button after,
 * rather than hearing "+2" spliced into the date announcement.
 */
/**
 * Places a day cell's number and its event indicators so neither can crowd the other.
 *
 * [indicatorStripHeight] is reserved along the bottom edge whether or not this particular day has
 * indicators, and the number is centred in what is left. Reserving unconditionally is the whole
 * point: stacking the two in a centred [Column] centres the number against *number plus
 * indicators*, so it rises as the day fills up — measured at 3px for one dot and 10px for a full
 * set with an overflow label, which left a single week's numbers sitting at three different heights
 * and, with animation enabled, visibly sliding upward as the dots faded in.
 *
 * A cell too short to hold both drops the indicators instead of overlapping them. That case is real
 * rather than defensive: a year view draws twelve month grids at once, and its cells are a fraction
 * the height of a month view's. A day number drawn over by dots is unreadable, whereas a missing
 * dot on a very small cell costs only detail the cell had no room to carry.
 *
 * @param indicatorStripHeight Space reserved for indicators, from
 *   [KalendarDimensions.eventIndicatorStripHeight].
 * @param dayNumber The day number, centred in the remaining height.
 * @param indicators The indicator row, bottom-aligned within the reserved strip.
 */
@Composable
private fun KalendarDayCellLayout(
    indicatorStripHeight: Dp,
    dayNumber: @Composable () -> Unit,
    indicators: @Composable () -> Unit,
) {
    Layout(contents = listOf(dayNumber, indicators)) { (numberMeasurables, indicatorMeasurables), constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val stripPx = indicatorStripHeight.roundToPx()

        // The number yields to the strip rather than the strip yielding to the number: the number
        // auto-sizes, so squeezing its band shrinks the glyph, whereas dropping the strip loses
        // information outright. Only when the band left over would be narrower than the strip
        // itself — a year view's cells, where the number would end up smaller than its own dots —
        // is the strip abandoned and the whole cell given back to the number.
        val boundedHeight = constraints.hasBoundedHeight
        val indicatorsFit = boundedHeight && constraints.maxHeight - stripPx >= stripPx
        val numberBand = when {
            !boundedHeight -> loose.maxHeight
            indicatorsFit -> constraints.maxHeight - stripPx
            else -> constraints.maxHeight
        }

        val numberPlaceables = numberMeasurables.fastMap { it.measure(loose.copy(maxHeight = numberBand)) }
        val numberHeight = numberPlaceables.fastMaxOfOrNull { it.height } ?: 0
        val numberWidth = numberPlaceables.fastMaxOfOrNull { it.width } ?: 0

        val indicatorPlaceables = indicatorMeasurables.fastMap {
            it.measure(loose.copy(maxHeight = if (indicatorsFit) stripPx else 0))
        }
        val height = if (boundedHeight) constraints.maxHeight else numberHeight + stripPx
        val width = if (constraints.hasBoundedWidth) {
            constraints.maxWidth
        } else {
            maxOf(numberWidth, indicatorPlaceables.fastMaxOfOrNull { it.width } ?: 0)
        }
        val band = if (indicatorsFit || !boundedHeight) height - stripPx else height

        layout(width, height) {
            numberPlaceables.fastForEach {
                it.place((width - it.width) / 2, (band - it.height) / 2)
            }
            if (indicatorsFit || !boundedHeight) {
                indicatorPlaceables.fastForEach {
                    it.place((width - it.width) / 2, height - it.height)
                }
            }
        }
    }
}

@Composable
private fun EventIndicatorRow(
    scope: KalendarDayScope,
    maxEventIndicators: Int,
    textColor: Color,
    onOverflowClick: (() -> Unit)?,
) {
    val colors = kalendarColors()
    val typography = kalendarTypography()
    val shapes = kalendarShapes()
    val dimensions = kalendarDimensions()
    val strings = kalendarStrings()

    Row(
        modifier = Modifier.padding(bottom = dimensions.eventIndicatorBottomPadding),
        horizontalArrangement = Arrangement.spacedBy(dimensions.eventIndicatorSpacing),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Indexed rather than `take(n).forEach`: take allocates a sublist on every cell, which is
        // 42 allocations for a month grid and around 400 for a year. There is no fastTake.
        val shownCount = minOf(maxEventIndicators, scope.events.size)
        for (index in 0 until shownCount) {
            val event = scope.events[index]
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
            BasicText(
                text = strings.eventOverflowLabel(scope.events.size - maxEventIndicators),
                style = typography.eventOverflowLabel.copy(
                    color = if (scope.isSelected) textColor else colors.eventIndicator,
                ),
                modifier = if (onOverflowClick == null) {
                    Modifier
                } else {
                    Modifier
                        .semantics { contentDescription = strings.showAllDayEvents }
                        .clickable(
                            interactionSource = null,
                            indication = null,
                            role = Role.Button,
                            onClick = onOverflowClick,
                        )
                },
            )
        }
    }
}
