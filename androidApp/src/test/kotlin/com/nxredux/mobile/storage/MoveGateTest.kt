package com.nxredux.mobile.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** When the one-time move may run: never while a game session or Device Sync uses the data. */
class MoveGateTest {
    @Test fun startsWhenNothingUsesTheData() {
        val g = MoveGate()
        assertTrue(g.tryStart(postpone = true))
        assertFalse(g.tryStart(postpone = true))   // one at a time
        g.finish()
        assertTrue(g.tryStart(postpone = true))
    }

    @Test fun aSessionPostponesTheMoveUntilItEnds() {
        val g = MoveGate()
        g.begin()
        assertFalse(g.tryStart(postpone = true))
        assertTrue(g.end())                         // the last use ended: start the postponed move now
        assertTrue(g.tryStart(postpone = true))
    }

    @Test fun waitsForTheLastOfSeveralUses() {
        val g = MoveGate()
        g.begin(); g.begin()                        // a game and Device Sync
        assertFalse(g.tryStart(postpone = true))
        assertFalse(g.end())
        assertFalse(g.idle)
        assertTrue(g.end())
        assertTrue(g.idle)
    }

    @Test fun aRefusedNowMoveIsNotPostponed() {
        val g = MoveGate()
        g.begin()
        assertFalse(g.tryStart(postpone = false))   // the upgrade screen's move: refused, it reports the failure itself
        assertFalse(g.end())
    }

    @Test fun anExtraEndNeverGoesBelowNothing() {
        val g = MoveGate()
        g.end()
        g.begin()
        assertFalse(g.idle)
        assertEquals(false, g.tryStart(postpone = false))
    }

    @Test fun aSessionStartedDuringTheMoveDoesNotStartAnother() {
        val g = MoveGate()
        assertTrue(g.tryStart(postpone = true))
        g.begin()                                   // waits on the running move (DataRoot.settled)
        assertFalse(g.tryStart(postpone = true))
        g.finish()
        assertFalse(g.end())                        // the move already ran; nothing postponed
    }

    @Test fun aDeadGameProcessCountsAsNoUse() {
        val alive = mutableSetOf(500)
        val g = MoveGate { it in alive }
        g.beginFor(500)
        assertFalse(g.tryStart(postpone = true))
        alive.clear()                               // killed without its end
        assertTrue(g.idle)
        assertTrue(g.tryStart(postpone = true))
    }

    @Test fun gameUsesAreCountedPerPid() {
        val g = MoveGate { true }
        g.beginFor(500); g.beginFor(500); g.beginFor(600)
        assertFalse(g.endFor(500))
        assertFalse(g.endFor(500))
        assertFalse(g.tryStart(postpone = true))    // 600 still uses it
        assertTrue(g.endFor(600))                   // the last: the postponed move starts
    }

    @Test fun aLiveGameProcessPostponesTheStart() {
        val g = MoveGate()
        assertFalse(g.tryStart(postpone = true, gameLive = true))   // the menu restarted mid-game: nothing in the map
        assertTrue(g.tryStart(postpone = true))
    }
}
