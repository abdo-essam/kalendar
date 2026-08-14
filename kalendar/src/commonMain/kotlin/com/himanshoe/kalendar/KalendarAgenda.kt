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

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.util.fastForEach
import com.himanshoe.kalendar.foundation.event.KalendarEvent
import com.himanshoe.kalendar.foundation.event.KalendarEvents
import com.himanshoe.kalendar.foundation.event.byDateExpandingSpans
import com.himanshoe.kalendar.theme.kalendarColors
import com.himanshoe.kalendar.theme.kalendarDimensions
import com.himanshoe.kalendar.theme.kalendarShapes
import com.himanshoe.kalendar.theme.kalendarStrings
import com.himanshoe.kalendar.theme.kalendarTypography
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.Month

/**
 * A vertically-scrolling list of events grouped by date, with a header above each day's events —
 * the view to reach for when the events matter more than the grid they fall on.
 *
 * Dates with no events are skipped entirely, so the list is as long as the events are dense.
 * Multi-day events (those with [KalendarEvent.endDate] set) appear under every date they occupy,
 * exactly as they do on the grid views, and events within a day are ordered by
 * [KalendarEvent.startTime] with all-day events last. There is no navigation chrome: the scroll is
 * the only navigation, so the header, arrow, today-button and jump-picker options on [config] have
 * no effect here.
 *
 * Both drawn pieces are slots — [eventContent] and [emptyState] default to the matching
 * [KalendarAgendaDefaults] rendering, and the date header is [dateHeader] — so you can replace one
 * and leave the rest built-in. The list keeps the click handling around a replaced row, so custom
 * content never loses it.
 *
 * ```kotlin
 * KalendarAgenda(
 *     events = events,
 *     onEventClick = { event -> openDetails(event) },
 * ) { event ->
 *     MyEventCard(title = event.eventName)
 * }
 * ```
 *
 * @param events The events to list. Supply [KalendarEvent.id] to keep scroll and per-row state
 *   attached to the right event across edits; without it the list falls back to each event's
 *   position within its date.
 * @param modifier [Modifier] applied to the outermost container.
 * @param onEventClick Called when an event row is tapped.
 * @param config Shared visual/behavioural settings. Only
 *   [KalendarViewConfig.monthNameFormatter] — which localizes the date headers — and
 *   [KalendarViewConfig.background] apply; every other option is about a grid or a pager this view
 *   does not have.
 * @param state Scroll state for the underlying list. Own it yourself to read the scroll position
 *   or to scroll programmatically.
 * @param dateHeader Slot for one date's header, given the date its events fall on. Defaults to
 *   [KalendarAgendaDefaults.DateHeader] formatted with [KalendarViewConfig.monthNameFormatter].
 * @param emptyState Slot shown centred in place of the list when [events] is empty. Defaults to
 *   [KalendarAgendaDefaults.EmptyState], which shows [KalendarStrings.agendaEmptyState].
 * @param eventContent Slot for one event row; the list applies [onEventClick] around it. Last, so
 *   trailing-lambda syntax lands on it — this is the view's primary slot. Defaults to
 *   [KalendarAgendaDefaults.EventRow].
 */
@Composable
public fun KalendarAgenda(
    events: KalendarEvents,
    modifier: Modifier = Modifier,
    onEventClick: (KalendarEvent) -> Unit = {},
    config: KalendarViewConfig = KalendarViewConfig(),
    state: LazyListState = rememberLazyListState(),
    dateHeader: @Composable (date: LocalDate) -> Unit = { date ->
        KalendarAgendaDefaults.DateHeader(date = date, monthNameFormatter = config.monthNameFormatter)
    },
    emptyState: @Composable () -> Unit = { KalendarAgendaDefaults.EmptyState() },
    eventContent: @Composable (event: KalendarEvent) -> Unit = { event ->
        KalendarAgendaDefaults.EventRow(event = event)
    },
) {
    val colors = kalendarColors()
    val dimensions = kalendarDimensions()
    val grouped = remember(events) { events.groupedForAgenda() }
    val background = config.background ?: SolidColor(colors.background)

    Box(modifier = modifier.background(background)) {
        if (grouped.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                emptyState()
            }
        } else {
            LazyColumn(
                state = state,
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(dimensions.agendaRowSpacing),
            ) {
                grouped.fastForEach { (date, dateEvents) ->
                    val day = date.toEpochDays()
                    item(key = "header-$day", contentType = AGENDA_HEADER_CONTENT_TYPE) {
                        dateHeader(date)
                    }
                    items(
                        count = dateEvents.size,
                        key = { index -> "event-$day-${dateEvents[index].id ?: index}" },
                        contentType = { AGENDA_EVENT_CONTENT_TYPE },
                    ) { index ->
                        val event = dateEvents[index]
                        Box(modifier = Modifier.clickable { onEventClick(event) }) {
                            eventContent(event)
                        }
                    }
                }
            }
        }
    }
}

/**
 * The built-in renderings behind [KalendarAgenda]'s content slots.
 *
 * Every slot defaults to one of these, which also makes them the pieces to reach for when you want
 * to keep the built-in look and only add to it — wrap one in your own container, or fall back to it
 * for the events you don't render specially:
 *
 * ```kotlin
 * KalendarAgenda(events = events) { event ->
 *     if (event.calendarId == "work") {
 *         MyWorkEventRow(event)
 *     } else {
 *         KalendarAgendaDefaults.EventRow(event = event)
 *     }
 * }
 * ```
 *
 * All of them draw only: the list owns scrolling and clicks.
 */
public object KalendarAgendaDefaults {

    /**
     * The built-in date header: the day, month and year in [KalendarTypography.headerTitle],
     * inset by [KalendarDimensions.sectionPadding].
     *
     * @param date The date whose events follow the header.
     * @param monthNameFormatter Turns [date]'s month into its name —
     *   [KalendarViewConfig.monthNameFormatter] by default, which is the hook that localizes it.
     * @param modifier [Modifier] applied to the header.
     */
    @Composable
    public fun DateHeader(
        date: LocalDate,
        monthNameFormatter: (Month) -> String,
        modifier: Modifier = Modifier,
    ) {
        val colors = kalendarColors()
        BasicText(
            text = "${date.day} ${monthNameFormatter(date.month)} ${date.year}",
            style = kalendarTypography().headerTitle.copy(color = colors.headerContent),
            modifier = modifier.fillMaxWidth().padding(kalendarDimensions().sectionPadding),
        )
    }

    /**
     * The built-in event row: the event's accent dot beside its name, description and time range,
     * on a [KalendarColors.agendaCard] surface clipped to [KalendarShapes.agendaCard]. The
     * description and the time range are each dropped when the event has none.
     *
     * @param event The event to draw, as handed to the `eventContent` slot.
     * @param modifier [Modifier] applied to the row.
     */
    @Composable
    public fun EventRow(event: KalendarEvent, modifier: Modifier = Modifier) {
        val colors = kalendarColors()
        val typography = kalendarTypography()
        val dimensions = kalendarDimensions()
        val accent = event.eventColor ?: colors.eventIndicator
        Row(
            modifier = modifier
                .fillMaxWidth()
                .clip(kalendarShapes().agendaCard)
                .background(colors.agendaCard)
                .padding(dimensions.agendaRowPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dimensions.agendaRowGap),
        ) {
            Box(
                modifier = Modifier
                    .size(dimensions.agendaDotSize)
                    .clip(kalendarShapes().eventIndicator)
                    .background(accent),
            )
            Column(modifier = Modifier.weight(1f)) {
                BasicText(
                    text = event.eventName,
                    style = typography.agendaEventTitle.copy(color = colors.dayContent),
                )
                event.eventDescription?.let { description ->
                    BasicText(
                        text = description,
                        style = typography.agendaEventSubtitle.copy(color = colors.dayLabelContent),
                    )
                }
                agendaTimeLabel(start = event.startTime, end = event.endTime)?.let { timeLabel ->
                    BasicText(
                        text = timeLabel,
                        style = typography.agendaEventSubtitle.copy(color = colors.dayLabelContent),
                    )
                }
            }
        }
    }

    /**
     * The built-in empty state: [KalendarStrings.agendaEmptyState] in
     * [KalendarTypography.agendaEventTitle]. [KalendarAgenda] centres it for you.
     *
     * @param modifier [Modifier] applied to the message.
     */
    @Composable
    public fun EmptyState(modifier: Modifier = Modifier) {
        BasicText(
            text = kalendarStrings().agendaEmptyState,
            style = kalendarTypography().agendaEventTitle.copy(color = kalendarColors().dayLabelContent),
            modifier = modifier,
        )
    }
}

private const val AGENDA_HEADER_CONTENT_TYPE = "kalendar-agenda-header"
private const val AGENDA_EVENT_CONTENT_TYPE = "kalendar-agenda-event"

/**
 * The agenda's sections: every date that has at least one event, in ascending order, each paired
 * with that date's events ordered by start time with all-day events last.
 *
 * Multi-day events are expanded onto each date they occupy by [byDateExpandingSpans], so the same
 * event can appear in more than one section — which is why the list keys on the date as well as
 * the event.
 */
private fun KalendarEvents.groupedForAgenda(): List<Pair<LocalDate, List<KalendarEvent>>> =
    byDateExpandingSpans()
        .entries
        .sortedBy { it.key }
        .map { (date, dateEvents) ->
            date to dateEvents.sortedWith(compareBy(nullsLast()) { it.startTime })
        }

/** `"09:00 – 10:30"`, the end omitted when unknown, or `null` for an all-day event. */
private fun agendaTimeLabel(start: LocalDateTime?, end: LocalDateTime?): String? {
    if (start == null) {
        return null
    }
    val startLabel = formatAgendaTime(start.time)
    val endLabel = end?.let { formatAgendaTime(it.time) } ?: return startLabel
    return "$startLabel – $endLabel"
}

private fun formatAgendaTime(time: LocalTime): String {
    val hour = time.hour.toString().padStart(2, '0')
    val minute = time.minute.toString().padStart(2, '0')
    return "$hour:$minute"
}
