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

package com.himanshoe.kalendar.state

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import com.himanshoe.kalendar.foundation.datetime.DAYS_PER_WEEK
import com.himanshoe.kalendar.foundation.datetime.startOfWeek
import com.himanshoe.kalendar.foundation.event.KalendarEvent
import com.himanshoe.kalendar.foundation.paging.KalendarPageUnit
import com.himanshoe.kalendar.foundation.paging.KalendarPager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/** One page either side of the visible one — enough that an ordinary swipe never waits. */
private const val DEFAULT_PREFETCH_PAGES = 1

/** How far a page's events have got. */
public enum class KalendarLoadStatus {

    /**
     * The range has been asked for and nothing has come back yet. A view draws its `loadingContent`
     * for a range in this state, which is what keeps "no events yet" from looking like "no events".
     */
    Loading,

    /**
     * The range's events have arrived. An empty list here is a real answer — the range genuinely has
     * no events — and draws as an empty calendar rather than as a placeholder.
     */
    Loaded,

    /** The source failed for this range. The failure is on [KalendarEventLoader.errorAt]. */
    Failed,
}

/**
 * The events a calendar currently holds, loaded a visible range at a time from a
 * [KalendarEventSource].
 *
 * This is the alternative to handing a view every event up front. It watches the state it is built
 * against, asks the source for the range that state is showing plus a page either side, and hands
 * the views what has arrived along with the [status][statusAt] of what has not:
 *
 * ```kotlin
 * val monthState = rememberKalendarMonthState()
 * val bookings = rememberKalendarEventLoader(state = monthState) { range ->
 *     bookingDao.observeBetween(from = range.start, to = range.endInclusive)
 * }
 *
 * KalendarMonth(selectedDate = today, state = monthState, eventLoader = bookings)
 * ```
 *
 * Four properties are worth knowing, because they are what a hand-rolled version usually gets wrong:
 *
 * - **A range in the window is requested once.** Swiping from one page to the next re-uses the
 *   collections already running for the pages that stay in the window; only the page that has just
 *   entered it is asked for.
 * - **Prefetching is what makes a swipe silent.** The window is the visible page ± `prefetchPages`,
 *   so the page a swipe is heading for has normally arrived before it is on screen. A jump — the
 *   today button, the jump picker — extends the window to the destination the moment the animation
 *   starts, rather than when it lands.
 * - **A range the user has swiped past is cancelled.** Leaving the window cancels that range's
 *   collection, so a request in flight for a page nobody is looking at stops, and an answer that
 *   arrives late for it is never shown.
 * - **Memory is bounded by the window**, not by how far the user has scrolled: a page that leaves
 *   the window is dropped along with its events. Coming back to it asks again.
 *
 * The events reaching [events] are de-duplicated by [KalendarEvent.id] — two adjacent ranges both
 * legitimately return a multi-day event that straddles their boundary, and without an id the
 * calendar has only the event's own `equals` to go on and may draw it twice.
 *
 * @param E The event type this loader holds — the caller's own domain type, straight through from
 *   the source to the views' callbacks.
 */
@Stable
public class KalendarEventLoader<E : KalendarEvent> internal constructor(
    private val pager: KalendarPager,
    source: KalendarEventSource<E>,
) {

    private val sources = MutableStateFlow(source)
    private val retries = MutableStateFlow(0)

    /**
     * The pages currently held, keyed by page index.
     *
     * Snapshot-backed, so a load completing on a background dispatcher recomposes the calendar; the
     * `jobs` map beside it deliberately is not, since nothing draws a [Job].
     */
    private val pages = mutableStateMapOf<Int, KalendarEventPage<E>>()
    private val jobs = mutableMapOf<Int, Job>()
    private var lastRetry = 0

    /**
     * Everything loaded so far, in page order — what a view's `events` parameter defaults to.
     *
     * It covers the whole window rather than the visible page alone, which is what lets a view index
     * events by date once above its pager instead of re-slicing them per page.
     */
    public val events: List<E> by derivedStateOf {
        pages.entries
            .sortedBy { entry -> entry.key }
            .flatMap { entry -> entry.value.events }
            .distinctBy { event -> event.id ?: event }
    }

    /**
     * The whole span currently held, which is the visible page plus its prefetched neighbours.
     *
     * Before the first range has been asked for this is the range of the page the calendar opens on,
     * so it is always a real span rather than a nullable one.
     */
    public val range: KalendarDateRange by derivedStateOf {
        val held = pages.keys
        if (held.isEmpty()) {
            rangeOfPage(pager.initialPage)
        } else {
            KalendarDateRange(
                start = rangeOfPage(held.min()).start,
                endInclusive = rangeOfPage(held.max()).endInclusive,
            )
        }
    }

    /**
     * The whole loader's state in one value, for a view with no page of its own to ask about —
     * [com.himanshoe.kalendar.KalendarAgenda], which lists everything it is given at once.
     *
     * [KalendarLoadStatus.Failed] wins over [KalendarLoadStatus.Loading]: a window where one range
     * failed and another is still arriving is a window with something to tell the user about.
     */
    public val status: KalendarLoadStatus by derivedStateOf {
        val held = pages.values
        when {
            held.isEmpty() -> KalendarLoadStatus.Loading
            held.any { page -> page.status == KalendarLoadStatus.Failed } -> KalendarLoadStatus.Failed
            held.any { page -> page.status == KalendarLoadStatus.Loading } -> KalendarLoadStatus.Loading
            else -> KalendarLoadStatus.Loaded
        }
    }

    /** The first failure among the held ranges, or `null` when none of them failed. */
    public val error: Throwable? by derivedStateOf {
        pages.entries.sortedBy { entry -> entry.key }.firstNotNullOfOrNull { entry -> entry.value.error }
    }

    /**
     * How far the events covering [date] have got.
     *
     * A date outside the window — one nothing has been asked for yet — reads as
     * [KalendarLoadStatus.Loading], because that is what it is about to be: a view only ever asks
     * about a page it is drawing, and drawing a page is what puts it in the window.
     */
    public fun statusAt(date: LocalDate): KalendarLoadStatus =
        pages[pager.pageOf(date)]?.status ?: KalendarLoadStatus.Loading

    /** The failure that stopped the range covering [date], or `null` when it did not fail. */
    public fun errorAt(date: LocalDate): Throwable? = pages[pager.pageOf(date)]?.error

    /**
     * Asks again for every range that failed, leaving the ranges that succeeded alone.
     *
     * Safe to call at any time, including from a slot drawn for a failed range: it records the
     * request rather than launching anything, so a loader whose calendar has left the composition
     * does no work until one is watching it again.
     */
    public fun retry() {
        retries.update { count -> count + 1 }
    }

    /** The span [page] covers — see [KalendarDateRange] for why a month page reports more than a month. */
    internal fun rangeOfPage(page: Int): KalendarDateRange {
        val start = pager.dateAt(page)
        val end = pager.dateAt(page + 1).minus(1, DateTimeUnit.DAY)
        // A month or year page draws a whole number of weeks, so its first and last rows carry days
        // from the neighbouring period. Those cells show events like any other, and a source asked
        // for the period alone would leave them empty until the neighbouring page happened to load.
        val drawsWholeWeeks = pager.unit == KalendarPageUnit.Month || pager.unit == KalendarPageUnit.Year
        return if (drawsWholeWeeks) {
            KalendarDateRange(
                start = start.startOfWeek(pager.startDayOfWeek),
                endInclusive = end.startOfWeek(pager.startDayOfWeek).plus(DAYS_PER_WEEK - 1, DateTimeUnit.DAY),
            )
        } else {
            KalendarDateRange(start = start, endInclusive = end)
        }
    }

    /** The range covering [date], for the slot drawn over a page that has not arrived. */
    internal fun rangeAt(date: LocalDate): KalendarDateRange = rangeOfPage(pager.pageOf(date))

    /** Replaces the source without disturbing what is already loaded. See [rememberKalendarEventLoader]. */
    internal fun updateSource(source: KalendarEventSource<E>) {
        sources.value = source
    }

    /**
     * Keeps the pages named by [windows] loaded, for as long as this is running.
     *
     * Suspends forever by design: it is the loader's whole engine, and cancelling it — which is what
     * leaving the composition does — cancels every collection it started. Kept separate from the
     * `remember` factories so that the window can be driven by a plain flow in a test, rather than
     * by a pager only a composition can move.
     */
    internal suspend fun keepLoaded(windows: Flow<Set<Int>>) {
        coroutineScope {
            combine(windows, retries) { window, retryCount -> window to retryCount }
                .distinctUntilChanged()
                .collect { (window, retryCount) ->
                    applyWindow(window = window, retryCount = retryCount, scope = this)
                }
        }
    }

    private fun applyWindow(window: Set<Int>, retryCount: Int, scope: CoroutineScope) {
        val retrying = retryCount != lastRetry
        lastRetry = retryCount
        jobs.keys.filterNot { page -> page in window }.forEach(::drop)
        if (retrying) {
            pages.entries
                .filter { entry -> entry.value.status == KalendarLoadStatus.Failed }
                .map { entry -> entry.key }
                .forEach(::drop)
        }
        window.forEach { page ->
            // The guard is the whole point of the window: a page that was already being collected
            // stays on the collection it has, so sliding the window by one asks only for the page
            // that just entered it.
            if (page !in jobs) {
                pages[page] = KalendarEventPage(
                    status = KalendarLoadStatus.Loading,
                    events = emptyList(),
                    error = null,
                )
                jobs[page] = scope.launch { collectPage(page) }
            }
        }
    }

    private fun drop(page: Int) {
        jobs.remove(page)?.cancel()
        pages.remove(page)
    }

    private suspend fun collectPage(page: Int) {
        val range = rangeOfPage(page)
        sources.value
            .eventsIn(range)
            .catch { failure ->
                publish(
                    page = page,
                    entry = KalendarEventPage(
                        status = KalendarLoadStatus.Failed,
                        events = pages[page]?.events.orEmpty(),
                        error = failure,
                    ),
                )
            }
            .collect { loaded ->
                publish(
                    page = page,
                    entry = KalendarEventPage(
                        status = KalendarLoadStatus.Loaded,
                        events = loaded,
                        error = null,
                    ),
                )
            }
    }

    /**
     * Records [entry] for [page] unless [page] has left the window while its load was in flight.
     *
     * Cancellation is cooperative, so a collection [drop] has just cancelled may still have an
     * emission on the way from another thread. Writing that back would put a range nobody is looking
     * at into a calendar that has moved on — the exact thing cancelling it was for. [drop] removes
     * the entry, so the entry's absence is the signal.
     */
    private fun publish(page: Int, entry: KalendarEventPage<E>) {
        if (page in pages) {
            pages[page] = entry
        }
    }
}

/** One page's worth of loaded events, and how that load went. */
@Immutable
private class KalendarEventPage<E : KalendarEvent>(
    val status: KalendarLoadStatus,
    val events: List<E>,
    val error: Throwable?,
)

/**
 * Creates and [remember]s a [KalendarEventLoader] that follows [state] — the paged views'
 * ([com.himanshoe.kalendar.KalendarMonth], [com.himanshoe.kalendar.KalendarWeek],
 * [com.himanshoe.kalendar.KalendarYear] and both Schedule views) way of loading events as they are
 * needed.
 *
 * ```kotlin
 * val monthState = rememberKalendarMonthState()
 * val bookings = rememberKalendarEventLoader(
 *     state = monthState,
 *     source = KalendarEventSource.suspending { range -> api.bookings(range.start, range.endInclusive) },
 * )
 *
 * KalendarMonth(selectedDate = today, state = monthState, eventLoader = bookings)
 * ```
 *
 * The window follows both where the pager is and where it is going, so the destination of a
 * programmatic jump starts loading while the animation is still running.
 *
 * @param state The navigation state to follow. Pass the same instance to the view, or the loader
 *   will be loading ranges the calendar is not showing.
 * @param source Where a range's events come from. Re-read for every range requested *after* it
 *   changes, so a source closing over changing state stays current; ranges already held keep the
 *   collection they were started with.
 * @param prefetchPages How many pages either side of the visible one to load as well. `0` loads
 *   only what is on screen and shows a placeholder on every swipe; the default of `1` is what makes
 *   an ordinary swipe land on a page that is already there. Must not be negative.
 */
@Composable
public fun <E : KalendarEvent> rememberKalendarEventLoader(
    state: KalendarViewState,
    source: KalendarEventSource<E>,
    prefetchPages: Int = DEFAULT_PREFETCH_PAGES,
): KalendarEventLoader<E> {
    require(prefetchPages >= 0) { "prefetchPages must be >= 0, was $prefetchPages" }
    // Deliberately not keyed on `source`: a source is usually a lambda that captures a repository,
    // so it is a new instance on every recomposition and keying on it would rebuild the loader —
    // and re-request every visible range — on each pass. updateSource keeps it current instead.
    val loader = remember(state.pager) { KalendarEventLoader(pager = state.pager, source = source) }
    SideEffect { loader.updateSource(source) }
    LaunchedEffect(loader, state, prefetchPages) {
        loader.keepLoaded(
            snapshotFlow {
                pagerWindow(
                    currentPage = state.pagerState.currentPage,
                    targetPage = state.pagerState.targetPage,
                    prefetchPages = prefetchPages,
                    pageCount = state.pager.pageCount,
                )
            },
        )
    }
    return loader
}

/**
 * Creates and [remember]s a [KalendarEventLoader] that follows a
 * [com.himanshoe.kalendar.KalendarTimeline]'s scroll.
 *
 * The timeline shows several months at once rather than one page at a time, so the window is every
 * month currently on screen plus [prefetchPages] either side of the run.
 *
 * @param state The timeline state to follow. Pass the same instance to the view.
 * @param source Where a range's events come from. See the paged overload.
 * @param prefetchPages How many months beyond the visible run to load as well. Must not be negative.
 */
@Composable
public fun <E : KalendarEvent> rememberKalendarEventLoader(
    state: KalendarTimelineState,
    source: KalendarEventSource<E>,
    prefetchPages: Int = DEFAULT_PREFETCH_PAGES,
): KalendarEventLoader<E> {
    require(prefetchPages >= 0) { "prefetchPages must be >= 0, was $prefetchPages" }
    val loader = remember(state.pager) { KalendarEventLoader(pager = state.pager, source = source) }
    SideEffect { loader.updateSource(source) }
    LaunchedEffect(loader, state, prefetchPages) {
        loader.keepLoaded(
            snapshotFlow {
                val visible = state.lazyListState.layoutInfo.visibleItemsInfo
                listWindow(
                    firstVisible = visible.firstOrNull()?.index ?: state.lazyListState.firstVisibleItemIndex,
                    lastVisible = visible.lastOrNull()?.index ?: state.lazyListState.firstVisibleItemIndex,
                    prefetchPages = prefetchPages,
                    pageCount = state.pager.pageCount,
                )
            },
        )
    }
    return loader
}

/**
 * The pages to hold for a pager sitting on [currentPage] and heading for [targetPage].
 *
 * The two neighbourhoods are unioned rather than spanned: `animateScrollToPage` sets the target
 * immediately, so a jump of a thousand pages would otherwise put a thousand ranges in the window —
 * every one of them a request — for a journey that passes through none of them.
 */
private fun pagerWindow(
    currentPage: Int,
    targetPage: Int,
    prefetchPages: Int,
    pageCount: Int,
): Set<Int> {
    val window = LinkedHashSet<Int>()
    listOf(currentPage, targetPage).forEach { centre ->
        window += pagesAround(centre = centre, prefetchPages = prefetchPages, pageCount = pageCount)
    }
    return window
}

/** The items to hold for a lazy list showing [firstVisible]..[lastVisible]. */
private fun listWindow(
    firstVisible: Int,
    lastVisible: Int,
    prefetchPages: Int,
    pageCount: Int,
): Set<Int> {
    val from = (firstVisible - prefetchPages).coerceAtLeast(0)
    val until = (lastVisible + prefetchPages).coerceAtMost(pageCount - 1)
    return if (from > until) emptySet() else (from..until).toSet()
}

private fun pagesAround(centre: Int, prefetchPages: Int, pageCount: Int): List<Int> {
    val from = (centre - prefetchPages).coerceAtLeast(0)
    val until = (centre + prefetchPages).coerceAtMost(pageCount - 1)
    return if (from > until) emptyList() else (from..until).toList()
}
