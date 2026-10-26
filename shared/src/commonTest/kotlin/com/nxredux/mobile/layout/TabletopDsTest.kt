package com.nxredux.mobile.layout

import com.nxredux.mobile.options.NdsScreen
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
    fun fold_half_folded_ds_top_6x_bottom_4x_meeting_at_the_fold() {
        val p = place(DsKind.DS)
        assertEquals(TabletopDsMode.SPLIT, p.mode)
        // 3/4 of the 432 px reach set aside each side: 1200 px wide, 4x; the screens meet the hinge's two edges.
        assertEquals(PxRect(156f, 1212f - 1152f, 1536f, 1152f), p.top)
        assertEquals(PxRect(412f, 1236f, 1024f, 768f), p.bottom)
        assertNotNull(p.left)
        assertNotNull(p.right)
    }

    @Test
    fun fold_half_folded_3ds_fills() {
        val p = place(DsKind.N3DS)
        assertEquals(1848f, p.top.width)
        assertEquals(1108f, p.top.height)
        assertEquals(0f, p.top.x)
        assertEquals(1212f - 1108f, p.top.y)
        // bottom space is 1200 x 1212: width-limited 4:3
        assertEquals(1200f, p.bottom.width)
        assertEquals(900f, p.bottom.height)
        assertEquals(324f, p.bottom.x)
        assertEquals(1236f, p.bottom.y)
    }

    @Test
    fun controller_ds_bottom_grows() {
        val p = place(DsKind.DS, controller = true)
        assertNull(p.left)
        assertNull(p.right)
        // whole bottom area 1848 x 1212: 7x (1792 x 1344) is too tall, 6x fits
        assertEquals(PxRect(156f, 1236f, 1536f, 1152f), p.bottom)
        assertEquals(1212f, p.top.y + p.top.height)   // still meeting at the fold
    }

    @Test
    fun touch_screen_overlaps_each_half_by_a_quarter_of_its_reach_at_most() {
        for (kind in DsKind.entries) {
            val p = place(kind)
            assertTrue(reach.total - p.bottom.x <= reach.total / 4f, "$kind overlaps by ${reach.total - p.bottom.x}")
        }
    }

    @Test
    fun bottom_never_below_1x() {
        val p = place(DsKind.DS, h = 1436, hingeBottom = 1236)   // a 200 px bottom area: 1x, still split
        assertEquals(TabletopDsMode.SPLIT, p.mode)
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

    @Test
    fun n3ds_swap_fits_each_rect_to_the_screen_it_shows() {
        val p = tabletopDs(1848, 2448, ScreenClass.NEAR_SQUARE_TABLET, 0, 1212, 1236, DsKind.N3DS, 122f, reach, 2.625f, false, big = NdsScreen.BOTTOM)
        // Above the hinge the 4:3 touch screen: 1212 tall, 1616 wide, centred, meeting the fold.
        assertEquals(PxRect(116f, 0f, 1616f, 1212f), p.top)
        // Below it the 5:3 top screen in the 1200 x 1212 space: 1200 x 720, against the hinge.
        assertEquals(PxRect(324f, 1236f, 1200f, 720f), p.bottom)
    }

    @Test
    fun n3ds_stacked_swap_keeps_the_touch_screen_narrow() {
        val n = tabletopDs(1080, 2640, ScreenClass.REGULAR_PHONE, 0, 1308, 1332, DsKind.N3DS, 100f, reach, 2.625f, false)
        val s = tabletopDs(1080, 2640, ScreenClass.REGULAR_PHONE, 0, 1308, 1332, DsKind.N3DS, 100f, reach, 2.625f, false, big = NdsScreen.BOTTOM)
        assertEquals(n.bottom.width, s.top.width)
        assertEquals(n.top.width, s.bottom.width)
        assertEquals(n.top.y, s.top.y)
        assertEquals(s.top.y + s.top.height, s.bottom.y)
        assertEquals(n.bottom.x, s.top.x)
    }

    @Test
    fun ds_swap_changes_nothing() {
        assertEquals(place(DsKind.DS), tabletopDs(1848, 2448, ScreenClass.NEAR_SQUARE_TABLET, 0, 1212, 1236, DsKind.DS, 122f, reach, 2.625f, false, big = NdsScreen.BOTTOM))
    }

    @Test
    fun bottom_area_too_short_for_1x_stacks_above_the_hinge() {
        // A 150 px bottom area: no 1x DS touch screen fits there, so both screens go above the hinge.
        val p = place(DsKind.DS, h = 1386, hingeBottom = 1236)
        assertEquals(TabletopDsMode.STACKED_TOP, p.mode)
        assertNull(p.left)
        assertTrue(p.bottom.y + p.bottom.height <= 1212f)
    }

    @Test
    fun top_area_too_short_for_1x_stacks() {
        // A 150 px top area cannot hold a 1x top screen: the stack's own 1x floor applies there instead.
        val p = tabletopDs(1848, 2448, ScreenClass.NEAR_SQUARE_TABLET, 0, 150, 174, DsKind.DS, 122f, reach, 2.625f, false)
        assertEquals(TabletopDsMode.STACKED_TOP, p.mode)
    }

    @Test
    fun top_ds_screen_at_least_1x() {
        val p = tabletopDs(1848, 2448, ScreenClass.NEAR_SQUARE_TABLET, 0, 200, 224, DsKind.DS, 122f, reach, 2.625f, false)
        assertEquals(TabletopDsMode.SPLIT, p.mode)
        assertEquals(PxRect(796f, 8f, 256f, 192f), p.top)
    }

    @Test
    fun controller_3ds_bottom_fills_the_bottom_area() {
        val p = place(DsKind.N3DS, controller = true)
        assertNull(p.left)
        // 1848 x 1212: height-limited 4:3, 1616 x 1212, centred, against the hinge.
        assertEquals(PxRect(116f, 1236f, 1616f, 1212f), p.bottom)
        assertEquals(1212f, p.top.y + p.top.height)
    }

    @Test
    fun wide_tablet_splits() {
        val p = tabletopDs(2560, 1600, ScreenClass.WIDE_TABLET, 0, 790, 810, DsKind.DS, 122f, reach, 2.625f, false)
        assertEquals(TabletopDsMode.SPLIT, p.mode)
        assertNotNull(p.left)
    }

    @Test
    fun flip_stacked_3ds_bottom_centred_under_the_top() {
        val n = tabletopDs(1080, 2640, ScreenClass.REGULAR_PHONE, 0, 1308, 1332, DsKind.N3DS, 100f, reach, 2.625f, false)
        // 1080 wide frame, 648 per screen: the touch screen 864 wide, centred under the top one.
        assertEquals(PxRect(0f, 6f, 1080f, 648f), n.top)
        assertEquals(PxRect(108f, 654f, 864f, 648f), n.bottom)
    }

    @Test
    fun touch_screen_stays_between_the_set_asides() {
        val aside = LayoutConstants.TABLETOP_DS_SET_ASIDE * reach.total
        for (kind in DsKind.entries) {
            val p = place(kind)
            assertTrue(p.bottom.x >= aside, "$kind left")
            assertTrue(p.bottom.x + p.bottom.width <= 1848f - aside, "$kind right")
        }
    }

    @Test
    fun editor_preview_draws_the_game_layout_at_the_fold() {
        val pv = assertNotNull(
            halfFoldedPreview(1848f, 2448f, ScreenClass.NEAR_SQUARE_TABLET, 0f, 404.6f, 2.625f, 0f, 0f, true, 3.3f, 2, DsKind.DS),
        )
        assertEquals(1224f, pv.hingeY)
        assertEquals(1224f, pv.placement.top.y + pv.placement.top.height)
        assertEquals(1224f, pv.placement.bottom.y)
        assertTrue(pv.boxes.topMarginPx >= 1224 && pv.boxes.topMarginPx + pv.boxes.heightPx <= 2448)
        assertEquals(tabletopDsUnitPx(404.6f, 2.625f), pv.unitPx)
        // A phone-shaped window still gets the halves (laid out as a tablet-class one).
        assertNotNull(halfFoldedPreview(1080f, 2400f, ScreenClass.REGULAR_PHONE, 0f, 400f, 2.625f, 0f, 0f, true, 3.3f, 2, DsKind.N3DS))
    }
}
