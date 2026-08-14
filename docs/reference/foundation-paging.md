# `foundation.paging`

`com.himanshoe.kalendar.foundation.paging`

The two-way map between a scroll container's page index and the date that page shows, plus the bounds
that map has to respect — with no scroll container in it.

```kotlin
import com.himanshoe.kalendar.foundation.paging.KalendarPager
```

This is the arithmetic the built-in views' `HorizontalPager` and `LazyColumn` are driven by, so a
custom strip built on it and a built-in `KalendarMonth` given the same `initialDate` and
`startDayOfWeek` agree on which page is which month.

## `KalendarPageUnit`

```kotlin
public enum class KalendarPageUnit { Day, Week, Month, Year }
```

How much of the calendar one page shows, and so what "the next page" means. This is the whole
per-view difference between a day schedule, a week strip, a month grid and a year overview —
everything else about paging is the same arithmetic.

| Entry | One page is |
|---|---|
| `Day` | One day — a schedule's single-day view. |
| `Week` | One week, beginning at the caller's `startDayOfWeek`. |
| `Month` | One calendar month. |
| `Year` | One calendar year. |

`KalendarPager` is built from exactly the three operations below; reach for the unit directly when
you want the step without the page-index bookkeeping.

### `startOf`

```kotlin
public fun startOf(date: LocalDate, startDayOfWeek: DayOfWeek = DayOfWeek.MONDAY): LocalDate
```

The date that keys the page containing `date` — the first day of its day/week/month/year. Two dates
on the same page always give the same answer, which is what makes it safe as a page identity.

| Parameter | Type | Default | Description |
|---|---|---|---|
| `date` | `LocalDate` | — | Any date on the page. |
| `startDayOfWeek` | `DayOfWeek` | `DayOfWeek.MONDAY` | Which weekday a week begins on. Read by `Week` only; ignored otherwise. |

```kotlin
KalendarPageUnit.Week.startOf(LocalDate(2026, 6, 10), DayOfWeek.MONDAY) // 2026-06-08
KalendarPageUnit.Month.startOf(LocalDate(2026, 8, 15))                  // 2026-08-01
```

### `plusPages`

```kotlin
public fun plusPages(date: LocalDate, pages: Int): LocalDate
```

`date` moved `pages` pages forward, or backward for a negative count. The day of the month is
preserved where the unit allows it, exactly as `kotlinx-datetime`'s own `plus` does.

| Parameter | Type | Default | Description |
|---|---|---|---|
| `date` | `LocalDate` | — | The starting date. |
| `pages` | `Int` | — | How many pages to move. Negative moves backwards. |

```kotlin
KalendarPageUnit.Month.plusPages(LocalDate(2026, 8, 1), -2) // 2026-06-01
```

### `pagesBetween`

```kotlin
public fun pagesBetween(
    from: LocalDate,
    to: LocalDate,
    startDayOfWeek: DayOfWeek = DayOfWeek.MONDAY,
): Int
```

Whole pages from `from` to `to`, counted between the two dates' *pages* — so two dates on the same
page are zero apart whatever days of the period they fall on. Negative when `to` precedes `from`.

| Parameter | Type | Default | Description |
|---|---|---|---|
| `from` | `LocalDate` | — | The earlier end. |
| `to` | `LocalDate` | — | The later end. |
| `startDayOfWeek` | `DayOfWeek` | `DayOfWeek.MONDAY` | Read by `Week` only; ignored otherwise. |

```kotlin
KalendarPageUnit.Month.pagesBetween(from = LocalDate(2026, 8, 31), to = LocalDate(2026, 9, 1)) // 1
```

## `KalendarPager`

```kotlin
@Immutable
public class KalendarPager
```

Everything a `HorizontalPager`, a `LazyRow`, a `ViewPager` or a hand-rolled gesture surface needs to
page a calendar, with none of them in it. The constructor is private — build one with
[`endless`](#endless) or [`bounded`](#bounded).

Page indices are non-negative and run `0 until pageCount`, because that is what every scroll container
in the ecosystem expects. Which index the calendar's origin lands on is the whole difference between
the two factories.

### Properties

| Property | Type | Description |
|---|---|---|
| `unit` | `KalendarPageUnit` | How much of the calendar one page shows. |
| `startDayOfWeek` | `DayOfWeek` | Which weekday a week page begins on. Read when `unit` is `Week`; otherwise carried for the caller's own use. |
| `pageCount` | `Int` | How many pages the axis has — what a scroll container should be told. |
| `minDate` | `LocalDate?` | The earliest date reachable, or `null` for no lower bound. The page *containing* it is reachable, so a mid-month bound still allows that whole month. |
| `maxDate` | `LocalDate?` | The latest date reachable, or `null` for no upper bound. |
| `firstPage` | `Int` | The lowest page a caller may scroll to: `minDate`'s page when one is set, otherwise `0`. |
| `lastPage` | `Int` | The highest page a caller may scroll to: `maxDate`'s page when one is set, otherwise `pageCount - 1`. |
| `initialPage` | `Int` | The page a container should open on — the page containing the `initialDate` this pager was built with, clamped into `firstPage..lastPage`. |

```kotlin
val canGoBack = currentPage > pager.firstPage
```

### Functions

#### `dateAt`

```kotlin
public fun dateAt(page: Int): LocalDate
```

The date keying `page` — the first day of the day/week/month/year that page shows.

Out-of-range indices are **not** rejected; the arithmetic simply continues past the bounds, which
keeps a scroll container's overscroll and prefetch queries harmless.

| Parameter | Type | Default | Description |
|---|---|---|---|
| `page` | `Int` | — | The page index being resolved. |

```kotlin
val monthStart = pager.dateAt(page)
val grid = monthGridDates(monthStart, pager.startDayOfWeek)
```

#### `pageOf`

```kotlin
public fun pageOf(date: LocalDate): Int
```

The page containing `date`, clamped into `firstPage..lastPage`.

Clamping rather than failing is what makes this safe to call with a date a user typed, a "jump to
today" that falls outside a bounded calendar, or a saved position from before the bounds changed:
the answer is always a page that exists.

| Parameter | Type | Default | Description |
|---|---|---|---|
| `date` | `LocalDate` | — | The date to scroll to. |

```kotlin
pagerState.animateScrollToPage(pager.pageOf(today))
```

#### `coercePage`

```kotlin
public fun coercePage(page: Int): Int
```

`page` pulled into `firstPage..lastPage` — what to apply to an index a caller computed themselves,
such as `currentPage - 1` for a "previous" button.

| Parameter | Type | Default | Description |
|---|---|---|---|
| `page` | `Int` | — | The index to clamp. |

```kotlin
pagerState.animateScrollToPage(pager.coercePage(pagerState.currentPage + 1))
```

### `CENTER_PAGE`

```kotlin
public const val CENTER_PAGE: Int = Int.MAX_VALUE / 2
```

The page an [`endless`](#endless) axis puts its origin on: the middle of the index space, leaving
about a billion pages of travel in each direction.

### `endless`

```kotlin
public fun endless(
    unit: KalendarPageUnit,
    initialDate: LocalDate,
    startDayOfWeek: DayOfWeek = DayOfWeek.MONDAY,
    minDate: LocalDate? = null,
    maxDate: LocalDate? = null,
): KalendarPager
```

A pager whose axis is always `Int.MAX_VALUE` pages long, with `initialDate`'s page sitting at
[`CENTER_PAGE`](#center-page).

This is what a swipeable pager wants: a container that must be told a page count up front, and that
should let the user swipe *backwards* from where they started. `minDate` and `maxDate` still narrow
`firstPage`/`lastPage`, so `pageOf` and `coercePage` keep every programmatic jump inside them.

| Parameter | Type | Default | Description |
|---|---|---|---|
| `unit` | `KalendarPageUnit` | — | How much of the calendar one page shows. |
| `initialDate` | `LocalDate` | — | The date whose page the axis is centred on and opens at. |
| `startDayOfWeek` | `DayOfWeek` | `DayOfWeek.MONDAY` | The first weekday of a week page. |
| `minDate` | `LocalDate?` | `null` | Lower bound for programmatic jumps. |
| `maxDate` | `LocalDate?` | `null` | Upper bound for programmatic jumps. |

```kotlin
val pager = KalendarPager.endless(
    unit = KalendarPageUnit.Week,
    initialDate = today,
    startDayOfWeek = DayOfWeek.MONDAY,
)
pager.dateAt(pager.initialPage) // the Monday of today's week
```

### `bounded`

```kotlin
public fun bounded(
    unit: KalendarPageUnit,
    initialDate: LocalDate,
    startDayOfWeek: DayOfWeek = DayOfWeek.MONDAY,
    minDate: LocalDate? = null,
    maxDate: LocalDate? = null,
): KalendarPager
```

A pager whose axis is exactly as long as `minDate`/`maxDate` allow, anchored so that page `0` is a
real page.

This is what a lazy list wants: an item count it can size a scrollbar from, and a page `0` that is
the first month rather than an arbitrary point a billion pages into nothing.

With **both** bounds set, `pageCount` is the exact number of pages between them. With **one** bound
the axis is still `Int.MAX_VALUE` long but anchored at that bound — at the start for a `minDate`, at
the end for a `maxDate` — so the bounded side is a real edge. With **neither**, it behaves like
`endless`.

| Parameter | Type | Default | Description |
|---|---|---|---|
| `unit` | `KalendarPageUnit` | — | How much of the calendar one page shows. |
| `initialDate` | `LocalDate` | — | The date whose page the axis opens at, clamped into the bounds. |
| `startDayOfWeek` | `DayOfWeek` | `DayOfWeek.MONDAY` | The first weekday of a week page. |
| `minDate` | `LocalDate?` | `null` | Lower bound. Anchors page `0` when set. |
| `maxDate` | `LocalDate?` | `null` | Upper bound. |

```kotlin
val pager = KalendarPager.bounded(
    unit = KalendarPageUnit.Month,
    initialDate = today,
    minDate = LocalDate(2026, 1, 1),
    maxDate = LocalDate(2026, 12, 31),
)
pager.pageCount // 12
pager.dateAt(0) // 2026-01-01
```

> **Note:** a caller who crosses their own bounds — a `maxDate` before the `minDate` — still gets an
> axis with one page on it, rather than an item count a lazy list would reject.

> **Warning:** neither form blocks a *swipe*. The bounds clamp `pageOf` and `coercePage`, so buttons
> and programmatic jumps respect them; a scroll container owns its own gesture, and stopping it at
> `firstPage`/`lastPage` is the caller's job.
