# `kalendar` — views

`com.himanshoe.kalendar`

The nine calendar composables, the config they share, and the four `*Defaults` objects every content
slot falls back to.

```kotlin
import com.himanshoe.kalendar.KalendarMonth
```

Each composable has a full narrative page under [Views](../views/index.md); this page is the
signature-level reference.

## The composables

| Composable | Shape | Paging unit | State |
|---|---|---|---|
| [`KalendarWeek`](#kalendarweek) | One week row | Week | `KalendarViewState` |
| [`KalendarMonth`](#kalendarmonth) | Month grid | Month | `KalendarViewState` |
| [`KalendarYear`](#kalendaryear) | 12 month grids in a lazy column | Year | `KalendarViewState` |
| [`KalendarTimeline`](#kalendartimeline) | Continuous month list | none — free scroll | `KalendarTimelineState` |
| [`KalendarSchedule`](#kalendarschedule) | Hourly grid, one day | Day | `KalendarViewState` |
| [`KalendarScheduleWeek`](#kalendarscheduleweek) | Hourly grid, 7 day columns | Week | `KalendarViewState` |
| [`KalendarResourceView`](#kalendarresourceview) | Hourly grid, one lane per resource | Day | `KalendarViewState` |
| [`KalendarAgenda`](#kalendaragenda) | Event list grouped by date | none — free scroll | `LazyListState` |
| [`KalendarDatePicker`](#kalendardatepicker) | Compact single-month picker | Month | `KalendarViewState` |

Every one of them is `restartable skippable` — see [Performance](../performance.md).

### `KalendarMonth`

```kotlin
@Composable
public fun KalendarMonth(
    selectedDate: LocalDate,
    modifier: Modifier = Modifier,
    state: KalendarViewState = rememberKalendarMonthState(selectedDate),
    events: KalendarEvents = emptyList(),
    selectedDates: Set<LocalDate> = setOf(selectedDate),
    onDateClick: (date: LocalDate, events: List<KalendarEvent>) -> Unit = { _, _ -> },
    onDayEventClick: ((event: KalendarEvent) -> Unit)? = null,
    onDateRangeSelect: ((start: LocalDate, current: LocalDate) -> Unit)? = null,
    onDateRangeSelectEnd: (() -> Unit)? = null,
    onEventDrop: ((events: List<KalendarEvent>, newDate: LocalDate) -> Unit)? = null,
    config: KalendarViewConfig = KalendarViewConfig(),
    header: @Composable (KalendarHeaderScope) -> Unit = { … },
    dayOfWeekLabel: @Composable (DayOfWeek) -> Unit = { … },
    dayContent: (@Composable (scope: KalendarDayScope) -> Unit)? = null,
)
```

The full month grid, paged by month. The most capable date-grid view: the only one with
drag-to-reschedule.

| Parameter | Type | Default | Description |
|---|---|---|---|
| `selectedDate` | `LocalDate` | — | Default single-selection highlight, and the initially visible month when `state` is left at its default. |
| `modifier` | `Modifier` | `Modifier` | Applied to the outermost container. |
| `state` | `KalendarViewState` | `rememberKalendarMonthState(selectedDate)` | Navigation state. |
| `events` | `KalendarEvents` | `emptyList()` | Rendered as indicator dots on day cells. |
| `selectedDates` | `Set<LocalDate>` | `setOf(selectedDate)` | The highlighted dates. Hoist your own for multi-select or range. |
| `onDateClick` | `(LocalDate, List<KalendarEvent>) -> Unit` | no-op | Called when a non-disabled date is tapped, with that date's events. |
| `onDayEventClick` | `((KalendarEvent) -> Unit)?` | `null` | Called when an individual event is tapped inside the cell's `+N` overflow popup. |
| `onDateRangeSelect` | `((LocalDate, LocalDate) -> Unit)?` | `null` | When non-null, press-and-hold then drag selects a range live. |
| `onDateRangeSelectEnd` | `(() -> Unit)?` | `null` | Called once when such a drag ends. |
| `onEventDrop` | `((List<KalendarEvent>, LocalDate) -> Unit)?` | `null` | When non-null, press-and-hold a date **that has events** and drag to another date to reschedule. |
| `config` | `KalendarViewConfig` | `KalendarViewConfig()` | Shared settings. |
| `header` | `@Composable (KalendarHeaderScope) -> Unit` | `KalendarHeaderDefaults.Header` | Replaces the navigation header. |
| `dayOfWeekLabel` | `@Composable (DayOfWeek) -> Unit` | `KalendarHeaderDefaults.DayOfWeekLabel` | Replaces one weekday column header's content. |
| `dayContent` | `(@Composable (KalendarDayScope) -> Unit)?` | `null` | Replaces the built-in day cell entirely. Last, so trailing-lambda syntax lands on it. |

```kotlin
KalendarMonth(
    selectedDate = today,
    events = events,
    selectedDates = selected,
    onDateClick = { date, _ -> selected = setOf(date) },
)
```

### `KalendarWeek`

```kotlin
@Composable
public fun KalendarWeek(
    selectedDate: LocalDate,
    modifier: Modifier = Modifier,
    state: KalendarViewState = rememberKalendarWeekState(selectedDate),
    events: KalendarEvents = emptyList(),
    selectedDates: Set<LocalDate> = setOf(selectedDate),
    onDateClick: (date: LocalDate, events: List<KalendarEvent>) -> Unit = { _, _ -> },
    onDayEventClick: ((event: KalendarEvent) -> Unit)? = null,
    onDateRangeSelect: ((start: LocalDate, current: LocalDate) -> Unit)? = null,
    onDateRangeSelectEnd: (() -> Unit)? = null,
    config: KalendarViewConfig = KalendarViewConfig(),
    header: @Composable (KalendarHeaderScope) -> Unit = { … },
    dayOfWeekLabel: @Composable (DayOfWeek) -> Unit = { … },
    dayContent: (@Composable (scope: KalendarDayScope) -> Unit)? = null,
)
```

A single-week row, paged by week. Every parameter means what it does on
[`KalendarMonth`](#kalendarmonth); it has no `onEventDrop`, because one row is not a drag surface.

```kotlin
KalendarWeek(selectedDate = today, state = rememberKalendarWeekState(today))
```

### `KalendarYear`

```kotlin
@Composable
public fun KalendarYear(
    selectedDate: LocalDate,
    modifier: Modifier = Modifier,
    state: KalendarViewState = rememberKalendarYearState(selectedDate),
    events: KalendarEvents = emptyList(),
    selectedDates: Set<LocalDate> = setOf(selectedDate),
    onDateClick: (date: LocalDate, events: List<KalendarEvent>) -> Unit = { _, _ -> },
    onDayEventClick: ((event: KalendarEvent) -> Unit)? = null,
    config: KalendarViewConfig = KalendarViewConfig(),
    header: @Composable (KalendarHeaderScope) -> Unit = { … },
    dayOfWeekLabel: @Composable (DayOfWeek) -> Unit = { … },
    dayContent: (@Composable (scope: KalendarDayScope) -> Unit)? = null,
)
```

Twelve month grids in one lazily composed scrolling column, paged by year. No drag gestures and no
sliding selection indicator: twelve grids are on screen at once, so there is no single indicator to
slide and no unambiguous drag surface.

```kotlin
KalendarYear(selectedDate = today, onDateClick = { date, _ -> jumpTo(date) })
```

### `KalendarTimeline`

```kotlin
@Composable
public fun KalendarTimeline(
    selectedDate: LocalDate,
    modifier: Modifier = Modifier,
    state: KalendarTimelineState = rememberKalendarTimelineState(selectedDate),
    events: KalendarEvents = emptyList(),
    selectedDates: Set<LocalDate> = setOf(selectedDate),
    onDateClick: (date: LocalDate, events: List<KalendarEvent>) -> Unit = { _, _ -> },
    onDayEventClick: ((event: KalendarEvent) -> Unit)? = null,
    config: KalendarViewConfig = KalendarViewConfig(),
    header: @Composable (KalendarHeaderScope) -> Unit = { … },
    dayOfWeekLabel: @Composable (DayOfWeek) -> Unit = { … },
    dayContent: (@Composable (scope: KalendarDayScope) -> Unit)? = null,
)
```

Every month in one uninterrupted vertical scroll. Note the different `state` type
([`KalendarTimelineState`](kalendar-state.md)) and the different `header` default
(`KalendarHeaderDefaults.TimelineHeader`) — a sticky overlay, not a page header.
`KalendarViewConfig.showNavigationArrows` and `showJumpPicker` have no effect here.

```kotlin
KalendarTimeline(selectedDate = today, events = events)
```

### `KalendarSchedule`

```kotlin
@Composable
public fun KalendarSchedule(
    modifier: Modifier = Modifier,
    state: KalendarViewState = rememberKalendarScheduleState(),
    events: KalendarEvents = emptyList(),
    onEventClick: (KalendarEvent) -> Unit = {},
    onEventTimeChange: ((event: KalendarEvent, newStart: LocalDateTime, newEnd: LocalDateTime) -> Unit)? = null,
    onEventCreate: ((start: LocalDateTime, end: LocalDateTime) -> Unit)? = null,
    config: KalendarViewConfig = KalendarViewConfig(),
    hourHeight: Dp = KalendarTheme.dimensions.hourHeight,
    nowIndicatorTick: Duration = 1.minutes,
    allDayEventContent: @Composable (KalendarEvent) -> Unit = { … },
    hourLabel: @Composable (Int) -> Unit = { … },
    nowIndicator: @Composable () -> Unit = { … },
    eventContent: @Composable (KalendarScheduleEventScope) -> Unit = { … },
)
```

An hourly time grid for a single day. There is **no** `selectedDate` parameter — the Schedule views
have no date selection, so the visible day is the `state`'s business alone.

| Parameter | Type | Default | Description |
|---|---|---|---|
| `modifier` | `Modifier` | `Modifier` | Applied to the outermost container. |
| `state` | `KalendarViewState` | `rememberKalendarScheduleState()` | Pages by single day. |
| `events` | `KalendarEvents` | `emptyList()` | Only those on the visible day are rendered. |
| `onEventClick` | `(KalendarEvent) -> Unit` | no-op | Called when a block or all-day chip is tapped. |
| `onEventTimeChange` | `((KalendarEvent, LocalDateTime, LocalDateTime) -> Unit)?` | `null` | When non-null, blocks become draggable and resizable from **either** edge. |
| `onEventCreate` | `((LocalDateTime, LocalDateTime) -> Unit)?` | `null` | When non-null, a press-and-drag on empty grid sweeps out a new time range and reports it on release. |
| `config` | `KalendarViewConfig` | `KalendarViewConfig()` | `scheduleVisibleHours`, `scheduleInitialScrollHour` and `scheduleDragSnapMinutes` all apply here. |
| `hourHeight` | `Dp` | `KalendarTheme.dimensions.hourHeight` | Vertical space for one hour; block heights scale with it. |
| `nowIndicatorTick` | `Duration` | `1.minutes` | How often the now-indicator re-reads the clock. |
| `allDayEventContent` | `@Composable (KalendarEvent) -> Unit` | `KalendarScheduleDefaults.AllDayChip` | One chip in the all-day row. |
| `hourLabel` | `@Composable (Int) -> Unit` | `KalendarScheduleDefaults.HourLabel` | One gutter label, given the absolute hour. |
| `nowIndicator` | `@Composable () -> Unit` | `KalendarScheduleDefaults.NowIndicator` | The current-time marker. The grid owns its offset. |
| `eventContent` | `@Composable (KalendarScheduleEventScope) -> Unit` | `KalendarScheduleDefaults.EventBlock` | One timed block. **Last**, so trailing-lambda syntax lands on the primary slot. |

```kotlin
KalendarSchedule(
    state = rememberKalendarScheduleState(initialDate = today),
    events = events,
    onEventClick = { openDetails(it) },
    onEventCreate = { start, end -> newEvent(start, end) },
)
```

### `KalendarScheduleWeek`

```kotlin
@Composable
public fun KalendarScheduleWeek(
    modifier: Modifier = Modifier,
    state: KalendarViewState = rememberKalendarWeekState(),
    events: KalendarEvents = emptyList(),
    onEventClick: (KalendarEvent) -> Unit = {},
    onEventTimeChange: ((event: KalendarEvent, newStart: LocalDateTime, newEnd: LocalDateTime) -> Unit)? = null,
    onEventCreate: ((start: LocalDateTime, end: LocalDateTime) -> Unit)? = null,
    config: KalendarViewConfig = KalendarViewConfig(),
    hourHeight: Dp = KalendarTheme.dimensions.hourHeight,
    nowIndicatorTick: Duration = 1.minutes,
    allDayEventContent: @Composable (KalendarEvent) -> Unit = { … },
    hourLabel: @Composable (Int) -> Unit = { … },
    nowIndicator: @Composable () -> Unit = { … },
    dayHeader: @Composable (date: LocalDate, isToday: Boolean) -> Unit = { … },
    eventContent: @Composable (KalendarScheduleEventScope) -> Unit = { … },
)
```

`KalendarSchedule`'s week variant: seven day columns sharing one hour gutter, paged by week. Note it
takes the **week** state factory, so it can share a hoisted state with `KalendarWeek`.

Two differences beyond the shape: a horizontal drag also moves an event across day columns, and it
adds a `dayHeader` slot for one column's header. `KalendarViewConfig.visibleDaysOfWeek` narrows the
column count.

```kotlin
KalendarScheduleWeek(state = rememberKalendarWeekState(today), events = events)
```

### `KalendarResourceView`

```kotlin
@Composable
public fun KalendarResourceView(
    resources: List<KalendarResource>,
    modifier: Modifier = Modifier,
    state: KalendarViewState = rememberKalendarScheduleState(),
    events: KalendarEvents = emptyList(),
    resourceIdOf: (KalendarEvent) -> String? = { it.calendarId },
    onEventClick: (KalendarEvent) -> Unit = {},
    onEventChange: ((
        event: KalendarEvent,
        newStart: LocalDateTime,
        newEnd: LocalDateTime,
        newResourceId: String,
    ) -> Unit)? = null,
    config: KalendarViewConfig = KalendarViewConfig(),
    hourHeight: Dp = KalendarTheme.dimensions.hourHeight,
    minResourceColumnWidth: Dp = KalendarTheme.dimensions.resourceColumnMinWidth,
    nowIndicatorTick: Duration = 1.minutes,
    resourceHeader: @Composable (resource: KalendarResource) -> Unit = { … },
    hourLabel: @Composable (Int) -> Unit = { … },
    nowIndicator: @Composable () -> Unit = { … },
    emptyState: @Composable () -> Unit = { … },
    eventContent: @Composable (KalendarResourceEventScope) -> Unit = { … },
)
```

One day, one column per resource — a room board, a chair schedule, a vehicle roster.

| Parameter | Type | Default | Description |
|---|---|---|---|
| `resources` | `List<KalendarResource>` | — | The lanes, left to right by `KalendarResource.order`. Empty renders `emptyState`. |
| `modifier` | `Modifier` | `Modifier` | Applied to the outermost container. |
| `state` | `KalendarViewState` | `rememberKalendarScheduleState()` | Pages by single day. It does **not** swipe between pages — a horizontal swipe belongs to the lanes; the header's arrows, today button and jump picker move between days. |
| `events` | `KalendarEvents` | `emptyList()` | Only timed events on the visible day, in a known lane, are drawn. |
| `resourceIdOf` | `(KalendarEvent) -> String?` | `KalendarEvent.calendarId` | Maps an event to its lane's `id`, or `null` for none. An accessor rather than an interface member, because `KalendarEvent` is shipped and cannot grow one. |
| `onEventClick` | `(KalendarEvent) -> Unit` | no-op | Called when a block is tapped. |
| `onEventChange` | 4-arg lambda, nullable | `null` | When non-null, blocks drag in time **and across lanes**, and resize from either edge. Reports the event, its new start/end, and the id of the lane it landed in. |
| `config` | `KalendarViewConfig` | `KalendarViewConfig()` | `disabledDates` and `showAdjacentMonthDates` have no effect. |
| `hourHeight` | `Dp` | `KalendarTheme.dimensions.hourHeight` | Vertical space for one hour. |
| `minResourceColumnWidth` | `Dp` | `KalendarTheme.dimensions.resourceColumnMinWidth` | Narrowest a lane may become before the lanes stop dividing the width evenly and start scrolling horizontally. |
| `nowIndicatorTick` | `Duration` | `1.minutes` | One timer serves every lane. |
| `resourceHeader` | `@Composable (KalendarResource) -> Unit` | `KalendarResourceDefaults.ResourceHeader` | One lane's column header. |
| `hourLabel` | `@Composable (Int) -> Unit` | `KalendarResourceDefaults.HourLabel` | One gutter label. |
| `nowIndicator` | `@Composable () -> Unit` | `KalendarResourceDefaults.NowIndicator` | The current-time marker. |
| `emptyState` | `@Composable () -> Unit` | `KalendarResourceDefaults.EmptyState` | Shown in place of the grid when `resources` is empty. |
| `eventContent` | `@Composable (KalendarResourceEventScope) -> Unit` | `KalendarResourceDefaults.EventBlock` | One block. **Last** — the primary slot. |

```kotlin
KalendarResourceView(
    resources = listOf(
        KalendarResource(id = "room-a", title = "Room A"),
        KalendarResource(id = "room-b", title = "Room B"),
    ),
    events = bookings,
    resourceIdOf = { event -> (event as Booking).roomId },
    onEventChange = { event, start, end, resourceId -> move(event, start, end, resourceId) },
)
```

> **Note:** untimed events are not drawn — a lane grid has no all-day row to pin them to.

### `KalendarAgenda`

```kotlin
@Composable
public fun KalendarAgenda(
    events: KalendarEvents,
    modifier: Modifier = Modifier,
    onEventClick: (KalendarEvent) -> Unit = {},
    config: KalendarViewConfig = KalendarViewConfig(),
    state: LazyListState = rememberLazyListState(),
    dateHeader: @Composable (date: LocalDate) -> Unit = { … },
    emptyState: @Composable () -> Unit = { … },
    eventContent: @Composable (event: KalendarEvent) -> Unit = { … },
)
```

Events grouped by date, with no grid and no pager. Dates with no events are skipped entirely.

| Parameter | Type | Default | Description |
|---|---|---|---|
| `events` | `KalendarEvents` | — | The events to list. |
| `modifier` | `Modifier` | `Modifier` | Applied to the outermost container. |
| `onEventClick` | `(KalendarEvent) -> Unit` | `{}` | Called when a row is tapped. |
| `config` | `KalendarViewConfig` | `KalendarViewConfig()` | Only the month-name formatter and the background apply here. |
| `state` | `LazyListState` | `rememberLazyListState()` | Scroll state. A list of events has nothing to page and nothing to bound, so there is no `KalendarViewState`. |
| `dateHeader` | `@Composable (LocalDate) -> Unit` | `KalendarAgendaDefaults.DateHeader` | One date's header. |
| `emptyState` | `@Composable () -> Unit` | `KalendarAgendaDefaults.EmptyState` | Shown centred when `events` is empty. |
| `eventContent` | `@Composable (KalendarEvent) -> Unit` | `KalendarAgendaDefaults.EventRow` | One event row. **Last** — the primary slot. Takes the event alone, because a list row has no position to describe. |

```kotlin
KalendarAgenda(events = events, onEventClick = { openDetails(it) })
```

Rows are keyed by [`KalendarEvent.id`](foundation-event.md) when supplied, which is what keeps
per-row state attached to the right event across edits.

### `KalendarDatePicker`

```kotlin
@Composable
public fun KalendarDatePicker(
    selectedDates: Set<LocalDate>,
    onDateClick: (date: LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    state: KalendarViewState = rememberKalendarMonthState(),
    selectionMode: KalendarSelectionMode = KalendarSelectionMode.Single,
    config: KalendarViewConfig = KalendarViewConfig(),
    yearRange: IntRange? = null,
    onClear: (() -> Unit)? = null,
    onTodayClick: ((date: LocalDate) -> Unit)? = null,
    header: @Composable (KalendarHeaderScope) -> Unit = { … },
    dayOfWeekLabel: @Composable (DayOfWeek) -> Unit = { … },
    dayContent: @Composable (scope: KalendarDatePickerDayScope) -> Unit = { … },
    actions: @Composable (scope: KalendarDatePickerActionScope) -> Unit = { … },
)
```

A compact single-month picker — the calendar that drops out of a form field, rather than the calendar
an app is built around. It trades events, drag-to-reschedule and full-bleed sizing for a header you
can jump years with and a rail of commit actions.

| Parameter | Type | Default | Description |
|---|---|---|---|
| `selectedDates` | `Set<LocalDate>` | — | The highlighted dates. Hoisted, like every other view's selection. |
| `onDateClick` | `(LocalDate) -> Unit` | — | Called when a selectable date is tapped or committed from the keyboard. |
| `modifier` | `Modifier` | `Modifier` | The picker imposes no size; give it one. |
| `state` | `KalendarViewState` | `rememberKalendarMonthState()` | Navigation state — **and where `minDate`/`maxDate` live**, so one bound both disables out-of-range cells and stops the header's arrows at the same edge. |
| `selectionMode` | `KalendarSelectionMode` | `Single` | How the selection is **drawn**, not how a tap behaves: `Range` fills the span between the earliest and latest selected dates as one band. Match it to your `KalendarSelectionState`'s mode. |
| `config` | `KalendarViewConfig` | `KalendarViewConfig()` | `disabledDates`, `showAdjacentMonthDates`, `showNavigationArrows`, `visibleDaysOfWeek`, the formatters and `background`. Schedule-only and event-only options are ignored. |
| `yearRange` | `IntRange?` | `null` | The years the header's year control offers. `null` derives it from `state`'s bounds where they exist and a century either side of the visible year where they do not. |
| `onClear` | `(() -> Unit)?` | `null` | `null` hides the Clear action entirely, independently of `onTodayClick`. |
| `onTodayClick` | `((LocalDate) -> Unit)?` | `null` | Called with today's date after the picker has scrolled to today's month, and only when today is itself selectable. `null` hides the action. |
| `header` | `@Composable (KalendarHeaderScope) -> Unit` | `KalendarDatePickerDefaults.Header` | Month and year controls, arrows demoted to the trailing corner. |
| `dayOfWeekLabel` | `@Composable (DayOfWeek) -> Unit` | `KalendarHeaderDefaults.DayOfWeekLabel` | Shared with the grid views — a column initial is a column initial. |
| `dayContent` | `@Composable (KalendarDatePickerDayScope) -> Unit` | `KalendarDatePickerDefaults.DayCell` | Non-nullable here, unlike the grid views. |
| `actions` | `@Composable (KalendarDatePickerActionScope) -> Unit` | `KalendarDatePickerDefaults.ActionRow` | Only composed when at least one of `onClear`/`onTodayClick` was supplied. |

```kotlin
val selection = rememberKalendarSelectionState(mode = KalendarSelectionMode.Range)
val state = rememberKalendarMonthState(maxDate = today)

KalendarDatePicker(
    selectedDates = selection.selectedDates,
    onDateClick = selection::onDateClick,
    modifier = Modifier.width(KalendarDatePickerDefaults.Width),
    state = state,
    selectionMode = KalendarSelectionMode.Range,
    onClear = selection::clear,
    onTodayClick = selection::onDateClick,
)
```

Adjacent-month dates are dimmed but, unlike in `KalendarMonth`, remain **selectable**: a picker that
showed the 1st of next month and then refused it would be asking the user to page for no reason.

## `KalendarViewConfig`

```kotlin
@Immutable
public class KalendarViewConfig(
    public val disabledDates: (LocalDate) -> Boolean = { false },
    public val showNavigationArrows: Boolean = true,
    public val showAdjacentMonthDates: Boolean = true,
    public val showTodayButton: Boolean = true,
    public val showJumpPicker: Boolean = true,
    public val showSelectionIndicator: Boolean = true,
    public val monthNameFormatter: (Month) -> String = KalendarFormatters.monthName,
    public val shortMonthNameFormatter: (Month) -> String = KalendarFormatters.shortMonthName,
    public val dayOfWeekNameFormatter: (DayOfWeek) -> String = KalendarFormatters.dayOfWeekName,
    public val dayOfWeekLabelFormatter: (DayOfWeek) -> String = KalendarFormatters.dayOfWeekLabel,
    public val hourLabelFormatter: (Int) -> String = KalendarFormatters.hourLabel,
    public val visibleDaysOfWeek: Set<DayOfWeek> = ALL_DAYS_OF_WEEK,
    public val scheduleVisibleHours: KalendarHourWindow = KalendarHourWindow.FullDay,
    public val scheduleInitialScrollHour: Int = 7,
    public val scheduleDragSnapMinutes: Int = 15,
    public val eventIndicatorCap: Int = 3,
    public val background: Brush? = null,
)
```

Behaviour and formatting shared by every view: what appears, what is interactive, and how dates are
turned into text. Colours, sizes and words live on the [theme](kalendar-theme.md) instead; dates,
bounds and the clock live on the [state factories](kalendar-state.md).

The full option-by-option table, with which views honour each, is on
[Configuration](../configuration.md).

| Member | Returns | Description |
|---|---|---|
| `copy(…)` | `KalendarViewConfig` | A duplicate with only the options passed here replaced. Revalidated. |

```kotlin
val base = KalendarViewConfig(monthNameFormatter = ::localizedMonthName)

KalendarMonth(selectedDate = today, config = base)
KalendarSchedule(config = base.copy(scheduleInitialScrollHour = 9))
```

> **Warning:** four options are validated in the constructor and throw `IllegalArgumentException`
> **on construction**, not at render time: `visibleDaysOfWeek` must be non-empty,
> `scheduleInitialScrollHour` must be in `0..23`, `scheduleDragSnapMinutes` at least `1`, and
> `eventIndicatorCap` at least `1`.

## Scope types

### `KalendarDatePickerDayScope`

```kotlin
@Immutable
public class KalendarDatePickerDayScope(
    public val day: KalendarDayScope,
    public val isInRange: Boolean = false,
    public val isRangeStart: Boolean = false,
    public val isRangeEnd: Boolean = false,
    public val isAdjacentMonth: Boolean = false,
)
```

What `KalendarDatePicker`'s `dayContent` slot is given: the ordinary
[`KalendarDayScope`](kalendar-slot.md) plus where the date sits in a range band.

| Property | Type | Default | Description |
|---|---|---|---|
| `day` | `KalendarDayScope` | — | The date, and whether it is selected, today, or disabled. |
| `isInRange` | `Boolean` | `false` | Whether the date lies inside the selected span. |
| `isRangeStart` | `Boolean` | `false` | Whether it is the span's first date. |
| `isRangeEnd` | `Boolean` | `false` | Whether it is the span's last. Both ends are `true` on the same date for a half-committed range, i.e. one tap in. |
| `isAdjacentMonth` | `Boolean` | `false` | Whether the date belongs to the previous or next month and is only on screen to complete a week. Dimmed, but still selectable. |
| `copy(…)` | `KalendarDatePickerDayScope` | — | A duplicate with only the fields passed here replaced. |

```kotlin
dayContent = { scope ->
    MyPickerCell(
        date = scope.day.date,
        banded = scope.isInRange,
        muted = scope.isAdjacentMonth,
    )
}
```

### `KalendarDatePickerActionScope`

```kotlin
@Stable
public interface KalendarDatePickerActionScope
```

What `KalendarDatePicker`'s `actions` slot is given: which actions the picker was configured to
offer, and what they do. An action is offered only when the picker was handed a callback for it, so
a replacement row can honour that rather than hard-coding two buttons. Calling an action the picker
is not offering is harmless — it does nothing.

| Member | Type | Description |
|---|---|---|
| `today` | `LocalDate` | Today's date in the picker's time zone, re-read when the day rolls over. |
| `showClearAction` | `Boolean` | Whether an `onClear` handler was supplied. |
| `showTodayAction` | `Boolean` | Whether an `onTodayClick` handler was supplied. |
| `isSelectionEmpty` | `Boolean` | Whether nothing is currently selected. The built-in row leaves Clear live either way — a control that greys out mid-interaction is worse than one that does nothing. |
| `clear()` | `Unit` | Empties the selection through the picker's `onClear` handler. |
| `selectToday()` | `Unit` | Scrolls to today's month and reports it through `onTodayClick` — but only when today is actually selectable. |

```kotlin
actions = { scope ->
    Row {
        if (scope.showTodayAction) MyTextButton("Today", onClick = scope::selectToday)
        if (scope.showClearAction) MyTextButton("Clear", onClick = scope::clear)
    }
}
```

## The `*Defaults` objects

Every content slot defaults to one of these. They are public so a custom slot can fall back to the
built-in rendering for the cases it does not handle specially — which is the intended pattern, and
what keeps a partly-customised calendar consistent with the rest of itself.

### `KalendarAgendaDefaults`

| Function | Signature |
|---|---|
| `DateHeader` | `(date: LocalDate, monthNameFormatter: (Month) -> String, modifier: Modifier = Modifier)` |
| `EventRow` | `(event: KalendarEvent, modifier: Modifier = Modifier)` |
| `EmptyState` | `(modifier: Modifier = Modifier)` |

```kotlin
eventContent = { event ->
    if (event.calendarId == "work") MyWorkRow(event) else KalendarAgendaDefaults.EventRow(event = event)
}
```

### `KalendarScheduleDefaults`

| Function | Signature |
|---|---|
| `EventBlock` | `(scope: KalendarScheduleEventScope, modifier: Modifier = Modifier)` |
| `AllDayChip` | `(event: KalendarEvent, modifier: Modifier = Modifier, prefix: String? = null)` |
| `HourLabel` | `(hour: Int, formatter: (Int) -> String, modifier: Modifier = Modifier)` |
| `WeekDayHeader` | `(date: LocalDate, isToday: Boolean, dayOfWeekLabelFormatter: (DayOfWeek) -> String, modifier: Modifier = Modifier)` |
| `NowIndicator` | `(modifier: Modifier = Modifier)` |

`AllDayChip`'s `prefix` is how `KalendarScheduleWeek`'s shared all-day row labels which day each chip
belongs to. A replacement slot does not get it automatically — pass it yourself.

```kotlin
allDayEventContent = { event ->
    KalendarScheduleDefaults.AllDayChip(event = event, prefix = event.date.day.toString())
}
```

### `KalendarResourceDefaults`

| Function | Signature |
|---|---|
| `ResourceHeader` | `(resource: KalendarResource, modifier: Modifier = Modifier)` |
| `EventBlock` | `(scope: KalendarResourceEventScope, modifier: Modifier = Modifier)` |
| `HourLabel` | `(hour: Int, formatter: (Int) -> String, modifier: Modifier = Modifier)` |
| `NowIndicator` | `(modifier: Modifier = Modifier)` |
| `EmptyState` | `(modifier: Modifier = Modifier)` |

```kotlin
resourceHeader = { resource -> KalendarResourceDefaults.ResourceHeader(resource = resource) }
```

### `KalendarDatePickerDefaults`

| Member | Signature |
|---|---|
| `Width` | `Dp` — `312.dp`, the width the picker is drawn for. It imposes no size of its own. |
| `Header` | `(scope: KalendarHeaderScope, modifier: Modifier = Modifier, yearRange: IntRange = …, showNavigationArrows: Boolean = true, showMonthPicker: Boolean = true, showYearPicker: Boolean = true, monthNameFormatter: (Month) -> String = …, shortMonthNameFormatter: (Month) -> String = …, previousIcon: @Composable () -> Unit = …, nextIcon: @Composable () -> Unit = …, dropdownIcon: @Composable () -> Unit = …)` |
| `DayCell` | `(scope: KalendarDatePickerDayScope, onClick: () -> Unit, modifier: Modifier = Modifier, showSelectionBackground: Boolean = true, showTodayMarker: Boolean = true, monthNameFormatter: (Month) -> String = …)` |
| `ActionRow` | `(scope: KalendarDatePickerActionScope, modifier: Modifier = Modifier, clearLabel: String = "Clear", todayLabel: String = KalendarTheme.strings.today)` |

```kotlin
KalendarDatePicker(
    selectedDates = selected,
    onDateClick = ::select,
    modifier = Modifier.width(KalendarDatePickerDefaults.Width),
    header = { scope -> KalendarDatePickerDefaults.Header(scope = scope, showYearPicker = false) },
)
```
