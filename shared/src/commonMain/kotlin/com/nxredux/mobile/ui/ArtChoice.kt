package com.nxredux.mobile.ui

import com.nxredux.mobile.library.GameEntry
import com.nxredux.mobile.settings.BackdropArt

/** What a game's box slot draws: the 3D box art, a flat 2D cover in the case, a wheel fitted whole, or the placeholder case. */
sealed interface BoxShown {
    data class Art3d(val url: String) : BoxShown
    data class Cover(val url: String) : BoxShown
    /** A wheel (logo) whole, fitted in the slot: never cut into a case. */
    data class Wheel(val url: String) : BoxShown
    data object Placeholder : BoxShown
}

/**
 * The box chain: the [prefer]red kind (Layouts → Backdrop art) first, then the 3D box art, the 2D box (drawn in the
 * case) and the wheel (fitted whole); none of them, the placeholder. Never the screenshot. [failed]: urls that did not
 * load, skipped so a broken file falls to the next link, never a hole.
 */
internal fun boxShown(prefer: BackdropArt, boxart: String?, box2d: String?, wheel: String?, failed: Set<String> = emptySet()): BoxShown {
    fun ok(url: String?) = url?.takeIf { it !in failed }
    val art3d = ok(boxart)?.let { BoxShown.Art3d(it) }
    val cover = ok(box2d)?.let { BoxShown.Cover(it) }
    val logo = ok(wheel)?.let { BoxShown.Wheel(it) }
    val first = when (prefer) {
        BackdropArt.BOX3D -> art3d
        BackdropArt.BOX2D -> cover
        BackdropArt.WHEEL -> logo
    }
    return first ?: art3d ?: cover ?: logo ?: BoxShown.Placeholder
}

/**
 * The game panel's header picture (the launcher hands it to the game screen): the [boxShown] chain's picture, else
 * [androidShot] (an Android game's screenshot, passed only for one: its single stored picture); null draws the console
 * logo. A ROM's screenshot is never the header.
 */
internal fun panelArt(prefer: BackdropArt, boxart: String?, box2d: String?, wheel: String?, androidShot: String? = null): String? =
    when (val shown = boxShown(prefer, boxart, box2d, wheel)) {
        is BoxShown.Art3d -> shown.url
        is BoxShown.Cover -> shown.url
        is BoxShown.Wheel -> shown.url
        BoxShown.Placeholder -> androidShot
    }

/**
 * An Android game's screenshot (its single stored picture), its box and header's last fallback ([panelArt]'s
 * androidShot); null for a ROM, whose screenshot is never a box or the header.
 */
internal val GameEntry.androidShot: String? get() = screenshotRef?.value?.takeIf { sourceId == ANDROID_SOURCE }

/** [panelArt] for [game]: its boxes and wheel by [prefer], else its [androidShot]. */
internal fun panelArt(prefer: BackdropArt, game: GameEntry): String? =
    panelArt(prefer, game.boxartRef?.value, game.box2dRef?.value, game.wheelRef?.value, game.androidShot)

/**
 * A full-screen background: the game's screenshot only (no grid or hero stand-in). A SteamGridDB hero saved as the
 * screenshot ([heroShot]) is wide art: never on a [portrait] background, which then is the plain one.
 */
internal fun backgroundArt(screenshot: String?, heroShot: Boolean, portrait: Boolean): String? =
    screenshot?.takeUnless { heroShot && portrait }

/**
 * How a tile draws its picture: [CROP] scales it to fill and centre-crops (a screenshot, the abstract art); [HERO]
 * scales a SteamGridDB hero to the tile's full height (no top or bottom cut) and anchors it right, where a hero's
 * subject usually is, the left part running off the tile.
 */
enum class TileFit { CROP, HERO }

/** A tile's picture: its [url] and how it fills the tile ([fit]). */
data class TileArt(val url: String, val fit: TileFit)

/**
 * A Grid, Carousel or Home pin game tile's picture: the screenshot only (never a box); a hero screenshot ([heroShot])
 * drawn [TileFit.HERO] in a [width] x [height] tile wider than tall, skipped in a taller or square one; last, the
 * game's generated [abstract] picture, so a tile is never a plain title tile. An unmeasured tile (0 x 0) takes only a
 * plain screenshot. The Home Continue card has its own chain ([continuePictures]); RetroAchievements and Game Time stay
 * screenshot-only.
 */
internal fun tileArt(width: Float, height: Float, screenshot: String?, heroShot: Boolean, abstract: String? = null): TileArt? {
    if (screenshot != null && !heroShot) return TileArt(screenshot, TileFit.CROP)
    if (width <= 0f || height <= 0f) return null
    if (screenshot != null && width > height) return TileArt(screenshot, TileFit.HERO)
    return abstract?.let { TileArt(it, TileFit.CROP) }
}

/** A Backdrop row's box: [boxShown] by Layouts → Backdrop art ([BackdropGame.art]); [failed]: files that did not load. */
internal fun backdropBoxShown(game: BackdropGame, failed: Set<String> = emptySet()): BoxShown =
    boxShown(game.art, game.boxartUrl, game.box2dUrl, game.wheelUrl, failed)
