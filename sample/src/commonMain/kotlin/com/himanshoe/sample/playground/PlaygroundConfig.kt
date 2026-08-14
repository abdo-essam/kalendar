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

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.himanshoe.kalendar.view.KalendarSelectionMode

/** The Kalendar view the playground is currently rendering. */
internal enum class PlaygroundView(val label: String, val call: String) {
    Week(label = "Week", call = "KalendarWeek"),
    Month(label = "Month", call = "KalendarMonth"),
    Year(label = "Year", call = "KalendarYear"),
    Timeline(label = "Timeline", call = "KalendarTimeline"),
    Schedule(label = "Schedule", call = "KalendarSchedule"),
    ScheduleWeek(label = "Week grid", call = "KalendarScheduleWeek"),
    Agenda(label = "Agenda", call = "KalendarAgenda"),
    ;

    /** Whether this view renders day cells, and therefore honours the `dayContent` slot. */
    val hasDayCells: Boolean
        get() = this == Week || this == Month || this == Year || this == Timeline

    /** Whether this view draws its own navigation header, and therefore honours the `header` slot. */
    val hasHeaderSlot: Boolean
        get() = hasDayCells

    /** Whether this view lays events out on an hour grid, and therefore honours `hourHeight`. */
    val isSchedule: Boolean
        get() = this == Schedule || this == ScheduleWeek
}

/** How many dates the playground calendar lets you select at once. */
internal enum class PlaygroundSelection(val label: String, val mode: KalendarSelectionMode, val code: String) {
    Single(label = "Single", mode = KalendarSelectionMode.Single, code = "KalendarSelectionMode.Single"),
    Multiple(label = "Multiple", mode = KalendarSelectionMode.Multiple, code = "KalendarSelectionMode.Multiple"),
    Range(label = "Range", mode = KalendarSelectionMode.Range, code = "KalendarSelectionMode.Range"),
}

/** Which light/dark palette the playground resolves its colours from. */
internal enum class PlaygroundAppearance(val label: String, val code: String) {
    Light(label = "Light", code = "KalendarThemeDefaults.lightColors()"),
    Dark(label = "Dark", code = "KalendarThemeDefaults.darkColors()"),
    System(label = "System", code = "KalendarThemeDefaults.systemColors()"),
}

/** The shape mapped onto [com.himanshoe.kalendar.view.KalendarShapes.dayCell]. */
internal enum class PlaygroundCellShape(val label: String) {
    Circle(label = "Circle"),
    Rounded(label = "Rounded"),
    Square(label = "Square"),
}

/** The overall visual language a preset commits to, driving colours, shapes and type together. */
internal enum class PlaygroundSkin {
    Material,
    Minimal,
    Brutalist,
    Playful,
}

internal val DefaultDayCellPadding = 2.dp
internal val DefaultEventIndicatorSize = 4.dp
internal val DefaultGridLineThickness = 1.dp
internal val DefaultHourHeight = 64.dp
internal const val DefaultScheduleScrollHour = 7
internal const val DefaultScheduleSnapMinutes = 15
internal const val DefaultEventIndicatorCap = 3

/**
 * Every knob the playground exposes, in one immutable value.
 *
 * Defaults mirror the library's own defaults exactly, which is what lets the code generator emit
 * only the tokens that actually differ from stock Kalendar.
 */
@Immutable
internal data class PlaygroundConfig(
    val view: PlaygroundView = PlaygroundView.Month,
    val selection: PlaygroundSelection = PlaygroundSelection.Single,
    val appearance: PlaygroundAppearance = PlaygroundAppearance.Light,
    val accent: PlaygroundAccent = PlaygroundAccent.Indigo,
    val skin: PlaygroundSkin = PlaygroundSkin.Material,
    val cellShape: PlaygroundCellShape = PlaygroundCellShape.Circle,
    val dayCellPadding: Dp = DefaultDayCellPadding,
    val eventIndicatorSize: Dp = DefaultEventIndicatorSize,
    val gridLineThickness: Dp = DefaultGridLineThickness,
    val hourHeight: Dp = DefaultHourHeight,
    val scheduleInitialScrollHour: Int = DefaultScheduleScrollHour,
    val scheduleDragSnapMinutes: Int = DefaultScheduleSnapMinutes,
    val eventIndicatorCap: Int = DefaultEventIndicatorCap,
    val showNavigationArrows: Boolean = true,
    val showTodayButton: Boolean = true,
    val showJumpPicker: Boolean = true,
    val showSelectionIndicator: Boolean = true,
    val showAdjacentMonthDates: Boolean = true,
    val animationsEnabled: Boolean = true,
    val bouncyMotion: Boolean = false,
    val heavyType: Boolean = false,
    val customCell: Boolean = false,
    val customHeader: Boolean = false,
)

/**
 * One-click looks that keep the same library and the same events but change everything about how
 * they read. Applying a preset preserves the choices that are orthogonal to a look — which view is
 * on screen, the selection mode, light/dark, the accent, and the slot demos.
 */
internal enum class PlaygroundPreset(val label: String) {
    Material(label = "Material"),
    Minimal(label = "Minimal"),
    Brutalist(label = "Brutalist"),
    Playful(label = "Playful"),
    ;

    fun applyTo(current: PlaygroundConfig): PlaygroundConfig {
        val base = PlaygroundConfig(
            view = current.view,
            selection = current.selection,
            appearance = current.appearance,
            accent = current.accent,
            customCell = current.customCell,
            customHeader = current.customHeader,
        )
        return when (this) {
            Material -> base
            Minimal -> base.copy(
                skin = PlaygroundSkin.Minimal,
                cellShape = PlaygroundCellShape.Square,
                dayCellPadding = MinimalDayCellPadding,
                eventIndicatorSize = MinimalEventIndicatorSize,
                eventIndicatorCap = MinimalEventIndicatorCap,
                showSelectionIndicator = false,
                showJumpPicker = false,
                showAdjacentMonthDates = false,
                animationsEnabled = false,
            )

            Brutalist -> base.copy(
                skin = PlaygroundSkin.Brutalist,
                cellShape = PlaygroundCellShape.Square,
                dayCellPadding = BrutalistDayCellPadding,
                eventIndicatorSize = BrutalistEventIndicatorSize,
                gridLineThickness = BrutalistGridLineThickness,
                hourHeight = BrutalistHourHeight,
                heavyType = true,
                showJumpPicker = false,
                animationsEnabled = false,
            )

            Playful -> base.copy(
                skin = PlaygroundSkin.Playful,
                cellShape = PlaygroundCellShape.Rounded,
                dayCellPadding = PlayfulDayCellPadding,
                eventIndicatorSize = PlayfulEventIndicatorSize,
                eventIndicatorCap = PlayfulEventIndicatorCap,
                hourHeight = PlayfulHourHeight,
                bouncyMotion = true,
            )
        }
    }
}

/** The preset [config] currently matches, or `null` once the user has hand-tuned it away. */
internal fun presetOf(config: PlaygroundConfig): PlaygroundPreset? =
    PlaygroundPreset.entries.firstOrNull { it.applyTo(config) == config }

private val MinimalDayCellPadding = 6.dp
private val MinimalEventIndicatorSize = 3.dp
private const val MinimalEventIndicatorCap = 1

private val BrutalistDayCellPadding = 0.dp
private val BrutalistEventIndicatorSize = 6.dp
private val BrutalistGridLineThickness = 3.dp
private val BrutalistHourHeight = 88.dp

private val PlayfulDayCellPadding = 5.dp
private val PlayfulEventIndicatorSize = 9.dp
private const val PlayfulEventIndicatorCap = 4
private val PlayfulHourHeight = 80.dp
