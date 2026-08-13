# Kalendar: KalendarWeek

A standalone single-week-row calendar navigable by **both** arrow-click and swipe — not called
through `Kalendar(type = ...)`.

## Usage

```kotlin
val state = rememberKalendarWeekState(initialDate = Clock.System.todayIn(TimeZone.currentSystemDefault()))
var selected by remember { mutableStateOf(setOf(state.visibleDate)) }

KalendarWeek(
    selectedDate = state.visibleDate,
    state = state,
    selectedDates = selected,
    onDateClick = { date, events ->
        selected = setOf(date)
        println("Selected $date with ${events.size} events")
    },
)
```

The arrow buttons in the header and a horizontal swipe on the row both move the same `state` — they
always stay in sync. `config.showNavigationArrows = false` hides the buttons; swiping still works.
The header also shows a today button by default (`config.showTodayButton = true`) that animates
back to the week containing today's date.

Multi-day events (via `KalendarEvent.endDate`) work the same as in
[KalendarMonth](Month.md#multi-day-events). Selection modes
([single/multiple/range](Month.md#selection-modes-single-multiple-range) via
`rememberKalendarSelectionState`), [drag-to-select](Month.md#drag-to-select-a-range) via
`onDateRangeSelect`/`onDateRangeSelectEnd`, the [sliding selection indicator](Month.md#sliding-selection-indicator),
and [locale formatters](Month.md#locale) all work the same way here.

### Programmatic navigation

```kotlin
val state = rememberKalendarWeekState(today)
val scope = rememberCoroutineScope()

KalendarWeek(selectedDate = today, state = state)

Button(onClick = { scope.launch { state.animateScrollTo(LocalDate(2026, 12, 25)) } }) {
    Text("Jump to Christmas week")
}
Button(onClick = { scope.launch { state.animateScrollToToday() } }) {
    Text("Today")
}
```

The built-in today button in the header calls `state.animateScrollToToday()` internally — this
snippet shows how to trigger the same navigation from your own UI.

### Custom day cell

```kotlin
KalendarWeek(
    selectedDate = today,
    dayContent = { scope ->
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(scope.date.dayOfWeek.name.take(1))
            Text(
                text = scope.date.dayOfMonth.toString(),
                color = if (scope.isSelected) Color.Red else Color.Unspecified,
            )
        }
    },
)
```

## Parameters

| Parameter | Type | Default | Description |
|---|---|---|---|
| `selectedDate` | `LocalDate` | — | Default single-selection highlight; the initially visible week when `state` is left at its default. |
| `modifier` | `Modifier` | `Modifier` | Applied to the outermost layout. |
| `state` | `KalendarViewState` | `rememberKalendarWeekState(selectedDate)` | Navigation state — pager position, bounds, `visibleDate`. |
| `events` | `KalendarEvents` | `emptyList()` | Events shown as indicator dots on day cells. |
| `selectedDates` | `Set<LocalDate>` | `setOf(selectedDate)` | Highlighted dates. Hoist your own set for multi-select/range (see [Month.md](Month.md#selection-modes-single-multiple-range)). |
| `onDateClick` | `(LocalDate, List<KalendarEvent>) -> Unit` | no-op | Called when a non-disabled date is tapped. |
| `onDateRangeSelect` | `((start: LocalDate, current: LocalDate) -> Unit)?` | `null` | Press-and-hold then drag to select a range. See [Month.md](Month.md#drag-to-select-a-range). |
| `onDateRangeSelectEnd` | `(() -> Unit)?` | `null` | Called once when a drag started by `onDateRangeSelect` ends. |
| `config` | `KalendarViewConfig` | `KalendarViewConfig()` | Disabled dates, arrow visibility, today button, locale formatters, background. |
| `dayContent` | `(@Composable (KalendarDayScope) -> Unit)?` | `null` | Fully replaces the built-in day cell. |

## `rememberKalendarWeekState` parameters

| Parameter | Type | Default | Description |
|---|---|---|---|
| `initialDate` | `LocalDate` | today | The week initially visible. |
| `startDayOfWeek` | `DayOfWeek` | `MONDAY` | First day of the week row. |
| `minDate` | `LocalDate?` | `null` | Bounds the previous arrow button. |
| `maxDate` | `LocalDate?` | `null` | Bounds the next arrow button. |
