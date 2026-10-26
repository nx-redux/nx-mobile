package com.nxredux.mobile.runner

import com.nxredux.mobile.layout.ScreenClass
import com.nxredux.mobile.options.NdsLayoutChoice
import com.nxredux.mobile.options.NdsOptions
import com.nxredux.mobile.options.NdsScreen
import com.nxredux.mobile.options.NdsSettings
import com.nxredux.mobile.options.PxRect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class Nds3dResolutionTest {
    private fun region(width: Float, inset: Boolean = false) = NdsRegion(
        NdsScreen.TOP, NdsLayout.TOP_SRC, PxRect(0f, 0f, width, width * 0.75f), shaded = true, touch = false, inset = inset,
    )

    @Test
    fun currentScaleTakesTheBiggerScreen() {
        assertEquals(6, Nds3dResolution.currentScale(listOf(region(1536f), region(768f))))
    }

    @Test
    fun pipInsetIsIgnored() {
        assertEquals(6, Nds3dResolution.currentScale(listOf(region(1536f), region(512f, inset = true))))
        assertNull(Nds3dResolution.currentScale(listOf(region(512f, inset = true))))
    }

    @Test
    fun nonWholeRoundsUp() {
        assertEquals(2, Nds3dResolution.currentScale(listOf(region(499f))))
    }

    @Test
    fun swappedSingleScreenFollowsTheShownScreen() {
        val space = PxRect(0f, 0f, 2448f, 1848f)
        val top = NdsLayout.regions(space, NdsLayoutChoice.SINGLE, false, ScreenClass.NEAR_SQUARE_TABLET, NdsSettings(big = NdsScreen.TOP), 0f)
        val bottom = NdsLayout.regions(space, NdsLayoutChoice.SINGLE, false, ScreenClass.NEAR_SQUARE_TABLET, NdsSettings(big = NdsScreen.BOTTOM), 0f)
        assertEquals(9, Nds3dResolution.currentScale(top))
        assertEquals(Nds3dResolution.currentScale(top), Nds3dResolution.currentScale(bottom))
    }

    @Test
    fun displayMaxPerDisplay() {
        assertEquals(9, Nds3dResolution.displayMax(1848f, 2448f, ScreenClass.NEAR_SQUARE_TABLET))
        assertEquals(5, Nds3dResolution.displayMax(1080f, 2400f, ScreenClass.REGULAR_PHONE))
        assertEquals(5, Nds3dResolution.displayMax(1920f, 1080f, ScreenClass.REGULAR_PHONE))
        assertEquals(7, Nds3dResolution.displayMax(2560f, 1600f, ScreenClass.WIDE_TABLET))
    }

    @Test
    fun performanceCeilingRoundsUp() {
        assertEquals(2, Nds3dResolution.performanceCeiling(3))
        assertEquals(3, Nds3dResolution.performanceCeiling(5))
        assertEquals(4, Nds3dResolution.performanceCeiling(7))
        assertEquals(5, Nds3dResolution.performanceCeiling(9))
        assertEquals(1, Nds3dResolution.performanceCeiling(1))
    }

    @Test
    fun modes() {
        assertEquals(4, Nds3dResolution.value(Nds3dMode.AUTO, 4, 2, 6, 9))
        assertEquals(3, Nds3dResolution.value(Nds3dMode.AUTO, 4, 2, 3, 9))
        assertEquals(6, Nds3dResolution.value(Nds3dMode.QUALITY, 4, 2, 6, 9))
        assertEquals(8, Nds3dResolution.value(Nds3dMode.QUALITY, 4, 2, 9, 9))
        assertEquals(2, Nds3dResolution.value(Nds3dMode.PERFORMANCE, 4, 2, 6, 9))
    }

    @Test
    fun noRegionsUsesTheFallback() {
        assertEquals(4, Nds3dResolution.value(Nds3dMode.AUTO, 4, 2, null, 9))
        assertEquals(5, Nds3dResolution.value(Nds3dMode.QUALITY, 4, 2, null, 5))
    }

    @Test
    fun storedValuesAboveTheMaximumAreClamped() {
        assertEquals(2, Nds3dResolution.value(Nds3dMode.PERFORMANCE, 4, 4, 3, 3))
        assertEquals(5, Nds3dResolution.value(Nds3dMode.AUTO, 8, 2, 5, 5))
    }

    @Test
    fun parseFallsBackToAuto() {
        assertEquals(Nds3dMode.AUTO, Nds3dMode.parse(null))
        assertEquals(Nds3dMode.AUTO, Nds3dMode.parse("nonsense"))
        assertEquals(Nds3dMode.QUALITY, Nds3dMode.parse("quality"))
    }

    @Test
    fun onlyOnChange() {
        val s = Nds3dSender()
        assertEquals(4, s.offer(4, false, 0))
        assertNull(s.offer(4, false, 10))
        assertEquals(6, s.offer(6, false, 20))
    }

    @Test
    fun sizeChangesSettleOtherChangesDoNot() {
        val s = Nds3dSender()
        assertNull(s.offer(5, true, 0))
        assertNull(s.offer(6, true, 100))
        assertNull(s.tick(350))
        assertEquals(6, s.tick(401))
        assertNull(s.tick(500))
        assertEquals(3, s.offer(3, false, 600))
    }

    @Test
    fun aSettlingValueIsPendingUntilSentOrOvertaken() {
        val s = Nds3dSender()
        assertFalse(s.pending)
        s.offer(5, true, 0)
        assertTrue(s.pending)
        s.tick(400)
        assertFalse(s.pending)
        s.offer(6, true, 500)
        s.offer(7, false, 510)   // a rotation meanwhile goes at once and drops the held size
        assertFalse(s.pending)
    }

    @Test
    fun valueForReadsTheStoredRows() {
        val auto = mapOf(NdsOptions.MODE_3D to "auto", NdsOptions.MAX_SCALE_3D to "3")
        assertEquals(3, Nds3dResolution.valueFor(auto, current = 6, displayMax = 7))
        val perf = mapOf(NdsOptions.MODE_3D to "performance", NdsOptions.SCALE_3D to "1")
        assertEquals(1, Nds3dResolution.valueFor(perf, current = 6, displayMax = 7))
        assertEquals(6, Nds3dResolution.valueFor(mapOf(NdsOptions.MODE_3D to "quality"), current = 6, displayMax = 7))
    }

    @Test
    fun valueForUnsetRowsUsesTheRowDefaults() {
        assertEquals(Nds3dResolution.DEFAULT_CAP, Nds3dResolution.valueFor(emptyMap(), current = 6, displayMax = 7))
        assertEquals(2, Nds3dResolution.valueFor(mapOf(NdsOptions.MODE_3D to "performance"), current = 6, displayMax = 7))
        assertEquals(Nds3dResolution.DEFAULT_CAP, Nds3dResolution.valueFor(mapOf(NdsOptions.MAX_SCALE_3D to "junk"), current = 6, displayMax = 7))
    }

    @Test
    fun displayMaxOfAWindowNeedsItsSizeAndClass() {
        assertEquals(5, Nds3dResolution.displayMaxOf(1080 to 2400, ScreenClass.REGULAR_PHONE))
        assertNull(Nds3dResolution.displayMaxOf(null, ScreenClass.REGULAR_PHONE))
        assertNull(Nds3dResolution.displayMaxOf(1080 to 2400, null))
        assertNull(Nds3dResolution.displayMaxOf(0 to 2400, ScreenClass.REGULAR_PHONE))
    }

    @Test
    fun onlyAResizeInPlaceIsASizeChange() {
        val a = Nds3dArea(1080f, 2000f, ScreenClass.REGULAR_PHONE, 0, landscape = false, halfFolded = false, dual = DualMode.OFF)
        assertFalse(a.isSizeChangeFrom(null))                                  // the first layout
        assertFalse(a.isSizeChangeFrom(a))                                     // nothing changed (the hotkey, SWAP)
        assertTrue(a.copy(width = 900f).isSizeChangeFrom(a))                   // a DeX window dragged
        assertFalse(Nds3dArea(2000f, 1080f, ScreenClass.REGULAR_PHONE, 0, true, false, DualMode.OFF).isSizeChangeFrom(a))   // a rotation
        assertFalse(a.copy(height = 1000f, halfFolded = true).isSizeChangeFrom(a))            // half-folding
        assertFalse(a.copy(width = 1812f, screen = ScreenClass.NEAR_SQUARE_TABLET).isSizeChangeFrom(a))   // unfolding
        assertFalse(a.copy(width = 900f, displayId = 2).isSizeChangeFrom(a))                  // another display
        assertFalse(a.copy(width = 900f, dual = DualMode.SPLIT).isSizeChangeFrom(a))          // Dual screen on
    }

    @Test
    fun threeDsUsesItsOwnCoreRangeAndCeiling() {
        assertEquals(9, Nds3dResolution.value(Nds3dMode.QUALITY, 3, 2, 9, 9, Res3dConsole.N3DS))
        assertEquals(3, Nds3dResolution.value(Nds3dMode.AUTO, 3, 2, 6, 6, Res3dConsole.N3DS))
        assertEquals(3, Nds3dResolution.performanceCeiling(6, Res3dConsole.N3DS))
        assertEquals(10, Nds3dResolution.performanceCeiling(20, Res3dConsole.N3DS))
        assertEquals(8, Nds3dResolution.value(Nds3dMode.QUALITY, 4, 2, 9, 9))
    }

    @Test
    fun valueForReadsTheConsolesKeys() {
        val values = mapOf("nx_3ds_3d_resolution" to "performance", "nx_3ds_3d_scale" to "3", NdsOptions.MODE_3D to "quality")
        assertEquals(3, Nds3dResolution.valueFor(values, 4, 8, Res3dConsole.N3DS))
        assertEquals(4, Nds3dResolution.valueFor(values, 4, 8))
    }

    @Test
    fun forTagMapsConsoles() {
        assertEquals(Res3dConsole.N3DS, Res3dConsole.forTag("3DS"))
        assertEquals(Res3dConsole.DS, Res3dConsole.forTag("NDS"))
        assertNull(Res3dConsole.forTag("GBA"))
        assertEquals(setOf("nx_3ds_3d_resolution", "nx_3ds_3d_max_scale", "nx_3ds_3d_scale"), Res3dConsole.N3DS.keys)
    }

    @Test
    fun consolesKnowTheirCore() {
        assertEquals("melondsds", Res3dConsole.DS.coreId)
        assertEquals("azahar", Res3dConsole.N3DS.coreId)
    }

    @Test
    fun displayMaxOfUsesTheConsolesMaths() {
        val fold = 1848 to 2448
        val screen = ScreenClass.NEAR_SQUARE_TABLET
        assertEquals(Nds3dResolution.displayMaxOf(fold, screen), Res3dConsole.DS.displayMaxOf(fold, screen))
        assertEquals(6, Res3dConsole.N3DS.displayMaxOf(fold, screen))
        assertNull(Res3dConsole.N3DS.displayMaxOf(null, screen))
        assertNull(Res3dConsole.DS.displayMaxOf(fold, null))
    }
}
