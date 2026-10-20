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

import android.content.ContentResolver
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

/**
 * Android: reduced motion is [Settings.Global.ANIMATOR_DURATION_SCALE] sitting at zero.
 *
 * That one setting covers both ways a person asks for it — the accessibility "Remove animations"
 * toggle writes `0` to the animation scales, and so does Developer options' "Animator duration
 * scale: Animation off". It is the same signal `ValueAnimator.areAnimatorsEnabled()` reports, read
 * directly so no API-26 gate is needed.
 *
 * The setting can change while the app runs, so it is watched with a [ContentObserver] and held in
 * snapshot state: flipping the toggle recomposes every caller with the new answer, and the
 * observer is unregistered when the caller leaves composition.
 */
@Composable
internal actual fun platformPrefersReducedMotion(): Boolean {
    val resolver = LocalContext.current.applicationContext.contentResolver
    var reduced by remember(resolver) { mutableStateOf(resolver.animatorScaleIsZero()) }
    DisposableEffect(resolver) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                reduced = resolver.animatorScaleIsZero()
            }
        }
        resolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),
            false,
            observer,
        )
        onDispose { resolver.unregisterContentObserver(observer) }
    }
    return reduced
}

/**
 * Whether the global animator duration scale is exactly zero — Android's "animations off".
 *
 * A missing setting falls back to [FULL_ANIMATOR_SCALE], the platform default, so devices that
 * have never touched the toggle animate.
 */
private fun ContentResolver.animatorScaleIsZero(): Boolean = Settings.Global.getFloat(
    this,
    Settings.Global.ANIMATOR_DURATION_SCALE,
    FULL_ANIMATOR_SCALE,
) == DISABLED_ANIMATOR_SCALE

private const val FULL_ANIMATOR_SCALE = 1f
private const val DISABLED_ANIMATOR_SCALE = 0f
