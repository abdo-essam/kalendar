# Events

Every view takes the same `events: KalendarEvents` list and renders it in the way that suits its
shape: indicator dots on the date grids, duration-sized blocks on the
[Schedule views](views/schedule.md).

`KalendarEvents` is a plain `typealias` for `List<KalendarEvent>`, so you build one with ordinary
Kotlin collection code.

## `KalendarEvent`

The interface every event implements. Only `date`, `eventName`, and `eventDescription` are required;
everything else defaults, so existing implementations stay source-compatible as the model grows.

| Property | Type | Default | Description |
|---|---|---|---|
| `date` | `LocalDate` | — | The date the event falls on, or the **first** date for a multi-day event. |
| `endDate` | `LocalDate?` | `null` | The last date the event occupies, inclusive. `null` confines it to `date`. |
| `eventName` | `String` | — | Short, human-readable name, e.g. `"Team standup"`. |
| `eventDescription` | `String?` | — | Optional longer description. |
| `startTime` | `LocalDateTime?` | `null` | When the event begins. Drives within-day ordering and Schedule layout. |
| `endTime` | `LocalDateTime?` | `null` | When the event ends. Shown wherever duration matters. |
| `eventColor` | `Color?` | `null` | Tints the event's indicator dot and block. Falls back to `KalendarColors.eventIndicator`. |
| `calendarId` | `String?` | `null` | Optional source-calendar identifier (`"work"`, `"personal"`), carried as metadata. The built-in views do not read it. |

> **Note:** `startTime` and `endTime` are `LocalDateTime`, not `LocalTime` — they carry a date as
> well as a time. Keep their date in sync with `date`; the grid places blocks by `date`, so a
> mismatch shows the event on one day with another day's times.

## `BasicKalendarEvent`

The ready-made implementation. It is a `data class`, so it has `copy()` — worth knowing, because
`KalendarEvent` itself does not.

```kotlin
import androidx.compose.ui.graphics.Color
import com.himanshoe.kalendar.foundation.event.BasicKalendarEvent
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime

val day = LocalDate(2026, 8, 12)

val events = listOf(
    BasicKalendarEvent(
        date = day,
        eventName = "Team standup",
        eventDescription = "Daily sync",
        startTime = LocalDateTime(day, LocalTime(9, 0)),
        endTime = LocalDateTime(day, LocalTime(9, 30)),
        eventColor = Color(0xFF4CAF50),
    ),
    BasicKalendarEvent(
        date = day,
        eventName = "All-hands",
    ),
)
```

Its constructor parameter order is `date`, `eventName`, `eventDescription`, `startTime`, `endTime`,
`eventColor`, `calendarId`, `endDate` — pass them by name and the order stops mattering.

### Using your own event type

`KalendarEvent` is an interface precisely so you do not have to convert your domain model:

```kotlin
data class Meeting(
    val id: Long,
    val title: String,
    val on: LocalDate,
    val from: LocalDateTime?,
    val to: LocalDateTime?,
) : KalendarEvent {
    override val date: LocalDate get() = on
    override val eventName: String get() = title
    override val eventDescription: String? get() = null
    override val startTime: LocalDateTime? get() = from
    override val endTime: LocalDateTime? get() = to
}
```

Pass `List<Meeting>` straight in — it is already a `List<KalendarEvent>`.

## Multi-day events

Set `endDate` and the event occupies every date from `date` through `endDate`, inclusive:

```kotlin
BasicKalendarEvent(
    date = LocalDate(2026, 8, 12),
    endDate = LocalDate(2026, 8, 14),
    eventName = "Conference",
)
```

The indicator dot appears on the 12th, 13th, and 14th. On the date grids, tapping any of those days
delivers the event in `onDateClick`'s events list.

This works on [`KalendarWeek`](views/week.md), [`KalendarMonth`](views/month.md),
[`KalendarYear`](views/year.md), and [`KalendarTimeline`](views/timeline.md).

> **Warning:** expansion is capped at **366 days**. An `endDate` far in the future is silently
> truncated rather than looping unbounded — so a runaway span degrades instead of hanging the UI. An
> `endDate` earlier than `date` is ignored and the event stays single-day.

## Indicator dots and the `+N` overflow

On the date grids, each day cell draws one dot per event below the day number:

- Dot colour comes from `eventColor`, falling back to `KalendarColors.eventIndicator`.
- On a **selected** cell the dots switch to the day number's colour, so they stay legible against
  the selection fill.
- Past `KalendarViewConfig.eventIndicatorCap` (**3** by default) the remainder collapses into a
  compact `+N` label — with four events you get three dots and `+1`.

Tune the cap per calendar:

```kotlin
KalendarMonth(
    selectedDate = today,
    events = events,
    config = KalendarViewConfig(eventIndicatorCap = 2),
)
```

The cap must be at least `1`; `KalendarViewConfig` throws on construction otherwise.

Dot size, spacing, and the gap above them are [dimension tokens](theming.md)
(`eventIndicatorSize`, `eventIndicatorSpacing`, `eventIndicatorTopPadding`); the `+N` text style is
`KalendarTypography.eventOverflowLabel`; the dot shape is `KalendarShapes.eventIndicator`. The label
text itself comes from [`KalendarStrings.eventOverflowLabel`](localization.md).

With [`KalendarAnimations.enabled`](theming.md) at its default, the dot row fades in when a day
gains its first event.

## Events on the Schedule views

The Schedule views split the same list in two:

| Event | Rendered as |
|---|---|
| Has a `startTime` | A block on the hour grid, sized by its duration. |
| No `startTime` | An all-day chip in the row above the grid. |

A missing `endTime` means a one-hour block. Anything shorter than 15 minutes is drawn 15 minutes
tall so it stays tappable. An event crossing midnight is truncated at each day's edges. Overlapping
events are packed into side-by-side columns.

See [`KalendarSchedule`](views/schedule.md) for the full layout rules.

## Reacting to taps

The date grids report the tapped date and everything on it:

```kotlin
KalendarMonth(
    selectedDate = today,
    events = events,
    onDateClick = { date, eventsOnDate ->
        showDaySheet(date, eventsOnDate)
    },
)
```

The Schedule views report the specific event instead:

```kotlin
KalendarSchedule(
    selectedDate = today,
    events = events,
    onEventClick = { event -> openDetails(event) },
)
```

## Drag to reschedule

Two different gestures, on two different views. Both are opt-in, and **neither mutates your
events** — each reports the intended change and leaves the write to you.

### Across dates, on `KalendarMonth`

Press and hold a date **that has events**, then drag to another date. A ghost highlight follows the
cell under the pointer; releasing calls `onEventDrop` with the pressed date's events and the drop
date.

```kotlin
var events by remember { mutableStateOf(initialEvents) }

KalendarMonth(
    selectedDate = today,
    events = events,
    onEventDrop = { dropped, newDate ->
        events = events.map { event ->
            if (event in dropped && event is BasicKalendarEvent) {
                event.copy(date = newDate, endDate = null)
            } else {
                event
            }
        }
    },
)
```

Dates *without* events fall through to `onDateRangeSelect`, so drag-to-reschedule and
drag-to-select can both be enabled at once.

### Across times, on the Schedule views

Press and hold a block to move it; press and hold its bottom edge to resize it. Movement snaps to
`KalendarViewConfig.scheduleDragSnapMinutes` (15 by default). On
[`KalendarScheduleWeek`](views/schedule-week.md) a horizontal drag also moves the event across day
columns.

```kotlin
KalendarSchedule(
    selectedDate = today,
    events = events,
    onEventTimeChange = { event, newStart, newEnd ->
        events = events.map { existing ->
            if (existing === event && existing is BasicKalendarEvent) {
                existing.copy(date = newStart.date, startTime = newStart, endTime = newEnd)
            } else {
                existing
            }
        }
    },
)
```

### Pitfalls worth knowing

> **Warning:** `KalendarEvent` is an interface and has **no `copy`**. Only concrete implementations
> like `BasicKalendarEvent` do. Smart-cast (as above) or rebuild the event from your domain model.

> **Warning:** always write `date` back alongside `startTime`. The grid places blocks by
> `KalendarEvent.date`, so an event whose `startTime` moved to a new day but whose `date` did not
> will visually snap back to its old column on the next recomposition.

> **Warning:** copying a multi-day event onto a new start date without moving its `endDate` silently
> changes the event's length. Decide explicitly whether a drop should preserve the span (shift
> `endDate` by the same number of days) or collapse it.

> **Warning:** a drag reaching the bottom of the day reports an end of `00:00` on the **following**
> date, because `LocalTime` cannot express `24:00`. Handle the date rollover when you write it back.

## Performance

Both `events` and `selectedDates` are read on every recomposition of the grid, and the views
`remember` derived maps keyed on the list instance. Hoist a stable list — from `remember`,
`mutableStateOf`, or a `StateFlow` — rather than rebuilding it inline in the composable body, or the
derived grouping is recomputed every frame.
