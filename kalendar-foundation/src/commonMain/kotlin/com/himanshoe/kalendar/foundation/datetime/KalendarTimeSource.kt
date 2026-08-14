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
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.todayIn
import kotlin.time.Clock

/**
 * Where a calendar reads "now" from — the one seam between calendar logic and the wall clock.
 *
 * Resolving today's date through a time source rather than calling [Clock.System] directly makes two
 * otherwise impossible things possible:
 *
 * - **Deterministic tests.** Hand a fixed [Clock] and the calendar's idea of "today" stops moving,
 *   so a screenshot or an assertion about the highlighted cell holds forever.
 * - **A calendar that rolls over at midnight.** A caller that re-reads [today] when the day changes
 *   moves its highlight to the new day instead of keeping yesterday's.
 *
 * ```kotlin
 * val system = KalendarTimeSource()
 * val startOfThisWeek = system.today().startOfWeek(DayOfWeek.MONDAY)
 *
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
 * @property timeZone The zone dates are resolved in. Defaults to the device's current zone, read
 *   once per instance.
 * @property clock The instant source. Defaults to [Clock.System].
 */
@Immutable
public class KalendarTimeSource(
    public val timeZone: TimeZone = TimeZone.currentSystemDefault(),
    public val clock: Clock = Clock.System,
) {

    /** The current date in [timeZone]. */
    public fun today(): LocalDate = clock.todayIn(timeZone)

    /** The current date and time in [timeZone]. */
    public fun now(): LocalDateTime = clock.now().toLocalDateTime(timeZone)

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
