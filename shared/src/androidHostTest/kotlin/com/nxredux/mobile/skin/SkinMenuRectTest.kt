package com.nxredux.mobile.skin

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** The in-game menu's room over a skin: its screen glass and the touch shortcut strip under it (hidden while it is open). */
class SkinMenuRectTest {
    private val carbon = assertNotNull(SkinPackManifest.parse(File("../androidApp/src/main/assets/skins/nx-carbon/skin.json").readText()))

    @Test
    fun the_menu_takes_the_glass_and_the_strip_under_it() {
        val p = assertNotNull(SkinChoice.place(carbon, 1248, 1972, 420, tablet = false, ds = false, picture = 240f to 160f, variant = SkinVariant.GBA))
        val g = assertNotNull(p.glass(GlassScreen.PICTURE))
        val strip = p.controls.filter { it.spec.id.startsWith("TOUCH_") }
        assertTrue(strip.isNotEmpty())
        val r = p.menuRect(g)
        assertEquals(g.rect.x, r.x); assertEquals(g.rect.y, r.y); assertEquals(g.rect.width, r.width)
        assertEquals(strip.maxOf { it.y + it.spec.hh }, r.y + r.height, 0.5f)   // down to the strip's foot
    }

    @Test
    fun a_glass_with_no_strip_under_it_is_the_menus_room_as_it_is() {
        val p = assertNotNull(SkinChoice.place(carbon, 1248, 1972, 420, tablet = false, ds = true, variant = SkinVariant.DS))
        val top = assertNotNull(p.glass(GlassScreen.TOP))   // the DS's strip is under the touch screen, not the top one
        assertEquals(top.rect, p.menuRect(top))
    }
}
