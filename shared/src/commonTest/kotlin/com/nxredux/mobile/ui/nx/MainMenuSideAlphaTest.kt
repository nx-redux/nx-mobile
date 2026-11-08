package com.nxredux.mobile.ui.nx

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// The main menu's Consoles and Collections, horizontal and vertical, fade their unselected items further.
class MainMenuSideAlphaTest {
    @Test fun theStops() {
        assertEquals(1f, mainMenuSideAlpha(0f), 1e-4f)
        assertEquals(0.35f, mainMenuSideAlpha(1f), 1e-4f)
        assertEquals(0.35f, mainMenuSideAlpha(-1f), 1e-4f)
        assertEquals(0.2f, mainMenuSideAlpha(2f), 1e-4f)
        assertEquals(0.2f, mainMenuSideAlpha(3f), 1e-4f)
        assertEquals(0.1f, mainMenuSideAlpha(-3.5f), 1e-4f)
        assertEquals(0f, mainMenuSideAlpha(4f), 1e-4f)
        assertEquals(0f, mainMenuSideAlpha(6f), 1e-4f)
    }

    @Test fun itIsSmoothAndNeverRisesOutward() {
        var last = mainMenuSideAlpha(0f)
        var d = 0.01f
        while (d <= 4.5f) {
            val a = mainMenuSideAlpha(d)
            assertTrue(a <= last + 1e-5f, "rises at $d")
            assertTrue(last - a < 0.01f, "jumps at $d")
            last = a
            d += 0.01f
        }
    }

    // The first two steps sit below the row's own fade (0.5, 0.38); beyond too.
    @Test fun belowTheRowsOwnFade() {
        val g = backdropSlotGeometry(BackdropSlot.COLLECTION, 358f)
        for (d in listOf(0.5f, 1f, 1.5f, 2f, 3f, 3.5f)) assertTrue(mainMenuSideAlpha(d) <= rowItem(RowStyle.BACKDROP, d, g).alpha + 1e-4f, "at $d")
    }

    @Test fun onlyConsolesAndCollections() {
        assertTrue(fadesFurther(BackdropSlot.LOGO))
        assertTrue(fadesFurther(BackdropSlot.COLLECTION))
        assertFalse(fadesFurther(BackdropSlot.TOOL))
        assertFalse(fadesFurther(BackdropSlot.BOX))
    }
}
