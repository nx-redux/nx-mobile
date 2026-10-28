package com.nxredux.mobile.scraper

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * libretro-thumbnails' pure rules: the system map keyed by the refined ScreenScraper id, the name candidates,
 * RetroArch's thumbnail-name substitution and the URL path encoding.
 */
object Libretro {
    const val BASE = "https://thumbnails.libretro.com"

    object Folder {
        const val BOX = "Named_Boxarts"
        const val SNAP = "Named_Snaps"
    }

    private const val FBNEO = "FBNeo - Arcade Games"
    private const val MAME = "MAME"
    private val ARCADE = listOf(FBNEO, MAME)
    private val NAOMI = listOf("Sega - Naomi", "Sega - Naomi 2", "Atomiswave")
    private val DOOM_TAGS = setOf("PRBOOM", "DOOM")

    /** ScreenScraper ids libretro-thumbnails has no system for (not asked). */
    val NONE: Set<Int> = setOf(90, 234)

    /** By refined ScreenScraper id; names as the server's index lists them. */
    private val bySystem: Map<Int, List<String>> = mapOf(
        1 to listOf("Sega - Mega Drive - Genesis"), 2 to listOf("Sega - Master System - Mark III"),
        3 to listOf("Nintendo - Nintendo Entertainment System"), 4 to listOf("Nintendo - Super Nintendo Entertainment System"),
        9 to listOf("Nintendo - Game Boy"), 10 to listOf("Nintendo - Game Boy Color"), 11 to listOf("Nintendo - Virtual Boy"),
        12 to listOf("Nintendo - Game Boy Advance"), 14 to listOf("Nintendo - Nintendo 64"), 15 to listOf("Nintendo - Nintendo DS"),
        13 to listOf("Nintendo - GameCube"), 16 to listOf("Nintendo - Wii"), 17 to listOf("Nintendo - Nintendo 3DS"),
        19 to listOf("Sega - 32X"), 20 to listOf("Sega - Mega-CD - Sega CD"), 21 to listOf("Sega - Game Gear"),
        22 to listOf("Sega - Saturn"), 23 to listOf("Sega - Dreamcast"), 25 to listOf("SNK - Neo Geo Pocket"),
        82 to listOf("SNK - Neo Geo Pocket Color"), 26 to listOf("Atari - 2600"), 27 to listOf("Atari - Jaguar"),
        28 to listOf("Atari - Lynx"), 29 to listOf("The 3DO Company - 3DO"), 31 to listOf("NEC - PC Engine - TurboGrafx 16"),
        114 to listOf("NEC - PC Engine CD - TurboGrafx-CD"), 105 to listOf("NEC - PC Engine SuperGrafx"), 72 to listOf("NEC - PC-FX"),
        40 to listOf("Atari - 5200"), 41 to listOf("Atari - 7800"), 42 to listOf("Atari - ST"), 43 to listOf("Atari - 8-bit"),
        45 to listOf("Bandai - WonderSwan"), 46 to listOf("Bandai - WonderSwan Color"), 48 to listOf("Coleco - ColecoVision"),
        52 to listOf("Handheld Electronic Game"), 57 to listOf("Sony - PlayStation"), 58 to listOf("Sony - PlayStation 2"), 61 to listOf("Sony - PlayStation Portable"),
        64 to listOf("Commodore - Amiga"), 65 to listOf("Amstrad - CPC"), 66 to listOf("Commodore - 64"), 70 to listOf("SNK - Neo Geo CD"),
        73 to listOf("Commodore - VIC-20"), 76 to listOf("Sinclair - ZX Spectrum"), 79 to listOf("Sharp - X68000"),
        80 to listOf("Fairchild - Channel F"), 99 to listOf("Commodore - Plus-4"), 102 to listOf("GCE - Vectrex"),
        104 to listOf("Magnavox - Odyssey2"), 106 to listOf("Nintendo - Family Computer Disk System"), 109 to listOf("Sega - SG-1000"),
        113 to listOf("Microsoft - MSX"), 116 to listOf("Microsoft - MSX2"), 115 to listOf("Mattel - Intellivision"),
        123 to listOf("ScummVM"), 135 to listOf("DOS"), 142 to listOf("SNK - Neo Geo", FBNEO),
        6 to ARCADE, 7 to ARCADE, 8 to ARCADE, 75 to ARCADE,
        207 to listOf("Watara - Supervision"), 211 to listOf("Nintendo - Pokemon Mini"), 222 to listOf("TIC-80"),
        240 to listOf("Commodore - PET"), 263 to listOf("Arduboy Inc - Arduboy"),
    )

    /**
     * The libretro systems to try for a game, in order; empty = not asked. A DC zip/7z (refined to arcade) is a NAOMI or
     * Atomiswave set; the PrBoom / DOOM tags use `DOOM`; an excluded tag or the Android console has none.
     */
    fun systems(tag: String, fileName: String): List<String> {
        val t = tag.trim().uppercase()
        if (t in ScraperSystems.EXCLUDED_TAGS) return emptyList()
        val id = ScraperSystems.systemIdFor(t, fileName) ?: return emptyList()
        return when {
            id == 75 && ScraperSystems.baseIdFor(t) == 23 -> NAOMI
            id == 135 && t in DOOM_TAGS -> listOf("DOOM")
            else -> bySystem[id].orEmpty()
        }
    }

    /** RetroArch's thumbnail-name rule: each of `& * / : ` < > ? \ | "` becomes `_`. */
    fun clean(name: String): String = buildString(name.length) {
        for (c in name) append(if (c in "&*/:`<>?\\|\"") '_' else c)
    }

    /** A URL path segment: UTF-8, unreserved `A-Z a-z 0-9 - . _ ~` kept, every other byte `%XX` (uppercase; a space `%20`). */
    fun pathEncode(s: String): String = percentEncode(s, spaceAsPlus = false)

    fun url(system: String, folder: String, name: String): String = "$BASE/${pathEncode(system)}/$folder/${pathEncode(clean(name))}.png"

    private val SQUARE = Regex("""\s*\[[^\]]*\]""")
    private val DISC = Regex("""\(Disc \d+\)""", RegexOption.IGNORE_CASE)

    /**
     * The names to try, at most three, distinct, in order: the DAT title (an arcade set), else the file's stem (a folder
     * game: its folder's name); that name without `[...]` groups (GoodTools tags), when different; for an `.m3u` or a
     * folder game without `(Disc N)`, the name plus ` (Disc 1)`.
     */
    fun candidates(ref: LibraryGameRef): List<String> {
        val first = ref.datTitle?.takeIf { it.isNotBlank() }
            ?: if (ref.folderGame) ref.relativeDir.split('/').last { it.isNotEmpty() } else ArtPaths.base(ref.fileName)
        val bare = first.replace(SQUARE, "").trim()
        val multiDisc = ref.folderGame || ref.fileName.lowercase().endsWith(".m3u")
        val disc1 = if (multiDisc && !DISC.containsMatchIn(bare)) "$bare (Disc 1)" else null
        return listOfNotNull(first, bare.takeIf { it.isNotEmpty() }, disc1).distinct().take(3)
    }

    /** The server's directory index of [system]'s [folder] (Apache's plain list, `F=0`: smaller than the table). */
    fun listingUrl(system: String, folder: String): String = "$BASE/${pathEncode(system)}/$folder/?F=0"

    private val HREF = Regex("""<a\s+href="([^"]*)"""", RegexOption.IGNORE_CASE)

    /**
     * The image names (no `.png`) of a directory index page: each relative `href` ending in `.png`, percent-decoded as
     * UTF-8 (a `+` is literal in a path) and HTML-unescaped; the parent link, sort links and other files are dropped.
     */
    fun listingNames(html: String): List<String> = HREF.findAll(html).mapNotNull { m ->
        val href = unescapeHtml(m.groupValues[1])
        if (href.startsWith("/") || href.startsWith("?") || "://" in href || !href.endsWith(".png", ignoreCase = true)) return@mapNotNull null
        percentDecode(href.dropLast(4)).takeIf { it.isNotBlank() }
    }.distinct().toList()

    private val GROUP = Regex("""\s*(\([^)]*\)|\[[^\]]*\])""")
    private val ARTICLE = Regex("""^(.+?), (The|A|An)( - .*)?$""", RegexOption.IGNORE_CASE)
    private val NON_ALNUM = Regex("""[^\p{L}\p{N}]+""")
    private val FOLD = mapOf(
        'à' to 'a', 'á' to 'a', 'â' to 'a', 'ã' to 'a', 'ä' to 'a', 'å' to 'a', 'è' to 'e', 'é' to 'e', 'ê' to 'e', 'ë' to 'e',
        'ì' to 'i', 'í' to 'i', 'î' to 'i', 'ï' to 'i', 'ò' to 'o', 'ó' to 'o', 'ô' to 'o', 'õ' to 'o', 'ö' to 'o', 'ø' to 'o',
        'ù' to 'u', 'ú' to 'u', 'û' to 'u', 'ü' to 'u', 'ñ' to 'n', 'ç' to 'c', 'ý' to 'y', 'ÿ' to 'y',
    )

    /**
     * A name's match key: every `(...)` and `[...]` group dropped, a No-Intro trailing article moved to the front
     * ("Legend of Zelda, The - A Link to the Past" = "The Legend of Zelda - A Link to the Past"), RetroArch's
     * substitution applied, case and common accents folded, apostrophes dropped ("Diddys" = "Diddy's"), every run of
     * punctuation or spaces one space (so " - ", ": " and "_" agree) and the Roman numerals II-IV and VI-IX as digits
     * ("Mortal Kombat 2" = "Mortal Kombat II"; V and X stay words: "Mega Man X"). "" when nothing is left.
     */
    fun matchKey(name: String): String {
        val bare = name.replace(GROUP, "").trim()
        val front = ARTICLE.matchEntire(bare)?.let { m -> "${m.groupValues[2]} ${m.groupValues[1]}${m.groupValues[3]}" } ?: bare
        val folded = buildString(front.length) {
            for (c in clean(front).lowercase()) if (c != '\'' && c != '\u2019') append(FOLD[c] ?: c)
        }
        return folded.replace(NON_ALNUM, " ").trim().split(' ').joinToString(" ") { ROMAN[it] ?: it }
    }

    private val ROMAN = mapOf("ii" to "2", "iii" to "3", "iv" to "4", "vi" to "6", "vii" to "7", "viii" to "8", "ix" to "9")

    private val TAG_GROUP = Regex("""\(([^)]*)\)""")
    private val REGIONS = listOf("usa", "world", "europe", "japan")
    private val UNWANTED = Regex("""\b(beta|proto|prototype|demo|sample|hack|pirate|unl|unlicensed)\b|^\[b\d*\]$""", RegexOption.IGNORE_CASE)

    /** A listed name's region rank: USA 0, World 1, Europe 2, Japan 3 (by its best region), any other 4. */
    fun regionRank(name: String): Int {
        val words = TAG_GROUP.findAll(name).flatMap { g -> g.groupValues[1].split(',').map { it.trim().lowercase() } }.toSet()
        return REGIONS.indexOfFirst { it in words }.let { if (it < 0) REGIONS.size else it }
    }

    /** It carries a Beta / Proto / Demo / Sample / Hack / Pirate / Unl tag (in a `(...)` or `[...]` group) or a `[b]` bad dump. */
    fun unwanted(name: String): Boolean = GROUP.findAll(name).any { UNWANTED.containsMatchIn(it.value.trim()) }

    /** A name list by [matchKey], by [TitleMatch.loose] and by [TitleMatch.head] (names in listing order). */
    class NameIndex(val exact: Map<String, List<String>>, val loose: Map<String, List<String>>, val heads: Map<String, List<String>>)

    /** The listing's [NameIndex]; names without a key are left out. */
    fun index(names: List<String>): NameIndex = NameIndex(
        exact = names.groupBy { matchKey(it) }.filterKeys { it.isNotEmpty() },
        loose = names.groupBy { TitleMatch.loose(it) }.filterKeys { it.isNotEmpty() },
        heads = names.mapNotNull { n -> TitleMatch.head(n)?.let { it to n } }.groupBy({ it.first }, { it.second }),
    )

    /**
     * The best listed name for [name] in [index], or null: names with the same [matchKey], else the same
     * [TitleMatch.loose] key, else a [TitleMatch.head] equal to it (only when those are one title); ranked by no
     * unwanted tag first, then region (USA, World, Europe, Japan, others), then the shortest name, then alphabetically.
     */
    fun bestListed(name: String, index: NameIndex): String? {
        val key = matchKey(name).ifEmpty { return null }
        val names = index.exact[key] ?: TitleMatch.loose(name).let { loose ->
            index.loose[loose] ?: index.heads[loose]?.takeIf { TitleMatch.oneTitle(it) }
        } ?: return null
        return names.minWithOrNull(compareBy<String>({ unwanted(it) }, { regionRank(it) }, { it.length }, { it }))
    }

    /** `%XX` escapes decoded as UTF-8 bytes; any other text (and a malformed escape) kept as is. */
    private fun percentDecode(s: String): String {
        if ('%' !in s) return s
        val out = ArrayList<Byte>(s.length)
        var i = 0
        while (i < s.length) {
            val hex = if (s[i] == '%' && i + 3 <= s.length) s.substring(i + 1, i + 3).toIntOrNull(16) else null
            if (hex != null) { out += hex.toByte(); i += 3; continue }
            var j = i + 1
            while (j < s.length && s[j] != '%') j++
            for (b in s.substring(i, j).encodeToByteArray()) out += b
            i = j
        }
        return out.toByteArray().decodeToString()
    }

    private fun unescapeHtml(s: String): String = if ('&' !in s) s else s
        .replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&#39;", "'").replace("&#x27;", "'")
        .replace("&amp;", "&")
}

/**
 * libretro-thumbnails as an [ArtSource]: no account; probes `Named_Boxarts` (2D box) and `Named_Snaps` (screenshot)
 * per system and name candidate with a paced GET; the first image answer is the hit and its bytes are handed to the
 * engine (no second download). When every candidate misses (an untagged "Advance Wars.gba" against libretro's
 * "Advance Wars (USA)"), the system's name list for that folder (its directory index, read once per job and kept for
 * the job) is matched by [Libretro.matchKey], then [TitleMatch]'s looser tier, and the [Libretro.bestListed] name
 * fetched. 404 = the next candidate; 429 / 5xx = asked again once after [BUSY_WAIT_MS], then the source stops for the
 * job; no connection after [TRIES] tries pauses the job.
 */
class LibretroThumbnails(
    private val http: ScraperHttp, now: () -> Long, private val sleep: suspend (Long) -> Unit,
) : ArtSource {
    companion object {
        const val RATE_MS = 250L
        const val TRIES = 3
        private val BACKOFF = listOf(2_000L, 5_000L)
        const val UNAVAILABLE = "libretro-thumbnails is unavailable right now"
        /** A 429 or 5xx is asked again once after this wait; a second one stops the source for the job. */
        const val BUSY_WAIT_MS = 5_000L
    }

    override val id = SourceId.LIBRETRO
    override val label = "libretro-thumbnails"
    override val shortLabel = "libretro"
    override val enabled = true
    override val slots: Set<ArtSlot> = setOf(ArtSlot.BOX_2D, ArtSlot.SCREENSHOT)
    override fun knows(ref: LibraryGameRef): Boolean = !ref.android && Libretro.systems(ref.tag, ref.fileName).isNotEmpty()

    private val calls = PacedGet(Pacer(RATE_MS, now, sleep), http, sleep, TRIES, BACKOFF, BUSY_WAIT_MS)
    private val listLock = Mutex()
    /** "system/folder" -> its name list by match key, for this job (an unreadable list is kept as empty). */
    private val lists = HashMap<String, Libretro.NameIndex>()

    override suspend fun startJob() { listLock.withLock { lists.clear() } }

    override suspend fun find(ref: LibraryGameRef, wanted: Set<ArtSlot>): List<ArtHit> {
        if (ref.android) return emptyList()
        val systems = Libretro.systems(ref.tag, ref.fileName)
        val names = Libretro.candidates(ref)
        val hits = ArrayList<ArtHit>()
        for ((slot, folder) in listOf(ArtSlot.BOX_2D to Libretro.Folder.BOX, ArtSlot.SCREENSHOT to Libretro.Folder.SNAP)) {
            if (slot !in wanted) continue
            (probe(systems, names, slot, folder) ?: fromList(systems, names.first(), slot, folder))?.let { hits += it }
        }
        return hits
    }

    /** The first system x name that answers with an image. */
    private suspend fun probe(systems: List<String>, names: List<String>, slot: ArtSlot, folder: String): ArtHit? {
        for (system in systems) for (name in names) fetch(system, folder, name, slot)?.let { return it }
        return null
    }

    /** The first system whose name list has a match for [name] that answers with an image. */
    private suspend fun fromList(systems: List<String>, name: String, slot: ArtSlot, folder: String): ArtHit? {
        if (Libretro.matchKey(name).isEmpty()) return null
        for (system in systems) {
            val listed = Libretro.bestListed(name, list(system, folder)) ?: continue
            fetch(system, folder, listed, slot)?.let { return it }
        }
        return null
    }

    private suspend fun fetch(system: String, folder: String, name: String, slot: ArtSlot): ArtHit? {
        val url = Libretro.url(system, folder, name)
        val body = get(url) ?: return null
        if (imageTypeOf(body) == ImageType.UNKNOWN) return null
        return ArtHit(slot, url, ArtOrigin(SourceId.LIBRETRO, gameId = system, assetId = "$folder/$name"), body)
    }

    /** [system]'s [folder] name list, read once per job (paced, as any GET). */
    private suspend fun list(system: String, folder: String): Libretro.NameIndex = listLock.withLock {
        lists.getOrPut("$system/$folder") {
            Libretro.index(get(Libretro.listingUrl(system, folder))?.let { Libretro.listingNames(it.decodeToString()) }.orEmpty())
        }
    }

    /** A paced GET: the body of a 200 (null when empty), null for any other answer; throws for the stop / pause cases. */
    private suspend fun get(url: String): ByteArray? = calls.get(url, busyNote = { UNAVAILABLE })?.takeIf { it.isNotEmpty() }
}
