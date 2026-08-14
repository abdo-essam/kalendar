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

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.himanshoe.kalendar.theme.KalendarThemeDefaults

private const val Indent = "    "
private const val HexDigits = 8

/**
 * Renders the current playground configuration as the Kotlin that reproduces it.
 *
 * Only tokens that actually differ from stock Kalendar are emitted — the colour block is produced
 * by diffing the resolved [com.himanshoe.kalendar.KalendarColors] against the palette the
 * snippet starts from — so an untouched playground generates a bare view call, and every extra
 * line in the snippet corresponds to something the user changed.
 *
 * @param config The configuration to render.
 * @param dark Whether the calendar is currently resolving its dark palette, which decides both the
 *   base palette the snippet copies from and the accent tone it overrides with.
 */
internal fun generatePlaygroundCode(config: PlaygroundConfig, dark: Boolean): String {
    val themeParameters = listOfNotNull(
        colorsParameter(config = config, dark = dark),
        typographyParameter(config = config),
        shapesParameter(config = config),
        dimensionsParameter(config = config),
        animationsParameter(config = config),
    )
    val call = viewCall(config = config)
    return buildString {
        appendLine("// today and events are your own state")
        if (config.view.hasSelection) {
            appendLine("val selection = rememberKalendarSelectionState(mode = ${config.selection.code})")
        }
        appendLine()
        if (themeParameters.isEmpty()) {
            append(call)
        } else {
            appendLine("KalendarTheme(")
            themeParameters.forEach { parameter -> appendLine(indent("$parameter,")) }
            appendLine(") {")
            appendLine(indent(call))
            append("}")
        }
    }
}

private fun colorsParameter(config: PlaygroundConfig, dark: Boolean): String? {
    val resolved = config.resolveColors(dark = dark)
    val base = if (dark) KalendarThemeDefaults.darkColors() else KalendarThemeDefaults.lightColors()
    val overrides = mutableListOf<String>()
    fun color(name: String, actual: Color, default: Color) {
        if (actual != default) {
            overrides += "$name = ${colorSource(actual)}"
        }
    }
    color(name = "background", actual = resolved.background, default = base.background)
    color(name = "selectionBackground", actual = resolved.selectionBackground, default = base.selectionBackground)
    color(name = "onSelectionBackground", actual = resolved.onSelectionBackground, default = base.onSelectionBackground)
    color(name = "todayContent", actual = resolved.todayContent, default = base.todayContent)
    color(name = "dayContent", actual = resolved.dayContent, default = base.dayContent)
    color(name = "dayLabelContent", actual = resolved.dayLabelContent, default = base.dayLabelContent)
    color(name = "headerContent", actual = resolved.headerContent, default = base.headerContent)
    color(name = "eventIndicator", actual = resolved.eventIndicator, default = base.eventIndicator)
    color(name = "nowIndicator", actual = resolved.nowIndicator, default = base.nowIndicator)
    color(name = "gridLine", actual = resolved.gridLine, default = base.gridLine)
    color(name = "dragGhostBackground", actual = resolved.dragGhostBackground, default = base.dragGhostBackground)
    if (resolved.eventBlockAlpha != base.eventBlockAlpha) {
        overrides += "eventBlockAlpha = ${resolved.eventBlockAlpha}f"
    }
    if (overrides.isEmpty()) {
        return null
    }
    return block(header = "colors = ${config.appearance.code}.copy(", entries = overrides)
}

private fun typographyParameter(config: PlaygroundConfig): String? = if (config.heavyType) {
    heavyTypographySnippet()
} else {
    null
}

private fun shapesParameter(config: PlaygroundConfig): String? {
    val defaults = KalendarThemeDefaults.shapes()
    val overrides = mutableListOf<String>()
    fun shape(name: String, choice: ShapeChoice, default: Any) {
        if (choice.shape != default) {
            overrides += "$name = ${choice.source}"
        }
    }
    shape(name = "dayCell", choice = config.dayCellShape(), default = defaults.dayCell)
    shape(name = "eventIndicator", choice = config.eventIndicatorShape(), default = defaults.eventIndicator)
    shape(name = "eventBlock", choice = config.eventBlockShape(), default = defaults.eventBlock)
    shape(name = "popup", choice = config.popupShape(), default = defaults.popup)
    shape(name = "agendaCard", choice = config.agendaCardShape(), default = defaults.agendaCard)
    if (overrides.isEmpty()) {
        return null
    }
    return block(header = "shapes = KalendarThemeDefaults.shapes().copy(", entries = overrides)
}

private fun dimensionsParameter(config: PlaygroundConfig): String? {
    val overrides = mutableListOf<String>()
    if (config.dayCellPadding != DefaultDayCellPadding) {
        overrides += "dayCellPadding = ${dpSource(config.dayCellPadding)}"
    }
    if (config.dayCellAspectRatio != DefaultDayCellAspectRatio) {
        overrides += "dayCellAspectRatio = ${ratioSource(config.dayCellAspectRatio)}"
    }
    if (config.eventIndicatorSize != DefaultEventIndicatorSize) {
        overrides += "eventIndicatorSize = ${dpSource(config.eventIndicatorSize)}"
    }
    if (config.gridLineThickness != DefaultGridLineThickness) {
        overrides += "gridLineThickness = ${dpSource(config.gridLineThickness)}"
    }
    if (config.hourHeight != DefaultHourHeight) {
        overrides += "hourHeight = ${dpSource(config.hourHeight)}"
    }
    if (overrides.isEmpty()) {
        return null
    }
    return block(header = "dimensions = KalendarDimensions(", entries = overrides)
}

private fun animationsParameter(config: PlaygroundConfig): String? = when {
    !config.animationsEnabled -> "animations = KalendarAnimations(enabled = false)"
    config.bouncyMotion -> bouncyAnimationsSnippet()
    else -> null
}

private fun viewConfigSource(config: PlaygroundConfig): String? {
    val overrides = mutableListOf<String>()
    fun flag(name: String, actual: Boolean) {
        if (!actual) {
            overrides += "$name = false"
        }
    }
    flag(name = "showNavigationArrows", actual = config.showNavigationArrows)
    flag(name = "showTodayButton", actual = config.showTodayButton)
    flag(name = "showJumpPicker", actual = config.showJumpPicker)
    flag(name = "showSelectionIndicator", actual = config.showSelectionIndicator)
    flag(name = "showAdjacentMonthDates", actual = config.showAdjacentMonthDates)
    if (config.eventIndicatorCap != DefaultEventIndicatorCap) {
        overrides += "eventIndicatorCap = ${config.eventIndicatorCap}"
    }
    if (config.scheduleInitialScrollHour != DefaultScheduleScrollHour) {
        overrides += "scheduleInitialScrollHour = ${config.scheduleInitialScrollHour}"
    }
    if (config.scheduleDragSnapMinutes != DefaultScheduleSnapMinutes) {
        overrides += "scheduleDragSnapMinutes = ${config.scheduleDragSnapMinutes}"
    }
    if (overrides.isEmpty()) {
        return null
    }
    return block(header = "KalendarViewConfig(", entries = overrides)
}

private fun viewCall(config: PlaygroundConfig): String = when (config.view) {
    PlaygroundView.Agenda -> agendaCall(config)
    PlaygroundView.Resource -> resourceCall(config)
    PlaygroundView.DatePicker -> datePickerCall(config)
    else -> gridOrScheduleCall(config)
}

private fun gridOrScheduleCall(config: PlaygroundConfig): String {
    val arguments = mutableListOf<String>()
    if (config.view.isSchedule) {
        // The schedule views take no `selectedDate`: the day on screen is the state's, so the
        // snippet has to name the state the way a caller would to open on a chosen day.
        arguments += "state = ${scheduleStateSource(config.view)}"
        arguments += "events = events"
        arguments += "onEventClick = { event -> openEvent(event) }"
        arguments += "onEventTimeChange = { event, start, end -> reschedule(event, start, end) }"
    } else {
        arguments += "selectedDate = today"
        arguments += "events = events"
        arguments += "selectedDates = selection.selectedDates"
        arguments += "onDateClick = { date, _ -> selection.onDateClick(date) }"
        if (config.selection == PlaygroundSelection.Range) {
            arguments += "onDateRangeSelect = { start, current -> selection.onRangeDrag(start, current) }"
            arguments += "onDateRangeSelectEnd = { selection.onRangeDragEnd() }"
        }
        if (config.view == PlaygroundView.Month) {
            arguments += "onEventDrop = { moved, newDate -> moveEvents(moved, newDate) }"
        }
    }
    viewConfigSource(config)?.let { source -> arguments += "config = $source" }
    if (config.customHeader && config.view.hasHeaderSlot) {
        arguments += "header = { scope -> BrandHeader(scope = scope) }"
    }
    if (config.customCell && config.view.hasDayCells) {
        arguments += customCellArgument()
    }
    return block(header = "${config.view.call}(", entries = arguments)
}

private fun agendaCall(config: PlaygroundConfig): String {
    val arguments = mutableListOf("events = events", "onEventClick = { event -> openEvent(event) }")
    viewConfigSource(config)?.let { source -> arguments += "config = $source" }
    return block(header = "KalendarAgenda(", entries = arguments)
}

/**
 * The resource view's call. `resourceIdOf` is spelled out even though it defaults to
 * [com.himanshoe.kalendar.foundation.event.KalendarEvent.calendarId], because which field carries
 * the lane link is the one decision a caller has to make here.
 */
private fun resourceCall(config: PlaygroundConfig): String {
    val arguments = mutableListOf(
        "resources = rooms",
        "state = rememberKalendarScheduleState(initialDate = today)",
        "events = events",
        "resourceIdOf = { event -> event.calendarId }",
        "onEventClick = { event -> openEvent(event) }",
        "onEventChange = { event, start, end, roomId -> reschedule(event, start, end, roomId) }",
    )
    viewConfigSource(config)?.let { source -> arguments += "config = $source" }
    return block(header = "KalendarResourceView(", entries = arguments)
}

private fun datePickerCall(config: PlaygroundConfig): String {
    val arguments = mutableListOf(
        "selectedDates = selection.selectedDates",
        "onDateClick = { date -> selection.onDateClick(date) }",
        "state = rememberKalendarMonthState(initialDate = today)",
        "selectionMode = ${config.selection.code}",
        "onTodayClick = { date -> selection.onDateClick(date) }",
        "onClear = { selection.clear() }",
    )
    viewConfigSource(config)?.let { source -> arguments += "config = $source" }
    return block(header = "KalendarDatePicker(", entries = arguments)
}

private fun scheduleStateSource(view: PlaygroundView): String = when (view) {
    PlaygroundView.ScheduleWeek -> "rememberKalendarWeekState(initialDate = today)"
    else -> "rememberKalendarScheduleState(initialDate = today)"
}

private fun block(header: String, entries: List<String>): String = buildString {
    appendLine(header)
    entries.forEach { entry -> appendLine(indent("$entry,")) }
    append(")")
}

private fun indent(text: String): String = text
    .lineSequence()
    .joinToString(separator = "\n") { line -> if (line.isEmpty()) line else Indent + line }

private fun colorSource(color: Color): String {
    val hex = color.toArgb().toUInt().toString(radix = 16).uppercase().padStart(length = HexDigits, padChar = '0')
    return "Color(0x$hex)"
}

private fun heavyTypographySnippet(): String = """
    typography = KalendarThemeDefaults.typography().let { type ->
        type.copy(
            headerTitle = type.headerTitle.copy(fontWeight = FontWeight.Black),
            monthLabel = type.monthLabel.copy(fontWeight = FontWeight.Black),
            dayOfWeekLabel = type.dayOfWeekLabel.copy(fontWeight = FontWeight.Black, letterSpacing = 1.5.sp),
            dayNumber = type.dayNumber.copy(fontWeight = FontWeight.Bold),
            dayNumberEmphasized = type.dayNumberEmphasized.copy(fontWeight = FontWeight.Black),
            weekDayNumber = type.weekDayNumber.copy(fontWeight = FontWeight.Black),
            eventLabel = type.eventLabel.copy(fontWeight = FontWeight.Bold),
            hourLabel = type.hourLabel.copy(fontWeight = FontWeight.Bold),
        )
    }
""".trimIndent()

private fun bouncyAnimationsSnippet(): String = """
    animations = KalendarAnimations(
        selectionColorSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        selectionIndicatorSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow,
        ),
        pressScale = ${BouncyPressScale}f,
    )
""".trimIndent()

private fun customCellArgument(): String = """
    dayContent = { scope ->
        BrandDayCell(
            scope = scope,
            onClick = { selection.onDateClick(scope.date) },
        )
    }
""".trimIndent()
