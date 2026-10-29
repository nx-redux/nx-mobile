package com.nxredux.mobile.runner

import com.nxredux.mobile.storage.Recent

/** The only door to the native emulator. Platform modules implement it; shared code never sees what is behind it. */
interface EmulatorRunner {
    /**
     * Starts [session]. [recent] is what the menu records as Recently Played, but only once the core has actually loaded
     * the game, never when the runner refuses it (a bad archive, a missing BIOS, a core load error) or the process dies
     * first. The runner keeps it as a launch record the game screen marks started; the menu does the recording.
     */
    fun launch(session: GameSession, recent: Recent)
}
