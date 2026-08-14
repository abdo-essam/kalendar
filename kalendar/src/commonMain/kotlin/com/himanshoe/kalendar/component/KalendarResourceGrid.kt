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

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The width of one lane column: the space left over after the hour gutter divided evenly between
 * the lanes, but never below [minColumnWidth] — past that point the columns keep their width and
 * the grid scrolls horizontally instead of squeezing every lane into an unreadable sliver.
 *
 * @param availableWidth Total width the grid has to work with.
 * @param hourGutterWidth Width of the shared hour gutter, which does not scroll with the lanes.
 * @param resourceCount How many lanes to fit.
 * @param minColumnWidth Narrowest a lane column may become before horizontal scrolling takes over.
 */
internal fun resourceColumnWidth(
    availableWidth: Dp,
    hourGutterWidth: Dp,
    resourceCount: Int,
    minColumnWidth: Dp,
): Dp {
    if (resourceCount <= 0) {
        return minColumnWidth
    }
    val usableWidth = availableWidth - hourGutterWidth
    if (usableWidth <= 0.dp) {
        return minColumnWidth
    }
    return maxOf(usableWidth / resourceCount, minColumnWidth)
}
