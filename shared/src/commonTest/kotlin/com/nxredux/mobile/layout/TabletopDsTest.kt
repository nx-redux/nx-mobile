package com.nxredux.mobile.layout

import com.nxredux.mobile.options.PxRect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TabletopDsTest {
    private val reach = Reach(42f + 24f, 366f)

    private fun place(kind: DsKind, controller: Boolean = false, h: Int = 2448, hingeBottom: Int = 1236) =
        tabletopDs(1848, h, ScreenClass.NEAR_SQUARE_TABLET, 0, 1212, hingeBottom, kind, 122f, reach, 2.625f, controller)

    @Test
    fun fold_half_folded_ds_top_6x_bottom_5x() {
        val p = place(DsKind.DS)
        assertEquals(TabletopDsMode.SPLIT, p.mode)
        assertEquals(PxRect(156f, 30f, 1536f, 1152f), p.top)
        assertEquals(PxRect(284f, 1236f + 126f, 1280f, 960f), p.bottom)
        assertNotNull(p.left)
        assertNotNull(p.right)
    }

    @Test
    fun fold_half_folded_3ds_fills() {
        val p = place(DsKind.N3DS)
        assertEquals(1848f, p.top.width)
        assertEquals(1108f, p.top.height)
        assertEquals(0f, p.top.x)
        // bottom space is 1416 x 1212: width-limited 4:3
        assertEquals(1416f, p.bottom.width)
        assertEquals(1062f, p.bottom.height)
        assertEquals(216f, p.bottom.x)
    }

    @Test
    fun controller_ds_bottom_grows() {
        val p = place(DsKind.DS, controller = true)
        assertNull(p.left)
        assertNull(p.right)
        // whole bottom area 1848 x 1212: 7x (1792 x 1344) is too tall, 6x fits
        assertEquals(1536f, p.bottom.width)
        assertEquals(1152f, p.bottom.height)
    }

    @Test
    fun bottom_never_below_1x() {
        val p = place(DsKind.DS, h = 1300, hingeBottom = 1236)
        assertEquals(256f, p.bottom.width)
        assertEquals(192f, p.bottom.height)
    }

    @Test
    fun flip_half_folded_stacks_both_on_top() {
        val p = tabletopDs(1080, 2640, ScreenClass.REGULAR_PHONE, 0, 1308, 1332, DsKind.DS, 100f, reach, 2.625f, false)
        assertEquals(TabletopDsMode.STACKED_TOP, p.mode)
        assertNull(p.left)
        assertNull(p.right)
        // 1080 / 256 = 4.2 and 1308 / 384 = 3.4: 3x
        assertEquals(PxRect(156f, 78f, 768f, 576f), p.top)
        assertEquals(PxRect(156f, 654f, 768f, 576f), p.bottom)
        val n = tabletopDs(1080, 2640, ScreenClass.REGULAR_PHONE, 0, 1308, 1332, DsKind.N3DS, 100f, reach, 2.625f, false)
        assertEquals(TabletopDsMode.STACKED_TOP, n.mode)
        assertEquals(1080f, n.top.width)
        assertEquals(n.top.y + n.top.height, n.bottom.y)
    }

    @Test
    fun tabletop_unit_is_cover_size() {
        assertTrue(kotlin.math.abs(tabletopDsUnitPx(404.6f, 2.625f) - 122.7f) < 1f)
    }
}
