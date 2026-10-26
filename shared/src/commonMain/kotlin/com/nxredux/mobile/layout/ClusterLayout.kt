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
 * when known, else the estimate max(systemPills x 1.4 unit + (systemPills - 1) x 12 dp, faceUnits x unit), where
 * [systemPills] is the pill count of the widest system row drawn (3 for the Wii's SELECT HOME START).
 */
fun reach(
    unitPx: Float,
    density: Float,
    cutoutLeft: Float,
    cutoutRight: Float,
    measuredButtonsPx: Float?,
    faceUnits: Float,
    systemPills: Int,
): Reach {
    val margin = LayoutConstants.CLUSTER_PADDING_DP * density + LayoutConstants.EDGE_NUDGE_UNITS * unitPx
    val extra = max(0f, max(cutoutLeft, cutoutRight) - margin)
    return Reach(margin + extra, measuredButtonsPx ?: max(systemRowPx(systemPills, unitPx, density), faceUnits * unitPx))
}

/** A system row of [pills] 1.4-unit pills, 12 dp apart, px. */
private fun systemRowPx(pills: Int, unitPx: Float, density: Float): Float =
    if (pills <= 0) 0f else pills * BUTTON_GROUP_UNITS * unitPx + (pills - 1) * BUTTON_GAP_DP * density

/** Per side, how much of the picture's span the on-screen pad halves take from it: the full reach on a wide tablet,
 * half of it otherwise. (With a controller there are no halves, so nothing is set aside: see [pictureSpace].) */
fun setAsidePx(screen: ScreenClass, reach: Reach): Float =
    if (screen == ScreenClass.WIDE_TABLET) reach.total else reach.total / 2f

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
 * - boxWidth = max(min(left, right), 4.5 unit + 2 pad, reach.buttons + 0.2 unit + 2 pad) + cutout extra, so the box
 *   holds the widest row drawn (reach.buttons: the measured groups, else the estimate with the system row's pills)
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
    val content = max(BOX_MIN_WIDTH_UNITS * unitPx, reach.buttonsPx + LayoutConstants.EDGE_NUDGE_UNITS * unitPx)
    val boxWidth = max(narrow, content + 2f * pad) + cutoutExtra
    val boxHeight = (BOX_ROWS + toolRows) * unitPx + 2f * pad
    val centreY = window.y + window.height / 2f
    val topMargin = centreY + pad + (CLUSTER_ROWS + toolRows) * unitPx - boxHeight
    val roomy = if (clear) max(0f, (narrow - reach.total - reach.edgePx) / 2f) else 0f
    return ClusterPlacement(boxWidth, boxHeight, topMargin, cutoutExtra + roomy, clear)
}

/** The landscape pad halves' button unit and [reach] in one window, px. */
data class BesideMeasures(val unitPx: Float, val reach: Reach)

/**
 * The pad beside the picture's unit ([landscapeUnitPx], on the window's height and short side) and [reach] in a
 * [windowW] x [windowH] window: one computation for the game and the Pad layout editor's preview, so both draw the same
 * pad. [secondRow]: both halves keep the second row. [measuredButtonsPx], [faceUnits] and [systemPills] as [reach].
 */
fun besideMeasures(
    screen: ScreenClass,
    windowW: Float,
    windowH: Float,
    ydpi: Float,
    density: Float,
    cutoutLeft: Float,
    cutoutRight: Float,
    secondRow: Boolean,
    measuredButtonsPx: Float?,
    faceUnits: Float,
    systemPills: Int,
): BesideMeasures {
    val unit = landscapeUnitPx(screen, windowH, min(windowW, windowH), ydpi, density, secondRow)
    return BesideMeasures(unit, reach(unit, density, cutoutLeft, cutoutRight, measuredButtonsPx, faceUnits, systemPills))
}

/**
 * [clusterPlacement] beside [picture]; with the picture not known (null: the game before its first frame, and the Pad
 * layout editor, which has no game) as if it took the window's middle half, so the unit, rows and boxes are already
 * those the game draws.
 */
fun besidePlacement(window: PxRect, picture: PxRect?, unitPx: Float, reach: Reach, density: Float, secondRow: Boolean): ClusterPlacement =
    clusterPlacement(
        window, picture ?: PxRect(window.x + window.width / 4f, window.y, window.width / 2f, window.height), unitPx, reach, density, secondRow,
    )

/** The typical picture the Pad layout editor previews the landscape pad around: a 224-line 4:3 console. */
private val PREVIEW_FRAME = CoreFrame(256f, 224f, 4f / 3f, rotated = false)

/** The Pad layout editor's landscape preview: the [measures], the typical [picture] and the halves' [placement] beside it. */
data class BesidePreview(val measures: BesideMeasures, val picture: PxRect, val placement: ClusterPlacement)

/**
 * The pad beside the picture in a [windowW] x [windowH] window around a typical game, for the Pad layout editor, which
 * has none: a 224-line 4:3 picture at the whole-number scale the game gives it under Auto in the space the pad leaves
 * ([pictureSpace], [setAsidePx], [picture]), and the halves placed beside that same rect ([besidePlacement]). The reach
 * is the estimate (nothing is measured in the editor). Arguments as [besideMeasures].
 */
fun besidePreview(
    screen: ScreenClass,
    windowW: Float,
    windowH: Float,
    ydpi: Float,
    density: Float,
    cutoutLeft: Float,
    cutoutRight: Float,
    secondRow: Boolean,
    faceUnits: Float,
    systemPills: Int,
): BesidePreview {
    val m = besideMeasures(screen, windowW, windowH, ydpi, density, cutoutLeft, cutoutRight, secondRow, null, faceUnits, systemPills)
    val space = pictureSpace(PadRoom.BESIDE, windowW, windowH, setAsidePx(screen, m.reach), 0f)
    val picture = picture(space, PREVIEW_FRAME, ConsoleScale.WHOLE_HEIGHT, overlayAspect = null)
    val placement = besidePlacement(PxRect(0f, 0f, windowW, windowH), picture, m.unitPx, m.reach, density, secondRow)
    return BesidePreview(m, picture, placement)
}
