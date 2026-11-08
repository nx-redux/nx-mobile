package com.nxredux.mobile.runner

import com.nxredux.mobile.layout.DsKind
import com.nxredux.mobile.options.NdsScreen
import com.nxredux.mobile.options.PxRect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DsScreenRegionsTest {
    // A skin's two glasses on the Fold's inner screen: the DS at 6x over 5x, the 3DS at each screen's own shape.
    private val dsTop = PxRect(156f, 60f, 1536f, 1152f)
    private val dsBottom = PxRect(284f, 1236f, 1280f, 960f)
    private val n3dsTop = PxRect(24f, 60f, 1800f, 1080f)
    private val n3dsBottom = PxRect(204f, 1236f, 1440f, 1080f)

    @Test
    fun ds_regions_top_and_touch() {
        val (top, bottom) = dsScreenRegions(dsTop, dsBottom, DsKind.DS, NdsScreen.TOP)
        assertEquals(NdsScreen.TOP, top.screen)
        assertEquals(NdsLayout.TOP_SRC, top.src)
        assertEquals(dsTop, top.dst)
        assertFalse(top.touch)
        assertEquals(NdsScreen.BOTTOM, bottom.screen)
        assertEquals(NdsLayout.BOTTOM_SRC, bottom.src)
        assertEquals(dsBottom, bottom.dst)
        assertTrue(bottom.touch)
        // Only the top screen is shaded (NdsLayout.takesShader): the 6x top screen, not the 5x touch screen.
        assertTrue(top.shaded)
        assertFalse(bottom.shaded)
    }

    @Test
    fun n3ds_regions_use_stacked_sources() {
        val (top, bottom) = dsScreenRegions(n3dsTop, n3dsBottom, DsKind.N3DS, NdsScreen.TOP)
        assertEquals(DualScreen.N3DS_TOP_SRC, top.src)
        assertEquals(n3dsTop, top.dst)
        assertFalse(top.touch)
        assertEquals(DualScreen.N3DS_BOTTOM_SRC, bottom.src)
        assertEquals(n3dsBottom, bottom.dst)
        assertTrue(bottom.touch)
    }

    @Test
    fun swap_puts_bottom_on_top() {
        val (top, bottom) = dsScreenRegions(dsTop, dsBottom, DsKind.DS, NdsScreen.BOTTOM)
        assertEquals(NdsScreen.BOTTOM, top.screen)
        assertEquals(NdsLayout.BOTTOM_SRC, top.src)
        assertEquals(dsTop, top.dst)
        assertTrue(top.touch)   // the touch screen takes touches wherever it is drawn
        assertEquals(NdsScreen.TOP, bottom.screen)
        assertEquals(NdsLayout.TOP_SRC, bottom.src)
        assertEquals(dsBottom, bottom.dst)
        assertFalse(bottom.touch)
    }

    @Test
    fun n3ds_touch_maps_through_bottom_source() {
        val bottom = dsScreenRegions(n3dsTop, n3dsBottom, DsKind.N3DS, NdsScreen.TOP).single { it.touch }
        // Corners and the centre of the drawn touch screen, in the 400 x 480 Stacked frame.
        listOf(
            bottom.dst.x to bottom.dst.y,
            bottom.dst.x + bottom.dst.width to bottom.dst.y + bottom.dst.height,
            bottom.dst.x + bottom.dst.width / 2f to bottom.dst.y + bottom.dst.height / 2f,
        ).forEach { (x, y) ->
            val (fx, fy) = frameAt(bottom, x, y)
            assertTrue(fx in 40f..360f, "x $fx")
            assertTrue(fy in 240f..480f, "y $fy")
        }
        val (cx, cy) = frameAt(bottom, bottom.dst.x + bottom.dst.width / 2f, bottom.dst.y + bottom.dst.height / 2f)
        assertEquals(200f, cx, 0.5f)
        assertEquals(360f, cy, 0.5f)
    }

    @Test
    fun only_the_top_screen_is_shaded_even_at_one_size() {
        // One tall glass cut in half (a stack): both screens at one scale; the shader is the top screen's (NdsLayout.takesShader).
        val stackTop = PxRect(0f, 0f, 1024f, 768f)
        val stackBottom = PxRect(0f, 768f, 1024f, 768f)
        assertEquals(listOf(true, false), dsScreenRegions(stackTop, stackBottom, DsKind.DS, NdsScreen.TOP).map { it.shaded })
    }

    /** Where window point ([x], [y]) in [r]'s screen lands in the 400 x 480 3DS frame, as the fork maps a touch. */
    private fun frameAt(r: NdsRegion, x: Float, y: Float): Pair<Float, Float> {
        val u = (x - r.dst.x) / r.dst.width
        val v = (y - r.dst.y) / r.dst.height
        return Pair(
            (r.src.left + u * (r.src.right - r.src.left)) * 400f,
            (r.src.top + v * (r.src.bottom - r.src.top)) * 480f,
        )
    }
}
