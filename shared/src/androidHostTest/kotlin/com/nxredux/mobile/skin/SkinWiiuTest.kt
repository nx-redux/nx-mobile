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
}
