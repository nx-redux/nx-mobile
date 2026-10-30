package com.nxredux.mobile.ui.nx

import androidx.compose.foundation.gestures.detectTapGestures
import kotlin.math.max
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nxredux.mobile.settings.MenuStyle
import com.nxredux.mobile.ui.HomeStrip
import com.nxredux.mobile.ui.InfoLine
import com.nxredux.mobile.ui.MainRow

/** List Home's Continue row tag, drawn after the game's name ([MainListBody]), as a shelf game's New or Not started. */
private const val CONTINUE_TAG = "Continue"

/**
 * List Home's rows for Home's [rows] (built in the List order: Continue or Pick a game, the pinned games, the shelves'
 * games, the pinned tools): a game wears why it is on Home as its [NxRow.tag], Continue, New or Not started (a game on
 * Pick up again, a pinned game or a tool, its name alone). List Home has no "+N".
 */
internal fun homeListRows(rows: List<MainRow>): List<NxRow> = rows.mapNotNull { r ->
    when (r) {
        is MainRow.Continue -> NxRow(r.entry.game.shownName, tag = CONTINUE_TAG)
        MainRow.PickAGame -> NxRow(PICK_A_GAME)
        is MainRow.Pin -> NxRow(r.entry.game.shownName)
        is MainRow.Shelf -> NxRow(r.entry.game.shownName, tag = r.tag ?: r.shelf.label.takeIf { r.shelf != HomeShelf.PICK_UP })
        is MainRow.PinnedTool -> NxRow(r.row.label)
        is MainRow.MoreTools, is MainRow.System, is MainRow.Collection, is MainRow.Tool -> null
    }
}

/**
 * The room between List Home's header and its first game, and (the tab row hidden) under its last, in row pitches; a
 * List game list keeps it under its stats line too.
 */
internal const val HOME_LIST_GAP_ROWS = 0.5f

/**
 * Home in the List style (Layouts → Home layout: List): the stats strip ([HomeStripLines] at the gutter, while Home
 * stats shows it; with the tab row hidden, in a header in its place, [homeStatsHeader]), the selected game's [info]
 * on a line under it ([GameFactsLines], Extra info; in the stats' place with them off; two lines upright), over Home's [rows]
 * ([MainListBody], right under the header, or under the icon row ([iconsRoom]) with neither). App draws the selected game's picture behind ([com.nxredux.mobile.ui.menuBackground]).
 * A fresh install's only row, Pick a game, is its card filling the body instead.
 * Navigation lives in AppState: this only draws [selection], and taps go to [onSelect] / [onOpen] as a main menu List's
 * do. A long press on any row calls [onLongPress], on the Pick a game card only when [tabMenu] (a hidden tab to offer
 * from MENU).
 */
@Composable
internal fun HomeListScreen(
    rows: List<MainRow>, selection: () -> Int, strip: HomeStrip?, info: () -> List<InfoLine>,
    onSelect: (Int) -> Unit, onOpen: (Int) -> Unit, onLongPress: ((Int) -> Unit)?, onHint: ((NxButton) -> Unit)?,
    modifier: Modifier = Modifier, tabMenu: Boolean = false, iconsRoom: Dp = 0.dp,
) {
    val chrome = LocalLauncherChrome.current
    val portrait = LocalPortrait.current
    // The names, the strip and the facts on the page inset, as the tab words.
    val inset = nxPageInset()
    val shownStrip = strip?.takeIf { chrome.homeStats }   // Layouts → Home stats hides it
    val fontScale = LocalDensity.current.fontScale
    // Only the text scale and the orientation (the underline) shape the strip; the frame's size is not read.
    val frame = HomeFrame(0f, 0f, portrait = portrait, textScale = fontScale, titleHidden = false, stripScale = nxTextScale())
    val stripLines = if (shownStrip?.most != null) 2 else 1
    // With the tab row hidden the strip moves into its room as a header ([homeStatsHeader]), as on Grid and Carousel
    // Home; with no strip the rows run up beside the Recent and Search icons ([homeListTopRoom]).
    // Extra info: the selected game's facts on a line of their own under the stats, in the header with them (in the first
    // line's place with Home stats off); upright with Home stats off, on two lines, the next goal on the second
    // ([GameFactsLines], [homeFactLines]).
    val facts = chrome.extraInfo && rows != listOf(MainRow.PickAGame)
    val statLines = if (shownStrip != null) stripLines else 0
    val factLines = homeFactLines(facts, portrait, statLines)
    val headerLines = statLines + factLines
    val header = if (!chrome.tabRow && headerLines > 0) homeStatsHeader(frame.copy(titleHidden = true), headerLines) else null
    // The facts line a little apart from the stats above it ([HOME_FACTS_GAP]); the header grows by as much.
    val factsGap = if (facts && statLines > 0) HOME_FACTS_GAP else 0f
    val topRoom = homeListTopRoom(chrome.tabRow, header?.height?.plus(factsGap)?.dp, pickAGame = rows == listOf(MainRow.PickAGame))
    val pageStrip = shownStrip?.takeIf { header == null }   // the strip in the page, under the tab row
    val (capTop, inkH) = homeStripInk(frame, stripLines)
    val above = homeUnderlineAbove(frame)
    // The strip's text the least gap under the tab underline ([homeListStripInkTop]).
    val inkTop = if (pageStrip != null) homeListStripInkTop(above, inkH, null) else 0f
    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            if (topRoom > 0.dp) Spacer(Modifier.height(topRoom))
            // The Pick a game card (its own 12 dp padding above it) the least gap under the strip's text.
            if (pageStrip != null && rows == listOf(MainRow.PickAGame)) Spacer(Modifier.height((2 * HOME_LIST_STRIP_MIN_GAP + inkH - above - 12f).coerceAtLeast(0f).dp))
            if (rows == listOf(MainRow.PickAGame)) {
                // Nothing played yet: the Pick a game card fills the body (the selection is always on it).
                NxScreenScaffold(
                    rows = emptyList(), selectedIndex = 0, hints = emptyList(), onSelect = onSelect, onOpen = onOpen,
                    modifier = Modifier.weight(1f), onHint = onHint, hideHints = chrome.emptyBarRoom || chrome.noBar,
                    grid = { m -> HomePickCard(selection, onSelect, onOpen, onLongPress?.takeIf { tabMenu }, portrait, m.padding(start = inset.start, end = inset.end, top = 12.dp, bottom = 12.dp).fillMaxSize()) },
                )
                return@Column
            }
            val names = remember(rows) { homeListRows(rows) }
            // The rows right under the header (the stats and the facts), else under the icon row; the fade parts them.
            val listTop = when {
                header != null -> 0.dp
                pageStrip != null -> (inkTop + inkH).coerceAtLeast(0f).dp
                else -> iconsRoom
            }
            // Half a row's room between the stats (or facts) and the first game, so Home's top does not crowd its list.
            MainListBody(names, selection, listTop, onSelect, onOpen, onLongPress, Modifier.weight(1f), gapRows = if (header != null) HOME_LIST_GAP_ROWS else 0f,
                bottomRows = if (header != null) HOME_LIST_GAP_ROWS else 0f,   // the tab row hidden: the other half off the bottom edge
            )
        }
        HomeStripOverlay(shownStrip, pageStrip, header?.takeIf { shownStrip != null }, statLines, inkTop, capTop, frame, inset)
        if (facts && header != null) {
            // The next line of the header's rhythm under the stats, short of the icons while level with them.
            val y = header.stripTop + statLines * homeStripPitch(frame) + factsGap
            val iconsRoom = if (y < MENU_TAB_ROW_HEIGHT.value) HOME_HEADER_ICONS_ROOM.dp else 0.dp
            GameFactsLines(
                info(), (HOME_STRIP_TYPE.sp * nxTextScale()).sp, homeStripPitch(frame).dp, rows = factLines,
                Modifier.pageFixed().fillMaxWidth().padding(top = y.dp, start = inset.start, end = inset.end + iconsRoom),
            )
        }
    }
}

/**
 * List and Minimal Home's strip over their body, on the page [inset]: [pageStrip] in the page under
 * the tab row, its text's top at [inkTop] ([capTop] under its lines' top), or [strip] in the stats [header] (the tab row
 * hidden), short of the Recent and Search icons MainMenuScreen draws at its right.
 */
@Composable
internal fun HomeStripOverlay(
    strip: HomeStrip?, pageStrip: HomeStrip?, header: HomeStatsHeader?, stripLines: Int, inkTop: Float, capTop: Float,
    frame: HomeFrame, inset: PageInset,
) {
    // Still while the page slides between tabs, as the tab row and the icons are ([pageFixed]).
    if (pageStrip != null) {
        BoxWithConstraints(Modifier.fillMaxWidth().pageFixed()) {
            HomeStripLines(
                pageStrip, stripLines, inset.start.value, inkTop - capTop,
                width = (maxWidth - inset.start - inset.end).value, lh = homeStripLineH(frame), pitch = homeStripPitch(frame),
            )
        }
    }
    if (header != null && strip != null) {
        BoxWithConstraints(Modifier.fillMaxWidth().pageFixed()) {
            HomeStripLines(
                strip, stripLines, inset.start.value, header.stripTop,
                width = (maxWidth - inset.start - inset.end).value - HOME_HEADER_ICONS_ROOM, lh = homeStripLineH(frame), pitch = homeStripPitch(frame),
            )
        }
    }
}

/** List Home's gap, dp, between the stats' last line and the facts line under it. */
private const val HOME_FACTS_GAP = 6f

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
 * List Home's lines for the selected game's facts ([GameFactsLines]): none without [facts]; one under the stats
 * ([statLines] above them), so the header stays short; with no stats, upright (the Recent and Search icons beside the
 * line leave it little room), two, the next goal on the second; sideways one.
 */
internal fun homeFactLines(facts: Boolean, portrait: Boolean, statLines: Int): Int = when {
    !facts -> 0
    portrait && statLines == 0 -> 2
    else -> 1
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
