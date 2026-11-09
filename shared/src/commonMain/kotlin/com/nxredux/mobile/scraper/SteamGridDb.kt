package com.nxredux.mobile.scraper

import com.nxredux.mobile.scraper.LenientJson.bool
import com.nxredux.mobile.scraper.LenientJson.int
import com.nxredux.mobile.scraper.LenientJson.str
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull

/** A `search/autocomplete` result. */
data class SgdbGame(val id: Int, val name: String, val verified: Boolean = false)

/** A grid, hero or logo; absent fields take these defaults (0 width / height fails every shape check); [thumb]: its small version. */
data class SgdbAsset(
    val id: Int, val url: String, val width: Int = 0, val height: Int = 0, val mime: String? = null, val score: Int = 0,
    val nsfw: Boolean = false, val humor: Boolean = false, val epilepsy: Boolean = false, val style: String? = null,
    val thumb: String? = null,
)

/** The answer of a key check ([SteamGridDbSource.check]). */
enum class KeyResult { OK, BAD_KEY, OFFLINE, UNAVAILABLE }

/**
 * SteamGridDB API v2's pure rules: request URLs, lenient parsers, the exact-name match and the ranking. Shapes from
 * the official OpenAPI file (`https://www.steamgriddb.com/static/openapi.yml`, 2.10.0) plus the fields the live API
 * carries beyond it (`width`, `height`, `mime`, `nsfw`, `humor`, `epilepsy`).
 * Size never filters or ranks; only the slot's shape does.
 */
object Sgdb {
    const val API = "https://www.steamgriddb.com/api/v2"
    private const val FILTERS = "types=static&mimes=image/png,image/jpeg&nsfw=false&humor=false&epilepsy=false"
    /** The term a key check searches for (any common name; only the status matters). */
    const val CHECK_TERM = "tetris"

    /** The request header carrying [key]. */
    fun auth(key: String): Map<String, String> = mapOf("Authorization" to "Bearer $key")

    fun search(name: String): String = "$API/search/autocomplete/${Libretro.pathEncode(name)}"

    /** `alternate` grids only (the closest to official covers); no `dimensions` (size never filters). */
    fun grids(gameId: Int): String = "$API/grids/game/$gameId?styles=alternate&$FILTERS"

    fun heroes(gameId: Int): String = "$API/heroes/game/$gameId?$FILTERS"

    /** The game's logos (the wheel): still PNGs only (logos come as PNG or WebP), any style. */
    fun logos(gameId: Int): String = "$API/logos/game/$gameId?types=static&mimes=image/png&nsfw=false&humor=false&epilepsy=false"

    private fun data(body: String?): JsonArray? = LenientJson.parseObject(body)
        ?.takeIf { (it["success"] as? JsonPrimitive)?.booleanOrNull != false }
        ?.get("data") as? JsonArray

    /** A `search/autocomplete` body; anything unreadable (or `success:false`) is empty, never a throw. */
    fun parseGames(body: String?): List<SgdbGame> = data(body).orEmpty().mapNotNull { e ->
        val g = e as? JsonObject ?: return@mapNotNull null
        SgdbGame(g["id"].int() ?: return@mapNotNull null, g["name"].str() ?: return@mapNotNull null, g["verified"].bool())
    }

    /** A `grids/game`, `heroes/game` or `logos/game` body; anything unreadable (or `success:false`) is empty, never a throw. */
    fun parseAssets(body: String?): List<SgdbAsset> = data(body).orEmpty().mapNotNull { e ->
        val a = e as? JsonObject ?: return@mapNotNull null
        SgdbAsset(
            id = a["id"].int() ?: return@mapNotNull null, url = a["url"].str()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null,
            width = a["width"].int() ?: 0, height = a["height"].int() ?: 0, mime = a["mime"].str(), score = a["score"].int() ?: 0,
            nsfw = a["nsfw"].bool(), humor = a["humor"].bool(), epilepsy = a["epilepsy"].bool(), style = a["style"].str(),
            thumb = a["thumb"].str()?.takeIf { it.isNotEmpty() },
        )
    }

    /**
     * The game whose name matches [name]: equal once both are normalised ([normaliseTitle]), else in [TitleMatch]'s
     * looser tier; within the tier verified first, then API order.
     */
    fun match(games: List<SgdbGame>, name: String): SgdbGame? {
        val same = TitleMatch.pick(games, name) { it.name }
        return same.firstOrNull { it.verified } ?: same.firstOrNull()
    }

    private val STATIC_MIMES = setOf("image/png", "image/jpeg")
    private val STATIC_EXTS = listOf(".png", ".jpg", ".jpeg")

    /** A still PNG / JPEG (never `.webm` / `.gif` / WebP), not tagged nsfw, humor or epilepsy. */
    private fun eligible(a: SgdbAsset): Boolean {
        val path = a.url.substringBefore('?').lowercase()
        val still = if (a.mime != null) a.mime.lowercase() in STATIC_MIMES else STATIC_EXTS.any { path.endsWith(it) }
        return still && !path.endsWith(".webm") && !path.endsWith(".gif") && !a.nsfw && !a.humor && !a.epilepsy
    }

    private val BY_SCORE = compareByDescending<SgdbAsset> { it.score }.thenBy { it.id }

    /** Portrait 2:3-ish: 1.3 <= height / width <= 1.7 (drops the horizontal 920x430 / 460x215 and the square grids). */
    fun portrait(a: SgdbAsset): Boolean = a.width > 0 && a.height * 10L >= a.width * 13L && a.height * 10L <= a.width * 17L

    /** Wide: width / height >= 2. */
    fun wide(a: SgdbAsset): Boolean = a.height > 0 && a.width.toLong() >= a.height * 2L

    /** Alternate style, still, untagged, portrait; score descending, then id ascending. Never by size. */
    fun rankGrids(assets: List<SgdbAsset>): List<SgdbAsset> =
        assets.filter { it.style == "alternate" && eligible(it) && portrait(it) }.sortedWith(BY_SCORE)

    /** Still, untagged, wide (any hero style); score descending, then id ascending. Never by size. */
    fun rankHeroes(assets: List<SgdbAsset>): List<SgdbAsset> =
        assets.filter { eligible(it) && wide(it) }.sortedWith(BY_SCORE)

    /** Still, untagged logos of any style and shape; score descending, then id ascending. Never by size. */
    fun rankLogos(assets: List<SgdbAsset>): List<SgdbAsset> = assets.filter { eligible(it) }.sortedWith(BY_SCORE)
}

/**
 * SteamGridDB as an [ArtSource]: the user's own API key ([key], from Tools → Artwork → Account; none = off), sent only
 * as the `Authorization: Bearer` header (never in a URL, so never logged). Per game one paced `search/autocomplete`,
 * then `grids/game` when [ArtSlot.GRID] is wanted, `heroes/game` when [ArtSlot.HERO] is (the engine's want rule) and
 * `logos/game` when [ArtSlot.WHEEL] is. 401 / 403 (the key refused) stop the source for the job, as 429 and 5xx do
 * when asked again once after [BUSY_WAIT_MS]; no connection pauses the job. The API publishes no rate limit: paced
 * [RATE_MS] like TheGamesDB. A job asks each request once ([answers]): regional variants share the search and the game.
 */
class SteamGridDbSource(
    private val http: ScraperHttp, private val key: () -> String?, now: () -> Long, private val sleep: suspend (Long) -> Unit,
) : ArtSource {
    companion object {
        const val RATE_MS = 1_000L
        const val TRIES = 3
        private val BACKOFF = listOf(2_000L, 5_000L)
        const val KEY_REFUSED = "SteamGridDB refused the key"
        const val TOO_MANY = "SteamGridDB: too many requests, try later"
        const val UNAVAILABLE = "SteamGridDB is unavailable right now"
        /** A 429 or 5xx is asked again once after this wait; a second one stops the source for the job. */
        const val BUSY_WAIT_MS = 5_000L
    }

    override val id = SourceId.SGDB
    override val label = "SteamGridDB"
    override val enabled: Boolean get() = !key().isNullOrEmpty()
    override val slots: Set<ArtSlot> = setOf(ArtSlot.GRID, ArtSlot.HERO, ArtSlot.WHEEL)
    /** SteamGridDB has no platform filter: any ROM game outside the excluded tags. */
    override fun knows(ref: LibraryGameRef): Boolean = !ref.android && ref.tag.trim().uppercase() !in ScraperSystems.EXCLUDED_TAGS

    private val pacer = Pacer(RATE_MS, now, sleep)
    private val calls = PacedGet(pacer, http, sleep, TRIES, BACKOFF, BUSY_WAIT_MS)
    /** A stop met on a game's hero or logo call after other assets were in hand: that game keeps them, the next call stops. */
    private val pendingStop = PendingStop()
    private val answers = JobAnswers()

    override suspend fun startJob() { pendingStop.clear(); answers.clear() }
    override suspend fun endJob() { answers.clear() }

    override suspend fun find(ref: LibraryGameRef, wanted: Set<ArtSlot>): List<ArtHit> {
        val k = key()?.ifEmpty { null } ?: return emptyList()
        if (slots.none { it in wanted }) return emptyList()
        pendingStop.throwIfHeld()
        val game = Sgdb.match(Sgdb.parseGames(api(Sgdb.search(TitleMatch.searchTerm(ref.name)), k)), ref.name) ?: return emptyList()
        val hits = ArrayList<ArtHit>()
        suspend fun add(slot: ArtSlot, url: String, rank: (List<SgdbAsset>) -> List<SgdbAsset>) {
            val body = try { api(url, k) } catch (e: SourceStop) { pendingStop.hold(e, hits); return }
            rank(Sgdb.parseAssets(body)).mapTo(hits) { ArtHit(slot, it.url, ArtOrigin(SourceId.SGDB, game.id.toString(), it.id.toString()), thumb = it.thumb) }
        }
        if (ArtSlot.GRID in wanted) add(ArtSlot.GRID, Sgdb.grids(game.id), Sgdb::rankGrids)
        if (ArtSlot.HERO in wanted && !pendingStop.held()) add(ArtSlot.HERO, Sgdb.heroes(game.id), Sgdb::rankHeroes)
        if (ArtSlot.WHEEL in wanted && !pendingStop.held()) add(ArtSlot.WHEEL, Sgdb.logos(game.id), Sgdb::rankLogos)
        return hits
    }

    /** Checks [candidate] with one paced search: 200 = OK, 401 / 403 = refused; the key is not stored here. */
    suspend fun check(candidate: String): KeyResult {
        val r = pacer.paced { http.get(Sgdb.search(Sgdb.CHECK_TERM), Sgdb.auth(candidate)) }
        return when (r.status) {
            200 -> KeyResult.OK
            401, 403 -> KeyResult.BAD_KEY
            -1 -> KeyResult.OFFLINE
            else -> KeyResult.UNAVAILABLE
        }
    }

    /** A paced call with the key's header (once per job: [answers]): the body of a 200, null for a not-found or other answer. */
    private suspend fun api(url: String, k: String): String? = answers.get("$k $url") {
        calls.get(
            url, Sgdb.auth(k), busyNote = { if (it == 429) TOO_MANY else UNAVAILABLE },
            stop = { if (it == 401 || it == 403) KEY_REFUSED else null },
        )?.decodeToString()
    }.first
}
