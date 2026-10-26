package com.nxredux.mobile.layout

import com.nxredux.mobile.options.PxRect

/** What the on-screen pad takes from the game area, which decides the picture's space under Auto. */
enum class PadRoom {
    /** Landscape clusters beside the picture: the set-aside width ([setAsidePx]) stays free on each side. */
    BESIDE,

    /** Portrait with the band shown: the game area already ends above it. */
    BAND,

    /** Portrait with the band hidden: the see-through overlay across the bottom of the game area. */
    HIDDEN_BAND,

    /** No on-screen pad over the game (a controller, the tabletop halves below the hinge): the whole game area. */
    NONE,
}

/**
 * The picture's space in an [areaW] x [areaH] game area (its own pixels, top-left origin): [setAsidePx] off each side
 * for [PadRoom.BESIDE] (never past the middle), the [hiddenSpaceHeight] above an overlay [overlayH] tall, reaching
 * [reach] into it, for [PadRoom.HIDDEN_BAND], else the whole area.
 */
fun pictureSpace(
    room: PadRoom, areaW: Float, areaH: Float, setAsidePx: Float, overlayH: Float, reach: Float = LayoutConstants.HIDDEN_BAND_REACH,
): PxRect = when (room) {
    PadRoom.BESIDE -> {
        val side = setAsidePx.coerceIn(0f, areaW / 2f)
        PxRect(side, 0f, areaW - 2f * side, areaH)
    }
    PadRoom.HIDDEN_BAND -> PxRect(0f, 0f, areaW, hiddenSpaceHeight(areaH, overlayH, reach).coerceAtLeast(0f))
    PadRoom.BAND, PadRoom.NONE -> PxRect(0f, 0f, areaW, areaH)
}

/**
 * Where [picture], placed in its [pictureSpace], finally goes: with the band hidden its top follows [hiddenPictureTop]
 * (the top of the area when it reaches into the overlay, else centred above it); otherwise it stays as placed.
 */
fun placedFor(room: PadRoom, picture: PxRect, areaH: Float, overlayH: Float): PxRect =
    if (room == PadRoom.HIDDEN_BAND) picture.copy(y = hiddenPictureTop(areaH, overlayH, picture.height)) else picture
