# `kalendar.state`

`com.himanshoe.kalendar.state`

Navigation state, selection state, and the clock. Each state class wraps a Compose scroll state and
exposes it in **date** terms; each is created by a `remember*` factory rather than a constructor, so
it survives configuration changes and is keyed on the things that should rebuild it.

```kotlin
import com.himanshoe.kalendar.state.rememberKalendarMonthState
```

## Navigation state

### `KalendarViewState`

```kotlin
@Stable
public class KalendarViewState
```

The paged views' state — a `PagerState` and a [`KalendarPager`](foundation-paging.md), presented as
dates. Returned by `rememberKalendarWeekState`, `rememberKalendarMonthState`,
`rememberKalendarYearState` and `rememberKalendarScheduleState`.

| Member | Type | Description |
|---|---|---|
| `visibleDate` | `LocalDate` | Start date of the page currently on screen. Backed by `derivedStateOf`, so reading it only invalidates on a page change. |
| `startDayOfWeek` | `DayOfWeek` | The week's first column, as the state was built with. |
| `minDate` | `LocalDate?` | The lower bound this state was created with. |
| `maxDate` | `LocalDate?` | The upper bound. |
| `canScrollBackward` | `Boolean` | Whether stepping one page back stays within `minDate`. |
| `canScrollForward` | `Boolean` | Whether stepping one page forward stays within `maxDate`. |
| `timeSource` | `KalendarTimeSource` | Where this state reads "today" from. **Mutable**, so a hoisted state can follow a changing clock. |
| `suspend animateScrollTo(date)` | `Unit` | Animates to the page containing `date`, clamped to the bounds. |
| `suspend animateScrollToToday()` | `Unit` | Animates to the page containing today, clamped. |
| `suspend animateToPreviousPage()` | `Unit` | One page back. A no-op past `minDate`. |
| `suspend animateToNextPage()` | `Unit` | One page forward. A no-op past `maxDate`. |

```kotlin
val state = rememberKalendarMonthState(initialDate = today)
val scope = rememberCoroutineScope()

Text("${state.visibleDate.month} ${state.visibleDate.year}")
KalendarMonth(selectedDate = today, state = state)
Button(onClick = { scope.launch { state.animateScrollTo(LocalDate(2026, 12, 25)) } }) {
    Text("Jump to December")
}
```

> **Note:** there is deliberately **no** `today` property here. A clock-derived value stored on a
> `@Stable` object notifies nobody when the clock moves on, so it would go stale at midnight and no
> recomposition would ever be scheduled to fix it. Read it in composition instead:
> `rememberKalendarToday(state.timeSource)`.

### `KalendarTimelineState`

```kotlin
@Stable
public class KalendarTimelineState
```

[`KalendarTimeline`](kalendar-views.md)'s state — a `LazyListState` and a `KalendarPager`. A timeline
scrolls continuously, so it has no pages and therefore no `canScrollBackward`/`Forward` and no
per-page animation.

| Member | Type | Description |
|---|---|---|
| `visibleDate` | `LocalDate` | Start of the month currently topmost in the list. |
| `startDayOfWeek` | `DayOfWeek` | The week's first column. |
| `minDate` | `LocalDate?` | The lower bound. |
| `maxDate` | `LocalDate?` | The upper bound. |
| `timeSource` | `KalendarTimeSource` | Where this state reads "today" from. Mutable. |
| `suspend animateScrollTo(date)` | `Unit` | Animates so the month containing `date` reaches the top. |
| `suspend animateScrollToToday()` | `Unit` | Animates back to today's month. |

```kotlin
val state = rememberKalendarTimelineState(initialDate = today)
KalendarTimeline(selectedDate = today, state = state)
```

> **Warning:** `minDate`/`maxDate` bound the **buttons**, not the swipe. On the paged views they
> disable the header's arrows and clamp `animateScrollTo`, but a swipe can carry you past them. On
> `KalendarTimeline` the bounds *do* clamp the scroll itself, because the list is built with a finite
> item count.

### The factories

All five take the same parameters, except `rememberKalendarScheduleState`, which has no
`startDayOfWeek` — a single day has no week columns to align.

| Parameter | Type | Default | Description |
|---|---|---|---|
| `initialDate` | `LocalDate` | today, via `timeSource` | The week / month / year / day initially visible. |
| `startDayOfWeek` | `DayOfWeek` | `DayOfWeek.MONDAY` | The first week column. Not on `rememberKalendarScheduleState`. |
| `minDate` | `LocalDate?` | `null` | Lower bound. Unbounded when `null`. |
| `maxDate` | `LocalDate?` | `null` | Upper bound. Unbounded when `null`. |
| `timeSource` | `KalendarTimeSource` | `rememberKalendarTimeSource()` | The seam between the calendar and the wall clock. |

| Factory | Returns | Pages by |
|---|---|---|
| `rememberKalendarWeekState(…)` | `KalendarViewState` | Week. Shared by `KalendarWeek` and `KalendarScheduleWeek`. |
| `rememberKalendarMonthState(…)` | `KalendarViewState` | Month. Also used by `KalendarDatePicker`. |
| `rememberKalendarYearState(…)` | `KalendarViewState` | Year. |
| `rememberKalendarScheduleState(…)` | `KalendarViewState` | Day. Used by `KalendarSchedule` and `KalendarResourceView`. |
| `rememberKalendarTimelineState(…)` | `KalendarTimelineState` | Nothing — free scroll. |

```kotlin
val state = rememberKalendarMonthState(
    initialDate = today,
    startDayOfWeek = DayOfWeek.SUNDAY,
    minDate = LocalDate(2020, 1, 1),
    maxDate = LocalDate(2030, 12, 31),
)
```

`initialDate` defaults to today *as read through `timeSource`*, so wrapping a screen in
`ProvideKalendarTimeSource(fixed) { … }` pins every view's idea of the current date at once.

## Selection state

### `KalendarSelectionState`

```kotlin
@Stable
public class KalendarSelectionState
```

A `rememberSaveable`-backed holder over [`KalendarSelection`](foundation-selection.md). It implements
the tap behaviour of one `KalendarSelectionMode` and survives configuration changes and process
death — including a half-finished range.

| Member | Type | Description |
|---|---|---|
| `selectedDates` | `Set<LocalDate>` | The current selection. Pass it to a view's `selectedDates`. |
| `onDateClick(date)` | `Unit` | Reduces a tap under the mode this state was created with. |
| `onRangeDrag(start, current)` | `Unit` | Updates a live press-and-drag range. Call on every pointer move. |
| `onRangeDragEnd()` | `Unit` | Ends such a drag, so the next tap starts a fresh range. |
| `clear()` | `Unit` | Empties the selection, including any half-finished range. |

### `rememberKalendarSelectionState`

```kotlin
@Composable
public fun rememberKalendarSelectionState(
    mode: KalendarSelectionMode = KalendarSelectionMode.Single,
    initialSelection: Set<LocalDate> = emptySet(),
): KalendarSelectionState
```

| Parameter | Type | Default | Description |
|---|---|---|---|
| `mode` | `KalendarSelectionMode` | `Single` | What a tap means. **Changing it resets the state**, since it is the `rememberSaveable` key. |
| `initialSelection` | `Set<LocalDate>` | `emptySet()` | What is selected before the first tap. |

```kotlin
val selection = rememberKalendarSelectionState(mode = KalendarSelectionMode.Range)

KalendarMonth(
    selectedDate = today,
    selectedDates = selection.selectedDates,
    onDateClick = { date, _ -> selection.onDateClick(date) },
    onDateRangeSelect = selection::onRangeDrag,
    onDateRangeSelectEnd = selection::onRangeDragEnd,
)
```

For anything the three modes do not cover — a minimum range length, disallowed end dates — hold the
`Set<LocalDate>` yourself, or reduce a [`KalendarSelection`](foundation-selection.md) directly.

## The clock

Every view, state factory and now-indicator reads "today" through a
[`KalendarTimeSource`](foundation-datetime.md) rather than calling `Clock.System`.

### `ProvideKalendarTimeSource`

```kotlin
@Composable
public fun ProvideKalendarTimeSource(
    timeSource: KalendarTimeSource,
    content: @Composable () -> Unit,
)
```

Provides a time source to every view inside. **The single injection point** — a view's `state`, its
`initialDate` default, and its now-indicator all end up reading the same clock.

| Parameter | Type | Default | Description |
|---|---|---|---|
| `timeSource` | `KalendarTimeSource` | — | The source to provide. `remember` it. |
| `content` | `@Composable () -> Unit` | — | The subtree that reads it. |

```kotlin
val fixed = remember {
    KalendarTimeSource(
        timeZone = TimeZone.UTC,
        clock = object : Clock {
            override fun now(): Instant = Instant.parse("2026-03-10T10:20:00Z")
        },
    )
}

ProvideKalendarTimeSource(fixed) {
    KalendarMonth(selectedDate = LocalDate(2026, 3, 12))
}
```

Every picture on this documentation site is generated this way.

### `rememberKalendarTimeSource`

```kotlin
@Composable
public fun rememberKalendarTimeSource(): KalendarTimeSource
```

The ambient time source, or a composition-scoped default when nothing provided one.

```kotlin
val timeSource = rememberKalendarTimeSource()
```

### `rememberKalendarToday`

```kotlin
@Composable
public fun rememberKalendarToday(
    timeSource: KalendarTimeSource = rememberKalendarTimeSource(),
): LocalDate
```

Today's date, **re-read when the day changes** — so a calendar left open overnight highlights the new
day instead of yesterday's.

| Parameter | Type | Default | Description |
|---|---|---|---|
| `timeSource` | `KalendarTimeSource` | the ambient one | Where to read the date from. |

```kotlin
val today = rememberKalendarToday(state.timeSource)
```

### `rememberKalendarNow`

```kotlin
@Composable
public fun rememberKalendarNow(
    timeSource: KalendarTimeSource = rememberKalendarTimeSource(),
    tick: Duration = 1.minutes,
): LocalDateTime
```

The current date *and time*, re-read every `tick`. This is what a now-indicator is positioned from.

| Parameter | Type | Default | Description |
|---|---|---|---|
| `timeSource` | `KalendarTimeSource` | the ambient one | Where to read from. |
| `tick` | `Duration` | `1.minutes` | How often to re-read. |

```kotlin
val now = rememberKalendarNow(tick = 30.seconds)
```

> **Warning:** this recomposes its caller every `tick`. The built-in grids instead hold the reading
> as a `State` and read it inside `Modifier.offset { }`, so a minute passing re-**places** one line
> rather than recomposing the indicator, its slot content, and everything above the read. Prefer that
> shape if you are building your own hour grid.
