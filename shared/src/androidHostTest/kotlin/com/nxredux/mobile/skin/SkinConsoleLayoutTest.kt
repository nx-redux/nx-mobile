package com.nxredux.mobile.skin

import com.nxredux.mobile.ui.nx.NxButton
import com.nxredux.mobile.ui.nx.PadEvent
import com.nxredux.mobile.ui.nx.SkinPadTouch
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** The GB family and GBA layouts of the built-in NX Carbon pack: picked by variant, with a fallback, and TL / TR pressing L2 / R2. */
class SkinConsoleLayoutTest {
    private val pack = assertNotNull(SkinPackManifest.parse(File("../androidApp/src/main/assets/skins/nx-carbon/skin.json").readText()))
    private val gba = 240f to 160f

    @Test
    fun aGameBoyGameGetsTheGbLayout() {
        val p = assertNotNull(SkinChoice.place(pack, 1080, 2520, 420, tablet = false, ds = false, picture = 160f to 144f, variant = SkinVariant.GB))
        assertEquals(SkinVariant.GB, p.spec.variant)
    }

    @Test
    fun aPackWithoutTheVariantFallsBackToSingle() {
        val old = pack.copy(layouts = pack.layouts.filter { it.variant != SkinVariant.GB })
        val p = assertNotNull(SkinChoice.place(old, 1080, 2520, 420, tablet = false, ds = false, picture = 160f to 144f, variant = SkinVariant.GB))
        assertEquals(SkinVariant.SINGLE, p.spec.variant)
    }

    @Test
    fun tlAndTrPressL2AndR2() {
        val p = assertNotNull(SkinChoice.place(pack, 1080, 2520, 420, tablet = false, ds = false, picture = gba, variant = SkinVariant.GBA))
        val events = mutableListOf<PadEvent>()
        val touch = SkinPadTouch({ events += it }, { _, _ -> }, {}).apply { placement = p }
        val tl = p.controls.first { it.spec.id == "L2" }; val tr = p.controls.first { it.spec.id == "R2" }
        assertTrue(touch.press(1, tl.x, tl.y)); assertTrue(touch.press(2, tr.x, tr.y))
        assertTrue(touch.pressedShapes().size == 2)
        assertEquals(listOf<PadEvent>(PadEvent.Down(NxButton.L2), PadEvent.Down(NxButton.R2)), events)
    }
}
