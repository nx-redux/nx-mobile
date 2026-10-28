package com.nxredux.mobile.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BusyReasonTest {
    @Test fun nothingRunningIsFree() = assertNull(busyReason(game = false, sync = false, art = false, ra = false, cheats = false, foreground = true))

    // A game outranks a sync: the user's first step is closing the game.
    @Test fun gameComesBeforeSync() =
        assertEquals("Close your game to install", busyReason(game = true, sync = true, art = false, ra = false, cheats = false, foreground = true))

    @Test fun cheatsAlone() =
        assertEquals("Waiting for Cheats & Patches to finish", busyReason(game = false, sync = false, art = false, ra = false, cheats = true, foreground = true))

    @Test fun eachFlagAloneHasItsReason() {
        assertEquals("Close your game to install", busyReason(game = true, sync = false, art = false, ra = false, cheats = false, foreground = true))
        assertEquals("Waiting for Device Sync to finish", busyReason(game = false, sync = true, art = false, ra = false, cheats = false, foreground = true))
        assertEquals("Waiting for artwork to finish", busyReason(game = false, sync = false, art = true, ra = false, cheats = false, foreground = true))
        assertEquals("Waiting for the achievements download to finish", busyReason(game = false, sync = false, art = false, ra = true, cheats = false, foreground = true))
    }

    // Hidden, the confirm screen would be blocked: wait until the user is back.
    @Test fun notForegroundIsReady() =
        assertEquals("Ready to install", busyReason(game = false, sync = false, art = false, ra = false, cheats = false, foreground = false))

    @Test fun gameComesBeforeNotForeground() =
        assertEquals("Close your game to install", busyReason(game = true, sync = false, art = false, ra = false, cheats = false, foreground = false))

    @Test fun laterReasonsKeepTheOrder() {
        assertEquals("Waiting for Device Sync to finish", busyReason(game = false, sync = true, art = true, ra = true, cheats = true, foreground = true))
        assertEquals("Waiting for artwork to finish", busyReason(game = false, sync = false, art = true, ra = true, cheats = true, foreground = true))
        assertEquals("Waiting for the achievements download to finish", busyReason(game = false, sync = false, art = false, ra = true, cheats = true, foreground = true))
    }
}
