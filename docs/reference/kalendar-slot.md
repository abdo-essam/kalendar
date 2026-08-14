# `kalendar.slot`

`com.himanshoe.kalendar.slot`

Every scope type handed to a content slot. A slot receives a scope object rather than positional
lambda parameters so that a later release can add a field without breaking every existing call site.

```kotlin
import com.himanshoe.kalendar.slot.KalendarDayScope
```

The interfaces here are interfaces for the same reason, plus one more: they carry *behaviour*
(`goToNextPage()`, `dismiss()`), which is not a data-class shape.

[Customization](../customization.md) is the narrative version of this page.

## `KalendarDayScope`

```kotlin
@Immutable
public class KalendarDayScope(
    public val date: LocalDate,
    public val isSelected: Boolean,
    public val isToday: Boolean,
    public val isDisabled: Boolean,
    public val events: List<KalendarEvent>,
)
```

What a `dayContent` slot is given, on `KalendarWeek`, `KalendarMonth`, `KalendarYear` and
`KalendarTimeline` — and, wrapped in [`KalendarDatePickerDayScope`](kalendar-views.md), on
`KalendarDatePicker`.

| Property | Type | Description |
|---|---|---|
| `date` | `LocalDate` | The date this cell represents. |
| `isSelected` | `Boolean` | Whether `date` is in the calendar's `selectedDates`. |
| `isToday` | `Boolean` | Whether `date` is today, as read through the view's `KalendarTimeSource`. |
| `isDisabled` | `Boolean` | Whether `date` matched `KalendarViewConfig.disabledDates`. On the month-grid views this is **also** `true` for adjacent-month padding dates. |
| `events` | `List<KalendarEvent>` | The events occurring on `date`, multi-day spans included. |
| `copy(…)` | `KalendarDayScope` | A duplicate with only the fields passed here replaced. |

```kotlin
KalendarMonth(
    selectedDate = today,
    dayContent = { scope ->
        MyDayCell(
            day = scope.date.day,
            highlighted = scope.isSelected,
            muted = scope.isDisabled,
            dots = scope.events.size,
            modifier = Modifier.kalendarDaySemantics(scope = scope, onClick = { select(scope.date) }),
        )
    },
)
```

> **Note:** to tell an adjacent-month padding date from a genuinely disabled one, compare
> `scope.date.month` against the month being drawn — `isDisabled` alone cannot distinguish them.

## `KalendarDayOverflowScope`

```kotlin
@Stable
public interface KalendarDayOverflowScope
```

What a day cell's `+N` overflow popup is given, when a date has more events than
`KalendarViewConfig.eventIndicatorCap` and the user opens the remainder.

| Member | Type | Description |
|---|---|---|
| `date` | `LocalDate` | The date whose events are listed. |
| `events` | `List<KalendarEvent>` | **Every** event on `date`, in the order the cell received them — not just the hidden tail. A popover opened from a `+2` that showed the fourth and fifth without the first three would be a list with no beginning. |
| `hiddenEventCount` | `Int` | The `N` in the `+N` label — how many the indicator dots did not represent. Always at least 1 while the popover is open. |
| `onEventClick(event)` | `Unit` | Reports the event to the cell's `onEventClick` and closes the popover, in that order. A popover that wants to stay open should call its own callback instead. |
| `dismiss()` | `Unit` | Closes the popover. Tapping outside and pressing escape already do this; call it for a close button of your own. |

```kotlin
overflowPopup = { scope ->
    MySheet(onDismiss = scope::dismiss) {
        scope.events.forEach { event ->
            MyEventRow(event = event, onClick = { scope.onEventClick(event) })
        }
    }
}
```

The slot lives on [`KalendarDayCellDefaults.Cell`](kalendar-component.md), which is what the views'
default `dayContent` calls.

## `KalendarHeaderScope`

```kotlin
@Stable
public interface KalendarHeaderScope
```

What a `header` slot is given: the formatted title and the navigation callbacks, so your own chrome
keeps working arrows, today button and jump behaviour.

| Member | Type | Description |
|---|---|---|
| `title` | `String` | The title the built-in header would show, already formatted — e.g. `"August 2026"`. |
| `visibleDate` | `LocalDate` | The start date of the page currently on screen. |
| `canScrollBackward` | `Boolean` | Whether a previous page exists within the calendar's `minDate` bound. |
| `canScrollForward` | `Boolean` | Whether a next page exists within `maxDate`. |
| `goToPreviousPage()` | `Unit` | Animates to the previous week/month/year/day. A no-op when `canScrollBackward` is `false`. |
| `goToNextPage()` | `Unit` | Animates to the next page. A no-op when `canScrollForward` is `false`. |
| `goToToday()` | `Unit` | Animates back to the page containing today, clamped to the bounds. |
| `goTo(date)` | `Unit` | Animates to the page containing `date`, clamped. |

The four navigation functions are **plain, non-suspending calls** — each launches the underlying
`suspend` animation on a scope the view owns — so a header slot can navigate straight from an
`onClick` without managing a coroutine scope of its own.

```kotlin
KalendarMonth(
    selectedDate = today,
    header = { scope ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            MyIconButton(MyIcons.ChevronLeft, enabled = scope.canScrollBackward, onClick = scope::goToPreviousPage)
            Text(text = scope.title, modifier = Modifier.weight(1f))
            MyIconButton(MyIcons.ChevronRight, enabled = scope.canScrollForward, onClick = scope::goToNextPage)
            MyTextButton(text = "Today", onClick = scope::goToToday)
        }
    },
)
```

On [`KalendarTimeline`](kalendar-views.md) the same interface is mapped onto **months**, since a
timeline has no pages — and a timeline header must paint an opaque background of its own, or the
months scroll visibly underneath it.

## `KalendarScheduleEventScope`

```kotlin
@Stable
public interface KalendarScheduleEventScope
```

What an `eventContent` slot is given on `KalendarSchedule` and `KalendarScheduleWeek`. The slot is
handed a box already positioned and sized for the event's duration and its share of any overlap, so
an implementation only **draws** — it never positions.

| Member | Type | Description |
|---|---|---|
| `event` | `KalendarEvent` | The event being rendered. |
| `start` | `LocalDateTime` | The event's start, clamped to the rendered day when it began earlier. |
| `end` | `LocalDateTime` | The event's end, clamped to the rendered day when it runs past midnight. |
| `durationMinutes` | `Int` | How many minutes of the rendered day this block covers, after clamping. |
| `isDragging` | `Boolean` | Whether the user is currently dragging or resizing this block. |
| `overlapColumn` | `Int` | Zero-based column among events that overlap this one. `0` when nothing overlaps. |
| `overlapColumns` | `Int` | How many events share this block's span, including itself. `1` when nothing overlaps. |

`durationMinutes` and `overlapColumns` are there so you can adapt density — a short block or a
crowded column has no room for a subtitle:

```kotlin
eventContent = { scope ->
    if (scope.durationMinutes >= 45 && scope.overlapColumns == 1) {
        MyRichEventCard(scope)
    } else {
        KalendarScheduleDefaults.EventBlock(scope = scope)
    }
}
```

`overlapColumn`/`overlapColumns` are the same two numbers
[`KalendarScheduleBlock`](foundation-schedule.md) carries — the view has already applied them to the
box's width, so you only need them if you want to change *what* you draw at a given density.

## `KalendarResourceEventScope`

```kotlin
@Stable
public interface KalendarResourceEventScope : KalendarScheduleEventScope
```

`KalendarResourceView`'s `eventContent` scope: everything above, plus which lane the block is in.

| Member | Type | Description |
|---|---|---|
| `resource` | `KalendarResource` | The lane this block sits in — its `id`, `title`, `color` and `order`. |

Because it *extends* the schedule scope, a slot written as
`@Composable (KalendarScheduleEventScope) -> Unit` can be passed straight to
`KalendarResourceView.eventContent` by contravariance — one event renderer serves both views.

```kotlin
eventContent = { scope ->
    MyBookingCard(
        title = scope.event.eventName,
        room = scope.resource.title,
        accent = scope.resource.color,
        dimmed = scope.isDragging,
    )
}
```
