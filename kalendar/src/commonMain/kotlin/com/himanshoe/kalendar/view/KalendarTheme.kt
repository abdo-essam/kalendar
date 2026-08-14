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

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Colour tokens for every Kalendar view, replacing the built-in Material-derived palette.
 *
 * Obtain a Material-matching instance from [KalendarThemeDefaults.colors] and `copy` the tokens
 * you want to change, or construct one from scratch for non-Material apps:
 *
 * ```kotlin
 * KalendarTheme(
 *     colors = KalendarThemeDefaults.colors().copy(
 *         selectionBackground = BrandPurple,
 *         nowIndicator = BrandRed,
 *     ),
 * ) {
 *     KalendarMonth(selectedDate = today)
 * }
 * ```
 *
 * @property background Fill behind the whole calendar. Defaults to the Material surface colour, so
 *   the calendar follows light/dark mode out of the box.
 * @property selectionBackground Fill of the selected day's circle and the sliding indicator.
 * @property onSelectionBackground Content colour on top of [selectionBackground].
 * @property todayContent Day-number colour for today's cell when it isn't selected.
 * @property dayContent Day-number colour for ordinary cells.
 * @property dayLabelContent Colour of the day-of-week column labels and hour-gutter labels.
 * @property headerContent Colour of the navigation header's title and its arrow/today icons.
 * @property headerContentDisabled Colour of a header arrow whose direction is blocked by
 *   [KalendarViewState.minDate]/[KalendarViewState.maxDate]. Without it a bounded calendar's end
 *   arrows look live and silently do nothing. Defaults to [headerContent] at
 *   [disabledContentAlpha]'s default opacity, and applies to the built-in chevron and today glyphs;
 *   a caller-supplied icon slot draws itself and is left alone.
 * @property focusIndicator Colour of the ring drawn around the keyboard-focused day. On the paged
 *   views this is the roving ring the arrow keys move around the grid, which is what makes keyboard
 *   navigation visible at all; a standalone [KalendarDayCellDefaults.Cell] draws the same ring for
 *   itself when it holds focus. Defaults to [todayContent].
 * @property hoverBackground Fill washed over a day cell while the pointer is over it. Defaults to
 *   [dayContent] at a low opacity.
 * @property eventIndicator Default event dot/block accent when
 *   [KalendarEvent.eventColor][com.himanshoe.kalendar.foundation.event.KalendarEvent.eventColor]
 *   is `null`.
 * @property nowIndicator The current-time line on [KalendarSchedule].
 * @property gridLine Hour lines and separators on [KalendarSchedule].
 * @property dragGhostBackground Fill of the drop-target ghost while dragging an event.
 * @property popupBackground Fill behind the month/year jump picker. It has to be opaque, since the
 *   picker floats over the calendar.
 * @property popupBorder The jump picker's hairline outline, which separates it from the calendar
 *   underneath. Set it to [Color.Transparent] to drop the outline.
 * @property agendaCard Fill of a raised surface sitting on top of [background] — [KalendarAgenda]'s
 *   event rows. It has to be distinguishable from [background], since the raise is drawn with
 *   colour rather than a shadow.
 * @property disabledContentAlpha Opacity applied to a day cell that
 *   [KalendarViewConfig.disabledDates] rejected, and to adjacent-month dates.
 * @property eventBlockAlpha Opacity of an event block's fill, applied over the event's accent
 *   colour. The accent stripe and text stay fully opaque.
 * @property eventBlockDraggingAlpha Opacity of an event block's fill while it is being dragged.
 * @property dragGhostAlpha Opacity of [dragGhostBackground].
 */
@Immutable
@Suppress("LongParameterList")
public class KalendarColors(
    public val background: Color,
    public val selectionBackground: Color,
    public val onSelectionBackground: Color,
    public val todayContent: Color,
    public val dayContent: Color,
    public val dayLabelContent: Color,
    public val headerContent: Color,
    public val eventIndicator: Color,
    public val nowIndicator: Color,
    public val gridLine: Color,
    public val dragGhostBackground: Color,
    public val popupBackground: Color,
    public val popupBorder: Color,
    public val agendaCard: Color,
    public val headerContentDisabled: Color = headerContent.copy(alpha = DEFAULT_DISABLED_ALPHA),
    public val focusIndicator: Color = todayContent,
    public val hoverBackground: Color = dayContent.copy(alpha = DEFAULT_HOVER_ALPHA),
    public val disabledContentAlpha: Float = DEFAULT_DISABLED_ALPHA,
    public val eventBlockAlpha: Float = DEFAULT_EVENT_BLOCK_ALPHA,
    public val eventBlockDraggingAlpha: Float = DEFAULT_EVENT_BLOCK_DRAGGING_ALPHA,
    public val dragGhostAlpha: Float = DEFAULT_DRAG_GHOST_ALPHA,
) {

    /** Returns a duplicate of these colours with only the tokens passed here replaced. */
    public fun copy(
        background: Color = this.background,
        selectionBackground: Color = this.selectionBackground,
        onSelectionBackground: Color = this.onSelectionBackground,
        todayContent: Color = this.todayContent,
        dayContent: Color = this.dayContent,
        dayLabelContent: Color = this.dayLabelContent,
        headerContent: Color = this.headerContent,
        eventIndicator: Color = this.eventIndicator,
        nowIndicator: Color = this.nowIndicator,
        gridLine: Color = this.gridLine,
        dragGhostBackground: Color = this.dragGhostBackground,
        popupBackground: Color = this.popupBackground,
        popupBorder: Color = this.popupBorder,
        agendaCard: Color = this.agendaCard,
        headerContentDisabled: Color = this.headerContentDisabled,
        focusIndicator: Color = this.focusIndicator,
        hoverBackground: Color = this.hoverBackground,
        disabledContentAlpha: Float = this.disabledContentAlpha,
        eventBlockAlpha: Float = this.eventBlockAlpha,
        eventBlockDraggingAlpha: Float = this.eventBlockDraggingAlpha,
        dragGhostAlpha: Float = this.dragGhostAlpha,
    ): KalendarColors = KalendarColors(
        background = background,
        selectionBackground = selectionBackground,
        onSelectionBackground = onSelectionBackground,
        todayContent = todayContent,
        dayContent = dayContent,
        dayLabelContent = dayLabelContent,
        headerContent = headerContent,
        eventIndicator = eventIndicator,
        nowIndicator = nowIndicator,
        gridLine = gridLine,
        dragGhostBackground = dragGhostBackground,
        popupBackground = popupBackground,
        popupBorder = popupBorder,
        agendaCard = agendaCard,
        headerContentDisabled = headerContentDisabled,
        focusIndicator = focusIndicator,
        hoverBackground = hoverBackground,
        disabledContentAlpha = disabledContentAlpha,
        eventBlockAlpha = eventBlockAlpha,
        eventBlockDraggingAlpha = eventBlockDraggingAlpha,
        dragGhostAlpha = dragGhostAlpha,
    )

    @Suppress("CyclomaticComplexMethod")
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is KalendarColors) return false
        if (background != other.background) return false
        if (selectionBackground != other.selectionBackground) return false
        if (onSelectionBackground != other.onSelectionBackground) return false
        if (todayContent != other.todayContent) return false
        if (dayContent != other.dayContent) return false
        if (dayLabelContent != other.dayLabelContent) return false
        if (headerContent != other.headerContent) return false
        if (eventIndicator != other.eventIndicator) return false
        if (nowIndicator != other.nowIndicator) return false
        if (gridLine != other.gridLine) return false
        if (dragGhostBackground != other.dragGhostBackground) return false
        if (popupBackground != other.popupBackground) return false
        if (popupBorder != other.popupBorder) return false
        if (agendaCard != other.agendaCard) return false
        if (headerContentDisabled != other.headerContentDisabled) return false
        if (focusIndicator != other.focusIndicator) return false
        if (hoverBackground != other.hoverBackground) return false
        if (disabledContentAlpha != other.disabledContentAlpha) return false
        if (eventBlockAlpha != other.eventBlockAlpha) return false
        if (eventBlockDraggingAlpha != other.eventBlockDraggingAlpha) return false
        if (dragGhostAlpha != other.dragGhostAlpha) return false
        return true
    }

    override fun hashCode(): Int {
        var result = background.hashCode()
        result = 31 * result + selectionBackground.hashCode()
        result = 31 * result + onSelectionBackground.hashCode()
        result = 31 * result + todayContent.hashCode()
        result = 31 * result + dayContent.hashCode()
        result = 31 * result + dayLabelContent.hashCode()
        result = 31 * result + headerContent.hashCode()
        result = 31 * result + eventIndicator.hashCode()
        result = 31 * result + nowIndicator.hashCode()
        result = 31 * result + gridLine.hashCode()
        result = 31 * result + dragGhostBackground.hashCode()
        result = 31 * result + popupBackground.hashCode()
        result = 31 * result + popupBorder.hashCode()
        result = 31 * result + agendaCard.hashCode()
        result = 31 * result + headerContentDisabled.hashCode()
        result = 31 * result + focusIndicator.hashCode()
        result = 31 * result + hoverBackground.hashCode()
        result = 31 * result + disabledContentAlpha.hashCode()
        result = 31 * result + eventBlockAlpha.hashCode()
        result = 31 * result + eventBlockDraggingAlpha.hashCode()
        result = 31 * result + dragGhostAlpha.hashCode()
        return result
    }

    override fun toString(): String = "KalendarColors(" +
        "background=$background, " +
        "selectionBackground=$selectionBackground, " +
        "onSelectionBackground=$onSelectionBackground, " +
        "todayContent=$todayContent, " +
        "dayContent=$dayContent, " +
        "dayLabelContent=$dayLabelContent, " +
        "headerContent=$headerContent, " +
        "eventIndicator=$eventIndicator, " +
        "nowIndicator=$nowIndicator, " +
        "gridLine=$gridLine, " +
        "dragGhostBackground=$dragGhostBackground, " +
        "popupBackground=$popupBackground, " +
        "popupBorder=$popupBorder, " +
        "agendaCard=$agendaCard, " +
        "headerContentDisabled=$headerContentDisabled, " +
        "focusIndicator=$focusIndicator, " +
        "hoverBackground=$hoverBackground, " +
        "disabledContentAlpha=$disabledContentAlpha, " +
        "eventBlockAlpha=$eventBlockAlpha, " +
        "eventBlockDraggingAlpha=$eventBlockDraggingAlpha, " +
        "dragGhostAlpha=$dragGhostAlpha" +
        ")"
}

/**
 * Text styles for every piece of text a Kalendar view draws, replacing the built-in
 * Material-derived scale.
 *
 * Obtain a Material-matching instance from [KalendarThemeDefaults.typography] and `copy` the
 * tokens you want to change, or construct one from scratch to plug in a non-Material type scale:
 *
 * ```kotlin
 * KalendarTheme(
 *     typography = KalendarThemeDefaults.typography().copy(
 *         headerTitle = MyDesignSystem.type.heading3,
 *         dayNumber = MyDesignSystem.type.numeric,
 *     ),
 * ) {
 *     KalendarMonth(selectedDate = today)
 * }
 * ```
 *
 * @property headerTitle The navigation header's title, [KalendarTimeline]'s month titles, and the
 *   jump picker's year.
 * @property monthLabel Month names inside [KalendarYear]'s month grid.
 * @property dayOfWeekLabel The day-of-week column headers.
 * @property dayNumber The day number inside an ordinary day cell.
 * @property dayNumberEmphasized The day number when the cell is selected or is today. Defaults to
 *   [dayNumber] in bold; set it equal to [dayNumber] to drop the emphasis entirely.
 * @property weekDayNumber The date number in [KalendarScheduleWeek]'s day-column headers.
 * @property resourceTitle A lane's name in [KalendarResourceView]'s column headers. Its own token
 *   rather than [weekDayNumber], because a room name is not a day number: it is prose, it can be
 *   long, and a design system will usually want a different style for it. Defaults to the same
 *   scale step [weekDayNumber] uses, so the built-in look is unchanged.
 * @property eventLabel Event block titles and all-day chip titles.
 * @property eventOverflowLabel The compact `+N` label shown when a day has more events than
 *   [KalendarViewConfig.eventIndicatorCap].
 * @property hourLabel The hour-gutter labels on the Schedule views.
 * @property agendaEventTitle An event's name in [KalendarAgenda]'s rows.
 * @property agendaEventSubtitle An event's description and time range in [KalendarAgenda]'s rows,
 *   and its empty-state message.
 */
@Immutable
@Suppress("LongParameterList")
public class KalendarTypography(
    public val headerTitle: TextStyle,
    public val monthLabel: TextStyle,
    public val dayOfWeekLabel: TextStyle,
    public val dayNumber: TextStyle,
    public val dayNumberEmphasized: TextStyle,
    public val weekDayNumber: TextStyle,
    public val resourceTitle: TextStyle,
    public val eventLabel: TextStyle,
    public val eventOverflowLabel: TextStyle,
    public val hourLabel: TextStyle,
    public val agendaEventTitle: TextStyle,
    public val agendaEventSubtitle: TextStyle,
) {

    /** Returns a duplicate of this type scale with only the styles passed here replaced. */
    public fun copy(
        headerTitle: TextStyle = this.headerTitle,
        monthLabel: TextStyle = this.monthLabel,
        dayOfWeekLabel: TextStyle = this.dayOfWeekLabel,
        dayNumber: TextStyle = this.dayNumber,
        dayNumberEmphasized: TextStyle = this.dayNumberEmphasized,
        weekDayNumber: TextStyle = this.weekDayNumber,
        resourceTitle: TextStyle = this.resourceTitle,
        eventLabel: TextStyle = this.eventLabel,
        eventOverflowLabel: TextStyle = this.eventOverflowLabel,
        hourLabel: TextStyle = this.hourLabel,
        agendaEventTitle: TextStyle = this.agendaEventTitle,
        agendaEventSubtitle: TextStyle = this.agendaEventSubtitle,
    ): KalendarTypography = KalendarTypography(
        headerTitle = headerTitle,
        monthLabel = monthLabel,
        dayOfWeekLabel = dayOfWeekLabel,
        dayNumber = dayNumber,
        dayNumberEmphasized = dayNumberEmphasized,
        weekDayNumber = weekDayNumber,
        resourceTitle = resourceTitle,
        eventLabel = eventLabel,
        eventOverflowLabel = eventOverflowLabel,
        hourLabel = hourLabel,
        agendaEventTitle = agendaEventTitle,
        agendaEventSubtitle = agendaEventSubtitle,
    )

    @Suppress("CyclomaticComplexMethod")
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is KalendarTypography) return false
        if (headerTitle != other.headerTitle) return false
        if (monthLabel != other.monthLabel) return false
        if (dayOfWeekLabel != other.dayOfWeekLabel) return false
        if (dayNumber != other.dayNumber) return false
        if (dayNumberEmphasized != other.dayNumberEmphasized) return false
        if (weekDayNumber != other.weekDayNumber) return false
        if (resourceTitle != other.resourceTitle) return false
        if (eventLabel != other.eventLabel) return false
        if (eventOverflowLabel != other.eventOverflowLabel) return false
        if (hourLabel != other.hourLabel) return false
        if (agendaEventTitle != other.agendaEventTitle) return false
        if (agendaEventSubtitle != other.agendaEventSubtitle) return false
        return true
    }

    override fun hashCode(): Int {
        var result = headerTitle.hashCode()
        result = 31 * result + monthLabel.hashCode()
        result = 31 * result + dayOfWeekLabel.hashCode()
        result = 31 * result + dayNumber.hashCode()
        result = 31 * result + dayNumberEmphasized.hashCode()
        result = 31 * result + weekDayNumber.hashCode()
        result = 31 * result + resourceTitle.hashCode()
        result = 31 * result + eventLabel.hashCode()
        result = 31 * result + eventOverflowLabel.hashCode()
        result = 31 * result + hourLabel.hashCode()
        result = 31 * result + agendaEventTitle.hashCode()
        result = 31 * result + agendaEventSubtitle.hashCode()
        return result
    }

    override fun toString(): String = "KalendarTypography(" +
        "headerTitle=$headerTitle, " +
        "monthLabel=$monthLabel, " +
        "dayOfWeekLabel=$dayOfWeekLabel, " +
        "dayNumber=$dayNumber, " +
        "dayNumberEmphasized=$dayNumberEmphasized, " +
        "weekDayNumber=$weekDayNumber, " +
        "resourceTitle=$resourceTitle, " +
        "eventLabel=$eventLabel, " +
        "eventOverflowLabel=$eventOverflowLabel, " +
        "hourLabel=$hourLabel, " +
        "agendaEventTitle=$agendaEventTitle, " +
        "agendaEventSubtitle=$agendaEventSubtitle" +
        ")"
}

/**
 * Shapes used by Kalendar views.
 *
 * [dayCell] is shared by the day cell, the sliding selection indicator that animates behind it,
 * and the drag-and-drop ghost — they are separate composables that must agree visually, which is
 * why this is a theme token rather than a per-composable parameter.
 *
 * @property dayCell The day cell's selection fill, the sliding selection indicator, and the
 *   drag ghost.
 * @property eventIndicator The per-event indicator dots below a day number.
 * @property eventBlock Event blocks on the Schedule views and all-day chips.
 * @property popup The month/year jump picker surface.
 * @property agendaCard [KalendarAgenda]'s event rows.
 */
@Immutable
public class KalendarShapes(
    public val dayCell: Shape,
    public val eventIndicator: Shape,
    public val eventBlock: Shape,
    public val popup: Shape,
    public val agendaCard: Shape,
) {

    /** Returns a duplicate of these shapes with only the ones passed here replaced. */
    public fun copy(
        dayCell: Shape = this.dayCell,
        eventIndicator: Shape = this.eventIndicator,
        eventBlock: Shape = this.eventBlock,
        popup: Shape = this.popup,
        agendaCard: Shape = this.agendaCard,
    ): KalendarShapes = KalendarShapes(
        dayCell = dayCell,
        eventIndicator = eventIndicator,
        eventBlock = eventBlock,
        popup = popup,
        agendaCard = agendaCard,
    )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is KalendarShapes) return false
        if (dayCell != other.dayCell) return false
        if (eventIndicator != other.eventIndicator) return false
        if (eventBlock != other.eventBlock) return false
        if (popup != other.popup) return false
        if (agendaCard != other.agendaCard) return false
        return true
    }

    override fun hashCode(): Int {
        var result = dayCell.hashCode()
        result = 31 * result + eventIndicator.hashCode()
        result = 31 * result + eventBlock.hashCode()
        result = 31 * result + popup.hashCode()
        result = 31 * result + agendaCard.hashCode()
        return result
    }

    override fun toString(): String = "KalendarShapes(" +
        "dayCell=$dayCell, " +
        "eventIndicator=$eventIndicator, " +
        "eventBlock=$eventBlock, " +
        "popup=$popup, " +
        "agendaCard=$agendaCard" +
        ")"
}

/**
 * Sizing and spacing for every Kalendar view.
 *
 * Several of these are shared by composables that must agree visually — [dayCellPadding] is used
 * by the day cell, the sliding selection indicator, and the drag ghost; [hourGutterWidth] by both
 * the hour gutter and the header spacer above it — which is why they are theme tokens rather than
 * per-composable parameters.
 *
 * Every value has a default, so overriding one is a one-liner:
 *
 * ```kotlin
 * KalendarTheme(dimensions = KalendarDimensions(hourHeight = 96.dp)) {
 *     KalendarSchedule(selectedDate = today)
 * }
 * ```
 *
 * @property dayCellPadding Inset around each day cell, and around the selection indicator and drag
 *   ghost that align to it.
 * @property dayCellAspectRatio Width-to-height ratio of a day cell. `1f` gives square cells; a
 *   value above `1f` gives short, wide cells.
 * @property eventIndicatorSize Diameter of one event dot below a day number.
 * @property eventIndicatorSpacing Gap between adjacent event dots.
 * @property eventIndicatorBottomPadding Inset between the row of event dots and the bottom edge
 *   of the day cell. The dots are anchored to the cell rather than stacked under the number,
 *   so that the day number's baseline does not move with the event count.
 * @property headerPadding Padding around the navigation header's contents.
 * @property headerIconSlotWidth Width reserved on each side of the header title for arrow and
 *   today buttons; the title's available width is computed from it.
 * @property headerContentSpacing Gap between the navigation header and the calendar content below
 *   it. Separate from [headerPadding] so a caller can retune the header's own inset without
 *   changing how far the grid sits from it, and so a custom `header` slot of any height keeps the
 *   same gap.
 * @property hourHeight Default vertical space for one hour on the Schedule views. The
 *   `hourHeight` parameter on those composables overrides this when supplied.
 * @property hourGutterWidth Width of the Schedule views' hour-label gutter.
 * @property gridLineThickness Thickness of hour lines and separators.
 * @property nowIndicatorThickness Thickness of the current-time line.
 * @property eventBlockGap Horizontal gap between side-by-side event blocks.
 * @property eventBlockAccentWidth Width of an event block's leading accent stripe. Set to `0.dp`
 *   to remove the stripe.
 * @property eventBlockPadding Padding around an event block's label.
 * @property eventBlockResizeHandleHeight Height of the grab strip at an event block's top *and*
 *   bottom edge that starts a resize rather than a move — dragging the top edge moves the event's
 *   start, the bottom edge its end. On a block too short for two full strips plus a middle, each
 *   strip narrows to a third of the block's height, so the move region never disappears.
 * @property allDayRowPadding Padding around the all-day chip row.
 * @property allDayChipSpacing Gap between all-day chips.
 * @property allDayChipPadding Padding inside one all-day chip.
 * @property resourceColumnMinWidth Narrowest a [KalendarResourceView] lane column may become
 *   before the lanes stop dividing the width evenly and the grid scrolls horizontally instead. The
 *   `minResourceColumnWidth` parameter on that composable overrides this when supplied.
 * @property resourceHeaderPadding Inset around a [KalendarResourceView] lane header's contents.
 * @property resourceHeaderAccentHeight Thickness of the rule in the lane's colour under a
 *   [KalendarResourceView] lane header's title.
 * @property monthSpacing Vertical gap between month grids in [KalendarYear].
 * @property sectionPadding Padding around section titles — [KalendarTimeline]'s month titles and
 *   sticky header, [KalendarYear]'s month names, and [KalendarAgenda]'s date headers.
 * @property agendaRowPadding Padding inside one [KalendarAgenda] event row.
 * @property agendaRowSpacing Vertical gap between [KalendarAgenda] list items.
 * @property agendaRowGap Horizontal gap between an [KalendarAgenda] row's accent dot and its text.
 * @property agendaDotSize Diameter of an [KalendarAgenda] row's leading accent dot.
 */
@Immutable
public class KalendarDimensions(
    public val dayCellPadding: Dp = 2.dp,
    public val dayCellAspectRatio: Float = 1f,
    public val eventIndicatorSize: Dp = 4.dp,
    public val eventIndicatorSpacing: Dp = 2.dp,
    public val eventIndicatorBottomPadding: Dp = 3.dp,
    public val headerPadding: PaddingValues = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
    public val headerIconSlotWidth: Dp = 48.dp,
    public val headerContentSpacing: Dp = 8.dp,
    public val hourHeight: Dp = 64.dp,
    public val hourGutterWidth: Dp = 48.dp,
    public val gridLineThickness: Dp = 1.dp,
    public val nowIndicatorThickness: Dp = 2.dp,
    public val eventBlockGap: Dp = 1.dp,
    public val eventBlockAccentWidth: Dp = 3.dp,
    public val eventBlockPadding: PaddingValues = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
    public val eventBlockResizeHandleHeight: Dp = 12.dp,
    public val allDayRowPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
    public val allDayChipSpacing: Dp = 4.dp,
    public val allDayChipPadding: PaddingValues = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
    public val resourceColumnMinWidth: Dp = 96.dp,
    public val resourceHeaderPadding: Dp = 4.dp,
    public val resourceHeaderAccentHeight: Dp = 2.dp,
    public val monthSpacing: Dp = 16.dp,
    public val sectionPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
    public val agendaRowPadding: PaddingValues = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
    public val agendaRowSpacing: Dp = 4.dp,
    public val agendaRowGap: Dp = 12.dp,
    public val agendaDotSize: Dp = 10.dp,
) {

    /** Returns a duplicate of these dimensions with only the values passed here replaced. */
    public fun copy(
        dayCellPadding: Dp = this.dayCellPadding,
        dayCellAspectRatio: Float = this.dayCellAspectRatio,
        eventIndicatorSize: Dp = this.eventIndicatorSize,
        eventIndicatorSpacing: Dp = this.eventIndicatorSpacing,
        eventIndicatorBottomPadding: Dp = this.eventIndicatorBottomPadding,
        headerPadding: PaddingValues = this.headerPadding,
        headerIconSlotWidth: Dp = this.headerIconSlotWidth,
        headerContentSpacing: Dp = this.headerContentSpacing,
        hourHeight: Dp = this.hourHeight,
        hourGutterWidth: Dp = this.hourGutterWidth,
        gridLineThickness: Dp = this.gridLineThickness,
        nowIndicatorThickness: Dp = this.nowIndicatorThickness,
        eventBlockGap: Dp = this.eventBlockGap,
        eventBlockAccentWidth: Dp = this.eventBlockAccentWidth,
        eventBlockPadding: PaddingValues = this.eventBlockPadding,
        eventBlockResizeHandleHeight: Dp = this.eventBlockResizeHandleHeight,
        allDayRowPadding: PaddingValues = this.allDayRowPadding,
        allDayChipSpacing: Dp = this.allDayChipSpacing,
        allDayChipPadding: PaddingValues = this.allDayChipPadding,
        resourceColumnMinWidth: Dp = this.resourceColumnMinWidth,
        resourceHeaderPadding: Dp = this.resourceHeaderPadding,
        resourceHeaderAccentHeight: Dp = this.resourceHeaderAccentHeight,
        monthSpacing: Dp = this.monthSpacing,
        sectionPadding: PaddingValues = this.sectionPadding,
        agendaRowPadding: PaddingValues = this.agendaRowPadding,
        agendaRowSpacing: Dp = this.agendaRowSpacing,
        agendaRowGap: Dp = this.agendaRowGap,
        agendaDotSize: Dp = this.agendaDotSize,
    ): KalendarDimensions = KalendarDimensions(
        dayCellPadding = dayCellPadding,
        dayCellAspectRatio = dayCellAspectRatio,
        eventIndicatorSize = eventIndicatorSize,
        eventIndicatorSpacing = eventIndicatorSpacing,
        eventIndicatorBottomPadding = eventIndicatorBottomPadding,
        headerPadding = headerPadding,
        headerIconSlotWidth = headerIconSlotWidth,
        headerContentSpacing = headerContentSpacing,
        hourHeight = hourHeight,
        hourGutterWidth = hourGutterWidth,
        gridLineThickness = gridLineThickness,
        nowIndicatorThickness = nowIndicatorThickness,
        eventBlockGap = eventBlockGap,
        eventBlockAccentWidth = eventBlockAccentWidth,
        eventBlockPadding = eventBlockPadding,
        eventBlockResizeHandleHeight = eventBlockResizeHandleHeight,
        allDayRowPadding = allDayRowPadding,
        allDayChipSpacing = allDayChipSpacing,
        allDayChipPadding = allDayChipPadding,
        resourceColumnMinWidth = resourceColumnMinWidth,
        resourceHeaderPadding = resourceHeaderPadding,
        resourceHeaderAccentHeight = resourceHeaderAccentHeight,
        monthSpacing = monthSpacing,
        sectionPadding = sectionPadding,
        agendaRowPadding = agendaRowPadding,
        agendaRowSpacing = agendaRowSpacing,
        agendaRowGap = agendaRowGap,
        agendaDotSize = agendaDotSize,
    )

    @Suppress("CyclomaticComplexMethod")
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is KalendarDimensions) return false
        if (dayCellPadding != other.dayCellPadding) return false
        if (dayCellAspectRatio != other.dayCellAspectRatio) return false
        if (eventIndicatorSize != other.eventIndicatorSize) return false
        if (eventIndicatorSpacing != other.eventIndicatorSpacing) return false
        if (eventIndicatorBottomPadding != other.eventIndicatorBottomPadding) return false
        if (headerPadding != other.headerPadding) return false
        if (headerIconSlotWidth != other.headerIconSlotWidth) return false
        if (headerContentSpacing != other.headerContentSpacing) return false
        if (hourHeight != other.hourHeight) return false
        if (hourGutterWidth != other.hourGutterWidth) return false
        if (gridLineThickness != other.gridLineThickness) return false
        if (nowIndicatorThickness != other.nowIndicatorThickness) return false
        if (eventBlockGap != other.eventBlockGap) return false
        if (eventBlockAccentWidth != other.eventBlockAccentWidth) return false
        if (eventBlockPadding != other.eventBlockPadding) return false
        if (eventBlockResizeHandleHeight != other.eventBlockResizeHandleHeight) return false
        if (allDayRowPadding != other.allDayRowPadding) return false
        if (allDayChipSpacing != other.allDayChipSpacing) return false
        if (allDayChipPadding != other.allDayChipPadding) return false
        if (resourceColumnMinWidth != other.resourceColumnMinWidth) return false
        if (resourceHeaderPadding != other.resourceHeaderPadding) return false
        if (resourceHeaderAccentHeight != other.resourceHeaderAccentHeight) return false
        if (monthSpacing != other.monthSpacing) return false
        if (sectionPadding != other.sectionPadding) return false
        if (agendaRowPadding != other.agendaRowPadding) return false
        if (agendaRowSpacing != other.agendaRowSpacing) return false
        if (agendaRowGap != other.agendaRowGap) return false
        if (agendaDotSize != other.agendaDotSize) return false
        return true
    }

    override fun hashCode(): Int {
        var result = dayCellPadding.hashCode()
        result = 31 * result + dayCellAspectRatio.hashCode()
        result = 31 * result + eventIndicatorSize.hashCode()
        result = 31 * result + eventIndicatorSpacing.hashCode()
        result = 31 * result + eventIndicatorBottomPadding.hashCode()
        result = 31 * result + headerPadding.hashCode()
        result = 31 * result + headerIconSlotWidth.hashCode()
        result = 31 * result + headerContentSpacing.hashCode()
        result = 31 * result + hourHeight.hashCode()
        result = 31 * result + hourGutterWidth.hashCode()
        result = 31 * result + gridLineThickness.hashCode()
        result = 31 * result + nowIndicatorThickness.hashCode()
        result = 31 * result + eventBlockGap.hashCode()
        result = 31 * result + eventBlockAccentWidth.hashCode()
        result = 31 * result + eventBlockPadding.hashCode()
        result = 31 * result + eventBlockResizeHandleHeight.hashCode()
        result = 31 * result + allDayRowPadding.hashCode()
        result = 31 * result + allDayChipSpacing.hashCode()
        result = 31 * result + allDayChipPadding.hashCode()
        result = 31 * result + resourceColumnMinWidth.hashCode()
        result = 31 * result + resourceHeaderPadding.hashCode()
        result = 31 * result + resourceHeaderAccentHeight.hashCode()
        result = 31 * result + monthSpacing.hashCode()
        result = 31 * result + sectionPadding.hashCode()
        result = 31 * result + agendaRowPadding.hashCode()
        result = 31 * result + agendaRowSpacing.hashCode()
        result = 31 * result + agendaRowGap.hashCode()
        result = 31 * result + agendaDotSize.hashCode()
        return result
    }

    override fun toString(): String = "KalendarDimensions(" +
        "dayCellPadding=$dayCellPadding, " +
        "dayCellAspectRatio=$dayCellAspectRatio, " +
        "eventIndicatorSize=$eventIndicatorSize, " +
        "eventIndicatorSpacing=$eventIndicatorSpacing, " +
        "eventIndicatorBottomPadding=$eventIndicatorBottomPadding, " +
        "headerPadding=$headerPadding, " +
        "headerIconSlotWidth=$headerIconSlotWidth, " +
        "headerContentSpacing=$headerContentSpacing, " +
        "hourHeight=$hourHeight, " +
        "hourGutterWidth=$hourGutterWidth, " +
        "gridLineThickness=$gridLineThickness, " +
        "nowIndicatorThickness=$nowIndicatorThickness, " +
        "eventBlockGap=$eventBlockGap, " +
        "eventBlockAccentWidth=$eventBlockAccentWidth, " +
        "eventBlockPadding=$eventBlockPadding, " +
        "eventBlockResizeHandleHeight=$eventBlockResizeHandleHeight, " +
        "allDayRowPadding=$allDayRowPadding, " +
        "allDayChipSpacing=$allDayChipSpacing, " +
        "allDayChipPadding=$allDayChipPadding, " +
        "resourceColumnMinWidth=$resourceColumnMinWidth, " +
        "resourceHeaderPadding=$resourceHeaderPadding, " +
        "resourceHeaderAccentHeight=$resourceHeaderAccentHeight, " +
        "monthSpacing=$monthSpacing, " +
        "sectionPadding=$sectionPadding, " +
        "agendaRowPadding=$agendaRowPadding, " +
        "agendaRowSpacing=$agendaRowSpacing, " +
        "agendaRowGap=$agendaRowGap, " +
        "agendaDotSize=$agendaDotSize" +
        ")"
}

/**
 * Motion settings for every Kalendar view.
 *
 * Set [enabled] to `false` to make every transition instant — the right response to a platform
 * reduced-motion preference, and what screenshot tests want:
 *
 * ```kotlin
 * KalendarTheme(animations = KalendarAnimations(enabled = false)) { ... }
 * ```
 *
 * @property enabled Master switch. When `false`, every animation below snaps to its target and
 *   [pressScale] is ignored.
 * @property selectionColorSpec Drives the day cell's selection fill fading in and out.
 * @property selectionIndicatorSpec Drives the indicator that slides between selected day cells.
 * @property pressScale Scale a day cell shrinks to while pressed. `1f` disables the effect.
 * @property pressScaleSpec Drives the [pressScale] animation.
 * @property titleTransitionMillis Duration of the header/sticky-title crossfade.
 */
@Immutable
public class KalendarAnimations(
    public val enabled: Boolean = true,
    public val selectionColorSpec: AnimationSpec<Color> = spring(),
    public val selectionIndicatorSpec: AnimationSpec<Offset> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessHigh,
    ),
    public val pressScale: Float = DEFAULT_PRESS_SCALE,
    public val pressScaleSpec: AnimationSpec<Float> = spring(),
    public val titleTransitionMillis: Int = DEFAULT_TITLE_TRANSITION_MS,
) {

    /** Returns a duplicate of these motion settings with only the values passed here replaced. */
    public fun copy(
        enabled: Boolean = this.enabled,
        selectionColorSpec: AnimationSpec<Color> = this.selectionColorSpec,
        selectionIndicatorSpec: AnimationSpec<Offset> = this.selectionIndicatorSpec,
        pressScale: Float = this.pressScale,
        pressScaleSpec: AnimationSpec<Float> = this.pressScaleSpec,
        titleTransitionMillis: Int = this.titleTransitionMillis,
    ): KalendarAnimations = KalendarAnimations(
        enabled = enabled,
        selectionColorSpec = selectionColorSpec,
        selectionIndicatorSpec = selectionIndicatorSpec,
        pressScale = pressScale,
        pressScaleSpec = pressScaleSpec,
        titleTransitionMillis = titleTransitionMillis,
    )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is KalendarAnimations) return false
        if (enabled != other.enabled) return false
        if (selectionColorSpec != other.selectionColorSpec) return false
        if (selectionIndicatorSpec != other.selectionIndicatorSpec) return false
        if (pressScale != other.pressScale) return false
        if (pressScaleSpec != other.pressScaleSpec) return false
        if (titleTransitionMillis != other.titleTransitionMillis) return false
        return true
    }

    override fun hashCode(): Int {
        var result = enabled.hashCode()
        result = 31 * result + selectionColorSpec.hashCode()
        result = 31 * result + selectionIndicatorSpec.hashCode()
        result = 31 * result + pressScale.hashCode()
        result = 31 * result + pressScaleSpec.hashCode()
        result = 31 * result + titleTransitionMillis.hashCode()
        return result
    }

    override fun toString(): String = "KalendarAnimations(" +
        "enabled=$enabled, " +
        "selectionColorSpec=$selectionColorSpec, " +
        "selectionIndicatorSpec=$selectionIndicatorSpec, " +
        "pressScale=$pressScale, " +
        "pressScaleSpec=$pressScaleSpec, " +
        "titleTransitionMillis=$titleTransitionMillis" +
        ")"
}

/**
 * Defaults for [KalendarTheme].
 *
 * This is the only place in Kalendar that reads Material at all, and it reads it to *derive token
 * values*, never to render: every calendar view draws with Compose Foundation, so an app with its
 * own design system — or no Material 3 at all — gets a calendar whose appearance is decided
 * entirely by the tokens on [KalendarThemeTokens].
 *
 * Three palettes are available, and which one you want depends on your app:
 *
 * - [colors] (the default) derives from the ambient [MaterialTheme.colorScheme], so a Material 3
 *   app gets its own brand colours and its own light/dark scheme for free.
 * - [systemColors] returns Kalendar's own palette following the system light/dark setting, with no
 *   dependency on a Material theme being present — use it if your app doesn't use Material 3.
 * - [lightColors] and [darkColors] are those two palettes, chosen explicitly, for apps that drive
 *   their own theme switch instead of following the system.
 */
public object KalendarThemeDefaults {

    /**
     * [KalendarColors] resolved from the ambient [MaterialTheme.colorScheme] — the default, and
     * correct in both light and dark mode for any app wrapped in a Material 3 theme.
     *
     * Compose Material's colour scheme falls back to its baseline *light* scheme when no
     * `MaterialTheme` is present, so an app that doesn't use Material 3 should pass
     * [systemColors] instead, which tracks dark mode on its own.
     */
    @Composable
    public fun colors(): KalendarColors = MaterialTheme.colorScheme.let { scheme ->
        remember(scheme) { materialColors(scheme) }
    }

    /** Kalendar's built-in [KalendarDimensions]. */
    public fun dimensions(): KalendarDimensions = DefaultDimensions

    /** Kalendar's built-in [KalendarAnimations]. */
    public fun animations(): KalendarAnimations = DefaultAnimations

    /** Kalendar's built-in [KalendarStrings] — English, and the thing to override to localize. */
    public fun strings(): KalendarStrings = DefaultStrings

    private fun materialColors(scheme: ColorScheme): KalendarColors = KalendarColors(
        background = scheme.surface,
        selectionBackground = scheme.primaryContainer,
        onSelectionBackground = scheme.onPrimaryContainer,
        todayContent = scheme.primary,
        dayContent = scheme.onSurface,
        dayLabelContent = scheme.onSurfaceVariant,
        headerContent = scheme.onSurface,
        eventIndicator = scheme.primary,
        nowIndicator = scheme.error,
        gridLine = scheme.outlineVariant,
        dragGhostBackground = scheme.secondaryContainer,
        popupBackground = scheme.surfaceContainerHigh,
        popupBorder = scheme.outlineVariant,
        agendaCard = scheme.surfaceContainerLow,
    )

    /**
     * Kalendar's own palette for the current system light/dark setting, independent of Material.
     *
     * ```kotlin
     * KalendarTheme(colors = KalendarThemeDefaults.systemColors()) {
     *     KalendarMonth(selectedDate = today)
     * }
     * ```
     */
    @Composable
    public fun systemColors(): KalendarColors =
        if (isSystemInDarkTheme()) {
            darkColors()
        } else {
            lightColors()
        }

    /** Kalendar's built-in light palette. Material-free, so it works in any Compose app. */
    @Suppress("MagicNumber")
    public fun lightColors(): KalendarColors = KalendarColors(
        background = Color(0xFFFFFBFE),
        selectionBackground = Color(0xFFE8DEF8),
        onSelectionBackground = Color(0xFF1D192B),
        todayContent = Color(0xFF6750A4),
        dayContent = Color(0xFF1C1B1F),
        dayLabelContent = Color(0xFF49454F),
        headerContent = Color(0xFF1C1B1F),
        eventIndicator = Color(0xFF6750A4),
        nowIndicator = Color(0xFFB3261E),
        gridLine = Color(0xFFCAC4D0),
        dragGhostBackground = Color(0xFFE8DEF8),
        popupBackground = Color(0xFFECE6F0),
        popupBorder = Color(0xFFCAC4D0),
        agendaCard = Color(0xFFF7F2FA),
    )

    /** Kalendar's built-in dark palette. Material-free, so it works in any Compose app. */
    @Suppress("MagicNumber")
    public fun darkColors(): KalendarColors = KalendarColors(
        background = Color(0xFF1C1B1F),
        selectionBackground = Color(0xFF4F378B),
        onSelectionBackground = Color(0xFFEADDFF),
        todayContent = Color(0xFFD0BCFF),
        dayContent = Color(0xFFE6E1E5),
        dayLabelContent = Color(0xFFCAC4D0),
        headerContent = Color(0xFFE6E1E5),
        eventIndicator = Color(0xFFD0BCFF),
        nowIndicator = Color(0xFFF2B8B5),
        gridLine = Color(0xFF49454F),
        dragGhostBackground = Color(0xFF4A4458),
        popupBackground = Color(0xFF2B2930),
        popupBorder = Color(0xFF49454F),
        agendaCard = Color(0xFF26242A),
    )

    /** [KalendarTypography] resolved from the ambient [MaterialTheme.typography]. */
    @Composable
    public fun typography(): KalendarTypography = MaterialTheme.typography.let { scale ->
        remember(scale) { materialTypography(scale) }
    }

    private fun materialTypography(scale: Typography): KalendarTypography = KalendarTypography(
        headerTitle = scale.titleMedium,
        monthLabel = scale.labelLarge,
        dayOfWeekLabel = scale.labelSmall,
        dayNumber = scale.bodyLarge,
        dayNumberEmphasized = scale.bodyLarge.copy(fontWeight = FontWeight.Bold),
        weekDayNumber = scale.labelLarge,
        resourceTitle = scale.labelLarge,
        eventLabel = scale.labelSmall,
        eventOverflowLabel = scale.labelSmall.copy(fontSize = EVENT_OVERFLOW_FONT_SIZE),
        hourLabel = scale.labelSmall,
        agendaEventTitle = scale.titleSmall,
        agendaEventSubtitle = scale.bodySmall,
    )

    /** Kalendar's built-in [KalendarShapes] — circular day cells, lightly rounded event blocks. */
    public fun shapes(): KalendarShapes = DefaultShapes
}

private val EVENT_OVERFLOW_FONT_SIZE = 9.sp
private val EVENT_BLOCK_CORNER_RADIUS = 4.dp
private val POPUP_CORNER_RADIUS = 12.dp
private val AGENDA_CARD_CORNER_RADIUS = 12.dp
private const val DEFAULT_DISABLED_ALPHA = 0.38f
private const val DEFAULT_HOVER_ALPHA = 0.08f
private const val DEFAULT_EVENT_BLOCK_ALPHA = 0.18f
private const val DEFAULT_EVENT_BLOCK_DRAGGING_ALPHA = 0.45f
private const val DEFAULT_DRAG_GHOST_ALPHA = 0.6f
private const val DEFAULT_PRESS_SCALE = 0.92f
private const val DEFAULT_TITLE_TRANSITION_MS = 150

/**
 * Every Kalendar token set in one value, so a whole look can be passed around, `copy`-ed, and
 * overridden for a single calendar without wrapping it in a [KalendarTheme].
 *
 * Read the ambient instance with [KalendarTheme.tokens], and hand a modified copy to any view's
 * `theme` parameter to restyle just that one:
 *
 * ```kotlin
 * KalendarMonth(
 *     selectedDate = today,
 *     theme = KalendarTheme.tokens.copy(
 *         colors = KalendarTheme.colors.copy(selectionBackground = BrandPurple),
 *     ),
 * )
 * ```
 *
 * @property colors Colour tokens. See [KalendarColors].
 * @property typography Text styles. See [KalendarTypography].
 * @property shapes Corner shapes. See [KalendarShapes].
 * @property dimensions Sizing and spacing. See [KalendarDimensions].
 * @property animations Motion settings. See [KalendarAnimations].
 * @property strings Localizable text and accessibility labels. See [KalendarStrings].
 */
@Immutable
public class KalendarThemeTokens(
    public val colors: KalendarColors,
    public val typography: KalendarTypography,
    public val shapes: KalendarShapes,
    public val dimensions: KalendarDimensions,
    public val animations: KalendarAnimations,
    public val strings: KalendarStrings,
) {

    /** Returns a duplicate of this token set with only the token sets passed here replaced. */
    public fun copy(
        colors: KalendarColors = this.colors,
        typography: KalendarTypography = this.typography,
        shapes: KalendarShapes = this.shapes,
        dimensions: KalendarDimensions = this.dimensions,
        animations: KalendarAnimations = this.animations,
        strings: KalendarStrings = this.strings,
    ): KalendarThemeTokens = KalendarThemeTokens(
        colors = colors,
        typography = typography,
        shapes = shapes,
        dimensions = dimensions,
        animations = animations,
        strings = strings,
    )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is KalendarThemeTokens) return false
        if (colors != other.colors) return false
        if (typography != other.typography) return false
        if (shapes != other.shapes) return false
        if (dimensions != other.dimensions) return false
        if (animations != other.animations) return false
        if (strings != other.strings) return false
        return true
    }

    override fun hashCode(): Int {
        var result = colors.hashCode()
        result = 31 * result + typography.hashCode()
        result = 31 * result + shapes.hashCode()
        result = 31 * result + dimensions.hashCode()
        result = 31 * result + animations.hashCode()
        result = 31 * result + strings.hashCode()
        return result
    }

    override fun toString(): String = "KalendarThemeTokens(" +
        "colors=$colors, " +
        "typography=$typography, " +
        "shapes=$shapes, " +
        "dimensions=$dimensions, " +
        "animations=$animations, " +
        "strings=$strings" +
        ")"
}

internal val LocalKalendarTokens = staticCompositionLocalOf<KalendarThemeTokens?> { null }

/**
 * Accessors for the ambient Kalendar tokens, mirroring how `MaterialTheme.colorScheme` works.
 *
 * Outside a [KalendarTheme] these fall back to [KalendarThemeDefaults], so reading them is always
 * safe.
 */
public object KalendarTheme {

    /** The ambient [KalendarColors]. */
    public val colors: KalendarColors
        @Composable get() = kalendarColors()

    /** The ambient [KalendarTypography]. */
    public val typography: KalendarTypography
        @Composable get() = kalendarTypography()

    /** The ambient [KalendarShapes]. */
    public val shapes: KalendarShapes
        @Composable get() = kalendarShapes()

    /** The ambient [KalendarDimensions]. */
    public val dimensions: KalendarDimensions
        @Composable get() = kalendarDimensions()

    /** The ambient [KalendarAnimations]. */
    public val animations: KalendarAnimations
        @Composable get() = kalendarAnimations()

    /** The ambient [KalendarStrings]. */
    public val strings: KalendarStrings
        @Composable get() = kalendarStrings()

    /** All ambient token sets bundled together, ready to `copy` into a view's `theme` parameter. */
    public val tokens: KalendarThemeTokens
        @Composable get() = LocalKalendarTokens.current ?: KalendarThemeTokens(
            colors = KalendarThemeDefaults.colors(),
            typography = KalendarThemeDefaults.typography(),
            shapes = KalendarThemeDefaults.shapes(),
            dimensions = KalendarDimensions(),
            animations = KalendarAnimations(),
            strings = KalendarStrings(),
        )
}

/**
 * Provides colours, type, shapes, sizing, and motion to every Kalendar view inside [content].
 *
 * Wrapping is optional — views used outside a [KalendarTheme] fall back to [KalendarThemeDefaults]
 * — and each token set is independent, so overriding one leaves the rest at their defaults:
 *
 * ```kotlin
 * KalendarTheme(
 *     colors = KalendarThemeDefaults.colors().copy(selectionBackground = BrandPurple),
 *     shapes = KalendarShapes(dayCell = RoundedCornerShape(8.dp), ...),
 *     dimensions = KalendarDimensions(hourHeight = 96.dp),
 * ) {
 *     KalendarMonth(selectedDate = today)
 * }
 * ```
 *
 * @param colors Colour tokens. Defaults to [KalendarThemeDefaults.colors], which follows the
 *   ambient Material theme; pass [KalendarThemeDefaults.systemColors] in a non-Material app.
 * @param typography Text styles. Defaults to [KalendarThemeDefaults.typography].
 * @param shapes Corner shapes. Defaults to [KalendarThemeDefaults.shapes].
 * @param dimensions Sizing and spacing. Defaults to [KalendarDimensions]'s own defaults.
 * @param animations Motion settings. Pass `KalendarAnimations(enabled = false)` for reduced
 *   motion or deterministic screenshot tests.
 * @param content The calendar views this theme applies to.
 */
@Composable
public fun KalendarTheme(
    colors: KalendarColors = KalendarTheme.colors,
    typography: KalendarTypography = KalendarTheme.typography,
    shapes: KalendarShapes = KalendarTheme.shapes,
    dimensions: KalendarDimensions = KalendarTheme.dimensions,
    animations: KalendarAnimations = KalendarTheme.animations,
    strings: KalendarStrings = KalendarTheme.strings,
    content: @Composable () -> Unit,
) {
    val tokens = remember(colors, typography, shapes, dimensions, animations, strings) {
        KalendarThemeTokens(
            colors = colors,
            typography = typography,
            shapes = shapes,
            dimensions = dimensions,
            animations = animations,
            strings = strings,
        )
    }
    CompositionLocalProvider(LocalKalendarTokens provides tokens, content = content)
}

/** The effective [KalendarColors]: the ambient [KalendarTheme]'s, or Material-derived defaults. */
@Composable
internal fun kalendarColors(): KalendarColors =
    LocalKalendarTokens.current?.colors ?: KalendarThemeDefaults.colors()

/** The effective [KalendarTypography]: the ambient [KalendarTheme]'s, or Material-derived defaults. */
@Composable
internal fun kalendarTypography(): KalendarTypography =
    LocalKalendarTokens.current?.typography ?: KalendarThemeDefaults.typography()

/** The effective [KalendarShapes]: the ambient [KalendarTheme]'s, or Kalendar's built-in shapes. */
@Composable
@ReadOnlyComposable
internal fun kalendarShapes(): KalendarShapes =
    LocalKalendarTokens.current?.shapes ?: KalendarThemeDefaults.shapes()

/** The effective [KalendarDimensions]: the ambient [KalendarTheme]'s, or the built-in defaults. */
@Composable
@ReadOnlyComposable
internal fun kalendarDimensions(): KalendarDimensions =
    LocalKalendarTokens.current?.dimensions ?: KalendarThemeDefaults.dimensions()

/** The effective [KalendarAnimations]: the ambient [KalendarTheme]'s, or the built-in defaults. */
@Composable
@ReadOnlyComposable
internal fun kalendarAnimations(): KalendarAnimations =
    LocalKalendarTokens.current?.animations ?: KalendarThemeDefaults.animations()

/** The effective [KalendarStrings]: the ambient [KalendarTheme]'s, or the English defaults. */
@Composable
@ReadOnlyComposable
internal fun kalendarStrings(): KalendarStrings =
    LocalKalendarTokens.current?.strings ?: KalendarThemeDefaults.strings()

private val DefaultShapes = KalendarShapes(
    dayCell = CircleShape,
    eventIndicator = CircleShape,
    eventBlock = RoundedCornerShape(EVENT_BLOCK_CORNER_RADIUS),
    popup = RoundedCornerShape(POPUP_CORNER_RADIUS),
    agendaCard = RoundedCornerShape(AGENDA_CARD_CORNER_RADIUS),
)
private val DefaultDimensions = KalendarDimensions()
private val DefaultAnimations = KalendarAnimations()
private val DefaultStrings = KalendarStrings()
