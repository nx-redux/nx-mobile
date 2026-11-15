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

/**
 * A game fold board the row rules place with its right side group (the face buttons' ring, R, its button column) moved
 * up or down so the ring stands at [menu]'s height (the main menu's fold board drawn alike, the reference); any other
 * board, or one without a ring in both, as it is.
 */
internal fun SkinLayoutSpec.faceAt(menu: SkinLayoutSpec?): SkinLayoutSpec {
    if (menu == null || menu === this || variant == SkinVariant.MENU || menu.minRefH != minRefH) return this
    // a board the row rules place (its side groups hold only [ROW_IDS]' controls): another keeps its own design
    fun SkinLayoutSpec.holds(p: PieceSpec) = controls.any { c ->
        c.id in ROW_IDS && c.anchor == p.anchor && c.x >= p.x && c.x <= p.x + p.w * p.scale && c.y >= p.y && c.y <= p.y + p.h * p.scale
    }
    if (!pieces.filter { it.glass == null && it.anchor != PieceAnchor.CENTRE }.all { holds(it) } ||
        !controls.filter { it.anchor == PieceAnchor.LEFT || it.anchor == PieceAnchor.RIGHT }.all { it.id in ROW_IDS }) return this
    val face = setOf("X", "Y", "A", "B")
    fun SkinLayoutSpec.ring() = pieces.firstOrNull { p ->
        p.glass == null && p.anchor == PieceAnchor.RIGHT && controls.any { c ->
            c.id in face && c.anchor == PieceAnchor.RIGHT && c.x >= p.x && c.x <= p.x + p.w * p.scale && c.y >= p.y && c.y <= p.y + p.h * p.scale
        }
    }
    val mine = ring() ?: return this
    val theirs = menu.ring() ?: return this
    // a full diamond only: a pair (the Atari 5200's fire buttons, PICO-8's O X) keeps its own place by its stick
    if (!face.all { id -> controls.any { c -> c.id == id && c.anchor == PieceAnchor.RIGHT && c.x >= mine.x && c.x <= mine.x + mine.w * mine.scale && c.y >= mine.y && c.y <= mine.y + mine.h * mine.scale } }) return this
    if (mine.level != theirs.level || mine.drop != theirs.drop) return this
    val dy = theirs.y - mine.y
    if (dy == 0) return this
    return copy(
        pieces = pieces.map { if (it.glass == null && it.anchor == PieceAnchor.RIGHT) it.copy(y = it.y + dy) else it },
        controls = controls.map { if (it.anchor == PieceAnchor.RIGHT) it.copy(y = it.y + dy) else it },
    )
}
