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

    @Test
    fun turboCapsSendNoPadEvent() {
        val p = assertNotNull(SkinChoice.place(pack, 1080, 2520, 420, tablet = false, ds = false, picture = 320f to 224f, variant = SkinVariant.ARCADE))
        assertEquals(SkinVariant.ARCADE, p.spec.variant)
        val events = mutableListOf<PadEvent>(); val actions = mutableListOf<Pair<String, Boolean>>()
        val touch = SkinPadTouch({ events += it }, { id, d -> actions += id to d }, {}).apply { placement = p }
        val tb = p.controls.first { it.spec.id == "TURBO_B" }; val b = p.controls.first { it.spec.id == "B" }
        assertTrue(touch.press(1, tb.x, tb.y))
        assertEquals(listOf("TURBO_B" to true), actions)
        assertEquals(1, touch.pressedShapes().size)
        touch.move(1, b.x, b.y)   // an action cap does not slide (as MIC, SWAP)
        touch.release(1)
        assertEquals(listOf("TURBO_B" to true, "TURBO_B" to false), actions)
        assertTrue(events.isEmpty(), "$events")
    }

    @Test
    fun arcadeFallsBackToSingleWithoutItsLayouts() {
        val old = pack.copy(layouts = pack.layouts.filter { it.variant != SkinVariant.ARCADE })
        val p = assertNotNull(SkinChoice.place(old, 1080, 2520, 420, tablet = false, ds = false, picture = 320f to 224f, variant = SkinVariant.ARCADE))
        assertEquals(SkinVariant.SINGLE, p.spec.variant)
    }

    @Test
    fun aScreenWithoutTheConsolesLayoutTakesTheSharedOne() {
        // a player's pack with its console layouts for portrait only: sideways, the shared one-screen layout, not no skin
        for (v in listOf(SkinVariant.ARCADE, SkinVariant.GB, SkinVariant.GBA)) {
            val partial = pack.copy(layouts = pack.layouts.filterNot { it.variant == v && it.family == SkinFamily.GRIP })
            val land = assertNotNull(SkinChoice.place(partial, 2520, 1080, 420, tablet = false, ds = false, picture = 320f to 224f, variant = v), "$v")
            assertEquals(SkinVariant.SINGLE, land.spec.variant, "$v")
            // portrait keeps the console's own
            assertEquals(v, assertNotNull(SkinChoice.place(partial, 1080, 2520, 420, tablet = false, ds = false, picture = 320f to 224f, variant = v)).spec.variant)
        }
    }

    @Test
    fun dcNeverFallsBackToTheStickless() {
        // the shared one-screen set has no stick: a pack without dc layouts gives a Dreamcast disc no skin
        val noDc = pack.copy(layouts = pack.layouts.filter { it.variant != SkinVariant.DC })
        kotlin.test.assertNull(SkinChoice.place(noDc, 1080, 2520, 420, tablet = false, ds = false, picture = 640f to 480f, variant = SkinVariant.DC))
        kotlin.test.assertNull(SkinChoice.place(noDc, 2520, 1080, 420, tablet = false, ds = false, picture = 640f to 480f, variant = SkinVariant.DC))
    }
}
