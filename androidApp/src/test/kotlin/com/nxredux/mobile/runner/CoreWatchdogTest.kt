package com.nxredux.mobile.runner

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CoreWatchdogTest {
    @Test fun aStepPastTheLimitReportsTheHangOnce() {
        val stalled = AtomicLong(0)
        val hangs = AtomicInteger()
        val fired = CountDownLatch(1)
        val dog = CoreWatchdog({ stalled.get() }, limitMillis = { 100 }, pollMillis = 5) { hangs.incrementAndGet(); fired.countDown() }
        dog.start()
        stalled.set(150)
        assertTrue(fired.await(2, TimeUnit.SECONDS))
        Thread.sleep(50)   // a hang is reported once: the watchdog ends with it
        assertEquals(1, hangs.get())
    }

    @Test fun stepsUnderTheLimitNeverReport() {
        val fired = CountDownLatch(1)
        val dog = CoreWatchdog({ 99 }, limitMillis = { 100 }, pollMillis = 5) { fired.countDown() }
        dog.start()
        assertFalse(fired.await(100, TimeUnit.MILLISECONDS))
        dog.stop()
    }

    @Test fun aStoppedWatchdogNeverReports() {
        val fired = CountDownLatch(1)
        val dog = CoreWatchdog({ 1_000 }, limitMillis = { 100 }, pollMillis = 50) { fired.countDown() }
        dog.start()
        dog.stop()
        assertFalse(fired.await(150, TimeUnit.MILLISECONDS))
    }

    @Test fun aLimitThatDropsCatchesTheStepAlreadyRunning() {
        val limit = AtomicLong(1_000)
        val fired = CountDownLatch(1)
        val dog = CoreWatchdog({ 150 }, limitMillis = { limit.get() }, pollMillis = 5) { fired.countDown() }
        dog.start()
        assertFalse(fired.await(50, TimeUnit.MILLISECONDS))
        limit.set(100)
        assertTrue(fired.await(2, TimeUnit.SECONDS))
    }

    @Test fun aCoreWithItsOwnLimitIsWatchedWithItUntilTheGameIsReady() {
        val cemu = CoreRegistry.forTag("WIIU")
        assertEquals(120_000L, CoreWatchdog.limitFor(cemu, gameReady = false))
        assertEquals(CoreWatchdog.LIMIT_MILLIS, CoreWatchdog.limitFor(cemu, gameReady = true))
    }

    @Test fun everyOtherCoreAndNoCoreGetTheDefault() {
        assertEquals(CoreWatchdog.LIMIT_MILLIS, CoreWatchdog.limitFor(CoreRegistry.forTag("GBA"), gameReady = false))
        assertEquals(CoreWatchdog.LIMIT_MILLIS, CoreWatchdog.limitFor(null, gameReady = false))
    }
}
