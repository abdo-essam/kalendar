package com.himanshoe.kalendar.foundation

import com.himanshoe.kalendar.foundation.datetime.startOfMonth
import com.himanshoe.kalendar.foundation.event.BasicKalendarEvent
import com.himanshoe.kalendar.foundation.event.KalendarEvent
import com.himanshoe.kalendar.foundation.event.byDateExpandingSpans
import com.himanshoe.kalendar.foundation.format.KalendarFormatters
import com.himanshoe.kalendar.foundation.format.monthTitle
import com.himanshoe.kalendar.foundation.grid.ALL_DAYS_OF_WEEK
import com.himanshoe.kalendar.foundation.grid.daysOfWeekStartingAt
import com.himanshoe.kalendar.foundation.grid.monthGridDates
import com.himanshoe.kalendar.foundation.paging.KalendarPageUnit
import com.himanshoe.kalendar.foundation.paging.KalendarPager
import com.himanshoe.kalendar.foundation.schedule.KalendarHourWindow
import com.himanshoe.kalendar.foundation.schedule.KalendarScheduleDragMode
import com.himanshoe.kalendar.foundation.schedule.MINUTES_PER_HOUR
import com.himanshoe.kalendar.foundation.schedule.adjustedScheduleBlockForDrag
import com.himanshoe.kalendar.foundation.schedule.blockPlacement
import com.himanshoe.kalendar.foundation.schedule.dragReleaseTimes
import com.himanshoe.kalendar.foundation.schedule.initialScrollHourOffset
import com.himanshoe.kalendar.foundation.schedule.minutesFromMidnight
import com.himanshoe.kalendar.foundation.schedule.scheduleBlocks
import com.himanshoe.kalendar.foundation.schedule.scheduleDragMode
import com.himanshoe.kalendar.foundation.schedule.snappedDragMinutes
import com.himanshoe.kalendar.foundation.selection.KalendarSelection
import com.himanshoe.kalendar.foundation.selection.KalendarSelectionMode
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The worked examples from `docs/engine.md`, compiled and asserted.
 *
 * Documentation that has drifted from the API is worse than none, because a reader trusts it. Every
 * snippet on that page exists here so a signature change breaks the build rather than the docs.
 */
class EngineDocExampleTest {

    @Test
    fun buildingAMonthGrid() {
        val startDayOfWeek = DayOfWeek.MONDAY
        val monthStart = LocalDate(2026, 8, 1).startOfMonth()

        val columnLabels = daysOfWeekStartingAt(startDayOfWeek = startDayOfWeek)
        val dates = monthGridDates(monthStart = monthStart, startDayOfWeek = startDayOfWeek)

        assertEquals(7, columnLabels.size)
        assertEquals(DayOfWeek.MONDAY, columnLabels.first())

        // The grid pads the leading row so the 1st lands under its real weekday, and stops at the
        // month's last day rather than padding the trailing row. A renderer that assumes whole rows
        // has to place the tail itself — which is why this is asserted rather than described.
        assertEquals(columnLabels.first(), dates.first().dayOfWeek, "first cell is the start column")
        assertTrue(dates.first() <= monthStart, "leads in with the previous month's tail")
        assertEquals(LocalDate(2026, 8, 31), dates.last(), "stops on the month's last day")
        assertTrue(dates.size % columnLabels.size != 0, "trailing row is not padded")
        assertEquals("August 2026", monthTitle(monthStart, KalendarFormatters.monthName))

        // The exact figures the guide quotes, so a change to the padding rule fails here.
        assertEquals(LocalDate(2026, 7, 27), dates.first())
        assertEquals(GRID_CELLS_AUGUST_2026, dates.size)

        // How a renderer squares the short final row: chunk, then pad what is missing.
        val rows = dates.chunked(columnLabels.size)
        assertTrue(rows.last().size < columnLabels.size, "the final row is short")
        assertEquals(columnLabels.size, rows.first().size, "every other row is whole")
    }

    @Test
    fun buildingAWorkWeekGrid() {
        val workWeek = ALL_DAYS_OF_WEEK - setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
        val monthStart = LocalDate(2026, 8, 1).startOfMonth()

        val labels = daysOfWeekStartingAt(
            startDayOfWeek = DayOfWeek.MONDAY,
            visibleDaysOfWeek = workWeek,
        )
        val cells = monthGridDates(
            monthStart = monthStart,
            startDayOfWeek = DayOfWeek.MONDAY,
            visibleDaysOfWeek = workWeek,
        )

        assertEquals(workWeek.size, labels.size)
        assertTrue(cells.none { it.dayOfWeek in setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY) })
        assertEquals(labels.first(), cells.first().dayOfWeek, "columns stay under their labels")
    }

    @Test
    fun drivingYourOwnPager() {
        val pager = KalendarPager.endless(
            unit = KalendarPageUnit.Month,
            initialDate = LocalDate(2026, 8, 14),
        )

        val page = pager.initialPage
        assertEquals(LocalDate(2026, 8, 1), pager.dateAt(page).startOfMonth())
        assertEquals(LocalDate(2026, 9, 1), pager.dateAt(page + 1).startOfMonth())
        assertEquals(page, pager.pageOf(LocalDate(2026, 8, 31)), "any day maps to its month's page")

        assertEquals(Int.MAX_VALUE, pager.pageCount, "an endless axis is the whole index space")
        assertEquals(KalendarPager.CENTER_PAGE, page, "and it opens in the middle of it")
    }

    @Test
    fun steppingPagesWithoutAPager() {
        val unit = KalendarPageUnit.Month
        val monthStart = unit.startOf(LocalDate(2026, 8, 15))

        assertEquals(LocalDate(2026, 8, 1), monthStart)
        assertEquals(LocalDate(2026, 9, 1), unit.plusPages(monthStart, 1))
        assertEquals(
            MONTHS_TO_FEBRUARY_2027,
            unit.pagesBetween(from = monthStart, to = LocalDate(2027, 2, 3)),
        )
    }

    @Test
    fun boundedPagerClampsToItsRange() {
        val pager = KalendarPager.bounded(
            unit = KalendarPageUnit.Month,
            initialDate = LocalDate(2026, 8, 14),
            minDate = LocalDate(2026, 8, 1),
            maxDate = LocalDate(2026, 10, 31),
        )
        assertEquals(pager.lastPage, pager.coercePage(pager.lastPage + 5))
        assertEquals(pager.firstPage, pager.coercePage(pager.firstPage - 5))

        // A lazy list wants both of these: a real item count, and a page 0 that is the first month.
        assertEquals(BOUNDED_MONTH_PAGES, pager.pageCount)
        assertEquals(LocalDate(2026, 8, 1), pager.dateAt(0))
    }

    @Test
    fun layingOutADayView() {
        val date = LocalDate(2026, 8, 14)
        val events = listOf(
            BasicKalendarEvent(
                date = date,
                eventName = "Standup",
                startTime = LocalDateTime(2026, 8, 14, 9, 0),
                endTime = LocalDateTime(2026, 8, 14, 9, 30),
            ),
            BasicKalendarEvent(
                date = date,
                eventName = "Review",
                startTime = LocalDateTime(2026, 8, 14, 9, 15),
                endTime = LocalDateTime(2026, 8, 14, 10, 0),
            ),
        )

        val window = KalendarHourWindow(startHour = 8, endHour = 18)
        val blocks = scheduleBlocks(events = events, date = date, window = window)

        assertEquals(2, blocks.size)
        assertTrue(blocks.all { it.columns == 2 }, "overlapping events share the width")
        assertEquals(setOf(0, 1), blocks.map { it.column }.toSet())

        val placement = blockPlacement(block = blocks.first(), window = window)
        assertEquals(60, placement.topMinutes, "09:00 is 60 minutes into an 08:00 window")
        assertEquals(30, placement.heightMinutes)

        assertEquals(WINDOW_HOUR_COUNT, window.hourCount, "ten rows to draw")
        assertEquals(WINDOW_START_MINUTES, window.startMinutes)

        // The scroll hour is absolute, so one already above the window opens the grid at its top.
        assertEquals(0, initialScrollHourOffset(initialScrollHour = 7, window = window))
        assertEquals(
            WINDOW_START_MINUTES + 30,
            LocalDateTime(2026, 8, 14, 8, 30).minutesFromMidnight(),
        )
    }

    @Test
    fun draggingABlockAndReportingItsNewTimes() {
        val date = LocalDate(2026, 8, 14)
        val window = KalendarHourWindow(startHour = 8, endHour = 18)
        val block = scheduleBlocks(
            events = listOf(
                BasicKalendarEvent(
                    date = date,
                    eventName = "Standup",
                    startTime = LocalDateTime(2026, 8, 14, 9, 0),
                    endTime = LocalDateTime(2026, 8, 14, 9, 30),
                ),
            ),
            date = date,
            window = window,
        ).single()

        val mode = scheduleDragMode(
            pressYPx = BLOCK_HEIGHT_PX / 2,
            blockHeightPx = BLOCK_HEIGHT_PX,
            resizeHandlePx = RESIZE_HANDLE_PX,
        )
        assertEquals(KalendarScheduleDragMode.Move, mode, "a press in the middle moves the block")

        val delta = snappedDragMinutes(
            rawPx = ONE_HOUR_PX,
            minutePx = MINUTE_PX,
            snapMinutes = SNAP_MINUTES,
        )
        assertEquals(MINUTES_PER_HOUR, delta, "an hour of travel is an hour of time")

        val moved = adjustedScheduleBlockForDrag(
            block = block,
            deltaMinutes = delta,
            mode = mode,
            window = window,
        )
        val (start, end) = dragReleaseTimes(block = moved, targetDate = date)
        assertEquals(LocalDateTime(2026, 8, 14, 10, 0), start)
        assertEquals(LocalDateTime(2026, 8, 14, 10, 30), end, "the duration is preserved")
    }

    @Test
    fun reducingASelection() {
        val mode = KalendarSelectionMode.Multiple
        var selection = KalendarSelection.Empty

        selection = selection.afterClick(mode, LocalDate(2026, 8, 14))
        selection = selection.afterClick(mode, LocalDate(2026, 8, 15))
        assertEquals(2, selection.dates.size)

        selection = selection.afterClick(mode, LocalDate(2026, 8, 14))
        assertEquals(setOf(LocalDate(2026, 8, 15)), selection.dates, "clicking again deselects")

        val single = KalendarSelection.Empty
            .afterClick(KalendarSelectionMode.Single, LocalDate(2026, 8, 14))
            .afterClick(KalendarSelectionMode.Single, LocalDate(2026, 8, 20))
        assertEquals(setOf(LocalDate(2026, 8, 20)), single.dates, "single mode replaces")
    }

    @Test
    fun sweepingARangeAndFinishingIt() {
        val anchor = LocalDate(2026, 8, 14)

        var selection = KalendarSelection.Empty.afterClick(KalendarSelectionMode.Range, anchor)
        assertEquals(anchor, selection.pendingRangeStart, "the first tap opens a range")

        selection = selection.afterRangeDrag(anchor, LocalDate(2026, 8, 16))
        assertEquals(RANGE_SWEEP_DAYS, selection.dates.size, "the sweep is inclusive at both ends")

        selection = selection.afterRangeDrag(anchor, LocalDate(2026, 8, 12))
        assertEquals(
            RANGE_SWEEP_DAYS,
            selection.dates.size,
            "sweeping backwards reads the same as forwards",
        )

        selection = selection.afterRangeDragEnd()
        assertNull(selection.pendingRangeStart, "so the next tap starts a fresh range")
    }

    @Test
    fun indexingEventsByEveryDateTheyOccupy() {
        val conference = BasicKalendarEvent(
            date = LocalDate(2026, 8, 14),
            endDate = LocalDate(2026, 8, 16),
            eventName = "Compose conference",
        )
        val booking = Booking(
            bookingId = 1,
            room = "Room A",
            guest = "Ada",
            from = LocalDateTime(2026, 8, 14, 9, 0),
            to = LocalDateTime(2026, 8, 14, 10, 0),
        )

        val byDate = listOf(conference, booking).byDateExpandingSpans()

        assertEquals(SPAN_DAYS, byDate.keys.size, "a three-day span occupies three dates")
        assertTrue(conference in byDate.getValue(LocalDate(2026, 8, 16)), "including its last day")
        assertEquals(2, byDate.getValue(LocalDate(2026, 8, 14)).size, "both events on the first day")
    }

    /** A caller's own domain type implementing [KalendarEvent], as the guide's example does. */
    private class Booking(
        val bookingId: Long,
        val room: String,
        val guest: String,
        val from: LocalDateTime,
        val to: LocalDateTime,
    ) : KalendarEvent {
        override val id: String get() = bookingId.toString()
        override val date: LocalDate get() = from.date
        override val eventName: String get() = guest
        override val eventDescription: String get() = room
        override val startTime: LocalDateTime get() = from
        override val endTime: LocalDateTime get() = to
    }

    private companion object {

        /** July 27th through August 31st: five padded days plus the month's own thirty-one. */
        const val GRID_CELLS_AUGUST_2026 = 36

        /** August 2026 to February 2027, counted between month starts. */
        const val MONTHS_TO_FEBRUARY_2027 = 6

        /** August, September and October — the three months the bounded pager's range covers. */
        const val BOUNDED_MONTH_PAGES = 3

        /** 08:00 through 18:00, exclusive at the end. */
        const val WINDOW_HOUR_COUNT = 10

        /** 08:00 as minutes from midnight. */
        const val WINDOW_START_MINUTES = 480

        /** Tall enough that a press at its centre is nowhere near either resize strip. */
        const val BLOCK_HEIGHT_PX = 120f

        /** The nominal grab strip at each edge of a block. */
        const val RESIZE_HANDLE_PX = 12f

        /** A 64px hour, the built-in `KalendarDimensions.hourHeight`, expressed per minute. */
        const val MINUTE_PX = 64f / MINUTES_PER_HOUR

        /** One hour of vertical travel at [MINUTE_PX]. */
        const val ONE_HOUR_PX = 64f

        /** The built-in `KalendarViewConfig.scheduleDragSnapMinutes`. */
        const val SNAP_MINUTES = 15

        /** The 14th through the 16th, inclusive. */
        const val RANGE_SWEEP_DAYS = 3

        /** The same three dates, as map keys. */
        const val SPAN_DAYS = 3
    }
}
