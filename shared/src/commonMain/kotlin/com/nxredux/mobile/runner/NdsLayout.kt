package com.nxredux.mobile.runner

import com.nxredux.mobile.layout.ScreenClass
import com.nxredux.mobile.options.NdsLayoutChoice
import com.nxredux.mobile.options.NdsScreen
import com.nxredux.mobile.options.NdsSettings
import com.nxredux.mobile.options.PipCorner
import com.nxredux.mobile.options.PxRect
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.round

/** A rectangle in 0..1 of the core frame (y down). */
data class FrameRect(val left: Float, val top: Float, val right: Float, val bottom: Float)

/**
 * One DS screen placed in the game area: [src] in the core frame, [dst] in game-area pixels. [shaded] screens get the
 * shader preset; [touch] screens take touches; [inset] marks the picture-in-picture small screen (drawn last, over the big one).
 * [alpha] below 1 blends the screen over what is drawn before it: only the inset ever has it (never shaded, never touched).
 */
data class NdsRegion(
    val screen: NdsScreen,
    val src: FrameRect,
    val dst: PxRect,
    val shaded: Boolean,
    val touch: Boolean,
    val inset: Boolean = false,
    val alpha: Float = 1f,
)

/**
 * Where the two DS screens go. melonDS DS draws them stacked in one 256x384 frame (256N x 384N with the GL renderer);
 * each screen is 4:3 (256x192 at scale 1).
 * Every layout uses whole-number scales of the 256x192 screen, with the pair centred in the space it is given (the
 * game area after the pad set-aside in landscape, or above the band in portrait): Large screen (the top screen big, the
 * touch screen centred below it), Stacked, Single screen, Picture in picture (the big screen with the other one as an
 * inset in a corner) and Side by side. Shaded regions always share one size, in whole pixels at whole-pixel positions,
 * so the one preset chain that draws them never resizes its framebuffers between them; a screen of another size is
 * drawn unshaded. Screen offsets don't apply here (the fork ignores them for regions).
 */
object NdsLayout {
    const val FRAME_WIDTH = 256
    private const val SCREEN_WIDTH = 256f
    private const val SCREEN_HEIGHT = 192f
    private const val ASPECT = 4f / 3f
    private const val EPSILON = 0.001f

    val TOP_SRC = FrameRect(0f, 0f, 1f, 0.5f)
    val BOTTOM_SRC = FrameRect(0f, 0.5f, 1f, 1f)

    fun src(screen: NdsScreen): FrameRect = if (screen == NdsScreen.TOP) TOP_SRC else BOTTOM_SRC

    /** A plain top-over-bottom stack at any whole scale: the software 256x384, or the GL renderer's 256N x 384N. */
    fun isDsFrame(width: Int, height: Int): Boolean = width > 0 && width % FRAME_WIDTH == 0 && height * 2 == width * 3

    /**
     * The regions of [layout] in [space] (game-area pixels), in draw order; empty when not even one screen at scale 1 fits.
     * [screen] only matters for Single screen and Picture in picture, which take one step less than the most that fits on
     * a tablet in landscape. The bottom screen takes touches, except as a picture-in-picture inset. R2 swaps which
     * screen is the main one ([NdsSettings.big]).
     */
    fun regions(
        space: PxRect,
        layout: NdsLayoutChoice,
        landscape: Boolean,
        screen: ScreenClass,
        settings: NdsSettings,
        insetMarginPx: Float,
    ): List<NdsRegion> {
        if (space.width <= 0f || space.height <= 0f) return emptyList()
        val regions = when (layout) {
            NdsLayoutChoice.LARGE -> large(space, landscape, settings.big)
            NdsLayoutChoice.STACKED -> stacked(space, settings.big)
            NdsLayoutChoice.SIDE_BY_SIDE -> sideBySide(space, settings.big)
            NdsLayoutChoice.SINGLE -> single(space, landscape, screen)?.let { listOf(full(settings.big, it)) }.orEmpty()
            NdsLayoutChoice.PIP -> single(space, landscape, screen)?.let { pip(it, settings, insetMarginPx) }.orEmpty()
        }
        return regions.map { it.copy(touch = it.screen == NdsScreen.BOTTOM && !it.inset) }
    }

    /** The centre of DS pixel ([dsX], [dsY]) of [region]'s screen, in game-area pixels. */
    fun pointIn(region: NdsRegion, dsX: Float, dsY: Float): Pair<Float, Float> = Pair(
        region.dst.x + (dsX + 0.5f) / SCREEN_WIDTH * region.dst.width,
        region.dst.y + (dsY + 0.5f) / SCREEN_HEIGHT * region.dst.height,
    )

    /**
     * A full-size screen's whole-pixel size within [fitWidth] x (fitWidth x 3/4): the height rounded down, the width the
     * nearest to 4:3 of it that still fits.
     */
    internal class ScreenSize(fitWidth: Float, aspect: Float = ASPECT) {
        val height = wholePx(fitWidth / aspect)
        val width = min(round(height * aspect), wholePx(fitWidth))
    }

    /** Rounds down, forgiving float error just under a whole pixel (1500 x 4/3 / 2 is 1000, not 999). */
    internal fun wholePx(v: Float): Float = floor(v + 0.001f).coerceAtLeast(0f)

    private fun full(screen: NdsScreen, dst: PxRect) =
        NdsRegion(screen, src(screen), dst, shaded = true, touch = screen == NdsScreen.BOTTOM)

    /** The largest whole scale that fits [space] with [cols] x [rows] screens of 256x192 each; 0 when not even 1x does. */
    private fun fitScale(space: PxRect, cols: Int, rows: Int): Int =
        floor(min(space.width / (SCREEN_WIDTH * cols), space.height / (SCREEN_HEIGHT * rows)) + EPSILON).toInt().coerceAtLeast(0)

    /** A [w] x [h] block centred in [space] at whole-pixel positions. */
    private fun centred(space: PxRect, w: Float, h: Float) =
        PxRect(space.x + wholePx((space.width - w) / 2f), space.y + wholePx((space.height - h) / 2f), w, h)

    /** One screen at the largest whole scale in [space], centred; one step less on a tablet in landscape (at least 1x). */
    private fun single(space: PxRect, landscape: Boolean, screen: ScreenClass): PxRect? {
        val most = fitScale(space, 1, 1)
        if (most < 1) return null
        val k = if (landscape && screen.isTablet) maxOf(1, most - 1) else most
        return centred(space, SCREEN_WIDTH * k, SCREEN_HEIGHT * k)
    }

    /** The big screen at [big] and the other as an inset in its corner; the inset's width is a fraction of the big screen's. */
    private fun pip(big: PxRect, s: NdsSettings, margin: Float): List<NdsRegion> {
        // Never bigger than fits inside the big screen with its margins (a height-limited big screen, split screen).
        val width = minOf(big.width * s.size.fraction, (big.height - 2f * margin) * ASPECT, big.width - 2f * margin)
        if (width <= 0f) return listOf(full(s.big, big))   // no room for an inset
        val height = width / ASPECT
        val left = s.corner == PipCorner.TOP_LEFT || s.corner == PipCorner.BOTTOM_LEFT
        val top = s.corner == PipCorner.TOP_LEFT || s.corner == PipCorner.TOP_RIGHT
        val x = if (left) big.x + margin else big.x + big.width - margin - width
        val y = if (top) big.y + margin else big.y + big.height - margin - height
        val small = s.big.other
        return listOf(
            full(s.big, big),
            NdsRegion(small, src(small), PxRect(x, y, width, height), shaded = false, touch = false, inset = true, alpha = s.opacity.alpha),
        )
    }

    /**
     * Large screen: the [big] screen at the largest scale k that leaves the other screen half of it (rounded down, at least
     * 1x) in the height, and the other one centred below it, fixed (the inset corner and size don't apply). The other
     * screen takes the largest whole scale the leftover height holds, up to one step under the big one (but never less
     * than that half). In portrait, where that leaves it under 2x, it fills the leftover height up to 2x at any scale (the
     * band never shrinks for it). Only the big screen is shaded unless both end up the same size; the other is drawn
     * unshaded like an inset (shaded regions share one size).
     */
    private fun large(space: PxRect, landscape: Boolean, big: NdsScreen): List<NdsRegion> {
        val k = (1..fitScale(space, 1, 1)).lastOrNull { k ->
            SCREEN_HEIGHT * (k + maxOf(1, k / 2)) <= space.height + EPSILON && SCREEN_WIDTH * k <= space.width + EPSILON
        } ?: return emptyList()
        val half = maxOf(1, k / 2)
        val m = maxOf(half, minOf(k - 1, floor((space.height - SCREEN_HEIGHT * k) / SCREEN_HEIGHT + EPSILON).toInt()))
        val bigH = SCREEN_HEIGHT * k
        var touchH = SCREEN_HEIGHT * m
        if (!landscape && m < 2) touchH = minOf(space.height - bigH, 2f * SCREEN_HEIGHT, bigH, space.width / ASPECT)
        val touchW = if (touchH == SCREEN_HEIGHT * m) SCREEN_WIDTH * m else touchH * ASPECT
        val top = centred(space, SCREEN_WIDTH * k, bigH + touchH)
        val other = big.other
        val bigRect = PxRect(top.x, top.y, SCREEN_WIDTH * k, bigH)
        val otherRect = PxRect(space.x + wholePx((space.width - touchW) / 2f), top.y + bigH, touchW, touchH)
        return listOf(full(big, bigRect), NdsRegion(other, src(other), otherRect, shaded = m == k, touch = false))
    }

    /** Both screens at one whole scale, [first] over the other, centred. */
    private fun stacked(space: PxRect, first: NdsScreen): List<NdsRegion> {
        val k = fitScale(space, 1, 2)
        if (k < 1) return emptyList()
        val w = SCREEN_WIDTH * k
        val h = SCREEN_HEIGHT * k
        val at = centred(space, w, 2f * h)
        return listOf(full(first, PxRect(at.x, at.y, w, h)), full(first.other, PxRect(at.x, at.y + h, w, h)))
    }

    /** Both screens at one whole scale, [left] first, edge to edge, centred. */
    private fun sideBySide(space: PxRect, left: NdsScreen): List<NdsRegion> {
        val k = fitScale(space, 2, 1)
        if (k < 1) return emptyList()
        val w = SCREEN_WIDTH * k
        val h = SCREEN_HEIGHT * k
        val at = centred(space, 2f * w, h)
        return listOf(full(left, PxRect(at.x, at.y, w, h)), full(left.other, PxRect(at.x + w, at.y, w, h)))
    }
}
