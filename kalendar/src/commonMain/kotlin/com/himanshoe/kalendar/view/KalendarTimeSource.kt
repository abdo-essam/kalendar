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

package com.himanshoe.kalendar.view

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.delay
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.todayIn
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/** Default tick for [rememberKalendarNow] — the Schedule views' now-indicator resolution. */
internal val DEFAULT_NOW_TICK: Duration = 1.minutes

/** Shortest wait [rememberKalendarToday] will schedule, so a clock at exactly midnight can't spin. */
private const val MIN_ROLLOVER_DELAY_MS = 1L

/**
 * Where Kalendar reads "now" from — the one seam between the calendar and the wall clock.
 *
 * Every view, state factory, and now-indicator resolves today's date through a time source rather
 * than calling [Clock.System] directly, which makes two otherwise impossible things possible:
 *
 * - **Deterministic tests.** Hand a fixed [Clock] and the calendar's idea of "today" stops moving,
 *   so a screenshot or assertion about the highlighted cell holds forever.
 * - **A calendar that rolls over at midnight.** [rememberKalendarToday] re-reads the clock when the
 *   day changes, so a calendar left open overnight highlights the new day instead of yesterday's.
 *
 * ```kotlin
 * // Production: the system clock in the device's own zone (the default — nothing to do).
 * KalendarMonth(selectedDate = today)
 *
 * // Tests: a frozen clock, so "today" is whatever the fixture says it is.
 * val fixed = KalendarTimeSource(
 *     timeZone = TimeZone.UTC,
 *     clock = object : Clock { override fun now() = Instant.parse("2026-03-10T09:00:00Z") },
 * )
 * ProvideKalendarTimeSource(fixed) {
 *     KalendarMonth(selectedDate = LocalDate(2026, 3, 10))
 * }
 * ```
 *
 * Instances must be stable across recompositions — `remember` yours, or hold it in a view model.
 * A fresh instance built inline every frame re-arms every rollover timer that depends on it.
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

/**
 * The ambient time source, or `null` when nothing provided one.
 *
 * Deliberately `static` and deliberately nullable: [androidx.compose.runtime.compositionLocalOf]'s
 * default is evaluated once per process and cached, so a `KalendarTimeSource()` default would pin
 * one `TimeZone.currentSystemDefault()` reading for the lifetime of the app — exactly the staleness
 * this whole file exists to remove. The fallback therefore lives in [rememberKalendarTimeSource],
 * where it is scoped to a composition instead.
 */
internal val LocalKalendarTimeSource = staticCompositionLocalOf<KalendarTimeSource?> { null }

/**
 * Provides [timeSource] to every Kalendar view, state factory, and now-indicator inside [content].
 *
 * This is the single injection point: providing it here keeps a view's `state`, its `initialDate`
 * default, and its now-indicator all reading the same clock.
 *
 * @param timeSource The clock and zone to use. `remember` it — see [KalendarTimeSource].
 * @param content The calendar views this time source applies to.
 */
@Composable
public fun ProvideKalendarTimeSource(
    timeSource: KalendarTimeSource,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalKalendarTimeSource provides timeSource, content = content)
}

/**
 * The effective [KalendarTimeSource]: the one provided by [ProvideKalendarTimeSource], or a
 * composition-scoped default reading [Clock.System] in the device's current zone.
 */
@Composable
public fun rememberKalendarTimeSource(): KalendarTimeSource =
    LocalKalendarTimeSource.current ?: remember { KalendarTimeSource() }

/**
 * Today's date from [timeSource], **re-read when the day changes** — a calendar left open past
 * midnight moves its "today" highlight to the new day instead of keeping yesterday's.
 *
 * This is the replacement for a `today` property on a state object: a clock-derived value stored on
 * a `@Stable` class notifies nobody when the clock moves on, so it goes stale. Reading it here, in
 * composition, keeps it snapshot-observable.
 *
 * ```kotlin
 * val state = rememberKalendarMonthState()
 * val today = rememberKalendarToday(state.timeSource)
 * ```
 *
 * @param timeSource Where to read the date from. Defaults to the ambient time source.
 */
@Composable
public fun rememberKalendarToday(
    timeSource: KalendarTimeSource = rememberKalendarTimeSource(),
): LocalDate {
    var today by remember(timeSource) { mutableStateOf(timeSource.today()) }
    LaunchedEffect(timeSource) {
        while (true) {
            delay(timeSource.millisUntilNextDay())
            today = timeSource.today()
        }
    }
    return today
}

/**
 * The current date and time from [timeSource], re-read every [tick].
 *
 * Reading this invalidates the calling composable on every tick, which is what a clock face wants
 * and what a calendar mostly does not — the Schedule views read the same state through a
 * placement-phase offset so only the now-indicator's position is recomputed each minute.
 *
 * @param timeSource Where to read the time from. Defaults to the ambient time source.
 * @param tick How often to re-read. Defaults to one minute.
 */
@Composable
public fun rememberKalendarNow(
    timeSource: KalendarTimeSource = rememberKalendarTimeSource(),
    tick: Duration = DEFAULT_NOW_TICK,
): LocalDateTime = rememberKalendarNowState(timeSource = timeSource, tick = tick).value

/**
 * [rememberKalendarNow] without the read — the [State] itself, so a caller can defer reading it to
 * the layout or draw phase and keep a minute-by-minute clock out of recomposition entirely.
 */
@Composable
internal fun rememberKalendarNowState(
    timeSource: KalendarTimeSource,
    tick: Duration,
): State<LocalDateTime> {
    val now = remember(timeSource) { mutableStateOf(timeSource.now()) }
    LaunchedEffect(timeSource, tick) {
        while (true) {
            delay(tick)
            now.value = timeSource.now()
        }
    }
    return now
}

/**
 * Today's date from the ambient time source, captured once for the lifetime of the composition.
 *
 * This is what the `initialDate` parameters default to, and it deliberately does *not* roll over:
 * those values anchor a pager's page-to-date mapping, so a value that changed at midnight would
 * silently re-anchor the pager and jump the visible page by a day.
 */
@Composable
internal fun rememberInitialKalendarDate(): LocalDate {
    val timeSource = rememberKalendarTimeSource()
    return remember(timeSource) { timeSource.today() }
}

/** Milliseconds from now until midnight opens the next day in [KalendarTimeSource.timeZone]. */
private fun KalendarTimeSource.millisUntilNextDay(): Long {
    val nextDayStart = today().plus(1, DateTimeUnit.DAY).atStartOfDayIn(timeZone)
    return (nextDayStart - clock.now()).inWholeMilliseconds.coerceAtLeast(MIN_ROLLOVER_DELAY_MS)
}
