# Kalendar

Kalendar is a calendar library for **Compose Multiplatform**. It ships seven standalone calendar
composables — from a one-row week strip to a Google-Calendar-style hourly time grid — that you call
directly, style through a complete design-token layer, and drive with plain hoisted state.

It runs on **Android**, **iOS**, **Desktop (JVM)**, and **wasmJs**, and it does not require your app
to use Material 3.

> **Try it live.** Every view runs in your browser in the
> [**Kalendar playground**](https://hi-manshu.github.io/Kalendar/playground/) — the sample app
> compiled to WebAssembly. No install, no setup.

## The seven views

| View | What it is |
|---|---|
| [`KalendarWeek`](views/week.md) | A single week row, paged by week — arrow buttons and swipe drive the same state. |
| [`KalendarMonth`](views/month.md) | The full month grid, paged by month, with drag-to-select and drag-to-reschedule. |
| [`KalendarYear`](views/year.md) | All twelve months of a year in one vertically scrolling page, paged by year. |
| [`KalendarTimeline`](views/timeline.md) | A continuous vertical scroll through every month, with no page boundaries and a sticky month header. |
| [`KalendarSchedule`](views/schedule.md) | An hourly time grid for one day — duration-sized event blocks, overlap packing, and a now-indicator. |
| [`KalendarScheduleWeek`](views/schedule-week.md) | The seven-day time grid: day columns sharing one hour gutter. |
| [`KalendarAgenda`](views/agenda.md) | A scrolling list of the events themselves, grouped by date, skipping every empty day. |

<div class="shot-gallery">
<figure>
<img src="images/week-theme.png" alt="A one-week row for 9–15 March 2026 with the 12th selected and event dots under four of the days.">
<figcaption><a href="views/week.md"><code>KalendarWeek</code></a></figcaption>
</figure>
<figure>
<img src="images/month-theme.png" alt="The March 2026 month grid with the 12th selected and event dots with a plus-one overflow badge.">
<figcaption><a href="views/month.md"><code>KalendarMonth</code></a></figcaption>
</figure>
<figure>
<img src="images/year-theme.png" alt="The top of the 2026 year page, with the January and February grids stacked in one scrolling column.">
<figcaption><a href="views/year.md"><code>KalendarYear</code></a></figcaption>
</figure>
<figure>
<img src="images/timeline-theme.png" alt="A continuous month list scrolled to the join between March and April 2026, with no page break between them.">
<figcaption><a href="views/timeline.md"><code>KalendarTimeline</code></a></figcaption>
</figure>
<figure>
<img src="images/schedule-theme.png" alt="The hourly day grid for 10 March 2026 with an all-day chip and two overlapping meetings packed side by side.">
<figcaption><a href="views/schedule.md"><code>KalendarSchedule</code></a></figcaption>
</figure>
<figure>
<img src="images/schedule-week-theme.png" alt="The seven-day time grid for 9–15 March 2026, seven day columns sharing one hour gutter.">
<figcaption><a href="views/schedule-week.md"><code>KalendarScheduleWeek</code></a></figcaption>
</figure>
<figure>
<img src="images/agenda-theme.png" alt="An agenda list for March 2026, each date a heading with the day's events as cards underneath it.">
<figcaption><a href="views/agenda.md"><code>KalendarAgenda</code></a></figcaption>
</figure>
</div>

Every picture above is rendered from the library when this site is built, so it shows the current
default theme rather than whatever the calendar looked like when somebody last took a screenshot.

There is also a separate [`kalendar-sync`](sync.md) module that reads and writes the device's real
calendar.

## Install

Current coordinates:

| Artifact | Version | What it gives you |
|---|---|---|
| `com.himanshoe:kalendar` | `2.0.0-RC3` | The calendar views. Pulls in `kalendar-foundation` as an `api` dependency. |
| `com.himanshoe:kalendar-foundation` | `2.0.0` | The event model: `KalendarEvent`, `BasicKalendarEvent`, `KalendarEvents`. |
| `com.himanshoe:kalendar-sync` | `1.0.0` | Optional. Device calendar read/write, recurrence, and iCal. Independent of the other two. |

### Version catalog

In `gradle/libs.versions.toml`:

```toml
[versions]
kalendar = "2.0.0-RC3"
kalendarFoundation = "2.0.0"
kalendarSync = "1.0.0"

[libraries]
kalendar = { module = "com.himanshoe:kalendar", version.ref = "kalendar" }
kalendar-foundation = { module = "com.himanshoe:kalendar-foundation", version.ref = "kalendarFoundation" }
kalendar-sync = { module = "com.himanshoe:kalendar-sync", version.ref = "kalendarSync" }
```

Then in your module's `build.gradle.kts`:

```kotlin
dependencies {
    implementation(libs.kalendar)
    implementation(libs.kalendar.sync) // optional: device calendar sync
}
```

### Plain Gradle

Android or JVM:

```kotlin
dependencies {
    implementation("com.himanshoe:kalendar:2.0.0-RC3")
    implementation("com.himanshoe:kalendar-sync:1.0.0") // optional
}
```

Kotlin Multiplatform:

```kotlin
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation("com.himanshoe:kalendar:2.0.0-RC3")
            implementation("com.himanshoe:kalendar-sync:1.0.0") // optional
        }
    }
}
```

> **Tip:** you usually only need one dependency. `kalendar` declares `kalendar-foundation` as an
> `api` dependency, so `KalendarEvent` and `BasicKalendarEvent` are already on your compile
> classpath. Add `kalendar-foundation` explicitly only if you want it without the views.

## Hello, calendar

```kotlin
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.himanshoe.kalendar.view.KalendarMonth
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlin.time.Clock

@Composable
fun MyCalendar() {
    val today = remember { Clock.System.todayIn(TimeZone.currentSystemDefault()) }
    var selected by remember { mutableStateOf(setOf(today)) }

    KalendarMonth(
        selectedDate = today,
        selectedDates = selected,
        onDateClick = { date, _ -> selected = setOf(date) },
    )
}
```

That is the whole integration. Everything else — [theming](theming.md),
[custom day cells](customization.md), [events](events.md), [localization](localization.md) — is
opt-in on top of it.

## Where to go next

- [**Getting started**](getting-started.md) — the guided path: first calendar, selection, events.
- [**Views**](views/index.md) — one page per composable, with the full parameter table.
- [**Theming**](theming.md) — the token layer: colours, type, shapes, dimensions, motion, strings.
- [**Customization**](customization.md) — `dayContent`, header slots, and keeping custom cells accessible.
- [**Events**](events.md) — the event model, multi-day spans, indicator dots, drag-to-reschedule.
- [**Configuration**](configuration.md) — the `KalendarViewConfig` knobs every view shares.
- [**Localization**](localization.md) — `KalendarStrings` and the date formatter hooks.
- [**Device sync**](sync.md) — reading and writing the real device calendar.
- [**Migration from 1.x**](migration.md) — what `KalendarType` and `KalendarConfig` became.
- [**Changelog**](changelog.md) — everything that has changed.

The generated API reference lives at
[`/api/kalendar/`](https://hi-manshu.github.io/Kalendar/api/kalendar/) and
[`/api/kalendar-foundation/`](https://hi-manshu.github.io/Kalendar/api/kalendar-foundation/).
