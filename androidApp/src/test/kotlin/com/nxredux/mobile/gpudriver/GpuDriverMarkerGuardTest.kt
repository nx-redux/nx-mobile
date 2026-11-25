package com.nxredux.mobile.gpudriver

import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GpuDriverMarkerGuardTest {
    private val writes: MutableList<String?> = Collections.synchronizedList(ArrayList())

    @Test fun markThenClear() {
        val g = GpuDriverMarkerGuard { writes += it }
        assertTrue(g.mark("m"))
        g.clear()
        g.clear()
        assertEquals(listOf("m", null), writes)
    }

    @Test fun clearWithoutMarkWritesNothing() {
        val g = GpuDriverMarkerGuard { writes += it }
        assertFalse(g.abandon())
        g.clear()
        assertEquals(emptyList<String?>(), writes)
    }

    @Test fun abandonedBeforeTheWriteNeverWrites() {
        val g = GpuDriverMarkerGuard { writes += it }
        assertFalse(g.abandon())
        assertFalse(g.mark("m"))
        assertEquals(emptyList<String?>(), writes)
    }

    @Test fun abandonedAfterTheWriteIsCleared() {
        val g = GpuDriverMarkerGuard { writes += it }
        assertTrue(g.mark("m"))
        assertTrue(g.abandon())
        g.clear()
        assertEquals(listOf("m", null), writes)
    }

    /** The screen goes while the commit is in flight: the clear waits for it, so the marker never outlives the launch. */
    @Test fun abandonedDuringTheWriteIsClearedAfterIt() {
        val writing = CountDownLatch(1)
        val release = CountDownLatch(1)
        val g = GpuDriverMarkerGuard { v ->
            if (v != null) { writing.countDown(); release.await(5, TimeUnit.SECONDS) }
            writes += v
        }
        val writer = thread { g.mark("m") }
        assertTrue(writing.await(5, TimeUnit.SECONDS))
        assertTrue(g.abandon())   // does not wait: the onDestroy side
        val clearer = thread { g.clear() }   // the background clear, blocked behind the commit
        Thread.sleep(50)
        assertEquals(emptyList<String?>(), writes)
        release.countDown()
        writer.join(5000); clearer.join(5000)
        assertEquals(listOf("m", null), writes)
    }
}
