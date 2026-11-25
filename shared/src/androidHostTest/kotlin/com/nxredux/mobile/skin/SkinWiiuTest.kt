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
            "phone" to "stacked_wiiugp", "cover" to "large_wiiugp", "phone_land" to "grip_wiiugp", "cover_land" to "grip_wiiugp",
            "inner" to "flex_wiiugp_fold", "inner_half" to "flex_wiiugp_fold", "ultra" to "flex_wiiugp_fold", "ultra_land" to "flex_wiiugp_fold",
            "ultra_half" to "flex_wiiugp_fold", "ultra_land_half" to "flex_wiiugp_fold", "flip_half" to "flex_wiiugp",
        )
        for (d in GOLDEN_DEVICES) want[d.name]?.let { assertEquals(it, place(pack, d)?.spec?.id, d.name) }
        // the Fold cover's large layout: both glasses the screens' own shape, no bars
        val cover = assertNotNull(place(pack, GOLDEN_DEVICES.single { it.name == "cover" }))
        for (g in listOf(GlassScreen.TOP, GlassScreen.TOUCH)) assertEquals(16f / 9f, aspect(assertNotNull(cover.glass(g)).rect), 0.01f, "$g")
    }

    /**
     * Sideways on a phone or a tablet with no fold, the GamePad has no column twin: the 860-tall landscape shell takes every
     * window up to its tall twin's height.
     */
    @Test
    fun a_landscape_window_below_the_tall_twin_takes_the_row_shell() {
        for (id in packs) {
            val pack = skinPack(id)
            for ((w, h) in listOf(2640 to 1200, 2700 to 1500)) {
                val p = assertNotNull(SkinChoice.place(pack, w, h, 420, tablet = false, ds = true, variant = SkinVariant.WIIU), "$id ${w}x$h")
                assertEquals("grip_wiiugp", p.spec.id, "$id ${w}x$h")
                assertNotNull(p.glass(GlassScreen.STACK), "$id ${w}x$h")
            }
            assertEquals("grip_wiiugp_tall", SkinChoice.place(pack, 2700, 1600, 420, tablet = false, ds = true, variant = SkinVariant.WIIU)?.spec?.id, id)
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
     * No touch shortcut strip (the Wii U offers none), and every screen's frame as thick under its glass as over it and
     * as thick right as left: no black band under the picture.
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
     * The roomy landscape shell (the unfolded Fold sideways): the right stick the left's size, level with it; the face
     * level with the d-pad under it; L3 at the left stick's lower right and R3 at the right stick's lower left.
     */
    @Test
    fun the_unfolded_fold_sideways_mirrors_the_sticks() {
        for (id in packs) {
            val pack = skinPack(id)
            for (name in listOf("inner_land", "inner_land_half", "tablet_land")) {
                val d = GOLDEN_DEVICES.single { it.name == name }
                val label = "$id $name"
                val p = assertNotNull(place(pack, d), label)
                assertEquals("grip_wiiugp_tall", p.spec.id, label)
                val c = p.controls.associateBy { it.spec.id }
                val st = c.getValue("STICK"); val cs = c.getValue("CSTICK"); val dp = c.getValue("DPAD")
                assertEquals(st.spec.r, cs.spec.r, 0.5f, "$label stick sizes")
                assertEquals(st.y, cs.y, 1f, "$label stick heights")
                val face = listOf("X", "Y", "A", "B").map { c.getValue(it) }
                assertEquals(dp.y, face.map { it.y }.average().toFloat(), 1f, "$label face height")
                val l3 = c.getValue("L3"); val r3 = c.getValue("R3")
                assertTrue(l3.x > st.x && l3.y > st.y, "$label L3 at ${l3.x},${l3.y}")
                assertTrue(r3.x < cs.x && r3.y > cs.y, "$label R3 at ${r3.x},${r3.y}")
                assertEquals(l3.y, r3.y, 1f, "$label L3 / R3 level")
            }
        }
    }
}
