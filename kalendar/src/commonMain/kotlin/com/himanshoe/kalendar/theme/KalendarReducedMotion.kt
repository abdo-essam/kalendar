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

package com.himanshoe.kalendar.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Whether the person using this device has asked the operating system to minimise motion.
 *
 * Each platform reads its own signal — Android the global animator duration scale, iOS the Reduce
 * Motion accessibility switch, web the `prefers-reduced-motion` media query — and desktop, which
 * has no signal the JVM can reach, reports `false`. Platforms that can observe the setting return
 * a snapshot-backed read, so a change while the app is open recomposes whatever called this.
 *
 * This is the raw platform answer. Kalendar code should read [kalendarPrefersReducedMotion]
 * instead, which lets [LocalKalendarReducedMotionOverride] stand in for the platform.
 */
@Composable
internal expect fun platformPrefersReducedMotion(): Boolean

/**
 * Overrides the platform reduced-motion signal for everything composed beneath the provider.
 *
 * The default `null` means "ask the platform". Tests and previews provide `true` or `false` to
 * exercise both sides of the setting deterministically — on desktop, where
 * [platformPrefersReducedMotion] has no signal to read, this is the only way to reach the
 * reduced-motion path:
 *
 * ```kotlin
 * CompositionLocalProvider(LocalKalendarReducedMotionOverride provides true) {
 *     KalendarTheme { /* composes with animations disabled */ }
 * }
 * ```
 */
internal val LocalKalendarReducedMotionOverride: ProvidableCompositionLocal<Boolean?> =
    staticCompositionLocalOf { null }

/**
 * The reduced-motion answer [KalendarThemeDefaults.animations] acts on: the ambient
 * [LocalKalendarReducedMotionOverride] when one is provided, otherwise the platform's own signal
 * from [platformPrefersReducedMotion].
 */
@Composable
internal fun kalendarPrefersReducedMotion(): Boolean =
    LocalKalendarReducedMotionOverride.current ?: platformPrefersReducedMotion()
