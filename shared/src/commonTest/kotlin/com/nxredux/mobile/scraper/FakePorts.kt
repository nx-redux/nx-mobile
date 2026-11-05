package com.nxredux.mobile.scraper

import com.nxredux.mobile.library.ArtRefs
import com.nxredux.mobile.library.FakeRomsSource
import com.nxredux.mobile.library.RomsSource
import com.nxredux.mobile.runner.ContentRef
import com.nxredux.mobile.storage.GamePath
import kotlinx.coroutines.delay

/** Routes by URL substring (first match in insertion order); each route's last result repeats. Unrouted → 404. */
class FakeHttp(private val routes: MutableMap<String, ArrayDeque<HttpResult>> = linkedMapOf()) : ScraperHttp {
    val calls = mutableListOf<String>()
    /** Each call's request headers, in call order. */
    val headers = mutableListOf<Map<String, String>>()
    val times = mutableListOf<Long>()
    var clock: () -> Long = { 0L }
    /** Virtual time each request takes, so a job can be caught mid-flight. */
    var latency = 0L
    /** Extra virtual time for a URL containing the key (first match), on top of [latency]. */
    val delays = linkedMapOf<String, Long>()
    fun on(urlPart: String, vararg r: HttpResult) { routes.getOrPut(urlPart) { ArrayDeque() }.addAll(r) }
    fun set(urlPart: String, vararg r: HttpResult) { routes[urlPart] = ArrayDeque(r.toList()) }
    override suspend fun get(url: String, headers: Map<String, String>): HttpResult {
        calls += url; this.headers += headers; times += clock()
        if (latency > 0) delay(latency)
        delays.entries.firstOrNull { url.contains(it.key) }?.let { delay(it.value) }
        val key = routes.keys.firstOrNull { url.contains(it) } ?: return HttpResult(404, null)
        val q = routes.getValue(key); return if (q.size > 1) q.removeFirst() else q.first()
    }
    fun count(part: String) = calls.count { it.contains(part) }
}

/**
 * Writes the bytes as downloaded, in the cap's forced encoding, else JPEG for a JPEG download, else PNG (as
 * [ArtFit.encoding] for a source without alpha). [caps]: every cap asked for; [undecodable]: those downloads are null.
 */
class FakeFitter(var undecodable: (ByteArray) -> Boolean = { false }) : ImageFitter {
    val caps = mutableListOf<ArtCap>()
    override suspend fun fit(image: ByteArray, cap: ArtCap): FittedImage? {
        caps += cap
        if (image.isEmpty() || undecodable(image)) return null
        val type = imageTypeOf(image)
        return FittedImage(image, ArtFit.encoding(type, hasAlpha = type != ImageType.JPEG, cap))
    }
}

/**
 * The mix's codec without pixels: a download "decodes" to a 1x1 image whose pixel is its first byte ([decoded]: every
 * input, in order; [undecodable] ones are null); [encodePng] gives PNG's magic and "MIX" plus the pixels, or null when [failEncode].
 */
class FakePixels(var failEncode: Boolean = false, var undecodable: (ByteArray) -> Boolean = { false }) : PixelCodec {
    val decoded = mutableListOf<ByteArray>()
    val encoded = mutableListOf<ArgbImage>()
    override suspend fun decode(image: ByteArray): ArgbImage? {
        decoded += image
        return if (undecodable(image)) null else ArgbImage(1, 1, intArrayOf(image.first().toInt()))
    }
    override suspend fun encodePng(image: ArgbImage): ByteArray? {
        encoded += image
        return if (failEncode) null else byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47) + "MIX".encodeToByteArray()
    }
    companion object { val MIX_PNG = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47) + "MIX".encodeToByteArray() }
}

/**
 * A source with canned hits per game file name ([offer]); records what it was asked ([asked]) and when it started a
 * job ([starts]). [fail]: thrown instead of answering (a [SourceStop] or [JobPause]) when it returns non-null.
 */
class FakeSource(
    override val id: String, override val slots: Set<ArtSlot> = setOf(ArtSlot.BOX_2D, ArtSlot.SCREENSHOT),
    var on: Boolean = true, var knowsGame: (LibraryGameRef) -> Boolean = { true }, override val wheelLast: Boolean = false,
) : ArtSource {
    override val label = id
    override val enabled get() = on
    val asked = mutableListOf<Pair<String, Set<ArtSlot>>>()
    var starts = 0
    var fail: (LibraryGameRef) -> Exception? = { null }
    private val hits = mutableMapOf<String, List<ArtHit>>()
    fun offer(file: String, slot: ArtSlot, url: String, assetId: String = url, thumb: String? = null) {
        hits[file] = hits[file].orEmpty() + ArtHit(slot, url, ArtOrigin(id, file, assetId), thumb = thumb)
    }
    override fun knows(ref: LibraryGameRef) = knowsGame(ref)
    override suspend fun startJob() { starts++ }
    override suspend fun find(ref: LibraryGameRef, wanted: Set<ArtSlot>): List<ArtHit> {
        asked += ref.fileName to wanted
        fail(ref)?.let { throw it }
        return hits[ref.fileName].orEmpty().filter { it.slot in wanted }
    }
}

/** An engine with ScreenScraper as its first source (then [more]); the old engine's argument order. */
fun ssEngine(
    http: ScraperHttp, fitter: ImageFitter, hasher: RomHasher, source: RomsSource, home: ContentRef,
    auth: () -> ScraperAuth, now: () -> Long, sleep: suspend (Long) -> Unit,
    onArt: suspend (String, ArtRefs) -> Unit, onWritten: suspend (List<Pair<String, ArtOrigin?>>) -> Unit,
    androidArt: AndroidArtStore? = null, onAndroidArt: suspend () -> Unit = {}, more: List<ArtSource> = emptyList(),
    logged: LoggedArt = LoggedArt.NONE,
    onOptions: suspend (String, List<ArtOption>, Boolean) -> Unit = { _, _, _ -> }, picked: suspend (String) -> Set<ArtSlot> = { emptySet() },
    extras: () -> ArtExtras = { ArtExtras() }, pixels: PixelCodec? = null, warn: (String) -> Unit = {},
) = ScrapeEngine(
    http, fitter, source, home, listOf(ScreenScraperSource(http, hasher, auth, now, sleep)) + more, onArt, onWritten, androidArt, onAndroidArt, logged,
    extras = extras, pixels = pixels, warn = warn, onOptions = onOptions, picked = picked,
)

/** The written-art log as a set of paths; [origins]: each path's origin ([LoggedArt.origin]), a rig's writes recorded there. */
class FakeLoggedArt(private val paths: MutableSet<String>, val origins: MutableMap<String, ArtOrigin> = mutableMapOf()) : LoggedArt {
    override suspend fun contains(path: String) = path in paths
    override suspend fun remove(path: String) { paths -= path; origins -= path }
    override suspend fun origin(path: String): ArtOrigin? = origins[path]
    /** Records [written] origins (an engine's onWritten), as the app's log does before each write. */
    fun record(written: List<Pair<String, ArtOrigin?>>) { for ((p, o) in written) if (o != null) origins[p] = o }
}

class FakeHasher(var result: Pair<Long, Long>? = 0x1234L to 100L, var inZip: Pair<Long, Long>? = 0x5678L to 200L) : RomHasher {
    val asked = mutableListOf<ContentRef>()
    val askedInZip = mutableListOf<ContentRef>()
    override suspend fun crc32AndSize(rom: ContentRef): Pair<Long, Long>? { asked += rom; return result }
    override suspend fun crc32AndSizeInZip(zip: ContentRef): Pair<Long, Long>? { askedInZip += zip; return inZip }
}

/**
 * The Android games' pictures in memory: [files] the screenshots (by app id), [slots] every slot's picture, [heroes] the
 * app ids whose screenshot is a hero. A stored one's ref is `file:///art/<package>.png` (`.box2d.png`, `.wheel.png`).
 * [fail]: writes fail.
 */
class FakeArtStore(var fail: Boolean = false) : AndroidArtStore {
    val files = mutableMapOf<String, ByteArray>()
    val others = mutableMapOf<Pair<String, ArtSlot>, ByteArray>()
    val heroes = mutableSetOf<String>()
    private fun has(appId: String, slot: ArtSlot) = if (slot == ArtSlot.SCREENSHOT) appId in files else (appId to slot) in others
    override fun art(appId: String, slot: ArtSlot): ContentRef? {
        if (!has(appId, slot)) return null
        val suffix = when (slot) { ArtSlot.BOX_2D -> ".box2d"; ArtSlot.WHEEL -> ".wheel"; ArtSlot.MIX -> ".mix"; else -> "" }
        return ContentRef("file:///art/${appId.substringBefore('/')}$suffix.png")
    }
    override fun heroShot(appId: String): Boolean = appId in heroes
    override suspend fun write(appId: String, slot: ArtSlot, bytes: ByteArray, hero: Boolean): ContentRef? {
        if (fail) return null
        when (slot) {
            ArtSlot.SCREENSHOT -> { files[appId] = bytes; if (hero) heroes += appId else heroes -= appId }
            ArtSlot.BOX_2D, ArtSlot.WHEEL, ArtSlot.MIX -> others[appId to slot] = bytes
            else -> return null
        }
        return art(appId, slot)
    }
    override suspend fun read(appId: String, slot: ArtSlot): ByteArray? = if (slot == ArtSlot.SCREENSHOT) files[appId] else others[appId to slot]
    override suspend fun deleteAll(): Int = (files.size + others.size).also { files.clear(); others.clear(); heroes.clear() }
}

class FakeRunner : ScrapeRunner {
    val calls = mutableListOf<Pair<Boolean, String?>>()
    /** Called after each keepAlive with its text, so a test can act at an exact point of a job. */
    var hook: (String?) -> Unit = {}
    override fun keepAlive(active: Boolean, progress: String?) { calls += active to progress; hook(progress) }
}

class FakeCredentials(override val devId: String = "DEV", override val devPassword: String = "DEVPW") : ScraperCredentials {
    override var user: String? = null
    override var password: String? = null
    override var sgdbKey: String? = null
    override var tgdbUserKey: String? = null
}

class FakeHost(var games: List<LibraryGameRef>, var hidden: List<LibraryGameRef> = emptyList()) : ArtworkHost {
    val applied = mutableListOf<Pair<String, ArtRefs>>()
    val files = mutableMapOf<String, String>()
    /** When true for a game, applyArt throws (a failing index update). */
    var failApply: (String) -> Boolean = { false }
    /** When non-null for a game, applyArt throws it (an Error such as running out of memory). */
    var throwOnApply: (String) -> Throwable? = { null }
    override suspend fun libraryGames() = games
    /** As the app's host: the Android games console's games are left out (their art is not in the log). */
    override suspend fun allLibraryGames() = games.filterNot { it.android } + hidden
    /** Records the call and, like the real index, updates the game's [LibraryGameRef.have] and [LibraryGameRef.art] from the refs. */
    override suspend fun applyArt(gamePath: String, art: ArtRefs) {
        if (failApply(gamePath)) error("index update failed")
        throwOnApply(gamePath)?.let { throw it }
        applied += gamePath to art
        games = games.map { if (it.gamePath == gamePath) it.copy(have = ArtHave.of(art), art = art) else it }
        hidden = hidden.map { if (it.gamePath == gamePath) it.copy(have = ArtHave.of(art), art = art) else it }
    }
    /** Every whole-file write and append, in order: (name, text). */
    val writes = mutableListOf<Pair<String, String>>()
    val appends = mutableListOf<Pair<String, String>>()
    /** When true, [appendPrivate] appends (as Android does); false, it declines and the caller rewrites. */
    var appendable = false
    /** When true, [readPrivate] throws (an unreadable file). */
    var failRead = false
    override suspend fun readPrivate(name: String): String? { if (failRead) error("unreadable"); return files[name] }
    override suspend fun writePrivate(name: String, text: String) { files[name] = text; writes += name to text }
    override suspend fun appendPrivate(name: String, text: String): Boolean {
        if (!appendable) return false
        files[name] = files[name].orEmpty() + text; appends += name to text
        return true
    }
}

object Scrape {
    val OK_PNG = HttpResult(200, byteArrayOf(1, 2, 3))
    /** A download with JPEG's magic bytes. */
    val OK_JPEG = HttpResult(200, byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 4))
    fun json(s: String) = HttpResult(200, s.encodeToByteArray())
    /** A jeuInfos body (game id 42) with the given (type → url) media, all region us. */
    fun found(vararg media: Pair<String, String>) = json(
        """{"response":{"jeu":{"id":"42","medias":[${media.joinToString(",") { (t, u) -> """{"type":"$t","region":"us","url":"$u"}""" }}]}}}""",
    )
    /** A jeuInfos answer with a screenshot and a 2D box for every game: a full fetch. */
    val BOTH = found("ss" to "https://m/s.png", "box-2D" to "https://m/b.png")
    fun quota(today: Int, max: Int) = json("""{"response":{"ssuser":{"requeststoday":"$today","maxrequestsperday":"$max"}}}""")

    fun homeTree() = FakeRomsSource(
        dirs = mapOf("Home" to listOf("Roms"), "Home/Roms" to listOf("GB", "MD"), "Home/Roms/GB" to listOf("Tetris.gb", "Zelda.gb"), "Home/Roms/MD" to listOf("Sonic.md")),
    )
    val home = ContentRef("Home")

    /** A game with a box and a screenshot: has art. */
    val HAS_ART = ArtHave(box = true, screenshot = true)

    fun homeGame(file: String, tag: String = "GB", dir: String = "Roms/$tag", have: ArtHave = ArtHave()) = LibraryGameRef(
        gamePath = "/$dir/$file", tag = tag, name = file.substringBeforeLast('.'), fileName = file, rom = ContentRef("Home/$dir/$file"),
        sourceId = GamePath.HOME, relativeDir = dir, folderGame = false, have = have,
    )
    fun extraGame(file: String, tag: String, id: String = "x1", dir: String = "Genesis") = LibraryGameRef(
        gamePath = "/Extra/$id/$dir/$file", tag = tag, name = file.substringBeforeLast('.'), fileName = file, rom = ContentRef("Extra/$dir/$file"),
        sourceId = id, relativeDir = dir, folderGame = false, have = ArtHave(),
    )
    /** An installed Android game as the host lists it (its app id is the file name, its gamePath the `app:` ref). */
    fun androidGame(id: String, label: String, hasShot: Boolean = false) = LibraryGameRef(
        gamePath = "app:$id", tag = "ANDROID", name = label, fileName = id, rom = ContentRef("app:$id"),
        sourceId = "android", relativeDir = "", folderGame = false, have = ArtHave(box = true, screenshot = hasShot), android = true,
    )
    /** A jeuRecherche body: one game per entry, each its names and (type → url) media, all region us. */
    fun search(vararg games: Pair<List<String>, List<Pair<String, String>>>) = json(
        """{"response":{"jeux":[${games.joinToString(",") { (names, media) ->
            """{"noms":[${names.joinToString(",") { """{"region":"wor","text":"$it"}""" }}],"medias":[${media.joinToString(",") { (t, u) -> """{"type":"$t","region":"us","url":"$u"}""" }}]}"""
        }}]}}""",
    )
    fun item(g: LibraryGameRef, status: ItemStatus = ItemStatus.WAITING) = ScrapeItem(g.gamePath, g.tag, g.name, status)
}

/** The option images in memory, by key; a cached one's ref is `cache:<key>`. */
class FakeOptionCache : ArtOptionCache {
    val files = linkedMapOf<String, ByteArray>()
    override fun ref(key: String): ContentRef? = if (key in files) ContentRef("cache:$key") else null
    override suspend fun read(key: String): ByteArray? = files[key]
    override suspend fun write(key: String, bytes: ByteArray): ContentRef? { files[key] = bytes; return ref(key) }
    override suspend fun size(): Long = files.values.sumOf { it.size.toLong() }
    override suspend fun remove(keys: Collection<String>) { keys.forEach { files.remove(it) } }
    override suspend fun clear(): Long = size().also { files.clear() }
}
