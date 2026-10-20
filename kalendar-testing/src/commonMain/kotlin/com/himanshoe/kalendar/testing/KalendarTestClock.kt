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

package com.himanshoe.kalendar.testing

import com.himanshoe.kalendar.foundation.datetime.KalendarTimeSource
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

/**
 * A [Clock] that reads whatever a test last set, so a calendar's "today" and now-indicator hold
 * still until the test says otherwise.
 *
 * This is the advanceable half of the module's time control — for a clock that never moves at all,
 * [fixedKalendarTimeSource] builds one of these and hides it. Advance it with [advanceBy] or jump
 * it by assigning [instant]; either way, nothing recomposes on its own — Kalendar re-reads the
 * clock on its own schedule (the now-indicator ticks once a minute, the today highlight re-reads at
 * what the clock says is midnight), so a UI test advances the compose clock alongside this one:
 *
 * ```kotlin
 * val clock = KalendarTestClock(start = LocalDateTime(2026, 3, 10, 23, 59))
 * rule.setContent {
 *     ProvideKalendarTimeSource(clock.asTimeSource()) {
 *         KalendarMonth(selectedDate = LocalDate(2026, 3, 1))
 *     }
 * }
 * clock.advanceBy(2.minutes)                    // the clock crosses midnight...
 * rule.mainClock.advanceTimeBy(120_000L)        // ...and the rollover timer gets to notice
 * rule.onKalendarDay(LocalDate(2026, 3, 11)).assert(isKalendarToday())
 * ```
 *
 * Deliberately compared by identity: two test clocks started at the same instant are two
 * independently advanceable clocks, not one.
 *
 * @param start The instant [now] reports until the clock is moved.
 */
public class KalendarTestClock(start: Instant) : Clock {

    /**
     * Builds the clock from a wall-clock reading instead of an [Instant] — `"10 March 2026, 09:00
     * in this zone"`, which is the sentence a calendar test is usually written in.
     *
     * @param start What the clock reads, as a date and time on [timeZone]'s clock face.
     * @param timeZone The zone [start] is a reading of. Defaults to [TimeZone.UTC] — a test that
     *   does not say otherwise should not depend on the machine it runs on.
     */
    public constructor(
        start: LocalDateTime,
        timeZone: TimeZone = TimeZone.UTC,
    ) : this(start.toInstant(timeZone))

    /** The instant [now] currently reports. Assign it to jump the clock anywhere, backwards included. */
    public var instant: Instant = start

    override fun now(): Instant = instant

    /** Winds the clock forward by [duration] — the passage of time, on command. */
    public fun advanceBy(duration: Duration) {
        instant += duration
    }

    /**
     * A [KalendarTimeSource] reading this clock in [timeZone], ready to hand to
     * [com.himanshoe.kalendar.state.ProvideKalendarTimeSource] or any state factory.
     *
     * The same clock can back several time sources in different zones — that is how a test shows
     * one instant being two different "today"s on two calendars.
     *
     * @param timeZone The zone the time source resolves dates in. Defaults to [TimeZone.UTC],
     *   *not* the device zone [KalendarTimeSource]'s own constructor defaults to, because a test's
     *   answer must not change with the machine running it.
     */
    public fun asTimeSource(timeZone: TimeZone = TimeZone.UTC): KalendarTimeSource =
        KalendarTimeSource(timeZone = timeZone, clock = this)

    override fun toString(): String = "KalendarTestClock(instant=$instant)"
}

/**
 * A [KalendarTimeSource] pinned to one wall-clock moment forever — the one-liner for every test
 * that only needs "today" to be a known date.
 *
 * ```kotlin
 * rule.setContent {
 *     ProvideKalendarTimeSource(fixedKalendarTimeSource(now = LocalDateTime(2026, 3, 10, 9, 0))) {
 *         KalendarMonth(selectedDate = LocalDate(2026, 3, 1))
 *     }
 * }
 * rule.onKalendarDay(LocalDate(2026, 3, 10)).assert(isKalendarToday())
 * ```
 *
 * When the test itself has to move time, build a [KalendarTestClock] instead and keep hold of it.
 *
 * @param now What the calendar's clock reads, as a date and time on [timeZone]'s clock face.
 * @param timeZone The zone the time source resolves dates in. Defaults to [TimeZone.UTC] — see
 *   [KalendarTestClock.asTimeSource] for why a test API does not default to the device zone.
 */
public fun fixedKalendarTimeSource(
    now: LocalDateTime,
    timeZone: TimeZone = TimeZone.UTC,
): KalendarTimeSource = KalendarTestClock(start = now, timeZone = timeZone).asTimeSource(timeZone)
