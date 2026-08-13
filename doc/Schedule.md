# Kalendar: KalendarSchedule & KalendarScheduleWeek

An hourly time-grid calendar for a single day — the "day view" of a typical calendar app.
Navigable by both arrow-click and swipe, one day per page. Timed events (those with a
`startTime`) render as blocks sized by their duration; overlapping events pack side by side;
all-day events pin as chips above the grid; and a now-indicator line marks the current time on
today's page, refreshing every minute. The grid starts scrolled to early morning.

## Usage

```kotlin
KalendarSchedule(
    selectedDate = today,
    events = listOf(
        BasicKalendarEvent(
            date = today,
            eventName = "Design review",
            startTime = LocalDateTime(today.year, today.monthNumber, today.dayOfMonth, 9, 0),
            endTime = LocalDateTime(today.year, today.monthNumber, today.dayOfMonth, 10, 0),
            eventColor = Color(0xFFEF6C00),
        ),
    ),
    onEventClick = { event -> openDetails(event) },
)
```

Events without an `endTime` render as one hour. Events without a `startTime` are treated as
all-day and shown as chips above the grid rather than on it. An event whose `startTime`/`endTime`
crosses midnight is truncated at the day's edges on each page it touches. `eventColor` tints the
block (falling back to the theme primary).

### Bounds

```kotlin
val state = rememberKalendarScheduleState(
    initialDate = today,
    minDate = today.minus(30, DateTimeUnit.DAY),
    maxDate = today.plus(30, DateTimeUnit.DAY),
)
KalendarSchedule(selectedDate = today, state = state)
```

## Drag to move and resize

When `onEventTimeChange` is set, blocks become draggable: press-and-hold then drag to move an
event to a new time (snapped to `KalendarViewConfig.scheduleDragSnapMinutes`, 15 by default), or
press-and-hold the block's **bottom edge** and drag to change its duration. On the week variant a
horizontal drag also moves the event across days. The callback fires on release with the event and
its new start/end — update your event source to complete the change:

```kotlin
KalendarSchedule(
    selectedDate = today,
    events = events,
    onEventTimeChange = { event, newStart, newEnd ->
        events = events.map {
            if (it == event) it.copy(date = newStart.date, startTime = newStart, endTime = newEnd) else it
        }
    },
)
```

## Week variant

`KalendarScheduleWeek` shows seven day columns side by side, paged by week via
`rememberKalendarWeekState`. Day columns share one hour gutter; each column packs its own
overlapping events; a header row shows each day's label and number with today emphasised; all-day
events appear as chips prefixed with their day of month; the now-indicator appears only on
today's column.

```kotlin
KalendarScheduleWeek(
    selectedDate = today,
    events = events,
    onEventClick = { event -> openDetails(event) },
)
```

Its parameters mirror `KalendarSchedule` exactly, except `state` defaults to
`rememberKalendarWeekState(selectedDate)`.

## Theming

Both views resolve colours through the ambient [`KalendarTheme`](Theme.md) — the event-block
accent falls back to `KalendarColors.eventIndicator`, hour lines use `KalendarColors.gridLine`,
and the now-indicator uses `KalendarColors.nowIndicator`.

## Parameters

| Parameter | Type | Default | Description |
|---|---|---|---|
| `selectedDate` | `LocalDate` | — | The day initially visible when `state` is left at its default. |
| `modifier` | `Modifier` | `Modifier` | Applied to the outermost layout. |
| `state` | `KalendarViewState` | `rememberKalendarScheduleState(selectedDate)` | Navigation state — pages by single day. |
| `events` | `KalendarEvents` | `emptyList()` | Events; only those on the visible day are rendered. |
| `onEventClick` | `(KalendarEvent) -> Unit` | no-op | Called when an event block or all-day chip is tapped. |
| `config` | `KalendarViewConfig` | `KalendarViewConfig()` | Arrows, today button, month-name formatter, background. `disabledDates`/`showAdjacentMonthDates` have no effect here. |
| `hourHeight` | `Dp` | `64.dp` | Vertical space per hour; block heights scale with it. |

## `rememberKalendarScheduleState` parameters

| Parameter | Type | Default | Description |
|---|---|---|---|
| `initialDate` | `LocalDate` | today | The day initially visible. |
| `minDate` | `LocalDate?` | `null` | Bounds the previous arrow button. |
| `maxDate` | `LocalDate?` | `null` | Bounds the next arrow button. |
