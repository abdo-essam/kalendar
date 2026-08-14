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

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Colour tokens for every Kalendar view, replacing the built-in Material-derived palette.
 *
 * Obtain a Material-matching instance from [KalendarThemeDefaults.colors] and `copy` the tokens
 * you want to change, or construct one from scratch for non-Material apps:
 *
 * ```kotlin
 * KalendarTheme(
 *     colors = KalendarThemeDefaults.colors().copy(
 *         selectionBackground = BrandPurple,
 *         nowIndicator = BrandRed,
 *     ),
 * ) {
 *     KalendarMonth(selectedDate = today)
 * }
 * ```
 *
 * @property background Fill behind the whole calendar. Defaults to the Material surface colour, so
 *   the calendar follows light/dark mode out of the box.
 * @property selectionBackground Fill of the selected day's circle and the sliding indicator.
 * @property onSelectionBackground Content colour on top of [selectionBackground].
 * @property todayContent Day-number colour for today's cell when it isn't selected.
 * @property dayContent Day-number colour for ordinary cells.
 * @property dayLabelContent Colour of the day-of-week column labels and hour-gutter labels.
 * @property headerContent Colour of the navigation header's title and its arrow/today icons.
 * @property eventIndicator Default event dot/block accent when
 *   [KalendarEvent.eventColor][com.himanshoe.kalendar.foundation.event.KalendarEvent.eventColor]
 *   is `null`.
 * @property nowIndicator The current-time line on [KalendarSchedule].
 * @property gridLine Hour lines and separators on [KalendarSchedule].
 * @property dragGhostBackground Fill of the drop-target ghost while dragging an event.
 * @property disabledContentAlpha Opacity applied to a day cell that
 *   [KalendarViewConfig.disabledDates] rejected, and to adjacent-month dates.
 * @property eventBlockAlpha Opacity of an event block's fill, applied over the event's accent
 *   colour. The accent stripe and text stay fully opaque.
 * @property eventBlockDraggingAlpha Opacity of an event block's fill while it is being dragged.
 * @property dragGhostAlpha Opacity of [dragGhostBackground].
 */
@Immutable
public data class KalendarColors(
    val background: Color,
    val selectionBackground: Color,
    val onSelectionBackground: Color,
    val todayContent: Color,
    val dayContent: Color,
    val dayLabelContent: Color,
    val headerContent: Color,
    val eventIndicator: Color,
    val nowIndicator: Color,
    val gridLine: Color,
    val dragGhostBackground: Color,
    val disabledContentAlpha: Float = DEFAULT_DISABLED_ALPHA,
    val eventBlockAlpha: Float = DEFAULT_EVENT_BLOCK_ALPHA,
    val eventBlockDraggingAlpha: Float = DEFAULT_EVENT_BLOCK_DRAGGING_ALPHA,
    val dragGhostAlpha: Float = DEFAULT_DRAG_GHOST_ALPHA,
)

/**
 * Text styles for every piece of text a Kalendar view draws, replacing the built-in
 * Material-derived scale.
 *
 * Obtain a Material-matching instance from [KalendarThemeDefaults.typography] and `copy` the
 * tokens you want to change, or construct one from scratch to plug in a non-Material type scale:
 *
 * ```kotlin
 * KalendarTheme(
 *     typography = KalendarThemeDefaults.typography().copy(
 *         headerTitle = MyDesignSystem.type.heading3,
 *         dayNumber = MyDesignSystem.type.numeric,
 *     ),
 * ) {
 *     KalendarMonth(selectedDate = today)
 * }
 * ```
 *
 * @property headerTitle The navigation header's title, [KalendarTimeline]'s month titles, and the
 *   jump picker's year.
 * @property monthLabel Month names inside [KalendarYear]'s month grid.
 * @property dayOfWeekLabel The day-of-week column headers.
 * @property dayNumber The day number inside an ordinary day cell.
 * @property dayNumberEmphasized The day number when the cell is selected or is today. Defaults to
 *   [dayNumber] in bold; set it equal to [dayNumber] to drop the emphasis entirely.
 * @property weekDayNumber The date number in [KalendarScheduleWeek]'s day-column headers.
 * @property eventLabel Event block titles and all-day chip titles.
 * @property eventOverflowLabel The compact `+N` label shown when a day has more events than
 *   [KalendarViewConfig.eventIndicatorCap].
 * @property hourLabel The hour-gutter labels on the Schedule views.
 */
@Immutable
public data class KalendarTypography(
    val headerTitle: TextStyle,
    val monthLabel: TextStyle,
    val dayOfWeekLabel: TextStyle,
    val dayNumber: TextStyle,
    val dayNumberEmphasized: TextStyle,
    val weekDayNumber: TextStyle,
    val eventLabel: TextStyle,
    val eventOverflowLabel: TextStyle,
    val hourLabel: TextStyle,
)

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
 */
@Immutable
public data class KalendarShapes(
    val dayCell: Shape,
    val eventIndicator: Shape,
    val eventBlock: Shape,
    val popup: Shape,
)

/**
 * Sizing and spacing for every Kalendar view.
 *
 * Several of these are shared by composables that must agree visually — [dayCellPadding] is used
 * by the day cell, the sliding selection indicator, and the drag ghost; [hourGutterWidth] by both
 * the hour gutter and the header spacer above it — which is why they are theme tokens rather than
 * per-composable parameters.
 *
 * Every value has a default, so overriding one is a one-liner:
 *
 * ```kotlin
 * KalendarTheme(dimensions = KalendarDimensions(hourHeight = 96.dp)) {
 *     KalendarSchedule(selectedDate = today)
 * }
 * ```
 *
 * @property dayCellPadding Inset around each day cell, and around the selection indicator and drag
 *   ghost that align to it.
 * @property dayCellAspectRatio Width-to-height ratio of a day cell. `1f` gives square cells; a
 *   value above `1f` gives short, wide cells.
 * @property eventIndicatorSize Diameter of one event dot below a day number.
 * @property eventIndicatorSpacing Gap between adjacent event dots.
 * @property eventIndicatorTopPadding Gap between the day number and the row of event dots.
 * @property headerPadding Padding around the navigation header's contents.
 * @property headerIconSlotWidth Width reserved on each side of the header title for arrow and
 *   today buttons; the title's available width is computed from it.
 * @property hourHeight Default vertical space for one hour on the Schedule views. The
 *   `hourHeight` parameter on those composables overrides this when supplied.
 * @property hourGutterWidth Width of the Schedule views' hour-label gutter.
 * @property gridLineThickness Thickness of hour lines and separators.
 * @property nowIndicatorThickness Thickness of the current-time line.
 * @property eventBlockGap Horizontal gap between side-by-side event blocks.
 * @property eventBlockAccentWidth Width of an event block's leading accent stripe. Set to `0.dp`
 *   to remove the stripe.
 * @property eventBlockPadding Padding around an event block's label.
 * @property eventBlockResizeHandleHeight Height of the grab strip at an event block's bottom edge
 *   that starts a resize rather than a move.
 * @property allDayRowPadding Padding around the all-day chip row.
 * @property allDayChipSpacing Gap between all-day chips.
 * @property allDayChipPadding Padding inside one all-day chip.
 * @property monthSpacing Vertical gap between month grids in [KalendarYear].
 * @property sectionPadding Padding around section titles — [KalendarTimeline]'s month titles and
 *   sticky header, and [KalendarYear]'s month names.
 */
@Immutable
public data class KalendarDimensions(
    val dayCellPadding: Dp = 2.dp,
    val dayCellAspectRatio: Float = 1f,
    val eventIndicatorSize: Dp = 4.dp,
    val eventIndicatorSpacing: Dp = 2.dp,
    val eventIndicatorTopPadding: Dp = 3.dp,
    val headerPadding: PaddingValues = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
    val headerIconSlotWidth: Dp = 48.dp,
    val hourHeight: Dp = 64.dp,
    val hourGutterWidth: Dp = 48.dp,
    val gridLineThickness: Dp = 1.dp,
    val nowIndicatorThickness: Dp = 2.dp,
    val eventBlockGap: Dp = 1.dp,
    val eventBlockAccentWidth: Dp = 3.dp,
    val eventBlockPadding: PaddingValues = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
    val eventBlockResizeHandleHeight: Dp = 12.dp,
    val allDayRowPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
    val allDayChipSpacing: Dp = 4.dp,
    val allDayChipPadding: PaddingValues = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
    val monthSpacing: Dp = 16.dp,
    val sectionPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
)

/**
 * Motion settings for every Kalendar view.
 *
 * Set [enabled] to `false` to make every transition instant — the right response to a platform
 * reduced-motion preference, and what screenshot tests want:
 *
 * ```kotlin
 * KalendarTheme(animations = KalendarAnimations(enabled = false)) { ... }
 * ```
 *
 * @property enabled Master switch. When `false`, every animation below snaps to its target and
 *   [pressScale] is ignored.
 * @property selectionColorSpec Drives the day cell's selection fill fading in and out.
 * @property selectionIndicatorSpec Drives the indicator that slides between selected day cells.
 * @property pressScale Scale a day cell shrinks to while pressed. `1f` disables the effect.
 * @property pressScaleSpec Drives the [pressScale] animation.
 * @property titleTransitionMillis Duration of the header/sticky-title crossfade.
 */
@Immutable
public data class KalendarAnimations(
    val enabled: Boolean = true,
    val selectionColorSpec: AnimationSpec<Color> = spring(),
    val selectionIndicatorSpec: AnimationSpec<Offset> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessHigh,
    ),
    val pressScale: Float = DEFAULT_PRESS_SCALE,
    val pressScaleSpec: AnimationSpec<Float> = spring(),
    val titleTransitionMillis: Int = DEFAULT_TITLE_TRANSITION_MS,
)

/**
 * Defaults for [KalendarTheme].
 *
 * Three palettes are available, and which one you want depends on your app:
 *
 * - [colors] (the default) derives from the ambient [MaterialTheme.colorScheme], so a Material 3
 *   app gets its own brand colours and its own light/dark scheme for free.
 * - [systemColors] returns Kalendar's own palette following the system light/dark setting, with no
 *   dependency on a Material theme being present — use it if your app doesn't use Material 3.
 * - [lightColors] and [darkColors] are those two palettes, chosen explicitly, for apps that drive
 *   their own theme switch instead of following the system.
 */
public object KalendarThemeDefaults {

    /**
     * [KalendarColors] resolved from the ambient [MaterialTheme.colorScheme] — the default, and
     * correct in both light and dark mode for any app wrapped in a Material 3 theme.
     *
     * Compose Material's colour scheme falls back to its baseline *light* scheme when no
     * `MaterialTheme` is present, so an app that doesn't use Material 3 should pass
     * [systemColors] instead, which tracks dark mode on its own.
     */
    @Composable
    public fun colors(): KalendarColors = MaterialTheme.colorScheme.let { scheme ->
        remember(scheme) { materialColors(scheme) }
    }

    /** Kalendar's built-in [KalendarDimensions]. */
    public fun dimensions(): KalendarDimensions = DefaultDimensions

    /** Kalendar's built-in [KalendarAnimations]. */
    public fun animations(): KalendarAnimations = DefaultAnimations

    /** Kalendar's built-in [KalendarStrings] — English, and the thing to override to localize. */
    public fun strings(): KalendarStrings = DefaultStrings

    private fun materialColors(scheme: ColorScheme): KalendarColors = KalendarColors(
        background = scheme.surface,
        selectionBackground = scheme.primaryContainer,
        onSelectionBackground = scheme.onPrimaryContainer,
        todayContent = scheme.primary,
        dayContent = scheme.onSurface,
        dayLabelContent = scheme.onSurfaceVariant,
        headerContent = scheme.onSurface,
        eventIndicator = scheme.primary,
        nowIndicator = scheme.error,
        gridLine = scheme.outlineVariant,
        dragGhostBackground = scheme.secondaryContainer,
    )

    /**
     * Kalendar's own palette for the current system light/dark setting, independent of Material.
     *
     * ```kotlin
     * KalendarTheme(colors = KalendarThemeDefaults.systemColors()) {
     *     KalendarMonth(selectedDate = today)
     * }
     * ```
     */
    @Composable
    public fun systemColors(): KalendarColors =
        if (isSystemInDarkTheme()) {
            darkColors()
        } else {
            lightColors()
        }

    /** Kalendar's built-in light palette. Material-free, so it works in any Compose app. */
    @Suppress("MagicNumber")
    public fun lightColors(): KalendarColors = KalendarColors(
        background = Color(0xFFFFFBFE),
        selectionBackground = Color(0xFFE8DEF8),
        onSelectionBackground = Color(0xFF1D192B),
        todayContent = Color(0xFF6750A4),
        dayContent = Color(0xFF1C1B1F),
        dayLabelContent = Color(0xFF49454F),
        headerContent = Color(0xFF1C1B1F),
        eventIndicator = Color(0xFF6750A4),
        nowIndicator = Color(0xFFB3261E),
        gridLine = Color(0xFFCAC4D0),
        dragGhostBackground = Color(0xFFE8DEF8),
    )

    /** Kalendar's built-in dark palette. Material-free, so it works in any Compose app. */
    @Suppress("MagicNumber")
    public fun darkColors(): KalendarColors = KalendarColors(
        background = Color(0xFF1C1B1F),
        selectionBackground = Color(0xFF4F378B),
        onSelectionBackground = Color(0xFFEADDFF),
        todayContent = Color(0xFFD0BCFF),
        dayContent = Color(0xFFE6E1E5),
        dayLabelContent = Color(0xFFCAC4D0),
        headerContent = Color(0xFFE6E1E5),
        eventIndicator = Color(0xFFD0BCFF),
        nowIndicator = Color(0xFFF2B8B5),
        gridLine = Color(0xFF49454F),
        dragGhostBackground = Color(0xFF4A4458),
    )

    /** [KalendarTypography] resolved from the ambient [MaterialTheme.typography]. */
    @Composable
    public fun typography(): KalendarTypography = MaterialTheme.typography.let { scale ->
        remember(scale) { materialTypography(scale) }
    }

    private fun materialTypography(scale: Typography): KalendarTypography = KalendarTypography(
        headerTitle = scale.titleMedium,
        monthLabel = scale.labelLarge,
        dayOfWeekLabel = scale.labelSmall,
        dayNumber = scale.bodyLarge,
        dayNumberEmphasized = scale.bodyLarge.copy(fontWeight = FontWeight.Bold),
        weekDayNumber = scale.labelLarge,
        eventLabel = scale.labelSmall,
        eventOverflowLabel = scale.labelSmall.copy(fontSize = EVENT_OVERFLOW_FONT_SIZE),
        hourLabel = scale.labelSmall,
    )

    /** Kalendar's built-in [KalendarShapes] — circular day cells, lightly rounded event blocks. */
    public fun shapes(): KalendarShapes = KalendarShapes(
        dayCell = CircleShape,
        eventIndicator = CircleShape,
        eventBlock = RoundedCornerShape(EVENT_BLOCK_CORNER_RADIUS),
        popup = RoundedCornerShape(POPUP_CORNER_RADIUS),
    )
}

private val EVENT_OVERFLOW_FONT_SIZE = 7.sp
private val EVENT_BLOCK_CORNER_RADIUS = 4.dp
private val POPUP_CORNER_RADIUS = 12.dp
private const val DEFAULT_DISABLED_ALPHA = 0.38f
private const val DEFAULT_EVENT_BLOCK_ALPHA = 0.18f
private const val DEFAULT_EVENT_BLOCK_DRAGGING_ALPHA = 0.45f
private const val DEFAULT_DRAG_GHOST_ALPHA = 0.6f
private const val DEFAULT_PRESS_SCALE = 0.92f
private const val DEFAULT_TITLE_TRANSITION_MS = 150

/**
 * Every Kalendar token set in one value, so a whole look can be passed around, `copy`-ed, and
 * overridden for a single calendar without wrapping it in a [KalendarTheme].
 *
 * Read the ambient instance with [KalendarTheme.tokens], and hand a modified copy to any view's
 * `theme` parameter to restyle just that one:
 *
 * ```kotlin
 * KalendarMonth(
 *     selectedDate = today,
 *     theme = KalendarTheme.tokens.copy(
 *         colors = KalendarTheme.colors.copy(selectionBackground = BrandPurple),
 *     ),
 * )
 * ```
 *
 * @property colors Colour tokens. See [KalendarColors].
 * @property typography Text styles. See [KalendarTypography].
 * @property shapes Corner shapes. See [KalendarShapes].
 * @property dimensions Sizing and spacing. See [KalendarDimensions].
 * @property animations Motion settings. See [KalendarAnimations].
 * @property strings Localizable text and accessibility labels. See [KalendarStrings].
 */
@Immutable
public data class KalendarThemeTokens(
    val colors: KalendarColors,
    val typography: KalendarTypography,
    val shapes: KalendarShapes,
    val dimensions: KalendarDimensions,
    val animations: KalendarAnimations,
    val strings: KalendarStrings,
)

internal val LocalKalendarTokens = staticCompositionLocalOf<KalendarThemeTokens?> { null }

/**
 * Accessors for the ambient Kalendar tokens, mirroring how `MaterialTheme.colorScheme` works.
 *
 * Outside a [KalendarTheme] these fall back to [KalendarThemeDefaults], so reading them is always
 * safe.
 */
public object KalendarTheme {

    /** The ambient [KalendarColors]. */
    public val colors: KalendarColors
        @Composable get() = kalendarColors()

    /** The ambient [KalendarTypography]. */
    public val typography: KalendarTypography
        @Composable get() = kalendarTypography()

    /** The ambient [KalendarShapes]. */
    public val shapes: KalendarShapes
        @Composable get() = kalendarShapes()

    /** The ambient [KalendarDimensions]. */
    public val dimensions: KalendarDimensions
        @Composable get() = kalendarDimensions()

    /** The ambient [KalendarAnimations]. */
    public val animations: KalendarAnimations
        @Composable get() = kalendarAnimations()

    /** The ambient [KalendarStrings]. */
    public val strings: KalendarStrings
        @Composable get() = kalendarStrings()

    /** All ambient token sets bundled together, ready to `copy` into a view's `theme` parameter. */
    public val tokens: KalendarThemeTokens
        @Composable get() = LocalKalendarTokens.current ?: KalendarThemeTokens(
            colors = KalendarThemeDefaults.colors(),
            typography = KalendarThemeDefaults.typography(),
            shapes = KalendarThemeDefaults.shapes(),
            dimensions = KalendarDimensions(),
            animations = KalendarAnimations(),
            strings = KalendarStrings(),
        )
}

/**
 * Provides colours, type, shapes, sizing, and motion to every Kalendar view inside [content].
 *
 * Wrapping is optional — views used outside a [KalendarTheme] fall back to [KalendarThemeDefaults]
 * — and each token set is independent, so overriding one leaves the rest at their defaults:
 *
 * ```kotlin
 * KalendarTheme(
 *     colors = KalendarThemeDefaults.colors().copy(selectionBackground = BrandPurple),
 *     shapes = KalendarShapes(dayCell = RoundedCornerShape(8.dp), ...),
 *     dimensions = KalendarDimensions(hourHeight = 96.dp),
 * ) {
 *     KalendarMonth(selectedDate = today)
 * }
 * ```
 *
 * @param colors Colour tokens. Defaults to [KalendarThemeDefaults.colors], which follows the
 *   ambient Material theme; pass [KalendarThemeDefaults.systemColors] in a non-Material app.
 * @param typography Text styles. Defaults to [KalendarThemeDefaults.typography].
 * @param shapes Corner shapes. Defaults to [KalendarThemeDefaults.shapes].
 * @param dimensions Sizing and spacing. Defaults to [KalendarDimensions]'s own defaults.
 * @param animations Motion settings. Pass `KalendarAnimations(enabled = false)` for reduced
 *   motion or deterministic screenshot tests.
 * @param content The calendar views this theme applies to.
 */
@Composable
public fun KalendarTheme(
    colors: KalendarColors = KalendarTheme.colors,
    typography: KalendarTypography = KalendarTheme.typography,
    shapes: KalendarShapes = KalendarTheme.shapes,
    dimensions: KalendarDimensions = KalendarTheme.dimensions,
    animations: KalendarAnimations = KalendarTheme.animations,
    strings: KalendarStrings = KalendarTheme.strings,
    content: @Composable () -> Unit,
) {
    val tokens = remember(colors, typography, shapes, dimensions, animations, strings) {
        KalendarThemeTokens(
            colors = colors,
            typography = typography,
            shapes = shapes,
            dimensions = dimensions,
            animations = animations,
            strings = strings,
        )
    }
    CompositionLocalProvider(LocalKalendarTokens provides tokens, content = content)
}

/** The effective [KalendarColors]: the ambient [KalendarTheme]'s, or Material-derived defaults. */
@Composable
internal fun kalendarColors(): KalendarColors =
    LocalKalendarTokens.current?.colors ?: KalendarThemeDefaults.colors()

/** The effective [KalendarTypography]: the ambient [KalendarTheme]'s, or Material-derived defaults. */
@Composable
internal fun kalendarTypography(): KalendarTypography =
    LocalKalendarTokens.current?.typography ?: KalendarThemeDefaults.typography()

/** The effective [KalendarShapes]: the ambient [KalendarTheme]'s, or Kalendar's built-in shapes. */
@Composable
internal fun kalendarShapes(): KalendarShapes =
    LocalKalendarTokens.current?.shapes ?: KalendarThemeDefaults.shapes()

/** The effective [KalendarDimensions]: the ambient [KalendarTheme]'s, or the built-in defaults. */
@Composable
internal fun kalendarDimensions(): KalendarDimensions =
    LocalKalendarTokens.current?.dimensions ?: KalendarThemeDefaults.dimensions()

/** The effective [KalendarAnimations]: the ambient [KalendarTheme]'s, or the built-in defaults. */
@Composable
internal fun kalendarAnimations(): KalendarAnimations =
    LocalKalendarTokens.current?.animations ?: KalendarThemeDefaults.animations()

/** The effective [KalendarStrings]: the ambient [KalendarTheme]'s, or the English defaults. */
@Composable
internal fun kalendarStrings(): KalendarStrings =
    LocalKalendarTokens.current?.strings ?: KalendarThemeDefaults.strings()

private val DefaultDimensions = KalendarDimensions()
private val DefaultAnimations = KalendarAnimations()
private val DefaultStrings = KalendarStrings()
