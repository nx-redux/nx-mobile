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
 * each screen is 4:3.
 * Portrait: picture in picture (big screen fitted, top-aligned and centred; the other screen as an inset in a corner of
 * it), stacked (both at one scale, centred) or single (the big screen, top-aligned). Landscape: side by side, edge to
 * edge, centred; single (the big screen centred on both axes, leaving the side margins to the pad clusters); or picture
 * in picture (that centred big screen with the portrait inset rules). Touch only on a full-size bottom screen. Shaded
 * regions always share one size, in whole pixels at whole-pixel positions ([ScreenSize], once per layout), so the one
 * preset chain that draws them never resizes its framebuffers between them. Screen offsets don't apply here (the fork
 * ignores them for regions).
 */
object NdsLayout {
    const val FRAME_WIDTH = 256
    private const val SCREEN_WIDTH = 256f
    private const val SCREEN_HEIGHT = 192f
    private const val ASPECT = 4f / 3f

    val TOP_SRC = FrameRect(0f, 0f, 1f, 0.5f)
    val BOTTOM_SRC = FrameRect(0f, 0.5f, 1f, 1f)

    fun src(screen: NdsScreen): FrameRect = if (screen == NdsScreen.TOP) TOP_SRC else BOTTOM_SRC

    /** A plain top-over-bottom stack at any whole scale: the software 256x384, or the GL renderer's 256N x 384N. */
    fun isDsFrame(width: Int, height: Int): Boolean = width > 0 && width % FRAME_WIDTH == 0 && height * 2 == width * 3

    /**
     * The regions for an [areaWidth] x [areaHeight] game area, in draw order; empty for an area too small for a screen pixel.
     * Portrait uses [NdsSettings.controller] in [controllerMode] (a non-clip-on controller dropped the pad band), else
     * [NdsSettings.portrait]; a layout not picked yet is [screen]'s default ([NdsSettings.layoutShown]).
     * Only the bottom screen takes touches: as the main screen ([NdsSettings.big]: the big one, or the first in stacked
     * and side by side) always, as the other one only while no pad clusters are drawn over the game: [padOverGame] in
     * landscape (no controller), [portraitPadOver] in portrait (the band hidden). R2 swaps it into the main spot.
     */
    fun regions(
        areaWidth: Float,
        areaHeight: Float,
        landscape: Boolean,
        settings: NdsSettings,
        marginPx: Float,
        controllerMode: Boolean = false,
        padOverGame: Boolean = false,
        portraitPadOver: Boolean = false,
        screen: ScreenClass = ScreenClass.REGULAR_PHONE,
    ): List<NdsRegion> {
        if (areaWidth <= 0f || areaHeight <= 0f) return emptyList()
        val regions = when (settings.layoutShown(landscape, controllerMode, screen)) {
            NdsLayoutChoice.SIDE_BY_SIDE -> sideBySide(areaWidth, areaHeight, settings.big)
            NdsLayoutChoice.SINGLE -> listOf(full(settings.big, if (landscape) centreFit(areaWidth, areaHeight) else topFit(areaWidth, areaHeight)))
            NdsLayoutChoice.PIP -> pip(if (landscape) centreFit(areaWidth, areaHeight) else topFit(areaWidth, areaHeight), settings, marginPx)
            NdsLayoutChoice.LARGE -> large(areaWidth, areaHeight, settings, beside = landscape)
            NdsLayoutChoice.STACKED ->
                if (landscape) stackedLargeFirst(areaWidth, areaHeight, settings.big) else stacked(areaWidth, areaHeight, settings.big)
        }
        val laid = if (regions.any { !it.inset && (it.dst.width <= 0f || it.dst.height <= 0f) }) emptyList() else regions
        val padOver = if (landscape) padOverGame else portraitPadOver
        return laid.map { it.copy(touch = it.screen == NdsScreen.BOTTOM && (it.screen == settings.big || !padOver)) }
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

    /** One screen fitted 4:3, centred horizontally, top-aligned. */
    private fun topFit(w: Float, h: Float): PxRect {
        val size = ScreenSize(min(w, h * ASPECT))
        return PxRect(wholePx((w - size.width) / 2f), 0f, size.width, size.height)
    }

    /** One screen fitted 4:3, centred on both axes. */
    private fun centreFit(w: Float, h: Float): PxRect {
        val size = ScreenSize(min(w, h * ASPECT))
        return PxRect(wholePx((w - size.width) / 2f), wholePx((h - size.height) / 2f), size.width, size.height)
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
     * Large screen: the big screen and the other one small next to it, apart. Like an inset the small one is drawn
     * unshaded (shaded regions share one size).
     * The small one is [NdsSettings.size] of the big one's width, at [NdsSettings.corner]: [beside] the big screen on
     * that side and level with its top or bottom edge (landscape, the pair centred), or above or below it at that side
     * (portrait, the pair top-aligned like the other portrait layouts).
     */
    private fun large(w: Float, h: Float, s: NdsSettings, beside: Boolean): List<NdsRegion> {
        val f = s.size.fraction
        val left = s.corner == PipCorner.TOP_LEFT || s.corner == PipCorner.BOTTOM_LEFT
        val top = s.corner == PipCorner.TOP_LEFT || s.corner == PipCorner.TOP_RIGHT
        val bigSize = ScreenSize(if (beside) min(w / (1f + f), h * ASPECT) else min(w, h / (1f + f) * ASPECT))
        val small = ScreenSize(bigSize.width * f)
        val bw = bigSize.width
        val bh = bigSize.height
        val big: PxRect
        val inset: PxRect
        if (beside) {
            val x = wholePx((w - bw - small.width) / 2f)
            val y = wholePx((h - bh) / 2f)
            val bigX = if (left) x + small.width else x
            big = PxRect(bigX, y, bw, bh)
            inset = PxRect(if (left) x else x + bw, if (top) y else y + bh - small.height, small.width, small.height)
        } else {
            val x = wholePx((w - bw) / 2f)
            big = PxRect(x, if (top) small.height else 0f, bw, bh)
            inset = PxRect(if (left) x else x + bw - small.width, if (top) 0f else bh, small.width, small.height)
        }
        if (inset.width <= 0f || inset.height <= 0f) return listOf(full(s.big, big))
        val other = s.big.other
        return listOf(full(s.big, big), NdsRegion(other, src(other), inset, shaded = false, touch = false))
    }

    /**
     * Landscape stacked: the [first] screen over the other at a third of its size (three quarters of the height and a
     * quarter), both centred; the second is drawn unshaded like an inset (shaded regions share one size).
     */
    private fun stackedLargeFirst(w: Float, h: Float, first: NdsScreen): List<NdsRegion> {
        val big = ScreenSize(min(w, h * 0.75f * ASPECT))
        // Unshaded, so exact thirds rather than whole pixels: the 4:3 shape holds even on a tiny screen.
        val smallW = big.width / 3f
        val smallH = big.height / 3f
        val y = wholePx((h - big.height - smallH) / 2f)
        val second = first.other
        return listOf(
            full(first, PxRect(wholePx((w - big.width) / 2f), y, big.width, big.height)),
            NdsRegion(second, src(second), PxRect((w - smallW) / 2f, y + big.height, smallW, smallH), shaded = false, touch = false),
        )
    }

    private fun stacked(w: Float, h: Float, first: NdsScreen): List<NdsRegion> {
        val size = ScreenSize(min(w, h * ASPECT / 2f))
        val x = wholePx((w - size.width) / 2f)
        val y = wholePx((h - 2f * size.height) / 2f)
        return listOf(
            full(first, PxRect(x, y, size.width, size.height)),
            full(first.other, PxRect(x, y + size.height, size.width, size.height)),
        )
    }

    private fun sideBySide(w: Float, h: Float, left: NdsScreen): List<NdsRegion> {
        val size = ScreenSize(min(w / 2f, h * ASPECT))
        val x = wholePx((w - 2f * size.width) / 2f)
        val y = wholePx((h - size.height) / 2f)
        return listOf(
            full(left, PxRect(x, y, size.width, size.height)),
            full(left.other, PxRect(x + size.width, y, size.width, size.height)),
        )
    }
}
