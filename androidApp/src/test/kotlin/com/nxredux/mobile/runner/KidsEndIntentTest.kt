package com.nxredux.mobile.runner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KidsEndIntentTest {
    @Test fun kidsLockIntentIsEndGameWithFlag() {
        val spec = endGameSpec("com.nxredux.mobile", kidsLock = true)
        assertEquals("com.nxredux.mobile.END_GAME", spec.action)
        assertEquals("com.nxredux.mobile", spec.packageName)
        assertTrue(spec.kidsLock)
        assertEquals("kids_lock", EXTRA_KIDS_LOCK)
    }

    @Test fun plainEndHasNoFlag() {
        val spec = endGameSpec("com.nxredux.mobile", kidsLock = false)
        assertEquals("com.nxredux.mobile.END_GAME", spec.action)
        assertFalse(spec.kidsLock)
    }
}
