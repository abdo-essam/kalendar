# Customization

[Theming](theming.md) changes what the built-in rendering *looks* like. Customization replaces the
rendering itself.

Every view is built from **slots**: composable parameters that default to a built-in rendering and
can be swapped for your own. The view keeps the parts that are hard — measurement, positioning,
paging, gestures, accessibility plumbing — and hands you the part that is yours: what to draw.

> Kalendar reads Material only to derive default token values. No Material widget renders the
> calendar, so an app with its own design system controls rendering completely, through
> [tokens](theming.md) and the slots on this page.

## What is replaceable, where

| Slot | Week | Month | Year | Timeline | Schedule | ScheduleWeek | Resource | Agenda | DatePicker |
|---|:-:|:-:|:-:|:-:|:-:|:-:|:-:|:-:|:-:|
| `dayContent` | yes | yes | yes | yes | no | no | no | no | yes |
| `header` | yes | yes | yes | yes | no | no | no | no | yes |
| `dayOfWeekLabel` | yes | yes | yes | yes | no | no | no | no | yes |
| `eventContent` | no | no | no | no | yes | yes | yes | yes | no |
| `allDayEventContent` | no | no | no | no | yes | yes | no | no | no |
| `hourLabel` | no | no | no | no | yes | yes | yes | no | no |
| `nowIndicator` | no | no | no | no | yes | yes | yes | no | no |
| `dayHeader` | no | no | no | no | no | yes | no | no | no |
| `resourceHeader` | no | no | no | no | no | no | yes | no | no |
| `dateHeader` | no | no | no | no | no | no | no | yes | no |
| `emptyState` | no | no | no | no | no | no | yes | yes | no |
| `actions` | no | no | no | no | no | no | no | no | yes |

One more sits a level down rather than on a view: `overflowPopup`, on
[`KalendarDayCellDefaults.Cell`](#reusing-the-built-in-cell), which is what the date grids' default
`dayContent` calls.

> **Note:** `eventContent` appears in three shapes. On `KalendarSchedule`/`KalendarScheduleWeek` it
> takes a `KalendarScheduleEventScope<E>` — the event plus where the grid placed it; on
> `KalendarResourceView` it takes a `KalendarResourceEventScope<E>`, which *extends* that with the lane;
> on [`KalendarAgenda`](views/agenda.md) it takes the `KalendarEvent` alone, because a list row has no
> position to describe. Contravariance means one
> `@Composable (KalendarScheduleEventScope<KalendarEvent>) -> Unit` serves both hour-grid shapes.

> **Note:** `eventContent` is the **last** parameter of every view that has one, so trailing-lambda
> syntax lands on the view's primary slot. The same is true of `dayContent` on the date grids.

The default-rendering objects are `KalendarHeaderDefaults` (the chrome around a date grid),
`KalendarDayCellDefaults` (the cell itself and its overflow popover), `KalendarScheduleDefaults`
(everything inside a time grid), `KalendarResourceDefaults` (a lane grid's pieces),
`KalendarAgendaDefaults` (the pieces of an event list) and `KalendarDatePickerDefaults` (the picker's
header, cell and action rail). Every slot defaults to one of their functions, which also makes them
the thing to reach for when you want to keep the built-in look and only add to it. Each is documented
symbol by symbol in the [API reference](reference/index.md).

## Custom day cells

`dayContent` replaces the built-in day cell entirely on the four date-grid views. It receives a
`KalendarDayScope<E>` describing the date being rendered, where `E` is the calendar's own event type.
`KalendarDatePicker` has the same slot but
wraps that scope in a [`KalendarDatePickerDayScope`](reference/kalendar-views.md), which adds where
the date sits in a range band.

### `KalendarDayScope<out E : KalendarEvent>`

| Property | Type | Description |
|---|---|---|
| `date` | `LocalDate` | The date this cell represents. |
| `isSelected` | `Boolean` | Whether `date` is in the calendar's `selectedDates`. |
| `isToday` | `Boolean` | Whether `date` is today, as read through the view's [`KalendarTimeSource`](reference/foundation-datetime.md) — not necessarily the system time zone. |
| `isDisabled` | `Boolean` | Whether `date` matched `KalendarViewConfig.disabledDates`. On the month-grid views this is also `true` for adjacent-month dates. |
| `events` | `List<E>` | The events occurring on `date`, multi-day spans included — your own event type, so no cast is needed to read your own fields. |

It is delivered as a scope object rather than positional lambda parameters so that future fields can
be added without breaking every existing call site. It is **covariant** in `E`, which is what lets a
cell written once against `KalendarDayScope<KalendarEvent>` be handed to a calendar of any event
type: a slot consumes its scope, and consumers are contravariant.

```kotlin
KalendarMonth(
    selectedDate = today,
    dayContent = { scope ->
        Box(
            modifier = Modifier
                .aspectRatio(1f)
                .padding(2.dp)
                .clip(CircleShape)
                .background(
                    if (scope.isSelected) KalendarTheme.colors.selectionBackground else Color.Transparent
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = scope.date.day.toString(),
                color = when {
                    scope.isSelected -> KalendarTheme.colors.onSelectionBackground
                    scope.isToday -> KalendarTheme.colors.todayContent
                    else -> KalendarTheme.colors.dayContent
                },
            )
        }
    },
)
```

### Keep custom cells accessible

A custom `dayContent` replaces the built-in cell — **including its accessibility and its click
handling**. `Modifier.kalendarDaySemantics()` gives both back:

```kotlin
import com.himanshoe.kalendar.component.kalendarDaySemantics

KalendarMonth(
    selectedDate = today,
    onDateClick = { date, _ -> select(date) },
    dayContent = { scope ->
        MyDayCell(
            day = scope.date.day,
            modifier = Modifier.kalendarDaySemantics(
                scope = scope,
                onClick = { select(scope.date) },
            ),
        )
    },
)
```

Applied to your cell's outermost modifier, it adds:

- a screen-reader `contentDescription` built from the date, plus
  `KalendarStrings.todaySuffix` when the date is today and `KalendarStrings.hasEventsSuffix` when it
  has events — for example `"August 12, 2026, today, has events"`;
- the `selected` semantic state, from `scope.isSelected`;
- `Role.Button`, so assistive technology announces the cell correctly;
- a click that is **ignored when `scope.isDisabled` is `true`**.

| Parameter | Type | Default | Description |
|---|---|---|---|
| `scope` | `KalendarDayScope<*>` | — | The cell's state, as handed to the slot. Star-projected: this modifier reads the interface only. |
| `onClick` | `() -> Unit` | — | Called when the cell is tapped and the date is not disabled. |
| `monthNameFormatter` | `(Month) -> String` | English month names | Formats the month name inside the announcement. Pass `KalendarViewConfig.monthNameFormatter` to match a localized calendar. |
| `interactionSource` | `MutableInteractionSource?` | `null` | Supply one when you want to observe press state yourself. Pass one to rebuild the focus ring and hover wash — see [Accessibility](#accessibility). |

> **Warning:** because the announcements come from `KalendarStrings`, a cell using
> `kalendarDaySemantics` stays localizable through the same [theme](theming.md) mechanism as the
> built-in one. A hand-rolled `contentDescription` does not.

> **Note:** `dayContent` does **not** disable the sliding selection indicator. The indicator is drawn
> behind the cell, so a custom cell keeps that animation. If you draw your own opaque selection fill,
> set `KalendarViewConfig(showSelectionIndicator = false)` to avoid drawing it twice.

### Reusing the built-in cell

`KalendarDayCellDefaults.Cell` is the exact composable the views fall back to, so you can wrap it
rather than reimplement it:

```kotlin
KalendarMonth(
    selectedDate = today,
    dayContent = { scope ->
        Box {
            KalendarDayCellDefaults.Cell(
                scope = scope,
                onClick = { select(scope.date) },
            )
            if (scope.date in holidays) {
                HolidayBadge(modifier = Modifier.align(Alignment.TopEnd))
            }
        }
    },
)
```

| Parameter | Type | Default | Description |
|---|---|---|---|
| `scope` | `KalendarDayScope<E>` | — | The cell's state. |
| `onClick` | `() -> Unit` | — | Called when the cell is tapped and the date is not disabled. |
| `modifier` | `Modifier` | `Modifier` | Applied to the cell. |
| `showSelectionBackground` | `Boolean` | `true` | Whether this cell draws its own selection fill. The views pass `false` when the sliding indicator is already drawing it. |
| `monthNameFormatter` | `(Month) -> String` | English month names | Used for the accessibility description. |
| `maxEventIndicators` | `Int` | `3` | Dots drawn before the remainder collapses into `+N`. The views pass `KalendarViewConfig.eventIndicatorCap`. |
| `onEventClick` | `(KalendarEvent) -> Unit` | `{}` | Called when an event is tapped inside the overflow popover. The views pass `onDayEventClick`. |
| `showOverflowPopup` | `Boolean` | `true` | Whether the `+N` label opens a popover at all. `false` leaves it as a plain count. |
| `overflowPopup` | `@Composable (KalendarDayOverflowScope<E>) -> Unit` | `KalendarDayCellDefaults.OverflowPopup` | Replaces the popover. **Last**, so trailing-lambda syntax lands on it. |

### The `+N` overflow popover

Tapping a cell's `+N` label opens a popover listing the date's **whole** event list — not just the
hidden tail, because a list starting at the fourth event is a list with no beginning. Its
`KalendarDayOverflowScope<E>` carries `date`, `events`, `hiddenEventCount`, `onEventClick(event)` and
`dismiss()`:

```kotlin
dayContent = { scope ->
    KalendarDayCellDefaults.Cell(
        scope = scope,
        onClick = { select(scope.date) },
        onEventClick = { event -> openDetails(event) },
    ) { overflowScope ->
        MySheet(onDismiss = overflowScope::dismiss) {
            overflowScope.events.forEach { event ->
                MyEventRow(event = event, onClick = { overflowScope.onEventClick(event) })
            }
        }
    }
}
```

Unlike the other slot scopes, `KalendarDayOverflowScope` is **invariant** in `E`, because
`onEventClick` consumes an event rather than producing one. That costs nothing at a call site like
the one above, where `E` is inferred. It matters only if you factor the popover out to share it
across calendars of different event types: make it a generic composable rather than a value typed
over `KalendarEvent`.

```kotlin
@Composable
fun <E : KalendarEvent> MyOverflowSheet(scope: KalendarDayOverflowScope<E>) { /* … */ }

// overflowPopup = { MyOverflowSheet(scope = it) }
```

A read-only popover may still take a `KalendarDayOverflowScope<*>`; a star projection can read
`events` but cannot call `onEventClick`.

## The header slot

`header` replaces the navigation chrome above a date grid on
[Week](views/week.md), [Month](views/month.md), [Year](views/year.md), and
[Timeline](views/timeline.md). It receives a `KalendarHeaderScope` that carries the formatted title
and the navigation callbacks, so your own chrome keeps working arrows, today button, and jump
behaviour.

### `KalendarHeaderScope`

| Member | Type | Description |
|---|---|---|
| `title` | `String` | The title the built-in header would show, already formatted — e.g. `"August 2026"`. |
| `visibleDate` | `LocalDate` | The start date of the page currently on screen. |
| `canScrollBackward` | `Boolean` | Whether a previous page exists within the calendar's `minDate` bound. |
| `canScrollForward` | `Boolean` | Whether a next page exists within the calendar's `maxDate` bound. |
| `goToPreviousPage()` | `Unit` | Animates to the previous week / month / year / day. A no-op when `canScrollBackward` is `false`. |
| `goToNextPage()` | `Unit` | Animates to the next page. A no-op when `canScrollForward` is `false`. |
| `goToToday()` | `Unit` | Animates back to the page containing today, clamped to the bounds. |
| `goTo(date)` | `Unit` | Animates to the page containing `date`, clamped to the bounds. |

The navigation functions are plain (non-suspending) calls — each launches the underlying `suspend`
animation on a scope the view owns — so a header slot can navigate straight from an `onClick`
without managing a coroutine scope of its own.

It is an interface rather than a data class, so later releases can add information to it without
breaking every existing `header` lambda.

```kotlin
KalendarMonth(
    selectedDate = today,
    header = { scope ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            MyIconButton(
                icon = MyIcons.ChevronLeft,
                enabled = scope.canScrollBackward,
                onClick = scope::goToPreviousPage,
            )
            Text(text = scope.title, modifier = Modifier.weight(1f), style = MyTheme.type.heading)
            MyIconButton(
                icon = MyIcons.ChevronRight,
                enabled = scope.canScrollForward,
                onClick = scope::goToNextPage,
            )
            MyTextButton(text = "Today", onClick = scope::goToToday)
        }
    },
)
```

On [`KalendarTimeline`](views/timeline.md) the same interface is mapped onto months, since a
timeline has no pages — see that page for the details, and note that a timeline header must paint an
opaque background of its own.

### Reusing the built-in header

`KalendarHeaderDefaults.Header` is what the slot defaults to, so calling it with different arguments
changes only what you asked to change:

```kotlin
KalendarMonth(
    selectedDate = today,
    // Keep the built-in header, but without the jump picker.
    header = { scope -> KalendarHeaderDefaults.Header(scope = scope, showJumpPicker = false) },
)
```

| Parameter | Type | Default | Description |
|---|---|---|---|
| `scope` | `KalendarHeaderScope` | — | Inside a `header` slot, the value the slot was handed. |
| `modifier` | `Modifier` | `Modifier` | Applied to the header's outermost container. |
| `showNavigationArrows` | `Boolean` | `true` | Whether to draw the previous/next buttons. Each disables itself when the matching `canScrollBackward` / `canScrollForward` is `false`. |
| `showTodayButton` | `Boolean` | `true` | Whether to draw the today button. |
| `showJumpPicker` | `Boolean` | `true` | Whether tapping the title opens the month/year jump picker. |
| `monthNameFormatter` | `(Month) -> String` | English month names | Formats the title's month name. |
| `shortMonthNameFormatter` | `(Month) -> String` | first three letters | Formats the jump picker's fixed-width month buttons, and the week/day titles. |
| `previousIcon` | `@Composable () -> Unit` | A Foundation-drawn chevron | The glyph inside the previous-page button. |
| `nextIcon` | `@Composable () -> Unit` | The same chevron, reversed | The glyph inside the next-page button. |
| `todayIcon` | `@Composable () -> Unit` | A Foundation-drawn calendar page | The glyph inside the today button. |
| `actions` | `@Composable RowScope.() -> Unit` | `{}` | Extra controls appended to the header's trailing edge, inside its `Row`. |

The flags and formatters default to values the view derives from
[`KalendarViewConfig`](configuration.md).

`Header` also works in [`KalendarTimeline`](views/timeline.md)'s `header` slot — every control drives
the `scope` rather than any particular state type — if you would rather have arrows there than the
default `TimelineHeader`.

### Swapping just one icon

This is the smallest version of bringing your own design system, and it does **not** require
replacing the `header` slot:

```kotlin
KalendarMonth(
    selectedDate = today,
    header = { scope ->
        KalendarHeaderDefaults.Header(
            scope = scope,
            previousIcon = { Image(painterResource(Res.drawable.chevron_left), contentDescription = null) },
            nextIcon = { Image(painterResource(Res.drawable.chevron_right), contentDescription = null) },
            todayIcon = { Image(painterResource(Res.drawable.today), contentDescription = null) },
        )
    },
)
```

The slot is a plain `@Composable () -> Unit`, so anything composes: a Foundation `Image` as above, a
Material `Icon`, your own design system's icon component, or a `Canvas` drawing the glyph by hand.

> **Note:** the slot draws the **glyph only**. The button around it — its touch target, its click
> handler, and its accessibility label from [`KalendarStrings`](localization.md) — belongs to the
> header. So pass `contentDescription = null` on your icon: labelling it again would make a screen
> reader announce the control twice.

Two consequences worth knowing:

- **The default icons are private.** There is no `KalendarChevronIcon` you can call, so an icon slot
  is all-or-nothing per icon — you supply a complete replacement rather than restyling the built-in
  one. The defaults are drawn with Compose Foundation and tinted `KalendarColors.headerContent`.
- **The default chevrons mirror under RTL** automatically. A replacement is your own composable, so
  mirroring it in a right-to-left layout is your responsibility.

Sizing still comes from the theme: `KalendarDimensions.headerIconSlotWidth` reserves the space each
button occupies, and the title's available width is computed from it — so an unusually wide custom
icon should be paired with a wider slot rather than left to overlap the title.

`KalendarHeaderDefaults.TimelineHeader` is the timeline's separate default — a leading-aligned
title, no arrows, an optional today button, and a `background: Brush?` for the opaque fill the
overlay needs.

## The day-of-week label slot

`dayOfWeekLabel` replaces the *content* of each weekday column header. The grid still measures and
places one label per column, so a replacement stays aligned with the day cells below it.

```kotlin
KalendarMonth(
    selectedDate = today,
    dayOfWeekLabel = { dayOfWeek ->
        Text(
            text = dayOfWeek.name.take(2),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    },
)
```

To keep the built-in styling and change only the text, call the default with your own formatter:

```kotlin
KalendarMonth(
    selectedDate = today,
    dayOfWeekLabel = { dayOfWeek ->
        KalendarHeaderDefaults.DayOfWeekLabel(
            dayOfWeek = dayOfWeek,
            formatter = { it.name.take(2) },
        )
    },
)
```

For a locale-aware label, set
[`KalendarViewConfig.dayOfWeekLabelFormatter`](localization.md) instead — no slot needed.

## Schedule content slots

The two Schedule views expose a slot for every piece they draw. The grid keeps layout, clicks, and
drag-to-reschedule, so custom content never loses them.

### `eventContent`

Draws one timed event block. It is given a box already positioned and sized for the event's duration
and its share of any overlap, so an implementation only draws — it never positions.

`KalendarScheduleEventScope<E>`:

| Property | Type | Description |
|---|---|---|
| `event` | `KalendarEvent` | The event being rendered. |
| `start` | `LocalDateTime` | The event's start, clamped to the rendered day when it began earlier. |
| `end` | `LocalDateTime` | The event's end, clamped to the rendered day when it runs past midnight. |
| `durationMinutes` | `Int` | How many minutes of the rendered day this block covers, after clamping. |
| `isDragging` | `Boolean` | Whether the user is currently dragging or resizing this block. |
| `overlapColumn` | `Int` | Zero-based column among events that overlap this one. `0` when nothing overlaps. |
| `overlapColumns` | `Int` | How many events share this block's span, including itself. `1` when nothing overlaps. |

```kotlin
KalendarSchedule(
    events = events,
    eventContent = { scope ->
        MyEventCard(
            title = scope.event.eventName,
            subtitle = "${scope.start.time} – ${scope.end.time}",
            dimmed = scope.isDragging,
        )
    },
)
```

`durationMinutes` and `overlapColumns` are there so you can adapt density — a short block or a
crowded column has no room for a subtitle:

```kotlin
eventContent = { scope ->
    if (scope.durationMinutes >= 45 && scope.overlapColumns == 1) {
        MyRichEventCard(scope)
    } else {
        KalendarScheduleDefaults.EventBlock(scope = scope)
    }
}
```

### `allDayEventContent`

Draws one chip in the all-day row above the grid. It receives just the `KalendarEvent`; the row
applies `onEventClick` for you.

On [`KalendarScheduleWeek`](views/schedule-week.md) all seven days share one row, so the default
prefixes each chip with its day of month. A replacement slot does not get that prefix
automatically — pass it yourself if you want it:

```kotlin
KalendarScheduleWeek(
    events = events,
    allDayEventContent = { event ->
        KalendarScheduleDefaults.AllDayChip(
            event = event,
            prefix = event.date.day.toString(),
        )
    },
)
```

### `hourLabel`

Draws one hour-gutter label, given the hour as `0..23`. The gutter aligns it to the top of its hour
row and is `KalendarDimensions.hourGutterWidth` wide.

```kotlin
KalendarSchedule(
    hourLabel = { hour -> Text(text = twelveHourLabel(hour), style = MyTheme.type.caption) },
)
```

For a plain format change, set
[`KalendarViewConfig.hourLabelFormatter`](localization.md) instead and keep the built-in styling.

### `nowIndicator`

Draws the current-time marker. The grid decides whether the day is today and owns the marker's
vertical offset, so the slot renders the marker alone:

```kotlin
KalendarSchedule(
    nowIndicator = {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(BrandRed))
            Box(modifier = Modifier.weight(1f).height(2.dp).background(BrandRed))
        }
    },
)
```

### `dayHeader`

`KalendarScheduleWeek` only. Draws one day column's header, given the column's date and whether it
is today. It is stretched to the column's width.

```kotlin
KalendarScheduleWeek(
    dayHeader = { date, isToday ->
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = date.dayOfWeek.name.take(3))
            Text(
                text = date.day.toString(),
                color = if (isToday) BrandAccent else Color.Unspecified,
            )
        }
    },
)
```

### `KalendarScheduleDefaults`

Every Schedule slot defaults to one of these, and all of them draw only — the grid owns positioning,
clicks, and drag-to-reschedule.

| Function | Signature |
|---|---|
| `EventBlock` | `(scope: KalendarScheduleEventScope<*>, modifier: Modifier = Modifier)` |
| `AllDayChip` | `(event: KalendarEvent, modifier: Modifier = Modifier, prefix: String? = null)` |
| `HourLabel` | `(hour: Int, formatter: (Int) -> String, modifier: Modifier = Modifier)` |
| `WeekDayHeader` | `(date: LocalDate, isToday: Boolean, dayOfWeekLabelFormatter: (DayOfWeek) -> String, modifier: Modifier = Modifier)` |
| `NowIndicator` | `(modifier: Modifier = Modifier)` |

Falling back to one of them for the cases you do not render specially is the intended pattern — it
keeps a custom slot consistent with the rest of the calendar.

### `KalendarResourceDefaults`

`KalendarResourceView`'s equivalents. Its `eventContent` scope adds `resource`, so a block can show
which lane it is in.

| Function | Signature |
|---|---|
| `ResourceHeader` | `(resource: KalendarResource, modifier: Modifier = Modifier)` |
| `EventBlock` | `(scope: KalendarResourceEventScope<*>, modifier: Modifier = Modifier)` |
| `HourLabel` | `(hour: Int, formatter: (Int) -> String, modifier: Modifier = Modifier)` |
| `NowIndicator` | `(modifier: Modifier = Modifier)` |
| `EmptyState` | `(modifier: Modifier = Modifier)` |

### `KalendarDatePickerDefaults`

`KalendarDatePicker`'s own chrome, which is a different shape from a grid view's — see the
[reference](reference/kalendar-views.md) for the full parameter lists.

| Member | What it is |
|---|---|
| `Width` | `312.dp`, the width the picker is drawn for. It imposes no size of its own. |
| `Header` | Month and year controls, each opening onto its own grid, with the page arrows demoted to the trailing corner. |
| `DayCell` | Today as a rule under the number, selected as a filled `KalendarShapes.dayCell` — different marks, so both can be true of one cell and still be read apart. |
| `ActionRow` | The Clear/Today rail. |

## Agenda content slots

[`KalendarAgenda`](views/agenda.md) has three, and the list keeps the scrolling and the click
handling around whatever they draw:

| Slot | Signature | Default |
|---|---|---|
| `dateHeader` | `(date: LocalDate) -> Unit` | `KalendarAgendaDefaults.DateHeader` |
| `eventContent` | `(event: KalendarEvent) -> Unit` | `KalendarAgendaDefaults.EventRow` |
| `emptyState` | `() -> Unit` | `KalendarAgendaDefaults.EmptyState` |

### `KalendarAgendaDefaults`

| Function | Signature |
|---|---|
| `DateHeader` | `(date: LocalDate, monthNameFormatter: (Month) -> String, modifier: Modifier = Modifier)` |
| `EventRow` | `(event: KalendarEvent, modifier: Modifier = Modifier)` |
| `EmptyState` | `(modifier: Modifier = Modifier)` |

```kotlin
KalendarAgenda(
    events = events,
    eventContent = { event ->
        if (event.calendarId == "work") {
            MyWorkEventRow(event)
        } else {
            KalendarAgendaDefaults.EventRow(event = event)
        }
    },
)
```

## Accessibility

None of this needs turning on. It is what the built-in renderings already do, and it is listed here
because a custom slot replaces the piece that does it — and because knowing which strings drive it is
how you [localize](localization.md) the announcements.

### Keyboard navigation

`KalendarMonth`, `KalendarWeek`, `KalendarYear`, `KalendarTimeline` and `KalendarDatePicker` are all
navigable from the keyboard on desktop and wasmJs, with the same key map.

**The grid is one tab stop, not forty-two.** This is the roving-tabindex pattern every native date
picker uses: `Tab` enters the grid once, arrow keys move a focus ring between days without leaving
it, and `Tab` again leaves. Reaching 42 cells by pressing `Tab` 42 times is a tab trap with extra
steps, so the cells themselves are deactivated as focus targets.

| Key | What it does |
|---|---|
| <kbd>←</kbd> <kbd>→</kbd> | One visible day. On a Mon–Fri calendar, `→` from Friday reaches Monday rather than stopping on a Saturday that has no cell. |
| <kbd>↑</kbd> <kbd>↓</kbd> | One week, landing on the same weekday and so in the same column. |
| <kbd>PageUp</kbd> <kbd>PageDown</kbd> | One page of the view's own unit — a week for `KalendarWeek`, a month for `KalendarMonth`, `KalendarTimeline` and `KalendarDatePicker`, a year for `KalendarYear`. |
| <kbd>Home</kbd> <kbd>End</kbd> | First and last day of the **period**, not the grid — `Home` in a month view reaches the 1st, not the leading padding day from the previous month. |
| <kbd>Enter</kbd> <kbd>Space</kbd> | Selects the focused date, through the same callback a tap goes through. |
| <kbd>Shift</kbd> + arrow | Extends a range from where focus was when `Shift` was first held, reporting through `onDateRangeSelect` exactly as the press-and-hold drag does. Releasing `Shift` calls `onDateRangeSelectEnd`. |

Moving off the end of the visible period scrolls the period holding the target into view and the
focus lands on it, so a month boundary is invisible to the user.

`Shift`+arrow reports a range only on the two views that have a range-drag gesture for it to mirror —
`KalendarMonth` and `KalendarWeek`, the ones with `onDateRangeSelect`. On `KalendarYear`,
`KalendarTimeline` and `KalendarDatePicker` it moves focus and reports nothing, rather than inventing
a second range API for a view that has no gesture equivalent. Where it does apply it is not a nicety:
without it `onDateRangeSelect` would be reachable by pointer only, which is a WCAG 2.1.1 failure.

**Disabled dates are focusable but not selectable.** Focus lands on a date your `disabledDates`
predicate rejects — a keyboard user is told the date exists and is unavailable — but `Enter` and
`Space` do nothing on it, exactly as the cell's own `clickable(enabled = false)` behaves for a tap.
Range extension does not consult it at all, matching the drag gesture, which never has either.

### Focus and hover affordances

A focused cell draws a 2dp ring in `KalendarColors.focusIndicator` (defaulting to `todayContent`),
and a cell under the pointer is washed with `KalendarColors.hoverBackground` (`dayContent` at 8%).
Without the ring, keyboard navigation of a month grid is invisible — the focus moves and nothing on
screen says where it went.

Both are driven by the cell's `MutableInteractionSource`. A custom `dayContent` replaces the cell and
therefore both affordances; pass your own interaction source to `kalendarDaySemantics` and read
`collectIsFocusedAsState()` / `collectIsHoveredAsState()` from it to draw them again:

```kotlin
val interactionSource = remember { MutableInteractionSource() }
val isFocused by interactionSource.collectIsFocusedAsState()
val isHovered by interactionSource.collectIsHoveredAsState()

MyDayCell(
    modifier = Modifier
        .background(if (isHovered) KalendarTheme.colors.hoverBackground else Color.Transparent)
        .border(
            width = 2.dp,
            color = if (isFocused) KalendarTheme.colors.focusIndicator else Color.Transparent,
            shape = KalendarTheme.shapes.dayCell,
        )
        .kalendarDaySemantics(
            scope = scope,
            onClick = { select(scope.date) },
            interactionSource = interactionSource,
        ),
)
```

### A bounded calendar's arrows look bounded

When `minDate` or `maxDate` blocks a direction, that header arrow is drawn in
`KalendarColors.headerContentDisabled` — `headerContent` at 38% — as well as being inert. An arrow
that looks live and silently does nothing is the version of this that ships by accident.

This applies to the built-in chevron and today glyphs. A caller-supplied icon slot draws itself and
is left alone, so it is yours to dim: read `scope.canScrollBackward` / `scope.canScrollForward` from
the [header scope](#kalendarheaderscope).

### What a screen reader hears

| Element | Announcement | String |
|---|---|---|
| Day cell | `"August 12, 2026, today, has events"` | `dayAccessibilityLabel` + `todaySuffix` + `hasEventsSuffix` |
| Day-of-week column header | `"Monday"` — the header only *shows* `"M"` | `dayOfWeekAccessibilityLabel` |
| Timed event block | `"Design review, 09:30 to 11:00"` | `eventAccessibilityLabel` |
| Timed block in a lane | `"Design review, 09:30 to 11:00, Room A"` | `resourceEventAccessibilityLabel` |
| All-day chip | `"Release day, all day"` | `allDayEventAccessibilityLabel` |
| Header arrows, today, jump picker | `"Previous"`, `"Next"`, `"Today"`, `"Choose month and year"` | `previousPage`, `nextPage`, `today`, `openJumpPicker` |

All of them live on [`KalendarStrings`](localization.md), so translating the calendar translates what
is announced. The event labels are applied by `KalendarScheduleDefaults.EventBlock`, `.AllDayChip`
and `KalendarResourceDefaults.EventBlock` — a replacement `eventContent` or `allDayEventContent` slot
draws its own semantics, and should.

## Choosing between a token, a config value, and a slot

Reach for the cheapest thing that works:

| You want to change | Use |
|---|---|
| A colour, size, corner, or motion spec | A [token](theming.md) |
| A date, month, or hour **format** | A [`KalendarViewConfig` formatter](localization.md) |
| A word or announcement | [`KalendarStrings`](localization.md) |
| Whether a built-in control appears at all | A [`KalendarViewConfig` flag](configuration.md) |
| The structure of what is drawn | A slot on this page |

A slot is the most powerful option and the most work — it opts you out of every future improvement
to that piece of the rendering. Prefer tokens and config until they genuinely cannot express what
you need.

And if what you want is not a different *rendering* but a different **calendar** — a booking grid, a
shift planner, a picker with its own layout entirely — the slots are the wrong lever. Build on the
[headless engine](engine.md) instead: the same date, paging, overlap and selection arithmetic these
views run on, with no rendering attached.
