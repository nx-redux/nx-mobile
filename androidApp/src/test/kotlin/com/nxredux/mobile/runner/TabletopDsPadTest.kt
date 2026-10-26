package com.nxredux.mobile.runner

import com.nxredux.mobile.layout.DsKind
import com.nxredux.mobile.layout.Reach
import com.nxredux.mobile.layout.ScreenClass
import com.nxredux.mobile.layout.tabletopDs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The half-folded Fold's DS / 3DS pad halves below the hinge, and when a change lays the tabletop out again. */
class TabletopDsPadTest {
    // The Fold half-folded: 1848 x 2448, hinge 1212..1236, the cover screen's 7.7 mm unit at 404.6 dpi.
    private val unit = 122.7f
    private val reach = Reach(42f + 0.2f * unit, 366f)

    private fun place(kind: DsKind, controller: Boolean = false, secondRow: Boolean = true) =
        tabletopDs(1848, 2448, ScreenClass.NEAR_SQUARE_TABLET, 0, 1212, 1236, kind, unit, reach, 2.625f, controller, secondRow)

    @Test fun boxes_sit_in_bottom_area() {
        for (kind in DsKind.entries) {
            val p = place(kind)
            val box = tabletopDsPadBoxes(p, rootH = 2448)!!
            assertTrue("$kind box starts below the hinge: ${box.topMarginPx}", box.topMarginPx >= 1236)
            assertTrue("$kind box ends inside the root", box.topMarginPx + box.heightPx <= 2448)
            // The second row (STYLUS / SWAP) is in the box: 7.07 units + the 16 dp padding each side.
            assertEquals((7.07f * unit + 2f * 16f * 2.625f).toInt().toFloat(), box.heightPx.toFloat(), 1f)
            // Beside the touch screen: the box reaches past the screen's edge only by what the set-aside leaves.
            assertTrue(box.widthPx >= p.bottom.x.toInt())
        }
    }

    @Test fun ds_pad_over_the_touch_screen_is_see_through() {
        // The bottom screen leaves 284 px each side, less than the 432 px reach: the halves overlap it, not clear.
        val box = tabletopDsPadBoxes(place(DsKind.DS), rootH = 2448)!!
        assertFalse(box.clear)
    }

    @Test fun box_kept_inside_a_short_root() {
        val box = tabletopDsPadBoxes(place(DsKind.DS), rootH = 1800)!!
        assertTrue(box.topMarginPx + box.heightPx <= 1800)
    }

    @Test fun controller_has_no_boxes() {
        assertNull(tabletopDsPadBoxes(place(DsKind.DS, controller = true), rootH = 2448))
        assertNull(tabletopDsPadBoxes(place(DsKind.N3DS, controller = true), rootH = 2448))
    }

    @Test fun stacked_top_has_no_boxes() {
        val flip = tabletopDs(1080, 2640, ScreenClass.REGULAR_PHONE, 0, 1308, 1332, DsKind.DS, unit, reach, 2.625f, false)
        assertNull(tabletopDsPadBoxes(flip, rootH = 2640))
    }

    private val built = TabletopBuild(halfFolded = true, controller = false, rootW = 1848, rootH = 2448)

    @Test fun relayout_on_posture_flip() {
        assertTrue(tabletopRelayoutNeeded(built, built.copy(halfFolded = false)))
        assertTrue(tabletopRelayoutNeeded(built.copy(halfFolded = false), built))
    }

    @Test fun relayout_on_controller_flip_while_half_folded() {
        assertTrue(tabletopRelayoutNeeded(built, built.copy(controller = true)))
        assertTrue(tabletopRelayoutNeeded(built.copy(controller = true), built))
    }

    @Test fun relayout_on_a_new_size_while_half_folded() {
        assertTrue(tabletopRelayoutNeeded(built, built.copy(rootW = 2448, rootH = 1848)))
    }

    @Test fun no_relayout_when_nothing_changed() {
        assertFalse(tabletopRelayoutNeeded(built, built.copy()))
    }

    @Test fun flat_changes_are_left_to_the_flat_layout() {
        val flat = built.copy(halfFolded = false)
        assertFalse(tabletopRelayoutNeeded(flat, flat.copy(rootW = 2448, rootH = 1848)))
        assertFalse(tabletopRelayoutNeeded(flat, flat.copy(controller = true)))
    }

    @Test fun nothing_built_yet_waits_for_the_first_layout() {
        assertFalse(tabletopRelayoutNeeded(null, built))
    }
}
