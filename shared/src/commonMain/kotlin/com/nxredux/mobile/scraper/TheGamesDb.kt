package com.nxredux.mobile.scraper

import com.nxredux.mobile.scraper.LenientJson.int
import com.nxredux.mobile.scraper.LenientJson.str
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

data class TgdbGame(val id: Int, val title: String, val platform: Int?)

/** One image of a game (`GameImage` in the API spec); [side] `front`/`back` for a boxart, else null. */
data class TgdbImage(val id: Int, val type: String, val side: String?, val filename: String, val resolution: String?)

/** `Games/ByGameName` with `include=boxart`: the games, their boxart by game id, the image base URL and the allowance left. */
data class TgdbGames(val games: List<TgdbGame>, val boxart: Map<Int, List<TgdbImage>>, val baseUrl: String?, val left: Int?)

/** `Games/Images`: images by game id, the base URL and the allowance left. */
data class TgdbImages(val images: Map<Int, List<TgdbImage>>, val baseUrl: String?, val left: Int?)

/**
 * TheGamesDB API v1's pure rules: the platform map keyed by the refined ScreenScraper id, the request URLs, lenient
 * parsers, the name match and the ranking. Shapes from the official spec (`https://api.thegamesdb.net/spec.yaml`).
 */
object Tgdb {
    const val API = "https://api.thegamesdb.net/v1"
    /** Used when an answer carries no `base_url` (the spec's documented CDN). */
    const val DEFAULT_ORIGINAL = "https://cdn.thegamesdb.net/images/original/"

    /** ScreenScraper ids TheGamesDB has no platform for (not asked). */
    val NONE: Set<Int> = setOf(222, 234, 263)

    /** By refined ScreenScraper id; ids from thegamesdb.net's platform list. */
    private val byId: Map<Int, String> = mapOf(
        1 to "18,36", 2 to "35", 3 to "7", 4 to "6", 6 to "23", 7 to "23", 8 to "23", 75 to "23", 9 to "4", 10 to "41",
        11 to "4918", 12 to "5", 13 to "2", 14 to "3", 15 to "8", 16 to "9", 17 to "4912", 19 to "33", 20 to "21", 21 to "20", 22 to "17", 23 to "16",
        25 to "4922", 82 to "4923", 26 to "22", 27 to "28", 28 to "4924", 29 to "25", 31 to "34", 114 to "4955", 105 to "34",
        72 to "4930", 40 to "26", 41 to "27", 42 to "4937", 43 to "4943", 45 to "4925", 46 to "4926", 48 to "31", 52 to "4950",
        57 to "10", 58 to "11", 61 to "13", 64 to "4911", 65 to "4914", 66 to "40", 70 to "4956", 73 to "4945", 76 to "4913", 79 to "4931",
        80 to "4928", 90 to "4948", 99 to "5007", 102 to "4939", 104 to "4927", 106 to "4936", 109 to "4949", 113 to "4929",
        116 to "4929", 115 to "32", 123 to "1", 135 to "1", 142 to "24", 207 to "4959", 211 to "4957", 240 to "5008",
    )

    /** The `filter[platform]` list for a game (comma separated), or null when TheGamesDB has none (not asked). */
    fun platforms(tag: String, fileName: String): String? {
        if (tag.trim().uppercase() in ScraperSystems.EXCLUDED_TAGS) return null
        return ScraperSystems.systemIdFor(tag, fileName)?.let { byId[it] }
    }

    fun byName(key: String, name: String, platforms: String): String =
        "$API/Games/ByGameName?apikey=${ssEncode(key)}&name=${ssEncode(name)}&filter%5Bplatform%5D=$platforms&include=boxart"

    /** A game's images of [types] (the API's comma-separated `filter[type]`: `screenshot`, `clearlogo`, ...). */
    fun images(key: String, gameId: Int, types: List<String> = listOf("screenshot")): String =
        "$API/Games/Images?apikey=${ssEncode(key)}&games_id=$gameId&filter%5Btype%5D=${types.joinToString("%2C")}"

    /** The allowance read; does not count against it. */
    fun limit(key: String): String = "$API/API/Limit?apikey=${ssEncode(key)}"

    private val BRACKETS = Regex("""\s*[(\[][^)\]]*[)\]]""")

    /** The name searched and matched: the shown name without bracketed text (region, revision, dump tags). */
    fun queryName(name: String): String = name.replace(BRACKETS, "").trim().ifEmpty { name.trim() }

    private fun parse(body: String?): JsonObject? = LenientJson.parseObject(body)
    private fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject

    /** `remaining_monthly_allowance` + `extra_allowance` of an answer; null when absent. */
    private fun left(o: JsonObject): Int? {
        val monthly = o["remaining_monthly_allowance"].int() ?: return null
        return monthly + (o["extra_allowance"].int() ?: 0)
    }

    private fun images(o: JsonObject?): Map<Int, List<TgdbImage>> = o?.entries?.mapNotNull { (k, v) ->
        val id = k.toIntOrNull() ?: return@mapNotNull null
        id to (v as? JsonArray).orEmpty().mapNotNull { e ->
            val i = e as? JsonObject ?: return@mapNotNull null
            TgdbImage(
                id = i["id"].int() ?: return@mapNotNull null, type = i["type"].str() ?: return@mapNotNull null, side = i["side"].str(),
                filename = i["filename"].str()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null, resolution = i["resolution"].str(),
            )
        }
    }?.toMap().orEmpty()

    /** A `Games/ByGameName` body; anything unreadable is empty, never a throw. */
    fun parseGames(body: String?): TgdbGames {
        val o = parse(body) ?: return TgdbGames(emptyList(), emptyMap(), null, null)
        val games = (o.obj("data")?.get("games") as? JsonArray).orEmpty().mapNotNull { e ->
            val g = e as? JsonObject ?: return@mapNotNull null
            TgdbGame(g["id"].int() ?: return@mapNotNull null, g["game_title"].str() ?: return@mapNotNull null, g["platform"].int())
        }
        val box = o.obj("include")?.obj("boxart")
        return TgdbGames(games, images(box?.obj("data")), box?.obj("base_url")?.get("original").str(), left(o))
    }

    /** A `Games/Images` body; anything unreadable is empty, never a throw. */
    fun parseImages(body: String?): TgdbImages {
        val o = parse(body) ?: return TgdbImages(emptyMap(), null, null)
        val data = o.obj("data")
        return TgdbImages(images(data?.obj("images")), data?.obj("base_url")?.get("original").str(), left(o))
    }

    /** An `API/Limit` body's allowance left, or null. */
    fun parseLimit(body: String?): Int? = parse(body)?.let(::left)

    /**
     * Whether an `API/Limit` 200 answers for a real key: the API answers an unknown key with 200 and
     * `{"remaining_monthly_allowance":0,"allowance_refresh_timer":0}` (no `extra_allowance`), a real one carries
     * `extra_allowance` and a running refresh timer (as the live API answers). Null = not an allowance answer.
     */
    fun keyAccepted(body: String?): Boolean? {
        val o = parse(body) ?: return null
        val monthly = o["remaining_monthly_allowance"].int() ?: return null
        return "extra_allowance" in o || monthly > 0 || (o["allowance_refresh_timer"].int() ?: 0) > 0
    }

    /**
     * The game whose title equals [name] once both are normalised ([normaliseTitle]), else matches it in
     * [TitleMatch]'s looser tier; within the tier the lowest id. [platforms] (the `filter[platform]` list): a game on
     * another platform never matches in any tier, as the API ignores a list filter and answers every platform (a
     * Genesis ROM got the SNES game of the same title).
     */
    fun match(games: List<TgdbGame>, name: String, platforms: String? = null): TgdbGame? {
        val allowed = platforms?.split(',')?.mapNotNull { it.trim().toIntOrNull() }?.toSet()
        val here = games.filter { allowed == null || it.platform == null || it.platform in allowed }
        return TitleMatch.pick(here, name) { it.title }.minByOrNull { it.id }
    }

    /** Front boxart only, by id ascending. Resolution never ranks or filters. */
    fun rankBoxart(images: List<TgdbImage>): List<TgdbImage> = images.filter { it.type == "boxart" && it.side == "front" }.sortedBy { it.id }

    /** Screenshots by id ascending. */
    fun rankScreens(images: List<TgdbImage>): List<TgdbImage> = images.filter { it.type == "screenshot" }.sortedBy { it.id }

    /** Clear logos (the wheel) by id ascending. */
    fun rankLogos(images: List<TgdbImage>): List<TgdbImage> = images.filter { it.type == "clearlogo" }.sortedBy { it.id }

    /** The original's URL (the smaller variants' sizes are undocumented, so only the original is known to be ≥ a cap). */
    fun imageUrl(baseUrl: String?, filename: String): String =
        (baseUrl?.takeIf { it.isNotEmpty() } ?: DEFAULT_ORIGINAL).let { if (it.endsWith('/')) it else "$it/" } + filename.removePrefix("/")
}

/**
 * TheGamesDB as an [ArtSource]; [key]: the user's own API key (Account; the app ships none); empty = off. The key
 * travels as the API's `apikey=` query parameter (the API takes no header), so URLs are never logged. One paced
 * `Games/ByGameName` (with the boxart) per game, then one `Games/Images` only when a screenshot is wanted and a game
 * matched. The wheel (its clear logo, [wheelLast]) rides on that screenshot call when wanted beside it, and costs a
 * call of its own only when asked for alone (the engine's last resort): the game's last match and logos are kept
 * ([last]), so that ask repeats neither the search nor a logo read. The allowance (per IP for a public key) is read
 * at job start with the free `API/Limit` and from every answer; at [STOP_AT] or fewer left, or on a 403 (bad key or
 * cap), the source stops for the job; 429 / 5xx stop it too when asked again once after [BUSY_WAIT_MS]; no
 * connection pauses the job. [left] is shared by the job and a single-game fetch: every read and write is under one
 * lock, and it belongs to the key it was read with (a changed key reads as unknown until its own read).
 */
class TheGamesDbSource(
    private val http: ScraperHttp, private val key: () -> String, now: () -> Long, private val sleep: suspend (Long) -> Unit,
) : ArtSource {
    companion object {
        const val RATE_MS = 1_000L
        const val STOP_AT = 1
        const val TRIES = 3
        private val BACKOFF = listOf(2_000L, 5_000L)
        const val LIMIT_REACHED = "TheGamesDB: monthly limit reached"
        const val UNAVAILABLE = "TheGamesDB is unavailable right now"
        /** A 429 or 5xx is asked again once after this wait; a second one stops the source for the job. */
        const val BUSY_WAIT_MS = 5_000L
    }

    override val id = SourceId.TGDB
    override val label = "TheGamesDB"
    override val enabled: Boolean get() = key().isNotEmpty()
    override val slots: Set<ArtSlot> = setOf(ArtSlot.BOX_2D, ArtSlot.SCREENSHOT, ArtSlot.WHEEL)
    override val wheelLast: Boolean get() = true
    override fun knows(ref: LibraryGameRef): Boolean = !ref.android && Tgdb.platforms(ref.tag, ref.fileName) != null

    private val pacer = Pacer(RATE_MS, now, sleep)
    private val calls = PacedGet(pacer, http, sleep, TRIES, BACKOFF, BUSY_WAIT_MS)
    private val lock = Mutex()
    private var _left: Int? = null
    private var leftKey: String? = null
    /** A stop met on a game's screenshot call after its box was found: that game keeps its box, the next call stops. */
    private val pendingStop = PendingStop()

    /** The last game asked about: its key and path, its match (null = none) and its logos once read (null = not read). */
    private class Last(val key: String, val gamePath: String, val game: TgdbGame?, val logos: List<ArtHit>?)
    private var last: Last? = null

    /** The requests left this month as last read (null before any read). */
    suspend fun left(): Int? { val k = key(); return lock.withLock { _left.takeIf { leftKey == k } } }

    private suspend fun setLeft(v: Int?) { if (v != null) { val k = key(); lock.withLock { _left = v; leftKey = k } } }

    /**
     * Checks [candidate] with the free `API/Limit`: a real key's allowance = OK, the API's unknown-key answer or a
     * 401 / 403 = refused; the key is not stored here.
     */
    suspend fun check(candidate: String): KeyResult {
        val r = pacer.paced { http.get(Tgdb.limit(candidate)) }
        return when (r.status) {
            200 -> when (Tgdb.keyAccepted(r.body?.decodeToString())) { true -> KeyResult.OK; false -> KeyResult.BAD_KEY; null -> KeyResult.UNAVAILABLE }
            401, 403 -> KeyResult.BAD_KEY
            -1 -> KeyResult.OFFLINE
            else -> KeyResult.UNAVAILABLE
        }
    }

    /** The free allowance read at the start of a job; a failed read leaves the last known value. */
    override suspend fun startJob() {
        pendingStop.clear()
        lock.withLock { last = null }
        allowance()
    }

    /** Reads the allowance with the free `API/Limit` (paced); null when it could not be read. */
    suspend fun allowance(): Int? {
        val k = key().ifEmpty { return null }
        val r = pacer.paced { http.get(Tgdb.limit(k)) }
        if (r.status != 200) return left()
        return Tgdb.parseLimit(r.body?.decodeToString())?.also { setLeft(it) } ?: left()
    }

    override suspend fun find(ref: LibraryGameRef, wanted: Set<ArtSlot>): List<ArtHit> {
        val k = key().ifEmpty { return emptyList() }
        pendingStop.throwIfHeld()
        val platforms = Tgdb.platforms(ref.tag, ref.fileName) ?: return emptyList()
        if (wanted == setOf(ArtSlot.WHEEL)) {
            val known = lock.withLock { last?.takeIf { it.key == k && it.gamePath == ref.gamePath } }
            if (known != null) {
                val game = known.game ?: return emptyList()
                known.logos?.let { return it }
                return logos(k, ref, game)
            }
        }
        val games = Tgdb.parseGames(api(Tgdb.byName(k, TitleMatch.searchTerm(ref.name), platforms)))
        setLeft(games.left)
        val game = Tgdb.match(games.games, ref.name, platforms)
        lock.withLock { last = Last(k, ref.gamePath, game, null) }
        if (game == null) return emptyList()
        if (wanted == setOf(ArtSlot.WHEEL)) return logos(k, ref, game)
        val hits = ArrayList<ArtHit>()
        if (ArtSlot.BOX_2D in wanted) {
            Tgdb.rankBoxart(games.boxart[game.id].orEmpty()).mapTo(hits) { hit(ArtSlot.BOX_2D, games.baseUrl, game, it) }
        }
        // The clear logo only beside a screenshot (no call of its own here: other sources' wheels come first).
        val withLogos = ArtSlot.SCREENSHOT in wanted && ArtSlot.WHEEL in wanted
        val types = listOfNotNull("screenshot".takeIf { ArtSlot.SCREENSHOT in wanted }, "clearlogo".takeIf { withLogos })
        if (types.isNotEmpty()) {
            val body = try { api(Tgdb.images(k, game.id, types)) } catch (e: SourceStop) { pendingStop.hold(e, hits); return hits }
            val images = Tgdb.parseImages(body)
            setLeft(images.left)
            val mine = images.images[game.id].orEmpty()
            if (ArtSlot.SCREENSHOT in wanted) Tgdb.rankScreens(mine).mapTo(hits) { hit(ArtSlot.SCREENSHOT, images.baseUrl, game, it) }
            if (withLogos) {
                val logos = Tgdb.rankLogos(mine).map { hit(ArtSlot.WHEEL, images.baseUrl, game, it) }
                hits += logos
                remember(k, ref, game, logos)
            }
        }
        return hits
    }

    /** [game]'s clear logos with one `Games/Images` call of their own (the wheel asked for alone), kept in [last]. */
    private suspend fun logos(k: String, ref: LibraryGameRef, game: TgdbGame): List<ArtHit> {
        val images = Tgdb.parseImages(api(Tgdb.images(k, game.id, listOf("clearlogo"))))
        setLeft(images.left)
        return Tgdb.rankLogos(images.images[game.id].orEmpty()).map { hit(ArtSlot.WHEEL, images.baseUrl, game, it) }
            .also { remember(k, ref, game, it) }
    }

    private suspend fun remember(k: String, ref: LibraryGameRef, game: TgdbGame, logos: List<ArtHit>) =
        lock.withLock { last = Last(k, ref.gamePath, game, logos) }

    private fun hit(slot: ArtSlot, base: String?, game: TgdbGame, img: TgdbImage) =
        ArtHit(slot, Tgdb.imageUrl(base, img.filename), ArtOrigin(SourceId.TGDB, game.id.toString(), img.id.toString()))

    /** A paced, counted call: stops first when the allowance is spent; the body of a 200, null for other answers. */
    private suspend fun api(url: String): String? = calls.get(
        url, busyNote = { UNAVAILABLE },
        stop = { if (it == 403) LIMIT_REACHED else null },   // bad API key or the cap (the API's only 403s)
        before = { left()?.let { if (it <= STOP_AT) throw SourceStop(LIMIT_REACHED) } },
    )?.decodeToString()
}
