# The headless engine

`kalendar-foundation` is a calendar **engine** with no calendar in it. It is the date arithmetic, the
paging arithmetic, the overlap packing and the selection reduction that every calendar needs, with
no opinion whatsoever about what any of it looks like.

This page is for someone building a calendar Kalendar did not design: a booking grid, a shift
planner, a resource board, a date-range picker that has to look like your design system and nothing
else. You get the parts that are tedious and easy to get subtly wrong, and you write every pixel
yourself.

> **Every engine call on this page is compiled.** The worked examples — and every value they claim
> in a comment — live in
> `kalendar-foundation/src/commonTest/kotlin/com/himanshoe/kalendar/foundation/EngineDocExampleTest.kt`,
> which asserts on them. A signature change breaks that build rather than quietly rotting this page.
> The renderer sketches around them (`Row`, `LazyRow`, `drawBlock`) are illustrative: they are your
> code, not the engine's, and the engine has no opinion about them.

The engine is published on its own:

```kotlin
dependencies {
    implementation("com.himanshoe:kalendar-foundation:2.0.0")
}
```

Nothing here depends on `com.himanshoe:kalendar`. If you also use the built-in views, you already
have it — `kalendar` re-exports it as an `api` dependency.

## What is in it

| Package | What it does |
|---|---|
| [`…foundation.event`](reference/foundation-event.md) | The event interface you implement on your own type, and the by-date index a grid wants. |
| [`…foundation.datetime`](reference/foundation-datetime.md) | Period starts, whole-period differences, inclusive date ranges, and the clock seam. |
| [`…foundation.grid`](reference/foundation-grid.md) | Which dates a week or a month grid draws, and which weekdays get a column. |
| [`…foundation.paging`](reference/foundation-paging.md) | The two-way map between a scroll container's page index and the date that page shows. |
| [`…foundation.schedule`](reference/foundation-schedule.md) | Hour windows, overlap packing, block placement, drag and resize maths, resource lanes. |
| [`…foundation.selection`](reference/foundation-selection.md) | Selection as a pure reduction over taps and drags. |
| [`…foundation.format`](reference/foundation-format.md) | The plain-English fallback labels, and the hooks to replace them. |

Every symbol in each of them is listed in the [API reference](reference/index.md).

## A month grid from scratch

Two calls. `daysOfWeekStartingAt` gives you the column headers, `monthGridDates` gives you the cells,
and they agree with each other by construction — both rotate to `startDayOfWeek` and then filter to
the visible weekdays, in that order, which is what keeps each column under its own label.

```kotlin
val startDayOfWeek = DayOfWeek.MONDAY
val monthStart = LocalDate(2026, 8, 1).startOfMonth()

val columnLabels = daysOfWeekStartingAt(startDayOfWeek = startDayOfWeek)
val dates = monthGridDates(monthStart = monthStart, startDayOfWeek = startDayOfWeek)
```

`columnLabels` is `[MONDAY, TUESDAY, …, SUNDAY]` — seven `DayOfWeek`s, ready to be handed to
whatever draws your header row. `dates` is the flat run of cells, in reading order.

### The leading row is padded. The trailing row is not

This is the first thing a renderer gets wrong, so it is worth being blunt about.

`monthGridDates` pads the **start** of the run with the tail of the previous month, so the 1st lands
under its true weekday. August 2026 opens on a Saturday, so with a Monday-first week the run begins
on Monday 27 July.

It does **not** pad the end. The run stops on the month's last day, full stop:

```kotlin
dates.first()           // 2026-07-27, a Monday — the previous month's tail
dates.last()            // 2026-08-31, the month's last day
dates.size              // 36 — not a multiple of 7
```

So `dates.size` is *not* a multiple of `columnLabels.size`, and a layout that assumes whole rows will
either crash on the last chunk or draw a ragged final row. `chunked` hands you the short row
honestly:

```kotlin
val rows = dates.chunked(columnLabels.size)
rows.forEach { row ->
    Row {
        row.forEach { date -> DayCell(date = date, modifier = Modifier.weight(1f)) }
        // The last row is short. Fill the gap so the cells that do exist keep their column width.
        repeat(columnLabels.size - row.size) { Spacer(modifier = Modifier.weight(1f)) }
    }
}
```

The asymmetry is deliberate rather than an oversight: leading padding is *required* for correctness —
without it every column sits under the wrong label — while trailing padding is a purely visual
choice, and one a booking grid may well not want. Making you write the `Spacer` is how the engine
stays out of that decision.

If you want the month's own days without any padding at all — for a keyboard `Home`/`End`, or for
"how many days does this month have on screen" — that is [`monthDates`](reference/foundation-grid.md),
which never leads in with a neighbour.

### Fewer than seven columns

Every function in the grid package takes a `visibleDaysOfWeek: Set<DayOfWeek>`, defaulting to
[`ALL_DAYS_OF_WEEK`](reference/foundation-grid.md). Narrow it and the header, the cells and the
keyboard step all narrow together:

```kotlin
val workWeek = ALL_DAYS_OF_WEEK - setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)

val labels = daysOfWeekStartingAt(startDayOfWeek = DayOfWeek.MONDAY, visibleDaysOfWeek = workWeek)
val cells = monthGridDates(
    monthStart = monthStart,
    startDayOfWeek = DayOfWeek.MONDAY,
    visibleDaysOfWeek = workWeek,
)
// labels.size == 5, and cells still chunks into rows of 5.
```

The leading padding is measured from the month's first *visible* day, not from its 1st — otherwise a
month opening on a Saturday would emit a whole leading row belonging entirely to the previous month.

### A title for the page

```kotlin
monthTitle(monthStart, KalendarFormatters.monthName) // "August 2026"
```

[`KalendarFormatters`](reference/foundation-format.md) is plain English, because `kotlinx-datetime`
carries no locale data. Pass your own `(Month) -> String` backed by `java.time`, `NSDateFormatter` or
`Intl` and the title is localized.

## Your own pager

[`KalendarPager`](reference/foundation-paging.md) is the two-way map between a page index and a
date, plus the bounds that map has to respect. It contains no Compose container: it works just as
well behind a `HorizontalPager`, a `LazyRow`, a `ViewPager` or a gesture surface you wrote yourself.

```kotlin
val pager = KalendarPager.endless(
    unit = KalendarPageUnit.Month,
    initialDate = LocalDate(2026, 8, 14),
)

val page = pager.initialPage
pager.dateAt(page).startOfMonth()      // 2026-08-01
pager.dateAt(page + 1).startOfMonth()  // 2026-09-01
pager.pageOf(LocalDate(2026, 8, 31))   // == page; any day maps to its month's page
```

Three operations carry the whole thing:

| Call | Answers |
|---|---|
| `dateAt(page)` | "What date does this page show?" — the date keying that day/week/month/year. |
| `pageOf(date)` | "Which page should I scroll to for this date?" — clamped into the bounds. |
| `coercePage(page)` | "Is this index I computed myself legal?" — for a `currentPage - 1` button. |

`pageCount` and `initialPage` are what you hand the container up front.

### Driving a scroll container

```kotlin
val pager = remember { KalendarPager.endless(unit = KalendarPageUnit.Month, initialDate = today) }
val listState = rememberLazyListState(initialFirstVisibleItemIndex = pager.initialPage)

LazyRow(state = listState) {
    items(count = pager.pageCount) { page ->
        MonthPage(monthStart = pager.dateAt(page), modifier = Modifier.fillParentMaxWidth())
    }
}
```

A "next" button is `coercePage`, and a "jump to today" is `pageOf`:

```kotlin
scope.launch { listState.animateScrollToItem(pager.coercePage(listState.firstVisibleItemIndex + 1)) }
scope.launch { listState.animateScrollToItem(pager.pageOf(today)) }
```

Both clamp rather than throw, which is what makes them safe to call with a date the user typed, a
"today" outside a bounded calendar, or a scroll position restored from before the bounds changed.

**This is the same arithmetic the built-in views use.** `KalendarMonth`'s `HorizontalPager`,
`KalendarWeek`'s, `KalendarSchedule`'s and `KalendarTimeline`'s `LazyColumn` are all driven by a
`KalendarPager` built exactly like the one above. A custom strip built on the engine and a built-in
`KalendarMonth` handed the same `initialDate` and `startDayOfWeek` agree on which page is which
month — so the two can sit on one screen, or share one hoisted scroll position, without a
translation layer between them.

### `endless` or `bounded`

They differ in one thing: where the calendar's origin lands in the index space.

```kotlin
val unbounded = KalendarPager.endless(
    unit = KalendarPageUnit.Month,
    initialDate = LocalDate(2026, 8, 14),
)
unbounded.pageCount   // Int.MAX_VALUE
unbounded.initialPage // KalendarPager.CENTER_PAGE
```

`endless` is what a swipeable pager wants: a container that must be told a page count up front, and
that should let the user swipe *backwards* from where they started. The axis is always
`Int.MAX_VALUE` pages and the initial date sits in the middle, leaving about a billion pages of
travel in each direction.

`bounded` is what a lazy list wants: an item count it can size a scrollbar from, and a page `0` that
is a real page rather than an arbitrary point a billion pages into nothing.

```kotlin
val pager = KalendarPager.bounded(
    unit = KalendarPageUnit.Month,
    initialDate = LocalDate(2026, 8, 14),
    minDate = LocalDate(2026, 8, 1),
    maxDate = LocalDate(2026, 10, 31),
)

pager.coercePage(pager.lastPage + 5)   // == pager.lastPage
pager.coercePage(pager.firstPage - 5)  // == pager.firstPage
```

With both bounds set, `pageCount` is the exact number of pages between them — three, here. With one
bound the axis is still `Int.MAX_VALUE` long but anchored at that bound, so the bounded side is a
real edge.

`minDate` and `maxDate` work on `endless` too; they narrow `firstPage`/`lastPage` without shortening
the axis, so `pageOf` and `coercePage` keep every *programmatic* jump inside them. Neither form
blocks a swipe — a scroll container owns its own gesture, and clamping it is your call.

### Skipping the page bookkeeping

If you only want the step and not the index space, [`KalendarPageUnit`](reference/foundation-paging.md)
is usable on its own — it is the three operations `KalendarPager` is built from:

```kotlin
val unit = KalendarPageUnit.Month
unit.startOf(LocalDate(2026, 8, 15))                          // 2026-08-01
unit.plusPages(LocalDate(2026, 8, 1), 1)                      // 2026-09-01
unit.pagesBetween(from = monthStart, to = LocalDate(2027, 2, 3)) // 6
```

## A day view

Three calls turn a list of events into a laid-out hour grid: `scheduleBlocks` packs the overlaps,
`KalendarHourWindow` says which hours are on screen, and `blockPlacement` turns a block into a
rectangle in minutes.

```kotlin
val date = LocalDate(2026, 8, 14)
val events = listOf(
    BasicKalendarEvent(
        date = date,
        eventName = "Standup",
        startTime = LocalDateTime(2026, 8, 14, 9, 0),
        endTime = LocalDateTime(2026, 8, 14, 9, 30),
    ),
    BasicKalendarEvent(
        date = date,
        eventName = "Review",
        startTime = LocalDateTime(2026, 8, 14, 9, 15),
        endTime = LocalDateTime(2026, 8, 14, 10, 0),
    ),
)

val window = KalendarHourWindow(startHour = 8, endHour = 18)
val blocks = scheduleBlocks(events = events, date = date, window = window)
```

### `column` and `columns` — how you get side-by-side events

This is the part that is genuinely hard to write and the reason to use the engine at all.

`scheduleBlocks` finds each **overlap cluster** — a run of events that transitively overlap — and
packs its members into the fewest side-by-side slots that keep them from colliding. Every block
carries two numbers:

| Property | Meaning |
|---|---|
| `column` | This block's zero-based slot within its cluster. |
| `columns` | How many slots the whole cluster needs. |

Crucially, **every member of a cluster reports the same `columns`**. That is what makes the widths
line up: each block divides the day's width by the same number, so two blocks in a two-way overlap
are each half-width and flush against each other, rather than each guessing.

For the two events above — 09:00–09:30 and 09:15–10:00, which overlap by fifteen minutes:

```kotlin
blocks.size                          // 2
blocks.all { it.columns == 2 }       // true — they share the width
blocks.map { it.column }.toSet()     // {0, 1}
```

Which becomes, in your renderer:

```kotlin
val columnWidth = dayWidth / block.columns
val left = block.column * columnWidth
```

A block with nothing overlapping it reports `column = 0, columns = 1` and takes the full width. No
special case needed.

### From block to rectangle

`blockPlacement` converts a block into a top and a height **in minutes, measured from the top of the
window** — not from midnight, which is the distinction that makes a business-hours grid work:

```kotlin
val placement = blockPlacement(block = blocks.first(), window = window)
placement.topMinutes     // 60 — 09:00 is 60 minutes into an 08:00 window
placement.heightMinutes  // 30
```

You choose the pixel scale, so the engine never needs to know your density:

```kotlin
val minutePx = hourHeightPx / MINUTES_PER_HOUR

blocks.forEach { block ->
    val placement = blockPlacement(block = block, window = window)
    if (placement.heightMinutes > 0) {
        val columnWidth = dayWidth / block.columns
        drawBlock(
            x = block.column * columnWidth,
            y = placement.topMinutes * minutePx,
            width = columnWidth,
            height = placement.heightMinutes * minutePx,
        )
    }
}
```

The grid's own scrollable extent is `window.spanMinutes * minutePx`, and its rules go at
`(hour - window.startHour) * hourHeightPx` for each `hour in window.hours`.

### Business hours

`KalendarHourWindow(startHour = 8, endHour = 18)` is a grid that draws ten hour rows and nothing
else. The end hour is exclusive, so this one's last labelled row is 17:00 and its bottom edge is
18:00.

The window **clips, it does not filter**. A 07:00–09:00 meeting on an 08:00 grid is drawn from the
top edge down to 09:00 with its head cut off — the alternative, dropping it, would let a
business-hours calendar quietly stop showing the meeting that runs into breakfast. Only an event
lying *entirely* outside the window is dropped, because it has no pixels to occupy.

Blocks keep their true minute range regardless; clipping happens in `blockPlacement`, at draw time.
That is what lets a drag report the event's own time rather than the clipped one.

| Member | For |
|---|---|
| `hourCount` | How many rows to draw. |
| `hours` | The `IntRange` of hours to label, top to bottom. |
| `startMinutes` / `endMinutes` | The window's edges as minutes from midnight. |
| `spanMinutes` | The grid's total height in minutes. |
| `contains(minutes)` | Whether to draw a now-indicator at all — `now.minutesFromMidnight() in window`. |
| `clamp(minutes)` | Pull an instant inside the window. |

Converting the wall clock into the grid's units is [`minutesFromMidnight`](reference/foundation-schedule.md):

```kotlin
val nowPx = (now.minutesFromMidnight() - window.startMinutes) * minutePx
```

And the initial scroll position, given a preferred absolute hour, is
[`initialScrollHourOffset`](reference/foundation-schedule.md) — which clamps rather than scrolling
backwards past the top of a window that already starts later than you asked for:

```kotlin
val scrollPx = initialScrollHourOffset(initialScrollHour = 7, window = window) * hourHeightPx
```

### Drag, resize, and sweep-to-create

The gesture maths is here too, all of it pure and all of it clamped to the window rather than to the
day — so a drag cannot push a block off the top of a business-hours grid into space the user has no
way to scroll back to. See [the schedule reference](reference/foundation-schedule.md) for the full
list; the shape of it is:

```kotlin
val mode = scheduleDragMode(pressYPx = y, blockHeightPx = height, resizeHandlePx = handlePx)
val delta = snappedDragMinutes(rawPx = dragY, minutePx = minutePx, snapMinutes = 15)
val preview = adjustedScheduleBlockForDrag(block = block, deltaMinutes = delta, mode = mode)
val (start, end) = dragReleaseTimes(block = preview, targetDate = date)
```

`dragReleaseTimes` is worth one note: a block ending at exactly midnight reports `00:00` on the
**following** date, because `LocalTime` cannot express `24:00` and silently truncating to 23:59 would
lose a minute of every end-of-day event.

## Selection

[`KalendarSelection`](reference/foundation-selection.md) is a value, and every operation on it
returns a new one. There is no state holder, no observer, no lifecycle — a tap is a pure function
from the old selection to the new one.

```kotlin
val mode = KalendarSelectionMode.Multiple
var selection = KalendarSelection.Empty

selection = selection.afterClick(mode, LocalDate(2026, 8, 14))
selection = selection.afterClick(mode, LocalDate(2026, 8, 15))
selection.dates.size // 2

selection = selection.afterClick(mode, LocalDate(2026, 8, 14))
selection.dates      // {2026-08-15} — clicking again deselects
```

What `afterClick` does depends entirely on the mode you hand it:

| Mode | A tap |
|---|---|
| `KalendarSelectionMode.Single` | Replaces the selection with just that date. |
| `KalendarSelectionMode.Multiple` | Adds the date, or removes it if it was already selected. |
| `KalendarSelectionMode.Range` | Starts a range; the next tap completes it, selecting every date between the two inclusive, in either tap order. The tap after that starts a fresh range. |

`Single` replaces rather than accumulates:

```kotlin
val single = KalendarSelection.Empty
    .afterClick(KalendarSelectionMode.Single, LocalDate(2026, 8, 14))
    .afterClick(KalendarSelectionMode.Single, LocalDate(2026, 8, 20))
single.dates // {2026-08-20}
```

The mode is a parameter of the *call*, not a property of the selection, so one selection value can
be driven by two different gestures — a tap in `Single` mode and a long-press sweep in range mode —
without being rebuilt.

### Press-and-drag ranges

A live range highlight is two calls: `afterRangeDrag` on every pointer move, `afterRangeDragEnd`
once on release.

```kotlin
Modifier.pointerInput(Unit) {
    detectDragGesturesAfterLongPress(
        onDragStart = { offset -> anchor = dateAt(offset) },
        onDrag = { change, _ -> selection = selection.afterRangeDrag(anchor, dateAt(change.position)) },
        onDragEnd = { selection = selection.afterRangeDragEnd() },
    )
}
```

`afterRangeDrag` overwrites `dates` outright with the inclusive span between the two dates, whatever
mode you are otherwise in — a sweep means a range by definition. `afterRangeDragEnd` clears
`pendingRangeStart` so the next *tap* starts a fresh range rather than trying to complete the
just-finished sweep.

### Why this is yours and not ours

A selection is the one piece of calendar state an app almost always already owns. It goes in a form,
a view model, a `SavedStateHandle`, a URL query, a database row. Hiding it behind a state holder
would mean every one of those has to reach through an object we designed to get at a `Set<LocalDate>`
it could have held directly.

So the engine ships the *reduction* and not the storage. `KalendarSelection` drops into a
`MutableState`, a `StateFlow`, a `rememberSaveable`, or a plain field on your own model:

```kotlin
var selection by remember { mutableStateOf(KalendarSelection.Empty) }

DayCell(
    isSelected = date in selection.dates,
    onClick = { selection = selection.afterClick(KalendarSelectionMode.Multiple, date) },
)
```

`pendingRangeStart` is public for exactly this reason: persist it alongside `dates` and a
half-finished range survives process death, which it cannot if the half-state is hidden.

The built-in views' `rememberKalendarSelectionState` is a thin `rememberSaveable` wrapper over these
same calls. It is a convenience, not a privileged path.

## Events

`KalendarEvent` is an **interface**, not a class you must convert to. Implement it on the type your
app already has and hand the engine your own list:

```kotlin
data class Booking(
    val bookingId: Long,
    val room: String,
    val guest: String,
    val from: LocalDateTime,
    val to: LocalDateTime,
) : KalendarEvent {
    override val id: String get() = bookingId.toString()
    override val date: LocalDate get() = from.date
    override val eventName: String get() = guest
    override val eventDescription: String? get() = room
    override val startTime: LocalDateTime get() = from
    override val endTime: LocalDateTime get() = to
}
```

Only `date`, `eventName` and `eventDescription` are abstract. Everything else — `id`, `endDate`,
`startTime`, `endTime`, `eventColor`, `calendarId` — has a default of `null`, so an implementation
stays source-compatible when the model grows.

`List<Booking>` is already a `KalendarEvents`, which is a `typealias` for `List<KalendarEvent>`. Pass
it straight to `scheduleBlocks`.

[`BasicKalendarEvent`](reference/foundation-event.md) is there for when you have no domain type worth
implementing on — a demo, a test fixture, a screen that reads events from a service and never models
them.

### Multi-day events, indexed by date

A grid wants a map, not a list: for each of 35-odd cells, "what falls on this date?" should be a
lookup rather than a scan of every event you have.

```kotlin
val byDate = events.byDateExpandingSpans()

monthGridDates(monthStart = monthStart, startDayOfWeek = DayOfWeek.MONDAY).forEach { date ->
    DayCell(date = date, events = byDate[date].orEmpty())
}
```

The *expanding* part is the point. An event with `endDate` set appears under **every** date from
`date` through `endDate` inclusive, so a three-day conference is present on all three days rather
than only on the first — which is the bug a hand-written `groupBy { it.date }` ships with.

Build it once per event list, outside your per-cell loop:

```kotlin
val byDate = remember(events) { events.byDateExpandingSpans() }
```

`remember` compares its key with `equals` regardless of Compose stability, so a value-equal list does
not rebuild the map.

Two edges are handled rather than trusted: a span longer than 366 days is truncated, so a malformed
`endDate` far in the future cannot turn one event into an unbounded map; and an `endDate` *before*
`date` is treated as a single-day event rather than producing an empty or reversed range.

## What foundation is not

It is a **Compose** engine, not a general-purpose Kotlin calendar library. That is a deliberate
scope, and it is visible in the dependency list:

```
compose.runtime   compose.ui   kotlinx-datetime
```

Nothing else. Never `compose.foundation`, never `material3` — so the engine drags in no widgets, no
theme, and no layout system, and it cannot quietly decide what your calendar looks like.

But `compose.runtime` and `compose.ui` *are* there, and that is not an accident to be tidied away
later.

**`compose.runtime` is there for stability inference.** The Compose compiler reads `@Stable` and
`@Immutable` off the classpath to decide whether your composables can skip recomposition. Those
annotations live in `compose.runtime`. If the engine hid the dependency, `KalendarEvent` would carry
no `@Stable`, every `KalendarEvent` parameter in every consumer's composable would be inferred
unstable, and calendars built on the engine would stop skipping — a real, measurable frame cost,
traded for a dependency line nobody reads.

**`compose.ui` is there for `Color`.** `KalendarEvent.eventColor` and `KalendarResource.color` are
`androidx.compose.ui.graphics.Color`. A `Long` or a hand-rolled colour type would have to be
converted at every call site of every consumer, and the conversion is the sort of thing that gets
written twice with two different alpha conventions.

The practical consequence: **you can use this engine from any Compose Multiplatform target, and only
from Compose.** A Ktor backend computing booking slots, an Android View-system calendar, a CLI that
prints a month — none of those are the audience. For those, the arithmetic here is small enough to
be worth writing against `kotlinx-datetime` directly.

Two more honest limits:

- **No recurrence.** The engine expands multi-day *spans*, not RRULEs. Recurrence expansion lives in
  the separate [`kalendar-sync`](sync.md) module, which reads and writes device calendars.
- **No time zones beyond the seam.** [`KalendarTimeSource`](reference/foundation-datetime.md) resolves
  "today" in a zone you choose, and every other function here works in `LocalDate`/`LocalDateTime`.
  An event whose participants are in different zones has to be converted to a single wall-clock zone
  before it reaches the engine.

## Where to go next

- [**API reference**](reference/index.md) — every public symbol in both modules, by package.
- [**Views**](views/index.md) — what the batteries-included calendar built on this engine looks like.
- [**Performance**](performance.md) — the measured cost of the built-in views, and how the engine's
  `remember` keys keep it there.
