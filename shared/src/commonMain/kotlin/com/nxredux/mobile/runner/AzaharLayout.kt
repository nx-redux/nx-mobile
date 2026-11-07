package com.nxredux.mobile.runner

import com.nxredux.mobile.options.NdsScreen
import com.nxredux.mobile.options.PxRect
import kotlin.math.max
import kotlin.math.min

/** One 3DS screen drawn at [dst], in pixels of the composed frame. */
data class Placement(val screen: NdsScreen, val dst: PxRect)

/** The frame Azahar outputs for a layout and where each shown screen sits inside it. */
data class Composition(val width: Int, val height: Int, val placements: List<Placement>)

/**
 * Azahar's single-display layouts as the libretro core composes them today: the frame size from
 * ComputeLayoutGeometry and the screen rectangles from its FramebufferLayout, with the core's integer
 * truncation and float maths kept so the rectangles match pixel for pixel.
 */
object AzaharLayout {
    private const val TOP_W = 400
    private const val TOP_H = 240
    private const val BOTTOM_W = 320
    private const val BOTTOM_H = 240

    private data class Rect(val left: Int, val top: Int, val right: Int, val bottom: Int) {
        val width get() = right - left
        val height get() = bottom - top
        fun translate(dx: Int, dy: Int) = Rect(left + dx, top + dy, right + dx, bottom + dy)
        fun toPx() = PxRect(left.toFloat(), top.toFloat(), width.toFloat(), height.toFloat())
    }

    fun compose(layout: String, factor: Int, proportion: Float, swapped: Boolean): Composition = when (layout) {
        "single_screen" -> single(factor, swapped)
        "side_by_side" -> sideBySide(factor, swapped)
        else -> stacked(factor, proportion, swapped)
    }

    private fun single(factor: Int, swapped: Boolean): Composition {
        val screen = if (swapped) NdsScreen.BOTTOM else NdsScreen.TOP
        val w = (if (swapped) BOTTOM_W else TOP_W) * factor
        val h = (if (swapped) BOTTOM_H else TOP_H) * factor
        // SingleFrameLayout fits the shown screen to a frame of its own size: scale 1, no offset.
        return Composition(w, h, listOf(Placement(screen, Rect(0, 0, w, h).toPx())))
    }

    private fun sideBySide(factor: Int, swapped: Boolean): Composition {
        val w = (BOTTOM_W + TOP_W) * factor
        val h = TOP_H * factor
        return large(w, h, swapped, 1f, below = false)
    }

    private fun stacked(factor: Int, proportion: Float, swapped: Boolean): Composition {
        val largeW = if (swapped) BOTTOM_W else TOP_W
        val smallW = ((if (swapped) TOP_W else BOTTOM_W) / proportion).toInt()
        val w = max(largeW, smallW) * factor
        val h = (TOP_H + (BOTTOM_H / proportion).toInt()) * factor
        return large(w, h, swapped, proportion, below = true)
    }

    /** LargeFrameLayout with screen_gap 0, no integer scaling; [below] = BelowLarge, else MiddleRight. */
    private fun large(width: Int, height: Int, swapped: Boolean, scaleFactor: Float, below: Boolean): Composition {
        val largeH = if (swapped) BOTTOM_H else TOP_H
        val smallH = ((if (swapped) TOP_H else BOTTOM_H) / scaleFactor).toInt()
        val largeW = if (swapped) BOTTOM_W else TOP_W
        val smallW = ((if (swapped) TOP_W else BOTTOM_W) / scaleFactor).toInt()

        val emuW = if (below) max(largeW, smallW) else largeW + smallW
        val emuH = if (below) largeH + smallH else max(largeH, smallH)

        val fit = min(width.toFloat() / emuW, height.toFloat() / emuH)
        var total = Rect(0, 0, (emuW * fit).toInt(), (emuH * fit).toInt())
        total = total.translate((width - total.width) / 2, (height - total.height) / 2)

        val scale = total.height.toFloat() / emuH
        var largeRect = Rect(
            total.left, total.top,
            (largeW * scale + total.left).toInt(), (largeH * scale + total.top).toInt(),
        )
        var smallRect = Rect(
            total.left, total.top,
            (smallW * scale + total.left).toInt(), (smallH * scale + total.top).toInt(),
        )

        if (below) {
            if (largeRect.width < total.width) {
                largeRect = largeRect.translate((total.width - largeRect.width) / 2, 0)
            }
            smallRect = smallRect.translate(
                (largeRect.left - total.left) + largeRect.width / 2 - smallRect.width / 2,
                largeRect.height,
            )
        } else {
            smallRect = smallRect.translate(
                largeRect.width,
                (largeRect.height - smallRect.height) / 2 + largeRect.top - smallRect.top,
            )
        }

        val top = if (swapped) smallRect else largeRect
        val bottom = if (swapped) largeRect else smallRect
        return Composition(
            width, height,
            listOf(Placement(NdsScreen.TOP, top.toPx()), Placement(NdsScreen.BOTTOM, bottom.toPx())),
        )
    }
}
