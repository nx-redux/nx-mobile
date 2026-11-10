package com.nxredux.mobile.achievements

import com.nxredux.mobile.library.IndexedSystem
import com.nxredux.mobile.library.LibraryIndex
import com.nxredux.mobile.storage.GamePath
import com.nxredux.mobile.storage.GameRef

/** The Tools browser's data: cached games joined with the index, and a game's achievements with their offline-aware state. */
object RaLibraryView {
    suspend fun games(store: RaStore, journal: RaJournal, confirmed: RaJournal, index: LibraryIndex?, user: String): List<RaGameView> {
        val pendingByHash = journal.entries(user).groupBy { it.m }
        val confirmedByHash = confirmed.entries(user).groupBy { it.m }
        val indexed = HashMap<Triple<String, String, String>, Indexed>()
        // A game's art: its screenshot (not a SteamGridDB hero's), else its 3D box, else its 2D cover.
        index?.systems?.forEach { s ->
            s.games.forEach { g -> indexed[Triple(g.source, g.dir, g.file)] = Indexed(g.screenshot?.takeIf { !g.heroShot } ?: g.boxart ?: g.box2d, s.takeUnless { it.unassigned }) }
        }
        val sessions = HashMap<Int, Set<Int>>()   // a multi-disc set's hashes share one game id and session
        val rows = store.list(RaPaths.GAMES).filter(RaPaths::isHash).mapNotNull { hash ->
            val sets = RaSetsCache.summary(store, hash) ?: return@mapNotNull null
            if (sets.core.isEmpty()) return@mapNotNull null   // nothing but the Unknown-Emulator warning (or no set at all)
            val pointer = RaGamePointers.read(store, hash)
            val core = sets.core
            val sessionIds = sessions[sets.gameId] ?: (readSession(store, sets.gameId)?.let(::sessionUnlocks)?.keys.orEmpty()).also { sessions[sets.gameId] = it }
            val unlockedIds = sessionIds + confirmedByHash[hash]?.map { it.a }.orEmpty()
            val pending = pendingByHash[hash]?.map { it.a }.orEmpty().toSet() - unlockedIds
            val ids = core.map { it.id }.toSet()
            val title = pointer?.title?.ifEmpty { null } ?: sets.title
            val hit = pointer?.let { indexed[Triple(it.source, it.dir, it.file)] }
            val (console, tag) = RaConsoleGroups.consoleOf(hit?.system?.tag, hit?.system?.displayName, pointer?.tag, sets.consoleId)
            RaGameView(hash, sets.gameId, title, hit?.art, (unlockedIds + pending).count { it in ids }, core.size, pending.count { it in ids }, console, tag) to (pointer?.file ?: "")
        }
        // Multi-disc sets share a game id: keep one row, preferring the m3u.
        return rows.groupBy { it.first.gameId }.values.map { group -> (group.firstOrNull { it.second.endsWith(".m3u", ignoreCase = true) } ?: group.first()).first }
            .sortedBy { it.title.lowercase() }
    }

    /** A library game's art and its console's system (null in Unassigned: no console to list it under). */
    private class Indexed(val art: String?, val system: IndexedSystem?)

    suspend fun achievements(store: RaStore, journal: RaJournal, confirmed: RaJournal, hash: String, user: String): List<RaAchievement> =
        achievementsOf(store, hash, journal.entries(user).filter { it.m == hash }, confirmed.entries(user).filter { it.m == hash })

    /** [hash]'s achievements with their state, from its [pending] and [confirmed] journal entries (already read). */
    private suspend fun achievementsOf(store: RaStore, hash: String, pending: List<RaUnlock>, confirmed: List<RaUnlock>): List<RaAchievement> {
        val sets = store.readJson(RaPaths.sets(hash), SetsBody.serializer()) ?: return emptyList()
        val states = States.of(store, sets.gameId, pending, confirmed)
        return sets.coreAchievements.map { a ->
            val (state, at) = states.of(a.ID)
            RaAchievement(a.ID, a.Title, a.Description, a.Points, a.BadgeName, state, at, null, 0f, a.Rarity, raType(a.Type))
        }
    }

    /** A game's unlocked (session and confirmed, confirmed times winning) and pending ids, with their unix times. */
    private class States(private val unlocked: Map<Int, Long>, private val pending: Map<Int, Long>) {
        fun of(id: Int): Pair<RaAchievementState, Long?> = when {
            id in unlocked -> RaAchievementState.UNLOCKED to unlocked[id]?.takeIf { it > 0 }   // 0: prefetched, time unknown
            id in pending -> RaAchievementState.PENDING to pending[id]
            else -> RaAchievementState.LOCKED to null
        }
        companion object {
            suspend fun of(store: RaStore, gameId: Int, pending: List<RaUnlock>, confirmed: List<RaUnlock>) = States(
                readSession(store, gameId)?.let(::sessionUnlocks).orEmpty() + confirmed.associate { it.a to it.t }, pending.associate { it.a to it.t },
            )
        }
    }

    private class Row(val id: Int, val title: String, val state: RaAchievementState, val at: Long?)

    /** [hash]'s core achievements with their state, from its [RaSetsSummary] (not the full sets.json). */
    private suspend fun rowsOf(store: RaStore, hash: String, pending: List<RaUnlock>, confirmed: List<RaUnlock>): List<Row> {
        val sets = RaSetsCache.summary(store, hash) ?: return emptyList()
        val states = States.of(store, sets.gameId, pending, confirmed)
        return sets.core.map { a -> states.of(a.id).let { (state, at) -> Row(a.id, a.title, state, at) } }
    }

    /** Every cached game with a library pointer: its hash by its library path (GamePath), for a game list's panel. */
    suspend fun hashesByPath(store: RaStore): Map<String, String> =
        store.list(RaPaths.GAMES).filter(RaPaths::isHash).mapNotNull { hash ->
            RaGamePointers.read(store, hash)?.let { p -> GamePath.of(GameRef(p.source, p.dir, p.file)) to hash }
        }.toMap()

    /** Every cached game with a library pointer, by its library path (GamePath): progress and the next locked achievement. */
    suspend fun progressByPath(store: RaStore, journal: RaJournal, confirmed: RaJournal, user: String): Map<String, RaProgress> {
        // Each journal read once and grouped by hash (as [games] does), not re-read per cached game.
        val pendingByHash = journal.entries(user).groupBy { it.m }
        val confirmedByHash = confirmed.entries(user).groupBy { it.m }
        return store.list(RaPaths.GAMES).filter(RaPaths::isHash).mapNotNull { hash ->
            val pointer = RaGamePointers.read(store, hash) ?: return@mapNotNull null
            val list = rowsOf(store, hash, pendingByHash[hash].orEmpty(), confirmedByHash[hash].orEmpty())
            if (list.isEmpty()) return@mapNotNull null
            val unlocked = list.count { it.state != RaAchievementState.LOCKED }
            GamePath.of(GameRef(pointer.source, pointer.dir, pointer.file)) to RaProgress(unlocked, list.size, list.firstOrNull { it.state == RaAchievementState.LOCKED }?.title)
        }.toMap()
    }

    /**
     * Unlocks (confirmed or pending) at or after [sinceSec], over the cached games whose pointer's library path is in [paths].
     * Each achievement id counts once: [rowsOf] has already merged the session and both journals, and multi-disc
     * sibling hashes share one game id, session and set (RA achievement ids are global).
     */
    suspend fun unlocksSince(store: RaStore, journal: RaJournal, confirmed: RaJournal, user: String, sinceSec: Long, paths: Set<String>): Int {
        if (paths.isEmpty()) return 0
        val pendingByHash = journal.entries(user).groupBy { it.m }
        val confirmedByHash = confirmed.entries(user).groupBy { it.m }
        val ids = HashSet<Int>()
        for (hash in store.list(RaPaths.GAMES).filter(RaPaths::isHash)) {
            val pointer = RaGamePointers.read(store, hash) ?: continue
            if (GamePath.of(GameRef(pointer.source, pointer.dir, pointer.file)) !in paths) continue
            rowsOf(store, hash, pendingByHash[hash].orEmpty(), confirmedByHash[hash].orEmpty())
                .filter { r -> r.at?.let { it >= sinceSec } == true }   // locked ones have no time
                .mapTo(ids) { it.id }
        }
        return ids.size
    }

    fun raType(server: String?): RaType? = when (server?.lowercase()) { "missable" -> RaType.MISSABLE; "progression" -> RaType.PROGRESSION; "win_condition", "win" -> RaType.WIN; else -> null }

    /**
     * A cached session's unlocks, id to unix time: softcore and hardcore (startsession splits them; an unlock made on
     * another device in hardcore counts here too). Time 0 is a prefetched `unlocks` reply's: unlocked, when unknown.
     */
    private fun sessionUnlocks(session: SessionBody): Map<Int, Long> =
        (session.HardcoreUnlocks + session.Unlocks).associate { it.ID to it.When }

    private suspend fun readSession(store: RaStore, gameId: Int): SessionBody? =
        store.readJson(RaPaths.session(gameId), SessionBody.serializer())
}
