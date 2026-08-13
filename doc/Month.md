# Kalendar: KalendarMonth

A standalone full-month grid calendar navigable by **both** arrow-click and swipe — not called
through `Kalendar(type = ...)`. Displays one month at a time in a 7-column week grid.

## Usage

```kotlin
val state = rememberKalendarMonthState(initialDate = Clock.System.todayIn(TimeZone.currentSystemDefault()))
var selected by remember { mutableStateOf(setOf(state.visibleDate)) }

KalendarMonth(
    selectedDate = state.visibleDate,
    state = state,
    selectedDates = selected,
    onDateClick = { date, events ->
        selected = setOf(date)
        println("Selected $date with ${events.size} events")
    },
)
```

The arrow buttons in the header and a horizontal swipe on the grid both move the same `state` — they
always stay in sync. `config.showNavigationArrows = false` hides the buttons; swiping still works.
`config.showAdjacentMonthDates = false` leaves the previous/next month's padding cells blank
instead of showing them dimmed. The header also shows a today button by default
(`config.showTodayButton = true`) that animates back to the month containing today's date; set it
to `false` to hide it.

### Multi-day events

Set [`KalendarEvent.endDate`](../kalendar-foundation/src/commonMain/kotlin/com/himanshoe/kalendar/foundation/event/KalendarEvent.kt)
to show an event's indicator dot on every day it spans, not just its start date:

```kotlin
KalendarMonth(
    selectedDate = today,
    events = listOf(
        BasicKalendarEvent(
            date = LocalDate(2026, 8, 12),
            endDate = LocalDate(2026, 8, 14),
            eventName = "Conference",
        ),
    ),
)
```

The dot appears on August 12th, 13th, and 14th, inclusive. Leave `endDate` at its default (`null`)
for a single-day event. This applies to `KalendarWeek`, `KalendarYear`, and `KalendarTimeline` too.

### Bounds and disabled dates

```kotlin
val state = rememberKalendarMonthState(
    initialDate = today,
    startDayOfWeek = DayOfWeek.SUNDAY,
    minDate = LocalDate(2020, 1, 1),
    maxDate = LocalDate(2030, 12, 31),
)

KalendarMonth(
    selectedDate = today,
    state = state,
    config = KalendarViewConfig(disabledDates = { date -> date.dayOfWeek == DayOfWeek.SUNDAY }),
)
```

`state.canScrollBackward`/`state.canScrollForward` (used internally to disable the arrow buttons)
are also public, so you can drive your own UI off them. `minDate`/`maxDate` bound the arrow
buttons; swiping past them is not blocked.

### Selection modes: single, multiple, range

`selectedDates` is a plain hoisted `Set<LocalDate>` — you decide what tapping a date does. For the
three common cases, `rememberKalendarSelectionState` implements the tap behaviour for you:

```kotlin
val selection = rememberKalendarSelectionState(mode = KalendarSelectionMode.Range)

KalendarMonth(
    selectedDate = today,
    selectedDates = selection.selectedDates,
    onDateClick = { date, _ -> selection.onDateClick(date) },
)
```

- `KalendarSelectionMode.Single` (the default) — tapping a date replaces the selection.
- `KalendarSelectionMode.Multiple` — tapping toggles a date in or out of the selection.
- `KalendarSelectionMode.Range` — first tap starts a range, second tap completes it (every date in
  between, inclusive, regardless of tap order); the next tap after that starts a fresh range.

Selecting exactly one date (the default for `Single` mode) also gets a sliding indicator — see
[Sliding selection indicator](#sliding-selection-indicator) below. Call `selection.clear()` to
reset. For anything this doesn't cover (a minimum range length, disallowed end dates, etc.), manage
`selectedDates` yourself instead:

```kotlin
var rangeStart by remember { mutableStateOf<LocalDate?>(null) }
var rangeEnd by remember { mutableStateOf<LocalDate?>(null) }

KalendarMonth(
    selectedDate = today,
    selectedDates = if (rangeStart != null && rangeEnd != null) {
        rangeStart!!.datesUntil(rangeEnd!!)
    } else {
        emptySet()
    },
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

When exactly one date is selected (and no custom `dayContent` is supplied), the selection highlight
animates smoothly from the previously selected cell to the newly tapped one instead of each cell
fading independently. It resets — snapping instantly rather than sliding — whenever the visible
month changes, so it never slides in from off-screen when you swipe or use the arrow buttons.
Selecting more than one date (multi-select or range) falls back to each cell highlighting itself
independently, since there's no single indicator to slide.

### Drag-to-select a range

Press and hold a date, then drag, to select a range live as your finger moves — a quick tap still
does whatever `onDateClick` does, since a long-press is required before the drag starts:

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

`onDateRangeSelect` fires continuously with `(the date under your initial press, the date under
your finger now)` as the drag moves; `onDateRangeSelectEnd` fires once when you lift your finger.
Leave both `null` (the default) to disable drag-select entirely — only tap-to-select applies then.

### Locale

kotlinx-datetime doesn't do locale-aware formatting, so month names and day-of-week labels default
to plain English. Pass your own formatters — backed by `java.time`, `NSDateFormatter`, `Intl`, or
similar — for a localized calendar:

```kotlin
KalendarMonth(
    selectedDate = today,
    config = KalendarViewConfig(
        monthNameFormatter = { month -> /* e.g. java.time.Month.of(month.ordinal + 1)... */ "" },
        dayOfWeekLabelFormatter = { dayOfWeek -> /* ... */ "" },
    ),
)
```

`monthNameFormatter` is used for the header title, the year view's month labels, and each day
cell's accessibility description; `dayOfWeekLabelFormatter` is used for the day-of-week column
headers (defaults to the first letter, e.g. "M" for Monday).

### Reading the visible month (e.g. for a screen title)

```kotlin
val state = rememberKalendarMonthState(today)
Text(text = "${state.visibleDate.month} ${state.visibleDate.year}")
KalendarMonth(selectedDate = today, state = state)
```

### Custom day cell

```kotlin
KalendarMonth(
    selectedDate = today,
    dayContent = { scope ->
        Box(
            modifier = Modifier
                .aspectRatio(1f)
                .background(if (scope.isSelected) Color.Blue else Color.Transparent),
            contentAlignment = Alignment.Center,
        ) {
            Text(scope.date.dayOfMonth.toString())
        }
    },
)
```

## Parameters

| Parameter | Type | Default | Description |
|---|---|---|---|
| `selectedDate` | `LocalDate` | — | Default single-selection highlight; the initially visible month when `state` is left at its default. |
| `modifier` | `Modifier` | `Modifier` | Applied to the outermost layout. |
| `state` | `KalendarViewState` | `rememberKalendarMonthState(selectedDate)` | Navigation state — pager position, bounds, `visibleDate`. |
| `events` | `KalendarEvents` | `emptyList()` | Events shown as indicator dots on day cells. |
| `selectedDates` | `Set<LocalDate>` | `setOf(selectedDate)` | Highlighted dates. Hoist your own set for multi-select/range. |
| `onDateClick` | `(LocalDate, List<KalendarEvent>) -> Unit` | no-op | Called when a non-disabled date is tapped. |
| `onDateRangeSelect` | `((start: LocalDate, current: LocalDate) -> Unit)?` | `null` | Press-and-hold then drag to select a range; called continuously as the drag moves. See [Drag-to-select a range](#drag-to-select-a-range). |
| `onDateRangeSelectEnd` | `(() -> Unit)?` | `null` | Called once when a drag started by `onDateRangeSelect` ends. |
| `config` | `KalendarViewConfig` | `KalendarViewConfig()` | Disabled dates, arrow visibility, adjacent-month dates, today button, locale formatters, background. |
| `dayContent` | `(@Composable (KalendarDayScope) -> Unit)?` | `null` | Fully replaces the built-in day cell. |

## `rememberKalendarMonthState` parameters

| Parameter | Type | Default | Description |
|---|---|---|---|
| `initialDate` | `LocalDate` | today | The month initially visible. |
| `startDayOfWeek` | `DayOfWeek` | `MONDAY` | First day of week column. |
| `minDate` | `LocalDate?` | `null` | Bounds the previous arrow button. |
| `maxDate` | `LocalDate?` | `null` | Bounds the next arrow button. |
