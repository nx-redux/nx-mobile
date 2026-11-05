package com.nxredux.mobile.scraper

/**
 * One kind of typed art and its `.media/<folder>/`. The 2D box is NX Redux's `boxart2d/` (the older `box2d/` is still
 * read, [LEGACY_BOX2D_FOLDER]); the wheel and the device-made mix are optional ([ArtExtras]). [GRID] and [HERO] are
 * SteamGridDB's hit kinds: written as a 2D box and a screenshot ([fileSlot]); their own `grid/` and `hero/` folders are
 * no longer read or written.
 */
enum class ArtSlot(val folder: String) {
    BOX_3D("boxart"), BOX_2D("boxart2d"), SCREENSHOT("screenshot"), GRID("grid"), HERO("hero"), WHEEL("wheel"), MIX("mix"),
}

/** The slot whose folder a hit of this kind is written to: a grid is a 2D box, a hero a screenshot; any other, itself. */
val ArtSlot.fileSlot: ArtSlot get() = when (this) {
    ArtSlot.GRID -> ArtSlot.BOX_2D
    ArtSlot.HERO -> ArtSlot.SCREENSHOT
    else -> this
}

/** The 2D box's older folder: still read (`boxart2d/` wins), never written; Reset deletes the app's files there. */
const val LEGACY_BOX2D_FOLDER = "box2d"

/**
 * Tools → Artwork → Settings' optional art (NX Redux `scraper_prefs.h`), all off by default: [box2d] the 2D box art as
 * an extra download beside the 3D box, [wheel] the game's logo, [mix] the handheld's mix picture (ScreenScraper's, else the
 * device-made one of the screenshot, box and wheel). With [mix] on a game has art only with its mix ([ArtHave.complete]).
 */
data class ArtExtras(val box2d: Boolean = false, val wheel: Boolean = false, val mix: Boolean = false) {
    val wantWheel: Boolean get() = wheel
}

/** How the app writes a fitted image; [ext] is the file extension. */
enum class ArtEncoding(val ext: String) { PNG("png"), JPEG("jpg") }

enum class ImageType { PNG, JPEG, WEBP, GIF, UNKNOWN }

/** A download's type by its magic bytes: `89 50 4E 47`, `FF D8 FF`, `RIFF....WEBP`, `GIF8`. */
fun imageTypeOf(bytes: ByteArray): ImageType {
    fun at(i: Int, vararg b: Int) = bytes.size >= i + b.size && b.indices.all { bytes[i + it] == b[it].toByte() }
    return when {
        at(0, 0x89, 0x50, 0x4E, 0x47) -> ImageType.PNG
        at(0, 0xFF, 0xD8, 0xFF) -> ImageType.JPEG
        at(0, 'R'.code, 'I'.code, 'F'.code, 'F'.code) && at(8, 'W'.code, 'E'.code, 'B'.code, 'P'.code) -> ImageType.WEBP
        at(0, 'G'.code, 'I'.code, 'F'.code, '8'.code) -> ImageType.GIF
        else -> ImageType.UNKNOWN
    }
}

/**
 * Fit within [maxW] x [maxH], never upscaled; [force] = always this encoding. Otherwise, with [photo] (scans are
 * photographic) PNG only for a PNG source or one with alpha, else JPEG; without it, as the source (PNG unless the source
 * is JPEG without alpha). [jpegQuality] for a JPEG encode.
 */
data class ArtCap(val maxW: Int, val maxH: Int, val force: ArtEncoding? = null, val jpegQuality: Int = 90, val photo: Boolean = false)

/**
 * The per-slot caps. Pixel size never filters or ranks a pick; it only bounds what is written. The 2D box and the wheel
 * as NX Redux stores them (`scraper_compositor.h`: full-colour PNG within 480x576, never grown); the mix is made at
 * exactly its canvas size ([MixCompositor]), so its cap only names the encoding.
 */
object ArtCaps {
    fun of(slot: ArtSlot): ArtCap = when (slot) {
        ArtSlot.BOX_3D -> ArtCap(900, 900, ArtEncoding.PNG)              // alpha cut-out
        ArtSlot.BOX_2D, ArtSlot.WHEEL -> ArtCap(480, 576, ArtEncoding.PNG)
        ArtSlot.MIX -> ArtCap(MixCompositor.W, MixCompositor.H, ArtEncoding.PNG)
        ArtSlot.SCREENSHOT -> ArtCap(1280, 960)                           // as the source: pixel art stays lossless
        ArtSlot.GRID -> ArtCap(600, 900, ArtEncoding.JPEG, 85)
        ArtSlot.HERO -> ArtCap(1920, 640, ArtEncoding.JPEG, 85)
    }
}

/** The pure numbers of the platform fitter ([ImageFitter]). */
object ArtFit {
    /** [w] x [h] fitted within the cap: at least 1x1, aspect kept, never upscaled. */
    fun size(w: Int, h: Int, cap: ArtCap): Pair<Int, Int> {
        if (w <= cap.maxW && h <= cap.maxH) return maxOf(1, w) to maxOf(1, h)
        val ww = w.toLong(); val hh = h.toLong()
        return if (ww * cap.maxH >= hh * cap.maxW) {   // width-bound
            cap.maxW to maxOf(1L, (hh * cap.maxW + ww / 2) / ww).toInt()
        } else {
            maxOf(1L, (ww * cap.maxH + hh / 2) / hh).toInt() to cap.maxH
        }
    }

    /**
     * The cap's forced encoding; else for a [ArtCap.photo] cap PNG for a PNG source or alpha, JPEG otherwise; else JPEG
     * for a JPEG source without alpha, PNG otherwise.
     */
    fun encoding(type: ImageType, hasAlpha: Boolean, cap: ArtCap): ArtEncoding = cap.force ?: when {
        cap.photo -> if (type == ImageType.PNG || hasAlpha) ArtEncoding.PNG else ArtEncoding.JPEG
        type == ImageType.JPEG && !hasAlpha -> ArtEncoding.JPEG
        else -> ArtEncoding.PNG
    }

    /** True when the download can be written as-is: within the cap and already in the target encoding. */
    fun passThrough(type: ImageType, w: Int, h: Int, hasAlpha: Boolean, cap: ArtCap): Boolean {
        if (w > cap.maxW || h > cap.maxH) return false
        return when (encoding(type, hasAlpha, cap)) {
            ArtEncoding.PNG -> type == ImageType.PNG
            ArtEncoding.JPEG -> type == ImageType.JPEG
        }
    }
}

/** Where a written file came from (the written-art log's origin): the source id, its game id and its asset id. */
data class ArtOrigin(val source: String, val gameId: String? = null, val assetId: String? = null)
