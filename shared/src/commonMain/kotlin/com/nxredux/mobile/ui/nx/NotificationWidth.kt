package com.nxredux.mobile.ui.nx

// The in-game top-centre notifications' width, kept from the landscape pad's old system rows: the notifications stay
// within the span they had between them, a long toast wrapping instead.

/** The share of the screen's width each side's pad box took: half. */
private const val CLUSTER_MAX_WIDTH_SHARE = 0.5f
/** A side's padding inside its box, dp. */
internal const val CLUSTER_PADDING = 16f
/** A side's content in units: 4.5 wide, 6 tall. */
private const val CLUSTER_W_UNITS = 4.5f
private const val CLUSTER_H_UNITS = 6f
/** A system pill's width in units, and the gap between two, dp. */
private const val SYSTEM_PILL_W_UNITS = 1.4f
private const val SYSTEM_PILL_GAP = 12f
/** The inset from the screen edge, units. */
private const val OVERLAY_EDGE_NUDGE = 0.2f

/**
 * A side's unit inside its padded box ([innerW] × [innerH] dp): the height's (innerH / 6), unless the box is narrower
 * than 4.5 of those: then the unit shrinks to fit the width.
 */
internal fun clusterUnit(innerW: Float, innerH: Float): Float = minOf(innerH / CLUSTER_H_UNITS, innerW / CLUSTER_W_UNITS)

/**
 * [clusterUnit] for a box [h] dp tall on a screen [screenW] dp wide, a display cutout [inset] dp on its own edge
 * ([symmetricCutoutInset] on both edges). 800 × 360: (360 − 32) / 6 ≈ 54.7; the Flip's cover, 474 wide: each fits
 * half, (237 − 32) / 4.5 ≈ 45.6.
 */
internal fun landscapeClusterUnit(screenW: Float, h: Float, inset: Float = 0f): Float =
    clusterUnit(screenW * CLUSTER_MAX_WIDTH_SHARE - inset - 2 * CLUSTER_PADDING, h - 2 * CLUSTER_PADDING)

/**
 * How far a side's top row (two pills) reaches in from its screen edge on a [screenW] × [h] dp screen with a cutout
 * [inset] dp: the inset, the padding, the nudge, the pills and the gap between them.
 */
internal fun overlaySystemRowReach(screenW: Float, h: Float, inset: Float): Float {
    val unit = landscapeClusterUnit(screenW, h, inset)
    return inset + CLUSTER_PADDING + unit * (OVERLAY_EDGE_NUDGE + 2 * SYSTEM_PILL_W_UNITS) + SYSTEM_PILL_GAP
}

/**
 * The widest the top-centre in-game notifications may be ([nxNotificationPlacement] top-centre, over the pad's bare
 * buttons or a pad skin): clear of [overlaySystemRowReach] on each side, so a long toast wraps instead.
 */
fun nxTopCentreMaxWidth(screenW: Float, h: Float, inset: Float): Float =
    (screenW - 2 * overlaySystemRowReach(screenW, h, inset)).coerceAtLeast(0f)

/**
 * The inset kept from both screen edges: the deeper of the display cutout's [start] and [end] insets (0 without a
 * cutout), so a camera hole on one side narrows both sides alike.
 */
fun symmetricCutoutInset(start: Float, end: Float): Float = maxOf(start, end)
