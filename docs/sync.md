# Device sync

`kalendar-sync` reads, writes, and exports events from the device's **built-in calendar** — no
third-party API, no cloud account, no OAuth.

It is an independent module. It does not depend on `kalendar` or `kalendar-foundation`, and its
event model (`KalendarSyncEvent`) is separate from the one the views use
([`KalendarEvent`](events.md)). Add it on its own if all you need is calendar access:

```kotlin
dependencies {
    implementation("com.himanshoe:kalendar-sync:1.0.0")
}
```

## Platform support

| Platform | Backing store | Read/write | Permission |
|---|---|---|---|
| Android | `CalendarContract` | Yes | `READ_CALENDAR` and `WRITE_CALENDAR` |
| iOS | EventKit | Yes | `NSCalendarUsageDescription` (pre-iOS 17 SDK) or `NSCalendarsFullAccessUsageDescription` (iOS 17+ SDK) |
| Desktop (JVM) | An `.ics` file you choose | Yes, when `icsFilePath` is set | None |
| Web (wasmJs) | — | No | — |

On Desktop without an `icsFilePath`, and on Web always, `fetchEvents` / `listCalendars` /
`insertEvent` / `updateEvent` / `deleteEvent` return `KalendarSyncResult.NotSupported`.

The pure in-memory [`exportToIcs` / `importFromIcs`](#ical-importexport) functions work on **every**
platform regardless, including wasmJs — they touch no device calendar and need no permission.

### Permissions in practice

On **Android**, declare both permissions in your manifest and request them at runtime as usual;
`hasPermission()` is a synchronous check.

On **iOS**, add the usage-description key to `Info.plist`. The provider requests **full access**
itself on first use — `requestFullAccessToEventsWithCompletion:` on iOS 17+, falling back to the
legacy `requestAccessToEntityType:completion:` on older versions. `hasPermission()` triggers the
system dialog the first time it is called.

> **Warning:** iOS 17 split calendar access into write-only and full. Only full access can fetch
> events, which is why the provider always asks for it.

## Getting a provider

`KalendarSync` is a factory function with a per-platform signature:

```kotlin
// Android, inside an Activity or ViewModel
val sync = KalendarSync(context)

// iOS and Web
val sync = KalendarSync()

// Desktop — backed by a chosen .ics file instead of NotSupported
val sync = KalendarSync(icsFilePath = "/Users/me/calendars/work.ics")
```

All of them return a `KalendarSyncProvider`.

## Reading events

Every call returns a `KalendarSyncResult<T>`, a sealed class with four cases:

```kotlin
when (val result = sync.fetchEvents(startDate, endDate)) {
    is KalendarSyncResult.Success -> showEvents(result.data)
    is KalendarSyncResult.PermissionDenied -> requestCalendarPermission()
    is KalendarSyncResult.NotSupported -> showUnsupportedMessage()
    is KalendarSyncResult.Error -> showError(result.message, result.cause)
}
```

| Case | Payload |
|---|---|
| `Success<T>` | `data: T` |
| `PermissionDenied` | — |
| `NotSupported` | — |
| `Error` | `message: String`, `cause: Throwable?` |

For a callback style, chain the extensions instead of branching:

```kotlin
sync.fetchEvents(startDate, endDate)
    .onSuccess { events -> showEvents(events) }
    .onPermissionDenied { requestCalendarPermission() }
    .onNotSupported { showUnsupportedMessage() }
    .onError { message, _ -> showError(message) }
```

And when you do not need to branch at all, `getOrNull()` returns `T?` and `getOrThrow()` returns `T`
or throws (`KalendarPermissionDeniedException` for the denied case).

`fetchEvents` takes an inclusive date range plus an optional `timeZone`, defaulting to
`TimeZone.currentSystemDefault()`:

```kotlin
val events = sync.fetchEvents(
    startDate = LocalDate(2026, 8, 1),
    endDate = LocalDate(2026, 8, 31),
).getOrNull().orEmpty()
```

## Writing events

```kotlin
val id = sync.insertEvent(
    BasicKalendarSyncEvent(
        date = LocalDate(2026, 8, 12),
        eventName = "Team offsite",
    ),
).getOrNull()

id?.let { sync.updateEvent(it, updatedEvent) }
id?.let { sync.deleteEvent(it) }
```

`insertEvent` returns the device-assigned ID. `updateEvent` locates the record by the `eventId`
argument only — the event's own `id` field is ignored.

## Choosing a calendar

`listCalendars()` returns the calendars events can be read from or written into:

```kotlin
val calendars = sync.listCalendars().getOrNull().orEmpty()
val work = calendars.firstOrNull { it.name == "Work" }

sync.insertEvent(event, calendarId = work?.id)
```

`KalendarSyncCalendar` carries `id: String`, `name: String`, and `colorArgb: Long?`.

| Platform | Behaviour |
|---|---|
| Android | Visible calendars with contributor access or higher, with their display colour in `colorArgb` (packed 32-bit ARGB). |
| iOS | All event calendars. `colorArgb` is always `null` — EventKit exposes calendar colours only as `CGColor`, which has no reliable cross-platform ARGB mapping. |
| Desktop | The single backing `.ics` file, as one calendar with id `"default"` named after the file. `calendarId` is ignored on insert. |
| Web | `NotSupported`. |

Passing `calendarId = null` (the default) writes to the platform's default calendar.

## `KalendarSyncEvent`

| Property | Type | Description |
|---|---|---|
| `id` | `String?` | Device-assigned identifier. `null` before the event is saved. |
| `date` | `LocalDate` | The event's date, or the first date of a multi-day event. |
| `endDate` | `LocalDate?` | Last date occupied, inclusive, for a multi-day event. `null` for single-day. |
| `eventName` | `String` | Display title. |
| `eventDescription` | `String?` | Optional notes. |
| `startTime` | `LocalTime?` | `null` for all-day events. |
| `endTime` | `LocalTime?` | `null` for all-day events. |
| `isAllDay` | `Boolean` | Derived: `true` when both times are `null`. |
| `recurrenceRule` | `KalendarRule?` | `null` for non-recurring events. |
| `exceptionDates` | `List<LocalDate>` | Dates to skip when `recurrenceRule` is expanded. Defaults to empty. |
| `reminders` | `List<KalendarReminder>` | Notifications a fixed number of minutes before the start. Defaults to empty. |
| `attendees` | `List<KalendarAttendee>` | People invited. Defaults to empty. |

> **Note:** `startTime` and `endTime` here are `LocalTime`, not `LocalDateTime` — the date comes
> from `date`. This is the opposite of [`KalendarEvent`](events.md) in the view module, so converting
> between the two models means moving the date across.

`BasicKalendarSyncEvent` is the ready-made implementation, a `data class` with every field
defaulted except `date` and `eventName`.

### Multi-day events

```kotlin
BasicKalendarSyncEvent(
    date = LocalDate(2026, 8, 12),
    endDate = LocalDate(2026, 8, 14),
    eventName = "Conference",
)
```

Carried through insert, update, and fetch on every platform provider, and through iCal encode/decode
as an RFC 5545 exclusive `DTEND`.

## Reminders

`KalendarReminder(minutesBefore: Int)` fires a notification that many minutes before the event
starts:

```kotlin
BasicKalendarSyncEvent(
    date = LocalDate(2026, 8, 12),
    eventName = "Team offsite",
    reminders = listOf(
        KalendarReminder(minutesBefore = 30),
        KalendarReminder(minutesBefore = 90),
    ),
)
```

Written and read on Android (`CalendarContract.Reminders`), iOS (`EKAlarm`), and the Desktop `.ics`
provider; encoded as RFC 5545 `VALARM` blocks by the iCal codec.

> **Warning:** updating an event **replaces** its existing reminders with the ones on the passed
> event. Fetch first and re-send the full list if you mean to add one.

## Attendees

`KalendarAttendee(email: String, name: String? = null)`:

```kotlin
BasicKalendarSyncEvent(
    date = LocalDate(2026, 8, 12),
    eventName = "Team offsite",
    attendees = listOf(
        KalendarAttendee(email = "jane@example.com", name = "Jane Doe"),
        KalendarAttendee(email = "anon@example.com"),
    ),
)
```

Written and read on Android (`CalendarContract.Attendees`), and encoded as
`ATTENDEE;CN=Name:mailto:email` lines by the iCal codec, so they round-trip through the Desktop
`.ics` provider too.

> **Warning:** on iOS attendees are **read-only**. EventKit does not allow adding attendees
> programmatically, so `attendees` is silently ignored on insert and update there. Events fetched
> from the device calendar still populate the list from the event's participants.

## Recurring events

`KalendarRule` models an RFC 5545 `RRULE`:

| Parameter | Type | Default | Description |
|---|---|---|---|
| `frequency` | `KalendarRecurrenceFrequency` | — | `DAILY`, `WEEKLY`, `MONTHLY`, or `YEARLY`. |
| `interval` | `Int` | `1` | Units of `frequency` between occurrences. Must be at least 1. |
| `count` | `Int?` | `null` | Total occurrences, including the first. Mutually exclusive with `until`. |
| `until` | `LocalDate?` | `null` | Last allowed occurrence date. Mutually exclusive with `count`. |
| `byDay` | `List<KalendarWeekDay>` | empty | Restrict to these weekdays. |
| `byMonthDay` | `List<Int>` | empty | Restrict to these days of month, 1–31. |
| `byMonth` | `List<Int>` | empty | Restrict to these months, 1–12. |

```kotlin
val template = BasicKalendarSyncEvent(
    date = LocalDate(2026, 1, 6),
    eventName = "Weekly team sync",
    recurrenceRule = KalendarRule(
        frequency = KalendarRecurrenceFrequency.WEEKLY,
        byDay = listOf(KalendarWeekDay.MONDAY),
    ),
)

val occurrences = template.expandOccurrences(
    rangeStart = LocalDate(2026, 1, 1),
    rangeEnd = LocalDate(2026, 3, 31),
)
```

`expandOccurrences` materialises the individual events inside a window. On an event with no
`recurrenceRule` it returns the event itself when it falls in the range, or an empty list.

### Filter semantics

The `BY` filters follow RFC 5545:

- `WEEKLY` + `byDay` fires on the listed weekdays of each week.
- `MONTHLY` + `byMonthDay` fires on the listed days of each month.
- `MONTHLY` + `byDay` fires on **every** matching weekday of each month — every Friday, not the
  first Friday.
- `YEARLY` + `byMonth` fires in each listed month on the start date's day-of-month, even when the
  series starts in a month outside the filter.

`interval` composes with the filters: `WEEKLY` + `byDay` + `interval = 2` fires only in every other
week, and a filtered `YEARLY` rule with `interval = 2` only in every other year.

`count` is always consumed **from the series start**, so expanding a window that begins after a
`count`-limited series has already ended yields nothing.

### Writing rules to the device

`recurrenceRule` is written by `insertEvent` and `updateEvent`, so a recurring template lands as a
real series rather than a one-off.

| Platform | How |
|---|---|
| Android | The `RRULE` column, plus the RFC 2445 `DURATION` the provider requires in place of `DTEND` for recurring rows, plus `EXDATE`. `fetchEvents` maps all three back. |
| iOS | An `EKRecurrenceRule` (frequency, interval, `count`/`until`, `byDay`/`byMonthDay`/`byMonth`), mapped back on fetch. Rule changes are saved with `EKSpanFutureEvents`, since EventKit cannot apply them to a single occurrence. |
| Desktop | Round-tripped through the iCal codec. |

> **Note:** on Android a recurring series is stored as one row whose start is the **series** start,
> so `fetchEvents` returns a recurring template whenever the series begins on or before the window's
> end — not only when it begins inside the window. Expand each returned template with
> `expandOccurrences(rangeStart, rangeEnd)` to get the occurrences that actually fall inside it.

### Recurrence exceptions

`exceptionDates` skips specific dates — the RFC 5545 `EXDATE` equivalent, for "delete just this one
occurrence" without breaking the series:

```kotlin
BasicKalendarSyncEvent(
    date = LocalDate(2026, 1, 6),
    eventName = "Weekly team sync",
    recurrenceRule = KalendarRule(frequency = KalendarRecurrenceFrequency.WEEKLY),
    exceptionDates = listOf(LocalDate(2026, 1, 20)),
)
```

Excepted dates still count towards `KalendarRule.count`: the `RRULE` determines the series first,
then `EXDATE` removes occurrences from it — matching how most calendar apps interpret the two
together.

Honoured by `expandOccurrences`, the iCal codec, and the Android provider.

> **Warning:** `exceptionDates` is **not written on iOS**. EventKit has no per-rule EXDATE API, so
> delete the individual occurrence instead. Fetched iOS events do not populate it either — EventKit
> returns materialised occurrences with the skipped ones simply absent.

## Editing one occurrence of a series

When a user edits or deletes one instance of a repeating event, every calendar app asks the same
question: *this event, this and all following, or all events?* `KalendarRecurrenceScope` is that
question, and `editOccurrence` / `deleteOccurrence` turn the answer into the writes that implement
it.

```kotlin
import com.himanshoe.kalendar.sync.KalendarRecurrenceScope
import com.himanshoe.kalendar.sync.deleteOccurrence
import com.himanshoe.kalendar.sync.editOccurrence

val change = series.deleteOccurrence(
    occurrenceDate = LocalDate(2026, 3, 10),
    scope = KalendarRecurrenceScope.THIS_OCCURRENCE,
)
```

Neither function touches a device. They are pure: series in, `KalendarSeriesChange` out. You decide
when to apply it, and to what — a provider, your own backend, a local database.

### What the receiver has to be

The receiver must be the **stored series template**: the single event carrying the
`recurrenceRule`, as `fetchEvents` returns it. It must *not* be one of the occurrences
`expandOccurrences` materialises from it — those are derived values with no row of their own, and
editing one is meaningless.

`occurrenceDate` is the start date of the occurrence the user acted on, **as it stands before the
edit**. `edited` carries the new field values and may sit on a different date, which is how an
occurrence moves.

> **Note:** `edited`'s own `recurrenceRule` and `exceptionDates` are ignored. A scoped edit changes
> *which* occurrences are affected, not the repeat pattern itself — the result always repeats with
> the receiver's rule. To change the pattern, call `updateEvent` with a new rule instead.

### What each scope does to the data

This is the part worth internalising, because it is what your backend will actually see.

| Scope | The stored series row | Rows created |
|---|---|---|
| `THIS_OCCURRENCE` | Keeps its rule; gains an `EXDATE` for `occurrenceDate`. | One **detached** event for an edit: `id = null`, `recurrenceRule = null`, `exceptionDates` empty. None for a delete. |
| `THIS_AND_FOLLOWING` | Terminated: `until = occurrenceDate - 1 day`, `count` cleared, exception dates trimmed to those before the split. | One **new series** for an edit, anchored on `edited.date`, repeating with the original rule. None for a delete. |
| `ENTIRE_SERIES` | Rewritten in place under the original `id`, keeping its rule — or deleted outright. | None. |

Read off `KalendarSeriesChange`:

| | |
|---|---|
| `updatedSeries` | The series row rewritten in place, to be written back under the **original** `id`. `null` means the row must be deleted. |
| `deletesSeries` | The same condition as `updatedSeries == null`, named for readability. |
| `newEvents` | Rows that do not exist yet and must be inserted. Each carries `id = null`; the provider assigns the real one. |

A change never both removes the series and creates events, so applying the series write **before** the
inserts cannot leave a window in which an occurrence exists twice:

```kotlin
val seriesId = requireNotNull(series.id)
val updated = change.updatedSeries
if (updated == null) {
    sync.deleteEvent(eventId = seriesId)
} else {
    sync.updateEvent(eventId = seriesId, event = updated)
}
for (event in change.newEvents) {
    sync.insertEvent(event = event)
}
```

### `THIS_OCCURRENCE` — exclude and detach

The series gains an exception date and keeps everything else. For an edit, the occurrence is
re-materialised as a standalone one-off event.

```kotlin
val change = series.editOccurrence(
    occurrenceDate = LocalDate(2026, 3, 10),
    edited = BasicKalendarSyncEvent(
        date = LocalDate(2026, 3, 10),
        eventName = "Standup (extended)",
        startTime = LocalTime(9, 0),
        endTime = LocalTime(10, 0),
    ),
    scope = KalendarRecurrenceScope.THIS_OCCURRENCE,
)
```

The detached event deliberately carries **no rule and no exception list**. It stands for one date, and
a copy that still carried the rule would resurrect the whole series under a second id the moment
anything expanded it. It does keep the edited event's reminders, attendees and multi-day span.

Two consequences worth knowing:

- **An `EXDATE` does not shorten the series.** RFC 5545 has it remove occurrences from an
  already-determined series, so an excluded date still consumes a `count`. A `COUNT=10` series with
  one exception yields nine occurrences, not ten.
- **Moving the occurrence leaves its original date empty**, which is what "move just this one" means:
  the old date is excluded and the detached event sits on the new one.

Excluding the series' *own first occurrence* still detaches — the series keeps its `DTSTART` and
simply skips it.

> **Warning:** on iOS this scope cannot be applied as written. EventKit has no per-rule `EXDATE` API,
> so the `updatedSeries` half is silently lost. Delete the materialised occurrence instead.

### `THIS_AND_FOLLOWING` — split into two series

The stored row is terminated immediately before the split, and an edit starts a fresh series at it.

```kotlin
val change = series.editOccurrence(
    occurrenceDate = LocalDate(2026, 3, 10),
    edited = BasicKalendarSyncEvent(
        date = LocalDate(2026, 3, 10),
        eventName = "Standup (30 min)",
        startTime = LocalTime(9, 0),
        endTime = LocalTime(9, 30),
    ),
    scope = KalendarRecurrenceScope.THIS_AND_FOLLOWING,
)
```

Four boundary rules decide what your backend sees:

- **`UNTIL` lands the day *before* the split.** RFC 5545's `UNTIL` is inclusive, so it has to, or the
  occurrence at the split would survive in both halves. The original keeps the occurrence before the
  split and loses the one at it; the continuation starts exactly at it. Every date the original
  series produced is still produced exactly once, by one half or the other.
- **A `count` is divided, not copied.** The continuation receives only what the original had left —
  carry it across and the series runs for the full count twice. The occurrences that stay behind are
  counted **with exception dates cleared**, for the reason above: an excluded date still consumed one.
- **A split at the series' own first occurrence rewrites in place.** Nothing precedes it, so
  terminating the original would leave a row whose `UNTIL` sits before its own `DTSTART` — an empty
  series. Instead the edit is applied to the whole series, and a *deletion* removes the row
  (`deletesSeries` is `true`).
- **The continuation can be dropped entirely.** If nothing is left of the count, or the original's
  `UNTIL` already lies before the new anchor, there is no second series and `newEvents` is empty.

Exception dates are divided at the same boundary, and those moving to the continuation shift with it
if the edit moved the occurrence.

### `ENTIRE_SERIES` — rewrite in place

The row keeps its `id` and its rule, and takes the edited fields.

```kotlin
val change = series.editOccurrence(
    occurrenceDate = LocalDate(2026, 3, 10),
    edited = BasicKalendarSyncEvent(
        date = LocalDate(2026, 3, 11),
        eventName = "Standup",
        startTime = LocalTime(9, 30),
        endTime = LocalTime(9, 45),
    ),
    scope = KalendarRecurrenceScope.ENTIRE_SERIES,
)
```

Moving the occurrence moves the **series**: the anchor date, the multi-day span, and every exception
date shift by the same number of days. That keeps the exception list attached to the occurrences it
belongs to instead of tearing it away from them.

For a deletion, `updatedSeries` is `null` and `occurrenceDate` is unused — the whole row goes.

> **Warning:** for a rule with `byDay` / `byMonthDay` / `byMonth`, the BY-parts still decide which
> days fire, so shifting the anchor does not reliably shift the occurrences. Prefer
> `THIS_AND_FOLLOWING` when moving a filtered series.

### An event with no rule

All three scopes mean the same thing on a non-recurring event: rewrite it, or delete it. The rewrite
clears any rule and exception dates the edited event was carrying, since a one-off event has neither.
You do not need to branch on `recurrenceRule == null` before calling either function.

## iCal import/export

Pure in-memory, no provider or permission needed, and available on every platform including wasmJs:

```kotlin
val icsText = exportToIcs(events)
val decoded = importFromIcs(icsText)
```

Timed events are written and read as RFC 5545 **floating local times** — `DTSTART:20260812T090000`,
with no `Z` suffix and no `TZID`. Each consuming application interprets them in its own zone, which
matches how `KalendarSyncEvent` itself carries zone-less `LocalDate` and `LocalTime` values.

`TZID` parameters are not supported and are ignored on import. A trailing `Z` is accepted and
treated as floating, for backwards compatibility with files older versions of this library exported.

`EXDATE` values are written with a value type matching `DTSTART` — plain dates for all-day events,
floating date-times at the event's start time for timed events. Both forms are accepted on import,
as are day-based `VALARM` triggers such as `-P1D`.

## Feeding sync events into the views

The two event models are separate by design, so convert at the boundary:

```kotlin
fun KalendarSyncEvent.toKalendarEvent(): BasicKalendarEvent = BasicKalendarEvent(
    date = date,
    endDate = endDate,
    eventName = eventName,
    eventDescription = eventDescription,
    startTime = startTime?.let { LocalDateTime(date, it) },
    endTime = endTime?.let { LocalDateTime(endDate ?: date, it) },
)

val events = sync.fetchEvents(monthStart, monthEnd)
    .getOrNull()
    .orEmpty()
    .map { it.toKalendarEvent() }

KalendarMonth(selectedDate = today, events = events)
```

Note the date each time is paired with: `startTime` belongs to `date`, while `endTime` belongs to
`endDate` when the event spans days.
