package com.nxredux.mobile.layout

import com.nxredux.mobile.options.PxRect

/** What the on-screen pad takes from the game area, which decides the picture's space under Auto. */
enum class PadRoom {
    /** Portrait: the skin's bare buttons see-through across the bottom of the game area. */
    HIDDEN_BAND,

    /** No band to keep clear (landscape, a controller, a pad skin): the whole game area. */
    NONE,
}

/**
 * The picture's space in an [areaW] x [areaH] game area (its own pixels, top-left origin): the [hiddenSpaceHeight]
 * above a band [overlayH] tall, reaching [reach] into it, for [PadRoom.HIDDEN_BAND], else the whole area.
 */
fun pictureSpace(
    room: PadRoom, areaW: Float, areaH: Float, overlayH: Float, reach: Float = LayoutConstants.HIDDEN_BAND_REACH,
): PxRect = when (room) {
    PadRoom.HIDDEN_BAND -> PxRect(0f, 0f, areaW, hiddenSpaceHeight(areaH, overlayH, reach).coerceAtLeast(0f))
    PadRoom.NONE -> PxRect(0f, 0f, areaW, areaH)
}

/**
 * Where [picture], placed in its [pictureSpace], finally goes: above portrait's band its top follows [hiddenPictureTop]
 * (the top of the area when it reaches into the band, else centred above it); otherwise it stays as placed.
 */
fun placedFor(room: PadRoom, picture: PxRect, areaH: Float, overlayH: Float): PxRect =
    if (room == PadRoom.HIDDEN_BAND) picture.copy(y = hiddenPictureTop(areaH, overlayH, picture.height)) else picture
