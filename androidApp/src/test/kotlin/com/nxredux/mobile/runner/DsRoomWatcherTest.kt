package com.nxredux.mobile.runner

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class DsRoomWatcherTest {
    private var room = DsPresence.OUT
    private val changes = mutableListOf<DsPresence>()
    private fun watcher(scope: CoroutineScope = CoroutineScope(Job())) = DsRoomWatcher(scope, { room }) { changes += it }

    @Test fun onlyChangesAreTold() {
        val w = watcher()
        w.check()
        assertEquals(emptyList<DsPresence>(), changes)   // starts out
        room = DsPresence.ROOM
        w.check(); w.check()
        room = DsPresence.SEEKING
        w.check()
        room = DsPresence.OUT
        w.check()
        assertEquals(listOf(DsPresence.ROOM, DsPresence.SEEKING, DsPresence.OUT), changes)
        assertEquals(DsPresence.OUT, w.current)
    }

    @Test fun nativeValuesMapToPresences() {
        assertEquals(DsPresence.OUT, DsPresence.of(0))
        assertEquals(DsPresence.SEEKING, DsPresence.of(1))
        assertEquals(DsPresence.ROOM, DsPresence.of(2))
        assertEquals(DsPresence.OUT, DsPresence.of(7))
    }

    @Test fun startReadsAtOnceAndStopEndsTheReads() = runBlocking {
        room = DsPresence.ROOM
        val w = watcher(this)
        w.start()
        w.start()   // a second start is no second loop
        delay(50)
        assertEquals(listOf(DsPresence.ROOM), changes)
        assertEquals(DsPresence.ROOM, w.current)
        w.stop()
        room = DsPresence.OUT
        delay(DsRoomWatcher.TICK_MS + 100)
        assertEquals(listOf(DsPresence.ROOM), changes)
    }
}
