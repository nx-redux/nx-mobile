package com.nxredux.mobile.ui

import com.nxredux.mobile.settings.BackdropArt
import com.nxredux.mobile.ui.nx.ConsolePad
import com.nxredux.mobile.ui.nx.consoleLogo
import com.nxredux.mobile.ui.nx.consolePad
import com.nxredux.mobile.ui.nx.toolIcon
import org.jetbrains.compose.resources.DrawableResource

/** What a Grid tile shows: a console's logo, a game's screenshot (or stand-in), a tool's icon and name, a collection's name. */
sealed interface GridTile {
    data class Logo(val res: DrawableResource?, val name: String, val count: Int? = null, val pad: ConsolePad? = null) : GridTile     // null res: the name as a title tile; count: its games; pad: its controller (Carousel only)
    /**
     * null urls: a plain title tile ([appId]: an Android game's, its app icon until the tile is measured); [heroUrl] / [gridUrl]: the stand-ins
     * ([tileArt]); [abstractUrl]: its generated picture ([abstractArtFor]), the tile's last fallback, drawn as a screenshot.
     */
    data class Game(
        val screenshotUrl: String?, val title: String, val appId: String? = null, val heroUrl: String? = null, val gridUrl: String? = null,
        val abstractUrl: String? = null,
        val resumeFrame: Boolean = false,   // the picture is Continue's resume frame: trimmed of its letterbox and zoomed, as Grid Home's card
    ) : GridTile
    data class Tool(val icon: DrawableResource, val lines: List<String>, val appId: String? = null) : GridTile   // appId: an Android app's, drawn with its own icon
    data class Collection(val name: String, val count: Int? = null) : GridTile   // count: its games; null when unknown
}

/**
 * Whether a game tile [width] x [height] dp shows no real art: [tileArt] without the abstract picture finds nothing (no
 * screenshot, and no SteamGridDB stand-in of the tile's shape), so it draws its abstract picture, an app icon or a title
 * tile. Such a tile's caption always shows ([com.nxredux.mobile.ui.nx.NxMenuTile]); one with real art shows it lit only.
 */
internal fun GridTile.Game.artLess(width: Float, height: Float): Boolean =
    tileArt(width, height, screenshotUrl, heroUrl, gridUrl) == null

/**
 * A tool's name as a tile shows it: "RetroAchievements" breaks as "Retro / Achievements" and "Cheats & Patches" as
 * "Cheats & / Patches", so neither runs to the tile's edges; the row keeps one line.
 */
internal fun toolDisplayLines(label: String): List<String> = when (label) {
    "RetroAchievements" -> listOf("Retro", "Achievements")
    "Cheats & Patches" -> listOf("Cheats &", "Patches")
    else -> listOf(label)
}

/** An Android game's app id (its file name), which its tile's icon is looked up by; null for a ROM. */
internal val Listed.appId: String? get() = game.fileName.takeIf { system.android }

internal fun gameGridTiles(entries: List<Listed>): List<GridTile.Game> = entries.map { gameTile(it, it.shown) }

private fun gameTile(e: Listed, title: String, shot: String? = null) =
    GridTile.Game(
        shot ?: e.game.screenshotRef?.value, title, e.appId, heroUrl = e.game.heroRef?.value, gridUrl = e.game.gridRef?.value, abstractUrl = abstractArtFor(e),
        resumeFrame = shot != null,   // a hidden save state's frame: trimmed and zoomed, as Continue's
    )

/**
 * A game's generated abstract picture ([abstractArtUrl], by its system's tag and its file name): every slot's last
 * fallback, after its real art (a tile's [tileArt], a background's [backgroundArt], the box chain's placeholder case,
 * Home's [continuePictures]). An Android game's is [abstractAppArtUrl] by its app id: seeded by [ANDROID_TAG] and the
 * app id (stable per app, so its shapes never match a ROM of the same name), in its launcher icon's colours with the
 * icon in the middle. Never art for fetching: the refs stay empty, so a game showing it still counts as missing.
 * Non-null for every game.
 */
internal fun abstractArtFor(e: Listed): String? =
    e.appId?.let(::abstractAppArtUrl) ?: abstractArtUrl(e.system.tag, e.game.fileName)

/**
 * Home's Continue card pictures, in fallback order (the next shows when one fails to load): the [resumeFrame] (its
 * hidden save state's frame, else the resume slot's: [continueFrame]), the
 * screenshot, the SteamGridDB hero, the grid (the tiles' stand-ins), and last the game's abstract picture
 * ([abstractArtFor]); empty only for a game with none, which the card draws as a title card.
 */
/** Continue's resume frame: the hidden save state's (Save & Quit's, [HomeShot]), else the resume slot's screenshot. */
internal fun continueFrame(row: MainRow.Continue): String? = row.shot ?: row.resume.previewPath

internal fun continuePictures(resumeFrame: String?, e: Listed): List<String> =
    listOfNotNull(resumeFrame, e.game.screenshotRef?.value, e.game.heroRef?.value, e.game.gridRef?.value, abstractArtFor(e)).distinct()

/**
 * A game list's full-screen picture for [e] (the List background, the Backdrop picture, either orientation):
 * [backgroundArt] (the screenshot, else the stand-in of the screen's shape), else its abstract picture
 * ([abstractBackgroundFor]: an Android game's without its icon), a box-only game's too (its real box stays in the box
 * slot).
 */
internal fun gameBackground(e: Listed, portrait: Boolean): String? =
    backgroundArt(e.game.screenshotRef?.value, e.game.heroRef?.value, e.game.gridRef?.value, portrait) ?: abstractBackgroundFor(e)

/**
 * A game's abstract picture as a full-screen background ([gameBackground]): an Android game's without its icon
 * ([abstractAppArtUrl] icon = false: the same palette and shapes; the Backdrop cover already shows the icon, and a
 * centred icon reads oddly behind a list); a ROM's as [abstractArtFor].
 */
internal fun abstractBackgroundFor(e: Listed): String? =
    e.appId?.let { abstractAppArtUrl(it, icon = false) } ?: abstractArtFor(e)

/**
 * A merged console row (SFC + SUPA) takes the first logo any of its tags has, as [menuBackground] does, and counts the
 * games of all its systems ([consoleInfoLines]' number). [controllerArt]: a console also carries its controller
 * ([consolePad], the first of its tags' as the logo), which the Carousel draws behind the focused logo; Grid and Home
 * leave it off.
 */
internal fun gridTiles(rows: List<MainRow>, controllerArt: Boolean = false): List<GridTile> = rows.map { r ->
    when (r) {
        is MainRow.System -> GridTile.Logo(
            r.systems.firstNotNullOfOrNull { consoleLogo(it.tag) }, r.system.displayName, r.systems.sumOf { it.gameCount },
            pad = if (controllerArt) r.systems.firstNotNullOfOrNull { consolePad(it.tag) } else null,
        )
        // Home's pinned and shelf games: their hidden save state's frame (Save & Quit's) first, else their screenshot.
        is MainRow.Pin -> gameTile(r.entry, r.entry.game.shownName, r.shot)
        is MainRow.Shelf -> gameTile(r.entry, r.entry.game.shownName, r.shot)
        is MainRow.Collection -> GridTile.Collection(r.name, r.count)
        is MainRow.Tool -> GridTile.Tool(toolIcon(r.row.kind), toolDisplayLines(r.row.label), r.row.appId)
        is MainRow.PinnedTool -> GridTile.Tool(toolIcon(r.row.kind), toolDisplayLines(r.row.label), r.row.appId)
        is MainRow.Continue, is MainRow.MoreTools, MainRow.PickAGame -> homeOnly(r)
    }
}

/**
 * [tiles] without their game counts: the Grid's reset identity, so a rescan that only changes counts neither snaps the
 * eased values nor drops a swipe's hold nor restarts the lit fade.
 */
internal fun gridTileIdentity(tiles: List<GridTile>): List<GridTile> = tiles.map { t ->
    when (t) {
        is GridTile.Logo -> if (t.count == null) t else t.copy(count = null)
        is GridTile.Collection -> if (t.count == null) t else t.copy(count = null)
        is GridTile.Game, is GridTile.Tool -> t
    }
}

/** A row only the Home tab has: the Home screens draw it (List, Grid, Carousel), so no other tab's style body is ever handed one. */
internal fun homeOnly(row: MainRow): Nothing = error("${row::class.simpleName} is a Home row: the Home screens draw it")

/**
 * What a Backdrop game shows: its box ([boxShown]: the 3D box art, else the 2D front [box2dUrl] or the grid [gridUrl]
 * in the case) over its screenshot as the backdrop; null urls are drawn plain. [tag]: its system's, for the console
 * logo on the placeholder box of a game without box art. [appId]: an Android game's, whose placeholder box shows the
 * app icon when it has no screenshot. [abstractUrl]: its generated picture ([abstractArtFor]): the placeholder case's cover.
 */
data class BackdropGame(
    val boxartUrl: String?, val screenshotUrl: String?, val title: String, val tag: String? = null, val appId: String? = null,
    val box2dUrl: String? = null, val gridUrl: String? = null, val abstractUrl: String? = null,
    val wheelUrl: String? = null, val art: BackdropArt = BackdropArt.BOX3D,
)

internal fun backdropGames(entries: List<Listed>, art: BackdropArt = BackdropArt.BOX3D): List<BackdropGame> =
    entries.map {
        BackdropGame(
            it.game.boxartRef?.value, it.game.screenshotRef?.value, it.shown, it.system.tag, it.appId,
            box2dUrl = it.game.box2dRef?.value, gridUrl = it.game.gridRef?.value, abstractUrl = abstractArtFor(it),
            wheelUrl = it.game.wheelRef?.value, art = art,
        )
    }

