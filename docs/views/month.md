# KalendarMonth

The full month grid — seven columns, one page per month. The header's arrow buttons and a horizontal
swipe drive the same `KalendarViewState`, so they always stay in sync.

This is the most capable of the date-grid views: it is the only one with drag-to-reschedule.

## Minimal example

```kotlin
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.himanshoe.kalendar.view.KalendarMonth
import com.himanshoe.kalendar.view.rememberKalendarMonthState
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlin.time.Clock

@Composable
fun MonthGrid() {
    val today = remember { Clock.System.todayIn(TimeZone.currentSystemDefault()) }
    val state = rememberKalendarMonthState(initialDate = today)
    var selected by remember { mutableStateOf(setOf(today)) }

    KalendarMonth(
        selectedDate = today,
        state = state,
        selectedDates = selected,
        onDateClick = { date, _ -> selected = setOf(date) },
    )
}
```

## Parameters

| Parameter | Type | Default | Description |
|---|---|---|---|
| `selectedDate` | `LocalDate` | — | Default single-selection highlight, and the initially visible month when `state` is left at its default. Superseded by `selectedDates` when that is passed. |
| `modifier` | `Modifier` | `Modifier` | Applied to the outermost container. |
| `state` | `KalendarViewState` | `rememberKalendarMonthState(selectedDate)` | Navigation state. Hoist it to read `visibleDate` or navigate from your own UI. |
| `events` | `KalendarEvents` | `emptyList()` | Shown as indicator dots on day cells. |
| `selectedDates` | `Set<LocalDate>` | `setOf(selectedDate)` | The highlighted dates. Hoist your own set for multi-select or range. |
| `onDateClick` | `(date: LocalDate, events: List<KalendarEvent>) -> Unit` | no-op | Called when a non-disabled date is tapped, with that date's events. |
| `onDateRangeSelect` | `((start: LocalDate, current: LocalDate) -> Unit)?` | `null` | When non-null, press-and-hold then drag selects a range live. |
| `onDateRangeSelectEnd` | `(() -> Unit)?` | `null` | Called once when a drag started by `onDateRangeSelect` ends. |
| `onEventDrop` | `((events: List<KalendarEvent>, newDate: LocalDate) -> Unit)?` | `null` | When non-null, press-and-hold a date **that has events** and drag to another date to reschedule. See [Drag to reschedule](#drag-to-reschedule). |
| `config` | `KalendarViewConfig` | `KalendarViewConfig()` | Shared visual and behavioural settings. See [Configuration](../configuration.md). |
| `header` | `@Composable (KalendarHeaderScope) -> Unit` | `KalendarHeaderDefaults.Header` | Replaces the navigation header above the grid. See [Customization](../customization.md). |
| `dayOfWeekLabel` | `@Composable (DayOfWeek) -> Unit` | `KalendarHeaderDefaults.DayOfWeekLabel` | Replaces the content of each weekday column header. |
| `dayContent` | `(@Composable (scope: KalendarDayScope) -> Unit)?` | `null` | Replaces the built-in day cell entirely. See [Customization](../customization.md). |

## State factory

`rememberKalendarMonthState`.

```kotlin
val state = rememberKalendarMonthState(
    initialDate = today,
    startDayOfWeek = DayOfWeek.SUNDAY,
    minDate = LocalDate(2020, 1, 1),
    maxDate = LocalDate(2030, 12, 31),
)
```

| Parameter | Type | Default | Description |
|---|---|---|---|
| `initialDate` | `LocalDate` | today | The month initially visible. |
| `startDayOfWeek` | `DayOfWeek` | `MONDAY` | The grid's first column. |
| `minDate` | `LocalDate?` | `null` | Turns `canScrollBackward` false once the previous month would start before this date's month. |
| `maxDate` | `LocalDate?` | `null` | Turns `canScrollForward` false once the next month would start after this date's month. |

Bounds are compared in *page* space, so a `minDate` that falls mid-month (say the 15th) still lets
you reach the month that contains it.

## Adjacent-month dates

A month grid needs leading and trailing padding days to complete its first and last weeks.
`KalendarViewConfig.showAdjacentMonthDates` decides what happens to them:

| Value | Result |
|---|---|
| `true` (default) | The neighbouring month's dates are drawn dimmed, at `KalendarColors.disabledContentAlpha`. |
| `false` | Those cells are blank placeholders that still hold the grid's alignment. |

Either way, adjacent-month dates are **not tappable** — they count as disabled.

## Selection

`selectedDates` is a plain hoisted set, so a tap does whatever you say it does. For single, multiple,
and range behaviour, `rememberKalendarSelectionState` implements the tap logic and survives
configuration changes and process death:

```kotlin
val selection = rememberKalendarSelectionState(mode = KalendarSelectionMode.Range)

KalendarMonth(
    selectedDate = today,
    selectedDates = selection.selectedDates,
    onDateClick = { date, _ -> selection.onDateClick(date) },
)
```

For a hand-rolled range, `LocalDate.datesUntil(other)` returns every date between the two, inclusive,
in either order:

```kotlin
import com.himanshoe.kalendar.view.datesUntil

var rangeStart by remember { mutableStateOf<LocalDate?>(null) }
var rangeEnd by remember { mutableStateOf<LocalDate?>(null) }
val start = rangeStart
val end = rangeEnd

KalendarMonth(
    selectedDate = today,
    selectedDates = if (start != null && end != null) start.datesUntil(end) else emptySet(),
    onDateClick = { date, _ ->
        if (rangeStart == null || rangeEnd != null) {
            rangeStart = date
            rangeEnd = null
        } else {
            rangeEnd = date
        }
    },
)
```

### Sliding selection indicator

When exactly one date is selected, a single shared indicator slides between cells rather than each
cell fading independently. On by default; turn it off with
`KalendarViewConfig(showSelectionIndicator = false)`. It is drawn behind the cell, so a custom
`dayContent` keeps the animation.

### Drag to select a range

```kotlin
val selection = rememberKalendarSelectionState(mode = KalendarSelectionMode.Range)

KalendarMonth(
    selectedDate = today,
    selectedDates = selection.selectedDates,
    onDateClick = { date, _ -> selection.onDateClick(date) },
    onDateRangeSelect = selection::onRangeDrag,
    onDateRangeSelectEnd = selection::onRangeDragEnd,
)
```

A long press is required before the drag begins, so a quick tap still reaches `onDateClick`.

## Drag to reschedule

With `onEventDrop` set, press and hold a date **that has events**, then drag: a ghost highlight
follows the cell under the pointer, and releasing calls the callback with the pressed date's events
and the drop date.

The calendar never mutates your events — update your own source to complete the move:

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

> **Warning:** `KalendarEvent` is an interface — it has no `copy`. Only the concrete implementation
> does. Smart-cast to `BasicKalendarEvent` (as above) or rebuild the event from your own domain
> model. And copying a multi-day event onto a new start date without also moving its `endDate` will
> silently change the event's length, so decide explicitly what should happen to the span.

Dates *without* events fall through to `onDateRangeSelect`'s range behaviour, so drag-to-reschedule
and drag-to-select can both be enabled at once.

## Events on the grid

Each day cell draws one dot per event, coloured by `KalendarEvent.eventColor` and falling back to
`KalendarColors.eventIndicator`. Past `KalendarViewConfig.eventIndicatorCap` (3 by default) the
remainder collapses into a compact `+N` label.

Multi-day events (`KalendarEvent.endDate`) appear on every date they span. See
[Events](../events.md).
