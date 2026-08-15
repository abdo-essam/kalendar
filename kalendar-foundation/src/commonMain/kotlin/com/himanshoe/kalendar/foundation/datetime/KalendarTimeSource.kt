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

package com.himanshoe.kalendar.foundation.datetime

import androidx.compose.runtime.Immutable
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

/**
 * Where a calendar reads "now" from, **and which region's clock it reads it on** — the one seam
 * between calendar logic and the world outside.
 *
 * Both halves matter. Resolving through a time source rather than calling [Clock.System] directly
 * makes three otherwise impossible things possible:
 *
 * - **Deterministic tests.** Hand a fixed [Clock] and the calendar's idea of "today" stops moving,
 *   so a screenshot or an assertion about the highlighted cell holds forever.
 * - **A calendar that rolls over at midnight.** A caller that re-reads [today] when the day changes
 *   moves its highlight to the new day instead of keeping yesterday's — and [untilNextDay] says
 *   when that is, in this zone, on this date, however many hours that date happens to have.
 * - **A calendar pinned to somewhere else.** A shift rota kept in the office's zone, or a schedule
 *   being read from another country, highlights the office's today and draws the office's
 *   now-indicator rather than the device's:
 *
 * ```kotlin
 * val office = KalendarTimeSource(timeZone = TimeZone.of("Europe/Berlin"))
 * office.today() // the date it is in Berlin, wherever the device is
 * ```
 *
 * Nothing here reads [Clock.System] or the device zone on its own, so a fixed instance is fully
 * deterministic:
 *
 * ```kotlin
 * val fixed = KalendarTimeSource(
 *     timeZone = TimeZone.UTC,
 *     clock = object : Clock { override fun now() = Instant.parse("2026-03-10T09:00:00Z") },
 * )
 * fixed.today() // always 2026-03-10
 * ```
 *
 * Instances must be stable across recompositions when one is handed to Compose UI — `remember` it,
 * or hold it in a view model. A fresh instance built inline every frame re-arms every rollover timer
 * that depends on it.
 *
 * @property timeZone The zone every date and time here is resolved in. Defaults to the device's
 *   current zone, **read once, when the instance is built**. That is the right reading for a
 *   calendar pinned to a zone and the wrong one for a traveller, whose device zone changes under a
 *   long-lived instance: rebuild the time source when the device zone changes, and every date
 *   derived from it follows.
 * @property clock The instant source. Defaults to [Clock.System].
 */
@Immutable
public class KalendarTimeSource(
    public val timeZone: TimeZone = TimeZone.currentSystemDefault(),
    public val clock: Clock = Clock.System,
) {

    /** The current date in [timeZone]. */
    public fun today(): LocalDate = dateOf(clock.now())

    /** The current date and time in [timeZone]. */
    public fun now(): LocalDateTime = timeOf(clock.now())

    /**
     * Which date [instant] falls on in [timeZone] — the answer that decides whether an event lands
     * on this calendar's Tuesday or its Wednesday.
     *
     * ```kotlin
     * val tokyo = KalendarTimeSource(timeZone = TimeZone.of("Asia/Tokyo"))
     * tokyo.dateOf(Instant.parse("2026-01-15T23:00:00Z")) // 2026-01-16, already tomorrow there
     * ```
     */
    public fun dateOf(instant: Instant): LocalDate = instant.dateIn(timeZone)

    /** [instant] as a wall-clock date and time on this calendar's clock face. */
    public fun timeOf(instant: Instant): LocalDateTime = instant.toLocalDateTime(timeZone)

    /**
     * How long until midnight opens the next date in [timeZone] — what a midnight rollover waits.
     *
     * It is the distance to the *next date's* midnight as [timeZone] places it, never a fixed 24
     * hours minus the time of day: on the two days a year the clocks move, those two figures differ
     * by an hour, and a rollover computed the other way fires an hour early or an hour late.
     */
    public fun untilNextDay(): Duration =
        today().plus(1, DateTimeUnit.DAY).atStartOfDayIn(timeZone) - clock.now()

    /** Returns a duplicate of this time source with only the values passed here replaced. */
    public fun copy(
        timeZone: TimeZone = this.timeZone,
        clock: Clock = this.clock,
    ): KalendarTimeSource = KalendarTimeSource(timeZone = timeZone, clock = clock)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is KalendarTimeSource) return false
        if (timeZone != other.timeZone) return false
        if (clock != other.clock) return false
        return true
    }

    override fun hashCode(): Int = 31 * timeZone.hashCode() + clock.hashCode()

    override fun toString(): String = "KalendarTimeSource(timeZone=$timeZone, clock=$clock)"
}
