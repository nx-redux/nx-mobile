package com.nxredux.mobile.ui.nx

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import com.nxredux.mobile.shared.res.Res
import com.nxredux.mobile.shared.res.misans_semibold
import org.jetbrains.compose.resources.Font
import com.nxredux.mobile.skin.SkinCell
import com.nxredux.mobile.skin.SkinPackManifest
import com.nxredux.mobile.skin.SkinPiece
import com.nxredux.mobile.skin.SkinPlacement
import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.roundToInt

/** A pad skin pack the menu can draw: its [manifest] and its pictures by file name, each decoded once ([load]: null if unreadable). */
class SkinPackSource(val manifest: SkinPackManifest, private val load: (String) -> ImageBitmap?) {
    private val pictures = HashMap<String, ImageBitmap?>()
    fun picture(name: String): ImageBitmap? = pictures.getOrPut(name) { load(name) }
}

/** The platform's pad skin packs by id ([com.nxredux.mobile.settings.PadTheme.skin]); null where there are none. */
val LocalSkinPacks = staticCompositionLocalOf<(String) -> SkinPackSource?> { { null } }

/**
 * A [SkinPlacement] drawn in [modifier]'s box (the area it was placed for) from its [picture] (and its pieces from
 * [parts], for a layout built from pieces), and its buttons: pad buttons go to [onPadEvent] (the skin's other buttons do
 * nothing here). A touch on no button is not taken. Slices are copied or tiled as rendered, pieces copied (the frame
 * scaled to its size); the only thing painted is a held cap's shade ([SkinPadTouch.pressedShapes]).
 */
@Composable
fun SkinPad(placement: SkinPlacement, picture: ImageBitmap, parts: ImageBitmap?, onPadEvent: (PadEvent) -> Unit, modifier: Modifier = Modifier) {
    val emit by rememberUpdatedState(onPadEvent)
    val haptic = rememberPadHaptic()
    val held = remember { mutableIntStateOf(0) }   // bumped on every press change: the shade redraws (draw only)
    val touch = remember { SkinPadTouch({ emit(it) }, { _, _ -> }, haptic) { held.intValue++ } }
    SideEffect { touch.placement = placement }
    DisposableEffect(touch) { onDispose { touch.releaseAll() } }
    val semibold = Font(Res.font.misans_semibold, FontWeight.SemiBold)
    val labelFont = remember(semibold) { FontFamily(semibold) }
    val measurer = rememberTextMeasurer()
    Box(
        modifier.pointerInput(touch) {
            awaitEachGesture {
                try {
                    do {
                        val event = awaitPointerEvent()
                        for (c in event.changes) {
                            val id = c.id.value
                            when {
                                c.changedToDown() -> if (touch.press(id, c.position.x, c.position.y)) c.consume()
                                c.pressed -> if (touch.holds(id)) { touch.move(id, c.position.x, c.position.y); c.consume() }
                                touch.holds(id) -> { touch.release(id); c.consume() }
                            }
                        }
                    } while (event.changes.any { it.pressed })
                } finally {
                    touch.releaseAll()
                }
            }
        },
    ) {
        // the skin in its own layer: recorded once per placement and replayed while only the shades over it change
        val holes = remember(placement) {
            Path().apply { for (g in placement.glasses) addRect(Rect(g.rect.x, g.rect.y, g.rect.x + g.rect.width, g.rect.y + g.rect.height)) }
        }
        Canvas(Modifier.fillMaxSize().graphicsLayer()) {
          // the screens are holes: what is under them shows through (the background runs under them otherwise)
          clipPath(holes, ClipOp.Difference) {
            for (c in placement.cells) drawCell(picture, c, placement.k)
            if (parts != null) for (p in placement.pieces) drawPiece(parts, p)
            // the labels under the buttons (written here, not in the picture): cap height labelH, centred labelDy below
            for (c in placement.controls) {
                val text = c.spec.label ?: continue
                if (c.spec.labelH <= 0f) continue
                val style = TextStyle(color = Color(SKIN_LABEL_GREY), fontFamily = labelFont, fontWeight = FontWeight.SemiBold,
                    fontSize = (c.spec.labelH / SKIN_LABEL_CAP).toSp())   // role: a printed button label
                val m = measurer.measure(text, style)
                val capTop = c.y + c.spec.labelDy - c.spec.labelH / 2f
                drawText(m, topLeft = Offset(c.x - m.size.width / 2f, capTop + c.spec.labelH - m.firstBaseline))
            }
          }
        }
        Canvas(Modifier.fillMaxSize()) {
            held.intValue
            for (shape in touch.pressedShapes()) drawPressShape(shape)
        }
    }
}

/** A held cap's shade: [SKIN_PRESS_SHADE] black over its outline. */
private fun DrawScope.drawPressShape(shape: SkinPressShape) {
    val shade = Color.Black.copy(alpha = SKIN_PRESS_SHADE)   // role: a held skin cap pushed in, darker
    when (shape) {
        is SkinPressShape.Circle -> drawCircle(shade, shape.r, Offset(shape.x, shape.y))
        is SkinPressShape.Box -> drawRoundRect(shade, Offset(shape.left, shape.top), Size(shape.right - shape.left, shape.bottom - shape.top), CornerRadius(shape.corner))
    }
}

/** A piece: its part of [parts] at its spot (the frame scaled, filtered). */
private fun DrawScope.drawPiece(parts: ImageBitmap, p: SkinPiece) {
    val sx = p.src.x.roundToInt()
    val sy = p.src.y.roundToInt()
    val dx = p.dst.x.roundToInt()
    val dy = p.dst.y.roundToInt()
    val src = IntSize((p.src.x + p.src.width).roundToInt() - sx, (p.src.y + p.src.height).roundToInt() - sy)
    val dst = IntSize((p.dst.x + p.dst.width).roundToInt() - dx, (p.dst.y + p.dst.height).roundToInt() - dy)
    if (src.width > 0 && src.height > 0 && dst.width > 0 && dst.height > 0) {
        drawImage(parts, IntOffset(sx, sy), src, IntOffset(dx, dy), dst, filterQuality = FilterQuality.High)
    }
}

/** One slice: copied where its part is fixed, tiled in picture-size steps where it repeats (the last tile cut). */
private fun DrawScope.drawCell(pic: ImageBitmap, c: SkinCell, k: Float) {
    val tileW = c.src.width * k
    val tileH = c.src.height * k
    if (tileW <= 0f || tileH <= 0f || c.dst.width <= 0f || c.dst.height <= 0f) return
    // a plain strip a few px across is stretched, not tiled: the same look in one draw (tiled, a big window's
    // strips would be thousands of draws a frame)
    val tileX = c.tileX && c.src.width > SKIN_STRETCH_MAX
    val tileY = c.tileY && c.src.height > SKIN_STRETCH_MAX
    val nx = if (tileX) ceil(c.dst.width / tileW - 0.001f).toInt().coerceAtLeast(1) else 1
    val ny = if (tileY) ceil(c.dst.height / tileH - 0.001f).toInt().coerceAtLeast(1) else 1
    for (j in 0 until ny) for (i in 0 until nx) {
        val x0 = c.dst.x + i * (if (tileX) tileW else c.dst.width)
        val y0 = c.dst.y + j * (if (tileY) tileH else c.dst.height)
        val w = if (tileX) min(tileW, c.dst.x + c.dst.width - x0) else c.dst.width
        val h = if (tileY) min(tileH, c.dst.y + c.dst.height - y0) else c.dst.height
        val sw = if (tileX) w / k else c.src.width
        val sh = if (tileY) h / k else c.src.height
        val sx = c.src.x.roundToInt()
        val sy = c.src.y.roundToInt()
        val srcSize = IntSize((c.src.x + sw).roundToInt() - sx, (c.src.y + sh).roundToInt() - sy)
        val dx = x0.roundToInt()
        val dy = y0.roundToInt()
        val dstSize = IntSize((x0 + w).roundToInt() - dx, (y0 + h).roundToInt() - dy)
        if (srcSize.width > 0 && srcSize.height > 0 && dstSize.width > 0 && dstSize.height > 0) {
            drawImage(pic, IntOffset(sx, sy), srcSize, IntOffset(dx, dy), dstSize)
        }
    }
}
