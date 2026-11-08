package com.nxredux.mobile

import org.junit.Assert.assertEquals
import org.junit.Test

class KeepAwakeTest {
    private class FakeLocks : AwakeLocks {
        var acquired = 0
        var released = 0
        val held get() = acquired > released
        override fun acquire() { acquired++ }
        override fun release() { released++ }
    }

    // A job wanting the service holds the CPU and Wi-Fi awake once, however many updates it sends.
    @Test fun heldOnceWhileWantedAndReleasedWhenNot() {
        val locks = FakeLocks()
        val awake = KeepAwake { locks }
        awake.hold(true); awake.hold(true); awake.hold(true)
        assertEquals(1, locks.acquired)
        assertEquals(true, locks.held)
        awake.hold(false); awake.hold(false)
        assertEquals(1, locks.released)
        assertEquals(false, locks.held)
    }

    @Test fun aNewJobAcquiresAgain() {
        val made = ArrayList<FakeLocks>()
        val awake = KeepAwake { FakeLocks().also(made::add) }
        awake.hold(false)   // nothing held: nothing to release
        assertEquals(0, made.size)
        awake.hold(true); awake.hold(false); awake.hold(true)
        assertEquals(1, made.size)   // the locks are made once and reused
        assertEquals(2, made[0].acquired)
        assertEquals(1, made[0].released)
    }
}
