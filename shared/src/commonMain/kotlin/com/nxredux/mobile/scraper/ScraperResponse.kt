package com.nxredux.mobile.scraper

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

data class SsMedia(val type: String, val region: String?, val url: String)

sealed interface GameLookup {
    /** [id]: ScreenScraper's `jeu.id` (the written-art log's game id). */
    data class Found(val medias: List<SsMedia>, val id: String? = null, val info: SsGameInfo? = null) : GameLookup
    data object NotFound : GameLookup
}

/** The text facts of a `jeuInfos` game; [lang] is the language the synopsis (else the genre) was read in. */
data class SsGameInfo(val synopsis: String?, val genre: String?, val year: String?, val publisher: String?, val lang: String?) {
    val empty: Boolean get() = synopsis == null && genre == null && year == null && publisher == null
}

data class SsQuota(val today: Int, val max: Int)

/** One `jeuRecherche` result: every name ScreenScraper keeps for it (regional pairs included) and its media. */
data class SsGame(val names: List<String>, val medias: List<SsMedia>)

/** jeuInfos / ssuserInfos parsing (NX Redux `scraper_api.c:181-256, 377-378`); any failure is NotFound / null, never a throw. */
object ScraperResponse {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private fun parse(body: String?): JsonObject? =
        body?.let { runCatching { json.parseToJsonElement(it) as? JsonObject }.getOrNull() }

    private fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject
    private fun JsonElement?.str(): String? = (this as? JsonPrimitive)?.takeIf { it.isString }?.content   // JSON null = absent
    private fun JsonElement?.int(): Int? = (this as? JsonPrimitive)?.let { it.intOrNull ?: it.content.trim().toIntOrNull() }

    /** [body] is a JSON object (a `jeuInfos` answer without a game is a not-found, not an unreadable answer). */
    internal fun isJson(body: String?): Boolean = parse(body) != null

    fun parseGame(status: Int, body: String?): GameLookup {
        if (status != 200) return GameLookup.NotFound
        val jeu = parse(body)?.obj("response")?.obj("jeu") ?: return GameLookup.NotFound
        return GameLookup.Found(medias(jeu), jeu["id"].str() ?: jeu["id"].int()?.toString(), parseInfo(jeu))
    }

    /** Null when the game carries none of the four facts. */
    internal fun parseInfo(jeu: JsonObject): SsGameInfo? {
        val syn = localized(jeu["synopsis"])
        val genreNames = ((jeu["genres"] as? JsonArray).orEmpty().firstOrNull() as? JsonObject)?.get("noms")
        val genre = localized(genreNames)
        val year = (jeu["dates"] as? JsonArray).orEmpty()
            .mapNotNull { e -> (e as? JsonObject)?.get("text").str()?.trim()?.take(4)?.takeIf { y -> y.length == 4 && y.all(Char::isDigit) } }
            .minOrNull()
        val publisher = jeu.obj("editeur")?.get("text").str()?.let(::cleanText)?.takeIf { it.isNotEmpty() }
        val info = SsGameInfo(syn?.second, genre?.second, year, publisher, (syn ?: genre)?.first)
        return info.takeUnless { it.empty }
    }

    private val LANG_ORDER = listOf("en", "fr", "ja")

    /** The `{langue, text}` entry of [arr] in English, French, Japanese order, else the first usable one: (language, cleaned text). */
    private fun localized(arr: JsonElement?): Pair<String, String>? {
        val entries = (arr as? JsonArray).orEmpty().mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            val text = cleanText(o["text"].str() ?: return@mapNotNull null).takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            (o["langue"].str() ?: "") to text
        }
        return LANG_ORDER.firstNotNullOfOrNull { l -> entries.firstOrNull { it.first == l } } ?: entries.firstOrNull()
    }

    private fun medias(jeu: JsonObject): List<SsMedia> = (jeu["medias"] as? JsonArray).orEmpty().mapNotNull { e ->
        val o = e as? JsonObject ?: return@mapNotNull null
        val type = o["type"].str() ?: return@mapNotNull null
        val url = o["url"].str() ?: return@mapNotNull null
        SsMedia(type, o["region"].str(), url)
    }

    /** `jeuRecherche`'s ranked games; a game without a name (ScreenScraper's empty `{}` for no result) is left out. */
    fun parseSearch(status: Int, body: String?): List<SsGame> {
        if (status != 200) return emptyList()
        val jeux = parse(body)?.obj("response")?.get("jeux") as? JsonArray ?: return emptyList()
        return jeux.mapNotNull { e ->
            val jeu = e as? JsonObject ?: return@mapNotNull null
            val names = (jeu["noms"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.get("text").str() } +
                listOfNotNull(jeu["nom"].str())
            if (names.isEmpty()) null else SsGame(names, medias(jeu))
        }
    }

    /**
     * True for ScreenScraper's 403 text about the developer login ("Vérifier vos identifiants développeur"), not the
     * user's ("identifiants utilisateurs"). Matched without the accent, so a body decoded with the wrong charset still is.
     */
    fun isDevLoginError(body: String?): Boolean = body?.contains("veloppeur", ignoreCase = true) == true

    fun parseQuota(status: Int, body: String?): SsQuota? {
        if (status != 200) return null
        val u = parse(body)?.obj("response")?.obj("ssuser") ?: return null
        return SsQuota(u["requeststoday"].int() ?: return null, u["maxrequestsperday"].int() ?: return null)
    }
}

/**
 * NX Redux's preference (`scraper_api.c:116, 235-254`), per slot: its types in order (3D box `box-3D`, 2D box
 * `box-2D`, screenshot `ss`, wheel `wheel-hd` then `wheel`; no title screen); within each type regions us, wor, eu,
 * then the rest in API order. Never by pixel size.
 */
object ScraperMedia {
    val REGIONS = listOf("us", "wor", "eu")

    /** The ScreenScraper media types for [slot], best first; empty for the slots ScreenScraper does not fill (grid, hero). */
    fun typesOf(slot: ArtSlot): List<String> = when (slot) {
        ArtSlot.BOX_3D -> listOf("box-3D")
        ArtSlot.BOX_2D -> listOf("box-2D")
        ArtSlot.SCREENSHOT -> listOf("ss")
        ArtSlot.WHEEL -> listOf("wheel-hd", "wheel")
        ArtSlot.MIX -> listOf("mixrbv2", "mixrbv1")   // v2 first, the newer layout
        ArtSlot.GRID, ArtSlot.HERO -> emptyList()
    }

    /** Every media for [slot], best first: by type, then by region. */
    fun rank(medias: List<SsMedia>, slot: ArtSlot): List<SsMedia> = typesOf(slot).flatMap { type ->
        medias.filter { it.type == type }
            .sortedBy { m -> REGIONS.indexOf(m.region).let { if (it < 0) REGIONS.size else it } }   // stable: API order kept
    }

    /** The slots ScreenScraper fills, in [choose]'s order. */
    val SLOTS = listOf(ArtSlot.BOX_3D, ArtSlot.BOX_2D, ArtSlot.SCREENSHOT, ArtSlot.WHEEL, ArtSlot.MIX)

    /** The best media per slot ScreenScraper offers (absent slots left out). */
    fun choose(medias: List<SsMedia>): Map<ArtSlot, SsMedia> =
        SLOTS.mapNotNull { s -> rank(medias, s).firstOrNull()?.let { s to it } }.toMap()

    /**
     * Per slot ScreenScraper offers, every media of every one of its types (every region), ranked as [rank] ranks (the
     * wheel: each `wheel-hd`, then each `wheel`): the write takes the first that downloads, so a failed download falls
     * back to the next from the same answer, and the Change art list keeps them all.
     */
    fun alternates(medias: List<SsMedia>): Map<ArtSlot, List<SsMedia>> =
        SLOTS.mapNotNull { s -> rank(medias, s).takeIf { it.isNotEmpty() }?.let { s to it } }.toMap()

    /** The `ss` screenshot only (an Android game's art). */
    fun screenshotOnly(medias: List<SsMedia>): String? = rank(medias, ArtSlot.SCREENSHOT).firstOrNull()?.url
}

internal val TITLE_MARKS = Regex("[™®©]")
private val TITLE_SPACE = Regex("\\s+")
private val TITLE_DASH = Regex("\\s+-\\s+")
/** Latin letters with marks and ligatures, folded to plain ASCII (commonMain has no Unicode normaliser). */
internal val TITLE_FOLD: Map<Char, String> = buildMap {
    fun put(plain: String, marked: String) = marked.forEach { put(it, plain) }
    put("a", "àáâãäåāăą"); put("c", "çćĉċč"); put("d", "ďđ"); put("e", "èéêëēĕėęě"); put("g", "ĝğġģ"); put("h", "ĥħ")
    put("i", "ìíîïĩīĭįı"); put("j", "ĵ"); put("k", "ķ"); put("l", "ĺļľŀł"); put("n", "ñńņňŉ"); put("o", "òóôõöøōŏő")
    put("r", "ŕŗř"); put("s", "śŝşš"); put("t", "ţťŧ"); put("u", "ùúûüũūŭůűų"); put("w", "ŵ"); put("y", "ýÿŷ"); put("z", "źżž")
    put("ae", "æ"); put("oe", "œ"); put("ss", "ß"); put("th", "þ"); put("d", "ð")
    put("'", "\u2019")   // a curly apostrophe is a straight one
}

/**
 * A game title for an exact compare between a store label and ScreenScraper's names: case-folded, accents stripped,
 * ™ ® © removed, ’ read as ', `:` and ` - ` treated as the same, and whitespace collapsed.
 */
internal fun normaliseTitle(s: String): String {
    val folded = buildString { for (c in s.lowercase()) append(TITLE_FOLD[c] ?: c) }
    return folded.replace(TITLE_MARKS, " ").replace(':', ' ').replace(TITLE_DASH, " ").replace(TITLE_SPACE, " ").trim()
}

private val ENTITY = Regex("&(#[xX][0-9a-fA-F]+|#[0-9]+|[a-zA-Z]+);")
private val NAMED_ENTITIES = mapOf("amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'", "nbsp" to " ")

/** ScreenScraper text as plain display text: HTML entities decoded, CRLF as LF, trimmed. */
internal fun cleanText(s: String): String = ENTITY.replace(s) { m ->
    val body = m.groupValues[1]
    when {
        body.startsWith("#") -> {
            val code = if (body[1] == 'x' || body[1] == 'X') body.drop(2).toIntOrNull(16) else body.drop(1).toIntOrNull()
            if (code != null && code in 1..0x10FFFF) codePointString(code) else m.value
        }
        else -> NAMED_ENTITIES[body] ?: m.value
    }
}.replace("\r\n", "\n").trim()

private fun codePointString(cp: Int): String =
    if (cp < 0x10000) cp.toChar().toString()
    else (cp - 0x10000).let { "${(0xD800 + (it shr 10)).toChar()}${(0xDC00 + (it and 0x3FF)).toChar()}" }
