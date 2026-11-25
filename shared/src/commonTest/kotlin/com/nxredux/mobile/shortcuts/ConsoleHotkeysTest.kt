package com.nxredux.mobile.shortcuts

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ConsoleHotkeysTest {
    private fun notes(tag: String) = ConsoleHotkeys.claimed(tag).associate { it.binding to it.note }
    private fun single(c: Int) = ShortcutBinding(null, c)
    private fun chord(c: Int) = ShortcutBinding(KeyCodes.BUTTON_SELECT, c)

    @Test fun everyConsoleClaimsMenuAndChord() {
        for (tag in listOf("GBA", "NDS", "3DS", "WII", "WIIU", "")) {
            val c = ConsoleHotkeys.claimed(tag).map { it.binding }
            assertTrue(single(KeyCodes.BUTTON_MODE) in c && chord(KeyCodes.BUTTON_START) in c, tag)
        }
        assertEquals(ConsoleHotkeys.always, ConsoleHotkeys.claimed("GBA"))
    }

    @Test fun nds() {
        val n = notes("nds")
        assertEquals("R2 is the stylus", n[single(KeyCodes.BUTTON_R2)])
        assertEquals("L2 swaps the DS screens", n[single(KeyCodes.BUTTON_L2)])
        for (d in listOf(KeyCodes.DPAD_UP, KeyCodes.DPAD_DOWN, KeyCodes.DPAD_LEFT, KeyCodes.DPAD_RIGHT)) assertTrue(chord(d) in n)
    }

    @Test fun threeDs() {
        val n = notes("3DS")
        for (c in listOf(KeyCodes.DPAD_LEFT, KeyCodes.DPAD_RIGHT, KeyCodes.BUTTON_L1, KeyCodes.BUTTON_R1)) assertTrue(chord(c) in n)
        assertEquals("L3 swaps screens", n[single(KeyCodes.BUTTON_THUMBL)])
        assertTrue(single(KeyCodes.BUTTON_R2) !in n)   // ZR reaches the core
    }

    @Test fun wii() {
        val n = notes("Wii")
        assertEquals("L3 recentres motion", n[single(KeyCodes.BUTTON_THUMBL)])
        assertEquals("R3 is HOME", n[single(KeyCodes.BUTTON_THUMBR)])
    }

    // SELECT is the GamePad's minus and L3 a real button: only the SELECT chords are claimed
    @Test fun wiiu() {
        val n = notes("WIIU")
        assertEquals("SELECT+Left cycles the Wii U layout", n[chord(KeyCodes.DPAD_LEFT)])
        assertEquals("SELECT+Right cycles the Wii U layout", n[chord(KeyCodes.DPAD_RIGHT)])
        assertEquals("SELECT+L1 swaps the Wii U screens", n[chord(KeyCodes.BUTTON_L1)])
        assertEquals(ConsoleHotkeys.always.size + 3, n.size)
        assertTrue(single(KeyCodes.BUTTON_THUMBL) !in n && chord(KeyCodes.BUTTON_R1) !in n)
    }
}
