package com.nxredux.mobile.ui.nx

import com.nxredux.mobile.layout.micReachPx
import com.nxredux.mobile.layout.reach
import com.nxredux.mobile.ui.padlayout.padLayoutShowsMic
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The pad's fixed mic button: its size and inset follow the pad's unit, and on a cluster it sits right of the d-pad. */
class PadMicTest {
    private fun near(a: Float, b: Float) = assertTrue(abs(a - b) < 0.001f, "expected $b, got $a")

    @Test fun scalesWithUnit() {
        assertEquals(9f, micButtonSize(10f))
        assertEquals(18f, micButtonSize(20f))
        assertEquals(3f, micButtonInset(20f))
    }

    @Test fun aClusterPutsItPastTheDpadAndTheMenuRow() {
        // unit 50, nudge 10: the d-pad reaches 150 from the nudge, MENU + toggle 2 x 70 + 12 = 152, so 10 + 152 + 7.5.
        near(clusterMicStart(50f, 10f), 169.5f)
        // A big unit: the d-pad (3 units) is the wider, so it starts past it.
        near(clusterMicStart(100f, 20f), 20f + 300f + 15f)
    }

    @Test fun theHalvesReachCountsIt() {
        // Beside the picture and half-folded (the 0.2-unit nudge): the reach the picture keeps clear of ends where the
        // button does, at a phone's, a tablet's and the half-folded unit, at density 1 (dp = px).
        for (unit in listOf(36f, 54.7f, 49.3f)) {
            near(micReachPx(unit, 1f), clusterMicStart(unit, unit * 0.2f) + micButtonSize(unit) - unit * 0.2f)
            val r = reach(unit, 1f, 0f, 0f, measuredButtonsPx = 2.8f * unit + 12f, faceUnits = 3f, systemPills = 2, mic = true)
            assertTrue(r.buttonsPx >= micReachPx(unit, 1f), "unit $unit: buttons ${r.buttonsPx}")
        }
        // Without a mic the measured buttons stand.
        assertEquals(112f, reach(40f, 1f, 0f, 0f, measuredButtonsPx = 112f, faceUnits = 3f, systemPills = 2).buttonsPx)
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
