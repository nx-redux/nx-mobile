package com.nxredux.mobile.ui.nx

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.FixedScale
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import kotlin.math.max
import kotlin.math.sqrt

// The default mark is a watermark behind lists and tool pages: ~5% grey after this.
private const val DEFAULT_MARK_ALPHA = 0.15f

/** How a background's art is scaled, aligned and pushed right (a share of the width): see [backdropFit]. */
internal class BackdropFit(val scale: ContentScale, val alignment: Alignment, val shift: Float)

/** The game background's diagonal fade over a [w] x [h] area: the gradient's start and end (see [NxScreenBackground]). */
internal fun backdropFadeLine(w: Float, h: Float): Pair<Offset, Offset> {
    // Leans left ~0.15*w over the height h.
    val gx = h
    val gy = 0.15f * w
    val glen = sqrt(gx * gx + gy * gy)
    val ux = gx / glen
    val uy = gy / glen
    val half = w * 0.72f
    val cx = w * 0.625f
    val cy = h * 0.5f
    return Offset(cx - ux * half, cy - uy * half) to Offset(cx + ux * half, cy + uy * half)
}

/** Where along [backdropFadeLine] the fade stops being fully opaque (its first two stops are the solid background). */
internal const val FADE_OPAQUE_STOP = 0.35f

/** How far left of the fade's opaque edge a landscape game background reaches, as a share of the width. */
internal const val FADE_EDGE_MARGIN = 0.04f

/**
 * The x where the fade stops being fully opaque on the BOTTOM edge of a [w] x [h] area: the line leans left, so this
 * is the leftmost point at which the picture behind can show; a picture edge right of it would be visible.
 */
internal fun fadeOpaqueEdgeX(w: Float, h: Float): Float {
    val (a, b) = backdropFadeLine(w, h)
    val dx = b.x - a.x
    val dy = b.y - a.y
    // Solve ((x, h) - a) . d = stop * |d|^2 for x.
    return a.x + (FADE_OPAQUE_STOP * (dx * dx + dy * dy) - (h - a.y) * dy) / dx
}

/**
 * A landscape game background's scale for an [imgW] x [imgH] picture in a [areaW] x [areaH] area, right-aligned with
 * no shift: at least the full height, and wide enough that its left edge sits [FADE_EDGE_MARGIN] left of
 * [fadeOpaqueEdgeX] (then the top and bottom are cropped, centred). A picture that already reaches it at full height
 * stays at full height.
 */
internal fun landscapeBackdropScale(imgW: Float, imgH: Float, areaW: Float, areaH: Float): Float {
    val fullHeight = areaH / imgH
    val reach = areaW - (fadeOpaqueEdgeX(areaW, areaH) - FADE_EDGE_MARGIN * areaW).coerceIn(0f, areaW)
    return max(fullHeight, reach / imgW)
}

/**
 * [NxScreenBackground]'s fit for an [imgW] x [imgH] picture (null size = not known yet) in an [areaW] x [areaH] area.
 * The NX mark ([centred]) is centre-cropped. Game art ([fade]) taller than wide (a vertical shooter, a DS pair) covers
 * the area, keeping its TOP band and cropping the overflow, so it fills the full height on any screen. Other game art
 * on a landscape area is right-aligned with no shift and scaled by [landscapeBackdropScale] so its left edge hides
 * under the fade; on a portrait area it fills the height (which always covers the width), right-aligned, pushed 20%
 * so its middle shows. The console background ([fade] false) fills the height, right-aligned.
 */
internal fun backdropFit(imgW: Float?, imgH: Float?, areaW: Float, areaH: Float, fade: Boolean, centred: Boolean): BackdropFit {
    val known = imgW != null && imgH != null && imgW > 0f && imgH > 0f
    val aspect = if (known) imgW!! / imgH!! else 2f
    val tall = fade && aspect < 1.0f
    return when {
        centred -> BackdropFit(ContentScale.Crop, Alignment.Center, 0f)
        // Crop, not FillWidth: a width-fitted tall shot ends short of the bottom on a tall screen (the Flip).
        tall -> BackdropFit(ContentScale.Crop, Alignment.TopCenter, 0.20f)
        !fade -> BackdropFit(ContentScale.FillHeight, Alignment.CenterEnd, 0f)   // console background: right-aligned, no shift
        areaW > areaH -> BackdropFit(
            if (known) FixedScale(landscapeBackdropScale(imgW!!, imgH!!, areaW, areaH)) else ContentScale.FillHeight, Alignment.CenterEnd, 0f,
        )
        // A portrait (or square) area: art at least as wide as tall covers it at full height; pushed 20% so its middle shows.
        else -> BackdropFit(ContentScale.FillHeight, Alignment.CenterEnd, 0.20f)
    }
}

/**
 * A full-screen background: the art scaled to the FULL SCREEN HEIGHT (aspect kept; tall art is cropped to cover),
 * right-aligned so any uncovered gap falls on the LEFT (under the list), never on the right ([backdropFit]). On a
 * landscape screen game art is not shifted and is scaled up (top and bottom cropped) until its left edge is under the
 * fade's opaque part ([landscapeBackdropScale]); on a portrait one it is pushed 20% so its middle shows. The game
 * background adds the smooth diagonal fade (NX Redux: 40% from the right at the top, 60% at the bottom); the console
 * background sets [fade] = false and is not shifted (those images are already dark on the list side). [centred] is for
 * the NX-mark default only: it is centre-cropped instead; [markShift] moves it off centre.
 */
@Composable
fun NxScreenBackground(
    url: String?,
    fallback: org.jetbrains.compose.resources.DrawableResource?,
    fade: Boolean,
    modifier: Modifier = Modifier,
    centred: Boolean = false,
    markShift: Float = 0f,   // moves the picture right by this share of the width (the two-pane Settings' mark)
    request: Any? = null,   // what loads in place of [url] (an ImageRequest with transformations: a trimmed resume frame)
    onFailed: (() -> Unit)? = null,   // [url] would not load (the caller moves on to its next picture)
    onLoaded: (() -> Unit)? = null,   // [url] is drawn (a picture kept under it until then can go)
) {
    val colors = LocalNxColors.current
    val painter: Painter? = when {
        url != null -> rememberAsyncImagePainter(
            model = request ?: url,
            onState = {
                if (it is AsyncImagePainter.State.Error) onFailed?.invoke()
                if (it is AsyncImagePainter.State.Success) onLoaded?.invoke()
            },
        )
        fallback != null -> org.jetbrains.compose.resources.painterResource(fallback)
        else -> null
    }
    BoxWithConstraints(modifier) {
        if (painter != null) {
            val screenW = constraints.maxWidth.toFloat()
            val screenH = constraints.maxHeight.toFloat()
            val isz = painter.intrinsicSize
            val known = isz.isSpecified && isz.height > 0f && isz.width > 0f
            val fit = backdropFit(if (known) isz.width else null, if (known) isz.height else null, screenW, screenH, fade, centred)
            Image(
                painter = painter, contentDescription = null,
                contentScale = fit.scale, alignment = fit.alignment,
                alpha = if (centred) DEFAULT_MARK_ALPHA else 1f,
                modifier = Modifier.fillMaxSize().graphicsLayer { translationX = this.size.width * (fit.shift + markShift) },
            )
        }
        if (fade) {
            Box(
                Modifier.fillMaxSize().drawBehind {
                    // Leans left over the height; the picture shows from [fadeOpaqueEdgeX] (bottom edge) rightwards.
                    val (start, end) = backdropFadeLine(size.width, size.height)
                    drawRect(
                        brush = Brush.linearGradient(
                            // A long, gradual ramp so dark blends smoothly into the art.
                            0f to colors.background,
                            FADE_OPAQUE_STOP to colors.background,
                            0.7f to colors.background.copy(alpha = 0.35f),
                            1f to Color.Transparent,
                            start = start, end = end,
                        ),
                    )
                },
            )
        }
    }
}
