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

package com.himanshoe.kalendar.component

import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.toSize
import com.himanshoe.kalendar.foundation.datetime.DAYS_PER_WEEK
import com.himanshoe.kalendar.foundation.grid.ALL_DAYS_OF_WEEK
import com.himanshoe.kalendar.foundation.grid.visibleDateOnOrAfter
import com.himanshoe.kalendar.foundation.grid.visibleDateStep
import com.himanshoe.kalendar.theme.KalendarStrings
import com.himanshoe.kalendar.theme.kalendarColors
import com.himanshoe.kalendar.theme.kalendarDimensions
import com.himanshoe.kalendar.theme.kalendarShapes
import com.himanshoe.kalendar.theme.kalendarStrings
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.plus

/**
 * Where keyboard focus currently sits inside a paged calendar, and whether a `Shift`-extended range
 * is in progress.
 *
 * Hoisted above the pager rather than kept per page, so that stepping off the edge of one month into
 * the next carries the focus with it. It is a `@Stable` holder rather than a pair of hoisted
 * `State`s for one reason that the recomposition benchmark cares about: passing the holder down lets
 * [KalendarGrid] receive the same instance on every recomposition and still skip, while every
 * *read* of [focusedDate] happens inside a layout or semantics lambda, where a change re-lays-out or
 * re-announces without recomposing the 42 cells.
 */
@Stable
internal class KalendarGridKeyboardState {

    /** The date the focus ring is drawn around, or `null` before the grid has ever held focus. */
    var focusedDate: LocalDate? by mutableStateOf(null)

    /** Whether the grid itself — not a descendant, and not the header — is the focus target. */
    var isFocused: Boolean by mutableStateOf(false)

    /**
     * The date a `Shift`-extended range started from, or `null` when no range is in progress. The
     * range being reported is always (this, [focusedDate]).
     */
    var rangeAnchor: LocalDate? = null
}

/** Creates and [remember]s the [KalendarGridKeyboardState] shared by a paged view and its grids. */
@Composable
internal fun rememberKalendarGridKeyboardState(): KalendarGridKeyboardState =
    remember { KalendarGridKeyboardState() }

/**
 * The date a navigation key moves focus to from [focused], or `null` if [key] does not navigate.
 *
 * Left/right step by one *visible* day, so on a Mon–Fri calendar `Right` from Friday reaches Monday
 * rather than stopping the ring on a Saturday that has no cell. Wrapping from the end of a row to
 * the start of the next therefore still falls out of date arithmetic rather than index arithmetic —
 * the caller decides whether the result still lies on the visible page.
 *
 * Up/down step a whole week, which lands on the same weekday and so on the same column; a column is
 * on screen by definition, so no snapping is needed. `Home`/`End` land on the first and last day of
 * [periodDates], the *period* rather than the grid, so `Home` in a month view reaches the 1st and not
 * the leading padding day from the previous month — and [periodDates] carries the same visible-day
 * filter, so neither lands on a hidden day either. `PageUp`/`PageDown` delegate to [pageStep], which
 * is the view's own unit: a week for [KalendarWeek], a month for [KalendarMonth]. A month step keeps
 * the day of *month*, which may be a hidden weekday, so its result is nudged forward to the next
 * visible day.
 */
@Suppress("ReturnCount")
internal fun keyboardTargetDate(
    key: Key,
    focused: LocalDate,
    periodDates: List<LocalDate>,
    pageStep: (date: LocalDate, pages: Int) -> LocalDate,
    visibleDaysOfWeek: Set<DayOfWeek> = ALL_DAYS_OF_WEEK,
): LocalDate? = when (key) {
    Key.DirectionLeft -> visibleDateStep(from = focused, step = -1, visibleDaysOfWeek = visibleDaysOfWeek)
    Key.DirectionRight -> visibleDateStep(from = focused, step = 1, visibleDaysOfWeek = visibleDaysOfWeek)
    Key.DirectionUp -> focused.plus(-DAYS_PER_WEEK, DateTimeUnit.DAY)
    Key.DirectionDown -> focused.plus(DAYS_PER_WEEK, DateTimeUnit.DAY)
    Key.PageUp -> visibleDateOnOrAfter(pageStep(focused, -1), visibleDaysOfWeek)
    Key.PageDown -> visibleDateOnOrAfter(pageStep(focused, 1), visibleDaysOfWeek)
    Key.MoveHome -> periodDates.firstOrNull()
    Key.MoveEnd -> periodDates.lastOrNull()
    else -> null
}

/** Whether [key] is one of the keys that commits the focused date as a selection. */
internal fun isKeyboardSelectKey(key: Key): Boolean =
    key == Key.Enter || key == Key.NumPadEnter || key == Key.Spacebar

/** Whether [key] is a shift key, whose release ends a `Shift`-extended range. */
private fun isShiftKey(key: Key): Boolean = key == Key.ShiftLeft || key == Key.ShiftRight

/**
 * Makes the node this is applied to the calendar grid's single tab stop, and drives
 * [keyboard] from the keyboard.
 *
 * Apply it to a container wrapping the pager — not to a day cell. The whole grid is one tab stop,
 * the roving-tabindex pattern every native date picker uses: `Tab` enters the grid once, arrows move
 * a focus ring between days without leaving it, and `Tab` again leaves. Reaching 42 cells by
 * pressing `Tab` 42 times is not navigation, it is a tab trap with extra steps, which is why
 * [KalendarGrid] deactivates focus on the cells themselves — and why [Modifier.kalendarKeyboardCell]
 * does the same for the grids that are not [KalendarGrid].
 *
 * The container underneath does not have to be a pager. [KalendarYear] applies this above a year
 * pager whose pages are themselves scrolling month columns, and [KalendarTimeline] applies it above a
 * `LazyColumn` with no pager at all; what a container scrolls to bring the ring back into view is
 * [KeepKeyboardFocusInView]'s job rather than this modifier's.
 *
 * What each key does:
 * - **Arrows** move one day or one row. Off the end of the visible period, [onScrollTo] brings the
 *   period holding the target into view and the focus lands on it, so a month boundary is invisible
 *   to the user.
 * - **Enter/Space** select via [onSelect], which is the same callback a tap goes through.
 * - **PageUp/PageDown** move by [pageStep] — one page of the view's own unit.
 * - **Home/End** move to the first and last day of [periodDates].
 * - **Shift + arrow** extends a range from the date focus was on when `Shift` was first held,
 *   reporting through [onRangeDrag] exactly as the press-and-hold drag gesture does; releasing
 *   `Shift` calls [onRangeDragEnd]. This is the whole point of the exercise: without it
 *   `onDateRangeSelect` is reachable by pointer only, which is a WCAG 2.1.1 failure.
 *
 * **Disabled dates are focusable but not selectable.** Focus lands on a date
 * [isDateEnabled] rejects — a keyboard user is told the date exists and is unavailable, rather than
 * having it silently skipped and a hole appear in the grid — but `Enter`/`Space` on it does nothing,
 * exactly as the cell's own `clickable(enabled = false)` behaves for a tap. Range extension does not
 * consult [isDateEnabled] at all, matching the drag gesture, which never has either.
 *
 * @param keyboard The focus state shared with every [KalendarGrid] under this node.
 * @param periodDates The visible period's own dates — a month's days rather than its 42-cell grid,
 *   so the trailing padding cells belonging to the next month are never focused in place of that
 *   month's real ones. It drives `Home`/`End`, and a navigation target outside it is what triggers
 *   [onScrollTo]. The period is the *view's* unit rather than the grid's: a month for
 *   [KalendarMonth], a week for [KalendarWeek], a whole year for [KalendarYear].
 * @param visibleDaysOfWeek The days that have a column, from [KalendarViewConfig.visibleDaysOfWeek].
 *   Arrowing skips the rest, so the ring can never land on a date that is not drawn.
 * @param today Today's date, used as the entry point when the grid is focused for the first time and
 *   nothing is selected.
 * @param selectedDate The date focus enters on, preferred over [today]. Pass the single selected
 *   date, or `null` for none.
 * @param onSelect Called by `Enter`/`Space` with the focused date, when [isDateEnabled] allows it.
 * @param onScrollTo Called with a date outside [periodDates], to bring the period holding it into
 *   view. Defaults to doing nothing, for the container that has nowhere to page to: a
 *   [KalendarTimeline] is one continuous list, so every date it can reach is already in it and
 *   [KeepKeyboardFocusInView] is the only thing that needs to scroll.
 * @param pageStep Returns the date `pages` pages away from `date`, in the view's own unit.
 * @param isDateEnabled Whether a date may be selected — [KalendarViewConfig.disabledDates] inverted.
 * @param onRangeDrag Reports a `Shift`-extended range as (anchor, focus). `null` disables range
 *   extension, matching a view whose `onDateRangeSelect` was left unset.
 * @param onRangeDragEnd Called once when `Shift` is released after a range extension.
 * @param monthNameFormatter Formats the month name inside the announcement of the newly focused
 *   date.
 */
@Composable
@Suppress("CyclomaticComplexMethod", "LongMethod")
internal fun Modifier.kalendarGridKeyboard(
    keyboard: KalendarGridKeyboardState,
    periodDates: List<LocalDate>,
    today: LocalDate,
    selectedDate: LocalDate?,
    onSelect: (LocalDate) -> Unit,
    onScrollTo: (LocalDate) -> Unit = {},
    pageStep: (date: LocalDate, pages: Int) -> LocalDate,
    isDateEnabled: (LocalDate) -> Boolean,
    onRangeDrag: ((start: LocalDate, current: LocalDate) -> Unit)?,
    onRangeDragEnd: (() -> Unit)?,
    monthNameFormatter: (Month) -> String,
    visibleDaysOfWeek: Set<DayOfWeek> = ALL_DAYS_OF_WEEK,
): Modifier {
    val strings = kalendarStrings()

    // Every callback and every list the handler needs is read through rememberUpdatedState, so the
    // handler lambda itself never has to be recreated — recreating it would tear down and rebuild
    // the key-input modifier node on each recomposition, and the pointer gesture code next door
    // keeps the same discipline for the same reason.
    val latestPeriodDates = rememberUpdatedState(periodDates)
    val latestToday = rememberUpdatedState(today)
    val latestSelectedDate = rememberUpdatedState(selectedDate)
    val latestOnSelect = rememberUpdatedState(onSelect)
    val latestOnScrollTo = rememberUpdatedState(onScrollTo)
    val latestPageStep = rememberUpdatedState(pageStep)
    val latestIsDateEnabled = rememberUpdatedState(isDateEnabled)
    val latestOnRangeDrag = rememberUpdatedState(onRangeDrag)
    val latestOnRangeDragEnd = rememberUpdatedState(onRangeDragEnd)
    val latestMonthNameFormatter = rememberUpdatedState(monthNameFormatter)
    val latestVisibleDaysOfWeek = rememberUpdatedState(visibleDaysOfWeek)

    return this
        .onFocusChanged { focusState ->
            keyboard.isFocused = focusState.isFocused
            if (focusState.isFocused) {
                if (keyboard.focusedDate == null) {
                    keyboard.focusedDate = latestSelectedDate.value
                        ?.takeIf { it in latestPeriodDates.value }
                        ?: latestToday.value.takeIf { it in latestPeriodDates.value }
                        ?: latestPeriodDates.value.firstOrNull()
                }
            } else if (keyboard.rangeAnchor != null) {
                keyboard.rangeAnchor = null
                latestOnRangeDragEnd.value?.invoke()
            }
        }
        .onPreviewKeyEvent { event ->
            if (!keyboard.isFocused) {
                return@onPreviewKeyEvent false
            }
            if (event.type == KeyEventType.KeyUp) {
                return@onPreviewKeyEvent endRangeOnShiftRelease(
                    event = event,
                    keyboard = keyboard,
                    onRangeDragEnd = latestOnRangeDragEnd.value,
                )
            }
            if (event.type != KeyEventType.KeyDown) {
                return@onPreviewKeyEvent false
            }
            val focused = keyboard.focusedDate ?: return@onPreviewKeyEvent false
            if (isKeyboardSelectKey(event.key)) {
                if (latestIsDateEnabled.value(focused)) {
                    latestOnSelect.value(focused)
                }
                return@onPreviewKeyEvent true
            }
            val target = keyboardTargetDate(
                key = event.key,
                focused = focused,
                periodDates = latestPeriodDates.value,
                pageStep = latestPageStep.value,
                visibleDaysOfWeek = latestVisibleDaysOfWeek.value,
            ) ?: return@onPreviewKeyEvent false

            val extending = event.isShiftPressed && latestOnRangeDrag.value != null
            if (extending) {
                val anchor = keyboard.rangeAnchor ?: focused
                keyboard.rangeAnchor = anchor
                keyboard.focusedDate = target
                latestOnRangeDrag.value?.invoke(anchor, target)
            } else {
                keyboard.rangeAnchor = null
                keyboard.focusedDate = target
            }
            if (target !in latestPeriodDates.value) {
                latestOnScrollTo.value(target)
            }
            true
        }
        .focusable()
        .semantics {
            // Read inside the semantics lambda on purpose: a change re-runs *this* lambda and
            // re-announces, without recomposing the grid's cells.
            val focused = keyboard.focusedDate
            if (focused != null && keyboard.isFocused) {
                contentDescription = keyboardFocusAnnouncement(
                    date = focused,
                    strings = strings,
                    monthNameFormatter = latestMonthNameFormatter.value,
                    isToday = focused == latestToday.value,
                )
                liveRegion = LiveRegionMode.Polite
            }
        }
}

/**
 * Ends an in-progress `Shift`-extended range when the shift key itself comes up. Returns `false`
 * either way: releasing shift is not a key the grid consumes, it just closes the range the way
 * lifting a finger closes the drag gesture.
 */
private fun endRangeOnShiftRelease(
    event: KeyEvent,
    keyboard: KalendarGridKeyboardState,
    onRangeDragEnd: (() -> Unit)?,
): Boolean {
    if (isShiftKey(event.key) && keyboard.rangeAnchor != null) {
        keyboard.rangeAnchor = null
        onRangeDragEnd?.invoke()
    }
    return false
}

/**
 * What a screen reader says when focus lands on [date]: the same sentence the day cell itself
 * announces, so moving by keyboard and moving by touch exploration sound alike.
 */
internal fun keyboardFocusAnnouncement(
    date: LocalDate,
    strings: KalendarStrings,
    monthNameFormatter: (Month) -> String,
    isToday: Boolean,
): String = buildString {
    append(strings.dayAccessibilityLabel(date, monthNameFormatter(date.month)))
    if (isToday) {
        append(strings.todaySuffix)
    }
}

/**
 * How many day cells a [KalendarKeyboardCells] tracks before the stale ones are swept out.
 *
 * A timeline scrolled through a decade reports around four thousand distinct dates, and a placement
 * whose `LazyColumn` item has since been recycled still holds a detached [LayoutCoordinates] — and
 * through it the layout node it belonged to. Sweeping on a size trigger rather than on every report
 * keeps the common case free: three months of grids is under two hundred entries, and a year page's
 * twelve months at most 366, so no calendar that is merely being *used* ever pays for a sweep.
 */
private const val MAX_TRACKED_CELLS = 1024

/**
 * Where a scrolling container's day cells landed, so a roving focus ring can be drawn over one of
 * them without any of them recomposing.
 *
 * [KalendarGrid] keeps an equivalent map of its own because it also positions a selection indicator
 * and a drag ghost from it; this is the same idea reduced to what the keyboard needs, for the grids
 * that are drawn by [KalendarStaticGrid] inside a scrolling parent and so have no such map. The
 * discipline is the one that matters and it is identical: the map is a plain one, every reader is a
 * layout lambda or a coroutine, and [revision] is the single [Int] that tells them the cells moved.
 * Held in snapshot state and read from a composable body, one layout pass of a month grid would
 * invalidate the composition that produced it, 42 writes at a time.
 *
 * Only cells whose date belongs to the grid's *own* month are ever reported. That is not an
 * optimisation: a year page draws twelve grids and a timeline draws an unbounded number, so April
 * 1st is on screen twice — once as March's trailing padding — and a map keyed by date could not say
 * which of the two the ring should sit on.
 */
@Stable
internal class KalendarKeyboardCells {

    private var container: LayoutCoordinates? by mutableStateOf(null)
    private val placements = mutableMapOf<LocalDate, KalendarKeyboardCellPlacement>()

    /**
     * Bumped whenever a cell reports a placement it did not already have, and so the one thing that
     * tells the focus ring the cells have moved.
     *
     * Written on a *change* only. A write on every callback would re-place the ring after every
     * layout pass a settled calendar performs, for the sake of a position it already holds — which
     * on a scrolling container is every frame the user's finger is down, ring or no ring.
     */
    private var revision by mutableIntStateOf(0)

    /** Records the container that every reported cell's bounds are expressed relative to. */
    fun attach(coordinates: LayoutCoordinates) {
        container = coordinates
    }

    /** Records where [date]'s cell landed. Called from the layout phase, once per cell per pass. */
    fun report(date: LocalDate, coordinates: LayoutCoordinates) {
        val root = container
        val known = placements[date]
        if (known != null && known.stillDescribes(candidate = coordinates, container = root)) {
            return
        }
        if (placements.size > MAX_TRACKED_CELLS) {
            placements.values.removeAll { !it.coordinates.isAttached }
        }
        placements[date] = KalendarKeyboardCellPlacement(coordinates = coordinates, container = root)
        revision++
    }

    /**
     * Where [date]'s cell sits in the container's coordinate space, or `null` when no such cell is on
     * screen — the date is in a month this container has not composed, or in one a `LazyColumn` has
     * since recycled.
     *
     * **Never call this from composition.** Everything it reads is written during layout.
     */
    fun boundsOf(date: LocalDate?): Rect? {
        // Read first and unconditionally: this is the subscription that re-runs a layout lambda once
        // the cells are positioned, and a revision of zero does mean no cell has ever reported one.
        if (revision == 0) {
            return null
        }
        val root = container ?: return null
        val cell = date?.let(placements::get)?.coordinates?.takeIf { it.isAttached } ?: return null
        return Rect(offset = root.localPositionOf(cell, Offset.Zero), size = cell.size.toSize())
    }

    /** The container's own height, which is the window [boundsOf]'s result has to fall inside. */
    fun viewportHeight(): Float = container?.size?.height?.toFloat() ?: 0f
}

/**
 * One day cell as its last [Modifier.onGloballyPositioned] callback described it, and the comparison
 * that tells a pass which *moved* a cell from one that merely re-reported it.
 *
 * The coordinates object cannot answer that itself: it is the same instance from one pass to the
 * next and a live view rather than a snapshot, so putting it back into the map is a no-op even when
 * the cell has moved half the screen.
 *
 * The position compared is the one **inside the container**, which is the only frame of reference
 * that answers the question the focus ring actually asks. Inside the cell's own parent — the row it
 * sits in — a whole month scrolling up the screen does not move a single cell, so the ring would
 * keep its old offset and be left behind by the scroll. In the root, the opposite: a year pager
 * translating a page across the screen moves every cell on every frame of the swipe, and the ring is
 * inside that page and travelling with it, so all of those writes would say nothing. The container
 * is what the ring is positioned against, so it is what a change has to be measured in.
 */
private class KalendarKeyboardCellPlacement(
    val coordinates: LayoutCoordinates,
    container: LayoutCoordinates?,
) {

    private val positionInContainer: Offset? = container?.localPositionOf(coordinates, Offset.Zero)
    private val size: IntSize = coordinates.size

    /**
     * Whether [candidate] is the same node, still exactly where this placement recorded it.
     *
     * A placement taken before the container had reported its own coordinates has no position to
     * compare, and describes nothing: it is replaced on the next pass, when there is one.
     */
    fun stillDescribes(candidate: LayoutCoordinates, container: LayoutCoordinates?): Boolean {
        val known = positionInContainer ?: return false
        if (candidate !== coordinates || candidate.size != size) {
            return false
        }
        return container != null && container.localPositionOf(candidate, Offset.Zero) == known
    }
}

/**
 * Makes one day cell of a [KalendarStaticGrid] part of keyboard navigation: it stops being a tab
 * stop, and — when [date] is non-null — it reports where it lands to [cells].
 *
 * Apply it to a node wrapping the cell rather than to the cell itself. `canFocus = false` has to sit
 * on a node with no focus target of its own in its chain, exactly as it does in [KalendarGrid]; on
 * the cell's own modifier it would be competing with the `clickable` that makes the cell focusable in
 * the first place.
 *
 * Pass `null` for [date] on a cell belonging to the previous or next month. It is still deactivated
 * — a year page would otherwise hold 400-odd tab stops — but it is not tracked, so the one cell the
 * ring can sit on for a given date is the one inside that date's own month grid.
 */
internal fun Modifier.kalendarKeyboardCell(cells: KalendarKeyboardCells, date: LocalDate?): Modifier = this
    .focusProperties { canFocus = false }
    .then(
        if (date == null) {
            Modifier
        } else {
            Modifier.onGloballyPositioned { coordinates -> cells.report(date, coordinates) }
        },
    )

/**
 * The roving keyboard focus ring for a scrolling calendar: a [KalendarColors.focusIndicator] outline
 * around whichever cell [keyboard] currently points at, in [KalendarShapes.dayCell] and at the day
 * cell's own [FOCUS_RING_WIDTH], so it is indistinguishable from the ring [KalendarGrid] draws and
 * from the one a focused cell draws for itself.
 *
 * Place it as a sibling *after* the scrolling list, inside the node [KalendarKeyboardCells.attach]
 * was given. After, rather than before as in [KalendarGrid], because a timeline's list paints a
 * background and a ring behind it would be invisible; the ring is an outline around the cell's
 * padding, so drawing it on top covers nothing.
 *
 * Both state reads — the focused date and whether the calendar has focus — happen inside
 * [Modifier.kalendarFocusOverlay]'s layout lambdas, so an arrow press recomposes nothing at all.
 */
@Composable
internal fun KalendarKeyboardFocusRing(
    keyboard: KalendarGridKeyboardState,
    cells: KalendarKeyboardCells,
) {
    val colors = kalendarColors()
    val shapes = kalendarShapes()
    val dimensions = kalendarDimensions()

    Box(
        modifier = Modifier
            .kalendarFocusOverlay {
                if (keyboard.isFocused) {
                    cells.boundsOf(keyboard.focusedDate)
                } else {
                    null
                }
            }
            .testTag(KEYBOARD_FOCUS_RING_TAG)
            .padding(dimensions.dayCellPadding)
            .border(width = FOCUS_RING_WIDTH, color = colors.focusIndicator, shape = shapes.dayCell),
    )
}

/**
 * Sizes and positions the focus ring over whichever cell [bounds] names, or measures it away to
 * nothing when [bounds] names none — the date is off screen, or the calendar does not have focus.
 *
 * Both reads happen inside the `offset` and `layout` lambdas, which run in the layout phase, so an
 * arrow press re-lays-out one node and recomposes nothing. Measuring an absent ring to zero rather
 * than leaving it out of the composition is what keeps that true in both directions: whether the ring
 * is *shown* is then a layout-phase decision like everything else about it, and a zero-sized node
 * draws nothing at all.
 */
private fun Modifier.kalendarFocusOverlay(bounds: () -> Rect?): Modifier = this
    .offset {
        val cell = bounds() ?: return@offset IntOffset.Zero
        IntOffset(cell.left.toInt(), cell.top.toInt())
    }
    .layout { measurable, _ ->
        val size = bounds()?.size ?: Size.Zero
        val placeable = measurable.measure(Constraints.fixed(size.width.toInt(), size.height.toInt()))
        layout(placeable.width, placeable.height) { placeable.place(0, 0) }
    }

/**
 * Keeps [listState] scrolled so that the cell [KalendarGridKeyboardState.focusedDate] points at stays
 * on screen.
 *
 * This is what replaces "page to the target" for a container that scrolls instead of paging, and it
 * is the same effect for both of them: [KalendarYear]'s column of twelve month grids and
 * [KalendarTimeline]'s unbounded list of them. It runs entirely outside composition — a [snapshotFlow]
 * on the focused date, and reads of cell bounds that were written during layout — so an arrow press
 * that scrolls the list recomposes no more of the calendar than the scroll itself does.
 *
 * Three cases, in order, and the order is the rule:
 * - **No cell at all.** The date is in a month the list has not composed, or has recycled since. Its
 *   item is scrolled to the top, which is the only way back to a date that is not on screen.
 * - **A different month from the last move.** Its item is scrolled to the top too, even when the cell
 *   is already visible. Crossing a month is the one keyboard move that changes what the calendar is
 *   *about*, and a paged view answers it by turning the page; a scrolling one answers it by bringing
 *   the month up. It is also what keeps [KalendarTimeline]'s `Home`/`End` — which act on the month at
 *   the top of the list — talking about the month the ring is in.
 * - **The same month, cell off screen.** Nobody moved months, so the user scrolled: the list moves by
 *   exactly the shortfall and no further, rather than throwing away the position they chose.
 *
 * A cell already fully on screen in the month focus was already in moves nothing at all, which is
 * what keeps an arrow press within one month free.
 *
 * @param keyboard The focus state shared with the calendar's grids.
 * @param cells Where this container's cells landed, as reported by [Modifier.kalendarKeyboardCell].
 * @param listState The scrolling list holding the month grids.
 * @param itemIndexOf The list item holding [LocalDate]'s month, or `null` when the date is not in
 *   this list at all — which is how a year page ignores focus that has moved to another year.
 */
@Composable
internal fun KeepKeyboardFocusInView(
    keyboard: KalendarGridKeyboardState,
    cells: KalendarKeyboardCells,
    listState: LazyListState,
    itemIndexOf: (LocalDate) -> Int?,
) {
    val latestItemIndexOf = rememberUpdatedState(itemIndexOf)
    LaunchedEffect(keyboard, cells, listState) {
        // The item focus was in when it last moved. A coroutine-local rather than state: nothing may
        // observe it, and it has to be forgotten along with the page that owns this effect.
        var focusedItem: Int? = null
        // collectLatest, not collect: holding an arrow down outruns the scroll animation, and a
        // queued collector would walk the list to every date passed through on the way rather than
        // to the one focus ended on.
        snapshotFlow { keyboard.focusedDate }.collectLatest { date ->
            val index = date?.let(latestItemIndexOf.value) ?: return@collectLatest
            // Wait for the container rather than give up on it. "Is the ring on screen?" means
            // nothing before the container has been measured, and on a page the year pager has just
            // composed it has not been — while the date it should scroll to was already decided
            // before that page existed. Returning early there strands the ring, because the flow
            // above only emits on a *change* and there is no further change coming.
            val viewport = snapshotFlow { cells.viewportHeight() }.first { it > 0f }
            val bounds = cells.boundsOf(date)
            val movedMonth = index != focusedItem
            focusedItem = index
            when {
                bounds == null || movedMonth -> listState.animateScrollToItem(index)
                bounds.top < 0f -> listState.animateScrollBy(bounds.top)
                bounds.bottom > viewport -> listState.animateScrollBy(bounds.bottom - viewport)
            }
        }
    }
}
