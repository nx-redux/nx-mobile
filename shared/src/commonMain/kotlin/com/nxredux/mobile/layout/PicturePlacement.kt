package com.nxredux.mobile.layout

import com.nxredux.mobile.options.PxRect
import com.nxredux.mobile.options.frontendLayout
import kotlin.math.floor

/**
 * The core's video frame: [width] x [height] in its own pixels, [aspect] the display aspect (0 = unknown,
 * use width / height), [rotated] when the screen is turned a quarter (the displayed lines are then the width).
 */
data class CoreFrame(val width: Float, val height: Float, val aspect: Float, val rotated: Boolean)

/**
 * Where the game picture goes under Screen Scaling Auto (Native, Aspect and Fullscreen keep [frontendLayout] in the
 * whole game area): every console fitted at its aspect to the available [space], centred (a fractional scale; Native
 * is the whole-number one). With an overlay ([overlayAspect]) the returned rect is the overlay's box, fitted in
 * [space]; the game sits inside it by the usual in-box rule.
 */
fun picture(space: PxRect, frame: CoreFrame, overlayAspect: Float?): PxRect {
    if (space.width <= 0f || space.height <= 0f) return PxRect(space.x, space.y, 0f, 0f)
    if (overlayAspect != null && overlayAspect > 0f) return fitIn(space, overlayAspect)
    return fitIn(space, if (frame.aspect > 0f) frame.aspect else frame.width / frame.height)
}

/** A [width] x [height] picture centred in [space], at a whole-pixel position (rounded down), so a whole-number scale
 * puts every source pixel on whole screen pixels. */
private fun centred(space: PxRect, width: Float, height: Float) =
    PxRect(floor(space.x + (space.width - width) / 2f), floor(space.y + (space.height - height) / 2f), width, height)

private fun fitIn(space: PxRect, aspect: Float): PxRect =
    if (space.width / space.height > aspect) centred(space, space.height * aspect, space.height)
    else centred(space, space.width, space.width / aspect)
