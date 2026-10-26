package com.nxredux.mobile.runner

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CoreRegistryMicTest {
    private fun core(id: String) = CoreRegistry.all.first { it.id == id }

    @Test fun onlyDsAnd3dsHaveMic() {
        assertEquals(setOf("melondsds", "azahar"), CoreRegistry.all.filter { it.microphone }.map { it.id }.toSet())
    }

    @Test fun melondsMicLocked() {
        val c = core("melondsds")
        val expected = mapOf(
            "melonds_mic_input" to "microphone",
            "melonds_mic_input_active" to "always",
            "melonds_show_mic_state" to "disabled",
        )
        expected.forEach { (k, v) ->
            assertEquals(v, c.defaultOptions[k], k)
            assertTrue(k in c.lockedOptions, "$k locked")
        }
    }

    @Test fun azaharMicLocked() {
        val c = core("azahar")
        assertEquals("frontend", c.defaultOptions["citra_input_type"])
        assertTrue("citra_input_type" in c.lockedOptions)
    }
}
