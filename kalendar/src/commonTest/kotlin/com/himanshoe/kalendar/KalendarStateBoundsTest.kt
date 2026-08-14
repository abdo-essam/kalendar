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

package com.himanshoe.kalendar

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * What [KalendarViewConfig] refuses to be built with. The page-index bounds the states rest on are
 * tested against [com.himanshoe.kalendar.foundation.paging.KalendarPager] in `kalendar-foundation`.
 */
class KalendarStateBoundsTest {

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
