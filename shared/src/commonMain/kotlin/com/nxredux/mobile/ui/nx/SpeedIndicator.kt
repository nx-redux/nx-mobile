package com.nxredux.mobile.ui.nx

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.dp
import com.nxredux.mobile.shortcuts.SpeedIndicator

/**
 * The game's fast forward / rewind mark: two chevron triangles pointing right ([SpeedIndicator.FAST_FORWARD]) or, mirrored,
 * left ([SpeedIndicator.REWIND]), in the app's white mono glyph style on a soft dark backing so it reads over any picture.
 * Nothing for null. Display only; the host places it (top-right of the picture) and hides it under the menu.
 */
@Composable
fun NxSpeedIndicator(kind: SpeedIndicator?, modifier: Modifier = Modifier) {
    if (kind == null) return
    Canvas(modifier.background(LocalNxColors.current.indicatorBacking, RoundedCornerShape(8.dp)).padding(5.dp).size(20.dp)) {
        val w = size.width
        val h = size.height
        // Two triangles side by side, each half the width, the glyph centred vertically with a little air at the sides.
        val tri = Path().apply {
            moveTo(0f, h * 0.12f); lineTo(w * 0.5f, h * 0.5f); lineTo(0f, h * 0.88f); close()
        }
        scale(if (kind == SpeedIndicator.REWIND) -1f else 1f, 1f, pivot = Offset(w / 2, h / 2)) {
            drawPath(tri, Color.White)
            translate(w * 0.5f, 0f) { drawPath(tri, Color.White) }
        }
    }
}
