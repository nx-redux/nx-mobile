package com.nxredux.mobile.ui.nx

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

/**
 * A Cable Link session's input delay ("Delay 2"), on the speed indicator's backing so it reads over any picture. Display
 * only; the host places it where the speed indicator goes, which never shows in a session.
 */
@Composable
fun NxDelayIndicator(text: String, modifier: Modifier = Modifier) {
    Text(
        text, color = Color.White, fontSize = 12.sp, fontFamily = FontFamily.Monospace,
        modifier = modifier.background(LocalNxColors.current.indicatorBacking, RoundedCornerShape(8.dp)).padding(horizontal = 6.dp, vertical = 3.dp),
    )
}

/**
 * Where a picture indicator's top-right corner goes, in the overlay's own pixels: [margin] in from the picture's top-right
 * ([picRight], [picTop]), not the screen's, since a pad skin's shell or a landscape pad's shoulders hold the screen's
 * corners; and kept clear of a cutout ([cutRight], [cutTop]) on an overlay [width] wide. (right edge x, top y).
 */
fun pictureIndicatorAnchor(picRight: Int, picTop: Int, width: Int, cutRight: Int, cutTop: Int, margin: Int): Pair<Int, Int> =
    (minOf(picRight, width - cutRight) - margin) to (maxOf(picTop, cutTop) + margin)
