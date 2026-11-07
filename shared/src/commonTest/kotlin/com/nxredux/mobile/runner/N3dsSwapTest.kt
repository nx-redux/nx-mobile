package com.nxredux.mobile.runner

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class N3dsSwapTest {
    @Test fun toggleFlipsOnPressOnly() {
        assertTrue(N3dsSwap.next(swapped = false, mode = "Toggle", down = true))
        assertTrue(N3dsSwap.next(swapped = true, mode = "Toggle", down = false))
        assertFalse(N3dsSwap.next(swapped = true, mode = "Toggle", down = true))
    }

    @Test fun holdSwapsWhileDown() {
        assertTrue(N3dsSwap.next(swapped = false, mode = "Hold", down = true))
        assertFalse(N3dsSwap.next(swapped = true, mode = "Hold", down = false))
    }

    @Test fun startsFromTheStoredScreen() {
        assertTrue(N3dsSwap.initial(mapOf("citra_swap_screen" to "Bottom")))
        assertFalse(N3dsSwap.initial(emptyMap()))
    }

    @Test fun factorFromTheMapsTopScreen() {
        assertEquals(3, N3dsSwap.factorFromTopHeight(720))
        assertEquals(1, N3dsSwap.factorFromTopHeight(0))
    }
}
