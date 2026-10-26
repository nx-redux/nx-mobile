package com.nxredux.mobile.layout

import com.nxredux.mobile.options.PxRect
import com.nxredux.mobile.options.ScreenScaling
import com.nxredux.mobile.options.frontendLayout
import kotlin.math.floor
import kotlin.math.min

/**
 * The core's video frame: [width] x [height] in its own pixels, [aspect] the display aspect (0 = unknown,
 * use width / height), [rotated] when the screen is turned a quarter (the displayed lines are then the width).
 */
data class CoreFrame(val width: Float, val height: Float, val aspect: Float, val rotated: Boolean)

/**
 * Where the game picture goes. With a [scaling] other than Auto the user's choice wins: today's layout in the
 * whole [window], ignoring [space]. With Auto the picture is sized for [scale] and centred in [space]:
 * whole-height consoles take the largest whole multiple of their displayed lines, native handhelds the largest
 * whole multiple of the core size, the rest fit at their aspect. DS and 3DS are placed like a fill here (their
 * screens are laid out separately). With an overlay ([overlayAspect]) the returned rect is the overlay's box,
 * fitted in [space]; the game sits inside it by the usual in-box rule.
 */
fun picture(
    space: PxRect,
    frame: CoreFrame,
    scale: ConsoleScale,
    scaling: ScreenScaling,
    window: PxRect,
    overlayAspect: Float?,
): PxRect {
    if (scaling != ScreenScaling.AUTO) {
        return frontendLayout(
            window.width, window.height, frame.width.toInt(), frame.height.toInt(), frame.aspect,
            scaling, 0, 0, overlayAspect,
        ).content
    }
    if (space.width <= 0f || space.height <= 0f) return PxRect(space.x, space.y, 0f, 0f)
    if (overlayAspect != null && overlayAspect > 0f) return fitIn(space, overlayAspect)

    val aspect = if (frame.aspect > 0f) frame.aspect else frame.width / frame.height
    return when (scale) {
        ConsoleScale.WHOLE_HEIGHT -> {
            val lines = if (frame.rotated) frame.width else frame.height
            val k = floor(min(space.height / lines, space.width / (lines * aspect))).coerceAtLeast(1f)
            centred(space, k * lines * aspect, k * lines)
        }
        ConsoleScale.NATIVE -> {
            val w = if (frame.rotated) frame.height else frame.width
            val h = if (frame.rotated) frame.width else frame.height
            val k = floor(min(space.width / w, space.height / h))
            if (k >= 1f) centred(space, k * w, k * h) else fitIn(space, aspect)
        }
        ConsoleScale.FILL, ConsoleScale.DS, ConsoleScale.N3DS -> fitIn(space, aspect)
    }
}

/** A [width] x [height] picture centred in [space], at a whole-pixel position (rounded down), so a whole-number scale
 * puts every source pixel on whole screen pixels. */
private fun centred(space: PxRect, width: Float, height: Float) =
    PxRect(floor(space.x + (space.width - width) / 2f), floor(space.y + (space.height - height) / 2f), width, height)

private fun fitIn(space: PxRect, aspect: Float): PxRect =
    if (space.width / space.height > aspect) centred(space, space.height * aspect, space.height)
    else centred(space, space.width, space.width / aspect)
