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
    // a followed button's word where the menu's is (MENU's beside it on the left, not under it)
    fun ControlSpec.wordAs(c: ControlSpec) = theirs[c.id]?.takeIf { follows(c) && c.label != null }?.let { copy(labelDx = it.labelDx, labelDy = it.labelDy) } ?: this
    val newControls = controls.map { c ->
        (moveOf(c)?.let { m -> c.copy(x = c.x + m.dx, y = c.y + m.dy, anchor = m.to.anchor, level = m.to.level, drop = m.to.drop) }
            ?: theirs[c.id]?.takeIf { follows(c) }?.let { to -> c.copy(x = to.x, y = to.y, anchor = to.anchor, level = to.level, drop = to.drop) }   // no piece holds it
            ?: c).wordAs(c)
    }
    return copy(pieces = newPieces, controls = newControls).swapOverMenu(menu)
}

/**
 * A fold / tablet board showing the stick and the d-pad both ([this] its "both" board): the stick where the menu has its
 * d-pad (the d-pad goes under it, [SkinSolver.placePieces]), the face buttons (the 3DS's C-stick nub with them) where
 * the menu has them. The Dreamcast ([menuUnderStart]): START where the menu has it and MENU under it in the right group,
 * spaced as the menu's START / SELECT column. The 3DS: START / SELECT where the menu has them, MENU kept in the row
 * under the touch screen with MIC and SWAP.
 */
internal fun SkinLayoutSpec.bothFollowMenu(menu: SkinLayoutSpec?, menuUnderStart: Boolean = true): SkinLayoutSpec {
    if (menu == null) return this
    if (!menuUnderStart) return followMenu(menu, alias = mapOf("STICK" to "DPAD"), skip = setOf("DPAD", "MENU"), anyHeight = true)
        .dpadInLineWithTheStick().withStart(this).nubAsDrawn(this)
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
 * MENU's companion where following [menu] left it beside MENU in the left group (the Dreamcast's stick / d-pad swap, the
 * FDS's DISK): on a board whose START / SELECT are a column (the fold board), stacked over MENU as that column is
 * spaced, a two-button column, in the menu's well where the two shared a wide one. As it is elsewhere (side by side on
 * a short landscape, as the menu's START / SELECT are).
 */
private fun SkinLayoutSpec.swapOverMenu(menu: SkinLayoutSpec): SkinLayoutSpec {
    val m = controls.firstOrNull { it.id == "MENU" && it.anchor == PieceAnchor.LEFT } ?: return this
    val swaps = controls.filter { it.id in MENU_COMPANIONS && it.anchor == PieceAnchor.LEFT }
    if (swaps.isEmpty()) return this
    val start = menu.controls.firstOrNull { it.id == "START" }; val select = menu.controls.firstOrNull { it.id == "SELECT" }
    val step = if (start != null && select != null) kotlin.math.abs(select.y - start.y) else 126f
    val s0 = swaps.first()
    val beside = kotlin.math.abs(s0.y - m.y) < 1f && kotlin.math.abs(s0.x - m.x) < 160f
    val column = start != null && select != null && kotlin.math.abs(start.x - select.x) < 1f
    // the Dreamcast's swap left on MENU's own spot (its cap moved there) stacks wherever
    val onIt = kotlin.math.abs(s0.y - m.y) < 1f && kotlin.math.abs(s0.x - m.x) < 120f && s0.id == "STICK_SWAP"
    if (!(onIt || (beside && column))) return this
    val dx = m.x - s0.x; val dy = m.y - step - s0.y
    fun inside(p: PieceSpec, c: ControlSpec) = c.anchor == p.anchor && c.x >= p.x && c.x <= p.x + p.w * p.scale && c.y >= p.y && c.y <= p.y + p.h * p.scale
    // a wide well holding the two: the menu's START / SELECT well instead, round the column
    val theirWell = if (start != null && select != null) menu.pieces.firstOrNull { it.glass == null && inside(it, start) && inside(it, select) } else null
    return copy(
        pieces = pieces.map { p ->
            val cx = p.x + p.w * p.scale / 2f; val cy = p.y + p.h * p.scale / 2f
            if (p.glass == null && p.anchor == PieceAnchor.LEFT && kotlin.math.abs(cx - s0.x) < 12f && kotlin.math.abs(cy - s0.y) < 12f)
                p.copy(x = (p.x + dx).roundToInt(), y = (p.y + dy).roundToInt())
            else if (theirWell != null && p.glass == null && p.w > p.h && inside(p, m) && inside(p, s0)) {
                val w = theirWell.w * theirWell.scale; val h = theirWell.h * theirWell.scale
                theirWell.copy(x = (m.x - w / 2f).roundToInt(), y = (m.y - step / 2f - h / 2f).roundToInt(), anchor = PieceAnchor.LEFT,
                    level = p.level, drop = p.drop, inward = p.inward)
            } else p
        },
        // their words on the column's outer side, as the menu's MENU has its own
        controls = controls.map { c ->
            val word = menu.controls.firstOrNull { it.id == "MENU" && it.anchor == PieceAnchor.LEFT } ?: m
            if (c.id in MENU_COMPANIONS && c.anchor == PieceAnchor.LEFT) c.copy(x = c.x + dx, y = c.y + dy, labelDx = word.labelDx, labelDy = word.labelDy)
            else if (c === m) c.copy(labelDx = word.labelDx, labelDy = word.labelDy) else c
        },
    )
}

/** The buttons that pair with MENU in the left group: the Dreamcast's stick / d-pad swap, the FDS's disk side. */
internal val MENU_COMPANIONS = setOf("STICK_SWAP", "DISK")


/**
 * The 3DS's C-stick nub (its ring and knob pieces) kept where [drawn] (this board before it followed the menu) has it
 * from X. Where that is over the face ring (over Y's column, the fold board's and the tall shell's), the face ring and
 * what stands under it drop by the nub's height over the ring, so the nub stands where the ring's top was: the
 * shoulders over it stay level with the left's ([SkinSolver.placePieces] sets them over the nub). As it is without a nub.
 */
internal fun SkinLayoutSpec.nubAsDrawn(drawn: SkinLayoutSpec): SkinLayoutSpec {
    val nub = controls.firstOrNull { it.id == "CSTICK" } ?: return this
    val x = controls.firstOrNull { it.id == "X" && it.anchor == nub.anchor } ?: return this
    val nub0 = drawn.controls.firstOrNull { it.id == "CSTICK" } ?: return this
    val x0 = drawn.controls.firstOrNull { it.id == "X" } ?: return this
    val dx = x.x + (nub0.x - x0.x) - nub.x; val dy = x.y + (nub0.y - x0.y) - nub.y
    fun centred(p: PieceSpec) = p.glass == null && p.anchor == nub.anchor &&
        kotlin.math.abs(p.x + p.w * p.scale / 2f - nub.x) < 2f && kotlin.math.abs(p.y + p.h * p.scale / 2f - nub.y) < 2f
    fun inside(p: PieceSpec, c: ControlSpec) = c.anchor == p.anchor && c.x >= p.x && c.x <= p.x + p.w * p.scale && c.y >= p.y && c.y <= p.y + p.h * p.scale
    val moved = copy(
        pieces = pieces.map { if (centred(it)) it.copy(x = (it.x + dx).roundToInt(), y = (it.y + dy).roundToInt(), level = x.level, drop = x.drop) else it },
        controls = controls.map { if (it === nub) it.copy(x = it.x + dx, y = it.y + dy, level = x.level, drop = x.drop) else it },
    )
    val n = moved.controls.first { it.id == "CSTICK" }
    val ring = moved.pieces.firstOrNull { it.glass == null && inside(it, x) } ?: return moved
    val nubTop = moved.pieces.filter { p -> p.glass == null && p.anchor == n.anchor &&
        kotlin.math.abs(p.x + p.w * p.scale / 2f - n.x) < 2f && kotlin.math.abs(p.y + p.h * p.scale / 2f - n.y) < 2f }.minOfOrNull { it.y } ?: return moved
    val nubBottom = n.y + (n.y - nubTop)
    if (nubBottom > ring.y) return moved   // in the ring's corner (a phone's): the shoulders clear it already
    val down = ring.y - nubTop
    val shoulders = shoulderIds() + companionIds()
    fun stays(p: PieceSpec) = p.glass != null || p.anchor != n.anchor || controls.any { it.id in shoulders && inside(p, it) }
    return moved.copy(
        pieces = moved.pieces.map { if (stays(it)) it else it.copy(y = it.y + down) },
        controls = moved.controls.map { if (it.anchor == n.anchor && it.id !in shoulders) it.copy(y = it.y + down) else it },
    )
}

/** The d-pad (its ring) moved across in line with the stick on the same side: its height under it is the solver's. */
private fun SkinLayoutSpec.dpadInLineWithTheStick(): SkinLayoutSpec {
    val stick = controls.firstOrNull { it.id == "STICK" } ?: return this
    val pad = controls.firstOrNull { it.id == "DPAD" }?.takeIf { it.anchor == stick.anchor } ?: return this
    val dx = (stick.x - pad.x).roundToInt()
    if (dx == 0) return this
    fun centred(p: PieceSpec) = p.glass == null && p.anchor == pad.anchor &&
        kotlin.math.abs(p.x + p.w * p.scale / 2f - pad.x) < 2f && kotlin.math.abs(p.y + p.h * p.scale / 2f - pad.y) < 2f
    return copy(pieces = pieces.map { if (centred(it)) it.copy(x = it.x + dx) else it }, controls = controls.map { if (it === pad) it.copy(x = it.x + dx) else it })
}

/**
 * The caps drawn in START's column under it on [drawn] (the 3DS's SWAP on its tall shell) moved as START moved, so the
 * column stays one.
 */
internal fun SkinLayoutSpec.withStart(drawn: SkinLayoutSpec): SkinLayoutSpec {
    val s0 = drawn.controls.firstOrNull { it.id == "START" } ?: return this
    val s1 = controls.firstOrNull { it.id == "START" } ?: return this
    val dx = s1.x - s0.x; val dy = s1.y - s0.y
    if (dx == 0f && dy == 0f) return this
    val ids = drawn.controls.filter { it.id != "START" && it.id != "SELECT" && it.anchor == s0.anchor && it.anchor != PieceAnchor.CENTRE &&
        kotlin.math.abs(it.x - s0.x) < 1f && it.y > s0.y }.map { it.id }.toSet()
    if (ids.isEmpty()) return this
    val caps = controls.filter { it.id in ids }
    fun centred(p: PieceSpec, c: ControlSpec) = p.glass == null && p.anchor == c.anchor &&
        kotlin.math.abs(p.x + p.w * p.scale / 2f - c.x) < 2f && kotlin.math.abs(p.y + p.h * p.scale / 2f - c.y) < 2f
    return copy(
        pieces = pieces.map { p -> if (caps.any { centred(p, it) }) p.copy(x = (p.x + dx).roundToInt(), y = (p.y + dy).roundToInt(), anchor = s1.anchor ?: p.anchor, level = s1.level, drop = s1.drop) else p },
        controls = controls.map { c -> if (c.id in ids) c.copy(x = c.x + dx, y = c.y + dy, anchor = s1.anchor, level = s1.level, drop = s1.drop) else c },
    )
}

/**
 * The 3DS board's small caps in the row under the touch screen, three wells [gap] apart (picture px), centred where the
 * row was: MIC / SWAP, MENU alone, SELECT / START. Each well is the row's own pill cut to its caps (its two ends, its
 * middle long enough for the pitch between two caps), so they look alike; START / SELECT's own well goes. As it is
 * without that row.
 */
internal fun SkinLayoutSpec.capsInTheRow(gap: Float): SkinLayoutSpec {
    fun at(id: String) = controls.firstOrNull { it.id == id }
    val mic = at("MIC") ?: return this; val swap = at("SWAP") ?: return this; val menu = at("MENU") ?: return this
    val start = at("START") ?: return this; val select = at("SELECT") ?: return this
    if (listOf(mic, swap, menu).any { it.anchor != PieceAnchor.CENTRE }) return this
    fun inside(p: PieceSpec, c: ControlSpec) = p.anchor == c.anchor && c.x >= p.x && c.x <= p.x + p.w * p.scale && c.y >= p.y && c.y <= p.y + p.h * p.scale
    fun centred(p: PieceSpec, c: ControlSpec) = p.glass == null && p.anchor == c.anchor &&
        kotlin.math.abs(p.x + p.w * p.scale / 2f - c.x) < 2f && kotlin.math.abs(p.y + p.h * p.scale / 2f - c.y) < 2f
    val caps = listOf(mic, swap, menu, select, start)
    // the wells: the pieces holding two caps each (a cap's own piece holds one)
    val well = pieces.firstOrNull { p -> p.glass == null && inside(p, mic) && inside(p, swap) } ?: return this
    val startWell = pieces.firstOrNull { p -> p.glass == null && inside(p, start) && inside(p, select) }
    val pitch = kotlin.math.abs(swap.x - menu.x)
    fun pill(left: Float, n: Int) = pillWell(well, left, n, pitch)
    val two = (well.h + pitch / well.scale) * well.scale; val one = well.h * well.scale
    val total = two + gap + one + gap + two
    val left = menu.x - total / 2f
    val wells = pill(left, 2) + pill(left + two + gap, 1) + pill(left + two + gap + one + gap, 2)
    // each cap's middle: a pair's caps the pitch apart in their well
    val pad = one / 2f
    val xs = mapOf(
        "MIC" to left + pad, "SWAP" to left + pad + pitch, "MENU" to left + two + gap + pad,
        "SELECT" to left + two + gap + one + gap + pad, "START" to left + two + gap + one + gap + pad + pitch,
    )
    val moved = caps.associateBy { it.id }
    val capPiece = pieces.first { centred(it, mic) }
    return copy(
        // the wells before the first of the caps and the row's well: drawn under them
        pieces = pieces.filter { it !== startWell }.let { ps -> ps.indexOfFirst { p -> p === well || (p !== well && caps.any { centred(p, it) }) }.let { i -> ps.take(i) + wells + ps.drop(i) } }.flatMap { p ->
            if (p === well) return@flatMap emptyList()
            if (wells.any { it === p }) return@flatMap listOf(p)
            val c = caps.firstOrNull { centred(p, it) } ?: return@flatMap listOf(p)
            val nx = xs.getValue(c.id)
            listOf(p.copy(x = (nx - p.w * p.scale / 2f).roundToInt(), y = (mic.y - p.h * p.scale / 2f).roundToInt(), anchor = PieceAnchor.CENTRE,
                inward = capPiece.inward, level = capPiece.level, rise = capPiece.rise, drop = capPiece.drop))
        },
        controls = controls.map { c ->
            if (c.id !in moved) c
            else c.copy(x = xs.getValue(c.id), y = mic.y, anchor = PieceAnchor.CENTRE, inward = mic.inward, level = mic.level, rise = mic.rise, drop = mic.drop,
                labelDy = mic.labelDy, labelDx = 0f, labelH = mic.labelH)
        },
    )
}

/**
 * The regular 3DS's board [this] given the New 3DS's controls from [new] (its own board at the same size), as [new]'s
 * layout (its id and variant): ZL / ZR (the row places them beside L / R) and the C-stick drawn as the stick (its ring
 * and knob at the Circle Pad's size), under the face buttons [gap] picture px from their ring, in line with them (moved
 * across once placed). As it is where either lacks what that needs.
 */
internal fun SkinLayoutSpec.withNewControls(new: SkinLayoutSpec, gap: Float): SkinLayoutSpec {
    fun inside(p: PieceSpec, c: ControlSpec) = p.anchor == c.anchor && c.x >= p.x && c.x <= p.x + p.w * p.scale && c.y >= p.y && c.y <= p.y + p.h * p.scale
    fun centredAt(p: PieceSpec, side: PieceAnchor?, x: Float, y: Float) = p.glass == null && p.anchor == side &&
        kotlin.math.abs(p.x + p.w * p.scale / 2f - x) < 2f && kotlin.math.abs(p.y + p.h * p.scale / 2f - y) < 2f
    val zs = new.controls.filter { it.id == "L2" || it.id == "R2" }
    val zPieces = new.pieces.filter { p -> p.glass == null && zs.any { inside(p, it) } }
    val stick = controls.firstOrNull { it.id == "STICK" } ?: return this
    val x = controls.firstOrNull { it.id == "X" } ?: return this
    val b = controls.firstOrNull { it.id == "B" } ?: return this
    if (new.controls.none { it.id == "CSTICK" }) return this
    val stickPieces = pieces.filter { centredAt(it, stick.anchor, stick.x, stick.y) }
    val fx = x.x; val fy = (x.y + b.y) / 2f
    val face = pieces.filter { centredAt(it, x.anchor, fx, fy) }.maxByOrNull { it.w * it.scale } ?: return this
    if (stickPieces.isEmpty()) return this
    val ring = stickPieces.maxOf { it.w * it.scale }
    val ny = fy + face.h * face.scale / 2f + gap + ring / 2f
    val nubPieces = stickPieces.map { p ->
        p.copy(x = (fx - p.w * p.scale / 2f).roundToInt(), y = (ny - p.h * p.scale / 2f).roundToInt(), anchor = face.anchor, inward = face.inward,
            level = face.level, rise = face.rise, drop = face.drop, knobOf = if (p.knob) "CSTICK" else p.knobOf)
    }
    val nub = stick.copy(id = "CSTICK", x = fx, y = ny, anchor = x.anchor, inward = x.inward, level = x.level, rise = x.rise, drop = x.drop)
    return copy(id = new.id, variant = new.variant, pieces = pieces + zPieces + nubPieces, controls = controls + zs + nub)
}

/**
 * The phone's 3DS board with SELECT / START in the row under the pad, three wells [gap] picture px apart (the stick /
 * d-pad swap and MIC, MENU alone, SELECT and START; centred where the row was), and SWAP where they stood, between the
 * shoulders in a well of its own (nearest the screens it swaps); each well the row's pill cut to its caps, their caps the row's size ([sized]'s, the fold board's, where it has them), their
 * words under them as the row's; their own well goes. As it is without that row.
 */
internal fun SkinLayoutSpec.startInTheRow(gap: Float, sized: SkinLayoutSpec?): SkinLayoutSpec {
    fun at(id: String) = controls.firstOrNull { it.id == id }
    val mic = at("MIC") ?: return this; val swap = at("SWAP") ?: return this
    val start = at("START") ?: return this; val select = at("SELECT") ?: return this
    if (mic.anchor != PieceAnchor.CENTRE) return this
    fun inside(p: PieceSpec, c: ControlSpec) = p.anchor == c.anchor && c.x >= p.x && c.x <= p.x + p.w * p.scale && c.y >= p.y && c.y <= p.y + p.h * p.scale
    fun centredAt(p: PieceSpec, x: Float, y: Float) = p.glass == null && kotlin.math.abs(p.x + p.w * p.scale / 2f - x) < 2f && kotlin.math.abs(p.y + p.h * p.scale / 2f - y) < 2f
    val well = pieces.firstOrNull { p -> p.glass == null && inside(p, mic) && inside(p, swap) } ?: return this
    val startWell = pieces.firstOrNull { p -> p.glass == null && inside(p, start) && inside(p, select) }
    val pitch = kotlin.math.abs(swap.x - mic.x)
    // the row's caps and words: the centre controls at its height inside its well (a swapped pair on one spot counts once)
    val rowCaps = controls.filter { it.anchor == PieceAnchor.CENTRE && kotlin.math.abs(it.y - mic.y) < 1f && inside(well, it) }
    if (rowCaps.none { it.id == "SWAP" }) return this
    val groups = listOf(listOf("STICK_SWAP", "MIC"), listOf("MENU"), listOf("SELECT", "START"))
    if (groups.flatten().any { id -> id != "SELECT" && id != "START" && rowCaps.none { it.id == id } }) return this
    val pad = well.h * well.scale / 2f
    fun width(g: List<String>) = 2f * pad + (g.size - 1) * pitch
    val total = groups.sumOf { width(it).toDouble() }.toFloat() + gap * (groups.size - 1)
    val lefts = groups.runningFold(well.x + well.w * well.scale / 2f - total / 2f) { x, g -> x + width(g) + gap }.dropLast(1)
    // SWAP where SELECT / START stood, between them
    val topX = (select.x + start.x) / 2f; val topY = start.y
    val xs = groups.zip(lefts).flatMap { (g, x) -> g.mapIndexed { i, id -> id to x + pad + i * pitch } }.toMap() + ("SWAP" to topX)
    val ys = mapOf("SWAP" to topY)
    fun sizedCap(id: String): Pair<PieceSpec, ControlSpec>? {
        val c = sized?.controls?.firstOrNull { it.id == id } ?: return null
        return sized.pieces.firstOrNull { centredAt(it, c.x, c.y) }?.let { it to c }
    }
    val capPiece = pieces.first { centredAt(it, mic.x, mic.y) }
    val moved = listOf(select, start)
    val caps = moved.mapNotNull { c ->
        val own = pieces.firstOrNull { centredAt(it, c.x, c.y) } ?: return@mapNotNull null
        val src = sizedCap(c.id)?.first ?: own
        val nx = xs.getValue(c.id)
        src.copy(x = (nx - src.w * src.scale / 2f).roundToInt(), y = (mic.y - src.h * src.scale / 2f).roundToInt(), anchor = PieceAnchor.CENTRE,
            inward = capPiece.inward, level = capPiece.level, rise = capPiece.rise, drop = capPiece.drop, alt = null)
    }
    val ownCaps = moved.mapNotNull { c -> pieces.firstOrNull { centredAt(it, c.x, c.y) } }
    return copy(
        pieces = pieces.filter { it !== startWell && ownCaps.none { o -> o === it } }.flatMap { p -> when {
            p === well -> groups.zip(lefts).flatMap { (g, x) -> pillWell(well, x, g.size, pitch) } + pillWell(well, topX - pad, 1, pitch).map { it.copy(y = (topY - pad).roundToInt()) }
            else -> rowCaps.firstOrNull { centredAt(p, it.x, it.y) }?.let { c ->
                listOf(p.copy(x = (p.x + xs.getValue(c.id) - c.x).roundToInt(), y = (p.y + (ys[c.id] ?: c.y) - c.y).roundToInt()))
            } ?: listOf(p)
        } } + caps,
        controls = controls.map { c -> when {
            c in rowCaps -> c.copy(x = xs.getValue(c.id), y = ys[c.id] ?: c.y)
            c in moved -> c.copy(x = xs.getValue(c.id), y = mic.y, anchor = PieceAnchor.CENTRE, inward = mic.inward, level = mic.level, rise = mic.rise, drop = mic.drop,
                labelDy = mic.labelDy, labelDx = 0f, labelH = mic.labelH, capR = sizedCap(c.id)?.second?.capR ?: c.capR)
            else -> c
        } },
    )
}

/** [well] (a pill) cut to [n] caps [pitch] apart from [left] (picture px): its two ends and its flat middle between them. */
private fun pillWell(well: PieceSpec, left: Float, n: Int, pitch: Float): List<PieceSpec> {
    val end = well.h / 2
    val mid = ((n - 1) * pitch / well.scale).roundToInt()
    val a = well.copy(w = end, x = left.roundToInt())
    val m = well.copy(sx = well.sx + (well.w - mid) / 2, w = mid, x = (left + end * well.scale).roundToInt())
    val b = well.copy(sx = well.sx + well.w - end, w = end, x = (left + (end + mid) * well.scale).roundToInt())
    return if (mid > 0) listOf(a, m, b) else listOf(a, b)
}
