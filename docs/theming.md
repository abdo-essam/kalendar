# Theming

Every visual decision a Kalendar view makes — colour, text style, corner shape, size, spacing,
motion, and the words it speaks to a screen reader — resolves through an ambient set of **design
tokens**. Nothing is hard-coded, and nothing reads your `MaterialTheme` at render time.

The rule to hold onto:

> Kalendar reads Material **only to derive default token values**. No Material widget renders the
> calendar. An app with its own design system controls rendering completely, through tokens and
> [slots](customization.md).

Concretely: the only Material types the view package touches are `MaterialTheme`, `ColorScheme`, and
`Typography`, and only inside `KalendarThemeDefaults` to read default colours and text styles. Every
pixel is drawn with Compose Foundation — the text, the icons, the jump picker, the dividers. Replace
the default token values and Material is out of the picture entirely.

That means a Material 3 app gets a calendar that matches its colour scheme for free, and a
non-Material app can restyle every pixel without fighting a Material dependency it never wanted.

## The shape of the API

It mirrors `MaterialTheme` deliberately, so it should already feel familiar:

| Piece | What it is |
|---|---|
| `KalendarColors`, `KalendarTypography`, `KalendarShapes`, `KalendarDimensions`, `KalendarAnimations`, `KalendarStrings` | The six token sets. |
| `KalendarThemeTokens` | All six bundled into one value. |
| `KalendarTheme { }` | The composable that **provides** tokens to everything inside it. |
| `KalendarTheme` (the object) | The accessors that **read** the ambient tokens — `KalendarTheme.colors`, `KalendarTheme.dimensions`, and so on. |
| `KalendarThemeDefaults` | The built-in token values, and the three palettes. |

Wrapping is optional. A view used outside any `KalendarTheme` falls back to `KalendarThemeDefaults`,
so reading a token is always safe.

## Overriding one token

Each token set is independent, so overriding one leaves the other five alone:

```kotlin
import com.himanshoe.kalendar.view.KalendarTheme
import com.himanshoe.kalendar.view.KalendarThemeDefaults

KalendarTheme(
    colors = KalendarThemeDefaults.colors().copy(selectionBackground = BrandPurple),
) {
    KalendarMonth(selectedDate = today)
}
```

`KalendarColors`, `KalendarTypography`, and `KalendarShapes` have **no default constructor values** —
every field is required — so you must start from an existing instance and `copy` it. The other three
are fully defaulted, so you can construct them directly:

```kotlin
KalendarTheme(dimensions = KalendarDimensions(hourHeight = 96.dp)) {
    KalendarSchedule(selectedDate = today)
}
```

> **Warning:** constructing a token set directly resets every field you did not name back to the
> built-in default. Inside a nested `KalendarTheme`, that silently discards the outer theme's
> values. To layer on top of whatever is already ambient, copy the accessor instead:
>
> ```kotlin
> KalendarTheme(dimensions = KalendarTheme.dimensions.copy(hourHeight = 96.dp)) {
>     KalendarSchedule(selectedDate = today)
> }
> ```

> **Note:** the token classes are `@Immutable` classes with hand-written members, not data classes.
> `copy()`, `equals()`, `hashCode()`, and `toString()` all work exactly as you would expect — which
> matters, because value equality is what lets Compose skip recomposition when a theme is rebuilt
> with the same values. Destructuring (`val (a, b) = tokens`) is the one data-class convenience they
> do not have.

## Reading tokens

The `KalendarTheme` object exposes the ambient values, for building custom cells and slots that
match the rest of the calendar:

```kotlin
@Composable
fun MyDayCell(scope: KalendarDayScope) {
    val colors = KalendarTheme.colors
    val typography = KalendarTheme.typography

    Text(
        text = scope.date.dayOfMonth.toString(),
        color = if (scope.isToday) colors.todayContent else colors.dayContent,
        style = if (scope.isSelected) typography.dayNumberEmphasized else typography.dayNumber,
    )
}
```

| Accessor | Type |
|---|---|
| `KalendarTheme.colors` | `KalendarColors` |
| `KalendarTheme.typography` | `KalendarTypography` |
| `KalendarTheme.shapes` | `KalendarShapes` |
| `KalendarTheme.dimensions` | `KalendarDimensions` |
| `KalendarTheme.animations` | `KalendarAnimations` |
| `KalendarTheme.strings` | `KalendarStrings` |
| `KalendarTheme.tokens` | `KalendarThemeTokens` — all six at once |

All of them are `@Composable` getters, and all fall back to `KalendarThemeDefaults` outside a
`KalendarTheme`.

## Colours

`KalendarColors` carries thirteen colours and four alpha values.

| Token | Used for | Material default | `lightColors()` | `darkColors()` |
|---|---|---|---|---|
| `background` | Fill behind the whole calendar | `surface` | `#FFFBFE` | `#1C1B1F` |
| `selectionBackground` | The selected day's fill and the sliding indicator | `primaryContainer` | `#E8DEF8` | `#4F378B` |
| `onSelectionBackground` | Content on top of `selectionBackground` | `onPrimaryContainer` | `#1D192B` | `#EADDFF` |
| `todayContent` | Today's day number when it is not selected | `primary` | `#6750A4` | `#D0BCFF` |
| `dayContent` | Ordinary day numbers | `onSurface` | `#1C1B1F` | `#E6E1E5` |
| `dayLabelContent` | Day-of-week column labels and hour-gutter labels | `onSurfaceVariant` | `#49454F` | `#CAC4D0` |
| `headerContent` | The navigation header's title and its icons | `onSurface` | `#1C1B1F` | `#E6E1E5` |
| `eventIndicator` | Event dots and blocks when `KalendarEvent.eventColor` is `null` | `primary` | `#6750A4` | `#D0BCFF` |
| `nowIndicator` | The current-time line on the Schedule views | `error` | `#B3261E` | `#F2B8B5` |
| `gridLine` | Hour lines and separators | `outlineVariant` | `#CAC4D0` | `#49454F` |
| `dragGhostBackground` | The drop-target ghost while dragging an event | `secondaryContainer` | `#E8DEF8` | `#4A4458` |
| `popupBackground` | Fill of the month/year jump picker | `surfaceContainerHigh` | `#ECE6F0` | `#2B2930` |
| `popupBorder` | Outline around the jump picker | `outlineVariant` | `#CAC4D0` | `#49454F` |

And the four alphas, which are the same in every palette:

| Token | Default | Applied to |
|---|---|---|
| `disabledContentAlpha` | `0.38f` | Day cells rejected by `KalendarViewConfig.disabledDates`, and adjacent-month dates. |
| `eventBlockAlpha` | `0.18f` | An event block's fill, over the event's accent colour. The stripe and text stay opaque. |
| `eventBlockDraggingAlpha` | `0.45f` | An event block's fill while it is being dragged. |
| `dragGhostAlpha` | `0.6f` | `dragGhostBackground`. |

> **Note:** the calendar's container fill has two sources. `KalendarColors.background` is the token;
> [`KalendarViewConfig.background`](configuration.md) is a per-view `Brush?` that overrides it when
> non-null. Use the token for a colour, and the config only for something a colour cannot express —
> a gradient, say.

### Apps that use Material 3

Do nothing. `KalendarThemeDefaults.colors()` derives from the ambient `MaterialTheme.colorScheme`, so
your brand colours and your light/dark scheme are already applied.

### Apps without Material 3

`MaterialTheme.colorScheme` falls back to Compose's baseline **light** scheme when no `MaterialTheme`
is present — which means a calendar in a non-Material app would stay light even in dark mode. Pass
one of Kalendar's own Material-free palettes instead:

```kotlin
KalendarTheme(colors = KalendarThemeDefaults.systemColors()) {
    KalendarMonth(selectedDate = today)
}
```

| Function | Behaviour |
|---|---|
| `KalendarThemeDefaults.colors()` | Derived from the ambient `MaterialTheme.colorScheme`. The default. |
| `KalendarThemeDefaults.systemColors()` | Kalendar's own palette, following the system light/dark setting via `isSystemInDarkTheme()`. No Material dependency. |
| `KalendarThemeDefaults.lightColors()` | The light palette, chosen explicitly. |
| `KalendarThemeDefaults.darkColors()` | The dark palette, chosen explicitly. |

Use `lightColors()` / `darkColors()` when your app drives its own theme switch rather than following
the system:

```kotlin
KalendarTheme(
    colors = if (myAppIsInDarkMode) {
        KalendarThemeDefaults.darkColors()
    } else {
        KalendarThemeDefaults.lightColors()
    },
) {
    KalendarMonth(selectedDate = today)
}
```

Or map your own design system onto the tokens directly, cutting the Material link entirely:

```kotlin
KalendarTheme(
    colors = KalendarColors(
        background = MyTheme.surface,
        selectionBackground = MyTheme.accentMuted,
        onSelectionBackground = MyTheme.onAccentMuted,
        todayContent = MyTheme.accent,
        dayContent = MyTheme.textPrimary,
        dayLabelContent = MyTheme.textSecondary,
        headerContent = MyTheme.textPrimary,
        eventIndicator = MyTheme.accent,
        nowIndicator = MyTheme.danger,
        gridLine = MyTheme.divider,
        dragGhostBackground = MyTheme.accentMuted,
        popupBackground = MyTheme.surfaceRaised,
        popupBorder = MyTheme.divider,
    ),
) {
    KalendarMonth(selectedDate = today)
}
```

Every colour is a required constructor parameter, so the compiler tells you when a release adds one
rather than letting a new surface silently fall back to something arbitrary.

### Dark mode

There is no dark-mode switch on Kalendar itself. Dark mode is whatever the colours say it is:

- With `KalendarThemeDefaults.colors()`, dark mode follows your `MaterialTheme`.
- With `KalendarThemeDefaults.systemColors()`, it follows the OS setting.
- With `lightColors()` / `darkColors()`, it follows whatever you pass.
- With a hand-built `KalendarColors`, it follows your own design system.

The other five token sets are mode-independent — sizes, shapes and motion do not change between
light and dark — so a single `KalendarTheme` can swap only `colors` and leave the rest alone.

## Typography

`KalendarTypography` covers every piece of text a view draws. Defaults come from
`MaterialTheme.typography`.

| Token | Used for | Default |
|---|---|---|
| `headerTitle` | The navigation header's title, the Timeline's month titles, the jump picker's year | `titleMedium` |
| `monthLabel` | Month names inside `KalendarYear`'s grid | `labelLarge` |
| `dayOfWeekLabel` | The day-of-week column headers | `labelSmall` |
| `dayNumber` | The day number in an ordinary cell | `bodyLarge` |
| `dayNumberEmphasized` | The day number when the cell is selected or is today | `bodyLarge` in bold |
| `weekDayNumber` | The date number in `KalendarScheduleWeek`'s day-column headers | `labelLarge` |
| `eventLabel` | Event block titles and all-day chip titles | `labelSmall` |
| `eventOverflowLabel` | The compact `+N` label | `labelSmall` at `7.sp` |
| `hourLabel` | The hour-gutter labels on the Schedule views | `labelSmall` |

To drop the selected/today emphasis entirely, set `dayNumberEmphasized` equal to `dayNumber`:

```kotlin
val type = KalendarTheme.typography
KalendarTheme(typography = type.copy(dayNumberEmphasized = type.dayNumber)) {
    KalendarMonth(selectedDate = today)
}
```

Plugging in a non-Material type scale is the same pattern as colours:

```kotlin
KalendarTheme(
    typography = KalendarThemeDefaults.typography().copy(
        headerTitle = MyTheme.type.heading3,
        dayNumber = MyTheme.type.numeric,
    ),
) {
    KalendarMonth(selectedDate = today)
}
```

## Shapes

| Token | Used for | Default |
|---|---|---|
| `dayCell` | The day cell's selection fill, the sliding selection indicator, and the drag ghost | `CircleShape` |
| `eventIndicator` | The per-event dots below a day number | `CircleShape` |
| `eventBlock` | Event blocks on the Schedule views, and all-day chips | `RoundedCornerShape(4.dp)` |
| `popup` | The month/year jump picker surface | `RoundedCornerShape(12.dp)` |

`dayCell` is one token shared by three separate composables that have to agree visually — the cell,
the indicator that animates behind it, and the drag ghost. That is exactly why it lives on the theme
rather than on each composable.

```kotlin
KalendarTheme(
    shapes = KalendarTheme.shapes.copy(dayCell = RoundedCornerShape(8.dp)),
) {
    KalendarMonth(selectedDate = today)
}
```

## Dimensions

Every field of `KalendarDimensions` has a default, so this is the one token set you can construct
from scratch as a one-liner.

| Token | Default | Controls |
|---|---|---|
| `dayCellPadding` | `2.dp` | Inset around each day cell, the selection indicator, and the drag ghost. |
| `dayCellAspectRatio` | `1f` | Width-to-height ratio of a day cell. Above `1f` gives short, wide cells. |
| `eventIndicatorSize` | `4.dp` | Diameter of one event dot. |
| `eventIndicatorSpacing` | `2.dp` | Gap between adjacent event dots. |
| `eventIndicatorTopPadding` | `3.dp` | Gap between the day number and the dot row. |
| `headerPadding` | `PaddingValues(horizontal = 4.dp, vertical = 8.dp)` | Padding around the navigation header's contents. |
| `headerIconSlotWidth` | `48.dp` | Width reserved on each side of the header title for icons. The title's available width is computed from it. |
| `hourHeight` | `64.dp` | Vertical space for one hour on the Schedule views. |
| `hourGutterWidth` | `48.dp` | Width of the Schedule views' hour-label gutter. |
| `gridLineThickness` | `1.dp` | Thickness of hour lines and separators. |
| `nowIndicatorThickness` | `2.dp` | Thickness of the current-time line. |
| `eventBlockGap` | `1.dp` | Horizontal gap between side-by-side event blocks. |
| `eventBlockAccentWidth` | `3.dp` | Width of an event block's leading accent stripe. `0.dp` removes it. |
| `eventBlockPadding` | `PaddingValues(horizontal = 4.dp, vertical = 2.dp)` | Padding around an event block's label. |
| `eventBlockResizeHandleHeight` | `12.dp` | Height of the grab strip at a block's bottom edge that starts a resize rather than a move. |
| `allDayRowPadding` | `PaddingValues(horizontal = 16.dp, vertical = 4.dp)` | Padding around the all-day chip row. |
| `allDayChipSpacing` | `4.dp` | Gap between all-day chips. |
| `allDayChipPadding` | `PaddingValues(horizontal = 6.dp, vertical = 2.dp)` | Padding inside one all-day chip. |
| `monthSpacing` | `16.dp` | Vertical gap between month grids in `KalendarYear`. |
| `sectionPadding` | `PaddingValues(horizontal = 16.dp, vertical = 8.dp)` | Padding around section titles — the Timeline's month titles and sticky header, and `KalendarYear`'s month names. |

Two of these are shared across composables for the same reason `shapes.dayCell` is: `dayCellPadding`
aligns the cell, the sliding indicator, and the drag ghost; `hourGutterWidth` sizes both the gutter
and the header spacer above it.

```kotlin
KalendarTheme(
    dimensions = KalendarTheme.dimensions.copy(
        hourHeight = 96.dp,
        eventBlockAccentWidth = 0.dp,
    ),
) {
    KalendarSchedule(selectedDate = today)
}
```

> **Note:** `KalendarSchedule` and `KalendarScheduleWeek` also take an `hourHeight: Dp?` parameter.
> When non-null it overrides `KalendarDimensions.hourHeight` for that one calendar. Use the
> parameter for a single view, the token for every view.

## Animations

| Token | Default | Controls |
|---|---|---|
| `enabled` | `true` | Master switch. When `false`, every animation below snaps to its target and `pressScale` is ignored. |
| `selectionColorSpec` | `spring()` | The day cell's selection fill fading in and out. |
| `selectionIndicatorSpec` | `spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessHigh)` | The indicator that slides between selected day cells. |
| `pressScale` | `0.92f` | The scale a day cell shrinks to while pressed. `1f` disables the effect. |
| `pressScaleSpec` | `spring()` | The `pressScale` animation. |
| `titleTransitionMillis` | `150` | Duration of the header and sticky-title crossfade. |

### Reduced motion

Set `enabled = false` and every transition becomes instant — the correct response to a platform
reduced-motion preference, and what screenshot tests want for determinism:

```kotlin
KalendarTheme(animations = KalendarAnimations(enabled = false)) {
    KalendarMonth(selectedDate = today)
}
```

Wire it to the platform preference rather than hard-coding it:

```kotlin
KalendarTheme(animations = KalendarAnimations(enabled = !prefersReducedMotion)) {
    KalendarMonth(selectedDate = today)
}
```

With `enabled = false`, the selection fill, the press-scale, the event-indicator row, and the header
title crossfade all change instantly instead of animating.

To keep motion but soften it, override the individual specs and leave `enabled` at `true`:

```kotlin
KalendarTheme(
    animations = KalendarTheme.animations.copy(
        pressScale = 1f,
        titleTransitionMillis = 80,
    ),
) {
    KalendarMonth(selectedDate = today)
}
```

## Strings

`KalendarStrings` holds every accessibility announcement and short label. It is a token set like any
other, so it is provided through `KalendarTheme` — see [Localization](localization.md) for the full
field list and how to feed it your app's resources.

```kotlin
KalendarTheme(
    strings = KalendarStrings(
        previousPage = stringResource(R.string.previous),
        nextPage = stringResource(R.string.next),
        today = stringResource(R.string.today),
    ),
) {
    KalendarMonth(selectedDate = today)
}
```

## Bundling a look with `KalendarThemeTokens`

`KalendarThemeTokens` bundles all six sets into one value, so a whole look can be built once, passed
around, and copied:

```kotlin
@Composable
fun rememberBrandKalendarTokens(): KalendarThemeTokens =
    KalendarTheme.tokens.copy(
        colors = KalendarThemeDefaults.systemColors().copy(selectionBackground = BrandPurple),
        shapes = KalendarTheme.shapes.copy(dayCell = RoundedCornerShape(8.dp)),
        animations = KalendarAnimations(enabled = false),
    )
```

Apply it by spreading it back over a `KalendarTheme`:

```kotlin
val tokens = rememberBrandKalendarTokens()

KalendarTheme(
    colors = tokens.colors,
    typography = tokens.typography,
    shapes = tokens.shapes,
    dimensions = tokens.dimensions,
    animations = tokens.animations,
    strings = tokens.strings,
) {
    KalendarMonth(selectedDate = today)
}
```

To restyle a single calendar differently from its surroundings, wrap just that one in its own
`KalendarTheme`. Nesting is the mechanism: each token set you do not pass is inherited from the
theme above, so an inner theme only states its differences.

```kotlin
KalendarTheme(colors = KalendarThemeDefaults.systemColors()) {
    KalendarMonth(selectedDate = today)

    // Same colours, denser layout — everything else inherited.
    KalendarTheme(dimensions = KalendarTheme.dimensions.copy(dayCellPadding = 0.dp)) {
        KalendarYear(selectedDate = today)
    }
}
```

## Setting a theme once, app-wide

Provide the theme high in your tree, next to your own:

```kotlin
@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = myColorScheme) {
        KalendarTheme(
            shapes = KalendarTheme.shapes.copy(dayCell = RoundedCornerShape(8.dp)),
            dimensions = KalendarTheme.dimensions.copy(dayCellPadding = 4.dp),
            content = content,
        )
    }
}
```

Because `colors` and `typography` are left at their defaults here, they still track the
`MaterialTheme` around them — including its dark-mode switch.

## Where to go next

- [**Customization**](customization.md) — replacing the day cell, header, and event rendering
  outright.
- [**Localization**](localization.md) — `KalendarStrings` and the date formatter hooks.
- [**Configuration**](configuration.md) — the behavioural knobs that are not tokens.
