# `foundation.schedule`

`com.himanshoe.kalendar.foundation.schedule`

Everything an hour grid needs: which hours are on screen, how overlapping events share the width,
where a block is drawn, what a drag or a resize does to it, and how to bucket events into resource
lanes.

```kotlin
import com.himanshoe.kalendar.foundation.schedule.scheduleBlocks
```

Every position in this package is measured in **minutes**, never pixels. You supply the scale:

```kotlin
val minutePx = hourHeightPx / MINUTES_PER_HOUR
```

## Constants

| Constant | Value | Description |
|---|---|---|
| `MINUTES_PER_HOUR` | `60` | Minutes in one hour — the unit every hour-grid position is measured in. |
| `HOURS_PER_DAY` | `24` | Hours in one day, and so the exclusive end of a full-day window. |
| `MINUTES_PER_DAY` | `1440` | Minutes in one day. A block ending here ends at midnight of the *following* day, which `LocalTime` cannot express — see [`dragReleaseTimes`](#dragreleasetimes). |
| `MIN_SCHEDULE_BLOCK_MINUTES` | `15` | Shortest rendered duration for a block, so zero and negative spans stay tappable. |

```kotlin
val hourPx = MINUTES_PER_HOUR * minutePx
```

## The visible window

### `KalendarHourWindow`

```kotlin
@Immutable
public class KalendarHourWindow(
    public val startHour: Int = 0,
    public val endHour: Int = HOURS_PER_DAY,
)
```

The slice of the day an hour grid draws: `startHour` through `endHour` and nothing else. A
business-hours calendar is `KalendarHourWindow(startHour = 8, endHour = 20)`.

Everything the grid derives from the day follows the window rather than midnight: the gutter labels
only these hours, the rules are drawn only between them, blocks are positioned relative to
`startHour`, a now-indicator disappears when the clock is outside it, and dragging, resizing and
sweeping out a new event clamp here instead of to `00:00..24:00`.

| Parameter | Type | Default | Description |
|---|---|---|---|
| `startHour` | `Int` | `0` | First hour drawn, `0..23`. |
| `endHour` | `Int` | `24` | Hour the grid stops at, **exclusive**, `startHour + 1..24`. `20` means the last labelled row is 19:00 and the bottom edge is 20:00. |

Throws `IllegalArgumentException` on construction if the hours are out of range or do not describe a
non-empty window.

| Member | Type | Description |
|---|---|---|
| `startHour` | `Int` | First hour drawn. |
| `endHour` | `Int` | Exclusive end hour. |
| `hourCount` | `Int` | How many hour rows the grid draws — `endHour - startHour`, always at least 1. |
| `startMinutes` | `Int` | `startHour` as minutes from midnight; the origin every block position is measured from. |
| `endMinutes` | `Int` | `endHour` as minutes from midnight; `1440` for a full day. |
| `spanMinutes` | `Int` | The window's height in minutes, which is also the grid's scrollable extent. |
| `hours` | `IntRange` | The hours to label, in order, top to bottom. |
| `contains(minutes)` | `Boolean` | Whether a minute-of-day falls inside, exclusive end excluded. `operator`, so `in` works. |
| `clamp(minutes)` | `Int` | A minute-of-day pulled into the window. |
| `copy(startHour, endHour)` | `KalendarHourWindow` | A duplicate with only the hours passed here replaced. Revalidated. |
| `Companion.FullDay` | `KalendarHourWindow` | Midnight to midnight — the default, and the whole day. |

```kotlin
val window = KalendarHourWindow(startHour = 8, endHour = 20)
val hourHeight = gridHeight / window.hourCount
window.hours.forEach { hour -> HourRule(y = (hour - window.startHour) * hourHeight) }

val showNowIndicator = now.minutesFromMidnight() in window
```

> **Note: the window clips, it does not filter.** A 07:00–09:00 meeting in an 08:00–20:00 window is
> drawn from the top of the grid to 09:00 with its start cut off, so the window never silently hides
> that something is booked. Only an event lying *entirely* outside is dropped, because it has no
> pixels to occupy.

The window is a display range, not a business rule. It does not stop your events starting at 03:00,
and callbacks always report real wall-clock times.

## Laying out a day

### `KalendarScheduleBlock`

```kotlin
@Immutable
public class KalendarScheduleBlock(
    public val event: KalendarEvent,
    public val startMinutes: Int,
    public val endMinutes: Int,
    public val column: Int,
    public val columns: Int,
)
```

A timed event positioned on an hour grid: its minute range within the day, and the column slot it
occupies when overlapping events share the horizontal space.

A block is *geometry*, not an event — it carries the `event` it came from so a caller can draw and
report it, but its own identity is the rectangle.

| Property | Type | Description |
|---|---|---|
| `event` | `KalendarEvent` | The event this block draws. |
| `startMinutes` | `Int` | Inclusive start, in minutes from the day's midnight. |
| `endMinutes` | `Int` | Exclusive end, at least `MIN_SCHEDULE_BLOCK_MINUTES` after the start and at most `MINUTES_PER_DAY` — where it means midnight of the following day. |
| `column` | `Int` | Zero-based slot within the event's overlap cluster. |
| `columns` | `Int` | Total slots in the cluster. **Every member of a cluster reports the same value**, which is what makes side-by-side widths line up. |
| `copy(…)` | `KalendarScheduleBlock` | A duplicate with only the values passed here replaced. |

```kotlin
val columnWidth = dayWidth / block.columns
val left = block.column * columnWidth
```

A block with nothing overlapping it reports `column = 0, columns = 1` and takes the full width — no
special case needed.

### `scheduleBlocks`

```kotlin
public fun scheduleBlocks(
    events: List<KalendarEvent>,
    date: LocalDate,
    window: KalendarHourWindow = KalendarHourWindow.FullDay,
): List<KalendarScheduleBlock>
```

The whole of a day view's layout. Lays out the **timed** events of `date` — those with a non-null
`startTime` — as blocks: minute ranges clamped to the day, a missing `endTime` defaulting to one
hour, events spanning midnight truncated at the day's edges, and overlapping events packed greedily
into side-by-side columns per overlap cluster.

| Parameter | Type | Default | Description |
|---|---|---|---|
| `events` | `List<KalendarEvent>` | — | Candidate events. Untimed ones are skipped. |
| `date` | `LocalDate` | — | The day being drawn; blocks are clamped to it. |
| `window` | `KalendarHourWindow` | `FullDay` | The visible slice. Events not intersecting it at all are dropped. |

```kotlin
val window = KalendarHourWindow(startHour = 8, endHour = 20)
scheduleBlocks(events = todaysEvents, date = today, window = window).forEach { block ->
    val placement = blockPlacement(block = block, window = window)
    val width = dayWidth / block.columns
    drawBlock(
        x = block.column * width,
        y = placement.topMinutes * minutePx,
        width = width,
        height = placement.heightMinutes * minutePx,
    )
}
```

Blocks keep their **true** minute range even when `window` shows less than the whole day — clipping
is a drawing concern ([`blockPlacement`](#blockplacement)), and truncating here would make a drag
report the clipped time rather than the event's own.

Everything returned has a hit target, so the result is exactly what the grid draws.

### `KalendarBlockPlacement`

```kotlin
@Immutable
public class KalendarBlockPlacement(
    public val topMinutes: Int,
    public val heightMinutes: Int,
)
```

Where a block sits in the grid's viewport. Both figures are minutes measured from the **top of the
visible window**, not from midnight.

| Property | Type | Description |
|---|---|---|
| `topMinutes` | `Int` | Distance from the top of the grid to the block's drawn top edge. |
| `heightMinutes` | `Int` | Drawn height. Zero when the block lies entirely outside the window. |
| `copy(…)` | `KalendarBlockPlacement` | A duplicate with only the values passed here replaced. |

```kotlin
val top = placement.topMinutes * minutePx
val height = placement.heightMinutes * minutePx
```

### `blockPlacement`

```kotlin
public fun blockPlacement(
    block: KalendarScheduleBlock,
    window: KalendarHourWindow,
): KalendarBlockPlacement
```

Where `block` is drawn inside `window`, clipped to it. A block starting before the window is drawn
from the top edge with its head cut off; one ending after it is cut off at the bottom; one wholly
outside gets a zero height, which draws nothing.

| Parameter | Type | Default | Description |
|---|---|---|---|
| `block` | `KalendarScheduleBlock` | — | The block being placed. |
| `window` | `KalendarHourWindow` | — | The visible slice. Required — placement is meaningless without it. |

```kotlin
val placement = blockPlacement(block = blocks.first(), window = KalendarHourWindow(8, 18))
placement.topMinutes    // 60, for a 09:00 start
placement.heightMinutes // 30, for a 30-minute event
```

### `initialScrollHourOffset`

```kotlin
public fun initialScrollHourOffset(initialScrollHour: Int, window: KalendarHourWindow): Int
```

How far down the grid a page opens, **in hours**, given a caller's preferred absolute hour and the
visible window.

The configured hour is absolute (`7` means 07:00), so on an 08:00–20:00 window it is already above
the top of the grid and the answer is `0` rather than a negative scroll. Below the window it clamps
to the bottom.

| Parameter | Type | Default | Description |
|---|---|---|---|
| `initialScrollHour` | `Int` | — | The absolute hour to open at. |
| `window` | `KalendarHourWindow` | — | The visible slice. |

```kotlin
val scrollPx = initialScrollHourOffset(initialScrollHour = 7, window = window) * hourHeightPx
```

### `LocalDateTime.minutesFromMidnight`

```kotlin
public fun LocalDateTime.minutesFromMidnight(): Int
```

A wall-clock time as minutes since midnight, which is the unit the hour grids position in.

```kotlin
val nowPx = (now.minutesFromMidnight() - window.startMinutes) * minutePx
```

## Dragging and resizing

All of it pure, and all of it clamped to the **window** rather than to the day — so a drag cannot
push a block off the top or bottom of a business-hours grid into space the user cannot scroll to and
would have no way to drag it back from.

### `KalendarScheduleDragMode`

```kotlin
public enum class KalendarScheduleDragMode { Move, ResizeStart, ResizeEnd }
```

Which part of a block a press grabbed, and so what a drag from there does.

| Entry | Effect |
|---|---|
| `Move` | Shift the whole block, preserving its duration. |
| `ResizeStart` | Move the block's top edge, pinning its end. |
| `ResizeEnd` | Move the block's bottom edge, pinning its start. |

### `scheduleDragMode`

```kotlin
public fun scheduleDragMode(
    pressYPx: Float,
    blockHeightPx: Float,
    resizeHandlePx: Float,
): KalendarScheduleDragMode
```

Which edge — if either — a press grabbed.

The strip is narrowed to `blockHeightPx / 3` when the block is too short for two full strips plus a
middle, so the top strip, the bottom strip and the move region never collide: a 15-minute block is
still movable rather than being all handle.

| Parameter | Type | Default | Description |
|---|---|---|---|
| `pressYPx` | `Float` | — | Press position within the block, from its top. |
| `blockHeightPx` | `Float` | — | The block's drawn height. `0` or less yields `Move`. |
| `resizeHandlePx` | `Float` | — | Nominal grab strip at each edge. |

```kotlin
scheduleDragMode(pressYPx = 4f, blockHeightPx = 120f, resizeHandlePx = 12f) // ResizeStart
```

### `snappedDragMinutes`

```kotlin
public fun snappedDragMinutes(rawPx: Float, minutePx: Float, snapMinutes: Int): Int
```

Converts an accumulated raw drag distance into a snapped minute delta in a **single** rounding step
(`rawPx` → snap units directly), avoiding the drift a two-stage px→minutes→snap rounding introduces
for snap values that do not divide 60.

| Parameter | Type | Default | Description |
|---|---|---|---|
| `rawPx` | `Float` | — | Accumulated drag distance in pixels, positive downwards. |
| `minutePx` | `Float` | — | Pixels one minute occupies. `0` or less yields no movement rather than a division by zero — which is what an unmeasured grid reports. |
| `snapMinutes` | `Int` | — | Step to snap to. Must be positive; anything else yields no movement. |

```kotlin
val delta = snappedDragMinutes(rawPx = dragY, minutePx = hourHeightPx / MINUTES_PER_HOUR, snapMinutes = 15)
```

### `adjustedScheduleBlock`

```kotlin
public fun adjustedScheduleBlock(
    block: KalendarScheduleBlock,
    deltaMinutes: Int,
    resizing: Boolean,
    window: KalendarHourWindow = KalendarHourWindow.FullDay,
): KalendarScheduleBlock
```

Applies a drag delta: resizing moves only the **end** (clamped to at least
`MIN_SCHEDULE_BLOCK_MINUTES` long and at most the window's end); moving shifts the whole block within
the window while preserving its duration.

| Parameter | Type | Default | Description |
|---|---|---|---|
| `block` | `KalendarScheduleBlock` | — | The block being adjusted. |
| `deltaMinutes` | `Int` | — | Snapped minute delta, from `snappedDragMinutes`. |
| `resizing` | `Boolean` | — | `true` to move the end edge, `false` to move the whole block. |
| `window` | `KalendarHourWindow` | `FullDay` | The clamp. |

```kotlin
val moved = adjustedScheduleBlock(block = block, deltaMinutes = 60, resizing = false)
```

### `adjustedScheduleBlockForDrag`

```kotlin
public fun adjustedScheduleBlockForDrag(
    block: KalendarScheduleBlock,
    deltaMinutes: Int,
    mode: KalendarScheduleDragMode,
    window: KalendarHourWindow = KalendarHourWindow.FullDay,
): KalendarScheduleBlock
```

The three-mode form. `Move` and `ResizeEnd` are exactly `adjustedScheduleBlock`'s two behaviours;
`ResizeStart` is their mirror — the start moves, the end stays put, and the start can never cross to
within less than `MIN_SCHEDULE_BLOCK_MINUTES` of the end nor run past the top of the window.

| Parameter | Type | Default | Description |
|---|---|---|---|
| `block` | `KalendarScheduleBlock` | — | The block being adjusted. |
| `deltaMinutes` | `Int` | — | Snapped minute delta. |
| `mode` | `KalendarScheduleDragMode` | — | What the press grabbed, from `scheduleDragMode`. |
| `window` | `KalendarHourWindow` | `FullDay` | The clamp. |

```kotlin
val preview = adjustedScheduleBlockForDrag(
    block = block,
    deltaMinutes = snappedDragMinutes(rawPx = dragY, minutePx = minutePx, snapMinutes = 15),
    mode = KalendarScheduleDragMode.ResizeStart,
)
```

### `dayShiftForDrag`

```kotlin
public fun dayShiftForDrag(
    rawDeltaXPx: Float,
    dayColumnWidthPx: Int,
    columnCount: Int = DAYS_PER_WEEK,
): Int
```

Converts a horizontal drag distance into whole day columns on a week grid, dividing by the full
day-column width and clamping to one grid width in either direction.

`columnCount` is the number of columns actually **drawn**, which is not always seven — a Mon–Fri week
has five, and clamping to six would let a drag land the block a column past the grid's edge. The
result counts *columns*, so on a filtered week convert it to a date by stepping visible days.

| Parameter | Type | Default | Description |
|---|---|---|---|
| `rawDeltaXPx` | `Float` | — | Accumulated horizontal drag distance. |
| `dayColumnWidthPx` | `Int` | — | Measured width of one day column. `0` yields no shift. |
| `columnCount` | `Int` | `DAYS_PER_WEEK` | How many day columns are drawn. |

```kotlin
val shift = dayShiftForDrag(rawDeltaXPx = dragX, dayColumnWidthPx = columnWidth, columnCount = 5)
val landedOn = visibleDateStep(from = block.event.date, step = shift, visibleDaysOfWeek = workWeek)
```

### `dragReleaseTimes`

```kotlin
public fun dragReleaseTimes(
    block: KalendarScheduleBlock,
    targetDate: LocalDate,
): Pair<LocalDateTime, LocalDateTime>
```

The `(start, end)` date-times to report for a released drag of `block` onto `targetDate`.

| Parameter | Type | Default | Description |
|---|---|---|---|
| `block` | `KalendarScheduleBlock` | — | The adjusted block, post-drag. |
| `targetDate` | `LocalDate` | — | The day it was dropped on. |

```kotlin
val (start, end) = dragReleaseTimes(block = adjusted, targetDate = day)
onEventMoved(block.event, start, end)
```

> **Warning:** an end at exactly `MINUTES_PER_DAY` is reported as `00:00` on the **following** date.
> `LocalTime` cannot express `24:00`, and truncating to 23:59 would lose a minute of every
> end-of-day event. Handle the rollover when you write it back.

### `minutesToLocalTime`

```kotlin
public fun minutesToLocalTime(minutes: Int): LocalTime
```

Minutes from midnight as a wall-clock time, clamped into the day.

| Parameter | Type | Default | Description |
|---|---|---|---|
| `minutes` | `Int` | — | Minutes from midnight. |

```kotlin
minutesToLocalTime(9 * MINUTES_PER_HOUR + 30) // 09:30
```

`MINUTES_PER_DAY` itself clamps to 23:59 rather than throwing, because `LocalTime` has no 24:00 — use
[`dragReleaseTimes`](#dragreleasetimes) when you need the distinction.

## Sweeping out a new event

### `KalendarScheduleCreateRange`

```kotlin
@Immutable
public class KalendarScheduleCreateRange(
    public val startMinutes: Int,
    public val endMinutes: Int,
)
```

A time range swept out on the grid, in minutes from the day's midnight.

| Property | Type | Description |
|---|---|---|
| `startMinutes` | `Int` | Inclusive start of the range. |
| `endMinutes` | `Int` | Exclusive end, always at least `MIN_SCHEDULE_BLOCK_MINUTES` after the start. May be exactly `MINUTES_PER_DAY`. |
| `copy(…)` | `KalendarScheduleCreateRange` | A duplicate with only the values passed here replaced. |

### `scheduleCreateRange`

```kotlin
public fun scheduleCreateRange(
    anchorPx: Float,
    currentPx: Float,
    minutePx: Float,
    snapMinutes: Int,
    window: KalendarHourWindow = KalendarHourWindow.FullDay,
): KalendarScheduleCreateRange
```

The range a create-drag sweeps out: both ends snapped and clamped to the window, ordered so an
upward drag reads the same as a downward one, then grown to `MIN_SCHEDULE_BLOCK_MINUTES` if the sweep
was shorter.

Both pixel positions are measured from the **top of the drawn grid**, not from midnight. A
minimum-length range that would spill past the bottom of the window is pushed back up against it
instead of overflowing.

| Parameter | Type | Default | Description |
|---|---|---|---|
| `anchorPx` | `Float` | — | Where the press landed, from the grid's top. |
| `currentPx` | `Float` | — | Where the pointer is now. |
| `minutePx` | `Float` | — | Pixels one minute occupies. |
| `snapMinutes` | `Int` | — | Step to snap both ends to. |
| `window` | `KalendarHourWindow` | `FullDay` | The clamp. |

```kotlin
val range = scheduleCreateRange(
    anchorPx = pressY,
    currentPx = dragY,
    minutePx = hourHeightPx / MINUTES_PER_HOUR,
    snapMinutes = 15,
    window = window,
)
```

### `scheduleCreateTimes`

```kotlin
public fun scheduleCreateTimes(
    range: KalendarScheduleCreateRange,
    date: LocalDate,
): Pair<LocalDateTime, LocalDateTime>
```

The `(start, end)` date-times to report for a released create-drag on `date`. As in
[`dragReleaseTimes`](#dragreleasetimes), an end at exactly `MINUTES_PER_DAY` is midnight of the
following day rather than a 23:59 truncation.

| Parameter | Type | Default | Description |
|---|---|---|---|
| `range` | `KalendarScheduleCreateRange` | — | The swept range. |
| `date` | `LocalDate` | — | The day it was swept on. |

```kotlin
val (start, end) = scheduleCreateTimes(range = range, date = day)
onCreateEvent(start, end)
```

### `scheduleBlockAt`

```kotlin
public fun scheduleBlockAt(
    blocks: List<KalendarScheduleBlock>,
    xPx: Float,
    yPx: Float,
    widthPx: Int,
    minutePx: Float,
    window: KalendarHourWindow = KalendarHourWindow.FullDay,
): KalendarScheduleBlock?
```

Which of `blocks` a press landed on, or `null` for empty space. It uses the same column-splitting and
minute-to-pixel geometry `scheduleBlocks` and `blockPlacement` lay blocks out with — `yPx` included,
which is measured from the top of the **window** rather than from midnight.

This is what turns a press into either "grab this block" or "start creating one".

| Parameter | Type | Default | Description |
|---|---|---|---|
| `blocks` | `List<KalendarScheduleBlock>` | — | The day column's blocks. |
| `xPx` | `Float` | — | Press x within the column. |
| `yPx` | `Float` | — | Press y from the top of the window. |
| `widthPx` | `Int` | — | The day column's width. `0` or less yields `null`. |
| `minutePx` | `Float` | — | Pixels one minute occupies. `0` or less yields `null`. |
| `window` | `KalendarHourWindow` | `FullDay` | The visible slice. |

```kotlin
val grabbed = scheduleBlockAt(
    blocks = blocks,
    xPx = press.x,
    yPx = press.y,
    widthPx = columnWidth,
    minutePx = minutePx,
    window = window,
)
if (grabbed == null) startCreateDrag(press) else startMoveDrag(grabbed)
```

## Resource lanes

### `KalendarResource`

```kotlin
@Immutable
public class KalendarResource(
    public val id: String,
    public val title: String,
    public val color: Color? = null,
    public val order: Int = 0,
)
```

One schedulable lane in a resource view — a room, a chair, a vehicle, a member of staff.

A resource is identity plus presentation and nothing else: **it owns no events**. Events are matched
to it by a `resourceIdOf` accessor returning this resource's `id`, which keeps `KalendarEvent` — a
shipped interface Kalendar cannot add members to — out of the picture.

| Parameter | Type | Default | Description |
|---|---|---|---|
| `id` | `String` | — | Stable identifier, unique across the list handed to a view. Columns are keyed by it and drag results report it, so it must survive edits and reordering. |
| `title` | `String` | — | Human-readable name shown in the column header and announced by screen readers. |
| `color` | `Color?` | `null` | Accent for this lane — the header's underline, and the fallback tint for blocks whose event has no `eventColor`. `null` leaves the choice to whatever draws the lane. |
| `order` | `Int` | `0` | Sort hint for [`orderedResources`](#orderedresources). The default leaves the list's own order untouched. |

| Member | Returns | Description |
|---|---|---|
| `copy(…)` | `KalendarResource` | A duplicate with only the values passed here replaced. |

```kotlin
val rooms = listOf(
    KalendarResource(id = "room-a", title = "Room A", color = Color(0xFF3B6EF3)),
    KalendarResource(id = "room-b", title = "Room B", order = 1),
)
```

### `orderedResources`

```kotlin
public fun orderedResources(resources: List<KalendarResource>): List<KalendarResource>
```

The lanes in the order they should be drawn: ascending `order`, ties keeping the order of the list as
given — so a caller that leaves `order` at its default gets its own ordering back untouched.

| Parameter | Type | Default | Description |
|---|---|---|---|
| `resources` | `List<KalendarResource>` | — | The lanes to sort. |

```kotlin
val lanes = orderedResources(rooms)
```

### `resourceBlocks`

```kotlin
public fun resourceBlocks(
    events: List<KalendarEvent>,
    resources: List<KalendarResource>,
    resourceIdOf: (KalendarEvent) -> String?,
    date: LocalDate,
    window: KalendarHourWindow = KalendarHourWindow.FullDay,
): Map<String, List<KalendarScheduleBlock>>
```

Lays out `events` as one list of blocks per lane, keyed by `KalendarResource.id`.

Each lane is packed **independently** by `scheduleBlocks`, so overlap columns are per lane: two
bookings that clash inside one room split that room's column and leave every other room full width.

Events whose `resourceIdOf` is `null` or names no known lane are dropped, as are untimed ones.

| Parameter | Type | Default | Description |
|---|---|---|---|
| `events` | `List<KalendarEvent>` | — | The day's events. |
| `resources` | `List<KalendarResource>` | — | The lanes to bucket into; only their `id`s are read. |
| `resourceIdOf` | `(KalendarEvent) -> String?` | — | Maps an event to the id of the lane it belongs in, or `null` for none. |
| `date` | `LocalDate` | — | The day being drawn, used to clamp blocks starting or ending outside it. |
| `window` | `KalendarHourWindow` | `FullDay` | The visible slice; a lane's clusters are packed from what is actually on screen. |

```kotlin
val lanes = orderedResources(rooms)
val blocksByLane = resourceBlocks(
    events = todaysBookings,
    resources = lanes,
    resourceIdOf = { it.calendarId },
    date = today,
)
lanes.forEach { lane -> drawLane(lane, blocksByLane[lane.id].orEmpty()) }
```

### `resourceShiftForDrag`

```kotlin
public fun resourceShiftForDrag(
    rawDeltaXPx: Float,
    resourceColumnWidthPx: Int,
    resourceCount: Int,
): Int
```

Converts a horizontal drag into whole lane columns, dividing by the **full column width** — never the
dragged block's own width, which is a fraction of the column whenever events overlap — and clamped to
the number of lanes that exist.

| Parameter | Type | Default | Description |
|---|---|---|---|
| `rawDeltaXPx` | `Float` | — | Accumulated horizontal drag distance. |
| `resourceColumnWidthPx` | `Int` | — | Measured width of one lane column. `0` yields no shift. |
| `resourceCount` | `Int` | — | How many lanes are on screen. |

```kotlin
val shift = resourceShiftForDrag(
    rawDeltaXPx = dragX,
    resourceColumnWidthPx = laneWidth,
    resourceCount = lanes.size,
)
```

### `shiftedResourceIndex`

```kotlin
public fun shiftedResourceIndex(index: Int, shift: Int, resourceCount: Int): Int
```

Where a lane column index lands after `shift` columns of drag, clamped to the ends of the list — so
dragging past the first or last lane parks the event there instead of reporting a lane that does not
exist.

| Parameter | Type | Default | Description |
|---|---|---|---|
| `index` | `Int` | — | The lane the block started in. |
| `shift` | `Int` | — | Columns moved, from `resourceShiftForDrag`. |
| `resourceCount` | `Int` | — | How many lanes exist. |

```kotlin
shiftedResourceIndex(index = 1, shift = 4, resourceCount = 3) // 2, the last lane
```
