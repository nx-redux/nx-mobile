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

    @Test
    fun frameFactorReadsTheFrame() {
        assertEquals(3, N3ds3dResolution.frameFactor(1200f, 1440f))   // Stacked
        assertEquals(2, N3ds3dResolution.frameFactor(800f, 720f))     // Large screen
        assertEquals(4, N3ds3dResolution.frameFactor(1280f, 1440f))   // Large screen, bottom screen prominent
        assertEquals(5, N3ds3dResolution.frameFactor(2000f, 1200f))   // Single screen
        assertEquals(6, N3ds3dResolution.frameFactor(1920f, 1440f))   // Single screen, the bottom one
        assertEquals(10, N3ds3dResolution.frameFactor(7200f, 2400f))  // Side by side
        assertEquals(1, N3ds3dResolution.frameFactor(400f, 480f))
    }

    @Test
    fun frameFactorNullForAnUnknownFrame() {
        assertNull(N3ds3dResolution.frameFactor(0f, 0f))
        assertNull(N3ds3dResolution.frameFactor(1000f, 700f))
        assertNull(N3ds3dResolution.frameFactor(4400f, 5280f))   // Stacked at 11x: past the core's range
    }

    @Test
    fun noTwoFrameShapesShareASize() {
        val sizes = N3ds3dResolution.FRAME_SHAPES_1X.flatMap { (w, h) -> (1..10).map { f -> (w * f to h * f) } }
        assertEquals(sizes.size, sizes.toSet().size)
    }

    @Test
    fun frameFactorReadsTheProportionInEffect() {
        // Large screen at 1.71: 1x is 400 x (240 + 140) = 400 x 380.
        assertEquals(3, N3ds3dResolution.frameFactor(1200f, 1140f, 1.71f))
        assertEquals(4, N3ds3dResolution.frameFactor(1280f, 1520f, 1.71f))   // the bottom screen prominent: 320 x 380
        assertNull(N3ds3dResolution.frameFactor(1200f, 1140f))               // a shape only that proportion makes
        // The other layouts' frames still read.
        assertEquals(5, N3ds3dResolution.frameFactor(2000f, 1200f, 1.71f))
        assertEquals(2, N3ds3dResolution.frameFactor(800f, 720f, 1.71f))
        assertNull(N3ds3dResolution.frameFactor(1000f, 700f, 1.71f))
        assertNull(N3ds3dResolution.frameFactor(0f, 0f, 1.71f))
    }

    @Test
    fun noTwoFrameShapesInUseShareASize() {
        // Every proportion the fill rule sends (1 to 2 in hundredths): its top over bottom shapes with Single screen and
        // Side by side never reach one size at two factors.
        for (c in 100..200) {
            val shapes = N3ds3dResolution.shapesInUse(c / 100f)
            val seen = mutableMapOf<Pair<Int, Int>, Int>()
            for ((w, h) in shapes) for (f in 1..10) {
                val prev = seen.put(w * f to h * f, f)
                if (prev != null) assertEquals(prev, f, "proportion ${c / 100f}: ${w * f} x ${h * f}")
            }
        }
    }
}
