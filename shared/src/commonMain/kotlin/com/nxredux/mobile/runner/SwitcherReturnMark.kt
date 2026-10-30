package com.nxredux.mobile.runner

/**
 * A game resumed from the Game Switcher is running (or just ended): when it ends the menu opens the switcher again, also
 * from a menu process started fresh meanwhile. The runner marks each launch ([GameSession.fromSwitcher]); the menu reads
 * the mark and clears it once used or overruled (Home).
 */
interface SwitcherReturnMark {
    fun switcherReturn(): Boolean
    fun clearSwitcherReturn()
}
