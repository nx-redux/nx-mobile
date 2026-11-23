package com.nxredux.mobile.ui

import com.nxredux.mobile.library.GameBadges
import com.nxredux.mobile.library.GameEntry
import com.nxredux.mobile.library.path
import com.nxredux.mobile.storage.UserDataStore
import com.nxredux.mobile.tryOrAny
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

/** The Badge row's header on a game's Game Settings page: it is the library's setting, not the emulator's. */
internal const val GAME_BADGE_SECTION = "Library"

/** A badge choice's label on the Badge row: Auto for null, else the stored word. */
internal fun gameBadgeLabel(choice: String?): String = choice ?: "Auto"

/**
 * Game Settings' Badge row: the game's own choice ([GameBadges.CHOICES]), written to `badges.txt` ([UserDataStore.writeBadges])
 * and then into the index ([saved]). A step shows at once; the writes follow in order, each from a fresh read, so
 * quick steps end on the last one. A failed write puts the row back ([failed]).
 */
internal class GameBadgeEditor(
    private val scope: CoroutineScope,
    private val userData: UserDataStore,
    private val work: CoroutineContext,
    private val saved: suspend (GameEntry, String?) -> Unit,
    private val failed: () -> Unit,
) {
    private val lock = Mutex()
    private val edits = HashMap<String, String?>()   // by game path: picked here, newer than the entry the page holds
    private val written = HashMap<String, String?>()   // by game path: the last choice that reached the file

    /** [game]'s choice as it now stands. */
    fun choiceOf(game: GameEntry): String? = if (game.path in edits) edits[game.path] else GameBadges.choiceOf(game.badgeChoice)

    /** The Badge row for [game] and its choices, as the page shows them. */
    fun section(game: GameEntry): EmulatorAppSection {
        val choice = choiceOf(game)
        val row = SettingsRow(SettingsKind.GAME_BADGE, "Badge", gameBadgeLabel(choice))
        return EmulatorAppSection(GAME_BADGE_SECTION, listOf(EmulatorRow.App(row, SettingChoices(GameBadges.CHOICES.map(::gameBadgeLabel), GameBadges.CHOICES.indexOf(choice)))))
    }

    /** LEFT / RIGHT (A: forward): the choice [delta] places on, wrapping. */
    fun step(game: GameEntry, delta: Int) {
        val choices = GameBadges.CHOICES
        set(game, choices[(choices.indexOf(choiceOf(game)) + delta).mod(choices.size)])
    }

    /** A touch pick of choice [index]. */
    fun choose(game: GameEntry, index: Int) {
        // Auto is the null choice, so the index is checked rather than the value.
        if (index in GameBadges.CHOICES.indices) set(game, GameBadges.CHOICES[index])
    }

    private fun set(game: GameEntry, choice: String?) {
        val path = game.path
        if (choiceOf(game) == choice) return
        edits[path] = choice
        scope.launch {
            lock.withLock {
                val want = edits[path]
                val ok = tryOrAny({ false }) {
                    withContext(work) { userData.writeBadges(GameBadges.with(userData.readBadges(), path, want)) }
                }
                if (!ok) {
                    if (edits[path] == want) { if (path in written) edits[path] = written[path] else edits.remove(path) }
                    failed()
                    return@withLock
                }
                written[path] = want
                saved(game, want)
            }
        }
    }
}
