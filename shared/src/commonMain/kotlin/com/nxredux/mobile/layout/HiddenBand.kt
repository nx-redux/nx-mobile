package com.nxredux.mobile.layout

import com.nxredux.mobile.ui.nx.midRowScale

/**
 * Height in pixels of portrait's see-through pad overlay over the game: the portrait pad column's
 * fixed 2.42 units (equal to `portraitOverlayUnits` in ui/nx/VirtualPad.kt, pinned by PortraitOverlayTest) plus three times the middle row's scale ([midRowScale] for the window's band [bandWidthUnits] x
 * [bandHeightUnits]), times [bandUnitPx], plus the cluster padding top and bottom.
 */
fun overlayHeightPx(bandUnitPx: Float, bandWidthUnits: Float, bandHeightUnits: Float, density: Float): Float =
    (2.42f + 3f * midRowScale(bandWidthUnits, bandHeightUnits)) * bandUnitPx +
        2f * LayoutConstants.CLUSTER_PADDING_DP * density

/**
 * Height of the space the picture may use above portrait's overlay: the overlay of [overlayH] may cover the bottom [reach]
 * of itself with the picture ([hiddenBandReach]), so only the rest is taken from [areaH].
 */
fun hiddenSpaceHeight(areaH: Float, overlayH: Float, reach: Float = LayoutConstants.HIDDEN_BAND_REACH): Float =
    areaH - (1f - reach) * overlayH

/**
 * How far into portrait's overlay a [ds] game's picture may reach, as a share of the overlay's height: a third
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
