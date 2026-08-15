# Events

Every view takes an `events: List<E>` list and renders it in the way that suits its shape: indicator
dots on the date grids, a continuous bar for anything [spanning more than one day](#multi-day-events),
duration-sized blocks on the [Schedule views](views/schedule.md).

`E` is **your** event type — any implementation of `KalendarEvent`, inferred from the list you pass —
and every callback and slot hands it straight back. See
[Using your own event type](#using-your-own-event-type).

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
| `eventColor` | `Color?` | `null` | Tints the event's indicator dot, span bar, and block. Falls back to `KalendarColors.eventIndicator`. |
| `calendarId` | `String?` | `null` | Optional source-calendar identifier (`"work"`, `"personal"`), carried as metadata. The built-in views do not read it. |
| `id` | `String?` | `null` | Optional stable identifier, unique across the list. Views that keep per-item state use it as their list key — see [Stable identifiers](#stable-identifiers). |

> **Note:** `startTime` and `endTime` are `LocalDateTime`, not `LocalTime` — they carry a date as
> well as a time. Keep their date in sync with `date`; the grid places blocks by `date`, so a
> mismatch shows the event on one day with another day's times.

## `BasicKalendarEvent`

The ready-made implementation. It is an `@Immutable` class with a **hand-written** `copy()` — worth
knowing on both counts. It has `copy()` where `KalendarEvent` itself does not; and being a plain
class rather than a `data class`, it has no `componentN`, so it cannot be destructured. Value
`equals`, `hashCode` and `toString` all work as you would expect.

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
`eventColor`, `calendarId`, `endDate`, `id` — pass them by name and the order stops mattering.

### Stable identifiers

`id` defaults to `null`, so nothing that already implements `KalendarEvent` has to change. Set it and
[`KalendarAgenda`](views/agenda.md) keys its list items on it, which is what keeps scroll position
and per-row state attached to the right event when the list is edited underneath them. Without one
the key falls back to the event's position within its date — fine for a list that never changes, and
wrong for one that does.

```kotlin
BasicKalendarEvent(
    date = LocalDate(2026, 3, 10),
    eventName = "Design review",
    id = "cal-events/8871",
)
```

It is also the handle to look an event back up when a click callback hands you one, instead of
matching on name and date.

### Using your own event type

`KalendarEvent` is an interface precisely so you do not have to convert your domain model:

```kotlin
data class Meeting(
    val meetingId: Long,
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
    override val id: String get() = meetingId.toString()
}
```

Pass `List<Meeting>` straight in. The view is generic in the event type, so `E` is inferred as
`Meeting` and **stays** `Meeting` all the way back out — `onDateClick` reports `List<Meeting>`, a
`dayContent` slot's `scope.events` is `List<Meeting>`, `onEventClick` reports a `Meeting`:

```kotlin
KalendarMonth(
    selectedDate = today,
    events = meetings,
    onDateClick = { date, dayMeetings -> open(dayMeetings.map { it.meetingId }) },
)
```

There is no cast anywhere in that snippet, and that is the point: before the views were generic, a
callback could only hand back `KalendarEvent`, so reaching `meetingId` meant `event as Meeting` — a
cast the compiler could not check and the API had no way to justify.

Two consequences worth knowing:

- **With no events, there is nothing to infer from.** `KalendarMonth(selectedDate = today)` still
  compiles: it resolves to an event-free overload that has no `events` parameter at all. A default
  value could not have covered this, because a Kotlin default argument is never a source of type
  inference.
- **A mixed list widens.** `listOf(meeting, someOtherEvent)` has the common supertype
  `List<KalendarEvent>`, so `E` becomes `KalendarEvent` and the callbacks report that. Keep the list
  homogeneous if you want your own type back — or accept `KalendarEvent` and branch on it, which is
  what a calendar aggregating several sources genuinely wants.

## Multi-day events

Set `endDate` and the event occupies every date from `date` through `endDate`, inclusive:

```kotlin
BasicKalendarEvent(
    date = LocalDate(2026, 8, 12),
    endDate = LocalDate(2026, 8, 14),
    eventName = "Conference",
)
```

The event occupies the 12th, 13th, and 14th. On the date grids, tapping any of those days delivers
the event in `onDateClick`'s events list.

This works on [`KalendarWeek`](views/week.md), [`KalendarMonth`](views/month.md),
[`KalendarYear`](views/year.md), and [`KalendarTimeline`](views/timeline.md).

> **Warning:** expansion is capped at **366 days**. An `endDate` far in the future is silently
> truncated rather than looping unbounded — so a runaway span degrades instead of hanging the UI. An
> `endDate` equal to or earlier than `date` is not a span at all: the event stays single-day and
> draws the ordinary dot.

### How a span is drawn

A multi-day event draws as a **continuous bar**, not as one dot per day. The bar runs edge to edge
across every cell the event covers, so the cells either side of it join into one unbroken strip —
which is what tells a three-day conference apart from three unrelated single-day events.

The bar breaks at the **week boundary**, because a grid row is a week and there is nothing between
Saturday and the following Sunday to run across. A Thursday-to-Tuesday event is therefore two bars,
one per row. The two ends are drawn differently on purpose:

| End of a bar | Drawn as | Reads as |
|---|---|---|
| The event's real first or last day | Inset by `spanBarEndInset` and rounded by `spanBarCornerRadius` | The event starts/finishes here |
| Cut by the end of a row, or continuing into tomorrow | Square, flush to the cell's edge | Continues past this cell |

Both insets are start/end relative rather than left/right, so the reading survives a right-to-left
calendar. The bar keeps `eventColor` even on the **selected** day, where the dots switch to the
selection's content colour — a bar that changed colour mid-run would read as two events.

### Lanes, and the cap

Overlapping spans are packed into **lanes** — horizontal rows within the strip under the day number
— rather than stacking arbitrarily. A lane belongs to the event for its whole run, so a bar sits at
one height across all of its days instead of climbing a staircase. Lanes are assigned once from the
whole event list, which is why every cell in a grid reserves the same number of them and every day
number keeps its shared baseline.

The number of lanes is capped by **`spanBarMaxLanes`, which defaults to 2**. Two lanes is what a day
cell has room for at the size a phone-width month grid produces; a third fits only on a tablet, and
would be reserved on every calendar whether or not anything ever overlapped to use it.

**A span past the last lane is not hidden.** It falls back to the indicator dot it would have drawn
anyway, so it stays visible on each of its days and still counts towards the `+N` overflow and the
popover behind it. What it loses is the visual continuity, not the event. Nothing is ever silently
dropped: whatever a cell cannot show as a bar or a dot is still listed in the overflow popover.

Two smaller cases worth knowing:

- A cell **too short** for the whole strip drops lanes one at a time, and a very short cell (a
  `KalendarYear` month, say) abandons the strip entirely and gives the height back to the day
  number. A lane dropped this way is a layout decision the `+N` label — computed during composition
  — does not know about, so the count can under-report on such a cell. The popover still lists
  everything.
- A `KalendarDayCellDefaults.Cell` composed **outside** any grid has no lane assignment to read, and
  renders its multi-day events as dots.

Raise the cap, or turn bars off altogether, through the [dimension tokens](theming.md):

```kotlin
KalendarTheme(dimensions = KalendarTheme.dimensions.copy(spanBarMaxLanes = 3)) {
    KalendarMonth(selectedDate = today, events = events)
}
```

`spanBarMaxLanes = 0` disables span bars, and every multi-day event goes back to being a dot on each
of its days. The value must be at least `0`; `KalendarDimensions` throws on construction otherwise.
Bar thickness, spacing, corner radius, and end inset are the `spanBarHeight`, `spanBarSpacing`,
`spanBarCornerRadius`, and `spanBarEndInset` tokens.

## Indicator dots and the `+N` overflow

On the date grids, each day cell draws one dot below the day number per event **not already drawn as
a [span bar](#lanes-and-the-cap)**:

- Dot colour comes from `eventColor`, falling back to `KalendarColors.eventIndicator`.
- On a **selected** cell the dots switch to the day number's colour, so they stay legible against
  the selection fill.
- Past `KalendarViewConfig.eventIndicatorCap` (**3** by default) the remainder collapses into a
  compact `+N` label — with four events you get three dots and `+1`. Tapping that label opens a
  popover listing the day's **whole** event list; wire `onDayEventClick` to react to a tap inside it,
  and replace the popover through `KalendarDayCellDefaults.Cell`'s `overflowPopup` slot.

The cap counts only the events drawn as dots. A multi-day event that got a bar lane has been shown,
not withheld, so it is not one of them and does not push another event into the `+N`.

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
(`eventIndicatorSize`, `eventIndicatorSpacing`, `eventIndicatorBottomPadding`, and
`eventIndicatorStripHeight`, which reserves the strip's height so a cell with events and one without
put the day number on the same baseline — the span-bar lanes are reserved above it on the same
principle); the `+N` text style is
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

`KalendarResourceView` splits them differently: it draws only the timed ones, because a lane grid has
no all-day row to pin the rest to.

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

Both report **your** event type. `eventsOnDate` above is a `List<Meeting>` when `events` was a
`List<Meeting>`.

The Schedule views report the specific event instead:

```kotlin
KalendarSchedule(
    events = meetings,
    onEventClick = { meeting -> openDetails(meeting.meetingId) },
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

Press and hold a block to move it; press and hold **either its top or its bottom edge** to move that
edge alone, changing the start or the end while the other stays put. The grab strip is
`KalendarDimensions.eventBlockResizeHandleHeight`, narrowed on short blocks so the middle stays
grabbable. Movement snaps to `KalendarViewConfig.scheduleDragSnapMinutes` (15 by default).

On [`KalendarScheduleWeek`](views/schedule-week.md) a horizontal drag also moves the event across day
columns; on `KalendarResourceView` it moves the event between lanes, and the callback reports the id
of the lane it landed in as a fourth argument.

Dragging is clamped to `KalendarViewConfig.scheduleVisibleHours` rather than to the whole day, so a
drag cannot push a block off a business-hours grid into space the user has no way to scroll back to.

```kotlin
KalendarSchedule(
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

### Sweeping out a new event

`KalendarSchedule` and `KalendarScheduleWeek` take an `onEventCreate` callback. When it is non-null,
a press-and-drag on **empty** grid sweeps out a time range, snapped to the same
`scheduleDragSnapMinutes`, and reports it on release:

```kotlin
KalendarSchedule(
    events = events,
    onEventCreate = { start, end -> events = events + BasicKalendarEvent(
        date = start.date,
        eventName = "New event",
        startTime = start,
        endTime = end,
    ) },
)
```

A sweep shorter than 15 minutes is grown to 15, so a tap-sized gesture still produces a usable event.

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

The index the grids build is
[`byDateExpandingSpans()`](reference/foundation-event.md), which is public: if you are laying out
cells yourself on the [engine](engine.md), wrap it in `remember(events)` exactly as the views do.

Event slots on the hour grids are **keyed by the event**, so inserting one at the top of a day
recomposes the newcomer and nothing else — measured in [Performance](performance.md).
