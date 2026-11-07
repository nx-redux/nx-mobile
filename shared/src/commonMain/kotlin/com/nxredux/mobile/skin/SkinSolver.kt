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

/** A piece of a layout built from pieces: [src] in its parts picture drawn at [dst] (scaled when the sizes differ). */
/** [strip]: one of the frame strip's shortcut labels (hidden with the in-game menu open). [piece]: the layout's piece it draws (an index into [SkinLayoutSpec.pieces]); -1: the frame or a strip label. */
data class SkinPiece(val src: PxRect, val dst: PxRect, val strip: Boolean = false, val alt: String? = null, val knob: Boolean = false, val knobOf: String = "STICK", val piece: Int = -1)

/** A control's hit area placed in the window: its [spec] (id, shape, sizes scaled) at [x], [y]. */
data class PlacedControl(val spec: ControlSpec, val x: Float, val y: Float)

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
     * room ends above the fold, and every piece must lie below it (else null).
     * [topScreen]: the DS / 3DS top screen's game size (256 x 192, 400 x 240): a filling frame showing it, on a board
     * with no fold, is cut to its shape (its border as thin above and below as at the sides), kept at the top, and the
     * pieces with a screen (the touch screen) move up by the height that saves.
     */
    fun placePieces(spec: SkinLayoutSpec, windowW: Float, windowH: Float, k: Float, picture: Pair<Float, Float>? = null, topInset: Float = 0f, hingeY: Float? = null, topScreen: Pair<Float, Float>? = null): SkinPlacement? {
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
        fun down(l: PieceLevel) = when (l) {
            PieceLevel.TOP -> 0f
            PieceLevel.MIDDLE -> dy / 2f
            PieceLevel.BOTTOM -> dy
        }
        fun win(x: Float, y: Float, pw: Float, ph: Float) = PxRect(x * k, y * k, pw * k, ph * k)
        val g = frame.glass
        // the frame's border round the glass, and the room the full render gives the frame, grown or shrunk with the window
        val bl = g.x.toFloat(); val bt = g.y.toFloat(); val br = (frame.w - g.x - g.w).toFloat(); val bb = (frame.h - g.y - g.h).toFloat()
        val left = if (frame.side >= 0) frame.side.toFloat() else frame.x.toFloat()
        val right = if (frame.side >= 0) frame.side.toFloat() else (spec.refW - frame.x - frame.w).toFloat()
        val ownTop = if (frame.top >= 0) frame.top.toFloat() else frame.y.toFloat()
        val roomTop = if (topInset > 0f) topInset / k else ownTop   // the inset is the frame's top margin
        val roomW = w - left - right
        val folded = hingeY != null && frame.hinge >= 0
        val roomH = if (folded) hingeY!! / k - frame.hinge - roomTop
            else h - roomTop - (if (frame.bottom >= 0) frame.bottom.toFloat() else (spec.refH - frame.y - frame.h).toFloat())
        // half-folded, the pad's pieces all go below the fold
        if (folded && spec.pieces.any { it.y + down(it.level) < hingeY!! / k + frame.hinge }) return null
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
        val gh = if (tight) tw * topScreen!!.second / topScreen.first else if (frame.fill) roomH - bt - bb else ah * fit
        val fw = gw + bl + br
        val fh = gh + bt + bb
        val fx = left + (roomW - fw) / 2f
        val fy = if (tight) roomTop else roomTop + (roomH - fh)   // down against what is under it: the spare height goes above
        val lift = if (tight) roomH - fh else 0f   // the touch screen follows the shorter frame up
        val pieces = ArrayList<SkinPiece>()
        // the frame: corners as drawn, straight edges stretched from the 2 px slice at their middle (the glass, a hole, skipped).
        // Each drawn border reaches past the glass's edge by its corner radius, so the hole's rounded corners are drawn
        // as they are (a border thicker than the frame's corner, as under the strip, would start right at the glass and
        // stretch the corner's curve into a square: the board then shows through)
        val gc = g.corner.toFloat()
        val cl = maxOf(bl + gc, frame.corner.toFloat()); val ct = maxOf(bt + gc, frame.corner.toFloat())
        val cr = maxOf(br + gc, frame.corner.toFloat()); val cb = maxOf(bb + gc, frame.corner.toFloat())
        val sx0 = frame.sx.toFloat(); val sy0 = frame.sy.toFloat()
        val midX = sx0 + frame.w / 2f - 1f; val midY = sy0 + frame.h / 2f - 1f
        val colsSrc = listOf(sx0 to cl, midX to 2f, sx0 + frame.w - cr to cr)
        val rowsSrc = listOf(sy0 to ct, midY to 2f, sy0 + frame.h - cb to cb)
        val colsDst = listOf(fx to cl, fx + cl to fw - cl - cr, fx + fw - cr to cr)
        val rowsDst = listOf(fy to ct, fy + ct to fh - ct - cb, fy + fh - cb to cb)
        for (j in 0..2) for (i in 0..2) {
            if (i == 1 && j == 1) continue
            val (dX, dW) = colsDst[i]; val (dY, dH) = rowsDst[j]
            if (dW <= 0f || dH <= 0f) continue
            pieces += SkinPiece(PxRect(colsSrc[i].first, rowsSrc[j].first, colsSrc[i].second, rowsSrc[j].second), win(dX, dY, dW, dH))
        }
        val controls = ArrayList<PlacedControl>()
        frame.strip?.let { st ->
            val share = gw / st.labels.size
            st.labels.forEachIndexed { i, l ->
                val cx = fx + bl + share * (i + 0.5f)
                val cy = fy + fh - st.y
                // a label drawn in the picture; none (the touch slots, TOUCH_1..4): the app writes the slot's action
                if (l.w > 0 && l.h > 0) pieces += SkinPiece(PxRect(l.sx.toFloat(), l.sy.toFloat(), l.w.toFloat(), l.h.toFloat()), win(cx - l.w / 2f, cy - l.h / 2f, l.w.toFloat(), l.h.toFloat()), strip = true)
                controls += PlacedControl(ControlSpec(l.id, ControlShape.RECT, cx, cy, hw = share / 2f * k, hh = st.hh * k), cx * k, cy * k)
            }
        }
        val glasses = arrayListOf(PlacedGlass(g.id, g.screen, win(fx + bl, fy + bt, gw, gh), fit * k, g.corner * k))
        val maxInward = spec.pieces.maxOfOrNull { it.inward } ?: 0
        for ((pi, p) in spec.pieces.withIndex()) {
            var px = p.x + across(p.anchor, p.inward)
            // the touch screen follows the shorter frame up; the pad (d-pad, face) up to its own rise
            var py = p.y + down(p.level) - (if (p.glass != null) lift else minOf(lift, p.rise.toFloat()))
            var gx = 0f; var gy = 0f   // a growing piece's growth, picture px
            val pg0 = p.glass
            if (p.grow > 0 && pg0 != null && dx > 0f) {
                // the room the side clusters leave as they spread, the glass's shape kept, up to just under the hinge
                gx = maxOf(0f, dx - 2f * maxInward)
                gy = gx * pg0.h / pg0.w
                val ceiling = (hingeY?.let { it / k } ?: roomTop) + HINGE_CLEAR
                if (py - gy < ceiling) { gy = maxOf(0f, py - ceiling); gx = gy * pg0.w / pg0.h }
                px -= gx / 2f; py -= gy
            }
            if (gx > 0f || gy > 0f) {
                pieces += nineSlice(p, p.grow.toFloat(), p.w + gx, p.h + gy) { x, y, w_, h_ -> win(px + x, py + y, w_, h_) }.map { it.copy(piece = pi) }
            } else {
                pieces += SkinPiece(PxRect(p.sx.toFloat(), p.sy.toFloat(), p.w.toFloat(), p.h.toFloat()), win(px, py, p.w * p.scale, p.h * p.scale), alt = p.alt, knob = p.knob, knobOf = p.knobOf, piece = pi)
            }
            p.glass?.let { pg ->
                val gameW = gameSize(pg.screen, picture)?.first ?: (pg.w + gx)
                val sc = p.scale
                glasses += PlacedGlass(pg.id, pg.screen, win(px + pg.x * sc, py + pg.y * sc, (pg.w + gx) * sc, (pg.h + gy) * sc), (pg.w + gx) * sc * k / gameW, pg.corner * sc * k)
            }
            p.strip?.let { st ->
                // the piece's strip: an even share of its glass's width each (the whole piece's without one), under the
                // grown glass when the piece grows (its border, the strip's band, keeps its size)
                val sc = p.scale
                // across the frame's inside (its border off each side): the glass's own width, or more where the frame is
                // wider than its screen (the DS's large bottom screen, between black bars)
                val inner = p.glass?.let { maxOf(it.w, p.w - 2 * 20) } ?: p.w
                val sx = px + (p.glass?.let { (p.w - inner) / 2f } ?: 0f) * sc; val sw = (inner + gx) * sc
                val share = sw / st.labels.size
                st.labels.forEachIndexed { i, l ->
                    val cx = sx + share * (i + 0.5f)
                    val cy = py + (p.h + gy - st.y) * sc
                    controls += PlacedControl(ControlSpec(l.id, ControlShape.RECT, cx, cy, hw = share / 2f * k, hh = st.hh * sc * k), cx * k, cy * k)
                }
            }
        }
        controls += spec.controls.map { c ->
            PlacedControl(c.copy(r = c.r * k, hw = c.hw * k, hh = c.hh * k, capR = c.capR * k, arm = c.arm * k, labelDy = c.labelDy * k, labelDx = c.labelDx * k, labelH = c.labelH * k, travel = c.travel * k, knobTravel = c.knobTravel * k), (c.x + across(c.anchor ?: PieceAnchor.LEFT, c.inward)) * k, (c.y + down(c.level) - minOf(lift, c.rise.toFloat())) * k)
        }
        return SkinPlacement(spec, k, bg.cells, glasses, controls, pieces)
    }

    /** A growing piece keeps this far (picture px) under the hinge (or the room's top). */
    private const val HINGE_CLEAR = 80f

    /** Piece [p]'s picture drawn [w] x [h] picture px: corners [c] as drawn, the edges and middle stretched. */
    private fun nineSlice(p: PieceSpec, c: Float, w: Float, h: Float, dst: (Float, Float, Float, Float) -> PxRect): List<SkinPiece> {
        val sx = p.sx.toFloat(); val sy = p.sy.toFloat()
        val cols = listOf(Triple(sx, c, 0f to c), Triple(sx + c, p.w - 2 * c, c to w - 2 * c), Triple(sx + p.w - c, c, w - c to c))
        val rows = listOf(Triple(sy, c, 0f to c), Triple(sy + c, p.h - 2 * c, c to h - 2 * c), Triple(sy + p.h - c, c, h - c to c))
        return buildList {
            for ((ry, rh, rd) in rows) for ((cx, cw, cd) in cols) {
                if (cd.second <= 0f || rd.second <= 0f) continue
                add(SkinPiece(PxRect(cx, ry, cw, rh), dst(cd.first, rd.first, cd.second, rd.second)))
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
        val ctx = Ctx(emptyMap(), null, ArrayList())
        val placement = solveInto(spec, windowW, windowH, k, scales, game, hingeY) { ctx.tooBig = ctx.tooBig || it }
        return Solved(placement, ctx.tooBig)
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
            PlacedControl(c.copy(r = c.r * k, hw = c.hw * k, hh = c.hh * k, capR = c.capR * k, arm = c.arm * k, labelDy = c.labelDy * k, labelDx = c.labelDx * k, labelH = c.labelH * k, travel = c.travel * k, knobTravel = c.knobTravel * k), x * k, y * k)
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
