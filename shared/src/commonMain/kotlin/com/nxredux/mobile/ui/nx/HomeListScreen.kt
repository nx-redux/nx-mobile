package com.nxredux.mobile.ui.nx

import com.nxredux.mobile.ui.mainMenuListEdges
import androidx.compose.foundation.gestures.detectTapGestures
import kotlin.math.max
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nxredux.mobile.ui.HomeStrip
import com.nxredux.mobile.ui.InfoLine
import com.nxredux.mobile.ui.gameListInfoInBarRow
import com.nxredux.mobile.ui.MainRow
import com.nxredux.mobile.ui.mainMenuListTopEdge

/** List Home's Continue row tag, drawn before the game's name (the handheld's `home_list_layout.c`). */
private const val CONTINUE_TAG = "Continue"

/**
 * List Home's rows for Home's [rows] (built in the List order: Continue or Pick a game, the pinned games, the shelves'
 * games, the pinned tools): Continue wears its [NxRow.tag] before the game's name, a shelf game wears its shelf's name as
 * its tag, a pinned game or tool its name alone. List Home has no "+N".
 */
internal fun homeListRows(rows: List<MainRow>): List<NxRow> = rows.mapNotNull { r ->
    when (r) {
        is MainRow.Continue -> NxRow(r.entry.game.shownName, tag = CONTINUE_TAG)
        MainRow.PickAGame -> NxRow(PICK_A_GAME)
        is MainRow.Pin -> NxRow(r.entry.game.shownName)
        is MainRow.Shelf -> NxRow(r.entry.game.shownName, tag = r.shelf.label)
        is MainRow.PinnedTool -> NxRow(r.row.label)
        is MainRow.MoreTools, is MainRow.System, is MainRow.Collection, is MainRow.Tool -> null
    }
}

/** List Home's band with the hints shown: no info ([HomeListScreen]). */
private val NoInfo: () -> List<InfoLine> = { emptyList() }

/**
 * Home in the List style (Layouts → Home layout: List), as the handheld's List Home: the stats strip ([HomeStripLines]
 * at the List gutter, while Home stats shows it, its text centred between the tab underline and the first row's pill,
 * [homeListStripInkTop]) over a main menu List of Home's [rows] ([homeListRows]). With the tab row hidden the strip
 * moves into a header in its place ([homeStatsHeader]). The rows fade where more scrolls in and run down to the hint
 * bar's row (no band under them); the selected game's [info] shows only in that row, with Button hints hidden or touch
 * only ([gameListInfoInBarRow]). App draws the selected game's picture behind ([com.nxredux.mobile.ui.menuBackground]). A
 * fresh install's only row, Pick a game, is its card filling the body instead.
 * Navigation lives in AppState: this only draws [selection], and taps go to [onSelect] / [onOpen] as a main menu List's
 * do. A long press on any row calls [onLongPress], on the Pick a game card only when [tabMenu] (a hidden tab to offer
 * from MENU). [bodyAlpha]: the tab focus dim of everything but the hint bar.
 */
@Composable
internal fun HomeListScreen(
    rows: List<MainRow>, selection: () -> Int, strip: HomeStrip?, info: () -> List<InfoLine>, hints: List<Hint>,
    onSelect: (Int) -> Unit, onOpen: (Int) -> Unit, onLongPress: ((Int) -> Unit)?, onHint: ((NxButton) -> Unit)?,
    bodyAlpha: () -> Float, modifier: Modifier = Modifier, tabMenu: Boolean = false,
) {
    val chrome = LocalLauncherChrome.current
    val portrait = LocalPortrait.current
    // The rows (and the band's arrows, the strip) start on the tab row's gutter, as a main menu List's.
    val gutter = if (portrait) 16.dp else 24.dp
    val shownStrip = strip?.takeIf { chrome.homeStats }   // Layouts → Home stats hides it
    val fontScale = LocalDensity.current.fontScale
    // Only the text scale and the orientation (the underline) shape the strip; the frame's size is not read.
    val frame = HomeFrame(0f, 0f, portrait = portrait, textScale = fontScale, titleHidden = false, stripScale = nxTextScale())
    val stripLines = if (shownStrip?.most != null) 2 else 1
    // With the tab row hidden the strip moves into its room as a header ([homeStatsHeader]), as on Grid and Carousel
    // Home; with no strip the rows run up beside the Recent and Search icons ([homeListTopRoom]).
    val header = if (!chrome.tabRow && shownStrip != null) homeStatsHeader(frame.copy(titleHidden = true), stripLines) else null
    val topRoom = homeListTopRoom(chrome.tabRow, header?.height?.dp, pickAGame = rows == listOf(MainRow.PickAGame))
    val pageStrip = shownStrip?.takeIf { header == null }   // the strip in the page, under the tab row
    val (capTop, inkH) = homeStripInk(frame, stripLines)
    val above = homeUnderlineAbove(frame)
    // The strip's text centred between the tab underline and the first row's pill ([homeListStripInkTop]); until the
    // list reports where that pill lands, the least gap under the underline.
    var firstPill by remember { mutableStateOf<Float?>(null) }
    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            if (topRoom > 0.dp) Spacer(Modifier.height(topRoom))
            // The Pick a game card (its own 12 dp padding above it) the least gap under the strip's text.
            if (pageStrip != null && rows == listOf(MainRow.PickAGame)) Spacer(Modifier.height((2 * HOME_LIST_STRIP_MIN_GAP + inkH - above - 12f).coerceAtLeast(0f).dp))
            if (rows == listOf(MainRow.PickAGame)) {
                // Nothing played yet: the Pick a game card fills the body (the selection is always on it).
                NxScreenScaffold(
                    rows = emptyList(), selectedIndex = 0, hints = hints, onSelect = onSelect, onOpen = onOpen,
                    modifier = Modifier.weight(1f), onHint = onHint, bodyAlpha = bodyAlpha, hideHints = chrome.emptyBarRoom || chrome.noBar,
                    grid = { m -> HomePickCard(selection, onSelect, onOpen, onLongPress?.takeIf { tabMenu }, portrait, m.padding(horizontal = gutter, vertical = 12.dp).fillMaxSize()) },
                )
                return@Column
            }
            val listRows = remember(rows) { homeListRows(rows) }
            // No band: the rows fade where more scrolls in, down to the bar. The selected game's info shows only in the bar's row, shaded
            // under it (as a List game list's, [BandGround.SHADE_WITH_INFO]): with the
            // hints hidden, or touch only, where the row is kept for it ([gameListInfoInBarRow]).
            // Extra info off leaves nothing for that row, so its room goes too.
            val barHidden = gameListInfoInBarRow(chrome.emptyBarRoom, touchOnly())
            val inRow = barHidden && chrome.extraInfo
            GameInfoPlacement(
                if (inRow) info else NoInfo, Modifier.weight(1f), textStart = gutter,
                ground = if (inRow) BandGround.SHADE_WITH_INFO else BandGround.BAR, shade = false,
            ) { scaffoldModifier, infoBand ->
                // The band's room goes to the rows; only its line in the bar's row ([NxHintsBand.barInfo]) stays.
                val band = NxHintsBand(reserve = 0.dp, overlap = 0.dp, barInfo = infoBand.barInfo) {}
                NxScreenScaffold(
                    rows = listRows,
                    selectedIndex = 0, selection = selection, showArt = false,   // App draws the game's picture behind; the rows read the selection
                    hints = hints, onSelect = onSelect, onOpen = onOpen,
                    modifier = scaffoldModifier, onHint = onHint, onLongPress = onLongPress, band = band,
                    labelStart = gutter,
                    // The edge the rows are spaced from ([nxListFitUnder]): with the strip in the page, its text's bottom at
                    // its least gap under the underline (the strip then centres, [homeListStripInkTop]); under the stats
                    // header, its bottom; under the tab row, its underline ([mainMenuListTopEdge]). With neither, none: the
                    // first row a fixed gap under the page's top, as a main menu List's.
                    listTopEdge = if (pageStrip != null) (above - HOME_LIST_STRIP_MIN_GAP - inkH).dp
                        else if (header != null) 0.dp
                        else mainMenuListTopEdge(chrome.tabRow),
                    listTopGap = if (portrait) 10.dp else 12.dp,
                    bodyAlpha = bodyAlpha, hideHints = barHidden, hintShade = inRow, keepBarRoom = inRow || !barHidden,
                    // The main menu Lists' row height and bottom gap (their edges here; the info row counts as a bar), so
                    // Home's text and its gap over the bottom match theirs.
                    scrollFade = true, rowsLike = mainMenuListEdges(chrome.tabRow, barShown = !barHidden || inRow),
                    onFirstPill = if (pageStrip != null) { y -> firstPill = y } else null,
                )
            }
        }
        val inkTop = if (pageStrip != null) homeListStripInkTop(above, inkH, firstPill) else 0f
        HomeStripOverlay(shownStrip, pageStrip, header, stripLines, inkTop, capTop, frame, gutter, bodyAlpha)
    }
}

/**
 * List and Minimal Home's strip over their body (faded with [bodyAlpha]), at the [gutter]: [pageStrip] in the page under
 * the tab row, its text's top at [inkTop] ([capTop] under its lines' top), or [strip] in the stats [header] (the tab row
 * hidden), short of the Recent and Search icons MainMenuScreen draws at its right.
 */
@Composable
internal fun HomeStripOverlay(
    strip: HomeStrip?, pageStrip: HomeStrip?, header: HomeStatsHeader?, stripLines: Int, inkTop: Float, capTop: Float,
    frame: HomeFrame, gutter: Dp, bodyAlpha: () -> Float,
) {
    if (pageStrip != null) {
        BoxWithConstraints(Modifier.fillMaxWidth().graphicsLayer { alpha = bodyAlpha() }) {
            HomeStripLines(
                pageStrip, stripLines, gutter.value, inkTop - capTop,
                width = maxWidth.value - 2 * gutter.value, lh = homeStripLineH(frame), pitch = homeStripPitch(frame),
            )
        }
    }
    if (header != null && strip != null) {
        BoxWithConstraints(Modifier.fillMaxWidth().graphicsLayer { alpha = bodyAlpha() }) {
            HomeStripLines(
                strip, header.lines, gutter.value, header.stripTop,
                width = maxWidth.value - 2 * gutter.value - HOME_HEADER_ICONS_ROOM, lh = homeStripLineH(frame), pitch = homeStripPitch(frame),
            )
        }
    }
}

/** List Home's least gap from the tab underline to the strip's text and from the text to the first row's pill. */
private const val HOME_LIST_STRIP_MIN_GAP = 12f

/**
 * List Home's strip text's top, dp from the body's top: centred between the tab underline [above] dp over the
 * body's top and the first row's pill top [firstPill] (as the list reports it; null before, the least gap
 * [HOME_LIST_STRIP_MIN_GAP] then), the text [inkH] tall, so the room above it equals the room below.
 */
internal fun homeListStripInkTop(above: Float, inkH: Float, firstPill: Float?): Float =
    if (firstPill == null) HOME_LIST_STRIP_MIN_GAP - above
    else -above + max(HOME_LIST_STRIP_MIN_GAP, (firstPill + above - inkH) / 2)

/**
 * List and Carousel Home's Pick a game card, filling [modifier]'s box; a tap opens it (Consoles), as Home's. [onLongPress]
 * (passed only while a hidden tab can be offered; null otherwise, so a held press still taps on release) on a long press.
 */
@Composable
internal fun HomePickCard(
    selection: () -> Int, onSelect: (Int) -> Unit, onOpen: (Int) -> Unit, onLongPress: ((Int) -> Unit)?, portrait: Boolean, modifier: Modifier,
) {
    val longPress by rememberUpdatedState(onLongPress)
    val select by rememberUpdatedState(onSelect)
    val open by rememberUpdatedState(onOpen)
    val tapOpens by rememberUpdatedState(LocalNxTapOpens.current)
    val sel by rememberUpdatedState(selection)
    BoxWithConstraints(modifier) {
        PickCard(
            lit = selection() == 0, portrait = portrait, cardH = maxHeight.value,
            modifier = Modifier.fillMaxSize().pointerInput(onLongPress != null) {
                detectTapGestures(
                    onTap = { nxTap(0, sel() == 0, tapOpens(0), select, open) },
                    onLongPress = if (onLongPress != null) { _ -> longPress?.invoke(0) } else null,
                )
            },
        )
    }
}

/**
 * The room List Home keeps above its body: the stats header's ([header], the tab row hidden), none under the tab row, and
 * with the tab row hidden and no header, the tab row's room only for the Pick a game card (it spans the width, under
 * Recent and Search); a list runs up beside them, its rows level with them stopping short ([LocalNxListTopCut]), as a
 * main menu List's.
 */
internal fun homeListTopRoom(tabRow: Boolean, header: Dp?, pickAGame: Boolean): Dp = when {
    header != null -> header
    tabRow -> 0.dp
    pickAGame -> MENU_TAB_ROW_HEIGHT
    else -> 0.dp
}
