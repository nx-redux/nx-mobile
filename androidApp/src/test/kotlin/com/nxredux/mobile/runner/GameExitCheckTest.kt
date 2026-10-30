package com.nxredux.mobile.runner

import android.app.ApplicationExitInfo
import org.junit.Assert.assertEquals
import org.junit.Test

class GameExitCheckTest {
    private fun classify(reason: Int) = GameExitCheck.classify(reason, null)

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
}
