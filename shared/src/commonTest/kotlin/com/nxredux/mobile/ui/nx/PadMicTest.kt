package com.nxredux.mobile.ui.nx

import com.nxredux.mobile.ui.padlayout.padLayoutShowsMic
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The pad's fixed mic button: its size and inset follow the pad's unit, and on a landscape half it sits right of L. */
class PadMicTest {
    private fun near(a: Float, b: Float) = assertTrue(abs(a - b) < 0.001f, "expected $b, got $a")

    @Test fun scalesWithUnit() {
        assertEquals(9f, micButtonSize(10f))
        assertEquals(18f, micButtonSize(20f))
        assertEquals(3f, micButtonInset(20f))
    }

    @Test fun rightOfLItEndsPastTheShoulders() {
        // unit 50: L (50), 8 dp, then the 45 dp button; with ZL (0.72 units) and its own 8 dp gap first.
        near(shoulderMicEnd(listOf(NxButton.L1), 50f, 36f), 50f + 8f + 45f)
        near(shoulderMicEnd(listOf(NxButton.L1, NxButton.L2), 50f, 36f), 50f + 8f + 36f + 8f + 45f)
    }

    @Test fun rightOfLItStaysInsideTheDpadsWidth() {
        // The halves' reach counts the d-pad (3 units), not the mic: it must end inside it, with ZL too, from a small
        // phone's landscape unit up (45 dp; the half-folded Fold's 49.3; a big phone's 65).
        for (unit in listOf(45f, 49.3f, 52f, 65f)) {
            for (shoulders in listOf(listOf(NxButton.L1), listOf(NxButton.L1, NxButton.L2))) {
                val end = shoulderMicEnd(shoulders, unit, unit * 0.72f)
                assertTrue(end <= 3f * unit, "unit $unit, $shoulders: ends at $end, past ${3f * unit}")
            }
        }
    }

    @Test fun portraitCentreIsTheBandCornerOrTheOverlayRow() {
        // unit 40: the band's button 6 up, 36 across (centre 16 + 6 + 18); the overlay row 28.8 tall (centre 16 + 14.4).
        near(portraitMicCentre(40f, hiddenBand = false), 40f)
        near(portraitMicCentre(40f, hiddenBand = true), 30.4f)
    }

    @Test fun portraitEdgeIsTheBandInsetOrTheOverlayNudge() {
        near(portraitMicEdge(40f, hiddenBand = false), 22f)   // 16 + 0.15 units
        near(portraitMicEdge(40f, hiddenBand = true), 24f)    // 16 + 0.2 units
    }

    @Test fun theEditorShowsItForDsAnd3dsAndShared() {
        assertTrue(padLayoutShowsMic(""))
        assertTrue(padLayoutShowsMic("NDS"))
        assertTrue(padLayoutShowsMic("3DS"))
        assertFalse(padLayoutShowsMic("GBA"))
        assertFalse(padLayoutShowsMic("WII"))
    }
}
