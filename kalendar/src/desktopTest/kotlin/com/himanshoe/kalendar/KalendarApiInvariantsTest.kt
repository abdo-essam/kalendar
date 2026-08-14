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

package com.himanshoe.kalendar

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Holds the API rules this project decided on, against the checked-in ABI dumps.
 *
 * These were previously enforced by nothing but memory, which is not enforcement: they are exactly
 * the kind of rule that is obeyed for a year and then quietly broken by a one-line change that looks
 * harmless. `apiCheck` guarantees the dumps match the source, so asserting against the dumps is the
 * same as asserting against the API — and it costs nothing at runtime.
 */
class KalendarApiInvariantsTest {

    private fun dumps(): List<File> = listOf(
        "apiDumpDir" to "kalendar",
        "foundationApiDumpDir" to "kalendar-foundation",
        "syncApiDumpDir" to "kalendar-sync",
    ).flatMap { (property, module) ->
        val dir = File(checkNotNull(System.getProperty(property)) { "$property system property missing" })
        listOf("android", "desktop").mapNotNull { platform ->
            File(dir, "$platform/$module.api").takeIf { it.isFile }
        }
    }.also {
        if (it.isEmpty()) fail("no ABI dumps found — the api* system properties are pointing nowhere")
    }

    /**
     * No `data class` may reach any public API.
     *
     * A `data class` compiles one `componentN` accessor per property plus a `copy` whose name
     * carries a hash of its signature. Adding a property therefore rewrites `copy` and breaks every
     * consumer compiled against the previous jar — at link time, which is the worst moment to learn
     * about it. `KalendarEvent` alone has gained two properties since 1.0, so this is a live hazard
     * rather than a hypothetical one, and it is why eight public types here hand-write `copy`,
     * `equals`, `hashCode` and `toString`.
     */
    @Test
    fun publicApi_hasNoDataClassAccessors() {
        val offenders = dumps().flatMap { dump ->
            dump.readLines()
                .filter { COMPONENT_ACCESSOR.containsMatchIn(it) }
                .map { "${dump.parentFile.parentFile.name}/${dump.parentFile.name}: ${it.trim()}" }
        }
        assertTrue(
            offenders.isEmpty(),
            "a data class reached the public API — hand-write copy/equals/hashCode/toString " +
                "instead:\n${offenders.joinToString("\n")}",
        )
    }

    /**
     * No composable may take a `State` or `MutableState` parameter.
     *
     * Such a parameter makes a plain value impossible to pass and leaks a storage decision into a
     * signature, and the deferral it buys is available from a `() -> T` lambda without the coupling.
     * The last holdout here carried an adapter whose only job was converting a lambda back into the
     * `State` it demanded, which is what the shape being wrong looks like from the inside.
     */
    @Test
    fun publicApi_takesNoStateParameters() {
        val offenders = dumps().flatMap { dump ->
            dump.readLines()
                .filter { it.contains("public") && STATE_PARAMETER.containsMatchIn(it) }
                .map { "${dump.parentFile.name}: ${it.trim()}" }
        }
        assertTrue(
            offenders.isEmpty(),
            "a composable takes State<T> — pass a value, or a lambda for a deferred read:\n" +
                offenders.joinToString("\n"),
        )
    }

    /**
     * The engine must stay reachable without the UI module.
     *
     * `kalendar-foundation`'s whole purpose is that a consumer can build their own calendar on it.
     * A type from `com.himanshoe.kalendar` appearing in its ABI would mean the dependency had been
     * inverted, and the guarantee quietly lost.
     */
    @Test
    fun foundationApi_referencesNothingFromTheUiModule() {
        val foundationDumps = dumps().filter { it.name.startsWith("kalendar-foundation") }
        assertTrue(foundationDumps.isNotEmpty(), "no kalendar-foundation dump found to check")

        val offenders = foundationDumps.flatMap { dump ->
            dump.readLines()
                .filter { UI_MODULE_TYPE.containsMatchIn(it) }
                .map { "${dump.parentFile.name}: ${it.trim()}" }
        }
        assertTrue(
            offenders.isEmpty(),
            "kalendar-foundation's API references the UI module, inverting the dependency:\n" +
                offenders.joinToString("\n"),
        )
    }

    /**
     * No compiler-generated composable-lambda holder may appear in a dump.
     *
     * The Compose compiler hoists capture-free composable lambdas into `ComposableSingletons$*`
     * classes whose accessors carry a hash of the lambda's source position. Seven of them, and 26
     * accessors, used to sit in each dump and re-hash whenever an unrelated edit moved a lambda —
     * which is how a real ABI change went through review unnoticed once. They are excluded by
     * `ignoredClasses` in kalendar/build.gradle.kts, and that list has to be spelled out class by
     * class because the validator matches it exactly. This is what notices when a new one is added.
     */
    @Test
    fun publicApi_hasNoComposableLambdaHolders() {
        val offenders = dumps().flatMap { dump ->
            dump.readLines()
                .filter { LAMBDA_HOLDER.containsMatchIn(it) }
                .map { "${dump.parentFile.parentFile.name}/${dump.parentFile.name}: ${it.trim()}" }
        }
        assertTrue(
            offenders.isEmpty(),
            "compiler-generated lambda holders reached the dump — add them to ignoredClasses in " +
                "the module's build.gradle.kts and re-run apiDump:\n${offenders.joinToString("\n")}",
        )
    }

    private companion object {
        val COMPONENT_ACCESSOR = Regex("""fun component\d+\s*\(""")
        val LAMBDA_HOLDER = Regex("""ComposableSingletons\$|fun getLambda\$""")
        val STATE_PARAMETER = Regex("""Landroidx/compose/runtime/(Mutable)?State;""")

        /** `com/himanshoe/kalendar/…` but not `com/himanshoe/kalendar/foundation/…`. */
        val UI_MODULE_TYPE = Regex("""Lcom/himanshoe/kalendar/(?!foundation/)""")
    }
}
