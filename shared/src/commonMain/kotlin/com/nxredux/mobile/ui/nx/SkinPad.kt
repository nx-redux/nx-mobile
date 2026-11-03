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
import com.nxredux.mobile.skin.skinTiles
import androidx.compose.ui.graphics.drawscope.scale
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
                val dx = c.spec.labelDx   // beside the button: right-aligned left of it, left-aligned right of it
                val left = if (dx < 0f) c.x + dx - m.size.width else if (dx > 0f) c.x + dx else c.x - m.size.width / 2f
                drawText(m, topLeft = Offset(left, capTop + c.spec.labelH - m.firstBaseline))
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

/** One slice: copied where its part is fixed, laid in mirrored tiles where it repeats ([skinTiles]). */
private fun DrawScope.drawCell(pic: ImageBitmap, c: SkinCell, k: Float) {
    for (t in skinTiles(c, k, SKIN_STRETCH_MAX)) {
        val sx = t.src.x.roundToInt()
        val sy = t.src.y.roundToInt()
        val srcSize = IntSize((t.src.x + t.src.width).roundToInt() - sx, (t.src.y + t.src.height).roundToInt() - sy)
        val dx = t.dst.x.roundToInt()
        val dy = t.dst.y.roundToInt()
        val dstSize = IntSize((t.dst.x + t.dst.width).roundToInt() - dx, (t.dst.y + t.dst.height).roundToInt() - dy)
        if (srcSize.width <= 0 || srcSize.height <= 0 || dstSize.width <= 0 || dstSize.height <= 0) continue
        if (!t.flipX && !t.flipY) { drawImage(pic, IntOffset(sx, sy), srcSize, IntOffset(dx, dy), dstSize); continue }
        val pivot = Offset(dx + dstSize.width / 2f, dy + dstSize.height / 2f)
        scale(if (t.flipX) -1f else 1f, if (t.flipY) -1f else 1f, pivot) {
            this@drawCell.drawImage(pic, IntOffset(sx, sy), srcSize, IntOffset(dx, dy), dstSize)
        }
    }
}
