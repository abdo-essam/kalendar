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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEach
import com.himanshoe.kalendar.component.KalendarHeaderDefaults
import com.himanshoe.kalendar.component.KalendarKeyboardCells
import com.himanshoe.kalendar.component.KalendarKeyboardFocusRing
import com.himanshoe.kalendar.component.KalendarMonthGridCell
import com.himanshoe.kalendar.component.KalendarStaticGrid
import com.himanshoe.kalendar.component.KeepKeyboardFocusInView
import com.himanshoe.kalendar.component.kalendarGridKeyboard
import com.himanshoe.kalendar.component.kalendarKeyboardCell
import com.himanshoe.kalendar.component.rememberKalendarGridKeyboardState
import com.himanshoe.kalendar.component.rememberKalendarHeaderScope
import com.himanshoe.kalendar.foundation.datetime.datesUntil
import com.himanshoe.kalendar.foundation.event.KalendarEvent
import com.himanshoe.kalendar.foundation.event.byDateExpandingSpans
import com.himanshoe.kalendar.foundation.grid.monthDates
import com.himanshoe.kalendar.foundation.grid.monthGridDates
import com.himanshoe.kalendar.slot.KalendarDayScope
import com.himanshoe.kalendar.slot.KalendarHeaderScope
import com.himanshoe.kalendar.state.KalendarEventLoader
import com.himanshoe.kalendar.state.KalendarLoadStatus
import com.himanshoe.kalendar.state.KalendarViewState
import com.himanshoe.kalendar.state.rememberKalendarEventLoader
import com.himanshoe.kalendar.state.rememberKalendarToday
import com.himanshoe.kalendar.state.rememberKalendarYearState
import com.himanshoe.kalendar.theme.kalendarColors
import com.himanshoe.kalendar.theme.kalendarDimensions
import com.himanshoe.kalendar.theme.kalendarTypography
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.plus

/** Inset either side of a month grid on the year page, so twelve grids don't run edge to edge. */
private val YEAR_MONTH_GRID_PADDING = 8.dp

/**
 * Test tag on the year page's scrolling list of months.
 *
 * The list is lazy, so a month below the viewport has no node for a test to find, let alone scroll
 * to; a test reaches one by scrolling *this* node until the month it wants composes.
 */
internal const val YEAR_MONTH_LIST_TAG = "KalendarYearMonthList"

/**
 * The dates [year] actually contains, month by month, without the padding days each month grid draws
 * to square off its first and last rows.
 *
 * This is the *period* a keyboard user navigates in a year view, in the same sense
 * [com.himanshoe.kalendar.foundation.grid.monthDates] is one in a month view: `Home` belongs on
 * January 1st, `End` on December 31st, and a target outside it is a target on another year page.
 */
private fun yearDates(year: Int, visibleDaysOfWeek: Set<DayOfWeek>): List<LocalDate> = buildList {
    Month.entries.fastForEach { month ->
        addAll(monthDates(monthStart = LocalDate(year, month, 1), visibleDaysOfWeek = visibleDaysOfWeek))
    }
}

/**
 * A year-overview calendar (all 12 months) navigable by arrow-click, swipe and keyboard.
 *
 * A single [KalendarViewState] backs both pointer navigation paths — the arrow buttons in the header
 * and a horizontal swipe move the same underlying pager, so they always stay in sync. Each page shows
 * one year as a vertically scrollable column of month grids.
 *
 * The year is keyboard-navigable and is a **single tab stop**, with the key map [KalendarMonth]
 * already uses: `Tab` enters at the selected date (or today) and scrolls its month into view, arrows
 * move a focus ring, `Home`/`End` jump to January 1st and December 31st, `PageUp`/`PageDown` move a
 * whole year, `Enter`/`Space` select, and `Tab` again leaves. A date
 * [KalendarViewConfig.disabledDates] rejects can be focused but not selected, so it is announced
 * rather than silently skipped.
 *
 * **Arrows move by date, not by grid cell**, which settles the two seams a year has and a month does
 * not. `Right` from the last cell of a week row lands on the next row, as it does anywhere else;
 * `Right` from the last day of a month lands on the 1st of the next one, in *that month's* grid, and
 * the month column scrolls to show it — the calendar reads as one continuous run of days that happens
 * to be drawn in twelve blocks. Past December 31st the same step lands on January 1st, which is on
 * another page, so the year pages forward and the ring lands on it. The alternative — treating each
 * month grid as its own closed keyboard region — would make the twelve grids twelve tab stops or
 * twelve dead ends, and a user could not reach the 1st of a month without a pointer.
 *
 * ```kotlin
 * val state = rememberKalendarYearState(initialDate = today)
 * var selected by remember { mutableStateOf(setOf(today)) }
 *
 * KalendarYear(
 *     selectedDate = today,
 *     state = state,
 *     selectedDates = selected,
 *     events = bookings,
 *     onDateClick = { date, dayBookings: List<Booking> -> selected = setOf(date) },
 * )
 * ```
 *
 * The year is generic in [E], the caller's own event type, inferred from [events]; every callback and
 * slot hands that type straight back. Use the event-free overload when there are no events to infer
 * from.
 *
 * @param E The event type this calendar holds. Any [KalendarEvent] implementation, including a
 *   caller's own domain type.
 * @param selectedDate The date used as the default single-selection highlight and, when [state]
 *   is left at its default, the year initially visible. Superseded by [selectedDates] when that
 *   is supplied explicitly.
 * @param modifier [Modifier] applied to the outermost container.
 * @param state Navigation state from [rememberKalendarYearState]. Own it yourself to read
 *   [KalendarViewState.visibleDate] (e.g. for a screen title) or call
 *   [KalendarViewState.animateScrollTo] from a button.
 * @param eventLoader Loads events one visible year at a time instead of taking them all up front —
 *   see [rememberKalendarEventLoader], and give it the same [state] this calendar has. A year page
 *   is a wide range to ask a source for; a calendar with many events is usually better served by a
 *   month or week view with its own loader. `null`, the default, is the eager list. See
 *   [KalendarMonth] for the full contract.
 * @param events Events shown as indicator dots on day cells. Defaults to the events [eventLoader]
 *   holds, or to none.
 * @param selectedDates The set of highlighted dates. Defaults to a single-element set containing
 *   [selectedDate]; pass your own hoisted set for multi-select or range selection (see
 *   [datesUntil] for a range-selection helper). Hoist/`remember` the same instance across
 *   recompositions rather than building a fresh [Set] every time.
 * @param onDateClick Called when a non-disabled date is tapped, with the events on that date.
 * @param onDayEventClick Called when a row of a day cell's `+N` overflow popover is tapped, with
 *   the event that row represents; the popover closes itself afterwards. Defaults to `null`, which
 *   leaves the popover a read-only list of what the cell could not show. Has no effect when
 *   [dayContent] replaces the built-in cell — a replacement cell owns its own popover, if any.
 * @param config Shared visual/behavioural settings — disabled dates, arrow visibility, background,
 *   and [KalendarViewConfig.visibleDaysOfWeek], applied to all twelve month grids alike.
 * @param header Replaces the navigation header above the year's months. Receives a
 *   [KalendarHeaderScope] carrying the formatted title and the navigation callbacks, so your own
 *   chrome keeps working arrows and today button. Defaults to [KalendarHeaderDefaults.Header]
 *   driven by [config].
 * @param dayOfWeekLabel Replaces the content of each weekday column header, on every month grid.
 *   The grid still places one per column, so a replacement stays aligned with the day cells.
 *   Defaults to [KalendarHeaderDefaults.DayOfWeekLabel] formatted by [config].
 * @param loadingContent Slot drawn over a year whose events have not arrived yet, given a
 *   [KalendarLoadingScope] with the range, its [KalendarLoadStatus] and the failure if it failed.
 *   Overlaid at the year's own size, so it cannot move the months under it. Defaults to
 *   [KalendarLoadingDefaults.Placeholder]. Never drawn when [eventLoader] is `null`.
 * @param dayContent Replaces the default day-cell rendering. Receives a [KalendarDayScope]
 *   describing the date being rendered.
 */
@Composable
public fun <E : KalendarEvent> KalendarYear(
    selectedDate: LocalDate,
    modifier: Modifier = Modifier,
    state: KalendarViewState = rememberKalendarYearState(selectedDate),
    eventLoader: KalendarEventLoader<E>? = null,
    events: List<E> = eventLoader?.events ?: emptyList(),
    selectedDates: Set<LocalDate> = setOf(selectedDate),
    onDateClick: (date: LocalDate, events: List<E>) -> Unit = { _, _ -> },
    onDayEventClick: ((event: E) -> Unit)? = null,
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
    val dimensions = kalendarDimensions()
    val today = rememberKalendarToday(state.timeSource)
    val eventsByDate = remember(events) { events.byDateExpandingSpans() }
    // Hoisted out of the cell lambda: `?:` on a non-capturing literal yields the same singleton
    // every recomposition, so the cell's `onEventClick` parameter stays equal and skippable.
    val dayEventClick = onDayEventClick ?: {}

    val keyboard = rememberKalendarGridKeyboardState()
    val coroutineScope = rememberCoroutineScope()
    val visibleYear = state.visibleDate.year
    val periodDates = remember(visibleYear, config.visibleDaysOfWeek) {
        yearDates(year = visibleYear, visibleDaysOfWeek = config.visibleDaysOfWeek)
    }

    Column(
        modifier = modifier.background(config.background ?: SolidColor(colors.background)),
        verticalArrangement = Arrangement.spacedBy(dimensions.headerContentSpacing),
    ) {
        val title = remember(state.visibleDate) { state.visibleDate.year.toString() }
        header(rememberKalendarHeaderScope(state = state, title = title))
        Box(
            modifier = Modifier.kalendarGridKeyboard(
                keyboard = keyboard,
                periodDates = periodDates,
                today = today,
                selectedDate = selectedDates.singleOrNull(),
                onSelect = { date -> onDateClick(date, eventsByDate[date] ?: emptyList()) },
                onScrollTo = { date -> coroutineScope.launch { state.animateScrollTo(date) } },
                pageStep = { date, pages -> date.plus(pages, DateTimeUnit.YEAR) },
                isDateEnabled = { date -> !config.disabledDates(date) },
                // A year view has no range-drag gesture to mirror, so Shift+arrow moves focus and
                // reports nothing rather than inventing a second range API for one view.
                onRangeDrag = null,
                onRangeDragEnd = null,
                monthNameFormatter = config.monthNameFormatter,
                visibleDaysOfWeek = config.visibleDaysOfWeek,
            )
        ) {
            HorizontalPager(
                state = state.pagerState,
                modifier = Modifier.fillMaxWidth(),
            ) { page ->
                val yearStart = state.pager.dateAt(page)
                // Per page, not per view: the ring is drawn in the page's own coordinate space, so it
                // travels with the page during a swipe instead of hanging in the pager's.
                val cells = remember { KalendarKeyboardCells() }
                val monthListState = rememberLazyListState()
                KeepKeyboardFocusInView(
                    keyboard = keyboard,
                    cells = cells,
                    listState = monthListState,
                ) { date ->
                    date.month.ordinal.takeIf { date.year == yearStart.year }
                }
                Box(modifier = Modifier.onGloballyPositioned { coordinates -> cells.attach(coordinates) }) {
                    // Lazy, one item per month: a year is twelve month grids of up to 42 cells, and a
                    // non-lazy scrolling Column composed all ~404 of them before the first frame even
                    // though one or two months are ever on screen. The *inner* grid stays eager
                    // ([KalendarStaticGrid]) on purpose — a LazyVerticalGrid nested in a scrolling parent
                    // is measured with an unbounded height and crashes.
                    LazyColumn(
                        state = monthListState,
                        modifier = Modifier.fillMaxWidth().testTag(YEAR_MONTH_LIST_TAG),
                        verticalArrangement = Arrangement.spacedBy(dimensions.monthSpacing),
                    ) {
                        items(items = Month.entries, key = { month -> month.ordinal }) { month ->
                            val monthStart = LocalDate(yearStart.year, month, 1)
                            val dates = remember(monthStart, state.startDayOfWeek, config.visibleDaysOfWeek) {
                                monthGridDates(
                                    monthStart = monthStart,
                                    startDayOfWeek = state.startDayOfWeek,
                                    visibleDaysOfWeek = config.visibleDaysOfWeek,
                                )
                            }
                            Column {
                                BasicText(
                                    text = config.monthNameFormatter(monthStart.month),
                                    style = kalendarTypography().monthLabel.copy(color = colors.headerContent),
                                    modifier = Modifier.padding(dimensions.sectionPadding),
                                )
                                KalendarStaticGrid(
                                    dates = dates,
                                    startDayOfWeek = state.startDayOfWeek,
                                    modifier = Modifier.padding(horizontal = YEAR_MONTH_GRID_PADDING),
                                    events = events,
                                    visibleDaysOfWeek = config.visibleDaysOfWeek,
                                    dayOfWeekLabel = dayOfWeekLabel,
                                ) { date ->
                                    val dateEvents = eventsByDate[date] ?: emptyList()
                                    val isCurrentMonth = date.month == month
                                    val scope = KalendarDayScope(
                                        date = date,
                                        isSelected = date in selectedDates,
                                        isToday = date == today,
                                        isDisabled = config.disabledDates(date) || !isCurrentMonth,
                                        events = dateEvents,
                                    )
                                    Box(
                                        modifier = Modifier.kalendarKeyboardCell(
                                            cells = cells,
                                            date = date.takeIf { isCurrentMonth },
                                        ),
                                    ) {
                                        KalendarMonthGridCell(
                                            scope = scope,
                                            isCurrentMonth = isCurrentMonth,
                                            showAdjacentMonthDates = config.showAdjacentMonthDates,
                                            onClick = { onDateClick(date, dateEvents) },
                                            dayContent = dayContent,
                                            monthNameFormatter = config.monthNameFormatter,
                                            maxEventIndicators = config.eventIndicatorCap,
                                            onEventClick = dayEventClick,
                                        )
                                    }
                                }
                            }
                        }
                    }
                    KalendarKeyboardFocusRing(keyboard = keyboard, cells = cells)
                    KalendarPageLoading(
                        loader = eventLoader,
                        date = yearStart,
                        content = loadingContent,
                    )
                }
            }
        }
    }
}

/**
 * A year overview with no events — the date-selection half of [KalendarYear] alone.
 *
 * ```kotlin
 * KalendarYear(selectedDate = today, onDateClick = { date, _ -> selected = setOf(date) })
 * ```
 *
 * This overload exists for inference, not for behaviour — see [KalendarMonth]'s event-free overload
 * for why a default value cannot stand in for it. `onDayEventClick` is absent because a calendar
 * with no events can never fire it.
 *
 * @param selectedDate See [KalendarYear].
 * @param modifier See [KalendarYear].
 * @param state See [KalendarYear].
 * @param selectedDates See [KalendarYear].
 * @param onDateClick Called when a non-disabled date is tapped. The event list is always empty.
 * @param config See [KalendarYear].
 * @param header See [KalendarYear].
 * @param dayOfWeekLabel See [KalendarYear].
 * @param dayContent See [KalendarYear].
 */
@Composable
public fun KalendarYear(
    selectedDate: LocalDate,
    modifier: Modifier = Modifier,
    state: KalendarViewState = rememberKalendarYearState(selectedDate),
    selectedDates: Set<LocalDate> = setOf(selectedDate),
    onDateClick: (date: LocalDate, events: List<KalendarEvent>) -> Unit = { _, _ -> },
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
    KalendarYear<KalendarEvent>(
        selectedDate = selectedDate,
        modifier = modifier,
        state = state,
        selectedDates = selectedDates,
        onDateClick = onDateClick,
        config = config,
        header = header,
        dayOfWeekLabel = dayOfWeekLabel,
        dayContent = dayContent,
    )
}
