package com.nxredux.mobile.skin

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The built-in NX Onyx pack: NX Carbon's shell in smoked black, built from the same layouts, so it has Carbon's layouts
 * (ids, sizes, controls) with its own pictures, and every picture it names is there.
 */
class NxOnyxPackTest {
    private val skins = File("../androidApp/src/main/assets/skins")
    private fun pack(id: String) = assertNotNull(SkinPackManifest.parse(File(skins, "$id/skin.json").readText()), id)

    @Test
    fun onyxHasCarbonsLayoutsAndItsOwnPictures() {
        val onyx = pack("nx-onyx"); val carbon = pack("nx-carbon")
        assertEquals("nx-onyx", onyx.id); assertEquals("NX Onyx", onyx.name)
        assertEquals(carbon.layouts.map { it.id }, onyx.layouts.map { it.id })
        for ((o, c) in onyx.layouts.zip(carbon.layouts)) {
            assertEquals(c.width to c.height, o.width to o.height, o.id)
            assertEquals(c.controls.map { it.id to (it.x to it.y) }, o.controls.map { it.id to (it.x to it.y) }, o.id)
            assertTrue(File(skins, "nx-onyx/${o.image}").isFile, o.image)
            o.parts?.let { assertTrue(File(skins, "nx-onyx/$it").isFile, it) }
        }
    }
}
