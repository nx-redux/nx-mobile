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
import kotlin.math.min

private const val CAPTION_GAP = 16f     // dp between the centre tile's bottom edge and the caption
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
    // A game list: the neighbours on the screen's centre line, and the selected card holds the game's stats
    // ([captionInfo]) over a shade, its name alone under it.
    gameList: Boolean = false,
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
        val infoStyle = TextStyle(fontFamily = font, fontSize = 14.sp, textAlign = TextAlign.Center)
        val room = rememberCaptionRoom(CAPTION_GAP, nameStyle, infoStyle, infoRows = if (gameList) 0 else CAROUSEL_INFO_ROWS).takeIf { cardInfo == null } ?: 0f
        // One size in both orientations, shrunk only so the tile and the caption room fit the body's height (the shown
        // title's; a hidden title's room left out), the block centred in the whole box.
        val (g, top) = carouselRowPlace(maxHeight.value, titleRoom.value, room, portraitW = maxWidth.value.takeIf { portrait })   // portrait: the neighbours show
        if (g.itemW <= 1f || g.itemH <= 1f || tiles.isEmpty()) return@BoxWithConstraints

        val gutter = pageGutter(portrait).dp
        val avail = maxWidth - gutter * 2
        val availPx = constraints.maxWidth - 2 * with(LocalDensity.current) { gutter.roundToPx() }
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
        fun selectedTop(drawn: Float) =
            if (centreLine != null) rowSelectedTopOn(centreLine, area, g.itemH, CAPTION_GAP, drawn) else rowSelectedTop(area, g.itemH, CAPTION_GAP, drawn)
        val selTop = remember(glide, area, g.itemH, centreLine) { { selectedTop(glide.value()) } }
        // The swipe band: from the highest a selected tile goes (its name's two lines, the info and every spare line)
        // down to the neighbours' bottom, so it covers every drawn tile.
        val bandTop = min(nbTop, selectedTop(room - CAPTION_GAP + spare))
        val bandH = nbTop + g.itemH - bandTop

        val hasLongPress = onLongPress != null
        val logoInset = remember(g) { rowLogoInset(g) }
        val toolSize = carouselToolSize(g)
        val stats = captionInfo.takeIf { gameList }

        // The window spans the target too, and the selection is lit while [lit] says so.
        NxRow(count, selectedIndex, pos, g, bandTop, bandH, onSelect, onOpen, onLongPress, litSelection = lit) { i, isLit, at, taps ->
            CarouselItem(tiles[i], i, isLit, at, g, nbTop, bandTop, selTop, logoInset, toolSize, portrait, taps, hasLongPress, badge(i), cardInfo, stats, drained)
        }

        if (cardInfo == null) RowCaption(
            captionTitle, if (gameList) NoTileInfo else captionInfo, glide, nameStyle, 2, infoStyle, gutter, avail, availPx, spare,
            selTop, g.itemH + CAPTION_GAP, colors.accent,   // the selected game's name in the accent, as Home's cards
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
        // A game list's selected card holds the stats ([stats], the selection's), shaded as the Grid's; its name is below.
        NxMenuTile(
            tile, lit = lit, caption = cardInfo != null || stats != null,
            info = remember(cardInfo, stats, i) { cardInfo?.let { ci -> { ci(i) } } ?: stats ?: NoTileInfo }, logoInset = logoInset, portrait = portrait,
            modifier = Modifier.fillMaxSize(), toolSize = toolSize, homeCaption = cardInfo != null,
            gameFade = stats != null, statsOnly = stats != null,
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
 * achievement count on the first row, the next goal on the second, an empty row left out. With Layouts → Extra info
 * hidden the info is empty ([com.nxredux.mobile.ui.launcherGameInfo]), so the caption is the name alone and its drawn
 * height ([rememberCaptionDrawnHeight]) the name's, which [rowSelectedTop] centres on.
 */
internal fun carouselCaptionRows(title: String?, info: List<InfoLine>): List<List<InfoLine>> =
    if (title == null) emptyList() else backdropCaptionRows(info)

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
 * A row caption's height as drawn, dp: [title] in [name] at the caption's width ([availPx], the Texts' own
 * max width), up to [nameLines] lines, then the [fitted] info rows in [info], each up to its own lines; the same styles,
 * width and line limits the caption's Texts use, measured before layout so the row never sits a frame wrong. A row's
 * trophy (an inline icon shorter than the line) doesn't change its height. 0 with no [title] (no caption drawn).
 */
@Composable
internal fun rememberCaptionDrawnHeight(
    title: String?, name: TextStyle, nameLines: Int, fitted: List<CaptionInfoRow>, info: TextStyle, availPx: Int,
): Float {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    return remember(title, name, nameLines, fitted, info, availPx, density) {
        if (title == null) return@remember 0f
        val c = Constraints(maxWidth = availPx.coerceAtLeast(0))
        var px = measurer.measure(title, name, TextOverflow.Ellipsis, maxLines = nameLines, constraints = c).size.height
        for (row in fitted) {
            val text = row.lines.joinToString(INFO_SEPARATOR) { it.text }
            px += measurer.measure(text, info, TextOverflow.Ellipsis, maxLines = row.maxLines, constraints = c).size.height
        }
        with(density) { px.toDp().value }
    }
}

/** The carousel caption's name size; a game list's follows Appearance → Text size ([nxCardTitleSize]). */
private val CAROUSEL_TITLE_SP = 18.sp
