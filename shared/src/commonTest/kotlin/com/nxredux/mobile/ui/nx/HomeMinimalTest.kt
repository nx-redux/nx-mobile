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

    // The same either side of the selection: 0.5 for the next name, 12% less each row on, never under 6%.
    @Test fun the_fade_steps_down_by_row_to_a_floor() {
        assertEquals(1f, minimalAlpha(0f))
        assertEquals(.5f, minimalAlpha(1f), 1e-6f)
        assertEquals(.5f, minimalAlpha(-1f), 1e-6f)
        assertEquals(.75f, minimalAlpha(.5f), 1e-6f)
        assertEquals(.38f, minimalAlpha(2f), 1e-6f)
        assertEquals(.26f, minimalAlpha(-3f), 1e-6f)
        assertEquals(.14f, minimalAlpha(4f), 1e-6f)
        assertEquals(.06f, minimalAlpha(5f), 1e-6f)
        assertEquals(.06f, minimalAlpha(-20f), 1e-6f)
    }
}
