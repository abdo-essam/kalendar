# API reference

Every public symbol in `kalendar-foundation` and `kalendar`, organised by package, with what it is,
what it is for, its parameters, and at least one usage line.

The symbol list is derived from the modules' binary-compatibility dumps —
`kalendar-foundation/api/*/kalendar-foundation.api` and `kalendar/api/*/kalendar.api` — so nothing
public is silently missing from these pages. The Android and desktop dumps are identical apart from
compiler-generated `ComposableSingletons` names, which are not API.

> This is the hand-written reference: it explains *why* a symbol exists and how the pieces fit.
> For generated signatures, KDoc rendering, and inherited members, the Dokka output is published
> alongside this site under `/api/kalendar` and `/api/kalendar-foundation`.

## `kalendar-foundation` — the engine

Headless calendar arithmetic. No widgets, no theme, no layout. See [The headless engine](../engine.md)
for the guided tour.

| Package | Page | Contents |
|---|---|---|
| `com.himanshoe.kalendar.foundation.event` | [Event](foundation-event.md) | `KalendarEvent`, `BasicKalendarEvent`, `KalendarEvents`, `byDateExpandingSpans` |
| `com.himanshoe.kalendar.foundation.datetime` | [Datetime](foundation-datetime.md) | Period starts, whole-period differences, `datesUntil`, `KalendarTimeSource` |
| `com.himanshoe.kalendar.foundation.grid` | [Grid](foundation-grid.md) | `weekDates`, `monthGridDates`, `monthDates`, visible-day stepping, column headers |
| `com.himanshoe.kalendar.foundation.paging` | [Paging](foundation-paging.md) | `KalendarPager`, `KalendarPageUnit` |
| `com.himanshoe.kalendar.foundation.schedule` | [Schedule](foundation-schedule.md) | Hour windows, overlap packing, placement, drag maths, resource lanes |
| `com.himanshoe.kalendar.foundation.selection` | [Selection](foundation-selection.md) | `KalendarSelection`, `KalendarSelectionMode` |
| `com.himanshoe.kalendar.foundation.format` | [Format](foundation-format.md) | `KalendarFormatters`, `monthTitle` |

## `kalendar` — the views

The batteries-included Compose calendar built on the engine.

| Package | Page | Contents |
|---|---|---|
| `com.himanshoe.kalendar` | [Views](kalendar-views.md) | The nine calendar composables, `KalendarViewConfig`, and the four `*Defaults` objects |
| `com.himanshoe.kalendar.state` | [State](kalendar-state.md) | `KalendarViewState`, `KalendarTimelineState`, `KalendarSelectionState`, the `remember*` factories, the clock |
| `com.himanshoe.kalendar.theme` | [Theme](kalendar-theme.md) | `KalendarTheme` and the six token sets |
| `com.himanshoe.kalendar.slot` | [Slot](kalendar-slot.md) | Every scope type handed to a content slot |
| `com.himanshoe.kalendar.component` | [Component](kalendar-component.md) | `KalendarDayCellDefaults`, `KalendarHeaderDefaults`, `Modifier.kalendarDaySemantics` |

> **The package `com.himanshoe.kalendar.view` no longer exists.** In 2.0.0 the views moved to
> `com.himanshoe.kalendar` and the engine moved to `com.himanshoe.kalendar.foundation.*`. See the
> [migration table](../changelog.md) for the full old-name → new-name mapping.

## Conventions used on these pages

- **`—` in a Default column** means the parameter is required.
- Extension functions are written with their receiver, e.g. `LocalDate.startOfWeek(…)`.
- Composables are `@Composable` unless the page says otherwise; `@Composable` getters are marked.
- Classes marked **`@Immutable`** carry hand-written `equals`, `hashCode`, `toString` and `copy`.
  They are not `data class`es, so they have no `componentN` and cannot be destructured. Value
  equality works exactly as you would expect, which is what lets Compose skip recomposition when a
  value is rebuilt from equal parts.
- Anything not listed on these pages is `internal` or `private` and not part of the compatible API.
