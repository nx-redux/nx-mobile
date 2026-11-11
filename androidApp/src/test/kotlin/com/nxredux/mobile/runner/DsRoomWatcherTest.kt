package com.nxredux.mobile.runner

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DsRoomWatcherTest {
    private var room = false
    private val changes = mutableListOf<Boolean>()
    private fun watcher(scope: CoroutineScope = CoroutineScope(Job())) = DsRoomWatcher(scope, { room }) { changes += it }

    @Test fun onlyChangesAreTold() {
        val w = watcher()
        w.check()
        assertEquals(emptyList<Boolean>(), changes)   // starts out of a room
        room = true
        w.check(); w.check()
        room = false
        w.check()
        assertEquals(listOf(true, false), changes)
        assertFalse(w.current)
    }

    @Test fun startReadsAtOnceAndStopEndsTheReads() = runBlocking {
        room = true
        val w = watcher(this)
        w.start()
        w.start()   // a second start is no second loop
        delay(50)
        assertEquals(listOf(true), changes)
        assertTrue(w.current)
        w.stop()
        room = false
        delay(DsRoomWatcher.TICK_MS + 100)
        assertEquals(listOf(true), changes)
    }
}
