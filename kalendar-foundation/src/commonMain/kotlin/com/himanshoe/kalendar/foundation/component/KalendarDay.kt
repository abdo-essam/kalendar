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

package com.himanshoe.kalendar.foundation.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastForEachIndexed
import com.himanshoe.kalendar.foundation.action.KalendarSelectedDayRange
import com.himanshoe.kalendar.foundation.component.config.KalendarDayConfig
import com.himanshoe.kalendar.foundation.event.KalendarEvent
import com.himanshoe.kalendar.foundation.event.KalendarEvents
import com.himanshoe.kalendar.foundation.ext.circleLayout
import com.himanshoe.kalendar.foundation.ext.dayBackgroundColor
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlin.time.Clock

/**
 * Renders a single day cell inside a calendar grid.
 *
 * The cell shows the day-of-month number and up to three event indicator dots below it.
 * Today's date is distinguished by a thin border ring even when not selected.
 * Disabled dates are rendered at 38 % opacity and do not respond to taps.
 * When there are more than three events, two dots are shown followed by a "+N" overflow label.
 *
 * Accessibility: each cell has a [contentDescription] built from its date, today/selected/disabled
 * state and event count so that screen readers can announce it fully.
 * A [HapticFeedbackType.LongPress] vibration pulse is emitted on each successful tap.
 *
 * @param date The [LocalDate] this cell represents.
 * @param modifier [Modifier] applied to the outermost layout.
 * @param selectedDates The list of currently selected dates used for multi-select and range
 *   highlighting.
 * @param selectedRange The currently active date range, used to highlight the span between start
 *   and end dates inclusively.
 * @param selectedDate The single currently selected date. Defaults to [date] (no selection).
 * @param events The list of events that fall on [date]. Up to three indicator dots are shown;
 *   per-event colour from [KalendarEvent.eventColor] is used when available, falling back to
 *   [calendarColors] and then [KalendarDayConfig.indicatorColor].
 * @param dayConfig Visual configuration (size, colours, text style) for the day cell.
 * @param isDisabled When `true` the cell is rendered at reduced opacity and taps are ignored.
 * @param calendarColors Optional map from [KalendarEvent.calendarId] to a display colour. Used
 *   to tint indicator dots when [KalendarEvent.eventColor] is `null`.
 * @param onDayClick Callback invoked when the user taps an enabled cell.
 */
@Composable
fun KalendarDay(
    date: LocalDate,
    modifier: Modifier = Modifier,
    selectedDates: List<LocalDate> = emptyList(),
    selectedRange: KalendarSelectedDayRange? = null,
    selectedDate: LocalDate = date,
    events: KalendarEvents = emptyList(),
    dayConfig: KalendarDayConfig = KalendarDayConfig(),
    isDisabled: Boolean = false,
    calendarColors: Map<String, Color> = emptyMap(),
    onDayClick: (LocalDate, List<KalendarEvent>) -> Unit = { _, _ -> },
) {
    KalendarDayContent(
        date = date,
        selectedDate = selectedDate,
        events = events,
        selectedRange = selectedRange,
        dayConfig = dayConfig,
        modifier = modifier,
        selectedDates = selectedDates,
        isDisabled = isDisabled,
        calendarColors = calendarColors,
        onDayClick = onDayClick,
    )
}

@Composable
private fun KalendarDayContent(
    date: LocalDate,
    modifier: Modifier = Modifier,
    selectedDates: List<LocalDate> = emptyList(),
    selectedRange: KalendarSelectedDayRange? = null,
    selectedDate: LocalDate = date,
    dayConfig: KalendarDayConfig = KalendarDayConfig(),
    events: KalendarEvents = emptyList(),
    isDisabled: Boolean = false,
    calendarColors: Map<String, Color> = emptyMap(),
    onDayClick: (LocalDate, List<KalendarEvent>) -> Unit = { _, _ -> },
) {
    val hapticFeedback = LocalHapticFeedback.current
    val today = remember(TimeZone.currentSystemDefault()) {
        Clock.System.todayIn(TimeZone.currentSystemDefault())
    }
    val currentDay = today == date
    val selected = date == selectedDate || selectedDates.contains(date)
    val brush = remember(selected) {
        if (selected) {
            Brush.linearGradient(dayConfig.selectedTextColor.value)
        } else {
            dayConfig.textStyle.brush
        }
    }
    val fontWeight = remember(selected) { if (selected) FontWeight.Bold else FontWeight.Normal }

    val semanticDescription = remember(date, currentDay, selected, isDisabled, events.size) {
        buildString {
            append(
                "${date.dayOfMonth} ${date.month.name.lowercase().replaceFirstChar { it.uppercaseChar() }} ${date.year}"
            )
            if (currentDay) append(", today")
            if (selected) append(", selected")
            if (events.isNotEmpty()) append(", ${events.size} event${if (events.size != 1) "s" else ""}")
            if (isDisabled) append(", not available")
        }
    }

    Column(
        modifier = modifier
            .semantics(mergeDescendants = true) {
                contentDescription = semanticDescription
                role = Role.Button
            }
            .alpha(if (isDisabled) 0.38f else 1f)
            .border(
                border = getBorderStroke(
                    currentDay = currentDay,
                    brush = Brush.linearGradient(dayConfig.borderColor.value),
                    selected = selected
                ),
                shape = CircleShape
            )
            .clip(CircleShape)
            .dayBackgroundColor(
                selected = selected,
                selectedDates = selectedDates,
                date = date,
                selectedRange = selectedRange,
                colors = dayConfig.selectedBackgroundColor.value
            )
            .then(
                if (!isDisabled) {
                    Modifier.clickable {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                        onDayClick(date, events)
                    }
                } else {
                    Modifier
                }
            )
            .circleLayout()
            .defaultMinSize(dayConfig.size),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = date.dayOfMonth.toString(),
            modifier = Modifier.wrapContentSize(),
            textAlign = TextAlign.Center,
            style = dayConfig.textStyle.copy(
                brush = brush,
                fontWeight = fontWeight
            )
        )
        if (events.isNotEmpty()) {
            EventIndicators(
                events = events,
                dayConfig = dayConfig,
                calendarColors = calendarColors,
                modifier = Modifier.wrapContentSize().padding(top = 4.dp)
            )
        }
    }
}

@Composable
private fun EventIndicators(
    events: List<KalendarEvent>,
    dayConfig: KalendarDayConfig,
    calendarColors: Map<String, Color>,
    modifier: Modifier = Modifier,
) {
    val overflowThreshold = 3
    val hasOverflow = events.size > overflowThreshold
    val visibleCount = if (hasOverflow) overflowThreshold - 1 else events.size
    val overflowCount = events.size - visibleCount

    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        events.take(visibleCount).fastForEachIndexed { index, event ->
            val resolvedColor = event.eventColor
                ?: event.calendarId?.let { calendarColors[it] }
            KalendarIndicator(
                modifier = Modifier,
                index = index,
                size = dayConfig.size,
                color = dayConfig.indicatorColor,
                overrideColor = resolvedColor,
            )
        }
        if (hasOverflow) {
            Text(
                text = "+$overflowCount",
                style = dayConfig.textStyle.copy(
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    brush = null,
                ),
                modifier = Modifier.padding(start = 1.dp),
            )
        }
    }
}

private fun getBorderStroke(
    currentDay: Boolean,
    brush: Brush,
    selected: Boolean,
) = if (currentDay && !selected) {
    BorderStroke(1.dp, brush)
} else {
    BorderStroke(0.dp, Color.Transparent)
}
