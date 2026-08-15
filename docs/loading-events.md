# Loading events as they are needed

Every view will take its whole event list up front, and for a screen that already holds its events
in memory that is the right thing — pass `events` and stop reading here.

This page is for the other case: the events live in a database or behind a network call, and loading
all of them before drawing anything is not an option. A calendar is unbounded in both directions, so
"all of them" has no natural end. The two things people reach for instead both go wrong:

- **Load a wide window up front.** Every swipe eventually leaves it, and widening it is the same
  problem one order of magnitude later.
- **Reload on every swipe.** The page the user just came from is fetched again on the way back, a
  swipe lands on an empty grid while the request is in flight, and a slow answer for a page they have
  already left arrives and overwrites the one they are looking at.

`KalendarEventLoader` is the alternative. It watches the state the calendar is navigating with, asks
for the range that is about to be drawn plus a page either side, and hands the view what has arrived
along with the status of what has not.

## The two halves

A [`KalendarEventSource`](reference/kalendar-state.md) answers one question — *what events fall in
this range* — and knows nothing about paging, scrolling or what is on screen. The loader does the
rest: it decides which ranges to ask for, when to stop caring about one, and what to keep.

```kotlin
import com.himanshoe.kalendar.KalendarMonth
import com.himanshoe.kalendar.state.KalendarEventSource
import com.himanshoe.kalendar.state.rememberKalendarEventLoader
import com.himanshoe.kalendar.state.rememberKalendarMonthState

@Composable
fun BookingCalendar(dao: BookingDao) {
    val monthState = rememberKalendarMonthState()
    val loader = rememberKalendarEventLoader(
        state = monthState,
        source = KalendarEventSource { range ->
            dao.observeBetween(from = range.start, to = range.endInclusive)
        },
    )

    KalendarMonth(
        selectedDate = monthState.visibleDate,
        state = monthState,
        eventLoader = loader,
    )
}
```

Three things about that call are load-bearing:

- **`state` and the view must be the same instance.** The loader follows the state it is given; hand
  the view a different one and the loader spends its time fetching ranges nobody is looking at.
- **`eventLoader` replaces `events`, it does not accompany it.** The `events` parameter defaults to
  `eventLoader?.events`, so passing the loader is enough. ([`KalendarAgenda`](views/agenda.md) is the
  one exception — see [below](#kalendaragenda).)
- **`source` is a parameter, not a trailing lambda.** `prefetchPages` follows it in the signature, so
  `rememberKalendarEventLoader(state = monthState) { range -> … }` does not compile. Pass it by name.

`KalendarEventSource` is a `fun interface`, which is why `KalendarEventSource { range -> … }` reads
as a lambda. Its return type is a `Flow<List<E>>`: a source that re-emits keeps the calendar current
on its own, which is exactly the shape a Room query already has. For a one-shot load — a network
call, a `suspend` DAO — wrap it with `suspending`:

```kotlin
val loader = rememberKalendarEventLoader(
    state = monthState,
    source = KalendarEventSource.suspending { range ->
        api.bookings(from = range.start, to = range.endInclusive)
    },
)
```

You do not need to cancel anything inside a source. When the user swipes past a range, the loader
cancels the collection of that range's flow, and a `suspend` call inside it is cancelled with it.

## The range a view asks for is the grid, not the month

This is the part that is most often got wrong in a hand-rolled version, and it produces a bug that
looks like a rendering fault rather than a fetching one.

A month grid draws whole weeks. March 2026 begins on a Sunday, so a Monday-start grid for it opens
on **Monday 23 February** and closes on **Sunday 5 April** — six days of February and five of April
are on screen, in cells that show events like any other. A source asked for "March" leaves those
eleven cells blank until some other page happens to load them.

So [`KalendarDateRange`](reference/kalendar-state.md) is what the page can *draw*, not the period it
is named after. Ask your backend for exactly the range you are handed and every visible cell is
covered:

```kotlin
KalendarEventSource { range ->
    dao.observeBetween(from = range.start, to = range.endInclusive)
}
```

`KalendarDateRange` is a `ClosedRange<LocalDate>`, so `date in range` works and `start` /
`endInclusive` read the way every other Kotlin range's do. **Both ends are inclusive** — a query
written with an exclusive upper bound silently drops the last day of every page.

Return every event that *touches* the range, including a multi-day event that started before it. The
views expand spans across the dates they occupy, and can only do that for events they were given: an
event running 28 February to 3 March must come back from the March page's query too, or the bar
starts on the 1st instead of running in from the edge.

## Prefetching is what makes a swipe silent

The loader holds a **window**: the visible page plus `prefetchPages` either side, defaulting to one.

```kotlin
val loader = rememberKalendarEventLoader(
    state = monthState,
    source = source,
    prefetchPages = 2,
)
```

That default is the difference between a calendar that flickers on every swipe and one that does
not. With `prefetchPages = 0` only the visible page is loaded, so every swipe lands on a page whose
events have not been asked for yet and draws a placeholder while they arrive. With the default of
`1`, the page a swipe is heading for was requested when the *previous* page came into view, and has
normally arrived before it is on screen.

Four properties follow from the window, and they are what a hand-rolled implementation usually
misses:

| | |
|---|---|
| **A range in the window is requested once** | Sliding the window by one page re-uses the collections already running for the pages that stay in it; only the page that has just entered is asked for. A page is re-requested only once it has left the window and is returned to — which is the price of the last row. |
| **A jump prefetches its destination** | The window follows both where the pager *is* and where it is *going*, so the today button and the jump picker start loading the destination when the animation begins rather than when it lands. The two neighbourhoods are unioned, not spanned: a thousand-page jump does not request a thousand ranges for a journey that passes through none of them. |
| **Leaving the window cancels** | A range the user has swiped past has its collection cancelled, so a slow answer for a page nobody is looking at is neither waited for nor written back. |
| **Memory is bounded by the window** | Not by how far the user has scrolled. A page that leaves the window is dropped along with its events; coming back to it asks again. |

Raising `prefetchPages` widens all four at once — more requests in flight, more held in memory, fewer
placeholders. `2` is a reasonable ceiling for a month view; the number of *days* per page is what
actually costs you, so a year view's pages are far more expensive than a week view's.

Events reaching the view are de-duplicated by [`KalendarEvent.id`](events.md#stable-identifiers).
Two adjacent ranges both legitimately return a multi-day event that straddles their boundary, and
without an id the calendar has only the event's own `equals` to go on — so **give your events ids**
when a range query can hand the same one back twice.

## A month that is loading must not look like a month that is empty

Once events can be absent-because-pending, "no events" has two meanings and the calendar has to tell
them apart. That is what [`KalendarLoadStatus`](reference/kalendar-state.md) is for:

| Status | Meaning | What the view draws |
|---|---|---|
| `Loading` | The range has been asked for and nothing has come back | `loadingContent` |
| `Loaded` | The range's events arrived. **An empty list here is a real answer** | the events, or a genuinely empty grid |
| `Failed` | The source failed for this range | `loadingContent`, with `error` and `retry` set |

Every view has a `loadingContent` slot, handed a `KalendarLoadingScope`. It defaults to
`KalendarLoadingDefaults.Placeholder` — a wash drawn from `KalendarColors.dayContent`, faint enough
that the dates underneath stay readable — so a calendar with a loader already distinguishes the two
cases without you writing anything.

The placeholder is drawn as a match-parent-size overlay. That is a layout guarantee rather than a
coincidence: it is measured after the page has decided its own size, so events landing changes what
is drawn and never *where*. The grid does not reflow when the events arrive.

There is deliberately **no shimmer** in the default. An indefinitely repeating animation never lets a
Compose test go idle, so a shimmering default would hang the first UI test anyone wrote around a
loading calendar. A slot is the right place for one, where it is opted into.

### Handling failure

The default placeholder draws nothing at all for `Failed`, because a failure needs a sentence in the
reader's language and the library has no string token for one. Handle it in your own slot:

```kotlin
import com.himanshoe.kalendar.KalendarLoadingDefaults
import com.himanshoe.kalendar.state.KalendarLoadStatus

KalendarMonth(
    selectedDate = monthState.visibleDate,
    state = monthState,
    eventLoader = loader,
    loadingContent = { scope ->
        if (scope.status == KalendarLoadStatus.Failed) {
            RetryBanner(
                message = scope.error?.message,
                onRetry = scope.retry,
            )
        } else {
            KalendarLoadingDefaults.Placeholder(scope = scope)
        }
    },
)
```

`scope.retry` asks again for **every** failed range and leaves the ranges that succeeded alone. It is
safe to call at any time: it records the request rather than launching anything, so a loader whose
calendar has left the composition does no work until one is watching it again.

`KalendarLoadingScope` also carries `range`, which is useful when the message should name the dates
that failed rather than say "something went wrong".

### Reading status outside a slot

The loader exposes the same information for a header, a snackbar, or anything else outside the grid:

```kotlin
val loader = rememberKalendarEventLoader(state = monthState, source = source)

if (loader.status == KalendarLoadStatus.Failed) {
    ErrorBar(error = loader.error, onRetry = loader::retry)
}
```

`loader.status` folds the whole window into one value, with `Failed` winning over `Loading` — a
window where one range failed and another is still arriving is a window with something to tell the
user about. For a specific date, `loader.statusAt(date)` and `loader.errorAt(date)` answer per page.

> **Note:** a date outside the window reads as `Loading`, because that is what it is about to be — a
> view only asks about a page it is drawing, and drawing a page is what puts it in the window.

## `KalendarAgenda`

The agenda has no pages, so it is the one view whose `events` parameter stays required even with a
loader. Pass both:

```kotlin
val agendaState = rememberKalendarMonthState()
val loader = rememberKalendarEventLoader(state = agendaState, source = source)

KalendarAgenda(
    events = loader.events,
    eventLoader = loader,
    onEventClick = { booking -> open(booking) },
)
```

`events` is what it lists; `eventLoader` is what tells it whether an empty list is empty or still
filling, so the empty state and the loading state stay distinguishable.

## Your own event type, all the way through

The loader is generic in the event type, so `E` flows from the source into every callback without a
cast:

```kotlin
val loader: KalendarEventLoader<Booking> = rememberKalendarEventLoader(
    state = monthState,
    source = KalendarEventSource { range -> dao.observeBookings(range.start, range.endInclusive) },
)

KalendarMonth(
    selectedDate = monthState.visibleDate,
    state = monthState,
    eventLoader = loader,
    onDayEventClick = { booking -> open(booking.bookingId) },
)
```

`onDayEventClick` receives a `Booking`, and a `dayContent` slot's `scope.events` is `List<Booking>`.
See [Using your own event type](events.md#using-your-own-event-type).

> **Note:** the event type has to come from somewhere. Passing `eventLoader` or `events` supplies it;
> passing neither selects the event-free overload, which has no `onDayEventClick` or `loadingContent`
> at all. A call that passes `loadingContent` and nothing else fails with *Cannot infer type for type
> parameter `E`* — give it `events = emptyList()` or an explicit `KalendarMonth<KalendarEvent>(…)`.

## A source that changes

A source is usually a lambda closing over a repository or a filter, so it is a new instance on every
recomposition. The loader deliberately does **not** rebuild itself when the source instance changes —
keying on it would re-request every visible range on each pass. It re-reads the source for every
range requested *after* the change, and ranges already held keep the collection they started with.

The practical consequence: **changing a filter does not by itself reload what is on screen.** If a
filter change has to be reflected immediately, make it part of the flow the source returns, so the
running collections re-emit:

```kotlin
val loader = rememberKalendarEventLoader(
    state = monthState,
    source = KalendarEventSource { range ->
        // `selectedRoom` is read inside the flow, so a change re-emits on the collections
        // that are already running rather than needing new ones.
        combine(dao.observeBetween(range.start, range.endInclusive), selectedRoom) { events, room ->
            events.filter { it.roomId == room }
        }
    },
)
```

## Choosing between eager and loaded

| | `events` | `eventLoader` |
|---|---|---|
| Events already in memory | ✅ | — |
| A few hundred, loaded once at screen entry | ✅ | — |
| Backed by a database | either | ✅ |
| Backed by a network call | — | ✅ |
| Unbounded scrolling | — | ✅ |

They are alternatives, not layers: the loader sits beside the eager list rather than replacing it, and
a screen that already has its events pays nothing for the loader existing.

## Where to go next

- [**Events**](events.md) — the event model, your own event type, and multi-day spans.
- [**Views**](views/index.md) — which views take an `eventLoader` (all of them).
- [**Performance**](performance.md) — what the views recompute, and when.
- [**API reference**](reference/kalendar-state.md) — `KalendarEventLoader`, `KalendarEventSource`,
  `KalendarDateRange`, `KalendarLoadStatus`.
