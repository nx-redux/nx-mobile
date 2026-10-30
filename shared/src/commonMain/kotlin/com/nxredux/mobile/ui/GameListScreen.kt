package com.nxredux.mobile.ui

import com.nxredux.mobile.ui.nx.HomeFactsLine
import com.nxredux.mobile.ui.nx.HOME_GRID_HEADER_PAD
import com.nxredux.mobile.ui.nx.homeStripPitch
import com.nxredux.mobile.ui.nx.homeStripLineH
import com.nxredux.mobile.ui.nx.HomeFrame
import com.nxredux.mobile.ui.nx.HOME_LIST_GAP_ROWS
import com.nxredux.mobile.ui.nx.LocalNxHintBarEnd
import com.nxredux.mobile.ui.nx.nxPageInset
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
import androidx.compose.foundation.layout.offset
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
import com.nxredux.mobile.ui.nx.LocalNxHintBarHeight
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
 * Whether a List has no hint bar: Button hints hidden ([emptyBarRoom]), or a touch-only screen ([touch]). A game list
 * with Page title Off then shows the selected game's stats in the bar's row (with the title, under it:
 * [gameListTopStats]); the switcher's List and [Home's List][com.nxredux.mobile.ui.nx.HomeListScreen] always do.
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
    val topStats = gameListTopStats(style, chrome.listTitle, chrome.extraInfo)
    // The hint bar as the main menu's tab row, in its place: on the page inset both sides ([LocalNxHintBarStart]), as the
    // title and the names, and the tab row's height ([LocalNxHintBarHeight]), its hints where the tab words sit.
    val inset = nxPageInset()
    CompositionLocalProvider(LocalCaptionTap provides onAchievements, LocalNxHintBarStart provides inset.start, LocalNxHintBarEnd provides inset.end, LocalNxHintBarHeight provides MENU_TAB_ROW_HEIGHT) {
        Box(modifier.fillMaxSize().onGloballyPositioned { pageBox[0] = it }) {
            // The main menu's top band behind the title, so the rows line up with the tabs' and the grey name reads over art.
            // Not over a Backdrop picture: its shade does this job, and the two would stack; nor over Minimal's names, which
            // stop at their band's edges, faded to nothing before the title. No title, no band, unless a List runs up under
            // the page's icons: they keep the band, as the main menu's List does.
            if (style != MenuStyle.BACKDROP && style != MenuStyle.MINIMAL && chrome.listTitle || style == MenuStyle.LIST && iconsUp) Box(Modifier.fillMaxWidth().height(MENU_TAB_ROW_HEIGHT + NX_TOP_BAND_REACH).nxTopBandFade(LocalNxColors.current.background))
            Column(Modifier.fillMaxSize()) {
                // Words and bars on the page inset in every layout, as Home's stats and tab words: the title, its facts line,
                // the names, the hint bar. Backdrop: the title over the selected game's screenshot.
                val textStart = inset.start
                val titleStart = inset.start
                // The page's icons at the title's right ([gameListTitleIcons]).
                // With the selected game's info shown under it ([gameListTopStats]: List, Minimal and Grid), the main menu's
                // two-line header instead, as Consoles' name over its count ([GridTitleLines]): the name in Home's strip type
                // on the first line, the facts on the second ([HomeFactsLine]), the block centred in the header as
                // [homeStatsHeader] centres it, short of the icons at the right. With achievements (the trophy icon's rule,
                // [onAchievements]) a third line is kept for the next goal ("Next: …"), whatever is selected, so the list
                // never moves; without, the header is two lines and the list has that room.
                if (chrome.listTitle && topStats) {
                    val k = nxTextScale()
                    val frame = HomeFrame(0f, 0f, LocalPortrait.current, textScale = LocalDensity.current.fontScale, stripScale = k)
                    val lh = homeStripLineH(frame)
                    val pitch = homeStripPitch(frame)
                    val next = onAchievements != null
                    val block = (if (next) 2 else 1) * pitch + lh
                    val height = maxOf(MENU_TAB_ROW_HEIGHT.value, block + 2 * HOME_GRID_HEADER_PAD)
                    val size = (HOME_STRIP_TYPE.sp * k).sp
                    val titleStyle = TextStyle(fontFamily = LocalNxFont.current, fontWeight = FontWeight.SemiBold, fontSize = size, lineHeight = (HOME_STRIP_TYPE.lineH * k).sp, color = LocalNxColors.current.listText)
                    val iconsRoom = MENU_TAB_ROW_HEIGHT * titleIcons.size - TOP_ICON_INSET
                    Box(Modifier.fillMaxWidth().height(height.dp)) {
                        Column(Modifier.fillMaxWidth().padding(start = titleStart, end = inset.end + iconsRoom, top = ((height - block) / 2).dp)) {
                            Box(Modifier.height(lh.dp), contentAlignment = Alignment.CenterStart) {
                                Text(
                                    title, style = titleStyle, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    // Where a B morph back to a Minimal tab starts (while this page shows); hidden while the name flies in.
                                    modifier = Modifier.onGloballyPositioned {
                                        titleText[0] = it
                                        if (minimalPage?.live?.value == true) morphPlace(it).let { (at, s) -> minimalPage.morph.title = MorphText(title, at, it.size.width.toFloat(), s, titleStyle) }
                                    }.graphicsLayer { alpha = if (flown.value < 1f) 0f else 1f },
                                )
                            }
                            // Each line [pitch] under the last: the boxes may overlap, as the main menu's.
                            val facts = info()
                            HomeFactsLine(if (next) facts.filter { it.kind != InfoKind.NEXT } else facts, size, Modifier.offset(y = (pitch - lh).dp).fillMaxWidth().height(lh.dp))
                            if (next) HomeFactsLine(facts.filter { it.kind == InfoKind.NEXT }, size, Modifier.offset(y = (2 * (pitch - lh)).dp).fillMaxWidth().height(lh.dp))
                        }
                        NxTopIcons(titleIcons, Modifier.align(Alignment.TopEnd).height(MENU_TAB_ROW_HEIGHT).padding(end = inset.end - TOP_ICON_INSET))
                    }
                } else if (chrome.listTitle) {
                    val titleStyle = menuTitleStyle()
                    MenuTitleRow(
                        title, overArt = style == MenuStyle.BACKDROP, textStart = titleStart,
                        // Where a B morph back to a Minimal tab starts (while this page shows); hidden while the name flies in.
                        textModifier = Modifier.onGloballyPositioned {
                            titleText[0] = it
                            if (minimalPage?.live?.value == true) morphPlace(it).let { (at, s) -> minimalPage.morph.title = MorphText(title, at, it.size.width.toFloat(), s, titleStyle) }
                        }.graphicsLayer { alpha = if (flown.value < 1f) 0f else 1f },
                        trailing = { NxTopIcons(titleIcons) },
                    )
                }
                // Hidden: a List runs up into its room; a Grid, Carousel or Backdrop (row or stack) takes it into its box,
                // sized as with the title, centred in the taller box. A Backdrop's picture stays put. With the page's icons
                // up there (Button hints hidden too, or touch), every layout keeps the row's room clear instead ([topIconsRoom]).
                // A List runs up under the icons too, its rows level with them stopping short of them ([LocalNxListTopCut]).
                val top = topIconsRoom(chrome.listTitle, titleIcons.isNotEmpty())
                if (top.pad > 0.dp && style != MenuStyle.LIST) Spacer(Modifier.height(top.pad))
                val titleRoom = top.titleRoom
                // A Grid, Carousel or Backdrop body in the scaffold's slot: no info band (a Grid's game info is in its tile, a
                // row's under its centre item), so it runs from the title down to the hint bar, or with Button hints hidden
                // down to the bottom: nothing goes in the bar's row, so no room is kept for it. The scaffold draws no list
                // here, so its index is unused.
                val slot = @Composable { body: @Composable (Modifier) -> Unit ->
                    NxScreenScaffold(
                        rows = emptyList(), selectedIndex = 0,
                        hints = hints.value, onSelect = onSelect, onOpen = onOpen, modifier = Modifier.weight(1f), onHint = onHint, grid = body,
                        hideHints = chrome.emptyBarRoom, keepBarRoom = !chrome.emptyBarRoom,
                    )
                }
                when (style) {
                    MenuStyle.GRID -> {
                        val tiles = remember(dataKey) { gridTiles() }
                        slot { m ->
                            NxTileGrid(
                                tiles, selectedIndex, onSelect = onSelect, onOpen = onOpen, onLongPress = onLongPress,
                                onColumns = onGridLayout, tileInfo = if (topStats) NoTileInfo else tileInfo,
                                modifier = m, layoutKey = layoutKey, games = true,   // on the gutter, in line with the title
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
                            onPageRows = onPageRows,
                        )
                    }
                    // List: the rows fade where more scrolls in (no band, no arrows), between a main menu List's gaps.
                    MenuStyle.LIST -> {
                        val rows = remember(dataKey) { listRows() }
                        // The selected game's info shows under the title ([topStats]); with Page title Off, in the hint bar's
                        // row when there is no bar (hints hidden, or touch only), only that row shaded. Extra info off leaves
                        // nothing for it, so its room goes too.
                        val barHidden = gameListInfoInBarRow(chrome.emptyBarRoom, touchOnly())
                        val inRow = barHidden && chrome.extraInfo && !topStats
                        GameInfoPlacement(if (inRow) info else NoGameInfo, Modifier.weight(1f), textStart = textStart, ground = if (inRow) BandGround.SHADE_WITH_INFO else BandGround.BAR, shade = false) { scaffoldModifier, infoBand ->
                            // The band's room goes to the rows; only its line in the bar's row ([NxHintsBand.barInfo]) stays.
                            val band = NxHintsBand(reserve = 0.dp, overlap = 0.dp, barInfo = infoBand.barInfo) {}
                            // The rows as the main menu's List draws them ([MainListBody]): the pill on the gutter, whole-row
                            // fades, the selection free until the third row from an edge; under the page's icons with no title.
                            NxScreenScaffold(
                                rows = emptyList(), selectedIndex = 0, hints = hints.value,
                                onSelect = onSelect, onOpen = onOpen, modifier = scaffoldModifier, onHint = onHint,
                                band = band, hideHints = barHidden, hintShade = inRow, keepBarRoom = true,   // hidden hints keep the bar's row, empty unless the info is in it
                                grid = { m ->
                                    BoxWithConstraints(m) {
                                        MainListBody(
                                            rows, selectedIndex, if (iconsUp) MENU_TAB_ROW_HEIGHT else 0.dp, onSelect, onOpen, onLongPress,
                                            endRoom = if (artBox) listArtLabelEnd(maxWidth.value, inset.end.value).dp else 0.dp, onPageRows = onPageRows,
                                            // Half a row's room under the stats line (shown under the title), as Home keeps under its stats.
                                            gapRows = if (topStats) HOME_LIST_GAP_ROWS else 0f,
                                        )
                                    }
                                },
                            )
                        }
                    }
                }
            }
            // Page title Off, the icons the title row would hold sit at the page's top right over the body
            // (the title room above the list is [MENU_TAB_ROW_HEIGHT] tall), ending on the page inset like the title row's.
            if (iconsUp) NxTopIcons(
                titleIcons,
                Modifier.align(Alignment.TopEnd).height(MENU_TAB_ROW_HEIGHT)
                    .padding(end = inset.end - TOP_ICON_INSET).nxListTopCut(topCut),
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
internal fun Modifier.pullUp(by: Dp): Modifier = layout { m, c ->
    val p = m.measure(c)
    val up = by.roundToPx().coerceAtMost(p.height)
    layout(p.width, p.height - up) { p.place(0, -up) }
}

/**
 * Whether a game list in [style] shows the selected game's info on a line under its title ([titleShown]; Extra info
 * [extraInfo] on): List, Minimal and Grid, hint bar shown or not. Carousel and Backdrop show theirs in their captions.
 */
internal fun gameListTopStats(style: MenuStyle, titleShown: Boolean, extraInfo: Boolean): Boolean =
    titleShown && extraInfo && when (style) {
        MenuStyle.LIST, MenuStyle.MINIMAL, MenuStyle.GRID -> true
        MenuStyle.CAROUSEL, MenuStyle.BACKDROP -> false
    }

/** A Grid's tiles with the info on the line under the title ([gameListTopStats]): none in the selected tile. */
private val NoTileInfo: (Int) -> List<InfoLine> = { emptyList() }
