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

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.himanshoe.kalendar.foundation.event.KalendarEvent
import kotlinx.datetime.LocalDate

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
data class KalendarDayScope(
    val date: LocalDate,
    val isSelected: Boolean,
    val isToday: Boolean,
    val isDisabled: Boolean,
    val events: List<KalendarEvent>,
)

/**
 * Default day-cell rendering, used by [KalendarWeek]/[KalendarMonth]/[KalendarYear] when no
 * `dayContent` slot is supplied.
 */
object KalendarDayCellDefaults {

    /**
     * Renders [scope] as a circular date cell: bold text for today, a filled background for the
     * selected state, reduced opacity and no click handling when disabled, and a small dot below
     * the day number when [KalendarDayScope.events] is non-empty.
     */
    @Composable
    fun Cell(
        scope: KalendarDayScope,
        onClick: () -> Unit,
        modifier: Modifier = Modifier,
    ) {
        val colors = MaterialTheme.colorScheme
        val background =
            if (scope.isSelected) {
                colors.primaryContainer
            } else {
                Color.Transparent
            }
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

        Box(
            contentAlignment = Alignment.Center,
            modifier = modifier
                .aspectRatio(1f)
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
                .let {
                    if (scope.isDisabled) {
                        it
                    } else {
                        it.clickable(onClick = onClick)
                    }
                },
        ) {
            Text(
                text = scope.date.dayOfMonth.toString(),
                color = textColor,
                fontWeight = fontWeight,
            )
            if (scope.events.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .padding(top = 22.dp)
                        .clip(CircleShape)
                        .background(
                            if (scope.isSelected) {
                                textColor
                            } else {
                                colors.primary
                            }
                        )
                        .aspectRatio(1f)
                        .padding(2.dp),
                )
            }
        }
    }
}
