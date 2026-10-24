package com.nxredux.mobile.ui.nx

import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The app's "more this way" cue for a vertical scroll: while [state] can scroll further down, the bottom [height] of what
 * this modifier wraps fades to transparent; while it can scroll back up, the top does too, so a cut-off line reads as
 * continuing. Place it after the container's background and before its `verticalScroll` (or on a lazy list), so only
 * the scrolled content fades, not the surface. [strong]: the fade eases in, so more of it is near-transparent (a list on
 * the page, where a linear fade reads too faint).
 */
fun Modifier.nxScrollFade(state: ScrollableState, height: Dp = 32.dp, strong: Boolean = false): Modifier =
    nxEdgeFade({ state.canScrollBackward }, { state.canScrollForward }, height, strong)

/**
 * [nxScrollFade] for a list that pages rather than scrolls (a window of rows): the top fades while [above] (rows before
 * the window), the bottom while [below]. Both are read at draw time.
 */
fun Modifier.nxEdgeFade(above: () -> Boolean, below: () -> Boolean, height: Dp = 32.dp, strong: Boolean = false): Modifier =
    graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            val fade = height.toPx().coerceAtMost(size.height / 2)
            if (above()) {
                drawRect(
                    brush = Brush.verticalGradient(*fadeStops(strong, toEdge = false), startY = 0f, endY = fade),
                    size = Size(size.width, fade),
                    blendMode = BlendMode.DstIn,
                )
            }
            if (below()) {
                drawRect(
                    brush = Brush.verticalGradient(*fadeStops(strong, toEdge = true), startY = size.height - fade, endY = size.height),
                    topLeft = Offset(0f, size.height - fade),
                    size = Size(size.width, fade),
                    blendMode = BlendMode.DstIn,
                )
            }
        }

/** The fade's alpha from its edge in: linear, or [strong] (ease-in: a fifth of the way in at half the height). [toEdge]: running towards the edge. */
private fun fadeStops(strong: Boolean, toEdge: Boolean): Array<Pair<Float, Color>> {
    val fromEdge = if (strong) listOf(0f to 0f, 0.5f to 0.2f, 1f to 1f) else listOf(0f to 0f, 1f to 1f)
    val stops = fromEdge.map { (at, a) -> (if (toEdge) 1f - at else at) to Color.Black.copy(alpha = a) }
    return (if (toEdge) stops.reversed() else stops).toTypedArray()
}
