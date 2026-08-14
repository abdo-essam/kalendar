# Getting started

This page goes from nothing to a calendar with working selection and events. Every snippet is real
code against the current API.

## 1. Add the dependency

```kotlin
dependencies {
    implementation("com.himanshoe:kalendar:2.0.0-RC3")
}
```

`kalendar` re-exports `kalendar-foundation` (the event model) as an `api` dependency, so this single
line is enough. See [Install](index.md) for the version-catalog and multiplatform forms.

## 2. Your first calendar

Every view takes a `selectedDate` and nothing else is required:

```kotlin
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.himanshoe.kalendar.view.KalendarMonth
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlin.time.Clock

@Composable
fun FirstCalendar() {
    val today = remember { Clock.System.todayIn(TimeZone.currentSystemDefault()) }
    KalendarMonth(selectedDate = today)
}
```

That already gives you a full month grid with a navigation header, arrow buttons, a today button, a
month/year jump picker, swipe paging, and a highlighted `today`.

> **Note:** `selectedDate` does two jobs. It is both the default single-date highlight *and*, when
> you leave `state` at its default, the page the calendar opens on. Pass an explicit `state`
> (step 5) or an explicit `selectedDates` (step 3) to separate the two.

## 3. Add selection

`selectedDates` is a plain hoisted `Set<LocalDate>` — the calendar never owns it, so you decide what
a tap does:

```kotlin
@Composable
fun SelectableCalendar() {
    val today = remember { Clock.System.todayIn(TimeZone.currentSystemDefault()) }
    var selected by remember { mutableStateOf(setOf(today)) }

    KalendarMonth(
        selectedDate = today,
        selectedDates = selected,
        onDateClick = { date, eventsOnDate ->
            selected = setOf(date)
            println("Tapped $date, which has ${eventsOnDate.size} events")
        },
    )
}
```

For the three common behaviours — single, multiple, range — `rememberKalendarSelectionState`
implements the tap logic for you and survives configuration changes and process death:

```kotlin
import com.himanshoe.kalendar.view.KalendarSelectionMode
import com.himanshoe.kalendar.view.rememberKalendarSelectionState

@Composable
fun RangeCalendar() {
    val today = remember { Clock.System.todayIn(TimeZone.currentSystemDefault()) }
    val selection = rememberKalendarSelectionState(mode = KalendarSelectionMode.Range)

    KalendarMonth(
        selectedDate = today,
        selectedDates = selection.selectedDates,
        onDateClick = { date, _ -> selection.onDateClick(date) },
    )
}
```

| Mode | Tap behaviour |
|---|---|
| `KalendarSelectionMode.Single` (default) | Replaces the selection with the tapped date. |
| `KalendarSelectionMode.Multiple` | Toggles the tapped date in or out of the selection. |
| `KalendarSelectionMode.Range` | First tap starts a range, second completes it (inclusive, in either tap order). The next tap starts a fresh range. |

`rememberKalendarSelectionState` also takes an `initialSelection: Set<LocalDate>`, defaulting to
empty. `selection.clear()` resets everything, including a half-finished range. Changing `mode`
resets the state.

For anything the three modes do not cover — a minimum range length, disallowed end dates — manage
`selectedDates` yourself; it is just a `Set`.

> **Tip:** hoist the set, don't rebuild it. Pass a `remember`ed or state-backed `Set<LocalDate>`.
> Building a fresh set inline on every recomposition defeats the views' `remember` keys and makes
> the grid recompose more than it needs to.

## 4. Add events

Events implement `KalendarEvent`. `BasicKalendarEvent` is the ready-made implementation:

```kotlin
import androidx.compose.ui.graphics.Color
import com.himanshoe.kalendar.foundation.event.BasicKalendarEvent
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime

val events = listOf(
    BasicKalendarEvent(
        date = LocalDate(2026, 8, 12),
        eventName = "Team standup",
        startTime = LocalDateTime(LocalDate(2026, 8, 12), LocalTime(9, 0)),
        endTime = LocalDateTime(LocalDate(2026, 8, 12), LocalTime(9, 30)),
        eventColor = Color(0xFF4CAF50),
    ),
    BasicKalendarEvent(
        date = LocalDate(2026, 8, 14),
        endDate = LocalDate(2026, 8, 16),
        eventName = "Conference",
    ),
)

KalendarMonth(selectedDate = today, events = events)
```

On a grid view each event becomes an indicator dot under the day number, tinted by `eventColor`. The
`Conference` event has an `endDate`, so its dot appears on the 14th, 15th, and 16th. On the
[Schedule views](views/schedule.md) the same list renders as duration-sized blocks instead.

Full details on the event model, multi-day spans, the `+N` overflow, and drag-to-reschedule are on
the [Events](events.md) page.

## 5. Own the navigation state

Every view has a matching `remember*State` factory. Hoist it when you want to read the visible date
or navigate from your own UI:

```kotlin
import androidx.compose.runtime.rememberCoroutineScope
import com.himanshoe.kalendar.view.rememberKalendarMonthState
import kotlinx.coroutines.launch
import kotlinx.datetime.DayOfWeek

@Composable
fun StatefulCalendar() {
    val today = remember { Clock.System.todayIn(TimeZone.currentSystemDefault()) }
    val state = rememberKalendarMonthState(
        initialDate = today,
        startDayOfWeek = DayOfWeek.SUNDAY,
        minDate = LocalDate(2020, 1, 1),
        maxDate = LocalDate(2030, 12, 31),
    )
    val scope = rememberCoroutineScope()

    Text(text = "${state.visibleDate.month} ${state.visibleDate.year}")

    KalendarMonth(selectedDate = today, state = state)

    Button(onClick = { scope.launch { state.animateScrollTo(LocalDate(2026, 12, 25)) } }) {
        Text("Jump to December")
    }
}
```

`state.canScrollBackward` and `state.canScrollForward` are public too, so your own chrome can
disable its buttons at the same bounds the built-in header does.

## 6. Style it

Colours, type, shapes, sizing, motion, and strings all resolve through an ambient token set. In a
Material 3 app the defaults already follow your colour scheme, so you may not need this at all:

```kotlin
import com.himanshoe.kalendar.view.KalendarTheme
import com.himanshoe.kalendar.view.KalendarThemeDefaults

KalendarTheme(
    colors = KalendarThemeDefaults.colors().copy(selectionBackground = BrandPurple),
) {
    KalendarMonth(selectedDate = today)
}
```

If your app does **not** use Material 3, pass `KalendarThemeDefaults.systemColors()` instead — see
[Theming](theming.md).

## Next steps

- [**Views**](views/index.md) — pick the right composable and see its full parameter table.
- [**Theming**](theming.md) — every token and its default.
- [**Configuration**](configuration.md) — the `KalendarViewConfig` knobs shared by all seven views.
- [**Customization**](customization.md) — replace the day cell, the header, or an event block.
- [**Device sync**](sync.md) — read and write the user's real calendar.
