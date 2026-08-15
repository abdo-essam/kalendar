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

import androidx.compose.runtime.Immutable
import kotlinx.datetime.LocalDate

/**
 * A span of dates, both ends included — one page's worth of calendar, and the unit a
 * [KalendarEventSource] is asked for.
 *
 * It is a [ClosedRange], so `date in range` works and the two ends read the way every other Kotlin
 * range's do:
 *
 * ```kotlin
 * val source = KalendarEventSource.suspending { range: KalendarDateRange ->
 *     bookingDao.between(from = range.start, to = range.endInclusive)
 * }
 * ```
 *
 * The range a view reports is what it can *draw*, not the period its page is named after: a month
 * page whose grid begins on the previous month's Monday reports that Monday, because those cells
 * show events too. Ask a source for exactly what it is given and every visible cell is covered.
 *
 * @property start The first date in the span.
 * @property endInclusive The last date in the span, itself part of it.
 */
@Immutable
public class KalendarDateRange(
    override val start: LocalDate,
    override val endInclusive: LocalDate,
) : ClosedRange<LocalDate> {

    init {
        require(start <= endInclusive) {
            "start must not be after endInclusive, was $start..$endInclusive"
        }
    }

    /** How many dates the span covers, counting both ends — never less than `1`. */
    public val dayCount: Int
        get() = (endInclusive.toEpochDays() - start.toEpochDays() + 1).toInt()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is KalendarDateRange) return false
        if (start != other.start) return false
        if (endInclusive != other.endInclusive) return false
        return true
    }

    override fun hashCode(): Int {
        var result = start.hashCode()
        result = HASH_FACTOR * result + endInclusive.hashCode()
        return result
    }

    override fun toString(): String = "KalendarDateRange(start=$start, endInclusive=$endInclusive)"
}

private const val HASH_FACTOR = 31
