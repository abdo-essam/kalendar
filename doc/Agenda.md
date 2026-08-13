# Kalendar: Agenda

An agenda-style calendar that renders a scrollable list of events grouped by date. Events within the
same day are sorted by `KalendarEvent.startTime` when available. An empty-state message is displayed
when no events are provided.

## Usage

```kotlin
val events = listOf(
    BasicKalendarEvent(
        date = LocalDate(2026, 4, 14),
        eventName = "Team standup",
        startTime = LocalDateTime(LocalDate(2026, 4, 14), LocalTime(9, 0)),
        endTime = LocalDateTime(LocalDate(2026, 4, 14), LocalTime(9, 30)),
        eventColor = Color(0xFF4CAF50),
    ),
    BasicKalendarEvent(
        date = LocalDate(2026, 4, 14),
        eventName = "Design review",
        startTime = LocalDateTime(LocalDate(2026, 4, 14), LocalTime(14, 0)),
    ),
)

Kalendar(
    type = KalendarType.Agenda,
    events = events,
    config = KalendarConfig(
        backgroundColor = KalendarColor.Solid(Color.White),
    ),
)
```

## Parameters

| Parameter | Type | Default | Description |
|---|---|---|---|
| `type` | `KalendarType` | — | Must be `KalendarType.Agenda`. |
| `modifier` | `Modifier` | `Modifier` | Applied to the outermost layout. |
| `events` | `KalendarEvents` | `emptyList()` | Events to list. Sorted by date, then by `startTime` within each day. |
| `config` | `KalendarConfig` | `KalendarConfig()` | Visual settings — the agenda list uses `backgroundColor` and `headerConfig`; see below. |

> **Note:** The `Kalendar` composable takes no selection-related parameters — date selection is
> only supported by the standalone view-package composables (`KalendarMonth`, `KalendarWeek`, …),
> which are documented in [Config.md](Config.md).

## `KalendarConfig` fields

`KalendarConfig` is the legacy configuration class from `kalendar-foundation`. The agenda list
renders no day grid, so only two of its fields have an effect here:

| Field | Type | Default | Effect on `KalendarType.Agenda` |
|---|---|---|---|
| `backgroundColor` | `KalendarColor` | `KalendarColor.Solid(Color.White)` | Background colour or gradient of the list container. |
| `headerConfig` | `KalendarHeaderConfig` | `KalendarHeaderConfig.default()` | `textStyle` is used for each date group header. |

The remaining fields configure day-grid behaviour (labels, navigation, selection) that the agenda
list does not render, so they are accepted but ignored:

| Field | Type | Default |
|---|---|---|
| `showDayLabel` | `Boolean` | `true` |
| `showArrows` | `Boolean` | `true` |
| `startDayOfWeek` | `DayOfWeek` | `DayOfWeek.MONDAY` |
| `firstVisibleDate` | `LocalDate?` | `null` |
| `minDate` | `LocalDate?` | `null` |
| `maxDate` | `LocalDate?` | `null` |
| `initialSelectedDates` | `List<LocalDate>` | `emptyList()` |
| `initialSelectedRange` | `KalendarSelectedDayRange?` | `null` |
| `disabledDates` | `(LocalDate) -> Boolean` | `{ false }` |
| `onVisibleRangeChange` | `((start: LocalDate, end: LocalDate) -> Unit)?` | `null` |
| `showWeekNumbers` | `Boolean` | `false` |
| `calendarColors` | `Map<String, Color>` | `emptyMap()` |
| `dayConfig` | `KalendarDayConfig` | `KalendarDayConfig()` |
| `dayLabelConfig` | `KalendarDayLabelConfig` | `KalendarDayLabelConfig.default()` |

Event rows show a colour dot taken from `KalendarEvent.eventColor` (with a fixed fallback colour
when it is `null`).
