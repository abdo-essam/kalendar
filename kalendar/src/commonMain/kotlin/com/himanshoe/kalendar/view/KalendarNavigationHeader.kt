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

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.minus
import kotlinx.datetime.plus

private const val JUMP_PICKER_MONTH_COLUMNS = 4
private val JUMP_PICKER_PADDING = 8.dp
private val JUMP_PICKER_LABEL_PADDING = 2.dp
private val JUMP_PICKER_MONTH_WIDTH = 72.dp
private val JUMP_PICKER_MONTH_HEIGHT = 40.dp

/**
 * The touch target every header control keeps, whatever [KalendarDimensions.headerIconSlotWidth] is
 * set to. Shrinking a button below this is an accessibility failure, not a style choice, so the slot
 * width raises the target but never lowers it.
 */
private val MIN_TOUCH_TARGET = 48.dp

private val ICON_SIZE = 24.dp
private val ICON_STROKE_WIDTH = 2.dp

private const val CHEVRON_TIP_FRACTION = 0.375f
private const val CHEVRON_BASE_FRACTION = 0.625f
private const val CHEVRON_TOP_FRACTION = 0.27f
private const val CHEVRON_BOTTOM_FRACTION = 0.73f
private const val CHEVRON_MID_FRACTION = 0.5f

private const val TODAY_LEFT_FRACTION = 0.125f
private const val TODAY_RIGHT_FRACTION = 0.875f
private const val TODAY_TOP_FRACTION = 0.167f
private const val TODAY_BOTTOM_FRACTION = 0.917f
private const val TODAY_CORNER_FRACTION = 0.125f
private const val TODAY_BAR_BOTTOM_FRACTION = 0.333f
private const val TODAY_DOT_CENTER_X_FRACTION = 0.396f
private const val TODAY_DOT_CENTER_Y_FRACTION = 0.521f
private const val TODAY_DOT_RADIUS_FRACTION = 0.104f

/**
 * [KalendarHeaderScope] over a pager-backed [KalendarViewState] — the state behind [KalendarWeek],
 * [KalendarMonth], [KalendarYear], and the Schedule views.
 *
 * Every navigation call launches the matching `suspend` function on [coroutineScope], so a header
 * slot can navigate straight from an `onClick` without owning a coroutine scope of its own. The
 * reported flags read through to [state], so they stay snapshot-observable.
 */
private class KalendarPagerHeaderScope(
    override val title: String,
    private val state: KalendarViewState,
    private val coroutineScope: CoroutineScope,
) : KalendarHeaderScope {

    override val visibleDate: LocalDate get() = state.visibleDate

    override val canScrollBackward: Boolean get() = state.canScrollBackward

    override val canScrollForward: Boolean get() = state.canScrollForward

    override fun goToPreviousPage() {
        coroutineScope.launch { state.animateToPreviousPage() }
    }

    override fun goToNextPage() {
        coroutineScope.launch { state.animateToNextPage() }
    }

    override fun goToToday() {
        coroutineScope.launch { state.animateScrollToToday() }
    }

    override fun goTo(date: LocalDate) {
        coroutineScope.launch { state.animateScrollTo(date) }
    }
}

/**
 * [KalendarHeaderScope] over a [KalendarTimelineState], which scrolls continuously and therefore
 * has no page concept at all. The interface's page vocabulary is mapped onto months:
 *
 * - [KalendarHeaderScope.canScrollBackward] and [KalendarHeaderScope.canScrollForward] are always
 *   `true`. The timeline's `minDate`/`maxDate` clamp the scroll itself, so a header never has to
 *   disable its own arrows; a call that would leave the bounds lands on the nearest month instead.
 * - [KalendarHeaderScope.goToPreviousPage] and [KalendarHeaderScope.goToNextPage] scroll one month
 *   back and forward from [KalendarHeaderScope.visibleDate], the month currently topmost.
 */
private class KalendarTimelineHeaderScope(
    override val title: String,
    private val state: KalendarTimelineState,
    private val coroutineScope: CoroutineScope,
) : KalendarHeaderScope {

    override val visibleDate: LocalDate get() = state.visibleDate

    override val canScrollBackward: Boolean = true

    override val canScrollForward: Boolean = true

    override fun goToPreviousPage() {
        coroutineScope.launch { state.animateScrollTo(state.visibleDate.minus(1, DateTimeUnit.MONTH)) }
    }

    override fun goToNextPage() {
        coroutineScope.launch { state.animateScrollTo(state.visibleDate.plus(1, DateTimeUnit.MONTH)) }
    }

    override fun goToToday() {
        coroutineScope.launch { state.animateScrollToToday() }
    }

    override fun goTo(date: LocalDate) {
        coroutineScope.launch { state.animateScrollTo(date) }
    }
}

/**
 * Creates and [remember]s the [KalendarHeaderScope] handed to the `header` slot of the paged views,
 * bound to [state] and to the [title] the view has already formatted.
 */
@Composable
internal fun rememberKalendarHeaderScope(
    state: KalendarViewState,
    title: String,
): KalendarHeaderScope {
    val coroutineScope = rememberCoroutineScope()
    return remember(state, coroutineScope, title) {
        KalendarPagerHeaderScope(title = title, state = state, coroutineScope = coroutineScope)
    }
}

/**
 * Creates and [remember]s the [KalendarHeaderScope] handed to [KalendarTimeline]'s `header` slot.
 * See [KalendarTimelineHeaderScope] for how the interface's page vocabulary maps onto a timeline
 * that has no pages.
 */
@Composable
internal fun rememberKalendarTimelineHeaderScope(
    state: KalendarTimelineState,
    title: String,
): KalendarHeaderScope {
    val coroutineScope = rememberCoroutineScope()
    return remember(state, coroutineScope, title) {
        KalendarTimelineHeaderScope(title = title, state = state, coroutineScope = coroutineScope)
    }
}

/**
 * The chrome every Kalendar view draws around its grid — the navigation header and the day-of-week
 * column labels — exposed so a `header` or `dayOfWeekLabel` slot can reuse a piece of it instead of
 * rebuilding the whole thing.
 *
 * These are the exact composables the views fall back to when the corresponding slot is left at its
 * default, so calling one with different arguments changes only what you asked to change:
 *
 * ```kotlin
 * KalendarMonth(
 *     selectedDate = today,
 *     // Keep the built-in header, but without the jump picker.
 *     header = { scope -> KalendarHeaderDefaults.Header(scope = scope, showJumpPicker = false) },
 *     // Keep the built-in label styling, but with two-letter names.
 *     dayOfWeekLabel = { dayOfWeek ->
 *         KalendarHeaderDefaults.DayOfWeekLabel(dayOfWeek = dayOfWeek, formatter = { it.name.take(2) })
 *     },
 * )
 * ```
 */
public object KalendarHeaderDefaults {

    /**
     * The built-in navigation header: a centered title flanked by previous/next arrows, an optional
     * today button, and an optional month/year jump picker opened by tapping the title. This is the
     * default `header` for [KalendarWeek], [KalendarMonth], and [KalendarYear]. Every control drives
     * [scope] rather than any particular state type, so it also works in [KalendarTimeline]'s
     * `header` slot if you prefer arrows there over the default [TimelineHeader].
     *
     * The title is centered via [Box] alignment rather than [Row]'s `SpaceBetween` (which centers in
     * the leftover space between two side groups — the moment those groups are different widths,
     * e.g. a today button on one side and nothing on the other, the title visibly drifts off-center).
     * Its available width is then constrained to exactly how much space is actually reserved for
     * icons on each side, so it can never render underneath one; [TextOverflow.Ellipsis] is the
     * fallback for a title still too long for what's left (e.g. a long locale-formatted month name).
     *
     * @param scope Navigation state and callbacks for the view this header belongs to. Inside a
     *   `header` slot this is the value the slot was handed.
     * @param modifier [Modifier] applied to the header's outermost container.
     * @param showNavigationArrows Whether to draw the previous/next arrow buttons. Each is disabled
     *   when the matching [KalendarHeaderScope.canScrollBackward]/[KalendarHeaderScope.canScrollForward]
     *   is `false`.
     * @param showTodayButton Whether to draw the button that navigates back to today.
     * @param showJumpPicker Whether tapping the title opens the month/year jump picker.
     * @param monthNameFormatter Formats the month name a screen reader announces for one jump-picker
     *   button. See [KalendarViewConfig.monthNameFormatter].
     * @param shortMonthNameFormatter Formats the month name a jump-picker button *shows*. The
     *   buttons are a fixed width, so an abbreviation that still doesn't fit is ellipsized rather
     *   than widening the picker. See [KalendarViewConfig.shortMonthNameFormatter].
     * @param previousIcon The glyph inside the previous-page button. Defaults to a chevron drawn
     *   with Compose Foundation in [KalendarColors.headerContent], mirrored in RTL. The button
     *   around it — its touch target, its click, and its accessibility label — belongs to the
     *   header, so a replacement only draws.
     * @param nextIcon The glyph inside the next-page button. Defaults to [previousIcon]'s chevron
     *   pointing the other way.
     * @param todayIcon The glyph inside the today button. Defaults to a calendar page drawn with
     *   Compose Foundation in [KalendarColors.headerContent].
     */
    @Composable
    public fun Header(
        scope: KalendarHeaderScope,
        modifier: Modifier = Modifier,
        showNavigationArrows: Boolean = true,
        showTodayButton: Boolean = true,
        showJumpPicker: Boolean = true,
        monthNameFormatter: (Month) -> String = defaultMonthNameFormatter,
        shortMonthNameFormatter: (Month) -> String = defaultShortMonthNameFormatter,
        previousIcon: @Composable () -> Unit = { KalendarChevronIcon(forward = false) },
        nextIcon: @Composable () -> Unit = { KalendarChevronIcon(forward = true) },
        todayIcon: @Composable () -> Unit = { KalendarTodayIcon() },
    ) {
        val colors = kalendarColors()
        val dimensions = kalendarDimensions()
        val strings = kalendarStrings()
        var pickerOpen by remember { mutableStateOf(false) }
        // One slot is one [IconButton]'s touch target — deliberately tight (no extra gap padding)
        // since it is subtracted from the title's available width on narrow phones (~320.dp) too; a
        // title long enough to need the full width already has a visible gap from the icon just by
        // not extending into its reserved slot, and TextOverflow.Ellipsis covers the rest.
        val leadingReserved = if (showNavigationArrows) dimensions.headerIconSlotWidth else 0.dp
        val trailingReserved = (if (showTodayButton) dimensions.headerIconSlotWidth else 0.dp) +
            (if (showNavigationArrows) dimensions.headerIconSlotWidth else 0.dp)

        Box(
            modifier = modifier.fillMaxWidth().padding(dimensions.headerPadding),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(start = leadingReserved, end = trailingReserved),
                contentAlignment = Alignment.Center,
            ) {
                AnimatedHeaderTitle(title = scope.title) { animatedTitle ->
                    BasicText(
                        text = animatedTitle,
                        style = kalendarTypography().headerTitle.copy(color = colors.headerContent),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = if (showJumpPicker) {
                            Modifier.clickable(
                                onClickLabel = strings.openJumpPicker,
                                role = Role.Button,
                            ) { pickerOpen = true }
                        } else {
                            Modifier
                        },
                    )
                }
            }

            if (pickerOpen) {
                Popup(
                    alignment = Alignment.TopCenter,
                    onDismissRequest = { pickerOpen = false },
                ) {
                    JumpPicker(
                        initialYear = scope.visibleDate.year,
                        monthNameFormatter = monthNameFormatter,
                        shortMonthNameFormatter = shortMonthNameFormatter,
                        onMonthSelected = { year, month ->
                            pickerOpen = false
                            scope.goTo(LocalDate(year, month, 1))
                        },
                    )
                }
            }

            if (showNavigationArrows) {
                HeaderIconButton(
                    label = strings.previousPage,
                    enabled = scope.canScrollBackward,
                    onClick = scope::goToPreviousPage,
                    modifier = Modifier.align(Alignment.CenterStart),
                    content = previousIcon,
                )
            }

            Row(modifier = Modifier.align(Alignment.CenterEnd), verticalAlignment = Alignment.CenterVertically) {
                if (showTodayButton) {
                    HeaderIconButton(
                        label = strings.today,
                        onClick = scope::goToToday,
                        content = todayIcon,
                    )
                }
                if (showNavigationArrows) {
                    HeaderIconButton(
                        label = strings.nextPage,
                        enabled = scope.canScrollForward,
                        onClick = scope::goToNextPage,
                        content = nextIcon,
                    )
                }
            }
        }
    }

    /**
     * [KalendarTimeline]'s sticky overlay: the topmost visible month's title, an optional today
     * button, and the divider that separates the overlay from the scrolling months.
     *
     * This is a separate default from [Header] because the timeline's chrome is a different shape —
     * leading-aligned title, no arrows, and an opaque fill so the months scroll underneath it.
     *
     * @param scope Navigation state and callbacks for the timeline this header belongs to. Inside a
     *   `header` slot this is the value the slot was handed.
     * @param modifier [Modifier] applied to the overlay's outermost container.
     * @param showTodayButton Whether to draw the button that scrolls back to today.
     * @param background Fill painted behind the overlay, which has to be opaque for the scrolling
     *   content not to show through. Defaults to `null`, meaning [KalendarColors.background]; pass
     *   [KalendarViewConfig.background] when the calendar itself has a custom one.
     */
    @Composable
    public fun TimelineHeader(
        scope: KalendarHeaderScope,
        modifier: Modifier = Modifier,
        showTodayButton: Boolean = true,
        background: Brush? = null,
    ) {
        val colors = kalendarColors()
        val typography = kalendarTypography()
        val dimensions = kalendarDimensions()
        val fill = background ?: SolidColor(colors.background)

        Column(modifier = modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(fill)
                    .padding(dimensions.sectionPadding),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                AnimatedHeaderTitle(title = scope.title) { animatedTitle ->
                    BasicText(
                        text = animatedTitle,
                        style = typography.headerTitle.copy(color = colors.headerContent),
                    )
                }
                if (showTodayButton) {
                    HeaderIconButton(
                        label = kalendarStrings().today,
                        onClick = scope::goToToday,
                        content = { KalendarTodayIcon() },
                    )
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(dimensions.gridLineThickness)
                    .background(colors.gridLine),
            )
        }
    }

    /**
     * The built-in day-of-week column label: [formatter]'s text in
     * [KalendarTypography.dayOfWeekLabel], coloured [KalendarColors.dayLabelContent] and centered
     * in its column.
     *
     * A `dayOfWeekLabel` slot replaces only the label's *content* — the grid still measures and
     * places one label per column — so this stays aligned with the day cells below it whether you
     * use it as-is, restyle it, or replace it entirely.
     *
     * @param dayOfWeek The column's day. Inside a `dayOfWeekLabel` slot this is the value the slot
     *   was handed; columns run from the calendar's `startDayOfWeek`.
     * @param modifier [Modifier] applied to the label.
     * @param formatter Turns [dayOfWeek] into the text to draw. Defaults to the day name's first
     *   letter; see [KalendarViewConfig.dayOfWeekLabelFormatter], which is what the views pass in.
     *
     * What a screen reader hears is *not* what is drawn: the drawn text is a single letter, which
     * announces as "M, T, W, T, F, S, S" — seven initials rather than seven weekdays — so the label
     * carries [KalendarStrings.dayOfWeekAccessibilityLabel]'s full name as its description instead.
     */
    @Composable
    public fun DayOfWeekLabel(
        dayOfWeek: DayOfWeek,
        modifier: Modifier = Modifier,
        formatter: (DayOfWeek) -> String = defaultDayOfWeekLabelFormatter,
    ) {
        val strings = kalendarStrings()
        BasicText(
            text = formatter(dayOfWeek),
            style = kalendarTypography().dayOfWeekLabel.copy(
                color = kalendarColors().dayLabelContent,
                textAlign = TextAlign.Center,
            ),
            modifier = modifier
                .fillMaxWidth()
                .semantics { contentDescription = strings.dayOfWeekAccessibilityLabel(dayOfWeek) },
        )
    }
}

/**
 * Config-resolving wrapper around [KalendarHeaderDefaults.Header] for the views that still drive
 * their header from a [KalendarViewState] rather than a slot.
 */
@Composable
internal fun KalendarNavigationHeader(
    title: String,
    state: KalendarViewState,
    showArrows: Boolean,
    modifier: Modifier = Modifier,
    showTodayButton: Boolean = false,
    showJumpPicker: Boolean = false,
    monthNameFormatter: (Month) -> String = defaultMonthNameFormatter,
    shortMonthNameFormatter: (Month) -> String = defaultShortMonthNameFormatter,
) {
    KalendarHeaderDefaults.Header(
        scope = rememberKalendarHeaderScope(state = state, title = title),
        modifier = modifier,
        showNavigationArrows = showArrows,
        showTodayButton = showTodayButton,
        showJumpPicker = showJumpPicker,
        monthNameFormatter = monthNameFormatter,
        shortMonthNameFormatter = shortMonthNameFormatter,
    )
}

/**
 * The header title's crossfade, shared by [KalendarHeaderDefaults.Header] and
 * [KalendarHeaderDefaults.TimelineHeader]: a fade lasting
 * [KalendarAnimations.titleTransitionMillis], or an instant swap when [KalendarAnimations.enabled]
 * is `false`.
 */
@Composable
private fun AnimatedHeaderTitle(
    title: String,
    content: @Composable (title: String) -> Unit,
) {
    val animations = kalendarAnimations()
    AnimatedContent(
        targetState = title,
        transitionSpec = {
            if (animations.enabled) {
                val duration = animations.titleTransitionMillis
                fadeIn(tween(duration)) togetherWith fadeOut(tween(duration))
            } else {
                EnterTransition.None togetherWith ExitTransition.None
            }
        },
    ) { animatedTitle ->
        content(animatedTitle)
    }
}

/**
 * The month/year jump popup opened by tapping the header title: a year stepper above a 4x3 grid
 * of month names; choosing a month closes the popup and scrolls the calendar to that month.
 *
 * The popup floats over the calendar, so it needs an opaque fill of its own —
 * [KalendarColors.popupBackground], outlined with [KalendarColors.popupBorder] so it stays
 * distinguishable from a background of a similar shade.
 */
@Composable
private fun JumpPicker(
    initialYear: Int,
    monthNameFormatter: (Month) -> String,
    shortMonthNameFormatter: (Month) -> String,
    onMonthSelected: (year: Int, month: Month) -> Unit,
) {
    val colors = kalendarColors()
    val typography = kalendarTypography()
    val shapes = kalendarShapes()
    val strings = kalendarStrings()
    var year by remember { mutableIntStateOf(initialYear) }
    Box(
        modifier = Modifier
            .clip(shapes.popup)
            .background(colors.popupBackground)
            .border(
                width = kalendarDimensions().gridLineThickness,
                color = colors.popupBorder,
                shape = shapes.popup,
            ),
    ) {
        Column(
            modifier = Modifier.padding(JUMP_PICKER_PADDING),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                HeaderIconButton(
                    label = strings.previousYear,
                    onClick = { year-- },
                    content = { KalendarChevronIcon(forward = false) },
                )
                BasicText(
                    text = year.toString(),
                    style = typography.headerTitle.copy(color = colors.headerContent),
                    modifier = Modifier.padding(horizontal = JUMP_PICKER_PADDING),
                )
                HeaderIconButton(
                    label = strings.nextYear,
                    onClick = { year++ },
                    content = { KalendarChevronIcon(forward = true) },
                )
            }
            Month.entries.chunked(JUMP_PICKER_MONTH_COLUMNS).forEach { rowMonths ->
                Row {
                    rowMonths.forEach { month ->
                        Box(
                            modifier = Modifier
                                .width(JUMP_PICKER_MONTH_WIDTH)
                                .height(JUMP_PICKER_MONTH_HEIGHT)
                                .semantics { contentDescription = monthNameFormatter(month) }
                                .clickable(role = Role.Button) { onMonthSelected(year, month) },
                            contentAlignment = Alignment.Center,
                        ) {
                            BasicText(
                                text = shortMonthNameFormatter(month),
                                style = typography.monthLabel.copy(color = colors.headerContent),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = JUMP_PICKER_LABEL_PADDING),
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * One header control: a [MIN_TOUCH_TARGET]-or-larger square carrying the click, the [Role.Button]
 * role and the accessibility label, with [content] centered inside it.
 *
 * The label lives here rather than on the glyph so that a caller-supplied icon slot stays
 * accessible without having to know anything about semantics.
 */
@Composable
private fun HeaderIconButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colors = kalendarColors()
    Box(
        modifier = modifier
            .sizeIn(minWidth = MIN_TOUCH_TARGET, minHeight = MIN_TOUCH_TARGET)
            .size(kalendarDimensions().headerIconSlotWidth)
            .semantics {
                contentDescription = label
                if (!enabled) {
                    disabled()
                }
            }
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        val tint = if (enabled) colors.headerContent else colors.headerContentDisabled
        CompositionLocalProvider(LocalKalendarHeaderIconTint provides tint, content = content)
    }
}

/**
 * The colour the built-in header glyphs draw themselves in — [KalendarColors.headerContent], or
 * [KalendarColors.headerContentDisabled] inside a [HeaderIconButton] whose direction is blocked.
 *
 * It travels as a local rather than a parameter because the glyphs arrive through the `previousIcon`
 * /`nextIcon`/`todayIcon` slots, which the button cannot reach into. `null` means "not inside a
 * header button", which is how [KalendarHeaderDefaults] renders a glyph on its own.
 */
private val LocalKalendarHeaderIconTint = staticCompositionLocalOf<Color?> { null }

/**
 * The built-in navigation chevron, drawn as two strokes in [KalendarColors.headerContent] so the
 * library needs no icon dependency.
 *
 * [forward] is a reading-order direction, not a screen direction: a forward chevron points right
 * in LTR and left in RTL, which is why the glyph is drawn pointing left and mirrored from
 * [LocalLayoutDirection] rather than baked into a fixed path.
 */
@Composable
private fun KalendarChevronIcon(forward: Boolean) {
    val tint = LocalKalendarHeaderIconTint.current ?: kalendarColors().headerContent
    val pointsRight = forward == (LocalLayoutDirection.current == LayoutDirection.Ltr)
    Canvas(
        modifier = Modifier
            .size(ICON_SIZE)
            .scale(
                scaleX = if (pointsRight) -1f else 1f,
                scaleY = 1f,
            ),
    ) {
        val baseX = size.width * CHEVRON_BASE_FRACTION
        val tipX = size.width * CHEVRON_TIP_FRACTION
        val midY = size.height * CHEVRON_MID_FRACTION
        val strokeWidth = ICON_STROKE_WIDTH.toPx()
        drawLine(
            color = tint,
            start = Offset(x = baseX, y = size.height * CHEVRON_TOP_FRACTION),
            end = Offset(x = tipX, y = midY),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = tint,
            start = Offset(x = tipX, y = midY),
            end = Offset(x = baseX, y = size.height * CHEVRON_BOTTOM_FRACTION),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
    }
}

/**
 * The built-in today glyph, drawn in [KalendarColors.headerContent] as a calendar page — an
 * outlined rounded rectangle, a filled header bar across its top, and a dot marking a date — so the
 * library needs no icon dependency. It is direction-neutral and therefore never mirrored.
 */
@Composable
private fun KalendarTodayIcon() {
    val tint = LocalKalendarHeaderIconTint.current ?: kalendarColors().headerContent
    Canvas(modifier = Modifier.size(ICON_SIZE)) {
        val left = size.width * TODAY_LEFT_FRACTION
        val right = size.width * TODAY_RIGHT_FRACTION
        val top = size.height * TODAY_TOP_FRACTION
        val bottom = size.height * TODAY_BOTTOM_FRACTION
        val corner = CornerRadius(size.width * TODAY_CORNER_FRACTION)
        val strokeWidth = ICON_STROKE_WIDTH.toPx()
        drawRoundRect(
            color = tint,
            topLeft = Offset(x = left, y = top),
            size = Size(width = right - left, height = bottom - top),
            cornerRadius = corner,
            style = Stroke(width = strokeWidth),
        )
        drawPath(
            path = Path().apply {
                addRoundRect(
                    RoundRect(
                        left = left,
                        top = top,
                        right = right,
                        bottom = size.height * TODAY_BAR_BOTTOM_FRACTION,
                        topLeftCornerRadius = corner,
                        topRightCornerRadius = corner,
                        bottomLeftCornerRadius = CornerRadius.Zero,
                        bottomRightCornerRadius = CornerRadius.Zero,
                    ),
                )
            },
            color = tint,
        )
        drawCircle(
            color = tint,
            radius = size.width * TODAY_DOT_RADIUS_FRACTION,
            center = Offset(
                x = size.width * TODAY_DOT_CENTER_X_FRACTION,
                y = size.height * TODAY_DOT_CENTER_Y_FRACTION,
            ),
        )
    }
}
