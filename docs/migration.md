# Migration from 1.x

Kalendar 2.0 replaces the `Kalendar(type = ...)` dispatcher with **standalone composables**. If you
were on 1.x, this page is the whole story.

> **Coming from a 2.0.0 release candidate instead?** The move you need is the package one, not this
> one: `com.himanshoe.kalendar.view` was retired and its contents redistributed across
> `com.himanshoe.kalendar`, `.state`, `.theme`, `.slot`, `.component` and the `kalendar-foundation`
> engine. Fifty-one top-level entities moved, none was renamed and none was removed, so it is a
> find-and-replace on import lines. The full old-name → new-name table is in the
> [changelog](changelog.md).

The short version: `KalendarType` and the `Kalendar` dispatcher are gone entirely, and every former
type has become a composable you call directly.

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
| `KalendarType.Agenda` | [`KalendarAgenda`](views/agenda.md) |

> **Note:** the arrow-navigation and swipe variants collapsed into one composable each. In 2.0 the
> header's arrow buttons and a horizontal swipe drive the same `KalendarViewState`, so both input
> methods always work and can never desync. There is no longer a choice to make.

There is **no `KalendarType`-based drop-in replacement**, and no `Kalendar` composable left to pass
one to. Call the view directly:

```kotlin
// 1.x
Kalendar(type = KalendarType.Agenda, events = events, config = KalendarConfig())

// 2.0
KalendarAgenda(events = events, config = KalendarViewConfig())
```

`KalendarAgenda` is not a survivor bolted onto the new package — it was rewritten against it. It
draws entirely with Compose Foundation, styles through [`KalendarTheme`](theming.md) like every other
view, takes `KalendarViewConfig`, and has content slots for its date headers, its rows and its empty
state. See [KalendarAgenda](views/agenda.md).

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

`KalendarConfig` itself no longer exists — it was deleted from `kalendar-foundation` in 2.0, along
with `KalendarDayConfig`, `KalendarDayLabelConfig`, `KalendarHeaderConfig`, `OnDaySelectionAction`,
`LocalDate.onDayClick`, `KalendarSelectedDayRange`, and the `KalendarColor` sealed class with its
`asSolidColor()` / `asGradientColor()` extensions. Its settings were split three ways, by what each
one actually governs:

| 1.x `KalendarConfig` field | 2.0 home |
|---|---|
| `startDayOfWeek`, `firstVisibleDate`, `minDate`, `maxDate` | The `rememberKalendar*State` factory — see [Views](views/index.md) |
| `disabledDates`, `showArrows`, `showDayLabel` | [`KalendarViewConfig`](configuration.md) (`showArrows` is now `showNavigationArrows`) |
| `initialSelectedDates`, `initialSelectedRange` | `selectedDates` on the view, or [`rememberKalendarSelectionState`](getting-started.md) |
| `dayConfig`, `dayLabelConfig`, `headerConfig`, `backgroundColor`, `calendarColors` | [`KalendarTheme`](theming.md) tokens |
| `onVisibleRangeChange`, `showWeekNumbers` | Nothing — neither was ever read |

What is left in `kalendar-foundation` is the event model and nothing else: `KalendarEvent`,
`BasicKalendarEvent`, `KalendarEvents`. The artifact is **2.0.0**, up from 1.1.0, so a build that
pins it explicitly needs its version bumped as well as `kalendar`'s.

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
| **`kalendar-foundation` as a public headless engine** — date, paging, grid, overlap and selection arithmetic with no UI | [The headless engine](engine.md) |
| [`KalendarTimeline`](views/timeline.md) — continuous vertical month scroll | New view |
| [`KalendarSchedule`](views/schedule.md) — hourly day grid | New view |
| [`KalendarScheduleWeek`](views/schedule-week.md) — hourly 7-day grid | New view |
| `KalendarResourceView` — hourly grid with one lane per room, chair or vehicle | [Views](reference/kalendar-views.md) |
| `KalendarDatePicker` — a compact single-month picker for a form field | [Views](reference/kalendar-views.md) |
| [`KalendarAgenda`](views/agenda.md) — themed, slot-based event list | Rewritten view |
| `KalendarEvent.id` — a stable key for list state | [Events](events.md) |
| Multi-day events via `KalendarEvent.endDate` | [Events](events.md) |
| Per-event indicator dots with a `+N` overflow | [Events](events.md) |
| Drag to select a range, drag to reschedule, drag/resize event blocks | [Events](events.md) |
| Month/year jump picker in the header | [Configuration](configuration.md) |
| Accessibility semantics on day cells, and `Modifier.kalendarDaySemantics()` | [Customization](customization.md) |
| Content slots for the header, day cells, and every part of the Schedule grid | [Customization](customization.md) |
| Locale hooks for month, weekday, and hour formatting | [Localization](localization.md) |
| Work weeks (`visibleDaysOfWeek`) and business hours (`scheduleVisibleHours`) | [Configuration](configuration.md) |
| Sweep out a new event on the hour grids (`onEventCreate`) | [Events](events.md) |
| [`kalendar-sync`](sync.md) — device calendar read/write, recurrence, iCal | New module |

The full history — and the exhaustive old-FQN → new-FQN table — is in the
[changelog](changelog.md). Every public symbol in both modules is documented in the
[API reference](reference/index.md).
