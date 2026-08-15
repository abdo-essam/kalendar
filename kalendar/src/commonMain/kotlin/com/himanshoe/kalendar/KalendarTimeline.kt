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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.onGloballyPositioned
import com.himanshoe.kalendar.component.KalendarHeaderDefaults
import com.himanshoe.kalendar.component.KalendarKeyboardCells
import com.himanshoe.kalendar.component.KalendarKeyboardFocusRing
import com.himanshoe.kalendar.component.KalendarMonthGridCell
import com.himanshoe.kalendar.component.KalendarStaticGrid
import com.himanshoe.kalendar.component.KeepKeyboardFocusInView
import com.himanshoe.kalendar.component.kalendarGridKeyboard
import com.himanshoe.kalendar.component.kalendarKeyboardCell
import com.himanshoe.kalendar.component.rememberKalendarGridKeyboardState
import com.himanshoe.kalendar.component.rememberKalendarTimelineHeaderScope
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
import com.himanshoe.kalendar.state.KalendarTimelineState
import com.himanshoe.kalendar.state.rememberKalendarEventLoader
import com.himanshoe.kalendar.state.rememberKalendarTimelineState
import com.himanshoe.kalendar.state.rememberKalendarToday
import com.himanshoe.kalendar.theme.kalendarColors
import com.himanshoe.kalendar.theme.kalendarDimensions
import com.himanshoe.kalendar.theme.kalendarTypography
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

/**
 * A continuous, vertically-scrolling calendar spanning many months, forward and backward from
 * [selectedDate], with no page boundaries — scroll from August straight into September into
 * October. Only the months near the current scroll position are ever composed.
 *
 * Unlike [KalendarWeek]/[KalendarMonth]/[KalendarYear], there are no arrow buttons — the scroll
 * itself is the only navigation, so [KalendarViewConfig.showNavigationArrows] has no effect here.
 * There is no month/year jump picker either — the sticky header's title is not tappable — so
 * [KalendarViewConfig.showJumpPicker] is likewise ignored.
 * A sticky header showing the topmost visible month (and, when [KalendarViewConfig.showTodayButton]
 * is enabled, a button that scrolls back to today) stays pinned above the scrolling content. How
 * far the list scrolls is controlled by [rememberKalendarTimelineState]'s `minDate`/`maxDate` —
 * unlimited in either direction when left unset (the default).
 *
 * The timeline is keyboard-navigable and is a **single tab stop**, with the key map [KalendarMonth]
 * already uses: `Tab` enters at the selected date (or today), arrows move a focus ring one day or one
 * row, `Home`/`End` jump to the first and last day of the month on screen, `Enter`/`Space` select,
 * and `Tab` again leaves. There being no pages, the *list* follows the ring rather than the other way
 * round: arrowing into a different month scrolls that month to the top, exactly as a paged view turns
 * a page, while a move within the month brings the ring back on screen by the shortfall alone and
 * leaves the position a pointer user scrolled to otherwise intact. The focused date is held above the
 * list, so it survives the month holding it being recycled: scroll a year away, press an arrow, and
 * focus is where it was left with its month scrolled back under it.
 *
 * `PageUp`/`PageDown` move **one month**, which is what this list pages by — a month grid is sized to
 * fill the viewport, so its item boundary is also its screenful. A step measured in viewports instead
 * would be a step whose length depended on the window height, and a keyboard user would be told a
 * different date on a phone than on a desktop for the same key.
 *
 * ```kotlin
 * val state = rememberKalendarTimelineState(initialDate = today)
 * var selected by remember { mutableStateOf(setOf(today)) }
 *
 * KalendarTimeline(
 *     selectedDate = today,
 *     state = state,
 *     selectedDates = selected,
 *     events = bookings,
 *     onDateClick = { date, dayBookings: List<Booking> -> selected = setOf(date) },
 * )
 * ```
 *
 * The timeline is generic in [E], the caller's own event type, inferred from [events]; every callback
 * and slot hands that type straight back. Use the event-free overload when there are no events to
 * infer from.
 *
 * @param E The event type this calendar holds. Any [KalendarEvent] implementation, including a
 *   caller's own domain type.
 * @param selectedDate The date used as the default single-selection highlight and, when [state]
 *   is left at its default, the month initially at the top of the list. Superseded by
 *   [selectedDates] when that is supplied explicitly.
 * @param modifier [Modifier] applied to the outermost container.
 * @param state Scroll state from [rememberKalendarTimelineState]. Own it yourself to read
 *   [KalendarTimelineState.visibleDate] (e.g. for a screen title) or call
 *   [KalendarTimelineState.scrollTo] from a button.
 * @param eventLoader Loads events a few visible months at a time instead of taking them all up
 *   front — see [rememberKalendarEventLoader], and give it the same [state] this timeline has. The
 *   timeline shows several months at once, so its window is every month on screen plus the
 *   prefetched ones either side of that run. `null`, the default, is the eager list. See
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
 * @param config Shared visual/behavioural settings — disabled dates, background, today button.
 *   Arrow visibility and the jump picker have no effect (see above).
 * @param header Replaces the sticky overlay pinned above the scrolling months. Receives a
 *   [KalendarHeaderScope] whose page vocabulary is mapped onto months, since a timeline has no
 *   pages: `canScrollBackward`/`canScrollForward` are always `true` (the scroll itself is clamped
 *   to `minDate`/`maxDate`), and `goToPreviousPage`/`goToNextPage` scroll one month back/forward
 *   from the topmost visible month. Defaults to [KalendarHeaderDefaults.TimelineHeader] driven by
 *   [config].
 * @param dayOfWeekLabel Replaces the content of each weekday column header, on every month grid.
 *   The grid still places one per column, so a replacement stays aligned with the day cells.
 *   Defaults to [KalendarHeaderDefaults.DayOfWeekLabel] formatted by [config].
 * @param loadingContent Slot drawn over a month whose events have not arrived yet, given a
 *   [KalendarLoadingScope] with the range, its [KalendarLoadStatus] and the failure if it failed.
 *   Overlaid at that month's own size, so it cannot move the list under it. Defaults to
 *   [KalendarLoadingDefaults.Placeholder]. Never drawn when [eventLoader] is `null`.
 * @param dayContent Replaces the default day-cell rendering. Receives a [KalendarDayScope]
 *   describing the date being rendered.
 */
@Composable
public fun <E : KalendarEvent> KalendarTimeline(
    selectedDate: LocalDate,
    modifier: Modifier = Modifier,
    state: KalendarTimelineState = rememberKalendarTimelineState(selectedDate),
    eventLoader: KalendarEventLoader<E>? = null,
    events: List<E> = eventLoader?.events ?: emptyList(),
    selectedDates: Set<LocalDate> = setOf(selectedDate),
    onDateClick: (date: LocalDate, events: List<E>) -> Unit = { _, _ -> },
    onDayEventClick: ((event: E) -> Unit)? = null,
    config: KalendarViewConfig = KalendarViewConfig(),
    header: @Composable (KalendarHeaderScope) -> Unit = { headerScope ->
        KalendarHeaderDefaults.TimelineHeader(
            scope = headerScope,
            showTodayButton = config.showTodayButton,
            background = config.background,
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
    val typography = kalendarTypography()
    val dimensions = kalendarDimensions()
    val today = rememberKalendarToday(state.timeSource)
    val eventsByDate = remember(events) { events.byDateExpandingSpans() }
    // Hoisted out of the cell lambda: `?:` on a non-capturing literal yields the same singleton
    // every recomposition, so the cell's `onEventClick` parameter stays equal and skippable.
    val dayEventClick = onDayEventClick ?: {}
    val background = config.background ?: SolidColor(colors.background)

    val keyboard = rememberKalendarGridKeyboardState()
    val cells = remember { KalendarKeyboardCells() }
    // The month at the top of the list, which is also the timeline's period: `Home`/`End` act on the
    // month the user is looking at. Any keyboard move that leaves it scrolls the month it landed in
    // to the top, so for a keyboard user the two are the same month by the time the next key arrives.
    val periodMonthStart = state.visibleDate
    val periodDates = remember(periodMonthStart, config.visibleDaysOfWeek) {
        monthDates(monthStart = periodMonthStart, visibleDaysOfWeek = config.visibleDaysOfWeek)
    }
    KeepKeyboardFocusInView(
        keyboard = keyboard,
        cells = cells,
        listState = state.lazyListState,
    ) { date ->
        state.pager.pageOf(date.startOfMonth())
    }

    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .kalendarGridKeyboard(
                    keyboard = keyboard,
                    periodDates = periodDates,
                    today = today,
                    selectedDate = selectedDates.singleOrNull(),
                    onSelect = { date -> onDateClick(date, eventsByDate[date] ?: emptyList()) },
                    // No onScrollTo: there is no page to bring in, and KeepKeyboardFocusInView above
                    // already scrolls for every move rather than only for the ones leaving the month.
                    pageStep = { date, pages -> date.plus(pages, DateTimeUnit.MONTH) },
                    isDateEnabled = { date -> !config.disabledDates(date) },
                    // As in KalendarYear: no range-drag gesture here for Shift+arrow to mirror.
                    onRangeDrag = null,
                    onRangeDragEnd = null,
                    monthNameFormatter = config.monthNameFormatter,
                    visibleDaysOfWeek = config.visibleDaysOfWeek,
                )
                .onGloballyPositioned { coordinates -> cells.attach(coordinates) },
        ) {
            LazyColumn(
                state = state.lazyListState,
                modifier = Modifier.fillMaxWidth().background(background),
            ) {
                items(count = state.pager.pageCount, key = { index -> index }) { index ->
                    val monthStart = state.pager.dateAt(index)
                    val dates = remember(monthStart, state.startDayOfWeek, config.visibleDaysOfWeek) {
                        monthGridDates(
                            monthStart = monthStart,
                            startDayOfWeek = state.startDayOfWeek,
                            visibleDaysOfWeek = config.visibleDaysOfWeek,
                        )
                    }
                    Box(propagateMinConstraints = true) {
                        Column {
                            BasicText(
                                text = monthTitle(monthStart, config.monthNameFormatter),
                                style = typography.headerTitle.copy(color = colors.headerContent),
                                modifier = Modifier.padding(dimensions.sectionPadding),
                            )
                            KalendarStaticGrid(
                                dates = dates,
                                startDayOfWeek = state.startDayOfWeek,
                                events = events,
                                visibleDaysOfWeek = config.visibleDaysOfWeek,
                                dayOfWeekLabel = dayOfWeekLabel,
                            ) { date ->
                                val dateEvents = eventsByDate[date] ?: emptyList()
                                val isCurrentMonth = date.month == monthStart.month
                                val cellScope = KalendarDayScope(
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
                                        scope = cellScope,
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
                        KalendarPageLoading(
                            loader = eventLoader,
                            date = monthStart,
                            content = loadingContent,
                        )
                    }
                }
            }
            KalendarKeyboardFocusRing(keyboard = keyboard, cells = cells)
        }

        Column(modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth()) {
            val stickyTitle = remember(state.visibleDate) {
                monthTitle(state.visibleDate, config.monthNameFormatter)
            }
            header(rememberKalendarTimelineHeaderScope(state = state, title = stickyTitle))
        }
    }
}

/**
 * A continuous scrolling calendar with no events — the date-selection half of [KalendarTimeline]
 * alone.
 *
 * ```kotlin
 * KalendarTimeline(selectedDate = today, onDateClick = { date, _ -> selected = setOf(date) })
 * ```
 *
 * This overload exists for inference, not for behaviour — see [KalendarMonth]'s event-free overload
 * for why a default value cannot stand in for it. `onDayEventClick` is absent because a calendar
 * with no events can never fire it.
 *
 * @param selectedDate See [KalendarTimeline].
 * @param modifier See [KalendarTimeline].
 * @param state See [KalendarTimeline].
 * @param selectedDates See [KalendarTimeline].
 * @param onDateClick Called when a non-disabled date is tapped. The event list is always empty.
 * @param config See [KalendarTimeline].
 * @param header See [KalendarTimeline].
 * @param dayOfWeekLabel See [KalendarTimeline].
 * @param dayContent See [KalendarTimeline].
 */
@Composable
public fun KalendarTimeline(
    selectedDate: LocalDate,
    modifier: Modifier = Modifier,
    state: KalendarTimelineState = rememberKalendarTimelineState(selectedDate),
    selectedDates: Set<LocalDate> = setOf(selectedDate),
    onDateClick: (date: LocalDate, events: List<KalendarEvent>) -> Unit = { _, _ -> },
    config: KalendarViewConfig = KalendarViewConfig(),
    header: @Composable (KalendarHeaderScope) -> Unit = { headerScope ->
        KalendarHeaderDefaults.TimelineHeader(
            scope = headerScope,
            showTodayButton = config.showTodayButton,
            background = config.background,
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
    KalendarTimeline<KalendarEvent>(
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
