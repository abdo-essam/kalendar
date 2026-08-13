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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class KalendarStateBoundsTest {

    @Test
    fun clampedTimelineIndex_inRange_passesThrough() {
        assertEquals(5, clampedTimelineIndex(baseIndex = 0, monthsFromAnchor = 5, itemCount = 12))
    }

    @Test
    fun clampedTimelineIndex_beforeMin_clampsToZero() {
        assertEquals(0, clampedTimelineIndex(baseIndex = 0, monthsFromAnchor = -3, itemCount = 12))
    }

    @Test
    fun clampedTimelineIndex_pastMax_clampsToLastItem() {
        assertEquals(11, clampedTimelineIndex(baseIndex = 0, monthsFromAnchor = 40, itemCount = 12))
    }

    @Test
    fun clampedTimelineIndex_maxDateOnlyOverflow_doesNotWrapNegative() {
        // baseIndex near Int.MAX_VALUE + months past the anchor would overflow Int arithmetic.
        assertEquals(
            Int.MAX_VALUE - 1,
            clampedTimelineIndex(
                baseIndex = Int.MAX_VALUE - 1,
                monthsFromAnchor = 3,
                itemCount = Int.MAX_VALUE,
            ),
        )
    }

    @Test
    fun viewConfig_invalidScrollHour_throws() {
        assertFailsWith<IllegalArgumentException> {
            KalendarViewConfig(scheduleInitialScrollHour = 24)
        }
    }

    @Test
    fun viewConfig_invalidSnapMinutes_throws() {
        assertFailsWith<IllegalArgumentException> {
            KalendarViewConfig(scheduleDragSnapMinutes = 0)
        }
    }

    @Test
    fun viewConfig_invalidIndicatorCap_throws() {
        assertFailsWith<IllegalArgumentException> {
            KalendarViewConfig(eventIndicatorCap = 0)
        }
    }

    @Test
    fun viewConfig_boundaryValues_accepted() {
        val config = KalendarViewConfig(
            scheduleInitialScrollHour = 0,
            scheduleDragSnapMinutes = 1,
            eventIndicatorCap = 1,
        )
        assertEquals(0, config.scheduleInitialScrollHour)
    }
}
