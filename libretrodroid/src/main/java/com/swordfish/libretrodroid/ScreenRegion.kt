/*
 *     Copyright (C) 2026  Mohammad Syuhada
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.swordfish.libretrodroid

/** A rectangle in 0..1 (y down). A plain type, so the mapping below runs in JVM unit tests. */
data class ScreenRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    fun contains(x: Float, y: Float): Boolean = x >= left && x <= right && y >= top && y <= bottom
}

/**
 * A part of the core frame ([src], 0..1 of the frame) drawn at [dst] (0..1 of the view's viewport). [shaded] regions go
 * through the shader preset, the others through the plain default shader; [touch] regions take touches, mapped back into
 * the frame. A list is drawn back to front. An unshaded region with [alpha] below 1 is blended over what is already drawn
 * (shaded regions ignore it); [alpha] never changes which region takes a touch.
 */
data class ScreenRegion(
    val src: ScreenRect,
    val dst: ScreenRect,
    val shaded: Boolean,
    val touch: Boolean,
    val alpha: Float = 1f,
)

/** One view of the core's video view map: a screen's rectangle in its frame, in px from the top-left as displayed. */
data class VideoView(val x: Int, val y: Int, val width: Int, val height: Int, val screen: Int, val eye: Int)

/** A position in the core frame, 0..1 each way (y down): what the core receives as its pointer. */
data class FramePoint(val x: Float, val y: Float)

/**
 * nx-mobile: a frame the frontend composes from the core's frame before drawing it like a core frame ([width] x [height]
 * px): each part draws its [ScreenRegion.src] (0..1 of the core frame) at its [ScreenRegion.dst] (0..1 of the composed
 * frame). Used to rebuild a core's own screen layout from its video view map.
 */
data class FrameComposition(val width: Int, val height: Int, val parts: List<ScreenRegion>)

object ScreenRegions {
    /** Where [p] (0..1 of the composed frame) lands in the core frame: through the touch part under it, else null. */
    fun mapCompositionTouch(c: FrameComposition, p: FramePoint): FramePoint? {
        val hit = c.parts.lastOrNull { it.dst.contains(p.x, p.y) } ?: return null
        if (!hit.touch || hit.dst.width <= 0f || hit.dst.height <= 0f) return null
        return FramePoint(
            hit.src.left + (p.x - hit.dst.left) / hit.dst.width * hit.src.width,
            hit.src.top + (p.y - hit.dst.top) / hit.dst.height * hit.src.height,
        )
    }

    const val PACKED_SIZE = 11

    /** For [LibretroDroid.setScreenRegions]: src l,t,r,b, dst l,t,r,b, shaded, touch (0/1), alpha per region, in order. */
    fun pack(regions: List<ScreenRegion>): FloatArray {
        val out = FloatArray(regions.size * PACKED_SIZE)
        regions.forEachIndexed { i, r ->
            val o = i * PACKED_SIZE
            out[o] = r.src.left; out[o + 1] = r.src.top; out[o + 2] = r.src.right; out[o + 3] = r.src.bottom
            out[o + 4] = r.dst.left; out[o + 5] = r.dst.top; out[o + 6] = r.dst.right; out[o + 7] = r.dst.bottom
            out[o + 8] = if (r.shaded) 1f else 0f
            out[o + 9] = if (r.touch) 1f else 0f
            out[o + 10] = r.alpha
        }
        return out
    }

    /**
     * Where a touch at ([viewX], [viewY], 0..1 of the view) lands in the core frame. The topmost region under it decides
     * (the last in the list), so a touch on an inset drawn over a touch region lands nowhere. Null outside every region,
     * on a region without [ScreenRegion.touch], and for an empty viewport.
     */
    fun mapTouch(regions: List<ScreenRegion>, viewport: ScreenRect, viewX: Float, viewY: Float): FramePoint? {
        if (viewport.width <= 0f || viewport.height <= 0f) return null
        val x = (viewX - viewport.left) / viewport.width
        val y = (viewY - viewport.top) / viewport.height
        val hit = regions.lastOrNull { it.dst.contains(x, y) } ?: return null
        if (!hit.touch || hit.dst.width <= 0f || hit.dst.height <= 0f) return null
        return FramePoint(
            hit.src.left + (x - hit.dst.left) / hit.dst.width * hit.src.width,
            hit.src.top + (y - hit.dst.top) / hit.dst.height * hit.src.height,
        )
    }
}
