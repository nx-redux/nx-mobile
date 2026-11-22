package com.nxredux.mobile.runner

import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SessionHandoffTest {
    @Test fun the_first_game_owns_the_session_at_once() = runTest {
        val handoff = SessionHandoff()
        val claim = handoff.claim { error("nobody to ask") }
        assertFalse(claim.mustWait)
        claim.awaitPredecessors()   // returns at once
        assertEquals(1, handoff.liveCount)
    }

    @Test fun a_second_game_asks_the_running_one_to_quit_once() {
        val handoff = SessionHandoff()
        var asked = 0
        handoff.claim { asked++ }
        val second = handoff.claim { error("the newcomer is never asked by its own claim") }
        assertEquals(1, asked)
        assertTrue(second.mustWait)
    }

    @Test fun the_second_game_waits_until_the_first_has_released() = runTest {
        val handoff = SessionHandoff()
        val first = handoff.claim {}
        val second = handoff.claim {}
        val waiting = async { second.awaitPredecessors(); "started" }
        testScheduler.runCurrent()
        assertFalse(waiting.isCompleted)
        handoff.release(first.token)
        assertEquals("started", waiting.await())
        assertEquals(1, handoff.liveCount)
    }

    @Test fun a_third_game_asks_only_the_newest_holder_and_waits_for_every_earlier_one() = runTest {
        val handoff = SessionHandoff()
        var firstAsked = 0
        var secondAsked = 0
        val first = handoff.claim { firstAsked++ }
        val second = handoff.claim { secondAsked++ }
        val third = handoff.claim {}
        assertEquals(1, firstAsked)    // asked by the second claim only
        assertEquals(1, secondAsked)
        val waiting = async { third.awaitPredecessors() }
        handoff.release(second.token)  // the second quit while still waiting for the first
        testScheduler.runCurrent()
        assertFalse(waiting.isCompleted, "the first still holds the native session")
        handoff.release(first.token)
        waiting.await()
    }

    @Test fun a_released_game_is_neither_asked_nor_waited_for() = runTest {
        val handoff = SessionHandoff()
        val first = handoff.claim { error("released: must not be asked") }
        handoff.release(first.token)
        val second = handoff.claim {}
        assertFalse(second.mustWait)
        second.awaitPredecessors()
    }

    @Test fun release_is_idempotent() {
        val handoff = SessionHandoff()
        val first = handoff.claim {}
        handoff.release(first.token)
        handoff.release(first.token)
        assertEquals(0, handoff.liveCount)
    }

    @Test fun releasing_the_waiting_game_does_not_release_its_predecessor() = runTest {
        val handoff = SessionHandoff()
        handoff.claim {}
        val second = handoff.claim {}
        handoff.release(second.token)   // backed out before it started
        assertEquals(1, handoff.liveCount)
        val third = handoff.claim {}
        assertTrue(third.mustWait)
    }
}

class SessionHandoffWatchdogTest {
    private data class Setup(val h: SessionHandoff, val first: SessionHandoff.Token, val second: SessionHandoff.Claim)

    private fun handoffWithHolder(): Setup {
        val h = SessionHandoff()
        val first = h.claim {}.token
        return Setup(h, first, h.claim {})
    }

    @Test fun a_quick_handoff_shows_nothing() = runTest {
        val (h, first, second) = handoffWithHolder()
        var slow = 0; var stuck = 0
        val waiting = async { second.awaitWithWatchdog(onSlow = { slow++ }, onStuck = { stuck++ }) }
        testScheduler.advanceTimeBy(1_000); testScheduler.runCurrent()
        h.release(first)
        assertFalse(waiting.await())
        testScheduler.advanceTimeBy(60_000)
        assertEquals(0, slow); assertEquals(0, stuck)
    }

    @Test fun a_slow_handoff_shows_the_hint_once_and_still_starts() = runTest {
        val (h, first, second) = handoffWithHolder()
        var slow = 0; var stuck = 0
        val waiting = async { second.awaitWithWatchdog(onSlow = { slow++ }, onStuck = { stuck++ }) }
        testScheduler.advanceTimeBy(SessionHandoff.SLOW_AFTER_MS + 1); testScheduler.runCurrent()
        assertEquals(1, slow)
        assertFalse(waiting.isCompleted)
        h.release(first)
        assertFalse(waiting.await())
        assertEquals(0, stuck)
    }

    @Test fun a_stuck_handoff_reports_stuck_and_never_returns_before_the_release() = runTest {
        val (h, first, second) = handoffWithHolder()
        var slow = 0; var stuck = 0
        val waiting = async { second.awaitWithWatchdog(onSlow = { slow++ }, onStuck = { stuck++ }) }
        testScheduler.advanceTimeBy(SessionHandoff.STUCK_AFTER_MS + 1); testScheduler.runCurrent()
        assertEquals(1, slow); assertEquals(1, stuck)
        testScheduler.advanceTimeBy(600_000); testScheduler.runCurrent()
        assertFalse(waiting.isCompleted, "never starts early")
        h.release(first)
        assertTrue(waiting.await())
    }

    @Test fun nothing_to_wait_for_returns_at_once() = runTest {
        val claim = SessionHandoff().claim {}
        assertFalse(claim.awaitWithWatchdog(onSlow = { error("no hint") }, onStuck = { error("no dialog") }))
    }
}
