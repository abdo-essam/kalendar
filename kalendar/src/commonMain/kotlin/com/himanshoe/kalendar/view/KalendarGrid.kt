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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.style.TextAlign
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate

/** Fresh 7-column grid engine backing [KalendarWeek], [KalendarMonth], and the year overview. */
@Composable
internal fun KalendarGrid(
    dates: List<LocalDate>,
    startDayOfWeek: DayOfWeek,
    modifier: Modifier = Modifier,
    cell: @Composable (LocalDate) -> Unit,
) {
    val labelDays = remember(startDayOfWeek) { daysOfWeekStartingAt(startDayOfWeek) }

    LazyVerticalGrid(
        columns = GridCells.Fixed(7),
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
    ) {
        items(labelDays) { day ->
            Text(
                text = day.name.take(1),
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().alpha(0.6f),
            )
        }
        items(items = dates, key = { it.toEpochDays() }) { date ->
            cell(date)
        }
    }
}

/**
 * Eager (non-lazy) variant of [KalendarGrid] for use inside another scrollable container — e.g.
 * [KalendarTimeline]'s `LazyColumn` items — where a nested `LazyVerticalGrid` would be measured
 * with an unbounded height and crash. A month grid is at most 42 cells, cheap enough to compose
 * eagerly.
 */
@Composable
internal fun KalendarStaticGrid(
    dates: List<LocalDate>,
    startDayOfWeek: DayOfWeek,
    modifier: Modifier = Modifier,
    cell: @Composable (LocalDate) -> Unit,
) {
    val labelDays = remember(startDayOfWeek) { daysOfWeekStartingAt(startDayOfWeek) }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth()) {
            labelDays.forEach { day ->
                Text(
                    text = day.name.take(1),
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f).alpha(0.6f),
                )
            }
        }
        dates.chunked(7).forEach { week ->
            Row(modifier = Modifier.fillMaxWidth()) {
                for (dayIndex in 0 until 7) {
                    Box(modifier = Modifier.weight(1f)) {
                        val date = week.getOrNull(dayIndex)
                        if (date != null) {
                            cell(date)
                        }
                    }
                }
            }
        }
    }
}
