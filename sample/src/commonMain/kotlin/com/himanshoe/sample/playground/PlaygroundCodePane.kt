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

package com.himanshoe.sample.playground

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp

/** Copies generated Kotlin to the system clipboard, reporting whether it actually worked. */
internal fun interface PlaygroundClipboard {
    fun copy(text: String, onResult: (success: Boolean) -> Unit)
}

private enum class CopyState { Idle, Copied, Failed }

/**
 * Compose's own clipboard, which is what every target except the browser uses.
 */
@Composable
@Suppress("DEPRECATION")
internal fun rememberDefaultClipboard(): PlaygroundClipboard {
    val manager = LocalClipboardManager.current
    return remember(manager) {
        PlaygroundClipboard { text, onResult ->
            manager.setText(AnnotatedString(text))
            onResult(true)
        }
    }
}

/**
 * The read-only Kotlin the current configuration compiles down to, plus a copy button.
 *
 * The code is inside a [SelectionContainer] on purpose: if the platform clipboard refuses (a
 * browser outside a secure context, say) the pane says so and the text is still selectable by
 * hand, rather than pretending the copy succeeded.
 *
 * The pane wraps its content and scrolls **horizontally only**. It used to be pinned to a fixed
 * height with a vertical scroll of its own, which put a second vertical scroller inside the page's
 * — a wheel over the snippet moved the snippet, hit its end, and the page lurched. Long lines still
 * need somewhere to go, so the horizontal scroll stays: it never competes with a vertical page.
 */
@Composable
internal fun PlaygroundCodePane(
    code: String,
    clipboard: PlaygroundClipboard,
    modifier: Modifier = Modifier,
) {
    var copyState by remember { mutableStateOf(CopyState.Idle) }
    LaunchedEffect(code) { copyState = CopyState.Idle }
    val failed = copyState == CopyState.Failed
    val buttonBackground = if (failed) PlaygroundChrome.Surface else PlaygroundChrome.Accent
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(PaneCorner))
            .background(PlaygroundChrome.CodeBackground)
            .border(width = 1.dp, color = PlaygroundChrome.Border, shape = RoundedCornerShape(PaneCorner)),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = PanePadding, vertical = PaneHeaderPadding),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "GENERATED KOTLIN",
                color = PlaygroundChrome.MutedText,
                style = PlaygroundType.Eyebrow,
            )
            Text(
                text = when (copyState) {
                    CopyState.Idle -> "Copy"
                    CopyState.Copied -> "Copied"
                    CopyState.Failed -> "Select the code to copy"
                },
                color = if (failed) PlaygroundChrome.MutedText else PlaygroundChrome.OnAccent,
                style = PlaygroundType.Action,
                maxLines = 1,
                modifier = Modifier
                    .clip(RoundedCornerShape(ButtonCorner))
                    .background(buttonBackground)
                    .clickable {
                        clipboard.copy(code) { success ->
                            copyState = if (success) CopyState.Copied else CopyState.Failed
                        }
                    }
                    .testTag("copy-code")
                    .padding(horizontal = ButtonPaddingHorizontal, vertical = ButtonPaddingVertical),
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(start = PanePadding, end = PanePadding, bottom = PanePadding),
        ) {
            SelectionContainer {
                Text(
                    text = code,
                    color = PlaygroundChrome.CodeText,
                    style = PlaygroundType.Code,
                    modifier = Modifier.testTag("generated-code"),
                )
            }
        }
    }
}

private val PaneCorner = 16.dp
private val PanePadding = 16.dp
private val PaneHeaderPadding = 12.dp
private val ButtonCorner = 10.dp
private val ButtonPaddingHorizontal = 14.dp
private val ButtonPaddingVertical = 7.dp
