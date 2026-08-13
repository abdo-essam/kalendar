# Kalendar: Theming

Every Kalendar view resolves its colours through an ambient set of tokens — `KalendarColors` —
instead of reading `MaterialTheme` directly. Unwrapped views derive the tokens from the ambient
Material colour scheme, so Material apps need to do nothing. Non-Material apps (or apps wanting a
different calendar accent) wrap the views in `KalendarTheme`:

```kotlin
KalendarTheme(
    colors = KalendarThemeDefaults.colors().copy(
        selectionBackground = BrandPurple,
        onSelectionBackground = Color.White,
        nowIndicator = BrandRed,
    ),
) {
    KalendarMonth(selectedDate = today)
}
```

`KalendarThemeDefaults.colors()` returns the Material-derived set, so `copy` lets you override
only what you need. Construct `KalendarColors` from scratch to cut the Material link entirely.

## Tokens

| Token | Used for | Material default |
|---|---|---|
| `selectionBackground` | Selected day fill + sliding indicator | `primaryContainer` |
| `onSelectionBackground` | Content on the selected day | `onPrimaryContainer` |
| `todayContent` | Today's day number (unselected) | `primary` |
| `dayContent` | Ordinary day numbers | `onSurface` |
| `dayLabelContent` | Day-of-week column labels, hour-gutter labels | `onSurfaceVariant` |
| `eventIndicator` | Event dots/blocks when `KalendarEvent.eventColor` is null | `primary` |
| `nowIndicator` | Current-time line on Schedule views | `error` |
| `gridLine` | Hour lines on Schedule views | `outlineVariant` |
| `dragGhostBackground` | Drop-target ghost while dragging an event | `secondaryContainer` |

Typography and shapes still come from `MaterialTheme` (day cells use `labelSmall`/`labelLarge`,
titles `titleMedium`); override those via a surrounding `MaterialTheme` if needed. The calendar
container background stays on `KalendarViewConfig.background`, which predates these tokens and
accepts a `Brush` rather than a colour.
