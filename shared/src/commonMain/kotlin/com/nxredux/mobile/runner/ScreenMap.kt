package com.nxredux.mobile.runner

import com.nxredux.mobile.options.NdsScreen

/** One view of a core's video view map (libretro SET_VIDEO_VIEWS): a screen's rectangle in its frame, px from the top-left. */
data class MapView(val x: Int, val y: Int, val width: Int, val height: Int, val screen: Int, val eye: Int)

/**
 * Where a DS / 3DS core's two screens sit in its frame, from its video view map: screen 0 is the top screen, screen 1 the
 * bottom. [src] replaces the fixed cuts of the plain stack (NdsLayout.TOP_SRC, DualScreen.N3DS_BOTTOM_SRC, ...).
 */
data class ScreenMap(
    val top: FrameRect,
    val bottom: FrameRect,
    val topSizePx: Pair<Int, Int>,
    val bottomSizePx: Pair<Int, Int>,
) {
    fun src(screen: NdsScreen): FrameRect = if (screen == NdsScreen.TOP) top else bottom

    companion object {
        private const val EYE_RIGHT = 2

        /**
         * The map for a [frameW] x [frameH] frame; null with no views, a missing screen 0 or 1, or a rectangle outside the
         * frame (a frame the map no longer fits, mid-resize: the caller falls back to the fixed cuts). A stereo pair's
         * right eye is ignored. [byShape]: the top screen is the wider view rather than screen 0 (Azahar numbers its
         * views by Prominent 3DS Screen, so a swapped frame sends the bottom screen as 0; the 3DS top is 5:3, the bottom 4:3).
         */
        fun from(views: List<MapView>, frameW: Int, frameH: Int, byShape: Boolean = false): ScreenMap? {
            if (frameW <= 0 || frameH <= 0) return null
            val shown = views.filter { it.eye != EYE_RIGHT }
            val first = shown.firstOrNull { it.screen == 0 } ?: return null
            val second = shown.firstOrNull { it.screen == 1 } ?: return null
            val firstIsTop = !byShape || first.width * second.height >= second.width * first.height
            val top = if (firstIsTop) first else second
            val bottom = if (firstIsTop) second else first
            if (listOf(top, bottom).any { it.x < 0 || it.y < 0 || it.x + it.width > frameW || it.y + it.height > frameH }) return null
            fun rect(v: MapView) = FrameRect(
                v.x / frameW.toFloat(), v.y / frameH.toFloat(), (v.x + v.width) / frameW.toFloat(), (v.y + v.height) / frameH.toFloat(),
            )
            return ScreenMap(rect(top), rect(bottom), top.width to top.height, bottom.width to bottom.height)
        }
    }
}

/** These regions with each screen's source taken from the core's [map]; unchanged without one. */
fun List<NdsRegion>.withMap(map: ScreenMap?): List<NdsRegion> =
    if (map == null) this else map { it.copy(src = map.src(it.screen)) }
