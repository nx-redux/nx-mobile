package com.nxredux.mobile.skin

import com.nxredux.mobile.options.PxRect
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** The bare skin (the overlay over the full-screen game): the built-in pack's buttons without the board, frame or screens. */
class SkinBareTest {
    private val pack = assertNotNull(SkinPackManifest.parse(File("../androidApp/src/main/assets/skins/nx-carbon/skin.json").readText()))

    @Test
    fun bare_drops_glass_pieces_and_the_frame() {
        val p = assertNotNull(SkinChoice.place(pack, 1248, 1972, 420, tablet = false, ds = true))
        assertEquals("large_ds", p.spec.id)
        val glassPieces = p.spec.pieces.indices.filter { p.spec.pieces[it].glass != null }
        assertTrue(glassPieces.isNotEmpty() && p.pieces.any { it.piece in glassPieces }, "large_ds draws a glass piece")
        assertTrue(p.pieces.any { it.piece < 0 && !it.strip }, "the frame is drawn in pieces")
        val b = p.bare()
        assertTrue(b.cells.isEmpty(), "no board")
        assertTrue(b.pieces.none { it.piece in glassPieces }, "no glass piece")
        assertTrue(b.pieces.none { it.piece < 0 && !it.strip }, "no frame")
        assertEquals(p.pieces.filter { it.piece >= 0 && it.piece !in glassPieces }, b.pieces, "every button piece kept, in order")
        assertEquals(p.controls, b.controls)
        assertEquals(p.glasses, b.glasses)
        assertEquals(p.spec, b.spec)
        assertEquals(p.k, b.k)
    }

    @Test
    fun bare_keeps_strip_pieces() {
        val p0 = assertNotNull(SkinChoice.place(pack, 1080, 2400, 420, tablet = false, ds = true))
        assertEquals("stacked_ds", p0.spec.id)
        // the built-in packs write their strip slots (no label pieces): one drawn label, as a pack may have
        val label = SkinPiece(PxRect(0f, 0f, 40f, 17f), PxRect(100f, 900f, 40f, 17f), strip = true)
        val p = p0.copy(pieces = p0.pieces + label)
        val b = p.bare()
        assertEquals(listOf(label), b.pieces.filter { it.strip })
        val strip = listOf("TOUCH_1", "TOUCH_2", "TOUCH_3", "TOUCH_4")
        assertEquals(strip, b.controls.map { it.spec.id }.filter { it in strip })
    }

    @Test
    fun overlay_mode() {
        assertEquals(OverlayMode.SKIN, overlayMode(padSkinOn = true, placed = true, controller = false))
        assertEquals(OverlayMode.BARE, overlayMode(padSkinOn = true, placed = false, controller = false))   // no layout fits
        assertEquals(OverlayMode.BARE, overlayMode(padSkinOn = false, placed = false, controller = false))
        assertEquals(OverlayMode.BARE, overlayMode(padSkinOn = false, placed = true, controller = false))
        for (on in listOf(true, false)) for (placed in listOf(true, false))
            assertEquals(OverlayMode.NONE, overlayMode(on, placed, controller = true))   // a controller: no pad
    }
}
