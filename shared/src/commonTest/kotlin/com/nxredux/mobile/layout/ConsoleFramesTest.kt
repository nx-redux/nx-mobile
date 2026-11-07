package com.nxredux.mobile.layout

import com.nxredux.mobile.runner.CoreRegistry
import kotlin.test.Test
import kotlin.test.assertTrue

/** Each whole-number or filling console has its usual frame in [CONSOLE_FRAMES]. */
class ConsoleFramesTest {
    @Test fun every_native_and_fill_console_has_its_own_frame() {
        for (c in CoreRegistry.consoles) {
            val scale = consoleScale(c.tags.first())
            if (scale == ConsoleScale.NATIVE || scale == ConsoleScale.FILL) assertTrue(c.id in CONSOLE_FRAMES, c.id)
        }
    }
}
