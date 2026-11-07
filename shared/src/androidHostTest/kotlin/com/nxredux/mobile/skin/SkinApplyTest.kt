package com.nxredux.mobile.skin

import com.nxredux.mobile.options.PxRect
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

    private fun near(a: Float, b: Float, what: String) = assertTrue(abs(a - b) < 0.01f, "$what: $a vs $b")

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
        val p = menu()
        val g = group(p, "A", "B", "X", "Y")
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
        // sideways, the face buttons may move far enough left to reach the screen: they stop short of it
        val p = assertNotNull(SkinChoice.place(carbon, 2420, 1080, 420, tablet = false, ds = false, picture = 240f to 160f, variant = SkinVariant.GBA))
        val zones = SkinTweaks.noGo(p)
        assertTrue(zones.isNotEmpty())
        val face = SkinGroups.of(p.spec).single { "A" in it.controls && "B" in it.controls }
        val start = union(pieces(p, face).map { it.dst })
        val free = SkinTweaks.limit(SkinTweak(dx = -10000f), face).dx * p.k
        assertTrue(zones.any { hits(PxRect(start.x + free, start.y, start.width, start.height), it) }, "the full move reaches the screen")
        val r = union(pieces(SkinTweaks.apply(p, mapOf(face.id to SkinTweak(dx = -10000f)), 2420f, 1080f), face).map { it.dst })
        for (z in zones) assertTrue(!hits(r, z), "$r hits $z")
        assertTrue(r.x < start.x - 10f, "moved some: $r")
        // the strip is free and starts against the screen's margin: pushed up it goes no further in
        val q = assertNotNull(SkinChoice.place(carbon, 1080, 2520, 420, tablet = false, ds = false, picture = 240f to 160f, variant = SkinVariant.GBA))
        val strip = SkinGroups.of(q.spec).single { it.id == SkinGroups.STRIP }
        fun top(x: SkinPlacement) = x.controls.filter { it.spec.id in strip.controls }.minOf { it.y - it.spec.hh }
        val up = SkinTweaks.apply(q, mapOf(strip.id to SkinTweak(dy = -2000f)), 1080f, 2520f)
        assertTrue(top(up) >= top(q) - 0.5f, "${top(up)} vs ${top(q)}")
        val down = SkinTweaks.apply(q, mapOf(strip.id to SkinTweak(dy = 30f)), 1080f, 2520f)
        near(top(down), top(q) + 30f * q.k, "strip down")
        assertTrue(down.pieces.filter { it.strip }.zip(q.pieces.filter { it.strip }).all { (a, b) -> abs(a.dst.y - b.dst.y - 30f * q.k) < 0.01f })
    }

    @Test
    fun apply_keeps_a_group_inside_the_window() {
        val w = 2420; val h = 1080   // the Fold's cover, sideways
        val p = assertNotNull(SkinChoice.place(carbon, w, h, 420, tablet = false, ds = false, picture = 240f to 160f, variant = SkinVariant.GBA))
        assertEquals(SkinFamily.GRIP, p.spec.family)
        val face = SkinGroups.of(p.spec).single { "A" in it.controls && "B" in it.controls }
        val out = SkinTweaks.apply(p, mapOf(face.id to SkinTweak(dx = 10000f, scale = 1.25f)), w.toFloat(), h.toFloat())
        val r = union(pieces(out, face).map { it.dst })
        assertTrue(r.x >= -0.01f && r.x + r.width <= w + 0.01f, "$r in 0..$w")
        assertTrue(r.y >= -0.01f && r.y + r.height <= h + 0.01f, "$r in 0..$h")
        for (c in controls(out, face)) assertTrue(c.x in 0f..w.toFloat(), "${c.spec.id} ${c.x}")
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
    fun apply_limits_stored_tweaks() {
        val p = menu()
        val g = group(p, "A", "B", "X", "Y")
        val wild = SkinTweaks.apply(p, mapOf(g.id to SkinTweak(scale = 3f)), 1080f, 2520f)
        val capped = SkinTweaks.apply(p, mapOf(g.id to SkinTweak(scale = SkinTweaks.MAX_SCALE)), 1080f, 2520f)
        assertEquals(capped, wild)
        val r0 = controls(p, g).first().spec.r
        near(controls(wild, g).first().spec.r, r0 * 1.25f, "r")
    }
}
