package com.nxredux.mobile.runner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BatterySaveOnPauseTest {
    /** The GL thread's queue: events run in the order they were queued. */
    private class GlQueue(var open: Boolean = true) {
        val events = mutableListOf<Pair<String, () -> Unit>>()
        fun queue(name: String, block: () -> Unit): Boolean { if (open) events += name to block; return open }
        fun drain() { while (events.isNotEmpty()) events.removeAt(0).second() }
    }

    @Test fun idleCoreWritesAtOnce() {
        val order = mutableListOf<String>()
        val gl = GlQueue()
        writeBatterySaveOnPause(busy = false, write = { order += "sram" }, queueBeforeDestroy = { gl.queue("sram", it) })
        assertEquals(listOf("sram"), order)
        assertTrue(gl.events.isEmpty())
    }

    @Test fun busyCoreQueuesBehindTheSerializeAndAheadOfTheDestroy() {
        val order = mutableListOf<String>()
        val gl = GlQueue()
        gl.queue("serialize") { order += "serialize" }   // onStop's auto state, already queued
        writeBatterySaveOnPause(busy = true, write = { order += "sram" }, queueBeforeDestroy = { gl.queue("sram", it) })
        assertTrue("main never waits for the serialize", order.isEmpty())
        gl.queue("destroy") { order += "destroy" }   // the screen is destroyed without a quit
        gl.drain()
        assertEquals(listOf("serialize", "sram", "destroy"), order)
    }

    @Test fun busyCoreWritesAtOnceWhenItCannotBeQueued() {
        val order = mutableListOf<String>()
        val gl = GlQueue(open = false)
        writeBatterySaveOnPause(busy = true, write = { order += "sram" }, queueBeforeDestroy = { gl.queue("sram", it) })
        assertEquals(listOf("sram"), order)
    }
}
