# KalendarScheduleWeek

The seven-day hourly time grid — [`KalendarSchedule`](schedule.md)'s week variant, paged by week.

Seven day columns share a single hour gutter. Each column lays out its own day's timed events as
duration-sized blocks with the same overlap packing. A header row shows each day's label and date
with today emphasised. All-day events pin as chips above the grid, prefixed with the day of month
they belong to, because one row is shared by all seven columns. The now-indicator appears only on
today's column.

## Minimal example

```kotlin
import com.himanshoe.kalendar.view.KalendarScheduleWeek

KalendarScheduleWeek(
    selectedDate = today,
    events = events,
    onEventClick = { event -> openDetails(event) },
)
```

## Parameters

| Parameter | Type | Default | Description |
|---|---|---|---|
| `selectedDate` | `LocalDate` | — | The date whose week is initially visible when `state` is left at its default. |
| `modifier` | `Modifier` | `Modifier` | Applied to the outermost container. |
| `state` | `KalendarViewState` | `rememberKalendarWeekState(selectedDate)` | Navigation state — pages by week. Note this is the **week** factory, not a schedule-specific one. |
| `events` | `KalendarEvents` | `emptyList()` | Events for the calendar; only those in the visible week are rendered. |
| `onEventClick` | `(KalendarEvent) -> Unit` | no-op | Called when an event block or all-day chip is tapped. |
| `onEventTimeChange` | `((event: KalendarEvent, newStart: LocalDateTime, newEnd: LocalDateTime) -> Unit)?` | `null` | When non-null, blocks become draggable and resizable, and a horizontal drag moves the event across day columns. See [Dragging across days](#dragging-across-days). |
| `config` | `KalendarViewConfig` | `KalendarViewConfig()` | Shared settings. `disabledDates` and `showAdjacentMonthDates` have no effect here. See [Configuration](../configuration.md). |
| `hourHeight` | `Dp?` | `null` | Vertical space for one hour; block heights scale with it. `null` means `KalendarDimensions.hourHeight` (64.dp). |
| `eventContent` | `@Composable (KalendarScheduleEventScope) -> Unit` | `KalendarScheduleDefaults.EventBlock` | Slot for one timed event block. |
| `allDayEventContent` | `@Composable (KalendarEvent) -> Unit` | `KalendarScheduleDefaults.AllDayChip`, prefixed with the day of month | Slot for one all-day chip. |
| `hourLabel` | `@Composable (Int) -> Unit` | `KalendarScheduleDefaults.HourLabel` | Slot for one hour-gutter label, given the hour as `0..23`. |
| `nowIndicator` | `@Composable () -> Unit` | `KalendarScheduleDefaults.NowIndicator` | Slot for the current-time line. Drawn only on today's column; the grid owns its offset. |
| `dayHeader` | `@Composable (date: LocalDate, isToday: Boolean) -> Unit` | `KalendarScheduleDefaults.WeekDayHeader` | Slot for one day column's header. Filled to the column's width. |

Everything `KalendarSchedule` documents about
[event layout](schedule.md#how-events-are-laid-out), [dragging](schedule.md#drag-to-move-and-resize),
and [theming](schedule.md#theming) applies here unchanged. This page covers only what differs.

## State factory

`rememberKalendarWeekState` — shared with [`KalendarWeek`](week.md), so the two views can be driven
by the same hoisted state and stay on the same week.

```kotlin
val state = rememberKalendarWeekState(
    initialDate = today,
    startDayOfWeek = DayOfWeek.SUNDAY,
)

Column {
    KalendarWeek(selectedDate = today, state = state)
    KalendarScheduleWeek(selectedDate = today, state = state)
}
```

| Parameter | Type | Default | Description |
|---|---|---|---|
| `initialDate` | `LocalDate` | today | The week initially visible. |
| `startDayOfWeek` | `DayOfWeek` | `MONDAY` | Which day is the leftmost column. |
| `minDate` | `LocalDate?` | `null` | Bounds the previous arrow button. |
| `maxDate` | `LocalDate?` | `null` | Bounds the next arrow button. |

## Dragging across days

With `onEventTimeChange` set, a drag behaves as it does on the day view — vertical movement changes
the time, a bottom-edge drag resizes — and additionally, **horizontal movement moves the event
across day columns**. The day shift is computed from the full day-column width and clamped to six
days in either direction.

The release callback's `newStart` and `newEnd` carry the shifted **date**, not just a new time, so
write both back:

```kotlin
KalendarScheduleWeek(
    selectedDate = today,
    events = events,
    onEventTimeChange = { event, newStart, newEnd ->
        events = events.map { existing ->
            if (existing === event && existing is BasicKalendarEvent) {
                existing.copy(
                    date = newStart.date,
                    startTime = newStart,
                    endTime = newEnd,
                )
            } else {
                existing
            }
        }
    },
)
```

> **Warning:** dropping `date = newStart.date` is the classic bug here — the event visually jumps
> back to its old column on the next recomposition, because the grid places blocks by
> `KalendarEvent.date`, not by `startTime`'s date.

## The all-day row

All seven days share one all-day row, so a chip has to say which day it belongs to. The default
`allDayEventContent` calls `KalendarScheduleDefaults.AllDayChip` with a `prefix` of the day of
month, rendering as `12 · Conference`.

A replacement slot receives only the `KalendarEvent`, so if you need the prefix, pass it to the
built-in chip:

```kotlin
KalendarScheduleWeek(
    selectedDate = today,
    events = events,
    allDayEventContent = { event ->
        KalendarScheduleDefaults.AllDayChip(event = event, prefix = event.date.dayOfMonth.toString())
    },
)
```

## The day header

`dayHeader` is given the column's date and whether it is today, and is stretched to the column's
width. The default draws the day-of-week label (from
[`KalendarViewConfig.dayOfWeekLabelFormatter`](../localization.md)) above the date number, with the
number in `KalendarColors.todayContent` when it is today.

```kotlin
KalendarScheduleWeek(
    selectedDate = today,
    dayHeader = { date, isToday ->
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = date.dayOfWeek.name.take(3))
            Text(
                text = date.dayOfMonth.toString(),
                color = if (isToday) KalendarTheme.colors.todayContent else KalendarTheme.colors.dayContent,
            )
        }
    },
)
```

## The hour gutter

The gutter is `KalendarDimensions.hourGutterWidth` (48.dp) wide, and the day-header row reserves a
spacer of exactly that width so the columns line up with the grid below. Changing the token moves
both together — that is why it is a theme token rather than a parameter.
