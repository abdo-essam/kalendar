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
import androidx.compose.ui.unit.Density
import androidx.compose.ui.use
import com.himanshoe.kalendar.foundation.event.BasicKalendarEvent
import com.himanshoe.kalendar.state.rememberKalendarScheduleState
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import org.jetbrains.skia.EncodedImageFormat
import org.junit.experimental.categories.Category
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.fail

/**
 * Golden-image tests locking in the visual appearance of the calendar views.
 *
 * Each test renders a view into a fixed-size, density-1 [ImageComposeScene] and compares the
 * PNG against a checked-in golden, tolerating up to [MAX_DIFFERING_PIXEL_RATIO] differing pixels
 * for antialiasing noise. Goldens are generated on macOS; other OSes may rasterise text
 * differently, so treat cross-OS failures as environmental until inspected — which is what the
 * [GoldenImageTests] category is for, and why it is on this class and nowhere else.
 *
 * Regenerate after an intentional visual change with:
 * `./gradlew :kalendar:desktopTest -PupdateGoldens=true`
 * On failure, the actual image is written to `build/reports/goldens-failed/` for inspection.
 *
 * March 2026 fixtures are used throughout so the system date (and its "today" styling) never
 * appears in the rendered month.
 */
@Category(GoldenImageTests::class)
class KalendarGoldenTest {

    @Test
    fun golden_monthDefault() {
        assertGolden(name = "month_default", width = 420, height = 480) {
            KalendarMonth(selectedDate = march(10))
        }
    }

    @Test
    fun golden_monthEventsAndOverflow() {
        val events = listOf(
            BasicKalendarEvent(
                date = march(9),
                endDate = march(11),
                eventName = "Conference",
                eventColor = Color(0xFF7E57C2),
            ),
            BasicKalendarEvent(date = march(12), eventName = "One", eventColor = Color(0xFF26A69A)),
            BasicKalendarEvent(date = march(12), eventName = "Two", eventColor = Color(0xFFEF6C00)),
            BasicKalendarEvent(date = march(12), eventName = "Three", eventColor = Color(0xFFC2185B)),
            BasicKalendarEvent(date = march(12), eventName = "Four"),
        )
        assertGolden(name = "month_events_overflow", width = 420, height = 480) {
            KalendarMonth(selectedDate = march(10), events = events)
        }
    }

    @Test
    fun golden_scheduleDayOverlap() {
        val events = listOf(
            BasicKalendarEvent(
                date = march(10),
                eventName = "Standup",
                startTime = LocalDateTime(2026, 3, 10, 9, 0),
                endTime = LocalDateTime(2026, 3, 10, 9, 30),
                eventColor = Color(0xFF26A69A),
            ),
            BasicKalendarEvent(
                date = march(10),
                eventName = "Design review",
                startTime = LocalDateTime(2026, 3, 10, 9, 15),
                endTime = LocalDateTime(2026, 3, 10, 10, 15),
                eventColor = Color(0xFFEF6C00),
            ),
            BasicKalendarEvent(date = march(10), eventName = "Holiday", eventColor = Color(0xFF7E57C2)),
        )
        assertGolden(name = "schedule_day_overlap", width = 420, height = 640) {
            KalendarSchedule(
                state = rememberKalendarScheduleState(initialDate = march(10)),
                events = events,
            )
        }
    }

    private fun march(day: Int): LocalDate = LocalDate(FIXTURE_YEAR, FIXTURE_MONTH, day)

    private fun assertGolden(name: String, width: Int, height: Int, content: @Composable () -> Unit) {
        val actualPng = renderToPng(width = width, height = height, content = content)
        val goldenDir = File(checkNotNull(System.getProperty("goldenDir")) { "goldenDir system property missing" })
        val goldenFile = File(goldenDir, "$name.png")

        if (System.getProperty("updateGoldens") == "true") {
            goldenDir.mkdirs()
            goldenFile.writeBytes(actualPng)
            return
        }
        if (!goldenFile.exists()) {
            fail("Golden $name.png missing — run ./gradlew :kalendar:desktopTest -PupdateGoldens=true")
        }

        val ratio = differingPixelRatio(expectedPng = goldenFile.readBytes(), actualPng = actualPng)
        if (ratio > MAX_DIFFERING_PIXEL_RATIO) {
            val failedDir = File("build/reports/goldens-failed").apply { mkdirs() }
            File(failedDir, "$name.png").writeBytes(actualPng)
            fail(
                "Golden $name differs: ${(ratio * 100)}% of pixels changed " +
                    "(limit ${(MAX_DIFFERING_PIXEL_RATIO * 100)}%). " +
                    "Actual written to ${failedDir.resolve("$name.png")}",
            )
        }
    }

    private fun renderToPng(width: Int, height: Int, content: @Composable () -> Unit): ByteArray =
        ImageComposeScene(width = width, height = height, density = Density(1f)) {
            MaterialTheme(colorScheme = lightColorScheme()) {
                content()
            }
        }.use { scene ->
            scene.render(nanoTime = 0L)
            val settled = scene.render(nanoTime = SETTLE_FRAME_NANOS)
            checkNotNull(settled.encodeToData(EncodedImageFormat.PNG)) { "PNG encode failed" }.bytes
        }

    private fun differingPixelRatio(expectedPng: ByteArray, actualPng: ByteArray): Double {
        val expected = ImageIO.read(expectedPng.inputStream())
        val actual = ImageIO.read(actualPng.inputStream())
        if (expected.width != actual.width || expected.height != actual.height) {
            return 1.0
        }
        var differing = 0L
        for (y in 0 until expected.height) {
            for (x in 0 until expected.width) {
                if (!pixelsClose(expected.getRGB(x, y), actual.getRGB(x, y))) {
                    differing++
                }
            }
        }
        return differing.toDouble() / (expected.width.toLong() * expected.height)
    }

    private fun pixelsClose(a: Int, b: Int): Boolean {
        for (shift in CHANNEL_SHIFTS) {
            val channelA = (a shr shift) and CHANNEL_MASK
            val channelB = (b shr shift) and CHANNEL_MASK
            if (kotlin.math.abs(channelA - channelB) > CHANNEL_TOLERANCE) {
                return false
            }
        }
        return true
    }

    private companion object {
        /**
         * How much of an image may differ before a golden is considered broken.
         *
         * It was 1%, and that number let a real defect live its whole life: day numbers sitting at
         * three different heights within one week, visibly sliding as event dots faded in, moved
         * **0.376%** of pixels. Every golden passed throughout.
         *
         * 1% was never argued for, so here is the argument for this one. Rendering is deterministic
         * on a given machine, so the honest expectation for an unchanged layout is a diff of zero;
         * the tolerance exists only to absorb text-rasterisation noise between environments, which
         * moves a handful of pixels — well under 0.05% at these image sizes. A layout shift confined
         * to a few cells lands between 0.3% and 0.5%. 0.1% sits above the noise and below the
         * smallest defect worth catching.
         *
         * This is still a backstop rather than a guarantee. A shift confined to one cell of a year
         * view would slip under any threshold that tolerates rasterisation noise at all, which is
         * why the layout invariants — baselines, centring, block bounds — are asserted directly
         * rather than left to whole-image comparison.
         */
        const val MAX_DIFFERING_PIXEL_RATIO = 0.001
        const val CHANNEL_TOLERANCE = 8
        const val CHANNEL_MASK = 0xFF
        const val SETTLE_FRAME_NANOS = 1_000_000_000L
        const val FIXTURE_YEAR = 2026
        const val FIXTURE_MONTH = 3
        val CHANNEL_SHIFTS = intArrayOf(0, 8, 16, 24)
    }
}
