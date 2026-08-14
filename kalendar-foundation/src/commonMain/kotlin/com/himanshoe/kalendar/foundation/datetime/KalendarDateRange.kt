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

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

/**
 * Every date from the earlier of `this`/[other] to the later, inclusive of both — the set a two-tap
 * or press-and-drag range selection highlights.
 *
 * Order-independent: `a.datesUntil(b)` and `b.datesUntil(a)` are the same set, so a caller never has
 * to sort the two ends of a range before asking.
 *
 * ```kotlin
 * val span = LocalDate(2026, 6, 1).datesUntil(LocalDate(2026, 6, 3))
 * // [2026-06-01, 2026-06-02, 2026-06-03]
 * val isSelected = LocalDate(2026, 6, 2) in span // true
 * ```
 */
public fun LocalDate.datesUntil(other: LocalDate): Set<LocalDate> {
    val start =
        if (this <= other) {
            this
        } else {
            other
        }
    val end =
        if (this <= other) {
            other
        } else {
            this
        }
    val dayCount = (end.toEpochDays() - start.toEpochDays())
    return (0..dayCount).map { start.plus(it, DateTimeUnit.DAY) }.toSet()
}
