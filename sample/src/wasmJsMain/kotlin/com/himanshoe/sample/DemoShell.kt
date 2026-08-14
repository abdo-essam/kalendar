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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.himanshoe.sample.playground.Playground
import com.himanshoe.sample.playground.PlaygroundChrome
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
private val PhoneMaxWidth = 380.dp

/** Width divided by height, roughly a modern handset at 9:19.5. */
private const val PHONE_ASPECT_RATIO = 9f / 19.5f

/** Leaves a margin so the device reads as an object sitting on the page, not a cropped panel. */
private const val PHONE_HEIGHT_FRACTION = 0.94f
private val PlaygroundHeight = 880.dp

/**
 * The GitHub Pages playground page: a dark gradient landing layout with the two-pane playground
 * underneath, the live calendar running inside a phone-shaped frame. Viewports too narrow for two
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
                header = { DemoHeader() },
            )
        } else {
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                DemoHeader()
                Playground(
                    modifier = Modifier.fillMaxWidth().height(PlaygroundHeight),
                    clipboard = browserClipboard(),
                    calendarFrame = { content -> PhoneFrame(content = content) },
                )
                Text(
                    text = "Runs entirely in your browser via Compose Multiplatform for Web (wasm).",
                    color = PlaygroundChrome.DimText,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(vertical = 24.dp),
                )
            }
        }
    }
}

@Composable
private fun DemoHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Text(
                text = "Kalendar",
                color = PlaygroundChrome.Text,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Playground",
                color = PlaygroundChrome.DimText,
                style = MaterialTheme.typography.labelMedium,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LinkChip(label = "Docs", url = "https://hi-manshu.github.io/Kalendar/")
            LinkChip(label = "GitHub", url = "https://github.com/hi-manshu/Kalendar")
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
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(PlaygroundChrome.Surface)
            .border(width = 1.dp, color = PlaygroundChrome.Border, shape = RoundedCornerShape(20.dp))
            .clickable { window.open(url, "_blank") }
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

/**
 * A handset silhouette around the live calendar.
 *
 * Sized by aspect ratio rather than by filling the pane, so the whole device — including its
 * bottom bezel — is visible; a frame cut off by the viewport reads as a panel, not a phone. The
 * screen itself is transparent so the calendar's own themed background shows through, which is
 * what lets a dark preset render a dark phone instead of a dark calendar on a white screen.
 */
@Composable
private fun PhoneFrame(content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxHeight(), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .fillMaxHeight(PHONE_HEIGHT_FRACTION)
                .widthIn(max = PhoneMaxWidth)
                .aspectRatio(PHONE_ASPECT_RATIO)
                .shadow(elevation = 28.dp, shape = RoundedCornerShape(46.dp), clip = false)
                .clip(RoundedCornerShape(46.dp))
                .background(Color(PHONE_BEZEL_COLOR))
                .border(
                    width = 1.dp,
                    color = Color(PHONE_RIM_COLOR),
                    shape = RoundedCornerShape(46.dp),
                )
                .padding(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(34.dp)),
            ) {
                content()
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 10.dp)
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(Color(PHONE_BEZEL_COLOR)),
                )
            }
        }
    }
}
