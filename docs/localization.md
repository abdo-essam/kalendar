# Localization

Kalendar has **no resource system of its own**. It is a Compose Multiplatform library and cannot
reach into your app's strings, so localization works by handing it values you have already resolved.

There are two places to hand them over, split by what they are:

| What | Where | Why |
|---|---|---|
| Fixed words and accessibility announcements | [`KalendarStrings`](#kalendarstrings), provided through `KalendarTheme` | They are theme tokens — one set for a whole subtree. |
| Date, month, and hour **formatting** | [`KalendarViewConfig`](#date-formatting) | It is per-view formatting, not fixed text. |

## Why you need this at all

`kotlinx-datetime` has no locale-aware formatting. Left alone, Kalendar shows plain English:

| Element | Default |
|---|---|
| Month names | `"August"` — from the enum name, title-cased |
| Day-of-week column labels | `"M"` — the day name's first letter |
| Hour-gutter labels | `"08:00"` — 24-hour |
| Day cell announcement | `"August 12, 2026"` — US order |

Every one of those is a hook. Fill them from a platform date API and the calendar is localized.

## `KalendarStrings`

Provided as a token, so it reaches every view inside the theme:

```kotlin
import com.himanshoe.kalendar.theme.KalendarStrings
import com.himanshoe.kalendar.theme.KalendarTheme

KalendarTheme(
    strings = KalendarStrings(
        previousPage = stringResource(R.string.kalendar_previous),
        nextPage = stringResource(R.string.kalendar_next),
        today = stringResource(R.string.kalendar_today),
        previousYear = stringResource(R.string.kalendar_previous_year),
        nextYear = stringResource(R.string.kalendar_next_year),
        openJumpPicker = stringResource(R.string.kalendar_open_picker),
        todaySuffix = stringResource(R.string.kalendar_today_suffix),
        hasEventsSuffix = stringResource(R.string.kalendar_has_events_suffix),
    ),
) {
    KalendarMonth(selectedDate = today)
}
```

### Every field

| Field | Type | Default | Used for |
|---|---|---|---|
| `previousPage` | `String` | `"Previous"` | Accessibility label for the header's previous-page arrow. |
| `nextPage` | `String` | `"Next"` | Accessibility label for the header's next-page arrow. |
| `today` | `String` | `"Today"` | Accessibility label for the today button. |
| `previousYear` | `String` | `"Previous year"` | Accessibility label for the jump picker's previous-year button. |
| `nextYear` | `String` | `"Next year"` | Accessibility label for the jump picker's next-year button. |
| `openJumpPicker` | `String` | `"Choose month and year"` | Accessibility label for the header title when tapping it opens the jump picker. |
| `todaySuffix` | `String` | `", today"` | Appended to a day cell's announcement when the date is today. **Include the leading separator.** |
| `hasEventsSuffix` | `String` | `", has events"` | Appended when the date has at least one event. **Include the leading separator.** |
| `agendaEmptyState` | `String` | `"No events"` | Shown in place of the list when `KalendarAgenda` is given no events. |
| `resourceEmptyState` | `String` | `"No resources"` | Shown in place of the grid when `KalendarResourceView` is given no lanes. |
| `showAllDayEvents` | `String` | `"Show all events"` | Accessibility label for the control that expands a truncated all-day row. |
| `eventOverflowLabel` | `(hiddenEventCount: Int) -> String` | `{ "+$it" }` | The compact label when a day has more events than `KalendarViewConfig.eventIndicatorCap`. |
| `dayAccessibilityLabel` | `(date: LocalDate, monthName: String) -> String` | `"$monthName ${date.day}, ${date.year}"` | Builds a day cell's base announcement. `todaySuffix` and `hasEventsSuffix` are appended to whatever this returns. |
| `dayOfWeekAccessibilityLabel` | `(DayOfWeek) -> String` | `"Monday"` | The full day name announced for a column header. The header *shows* `dayOfWeekLabelFormatter`'s short form — a bare "M" reads as an initial, not a weekday — so this supplies the name behind it. |
| `eventAccessibilityLabel` | `(event, start: LocalDateTime, end: LocalDateTime) -> String` | `"Design review, 09:30 to 11:00"` | What a screen reader announces for a timed event block. Override for a localized time format. Applied by `KalendarScheduleDefaults.EventBlock` — a replacement `eventContent` slot draws its own semantics. |
| `resourceEventAccessibilityLabel` | `(event, start, end, resourceTitle: String) -> String` | `"Design review, 09:30 to 11:00, Room A"` | The same, for a block in `KalendarResourceView` — it adds the lane, since a time alone does not say which room. Applied by `KalendarResourceDefaults.EventBlock`. |
| `allDayEventAccessibilityLabel` | `(event) -> String` | `"Release day, all day"` | The same, for an all-day chip. |

`KalendarStrings` is fully defaulted, so overriding one field is a one-liner — but inside a nested
theme, copy the accessor so you do not reset the rest:

```kotlin
KalendarTheme(strings = KalendarTheme.strings.copy(today = "Heute")) {
    KalendarMonth(selectedDate = today)
}
```

### Reordering the day announcement

`dayAccessibilityLabel` receives the date and its **already-formatted** month name, so it composes
with `monthNameFormatter` rather than duplicating it. Override it for locales that order or
punctuate dates differently:

```kotlin
KalendarTheme(
    strings = KalendarStrings(
        // "12. August 2026"
        dayAccessibilityLabel = { date, monthName -> "${date.day}. $monthName ${date.year}" },
        todaySuffix = ", heute",
        hasEventsSuffix = ", hat Termine",
    ),
) {
    KalendarMonth(
        selectedDate = today,
        config = KalendarViewConfig(monthNameFormatter = ::germanMonthName),
    )
}
```

The final announcement for a day that is today and has events would be
`"12. August 2026, heute, hat Termine"`.

### Pluralizing the overflow label

`eventOverflowLabel` is a lambda, so it can consult your plural resources:

```kotlin
KalendarTheme(
    strings = KalendarTheme.strings.copy(
        eventOverflowLabel = { count -> pluralStringResource(R.plurals.more_events, count, count) },
    ),
) {
    KalendarMonth(selectedDate = today, events = events)
}
```

> **Note:** the label is drawn in `KalendarTypography.eventOverflowLabel`, which defaults to
> **7.sp**. It sits inside a day cell next to the indicator dots, so there is room for very little —
> keep it to a few characters.

## Date formatting

The five formatters live on [`KalendarViewConfig`](configuration.md), because they are per-view
formatting rather than fixed text. Each one's plain-English fallback is a property of
[`KalendarFormatters`](reference/foundation-format.md) in the engine, so a calendar built directly on
`kalendar-foundation` has the same hooks.

| Formatter | Type | Default | Used for |
|---|---|---|---|
| `monthNameFormatter` | `(Month) -> String` | `"August"` | Header titles, `KalendarYear`'s month labels, the Timeline's month titles, `KalendarAgenda`'s date headers, the jump picker, and each day cell's accessibility description. |
| `shortMonthNameFormatter` | `(Month) -> String` | `"Aug"` | Week and day titles (`"Aug 10–16, 2026"`) and the jump picker's fixed-width month buttons. Its own hook rather than a `take(3)` on `monthNameFormatter`, because abbreviating is language-specific: truncating blindly turns Finnish `"toukokuu"` into `"tou"` and can cut a Devanagari name after a virama. |
| `dayOfWeekNameFormatter` | `(DayOfWeek) -> String` | `"Tue"` | The abbreviated day name in `KalendarSchedule`'s day title (`"Tue, Aug 11, 2026"`). |
| `dayOfWeekLabelFormatter` | `(DayOfWeek) -> String` | `"M"` | The day-of-week column headers, and `KalendarScheduleWeek`'s day-column labels. |
| `hourLabelFormatter` | `(Int) -> String` | `"08:00"` | The hour-gutter labels on `KalendarSchedule`, `KalendarScheduleWeek` and `KalendarResourceView`. Given the **absolute** hour, so it is unaffected by `scheduleVisibleHours`. |

```kotlin
KalendarMonth(
    selectedDate = today,
    config = KalendarViewConfig(
        monthNameFormatter = ::localizedMonthName,
        dayOfWeekLabelFormatter = ::localizedDayInitial,
    ),
)
```

> **Note:** nothing truncates your formatter's output. Where a full month name will not fit — the
> week and day titles, and the jump picker's fixed-width month buttons — the view calls
> `shortMonthNameFormatter` instead, and `dayOfWeekNameFormatter` for the abbreviated weekday in
> `KalendarSchedule`'s day title. Abbreviating is language-specific: a blind `take(3)` turns Finnish
> `"toukokuu"` into `"tou"` and can cut a Devanagari name after a virama, so supply the short forms
> yourself rather than letting the full ones be cut.

```kotlin
KalendarViewConfig(
    monthNameFormatter = ::localizedMonthName,
    shortMonthNameFormatter = ::localizedShortMonthName,
    dayOfWeekNameFormatter = ::localizedDayName,
    dayOfWeekLabelFormatter = ::localizedDayInitial,
)
```

Every platform date API has a short form beside its full one — `TextStyle.SHORT` on `java.time`,
`NSDateFormatter.shortMonthSymbols` on iOS, `{ month: "short" }` on `Intl` — so this is one more line
per formatter, not a new problem to solve.

### Per-platform implementations

`expect`/`actual` is the cleanest way to reach each platform's date API from `commonMain`:

```kotlin
// commonMain
expect fun localizedMonthName(month: Month): String
```

On **Android** and **Desktop (JVM)**, use `java.time`:

```kotlin
import kotlinx.datetime.Month
import kotlinx.datetime.number
import java.time.format.TextStyle
import java.util.Locale

actual fun localizedMonthName(month: Month): String =
    java.time.Month.of(month.number).getDisplayName(TextStyle.FULL, Locale.getDefault())
```

On **iOS**, use `NSDateFormatter`'s symbol arrays:

```kotlin
import kotlinx.datetime.Month
import kotlinx.datetime.number
import platform.Foundation.NSDateFormatter

actual fun localizedMonthName(month: Month): String =
    NSDateFormatter().monthSymbols[month.number - 1] as String
```

On **wasmJs**, use the `Intl` API through a small JS interop function.

> **Note:** `number` is an **extension** property on `kotlinx.datetime.Month`, so it needs an
> explicit `import kotlinx.datetime.number`. It runs 1–12, which matches `java.time.Month.of` and
> gives a zero-based index into `NSDateFormatter`'s symbol arrays. `month.ordinal + 1` is an
> import-free equivalent.

### 12-hour schedule labels

`hourLabelFormatter` takes the hour as `0..23`:

```kotlin
fun twelveHourLabel(hour: Int): String = when {
    hour == 0 -> "12 AM"
    hour < 12 -> "$hour AM"
    hour == 12 -> "12 PM"
    else -> "${hour - 12} PM"
}

KalendarSchedule(
    config = KalendarViewConfig(hourLabelFormatter = ::twelveHourLabel),
)
```

For full styling control rather than just the text, replace the `hourLabel` slot instead — see
[Customization](customization.md).

## Week start day

Not a string, but part of localizing a calendar: which day a week starts on is set per view on the
`remember*State` factory, not on the config.

```kotlin
val state = rememberKalendarMonthState(
    initialDate = today,
    startDayOfWeek = DayOfWeek.SUNDAY,
)

KalendarMonth(selectedDate = today, state = state)
```

The default is `DayOfWeek.MONDAY`. Derive it from the platform locale the same way as the month
names if you want it to follow the device.

## Right-to-left layouts

The header's chevrons are drawn with Compose Foundation and mirror themselves under an RTL
`LayoutDirection`, so previous and next point the right way without any configuration. The grid is
built from standard Compose layouts and mirrors with them; nothing extra is required.

> **Warning:** if you replace the header's icons with your own — see
> [Customization](customization.md) — the built-in mirroring goes with them. Mirror your own glyphs
> for RTL, or use an auto-mirroring asset.

## Putting it together

A single app-wide wrapper is usually enough:

```kotlin
@Composable
fun LocalizedKalendarTheme(content: @Composable () -> Unit) {
    KalendarTheme(
        strings = KalendarStrings(
            previousPage = stringResource(R.string.kalendar_previous),
            nextPage = stringResource(R.string.kalendar_next),
            today = stringResource(R.string.kalendar_today),
            todaySuffix = stringResource(R.string.kalendar_today_suffix),
            hasEventsSuffix = stringResource(R.string.kalendar_has_events_suffix),
        ),
        content = content,
    )
}

val localizedConfig = KalendarViewConfig(
    monthNameFormatter = ::localizedMonthName,
    dayOfWeekLabelFormatter = ::localizedDayInitial,
    hourLabelFormatter = ::twelveHourLabel,
)
```

`KalendarStrings` flows down through the theme to every view; the config is passed per view, so hold
one instance and reuse it.
