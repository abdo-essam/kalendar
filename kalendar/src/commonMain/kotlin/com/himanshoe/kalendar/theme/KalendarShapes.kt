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

package com.himanshoe.kalendar.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Shape
import com.himanshoe.kalendar.KalendarAgenda

/**
 * Shapes used by Kalendar views.
 *
 * [dayCell] is shared by the day cell, the sliding selection indicator that animates behind it,
 * and the drag-and-drop ghost — they are separate composables that must agree visually, which is
 * why this is a theme token rather than a per-composable parameter.
 *
 * @property dayCell The day cell's selection fill, the sliding selection indicator, and the
 *   drag ghost.
 * @property eventIndicator The per-event indicator dots below a day number.
 * @property eventBlock Event blocks on the Schedule views and all-day chips.
 * @property popup The month/year jump picker surface.
 * @property agendaCard [KalendarAgenda]'s event rows.
 */
@Immutable
public class KalendarShapes(
    public val dayCell: Shape,
    public val eventIndicator: Shape,
    public val eventBlock: Shape,
    public val popup: Shape,
    public val agendaCard: Shape,
) {

    /** Returns a duplicate of these shapes with only the ones passed here replaced. */
    public fun copy(
        dayCell: Shape = this.dayCell,
        eventIndicator: Shape = this.eventIndicator,
        eventBlock: Shape = this.eventBlock,
        popup: Shape = this.popup,
        agendaCard: Shape = this.agendaCard,
    ): KalendarShapes = KalendarShapes(
        dayCell = dayCell,
        eventIndicator = eventIndicator,
        eventBlock = eventBlock,
        popup = popup,
        agendaCard = agendaCard,
    )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is KalendarShapes) return false
        if (dayCell != other.dayCell) return false
        if (eventIndicator != other.eventIndicator) return false
        if (eventBlock != other.eventBlock) return false
        if (popup != other.popup) return false
        if (agendaCard != other.agendaCard) return false
        return true
    }

    override fun hashCode(): Int {
        var result = dayCell.hashCode()
        result = 31 * result + eventIndicator.hashCode()
        result = 31 * result + eventBlock.hashCode()
        result = 31 * result + popup.hashCode()
        result = 31 * result + agendaCard.hashCode()
        return result
    }

    override fun toString(): String = "KalendarShapes(" +
        "dayCell=$dayCell, " +
        "eventIndicator=$eventIndicator, " +
        "eventBlock=$eventBlock, " +
        "popup=$popup, " +
        "agendaCard=$agendaCard" +
        ")"
}
