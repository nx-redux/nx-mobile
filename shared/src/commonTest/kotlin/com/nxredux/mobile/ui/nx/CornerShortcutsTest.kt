package com.nxredux.mobile.ui.nx

import com.nxredux.mobile.shortcuts.ShortcutAction
import com.nxredux.mobile.shortcuts.TouchSlot
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The touch shortcut buttons in the screen's bottom-right corner ([cornerShortcuts]): with no pad drawn (a controller
 * attached), the slots the player set, in slot order, all four alike (user).
 */
class CornerShortcutsTest {
    private val ff = TouchShortcut(ShortcutAction.TOGGLE_FF, on = true)
    private val load = TouchShortcut(ShortcutAction.LOAD_STATE, on = false)

    @Test fun theSetSlotsInSlotOrder() {
        val set = mapOf(TouchSlot.FOUR to load, TouchSlot.ONE to ff)
        assertEquals(listOf(TouchSlot.ONE to ff, TouchSlot.FOUR to load), cornerShortcuts(set, padDrawn = false))
    }

    @Test fun noneUnderAPadWithItsOwnStrip() {
        assertEquals(emptyList<Pair<TouchSlot, TouchShortcut>>(), cornerShortcuts(mapOf(TouchSlot.ONE to ff), padDrawn = true))
    }

    @Test fun slotsThreeAndFourAreNamedAsTheOthers() {   // no longer a skin-only pair
        assertEquals(listOf("Touch button 1", "Touch button 2", "Touch button 3", "Touch button 4"), TouchSlot.entries.map { it.label })
    }
}
