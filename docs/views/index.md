# Views

Kalendar's seven views are **standalone composables**. You call `KalendarMonth(...)` directly — there
is no `Kalendar(type = ...)` dispatcher any more (see [Migration from 1.x](../migration.md)).

They all live in `com.himanshoe.kalendar.view`, share the same
[`KalendarViewConfig`](../configuration.md), resolve their visuals through the same
[`KalendarTheme`](../theming.md), and take the same [`KalendarEvents`](../events.md) list.

## What they look like

Every picture below is drawn by the library itself when this site is built — same composables, same
default theme, same events — so none of them can drift from the code they document. Each follows the
light/dark switch in the top bar.

<div class="shot-gallery">
<figure>
<img src="../images/week-theme.png" alt="A one-week row for 9–15 March 2026 with the 12th selected and event dots under four of the days.">
<figcaption><a href="week.md"><code>KalendarWeek</code></a><p>One week row, paged by week.</p></figcaption>
</figure>
<figure>
<img src="../images/month-theme.png" alt="The March 2026 month grid with the 12th selected, dimmed February dates in the leading row, and event dots with a plus-one overflow badge.">
<figcaption><a href="month.md"><code>KalendarMonth</code></a><p>The full month grid, paged by month.</p></figcaption>
</figure>
<figure>
<img src="../images/year-theme.png" alt="The top of the 2026 year page, showing the January and February grids stacked in one scrolling column with the 15th of January selected.">
<figcaption><a href="year.md"><code>KalendarYear</code></a><p>Twelve month grids in one scrolling page.</p></figcaption>
</figure>
<figure>
<img src="../images/timeline-theme.png" alt="A continuous month list scrolled to the join between March and April 2026, with no page break between the two grids.">
<figcaption><a href="timeline.md"><code>KalendarTimeline</code></a><p>Every month in one uninterrupted scroll.</p></figcaption>
</figure>
<figure>
<img src="../images/schedule-theme.png" alt="The hourly day grid for 10 March 2026 with an all-day chip above it and two overlapping meetings packed side by side.">
<figcaption><a href="schedule.md"><code>KalendarSchedule</code></a><p>One day as an hourly time grid.</p></figcaption>
</figure>
<figure>
<img src="../images/schedule-week-theme.png" alt="The seven-day time grid for 9–15 March 2026, seven day columns sharing one hour gutter with coloured event blocks.">
<figcaption><a href="schedule-week.md"><code>KalendarScheduleWeek</code></a><p>Seven day columns, one hour gutter.</p></figcaption>
</figure>
<figure>
<img src="../images/agenda-theme.png" alt="An agenda list for March 2026: each date is a heading with the day's events as cards underneath, each card carrying a coloured dot, a name, a description and a time range.">
<figcaption><a href="agenda.md"><code>KalendarAgenda</code></a><p>Events grouped by date, no grid at all.</p></figcaption>
</figure>
</div>

## Choosing one

| View | Shape | Paging unit | State factory |
|---|---|---|---|
| [`KalendarWeek`](week.md) | One week row | Week | `rememberKalendarWeekState` |
| [`KalendarMonth`](month.md) | Month grid | Month | `rememberKalendarMonthState` |
| [`KalendarYear`](year.md) | 12 month grids in a scrolling column | Year | `rememberKalendarYearState` |
| [`KalendarTimeline`](timeline.md) | Continuous month list | none — free scroll | `rememberKalendarTimelineState` |
| [`KalendarSchedule`](schedule.md) | Hourly grid for one day | Day | `rememberKalendarScheduleState` |
| [`KalendarScheduleWeek`](schedule-week.md) | Hourly grid, 7 day columns | Week | `rememberKalendarWeekState` |
| [`KalendarAgenda`](agenda.md) | Event list grouped by date | none — free scroll | `rememberLazyListState` |

## What they have in common

**Date-grid views** — `KalendarWeek`, `KalendarMonth`, `KalendarYear`, `KalendarTimeline` — render
day cells, take `selectedDates` / `onDateClick`, and show events as indicator dots.

**Schedule views** — `KalendarSchedule` and `KalendarScheduleWeek` — render an hourly time grid,
take `onEventClick` / `onEventTimeChange`, and lay events out as duration-sized blocks. They have no
concept of date selection.

**The agenda** — `KalendarAgenda` — has no grid and no pager at all: it lists the events themselves,
grouped by date, and skips every date that has none. It takes `onEventClick` and, like the Schedule
views, has no concept of date selection.

### Feature matrix

| Feature | Week | Month | Year | Timeline | Schedule | ScheduleWeek | Agenda |
|---|:-:|:-:|:-:|:-:|:-:|:-:|:-:|
| Arrow + swipe navigation | yes | yes | yes | no | yes | yes | no |
| Today button | yes | yes | yes | yes | yes | yes | no |
| Month/year jump picker | yes | yes | yes | no | yes | yes | no |
| `selectedDates` / `onDateClick` | yes | yes | yes | yes | no | no | no |
| Sliding selection indicator | yes | yes | no | no | no | no | no |
| Drag-to-select a range | yes | yes | no | no | no | no | no |
| Drag events to a new date | no | yes | no | no | no | no | no |
| Drag / resize event blocks | no | no | no | no | yes | yes | no |
| Event indicator dots and `+N` | yes | yes | yes | yes | no | no | no |
| `dayContent` slot | yes | yes | yes | yes | no | no | no |
| `header` slot | yes | yes | yes | yes | no | no | no |
| `dayOfWeekLabel` slot | yes | yes | yes | yes | no | no | no |
| Event / hour / now-indicator slots | no | no | no | no | yes | yes | no |
| `dateHeader` / `eventContent` / `emptyState` slots | no | no | no | no | no | no | yes |

`KalendarTimeline` scrolls continuously, so it has no pages and no arrow buttons — its `header` slot
receives a scope whose page vocabulary is mapped onto months, and
`KalendarViewConfig.showNavigationArrows` and `showJumpPicker` are ignored there.

The Schedule views draw the built-in navigation header directly and do not expose a `header` slot;
style it through [`KalendarTheme`](../theming.md) instead. They do expose slots for everything
*inside* the grid — see [Customization](../customization.md).

## The state factories

Each factory wraps a `PagerState` (or a `LazyListState` for the timeline) and exposes it in date
terms. All of them share the same five parameters, except `rememberKalendarScheduleState`, which has
no `startDayOfWeek` — a single day has no week columns to align.

| Parameter | Type | Default | Description |
|---|---|---|---|
| `initialDate` | `LocalDate` | today, system time zone | The week / month / year / day initially visible. |
| `startDayOfWeek` | `DayOfWeek` | `DayOfWeek.MONDAY` | The first week column. Not on `rememberKalendarScheduleState`. |
| `minDate` | `LocalDate?` | `null` | Lower bound. Unbounded when `null`. |
| `maxDate` | `LocalDate?` | `null` | Upper bound. Unbounded when `null`. |
| `timeSource` | `KalendarTimeSource` | the ambient one | Where the state reads "today" from — the seam between the calendar and the wall clock. |

`initialDate` itself defaults to today *as read through `timeSource`*, so wrapping a screen in
`ProvideKalendarTimeSource(fixed) { … }` pins every view's idea of the current date at once: the
`today` highlight, the today button's target, and the Schedule views' now-indicator. That is how the
screenshots on this page are made — a fixed clock, so the pictures cannot drift. See
[The clock](../configuration.md#the-clock).

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
| `suspend animateScrollTo(date)` | `Unit` | Animates so the month containing `date` reaches the top. |
| `suspend animateScrollToToday()` | `Unit` | Animates back to today's month. |
| `timeSource` | `KalendarTimeSource` | Where this state reads "today" from. Mutable, so a hoisted state can follow a changing clock. |

> **Warning:** `minDate` / `maxDate` bound the buttons, not the swipe. On the paged views the bounds
> disable the header's arrow buttons and clamp `animateScrollTo`, but a swipe is not blocked and can
> carry you past them. On `KalendarTimeline` the bounds *do* clamp the scroll itself, because the
> list is built with a finite item count.

`KalendarAgenda` has neither: it takes a plain `LazyListState`, because a list of events has nothing
to page through and nothing to bound.
