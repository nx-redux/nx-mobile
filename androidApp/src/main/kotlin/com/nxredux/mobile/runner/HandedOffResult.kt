package com.nxredux.mobile.runner

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * A result computed on one thread ([run], the GL thread) and awaited on another ([await], the auto-state writer), so the
 * thread that queued the work (main) never waits for it. A waiter that times out before [run] starts claims it first: the
 * work then never runs, so a GL thread that recovers late never reads a core nobody is waiting on any more.
 */
internal class HandedOffResult<T : Any> {
    private val claimed = AtomicBoolean(false)
    private val done = CountDownLatch(1)
    @Volatile private var value: T? = null

    /** Runs [block] unless [await] already gave up on it. A throw counts as null: it must never end the calling thread. */
    fun run(block: () -> T?) {
        if (!claimed.compareAndSet(false, true)) return
        try { value = runCatching(block).getOrNull() } finally { done.countDown() }
    }

    /**
     * The result, or null when [block] gave none or did not finish in time. [timeoutMs] bounds the wait for it to start,
     * and again for a run already under way to finish.
     */
    fun await(timeoutMs: Long): T? {
        if (done.await(timeoutMs, TimeUnit.MILLISECONDS)) return value
        if (claimed.compareAndSet(false, true)) return null
        return if (done.await(timeoutMs, TimeUnit.MILLISECONDS)) value else null
    }
}
