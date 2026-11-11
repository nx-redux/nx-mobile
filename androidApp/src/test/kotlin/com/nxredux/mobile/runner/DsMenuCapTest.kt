package com.nxredux.mobile.runner

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DsMenuCapTest {
    private var calls = 0

    @Test fun firesOnceAfterTheCap() = runTest {
        val cap = DsMenuCap(backgroundScope, 1000) { calls++ }
        cap.sync(true)
        advanceTimeBy(999); runCurrent()
        assertEquals(0, calls)
        advanceTimeBy(1); runCurrent()
        assertEquals(1, calls)
        advanceTimeBy(5000); runCurrent()
        assertEquals(1, calls)
    }

    @Test fun repeatedSyncDoesNotRestart() = runTest {
        val cap = DsMenuCap(backgroundScope, 1000) { calls++ }
        cap.sync(true)
        advanceTimeBy(600); runCurrent()
        cap.sync(true)
        advanceTimeBy(400); runCurrent()
        assertEquals(1, calls)
    }

    @Test fun syncFalseCancels() = runTest {
        val cap = DsMenuCap(backgroundScope, 1000) { calls++ }
        cap.sync(true)
        advanceTimeBy(900); runCurrent()
        cap.sync(false)
        advanceTimeBy(5000); runCurrent()
        assertEquals(0, calls)
    }

    @Test fun aNewRoomGetsAFullCap() = runTest {
        val cap = DsMenuCap(backgroundScope, 1000) { calls++ }
        cap.sync(true)
        advanceTimeBy(900); runCurrent()
        cap.sync(false)
        cap.sync(true)
        advanceTimeBy(999); runCurrent()
        assertEquals(0, calls)
        advanceTimeBy(1); runCurrent()
        assertEquals(1, calls)
    }

    @Test fun countsAgainAfterFiring() = runTest {
        val cap = DsMenuCap(backgroundScope, 1000) { calls++ }
        cap.sync(true)
        advanceTimeBy(1000); runCurrent()
        assertEquals(1, calls)
        cap.sync(true)
        advanceTimeBy(1000); runCurrent()
        assertEquals(2, calls)
    }
}
