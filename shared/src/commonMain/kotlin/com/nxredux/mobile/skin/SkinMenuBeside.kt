package com.nxredux.mobile.skin

import kotlin.math.roundToInt

/**
 * The main menu's fold board with MENU in the left group: under the d-pad, mirroring the START / SELECT column on the
 * right (level with the pair's middle, its label on its left), so nothing is left under the menu's screen and its frame
 * grows down to the side groups' bottom ([SkinSolver.placePieces]). The layout as it is where it has no MENU cap under
 * the screen or no START / SELECT pair to mirror.
 */
internal fun SkinLayoutSpec.menuBesideTheDpad(): SkinLayoutSpec {
    val menu = controls.firstOrNull { it.id == "MENU" && it.anchor == PieceAnchor.CENTRE } ?: return this
    val start = controls.firstOrNull { it.id == "START" && it.anchor == PieceAnchor.RIGHT } ?: return this
    val select = controls.firstOrNull { it.id == "SELECT" && it.anchor == PieceAnchor.RIGHT } ?: return this
    // the MENU cap: the centred piece without a screen that holds the control's middle
    val cap = pieces.indexOfFirst { p ->
        p.glass == null && p.anchor == PieceAnchor.CENTRE &&
            menu.x >= p.x && menu.x <= p.x + p.w * p.scale && menu.y >= p.y && menu.y <= p.y + p.h * p.scale
    }
    if (cap < 0) return this
    val x = refW - start.x
    val y = (start.y + select.y) / 2f
    val p = pieces[cap]
    val moved = p.copy(
        x = (x - p.w * p.scale / 2f).roundToInt(), y = (y - p.h * p.scale / 2f).roundToInt(),
        anchor = PieceAnchor.LEFT, inward = start.inward, level = start.level,
    )
    val control = menu.copy(x = x, y = y, anchor = PieceAnchor.LEFT, inward = start.inward, level = start.level,
        labelDx = -start.labelDx, labelDy = start.labelDy)
    return copy(
        pieces = pieces.mapIndexed { i, it -> if (i == cap) moved else it },
        controls = controls.map { if (it === menu) control else it },
    )
}
