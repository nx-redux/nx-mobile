package com.nxredux.mobile.layout

import com.nxredux.mobile.options.PxRect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ClusterLayoutTest {
    @Test fun phone_unit_is_11mm() {
        val unit = landscapeUnitPx(ScreenClass.REGULAR_PHONE, 1080f, 1080f, 420f, 2.625f, toolRow = false)
        assertEquals(181.9f, unit, 0.1f)
    }

    @Test fun stubby_phone_takes_portrait_unit() {
        val unit = landscapeUnitPx(ScreenClass.STUBBY_PHONE, 1248f, 1248f, 428f, 2.625f, toolRow = false)
        assertEquals((1248f - 84f) / 9f, unit, 0.1f)
    }

    @Test fun tool_row_shrinks_unit_only_when_height_short() {
        val tablet = landscapeUnitPx(ScreenClass.WIDE_TABLET, 1440f, 1440f, 249f, 1.75f, toolRow = true)
        assertEquals(107.8f, tablet, 0.1f)
        val short = landscapeUnitPx(ScreenClass.REGULAR_PHONE, 1080f, 1080f, 420f, 2.625f, toolRow = true)
        assertEquals((540f - 42f) / (2.57f + 1.07f), short, 0.1f)
    }

    private val reach = Reach(100f, 300f)

    @Test fun wide_tablet_sets_aside_full_reach() =
        assertEquals(400f, setAsidePx(ScreenClass.WIDE_TABLET, reach), 0.01f)

    @Test fun others_set_aside_half() {
        assertEquals(200f, setAsidePx(ScreenClass.REGULAR_PHONE, reach), 0.01f)
        assertEquals(200f, setAsidePx(ScreenClass.NEAR_SQUARE_TABLET, reach), 0.01f)
    }

    @Test fun cutout_beyond_margin_moves_both_halves() {
        val r = reach(182f, 2.625f, 136f, 0f, null, 0f, systemPills = 2)
        assertEquals(42f + 36.4f + 57.6f, r.edgePx, 0.1f)
        assertEquals(136f, r.edgePx, 0.1f)
    }

    @Test fun reach_counts_tweaked_group_bounds() {
        val r = reach(182f, 2.625f, 0f, 0f, 900f, 0f, systemPills = 2)
        assertEquals(900f, r.buttonsPx, 0.01f)
        assertEquals(2f * 1.4f * 182f + 12f * 2.625f, reach(182f, 2.625f, 0f, 0f, null, 0f, systemPills = 2).buttonsPx, 0.01f)
    }

    @Test fun reach_estimate_counts_a_three_pill_system_row() =
        assertEquals(3f * 1.4f * 182f + 2f * 12f * 2.625f, reach(182f, 2.625f, 0f, 0f, null, 3f, systemPills = 3).buttonsPx, 0.01f)

    @Test fun box_holds_the_widest_row_drawn() {
        // A Wii pad's SELECT HOME START (3 x 1.4 units + 2 x 12 dp) is wider than the 4.5-unit floor's content once
        // the 0.2-unit nudge is added: the box takes the buttons plus the nudge and both paddings, even in a tight column.
        val window = PxRect(0f, 0f, 2400f, 1080f)
        val buttons = 3f * 1.4f * 100f + 2f * 12f * 2f
        val p = clusterPlacement(window, PxRect(100f, 0f, 2200f, 1080f), 100f, Reach(52f, buttons), 2f, false)
        assertEquals(buttons + 20f + 64f, p.boxWidthPx, 0.01f)
        // Narrow buttons keep the 4.5-unit floor.
        assertEquals(450f + 64f, clusterPlacement(window, PxRect(100f, 0f, 2200f, 1080f), 100f, Reach(52f, 300f), 2f, false).boxWidthPx, 0.01f)
    }

    @Test fun clear_when_both_columns_hold_the_reach() {
        val window = PxRect(0f, 0f, 2400f, 1080f)
        val r = Reach(50f, 300f)
        assertTrue(clusterPlacement(window, PxRect(400f, 0f, 1600f, 1080f), 182f, r, 2.625f, false).clear)
        assertFalse(clusterPlacement(window, PxRect(300f, 0f, 1800f, 1080f), 182f, r, 2.625f, false).clear)
    }

    @Test fun roomy_column_centres_buttons() {
        val window = PxRect(0f, 0f, 2304f, 1440f)
        val picture = PxRect(554.5f, 0f, 1195f, 1440f)
        val r = Reach(49.6f, 365.4f)
        val p = clusterPlacement(window, picture, 107.8f, r, 1.75f, toolRow = false)
        assertTrue(p.clear)
        assertEquals((554.5f - 415f - 49.6f) / 2f, p.extraInsetPx, 0.1f)
    }

    @Test fun d_pad_centre_sits_at_mid_height() {
        val window = PxRect(0f, 0f, 2400f, 1080f)
        val p = clusterPlacement(window, PxRect(400f, 0f, 1600f, 1080f), 100f, Reach(36f, 300f), 2f, false)
        assertEquals(6f * 100f + 64f, p.boxHeightPx, 0.01f)
        assertEquals(540f + 32f + 257f - p.boxHeightPx, p.topMarginPx, 0.01f)
    }

    @Test fun beside_measures_are_the_unit_and_reach_the_game_reads() {
        // A 20:9 phone in landscape, a 136 px cutout on the left, the second row kept.
        val m = besideMeasures(ScreenClass.REGULAR_PHONE, 2400f, 1080f, 420f, 2.625f, 136f, 0f, secondRow = true, measuredButtonsPx = null, faceUnits = 3f, systemPills = 2)
        val unit = landscapeUnitPx(ScreenClass.REGULAR_PHONE, 1080f, 1080f, 420f, 2.625f, toolRow = true)
        assertEquals(unit, m.unitPx)
        assertEquals(reach(unit, 2.625f, 136f, 0f, null, 3f, systemPills = 2), m.reach)
    }

    @Test fun beside_measures_on_a_stubby_phone_take_the_portrait_band_unit() {
        val m = besideMeasures(ScreenClass.STUBBY_PHONE, 2176f, 1248f, 428f, 2.625f, 0f, 0f, secondRow = false, measuredButtonsPx = null, faceUnits = 3f, systemPills = 2)
        assertEquals((1248f - 84f) / 9f, m.unitPx, 0.1f)
    }

    @Test fun beside_placement_without_a_picture_assumes_the_middle_half() {
        val window = PxRect(0f, 0f, 2400f, 1080f)
        val r = Reach(36f, 300f)
        assertEquals(
            clusterPlacement(window, PxRect(600f, 0f, 1200f, 1080f), 100f, r, 2f, toolRow = true),
            besidePlacement(window, null, 100f, r, 2f, secondRow = true),
        )
        val picture = PxRect(500f, 0f, 1400f, 1080f)
        assertEquals(clusterPlacement(window, picture, 100f, r, 2f, toolRow = false), besidePlacement(window, picture, 100f, r, 2f, secondRow = false))
    }
}
