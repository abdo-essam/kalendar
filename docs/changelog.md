# Changelog

All notable changes to Kalendar will be documented in this file. The format is
based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and this
project adheres to [Semantic Versioning](https://semver.org/).

This page mirrors `CHANGELOG.md` at the repository root, which is the canonical copy.

---

## [2.0.0]

**2.0.0 is a ground-up redesign. There is no compatibility guarantee with 1.x, and none with any
2.0.0 release candidate.** Every public type has moved, most have been rewritten, and the 1.x entry
point no longer exists. This is not a version you upgrade into by bumping a number — read the
migration table below, then [Migration from 1.x](https://hi-manshu.github.io/Kalendar/migration.html).

Two things define the release:

1. **`kalendar-foundation` is a public, documented headless calendar engine.** The date arithmetic,
   paging arithmetic, grid layout, overlap packing, drag maths and selection reduction that were
   sealed inside one flat internal package are now a supported API — enough to build a calendar
   Kalendar did not design, without depending on Kalendar's UI. Its public surface went from 42 lines
   of API dump to 334. See [The headless engine](https://hi-manshu.github.io/Kalendar/engine.html).
2. **The package `com.himanshoe.kalendar.view` no longer exists.** "View" is a Swing-and-Android-View
   word in a library whose entire selling point is Compose, and one flat 27-file package gave an IDE
   user nothing to navigate. The nine calendar composables sit at the package root, with `state`,
   `theme`, `slot` and `component` beside them.

Artifact coordinates at the time of writing: `com.himanshoe:kalendar:2.0.0-RC3`,
`com.himanshoe:kalendar-foundation:2.0.0`, `com.himanshoe:kalendar-sync:1.0.0`.

### Migration: every public symbol, old name to new

Derived from the two modules' binary-compatibility dumps — `kalendar/api/*/kalendar.api` and
`kalendar-foundation/api/*/kalendar-foundation.api` — before and after the move, so nothing public is
missing from it. **Fifty-one** top-level entities moved. Not one was renamed and not one was removed:
every row below is a package change, so in most codebases the migration is a find-and-replace on
import lines.

Members did not move independently of their owners — `KalendarViewState.visibleDate` is still
`visibleDate` — so the table lists top-level entities only. Every member of every one of them is
documented in the [API reference](https://hi-manshu.github.io/Kalendar/reference/index.html).

**Calendar composables, their config, and their `*Defaults` — now at the package root**, the way `material3` puts `Button` at its root.

| 1.x / RC | 2.0.0 |
|---|---|
| `com.himanshoe.kalendar.view.KalendarWeek` | `com.himanshoe.kalendar.KalendarWeek` |
| `com.himanshoe.kalendar.view.KalendarMonth` | `com.himanshoe.kalendar.KalendarMonth` |
| `com.himanshoe.kalendar.view.KalendarYear` | `com.himanshoe.kalendar.KalendarYear` |
| `com.himanshoe.kalendar.view.KalendarTimeline` | `com.himanshoe.kalendar.KalendarTimeline` |
| `com.himanshoe.kalendar.view.KalendarSchedule` | `com.himanshoe.kalendar.KalendarSchedule` |
| `com.himanshoe.kalendar.view.KalendarScheduleWeek` | `com.himanshoe.kalendar.KalendarScheduleWeek` |
| `com.himanshoe.kalendar.view.KalendarResourceView` | `com.himanshoe.kalendar.KalendarResourceView` |
| `com.himanshoe.kalendar.view.KalendarAgenda` | `com.himanshoe.kalendar.KalendarAgenda` |
| `com.himanshoe.kalendar.view.KalendarDatePicker` | `com.himanshoe.kalendar.KalendarDatePicker` |
| `com.himanshoe.kalendar.view.KalendarViewConfig` | `com.himanshoe.kalendar.KalendarViewConfig` |
| `com.himanshoe.kalendar.view.KalendarAgendaDefaults` | `com.himanshoe.kalendar.KalendarAgendaDefaults` |
| `com.himanshoe.kalendar.view.KalendarScheduleDefaults` | `com.himanshoe.kalendar.KalendarScheduleDefaults` |
| `com.himanshoe.kalendar.view.KalendarResourceDefaults` | `com.himanshoe.kalendar.KalendarResourceDefaults` |
| `com.himanshoe.kalendar.view.KalendarDatePickerDefaults` | `com.himanshoe.kalendar.KalendarDatePickerDefaults` |
| `com.himanshoe.kalendar.view.KalendarDatePickerDayScope` | `com.himanshoe.kalendar.KalendarDatePickerDayScope` |
| `com.himanshoe.kalendar.view.KalendarDatePickerActionScope` | `com.himanshoe.kalendar.KalendarDatePickerActionScope` |

**Navigation, selection and clock state → `com.himanshoe.kalendar.state`.**

| 1.x / RC | 2.0.0 |
|---|---|
| `com.himanshoe.kalendar.view.KalendarViewState` | `com.himanshoe.kalendar.state.KalendarViewState` |
| `com.himanshoe.kalendar.view.KalendarTimelineState` | `com.himanshoe.kalendar.state.KalendarTimelineState` |
| `com.himanshoe.kalendar.view.KalendarSelectionState` | `com.himanshoe.kalendar.state.KalendarSelectionState` |
| `com.himanshoe.kalendar.view.rememberKalendarWeekState` | `com.himanshoe.kalendar.state.rememberKalendarWeekState` |
| `com.himanshoe.kalendar.view.rememberKalendarMonthState` | `com.himanshoe.kalendar.state.rememberKalendarMonthState` |
| `com.himanshoe.kalendar.view.rememberKalendarYearState` | `com.himanshoe.kalendar.state.rememberKalendarYearState` |
| `com.himanshoe.kalendar.view.rememberKalendarScheduleState` | `com.himanshoe.kalendar.state.rememberKalendarScheduleState` |
| `com.himanshoe.kalendar.view.rememberKalendarTimelineState` | `com.himanshoe.kalendar.state.rememberKalendarTimelineState` |
| `com.himanshoe.kalendar.view.rememberKalendarSelectionState` | `com.himanshoe.kalendar.state.rememberKalendarSelectionState` |
| `com.himanshoe.kalendar.view.ProvideKalendarTimeSource` | `com.himanshoe.kalendar.state.ProvideKalendarTimeSource` |
| `com.himanshoe.kalendar.view.rememberKalendarTimeSource` | `com.himanshoe.kalendar.state.rememberKalendarTimeSource` |
| `com.himanshoe.kalendar.view.rememberKalendarToday` | `com.himanshoe.kalendar.state.rememberKalendarToday` |
| `com.himanshoe.kalendar.view.rememberKalendarNow` | `com.himanshoe.kalendar.state.rememberKalendarNow` |

**The theme and its six token sets → `com.himanshoe.kalendar.theme`.**

| 1.x / RC | 2.0.0 |
|---|---|
| `com.himanshoe.kalendar.view.KalendarTheme` | `com.himanshoe.kalendar.theme.KalendarTheme` |
| `com.himanshoe.kalendar.view.KalendarThemeDefaults` | `com.himanshoe.kalendar.theme.KalendarThemeDefaults` |
| `com.himanshoe.kalendar.view.KalendarThemeTokens` | `com.himanshoe.kalendar.theme.KalendarThemeTokens` |
| `com.himanshoe.kalendar.view.KalendarColors` | `com.himanshoe.kalendar.theme.KalendarColors` |
| `com.himanshoe.kalendar.view.KalendarTypography` | `com.himanshoe.kalendar.theme.KalendarTypography` |
| `com.himanshoe.kalendar.view.KalendarShapes` | `com.himanshoe.kalendar.theme.KalendarShapes` |
| `com.himanshoe.kalendar.view.KalendarDimensions` | `com.himanshoe.kalendar.theme.KalendarDimensions` |
| `com.himanshoe.kalendar.view.KalendarAnimations` | `com.himanshoe.kalendar.theme.KalendarAnimations` |
| `com.himanshoe.kalendar.view.KalendarStrings` | `com.himanshoe.kalendar.theme.KalendarStrings` |

**Content-slot scope types → `com.himanshoe.kalendar.slot`.**

| 1.x / RC | 2.0.0 |
|---|---|
| `com.himanshoe.kalendar.view.KalendarDayScope` | `com.himanshoe.kalendar.slot.KalendarDayScope` |
| `com.himanshoe.kalendar.view.KalendarDayOverflowScope` | `com.himanshoe.kalendar.slot.KalendarDayOverflowScope` |
| `com.himanshoe.kalendar.view.KalendarHeaderScope` | `com.himanshoe.kalendar.slot.KalendarHeaderScope` |
| `com.himanshoe.kalendar.view.KalendarScheduleEventScope` | `com.himanshoe.kalendar.slot.KalendarScheduleEventScope` |
| `com.himanshoe.kalendar.view.KalendarResourceEventScope` | `com.himanshoe.kalendar.slot.KalendarResourceEventScope` |

**Default renderings and the day-cell modifier → `com.himanshoe.kalendar.component`.**

| 1.x / RC | 2.0.0 |
|---|---|
| `com.himanshoe.kalendar.view.KalendarDayCellDefaults` | `com.himanshoe.kalendar.component.KalendarDayCellDefaults` |
| `com.himanshoe.kalendar.view.KalendarHeaderDefaults` | `com.himanshoe.kalendar.component.KalendarHeaderDefaults` |
| `com.himanshoe.kalendar.view.kalendarDaySemantics` | `com.himanshoe.kalendar.component.kalendarDaySemantics` |

**Types that turned out to be pure logic → `kalendar-foundation`.** These moved *module*, not just package. `kalendar` re-exports the engine as an `api` dependency, so a single-dependency consumer needs no build change.

| 1.x / RC | 2.0.0 |
|---|---|
| `com.himanshoe.kalendar.view.KalendarTimeSource` | `com.himanshoe.kalendar.foundation.datetime.KalendarTimeSource` |
| `com.himanshoe.kalendar.view.datesUntil` | `com.himanshoe.kalendar.foundation.datetime.datesUntil` |
| `com.himanshoe.kalendar.view.KalendarSelectionMode` | `com.himanshoe.kalendar.foundation.selection.KalendarSelectionMode` |
| `com.himanshoe.kalendar.view.KalendarHourWindow` | `com.himanshoe.kalendar.foundation.schedule.KalendarHourWindow` |
| `com.himanshoe.kalendar.view.KalendarResource` | `com.himanshoe.kalendar.foundation.schedule.KalendarResource` |

**Newly public, with no 1.x equivalent:** the whole of `com.himanshoe.kalendar.foundation.datetime`
(period starts, whole-period differences), `.grid` (`weekDates`, `monthGridDates`, `monthDates`,
`visibleDateOnOrAfter`, `visibleDateStep`, `daysOfWeekStartingAt`, `ALL_DAYS_OF_WEEK`), `.paging`
(`KalendarPager`, `KalendarPageUnit`), `.format` (`KalendarFormatters`, `monthTitle`), `.selection`
(`KalendarSelection`), most of `.schedule` (`KalendarScheduleBlock`, `scheduleBlocks`,
`KalendarBlockPlacement`, `blockPlacement`, the drag and create maths, the resource-lane functions,
and the four minute/hour constants), and `byDateExpandingSpans` on `.event`. None of these are
migrations — they were internal, and are now supported.


### Breaking changes
- **`Kalendar(type = ...)` and `KalendarType` are gone.** The last remaining type, `Agenda`, is now
  the top-level `com.himanshoe.kalendar.KalendarAgenda` composable, so every view in the
  library is called directly. There is no dispatch entry point left; replace
  `Kalendar(type = KalendarType.Agenda, events = events, config = KalendarConfig(...))` with
  `KalendarAgenda(events = events, config = KalendarViewConfig(...))`.
- **`KalendarAgenda` rewritten as a first-class modern view.** It is public, lives in
  `com.himanshoe.kalendar`, takes `KalendarViewConfig`, and draws entirely with Compose
  Foundation — no Material widgets, no hardcoded colours, shapes or text sizes. Every visual comes
  from `KalendarTheme`, so it restyles with the other views. New slots `dateHeader`,
  `eventContent` and `emptyState` default to `KalendarAgendaDefaults.DateHeader` / `.EventRow` /
  `.EmptyState`. Its date headers now go through `KalendarViewConfig.monthNameFormatter` (they used
  the raw English `Month` enum name and could not be localized), and its empty-state text comes
  from the new `KalendarStrings.agendaEmptyState`.
- **`kalendar-foundation` is now 2.0.0** (was 1.1.0 stable), aligning it with `kalendar` on one
  major. The following had zero read sites anywhere in the project and were deleted:
  `KalendarConfig`, `KalendarDayConfig`, `KalendarDayLabelConfig`, `KalendarHeaderConfig`,
  `OnDaySelectionAction`, `LocalDate.onDayClick`, `KalendarSelectedDayRange`, and the
  `KalendarColor` sealed class with its `Color.asSolidColor()` / `List<Color>.asGradientColor()`
  extensions. `KalendarConfig` in particular published 16 fields of which the Agenda view read
  exactly two; the rest were inert. What remains in `kalendar-foundation` is the event model:
  `KalendarEvent`, `BasicKalendarEvent`, `KalendarEvents`. Configure views with
  `KalendarViewConfig` and style them with `KalendarTheme`.
- Removed `KalendarType.Oceanic`, `Firey`, `Solaris`, `Aerial`, and `Yearly` (and the
  `kalendar-foundation` internals they depended on) entirely. Replace them with the standalone
  `com.himanshoe.kalendar` composables below — there is no `KalendarType`-based drop-in
  replacement; call `KalendarWeek`/`KalendarMonth`/`KalendarYear`/`KalendarTimeline` directly.
- `KalendarColors`, `KalendarTypography` and `KalendarShapes` each gained a required constructor
  parameter (`agendaCard`; `agendaEventTitle`/`agendaEventSubtitle`; `agendaCard`). Code that
  `copy()`s a defaults instance is unaffected; code that constructs one from scratch must supply
  them.

### Added
- **Multi-day events draw as one continuous bar, not as loose dots.** A three-day conference used to
  render as three dots on three days, which is exactly what three unrelated single-day events look
  like — the month grid had no way to say "this is one thing that lasts". An event with an `endDate`
  now draws a bar running edge to edge across every day it covers, breaking at the week boundary the
  way a calendar has to, so a Thursday-to-Tuesday event is two bars. The event's real start and end
  are inset and rounded; an end that is only the row running out stays square and flush, which is
  what distinguishes a conference that finished on Saturday from one that continues into Sunday.
  Overlapping spans pack into lanes by the same greedy interval sweep the schedule grid already uses
  for overlapping blocks, so a bar holds one height for its whole run instead of climbing a
  staircase. Five dimension tokens control it: `KalendarDimensions.spanBarHeight` (`4.dp`),
  `spanBarSpacing` (`2.dp`), `spanBarCornerRadius` (`2.dp`), `spanBarEndInset` (`3.dp`) and
  `spanBarMaxLanes` (`2`). **A span past the last lane is not hidden** — it falls back to the
  indicator dot it drew before and still reaches the `+N` overflow, so the cap costs continuity, not
  the event. `spanBarMaxLanes = 0` turns bars off entirely and restores the old rendering. The
  property this could not break, and did not: every day number in a grid sits on one baseline
  whatever its day holds — measured across the week of the 9th at 221, 222, 221, 221, 222, 222.
- **API invariants are enforced against the checked-in ABI dumps, not against memory.**
  `KalendarApiInvariantsTest` asserts four rules that were previously obeyed by discipline alone and
  are exactly the kind quietly broken by a one-line change that looks harmless. (1) **No `data class`
  reaches the public API** — a `data class` compiles one `componentN` per property plus a `copy`
  whose name carries a signature hash, so adding a property breaks every consumer at link time;
  `KalendarEvent` has gained two properties since 1.0, so this is live rather than hypothetical, and
  it is why eight public types hand-write `copy`/`equals`/`hashCode`/`toString`. (2) **No composable
  takes a `State<T>` or `MutableState<T>` parameter** — it makes a plain value impossible to pass and
  leaks a storage decision into a signature, and a `() -> T` buys the same deferral without the
  coupling. (3) **`kalendar-foundation`'s ABI references nothing from the UI module**, so the
  engine stays reachable without it. (4) **No compiler-generated `ComposableSingletons$*` lambda
  holders appear in a dump** — their accessors carry a hash of the lambda's source position and
  re-hash whenever an unrelated edit moves a lambda, which is how a real ABI change once went through
  review unnoticed; they are excluded via `ignoredClasses`, and this notices when a new one appears.
  Because `apiCheck` already guarantees the dumps match the source, asserting against the dumps is
  the same as asserting against the API, and costs nothing at runtime.
- **`KalendarEvent.id`** — an optional stable identifier (`null` by default, so existing
  implementations are unaffected). Views that keep per-item state use it as their list key, and it
  gives consumers a way to look an event back up instead of matching on name and date.
  `BasicKalendarEvent` gained a matching trailing `id` parameter.
- **Agenda theme tokens** — `KalendarColors.agendaCard`, `KalendarShapes.agendaCard`,
  `KalendarTypography.agendaEventTitle`/`agendaEventSubtitle`,
  `KalendarDimensions.agendaRowPadding`/`agendaRowSpacing`/`agendaRowGap`/`agendaDotSize`, and
  `KalendarStrings.agendaEmptyState`, so `KalendarAgenda` restyles and localizes like every other
  view.
- **Recurring events write to device calendars** — `insertEvent`/`updateEvent` now persist
  `recurrenceRule` on Android (RRULE + RFC 2445 DURATION — recurring rows must not set DTEND —
  plus EXDATE) and iOS (`EKRecurrenceRule` mapping, with `EKSpanFutureEvents` on rule updates so
  EventKit applies them); fetch round-trips the rule on both platforms. iOS cannot write
  `exceptionDates` (no EventKit EXDATE API) — documented.
- **Drag to move/resize Schedule blocks** — press-and-hold a block to drag it to a new time
  (bottom-edge drag resizes; horizontal drag on the week grid moves across days), snapped to a
  configurable step, with live preview and overlap re-packing; `onEventTimeChange(event, newStart,
  newEnd)` fires on release.
- **Month/year jump picker** — tapping the header title opens a year-stepper + month-grid popup
  that scrolls straight to the chosen month, on every header-bearing view.
- **Configurability pass** — `KalendarViewConfig` gains `showJumpPicker`, `hourLabelFormatter`
  (12h/24h gutter labels), `scheduleInitialScrollHour`, `scheduleDragSnapMinutes`, and
  `eventIndicatorCap`; `doc/Config.md` rewritten around the real `KalendarViewConfig` (it
  previously documented a deleted 1.x API).
- **Phone-framed demo site** — the wasm demo now renders the sample app inside a phone mockup on
  a gradient landing page (pure Compose: hero title, GitHub/Docs link chips, punch-hole bezel),
  falling back to full-bleed on narrow viewports.
- **`KalendarTheme` / `KalendarColors`** — every view colour now resolves through an ambient
  token set (selection, today/day/label content, event accent, now-indicator, grid lines, drag
  ghost). Material apps need no change (tokens derive from the ambient colour scheme); any app can
  restyle by wrapping views in `KalendarTheme(colors = ...)`.
- **`KalendarScheduleWeek`** — 7-day time grid paged by week: day columns share one hour gutter,
  overlap packing per column, day-header row with today emphasised, all-day chips prefixed with
  day-of-month, now-indicator on today's column.
- **Saveable selection** — `rememberKalendarSelectionState` now uses `rememberSaveable`, so
  selection (including an in-progress range) survives rotation and process death.
- **Golden-image tests** — desktop screenshot tests lock in the month grid (default and
  events/overflow) and the schedule day view; regenerate intentionally changed visuals with
  `./gradlew :kalendar:desktopTest -PupdateGoldens=true`.
- **Live demo deployment** — a `Deploy Demo` GitHub Actions workflow publishes the sample's
  wasm build to GitHub Pages on every push to `main`; README links the demo.
- **Calendar picker in `kalendar-sync`** — `KalendarSyncProvider.listCalendars()` returns the
  device's calendars (`KalendarSyncCalendar`), and `insertEvent` takes an optional `calendarId`
  to write into a specific calendar instead of the platform default.
- **Reminders in `kalendar-sync`** — `KalendarSyncEvent.reminders` (`KalendarReminder`, minutes
  before start) written/read on Android (`CalendarContract.Reminders`), iOS (`EKAlarm`), and the
  Desktop `.ics` provider; encoded as RFC 5545 `VALARM` blocks by the iCal codec.
- **Attendees in `kalendar-sync`** — `KalendarSyncEvent.attendees` (`KalendarAttendee`) on
  Android (`CalendarContract.Attendees`) and via `ATTENDEE` lines in the iCal codec. On iOS
  attendees are read-only (EventKit cannot add them programmatically): populated on fetch,
  ignored on write.
- **`KalendarSchedule`** — hourly time-grid day view paged by day: timed events as
  duration-sized blocks with side-by-side packing for overlaps, all-day chips above the grid, a
  minute-refreshed now-indicator on today, and an initial scroll to early morning.
- **Drag-to-reschedule** on `KalendarMonth` — press-and-hold a date with events and drag to
  another date; `onEventDrop` receives the events and the drop date, with a ghost highlight
  following the pointer. Composes with drag-to-select (event-less dates still range-select).
- **Per-event indicator dots** — day cells show one dot per event coloured by
  `KalendarEvent.eventColor` (theme-primary fallback), capped at three with a `+N` overflow label.
- **`KalendarWeek`, `KalendarMonth`, `KalendarYear`** — standalone composables backed by a shared
  `KalendarViewState`, navigable by both arrow-click and swipe (`HorizontalPager`) through the same
  state so the two input methods never desync.
- **`KalendarTimeline`** — a continuous, virtualized vertical scroll through months (Google
  Calendar-list style), optionally bounded by `minDate`/`maxDate` on `rememberKalendarTimelineState`,
  unbounded by default.
- **`KalendarViewConfig`** — shared visual/behavioural config (`disabledDates`,
  `showNavigationArrows`, `showAdjacentMonthDates`, `showTodayButton`, `background`) for all four
  views above.
- **Today button** — `KalendarWeek`/`Month`/`Year`'s header, and `KalendarTimeline`'s new sticky
  header, show a button that animates back to today's date. Enabled by default
  (`KalendarViewConfig.showTodayButton = true`).
- **Sticky header on `KalendarTimeline`** — the topmost visible month's title and the today button
  now stay pinned above the scrolling content instead of scrolling away with it.
- **Sliding selection indicator** on `KalendarWeek`/`KalendarMonth` — when exactly one date is
  selected and no custom `dayContent` is supplied, the highlight animates smoothly from the
  previous cell to the newly tapped one instead of each cell fading independently. Snaps instantly
  (no fly-in) the first time a grid page appears, and falls back to independent per-cell fades for
  multi-select/range.
- **`KalendarSelectionMode` + `rememberKalendarSelectionState`** — a hoisted-state helper
  implementing single/multiple/range tap behaviour for `selectedDates`/`onDateClick`, so common
  selection modes don't need to be hand-rolled.
- **Drag-to-select a range** on `KalendarWeek`/`KalendarMonth` — press and hold a date, then drag,
  to select a range live via the new `onDateRangeSelect`/`onDateRangeSelectEnd` parameters (paired
  with `KalendarSelectionState.onRangeDrag`/`onRangeDragEnd` for the common case). Requires a
  long-press before the drag starts, so a quick tap is unaffected.
- **Locale-aware formatters** — `KalendarViewConfig.monthNameFormatter`/`dayOfWeekLabelFormatter`
  let you plug in a platform locale API (`java.time`, `NSDateFormatter`, `Intl`, ...) for month
  names and day-of-week labels; kotlinx-datetime has no locale-aware formatting of its own, so both
  default to plain English.
- **`KalendarSyncEvent.exceptionDates`** — the RFC 5545 EXDATE equivalent for `kalendar-sync`:
  skips specific dates when a recurring event is expanded via `expandOccurrences`, and
  round-trips through `exportToIcs`/`importFromIcs` as an `EXDATE` line.
- **Accessibility semantics on day cells** — every day cell now exposes a screen-reader
  `contentDescription` (e.g. "August 12, 2026, today, has events") and a `selected` semantic state,
  and uses `Role.Button` so assistive tech announces it correctly.
- **`KalendarEvent.endDate`** — multi-day event spans. A day cell shows an event on every date from
  `date` through `endDate` inclusive; expansion is capped at 366 days to guard against runaway spans.
- Selection, press, and event-indicator animations (`animateColorAsState`, press-scale,
  `AnimatedVisibility`) and a title crossfade in the shared navigation header.
- **`kalendar-sync` module** — reads/writes the real device calendar with no third-party account:
  `CalendarContract` on Android, `EventKit` on iOS, a file-backed `.ics` provider on Desktop.
  Includes RFC 5545 recurrence (`KalendarRule`, `KalendarRecurrenceExpander`) and pure in-memory
  iCal encode/decode (`exportToIcs`/`importFromIcs`) available on every platform including wasmJS.
  `KalendarSyncEvent.endDate` carries multi-day spans through insert/update/fetch and iCal
  round-trips on every platform provider.
- Sample app now has a view switcher covering every view type (Week, Month, Year, Timeline, Schedule, Week grid, Agenda).
- `explicitApi()` mode enabled across all three published modules — every public declaration now
  requires an explicit visibility modifier and return type, guarding against accidental API leaks.
- `kotlinx-binary-compatibility-validator` wired into all three published modules and CI
  (`apiCheck`), so an accidental breaking change to the public API surface now fails the build.
- Root `.editorconfig` documenting this project's formatting conventions (4-space indent, 140-char
  line length, no forced trailing commas) for editors and the existing `detekt-formatting`
  (ktlint-based) ruleset to read.

### Fixed
- **`KalendarGrid` no longer writes snapshot state from the layout phase.** It tracked where each day
  cell landed in a `mutableStateMapOf` written from `onGloballyPositioned` and read from its own
  composable body to position the selection indicator — a cross-phase back-write that cost one whole
  extra composition of the grid per page composed. The map is a plain `remember { mutableMapOf() }`
  now, and no reader is in composition: the gesture code reads it from a pointer callback, the
  overlays from their layout lambdas, and the indicator's target is computed inside a `snapshotFlow`
  in the effect that drives its animator. The one thing a plain map gives up — telling a reader the
  cells have moved — comes back as a single `Int` revision bumped **only** on a placement that
  actually changed; bumping it on every callback would invalidate the placement that produced the
  callback and the grid would lay itself out forever. Measured on a March 2026 month: snapshot-map
  writes on a first frame **37 → 0**, grid-body compositions **2 → 1**, grid content recompositions
  during a six-move drag **7 → 0**. `layingOutAMonthGridWritesNoSnapshotMap` guards it by counting
  the cause rather than the symptom, since a lazy grid memoizes item content by key and the second
  composition walked no cells to observe. Two earlier claims about this were wrong and are corrected
  here: the writes did **not** recur on every layout pass (`SnapshotStateMap.put` of an equal value
  is a no-op, so the real cost was one extra whole composition per page, not per-frame churn), and
  there was never a "404 writes for a year" — `KalendarYear` composes through `KalendarStaticGrid`,
  which never tracked coordinates at all; 404 was the *cell* count of the old eager year column.
  The naive version of this fix moves the reads to the layout phase and silently stops the indicator
  animating, because the `LaunchedEffect` key is itself a composition read — the first-paint-flash
  and post-offset test-tag tests are what keep that honest.
- **`KalendarAgenda` no longer collides an event `id` with an item index.** Items were keyed
  `"event-$day-${event.id ?: index}"`, which put the id space and the index space in one space: an
  event carrying the `id` `"1"` beside an id-less event sitting at index 1 produced identical keys,
  and a `LazyColumn` throws on a duplicate rather than degrading, so the whole agenda crashed. The
  mix that triggers it is ordinary rather than exotic — events synced from a device arrive with ids
  and locally drafted ones do not, so any consumer doing both on one day hit it. The two fallbacks
  are namespaced apart now (`id-$id` against `at-$index`), which is sufficient because neither prefix
  can be produced by the other. Found by an adversarial review probe rather than by any existing
  test; the probe is kept as a regression test, with a second case one step along.
- **`KalendarAgenda` no longer crashes on duplicate events.** Its list keyed items on
  `"${date}_${eventName}_${startTime}"`, so two all-day events with the same name on one date
  produced identical keys and `SaveableStateHolder` threw `IllegalArgumentException` — reliably,
  since the stable sort placed the colliding pair adjacent. Items are now keyed on the new
  `KalendarEvent.id` (falling back to the event's position within its date) plus the date it is
  rendered under.
- On iOS 17+ the sync provider requested calendar access with the deprecated pre-17 API, which
  can grant only write access and break event fetching; it now calls
  `requestFullAccessToEventsWithCompletion` when available (runtime-gated), falling back to the
  legacy request on older iOS.
- Drag gesture handlers on the Schedule views and the month grid captured their first
  composition's state forever (a `pointerInput` coroutine launches once and never sees later
  lambdas): after the events list changed, the next drag moved/dropped events using stale blocks
  and stale callbacks. All gesture callbacks now read through `rememberUpdatedState`, and a
  regression UI test drags the same block twice across an events update.
- On `KalendarScheduleWeek`, dragging a block horizontally computed the day shift from the
  block's own width instead of the day column's width, so drags across days landed on the wrong
  day whenever events overlapped (blocks share a column side by side). Day shift is also now
  clamped to ±6 days.
- Dragging a block to the bottom of the day reported an end time of 23:59 on the same day instead
  of 00:00 on the next day, silently shortening the event by a minute.
- Drag snapping rounded twice (pixels → minutes → snap step), drifting up to half a step for snap
  steps that don't divide 60; it now rounds once, directly to the snap step.
- `KalendarTimeline` crashed (`IndexOutOfBoundsException`) or wrapped to a negative index when
  scrolled to the edge of a `minDate`/`maxDate`-bounded range, and when only one bound was set the
  index math could overflow `Int`. Index arithmetic is now done in `Long` and clamped.
- The header's previous-arrow disabled itself one page too early when `minDate` fell mid-period
  (e.g. a `minDate` of the 15th made its own month unreachable), and `animateScrollTo` with an
  out-of-bounds date scrolled past the configured `minDate`/`maxDate` instead of clamping to it.
- `KalendarRecurrenceExpander` with a `count`-limited rule invented occurrences when the query
  window started after the series began: the count was only consumed inside the window, so a
  `DAILY COUNT=3` series from January 1st still produced events in February. The count is now
  consumed from the series start.
- Recurring rules that combine `interval` with MONTHLY `byDay`, or YEARLY `byMonth`, never
  produced any occurrences (the expander stepped by whole months/years and missed the filter
  days). YEARLY `byMonth` also correctly pins the day-of-month from the series start per RFC 5545.
- All-day events written to Android used local-midnight timestamps instead of the UTC midnights
  `CalendarContract` requires, shifting them a day in negative-offset timezones; reads now convert
  back through UTC as well.
- Timed recurrence exceptions (`exceptionDates`) written to Android used wall-clock time with a
  `Z` suffix, so the platform matched no instance and the exclusions silently failed; EXDATE
  values now convert through the device timezone to real UTC instants (and parse back the same
  way).
- Fetching events on iOS excluded the final day of the requested range (predicate ended at the
  range end's start-of-day instead of the day after).
- The iCal codec stamped `Z` onto wall-clock times on export, silently shifting every timed event
  for consumers in non-UTC timezones; it now emits RFC 5545 floating times (legacy `Z` input
  still decodes).
- Expanded recurrence occurrences dropped their template's `exceptionDates`; `DURATION` values
  using weeks (`P1W`) failed to parse; day-based `VALARM` triggers (`-P1D`) were ignored; Android
  fetch hid recurring series that started before the query window; iOS all-day fetches reported
  an end date one day off.
- `kalendar-sync` did not actually compile for iOS or wasmJS — `commonMain` used JVM-only
  `String.format`/`SecurityException`, and the iOS EventKit integration referenced
  `EKAuthorizationStatusAuthorized` (deprecated in iOS 17, replaced by `EKAuthorizationStatusFullAccess`)
  and an `NSDate` constructor signature that doesn't exist in the generated Kotlin/Native bindings.
- Multi-day events (`endDate`) were silently dropped when a recurring event was expanded into
  occurrences, and when the Desktop/Android/iOS sync providers inserted or updated an event.
- `KalendarRule` with a multi-value `byMonthDay` (e.g. the 1st and 15th of every month, as shown in
  its own documentation) only ever produced one occurrence per month instead of both.
- `KalendarRule.interval` was silently ignored when combined with WEEKLY `byDay` or MONTHLY
  `byMonthDay` filters — an every-other-week rule fired every week. Interval now composes with the
  BY-filters, and the previously documented limitation is removed.
- The month/week grid's internal `LazyVerticalGrid` scrollable consumed vertical drags before the
  long-press drag gestures (drag-to-select, drag-to-reschedule) could see them, cancelling the
  gesture; internal grid scrolling is now disabled (the grid never needed it — pages are fixed
  height).
- Nested-lazy-layout crash on Android/iOS in `KalendarTimeline`/`KalendarYear` (a `LazyVerticalGrid`
  inside an already-scrollable parent) — replaced with the eager `KalendarStaticGrid` in both.
- Grid cells stretched to fill the row on trailing partial weeks (fewer than 7 days).

## [2.0.0-RC1] – 2026

### Breaking changes from 1.x
- The library has been fully migrated to **Kotlin Multiplatform** (Android, JVM/Desktop, iOS, wasmJS).
- `kalendar` now depends on the new `kalendar-foundation` module which must be added separately if
  you need direct access to foundation types.
- `KalendarType` is now a **sealed interface** instead of an enum. Replace `KalendarType.OCEANIC`
  style usages with `KalendarType.Oceanic` data-object references.
- Day-click callbacks now receive `List<KalendarEvent>` (events on that day) in addition to the
  tapped `LocalDate`.
- Selection behaviour is now configured through `OnDaySelectionAction` instead of individual
  boolean flags.

### Added
- **Four calendar variants** via `KalendarType`:
  - `Oceanic` — full month grid with previous/next arrow navigation.
  - `Firey` — single week row with previous/next arrow navigation.
  - `Solaris` — swipeable full month grid (infinite `HorizontalPager`).
  - `Aerial` — swipeable week row (infinite `HorizontalPager`).
- **`OnDaySelectionAction`** sealed class — `Single`, `Multiple`, and `Range` selection modes.
- **`KalendarSelectedDayRange`** — typed value for range selection with compile-time start/end fields.
- **`KalendarEvents` / `KalendarEvent` / `BasicKalendarEvent`** — typed event model with per-day
  event indicators (up to 3 dots).
- **`KalendarKonfig` / `KalendarDayKonfig` / `KalendarHeaderKonfig` / `KalendarDayLabelKonfig`**
  — composable configuration objects for colours, sizes, and text styles.
- **`KalendarColor`** — sealed class for `Solid` and `Gradient` colour values used throughout the
  configuration API.
- **`startDayOfWeek`** parameter — configure the first column of the week grid.
- **`restrictToCurrentWeekOrMonth`** flag — prevent back-navigation past the current period.
- wasmJS target support.

### Fixed
- `isCurrentMonth` navigation guard now correctly handles cross-year navigation
  (e.g. viewing January 2026 from December 2026 no longer disables back navigation).

---

## [1.0.0]

Initial release supporting Android-only Jetpack Compose with basic month and week calendar views.
