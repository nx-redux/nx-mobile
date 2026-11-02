package com.nxredux.mobile.scraper

import com.nxredux.mobile.library.DiscSheet
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.concurrent.Volatile

/** The ScreenScraper system for [ref], or null when its tag is excluded or has no system (skipped). An Android game: Android. */
internal fun LibraryGameRef.systemId(): Int? = when {
    android -> ScraperSystems.ANDROID
    tag.trim().uppercase() in ScraperSystems.EXCLUDED_TAGS -> null
    else -> ScraperSystems.systemIdFor(tag, fileName)
}

/** An Android game's lookup ([ScreenScraperSource.androidScreenshot]). */
internal sealed interface AndroidHit {
    data object None : AndroidHit
    /** A game matched, but none of the matches has a screenshot. */
    data object NoScreenshot : AndroidHit
    data class Screenshot(val url: String) : AndroidHit
}

/**
 * ScreenScraper as an [ArtSource] (NX Redux `scraper.c`): paced lookups (try 1 by name, try 2 with CRC + size), the
 * best `box-3D`, `box-2D`, `ss` and wheel (`wheel-hd`, then `wheel` should its download fail) per
 * [ScraperMedia.alternates], the quota read at job start and every [QUOTA_EVERY] lookups when signed in, and the error
 * table: no connection pauses the job, every other refusal stops this source only. Also the account calls (sign-in
 * check, quota) and the Android games console's lookup.
 */
class ScreenScraperSource(
    private val http: ScraperHttp, private val hasher: RomHasher, private val auth: () -> ScraperAuth,
    now: () -> Long, private val sleep: suspend (Long) -> Unit,
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

    /** The last quota read (null after [forgetQuota] or before any read). */
    val lastQuota: SsQuota? get() = quota

    /** Drops the last known quota (sign-out): the next run reports only its own reads. */
    fun forgetQuota() { quota = null }

    override suspend fun startJob() {
        countLock.withLock { sinceQuota = 0 }
        if (auth().signedIn) checkQuota()
    }

    override suspend fun find(ref: LibraryGameRef, wanted: Set<ArtSlot>): List<ArtHit> {
        val systemId = ref.systemId() ?: return emptyList()
        countLookup()
        val found = lookup(ref, systemId) ?: return emptyList()
        val offered = ScraperMedia.alternates(found.medias)
        return ScraperMedia.SLOTS.filter { it in wanted }.flatMap { s -> offered[s].orEmpty().map { ArtHit(s, it.url, origin(found.id, it), note = it.region) } }
    }

    /** The signed-in user's quota (paced), or null when not signed in or the call failed. */
    suspend fun quota(): SsQuota? {
        val a = auth()
        if (!a.signedIn) return null
        return try {
            val r = api(ScraperRequest.userInfo(a))
            ScraperResponse.parseQuota(r.status, r.body?.decodeToString())?.also { quota = it }
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
        quota = ScraperResponse.parseQuota(r.status, r.body?.decodeToString()) ?: return SignInResult.BAD_LOGIN
        return SignInResult.OK
    }

    /**
     * Android's lookup order: `jeuInfos` on Android by `<label>.apk` (ScreenScraper falls back to a name match), then
     * `jeuRecherche` on Android, then on every system; a search keeps only games with a name equal to [label] once
     * normalised ([normaliseTitle]), never the first hit as such. A match without an `ss` screenshot moves on to the
     * next step. Every call is paced; no hashing.
     */
    internal suspend fun androidScreenshot(label: String): AndroidHit {
        countLookup()
        val a = auth()
        var matched = false
        found(api(ScraperRequest.gameInfo(a, "$label.apk", ScraperSystems.ANDROID)))?.let { hit ->
            matched = true
            ScraperMedia.screenshotOnly(hit.medias)?.let { return AndroidHit.Screenshot(it) }
        }
        val wanted = normaliseTitle(label)
        for (system in listOf(ScraperSystems.ANDROID, null)) {
            val r = api(ScraperRequest.search(a, label, system))
            val exact = ScraperResponse.parseSearch(r.status, r.body?.decodeToString())
                .filter { g -> g.names.any { normaliseTitle(it) == wanted } }
            if (exact.isNotEmpty()) matched = true
            exact.firstNotNullOfOrNull { ScraperMedia.screenshotOnly(it.medias) }?.let { return AndroidHit.Screenshot(it) }
        }
        return if (matched) AndroidHit.NoScreenshot else AndroidHit.None
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
        quota = q
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
        quota = q
        return q.today >= q.max
    }

    /**
     * Try 1 by name; try 2 with CRC + size when try 1 finds nothing (skipped when the ROM can't be hashed, and for a
     * sheet or container ([NOT_HASHED]): its CRC names no game). A console game's zip sends the ROM inside it; its 7z
     * (not read) skips try 2. An arcade set's archive is the game itself and is hashed whole.
     */
    private suspend fun lookup(ref: LibraryGameRef, systemId: Int): GameLookup.Found? {
        val a = auth()
        found(api(ScraperRequest.gameInfo(a, ref.fileName, systemId)))?.let { return it }
        val ext = ref.fileName.substringAfterLast('.', "").lowercase()
        if (ext in NOT_HASHED) return null
        val inside = ext == "zip" && systemId !in ARCADE_IDS
        if (ext == "7z" && systemId !in ARCADE_IDS) return null
        val (crc, size) = hash(ref, inside)?.takeIf { it.second <= HASH_LIMIT } ?: return null
        return found(api(ScraperRequest.gameInfo(a, ref.fileName, systemId, crc, size)))
    }

    private suspend fun hash(ref: LibraryGameRef, inZip: Boolean): Pair<Long, Long>? = try {
        if (inZip) hasher.crc32AndSizeInZip(ref.rom) else hasher.crc32AndSize(ref.rom)
    } catch (e: CancellationException) { throw e } catch (_: Exception) { null }

    private fun found(r: HttpResult): GameLookup.Found? =
        ScraperResponse.parseGame(r.status, r.body?.decodeToString()) as? GameLookup.Found

    /** The log's origin of a ScreenScraper media: the game id and `<type>/<region>`. */
    private fun origin(gameId: String?, m: SsMedia) = ArtOrigin(SourceId.SS, gameId, m.region?.let { "${m.type}/$it" } ?: m.type)
}
