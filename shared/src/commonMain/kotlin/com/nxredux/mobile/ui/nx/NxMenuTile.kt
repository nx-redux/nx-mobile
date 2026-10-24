package com.nxredux.mobile.ui.nx

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.nxredux.mobile.ui.GridTile
import com.nxredux.mobile.ui.InfoLine
import com.nxredux.mobile.ui.artLess
import com.nxredux.mobile.ui.TileFit
import com.nxredux.mobile.ui.gamesLabel
import com.nxredux.mobile.ui.tileArt
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

internal const val NX_TILE_CORNER = 14f      // a menu tile's corner radius, dp
internal const val NX_TILE_RING = 3f         // the room kept around a tile for a selected game's ring, dp
internal const val NX_TILE_RING_W = 2f       // the ring drawn in it: a selected game's accent ring (Home, Grid, Carousel), dp
internal const val NX_TILE_OUTLINE = 2f      // the Grid's lit logo, tool or collection: its outline around the tile, dp (the full accent)
private const val UNLIT_LOGO_ALPHA = 0.82f   // an unlit console logo's (and so its count's) opacity
private const val LINE_HEIGHT = 1.15f        // a tile text's line height, times its size
private const val HOME_INFO_LINE = 14f / 11f // its info line: the mockup's 14 dp line for the 11 sp text
private val INFO_SP = 11.sp                  // the selected game tile's info line
private val GAME_STATS_SP = 13.sp           // a game list Grid or Carousel's selected card: its stat lines
private const val HOME_CAPTION_LINE = 16f / 13f // on a 16 dp line each
private const val APP_ICON_SHARE = 0.5f      // an Android game tile's app icon, of the tile's smaller side
internal const val GAME_FADE_SHARE = 0.65f   // a game list Grid tile's shade: up to 35% from the top
private const val GAME_FADE_EDGE = 0.8f      // its black, solid from the bottom edge to the title's bottom
private const val GAME_FADE_STOPS = 16       // the soft part's stops
private const val CAPTION_BOTTOM = 8f        // the caption block's bottom padding, dp

/**
 * The game list Grid tile's shade [fromBottom] dp up from the bottom edge: [GAME_FADE_EDGE] black
 * solid up to [solid] (the caption title's bottom, [gameFadeSolid]), then a smoothstep to clear at [reach]
 * ([GAME_FADE_SHARE] of the tile), clear above.
 */
internal fun gameFadeAlpha(fromBottom: Float, solid: Float, reach: Float): Float {
    if (fromBottom <= solid) return GAME_FADE_EDGE
    if (fromBottom >= reach) return 0f
    val t = (fromBottom - solid) / (reach - solid)
    return GAME_FADE_EDGE * (1f - (3 * t * t - 2 * t * t * t))
}

/** The shade's solid height, dp: the caption title's bottom over the tile's, the bottom padding and the [infoLineDp] info line. */
internal fun gameFadeSolid(infoLineDp: Float): Float = CAPTION_BOTTOM + infoLineDp

/** [gameFadeAlpha]'s gradient of [ground] over a tile [tileH] dp tall (the element fills the tile), [solid] dp solid. */
private fun Modifier.gameFade(ground: Color, tileH: Float, solid: Float): Modifier {
    val reach = GAME_FADE_SHARE * tileH
    if (tileH <= 0f) return this
    val soft = Array(GAME_FADE_STOPS) { i ->
        val up = reach - (reach - solid).coerceAtLeast(0f) * i / (GAME_FADE_STOPS - 1f)   // reach down to solid
        (1f - up / tileH) to ground.copy(alpha = gameFadeAlpha(up, solid, reach))
    }
    return background(Brush.verticalGradient(0f to ground.copy(alpha = 0f), *soft, 1f to ground.copy(alpha = GAME_FADE_EDGE)))
}

/**
 * One main menu tile (the Grid's, the Carousel's and Home's), clipped to its rounded shape; [lit] is the selection.
 * A lit logo, tool or collection paints its own accent fill (or, with [outlineLit], the Logo look), and a lit game draws
 * its own [NX_TILE_RING_W] accent ring outside its bounds (the tile's parent must not clip it).
 * [caption]: whether a lit game shows its name and [info] inside the tile (the Grid, Home's pins); false when a caption
 * sits outside. With it, a game with no real art ([GridTile.Game.artLess]: its abstract picture or a title tile) shows
 * its caption lit or not, the same caption in the same place, so its name always reads. [info] is read only where a
 * caption shows, so an unlit tile with art never computes it.
 * [logoInset]: a console logo's inset, (sides, top and bottom) in dp ([gridLogoInset]).
 * [toolSize]: a tool's icon and name sizes; the Grid's by default, the Carousel's scale with its tile ([carouselToolSize]).
 * [titleSize], [titleLines]: a game title tile's name (no screenshot); the Grid's 14 sp on 3 lines by default.
 * [homeCaption]: Home's cards: a lit game's caption (the [nxCardTitleSize] name, on up to 2 balanced lines where the card holds them
 * ([homeCaptionTitleLines]), then the info at [nxCardStatsSize]) sits over an eased fade ([homeCaptionFade]) over the
 * tile's bottom [HOME_CAPTION_SHARE], on a title tile too; the Grid's is the linear 55% one.
 * [outlineLit]: the Grid's "Logo" look: a lit logo, tool or collection stays dark inside a [NX_TILE_OUTLINE] dp outline
 * in the full accent, its logo, icon or name staying in the ink; a console with a logo shows "N games" 6 dp under the
 * logo as drawn, a collection or a logo-less console its count 4 dp under the name, lit or not, each count in its
 * logo's or name's colour and opacity. Games keep the ring.
 * [gameFade]: a game list's Grid: a game tile with a picture has a black shade ([gameFadeAlpha]) under its caption on
 * the lit tile only, in place of the linear one; unlit, the art shows bare, so the shade fades with the lit face (an
 * art-less tile keeps it).
 * [subtitle]: a game's second caption line under its name (the console; Home's Not started shelf), in the hint grey at
 * the info line's size; with it the caption shows lit or not, as on an art-less tile.
 * [statsOnly]: a game list Carousel's selected card: its name is drawn under the tile, so the lit caption is the info
 * alone, each of [backdropCaptionRows] on its own line over [gameFade]'s shade; a title tile keeps its centred name.
 */
@Composable
internal fun NxMenuTile(
    tile: GridTile, lit: Boolean, caption: Boolean,
    info: () -> List<InfoLine>, logoInset: Pair<Float, Float>, portrait: Boolean,
    modifier: Modifier = Modifier,
    toolSize: ToolTileSize = gridToolSize(portrait),
    titleSize: TextUnit = 14.sp, titleLines: Int = 3,
    homeCaption: Boolean = false,
    outlineLit: Boolean = false,
    gameFade: Boolean = false,
    subtitle: String? = null,
    statsOnly: Boolean = false,
    statsSize: TextUnit = GAME_STATS_SP,   // the stat lines' size ([gameFade], [statsOnly])
    captionAlways: Boolean = false,        // the caption shows lit or not (Home's pinned games), as with a [subtitle]
    ring: Float = NX_TILE_RING_W,          // a lit game's ring, dp
) {
    val highlight = nxHighlight()
    val logoLook = outlineLit && lit && tile !is GridTile.Game
    // The ring sits behind the clipped tile, drawn on the unclipped outer box inflated by the ring's width.
    val ringMod = if (lit && tile is GridTile.Game) Modifier.drawBehind {
        val o = ring.dp.toPx()
        drawRoundRect(
            highlight, topLeft = Offset(-o, -o), size = Size(size.width + 2 * o, size.height + 2 * o),
            cornerRadius = CornerRadius(NX_TILE_CORNER.dp.toPx() + o),
        )
    } else if (logoLook) Modifier.drawBehind {
        // The same technique, NX_TILE_OUTLINE wide: the mockup's box-shadow 0 0 0 2px in the accent.
        val o = NX_TILE_OUTLINE.dp.toPx()
        drawRoundRect(
            highlight, topLeft = Offset(-o, -o),
            size = Size(size.width + 2 * o, size.height + 2 * o), cornerRadius = CornerRadius(NX_TILE_CORNER.dp.toPx() + o),
        )
    } else Modifier
    Box(modifier.then(ringMod)) {
        Box(Modifier.fillMaxSize().clip(RoundedCornerShape(NX_TILE_CORNER.dp))) {
            TileContent(tile, lit, logoLook, outlineLit, caption, info, logoInset, portrait, toolSize, titleSize, titleLines, homeCaption, gameFade, subtitle, statsOnly, statsSize, captionAlways)
        }
    }
}

@Composable
private fun TileContent(
    tile: GridTile, lit: Boolean, logoLook: Boolean, outlineLit: Boolean, caption: Boolean,
    info: () -> List<InfoLine>, logoInset: Pair<Float, Float>, portrait: Boolean, toolSize: ToolTileSize,
    titleSize: TextUnit, titleLines: Int, homeCaption: Boolean, gameFade: Boolean, subtitle: String?, statsOnly: Boolean, statsSize: TextUnit,
    captionAlways: Boolean,
) {
    val colors = LocalNxColors.current
    val highlight = nxHighlight()
    // The Logo look keeps the ink: its selection is the border alone.
    val content = if (logoLook) colors.listText else if (lit) colors.onAccent else colors.listText
    // Unlit: the mockup's black tile with a 1 dp inset 8% white edge. Lit: the Logo look's plain dark plate, or its own
    // accent fill.
    val plate = when {
        !lit -> Modifier.tilePlate(colors.background, colors.main)
        logoLook -> Modifier.background(colors.background)
        else -> Modifier.background(highlight)
    }
    when (tile) {
        is GridTile.Logo -> BoxWithConstraints(Modifier.fillMaxSize().then(plate), contentAlignment = Alignment.Center) {
            val res = tile.res
            val count = tile.count
            if (res == null) {
                if (outlineLit) NamedTile(tile.name, content, 17.sp, count)
                else TileTitle(tile.name, content, 17.sp, maxLines = 3)
            } else {
                // With a count under it, the logo and the count centre as one ([LOGO_COUNT_LIFT] up), not the logo alone.
                val lift = if (outlineLit && count != null) LOGO_COUNT_LIFT else 0f
                Image(
                    painterResource(res), contentDescription = tile.name,
                    modifier = Modifier.fillMaxSize().offset(y = (-lift).dp).padding(horizontal = logoInset.first.dp, vertical = logoInset.second.dp)
                        .graphicsLayer { alpha = if (lit) 1f else UNLIT_LOGO_ALPHA },
                    contentScale = ContentScale.Fit, colorFilter = ColorFilter.tint(content),
                )
                // The Grid tile's count, lit or not, 6 dp under the logo as drawn (Fit in the tile less its inset), in the
                // logo's colour and opacity, lifted with it.
                if (outlineLit && count != null) {
                    val boxW = maxWidth.value - 2 * logoInset.first
                    val boxH = maxHeight.value - 2 * logoInset.second
                    val top = underLogoTop(logoInset.second, boxH, logoDrawnHeight(boxW, boxH, logoAspect(res)), LOGO_COUNT_GAP) - lift
                    val sp = if (portrait) 12f else 13f
                    TileText(
                        gamesLabel(count), content, sp.sp, maxLines = 1, lineHeight = 16f / sp,
                        modifier = Modifier.align(Alignment.TopCenter).offset(y = top.dp).padding(horizontal = 8.dp)
                            .graphicsLayer { alpha = if (lit) 1f else UNLIT_LOGO_ALPHA },
                    )
                }
            }
        }
        is GridTile.Collection -> Box(Modifier.fillMaxSize().then(plate), contentAlignment = Alignment.Center) {
            if (outlineLit) NamedTile(tile.name, content, 17.sp, tile.count)
            else TileTitle(tile.name, content, 17.sp, maxLines = 3)
        }
        is GridTile.Tool -> Column(
            Modifier.fillMaxSize().then(plate).padding(horizontal = 8.dp),
            verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ToolIcon(tile.icon, tile.appId, content, Modifier.size(toolSize.iconDp.dp))
            Spacer(Modifier.height(toolSize.gapDp.dp))
            TileText(tile.lines.joinToString("\n"), content, toolSize.nameSp.sp, maxLines = 2)
        }
        is GridTile.Game -> GameTile(tile, lit, caption, info, titleSize, titleLines, homeCaption, gameFade, subtitle, statsOnly, statsSize, captionAlways)
    }
}

/** The gap from a Grid logo's drawn bottom to its "N games" line, dp. */
private const val LOGO_COUNT_GAP = 6f

/** How far a Grid logo with its count lifts, dp: half the gap and the count's 16 dp line, so the pair centres in the tile. */
private const val LOGO_COUNT_LIFT = (LOGO_COUNT_GAP + 16f) / 2

/** A tile with no caption info (the Carousel's: its caption sits outside the tile). */
internal val NoTileInfo: () -> List<InfoLine> = { emptyList() }

/** What a game tile draws, in order: its picture or its plain title, the edge, the caption fade, the caption text. */
internal enum class GameTileLayer { PICTURE, EDGE, FADE, TITLE, CAPTION }

/**
 * A game tile's layers bottom to top ([picture]: it has a screenshot or a stand-in; [lit]; [caption]: a lit tile shows its caption;
 * [homeCaption]: Home's eased fade, drawn even on a title tile under its info line). A title tile's centred name is
 * drawn over the fade, so a lit one keeps its full white instead of the fade greying it.
 * [gameFade]: a game list's Grid ([GAME_FADE_SHARE]): a lit tile with a picture has its shade (even without a caption);
 * an unlit one shows its art bare, so the shade fades in and out with the lit face (the Grid's [GRID_LIT_MS]).
 * [artLess]: the tile shows no real art ([GridTile.Game.artLess]); with [caption], its caption shows lit or not, with the
 * lit tile's fade, and carries the name, so a title tile drops its centred name (one name, at the caption's place);
 * unlit it keeps its edge, under the caption text.
 */
internal fun gameTileLayers(
    picture: Boolean, lit: Boolean, caption: Boolean, homeCaption: Boolean, gameFade: Boolean = false, artLess: Boolean = false,
): List<GameTileLayer> = buildList {
    val always = caption && artLess
    if (picture) add(GameTileLayer.PICTURE)
    if (gameFade && picture && (lit || always)) {
        add(GameTileLayer.FADE)
        if (!lit) add(GameTileLayer.EDGE)
        if (caption) add(GameTileLayer.CAPTION)
        return@buildList
    }
    if (!lit && !always) {
        if (!picture) add(GameTileLayer.TITLE)
        add(GameTileLayer.EDGE)
        return@buildList
    }
    if (caption && (homeCaption || picture)) add(GameTileLayer.FADE)
    if (!lit) add(GameTileLayer.EDGE)
    if (!picture && !always) add(GameTileLayer.TITLE)
    if (caption) add(GameTileLayer.CAPTION)
}

@Composable
private fun GameTile(
    tile: GridTile.Game, lit: Boolean, caption: Boolean, info: () -> List<InfoLine>, titleSize: TextUnit, titleLines: Int, homeCaption: Boolean,
    gameFade: Boolean, subtitle: String?, statsOnly: Boolean, statsSize: TextUnit, captionAlways: Boolean,
) {
    val colors = LocalNxColors.current
    // An opaque base: the glide's white must show only as the ring, never through a plain, loading or failed tile.
    BoxWithConstraints(Modifier.fillMaxSize().background(colors.background)) {
        // The screenshot, else the SteamGridDB stand-in of the tile's shape, else the generated abstract picture
        // ([tileArt]); all drawn as a screenshot (a hero: full height, anchored right), the name only in the caption (or
        // beside the tile).
        val art = tileArt(maxWidth.value, maxHeight.value, tile.screenshotUrl, tile.heroUrl, tile.gridUrl, tile.abstractUrl)
        val url = art?.url
        // An Android game's picture is its art, else its abstract picture, like any game's; its app icon (centred, full
        // colour) stands in only for one with no abstract picture.
        val icon = if (url == null && tile.abstractUrl == null) rememberAppIcon(tile.appId) else null
        // No real art (the abstract picture, the app icon, or a title tile): the caption always shows, and carries the name.
        // A subtitle (Home's Not started) keeps it always shown too.
        // Stats only: the name is under the tile, so the caption never carries it and a title tile keeps its own.
        val artLess = !statsOnly && (subtitle != null || captionAlways || tile.artLess(maxWidth.value, maxHeight.value))
        val layers = gameTileLayers(url != null || icon != null, lit, caption, homeCaption, gameFade, artLess)
        val named = !statsOnly && GameTileLayer.TITLE !in layers   // the caption carries the name unless the centred title does
        // Stats only: the info's rows (time and achievements, then the next goal), each a line of its own.
        val statRows = if (statsOnly && caption && lit) backdropCaptionRows(info()) else emptyList()
        for (layer in layers) when (layer) {
            GameTileLayer.PICTURE -> if (icon != null) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Image(icon, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize(APP_ICON_SHARE))
            } else if (art?.fit == TileFit.HERO) AsyncImage(
                model = url, contentDescription = null, contentScale = ContentScale.FillHeight, alignment = Alignment.CenterEnd,
                modifier = Modifier.fillMaxSize(),
            ) else AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            // No screenshot: a plain title tile, so no hole in the grid; the ring still goes round it. On Home the name
            // takes the lines the tile holds, and lit with its info line it keeps clear of it (the bottom's caption room).
            GameTileLayer.TITLE -> if (!homeCaption) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                TileTitle(tile.title, colors.listText, titleSize, maxLines = titleLines)
            } else BoxWithConstraints(Modifier.fillMaxSize()) {
                // The info line's room at its size ([nxCardStatsSize], the 11 sp line's room scaled to it).
                val scale = LocalDensity.current.fontScale
                val room = if (lit && caption && info().isNotEmpty()) homeTitleCaptionRoom(scale * nxCardStatsSize().value / INFO_SP.value) else 0f
                val lines = homeTitleLines(maxHeight.value, titleSize.value, room, maxLines = titleLines, scale = scale)
                Box(Modifier.fillMaxWidth().height((maxHeight.value - room).coerceAtLeast(0f).dp), contentAlignment = Alignment.Center) {
                    TileTitle(tile.title, colors.listText, titleSize, maxLines = lines)
                }
            }
            // The tile's subtle edge, over the screenshot's too; lit, the ring takes its place.
            GameTileLayer.EDGE -> Box(Modifier.fillMaxSize().border(1.dp, colors.main.copy(alpha = 0.08f), RoundedCornerShape(NX_TILE_CORNER.dp)))
            GameTileLayer.FADE -> if (gameFade) {
                val infoDp = with(LocalDensity.current) { (statsSize * LINE_HEIGHT).toDp().value }
                Box(Modifier.fillMaxSize().gameFade(colors.background, maxHeight.value, gameFadeSolid(infoDp * statRows.size.coerceAtLeast(1))))
            } else if (homeCaption) Box(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(HOME_CAPTION_SHARE).homeCaptionFade(colors.background),
            ) else Box(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(0.55f)
                    .background(Brush.verticalGradient(listOf(colors.background.copy(alpha = 0f), colors.background.copy(alpha = 0.85f)))),
            )
            GameTileLayer.CAPTION -> {
                // The info line with the List's inline trophy ([infoText]), the icon fitted to the 11 sp line (12 dp where
                // it fits); on Home the info lines are a step under the title's 13 sp, at 11 sp on 14 dp lines.
                // Home's cards: the info at the game list Carousel card's size, the name a step under its name, under Text size.
                val statsSp = if (homeCaption) nxCardStatsSize() else INFO_SP
                val homeName = NxTextSizes.secondary(nxCardTitleSize())
                val statsLine = if (homeCaption) HOME_INFO_LINE else LINE_HEIGHT
                val lineDp = with(LocalDensity.current) { (statsSp * statsLine).toDp() }
                val info = if (statsOnly) emptyList() else info()
                // Home's info on its handheld rows (the time and the achievements, then the next goal), a line each.
                val allHomeRows = if (homeCaption) backdropCaptionRows(info) else emptyList()
                val infoText = infoText(info, colors.hint, icon = minOf(12.dp, lineDp))
                val rowDp = with(LocalDensity.current) { (statsSize * LINE_HEIGHT).toDp() }   // a game list's stat line
                // Home's name wraps to a second (balanced) line where the card holds it ([homeCaptionTitleLines]),
                // unless two info rows are under it (the name, then the two: three lines); the block is bottom-anchored,
                // so it grows upward and the info lines keep their place.
                // A shelf game with its console ([subtitle]: New, Not started) keeps its name whole first: when its info
                // rows would hold the name to one line and it wraps without them, the rows go (the console stays).
                val scale = LocalDensity.current.fontScale
                fun linesWith(rows: Int): Int {
                    val line = homeName.value * HOME_CAPTION_LINE * scale
                    val below = CAPTION_BOTTOM + (if (subtitle != null) statsSp.value * HOME_INFO_LINE * scale + 2f else 0f) +
                        rows * (statsSp.value * statsLine * scale + 2f)
                    return homeCaptionTitleLines(maxHeight.value, line, below, maxLines = if (rows > 1) 1 else 2)
                }
                val homeRows = if (homeCaption && named && subtitle != null && allHomeRows.isNotEmpty() && linesWith(allHomeRows.size) < 2 && linesWith(0) >= 2)
                    emptyList() else allHomeRows
                val homeLines = if (homeCaption && named) linesWith(homeRows.size) else 1
                Column(
                    Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(start = 10.dp, end = 10.dp, bottom = CAPTION_BOTTOM.dp),
                    verticalArrangement = Arrangement.spacedBy(if (homeCaption) 2.dp else 0.dp),
                ) {
                    if (named) {
                        if (homeCaption) TileText(
                            tile.title, colors.listText, homeName, maxLines = homeLines, align = TextAlign.Start,
                            balanced = homeLines > 1, lineHeight = HOME_CAPTION_LINE, shadow = if (homeLines > 1) titleShadow() else null,
                        )
                        else TileText(tile.title, colors.listText, titleSize, maxLines = 2, align = TextAlign.Start)
                    }
                    if (subtitle != null) TileText(subtitle, colors.hint, statsSp, maxLines = 1, align = TextAlign.Start, lineHeight = HOME_INFO_LINE)
                    if (homeCaption) for (row in homeRows) {
                        val text = infoText(row, colors.hint, icon = minOf(12.dp, lineDp))
                        TileText(text.text, colors.hint, statsSp, maxLines = 1, align = TextAlign.Start, inlineContent = text.inlineContent, lineHeight = statsLine)
                    } else if (info.isNotEmpty()) {
                        val text = if (gameFade) infoText(info, colors.hint, icon = minOf(14.dp, rowDp)) else infoText
                        TileText(
                            text.text, colors.hint, if (gameFade) statsSize else INFO_SP, maxLines = 1, align = TextAlign.Start,
                            inlineContent = text.inlineContent, lineHeight = LINE_HEIGHT,
                        )
                    }
                    for (row in statRows) {
                        val text = infoText(row, colors.hint, icon = minOf(14.dp, rowDp))
                        TileText(text.text, colors.hint, statsSize, maxLines = 1, align = TextAlign.Start, inlineContent = text.inlineContent, lineHeight = LINE_HEIGHT)
                    }
                }
            }
        }
    }
}

/** An unlit tile's ground: [ground] (black) with a 1 dp inset edge of [edge] at 8%, on the tile's rounded shape. */
internal fun Modifier.tilePlate(ground: Color, edge: Color): Modifier =
    background(ground).border(1.dp, edge.copy(alpha = 0.08f), RoundedCornerShape(NX_TILE_CORNER.dp))

/**
 * The Grid's name tile (a collection, a logo-less console): its name on up to 3 lines, and with a [count] "N games" 4 dp
 * under it (13 sp) in the name's colour, on either face.
 */
@Composable
private fun NamedTile(name: String, color: Color, size: TextUnit, count: Int?) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        TileTitle(name, color, size, maxLines = 3)
        if (count != null) {
            Spacer(Modifier.height(4.dp))
            TileText(
                gamesLabel(count), color, 13.sp, maxLines = 1,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
        }
    }
}

/** A logo's aspect (height over width) from its intrinsic size; null while unknown. */
@Composable
internal fun logoAspect(res: DrawableResource): Float? =
    painterResource(res).intrinsicSize.let { if (it.isSpecified && it.width > 0f) it.height / it.width else null }

/** A title filling a tile (a collection, a logo-less console, a game without a picture): centred, balanced lines. */
@Composable
private fun TileTitle(text: String, color: Color, size: TextUnit, maxLines: Int) {
    TileText(text, color, size, maxLines, modifier = Modifier.padding(horizontal = 12.dp), balanced = true)
}

/** A name's shadow over a picture (the placeholder case's abstract cover): the mockup's text-shadow 0 1 6 dp black at 90% (a Shadow is in px). */
@Composable
internal fun titleShadow(): Shadow {
    val d = LocalDensity.current.density
    return Shadow(Color.Black.copy(alpha = 0.9f), Offset(0f, 1f * d), 6f * d)
}

@Composable
private fun TileText(
    text: String, color: Color, size: TextUnit, maxLines: Int,
    align: TextAlign = TextAlign.Center, modifier: Modifier = Modifier, balanced: Boolean = false, lineHeight: Float = LINE_HEIGHT,
    shadow: Shadow? = null,
) = TileText(AnnotatedString(text), color, size, maxLines, align, modifier, balanced, lineHeight = lineHeight, shadow = shadow)

/** A tile's text at [size]. */
@Composable
private fun TileText(
    text: AnnotatedString, color: Color, size: TextUnit, maxLines: Int,
    align: TextAlign = TextAlign.Center, modifier: Modifier = Modifier, balanced: Boolean = false,
    inlineContent: Map<String, InlineTextContent> = emptyMap(), lineHeight: Float = LINE_HEIGHT, shadow: Shadow? = null,
) {
    val scaled = size
    Text(
        text, modifier, color = color, maxLines = maxLines, overflow = TextOverflow.Ellipsis, inlineContent = inlineContent,
        style = TextStyle(
            fontFamily = LocalNxFont.current, fontWeight = FontWeight.SemiBold, fontSize = scaled, lineHeight = scaled * lineHeight,
            textAlign = align, lineBreak = if (balanced) LineBreak.Heading else LineBreak.Unspecified, shadow = shadow,
        ),
    )
}
