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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import com.himanshoe.kalendar.foundation.schedule.KalendarHourWindow
import com.himanshoe.kalendar.foundation.schedule.MINUTES_PER_HOUR
import com.himanshoe.kalendar.foundation.schedule.minutesFromMidnight
import com.himanshoe.kalendar.theme.kalendarColors
import com.himanshoe.kalendar.theme.kalendarDimensions
import kotlinx.datetime.LocalDateTime
import kotlin.math.roundToInt

/*
 * The chrome every hour-grid view rules across itself — the gutter, the hour lines, the dividers and
 * the now-indicator — factored out of KalendarSchedule, KalendarScheduleWeek and
 * KalendarResourceView, which drew byte-identical copies of it between them.
 *
 * Each piece takes the visible KalendarHourWindow rather than assuming a 24-hour day, so a
 * business-hours window is honoured everywhere at once instead of in whichever view was remembered.
 */

/**
 * The hour-label gutter down the left of a grid, one [hourHeight]-tall row per hour in [window].
 *
 * [hourLabel] is handed the *absolute* hour (`8` for 08:00), not an index into the window, so a
 * caller's formatter never has to know the grid starts somewhere other than midnight.
 */
@Composable
internal fun KalendarHourGutter(
    hourHeight: Dp,
    window: KalendarHourWindow,
    hourLabel: @Composable (Int) -> Unit,
) {
    Column(modifier = Modifier.width(kalendarDimensions().hourGutterWidth)) {
        for (hour in window.hours) {
            Box(
                modifier = Modifier.height(hourHeight).fillMaxWidth(),
                contentAlignment = Alignment.TopCenter,
            ) {
                hourLabel(hour)
            }
        }
    }
}

/**
 * The horizontal rule at the top of each hour row in [window].
 *
 * Both shapes are built once for the whole column rather than per row, and the rule is emitted
 * directly instead of through [KalendarHourGridLine]. The theme lookups behind it answer the same
 * thing for every row, and this is the most-repeated composable in the library — a week grid draws
 * one of these per hour in each of its seven columns.
 */
@Composable
internal fun KalendarHourLines(hourHeight: Dp, window: KalendarHourWindow) {
    val gridLineThickness = kalendarDimensions().gridLineThickness
    val rule = Modifier
        .fillMaxWidth()
        .height(gridLineThickness)
        .background(kalendarColors().gridLine)
    val gap = Modifier.height(hourHeight - gridLineThickness)
    Column(modifier = Modifier.fillMaxSize()) {
        repeat(window.hourCount) {
            Box(modifier = rule)
            Box(modifier = gap)
        }
    }
}

/** A single horizontal rule in [KalendarColors.gridLine], full width of whatever contains it. */
@Composable
internal fun KalendarHourGridLine(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(kalendarDimensions().gridLineThickness)
            .background(kalendarColors().gridLine),
    )
}

/** [KalendarHourGridLine]'s vertical twin, separating one column — day or resource — from the next. */
@Composable
internal fun KalendarHourColumnDivider(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxHeight()
            .width(kalendarDimensions().gridLineThickness)
            .background(kalendarColors().gridLine),
    )
}

/**
 * How far below the top edge of [window] a clock reading [now] sits, in grid minutes. Outside the
 * window the answer is negative or past [KalendarHourWindow.spanMinutes], which is how a caller
 * knows there is no row to draw the line on.
 *
 * [now] must already be resolved in the calendar's own zone — `KalendarTimeSource.now()` — so a
 * calendar pinned to an office's zone positions its line on that office's clock rather than the
 * device's. Nothing here can recover the zone from a [LocalDateTime]; it is a wall-clock reading
 * with no offset attached, and that is exactly what this wants.
 *
 * **Wall clock, deliberately, and this is the load-bearing part on the two days a year a day is not
 * 24 hours long.** The grid is ruled in wall-clock hours, `scheduleBlocks` positions blocks in
 * wall-clock minutes from midnight, and both must agree. On the morning the clocks go forward the
 * line therefore jumps from 01:59 to 03:00 across an hour the grid draws but no event can occupy,
 * and on the morning they go back it draws 02:30 twice, an hour of real time apart — in both cases
 * landing exactly where a block for that wall-clock time is drawn.
 *
 * Measuring elapsed real time from the day's start instead — the reading
 * [com.himanshoe.kalendar.foundation.datetime.dayLengthIn] answers — would put the line an hour off
 * every block for the rest of a spring-forward day. That reading is the right one for a midnight
 * rollover and the wrong one for a position.
 */
internal fun nowLineMinutes(now: LocalDateTime, window: KalendarHourWindow): Int =
    now.minutesFromMidnight() - window.startMinutes

/**
 * The current-time line, drawn only on a column that is today and only while the clock is inside
 * [window] — on an 08:00–20:00 grid there is no row for 21:30, and a line pinned to the bottom edge
 * would claim there is.
 *
 * [isToday] is the caller's, because only the caller knows which date its column draws; it must be
 * decided against the same time source [now] comes from, or a calendar pinned to another zone shows
 * its line on the device's today.
 *
 * [now] is read in two places, both deliberate. The *position* is read inside [Modifier.offset]'s
 * lambda, which runs in the layout phase, so a minute passing re-places one line instead of
 * recomposing this composable and its slot. The *visibility* is read through [derivedStateOf], whose
 * result changes at most twice a day — at the window's two edges — so gating the line on it costs a
 * recomposition only when it genuinely appears or disappears. Both go through [nowLineMinutes], so
 * the line can never be shown at a position the window does not contain.
 */
@Composable
internal fun KalendarNowLine(
    isToday: Boolean,
    now: () -> LocalDateTime,
    hourHeight: Dp,
    window: KalendarHourWindow,
    content: @Composable () -> Unit,
) {
    if (!isToday) {
        return
    }
    val isWithinWindow by remember(now, window) {
        derivedStateOf { nowLineMinutes(now(), window) in 0 until window.spanMinutes }
    }
    if (!isWithinWindow) {
        return
    }
    Box(
        modifier = Modifier.fillMaxWidth().offset {
            val minutes = nowLineMinutes(now(), window)
            IntOffset(x = 0, y = (hourHeight.toPx() * minutes / MINUTES_PER_HOUR).roundToInt())
        },
    ) {
        content()
    }
}
