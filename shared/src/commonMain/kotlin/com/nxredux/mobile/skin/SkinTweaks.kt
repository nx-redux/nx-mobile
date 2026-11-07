package com.nxredux.mobile.skin

import com.nxredux.mobile.options.PxRect
import kotlin.math.abs
import kotlin.math.ceil

/** A player's nudge of one button group: [dx], [dy] in picture px, and a [scale] about the group's centre. */
data class SkinTweak(val dx: Float = 0f, val dy: Float = 0f, val scale: Float = 1f)

/** One console's tweaks: layout id, then group id. Keyed by layout, so every pack sharing the layout shares the tweak. */
typealias ConsoleTweaks = Map<String, Map<String, SkinTweak>>

object SkinTweaks {
    const val MIN_SCALE = 0.8f
    const val MAX_SCALE = 1.25f

    /** How far a group may move from where its layout puts it, as a fraction of its larger side. */
    const val MOVE_FRACTION = 0.5f

    /** [t] held to the limits for [g]: scale within [MIN_SCALE]..[MAX_SCALE], a move within [MOVE_FRACTION] of its size (a free group is not capped). */
    fun limit(t: SkinTweak, g: SkinGroup): SkinTweak {
        val cap = MOVE_FRACTION * maxOf(g.ref.width, g.ref.height)
        return SkinTweak(
            dx = if (g.free) t.dx else t.dx.coerceIn(-cap, cap),
            dy = if (g.free) t.dy else t.dy.coerceIn(-cap, cap),
            scale = t.scale.coerceIn(MIN_SCALE, MAX_SCALE),
        )
    }

    /** How far (picture px) a group keeps from every screen. */
    const val SCREEN_CLEAR = 12f

    /**
     * Where no group may go in [p] (window px): each screen with [SCREEN_CLEAR] round it, each piece holding a screen as
     * drawn (the large DS layout's touch-screen frame reaches past that margin), and on a layout made for half-folding
     * the band from the screen frame's bottom past the fold (the pieces keep [FrameSpec.hinge] below it).
     */
    fun noGo(p: SkinPlacement): List<PxRect> {
        val m = SCREEN_CLEAR * p.k
        val out = p.glasses.mapTo(ArrayList()) { PxRect(it.rect.x - m, it.rect.y - m, it.rect.width + 2 * m, it.rect.height + 2 * m) }
        for (piece in p.pieces) if (piece.piece >= 0 && p.spec.pieces.getOrNull(piece.piece)?.glass != null) out += piece.dst
        val f = p.spec.frame
        val g = p.glasses.firstOrNull()   // the frame's screen comes first
        if (f != null && f.hinge >= 0 && g != null) {
            val bottom = g.rect.y + g.rect.height + (f.h - f.glass.y - f.glass.h) * p.k
            val l = minOf(0f, p.cells.minOfOrNull { it.dst.x } ?: 0f)
            val r = maxOf(g.rect.x + g.rect.width, p.cells.maxOfOrNull { it.dst.x + it.dst.width } ?: 0f)
            out += PxRect(l, bottom, r - l, 2f * f.hinge * p.k)
        }
        return out
    }

    /**
     * [p] with [tweaks] (group id to tweak, each held to [limit]) applied in a [windowW] x [windowH] window: each group's
     * pieces and controls (their place and every size) scaled about the middle of its pieces and moved. A group's box
     * (its pieces and its controls' hit areas) may not go further into [noGo], into another group's box, or out of the
     * window than it started, nor pass over any of them on the way: the move stops short (and a growth likewise, toward
     * its own size). Groups go in [SkinGroups.of]'s order, each against the others' boxes as they stand. The frame's
     * strip ([SkinGroups.STRIP]) moves its labels and its buttons. Apply before [SkinPlacement.forStick] (both of a
     * swap's sets move together).
     */
    fun apply(p: SkinPlacement, tweaks: Map<String, SkinTweak>, windowW: Float, windowH: Float): SkinPlacement {
        // groups are made of a layout's pieces, and only a frame layout has pieces (every pack's piece layout has one)
        val frame = p.spec.frame
        if (tweaks.isEmpty() || frame == null) return p
        val groups = SkinGroups.of(p.spec)
        val zones = noGo(p)
        val window = PxRect(0f, 0f, windowW, windowH)
        val stripN = frame.strip?.labels?.size ?: 0
        val pieces = p.pieces.toMutableList()
        val controls = p.controls.toMutableList()
        val pieceAt = groups.map { g ->
            val strip = g.id == SkinGroups.STRIP
            pieces.indices.filter { if (strip) pieces[it].strip else pieces[it].piece in g.pieces }
        }
        val controlAt = groups.map { g ->
            if (g.id == SkinGroups.STRIP) (0 until minOf(stripN, controls.size)).toList() else ownControls(p, groups, g, stripN)
        }
        fun boxOf(i: Int) = union(pieceAt[i].map { pieces[it].dst } + controlAt[i].map { hit(controls[it]) })
        val boxes = groups.indices.mapTo(ArrayList()) { boxOf(it) }
        for ((gi, g) in groups.withIndex()) {
            val t = limit(tweaks[g.id] ?: continue, g)
            if (t == SkinTweak()) continue
            val box = boxes[gi] ?: continue
            // the middle it scales about: its pieces' (the strip's: its whole box)
            val mid = (if (g.id == SkinGroups.STRIP) null else union(pieceAt[gi].map { pieces[it].dst })) ?: box
            val cx = mid.x + mid.width / 2f; val cy = mid.y + mid.height / 2f
            fun moved(s: Float, ox: Float, oy: Float) = PxRect(cx + (box.x - cx) * s + ox, cy + (box.y - cy) * s + oy, box.width * s, box.height * s)
            val obstacles = zones + boxes.filterIndexed { i, b -> i != gi && b != null }.map { it!! }
            // how far the box is in each obstacle (its area there) and out of the window (its edges' distance past it):
            // never more than where it started
            fun cost(r: PxRect) = obstacles.map { overlap(r, it) } + outside(r, window)
            val base = cost(box)
            fun fits(r: PxRect) = cost(r).zip(base).all { (c, b) -> c <= b + 0.01f }   // px², over float noise
            val ox = t.dx * p.k; val oy = t.dy * p.k
            // a growth only adds to the box round its middle (no jumping), a move is walked from where it starts
            val sc = 1f + (t.scale - 1f) * longest { fits(moved(1f + (t.scale - 1f) * it, 0f, 0f)) }
            val f = reach(maxOf(abs(ox), abs(oy))) { fits(moved(sc, ox * it, oy * it)) }
            val dx = ox * f; val dy = oy * f
            fun mx(x: Float) = cx + (x - cx) * sc + dx
            fun my(y: Float) = cy + (y - cy) * sc + dy
            for (i in pieceAt[gi]) {
                val d = pieces[i].dst
                pieces[i] = pieces[i].copy(dst = PxRect(mx(d.x), my(d.y), d.width * sc, d.height * sc))
            }
            for (i in controlAt[gi]) {
                val c = controls[i]
                controls[i] = PlacedControl(c.spec.scaled(sc), mx(c.x), my(c.y))
            }
            boxes[gi] = boxOf(gi)
        }
        return p.copy(pieces = pieces, controls = controls)
    }

    /** How far (window px) a move steps at most, so it never passes over a thinner obstacle. */
    private const val STEP = 2f

    /**
     * The most of a move [length] window px long (0..1) that is still [ok] all the way: walked in [STEP]s from none, the
     * first step that is not, halved back to where it stops.
     */
    private fun reach(length: Float, ok: (Float) -> Boolean): Float {
        val n = ceil(length / STEP).toInt().coerceIn(1, 4096)
        for (i in 1..n) {
            if (ok(i.toFloat() / n)) continue
            var lo = (i - 1f) / n; var hi = i.toFloat() / n
            repeat(16) { val m = (lo + hi) / 2f; if (ok(m)) lo = m else hi = m }
            return lo
        }
        return 1f
    }

    /** The most of a change (0..1) that is still [ok]: all of it, else found by halving (none at worst). */
    private fun longest(ok: (Float) -> Boolean): Float {
        if (ok(1f)) return 1f
        var lo = 0f; var hi = 1f
        repeat(20) { val mid = (lo + hi) / 2f; if (ok(mid)) lo = mid else hi = mid }
        return lo
    }

    /**
     * [g]'s controls in [p] (indexes into its controls, the frame strip's first [stripN] left out). Matched by id and
     * picture place, so a control the layout draws twice (in two groups) moves with the group whose place holds it.
     */
    private fun ownControls(p: SkinPlacement, groups: List<SkinGroup>, g: SkinGroup, stripN: Int): List<Int> {
        val mine = p.spec.controls.filter { c ->
            c.id in g.controls && groups.filter { c.id in it.controls && it.id != SkinGroups.STRIP }.minByOrNull { gap(it.ref, c.x, c.y) } == g
        }.map { Triple(it.id, it.x, it.y) }.toSet()
        return p.controls.indices.filter { it >= stripN && Triple(p.controls[it].spec.id, p.controls[it].spec.x, p.controls[it].spec.y) in mine }
    }

    private fun ControlSpec.scaled(s: Float) = copy(
        r = r * s, hw = hw * s, hh = hh * s, capR = capR * s, arm = arm * s, labelDy = labelDy * s, labelDx = labelDx * s,
        labelH = labelH * s, travel = travel * s, knobTravel = knobTravel * s,
    )

    /** A placed control's hit box (window px). */
    private fun hit(c: PlacedControl): PxRect {
        val hw = if (c.spec.shape == ControlShape.RECT) c.spec.hw else c.spec.r
        val hh = if (c.spec.shape == ControlShape.RECT) c.spec.hh else c.spec.r
        return PxRect(c.x - hw, c.y - hh, 2f * hw, 2f * hh)
    }

    private fun union(rs: List<PxRect>): PxRect? {
        if (rs.isEmpty()) return null
        val l = rs.minOf { it.x }; val t = rs.minOf { it.y }
        return PxRect(l, t, rs.maxOf { it.x + it.width } - l, rs.maxOf { it.y + it.height } - t)
    }

    /** How far [r]'s edges lie past [w]'s, added up. */
    private fun outside(r: PxRect, w: PxRect) = maxOf(0f, w.x - r.x) + maxOf(0f, w.y - r.y) +
        maxOf(0f, r.x + r.width - (w.x + w.width)) + maxOf(0f, r.y + r.height - (w.y + w.height))

    private fun overlap(a: PxRect, b: PxRect): Float {
        val w = minOf(a.x + a.width, b.x + b.width) - maxOf(a.x, b.x)
        val h = minOf(a.y + a.height, b.y + b.height) - maxOf(a.y, b.y)
        return if (w > 0f && h > 0f) w * h else 0f
    }

    private fun gap(r: PxRect, x: Float, y: Float): Float {
        val dx = maxOf(r.x - x, 0f, x - (r.x + r.width)); val dy = maxOf(r.y - y, 0f, y - (r.y + r.height))
        return dx * dx + dy * dy
    }

    /** The tweaks as text: one line per group, `console layout group dx dy scale`. */
    fun encode(m: Map<String, ConsoleTweaks>): String = buildString {
        for ((console, layouts) in m) for ((layout, groups) in layouts) for ((group, t) in groups) {
            if (isNotEmpty()) append('\n')
            append("$console $layout $group ${t.dx} ${t.dy} ${t.scale}")
        }
    }

    /** What [encode] wrote. Lines that do not read (wrong shape, a bad number) are skipped. */
    fun decode(s: String): Map<String, ConsoleTweaks> {
        val out = linkedMapOf<String, LinkedHashMap<String, LinkedHashMap<String, SkinTweak>>>()
        for (line in s.lines()) {
            val p = line.trim().split(' ')
            if (p.size != 6) continue
            val dx = p[3].toFloatOrNull() ?: continue
            val dy = p[4].toFloatOrNull() ?: continue
            val sc = p[5].toFloatOrNull() ?: continue
            if (!dx.isFinite() || !dy.isFinite() || !sc.isFinite()) continue
            out.getOrPut(p[0]) { linkedMapOf() }.getOrPut(p[1]) { linkedMapOf() }[p[2]] = SkinTweak(dx, dy, sc)
        }
        return out
    }
}
