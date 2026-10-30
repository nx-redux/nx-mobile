package com.nxredux.mobile.storage

/**
 * When [DataRoot]'s one-time move may run: never while a game session or Device Sync uses the data root ([begin] to
 * [end]), and one at a time ([tryStart] to [finish]). A background move asked for while the data is in use is
 * postponed: the last [end] says to start it. Uses by the game process are kept per pid ([beginFor], [endFor]): a pid
 * that [isAlive] no longer reports (killed without its end) counts as no use, so it never holds the move off for good.
 */
internal class MoveGate(private val isAlive: (Int) -> Boolean = { true }) {
    private var local = 0
    private val games = HashMap<Int, Int>()
    private val users: Int get() {
        games.keys.filterNot(isAlive).forEach { games.remove(it) }
        return local + games.values.sum()
    }
    private var running = false
    private var postponed = false

    /** A game session or a Device Sync run starts using the data root. */
    @Synchronized fun begin() { local++ }

    /** The game process [pid] starts using the data root. */
    @Synchronized fun beginFor(pid: Int) { games.merge(pid, 1, Int::plus) }

    /** A use of [pid] ended; true as [end]. */
    @Synchronized fun endFor(pid: Int): Boolean {
        games.computeIfPresent(pid) { _, n -> (n - 1).takeIf { it > 0 } }
        return released()
    }

    /** A use ended; true when it was the last and a move was postponed for it (the caller starts it now). */
    @Synchronized fun end(): Boolean {
        if (local > 0) local--
        return released()
    }

    private fun released(): Boolean {
        if (users > 0 || running || !postponed) return false
        postponed = false
        return true
    }

    /**
     * True when a move may start now (it runs until [finish]). False while one runs, or while the data is in use: then,
     * with [postpone], the last [end] asks for it again. [gameLive]: a game process is alive that this gate has no use
     * for (the menu process restarted mid-game), so the start waits for it too (a later start asks again).
     */
    @Synchronized fun tryStart(postpone: Boolean, gameLive: Boolean = false): Boolean {
        if (running) return false
        if (users > 0 || gameLive) {
            if (postpone) postponed = true
            return false
        }
        running = true
        return true
    }

    @Synchronized fun finish() { running = false }

    /** Nothing uses the data root now. */
    val idle: Boolean @Synchronized get() = users == 0
}
