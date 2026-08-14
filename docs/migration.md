# Migration from 1.x

Kalendar 2.0 replaces the `Kalendar(type = ...)` dispatcher with **standalone composables**. If you
were on 1.x, this page is the whole story.

The short version: `KalendarType` is gone except for `Agenda`, and each former type has become a
composable you call directly.

## What happened to `KalendarType`

1.x selected a layout by passing a `KalendarType` to a single `Kalendar` composable. Those variants
were removed in 2.0 — along with the `kalendar-foundation` internals they depended on — because a
sealed type could not express the very different parameter sets each layout actually needed.

| 1.x | 2.0 |
|---|---|
| `KalendarType.Oceanic` (month grid, arrow navigation) | [`KalendarMonth`](views/month.md) |
| `KalendarType.Solaris` (swipeable month grid) | [`KalendarMonth`](views/month.md) |
| `KalendarType.Firey` (week row, arrow navigation) | [`KalendarWeek`](views/week.md) |
| `KalendarType.Aerial` (swipeable week row) | [`KalendarWeek`](views/week.md) |
| `KalendarType.Yearly` | [`KalendarYear`](views/year.md) |
| `KalendarType.Agenda` | Unchanged — still `Kalendar(type = KalendarType.Agenda)` |

> **Note:** the arrow-navigation and swipe variants collapsed into one composable each. In 2.0 the
> header's arrow buttons and a horizontal swipe drive the same `KalendarViewState`, so both input
> methods always work and can never desync. There is no longer a choice to make.

There is **no `KalendarType`-based drop-in replacement**. Call the composable directly.

## Rewriting a call

Before, in 1.x:

```kotlin
Kalendar(
    type = KalendarType.Solaris,
    events = myEvents,
    config = KalendarConfig(
        startDayOfWeek = DayOfWeek.SUNDAY,
        minDate = LocalDate(2020, 1, 1),
        maxDate = LocalDate(2030, 12, 31),
        disabledDates = { it.dayOfWeek == DayOfWeek.SUNDAY },
        showArrows = true,
    ),
)
```

After, in 2.0:

```kotlin
val state = rememberKalendarMonthState(
    initialDate = today,
    startDayOfWeek = DayOfWeek.SUNDAY,
    minDate = LocalDate(2020, 1, 1),
    maxDate = LocalDate(2030, 12, 31),
)

KalendarMonth(
    selectedDate = today,
    state = state,
    events = myEvents,
    config = KalendarViewConfig(
        disabledDates = { it.dayOfWeek == DayOfWeek.SUNDAY },
        showNavigationArrows = true,
    ),
)
```

The single 1.x `KalendarConfig` has been split three ways, by what each setting actually governs:

| 1.x `KalendarConfig` field | 2.0 home |
|---|---|
| `startDayOfWeek`, `firstVisibleDate`, `minDate`, `maxDate` | The `rememberKalendar*State` factory — see [Views](views/index.md) |
| `disabledDates`, `showArrows`, `showDayLabel` | [`KalendarViewConfig`](configuration.md) (`showArrows` is now `showNavigationArrows`) |
| `initialSelectedDates`, `initialSelectedRange` | `selectedDates` on the view, or [`rememberKalendarSelectionState`](getting-started.md) |
| `dayConfig`, `dayLabelConfig`, `headerConfig`, `backgroundColor`, `calendarColors` | [`KalendarTheme`](theming.md) tokens |

## Selection

1.x configured selection through `OnDaySelectionAction` and the `initialSelected*` config fields.
2.0 hoists it: `selectedDates` is a plain `Set<LocalDate>` you own, and `onDateClick` tells you what
was tapped.

```kotlin
var selected by remember { mutableStateOf(setOf(today)) }

KalendarMonth(
    selectedDate = today,
    selectedDates = selected,
    onDateClick = { date, _ -> selected = setOf(date) },
)
```

For the three behaviours `OnDaySelectionAction` used to cover, `rememberKalendarSelectionState`
implements the tap logic and additionally survives configuration changes and process death:

| 1.x | 2.0 |
|---|---|
| `OnDaySelectionAction.Single` | `KalendarSelectionMode.Single` |
| `OnDaySelectionAction.Multiple` | `KalendarSelectionMode.Multiple` |
| `OnDaySelectionAction.Range` | `KalendarSelectionMode.Range` |
| `KalendarSelectedDayRange` | A plain `Set<LocalDate>`, built by `LocalDate.datesUntil(other)` |

```kotlin
val selection = rememberKalendarSelectionState(mode = KalendarSelectionMode.Range)

KalendarMonth(
    selectedDate = today,
    selectedDates = selection.selectedDates,
    onDateClick = { date, _ -> selection.onDateClick(date) },
)
```

## Styling

1.x styled the calendar through `KalendarKonfig` / `KalendarDayKonfig` / `KalendarHeaderKonfig` /
`KalendarDayLabelKonfig` and the `KalendarColor` sealed class.

2.0 replaces all of that with a [token layer](theming.md): `KalendarColors`, `KalendarTypography`,
`KalendarShapes`, `KalendarDimensions`, `KalendarAnimations`, and `KalendarStrings`, provided by
`KalendarTheme { }` and read through the `KalendarTheme` object — the same shape as `MaterialTheme`.

```kotlin
KalendarTheme(
    colors = KalendarThemeDefaults.colors().copy(selectionBackground = BrandPurple),
) {
    KalendarMonth(selectedDate = today)
}
```

If your app uses Material 3, the defaults already derive from your colour scheme and you may not
need to write any of this. If it does not, pass `KalendarThemeDefaults.systemColors()` — see
[Theming](theming.md).

The one survivor is the container background: `KalendarViewConfig.background` takes a `Brush?` for
gradients. A plain colour belongs on `KalendarColors.background`, which follows light and dark mode.

## Other changes to know about

- **The library is Kotlin Multiplatform.** Android, JVM/Desktop, iOS, and wasmJs, rather than
  Android-only Jetpack Compose.
- **`kalendar` depends on `kalendar-foundation`** as an `api` dependency, so the event model is on
  your classpath from the one artifact. Add `kalendar-foundation` explicitly only if you want it
  alone.
- **Day-click callbacks carry events.** `onDateClick` receives `(LocalDate, List<KalendarEvent>)`,
  not just the date.
- **`explicitApi()` is on** across all three published modules, and
  `kotlinx-binary-compatibility-validator` guards the public surface in CI.

## New in 2.0

Things that had no 1.x equivalent at all, worth knowing exist:

| Feature | Where |
|---|---|
| [`KalendarTimeline`](views/timeline.md) — continuous vertical month scroll | New view |
| [`KalendarSchedule`](views/schedule.md) — hourly day grid | New view |
| [`KalendarScheduleWeek`](views/schedule-week.md) — hourly 7-day grid | New view |
| Multi-day events via `KalendarEvent.endDate` | [Events](events.md) |
| Per-event indicator dots with a `+N` overflow | [Events](events.md) |
| Drag to select a range, drag to reschedule, drag/resize event blocks | [Events](events.md) |
| Month/year jump picker in the header | [Configuration](configuration.md) |
| Accessibility semantics on day cells, and `Modifier.kalendarDaySemantics()` | [Customization](customization.md) |
| Content slots for the header, day cells, and every part of the Schedule grid | [Customization](customization.md) |
| Locale hooks for month, weekday, and hour formatting | [Localization](localization.md) |
| [`kalendar-sync`](sync.md) — device calendar read/write, recurrence, iCal | New module |

The full history is in the [changelog](changelog.md).
