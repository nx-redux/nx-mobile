package com.nxredux.mobile.runner

import com.nxredux.mobile.layout.ScreenClass
import com.nxredux.mobile.options.NdsLayoutChoice
import com.nxredux.mobile.options.NdsScreen
import com.nxredux.mobile.options.NdsSettings
import com.nxredux.mobile.options.PxRect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

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
}
