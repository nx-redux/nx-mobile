package com.nxredux.mobile.layout

import com.nxredux.mobile.ui.nx.PAD_PORTRAIT_CM
import com.nxredux.mobile.ui.nx.padBandPx
import kotlin.math.floor

/** Tablet pad buttons span this many units, so the band has to hold them at full size. */
private const val TABLET_BAND_UNITS = 6.2f

/**
 * Height in pixels of the portrait pad band, for the main menu and the game alike so the two cannot drift.
 * [areaPx] is the window height minus the top display cutout.
 *
 * Tablet (the width left after the cluster padding still fits nine 11 mm units): the 5.8 cm band, grown to hold
 * the buttons at 11 mm, but never past [LayoutConstants.BAND_MAX_SHARE] of the area. Phone: the 5.8 cm band,
 * shrunk so a 224-line 4:3 picture, whole-number scaled to the width, keeps its place above it.
 */
fun portraitBandPx(widthPx: Float, areaPx: Float, ydpi: Float, density: Float): Float {
    val full = padBandPx(PAD_PORTRAIT_CM, ydpi, density, areaPx)
    val unit = unitPx(ydpi, density)
    val pad = LayoutConstants.CLUSTER_PADDING_DP * density
    if ((widthPx - 2f * pad) / 9f >= unit) {
        val wanted = minOf(TABLET_BAND_UNITS * unit + 2f * pad, LayoutConstants.BAND_MAX_SHARE * areaPx)
        return maxOf(full, wanted)
    }
    val lineW = LayoutConstants.REFERENCE_LINES * 4f / 3f
    val k = floor(widthPx / lineW)
    if (k < 1f) return full
    val ph = LayoutConstants.REFERENCE_LINES * k
    val gap = (widthPx - ph * 4f / 3f) / 2f
    return maxOf(0f, minOf(full, areaPx - ph - 2f * gap))
}
