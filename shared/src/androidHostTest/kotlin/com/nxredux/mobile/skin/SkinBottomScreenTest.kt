package com.nxredux.mobile.skin

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

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
}
