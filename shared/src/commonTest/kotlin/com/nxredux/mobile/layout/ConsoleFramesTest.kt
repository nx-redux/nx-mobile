package com.nxredux.mobile.layout

import com.nxredux.mobile.runner.CoreRegistry
import kotlin.test.Test
import kotlin.test.assertTrue

/** Each handheld and filling console has its usual frame in [CONSOLE_FRAMES]. */
class ConsoleFramesTest {
    // The consoles once scaled by a whole number of their own size, and the ones that fill their space.
    private val framed = setOf("gb", "gbc", "gba", "sgb", "gg", "ngp", "ngpc", "wsc", "lynx", "pkm", "vb", "p8") +
        setOf("n64", "ps", "dc", "gc", "wii", "psp", "ps2")

    @Test fun every_handheld_and_fill_console_has_its_own_frame() {
        for (c in CoreRegistry.consoles) if (c.id in framed) assertTrue(c.id in CONSOLE_FRAMES, c.id)
    }
}
