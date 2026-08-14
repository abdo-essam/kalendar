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
 * A person invited to a [KalendarSyncEvent].
 *
 * Maps to an `ATTENDEE` line in iCal output and a `CalendarContract.Attendees` row on
 * Android. On iOS attendees can only be read, never written — see
 * [KalendarSyncEvent.attendees] for details.
 *
 * @param email The attendee's email address.
 * @param name Optional display name shown alongside the email address.
 *
 * This is deliberately a plain `class` with a hand-written [copy], [equals], and [hashCode]
 * rather than a `data class`, so that adding a property in a later release does not change the
 * compiled signature of `copy` and break already compiled consumers.
 */
public class KalendarAttendee(
    public val email: String,
    public val name: String? = null,
) {
    /**
     * Returns a copy of this attendee with the given properties replaced and all others carried
     * over unchanged.
     */
    public fun copy(
        email: String = this.email,
        name: String? = this.name,
    ): KalendarAttendee = KalendarAttendee(email = email, name = name)

    /** Two attendees are equal when both the email address and the display name match. */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is KalendarAttendee) return false
        return email == other.email && name == other.name
    }

    override fun hashCode(): Int = HASH_FACTOR * email.hashCode() + (name?.hashCode() ?: 0)

    override fun toString(): String = "KalendarAttendee(email=$email, name=$name)"
}

private const val HASH_FACTOR = 31
