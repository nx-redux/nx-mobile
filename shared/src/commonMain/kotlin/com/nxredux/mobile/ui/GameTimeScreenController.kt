package com.nxredux.mobile.ui

import com.nxredux.mobile.tryOr
import com.nxredux.mobile.gametime.GameTimeData
import com.nxredux.mobile.gametime.GameTotal
import com.nxredux.mobile.ui.nx.ListSelection
import com.nxredux.mobile.ui.nx.NxButton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

data class GameTimeRow(val total: GameTotal, val art: String?)

enum class GameTimeAction(val label: String) { MERGE("Merge into…"), UNMERGE("Unmerge…"), DELETE("Delete") }

sealed interface GameTimeModal {
    data class Options(val actions: List<GameTimeAction>, val index: Int = 0) : GameTimeModal
    data class PickMerge(val candidates: List<GameTotal>, val index: Int = 0) : GameTimeModal
    data class PickName(val into: GameTotal, val names: List<String>, val index: Int = 0) : GameTimeModal
    data class PickUnmerge(val members: List<GameTotal>, val index: Int = 0) : GameTimeModal
    data object ConfirmDelete : GameTimeModal
}

/** The Game Tracker list beside a game's sessions in the column browser ([GameTimeScreenController.parentOf]), and the row that opened them. */
data class GameTimeParent(val screen: Screen.GameTime, val row: Int)

/**
 * Tools → Game Time: the tracker list and its merge / unmerge / delete modals, and a game's sessions
 * ([Screen.GameTimeSessions]; X / SELECT / Y open its Merge / Unmerge / Delete), opened from its row with A or a tap.
 */
internal class GameTimeScreenController(
    private val scope: CoroutineScope,
    private val screen: MutableStateFlow<Screen>,
    private val selection: MutableStateFlow<ListSelection>,
    private val error: MutableStateFlow<String?>,
    private val data: GameTimeData,
    private val loader: ScreenLoader = ScreenLoader(scope),
    private val artFor: suspend () -> (String) -> String?,
) {
    /** The page opens empty at once; the first read runs under the loading overlay, and B there ([onCancel]) leaves. */
    fun open(onCancel: () -> Unit = {}) {
        screen.value = Screen.GameTime(emptyList(), 0L, loading = true)
        selection.value = ListSelection(0, 0)
        loader.start(onCancel) { load() }
    }

    private fun reload() { scope.launch { load() } }

    private var parent: Screen.GameTime? = null          // the list the sessions page was opened from (the left pane beside it)
    private var parentRow = ListSelection(0, 0)          // its row, restored by B

    /**
     * Opens the sessions of [list]'s game on [row]. The page it is opened from stays until they have loaded (the list, or
     * another game's sessions beside it in the column browser: the list never shows on the right in between).
     */
    private fun openSessions(list: Screen.GameTime, row: ListSelection) {
        val game = list.rows.getOrNull(row.index)?.total ?: return
        val here = screen.value
        loader.start {
            val sessions = tryOr({ error.value = "Couldn't read game time"; return@start }) { data.sessions(game.gameId) }
            if (screen.value !== here) return@start   // the user moved on meanwhile
            parent = list; parentRow = row
            screen.value = Screen.GameTimeSessions(game, sessions, canMerge = list.rows.size >= 2)
            selection.value = ListSelection(0, sessions.sessions.size)
        }
    }

    /** Back from a game's sessions to the list, on the row that opened them. */
    fun leaveSessions() {
        val p = parent ?: run { open(); return }
        screen.value = p
        selection.value = parentRow.clampedTo(p.rows.size)
    }

    /** The list and its highlighted row the column browser's left pane shows beside [s] (a game's sessions); null for any other screen. */
    fun parentOf(s: Screen): GameTimeParent? =
        if (s is Screen.GameTimeSessions) parent?.let { GameTimeParent(it, parentRow.index) } else null

    /**
     * A tap on row [index] of the dimmed list beside a game's sessions: that game's sessions replace them, as B then A
     * would. The game already open, or a load in flight, does nothing: a one-tap list ([com.nxredux.mobile.ui.nx.nxTap])
     * calls this twice, and the second call finds the game open (or loading).
     */
    fun parentTap(index: Int) {
        if (screen.value !is Screen.GameTimeSessions || loader.active) return
        val p = parent ?: return
        if (index == parentRow.index || index !in p.rows.indices) return
        openSessions(p, ListSelection(index, p.rows.size))
    }

    private suspend fun load() {
        val (rows, total) = tryOr({ error.value = "Couldn't read game time"; emptyList<GameTimeRow>() to 0L }) {
            val art = artFor()
            val totals = data.totals()
            totals.map { GameTimeRow(it, art(it.romPath)) } to totals.sumOf { it.totalMs }
        }
        if (screen.value !is Screen.GameTime) return
        screen.value = Screen.GameTime(rows, total)
        selection.value = selection.value.clampedTo(rows.size)
    }

    private fun current(): Screen.GameTime? = screen.value as? Screen.GameTime
    private fun row(s: Screen.GameTime): GameTotal? = s.rows.getOrNull(selection.value.index)?.total
    private fun setModal(m: GameTimeModal?) { current()?.let { screen.value = it.copy(modal = m) } }

    fun back(s: Screen.GameTime) { if (s.modal != null) onButton(s, NxButton.B) }

    fun selectModal(index: Int) {
        when (val m = current()?.modal) {
            is GameTimeModal.Options -> setModal(m.copy(index = index.coerceIn(0, m.actions.lastIndex)))
            is GameTimeModal.PickMerge -> setModal(m.copy(index = index.coerceIn(0, m.candidates.lastIndex)))
            is GameTimeModal.PickName -> setModal(m.copy(index = index.coerceIn(0, m.names.lastIndex)))
            is GameTimeModal.PickUnmerge -> setModal(m.copy(index = index.coerceIn(0, m.members.lastIndex)))
            else -> Unit
        }
    }

    fun onButton(s: Screen, button: NxButton): Boolean {
        if (s is Screen.GameTimeSessions) {   // read-only rows: move, B back; X / SELECT / Y the game's actions; A and MENU nothing
            when (button) {
                NxButton.UP -> selection.value = selection.value.up()
                NxButton.DOWN -> selection.value = selection.value.down()
                NxButton.B -> leaveSessions()
                NxButton.X -> if (s.canMerge) fromSessions { act { setModal(GameTimeModal.PickMerge(data.mergeCandidates(it.gameId))) } }
                NxButton.SELECT -> if (s.game.members > 0) fromSessions { act { setModal(GameTimeModal.PickUnmerge(data.members(it.gameId).drop(1))) } }
                NxButton.Y -> fromSessions { setModal(GameTimeModal.ConfirmDelete) }
                else -> Unit
            }
            return true
        }
        if (s !is Screen.GameTime) return false
        val modal = s.modal
        if (modal != null && acting) return true   // a merge / unmerge / delete in flight: its modal takes no input
        if (modal == null) {
            when (button) {
                NxButton.UP -> selection.value = selection.value.up()
                NxButton.DOWN -> selection.value = selection.value.down()
                NxButton.A -> openSessions(s, selection.value)
                NxButton.MENU -> row(s)?.let { r -> fromSessionsModal = false; setModal(GameTimeModal.Options(optionsFor(s, r))) }
                NxButton.B -> return false
                else -> Unit
            }
            return true
        }
        val r = row(s) ?: run { setModal(null); return true }
        fun step(index: Int, size: Int) = when (button) {
            NxButton.UP -> (index - 1).coerceAtLeast(0)
            NxButton.DOWN -> (index + 1).coerceAtMost(size - 1)
            else -> index
        }
        when (modal) {
            is GameTimeModal.Options -> when (button) {
                NxButton.UP, NxButton.DOWN -> setModal(modal.copy(index = step(modal.index, modal.actions.size)))
                NxButton.B -> setModal(null)
                NxButton.A -> when (modal.actions[modal.index]) {
                    GameTimeAction.MERGE -> act { setModal(GameTimeModal.PickMerge(data.mergeCandidates(r.gameId))) }
                    GameTimeAction.UNMERGE -> act { setModal(GameTimeModal.PickUnmerge(data.members(r.gameId).drop(1))) }
                    GameTimeAction.DELETE -> setModal(GameTimeModal.ConfirmDelete)
                }
                else -> Unit
            }
            is GameTimeModal.PickMerge -> when (button) {
                NxButton.UP, NxButton.DOWN -> setModal(modal.copy(index = step(modal.index, modal.candidates.size)))
                NxButton.B -> cancel(s) { setModal(GameTimeModal.Options(optionsFor(s, r))) }
                NxButton.A -> modal.candidates.getOrNull(modal.index)?.let { into ->
                    val names = listOf(r.name, into.name).distinct()
                    if (names.size == 1) act { data.merge(r.gameId, into.gameId, names[0]); setModal(null); reload() }
                    else setModal(GameTimeModal.PickName(into, names))
                }
                else -> Unit
            }
            is GameTimeModal.PickName -> when (button) {
                NxButton.UP, NxButton.DOWN -> setModal(modal.copy(index = step(modal.index, modal.names.size)))
                NxButton.B -> act { setModal(GameTimeModal.PickMerge(data.mergeCandidates(r.gameId))) }
                NxButton.A -> act { data.merge(r.gameId, modal.into.gameId, modal.names[modal.index]); setModal(null); reload() }
                else -> Unit
            }
            is GameTimeModal.PickUnmerge -> when (button) {
                NxButton.UP, NxButton.DOWN -> setModal(modal.copy(index = step(modal.index, modal.members.size)))
                NxButton.B -> cancel(s) { setModal(GameTimeModal.Options(optionsFor(s, r))) }
                NxButton.A -> modal.members.getOrNull(modal.index)?.let { m -> act { data.unmerge(m.gameId); setModal(null); reload() } }
                else -> Unit
            }
            GameTimeModal.ConfirmDelete -> when (button) {
                NxButton.B -> cancel(s) { setModal(null) }
                NxButton.A -> act { data.delete(r.gameId); setModal(null); reload() }
                else -> Unit
            }
        }
        return true
    }

    /** The game's actions opened from its sessions page (touch has no long press there): B leaves the modal for those sessions again. */
    private var fromSessionsModal = false

    /** Back on the list at the sessions' row, then [open] its modal (on the list, as MENU's would be), B returning to the sessions. */
    private fun fromSessions(open: (GameTotal) -> Unit) {
        val p = parent ?: return
        leaveSessions()
        val r = row(p) ?: return
        fromSessionsModal = true
        open(r)
    }

    /** B on a modal: back to the sessions it was opened from ([fromSessions]), else [otherwise]. */
    private fun cancel(s: Screen.GameTime, otherwise: () -> Unit) {
        if (!fromSessionsModal) return otherwise()
        fromSessionsModal = false
        val list = s.copy(modal = null)
        screen.value = list
        openSessions(list, selection.value)
    }

    private fun optionsFor(s: Screen.GameTime, r: GameTotal) = buildList {
        if (s.rows.size >= 2) add(GameTimeAction.MERGE)
        if (r.members > 0) add(GameTimeAction.UNMERGE)
        add(GameTimeAction.DELETE)
    }

    private var acting = false   // an [act] in flight: a second A (or B) on its modal waits for it, so nothing runs twice

    private fun act(block: suspend () -> Unit) {
        if (acting) return
        acting = true
        scope.launch {
            try { block() }
            catch (e: CancellationException) { throw e }   // the page left: not a failure
            catch (e: Throwable) { error.value = "Couldn't change game time"; setModal(null) }
            finally { acting = false }
        }
    }
}
