package com.nxredux.mobile.kids

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KidsPinTest {
    @Test fun matchesRightPinOnly() {
        val s = KidsPin.hash("4821")
        assertTrue(KidsPin.matches("4821", s))
        assertFalse(KidsPin.matches("4822", s))
    }

    @Test fun saltDiffers() = assertNotEquals(KidsPin.hash("4821"), KidsPin.hash("4821"))

    @Test fun noPlainPin() {
        assertFalse(KidsPin.hash("4821").contains("4821"))
    }

    @Test fun garbageStoredNeverMatches() {
        assertFalse(KidsPin.matches("4821", ""))
        assertFalse(KidsPin.matches("4821", "v1:x"))
    }
}
