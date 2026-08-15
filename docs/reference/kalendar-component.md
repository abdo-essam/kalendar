# `kalendar.component`

`com.himanshoe.kalendar.component`

The two default-rendering objects behind the date-grid views' day cell and header, and the modifier
that gives a custom cell its accessibility and click handling back.

```kotlin
import com.himanshoe.kalendar.component.KalendarDayCellDefaults
```

Everything else in this package — `KalendarGrid` and `KalendarStaticGrid`, `KalendarBlockLayout`,
`KalendarNavigationHeader`, the hour-grid and lane-grid parts, the keyboard-navigation plumbing — is
`internal`. It is where measurement, positioning, paging and gestures live, and it is deliberately
not part of the compatible API: those are the parts a view *keeps* so a slot never has to reimplement
them.

## `Modifier.kalendarDaySemantics`

```kotlin
@Composable
public fun Modifier.kalendarDaySemantics(
    scope: KalendarDayScope<*>,
    onClick: () -> Unit,
    monthNameFormatter: (Month) -> String = KalendarFormatters.monthName,
    interactionSource: MutableInteractionSource? = null,
): Modifier
```

A custom `dayContent` replaces the built-in cell — **including its accessibility and its click
handling**. This modifier gives both back.

| Parameter | Type | Default | Description |
|---|---|---|---|
| `scope` | `KalendarDayScope<*>` | — | The cell's state, as handed to the slot. Star-projected: this modifier reads the interface only. |
| `onClick` | `() -> Unit` | — | Called when the cell is tapped and the date is **not** disabled. |
| `monthNameFormatter` | `(Month) -> String` | `KalendarFormatters.monthName` | Formats the month name inside the announcement. Pass `config.monthNameFormatter` to match a localized calendar. |
| `interactionSource` | `MutableInteractionSource?` | `null` | Supply one to observe press, focus and hover state yourself — which is how you rebuild the focus ring and hover wash a custom cell also loses. |

Applied to your cell's outermost modifier, it adds:

- a screen-reader `contentDescription` built from the date, plus `KalendarStrings.todaySuffix` when
  the date is today and `KalendarStrings.hasEventsSuffix` when it has events — e.g.
  `"August 12, 2026, today, has events"`;
- the `selected` semantic state, from `scope.isSelected`;
- `Role.Button`, so assistive technology announces the cell correctly;
- a click that is **ignored when `scope.isDisabled` is `true`**.

```kotlin
KalendarMonth(
    selectedDate = today,
    onDateClick = { date, _ -> select(date) },
    dayContent = { scope ->
        MyDayCell(
            day = scope.date.day,
            modifier = Modifier.kalendarDaySemantics(scope = scope, onClick = { select(scope.date) }),
        )
    },
)
```

Because the announcements come from [`KalendarStrings`](kalendar-theme.md), a cell using this stays
localizable through the same theme mechanism as the built-in one. A hand-rolled `contentDescription`
does not.

Rebuilding the focus ring and hover wash:

```kotlin
val interactionSource = remember { MutableInteractionSource() }
val isFocused by interactionSource.collectIsFocusedAsState()
val isHovered by interactionSource.collectIsHoveredAsState()

MyDayCell(
    modifier = Modifier
        .background(if (isHovered) KalendarTheme.colors.hoverBackground else Color.Transparent)
        .border(
            width = 2.dp,
            color = if (isFocused) KalendarTheme.colors.focusIndicator else Color.Transparent,
            shape = KalendarTheme.shapes.dayCell,
        )
        .kalendarDaySemantics(
            scope = scope,
            onClick = { select(scope.date) },
            interactionSource = interactionSource,
        ),
)
```

## `KalendarDayCellDefaults`

```kotlin
public object KalendarDayCellDefaults
```

The exact composables the date-grid views fall back to, so a custom slot can **wrap** the built-in
cell rather than reimplement it.

### `Cell`

```kotlin
@Composable
public fun <E : KalendarEvent> Cell(
    scope: KalendarDayScope<E>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showSelectionBackground: Boolean = true,
    monthNameFormatter: (Month) -> String = KalendarFormatters.monthName,
    maxEventIndicators: Int = 3,
    onEventClick: (E) -> Unit = {},
    showOverflowPopup: Boolean = true,
    overflowPopup: @Composable (KalendarDayOverflowScope<E>) -> Unit = { OverflowPopup(scope = it) },
)
```

| Parameter | Type | Default | Description |
|---|---|---|---|
| `scope` | `KalendarDayScope<E>` | — | The cell's state. |
| `onClick` | `() -> Unit` | — | Called when the cell is tapped and the date is not disabled. |
| `modifier` | `Modifier` | `Modifier` | Applied to the cell. |
| `showSelectionBackground` | `Boolean` | `true` | Whether this cell draws its own selection fill. The views pass `false` when the sliding indicator is already drawing it. |
| `monthNameFormatter` | `(Month) -> String` | English month names | Used for the accessibility description. |
| `maxEventIndicators` | `Int` | `3` | Dots drawn before the remainder collapses into `+N`. Counts only the events drawn as dots — a multi-day event that got a [span bar](../events.md#multi-day-events) lane is not one of them. The views pass `KalendarViewConfig.eventIndicatorCap`. |
| `onEventClick` | `(E) -> Unit` | `{}` | Called when an event is tapped inside the overflow popover — the calendar's own event type. |
| `showOverflowPopup` | `Boolean` | `true` | Whether the `+N` label opens a popover at all. `false` leaves it as a plain count. |
| `overflowPopup` | `@Composable (KalendarDayOverflowScope<E>) -> Unit` | `OverflowPopup` | Replaces the popover. **Last**, so trailing-lambda syntax lands on it. |

```kotlin
KalendarMonth(
    selectedDate = today,
    dayContent = { scope ->
        Box {
            KalendarDayCellDefaults.Cell(scope = scope, onClick = { select(scope.date) })
            if (scope.date in holidays) HolidayBadge(modifier = Modifier.align(Alignment.TopEnd))
        }
    },
)
```

> **Note:** span-bar lanes are assigned by the surrounding grid from the whole event list, because a
> lane cannot be decided from one day's events alone. A `Cell` composed **outside** any grid
> therefore has no lane assignment to read and draws its multi-day events as dots. Inside a
> `dayContent` slot — as above — it is still inside the grid, so the bars are drawn normally.

### `OverflowPopup`

```kotlin
@Composable
public fun OverflowPopup(
    scope: KalendarDayOverflowScope<*>,
    modifier: Modifier = Modifier,
)
```

The built-in `+N` popover: the date's whole event list, dismissed by tapping outside or pressing
escape.

| Parameter | Type | Default | Description |
|---|---|---|---|
| `scope` | [`KalendarDayOverflowScope<*>`](kalendar-slot.md) | — | The date, its events, and the hidden count. |
| `modifier` | `Modifier` | `Modifier` | Applied to the popover surface. |

```kotlin
overflowPopup = { scope -> KalendarDayCellDefaults.OverflowPopup(scope = scope) }
```

## `KalendarHeaderDefaults`

```kotlin
public object KalendarHeaderDefaults
```

The chrome around a date grid.

### `Header`

```kotlin
@Composable
public fun Header(
    scope: KalendarHeaderScope,
    modifier: Modifier = Modifier,
    showNavigationArrows: Boolean = true,
    showTodayButton: Boolean = true,
    showJumpPicker: Boolean = true,
    monthNameFormatter: (Month) -> String = KalendarFormatters.monthName,
    shortMonthNameFormatter: (Month) -> String = KalendarFormatters.shortMonthName,
    previousIcon: @Composable () -> Unit = { … },
    nextIcon: @Composable () -> Unit = { … },
    todayIcon: @Composable () -> Unit = { … },
    actions: @Composable RowScope.() -> Unit = {},
)
```

The default `header` on `KalendarWeek`, `KalendarMonth` and `KalendarYear` — and usable in
`KalendarTimeline`'s slot too, since every control drives the `scope` rather than any particular
state type.

| Parameter | Type | Default | Description |
|---|---|---|---|
| `scope` | `KalendarHeaderScope` | — | Inside a `header` slot, the value the slot was handed. |
| `modifier` | `Modifier` | `Modifier` | Applied to the header's outermost container. |
| `showNavigationArrows` | `Boolean` | `true` | Whether to draw the previous/next buttons. Each disables itself when the matching `canScroll*` is `false`. |
| `showTodayButton` | `Boolean` | `true` | Whether to draw the today button. |
| `showJumpPicker` | `Boolean` | `true` | Whether tapping the title opens the month/year jump picker. |
| `monthNameFormatter` | `(Month) -> String` | English month names | The title's month name. |
| `shortMonthNameFormatter` | `(Month) -> String` | first three letters | The jump picker's fixed-width month buttons, and the week/day titles. |
| `previousIcon` | `@Composable () -> Unit` | a Foundation-drawn chevron | The **glyph only** inside the previous-page button. |
| `nextIcon` | `@Composable () -> Unit` | the same chevron, reversed | The glyph inside the next-page button. |
| `todayIcon` | `@Composable () -> Unit` | a Foundation-drawn calendar page | The glyph inside the today button. |
| `actions` | `@Composable RowScope.() -> Unit` | `{}` | Extra controls appended to the header's trailing edge, inside its `Row`. |

```kotlin
// Keep the built-in header, but without the jump picker.
header = { scope -> KalendarHeaderDefaults.Header(scope = scope, showJumpPicker = false) }
```

> **Note:** the icon slots draw the **glyph only**. The button around it — its touch target, its
> click handler, and its accessibility label from `KalendarStrings` — belongs to the header. Pass
> `contentDescription = null` on your icon, or a screen reader announces the control twice.

Two consequences: the default icons are private, so an icon slot is all-or-nothing per icon; and the
default chevrons mirror under RTL automatically, whereas a replacement is your composable and
mirroring it is yours.

### `TimelineHeader`

```kotlin
@Composable
public fun TimelineHeader(
    scope: KalendarHeaderScope,
    modifier: Modifier = Modifier,
    showTodayButton: Boolean = true,
    background: Brush? = null,
)
```

`KalendarTimeline`'s separate default: a leading-aligned title, no arrows, an optional today button,
and a `background` brush for the **opaque fill the overlay needs** — without one, the months scroll
visibly underneath it.

| Parameter | Type | Default | Description |
|---|---|---|---|
| `scope` | `KalendarHeaderScope` | — | The value the slot was handed. On a timeline its page vocabulary is mapped onto months. |
| `modifier` | `Modifier` | `Modifier` | Applied to the sticky container. |
| `showTodayButton` | `Boolean` | `true` | Whether to draw the today button. |
| `background` | `Brush?` | `null` | The overlay's fill. `null` means `KalendarColors.background`. |

```kotlin
header = { scope ->
    KalendarHeaderDefaults.TimelineHeader(scope = scope, background = config.background)
}
```

### `DayOfWeekLabel`

```kotlin
@Composable
public fun DayOfWeekLabel(
    dayOfWeek: DayOfWeek,
    modifier: Modifier = Modifier,
    formatter: (DayOfWeek) -> String = KalendarFormatters.dayOfWeekLabel,
)
```

One weekday column header's content, styled with `KalendarTypography.dayOfWeekLabel` and announced
with `KalendarStrings.dayOfWeekAccessibilityLabel`. Shared by every view that has columns, including
`KalendarDatePicker` — a column initial is a column initial.

| Parameter | Type | Default | Description |
|---|---|---|---|
| `dayOfWeek` | `DayOfWeek` | — | The weekday this column is. |
| `modifier` | `Modifier` | `Modifier` | Applied to the label. The grid still measures and places one per column. |
| `formatter` | `(DayOfWeek) -> String` | first letter | The text to draw. |

```kotlin
dayOfWeekLabel = { day ->
    KalendarHeaderDefaults.DayOfWeekLabel(dayOfWeek = day, formatter = { it.name.take(2) })
}
```

For a locale-aware label, set [`KalendarViewConfig.dayOfWeekLabelFormatter`](kalendar-views.md)
instead — no slot needed.
