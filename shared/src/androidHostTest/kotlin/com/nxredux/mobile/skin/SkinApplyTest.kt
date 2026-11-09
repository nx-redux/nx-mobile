package com.nxredux.mobile.skin

import com.nxredux.mobile.options.PxRect
import com.nxredux.mobile.ui.nx.SkinPadTouch
import java.io.File
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Player tweaks applied to the built-in packs' placed layouts: groups move and scale, clear of the screens. */
class SkinApplyTest {
    private fun pack(id: String) =
        assertNotNull(SkinPackManifest.parse(File("../androidApp/src/main/assets/skins/$id/skin.json").readText()))
    private val carbon = pack("nx-carbon")

    private fun menu(pack: SkinPackManifest = carbon) = assertNotNull(SkinChoice.placeMenu(pack, 1080, 2520, 420)).also {
        assertEquals("stacked_menu", it.spec.id)
    }

    private fun group(p: SkinPlacement, vararg ids: String) = SkinGroups.of(p.spec).single { it.controls.toSet() == ids.toSet() }

    private fun pieces(p: SkinPlacement, g: SkinGroup) = p.pieces.filter { it.piece in g.pieces }

    private fun controls(p: SkinPlacement, g: SkinGroup) = p.controls.filter { it.spec.id in g.controls }

    private fun union(rs: List<PxRect>): PxRect {
        val l = rs.minOf { it.x }; val t = rs.minOf { it.y }
        return PxRect(l, t, rs.maxOf { it.x + it.width } - l, rs.maxOf { it.y + it.height } - t)
    }

    /** [a] and [b] overlap by more than float slack. */
    private fun hits(a: PxRect, b: PxRect, e: Float = 0.01f) =
        a.x + e < b.x + b.width && b.x + e < a.x + a.width && a.y + e < b.y + b.height && b.y + e < a.y + a.height

    private fun hit(c: PlacedControl): PxRect {
        val hw = if (c.spec.shape == ControlShape.RECT) c.spec.hw else c.spec.r
        val hh = if (c.spec.shape == ControlShape.RECT) c.spec.hh else c.spec.r
        return PxRect(c.x - hw, c.y - hh, 2f * hw, 2f * hh)
    }

    /** The game's touch area for [c] (SkinPadTouch's hit test): a rect's or d-pad's hit box and 22 picture px round it, a stick's ring 1.25x, a round button's own circle. */
    private fun zone(c: PlacedControl, k: Float): PxRect {
        val h = hit(c)
        val m = when (c.spec.shape) {
            ControlShape.RECT, ControlShape.DPAD -> 22f * k
            ControlShape.STICK -> c.spec.r * 0.25f
            ControlShape.CIRCLE -> 0f
        }
        return PxRect(h.x - m, h.y - m, h.width + 2 * m, h.height + 2 * m)
    }

    private fun groupControls(p: SkinPlacement, g: SkinGroup) =
        if (g.id == SkinGroups.STRIP) p.controls.take(g.controls.size) else controls(p, g)

    /** A group's touch areas. */
    private fun zones(p: SkinPlacement, g: SkinGroup) = groupControls(p, g).map { zone(it, p.k) }

    /** A group's controls' hit boxes, together. */
    private fun hits(p: SkinPlacement, g: SkinGroup) = union(groupControls(p, g).map { hit(it) })

    /** How much two groups' touch areas lie on each other. */
    private fun touching(p: SkinPlacement, a: SkinGroup, q: SkinPlacement = p, b: SkinGroup) =
        zones(p, a).sumOf { x -> zones(q, b).sumOf { y -> overlap(x, y).toDouble() } }.toFloat()

    /** A screen area's middle, off-limits on the skin: all but 10% of it from each edge. */
    private fun inner(r: PxRect) = PxRect(r.x + 0.1f * r.width, r.y + 0.1f * r.height, 0.8f * r.width, 0.8f * r.height)

    /** [p]'s [i]th screen area ([SkinTweaks.screenAreas]). */
    private fun area(p: SkinPlacement, i: Int = 0) = SkinTweaks.screenAreas(p)[i]

    /** [p] with its frame strip (its buttons and labels) moved up by [up] window px. */
    private fun stripUp(p: SkinPlacement, up: Float): SkinPlacement {
        val ids = assertNotNull(p.spec.frame?.strip).labels.map { it.id }.toSet()
        return p.copy(
            controls = p.controls.map { if (it.spec.id in ids) it.copy(y = it.y - up) else it },
            pieces = p.pieces.map { if (it.strip) it.copy(dst = it.dst.copy(y = it.dst.y - up)) else it },
        )
    }

    /** The top of [p]'s frame strip's touch row, window px. */
    private fun stripTop(p: SkinPlacement): Float {
        val ids = assertNotNull(p.spec.frame?.strip).labels.map { it.id }.toSet()
        return p.controls.filter { it.spec.id in ids }.minOf { it.y - it.spec.hh }
    }

    private fun grip() = assertNotNull(SkinChoice.place(carbon, 2420, 1080, 420, tablet = false, ds = false, picture = 240f to 160f, variant = SkinVariant.GBA))

    /** A group's box: its pieces and its controls' hit areas (the strip's: its buttons). */
    private fun box(p: SkinPlacement, g: SkinGroup): PxRect {
        val ps = if (g.id == SkinGroups.STRIP) p.pieces.filter { it.strip } else pieces(p, g)
        val cs = if (g.id == SkinGroups.STRIP) p.controls.take(g.controls.size) else controls(p, g)
        return union(ps.map { it.dst } + cs.map { hit(it) })
    }

    private fun overlap(a: PxRect, b: PxRect): Float {
        val w = minOf(a.x + a.width, b.x + b.width) - maxOf(a.x, b.x)
        val h = minOf(a.y + a.height, b.y + b.height) - maxOf(a.y, b.y)
        return if (w > 0f && h > 0f) w * h else 0f
    }

    private fun near(a: Float, b: Float, what: String, e: Float = 0.01f) = assertTrue(abs(a - b) < e, "$what: $a vs $b")

    @Test
    fun apply_moves_a_group_and_its_controls() {
        val p = menu()
        val g = group(p, "SELECT", "START")
        val out = SkinTweaks.apply(p, mapOf(g.id to SkinTweak(dx = 40f)), 1080f, 2520f)
        for ((a, b) in controls(p, g).zip(controls(out, g))) {
            near(b.x, a.x + 40f * p.k, "${a.spec.id} x"); near(b.y, a.y, "${a.spec.id} y")
        }
        val before = pieces(p, g); val after = pieces(out, g)
        assertTrue(before.isNotEmpty())
        for ((a, b) in before.zip(after)) near(b.dst.x, a.dst.x + 40f * p.k, "piece left")
        // the rest stays put
        val rest = p.controls.filter { it.spec.id !in g.controls }
        assertEquals(rest, out.controls.filter { it.spec.id !in g.controls })
    }

    @Test
    fun apply_scales_about_the_centre() {
        // MENU has room round it (the face buttons grown 1.2 would reach SELECT START: see groups_never_overlap)
        val p = menu()
        val g = group(p, "MENU")
        val out = SkinTweaks.apply(p, mapOf(g.id to SkinTweak(scale = 1.2f)), 1080f, 2520f)
        val a = union(pieces(p, g).map { it.dst }); val b = union(pieces(out, g).map { it.dst })
        near(b.x + b.width / 2f, a.x + a.width / 2f, "centre x"); near(b.y + b.height / 2f, a.y + a.height / 2f, "centre y")
        near(b.width, a.width * 1.2f, "width")
        for ((c0, c1) in controls(p, g).zip(controls(out, g))) {
            near(c1.spec.r, c0.spec.r * 1.2f, "${c0.spec.id} r")
            near(c1.x - (a.x + a.width / 2f), (c0.x - (a.x + a.width / 2f)) * 1.2f, "${c0.spec.id} x")
        }
    }

    @Test
    fun apply_never_enters_the_no_go_zone() {
        // sideways, the face buttons may move far enough left to reach the screen's middle: they stop short of it
        val p = grip()
        val zones = SkinTweaks.noGo(p)
        assertTrue(zones.isNotEmpty())
        val face = SkinGroups.of(p.spec).single { "A" in it.controls && "B" in it.controls }
        val start = box(p, face)
        assertTrue(zones.any { hits(PxRect(start.x - 10000f * p.k, start.y, start.width + 10000f * p.k, start.height), it) }, "the full move crosses the screen")
        val r = box(SkinTweaks.apply(p, mapOf(face.id to SkinTweak(dx = -10000f)), 2420f, 1080f), face)
        for (z in zones) assertTrue(!hits(r, z), "$r hits $z")
        assertTrue(r.x < start.x - 10f, "moved some: $r")
        // the strip pushed up goes into the screen's edge, not its middle
        val q = assertNotNull(SkinChoice.place(carbon, 1080, 2520, 420, tablet = false, ds = false, picture = 240f to 160f, variant = SkinVariant.GBA))
        val strip = SkinGroups.of(q.spec).single { it.id == SkinGroups.STRIP }
        fun top(x: SkinPlacement) = x.controls.filter { it.spec.id in strip.controls }.minOf { it.y - it.spec.hh }
        val up = SkinTweaks.apply(q, mapOf(strip.id to SkinTweak(dy = -2000f)), 1080f, 2520f)
        assertTrue(top(up) < top(q) - 10f, "${top(up)} vs ${top(q)}")
        for (z in SkinTweaks.noGo(q)) assertTrue(!hits(box(up, strip), z), "${box(up, strip)} in $z")
        val down = SkinTweaks.apply(q, mapOf(strip.id to SkinTweak(dy = 30f)), 1080f, 2520f)
        near(top(down), top(q) + 30f * q.k, "strip down")
        assertTrue(down.pieces.filter { it.strip }.zip(q.pieces.filter { it.strip }).all { (a, b) -> abs(a.dst.y - b.dst.y - 30f * q.k) < 0.01f })
    }

    @Test
    fun apply_keeps_a_group_inside_the_window() {
        val w = 2420; val h = 1080   // the Fold's cover, sideways
        val p = grip()
        assertEquals(SkinFamily.GRIP, p.spec.family)
        val face = SkinGroups.of(p.spec).single { "A" in it.controls && "B" in it.controls }
        val out = SkinTweaks.apply(p, mapOf(face.id to SkinTweak(dx = 10000f, scale = 1.25f)), w.toFloat(), h.toFloat())
        val start = box(p, face)
        // the buttons' hit boxes stay in (their art may reach past the edge)
        for (r in controls(out, face).map { hit(it) }) {
            assertTrue(r.x >= -0.01f && r.x + r.width <= w + 0.01f, "$r in 0..$w")
            assertTrue(r.y >= -0.01f && r.y + r.height <= h + 0.01f, "$r in 0..$h")
        }
        assertTrue(box(out, face).x > start.x + 1f, "moved some")
        near(hits(out, face).x + hits(out, face).width, w.toFloat(), "against the edge", e = 0.5f)
    }

    @Test
    fun the_strip_never_crosses_the_fold() {
        // half-folded, the strip is under the screen above the fold: pushed down it stays above the hinge band
        val p = assertNotNull(SkinChoice.place(carbon, 1080, 2520, 420, tablet = false, ds = false, picture = 240f to 160f, hingeY = 1200f, variant = SkinVariant.GBA))
        assertEquals(SkinFamily.FLEX, p.spec.family, p.spec.id)
        val f = assertNotNull(p.spec.frame)
        assertTrue(f.hinge >= 0 && f.strip != null, p.spec.id)
        val strip = SkinGroups.of(p.spec).single { it.id == SkinGroups.STRIP }
        val zones = SkinTweaks.noGo(p)
        val band = zones.last()
        val start = box(p, strip)
        assertTrue(start.y + start.height <= band.y + 0.01f, "$start above $band")
        for (dy in listOf(300f, 2000f)) {
            val r = box(SkinTweaks.apply(p, mapOf(strip.id to SkinTweak(dy = dy)), 1080f, 2520f), strip)
            assertTrue(r.y + r.height <= band.y + 0.01f, "dy $dy: $r crossed $band")
        }
        // nor into the middle of the screen above it
        val up = box(SkinTweaks.apply(p, mapOf(strip.id to SkinTweak(dy = -2000f)), 1080f, 2520f), strip)
        assertTrue(!hits(up, inner(area(p))), "$up in the screen's middle")
    }

    @Test
    fun touch_areas_never_overlap() {
        val p = menu()
        val ss = group(p, "SELECT", "START")
        val face = group(p, "A", "B", "X", "Y")
        assertEquals(0f, touching(p, ss, b = face), "they start apart")
        val toward = mapOf(ss.id to SkinTweak(dx = 1000f, dy = 1000f), face.id to SkinTweak(dx = -1000f, dy = -1000f, scale = 1.25f))
        val out = SkinTweaks.apply(p, toward, 1080f, 2520f)
        assertTrue(touching(out, ss, b = face) < 0.5f, "${zones(out, ss)} on ${zones(out, face)}")
        assertTrue(box(out, ss) != box(p, ss) && box(out, face) != box(p, face), "both moved some")
        // the same tweaks in another order give the same result (groups go in the layout's order)
        assertEquals(out, SkinTweaks.apply(p, toward.entries.reversed().associate { it.key to it.value }, 1080f, 2520f))
    }

    @Test
    fun a_group_moves_into_the_room_a_later_group_left() {
        // the d-pad (earlier in the layout) and the face buttons (later) sit side by side: with the face buttons moved
        // right to the window's edge, the d-pad pushed right reaches into where they were and stops against them there
        val p = menu()
        val dpad = group(p, "DPAD")
        val face = group(p, "A", "B", "X", "Y")
        assertTrue(SkinGroups.of(p.spec).indexOf(dpad) < SkinGroups.of(p.spec).indexOf(face), "the d-pad goes first")
        val home = hits(p, face)
        val room = 1080f - (home.x + home.width)   // how far the face buttons can go right
        assertTrue(room > 10f, "room $room")
        val faceZoneHome = union(zones(p, face))
        for (faceDx in listOf(room / p.k, 1000f)) {   // just to the edge, or further (stopped by the window)
            val out = SkinTweaks.apply(p, mapOf(dpad.id to SkinTweak(dx = 1000f), face.id to SkinTweak(dx = faceDx)), 1080f, 2520f)
            val f = union(zones(out, face)); val d = union(zones(out, dpad))
            near(hits(out, face).x, home.x + room, "face $faceDx moved to the edge")
            assertTrue(d.x + d.width > faceZoneHome.x + 1f, "face $faceDx: the d-pad $d reaches the face buttons' home $faceZoneHome")
            assertTrue(abs(d.x + d.width - f.x) < 0.5f, "face $faceDx: the d-pad stops against them: $d, $f")
            assertTrue(touching(out, dpad, b = face) < 0.5f, "$d on $f")
        }
    }

    @Test
    fun every_group_pushed_every_way_stays_clear() {
        // controls drawn beside their piece (START on the Dreamcast, N64, GameCube pads) count with their group
        val cases = listOf(SkinVariant.GBA, SkinVariant.DC, SkinVariant.N64, SkinVariant.GC).flatMap { v ->
            listOf(1080 to 2520, 2420 to 1080).map { (w, h) -> Triple(v, w, h) }
        }
        val ways = listOf(-1f to 0f, 1f to 0f, 0f to -1f, 0f to 1f, -1f to -1f, 1f to -1f, -1f to 1f, 1f to 1f)
        for ((v, w, h) in cases) {
            val p = assertNotNull(SkinChoice.place(carbon, w, h, 420, tablet = false, ds = false, picture = 320f to 240f, variant = v), "$v $w")
            val zones = SkinTweaks.noGo(p)
            val groups = SkinGroups.of(p.spec)
            for (g in groups) for ((ux, uy) in ways) {
                val out = SkinTweaks.apply(p, mapOf(g.id to SkinTweak(dx = ux * 5000f, dy = uy * 5000f, scale = 1.25f)), w.toFloat(), h.toFloat())
                val before = box(p, g); val after = box(out, g)
                for (z in zones) assertTrue(overlap(after, z) <= overlap(before, z) + 0.5f, "${p.spec.id} ${g.id} ($ux,$uy): $after in $z")
                val h0 = hits(p, g); val h1 = hits(out, g)
                // past the window's edge by no more than float noise
                val e = 0.05f
                assertTrue(h1.x >= minOf(h0.x, 0f) - e && h1.x + h1.width <= maxOf(h0.x + h0.width, w.toFloat()) + e, "${p.spec.id} ${g.id} x $h1")
                assertTrue(h1.y >= minOf(h0.y, 0f) - e && h1.y + h1.height <= maxOf(h0.y + h0.height, h.toFloat()) + e, "${p.spec.id} ${g.id} y $h1")
                for (o in groups) if (o != g) assertTrue(touching(out, g, b = o) <= touching(p, g, b = o) + 0.5f, "${p.spec.id} ${g.id} on ${o.id}")
            }
        }
    }

    @Test
    fun all_groups_pushed_at_once_stay_off_each_other() {
        // every group moved and grown together, each way and toward each other: no two end on each other
        val ways = listOf(-1f to 0f, 1f to 0f, 0f to -1f, 0f to 1f, -1f to -1f, 1f to 1f)
        for (v in listOf(SkinVariant.GBA, SkinVariant.N64)) for ((w, h) in listOf(1080 to 2520, 2420 to 1080)) {
            val p = assertNotNull(SkinChoice.place(carbon, w, h, 420, tablet = false, ds = false, picture = 320f to 240f, variant = v))
            val groups = SkinGroups.of(p.spec)
            val tweakSets = ways.map { (ux, uy) -> groups.associate { it.id to SkinTweak(ux * 5000f, uy * 5000f, 1.25f) } } +
                // each half toward the other: the first groups one way, the rest the other
                ways.map { (ux, uy) -> groups.withIndex().associate { (i, g) -> val s = if (i < groups.size / 2) 1f else -1f; g.id to SkinTweak(s * ux * 5000f, s * uy * 5000f, 1.25f) } }
            for (t in tweakSets) {
                val out = SkinTweaks.apply(p, t, w.toFloat(), h.toFloat())
                for (a in groups) for (b in groups) if (a != b) assertTrue(touching(out, a, b = b) <= touching(p, a, b = b) + 0.5f, "${p.spec.id} $w ${a.id} on ${b.id}: $t")
            }
        }
    }

    @Test
    fun empty_tweaks_return_the_placement_unchanged() {
        val p = menu()
        assertEquals(p, SkinTweaks.apply(p, emptyMap(), 1080f, 2520f))
    }

    @Test
    fun apply_ignores_groups_not_in_the_layout() {
        val p = menu()
        assertEquals(p, SkinTweaks.apply(p, mapOf("NOPE+GONE" to SkinTweak(40f, 40f, 1.2f)), 1080f, 2520f))
    }

    @Test
    fun the_same_tweaks_give_the_same_result_on_every_colour_pack() {
        val a = menu(carbon)
        val b = menu(pack("nx-plasma"))
        val tweaks = SkinGroups.of(a.spec).associate { it.id to SkinTweak(dx = 20f, dy = -30f, scale = 1.1f) }
        val ca = SkinTweaks.apply(a, tweaks, 1080f, 2520f).controls
        val cb = SkinTweaks.apply(b, tweaks, 1080f, 2520f).controls
        assertEquals(ca.map { Triple(it.spec.id, it.x, it.y) }, cb.map { Triple(it.spec.id, it.x, it.y) })
        assertTrue(ca != a.controls)
    }

    @Test
    fun apply_ignores_a_stored_scale_that_is_not_a_size() {
        val p = menu()
        val g = group(p, "MENU")
        for (s in listOf(0f, -2f, Float.NaN)) assertEquals(p, SkinTweaks.apply(p, mapOf(g.id to SkinTweak(scale = s)), 1080f, 2520f), "$s")
    }
    @Test
    fun each_screens_middle_is_off_limits_not_its_frame() {
        // the large DS layout draws the touch screen's frame as a piece: it is no limit (each screen's middle is). L
        // pushed up and a little right (clear of the touch shortcuts) goes over the top screen's edge and stops at its middle
        val p = assertNotNull(SkinChoice.place(carbon, 1248, 1972, 420, tablet = false, ds = true))
        assertEquals("large_ds", p.spec.id)
        val frames = p.pieces.filter { it.piece >= 0 && p.spec.pieces[it.piece].glass != null }.map { it.dst }
        assertTrue(frames.isNotEmpty(), "large_ds draws a screen piece")
        val top = assertNotNull(p.glass(GlassScreen.TOP))
        val l = group(p, "L")
        val mid = inner(area(p, p.glasses.indexOf(top)))
        val out = SkinTweaks.apply(p, mapOf(l.id to SkinTweak(dx = 2000f, dy = -10000f)), 1248f, 1972f)
        val after = box(out, l)
        assertTrue(after.x + after.width > mid.x + 1f, "$after under the middle $mid")
        assertTrue(hits(after, top.rect), "$after over the screen's edge")
        near(after.y, mid.y + mid.height, "stopped at the screen's middle", e = 0.5f)
    }

    @Test
    fun buttons_in_no_group_keep_their_touch_areas() {
        // the large DS layout's touch shortcuts sit in no group: SELECT START pushed up stays off them
        val p = assertNotNull(SkinChoice.place(carbon, 1248, 1972, 420, tablet = false, ds = true))
        val ss = group(p, "SELECT", "START")
        val loose = p.controls.filter { it.spec.id.startsWith("TOUCH_") }.map { zone(it, p.k) }
        assertTrue(loose.isNotEmpty())
        fun on(q: SkinPlacement) = zones(q, ss).sumOf { a -> loose.sumOf { overlap(a, it).toDouble() } }
        val out = SkinTweaks.apply(p, mapOf(ss.id to SkinTweak(dy = -10000f)), 1248f, 1972f)
        assertTrue(on(out) <= on(p) + 0.5, "${on(out)} vs ${on(p)}")
    }

    @Test
    fun no_go_is_each_screens_middle_and_the_hinge() {
        val ds = assertNotNull(SkinChoice.place(carbon, 1248, 1972, 420, tablet = false, ds = true))
        assertEquals(ds.glasses.map { inner(it.rect) }, SkinTweaks.noGo(ds))
        val fold = assertNotNull(SkinChoice.place(carbon, 1080, 2520, 420, tablet = false, ds = false, picture = 240f to 160f, hingeY = 1200f, variant = SkinVariant.GBA))
        val z = SkinTweaks.noGo(fold)
        assertEquals(fold.glasses.map { inner(it.rect) }, z.dropLast(1))
        assertTrue(z.last().y < 1200f && z.last().y + z.last().height > 1200f, "the hinge band ${z.last()}")
        assertEquals(emptyList(), SkinTweaks.noGo(fold, TweakRules.OVERLAY))
    }

    @Test
    fun touch_zone_is_the_games_hit_area() {
        val n64 = assertNotNull(SkinChoice.place(carbon, 2420, 1080, 420, tablet = false, ds = false, picture = 320f to 240f, variant = SkinVariant.N64))
        val shapes = (n64.controls + menu().controls).map { it.spec.shape }.toSet()
        assertEquals(ControlShape.entries.toSet(), shapes, "every shape tried")
        // probed with the game's own hit test, each control alone: just inside each edge of the zone is the control's,
        // just past it is not (on the axes through its centre, where a round button's circle meets its square)
        val touch = SkinPadTouch({}, { _, _ -> }, {})
        for ((p, c) in n64.controls.map { n64 to it } + menu().let { m -> m.controls.map { m to it } }) {
            touch.placement = p.copy(controls = listOf(c))
            if (!touch.hits(c.x, c.y)) continue   // a control the game takes no touch on
            val z = SkinTweaks.touchZone(c, p.k)
            val e = 0.5f
            for ((x, y) in listOf(z.x to c.y, z.x + z.width to c.y, c.x to z.y, c.x to z.y + z.height)) {
                val inX = x + (if (x < c.x) e else if (x > c.x) -e else 0f); val inY = y + (if (y < c.y) e else if (y > c.y) -e else 0f)
                val outX = x + (if (x < c.x) -e else if (x > c.x) e else 0f); val outY = y + (if (y < c.y) -e else if (y > c.y) e else 0f)
                assertTrue(touch.hits(inX, inY), "${c.spec.id} ($inX, $inY) in $z")
                assertTrue(!touch.hits(outX, outY), "${c.spec.id} ($outX, $outY) past $z")
            }
        }
    }

    @Test
    fun a_button_reaches_the_top_edge_in_landscape() {
        val p = grip()
        val r = group(p, "R")
        val out = SkinTweaks.apply(p, mapOf(r.id to SkinTweak(dy = -10000f)), 2420f, 1080f)
        assertTrue(abs(hits(out, r).y) <= 1f, "R's top at ${hits(out, r).y}")
    }

    @Test
    fun a_button_enters_a_screen_up_to_ten_percent() {
        val p = grip()
        val r = area(p)
        val face = group(p, "A", "B", "X", "Y")
        assertTrue(box(p, face).x > r.x + r.width, "starts right of the screen")
        val out = SkinTweaks.apply(p, mapOf(face.id to SkinTweak(dx = -10000f)), 2420f, 1080f)
        assertTrue(abs(box(out, face).x - (r.x + r.width - 0.1f * r.width)) <= 1f, "${box(out, face)} vs $r")
    }

    @Test
    fun a_button_on_a_screens_edge_takes_its_touch_before_the_game() {
        // the face buttons moved onto the screen's edge: a touch on a button there presses it and never reaches the game
        val p = grip()
        val r = area(p)
        val face = group(p, "A", "B", "X", "Y")
        val out = SkinTweaks.apply(p, mapOf(face.id to SkinTweak(dx = -10000f)), 2420f, 1080f)
        val onGlass = controls(out, face).filter { it.x > r.x && it.x < r.x + r.width && it.y > r.y && it.y < r.y + r.height }
        assertTrue(onGlass.isNotEmpty(), "a face button over the screen")
        val pressed = mutableListOf<com.nxredux.mobile.ui.nx.PadEvent>()
        val touch = SkinPadTouch({ pressed += it }, { _, _ -> }, {})
        touch.placement = out
        val sent = mutableListOf<String>()
        val pass = com.nxredux.mobile.ui.nx.SkinGlassPass { x, y, down -> sent += "$x,$y,$down" }
        for ((n, c) in onGlass.withIndex()) {
            pass.down(n.toLong(), c.x, c.y, onCap = touch.press(n.toLong(), c.x, c.y))
            pass.up(n.toLong()); touch.release(n.toLong())
        }
        assertTrue(pressed.isNotEmpty(), "pressed")
        assertEquals(emptyList(), sent, "none passed to the game")
        // a touch on the screen off every button still reaches it
        val mid = r.x + r.width / 2f to r.y + r.height / 2f
        pass.down(9, mid.first, mid.second, onCap = touch.press(9, mid.first, mid.second))
        assertEquals(listOf("${mid.first},${mid.second},true"), sent)
    }

    @Test
    fun a_screen_area_is_its_glass_less_the_strip_row_over_it() {
        // the Fold's cover, arcade: NX Carbon's strip sits under the glass, so the area is the glass whole
        for ((w, h) in listOf(1248 to 1972, 1972 to 1248)) {
            val p = assertNotNull(SkinChoice.place(carbon, w, h, 420, tablet = false, ds = false, picture = 320f to 224f, variant = SkinVariant.ARCADE))
            assertEquals(p.glasses.map { it.rect }, SkinTweaks.screenAreas(p), "${w}x$h")
            assertTrue(stripTop(p) >= p.glasses.first().rect.let { it.y + it.height }, "${w}x$h: the strip under the glass")
        }
        // a strip drawn over the glass's foot: the area stops at its top, and the 10% is of the area, not the glass
        val p0 = assertNotNull(SkinChoice.place(carbon, 1248, 1972, 420, tablet = false, ds = false, picture = 320f to 224f, variant = SkinVariant.ARCADE))
        val g = p0.glasses.first().rect
        val p = stripUp(p0, stripTop(p0) - (g.y + g.height - 80f))
        val r = area(p)
        near(r.y + r.height, stripTop(p), "the area's foot at the strip's top", e = 1f)
        assertEquals(PxRect(g.x, g.y, g.width, r.height), r)
        assertTrue(r.height < g.height - 70f, "$r less the strip")
        assertEquals(inner(r), SkinTweaks.noGo(p).first())
        // a group sent into it from the side still stops at 10% of the area's width
        val q0 = grip()
        val qg = q0.glasses.first().rect
        val q = stripUp(q0, stripTop(q0) - (qg.y + qg.height - 80f))
        val qa = area(q)
        val face = group(q, "A", "B", "X", "Y")
        assertTrue(box(q, face).let { it.y + it.height < inner(qa).let { m -> m.y + m.height } }, "the face buttons level with the area's middle")
        val moved = box(SkinTweaks.apply(q, mapOf(face.id to SkinTweak(dx = -10000f)), 2420f, 1080f), face)
        near(moved.x, qa.x + qa.width - 0.1f * qa.width, "stopped 10% in", e = 1f)
    }

    @Test
    fun touch_zones_never_overlap_but_art_may() {
        val p = menu()
        val dpad = group(p, "DPAD")
        val face = group(p, "A", "B", "X", "Y")
        val out = SkinTweaks.apply(p, mapOf(face.id to SkinTweak(dx = -1000f)), 1080f, 2520f)
        assertTrue(touching(out, dpad, b = face) <= 0.01f, "touch areas")
        assertTrue(overlap(union(pieces(out, dpad).map { it.dst }), union(pieces(out, face).map { it.dst })) > 0f, "the art overlaps")
        assertTrue(abs(union(zones(out, dpad)).let { it.x + it.width } - union(zones(out, face)).x) < 0.5f, "against each other")
    }

    @Test
    fun a_default_inside_the_inner_area_is_not_moved_and_can_leave() {
        // a layout that puts MENU in the screen's middle, and L's and L2's touch areas on each other (as the grip does)
        val p0 = grip()
        val menuG = group(p0, "MENU")
        val mid = inner(area(p0))
        val b0 = box(p0, menuG)
        val sx = mid.x + mid.width / 2f - (b0.x + b0.width / 2f); val sy = mid.y + mid.height / 2f - (b0.y + b0.height / 2f)
        val p = p0.copy(
            pieces = p0.pieces.map { if (it.piece in menuG.pieces) it.copy(dst = it.dst.copy(x = it.dst.x + sx, y = it.dst.y + sy)) else it },
            controls = p0.controls.map { if (it.spec.id in menuG.controls) it.copy(x = it.x + sx, y = it.y + sy) else it },
        )
        val l = group(p, "L"); val l2 = group(p, "L2")
        assertTrue(touching(p, l, b = l2) > 0f, "L and L2 start on each other")
        assertTrue(overlap(box(p, menuG), mid) > 0f, "MENU starts in the middle")
        // others moved: neither is touched
        val others = SkinTweaks.apply(p, mapOf(group(p, "DPAD").id to SkinTweak(dx = 50f), group(p, "R").id to SkinTweak(dy = 40f)), 2420f, 1080f)
        assertEquals(controls(p, menuG), controls(others, menuG))
        assertEquals(controls(p, l) + controls(p, l2), controls(others, l) + controls(others, l2))
        // each moves away in full
        // just past the middle's right edge
        val out = (mid.x + mid.width - box(p, menuG).x + 4f) / p.k
        val away = SkinTweaks.apply(p, mapOf(menuG.id to SkinTweak(dx = out), l.id to SkinTweak(dy = -60f)), 2420f, 1080f)
        for ((a, b) in controls(p, menuG).zip(controls(away, menuG))) near(b.x, a.x + out * p.k, "MENU x")
        assertEquals(0f, overlap(box(away, menuG), mid), "MENU left the middle")
        for ((a, b) in controls(p, l).zip(controls(away, l))) near(b.y, a.y - 60f * p.k, "L y")
        // and L goes no further onto L2
        val onto = SkinTweaks.apply(p, mapOf(l.id to SkinTweak(dx = 300f)), 2420f, 1080f)
        assertTrue(touching(onto, l, b = l2) <= touching(p, l, b = l2) + 0.5f, "L onto L2")
    }

    @Test
    fun old_tweaks_land_where_they_did() {
        // a tweak within half the group's size and scale 0.8..1.25, clear of everything, lands in full
        val cases = listOf(
            menu() to mapOf("MENU" to SkinTweak(dx = 0.3f * 116f), "SELECT+START" to SkinTweak(dy = -0.3f * 252f, scale = 1.1f)),
            grip() to mapOf("R" to SkinTweak(dy = -0.3f * 160f), "DPAD" to SkinTweak(dy = 40f, scale = 0.9f)),
        )
        for ((p, t) in cases) {
            val out = SkinTweaks.apply(p, t, if (p.spec.id == "grip_gba") 2420f else 1080f, if (p.spec.id == "grip_gba") 1080f else 2520f, minTouchPx = phoneFloor)
            for ((id, tw) in t) {
                val g = SkinGroups.of(p.spec).single { it.id == id }
                val m = union(pieces(p, g).map { it.dst })
                val cx = m.x + m.width / 2f; val cy = m.y + m.height / 2f
                for ((a, b) in controls(p, g).zip(controls(out, g))) {
                    near(b.x, cx + (a.x - cx) * tw.scale + tw.dx * p.k, "$id ${a.spec.id} x")
                    near(b.y, cy + (a.y - cy) * tw.scale + tw.dy * p.k, "$id ${a.spec.id} y")
                }
            }
        }
    }

    @Test
    fun shrinking_stops_at_the_touch_floor() {
        val p = menu()
        val face = group(p, "A", "B", "X", "Y")
        val out = SkinTweaks.apply(p, mapOf(face.id to SkinTweak(scale = 0.05f)), 1080f, 2520f, minTouchPx = 84f)
        val side = controls(out, face).minOf { minOf(hit(it).width, hit(it).height) }
        assertTrue(side >= 84f - 1f && side < 84f + 1f, "smallest side $side")
        // a group already under the floor shrinks no further than 0.8, the smallest a tweak could always take
        val under = SkinTweaks.apply(p, mapOf(face.id to SkinTweak(scale = 0.5f)), 1080f, 2520f, minTouchPx = 1000f)
        for ((a, b) in controls(p, face).zip(controls(under, face))) near(b.spec.r, a.spec.r * 0.8f, "${a.spec.id} r")
    }

    /** 32 dp in window px on a 420 dpi phone, as the game passes it. */
    private val phoneFloor = 32f * 420f / 160f

    @Test
    fun the_touch_floor_is_on_the_touch_area() {
        // a shoulder's touch area is its hit box and its slop: the floor holds that, so the shoulder still shrinks
        val p = grip()
        val r = group(p, "R")
        val out = SkinTweaks.apply(p, mapOf(r.id to SkinTweak(scale = 0.05f)), 2420f, 1080f, minTouchPx = phoneFloor)
        val z = controls(out, r).map { zone(it, out.k) }
        val side = z.minOf { minOf(it.width, it.height) }
        assertTrue(abs(side - phoneFloor) < 1f, "smallest touch side $side vs $phoneFloor")
        assertTrue(controls(out, r).first().spec.hh < controls(p, r).first().spec.hh * 0.8f, "shrank past 0.8")
    }

    @Test
    fun old_shrinks_keep_their_size_under_the_game_floor() {
        // a shoulder at 0.8, as the old cap let a player save it, with the floor the game uses
        val p = grip()
        for (id in listOf("R", "L2")) {
            val g = group(p, id)
            val out = SkinTweaks.apply(p, mapOf(g.id to SkinTweak(scale = 0.8f)), 2420f, 1080f, minTouchPx = phoneFloor)
            for ((a, b) in controls(p, g).zip(controls(out, g))) near(b.spec.hh, a.spec.hh * 0.8f, "$id ${a.spec.id} hh")
        }
    }

    @Test
    fun a_tweak_past_a_neighbour_lands_there_when_the_spot_is_free() {
        // MENU up past SELECT START to just above them (the screen's edge, clear of its middle): it goes there whole
        val p = menu()
        val g = group(p, "MENU")
        val ss = group(p, "SELECT", "START")
        val top = union(zones(p, ss)).y
        val dy = top - 4f - (box(p, g).y + box(p, g).height)
        val out = SkinTweaks.apply(p, mapOf(g.id to SkinTweak(dy = dy / p.k)), 1080f, 2520f)
        near(box(out, g).y, box(p, g).y + dy, "MENU above SELECT START")
        assertEquals(0f, touching(out, g, b = ss))
    }

    @Test
    fun a_kept_group_never_gives_way() {
        // the d-pad (earlier in the layout) kept where its tweak puts it; the face buttons' tweak onto it stops short
        val p = menu()
        val dpad = group(p, "DPAD"); val face = group(p, "A", "B", "X", "Y")
        val t = mapOf(dpad.id to SkinTweak(dx = 150f), face.id to SkinTweak(dx = -100f))
        val out = SkinTweaks.apply(p, t, 1080f, 2520f, TweakRules.OVERLAY, keep = setOf(dpad.id))
        near(box(out, dpad).x, box(p, dpad).x + 150f * p.k, "the d-pad where its tweak puts it")
        val d = union(zones(out, dpad)); val f = union(zones(out, face))
        near(f.x, d.x + d.width, "the face buttons against it", e = 0.5f)
        // kept or not, never on each other
        assertTrue(touching(out, dpad, b = face) < 0.5f)
    }

    @Test
    fun a_walk_starts_from_where_the_group_stands() {
        // MENU above SELECT START, pushed on up: it goes from there to the screen's middle, not from home into SELECT START
        val p = menu()
        val g = group(p, "MENU")
        val ss = group(p, "SELECT", "START")
        val dy = union(zones(p, ss)).y - 4f - (box(p, g).y + box(p, g).height)
        val now = mapOf(g.id to SkinTweak(dy = dy / p.k))
        val out = SkinTweaks.apply(p, mapOf(g.id to SkinTweak(dy = -10000f)), 1080f, 2520f, from = now)
        near(box(out, g).y, inner(area(p)).let { it.y + it.height }, "stopped at the screen's middle", e = 0.5f)
    }

    @Test
    fun a_very_long_move_never_passes_over_a_group() {
        // a stored move far past the window is walked in small steps all the same
        val p = menu()
        val g = group(p, "MENU")
        val ss = group(p, "SELECT", "START")
        val out = SkinTweaks.apply(p, mapOf(g.id to SkinTweak(dy = -1e7f)), 1080f, 2520f, TweakRules.OVERLAY)
        assertTrue(union(zones(out, g)).y >= union(zones(p, ss)).let { it.y + it.height } - 0.5f, "${union(zones(out, g))} passed SELECT START")
        assertTrue(touching(out, g, b = ss) < 0.5f)
        // nor does a kept group: only its end spot is held against the kept groups alone
        val kept = SkinTweaks.apply(p, mapOf(g.id to SkinTweak(dy = -1e7f)), 1080f, 2520f, TweakRules.OVERLAY, keep = setOf(g.id))
        assertTrue(union(zones(kept, g)).y >= union(zones(p, ss)).let { it.y + it.height } - 0.5f, "kept: ${union(zones(kept, g))} passed SELECT START")
    }

    @Test
    fun a_kept_groups_end_spot_never_lands_on_an_untweaked_group() {
        // MENU kept and sent straight onto SELECT START, which has no tweak and so never gives way: it stops short
        val p = menu()
        val g = group(p, "MENU")
        val ss = group(p, "SELECT", "START")
        val a = union(zones(p, g)); val b = union(zones(p, ss))
        val dx = b.x + b.width / 2f - (a.x + a.width / 2f); val dy = b.y + b.height / 2f - (a.y + a.height / 2f)
        val out = SkinTweaks.apply(p, mapOf(g.id to SkinTweak(dx = dx / p.k, dy = dy / p.k)), 1080f, 2520f, TweakRules.OVERLAY, keep = setOf(g.id))
        assertEquals(controls(p, ss), controls(out, ss), "SELECT START stays at home")
        assertTrue(touching(out, g, b = ss) < 0.5f, "touch areas on each other: ${touching(out, g, b = ss)}")
        assertTrue(box(out, g) != box(p, g), "MENU moved some")
    }

    @Test
    fun growing_has_no_cap_but_the_window() {
        val p = menu()
        val g = group(p, "MENU")
        val out = SkinTweaks.apply(p, mapOf(g.id to SkinTweak(scale = 3f)), 1080f, 2520f)
        val r0 = controls(p, g).first().spec.r
        assertTrue(controls(out, g).first().spec.r > r0 * 1.5f, "grew to ${controls(out, g).first().spec.r / r0}")
        for (o in SkinGroups.of(p.spec)) if (o != g) assertTrue(touching(out, g, b = o) < 0.5f, "on ${o.id}")
        assertTrue(hits(out, g).let { it.y + it.height } <= 2520f + 0.01f, "in the window")
    }

    @Test
    fun overlay_rules_ignore_the_screens() {
        val p = grip()
        val glass = p.glasses.first().rect
        val face = group(p, "A", "B", "X", "Y")
        val b = box(p, face)
        val dx = glass.x + glass.width / 2f - (b.x + b.width / 2f)
        val out = SkinTweaks.apply(p, mapOf(face.id to SkinTweak(dx = dx / p.k)), 2420f, 1080f, TweakRules.OVERLAY)
        near(box(out, face).x, b.x + dx, "in the screen's middle")
        val skin = SkinTweaks.apply(p, mapOf(face.id to SkinTweak(dx = dx / p.k)), 2420f, 1080f, TweakRules.SKIN)
        assertTrue(box(skin, face).x > b.x + dx + 10f, "skin rules stop it")
    }

    @Test
    fun the_hinge_band_stays_off_limits_half_folded() {
        val p = assertNotNull(SkinChoice.place(carbon, 1080, 2520, 420, tablet = false, ds = false, picture = 240f to 160f, hingeY = 1200f, variant = SkinVariant.GBA))
        val band = SkinTweaks.noGo(p).last()
        val l = group(p, "L")
        assertTrue(box(p, l).y >= band.y + band.height, "L starts below the band")
        val out = SkinTweaks.apply(p, mapOf(l.id to SkinTweak(dy = -10000f)), 1080f, 2520f)
        near(box(out, l).y, band.y + band.height, "stopped at the band")
    }
}
