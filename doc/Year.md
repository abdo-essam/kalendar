# Kalendar: KalendarYear

A standalone year-overview calendar (all 12 months in a scrollable column) navigable by **both**
arrow-click and swipe — not called through `Kalendar(type = ...)`.

## Usage

```kotlin
val state = rememberKalendarYearState(initialDate = Clock.System.todayIn(TimeZone.currentSystemDefault()))
var selected by remember { mutableStateOf(setOf(state.visibleDate)) }

KalendarYear(
    selectedDate = state.visibleDate,
    state = state,
    selectedDates = selected,
    onDateClick = { date, events ->
        selected = setOf(date)
        println("Selected $date")
    },
)
```

The arrow buttons in the header (labelled with the year) and a horizontal swipe both move the same
`state` — they always stay in sync. Each page is one year, scrollable vertically through its 12
months. The header also shows a today button by default (`config.showTodayButton = true`) that
animates back to the year containing today's date.

Multi-day events (via `KalendarEvent.endDate`) work the same as in
[KalendarMonth](Month.md#multi-day-events). So do selection modes
([single/multiple/range](Month.md#selection-modes-single-multiple-range)) and
[locale formatters](Month.md#locale) — `onDateRangeSelect`/the sliding selection indicator are
Week/Month-only, since Year has 12 separate grids on screen at once.

## Parameters

| Parameter | Type | Default | Description |
|---|---|---|---|
| `selectedDate` | `LocalDate` | — | Default single-selection highlight; the initially visible year when `state` is left at its default. |
| `modifier` | `Modifier` | `Modifier` | Applied to the outermost layout. |
| `state` | `KalendarViewState` | `rememberKalendarYearState(selectedDate)` | Navigation state — pager position, bounds, `visibleDate`. |
| `events` | `KalendarEvents` | `emptyList()` | Events shown as indicator dots on day cells. |
| `selectedDates` | `Set<LocalDate>` | `setOf(selectedDate)` | Highlighted dates. Hoist your own set for multi-select/range (see [Month.md](Month.md#selection-modes-single-multiple-range)). |
| `onDateClick` | `(LocalDate, List<KalendarEvent>) -> Unit` | no-op | Called when a non-disabled date is tapped. |
| `config` | `KalendarViewConfig` | `KalendarViewConfig()` | Disabled dates, arrow visibility, adjacent-month dates, today button, locale formatters, background. |
| `dayContent` | `(@Composable (KalendarDayScope) -> Unit)?` | `null` | Fully replaces the built-in day cell for every month. |

## `rememberKalendarYearState` parameters

| Parameter | Type | Default | Description |
|---|---|---|---|
| `initialDate` | `LocalDate` | today | The year initially visible. |
| `startDayOfWeek` | `DayOfWeek` | `MONDAY` | First day of each month's week columns. |
| `minDate` | `LocalDate?` | `null` | Bounds the previous arrow button. |
| `maxDate` | `LocalDate?` | `null` | Bounds the next arrow button. |
