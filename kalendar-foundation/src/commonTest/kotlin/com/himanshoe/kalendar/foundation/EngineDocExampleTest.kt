package com.himanshoe.kalendar.foundation

import com.himanshoe.kalendar.foundation.datetime.startOfMonth
import com.himanshoe.kalendar.foundation.event.BasicKalendarEvent
import com.himanshoe.kalendar.foundation.format.KalendarFormatters
import com.himanshoe.kalendar.foundation.format.monthTitle
import com.himanshoe.kalendar.foundation.grid.daysOfWeekStartingAt
import com.himanshoe.kalendar.foundation.grid.monthGridDates
import com.himanshoe.kalendar.foundation.paging.KalendarPageUnit
import com.himanshoe.kalendar.foundation.paging.KalendarPager
import com.himanshoe.kalendar.foundation.schedule.KalendarHourWindow
import com.himanshoe.kalendar.foundation.schedule.blockPlacement
import com.himanshoe.kalendar.foundation.schedule.scheduleBlocks
import com.himanshoe.kalendar.foundation.selection.KalendarSelection
import com.himanshoe.kalendar.foundation.selection.KalendarSelectionMode
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
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
}
