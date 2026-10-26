package com.nxredux.mobile.shortcuts

import com.nxredux.mobile.options.SpeedSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SpeedControlTest {
    private fun control(s: SpeedSettings = SpeedSettings(rewind = true), canRewind: Boolean = true) =
        SpeedControl().also { it.configure(s, canRewind) }

    private fun SpeedControl.tap(a: ShortcutAction) { onAction(a, true); onAction(a, false) }

    @Test fun toggleFf() {
        val c = control()
        c.tap(ShortcutAction.TOGGLE_FF)
        assertEquals(4, c.output.ffSpeed)
        c.tap(ShortcutAction.TOGGLE_FF)
        assertEquals(0, c.output.ffSpeed)
    }

    // Save & Quit asks the host to quit as the menu's Save & Quit does, on the press; the release and the speed do nothing.
    @Test fun saveAndQuitIsTheHostsOnThePress() {
        val c = control()
        val before = c.output
        assertEquals(SpeedEffect.SaveAndQuit, c.onAction(ShortcutAction.SAVE_AND_QUIT, true))
        assertNull(c.onAction(ShortcutAction.SAVE_AND_QUIT, false))
        assertEquals(before, c.output)
    }

    @Test fun holdFf() {
        val c = control()
        c.onAction(ShortcutAction.HOLD_FF, true)
        assertEquals(4, c.output.ffSpeed)
        c.onAction(ShortcutAction.HOLD_FF, false)
        assertEquals(0, c.output.ffSpeed)
    }

    @Test fun holdReleaseKeepsToggle() {
        val c = control()
        c.tap(ShortcutAction.TOGGLE_FF)
        c.tap(ShortcutAction.HOLD_FF)
        assertEquals(4, c.output.ffSpeed)
    }

    @Test fun lastToggleWins() {
        val c = control()
        c.tap(ShortcutAction.TOGGLE_FF)
        c.tap(ShortcutAction.TOGGLE_REWIND)
        assertTrue(c.output.rewinding)
        assertEquals(0, c.output.ffSpeed)
        c.tap(ShortcutAction.TOGGLE_FF)
        assertFalse(c.output.rewinding)
        assertEquals(4, c.output.ffSpeed)
    }

    @Test fun holdRewindPausesFf() {
        val c = control()
        c.tap(ShortcutAction.TOGGLE_FF)
        c.onAction(ShortcutAction.HOLD_REWIND, true)
        assertTrue(c.output.rewinding)
        assertEquals(0, c.output.ffSpeed)
        c.onAction(ShortcutAction.HOLD_REWIND, false)
        assertFalse(c.output.rewinding)
        assertEquals(4, c.output.ffSpeed)
    }

    @Test fun holdFfPausesToggledRewind() {
        val c = control()
        c.tap(ShortcutAction.TOGGLE_REWIND)
        c.onAction(ShortcutAction.HOLD_FF, true)
        assertFalse(c.output.rewinding)
        assertEquals(4, c.output.ffSpeed)
        c.onAction(ShortcutAction.HOLD_FF, false)
        assertTrue(c.output.rewinding)
        assertEquals(0, c.output.ffSpeed)
    }

    @Test fun emptyResumesFf() {
        val c = control()
        c.tap(ShortcutAction.TOGGLE_FF)
        c.setToggle(rewind = true)
        assertTrue(c.output.rewinding)
        c.onRewindEmpty()
        assertFalse(c.output.rewinding)
        assertEquals(4, c.output.ffSpeed)
    }

    @Test fun heldRewindKeepsRewindingOnEmpty() {
        val c = control()
        c.tap(ShortcutAction.TOGGLE_FF)
        c.onAction(ShortcutAction.HOLD_REWIND, true)
        c.onRewindEmpty()
        assertTrue(c.output.rewinding)
        assertEquals(0, c.output.ffSpeed)
    }

    @Test fun heldRewindReleaseAfterEmptyResumesFf() {
        val c = control()
        c.tap(ShortcutAction.TOGGLE_FF)
        c.onAction(ShortcutAction.HOLD_REWIND, true)
        c.onRewindEmpty()
        c.onAction(ShortcutAction.HOLD_REWIND, false)
        assertFalse(c.output.rewinding)
        assertEquals(4, c.output.ffSpeed)
    }

    @Test fun lateEmptyWhileNotRewindingKeepsToggle() {
        val c = control()
        c.tap(ShortcutAction.TOGGLE_REWIND)
        c.onAction(ShortcutAction.HOLD_FF, true)
        c.onRewindEmpty()
        c.onAction(ShortcutAction.HOLD_FF, false)
        assertTrue(c.output.rewinding)
    }

    @Test fun emptyEndsToggledRewind() {
        val c = control()
        c.tap(ShortcutAction.TOGGLE_REWIND)
        c.onRewindEmpty()
        assertFalse(c.output.rewinding)
        assertNull(c.output.indicator)
    }

    @Test fun releaseHoldsEndsHolds() {
        val c = control()
        c.tap(ShortcutAction.TOGGLE_FF)
        c.onAction(ShortcutAction.HOLD_REWIND, true)
        c.releaseHolds()
        assertFalse(c.output.rewinding)
        assertEquals(4, c.output.ffSpeed)
        val d = control()
        d.onAction(ShortcutAction.HOLD_FF, true)
        d.releaseHolds()
        assertEquals(0, d.output.ffSpeed)
    }

    @Test fun netplayTurnsAllOff() {
        val c = control()
        c.tap(ShortcutAction.TOGGLE_FF)
        c.setSession(netplay = true)
        assertEquals(0, c.output.ffSpeed)
        c.tap(ShortcutAction.TOGGLE_FF)
        c.tap(ShortcutAction.TOGGLE_REWIND)
        c.onAction(ShortcutAction.HOLD_FF, true)
        assertEquals(0, c.output.ffSpeed)
        assertFalse(c.output.rewinding)
        c.onAction(ShortcutAction.HOLD_FF, false)
        c.setSession(netplay = false)
        assertEquals(0, c.output.ffSpeed)
        c.tap(ShortcutAction.TOGGLE_FF)
        assertEquals(4, c.output.ffSpeed)
    }

    @Test fun rewindIgnoredWhenOff() {
        val c = control(SpeedSettings(rewind = false))
        c.tap(ShortcutAction.TOGGLE_REWIND)
        c.onAction(ShortcutAction.HOLD_REWIND, true)
        assertFalse(c.output.rewinding)
        val d = control(SpeedSettings(rewind = true), canRewind = false)
        d.tap(ShortcutAction.TOGGLE_REWIND)
        assertFalse(d.output.rewinding)
    }

    @Test fun resetAsks() {
        val c = control()
        assertEquals(SpeedEffect.AskReset, c.onAction(ShortcutAction.RESET, true))
        assertNull(c.onAction(ShortcutAction.RESET, false))
        assertNull(c.onAction(ShortcutAction.TOGGLE_FF, true))
    }

    @Test fun indicator() {
        val c = control()
        assertNull(c.output.indicator)
        c.tap(ShortcutAction.TOGGLE_FF)
        assertEquals(SpeedIndicator.FAST_FORWARD, c.output.indicator)
        c.tap(ShortcutAction.TOGGLE_REWIND)
        assertEquals(SpeedIndicator.REWIND, c.output.indicator)
    }

    @Test fun settingsChangeKeepsToggle() {
        val c = control()
        c.tap(ShortcutAction.TOGGLE_FF)
        c.configure(SpeedSettings(maxFf = 8, ffAudio = true, rewind = true), true)
        assertEquals(8, c.output.ffSpeed)
        assertTrue(c.output.ffAudio)
    }

    @Test fun rewindTurnedOffInSettingsEndsRewind() {
        val c = control()
        c.tap(ShortcutAction.TOGGLE_REWIND)
        c.configure(SpeedSettings(rewind = false), true)
        assertFalse(c.output.rewinding)
    }

    @Test fun engineWithNoBufferEndsARewindEvenAHeldOne() {
        val c = control()
        c.tap(ShortcutAction.TOGGLE_FF)
        c.onAction(ShortcutAction.HOLD_REWIND, true)
        assertTrue(c.output.rewinding)
        c.onRewindOff()   // the engine stepped back with nothing built: the press did nothing
        assertFalse(c.output.rewinding)
        assertEquals(4, c.output.ffSpeed)   // the toggled FF the hold paused plays on
        assertTrue(c.rewindOn)   // still offered: the buffer may yet be built
        c.onAction(ShortcutAction.HOLD_REWIND, false)
        assertFalse(c.output.rewinding)
    }

    @Test fun rewindOffWhenNotRewindingChangesNothing() {
        val c = control()
        c.tap(ShortcutAction.TOGGLE_FF)
        c.onRewindOff()
        assertEquals(4, c.output.ffSpeed)
    }

    @Test fun engineGivingUpHidesRewindForTheSession() {
        val c = control()
        c.tap(ShortcutAction.TOGGLE_REWIND)
        c.onRewindUnavailable()
        assertFalse(c.output.rewinding)
        assertNull(c.output.indicator)
        assertFalse(c.rewindOn)
        c.configure(SpeedSettings(rewind = true), canRewind = true)   // a speed row changed: still unavailable
        assertFalse(c.rewindOn)
        c.tap(ShortcutAction.TOGGLE_REWIND)
        assertFalse(c.output.rewinding)
    }

    @Test fun micActionIgnored() {
        val c = control()
        c.tap(ShortcutAction.TOGGLE_FF)
        val before = c.output
        assertNull(c.onAction(ShortcutAction.TOGGLE_MIC, true))
        assertNull(c.onAction(ShortcutAction.TOGGLE_MIC, false))
        assertEquals(before, c.output)
    }
}
