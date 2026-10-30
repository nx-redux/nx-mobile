package com.nxredux.mobile.runner

import android.app.ApplicationExitInfo
import org.junit.Assert.assertEquals
import org.junit.Test

class GameExitCheckTest {
    private fun classify(reason: Int) = GameExitCheck.classify(reason)

    @Test fun crashesAreReported() {
        assertEquals(GameExit.CRASH, classify(ApplicationExitInfo.REASON_CRASH))
        assertEquals(GameExit.CRASH, classify(ApplicationExitInfo.REASON_CRASH_NATIVE))
        assertEquals(GameExit.CRASH, classify(ApplicationExitInfo.REASON_ANR))
    }

    @Test fun lowMemoryIsSilent() {
        assertEquals(GameExit.NONE, classify(ApplicationExitInfo.REASON_LOW_MEMORY))
    }

    @Test fun theGamesOwnEndIsSilent() {
        assertEquals(GameExit.NONE, classify(ApplicationExitInfo.REASON_EXIT_SELF))
        // Process.killProcess(myPid()), the normal end after a quit, is reported as a signal.
        assertEquals(GameExit.NONE, classify(ApplicationExitInfo.REASON_SIGNALED))
        assertEquals(GameExit.NONE, classify(ApplicationExitInfo.REASON_USER_REQUESTED))
        assertEquals(GameExit.NONE, classify(-1))
    }

    private val crash = GameExit.CRASH
    private val quit = GameExit.NONE

    @Test fun firstRunWithNoHistoryStillCatchesTheNextCrash() {
        val (first, mark) = GameExitCheck.decide(emptyList(), null, hangShown = false, now = 1000)
        assertEquals(GameExit.NONE, first)
        assertEquals(1000L, mark)
        assertEquals(GameExit.CRASH, GameExitCheck.decide(listOf(1500L to crash), mark, false, 2000).first)
    }

    @Test fun firstRunSkipsOldDeaths() {
        val (exit, mark) = GameExitCheck.decide(listOf(500L to crash), null, false, 1000)
        assertEquals(GameExit.NONE, exit)
        assertEquals(1000L, mark)
    }

    @Test fun crashThenNormalQuitStillShowsCrash() {
        val (exit, mark) = GameExitCheck.decide(listOf(1500L to crash, 1800L to quit), 1000L, false, 2000)
        assertEquals(GameExit.CRASH, exit)
        assertEquals(1800L, mark)
    }

    @Test fun sameCrashIsNotShownTwice() {
        val (_, mark) = GameExitCheck.decide(listOf(1500L to crash), 1000L, false, 2000)
        assertEquals(GameExit.NONE, GameExitCheck.decide(listOf(1500L to crash), mark, false, 2100).first)
    }

    @Test fun hangWinsAndAdvancesTheMark() {
        val (exit, mark) = GameExitCheck.decide(listOf(1500L to crash), 1000L, true, 2000)
        assertEquals(GameExit.HANG, exit)
        assertEquals(1500L, mark)
    }
}
