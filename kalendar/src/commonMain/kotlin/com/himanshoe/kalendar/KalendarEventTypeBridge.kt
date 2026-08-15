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

package com.himanshoe.kalendar

import com.himanshoe.kalendar.foundation.event.KalendarEvent

/*
 * Where the event type parameter stops, and how it is picked back up.
 *
 * The views are generic in E, the caller's own event type. The hour-grid engine underneath them —
 * `scheduleBlocks`, `resourceBlocks`, `KalendarScheduleBlock`, and the block layout that positions
 * their output — is not, and deliberately so: it is *geometry*. It packs minute ranges into overlap
 * columns and carries each event along untouched, never reading anything beyond `startTime` and
 * `endTime`, so nothing it computes could be more precise than KalendarEvent. Parameterising it
 * would put a type argument on every one of those signatures and buy no expressiveness at all.
 *
 * The cost of that decision is the cast below, paid once per boundary crossing inside the library
 * rather than once per callback inside every consumer — which is the whole point of the exercise.
 * The date-grid views need none of this: their index, `byDateExpandingSpans`, is generic, so the
 * events they hand back never lose their type in the first place.
 */

/**
 * Re-attaches the calendar's own event type to an event that has come back out of the block engine.
 *
 * Sound because of an invariant the engine cannot express: every event it is holding arrived from
 * the `List<E>` the view passed in, and the engine only ever hands the same instances back. Nothing
 * in `com.himanshoe.kalendar` constructs a [KalendarEvent], so there is no other way for one to
 * reach a block.
 */
@Suppress("UNCHECKED_CAST")
internal fun <E : KalendarEvent> asCalendarEvent(event: KalendarEvent): E = event as E

/**
 * The engine's `(KalendarEvent) -> Unit` in front of a view's `(E) -> Unit`.
 *
 * A named function rather than a lambda written inline in the `remember`, so the adapter has exactly
 * one identity per `onEventClick` and the blocks reading it stay skippable.
 */
internal fun <E : KalendarEvent> blockEventClickAdapter(
    onEventClick: (E) -> Unit,
): (KalendarEvent) -> Unit = { event -> onEventClick(asCalendarEvent(event)) }
