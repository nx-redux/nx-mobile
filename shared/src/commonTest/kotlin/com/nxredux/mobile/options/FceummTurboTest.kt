package com.nxredux.mobile.options

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** fceumm's turbo default moved to Player 1: a cfg saved before then holds the old default None, which follows the new one once. */
class FceummTurboTest {
    private fun cfg(text: String) = CoreConfig.parse(text)

    @Test
    fun aCfgSavedBeforeTheChangeWithTheOldDefaultIsMigrated() {
        assertTrue(FceummTurbo.needsMigration(cfg("fceumm_turbo_enable = None\nfceumm_aspect = 8:7 PAR\n"), modifiedMs = 100, sinceMs = 200))
    }

    @Test
    fun aCfgSavedAfterTheChangeKeepsItsChoice() {
        // saved since: None is the player's pick (the menu offers it), never touched again
        assertFalse(FceummTurbo.needsMigration(cfg("fceumm_turbo_enable = None\n"), modifiedMs = 300, sinceMs = 200))
    }

    @Test
    fun otherValuesLockedLinesAndNoFileAreLeftAlone() {
        assertFalse(FceummTurbo.needsMigration(cfg("fceumm_turbo_enable = Both\n"), 100, 200))
        assertFalse(FceummTurbo.needsMigration(cfg("-fceumm_turbo_enable = None\n"), 100, 200))
        assertFalse(FceummTurbo.needsMigration(cfg("fceumm_aspect = 8:7 PAR\n"), 100, 200))
        assertFalse(FceummTurbo.needsMigration(null, 100, 200))
    }
}
