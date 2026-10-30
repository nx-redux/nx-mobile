package com.nxredux.mobile.achievements

import kotlin.concurrent.Volatile
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull

enum class RaSessionMode { ONLINE, OFFLINE, DISABLED }

private const val OFFLINE_BODY = """{"Success":false,"Error":"Offline mode"}"""

/**
 * The server-call shim (nx-redux `ra_offline.c`): online, requests go out and successful bodies are cached by kind;
 * offline, they are answered from the cache, `startsession` gets the journal and confirmed unlocks merged in, and
 * `awardachievement` is journaled. Online, an award is journaled before its POST and taken off on a [definitive] answer,
 * so one whose answer never comes (no connection, a 5xx or 429, an HTML error page, or the process killed mid-POST)
 * stays for the sync; a game lookup without one falls back to its cached body when there is one.
 */
class RaTransport(
    private val store: RaStore, private val http: RaHttp,
    private val journal: RaJournal, private val confirmed: RaJournal,
    private val now: () -> Long,
) {
    @Volatile var mode: RaSessionMode = RaSessionMode.DISABLED   // written by RaSession, read on IO threads

    suspend fun call(url: String, post: String): RaResponse {
        val kind = RaRequestKind.of(post)
        return when (mode) {
            RaSessionMode.ONLINE -> online(url, post, kind)
            RaSessionMode.OFFLINE -> offline(kind)
            RaSessionMode.DISABLED -> RaResponse(200, OFFLINE_BODY)
        }
    }

    private suspend fun online(url: String, post: String, kind: RaRequestKind): RaResponse {
        if (kind.r == "awardachievement") return onlineAward(url, post, kind)
        val response = try { http.post(url, post, RaRequests.CONTENT_TYPE) } catch (e: RaHttpException) { null }
        if (response != null && definitive(response)) {
            if (response.status == 200 && RaJson.isSuccess(response.body)) cache(kind, response.body)
            return response
        }
        cachedLookup(kind)?.let { return it }
        return response ?: RaResponse(0, "")   // 0 = retryable for rc_client
    }

    /**
     * rc_client retries an award only in memory, so it is lost when the game unloads, and the game process may end (or be
     * killed) mid-POST: the journal holds it from before the POST until the server's own answer (a double submission is
     * harmless, the sync takes "already has" as done).
     */
    private suspend fun onlineAward(url: String, post: String, kind: RaRequestKind): RaResponse {
        val entry = unlock(kind) ?: return RaResponse(200, OFFLINE_BODY)
        // An unwritable journal (a full disk) must not keep the award from the server: it is only a failure unanswered.
        var unwritten: RaStoreException? = null
        val added = try { journal.append(entry) } catch (e: RaStoreException) { unwritten = e; false }
        val response = try { http.post(url, post, RaRequests.CONTENT_TYPE) } catch (e: RaHttpException) { null }
        if (response != null && definitive(response)) {
            // Only this call's entry: an equal older one (an offline unlock) stays for the sync. A failed rewrite only
            // costs a harmless second submission.
            if (added) runCatching { journal.remove(listOf(entry)) }
            return response
        }
        unwritten?.let { throw it }   // neither answered nor kept: the bridge's hard failure
        return journaled(entry)
    }

    /** A game lookup's cached body; startsession only from a real cached session, merged like offline. */
    private suspend fun cachedLookup(kind: RaRequestKind): RaResponse? = when (kind.r) {
        "gameid", "achievementsets" -> cachePath(kind)?.let { store.read(it) }?.let { RaResponse(200, it.decodeToString()) }
        "startsession" -> kind.g?.takeIf { store.exists(RaPaths.session(it)) }?.let { startSession(kind) }
        else -> null
    }

    /** A set reply goes through [RaSetsCache] (its summary is rewritten with it); every other kind is written as is. */
    private suspend fun cache(kind: RaRequestKind, body: String) {
        val path = cachePath(kind) ?: return
        val hash = kind.m
        if (kind.r == "achievementsets" && hash != null) RaSetsCache.write(store, hash, body) else store.writeAtomic(path, body.encodeToByteArray())
    }

    private fun cachePath(kind: RaRequestKind): String? = when (kind.r) {
        "login2" -> RaPaths.login()
        "gameid" -> kind.m?.takeIf(RaPaths::isHash)?.let(RaPaths::gameId)
        "achievementsets" -> kind.m?.takeIf(RaPaths::isHash)?.let(RaPaths::sets)
        "startsession" -> kind.g?.let(RaPaths::session)
        else -> null
    }

    private suspend fun offline(kind: RaRequestKind): RaResponse = when (kind.r) {
        "login2" -> {
            val body = store.read(RaPaths.login())?.decodeToString()
            val user = body?.let { RaJson.parse(LoginBody.serializer(), it) }?.User
            if (body != null && user != null && user.equals(kind.u, ignoreCase = true)) RaResponse(200, body) else RaResponse(200, OFFLINE_BODY)
        }
        "gameid" -> kind.m?.takeIf(RaPaths::isHash)?.let { store.read(RaPaths.gameId(it)) }?.let { RaResponse(200, it.decodeToString()) }
            ?: RaResponse(200, """{"Success":true,"GameID":0}""")
        "achievementsets" -> kind.m?.takeIf(RaPaths::isHash)?.let { store.read(RaPaths.sets(it)) }?.let { RaResponse(200, it.decodeToString()) }
            ?: RaResponse(200, OFFLINE_BODY)
        "startsession" -> startSession(kind)
        "ping" -> RaResponse(200, """{"Success":true}""")
        "awardachievement" -> journalAward(kind)
        else -> RaResponse(200, OFFLINE_BODY)
    }

    private suspend fun startSession(kind: RaRequestKind): RaResponse {
        val gameId = kind.g ?: return RaResponse(200, OFFLINE_BODY)
        val hash = kind.m?.takeIf(RaPaths::isHash)
        val user = kind.u ?: ""
        val cached = store.readJson(RaPaths.session(gameId), SessionBody.serializer())
        val session = cached ?: if (hash != null && store.exists(RaPaths.sets(hash))) SessionBody(true, emptyList(), emptyList(), now() / 1000) else return RaResponse(200, OFFLINE_BODY)
        val extra = (journal.entries(user) + confirmed.entries(user)).filter { hash == null || it.m == hash }
        val merged = LinkedHashMap<Int, Long>()
        session.Unlocks.forEach { merged[it.ID] = it.When }
        extra.forEach { if (it.a !in merged) merged[it.a] = it.t }
        val body = RaJson.json.encodeToString(session.copy(Unlocks = merged.map { SessionUnlock(it.key, it.value) }))
        return RaResponse(200, body)
    }

    private suspend fun journalAward(kind: RaRequestKind): RaResponse {
        val entry = unlock(kind) ?: return RaResponse(200, OFFLINE_BODY)
        journal.append(entry)   // RaStoreException propagates to the bridge (hard failure)
        return journaled(entry)
    }

    private fun unlock(kind: RaRequestKind): RaUnlock? = kind.a?.let { RaUnlock(kind.u ?: "", it, kind.m ?: "", now() / 1000) }

    private fun journaled(entry: RaUnlock) = RaResponse(200, """{"Success":true,"AchievementID":${entry.a}}""")
}

/**
 * The server's own answer: a reply whose body is RA JSON with a `Success` flag, from a status that is not a retryable
 * one (5xx, 429). An auth error (a 200 or 401 with `Success:false`) is definitive; a gateway's HTML page is not.
 */
internal fun definitive(response: RaResponse): Boolean {
    if (response.status == 0 || response.status == 429 || response.status >= 500) return false
    val obj = runCatching { RaJson.json.parseToJsonElement(response.body) }.getOrNull() as? JsonObject ?: return false
    return (obj["Success"] as? JsonPrimitive)?.booleanOrNull != null
}

/** Decides a game session's mode once: connectivity, then a login2 that may fall back to the cache (2 s, 4 s retries). */
class RaSession(
    private val transport: RaTransport, private val store: RaStore, private val connectivity: RaConnectivity,
    private val delay: suspend (Long) -> Unit,
) {
    suspend fun start(user: String, token: String): RaSessionMode {
        val cached = store.exists(RaPaths.login())
        if (!connectivity.isConnected()) return apply(if (cached) RaSessionMode.OFFLINE else RaSessionMode.DISABLED)
        transport.mode = RaSessionMode.ONLINE
        val post = RaRequests.login2(user, token)
        for (wait in listOf(0L, 2_000L, 4_000L)) {
            if (wait > 0) delay(wait)
            if (definitive(transport.call(RaRequests.URL, post))) return RaSessionMode.ONLINE   // a 5xx / 429 / HTML page is no answer
        }
        return apply(if (cached) RaSessionMode.OFFLINE else RaSessionMode.DISABLED)
    }
    private fun apply(mode: RaSessionMode): RaSessionMode { transport.mode = mode; return mode }
}
