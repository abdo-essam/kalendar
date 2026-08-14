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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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

private val PageGradient = Brush.verticalGradient(
    colors = listOf(
        Color(GRADIENT_START_COLOR),
        Color(GRADIENT_MID_COLOR),
        Color(GRADIENT_END_COLOR),
    ),
)
private val PhoneWidth = 400.dp
private val PlaygroundHeight = 1000.dp

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
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 40.dp, bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Kalendar",
            color = PlaygroundChrome.Text,
            fontSize = 44.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "The calendar library for Compose Multiplatform",
            color = PlaygroundChrome.MutedText,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LinkChip(label = "GitHub", url = "https://github.com/hi-manshu/Kalendar")
            LinkChip(label = "Docs", url = "https://github.com/hi-manshu/Kalendar/tree/main/doc")
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

@Composable
private fun PhoneFrame(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .width(PhoneWidth)
            .fillMaxHeight()
            .clip(RoundedCornerShape(44.dp))
            .background(Color(PHONE_BEZEL_COLOR))
            .padding(10.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(34.dp))
                .background(Color.White),
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
