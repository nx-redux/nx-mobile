package com.nxredux.mobile.layout

/**
 * Height of the space the picture may use above portrait's bare buttons: their band of [overlayH] may cover the bottom [reach]
 * of itself with the picture ([hiddenBandReach]), so only the rest is taken from [areaH].
 */
fun hiddenSpaceHeight(areaH: Float, overlayH: Float, reach: Float = LayoutConstants.HIDDEN_BAND_REACH): Float =
    areaH - (1f - reach) * overlayH

/**
 * How far into portrait's bare-button band a [ds] game's picture may reach, as a share of the band's height: a third
 * for the DS / 3DS ([LayoutConstants.DS_HIDDEN_BAND_REACH]), a quarter for the rest (tall arcade, WonderSwan, Lynx).
 */
fun hiddenBandReach(ds: Boolean): Float =
    if (ds) LayoutConstants.DS_HIDDEN_BAND_REACH else LayoutConstants.HIDDEN_BAND_REACH

/**
 * Top of a picture [pictureH] tall in [areaH] with an overlay of [overlayH] at the bottom: 0 when it reaches into
 * the overlay, else centred in the area above the overlay.
 */
fun hiddenPictureTop(areaH: Float, overlayH: Float, pictureH: Float): Float =
    if (pictureH > areaH - overlayH) 0f else (areaH - overlayH - pictureH) / 2f

/** True when a picture ending at [pictureBottom] stays clear of the overlay (no buttons over the picture). */
fun overlayClear(pictureBottom: Float, areaH: Float, overlayH: Float): Boolean =
    pictureBottom <= areaH - overlayH
