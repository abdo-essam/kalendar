/*
 *
 *  * Copyright 2026 Kalendar Contributors (https://www.himanshoe.com). All rights reserved.
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  * http://www.apache.org/licenses/LICENSE-2.0
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *  *
 *
 */

import com.android.build.api.dsl.LibraryExtension
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.publish)
    alias(libs.plugins.dokka)
    alias(libs.plugins.binary.compatibility.validator)
}

mavenPublishing {
    publishToMavenCentral()
    signAllPublications()
}

kotlin {
    explicitApi()

    androidTarget {
        compilations.all {
            compileTaskProvider.configure {
                compilerOptions {
                    jvmTarget.set(JvmTarget.JVM_11)
                }
            }
        }
    }
    jvm("desktop") {
        compilations.all {
            compileTaskProvider.configure {
                compilerOptions {
                    jvmTarget.set(JvmTarget.JVM_11)
                }
            }
        }
    }
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser {
            binaries.executable()
        }
    }

    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64()
    ).forEach {
        it.binaries.framework {
            baseName = "kalendar"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.ui)
            implementation(compose.foundation)
            // Views render entirely in Foundation; material3 is consulted only by
            // KalendarThemeDefaults, to derive default token values from the host app's theme.
            implementation(compose.material3)
            implementation(libs.kotlinx.datetime)
            // api, not implementation: KalendarEventSource hands back a Flow, so kotlinx-coroutines
            // is part of this module's public API rather than an internal detail of it.
            api(libs.kotlinx.coroutines.core)
            api(project(":kalendar-foundation"))
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
        named("desktopTest") {
            dependencies {
                implementation(compose.desktop.uiTestJUnit4)
                implementation(compose.desktop.currentOs)
            }
        }
    }
}

// Compose compiler metrics and stability reports. Off by default: they add a full non-incremental
// recompile plus report serialisation to every build, which is not worth paying on CI for data
// nothing gates on. Regenerate on demand with
//   ./gradlew :kalendar:compileKotlinDesktop -PkalendarComposeMetrics --no-configuration-cache
// (the configuration-cache opt-out is needed because the flag is read at configuration time).
// Output lands in kalendar/build/compose-reports and kalendar/build/compose-metrics.
// See docs/performance.md.
if (providers.gradleProperty("kalendarComposeMetrics").isPresent) {
    composeCompiler {
        metricsDestination.set(layout.buildDirectory.dir("compose-metrics"))
        reportsDestination.set(layout.buildDirectory.dir("compose-reports"))
    }
}

compose.resources {
    publicResClass = false
    packageOfResClass = "com.himanshoe.kalendar.resources"
    generateResClass = auto
}

// The Compose compiler hoists every capture-free composable lambda into a ComposableSingletons$*
// holder, whose accessors are named getLambda$<hash of the lambda's source position>. They are
// public only because the compiler has nowhere private to put them: no consumer can name one, and
// none of it is a compatibility promise. Left in, seven such classes and 26 accessors sat in each
// dump and re-hashed whenever an unrelated edit moved a lambda — noise a real ABI change already
// hid inside once.
//
// The names must be spelled out: this validator matches ignoredClasses exactly, not by prefix or
// glob. A new file with a capture-free composable lambda therefore adds a holder that belongs here,
// and KalendarApiInvariantsTest fails until it is added rather than letting the noise creep back.
apiValidation {
    ignoredClasses.addAll(
        listOf(
            "com.himanshoe.kalendar.ComposableSingletons\$KalendarAgendaKt",
            "com.himanshoe.kalendar.ComposableSingletons\$KalendarDatePickerKt",
            "com.himanshoe.kalendar.ComposableSingletons\$KalendarMonthKt",
            "com.himanshoe.kalendar.ComposableSingletons\$KalendarResourceViewKt",
            "com.himanshoe.kalendar.ComposableSingletons\$KalendarScheduleKt",
            "com.himanshoe.kalendar.ComposableSingletons\$KalendarTimelineKt",
            "com.himanshoe.kalendar.ComposableSingletons\$KalendarWeekKt",
            "com.himanshoe.kalendar.ComposableSingletons\$KalendarYearKt",
            "com.himanshoe.kalendar.component.ComposableSingletons\$KalendarDayCellKt",
            "com.himanshoe.kalendar.component.ComposableSingletons\$KalendarGridKt",
            "com.himanshoe.kalendar.component.ComposableSingletons\$KalendarNavigationHeaderKt",
        ),
    )
}

tasks.withType<Test>().configureEach {
    systemProperty(
        "goldenDir",
        layout.projectDirectory.dir("src/desktopTest/resources/goldens").asFile.absolutePath,
    )
    systemProperty("updateGoldens", providers.gradleProperty("updateGoldens").getOrElse("false"))
    // The checked-in ABI dumps, so a test can hold the API invariants this project relies on.
    systemProperty("apiDumpDir", layout.projectDirectory.dir("api").asFile.absolutePath)
    systemProperty(
        "foundationApiDumpDir",
        rootProject.layout.projectDirectory.dir("kalendar-foundation/api").asFile.absolutePath,
    )
    systemProperty(
        "syncApiDumpDir",
        rootProject.layout.projectDirectory.dir("kalendar-sync/api").asFile.absolutePath,
    )
}
// Everything in :kalendar:desktopTest except the three image comparisons is portable, so the task
// runs everywhere and only the comparisons are conditional. CI used to skip the whole task for
// their sake, which meant ~209 tests — every drag, keyboard-navigation, accessibility and layout
// assertion in the library — ran nowhere at all.
//
// Goldens are rasterised on macOS and text rasterises differently elsewhere, so off macOS they
// would fail on font metrics rather than on the library. Override in either direction with
// -PkalendarGoldenTests=true|false.
val runGoldenImageTests = providers.gradleProperty("kalendarGoldenTests")
    .map(String::toBoolean)
    .orElse(providers.systemProperty("os.name").map { it.startsWith("Mac") })
    .get()

tasks.named<Test>("desktopTest") {
    useJUnit {
        if (!runGoldenImageTests) {
            excludeCategories("com.himanshoe.kalendar.GoldenImageTests")
        }
    }
}

extensions.configure<LibraryExtension> {
    namespace = "com.himanshoe.kalendar"
    compileSdk = 36
    defaultConfig {
        minSdk = 24
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}
val detekt by configurations.creating
dependencies {
    detekt(libs.detekt.cli)
    detekt(libs.detekt.formatting)
    debugImplementation(compose.uiTooling)
    dokkaPlugin(libs.dokka)
}
tasks.register<JavaExec>("detekt") {
    mainClass = "io.gitlab.arturbosch.detekt.cli.Main"
    classpath = detekt

    val input = projectDir
    val config = "${rootProject.projectDir}/detekt.yml"
    // detekt CLI --excludes takes globs, not regexes; the old regex form matched nothing
    val exclude = "**/build/**,**/resources/**"
    val report = "sarif:${layout.buildDirectory.file("reports/detekt/detekt.sarif").get()}"
    val params = listOf(
        "-i",
        input,
        "-c",
        config,
        "-ex",
        exclude,
        "-r",
        report,
        "--build-upon-default-config"
    )

    args(params)
}
