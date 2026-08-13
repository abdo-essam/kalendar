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
 * A calendar on the device that events can be read from or written into.
 *
 * Returned by [KalendarSyncProvider.listCalendars]; pass [id] as the `calendarId` argument of
 * [KalendarSyncProvider.insertEvent] to write into a specific calendar instead of the default.
 *
 * @param id Stable identifier assigned by the platform calendar store.
 * @param name Human-readable display name of the calendar.
 * @param colorArgb The calendar's display colour packed as 32-bit ARGB (`0xAARRGGBB`) in a
 *   [Long], or `null` when the platform does not report one. Always `null` on iOS, where
 *   EventKit exposes calendar colours only as `CGColor`, which has no reliable
 *   cross-platform ARGB mapping.
 */
public data class KalendarSyncCalendar(
    val id: String,
    val name: String,
    val colorArgb: Long? = null,
)
