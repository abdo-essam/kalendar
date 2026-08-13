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
together). This is currently supported for `expandOccurrences` and the iCal codec only — the
Android/iOS providers don't round-trip `exceptionDates` on fetch, since `CalendarContract`/EventKit
already expand recurrence server-side and return materialized instances rather than the RRULE
template (so `recurrenceRule` itself isn't populated on fetch either).

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
| `recurrenceRule` | `KalendarRule?` | `null` for non-recurring events. |
| `exceptionDates` | `List<LocalDate>` | Dates to skip when `recurrenceRule` is expanded. Defaults to empty. |

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
