package com.nxredux.mobile.scraper

import com.nxredux.mobile.library.DiscSheet
import com.nxredux.mobile.tryOrNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.concurrent.Volatile

/** The ScreenScraper system for [ref], or null when its tag is excluded or has no system (skipped). An Android game: Android. */
internal fun LibraryGameRef.systemId(): Int? = when {
    android -> ScraperSystems.ANDROID
    tag.trim().uppercase() in ScraperSystems.EXCLUDED_TAGS -> null
    else -> ScraperSystems.systemIdFor(tag, fileName)
}

/**
 * An Android game's lookup ([ScreenScraperSource.androidScreenshot]); a match carries its [medias] (every one it has, for
 * the Change art options) and its ScreenScraper game id when the answer had one.
 */
internal sealed interface AndroidHit {
    data object None : AndroidHit
    /** A game matched, but none of the matches has a screenshot: the first match's media. */
    data class NoScreenshot(val medias: List<SsMedia>, val gameId: String?) : AndroidHit
    /** The first match with a screenshot: its best `ss` [url] and its media. */
    data class Screenshot(val url: String, val medias: List<SsMedia>, val gameId: String?) : AndroidHit
}

/**
 * A game's info from a `jeuInfos` lookup: [Found] (any of the facts), [Missing] (not found, or found with none of them:
 * a miss to remember) or [Failed] (an answer that could not be read, or an unexpected status: never a miss).
 */
sealed interface InfoResult {
    data class Found(val info: SsGameInfo, val ssId: String?) : InfoResult
    data object Missing : InfoResult
    data object Failed : InfoResult
}

/**
 * ScreenScraper as an [ArtSource] (NX Redux `scraper.c`): paced lookups (try 1 by name, try 2 with CRC + size), every
 * `box-3D`, `box-2D`, `ss` and wheel (`wheel-hd`, then `wheel`) ranked per [ScraperMedia.alternates], each with its
 * thumbnail link ([ScraperRequest.thumb]), the quota read at job start and every [QUOTA_EVERY] lookups when signed in, and the error
 * table: no connection pauses the job, every other refusal stops this source only. Also the account calls (sign-in
 * check, quota) and the Android games console's lookup.
 */
class ScreenScraperSource(
    private val http: ScraperHttp, private val hasher: RomHasher, private val auth: () -> ScraperAuth,
    /** Epoch millis: the pacing's clock and the stored info's fetch time. */
    internal val now: () -> Long, private val sleep: suspend (Long) -> Unit,
) : ArtSource {
    companion object {
        const val RATE_MS = 1_200L
        const val HASH_LIMIT = 64L * 1024 * 1024
        const val QUOTA_EVERY = 50
        val BACKOFF = listOf(2_000L, 5_000L, 10_000L)
        const val TOO_FAST_WAIT_MS = 10_000L
        const val SIGN_IN_FAILED = "ScreenScraper sign-in failed"
        const val DAILY_LIMIT = "ScreenScraper daily limit reached; Fetch missing again tomorrow to ask it"
        const val UNAVAILABLE = "ScreenScraper is unavailable right now"
        /** 403 with ScreenScraper's developer-login text: this build's dev credentials were refused. */
        const val BUILD_REFUSED = "This build's ScreenScraper access was refused"
        /** 426: the software is blacklisted or too old. */
        const val APP_BLOCKED = "This app version is blocked by ScreenScraper — update the app"
        /** 431: too many unrecognised ROMs today. */
        const val UNRECOGNISED_LIMIT = "ScreenScraper's daily limit for unrecognised games reached; Fetch missing again tomorrow to ask it"
        /**
         * Files whose CRC is not the game's (a disc sheet, a playlist, a CHD container): try 2 is never sent for them,
         * as its answer can only be a miss that costs a quota request.
         */
        private val NOT_HASHED = DiscSheet.EXTENSIONS + "chd"
        /** Arcade systems (a DC zip/7z refines to 75): a set's archive is the game, its own CRC the one to send. */
        private val ARCADE_IDS = setOf(75, 6, 7, 8, 142)
        /** ROM hashes kept for the process ([hash]); each about a hundred bytes. */
        private const val HASHES_KEPT = 4_096
    }

    override val id = SourceId.SS
    override val label = "ScreenScraper"
    override val enabled: Boolean get() = auth().available
    override val slots: Set<ArtSlot> = ScraperMedia.SLOTS.toSet()
    override fun knows(ref: LibraryGameRef): Boolean = ref.systemId() != null

    private val pacer = Pacer(RATE_MS, now, sleep)
    /** Written by the job and a single-game fetch at once (one shared source): a whole value, read without a lock. */
    @Volatile private var quota: SsQuota? = null
    /** Guards [sinceQuota] (the job and a single-game fetch count lookups together). */
    private val countLock = Mutex()
    private var sinceQuota = 0

    /** Guards [hashes] (the job and a single-game fetch hash at once). */
    private val hashLock = Mutex()
    /**
     * Each ROM's last hash by (ref, read inside a zip) with the [RomStamp] it was read at: Resume, Fetch again and
     * Change art ask for the same ROMs again, and a hash streams up to [HASH_LIMIT] bytes. Oldest dropped first.
     */
    private val hashes = LinkedHashMap<String, Pair<RomStamp, Pair<Long, Long>>>()

    /** When [quota] was read ([now]); with it, a reading from an earlier day is told from today's. */
    @Volatile private var quotaRead: Pair<SsQuota, Long>? = null

    /** The last quota read (null after [forgetQuota] or before any read). */
    val lastQuota: SsQuota? get() = quota
    /** [lastQuota] with when it was read (epoch millis). */
    val lastQuotaRead: Pair<SsQuota, Long>? get() = quotaRead

    /** Drops the last known quota (sign-out): the next run reports only its own reads. */
    fun forgetQuota() { quota = null; quotaRead = null }

    private fun keepQuota(q: SsQuota) { quota = q; quotaRead = q to now() }

    override suspend fun startJob() {
        countLock.withLock { sinceQuota = 0 }
        if (auth().signedIn) checkQuota()
    }

    override suspend fun find(ref: LibraryGameRef, wanted: Set<ArtSlot>): List<ArtHit> = findWithInfo(ref, wanted).first

    /** [find], with the game's info read from the same answer (no extra request); null info: [ref] has no system. */
    internal suspend fun findWithInfo(ref: LibraryGameRef, wanted: Set<ArtSlot>): Pair<List<ArtHit>, InfoResult?> {
        val systemId = ref.systemId() ?: return emptyList<ArtHit>() to null
        countLookup()
        val answer = lookup(ref, systemId)
        val found = answer.found ?: return emptyList<ArtHit>() to answer.info()
        val offered = ScraperMedia.alternates(found.medias)
        val hits = ScraperMedia.SLOTS.filter { it in wanted }.flatMap { s -> offered[s].orEmpty().map { ArtHit(s, it.url, origin(found.id, it), note = it.region, thumb = ScraperRequest.thumb(it.url)) } }
        return hits to answer.info()
    }

    /**
     * The game's info alone: the art's lookup ([lookup]) without any media download, counted and paced as one.
     * Throws [SourceStop] / [JobPause] as [find] does; [InfoResult.Missing] when [ref] has no system.
     */
    internal suspend fun info(ref: LibraryGameRef): InfoResult {
        val systemId = ref.systemId() ?: return InfoResult.Missing
        countLookup()
        return lookup(ref, systemId).info()
    }

    /** The signed-in user's quota (paced), or null when not signed in or the call failed. */
    suspend fun quota(): SsQuota? {
        val a = auth()
        if (!a.signedIn) return null
        return try {
            val r = api(ScraperRequest.userInfo(a))
            ScraperResponse.parseQuota(r.status, r.body?.decodeToString())?.also { keepQuota(it) }
        } catch (_: SourceStop) { null } catch (_: JobPause) { null }
    }

    /**
     * Validates the stored login with `ssuserInfos`: bad login (a 403 about the user's login, or an answer without a
     * user) vs this build refused (a 403 about the developer login) vs offline vs unavailable (401 included).
     */
    suspend fun checkLogin(): SignInResult {
        val a = auth()
        if (!a.signedIn) return SignInResult.BAD_LOGIN
        val r = try { api(ScraperRequest.userInfo(a)) } catch (_: JobPause) { return SignInResult.OFFLINE } catch (s: SourceStop) {
            return when (s.note) {
                SIGN_IN_FAILED -> SignInResult.BAD_LOGIN
                BUILD_REFUSED -> SignInResult.BUILD_REFUSED
                else -> SignInResult.UNAVAILABLE
            }
        }
        if (r.status != 200) return SignInResult.UNAVAILABLE
        keepQuota(ScraperResponse.parseQuota(r.status, r.body?.decodeToString()) ?: return SignInResult.BAD_LOGIN)
        return SignInResult.OK
    }

    /**
     * Android's lookup order: `jeuInfos` on Android by `<label>.apk` (ScreenScraper falls back to a name match), then
     * `jeuRecherche` on Android, then on every system; a search keeps only games with a name equal to [label] once
     * normalised ([normaliseTitle]), never the first hit as such. A match without an `ss` screenshot moves on to the
     * next step (the first match's media are kept: [AndroidHit.NoScreenshot]). Every call is paced; no hashing.
     */
    internal suspend fun androidScreenshot(label: String): AndroidHit {
        countLookup()
        val a = auth()
        var first: AndroidHit.NoScreenshot? = null
        found(api(ScraperRequest.gameInfo(a, "$label.apk", ScraperSystems.ANDROID)))?.let { hit ->
            ScraperMedia.screenshotOnly(hit.medias)?.let { return AndroidHit.Screenshot(it, hit.medias, hit.id) }
            first = AndroidHit.NoScreenshot(hit.medias, hit.id)
        }
        val wanted = normaliseTitle(label)
        for (system in listOf(ScraperSystems.ANDROID, null)) {
            val r = api(ScraperRequest.search(a, label, system))
            val exact = ScraperResponse.parseSearch(r.status, r.body?.decodeToString())
                .filter { g -> g.names.any { normaliseTitle(it) == wanted } }
            if (first == null) exact.firstOrNull()?.let { first = AndroidHit.NoScreenshot(it.medias, null) }
            exact.firstNotNullOfOrNull { g -> ScraperMedia.screenshotOnly(g.medias)?.let { AndroidHit.Screenshot(it, g.medias, null) } }?.let { return it }
        }
        return first ?: AndroidHit.None
    }

    /**
     * An Android game's options from its lookup's [medias] ([androidScreenshot]): every screenshot, 2D box and wheel
     * (no 3D box: an Android game has none), as [find] gives them (each with its thumbnail link and origin).
     */
    internal fun androidHits(medias: List<SsMedia>, gameId: String?): List<ArtHit> {
        val offered = ScraperMedia.alternates(medias)
        return ScraperMedia.SLOTS.filter { it in AndroidArtStore.SLOTS }.flatMap { s ->
            offered[s].orEmpty().map { ArtHit(s, it.url, origin(gameId, it), note = it.region, thumb = ScraperRequest.thumb(it.url)) }
        }
    }

    /** Every [QUOTA_EVERY] games looked up, a signed-in user's quota is read again. */
    private suspend fun countLookup() {
        val due = countLock.withLock {
            (sinceQuota >= QUOTA_EVERY && auth().signedIn).also { if (it) sinceQuota = 0; sinceQuota++ }
        }
        if (due) checkQuota()
    }

    private suspend fun checkQuota() {
        val r = api(ScraperRequest.userInfo(auth()))
        val q = ScraperResponse.parseQuota(r.status, r.body?.decodeToString()) ?: return
        keepQuota(q)
        if (q.today >= q.max) throw SourceStop(DAILY_LIMIT)
    }

    /**
     * A paced API call with the error table (codes as ScreenScraper answers them, 403/404 checked against the live
     * API): retries, then stops this source (no connection: pauses the job); returns only answers that are neither (a
     * 404, not found, is returned). Not [PacedGet]: a 5xx stops at once here, 429 / 430 are quota answers and the whole
     * answer comes back.
     */
    private suspend fun api(url: String): HttpResult {
        var failures = 0
        var retriedTooFast = false
        while (true) {
            val r = pacer.paced { http.get(url) }
            when {
                r.status == -1 -> { if (failures == BACKOFF.size) throw JobPause(ScrapeEngine.NO_CONNECTION); sleep(BACKOFF[failures++]) }
                // A wrong user password on jeuInfos is a 200 (anonymous fallback); only ssuserInfos says 403 for it.
                r.status == 403 -> throw SourceStop(if (ScraperResponse.isDevLoginError(r.body?.decodeToString())) BUILD_REFUSED else SIGN_IN_FAILED)
                r.status == 401 -> throw SourceStop(UNAVAILABLE)   // API closed to non-members / inactive: not a bad login
                r.status == 426 -> throw SourceStop(APP_BLOCKED)
                r.status == 431 -> throw SourceStop(UNRECOGNISED_LIMIT)
                r.status == 429 || r.status == 430 -> {
                    if (retriedTooFast) throw SourceStop(if (r.status == 430 || quotaReached()) DAILY_LIMIT else UNAVAILABLE)
                    retriedTooFast = true
                    sleep(TOO_FAST_WAIT_MS)
                }
                r.status == 423 || r.status >= 500 -> throw SourceStop(UNAVAILABLE)
                else -> return r
            }
        }
    }

    /** One plain quota read (no retries) after repeated 429s. */
    private suspend fun quotaReached(): Boolean {
        val a = auth()
        if (!a.signedIn) return false
        val r = pacer.paced { http.get(ScraperRequest.userInfo(a)) }
        val q = ScraperResponse.parseQuota(r.status, r.body?.decodeToString()) ?: return false
        keepQuota(q)
        return q.today >= q.max
    }

    /**
     * Try 1 by name; try 2 with CRC + size when try 1 finds nothing (skipped when the ROM can't be hashed, and for a
     * sheet or container ([NOT_HASHED]): its CRC names no game). A console game's zip sends the ROM inside it; its 7z
     * (not read) skips try 2. An arcade set's archive is the game itself and is hashed whole.
     */
    private suspend fun lookup(ref: LibraryGameRef, systemId: Int): Answer {
        val a = auth()
        val first = answer(api(ScraperRequest.gameInfo(a, ref.fileName, systemId)))
        if (first.found != null) return first
        val ext = ref.fileName.substringAfterLast('.', "").lowercase()
        if (ext in NOT_HASHED) return first
        val inside = ext == "zip" && systemId !in ARCADE_IDS
        if (ext == "7z" && systemId !in ARCADE_IDS) return first
        val (crc, size) = hash(ref, inside)?.takeIf { it.second <= HASH_LIMIT } ?: return first
        val second = answer(api(ScraperRequest.gameInfo(a, ref.fileName, systemId, crc, size)))
        return if (second.found != null) second else Answer(null, first.unread || second.unread)
    }

    /** A lookup's game, or none; [unread]: an answer was neither a game nor a plain not-found (its info is no miss). */
    private class Answer(val found: GameLookup.Found?, val unread: Boolean) {
        fun info(): InfoResult = when {
            found != null -> found.info?.let { InfoResult.Found(it, found.id) } ?: InfoResult.Missing
            unread -> InfoResult.Failed
            else -> InfoResult.Missing
        }
    }

    private fun answer(r: HttpResult): Answer {
        val body = r.body?.decodeToString()
        val found = ScraperResponse.parseGame(r.status, body) as? GameLookup.Found
        val unread = found == null && r.status != 404 && !(r.status == 200 && ScraperResponse.isJson(body))
        return Answer(found, unread)
    }

    /** [ref]'s ROM's CRC32 and size, reused while its [RomStamp] is unchanged; a failed hash is not kept. */
    private suspend fun hash(ref: LibraryGameRef, inZip: Boolean): Pair<Long, Long>? = tryOrNull {
        val key = (if (inZip) "zip:" else "") + ref.rom.value
        val stamp = hasher.stamp(ref.rom)
        stamp?.let { st -> hashLock.withLock { hashes[key]?.takeIf { it.first == st }?.second } }
            ?: (if (inZip) hasher.crc32AndSizeInZip(ref.rom) else hasher.crc32AndSize(ref.rom))?.also { h ->
                if (stamp != null) hashLock.withLock {
                    hashes.remove(key); hashes[key] = stamp to h
                    while (hashes.size > HASHES_KEPT) hashes.remove(hashes.keys.first())
                }
            }
    }

    private fun found(r: HttpResult): GameLookup.Found? =
        ScraperResponse.parseGame(r.status, r.body?.decodeToString()) as? GameLookup.Found

    /** The log's origin of a ScreenScraper media: the game id and `<type>/<region>`. */
    private fun origin(gameId: String?, m: SsMedia) = ArtOrigin(SourceId.SS, gameId, m.region?.let { "${m.type}/$it" } ?: m.type)
}
