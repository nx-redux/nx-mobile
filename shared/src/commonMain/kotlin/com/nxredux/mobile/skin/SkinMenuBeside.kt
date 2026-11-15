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
    // a board the row rules place (its side groups hold only [rowIds]' controls): another keeps its own design
    fun SkinLayoutSpec.holds(p: PieceSpec) = controls.any { c ->
        c.id in rowIds() && c.anchor == p.anchor && c.x >= p.x && c.x <= p.x + p.w * p.scale && c.y >= p.y && c.y <= p.y + p.h * p.scale
    }
    if (!pieces.filter { it.glass == null && it.anchor != PieceAnchor.CENTRE }.all { holds(it) } ||
        !controls.filter { it.anchor == PieceAnchor.LEFT || it.anchor == PieceAnchor.RIGHT }.all { it.id in rowIds() }) return this
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

/**
 * A game layout with its d-pad, face buttons, START / SELECT and MENU where [menu] (the main menu's layout for the same
 * board, the reference) has them: each piece holding one (a ring, a well, a cap) moved by its control's offset to the
 * menu's, taking the menu's anchor and level, and every control it holds with it. L / R and anything else keep their
 * place (the row rules set L / R by their ring). As it is without [menu].
 */
internal fun SkinLayoutSpec.followMenu(
    menu: SkinLayoutSpec?, alias: Map<String, String> = emptyMap(), skip: Set<String> = emptySet(), anyHeight: Boolean = false,
): SkinLayoutSpec {
    // the same board drawn for the same height (a twin for taller windows, the stick and d-pad apart, keeps its own)
    if (menu == null || menu.refW != refW || menu.refH != refH || (!anyHeight && menu.minRefH != minRefH)) return this
    val ids = setOf("DPAD", "X", "Y", "A", "B", "START", "SELECT", "MENU") - skip + alias.keys
    // [alias]: a control of this board standing in for one of the menu's (the stick on the d-pad's spot)
    val theirs = menu.controls.filter { it.id in ids || it.id in alias.values }.associateBy { it.id }
        .let { m -> m + alias.mapNotNull { (mine, its) -> m[its]?.let { mine to it } } }.filterKeys { it in ids }
    fun inside(p: PieceSpec, c: ControlSpec) = c.anchor == p.anchor && c.x >= p.x && c.x <= p.x + p.w * p.scale && c.y >= p.y && c.y <= p.y + p.h * p.scale
    // a centred button the menu keeps centred too (a phone's bottom row) stays in its own row
    fun follows(c: ControlSpec) = c.id in theirs && !(c.anchor == PieceAnchor.CENTRE && theirs.getValue(c.id).anchor == PieceAnchor.CENTRE)
    // each piece holding a followed button: its move (dx, dy, anchor, level, drop), and the pieces inside it (a well's caps)
    data class Move(val dx: Float, val dy: Float, val to: ControlSpec)
    val moves = HashMap<Int, Move>()
    pieces.forEachIndexed { i, p ->
        if (p.glass != null) return@forEachIndexed
        val lead = controls.firstOrNull { it.id in ids && inside(p, it) && follows(it) } ?: return@forEachIndexed
        val to = theirs.getValue(lead.id)
        moves[i] = Move(to.x - lead.x, to.y - lead.y, to)
    }
    fun within(a: PieceSpec, b: PieceSpec) = a.x >= b.x && a.y >= b.y && a.x + a.w * a.scale <= b.x + b.w * b.scale && a.y + a.h * a.scale <= b.y + b.h * b.scale
    pieces.forEachIndexed { i, p ->
        if (i in moves || p.glass != null) return@forEachIndexed
        moves.entries.firstOrNull { (j, _) -> within(p, pieces[j]) }?.let { moves[i] = it.value }
    }
    fun moveOf(c: ControlSpec): Move? = pieces.indices.firstOrNull { it in moves && inside(pieces[it], c) }?.let { moves.getValue(it) }
    val newPieces = pieces.mapIndexed { i, p ->
        val m = moves[i] ?: return@mapIndexed p
        p.copy(x = (p.x + m.dx).roundToInt(), y = (p.y + m.dy).roundToInt(), anchor = m.to.anchor ?: p.anchor, level = m.to.level, drop = m.to.drop)
    }
    val newControls = controls.map { c ->
        moveOf(c)?.let { m -> c.copy(x = c.x + m.dx, y = c.y + m.dy, anchor = m.to.anchor, level = m.to.level, drop = m.to.drop) }
            ?: theirs[c.id]?.takeIf { follows(c) }?.let { to -> c.copy(x = to.x, y = to.y, anchor = to.anchor, level = to.level, drop = to.drop) }   // no piece holds it
            ?: c
    }
    return copy(pieces = newPieces, controls = newControls).swapOverMenu(menu)
}

/**
 * The Dreamcast's fold / tablet board showing the stick and the d-pad both ([this] its "both" board): the stick where the
 * menu has its d-pad (the d-pad goes under it, [SkinSolver.placePieces]), the face buttons and START where the menu has
 * them, and MENU under START in the right group, spaced as the menu's START / SELECT column.
 */
internal fun SkinLayoutSpec.bothFollowMenu(menu: SkinLayoutSpec?): SkinLayoutSpec {
    if (menu == null) return this
    val f = followMenu(menu, alias = mapOf("STICK" to "DPAD"), skip = setOf("DPAD", "MENU", "START"), anyHeight = true)
    val start = f.controls.firstOrNull { it.id == "START" } ?: return f
    val m = f.controls.firstOrNull { it.id == "MENU" } ?: return f
    val ms = menu.controls.firstOrNull { it.id == "START" } ?: return f
    val sel = menu.controls.firstOrNull { it.id == "SELECT" } ?: return f
    fun inside(p: PieceSpec, c: ControlSpec) = c.anchor == p.anchor && c.x >= p.x && c.x <= p.x + p.w * p.scale && c.y >= p.y && c.y <= p.y + p.h * p.scale
    fun centred(p: PieceSpec, c: ControlSpec) = p.glass == null && p.anchor == c.anchor &&
        kotlin.math.abs(p.x + p.w * p.scale / 2f - c.x) < 2f && kotlin.math.abs(p.y + p.h * p.scale / 2f - c.y) < 2f
    // the menu's START / SELECT column: START on its upper spot, MENU on its lower, in the menu's well
    val upper = if (ms.y <= sel.y) ms else sel; val lower = if (upper === ms) sel else ms
    val well = menu.pieces.firstOrNull { it.glass == null && inside(it, ms) && inside(it, sel) }
    // the d-pad in line with the stick already (its height under it is the solver's, [SkinSolver.placePieces])
    val stick = f.controls.firstOrNull { it.id == "STICK" }; val pad = f.controls.firstOrNull { it.id == "DPAD" }
    val px = if (stick != null && pad != null && stick.anchor == pad.anchor) (stick.x - pad.x).roundToInt() else 0
    // L2 / R2 drawn high on this board (over the stick where it was): down to the menu's L / R, so the board fits under
    // the fold at the menu's size (the solver sets them over their rings either way)
    val ml = menu.controls.firstOrNull { it.id == "L" }?.let { c -> menu.pieces.firstOrNull { it.glass == null && inside(it, c) } }
    val l2Piece = f.controls.firstOrNull { it.id == "L2" }?.let { c -> f.pieces.firstOrNull { it.glass == null && inside(it, c) } }
    val sdy = if (ml != null && l2Piece != null) ml.y - l2Piece.y else 0
    fun shoulder(id: String) = id == "L2" || id == "R2"
    val shoulderPieces = f.pieces.filter { p -> p.glass == null && f.controls.any { shoulder(it.id) && inside(p, it) } }.toSet()
    fun onto(p: PieceSpec, from: ControlSpec, to: ControlSpec) =
        p.copy(x = (p.x + to.x - from.x).roundToInt(), y = (p.y + to.y - from.y).roundToInt(), anchor = to.anchor ?: p.anchor, level = to.level, drop = to.drop)
    val moved = f.pieces.map { p ->
        if (centred(p, start)) onto(p, start, upper)
        else if (centred(p, m)) onto(p, m, lower)
        else if (pad != null && centred(p, pad)) p.copy(x = p.x + px)
        else if (p in shoulderPieces) p.copy(y = p.y + sdy) else p
    }
    val cap = f.pieces.indexOfFirst { centred(it, start) }
    val withWell = if (well == null || cap < 0) moved else moved.toMutableList().apply { add(cap, well) }
    // their words beside them as the menu's START's is
    fun ControlSpec.at(to: ControlSpec) = copy(x = to.x, y = to.y, anchor = to.anchor, level = to.level, drop = to.drop, labelDx = ms.labelDx, labelDy = ms.labelDy)
    return f.copy(
        pieces = withWell,
        controls = f.controls.map {
            if (it === start) it.at(upper)
            else if (it === m) it.at(lower)
            else if (it === pad) it.copy(x = it.x + px)
            else if (shoulder(it.id)) it.copy(y = it.y + sdy) else it
        },
    )
}

/**
 * The Dreamcast's stick / d-pad swap where following [menu] left it on MENU's spot (the fold board, MENU moved into the
 * left group): stacked over MENU as the menu's START / SELECT column is spaced, a two-button column. As it is elsewhere.
 */
private fun SkinLayoutSpec.swapOverMenu(menu: SkinLayoutSpec): SkinLayoutSpec {
    val m = controls.firstOrNull { it.id == "MENU" && it.anchor == PieceAnchor.LEFT } ?: return this
    val swaps = controls.filter { it.id == "STICK_SWAP" && it.anchor == PieceAnchor.LEFT }
    if (swaps.isEmpty() || swaps.none { kotlin.math.abs(it.y - m.y) < 1f && kotlin.math.abs(it.x - m.x) < 120f }) return this
    val start = menu.controls.firstOrNull { it.id == "START" }; val select = menu.controls.firstOrNull { it.id == "SELECT" }
    val step = if (start != null && select != null) kotlin.math.abs(select.y - start.y) else 126f
    val s0 = swaps.first()
    val dx = m.x - s0.x; val dy = m.y - step - s0.y
    return copy(
        // its caps: centred on it (MENU, now on the same spot, has its own)
        pieces = pieces.map { p ->
            val cx = p.x + p.w * p.scale / 2f; val cy = p.y + p.h * p.scale / 2f
            if (p.glass == null && p.anchor == PieceAnchor.LEFT && kotlin.math.abs(cx - s0.x) < 12f && kotlin.math.abs(cy - s0.y) < 12f)
                p.copy(x = (p.x + dx).roundToInt(), y = (p.y + dy).roundToInt()) else p
        },
        controls = controls.map { if (it.id == "STICK_SWAP" && it.anchor == PieceAnchor.LEFT) it.copy(x = it.x + dx, y = it.y + dy) else it },
    )
}

