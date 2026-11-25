package com.nxredux.mobile.runner

import com.nxredux.mobile.options.NdsLayoutChoice
import com.nxredux.mobile.options.NdsScreen
import com.nxredux.mobile.options.NdsSettings
import com.nxredux.mobile.options.PipCorner
import com.nxredux.mobile.options.PxRect
import kotlin.math.abs
import kotlin.math.min

/**
 * Where the Wii U's two screens go. Cemu draws them stacked in one frame, the TV over the GamePad, each the full width
 * and 16:9 (1280x1440 at 1x). The TV is [NdsScreen.TOP], the GamePad [NdsScreen.BOTTOM] and the touch screen.
 * Unlike the DS, every layout fills its space at any scale: a 1280x720 screen at a whole-number scale would rarely fit a
 * phone at all.
 */
object WiiuLayout {
    private const val ASPECT = 16f / 9f
    private const val FRAME_ASPECT = 8f / 9f
    private const val LARGE_OTHER = 0.5f

    val TV_SRC = FrameRect(0f, 0f, 1f, 0.5f)
    val PAD_SRC = FrameRect(0f, 0.5f, 1f, 1f)

    fun src(screen: NdsScreen): FrameRect = if (screen == NdsScreen.TOP) TV_SRC else PAD_SRC

    /** The TV over the GamePad at any internal resolution. */
    fun isWiiuFrame(width: Int, height: Int): Boolean =
        width > 0 && height > 0 && abs(width.toFloat() / height - FRAME_ASPECT) < 0.01f

    /**
     * The regions of [layout] in [space] (game-area px), the big screen ([NdsSettings.big]) first; empty without room.
     * The GamePad takes touches, except as a picture-in-picture inset (drawn over the big screen, as on the DS).
     */
    fun regions(space: PxRect, layout: NdsLayoutChoice, settings: NdsSettings, insetMarginPx: Float): List<NdsRegion> {
        if (space.width <= 0f || space.height <= 0f) return emptyList()
        val big = settings.big
        val regions = when (layout) {
            NdsLayoutChoice.STACKED -> pair(space, big, cols = 1, rows = 2)
            NdsLayoutChoice.SIDE_BY_SIDE -> pair(space, big, cols = 2, rows = 1)
            NdsLayoutChoice.LARGE -> large(space, big)
            NdsLayoutChoice.SINGLE -> fit(space, 1f, 1f)?.let { listOf(screen(big, it)) }
            NdsLayoutChoice.PIP -> fit(space, 1f, 1f)?.let { pip(it, settings, insetMarginPx) }
        }.orEmpty()
        return regions.map { it.copy(touch = it.screen == NdsScreen.BOTTOM && !it.inset) }
    }

    private fun screen(which: NdsScreen, dst: PxRect) =
        NdsRegion(which, src(which), dst, shaded = NdsLayout.takesShader(which), touch = which == NdsScreen.BOTTOM)

    /** One screen's whole-px size when [cols] x [rows] screens' worth fits [space]; null below a pixel. */
    private fun size(space: PxRect, cols: Float, rows: Float): NdsLayout.ScreenSize? {
        val s = NdsLayout.ScreenSize(min(space.width / cols, space.height / rows * ASPECT), ASPECT)
        return s.takeIf { it.width >= 1f && it.height >= 1f }
    }

    private fun centred(space: PxRect, w: Float, h: Float) =
        PxRect(space.x + NdsLayout.wholePx((space.width - w) / 2f), space.y + NdsLayout.wholePx((space.height - h) / 2f), w, h)

    private fun fit(space: PxRect, cols: Float, rows: Float): PxRect? = size(space, cols, rows)?.let { centred(space, it.width, it.height) }

    /** Both screens one size, [first] then the other, edge to edge across ([cols] 2) or down ([rows] 2), centred. */
    private fun pair(space: PxRect, first: NdsScreen, cols: Int, rows: Int): List<NdsRegion>? {
        val s = size(space, cols.toFloat(), rows.toFloat()) ?: return null
        val at = centred(space, s.width * cols, s.height * rows)
        val second = if (cols == 2) PxRect(at.x + s.width, at.y, s.width, s.height) else PxRect(at.x, at.y + s.height, s.width, s.height)
        return listOf(screen(first, PxRect(at.x, at.y, s.width, s.height)), screen(first.other, second))
    }

    /**
     * Large screen: the [big] screen with the other at half its width, under it or beside it (bottoms level), whichever
     * lets the big one be bigger: under on portrait and near-square screens, beside on a wide phone in landscape.
     */
    private fun large(space: PxRect, big: NdsScreen): List<NdsRegion>? {
        val under = size(space, 1f, 1f + LARGE_OTHER)
        val beside = size(space, 1f + LARGE_OTHER, 1f)
        val besideWins = beside != null && (under == null || beside.width > under.width)
        val s = (if (besideWins) beside else under) ?: return null
        val other = NdsLayout.ScreenSize(s.width * LARGE_OTHER, ASPECT)
        if (other.width < 1f || other.height < 1f) return null
        return if (besideWins) {
            val at = centred(space, s.width + other.width, s.height)
            listOf(
                screen(big, PxRect(at.x, at.y, s.width, s.height)),
                screen(big.other, PxRect(at.x + s.width, at.y + s.height - other.height, other.width, other.height)),
            )
        } else {
            val at = centred(space, s.width, s.height + other.height)
            listOf(
                screen(big, PxRect(at.x, at.y, s.width, s.height)),
                screen(big.other, PxRect(at.x + NdsLayout.wholePx((s.width - other.width) / 2f), at.y + s.height, other.width, other.height)),
            )
        }
    }

    /** The big screen in [big] and the other as an inset in its corner, as the DS's picture in picture. */
    private fun pip(big: PxRect, s: NdsSettings, margin: Float): List<NdsRegion> {
        val width = minOf(big.width * s.size.fraction, (big.height - 2f * margin) * ASPECT, big.width - 2f * margin)
        if (width <= 0f) return listOf(screen(s.big, big))
        val height = width / ASPECT
        val left = s.corner == PipCorner.TOP_LEFT || s.corner == PipCorner.BOTTOM_LEFT
        val top = s.corner == PipCorner.TOP_LEFT || s.corner == PipCorner.TOP_RIGHT
        val x = if (left) big.x + margin else big.x + big.width - margin - width
        val y = if (top) big.y + margin else big.y + big.height - margin - height
        val small = s.big.other
        return listOf(
            screen(s.big, big),
            NdsRegion(small, src(small), PxRect(x, y, width, height), shaded = false, touch = false, inset = true, alpha = s.opacity.alpha),
        )
    }
}
