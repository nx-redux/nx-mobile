package com.nxredux.mobile.runner

import com.nxredux.mobile.layout.ScreenClass
import com.nxredux.mobile.options.NdsScreen
import com.nxredux.mobile.options.PxRect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class N3ds3dResolutionTest {
    private fun region(screen: NdsScreen, width: Float, inset: Boolean = false) = NdsRegion(
        screen, NdsLayout.TOP_SRC, PxRect(0f, 0f, width, width * 0.6f), shaded = true, touch = false, inset = inset,
    )

    @Test
    fun currentScaleFloorsPictureOverFrame() {
        assertEquals(2, N3ds3dResolution.currentScale(900f, 800f, 2))
        assertEquals(4, N3ds3dResolution.currentScale(1840f, 800f, 2))
    }

    @Test
    fun currentScaleUsesTheFramesOwnFactor() {
        assertEquals(4, N3ds3dResolution.currentScale(1840f, 1600f, 4))
        assertEquals(4, N3ds3dResolution.currentScale(1840f, 800f, 2))
    }

    @Test
    fun singleBottomUsesItsOwnFrame() {
        assertEquals(4, N3ds3dResolution.currentScale(1300f, 640f, 2))
    }

    @Test
    fun sideBySideScale() {
        assertEquals(2, N3ds3dResolution.currentScale(1440f, 1440f, 2))
    }

    @Test
    fun currentScaleIsAtLeastOne() {
        assertEquals(1, N3ds3dResolution.currentScale(100f, 800f, 2))
    }

    @Test
    fun regionScaleTakesTheBiggerScreen() {
        val regions = listOf(region(NdsScreen.TOP, 1840f), region(NdsScreen.BOTTOM, 920f))
        assertEquals(4, N3ds3dResolution.regionScale(regions))
    }

    @Test
    fun regionScaleNullWithNoRegions() {
        assertNull(N3ds3dResolution.regionScale(emptyList()))
    }

    @Test
    fun displayMaxUnfoldedFold() {
        assertEquals(6, N3ds3dResolution.displayMax(1848f, 2448f, ScreenClass.NEAR_SQUARE_TABLET))
    }

    @Test
    fun displayMaxPhones() {
        assertEquals(4, N3ds3dResolution.displayMax(1080f, 2400f, ScreenClass.REGULAR_PHONE))
        assertEquals(4, N3ds3dResolution.displayMax(1920f, 1080f, ScreenClass.REGULAR_PHONE))
    }

    @Test
    fun displayMaxOfNeedsAWindowAndClass() {
        assertNull(N3ds3dResolution.displayMaxOf(null, ScreenClass.REGULAR_PHONE))
        assertNull(N3ds3dResolution.displayMaxOf(1080 to 2400, null))
        assertNull(N3ds3dResolution.displayMaxOf(0 to 2400, ScreenClass.REGULAR_PHONE))
        assertEquals(4, N3ds3dResolution.displayMaxOf(1080 to 2400, ScreenClass.REGULAR_PHONE))
    }
}
