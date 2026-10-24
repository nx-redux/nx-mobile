package com.nxredux.mobile.ui.nx

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.nxredux.mobile.ui.InfoKind
import com.nxredux.mobile.ui.InfoLine
import com.nxredux.mobile.ui.HOME_SHELF_MAX
import com.nxredux.mobile.ui.homeShelfMax
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/** Home's screen shape ([homeArrangement]): [WIDE] a very wide menu area ([homeBoardWide]), [SPLIT] every other. */
enum class HomeArrangement { SPLIT, WIDE }

/** Home's discovery shelves, in page order, with their labels. */
enum class HomeShelf(val label: String) { NEW("New"), NOT_STARTED("Not started"), PICK_UP("Pick up again") }

/** A rectangle on Home's page, in dp from the body's top left. */
data class HomeRect(val x: Float, val y: Float, val w: Float, val h: Float)

/** Above the top section and below the Pinned row: the mockup's 52 dp page padding less the 44 dp bar it sits under. */
internal const val HOME_PAGE_MARGIN = 8f
private const val RING_ROOM = NX_TILE_RING   // clear above and below the tiles, for the selected game's ring
private const val LAND_ROW_SHARE = 0.4f   // landscape: a pin row takes 40% of the top section + first row, between 4:3 and square
/**
 * The shortest a halves screen's top section gets (below it the page scrolls instead): the mockup's 145 dp on the
 * portrait type and 165 on the landscape type.
 */
private fun homeMinTop(portrait: Boolean): Float = if (portrait) 145f else 165f

/**
 * Narrower than this, a tall halves screen is a phone: its tools go in a row under Continue ([HomeToolMode.UNDER]).
 * The portrait type is always [HomeToolMode.COLUMN].
 */
private const val HOME_PHONE_MAX_W = 600f

/** The narrowest a grid column gets: under it the grid drops a column. */
private const val HOME_MIN_TILE_W = 90f

/** A halves screen's top section is at most its half width / this tall: the Fold cover's shape (215.5 × 184.19 at most). */
private const val HOME_HALF_SHAPE = 1.17f

/**
 * Everything on the first screen but the top section and the first row: the two margins, the gap under the top (which
 * holds the row's ring room above it) and the ring room under the row. 31 dp in portrait, 33 otherwise.
 */
private fun pageFixed(gap: Float): Float = 2 * HOME_PAGE_MARGIN + gap + RING_ROOM

/** The tab row and the hint bar Home's body sits between (44 dp each): the menu area is the body + 88. */
private const val HOME_BARS = 88f

/**
 * Wide: the menu area (the app area inside the safe insets) is at least this many times as wide as tall. That takes in
 * every 20:9 phone in landscape (about 2.2–2.3; the mockup's 960 × 432 is 2.22) and the Flip (2.44); 16:9 (about 1.9)
 * and the unfolded Fold (1.33) stay split.
 */
private const val WIDE_RATIO = 2.15f

internal fun homeGutter(portrait: Boolean): Float = if (portrait) 16f else 24f

/**
 * How Home's top section holds the pinned tools' squares ([homeBoard]).
 * [COLUMN]: columns beside Continue, on the portrait type and a tall landscape-type body at least [HOME_PHONE_MAX_W] wide.
 * [SPS3]: a landscape split, one column beside Continue (two with more tools).
 * [UNDER]: a row of five under Continue, on a tall landscape-type window narrower than [HOME_PHONE_MAX_W].
 * [LARGE]: the large face, such as the unfolded Fold ([homeBoardLarge]).
 * [WIDE]: the wide face, such as the Flip and 20:9 phones in landscape ([homeBoardWide]).
 */
enum class HomeToolMode { COLUMN, SPS3, UNDER, LARGE, WIDE }

/**
 * Home's body as last laid out: [w] × [h] dp between the bars, the [portrait] type, and the menu area's [menuRatio].
 * A hidden hint bar keeps its room, so it is never in [h].
 * [textScale]: the system font scale the text is drawn at; the strip's line boxes grow with it ([homeStripLineH]).
 * [stripScale]: Appearance → Text size's scale of the strip's text ([nxTextScale]): its ink (and so its centring)
 * follows it; its line boxes, and so the top section, do not.
 * [titleHidden]: the tab row is hidden and its [MENU_TAB_ROW_HEIGHT] is in [h], at the top. The strip moves up into it
 * and the top section gains it; the face is still read as with the tab row shown ([homeShownH]).
 * [statsHidden]: Layouts → Home stats is off, so the stats strip is not drawn and its room goes to the top section
 * ([homeStatsRoom]). It is in the frame so AppState's board and HomeScreen's agree.
 */
data class HomeFrame(
    val w: Float, val h: Float, val portrait: Boolean, val menuRatio: Float? = null, val textScale: Float = 1f,
    val titleHidden: Boolean = false, val statsHidden: Boolean = false, val stripScale: Float = 1f,
)

/** The tab row's room Home's body takes while it is hidden ([HomeFrame.titleHidden]). */
private val HOME_TITLE_ROOM = MENU_TAB_ROW_HEIGHT.value

/**
 * Home with the tab row hidden (Grid, Carousel and List): the stats strip goes in a header in the tab row's place,
 * [height] dp tall (at least the tab row's, more when the strip's [lines] need it), its lines [stripTop] dp from its top,
 * centred. Grid's header stays put over its scrolling page. The page below is laid out in [body], as under a tab row
 * with the strip hidden.
 */
internal data class HomeStatsHeader(val height: Float, val lines: Int, val stripTop: Float, val body: HomeFrame)

/** [HomeStatsHeader] for Home's frame [f] and the strip's [stripLines] (0: none); null with the tab row shown. */
internal fun homeStatsHeader(f: HomeFrame, stripLines: Int): HomeStatsHeader? {
    if (!f.titleHidden) return null
    val lines = homeStripLinesShown(f, stripLines)
    val block = if (lines == 0) 0f else (lines - 1) * homeStripPitch(f) + homeStripLineH(f)
    val height = max(MENU_TAB_ROW_HEIGHT.value, block + 2 * HOME_GRID_HEADER_PAD)
    return HomeStatsHeader(height, lines, (height - block) / 2, f.copy(h = f.h - height, titleHidden = false, statsHidden = true))
}

/** The least room above and below the header's strip. */
private const val HOME_GRID_HEADER_PAD = 4f

/** [f]'s body height as it would be with the tab row shown: what decides the face. */
internal fun homeShownH(f: HomeFrame): Float = f.h - (if (f.titleHidden) HOME_TITLE_ROOM else 0f)

/**
 * The room the stats strip's [stripLines] take above the top section, which the top section takes while Home stats
 * is off ([HomeFrame.statsHidden]): 40 dp for two lines at a text scale of 1, 20 for one; 0 with the strip shown.
 */
internal fun homeStatsRoom(f: HomeFrame, stripLines: Int): Float =
    if (f.statsHidden) homeTopY(f, stripLines) - homeTopY(f, 0) else 0f

/** The strip lines drawn on [f]: none while Home stats is off ([HomeFrame.statsHidden]). */
internal fun homeStripLinesShown(f: HomeFrame, stripLines: Int): Int = if (f.statsHidden) 0 else stripLines

/** What a hidden tab row adds to a top section ([HOME_TITLE_ROOM]): bigger squares, never another row ([HOME_TOOL_ROWS]). */
private fun homeTitleRoom(f: HomeFrame): Float = if (f.titleHidden) HOME_TITLE_ROOM else 0f

/** A top section tile's kind. [GAME]: a pinned game in the top section ([HomeBoard.inlineGames], every landscape face). */
enum class HomeTopKind { CONTINUE, GAME, TOOL, MORE }

/** One focusable tile of the top section, in page coordinates. */
data class HomeTopTile(val kind: HomeTopKind, val rect: HomeRect)

/**
 * Every rectangle on Home's page ([homeBoard]). [top] is in focus order: Continue, the inline games, then the squares in
 * reading order (MORE last). [inlineGames]: the pinned games drawn in the top section rather than the rows (up to 2, on
 * every landscape face but UNDER). [labelH]: a shelf label's line box ([homeLabelLineH]). [shelfCols]: each shelf's tiles a
 * row, matching [shelfSizes] (the pins' [perRow]: 4 on the large face).
 */
data class HomeBoard(
    val mode: HomeToolMode, val wide: Boolean, val stripLines: Int, val topY: Float, val topH: Float,
    val top: List<HomeTopTile>, val slots: Int, val more: Int,
    val gutter: Float, val gap: Float, val perRow: Int, val pinW: Float, val pinH: Float,
    val tilesY: Float, val rowGames: Int, val pageH: Float, val square: Float, val stripY: Float = HOME_STRIP_TOP,
    val labels: List<HomeLabel> = emptyList(), val shelfTiles: List<HomeRect> = emptyList(), val shelfSizes: List<Int> = emptyList(),
    val inlineGames: Int = 0, val labelH: Float = HOME_LABEL_H, val shelfCols: List<Int> = emptyList(),
)

private const val HOME_STRIP_TOP = 2f        // the strip's room under the tab row in topY (its own offset is [homeStripTop])
private const val HOME_STRIP_LINE_GAP = 2f

/** The stats strip's type: [sp] text on [lineH] dp line boxes. The composable and [homeBoard] both read it. */
internal class HomeStripType(val sp: Float, val lineH: Float)

/** The strip's type at a text scale of 1: 14 sp on 18 dp lines, on both types. */
internal val HOME_STRIP_TYPE = HomeStripType(sp = 14f, lineH = 18f)

/**
 * The strip's line box on [f]: [HOME_STRIP_TYPE]'s times its text scale, never under 18 dp. A smaller system font
 * shrinks the text inside the box; a larger one grows the box with it, so nothing clips.
 */
internal fun homeStripLineH(f: HomeFrame): Float = HOME_STRIP_TYPE.lineH * max(1f, f.textScale)

/** The top section's top on [f] with the strip's [stripLines]: under the strip's lines and the margin, else the margin alone. */
private fun homeTopY(f: HomeFrame, stripLines: Int): Float =
    if (stripLines > 0) HOME_STRIP_TOP + stripLines * homeStripLineH(f) + (stripLines - 1) * HOME_STRIP_LINE_GAP + HOME_PAGE_MARGIN else HOME_PAGE_MARGIN

/** The Fold cover's body (the portrait type, the strip on two lines): the reference for [homePortraitTopCap]. */
private val HOME_FOLD_COVER = HomeFrame(475f, 291f, true)

/**
 * The portrait frame [f]'s portrait pieces are sized on, with its text scales and hidden rows: the Fold cover, whose
 * body is the portrait type's top-section cap ([homePortraitTopCap]), so its squares are any portrait phone's.
 */
internal fun homePortraitTwin(f: HomeFrame): HomeFrame =
    HOME_FOLD_COVER.copy(textScale = f.textScale, titleHidden = f.titleHidden, statsHidden = f.statsHidden)

/**
 * The portrait type's top section is at most this tall: the Fold cover's whole body under its two strip lines at a text
 * scale of 1 (291 − 48 − 8 = 235 dp). A shorter body takes all of it; a taller one (the Flip's inner display) stops here
 * and its pin rows follow.
 */
private fun homePortraitTopCap(): Float = HOME_FOLD_COVER.h - homeTopY(HOME_FOLD_COVER, 2) - HOME_PAGE_MARGIN

/** [f]'s cap: [homePortraitTopCap], plus the tab row's room while it is hidden (279 dp). */
private fun homePortraitTopCap(f: HomeFrame): Float = homePortraitTopCap() + homeTitleRoom(f)

/**
 * The bundled MiSans Latin SemiBold, per em: its line metrics (hhea ascent 1044, descent 282: a natural line is 1.326 em, the
 * baseline (ascent − descent) / 2 below a line box's centre) and its visible text: the cap-top (digits 769/1000,
 * capitals 755) above the baseline and the descent (g 239, y and p 225) below it.
 */
internal const val NX_FONT_ASCENT = 1.044f
internal const val NX_FONT_DESCENT = 0.282f
internal const val NX_FONT_LINE = NX_FONT_ASCENT + NX_FONT_DESCENT
internal const val HOME_FONT_CAP = 0.77f
internal const val HOME_FONT_DESC = 0.24f

/**
 * The room round the two strip lines' ink, as a multiple of the room between them (the first line's descent to the
 * second's cap-top), so the two lines read as one block. Set for the tab row hidden, the tighter case; with it shown the
 * room above and below grows and the lines stay put. A ratio rather than an em size, so it holds at any text scale.
 */
private const val HOME_STRIP_ROUND_TO_BETWEEN = 2f

/** The strip's visible text height on [f], cap-top to descent ([HOME_FONT_CAP] + [HOME_FONT_DESC]), dp. */
private fun homeStripInkH(f: HomeFrame): Float = (HOME_FONT_CAP + HOME_FONT_DESC) * HOME_STRIP_TYPE.sp * f.textScale * f.stripScale

/**
 * From one strip line box's top to the next's, dp: the ink plus the room between the lines. The free room round two
 * lines with the tab row hidden (from the body's top to Continue's ring) is shared 2 : 1 : 2 above, between and below
 * ([HOME_STRIP_ROUND_TO_BETWEEN]). The boxes may overlap; they draw no background. [HomeBoard.topY] still reserves
 * [HOME_STRIP_LINE_GAP] between the boxes, so the top section does not move; what the tighter lines save goes round the block.
 */
internal fun homeStripPitch(f: HomeFrame): Float {
    val ink = homeStripInkH(f)
    val free = homeTopY(f, 2) - NX_TILE_RING - 2 * ink
    return ink + free / (2 * HOME_STRIP_ROUND_TO_BETWEEN + 1)
}

/**
 * The strip's first line box, in dp from the body's top, placed so the strip's text sits centred between the selected
 * tab's underline ([homeUnderlineAbove]; the tab words are pinned, so it never moves) and the selected Continue's ring
 * ([NX_TILE_RING] above topY, Home's first focus): the underline to the first line's cap-top equals the last line's
 * descent to the ring. With the tab row hidden the body's top stands in for the underline. The [lines] are
 * [homeStripPitch] apart. Only the strip moves; topY does not.
 */
internal fun homeStripTop(f: HomeFrame, lines: Int): Float {
    val lh = homeStripLineH(f)
    val sp = HOME_STRIP_TYPE.sp * f.textScale * f.stripScale
    val baseline = lh / 2 + (NX_FONT_ASCENT - NX_FONT_DESCENT) / 2 * sp   // in a line box, from its top
    val capTop = baseline - HOME_FONT_CAP * sp
    val inkBottom = baseline + HOME_FONT_DESC * sp
    val underline = homeUnderlineAbove(f)
    val n = max(1, lines)
    // above = underline + top + capTop; below = topY − ring − (top + (n − 1) · pitch + inkBottom).
    return (homeTopY(f, n) - NX_TILE_RING - (n - 1) * homeStripPitch(f) - inkBottom - underline - capTop) / 2
}

/** How far the selected tab's underline sits above [f]'s body top ([menuTabUnderlineBottom]); 0 with the tab row hidden. */
internal fun homeUnderlineAbove(f: HomeFrame): Float =
    if (f.titleHidden) 0f else MENU_TAB_ROW_HEIGHT.value - menuTabUnderlineBottom()

/**
 * The strip's visible text on [f] over [lines] lines ([homeStripPitch] apart): the first cap-top's depth in its line
 * box, and the ink's height from that cap-top down to the last line's descent. List and Carousel Home space their rows
 * by it, as Grid Home does with [homeStripTop].
 */
internal fun homeStripInk(f: HomeFrame, lines: Int): Pair<Float, Float> {
    val sp = HOME_STRIP_TYPE.sp * f.textScale * f.stripScale
    val baseline = homeStripLineH(f) / 2 + (NX_FONT_ASCENT - NX_FONT_DESCENT) / 2 * sp
    val capTop = baseline - HOME_FONT_CAP * sp
    return capTop to ((max(1, lines) - 1) * homeStripPitch(f) + HOME_FONT_DESC * sp + HOME_FONT_CAP * sp)
}

internal fun homeIsWide(f: HomeFrame): Boolean = homeArrangement(f.w, homeShownH(f), f.menuRatio) == HomeArrangement.WIDE

/** A landscape body at least this wide (and not WIDE) is the large face ([homeIsLarge]): the unfolded Fold's 933. */
private const val HOME_LARGE_MIN_W = 840f
/** The large face's pin columns (every pinned game one tile) and shelf columns. */
private const val HOME_LARGE_PIN_COLS = 4
internal const val HOME_SHELF_COLS = 4   // a board's shelf columns when it names none
/** A band's label: an 18 dp line ([homeLabelLineH] on a frame), 6 dp above its tiles, in the gap above the band. */
private const val HOME_LABEL_H = 18f
private const val HOME_LABEL_GAP = 6f

/**
 * A band label's line box on [f]: [HOME_LABEL_H] times its text scale, never under it, as the strip's ([homeStripLineH]).
 * HomeScreen draws the label at 13 sp on 18 sp lines times that scale, so a large font does not run into the tiles.
 */
internal fun homeLabelLineH(f: HomeFrame): Float = HOME_LABEL_H * max(1f, f.textScale)

/** A band's label: its [text] and its line box's top, in page dp. */
data class HomeLabel(val text: String, val y: Float)

/** The large face's body is at least this tall too: an 846 × 324 21:9 landscape is wide enough, but too short. */
private const val HOME_LARGE_MIN_H = 480f

/** The large face: landscape, not WIDE, not a tall window, the body [HOME_LARGE_MIN_W] wide or more and [HOME_LARGE_MIN_H] tall or more. */
internal fun homeIsLarge(f: HomeFrame): Boolean =
    homeShownH(f).let { h -> !f.portrait && h <= f.w && !homeIsWide(f) && f.w >= HOME_LARGE_MIN_W && h >= HOME_LARGE_MIN_H }

/**
 * The squares a column on every face but UNDER (its 5 in a row), always: a hidden tab row or strip makes them bigger
 * (to each face's cap), never adds a row.
 */
private const val HOME_TOOL_ROWS = 5

/**
 * The most games a Grid Home shelf shows on a board [perRow] games a row: 4 in [portrait] (two rows of 2) and on a
 * landscape grid of 4 or more a row (the large face's one row), else 3 (a narrow landscape's row of 3).
 */
internal fun homeShelfCap(portrait: Boolean, perRow: Int): Int = if (portrait || perRow >= 4) HOME_SHELF_MAX else HOME_SHELF_MAX - 1

/**
 * The top section's squares for [tools] pinned tools on [mode] with [rows] squares a column ([HOME_TOOL_ROWS]): UNDER
 * 5, the others one column. On SPS3 and the [portrait] type's COLUMN, more tools than that widen the block to two
 * columns (2 · rows).
 */
private fun homeSlots(mode: HomeToolMode, tools: Int, portrait: Boolean, rows: Int): Int = when {
    mode == HomeToolMode.UNDER -> 5
    (mode == HomeToolMode.COLUMN && portrait) || mode == HomeToolMode.SPS3 -> if (tools > rows) 2 * rows else rows
    else -> rows
}

/**
 * Home's page for [f] with the strip's [stripLines], [tools] pinned tools and [games] pinned games (Continue's own game
 * left out), then the [shelves] under the pins ([homeBands]). The large and wide faces are [homeBoardLarge] and
 * [homeBoardWide]; this lays out the split faces. In landscape (SPS3, a halves COLUMN) the first 1–2 pinned games sit
 * inline between Continue and the squares ([homeInlineColumn]), as on the wide and large faces, the rest are row games;
 * in portrait and on UNDER every pinned game is a row game.
 *
 * The pins sit on a column grid (6 columns landscape, 4 halves, dropping one while a column is under 90 dp), a game two
 * columns wide. On every face the top section is the body (to its cap) and the row games start below it, a scroll away. The
 * portrait type is always COLUMN: its top section is the body, at most the Fold cover's ([homePortraitTopCap]), never
 * squeezed for a pin row; its pin rows are [HOME_PORTRAIT_PIN_TALL] times their 4:3 shape and scroll.
 *
 * The squares sit flush right. On the portrait type they fill columns of 5 top to bottom, rows half a gap apart, a
 * second column left of the first past 5 tools. On COLUMN and SPS3 they fill the top section's height the same way,
 * rows half a gap apart, in reading order (SPS3 two columns once the tools pass a column, [homeSlots]). UNDER puts 5 in a row
 * under Continue. Continue runs to a gap before the squares' leftmost column, else the full width.
 *
 * With the tab row hidden ([HomeFrame.titleHidden]) its 44 dp go to the top section (the caps too): every column keeps
 * its 5 ([HOME_TOOL_ROWS]) and the squares grow, UNDER keeps its 5 in a row, and the pin rows keep the shape they have
 * with the tab row shown. With Home stats off ([HomeFrame.statsHidden]), [stripLines] are the lines the strip would
 * take; the board's lines are 0 and their room ([homeStatsRoom]) goes to the top section the same way: Continue grows,
 * the caps rise by it (so the portrait type's pins start where they did), and the squares grow.
 */
internal fun homeBoard(f: HomeFrame, stripLines: Int, tools: Int, games: Int, shelves: List<Pair<HomeShelf, Int>> = emptyList()): HomeBoard {
    val freed = homeStatsRoom(f, stripLines)
    val lines = homeStripLinesShown(f, stripLines)
    if (homeIsLarge(f)) return homeBoardLarge(f, lines, freed, tools, games, shelves)
    if (homeIsWide(f)) return homeBoardWide(f, lines, freed, tools, games, shelves)
    val gutter = homeGutter(f.portrait)
    val gap = if (f.portrait) 12f else 14f
    val w = f.w - 2 * gutter
    val shownH = homeShownH(f)   // the pin row's shape and the face as with the tab row shown
    val halves = f.portrait || shownH > f.w
    val (tw, perRow, pinW) = homeColumnGrid(w, gap, if (halves) 4 else 6)
    val shapeH = if (halves) tw * 3 / 4 else min(tw, max(tw * 3 / 4, (shownH - pageFixed(gap)) * LAND_ROW_SHARE))
    val topY = homeTopY(f, lines)
    val body = f.h - topY - HOME_PAGE_MARGIN
    val mode = when {
        f.portrait -> HomeToolMode.COLUMN
        halves -> if (shownH > f.w && f.w < HOME_PHONE_MAX_W) HomeToolMode.UNDER else HomeToolMode.COLUMN
        else -> HomeToolMode.SPS3
    }
    // Landscape SPS3 and COLUMN: up to 2 games inline in the top section, the rest row games.
    val inline = if (!f.portrait && mode != HomeToolMode.UNDER) min(games.coerceAtLeast(0), HOME_INLINE_MAX) else 0
    val rowGames = games.coerceAtLeast(0) - inline
    val under = max(0f, (w - 4 * gap) / 5)
    // Every face's top section is the body (to its cap): row games start below it, on the next screen.
    val cap = (if (mode == HomeToolMode.UNDER) w / 1.5f + gap + under else (w - gap) / 2 / HOME_HALF_SHAPE) + homeTitleRoom(f) + freed
    val topFull = when {
        f.portrait -> max(homeMinTop(true), min(body, homePortraitTopCap(f) + freed))
        halves -> max(homeMinTop(false), min(body, cap))
        else -> max(0f, body)
    }
    val pinFull = if (f.portrait) shapeH * HOME_PORTRAIT_PIN_TALL else shapeH
    val rowsN = HOME_TOOL_ROWS   // UNDER's row is 5 too
    val slots = homeSlots(mode, tools, f.portrait, rowsN)
    val shown = min(tools, slots)
    val more = if (tools > slots) tools - slots + 1 else 0
    // The squares, relative to the top section (x 0 … w, y 0 …); right: where they start.
    val sq = ArrayList<HomeRect>()
    var s = 0f
    var right = w
    var saved = 0f   // what a block's half gaps take off the top section and give the pin row
    if (shown > 0) when {
        // Every side floored at 0: a very short body (a freeform window) leaves the squares nothing, never less.
        // The portrait type: columns from the right (the second left of the first), each top to bottom; the rows and
        // their half gaps are the whole top section, so it saves nothing.
        f.portrait -> {
            val cols = if (slots == 2 * rowsN) 2 else 1
            val vGap = gap / 2
            s = max(0f, (topFull - (rowsN - 1) * vGap) / rowsN)
            saved = topFull - min(topFull, rowsN * s + (rowsN - 1) * vGap)
            right = w - cols * s - (cols - 1) * gap
            repeat(shown) { sq += HomeRect(w - s - (it / rowsN) * (s + gap), (it % rowsN) * (s + vGap), s, s) }
        }
        // A block flush right, in reading order: COLUMN one column, SPS3 two once the tools pass a column. The rows,
        // half a gap apart, fill the top section; the columns sit a full gap apart.
        mode == HomeToolMode.COLUMN || mode == HomeToolMode.SPS3 -> {
            val cols = if (slots == 2 * rowsN) 2 else 1
            val vGap = gap / 2
            s = max(0f, (topFull - (rowsN - 1) * vGap) / rowsN)
            saved = topFull - min(topFull, rowsN * s + (rowsN - 1) * vGap)
            right = w - cols * s - (cols - 1) * gap
            repeat(shown) { sq += HomeRect(right + (it % cols) * (s + gap), (it / cols) * (s + vGap), s, s) }
        }
        mode == HomeToolMode.UNDER -> { s = under; repeat(shown) { sq += HomeRect(it * (s + gap), topFull - s, s, s) } }
        else -> Unit   // LARGE, WIDE: never reached ([homeBoardLarge], [homeBoardWide])
    }
    val topH = topFull - saved
    val pinH = pinFull + saved
    val contH = if (shown > 0 && mode == HomeToolMode.UNDER) max(0f, topH - gap - s) else topH
    val squaresBeside = shown > 0 && mode != HomeToolMode.UNDER
    val col = homeInlineColumn(inline, topH, w, right, squaresBeside, gap)   // no inline games: Continue to the squares
    fun page(r: HomeRect) = HomeRect(gutter + r.x, topY + r.y, r.w, r.h)
    val top = buildList {
        add(HomeTopTile(HomeTopKind.CONTINUE, page(HomeRect(0f, 0f, col.contW, contH))))
        col.cards.forEach { add(HomeTopTile(HomeTopKind.GAME, page(it))) }
        sq.forEachIndexed { i, r -> add(HomeTopTile(if (more > 0 && i == shown - 1) HomeTopKind.MORE else HomeTopKind.TOOL, page(r))) }
    }
    // The shelves under the pin rows ([homeBands]), each in the pins' grid: perRow a row at the pins' size.
    val labelled = shelves.any { it.second > 0 }
    val bands = homeBands(topY + topH, gutter, gap, homeLabelLineH(f), rowGames, perRow, pinH, shelves) { shelf ->
        HomeShelfShape(perRow, pinW, pinH, min(homeShelfMax(shelf), homeShelfCap(f.portrait, perRow)))
    }
    return HomeBoard(
        mode, false, lines, topY, topH, top, slots, more, gutter, gap, perRow, pinW, pinH, bands.tilesY, rowGames, bands.pageH, s,
        homeStripTop(f, lines), bands.labels, bands.tiles, bands.sizes, inlineGames = inline,
        labelH = if (labelled) homeLabelLineH(f) else HOME_LABEL_H, shelfCols = bands.cols,
    )
}

/** One shelf's tiles in [homeBands]: [cols] a row, each [w] × [h], at most [max] of them. */
private class HomeShelfShape(val cols: Int, val w: Float, val h: Float, val max: Int)

/** The bands under Home's top section ([homeBands]): their labels, the pins' top, the shelves' tiles, sizes and columns, the page's end. */
private class HomeBands(
    val labels: List<HomeLabel>, val tilesY: Float, val tiles: List<HomeRect>, val sizes: List<Int>, val cols: List<Int>, val pageH: Float,
)

/**
 * The bands under a top section ending at [above]: the [rowGames] pins ([perRow] a row, [pinH] tall, a gap apart, with
 * no label), then each of [shelves] under its label, in its [shape] (an empty shelf is dropped). A label sits a gap
 * under what is above it, its line [labelH] tall, [HOME_LABEL_GAP] above its tiles. The page ends under the last tile
 * (its ring room and the margin), else under the top section.
 */
private fun homeBands(
    above: Float, gutter: Float, gap: Float, labelH: Float, rowGames: Int, perRow: Int, pinH: Float,
    shelves: List<Pair<HomeShelf, Int>>, shape: (HomeShelf) -> HomeShelfShape,
): HomeBands {
    val labels = ArrayList<HomeLabel>()
    var y = above   // the bottom of what is above
    fun band(text: String): Float { labels += HomeLabel(text, y + gap); return y + gap + labelH + HOME_LABEL_GAP }
    var tilesY = y + gap
    var any = false
    if (rowGames > 0) {
        val rows = (rowGames + perRow - 1) / perRow
        y = tilesY + rows * pinH + (rows - 1) * gap
        any = true
    }
    val tiles = ArrayList<HomeRect>()
    val sizes = ArrayList<Int>()
    val cols = ArrayList<Int>()
    for ((shelf, n) in shelves) {
        val sh = shape(shelf)
        val count = min(n, sh.max)
        if (count <= 0) continue
        val ty = band(shelf.label)
        repeat(count) { tiles += HomeRect(gutter + (it % sh.cols) * (sh.w + gap), ty + (it / sh.cols) * (sh.h + gap), sh.w, sh.h) }
        sizes += count
        cols += sh.cols
        val rows = (count + sh.cols - 1) / sh.cols
        y = ty + rows * sh.h + (rows - 1) * gap
        any = true
    }
    val pageH = if (any) y + RING_ROOM + HOME_PAGE_MARGIN else above + HOME_PAGE_MARGIN
    return HomeBands(labels, tilesY, tiles, sizes, cols, pageH)
}

/**
 * The portrait type's pin rows are this many times their 4:3 half shape tall (tw · 3 / 4 of the 4-column grid): the same
 * width and per-row count, a taller tile, the art cropped to it; the rows scroll. On the Fold cover that is
 * 215.5 × 114.47 dp, close to the cover's landscape pins (225 × 119.5); twice as tall was too large on the device.
 */
internal const val HOME_PORTRAIT_PIN_TALL = 1.5f

/** The wide face's squares are at most this big. */
private const val HOME_WIDE_SQUARE = 58f
/** The inline games' column's share of the width left of the squares ([homeInlineColumn]); Continue takes the rest. */
private const val HOME_INLINE_SHARE = 0.4f
/** The most pinned games a landscape face draws in its top section (stacked); the rest are row games. */
private const val HOME_INLINE_MAX = 2

/** Continue's width and the inline games' cards ([homeInlineColumn]), relative to the top section. */
private class HomeInlineColumn(val contW: Float, val cards: List<HomeRect>)

/**
 * The top section's [inline] games (0–2) on a landscape face, in a top section [topH] tall whose squares (if
 * any) start at [right]. Of the width left of the squares (a gap before them; without them, to [w]) the column takes
 * [HOME_INLINE_SHARE] and Continue the rest, a gap between. One game takes the full height; two stack, each (topH − gap) / 2 tall.
 */
private fun homeInlineColumn(inline: Int, topH: Float, w: Float, right: Float, squares: Boolean, gap: Float): HomeInlineColumn {
    val end = if (squares) right - gap else w
    if (inline <= 0) return HomeInlineColumn(max(0f, end), emptyList())
    val gw = max(0f, (end - gap) * HOME_INLINE_SHARE)
    val contW = max(0f, end - gap - gw)
    val x = contW + gap
    val cards = if (inline == 1) listOf(HomeRect(x, 0f, gw, topH)) else {
        val hh = max(0f, (topH - gap) / 2)
        listOf(HomeRect(x, 0f, gw, hh), HomeRect(x, hh + gap, gw, hh))
    }
    return HomeInlineColumn(contW, cards)
}

/**
 * Home on the wide face (the Flip, 20:9 phones in landscape). The squares: 5 rows ([HOME_TOOL_ROWS]) of side
 * min(58, (topH − 4 · gap / 2) / 5), up to 3 columns (ceil(shown / 5)), flush right, top-aligned, rows half a gap apart.
 * Past the slots the last square is "+N". The first 1–2 [games] sit inline between Continue and the block
 * ([homeInlineColumn], two stacked); the rest are row games on [homeBoard]'s landscape grid. The top section is always
 * the whole body (row games start below it, a scroll away). Continue takes the rest. [freed] is the hidden strip's room
 * ([homeStatsRoom]): the top section starts at the margin and takes it, the squares growing (to 58) as with the tab row hidden.
 */
private fun homeBoardWide(f: HomeFrame, stripLines: Int, freed: Float, tools: Int, games: Int, shelves: List<Pair<HomeShelf, Int>>): HomeBoard {
    val gutter = homeGutter(false)
    val gap = 14f
    val w = f.w - 2 * gutter
    val topY = homeTopY(f, stripLines)
    val inline = min(games.coerceAtLeast(0), HOME_INLINE_MAX)   // the first 2 inline, the rest row games
    val rowGames = games.coerceAtLeast(0) - inline
    // The rows as on the landscape split: 6 columns (fewer while one is under 90 dp), a game two wide, the same row shape.
    val (tw, perRow, pinW) = homeColumnGrid(w, gap, 6)
    val shapeH = min(tw, max(tw * 3 / 4, (homeShownH(f) - pageFixed(gap)) * LAND_ROW_SHARE))
    val topH = max(0f, f.h - topY - HOME_PAGE_MARGIN)   // the whole body: row games start below it, on the next screen
    val pinH = shapeH
    val rowsN = HOME_TOOL_ROWS
    val s = max(0f, min(HOME_WIDE_SQUARE, (topH - (rowsN - 1) * gap / 2) / rowsN))
    val slots = 3 * rowsN
    val shown = min(tools, slots)
    val more = if (tools > slots) tools - slots + 1 else 0
    val cols = (shown + rowsN - 1) / rowsN
    val blockW = if (shown > 0) cols * s + (cols - 1) * gap else 0f
    val right = w - blockW
    val col = homeInlineColumn(inline, topH, w, right, shown > 0, gap)
    val top = homeTopTiles(gutter, topY, topH, col, shown, more, cols, right, s, gap)
    // The shelves under the row games, in their grid, as [homeBoard]'s.
    val labelled = shelves.any { it.second > 0 }
    val labelH = if (labelled) homeLabelLineH(f) else HOME_LABEL_H
    val bands = homeBands(topY + topH, gutter, gap, labelH, rowGames, perRow, pinH, shelves) { shelf ->
        HomeShelfShape(perRow, pinW, pinH, min(homeShelfMax(shelf), homeShelfCap(false, perRow)))
    }
    return HomeBoard(
        HomeToolMode.WIDE, true, stripLines, topY, topH, top, slots, more, gutter, gap, perRow, pinW, pinH, bands.tilesY, rowGames,
        bands.pageH, s, homeStripTop(f, stripLines), bands.labels, bands.tiles, bands.sizes, inline, labelH, bands.cols,
    )
}

/**
 * The wide and large faces' top section, in the page: Continue ([col]'s width, [topH] tall), [col]'s inline cards, then
 * [shown] squares of side [s] from [right] in reading order, [cols] a row (a gap apart, rows half a gap apart), the last
 * one "+N" when there are [more].
 */
private fun homeTopTiles(
    gutter: Float, topY: Float, topH: Float, col: HomeInlineColumn, shown: Int, more: Int, cols: Int, right: Float, s: Float, gap: Float,
): List<HomeTopTile> {
    fun page(r: HomeRect) = HomeRect(gutter + r.x, topY + r.y, r.w, r.h)
    return buildList {
        add(HomeTopTile(HomeTopKind.CONTINUE, page(HomeRect(0f, 0f, col.contW, topH))))
        col.cards.forEach { add(HomeTopTile(HomeTopKind.GAME, page(it))) }
        repeat(shown) {
            val kind = if (more > 0 && it == shown - 1) HomeTopKind.MORE else HomeTopKind.TOOL
            add(HomeTopTile(kind, page(HomeRect(right + (it % cols) * (s + gap), (it / cols) * (s + gap / 2), s, s))))
        }
    }
}

/** The pin grid across [w]: [start] columns (fewer while one is under [HOME_MIN_TILE_W]) of [tw], a pin two wide ([perRow] a row of [pinW]). */
private data class HomeColumnGrid(val tw: Float, val perRow: Int, val pinW: Float)

private fun homeColumnGrid(w: Float, gap: Float, start: Int): HomeColumnGrid {
    var n = start
    var tw = (w - (n - 1) * gap) / n
    while (tw < HOME_MIN_TILE_W && n > 2) { n--; tw = (w - (n - 1) * gap) / n }
    val perRow = max(2, n / 2)
    return HomeColumnGrid(tw, perRow, (w - (perRow - 1) * gap) / perRow)
}

/** Row game [i]'s tile, in the page. */
internal fun homeBoardPin(b: HomeBoard, i: Int): HomeRect =
    HomeRect(b.gutter + (i % b.perRow) * (b.pinW + b.gap), b.tilesY + (i / b.perRow) * (b.pinH + b.gap), b.pinW, b.pinH)

/**
 * Home on the large face. The top section is 2 · S + gap / 2 tall whatever the tools, with S = (w / 2 − 3 · gap) / 3.
 * Continue, then flush right and top-aligned a block of squares, 5 a column (rows half a gap apart) filling the top
 * section's height, at most S / 2, at most 2 columns (ceil(shown / 5), a gap apart, reading order across them); the
 * first 1–2 [games] sit inline between them ([homeInlineColumn], two stacked, as on the wide face). Under it the
 * [homeBands]: the rest of the pins, 4 a row at (w − 3 · gap) / 4 wide and 16:9, then each shelf at the pins' size, up to 4 tiles ([homeShelfCap]).
 * With the tab row hidden the top section is 44 dp taller, and [freed] (the hidden strip's room, [homeStatsRoom]) makes
 * it that much taller again from the margin, so the bands below stay put; a column keeps its 5 and they grow (to S / 2).
 */
private fun homeBoardLarge(f: HomeFrame, stripLines: Int, freed: Float, tools: Int, games: Int, shelves: List<Pair<HomeShelf, Int>>): HomeBoard {
    val gutter = homeGutter(false)
    val gap = 14f
    val w = f.w - 2 * gutter
    val topY = homeTopY(f, stripLines)
    val big = max(0f, (w / 2 - 3 * gap) / 3)
    val topH = 2 * big + gap / 2 + homeTitleRoom(f) + freed
    val rowsN = HOME_TOOL_ROWS
    val s = max(0f, min(big / 2, (topH - (rowsN - 1) * gap / 2) / rowsN))
    val slots = 2 * rowsN
    val shown = min(tools, slots)
    val more = if (tools > slots) tools - slots + 1 else 0
    val cols = (shown + rowsN - 1) / rowsN
    val right = if (shown > 0) w - cols * s - (cols - 1) * gap else w
    val inline = min(games.coerceAtLeast(0), HOME_INLINE_MAX)   // the first 2 inline, the rest row games
    val col = homeInlineColumn(inline, topH, w, right, shown > 0, gap)
    val top = homeTopTiles(gutter, topY, topH, col, shown, more, cols, right, s, gap)
    val labelH = homeLabelLineH(f)
    val pinW = (w - (HOME_LARGE_PIN_COLS - 1) * gap) / HOME_LARGE_PIN_COLS
    val pinH = pinW * 9 / 16
    val rowGames = games.coerceAtLeast(0) - inline
    // Each shelf up to 4 tiles ([homeShelfCap]) at the pins' size, 4 to a row: one row.
    val bands = homeBands(topY + topH, gutter, gap, labelH, rowGames, HOME_LARGE_PIN_COLS, pinH, shelves) { shelf ->
        HomeShelfShape(HOME_LARGE_PIN_COLS, pinW, pinH, min(homeShelfMax(shelf), homeShelfCap(false, HOME_LARGE_PIN_COLS)))
    }
    return HomeBoard(
        HomeToolMode.LARGE, false, stripLines, topY, topH, top, slots, more, gutter, gap, HOME_LARGE_PIN_COLS, pinW, pinH,
        bands.tilesY, rowGames, bands.pageH, s, homeStripTop(f, stripLines), bands.labels, bands.tiles, bands.sizes, inline, labelH, bands.cols,
    )
}

/** Row tile [k] (a pinned game, then the shelves' tiles in order), in the page. */
internal fun homeBoardTile(b: HomeBoard, k: Int): HomeRect =
    if (k < b.rowGames) homeBoardPin(b, k) else b.shelfTiles[k - b.rowGames]

/**
 * Row tile [k]'s rect for the scroll ([homeScrollTo]): a tile in a shelf's first row reaches up over the shelf's label
 * ([HomeBoard.labelH] + [HOME_LABEL_GAP]), so the label stays in view. The pins have no label.
 */
internal fun homeScrollRect(b: HomeBoard, k: Int): HomeRect {
    val r = homeBoardTile(b, k)
    if (b.labels.isEmpty() || k < b.rowGames) return r
    var at = b.rowGames
    var firstRow = false
    for ((i, n) in b.shelfSizes.withIndex()) {
        if (k < at + n) { firstRow = k - at < b.shelfCols.getOrElse(i) { HOME_SHELF_COLS }; break }
        at += n
    }
    if (!firstRow) return r
    val room = b.labelH + HOME_LABEL_GAP
    return HomeRect(r.x, r.y - room, r.w, r.h + room)
}

/**
 * The arrangement for a body [w] × [h] dp: [HomeArrangement.WIDE] when the menu area is at least [WIDE_RATIO] times as
 * wide as tall, else [HomeArrangement.SPLIT]. [menuRatio] is that area's own aspect (the app window in landscape,
 * [LocalWindowSize]); the bars above and below the body are not always 88 dp (the Flip's leave it 804 × ~261 of an
 * 804 × 330 area), so the body alone can misread it. Without one the area is taken as the body plus 88.
 */
internal fun homeArrangement(w: Float, h: Float, menuRatio: Float? = null): HomeArrangement {
    val ratio = menuRatio ?: if (h + HOME_BARS > 0f) w / (h + HOME_BARS) else 0f
    return if (ratio >= WIDE_RATIO) HomeArrangement.WIDE else HomeArrangement.SPLIT
}

/** Home's caption fade (Continue, a lit pinned game) covers the card's bottom 80%. */
internal const val HOME_CAPTION_SHARE = 0.8f
private const val HOME_CAPTION_EDGE = 0.92f   // its alpha at the bottom edge
private const val HOME_CAPTION_POWER = 3f     // eased: dark further up than the Grid's linear one

/** Home's caption fade's alpha at [t] (0 at the bottom edge, 1 at its top): [easedFadeAlpha], edge 0.92, power 3. */
internal fun homeCaptionAlpha(t: Float): Float = easedFadeAlpha(HOME_CAPTION_EDGE, t, HOME_CAPTION_POWER)

/**
 * Home's caption fade of [ground] over the element's bounds (size it to [HOME_CAPTION_SHARE] of the card): the Continue
 * card's and a lit pinned game's, one fade for both ([nxEasedFade]: edge 0.92, power 3, 20 stops).
 */
internal fun Modifier.homeCaptionFade(ground: Color): Modifier = nxEasedFade(ground, HOME_CAPTION_EDGE, power = HOME_CAPTION_POWER)

/** Continue's picture zoom: 1.15× over its Crop fill, so a game's black screen border falls outside the card. */
internal const val HOME_CONTINUE_ZOOM = 1.15f
/** The zoom's pivot, (0.5, 0.4) of the card: the same point the Crop frames the picture at (40% from the top). */
internal const val HOME_CONTINUE_PIVOT_X = 0.5f
internal const val HOME_CONTINUE_PIVOT_Y = 0.4f

/** The band of Continue's cropped picture (0 top, 1 bottom) still in the card after the zoom. */
internal fun homeContinueVisible(): ClosedFloatingPointRange<Float> =
    (HOME_CONTINUE_PIVOT_Y - HOME_CONTINUE_PIVOT_Y / HOME_CONTINUE_ZOOM)..(HOME_CONTINUE_PIVOT_Y + (1 - HOME_CONTINUE_PIVOT_Y) / HOME_CONTINUE_ZOOM)

/** A selected tile's ring room plus 5 dp: how far it stays from the body's top and bottom (the tab row, the hint bar). */
private const val HOME_SCROLL_CLEAR = NX_TILE_RING + 5f

/**
 * How far the page is scrolled up in a body [bodyH] tall with [selected] (the selected pin's tile; null: the top section,
 * which sits at the top): from [current] (the last target, or where a drag left it), as little as keeps the tile with
 * its ring room 5 dp clear of the body's top and bottom ([HOME_SCROLL_CLEAR]), within 0 and the page's end ([pageH]).
 */
internal fun homeScrollTo(current: Float, selected: HomeRect?, pageH: Float, bodyH: Float): Float {
    if (selected == null) return 0f
    val max = max(0f, pageH - bodyH)
    var y = current.coerceIn(0f, max)
    if (selected.y + selected.h + HOME_SCROLL_CLEAR - y > bodyH) y = selected.y + selected.h + HOME_SCROLL_CLEAR - bodyH
    if (selected.y - HOME_SCROLL_CLEAR < y) y = selected.y - HOME_SCROLL_CLEAR
    return y.coerceIn(0f, max)
}

/** How the scroll answers a recomposition ([homeScrollStep]): stay, ease to a new offset, or jump to it. */
enum class HomeScrollMove { KEEP, EASE, SNAP }

/** The scroll's move and the offset it goes to. */
data class HomeScrollStep(val move: HomeScrollMove, val to: Float)

/**
 * The scroll for Home drawn on board [b] with [selected] (the selected row game's tile, null: the top section), from
 * [current] (the offset now, a drag's included), after [prev] and [prevSelected]: a new board (the first layout, a
 * rotation, the pins changed) snaps to [homeScrollTo] from the top; a new selection eases there from [current];
 * anything else (the rows rebuilt with the same board: stats, recents) keeps [current], clamped into a page that got
 * shorter ([pageH] in a body [bodyH] tall).
 */
internal fun homeScrollStep(
    current: Float, prev: HomeBoard?, b: HomeBoard, prevSelected: HomeRect?, selected: HomeRect?, pageH: Float, bodyH: Float,
): HomeScrollStep = when {
    prev != b -> HomeScrollStep(HomeScrollMove.SNAP, homeScrollTo(0f, selected, pageH, bodyH))
    prevSelected != selected -> HomeScrollStep(HomeScrollMove.EASE, homeScrollTo(current, selected, pageH, bodyH))
    else -> HomeScrollStep(HomeScrollMove.KEEP, current.coerceIn(0f, max(0f, pageH - bodyH)))
}

/**
 * Pick a game's NX mark box: the square the 108-unit mark is drawn in (contain), so the drawn mark (44.13 units with
 * its strokes) is 60% of the card's [cardH].
 */
internal fun homeMarkBox(cardH: Float): Float = 0.6f * cardH * 108f / 44.13f

/** The mark's alpha: white at 10%, black at 8% on the selected (white-filled) card. */
internal fun homeMarkAlpha(lit: Boolean): Float = if (lit) 0.08f else 0.10f

/** A Home title tile's name: its line height, times its size (the tiles' text line height). */
private const val HOME_TITLE_LINE = 1.15f

/**
 * The room a lit Home title tile keeps free at its bottom for its caption: the info line's 14 dp line (times [scale],
 * the text scale) and 8 more, so the centred name never runs into it.
 */
internal fun homeTitleCaptionRoom(scale: Float): Float = 14f * scale + 8f

/**
 * How many lines a Home title tile's name at [titleSp] (times [scale]) takes in a tile [tileH] tall, less [captionRoom]
 * at the bottom ([homeTitleCaptionRoom] when lit with its info line): as many as fit, at most [maxLines], at least one.
 */
internal fun homeTitleLines(tileH: Float, titleSp: Float, captionRoom: Float, maxLines: Int, scale: Float = 1f): Int =
    min(maxLines, floor((tileH - captionRoom) / (titleSp * HOME_TITLE_LINE * scale) + 1e-4f).toInt()).coerceAtLeast(1)

/**
 * How far up a Home caption (Continue, a pinned game) may reach, of the card's height: its top at 52% keeps the
 * text inside the [homeCaptionFade] (the bottom 80%, so 65% of the way up it) where it is still at least 0.35 dark at
 * the very top of a wrapped name, darker down its lines; the wrapped name also carries [titleShadow]. The Fold cover's
 * 114 dp pins just hold 2 lines over the 13 sp info line (58 of 59.3 dp).
 */
internal const val HOME_CAPTION_WRAP_SHARE = 0.52f

/**
 * How many lines a Home caption's title takes in a card [cardH] dp tall: the title's line is [titleLine] dp and
 * everything under it (the gaps, the info and subtitle lines, the bottom padding) [below] dp, all at the drawn text
 * scale. As many as keep the bottom-anchored block within [HOME_CAPTION_WRAP_SHARE] of the card, at most [maxLines] (2),
 * at least one: a short card keeps the one ellipsized line.
 */
internal fun homeCaptionTitleLines(cardH: Float, titleLine: Float, below: Float, maxLines: Int = 2): Int {
    if (titleLine <= 0f) return 1
    return min(maxLines, floor((HOME_CAPTION_WRAP_SHARE * cardH - below) / titleLine + 1e-4f).toInt()).coerceAtLeast(1)
}

/** A game's info on Home: signed out of RetroAchievements, no achievement data (the count, the next goal) anywhere. */
internal fun homeInfo(lines: List<InfoLine>, signedIn: Boolean): List<InfoLine> =
    if (signedIn) lines else lines.filter { it.kind != InfoKind.ACHIEVEMENTS && it.kind != InfoKind.NEXT }

/** Only the TIME segment ("Yesterday - 1h 12m"): Home's captions (Continue, the pinned games) carry no achievements or next goal. */
internal fun timeOnly(info: List<InfoLine>): List<InfoLine> = info.filter { it.kind == InfoKind.TIME }
