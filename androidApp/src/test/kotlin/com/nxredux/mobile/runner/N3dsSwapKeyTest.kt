package com.nxredux.mobile.runner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class N3dsSwapKeyTest {
    @Test fun repeatsOfAClaimedPressAreTakenWithoutPressingAgain() {
        val key = N3dsSwapKey()
        var presses = 0
        assertTrue(key.down(repeat = false) { presses++; true })
        assertTrue(key.down(repeat = true) { presses++; true })
        assertTrue(key.down(repeat = true) { presses++; true })
        assertEquals(1, presses)
    }

    @Test fun repeatsOfAnUnclaimedPressGoOnToTheCore() {
        val key = N3dsSwapKey()
        assertFalse(key.down(repeat = false) { false })
        assertFalse(key.down(repeat = true) { true })
    }
}
