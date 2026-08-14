# `foundation.datetime`

`com.himanshoe.kalendar.foundation.datetime`

The date arithmetic every calendar needs and `kotlinx-datetime` does not ship: which date starts a
period, how many whole periods lie between two dates, the inclusive span between two dates, and the
one seam between calendar logic and the wall clock.

```kotlin
import com.himanshoe.kalendar.foundation.datetime.startOfWeek
```

## Constants

### `DAYS_PER_WEEK`

```kotlin
public const val DAYS_PER_WEEK: Int = 7
```

Days in one grid row, and so how far one row of a calendar moves in date terms.

```kotlin
val nextRow = date.plus(DAYS_PER_WEEK, DateTimeUnit.DAY)
```

### `MONTHS_PER_YEAR`

```kotlin
public const val MONTHS_PER_YEAR: Int = 12
```

Months in one year — the conversion [`monthsBetween`](#monthsbetween) uses to turn a year difference
into months.

```kotlin
val monthsInADecade = 10 * MONTHS_PER_YEAR
```

## Period starts

Each of these answers "what date keys the period containing this one?". Two dates in the same period
always give the same answer, which is what makes the result safe to use as a page identity.

### `LocalDate.startOfWeek`

```kotlin
public fun LocalDate.startOfWeek(startDayOfWeek: DayOfWeek): LocalDate
```

The first day of the week containing this date, given the weekday that week starts on. This is the
date a week page is keyed by, and the date a month grid's first row is aligned to.

| Parameter | Type | Default | Description |
|---|---|---|---|
| `startDayOfWeek` | `DayOfWeek` | — | Which weekday the week begins on. |

```kotlin
val wednesday = LocalDate(2026, 6, 10)
wednesday.startOfWeek(DayOfWeek.MONDAY) // 2026-06-08
wednesday.startOfWeek(DayOfWeek.SUNDAY) // 2026-06-07
```

### `LocalDate.startOfMonth`

```kotlin
public fun LocalDate.startOfMonth(): LocalDate
```

The first day of this date's month — the date a month page is keyed by.

```kotlin
LocalDate(2026, 6, 15).startOfMonth() // 2026-06-01
```

### `LocalDate.startOfYear`

```kotlin
public fun LocalDate.startOfYear(): LocalDate
```

The first day of this date's year — the date a year page is keyed by.

```kotlin
LocalDate(2026, 6, 15).startOfYear() // 2026-01-01
```

## Whole-period differences

All three count between the two dates' *period starts*, so two dates inside the same period are zero
apart whatever days they fall on. All three are negative when `to` precedes `from`. This is what
`KalendarPager` measures page distance with.

### `weeksBetween`

```kotlin
public fun weeksBetween(from: LocalDate, to: LocalDate, startDayOfWeek: DayOfWeek): Int
```

| Parameter | Type | Default | Description |
|---|---|---|---|
| `from` | `LocalDate` | — | The earlier end of the span. |
| `to` | `LocalDate` | — | The later end. |
| `startDayOfWeek` | `DayOfWeek` | — | Which weekday a week begins on — the boundary being counted across. |

```kotlin
val monday = LocalDate(2026, 6, 8)
weeksBetween(from = monday, to = LocalDate(2026, 6, 14), startDayOfWeek = DayOfWeek.MONDAY) // 0
weeksBetween(from = monday, to = LocalDate(2026, 6, 15), startDayOfWeek = DayOfWeek.MONDAY) // 1
```

### `monthsBetween`

```kotlin
public fun monthsBetween(from: LocalDate, to: LocalDate): Int
```

| Parameter | Type | Default | Description |
|---|---|---|---|
| `from` | `LocalDate` | — | The earlier end of the span. |
| `to` | `LocalDate` | — | The later end. |

```kotlin
monthsBetween(from = LocalDate(2026, 6, 15), to = LocalDate(2026, 7, 1)) // 1
monthsBetween(from = LocalDate(2026, 11, 1), to = LocalDate(2027, 2, 1)) // 3
```

### `yearsBetween`

```kotlin
public fun yearsBetween(from: LocalDate, to: LocalDate): Int
```

A plain difference of calendar years.

| Parameter | Type | Default | Description |
|---|---|---|---|
| `from` | `LocalDate` | — | The earlier end of the span. |
| `to` | `LocalDate` | — | The later end. |

```kotlin
yearsBetween(from = LocalDate(2026, 6, 1), to = LocalDate(2027, 1, 1)) // 1
```

## Date ranges

### `LocalDate.datesUntil`

```kotlin
public fun LocalDate.datesUntil(other: LocalDate): Set<LocalDate>
```

Every date from the earlier of `this`/`other` to the later, **inclusive of both** — the set a
two-tap or press-and-drag range selection highlights.

Order-independent: `a.datesUntil(b)` and `b.datesUntil(a)` are the same set, so a caller never has
to sort the two ends of a range first. This is what [`KalendarSelection`](foundation-selection.md)
builds a range from.

| Parameter | Type | Default | Description |
|---|---|---|---|
| `other` | `LocalDate` | — | The other end of the span. May be before or after the receiver. |

```kotlin
val span = LocalDate(2026, 6, 1).datesUntil(LocalDate(2026, 6, 3))
// [2026-06-01, 2026-06-02, 2026-06-03]
val isSelected = LocalDate(2026, 6, 2) in span // true
```

## The clock

### `KalendarTimeSource`

```kotlin
@Immutable
public class KalendarTimeSource(
    public val timeZone: TimeZone = TimeZone.currentSystemDefault(),
    public val clock: Clock = Clock.System,
)
```

Where a calendar reads "now" from — the one seam between calendar logic and the wall clock.

Resolving today through a time source rather than calling `Clock.System` directly makes two
otherwise impossible things possible. **Deterministic tests:** hand it a fixed `Clock` and the
calendar's idea of today stops moving, so an assertion about the highlighted cell — or a screenshot
of it — holds forever. **A calendar that survives midnight:** a caller that re-reads `today()` when
the day changes moves its highlight to the new day instead of keeping yesterday's.

| Parameter | Type | Default | Description |
|---|---|---|---|
| `timeZone` | `TimeZone` | the device's current zone | The zone dates are resolved in. Read once per instance. |
| `clock` | `Clock` | `Clock.System` | The instant source. |

| Member | Returns | Description |
|---|---|---|
| `timeZone` | `TimeZone` | The zone this source resolves dates in. |
| `clock` | `Clock` | The instant source this was built with. |
| `today()` | `LocalDate` | The current date in `timeZone`. |
| `now()` | `LocalDateTime` | The current date *and time* in `timeZone`. |
| `copy(timeZone, clock)` | `KalendarTimeSource` | A duplicate with only the values passed here replaced. |

```kotlin
val system = KalendarTimeSource()
val startOfThisWeek = system.today().startOfWeek(DayOfWeek.MONDAY)

val fixed = KalendarTimeSource(
    timeZone = TimeZone.UTC,
    clock = object : Clock {
        override fun now(): Instant = Instant.parse("2026-03-10T09:00:00Z")
    },
)
fixed.today() // always 2026-03-10
```

> **Warning:** an instance handed to Compose UI must be stable across recompositions — `remember` it,
> or hold it in a view model. A fresh instance built inline every frame re-arms every rollover timer
> that depends on it.

In the views module this is provided through
[`ProvideKalendarTimeSource`](kalendar-state.md) and read through `rememberKalendarTimeSource()`.
