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
                // no HOME cap (the GamePad's HOME does what MENU does) nor SWAP (the strip's middle, the app's)
                assertTrue(p.controls.none { it.spec.id == "HOME" || it.spec.id == "SWAP" } && p.controls.any { it.spec.id == "CSTICK" }, label)
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
     * The touch shortcut strip under the GamePad's screen (its frame's, or its touch piece's): its four slots under that
     * glass, inside its frame (SWAP goes in its middle, [arrangeStrip]); every screen's frame as thick right as left, and
     * one with no strip as thick under its glass as over it; each GamePad glass 16:9, the roomy shells' one glass the TV
     * over the GamePad (16:18), the pictures filling it.
     */
    @Test
    fun the_gamepads_screen_has_the_touch_shortcut_strip_under_it() {
        for (id in packs) {
            val pack = skinPack(id)
            for (d in GOLDEN_DEVICES) {
                val label = "$id ${d.name}"
                val p = assertNotNull(place(pack, d), label)
                val (frame, touchFrame) = frames(p)
                val ti = p.spec.pieces.indexOfFirst { it.glass?.screen == GlassScreen.TOUCH }
                val stripped = (if (ti >= 0) p.spec.pieces[ti].strip else p.spec.frame?.strip)
                assertNotNull(stripped, "$label: no strip under the GamePad's screen")
                val slots = p.controls.filter { it.spec.id.startsWith("TOUCH_") }
                assertEquals(4, slots.size, "$label slots")
                val under = assertNotNull(p.glass(GlassScreen.TOUCH) ?: p.glass(GlassScreen.STACK), label).rect
                val holder = assertNotNull(if (ti >= 0) touchFrame else frame, label)
                for (c in slots) {
                    assertTrue(c.y > under.y + under.height && c.y < holder.y + holder.height, "$label ${c.spec.id} at ${c.y} not under $under in $holder")
                    assertTrue(c.x - c.spec.hw >= under.x - 1f && c.x + c.spec.hw <= under.x + under.width + 1f, "$label ${c.spec.id} not across $under")
                }
                val top = assertNotNull(p.glass(GlassScreen.TOP) ?: p.glass(GlassScreen.STACK), label).rect
                // (a tablet upright: the GamePad's glass taller, down to the shoulders, the_tablets_gamepad_frame_comes_down_to_the_shoulders)
                if (d.name != "tablet") p.glass(GlassScreen.TOUCH)?.rect?.let { assertEquals(16f / 9f, aspect(it), 0.01f, "$label GamePad glass ${it}") }
                p.glass(GlassScreen.STACK)?.rect?.let { g ->
                    if (p.spec.id.endsWith("_tall") && d.foldable) {   // the unfolded Fold sideways: cut to the TV over the GamePad
                        assertEquals(g.height * 16f / 18f, g.width, 1f, "$label stack glass $g")
                        assertFills(g, assertNotNull(WiiuLayout.skinRegions(g, null, null, NdsSettings()), label).map { it.dst }, label)
                    } else assertStackAsThe3ds(pack, d, p, g, label)
                }
                for ((f, g, strip) in listOf(Triple(frame, top, p.spec.frame?.strip != null), Triple(touchFrame, p.glass(GlassScreen.TOUCH)?.rect, ti >= 0))) {
                    if (f == null || g == null) continue
                    val (l, t, r, b) = borders(f, g)
                    assertEquals(l, r, 1f, "$label left / right $l $r")
                    if (strip) assertTrue(b > t, "$label no band for the strip: $t $b") else assertEquals(t, b, 1f, "$label top / bottom $t $b")
                }
            }
        }
    }

    /**
     * Elsewhere the GamePad's one screen frame is the 3DS's: sideways the 3DS's frame; a phone upright its top and
     * sides, its edge as far over the L3 / R3 pills as the 3DS's is over L / R; the Fold cover's from the 3DS's top screen's top
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
            SkinFamily.STACKED -> assertEquals(top(n, "L") - (nf.y + nf.height), top(p, "L3") - (wf.y + wf.height), 1f, "$label frame edge over L3")
            else -> {
                // the shoulder row's pieces (− + with them): the topmost a box gap under the frame
                val row = p.controls.filter { it.spec.id in setOf("L", "SELECT", "START") }
                val shoulder = p.pieces.filter { pc -> pc.piece >= 0 && pc.dst.width < 300f && row.any { c -> c.x in pc.dst.x..(pc.dst.x + pc.dst.width) && c.y in pc.dst.y..(pc.dst.y + pc.dst.height) } }
                val (frame, _) = frames(p)
                assertEquals(shoulder.minOf { it.dst.y } - SkinBoxes.GAP_DP * d.dpi / 160f, assertNotNull(frame, label).let { it.y + it.height }, 1f, "$label frame over the shoulder row")
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
     * 3DS twin has them, at its sizes; but a phone upright, whose L3 / R3 pills stand over L / R and ZL / ZR sit in the
     * L / R row, and the Fold cover, whose ZL / ZR sit beside L / R on their inner side, the four lower (its one screen
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
                if (stacked) for ((p3, s, z) in listOf(Triple("L3", "L", "L2"), Triple("R3", "R", "R2"))) {   // L3 / R3 pills over L / R, ZL / ZR in their row
                    val pc = at(p, p3); val sc = at(p, s); val zc = at(p, z)
                    assertEquals(sc.x, pc.x, 1f, "$label $p3 over $s")
                    assertTrue(pc.y + pc.spec.hh <= sc.y - sc.spec.hh, "$label $p3 ${pc.y} not over $s ${sc.y}")
                    assertPill(pc, sc, label)
                    assertEquals(sc.y, zc.y, 1f, "$label $z in the L / R row")
                    val (l, r) = at(p, "L").x to at(p, "R").x
                    assertTrue(zc.x > l && zc.x < r, "$label $z ${zc.x} not between L and R")
                }
                if (stacked) {   // the bottom row's four in one row, evenly spaced, centred across the window
                    val row = listOf("STICK_SWAP", "MENU", "SELECT", "START").map { at(p, it) }
                    for (c in row) assertEquals(row[0].y, c.y, 1f, "$label ${c.spec.id} in the bottom row")
                    val steps = row.zipWithNext { a, b -> b.x - a.x }
                    for (st in steps) assertEquals(steps[0], st, 1f, "$label bottom row steps $steps")
                    assertEquals(d.w / 2f, (row.first().x + row.last().x) / 2f, 1f, "$label bottom row centred")
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
                        if (a.spec.id.startsWith("TOUCH_") && b.spec.id.startsWith("TOUCH_")) continue   // (the strip's slots tile it)
                        val round = a.spec.shape != ControlShape.RECT && b.spec.shape != ControlShape.RECT
                        val ok = if (round) kotlin.math.hypot(a.x - b.x, a.y - b.y) >= reach(a) + reach(b) - 1f
                            else box(a).let { x -> box(b).let { y -> x[2] <= y[0] || y[2] <= x[0] || x[3] <= y[1] || y[3] <= x[1] } }
                        assertTrue(ok, "$label ${a.spec.id} on ${b.spec.id}")
                    }
                    for (x in cs) assertTrue(x.x >= 0f && x.x <= d.w && x.y >= 0f && x.y <= d.h, "$label ${x.spec.id} off the window")
                }
                for (id in listOf("CSTICK", "L3", "R3", "SELECT", "START")) for (g in p.glasses) {
                    val pc = c.getValue(id); val r = g.rect
                    val nx = pc.x.coerceIn(r.x, r.x + r.width); val ny = pc.y.coerceIn(r.y, r.y + r.height)
                    assertTrue(kotlin.math.hypot(pc.x - nx, pc.y - ny) >= reach(pc) - 1f, "$label $id's reach on the ${g.screen} glass")
                }
            }
        }
    }

    /**
     * The Fold cover: upright the bottom row L3 the stick swap MENU R3 (where the 3DS's row is), − + in the shoulder row
     * between ZL and ZR; sideways the rows L3 + the stick swap and MENU + R3 on the 3DS's spots, − + shoulder pills over
     * L / R.
     */
    @Test
    fun the_fold_covers_caps_rows_and_shoulders() {
        for (id in packs) {
            val pack = skinPack(id)
            for (name in listOf("cover", "cover_land")) {
                val d = GOLDEN_DEVICES.single { it.name == name }
                val label = "$id $name"
                val p = assertNotNull(place(pack, d), label)
                val n = assertNotNull(SkinChoice.place(pack, d.w, d.h, d.dpi, d.tablet, ds = true, foldable = d.foldable, variant = SkinVariant.N3DS), label)
                fun c(pl: SkinPlacement, cid: String) = pl.controls.first { it.spec.id == cid }
                fun xs(pl: SkinPlacement, vararg ids: String) = ids.map { c(pl, it).x }
                if (name == "cover") {
                    // the bottom row L3 the stick swap MENU R3, a 3DS step apart, centred where the 3DS's row is
                    val n3 = xs(n, "STICK_SWAP", "MIC", "MENU", "SWAP"); val step = n3[1] - n3[0]; val mid = (n3.first() + n3.last()) / 2f
                    listOf("L3", "STICK_SWAP", "MENU", "R3").forEachIndexed { i, cid ->
                        assertEquals(mid + (i - 1.5f) * step, c(p, cid).x, 1f, "$label $cid x"); assertEquals(c(n, "MENU").y, c(p, cid).y, 1f, "$label $cid y")
                    }
                    // − + in the shoulder row, midway between ZL and ZR
                    for (cid in listOf("SELECT", "START")) assertEquals(c(p, "L").y, c(p, cid).y, 1f, "$label $cid in the shoulder row")
                    assertEquals((c(p, "L2").x + c(p, "R2").x) / 2f, (c(p, "SELECT").x + c(p, "START").x) / 2f, 1f, "$label − + midway")
                } else {
                    // the rows L3 + the stick swap and MENU + R3 on the 3DS's spots; − + pills over L / R
                    val left = xs(n, "STICK_SWAP", "MIC", "MENU"); val right = xs(n, "SWAP", "SELECT", "START")
                    for ((cid, x) in listOf("L3" to left[0], "STICK_SWAP" to left[1], "MENU" to right[1], "R3" to right[2])) {
                        assertEquals(x, c(p, cid).x, 1f, "$label $cid x"); assertEquals(c(n, "MENU").y, c(p, cid).y, 1f, "$label $cid y")
                    }
                    for ((pm, sh) in listOf("SELECT" to "L", "START" to "R")) {
                        assertEquals(c(p, sh).x, c(p, pm).x, 1f, "$label $pm over $sh")
                        assertTrue(c(p, pm).y < c(p, sh).y, "$label $pm not over $sh")
                        assertEquals(ControlShape.RECT, c(p, pm).spec.shape, "$label $pm a pill")
                        assertEquals(c(p, sh).spec.hw, c(p, pm).spec.hw, 0.5f, "$label $pm hw"); assertEquals(c(p, sh).spec.hh, c(p, pm).spec.hh, 0.5f, "$label $pm hh")
                    }
                }
            }
        }
    }

    /** The fold boards (the Fold and the Ultra inside, a tablet upright): L3 and R3 by their sticks, on the screen's side, low. */
    @Test
    fun the_fold_boards_stick_presses_sit_by_their_sticks() {
        for (id in packs) for (d in GOLDEN_DEVICES.filter { it.name in setOf("inner", "inner_half", "ultra", "ultra_half", "tablet") }) {
            val label = "$id ${d.name}"
            val p = assertNotNull(place(skinPack(id), d), label)
            val c = p.controls.associateBy { it.spec.id }
            for ((cap, stick, side) in listOf(Triple("L3", "STICK", 1f), Triple("R3", "CSTICK", -1f))) {
                val a = c.getValue(cap); val s = c.getValue(stick)
                assertTrue((a.x - s.x) * side > 0f && a.y > s.y, "$label $cap ${a.x},${a.y} not at $stick's lower inner side ${s.x},${s.y}")
                val gap = kotlin.math.hypot(a.x - s.x, a.y - s.y) - 1.25f * s.spec.r - a.spec.r
                assertTrue(gap <= 140f * p.k, "$label $cap ${gap}px off $stick's reach")
            }
        }
    }

    /**
     * A tablet upright: the GamePad's frame down to the box gap over the shoulder buttons (room to spare there), its
     * glass taller and the GamePad centred in it, the strip under it, the row under the frame; pads and shoulders put.
     */
    @Test
    fun the_tablets_gamepad_frame_comes_down_to_the_shoulders() {
        val d = GOLDEN_DEVICES.single { it.name == "tablet" }
        for (id in packs) {
            val pack = skinPack(id)
            val label = "$id tablet"
            val p = assertNotNull(place(pack, d), label)
            val (_, touch) = frames(p)
            val tf = assertNotNull(touch, label)
            val shoulders = p.controls.filter { it.spec.id in setOf("L", "R", "L2", "R2") }
            val tops = p.pieces.filter { pc -> pc.piece >= 0 && pc.dst.width < 300f && shoulders.any { c -> c.x in pc.dst.x..(pc.dst.x + pc.dst.width) && c.y in pc.dst.y..(pc.dst.y + pc.dst.height) } }
            assertEquals(tops.minOf { it.dst.y } - SkinBoxes.GAP_DP * d.dpi / 160f, tf.y + tf.height, 1f, "$label frame over the shoulders")
            val g = assertNotNull(p.glass(GlassScreen.TOUCH), label).rect
            val (_, pad) = assertNotNull(WiiuLayout.skinRegions(null, p.glass(GlassScreen.TOP)?.rect, g, NdsSettings()), label).map { it.dst }
            assertEquals(16f / 9f, aspect(pad), 0.01f, label); assertEquals(g.y + g.height / 2f, pad.y + pad.height / 2f, 1f, "$label GamePad centred")
            for (cid in listOf("MENU", "SELECT", "START")) assertTrue(p.controls.first { it.spec.id == cid }.y > tf.y + tf.height, "$label $cid under the frame")
        }
    }

    /** A tablet sideways: the 3DS's frame, the TV across its width at the top, the GamePad centred in the height left. */
    @Test
    fun the_tablet_sideways_has_the_3ds_frame_and_the_tv_across() {
        val d = GOLDEN_DEVICES.single { it.name == "tablet_land" }
        for (id in packs) {
            val pack = skinPack(id)
            val p = assertNotNull(place(pack, d), id)
            val n = assertNotNull(SkinChoice.place(pack, d.w, d.h, d.dpi, d.tablet, ds = true, foldable = d.foldable, variant = SkinVariant.N3DS), id)
            val wf = assertNotNull(frames(p).first, id); val nf = assertNotNull(frames(n).first, id)
            assertEquals(nf.width, wf.width, 1f, "$id frame width")
            val g = assertNotNull(p.glass(GlassScreen.STACK), id).rect
            val (tv, pad) = assertNotNull(WiiuLayout.skinRegions(g, null, null, NdsSettings()), id).map { it.dst }
            assertEquals(g.width, tv.width, 1f, "$id TV width"); assertEquals(16f / 9f, aspect(tv), 0.01f, "$id TV")
            assertEquals(16f / 9f, aspect(pad), 0.01f, "$id GamePad"); assertEquals(g.x + g.width / 2f, pad.x + pad.width / 2f, 1f, "$id GamePad centred")
            assertEquals(tv.y + tv.height, pad.y, 1f, "$id GamePad under the TV"); assertEquals(g.y + g.height, pad.y + pad.height, 1f, "$id GamePad to the foot")
        }
    }

    /** The Button layout editor's dummy strip as the game's with every slot filled: the Wii U's SWAP in the middle. */
    @Test
    fun the_layout_editors_dummy_strip_has_swap_in_the_middle() {
        val pack = skinPack()
        for (d in GOLDEN_DEVICES) {
            val p = assertNotNull(place(pack, d), d.name)
            val dummies = p.stripDummies()
            assertEquals(listOf("TOUCH_1", "TOUCH_2", STRIP_SWAP, "TOUCH_3", "TOUCH_4"), dummies.map { it.spec.id }, d.name)
            val slots = p.controls.filter { it.spec.id.startsWith("TOUCH_") }
            val mid = (slots.minOf { it.x - it.spec.hw } + slots.maxOf { it.x + it.spec.hw }) / 2f
            assertEquals(mid, dummies[2].x, 0.5f, "${d.name} SWAP centred")
            val n = assertNotNull(SkinChoice.place(pack, d.w, d.h, d.dpi, d.tablet, ds = true, hingeY = d.hingeY, foldable = d.foldable, variant = SkinVariant.N3DS), d.name)
            assertTrue(n.stripDummies().none { it.spec.id == STRIP_SWAP }, "${d.name} 3DS")
        }
    }

    /** A regular phone sideways, before a pick: the TV across the 3DS-sized glass, the GamePad centred under it. */
    @Test
    fun the_phone_sideways_has_the_tv_across_and_the_gamepad_under_it() {
        val d = GOLDEN_DEVICES.single { it.name == "phone_land" }
        val layout = assertNotNull(com.nxredux.mobile.options.WiiuOptions.skinLayout(emptyMap(), landscape = true, controllerMode = false,
            com.nxredux.mobile.layout.ScreenClass.REGULAR_PHONE))
        for (id in packs) {
            val p = assertNotNull(SkinChoice.place(skinPack(id), d.w, d.h, d.dpi, d.tablet, ds = true, foldable = d.foldable, variant = SkinVariant.WIIU,
                stackAspect = WiiuLayout.skinAspect(layout)), id)
            val g = assertNotNull(p.glass(GlassScreen.STACK), id).rect
            val (tv, pad) = assertNotNull(WiiuLayout.skinRegions(g, null, null, NdsSettings(), layout), id).map { it.dst }
            assertEquals(g.width, tv.width, 1f, "$id TV width"); assertEquals(16f / 9f, aspect(tv), 0.01f, "$id TV")
            assertEquals(16f / 9f, aspect(pad), 0.01f, "$id GamePad"); assertEquals(g.x + g.width / 2f, pad.x + pad.width / 2f, 1f, "$id GamePad centred")
            for (r in listOf(tv, pad)) assertTrue(r.x >= g.x - 0.5f && r.y >= g.y - 0.5f && r.x + r.width <= g.x + g.width + 0.5f && r.y + r.height <= g.y + g.height + 0.5f, "$id $r outside $g")
        }
    }

    /** [pill] (L3 / R3) the shoulder [shoulder] (L / R) is: a rect of its touch size, its own word on it. */
    private fun assertPill(pill: PlacedControl, shoulder: PlacedControl, label: String) {
        assertEquals(ControlShape.RECT, pill.spec.shape, "$label ${pill.spec.id} shape")
        assertEquals(shoulder.spec.hw, pill.spec.hw, 0.5f, "$label ${pill.spec.id} hw"); assertEquals(shoulder.spec.hh, pill.spec.hh, 0.5f, "$label ${pill.spec.id} hh")
        assertEquals(pill.spec.id, pill.spec.mark, "$label ${pill.spec.id} word")
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
            assertEquals(l, r, 1f, "$label left / right"); assertTrue(b > t, "$label no band for the strip")
        }
    }
}
