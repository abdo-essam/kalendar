# `foundation.grid`

`com.himanshoe.kalendar.foundation.grid`

Which dates a calendar grid draws, and which weekdays get a column at all.

Every function here shares one obligation: produce a run of dates in the same weekday rotation the
header uses, so that chunking the run by the column count puts each date under its own label.
Getting that wrong misaligns every column at once, which is why all of it is one small package.

```kotlin
import com.himanshoe.kalendar.foundation.grid.monthGridDates
```

Every function takes a `visibleDaysOfWeek: Set<DayOfWeek>` defaulting to
[`ALL_DAYS_OF_WEEK`](#all-days-of-week). They all **rotate first, then filter**, in that order — that
identity is what keeps the columns under their labels, and it is not an accident of the arithmetic.

## `ALL_DAYS_OF_WEEK`

```kotlin
public val ALL_DAYS_OF_WEEK: Set<DayOfWeek>
```

Every day of the week, and so the default for every `visibleDaysOfWeek` parameter here: a calendar
shows the whole week unless a caller asks for less.

```kotlin
val workWeek = ALL_DAYS_OF_WEEK - setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
```

## `weekDates`

```kotlin
public fun weekDates(
    weekStart: LocalDate,
    visibleDaysOfWeek: Set<DayOfWeek> = ALL_DAYS_OF_WEEK,
): List<LocalDate>
```

The dates of the week beginning at `weekStart`, keeping only those whose day of week is visible — a
Mon–Fri configuration yields five dates, and so five grid columns.

Order is preserved, so the result still runs left to right from `weekStart`: hiding a day removes a
column rather than shifting the rest onto different weekdays.

| Parameter | Type | Default | Description |
|---|---|---|---|
| `weekStart` | `LocalDate` | — | The week's first day. Get it with `date.startOfWeek(startDayOfWeek)`. |
| `visibleDaysOfWeek` | `Set<DayOfWeek>` | `ALL_DAYS_OF_WEEK` | Which weekdays get a column. |

```kotlin
val weekStart = LocalDate(2026, 6, 10).startOfWeek(DayOfWeek.MONDAY)
weekDates(weekStart).forEach { date -> DayCell(date) }
```

## `monthGridDates`

```kotlin
public fun monthGridDates(
    monthStart: LocalDate,
    startDayOfWeek: DayOfWeek,
    visibleDaysOfWeek: Set<DayOfWeek> = ALL_DAYS_OF_WEEK,
): List<LocalDate>
```

The dates a month grid renders: the month's own days, plus the **leading** padding needed to align
the first row on `startDayOfWeek`, filtered to `visibleDaysOfWeek`.

| Parameter | Type | Default | Description |
|---|---|---|---|
| `monthStart` | `LocalDate` | — | The month's first day. Any date in the month works if you call `.startOfMonth()` first. |
| `startDayOfWeek` | `DayOfWeek` | — | Which weekday the first column is. |
| `visibleDaysOfWeek` | `Set<DayOfWeek>` | `ALL_DAYS_OF_WEEK` | Which weekdays get a column. |

```kotlin
val headers = daysOfWeekStartingAt(DayOfWeek.MONDAY)
val rows = monthGridDates(
    monthStart = LocalDate(2026, 8, 1),
    startDayOfWeek = DayOfWeek.MONDAY,
).chunked(headers.size)
```

> **Warning: the leading row is padded, the trailing row is not.** The run stops on the month's last
> day, so `size` is **not** a multiple of the column count and `chunked` produces a short final row.
> A layout that assumes whole rows draws a ragged last week. Pad it yourself — see
> [the engine guide](../engine.md) for the `Spacer` pattern.

The asymmetry is deliberate. Leading padding is *required* for correctness: without it every column
sits under the wrong label. Trailing padding is a purely visual choice, and one a booking grid may
not want.

The padding is measured from the month's first **visible** day rather than from its 1st. With every
day visible the two are the same date; with weekends hidden and a month opening on a Saturday they
differ, and measuring from the 1st would emit a whole leading row belonging entirely to the previous
month.

## `monthDates`

```kotlin
public fun monthDates(
    monthStart: LocalDate,
    visibleDaysOfWeek: Set<DayOfWeek> = ALL_DAYS_OF_WEEK,
): List<LocalDate>
```

A month's own days, **without** the padding `monthGridDates` adds, filtered to `visibleDaysOfWeek`.

This is the *period* a keyboard user navigates: `Home` belongs on the 1st, not on the previous
month's 26th — and not on a Saturday that is not on screen.

| Parameter | Type | Default | Description |
|---|---|---|---|
| `monthStart` | `LocalDate` | — | The month's first day. |
| `visibleDaysOfWeek` | `Set<DayOfWeek>` | `ALL_DAYS_OF_WEEK` | Which weekdays are on screen. |

```kotlin
val august = monthDates(LocalDate(2026, 8, 1))
val home = august.first() // 2026-08-01
val end = august.last()   // 2026-08-31
```

## `visibleDateOnOrAfter`

```kotlin
public fun visibleDateOnOrAfter(
    date: LocalDate,
    visibleDaysOfWeek: Set<DayOfWeek> = ALL_DAYS_OF_WEEK,
): LocalDate
```

`date` itself when its day of week is visible, otherwise the next date that is.

This is how a jump that does not preserve the weekday — a month step keeps the day of *month* —
lands on a cell that exists. `visibleDaysOfWeek` must be non-empty, so one of the next seven days
always qualifies and this cannot fail.

| Parameter | Type | Default | Description |
|---|---|---|---|
| `date` | `LocalDate` | — | The date being landed on. |
| `visibleDaysOfWeek` | `Set<DayOfWeek>` | `ALL_DAYS_OF_WEEK` | Which weekdays exist as cells. Must be non-empty. |

```kotlin
val workWeek = ALL_DAYS_OF_WEEK - setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
// 2026-08-01 is a Saturday
visibleDateOnOrAfter(LocalDate(2026, 8, 1), workWeek) // 2026-08-03, the Monday
```

## `visibleDateStep`

```kotlin
public fun visibleDateStep(
    from: LocalDate,
    step: Int,
    visibleDaysOfWeek: Set<DayOfWeek> = ALL_DAYS_OF_WEEK,
): LocalDate
```

The nearest visible date `step` **visible days** away from `from` — `+1` for the next one, `-1` for
the previous — skipping hidden days entirely.

This is what a left/right arrow key means once a day can be missing from the grid: on a Mon–Fri week,
`Right` from Friday lands on Monday, because Saturday is not a cell the focus ring can sit on.

| Parameter | Type | Default | Description |
|---|---|---|---|
| `from` | `LocalDate` | — | The date being stepped away from. |
| `step` | `Int` | — | How many visible days to move. Negative moves backwards. |
| `visibleDaysOfWeek` | `Set<DayOfWeek>` | `ALL_DAYS_OF_WEEK` | Which weekdays exist as cells. |

```kotlin
val workWeek = ALL_DAYS_OF_WEEK - setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
val friday = LocalDate(2026, 3, 13)
visibleDateStep(from = friday, step = 1, visibleDaysOfWeek = workWeek) // 2026-03-16, the Monday
```

## `daysOfWeekStartingAt`

```kotlin
public fun daysOfWeekStartingAt(
    startDayOfWeek: DayOfWeek,
    visibleDaysOfWeek: Set<DayOfWeek> = ALL_DAYS_OF_WEEK,
): List<DayOfWeek>
```

The weekday column headers, rotated to begin at `startDayOfWeek` and reduced to `visibleDaysOfWeek`.

The same rotate-then-filter order [`monthGridDates`](#monthgriddates) and [`weekDates`](#weekdates)
use, which is what guarantees the labels line up one-for-one with the columns beneath them.

| Parameter | Type | Default | Description |
|---|---|---|---|
| `startDayOfWeek` | `DayOfWeek` | — | The first column. |
| `visibleDaysOfWeek` | `Set<DayOfWeek>` | `ALL_DAYS_OF_WEEK` | Which weekdays get a column. |

```kotlin
daysOfWeekStartingAt(DayOfWeek.SUNDAY) // [SUNDAY, MONDAY, ..., SATURDAY]
```

Pass each entry through a `(DayOfWeek) -> String` of your own — or through
[`KalendarFormatters.dayOfWeekLabel`](foundation-format.md) — to get the text.
