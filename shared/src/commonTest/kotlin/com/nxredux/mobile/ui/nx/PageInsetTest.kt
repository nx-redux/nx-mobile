package com.nxredux.mobile.ui.nx

import kotlin.test.Test
import kotlin.test.assertEquals

/** Every screen's side inset ([nxPageInset]): Home List's, with the camera cutout counted inside it. */
class PageInsetTest {
    @Test fun the_inset_is_home_lists_old_gutter_plus_the_row_inset() {
        assertEquals(30f, pageInset(portrait = true))
        assertEquals(38f, pageInset(portrait = false))
        assertEquals(62f, pageInset(portrait = true, tablet = true))
        assertEquals(62f, pageInset(portrait = false, tablet = true))
    }

    @Test fun sideways_the_camera_side_takes_the_cutout_when_deeper() {
        // The Fold cover held sideways: a 40 dp cutout on one side, none on the other.
        val inset = pageInset(portrait = false)
        assertEquals(40f, pageInsetSide(inset, cutout = 40f))
        assertEquals(38f, pageInsetSide(inset, cutout = 0f))
    }

    @Test fun a_shallow_cutout_sits_inside_the_inset() {
        val inset = pageInset(portrait = false)
        assertEquals(38f, pageInsetSide(inset, cutout = 20f))
        assertEquals(38f, pageInsetSide(inset, cutout = 0f))
    }

    @Test fun upright_the_hole_is_in_the_status_bar() {
        val inset = pageInset(portrait = true)
        assertEquals(30f, pageInsetSide(inset, cutout = 0f))
        assertEquals(30f, pageInsetSide(inset, cutout = 0f))
    }

    @Test fun a_highlight_reaches_the_row_inset_outside() {
        val inset = PageInset(start = androidx.compose.ui.unit.Dp(40f), end = androidx.compose.ui.unit.Dp(38f))
        assertEquals(26f, inset.pillStart.value)
        assertEquals(24f, inset.pillEnd.value)
    }
}
