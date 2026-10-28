package com.nxredux.mobile.ui

import com.nxredux.mobile.ui.nx.nxModalText
import com.nxredux.mobile.ui.nx.touchOnly
import com.nxredux.mobile.runner.CoreRegistry
import com.nxredux.mobile.ui.nx.touchIcons
import com.nxredux.mobile.settings.BackdropArt
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.structuralEqualityPolicy
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.platform.LocalDensity
import com.nxredux.mobile.settings.MenuOrientation
import com.nxredux.mobile.settings.MenuStyle
import com.nxredux.mobile.settings.VerticalListAlignment
import com.nxredux.mobile.ui.nx.BandGround
import com.nxredux.mobile.ui.nx.GameInfoPlacement
import com.nxredux.mobile.ui.nx.LauncherChrome
import com.nxredux.mobile.ui.nx.NxTopIcons
import com.nxredux.mobile.ui.nx.LocalNxListTopCut
import com.nxredux.mobile.ui.nx.nxListTopCut
import com.nxredux.mobile.ui.nx.rememberNxListTopCut
import com.nxredux.mobile.ui.nx.TOP_ICON_INSET
import com.nxredux.mobile.ui.nx.TopIconAction
import com.nxredux.mobile.ui.nx.launcherTopActions
import com.nxredux.mobile.ui.nx.LocalCaptionTap
import com.nxredux.mobile.ui.nx.LocalHasTouchscreen
import com.nxredux.mobile.shared.res.Res
import com.nxredux.mobile.shared.res.nav_icon_layout
import com.nxredux.mobile.shared.res.menu_icon_achievements
import com.nxredux.mobile.shared.res.nav_icon_recent
import com.nxredux.mobile.shared.res.nav_icon_search
import com.nxredux.mobile.ui.nx.LocalLauncherChrome
import com.nxredux.mobile.ui.nx.Hint
import com.nxredux.mobile.ui.nx.LocalNxColors
import com.nxredux.mobile.ui.nx.LocalNxFont
import com.nxredux.mobile.ui.nx.LocalNxTapOpens
import com.nxredux.mobile.ui.nx.LocalPortrait
import com.nxredux.mobile.ui.nx.MENU_TAB_ROW_HEIGHT
import com.nxredux.mobile.ui.nx.MenuTitleRow
import com.nxredux.mobile.ui.nx.NX_TOP_BAND_REACH
import com.nxredux.mobile.ui.nx.NxBackdrop
import com.nxredux.mobile.ui.nx.NxBackdropStack
import com.nxredux.mobile.ui.nx.NxButton
import com.nxredux.mobile.ui.nx.NxCarousel
import com.nxredux.mobile.ui.nx.NxCarouselStack
import com.nxredux.mobile.ui.nx.NxRow
import com.nxredux.mobile.ui.nx.NxHintsBand
import com.nxredux.mobile.ui.nx.NxScreenScaffold
import com.nxredux.mobile.ui.nx.NxTapNeverOpens
import com.nxredux.mobile.ui.nx.NxTextSizes
import com.nxredux.mobile.ui.nx.NxTileGrid
import com.nxredux.mobile.ui.nx.nxListInset
import com.nxredux.mobile.ui.nx.nxTopBandFade
import com.nxredux.mobile.ui.nx.LocalMinimalPage
import com.nxredux.mobile.ui.nx.MINIMAL_MORPH_MS
import com.nxredux.mobile.ui.nx.MinimalFlight
import com.nxredux.mobile.ui.nx.MinimalMorphEasing
import com.nxredux.mobile.ui.nx.MorphText
import com.nxredux.mobile.ui.nx.menuTitleStyle
import com.nxredux.mobile.ui.nx.morphPlace
import com.nxredux.mobile.ui.nx.MinimalScreen
import com.nxredux.mobile.ui.nx.MinimalSelected
import com.nxredux.mobile.ui.nx.LocalNxHintBarStart
import com.nxredux.mobile.ui.nx.MainListBody
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.rememberTextMeasurer
import com.nxredux.mobile.ui.nx.nxTextScale
import com.nxredux.mobile.ui.nx.HOME_STRIP_TYPE
import com.nxredux.mobile.ui.nx.GameFactsLines
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.zIndex

/**
 * A mixed list's second line: the tag as the handheld names it (GPGX_SMS shows as GPGX, CoreRegistry.handheldTag), then an
 * extra copy's folder ("DC · My Dreamcast"); Unassigned shows its dir.
 */
internal fun tagLine(listed: Listed): String? =
    if (listed.system.unassigned) listed.game.subtitle
    else listOfNotNull(CoreRegistry.handheldTag(listed.system.tag), listed.game.subtitle).joinToString(" · ")

/**
 * A game list under its [title] (the console's or collection's name, grey), where the main menu has its tab row. The
 * selection-dependent inputs ([selectedIndex], [canResume], [canOpen], [info], [tileInfo]) are lambdas read only where
 * they show (the row or list, the caption, the hints, the info band), so a selection change recomposes those alone.
 */
@Composable
fun GameListScreen(
    title: String, entries: List<Listed>, selectedIndex: () -> Int,
    canResume: () -> Boolean = { false }, menuAvailable: Boolean = false, canOpen: () -> Boolean = { true }, openLabel: String = "Open",
    canNetplay: () -> Boolean = { false },   // the selected game has Netplay here: Y opens its sheet
    onSelect: (Int) -> Unit, onOpen: (Int) -> Unit, modifier: Modifier = Modifier,
    onHint: ((NxButton) -> Unit)? = null, onLongPress: ((Int) -> Unit)? = null,
    showTag: Boolean = false,
    emptyText: String? = null,            // shown centred when [entries] is empty (a search with no matches)
    info: () -> List<InfoLine> = { emptyList() },   // the selected game's info (gameInfoLines): List's band, Carousel's and Backdrop's caption
    style: MenuStyle = MenuStyle.LIST,    // the game list style
    orientation: MenuOrientation = MenuOrientation.HORIZONTAL,   // the game list orientation (Carousel / Backdrop: Vertical is the stack)
    alignment: VerticalListAlignment = VerticalListAlignment.LEFT,   // Vertical: the stack's side (Layouts → Vertical alignment)
    onGridLayout: (Int) -> Unit = {},   // a Grid reports its column count
    onPageRows: ((Int) -> Unit)? = null,   // a List, Minimal or vertical stack reports the items one screen shows (LEFT / RIGHT page)
    tileInfo: (Int) -> List<InfoLine> = { emptyList() },   // Grid: a game's count-only info, in its tile (read lazily per tile)
    layoutKey: Any? = null,               // the list laid out (its source): a Grid re-reports its columns, a Carousel / Backdrop row snaps, for each
    artBox: Boolean = false,   // Layouts → List art is a boxed picture: the names stop short of it
    backdropArt: BackdropArt = BackdropArt.BOX3D,   // Layouts → Backdrop art
    onSearch: (() -> Unit)? = null,       // the list can be searched: the Search icon (touchscreens)
    onLayout: (() -> Unit)? = null,       // the Layout icon (touchscreens), opening its dropdown
    layoutOpen: Boolean = false,          // the dropdown is open: its icon lit
    onAchievements: (() -> Unit)? = null, // the trophy icon (touchscreens) and X's hint: the achievements panel
    achievementsOpen: Boolean = false,    // the panel is open: its icon lit
    minimalOpening: MinimalOpening? = null,   // Minimal: this list's selected row opening as a folder: the others fade (a launch runs App's [LaunchOverlay])
    tapOpens: (Int) -> Boolean = NxTapNeverOpens,   // one tap on another game opens it too ([LocalNxTapOpens]); the second display's preview
) {
    val index = rememberUpdatedState(selectedIndex)
    val resume = rememberUpdatedState(canResume)
    val open = rememberUpdatedState(canOpen)
    val netplay = rememberUpdatedState(canNetplay)
    val netplayHint = LocalLauncherChrome.current.netplayHint   // Layouts → Netplay hint (hidden, the menu leads with Netplay)
    // Equal hints keep one list, so the hint bar recomposes only when they change (A Resume or Open, showing or not).
    val achievementsHint = onAchievements != null
    val hints = remember(menuAvailable, openLabel, netplayHint, achievementsHint) {
        derivedStateOf(structuralEqualityPolicy()) {
            buildList {
                // No B Back: B still leaves, the bar keeps its room for what the game offers.
                if (menuAvailable) add(Hint(NxButton.MENU, "Options"))
                if (achievementsHint) add(Hint(NxButton.X, "Achievements"))   // the panel (no touch stand-in: the trophy icon is one)
                if (netplayHint && netplay.value()) add(Hint(NxButton.Y, "Netplay"))
                // A resumes a game's save state. An unassigned game has no emulator yet: Options is the only action.
                if (open.value()) add(Hint(NxButton.A, if (resume.value()) "Resume" else openLabel))
            }
        }
    }
    // Every row launches a game (or, Hidden, unhides one): tap to highlight, tap again to act ([LocalNxTapOpens]) in
    // every style, so a stray tap never plays. Its context menu and dialogs sit outside this and open on one tap. The
    // second display's preview ([tapOpens]) launches nothing: one tap opens the list there.
    CompositionLocalProvider(LocalNxTapOpens provides tapOpens) {
        GameListFrame(
            title, style, hints, { index.value() }, dataKey = listOf(entries, showTag, backdropArt), orientation = orientation, alignment = alignment,
            gridTiles = { gameGridTiles(entries) }, backdropGames = { backdropGames(entries, backdropArt) },
            listRows = { entries.map { NxRow(it.shown, secondary = if (showTag) tagLine(it) else it.game.subtitle) } },
            minimalRows = { entries.map { NxRow(it.shown) } },
            captionTitle = { entries.getOrNull(index.value())?.shown },
            onSelect = onSelect, onOpen = onOpen, modifier = modifier, onHint = onHint, onLongPress = onLongPress,
            info = info, onGridLayout = onGridLayout, onPageRows = onPageRows, tileInfo = tileInfo, layoutKey = layoutKey,
            emptyText = emptyText.takeIf { entries.isEmpty() }, artBox = artBox,
            onSearch = onSearch, onLayout = onLayout, layoutOpen = layoutOpen,
            onAchievements = onAchievements, achievementsOpen = achievementsOpen, minimalOpening = minimalOpening,
        )
    }
}

/**
 * A List game list (and the switcher's List, [Home's List][com.nxredux.mobile.ui.nx.HomeListScreen]) shows
 * the selected game's stats only in the hint bar's row: with Button hints hidden ([emptyBarRoom]), or on a touch-only
 * screen ([touch]), where there is no bar and the row is kept for them. With the bar shown, no stats.
 */
internal fun gameListInfoInBarRow(emptyBarRoom: Boolean, touch: Boolean): Boolean = emptyBarRoom || touch

/** No info: the band of a List whose stats are not shown ([gameListInfoInBarRow] false). */
internal val NoGameInfo: () -> List<InfoLine> = { emptyList() }

/**
 * A game list's page under its grey [title] (where the main menu has its tab row), in its [style]: the body of
 * [GameListScreen]. The style's items are built only for the style shown,
 * once per [dataKey]: [gridTiles] (Grid, Carousel), [backdropGames] (Backdrop's games row), [listRows] (List; App draws the selected
 * game's screenshot behind it) and [minimalRows] (Minimal: the names alone, on plain black).
 */
@Composable
internal fun GameListFrame(
    title: String, style: MenuStyle, hints: State<List<Hint>>, selectedIndex: () -> Int, dataKey: Any?,
    gridTiles: () -> List<GridTile>, backdropGames: () -> List<BackdropGame>,
    listRows: () -> List<NxRow>, captionTitle: () -> String?, minimalRows: () -> List<NxRow> = listRows,
    onSelect: (Int) -> Unit, onOpen: (Int) -> Unit, modifier: Modifier = Modifier,
    onHint: ((NxButton) -> Unit)? = null, onLongPress: ((Int) -> Unit)? = null,
    info: () -> List<InfoLine> = { emptyList() }, onGridLayout: (Int) -> Unit = {},
    onPageRows: ((Int) -> Unit)? = null,
    tileInfo: (Int) -> List<InfoLine> = { emptyList() }, layoutKey: Any? = null,
    orientation: MenuOrientation = MenuOrientation.HORIZONTAL,
    alignment: VerticalListAlignment = VerticalListAlignment.LEFT,
    emptyText: String? = null,            // centred over the page (a list with no rows: a search's "No results")
    artBox: Boolean = false,   // List: a boxed List art on the right, the names stopping short of it
    recentIcon: Boolean = true,   // false inside Recent itself: no Recent icon on touch
    onSearch: (() -> Unit)? = null, onLayout: (() -> Unit)? = null, layoutOpen: Boolean = false,   // a game list's Search and Layout icons
    onAchievements: (() -> Unit)? = null, achievementsOpen: Boolean = false,   // and its trophy icon
    minimalOpening: MinimalOpening? = null,   // Minimal: this list's selected row opening as a folder: the others fade
) {
    // Layouts → Page title and Button hints, in effect ([LocalLauncherChrome]): hidden, the hint bar keeps its room; the
    // title's goes to the body (a List gets more rows, the other styles centre in it).
    val chrome = LocalLauncherChrome.current
    // The page's icons, built once for the title row's right or, with Page title Off, the page's top right.
    val touch = touchOnly()
    val titleIcons = gameListTitleIcons(chrome, hints.value, onHint, recentIcon, touch, LocalHasTouchscreen.current, onSearch, onLayout, layoutOpen, onAchievements, achievementsOpen)
    // The selected game's name and info open the achievements panel too, as the trophy does (Carousel, Backdrop, List's info).
    val iconsUp = !chrome.listTitle && titleIcons.isNotEmpty()   // the icons at the page's top right
    val topCut = rememberNxListTopCut()
    // Minimal: the selected name as last laid out (where B's morph lands), and this page's box (a morph's flight's).
    val minimalSelected = remember { MinimalSelected() }
    val pageBox = remember { arrayOfNulls<LayoutCoordinates>(1) }
    // A Minimal list come in by a folder's morph: the last page's selected name flies into this title as the names rise in.
    val minimalPage = LocalMinimalPage.current
    val flight = remember { minimalPage?.morph?.name.takeIf { style == MenuStyle.MINIMAL && minimalPage?.arriving(NavMotion.MINIMAL_FORWARD, "flight") == true } }
    val flown = remember { Animatable(if (flight != null) 0f else 1f) }
    LaunchedEffect(flown) { if (flown.value < 1f) flown.animateTo(1f, tween(MINIMAL_MORPH_MS, easing = MinimalMorphEasing)) }
    val titleText = remember { arrayOfNulls<LayoutCoordinates>(1) }
    // No title shown (Page title Off): B has none to drop back, not an older list's.
    if (!LocalLauncherChrome.current.listTitle) SideEffect { if (minimalPage?.live?.value == true) minimalPage.morph.title = null }
    val topStats = gameListTopStats(style, chrome.listTitle, chrome.extraInfo, gameListInfoInBarRow(chrome.emptyBarRoom, touch))
    // The hint bar in line with the page's title and text, both sides ([LocalNxHintBarStart]): the gutter and the List
    // pill's inset, in every layout (Carousel and Backdrop: their bars only, their rows keep the gutter).
    val barInset = (if (LocalPortrait.current) 16.dp else 24.dp) + nxListInset()
    CompositionLocalProvider(LocalCaptionTap provides onAchievements, LocalNxHintBarStart provides barInset) {
        Box(modifier.fillMaxSize().onGloballyPositioned { pageBox[0] = it }) {
            // The main menu's top band behind the title, so the rows line up with the tabs' and the grey name reads over art.
            // Not over a Backdrop picture: its shade does this job, and the two would stack; nor over Minimal's names, which
            // stop at their band's edges, faded to nothing before the title. No title, no band, unless a List runs up under
            // the page's icons: they keep the band, as the main menu's List does.
            if (style != MenuStyle.BACKDROP && style != MenuStyle.MINIMAL && chrome.listTitle || style == MenuStyle.LIST && iconsUp) Box(Modifier.fillMaxWidth().height(MENU_TAB_ROW_HEIGHT + NX_TOP_BAND_REACH).nxTopBandFade(LocalNxColors.current.background))
            Column(Modifier.fillMaxSize()) {
                // The name starts where the body's text does: the list labels' (the gutter and the pill's inset) in List style, the gutter the Grid's tiles
                // and the Carousel's and Backdrop's captions start on otherwise. Backdrop: over the selected game's screenshot.
                val listGutter = if (LocalPortrait.current) 16.dp else 24.dp   // a List's pill edge ([MainListBody])
                val textStart = listGutter + nxListInset()   // every layout's title and bars; List, Grid and Minimal's content too
                // The page's icons at the title's right ([gameListTitleIcons]).
                if (chrome.listTitle) {
                    val titleStyle = menuTitleStyle()
                    MenuTitleRow(
                        title, overArt = style == MenuStyle.BACKDROP, textStart = textStart,
                        // Where a B morph back to a Minimal tab starts (while this page shows); hidden while the name flies in.
                        textModifier = Modifier.onGloballyPositioned {
                            titleText[0] = it
                            if (minimalPage?.live?.value == true) morphPlace(it).let { (at, s) -> minimalPage.morph.title = MorphText(title, at, it.size.width.toFloat(), s, titleStyle) }
                        }.graphicsLayer { alpha = if (flown.value < 1f) 0f else 1f },
                        trailing = { NxTopIcons(titleIcons) }, endInset = textStart - listGutter,
                    )
                    // The selected game's info on a line under the title, in line with it, as Home's facts line reads
                    // ([gameListTopStats]): List with Button hints shown (hidden, it is in the bar's row), Minimal and Grid
                    // (its selected tile then has none). Carousel and Backdrop keep theirs in their captions.
                    // Right under the title's text (the row's room below it taken back), on two lines upright (its next goal
                    // on the second when the width runs out: [GameFactsLines]).
                    if (topStats) {
                        val scale = nxTextScale()
                        val titleH = with(LocalDensity.current) { rememberTextMeasurer().measure("Ag", menuTitleStyle(), maxLines = 1).size.height.toDp() }
                        val under = ((MENU_TAB_ROW_HEIGHT - titleH) / 2).coerceAtLeast(0.dp)
                        GameFactsLines(
                            info(), (HOME_STRIP_TYPE.sp * scale).sp, (HOME_STRIP_TYPE.lineH * scale).dp, rows = if (LocalPortrait.current) 2 else 1,
                            Modifier.fillMaxWidth().pullUp(under).padding(start = textStart, end = textStart),
                        )
                    }
                }
                // Hidden: a List runs up into its room; a Grid, Carousel or Backdrop (row or stack) takes it into its box,
                // sized as with the title, centred in the taller box. A Backdrop's picture stays put. With the page's icons
                // up there (Button hints hidden too, or touch), every layout keeps the row's room clear instead ([topIconsRoom]).
                // A List runs up under the icons too, its rows level with them stopping short of them ([LocalNxListTopCut]).
                val top = topIconsRoom(chrome.listTitle, titleIcons.isNotEmpty())
                if (top.pad > 0.dp && style != MenuStyle.LIST) Spacer(Modifier.height(top.pad))
                val titleRoom = top.titleRoom
                // A Grid, Carousel or Backdrop body in the scaffold's slot: no info band (a Grid's game info is in its tile, a
                // row's under its centre item), so it runs from the title down to the hint bar. The scaffold draws no list
                // here, so its index is unused.
                val slot = @Composable { body: @Composable (Modifier) -> Unit ->
                    NxScreenScaffold(
                        rows = emptyList(), selectedIndex = 0,
                        hints = hints.value, onSelect = onSelect, onOpen = onOpen, modifier = Modifier.weight(1f), onHint = onHint, grid = body,
                        hideHints = chrome.emptyBarRoom,
                    )
                }
                when (style) {
                    MenuStyle.GRID -> {
                        val tiles = remember(dataKey) { gridTiles() }
                        slot { m ->
                            NxTileGrid(
                                tiles, selectedIndex, onSelect = onSelect, onOpen = onOpen, onLongPress = onLongPress,
                                onColumns = onGridLayout, tileInfo = if (topStats) NoTileInfo else tileInfo,
                                modifier = m.padding(horizontal = nxListInset()), layoutKey = layoutKey, games = true,   // in line with the title and bars
                            )
                        }
                    }
                    // Carousel and Backdrop: the selected game's name and [info] under the centre item.
                    MenuStyle.CAROUSEL -> {
                        val tiles = remember(dataKey) { gridTiles() }
                        slot { m ->
                            // Vertical: the tiles stacked on end, Backdrop's three-row caption beside them.
                            if (orientation == MenuOrientation.VERTICAL) NxCarouselStack(
                                tiles, selectedIndex, onSelect = onSelect, onOpen = onOpen, onLongPress = onLongPress,
                                captionTitle = captionTitle, captionInfo = info, modifier = m, layoutKey = layoutKey, alignment = alignment,
                                titleRoom = titleRoom, onPageRows = onPageRows, drained = true,
                            ) else NxCarousel(
                                tiles, selectedIndex, onSelect = onSelect, onOpen = onOpen, onLongPress = onLongPress,
                                captionTitle = captionTitle, captionInfo = info, modifier = m, layoutKey = layoutKey, titleRoom = titleRoom,
                                gameList = true, drained = true,
                            )
                        }
                    }
                    MenuStyle.BACKDROP -> {
                        val games = remember(dataKey) { backdropGames() }
                        slot { m ->
                            // Vertical: the box art stacked on end, the caption beside it; the picture behind is the same.
                            if (orientation == MenuOrientation.VERTICAL) NxBackdropStack(
                                games, emptyList(), selectedIndex, onSelect = onSelect, onOpen = onOpen, onLongPress = onLongPress,
                                captionTitle = captionTitle, captionInfo = info, modifier = m, layoutKey = layoutKey, alignment = alignment,
                                titleRoom = titleRoom, onPageRows = onPageRows,
                            ) else NxBackdrop(
                                games, emptyList(), selectedIndex, onSelect = onSelect, onOpen = onOpen, onLongPress = onLongPress,
                                captionTitle = captionTitle, captionInfo = info, modifier = m, layoutKey = layoutKey, titleRoom = titleRoom,
                            )
                        }
                    }
                    // Minimal: the names alone (no art, tags or info), in the band from the title (or icons' room) to the hint
                    // bar's top, or the page's bottom with hints hidden.
                    MenuStyle.MINIMAL -> {
                        val names = remember(dataKey) { minimalRows() }
                        MinimalScreen(
                            names, selectedIndex, hints.value, onSelect, onOpen, onLongPress, onHint, minimalSelected,
                            Modifier.weight(1f).zIndex(-1f), hideHints = chrome.emptyBarRoom, toBoxBottom = chrome.emptyBarRoom,
                            layoutKey = layoutKey, leaving = minimalOpening != null, tapOpens = LocalNxTapOpens.current,   // the list's own rule (two taps, but for the preview's)
                            inset = nxListInset(),   // the names in line with the title and bars
                            onPageRows = onPageRows,
                        )
                    }
                    // List: the rows fade where more scrolls in (no band, no arrows), between a main menu List's gaps.
                    MenuStyle.LIST -> {
                        val rows = remember(dataKey) { listRows() }
                        // The selected game's info shows only in the hint bar's row (hints hidden, or touch only with no bar),
                        // only that row shaded; Extra info off leaves nothing for it, so its room goes too.
                        val barHidden = gameListInfoInBarRow(chrome.emptyBarRoom, touchOnly())
                        val inRow = barHidden && chrome.extraInfo
                        GameInfoPlacement(if (inRow) info else NoGameInfo, Modifier.weight(1f), textStart = textStart, ground = if (inRow) BandGround.SHADE_WITH_INFO else BandGround.BAR, shade = false) { scaffoldModifier, infoBand ->
                            // The band's room goes to the rows; only its line in the bar's row ([NxHintsBand.barInfo]) stays.
                            val band = NxHintsBand(reserve = 0.dp, overlap = 0.dp, barInfo = infoBand.barInfo) {}
                            // The rows as the main menu's List draws them ([MainListBody]): the pill on the gutter, whole-row
                            // fades, the selection free until the third row from an edge; under the page's icons with no title.
                            NxScreenScaffold(
                                rows = emptyList(), selectedIndex = 0, hints = hints.value,
                                onSelect = onSelect, onOpen = onOpen, modifier = scaffoldModifier, onHint = onHint,
                                band = band, hideHints = barHidden, hintShade = true, keepBarRoom = inRow || !barHidden,
                                grid = { m ->
                                    BoxWithConstraints(m) {
                                        MainListBody(
                                            rows, selectedIndex, if (iconsUp) MENU_TAB_ROW_HEIGHT else 0.dp, listGutter, onSelect, onOpen, onLongPress,
                                            endRoom = if (artBox) listArtLabelEnd(maxWidth.value, LocalPortrait.current).dp else 0.dp, onPageRows = onPageRows,
                                            // A row's room under the stats line (shown under the title), as Home keeps under its stats.
                                            gapRows = if (chrome.listTitle && chrome.extraInfo && !barHidden) 1 else 0,
                                        )
                                    }
                                },
                            )
                        }
                    }
                }
            }
            // Page title Off, the icons the title row would hold sit at the page's top right over the body
            // (the title room above the list is [MENU_TAB_ROW_HEIGHT] tall), ending on the gutter like the title row's.
            if (iconsUp) NxTopIcons(
                titleIcons,
                Modifier.align(Alignment.TopEnd).height(MENU_TAB_ROW_HEIGHT)
                    .padding(end = (if (LocalPortrait.current) 16.dp else 24.dp) - TOP_ICON_INSET + nxListInset()).nxListTopCut(topCut),
            )
            emptyText?.let {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(it, color = LocalNxColors.current.listText, fontFamily = LocalNxFont.current, fontSize = nxModalText())
                }
            }
            // The folder's name landing in the title (none with Page title Off: it fades where it was).
            if (flight != null && flown.value < 1f) {
                MinimalFlight(flight, title, menuTitleStyle(), { titleText[0]?.takeIf { it.isAttached && chrome.listTitle }?.let(::morphPlace) }, { flown.value }, { pageBox[0] })
            }
        }
    }
}

/**
 * The icons a game list's page holds, at the title's right or, with Page title Off, the page's top right. None without
 * [onHint]. On a device with a touchscreen ([touchscreen]): Recent (unless [recentIcon] is false, as inside Recent
 * itself), Search ([onSearch]), Layout ([onLayout], lit while its dropdown is open) and the trophy ([onAchievements], lit
 * while its panel is open), whatever the bar shows; without one, the launcher's ([launcherTopActions]: Recent, always
 * an icon), and Settings → Layouts, START and X stand in for the rest. Then, touch only ([touch]), the [hints]'
 * own ([HintTouch.Icon], the switcher's trash).
 */
internal fun gameListTitleIcons(
    chrome: LauncherChrome, hints: List<Hint>, onHint: ((NxButton) -> Unit)?, recentIcon: Boolean, touch: Boolean,
    touchscreen: Boolean = false, onSearch: (() -> Unit)? = null, onLayout: (() -> Unit)? = null, layoutOpen: Boolean = false,
    onAchievements: (() -> Unit)? = null, achievementsOpen: Boolean = false,
): List<TopIconAction> {
    if (onHint == null) return emptyList()
    val launcher = if (touchscreen) buildList {
        if (recentIcon) add(TopIconAction(Res.drawable.nav_icon_recent, "Recent") { onHint(NxButton.SELECT) })
        onSearch?.let { add(TopIconAction(Res.drawable.nav_icon_search, "Search", onClick = it)) }
        onLayout?.let { add(TopIconAction(Res.drawable.nav_icon_layout, "Layout", lit = layoutOpen, onClick = it)) }
        onAchievements?.let { add(TopIconAction(Res.drawable.menu_icon_achievements, "Achievements", lit = achievementsOpen, onClick = it)) }
    }
    // Recent, always an icon; the page's own icons on touch only (a pad has the button).
    else launcherTopActions(onHint, canSearch = false, recent = recentIcon)
    return launcher + if (touch) hints.touchIcons(onHint) else emptyList()
}

/** Laid out [by] higher than it would sit, taking that much less room: drawn into the room above it (the title row's). */
private fun Modifier.pullUp(by: Dp): Modifier = layout { m, c ->
    val p = m.measure(c)
    val up = by.roundToPx().coerceAtMost(p.height)
    layout(p.width, p.height - up) { p.place(0, -up) }
}

/**
 * Whether a game list in [style] shows the selected game's info on a line under its title ([titleShown]; Extra info
 * [extraInfo] on): a List while its hint bar shows (with [infoInBarRow] the info has the bar's empty row), Minimal and
 * Grid always. Carousel and Backdrop show theirs in their captions.
 */
internal fun gameListTopStats(style: MenuStyle, titleShown: Boolean, extraInfo: Boolean, infoInBarRow: Boolean): Boolean =
    titleShown && extraInfo && when (style) {
        MenuStyle.LIST -> !infoInBarRow
        MenuStyle.MINIMAL, MenuStyle.GRID -> true
        MenuStyle.CAROUSEL, MenuStyle.BACKDROP -> false
    }

/** A Grid's tiles with the info on the line under the title ([gameListTopStats]): none in the selected tile. */
private val NoTileInfo: (Int) -> List<InfoLine> = { emptyList() }
