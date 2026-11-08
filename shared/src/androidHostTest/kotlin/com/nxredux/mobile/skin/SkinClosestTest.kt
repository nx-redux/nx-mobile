package com.nxredux.mobile.skin

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The closest layout, scaled, for windows no layout fits. */
class SkinClosestTest {
    private val carbon = assertNotNull(
        SkinPackManifest.parse(File("../androidApp/src/main/assets/skins/nx-carbon/skin.json").readText()),
    )
    private val gba = 240f to 160f

    @Test
    fun a_fitting_window_gets_the_same_placement_as_place() {
        val a = assertNotNull(SkinChoice.place(carbon, 1080, 2520, 420, false, false, gba))
        val b = assertNotNull(SkinChoice.placeClosest(carbon, 1080, 2520, 420, false, false, gba))
        assertEquals(a, b)
    }

    @Test
    fun a_split_screen_window_gets_a_smaller_scaled_layout() {
        assertNull(SkinChoice.place(carbon, 1080, 1100, 420, false, false, gba))
        val full = assertNotNull(SkinChoice.place(carbon, 1080, 2520, 420, false, false, gba))
        val c = assertNotNull(SkinChoice.placeClosest(carbon, 1080, 1100, 420, false, false, gba))
        assertTrue(c.k < full.k, "${c.k} vs ${full.k}")
    }

    @Test
    fun gives_up_below_half_scale() {
        assertNull(SkinChoice.placeClosest(carbon, 200, 200, 420, false, false, gba))
    }
}
