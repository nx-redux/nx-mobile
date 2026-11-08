package com.nxredux.mobile.skin

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The built-in colour packs (NX Onyx: Carbon's shell in smoked black; NX Obsidian: darker, near-black; NX Plasma, Ember, Volt, Tide: translucent purple,
 * red, green, blue): built from Carbon's layouts, so each has Carbon's layouts (ids, sizes, controls) with its own
 * pictures, and every picture it names is there.
 */
class NxOnyxPackTest {
    private val skins = File("../androidApp/src/main/assets/skins")
    private fun pack(id: String) = assertNotNull(SkinPackManifest.parse(File(skins, "$id/skin.json").readText()), id)

    @Test
    fun onyxHasCarbonsLayoutsAndItsOwnPictures() = sameAsCarbon("nx-onyx", "NX Onyx")

    @Test
    fun theTranslucentColoursHaveCarbonsLayoutsAndTheirOwnPictures() {
        for ((id, name) in listOf("nx-obsidian" to "NX Obsidian", "nx-plasma" to "NX Plasma", "nx-ember" to "NX Ember", "nx-volt" to "NX Volt", "nx-tide" to "NX Tide")) sameAsCarbon(id, name)
    }

    private fun sameAsCarbon(id: String, name: String) {
        val onyx = pack(id); val carbon = pack("nx-carbon")
        assertEquals(id, onyx.id); assertEquals(name, onyx.name)
        assertEquals(carbon.layouts.map { it.id }, onyx.layouts.map { it.id })
        for ((o, c) in onyx.layouts.zip(carbon.layouts)) {
            assertEquals(c.width to c.height, o.width to o.height, o.id)
            assertEquals(c.controls.map { it.id to (it.x to it.y) }, o.controls.map { it.id to (it.x to it.y) }, o.id)
            // the screen frame placed alike (its margins, the hinge gap): a pack built before a layout change shows it
            assertEquals(c.frame?.let { listOf(it.side, it.top, it.hinge, it.bottom, it.corner) }, o.frame?.let { listOf(it.side, it.top, it.hinge, it.bottom, it.corner) }, o.id)
            // the pieces' glasses (the fold board's touch and panel screens) alike
            assertEquals(c.pieces.mapNotNull { it.glass }, o.pieces.mapNotNull { it.glass }, o.id)
            assertTrue(File(skins, "$id/${o.image}").isFile, o.image)
            o.parts?.let { assertTrue(File(skins, "$id/$it").isFile, it) }
        }
    }
}
