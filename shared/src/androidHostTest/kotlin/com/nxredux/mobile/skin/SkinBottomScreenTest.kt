package com.nxredux.mobile.skin

import com.nxredux.mobile.options.NdsScreen
import com.nxredux.mobile.runner.DualMode
import com.nxredux.mobile.runner.PictureOn
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** The game panel's glass in the built-in NX Carbon pack: a one-screen game's bottom screen on the fold board. */
class SkinBottomScreenTest {
    private val text = File("../androidApp/src/main/assets/skins/nx-carbon/skin.json").readText()

    @Test
    fun aPanelGlassParsesAndIsPlacedAtItsPieceSize() {
        // the two-screen menu's touch glass, marked as a panel glass
        val marked = text.replace("\"id\": \"menu\",\n      \"screen\": \"touch\"", "\"id\": \"panel\",\n      \"screen\": \"panel\"")
        val pack = assertNotNull(SkinPackManifest.parse(marked))
        val spec = pack.layouts.single { it.id == "stacked_menu_fold" }
        val p = assertNotNull(SkinSolver.placePieces(spec, 1848f, 2448f, 1f, hingeY = 1224f))
        val g = assertNotNull(p.glass(GlassScreen.PANEL))
        assertEquals("panel", g.id)
        assertEquals(944f, g.rect.width, 1f)
    }

    private val pack = assertNotNull(SkinPackManifest.parse(text))

    @Test
    fun theFoldBoardGlassIsThePanelWithoutASecondDisplay() {
        val single = assertNotNull(SkinChoice.place(pack, 1848, 2448, 420, tablet = true, ds = false, picture = 240f to 160f, foldable = true))
        assertEquals("panel", SkinDual.panelGlass(single, DualMode.OFF, PictureOn.MAIN)?.id)
        val ds = assertNotNull(SkinChoice.place(pack, 1848, 2448, 420, tablet = true, ds = true, foldable = true))
        assertNull(SkinDual.panelGlass(ds, DualMode.OFF, PictureOn.MAIN))
    }

    @Test
    fun theGripPictureGlassIsThePanelWhenThePictureIsOnTheSecondDisplay() {
        val grip = assertNotNull(SkinChoice.place(pack, 2448, 1848, 420, tablet = true, ds = false, picture = 240f to 160f, foldable = true))
        assertEquals(GlassScreen.PICTURE, SkinDual.panelGlass(grip, DualMode.PANEL, PictureOn.SECOND)?.screen)
        assertNull(SkinDual.panelGlass(grip, DualMode.PANEL, PictureOn.MAIN))
        assertNull(SkinDual.panelGlass(grip, DualMode.OFF, PictureOn.MAIN))
        assertNull(SkinDual.panelGlass(null, DualMode.OFF, PictureOn.MAIN))
    }

    @Test
    fun aPortraitFoldBoardNeverPutsThePanelInItsTopScreen() {
        // a second display came before the turn to landscape (or the lock is ignored): no glass, the panel covers the window
        val single = assertNotNull(SkinChoice.place(pack, 1848, 2448, 420, tablet = true, ds = false, picture = 240f to 160f, foldable = true))
        assertNull(SkinDual.panelGlass(single, DualMode.PANEL, PictureOn.SECOND))
    }

    @Test
    fun aDsSplitGoesInTheScreenGlassItShows() {
        val fold = assertNotNull(SkinChoice.place(pack, 1848, 2448, 420, tablet = true, ds = true, foldable = true))
        assertEquals(fold.glass(GlassScreen.TOUCH)!!.rect, SkinDual.splitGlass(fold, NdsScreen.BOTTOM))
        assertEquals(fold.glass(GlassScreen.TOP)!!.rect, SkinDual.splitGlass(fold, NdsScreen.TOP))
        val grip = assertNotNull(SkinChoice.place(pack, 2448, 1848, 420, tablet = true, ds = true, foldable = true))
        assertEquals(grip.glass(GlassScreen.STACK)!!.rect, SkinDual.splitGlass(grip, NdsScreen.BOTTOM))
        assertNull(SkinDual.splitGlass(null, NdsScreen.BOTTOM))
    }
}
