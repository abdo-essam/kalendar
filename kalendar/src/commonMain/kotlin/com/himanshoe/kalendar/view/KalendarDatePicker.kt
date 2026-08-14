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

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.plus

/** Gap between the header's month control and its year control. */
private val HEADER_CONTROL_GAP = 4.dp

/** Inset around one header dropdown's label-and-caret pair. */
private val HEADER_CONTROL_PADDING = 6.dp

/** Gap between a dropdown's label and its caret. */
private val HEADER_CARET_GAP = 2.dp

/** Edge of the square every header button and action reserves, whatever its content measures. */
private val MIN_TOUCH_TARGET = 48.dp

private val ICON_SIZE = 24.dp
private val CARET_SIZE = 16.dp
private val ICON_STROKE_WIDTH = 2.dp

private const val CHEVRON_TIP_FRACTION = 0.375f
private const val CHEVRON_BASE_FRACTION = 0.625f
private const val CHEVRON_TOP_FRACTION = 0.27f
private const val CHEVRON_BOTTOM_FRACTION = 0.73f
private const val CHEVRON_MID_FRACTION = 0.5f

/** Degrees a leading chevron is turned by to point down, i.e. to read as a dropdown affordance. */
private const val CARET_ROTATION_DEGREES = -90f

private val PICKER_POPUP_PADDING = 8.dp
private val PICKER_POPUP_MAX_HEIGHT = 232.dp

private const val MONTH_PICKER_COLUMNS = 4
private val MONTH_PICKER_CELL_WIDTH = 64.dp
private val MONTH_PICKER_CELL_HEIGHT = 40.dp

private const val YEAR_PICKER_COLUMNS = 3
private val YEAR_PICKER_CELL_WIDTH = 68.dp
private val YEAR_PICKER_CELL_HEIGHT = 40.dp

/**
 * Rows of years kept above the selected one when the year grid first opens, so the current year
 * lands just inside the top edge rather than flush against it — a year flush against the edge reads
 * as the first year available, which for a birthdate picker is exactly the wrong impression.
 */
private const val YEAR_PICKER_LEAD_ROWS = 2

/** Gap between the day number and the rule that marks today. */
private val TODAY_UNDERLINE_GAP = 2.dp

/** Inset around the action row's contents. */
private val ACTION_ROW_PADDING = 4.dp

/** Gap between adjacent text actions. */
private val ACTION_GAP = 4.dp

/** Inset around one text action's label, inside its [MIN_TOUCH_TARGET] square. */
private val ACTION_LABEL_PADDING = 12.dp

/**
 * Fallback for the Clear action's label.
 *
 * [KalendarStrings] has no entry for it — the string this library owns closest to "discard the
 * current selection" is [KalendarStrings.today], which is a different action — so the label is a
 * parameter on [KalendarDatePickerDefaults.ActionRow] defaulting to plain English. Pass your own
 * resolved string to localize it until `KalendarStrings.clearSelection` exists.
 */
private const val FALLBACK_CLEAR_LABEL = "Clear"

/**
 * One date cell of a [KalendarDatePicker], as handed to its `dayContent` slot.
 *
 * It wraps rather than replaces [KalendarDayScope] — the scope every other Kalendar view's day slot
 * receives — so a cell written for [KalendarMonth] can be reused here by reaching through [day], and
 * so the fields a picker adds are visibly the picker's rather than pretending to be universal.
 *
 * Delivered as a class rather than positional lambda parameters so later releases can add fields
 * without breaking every existing `dayContent` call site.
 *
 * @property day The date and its shared state: which date this is, whether it is selected, whether it
 *   is today, whether it may be tapped, and (always empty here — a picker draws no events) its
 *   events. [KalendarDayScope.isDisabled] is the *authority* on tappability, and folds together the
 *   calendar's `minDate`/`maxDate` bounds and its `disabledDates` predicate.
 * @property isInRange Whether this date falls between the endpoints of a
 *   [KalendarSelectionMode.Range] selection, inclusive. Always `false` in the other modes. This is
 *   what a cell needs to draw a continuous band rather than one circle per day; note it is computed
 *   from the minimum and maximum of the selection, so it is correct whether the caller supplies the
 *   whole span or only the two endpoints.
 * @property isRangeStart Whether this is the earliest date of a range selection — the end that takes
 *   the leading rounded cap.
 * @property isRangeEnd Whether this is the latest date of a range selection. Both this and
 *   [isRangeStart] are `true` for a range that has only been half-committed, i.e. one tap in.
 * @property isAdjacentMonth Whether this date belongs to the previous or next month and is only on
 *   screen to complete the grid's first or last week. Such dates are dimmed but, unlike in
 *   [KalendarMonth], remain selectable: a picker that showed the 1st of next month and then refused
 *   it would be asking the user to page for no reason.
 */
@Immutable
public class KalendarDatePickerDayScope(
    public val day: KalendarDayScope,
    public val isInRange: Boolean = false,
    public val isRangeStart: Boolean = false,
    public val isRangeEnd: Boolean = false,
    public val isAdjacentMonth: Boolean = false,
) {

    /** Returns a duplicate of this scope with only the fields passed here replaced. */
    public fun copy(
        day: KalendarDayScope = this.day,
        isInRange: Boolean = this.isInRange,
        isRangeStart: Boolean = this.isRangeStart,
        isRangeEnd: Boolean = this.isRangeEnd,
        isAdjacentMonth: Boolean = this.isAdjacentMonth,
    ): KalendarDatePickerDayScope = KalendarDatePickerDayScope(
        day = day,
        isInRange = isInRange,
        isRangeStart = isRangeStart,
        isRangeEnd = isRangeEnd,
        isAdjacentMonth = isAdjacentMonth,
    )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is KalendarDatePickerDayScope) return false
        if (day != other.day) return false
        if (isInRange != other.isInRange) return false
        if (isRangeStart != other.isRangeStart) return false
        if (isRangeEnd != other.isRangeEnd) return false
        if (isAdjacentMonth != other.isAdjacentMonth) return false
        return true
    }

    override fun hashCode(): Int {
        var result = day.hashCode()
        result = 31 * result + isInRange.hashCode()
        result = 31 * result + isRangeStart.hashCode()
        result = 31 * result + isRangeEnd.hashCode()
        result = 31 * result + isAdjacentMonth.hashCode()
        return result
    }

    override fun toString(): String = "KalendarDatePickerDayScope(" +
        "day=$day, " +
        "isInRange=$isInRange, " +
        "isRangeStart=$isRangeStart, " +
        "isRangeEnd=$isRangeEnd, " +
        "isAdjacentMonth=$isAdjacentMonth" +
        ")"
}

/**
 * What a [KalendarDatePicker]'s `actions` slot is given: which actions the picker was configured to
 * offer, and what they do.
 *
 * An action is offered only when the picker was handed a callback for it, so a replacement row can
 * honour that by asking [showClearAction]/[showTodayAction] rather than by hard-coding two buttons
 * and hoping. Calling an action the picker is not offering is harmless — it does nothing.
 *
 * @see KalendarHeaderScope for why this is an interface rather than a data class.
 */
@Stable
public interface KalendarDatePickerActionScope {

    /** Today's date in the picker's time zone, re-read when the day rolls over. */
    public val today: LocalDate

    /** Whether the picker was given an `onClear` handler and so wants a Clear action drawn. */
    public val showClearAction: Boolean

    /** Whether the picker was given an `onTodayClick` handler and so wants a Today action drawn. */
    public val showTodayAction: Boolean

    /**
     * Whether nothing is currently selected, so a row can grey out or omit its Clear action. The
     * built-in row leaves Clear live either way — clearing an empty selection is a no-op the user
     * never notices, and a control that greys out mid-interaction is worse than one that does
     * nothing.
     */
    public val isSelectionEmpty: Boolean

    /** Empties the selection through the picker's `onClear` handler. */
    public fun clear()

    /**
     * Scrolls the picker to the month containing [today] and reports it through the picker's
     * `onTodayClick` handler — but only reports it when today is actually selectable, so a picker
     * bounded to end last week navigates without smuggling an out-of-range date into the selection.
     */
    public fun selectToday()
}

/**
 * The [KalendarDatePickerActionScope] [KalendarDatePicker] hands its `actions` slot.
 *
 * [selectToday] navigates before it reports, so a handler that reads the visible month sees the month
 * it is about to land on rather than the one being left.
 */
private class KalendarDatePickerActionScopeImpl(
    override val today: LocalDate,
    override val isSelectionEmpty: Boolean,
    private val onClear: (() -> Unit)?,
    private val onTodayClick: ((LocalDate) -> Unit)?,
    private val isDateSelectable: (LocalDate) -> Boolean,
    private val state: KalendarViewState,
    private val coroutineScope: CoroutineScope,
) : KalendarDatePickerActionScope {

    override val showClearAction: Boolean = onClear != null

    override val showTodayAction: Boolean = onTodayClick != null

    override fun clear() {
        onClear?.invoke()
    }

    override fun selectToday() {
        coroutineScope.launch { state.animateScrollTo(today) }
        if (isDateSelectable(today)) {
            onTodayClick?.invoke(today)
        }
    }
}

/**
 * A compact, single-month date picker — the calendar that drops out of a form field, rather than the
 * calendar an app is built around.
 *
 * It is a different composable from [KalendarMonth] because it answers a different question. A month
 * view shows what is happening; a picker takes an answer, so it trades events, drag-to-reschedule and
 * full-bleed sizing for a header you can jump years with and a rail of commit actions:
 *
 * - **Header.** A month control and a year control, each opening straight onto its own grid, and the
 *   page arrows demoted to the trailing corner. See [KalendarDatePickerDefaults.Header] for why.
 * - **Today versus selected.** Today is a rule under the number; selected is a filled
 *   [KalendarShapes.dayCell]. They are different marks so both can be true of the same cell and still
 *   be read apart — see [KalendarDatePickerDefaults.DayCell].
 * - **Actions.** Clear and Today appear only when handed a handler, so the row never shows a control
 *   that would do nothing.
 *
 * It imposes no size; give it one. [KalendarDatePickerDefaults.Width] is the width it is drawn for.
 *
 * The grid is a **single tab stop** with a roving focus ring, the same keyboard model the grid views
 * use: `Tab` enters at the selected date (or today), arrows move a day or a row and page the calendar
 * when they run off the edge, `Home`/`End` reach the first and last of the month, `PageUp`/`PageDown`
 * move a month, and `Enter`/`Space` select. A date the bounds or [KalendarViewConfig.disabledDates]
 * rejected can be focused but not selected, so it is announced as unavailable rather than skipped.
 *
 * ```kotlin
 * val selection = rememberKalendarSelectionState(mode = KalendarSelectionMode.Range)
 * val state = rememberKalendarMonthState(maxDate = today)
 *
 * KalendarDatePicker(
 *     selectedDates = selection.selectedDates,
 *     onDateClick = selection::onDateClick,
 *     modifier = Modifier.width(KalendarDatePickerDefaults.Width),
 *     state = state,
 *     selectionMode = KalendarSelectionMode.Range,
 *     onClear = selection::clear,
 *     onTodayClick = selection::onDateClick,
 * )
 * ```
 *
 * @param selectedDates The highlighted dates. Hoisted, like every other Kalendar view's selection —
 *   pair it with [rememberKalendarSelectionState], which already implements every
 *   [KalendarSelectionMode]'s tap behaviour, or drive it yourself. `remember` the same instance
 *   across recompositions rather than building a fresh [Set] each pass.
 * @param onDateClick Called when a selectable date is tapped or committed from the keyboard. Wire it
 *   to [KalendarSelectionState.onDateClick] for the built-in behaviour of the chosen mode.
 * @param modifier [Modifier] applied to the outermost container. The picker fills what it is given;
 *   see [KalendarDatePickerDefaults.Width].
 * @param state Navigation state from [rememberKalendarMonthState]. **This is also where `minDate` and
 *   `maxDate` live** — they are properties of the calendar's paging, not of the picker's chrome, and
 *   putting them here means one bound both disables the out-of-range cells *and* stops the header's
 *   arrows at the same edge, instead of two settings that can disagree. Own the state yourself to
 *   read [KalendarViewState.visibleDate] or to scroll it from elsewhere.
 * @param selectionMode How the selection is meant to be read. It does not *apply* tap behaviour —
 *   that belongs to [KalendarSelectionState], whose own mode this should match — it tells the picker
 *   how to draw: [KalendarSelectionMode.Range] fills the span between the earliest and latest
 *   selected dates as one continuous band, the other two draw one mark per selected date. Defaults to
 *   [KalendarSelectionMode.Single].
 * @param config Shared visual/behavioural settings, the same type the grid views take:
 *   [KalendarViewConfig.disabledDates] for dates that exist but may not be chosen,
 *   [KalendarViewConfig.showAdjacentMonthDates], [KalendarViewConfig.showNavigationArrows],
 *   [KalendarViewConfig.visibleDaysOfWeek], the formatters, and
 *   [KalendarViewConfig.background]. Its Schedule-only and event-only options are ignored here.
 * @param yearRange The years the header's year control offers. Defaults to `null`, meaning derive it
 *   from [state]'s bounds where they exist and a century either side of the visible year where they
 *   do not — wide enough that a birthdate is scrolled to rather than stepped to.
 * @param onClear Called when the Clear action is used. `null` — the default — hides that action
 *   entirely, independently of [onTodayClick]. Wire it to [KalendarSelectionState.clear].
 * @param onTodayClick Called with today's date when the Today action is used, after the picker has
 *   scrolled to today's month, and only when today is itself selectable. `null` — the default —
 *   hides that action, independently of [onClear].
 * @param header Replaces the header above the grid. Receives the same [KalendarHeaderScope] the grid
 *   views' headers get, so a replacement keeps working navigation. Defaults to
 *   [KalendarDatePickerDefaults.Header] driven by [config].
 * @param dayOfWeekLabel Replaces the content of each weekday column header. The grid still places one
 *   per column, so a replacement stays aligned with the dates below it. Defaults to
 *   [KalendarHeaderDefaults.DayOfWeekLabel] — shared with the grid views, since a column initial is a
 *   column initial — formatted by [config].
 * @param dayContent Replaces the day cell. Receives a [KalendarDatePickerDayScope]. Defaults to
 *   [KalendarDatePickerDefaults.DayCell]; apply [kalendarDaySemantics] to your own cell to keep it
 *   accessible.
 * @param actions Replaces the bottom action rail. Only composed when at least one of [onClear] and
 *   [onTodayClick] was supplied, so the space is given back to the grid when the picker has no
 *   actions. Defaults to [KalendarDatePickerDefaults.ActionRow].
 */
@Composable
@Suppress("LongParameterList")
public fun KalendarDatePicker(
    selectedDates: Set<LocalDate>,
    onDateClick: (date: LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    state: KalendarViewState = rememberKalendarMonthState(),
    selectionMode: KalendarSelectionMode = KalendarSelectionMode.Single,
    config: KalendarViewConfig = KalendarViewConfig(),
    yearRange: IntRange? = null,
    onClear: (() -> Unit)? = null,
    onTodayClick: ((date: LocalDate) -> Unit)? = null,
    header: @Composable (KalendarHeaderScope) -> Unit = { headerScope ->
        KalendarDatePickerDefaults.Header(
            scope = headerScope,
            yearRange = yearRange ?: datePickerYearRange(
                anchorYear = headerScope.visibleDate.year,
                minDate = state.minDate,
                maxDate = state.maxDate,
            ),
            showNavigationArrows = config.showNavigationArrows,
            monthNameFormatter = config.monthNameFormatter,
            shortMonthNameFormatter = config.shortMonthNameFormatter,
        )
    },
    dayOfWeekLabel: @Composable (DayOfWeek) -> Unit = { dayOfWeek ->
        KalendarHeaderDefaults.DayOfWeekLabel(
            dayOfWeek = dayOfWeek,
            formatter = config.dayOfWeekLabelFormatter,
        )
    },
    dayContent: @Composable (scope: KalendarDatePickerDayScope) -> Unit = { dayScope ->
        KalendarDatePickerDefaults.DayCell(
            scope = dayScope,
            onClick = { onDateClick(dayScope.day.date) },
            monthNameFormatter = config.monthNameFormatter,
        )
    },
    actions: @Composable (scope: KalendarDatePickerActionScope) -> Unit = { actionScope ->
        KalendarDatePickerDefaults.ActionRow(scope = actionScope)
    },
) {
    val colors = kalendarColors()
    val today = rememberKalendarToday(state.timeSource)
    val coroutineScope = rememberCoroutineScope()
    val keyboard = rememberKalendarGridKeyboardState()

    val minDate = state.minDate
    val maxDate = state.maxDate
    val isDateSelectable: (LocalDate) -> Boolean = { date ->
        !config.disabledDates(date) && date.isWithinBounds(minDate = minDate, maxDate = maxDate)
    }

    // The endpoints rather than the members, so a caller who reports only the two tapped dates still
    // gets the span between them filled — the picker's job is to show what the range *means*.
    val rangeBounds = remember(selectedDates, selectionMode) {
        if (selectionMode == KalendarSelectionMode.Range && selectedDates.isNotEmpty()) {
            selectedDates.min() to selectedDates.max()
        } else {
            null
        }
    }
    val slidingSelection = if (config.showSelectionIndicator && rangeBounds == null) {
        selectedDates.singleOrNull()
    } else {
        null
    }

    val visibleMonthStart = state.visibleDate.startOfMonth()
    val periodDates = remember(visibleMonthStart, config.visibleDaysOfWeek) {
        monthDates(monthStart = visibleMonthStart, visibleDaysOfWeek = config.visibleDaysOfWeek)
    }

    Column(modifier = modifier.background(config.background ?: SolidColor(colors.background))) {
        val title = remember(state.visibleDate, config.monthNameFormatter) {
            monthTitle(state.visibleDate, config.monthNameFormatter)
        }
        header(rememberKalendarHeaderScope(state = state, title = title))

        Box(
            modifier = Modifier.kalendarGridKeyboard(
                keyboard = keyboard,
                periodDates = periodDates,
                today = today,
                selectedDate = selectedDates.singleOrNull(),
                onSelect = onDateClick,
                onScrollTo = { date -> coroutineScope.launch { state.animateScrollTo(date) } },
                pageStep = { date, pages -> date.plus(pages, DateTimeUnit.MONTH) },
                isDateEnabled = isDateSelectable,
                // A picker's range is committed by two taps rather than by a drag, so there is no
                // drag for `Shift` to mirror: two `Enter` presses are the keyboard path, and wiring a
                // second, overlapping range mechanism here would fight the first.
                onRangeDrag = null,
                onRangeDragEnd = null,
                monthNameFormatter = config.monthNameFormatter,
                visibleDaysOfWeek = config.visibleDaysOfWeek,
            )
        ) {
            HorizontalPager(state = state.pagerState, modifier = Modifier.fillMaxWidth()) { page ->
                val monthStart = state.pageToDate(page)
                val dates = remember(monthStart, state.startDayOfWeek, config.visibleDaysOfWeek) {
                    monthGridDates(
                        monthStart = monthStart,
                        startDayOfWeek = state.startDayOfWeek,
                        visibleDaysOfWeek = config.visibleDaysOfWeek,
                    )
                }
                KalendarGrid(
                    dates = dates,
                    startDayOfWeek = state.startDayOfWeek,
                    selectedDate = slidingSelection,
                    keyboard = keyboard,
                    visibleDaysOfWeek = config.visibleDaysOfWeek,
                    dayOfWeekLabel = dayOfWeekLabel,
                ) { date ->
                    val isAdjacentMonth = date.month != monthStart.month
                    if (isAdjacentMonth && !config.showAdjacentMonthDates) {
                        Box(modifier = Modifier.aspectRatio(kalendarDimensions().dayCellAspectRatio))
                    } else {
                        dayContent(
                            rememberDatePickerDayScope(
                                date = date,
                                today = today,
                                selectedDates = selectedDates,
                                rangeBounds = rangeBounds,
                                isAdjacentMonth = isAdjacentMonth,
                                isSelectable = isDateSelectable,
                            )
                        )
                    }
                }
            }
        }

        if (onClear != null || onTodayClick != null) {
            val actionScope = remember(
                today,
                selectedDates,
                onClear,
                onTodayClick,
                state,
            ) {
                KalendarDatePickerActionScopeImpl(
                    today = today,
                    isSelectionEmpty = selectedDates.isEmpty(),
                    onClear = onClear,
                    onTodayClick = onTodayClick,
                    isDateSelectable = isDateSelectable,
                    state = state,
                    coroutineScope = coroutineScope,
                )
            }
            actions(actionScope)
        }
    }
}

/**
 * Builds one cell's [KalendarDatePickerDayScope], keyed so a cell that has not changed keeps the same
 * instance and can skip recomposition — 42 fresh scope objects a frame is exactly what the
 * recomposition benchmark exists to catch.
 */
@Composable
private fun rememberDatePickerDayScope(
    date: LocalDate,
    today: LocalDate,
    selectedDates: Set<LocalDate>,
    rangeBounds: Pair<LocalDate, LocalDate>?,
    isAdjacentMonth: Boolean,
    isSelectable: (LocalDate) -> Boolean,
): KalendarDatePickerDayScope {
    val isDisabled = !isSelectable(date)
    return remember(date, today, selectedDates, rangeBounds, isAdjacentMonth, isDisabled) {
        val isInRange = rangeBounds != null && date >= rangeBounds.first && date <= rangeBounds.second
        KalendarDatePickerDayScope(
            day = KalendarDayScope(
                date = date,
                isSelected = date in selectedDates || isInRange,
                isToday = date == today,
                isDisabled = isDisabled,
                events = emptyList(),
            ),
            isInRange = isInRange,
            isRangeStart = rangeBounds != null && date == rangeBounds.first,
            isRangeEnd = rangeBounds != null && date == rangeBounds.second,
            isAdjacentMonth = isAdjacentMonth,
        )
    }
}

/** Whether this date lies inside the closed interval [minDate]..[maxDate]; an absent bound is open. */
private fun LocalDate.isWithinBounds(minDate: LocalDate?, maxDate: LocalDate?): Boolean =
    (minDate == null || this >= minDate) && (maxDate == null || this <= maxDate)

/**
 * Everything [KalendarDatePicker] draws, exposed so a slot can reuse one piece instead of rebuilding
 * the whole picker.
 *
 * These are the exact composables the picker falls back to when the matching slot is left at its
 * default, so calling one with different arguments changes only what you asked to change:
 *
 * ```kotlin
 * KalendarDatePicker(
 *     selectedDates = selection.selectedDates,
 *     onDateClick = selection::onDateClick,
 *     // Keep the built-in header, but without the year dropdown.
 *     header = { scope -> KalendarDatePickerDefaults.Header(scope = scope, showYearPicker = false) },
 *     // Keep the built-in actions, but relabelled.
 *     onClear = selection::clear,
 *     actions = { scope -> KalendarDatePickerDefaults.ActionRow(scope = scope, clearLabel = "Reset") },
 * )
 * ```
 *
 * Every colour, text style, shape, dimension and motion spec below resolves through the ambient
 * [KalendarTheme], so a picker restyles with the rest of the library rather than separately from it.
 */
public object KalendarDatePickerDefaults {

    /**
     * The width a [KalendarDatePicker] is designed around — seven ~44.dp day columns plus the
     * header's breathing room, which is about the widest a popup anchored under a text field can get
     * before it stops reading as an attachment to that field.
     *
     * The picker imposes no width of its own: it fills whatever it is given, so a caller who wants
     * the intended proportions asks for them (`Modifier.width(KalendarDatePickerDefaults.Width)`) and
     * a caller embedding it in a wider sheet is not fought. It is public precisely so the recommended
     * size is quotable rather than guessed at.
     */
    public val Width: Dp = 312.dp

    /**
     * The built-in date-picker header: a month dropdown and a year dropdown at the leading edge, and
     * the previous/next page arrows grouped at the trailing edge.
     *
     * This is deliberately a different shape from [KalendarHeaderDefaults.Header], which centres a
     * combined "August 2026" title and opens one popup for both fields. A picker anchored under a
     * form field is navigated by *jumping* — a birthdate is 400 months away, not four — so month and
     * year are separate controls that each open straight onto their own list, and the arrows are
     * demoted to the corner where they serve the "one month either way" case they are good at.
     *
     * Both dropdowns drive [scope] rather than any particular state type, so this also works in the
     * `header` slot of [KalendarMonth] if you prefer these controls to that view's centred title.
     *
     * @param scope Navigation state and callbacks for the picker this header belongs to. Inside a
     *   `header` slot this is the value the slot was handed.
     * @param modifier [Modifier] applied to the header's outermost container.
     * @param yearRange The years the year dropdown offers. Defaults to a century either side of the
     *   visible year, which is what makes a birthdate reachable by scrolling rather than by stepping;
     *   [KalendarDatePicker] narrows it to the calendar's own `minDate`/`maxDate` when those are set,
     *   so a bounded picker never offers a year it would refuse to navigate to.
     * @param showNavigationArrows Whether to draw the previous/next arrows. Each is disabled when the
     *   matching [KalendarHeaderScope.canScrollBackward]/[KalendarHeaderScope.canScrollForward] is
     *   `false`, which is how a `maxDate` in the visible month stops the header dead rather than
     *   letting it page into a month with nothing selectable in it.
     * @param showMonthPicker Whether tapping the month control opens the month grid. With this
     *   `false` the month is still shown — it is the header's primary label — but is inert.
     * @param showYearPicker Whether tapping the year control opens the year grid. See
     *   [showMonthPicker].
     * @param monthNameFormatter Formats the month name a screen reader announces for one month-grid
     *   button, and the announcement of the month control itself. See
     *   [KalendarViewConfig.monthNameFormatter].
     * @param shortMonthNameFormatter Formats the month name the header and the month grid *show*.
     *   The control is compact and the grid's buttons are a fixed width, so a name that still does
     *   not fit is ellipsized rather than widening either. See
     *   [KalendarViewConfig.shortMonthNameFormatter].
     * @param previousIcon The glyph inside the previous-page button. Defaults to a chevron drawn with
     *   Compose Foundation, mirrored in RTL. The button around it — its touch target, its click and
     *   its accessibility label — belongs to the header, so a replacement only draws.
     * @param nextIcon The glyph inside the next-page button. Defaults to [previousIcon]'s chevron
     *   pointing the other way.
     * @param dropdownIcon The caret drawn beside each dropdown's label. Defaults to the same chevron
     *   turned to point down. It is direction-neutral and so is never mirrored.
     */
    @Composable
    @Suppress("LongParameterList")
    public fun Header(
        scope: KalendarHeaderScope,
        modifier: Modifier = Modifier,
        yearRange: IntRange = datePickerYearRange(anchorYear = scope.visibleDate.year),
        showNavigationArrows: Boolean = true,
        showMonthPicker: Boolean = true,
        showYearPicker: Boolean = true,
        monthNameFormatter: (Month) -> String = defaultMonthNameFormatter,
        shortMonthNameFormatter: (Month) -> String = defaultShortMonthNameFormatter,
        previousIcon: @Composable () -> Unit = { PickerNavChevron(forward = false) },
        nextIcon: @Composable () -> Unit = { PickerNavChevron(forward = true) },
        dropdownIcon: @Composable () -> Unit = { PickerDropdownCaret() },
    ) {
        val dimensions = kalendarDimensions()
        val strings = kalendarStrings()
        var monthPickerOpen by remember { mutableStateOf(false) }
        var yearPickerOpen by remember { mutableStateOf(false) }
        val visible = scope.visibleDate

        Row(
            modifier = modifier.fillMaxWidth().padding(dimensions.headerPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DropdownControl(
                label = shortMonthNameFormatter(visible.month),
                accessibilityLabel = monthNameFormatter(visible.month),
                enabled = showMonthPicker,
                onClick = { monthPickerOpen = true },
                caret = dropdownIcon,
            ) {
                if (monthPickerOpen) {
                    Popup(
                        alignment = Alignment.TopStart,
                        onDismissRequest = { monthPickerOpen = false },
                        properties = PopupProperties(focusable = true),
                    ) {
                        MonthGrid(
                            monthNameFormatter = monthNameFormatter,
                            shortMonthNameFormatter = shortMonthNameFormatter,
                            selected = visible.month,
                            onMonthSelected = { month ->
                                monthPickerOpen = false
                                scope.goTo(LocalDate(visible.year, month, 1))
                            },
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(HEADER_CONTROL_GAP))

            DropdownControl(
                label = visible.year.toString(),
                accessibilityLabel = visible.year.toString(),
                enabled = showYearPicker,
                onClick = { yearPickerOpen = true },
                caret = dropdownIcon,
            ) {
                if (yearPickerOpen) {
                    Popup(
                        alignment = Alignment.TopStart,
                        onDismissRequest = { yearPickerOpen = false },
                        properties = PopupProperties(focusable = true),
                    ) {
                        YearGrid(
                            yearRange = yearRange,
                            selected = visible.year,
                            onYearSelected = { year ->
                                yearPickerOpen = false
                                scope.goTo(LocalDate(year, visible.month, 1))
                            },
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            if (showNavigationArrows) {
                PickerIconButton(
                    label = strings.previousPage,
                    enabled = scope.canScrollBackward,
                    onClick = scope::goToPreviousPage,
                    content = previousIcon,
                )
                PickerIconButton(
                    label = strings.nextPage,
                    enabled = scope.canScrollForward,
                    onClick = scope::goToNextPage,
                    content = nextIcon,
                )
            }
        }
    }

    /**
     * The built-in picker day cell: the day number, a rule beneath it when the date is today, and a
     * [KalendarShapes.dayCell] fill when it is selected.
     *
     * The two states are drawn by different means on purpose. A filled circle means *selected* here —
     * it is the thing the user is choosing — so today cannot also be a circle without the picker
     * claiming to have a selection it does not have. Today is therefore a rule under the number in
     * [KalendarColors.todayContent], which composes: a cell that is both today and selected shows the
     * circle *and* the rule, the rule switching to [KalendarColors.onSelectionBackground] so it stays
     * legible on the fill. The rule's space is reserved on every cell, today or not, so gaining it
     * never nudges a number by a pixel.
     *
     * In [KalendarSelectionMode.Range] the span between the endpoints is filled edge to edge rather
     * than as one circle per day: the band ignores [KalendarDimensions.dayCellPadding] horizontally,
     * which is what makes it join up across cells into a single bar, and the endpoints add the
     * rounded cap on top. A range is a continuous thing and a row of separate circles does not say so.
     *
     * Accessibility comes from [kalendarDaySemantics], the same modifier the grid views' cells use, so
     * this cell announces its date, its selected state and — via the click it refuses to take — its
     * disabled state, in whatever [KalendarStrings] were provided.
     *
     * @param scope The cell's state, as handed to the `dayContent` slot: the date, whether it is
     *   selected/today/disabled, and where it falls in an in-progress range.
     * @param onClick Called when the cell is tapped and [KalendarDayScope.isDisabled] is `false`.
     * @param modifier [Modifier] applied to the cell.
     * @param showSelectionBackground Whether this cell draws its own selection fill. The picker sets
     *   it to `false` for the one date a sliding indicator is already animating behind, so the two do
     *   not both paint the same circle; the text colour and weight still follow the selected state.
     * @param showTodayMarker Whether today's rule is drawn. Set it to `false` when the surrounding
     *   form already says what today is and a second marker would just compete with the selection.
     * @param monthNameFormatter Formats the month name inside this cell's accessibility description
     *   (e.g. "August 12, 2026"). See [KalendarViewConfig.monthNameFormatter].
     */
    @Composable
    public fun DayCell(
        scope: KalendarDatePickerDayScope,
        onClick: () -> Unit,
        modifier: Modifier = Modifier,
        showSelectionBackground: Boolean = true,
        showTodayMarker: Boolean = true,
        monthNameFormatter: (Month) -> String = defaultMonthNameFormatter,
    ) {
        val colors = kalendarColors()
        val typography = kalendarTypography()
        val shapes = kalendarShapes()
        val dimensions = kalendarDimensions()
        val animations = kalendarAnimations()
        val day = scope.day

        val interactionSource = remember { MutableInteractionSource() }
        val isPressed by interactionSource.collectIsPressedAsState()
        val isHovered by interactionSource.collectIsHoveredAsState()
        val pressScale = if (animations.enabled && isPressed) animations.pressScale else 1f

        val isCapped = scope.isRangeStart || scope.isRangeEnd || (day.isSelected && !scope.isInRange)
        val drawsCap = isCapped && showSelectionBackground
        // Follows the *state*, not what this cell happens to paint: with a sliding indicator the fill
        // behind a selected cell is drawn by the grid, and the number still has to be legible on it.
        val onFill = day.isSelected || scope.isInRange
        val contentColor = when {
            onFill -> colors.onSelectionBackground
            day.isToday -> colors.todayContent
            else -> colors.dayContent
        }
        val contentAlpha = when {
            day.isDisabled -> colors.disabledContentAlpha
            scope.isAdjacentMonth -> colors.disabledContentAlpha
            else -> 1f
        }

        Box(
            contentAlignment = Alignment.Center,
            modifier = modifier
                .aspectRatio(dimensions.dayCellAspectRatio)
                .scale(pressScale)
                .alpha(contentAlpha)
                .kalendarDaySemantics(
                    scope = day,
                    onClick = onClick,
                    monthNameFormatter = monthNameFormatter,
                    interactionSource = interactionSource,
                ),
        ) {
            if (scope.isInRange) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .padding(vertical = dimensions.dayCellPadding)
                        .background(colors.selectionBackground),
                )
            }
            if (drawsCap) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .padding(dimensions.dayCellPadding)
                        .clip(shapes.dayCell)
                        .background(colors.selectionBackground),
                )
            }
            if (isHovered) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .padding(dimensions.dayCellPadding)
                        .clip(shapes.dayCell)
                        .background(colors.hoverBackground),
                )
            }

            Column(
                modifier = Modifier.width(IntrinsicSize.Max),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                BasicText(
                    text = day.date.dayOfMonth.toString(),
                    style = pickerDayTextStyle(
                        typography = typography,
                        emphasized = day.isSelected || day.isToday,
                    ).copy(color = contentColor, textAlign = TextAlign.Center),
                )
                Spacer(modifier = Modifier.height(TODAY_UNDERLINE_GAP))
                // Always laid out, transparent when it does not apply: a marker that appears and
                // disappears would otherwise move every other number in the row by its own height.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(dimensions.nowIndicatorThickness)
                        .background(
                            if (day.isToday && showTodayMarker) {
                                contentColor
                            } else {
                                Color.Transparent
                            }
                        ),
                )
            }
        }
    }

    /**
     * The built-in action row: the text actions along the bottom of the picker, trailing-aligned so
     * they read as the commit rail of the popup rather than as part of the grid.
     *
     * Each action appears only when the picker was given a handler for it — [KalendarDatePicker]'s
     * `onClear` and `onTodayClick` — so a picker never shows a control that would do nothing, and
     * hiding one has no effect on the other. When neither is present the picker omits this row
     * entirely and gives the space back to the grid.
     *
     * @param scope The row's callbacks and the state they depend on. Inside an `actions` slot this is
     *   the value the slot was handed.
     * @param modifier [Modifier] applied to the row.
     * @param clearLabel Text and accessibility label for the action that empties the selection.
     *   Defaults to plain English, because [KalendarStrings] has no entry for it yet — see
     *   [FALLBACK_CLEAR_LABEL] — so this is the parameter to pass a resolved string to.
     * @param todayLabel Text and accessibility label for the action that jumps to, and selects,
     *   today. Defaults to [KalendarStrings.today], the same string the grid views' today button
     *   announces, so localizing the theme localizes this too.
     */
    @Composable
    public fun ActionRow(
        scope: KalendarDatePickerActionScope,
        modifier: Modifier = Modifier,
        clearLabel: String = FALLBACK_CLEAR_LABEL,
        todayLabel: String = kalendarStrings().today,
    ) {
        Row(
            modifier = modifier.fillMaxWidth().padding(ACTION_ROW_PADDING),
            horizontalArrangement = Arrangement.spacedBy(ACTION_GAP, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (scope.showClearAction) {
                TextAction(label = clearLabel, onClick = scope::clear)
            }
            if (scope.showTodayAction) {
                TextAction(label = todayLabel, onClick = scope::selectToday)
            }
        }
    }
}

/**
 * The years a year dropdown offers when nothing narrows them: a century either side of [anchorYear],
 * clamped to whichever of [minDate]/[maxDate] were supplied.
 *
 * A century is not arbitrary. The one requirement a date picker's year control has that a calendar's
 * has none of is that a birthdate must be reachable, and reaching 1950 from 2026 by stepping is 76
 * taps — so the range is wide enough to scroll to instead, and the control that presents it is a grid
 * rather than a stepper.
 */
internal fun datePickerYearRange(
    anchorYear: Int,
    minDate: LocalDate? = null,
    maxDate: LocalDate? = null,
): IntRange {
    val first = minDate?.year ?: (anchorYear - DEFAULT_YEAR_SPAN)
    val last = maxDate?.year ?: (anchorYear + DEFAULT_YEAR_SPAN)
    return first..maxOf(first, last)
}

/** How far either side of the visible year [datePickerYearRange] reaches when unbounded. */
private const val DEFAULT_YEAR_SPAN = 100

/** The day number's style, emphasized when the cell is selected or is today. */
private fun pickerDayTextStyle(
    typography: KalendarTypography,
    emphasized: Boolean,
): TextStyle = if (emphasized) {
    typography.dayNumberEmphasized
} else {
    typography.dayNumber
}

/**
 * One header dropdown: its label, its caret, the click that opens its popup, and the popup itself as
 * [popup], anchored to this control rather than to the header.
 *
 * The label and caret share a single click target — splitting them would leave a caret that looks
 * pressable and is not — and the whole control announces itself as a button carrying
 * [KalendarStrings.openJumpPicker], which is the nearest thing this library's strings have to "opens
 * a picker for this field".
 */
@Composable
private fun DropdownControl(
    label: String,
    accessibilityLabel: String,
    enabled: Boolean,
    onClick: () -> Unit,
    caret: @Composable () -> Unit,
    popup: @Composable () -> Unit,
) {
    val colors = kalendarColors()
    val typography = kalendarTypography()
    val strings = kalendarStrings()
    Box {
        Row(
            modifier = Modifier
                .sizeIn(minHeight = MIN_TOUCH_TARGET)
                .semantics {
                    contentDescription = "$accessibilityLabel, ${strings.openJumpPicker}"
                    if (!enabled) {
                        disabled()
                    }
                }
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                .padding(HEADER_CONTROL_PADDING),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(HEADER_CARET_GAP),
        ) {
            BasicText(
                text = label,
                style = typography.headerTitle.copy(color = colors.headerContent),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (enabled) {
                CompositionLocalProvider(LocalPickerIconTint provides colors.headerContent, content = caret)
            }
        }
        popup()
    }
}

/**
 * The month dropdown's popup: a 4x3 grid of abbreviated month names, the visible month marked with
 * the same [KalendarShapes.dayCell] fill a selected date gets so the two selections read alike.
 */
@Composable
private fun MonthGrid(
    monthNameFormatter: (Month) -> String,
    shortMonthNameFormatter: (Month) -> String,
    selected: Month,
    onMonthSelected: (Month) -> Unit,
) {
    PickerPopupSurface {
        Column(modifier = Modifier.padding(PICKER_POPUP_PADDING)) {
            Month.entries.chunked(MONTH_PICKER_COLUMNS).forEach { rowMonths ->
                Row {
                    rowMonths.forEach { month ->
                        PickerPopupCell(
                            label = shortMonthNameFormatter(month),
                            accessibilityLabel = monthNameFormatter(month),
                            isSelected = month == selected,
                            width = MONTH_PICKER_CELL_WIDTH,
                            height = MONTH_PICKER_CELL_HEIGHT,
                            onClick = { onMonthSelected(month) },
                        )
                    }
                }
            }
        }
    }
}

/**
 * The year dropdown's popup: a scrolling three-column grid over [yearRange], opened with [selected]
 * near the top.
 *
 * A grid rather than a stepper, and scrolling rather than paged, because the range it has to cover is
 * a lifetime: 1950 is two flings away here and 76 taps away from a stepper. The list is lazy so the
 * two centuries it can hold cost nothing until they are scrolled to.
 */
@Composable
private fun YearGrid(
    yearRange: IntRange,
    selected: Int,
    onYearSelected: (Int) -> Unit,
) {
    val years = remember(yearRange) { yearRange.toList() }
    val initialIndex = remember(years, selected) {
        val selectedIndex = years.indexOf(selected).coerceAtLeast(0)
        (selectedIndex - YEAR_PICKER_LEAD_ROWS * YEAR_PICKER_COLUMNS).coerceAtLeast(0)
    }
    val gridState = rememberLazyGridState(initialFirstVisibleItemIndex = initialIndex)
    PickerPopupSurface {
        LazyVerticalGrid(
            columns = GridCells.Fixed(YEAR_PICKER_COLUMNS),
            state = gridState,
            modifier = Modifier
                .width(YEAR_PICKER_CELL_WIDTH * YEAR_PICKER_COLUMNS + PICKER_POPUP_PADDING * 2)
                .heightIn(max = PICKER_POPUP_MAX_HEIGHT)
                .padding(PICKER_POPUP_PADDING),
        ) {
            items(items = years, key = { it }) { year ->
                PickerPopupCell(
                    label = year.toString(),
                    accessibilityLabel = year.toString(),
                    isSelected = year == selected,
                    width = YEAR_PICKER_CELL_WIDTH,
                    height = YEAR_PICKER_CELL_HEIGHT,
                    onClick = { onYearSelected(year) },
                )
            }
        }
    }
}

/**
 * The opaque, outlined surface both dropdown popups float on. Opaque because a popup over a grid of
 * numbers is unreadable otherwise, and outlined because [KalendarColors.popupBackground] is only a
 * shade away from [KalendarColors.background] in most palettes.
 */
@Composable
private fun PickerPopupSurface(content: @Composable () -> Unit) {
    val colors = kalendarColors()
    val shapes = kalendarShapes()
    Box(
        modifier = Modifier
            .clip(shapes.popup)
            .background(colors.popupBackground)
            .border(
                width = kalendarDimensions().gridLineThickness,
                color = colors.popupBorder,
                shape = shapes.popup,
            ),
        content = { content() },
    )
}

/** One tappable cell inside a dropdown popup — a month name or a year. */
@Composable
private fun PickerPopupCell(
    label: String,
    accessibilityLabel: String,
    isSelected: Boolean,
    width: Dp,
    height: Dp,
    onClick: () -> Unit,
) {
    val colors = kalendarColors()
    val typography = kalendarTypography()
    val shapes = kalendarShapes()
    Box(
        modifier = Modifier
            .width(width)
            .height(height)
            .padding(kalendarDimensions().dayCellPadding)
            .clip(shapes.dayCell)
            .background(if (isSelected) colors.selectionBackground else Color.Transparent)
            .semantics {
                contentDescription = accessibilityLabel
                selected = isSelected
            }
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = label,
            style = typography.monthLabel.copy(
                color = if (isSelected) colors.onSelectionBackground else colors.headerContent,
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * One text action in the picker's bottom rail: the label in [KalendarColors.todayContent], the
 * library's accent, inside a [MIN_TOUCH_TARGET]-tall target that carries the click and the label.
 */
@Composable
private fun TextAction(
    label: String,
    onClick: () -> Unit,
) {
    val colors = kalendarColors()
    val typography = kalendarTypography()
    Box(
        modifier = Modifier
            .sizeIn(minHeight = MIN_TOUCH_TARGET)
            .clip(kalendarShapes().dayCell)
            .semantics { contentDescription = label }
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = ACTION_LABEL_PADDING),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = label,
            style = typography.monthLabel.copy(color = colors.todayContent),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * One header arrow: a [MIN_TOUCH_TARGET] square carrying the click, the [Role.Button] role and the
 * accessibility label, with [content] centred in it.
 *
 * The label lives here rather than on the glyph so a caller-supplied icon slot stays accessible
 * without knowing anything about semantics.
 */
@Composable
private fun PickerIconButton(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    val colors = kalendarColors()
    Box(
        modifier = Modifier
            .sizeIn(minWidth = MIN_TOUCH_TARGET, minHeight = MIN_TOUCH_TARGET)
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
        CompositionLocalProvider(LocalPickerIconTint provides tint, content = content)
    }
}

/**
 * The colour the picker's built-in glyphs draw themselves in.
 *
 * It travels as a local rather than a parameter because the glyphs arrive through the `previousIcon`
 * /`nextIcon`/`dropdownIcon` slots, which the button around them cannot reach into. `null` means "not
 * inside a picker control", which is how a glyph rendered on its own resolves.
 *
 * This mirrors — rather than shares — the local [KalendarHeaderDefaults] uses for the same job: that
 * one is `private` to its own file, and copying a private helper across files is how two drifting
 * implementations get created. The two are candidates to be merged into one internal helper.
 */
private val LocalPickerIconTint = staticCompositionLocalOf<Color?> { null }

/**
 * The picker's navigation chevron, drawn as two strokes so the library needs no icon dependency.
 *
 * [forward] is a reading-order direction, not a screen direction: a forward chevron points right in
 * LTR and left in RTL, which is why the glyph is drawn pointing left and mirrored from
 * [LocalLayoutDirection] rather than baked into a fixed path.
 */
@Composable
private fun PickerNavChevron(forward: Boolean) {
    val tint = LocalPickerIconTint.current ?: kalendarColors().headerContent
    val pointsRight = forward == (LocalLayoutDirection.current == LayoutDirection.Ltr)
    Canvas(
        modifier = Modifier
            .size(ICON_SIZE)
            .scale(scaleX = if (pointsRight) -1f else 1f, scaleY = 1f),
    ) {
        drawChevron(tint)
    }
}

/**
 * The caret beside a dropdown's label: the same chevron turned to point down, which is the
 * conventional "this opens a list" affordance. Direction-neutral, so it is never mirrored.
 */
@Composable
private fun PickerDropdownCaret() {
    val tint = LocalPickerIconTint.current ?: kalendarColors().headerContent
    Canvas(modifier = Modifier.size(CARET_SIZE).rotate(CARET_ROTATION_DEGREES)) {
        drawChevron(tint)
    }
}

/** Draws a left-pointing chevron filling the draw scope, in [tint]. */
private fun DrawScope.drawChevron(tint: Color) {
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
