package com.nxredux.mobile.skin

import com.nxredux.mobile.options.PxRect
import kotlin.math.abs
import kotlin.math.ceil

/** A player's nudge of one button group: [dx], [dy] in picture px, and a [scale] about the group's centre. */
data class SkinTweak(val dx: Float = 0f, val dy: Float = 0f, val scale: Float = 1f)

/** One console's tweaks: layout id, then group id. Keyed by layout, so every pack sharing the layout shares the tweak. */
typealias ConsoleTweaks = Map<String, Map<String, SkinTweak>>

/**
 * Which limits [SkinTweaks.apply] holds a group to. [SKIN]: on the skin, where the screens' middles and a half-fold's
 * hinge stay clear. [OVERLAY]: the buttons over the game's full-window picture, where the picture is no limit.
 */
enum class TweakRules { SKIN, OVERLAY }

object SkinTweaks {
    /**
     * [t] as [apply] takes it: a scale that is not a size (zero, negative, not finite) counts as none. What limits a
     * move or a size is the room round the group, not the tweak.
     */
    fun limit(t: SkinTweak): SkinTweak = if (t.scale.isFinite() && t.scale > 0f) t else t.copy(scale = 1f)

    /** How much of a screen's width (left and right) and height (top and bottom) a group may cover from its edges. */
    private const val SCREEN_EDGE = 0.2f

    /**
     * Where no group may go in [p] (window px) under [rules]. [TweakRules.SKIN]: each screen's middle (all but
     * [SCREEN_EDGE] of it from each edge), so a button may cover a screen's edge but never what the game shows in the
     * middle; and on a layout made for half-folding the band from the screen frame's bottom past the fold (the pieces
     * keep [FrameSpec.hinge] below it), where a button on the crease is hard to press. [TweakRules.OVERLAY]: nowhere.
     */
    fun noGo(p: SkinPlacement, rules: TweakRules = TweakRules.SKIN): List<PxRect> {
        if (rules == TweakRules.OVERLAY) return emptyList()
        val out = p.glasses.mapTo(ArrayList()) {
            val r = it.rect
            val keep = 1f - 2f * SCREEN_EDGE
            PxRect(r.x + SCREEN_EDGE * r.width, r.y + SCREEN_EDGE * r.height, keep * r.width, keep * r.height)
        }
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

    /** What the game's hit test adds round a rect button's or the d-pad's hit box (picture px), as SkinPadTouch does. */
    private const val TOUCH_SLOP = 22f

    /** A stick's touch area: its ring's radius times this, as SkinPadTouch does. */
    private const val STICK_SLOP = 1.25f

    /**
     * [c]'s touch area (window px, [k] window px per picture px): where the game takes a touch as [c]'s. A rect button
     * and the d-pad: the hit box and [TOUCH_SLOP] round it; a stick: the square round its ring times [STICK_SLOP]; a
     * round button: its circle's square.
     */
    internal fun touchZone(c: PlacedControl, k: Float): PxRect = grow(core(c), pad(c.spec, k))

    /** The part of [c]'s [touchZone] that scales with it: a stick's whole touch area, else its hit box. */
    private fun core(c: PlacedControl): PxRect =
        if (c.spec.shape == ControlShape.STICK) grow(hit(c), c.spec.r * (STICK_SLOP - 1f)) else hit(c)

    /** The part of a [touchZone] round its [core] that stays the same at any size: the slop. */
    private fun pad(s: ControlSpec, k: Float) =
        if (s.shape == ControlShape.RECT || s.shape == ControlShape.DPAD) TOUCH_SLOP * k else 0f

    private fun grow(r: PxRect, m: Float) = PxRect(r.x - m, r.y - m, r.width + 2f * m, r.height + 2f * m)

    /**
     * [p] with [tweaks] (group id to tweak, each through [limit]) applied in a [windowW] x [windowH] window: each
     * group's pieces and controls (their place and every size) scaled about the middle of its pieces and moved. A
     * group may not go further than it started into [noGo] for [rules] (its pieces and its controls' hit boxes), with its
     * controls' touch areas ([touchZone]) onto another group's or onto a control in no group, nor with its hit boxes
     * out of the window; nor pass over any of them on the way: the move stops short (and a growth likewise, toward its
     * own size). Drawn parts may lie on each other and past the window's edge: only where a touch lands has to stay
     * clear. A shrink stops where a control's hit box would be under [minTouchPx] (window px) on its shorter side, and
     * a group already under it does not shrink. Groups go in [SkinGroups.of]'s order, each against the groups before it
     * where they ended and the groups after it where they are going (so a group may move into the room a later one
     * leaves); a later group that gets less far than that is tried again where it got to, and should that still leave
     * two groups on each other, each group goes against the later ones where they stand. The frame's strip
     * ([SkinGroups.STRIP]) moves its labels and its buttons. Apply before [SkinPlacement.forStick] (both of a swap's
     * sets move together).
     */
    fun apply(
        p: SkinPlacement, tweaks: Map<String, SkinTweak>, windowW: Float, windowH: Float,
        rules: TweakRules = TweakRules.SKIN, minTouchPx: Float = 0f,
    ): SkinPlacement {
        // groups are made of a layout's pieces, and only a frame layout has pieces (every pack's piece layout has one)
        val frame = p.spec.frame
        if (tweaks.isEmpty() || frame == null) return p
        val groups = SkinGroups.of(p.spec)
        val zones = noGo(p, rules)
        val window = PxRect(0f, 0f, windowW, windowH)
        val (pieceAt, controlAt) = members(p, groups)
        val ts = groups.map { g -> tweaks[g.id]?.let { limit(it) }?.takeIf { it != SkinTweak() } }
        // controls no group holds never move: their touch areas are in every group's way
        val held = controlAt.flatten().toSet()
        val loose = p.controls.indices.filter { it !in held }.map { touchZone(p.controls[it], p.k) }
        val home = groups.indices.map { footprint(p, pieceAt[it], controlAt[it], p.pieces, p.controls) }
        // the middle a group scales about: its pieces' (the strip's: its whole box)
        val mids = groups.indices.map { gi -> (if (groups[gi].id == SkinGroups.STRIP) null else union(pieceAt[gi].map { p.pieces[it].dst })) ?: home[gi]?.box }
        // the smallest scale that keeps every control's hit box at [minTouchPx] (a group already under it: its own size)
        val floors = groups.indices.map { gi ->
            val side = controlAt[gi].minOfOrNull { hit(p.controls[it]).let { h -> minOf(h.width, h.height) } } ?: 0f
            if (minTouchPx <= 0f || side <= 0f) 0f else if (side < minTouchPx) 1f else minTouchPx / side
        }

        /**
         * How far group [gi] gets against the no-go zones, the window and [others]' touch areas: its scale and its move
         * (window px), walked from where it starts. None when it has no tweak or nothing placed.
         */
        fun land(gi: Int, others: List<List<PxRect>>): Triple<Float, Float, Float>? {
            val t = ts[gi] ?: return null
            val start = home[gi] ?: return null
            val mid = mids[gi] ?: start.box
            // how far the group is in each no-go zone (its area there), on each other group's touch areas and out of the
            // window (its hit boxes' edges' distance past it): never more than where it started
            fun cost(f: Footprint) = zones.map { overlap(f.box, it) } + others.map { touching(f.touch, it) } +
                outside(f.hit ?: f.box, window)
            val base = cost(start)
            fun fits(f: Footprint) = cost(f).zip(base).all { (c, b) -> c <= b + 0.01f }   // px², over float noise
            val ox = t.dx * p.k; val oy = t.dy * p.k
            val target = maxOf(t.scale, floors[gi])
            // a growth only adds to the group round its middle (no jumping), a move is walked from where it starts
            val sc = 1f + (target - 1f) * longest { fits(start.moved(mid, 1f + (target - 1f) * it, 0f, 0f)) }
            val f = reach(maxOf(abs(ox), abs(oy))) { fits(start.moved(mid, sc, ox * it, oy * it)) }
            return Triple(sc, ox * f, oy * f)
        }

        /**
         * Where group [gi] is going: its tweak as far as the screens and the window let it, the other groups left out
         * (a move past the window's edge would otherwise hold back a group that could take the room it leaves).
         */
        fun going(gi: Int): Footprint? {
            val f = home[gi] ?: return null
            val (sc, dx, dy) = land(gi, listOf(loose)) ?: return f
            return f.moved(mids[gi] ?: f.box, sc, dx, dy)
        }

        /** Every group placed in turn, each against [ahead]'s footprints for the groups after it: the pieces, controls and footprints. */
        fun place(ahead: List<Footprint?>): Triple<List<SkinPiece>, List<PlacedControl>, List<Footprint?>> {
            val pieces = p.pieces.toMutableList()
            val controls = p.controls.toMutableList()
            val placed = home.toMutableList()
            for (gi in groups.indices) {
                val others = groups.indices.mapNotNull { i -> if (i < gi) placed[i]?.touch else if (i > gi) ahead[i]?.touch else null } + listOf(loose)
                val (sc, dx, dy) = land(gi, others) ?: continue
                val mid = mids[gi] ?: continue
                val cx = mid.x + mid.width / 2f; val cy = mid.y + mid.height / 2f
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
                placed[gi] = footprint(p, pieceAt[gi], controlAt[gi], pieces, controls)
            }
            return Triple(pieces, controls, placed)
        }

        /** No two groups' touch areas more on each other in [placed] than they started. */
        fun clear(placed: List<Footprint?>) = groups.indices.all { i ->
            (i + 1 until groups.size).all { j ->
                val a = placed[i]; val b = placed[j]
                a == null || b == null || touching(a.touch, b.touch) <= touching(home[i]!!.touch, home[j]!!.touch) + 0.01f
            }
        }

        // first where each group is going, then where it got to
        var ahead = groups.indices.map { going(it) }
        repeat(PASSES) {
            val (pieces, controls, placed) = place(ahead)
            if (clear(placed)) return p.copy(pieces = pieces, controls = controls)
            ahead = placed
        }
        // each against the later groups where they stand: never on each other
        val (pieces, controls) = place(home)
        return p.copy(pieces = pieces, controls = controls)
    }

    /**
     * What of a group [apply] keeps clear (window px): its [box] (pieces and controls' hit boxes, against the no-go
     * zones), its controls' hit boxes together ([hit], against the window; none without controls) and its controls'
     * touch areas ([touch], against other groups'), each a [cores] rect with its [pads] round it.
     */
    private class Footprint(val box: PxRect, val hit: PxRect?, val cores: List<PxRect>, val pads: List<Float>) {
        val touch: List<PxRect> = cores.mapIndexed { i, c -> grow(c, pads[i]) }

        /** This scaled by [s] about [m]'s centre and moved [ox], [oy]: the slop round each touch area stays as it is. */
        fun moved(m: PxRect, s: Float, ox: Float, oy: Float): Footprint {
            val cx = m.x + m.width / 2f; val cy = m.y + m.height / 2f
            fun mv(r: PxRect) = PxRect(cx + (r.x - cx) * s + ox, cy + (r.y - cy) * s + oy, r.width * s, r.height * s)
            return Footprint(mv(box), hit?.let(::mv), cores.map(::mv), pads)
        }
    }

    private fun footprint(p: SkinPlacement, pieceAt: List<Int>, controlAt: List<Int>, pieces: List<SkinPiece>, controls: List<PlacedControl>): Footprint? {
        val cs = controlAt.map { controls[it] }
        val hs = cs.map { hit(it) }
        val box = union(pieceAt.map { pieces[it].dst } + hs) ?: return null
        return Footprint(box, union(hs), cs.map { core(it) }, cs.map { pad(it.spec, p.k) })
    }

    /** How much (px²) any of [a] lies on any of [b], added up. */
    private fun touching(a: List<PxRect>, b: List<PxRect>): Float {
        var sum = 0f
        for (x in a) for (y in b) sum += overlap(x, y)
        return sum
    }

    /** How many times [apply] tries the groups against where the later ones are going before it gives that up. */
    private const val PASSES = 4

    /** Each group's pieces and controls in [p] (indexes into its lists), in [groups]' order: the strip's are its strip pieces and its first buttons. */
    private fun members(p: SkinPlacement, groups: List<SkinGroup>): Pair<List<List<Int>>, List<List<Int>>> {
        val stripN = p.spec.frame?.strip?.labels?.size ?: 0
        val pieceAt = groups.map { g ->
            val strip = g.id == SkinGroups.STRIP
            p.pieces.indices.filter { if (strip) p.pieces[it].strip else p.pieces[it].piece in g.pieces }
        }
        val controlAt = groups.map { g ->
            if (g.id == SkinGroups.STRIP) (0 until minOf(stripN, p.controls.size)).toList() else ownControls(p, groups, g, stripN)
        }
        return pieceAt to controlAt
    }

    /**
     * Where each of [p]'s groups stands (window px), by group id: its [GroupBox.box] (pieces and controls' hit areas, as
     * [apply] keeps clear) and the [GroupBox.mid] it scales about. A group with nothing placed is left out.
     */
    internal fun boxes(p: SkinPlacement): Map<String, GroupBox> {
        val groups = SkinGroups.of(p.spec)
        val (pieceAt, controlAt) = members(p, groups)
        val out = LinkedHashMap<String, GroupBox>()
        for ((gi, g) in groups.withIndex()) {
            val box = union(pieceAt[gi].map { p.pieces[it].dst } + controlAt[gi].map { hit(p.controls[it]) }) ?: continue
            val mid = (if (g.id == SkinGroups.STRIP) null else union(pieceAt[gi].map { p.pieces[it].dst })) ?: box
            out[g.id] = GroupBox(box, mid)
        }
        return out
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

    /** [consoleId]'s tweaks for [layoutId] in the [stored] text ([decode]); none when it has none. */
    fun of(stored: String, consoleId: String, layoutId: String): Map<String, SkinTweak> =
        decode(stored)[consoleId]?.get(layoutId).orEmpty()

    /**
     * The overlay's tweaks: per group the [overlayStored] one when the player edited it there, else the [skinStored]
     * one, so a group they never touched in the overlay keeps following the skin layout.
     */
    fun overlayOf(skinStored: String, overlayStored: String, consoleId: String, layoutId: String): Map<String, SkinTweak> =
        of(skinStored, consoleId, layoutId) + of(overlayStored, consoleId, layoutId)

    /**
     * The [stored] text with [tweaks] as [consoleId]'s for [layoutId]; its other layouts and every other console's stay.
     * No tweaks drops the layout, and a console left with none goes too.
     */
    fun withLayout(stored: String, consoleId: String, layoutId: String, tweaks: Map<String, SkinTweak>): String {
        val all = decode(stored)
        val mine = all[consoleId].orEmpty().let { if (tweaks.isEmpty()) it - layoutId else it + (layoutId to tweaks) }
        return encode(if (mine.isEmpty()) all - consoleId else all + (consoleId to mine))
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

/** A placed group's [box] (what may not overlap) and the [mid] it scales about (its pieces; the strip's: its box), window px. */
internal data class GroupBox(val box: PxRect, val mid: PxRect)
