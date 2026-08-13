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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import kotlinx.browser.window

private val PageGradient = Brush.linearGradient(
    colors = listOf(Color(0xFF1A1036), Color(0xFF2D1B54), Color(0xFF0F1B3D)),
)
private val PhoneWidth = 400.dp
private val PhoneHeight = 780.dp
private val WideLayoutMinWidth = 560.dp

/**
 * The GitHub Pages demo page: a dark gradient landing layout with the sample app running inside
 * a phone-shaped frame. Narrow viewports (actual phones) skip the frame and show the app
 * full-bleed, since a phone inside a phone helps nobody.
 */
@Composable
fun DemoShell() {
    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(PageGradient)) {
        if (maxWidth < WideLayoutMinWidth) {
            Surface(modifier = Modifier.fillMaxSize()) {
                App()
            }
        } else {
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                DemoHeader()
                PhoneFrame {
                    App()
                }
                Text(
                    text = "Runs entirely in your browser via Compose Multiplatform for Web (wasm).",
                    color = Color.White.copy(alpha = 0.55f),
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
        modifier = Modifier.padding(top = 40.dp, bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Kalendar",
            color = Color.White,
            fontSize = 44.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "The calendar library for Compose Multiplatform",
            color = Color.White.copy(alpha = 0.7f),
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
        color = Color.White,
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White.copy(alpha = 0.12f))
            .clickable { window.open(url, "_blank") }
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun PhoneFrame(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .width(PhoneWidth)
            .height(PhoneHeight)
            .clip(RoundedCornerShape(44.dp))
            .background(Color(0xFF0A0A0F))
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
                    .background(Color(0xFF0A0A0F)),
            )
        }
    }
}
