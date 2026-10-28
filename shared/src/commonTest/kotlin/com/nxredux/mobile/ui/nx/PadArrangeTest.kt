package com.nxredux.mobile.ui.nx

import androidx.compose.ui.geometry.Rect
import com.nxredux.mobile.settings.GroupTweak
import com.nxredux.mobile.settings.PadGroup
import com.nxredux.mobile.settings.PadLayout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PadArrangeTest {
    private fun arrange(layout: PadLayout) = PadArrange().apply {
        this.layout = layout
        unitPx = 50f
        bounds = Rect(0f, 0f, 1000f, 400f)
        defaults[PadGroup.DPAD] = Rect(100f, 150f, 250f, 300f)
        defaults[PadGroup.FACE] = Rect(750f, 150f, 900f, 300f)
    }

    @Test fun an_untweaked_group_is_at_its_default() =
        assertEquals(Rect(750f, 150f, 900f, 300f), arrange(PadLayout.EMPTY).placed(PadGroup.FACE))

    @Test fun a_tweaked_group_is_placed() =
        assertEquals(Rect(200f, 150f, 350f, 300f), arrange(PadLayout.EMPTY.with(PadGroup.DPAD, GroupTweak(dx = 2f))).placed(PadGroup.DPAD))

    @Test fun an_unmeasured_group_has_no_rect() = assertNull(arrange(PadLayout.EMPTY).placed(PadGroup.C_STICK))

    @Test fun ps2LandscapeKeepsDpadAndStickApart() {
        for (side in PadSide.entries) {
            val c = dualStickCluster(side)
            val bounds = Rect(0f, 0f, c.width, c.height)
            assertFalse(c.main.overlaps(c.stick), "$side: d-pad / face and stick overlap")
            for (r in listOf(c.main, c.stick, c.thumb)) assertTrue(bounds.contains(r.topLeft) && r.right <= c.width + 1e-4f && r.bottom <= c.height + 1e-4f, "$side: $r outside $bounds")
            assertFalse(c.thumb.overlaps(c.main) || c.thumb.overlaps(c.stick), "$side: the thumb pill overlaps")
            // The stick group (stick + its thumb pill) keeps out of the d-pad / face box, so the editor picks either cleanly.
            assertFalse(c.stickGroup.overlaps(c.main), "$side: stick group over the d-pad / face")
        }
        val left = dualStickCluster(PadSide.LEFT)
        assertTrue(left.stick.left >= left.main.right && left.stick.top > left.main.top)        // below-right of the d-pad
        val right = dualStickCluster(PadSide.RIGHT)
        assertTrue(right.stick.right <= right.main.left && right.stick.top > right.main.top)    // below-left of the face
    }

    @Test fun ps2OverlaySidesFitTheBand() {
        // A 9-unit band less the edges: both sides the d-pad row's gap apart, no taller than the row.
        val row = 8.4f
        val s = dualStickScale(row, stickScale = 1f)
        assertTrue(2f * dualStickCluster(PadSide.LEFT).width * s + 0.5f <= row + 1e-4f)
        assertTrue(3f * s <= 3f)
        assertEquals(1.25f, dualStickScale(20f, stickScale = 1.25f))   // a wide band: the d-pad row's own scale
    }

    @Test fun ps2PortraitSticksSitUnderTheClustersByTheEdges() {
        for ((h, scale) in listOf(6.2f to 1f, 7.5f to 1.2f, 10f to 1.25f)) {
            val w = 9f
            val (l, r) = portraitDualSticks(w, h, scale)
            val rowBottom = 1f + (h - 3.12f - 3f * scale) / 2f + 3f * scale
            // The d-pad's down arm (its middle third, the row's bottom third) and the diamond's bottom button.
            val dpadArm = Rect(PS2_PORTRAIT_CLUSTER_INSET + scale, rowBottom - scale, PS2_PORTRAIT_CLUSTER_INSET + 2f * scale, rowBottom)
            val faceArm = Rect(w - PS2_PORTRAIT_CLUSTER_INSET - 2f * scale, rowBottom - scale, w - PS2_PORTRAIT_CLUSTER_INSET - scale, rowBottom)
            for ((p, arm) in listOf(l to dpadArm, r to faceArm)) {
                assertTrue(p.group.left >= -1e-4f && p.group.right <= w + 1e-4f && p.group.bottom <= h + 1e-4f, "h=$h: ${p.group} outside the pad")
                assertTrue(p.thumb.top >= p.stick.bottom, "h=$h: the pill is not under the stick")
                assertFalse(p.group.overlaps(arm), "h=$h: ${p.group} over the cluster's arm $arm")
                // MENU (1.4 wide) and the NX mark (1.15) stay in the middle, clear of both sticks.
                assertTrue(p.group.right <= w / 2f - 0.7f || p.group.left >= w / 2f + 0.7f, "h=$h: ${p.group} reaches MENU")
            }
            assertEquals(0f, l.stick.left, 1e-4f)
            assertEquals(w, r.stick.right, 1e-4f)
            assertEquals(PS2_PORTRAIT_STICK_UNITS * scale, l.stick.width, 1e-4f)
        }
    }
}
