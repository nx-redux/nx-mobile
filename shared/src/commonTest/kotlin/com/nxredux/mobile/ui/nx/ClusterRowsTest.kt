package com.nxredux.mobile.ui.nx

import com.nxredux.mobile.shortcuts.ShortcutAction
import com.nxredux.mobile.shortcuts.TouchSlot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The landscape pad beside the picture: its second row, and the notification width that clears only its shoulders. */
class ClusterRowsTest {
    private val tools = PadTools(stylusOn = false, onStylus = {}, onSwap = {})

    // Either half having the row makes both keep it, so L1 / R1 and the d-pad / face cluster line up.
    @Test fun secondRowKeptWhenEitherHalfHasIt() {
        val oneShortcut = mapOf(TouchSlot.ONE to TouchShortcut(ShortcutAction.TOGGLE_FF, on = false))
        val left = padSecondRow(tools = null, shortcuts = oneShortcut, placeholders = false)
        val right = padSecondRow(tools = null, shortcuts = emptyMap(), placeholders = false)
        assertTrue(left)
        assertFalse(right)
        assertTrue(left || right)
        assertTrue(padSecondRow(tools, emptyMap(), placeholders = false))
        assertTrue(padSecondRow(null, emptyMap(), placeholders = true))
        assertFalse(padSecondRow(null, emptyMap(), placeholders = false))
    }

    // Shoulders: 0.2 + 1 + 0.72 + 0.35 units past the edge margin; the system rows below do not count.
    @Test fun notificationCapClearsShouldersOnly() {
        val reach = shoulderReach(unit = 50f, edge = 30f)
        assertEquals(30f + 50f * 2.27f, reach, 0.01f)
        assertEquals(800f - 2 * reach, topCentreMaxWidth(800f, reach), 0.01f)
        assertEquals(0f, topCentreMaxWidth(100f, reach))
        // Wider than the old cap, which cleared the two 1.4-unit system pills as well.
        assertTrue(topCentreMaxWidth(800f, reach) > 800f - 2 * (30f + 50f * (0.2f + 2 * 1.4f) + 12f))
    }
}
