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

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import com.himanshoe.kalendar.KalendarAgenda
import com.himanshoe.kalendar.KalendarSchedule
import com.himanshoe.kalendar.KalendarViewConfig
import com.himanshoe.kalendar.component.KalendarDayCellDefaults
import com.himanshoe.kalendar.state.KalendarViewState

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

private const val DEFAULT_DISABLED_ALPHA = 0.38f
private const val DEFAULT_HOVER_ALPHA = 0.08f
private const val DEFAULT_EVENT_BLOCK_ALPHA = 0.18f
private const val DEFAULT_EVENT_BLOCK_DRAGGING_ALPHA = 0.45f
private const val DEFAULT_DRAG_GHOST_ALPHA = 0.6f
