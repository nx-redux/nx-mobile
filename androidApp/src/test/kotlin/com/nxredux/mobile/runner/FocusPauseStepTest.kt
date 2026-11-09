package com.nxredux.mobile.runner

import org.junit.Assert.assertEquals
import org.junit.Test

class FocusPauseStepTest {
    private fun step(paused: Boolean, top: Boolean, setting: Boolean, resumed: Boolean = true) =
        focusPauseStep(focusPaused = paused, topResumed = top, pauseWhenUnfocused = setting, resumed = resumed)

    @Test fun pausedWithTheOldValueResumesOnceTheSettingIsOff() =
        assertEquals(FocusPauseStep.RESUME, step(paused = true, top = false, setting = false))

    @Test fun pausedButTopAgainResumes() =
        assertEquals(FocusPauseStep.RESUME, step(paused = true, top = true, setting = true))

    @Test fun pausedNotTopSettingOnStays() =
        assertEquals(FocusPauseStep.NONE, step(paused = true, top = false, setting = true))

    @Test fun playingNotTopSettingTurnedOnPauses() =
        assertEquals(FocusPauseStep.PAUSE, step(paused = false, top = false, setting = true))

    @Test fun playingNotTopButNotResumedLeavesItToTheLifecycle() =
        assertEquals(FocusPauseStep.NONE, step(paused = false, top = false, setting = true, resumed = false))

    @Test fun playingTopOrSettingOffStays() {
        assertEquals(FocusPauseStep.NONE, step(paused = false, top = true, setting = true))
        assertEquals(FocusPauseStep.NONE, step(paused = false, top = false, setting = false))
    }
}
