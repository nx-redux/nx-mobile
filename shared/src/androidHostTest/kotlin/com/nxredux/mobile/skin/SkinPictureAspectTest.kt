package com.nxredux.mobile.skin

import com.nxredux.mobile.layout.CONSOLE_FRAMES
import com.nxredux.mobile.runner.CoreRegistry
import com.nxredux.mobile.runner.DualScreenConsole
import com.nxredux.mobile.runner.WiiPad
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Button layout's editor places a one-screen console at 4:3 (the game's picture until its first frame); the game then
 * places it again at its core's aspect. Tweaks are keyed by the layout id, so both must land on the same layout: for
 * the built-in packs the picture's shape never changes which layout a window gets.
 */
class SkinPictureAspectTest {
    /** Fold cover, Fold inner (a tablet-class window), a phone; each upright and sideways. (w, h, tablet, foldable) */
    private val windows = listOf(
        Window(904, 2316, false, true), Window(1812, 2176, true, true), Window(1080, 2400, false, false),
    ).flatMap { listOf(it, it.copy(w = it.h, h = it.w)) }

    private data class Window(val w: Int, val h: Int, val tablet: Boolean, val foldable: Boolean)

    /** The console's picture as it is drawn: its core's aspect, or its frame's shape where it reports square pixels. */
    private fun native(id: String): Pair<Float, Float> {
        val f = CONSOLE_FRAMES[id] ?: return (4f / 3f * 240f) to 240f
        val a = if (f.aspect > 0f) f.aspect else f.width / f.height
        return a * 240f to 240f
    }

    @Test
    fun a_consoles_picture_shape_never_changes_its_layout() {
        val fourThree = (4f / 3f * 240f) to 240f
        var compared = 0
        for (packId in listOf("nx-carbon", "nx-onyx", "nx-obsidian", "nx-plasma", "nx-ember", "nx-volt", "nx-tide")) {
            val pack = skinPack(packId)
            for (console in CoreRegistry.consoles) {
                val tag = console.defaultTag
                if (DualScreenConsole.appliesTo(tag)) continue   // two screens: no picture passed
                val base = CoreRegistry.baseTag(tag)
                val pads = if (console.id == "wii") WiiPad.entries.toList() else listOf(null)
                for (pad in pads) for (arcade in if (console.id == "dc") listOf(false, true) else listOf(false)) {
                    val variant = SkinChoice.variantFor(base, false, arcade, console.id == "gc", pad)
                    for (win in windows) {
                        val at43 = SkinChoice.placeClosest(pack, win.w, win.h, 420, win.tablet, false, fourThree, foldable = win.foldable, variant = variant)
                        val atNative = SkinChoice.placeClosest(pack, win.w, win.h, 420, win.tablet, false, native(console.id), foldable = win.foldable, variant = variant)
                        assertEquals(at43?.spec?.id, atNative?.spec?.id, "$packId ${console.id} $pad arcade=$arcade $win")
                        val full43 = SkinChoice.place(pack, win.w, win.h, 420, win.tablet, false, fourThree, foldable = win.foldable, variant = variant)
                        val fullNative = SkinChoice.place(pack, win.w, win.h, 420, win.tablet, false, native(console.id), foldable = win.foldable, variant = variant)
                        assertEquals(full43?.spec?.id, fullNative?.spec?.id, "place: $packId ${console.id} $pad arcade=$arcade $win")
                        if (at43 != null) compared++
                    }
                }
            }
        }
        assertTrue(compared > 100, "only $compared placements compared")
    }

    @Test
    fun the_gamepads_glasses_are_16_by_9_or_two_of_them_stacked() {
        var seen = 0
        for (packId in listOf("nx-carbon", "nx-onyx", "nx-obsidian", "nx-plasma", "nx-ember", "nx-volt", "nx-tide")) {
            for (spec in skinPack(packId).layouts.filter { it.variant == SkinVariant.WIIU }) {
                assertTrue(spec.id.endsWith("_wiiugp") || "_wiiugp_" in spec.id, spec.id)
                val glasses = listOfNotNull(spec.frame?.glass) + spec.pieces.mapNotNull { it.glass }
                for (g in glasses) {
                    val want = if (g.screen == GlassScreen.STACK) 16f / 18f else 16f / 9f
                    assertTrue(g.screen in setOf(GlassScreen.STACK, GlassScreen.TOP, GlassScreen.TOUCH), "$packId ${spec.id} ${g.screen}")
                    assertEquals(want, g.w.toFloat() / g.h, 0.01f, "$packId ${spec.id} ${g.screen}")
                }
                assertTrue(glasses.map { it.screen }.toSet().let { it == setOf(GlassScreen.STACK) || it == setOf(GlassScreen.TOP, GlassScreen.TOUCH) }, "$packId ${spec.id}")
                seen++
            }
        }
        assertEquals(7 * 10, seen)   // the GamePad's ten layouts in each pack
    }
}
