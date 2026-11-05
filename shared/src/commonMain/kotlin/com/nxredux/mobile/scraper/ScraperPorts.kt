package com.nxredux.mobile.scraper

import com.nxredux.mobile.library.ArtRefs
import com.nxredux.mobile.runner.ContentRef

/**
 * One GET's answer; [status] -1 = network error or timeout (no HTTP answer); [TOO_LARGE] = the body passed the platform's
 * cap and was dropped (no source treats it as a stop or a pause: an image is skipped, an API answer is not found).
 */
class HttpResult(val status: Int, val body: ByteArray?) {
    companion object { const val TOO_LARGE = 0 }
}

/**
 * A plain GET (gzip-aware on the platform) with optional request [headers]. URLs and headers carry credentials:
 * implementations never log them.
 */
interface ScraperHttp { suspend fun get(url: String, headers: Map<String, String> = emptyMap()): HttpResult }

/** A download fitted to its slot's cap, as it is written ([encoding] gives the file extension). */
class FittedImage(val bytes: ByteArray, val encoding: ArtEncoding)

/**
 * Decodes a download, downscales it to [ArtCap] (never up) and encodes it per [ArtFit.encoding]; a download already
 * within the cap and in its target encoding comes back as is ([ArtFit.passThrough]). Null = undecodable.
 */
interface ImageFitter { suspend fun fit(image: ByteArray, cap: ArtCap): FittedImage? }

/** The platform's pixels for the mix ([MixCompositor]): a decode to straight-alpha ARGB and a full-colour RGBA PNG encode; null = failed. */
interface PixelCodec {
    suspend fun decode(image: ByteArray): ArgbImage?
    suspend fun encodePng(image: ArgbImage): ByteArray?
}

interface RomHasher {
    /** CRC32 and size of [rom]; null when unreadable or larger than [ScreenScraperSource.HASH_LIMIT]. */
    suspend fun crc32AndSize(rom: ContentRef): Pair<Long, Long>?
    /**
     * CRC32 and size of the ROM inside the zip [zip] (its largest file); null when unreadable, not a zip, empty, or
     * the zip or that file is larger than [ScreenScraperSource.HASH_LIMIT].
     */
    suspend fun crc32AndSizeInZip(zip: ContentRef): Pair<Long, Long>?
}

/** Keeps a job alive (Android: the foreground service and its notification text). */
interface ScrapeRunner { fun keepAlive(active: Boolean, progress: String?) }

/**
 * Build-time ScreenScraper dev credentials (empty = ScreenScraper off), the user's optional ScreenScraper login, the
 * user's own TheGamesDB API key ([tgdbUserKey], null = TheGamesDB off; stored only once checked; the app ships none) and
 * the user's own SteamGridDB API key ([sgdbKey], null = SteamGridDB off; stored only once checked).
 */
interface ScraperCredentials {
    val devId: String
    val devPassword: String
    var tgdbUserKey: String?
        get() = null
        set(value) {}
    var sgdbKey: String?
        get() = null
        set(value) {}
    var user: String?
    var password: String?
}

/**
 * Which kinds of art a game has: [box] = boxart or box2d (a SteamGridDB grid is written as the 2D box); a
 * [screenshot] (a SteamGridDB hero is written as one: [heroShot]). It has art ([complete]) with both, and with its [mix]
 * when [needMix] (Mix on, [ArtExtras.mix]) and it has something to make one from ([mixInput]); a fetch asks
 * only for what it lacks. [box2d] and [wheel] (the optional extras) never count toward it beyond the box. [boxart]: the
 * 3D box (by default any [box]).
 */
data class ArtHave(
    val box: Boolean = false, val screenshot: Boolean = false,
    val box2d: Boolean = false, val wheel: Boolean = false, val mix: Boolean = false, val needMix: Boolean = false,
    val boxart: Boolean = box, val heroShot: Boolean = false,
) {
    /** A screenshot that is not a hero, or a box (3D or 2D): NX Redux's mix needs one of them, so only then is a mix needed. */
    val mixInput: Boolean get() = (screenshot && !heroShot) || boxart || box2d
    val complete: Boolean get() = box && screenshot && (!needMix || mix || !mixInput)

    companion object {
        fun of(art: ArtRefs, needMix: Boolean = false) = ArtHave(
            box = art.boxart != null || art.box2d != null, screenshot = art.screenshot != null,
            box2d = art.box2d != null, wheel = art.wheel != null, mix = art.mix != null, needMix = needMix, boxart = art.boxart != null,
            heroShot = art.heroShot,
        )
    }
}

/**
 * A library game as the scrape engine needs it; built by the host from the library index. [android]: an installed
 * Android game of the Android games console ([fileName] its app id, [name] its label, [gamePath] and [rom] its `app:`
 * ref, [have] `box` always and `screenshot` when one is stored, as it never wants a box, and while Mix is on its mix:
 * stored or not, from a screenshot that is not a hero or a 2D box): looked up by label and stored in the
 * [AndroidArtStore]. [datTitle]: an arcade set's raw DAT title (fbneo's table, or flycast's for a DC zip/7z;
 * libretro-thumbnails names arcade art by it), null otherwise. [art]: the index's current art refs (an extra folder's
 * own `.media` refs among them, which the engine keeps when it reports the game's art).
 */
data class LibraryGameRef(
    val gamePath: String, val tag: String, val name: String, val fileName: String, val rom: ContentRef,
    val sourceId: String, val relativeDir: String, val folderGame: Boolean, val have: ArtHave,
    val android: Boolean = false, val datTitle: String? = null, val art: ArtRefs = ArtRefs(),
)
