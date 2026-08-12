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

### Range selection

There's no built-in range-selection mode — `selectedDates` is a plain hoisted `Set<LocalDate>`, so
a two-tap range is a few lines using the [`datesUntil`](../kalendar/src/commonMain/kotlin/com/himanshoe/kalendar/view/KalendarDateRange.kt) helper:

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
| `config` | `KalendarViewConfig` | `KalendarViewConfig()` | Disabled dates, arrow visibility, background. |
| `dayContent` | `(@Composable (KalendarDayScope) -> Unit)?` | `null` | Fully replaces the built-in day cell. |

## `rememberKalendarMonthState` parameters

| Parameter | Type | Default | Description |
|---|---|---|---|
| `initialDate` | `LocalDate` | today | The month initially visible. |
| `startDayOfWeek` | `DayOfWeek` | `MONDAY` | First day of week column. |
| `minDate` | `LocalDate?` | `null` | Bounds the previous arrow button. |
| `maxDate` | `LocalDate?` | `null` | Bounds the next arrow button. |
