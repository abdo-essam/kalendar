# `kalendar.theme`

`com.himanshoe.kalendar.theme`

Six token sets, the composable that provides them, the object that reads them, and the built-in
values. Nothing a view draws is hard-coded, and nothing reads `MaterialTheme` at render time.

```kotlin
import com.himanshoe.kalendar.theme.KalendarTheme
```

[Theming](../theming.md) is the narrative version of this page, with every default value and every
palette. This page is the signature-level reference.

> Kalendar reads Material **only to derive default token values**, inside `KalendarThemeDefaults`.
> No Material widget renders the calendar. Replace the defaults and Material is out of the picture.

## `KalendarTheme` — the composable

```kotlin
@Composable
public fun KalendarTheme(
    colors: KalendarColors = KalendarTheme.colors,
    typography: KalendarTypography = KalendarTheme.typography,
    shapes: KalendarShapes = KalendarTheme.shapes,
    dimensions: KalendarDimensions = KalendarTheme.dimensions,
    animations: KalendarAnimations = KalendarTheme.animations,
    strings: KalendarStrings = KalendarTheme.strings,
    content: @Composable () -> Unit,
)
```

Provides tokens to everything inside. Each parameter defaults to the **ambient** value, so overriding
one leaves the other five as they were — including inside a nested theme.

```kotlin
KalendarTheme(colors = KalendarThemeDefaults.colors().copy(selectionBackground = BrandPurple)) {
    KalendarMonth(selectedDate = today)
}
```

Wrapping is optional: a view used outside any `KalendarTheme` falls back to `KalendarThemeDefaults`,
so reading a token is always safe.

## `KalendarTheme` — the object

```kotlin
public object KalendarTheme
```

The accessors that **read** the ambient tokens. All are `@Composable` getters.

| Accessor | Type |
|---|---|
| `KalendarTheme.colors` | `KalendarColors` |
| `KalendarTheme.typography` | `KalendarTypography` |
| `KalendarTheme.shapes` | `KalendarShapes` |
| `KalendarTheme.dimensions` | `KalendarDimensions` |
| `KalendarTheme.animations` | `KalendarAnimations` |
| `KalendarTheme.strings` | `KalendarStrings` |
| `KalendarTheme.tokens` | `KalendarThemeTokens` — all six at once |

```kotlin
@Composable
fun MyDayCell(scope: KalendarDayScope<*>) {
    Text(
        text = scope.date.day.toString(),
        color = if (scope.isToday) KalendarTheme.colors.todayContent else KalendarTheme.colors.dayContent,
        style = KalendarTheme.typography.dayNumber,
    )
}
```

> **Warning:** to layer on top of what is already ambient, copy the **accessor**, not the defaults:
> `KalendarTheme(dimensions = KalendarTheme.dimensions.copy(hourHeight = 96.dp))`. Constructing a
> token set directly resets every field you did not name back to the built-in default, which inside a
> nested theme silently discards the outer theme's values.

## `KalendarThemeDefaults`

```kotlin
public object KalendarThemeDefaults
```

The built-in token values, and the three palettes.

| Function | Composable | Returns | Description |
|---|---|---|---|
| `colors()` | yes | `KalendarColors` | Derived from the ambient `MaterialTheme.colorScheme`. The default. |
| `systemColors()` | yes | `KalendarColors` | Kalendar's own palette, following the system light/dark setting via `isSystemInDarkTheme()`. **No Material dependency.** |
| `lightColors()` | no | `KalendarColors` | The light palette, chosen explicitly. |
| `darkColors()` | no | `KalendarColors` | The dark palette, chosen explicitly. |
| `typography()` | yes | `KalendarTypography` | Derived from `MaterialTheme.typography`. |
| `shapes()` | no | `KalendarShapes` | The built-in shapes. |
| `dimensions()` | no | `KalendarDimensions` | The built-in sizes. |
| `animations()` | no | `KalendarAnimations` | The built-in motion specs. |
| `strings()` | no | `KalendarStrings` | The built-in English strings. |

```kotlin
// An app without Material 3: MaterialTheme.colorScheme falls back to the baseline *light* scheme,
// so a calendar would stay light in dark mode. Use Kalendar's own palette instead.
KalendarTheme(colors = KalendarThemeDefaults.systemColors()) {
    KalendarMonth(selectedDate = today)
}
```

## `KalendarThemeTokens`

```kotlin
@Immutable
public class KalendarThemeTokens(
    public val colors: KalendarColors,
    public val typography: KalendarTypography,
    public val shapes: KalendarShapes,
    public val dimensions: KalendarDimensions,
    public val animations: KalendarAnimations,
    public val strings: KalendarStrings,
)
```

All six sets bundled into one value, so a whole look can be built once, passed around, and copied.

| Member | Returns | Description |
|---|---|---|
| `colors`, `typography`, `shapes`, `dimensions`, `animations`, `strings` | the six token sets | The bundled values. |
| `copy(…)` | `KalendarThemeTokens` | A duplicate with only the sets passed here replaced. |

```kotlin
@Composable
fun rememberBrandTokens(): KalendarThemeTokens =
    KalendarTheme.tokens.copy(
        colors = KalendarThemeDefaults.systemColors().copy(selectionBackground = BrandPurple),
        shapes = KalendarTheme.shapes.copy(dayCell = RoundedCornerShape(8.dp)),
        animations = KalendarAnimations(enabled = false),
    )
```

## `KalendarColors`

```kotlin
@Immutable
public class KalendarColors(
    public val background: Color,
    public val selectionBackground: Color,
    public val onSelectionBackground: Color,
    public val todayContent: Color,
    public val dayContent: Color,
    public val dayLabelContent: Color,
    public val headerContent: Color,
    public val eventIndicator: Color,
    public val nowIndicator: Color,
    public val gridLine: Color,
    public val dragGhostBackground: Color,
    public val popupBackground: Color,
    public val popupBorder: Color,
    public val agendaCard: Color,
    public val headerContentDisabled: Color = headerContent.copy(alpha = 0.38f),
    public val focusIndicator: Color = todayContent,
    public val hoverBackground: Color = dayContent.copy(alpha = 0.08f),
    public val disabledContentAlpha: Float = 0.38f,
    public val eventBlockAlpha: Float = 0.18f,
    public val eventBlockDraggingAlpha: Float = 0.45f,
    public val dragGhostAlpha: Float = 0.6f,
)
```

Fourteen required colours, three derived colours and four alphas.

| Property | Type | Used for |
|---|---|---|
| `background` | `Color` | Fill behind the whole calendar. |
| `selectionBackground` | `Color` | The selected day's fill and the sliding indicator. |
| `onSelectionBackground` | `Color` | Content on top of `selectionBackground`. |
| `todayContent` | `Color` | Today's day number when it is not selected. |
| `dayContent` | `Color` | Ordinary day numbers. |
| `dayLabelContent` | `Color` | Day-of-week column labels and hour-gutter labels. |
| `headerContent` | `Color` | The navigation header's title and icons. |
| `eventIndicator` | `Color` | Event dots and blocks when `KalendarEvent.eventColor` is `null`. |
| `nowIndicator` | `Color` | The current-time line on the hour grids. |
| `gridLine` | `Color` | Hour lines and separators. |
| `dragGhostBackground` | `Color` | The drop-target ghost while dragging an event. |
| `popupBackground` | `Color` | Fill of the month/year jump picker. |
| `popupBorder` | `Color` | Outline around the jump picker. |
| `agendaCard` | `Color` | Fill of one `KalendarAgenda` event row. |
| `headerContentDisabled` | `Color` | A header arrow blocked by `minDate`/`maxDate`. |
| `focusIndicator` | `Color` | The ring around a keyboard-focused day cell. |
| `hoverBackground` | `Color` | The wash over a day cell under the pointer. |
| `disabledContentAlpha` | `Float` | Dates rejected by `disabledDates`, and adjacent-month dates. |
| `eventBlockAlpha` | `Float` | An event block's fill over its accent colour. Stripe and text stay opaque. |
| `eventBlockDraggingAlpha` | `Float` | An event block's fill while being dragged. |
| `dragGhostAlpha` | `Float` | Applied to `dragGhostBackground`. |
| `copy(…)` | `KalendarColors` | A duplicate with only the values passed here replaced. |

The fourteen colours at the top have **no defaults**, so the compiler tells you when a release adds
one rather than letting a new surface silently fall back to something arbitrary.

```kotlin
KalendarTheme(colors = KalendarTheme.colors.copy(nowIndicator = BrandRed)) {
    KalendarSchedule(events = events)
}
```

## `KalendarTypography`

```kotlin
@Immutable
public class KalendarTypography(
    public val headerTitle: TextStyle,
    public val monthLabel: TextStyle,
    public val dayOfWeekLabel: TextStyle,
    public val dayNumber: TextStyle,
    public val dayNumberEmphasized: TextStyle,
    public val weekDayNumber: TextStyle,
    public val resourceTitle: TextStyle,
    public val eventLabel: TextStyle,
    public val eventOverflowLabel: TextStyle,
    public val hourLabel: TextStyle,
    public val agendaEventTitle: TextStyle,
    public val agendaEventSubtitle: TextStyle,
)
```

| Property | Used for |
|---|---|
| `headerTitle` | The navigation header's title, the Timeline's month titles, the jump picker's year. |
| `monthLabel` | Month names inside `KalendarYear`'s grid. |
| `dayOfWeekLabel` | The day-of-week column headers. |
| `dayNumber` | The day number in an ordinary cell. |
| `dayNumberEmphasized` | The day number when the cell is selected or is today. |
| `weekDayNumber` | The date number in `KalendarScheduleWeek`'s day-column headers. |
| `resourceTitle` | A lane's name in `KalendarResourceView`'s column headers. |
| `eventLabel` | Event block titles and all-day chip titles. |
| `eventOverflowLabel` | The compact `+N` label. |
| `hourLabel` | The hour-gutter labels. |
| `agendaEventTitle` | An event's name in `KalendarAgenda`'s rows, and its empty-state message. |
| `agendaEventSubtitle` | An event's description and time range in `KalendarAgenda`'s rows. |
| `copy(…)` | A duplicate with only the styles passed here replaced. |

Every field is required. To drop the selected/today emphasis entirely, set `dayNumberEmphasized`
equal to `dayNumber`:

```kotlin
val type = KalendarTheme.typography
KalendarTheme(typography = type.copy(dayNumberEmphasized = type.dayNumber)) {
    KalendarMonth(selectedDate = today)
}
```

## `KalendarShapes`

```kotlin
@Immutable
public class KalendarShapes(
    public val dayCell: Shape,
    public val eventIndicator: Shape,
    public val eventBlock: Shape,
    public val popup: Shape,
    public val agendaCard: Shape,
)
```

| Property | Used for | Default |
|---|---|---|
| `dayCell` | The day cell's selection fill, the sliding indicator, **and** the drag ghost. | `CircleShape` |
| `eventIndicator` | The per-event dots below a day number. | `CircleShape` |
| `eventBlock` | Event blocks and all-day chips. | `RoundedCornerShape(4.dp)` |
| `popup` | The month/year jump picker surface. | `RoundedCornerShape(12.dp)` |
| `agendaCard` | One `KalendarAgenda` event row. | `RoundedCornerShape(12.dp)` |
| `copy(…)` | A duplicate with only the shapes passed here replaced. | — |

`dayCell` is one token shared by three separate composables that have to agree visually — which is
exactly why it lives on the theme rather than on each composable.

```kotlin
KalendarTheme(shapes = KalendarTheme.shapes.copy(dayCell = RoundedCornerShape(8.dp))) {
    KalendarMonth(selectedDate = today)
}
```

## `KalendarDimensions`

```kotlin
@Immutable
public class KalendarDimensions(…)
```

Every field has a default, so this is the one token set you can construct from scratch as a one-liner.

| Property | Default | Controls |
|---|---|---|
| `dayCellPadding` | `2.dp` | Inset around each day cell, the selection indicator, and the drag ghost. |
| `dayCellAspectRatio` | `1f` | Width-to-height ratio of a day cell. Above `1f` gives short, wide cells. |
| `eventIndicatorSize` | `4.dp` | Diameter of one event dot. |
| `eventIndicatorSpacing` | `2.dp` | Gap between adjacent event dots. |
| `eventIndicatorBottomPadding` | `2.dp` | Gap below the dot row, between it and the cell's bottom edge. |
| `eventIndicatorStripHeight` | `14.dp` | Height reserved for the dot/overflow strip, so a cell with events and one without agree on where the day number sits. |
| `spanBarHeight` | `4.dp` | Thickness of one multi-day event's span bar — the continuous bar drawn across every day an event with an `endDate` occupies. |
| `spanBarSpacing` | `2.dp` | Gap below each span bar, separating it from the next bar down and the lowest bar from the dot row. One lane is `spanBarHeight + spanBarSpacing` tall. |
| `spanBarCornerRadius` | `2.dp` | Corner radius applied to a bar's **real** ends only — the day the event starts and the day it ends. Where a bar is cut by the end of a week it stays square and runs flush to the cell's edge. |
| `spanBarEndInset` | `3.dp` | How far a bar is pulled back from the cell's edge at a real end. The other half of the same distinction; `0.dp` relies on the corner radius alone. |
| `spanBarMaxLanes` | `2` | How many span bars may be stacked in one day cell. A budget for vertical space, not a count of events. `0` disables span bars; the constructor requires `>= 0`. |
| `headerPadding` | `PaddingValues(horizontal = 4.dp, vertical = 8.dp)` | Padding around the navigation header's contents. |
| `headerIconSlotWidth` | `48.dp` | Width reserved on each side of the header title for icons. |
| `headerContentSpacing` | `8.dp` | Gap between the header's title and the controls beside it. |
| `hourHeight` | `64.dp` | Vertical space for one hour on the hour grids. |
| `hourGutterWidth` | `48.dp` | Width of the hour-label gutter, and of the header spacer above it. |
| `gridLineThickness` | `1.dp` | Thickness of hour lines and separators. |
| `nowIndicatorThickness` | `2.dp` | Thickness of the current-time line. |
| `eventBlockGap` | `1.dp` | Horizontal gap between side-by-side event blocks. |
| `eventBlockAccentWidth` | `3.dp` | Width of an event block's leading accent stripe. `0.dp` removes it. |
| `eventBlockPadding` | `PaddingValues(horizontal = 4.dp, vertical = 2.dp)` | Padding around an event block's label. |
| `eventBlockResizeHandleHeight` | `12.dp` | Height of the grab strip at each edge of a block that starts a resize rather than a move. |
| `allDayRowPadding` | `PaddingValues(horizontal = 16.dp, vertical = 4.dp)` | Padding around the all-day chip row. |
| `allDayChipSpacing` | `4.dp` | Gap between all-day chips. |
| `allDayChipPadding` | `PaddingValues(horizontal = 6.dp, vertical = 2.dp)` | Padding inside one all-day chip. |
| `resourceColumnMinWidth` | `96.dp` | Narrowest a `KalendarResourceView` lane may become before the lanes start scrolling horizontally. |
| `resourceHeaderPadding` | `4.dp` | Padding inside a resource column header. |
| `resourceHeaderAccentHeight` | `2.dp` | Thickness of a lane header's accent underline. |
| `monthSpacing` | `16.dp` | Vertical gap between month grids in `KalendarYear`. |
| `sectionPadding` | `PaddingValues(horizontal = 16.dp, vertical = 8.dp)` | Padding around section titles. |
| `agendaRowPadding` | `PaddingValues(horizontal = 12.dp, vertical = 10.dp)` | Padding inside one agenda row. |
| `agendaRowSpacing` | `4.dp` | Vertical gap between agenda rows. |
| `agendaRowGap` | `12.dp` | Gap between an agenda row's accent dot and its text. |
| `agendaDotSize` | `10.dp` | Diameter of an agenda row's accent dot. |
| `copy(…)` | — | A duplicate with only the values passed here replaced. |

```kotlin
KalendarTheme(
    dimensions = KalendarTheme.dimensions.copy(hourHeight = 96.dp, eventBlockAccentWidth = 0.dp),
) {
    KalendarSchedule(state = rememberKalendarScheduleState(initialDate = today))
}
```

> **Note:** `KalendarSchedule`, `KalendarScheduleWeek` and `KalendarResourceView` also take an
> `hourHeight: Dp` parameter, which defaults from this token and overrides it for that one calendar.
> Use the parameter for a single view, the token for every view.

## `KalendarAnimations`

```kotlin
@Immutable
public class KalendarAnimations(
    public val enabled: Boolean = true,
    public val selectionColorSpec: AnimationSpec<Color> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow,
    ),
    public val selectionIndicatorSpec: AnimationSpec<Offset> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMedium,
    ),
    public val pressScale: Float = 0.92f,
    public val pressScaleSpec: AnimationSpec<Float> = spring(
        dampingRatio = Spring.DampingRatioLowBouncy,
        stiffness = Spring.StiffnessMedium,
    ),
    public val eventIndicatorFadeSpec: FiniteAnimationSpec<Float> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow,
    ),
    public val titleTransitionMillis: Int = 200,
    public val titleFadeOutEasing: Easing = FastOutLinearInEasing,
    public val titleFadeInEasing: Easing = LinearOutSlowInEasing,
)
```

Every spec is stated in full rather than left to `spring()`'s defaults, so retuning one means
changing a number here rather than first discovering what the default was.

| Property | Default | Controls |
|---|---|---|
| `enabled` | `true` | Master switch. When `false`, every animation snaps to its target and `pressScale` is ignored. |
| `selectionColorSpec` | a fully damped, medium-low-stiffness `spring()` | The day cell's selection fill fading in and out. |
| `selectionIndicatorSpec` | a fully damped, medium-stiffness `spring()` | The indicator sliding between selected day cells. |
| `pressScale` | `0.92f` | The scale a day cell shrinks to while pressed. `1f` disables the effect. |
| `pressScaleSpec` | a low-bounce, medium-stiffness `spring()` | The `pressScale` animation — the one spec that overshoots. |
| `eventIndicatorFadeSpec` | a fully damped, medium-low-stiffness `spring()` | The row of event dots fading in and out as a day gains or loses events. |
| `titleTransitionMillis` | `200` | Total duration of the header and sticky-title fade, split evenly between out and in. |
| `titleFadeOutEasing` | `FastOutLinearInEasing` | The half that takes the old title away: accelerating, so it leaves promptly. |
| `titleFadeInEasing` | `LinearOutSlowInEasing` | The half that brings the new title back: decelerating, so it settles. |
| `copy(…)` | — | A duplicate with only the values passed here replaced. |

`enabled = false` is the correct response to a platform reduced-motion preference, and what
screenshot tests want for determinism:

```kotlin
KalendarTheme(animations = KalendarAnimations(enabled = !prefersReducedMotion)) {
    KalendarMonth(selectedDate = today)
}
```

## `KalendarStrings`

```kotlin
@Immutable
public class KalendarStrings(…)
```

Every fixed word and accessibility announcement, as a token set — so translating the calendar is a
theme concern like any other. Fully defaulted, so overriding one field is a one-liner.

| Property | Type | Default | Used for |
|---|---|---|---|
| `previousPage` | `String` | `"Previous"` | Label for the header's previous-page arrow. |
| `nextPage` | `String` | `"Next"` | Label for the next-page arrow. |
| `today` | `String` | `"Today"` | Label for the today button, and the date picker's Today action. |
| `previousYear` | `String` | `"Previous year"` | Label for the jump picker's previous-year button. |
| `nextYear` | `String` | `"Next year"` | Label for its next-year button. |
| `openJumpPicker` | `String` | `"Choose month and year"` | Label for the header title when tapping it opens the jump picker. |
| `todaySuffix` | `String` | `", today"` | Appended to a day cell's announcement when the date is today. **Include the leading separator.** |
| `hasEventsSuffix` | `String` | `", has events"` | Appended when the date has at least one event. **Include the leading separator.** |
| `agendaEmptyState` | `String` | `"No events"` | Shown when `KalendarAgenda` is given no events. |
| `resourceEmptyState` | `String` | `"No resources"` | Shown when `KalendarResourceView` is given no lanes. |
| `showAllDayEvents` | `String` | `"Show all events"` | Label for the control that expands a truncated all-day row. |
| `eventOverflowLabel` | `(hiddenEventCount: Int) -> String` | `{ "+$it" }` | The compact label past `eventIndicatorCap`. |
| `dayAccessibilityLabel` | `(date: LocalDate, monthName: String) -> String` | `"August 12, 2026"` | A day cell's base announcement. The two suffixes are appended to whatever this returns. |
| `dayOfWeekAccessibilityLabel` | `(DayOfWeek) -> String` | `"Monday"` | The full day name announced for a column header, which only *shows* `"M"`. |
| `eventAccessibilityLabel` | `(event, start: LocalDateTime, end: LocalDateTime) -> String` | `"Design review, 09:30 to 11:00"` | A timed event block. |
| `resourceEventAccessibilityLabel` | `(event, start, end, resourceTitle: String) -> String` | the above, plus the lane | A block in `KalendarResourceView`. |
| `allDayEventAccessibilityLabel` | `(event) -> String` | `"Release day, all day"` | An all-day chip. |
| `copy(…)` | — | — | A duplicate with only the values passed here replaced. |

```kotlin
KalendarTheme(strings = KalendarTheme.strings.copy(today = stringResource(R.string.today))) {
    KalendarMonth(selectedDate = today)
}
```

The event labels are applied by `KalendarScheduleDefaults.EventBlock` and `.AllDayChip` — a
replacement `eventContent` or `allDayEventContent` slot draws its own semantics, and should. See
[Localization](../localization.md).
