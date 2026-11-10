package com.nxredux.mobile.achievements

import com.nxredux.mobile.storage.PrivateFiles
import com.nxredux.mobile.tryOrNull
import com.nxredux.mobile.tryQuietly
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.coroutines.CoroutineContext

@Serializable
private data class SnapshotGame(
    val hash: String, val gameId: Int, val title: String, val art: String? = null, val unlocked: Int, val total: Int, val pending: Int,
    val console: String, val consoleTag: String? = null,
)

@Serializable
private data class SnapshotFile(val version: Int, val user: String, val games: List<SnapshotGame> = emptyList())

/** JSON for [RaGamesCache.FILE]. Another [VERSION], another user, or anything that does not decode is no snapshot. */
object RaGamesSnapshotCodec {
    const val VERSION = 1
    private val json = Json { ignoreUnknownKeys = true }

    fun encode(user: String, games: List<RaGameView>): String = json.encodeToString(
        SnapshotFile.serializer(),
        SnapshotFile(VERSION, user, games.map { SnapshotGame(it.hash, it.gameId, it.title, it.art, it.unlocked, it.total, it.pending, it.console, it.consoleTag) }),
    )

    fun decode(text: String?, user: String): List<RaGameView>? {
        if (text.isNullOrEmpty()) return null
        val file = tryOrNull { json.decodeFromString(SnapshotFile.serializer(), text) } ?: return null
        if (file.version != VERSION || file.user != user) return null
        return file.games.map { RaGameView(it.hash, it.gameId, it.title, it.art, it.unlocked, it.total, it.pending, it.console, it.consoleTag) }
    }
}

/**
 * The Achievements browser's last read games, per RetroAchievements user: in memory for the run and in the app-private
 * [files] for the next, so the browser opens without reading every cached game first. [generation] moves on every
 * [clear]: a read started before it must not be [put] back. Writes run in order on [io] and never throw.
 */
class RaGamesCache(private val files: PrivateFiles, private val scope: CoroutineScope, private val io: CoroutineContext) {
    private var user: String? = null
    private var games: List<RaGameView>? = null
    private var readFor: String? = null   // the user whose file was read this run: an absent snapshot is not read again
    private val writes = Mutex()
    var generation = 0
        private set

    /** [user]'s games held in memory; null when none are (not read yet, cleared, or another user's). */
    fun peek(user: String): List<RaGameView>? = games?.takeIf { this.user == user }

    /** [peek], else [user]'s snapshot file, read once a run. */
    suspend fun load(user: String): List<RaGameView>? {
        peek(user)?.let { return it }
        if (readFor == user) return null
        val gen = generation
        val read = withContext(io) { RaGamesSnapshotCodec.decode(tryOrNull { files.read(FILE) }, user) }
        if (gen != generation) return null
        readFor = user
        peek(user)?.let { return it }   // a fresh read landed while the file was read
        if (read != null) { this.user = user; games = read }
        return read
    }

    /** Keeps [games] as [user]'s, unless cleared since [generation] [gen]; saved to the file for a named user. */
    fun put(user: String, games: List<RaGameView>, gen: Int = generation) {
        if (gen != generation) return
        this.user = user; this.games = games; readFor = user
        if (user.isNotBlank()) persist(gen) { RaGamesSnapshotCodec.encode(user, games) }
    }

    /** One game's row as a page read it, in the held list, so the next open shows it. */
    fun patch(user: String, game: RaGameView) {
        val held = peek(user) ?: return
        if (held.none { it.hash == game.hash }) return
        put(user, held.map { if (it.hash == game.hash) game else it })
    }

    /** Forgets every user's games, in memory and on file (sign-out, reset, erase). */
    fun clear() {
        generation++
        user = null; games = null; readFor = null
        persist(generation) { "" }
    }

    private fun persist(gen: Int, text: () -> String) {
        scope.launch {
            writes.withLock {
                if (gen != generation) return@withLock
                tryQuietly { withContext(io) { files.write(FILE, text()) } }
            }
        }
    }

    companion object {
        const val FILE = "ra-games.json"
    }
}
