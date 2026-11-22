package com.nxredux.mobile.achievements

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

/** What a sync is doing: the replay, then the refresh's account check, cache read, per-console progress and stale sessions. */
enum class RaSyncStep { STARTING, SUBMITTING, ACCOUNT, READING, PROGRESS, SESSIONS }

/** [done] of [total] items in [step] (0 of 0: no count); [current] names the item (a game title, a console's game count). */
data class RaSyncProgress(val step: RaSyncStep, val done: Int = 0, val total: Int = 0, val current: String? = null)

/** Why a sync stopped early: [AUTH] the saved login was refused (sign in again), [BUSY] throttled or down (try later), [REFUSED] account or client refused. */
enum class RaSyncStop { AUTH, BUSY, REFUSED }

/**
 * [rejected] journal entries the server refused for good went to the dead letters ([RaPaths.REJECTED]); [remaining] are
 * still journaled after the run. [stop] says why a [failed] run ended early ([error]: the server's words for [RaSyncStop.REFUSED]).
 */
data class RaSyncResult(
    val submitted: Int, val refreshedGames: Int, val points: Int?, val failed: Boolean,
    val rejected: Int = 0, val remaining: Int = 0, val stop: RaSyncStop? = null, val error: String? = null,
)

/** [RaPaths.FULL_SYNC]: [user]'s last complete full sync at [at] (ms), and the cloud totals (hardcore [score], [softcore]) it reconciled. */
@Serializable
data class RaFullSync(val user: String, val at: Long, val score: Int, val softcore: Int)

/** How old a full sync may be before the next sync is full again, whatever the totals say. */
const val RA_FULL_SYNC_MAX_AGE_MS = 7 * 24 * 3600 * 1000L

/**
 * Phase 1 replays the journal (signed awards); phase 2 refreshes login, per-console progress and stale sessions.
 * Phase 2 stops after login when nothing changed in the cloud: its totals equal the last full sync's plus the
 * softcore points this replay added, that full sync is at most a week old and there is a cache to keep.
 * Requests are paced and a busy server ends the run ([RaPacer]); a refused login ends it with the journal kept; an
 * award the server refuses for itself (not the account) moves to the dead letters so it is not resent forever.
 */
class RaSync(
    private val store: RaStore, private val http: RaHttp, private val signer: RaSigner,
    private val journal: RaJournal, private val confirmed: RaJournal,
    private val rejected: RaJournal = RaJournal(store, RaPaths.REJECTED),
    private val delay: suspend (Long) -> Unit = { kotlinx.coroutines.delay(it) },
    private val now: () -> Long,
) {
    suspend fun run(user: String, token: String, onProgress: (RaSyncProgress) -> Unit): RaSyncResult {
        val pacer = RaPacer(http, delay)
        var submitted = 0
        var dropped = 0
        var points: Int? = null
        val pending = journal.entries(user)
        val done = ArrayList<RaUnlock>()   // confirmed or dead-lettered: off the journal at the end
        val sets = HashMap<String, SetsBody?>()
        // Softcore points the server added in this replay (journal awards are softcore); null once one is unknown.
        var sentPoints: Int? = 0
        try {
            for ((i, e) in pending.withIndex()) {
                currentCoroutineContext().ensureActive()
                onProgress(RaSyncProgress(RaSyncStep.SUBMITTING, i + 1, pending.size, cachedSets(e.m, sets)?.Title?.takeIf { it.isNotBlank() }))
                val seconds = (now() / 1000 - e.t).coerceAtLeast(1L).toInt()
                val (url, post) = signer.award(user, token, e.a, e.m, seconds) ?: continue
                val stopped = { stop: RaSyncStop?, error: String? -> Phase1(submitted, dropped, points, stop, error) }
                val r = try { pacer.post(post, url) } catch (ex: RaHttpException) { return finish(user, done, stopped(null, null), Refresh.FAILED, phase1Complete = false) }
                    ?: return finish(user, done, stopped(RaSyncStop.BUSY, null), Refresh.FAILED, phase1Complete = false)
                // An unreadable reply may still have awarded: the points it added are unknown.
                val body = RaJson.parse(AwardBody.serializer(), r.body)
                if (body == null) { sentPoints = null; continue }
                when {
                    body.Success || body.alreadyUnlocked -> {
                        sentPoints = if (body.alreadyUnlocked) sentPoints else sentPoints?.let { sum -> awardPoints(cachedSets(e.m, sets), e.a)?.let { sum + it } }
                        confirmed.append(e); done += e; submitted++
                        body.SoftcoreScore?.let { points = it; patchScore(body.Score ?: 0, it) }
                    }
                    // The login, not this unlock: every later award would be refused too, so all of them stay journaled.
                    authRefused(r) -> return finish(user, done, stopped(RaSyncStop.AUTH, null), Refresh.FAILED, phase1Complete = false)
                    accountRefused(r) -> return finish(user, done, stopped(RaSyncStop.REFUSED, body.Error), Refresh.FAILED, phase1Complete = false)
                    // The server's own answer about this unlock (an unknown achievement, a bad parameter): resending won't change it.
                    else -> { rejected.append(e); done += e; dropped++ }
                }
            }
            val refreshed = refresh(user, token, sentPoints, pacer, onProgress)
            refreshed.points?.let { points = it }
            return finish(user, done, Phase1(submitted, dropped, points, null, null), refreshed, phase1Complete = true)
        } catch (e: CancellationException) {
            // The cancelled coroutine cannot suspend: without NonCancellable the rewrite never runs and confirmed awards replay.
            if (done.isNotEmpty()) withContext(NonCancellable) { journal.remove(done) }
            throw e
        }
    }

    private data class Phase1(val submitted: Int, val rejected: Int, val points: Int?, val stop: RaSyncStop?, val error: String?)

    /** Phase 2's outcome: [games] sessions refreshed, the login's softcore [points]; [stop] when it ended early. */
    private data class Refresh(val games: Int, val points: Int?, val failed: Boolean, val stop: RaSyncStop? = null) {
        companion object { val FAILED = Refresh(0, null, true) }
    }

    private suspend fun finish(user: String, done: List<RaUnlock>, phase1: Phase1, refresh: Refresh, phase1Complete: Boolean): RaSyncResult {
        if (done.isNotEmpty()) journal.remove(done)
        pruneConfirmed(user)
        // lastsync marks a replay that ran to the end; a later refresh failure does not undo that.
        if (phase1Complete) store.writeAtomic(RaPaths.LAST_SYNC, (now() / 1000).toString().encodeToByteArray())
        val stop = phase1.stop ?: refresh.stop
        val failed = !phase1Complete || phase1.stop != null || refresh.failed
        return RaSyncResult(phase1.submitted, refresh.games, phase1.points, failed, phase1.rejected, journal.count(user), stop, phase1.error)
    }

    /**
     * Drops [user]'s confirmed entries the cached session already shows with a time (a refreshed startsession body), so
     * the confirmed journal does not grow forever. Only the session's softcore list counts: the shim merges confirmed
     * unlocks into that one ([RaTransport]), so a pruned entry changes nothing a game or the menu sees. Best effort.
     */
    private suspend fun pruneConfirmed(user: String) {
        try {
            val entries = confirmed.entries(user).filter { RaPaths.isHash(it.m) }
            if (entries.isEmpty()) return
            val sessions = HashMap<Int, Set<Int>>()
            val gameIds = HashMap<String, Int?>()
            val shown = entries.filter { e ->
                val gameId = gameIds.getOrPut(e.m) { RaSetsCache.summary(store, e.m)?.gameId?.takeIf { it != 0 } } ?: return@filter false
                e.a in sessions.getOrPut(gameId) {
                    store.readJson(RaPaths.session(gameId), SessionBody.serializer())
                        ?.Unlocks?.filter { it.When > 0 }?.mapTo(HashSet()) { it.ID }.orEmpty()
                }
            }
            if (shown.isNotEmpty()) confirmed.remove(shown)
        } catch (e: RaStoreException) {
        }
    }

    private suspend fun patchScore(score: Int, softcore: Int) {
        val body = store.read(RaPaths.login())?.decodeToString() ?: return
        val patched = body.replace(Regex("\"Score\":\\d+"), "\"Score\":$score").replace(Regex("\"SoftcoreScore\":\\d+"), "\"SoftcoreScore\":$softcore")
        store.writeAtomic(RaPaths.login(), patched.encodeToByteArray())
    }

    /** The cached sets of [hash], read once per sync into [memo]: null when not cached or unreadable. */
    private suspend fun cachedSets(hash: String, memo: HashMap<String, SetsBody?>): SetsBody? = memo.getOrPut(hash) {
        try { store.readJson(RaPaths.sets(hash), SetsBody.serializer()) } catch (e: RaStoreException) { null }
    }

    /** An official achievement's points from the cached sets; null when unknown (not cached, not found, or unofficial). */
    private fun awardPoints(sets: SetsBody?, id: Int): Int? =
        sets?.Sets?.asSequence()?.flatMap { it.Achievements }?.firstOrNull { it.ID == id }?.takeIf { it.Flags == 3 }?.Points

    private suspend fun readFullSync(): RaFullSync? = try {
        store.readJson(RaPaths.FULL_SYNC, RaFullSync.serializer())
    } catch (e: RaStoreException) { null }

    private suspend fun writeFullSync(record: RaFullSync) =
        store.writeAtomic(RaPaths.FULL_SYNC, RaJson.json.encodeToString(RaFullSync.serializer(), record).encodeToByteArray())

    /** True when the cloud's totals are exactly [last]'s plus [sentPoints] softcore and [last] is recent enough to trust. */
    private fun unchanged(user: String, last: RaFullSync?, score: Int?, softcore: Int?, sentPoints: Int?): Boolean {
        if (last == null || !last.user.equals(user, ignoreCase = true) || score == null || softcore == null || sentPoints == null) return false
        val age = now() - last.at
        return age in 0..RA_FULL_SYNC_MAX_AGE_MS && last.score == score && last.softcore.toLong() + sentPoints == softcore.toLong()
    }

    /** Phase 2: refreshed games and the login's softcore points; [Refresh.failed] on a network failure, a busy server or a refused login. */
    private suspend fun refresh(user: String, token: String, sentPoints: Int?, pacer: RaPacer, onProgress: (RaSyncProgress) -> Unit): Refresh {
        val busy = Refresh(0, null, true, RaSyncStop.BUSY)
        val auth = Refresh(0, null, true, RaSyncStop.AUTH)
        try {
            onProgress(RaSyncProgress(RaSyncStep.ACCOUNT))
            val login = pacer.post(RaRequests.login2(user, token)) ?: return busy
            if (authRefused(login)) return auth
            var points: Int? = null
            var totals: LoginTotals? = null
            if (login.status == 200 && RaJson.isSuccess(login.body)) {
                store.writeAtomic(RaPaths.login(), login.body.encodeToByteArray())
                points = RaJson.parse(LoginBody.serializer(), login.body)?.SoftcoreScore
                totals = RaJson.parse(LoginTotals.serializer(), login.body)
            }
            val score = totals?.Score; val softcore = totals?.SoftcoreScore
            val hashes = store.list(RaPaths.GAMES).filter(RaPaths::isHash)
            val last = readFullSync()
            if (hashes.isNotEmpty() && unchanged(user, last, score, softcore, sentPoints)) {
                // The cache matches the cloud: keep the full sync's date, move its totals past this replay's unlocks.
                if (last != null && score != null && softcore != null) writeFullSync(last.copy(score = score, softcore = softcore))
                return Refresh(0, points, false)
            }
            data class Cached(val hash: String, val gameId: Int, val setGameIds: List<Int>, val consoleId: Int, val unlocks: Int, val title: String)
            val cached = hashes.withIndex().mapNotNull { (i, hash) ->
                onProgress(RaSyncProgress(RaSyncStep.READING, i + 1, hashes.size))
                val sets = RaSetsCache.summary(store, hash) ?: return@mapNotNull null
                if (sets.gameId == 0 || sets.consoleId == 0) return@mapNotNull null   // not a usable set reply
                val session = store.readJson(RaPaths.session(sets.gameId), SessionBody.serializer())
                Cached(hash, sets.gameId, sets.setGameIds.ifEmpty { listOf(sets.gameId) }, sets.consoleId, session?.let(::unlockCount) ?: -1, sets.title)
            }
            val serverCounts = HashMap<Int, Int>()
            var complete = true   // every console's progress read and every stale game refreshed
            val consoles = cached.map { it.consoleId }.distinct()
            for ((i, console) in consoles.withIndex()) {
                currentCoroutineContext().ensureActive()
                val games = cached.count { it.consoleId == console }
                onProgress(RaSyncProgress(RaSyncStep.PROGRESS, i + 1, consoles.size, "$games game${if (games == 1) "" else "s"}"))
                val r = pacer.post(RaRequests.allProgress(user, token, console)) ?: return busy.copy(points = points)
                if (authRefused(r)) return auth.copy(points = points)
                val progress = RaJson.parse(AllProgressBody.serializer(), r.body)
                if (progress == null || r.status != 200 || !progress.Success) complete = false
                progress?.Response?.forEach { (id, p) -> id.toIntOrNull()?.let { serverCounts[it] = p.Unlocked } }
            }
            var refreshed = 0
            // A game the account never played is absent or 0 on the server and has no cached unlocks: never stale, so a
            // sync opens no session (which would mark it played) for it. The session lists every set's unlocks, while
            // allprogress counts each set under its own game id: the server's count is their sum.
            val stale = cached.filter { g ->
                val counts = g.setGameIds.mapNotNull { serverCounts[it] }
                counts.isNotEmpty() && counts.sum() != g.unlocks
            }
            for ((i, g) in stale.withIndex()) {
                currentCoroutineContext().ensureActive()
                onProgress(RaSyncProgress(RaSyncStep.SESSIONS, i + 1, stale.size, g.title.ifBlank { null }))
                val r = pacer.post(RaRequests.startSession(user, token, g.gameId, g.hash)) ?: return Refresh(refreshed, points, true, RaSyncStop.BUSY)
                if (authRefused(r)) return Refresh(refreshed, points, true, RaSyncStop.AUTH)
                if (r.status == 200 && RaJson.isSuccess(r.body)) { store.writeAtomic(RaPaths.session(g.gameId), r.body.encodeToByteArray()); refreshed++ } else complete = false
            }
            // Only a complete full sync against known totals is a baseline a later sync may trust.
            if (complete && score != null && softcore != null) writeFullSync(RaFullSync(user, now(), score, softcore))
            return Refresh(refreshed, points, false)
        } catch (e: RaHttpException) {
            return Refresh.FAILED
        }
    }
}

/**
 * The unlocks a cached session counts as the server's `allprogress` does (`Unlocked`: softcore and hardcore, official
 * only): both lists, each id once, without the Unknown-Emulator warning the server adds for this client.
 */
internal fun unlockCount(session: SessionBody): Int =
    (session.Unlocks.asSequence() + session.HardcoreUnlocks.asSequence()).map { it.ID }.filter { it != UNKNOWN_EMULATOR_ID }.toSet().size
