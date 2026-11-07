package com.nxredux.mobile.runner

import com.nxredux.mobile.layout.DsKind
import com.nxredux.mobile.options.NdsScreen
import com.nxredux.mobile.options.PxRect
import com.swordfish.libretrodroid.ScreenRect
import com.swordfish.libretrodroid.ScreenRegions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** A finger on a skin's DS / 3DS glasses, through the regions as the view gets them, lands on the touch screen's frame pixels. */
class DsScreenTouchTest {
    private val w = 1848f
    private val h = 2448f
    private val whole = ScreenRect(0f, 0f, 1f, 1f)

    /** The two glasses for [kind] with [big] in the top one: each rect at the shape of the screen it shows. */
    private fun rects(kind: DsKind, big: NdsScreen): Pair<PxRect, PxRect> = when (kind) {
        DsKind.DS -> PxRect(156f, 60f, 1536f, 1152f) to PxRect(284f, 1236f, 1280f, 960f)
        DsKind.N3DS -> {
            val wide = PxRect(24f, 60f, 1800f, 1080f)        // 400 x 240
            val narrow = PxRect(204f, 1236f, 1440f, 1080f)   // 320 x 240
            if (big == NdsScreen.TOP) wide to narrow else PxRect(204f, 60f, 1440f, 1080f) to PxRect(24f, 1236f, 1800f, 1080f)
        }
    }

    private fun regions(kind: DsKind, big: NdsScreen) =
        rects(kind, big).let { (top, bottom) -> dsScreenRegions(top, bottom, kind, big).map { it.toScreenRegion(w, h) } }

    /** The frame point (0..1) a touch at window px ([x], [y]) maps to, or null. */
    private fun touch(kind: DsKind, big: NdsScreen, x: Float, y: Float) =
        ScreenRegions.mapTouch(regions(kind, big), whole, x / w, y / h)

    @Test fun ds_touch_screen_in_the_bottom_glass() {
        val (top, bottom) = rects(DsKind.DS, NdsScreen.TOP)
        val c = touch(DsKind.DS, NdsScreen.TOP, bottom.x + bottom.width / 2f, bottom.y + bottom.height / 2f)!!
        // The middle of the bottom half of the 256 x 384 stack.
        assertEquals(0.5f, c.x, 0.001f)
        assertEquals(0.75f, c.y, 0.001f)
        // The top-left corner of the touch screen is the stack's (0, 0.5).
        val tl = touch(DsKind.DS, NdsScreen.TOP, bottom.x, bottom.y)!!
        assertEquals(0f, tl.x, 0.001f)
        assertEquals(0.5f, tl.y, 0.001f)
        // The top screen takes no touch.
        assertNull(touch(DsKind.DS, NdsScreen.TOP, top.x + 10f, top.y + 10f))
    }

    @Test fun ds_swapped_touch_screen_in_the_top_glass() {
        val (top, bottom) = rects(DsKind.DS, NdsScreen.BOTTOM)
        val c = touch(DsKind.DS, NdsScreen.BOTTOM, top.x + top.width / 2f, top.y + top.height / 2f)!!
        assertEquals(0.75f, c.y, 0.001f)
        assertNull(touch(DsKind.DS, NdsScreen.BOTTOM, bottom.x + 10f, bottom.y + 10f))
    }

    @Test fun n3ds_touch_maps_into_the_stacked_frame_bottom() {
        for (big in NdsScreen.entries) {
            val (top, bottom) = rects(DsKind.N3DS, big)
            val touchRect = if (big == NdsScreen.BOTTOM) top else bottom
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
