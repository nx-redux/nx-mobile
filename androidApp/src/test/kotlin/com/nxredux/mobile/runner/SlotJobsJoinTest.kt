package com.nxredux.mobile.runner

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SlotJobsJoinTest {
    @Test fun returnsAtOnceWithNoJobs() = runBlocking {
        joinSlotJobs(emptyList())
    }

    @Test fun waitsForARunningJob() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        val jobs = mutableListOf<Job>()
        val order = mutableListOf<String>()
        jobs += launch { gate.await(); order += "slot job" }
        val quit = launch { joinSlotJobs(jobs); order += "quit" }
        yield()
        assertTrue(quit.isActive)
        gate.complete(Unit)
        quit.join()
        assertEquals(listOf("slot job", "quit"), order)
    }

    @Test fun waitsForAJobAddedWhileWaiting() = runBlocking {
        val first = CompletableDeferred<Unit>()
        val second = CompletableDeferred<Unit>()
        val jobs = mutableListOf<Job>()
        val order = mutableListOf<String>()
        jobs += launch { first.await(); order += "first" }
        val quit = launch { joinSlotJobs(jobs); order += "quit" }
        yield()
        jobs += launch { second.await(); order += "second" }
        first.complete(Unit)
        yield()
        yield()
        assertTrue(quit.isActive)
        second.complete(Unit)
        quit.join()
        assertEquals(listOf("first", "second", "quit"), order)
    }

    @Test fun skipsFinishedAndCancelledJobs() = runBlocking {
        val done = launch {}
        done.join()
        val cancelled = launch { CompletableDeferred<Unit>().await() }
        cancelled.cancel()
        cancelled.join()
        joinSlotJobs(listOf(done, cancelled))
    }
}
