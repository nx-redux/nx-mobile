package com.nxredux.mobile.ui

import com.nxredux.mobile.library.path
import com.nxredux.mobile.runner.SwitcherReturnMark
import com.nxredux.mobile.settings.AppSettings
import com.nxredux.mobile.settings.SwitcherLayout
import com.nxredux.mobile.storage.Recents
import com.nxredux.mobile.storage.UserDataStore
import com.nxredux.mobile.ui.nx.ListSelection
import com.nxredux.mobile.ui.nx.NxButton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** What the Game Switcher needs from [AppState]: launches, the main menu to fall back on, its rows, and the recents' lock. */
internal interface SwitcherHost {
    fun kidsLocked(): Boolean
    fun navStep(motion: NavMotion)
    /** A recents line resolved against the playable cache; null when hidden, coreless or not indexed. */
    fun resolveOne(path: String): Listed?
    /** A game started from the switcher ([AppState]'s launch); null when refused before it started. */
    fun launch(entry: Listed, resumeSlot: Int?, fromSwitcher: Boolean): Job?
    /** The main menu on its current tab with that tab's remembered row: where a switcher with no snapshot closes to. */
    fun menuWithItsRow(): Pair<Screen, ListSelection>
    /** Home's own view for the rows on screen now. */
    fun publishHomeView()
    fun swapMainRows()
    fun refreshResume()
    suspend fun refreshMainRows()
    suspend fun rebuildMainRows()
    /** recent.txt's read-modify-write lock, shared with every other writer. */
    val recentsLock: Mutex
}

/** What a launch tells the switcher ([LaunchController]): a load still under way stops, and the runner's call sets the return. */
internal interface LaunchSwitcher {
    /** A switcher still loading is abandoned (a launch, Home or B came first). */
    fun cancelLoad()
    /** The runner is being called for a game; [fromSwitcher]: it was resumed from the switcher, whose return is armed only here (a refused launch never arms it). */
    fun onRunnerCalled(fromSwitcher: Boolean)
}

/**
 * The Game Switcher (SELECT on the menu or a list): the resumable recents as cards, a game opened over it (it stays up under
 * the game and comes back after), Y Remove, and reopening it after a game resumed from it ([returns], kept on disk so a
 * menu process started later reopens it too).
 */
internal class SwitcherController(
    private val scope: CoroutineScope,
    private val screen: MutableStateFlow<Screen>,
    private val selection: MutableStateFlow<ListSelection>,
    private val settings: AppSettings,
    private val userData: UserDataStore,
    private val returns: SwitcherReturnMark?,
    private val host: SwitcherHost,
) : LaunchSwitcher {
    private var beforeSwitcher: Pair<Screen, ListSelection>? = null
    private var switcherJob: Job? = null   // switcher entries loading
    private var switcherRemoval: Job? = null   // the switcher's last Y Remove write (chained after the one before)
    private var switcherReturn = false   // a game resumed from the switcher is running: the menu's next foreground reopens it

    /** [switcherReturn] came from disk (this menu started while, or after, such a game ran): it waits for the first loaded screen. */
    private var switcherRestored = false

    /** A game resumed from the switcher ended: the switcher opens again, once (a list or the main menu; elsewhere, nothing). */
    fun reopenSwitcher() {
        if (!switcherReturn || switcherRestored) return   // a restored one opens once the first screen has loaded (init)
        dropSwitcherReturn()
        openSwitcher()
    }

    /** [switcherReturn] used or overruled: on disk too, so a menu started again later does not reopen the switcher. */
    fun dropSwitcherReturn() {
        switcherReturn = false
        switcherRestored = false
        returns?.clearSwitcherReturn()
    }

    fun openSwitcher() {
        if (host.kidsLocked()) return   // Kids Mode: no game while Locked
        switcherUnderGame = false   // a refused launch's flag never closes a switcher opened since
        val here = screen.value
        if (here !is Screen.Systems && here !is Screen.Games) return
        if (switcherJob?.isActive == true) return   // already loading
        val sel = selection.value
        switcherJob = scope.launch {
            val entries = switcherEntries()
            if (screen.value !== here) return@launch   // user navigated or launched meanwhile
            beforeSwitcher = here to sel
            host.navStep(NavMotion.SWITCHER_OPEN)
            screen.value = Screen.Switcher(entries, 0, list = settings.switcherLayout == SwitcherLayout.LIST)
        }
    }

    /** Each recent resolved against the index by [matches], with no SAF call. A recent not in the index is hidden, not dropped from recent.txt. */
    private suspend fun switcherEntries(): List<SwitcherEntry> =
        userData.readRecents().mapNotNull { recent ->
            val e = host.resolveOne(recent.path)?.takeUnless { it.system.android } ?: return@mapNotNull null   // never one (not recorded): kept out anyway
            val info = userData.resumeInfo(e.system.tag, e.game.fileName)
            if (info.canResume) SwitcherEntry(e.system, e.game, info) else null
        }.distinctBy { it.game.path }   // a hidden copy's line and its home game's are one game

    /**
     * A game was opened from the switcher, which stays up under it the whole game: the game opens over it and Save & Quit
     * cuts back to it, with no slide either way ([refreshSwitcher] on the way back).
     */
    private var switcherUnderGame = false

    /** Home's rows were rebuilt after a game the switcher stayed up over: its close swaps them in ([closeSwitcher]). */
    private var switcherRowsRebuilt = false

    override fun onRunnerCalled(fromSwitcher: Boolean) { switcherReturn = fromSwitcher }

    /**
     * The menu came to the foreground: true when it is back from a game opened over the switcher that is still up
     * ([backUnderGame] then follows). Read once: a later foreground is not one from that game.
     */
    fun onForeground(): Boolean {
        val back = switcherUnderGame && screen.value is Screen.Switcher
        switcherUnderGame = false
        return back
    }

    /**
     * Back from the game the switcher stayed up over: its return is used up, Home's rows are rebuilt behind it (swapped
     * in at its close), and its cards read again in place.
     */
    fun backUnderGame() {
        dropSwitcherReturn()
        switcherRowsRebuilt = true
        scope.launch { host.rebuildMainRows() }
        refreshSwitcher()
    }

    /**
     * A from the switcher: the game opens over the switcher, which stays ([switcherUnderGame]). A launch that never reaches the runner
     * (the game gone, a transfer running: [launch]'s refusals) closes it as before, so B and the error land on its page.
     */
    private fun launchFromSwitcher(entry: Listed, resumeSlot: Int?) {
        switcherUnderGame = true
        val job = host.launch(entry, resumeSlot, fromSwitcher = true) ?: run { closeSwitcher(); return }
        scope.launch {
            job.join()
            if (switcherUnderGame && !switcherReturn) closeSwitcher()   // refused: [switcherReturn] is set only at the runner's call
        }
    }

    /**
     * The switcher back on screen after its game: its cards read again (the game just played first, the latest recent),
     * in place; none left resumable, it closes.
     */
    private fun refreshSwitcher() {
        val here = screen.value as? Screen.Switcher ?: return
        switcherJob?.cancel()
        switcherJob = scope.launch {
            val entries = switcherEntries()
            if (screen.value !== here) return@launch   // the user moved on meanwhile
            if (entries.isEmpty()) closeSwitcher(slide = false)
            else screen.value = here.copy(entries = entries, index = 0, confirmRemove = false)
        }
    }

    /** Back to the page the switcher opened over; [slide]: the switcher's slide down (none: an instant swap). */
    fun closeSwitcher(slide: Boolean = true) {
        val (back, backSelection) = beforeSwitcher ?: host.menuWithItsRow()
        beforeSwitcher = null
        switcherUnderGame = false
        if (slide) host.navStep(NavMotion.SWITCHER_CLOSE)
        screen.value = back
        host.publishHomeView()   // the restored Home rows' own view, not one built while the switcher was up
        selection.value = backSelection
        // After a game the switcher stayed up over, the tabs were rebuilt (Continue's new resume frame): those, not the
        // snapshot. Otherwise the snapshot stays, with its own Home view ([publishHomeView] above).
        if (switcherRowsRebuilt) { switcherRowsRebuilt = false; host.swapMainRows() }
        host.refreshResume()
        // A Y Remove changed recent.txt: once written, rebuild the tabs, so Home's Continue moves past the game (the
        // snapshot above still shows it); the highlight stays on its row where that row is still there.
        switcherRemoval?.let { removal ->
            switcherRemoval = null
            scope.launch { removal.join(); host.refreshMainRows() }
        }
    }

    fun onSwitcherButton(s: Screen.Switcher, button: NxButton, repeat: Boolean = false) {
        if (s.confirmRemove) {
            when (button) {
                NxButton.A -> removeFromSwitcher(s)
                NxButton.B, NxButton.SELECT -> screen.value = s.copy(confirmRemove = false)
                else -> Unit
            }
            return
        }
        // List: a game list's UP/DOWN, a fresh press wrapping past an end (a held one stops there); LEFT/RIGHT do nothing.
        val at = ListSelection(s.index, s.entries.size)
        when (button) {
            NxButton.UP -> if (s.list) screen.value = s.copy(index = at.up(wrap = !repeat).index)
            NxButton.DOWN -> if (s.list) screen.value = s.copy(index = at.down(wrap = !repeat).index)
            NxButton.LEFT -> if (!s.list) screen.value = s.copy(index = at.up(wrap = false).index)
            NxButton.RIGHT -> if (!s.list) screen.value = s.copy(index = at.down(wrap = false).index)
            // The switcher stays up while the game opens over it (no slide back to the menu first) and through the game.
            NxButton.A -> s.entries.getOrNull(s.index)?.let { e -> launchFromSwitcher(Listed(e.system, e.game), e.resume.slot) }
            NxButton.Y -> if (s.entries.getOrNull(s.index) != null) screen.value = s.copy(confirmRemove = true)
            NxButton.B, NxButton.SELECT -> closeSwitcher()
            else -> Unit
        }
    }

    /** A tap on the switcher List's row [index]: the switcher's own index (the menu's selection under it stays as it was). */
    fun selectSwitcherRow(index: Int) {
        val s = screen.value as? Screen.Switcher ?: return
        if (s.list && !s.confirmRemove && index in s.entries.indices) screen.value = s.copy(index = index)
    }

    private fun removeFromSwitcher(s: Screen.Switcher) {
        val e = s.entries.getOrNull(s.index) ?: run { screen.value = s.copy(confirmRemove = false); return }
        val remaining = s.entries - e
        screen.value = s.copy(entries = remaining, index = s.index.coerceAtMost((remaining.size - 1).coerceAtLeast(0)), confirmRemove = false)
        val path = e.game.path
        val previous = switcherRemoval
        switcherRemoval = scope.launch { previous?.join(); host.recentsLock.withLock { userData.writeRecents(Recents.remove(userData.readRecents(), path)) } }
    }

    /**
     * A game resumed from the switcher, launched by an earlier menu process: its end reopens the switcher here too,
     * once this menu's first screen has loaded (a list or the main menu; anywhere else it is dropped).
     */
    fun restoreReturn() {
        if (returns?.switcherReturn() == true) {
            switcherReturn = true
            switcherRestored = true
            scope.launch {
                val first = screen.first { it !is Screen.Loading }
                if (!switcherRestored) return@launch
                switcherRestored = false
                if ((first is Screen.Systems && first.menu == null) || first is Screen.Games) reopenSwitcher() else dropSwitcherReturn()
            }
        }
    }

    override fun cancelLoad() { switcherJob?.cancel() }

    /** The page under the switcher was a hidden tab pushed over the menu, now dropped: the snapshot goes with it. */
    fun dropPushedSnapshot() {
        if ((beforeSwitcher?.first as? Screen.Systems)?.pushed == true) beforeSwitcher = null
    }
}
