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
import com.himanshoe.kalendar.foundation.selection.KalendarSelectionMode

/**
 * The Kalendar view the playground is currently rendering — every public view the library ships,
 * so nothing is reachable only by reading the docs.
 *
 * @property label The chip's text.
 * @property call The composable this view calls, which is also what the generated snippet prints.
 * @property scrollsInternally Whether the view owns a scrolling container of its own — a
 *   `LazyColumn` or a `Modifier.verticalScroll` — and will therefore take every pixel of height it
 *   is offered and page the rest. A view without one wraps to a height its width implies: seven
 *   columns of square cells, six rows deep.
 *
 *   What it does **not** mean is that the other views may be given an unbounded height. None of
 *   them may: every grid in the library is drawn by a `LazyVerticalGrid` (kept unscrollable so the
 *   cells' own long-press gestures survive), and a lazy layout measured against an infinite maximum
 *   throws rather than wrapping. So the demo always hands the calendar a bounded height and never
 *   puts a view inside a scroll of its own; this flag only decides whether that bound should be a
 *   height the view fills, or a ceiling it is free to come in under.
 */
internal enum class PlaygroundView(
    val label: String,
    val call: String,
    val scrollsInternally: Boolean,
) {
    Week(label = "Week", call = "KalendarWeek", scrollsInternally = false),
    Month(label = "Month", call = "KalendarMonth", scrollsInternally = false),
    Year(label = "Year", call = "KalendarYear", scrollsInternally = true),
    Timeline(label = "Timeline", call = "KalendarTimeline", scrollsInternally = true),
    Schedule(label = "Schedule", call = "KalendarSchedule", scrollsInternally = true),
    ScheduleWeek(label = "Week grid", call = "KalendarScheduleWeek", scrollsInternally = true),
    Resource(label = "Resources", call = "KalendarResourceView", scrollsInternally = true),
    Agenda(label = "Agenda", call = "KalendarAgenda", scrollsInternally = true),
    DatePicker(label = "Date picker", call = "KalendarDatePicker", scrollsInternally = false),
    ;

    /** Whether this view renders day cells, and therefore honours the `dayContent` slot. */
    val hasDayCells: Boolean
        get() = this == Week || this == Month || this == Year || this == Timeline

    /** Whether this view draws its own navigation header, and therefore honours the `header` slot. */
    val hasHeaderSlot: Boolean
        get() = hasDayCells

    /** Whether this view lays events out on an hour grid, and therefore honours `hourHeight`. */
    val isSchedule: Boolean
        get() = this == Schedule || this == ScheduleWeek || this == Resource

    /** Whether this view tracks selected dates, and therefore honours the selection mode. */
    val hasSelection: Boolean
        get() = hasDayCells || this == DatePicker
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

/** The shape mapped onto [com.himanshoe.kalendar.KalendarShapes.dayCell]. */
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
internal const val DefaultDayCellAspectRatio = 1f
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
    val dayCellAspectRatio: Float = DefaultDayCellAspectRatio,
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
                dayCellAspectRatio = MinimalDayCellAspectRatio,
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
                dayCellAspectRatio = BrutalistDayCellAspectRatio,
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
                dayCellAspectRatio = PlayfulDayCellAspectRatio,
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
private const val MinimalDayCellAspectRatio = 0.85f
private val MinimalEventIndicatorSize = 3.dp
private const val MinimalEventIndicatorCap = 1

private val BrutalistDayCellPadding = 0.dp
private const val BrutalistDayCellAspectRatio = 0.75f
private val BrutalistEventIndicatorSize = 6.dp
private val BrutalistGridLineThickness = 3.dp
private val BrutalistHourHeight = 88.dp

private val PlayfulDayCellPadding = 5.dp
private const val PlayfulDayCellAspectRatio = 0.8f
private val PlayfulEventIndicatorSize = 9.dp
private const val PlayfulEventIndicatorCap = 4
private val PlayfulHourHeight = 80.dp
