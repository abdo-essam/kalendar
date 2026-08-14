# Kalendar

Kalendar is a calendar library for **Compose Multiplatform**. It ships seven standalone calendar
composables — from a one-row week strip to a Google-Calendar-style hourly time grid — that you call
directly, style through a complete design-token layer, and drive with plain hoisted state.

It runs on **Android**, **iOS**, **Desktop (JVM)**, and **wasmJs**, and it does not require your app
to use Material 3.

> **Try it live.** Every view runs in your browser in the
> [**Kalendar playground**](https://hi-manshu.github.io/Kalendar/playground/) — the sample app
> compiled to WebAssembly. No install, no setup.

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
