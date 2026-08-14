# Agenda (legacy)

`KalendarType.Agenda` renders a scrollable list of events grouped by date. Events within the same
day are sorted by `startTime` when it is available, with untimed events last. An empty-state message
is shown when no events are provided.

This is the **only surviving `KalendarType`**. It predates the `com.himanshoe.kalendar.view` package
and is reached through the older `Kalendar(type = ...)` entry point with the older `KalendarConfig`
from `kalendar-foundation`. Every grid view is a standalone composable instead — see
[Migration from 1.x](../migration.md).

## Usage

```kotlin
import androidx.compose.ui.graphics.Color
import com.himanshoe.kalendar.Kalendar
import com.himanshoe.kalendar.KalendarType
import com.himanshoe.kalendar.foundation.color.KalendarColor
import com.himanshoe.kalendar.foundation.component.config.KalendarConfig
import com.himanshoe.kalendar.foundation.event.BasicKalendarEvent
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime

val events = listOf(
    BasicKalendarEvent(
        date = LocalDate(2026, 8, 12),
        eventName = "Team standup",
        startTime = LocalDateTime(LocalDate(2026, 8, 12), LocalTime(9, 0)),
        endTime = LocalDateTime(LocalDate(2026, 8, 12), LocalTime(9, 30)),
        eventColor = Color(0xFF4CAF50),
    ),
    BasicKalendarEvent(
        date = LocalDate(2026, 8, 12),
        eventName = "Design review",
        startTime = LocalDateTime(LocalDate(2026, 8, 12), LocalTime(14, 0)),
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
| `type` | `KalendarType` | — | Must be `KalendarType.Agenda`; it is the only value. |
| `modifier` | `Modifier` | `Modifier` | Applied to the outermost layout. |
| `events` | `KalendarEvents` | `emptyList()` | Events to list. Sorted by date, then by `startTime` within each day. |
| `config` | `KalendarConfig` | `KalendarConfig()` | Legacy visual settings. Only two fields have any effect here. |

There are no selection parameters — date selection belongs to the
[standalone views](index.md).

## Which `KalendarConfig` fields matter

The agenda renders no day grid, so most of the legacy config is accepted and ignored. Only these two
do anything:

| Field | Type | Default | Effect |
|---|---|---|---|
| `backgroundColor` | `KalendarColor` | `KalendarColor.Solid(Color.White)` | Background colour or gradient of the list container. |
| `headerConfig` | `KalendarHeaderConfig` | `KalendarHeaderConfig.default()` | Its `textStyle` is used for each date group header. |

Ignored here: `showDayLabel`, `showArrows`, `startDayOfWeek`, `firstVisibleDate`, `minDate`,
`maxDate`, `initialSelectedDates`, `initialSelectedRange`, `disabledDates`, `onVisibleRangeChange`,
`showWeekNumbers`, `calendarColors`, `dayConfig`, `dayLabelConfig`.

## What it draws

Each date becomes a group header (`"12 August 2026"`), followed by one card per event showing a
colour dot from `KalendarEvent.eventColor`, the event name, its optional description, and a
`HH:mm – HH:mm` time label built from `startTime` and `endTime`.

> **Note:** the agenda list does **not** resolve its colours through
> [`KalendarTheme`](../theming.md). It predates the token layer and uses fixed colours plus
> `KalendarConfig.backgroundColor`. If you want a themed, restylable event list, build one from
> [`KalendarSchedule`](schedule.md) or from your own `LazyColumn` over the same `KalendarEvents`.
