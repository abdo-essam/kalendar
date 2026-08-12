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

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

/**
 * Returns every date from the earlier of `this`/[other] to the later, inclusive.
 *
 * Intended for a two-tap range selection against [KalendarWeek]/[KalendarMonth]/[KalendarYear]'s
 * `selectedDates`: track the two tapped dates yourself, then pass `start.datesUntil(end)` as
 * `selectedDates` to highlight the whole span.
 *
 * ```kotlin
 * var rangeStart by remember { mutableStateOf<LocalDate?>(null) }
 * var rangeEnd by remember { mutableStateOf<LocalDate?>(null) }
 *
 * KalendarMonth(
 *     selectedDate = today,
 *     selectedDates = if (rangeStart != null && rangeEnd != null) {
 *         rangeStart!!.datesUntil(rangeEnd!!)
 *     } else {
 *         emptySet()
 *     },
 *     onDateClick = { date, _ ->
 *         if (rangeStart == null || rangeEnd != null) {
 *             rangeStart = date
 *             rangeEnd = null
 *         } else {
 *             rangeEnd = date
 *         }
 *     },
 * )
 * ```
 */
fun LocalDate.datesUntil(other: LocalDate): Set<LocalDate> {
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
