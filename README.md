## 📅 Kalendar: The Calendar Your App Deserves

![Kalendar](img/banner.png)
![Github: Open Issues](https://img.shields.io/github/issues-raw/hi-manshu/kalendar?color=7E8EFB&label=Kalendar%3A%20Open%20Issues)
[![Kalendar](https://img.shields.io/badge/Kotlin%20Weekly-%23286-orange)](https://mailchi.mp/kotlinweekly/kotlin-weekly-286)
[![Kalendar](https://img.shields.io/badge/Android%20Weekly-%23533-Pink)](https://androidweekly.net/issues/issue-533)
[![Kalendar](https://img.shields.io/badge/Canopas%20Engineering-%2372-blue)](https://blog.canopas.com/android-stack-weekly-issue-72-20658bea40a2)
![Kalendar](https://img.shields.io/maven-central/v/com.himanshoe/kalendar?style=flat&label=Kalendar)
![KalendarFoundation](https://img.shields.io/maven-central/v/com.himanshoe/kalendar-foundation?style=plastic&label=Kalendar-Foundation)
[![Github Followers](https://img.shields.io/github/followers/hi-manshu?label=Follow&style=social)](https://github.com/hi-manshu)
[![Twitter Follow](https://img.shields.io/twitter/follow/hi_man_shoe?label=Follow&style=social)](https://twitter.com/hi_man_shoe)

Kalendar is no ordinary library — it’s the _Elder Wand_ of calendar components, crafted for
Compose (now with KMP support) sorcerers who demand both power and elegance.
With the flick of your wrist (or a few lines of Kotlin), you conjure a magical, interactive calendar
that bends to your will. With Kalendar, your app becomes as organized as Hermione’s study schedule —
but far more fun.

So go on, Wield this enchanted tool and create a calendar so charming and powerful, even *
*_Dumbledore_** would pause to admire it.

## 🎉 Why Kalendar?

- **🪄 Unparalleled Customization**: Shape your calendar like it’s made of Transfiguration magic.
  Colors, styles, themes — you control it all.
- **📜 Event Management, Sorted**: Whether it’s the next **Quidditch match** or your weekly stand-up,
  display events clearly and beautifully.
- **✨ Interactive and Responsive**:Tap, click, and swipe — and watch your calendar respond like it’s
  enchanted.
- **🚀 KMP/CMP-Friendly**: No weird incantations needed — just simple, idiomatic Multiplatform
  Compose code.

So unless you fancy scribbling your dates on a Howler, let Kalendar handle your scheduling magic.

> _Made with ❤️ for Android Developers by Himanshu_

## Live demo

Try every view in your browser (Compose for Web/wasm build of the sample app):
**[hi-manshu.github.io/Kalendar](https://hi-manshu.github.io/Kalendar/)** — deployed from `main`
by the `Deploy Demo` workflow. (Requires GitHub Pages to be set to "GitHub Actions" in the repo
settings the first time.) The same deployment also publishes the Dokka API reference under
[`/api/kalendar/`](https://hi-manshu.github.io/Kalendar/api/kalendar/) and
[`/api/kalendar-foundation/`](https://hi-manshu.github.io/Kalendar/api/kalendar-foundation/).

## Standalone views (recommended)

- **[KalendarMonth](docs/views/month.md)**: full month grid, arrow-click and swipe navigation together.
- **[KalendarWeek](docs/views/week.md)**: single week row, arrow-click and swipe navigation together.
- **[KalendarYear](docs/views/year.md)**: 12-month year overview, arrow-click and swipe navigation together.
- **[KalendarTimeline](docs/views/timeline.md)**: continuous, boundless vertical scroll through every month — no paging.
- **[KalendarSchedule](docs/views/schedule.md)**: hourly time grid for a single day — duration-sized event blocks, overlap packing, and a now-indicator.
- **[KalendarScheduleWeek](docs/views/schedule-week.md)**: the 7-day variant — day columns sharing one hour gutter.
- **[KalendarAgenda](docs/views/agenda.md)**: scrollable list of events grouped by date — no grid, no paging.

All views are restylable via **[KalendarTheme](docs/theming.md)** colour tokens — no Material dependency required in your app's design system.

Events can be handed over up front as a list, or **[loaded a visible range at a
time](docs/loading-events.md)** when they live in a database or behind a network call: the view
reports the range it is about to draw, adjacent pages are prefetched so a swipe does not land on an
empty grid, and a `loadingContent` slot keeps "no events yet" from looking like "no events".

Every view is a top-level composable called directly — `KalendarMonth(...)`, `KalendarAgenda(...)`.
There is no `Kalendar(type = ...)` entry point and no `KalendarType`: both were removed in 2.0.
They use the event model from `kalendar-foundation` (`KalendarEvent`/`BasicKalendarEvent`), which
`kalendar` re-exports as an `api` dependency — adding `com.himanshoe:kalendar` alone is enough. See
their docs for usage.

## Device calendar sync

**[kalendar-sync](docs/sync.md)**: reads, writes, and exports events from the device's real calendar
(Android `CalendarContract`, iOS EventKit, a file-backed `.ics` provider on Desktop) — no
third-party account required. Includes RFC 5545 recurrence and iCal import/export, and is
independent of `kalendar`/`kalendar-foundation`.

Recurring series can be edited or deleted at **[one occurrence, this and following, or the whole
series](docs/sync.md#editing-one-occurrence-of-a-series)** — each scope resolving to an explicit set
of writes, so you can see exactly what your backend will be asked to do.

## 🎉 Getting Started

### Version Catalog

If you're using Version Catalog, you can configure the dependency by adding it to your
`libs.versions.toml` file as follows:
<details open>

```toml
[versions]
kalendar = "<version>"
kalendarFoundation = "<version>"
kalendarSync = "<version>"

[libraries]
kalendar = { module = "com.himanshoe:kalendar", version.ref = "kalendar" }
kalendar-foundation = { module = "com.himanshoe:kalendar-foundation", version.ref = "kalendarFoundation" }
kalendar-sync = { module = "com.himanshoe:kalendar-sync", version.ref = "kalendarSync" }
```

</details>

### Gradle

<details>
Add the dependency below to your module's `build.gradle.kts` file:

```gradle
dependencies {
    implementation("com.himanshoe:kalendar:$version")
    implementation("com.himanshoe:kalendar-foundation:$version")
    implementation("com.himanshoe:kalendar-sync:$version") // optional: device calendar sync

    // if you're using Version Catalog
    implementation(libs.kalendar)
    implementation(libs.kalendar.foundation)
    implementation(libs.kalendar.sync)

}
```

For Kotlin Multiplatform, add the dependency below to your commonMain source set's
`build.gradle.kts` file:

```gradle
sourceSets {
    commonMain.dependencies {
          implementation(libs.kalendar)
          implementation(libs.kalendar.foundation)
          implementation(libs.kalendar.sync) // optional: device calendar sync
     }
}
```

</details>

## 📖Documentation

You can find it here: [Kalendar Documentation](https://hi-manshu.github.io/Kalendar/)



