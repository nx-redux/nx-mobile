package com.nxredux.mobile.ui.nx

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nxredux.mobile.ui.GridTile
import com.nxredux.mobile.ui.HOME_DOCK_GAP
import com.nxredux.mobile.ui.HomeStrip
import com.nxredux.mobile.ui.InfoLine
import com.nxredux.mobile.ui.MainRow
import com.nxredux.mobile.ui.abstractAccentArtUrl
import com.nxredux.mobile.ui.abstractArtFor
import com.nxredux.mobile.ui.continuePictures
import com.nxredux.mobile.ui.gridTiles
import com.nxredux.mobile.ui.homeCarouselRowCount
import com.nxredux.mobile.ui.homeDockSide
import com.nxredux.mobile.ui.homeStripLines
import kotlin.math.max
import kotlin.math.min

/** The least room above, between and below the Carousel Home's rows (the strip, the carousel, the dock). */
private const val HOME_CAROUSEL_MIN_GAP = 12f
/** The room, in the rows' one centred group, between the card and the strip's text over it, and the card and the dock under it. */
private const val HOME_CAROUSEL_CARD_GAP = 16f
/**
 * The strip's text to the carousel's box, and the box to the dock: [HOME_CAROUSEL_CARD_GAP] to the card itself, less the
 * box's room each side of it at its full size (the card is centred in the box, [rowSelectedTop]: half its ring room and
 * ring each side, [homeCarouselMaxH]).
 */
private const val HOME_CAROUSEL_BOX_ROOM = (GRID_RING_ROOM + NX_TILE_RING) / 2
private const val HOME_CAROUSEL_STRIP_GAP = HOME_CAROUSEL_CARD_GAP - HOME_CAROUSEL_BOX_ROOM
private const val HOME_CAROUSEL_GROUP_GAP = HOME_CAROUSEL_CARD_GAP - HOME_CAROUSEL_BOX_ROOM

/**
 * The carousel's box at its card's full size on a body [widthDp] wide ([carouselRowPlace]: the size cap, and in
 * [portrait] the width share), with its ring room above and its ring below; the box never needs more.
 */
internal fun homeCarouselMaxH(widthDp: Float, portrait: Boolean): Float =
    carouselRowPlace(HOME_UNBOUNDED_DP, 0f, 0f, portraitW = widthDp.takeIf { portrait }).g.itemH + GRID_RING_ROOM + NX_TILE_RING

private const val HOME_UNBOUNDED_DP = 10_000f   // a body tall enough that the card's height never limits it

/** Where the Carousel Home's rows go ([homeCarouselPlace]), in dp from the body's top: the strip's ink top, the carousel's box, the dock's top. */
internal data class HomeCarouselPlace(val stripInkTop: Float, val boxTop: Float, val boxH: Float, val dockTop: Float)

/**
 * The Carousel Home's rows in a body [bodyH] tall, from the tab underline [above] dp over its top (0 with the tab row
 * hidden; [homeUnderlineAbove]) to the hint bar: the strip's visible text ([stripInkH], 0 for none; [homeStripInk]),
 * the carousel's box, and the dock ([dockH], 0 for none), one group centred in the span, the card
 * [HOME_CAROUSEL_CARD_GAP] from the strip's text and from the dock. The box is the card at its full size ([maxBoxH]; [homeCarouselMaxH]), less what
 * keeps [HOME_CAROUSEL_MIN_GAP] above and below the group. With [fill] (the Pick a game card) the rows are spaced evenly
 * instead, the same room above, between and below them, the card taking all the rest.
 */
internal fun homeCarouselPlace(bodyH: Float, above: Float, stripInkH: Float, dockH: Float, fill: Boolean = false, maxBoxH: Float = homeCarouselMaxH(0f, false)): HomeCarouselPlace {
    val span = bodyH + above
    if (!fill) {
        val stripPart = if (stripInkH > 0f) stripInkH + HOME_CAROUSEL_STRIP_GAP else 0f
        val dockPart = if (dockH > 0f) HOME_CAROUSEL_GROUP_GAP + dockH else 0f
        val boxH = min(maxBoxH, (span - stripPart - dockPart - 2 * HOME_CAROUSEL_MIN_GAP).coerceAtLeast(0f))
        val top = (span - stripPart - boxH - dockPart) / 2 - above
        val boxTop = top + stripPart
        return HomeCarouselPlace(top, boxTop, boxH, boxTop + boxH + (if (dockH > 0f) HOME_CAROUSEL_GROUP_GAP else 0f))
    }
    val gaps = 2 + (if (stripInkH > 0f) 1 else 0) + (if (dockH > 0f) 1 else 0)
    val room = (span - stripInkH - dockH - gaps * HOME_CAROUSEL_MIN_GAP).coerceAtLeast(0f)
    val g = ((span - stripInkH - room - dockH) / gaps).coerceAtLeast(0f)
    var y = g - above
    val stripTop = y
    if (stripInkH > 0f) y += stripInkH + g
    val boxTop = y
    return HomeCarouselPlace(stripTop, boxTop, room, boxTop + room + g)
}

/**
 * Sideways, [place] moved so its rows (the strip's text when it is over the card, the card, the dock) are centred on the
 * screen's middle rather than the body's: the body [bodyTop] dp down the window, the screen's centre [centreY] dp down it ([LocalScreenCentreY]). The rows go no higher
 * than [minTop] and no lower than [maxBottom] (body dp), or than the body-centred [place] already does.
 */
internal fun homeCarouselScreenCentred(
    place: HomeCarouselPlace, stripInkH: Float, dockH: Float, bodyTop: Float, centreY: Float, minTop: Float, maxBottom: Float,
): HomeCarouselPlace {
    val top = if (stripInkH > 0f) place.stripInkTop else place.boxTop
    val bottom = if (dockH > 0f) place.dockTop + dockH else place.boxTop + place.boxH
    val want = centreY - bodyTop - (top + bottom) / 2
    // Never tighter than the body-centred place already is (it may reach into the tab underline's room).
    val shift = max(min(minTop, top) - top, min(max(maxBottom, bottom) - bottom, want))
    return place.copy(stripInkTop = place.stripInkTop + shift, boxTop = place.boxTop + shift, dockTop = place.dockTop + shift)
}

/**
 * The Carousel Home's row items in [rows] as the carousel's tiles (the dock's tools and "+N" are not among them):
 * Continue its first picture ([continuePictures]: the resume frame first) under its name, its abstract picture the
 * fallback; Pick a game the accent's abstract picture ([abstractAccentArtUrl] of [accentRgb]); a pin or a shelf game its
 * Grid tile ([gridTiles]).
 */
internal fun homeCarouselTiles(rows: List<MainRow>, accentRgb: Int): List<GridTile> = rows.mapNotNull { r ->
    when (r) {
        is MainRow.Continue -> continuePictures(r.resume.previewPath, r.entry).firstOrNull().let { picture ->
            GridTile.Game(
                picture, r.entry.game.shownName, abstractUrl = abstractArtFor(r.entry),
                resumeFrame = picture != null && picture == r.resume.previewPath,
            )
        }
        MainRow.PickAGame -> GridTile.Game(abstractAccentArtUrl(accentRgb), PICK_A_GAME)
        is MainRow.Pin, is MainRow.Shelf -> gridTiles(listOf(r)).single()
        else -> null
    }
}

/** A Carousel Home item's badge ([HomeBadge]): Continue's "Continue", a shelf game's shelf name; none on the others. */
internal fun homeCarouselBadge(row: MainRow?): String? = when (row) {
    is MainRow.Continue -> "Continue"
    is MainRow.Shelf -> row.shelf.label
    else -> null
}

/**
 * The row item the Carousel Home's carousel shows: the selection [sel] while it is on one of the [rowCount] row items,
 * else (the dock has the focus) the one last selected, [last], kept in range.
 */
internal fun homeCarouselShownRow(sel: Int, rowCount: Int, last: Int): Int =
    if (sel in 0 until rowCount) sel else last.coerceIn(0, (rowCount - 1).coerceAtLeast(0))

/**
 * Whether a Carousel Home [row] takes a long press (its menu): not Pick a game nor "+N", as on Grid Home, unless
 * [tabMenu] (a hidden tab can be opened from MENU): then every row does, its menu holding the hidden tabs.
 */
internal fun homeCarouselMenu(row: MainRow?, tabMenu: Boolean = false): Boolean =
    row != null && (tabMenu || row != MainRow.PickAGame && row !is MainRow.MoreTools)

/**
 * Home in the Carousel style (Layouts → Home layout: Carousel), as the handheld's Carousel Home: the stats strip
 * ([HomeStripLines], centred, while Home stats shows it) over a carousel ([NxCarousel]) of Home's row items (Continue or
 * Pick a game, the pinned games, the shelves' games; [homeCarouselTiles], badged by [homeCarouselBadge]) over the dock
 * of pinned tools and "+N" ([HomeDock]). A fresh install's only row item, Pick a game, is drawn as its card ([PickCard])
 * between the strip and the dock instead. The rows are one centred group ([homeCarouselPlace]), the card shrinking first. With the
 * tab row hidden the strip moves into a header in its place ([homeStatsHeader]).
 *
 * [rows] are the Carousel Home's ([homeCarouselRowCount] of them row items, the rest the dock); [selection] indexes
 * them. Navigation and focus memory live in AppState: this draws the selection, taps go through [onSelect] / [onOpen]
 * ([nxTap]), and a long press on a row with a menu ([homeCarouselMenu]) calls [onLongPress]. While the dock has the
 * focus the carousel stays, unlit, on the row item last selected ([rowLast], the one UP from the dock lands on;
 * [homeCarouselShownRow]). [captionInfo]: a row item's info line, by its index in [rows], drawn with its name inside
 * the lit card and an art-less one ([NxCarousel]'s cardInfo).
 * Reports its [HomeFrame] ([homeFrameHere]) through [onLayout] once laid out, and again for each [layoutKey] (AppState
 * fits the dock to it). Until [known] (the rows were built on a reported frame, not AppState's stand-in) nothing is
 * drawn, as on Grid Home: the stand-in's dock may not fit this width.
 * [tabMenu]: a hidden tab can be opened from MENU, so Pick a game and "+N" take a long press too.
 */
@Composable
internal fun HomeCarouselScreen(
    rows: List<MainRow>, selection: () -> Int, strip: HomeStrip?, captionInfo: (Int) -> List<InfoLine>,
    onSelect: (Int) -> Unit, onOpen: (Int) -> Unit, onLongPress: ((Int) -> Unit)?,
    onLayout: (HomeFrame) -> Unit,
    modifier: Modifier = Modifier,
    layoutKey: Any? = null,
    rowLast: () -> Int = { 0 },
    known: Boolean = true,
    tabMenu: Boolean = false,
) {
    val colors = LocalNxColors.current
    val portrait = LocalPortrait.current
    val gutter = if (portrait) 16.dp else 24.dp
    BoxWithConstraints(modifier.fillMaxSize()) {
        val frame = homeFrameHere() ?: return@BoxWithConstraints
        val report by rememberUpdatedState(onLayout)
        LaunchedEffect(frame, layoutKey) { report(frame) }
        if (!known) return@BoxWithConstraints
        val width = maxWidth.value

        val rowCount = homeCarouselRowCount(rows)
        val sel by rememberUpdatedState(selection)
        val info by rememberUpdatedState(captionInfo)
        val last by rememberUpdatedState(rowLast)
        val longPress = onLongPress?.let { lp -> remember(rows, lp, tabMenu) { { i: Int -> if (homeCarouselMenu(rows.getOrNull(i), tabMenu)) lp(i) } } }
        val shownStrip = strip?.takeIf { !frame.statsHidden }
        val stripLines = if (shownStrip?.most != null) 2 else 1
        // With the tab row hidden the strip moves into a header in its place ([homeStatsHeader]), at the gutter short
        // of the Recent and Search icons; the carousel and the dock are placed under it.
        val header = homeStatsHeader(frame, if (shownStrip != null) stripLines else 0)?.takeIf { it.lines > 0 }
        // The tab row's room kept with it hidden too (the Recent and Search icons are there): the rows never run up into it.
        val headerH = header?.height ?: if (frame.titleHidden) MENU_TAB_ROW_HEIGHT.value else 0f
        val (capTop, inkH) = if (shownStrip != null && header == null) homeStripInk(frame, stripLines) else 0f to 0f
        // Grid Home's pinned tool squares on this frame, as AppState fitted the dock to ([homeDockSide]).
        val side = remember(frame, strip, rows) {
            homeDockSide(frame, homeStripLines(strip), rows.count { it is MainRow.Pin })
        }
        val dockH = if (rows.size > rowCount) side else 0f
        val pick = rowCount == 1 && rows.first() == MainRow.PickAGame
        val bodyH = maxHeight.value - headerH
        val centred = homeCarouselPlace(
            bodyH, if (header != null) 0f else homeUnderlineAbove(frame), inkH, dockH, fill = pick,
            maxBoxH = homeCarouselMaxH(width, portrait),
        )
        // Sideways, the rows centred on the screen's middle, not the body's (the tab row and the header make it uneven).
        val density = LocalDensity.current
        val centreY = LocalScreenCentreY.current?.let { with(density) { it.toDp().value } }
        var bodyTop by remember { mutableFloatStateOf(Float.NaN) }
        val place = if (portrait || pick || bodyTop.isNaN() || centreY == null) centred
            else homeCarouselScreenCentred(centred, inkH, dockH, bodyTop + headerH, centreY, HOME_CAROUSEL_MIN_GAP, bodyH - HOME_CAROUSEL_MIN_GAP)
        Box(Modifier.fillMaxSize().onGloballyPositioned { bodyTop = with(density) { it.positionInWindow().y.toDp().value } }) {
            // With the tab row hidden, the tab row's eased band in its place (the header's height), under the strip
            // and the Recent and Search icons MainMenuScreen draws over it.
            if (frame.titleHidden) Box(Modifier.fillMaxWidth().height((header?.height ?: MENU_TAB_ROW_HEIGHT.value).dp + NX_TOP_BAND_REACH).nxTopBandFade(colors.background))
            // In the header with the tab row hidden, else centred over the centred carousel, in either orientation.
            if (header != null) shownStrip?.let {
                HomeStripLines(
                    it, header.lines, gutter.value, header.stripTop, width = width - 2 * gutter.value - HOME_HEADER_ICONS_ROOM,
                    lh = homeStripLineH(frame), pitch = homeStripPitch(frame),
                )
            } else shownStrip?.let {
                HomeStripLines(
                    it, stripLines, gutter.value, place.stripInkTop - capTop,
                    width = width - 2 * gutter.value, lh = homeStripLineH(frame), pitch = homeStripPitch(frame), centred = true,
                )
            }
            val box = Modifier.offset(y = (headerH + place.boxTop).dp).height(place.boxH.dp).fillMaxWidth()
            if (pick) {
                // Nothing played and no pins: the Pick a game card fills the room between the strip and the dock.
                HomePickCard({ sel() }, onSelect, onOpen, longPress?.takeIf { tabMenu }, portrait, box.padding(horizontal = gutter))
            } else if (rowCount > 0) {
                val accent = colors.accent.toArgb() and 0xFFFFFF
                val tiles = remember(rows, rowCount, accent) { homeCarouselTiles(rows.take(rowCount), accent) }
                // While the dock has the focus: AppState's row item last on, the one UP lands on.
                val rowSel = remember(rowCount) { { homeCarouselShownRow(sel(), rowCount, last()) } }
                NxCarousel(
                    tiles = tiles, selectedIndex = rowSel,
                    onSelect = onSelect, onOpen = onOpen, onLongPress = longPress,
                    captionTitle = { null }, captionInfo = { emptyList() },
                    // The name and info inside the cards, over Home's caption fade (as Continue and the pins).
                    cardInfo = { info(it) },
                    modifier = box, layoutKey = layoutKey,
                    badge = { homeCarouselBadge(rows.getOrNull(it)) },
                    lit = { sel() in 0 until rowCount },
                    drained = true,   // as Grid Home: no ring, the selected card in colour, the rest grey
                )
            }
            if (dockH > 0f) HomeDock(rows, rowCount, { sel() }, onSelect, onOpen, longPress, side, tabMenu, Modifier.offset(y = (headerH + place.dockTop).dp))
        }
    }
}

/**
 * The Carousel Home's dock: [rows] from [first] on (the pinned tools AppState fits, then "+N") as Grid Home's squares
 * ([HomeToolSquare], [HomeMoreSquare]), [side] dp ([homeDockSide]) and [HOME_DOCK_GAP] dp apart, centred across at
 * [modifier]'s place; lit when [selection] is on one. Taps as Grid Home's ([nxTap]); a long press (only rows with a
 * menu reach it) calls [onLongPress].
 */
@Composable
private fun HomeDock(
    rows: List<MainRow>, first: Int, selection: () -> Int,
    onSelect: (Int) -> Unit, onOpen: (Int) -> Unit, onLongPress: ((Int) -> Unit)?, side: Float, tabMenu: Boolean, modifier: Modifier,
) {
    val tools = remember(rows, first) { rows.drop(first).map { if (it is MainRow.PinnedTool) gridTiles(listOf(it)).single() as GridTile.Tool else null } }
    val sel by rememberUpdatedState(selection)
    val select by rememberUpdatedState(onSelect)
    val open by rememberUpdatedState(onOpen)
    val tapOpens by rememberUpdatedState(LocalNxTapOpens.current)
    val longPress by rememberUpdatedState(onLongPress)
    fun Modifier.taps(i: Int, menu: Boolean): Modifier = pointerInput(i, menu && onLongPress != null) {
        detectTapGestures(
            onTap = { nxTap(i, i == sel(), tapOpens(i), select, open) },
            onLongPress = if (menu && longPress != null) { _ -> longPress?.invoke(i) } else null,
        )
    }
    val lit = selection()
    Row(
        modifier.fillMaxWidth().height(side.dp),
        horizontalArrangement = Arrangement.spacedBy(HOME_DOCK_GAP.dp, Alignment.CenterHorizontally),
    ) {
        rows.drop(first).forEachIndexed { k, row ->
            val i = first + k
            val m = Modifier.size(side.dp)
            val menu = homeCarouselMenu(row, tabMenu)
            when (row) {
                is MainRow.PinnedTool -> tools[k]?.let { HomeToolSquare(it, i == lit, side, m.taps(i, menu)) }
                is MainRow.MoreTools -> HomeMoreSquare(row.count, i == lit, side, m.taps(i, menu))
                else -> Unit
            }
        }
    }
}
