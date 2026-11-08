package com.nxredux.mobile.runner

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class HandedOffResultTest {
    @Test
    fun runBeforeAwaitHandsTheValueOver() {
        val r = HandedOffResult<ByteArray>()
        r.run { byteArrayOf(1, 2) }
        assertArrayEquals(byteArrayOf(1, 2), r.await(1000))
    }

    @Test
    fun awaitWaitsForARunOnAnotherThread() {
        val r = HandedOffResult<String>()
        thread { Thread.sleep(50); r.run { "state" } }
        assertEquals("state", r.await(5000))
    }

    @Test
    fun aRunThatNeverStartedInTimeNeverRuns() {
        val r = HandedOffResult<String>()
        assertNull(r.await(10))
        var ran = false
        r.run { ran = true; "late" }
        assertFalse(ran)
        assertNull(r.await(10))
    }

    @Test
    fun aRunUnderWayWhenTheFirstWaitEndsIsStillWaitedFor() {
        val r = HandedOffResult<String>()
        val started = CountDownLatch(1)
        thread { r.run { started.countDown(); Thread.sleep(150); "slow" } }
        started.await(5, TimeUnit.SECONDS)
        assertEquals("slow", r.await(100))
    }

    @Test
    fun aThrowingBlockGivesNullAndDoesNotThrow() {
        val r = HandedOffResult<String>()
        r.run { error("core refused") }
        assertNull(r.await(1000))
    }

    @Test
    fun nullFromTheBlockIsNull() {
        val r = HandedOffResult<String>()
        r.run { null }
        assertNull(r.await(1000))
    }
}
