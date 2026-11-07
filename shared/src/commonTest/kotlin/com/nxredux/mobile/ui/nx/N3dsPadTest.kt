package com.nxredux.mobile.ui.nx

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class N3dsPadTest {
    @Test fun the_3ds_tag_picks_the_3ds_pad_with_or_without_the_new_controls() {
        assertSame(PadProfile.N3DS, PadProfile.forTag("3DS"))
        assertSame(PadProfile.N3DS, PadProfile.forTag(" 3ds ", n3dsNew = false))
        assertSame(PadProfile.N3DS_NEW, PadProfile.forTag("3DS", n3dsNew = true))
        val ds = PadProfile.forTag("NDS", n3dsNew = true)   // only the 3DS has them: still the DS pad, no C-stick
        assertFalse(ds.cStick)
        assertTrue(ds.isHidden(NxButton.R2))
    }

    @Test fun without_the_new_controls_zl_zr_and_the_c_stick_are_left_off() {
        val p = PadProfile.N3DS
        assertEquals(setOf(NxButton.L2, NxButton.R2), NxButton.entries.filter { p.isHidden(it) }.toSet())
        assertFalse(p.cStick)
    }

    @Test fun with_them_zl_zr_are_drawn_and_named_and_the_c_stick_shows() {
        val p = PadProfile.N3DS_NEW
        assertTrue(NxButton.entries.none { p.isHidden(it) })
        assertEquals("ZL", p.label(NxButton.L2))
        assertEquals("ZR", p.label(NxButton.R2))
        assertTrue(p.cStick)
        assertFalse(PadProfile.STANDARD.cStick)
    }

    @Test fun letters_and_menus_go_by_position_like_the_3ds() {
        for (p in listOf(PadProfile.N3DS, PadProfile.N3DS_NEW)) {
            assertEquals("A", p.label(NxButton.A)); assertEquals("B", p.label(NxButton.B))
            assertEquals("X", p.label(NxButton.X)); assertEquals("Y", p.label(NxButton.Y))
            assertEquals("L", p.label(NxButton.L1)); assertEquals("R", p.label(NxButton.R1))
            for (b in NxButton.entries) assertEquals(b, p.menuButton(b), b.name)
        }
    }
}
