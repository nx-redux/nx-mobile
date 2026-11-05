package com.nxredux.mobile.skin

import com.nxredux.mobile.options.PxRect
import com.nxredux.mobile.runner.DualMode
import com.nxredux.mobile.runner.PictureOn
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SkinDualTest {
    private val glass = PlacedGlass("panel", GlassScreen.PANEL, PxRect(0f, 0f, 10f, 10f), 1f)

    @Test
    fun skinShowsOffPanelAndDsSplitOnly() {
        assertTrue(SkinDual.skinAllowed(DualMode.OFF, ds = false, n3ds = false))
        assertTrue(SkinDual.skinAllowed(DualMode.PANEL, ds = false, n3ds = false))
        assertTrue(SkinDual.skinAllowed(DualMode.SPLIT, ds = true, n3ds = false))
        assertFalse(SkinDual.skinAllowed(DualMode.SPLIT, ds = false, n3ds = false))
        assertFalse(SkinDual.skinAllowed(DualMode.PANEL_UP, ds = true, n3ds = false))
    }

    @Test
    fun n3dsSkinWithDualScreenOffAndSplit() {
        assertTrue(SkinDual.skinAllowed(DualMode.OFF, ds = false, n3ds = true))
        assertTrue(SkinDual.skinAllowed(DualMode.SPLIT, ds = false, n3ds = true))   // the bottom screen in the landscape skin
        assertTrue(SkinDual.skinAllowed(DualMode.PANEL, ds = false, n3ds = true))
        assertFalse(SkinDual.skinAllowed(DualMode.PANEL_UP, ds = false, n3ds = true))
    }

    @Test
    fun thePanelIsInOnePlaceOnly() {
        for (mode in DualMode.entries) for (on in PictureOn.entries) for (second in listOf(true, false)) for (g in listOf(glass, null)) {
            val spot = SkinDual.panelSpot(mode, on, second, g)
            val case = "$mode $on $second ${g != null}"
            if (spot == PanelSpot.SKIN_GLASS) assertTrue(g != null, case)
            if (spot == PanelSpot.SECOND_DISPLAY) assertTrue(second && on == PictureOn.MAIN, case)
            if (mode == DualMode.SPLIT) assertEquals(PanelSpot.NONE, spot, case)
        }
        assertEquals(PanelSpot.SKIN_GLASS, SkinDual.panelSpot(DualMode.OFF, PictureOn.MAIN, false, glass))
        assertEquals(PanelSpot.NONE, SkinDual.panelSpot(DualMode.OFF, PictureOn.MAIN, false, null))
        assertEquals(PanelSpot.SKIN_GLASS, SkinDual.panelSpot(DualMode.PANEL, PictureOn.SECOND, true, glass))
        assertEquals(PanelSpot.WINDOW, SkinDual.panelSpot(DualMode.PANEL, PictureOn.SECOND, true, null))
        assertEquals(PanelSpot.SECOND_DISPLAY, SkinDual.panelSpot(DualMode.PANEL, PictureOn.MAIN, true, glass))
    }

    @Test
    fun aDsScreenIsFittedFourByThreeInTheGlass() {
        assertEquals(PxRect(0f, 150f, 800f, 600f), SkinDual.dsScreenIn(PxRect(0f, 0f, 800f, 900f)))
        assertEquals(PxRect(150f, 0f, 800f, 600f), SkinDual.dsScreenIn(PxRect(0f, 0f, 1100f, 600f)))
    }
}
