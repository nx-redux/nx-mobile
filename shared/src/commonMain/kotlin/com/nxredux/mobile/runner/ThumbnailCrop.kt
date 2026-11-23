package com.nxredux.mobile.runner

import com.nxredux.mobile.options.NdsScreen
import com.nxredux.mobile.options.PxRect
import kotlin.math.PI
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

// Screens this close in area are the same size (Stacked, Side by side): px rounding must not pick the bottom one.
private const val SAME_SIZE = 0.97f

/**
 * The one DS / 3DS screen a save state's picture keeps: the biggest drawn (Large screen: the big one, wherever SWAP put
 * it), the top screen when both are the same size; an inset never while another screen is drawn. Null with no screens.
 */
fun List<NdsRegion>.thumbnailRegion(): NdsRegion? {
    val drawn = filterNot { it.inset }.ifEmpty { this }
    val biggest = drawn.maxOfOrNull { it.dst.width * it.dst.height } ?: return null
    val big = drawn.filter { it.dst.width * it.dst.height >= biggest * SAME_SIZE }
    return big.firstOrNull { it.screen == NdsScreen.TOP } ?: big.first()
}

/** Where [thumbnailRegion] is drawn. */
fun List<NdsRegion>.thumbnailPicture(): PxRect? = thumbnailRegion()?.dst

/** One screen inside a picture drawn as one rect (a frame holding both DS / 3DS screens): [src] of the core frame, [dst] 0..1 of the picture. */
data class PictureScreen(val screen: NdsScreen, val src: FrameRect, val dst: FrameRect)

/** These screens where the picture is drawn, at [picture] (px). */
fun List<PictureScreen>.placedIn(picture: PxRect): List<NdsRegion> = map {
    NdsRegion(
        it.screen, it.src,
        PxRect(
            picture.x + it.dst.left * picture.width, picture.y + it.dst.top * picture.height,
            (it.dst.right - it.dst.left) * picture.width, (it.dst.bottom - it.dst.top) * picture.height,
        ),
        shaded = false, touch = it.screen == NdsScreen.BOTTOM,
    )
}

/** The screens of a core frame drawn whole, from its video view [map]: each screen sits in the picture where it sits in the frame. */
fun ScreenMap.pictureScreens(): List<PictureScreen> =
    listOf(PictureScreen(NdsScreen.TOP, top, top), PictureScreen(NdsScreen.BOTTOM, bottom, bottom))

/** Which display's surface a save state's picture is copied from. */
enum class ThumbnailDisplay { MAIN, SECOND }

/**
 * What a save state's picture keeps: the display showing it and where on that display's surface ([picture], px; null:
 * the whole surface), and the part of the core frame it is ([src]; null: the whole frame).
 */
data class ThumbnailSource(val display: ThumbnailDisplay, val picture: PxRect?, val src: FrameRect? = null) {
    /**
     * The display's surface holds the picture now, for a copy of what it shows: main draws only black under the panel
     * ([mainBlack]), and the second display only while it is drawn ([secondDrawing]).
     */
    fun copyable(mainBlack: Boolean, secondDrawing: Boolean): Boolean = when (display) {
        ThumbnailDisplay.MAIN -> !mainBlack
        ThumbnailDisplay.SECOND -> secondDrawing
    }
}

/**
 * What a save state's picture keeps: the game on the display that shows the picture. That is the second display when
 * the picture goes there ([pictureOnSecond]) and it draws something ([second] DS / 3DS screens, or with null the whole
 * frame at [secondWhole]); else main ([main] screens, else the one picture at [mainPicture]). DS / 3DS screens keep one
 * of them ([thumbnailRegion]).
 */
fun thumbnailSource(
    main: List<NdsRegion>,
    mainPicture: PxRect?,
    second: List<NdsRegion>?,
    secondWhole: PxRect?,
    pictureOnSecond: Boolean,
): ThumbnailSource {
    if (pictureOnSecond) {
        val screen = second?.thumbnailRegion()
        if (screen != null) return ThumbnailSource(ThumbnailDisplay.SECOND, screen.dst, screen.src)
        if (second == null && secondWhole != null) return ThumbnailSource(ThumbnailDisplay.SECOND, secondWhole)
    }
    val screen = main.thumbnailRegion() ?: return ThumbnailSource(ThumbnailDisplay.MAIN, mainPicture)
    return ThumbnailSource(ThumbnailDisplay.MAIN, screen.dst, screen.src)
}

/**
 * A read of the core's own frame for a save state's picture: [src] of the frame (y down), drawn at [width] x [height]
 * in the frame's orientation, then turned [quarterTurns] counterclockwise to stand as it is shown.
 */
data class FrameRead(val src: FrameRect, val width: Int, val height: Int, val quarterTurns: Int)

private val WHOLE_FRAME = FrameRect(0f, 0f, 1f, 1f)

/**
 * The frame read for [source]: the picture's size as shown, fitted by [size] (the stored thumbnail's), read in the
 * frame's own orientation when the core turns its frame ([rotation], radians as SET_ROTATION gives it: a quarter turn
 * counterclockwise is -PI/2). DS / 3DS screens are never turned. Null without a picture size to go by.
 */
fun frameRead(source: ThumbnailSource, rotation: Float, size: (Int, Int) -> Pair<Int, Int>): FrameRead? {
    val picture = source.picture ?: return null
    val w = picture.width.roundToInt()
    val h = picture.height.roundToInt()
    if (w <= 0 || h <= 0) return null
    val (tw, th) = size(w, h)
    val turns = if (source.src != null) 0 else (((rotation / (-PI / 2)).roundToInt() % 4) + 4) % 4
    val src = source.src ?: WHOLE_FRAME
    return if (turns % 2 == 1) FrameRead(src, th, tw, turns) else FrameRead(src, tw, th, turns)
}
