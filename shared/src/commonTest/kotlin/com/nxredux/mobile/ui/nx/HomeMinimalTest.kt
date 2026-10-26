package com.nxredux.mobile.ui.nx

import kotlin.test.Test
import kotlin.test.assertEquals

class HomeMinimalTest {
    // Only Continue keeps its tag in Minimal: no shelf or tool tags.
    @Test fun only_continue_wears_a_tag() {
        val rows = homeMinimalRows(listOf(com.nxredux.mobile.ui.MainRow.PickAGame, com.nxredux.mobile.ui.MainRow.PinnedTool(com.nxredux.mobile.ui.ToolRow(com.nxredux.mobile.ui.ToolKind.SETTINGS, "Settings", null))))
        assertEquals(listOf(null, null), rows.map { it.tag })
        assertEquals("Settings", rows[1].label)
    }

    // 0.5 next to the selected name's line, falling by the exponent to nothing at D (and past it).
    @Test fun outline_alpha_is_half_next_to_the_line_and_zero_at_the_edge() {
        assertEquals(.5f, minimalOutlineAlpha(0f, 300f, 2.2f), 1e-6f)
        assertEquals(0f, minimalOutlineAlpha(300f, 300f, 2.2f))
        assertEquals(0f, minimalOutlineAlpha(400f, 300f, 2.2f))
        assertEquals(.5f * kotlin.math.exp(2.2f * kotlin.math.ln(.5f)), minimalOutlineAlpha(150f, 300f, 2.2f), 1e-4f)
        assertEquals(.25f, minimalOutlineAlpha(150f, 300f, 1f), 1e-5f)
        assertEquals(0f, minimalOutlineAlpha(10f, 0f, 2.2f))
    }

    // Short bands (fewer than 8 regular 38-size pitches) use the 30 size; the text scale scales both and the threshold.
    @Test fun short_bands_use_the_30_size() {
        assertEquals(38f, minimalNameSize(bandHeightDp = 320f, textScale = 1f))   // 8 × 40: not fewer
        assertEquals(38f, minimalNameSize(bandHeightDp = 600f, textScale = 1f))
        assertEquals(30f, minimalNameSize(bandHeightDp = 319f, textScale = 1f))
        assertEquals(30f, minimalNameSize(bandHeightDp = 200f, textScale = 1f))
        assertEquals(30f * 1.2f, minimalNameSize(bandHeightDp = 350f, textScale = 1.2f), 1e-4f)   // 8 × 47.6 = 380.8
        assertEquals(38f * 1.2f, minimalNameSize(bandHeightDp = 400f, textScale = 1.2f), 1e-4f)
    }

    // exponent = clamp(rowsBelow / 4, 1.0, 2.2).
    @Test fun the_ladder_exponent_clamps() {
        assertEquals(1f, minimalLadderExponent(2f), 1e-6f)
        assertEquals(1.5f, minimalLadderExponent(6f), 1e-6f)
        assertEquals(2.2f, minimalLadderExponent(12f), 1e-6f)
        assertEquals(1f, minimalLadderExponent(0f), 1e-6f)
    }

    // D = the line's distance to the band's edge plus half a pitch; a name centred at D is at 0.
    @Test fun the_reach_runs_half_a_pitch_past_the_edge() {
        assertEquals(220f, minimalLadderReach(lineToEdge = 200f, pitch = 40f), 1e-6f)
        val d = minimalLadderReach(200f, 40f)
        assertEquals(0f, minimalOutlineAlpha(d, d, 1.5f))
        assertEquals(true, minimalOutlineAlpha(200f, d, 1.5f) > 0f)   // a name centred on the edge still shows (the mask cuts it)
    }
}
