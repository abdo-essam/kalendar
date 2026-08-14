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

package com.himanshoe.sample.playground

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.himanshoe.kalendar.component.kalendarDaySemantics
import com.himanshoe.kalendar.slot.KalendarDayScope
import com.himanshoe.kalendar.slot.KalendarHeaderScope
import com.himanshoe.kalendar.theme.KalendarTheme

/**
 * A hand-written replacement for the built-in day cell, wired into the `dayContent` slot.
 *
 * It reads every colour and shape from the ambient [KalendarTheme] — so the playground's controls
 * still drive it — and applies [kalendarDaySemantics], which is what keeps a custom cell clickable,
 * announced to screen readers, and reported as selected. That modifier is the whole point of the
 * example: replacing the default cell must not mean replacing its accessibility.
 */
@Composable
internal fun BrandDayCell(scope: KalendarDayScope, onClick: () -> Unit) {
    val colors = KalendarTheme.colors
    val shapes = KalendarTheme.shapes
    val dimensions = KalendarTheme.dimensions
    val background = when {
        scope.isSelected -> colors.selectionBackground
        scope.isToday -> colors.todayContent.copy(alpha = 0.14f)
        else -> Color.Transparent
    }
    val content = if (scope.isSelected) colors.onSelectionBackground else colors.dayContent
    Column(
        modifier = Modifier
            .aspectRatio(1f)
            .padding(dimensions.dayCellPadding)
            .clip(shapes.dayCell)
            .background(background)
            .border(
                width = 1.dp,
                color = if (scope.isToday) colors.todayContent else colors.gridLine,
                shape = shapes.dayCell,
            )
            .kalendarDaySemantics(scope = scope, onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 3.dp)
            .alpha(if (scope.isDisabled) colors.disabledContentAlpha else 1f),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = scope.date.day.toString(),
            color = content,
            fontSize = 13.sp,
            fontWeight = if (scope.isToday) FontWeight.Black else FontWeight.Medium,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            scope.events.take(2).forEach { event ->
                Box(
                    modifier = Modifier
                        .width(dimensions.eventIndicatorSize * 2)
                        .height(dimensions.eventIndicatorSize)
                        .clip(RoundedCornerShape(1.dp))
                        .background(event.eventColor ?: colors.eventIndicator),
                )
            }
        }
    }
}

/**
 * A hand-written replacement for the built-in navigation header, wired into the `header` slot.
 *
 * Navigation still runs entirely through [KalendarHeaderScope], so the chrome can change shape
 * without the calendar losing its paging, bounds checks, or jump-to-today.
 */
@Composable
internal fun BrandHeader(scope: KalendarHeaderScope) {
    val colors = KalendarTheme.colors
    val typography = KalendarTheme.typography
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "PLANNER",
                color = colors.dayLabelContent,
                fontSize = 9.sp,
                letterSpacing = 2.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = scope.title,
                color = colors.headerContent,
                style = typography.headerTitle,
                fontWeight = FontWeight.Black,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            HeaderPill(label = "<", enabled = scope.canScrollBackward, onClick = scope::goToPreviousPage)
            HeaderPill(label = "Today", enabled = true, onClick = scope::goToToday)
            HeaderPill(label = ">", enabled = scope.canScrollForward, onClick = scope::goToNextPage)
        }
    }
}

@Composable
private fun HeaderPill(label: String, enabled: Boolean, onClick: () -> Unit) {
    val colors = KalendarTheme.colors
    Text(
        text = label,
        color = colors.headerContent,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .alpha(if (enabled) 1f else colors.disabledContentAlpha)
            .clip(RoundedCornerShape(8.dp))
            .background(colors.selectionBackground)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    )
}
