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

package com.himanshoe.kalendar

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.himanshoe.kalendar.foundation.component.config.KalendarConfig
import com.himanshoe.kalendar.foundation.event.KalendarEvents

/**
 * The main entry point for displaying a calendar.
 *
 * The only [KalendarType] is [KalendarType.Agenda] — a scrollable event list. For a month, week,
 * or year grid, use the standalone [com.himanshoe.kalendar.view.KalendarMonth],
 * [com.himanshoe.kalendar.view.KalendarWeek], or [com.himanshoe.kalendar.view.KalendarYear]
 * composables instead; they aren't called through [Kalendar]/[KalendarType].
 *
 * ```kotlin
 * Kalendar(
 *     type = KalendarType.Agenda,
 *     events = myEvents,
 *     config = KalendarConfig(
 *         backgroundColor = KalendarColor.Solid(Color.White),
 *     ),
 * )
 * ```
 *
 * @param type The [KalendarType] that controls the layout variant.
 * @param modifier [Modifier] applied to the outermost layout container.
 * @param events The full list of events to display.
 * @param config Visual and behavioural configuration for the calendar.
 */
@Composable
public fun Kalendar(
    type: KalendarType,
    modifier: Modifier = Modifier,
    events: KalendarEvents = emptyList(),
    config: KalendarConfig = KalendarConfig(),
) {
    when (type) {
        KalendarType.Agenda -> KalendarAgenda(
            events = events,
            config = config,
            modifier = modifier,
        )
    }
}
