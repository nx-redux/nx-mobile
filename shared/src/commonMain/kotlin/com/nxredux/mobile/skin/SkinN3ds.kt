package com.nxredux.mobile.skin

import com.nxredux.mobile.options.NdsScreen
import com.nxredux.mobile.options.PxRect
import com.nxredux.mobile.runner.DualScreen
import com.nxredux.mobile.runner.NdsRegion
import kotlin.math.abs

/**
 * The 3DS screens in a pad skin's glasses (Dual screen off): the core runs its plain stack (azahar's "default" layout at
 * proportion 1, unswapped, as half-folded), and each screen is cut from it into the skin at its own shape, the top 5:3
 * and the touch screen 4:3, whichever glass it lands in.
 */
object SkinN3ds {
    private const val TOP_ASPECT = 5f / 3f
    private const val BOTTOM_ASPECT = 4f / 3f
    private const val STACK_ASPECT = 400f / 480f
    /** The 3DS screens' height (game px). */
    private const val SCREEN_H = 240f
    /** In a stack glass wider than the stack: the most of its height the upper screen takes. */
    private const val UPPER_MAX_SHARE = 0.6f

    /** Whether a [frameW] x [frameH] frame is the plain stack: 400s x 480s (to a pixel). */
    fun isPlainStack(frameW: Int, frameH: Int): Boolean =
        frameW > 0 && frameH > 0 && abs(frameW * 6 - frameH * 5) <= 6

    /**
     * Whether a [frameW] x [frameH] frame is cut into the two screens: the plain stack while the core is [pinned] to it
     * (unswapped); else (netplay keeps the player's layout, maybe swapped) the frame is drawn whole.
     */
    fun cutsStack(frameW: Int, frameH: Int, pinned: Boolean): Boolean = pinned && isPlainStack(frameW, frameH)

    /**
     * The two screens: with a [stack] glass, the biggest 400:480 rect in it cut into an upper and a lower half (a glass
     * wider than that: the upper screen across its width, the lower in the height left); else the
     * [top] and [touch] glasses. [big] goes in the upper slot, its other in the lower; each fitted at its own shape and
     * centred there. The touch screen takes touches; the upper screen is shaded, the lower only when the two are one size
     * (one shader chain). Null without the glasses.
     */
    fun regions(stack: PxRect?, top: PxRect?, touch: PxRect?, big: NdsScreen, layout: String? = null): List<NdsRegion>? {
        if (stack != null && layout != null && layout != "large") return laidOut(stack, big, layout)
        val (upper, lower) = if (stack != null && stack.width > stack.height * STACK_ASPECT) {
            // wider than the stack (landscape, user): the upper screen across the whole width at the top, at most
            // [UPPER_MAX_SHARE] of the height; the lower one in the height left under it
            val upperH = minOf(stack.width / aspectOf(big), stack.height * UPPER_MAX_SHARE)
            stack.copy(height = upperH) to stack.copy(y = stack.y + upperH, height = stack.height - upperH)
        } else if (stack != null) {
            val s = fit(stack, STACK_ASPECT)
            s.copy(height = s.height / 2f) to s.copy(y = s.y + s.height / 2f, height = s.height / 2f)
        } else (top ?: return null) to (touch ?: return null)
        val a = fit(upper, aspectOf(big))
        val b = fit(lower, aspectOf(big.other))
        val same = a.width == b.width && a.height == b.height
        return listOf(
            NdsRegion(big, DualScreen.n3dsSrc(big), a, shaded = true, touch = big == NdsScreen.BOTTOM),
            NdsRegion(big.other, DualScreen.n3dsSrc(big.other), b, shaded = same, touch = big.other == NdsScreen.BOTTOM),
        )
    }

    /**
     * The player's 3DS [layout] (N3dsOptions.LAYOUT_CYCLE, a regular phone's landscape, user) in the one [stack] glass:
     * Side by side (one height, [big] on the left), Stacked (one scale, [big] over), Single screen ([big] alone), each
     * fitted and centred; null for a layout it does not know.
     */
    private fun laidOut(stack: PxRect, big: NdsScreen, layout: String): List<NdsRegion>? {
        val wa = widthOf(big); val wb = widthOf(big.other)
        fun region(screen: NdsScreen, dst: PxRect, shaded: Boolean) =
            NdsRegion(screen, DualScreen.n3dsSrc(screen), dst, shaded = shaded, touch = screen == NdsScreen.BOTTOM)
        return when (layout) {
            "side_by_side" -> {
                val box = fit(stack, (wa + wb) / SCREEN_H)
                val s = box.height / SCREEN_H
                listOf(region(big, PxRect(box.x, box.y, wa * s, box.height), true),
                    region(big.other, PxRect(box.x + wa * s, box.y, wb * s, box.height), false))
            }
            "stacked" -> {
                val box = fit(stack, maxOf(wa, wb) / (2f * SCREEN_H))
                val s = box.height / (2f * SCREEN_H); val h = SCREEN_H * s
                val cx = box.x + box.width / 2f
                listOf(region(big, PxRect(cx - wa * s / 2f, box.y, wa * s, h), true),
                    region(big.other, PxRect(cx - wb * s / 2f, box.y + h, wb * s, h), false))
            }
            "single_screen" -> listOf(region(big, fit(stack, aspectOf(big)), true))
            else -> null
        }
    }

    /** A screen's width at the 3DS's one height ([SCREEN_H]): the top 400, the touch screen 320. */
    private fun widthOf(screen: NdsScreen) = if (screen == NdsScreen.TOP) 400f else 320f

    /** A frame that isn't the plain stack, whole at its own shape in the [stack] glass, else the [top] one; null without either. */
    fun whole(stack: PxRect?, top: PxRect?, frameW: Int, frameH: Int): PxRect? {
        val g = stack ?: top ?: return null
        return if (frameW > 0 && frameH > 0) fit(g, frameW.toFloat() / frameH) else g
    }

    private fun aspectOf(screen: NdsScreen) = if (screen == NdsScreen.TOP) TOP_ASPECT else BOTTOM_ASPECT

    /** The biggest [aspect] (width / height) rect centred in [r]. */
    private fun fit(r: PxRect, aspect: Float): PxRect {
        val w = minOf(r.width, r.height * aspect)
        val h = w / aspect
        return PxRect(r.x + (r.width - w) / 2f, r.y + (r.height - h) / 2f, w, h)
    }
}
