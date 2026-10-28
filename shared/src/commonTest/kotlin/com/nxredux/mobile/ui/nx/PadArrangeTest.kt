package com.nxredux.mobile.ui.nx

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.nxredux.mobile.layout.ScreenClass
import com.nxredux.mobile.layout.besidePlacement
import com.nxredux.mobile.layout.landscapeUnitPx
import com.nxredux.mobile.layout.reach
import com.nxredux.mobile.layout.rowsOnScreen
import com.nxredux.mobile.options.PxRect
import com.nxredux.mobile.settings.GroupTweak
import com.nxredux.mobile.settings.PadGroup
import com.nxredux.mobile.settings.PadLayout
import kotlin.math.hypot
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

    @Test fun ps2LandscapeDpadIsFullSizeAndTheStickClearsItsButtons() {
        for (arrangement in DualStickArrangement.entries) for (side in PadSide.entries) {
            val c = dualStickCluster(side, arrangement)
            val at = "$arrangement $side"
            assertEquals(3f * PS2_LANDSCAPE_SCALE, c.main.width, 1e-4f)   // the portrait pad's d-pad / diamond
            assertEquals(PS2_PORTRAIT_STICK_UNITS * PS2_LANDSCAPE_SCALE, c.stick.width, 1e-4f)
            for (r in listOf(c.main, c.stick, c.thumb)) assertTrue(r.left >= -1e-4f && r.top >= -1e-4f && r.right <= c.width + 1e-4f && r.bottom <= c.height + 1e-4f, "$at: $r outside")
            assertTrue(c.thumb.top >= c.stick.bottom, "$at: L3 / R3 not under the stick")
            assertFalse(c.thumb.overlaps(c.main), "$at: the pill over the d-pad / diamond")
            val m = c.main
            val centre = c.stick.center
            val radius = c.stick.width / 2f
            val t = m.width / 3f
            if (side == PadSide.LEFT) {
                // The cross's arms: right (middle row, right third) and down (bottom row, middle third).
                for (arm in listOf(Rect(m.left + 2f * t, m.top + t, m.right, m.top + 2f * t), Rect(m.left + t, m.top + 2f * t, m.left + 2f * t, m.bottom))) {
                    assertTrue(distanceTo(arm, centre) >= radius, "$at: the stick over the arm $arm")
                }
                assertTrue(c.stick.left > m.center.x && c.stick.top >= m.center.y)   // below-right of the d-pad's middle
            } else {
                // The diamond's left and bottom buttons (radius 0.5 in their thirds).
                for (b in listOf(Offset(m.left + 0.5f * t, m.top + 1.5f * t), Offset(m.left + 1.5f * t, m.top + 2.5f * t))) {
                    assertTrue((centre - b).getDistance() >= radius + 0.5f * t, "$at: the stick over the button at $b")
                }
                assertTrue(c.stick.right < m.center.x && c.stick.top >= m.center.y)   // below-left of the diamond's middle
            }
            if (arrangement == DualStickArrangement.BESIDE) {
                // Beside: the well and its pill clear of the d-pad / diamond's whole box, level with its lower part.
                assertTrue(distanceTo(m, centre) > radius, "$at: the stick's well reaches the d-pad / diamond's box")
                assertFalse(c.stickGroup.overlaps(m), "$at: the stick's group over the d-pad / diamond's box")
                assertEquals(m.bottom, c.stick.bottom, 1e-4f)
            }
        }
    }

    // The editor picks a group by its box: the stick's and the d-pad / diamond's meet only in the cross's empty lower corner
    // (stacked; beside, not at all). There the d-pad's own touch is its square box, and the stick's only its round well.
    @Test fun ps2LandscapeStickAndDpadGroupBoxesMeetOnlyInTheEmptyCorner() {
        for (side in PadSide.entries) {
            val c = dualStickCluster(side, DualStickArrangement.STACKED)
            val m = c.main
            val t = m.width / 3f
            val corner = if (side == PadSide.LEFT) Rect(m.left + 2f * t, m.top + 2f * t, m.right, m.bottom) else Rect(m.left, m.top + 2f * t, m.left + t, m.bottom)
            val g = c.stickGroup
            val meet = Rect(maxOf(g.left, m.left), maxOf(g.top, m.top), minOf(g.right, m.right), minOf(g.bottom, m.bottom))
            assertTrue(meet.left >= corner.left - 1e-4f && meet.top >= corner.top - 1e-4f && meet.right <= corner.right + 1e-4f && meet.bottom <= corner.bottom + 1e-4f, "$side: $meet outside the corner $corner")
            assertFalse(dualStickCluster(side, DualStickArrangement.BESIDE).let { it.stickGroup.overlaps(it.main) })
        }
    }

    // The stick takes touches only inside its round well ([cStickWellHit]), so the d-pad keeps its down-right diagonal:
    // from near the middle to about half-way into the corner cell past where the right and down arms meet, a touch is the
    // d-pad's, though the stick's square box reaches over it there. Only the outer half of that cell, under the well's
    // edge, is still the stick's (stacked; beside, none of the d-pad's box is).
    @Test fun ps2LandscapeDpadDownRightCornerIsNotTheSticks() {
        val c = dualStickCluster(PadSide.LEFT, DualStickArrangement.STACKED)
        val m = c.main
        val t = m.width / 3f
        var inSquare = 0
        for (i in 0..20) {
            val p = m.center + Offset(1f, 1f) * (t * 0.3f + t * 0.65f * i / 20f)   // near the middle to 0.45 into the corner cell
            assertTrue(m.contains(p))
            if (c.stick.contains(p)) inSquare++
            assertFalse(cStickWellHit(c.stick, p), "$p is the stick's")
        }
        assertTrue(inSquare > 0)   // the square box would have taken some of them
        // The box's corner beyond, under the drawn well, stays the stick's.
        assertTrue(cStickWellHit(c.stick, Offset(m.right, m.bottom)))
        // Beside the d-pad, none of its box is the stick's.
        val b = dualStickCluster(PadSide.LEFT, DualStickArrangement.BESIDE)
        assertFalse(cStickWellHit(b.stick, Offset(b.main.right, b.main.bottom)))
        assertTrue(distanceTo(b.main, b.stick.center) > b.stick.width / 2f)
    }

    @Test fun ps2LandscapeSidesFitTheHalf() {
        val other = ps2OtherRowsUnits(overGame = true, besidePicture = false, secondRow = false)
        // A tall half: both fit at full size, a tie, so stacked (as before).
        assertEquals(DualStickFit(DualStickArrangement.STACKED, 1f), dualStickFit(boxW = 4.9f, boxH = 13f, otherH = other, nudge = 0.15f))
        // A narrow half (the Fold unfolded over the game: width-bound): stacked, the narrower one.
        assertEquals(DualStickArrangement.STACKED, dualStickFit(boxW = 4.5f, boxH = 8.4f, otherH = other, nudge = 0.2f).arrangement)
        // A phone's half: short and wide, beside, and it fits.
        val phone = dualStickFit(boxW = 6.7f, boxH = 6f, otherH = other, nudge = 0.2f)
        assertEquals(DualStickArrangement.BESIDE, phone.arrangement)
        val c = phone.cluster(PadSide.LEFT)
        assertTrue(phone.scale < 1f && other + c.height * phone.scale <= 6f + 1e-4f && 0.2f + c.width * phone.scale <= 6.7f + 1e-4f)
    }

    // An 800 x 360 dp phone: the d-pad at least the size the compact side drew it at before (2.1 units, ≈ 115 dp over the game).
    @Test fun ps2LandscapeDpadOnAPhoneIsNoSmallerThanBefore() {
        val d = 2.625f
        val (w, h) = 800f to 360f
        fun dpad(fit: DualStickFit, unit: Float) = 3f * PS2_LANDSCAPE_SCALE * fit.scale * unit
        // Over the game: each half 400 wide, 16 dp padding.
        val unit = landscapeClusterUnit(w, h)
        val boxW = (w * CLUSTER_MAX_WIDTH_SHARE - 2 * CLUSTER_PADDING) / unit
        val boxH = (h - 2 * CLUSTER_PADDING) / unit
        val other = ps2OtherRowsUnits(overGame = true, besidePicture = false, secondRow = false)
        val over = dualStickFit(boxW, boxH, other, leftClusterNudge(true))
        assertEquals(DualStickArrangement.BESIDE, over.arrangement)
        assertTrue(dpad(over, unit) >= maxOf(115f, 2.1f * unit), "over the game: ${dpad(over, unit)} dp")
        // Beside the picture, with and without the second row: the box the game places, only its rows on screen.
        for (secondRow in listOf(false, true)) {
            val unitPx = landscapeUnitPx(ScreenClass.REGULAR_PHONE, h * d, h * d, 420f, d, secondRow)
            val r = reach(unitPx, d, 0f, 0f, null, LANDSCAPE_DUAL_W_UNITS, 2)
            val p = besidePlacement(PxRect(0f, 0f, w * d, h * d), null, unitPx, r, d, secondRow)
            val rows = minOf(p.boxHeightPx / unitPx - 2 * CLUSTER_PADDING * d / unitPx, p.rowsOnScreen(0f, unitPx, d))
            val bw = p.boxWidthPx / unitPx - 2 * CLUSTER_PADDING * d / unitPx
            val o = ps2OtherRowsUnits(overGame = true, besidePicture = true, secondRow = secondRow)
            val fit = dualStickFit(bw, rows, o, leftClusterNudge(true))
            val c = fit.cluster(PadSide.LEFT)
            assertEquals(DualStickArrangement.BESIDE, fit.arrangement)
            assertTrue(o + c.height * fit.scale <= rows + 1e-3f, "secondRow=$secondRow: past the window's top")
            assertTrue(dpad(fit, unitPx / d) >= maxOf(115f, 2.1f * unitPx / d), "secondRow=$secondRow: ${dpad(fit, unitPx / d)} dp")
        }
    }

    // Beside the picture the box is 6 units tall (7.07 with the second row, whose rows take the 1.07 more), and its width
    // comes from the reach estimate: the estimate is the width the side is drawn at there, which the box then holds.
    @Test fun ps2LandscapeReachEstimateIsTheDrawnWidthBesideThePicture() {
        for ((boxH, secondRow) in listOf(6f to false, 6f + 0.72f + 0.35f to true)) {
            val other = ps2OtherRowsUnits(overGame = true, besidePicture = true, secondRow = secondRow)
            val fit = dualStickFit(LANDSCAPE_DUAL_W_UNITS + OVERLAY_EDGE_NUDGE, boxH, other, OVERLAY_EDGE_NUDGE)
            assertEquals(LANDSCAPE_DUAL_W_UNITS, fit.cluster(PadSide.LEFT).width * fit.scale, 1e-3f)
            assertEquals(fit, dualStickFit(100f, boxH, other, OVERLAY_EDGE_NUDGE))   // the width did not hold it back
        }
        assertEquals(LANDSCAPE_DUAL_W_UNITS, landscapeClusterUnits(PadProfile.PS2))
    }

    private fun distanceTo(r: Rect, p: Offset): Float = hypot(p.x - p.x.coerceIn(r.left, r.right), p.y - p.y.coerceIn(r.top, r.bottom))

    @Test fun ps2CompactSidesKeepDpadAndStickApart() {
        for (side in PadSide.entries) {
            val c = compactDualStickCluster(side)
            assertFalse(c.main.overlaps(c.stick), "$side: d-pad / face and stick overlap")
            assertFalse(c.stickGroup.overlaps(c.main), "$side: stick group over the d-pad / face")
        }
    }

    @Test fun ps2OverlaySidesFitTheBand() {
        // A 9-unit band less the edges: both sides the d-pad row's gap apart, no taller than the row.
        val row = 8.4f
        val s = dualStickScale(row, stickScale = 1f)
        assertTrue(2f * compactDualStickCluster(PadSide.LEFT).width * s + 0.5f <= row + 1e-4f)
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
