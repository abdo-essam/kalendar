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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import com.himanshoe.kalendar.component.KalendarGrid
import com.himanshoe.kalendar.component.KalendarHeaderDefaults
import com.himanshoe.kalendar.component.KalendarMonthGridCell
import com.himanshoe.kalendar.component.kalendarGridKeyboard
import com.himanshoe.kalendar.component.rememberKalendarGridKeyboardState
import com.himanshoe.kalendar.component.rememberKalendarHeaderScope
import com.himanshoe.kalendar.foundation.datetime.datesUntil
import com.himanshoe.kalendar.foundation.datetime.startOfMonth
import com.himanshoe.kalendar.foundation.event.KalendarEvent
import com.himanshoe.kalendar.foundation.event.byDateExpandingSpans
import com.himanshoe.kalendar.foundation.format.monthTitle
import com.himanshoe.kalendar.foundation.grid.monthDates
import com.himanshoe.kalendar.foundation.grid.monthGridDates
import com.himanshoe.kalendar.slot.KalendarDayScope
import com.himanshoe.kalendar.slot.KalendarHeaderScope
import com.himanshoe.kalendar.state.KalendarEventLoader
import com.himanshoe.kalendar.state.KalendarLoadStatus
import com.himanshoe.kalendar.state.KalendarViewState
import com.himanshoe.kalendar.state.rememberKalendarEventLoader
import com.himanshoe.kalendar.state.rememberKalendarMonthState
import com.himanshoe.kalendar.state.rememberKalendarToday
import com.himanshoe.kalendar.theme.kalendarColors
import com.himanshoe.kalendar.theme.kalendarDimensions
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

/**
 * A month-grid calendar navigable by both arrow-click and swipe.
 *
 * A single [KalendarViewState] backs both navigation paths — the arrow buttons in the header and
 * a horizontal swipe on the grid move the same underlying pager, so they always stay in sync.
 *
 *
 * The grid is keyboard-navigable and is a **single tab stop**: `Tab` enters it at the selected date
 * (or today), arrows move a focus ring one day or one row — paging to the next or previous month
 * when they run off the edge — `Home`/`End` jump to the first and last day of the visible month,
 * `PageUp`/`PageDown` move a whole month, `Enter`/`Space` select, and `Tab` again leaves. Holding
 * `Shift` while arrowing extends a range through `onDateRangeSelect`/`onDateRangeSelectEnd`, which
 * is otherwise reachable by pointer only. A date [KalendarViewConfig.disabledDates] rejects can be
 * focused but not selected, so it is announced rather than silently skipped.
 *
 * ```kotlin
 * val state = rememberKalendarMonthState(initialDate = today)
 * var selected by remember { mutableStateOf(setOf(today)) }
 *
 * KalendarMonth(
 *     selectedDate = today,
 *     state = state,
 *     selectedDates = selected,
 *     events = bookings,
 *     onDateClick = { date, dayBookings: List<Booking> -> selected = setOf(date) },
 * )
 * ```
 *
 * The calendar is generic in [E], the caller's own event type, inferred from [events]. Every callback
 * and slot hands that type straight back — `onDateClick` above receives `List<Booking>`, not
 * `List<KalendarEvent>`, so nothing has to be cast to be used. Use the event-free overload when
 * there are no events to infer from.
 *
 * @param E The event type this calendar holds. Any [KalendarEvent] implementation, including a
 *   caller's own domain type.
 * @param selectedDate The date used as the default single-selection highlight and, when [state]
 *   is left at its default, the month initially visible. Superseded by [selectedDates] when that
 *   is supplied explicitly.
 * @param modifier [Modifier] applied to the outermost container.
 * @param state Navigation state from [rememberKalendarMonthState]. Own it yourself to read
 *   [KalendarViewState.visibleDate] (e.g. for a screen title) or call
 *   [KalendarViewState.animateScrollTo] from a button.
 * @param eventLoader Loads events one visible month at a time instead of taking them all up front —
 *   see [rememberKalendarEventLoader], and give it the same [state] this calendar has. When set,
 *   [events] defaults to everything it currently holds and each month draws [loadingContent] until
 *   its own range has arrived. `null`, the default, is the eager list: [events] is all there is and
 *   nothing is ever pending. Pass one or the other for the calendar's lifetime — swapping between
 *   them re-creates the pages and loses their scroll position.
 * @param events Events shown as indicator dots on day cells. Defaults to the events [eventLoader]
 *   holds, or to none. Pass it explicitly alongside a loader only to transform what the loader
 *   produced — filtering it, say; the loader still supplies the load status.
 * @param selectedDates The set of highlighted dates. Defaults to a single-element set containing
 *   [selectedDate]; pass your own hoisted set for multi-select or range selection (see
 *   [datesUntil] for a range-selection helper). Hoist/`remember` the same instance across
 *   recompositions rather than building a fresh [Set] every time.
 * @param onDateClick Called when a non-disabled date is tapped, with the events on that date.
 * @param onDayEventClick Called when a row of a day cell's `+N` overflow popover is tapped, with
 *   the event that row represents; the popover closes itself afterwards. Defaults to `null`, which
 *   leaves the popover a read-only list of what the cell could not show. Has no effect when
 *   [dayContent] replaces the built-in cell — a replacement cell owns its own popover, if any.
 * @param onDateRangeSelect When non-null, press-and-hold on a date then drag to select a range —
 *   called continuously with (the date under the initial press, the date under the pointer now)
 *   as the drag moves. Pair with [onDateRangeSelectEnd] and, for the common case, wire both to
 *   [KalendarSelectionState.onRangeDrag]/[KalendarSelectionState.onRangeDragEnd].
 * @param onDateRangeSelectEnd Called once when a drag started by [onDateRangeSelect] ends.
 * @param onEventDrop When non-null, press-and-hold on a date that has events then drag to another
 *   date to reschedule: a ghost highlight follows the cell under the pointer, and releasing calls
 *   this with the pressed date's events and the drop date — update your event source with the new
 *   date to complete the move. Dates without events fall through to [onDateRangeSelect]'s range
 *   behaviour, so both can be enabled together.
 * @param config Shared visual/behavioural settings — disabled dates, arrow visibility, background,
 *   and [KalendarViewConfig.visibleDaysOfWeek], which drops a weekday's column from the grid, its
 *   header row and the keyboard's path through it.
 * @param header Replaces the navigation header above the grid. Receives a [KalendarHeaderScope]
 *   carrying the formatted title and the navigation callbacks, so your own chrome keeps working
 *   arrows and today button. Defaults to [KalendarHeaderDefaults.Header] driven by [config].
 * @param dayOfWeekLabel Replaces the content of each weekday column header. The grid still places
 *   one per column, so a replacement stays aligned with the day cells. Defaults to
 *   [KalendarHeaderDefaults.DayOfWeekLabel] formatted by [config].
 * @param loadingContent Slot drawn over a month whose events have not arrived yet, given a
 *   [KalendarLoadingScope] with the range, its [KalendarLoadStatus] and the failure if it failed.
 *   Overlaid at the grid's own size, so it cannot move the calendar under it. Defaults to
 *   [KalendarLoadingDefaults.Placeholder]. Never drawn when [eventLoader] is `null`.
 * @param dayContent Replaces the default day-cell rendering. Receives a [KalendarDayScope]
 *   describing the date being rendered.
 */
@Composable
public fun <E : KalendarEvent> KalendarMonth(
    selectedDate: LocalDate,
    modifier: Modifier = Modifier,
    state: KalendarViewState = rememberKalendarMonthState(selectedDate),
    eventLoader: KalendarEventLoader<E>? = null,
    events: List<E> = eventLoader?.events ?: emptyList(),
    selectedDates: Set<LocalDate> = setOf(selectedDate),
    onDateClick: (date: LocalDate, events: List<E>) -> Unit = { _, _ -> },
    onDayEventClick: ((event: E) -> Unit)? = null,
    onDateRangeSelect: ((start: LocalDate, current: LocalDate) -> Unit)? = null,
    onDateRangeSelectEnd: (() -> Unit)? = null,
    onEventDrop: ((events: List<E>, newDate: LocalDate) -> Unit)? = null,
    config: KalendarViewConfig = KalendarViewConfig(),
    header: @Composable (KalendarHeaderScope) -> Unit = { headerScope ->
        KalendarHeaderDefaults.Header(
            scope = headerScope,
            showNavigationArrows = config.showNavigationArrows,
            showTodayButton = config.showTodayButton,
            showJumpPicker = config.showJumpPicker,
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
    loadingContent: @Composable (KalendarLoadingScope) -> Unit = { loadingScope ->
        KalendarLoadingDefaults.Placeholder(scope = loadingScope)
    },
    dayContent: (@Composable (scope: KalendarDayScope<E>) -> Unit)? = null,
) {
    val colors = kalendarColors()
    val today = rememberKalendarToday(state.timeSource)
    val eventsByDate = remember(events) { events.byDateExpandingSpans() }
    val slidingSelection = if (config.showSelectionIndicator) selectedDates.singleOrNull() else null
    // Hoisted out of the cell lambda: `?:` on a non-capturing literal yields the same singleton
    // every recomposition, so the cell's `onEventClick` parameter stays equal and skippable.
    val dayEventClick = onDayEventClick ?: {}
    val gridEventDrop: ((LocalDate, LocalDate) -> Unit)? = if (onEventDrop == null) {
        null
    } else {
        { from, to -> onEventDrop(eventsByDate[from].orEmpty(), to) }
    }

    val keyboard = rememberKalendarGridKeyboardState()
    val coroutineScope = rememberCoroutineScope()
    val visibleMonthStart = state.visibleDate.startOfMonth()
    val periodDates = remember(visibleMonthStart, config.visibleDaysOfWeek) {
        monthDates(monthStart = visibleMonthStart, visibleDaysOfWeek = config.visibleDaysOfWeek)
    }

    Column(
        modifier = modifier.background(config.background ?: SolidColor(colors.background)),
        verticalArrangement = Arrangement.spacedBy(kalendarDimensions().headerContentSpacing),
    ) {
        val title = remember(state.visibleDate) { monthTitle(state.visibleDate, config.monthNameFormatter) }
        header(rememberKalendarHeaderScope(state = state, title = title))
        Box(
            modifier = Modifier.kalendarGridKeyboard(
                keyboard = keyboard,
                periodDates = periodDates,
                today = today,
                selectedDate = selectedDates.singleOrNull(),
                onSelect = { date -> onDateClick(date, eventsByDate[date] ?: emptyList()) },
                onScrollTo = { date -> coroutineScope.launch { state.animateScrollTo(date) } },
                pageStep = { date, pages -> date.plus(pages, DateTimeUnit.MONTH) },
                isDateEnabled = { date -> !config.disabledDates(date) },
                onRangeDrag = onDateRangeSelect,
                onRangeDragEnd = onDateRangeSelectEnd,
                monthNameFormatter = config.monthNameFormatter,
                visibleDaysOfWeek = config.visibleDaysOfWeek,
            )
        ) {
            HorizontalPager(
                state = state.pagerState,
                modifier = Modifier.fillMaxWidth(),
            ) { page ->
                val monthStart = state.pager.dateAt(page)
                val dates = remember(monthStart, state.startDayOfWeek, config.visibleDaysOfWeek) {
                    monthGridDates(
                        monthStart = monthStart,
                        startDayOfWeek = state.startDayOfWeek,
                        visibleDaysOfWeek = config.visibleDaysOfWeek,
                    )
                }
                Box(propagateMinConstraints = true) {
                    KalendarGrid(
                        dates = dates,
                        startDayOfWeek = state.startDayOfWeek,
                        selectedDate = slidingSelection,
                        keyboard = keyboard,
                        onDateRangeDrag = onDateRangeSelect,
                        onDateRangeDragEnd = onDateRangeSelectEnd,
                        hasEvents = { date -> eventsByDate[date]?.isNotEmpty() == true },
                        onEventDrop = gridEventDrop,
                        events = events,
                        visibleDaysOfWeek = config.visibleDaysOfWeek,
                        dayOfWeekLabel = dayOfWeekLabel,
                    ) { date ->
                        val dateEvents = eventsByDate[date] ?: emptyList()
                        val isCurrentMonth = date.month == monthStart.month
                        val scope = KalendarDayScope(
                            date = date,
                            isSelected = date in selectedDates,
                            isToday = date == today,
                            isDisabled = config.disabledDates(date) || !isCurrentMonth,
                            events = dateEvents,
                        )
                        KalendarMonthGridCell(
                            scope = scope,
                            isCurrentMonth = isCurrentMonth,
                            showAdjacentMonthDates = config.showAdjacentMonthDates,
                            onClick = { onDateClick(date, dateEvents) },
                            dayContent = dayContent,
                            showSelectionBackground = date != slidingSelection,
                            monthNameFormatter = config.monthNameFormatter,
                            maxEventIndicators = config.eventIndicatorCap,
                            onEventClick = dayEventClick,
                        )
                    }
                    KalendarPageLoading(
                        loader = eventLoader,
                        date = monthStart,
                        content = loadingContent,
                    )
                }
            }
        }
    }
}

/**
 * A month grid with no events — the date-selection half of [KalendarMonth] alone.
 *
 * ```kotlin
 * KalendarMonth(selectedDate = today, onDateClick = { date, _ -> selected = setOf(date) })
 * ```
 *
 * This overload exists for inference, not for behaviour: it delegates straight to the generic
 * [KalendarMonth]. With no `events` to read the event type from, Kotlin has nothing to fix `E` to —
 * a default value cannot supply it, because a default argument is never a source of inference — so
 * the event-free calendar needs a signature that does not mention `E` at all. Omitting `events` is
 * also what distinguishes the two at a call site: supply events and the generic one is the only
 * candidate; supply none and this one is.
 *
 * `onDayEventClick` and `onEventDrop` are absent rather than defaulted, because a calendar with no
 * events can never fire them. [onDateClick] keeps its event list — always empty here — so that
 * `{ date, _ -> … }` reads the same on both overloads.
 *
 * @param selectedDate See [KalendarMonth].
 * @param modifier See [KalendarMonth].
 * @param state See [KalendarMonth].
 * @param selectedDates See [KalendarMonth].
 * @param onDateClick Called when a non-disabled date is tapped. The event list is always empty.
 * @param onDateRangeSelect See [KalendarMonth].
 * @param onDateRangeSelectEnd See [KalendarMonth].
 * @param config See [KalendarMonth].
 * @param header See [KalendarMonth].
 * @param dayOfWeekLabel See [KalendarMonth].
 * @param dayContent See [KalendarMonth].
 */
@Composable
public fun KalendarMonth(
    selectedDate: LocalDate,
    modifier: Modifier = Modifier,
    state: KalendarViewState = rememberKalendarMonthState(selectedDate),
    selectedDates: Set<LocalDate> = setOf(selectedDate),
    onDateClick: (date: LocalDate, events: List<KalendarEvent>) -> Unit = { _, _ -> },
    onDateRangeSelect: ((start: LocalDate, current: LocalDate) -> Unit)? = null,
    onDateRangeSelectEnd: (() -> Unit)? = null,
    config: KalendarViewConfig = KalendarViewConfig(),
    header: @Composable (KalendarHeaderScope) -> Unit = { headerScope ->
        KalendarHeaderDefaults.Header(
            scope = headerScope,
            showNavigationArrows = config.showNavigationArrows,
            showTodayButton = config.showTodayButton,
            showJumpPicker = config.showJumpPicker,
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
    dayContent: (@Composable (scope: KalendarDayScope<KalendarEvent>) -> Unit)? = null,
) {
    KalendarMonth<KalendarEvent>(
        selectedDate = selectedDate,
        modifier = modifier,
        state = state,
        selectedDates = selectedDates,
        onDateClick = onDateClick,
        onDateRangeSelect = onDateRangeSelect,
        onDateRangeSelectEnd = onDateRangeSelectEnd,
        config = config,
        header = header,
        dayOfWeekLabel = dayOfWeekLabel,
        dayContent = dayContent,
    )
}
