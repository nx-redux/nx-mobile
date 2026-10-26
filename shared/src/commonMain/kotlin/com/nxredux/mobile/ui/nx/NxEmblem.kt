package com.nxredux.mobile.ui.nx

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate

// Glyph geometry from art/icon.svg (512-unit viewBox).
private const val STROKE = 13.6f
private const val MIN_X = 110.3f
private const val MAX_X = 401.7f
private const val MIN_Y = 115.7f
private const val MAX_Y = 396.3f

private val LEFT_PATH = floatArrayOf(110.3f, 115.7f, 147.7f, 115.7f, 356.8f, 328.2f, 356.8f, 183.8f, 147.7f, 396.3f, 110.3f, 396.3f)
private val RIGHT_PATH = floatArrayOf(401.7f, 115.7f, 364.3f, 115.7f, 155.2f, 328.2f, 155.2f, 183.8f, 364.3f, 396.3f, 401.7f, 396.3f)

/**
 * The NX mark, engraved (cut) into the pad surface, lit from above, like the button sockets; its groove colours come
 * from [LocalPadColors] (a theme with transparent lip and shadow colours, Black, gets a flat mark in the floor colour).
 * Draw-only: no pointer handling, so touches pass through to whatever sits beneath.
 */
@Composable
fun NxEmblem(modifier: Modifier = Modifier) {
    val colors = LocalPadColors.current   // the engraving is tuned against the pad body it is cut into
    Canvas(modifier) {
        // Fit the glyph's own bounds (including stroke) into the canvas, keeping aspect.
        val glyphW = MAX_X - MIN_X + STROKE
        val glyphH = MAX_Y - MIN_Y + STROKE
        val scale = minOf(size.width / glyphW, size.height / glyphH)
        if (scale <= 0f) return@Canvas
        val cx = (MIN_X + MAX_X) / 2f
        val cy = (MIN_Y + MAX_Y) / 2f
        val ox = size.width / 2f - cx * scale
        val oy = size.height / 2f - cy * scale

        val path = Path()
        for (pts in listOf(LEFT_PATH, RIGHT_PATH)) {
            path.moveTo(ox + pts[0] * scale, oy + pts[1] * scale)
            var i = 2
            while (i < pts.size) {
                path.lineTo(ox + pts[i] * scale, oy + pts[i + 1] * scale)
                i += 2
            }
            path.close()
        }

        val strokePx = STROKE * scale
        val stroke = Stroke(width = strokePx, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val d = maxOf(1f, 0.45f * strokePx)

        translate(0f, d) { drawPath(path, colors.engraveLitLip, style = stroke) }
        translate(0f, -d) { drawPath(path, colors.engraveInnerShadow, style = stroke) }
        drawPath(path, colors.engraveFloor, style = stroke)
    }
}
