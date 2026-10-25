package com.nxredux.mobile.options

import kotlin.math.floor
import kotlin.math.min

/** A rectangle in pixels of the game area. */
data class PxRect(val x: Float, val y: Float, val width: Float, val height: Float)

/**
 * [box]: where the picture is laid out (the overlay's box, else the whole area). [content]: the picture
 * itself, offset included. [offsetX]/[offsetY]: the screen offset in pixels.
 */
data class FrontendLayout(val box: PxRect, val content: PxRect, val offsetX: Float, val offsetY: Float)

/** The Brick's screen height: an offset value moves the picture the same visual amount as on the handheld. */
const val BRICK_HEIGHT = 768f

/**
 * Lays the picture out in an [areaWidth] x [areaHeight] game area. With an overlay, the box is the largest
 * centred box of [overlayAspect]. Native = the largest whole multiple of the core size (square pixels),
 * else Aspect; Aspect = fit keeping [coreAspect] (else the core size's, else the box's); Fullscreen = the
 * box. Offsets are `value x box height / 768`. Mirrors the fork's native `VideoLayout`.
 */
fun frontendLayout(
    areaWidth: Float,
    areaHeight: Float,
    coreWidth: Int,
    coreHeight: Int,
    coreAspect: Float,
    scaling: ScreenScaling,
    offsetX: Int,
    offsetY: Int,
    overlayAspect: Float?,
): FrontendLayout {
    if (areaWidth <= 0f || areaHeight <= 0f) {
        val zero = PxRect(0f, 0f, 0f, 0f)
        return FrontendLayout(zero, zero, 0f, 0f)
    }
    val area = PxRect(0f, 0f, areaWidth, areaHeight)
    val box = if (overlayAspect != null && overlayAspect > 0f) fit(area, overlayAspect) else area
    val dx = offsetX * box.height / BRICK_HEIGHT
    val dy = offsetY * box.height / BRICK_HEIGHT
    val aspect = when {
        coreAspect > 0f -> coreAspect
        coreWidth > 0 && coreHeight > 0 -> coreWidth.toFloat() / coreHeight
        else -> box.width / box.height
    }
    val picture = when (scaling) {
        ScreenScaling.FULLSCREEN -> box
        ScreenScaling.NATIVE -> integer(box, coreWidth, coreHeight) ?: fit(box, aspect)
        // AUTO follows ASPECT until the side-column placement lands.
        ScreenScaling.AUTO, ScreenScaling.ASPECT -> fit(box, aspect)
    }
    return FrontendLayout(box, picture.copy(x = picture.x + dx, y = picture.y + dy), dx, dy)
}

private fun fit(box: PxRect, aspect: Float): PxRect {
    val width: Float
    val height: Float
    if (box.width / box.height > aspect) {
        height = box.height
        width = height * aspect
    } else {
        width = box.width
        height = width / aspect
    }
    return PxRect(box.x + (box.width - width) / 2f, box.y + (box.height - height) / 2f, width, height)
}

private fun integer(box: PxRect, coreWidth: Int, coreHeight: Int): PxRect? {
    if (coreWidth <= 0 || coreHeight <= 0) return null
    val k = floor(min(box.width / coreWidth, box.height / coreHeight))
    if (k < 1f) return null
    val width = k * coreWidth
    val height = k * coreHeight
    return PxRect(box.x + (box.width - width) / 2f, box.y + (box.height - height) / 2f, width, height)
}
