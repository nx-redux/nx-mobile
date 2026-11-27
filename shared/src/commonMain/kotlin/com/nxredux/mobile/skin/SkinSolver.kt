package com.nxredux.mobile.skin

import com.nxredux.mobile.options.PxRect
import kotlin.math.abs

/** One slice of a skin picture: [src] in the picture's px drawn at [dst]; [tileX] / [tileY]: repeated along that axis. */
data class SkinCell(val src: PxRect, val dst: PxRect, val tileX: Boolean, val tileY: Boolean)

/** One draw of a cell: [src] (picture px, whole) at [dst] (window px), mirrored across where [flipX] / [flipY]. */
data class SkinTile(val src: PxRect, val dst: PxRect, val flipX: Boolean, val flipY: Boolean)

/**
 * The draws for [c] at [k] window px per picture px. A fixed slice, or a repeating one at most [stretchMax] picture px
 * across, is one draw (stretched). A wider repeating slice is laid as an odd number of tiles, every other one mirrored,
 * so each tile meets the next on the same edge and the run starts and ends as the picture does on either side (no seam);
 * the tiles share the length alike, the count picked so each stays nearest the picture's own size (the board's grain
 * kept, never smeared into lines).
 */
fun skinTiles(c: SkinCell, k: Float, stretchMax: Float): List<SkinTile> {
    if (c.src.width <= 0f || c.src.height <= 0f || c.dst.width <= 0f || c.dst.height <= 0f) return emptyList()
    fun counts(tile: Boolean, src: Float, dst: Float): Int {
        if (!tile || src <= stretchMax) return 1
        val n = dst / (src * k)
        // odd only: 1, 3, 5... the nearest to n by the tile's scale (above or below its size)
        var best = 1
        var i = 3
        while (i <= n * 2 + 1) {
            if (abs(kotlin.math.ln(n / i)) < abs(kotlin.math.ln(n / best))) best = i
            i += 2
        }
        return best
    }
    val nx = counts(c.tileX, c.src.width, c.dst.width)
    val ny = counts(c.tileY, c.src.height, c.dst.height)
    val w = c.dst.width / nx
    val h = c.dst.height / ny
    return buildList {
        for (j in 0 until ny) for (i in 0 until nx) {
            add(SkinTile(c.src, PxRect(c.dst.x + i * w, c.dst.y + j * h, w, h), flipX = i % 2 == 1, flipY = j % 2 == 1))
        }
    }
}

/**
 * How a board spaces its side groups and the screen between them across the window, picture px: [edge] from each
 * window edge to its group and [gap] from each group to the screen where there is room (the screen's frame taking the
 * rest); where there is not, the screen narrows first, its glass to [frameMin] (null: a touch screen's 4:3 picture at
 * its height), then the edge gaps come down toward [gap], then all four shrink alike to [minGap] ([share]). [pairGap]:
 * between a shoulder and its companion beside it (the arcade's TB / TA).
 */
data class RowSpacing(val edge: Float, val gap: Float, val minGap: Float, val frameMin: Float?, val pairGap: Float = 0f) {
    /**
     * The edge gap, the inner gap and the screen's frame width across [room] (the window less the two groups) with a frame
     * no narrower than [frameMin]: the full gaps, the frame the rest; else the frame at its least and the edge gaps down
     * toward the inner one; else all four alike down to [minGap]; null where even that does not fit.
     */
    fun share(room: Float, frameMin: Float): Triple<Float, Float, Float>? = when {
        room - 2f * edge - 2f * gap >= frameMin -> Triple(edge, gap, room - 2f * edge - 2f * gap)
        room - frameMin >= 4f * gap -> Triple((room - frameMin - 2f * gap) / 2f, gap, frameMin)
        room - frameMin >= 4f * minGap -> ((room - frameMin) / 4f).let { Triple(it, it, frameMin) }
        else -> null
    }
}

/** [c] sits on this piece: on its anchor, its centre within the piece's box. */
private fun PieceSpec.holds(c: ControlSpec): Boolean =
    c.anchor == anchor && c.x >= x && c.x <= x + w * scale && c.y >= y && c.y <= y + h * scale

/** The side controls a [RowSpacing] places: the d-pad / stick and face buttons, L / R, the small button columns. */
internal val ROW_IDS = setOf("DPAD", "STICK", "X", "Y", "A", "B", "L", "R", "START", "SELECT", "MENU", "MIC", "SWAP", "STICK_SWAP", "DISK", "TURBO_A", "TURBO_B")

/** A board's shoulders: L / R, or where it has neither but both L2 and R2 (the Dreamcast's triggers) those in their place. */
internal fun SkinLayoutSpec.shoulderIds(): Set<String> =
    if (controls.none { it.id == "L" || it.id == "R" } && controls.any { it.id == "L2" } && controls.any { it.id == "R2" }) setOf("L2", "R2") else setOf("L", "R")

/** The 3DS's layout sets, the New 3DS's and the regular one's. */
internal val N3DS_VARIANTS = setOf(SkinVariant.N3DS, SkinVariant.N3DS_REGULAR)

/**
 * The layout sets drawn on the 3DS's board: the 3DS's and the Wii U GamePad's (the same rings with the d-pad under the
 * stick, ZL / ZR beside L / R, the right stick over the face buttons).
 */
internal val N3DS_BOARD = N3DS_VARIANTS + SkinVariant.WIIU

/**
 * A board's shoulders' companions, each beside its shoulder on the inner side: the arcade's TB / TA, the GBA's L2 / R2
 * and the 3DS's and the GamePad's ZL / ZR (small caps beside L / R; another console's L2 / R2 keep their own design).
 */
internal fun SkinLayoutSpec.companionIds(): Set<String> =
    setOf("TURBO_A", "TURBO_B") + (if ((variant != SkinVariant.GBA && variant !in N3DS_BOARD) || "L2" in shoulderIds()) emptySet()
        else setOf("L2", "R2").filter { id -> controls.any { it.id == id } })

/**
 * The side controls a [RowSpacing] places on this board: [ROW_IDS], its [shoulderIds] and their [companionIds] (the
 * 3DS's C-stick nub and the GamePad's right stick too, over the face buttons, moving with them).
 */
internal fun SkinLayoutSpec.rowIds(): Set<String> = ROW_IDS + shoulderIds() + companionIds() + (if (variant in N3DS_BOARD) setOf("CSTICK") else emptySet())

/**
 * A taller window's twin showing the stick and the d-pad apart, each its own ring (the Dreamcast's "both" boards): its
 * own design, the row rules leave it alone.
 */
internal fun SkinLayoutSpec.tallTwinApart(): Boolean {
    // the Dreamcast's, the 3DS's and the GamePad's (both shown on every fold / tablet board) take the row too
    if (minRefH <= 0 || variant == SkinVariant.DC || variant in N3DS_BOARD) return false
    fun holdsOnly(p: PieceSpec, id: String) = controls.filter { c -> p.holds(c) }
        .map { it.id }.toSet() == setOf(id)
    return pieces.any { holdsOnly(it, "STICK") } && pieces.any { holdsOnly(it, "DPAD") }
}

/**
 * A piece of a layout built from pieces: [src] in its parts picture drawn at [dst] (scaled when the sizes differ).
 * [strip]: one of the frame strip's shortcut labels (hidden with the in-game menu open). [piece]: the layout's piece it
 * draws (an index into [SkinLayoutSpec.pieces]); -1: the frame or a strip label.
 */
data class SkinPiece(val src: PxRect, val dst: PxRect, val strip: Boolean = false, val alt: String? = null, val knob: Boolean = false, val knobOf: String = "STICK", val piece: Int = -1,
    /** 0 or more: no art, [dst] filled black with corners this round, the screens cut out (a frame's opening where no screen is). */
    val fill: Float = -1f)

/** A control's hit area placed in the window: its [spec] (id, shape, sizes scaled) at [x], [y]. */
data class PlacedControl(val spec: ControlSpec, val x: Float, val y: Float)

/** How far a round control takes touches from its middle, window px (a stick's ring is [TouchSlop.STICK] wider); 0 for a rect. */
internal fun PlacedControl.touchReach(): Float = when (spec.shape) {
    ControlShape.RECT -> 0f
    ControlShape.STICK -> spec.r * TouchSlop.STICK
    else -> spec.r
}

/** The half width and height of the box a control takes touches in: [touchReach] both ways, else a rect's own. */
internal fun PlacedControl.touchHalf(): Pair<Float, Float> = touchReach().let { r -> if (r > 0f) r to r else spec.hw to spec.hh }

/** A glass placed in the window: [rect] showing [screen] at [scale] window px per game px; [corner]: its corners' radius, window px. */
data class PlacedGlass(val id: String, val screen: GlassScreen, val rect: PxRect, val scale: Float, val corner: Float = 0f)

/**
 * A skin layout assembled for one window: the [cells] to draw, the [glasses] where the game's screens go and the
 * [controls] to hit-test, all in window px. [k] is window px per picture px. A layout built from pieces also draws its
 * [pieces] (the screen frame first, then the buttons) from its parts picture over the cells.
 */
data class SkinPlacement(
    val spec: SkinLayoutSpec,
    val k: Float,
    val cells: List<SkinCell>,
    val glasses: List<PlacedGlass>,
    val controls: List<PlacedControl>,
    val pieces: List<SkinPiece> = emptyList(),
    /** A fold board drawn without its hinge (the device not half-folded): its lid and body one slab ([SkinSeam]). */
    val slab: Boolean = false,
    /** Window px the fold board's side groups moved in from the window's edges (a window wider than its render). */
    val sideIn: Float = 0f,
    /** Its controls from the layout's boxes ([SkinBoxes]): as the pack draws them, no rule moves them. */
    val boxed: Boolean = false,
) {
    fun glass(screen: GlassScreen): PlacedGlass? = glasses.firstOrNull { it.screen == screen }

    /**
     * The controls and pieces shown with the stick ([useStick]) or the d-pad: a layout that swaps them carries both, each
     * marked `alt` "stick" / "dpad"; unmarked ones always show.
     */
    fun forStick(useStick: Boolean): SkinPlacement {
        val drop = if (useStick) "dpad" else "stick"
        if (controls.none { it.spec.alt == drop } && pieces.none { it.alt == drop }) return this
        return copy(controls = controls.filter { it.spec.alt != drop }, pieces = pieces.filter { it.alt != drop })
    }
}

/**
 * Assembles a [SkinLayoutSpec] to a window: the screens get their size first (the first [SkinLayoutSpec.scales] entry
 * that fits; a 0 entry is solved for the biggest size that fits), then every split shares out the difference between the
 * picture and the window: glass slices take their glass's growth, repeat slices the rest, fixed slices nothing. A layout
 * that would need a slice shorter than nothing does not fit (null).
 */
object SkinSolver {
    /** DS screen size, game px. */
    const val DS_W = 256f
    const val DS_H = 192f

    /**
     * [spec] in a [windowW] x [windowH] window at [k] window px per picture px. [picture]: a one-screen console's picture
     * size in game px (its aspect sizes a [GlassScreen.PICTURE] glass). [hingeY]: the fold's middle in window px while
     * half-folded (a `hinge` slice lands on it), else null.
     */
    fun place(spec: SkinLayoutSpec, windowW: Float, windowH: Float, k: Float, picture: Pair<Float, Float>? = null, hingeY: Float? = null): SkinPlacement? {
        if (k <= 0f || windowW <= 0f || windowH <= 0f) return null
        val game = spec.glasses.associate { g -> g.id to gameSize(g.screen, picture) }
        if (game.values.any { it == null }) return null
        for (tuple in spec.scales) {
            if (tuple.size != spec.glasses.size) continue
            val fillAt = tuple.indices.filter { tuple[it] <= 0f }
            fun attempt(fill: Float): Solved {
                val scales = tuple.mapIndexed { i, s -> if (i in fillAt) fill else s }
                return solve(spec, windowW, windowH, k, scales, game.mapValues { it.value!! }, hingeY)
            }
            if (fillAt.isEmpty()) { attempt(0f).placement?.let { return it }; continue }
            // the fill glass: the biggest scale with no slice short of nothing. A smaller glass may leave room no slice
            // can take (a glass row with no repeat), so that one is the answer only if it then fits exactly.
            var lo = 0.25f
            var hi = 64f
            if (attempt(lo).tooBig) continue
            repeat(30) {
                val mid = (lo + hi) / 2f
                if (!attempt(mid).tooBig) lo = mid else hi = mid
            }
            attempt(lo).placement?.let { return it }
        }
        return null
    }

    /**
     * A layout built from pieces ([SkinLayoutSpec.frame]) in a [windowW] x [windowH] window at [k]: the background's
     * strips stretched to the window, the buttons at their own size kept to the bottom and their side (or the middle),
     * and the screen as big as fits above them in its shape (the DS screens', [picture]'s, else the frame glass's own), the frame
     * round it at its own border, centred across and kept down against the pieces under it (any height to spare goes above
     * it); or, for a filling frame, the frame over all that room and the screen all of it
     * inside the border (its scale: the picture's as big as fits in it). Null when the window is smaller than the background or leaves no room.
     * [topInset]: window px at the top the screen keeps clear of (a camera cutout, the status bar); the background still
     * runs under it, and the frame starts right below it (the inset is its top margin, in place of the render's own).
     * [hingeY]: the fold's middle in window px while half-folded, for a frame made for it ([FrameSpec.hinge]): the frame's
     * room ends above the fold, and every piece must lie below it (else null). [hingeGap]: picture px the frame's room
     * keeps above the fold in place of the frame's own [FrameSpec.hinge] (0 on a board drawn without its hinge: to the middle).
     * [capPad]: on a fold board, a filling frame showing [topScreen] no taller than that screen across its whole width plus
     * this many picture px above and below (the 3DS's wide screen: no tall bars, the frame clear of what is under it), kept at the top.
     * [capLift]: the touch screen then moves up by the height the cap saves, under the frame as the room's bottom was, and
     * the controls are centred in the height between the frame and the window's bottom (only with no hinge between them).
     * [sideInset]: on a fold board in a window wider than its render, how far (picture px) the side groups move in toward
     * the screen between them at most; that screen's frame (not its picture) widens up to as far from them, where there is room.
     * [bottomMargin]: picture px a screen frame with nothing under it keeps from the window's bottom as it grows down
     * (the pack's landscape shell's own margin; null: the top screen's frame's from the top). [bottomGlass]: in its place,
     * window px its glass (not its frame) ends above the window's bottom, so it ends where another layout's screen does.
     * [row]: on a fold board or a landscape shell, the side groups and the screen between them spaced across the window
     * by it ([RowSpacing]); on a portrait board, the groups under its screen from the window's edges;
     * in place of [sideInset]'s, on a board whose side groups hold only [ROW_IDS]' controls; null when even its least
     * spacing does not fit (a smaller board is tried).
     * [centreControls]: where the touch screen follows a capped top screen up, the side groups go up with it, centred in
     * the height under it (false: they stay where their layout puts them, a board following the main menu's).
     * [roomLeft], [roomRight], [roomBottom]: window px where control boxes ([SkinBoxes]) end the screen's room on that
     * side, in place of the frame's own margins (a fold board's: the room of the screen between its side groups).
     * [topScreen]: the DS / 3DS top screen's game size (256 x 192, 400 x 240): a filling frame showing it, on a board
     * with no fold, is cut to its shape (its border as thin above and below as at the sides), kept at the top, and the
     * pieces with a screen (the touch screen) move up by the height that saves.
     */
    fun placePieces(spec: SkinLayoutSpec, windowW: Float, windowH: Float, k: Float, picture: Pair<Float, Float>? = null, topInset: Float = 0f, hingeY: Float? = null, topScreen: Pair<Float, Float>? = null, hingeGap: Float? = null, capPad: Float? = null, capLift: Boolean = false, sideInset: Float = 0f, bottomMargin: Float? = null, bottomGlass: Float? = null, row: RowSpacing? = null, centreControls: Boolean = true,
        roomLeft: Float? = null, roomRight: Float? = null, roomBottom: Float? = null): SkinPlacement? {
        val frame = spec.frame ?: return null
        if (k <= 0f || spec.refW <= 0 || spec.refH <= 0) return null
        // the background split at the fold too: each half takes its own side's room, the board's hinge on the fold
        val bg = place(spec.copy(glasses = emptyList(), scales = listOf(emptyList()), controls = emptyList()), windowW, windowH, k, hingeY = hingeY) ?: return null
        val w = windowW / k
        val h = windowH / k
        val dx = w - spec.refW
        val dy = h - spec.refH
        fun across(a: PieceAnchor, inward: Int) = when (a) {
            PieceAnchor.LEFT -> inwardShare(inward, dx)
            PieceAnchor.CENTRE -> dx / 2f
            PieceAnchor.RIGHT -> dx - inwardShare(inward, dx)
        }
        fun down(l: PieceLevel, drop: Float = -1f) = if (drop >= 0f) dy * drop else when (l) {
            PieceLevel.TOP -> 0f
            PieceLevel.MIDDLE -> dy / 2f
            PieceLevel.BOTTOM -> dy
        }
        fun x0(p: PieceSpec) = p.x + across(p.anchor, p.inward)
        fun win(x: Float, y: Float, pw: Float, ph: Float) = PxRect(x * k, y * k, pw * k, ph * k)
        val g = frame.glass
        // the frame's border round the glass, and the room the full render gives the frame, grown or shrunk with the window
        val bl = g.x.toFloat(); val bt = g.y.toFloat(); val br = (frame.w - g.x - g.w).toFloat(); val bb = (frame.h - g.y - g.h).toFloat()
        // [row] on a board whose screen is its frame: a landscape shell's, the side groups beside it (the frame's room
        // between them, as a fold board's screen between its groups); a portrait board's, the groups under it ([frameRow])
        val frameRow: FloatArray? = if (row == null || spec.pieces.any { it.glass != null }) null else {
            fun holds(p: PieceSpec) = spec.controls.any { c ->
                c.id in spec.rowIds() && p.holds(c)
            }
            val sides = spec.pieces.filter { it.anchor != PieceAnchor.CENTRE }
            val leftG = sides.filter { it.anchor == PieceAnchor.LEFT }; val rightG = sides.filter { it.anchor == PieceAnchor.RIGHT }
            val shaped = sides.all { holds(it) } && spec.controls.filter { it.anchor == PieceAnchor.LEFT || it.anchor == PieceAnchor.RIGHT }.all { it.id in spec.rowIds() } &&
                !spec.tallTwinApart()
            if (!shaped || leftG.isEmpty() || rightG.isEmpty()) null else {
                val l0 = leftG.minOf { x0(it) }; val gl = leftG.maxOf { x0(it) + it.w * it.scale } - l0
                val r0 = rightG.minOf { x0(it) }; val gr = rightG.maxOf { x0(it) + it.w * it.scale } - r0
                val room = w - gl - gr
                if (spec.family == SkinFamily.GRIP) {
                    // the gaps round the screen, as on a fold board ([rowPlan]); its frame's border round the glass kept
                    val (edge, gap, fw) = row.share(room, (row.frameMin ?: 0f) + bl + br) ?: return null
                    floatArrayOf(edge - l0, edge + gl + gap + fw + gap - r0, edge + gl + gap, fw, gap, edge)
                } else {
                    // the groups under the screen, nothing between them: [RowSpacing.edge] from the edges where they fit
                    val edge = minOf(row.edge, maxOf(row.minGap, room / 2f))
                    floatArrayOf(edge - l0, (w - edge - gr) - r0, edge + gl, w - 2f * edge - gl - gr, row.gap, edge)
                }
            }
        }
        val gripRow = frameRow?.takeIf { spec.family == SkinFamily.GRIP }
        // the screen between a fold board's side groups takes the boxes' room; the frame above keeps its own
        val boxesBeside = spec.pieces.any { it.glass != null && it.anchor == PieceAnchor.CENTRE } && spec.tree.parts.any { it.isHinge }
        val left = roomLeft?.takeUnless { boxesBeside }?.let { it / k } ?: gripRow?.get(2) ?: if (frame.side >= 0) frame.side.toFloat() else frame.x.toFloat()
        val right = roomRight?.takeUnless { boxesBeside }?.let { w - it / k } ?: gripRow?.let { w - it[2] - it[3] } ?: if (frame.side >= 0) frame.side.toFloat() else (spec.refW - frame.x - frame.w).toFloat()
        val ownTop = if (frame.top >= 0) frame.top.toFloat() else frame.y.toFloat()
        val roomTop = if (topInset > 0f) topInset / k else ownTop   // the inset is the frame's top margin
        val roomW = w - left - right
        val folded = hingeY != null && frame.hinge >= 0
        val gap = hingeGap ?: frame.hinge.toFloat()
        val roomH = if (folded) hingeY!! / k - gap - roomTop
            else if (roomBottom != null && !boxesBeside) roomBottom / k - roomTop
            else h - roomTop - (if (frame.bottom >= 0) frame.bottom.toFloat() else (spec.refH - frame.y - frame.h).toFloat())
        // half-folded, the pad's pieces all go below the fold
        if (folded && spec.pieces.any { it.y + down(it.level, it.drop) < hingeY!! / k + gap }) return null
        val (aw, ah) = gameSize(g.screen, picture) ?: (g.w.toFloat() to g.h.toFloat())   // the menu's: the glass's own
        val fit = minOf((roomW - bl - br) / aw, (roomH - bt - bb) / ah)
        if (fit <= 0f) return null
        // a filling frame takes the whole room; its screen is all of it inside the border. Showing the DS / 3DS top
        // screen on a board with no fold: its screen in the top picture's shape instead, the frame up at the top
        // (only where the width limits it: the picture across the whole room, height to spare under it; a window short of
        // height keeps the full-width frame, the picture between bars, as nothing below could move up)
        val tight = frame.fill && g.screen == GlassScreen.TOP && frame.hinge < 0 && topScreen != null && topScreen.first > 0f && topScreen.second > 0f &&
            (roomW - bl - br) * topScreen.second / topScreen.first < roomH - bt - bb
        val tw = if (tight) roomW - bl - br else 0f
        val gw = if (tight) tw else if (frame.fill) roomW - bl - br else aw * fit
        val capped = !tight && capPad != null && frame.fill && g.screen == GlassScreen.TOP && topScreen != null && topScreen.first > 0f && topScreen.second > 0f
        val gh = if (tight) tw * topScreen!!.second / topScreen.first
            else if (capped) minOf(roomH - bt - bb, gw * topScreen!!.second / topScreen.first + 2f * capPad!!)
            else if (frame.fill) roomH - bt - bb else ah * fit
        val fw = gw + bl + br
        val fh = gh + bt + bb
        val fx = left + (roomW - fw) / 2f
        // down against what is under it, the spare height above; over boxes, up at the top, the spare height between
        val fy = if (tight || capped || (roomBottom != null && !boxesBeside)) roomTop else roomTop + (roomH - fh)
        val lift = if (tight) roomH - fh else 0f   // the touch screen follows the shorter frame up
        val pieces = ArrayList<SkinPiece>()
        // the frame: corners as drawn, straight edges stretched from the 2 px slice at their middle (the glass, a hole, skipped).
        // Each drawn border reaches past the glass's edge by its corner radius, so the hole's rounded corners are drawn
        // as they are (a border thicker than the frame's corner, as under the strip, would start right at the glass and
        // stretch the corner's curve into a square: the board then shows through)
        pieces += frameSlices(frame, fx, fy, fw, fh, 1f, k) { x, y, w_, h_ -> win(x, y, w_, h_) }
        val controls = ArrayList<PlacedControl>()
        frame.strip?.let { st ->
            val share = gw / st.labels.size
            st.labels.forEachIndexed { i, l ->
                val cx = fx + bl + share * (i + 0.5f)
                val cy = fy + fh - st.y
                // a label drawn in the picture; none (the touch slots, TOUCH_1..4): the app writes the slot's action
                if (l.w > 0 && l.h > 0) pieces += SkinPiece(PxRect(l.sx.toFloat(), l.sy.toFloat(), l.w.toFloat(), l.h.toFloat()), win(cx - l.w / 2f, cy - l.h / 2f, l.w.toFloat(), l.h.toFloat()), strip = true)
                controls += PlacedControl(ControlSpec(l.id, ControlShape.RECT, cx, cy, hw = share / 2f * k, hh = st.hh * k, labelDy = -st.wordUp * k), cx * k, cy * k)
            }
        }
        val glasses = arrayListOf(PlacedGlass(g.id, g.screen, win(fx + bl, fy + bt, gw, gh), fit * k, g.corner * k))
        val maxInward = spec.pieces.maxOfOrNull { it.inward } ?: 0
        // The screen between the side groups (the touch screen, the panel): how far its left and right edges moved out
        // from the render's, picture px. A wider window than the render's widens the gaps beside it where it does not
        // grow into them; the side groups move in by [sideInset] of that ([sideShift]) and the screen's frame takes the rest.
        // (the fold board's: its touch screen or panel between the clusters)
        val between = spec.pieces.firstOrNull { it.glass != null && it.anchor == PieceAnchor.CENTRE }
            ?.takeIf { spec.tree.parts.any { it.isHinge } }
        var betweenMoved = 0f to 0f   // set below, once [spot] can place it (its own spot needs no side shift)
        // [row]'s spacing: the left and right groups' shifts, the screen's frame's x and width, the inner gap, the edge gap
        // (picture px); null: no row
        // only a board whose side groups hold nothing but what the row places (rings, shoulders, small columns): another's
        // pieces (ZL / ZR, a C-stick, C buttons, L2 / R2) keep the side groups' own spacing
        fun holdsAny(p: PieceSpec, ids: Set<String>) = spec.controls.any { c ->
            c.id in ids && p.holds(c)
        }
        val rowShaped = spec.pieces.filter { it.glass == null && it.anchor != PieceAnchor.CENTRE }.all { holdsAny(it, spec.rowIds()) } &&
            spec.controls.filter { it.anchor == PieceAnchor.LEFT || it.anchor == PieceAnchor.RIGHT }.all { it.id in spec.rowIds() } &&
            !spec.tallTwinApart()
        val rowPlan: FloatArray? = frameRow ?: if (row == null || between == null || !rowShaped) null else {
            val left = spec.pieces.filter { it.glass == null && it.anchor == PieceAnchor.LEFT }
            val right = spec.pieces.filter { it.glass == null && it.anchor == PieceAnchor.RIGHT }
            if (left.isEmpty() || right.isEmpty()) null else {
                val l0 = left.minOf { x0(it) }; val gl = left.maxOf { x0(it) + it.w * it.scale } - l0
                val r0 = right.minOf { x0(it) }; val gr = right.maxOf { x0(it) + it.w * it.scale } - r0
                val border = (between.w - (between.glass?.w ?: between.w)) * between.scale
                // a game's touch screen: no narrower than its 4:3 picture at the glass's height
                val shape = between.glass?.takeIf { it.screen == GlassScreen.TOUCH }?.let { it.h * between.scale * 4f / 3f }
                val frameMin = (row.frameMin ?: shape ?: 0f) + border
                val room = w - gl - gr
                val (edge, gap, fw) = row.share(room, frameMin) ?: return null
                floatArrayOf(edge - l0, edge + gl + gap + fw + gap - r0, edge + gl + gap, fw, gap, edge)
            }
        }
        /** How much a side group's piece or control ([anchor] LEFT or RIGHT) gained on the render's gap to the screen between. */
        fun sideGain(anchor: PieceAnchor, inward: Int): Float {
            val (leftOut, rightOut) = betweenMoved
            // the screen's left edge moved right by -leftOut, the piece by its share less the render's (the right likewise)
            return maxOf(0f, if (anchor == PieceAnchor.LEFT) -leftOut - (across(anchor, inward) - inward) else (across(anchor, inward) + inward) - rightOut)
        }
        // [row]'s small button columns (START / SELECT, MENU beside a side group): their pieces (a well, its caps) and
        // controls, and how far each side's moves on from its group, so its inner edge sits two gaps from the screen
        val columnIds = setOf("START", "SELECT", "MENU", "MIC", "SWAP", "STICK_SWAP", "DISK")
        // (not another control's own piece over them: a New 3DS C-stick's ring under the face buttons)
        fun holdsColumn(p: PieceSpec) = p.glass == null && p.anchor != PieceAnchor.CENTRE && spec.controls.any { c ->
            c.id in columnIds && p.holds(c)
        } && spec.controls.none { c -> c.id !in columnIds && c.anchor == p.anchor &&
            kotlin.math.abs(p.x + p.w * p.scale / 2f - c.x) < 2f && kotlin.math.abs(p.y + p.h * p.scale / 2f - c.y) < 2f }
        val columnShift: Pair<Float, Float> = if (rowPlan == null) 0f to 0f else {
            val gap = rowPlan[4]
            // a side's small caps in a row (the 3DS's tall shell) stay as drawn, under their ring
            fun inRow(side: PieceAnchor) = spec.variant in N3DS_BOARD && spec.controls.filter { it.id in columnIds && it.anchor == side }.let { cs -> cs.size >= 2 && cs.all { kotlin.math.abs(it.y - cs[0].y) < 1f } }
            val left = spec.pieces.filter { it.anchor == PieceAnchor.LEFT && holdsColumn(it) }.takeUnless { inRow(PieceAnchor.LEFT) }.orEmpty()
            val right = spec.pieces.filter { it.anchor == PieceAnchor.RIGHT && holdsColumn(it) }.takeUnless { inRow(PieceAnchor.RIGHT) }.orEmpty()
            val l = if (left.isEmpty()) 0f else (rowPlan[2] - 2f * gap) - (left.maxOf { x0(it) + it.w * it.scale } + rowPlan[0])
            val r = if (right.isEmpty()) 0f else (rowPlan[2] + rowPlan[3] + 2f * gap) - (right.minOf { x0(it) } + rowPlan[1])
            l to r
        }
        fun columnShiftOf(anchor: PieceAnchor?) = when (anchor) { PieceAnchor.LEFT -> columnShift.first; PieceAnchor.RIGHT -> columnShift.second; else -> 0f }
        // [row]'s shoulders (L, R): their outer edge [RowSpacing.gap] in from their ring's outer edge (the d-pad's left, the
        // face buttons' right; both moved with their group alike, so the group shift cancels out)
        val shoulderIds = spec.shoulderIds()
        fun holdsShoulder(p: PieceSpec) = p.glass == null && p.anchor != PieceAnchor.CENTRE && spec.controls.any { c ->
            c.id in shoulderIds && p.holds(c)
        }
        val shoulderShift: Pair<Float, Float> = if (rowPlan == null) 0f to 0f else {
            val left = spec.pieces.filter { it.anchor == PieceAnchor.LEFT && holdsShoulder(it) }
            val right = spec.pieces.filter { it.anchor == PieceAnchor.RIGHT && holdsShoulder(it) }
            // the rings' outer edges (the d-pad's left, the face buttons' right, at their middle the circle's outline)
            fun ring(side: PieceAnchor, ids: Set<String>) = spec.pieces.filter { p -> p.glass == null && p.anchor == side && spec.controls.any { c ->
                c.id in ids && p.holds(c) } }
            val lRing = ring(PieceAnchor.LEFT, setOf("DPAD", "STICK")); val rRing = ring(PieceAnchor.RIGHT, setOf("X", "Y", "A", "B"))
            val inset = row?.gap ?: 0f
            val l = if (left.isEmpty() || lRing.isEmpty()) 0f else (lRing.minOf { x0(it) } + inset) - left.minOf { x0(it) }
            val r = if (right.isEmpty() || rRing.isEmpty()) 0f else (rRing.maxOf { x0(it) + it.w * it.scale } - inset) - right.maxOf { x0(it) + it.w * it.scale }
            l to r
        }
        fun shoulderShiftOf(anchor: PieceAnchor?) = when (anchor) { PieceAnchor.LEFT -> shoulderShift.first; PieceAnchor.RIGHT -> shoulderShift.second; else -> 0f }
        // the stick and the d-pad both shown, each its own ring (the Dreamcast's, the 3DS's): the d-pad under the stick, [RowSpacing.gap]
        // from it, in line. Rings found by centre: one may lie over the other's box before it moves
        fun centredOn(id: String) = spec.controls.firstOrNull { it.id == id }?.let { c ->
            spec.pieces.filter { p -> p.glass == null && p.anchor == c.anchor &&
                kotlin.math.abs(p.x + p.w * p.scale / 2f - c.x) < 2f && kotlin.math.abs(p.y + p.h * p.scale / 2f - c.y) < 2f }
        }.orEmpty()
        val padPieces = if (spec.variant == SkinVariant.DC || spec.variant in N3DS_BOARD) centredOn("DPAD") else emptyList()
        val dpadUnder: Pair<Float, Float> = run {
            if (rowPlan == null || row == null) return@run 0f to 0f
            val stick = centredOn("STICK").maxByOrNull { it.w }
            val pad = padPieces.maxByOrNull { it.w }
            if (stick == null || pad == null || stick.anchor != pad.anchor || centredOn("STICK").any { it in padPieces }) return@run 0f to 0f
            fun y0(p: PieceSpec) = p.y + down(p.level, p.drop) - minOf(lift, p.rise.toFloat())
            ((stick.x + across(stick.anchor, stick.inward)) - (pad.x + across(pad.anchor, pad.inward))) to (y0(stick) + stick.h * stick.scale + row.gap - y0(pad))
        }
        fun movesWithDpad(p: PieceSpec) = (dpadUnder.first != 0f || dpadUnder.second != 0f) && p in padPieces
        // and down: its top [RowSpacing.gap] under its side's d-pad / face buttons (the pieces holding them), or a single
        // button's middle level with the middle of the column across
        val padIds = setOf("DPAD", "STICK", "X", "Y", "A", "B")
        fun columnDrop(anchor: PieceAnchor?): Float {
            if (rowPlan == null || row == null || anchor == null || anchor == PieceAnchor.CENTRE) return 0f
            fun y0(p: PieceSpec) = p.y + down(p.level, p.drop)
            val col = spec.pieces.filter { it.anchor == anchor && holdsColumn(it) }
            // (a New 3DS C-stick under the face buttons too: the caps go under it)
            val pad = spec.pieces.filter { p -> p.glass == null && p.anchor == anchor && !holdsColumn(p) && spec.controls.any { c ->
                (c.id in padIds || c.id == "CSTICK") && p.holds(c) } }
            if (col.isEmpty() || pad.isEmpty()) return 0f
            // the d-pad where it goes under the stick
            return pad.maxOf { y0(it) + it.h * it.scale + (if (movesWithDpad(it)) dpadUnder.second else 0f) } + row.gap - col.minOf { y0(it) }
        }
        // a one-button column (the main menu's MENU) centred on the middle of the column of two or more across from it
        fun columnControls(anchor: PieceAnchor) = spec.controls.filter { it.id in columnIds && it.anchor == anchor }
        fun centred(one: PieceAnchor, other: PieceAnchor, otherDrop: Float): Float? {
            val mine = columnControls(one).singleOrNull() ?: return null
            val theirs = columnControls(other).takeIf { it.size >= 2 } ?: return null
            val middle = theirs.map { it.y + down(it.level, it.drop) }.let { (it.min() + it.max()) / 2f } + otherDrop
            return middle - (mine.y + down(mine.level, mine.drop))
        }
        val ownLeft = columnDrop(PieceAnchor.LEFT)
        val ownRight = columnDrop(PieceAnchor.RIGHT)
        val columnDropLeft = if (rowPlan == null) 0f else centred(PieceAnchor.LEFT, PieceAnchor.RIGHT, ownRight) ?: ownLeft
        val columnDropRight = if (rowPlan == null) 0f else centred(PieceAnchor.RIGHT, PieceAnchor.LEFT, ownLeft) ?: ownRight
        fun columnDropOf(anchor: PieceAnchor?) = when (anchor) { PieceAnchor.LEFT -> columnDropLeft; PieceAnchor.RIGHT -> columnDropRight; else -> 0f }
        // the shoulders' bottom [RowSpacing.gap] over their side's d-pad / face buttons, as the column is under them
        fun shoulderDrop(anchor: PieceAnchor?): Float {
            if (rowPlan == null || row == null || anchor == null || anchor == PieceAnchor.CENTRE) return 0f
            fun y0(p: PieceSpec) = p.y + down(p.level, p.drop)
            val sh = spec.pieces.filter { it.anchor == anchor && holdsShoulder(it) }
            fun holding(ids: Set<String>) = spec.pieces.filter { p -> p.glass == null && p.anchor == anchor && !holdsColumn(p) && !holdsShoulder(p) && spec.controls.any { c ->
                c.id in ids && p.holds(c) } }
            val rings = holding(padIds)
            // the 3DS's C-stick nub wholly over the face ring stands under the shoulders too (in the ring's corner it is clear)
            val pad = rings + holding(setOf("CSTICK")).filter { n -> rings.isNotEmpty() && n.y + n.h * n.scale <= rings.minOf { it.y } + 0.5f }
            if (sh.isEmpty() || pad.isEmpty()) return 0f
            return pad.minOf { y0(it) } - row.gap - sh.maxOf { y0(it) + it.h * it.scale }
        }
        val shoulderDropLeft = shoulderDrop(PieceAnchor.LEFT)
        val shoulderDropRight = shoulderDrop(PieceAnchor.RIGHT)
        fun shoulderDropOf(anchor: PieceAnchor?) = when (anchor) { PieceAnchor.LEFT -> shoulderDropLeft; PieceAnchor.RIGHT -> shoulderDropRight; else -> 0f }
        // a shoulder's companion (the arcade's TB / TA, the GBA's L2 / R2): on its shoulder's inner side, [RowSpacing.pairGap]
        // from it, level with it
        val companionIds = spec.companionIds()
        fun holdsCompanion(p: PieceSpec) = p.glass == null && p.anchor != PieceAnchor.CENTRE && spec.controls.any { c ->
            c.id in companionIds && p.holds(c)
        }
        fun companionMove(side: PieceAnchor): Pair<Float, Float> {
            if (rowPlan == null || row == null) return 0f to 0f
            val groupShift = if (side == PieceAnchor.LEFT) rowPlan[0] else rowPlan[1]   // [sideShift] under the row
            fun x0(p: PieceSpec): Float = p.x + across(p.anchor, p.inward) + groupShift
            fun y0(p: PieceSpec) = p.y + down(p.level, p.drop)
            val sh = spec.pieces.filter { it.anchor == side && holdsShoulder(it) }
            val comp = spec.pieces.filter { it.anchor == side && holdsCompanion(it) }
            if (sh.isEmpty() || comp.isEmpty()) return 0f to 0f
            val dy = (sh.minOf { y0(it) } + shoulderDropOf(side)) - comp.minOf { y0(it) }
            val dx = if (side == PieceAnchor.LEFT) (sh.maxOf { x0(it) + it.w * it.scale } + shoulderShiftOf(side) + row.pairGap) - comp.minOf { x0(it) }
                else (sh.minOf { x0(it) } + shoulderShiftOf(side) - row.pairGap) - comp.maxOf { x0(it) + it.w * it.scale }
            return dx to dy
        }
        val companionLeft = companionMove(PieceAnchor.LEFT)
        val companionRight = companionMove(PieceAnchor.RIGHT)
        fun companionOf(anchor: PieceAnchor?) = when (anchor) { PieceAnchor.LEFT -> companionLeft; PieceAnchor.RIGHT -> companionRight; else -> 0f to 0f }
        /**
         * A side group's piece or control ([anchor] LEFT or RIGHT, [inward] as placed) moved toward the screen between them
         * by [sideInset], or what its gap to that screen gained over the render's where less: never nearer the screen
         * than the render has it.
         */
        fun sideShift(anchor: PieceAnchor, inward: Int): Float {
            if (anchor == PieceAnchor.CENTRE) return 0f
            rowPlan?.let { return if (anchor == PieceAnchor.LEFT) it[0] else it[1] }
            if (between == null) return 0f
            if (sideInset <= 0f) return 0f
            // at least [MIN_SIDE_EDGE] from the window's edge (the group as placed nearer it moves in that far)
            val group = spec.pieces.filter { it.anchor == anchor && it.glass == null }
            val edge = if (group.isEmpty()) MIN_SIDE_EDGE else if (anchor == PieceAnchor.LEFT) group.minOf { it.x + across(anchor, it.inward) }
                else w - group.maxOf { it.x + across(anchor, it.inward) + it.w * it.scale }
            val by = maxOf(MIN_SIDE_EDGE - edge, minOf(sideInset, sideGain(anchor, inward)))
            return if (anchor == PieceAnchor.LEFT) by else -by
        }
        /** Piece [p]'s spot and growth, picture px: x, y, grown by gx, gy. */
        fun spot(p: PieceSpec): FloatArray {
            var px = p.x + across(p.anchor, p.inward) + sideShift(p.anchor, p.inward) +
                (if (holdsColumn(p)) columnShiftOf(p.anchor) else if (holdsShoulder(p)) shoulderShiftOf(p.anchor) else if (holdsCompanion(p)) companionOf(p.anchor).first
                else if (movesWithDpad(p)) dpadUnder.first else 0f)
            // the touch screen follows the shorter frame up; the pad (d-pad, face) up to its own rise
            var py = p.y + down(p.level, p.drop) - (if (p.glass != null) lift else minOf(lift, p.rise.toFloat())) + (if (holdsColumn(p)) columnDropOf(p.anchor) else if (holdsShoulder(p)) shoulderDropOf(p.anchor) else if (holdsCompanion(p)) companionOf(p.anchor).second else if (movesWithDpad(p)) dpadUnder.second else 0f)
            var gx = 0f; var gy = 0f   // a growing piece's growth, picture px
            val pg0 = p.glass
            if (p.grow > 0 && pg0 != null && dx > 0f) {
                // the room the side clusters leave as they spread, the glass's shape kept, up to just under the hinge
                gx = maxOf(0f, dx - 2f * maxInward)
                gy = gx * pg0.h / pg0.w
                // under a drawn hinge as far as the top screen's frame keeps above it ([FrameSpec.hinge]): the two alike
                val clear = if (hingeY != null && hingeGap == null && frame.hinge >= 0) frame.hinge.toFloat() else HINGE_CLEAR
                val ceiling = (hingeY?.let { it / k } ?: roomTop) + clear
                if (py - gy < ceiling) { gy = maxOf(0f, py - ceiling); gx = gy * pg0.w / pg0.h }
                px -= gx / 2f; py -= gy
            }
            return floatArrayOf(px, py, gx, gy)
        }
        between?.let { sp ->
            val at = spot(sp)
            betweenMoved = (sp.x - at[0]) to (at[0] + (sp.w + at[2]) * sp.scale - (sp.x + sp.w * sp.scale))
        }
        // The screen's frame widens toward each side group, up to [sideInset] from the nearest of its pieces; where it is
        // nearer than [MIN_SIDE_GAP], it narrows to that; in between it keeps its width.
        fun widen(side: PieceAnchor): Float {
            val sp = between ?: return 0f
            if (sideInset <= 0f) return 0f
            val at = spot(sp)
            val group = spec.pieces.filter { it.anchor == side && it.glass == null }
            if (group.isEmpty()) return 0f
            // the gap to the group: grown to [sideInset] where wider, cut to [MIN_SIDE_GAP] where narrower, else as it is
            val gap = if (side == PieceAnchor.LEFT) at[0] - group.maxOf { spot(it)[0] + it.w * it.scale }
                else group.minOf { spot(it)[0] } - (at[0] + (sp.w + at[2]) * sp.scale)
            return if (gap > sideInset) gap - sideInset else if (gap < MIN_SIDE_GAP) gap - MIN_SIDE_GAP else 0f
        }
        // [row]'s: the frame at the plan's x and width
        val rowAt = if (rowPlan != null && between != null) spot(between) else null
        // the boxes' room: the frame's left and right edges there
        val boxAt = if (between != null && roomLeft != null && roomRight != null) spot(between) else null
        val widenLeft = if (boxAt != null) boxAt[0] - roomLeft!! / k else if (rowAt != null) rowAt[0] - rowPlan!![2] else widen(PieceAnchor.LEFT)
        val widenRight = if (boxAt != null) (roomRight!! - roomLeft!!) / k - (between!!.w + boxAt[2]) * between.scale - widenLeft
            else if (rowAt != null) rowPlan!![3] - (between!!.w + rowAt[2]) * between.scale - widenLeft else widen(PieceAnchor.RIGHT)
        // nothing under the screen: it may grow up and down (the main menu's, its MENU with the side groups)
        val free = between?.takeIf { sideInset > 0f }?.let { sp ->
            val at = spot(sp)
            val bottom = at[1] + (sp.h + at[3]) * sp.scale
            !(spec.pieces.any { it !== sp && it.anchor == PieceAnchor.CENTRE && spot(it)[1] >= bottom - 1f } ||
                spec.controls.any { it.anchor == PieceAnchor.CENTRE && it.y + down(it.level, it.drop) > bottom })
        } ?: false
        // Under a drawn hinge, the screen's frame starts as far below it as the top screen's frame ends above it
        // ([FrameSpec.hinge]), growing up into the gap (its picture keeps its shape, centred in it).
        val raise = between?.takeIf { sideInset > 0f && hingeY != null && hingeGap == null && frame.hinge >= 0 }?.let { sp ->
            maxOf(0f, spot(sp)[1] - (hingeY!! / k + frame.hinge))
        } ?: 0f
        val tallen = between?.takeIf { sideInset > 0f }?.let { sp ->
            val at = spot(sp)
            val bottom = at[1] + (sp.h + at[3]) * sp.scale
            val under = !free
            val target = bottomGlass?.let { gm ->
                // the frame's border under its glass kept: the glass ends gm above the window's bottom
                val border = sp.glass?.let { (sp.h - it.y - it.h) * sp.scale } ?: 0f
                h - gm / k + border
            } ?: (h - (bottomMargin ?: ownTop))
            // a bottom row of boxes under it: down to the row's room
            if (roomBottom != null) maxOf(0f, roomBottom / k - bottom) else if (under) 0f else maxOf(0f, target - bottom)
        } ?: 0f
        // Under a capped frame the touch screen's frame grows up by the height the cap saves (under the frame as the
        // room's bottom was), its bottom by the row under it kept; the side groups (shoulders, clusters and their buttons) centred in the height between the frame and the
        // window's bottom, never closer to the frame than they were placed.
        val screenRise = if (capped && capLift) roomH - fh else 0f
        // the side groups only: the row under the touch screen keeps its place, as half-folded (folding never moves it)
        val own = spec.pieces.indices.filter { spec.pieces[it].glass == null && spec.pieces[it].anchor != PieceAnchor.CENTRE }
        val controlRise = if (screenRise <= 0f || own.isEmpty() || !centreControls) 0f else {
            val spots = own.associateWith { spot(spec.pieces[it]) }
            val top = own.minOf { spots.getValue(it)[1] }
            val bottom = own.maxOf { spots.getValue(it)[1] + spec.pieces[it].h * spec.pieces[it].scale }
            ((top + bottom - (fy + fh) - h) / 2f).coerceIn(0f, screenRise)
        }
        for ((pi, p) in spec.pieces.withIndex()) {
            val at = spot(p)
            // the screen between the side groups: its frame (not its picture's height) as wide as they leave room for
            val wide = if (p === between) widenLeft + widenRight else 0f
            val wideX = at[0] - (if (p === between) widenLeft else 0f); val wideGx = at[2] + wide / p.scale
            // the screen between the side groups grows up under a capped frame (its bottom kept by the row under it)
            val grown = at[3] + (if (p === between) (tallen + raise + screenRise) / p.scale else 0f)
            // the GamePad's frame hugs its 16:9 picture (no bars round it): no taller than the picture at the frame's
            // width, its top kept; no wider than the picture at that height, centred where the frame would be (a
            // tablet's, and a fold board's sideways or half-folded: the frame the other consoles' panels have, the picture
            // between bars)
            val pad = p.glass?.takeIf { p === between && spec.variant == SkinVariant.WIIU && it.screen == GlassScreen.TOUCH && !spec.id.endsWith(TABLET_BOARD) && windowW <= windowH && !(hingeY != null && hingeGap == null) }
            val gy = pad?.let { pg -> minOf(grown, (pg.w + wideGx) * 9f / 16f - pg.h) } ?: grown
            val gx = pad?.let { pg -> minOf(wideGx, (pg.h + gy) * 16f / 9f - pg.w) } ?: wideGx
            val px = wideX + (wideGx - gx) * p.scale / 2f
            val shapeW = at[2]   // the glass's own growth, its picture's shape kept
            // after its growth, which reaches up from where the piece is drawn for the window, not where it moves to
            val py = at[1] - (if (p === between) raise else 0f) - (if (p.glass != null) screenRise else if (p.anchor == PieceAnchor.CENTRE) 0f else controlRise)
            if (gx != 0f || gy != 0f || p.srcW > 0 || p.srcH > 0 || p.hole.isNotEmpty()) {   // grown, narrowed clear of the side groups, or from smaller art
                pieces += nineSlice(p, p.grow.toFloat(), p.w + gx, p.h + gy, k) { x, y, w_, h_ -> win(px + x, py + y, w_, h_) }.map { it.copy(piece = pi) }
            } else {
                pieces += SkinPiece(PxRect(p.sx.toFloat(), p.sy.toFloat(), p.w.toFloat(), p.h.toFloat()), win(px, py, p.w * p.scale, p.h * p.scale), alt = p.alt, knob = p.knob, knobOf = p.knobOf, piece = pi)
            }
            p.glass?.let { pg ->
                val gameW = gameSize(pg.screen, picture)?.first ?: (pg.w + shapeW)
                val sc = p.scale
                glasses += PlacedGlass(pg.id, pg.screen, win(px + pg.x * sc, py + pg.y * sc, (pg.w + gx) * sc, (pg.h + gy) * sc), (pg.w + shapeW) * sc * k / gameW, pg.corner * sc * k)
            }
            p.strip?.let { st ->
                // the piece's strip: an even share of its glass's width each (the whole piece's without one), under the
                // grown glass when the piece grows (its border, the strip's band, keeps its size)
                val sc = p.scale
                // across the frame's inside ([pieceStripSpan])
                val (inset, inner) = pieceStripSpan(p)
                val sx = px + inset * sc; val sw = (inner + gx) * sc
                val share = sw / st.labels.size
                st.labels.forEachIndexed { i, l ->
                    val cx = sx + share * (i + 0.5f)
                    val cy = py + (p.h + gy - st.y) * sc
                    controls += PlacedControl(ControlSpec(l.id, ControlShape.RECT, cx, cy, hw = share / 2f * k, hh = st.hh * sc * k), cx * k, cy * k)
                }
            }
        }
        controls += spec.controls.map { c ->
            PlacedControl(c.scaled(k), (c.x + across(c.anchor ?: PieceAnchor.LEFT, c.inward) + sideShift(c.anchor ?: PieceAnchor.LEFT, c.inward) + (if (c.id in columnIds) columnShiftOf(c.anchor) else if (c.id in shoulderIds) shoulderShiftOf(c.anchor) else if (c.id in companionIds) companionOf(c.anchor).first
                else if (c.id == "DPAD" && (dpadUnder.first != 0f || dpadUnder.second != 0f)) dpadUnder.first else 0f)) * k, (c.y + down(c.level, c.drop) - minOf(lift, c.rise.toFloat()) - (if (c.anchor == PieceAnchor.CENTRE) 0f else controlRise) + (if (c.id in columnIds) columnDropOf(c.anchor) else if (c.id in shoulderIds) shoulderDropOf(c.anchor) else if (c.id in companionIds) companionOf(c.anchor).second
                else if (c.id == "DPAD" && (dpadUnder.first != 0f || dpadUnder.second != 0f)) dpadUnder.second else 0f)) * k)
        }
        // how far the right-hand group moved in, window px: what sits at the window's right over the board follows it
        val sideIn = spec.pieces.filter { it.anchor == PieceAnchor.RIGHT && it.glass == null }.maxOfOrNull { -sideShift(PieceAnchor.RIGHT, it.inward) } ?: 0f
        return SkinPlacement(spec, k, bg.cells, glasses, controls, pieces, sideIn = sideIn * k)
    }

    /** The least a fold board's side group keeps from the window's edge, picture px (the DS board's own). */
    private const val MIN_SIDE_EDGE = 32f

    /** The least the screen between the side groups keeps from them, picture px (the DS board's own). */
    private const val MIN_SIDE_GAP = 6f

    /** A growing piece keeps this far (picture px) under the hinge (or the room's top). */
    private const val HINGE_CLEAR = 80f

    /** Piece [p]'s picture drawn [w] x [h] picture px: corners [c] as drawn, the edges and middle stretched. */
    private fun nineSlice(p: PieceSpec, corner: Float, w: Float, h: Float, cornerK: Float, dst: (Float, Float, Float, Float) -> PxRect): List<SkinPiece> {
        val sx = p.sx.toFloat(); val sy = p.sy.toFloat()
        // a corner the art is too small for: as much as it has, its middle kept
        val c = corner.coerceAtMost(((minOf(p.artW, p.artH) - 2) / 2f).coerceAtLeast(0f))
        val cols = listOf(Triple(sx, c, 0f to c), Triple(sx + c, p.artW - 2 * c, c to w - 2 * c), Triple(sx + p.artW - c, c, w - c to c))
        val rows = listOf(Triple(sy, c, 0f to c), Triple(sy + c, p.artH - 2 * c, c to h - 2 * c), Triple(sy + p.artH - c, c, h - c to c))
        return buildList {
            for ((ry, rh, rd) in rows) for ((cx, cw, cd) in cols) {
                if (cd.second <= 0f || rd.second <= 0f) continue
                add(SkinPiece(PxRect(cx, ry, cw, rh), dst(cd.first, rd.first, cd.second, rd.second)))
            }
            val g = p.glass
            if (g != null && p.hole.size == 4) { val (l, t, r, b) = p.hole
                if (w - l - r > 0f && h - t - b > 0f) add(SkinPiece(PxRect(0f, 0f, 0f, 0f), dst(l.toFloat(), t.toFloat(), w - l - r, h - t - b), fill = g.corner * cornerK)) }
        }
    }

    /**
     * [frame]'s art round a [fw] x [fh] frame at [fx], [fy] (frame px times [k] give the drawn size): corners as drawn,
     * straight edges stretched from the 2 px slice at their middle, the glass a hole. Each drawn border reaches past the
     * glass's edge by its corner radius, so the hole's rounded corners are drawn as they are (a border thicker than the
     * frame's corner, as under the strip, would start right at the glass and stretch the corner's curve into a square).
     * With a [FrameSpec.hole], black over the art's opening last, where no screen is.
     */
    fun frameSlices(frame: FrameSpec, fx: Float, fy: Float, fw: Float, fh: Float, k: Float, cornerK: Float = k, dst: (Float, Float, Float, Float) -> PxRect): List<SkinPiece> {
        val g = frame.glass
        val bl = g.x.toFloat(); val bt = g.y.toFloat(); val br = (frame.w - g.x - g.w).toFloat(); val bb = (frame.h - g.y - g.h).toFloat()
        val gc = g.corner.toFloat()
        // corners the art is too small for share what it has, its 2 px middle kept
        fun fit(a: Float, b: Float, art: Int) = (art - 2f).coerceAtLeast(0f).let { room -> if (a + b <= room) a to b else (a * room / (a + b)) to (b * room / (a + b)) }
        val (cl, cr) = fit(maxOf(bl + gc, frame.corner.toFloat()), maxOf(br + gc, frame.corner.toFloat()), frame.artW)
        val (ct, cb) = fit(maxOf(bt + gc, frame.corner.toFloat()), maxOf(bb + gc, frame.corner.toFloat()), frame.artH)
        val sx0 = frame.sx.toFloat(); val sy0 = frame.sy.toFloat()
        val colsSrc = listOf(sx0 to cl, sx0 + frame.artW / 2f - 1f to 2f, sx0 + frame.artW - cr to cr)
        val rowsSrc = listOf(sy0 to ct, sy0 + frame.artH / 2f - 1f to 2f, sy0 + frame.artH - cb to cb)
        val colsDst = listOf(fx to cl * k, fx + cl * k to fw - (cl + cr) * k, fx + fw - cr * k to cr * k)
        val rowsDst = listOf(fy to ct * k, fy + ct * k to fh - (ct + cb) * k, fy + fh - cb * k to cb * k)
        return buildList {
            for (j in 0..2) for (i in 0..2) {
                if (i == 1 && j == 1) continue
                val (dX, dW) = colsDst[i]; val (dY, dH) = rowsDst[j]
                if (dW <= 0f || dH <= 0f) continue
                add(SkinPiece(PxRect(colsSrc[i].first, rowsSrc[j].first, colsSrc[i].second, rowsSrc[j].second), dst(dX, dY, dW, dH)))
            }
            frame.hole.takeIf { it.size == 4 }?.let { (l, t, r, b) ->
                val hw = fw - (l + r) * k; val hh = fh - (t + b) * k
                if (hw > 0f && hh > 0f) add(SkinPiece(PxRect(0f, 0f, 0f, 0f), dst(fx + l * k, fy + t * k, hw, hh), fill = gc * cornerK))
            }
        }
    }

    /**
     * How much of a piece's [inward] shift it gets in a window [dx] picture px wider (or narrower) than the full render:
     * all of it from the render's width up, none [inward] px each side narrower, in between in proportion.
     */
    fun inwardShare(inward: Int, dx: Float): Float = (dx / 2f + inward).coerceIn(0f, inward.toFloat())

    private fun gameSize(screen: GlassScreen, picture: Pair<Float, Float>?): Pair<Float, Float>? = when (screen) {
        GlassScreen.TOP, GlassScreen.TOUCH -> DS_W to DS_H
        GlassScreen.STACK -> DS_W to 2f * DS_H
        GlassScreen.PICTURE -> picture?.takeIf { it.first > 0f && it.second > 0f }
        GlassScreen.PANEL -> null
    }

    /** A [solve]: the [placement], or null; [tooBig]: a slice came out shorter than nothing (the glasses are too big). */
    private class Solved(val placement: SkinPlacement?, val tooBig: Boolean)

    private fun solve(
        spec: SkinLayoutSpec, windowW: Float, windowH: Float, k: Float, scales: List<Float>,
        game: Map<String, Pair<Float, Float>>, hingeY: Float?,
    ): Solved {
        var tooBig = false
        val placement = solveInto(spec, windowW, windowH, k, scales, game, hingeY) { tooBig = tooBig || it }
        return Solved(placement, tooBig)
    }

    private fun solveInto(
        spec: SkinLayoutSpec, windowW: Float, windowH: Float, k: Float, scales: List<Float>,
        game: Map<String, Pair<Float, Float>>, hingeY: Float?, onTooBig: (Boolean) -> Unit,
    ): SkinPlacement? {
        // glass sizes in picture px, and how much each grows over the picture's own
        val size = spec.glasses.mapIndexed { i, g -> g.id to (game.getValue(g.id).first * scales[i] / k to game.getValue(g.id).second * scales[i] / k) }.toMap()
        val grow = spec.glasses.associate { g -> g.id to (size.getValue(g.id).first - g.w to size.getValue(g.id).second - g.h) }
        val cells = ArrayList<SkinCell>()
        val ctx = Ctx(grow, hingeY?.let { it / k }, cells)
        val root = PxRect(0f, 0f, spec.width.toFloat(), spec.height.toFloat())
        val laid = ctx.lay(spec.tree, root, PxRect(0f, 0f, windowW / k, windowH / k), tiledX = false, tiledY = false)
        onTooBig(ctx.tooBig)
        if (!laid) return null
        val glasses = spec.glasses.mapIndexed { i, g ->
            val (x, y) = ctx.map(g.x + 0.5f, g.y + 0.5f) ?: return null
            val (w, h) = size.getValue(g.id)
            PlacedGlass(g.id, g.screen, PxRect((x - 0.5f) * k, (y - 0.5f) * k, w * k, h * k), scales[i], g.corner * k)
        }
        val controls = spec.controls.map { c ->
            val (x, y) = ctx.map(c.x, c.y) ?: return null
            PlacedControl(c.scaled(k), x * k, y * k)
        }
        val windowCells = cells.map { it.copy(dst = PxRect(it.dst.x * k, it.dst.y * k, it.dst.width * k, it.dst.height * k)) }
        return SkinPlacement(spec, k, windowCells, glasses, controls)
    }

    private class Ctx(val grow: Map<String, Pair<Float, Float>>, val hingeY: Float?, val cells: MutableList<SkinCell>) {
        var tooBig = false

        fun lay(node: SliceNode, src: PxRect, dst: PxRect, tiledX: Boolean, tiledY: Boolean): Boolean {
            val split = node.split
            if (split == null || node.parts.isEmpty()) {
                cells += SkinCell(src, dst, tiledX || abs(src.width - dst.width) > 0.01f, tiledY || abs(src.height - dst.height) > 0.01f)
                return true
            }
            val cols = split == SliceSplit.COLS
            val srcStart = if (cols) src.x else src.y
            val dstStart = if (cols) dst.x else dst.y
            val dstLen = if (cols) dst.width else dst.height
            val lens = allocate(node.parts, dstStart, dstLen, cols) ?: return false
            var s = srcStart
            var d = dstStart
            node.parts.forEachIndexed { i, p ->
                val l = lens[i]
                // a crop slice given less than its length: its last [l] px at their size (the top cut off)
                val cropped = p.isCrop && l < p.len
                val from = if (cropped) s + p.len - l else s
                val take = if (cropped) l else p.len.toFloat()
                val partSrc = if (cols) PxRect(from, src.y, take, src.height) else PxRect(src.x, from, src.width, take)
                val partDst = if (cols) PxRect(d, dst.y, l, dst.height) else PxRect(dst.x, d, dst.width, l)
                val stretches = p.mode != SlicePart.FIXED && p.mode != SlicePart.HINGE && !cropped
                val tx = tiledX || (cols && stretches)
                val ty = tiledY || (!cols && stretches)
                if (l > 0.01f || p.node != null) {
                    if (!lay(p.node ?: SliceNode(), partSrc, partDst, tx, ty)) return false
                }
                s += p.len
                d += l
            }
            return true
        }

        /** Each part's length in [total] window-side px, or null when one would be negative. */
        private fun allocate(parts: List<SlicePart>, start: Float, total: Float, cols: Boolean): FloatArray? {
            val hinge = parts.indexOfFirst { it.isHinge }
            if (hinge >= 0 && !cols && hingeY != null) {
                val before = parts.subList(0, hinge)
                val after = parts.subList(hinge + 1, parts.size)
                val hingeStart = hingeY - parts[hinge].len / 2f - start
                val a = share(before, hingeStart, cols) ?: return null
                val b = share(after, total - hingeStart - parts[hinge].len, cols) ?: return null
                return a + floatArrayOf(parts[hinge].len.toFloat()) + b
            }
            return share(parts, total, cols)
        }

        private fun share(parts: List<SlicePart>, total: Float, cols: Boolean): FloatArray? {
            if (parts.isEmpty()) return if (total in -0.01f..0.5f) FloatArray(0) else { if (total < 0f) tooBig = true; null }
            val counts = parts.mapNotNull { it.glass }.groupingBy { it }.eachCount()
            val out = FloatArray(parts.size) { parts[it].len.toFloat() }
            var used = 0f
            parts.forEachIndexed { i, p ->
                val g = p.glass ?: return@forEachIndexed
                val d = grow[g]?.let { if (cols) it.first else it.second } ?: 0f
                out[i] += d / counts.getValue(g)
                used += d / counts.getValue(g)
            }
            val rest = total - parts.sumOf { it.len } - used
            val repeats = parts.indices.filter { parts[it].isRepeat }
            if (repeats.isEmpty()) {
                if (rest < -0.01f) { tooBig = true; return null }
                if (rest > 0.5f) return null   // room no slice here can take
            } else {
                spread(parts, repeats, out, rest)
            }
            // a glass shorter than its own fixed edges is too small (a bigger one may fit); anything else short, too big
            if (out.indices.any { out[it] < -0.01f && parts[it].glass == null }) { tooBig = true; return null }
            if (out.any { it < -0.01f }) return null
            return FloatArray(out.size) { maxOf(0f, out[it]) }
        }

        /**
         * Shares [rest] among the [repeats] by weight (all alike when the weights add up to nothing). Taking away, a slice
         * that runs out stops at nothing and the others take the rest of the cut.
         */
        private fun spread(parts: List<SlicePart>, repeats: List<Int>, out: FloatArray, rest: Float) {
            var left = rest
            var active = if (rest < 0f) repeats.filter { parts[it].shrink } else repeats
            while (active.isNotEmpty() && abs(left) > 0.001f) {
                val weights = active.sumOf { parts[it].weight.toDouble() }.toFloat()
                fun share(i: Int) = if (weights > 0f) left * parts[i].weight / weights else left / active.size
                val short = if (left < 0f) active.filter { out[it] + share(it) < 0f } else emptyList()
                if (short.isEmpty()) { active.forEach { out[it] += share(it) }; return }
                short.forEach { left += out[it]; out[it] = 0f }
                active = active - short.toSet()
            }
            if (active.isEmpty() && left < -0.01f) out[repeats.first()] = -1f   // more than they all had: reads as too big
        }

        /** A picture point's place in the window (picture px): its offset kept within the leaf that holds it. */
        fun map(x: Float, y: Float): Pair<Float, Float>? {
            val c = cells.firstOrNull { x >= it.src.x && x < it.src.x + it.src.width && y >= it.src.y && y < it.src.y + it.src.height } ?: return null
            return c.dst.x + (x - c.src.x) to c.dst.y + (y - c.src.y)
        }
    }
}

/**
 * A piece's touch shortcut strip across its frame's inside, piece px before any growth: its start from the piece's left
 * and its width. The glass's own width, or more where the frame is wider than its screen (the DS's large bottom screen,
 * between black bars), the frame's border ([PIECE_FRAME_BORDER]) off each side; a piece with no glass, all of it.
 * [SkinSolver] places the strip's buttons by it, and [SkinGroups] the strip group's reference.
 */
internal fun pieceStripSpan(p: PieceSpec): Pair<Float, Float> {
    val inner = p.glass?.let { maxOf(it.w, p.w - 2 * PIECE_FRAME_BORDER) } ?: p.w
    return (p.glass?.let { (p.w - inner) / 2f } ?: 0f) to inner.toFloat()
}

/** The id ending of a tablet's fold board layouts (a tablet that does not fold): their frames are the main menu's. */
internal const val TABLET_BOARD = "_fold_tab"

/** A glass piece's frame border, piece px ([pieceStripSpan]). */
internal const val PIECE_FRAME_BORDER = 20
