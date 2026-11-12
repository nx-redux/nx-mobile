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

    @Test fun ticksTheSecondsLeftAndClearsThem() = runTest {
        val ticks = mutableListOf<Int?>()
        val cap = DsMenuCap(backgroundScope, 2500, { ticks += it }) { calls++ }
        cap.sync(true)
        runCurrent()
        assertEquals(listOf<Int?>(3), ticks)
        assertEquals(3, cap.secondsLeft)
        advanceTimeBy(1000); runCurrent()
        advanceTimeBy(1000); runCurrent()
        assertEquals(listOf<Int?>(3, 2, 1), ticks)
        advanceTimeBy(500); runCurrent()
        assertEquals(listOf<Int?>(3, 2, 1, null), ticks)
        assertEquals(1, calls)
        assertEquals(null, cap.secondsLeft)
    }

    @Test fun cancellingClearsTheSecondsLeft() = runTest {
        val ticks = mutableListOf<Int?>()
        val cap = DsMenuCap(backgroundScope, 5000, { ticks += it }) { calls++ }
        cap.sync(true)
        runCurrent()
        cap.sync(false)
        assertEquals(listOf<Int?>(5, null), ticks)
        cap.sync(false)
        assertEquals(listOf<Int?>(5, null), ticks)
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
