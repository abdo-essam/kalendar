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

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.use
import com.himanshoe.kalendar.foundation.event.BasicKalendarEvent
import com.himanshoe.kalendar.foundation.event.KalendarEvents
import com.himanshoe.kalendar.theme.KalendarAnimations
import com.himanshoe.kalendar.theme.KalendarTheme
import com.himanshoe.kalendar.theme.KalendarThemeDefaults
import kotlinx.datetime.LocalDate
import org.jetbrains.skia.EncodedImageFormat
import org.junit.Rule
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * What a multi-day event actually draws, read back off a rendered frame.
 *
 * Every assertion here is on pixels rather than on semantics, because there is nothing in the
 * semantics tree that can tell a continuous bar from three dots — the whole feature is geometry.
 * The goldens cannot stand in either: their tolerance is 1% of the frame, and a span bar is around
 * 0.3% of one, so a bar could break in half without moving them.
 *
 * The fixtures render a March 2026 month at density 1, 420px wide, which makes every column exactly
 * [COLUMN_WIDTH] and lets a cell be named by arithmetic rather than by a magic offset. March 2026
 * starts on a Sunday and the grid starts on a Monday, so the week of the 9th is row [EVENT_ROW],
 * runs Monday the 9th to Sunday the 15th, and is the only row the fixtures put anything in.
 */
class KalendarSpanBarUiTest {

    @get:Rule
    val rule = createComposeRule()

    /** Monday the 9th to Friday the 13th: five days inside one week. */
    private val conference = BasicKalendarEvent(
        date = march(CONFERENCE_FIRST),
        endDate = march(CONFERENCE_LAST),
        eventName = "Conference",
        eventColor = LANE_ONE_INK,
    )

    /** Tuesday the 10th to Thursday the 12th: overlaps the conference on three of its days. */
    private val workshop = BasicKalendarEvent(
        date = march(WORKSHOP_FIRST),
        endDate = march(WORKSHOP_LAST),
        eventName = "Workshop",
        eventColor = LANE_TWO_INK,
    )

    /** Thursday the 12th to Tuesday the 17th: crosses the end of a week. */
    private val wrapping = BasicKalendarEvent(
        date = march(WRAPPING_FIRST),
        endDate = march(WRAPPING_LAST),
        eventName = "Retreat",
        eventColor = LANE_ONE_INK,
    )

    /**
     * The headline: five days of one event are one bar, not five dots.
     *
     * "Unbroken" is the assertion that matters and it is why the run is read at a single y — a bar
     * drawn per cell inside the day's padding would leave a 4px hole on every cell boundary and
     * still look plausible in a screenshot.
     */
    @Test
    fun aMultiDayEvent_drawsOneUnbrokenBarAcrossTheDaysItCovers() {
        val frame = renderMonth(listOf(conference))

        val bands = frame.bandsOf(LANE_ONE_INK)
        assertEquals(1, bands.size, "one span confined to one week must draw exactly one bar")

        val runs = frame.runsAt(bands.single().middle(), LANE_ONE_INK)
        assertEquals(1, runs.size, "the bar must be continuous across the cell boundaries: $runs")
        assertTrue(
            runs.single().first in columnStart(MONDAY)..(columnStart(MONDAY) + END_INSET_LIMIT),
            "the bar must start inside Monday the 9th, was ${runs.single()}",
        )
        assertTrue(
            runs.single().last in (columnEnd(FRIDAY) - END_INSET_LIMIT)..columnEnd(FRIDAY),
            "the bar must end inside Friday the 13th, was ${runs.single()}",
        )
    }

    /**
     * The distinction the feature turns on: a bar cut off by the end of a week has to read
     * differently from one that has actually finished.
     *
     * A wrap runs into the grid's edge and out the other side on the next row; a real end stops
     * short of the edge by [com.himanshoe.kalendar.theme.KalendarDimensions.spanBarEndInset]. Both
     * halves are asserted on the same event so neither can be satisfied by a bar that always looks
     * the same.
     */
    @Test
    fun aBarCutByTheWeek_runsToTheEdgeWhereARealEndStopsShort() {
        val frame = renderMonth(listOf(wrapping))

        val bands = frame.bandsOf(LANE_ONE_INK)
        assertEquals(2, bands.size, "a Thursday-to-Tuesday event is two bars, one per week")

        val firstWeek = frame.runsAt(bands[0].middle(), LANE_ONE_INK).single()
        val secondWeek = frame.runsAt(bands[1].middle(), LANE_ONE_INK).single()

        assertTrue(
            firstWeek.first > columnStart(THURSDAY),
            "the event's real start must be inset from Thursday's edge, was $firstWeek",
        )
        assertEquals(
            WIDTH - 1,
            firstWeek.last,
            "a bar continuing into the next week must run to the grid's edge",
        )
        assertEquals(
            0,
            secondWeek.first,
            "the continuation must start at the grid's edge, not inset like a real start",
        )
        assertTrue(
            secondWeek.last < columnEnd(TUESDAY),
            "the event's real end must stop short of Tuesday's edge, was $secondWeek",
        )
    }

    /**
     * Two overlapping spans stack, and each one holds its lane for its whole run.
     *
     * Asserting that each colour occupies exactly one horizontal band is the direct form of that:
     * a lane that shifted when the span above it ended would put the same colour in two bands.
     */
    @Test
    fun overlappingSpans_stackAndEachHoldsOneLane() {
        val frame = renderMonth(listOf(conference, workshop))

        val upper = frame.bandsOf(LANE_ONE_INK)
        val lower = frame.bandsOf(LANE_TWO_INK)
        assertEquals(1, upper.size, "the first span must stay in one lane for its whole run")
        assertEquals(1, lower.size, "the second span must stay in one lane for its whole run")
        assertTrue(
            upper.single().last < lower.single().first,
            "the spans must stack rather than overlap: ${upper.single()} vs ${lower.single()}",
        )

        assertEquals(
            1,
            frame.runsAt(lower.single().middle(), LANE_TWO_INK).size,
            "the lower bar must be continuous too",
        )
    }

    /**
     * A span past the lane budget is not dropped: it falls back to the indicator dot it drew before
     * span bars existed. Width is what tells the two apart — a bar covers most of a cell, a dot is
     * [com.himanshoe.kalendar.theme.KalendarDimensions.eventIndicatorSize] across.
     */
    @Test
    fun aSpanPastTheLaneBudget_fallsBackToDots() {
        val third = BasicKalendarEvent(
            date = march(11),
            endDate = march(14),
            eventName = "Third",
            eventColor = OVERFLOW_INK,
        )
        val frame = renderMonth(listOf(conference, workshop, third))

        val bands = frame.bandsOf(OVERFLOW_INK)
        assertEquals(1, bands.size, "the third span must still be drawn")
        val widest = frame.runsAt(bands.single().middle(), OVERFLOW_INK).maxOf { it.count() }
        assertTrue(
            widest <= DOT_WIDTH_LIMIT,
            "past the budget the event must degrade to dots, not draw a $widest px bar",
        )
    }

    /**
     * The invariant the whole feature rests on: **every event on a day is drawn as exactly one of a
     * bar or a dot, at every cell size** — never as neither.
     *
     * The lane budget is a function of the cell's *height*, and a cell is as tall as it is wide, so
     * the sweep is over widths: [LANE_BUDGET_SWEEP] runs from a cell that affords both lanes, through
     * one that affords a single lane, to one that affords none, down to one with no room for the
     * strip at all. A cell that drew a bar for the first span and nothing whatsoever for the second
     * is what this forbids, and it is what a month grid at phone width used to produce.
     *
     * [DOT_INK]'s single-day event is the vacuity guard. The smallest cell here drops the strip
     * whole and draws no indicator of any kind, and asserting that the spans are missing from a
     * frame like that would prove nothing — so a frame with no dot ink in it is not asserted on, and
     * [assertTrue] at the end insists that some frame did carry it.
     */
    @Test
    fun atEveryCellSize_aSpanTheCellCannotBar_isDrawnAsADotInstead() {
        val solo = BasicKalendarEvent(
            date = march(WEDNESDAY_DAY),
            eventName = "Solo",
            eventColor = DOT_INK,
        )
        var asserted = 0
        LANE_BUDGET_SWEEP.forEach { width ->
            val frame = renderMonth(listOf(conference, workshop, solo), width = width)
            if (frame.bandsOf(DOT_INK).isEmpty()) {
                return@forEach
            }
            asserted++
            assertTrue(
                frame.bandsOf(LANE_ONE_INK).isNotEmpty(),
                "at width $width the first span was drawn as neither a bar nor a dot",
            )
            assertTrue(
                frame.bandsOf(LANE_TWO_INK).isNotEmpty(),
                "at width $width the second span was drawn as neither a bar nor a dot",
            )
        }
        assertTrue(asserted > 1, "only $asserted of the swept widths drew any indicator at all")
    }

    /** Bars and the single-day dots the cell has always drawn share the strip, bars above. */
    @Test
    fun spanBarsAndSingleDayDots_coexistInOneCell() {
        val solo = BasicKalendarEvent(date = march(10), eventName = "Solo", eventColor = DOT_INK)
        val frame = renderMonth(listOf(conference, solo))

        val bar = frame.bandsOf(LANE_ONE_INK).single()
        val dot = frame.bandsOf(DOT_INK).single()
        assertTrue(bar.last < dot.first, "dots must sit below the bars, not on them: $bar vs $dot")
        assertTrue(
            frame.runsAt(dot.middle(), DOT_INK).single().let { it.first > columnStart(TUESDAY) },
            "the dot must be drawn in Tuesday the 10th's cell",
        )
    }

    /**
     * The constraint span bars had to be built without breaking: every day number in a grid sits at
     * one height, whatever its day holds.
     *
     * The row measured carries, left to right, a day with one bar, a day with two bars, a day with
     * two bars *and* a full set of dots plus an overflow label, two more days of bars, a day with a
     * single dot, and an empty day. Before the strip was reserved unconditionally that spread was
     * 10px; the tolerance here is the 1px that glyph shape alone accounts for.
     */
    @Test
    fun everyDayNumberInARow_sharesOneBaseline() {
        val busy = march(11)
        val frame = renderMonth(
            listOf(conference, workshop) +
                List(FULL_DOT_ROW) { index ->
                    BasicKalendarEvent(date = busy, eventName = "Meeting $index", eventColor = DOT_INK)
                } +
                BasicKalendarEvent(date = march(14), eventName = "Solo", eventColor = DOT_INK),
        )

        val tops = frame.dayNumberTops(row = EVENT_ROW)
        assertEquals(COLUMNS, tops.size, "every day in the row must have drawn its number")
        assertTrue(
            tops.max() - tops.min() <= BASELINE_TOLERANCE,
            "day numbers in one row must share a baseline, tops were $tops",
        )
    }

    /**
     * The baseline guarantee with the glyphs held still, so the answer is exact rather than within
     * [BASELINE_TOLERANCE].
     *
     * [everyDayNumberInARow_sharesOneBaseline] compares seven *different* digits, and the topmost
     * lit pixel of a "4" is genuinely not the topmost lit pixel of an "8" — which is why it carries a
     * 1px tolerance, and why a real 1px layout drift could hide behind it. This renders the same grid
     * twice and moves only the events: the column measured draws the same digit at the same size in
     * both frames, and holds five dots and an overflow label in one and nothing in the other. Any
     * difference at all is then the layout, and the assertion is to the pixel.
     */
    @Test
    fun aDayNumbersHeight_doesNotDependOnWhatItsOwnDayHolds() {
        val spans = listOf(conference, workshop)
        fun dotsOn(day: Int) = List(FULL_DOT_ROW) { index ->
            BasicKalendarEvent(date = march(day), eventName = "Meeting $index", eventColor = DOT_INK)
        }

        // Wednesday the 11th carries the dots in one frame and Sunday the 15th in the other. Both
        // days are inside the span rows, so the grid reserves the same two lanes either way.
        val loadedFrame = renderMonth(spans + dotsOn(WEDNESDAY_DAY))
        val bareFrame = renderMonth(spans + dotsOn(SUNDAY_DAY))
        val loaded = loadedFrame.dayNumberTops(row = EVENT_ROW)
        val bare = bareFrame.dayNumberTops(row = EVENT_ROW)

        // Vacuity guard: unless the dots really did move between the two frames, the two numbers
        // below are the same number twice and the assertion proves nothing.
        assertTrue(
            loadedFrame.dotColumns().contains(WEDNESDAY) && !bareFrame.dotColumns().contains(WEDNESDAY),
            "the dots did not move off Wednesday: ${loadedFrame.dotColumns()} vs ${bareFrame.dotColumns()}",
        )

        assertEquals(
            bare[WEDNESDAY],
            loaded[WEDNESDAY],
            "the 11th's number moved when its own day filled up: $bare vs $loaded",
        )
        assertEquals(
            bare[SUNDAY],
            loaded[SUNDAY],
            "the 15th's number moved when its own day emptied: $bare vs $loaded",
        )
    }

    /**
     * The same guarantee across rows, which is what makes the lane count a property of the grid
     * rather than of the day: the week holding the spans must not sit lower than an empty one.
     */
    @Test
    fun aRowWithSpans_keepsTheSameBaselineAsARowWithout() {
        val frame = renderMonth(listOf(conference, workshop))

        val withSpans = frame.dayNumberTops(row = EVENT_ROW)
        val empty = frame.dayNumberTops(row = EVENT_ROW + 1)
        val offset = (withSpans.min() - empty.min()) % ROW_HEIGHT
        assertTrue(
            abs(offset) <= BASELINE_TOLERANCE,
            "the row with spans is $offset px off the row without: $withSpans vs $empty",
        )
    }

    /**
     * Span support is not paid for by calendars that do not use it.
     *
     * No multi-day event means no lanes, which must leave the day number exactly where it was —
     * asserted against the same grid rendered with no events at all, to the pixel.
     */
    @Test
    fun singleDayEventsAlone_reserveNoLaneAndMoveNothing() {
        val dotted = renderMonth(
            listOf(BasicKalendarEvent(date = march(11), eventName = "Solo", eventColor = DOT_INK)),
        )
        val bare = renderMonth(emptyList())

        assertEquals(
            bare.dayNumberTops(row = EVENT_ROW),
            dotted.dayNumberTops(row = EVENT_ROW),
            "a calendar with no multi-day events must lay out exactly as it did before span bars",
        )
    }

    /**
     * A cell too short for the strip drops it whole, bars included, rather than drawing over the
     * number — the rule a year view's cells have always hit. What must survive is the baseline: the
     * numbers stay level with each other whatever was dropped.
     */
    @Test
    fun aCellTooShortForTheStrip_dropsTheBarsAndKeepsTheBaseline() {
        val frame = renderMonth(listOf(conference, workshop), width = CRAMPED_WIDTH)

        assertEquals(
            emptyList(),
            frame.bandsOf(LANE_ONE_INK),
            "a cell with no room for the strip must not draw a bar over its day number",
        )
        val tops = frame.dayNumberTops(row = EVENT_ROW, columnWidth = CRAMPED_WIDTH / COLUMNS)
        assertTrue(
            tops.max() - tops.min() <= BASELINE_TOLERANCE,
            "dropping the strip must not tilt the row, tops were $tops",
        )
    }

    /** A year view stacks month grids of the same shape, so its cells draw the same bars. */
    @Test
    fun theYearView_drawsSpanBarsToo() {
        val frame = render(width = WIDTH, height = YEAR_HEIGHT) {
            KalendarYear(selectedDate = march(24), events = listOf(conference, workshop))
        }

        assertEquals(
            1,
            frame.bandsOf(LANE_ONE_INK).size,
            "the year view's March grid must draw the span as one bar",
        )
    }

    /**
     * An event drawn as a bar is shown, so it must not also be counted as one the cell could not
     * show: three single-day events beside a span still fit the three-dot budget.
     */
    @Test
    fun aBarDoesNotSpendADotSlot() {
        rule.setContent {
            Themed {
                KalendarMonth(
                    selectedDate = march(24),
                    events = listOf(conference) +
                        List(DEFAULT_DOT_BUDGET) { index ->
                            BasicKalendarEvent(date = march(10), eventName = "Meeting $index")
                        },
                )
            }
        }

        rule.onNodeWithText(ONE_HIDDEN_LABEL).assertDoesNotExist()
    }

    /** The control for the assertion above: one more single-day event does overflow. */
    @Test
    fun aFourthDotBesideABar_stillOverflows() {
        rule.setContent {
            Themed {
                KalendarMonth(
                    selectedDate = march(24),
                    events = listOf(conference) +
                        List(DEFAULT_DOT_BUDGET + 1) { index ->
                            BasicKalendarEvent(date = march(10), eventName = "Meeting $index")
                        },
                )
            }
        }

        rule.onNodeWithText(ONE_HIDDEN_LABEL).assertIsDisplayed()
    }

    @Composable
    private fun Themed(content: @Composable () -> Unit) {
        MaterialTheme(colorScheme = lightColorScheme()) {
            KalendarTheme(
                // A day number nothing else on screen shares, so a pixel of it is unambiguous.
                colors = KalendarThemeDefaults.colors().copy(dayContent = DAY_NUMBER_INK),
                animations = KalendarAnimations(enabled = false),
                content = content,
            )
        }
    }

    private fun renderMonth(events: KalendarEvents, width: Int = WIDTH): Frame =
        render(width = width, height = HEIGHT) {
            // Selected away from the row under test: a selected day draws its number in
            // onSelectionBackground, which would not answer to the day-number probe.
            KalendarMonth(selectedDate = march(24), events = events)
        }

    private fun render(width: Int, height: Int, content: @Composable () -> Unit): Frame {
        val png = ImageComposeScene(width = width, height = height, density = Density(1f)) {
            Themed(content)
        }.use { scene ->
            scene.render(nanoTime = 0L)
            val settled = scene.render(nanoTime = SETTLE_FRAME_NANOS)
            checkNotNull(settled.encodeToData(EncodedImageFormat.PNG)) { "PNG encode failed" }.bytes
        }
        val image = ImageIO.read(png.inputStream())
        val argb = IntArray(image.width * image.height)
        for (y in 0 until image.height) {
            for (x in 0 until image.width) {
                argb[y * image.width + x] = image.getRGB(x, y)
            }
        }
        return Frame(argb = argb, width = image.width, height = image.height)
    }

    /** A rendered frame, asked about where a given colour ended up. */
    private class Frame(private val argb: IntArray, val width: Int, val height: Int) {

        /**
         * The horizontal bands [color] occupies, top to bottom — one per row of the grid it appears
         * in. Rows of pixels are grouped while they stay adjacent, so a 4px bar is one band and the
         * same bar a week later is another.
         */
        fun bandsOf(color: Color): List<IntRange> {
            val rows = (0 until height).filter { y -> (0 until width).any { x -> matches(x, y, color) } }
            return rows.fold(mutableListOf<IntRange>()) { bands, y ->
                val last = bands.lastOrNull()
                if (last != null && y == last.last + 1) {
                    bands[bands.lastIndex] = last.first..y
                } else {
                    bands.add(y..y)
                }
                bands
            }
        }

        /** Which columns of the grid have any indicator-dot ink in them at all. */
        fun dotColumns(): Set<Int> = (0 until height)
            .flatMap { y -> runsAt(y, DOT_INK) }
            .mapTo(mutableSetOf()) { run -> run.first / COLUMN_WIDTH }

        /** The runs of adjacent [color] pixels along row [y], left to right. */
        fun runsAt(y: Int, color: Color): List<IntRange> {
            val runs = mutableListOf<IntRange>()
            var start = -1
            for (x in 0 until width) {
                val hit = matches(x, y, color)
                if (hit && start < 0) {
                    start = x
                } else if (!hit && start >= 0) {
                    runs.add(start until x)
                    start = -1
                }
            }
            if (start >= 0) {
                runs.add(start until width)
            }
            return runs
        }

        /**
         * The topmost row of day-number ink in each column of grid row [row], counting rows by the
         * bands the day numbers themselves form rather than by an offset into the frame — the
         * header's height is not this test's business.
         */
        fun dayNumberTops(row: Int, columnWidth: Int = COLUMN_WIDTH): List<Int> {
            val band = bandsOf(DAY_NUMBER_INK)[row]
            return (0 until COLUMNS).mapNotNull { column ->
                val columns = (column * columnWidth) until ((column + 1) * columnWidth)
                band.firstOrNull { y -> columns.any { x -> matches(x, y, DAY_NUMBER_INK) } }
            }
        }

        private fun matches(x: Int, y: Int, color: Color): Boolean {
            val pixel = argb[y * width + x]
            val target = color.toArgbInt()
            return CHANNEL_SHIFTS.all { shift ->
                val a = (pixel shr shift) and CHANNEL_MASK
                val b = (target shr shift) and CHANNEL_MASK
                abs(a - b) <= CHANNEL_TOLERANCE
            }
        }
    }

    private companion object {
        fun march(day: Int): LocalDate = LocalDate(FIXTURE_YEAR, FIXTURE_MONTH, day)

        fun IntRange.middle(): Int = (first + last) / 2

        fun columnStart(column: Int): Int = column * COLUMN_WIDTH

        fun columnEnd(column: Int): Int = (column + 1) * COLUMN_WIDTH - 1

        fun Color.toArgbInt(): Int {
            val alpha = (alpha * MAX_CHANNEL).toInt()
            val red = (red * MAX_CHANNEL).toInt()
            val green = (green * MAX_CHANNEL).toInt()
            val blue = (blue * MAX_CHANNEL).toInt()
            return (alpha shl ALPHA_SHIFT) or (red shl RED_SHIFT) or (green shl GREEN_SHIFT) or blue
        }

        const val FIXTURE_YEAR = 2026
        const val FIXTURE_MONTH = 3
        const val CONFERENCE_FIRST = 9
        const val CONFERENCE_LAST = 13
        const val WORKSHOP_FIRST = 10
        const val WORKSHOP_LAST = 12
        const val WRAPPING_FIRST = 12
        const val WRAPPING_LAST = 17

        const val WIDTH = 420
        const val HEIGHT = 480
        const val YEAR_HEIGHT = 1500
        const val COLUMNS = 7
        const val COLUMN_WIDTH = WIDTH / COLUMNS
        const val ROW_HEIGHT = COLUMN_WIDTH

        /** Narrow enough that the indicator strip is more than half a cell and has to be dropped. */
        const val CRAMPED_WIDTH = 210

        /** Wide enough for the dot strip, too short by 2px for even one span lane above it. */
        const val NO_LANE_WIDTH = 280

        /** Room for the dot strip and exactly one of the two lanes the fixtures ask for. */
        const val ONE_LANE_WIDTH = 350

        /**
         * The four cell sizes the lane budget behaves differently at, widest first: two lanes, one
         * lane, none, and no strip at all. A cell is square, so a width fixes a height — at density
         * 1 a column of [WIDTH]/[COLUMNS] leaves that minus
         * [com.himanshoe.kalendar.theme.KalendarDimensions.dayCellPadding] twice for the strip to
         * fit in.
         */
        val LANE_BUDGET_SWEEP = intArrayOf(WIDTH, ONE_LANE_WIDTH, NO_LANE_WIDTH, CRAMPED_WIDTH)

        /** Column indices within a Monday-first week. */
        const val MONDAY = 0
        const val TUESDAY = 1
        const val WEDNESDAY = 2
        const val THURSDAY = 3
        const val FRIDAY = 4
        const val SUNDAY = 6

        /** The days of March 2026 that fall in [WEDNESDAY] and [SUNDAY] of [EVENT_ROW]. */
        const val WEDNESDAY_DAY = 11
        const val SUNDAY_DAY = 15

        /** The week of March 9th, counting the grid's rows of day numbers from zero. */
        const val EVENT_ROW = 2

        /**
         * How far a bar's real end may sit from its cell's edge — the 3dp inset plus a pixel of
         * rounding, at density 1.
         */
        const val END_INSET_LIMIT = 6

        /** A dot is 4dp across; anything wider is a bar. */
        const val DOT_WIDTH_LIMIT = 6

        /** Glyph shape alone moves the topmost lit pixel of a digit by this much. */
        const val BASELINE_TOLERANCE = 1

        const val DEFAULT_DOT_BUDGET = 3
        const val FULL_DOT_ROW = 5
        const val ONE_HIDDEN_LABEL = "+1"

        const val SETTLE_FRAME_NANOS = 1_000_000_000L
        const val CHANNEL_TOLERANCE = 6
        const val CHANNEL_MASK = 0xFF
        const val MAX_CHANNEL = 255f
        const val ALPHA_SHIFT = 24
        const val RED_SHIFT = 16
        const val GREEN_SHIFT = 8
        val CHANNEL_SHIFTS = intArrayOf(0, 8, 16, 24)

        // Saturated and mutually distinguishable, so no assertion can be satisfied by the wrong ink.
        val LANE_ONE_INK = Color(0xFFD32F2F)
        val LANE_TWO_INK = Color(0xFF1565C0)
        val OVERFLOW_INK = Color(0xFF00BFA5)
        val DOT_INK = Color(0xFFFF6D00)
        val DAY_NUMBER_INK = Color(0xFF00FF00)
    }
}
