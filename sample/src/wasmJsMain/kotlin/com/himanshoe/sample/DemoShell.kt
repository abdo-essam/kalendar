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

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.himanshoe.sample.playground.Playground
import com.himanshoe.sample.playground.PlaygroundChrome
import com.himanshoe.sample.playground.PlaygroundFootnote
import com.himanshoe.sample.playground.PlaygroundType
import com.himanshoe.sample.playground.TwoPaneMinWidth
import kotlinx.browser.window

private const val GRADIENT_START_COLOR = 0xFF141414
private const val GRADIENT_MID_COLOR = 0xFF0A0A0A
private const val GRADIENT_END_COLOR = 0xFF000000
private const val PHONE_BEZEL_COLOR = 0xFF1A1A1A
private const val PHONE_RIM_COLOR = 0xFF3A3A3A

private val PageGradient = Brush.verticalGradient(
    colors = listOf(
        Color(GRADIENT_START_COLOR),
        Color(GRADIENT_MID_COLOR),
        Color(GRADIENT_END_COLOR),
    ),
)

/**
 * The widest the device may get.
 *
 * A real handset is 360–430dp across, and the library's own header assumes as much: below roughly
 * 300dp of screen the navigation header runs out of room between its arrow buttons and ellipsizes
 * the month title. A demo whose "phone" is narrower than any phone therefore shows the calendar
 * behaving worse than it ever does on a device.
 */
private val PhoneMaxWidth = 392.dp

/** Width divided by height, roughly a modern handset at 9:19.5. */
private const val PHONE_ASPECT_RATIO = 9f / 19.5f

/** Leaves a margin so the device reads as an object sitting on the page, not a cropped panel. */
private const val PHONE_HEIGHT_FRACTION = 0.96f

/** The hero row's height, fixed so the playground below it can be sized to the rest of the window. */
private val HeaderHeight = 76.dp

/**
 * How short the playground is allowed to get before the page starts scrolling instead of shrinking.
 * Below this the device would be too small to read and the controls too cramped to use.
 */
private val MinPlaygroundHeight = 660.dp

private const val TAGLINE = "A Compose Multiplatform calendar. Pick a view, restyle it, copy the Kotlin."
private const val FOOTNOTE =
    "Everything on this page runs in your browser: Kalendar compiled to WebAssembly by Compose " +
        "Multiplatform, drawing to a single canvas."

/**
 * The GitHub Pages playground page: a dark gradient landing layout with the two-pane playground
 * underneath, the live calendar running inside a phone-shaped frame.
 *
 * The wide layout gives the playground the whole window below the hero, so the device is as large as
 * the viewport allows and the controls scroll in the column beside it. Viewports too narrow for two
 * panes get the playground full-bleed with the hero scrolling above it, so the page never nests one
 * scrolling column inside another.
 */
@Composable
fun DemoShell() {
    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(PageGradient)) {
        if (maxWidth < TwoPaneMinWidth) {
            Playground(
                modifier = Modifier.fillMaxSize(),
                clipboard = browserClipboard(),
                header = { DemoHeader(modifier = Modifier.fillMaxWidth()) },
                footer = { PlaygroundFootnote(text = FOOTNOTE) },
            )
        } else {
            // Sized to the window, with a floor. At or above the floor the column is exactly the
            // viewport and the page has nothing to scroll; below it the page scrolls rather than
            // shrinking the device into illegibility.
            val playgroundHeight = maxOf(maxHeight - HeaderHeight, MinPlaygroundHeight)
            Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                DemoHeader(modifier = Modifier.fillMaxWidth().height(HeaderHeight))
                Playground(
                    modifier = Modifier.fillMaxWidth().height(playgroundHeight),
                    clipboard = browserClipboard(),
                    footer = { PlaygroundFootnote(text = FOOTNOTE) },
                    calendarFrame = { content -> PhoneFrame(content = content) },
                )
            }
        }
    }
}

/**
 * Wordmark, one line saying what this is, and the outbound links.
 *
 * The links are a [FlowRow] rather than a [Row]: at the two-pane breakpoint four pills plus the
 * wordmark are wider than the bar, and a Row would have squeezed "Buy me a coffee" until it
 * ellipsized. Wrapping to a second line costs nothing and never truncates a label.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DemoHeader(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.padding(horizontal = HeaderPadding, vertical = HeaderVerticalPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(
            modifier = Modifier.padding(end = HeaderPadding),
            verticalArrangement = Arrangement.spacedBy(WordmarkGap),
        ) {
            Text(text = "Kalendar", color = PlaygroundChrome.Text, style = PlaygroundType.Wordmark)
            Text(
                text = TAGLINE,
                color = PlaygroundChrome.MutedText,
                style = PlaygroundType.Tagline,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(LinkSpacing),
            verticalArrangement = Arrangement.spacedBy(LinkSpacing),
        ) {
            LinkChip(label = "Docs", url = "https://hi-manshu.github.io/Kalendar/")
            LinkChip(label = "GitHub", url = "https://github.com/hi-manshu/kalendar")
            LinkChip(label = "Sponsor", url = "https://github.com/sponsors/hi-manshu")
            LinkChip(label = "Buy me a coffee", url = "https://buymeacoffee.com/himanshoe")
        }
    }
}

@Composable
private fun LinkChip(label: String, url: String) {
    Text(
        text = label,
        color = PlaygroundChrome.Text,
        style = PlaygroundType.Link,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(LinkCorner))
            .background(PlaygroundChrome.Surface)
            .border(width = 1.dp, color = PlaygroundChrome.Border, shape = RoundedCornerShape(LinkCorner))
            .clickable { window.open(url, "_blank") }
            .padding(horizontal = LinkPaddingHorizontal, vertical = LinkPaddingVertical),
    )
}

/**
 * A handset silhouette around the live calendar.
 *
 * Sized by whichever of the pane's height and [PhoneMaxWidth] binds first, so the whole device —
 * including its bottom bezel — is always visible and never grows into a tall, narrow sliver on a
 * very tall window. Deriving the height explicitly rather than composing `fillMaxHeight` with
 * `aspectRatio` is what makes the width predictable: those two together let the device end up any
 * width the pane's height happened to imply, which on the old fixed-height playground meant a 250dp
 * "phone" and a month title ellipsized to "A…".
 *
 * The screen itself is transparent so the calendar's own themed background shows through, which is
 * what lets a dark preset render a dark phone instead of a dark calendar on a white screen.
 */
@Composable
private fun PhoneFrame(content: @Composable () -> Unit) {
    BoxWithConstraints(modifier = Modifier.fillMaxHeight(), contentAlignment = Alignment.Center) {
        val heightFromPane = maxHeight * PHONE_HEIGHT_FRACTION
        val heightFromWidth = PhoneMaxWidth / PHONE_ASPECT_RATIO
        val frameHeight: Dp = minOf(heightFromPane, heightFromWidth)
        Box(
            modifier = Modifier
                .height(frameHeight)
                .aspectRatio(PHONE_ASPECT_RATIO)
                .shadow(elevation = PhoneShadow, shape = RoundedCornerShape(PhoneCorner), clip = false)
                .clip(RoundedCornerShape(PhoneCorner))
                .background(Color(PHONE_BEZEL_COLOR))
                .border(
                    width = 1.dp,
                    color = Color(PHONE_RIM_COLOR),
                    shape = RoundedCornerShape(PhoneCorner),
                )
                .padding(PhoneBezel),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(ScreenCorner)),
            ) {
                content()
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = CameraInset)
                        .size(CameraSize)
                        .clip(CircleShape)
                        .background(Color(PHONE_BEZEL_COLOR)),
                )
            }
        }
    }
}

private val HeaderPadding = 24.dp
private val HeaderVerticalPadding = 12.dp
private val WordmarkGap = 3.dp
private val LinkSpacing = 8.dp
private val LinkCorner = 20.dp
private val LinkPaddingHorizontal = 16.dp
private val LinkPaddingVertical = 9.dp
private val PhoneShadow = 28.dp
private val PhoneCorner = 46.dp
private val PhoneBezel = 12.dp
private val ScreenCorner = 34.dp
private val CameraInset = 10.dp
private val CameraSize = 10.dp
