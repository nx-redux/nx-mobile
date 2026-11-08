package com.nxredux.mobile.runner

import com.nxredux.mobile.options.NdsScreen
import com.nxredux.mobile.options.PxRect
import kotlin.math.roundToInt

/** The part of the game surface a save state's picture keeps, surface px. */
data class ThumbnailCrop(val x: Int, val y: Int, val width: Int, val height: Int)

/**
 * The game's own [picture] (surface px) within the [w] x [h] surface, rounded and clamped to it: a save state's picture
 * without the bars or anything else drawn around the game. Null keeps the whole surface: no one picture rect (the DS /
 * 3DS screens), the picture is the whole surface, or nothing of it is on the surface.
 */
fun thumbnailCrop(picture: PxRect?, w: Int, h: Int): ThumbnailCrop? {
    if (picture == null) return null
    val left = picture.x.roundToInt().coerceIn(0, w)
    val top = picture.y.roundToInt().coerceIn(0, h)
    val right = (picture.x + picture.width).roundToInt().coerceIn(0, w)
    val bottom = (picture.y + picture.height).roundToInt().coerceIn(0, h)
    if (right - left <= 0 || bottom - top <= 0) return null
    if (left == 0 && top == 0 && right == w && bottom == h) return null
    return ThumbnailCrop(left, top, right - left, bottom - top)
}

/**
 * A DS / 3DS save state's picture: the top screen alone, wherever the layout draws it (an inset too); the drawn
 * screen when the top one is not shown (Single screen on the bottom). Null with no screens.
 */
fun List<NdsRegion>.thumbnailPicture(): PxRect? =
    (firstOrNull { it.screen == NdsScreen.TOP } ?: firstOrNull { !it.inset } ?: firstOrNull())?.dst
