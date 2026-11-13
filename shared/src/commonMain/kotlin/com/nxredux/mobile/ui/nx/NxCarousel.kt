package com.nxredux.mobile.ui.nx

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nxredux.mobile.ui.GridTile
import com.nxredux.mobile.ui.INFO_SEPARATOR
import com.nxredux.mobile.ui.InfoLine
import com.nxredux.mobile.ui.InfoKind
import com.nxredux.mobile.ui.NO_GENRE
import kotlin.math.max
import kotlin.math.min

internal const val CAPTION_GAP = 16f     // dp between the centre tile's bottom edge and the caption
/** The Carousel caption's info rows ([carouselCaptionRows]): the time and trophies, then the next goal. */
private const val CAROUSEL_INFO_ROWS = 2
/** The dp a game caption's extra lines ([CaptionInfoRows]) keep clear above the body's bottom (the hint bar). */
internal const val CAPTION_SPARE_MARGIN = 8f

/**
 * A game list's Carousel style, and the Carousel Home's row: [tiles] in one row with the selection large in the centre,
 * its neighbours smaller and darker with distance, nothing past three steps. The main menu's Carousel is [NxBackdrop]'s
 * row on black instead. The row slides (0.3 s, [NxMenuEasing]) to keep the selection centred. [captionTitle] (a game's,
 * a console's or a collection's name; null for a tool, whose tile shows its name) and [captionInfo] (the game's info
 * line, the console's or collection's game count) are centred under the centre tile.
 * The tile and the caption's room (kept on every tab, so the row sits at the same height on every tab) size the row
 * ([carouselRowPlace]), but the tiles are placed by what is drawn: a neighbour centred alone, the selection with its
 * caption as drawn ([rowSelectedTop]), gliding between the two with the slide ([rowItemTop]).
 * A tap on a side item selects it, a tap on the centre item opens it, a long press calls [onLongPress]; only items
 * [rowItem] shows take touches. A horizontal drag moves the row live ([rowSwipeDelta]) and, on release, selects the
 * item nearest where it would come to rest ([rowSwipeTarget]). [layoutKey]: the current Screen instance; a new key
 * snaps the row to its selection, as a new [tiles] list does. Nothing here clips: the lit game's ring is drawn outside
 * its tile. Orientation Vertical (game lists): [NxCarouselStack].
 * [titleRoom]: a hidden page title's room at the top of this box: sized without it, centred with it.
 * [badge]: an item's [HomeBadge] (the Carousel Home's "Continue" and shelf names), top-left on its tile.
 * [lit]: false draws no tile lit (the Carousel Home while its dock has the focus); the row's place and dimming stay.
 * [cardInfo] (the Carousel Home): the caption goes inside the cards instead ([NxMenuTile]'s homeCaption), and the row
 * keeps no caption room, so the cards take it. Null (game lists): the caption under the row.
 */
@Composable
fun NxCarousel(
    tiles: List<GridTile>, selectedIndex: () -> Int,
    onSelect: (Int) -> Unit, onOpen: (Int) -> Unit, onLongPress: ((Int) -> Unit)?,
    captionTitle: () -> String?, captionInfo: () -> List<InfoLine>,
    modifier: Modifier = Modifier,
    layoutKey: Any? = null,
    titleRoom: Dp = 0.dp,
    badge: (Int) -> String? = { null },
    lit: () -> Boolean = { true },
    cardInfo: ((Int) -> List<InfoLine>)? = null,
    // A game list: the neighbours on the screen's centre line; of the game's lines ([captionInfo], [carouselGameLines])
    // the time and achievements in the selected card over a shade, its details under its name, one line each in the
    // vertical stack's style ([StackCaption]).
    gameList: Boolean = false,
    // A game list that can show details under the name (Layouts → Extra info on, not the dual-screen menu): its tile's
    // cap keeps room for them ([carouselCaptionNeed]); false, for the name alone.
    gameDetails: Boolean = true,
    drained: Boolean = false,   // Home's: no ring, the selected card in colour and the rest grey ([NxMenuTile]'s drained)
) {
    val colors = LocalNxColors.current
    val portrait = LocalPortrait.current
    val screenCentreY = LocalScreenCentreY.current
    var boxTopY by remember { mutableStateOf<Float?>(null) }   // this box's top in window px, for the screen's centre line
    BoxWithConstraints(modifier.fillMaxSize().onGloballyPositioned { boxTopY = it.positionInWindow().y }) {
        // The caption's room: the gap, two lines of the name and the info line, measured in the caption's own styles.
        // The room follows the caption's sizes (and the system font), so the row's height fit does too.
        val font = LocalNxFont.current
        // A game list's selected title follows Appearance → Text size (the room below follows it too).
        val nameSize = if (gameList) nxCardTitleSize() else CAROUSEL_TITLE_SP
        val nameStyle = TextStyle(fontFamily = font, fontWeight = FontWeight.SemiBold, fontSize = nameSize, textAlign = TextAlign.Center)
        // A game list's details at 80% of its name, as the vertical stack's lines ([NxCarouselStack]'s infoShare).
        val infoStyle = TextStyle(fontFamily = font, fontSize = if (gameList) nameSize * STACK_INFO_SHARE else 14.sp, textAlign = TextAlign.Center)
        val room = rememberCaptionRoom(CAPTION_GAP, nameStyle, infoStyle, infoRows = if (gameList) 0 else CAROUSEL_INFO_ROWS).takeIf { cardInfo == null } ?: 0f
        // One size in both orientations, shrunk only so the tile and the caption room fit the body's height (the shown
        // title's; a hidden title's room left out), the block centred in the whole box.
        // A game list's selection is capped so its caption has room for a name line and two detail rows under it, or a
        // two-line name and one ([carouselCaptionNeed], [carouselItemHMax]), measured in their styles; a list that can
        // show no details ([gameDetails] false), for the name alone. Where the box leaves that room already, nothing changes.
        val need = if (gameList) rememberCaptionNeed(nameStyle, infoStyle, gameDetails) else null
        val (g, top, capped) = carouselRowPlace(
            maxHeight.value, titleRoom.value, room, bodyW = maxWidth.value,   // held to a share of the width: the neighbours show
            maxItemH = need?.let { carouselItemHMax(maxHeight.value, it) },
        )
        // Capped, the tile and its caption keep the breathing room the cap reserved over and under them
        // ([carouselMargins]); uncapped, today's margins, the tile placed as before.
        val margins = carouselMargins(maxHeight.value, g.itemH, need ?: 0f, capped)
        val captionMax = carouselCaptionMax(maxHeight.value, g.itemH, margins)
        if (g.itemW <= 1f || g.itemH <= 1f || tiles.isEmpty()) return@BoxWithConstraints

        val inset = nxPageInset()   // the caption's text inside it
        val avail = maxWidth - inset.start - inset.end
        val availPx = constraints.maxWidth - with(LocalDensity.current) { inset.start.roundToPx() + inset.end.roundToPx() }
        val spare = (maxHeight.value - (top + g.itemH + room) - CAPTION_SPARE_MARGIN).coerceAtLeast(0f)

        val count = tiles.size
        // The animated position, in items ([rememberRowPosition]: a new screen or a new list snaps).
        val pos = rememberRowPosition(selectedIndex, count, layoutKey, tiles)

        // The tiles centred in the box, not the reserved block ([rowNeighbourTop], [rowSelectedTop]): a
        // neighbour by itself, the selection with its caption as drawn, the caption's height gliding with the slide (a
        // new screen snaps), each tile between the two by its distance ([rowItemTop], the scale's weight).
        val area = maxHeight.value
        // A neighbour's top: centred in the box by itself, or a game list's on the screen's centre line (the box sits
        // between the title and the hint bar, so its own centre is lower), kept inside the box.
        val density = LocalDensity.current.density
        val nbTop = (if (gameList) screenCentreLineTop(screenCentreY, boxTopY, density, g.itemH) else null)
            ?.coerceIn(0f, (area - g.itemH).coerceAtLeast(0f)) ?: rowNeighbourTop(area, g.itemH)
        val glide = remember(pos) { CaptionGlide() }
        // A game list's selection, with its name under it, centred on the screen's centre line too, not the box's.
        val centreLine = if (gameList) screenCentreLineTop(screenCentreY, boxTopY, density, 0f) else null
        fun selectedTop(drawn: Float) = when {
            capped -> rowSelectedTopOn(centreLine ?: (area / 2), area, g.itemH, CAPTION_GAP, drawn, margins.top, margins.bottom)
            centreLine != null -> rowSelectedTopOn(centreLine, area, g.itemH, CAPTION_GAP, drawn)
            else -> rowSelectedTop(area, g.itemH, CAPTION_GAP, drawn)
        }
        val selTop = remember(glide, area, g.itemH, centreLine, capped, margins) { { selectedTop(glide.value()) } }
        // The swipe band: from the highest a selected tile goes (its name's two lines, the info and every spare line; a
        // game list's caption up to [carouselCaptionMax], its rows cut to that) down to the neighbours' bottom, so it
        // covers every drawn tile.
        val tallest = room - CAPTION_GAP + spare
        val bandTop = min(nbTop, selectedTop(if (gameList) max(tallest, captionMax) else tallest))
        val bandH = nbTop + g.itemH - bandTop

        val hasLongPress = onLongPress != null
        // A game list's lines split by kind ([carouselGameLines]): the card's stats, the caption's details.
        val stats = if (gameList) remember(captionInfo) { { carouselGameLines(captionInfo()).picture } } else null
        val details = if (gameList) remember(captionInfo) { { carouselGameLines(captionInfo()).underTitle } } else captionInfo
        val logoInset = remember(g) { rowLogoInset(g) }
        val toolSize = carouselToolSize(g)

        // The window spans the target too, and the selection is lit while [lit] says so.
        NxRow(count, selectedIndex, pos, g, bandTop, bandH, onSelect, onOpen, onLongPress, litSelection = lit) { i, isLit, at, taps ->
            CarouselItem(tiles[i], i, isLit, at, g, nbTop, bandTop, selTop, logoInset, toolSize, portrait, taps, hasLongPress, badge(i), cardInfo, stats, drained)
        }

        // A game list's details under the name as the stack's lines (a row each, [STACK_CAPTION_GAP] apart), only the
        // whole rows that fit under the tile at its highest ([carouselCaptionMax]): the caption never reaches the bottom row.
        if (cardInfo == null) RowCaption(
            captionTitle, details, glide, nameStyle, 2, infoStyle, inset, avail, availPx, spare,
            selTop, g.itemH + CAPTION_GAP, colors.accent,   // the selected game's name in the accent, as Home's cards
            stackRows = gameList, rowGap = if (gameList) STACK_CAPTION_GAP else 0.dp,
            maxDrawn = if (gameList) captionMax else Float.POSITIVE_INFINITY,
        )
    }
}

@Composable
private fun BoxScope.CarouselItem(
    tile: GridTile, i: Int, lit: Boolean, at: () -> Float, g: RowGeometry, nbTop: Float, bandTop: Float, selTop: () -> Float,
    logoInset: Pair<Float, Float>, toolSize: ToolTileSize, portrait: Boolean, taps: ItemTaps, hasLongPress: Boolean,
    badge: String?, cardInfo: ((Int) -> List<InfoLine>)?, stats: (() -> List<InfoLine>)?, drained: Boolean,
) {
    val colors = LocalNxColors.current
    val geometry by rememberUpdatedState(g)
    val shownAt by rememberUpdatedState(at)
    Box(
        Modifier.align(Alignment.TopCenter).size(g.itemW.dp, g.itemH.dp)
            .graphicsLayer {
                val r = rowItem(RowStyle.CAROUSEL, i - at(), g)
                translationX = r.dx.dp.toPx()
                translationY = (rowItemTop(nbTop, selTop(), i - at()) - bandTop).dp.toPx()
                scaleX = r.scale
                scaleY = r.scale
                alpha = if (r.visible) r.alpha.coerceIn(0f, 1f) else 0f
            }
            // The window spans the target too: during a slide an item rowItem hides must not take a touch.
            .shownItemTaps(i, taps, hasLongPress) { rowItem(RowStyle.CAROUSEL, i - shownAt(), geometry).visible },
    ) {
        // A game list's selected card holds its stats ([stats], the selection's), shaded as the Grid's; its name is below.
        // No stats to show (a game never played): no shade either ([carouselCardShaded]).
        val shaded = stats != null && carouselCardShaded(lit, stats)
        NxMenuTile(
            tile, lit = lit, caption = cardInfo != null || shaded,
            info = remember(cardInfo, stats, i) { cardInfo?.let { ci -> { ci(i) } } ?: stats ?: NoTileInfo }, logoInset = logoInset, portrait = portrait,
            modifier = Modifier.fillMaxSize(), toolSize = toolSize, homeCaption = cardInfo != null,
            gameFade = shaded, statsOnly = stats != null,
            statsSize = if (stats != null) nxCardStatsSize() else 13.sp, drained = drained, heroName = drained && cardInfo != null,
        )
        badge?.let { HomeBadge(it, portrait, Modifier.align(Alignment.TopStart), lit = lit || !drained) }
        // The neighbour's black layer, on the tile's own rounded shape (the ring outside stays bright). Drained, the
        // greyscale dims the neighbours already.
        if (!drained) Box(
            Modifier.fillMaxSize().clip(RoundedCornerShape(NX_TILE_CORNER.dp))
                .graphicsLayer { alpha = rowItem(RowStyle.CAROUSEL, i - at(), g).dim }
                .background(colors.background),
        )
    }
}

/**
 * The Carousel caption's info rows under a [title], none without either. As on the handheld: the time and the
 * achievement count on the first row, the next goal on the second, an empty row left out; a game list's ([gameList])
 * one line a row, as its vertical stack's caption ([stackCaptionRows]). With Layouts → Extra info hidden the info is
 * empty ([com.nxredux.mobile.ui.launcherGameInfo]), so the caption is the name alone and its drawn height
 * ([rememberCaptionHeights]) the name's, which [rowSelectedTop] centres on.
 */
internal fun carouselCaptionRows(title: String?, info: List<InfoLine>, gameList: Boolean = false): List<List<InfoLine>> =
    if (title == null) emptyList() else if (gameList) stackCaptionRows(info) else backdropCaptionRows(info)

/** A game list Carousel's lines ([carouselGameLines]): those in the selected card's [picture], those [underTitle]. */
internal data class CarouselGameLines(val picture: List<InfoLine>, val underTitle: List<InfoLine>)

/**
 * A game list Carousel's selected game's [lines] by kind: the play time and the achievements (the count, the next goal)
 * in its picture; its details (the genre, "<year> · <publisher>": the stored ScreenScraper facts) under its name. The
 * genre's stand-in for a game with no genre stored ([NO_GENRE]) alone is no detail: nothing goes under the name then.
 */
internal fun carouselGameLines(lines: List<InfoLine>): CarouselGameLines {
    val (details, stats) = lines.partition { it.kind == InfoKind.GENRE || it.kind == InfoKind.RELEASE }
    val shown = details.takeUnless { d -> d.all { it.kind == InfoKind.GENRE && it.text == NO_GENRE } }.orEmpty()
    return CarouselGameLines(stats, shown)
}

/**
 * Whether a game list Carousel card keeps its shade and stats block: lit, only with stats to show ([picture], read only
 * then); an unlit card draws neither whatever this says, so it keeps its layers as before.
 */
internal fun carouselCardShaded(lit: Boolean, picture: () -> List<InfoLine>): Boolean = !lit || picture().isNotEmpty()

/**
 * The room a game list Carousel keeps over its selected tile at its highest ([top], from the box's top: the top row) and
 * under its caption's last row ([bottom], over the box's bottom: the tab row or hint bar), dp.
 */
internal data class CaptionMargins(val top: Float, val bottom: Float)

/**
 * The margin a capped game list Carousel keeps between its box's top and its selected tile at its highest, dp
 * ([carouselMargins]). The visible gap to the top row is larger: that row carries its own empty room.
 */
internal const val CAROUSEL_TOP_BREATH = 12f
/**
 * The margin a capped game list Carousel keeps between its caption's last row and its box's bottom, dp. The visible gap
 * to the bottom row (the tab row, the hint bar) is larger: that row carries its own empty room.
 */
internal const val CAROUSEL_BOTTOM_BREATH = 12f

/** The dp [carouselItemHMax] (and so [carouselMargins]) leaves over a caption's need, so a rounding never cuts a row. */
internal const val CAPTION_NEED_SLACK = 1f

/**
 * A game list Carousel's [CaptionMargins] in a box [areaDp] tall with tiles [itemH] tall. Uncapped (![capped]), today's:
 * the ring's room at the top (or the neighbours' top if that is higher) and [CAPTION_SPARE_MARGIN] at the bottom.
 * Capped ([carouselItemHMax] reserved it), [CAROUSEL_TOP_BREATH] and [CAROUSEL_BOTTOM_BREATH]; at the
 * [CAROUSEL_MIN_HEIGHT_SHARE] floor, where the box is short of that room once the caption's [needDp] is under the tile,
 * the breathing room gives way first, both margins back towards today's in step, before [captionRowsWithin] cuts a row.
 */
internal fun carouselMargins(areaDp: Float, itemH: Float, needDp: Float, capped: Boolean): CaptionMargins {
    val tight = CaptionMargins(min(GRID_RING_ROOM, rowNeighbourTop(areaDp, itemH)), CAPTION_SPARE_MARGIN)
    if (!capped) return tight
    val spare = areaDp - itemH - CAPTION_GAP - needDp - CAPTION_NEED_SLACK
    val least = tight.top + tight.bottom
    val t = ((spare - least) / (CAROUSEL_TOP_BREATH + CAROUSEL_BOTTOM_BREATH - least)).coerceIn(0f, 1f)
    return CaptionMargins(tight.top + (CAROUSEL_TOP_BREATH - tight.top) * t, tight.bottom + (CAROUSEL_BOTTOM_BREATH - tight.bottom) * t)
}

/**
 * The most a game list Carousel's caption may take under its selected tile, dp, in a box [areaDp] tall with tiles
 * [itemH] tall: what is left once the tile sits at its highest ([margins]' top: by default its ring's room, or the
 * neighbours' top if that is higher), less the gap over the caption and the [margins]' bottom over the bottom row (the
 * hint bar, the tab row).
 */
internal fun carouselCaptionMax(areaDp: Float, itemH: Float, margins: CaptionMargins = carouselMargins(areaDp, itemH, 0f, false)): Float =
    (areaDp - itemH - margins.top - CAPTION_GAP - margins.bottom).coerceAtLeast(0f)

/** A game list Carousel's selected tile is never capped below this share of its box's height ([carouselItemHMax]). */
internal const val CAROUSEL_MIN_HEIGHT_SHARE = 0.4f

/**
 * The tallest a game list Carousel's selected tile may be in a box [areaDp] tall for its caption to have [needDp] under
 * it ([carouselCaptionNeed]: the name and its detail rows) with [CAROUSEL_TOP_BREATH] over the tile and
 * [CAROUSEL_BOTTOM_BREATH] under the caption ([carouselMargins]), and [CAPTION_NEED_SLACK] more, so a rounding of the
 * drawn heights never cuts the last row. Never below [CAROUSEL_MIN_HEIGHT_SHARE] of the box (a box too short for all
 * of it keeps a tile; the breathing room gives way first, then the rows are cut to what fits).
 */
internal fun carouselItemHMax(areaDp: Float, needDp: Float): Float =
    max(areaDp - CAROUSEL_TOP_BREATH - CAPTION_GAP - CAROUSEL_BOTTOM_BREATH - needDp - CAPTION_NEED_SLACK, CAROUSEL_MIN_HEIGHT_SHARE * areaDp)

/**
 * A row's caption room in dp: [gap] under the slot, [nameLines] lines of the name in [name] (its most: the Carousel's
 * two, Backdrop's one) and [infoRows] info lines in [info] (two for both), measured in those styles, so it follows the
 * font and the font scale. Kept on every tab, captioned or not.
 */
@Composable
internal fun rememberCaptionRoom(gap: Float, name: TextStyle, info: TextStyle, infoRows: Int = 1, nameLines: Int = 2): Float {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    return remember(gap, name, info, infoRows, nameLines, density) {
        val px = measurer.measure(List(nameLines) { "A" }.joinToString("\n"), name, maxLines = nameLines).size.height + infoRows * measurer.measure("A", info).size.height
        gap + with(density) { px.toDp().value }
    }
}

/**
 * A game list Carousel caption's need in dp ([carouselItemHMax]), the taller of a one-line name ([nameH]) with both detail
 * rows ([rowH], [gap] before each) and a two-line name ([twoLineNameH]) with one, so a long name keeps its genre. A list
 * that can show no details ([details] false: Layouts → Extra info off, or the dual-screen menu) needs the name alone, its
 * two lines. The need is the list's, not the selected game's: a game with no details in a list that can show them keeps
 * the same need, so the tile never resizes from game to game.
 */
internal fun carouselCaptionNeed(nameH: Float, twoLineNameH: Float, rowH: Float, gap: Float, details: Boolean = true): Float =
    if (!details) twoLineNameH
    else max(captionDrawnHeight(nameH, listOf(rowH, rowH), gap), captionDrawnHeight(twoLineNameH, listOf(rowH), gap))

/**
 * [carouselCaptionNeed] measured in the caption's styles: one and two lines of the name in [name] (as
 * [rememberCaptionRoom] measures them), a detail row in [info]; so it follows the text size.
 */
@Composable
private fun rememberCaptionNeed(name: TextStyle, info: TextStyle, details: Boolean): Float {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    return remember(name, info, details, density) {
        with(density) {
            val nameH = measurer.measure("A", name).size.height.toDp().value
            val twoLines = measurer.measure("A\nA", name, maxLines = 2).size.height.toDp().value
            val rowH = measurer.measure("A", info).size.height.toDp().value
            carouselCaptionNeed(nameH, twoLines, rowH, STACK_CAPTION_GAP.value, details)
        }
    }
}

/**
 * A row caption's heights as drawn, dp: [title] in [name] at the caption's width ([availPx], the Texts' own
 * max width), up to [nameLines] lines, then each of the [fitted] info rows in [info], up to its own lines; the same
 * styles, width and line limits the caption's Texts use, measured before layout so the row never sits a frame wrong
 * ([captionDrawnHeight] adds them up). A row's trophy (an inline icon shorter than the line) doesn't change its height.
 * 0 and none with no [title] (no caption drawn).
 */
@Composable
internal fun rememberCaptionHeights(
    title: String?, name: TextStyle, nameLines: Int, fitted: List<CaptionInfoRow>, info: TextStyle, availPx: Int,
): Pair<Float, List<Float>> {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    return remember(title, name, nameLines, fitted, info, availPx, density) {
        if (title == null) return@remember 0f to emptyList()
        val c = Constraints(maxWidth = availPx.coerceAtLeast(0))
        with(density) {
            val nameH = measurer.measure(title, name, TextOverflow.Ellipsis, maxLines = nameLines, constraints = c).size.height.toDp().value
            nameH to fitted.map { row ->
                val text = row.lines.joinToString(INFO_SEPARATOR) { it.text }
                measurer.measure(text, info, TextOverflow.Ellipsis, maxLines = row.maxLines, constraints = c).size.height.toDp().value
            }
        }
    }
}

/** The carousel caption's name size; a game list's follows Appearance → Text size ([nxCardTitleSize]). */
private val CAROUSEL_TITLE_SP = 18.sp
