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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
private const val JUMP_PICKER_CLICK_LABEL = "Open the month and year picker"

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
        coroutineScope.launch { state.scrollTo(state.visibleDate.minus(1, DateTimeUnit.MONTH)) }
    }

    override fun goToNextPage() {
        coroutineScope.launch { state.scrollTo(state.visibleDate.plus(1, DateTimeUnit.MONTH)) }
    }

    override fun goToToday() {
        coroutineScope.launch { state.scrollToToday() }
    }

    override fun goTo(date: LocalDate) {
        coroutineScope.launch { state.scrollTo(date) }
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
     * @param monthNameFormatter Formats the month names inside the jump picker. See
     *   [KalendarViewConfig.monthNameFormatter].
     */
    @Composable
    public fun Header(
        scope: KalendarHeaderScope,
        modifier: Modifier = Modifier,
        showNavigationArrows: Boolean = true,
        showTodayButton: Boolean = true,
        showJumpPicker: Boolean = true,
        monthNameFormatter: (Month) -> String = defaultMonthNameFormatter,
    ) {
        val colors = kalendarColors()
        val dimensions = kalendarDimensions()
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
                    Text(
                        text = animatedTitle,
                        style = kalendarTypography().headerTitle,
                        color = colors.headerContent,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = if (showJumpPicker) {
                            Modifier.clickable(
                                onClickLabel = JUMP_PICKER_CLICK_LABEL,
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
                        onMonthSelected = { year, month ->
                            pickerOpen = false
                            scope.goTo(LocalDate(year, month, 1))
                        },
                    )
                }
            }

            if (showNavigationArrows) {
                IconButton(
                    enabled = scope.canScrollBackward,
                    onClick = scope::goToPreviousPage,
                    modifier = Modifier.align(Alignment.CenterStart),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        contentDescription = "Previous",
                        tint = colors.headerContent,
                    )
                }
            }

            Row(modifier = Modifier.align(Alignment.CenterEnd), verticalAlignment = Alignment.CenterVertically) {
                if (showTodayButton) {
                    IconButton(onClick = scope::goToToday) {
                        Icon(
                            imageVector = Icons.Filled.Today,
                            contentDescription = "Today",
                            tint = colors.headerContent,
                        )
                    }
                }
                if (showNavigationArrows) {
                    IconButton(
                        enabled = scope.canScrollForward,
                        onClick = scope::goToNextPage,
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = "Next",
                            tint = colors.headerContent,
                        )
                    }
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
                    Text(
                        text = animatedTitle,
                        style = typography.headerTitle,
                        color = colors.headerContent,
                    )
                }
                if (showTodayButton) {
                    IconButton(onClick = scope::goToToday) {
                        Icon(
                            imageVector = Icons.Filled.Today,
                            contentDescription = "Today",
                            tint = colors.headerContent,
                        )
                    }
                }
            }
            HorizontalDivider(color = colors.gridLine)
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
     */
    @Composable
    public fun DayOfWeekLabel(
        dayOfWeek: DayOfWeek,
        modifier: Modifier = Modifier,
        formatter: (DayOfWeek) -> String = defaultDayOfWeekLabelFormatter,
    ) {
        Text(
            text = formatter(dayOfWeek),
            style = kalendarTypography().dayOfWeekLabel,
            color = kalendarColors().dayLabelContent,
            textAlign = TextAlign.Center,
            modifier = modifier.fillMaxWidth(),
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
) {
    KalendarHeaderDefaults.Header(
        scope = rememberKalendarHeaderScope(state = state, title = title),
        modifier = modifier,
        showNavigationArrows = showArrows,
        showTodayButton = showTodayButton,
        showJumpPicker = showJumpPicker,
        monthNameFormatter = monthNameFormatter,
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
 */
@Composable
private fun JumpPicker(
    initialYear: Int,
    monthNameFormatter: (Month) -> String,
    onMonthSelected: (year: Int, month: Month) -> Unit,
) {
    val colors = kalendarColors()
    var year by remember { mutableIntStateOf(initialYear) }
    Surface(
        shape = kalendarShapes().popup,
        tonalElevation = 3.dp,
        shadowElevation = 6.dp,
    ) {
        Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { year-- }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        contentDescription = "Previous year",
                        tint = colors.headerContent,
                    )
                }
                Text(
                    text = year.toString(),
                    style = kalendarTypography().headerTitle,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
                IconButton(onClick = { year++ }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "Next year",
                        tint = colors.headerContent,
                    )
                }
            }
            Month.entries.chunked(JUMP_PICKER_MONTH_COLUMNS).forEach { rowMonths ->
                Row {
                    rowMonths.forEach { month ->
                        TextButton(
                            onClick = { onMonthSelected(year, month) },
                            modifier = Modifier.width(72.dp),
                        ) {
                            Text(text = monthNameFormatter(month).take(3))
                        }
                    }
                }
            }
        }
    }
}
