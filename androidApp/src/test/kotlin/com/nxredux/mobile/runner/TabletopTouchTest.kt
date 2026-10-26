package com.nxredux.mobile.runner

import com.nxredux.mobile.layout.DsKind
import com.nxredux.mobile.layout.Reach
import com.nxredux.mobile.layout.ScreenClass
import com.nxredux.mobile.layout.tabletopDs
import com.nxredux.mobile.options.NdsScreen
import com.swordfish.libretrodroid.ScreenRect
import com.swordfish.libretrodroid.ScreenRegions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** A finger on the half-folded Fold, through the regions as the view gets them, lands on the touch screen's frame pixels. */
class TabletopTouchTest {
    private val w = 1848f
    private val h = 2448f
    private val reach = Reach(42f + 24.5f, 366f)
    private val whole = ScreenRect(0f, 0f, 1f, 1f)

    private fun regions(kind: DsKind, big: NdsScreen) =
        tabletopDs(1848, 2448, ScreenClass.NEAR_SQUARE_TABLET, 0, 1212, 1236, kind, 122.7f, reach, 2.625f, false, true, big)
            .let { p -> tabletopRegions(p, kind, big).map { it.toScreenRegion(w, h) } to p }

    /** The frame point (0..1) a touch at window px ([x], [y]) maps to, or null. */
    private fun touch(kind: DsKind, big: NdsScreen, x: Float, y: Float) =
        ScreenRegions.mapTouch(regions(kind, big).first, whole, x / w, y / h)

    @Test fun ds_touch_screen_below_the_hinge() {
        val p = regions(DsKind.DS, NdsScreen.TOP).second
        val c = touch(DsKind.DS, NdsScreen.TOP, p.bottom.x + p.bottom.width / 2f, p.bottom.y + p.bottom.height / 2f)!!
        // The middle of the bottom half of the 256 x 384 stack.
        assertEquals(0.5f, c.x, 0.001f)
        assertEquals(0.75f, c.y, 0.001f)
        // The top-left corner of the touch screen is the stack's (0, 0.5).
        val tl = touch(DsKind.DS, NdsScreen.TOP, p.bottom.x, p.bottom.y)!!
        assertEquals(0f, tl.x, 0.001f)
        assertEquals(0.5f, tl.y, 0.001f)
        // The top screen takes no touch.
        assertNull(touch(DsKind.DS, NdsScreen.TOP, p.top.x + 10f, p.top.y + 10f))
    }

    @Test fun ds_swapped_touch_screen_above_the_hinge() {
        val p = regions(DsKind.DS, NdsScreen.BOTTOM).second
        val c = touch(DsKind.DS, NdsScreen.BOTTOM, p.top.x + p.top.width / 2f, p.top.y + p.top.height / 2f)!!
        assertEquals(0.75f, c.y, 0.001f)
        assertNull(touch(DsKind.DS, NdsScreen.BOTTOM, p.bottom.x + 10f, p.bottom.y + 10f))
    }

    @Test fun n3ds_touch_maps_into_the_stacked_frame_bottom() {
        for (big in NdsScreen.entries) {
            val p = regions(DsKind.N3DS, big).second
            val touchRect = if (big == NdsScreen.BOTTOM) p.top else p.bottom
            val c = touch(DsKind.N3DS, big, touchRect.x + touchRect.width / 2f, touchRect.y + touchRect.height / 2f)!!
            // The centre of Azahar's bottom screen in the 400 x 480 frame: (200, 360).
            assertEquals(0.5f, c.x, 0.001f)
            assertEquals(0.75f, c.y, 0.001f)
            val br = touch(DsKind.N3DS, big, touchRect.x + touchRect.width, touchRect.y + touchRect.height)!!
            // Its bottom-right corner: Azahar's bottom screen ends at 0.9 x 1 of the frame (DualScreen's bottom source).
            assertEquals(0.9f, br.x, 0.001f)
            assertEquals(1f, br.y, 0.001f)
        }
    }
}
