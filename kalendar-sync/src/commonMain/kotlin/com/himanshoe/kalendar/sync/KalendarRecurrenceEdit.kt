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

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus
import kotlinx.datetime.plus

private const val HASH_FACTOR = 31

/**
 * Which occurrences of a recurring series an edit or a deletion applies to — the three choices
 * every calendar app offers when one instance of a repeating event is changed.
 *
 * Pass one to [editOccurrence] or [deleteOccurrence].
 */
public enum class KalendarRecurrenceScope {

    /**
     * Only the occurrence named by the call. The series keeps its rule and gains an RFC 5545
     * EXDATE for that date; an edit additionally produces a **detached** one-off event carrying
     * the change, which never inherits the series' [KalendarSyncEvent.recurrenceRule].
     */
    THIS_OCCURRENCE,

    /**
     * The named occurrence and every later one. The stored series is terminated with an UNTIL
     * that falls immediately before the occurrence — it keeps the occurrence *before* the split
     * and loses the one at it — and an edit additionally produces a new series starting at the
     * occurrence, repeating with the original rule and carrying the edited fields.
     */
    THIS_AND_FOLLOWING,

    /**
     * Every occurrence. The series is edited (or deleted) in place, keeping its rule and its
     * existing exception dates.
     */
    ENTIRE_SERIES,
}

/**
 * The writes that apply one [editOccurrence] or [deleteOccurrence] to a calendar: at most one
 * change to the stored series row, plus the events that have to be created.
 *
 * Apply it through any [KalendarSyncProvider]:
 * ```kotlin
 * val change = series.deleteOccurrence(
 *     occurrenceDate = LocalDate(2026, 3, 10),
 *     scope = KalendarRecurrenceScope.THIS_OCCURRENCE,
 * )
 * val seriesId = requireNotNull(series.id)
 * val updated = change.updatedSeries
 * if (updated == null) {
 *     sync.deleteEvent(eventId = seriesId)
 * } else {
 *     sync.updateEvent(eventId = seriesId, event = updated)
 * }
 * for (event in change.newEvents) {
 *     sync.insertEvent(event = event)
 * }
 * ```
 *
 * A change never both removes the series and creates events, so applying the series write before
 * the inserts — as above — cannot leave a window in which an occurrence exists twice.
 *
 * This is deliberately a plain `class` rather than a `data class`, so that a later release can
 * describe a further kind of write without changing a compiled `copy`/`componentN` signature.
 *
 * @param updatedSeries The series row rewritten in place, to be written back under the original
 *   [KalendarSyncEvent.id], or `null` when the whole series row must be deleted instead.
 * @param newEvents Events that do not exist yet and must be inserted. Each carries a `null`
 *   [KalendarSyncEvent.id]; the provider assigns the real one.
 */
public class KalendarSeriesChange internal constructor(
    public val updatedSeries: KalendarSyncEvent?,
    public val newEvents: List<KalendarSyncEvent>,
) {

    /**
     * `true` when the original series row must be deleted rather than rewritten — the same
     * condition as `updatedSeries == null`, named for readability at the call site.
     */
    public val deletesSeries: Boolean
        get() = updatedSeries == null

    /** Two changes are equal when both the rewritten series and the created events match. */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is KalendarSeriesChange) return false
        if (updatedSeries != other.updatedSeries) return false
        return newEvents == other.newEvents
    }

    override fun hashCode(): Int = HASH_FACTOR * (updatedSeries?.hashCode() ?: 0) + newEvents.hashCode()

    override fun toString(): String =
        "KalendarSeriesChange(updatedSeries=$updatedSeries, newEvents=$newEvents)"
}

/**
 * Applies [edited] to the occurrence of this series that starts on [occurrenceDate], as far as
 * [scope] allows, and returns the resulting writes.
 *
 * The receiver must be the **stored series template** — the event carrying the
 * [KalendarSyncEvent.recurrenceRule], as returned by [KalendarSyncProvider.fetchEvents] — not one
 * of the occurrences [expandOccurrences] materialises from it. [occurrenceDate] is the start date
 * of the occurrence the user acted on, as it stands *before* the edit; [edited] carries the new
 * field values and may sit on a different date, which moves the occurrence.
 *
 * The resulting series always repeats with the **receiver's** rule; [edited]'s own
 * [KalendarSyncEvent.recurrenceRule] and [KalendarSyncEvent.exceptionDates] are ignored, because
 * a scoped edit changes *which* occurrences are affected, not the repeat pattern itself. Change
 * the pattern with a plain [KalendarSyncProvider.updateEvent] instead.
 *
 * Per scope:
 * - [KalendarRecurrenceScope.THIS_OCCURRENCE] — the series gains an EXDATE for [occurrenceDate]
 *   and a detached, non-recurring copy of [edited] is created. RFC 5545 requires that EXDATE to
 *   match the excluded occurrence's DTSTART exactly, including its time; the iCal codec and the
 *   Android provider both write it at the series' start time for that reason.
 * - [KalendarRecurrenceScope.THIS_AND_FOLLOWING] — the series is terminated with
 *   `UNTIL = occurrenceDate - 1 day`, which is inclusive per RFC 5545 and therefore keeps the
 *   occurrence before the split while dropping the one at it, and a new series anchored on
 *   [edited]'s date is created. A [KalendarRule.count] is divided between the two: the new series
 *   receives only the occurrences the original had left. Exception dates are divided at the same
 *   boundary. When the split lands on the series' **first** occurrence nothing survives ahead of
 *   it, so the edit is applied to the whole series in place instead of leaving an empty stub.
 * - [KalendarRecurrenceScope.ENTIRE_SERIES] — the series is rewritten in place. Moving the
 *   occurrence to another date shifts the series anchor, and its exception dates, by the same
 *   number of days; note that for a rule with BY-parts the BY-filters still decide which days
 *   fire, so prefer [KalendarRecurrenceScope.THIS_AND_FOLLOWING] when moving a filtered series.
 *
 * An event with no [KalendarSyncEvent.recurrenceRule] has nothing to scope, so every scope
 * rewrites it in place.
 *
 * ```kotlin
 * val series = BasicKalendarSyncEvent(
 *     id = "42",
 *     date = LocalDate(2026, 3, 2),
 *     eventName = "Standup",
 *     startTime = LocalTime(9, 0),
 *     endTime = LocalTime(9, 15),
 *     recurrenceRule = KalendarRule(frequency = KalendarRecurrenceFrequency.DAILY),
 * )
 * val change = series.editOccurrence(
 *     occurrenceDate = LocalDate(2026, 3, 10),
 *     edited = BasicKalendarSyncEvent(
 *         date = LocalDate(2026, 3, 10),
 *         eventName = "Standup (extended)",
 *         startTime = LocalTime(9, 0),
 *         endTime = LocalTime(10, 0),
 *     ),
 *     scope = KalendarRecurrenceScope.THIS_AND_FOLLOWING,
 * )
 * ```
 *
 * @param occurrenceDate Start date of the occurrence the user acted on.
 * @param edited The occurrence's new field values.
 * @param scope How much of the series the edit reaches.
 */
public fun KalendarSyncEvent.editOccurrence(
    occurrenceDate: LocalDate,
    edited: KalendarSyncEvent,
    scope: KalendarRecurrenceScope,
): KalendarSeriesChange = reviseSeries(occurrenceDate = occurrenceDate, edited = edited, scope = scope)

/**
 * Removes the occurrence of this series that starts on [occurrenceDate], as far as [scope]
 * allows, and returns the resulting writes.
 *
 * The receiver must be the stored series template, exactly as for [editOccurrence]. Deletion is
 * that same operation with nothing to write back, so it shares its boundary semantics:
 * - [KalendarRecurrenceScope.THIS_OCCURRENCE] — the series gains an EXDATE for [occurrenceDate]
 *   and keeps every other occurrence.
 * - [KalendarRecurrenceScope.THIS_AND_FOLLOWING] — the series is terminated with an UNTIL
 *   immediately before [occurrenceDate], keeping the occurrence before the split and dropping the
 *   one at it. When the split lands on the series' first occurrence there is nothing left to
 *   keep, so the whole series row is deleted ([KalendarSeriesChange.deletesSeries]).
 * - [KalendarRecurrenceScope.ENTIRE_SERIES] — the series row is deleted; [occurrenceDate] is
 *   unused.
 *
 * ```kotlin
 * val change = series.deleteOccurrence(
 *     occurrenceDate = LocalDate(2026, 3, 10),
 *     scope = KalendarRecurrenceScope.THIS_OCCURRENCE,
 * )
 * ```
 *
 * @param occurrenceDate Start date of the occurrence to remove.
 * @param scope How much of the series the deletion reaches.
 */
public fun KalendarSyncEvent.deleteOccurrence(
    occurrenceDate: LocalDate,
    scope: KalendarRecurrenceScope,
): KalendarSeriesChange = reviseSeries(occurrenceDate = occurrenceDate, edited = null, scope = scope)

/**
 * The shared body of [editOccurrence] and [deleteOccurrence]: a deletion is an edit with no
 * replacement, so [edited] is `null` for one and the new field values for the other. Keeping them
 * on one path is what stops the two operations from drifting apart at the boundary — the UNTIL
 * that ends the original series has to land in exactly the same place either way.
 */
private fun KalendarSyncEvent.reviseSeries(
    occurrenceDate: LocalDate,
    edited: KalendarSyncEvent?,
    scope: KalendarRecurrenceScope,
): KalendarSeriesChange {
    val rule = recurrenceRule ?: return rewriteAsOneOff(edited)
    return when (scope) {
        KalendarRecurrenceScope.THIS_OCCURRENCE -> detachOccurrence(occurrenceDate = occurrenceDate, edited = edited)
        KalendarRecurrenceScope.THIS_AND_FOLLOWING ->
            splitSeries(rule = rule, occurrenceDate = occurrenceDate, edited = edited)

        KalendarRecurrenceScope.ENTIRE_SERIES -> rewriteWholeSeries(occurrenceDate = occurrenceDate, edited = edited)
    }
}

/**
 * Handles a receiver that carries no recurrence rule, where all three scopes mean the same thing:
 * rewrite the single event, or delete it. The rewrite clears the rule and exception dates that
 * [edited] may be carrying, since a one-off event has neither.
 */
private fun KalendarSyncEvent.rewriteAsOneOff(edited: KalendarSyncEvent?): KalendarSeriesChange =
    KalendarSeriesChange(
        updatedSeries = edited?.copyAsBasic(id = id, recurrenceRule = null, exceptionDates = emptyList()),
        newEvents = emptyList(),
    )

/**
 * Excludes [occurrenceDate] from the series and, for an edit, materialises the occurrence as a
 * standalone event. The detached event must not inherit the rule or the exception list: it stands
 * for one date, and re-expanding a copy that still carried the rule would resurrect the whole
 * series under a second id.
 */
private fun KalendarSyncEvent.detachOccurrence(
    occurrenceDate: LocalDate,
    edited: KalendarSyncEvent?,
): KalendarSeriesChange = KalendarSeriesChange(
    updatedSeries = copyAsBasic(exceptionDates = (exceptionDates + occurrenceDate).distinct().sorted()),
    newEvents = listOfNotNull(
        edited?.copyAsBasic(id = null, recurrenceRule = null, exceptionDates = emptyList()),
    ),
)

/**
 * Cuts the series in two at [occurrenceDate]: the stored row keeps everything strictly before it,
 * and an edit starts a fresh series at the occurrence itself.
 *
 * When no occurrence precedes the split the original row would be left describing an empty
 * series — an UNTIL before its own DTSTART — so the change is applied to the whole series
 * instead, which for a deletion means removing the row.
 */
private fun KalendarSyncEvent.splitSeries(
    rule: KalendarRule,
    occurrenceDate: LocalDate,
    edited: KalendarSyncEvent?,
): KalendarSeriesChange {
    val consumed = ruleOccurrencesBefore(occurrenceDate)
    if (consumed == 0) {
        return rewriteWholeSeries(occurrenceDate = occurrenceDate, edited = edited)
    }
    val head = copyAsBasic(
        recurrenceRule = rule.copy(count = null, until = splitBoundary(rule = rule, occurrenceDate = occurrenceDate)),
        exceptionDates = exceptionDates.filter { it < occurrenceDate },
    )
    return KalendarSeriesChange(
        updatedSeries = head,
        newEvents = listOfNotNull(
            edited?.let {
                continuationSeries(rule = rule, occurrenceDate = occurrenceDate, edited = it, consumed = consumed)
            },
        ),
    )
}

/**
 * The UNTIL that ends the truncated series: the day before [occurrenceDate], since RFC 5545's
 * UNTIL is **inclusive** and the occurrence at the split must be lost while the one before it is
 * kept. A series that already ended earlier keeps its own end rather than being extended.
 */
private fun splitBoundary(rule: KalendarRule, occurrenceDate: LocalDate): LocalDate {
    val dayBeforeSplit = occurrenceDate.minus(1, DateTimeUnit.DAY)
    return rule.until?.let { minOf(it, dayBeforeSplit) } ?: dayBeforeSplit
}

/**
 * Builds the series that takes over at the split, anchored on [edited]'s date and repeating with
 * the original rule.
 *
 * A [KalendarRule.count] cannot simply be carried across: both halves would then run for the full
 * count and the series would come out longer than the user ever asked for. The continuation gets
 * what the original had left after the [consumed] occurrences that stay behind, and is dropped
 * entirely when nothing is left — or when the original's UNTIL already lies before the new
 * anchor, which is the same situation reached from the other termination form.
 */
private fun KalendarSyncEvent.continuationSeries(
    rule: KalendarRule,
    occurrenceDate: LocalDate,
    edited: KalendarSyncEvent,
    consumed: Int,
): KalendarSyncEvent? {
    val remaining = rule.count?.let { it - consumed }
    if (remaining != null && remaining < 1) {
        return null
    }
    if (rule.until?.let { edited.date > it } == true) {
        return null
    }
    val shiftDays = occurrenceDate.daysUntil(edited.date)
    return edited.copyAsBasic(
        id = null,
        recurrenceRule = rule.copy(count = remaining),
        exceptionDates = exceptionDates.filter { it >= occurrenceDate }.map { it.plus(shiftDays, DateTimeUnit.DAY) },
    )
}

/**
 * Rewrites the whole series in place, keeping its rule. Moving the occurrence moves the series:
 * the anchor, the span, and every exception date shift by the same number of days, so a series
 * edited "all occurrences, one hour later and a day on" keeps its shape instead of tearing the
 * exception list away from the occurrences it belongs to.
 */
private fun KalendarSyncEvent.rewriteWholeSeries(
    occurrenceDate: LocalDate,
    edited: KalendarSyncEvent?,
): KalendarSeriesChange {
    if (edited == null) {
        return KalendarSeriesChange(updatedSeries = null, newEvents = emptyList())
    }
    val shiftDays = occurrenceDate.daysUntil(edited.date)
    val seriesStart = date.plus(shiftDays, DateTimeUnit.DAY)
    return KalendarSeriesChange(
        updatedSeries = edited.copyAsBasic(
            id = id,
            date = seriesStart,
            endDate = edited.endDate?.let { seriesStart.plus(edited.date.daysUntil(it), DateTimeUnit.DAY) },
            recurrenceRule = recurrenceRule,
            exceptionDates = exceptionDates.map { it.plus(shiftDays, DateTimeUnit.DAY) },
        ),
        newEvents = emptyList(),
    )
}

/**
 * Counts the occurrences the **rule** produces strictly before [occurrenceDate].
 *
 * Exception dates are cleared first on purpose. RFC 5545 has EXDATE remove occurrences from an
 * already-determined series rather than shorten it, so an excluded date still consumes a
 * [KalendarRule.count]; counting the expanded, exception-filtered list instead would hand the
 * continuation series too many remaining occurrences.
 */
private fun KalendarSyncEvent.ruleOccurrencesBefore(occurrenceDate: LocalDate): Int =
    copyAsBasic(exceptionDates = emptyList())
        .expandOccurrences(rangeStart = date, rangeEnd = occurrenceDate.minus(1, DateTimeUnit.DAY))
        .size
