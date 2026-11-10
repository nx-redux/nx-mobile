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

/** Which display's surface a save state's picture is copied from. */
enum class ThumbnailDisplay { MAIN, SECOND }

/** Where a save state's picture is copied from: the display's surface and the picture in its px (null: all of it). */
data class ThumbnailSource(val display: ThumbnailDisplay, val picture: PxRect?)

/**
 * Where to copy a save state's picture from. Main's surface ([main] DS / 3DS screens, else [mainPicture]) unless the
 * second display draws ([secondDrawing]) the picture: with the panel on main ([mainBlack]) main draws only black, so
 * the second display's screens ([second]; null when it draws the whole frame, at [secondWhole]) hold it; split, the
 * top screen is there when main shows the other one.
 */
fun thumbnailSource(
    main: List<NdsRegion>,
    mainPicture: PxRect?,
    second: List<NdsRegion>?,
    secondWhole: PxRect?,
    mainBlack: Boolean,
    secondDrawing: Boolean,
): ThumbnailSource {
    val onMain = ThumbnailSource(ThumbnailDisplay.MAIN, if (main.isNotEmpty()) main.thumbnailPicture() else mainPicture)
    if (!secondDrawing) return onMain
    if (mainBlack) {
        return ThumbnailSource(ThumbnailDisplay.SECOND, if (second.isNullOrEmpty()) secondWhole else second.thumbnailPicture())
    }
    val secondTop = second?.firstOrNull { it.screen == NdsScreen.TOP }
    if (secondTop != null && main.none { it.screen == NdsScreen.TOP }) return ThumbnailSource(ThumbnailDisplay.SECOND, secondTop.dst)
    return onMain
}
