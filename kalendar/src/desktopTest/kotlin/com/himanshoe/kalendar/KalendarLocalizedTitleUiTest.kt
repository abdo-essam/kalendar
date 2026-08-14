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

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.himanshoe.kalendar.state.rememberKalendarScheduleState
import kotlinx.datetime.LocalDate
import org.junit.Rule
import kotlin.test.Test

/**
 * Titles built from a caller's own month and day names must use them whole.
 *
 * Every one of these titles used to be assembled with `take(3)` on an already-localized string —
 * which turns Finnish "toukokuu" into "tou" — and the Schedule day title did not even go through a
 * hook: it read `DayOfWeek.name.take(3)`, so a fully localized German app still rendered "Mon".
 */
class KalendarLocalizedTitleUiTest {

    @get:Rule
    val rule = createComposeRule()

    private val finnish = KalendarViewConfig(
        shortMonthNameFormatter = { FINNISH_MAY },
        dayOfWeekNameFormatter = { FINNISH_TUESDAY },
    )

    @Test
    fun scheduleDayTitle_usesBothFormattersWholeAndUntruncated() {
        rule.setContent {
            KalendarSchedule(
                state = rememberKalendarScheduleState(initialDate = TUESDAY_IN_MAY),
                config = finnish,
            )
        }

        rule.onNodeWithText("$FINNISH_TUESDAY, $FINNISH_MAY 12, 2026").assertExists()
    }

    @Test
    fun weekTitle_usesTheShortMonthNameWhole() {
        rule.setContent {
            KalendarWeek(selectedDate = TUESDAY_IN_MAY, config = finnish)
        }

        rule.onNodeWithText("$FINNISH_MAY 11–17, 2026").assertExists()
    }

    @Test
    fun jumpPicker_showsTheShortMonthNameAndAnnouncesTheFullOne() {
        rule.setContent {
            KalendarMonth(
                selectedDate = TUESDAY_IN_MAY,
                config = KalendarViewConfig(
                    monthNameFormatter = { "kuukausi-${it.ordinal + 1}" },
                    shortMonthNameFormatter = { "kk-${it.ordinal + 1}" },
                ),
            )
        }
        rule.onNodeWithText("kuukausi-5 2026").performClick()

        // The picker's buttons are a fixed 72.dp wide, so they show the abbreviation and leave the
        // full name to the screen reader rather than growing to fit it.
        rule.onNodeWithText("kk-5").assertExists()
        rule.onNodeWithContentDescription("kuukausi-5").assertExists()
    }

    private companion object {
        val TUESDAY_IN_MAY = LocalDate(2026, 5, 12)
        const val FINNISH_MAY = "toukokuu"
        const val FINNISH_TUESDAY = "tiistaina"
    }
}
