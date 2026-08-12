# Kalendar: KalendarTimeline

A standalone, continuously scrolling calendar — every month, forward and backward from the
selected date, in one vertical scroll with no page boundaries. Not called through
`Kalendar(type = ...)`.

Unlike [KalendarMonth](Month.md), there's no header or arrow buttons — scrolling is the only
navigation. Only the months near the current scroll position are ever composed. By default the
list scrolls unlimited in both directions; pass `minDate`/`maxDate` to `rememberKalendarTimelineState`
to bound how far back or forward it goes.

### Bounding how far back/forward it scrolls

```kotlin
val state = rememberKalendarTimelineState(
    initialDate = today,
    minDate = LocalDate(2020, 1, 1),
    maxDate = LocalDate(2030, 12, 31),
)

KalendarTimeline(selectedDate = today, state = state)
```

Leave `minDate`/`maxDate` unset (the default) for unlimited scroll in that direction.

## Usage

```kotlin
val state = rememberKalendarTimelineState(initialDate = Clock.System.todayIn(TimeZone.currentSystemDefault()))
var selected by remember { mutableStateOf(setOf(state.visibleDate)) }

KalendarTimeline(
    selectedDate = state.visibleDate,
    state = state,
    selectedDates = selected,
    onDateClick = { date, events ->
        selected = setOf(date)
        println("Selected $date with ${events.size} events")
    },
)
```

### Programmatic scroll

```kotlin
val state = rememberKalendarTimelineState(today)
val scope = rememberCoroutineScope()

KalendarTimeline(selectedDate = today, state = state)

Button(onClick = { scope.launch { state.scrollTo(LocalDate(2026, 12, 25)) } }) {
    Text("Jump to December")
}
```

### Reading the topmost visible month (e.g. for a screen title)

```kotlin
val state = rememberKalendarTimelineState(today)
Text(text = "${state.visibleDate.month} ${state.visibleDate.year}")
KalendarTimeline(selectedDate = today, state = state)
```

### Custom day cell

```kotlin
KalendarTimeline(
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
| `selectedDate` | `LocalDate` | — | Default single-selection highlight; the month initially at the top of the list when `state` is left at its default. |
| `modifier` | `Modifier` | `Modifier` | Applied to the outermost layout. |
| `state` | `KalendarTimelineState` | `rememberKalendarTimelineState(selectedDate)` | Scroll state — list position, `visibleDate`. |
| `events` | `KalendarEvents` | `emptyList()` | Events shown as indicator dots on day cells. |
| `selectedDates` | `Set<LocalDate>` | `setOf(selectedDate)` | Highlighted dates. Hoist your own set for multi-select/range (see [Month.md](Month.md#range-selection)). |
| `onDateClick` | `(LocalDate, List<KalendarEvent>) -> Unit` | no-op | Called when a non-disabled date is tapped. |
| `config` | `KalendarViewConfig` | `KalendarViewConfig()` | Disabled dates and background. `showNavigationArrows` has no effect — there's no header. |
| `dayContent` | `(@Composable (KalendarDayScope) -> Unit)?` | `null` | Fully replaces the built-in day cell. |

## `rememberKalendarTimelineState` parameters

| Parameter | Type | Default | Description |
|---|---|---|---|
| `initialDate` | `LocalDate` | today | The month initially at the top of the list. |
| `startDayOfWeek` | `DayOfWeek` | `MONDAY` | First day of each month's week columns. |
| `minDate` | `LocalDate?` | `null` | Bounds how far back the list scrolls. Unlimited when `null`. |
| `maxDate` | `LocalDate?` | `null` | Bounds how far forward the list scrolls. Unlimited when `null`. |
