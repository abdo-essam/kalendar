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

@file:OptIn(ExperimentalWasmJsInterop::class)

package com.himanshoe.sample

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.himanshoe.sample.playground.PlaygroundClipboard
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.JsAny
import kotlin.js.Promise

/**
 * The browser's async clipboard.
 *
 * `navigator.clipboard` is absent outside a secure context and its write can still be rejected, so
 * the result is reported from the promise rather than assumed — the playground says "select the
 * code to copy" instead of claiming a copy that never happened.
 */
@Composable
internal fun browserClipboard(): PlaygroundClipboard = remember {
    PlaygroundClipboard { text, onResult ->
        if (clipboardAvailable()) {
            writeClipboardText(text).then<JsAny?>(
                {
                    onResult(true)
                    null
                },
                {
                    onResult(false)
                    null
                },
            )
        } else {
            onResult(false)
        }
    }
}

private fun clipboardAvailable(): Boolean = js("typeof navigator !== 'undefined' && !!navigator.clipboard")

@Suppress("UnusedParameter")
private fun writeClipboardText(text: String): Promise<JsAny?> = js("navigator.clipboard.writeText(text)")
