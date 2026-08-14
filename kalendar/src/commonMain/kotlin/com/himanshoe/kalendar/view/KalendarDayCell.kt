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
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.himanshoe.kalendar.foundation.event.KalendarEvent
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month

/**
 * Thickness of the ring [KalendarDayCellDefaults.Cell] draws around a keyboard-focused cell. It is
 * always drawn — transparent when unfocused — so gaining focus never changes the cell's size.
 */
private val FOCUS_RING_WIDTH = 2.dp

/** Width bounds of [KalendarDayCellDefaults.OverflowPopup], which sizes itself to its longest row. */
private val OVERFLOW_POPUP_MIN_WIDTH = 160.dp
private val OVERFLOW_POPUP_MAX_WIDTH = 280.dp

/** Height ceiling of [KalendarDayCellDefaults.OverflowPopup]; past it the list scrolls. */
private val OVERFLOW_POPUP_MAX_HEIGHT = 240.dp

/** Inset above and below [KalendarDayCellDefaults.OverflowPopup]'s rows. */
private val OVERFLOW_POPUP_PADDING = 4.dp

/**
 * Accessibility label for the `+N` overflow trigger, until [KalendarStrings] carries one of its own.
 *
 * Every other announcement in the library is localizable through [KalendarStrings]; this one is not
 * yet, because the entry it belongs in does not exist. It is deliberately a private constant rather
 * than a public parameter so that adding `KalendarStrings.showAllDayEvents` later is a pure
 * addition — nothing here has to be removed or deprecated.
 */
private const val OVERFLOW_TRIGGER_LABEL = "Show all events"

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
public class KalendarDayScope(
    public val date: LocalDate,
    public val isSelected: Boolean,
    public val isToday: Boolean,
    public val isDisabled: Boolean,
    public val events: List<KalendarEvent>,
) {

    /** Returns a duplicate of this scope with only the fields passed here replaced. */
    public fun copy(
        date: LocalDate = this.date,
        isSelected: Boolean = this.isSelected,
        isToday: Boolean = this.isToday,
        isDisabled: Boolean = this.isDisabled,
        events: List<KalendarEvent> = this.events,
    ): KalendarDayScope = KalendarDayScope(
        date = date,
        isSelected = isSelected,
        isToday = isToday,
        isDisabled = isDisabled,
        events = events,
    )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is KalendarDayScope) return false
        if (date != other.date) return false
        if (isSelected != other.isSelected) return false
        if (isToday != other.isToday) return false
        if (isDisabled != other.isDisabled) return false
        if (events != other.events) return false
        return true
    }

    override fun hashCode(): Int {
        var result = date.hashCode()
        result = 31 * result + isSelected.hashCode()
        result = 31 * result + isToday.hashCode()
        result = 31 * result + isDisabled.hashCode()
        result = 31 * result + events.hashCode()
        return result
    }

    override fun toString(): String = "KalendarDayScope(" +
        "date=$date, " +
        "isSelected=$isSelected, " +
        "isToday=$isToday, " +
        "isDisabled=$isDisabled, " +
        "events=$events" +
        ")"
}

/**
 * What one day's overflow popover is given to draw: the date, that date's events, and the callbacks
 * that report a tap and close the popover.
 *
 * Passed to the `overflowPopup` slot on [KalendarDayCellDefaults.Cell], and to
 * [KalendarDayCellDefaults.OverflowPopup] when the slot is left at its default. [events] is the
 * date's *whole* event list rather than only the hidden tail: a popover opened from a `+2` label
 * that showed the fourth and fifth event without the first three would be a list with no beginning,
 * so the count that was hidden is reported separately as [hiddenEventCount] for a popover that wants
 * to mark the cut-off.
 *
 * This is an interface rather than a data class so later releases can add information to it without
 * breaking every existing `overflowPopup` lambda.
 */
@Stable
public interface KalendarDayOverflowScope {

    /** The date whose events the popover is listing. */
    public val date: LocalDate

    /** Every event on [date], in the order the day cell received them — not just the hidden ones. */
    public val events: List<KalendarEvent>

    /**
     * How many of [events] the cell's indicator dots did not represent, i.e. the `N` in its `+N`
     * label. Always at least 1 while the popover is open, since nothing else opens it.
     */
    public val hiddenEventCount: Int

    /**
     * Reports [event] to the cell's `onEventClick` and closes the popover, in that order. A popover
     * that wants to stay open should call the callback it was built with instead of this.
     */
    public fun onEventClick(event: KalendarEvent)

    /**
     * Closes the popover. Tapping outside it and pressing escape already do this; call it for a
     * close button of your own.
     */
    public fun dismiss()
}

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
 *             day = scope.date.dayOfMonth,
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
    monthNameFormatter: (Month) -> String = defaultMonthNameFormatter,
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
        monthNameFormatter: (Month) -> String = defaultMonthNameFormatter,
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
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                BasicText(
                    text = scope.date.dayOfMonth.toString(),
                    style = textStyle.copy(color = textColor),
                )
                val hasEvents = scope.events.isNotEmpty()
                val onOverflowClick = if (showOverflowPopup && hiddenEventCount > 0) {
                    { overflowOpen = true }
                } else {
                    null
                }
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
            }

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
            BasicText(
                text = strings.eventOverflowLabel(scope.events.size - maxEventIndicators),
                style = typography.eventOverflowLabel.copy(
                    color = if (scope.isSelected) textColor else colors.eventIndicator,
                ),
                modifier = if (onOverflowClick == null) {
                    Modifier
                } else {
                    Modifier
                        .semantics { contentDescription = OVERFLOW_TRIGGER_LABEL }
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
