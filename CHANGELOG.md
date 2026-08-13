# Changelog

All notable changes to Kalendar will be documented in this file. The format is
based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and this
project adheres to [Semantic Versioning](https://semver.org/).

---

## [Unreleased]

### Breaking changes
- Removed `KalendarType.Oceanic`, `Firey`, `Solaris`, `Aerial`, and `Yearly` (and the
  `kalendar-foundation` internals they depended on) entirely. Replace them with the standalone
  `com.himanshoe.kalendar.view` composables below — there is no `KalendarType`-based drop-in
  replacement; call `KalendarWeek`/`KalendarMonth`/`KalendarYear`/`KalendarTimeline` directly.

### Added
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
- Sample app now has a view switcher covering every view type (Week, Month, Year, Timeline, Agenda).
- `explicitApi()` mode enabled across all three published modules — every public declaration now
  requires an explicit visibility modifier and return type, guarding against accidental API leaks.
- `kotlinx-binary-compatibility-validator` wired into all three published modules and CI
  (`apiCheck`), so an accidental breaking change to the public API surface now fails the build.
- Root `.editorconfig` documenting this project's formatting conventions (4-space indent, 140-char
  line length, no forced trailing commas) for editors and the existing `detekt-formatting`
  (ktlint-based) ruleset to read.

### Fixed
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
