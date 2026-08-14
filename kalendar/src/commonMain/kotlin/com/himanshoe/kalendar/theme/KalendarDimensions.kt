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

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.himanshoe.kalendar.KalendarAgenda
import com.himanshoe.kalendar.KalendarResourceView
import com.himanshoe.kalendar.KalendarTimeline
import com.himanshoe.kalendar.KalendarYear

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
