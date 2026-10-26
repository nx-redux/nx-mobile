package com.nxredux.mobile.runner

import com.nxredux.mobile.layout.DsKind
import com.nxredux.mobile.layout.Reach
import com.nxredux.mobile.layout.ScreenClass
import com.nxredux.mobile.layout.TabletopDsPlacement
import com.nxredux.mobile.layout.tabletopDs
import com.nxredux.mobile.options.NdsScreen
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TabletopRegionsTest {
    private val reach = Reach(42f + 24f, 366f)

    /** The Fold's inner screen half-folded like a laptop. */
    private fun fold(kind: DsKind): TabletopDsPlacement =
        tabletopDs(1848, 2448, ScreenClass.NEAR_SQUARE_TABLET, 0, 1212, 1236, kind, 122f, reach, 2.625f, controller = false)

    /** A phone-shaped foldable (Z Flip) half-folded: both screens stacked above the hinge. */
    private fun flip(kind: DsKind): TabletopDsPlacement =
        tabletopDs(1080, 2520, ScreenClass.REGULAR_PHONE, 0, 1250, 1270, kind, 122f, reach, 2.625f, controller = false)

    @Test
    fun ds_regions_top_and_touch() {
        val p = fold(DsKind.DS)
        val (top, bottom) = tabletopRegions(p, DsKind.DS, NdsScreen.TOP)
        assertEquals(NdsScreen.TOP, top.screen)
        assertEquals(NdsLayout.TOP_SRC, top.src)
        assertEquals(p.top, top.dst)
        assertFalse(top.touch)
        assertEquals(NdsScreen.BOTTOM, bottom.screen)
        assertEquals(NdsLayout.BOTTOM_SRC, bottom.src)
        assertEquals(p.bottom, bottom.dst)
        assertTrue(bottom.touch)
        // Shaded regions share one size: the 6x top screen is shaded, the 5x touch screen drawn plain.
        assertTrue(top.shaded)
        assertFalse(bottom.shaded)
    }

    @Test
    fun n3ds_regions_use_stacked_sources() {
        val p = fold(DsKind.N3DS)
        val (top, bottom) = tabletopRegions(p, DsKind.N3DS, NdsScreen.TOP)
        assertEquals(DualScreen.N3DS_TOP_SRC, top.src)
        assertEquals(p.top, top.dst)
        assertFalse(top.touch)
        assertEquals(DualScreen.N3DS_BOTTOM_SRC, bottom.src)
        assertEquals(p.bottom, bottom.dst)
        assertTrue(bottom.touch)
    }

    @Test
    fun swap_puts_bottom_on_top() {
        val p = fold(DsKind.DS)
        val (top, bottom) = tabletopRegions(p, DsKind.DS, NdsScreen.BOTTOM)
        assertEquals(NdsScreen.BOTTOM, top.screen)
        assertEquals(NdsLayout.BOTTOM_SRC, top.src)
        assertEquals(p.top, top.dst)
        assertTrue(top.touch)   // the touch screen takes touches wherever it is drawn
        assertEquals(NdsScreen.TOP, bottom.screen)
        assertEquals(NdsLayout.TOP_SRC, bottom.src)
        assertEquals(p.bottom, bottom.dst)
        assertFalse(bottom.touch)
    }

    @Test
    fun n3ds_touch_maps_through_bottom_source() {
        val p = fold(DsKind.N3DS)
        val bottom = tabletopRegions(p, DsKind.N3DS, NdsScreen.TOP).single { it.touch }
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
    fun phone_shaped_stacks_both_screens_above_the_hinge() {
        listOf(DsKind.DS, DsKind.N3DS).forEach { kind ->
            val p = flip(kind)
            val (top, bottom) = tabletopRegions(p, kind, NdsScreen.TOP)
            assertEquals(p.top, top.dst)
            assertEquals(p.bottom, bottom.dst)
            assertTrue(bottom.dst.y + bottom.dst.height <= 1250f)
            assertTrue(bottom.touch)
        }
        // DS stacked at one scale: both shaded.
        assertTrue(tabletopRegions(flip(DsKind.DS), DsKind.DS, NdsScreen.TOP).all { it.shaded })
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
