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
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIAccessibilityIsReduceMotionEnabled
import platform.UIKit.UIAccessibilityReduceMotionStatusDidChangeNotification

/**
 * iOS: the Reduce Motion switch in Settings > Accessibility > Motion, via
 * [UIAccessibilityIsReduceMotionEnabled].
 *
 * The switch can flip while the app runs, so the value is held in snapshot state refreshed from
 * [UIAccessibilityReduceMotionStatusDidChangeNotification]: a change recomposes every caller with
 * the new answer.
 */
@Composable
internal actual fun platformPrefersReducedMotion(): Boolean = reduceMotionState.value

/**
 * Process-wide snapshot of [UIAccessibilityIsReduceMotionEnabled], created on first read.
 *
 * The notification observer registered here is deliberately never removed: the state it feeds
 * lives as long as the process, so there is no earlier moment at which removing it would be
 * correct, and one retained observer is the entire cost.
 */
private val reduceMotionState: MutableState<Boolean> by lazy {
    val state = mutableStateOf(UIAccessibilityIsReduceMotionEnabled())
    NSNotificationCenter.defaultCenter.addObserverForName(
        name = UIAccessibilityReduceMotionStatusDidChangeNotification,
        `object` = null,
        queue = NSOperationQueue.mainQueue,
    ) { _ ->
        state.value = UIAccessibilityIsReduceMotionEnabled()
    }
    state
}
