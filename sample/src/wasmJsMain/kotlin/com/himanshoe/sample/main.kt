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

package com.himanshoe.sample

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import kotlinx.browser.document
import kotlinx.browser.window

private const val COMPOSE_ROOT_ID = "compose-root"
private const val BOOT_SKELETON_ID = "boot-skeleton"
private const val DISMISSED_CLASS = "is-dismissed"

/** Matches the `#boot-skeleton` opacity transition in `index.html`. */
private const val FADE_OUT_MILLIS = 220

/**
 * Compose mounts into its own container rather than into `<body>`.
 *
 * `ComposeViewport` clears the element it is given, and it does so as soon as `skiko.wasm` resolves
 * — several frames before the first one is drawn. Handing it `<body>` would therefore delete the
 * static shell during precisely the gap the shell exists to cover. Giving it a dedicated,
 * full-viewport `<div>` leaves the shell a sibling that this module removes on its own terms.
 */
@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    val container = document.getElementById(COMPOSE_ROOT_ID) ?: document.body!!
    ComposeViewport(container) {
        BootSkeletonDismissal()
        DemoShell()
    }
}

/**
 * Fades out the static first-paint shell once Compose has actually drawn.
 *
 * A callback from Kotlin rather than a `MutationObserver` on the canvas: the canvas is appended
 * while Skia is still warming up and while the first composition has yet to run, so watching for it
 * would uncover an empty canvas and reintroduce the blank frame. `withFrameNanos` resumes on the
 * frame clock, so waiting two frames puts this after a composition, a layout and a draw have gone
 * out. The 220ms cross-fade that follows is the belt to that braces — the shell is still on screen,
 * at falling opacity, over a canvas that already has the calendar on it.
 */
@Composable
private fun BootSkeletonDismissal() {
    LaunchedEffect(Unit) {
        withFrameNanos { }
        withFrameNanos { }
        dismissBootSkeleton()
    }
}

private fun dismissBootSkeleton() {
    val skeleton = document.getElementById(BOOT_SKELETON_ID) ?: return
    skeleton.classList.add(DISMISSED_CLASS)
    window.setTimeout({
        skeleton.remove()
        null
    }, FADE_OUT_MILLIS)
}
