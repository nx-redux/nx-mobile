package com.nxredux.mobile.shortcuts

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ConsoleHotkeysTest {
    private fun notes(tag: String) = ConsoleHotkeys.claimed(tag).associate { it.binding to it.note }
    private fun single(c: Int) = ShortcutBinding(null, c)
    private fun chord(c: Int) = ShortcutBinding(KeyCodes.BUTTON_SELECT, c)

    @Test fun everyConsoleClaimsMenuAndChord() {
        for (tag in listOf("GBA", "NDS", "3DS", "WII", "")) {
            val c = ConsoleHotkeys.claimed(tag).map { it.binding }
            assertTrue(single(KeyCodes.BUTTON_MODE) in c && chord(KeyCodes.BUTTON_START) in c, tag)
            // Either half of the chord bound alone would take it from the chord.
            assertTrue(single(KeyCodes.BUTTON_SELECT) in c && single(KeyCodes.BUTTON_START) in c, tag)
        }
        assertEquals(ConsoleHotkeys.always, ConsoleHotkeys.claimed("GBA"))
    }

    @Test fun nds() {
        val n = notes("nds")
        assertEquals("R2 swaps the DS screens", n[single(KeyCodes.BUTTON_R2)])
        assertEquals("L2 is the stylus", n[single(KeyCodes.BUTTON_L2)])
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
}
