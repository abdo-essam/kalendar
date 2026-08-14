# Views

Kalendar's six views are **standalone composables**. You call `KalendarMonth(...)` directly — there
is no `Kalendar(type = ...)` dispatcher for them (see [Migration from 1.x](../migration.md)).

They all live in `com.himanshoe.kalendar.view`, share the same
[`KalendarViewConfig`](../configuration.md), resolve their visuals through the same
[`KalendarTheme`](../theming.md), and take the same [`KalendarEvents`](../events.md) list.

## Choosing one

| View | Shape | Paging unit | State factory |
|---|---|---|---|
| [`KalendarWeek`](week.md) | One week row | Week | `rememberKalendarWeekState` |
| [`KalendarMonth`](month.md) | Month grid | Month | `rememberKalendarMonthState` |
| [`KalendarYear`](year.md) | 12 month grids in a scrolling column | Year | `rememberKalendarYearState` |
| [`KalendarTimeline`](timeline.md) | Continuous month list | none — free scroll | `rememberKalendarTimelineState` |
| [`KalendarSchedule`](schedule.md) | Hourly grid for one day | Day | `rememberKalendarScheduleState` |
| [`KalendarScheduleWeek`](schedule-week.md) | Hourly grid, 7 day columns | Week | `rememberKalendarWeekState` |

## What they have in common

**Date-grid views** — `KalendarWeek`, `KalendarMonth`, `KalendarYear`, `KalendarTimeline` — render
day cells, take `selectedDates` / `onDateClick`, and show events as indicator dots.

**Schedule views** — `KalendarSchedule` and `KalendarScheduleWeek` — render an hourly time grid,
take `onEventClick` / `onEventTimeChange`, and lay events out as duration-sized blocks. They have no
concept of date selection.

### Feature matrix

| Feature | Week | Month | Year | Timeline | Schedule | ScheduleWeek |
|---|:-:|:-:|:-:|:-:|:-:|:-:|
| Arrow + swipe navigation | yes | yes | yes | no | yes | yes |
| Today button | yes | yes | yes | yes | yes | yes |
| Month/year jump picker | yes | yes | yes | no | yes | yes |
| `selectedDates` / `onDateClick` | yes | yes | yes | yes | no | no |
| Sliding selection indicator | yes | yes | no | no | no | no |
| Drag-to-select a range | yes | yes | no | no | no | no |
| Drag events to a new date | no | yes | no | no | no | no |
| Drag / resize event blocks | no | no | no | no | yes | yes |
| Event indicator dots and `+N` | yes | yes | yes | yes | no | no |
| `dayContent` slot | yes | yes | yes | yes | no | no |
| `header` slot | yes | yes | yes | yes | no | no |
| `dayOfWeekLabel` slot | yes | yes | yes | yes | no | no |
| Event / hour / now-indicator slots | no | no | no | no | yes | yes |

`KalendarTimeline` scrolls continuously, so it has no pages and no arrow buttons — its `header` slot
receives a scope whose page vocabulary is mapped onto months, and
`KalendarViewConfig.showNavigationArrows` and `showJumpPicker` are ignored there.

The Schedule views draw the built-in navigation header directly and do not expose a `header` slot;
style it through [`KalendarTheme`](../theming.md) instead. They do expose slots for everything
*inside* the grid — see [Customization](../customization.md).

## The state factories

Each factory wraps a `PagerState` (or a `LazyListState` for the timeline) and exposes it in date
terms. All of them share the same four parameters, except `rememberKalendarScheduleState`, which has
no `startDayOfWeek` — a single day has no week columns to align.

| Parameter | Type | Default | Description |
|---|---|---|---|
| `initialDate` | `LocalDate` | today, system time zone | The week / month / year / day initially visible. |
| `startDayOfWeek` | `DayOfWeek` | `DayOfWeek.MONDAY` | The first week column. Not on `rememberKalendarScheduleState`. |
| `minDate` | `LocalDate?` | `null` | Lower bound. Unbounded when `null`. |
| `maxDate` | `LocalDate?` | `null` | Upper bound. Unbounded when `null`. |

### `KalendarViewState`

Returned by `rememberKalendarWeekState`, `rememberKalendarMonthState`, `rememberKalendarYearState`,
and `rememberKalendarScheduleState`.

| Member | Type | Description |
|---|---|---|
| `visibleDate` | `LocalDate` | Start date of the page currently on screen. |
| `startDayOfWeek` | `DayOfWeek` | The week's first column. |
| `minDate` / `maxDate` | `LocalDate?` | The bounds this state was created with. |
| `canScrollBackward` | `Boolean` | Whether stepping one page back stays within `minDate`. |
| `canScrollForward` | `Boolean` | Whether stepping one page forward stays within `maxDate`. |
| `suspend animateScrollTo(date)` | `Unit` | Animates to the page containing `date`, clamped to the bounds. |
| `suspend animateScrollToToday()` | `Unit` | Animates to the page containing today, clamped. |
| `suspend animateToPreviousPage()` | `Unit` | One page back. A no-op past `minDate`. |
| `suspend animateToNextPage()` | `Unit` | One page forward. A no-op past `maxDate`. |

### `KalendarTimelineState`

Returned by `rememberKalendarTimelineState`.

| Member | Type | Description |
|---|---|---|
| `visibleDate` | `LocalDate` | Start of the month currently topmost in the list. |
| `startDayOfWeek` | `DayOfWeek` | The week's first column. |
| `minDate` / `maxDate` | `LocalDate?` | The bounds this state was created with. |
| `suspend scrollTo(date)` | `Unit` | Animates so the month containing `date` reaches the top. |
| `suspend scrollToToday()` | `Unit` | Animates back to today's month. |

> **Warning:** `minDate` / `maxDate` bound the buttons, not the swipe. On the paged views the bounds
> disable the header's arrow buttons and clamp `animateScrollTo`, but a swipe is not blocked and can
> carry you past them. On `KalendarTimeline` the bounds *do* clamp the scroll itself, because the
> list is built with a finite item count.

## Also available

- [Agenda (legacy)](agenda.md) — the one surviving `KalendarType`, a scrollable event list grouped
  by date.
