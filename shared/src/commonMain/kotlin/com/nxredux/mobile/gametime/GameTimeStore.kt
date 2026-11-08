package com.nxredux.mobile.gametime

import com.nxredux.mobile.gametime.db.GameTimeDb
import com.nxredux.mobile.gametime.db.WindowGroupTotals
import com.nxredux.mobile.library.Names

/**
 * Play time in SQLite. Blocking: call it off the main thread. One device row per install;
 * sessions are never rewritten after they close, merges live in `game_merge`.
 */
class GameTimeStore(
    private val db: GameTimeDb,
    private val deviceId: String,
    private val deviceName: String,
    private val now: () -> Long,
    private val newId: () -> String,
) {
    private val q get() = db.gameTimeQueries

    /**
     * Writes this device's row (name refreshed), then closes the sessions a killed process left open. Every process opens
     * the store, so only sessions untouched for [STALE_MS] count as left: a game playing in another process keeps its own.
     */
    fun prepare() {
        db.transaction {
            q.insertDevice(deviceId, deviceName)
            q.renameDevice(deviceName, deviceId)
        }
        closeStale(seenBefore = now() - STALE_MS)
    }

    /**
     * Opens a session on [romPath]'s game (created on first play) and returns its uuid. Closes any open one first.
     * [play]: the uuid [start] returned for the launch this session continues (a return from the background); null, a
     * new launch (the session is its first slice).
     */
    fun start(romPath: String, tag: String, name: String, play: String? = null): String {
        closeStale()
        val id = newId()
        db.transaction {
            val gameId = q.gameByPath(romPath).executeAsOneOrNull()?.id?.also { q.refreshGame(name, tag, it) } ?: run {
                q.insertGame(romPath, name, tag)
                q.lastInsertRowId().executeAsOne()
            }
            val t = now()
            q.insertSession(id, gameId, deviceId, tag, t, t, play ?: id)
        }
        return id
    }

    /**
     * Moves games to new rom paths ([map]: old to new) in one transaction; sessions and merges follow, as they point at
     * the game id. A path with no game is skipped, and a game whose new path already has one is left as it is (no merge).
     */
    fun renamePaths(map: Map<String, String>) {
        db.transaction { map.forEach { (from, to) -> q.renamePath(to = to, from = from) } }
    }

    /** Every game's rom path, played or not (merged members too). */
    fun romPaths(): List<String> = q.gameGroups().executeAsList().map { it.rom_path }

    fun touch(uuid: String) {
        q.touchSession(now(), uuid)
    }

    fun end(uuid: String) {
        db.transaction {
            q.closeSession(now(), uuid)
            q.deleteImplausible(MIN_MS, MAX_MS)
        }
    }

    /**
     * Sessions of this device still open, last touched at or before [seenBefore], close at their last touch (launch or
     * last move to the background). [start] closes them all: only the game process records, one game at a time.
     */
    fun closeStale(seenBefore: Long = Long.MAX_VALUE) {
        db.transaction {
            q.closeStaleSessions(deviceId = deviceId, seenBefore = seenBefore)
            q.deleteImplausible(MIN_MS, MAX_MS)
        }
    }

    /** Per group: its time, plays (launches, however many returns from the background each had) and last play. */
    fun totals(): List<GameTotal> =
        q.groupTotals().executeAsList().map {
            GameTotal(it.group_id, it.name, it.tag, it.rom_path, it.total?.toLong() ?: 0L, it.plays, it.last_played ?: 0L, it.members.toInt())
        }.sortedByDescending { it.totalMs }

    /** Every recorded rom path to its group's [totals] row (a merged member maps to its group); unplayed games left out. */
    fun byPath(): Map<String, GameTotal> {
        val groups = totals().associateBy { it.gameId }
        return q.gameGroups().executeAsList().mapNotNull { g -> groups[g.group_id]?.let { g.rom_path to it } }.toMap()
    }

    /**
     * Play time overlapping [sinceMs, nowMs] on every device. An open session counts to its last touch, at most [MAX_MS]
     * after its start (the bound a closed session must pass to be kept). The top groups (most time in the window, a tie
     * to the most recent play; up to [TOP_GAMES]) carry the window's total, plays and last play.
     */
    fun window(sinceMs: Long, nowMs: Long): WindowTotals {
        val rows = q.windowGroupTotals(now = nowMs, since = sinceMs, maxOpen = MAX_MS).executeAsList().filter { (it.total ?: 0L) > 0L }
        val ranked = rows.sortedWith(compareByDescending<WindowGroupTotals> { it.total ?: 0L }.thenByDescending { it.last_played ?: 0L })
        val tops = ranked.take(TOP_GAMES).map { r ->
            GameTotal(r.group_id, r.name, r.tag, r.rom_path, r.total ?: 0L, r.plays, r.last_played ?: 0L, r.members.toInt())
        }
        return WindowTotals(rows.sumOf { it.total ?: 0L }, tops.firstOrNull(), tops)
    }

    /**
     * The rom paths of every game in each group [window] counts (the same overlap rule, every device): a merge target and
     * all its members, so a played group's achievements are looked up under each of its games.
     */
    fun playedPaths(sinceMs: Long, nowMs: Long): Set<String> =
        q.windowGroupPaths(now = nowMs, since = sinceMs, maxOpen = MAX_MS).executeAsList().toSet()

    fun grandTotal(): Long = totals().sumOf { it.totalMs }

    /**
     * Every play of [target]'s group (the target and the games merged into it) on every device, newest first: the plays
     * [totals] counts for it, each its closed slices together. An open slice (a game still running) is left out, as there.
     */
    fun sessions(target: Long): GameSessions = GameSessions(
        q.groupPlays(target).executeAsList().map {
            val start = it.first_start ?: 0L
            GameSession(it.play, it.game_id, it.device_id, it.device_name ?: it.device_id, start, it.last_end ?: start, it.played?.toLong() ?: 0L)
        },
    )

    /** [target] and the games merged into it, each with its own totals. */
    fun members(target: Long): List<GameTotal> {
        val (head, rest) = q.memberTotals(target).executeAsList()
            .map { GameTotal(it.id, it.name, it.tag, it.rom_path, it.total?.toLong() ?: 0L, it.plays, it.last_played ?: 0L) }
            .partition { it.gameId == target }
        return if (head.isEmpty()) emptyList() else head + rest.sortedByDescending { it.totalMs }
    }

    /** Counts [from]'s group under [into]'s, shown as [name]. Both must be group targets; sessions are not touched. */
    fun merge(from: Long, into: Long, name: String) {
        require(from != into) { "merge into itself" }
        require(q.mergeTarget(from).executeAsOneOrNull() == null && q.mergeTarget(into).executeAsOneOrNull() == null) { "not a group target" }
        db.transaction {
            q.repointMembers(into = into, from = from)
            q.insertMerge(from, into)
            q.setTitle(name, into)
            q.setTitle(null, from)
        }
    }

    /** [gameId] is its own group again; a target left with no members loses its chosen title. */
    fun unmerge(gameId: Long) {
        val target = q.mergeTarget(gameId).executeAsOneOrNull() ?: return
        db.transaction {
            q.deleteMerge(gameId)
            if (q.memberIds(target).executeAsList().isEmpty()) q.setTitle(null, target)
        }
    }

    /** Every other group, same base name (region/revision tags stripped) first, then by total. */
    fun mergeCandidates(target: Long): List<GameTotal> {
        val all = totals()
        val base = all.firstOrNull { it.gameId == target }?.let { Names.plainName(it.name).lowercase() }
        return all.filter { it.gameId != target }
            .sortedWith(compareByDescending<GameTotal> { Names.plainName(it.name).lowercase() == base }.thenByDescending { it.totalMs })
    }

    /** The whole group: every member's sessions, links and game rows (the rows others reference go first). */
    fun delete(target: Long) {
        db.transaction {
            val ids = listOf(target) + q.memberIds(target).executeAsList()
            ids.forEach { id ->
                q.deleteSessionsOfGame(id)
                q.deleteMerge(id)
            }
            ids.forEach { q.deleteGame(it) }
        }
    }

    companion object {
        const val MIN_MS = 1_000L
        const val MAX_MS = 86_400_000L
        /** How often a running game touches its open session. */
        const val TOUCH_MS = 60_000L
        /**
         * An open session untouched this long belongs to a dead process. Well above [TOUCH_MS], so one process opening the
         * database never closes a session a game in another process is still touching.
         */
        const val STALE_MS = 3 * TOUCH_MS
        /** How many top games [window] lists ([WindowTotals.tops]; Home's strip reads only the first, [WindowTotals.top], as Most played). */
        const val TOP_GAMES = 3
    }
}
