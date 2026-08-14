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

package com.himanshoe.kalendar.component

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The only part of the resource grid that is a layout decision rather than date maths: how wide
 * a lane column gets. The lane bucketing and drag arithmetic live in `kalendar-foundation`.
 */
@Suppress("MagicNumber")
class KalendarResourceStateTest {

    @Test
    fun resourceColumnWidth_dividesTheSpaceLeftAfterTheGutter() {
        val width = resourceColumnWidth(
            availableWidth = 448.dp,
            hourGutterWidth = 48.dp,
            resourceCount = 4,
            minColumnWidth = 96.dp,
        )
        assertEquals(100.dp, width)
    }

    @Test
    fun resourceColumnWidth_stopsAtTheMinimumSoTheGridScrollsInstead() {
        val width = resourceColumnWidth(
            availableWidth = 448.dp,
            hourGutterWidth = 48.dp,
            resourceCount = 10,
            minColumnWidth = 96.dp,
        )
        assertEquals(96.dp, width)
    }

    @Test
    fun resourceColumnWidth_withNoSpaceOrNoLanesFallsBackToTheMinimum() {
        assertEquals(
            96.dp,
            resourceColumnWidth(
                availableWidth = 20.dp,
                hourGutterWidth = 48.dp,
                resourceCount = 2,
                minColumnWidth = 96.dp,
            ),
        )
        assertEquals(
            96.dp,
            resourceColumnWidth(
                availableWidth = 448.dp,
                hourGutterWidth = 48.dp,
                resourceCount = 0,
                minColumnWidth = 96.dp,
            ),
        )
    }
}
