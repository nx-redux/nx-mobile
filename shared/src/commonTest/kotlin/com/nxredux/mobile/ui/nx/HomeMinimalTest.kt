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

    // 0.5 next to the selected name's line, falling by gamma 2.2 to nothing at the band's edge (and past it).
    @Test fun outline_alpha_is_half_next_to_the_line_and_zero_at_the_edge() {
        assertEquals(.5f, minimalOutlineAlpha(0f, 300f), 1e-6f)
        assertEquals(0f, minimalOutlineAlpha(300f, 300f))
        assertEquals(0f, minimalOutlineAlpha(400f, 300f))
        assertEquals(.5f * kotlin.math.exp(2.2f * kotlin.math.ln(.5f)), minimalOutlineAlpha(150f, 300f), 1e-4f)
        assertEquals(0f, minimalOutlineAlpha(10f, 0f))
    }
}
