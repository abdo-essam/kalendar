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
| `selectedDates` | `Set<LocalDate>` | `setOf(selectedDate)` | Highlighted dates. Hoist your own set for multi-select/range (see [Month.md](Month.md#range-selection)). |
| `onDateClick` | `(LocalDate, List<KalendarEvent>) -> Unit` | no-op | Called when a non-disabled date is tapped. |
| `config` | `KalendarViewConfig` | `KalendarViewConfig()` | Disabled dates, arrow visibility, background. |
| `dayContent` | `(@Composable (KalendarDayScope) -> Unit)?` | `null` | Fully replaces the built-in day cell. |

## `rememberKalendarWeekState` parameters

| Parameter | Type | Default | Description |
|---|---|---|---|
| `initialDate` | `LocalDate` | today | The week initially visible. |
| `startDayOfWeek` | `DayOfWeek` | `MONDAY` | First day of the week row. |
| `minDate` | `LocalDate?` | `null` | Bounds the previous arrow button. |
| `maxDate` | `LocalDate?` | `null` | Bounds the next arrow button. |
