package com.nxredux.mobile.layout

import com.nxredux.mobile.ui.nx.midRowScale

/**
 * Whether portrait play offers hiding the pad band for a console of [scale] showing [frame]. DS and 3DS always
 * (their two screens stack tall); the rest only when the displayed picture is taller than wide, which is unknown
 * before the first frame ([frame] null).
 */
fun hideOffered(scale: ConsoleScale, frame: CoreFrame?): Boolean {
    if (scale == ConsoleScale.DS || scale == ConsoleScale.N3DS) return true
    if (frame == null) return false
    val aspect = if (frame.aspect > 0f) frame.aspect
    else if (frame.rotated) frame.height / frame.width
    else frame.width / frame.height
    return aspect < 1f
}

/**
 * Whether the portrait band waits unseen for the first frame: the player chose Hidden ([hiddenChosen]) on a console of
 * [scale] whose hiding depends on the picture's shape ([hideOffered] is false for no frame, so not DS / 3DS) and
 * [frame] is not known yet. The band is laid out (a wide game keeps it) but drawn only once that frame decides, so a
 * tall game going to the overlay never shows it.
 */
fun bandAwaitsFrame(hiddenChosen: Boolean, scale: ConsoleScale, frame: CoreFrame?): Boolean =
    hiddenChosen && frame == null && !hideOffered(scale, null)

/**
 * Height in pixels of the pad overlay drawn over the game when the band is hidden: the portrait pad column's
 * fixed 2.42 units (equal to `portraitOverlayUnits` in ui/nx/VirtualPad.kt, pinned by PortraitOverlayTest) plus three times the middle row's scale ([midRowScale] for a band [bandWidthUnits] x
 * [bandHeightUnits]), times [bandUnitPx], plus the cluster padding top and bottom.
 */
fun overlayHeightPx(bandUnitPx: Float, bandWidthUnits: Float, bandHeightUnits: Float, density: Float): Float =
    (2.42f + 3f * midRowScale(bandWidthUnits, bandHeightUnits)) * bandUnitPx +
        2f * LayoutConstants.CLUSTER_PADDING_DP * density

/**
 * Height of the space the picture may use with the band hidden: the overlay of [overlayH] may cover the bottom [reach]
 * of itself with the picture ([hiddenBandReach]), so only the rest is taken from [areaH].
 */
fun hiddenSpaceHeight(areaH: Float, overlayH: Float, reach: Float = LayoutConstants.HIDDEN_BAND_REACH): Float =
    areaH - (1f - reach) * overlayH

/**
 * How far into the hidden band's overlay a [ds] game's picture may reach, as a share of the overlay's height: a third
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
