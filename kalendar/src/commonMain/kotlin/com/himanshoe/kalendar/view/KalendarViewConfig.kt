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

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import kotlinx.datetime.LocalDate

/**
 * Shared visual and behavioural knobs for [KalendarWeek], [KalendarMonth], and [KalendarYear].
 *
 * @param disabledDates A predicate called for each date. When it returns `true` the date is
 *   rendered at reduced opacity and taps are ignored. Defaults to `{ false }`.
 * @param showNavigationArrows Whether to show previous/next arrow buttons in the header. Swiping
 *   the calendar works regardless of this setting. Defaults to `true`.
 * @param background Background brush for the calendar container. Defaults to solid white.
 */
@Immutable
data class KalendarViewConfig(
    val disabledDates: (LocalDate) -> Boolean = { false },
    val showNavigationArrows: Boolean = true,
    val background: Brush = SolidColor(Color.White),
)
