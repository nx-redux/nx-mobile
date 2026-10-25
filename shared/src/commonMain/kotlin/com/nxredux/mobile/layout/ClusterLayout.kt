package com.nxredux.mobile.layout

import com.nxredux.mobile.options.PxRect
import kotlin.math.max
import kotlin.math.min

private const val UNIT_DIVISOR_PER_MID = 2.85f
private const val CLUSTER_ROWS = 2.57f
private const val TOOL_ROWS = 1.07f
private const val BOX_ROWS = 6f
private const val STUBBY_SHORT_SIDE_UNITS = 9f
private const val BOX_MIN_WIDTH_UNITS = 4.5f
private const val BUTTON_GROUP_UNITS = 1.4f
private const val BUTTON_GAP_DP = 12f

/**
 * Pixels in one landscape layout unit. With mid = windowH / 2 and pad = 16 dp:
 * min(11 mm, mid / 2.85, (mid - pad) / (2.57 + toolRows)), toolRows = 1.07 with the tool row else 0.
 * A stubby phone also caps at (shortSide - 2 * pad) / 9 so the cluster fits its portrait width.
 */
fun landscapeUnitPx(
    screen: ScreenClass,
    windowH: Float,
    shortSide: Float,
    ydpi: Float,
    density: Float,
    toolRow: Boolean,
): Float {
    val mid = windowH / 2f
    val pad = LayoutConstants.CLUSTER_PADDING_DP * density
    val toolRows = if (toolRow) TOOL_ROWS else 0f
    var unit = min(unitPx(ydpi, density), min(mid / UNIT_DIVISOR_PER_MID, (mid - pad) / (CLUSTER_ROWS + toolRows)))
    if (screen == ScreenClass.STUBBY_PHONE) unit = min(unit, (shortSide - 2f * pad) / STUBBY_SHORT_SIDE_UNITS)
    return unit
}

/** How far a pad half reaches into the window from its edge: the margin [edgePx] plus the button group [buttonsPx]. */
data class Reach(val edgePx: Float, val buttonsPx: Float) {
    val total: Float get() = edgePx + buttonsPx
}

/**
 * Edge = pad + 0.2 unit + extra, where extra = max(0, max(cutoutLeft, cutoutRight) - pad - 0.2 unit), so a camera
 * cutout wider than the margin moves both halves. Buttons = [measuredButtonsPx] (the user's tweaked group bounds)
 * when known, else the estimate max(2 x 1.4 unit + 12 dp, faceUnits x unit).
 */
fun reach(
    unitPx: Float,
    density: Float,
    cutoutLeft: Float,
    cutoutRight: Float,
    measuredButtonsPx: Float?,
    faceUnits: Float,
): Reach {
    val margin = LayoutConstants.CLUSTER_PADDING_DP * density + LayoutConstants.EDGE_NUDGE_UNITS * unitPx
    val extra = max(0f, max(cutoutLeft, cutoutRight) - margin)
    val estimate = max(2f * BUTTON_GROUP_UNITS * unitPx + BUTTON_GAP_DP * density, faceUnits * unitPx)
    return Reach(margin + extra, measuredButtonsPx ?: estimate)
}

/** Per side, how much of the picture's span the pad halves take from the picture: 0 with a controller, the full
 * reach on a wide tablet, half of it otherwise. */
fun setAsidePx(screen: ScreenClass, reach: Reach, controller: Boolean): Float = when {
    controller -> 0f
    screen == ScreenClass.WIDE_TABLET -> reach.total
    else -> reach.total / 2f
}

/**
 * Where the landscape pad halves sit. [boxWidthPx], [boxHeightPx], [topMarginPx] and [extraInsetPx] are pixels.
 * [clear] means both side columns hold the reach, so the halves sit beside the picture.
 */
data class ClusterPlacement(
    val boxWidthPx: Float,
    val boxHeightPx: Float,
    val topMarginPx: Float,
    val extraInsetPx: Float,
    val clear: Boolean,
)

/**
 * Columns: left = picture.x - window.x, right = window.right - picture.right.
 * - cutout extra = reach.edge - pad - 0.2 unit
 * - boxWidth = max(min(left, right), 4.5 unit + 2 pad) + cutout extra (the controller widens each side to its own column)
 * - boxHeight = (6 + toolRows) unit + 2 pad
 * - topMargin = window centre y + pad + (2.57 + toolRows) unit - boxHeight, so the d-pad centre sits at mid-height
 *   (rows stack from the box bottom: pad, [tool row 1.07], system 0.72, gap 0.35, half d-pad 1.5); may be negative
 * - clear = both columns >= reach.total
 * - extraInset = cutout extra + (if clear) max(0, (min column - reach.total - edge) / 2), centring the buttons
 */
fun clusterPlacement(
    window: PxRect,
    picture: PxRect,
    unitPx: Float,
    reach: Reach,
    density: Float,
    toolRow: Boolean,
): ClusterPlacement {
    val pad = LayoutConstants.CLUSTER_PADDING_DP * density
    val toolRows = if (toolRow) TOOL_ROWS else 0f
    val left = picture.x - window.x
    val right = (window.x + window.width) - (picture.x + picture.width)
    val narrow = min(left, right)
    val cutoutExtra = max(0f, reach.edgePx - pad - LayoutConstants.EDGE_NUDGE_UNITS * unitPx)
    val clear = left >= reach.total && right >= reach.total
    val boxWidth = max(narrow, BOX_MIN_WIDTH_UNITS * unitPx + 2f * pad) + cutoutExtra
    val boxHeight = (BOX_ROWS + toolRows) * unitPx + 2f * pad
    val centreY = window.y + window.height / 2f
    val topMargin = centreY + pad + (CLUSTER_ROWS + toolRows) * unitPx - boxHeight
    val roomy = if (clear) max(0f, (narrow - reach.total - reach.edgePx) / 2f) else 0f
    return ClusterPlacement(boxWidth, boxHeight, topMargin, cutoutExtra + roomy, clear)
}
