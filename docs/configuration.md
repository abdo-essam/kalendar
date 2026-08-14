# Configuration

Every view — [`KalendarWeek`](views/week.md), [`KalendarMonth`](views/month.md),
[`KalendarYear`](views/year.md), [`KalendarTimeline`](views/timeline.md),
[`KalendarSchedule`](views/schedule.md), [`KalendarScheduleWeek`](views/schedule-week.md),
[`KalendarAgenda`](views/agenda.md), `KalendarResourceView` and `KalendarDatePicker` — takes the same
`KalendarViewConfig` through its `config` parameter.

Options that apply to only some views say so below. Passing one to a view that ignores it is
harmless.

```kotlin
import com.himanshoe.kalendar.KalendarViewConfig
import kotlinx.datetime.DayOfWeek

KalendarMonth(
    selectedDate = today,
    config = KalendarViewConfig(
        disabledDates = { it.dayOfWeek == DayOfWeek.SUNDAY },
        showJumpPicker = false,
        eventIndicatorCap = 2,
    ),
)
```

## All options

| Option | Type | Default | Description |
|---|---|---|---|
| `disabledDates` | `(LocalDate) -> Boolean` | `{ false }` | Called for each date. Returning `true` renders it at `KalendarColors.disabledContentAlpha` and ignores taps. No effect on the Schedule views. |
| `showNavigationArrows` | `Boolean` | `true` | Previous/next arrow buttons in the header. Swiping works regardless. Ignored by `KalendarTimeline`. |
| `showAdjacentMonthDates` | `Boolean` | `true` | Whether the previous/next month's padding dates are drawn dimmed or left blank. Month-grid views only — no effect on `KalendarWeek` or the Schedule views. |
| `showTodayButton` | `Boolean` | `true` | A header button that animates back to today. On `KalendarTimeline` this is the sticky header's button. |
| `showJumpPicker` | `Boolean` | `true` | Whether tapping the header title opens a month/year picker that jumps straight to the chosen month. Ignored by `KalendarTimeline`, whose sticky title is not tappable. |
| `showSelectionIndicator` | `Boolean` | `true` | Whether one shared indicator slides between selected day cells instead of each cell drawing its own fill. `KalendarWeek` and `KalendarMonth` only, and only while exactly one date is selected. |
| `monthNameFormatter` | `(Month) -> String` | English names | Month names for titles, headers, and accessibility descriptions. See [Localization](localization.md). |
| `shortMonthNameFormatter` | `(Month) -> String` | First three letters | Month names where the full one will not fit: week and day titles, and the jump picker's month buttons. See [Localization](localization.md). |
| `dayOfWeekNameFormatter` | `(DayOfWeek) -> String` | First three letters | The abbreviated day name in `KalendarSchedule`'s day title. See [Localization](localization.md). |
| `dayOfWeekLabelFormatter` | `(DayOfWeek) -> String` | First letter | The day-of-week column headers. See [Localization](localization.md). |
| `hourLabelFormatter` | `(Int) -> String` | `"HH:00"` | Hour-gutter labels on the hour-grid views. See [Localization](localization.md). |
| `visibleDaysOfWeek` | `Set<DayOfWeek>` | all seven | Which weekdays get a column. `KalendarWeek`, `KalendarMonth`, `KalendarYear`, `KalendarTimeline`, `KalendarScheduleWeek` and `KalendarDatePicker`; the single-day views have no columns to drop. See [Work weeks](#work-weeks). Must not be empty. |
| `scheduleVisibleHours` | `KalendarHourWindow` | `FullDay` | The slice of the day the hour grids draw. `KalendarSchedule`, `KalendarScheduleWeek` and `KalendarResourceView`. See [Business hours](#business-hours). |
| `scheduleInitialScrollHour` | `Int` | `7` | The hour the hour-grid views scroll to when a page first appears. **Absolute**, not relative to `scheduleVisibleHours` — an hour above the window opens the grid at its top rather than scrolling backwards past it. Must be `0..23`. |
| `scheduleDragSnapMinutes` | `Int` | `15` | Snap step, in minutes, for dragging, resizing and sweeping out event blocks. Must be at least `1`. |
| `eventIndicatorCap` | `Int` | `3` | Maximum event dots below a day number before collapsing into `+N`. Must be at least `1`. |
| `background` | `Brush?` | `null` | Background brush for the calendar container. `null` means `KalendarColors.background`. |

> **Warning:** four options are validated in `KalendarViewConfig`'s `init` block and throw
> `IllegalArgumentException` **on construction** — not at render time. `visibleDaysOfWeek` must be
> non-empty, `scheduleInitialScrollHour` must be in `0..23`, `scheduleDragSnapMinutes` at least `1`,
> and `eventIndicatorCap` at least `1`.

`KalendarViewConfig` is an `@Immutable` class with a hand-written `copy()`, so you can derive one
config from another:

```kotlin
val base = KalendarViewConfig(monthNameFormatter = ::localizedMonthName)

KalendarMonth(selectedDate = today, config = base)
KalendarSchedule(config = base.copy(scheduleInitialScrollHour = 9))
```

## Disabled dates

`disabledDates` is a predicate, so any rule you can express in Kotlin works:

```kotlin
// Weekends off.
KalendarViewConfig(
    disabledDates = { it.dayOfWeek == DayOfWeek.SATURDAY || it.dayOfWeek == DayOfWeek.SUNDAY },
)

// Nothing in the past.
KalendarViewConfig(disabledDates = { it < today })

// A fixed set of blackout dates.
KalendarViewConfig(disabledDates = { it in blackoutDates })
```

A disabled date is dimmed and ignores taps, but it is **not** removed from the grid — the layout
stays stable.

> **Note:** on the month-grid views, adjacent-month padding dates are treated as disabled regardless
> of this predicate, so `KalendarDayScope.isDisabled` is `true` for them too. Check
> `scope.date.month` in a custom `dayContent` if you need to tell the two cases apart.

## Work weeks

`visibleDaysOfWeek` drops a weekday's column entirely, rather than dimming it:

```kotlin
KalendarViewConfig(
    visibleDaysOfWeek = ALL_DAYS_OF_WEEK - setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY),
)
```

Everything narrows together: the grid to five columns, the day-of-week header row with it, the month
grid's leading padding recomputed so the columns stay under their labels, and keyboard arrows
stepping *over* the hidden days rather than onto them. That last one is
[`visibleDateStep`](reference/foundation-grid.md) in the engine — `Right` from Friday lands on
Monday, because Saturday is not a cell the focus ring can sit on.

It is independent of `KalendarViewState.startDayOfWeek`, which still decides where the week *begins*.

## Business hours

`scheduleVisibleHours` is the slice of the day an hour grid draws:

```kotlin
KalendarViewConfig(scheduleVisibleHours = KalendarHourWindow(startHour = 8, endHour = 20))
```

The end hour is exclusive: this grid's last labelled row is 19:00 and its bottom edge is 20:00.
Everything follows the window rather than midnight — the gutter labels, the rules, block positions,
the now-indicator's visibility, and the clamps on dragging and resizing.

> **Note: the window clips, it does not filter.** A 07:00–09:00 meeting on an 08:00 grid is drawn
> from the top edge to 09:00 with its start cut off, so the window never silently hides that
> something is booked. Only an event lying *entirely* outside is dropped. It is a display range, not
> a business rule: it does not stop your events starting at 03:00, and callbacks always report real
> wall-clock times.

It lives on the config rather than on each composable because it is a policy an app sets once for
every hour grid it shows. See [`KalendarHourWindow`](reference/foundation-schedule.md) for its full
surface.

## Backgrounds

`background` accepts a `Brush`, so it exists for what a single colour cannot express:

```kotlin
KalendarViewConfig(
    background = Brush.verticalGradient(listOf(Color(0xFFF3E8FF), Color(0xFFFFFFFF))),
)
```

For a plain colour, prefer the [`KalendarColors.background` token](theming.md) — it follows light and
dark mode automatically, whereas a `Brush` here is fixed.

When both are set, `background` wins.

> **Note:** on `KalendarTimeline`, pass the same brush to the sticky header if you replace it. The
> overlay needs an opaque fill or the months scroll visibly underneath it — see
> [Customization](customization.md).

## The clock

Every view, state factory and now-indicator reads "today" through a `KalendarTimeSource` rather than
calling `Clock.System` directly. It is the one seam between the calendar and the wall clock, and it
exists because that seam is needed twice:

```kotlin
import com.himanshoe.kalendar.foundation.datetime.KalendarTimeSource
import com.himanshoe.kalendar.state.ProvideKalendarTimeSource

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

| Member | What it is for |
|---|---|
| `KalendarTimeSource(timeZone, clock)` | The pair itself. `timeZone` defaults to the device's current zone, `clock` to `Clock.System`. |
| `ProvideKalendarTimeSource(source) { … }` | Provides it to every view inside. The single injection point — a view's `state`, its `initialDate` default, and its now-indicator all end up reading the same clock. |
| `rememberKalendarTimeSource()` | The ambient one, or a composition-scoped default. |
| `rememberKalendarToday(timeSource)` | Today's date, **re-read when the day changes**. |
| `rememberKalendarNow(timeSource, tick)` | The current date *and time*, re-read every `tick`. |

**Deterministic tests and screenshots.** Hand it a fixed `Clock` and the calendar's idea of today
stops moving, so an assertion about which cell is highlighted — or a screenshot of it — holds
forever. Every picture on this site is generated that way.

**A calendar that survives midnight.** Left alone, `rememberKalendarToday` re-reads the clock when
the day rolls over, so a calendar left open overnight highlights the new day instead of yesterday's.

**A calendar in another time zone.** Pass a `timeZone` and "today" is resolved there, which is what
you want for an app showing a venue's or a colleague's calendar rather than the device's.

> **Note:** there is deliberately **no** `today` property on the state classes. A clock-derived value
> stored on a `@Stable` object notifies nobody when the clock moves on, so it goes stale at midnight
> and no recomposition is ever scheduled to fix it. Read it in composition instead —
> `rememberKalendarToday(state.timeSource)`.

Instances must be stable across recompositions: `remember` yours or hold it in a view model. A fresh
instance built inline every frame re-arms every rollover timer that depends on it.

## What is *not* here

Three kinds of setting deliberately live elsewhere:

| Setting | Where it lives |
|---|---|
| Colours, text styles, shapes, sizes, motion | [`KalendarTheme`](theming.md) tokens |
| Words and accessibility announcements | [`KalendarStrings`](localization.md) |
| Initial date, week start day, min/max bounds, the clock | The `rememberKalendar*State` factories and [`KalendarTimeSource`](views/index.md#the-state-factories) |
| Which dates are selected, and what a tap does | `selectedDates` / `onDateClick`, or [`rememberKalendarSelectionState`](reference/kalendar-state.md) |

`KalendarViewConfig` holds *behaviour and formatting*: what appears, what is interactive, and how
dates are turned into text.
