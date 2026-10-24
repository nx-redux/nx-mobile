package com.nxredux.mobile.ui.nx

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.rememberSplineBasedDecay
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.transformations
import com.nxredux.mobile.ui.GridTile
import com.nxredux.mobile.ui.HomeStrip
import com.nxredux.mobile.ui.HomeStripMost
import com.nxredux.mobile.ui.HomeView
import com.nxredux.mobile.ui.InfoLine
import com.nxredux.mobile.ui.MainRow
import com.nxredux.mobile.ui.abstractAccentArtUrl
import com.nxredux.mobile.ui.continuePictures
import com.nxredux.mobile.ui.gridTiles
import com.nxredux.mobile.ui.homeStripLines
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import kotlin.math.max
import kotlin.math.min

private const val SCROLL_MS = 320           // the page's eased move to keep the selected pin in view
private const val PICTURE_BIAS = -0.2f      // Continue's picture framed at 40% from the top (Crop)
internal const val PICK_A_GAME = "Pick a game"
private const val NOTHING_PLAYED = "Nothing played yet"
private const val THIS_MONTH = "This month"
private const val MOST_PLAYED = "Most played"

/**
 * The Home tab in its Grid layout (List and Carousel Home are [HomeListScreen] and [HomeCarouselScreen]), for a body
 * between the tab row and the hint bar: the stats strip ([HomeStripLines]) over the top section (Continue or Pick a game,
 * the inline games, the tool squares, "+N") over the row games and shelves, every tile on its [homeBoard] rect. The page
 * scrolls to keep the selected row tile in view ([homeScrollTo], eased 0.32 s), and sits at the top while the top
 * section is selected. With the tab row hidden the body starts at the screen's top and the strip moves into a header
 * there ([homeStatsHeader]).
 *
 * [rows] are the Home tab's ([homeTabRows]) and [selection] indexes them, read only in small scopes (the scroll's
 * follow, each tile's lit state; RowItems' pattern), so a selection step recomposes the scroll and the two tiles whose
 * lit state changed, never the page, its board or its other tiles. [view] says how many are the top section,
 * the counts, and the frame and strip lines they were built for; the board comes from those, so rows built for an older
 * frame stay on their own board until the rebuild arrives. Navigation and focus memory live in AppState: this draws the
 * selection and the scroll. A tap opens an item ([nxTap]; a game or pinned tool takes a tap to select and one more to
 * open). A long press on a game or tool calls [onLongPress], and on Pick a game and "+N" too when [tabMenu] (a hidden
 * tab offered from MENU). A vertical drag scrolls the page without selecting and flings on when let go.
 * [continueInfo]: Continue's game info. [pinInfo]: a row game's, by row index, read lazily where the caption shows.
 * Both show their TIME segment only ([timeOnly]); [signedIn] false drops achievement data.
 * Reports its [HomeFrame] through [onLayout] once laid out, and again for each [layoutKey].
 */
@Composable
internal fun HomeScreen(
    rows: List<MainRow>, selection: () -> Int, view: HomeView,
    onSelect: (Int) -> Unit, onOpen: (Int) -> Unit, onLongPress: ((Int) -> Unit)?,
    onLayout: (HomeFrame) -> Unit,
    continueInfo: List<InfoLine>, pinInfo: (Int) -> List<InfoLine>, signedIn: Boolean,
    modifier: Modifier = Modifier,
    layoutKey: Any? = null,
    tabMenu: Boolean = false,
) {
    val colors = LocalNxColors.current
    val portrait = LocalPortrait.current
    val tabRow = LocalLauncherChrome.current.tabRow
    BoxWithConstraints(modifier.fillMaxSize().clipToBounds()) {
        val w = maxWidth.value
        val whole = homeFrameHere() ?: return@BoxWithConstraints
        // With the tab row hidden the stats strip sits in a fixed header in its place ([homeStatsHeader]); the page is
        // the body under it, laid out as under a tab row with the strip hidden.
        val header = homeStatsHeader(whole, homeStripLines(view.strip))
        val frame = header?.body ?: whole
        val h = frame.h
        val report by rememberUpdatedState(onLayout)
        LaunchedEffect(frame, layoutKey) { report(frame) }
        // The board the rows were built for: their frame and strip lines. Before the first report the rows were built
        // on a stand-in frame, so nothing is drawn until the view names the real one (the board here is a placeholder).
        val known = view.frame != null
        val built = view.frame ?: frame
        val b = remember(built, view) { homeBoard(built, view.lines ?: homeStripLines(view.strip), view.tools, view.games, view.shelves) }
        val topCount = view.topCount.coerceIn(0, rows.size)
        val pins = remember(rows, topCount) { rows.drop(topCount) }
        val pinTiles = remember(pins) { gridTiles(pins) }
        // The top section's tools and inline games, as their tiles (null: Continue, Pick a game, "+N").
        val topTiles = remember(rows, topCount) {
            rows.take(topCount).map { if (it is MainRow.PinnedTool || it is MainRow.Pin) gridTiles(listOf(it)).single() else null }
        }
        // Continue's info, kept as one list while it is equal, so a focus move elsewhere lets the card skip.
        val contInfo = remember(continueInfo, signedIn) { timeOnly(homeInfo(continueInfo, signedIn)) }
        val tap by rememberUpdatedState(selection)   // read when a tap lands
        val select by rememberUpdatedState(onSelect)
        val open by rememberUpdatedState(onOpen)
        val tapOpens by rememberUpdatedState(LocalNxTapOpens.current)   // Continue, pinned games and pinned tools take two taps
        val longPress by rememberUpdatedState(onLongPress)
        fun Modifier.taps(i: Int, menu: Boolean): Modifier = pointerInput(i, menu && onLongPress != null) {
            detectTapGestures(
                onTap = { nxTap(i, i == tap(), tapOpens(i), select, open) },
                onLongPress = if (menu && longPress != null) { _ -> longPress?.invoke(i) } else null,
            )
        }

        // The scroll: an offset only (read in the layers below, so a frame of it redraws, not recomposes). A new
        // selection eases it as little as keeps the selected tile in view (with its shelf's label, [homeScrollRect]),
        // from where it is (a drag included); a new board snaps; a rows swap on the same board (stats, recents) keeps
        // it ([homeScrollStep]).
        val tiles = b.rowGames + b.shelfTiles.size   // the row tiles the board has rects for: pins, then the shelves'
        val pageH = b.pageH
        val scroll = remember { Animatable(0f) }
        val last = remember { arrayOfNulls<Any>(2) }   // the board and the selected rect the scroll last answered
        HomeScrollFollow(scroll, last, b, selection, topCount, pins.size, tiles, pageH, h)
        val maxScroll by rememberUpdatedState(max(0f, pageH - h))
        val scope = rememberCoroutineScope()
        val scrolled by remember(scroll) { derivedStateOf { scroll.value > 0.5f } }
        // A drag let go flings on, as the List's and the Carousel's do: its velocity decays within the page.
        val decay = rememberSplineBasedDecay<Float>()
        val drag = Modifier.pointerInput(scroll) {
            val tracker = VelocityTracker()
            detectVerticalDragGestures(
                onDragStart = { tracker.resetTracking(); scope.launch { scroll.stop() } },
                onDragEnd = {
                    val v = -tracker.calculateVelocity().y / density   // dp/s, up the page positive
                    scope.launch {
                        scroll.updateBounds(0f, maxScroll)
                        scroll.animateDecay(v, decay)
                        scroll.updateBounds(null, null)
                    }
                },
            ) { change, dy ->
                change.consume()
                tracker.addPosition(change.uptimeMillis, change.position)
                val to = (scroll.value - dy / density).coerceIn(0f, maxScroll)
                scope.launch { scroll.snapTo(to) }
            }
        }

        fun Modifier.at(r: HomeRect): Modifier = offset(r.x.dp, r.y.dp).size(r.w.dp, r.h.dp)
        Box(Modifier.padding(top = (header?.height ?: 0f).dp).fillMaxSize().clipToBounds().then(drag)) {
            // The page moves with the scroll, the strip with it; a drag that starts on a tile scrolls too.
            if (known) Box(Modifier.fillMaxSize().graphicsLayer { translationY = -scroll.value.dp.toPx() }) {
                // None while Home stats is off (the board's lines 0, its room the top section's).
                if (!built.statsHidden) view.strip?.let { HomeStripLines(it, b.stripLines, b.gutter, b.stripY, w - 2 * b.gutter, homeStripLineH(built), homeStripPitch(built)) }
                // The shelves' labels: 13 sp grey on 18 sp lines, at the gutter (the text scale grows both; the board
                // leaves room for it, [HomeBoard.labelH]).
                // A label the body's bottom edge would cut shows only once it is in view whole (no sliver of a word
                // under the last card); read with the scroll, so a frame of it redraws, not recomposes.
                b.labels.forEach { l ->
                    HomeText(
                        l.text, colors.hint, 13f, 18f, maxLines = 1,
                        modifier = Modifier.offset(b.gutter.dp, l.y.dp).graphicsLayer { alpha = if (l.y + b.labelH - scroll.value <= h + 0.5f) 1f else 0f },
                    )
                }
                rows.take(topCount).forEachIndexed { i, row ->
                    val at = b.top.getOrNull(i)?.rect ?: return@forEachIndexed
                    when (row) {
                        is MainRow.Continue -> { val m = Modifier.at(at).taps(i, menu = true); HomeLit(i, selection) { lit -> ContinueCard(row, lit, contInfo, portrait, m) } }
                        MainRow.PickAGame -> { val m = Modifier.at(at).taps(i, menu = tabMenu); HomeLit(i, selection) { lit -> PickCard(lit, portrait, at.h, m) } }
                        is MainRow.PinnedTool -> (topTiles.getOrNull(i) as? GridTile.Tool)?.let { t ->
                            val m = Modifier.at(at).taps(i, menu = true); HomeLit(i, selection) { lit -> HomeToolSquare(t, lit, at.w, m) }
                        }
                        is MainRow.MoreTools -> { val m = Modifier.at(at).taps(i, menu = tabMenu); HomeLit(i, selection) { lit -> HomeMoreSquare(row.count, lit, at.w, m) } }
                        // An inline game (every landscape face) is a pinned game's tile at its rect, taps and all.
                        is MainRow.Pin -> topTiles.getOrNull(i)?.let { t ->
                            val m = Modifier.at(at).taps(i, menu = true)
                            HomeLit(i, selection) { lit -> HomePinTile(t, lit, { pinInfo(i) }, signedIn, portrait, m, always = true) }
                        }
                        else -> Unit
                    }
                }
                // The row tiles under the top section: the pins, then the shelves' games (a shelf's title on 2 lines; a
                // New or Not started game's caption always shows, its console under its name; a pinned game's too).
                pins.forEachIndexed { k, row ->
                    if (k >= tiles) return@forEachIndexed
                    val i = topCount + k
                    val lines = if (k >= b.rowGames) 2 else null
                    val console = (row as? MainRow.Shelf)?.takeIf { it.shelf == HomeShelf.NEW || it.shelf == HomeShelf.NOT_STARTED }?.entry?.system?.displayName
                    pinTiles.getOrNull(k)?.let { t ->
                        val m = Modifier.at(homeBoardTile(b, k)).taps(i, menu = true)
                        HomeLit(i, selection) { lit -> HomePinTile(t, lit, { pinInfo(i) }, signedIn, portrait, m, lines, console, always = row is MainRow.Pin) }
                    }
                }
            }
        }

        // The tab row's eased top band, continued over the body's top, only while the page is scrolled (at rest it
        // would grey the top of a white-filled card: Continue's, Pick a game's). No tab row, the header's the same way.
        val band = remember(layoutKey) { Animatable(if (scrolled) 1f else 0f) }
        LaunchedEffect(band, scrolled) { band.animateTo(if (scrolled) 1f else 0f, tween(SCROLL_MS, easing = NxMenuEasing)) }
        if (tabRow) {
            Box(
                Modifier.offset(y = -MENU_TAB_ROW_HEIGHT).fillMaxWidth().height(MENU_TAB_ROW_HEIGHT + NX_TOP_BAND_REACH)
                    .graphicsLayer { alpha = band.value }.nxTopBandFade(colors.background),
            )
        }
        // The header: the tab row's eased band while the page is scrolled under it, and the strip on it at the gutter
        // (short of the Recent and Search icons MainMenuScreen draws at its right).
        header?.let { hd ->
            Box(Modifier.fillMaxWidth().height(hd.height.dp + NX_TOP_BAND_REACH).graphicsLayer { alpha = band.value }.nxTopBandFade(colors.background))
            if (hd.lines > 0) view.strip?.let {
                HomeStripLines(it, hd.lines, b.gutter, hd.stripTop, w - 2 * b.gutter - HOME_HEADER_ICONS_ROOM, homeStripLineH(whole), homeStripPitch(whole))
            }
        }
    }
}

/**
 * The scroll's answer to a new selection or board ([homeScrollStep]): the selected row tile's rect ([homeScrollRect]; the
 * top section's none), eased or snapped to. Its own scope, as [FollowSelection]: only this reads [selection] for the
 * scroll, so a step recomposes it alone. [last] holds the board and the selected rect the scroll last answered.
 */
@Composable
private fun HomeScrollFollow(
    scroll: Animatable<Float, AnimationVector1D>, last: Array<Any?>, b: HomeBoard, selection: () -> Int,
    topCount: Int, pinCount: Int, tiles: Int, pageH: Float, h: Float,
) {
    val selected = (selection() - topCount).takeIf { it in 0 until pinCount && it < tiles }?.let { homeScrollRect(b, it) }
    LaunchedEffect(scroll, b, selected, pageH) {
        val step = homeScrollStep(scroll.targetValue, last[0] as HomeBoard?, b, last[1] as HomeRect?, selected, pageH, h)
        last[0] = b; last[1] = selected
        when (step.move) {
            HomeScrollMove.SNAP -> scroll.snapTo(step.to)
            // KEEP re-issues the ease to its own target: a swap mid-move must not stop it halfway.
            HomeScrollMove.EASE, HomeScrollMove.KEEP -> if (step.to != scroll.value) scroll.animateTo(step.to, tween(SCROLL_MS, easing = NxMenuEasing))
        }
    }
}

/**
 * [content] lit while [selection] is [i], through a derived state of its own: a selection step recomposes only the
 * tiles whose lit state changed (the one left and the one reached), never the page or the others.
 */
@Composable
private fun HomeLit(i: Int, selection: () -> Int, content: @Composable (lit: Boolean) -> Unit) {
    val lit by remember(i, selection) { derivedStateOf { selection() == i } }
    content(lit)
}

/** The room the header's strip leaves at its right for the Recent and Search icons (two 44 dp targets). */
internal const val HOME_HEADER_ICONS_ROOM = 88f

/**
 * Home's [HomeFrame] for this box (null before it has a size): its size, the portrait type, the app area's aspect
 * (landscape only: the window inside the safe insets, not the body, decides the wide rule), the text scale (the system
 * font scale), whether the tab row is hidden, and whether the stats strip is hidden (Home stats off).
 */
@Composable
internal fun BoxWithConstraintsScope.homeFrameHere(): HomeFrame? {
    val w = maxWidth.value
    val h = maxHeight.value
    if (w <= 0f || h <= 0f) return null
    val portrait = LocalPortrait.current
    val chrome = LocalLauncherChrome.current
    val window = LocalWindowSize.current
    val menuRatio = if (!portrait && window.height.value > 0f) window.width.value / window.height.value else null
    val fontScale = LocalDensity.current.fontScale
    return HomeFrame(w, h, portrait, menuRatio, fontScale, titleHidden = !chrome.tabRow, statsHidden = !chrome.homeStats, stripScale = nxTextScale())
}

/**
 * A pinned game in Home's rows: the menu tile with Home's caption and its time ([info], read only where the caption
 * shows: lit, and always on an art-less pin). [titleLines] overrides the face's title lines (a shelf tile's 2).
 * [console]: a New or Not started shelf game's console, under its name in the caption, which then always shows.
 * [always]: a pinned game: its caption (name and time) shows lit or not.
 */
@Composable
private fun HomePinTile(
    tile: GridTile, lit: Boolean, info: () -> List<InfoLine>, signedIn: Boolean, portrait: Boolean, modifier: Modifier,
    titleLines: Int? = null, console: String? = null, always: Boolean = false,
) =
    NxMenuTile(
        tile, lit = lit, caption = true,
        info = { timeOnly(homeInfo(info(), signedIn)) }, logoInset = 0f to 0f, portrait = portrait,
        modifier = modifier, titleSize = 12.sp * nxTextScale(), titleLines = titleLines ?: if (portrait) 4 else 3, homeCaption = true,
        subtitle = console, captionAlways = always,
    )

/**
 * A pinned tool's square: the tile plate with the tool's glyph alone, centred, min(46, 55% of the side) dp, the ink at
 * 82%; lit, the accent plate and the glyph on-accent, no ring. An Android app's own icon shows in full colour, never
 * tinted ([ToolIcon]).
 */
@Composable
internal fun HomeToolSquare(tile: GridTile.Tool, lit: Boolean, side: Float, modifier: Modifier) {
    val colors = LocalNxColors.current
    val icon = max(0f, min(46f, side * 0.55f))
    Box(modifier.homeCard(lit, fill = true), contentAlignment = Alignment.Center) {
        ToolIcon(tile.icon, tile.appId, if (lit) colors.onAccent else colors.listText.copy(alpha = 0.82f), Modifier.size(icon.dp))
    }
}

/** "+N": the pinned tools that don't fit, min(20, 36% of the side) sp in the hint grey (lit: on-accent muted). */
@Composable
internal fun HomeMoreSquare(count: Int, lit: Boolean, side: Float, modifier: Modifier) {
    val colors = LocalNxColors.current
    val sp = max(0f, min(20f, side * 0.36f))
    Box(modifier.homeCard(lit, fill = true), contentAlignment = Alignment.Center) {
        if (sp > 0f) HomeText("+$count", if (lit) colors.onAccentMuted else colors.hint, sp, sp * 1.2f, maxLines = 1, align = TextAlign.Center)
    }
}

/**
 * The stats strip, gutter to gutter ([width]), from [top] dp under the tab row ([HomeBoard.stripY] on Grid Home,
 * [homeStripTop]). [HOME_STRIP_TYPE] text on [lh] dp line boxes ([homeStripLineH]), [pitch] dp from box top to box top
 * ([homeStripPitch]). The labels are grey, the values in the ink, the dot grey at 55%. [lines] 1 with a Most played
 * runs both parts on as one line, the dot padded 11 dp between. Each line starts at the left gutter, under the
 * left-aligned tab row ([MenuTabRow]); [centred] (Carousel Home, over its centred row) centres each line in [width].
 * Only Most played's title gives way: its `weight(1f, fill = false)` takes what is left after the fixed parts, so a
 * long line still fills the width and ellipsizes the title.
 */
@Composable
internal fun HomeStripLines(strip: HomeStrip, lines: Int, gutter: Float, top: Float, width: Float, lh: Float, pitch: Float, centred: Boolean = false) {
    val most = strip.most
    val across = if (centred) Arrangement.Center else Arrangement.Start
    val line = Modifier.fillMaxWidth().height(lh.dp)
    Box(Modifier.offset(gutter.dp, top.dp).width(width.coerceAtLeast(0f).dp)) {
        if (lines == 1 && most != null) {
            Row(line, across, Alignment.CenterVertically) {
                StripMonth(strip); StripDot(11f); StripMost(most)
            }
        } else {
            Row(line, across, Alignment.CenterVertically) { StripMonth(strip) }
            if (most != null && lines >= 2) Row(line.offset(y = pitch.dp), across, Alignment.CenterVertically) { StripMost(most) }
        }
    }
}

/** One piece of strip text, one line, never wrapping. */
@Composable
private fun StripText(text: String, color: Color, modifier: Modifier = Modifier) =
    nxTextScale().let { k -> HomeText(text, color, HOME_STRIP_TYPE.sp * k, HOME_STRIP_TYPE.lineH, maxLines = 1, modifier = modifier) }   // Appearance → Text size

@Composable
private fun StripDot(pad: Float) =
    StripText("·", LocalNxColors.current.hint.copy(alpha = 0.55f), Modifier.padding(horizontal = pad.dp))

/** "This month ‹played› · ‹n› achievements" (signed out: the played time alone). */
@Composable
private fun RowScope.StripMonth(strip: HomeStrip) {
    val colors = LocalNxColors.current
    StripText(THIS_MONTH, colors.hint)
    Spacer(Modifier.width(6.dp))
    StripText(strip.played, if (strip.playedDim) colors.hint else colors.listText)
    val ach = strip.achievements ?: return
    StripDot(3.4f)
    StripText(ach, colors.listText); StripText(" achievements", colors.hint)
}

/** "Most played ‹title› ‹time›": the title alone ellipsizes. */
@Composable
private fun RowScope.StripMost(m: HomeStripMost) {
    val colors = LocalNxColors.current
    StripText(MOST_PLAYED, colors.hint)
    StripText(m.title, colors.listText, Modifier.padding(start = 5.dp, end = 6.dp).weight(1f, fill = false))
    m.time?.let { StripText(it, colors.hint) }
}

/** A selected card's [NX_TILE_RING_W] ring (a game tile's) in [color] round its rounded card, outside its bounds (the parent must not clip it). */
private fun Modifier.homeRing(lit: Boolean, color: Color): Modifier = if (!lit) this else drawBehind {
    val o = NX_TILE_RING_W.dp.toPx()
    drawRoundRect(color, topLeft = Offset(-o, -o), size = Size(size.width + 2 * o, size.height + 2 * o), cornerRadius = CornerRadius(NX_TILE_CORNER.dp.toPx() + o))
}

/**
 * A card's ground: unlit, the tile plate (black, 1 dp edge at 8%); lit, the accent highlight ([fill]) or the plain
 * background (a game, whose ring shows).
 */
@Composable
private fun Modifier.homeCard(lit: Boolean, fill: Boolean): Modifier {
    val colors = LocalNxColors.current
    val shaped = clip(RoundedCornerShape(NX_TILE_CORNER.dp))
    return when {
        !lit -> shaped.tilePlate(colors.background, colors.main)
        fill -> shaped.background(nxHighlight())
        else -> shaped.background(colors.background)
    }
}

/**
 * Continue: the last game's resume frame, else its screenshot, hero, grid, else its abstract picture ([continuePictures],
 * all through the one image loader). The picture is cropped, framed at 40% from the top, then zoomed [HOME_CONTINUE_ZOOM]
 * about that point and clipped to the card. Over it, the pinned games' caption fade ([homeCaptionFade], the bottom 80%)
 * holds the name (up to 2 balanced lines where the card holds them) and the time ([info], TIME only) at the name's size.
 * With no picture at all it is a title card: the name large in the middle, the time at the bottom. A "Continue"
 * [HomeBadge] sits top-left; selected, the ring.
 */
@Composable
private fun ContinueCard(row: MainRow.Continue, lit: Boolean, info: List<InfoLine>, portrait: Boolean, modifier: Modifier) {
    val colors = LocalNxColors.current
    val game = row.entry.game
    val pictures = remember(row) { continuePictures(row.resume.previewPath, row.entry) }
    val failed = remember(row) { mutableIntStateOf(0) }   // pictures that would not load, in order: the next one shows
    val picture = pictures.getOrNull(failed.intValue)
    // The info at the game list Carousel card's size, the name a step under its name (Appearance → Text size), each
    // on its own line ratio.
    val k = nxTextScale()
    val titleSp = NxTextSizes.secondary(nxCardTitleSize()).value; val titleLine = titleSp * HOME_CARD_TITLE_LINE / HOME_CARD_TITLE_SP
    val infoSp = nxCardStatsSize().value; val infoLine = infoSp * HOME_CARD_INFO_LINE / HOME_CARD_INFO_SP
    // The resume frame is a copy of the whole game view: trimmed of its letterbox first ([homeCoverTrim]), off the
    // main thread in the load, so the crop always covers the card.
    val context = LocalPlatformContext.current
    val model: Any? = remember(picture, context) {
        if (picture != null && picture == row.resume.previewPath) ImageRequest.Builder(context).data(picture).transformations(homeCoverTrim()).build()
        else picture
    }
    val rows = backdropCaptionRows(info)   // the time only ([timeOnly]); none without it
    Box(modifier.homeRing(lit, nxHighlight())) {
        BoxWithConstraints(Modifier.fillMaxSize().clip(RoundedCornerShape(NX_TILE_CORNER.dp)).background(colors.background)) {
            if (picture != null) {
                AsyncImage(
                    model = model, contentDescription = null, contentScale = ContentScale.Crop,
                    alignment = BiasAlignment(0f, PICTURE_BIAS),
                    // Zoomed about its crop point so a black screen border falls outside the card (which clips it).
                    modifier = Modifier.fillMaxSize().graphicsLayer {
                        scaleX = HOME_CONTINUE_ZOOM; scaleY = HOME_CONTINUE_ZOOM
                        transformOrigin = TransformOrigin(HOME_CONTINUE_PIVOT_X, HOME_CONTINUE_PIVOT_Y)
                    },
                    onError = { failed.intValue++ },
                )
                // The pinned games' caption fade, over the card's bottom 80%; the text sits on it at the bottom.
                Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(HOME_CAPTION_SHARE).homeCaptionFade(colors.background))
                // The name wraps to a second, balanced line where the card holds it ([homeCaptionTitleLines]),
                // growing upward from the bottom-anchored block; the time sits under it a step smaller, as on the
                // pinned games' cards ([nxCardTitleSize] the name, [nxCardStatsSize] the time).
                val gap = if (portrait) 1.7f else 2.55f
                val bottom = if (portrait) 8.5f else 10.2f
                val scale = LocalDensity.current.fontScale
                val line = titleLine * scale
                val lines = homeCaptionTitleLines(maxHeight.value, line, bottom + rows.size * (infoLine * scale + gap))
                Column(
                    Modifier.align(Alignment.BottomStart).fillMaxWidth()
                        .padding(horizontal = if (portrait) 10.2.dp else 13.6.dp).padding(bottom = bottom.dp),
                    verticalArrangement = Arrangement.spacedBy(gap.dp),
                ) {
                    HomeText(
                        game.shownName, colors.listText, titleSp, titleLine, maxLines = lines, balanced = lines > 1,
                        shadow = if (lines > 1) titleShadow() else null,
                    )
                    for (r in rows) InfoRow(r, infoSp, infoLine)
                }
            } else {
                Column(
                    Modifier.fillMaxSize().padding(
                        start = if (portrait) 12.dp else 16.dp, end = if (portrait) 12.dp else 16.dp,
                        top = if (portrait) 10.dp else 14.dp, bottom = if (portrait) 10.dp else 12.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(if (portrait) 2.dp else 3.dp),
                ) {
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        HomeText(
                            game.shownName, colors.listText, (if (portrait) 19f else 24f) * k * NxTextSizes.STEP, (if (portrait) 23f else 29f) * k * NxTextSizes.STEP,
                            maxLines = 2, align = TextAlign.Center, balanced = true,
                        )
                    }
                    for (r in rows) InfoRow(r, infoSp, infoLine)
                }
            }
            // The card's subtle edge over its picture too; selected, the ring takes its place.
            if (!lit) Box(Modifier.fillMaxSize().border(1.dp, colors.main.copy(alpha = 0.08f), RoundedCornerShape(NX_TILE_CORNER.dp)))
            // The Continue badge, top-left, as on Carousel Home ([HomeBadge]); Pick a game has none.
            HomeBadge(HOME_CONTINUE_BADGE, portrait, Modifier.align(Alignment.TopStart))
        }
    }
}

/** Grid Home's Continue badge ([HomeBadge]), the same word as the Carousel's. */
private const val HOME_CONTINUE_BADGE = "Continue"

// Home's card captions, Continue's as the pinned games' (NxMenuTile's Home caption): the name, and its time a step under.
private const val HOME_CARD_TITLE_SP = 13f
private const val HOME_CARD_TITLE_LINE = 16f
private const val HOME_CARD_INFO_SP = 11f
private const val HOME_CARD_INFO_LINE = 14f


/**
 * A Home card's badge, top-left ("Continue", and on Carousel Home a shelf game's shelf name; as the handheld's `drawContinueBadge`):
 * black at [HOME_BADGE_ALPHA] in a round pill, the word in the ink at 12 sp on a 16 sp line ([pinned]: never font-scaled),
 * padded 3 x 9 dp, its corner [inset] dp in from the card's.
 */
@Composable
internal fun HomeBadge(text: String, portrait: Boolean, modifier: Modifier = Modifier) {
    val inset = if (portrait) 8.dp else 10.dp   // three quarters of Continue's caption inset (10.2 / 13.6 dp)
    Box(
        modifier.padding(start = inset, top = inset)
            .background(Color.Black.copy(alpha = HOME_BADGE_ALPHA), RoundedCornerShape(50))
            .padding(horizontal = 9.dp, vertical = 3.dp),
    ) {
        val pin = LocalDensity.current.fontScale   // pinned: the sizes divided by the font scale draw at 12 and 16
        HomeText(text, LocalNxColors.current.listText, 12f / pin, 16f / pin, maxLines = 1)
    }
}

/** A [HomeBadge]'s black, as the handheld's (158 of 255). */
private const val HOME_BADGE_ALPHA = 0.62f

/** One of Continue's info rows ([infoText]), grey, on one line. */
@Composable
private fun InfoRow(lines: List<InfoLine>, sizeSp: Float, lineSp: Float) {
    val colors = LocalNxColors.current
    val info = infoText(lines, colors.hint, icon = if (sizeSp < 14f) 11.dp else 12.dp)
    HomeText(info.text, colors.hint, sizeSp, lineSp, maxLines = 1, inlineContent = info.inlineContent)
}

/**
 * A fresh install's only card: an abstract picture in the accent's palette ([abstractAccentArtUrl]; Crop, through the one
 * image loader), the NX mark in front of it (centred, the drawn mark 60% of the card's [cardH], [homeMarkAlpha] of the
 * ink), then "Pick a game" over grey "Nothing played yet" with a soft shadow so they read on any palette. Selected:
 * Continue's ring, so the picture stays.
 */
@Composable
internal fun PickCard(lit: Boolean, portrait: Boolean, cardH: Float, modifier: Modifier) {
    val colors = LocalNxColors.current
    val url = remember(colors.accent) { abstractAccentArtUrl(colors.accent.toArgb() and 0xFFFFFF) }
    Box(modifier.homeRing(lit, nxHighlight())) {
        Box(Modifier.fillMaxSize().clip(RoundedCornerShape(NX_TILE_CORNER.dp)).background(colors.background), contentAlignment = Alignment.Center) {
            AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            Image(
                painterResource(homePickMark), contentDescription = null, contentScale = ContentScale.Fit,
                colorFilter = ColorFilter.tint(colors.listText.copy(alpha = homeMarkAlpha(false))),
                // Wider than a narrow card is fine: the mark itself is 41% of the box, and the card clips the rest.
                modifier = Modifier.requiredSize(homeMarkBox(cardH).dp),
            )
            PickText(portrait)
            // The card's subtle edge over its picture; selected, the ring takes its place.
            if (!lit) Box(Modifier.fillMaxSize().border(1.dp, colors.main.copy(alpha = 0.08f), RoundedCornerShape(NX_TILE_CORNER.dp)))
        }
    }
}

@Composable
private fun PickText(portrait: Boolean) {
    val colors = LocalNxColors.current
    val shadow = Shadow(Color.Black.copy(alpha = PICK_TEXT_SHADOW_ALPHA), Offset(0f, 1f), 8f)
    Column(
        Modifier.fillMaxSize().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically), horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        HomeText(PICK_A_GAME, colors.listText, if (portrait) 20f else 24f, if (portrait) 24f else 29f, maxLines = 1, align = TextAlign.Center, shadow = shadow)
        HomeText(NOTHING_PLAYED, colors.hint, if (portrait) 12f else 14f, if (portrait) 16f else 18f, maxLines = 1, align = TextAlign.Center, shadow = shadow)
    }
}

/** The Pick card text's shadow: black at this alpha, 1 px down, 8 px blur; soft, just enough to lift it off a light palette. */
private const val PICK_TEXT_SHADOW_ALPHA = 0.6f

@Composable
private fun HomeText(
    text: String, color: Color, sizeSp: Float, lineSp: Float, maxLines: Int,
    modifier: Modifier = Modifier, align: TextAlign = TextAlign.Start, balanced: Boolean = false, shadow: Shadow? = null,
) = HomeText(AnnotatedString(text), color, sizeSp, lineSp, maxLines, modifier, align, balanced, shadow = shadow)

/** Home's text: [sizeSp] on [lineSp] line boxes; text past [maxLines] ellipsizes. */
@Composable
private fun HomeText(
    text: AnnotatedString, color: Color, sizeSp: Float, lineSp: Float, maxLines: Int,
    modifier: Modifier = Modifier, align: TextAlign = TextAlign.Start, balanced: Boolean = false,
    inlineContent: Map<String, InlineTextContent> = emptyMap(), shadow: Shadow? = null,
) {
    Text(
        text, modifier, color = color, maxLines = maxLines, overflow = TextOverflow.Ellipsis, inlineContent = inlineContent,
        style = TextStyle(
            fontFamily = LocalNxFont.current, fontWeight = FontWeight.SemiBold, fontSize = sizeSp.sp, lineHeight = lineSp.sp,
            textAlign = align, lineBreak = if (balanced) LineBreak.Heading else LineBreak.Unspecified, shadow = shadow,
        ),
    )
}
