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
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import kotlin.js.ExperimentalWasmJsInterop

/**
 * Web: the `prefers-reduced-motion: reduce` media query, which browsers derive from the operating
 * system's reduce-motion setting.
 *
 * The preference can change while the page is open, so the value is held in snapshot state
 * refreshed by the media query's `change` event: a change recomposes every caller with the new
 * answer.
 */
@Composable
internal actual fun platformPrefersReducedMotion(): Boolean = reduceMotionState.value

/**
 * Process-wide snapshot of the media query's answer, created on first read.
 *
 * The `change` listener registered here is deliberately never removed: the state it feeds lives as
 * long as the page, so there is no earlier moment at which removing it would be correct, and one
 * retained listener is the entire cost.
 */
private val reduceMotionState: MutableState<Boolean> by lazy {
    val state = mutableStateOf(matchesReducedMotion())
    onReducedMotionChange {
        state.value = matchesReducedMotion()
    }
    state
}

/** Whether `prefers-reduced-motion: reduce` currently matches. */
@OptIn(ExperimentalWasmJsInterop::class)
private fun matchesReducedMotion(): Boolean =
    js("window.matchMedia('(prefers-reduced-motion: reduce)').matches")

/**
 * Invokes [onChange] whenever the `prefers-reduced-motion` media query changes its answer.
 *
 * [onChange] is referenced only inside the `js` snippet, which static analysis cannot see — hence
 * the suppression.
 */
@OptIn(ExperimentalWasmJsInterop::class)
@Suppress("UnusedParameter")
private fun onReducedMotionChange(onChange: () -> Unit) {
    js("window.matchMedia('(prefers-reduced-motion: reduce)').addEventListener('change', onChange)")
}
