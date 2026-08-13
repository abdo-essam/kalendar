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
 */
public data class KalendarAttendee(
    val email: String,
    val name: String? = null,
)
