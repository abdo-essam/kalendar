# Kalendar: kalendar-sync

Reads, writes, and exports events from the device's built-in calendar — no third-party API or
cloud account required. Works standalone; it doesn't depend on `kalendar` or `kalendar-foundation`.

## Platform support

| Platform | Backing store | Read/write | Permission |
|---|---|---|---|
| Android | [`CalendarContract`](https://developer.android.com/reference/android/provider/CalendarContract) | Yes | `READ_CALENDAR` / `WRITE_CALENDAR` |
| iOS | [EventKit](https://developer.apple.com/documentation/eventkit) | Yes | `NSCalendarUsageDescription` |
| Desktop (JVM) | An `.ics` file you choose | Yes, when `icsFilePath` is set | None |
| Web (wasmJS) | — | No | — |

On Desktop and Web, `fetchEvents`/`insertEvent`/`updateEvent`/`deleteEvent` return
`KalendarSyncResult.NotSupported` unless a Desktop `icsFilePath` is provided. On every platform,
the pure in-memory `exportToIcs`/`importFromIcs` functions work regardless of device-calendar
support.

## Usage

```kotlin
// Android (inside Activity / ViewModel)
val sync = KalendarSync(context)

// iOS / Desktop / Web
val sync = KalendarSync()

// Desktop, backed by a chosen .ics file instead of NotSupported
val sync = KalendarSync(icsFilePath = "/Users/me/calendars/work.ics")
```

```kotlin
when (val result = sync.fetchEvents(startDate, endDate)) {
    is KalendarSyncResult.Success -> showEvents(result.data)
    is KalendarSyncResult.PermissionDenied -> requestCalendarPermission()
    is KalendarSyncResult.NotSupported -> showUnsupportedMessage()
    is KalendarSyncResult.Error -> showError(result.message)
}
```

`onSuccess`/`onError`/`onPermissionDenied`/`onNotSupported` chain off any result for a callback
style, and `getOrNull()`/`getOrThrow()` unwrap it directly when you don't need to branch.

### Writing events

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

### Choosing a calendar

`listCalendars` returns the calendars available on the device; pass a `KalendarSyncCalendar.id`
as `insertEvent`'s `calendarId` argument to write into a specific calendar instead of the
platform default:

```kotlin
val calendars = sync.listCalendars().getOrNull().orEmpty()
val work = calendars.firstOrNull { it.name == "Work" }

sync.insertEvent(event, calendarId = work?.id)
```

On Android, visible calendars with contributor access or higher are listed, with their display
colour in `colorArgb` (packed 32-bit ARGB). On iOS, `colorArgb` is always `null` — EventKit
exposes calendar colours only as `CGColor`, which has no reliable cross-platform ARGB mapping.
On Desktop, the single backing `.ics` file is returned as one calendar (id `"default"`, named
after the file) and `calendarId` is ignored; on Web, `listCalendars` returns
`KalendarSyncResult.NotSupported`.

### Reminders

Attach `KalendarReminder`s to fire notifications a fixed number of minutes before the event
starts. They're carried through insert/update/fetch on Android (`CalendarContract.Reminders`),
iOS (`EKAlarm`), and the Desktop `.ics` provider, and encoded as RFC 5545 `VALARM` blocks by
`exportToIcs`/`importFromIcs`:

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

Updating an event replaces its existing reminders with the ones on the passed event.

### Attendees

Attach `KalendarAttendee`s to invite people to an event. On Android they're written and read as
`CalendarContract.Attendees` rows; the iCal codec encodes them as `ATTENDEE;CN=Name:mailto:email`
lines, so they also round-trip through the Desktop `.ics` provider:

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

**On iOS attendees are read-only**: EventKit does not allow adding attendees programmatically,
so `attendees` is ignored on insert/update there — but events fetched from the device calendar
still populate the list from the event's participants.

### Multi-day events

Set `endDate` for an event spanning more than one day — it's carried through insert/update/fetch
on every platform provider and through iCal encode/decode as an RFC 5545 exclusive `DTEND`:

```kotlin
BasicKalendarSyncEvent(
    date = LocalDate(2026, 8, 12),
    endDate = LocalDate(2026, 8, 14),
    eventName = "Conference",
)
```

### Recurring events

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

`KalendarRule` supports `DAILY`/`WEEKLY`/`MONTHLY`/`YEARLY` frequencies, `interval`, `count`/`until`
termination (mutually exclusive), and the RFC 5545 `byDay`/`byMonthDay`/`byMonth` filters.
`interval` composes with the filters — e.g. `WEEKLY` + `byDay` + `interval = 2` fires only in every
other week, and `MONTHLY` + `byMonthDay` + `interval = 2` only in every other month.

`recurrenceRule` is written to the device calendar by `insertEvent`/`updateEvent`, so a recurring
template lands as a real recurring series, not a one-off:

- **Android** stores the rule in `CalendarContract`'s `RRULE` column, together with the RFC 2445
  `DURATION` the provider requires in place of `DTEND` for recurring events, and `exceptionDates`
  as an `EXDATE` column. `fetchEvents` maps all three back onto the event.
- **iOS** maps the rule to an `EKRecurrenceRule` (frequency, interval, `count`/`until` end,
  `byDay`/`byMonthDay`/`byMonth`), and `fetchEvents` maps it back. **`exceptionDates` is not
  written on iOS**: EventKit has no per-rule EXDATE API — mirroring the attendees limitation —
  and exceptions there are made by deleting an individual occurrence instead. When an iOS update
  adds, changes, or removes a rule it is saved with `EKSpanFutureEvents`, since EventKit does not
  apply rule changes to a single occurrence.
- The **Desktop** `.ics` provider round-trips both fields through the iCal codec as before.

### Recurrence exceptions ("delete just this one occurrence")

Set `exceptionDates` to skip specific dates when a recurring event is expanded — the RFC 5545
EXDATE equivalent, encoded/decoded as an `EXDATE` line by `exportToIcs`/`importFromIcs`:

```kotlin
val template = BasicKalendarSyncEvent(
    date = LocalDate(2026, 1, 6),
    eventName = "Weekly team sync",
    recurrenceRule = KalendarRule(frequency = KalendarRecurrenceFrequency.WEEKLY),
    exceptionDates = listOf(LocalDate(2026, 1, 20)), // skipped — team offsite that week
)
```

Excepted dates still count towards `KalendarRule.count` (RRULE determines the series first; EXDATE
then removes specific occurrences from it, matching how most calendar apps interpret the two
together). `exceptionDates` is honoured by `expandOccurrences`, the iCal codec, and the Android
provider, which writes it to `CalendarContract`'s `EXDATE` column and reads it back on fetch. On
iOS it is **not** written — EventKit has no per-rule EXDATE API, so delete the individual
occurrence instead — and fetched iOS events don't populate it either, since EventKit returns
materialized occurrences with the skipped ones simply absent. `recurrenceRule` itself round-trips
on both platforms: Android parses the `RRULE` column and iOS maps the series' `EKRecurrenceRule`,
which EventKit exposes on every fetched occurrence.

### iCal import/export

Pure in-memory, no permission or `KalendarSyncProvider` needed, works on every platform:

```kotlin
val icsText = exportToIcs(events)
val events = importFromIcs(icsText)
```

## Parameters

### `KalendarSyncEvent`

| Property | Type | Description |
|---|---|---|
| `id` | `String?` | Device-assigned identifier; `null` before the event is saved. |
| `date` | `LocalDate` | The event's date, or the first date for a multi-day event. |
| `endDate` | `LocalDate?` | Last date occupied, inclusive, for a multi-day all-day event. `null` for single-day. |
| `eventName` | `String` | Display title. |
| `eventDescription` | `String?` | Optional notes. |
| `startTime` / `endTime` | `LocalTime?` | `null` for all-day events. |
| `isAllDay` | `Boolean` | Derived: `true` when both times are `null`. |
| `recurrenceRule` | `KalendarRule?` | `null` for non-recurring events. Written to the device calendar on Android (`RRULE`/`DURATION`) and iOS (`EKRecurrenceRule`). |
| `exceptionDates` | `List<LocalDate>` | Dates to skip when `recurrenceRule` is expanded. Defaults to empty. Written/read as `EXDATE` on Android; not written on iOS. |
| `reminders` | `List<KalendarReminder>` | Reminders firing `minutesBefore` minutes before the event starts. Defaults to empty. |
| `attendees` | `List<KalendarAttendee>` | People invited to the event (`email`, optional `name`). Defaults to empty. Read-only on iOS — writes are ignored there. |

### `KalendarRule`

| Parameter | Type | Default | Description |
|---|---|---|---|
| `frequency` | `KalendarRecurrenceFrequency` | — | `DAILY`, `WEEKLY`, `MONTHLY`, or `YEARLY`. |
| `interval` | `Int` | `1` | Units of `frequency` between occurrences. |
| `count` | `Int?` | `null` | Total occurrences, including the first. Mutually exclusive with `until`. |
| `until` | `LocalDate?` | `null` | Last allowed occurrence date. Mutually exclusive with `count`. |
| `byDay` | `List<KalendarWeekDay>` | `emptyList()` | Restrict to these weekdays. |
| `byMonthDay` | `List<Int>` | `emptyList()` | Restrict to these days of month (1–31). |
| `byMonth` | `List<Int>` | `emptyList()` | Restrict to these months (1–12). |
