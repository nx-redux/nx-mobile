package com.nxredux.mobile.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.padding
import com.nxredux.mobile.ui.nx.TOP_ICON_INSET
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.structuralEqualityPolicy
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nxredux.mobile.settings.MenuCategory
import com.nxredux.mobile.settings.MenuOrientation
import com.nxredux.mobile.settings.MenuStyle
import com.nxredux.mobile.ui.nx.BandGround
import com.nxredux.mobile.ui.nx.GameInfoPlacement
import com.nxredux.mobile.ui.nx.LauncherTopIcons
import com.nxredux.mobile.ui.nx.NxTopIcons
import com.nxredux.mobile.ui.nx.LocalNxListTopCut
import com.nxredux.mobile.ui.nx.nxListTopCut
import com.nxredux.mobile.ui.nx.rememberNxListTopCut
import com.nxredux.mobile.ui.nx.launcherTopActions
import com.nxredux.mobile.ui.nx.LocalLauncherChrome
import com.nxredux.mobile.ui.nx.HomeFrame
import com.nxredux.mobile.ui.nx.HomeCarouselScreen
import com.nxredux.mobile.ui.nx.HomeListScreen
import com.nxredux.mobile.ui.nx.HomeScreen
import com.nxredux.mobile.ui.nx.HomeMinimalScreen
import com.nxredux.mobile.ui.nx.MinimalOpenOverlay
import com.nxredux.mobile.ui.nx.MinimalScreen
import com.nxredux.mobile.ui.nx.MinimalSelected
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.zIndex
import com.nxredux.mobile.ui.nx.Hint
import com.nxredux.mobile.ui.nx.LocalNxColors
import com.nxredux.mobile.ui.nx.LocalNxTapOpens
import com.nxredux.mobile.ui.nx.LocalPortrait
import com.nxredux.mobile.ui.nx.MENU_TAB_ROW_HEIGHT
import com.nxredux.mobile.ui.nx.MenuTabRow
import com.nxredux.mobile.ui.nx.MenuTitleRow
import com.nxredux.mobile.ui.nx.NX_TOP_BAND_REACH
import com.nxredux.mobile.ui.nx.NxBackdrop
import com.nxredux.mobile.ui.nx.NxBackdropStack
import com.nxredux.mobile.ui.nx.NxButton
import com.nxredux.mobile.ui.nx.NxRow
import com.nxredux.mobile.ui.nx.NX_HINT_BAR_ROW_HEIGHT
import com.nxredux.mobile.ui.nx.NxHintsBand
import com.nxredux.mobile.ui.nx.NxScreenScaffold
import com.nxredux.mobile.ui.nx.NxTileGrid
import com.nxredux.mobile.ui.nx.PrefetchPads
import com.nxredux.mobile.ui.nx.consolePad
import com.nxredux.mobile.ui.nx.nxTopBandFade
import com.nxredux.mobile.ui.nx.menuTabUnderlineBottom

/**
 * The main menu: the tab row and the current tab in its [style]. The selection-dependent inputs ([selectedIndex],
 * [info], [canResume], [tileInfo]) are lambdas read only where they show (the row or list, the caption, the hints, the
 * info band, the tab row's over-art look), so a selection change recomposes those and not the whole screen.
 */
@Composable
fun MainMenuScreen(
    screen: Screen.Systems, selectedIndex: () -> Int,
    onSelect: (Int) -> Unit, onOpen: (Int) -> Unit, onTab: (Int) -> Unit, modifier: Modifier = Modifier,
    onSwipeSelect: ((Int) -> Unit)? = null,         // Minimal's swipe release: moves the highlight only, never the tap's open (default: onSelect)
    onHint: ((NxButton) -> Unit)? = null, onLongPress: ((Int) -> Unit)? = null,
    info: () -> List<InfoLine> = { emptyList() },   // the selected row's info (a game's gameInfoLines, a console's or collection's game count): List's band
    rowInfo: (Int) -> List<InfoLine> = { emptyList() },   // Carousel Home: any row's full info by index (its caption stays on the last row item while the dock has focus)
    canResume: () -> Boolean = { false },           // the highlighted Continue or pinned game has a save (A Resume)
    canNetplay: () -> Boolean = { false },          // the highlighted Continue, pinned or shelf game has Netplay here (Y)
    style: MenuStyle = MenuStyle.LIST,    // the tab's style
    orientation: MenuOrientation = MenuOrientation.HORIZONTAL,   // the tab's orientation (Carousel only: Vertical is the stack)
    onGridLayout: (Int, Int) -> Unit = { _, _ -> },   // a Grid reports its column and row counts
    tileInfo: (Int) -> List<InfoLine> = { emptyList() },   // Grid and Home pins: a row's game's count-only info, in its tile (read lazily per tile)
    layoutKey: Any? = null,               // the tab laid out (its category): a Grid re-reports its columns, a Carousel row snaps, for each
    onHomeLayout: (HomeFrame) -> Unit = {},   // Home reports its body's frame here (its size, portrait type, menu area ratio)
    homeView: HomeView = HomeView(null, 1, 0, 0),   // Home: the strip, the top section's size, the pinned counts
    homeRowLast: () -> Int = { 0 },       // Carousel Home: AppState's row item last on (shown while the dock has the focus)
    continueInfo: () -> List<InfoLine> = { emptyList() },   // Home: Continue's game info (full)
    raSignedIn: Boolean = false,          // Home: false drops achievement data from Continue and the pinned games
    controllerArt: Boolean = false,       // a List or Carousel Consoles tab: Layouts → Consoles background is Controller
    tabFocus: Boolean = false,            // the tab row has the d-pad: its tab word is lit, the whole content below dims
    bandShade: Boolean = true,            // List: the info band's shade ([mainMenuListBand]: none on Consoles, Collections and Tools)
    bandInfo: Boolean = true,             // List: the band shows the row's info ([mainMenuListBand]: none on Consoles, Collections and Tools)
    canSearch: Boolean = false,           // the library has games: START Search in the hints
    onTabMenu: (() -> Unit)? = null,      // a long press on the tab row or the single-tab title: the hidden-tab menu
    tabMenu: Boolean = false,             // a hidden tab can be opened from MENU: every row's hints offer Options, every Home row takes a long press
    minimalOpening: MinimalOpening? = null,   // Minimal: this tab's selected row being opened: the others fade, a launch's band grows over the screen ([MinimalOpenOverlay])
) {
    val rows = screen.rows
    val home = screen.category == MenuCategory.HOME   // the Home tab: [HomeListScreen], [HomeScreen] (Grid), [HomeCarouselScreen] or [HomeMinimalScreen] by its own style
    val shared = remember(rows) { rows.mapNotNull { (it as? MainRow.System)?.system }.groupingBy { it.displayName }.eachCount().filterValues { it > 1 }.keys }   // rows merge by name (GBA and MGBA are one row): a tag badge only if two rows still share one
    val index = rememberUpdatedState(selectedIndex)
    val selection = remember { { index.value() } }   // the row reads it where it shows (its slide, its items), not here
    val resume = rememberUpdatedState(canResume)
    val netplay = rememberUpdatedState(canNetplay)
    val selectedRow = remember(rows) { derivedStateOf(structuralEqualityPolicy()) { rows.getOrNull(index.value()) } }
    // Equal hints keep one list, so the hint bar recomposes only when they change (A Resume or A Play). The tab row
    // focused: SELECT Recent and A Open. Recent, Search and Netplay leave the bar while their Layouts rows hide them.
    // Recent and Search then become icons ([LauncherChrome.topIcons]), as on a touch-only screen or with Button hints
    // hidden: at the tab row's right, or at the top right corner while the tab row is hidden.
    val shown = LocalLauncherChrome.current
    val hints = remember(rows, tabFocus, canSearch, screen.pushed, tabMenu, shown) {
        derivedStateOf(structuralEqualityPolicy()) {
            mainMenuHints(
                selectedRow.value, resume.value(), tabFocus, netplay.value() && shown.netplayHint, canSearch && shown.searchInBar,
                pushed = screen.pushed, tabMenu = tabMenu, recent = shown.recentInBar,
            )
        }
    }
    // Layouts → Page title and Button hints, in effect ([LocalLauncherChrome]). With the tab row hidden, a List and Home
    // take its room (the List more rows, Home its strip and top section); a Grid or Carousel takes it into its
    // box, its items sized as with the tab row and centred in the taller box. With Button hints hidden, the hint bar's
    // room is kept empty on every tab, Home too (the scaffold's hideHints: no hints, no taps).
    val chrome = LocalLauncherChrome.current
    // Consoles with their controllers (a List or Carousel by Layouts → Consoles background): every pad
    // decoded ahead, off the main thread, nearest the selection first, so stepping through the consoles never decodes one inside a frame.
    if (!home && style != MenuStyle.GRID && style != MenuStyle.MINIMAL && controllerArt) {
        val pads = remember(rows) { rows.map { r -> (r as? MainRow.System)?.systems?.firstNotNullOfOrNull { consolePad(it.tag) }?.res } }
        PrefetchPads(pads, selection)
    }
    // A Minimal tab's selected name as last laid out, and this screen's box: where the open band starts.
    val minimalSelected = remember { MinimalSelected() }
    val screenBox = remember { arrayOfNulls<LayoutCoordinates>(1) }
    Box(modifier.fillMaxSize().onGloballyPositioned { screenBox[0] = it }) {
        // Behind the tab row, over the backdrop App draws: the switcher title's eased top band, so the dim labels read over
        // bright art. It reaches [NX_TOP_BAND_REACH] past the row, over the list's top (the rows draw on top of it).
        // List Home draws the selected game's picture behind the page (App's MenuBackground.Picture), as a game list does,
        // so it has the band too ([mainMenuTopBand]). Grid Home continues it over its own page, and only while that page
        // is scrolled ([HomeScreen]); Carousel Home has no picture. With the tab row hidden, the Recent and Search icons
        // still get the band: here, behind a List's rows as the tab row's is, so its reach never dims the first row; over
        // the other styles' pages (below).
        val topIcons = if (chrome.tabRow) emptyList() else launcherTopActions(chrome, onHint, canSearch)
        val topCut = rememberNxListTopCut()
        val iconBand = topIcons.isNotEmpty() && !(home && style != MenuStyle.LIST)
        if (mainMenuTopBand(home, style, chrome.tabRow) || iconBand && style == MenuStyle.LIST) {
            Box(Modifier.fillMaxWidth().height(MENU_TAB_ROW_HEIGHT + NX_TOP_BAND_REACH).nxTopBandFade(LocalNxColors.current.background))
        }
        Column(Modifier.fillMaxSize()) {
            val labels = remember(screen.tabs) { screen.tabs.map { it.category.label } }
            // Hidden: L1 / R1 still switch tabs, and the page takes the row's room (a Grid or Carousel through
            // [titleRoom]).
            // One tab: no tab to switch to, so NX Redux's title stands in the row's place (same height: the page below keeps its place).
            val title = mainMenuTitle(screen)
            if (chrome.tabRow) {
                val longPress = onTabMenu?.let { f -> Modifier.pointerInput(f) { detectTapGestures(onLongPress = { f() }) } } ?: Modifier
                val icons = @Composable { LauncherTopIcons(chrome, onHint, canSearch) }
                if (title == null) MenuTabRow(labels, screen.tab, onTab, focused = tabFocus, onLongPress = onTabMenu, trailing = icons)
                else MenuTitleRow(title, modifier = longPress, textStart = if (LocalPortrait.current) 16.dp else 24.dp, trailing = icons)
            }
            val titleRoom = if (chrome.tabRow) 0.dp else MENU_TAB_ROW_HEIGHT
            // While the tab row has the d-pad, the whole content below it (every row, tile, logo, name and count, the List's
            // band, Home's page) dims as one layer to [mainMenuContentAlpha], so only the lit tab reads as active. The tab
            // row, the hint bar and App's page background stay lit. Read at draw time: the fade recomposes nothing.
            val contentAlpha = animateFloatAsState(mainMenuContentAlpha(tabFocus), tween(TAB_FOCUS_DIM_MS), label = "tabFocusDim")
            val bodyAlpha = remember { { contentAlpha.value } }
            // One tap opens a console, collection, Tools tab tool or "+N"; a game (Continue, a pin) and a Home pinned tool
            // take two ([mainRowTakesTwoTaps]).
            val tapOpens = remember(rows) { { i: Int -> !mainRowTakesTwoTaps(rows.getOrNull(i)) } }
            CompositionLocalProvider(LocalNxTapOpens provides tapOpens) {
                // A Grid or Carousel body in the scaffold's slot: no info band (a Grid's game info is in its tile, a
                // row's under its centre item), so it runs from the tab row down to the hint bar. The scaffold draws no list
                // here, so its index is unused.
                // [keepBarRoom] false (a Carousel): Button hints hidden, the body runs down to the bottom too, so the row
                // centres between what shows above and below it, as a hidden page title gives it the title's room.
                @Composable fun slot(keepBarRoom: Boolean = true, body: @Composable (Modifier) -> Unit) {
                    NxScreenScaffold(
                        rows = emptyList(), selectedIndex = 0,
                        hints = hints.value, onSelect = onSelect, onOpen = onOpen, modifier = Modifier.weight(1f), onHint = onHint, grid = body,
                        bodyAlpha = bodyAlpha, hideHints = chrome.emptyBarRoom, keepBarRoom = keepBarRoom,
                    )
                }
                // Home: List, the Grid dashboard or Carousel with its tool dock, by Home's own style (Layouts → Home; never
                // Backdrop, which the arm below names only to stay exhaustive).
                if (home) {
                    when (style) {
                        // Its rows level with Recent and Search (tab row hidden) stop short of them, as a List tab's.
                        MenuStyle.LIST -> CompositionLocalProvider(LocalNxListTopCut provides topCut.takeIf { topIcons.isNotEmpty() }) {
                            HomeListScreen(
                                rows, selection, homeView.strip, info, hints.value, onSelect, onOpen, onLongPress, onHint, bodyAlpha,
                                Modifier.weight(1f), tabMenu = tabMenu,
                            )
                        }
                        MenuStyle.CAROUSEL, MenuStyle.BACKDROP -> {
                            val carousel = @Composable { m: Modifier ->
                                HomeCarouselScreen(
                                    rows, selection, homeView.strip, captionInfo = rowInfo, onSelect = onSelect, onOpen = onOpen,
                                    onLongPress = onLongPress, onLayout = onHomeLayout, modifier = m, layoutKey = layoutKey,
                                    rowLast = homeRowLast, known = homeView.frame != null, tabMenu = tabMenu,
                                )
                            }
                            // The bar's room is always kept under the carousel, touch only too (no bar there), so its
                            // rows sit in the same place whatever Button hints says.
                            NxScreenScaffold(
                                rows = emptyList(), selectedIndex = 0,
                                hints = hints.value, onSelect = onSelect, onOpen = onOpen, modifier = Modifier.weight(1f), onHint = onHint, grid = carousel,
                                bodyAlpha = bodyAlpha, hideHints = chrome.emptyBarRoom || chrome.noBar,
                            )
                        }
                        MenuStyle.MINIMAL -> HomeMinimalScreen(
                            rows, selection, homeView.strip, hints.value, onSelect, onOpen, onLongPress, onHint, bodyAlpha,
                            minimalSelected, modifier = Modifier.weight(1f).zIndex(-1f),   // its names run on under the tab row
                            leaving = minimalOpening != null,
                        )
                        MenuStyle.GRID -> slot { m ->
                            HomeScreen(
                                rows, selection, onSelect = onSelect, onOpen = onOpen, onLongPress = onLongPress, onLayout = onHomeLayout, view = homeView,
                                continueInfo = continueInfo(), pinInfo = tileInfo, signedIn = raSignedIn, modifier = m, layoutKey = layoutKey, tabMenu = tabMenu,
                            )
                        }
                    }
                    return@CompositionLocalProvider
                }
                when (style) {
                    MenuStyle.GRID -> {
                        val tiles = remember(rows) { gridTiles(rows) }
                        val top = topIconsRoom(chrome.tabRow, topIcons.isNotEmpty())
                        slot { m ->
                            NxTileGrid(
                                tiles, selection, onSelect = onSelect, onOpen = onOpen, onLongPress = onLongPress,
                                onColumns = onGridLayout, tileInfo = tileInfo, modifier = m.padding(top = top.pad), layoutKey = layoutKey,
                                titleRoom = top.titleRoom,
                                // Consoles' tiles are wide, two across, in a phone's two-row portrait ([gridFor]).
                                consoles = screen.category == MenuCategory.CONSOLES,
                            )
                        }
                    }
                    // Carousel: Backdrop's frameless row on black (no picture), centred alone with no caption block (a console's
                    // or collection's count is drawn by the row itself). A main menu tab is never Backdrop (a game-list style,
                    // not in [com.nxredux.mobile.settings.MenuStyles.MAIN_MENU_STYLES]); the arm names it only to stay exhaustive.
                    // The tiles are this screen's rows, so a tab switch hands the row a new list and its selection in one
                    // composition: the row snaps instead of sliding. Vertical: the same items stood on end ([NxBackdropStack]).
                    MenuStyle.CAROUSEL, MenuStyle.BACKDROP -> {
                        val tiles = remember(rows, controllerArt) { gridTiles(rows, controllerArt) }   // the controllers by Layouts → Consoles background
                        // Centred on the screen, whatever shows: half a shown tab row up, half a shown hint bar down (a
                        // hidden title's room is in the box already, [titleRoom]).
                        val shift = mainMenuCarouselShift(chrome.tabRow, barShown = !chrome.emptyBarRoom && !chrome.noBar)
                        slot(keepBarRoom = false) { m ->
                            if (orientation == MenuOrientation.VERTICAL) NxBackdropStack(
                                null, tiles, selection, onSelect = onSelect, onOpen = onOpen, onLongPress = onLongPress,
                                captionTitle = NoCaptionTitle, captionInfo = NoCaptionInfo, modifier = m, layoutKey = layoutKey, titleRoom = titleRoom,
                            ) else NxBackdrop(
                                null, tiles, selection, onSelect = onSelect, onOpen = onOpen, onLongPress = onLongPress,
                                captionTitle = NoCaptionTitle, captionInfo = NoCaptionInfo, modifier = m, layoutKey = layoutKey, titleRoom = titleRoom,
                                centreShift = shift,
                            )
                        }
                    }
                    // Minimal: the names alone, as Minimal Home's without its strip and tags, running on under the tab row.
                    MenuStyle.MINIMAL -> {
                        val names = remember(rows) { minimalTabRows(rows) }
                        MinimalScreen(
                            names, selection, hints.value, onSelect, onOpen, onLongPress, onHint, bodyAlpha, minimalSelected,
                            Modifier.weight(1f).zIndex(-1f), hideHints = chrome.emptyBarRoom, layoutKey = layoutKey, leaving = minimalOpening != null, onSwipeSelect = onSwipeSelect,
                        )
                    }
                    // List: the info band on the hint bar; the rows fade where more scrolls in.
                    MenuStyle.LIST -> {
                        val listRows = remember(rows, shared) {
                            rows.map { r ->
                                when (r) {
                                    is MainRow.Pin -> NxRow(r.entry.game.shownName)
                                    is MainRow.Shelf -> NxRow(r.entry.game.shownName)
                                    is MainRow.System -> NxRow(r.system.displayName, secondary = if (r.system.displayName in shared) r.system.tag else null)
                                    is MainRow.Collection -> NxRow(r.name)
                                    is MainRow.Tool -> NxRow(r.row.label)
                                    is MainRow.PinnedTool -> NxRow(r.row.label)
                                    is MainRow.Continue, is MainRow.MoreTools, MainRow.PickAGame -> homeOnly(r)
                                }
                            }
                        }
                        // The row labels (and the band's arrows) start on the tab row's gutter, the tab words' edge, as Home,
                        // Grid and Carousel do; other lists keep the narrower nxListInset.
                        val portrait = LocalPortrait.current
                        val gutter = if (portrait) 16.dp else 24.dp
                        // Hints hidden: no shade where the bar was (the tabs' page has none), the info stays on the band.
                        // No info to show (Consoles): no band, and with hints hidden no bar room either; the rows fill the
                        // page between set gaps ([mainMenuListEdges]) and fade where more scrolls in.
                        val bandless = !bandInfo
                        GameInfoPlacement(info, Modifier.weight(1f), textStart = gutter, ground = if (!chrome.emptyBarRoom) BandGround.BAR else BandGround.NONE, shade = bandShade) { scaffoldModifier, infoBand ->
                            val band = if (bandless) NxHintsBand(reserve = 0.dp, overlap = 0.dp) {} else infoBand
                            // Its rows level with Recent and Search (tab row hidden) stop short of them.
                            CompositionLocalProvider(LocalNxListTopCut provides topCut.takeIf { topIcons.isNotEmpty() }) { NxScreenScaffold(
                                rows = listRows,
                                selectedIndex = 0, selection = selection, showArt = false,   // App draws the tab's background behind; the rows read the selection
                                hints = hints.value, onSelect = onSelect, onOpen = onOpen,
                                modifier = scaffoldModifier, onHint = onHint, onLongPress = onLongPress, band = band,   // the list sits right under the tabs
                                labelStart = gutter,
                                listTopGap = if (portrait) 10.dp else 12.dp,   // the tab row hidden: the first row's gap under the page's top
                                // The tab row shown: the first pill sits as far under the selected tab's underline as the
                                // last pill sits over the band's arrows, in place of listTopGap ([mainMenuListTopEdge]).
                                listTopEdge = mainMenuListTopEdge(chrome.tabRow),
                                bodyAlpha = bodyAlpha, hideHints = chrome.emptyBarRoom, keepBarRoom = !bandless,
                                scrollFade = true,   // the rows fade where more scrolls in, no arrows on the band
                                rowEdges = if (bandless) mainMenuListEdges(chrome.tabRow, barShown = !chrome.emptyBarRoom && !chrome.noBar) else null,
                            ) }
                        }
                    }
                }
            }
        }
        // The tab row hidden (Page title), Recent and Search keep their icons at the top right corner. A List has the
        // tab row's eased band behind them (its rows scroll under them); a Grid keeps their room clear
        // ([topIconsRoom]) and a Carousel sits below them, so neither has a band dimming its first row's edge (Grid and
        // Carousel Home draw their own, under the stats header's strip).
        if (!chrome.tabRow) {
            NxTopIcons(
                topIcons,
                Modifier.align(Alignment.TopEnd).height(MENU_TAB_ROW_HEIGHT).padding(end = (if (LocalPortrait.current) 16.dp else 24.dp) - TOP_ICON_INSET)
                    .nxListTopCut(topCut),
            )
        }
        // A Minimal tab's open: the selected row's band grows over the whole screen, then it goes dark.
        if (style == MenuStyle.MINIMAL && minimalOpening?.band == true) MinimalOpenOverlay(minimalSelected, screenBox[0])
    }
}

/**
 * A bandless main menu List's gaps above its first row and below its last, in rows: a quarter under the tab row, half
 * with none; a quarter over a shown hint bar, else what's left of the row the top took (the bottom fade's room).
 */
internal fun mainMenuListEdges(tabRow: Boolean, barShown: Boolean): Pair<Float, Float> {
    val top = if (tabRow) 0.25f else 0.5f
    return top to if (barShown) 0.25f else 1f - top
}

/** How far a main menu Carousel's row moves down so it centres on the screen: half a hint bar, less half a tab row. */
internal fun mainMenuCarouselShift(tabRow: Boolean, barShown: Boolean): Dp =
    ((if (barShown) NX_HINT_BAR_ROW_HEIGHT else 0.dp) - (if (tabRow) MENU_TAB_ROW_HEIGHT else 0.dp)) / 2

/** The main menu's rows have no caption block (a console's or collection's count is drawn by the row itself). */
private val NoCaptionTitle: () -> String? = { null }
private val NoCaptionInfo: () -> List<InfoLine> = { emptyList() }

/**
 * The edge a main menu List's first pill is spaced from ([NxScreenScaffold]'s listTopEdge) with the tab row shown: the
 * selected tab's underline, [MENU_TAB_ROW_HEIGHT] less [menuTabUnderlineBottom] above the list's top, either
 * orientation. The scaffold spaces the rows so the gap under it equals the last pill's gap to the
 * band's arrows ([nxListFitUnder]). Null with no tab row: the scaffold uses its fixed listTopGap.
 */
internal fun mainMenuListTopEdge(tabRow: Boolean): Dp? =
    if (!tabRow) null else (MENU_TAB_ROW_HEIGHT.value - menuTabUnderlineBottom()).dp

/** A page's top under a hidden title row: [pad] kept clear above its body, [titleRoom] its body takes from the row ([NxTileGrid]). */
internal data class TopIconsRoom(val pad: Dp, val titleRoom: Dp)

/**
 * A page's top (a main menu tab's Grid, any game list): under the title row ([tabRow]) nothing to keep; the row hidden,
 * the body takes its room, unless the page's icons (Recent, Search, …) sit there ([topIcons]), where it keeps the row's
 * room clear so its first row is never under them.
 */
internal fun topIconsRoom(tabRow: Boolean, topIcons: Boolean): TopIconsRoom = when {
    tabRow -> TopIconsRoom(0.dp, 0.dp)
    topIcons -> TopIconsRoom(MENU_TAB_ROW_HEIGHT, 0.dp)
    else -> TopIconsRoom(0.dp, MENU_TAB_ROW_HEIGHT)
}

/** What stands where the tab row is: null for the tab row (two tabs or more); a pushed tab's label; else (one tab) the app's name. */
internal fun mainMenuTitle(screen: Screen.Systems): String? = when {
    screen.pushed -> screen.category?.label
    screen.tabs.size < 2 -> APP_NAME
    else -> null
}

/**
 * Whether the main menu draws the eased top band behind its tab row ([tabRow] shown): on every tab but Home, and on Home
 * in the List style ([style] Home's), whose page is drawn over the selected game's picture. Grid Home draws its own while
 * its page is scrolled; Carousel Home has no picture behind it. Minimal, on any tab, has none: its names run on under the
 * tab row, faded there.
 */
internal fun mainMenuTopBand(home: Boolean, style: MenuStyle, tabRow: Boolean): Boolean =
    tabRow && (style == MenuStyle.LIST || !home && style != MenuStyle.MINIMAL)

/** A Minimal tab's names: each row's name alone (a console's, a collection's, a tool's), no tag, count or art. */
internal fun minimalTabRows(rows: List<MainRow>): List<NxRow> = rows.map { r ->
    NxRow(
        when (r) {
            is MainRow.Pin -> r.entry.game.shownName
            is MainRow.Shelf -> r.entry.game.shownName
            is MainRow.System -> r.system.displayName
            is MainRow.Collection -> r.name
            is MainRow.Tool -> r.row.label
            is MainRow.PinnedTool -> r.row.label
            is MainRow.Continue, is MainRow.MoreTools, MainRow.PickAGame -> homeOnly(r)
        },
    )
}

/** Whether [row] launches a game (Home's Continue, a pinned game, an Android game's pin too, a shelf game). */
internal fun mainRowLaunchesGame(row: MainRow?): Boolean = row is MainRow.Continue || row is MainRow.Pin || row is MainRow.Shelf

/**
 * Whether [row] takes two taps, a tap to highlight and a tap on the highlighted one to open ([LocalNxTapOpens]): what
 * launches a game ([mainRowLaunchesGame]) and a tool pinned on Home ([MainRow.PinnedTool], only ever on Home). Every
 * other main menu item opens on one tap: a console, a collection, a Tools tab tool ([MainRow.Tool]), Home's "+N".
 */
internal fun mainRowTakesTwoTaps(row: MainRow?): Boolean = mainRowLaunchesGame(row) || row is MainRow.PinnedTool

/** The main menu content's alpha (everything below the tab row, the hint bar excepted): dimmed while the tab row has the d-pad. */
internal fun mainMenuContentAlpha(tabFocus: Boolean): Float = if (tabFocus) TAB_FOCUS_CONTENT_ALPHA else 1f

/**
 * How the main menu's List band dresses a tab's selected row. [shade]: the band's fade (and, hints hidden,
 * its solid fill); [info]: the row's info text ("N games"). Consoles, Collections and Tools have neither: no band, their
 * rows fade to set gaps ([mainMenuListEdges]); Home (and a tab-less list) keeps both.
 */
internal data class MainMenuListBand(val shade: Boolean, val info: Boolean)

/** The List band for [category]'s tab ([MainMenuListBand]): none on Consoles, Collections and Tools, shaded with its info elsewhere. */
internal fun mainMenuListBand(category: MenuCategory?): MainMenuListBand = when (category) {
    MenuCategory.CONSOLES, MenuCategory.COLLECTIONS, MenuCategory.TOOLS -> MainMenuListBand(shade = false, info = false)
    MenuCategory.HOME, null -> MainMenuListBand(shade = true, info = true)
}

/** The content's alpha while the tab row has the d-pad. */
internal const val TAB_FOCUS_CONTENT_ALPHA = 0.4f

/** The content's dim in and out as the tab row takes or gives up the d-pad. */
private const val TAB_FOCUS_DIM_MS = 180

/**
 * The hint bar for the highlighted row: SELECT (the switcher) first. A game (Continue, a pin or a shelf game), a tool
 * (on the Tools tab, or pinned on Home) and a collection have MENU (Options: the game's menu, a tool's Pin / Unpin, a
 * collection's Rename / Delete). A resumes a game with a save ([canResume]) or plays it without one; a pinned tool's A
 * names it ("A Game Tracker"), "+N" is "A More tools" (it opens the Tools tab, no MENU), and A opens anything else (a
 * tool, Pick a game, a console, a collection).
 * [tabFocus] (the tab row has the d-pad, any tab, Home too): SELECT Recent and A Open (A enters the content); no MENU,
 * no Resume or Play. [canNetplay]: the highlighted game has Netplay here, Y opens its sheet (a game row only).
 * [canSearch]: the library has games, START opens the search prompt (the tab row focused too). [tabMenu]: a hidden tab
 * is in MENU, so every row has MENU Options. [recent] false: SELECT Recent is not in the bar (an icon stands in).
 */
internal fun mainMenuHints(row: MainRow?, canResume: Boolean = false, tabFocus: Boolean = false, canNetplay: Boolean = false, canSearch: Boolean = false, pushed: Boolean = false, tabMenu: Boolean = false, recent: Boolean = true): List<Hint> = buildList {
    if (pushed) add(Hint(NxButton.B, "Back"))   // a pushed tab: B returns to the tab underneath
    if (recent) add(Hint(NxButton.SELECT, "Recent"))
    if (canSearch) add(Hint(NxButton.START, "Search"))   // NX Redux's START search, from the content or the tab row
    if (tabFocus) { add(Hint(NxButton.A, "Open")); return@buildList }
    if (tabMenu || row is MainRow.Pin || row is MainRow.Shelf || row is MainRow.Continue || row is MainRow.Tool || row is MainRow.PinnedTool || row is MainRow.Collection) add(Hint(NxButton.MENU, "Options"))
    if (canNetplay && row.listed() != null) add(Hint(NxButton.Y, "Netplay"))
    add(Hint(NxButton.A, when (row) {
        is MainRow.Continue, is MainRow.Pin, is MainRow.Shelf -> if (canResume) "Resume" else "Play"
        is MainRow.PinnedTool -> row.row.label
        is MainRow.MoreTools -> "More tools"
        else -> "Open"
    }))
}
