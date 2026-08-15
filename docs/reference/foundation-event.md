# `foundation.event`

`com.himanshoe.kalendar.foundation.event`

The event model. `KalendarEvent` is an interface so you implement it on the type your app already
has; `BasicKalendarEvent` is there for when you have no such type; `byDateExpandingSpans` is the
index a grid actually wants.

```kotlin
import com.himanshoe.kalendar.foundation.event.BasicKalendarEvent
```

## `KalendarEvent`

```kotlin
@Stable
public interface KalendarEvent
```

An event tied to a date. Only three members are abstract — `date`, `eventName`, and
`eventDescription`. Everything else defaults to `null`, so an implementation stays
source-compatible as the model grows.

| Property | Type | Default | Description |
|---|---|---|---|
| `date` | `LocalDate` | — | The date the event falls on, or the **first** date for a multi-day event. |
| `eventName` | `String` | — | Short, human-readable name, e.g. `"Team standup"`. |
| `eventDescription` | `String?` | — | Optional longer description. Abstract, so it must be implemented — pass `null` if you have none. |
| `id` | `String?` | `null` | A stable identifier, unique across the whole list handed to a calendar. Views that keep per-item state key on it, so supplying it keeps that state attached to the right event across reorders and edits. `null` makes those views fall back to list position. |
| `endDate` | `LocalDate?` | `null` | The last date the event occupies, inclusive, for a multi-day event. `null` confines it to `date`. |
| `startTime` | `LocalDateTime?` | `null` | When the event begins. Non-null is what makes an event *timed*: it drives within-day ordering and is what [`scheduleBlocks`](foundation-schedule.md) lays out. |
| `endTime` | `LocalDateTime?` | `null` | When the event ends. A missing end means a one-hour block. |
| `eventColor` | `Color?` | `null` | Tints the event's indicator dot and block. `null` falls back to a default indicator colour. |
| `calendarId` | `String?` | `null` | Links the event to a named source (`"work"`, `"personal"`). Carried as metadata; the built-in views do not read it. |

`@Stable` is load-bearing: the Compose compiler reads it off the classpath to infer that a
`KalendarEvent` parameter can be compared rather than assumed changed. That is why the engine depends
on `compose.runtime` — see [What foundation is not](../engine.md).

```kotlin
data class Booking(
    val bookingId: Long,
    val room: String,
    val guest: String,
    val from: LocalDateTime,
    val to: LocalDateTime,
) : KalendarEvent {
    override val id: String get() = bookingId.toString()
    override val date: LocalDate get() = from.date
    override val eventName: String get() = guest
    override val eventDescription: String get() = room
    override val startTime: LocalDateTime get() = from
    override val endTime: LocalDateTime get() = to
}
```

> **Note:** `startTime` and `endTime` are `LocalDateTime`, not `LocalTime` — they carry a date as
> well. Keep their date in sync with `date`; the grids place blocks by `date`, so a mismatch shows
> the event on one day with another day's times.

## `BasicKalendarEvent`

```kotlin
@Immutable
public class BasicKalendarEvent(
    override val date: LocalDate,
    override val eventName: String,
    override val eventDescription: String? = null,
    override val startTime: LocalDateTime? = null,
    override val endTime: LocalDateTime? = null,
    override val eventColor: Color? = null,
    override val calendarId: String? = null,
    override val endDate: LocalDate? = null,
    override val id: String? = null,
) : KalendarEvent
```

The ready-made implementation, for demos, fixtures, and screens that read events from a service and
never model them.

| Parameter | Type | Default | Description |
|---|---|---|---|
| `date` | `LocalDate` | — | The date the event falls on, or the first date of a span. |
| `eventName` | `String` | — | Short, human-readable name. |
| `eventDescription` | `String?` | `null` | Optional longer description. |
| `startTime` | `LocalDateTime?` | `null` | Start; non-null makes the event timed. |
| `endTime` | `LocalDateTime?` | `null` | End; missing means one hour. |
| `eventColor` | `Color?` | `null` | Indicator/block tint. |
| `calendarId` | `String?` | `null` | Source-calendar identifier, carried as metadata. |
| `endDate` | `LocalDate?` | `null` | Last date occupied, inclusive. |
| `id` | `String?` | `null` | Stable list key. |

| Member | Returns | Description |
|---|---|---|
| `copy(…)` | `BasicKalendarEvent` | A duplicate with only the properties passed here replaced. Takes the same nine parameters as the constructor. |

```kotlin
val day = LocalDate(2026, 8, 12)

val events = listOf(
    BasicKalendarEvent(
        date = day,
        eventName = "Team standup",
        eventDescription = "Daily sync",
        startTime = LocalDateTime(day, LocalTime(9, 0)),
        endTime = LocalDateTime(day, LocalTime(9, 30)),
        eventColor = Color(0xFF4CAF50),
    ),
    BasicKalendarEvent(date = day, endDate = day.plus(2, DateTimeUnit.DAY), eventName = "Conference"),
)
```

> **Warning:** this is an `@Immutable` class with a hand-written `copy`, **not** a `data class`. It
> has value `equals`, `hashCode` and `toString`, but no `componentN` — so it cannot be destructured,
> and `copy` is the only generated-looking member it has. `KalendarEvent` itself has no `copy` at
> all, so an event handed back by a drag callback must be smart-cast to a concrete type (or rebuilt
> from your domain model) before you can change it.

The constructor's positional order is `date`, `eventName`, `eventDescription`, `startTime`,
`endTime`, `eventColor`, `calendarId`, `endDate`, `id` — pass by name and the order stops mattering.

> **Note:** the binary dump lists a `HASH_FACTOR` field on this class. It is the `31` multiplier of
> the private companion's `hashCode`, exposed as a JVM static by the Kotlin compiler. It is not part
> of the Kotlin API and is not visible from Kotlin source.

## `KalendarEvents`

```kotlin
public typealias KalendarEvents = List<KalendarEvent>
```

A list of events of no particular type — the widest event list there is. It is a plain `List`, so you
build one with ordinary collection code and any `List<YourEventType>` already satisfies it.

```kotlin
val events: KalendarEvents = bookings // List<Booking> where Booking : KalendarEvent
```

It is deliberately **not** parameterised. The views are generic in their event type and spell that
parameter `List<E>` directly: `KalendarEvents<E>` would be the same length as `List<E>`, one more
name to learn, and would force a type argument onto every place that genuinely does not care which
event type it holds. Reach for it when you are storing or passing events without caring what they
are; reach for `List<Booking>` when the element type is the point — every `List<Booking>` is already
a `KalendarEvents`, because `List` is covariant.

## `byDateExpandingSpans`

```kotlin
public fun <E : KalendarEvent> List<E>.byDateExpandingSpans(): Map<LocalDate, List<E>>
```

Groups events by **every** date they occupy: an event with `endDate` set appears under each date from
`date` through `endDate`, inclusive.

The element type is carried through rather than widened. This is the index the calendar views build
before handing a date's events to `onDateClick`, so widening it here would make every one of those
callbacks report `KalendarEvent` no matter what the caller supplied:

```kotlin
val byDate: Map<LocalDate, List<Booking>> = bookings.byDateExpandingSpans()
```

This is the index a grid wants. Build it once per event list and each cell becomes a map lookup,
rather than a scan of every event per cell. It is also the difference between a three-day conference
showing on three days and showing only on the first — the bug a hand-written `groupBy { it.date }`
ships with.

```kotlin
val byDate = remember(events) { events.byDateExpandingSpans() }

monthGridDates(monthStart = monthStart, startDayOfWeek = DayOfWeek.MONDAY).forEach { date ->
    DayCell(date = date, events = byDate[date].orEmpty())
}
```

Two edges are handled rather than trusted:

- A span longer than **366 days** is truncated, so a malformed `endDate` far in the future cannot
  turn one event into an unbounded map.
- An `endDate` *before* `date` is treated as a single-day event rather than producing an empty or
  reversed range.

`remember` compares its key with `equals` regardless of Compose stability, so a value-equal list does
not rebuild the map — which is why the built-in views wrap this call exactly as above.
