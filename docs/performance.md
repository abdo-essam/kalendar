# Performance

The measured baseline for `:kalendar`, how to reproduce it, and what the numbers mean.

Everything below was produced by running the commands in this document. Where something is
reasoning rather than measurement, it says so.

## Summary

The library is in good shape. All seven public views are `restartable skippable`, every class the
compiler sees is stable, and a recomposition above a calendar costs zero day cells. The one real
cost found is `KalendarYear`, which eagerly composes **404 day cells** on its first frame.

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

From `kalendar-composables.txt`, 89 composables, 59 `restartable skippable`. Every public view:

```
restartable skippable fun com.himanshoe.kalendar.view.KalendarMonth(
restartable skippable fun com.himanshoe.kalendar.view.KalendarWeek(
restartable skippable fun com.himanshoe.kalendar.view.KalendarYear(
restartable skippable fun com.himanshoe.kalendar.view.KalendarAgenda(
restartable skippable fun com.himanshoe.kalendar.view.KalendarTimeline(
restartable skippable fun com.himanshoe.kalendar.view.KalendarSchedule(
restartable skippable fun com.himanshoe.kalendar.view.KalendarScheduleWeek(
```

The 30 composables that are neither restartable nor skippable are all `remember*` factories and
`KalendarTheme` accessors. A composable that returns a value is never skippable, by construction —
that is correct, not a finding.

`kalendar-classes.txt` lists 26 classes, **all** of them `stable`, none `unstable` or `runtime`.

### The token classes are genuinely structural

The token classes were recently converted from `data class` to hand-written `equals`/`hashCode`.
An identity-based `equals` would have made every view silently non-skippable. It did not happen —
verified two ways.

By inspection, `KalendarColors`, `KalendarTypography`, `KalendarShapes`, `KalendarDimensions`,
`KalendarAnimations`, `KalendarThemeTokens`, `KalendarStrings`, `KalendarViewConfig`,
`KalendarDayScope` and `KalendarTimeSource` all compare field by field after the usual
`if (this === other) return true` fast path. None of them stops at identity.

By report, each is `stable` with every field enumerated, e.g.:

```
stable class com.himanshoe.kalendar.view.KalendarColors {
  stable val background: Color
  stable val selectionBackground: Color
  ...
```

Note the report alone would **not** have caught an identity-based `equals`: all of these carry
`@Immutable`, which asserts stability regardless of what `equals` does. The compiler would still
have said `stable` while every view stopped skipping at runtime. That is why the benchmark below
exists, and why it asserts on behaviour rather than on the report.

### "unstable" parameters are not a problem here

55 parameters are marked `unstable`, and they are all `kotlinx.datetime.LocalDate`, `List`, or
`Set`:

```
restartable skippable fun com.himanshoe.kalendar.view.KalendarMonth(
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
was written, measured, and **reverted**: it cleaned the report from 55 unstable parameters to 0 and
changed no runtime number whatsoever. It is not worth the standing claim that every `List` handed
to the library is immutable. If the benchmark above ever starts failing, that file is the fix, and
it belongs at `kalendar/compose-stability.conf` wired in via `composeCompiler {
stabilityConfigurationFiles }`. Note the file format takes `//` comments only — `#` fails the build
with `Error parsing stability configuration file on line 0`.

For the same reason, wrapping `KalendarEvents` in an `@Immutable` class would buy nothing
measurable and would cost real API churn. Not recommended.

## The benchmark

`kalendar/src/desktopTest/kotlin/com/himanshoe/kalendar/view/KalendarRecompositionBenchmark.kt`,
run by the ordinary `:kalendar:desktopTest`. It counts **compositions**, not milliseconds — a
composition count reproduces across machines, a timing does not.

```bash
./gradlew :kalendar:desktopTest --tests "*KalendarRecompositionBenchmark*"
```

Measured baseline:

| Measurement | Cells |
|---|---|
| `KalendarMonth` first composition | 37 |
| `KalendarWeek` first composition | 7 |
| `KalendarYear` first composition | **404** |
| `KalendarMonth`, caller recomposes, hoisted params | 0 |
| `KalendarWeek`, caller recomposes, hoisted params | 0 |
| `KalendarMonth`, caller recomposes, freshly allocated equal params | 0 |

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

### `KalendarYear` — 404 eager cells. Real, and the only real one

**Measured.** `KalendarYear` composes 404 day cells on its first frame. Twelve month grids sit in a
non-lazy `verticalScroll` `Column` (`KalendarYear.kt:122-125`), so every cell of every month is
composed whether or not it is on screen — roughly eleven months of invisible work.

It is not a *recomposition* problem: once composed, a caller recomposition costs zero cells, and
paging between years reuses the pager's own slot management. It is a first-frame and
memory-footprint problem, worst on a cold page in the `HorizontalPager`.

Not fixed here, deliberately. The fix is to make that `Column` a `LazyColumn`, which changes scroll
and measurement behaviour and risks the golden images — too big to land as a drive-by next to three
other agents editing the same files. `yearComposesEveryMonthEagerly` pins the current number so the
improvement is visible when someone takes it on.

### `KalendarGrid`'s `mutableStateMapOf` written from `onGloballyPositioned`

`KalendarGrid.kt:115` holds `mutableStateMapOf<LocalDate, LayoutCoordinates>`, written at line 265
inside `onGloballyPositioned` (layout phase) and read at line 118 during composition. That is a
textbook cross-phase back-write, and it is the one thing here I would still call a latent hazard.

In practice the cost is bounded: the writes only change when layout changes, so they cost one extra
composition pass after the first layout and nothing per frame. Measured indirectly — the
"caller recomposes" cases settle at zero cells, which they could not if the map were churning.

It is correct today for a reason worth preserving: the value read during composition
(`targetPosition`) is only used to key a `LaunchedEffect` and as a first-frame fallback, while the
actual positioning happens in `Modifier.offset { }` — the placement phase. So the frame-rate path
never reads snapshot state during composition. The comment at lines 128-136 shows this was thought
about.

I did not change it. A safe improvement, if someone wants it, is to move the coordinate lookup used
by `dateAtOffset` off the snapshot map entirely (it is only read from pointer callbacks, never
composition), leaving composition reading a single `selectedDate` entry. That is a real
simplification, not a measured win.

### `byDateExpandingSpans()` and `scheduleBlocks()`

Both are already guarded. `byDateExpandingSpans()` is called inside `remember(events)`
(`KalendarYear.kt:112`, `KalendarMonth.kt:115`), and `remember` compares its key with `equals`
regardless of Compose stability — so a value-equal event list does not rebuild the map. Confirmed
by `freshlyAllocatedEqualParametersRecomposeNothing`, which hands the view a brand-new list every
recomposition and still costs zero cells.

`scheduleBlocks()` re-packs overlaps per page, which is `O(n log n)` in the events of one day. Not
measured — the visible-page event count is small enough that this was not worth instrumenting, and
saying so is more honest than inventing a number.

### `HorizontalPager` over `Int.MAX_VALUE` pages, `LazyColumn` over `Int.MAX_VALUE` items

Not a problem. Both are lazy containers: page and item count is an index space, not an allocation.
Only visible pages are composed. This is the standard idiom for unbounded calendar paging.
Reasoned, not measured — there is nothing to measure, since nothing is allocated per page.

### The now-indicator timer — the fix holds

Verified by reading the code, which is the right tool here since the fix is structural.

- One timer per view, not one per column: `rememberKalendarNowState(timeSource, nowIndicatorTick)`
  is called once in `KalendarSchedule` (`KalendarSchedule.kt:171`) and once in
  `KalendarScheduleWeek` (line 306), and its `LaunchedEffect` is keyed on `(timeSource, tick)`.
- "Which column is today" goes through `derivedStateOf`, so it invalidates on date change rather
  than every minute: `remember(now) { derivedStateOf { now.value.date } }` (lines 172 and 307).
- The minute-by-minute value is read in the **placement** phase, not composition.
  `ScheduleNowIndicator` (`KalendarSchedule.kt:971`) takes `now: State<LocalDateTime>` and reads
  `now.value` inside `Modifier.offset { }`, so a minute passing re-places one line instead of
  recomposing the indicator, its slot content, and everything above the read.

All three properties are intact.

## Regressions this catches

`KalendarRecompositionBenchmark` fails if a view loses `restartable skippable`, if a token class's
`equals` becomes identity-based, or if `KalendarYear` silently changes its composition strategy. It
does not catch draw-phase or layout-phase regressions; there is no coverage for those yet.
