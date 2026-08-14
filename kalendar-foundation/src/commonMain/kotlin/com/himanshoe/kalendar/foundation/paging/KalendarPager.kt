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

package com.himanshoe.kalendar.foundation.paging

import androidx.compose.runtime.Immutable
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate

/**
 * The two-way map between a scroll container's page index and the date that page shows, plus the
 * bounds that map has to respect.
 *
 * This is everything a `HorizontalPager`, a `LazyRow`, a `ViewPager` or a hand-rolled gesture
 * surface needs to page a calendar, with none of them in it. Build one, ask it for [pageCount] and
 * [initialPage], and answer every "what date is this page?" with [dateAt] and every "scroll me to
 * this date" with [pageOf].
 *
 * ```kotlin
 * val pager = KalendarPager.endless(
 *     unit = KalendarPageUnit.Month,
 *     initialDate = LocalDate(2026, 8, 15),
 * )
 *
 * HorizontalPager(
 *     state = rememberPagerState(pager.initialPage) { pager.pageCount },
 * ) { page ->
 *     MonthGrid(monthStart = pager.dateAt(page))
 * }
 * ```
 *
 * Page indices are non-negative and run `0 until pageCount`, because that is what every scroll
 * container in the ecosystem expects. Which index the calendar's origin lands on is the difference
 * between the two ways to build one — see [endless] and [bounded].
 *
 * @property unit How much of the calendar one page shows.
 * @property startDayOfWeek Which weekday a week page begins on. Read when [unit] is
 *   [KalendarPageUnit.Week]; otherwise carried for the caller's own use.
 * @property minDate The earliest date reachable, or `null` for no lower bound. The page *containing*
 *   it is reachable, so a mid-period bound (the 15th of a month) still allows that whole month.
 * @property maxDate The latest date reachable, or `null` for no upper bound.
 * @property pageCount How many pages the axis has, and so what a scroll container should be told.
 */
@Immutable
public class KalendarPager private constructor(
    public val unit: KalendarPageUnit,
    public val startDayOfWeek: DayOfWeek,
    public val pageCount: Int,
    private val originDate: LocalDate,
    private val originPage: Int,
    initialDate: LocalDate,
    public val minDate: LocalDate? = null,
    public val maxDate: LocalDate? = null,
) {

    /**
     * The lowest page a caller may scroll to: [minDate]'s page when one is set, otherwise `0`.
     *
     * ```kotlin
     * val canGoBack = currentPage > pager.firstPage
     * ```
     */
    public val firstPage: Int = (minDate?.let(::rawPage) ?: 0L)
        .coerceIn(0L, (pageCount - 1).toLong())
        .toInt()

    /** The highest page a caller may scroll to: [maxDate]'s page when one is set, otherwise the last page. */
    public val lastPage: Int = (maxDate?.let(::rawPage) ?: (pageCount - 1).toLong())
        .coerceIn(firstPage.toLong(), (pageCount - 1).toLong())
        .toInt()

    /**
     * The page a container should open on — the page containing the `initialDate` this pager was
     * built with, clamped into `[firstPage, lastPage]` so an initial date outside the bounds opens
     * on the nearest bound rather than off the end of the axis.
     */
    public val initialPage: Int = pageOf(initialDate)

    /**
     * The date keying [page] — the first day of the day/week/month/year that page shows.
     *
     * Out-of-range indices are not rejected; the arithmetic simply continues past the bounds, which
     * keeps a scroll container's overscroll and prefetch queries harmless.
     *
     * ```kotlin
     * val monthStart = pager.dateAt(page)
     * val grid = monthGridDates(monthStart, pager.startDayOfWeek)
     * ```
     */
    public fun dateAt(page: Int): LocalDate = unit.plusPages(originDate, page - originPage)

    /**
     * The page containing [date], clamped into `[firstPage, lastPage]`.
     *
     * Clamping rather than failing is what makes this safe to call with a date a user typed, a
     * "jump to today" that falls outside a bounded calendar, or a saved position from before the
     * bounds changed: the answer is always a page that exists.
     *
     * ```kotlin
     * pagerState.animateScrollToPage(pager.pageOf(today))
     * ```
     */
    public fun pageOf(date: LocalDate): Int =
        rawPage(date).coerceIn(firstPage.toLong(), lastPage.toLong()).toInt()

    /**
     * [page] pulled into `[firstPage, lastPage]` — what to apply to a page index a caller computed
     * themselves, such as `currentPage - 1` for a "previous" button.
     *
     * ```kotlin
     * pagerState.animateScrollToPage(pager.coercePage(pagerState.currentPage + 1))
     * ```
     */
    public fun coercePage(page: Int): Int = page.coerceIn(firstPage, lastPage)

    /**
     * [date]'s page before any clamping, in [Long] arithmetic.
     *
     * The [Long] is load-bearing, not defensive: with only a `maxDate` set the origin sits one below
     * [Int.MAX_VALUE], so a date past the bound overflows [Int] and wraps negative — which a scroll
     * container reads as a valid index near the *start* of the axis and crashes on.
     */
    private fun rawPage(date: LocalDate): Long = originPage.toLong() +
        unit.pagesBetween(from = originDate, to = date, startDayOfWeek = startDayOfWeek).toLong()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is KalendarPager) return false
        if (unit != other.unit) return false
        if (startDayOfWeek != other.startDayOfWeek) return false
        if (minDate != other.minDate) return false
        if (maxDate != other.maxDate) return false
        if (pageCount != other.pageCount) return false
        if (originDate != other.originDate) return false
        if (originPage != other.originPage) return false
        if (initialPage != other.initialPage) return false
        return true
    }

    override fun hashCode(): Int {
        var result = unit.hashCode()
        result = 31 * result + startDayOfWeek.hashCode()
        result = 31 * result + minDate.hashCode()
        result = 31 * result + maxDate.hashCode()
        result = 31 * result + pageCount
        result = 31 * result + originDate.hashCode()
        result = 31 * result + originPage
        result = 31 * result + initialPage
        return result
    }

    override fun toString(): String = "KalendarPager(" +
        "unit=$unit, " +
        "startDayOfWeek=$startDayOfWeek, " +
        "minDate=$minDate, " +
        "maxDate=$maxDate, " +
        "pageCount=$pageCount, " +
        "firstPage=$firstPage, " +
        "lastPage=$lastPage, " +
        "initialPage=$initialPage" +
        ")"

    public companion object {

        /**
         * The page an [endless] axis puts its origin on: the middle of the index space, leaving
         * about a billion pages of travel in each direction.
         */
        public const val CENTER_PAGE: Int = Int.MAX_VALUE / 2

        /**
         * A pager whose axis is always [Int.MAX_VALUE] pages long, with [initialDate]'s page sitting
         * at [CENTER_PAGE].
         *
         * This is what a swipeable pager wants: a scroll container that must be told a page count up
         * front, and should let the user swipe backwards from where they started. [minDate] and
         * [maxDate] still narrow [firstPage]/[lastPage], so [pageOf] and [coercePage] keep every
         * programmatic jump inside them.
         *
         * ```kotlin
         * val pager = KalendarPager.endless(
         *     unit = KalendarPageUnit.Week,
         *     initialDate = today,
         *     startDayOfWeek = DayOfWeek.MONDAY,
         * )
         * pager.dateAt(pager.initialPage) // the Monday of today's week
         * ```
         *
         * @param initialDate The date whose page the axis is centred on and opens at.
         */
        public fun endless(
            unit: KalendarPageUnit,
            initialDate: LocalDate,
            startDayOfWeek: DayOfWeek = DayOfWeek.MONDAY,
            minDate: LocalDate? = null,
            maxDate: LocalDate? = null,
        ): KalendarPager = KalendarPager(
            unit = unit,
            startDayOfWeek = startDayOfWeek,
            minDate = minDate,
            maxDate = maxDate,
            pageCount = Int.MAX_VALUE,
            originDate = unit.startOf(date = initialDate, startDayOfWeek = startDayOfWeek),
            originPage = CENTER_PAGE,
            initialDate = initialDate,
        )

        /**
         * A pager whose axis is exactly as long as [minDate]/[maxDate] allow, anchored so that page
         * `0` is a real page.
         *
         * This is what a lazy list wants: an item count it can size a scrollbar from, and a page `0`
         * that is the first month rather than an arbitrary point a billion pages into nothing. With
         * both bounds set [pageCount] is the exact number of pages between them; with one bound the
         * axis is still [Int.MAX_VALUE] long but anchored at that bound — at the start for a
         * [minDate], at the end for a [maxDate] — so the bounded side is a real edge.
         *
         * ```kotlin
         * val pager = KalendarPager.bounded(
         *     unit = KalendarPageUnit.Month,
         *     initialDate = today,
         *     minDate = LocalDate(2026, 1, 1),
         *     maxDate = LocalDate(2026, 12, 31),
         * )
         * pager.pageCount            // 12
         * pager.dateAt(0)            // 2026-01-01
         * ```
         *
         * @param initialDate The date whose page the axis opens at, clamped into the bounds.
         */
        public fun bounded(
            unit: KalendarPageUnit,
            initialDate: LocalDate,
            startDayOfWeek: DayOfWeek = DayOfWeek.MONDAY,
            minDate: LocalDate? = null,
            maxDate: LocalDate? = null,
        ): KalendarPager {
            val minPageStart = minDate?.let { unit.startOf(date = it, startDayOfWeek = startDayOfWeek) }
            val maxPageStart = maxDate?.let { unit.startOf(date = it, startDayOfWeek = startDayOfWeek) }
            val origin = minPageStart
                ?: maxPageStart
                ?: unit.startOf(date = initialDate, startDayOfWeek = startDayOfWeek)
            val originPage = when {
                minPageStart != null -> 0
                maxPageStart != null -> Int.MAX_VALUE - 1
                else -> CENTER_PAGE
            }
            val pageCount = if (minPageStart != null && maxPageStart != null) {
                // A caller who crosses their own bounds still gets an axis with a page on it,
                // rather than an item count a lazy list would reject.
                (
                    unit.pagesBetween(
                        from = minPageStart,
                        to = maxPageStart,
                        startDayOfWeek = startDayOfWeek,
                    ) + 1
                    ).coerceAtLeast(1)
            } else {
                Int.MAX_VALUE
            }
            return KalendarPager(
                unit = unit,
                startDayOfWeek = startDayOfWeek,
                minDate = minDate,
                maxDate = maxDate,
                pageCount = pageCount,
                originDate = origin,
                originPage = originPage,
                initialDate = initialDate,
            )
        }
    }
}
