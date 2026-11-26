package com.nxredux.mobile.skin

import com.nxredux.mobile.options.NdsSettings
import com.nxredux.mobile.options.PxRect
import com.nxredux.mobile.runner.WiiuLayout
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The Wii U GamePad's layouts in the built-in packs: placed on every device class, the TV and the GamePad both 16:9. */
class SkinWiiuTest {
    private val packs = listOf("nx-carbon", "nx-onyx", "nx-obsidian", "nx-plasma", "nx-ember", "nx-volt", "nx-tide")

    private fun place(pack: SkinPackManifest, d: DeviceClass) =
        SkinChoice.place(pack, d.w, d.h, d.dpi, d.tablet, ds = true, hingeY = d.hingeY, foldable = d.foldable, variant = SkinVariant.WIIU)

    private fun aspect(r: PxRect) = r.width / r.height

    @Test
    fun every_device_class_gets_the_gamepad_with_the_tv_and_the_gamepad_16_by_9() {
        for (id in packs) {
            val pack = skinPack(id)
            for (d in GOLDEN_DEVICES) {
                val label = "$id ${d.name}"
                val p = assertNotNull(place(pack, d), label)
                assertEquals(SkinVariant.WIIU, p.spec.variant, label)
                val stack = p.glass(GlassScreen.STACK)?.rect
                val top = p.glass(GlassScreen.TOP)?.rect
                val touch = p.glass(GlassScreen.TOUCH)?.rect
                // the glasses grow with the board (a tall phone's stack, a tablet's touch screen): the screens 16:9 in them
                if (stack != null) { assertNull(top, label); assertNull(touch, label) }
                else { assertNotNull(top, label); assertNotNull(touch, label) }
                val (tv, pad) = assertNotNull(WiiuLayout.skinRegions(stack, top, touch, NdsSettings()), label)
                for (r in listOf(tv, pad)) assertTrue(abs(aspect(r.dst) - 16f / 9f) < 0.01f, "$label ${r.screen} ${r.dst}")
                val glasses = listOfNotNull(stack, top, touch)
                for (r in listOf(tv, pad)) assertTrue(glasses.any { g -> r.dst.x >= g.x - 0.5f && r.dst.y >= g.y - 0.5f &&
                    r.dst.x + r.dst.width <= g.x + g.width + 0.5f && r.dst.y + r.dst.height <= g.y + g.height + 0.5f }, "$label ${r.screen} outside its glass")
                assertEquals(WiiuLayout.TV_SRC, tv.src, label)
                assertTrue(pad.touch, label)
                assertTrue(p.controls.none { it.spec.id == "MIC" || it.spec.id == "SAVE" || it.spec.id == "LOAD" }, label)
                assertTrue(p.controls.any { it.spec.id == "HOME" } && p.controls.any { it.spec.id == "CSTICK" }, label)
            }
        }
    }

    @Test
    fun each_window_takes_its_family() {
        val pack = skinPack()
        val want = mapOf(
            "phone" to "stacked_wiiugp", "cover" to "large_wiiugp", "phone_land" to "grip_wiiugp", "cover_land" to "grip_wiiugp_col",
            "inner" to "flex_wiiugp_fold", "inner_half" to "flex_wiiugp_fold", "ultra" to "flex_wiiugp_fold", "ultra_land" to "flex_wiiugp_fold",
            "ultra_half" to "flex_wiiugp_fold", "ultra_land_half" to "flex_wiiugp_fold", "flip_half" to "flex_wiiugp",
        )
        for (d in GOLDEN_DEVICES) want[d.name]?.let { assertEquals(it, place(pack, d)?.spec?.id, d.name) }
        // the Fold cover's large layout: one screen frame, the TV across its top, the GamePad centred under it
        val cover = assertNotNull(place(pack, GOLDEN_DEVICES.single { it.name == "cover" }))
        assertNull(cover.glass(GlassScreen.TOP)); assertNull(cover.glass(GlassScreen.TOUCH))
        val g = assertNotNull(cover.glass(GlassScreen.STACK)).rect
        val (tv, pad) = assertNotNull(WiiuLayout.skinRegions(g, null, null, NdsSettings())).map { it.dst }
        assertEquals(g.x, tv.x, 1f); assertEquals(g.width, tv.width, 1f); assertEquals(g.y, tv.y, 1f)
        assertEquals(tv.y + tv.height, pad.y, 1f); assertEquals(g.y + g.height, pad.y + pad.height, 1f)
        assertEquals(g.x + g.width / 2f, pad.x + pad.width / 2f, 1f, "the GamePad centred")
        assertTrue(pad.width < g.width - 100f, "black either side of the GamePad: $pad in $g")
    }

    /**
     * Sideways on a phone or a tablet with no fold, the GamePad takes the 3DS's shell: the row shell, its column twin
     * where the 3DS's column is chosen, the tall shell up from the 3DS's tall height.
     */
    @Test
    fun a_landscape_window_takes_the_3ds_shell() {
        for (id in packs) {
            val pack = skinPack(id)
            for ((w, h) in listOf(2640 to 1200, 2700 to 1500, 2700 to 1600)) {
                val p = assertNotNull(SkinChoice.place(pack, w, h, 420, tablet = false, ds = true, variant = SkinVariant.WIIU), "$id ${w}x$h")
                val n = assertNotNull(SkinChoice.place(pack, w, h, 420, tablet = false, ds = true, variant = SkinVariant.N3DS), "$id ${w}x$h")
                assertEquals(n.spec.id.replace("_3ds", "_wiiugp"), p.spec.id, "$id ${w}x$h")
                assertNotNull(p.glass(GlassScreen.STACK), "$id ${w}x$h")
            }
        }
    }

    @Test
    fun a_pack_without_the_gamepad_gives_no_skin_rather_than_the_one_screen_set() {
        val pack = skinPack().let { it.copy(layouts = it.layouts.filter { l -> l.variant != SkinVariant.WIIU }) }
        for (d in GOLDEN_DEVICES) assertNull(place(pack, d), d.name)
    }

    /** The Button layout editor places the GamePad as the game does, so a tweak saved there lands on the drawn layout. */
    @Test
    fun the_layout_editor_picks_the_layout_the_game_draws() {
        val tag = "WIIU"
        val ds = com.nxredux.mobile.ui.skinlayout.SkinLayoutEdit.twoScreens(tag)
        val editorPicture = if (ds) null else (4f / 3f * 240f) to 240f
        for (id in packs) {
            val pack = skinPack(id)
            for (d in GOLDEN_DEVICES.filter { it.hingeY == null }) {
                val editor = SkinChoice.place(pack, d.w, d.h, d.dpi, d.tablet, ds, editorPicture, foldable = d.foldable, variant = SkinVariant.WIIU)
                val game = SkinChoice.place(pack, d.w, d.h, d.dpi, d.tablet, ds = true, foldable = d.foldable, variant = SkinVariant.WIIU)
                assertEquals(game?.spec?.id, editor?.spec?.id, "$id ${d.name}")
            }
        }
    }

    private fun union(rs: List<PxRect>): PxRect? = if (rs.isEmpty()) null else {
        val x0 = rs.minOf { it.x }; val y0 = rs.minOf { it.y }
        PxRect(x0, y0, rs.maxOf { it.x + it.width } - x0, rs.maxOf { it.y + it.height } - y0)
    }

    /** The drawn frame round the top (or stack) glass, and the touch piece round the touch glass, window px. */
    private fun frames(p: SkinPlacement): Pair<PxRect?, PxRect?> {
        val ti = p.spec.pieces.indexOfFirst { it.glass?.screen == GlassScreen.TOUCH }
        return union(p.pieces.filter { it.piece == -1 && !it.strip && it.fill < 0f }.map { it.dst }) to
            (if (ti >= 0) union(p.pieces.filter { it.piece == ti && it.fill < 0f }.map { it.dst }) else null)
    }

    /** Border widths (left, top, right, bottom) of [frame] round [glass]. */
    private fun borders(frame: PxRect, glass: PxRect) = listOf(glass.x - frame.x, glass.y - frame.y,
        frame.x + frame.width - glass.x - glass.width, frame.y + frame.height - glass.y - glass.height)

    /**
     * No touch shortcut strip (the Wii U offers none); every screen's frame as thick under its glass as over it and as
     * thick right as left; and each glass the pictures' own shape (the GamePad's 16:9, one stack glass the TV over the
     * GamePad, 16:18), the pictures filling it: no black band round them.
     */
    @Test
    fun the_frames_hug_their_glasses_with_no_shortcut_strip() {
        for (id in packs) {
            val pack = skinPack(id)
            for (d in GOLDEN_DEVICES) {
                val label = "$id ${d.name}"
                val p = assertNotNull(place(pack, d), label)
                assertTrue(p.controls.none { it.spec.id.startsWith("TOUCH_") }, "$label: a touch shortcut slot")
                assertTrue(p.spec.frame?.strip == null && p.spec.pieces.none { it.strip != null }, "$label: a strip")
                val (frame, touchFrame) = frames(p)
                val top = assertNotNull(p.glass(GlassScreen.TOP) ?: p.glass(GlassScreen.STACK), label).rect
                p.glass(GlassScreen.TOUCH)?.rect?.let { assertEquals(16f / 9f, aspect(it), 0.01f, "$label GamePad glass ${it}") }
                p.glass(GlassScreen.STACK)?.rect?.let { g ->
                    if (p.spec.id.endsWith("_tall")) {   // the roomy sideways shells: cut to the TV over the GamePad
                        assertEquals(g.height * 16f / 18f, g.width, 1f, "$label stack glass $g")
                        assertFills(g, assertNotNull(WiiuLayout.skinRegions(g, null, null, NdsSettings()), label).map { it.dst }, label)
                    } else assertStackAsThe3ds(pack, d, p, g, label)
                }
                for ((f, g) in listOf(frame to top, touchFrame to p.glass(GlassScreen.TOUCH)?.rect)) {
                    if (f == null || g == null) continue
                    val (l, t, r, b) = borders(f, g)
                    assertEquals(l, r, 1f, "$label left / right $l $r")
                    assertEquals(t, b, 1f, "$label top / bottom $t $b")
                }
            }
        }
    }

    /**
     * Elsewhere the GamePad's one screen frame is the 3DS's: sideways the 3DS's frame; a phone upright its top and
     * sides, its edge as far over ZL / ZR as the 3DS's is over L / R; the Fold cover's from the 3DS's top screen's top
     * and sides down to the box gap over L / R.
     */
    private fun assertStackAsThe3ds(pack: SkinPackManifest, d: DeviceClass, p: SkinPlacement, g: PxRect, label: String) {
        val n = assertNotNull(SkinChoice.place(pack, d.w, d.h, d.dpi, d.tablet, ds = true, hingeY = d.hingeY, foldable = d.foldable, variant = SkinVariant.N3DS), label)
        val ng = assertNotNull(n.glass(GlassScreen.STACK) ?: n.glass(GlassScreen.TOP), label).rect
        for ((a, b) in listOf(ng.x to g.x, ng.y to g.y, ng.width to g.width)) assertEquals(a, b, 1f, "$label glass $g, the 3DS's $ng")
        fun top(pl: SkinPlacement, id: String) = pl.controls.first { it.spec.id == id }.let { it.y - it.spec.hh }
        val wf = assertNotNull(frames(p).first, label); val nf = assertNotNull(frames(n).first, label)
        when (p.spec.family) {
            SkinFamily.GRIP -> for ((a, b) in listOf(nf.x to wf.x, nf.y to wf.y, nf.width to wf.width, nf.height to wf.height)) assertEquals(a, b, 1f, "$label frame $wf, the 3DS's $nf")
            SkinFamily.STACKED -> assertEquals(top(n, "L") - (nf.y + nf.height), top(p, "L2") - (wf.y + wf.height), 1f, "$label frame edge over ZL")
            else -> {
                val l = p.controls.first { it.spec.id == "L" }
                val shoulder = p.pieces.filter { it.piece >= 0 && it.dst.width < 300f && l.x in it.dst.x..(it.dst.x + it.dst.width) && l.y in it.dst.y..(it.dst.y + it.dst.height) }
                val (frame, _) = frames(p)
                assertEquals(shoulder.minOf { it.dst.y } - SkinBoxes.GAP_DP * d.dpi / 160f, assertNotNull(frame, label).let { it.y + it.height }, 1f, "$label frame over L")
            }
        }
    }

    /**
     * The touch screen's frame as far under the top screen's as the 3DS's on the same window; half-folded, the fold sets
     * it (the top screen's frame ends above the fold, no taller than its picture needs): its top where the 3DS's is.
     * Not on the Ultra sideways: there the 3DS's boxes keep its taller 4:3 touch frame lower than the board's frame gap,
     * which the GamePad keeps as on every other fold board.
     */
    @Test
    fun the_screens_are_as_far_apart_as_the_3ds_screens() {
        for (id in packs) {
            val pack = skinPack(id)
            for (d in GOLDEN_DEVICES.filter { it.name != "ultra_land" }) {
                val label = "$id ${d.name}"
                val p = assertNotNull(place(pack, d), label)
                val n3ds = assertNotNull(SkinChoice.place(pack, d.w, d.h, d.dpi, d.tablet, ds = true, hingeY = d.hingeY, foldable = d.foldable, variant = SkinVariant.N3DS), label)
                val (wf, wt) = frames(p); val (nf, nt) = frames(n3ds)
                if (wf == null || wt == null || nf == null || nt == null) continue
                if (d.hingeY != null) assertEquals(nt.y, wt.y, 1f, "$label: touch frame top ${p.spec.id} vs ${n3ds.spec.id}")
                else assertEquals(nt.y - nf.y - nf.height, wt.y - wf.y - wf.height, 1f, "$label: gap ${p.spec.id} vs ${n3ds.spec.id}")
            }
        }
    }

    /**
     * The GamePad is the New 3DS's layout on every window: the d-pad and left stick, the face, L / R and ZL / ZR where the
     * 3DS twin has them, at its sizes; but a phone upright, whose ZL / ZR stand over L / R, giving − + the L / R row's
     * middle, and the Fold cover, whose ZL / ZR sit beside L / R on their inner side, the four lower (its one screen
     * frame taller over them).
     */
    @Test
    fun the_pads_sit_where_the_new_3ds_has_them() {
        for (id in packs) {
            val pack = skinPack(id)
            for (d in GOLDEN_DEVICES) {
                val label = "$id ${d.name}"
                val p = assertNotNull(place(pack, d), label)
                val n3ds = assertNotNull(SkinChoice.place(pack, d.w, d.h, d.dpi, d.tablet, ds = true, hingeY = d.hingeY, foldable = d.foldable, variant = SkinVariant.N3DS), label)
                fun at(pl: SkinPlacement, cid: String) = pl.controls.first { it.spec.id == cid }
                val stacked = p.spec.family == SkinFamily.STACKED; val large = p.spec.family == SkinFamily.LARGE
                for (cid in listOf("DPAD", "STICK", "X", "Y", "A", "B") + (if (large) emptyList() else listOf("L", "R")) + (if (stacked || large) emptyList() else listOf("L2", "R2"))) {
                    val w = at(p, cid); val n = at(n3ds, cid)
                    assertEquals(n.x, w.x, 1f, "$label $cid x"); assertEquals(n.y, w.y, 1f, "$label $cid y")
                    for ((a, b) in listOf(n.spec.r to w.spec.r, n.spec.hw to w.spec.hw, n.spec.hh to w.spec.hh)) assertEquals(a, b, 0.5f, "$label $cid size")
                }
                if (large) for ((z, s, sg) in listOf(Triple("L2", "L", 1f), Triple("R2", "R", -1f))) {   // beside L / R on their inner side, lower
                    val zc = at(p, z); val sc = at(p, s); val nc = at(n3ds, s)
                    assertEquals(nc.x, sc.x, 1f, "$label $s x"); assertTrue(sc.y > nc.y, "$label $s ${sc.y} not under the 3DS's ${nc.y}")
                    for ((a, b) in listOf(nc.spec.hw to sc.spec.hw, nc.spec.hh to sc.spec.hh)) assertEquals(a, b, 0.5f, "$label $s size")
                    assertEquals(sc.y, zc.y, 1f, "$label $z level with $s")
                    assertTrue((zc.x - sc.x) * sg >= zc.spec.hw + sc.spec.hw, "$label $z ${zc.x} not inside $s ${sc.x}")
                }
                if (stacked) for ((z, s) in listOf("L2" to "L", "R2" to "R")) {
                    val zc = at(p, z); val sc = at(p, s)
                    assertEquals(sc.x, zc.x, 1f, "$label $z over $s")
                    assertTrue(zc.y + zc.spec.hh <= sc.y - sc.spec.hh, "$label $z ${zc.y} not over $s ${sc.y}")
                }
            }
        }
    }

    /**
     * The right stick 85% of the left one's size where its box has the room (the fold boards, the roomy sideways shell),
     * else the largest that fits there, still bigger than the 3DS's nub; and nothing on anything by the packs' rules: a
     * stick's touch reach a quarter past its radius, kept off every other control's, the right stick's and the GamePad's
     * own caps' off the screens; every control on the window.
     */
    @Test
    fun the_right_stick_is_85_percent_of_the_left_where_it_fits_and_nothing_overlaps() {
        for (id in packs) {
            val pack = skinPack(id)
            for (d in GOLDEN_DEVICES) {
                val label = "$id ${d.name}"
                val p = assertNotNull(place(pack, d), label)
                val c = p.controls.associateBy { it.spec.id }
                val want = 0.85f * c.getValue("STICK").spec.r
                if (d.name == "cover") {   // the Fold cover: small in the middle, the 3DS's nub
                    val n3ds = assertNotNull(SkinChoice.place(pack, d.w, d.h, d.dpi, d.tablet, ds = true, hingeY = d.hingeY, foldable = d.foldable, variant = SkinVariant.N3DS), label)
                    val nub = n3ds.controls.first { it.spec.id == "CSTICK" }; val cs = c.getValue("CSTICK")
                    assertEquals(nub.spec.r, cs.spec.r, 0.5f, "$label nub r"); assertEquals(nub.x, cs.x, 1f, "$label nub x"); assertEquals(nub.y, cs.y, 1f, "$label nub y")
                } else if (d.name in SMALLER_RIGHT_STICK) {
                    val n3ds = assertNotNull(SkinChoice.place(pack, d.w, d.h, d.dpi, d.tablet, ds = true, hingeY = d.hingeY, foldable = d.foldable, variant = SkinVariant.N3DS), label)
                    val r = c.getValue("CSTICK").spec.r
                    assertTrue(r > n3ds.controls.first { it.spec.id == "CSTICK" }.spec.r && r < want, "$label right stick r $r")
                } else assertEquals(want, c.getValue("CSTICK").spec.r, 0.5f, "$label right stick r")
                fun reach(pc: PlacedControl) = when (pc.spec.shape) { ControlShape.STICK -> pc.spec.r * 1.25f; ControlShape.RECT -> 0f; else -> pc.spec.r }
                fun box(pc: PlacedControl): FloatArray { val r = reach(pc); val hw = if (r > 0f) r else pc.spec.hw; val hh = if (r > 0f) r else pc.spec.hh; return floatArrayOf(pc.x - hw, pc.y - hh, pc.x + hw, pc.y + hh) }
                val face = setOf("A", "B", "X", "Y")
                for (useStick in listOf(true, false)) {
                    val cs = p.forStick(useStick).controls
                    for (i in cs.indices) for (j in i + 1 until cs.size) {
                        val a = cs[i]; val b = cs[j]
                        if (a.spec.id in face && b.spec.id in face) continue
                        val round = a.spec.shape != ControlShape.RECT && b.spec.shape != ControlShape.RECT
                        val ok = if (round) kotlin.math.hypot(a.x - b.x, a.y - b.y) >= reach(a) + reach(b) - 1f
                            else box(a).let { x -> box(b).let { y -> x[2] <= y[0] || y[2] <= x[0] || x[3] <= y[1] || y[3] <= x[1] } }
                        assertTrue(ok, "$label ${a.spec.id} on ${b.spec.id}")
                    }
                    for (x in cs) assertTrue(x.x >= 0f && x.x <= d.w && x.y >= 0f && x.y <= d.h, "$label ${x.spec.id} off the window")
                }
                for (id in listOf("CSTICK", "L3", "R3", "HOME", "SELECT", "START")) for (g in p.glasses) {
                    val pc = c.getValue(id); val r = g.rect
                    val nx = pc.x.coerceIn(r.x, r.x + r.width); val ny = pc.y.coerceIn(r.y, r.y + r.height)
                    assertTrue(kotlin.math.hypot(pc.x - nx, pc.y - ny) >= reach(pc) - 1f, "$label $id's reach on the ${g.screen} glass")
                }
            }
        }
    }

    /**
     * The Fold cover: upright L3 beside ZL and R3 beside ZR on their inner side (L ZL L3 … R3 ZR R), sideways L3 over L
     * and R3 over R; the caps' rows the 3DS's (HOME on MIC's spot).
     */
    @Test
    fun the_fold_covers_stick_presses_sit_by_the_shoulders() {
        for (id in packs) {
            val pack = skinPack(id)
            for (name in listOf("cover", "cover_land")) {
                val d = GOLDEN_DEVICES.single { it.name == name }
                val label = "$id $name"
                val p = assertNotNull(place(pack, d), label)
                val n = assertNotNull(SkinChoice.place(pack, d.w, d.h, d.dpi, d.tablet, ds = true, foldable = d.foldable, variant = SkinVariant.N3DS), label)
                fun c(pl: SkinPlacement, cid: String) = pl.controls.first { it.spec.id == cid }
                if (name == "cover") for ((s3, z, sg) in listOf(Triple("L3", "L2", 1f), Triple("R3", "R2", -1f))) {
                    assertEquals(c(p, z).y, c(p, s3).y, 1f, "$label $s3 level with $z")
                    assertTrue((c(p, s3).x - c(p, z).x) * sg > 0f, "$label $s3 not inside $z")
                } else for ((s3, sh) in listOf("L3" to "L", "R3" to "R")) {
                    assertEquals(c(p, sh).x, c(p, s3).x, 1f, "$label $s3 over $sh")
                    assertTrue(c(p, s3).y < c(p, sh).y, "$label $s3 not over $sh")
                }
                val row = if (name == "cover") listOf("STICK_SWAP", "MENU", "SWAP") else listOf("STICK_SWAP", "MENU", "SWAP", "SELECT", "START")
                for (cid in row) { assertEquals(c(n, cid).x, c(p, cid).x, 1f, "$label $cid x"); assertEquals(c(n, cid).y, c(p, cid).y, 1f, "$label $cid y") }
                assertEquals(c(n, "MIC").x, c(p, "HOME").x, 1f, "$label HOME x"); assertEquals(c(n, "MIC").y, c(p, "HOME").y, 1f, "$label HOME y")
            }
        }
    }

    /** Windows whose right stick's box has no room for 85% of the left stick without moving the 3DS's pads. */
    private val SMALLER_RIGHT_STICK = setOf("phone", "phone_land", "cover_land", "flip_half")

    private fun assertFills(glass: PxRect, screens: List<PxRect>, label: String, px: Float = 1f) {
        val u = assertNotNull(union(screens), label)
        for ((a, b) in listOf(u.x to glass.x, u.y to glass.y, u.width to glass.width, u.height to glass.height)) assertEquals(b, a, px, "$label: $u in $glass")
    }

    /**
     * On a regular phone sideways the player's layout goes in the one glass, its frame the 3DS's, centred in it, as
     * big as fits (Large the GamePad beside the TV, Side by side the two abreast, Stacked the one over the other, one screen
     * alone).
     */
    @Test
    fun the_players_layout_fits_the_3ds_sized_glass() {
        val d = GOLDEN_DEVICES.single { it.name == "phone_land" }
        for (id in packs) for (layout in com.nxredux.mobile.options.NdsLayoutChoice.entries) {
            val label = "$id $layout"
            val pack = skinPack(id)
            val p = assertNotNull(SkinChoice.place(pack, d.w, d.h, d.dpi, d.tablet, ds = true, foldable = d.foldable, variant = SkinVariant.WIIU,
                stackAspect = WiiuLayout.skinAspect(layout)), label)
            val g = assertNotNull(p.glass(GlassScreen.STACK), label).rect
            val n = assertNotNull(SkinChoice.place(pack, d.w, d.h, d.dpi, d.tablet, ds = true, foldable = d.foldable, variant = SkinVariant.N3DS), label)
            val wf = assertNotNull(frames(p).first, label); val nf = assertNotNull(frames(n).first, label)
            for ((a, b) in listOf(nf.x to wf.x, nf.y to wf.y, nf.width to wf.width, nf.height to wf.height)) assertEquals(a, b, 1f, "$label frame $wf, the 3DS's $nf")
            val u = assertNotNull(union(assertNotNull(WiiuLayout.skinRegions(g, null, null, NdsSettings(), layout), label).filter { !it.inset }.map { it.dst }), label)
            assertTrue(u.x >= g.x - 0.5f && u.y >= g.y - 0.5f && u.x + u.width <= g.x + g.width + 0.5f && u.y + u.height <= g.y + g.height + 0.5f, "$label $u outside $g")
            assertTrue(kotlin.math.abs(u.width - g.width) <= 4f || kotlin.math.abs(u.height - g.height) <= 4f, "$label $u not as big as fits $g")
            assertEquals(g.x + g.width / 2f, u.x + u.width / 2f, 2f, "$label centred across"); assertEquals(g.y + g.height / 2f, u.y + u.height / 2f, 2f, "$label centred down")
            val (frame, _) = frames(p)
            val (l, t, r, b) = borders(assertNotNull(frame, label), g)
            assertEquals(l, r, 1f, "$label left / right"); assertEquals(t, b, 1f, "$label top / bottom")
        }
    }
}
