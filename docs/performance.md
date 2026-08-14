# Performance

The measured baseline for `:kalendar`, how to reproduce it, and what the numbers mean.

Everything below was produced by running the commands in this document. Where something is
reasoning rather than measurement, it says so.

## Summary

The library is in good shape. All **nine** public views are `restartable skippable`, every class the
compiler sees is stable, and a recomposition above a calendar costs zero day cells. `KalendarYear`,
which used to eagerly compose 400-odd day cells on its first frame, is now viewport-bounded and
measures **235** with this benchmark's deliberately tiny probe cell.

## Regenerating the compiler reports

Metrics are off by default. They force a full non-incremental recompile and serialise a report per
compilation, which is not worth paying on every build for data that nothing gates on — so they are
**not** enabled on CI. Turn them on when you need them:

```bash
./gradlew :kalendar:compileKotlinDesktop -PkalendarComposeMetrics --no-configuration-cache
```

`--no-configuration-cache` is required: the flag is read at configuration time, so a cached
configuration silently ignores it and the reports never appear. If the task reports `UP-TO-DATE` or
`FROM-CACHE`, delete `kalendar/build/classes/kotlin/desktop` first — the compiler only writes the
reports when it actually runs.

Output lands in:

- `kalendar/build/compose-reports/kalendar-composables.txt` — per-composable, per-parameter
- `kalendar/build/compose-reports/kalendar-composables.csv` — the same, sorted and greppable
- `kalendar/build/compose-reports/kalendar-classes.txt` — inferred class stability
- `kalendar/build/compose-metrics/` — raw counters

## What the reports say

From `kalendar-composables.txt`, 106 composables, 72 `restartable skippable`. Every public view:

```
restartable skippable fun com.himanshoe.kalendar.KalendarWeek(
restartable skippable fun com.himanshoe.kalendar.KalendarMonth(
restartable skippable fun com.himanshoe.kalendar.KalendarYear(
restartable skippable fun com.himanshoe.kalendar.KalendarTimeline(
restartable skippable fun com.himanshoe.kalendar.KalendarSchedule(
restartable skippable fun com.himanshoe.kalendar.KalendarScheduleWeek(
restartable skippable fun com.himanshoe.kalendar.KalendarResourceView(
restartable skippable fun com.himanshoe.kalendar.KalendarAgenda(
restartable skippable fun com.himanshoe.kalendar.KalendarDatePicker(
```

The 34 composables that are neither restartable nor skippable are all `remember*` factories and
`KalendarTheme` accessors. A composable that returns a value is never skippable, by construction —
that is correct, not a finding.

`kalendar-classes.txt` lists 22 classes, **all** of them `stable`, none `unstable` or `runtime`.

### The token classes are genuinely structural

The token classes were recently converted from `data class` to hand-written `equals`/`hashCode`.
An identity-based `equals` would have made every view silently non-skippable. It did not happen —
verified two ways.

By inspection, `KalendarColors`, `KalendarTypography`, `KalendarShapes`, `KalendarDimensions`,
`KalendarAnimations`, `KalendarThemeTokens`, `KalendarStrings`, `KalendarViewConfig`,
`KalendarDayScope` and `KalendarDatePickerDayScope` all compare field by field after the usual
`if (this === other) return true` fast path. None of them stops at identity. The same is true of the
engine's own value types — `BasicKalendarEvent`, `KalendarScheduleBlock`, `KalendarHourWindow`,
`KalendarSelection`, `KalendarResource`, `KalendarPager`, `KalendarTimeSource` — which do not appear
in this report because they compile in `:kalendar-foundation`, but reach a consumer's composables as
parameters all the same.

By report, each is `stable` with every field enumerated, e.g.:

```
stable class com.himanshoe.kalendar.theme.KalendarColors {
  stable val background: Color
  stable val selectionBackground: Color
  ...
```

Note the report alone would **not** have caught an identity-based `equals`: all of these carry
`@Immutable`, which asserts stability regardless of what `equals` does. The compiler would still
have said `stable` while every view stopped skipping at runtime. That is why the benchmark below
exists, and why it asserts on behaviour rather than on the report.

### "unstable" parameters are not a problem here

77 parameters are marked `unstable`, and they are all `kotlinx.datetime.LocalDate`, `List`, or
`Set`:

```
restartable skippable fun com.himanshoe.kalendar.KalendarMonth(
  unstable selectedDate: LocalDate
  stable modifier: Modifier? = @static <expression>
  stable state: KalendarViewState? = @dynamic <expression>
  unstable events: List<KalendarEvent>? = @static <expression>
  unstable selectedDates: Set<LocalDate>? = @dynamic <expression>
```

This looks alarming and is not. The function is still `restartable skippable` — that is strong
skipping, which is on by default for Kotlin 2.x. Under it, unstable parameters are compared with
`equals`, not with `===`.

**This was measured, not assumed.** `freshlyAllocatedEqualParametersRecomposeNothing` in the
benchmark passes a `LocalDate` and an event `List` that are freshly allocated on every
recomposition — and constructed from the tick counter, so the compiler cannot hoist them into a
`remember` as compile-time constants — and asserts that zero day cells recompose. It passes.

A stability configuration file listing `kotlinx.datetime.LocalDate` and the collection interfaces
was written, measured, and **reverted**: it cleaned every unstable parameter out of the report and
changed no runtime number whatsoever. It is not worth the standing claim that every `List` handed
to the library is immutable. If the benchmark above ever starts failing, that file is the fix, and
it belongs at `kalendar/compose-stability.conf` wired in via `composeCompiler {
stabilityConfigurationFiles }`. Note the file format takes `//` comments only — `#` fails the build
with `Error parsing stability configuration file on line 0`.

For the same reason, wrapping `KalendarEvents` in an `@Immutable` class would buy nothing
measurable and would cost real API churn. Not recommended.

## The benchmark

`kalendar/src/desktopTest/kotlin/com/himanshoe/kalendar/KalendarRecompositionBenchmark.kt`,
run by the ordinary `:kalendar:desktopTest`. It counts **compositions**, not milliseconds — a
composition count reproduces across machines, a timing does not.

```bash
./gradlew :kalendar:desktopTest --tests "*KalendarRecompositionBenchmark*"
```

Measured baseline:

| Measurement | Count |
|---|---|
| `KalendarMonth` first composition | 37 cells |
| `KalendarWeek` first composition | 7 cells |
| `KalendarYear` first composition | **235 cells** — see below |
| `KalendarMonth`, caller recomposes, hoisted params | 0 cells |
| `KalendarWeek`, caller recomposes, hoisted params | 0 cells |
| `KalendarMonth`, caller recomposes, freshly allocated equal params | 0 cells |
| An arrow keypress moving the focus ring | 0 cells |
| One whole drag gesture, 8 blocks on screen | 4 block slots |
| Inserting an earlier event, 8 blocks on screen | 1 block slot |
| A now-indicator tick, 8 blocks on screen | 0 block slots |

The last four are the ones that catch real regressions. Keyed slots are why inserting an event at the
top of a day recomposes one block rather than all nine; reading the clock in the **placement** phase
is why a minute passing recomposes nothing at all and simply re-places the line.

### Three traps this benchmark had to survive

Each of these silently turns the numbers into meaningless zeros, and each was hit while writing it.

1. **The probe must not capture a mutable local.** A lambda capturing a plain `var` is itself an
   unstable parameter, so the view holding it can never skip, and the benchmark measures its own
   interference. The counter is a `@Stable` class in a top-level `val`.
2. **The probe must emit a node.** A composable that emits nothing has its group optimised away and
   can never be observed skipping.
3. **The invalidation must reach the calendar's caller.** Flip some state at the top of the tree and
   the first skippable composable in between — `KalendarTheme` — absorbs it, so the view is never
   re-offered its parameters and every count is zero *whatever* the view does. The tick is
   therefore threaded into the calling lambda as a parameter, and `CALL_SITES` asserts that lambda
   ran exactly twice. **Do not remove that assertion**; without it the whole file is decorative.

A fourth trap sits in the harness rather than the library, and is worth knowing when writing any
Compose test here: `MaterialTheme(colorScheme = lightColorScheme())` written inline allocates a
fresh `ColorScheme` on every recomposition, and Material 3's `ColorScheme` has no structural
`equals` — so it re-provides a changed composition local and force-recomposes its entire subtree.
Hoist it. Before that was found, every view in this repo appeared not to skip.

## The known suspects, assessed

### `KalendarYear` — fixed. The month column is lazy

**This was the one real cost, and it is no longer one.** `KalendarYear` used to stack twelve month
grids in a non-lazy `verticalScroll` `Column`, composing every cell of every month whether or not it
was on screen — 400-odd cells, roughly eleven months of invisible work on a first frame.

That column is now a `LazyColumn`, so only the months in the viewport compose.
`yearComposesOnlyTheMonthsOnScreen` measures **235** cells and asserts the count stays under 300,
against a `YEAR_EAGER_CELL_FLOOR` of 400 recorded as what the eager version cost.

> **Read 235 as a ceiling, not a target.** The benchmark's probe cell is 8dp tall, so seven of the
> twelve months fit in the test window at once and a lazy list has to compose all of them. With a day
> cell of any realistic height — the built-in one is an order of magnitude taller — far fewer months
> are on screen and the count falls with them. That is the whole point of the container being lazy,
> and it is why the budget is "materially below the eager cost" rather than a tight fit.

It was never a *recomposition* problem: once composed, a caller recomposition costs zero cells, and
paging between years reuses the pager's own slot management. It was a first-frame and
memory-footprint problem, worst on a cold page in the `HorizontalPager`.

### `KalendarGrid`'s `mutableStateMapOf` written from `onGloballyPositioned`

`KalendarGrid.kt` holds a `mutableStateMapOf<LocalDate, LayoutCoordinates>`, written from
`onGloballyPositioned` (layout phase) and read during composition. That is a textbook cross-phase
back-write, and it is the one thing here I would still call a latent hazard.

In practice the cost is bounded: the writes only change when layout changes, so they cost one extra
composition pass after the first layout and nothing per frame. Measured indirectly — the
"caller recomposes" cases settle at zero cells, which they could not if the map were churning.

It is correct today for a reason worth preserving: the value read during composition
(`targetPosition`) is only used to key a `LaunchedEffect` and as a first-frame fallback, while the
actual positioning happens in `Modifier.offset { }` — the placement phase. So the frame-rate path
never reads snapshot state during composition. The comment above `targetPosition` shows this was
thought about.

I did not change it. A safe improvement, if someone wants it, is to move the coordinate lookup used
by `dateAtOffset` off the snapshot map entirely (it is only read from pointer callbacks, never
composition), leaving composition reading a single `selectedDate` entry. That is a real
simplification, not a measured win.

### `byDateExpandingSpans()` and `scheduleBlocks()`

Both are already guarded. `byDateExpandingSpans()` is called inside `remember(events)` in every view
that indexes by date, and `remember` compares its key with `equals` regardless of Compose stability —
so a value-equal event list does not rebuild the map. Confirmed by
`freshlyAllocatedEqualParametersRecomposeNothing`, which hands the view a brand-new list every
recomposition and still costs zero cells. If you are building on the
[engine](engine.md) directly, wrap the call the same way.

`scheduleBlocks()` re-packs overlaps per page, which is `O(n log n)` in the events of one day. Not
measured — the visible-page event count is small enough that this was not worth instrumenting, and
saying so is more honest than inventing a number.

### `HorizontalPager` over `Int.MAX_VALUE` pages, `LazyColumn` over `Int.MAX_VALUE` items

Not a problem. Both are lazy containers: page and item count is an index space, not an allocation.
Only visible pages are composed. This is the standard idiom for unbounded calendar paging.
Reasoned, not measured — there is nothing to measure, since nothing is allocated per page.

### The now-indicator timer — the fix holds

Now measured as well as read. `aNowIndicatorTickRecomposesNoScheduleBlock` advances a fixed clock past
the tick interval and asserts **zero** event-block slots recompose while the line itself moves —
`128.0.dp` to `129.0.dp` on the run above.

Three structural properties make that possible, and all three are intact:

- **One timer per view, not one per column.** `rememberKalendarNowState(timeSource, nowIndicatorTick)`
  is called once in `KalendarSchedule` and once in `KalendarScheduleWeek`, and its `LaunchedEffect`
  is keyed on `(timeSource, tick)`.
- **"Which column is today" goes through `derivedStateOf`**, so it invalidates on a date change
  rather than every minute: `remember(nowState) { derivedStateOf { nowState.value.date } }`.
- **The minute-by-minute value is read in the placement phase, not composition.** `KalendarNowLine`
  in `component/KalendarHourGrid.kt` takes `now: () -> LocalDateTime` and calls it inside
  `Modifier.offset { }`, so a minute passing re-places one line instead of recomposing the indicator,
  its slot content, and everything above the read. Its *visibility* — whether the clock is inside
  `KalendarViewConfig.scheduleVisibleHours` — goes through `derivedStateOf`, whose result changes at
  most twice a day.

This is the shape to copy if you are building your own hour grid on the
[engine](engine.md): hold the reading as a `State`, and read it in `offset`.

### Keyed event slots

`insertingAnEarlierEventRecomposesOnlyTheNewScheduleBlock` inserts one event at the top of a day
already holding eight, and asserts at most **2** slot compositions — measured, 1.

Unkeyed, the loop body would be memoized by position, so an insertion at the top re-associates every
slot below it with a different event: nine compositions, and any state a caller's card holds lands on
the wrong block. Keyed by the event, the existing subtrees move instead of being rebuilt.

The same assertion exists for `KalendarResourceView`, whose lanes have the same hazard.

## Regressions this catches

`KalendarRecompositionBenchmark` fails if a view loses `restartable skippable`, if a token class's
`equals` becomes identity-based, if `KalendarYear`'s month column stops being lazy, if event slots
lose their keys, if a now-indicator tick starts recomposing blocks, or if arrow-key focus movement
starts recomposing cells. It does not catch draw-phase or layout-phase regressions; there is no
coverage for those yet.
