package com.nxredux.mobile.skin

import com.nxredux.mobile.options.PxRect

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
     * Where no group may go in [p] (window px): each screen with [SCREEN_CLEAR] round it, and on a layout made for
     * half-folding the band from the screen frame's bottom past the fold (the pieces keep [FrameSpec.hinge] below it).
     */
    fun noGo(p: SkinPlacement): List<PxRect> {
        val m = SCREEN_CLEAR * p.k
        val out = p.glasses.mapTo(ArrayList()) { PxRect(it.rect.x - m, it.rect.y - m, it.rect.width + 2 * m, it.rect.height + 2 * m) }
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
     * pieces and controls (their place and every size) scaled about the group's middle and moved. A move that would
     * take a group further into [noGo] or out of the window is cut short toward none (and a growth likewise, toward its
     * own size). The frame's strip ([SkinGroups.STRIP]) moves its labels and its buttons. Apply before
     * [SkinPlacement.forStick] (both of a swap's sets move together).
     */
    fun apply(p: SkinPlacement, tweaks: Map<String, SkinTweak>, windowW: Float, windowH: Float): SkinPlacement {
        val frame = p.spec.frame
        if (tweaks.isEmpty() || frame == null) return p
        val groups = SkinGroups.of(p.spec)
        val zones = noGo(p) + PxRect(0f, 0f, windowW, windowH)
        val stripN = frame.strip?.labels?.size ?: 0
        val pieces = p.pieces.toMutableList()
        val controls = p.controls.toMutableList()
        for (g in groups) {
            val t = limit(tweaks[g.id] ?: continue, g)
            if (t == SkinTweak()) continue
            val strip = g.id == SkinGroups.STRIP
            val pieceAt = pieces.indices.filter { if (strip) pieces[it].strip else pieces[it].piece in g.pieces }
            val controlAt = if (strip) (0 until minOf(stripN, controls.size)).toList() else ownControls(p, groups, g, stripN)
            // the group's box: its pieces (the strip's: its labels and its buttons, which span it)
            val box = union(pieceAt.map { pieces[it].dst } + if (strip || pieceAt.isEmpty()) controlAt.map { hit(controls[it]) } else emptyList()) ?: continue
            val cx = box.x + box.width / 2f; val cy = box.y + box.height / 2f
            fun moved(s: Float, ox: Float, oy: Float) = PxRect(cx + (box.x - cx) * s + ox, cy + (box.y - cy) * s + oy, box.width * s, box.height * s)
            // how far the box is in each no-go rect and out of the window: never more than where the layout put it
            fun cost(r: PxRect) = zones.mapIndexed { i, z -> if (i == zones.lastIndex) r.width * r.height - overlap(r, z) else overlap(r, z) }
            val base = cost(box)
            fun fits(r: PxRect) = cost(r).withIndex().all { (i, c) -> c <= base[i] }
            val ox = t.dx * p.k; val oy = t.dy * p.k
            val sc = 1f + (t.scale - 1f) * longest { fits(moved(1f + (t.scale - 1f) * it, 0f, 0f)) }
            val f = longest { fits(moved(sc, ox * it, oy * it)) }
            val dx = ox * f; val dy = oy * f
            fun mx(x: Float) = cx + (x - cx) * sc + dx
            fun my(y: Float) = cy + (y - cy) * sc + dy
            for (i in pieceAt) {
                val d = pieces[i].dst
                pieces[i] = pieces[i].copy(dst = PxRect(mx(d.x), my(d.y), d.width * sc, d.height * sc))
            }
            for (i in controlAt) {
                val c = controls[i]
                controls[i] = PlacedControl(c.spec.scaled(sc), mx(c.x), my(c.y))
            }
        }
        return p.copy(pieces = pieces, controls = controls)
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
