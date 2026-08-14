# `foundation.selection`

`com.himanshoe.kalendar.foundation.selection`

Selection as a pure reduction: a value, a mode, and a function from the old selection to the new one.
No state holder, no observer, no lifecycle.

```kotlin
import com.himanshoe.kalendar.foundation.selection.KalendarSelection
```

## `KalendarSelectionMode`

```kotlin
public sealed interface KalendarSelectionMode {
    public data object Single : KalendarSelectionMode
    public data object Multiple : KalendarSelectionMode
    public data object Range : KalendarSelectionMode
}
```

How a selection responds to a date being tapped.

| Entry | A tap |
|---|---|
| `Single` | Replaces the current selection with just that date. |
| `Multiple` | Adds the date to the selection, or removes it if it was already selected. |
| `Range` | Starts a new range; a second tap completes it, selecting every date between the two inclusive, **regardless of tap order**. The next tap after a range is complete starts a fresh one. |

The mode is a parameter of [`afterClick`](#afterclick), not a property of the selection, so one
selection value can be driven by two different gestures — a tap in `Single` mode and a long-press
sweep in range mode — without being rebuilt.

```kotlin
var selection = KalendarSelection.Empty
selection = selection.afterClick(KalendarSelectionMode.Range, LocalDate(2026, 8, 12))
selection = selection.afterClick(KalendarSelectionMode.Range, LocalDate(2026, 8, 15))
selection.dates // the 12th through the 15th
```

## `KalendarSelection`

```kotlin
@Immutable
public class KalendarSelection(
    public val dates: Set<LocalDate> = emptySet(),
    public val pendingRangeStart: LocalDate? = null,
)
```

A calendar's selection and the half-finished range behind it — the whole state a tap reduces, with no
UI attached.

Every operation returns a new instance, so this drops into whatever state container a caller already
has: a `MutableState`, a `StateFlow`, a `rememberSaveable`, a plain field on a view model.

| Parameter | Type | Default | Description |
|---|---|---|---|
| `dates` | `Set<LocalDate>` | `emptySet()` | The currently selected dates. |
| `pendingRangeStart` | `LocalDate?` | `null` | The first date of a `Range` range whose second tap has not arrived yet. Persist it alongside `dates` to let a half-finished range survive process death. |

```kotlin
var selection by remember { mutableStateOf(KalendarSelection.Empty) }

DayCell(
    isSelected = date in selection.dates,
    onClick = { selection = selection.afterClick(KalendarSelectionMode.Multiple, date) },
)
```

### `Companion.Empty`

```kotlin
public val Empty: KalendarSelection
```

Nothing selected and no range in progress — where a calendar starts.

```kotlin
var selection by remember { mutableStateOf(KalendarSelection.Empty) }
```

### `afterClick`

```kotlin
public fun afterClick(mode: KalendarSelectionMode, date: LocalDate): KalendarSelection
```

The selection after `date` is tapped under `mode`.

| Parameter | Type | Default | Description |
|---|---|---|---|
| `mode` | `KalendarSelectionMode` | — | What a tap means. |
| `date` | `LocalDate` | — | The date tapped. |

```kotlin
KalendarSelection.Empty
    .afterClick(KalendarSelectionMode.Multiple, LocalDate(2026, 8, 12))
    .afterClick(KalendarSelectionMode.Multiple, LocalDate(2026, 8, 15))
    .dates // both dates
```

`Single` and `Multiple` leave `pendingRangeStart` untouched, so switching modes mid-range does not
lose a half-finished one.

### `afterRangeDrag`

```kotlin
public fun afterRangeDrag(start: LocalDate, current: LocalDate): KalendarSelection
```

The selection after a press-and-drag range gesture has swept from `start` to `current`: every date
between the two, inclusive, regardless of order.

Call it on **every pointer move** to get a live-updating highlight. It overwrites `dates` outright,
so it is a range-mode operation whatever `KalendarSelectionMode` the caller is otherwise in — a sweep
means a range by definition.

| Parameter | Type | Default | Description |
|---|---|---|---|
| `start` | `LocalDate` | — | The date the press landed on. |
| `current` | `LocalDate` | — | The date under the pointer now. |

```kotlin
onDrag = { change, _ -> selection = selection.afterRangeDrag(anchor, dateAt(change.position)) }
```

### `afterRangeDragEnd`

```kotlin
public fun afterRangeDragEnd(): KalendarSelection
```

The selection after a drag started by `afterRangeDrag` finishes. It clears `pendingRangeStart` so the
next *tap* starts a fresh range instead of trying to complete the just-finished sweep.

```kotlin
onDragEnd = { selection = selection.afterRangeDragEnd() }
```

### `cleared`

```kotlin
public fun cleared(): KalendarSelection
```

An empty selection with no range in progress — the same value as `Empty`, as an instance method for
call sites that already have a selection in hand.

```kotlin
Button(onClick = { selection = selection.cleared() }) { Text("Clear") }
```

### `copy`

```kotlin
public fun copy(
    dates: Set<LocalDate> = this.dates,
    pendingRangeStart: LocalDate? = this.pendingRangeStart,
): KalendarSelection
```

A duplicate with only the values passed here replaced — for restoring a persisted selection, or for
applying a rule the three modes do not express.

| Parameter | Type | Default | Description |
|---|---|---|---|
| `dates` | `Set<LocalDate>` | current | The selected dates. |
| `pendingRangeStart` | `LocalDate?` | current | The half-finished range's start. |

```kotlin
val restored = KalendarSelection.Empty.copy(dates = savedDates)
```

## Why this is a value and not a state holder

A selection is the one piece of calendar state an app almost always already owns: it goes in a form,
a view model, a `SavedStateHandle`, a URL query, a database row. Hiding it behind a holder would mean
every one of those has to reach through an object we designed to get at a `Set<LocalDate>` it could
have held directly.

So the engine ships the **reduction** and not the storage. The views module's
[`rememberKalendarSelectionState`](kalendar-state.md) is a thin `rememberSaveable` wrapper over
exactly these calls — a convenience, not a privileged path.
