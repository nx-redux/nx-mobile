package com.nxredux.mobile.ui

import androidx.compose.ui.graphics.Color
import com.nxredux.mobile.settings.AccentPreset
import com.nxredux.mobile.ui.nx.NxDefaults
import com.nxredux.mobile.ui.nx.nxColorsFor
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The shown mark on a Tool Apps tile (disc, tick). On a lit tile an NX teal disc would vanish into a dark accent (Teal
 * is NX teal itself, Red close in tone), so there the disc takes the accent's text colour and the tick the accent; an
 * unlit tile keeps the NX teal disc under the ink.
 */
class ToolAppBadgeTest {
    @Test fun aLitTileInvertsTheAccent() {
        val red = nxColorsFor(AccentPreset.RED)
        assertEquals(Color.Black to Color(0xFFF87171), toolAppBadgeColors(red, lit = true))
        val white = nxColorsFor(AccentPreset.WHITE)
        assertEquals(Color.Black to white.accent, toolAppBadgeColors(white, lit = true))
    }

    @Test fun anUnlitTileKeepsTheTealDiscUnderTheInk() {
        val c = nxColorsFor(AccentPreset.TEAL)
        assertEquals(Color(0xFF006666) to NxDefaults.colors.main, toolAppBadgeColors(c, lit = false))
    }
}
