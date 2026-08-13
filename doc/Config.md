# Kalendar – Configuration Reference

Every standalone view — `KalendarWeek`, `KalendarMonth`, `KalendarYear`, `KalendarTimeline`,
`KalendarSchedule`, `KalendarScheduleWeek` — takes the same `KalendarViewConfig` via its `config`
parameter. Options that only apply to some views say so; passing them elsewhere is harmless.

```kotlin
KalendarMonth(
    selectedDate = today,
    config = KalendarViewConfig(
        disabledDates = { it.dayOfWeek == DayOfWeek.SUNDAY },
        eventIndicatorCap = 2,
        hourLabelFormatter = { hour -> twelveHourLabel(hour) },
    ),
)
```

## KalendarViewConfig

| Parameter | Type | Default | Description |
|---|---|---|---|
| `disabledDates` | `(LocalDate) -> Boolean` | `{ false }` | Dates rendered dimmed with taps ignored. |
| `showNavigationArrows` | `Boolean` | `true` | Previous/next arrows in the header; swiping works regardless. No effect on `KalendarTimeline`. |
| `showAdjacentMonthDates` | `Boolean` | `true` | Show the previous/next month's padding dates dimmed, or leave those cells blank. Month-grid views only. |
| `showTodayButton` | `Boolean` | `true` | Header button (sticky-header button on Timeline) that animates back to today. |
| `showJumpPicker` | `Boolean` | `true` | Tapping the header title opens a month/year picker that jumps straight to the chosen month. Not on Timeline (no header title). |
| `monthNameFormatter` | `(Month) -> String` | English names | Month names for titles, labels, and accessibility descriptions — plug in a locale-aware platform formatter. |
| `dayOfWeekLabelFormatter` | `(DayOfWeek) -> String` | First letter | Day-of-week column labels. |
| `hourLabelFormatter` | `(Int) -> String` | `"HH:00"` | Hour-gutter labels on the Schedule views; supply your own for 12-hour AM/PM. |
| `scheduleInitialScrollHour` | `Int` | `7` | Hour the Schedule views scroll to when a page appears (0–23). |
| `scheduleDragSnapMinutes` | `Int` | `15` | Snap step for dragging/resizing Schedule event blocks. |
| `eventIndicatorCap` | `Int` | `3` | Max per-event dots under a day number before collapsing to `+N`. |
| `background` | `Brush` | solid white | Background of the calendar container. |

Colours are themed separately through [`KalendarTheme`](Theme.md); per-view state (initial date,
week start day, min/max bounds) lives on the `rememberKalendar*State` functions; selection
behaviour is hoisted via `selectedDates`/`onDateClick` or [`rememberKalendarSelectionState`](Month.md#selection-modes-single-multiple-range).

## Legacy

The `Kalendar(type = KalendarType.Agenda)` composable predates the view package and takes the
older `KalendarConfig` from `kalendar-foundation` — see [Agenda](Agenda.md). All other former
`KalendarType`s were removed in 2.0.
