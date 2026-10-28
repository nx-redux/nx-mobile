package com.nxredux.mobile.ui.nx

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals

/** The ink alpha ladder follows [NxColors.main], and the scrims are fixed literals. */
class ColourRolesTest {
    @Test fun theLadderFollowsTheInk() {
        val c = NxColors()
        assertEquals(Color(0xFFD8D8D8).copy(alpha = 0.38f), c.inkDim)
        assertEquals(Color(0xFFD8D8D8).copy(alpha = 0.14f), c.inkFaint)
        assertEquals(Color(0xFFD8D8D8).copy(alpha = 0.08f), c.inkEdge)
    }

    @Test fun theLadderDerivesFromMainNotTheDefaultInk() {
        val c = NxColors(main = Color.Red)
        assertEquals(Color.Red.copy(alpha = 0.38f), c.inkDim)
        assertEquals(Color.Red.copy(alpha = 0.14f), c.inkFaint)
        assertEquals(Color.Red.copy(alpha = 0.08f), c.inkEdge)
    }

    @Test fun theScrimsAreFixedLiterals() {
        val c = NxColors()
        assertEquals(Color(0xCC000000), c.scrimHints)
        assertEquals(Color(0xE6000000), c.scrimSwitcher)
        assertEquals(Color(0xBF000000), c.scrimBackdrop)
    }
}
