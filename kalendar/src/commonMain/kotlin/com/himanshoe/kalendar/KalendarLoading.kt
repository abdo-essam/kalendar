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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.himanshoe.kalendar.state.KalendarDateRange
import com.himanshoe.kalendar.state.KalendarEventLoader
import com.himanshoe.kalendar.state.KalendarLoadStatus
import com.himanshoe.kalendar.theme.kalendarColors
import kotlinx.datetime.LocalDate

/**
 * What a view knows about a range whose events have not arrived — the value handed to every
 * `loadingContent` slot.
 *
 * ```kotlin
 * KalendarMonth(
 *     selectedDate = today,
 *     state = monthState,
 *     eventLoader = bookings,
 *     loadingContent = { scope ->
 *         when (scope.status) {
 *             KalendarLoadStatus.Failed -> RetryBanner(message = scope.error?.message, onRetry = scope.retry)
 *             else -> MyShimmer()
 *         }
 *     },
 * )
 * ```
 *
 * Delivered as a scope rather than positional parameters so that fields can be added later without
 * breaking every slot that already exists.
 *
 * @property range The dates this placeholder stands in for — one page's worth, expanded to what the
 *   page actually draws. See [KalendarDateRange].
 * @property status Why the slot is being drawn: [KalendarLoadStatus.Loading] while the range is on
 *   its way, [KalendarLoadStatus.Failed] once it has failed. Never [KalendarLoadStatus.Loaded] — a
 *   range that has arrived draws its events instead.
 * @property error What went wrong, when [status] is [KalendarLoadStatus.Failed]. `null` otherwise.
 * @property retry Asks the loader for every failed range again. Wire it to a button in a failure
 *   slot; calling it while nothing has failed does nothing.
 */
@Immutable
public class KalendarLoadingScope(
    public val range: KalendarDateRange,
    public val status: KalendarLoadStatus,
    public val error: Throwable?,
    public val retry: () -> Unit,
) {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is KalendarLoadingScope) return false
        if (range != other.range) return false
        if (status != other.status) return false
        if (error != other.error) return false
        if (retry != other.retry) return false
        return true
    }

    override fun hashCode(): Int {
        var result = range.hashCode()
        result = HASH_FACTOR * result + status.hashCode()
        result = HASH_FACTOR * result + error.hashCode()
        result = HASH_FACTOR * result + retry.hashCode()
        return result
    }

    override fun toString(): String = "KalendarLoadingScope(" +
        "range=$range, " +
        "status=$status, " +
        "error=$error" +
        ")"
}

/**
 * The built-in rendering behind every view's `loadingContent` slot.
 *
 * It is what a calendar draws over a page whose events are still on their way, and the thing to
 * reach for when you want to keep the built-in look for the ordinary case and handle only failure
 * yourself:
 *
 * ```kotlin
 * loadingContent = { scope ->
 *     if (scope.status == KalendarLoadStatus.Failed) {
 *         RetryBanner(onRetry = scope.retry)
 *     } else {
 *         KalendarLoadingDefaults.Placeholder(scope = scope)
 *     }
 * }
 * ```
 */
public object KalendarLoadingDefaults {

    /**
     * The built-in placeholder: a wash of [KalendarColors.dayContent] over the page, faint enough
     * that the dates underneath stay readable while their events are missing.
     *
     * It exists to answer one question the calendar cannot otherwise answer — whether an empty month
     * is empty or merely early — so it draws for [KalendarLoadStatus.Loading] and **nothing at all**
     * for [KalendarLoadStatus.Failed]: a failure needs a sentence in the reader's language, and this
     * library has no string token for one. Handle [KalendarLoadStatus.Failed] in your own slot, with
     * [KalendarLoadingScope.retry] behind the button.
     *
     * There is no shimmer. An indefinitely repeating animation never lets a Compose test go idle, so
     * a shimmering default would hang the first UI test any consumer wrote around a loading
     * calendar; a slot is the right place for one, where it is opted into.
     *
     * The view overlays this at the page's own size, so it cannot move the calendar underneath it —
     * events landing changes what is drawn and never where. It takes no pointer input either: the
     * dates on a month whose events are still coming are already known, and are still selectable.
     *
     * @param scope The range being waited for, as handed to the `loadingContent` slot.
     * @param modifier [Modifier] applied to the wash.
     * @param contentDescription Announced by a screen reader in place of the wash — pass your own
     *   localized "loading events" here. `null`, the default, leaves the placeholder silent, since a
     *   library cannot invent a phrase in the reader's language.
     */
    @Composable
    public fun Placeholder(
        scope: KalendarLoadingScope,
        modifier: Modifier = Modifier,
        contentDescription: String? = null,
    ) {
        if (scope.status != KalendarLoadStatus.Loading) {
            return
        }
        val describe = contentDescription
        val described = if (describe == null) {
            Modifier
        } else {
            Modifier.semantics { this.contentDescription = describe }
        }
        Box(
            modifier = modifier
                .fillMaxSize()
                .then(described)
                .background(kalendarColors().dayContent.copy(alpha = PLACEHOLDER_ALPHA)),
        )
    }
}

/**
 * The `loadingContent` slot drawn over one page, or nothing when that page's events are there.
 *
 * [Modifier.matchParentSize] is the layout guarantee the slot's contract rests on: a
 * match-parent-size child is measured *after* its siblings have decided the box's size, so however
 * tall a placeholder is it cannot make the page taller, and the grid underneath is laid out
 * identically whether or not one is drawn.
 */
@Composable
internal fun BoxScope.KalendarPageLoading(
    loader: KalendarEventLoader<*>?,
    date: LocalDate,
    content: @Composable (KalendarLoadingScope) -> Unit,
) {
    if (loader == null) {
        return
    }
    val status = loader.statusAt(date)
    if (status == KalendarLoadStatus.Loaded) {
        return
    }
    val scope = rememberKalendarLoadingScope(
        loader = loader,
        range = loader.rangeAt(date),
        status = status,
        error = loader.errorAt(date),
    )
    Box(modifier = Modifier.matchParentSize().testTag(KALENDAR_LOADING_TAG)) {
        content(scope)
    }
}

/**
 * The scope for a whole loader rather than one page — what [KalendarAgenda] draws, having no pages
 * of its own to ask about.
 */
@Composable
internal fun rememberKalendarLoadingScope(
    loader: KalendarEventLoader<*>,
    range: KalendarDateRange = loader.range,
    status: KalendarLoadStatus = loader.status,
    error: Throwable? = loader.error,
): KalendarLoadingScope = remember(loader, range, status, error) {
    KalendarLoadingScope(range = range, status = status, error = error, retry = loader::retry)
}

/** Finds the placeholder in tests without exposing a tag every consumer would then rely on. */
internal const val KALENDAR_LOADING_TAG: String = "kalendar-loading"

/**
 * Faint enough to leave the day numbers legible, strong enough to be visible on both a light and a
 * dark surface — the placeholder tints with the theme's own content colour rather than a grey, so it
 * lands on the right side of the background either way.
 */
private const val PLACEHOLDER_ALPHA = 0.10f

private const val HASH_FACTOR = 31
