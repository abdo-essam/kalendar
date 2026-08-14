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
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/** The gutter the control cards and the code pane share, so their edges line up. */
internal val ControlsInset = 16.dp

/**
 * The playground's control column: every group mutates one field of [config] and hands the whole
 * new configuration back through [onConfigChange], so the calendar, the theme, and the generated
 * snippet always describe exactly the same state.
 */
@Composable
internal fun PlaygroundControls(
    config: PlaygroundConfig,
    onConfigChange: (PlaygroundConfig) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(horizontal = ControlsInset),
        verticalArrangement = Arrangement.spacedBy(SectionSpacing),
    ) {
        PresetSection(config = config, onConfigChange = onConfigChange)
        ViewSection(config = config, onConfigChange = onConfigChange)
        AppearanceSection(config = config, onConfigChange = onConfigChange)
        ScheduleSection(config = config, onConfigChange = onConfigChange)
        BehaviourSection(config = config, onConfigChange = onConfigChange)
        MotionSection(config = config, onConfigChange = onConfigChange)
    }
}

@Composable
private fun PresetSection(config: PlaygroundConfig, onConfigChange: (PlaygroundConfig) -> Unit) {
    val active = presetOf(config)
    ControlSection(title = "Presets", caption = "Four looks, one library and one set of events.") {
        ChipRow(
            items = PlaygroundPreset.entries,
            isSelected = { preset -> preset == active },
            label = { preset -> preset.label },
            tag = { preset -> "preset-${preset.name}" },
            onSelect = { preset -> onConfigChange(preset.applyTo(config)) },
        )
    }
}

@Composable
private fun ViewSection(config: PlaygroundConfig, onConfigChange: (PlaygroundConfig) -> Unit) {
    ControlSection(title = "View", caption = "Every calendar surface Kalendar ships.") {
        ChipRow(
            items = PlaygroundView.entries,
            isSelected = { view -> view == config.view },
            label = { view -> view.label },
            tag = { view -> "view-${view.name}" },
            onSelect = { view -> onConfigChange(config.copy(view = view)) },
        )
        GroupLabel(text = "Selection")
        ChipRow(
            items = PlaygroundSelection.entries,
            isSelected = { selection -> selection == config.selection },
            label = { selection -> selection.label },
            tag = { selection -> "selection-${selection.name}" },
            onSelect = { selection -> onConfigChange(config.copy(selection = selection)) },
        )
    }
}

@Composable
private fun AppearanceSection(config: PlaygroundConfig, onConfigChange: (PlaygroundConfig) -> Unit) {
    ControlSection(title = "Appearance", caption = "The colour, shape and spacing tokens.") {
        ChipRow(
            items = PlaygroundAppearance.entries,
            isSelected = { appearance -> appearance == config.appearance },
            label = { appearance -> appearance.label },
            tag = { appearance -> "appearance-${appearance.name}" },
            onSelect = { appearance -> onConfigChange(config.copy(appearance = appearance)) },
        )
        GroupLabel(text = "Accent")
        AccentRow(config = config, onConfigChange = onConfigChange)
        GroupLabel(text = "Day cell shape")
        ChipRow(
            items = PlaygroundCellShape.entries,
            isSelected = { shape -> shape == config.cellShape },
            label = { shape -> shape.label },
            tag = { shape -> "shape-${shape.name}" },
            onSelect = { shape -> onConfigChange(config.copy(cellShape = shape)) },
        )
        ControlSlider(
            label = "Day cell padding",
            valueText = dpLabel(config.dayCellPadding),
            value = config.dayCellPadding.value,
            range = MinCellPadding..MaxCellPadding,
            steps = CellPaddingSteps,
            tag = "slider-dayCellPadding",
            onValueChange = { value -> onConfigChange(config.copy(dayCellPadding = value.roundToInt().dp)) },
        )
        ControlSlider(
            label = "Day cell height",
            valueText = "${ratioLabel(config.dayCellAspectRatio)} w:h",
            value = config.dayCellAspectRatio,
            range = MinCellRatio..MaxCellRatio,
            steps = CellRatioSteps,
            tag = "slider-dayCellAspectRatio",
            onValueChange = { value -> onConfigChange(config.copy(dayCellAspectRatio = roundToStep(value))) },
        )
        ControlSlider(
            label = "Event dot size",
            valueText = dpLabel(config.eventIndicatorSize),
            value = config.eventIndicatorSize.value,
            range = MinDotSize..MaxDotSize,
            steps = DotSizeSteps,
            tag = "slider-eventIndicatorSize",
            onValueChange = { value -> onConfigChange(config.copy(eventIndicatorSize = value.roundToInt().dp)) },
        )
        ControlSlider(
            label = "Grid line thickness",
            valueText = dpLabel(config.gridLineThickness),
            value = config.gridLineThickness.value,
            range = MinGridThickness..MaxGridThickness,
            steps = GridThicknessSteps,
            tag = "slider-gridLineThickness",
            onValueChange = { value -> onConfigChange(config.copy(gridLineThickness = value.roundToInt().dp)) },
        )
    }
}

@Composable
private fun ScheduleSection(config: PlaygroundConfig, onConfigChange: (PlaygroundConfig) -> Unit) {
    ControlSection(title = "Schedule", caption = "The hour grid: Schedule, Week grid and Resources.") {
        ControlSlider(
            label = "Hour height",
            valueText = dpLabel(config.hourHeight),
            value = config.hourHeight.value,
            range = MinHourHeight..MaxHourHeight,
            steps = HourHeightSteps,
            tag = "slider-hourHeight",
            onValueChange = { value -> onConfigChange(config.copy(hourHeight = value.roundToInt().dp)) },
        )
        ControlSlider(
            label = "Opens at",
            valueText = hourLabel(config.scheduleInitialScrollHour),
            value = config.scheduleInitialScrollHour.toFloat(),
            range = MinScrollHour..MaxScrollHour,
            steps = ScrollHourSteps,
            tag = "slider-scheduleInitialScrollHour",
            onValueChange = { value ->
                onConfigChange(config.copy(scheduleInitialScrollHour = value.roundToInt()))
            },
        )
        ControlSlider(
            label = "Drag snap",
            valueText = "${config.scheduleDragSnapMinutes} min",
            value = config.scheduleDragSnapMinutes.toFloat(),
            range = MinSnapMinutes..MaxSnapMinutes,
            steps = SnapMinutesSteps,
            tag = "slider-scheduleDragSnapMinutes",
            onValueChange = { value ->
                onConfigChange(config.copy(scheduleDragSnapMinutes = snapToStep(value)))
            },
        )
    }
}

@Composable
private fun BehaviourSection(config: PlaygroundConfig, onConfigChange: (PlaygroundConfig) -> Unit) {
    ControlSection(title = "Behaviour", caption = "What the header and the day cells offer.") {
        ControlSwitch(
            label = "Navigation arrows",
            hint = "showNavigationArrows",
            checked = config.showNavigationArrows,
            tag = "toggle-showNavigationArrows",
            onCheckedChange = { checked -> onConfigChange(config.copy(showNavigationArrows = checked)) },
        )
        ControlSwitch(
            label = "Today button",
            hint = "showTodayButton",
            checked = config.showTodayButton,
            tag = "toggle-showTodayButton",
            onCheckedChange = { checked -> onConfigChange(config.copy(showTodayButton = checked)) },
        )
        ControlSwitch(
            label = "Jump picker",
            hint = "showJumpPicker",
            checked = config.showJumpPicker,
            tag = "toggle-showJumpPicker",
            onCheckedChange = { checked -> onConfigChange(config.copy(showJumpPicker = checked)) },
        )
        ControlSwitch(
            label = "Selection indicator",
            hint = "showSelectionIndicator",
            checked = config.showSelectionIndicator,
            tag = "toggle-showSelectionIndicator",
            onCheckedChange = { checked -> onConfigChange(config.copy(showSelectionIndicator = checked)) },
        )
        ControlSwitch(
            label = "Adjacent month dates",
            hint = "showAdjacentMonthDates",
            checked = config.showAdjacentMonthDates,
            tag = "toggle-showAdjacentMonthDates",
            onCheckedChange = { checked -> onConfigChange(config.copy(showAdjacentMonthDates = checked)) },
        )
        ControlSlider(
            label = "Event dots per day",
            valueText = "${config.eventIndicatorCap} max",
            value = config.eventIndicatorCap.toFloat(),
            range = MinIndicatorCap..MaxIndicatorCap,
            steps = IndicatorCapSteps,
            tag = "slider-eventIndicatorCap",
            onValueChange = { value -> onConfigChange(config.copy(eventIndicatorCap = value.roundToInt())) },
        )
    }
}

@Composable
private fun MotionSection(config: PlaygroundConfig, onConfigChange: (PlaygroundConfig) -> Unit) {
    ControlSection(title = "Motion and slots", caption = "Springs, and your own composables.") {
        ControlSwitch(
            label = "Animations",
            hint = "KalendarAnimations",
            checked = config.animationsEnabled,
            tag = "toggle-animations",
            onCheckedChange = { checked -> onConfigChange(config.copy(animationsEnabled = checked)) },
        )
        ControlSwitch(
            label = "Custom day cell",
            hint = "dayContent",
            checked = config.customCell,
            tag = "toggle-customCell",
            onCheckedChange = { checked -> onConfigChange(config.copy(customCell = checked)) },
        )
        ControlSwitch(
            label = "Custom header",
            hint = "header",
            checked = config.customHeader,
            tag = "toggle-customHeader",
            onCheckedChange = { checked -> onConfigChange(config.copy(customHeader = checked)) },
        )
    }
}

@Composable
private fun AccentRow(config: PlaygroundConfig, onConfigChange: (PlaygroundConfig) -> Unit) {
    val dark = config.isDark()
    Row(horizontalArrangement = Arrangement.spacedBy(SwatchSpacing)) {
        PlaygroundAccent.entries.forEach { accent ->
            val selected = accent == config.accent
            Box(
                modifier = Modifier
                    .size(SwatchSize)
                    .clip(CircleShape)
                    .background(accent.swatchColor(dark))
                    .border(
                        width = if (selected) SelectedSwatchBorder else UnselectedSwatchBorder,
                        color = if (selected) PlaygroundChrome.Text else PlaygroundChrome.Border,
                        shape = CircleShape,
                    )
                    .clickable { onConfigChange(config.copy(accent = accent)) }
                    .testTag("accent-${accent.name}"),
            )
        }
    }
}

/**
 * One card in the control column: an all-caps eyebrow, a sentence saying what the card is for, and
 * the controls.
 *
 * The heading block is spaced on its own — 3dp between the eyebrow and its caption, then a full gap
 * before the controls — because a single [Arrangement.spacedBy] over all three put the caption as
 * far from its own heading as the heading was from the card above it, and the caption read as a
 * floating aside rather than as part of the title.
 */
@Composable
private fun ControlSection(
    title: String,
    caption: String,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CardCorner))
            .background(PlaygroundChrome.Surface)
            .border(width = 1.dp, color = PlaygroundChrome.Border, shape = RoundedCornerShape(CardCorner))
            .padding(horizontal = CardPadding, vertical = CardPadding),
        verticalArrangement = Arrangement.spacedBy(ControlSpacing),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(HeadingGap)) {
            Text(
                text = title.uppercase(),
                color = PlaygroundChrome.MutedText,
                style = PlaygroundType.Eyebrow,
            )
            Text(text = caption, color = PlaygroundChrome.DimText, style = PlaygroundType.Caption)
        }
        content()
    }
}

/** Names a group of controls inside a card, with room above it so it reads as a divider. */
@Composable
private fun GroupLabel(text: String) {
    Text(
        text = text,
        color = PlaygroundChrome.MutedText,
        style = PlaygroundType.GroupLabel,
        modifier = Modifier.padding(top = GroupLabelTopSpacing),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> ChipRow(
    items: List<T>,
    isSelected: (T) -> Boolean,
    label: (T) -> String,
    tag: (T) -> String,
    onSelect: (T) -> Unit,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(ChipSpacing),
        verticalArrangement = Arrangement.spacedBy(ChipSpacing),
    ) {
        items.forEach { item ->
            Chip(
                label = label(item),
                selected = isSelected(item),
                tag = tag(item),
                onClick = { onSelect(item) },
            )
        }
    }
}

@Composable
private fun Chip(label: String, selected: Boolean, tag: String, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val shape = RoundedCornerShape(ChipCorner)
    val border = when {
        selected -> PlaygroundChrome.Accent
        hovered -> PlaygroundChrome.BorderStrong
        else -> PlaygroundChrome.Border
    }
    val content = when {
        selected -> PlaygroundChrome.OnAccent
        hovered -> PlaygroundChrome.Text
        else -> PlaygroundChrome.MutedText
    }
    Text(
        text = label,
        color = content,
        style = PlaygroundType.Chip,
        maxLines = 1,
        modifier = Modifier
            .clip(shape)
            .background(if (selected) PlaygroundChrome.Accent else Color.Transparent)
            .border(width = 1.dp, color = border, shape = shape)
            .hoverable(interactionSource = interactionSource)
            .clickable { onClick() }
            .testTag(tag)
            .padding(horizontal = ChipPaddingHorizontal, vertical = ChipPaddingVertical),
    )
}

@Composable
private fun ControlSlider(
    label: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    tag: String,
    onValueChange: (Float) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ControlName(label = label, hint = null)
            Text(
                text = valueText,
                color = PlaygroundChrome.Text,
                style = PlaygroundType.Value,
                maxLines = 1,
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            steps = steps,
            modifier = Modifier.height(SliderHeight).testTag(tag),
            colors = SliderDefaults.colors(
                thumbColor = PlaygroundChrome.Accent,
                activeTrackColor = PlaygroundChrome.Accent,
                inactiveTrackColor = PlaygroundChrome.Border,
                activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent,
            ),
        )
    }
}

@Composable
private fun ControlSwitch(
    label: String,
    hint: String,
    checked: Boolean,
    tag: String,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = SwitchRowPadding),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ControlName(label = label, hint = hint)
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(tag),
            colors = SwitchDefaults.colors(
                checkedThumbColor = PlaygroundChrome.OnAccent,
                checkedTrackColor = PlaygroundChrome.Accent,
                checkedBorderColor = PlaygroundChrome.Accent,
                uncheckedThumbColor = PlaygroundChrome.DimText,
                uncheckedTrackColor = Color.Transparent,
                uncheckedBorderColor = PlaygroundChrome.Border,
            ),
        )
    }
}

/**
 * A control's name, and under it the API it maps onto.
 *
 * Weighted rather than sized, and allowed to wrap, so a long label in a narrow column reflows
 * instead of being ellipsized into "Adjacent month d…" — the panel's job is to name the token, and a
 * truncated token names nothing.
 */
@Composable
private fun RowScope.ControlName(label: String, hint: String?) {
    Column(modifier = Modifier.weight(1f).padding(end = ControlNameGap)) {
        Text(text = label, color = PlaygroundChrome.Text, style = PlaygroundType.ControlLabel)
        if (hint != null) {
            Text(text = hint, color = PlaygroundChrome.DimText, style = PlaygroundType.ControlHint)
        }
    }
}

/**
 * Rounds a ratio to hundredths, so the slider reports exactly the number it prints — and so that
 * dragging back to `1.00` really does restore the stock token rather than leaving `0.9999998` behind
 * and an override in the snippet forever.
 */
private fun roundToStep(value: Float): Float = (value * RatioSteps).roundToInt() / RatioSteps

private fun snapToStep(value: Float): Int {
    val snapped = (value / SnapMinutesStep).roundToInt() * SnapMinutesStep
    return snapped.coerceIn(minimumValue = MinSnapMinutes.roundToInt(), maximumValue = MaxSnapMinutes.roundToInt())
}

private val SectionSpacing = 18.dp
private val ControlSpacing = 12.dp
private val HeadingGap = 3.dp
private val GroupLabelTopSpacing = 6.dp
private val CardCorner = 16.dp
private val CardPadding = 16.dp
private val ChipCorner = 10.dp
private val ChipSpacing = 8.dp
private val ChipPaddingHorizontal = 12.dp
private val ChipPaddingVertical = 8.dp
private val SliderHeight = 30.dp
private val SwitchRowPadding = 2.dp
private val ControlNameGap = 16.dp
private val SwatchSize = 30.dp
private val SwatchSpacing = 12.dp
private val SelectedSwatchBorder = 3.dp
private val UnselectedSwatchBorder = 1.dp

private const val MinCellPadding = 0f
private const val MaxCellPadding = 12f
private const val CellPaddingSteps = 11

private const val MinCellRatio = 0.55f
private const val MaxCellRatio = 1.4f
private const val CellRatioSteps = 16
private const val RatioSteps = 100f

private const val MinDotSize = 2f
private const val MaxDotSize = 12f
private const val DotSizeSteps = 9

private const val MinGridThickness = 1f
private const val MaxGridThickness = 5f
private const val GridThicknessSteps = 3

private const val MinHourHeight = 40f
private const val MaxHourHeight = 120f
private const val HourHeightSteps = 9

private const val MinScrollHour = 0f
private const val MaxScrollHour = 23f
private const val ScrollHourSteps = 22

private const val MinSnapMinutes = 5f
private const val MaxSnapMinutes = 60f
private const val SnapMinutesSteps = 10
private const val SnapMinutesStep = 5

private const val MinIndicatorCap = 1f
private const val MaxIndicatorCap = 6f
private const val IndicatorCapSteps = 4
