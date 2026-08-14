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

package com.himanshoe.kalendar.sync

/**
 * A notification reminder attached to a [KalendarSyncEvent], firing a fixed number of minutes
 * before the event starts.
 *
 * Maps to a `VALARM` block in iCal output, a `CalendarContract.Reminders` row on Android, and
 * an `EKAlarm` on iOS.
 *
 * @param minutesBefore How many minutes before the event start the reminder fires. Must be
 *   zero or greater; `0` fires at the event start time.
 *
 * This is deliberately a plain `class` with a hand-written [copy], [equals], and [hashCode]
 * rather than a `data class`, so that adding a property in a later release does not change the
 * compiled signature of `copy` and break already compiled consumers.
 */
public class KalendarReminder(public val minutesBefore: Int) {
    init {
        require(minutesBefore >= 0) { "minutesBefore must be >= 0 but was $minutesBefore" }
    }

    /**
     * Returns a copy of this reminder with [minutesBefore] replaced. The constructor's validation
     * applies to the result, so a negative value throws just as the constructor would.
     */
    public fun copy(minutesBefore: Int = this.minutesBefore): KalendarReminder =
        KalendarReminder(minutesBefore = minutesBefore)

    /** Two reminders are equal when they fire the same number of minutes before the event. */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        return other is KalendarReminder && minutesBefore == other.minutesBefore
    }

    override fun hashCode(): Int = minutesBefore

    override fun toString(): String = "KalendarReminder(minutesBefore=$minutesBefore)"
}
