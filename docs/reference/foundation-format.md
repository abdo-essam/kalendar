# `foundation.format`

`com.himanshoe.kalendar.foundation.format`

The fallback labels a calendar draws when its caller supplies no formatter of its own, and the hooks
to replace each of them.

```kotlin
import com.himanshoe.kalendar.foundation.format.KalendarFormatters
```

> **`kotlinx-datetime` carries no locale data, so every default here is plain English.** They exist
> so a calendar renders something readable out of the box. For anything localized, pass your own
> function backed by a platform date API — `java.time`, `NSDateFormatter`, `Intl` — through the
> matching [`KalendarViewConfig`](kalendar-views.md) formatter.

## `KalendarFormatters`

```kotlin
public object KalendarFormatters
```

Five function-valued properties, one per kind of label. Each is a separate hook precisely so a caller
can replace one without inheriting a bad default for the others.

| Property | Type | Returns | Example |
|---|---|---|---|
| `monthName` | `(Month) -> String` | The month's plain English name. | `KalendarFormatters.monthName(Month.AUGUST)` → `"August"` |
| `shortMonthName` | `(Month) -> String` | The English month name's first three letters. | `KalendarFormatters.shortMonthName(Month.AUGUST)` → `"Aug"` |
| `dayOfWeekName` | `(DayOfWeek) -> String` | The English day name's first three letters. | `KalendarFormatters.dayOfWeekName(DayOfWeek.MONDAY)` → `"Mon"` |
| `dayOfWeekLabel` | `(DayOfWeek) -> String` | The day name's first letter — the narrowest a column header gets. | `KalendarFormatters.dayOfWeekLabel(DayOfWeek.MONDAY)` → `"M"` |
| `hourLabel` | `(Int) -> String` | An hour of the day as 24-hour `"HH:00"`. | `KalendarFormatters.hourLabel(9)` → `"09:00"` |

```kotlin
daysOfWeekStartingAt(DayOfWeek.MONDAY).forEach { day ->
    ColumnHeader(text = KalendarFormatters.dayOfWeekLabel(day))
}
```

> **Why `shortMonthName` is its own hook rather than `monthName(…).take(3)`.** Truncating is only
> safe because the name is known to be English. Blind truncation turns Finnish "toukokuu" into "tou"
> and can cut a Devanagari name after a virama — so abbreviating is a language-specific decision, and
> the API makes you take it.

Supply your own for 12-hour clocks:

```kotlin
KalendarViewConfig(hourLabelFormatter = { hour -> twelveHour(hour) })
```

## `monthTitle`

```kotlin
public fun monthTitle(
    monthStart: LocalDate,
    monthNameFormatter: (Month) -> String = KalendarFormatters.monthName,
): String
```

A month page's title: the month's name followed by its year. The **day** of `monthStart` is ignored,
so any date in the month gives the same title — which is what makes it safe to call with whatever
date a pager hands you.

| Parameter | Type | Default | Description |
|---|---|---|---|
| `monthStart` | `LocalDate` | — | Any date in the month being titled. |
| `monthNameFormatter` | `(Month) -> String` | `KalendarFormatters.monthName` | How to name the month. |

```kotlin
monthTitle(LocalDate(2026, 8, 15))                    // "August 2026"
monthTitle(LocalDate(2026, 8, 1), localizedMonthName) // whatever your formatter returns
```
