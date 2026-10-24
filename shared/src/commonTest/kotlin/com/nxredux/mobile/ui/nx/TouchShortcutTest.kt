package com.nxredux.mobile.ui.nx

import com.nxredux.mobile.shortcuts.EffectiveShortcuts
import com.nxredux.mobile.shortcuts.ShortcutAction
import com.nxredux.mobile.shortcuts.SpeedIndicator
import com.nxredux.mobile.shortcuts.SpeedOutput
import com.nxredux.mobile.shortcuts.TouchSlot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TouchShortcutTest {
    private val idle = SpeedOutput(ffSpeed = 0, ffAudio = false, rewinding = false, rewindAudio = false, indicator = null)
    private val ff = idle.copy(ffSpeed = 4, indicator = SpeedIndicator.FAST_FORWARD)
    private val rewinding = idle.copy(rewinding = true, indicator = SpeedIndicator.REWIND)

    private fun effective(touch: Map<TouchSlot, ShortcutAction>) = EffectiveShortcuts(emptyMap(), touch, emptyMap())

    @Test fun labels() {
        assertEquals(
            listOf("FF", "Hold FF", "RW", "Hold RW", "Reset"),
            listOf(ShortcutAction.TOGGLE_FF, ShortcutAction.HOLD_FF, ShortcutAction.TOGGLE_REWIND, ShortcutAction.HOLD_REWIND, ShortcutAction.RESET)
                .map(::touchShortcutLabel),
        )
        assertEquals(ShortcutAction.entries.size, ShortcutAction.entries.map(::touchShortcutLabel).toSet().size)
    }

    @Test fun hidden_when_unassigned() {
        assertEquals(emptyMap(), visibleTouchShortcuts(null, idle, netplay = false))
        assertEquals(emptyMap(), visibleTouchShortcuts(effective(emptyMap()), idle, netplay = false))
        val two = effective(mapOf(TouchSlot.TWO to ShortcutAction.RESET))
        assertEquals(mapOf(TouchSlot.TWO to TouchShortcut(ShortcutAction.RESET, on = false)), visibleTouchShortcuts(two, idle, netplay = false))
    }

    @Test fun hidden_in_netplay() {
        val both = effective(mapOf(TouchSlot.ONE to ShortcutAction.TOGGLE_FF, TouchSlot.TWO to ShortcutAction.RESET))
        assertEquals(emptyMap(), visibleTouchShortcuts(both, ff, netplay = true))
    }

    @Test fun rewind_buttons_hidden_while_rewind_is_off() {
        // The core can rewind (effective keeps them) but the console's Rewind row is off: they would do nothing.
        val e = effective(mapOf(TouchSlot.ONE to ShortcutAction.HOLD_REWIND, TouchSlot.TWO to ShortcutAction.TOGGLE_FF))
        assertEquals(mapOf(TouchSlot.TWO to TouchShortcut(ShortcutAction.TOGGLE_FF, false)), visibleTouchShortcuts(e, idle, netplay = false, rewind = false))
        assertEquals(setOf(TouchSlot.ONE, TouchSlot.TWO), visibleTouchShortcuts(e, idle, netplay = false, rewind = true).keys)
    }

    @Test fun toggles_show_their_on_state_from_the_output() {
        val e = effective(mapOf(TouchSlot.ONE to ShortcutAction.TOGGLE_FF, TouchSlot.TWO to ShortcutAction.TOGGLE_REWIND))
        assertEquals(
            mapOf(TouchSlot.ONE to TouchShortcut(ShortcutAction.TOGGLE_FF, true), TouchSlot.TWO to TouchShortcut(ShortcutAction.TOGGLE_REWIND, false)),
            visibleTouchShortcuts(e, ff, netplay = false),
        )
        assertEquals(
            mapOf(TouchSlot.ONE to TouchShortcut(ShortcutAction.TOGGLE_FF, false), TouchSlot.TWO to TouchShortcut(ShortcutAction.TOGGLE_REWIND, true)),
            visibleTouchShortcuts(e, rewinding, netplay = false),
        )
    }

    @Test fun holds_and_reset_never_show_on() {
        // A hold shows its press under the finger; it is never latched by the output.
        val e = effective(mapOf(TouchSlot.ONE to ShortcutAction.HOLD_FF, TouchSlot.TWO to ShortcutAction.HOLD_REWIND))
        assertTrue(visibleTouchShortcuts(e, ff, netplay = false).values.none { it.on })
        assertTrue(visibleTouchShortcuts(e, rewinding, netplay = false).values.none { it.on })
        assertTrue(visibleTouchShortcuts(effective(mapOf(TouchSlot.ONE to ShortcutAction.RESET)), ff, netplay = false).values.none { it.on })
    }

    @Test fun portrait_shortcut_fits_beside_the_menu_row() {
        // MENU's size where the side room allows, narrower where a long MENU row (3DS STYLUS..SWAP) leaves less, never past it.
        assertEquals(50f, portraitShortcutWidth(pillWidth = 50f, sideRoom = 120f, gap = 12f))
        assertEquals(30f, portraitShortcutWidth(pillWidth = 50f, sideRoom = 42f, gap = 12f))
        assertEquals(0f, portraitShortcutWidth(pillWidth = 50f, sideRoom = 5f, gap = 12f))
    }
}
