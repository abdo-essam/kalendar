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

package com.himanshoe.kalendar.view

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * Fresh header used by [KalendarWeek], [KalendarMonth], and [KalendarYear]: a title plus
 * previous/next arrow buttons wired to [state]'s pager.
 */
@Composable
internal fun KalendarNavigationHeader(
    title: String,
    state: KalendarViewState,
    showArrows: Boolean,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()

    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        if (showArrows) {
            IconButton(
                enabled = state.canScrollBackward,
                onClick = {
                    scope.launch {
                        state.pagerState.animateScrollToPage(state.pagerState.currentPage - 1)
                    }
                },
            ) {
                Icon(imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous")
            }
        } else {
            Spacer(modifier = Modifier.size(48.dp))
        }

        AnimatedContent(
            targetState = title,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
        ) { animatedTitle ->
            Text(text = animatedTitle, style = MaterialTheme.typography.titleMedium)
        }

        if (showArrows) {
            IconButton(
                enabled = state.canScrollForward,
                onClick = {
                    scope.launch {
                        state.pagerState.animateScrollToPage(state.pagerState.currentPage + 1)
                    }
                },
            ) {
                Icon(imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next")
            }
        } else {
            Spacer(modifier = Modifier.size(48.dp))
        }
    }
}
