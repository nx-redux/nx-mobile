package com.swordfish.libretrodroid

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class DetachedVirtualFileTest {
    private val closed = mutableListOf<Int>()
    private fun files(vararg fds: Int) = fds.mapIndexed { i, fd -> DetachedVirtualFile("f$i", fd) }

    @Test fun successLeavesEveryFdToNative() {
        val list = files(3, 4, 5)
        val out = handOver(list, close = { closed += it }) { "ok" }
        assertEquals("ok", out)
        assertTrue(closed.isEmpty())
    }

    @Test fun walkStoppedPartWayClosesOnlyTheUnreadFds() {
        val list = files(3, 4, 5)
        try {
            handOver(list, close = { closed += it }) {
                it[0].taken = true   // native read the first, then a Java exception stopped it
                throw IllegalStateException("from the walk")
            }
            fail("expected the throw")
        } catch (e: IllegalStateException) {
            assertEquals("from the walk", e.message)
        }
        assertEquals(listOf(4, 5), closed)
    }

    @Test fun failureAfterNativeReadAllClosesNothing() {
        val list = files(3, 4)
        runCatching {
            handOver(list, close = { closed += it }) {
                it.forEach { f -> f.taken = true }
                throw RuntimeException("load failed")
            }
        }
        assertTrue(closed.isEmpty())
    }

    @Test fun detachFailingPartWayClosesTheDetachedOnes() {
        val result = runCatching {
            detachAll(listOf(10, 11, 12), close = { closed += it }) { fd ->
                if (fd == 12) error("dup failed") else DetachedVirtualFile("f$fd", fd)
            }
        }
        assertTrue(result.isFailure)
        assertEquals(listOf(10, 11), closed)
    }
}
