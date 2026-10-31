package com.nxredux.mobile.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.nxredux.mobile.shared.res.Res
import com.nxredux.mobile.shared.res.background
import com.nxredux.mobile.shared.res.nx_logo
import com.nxredux.mobile.ui.nx.LauncherChrome
import com.nxredux.mobile.ui.nx.ListSelection
import com.nxredux.mobile.ui.nx.LocalLauncherChrome
import com.nxredux.mobile.ui.nx.LocalNxColors
import com.nxredux.mobile.ui.nx.nxScreenInsets
import com.nxredux.mobile.ui.nx.LocalNxTapOpens
import com.nxredux.mobile.ui.nx.NxLoadingOverlay
import com.nxredux.mobile.ui.nx.LocalPortrait
import com.nxredux.mobile.ui.nx.LocalWindowSize
import com.nxredux.mobile.ui.nx.NxScreenBackground
import com.nxredux.mobile.ui.nx.NxTheme
import com.nxredux.mobile.ui.nx.footerModeFor
import com.nxredux.mobile.ui.nx.rememberControllerConnected
import com.nxredux.mobile.ui.nx.rememberHasTouchscreen
import org.jetbrains.compose.resources.painterResource

/**
 * The top screen while the main menu is on the second display ([AppState.dualMenu]): the same [AppState] as [App], drawn
 * by [DualMenu.top]. A [TopContent.Preview] of the highlighted console's or collection's games (unfocused: no hints, a
 * tap opens the list there, a swipe only scrolls); the [TopContent.GameList] itself while it has focus, with its hints,
 * every overlay and the Netplay sheet (the modals follow the focused screen); the highlighted Apps tile's drawer as a
 * [TopContent.AppsPreview] (a tap opens it there on that app); a [TopContent.Tool] opened from the menu, with the focus,
 * its loading, dialogs and notices; else the [TopContent.Backdrop]. It has the
 * theme and the launcher locals [App] has ([MenuLocals]), from the same settings; the image loader is App's (process-wide).
 * There is no pad here, so the footer follows a connected controller alone.
 */
@Composable
fun MenuTopScreen(state: AppState) {
    val screen by state.screen.collectAsState()
    val secondList by state.secondList.collectAsState()
    val drawerPreview by state.drawerPreview.collectAsState()
    val toolOnTop by state.toolOnTop.collectAsState()
    val dual by state.dualMenu.collectAsState()
    val selection = state.selection.collectAsState()
    val resumeInfo = state.resumeInfo.collectAsState()
    val gameInfo by state.gameInfo.collectAsState()
    val netplayPage = state.netplay?.page?.collectAsState()
    val look = rememberMenuLook(state, screen)
    val controllerConnected = rememberControllerConnected()
    val touchscreen = rememberHasTouchscreen()
    NxTheme(colors = look.colors) {
        MenuLocals(state, look, footerModeFor(controllerConnected, padVisible = false), touchscreen) {
            BoxWithConstraints(
                Modifier.fillMaxSize().background(LocalNxColors.current.background)
                    .windowInsetsPadding(nxScreenInsets()).clipToBounds(),
            ) {
                // No HandheldShell here: the screen's own shape and size, as the shell gives App's pages.
                CompositionLocalProvider(LocalPortrait provides (maxHeight > maxWidth), LocalWindowSize provides DpSize(maxWidth, maxHeight)) {
                    when (val top = menuTop(dual, screen, secondList, drawerPreview, toolOnTop)) {
                        is TopContent.Preview -> PreviewList(state, top.list, gameInfo)
                        is TopContent.AppsPreview -> AppsPreview(state, top.apps)
                        TopContent.Tool -> Box(Modifier.fillMaxSize()) {
                            // Laid out here: this window decides whether Settings splits (App reports the bottom's otherwise).
                            val split = settingsSplit(LocalWindowSize.current)
                            LaunchedEffect(split) { state.onSettingsSplit(split) }
                            val live = remember { mutableStateOf(true) }
                            val chooser by state.chooser.collectAsState()
                            val error by state.error.collectAsState()
                            val notice by state.notice.collectAsState()
                            val loading by state.loading.collectAsState()
                            val loadingMessage by state.loadingMessage.collectAsState()
                            PageContent(state, screen, selection, resumeInfo, gameInfo, live, onTop = true)
                            if (loading) NxLoadingOverlay(loadingMessage)
                            PageOverlays(state, chooser, error, notice)
                        }
                        TopContent.GameList -> (screen as? Screen.Games)?.let { s ->
                            val live = remember { mutableStateOf(true) }
                            val launcherInfo = launcherGameInfo(gameInfo, LocalLauncherChrome.current.extraInfo)
                            val now = rememberNow(gameInfo)
                            PageBackground(state, s, state.styleOf(s), selection, live)
                            GamesPage(state, s, selection, resumeInfo, launcherInfo, now)
                            netplayPage?.value?.let { p -> NetplayOverlay(state, p) }
                        }
                        TopContent.Backdrop -> Backdrop()
                    }
                }
            }
        }
    }
}

/** [DualMenu.top] while the menu is on the second display ([dual]); else the backdrop, never a list. */
internal fun menuTop(dual: Boolean, screen: Screen, preview: SecondList?, apps: List<ToolAppRow>? = null, tool: Boolean = false): TopContent =
    if (dual) DualMenu.top(screen, preview, apps, tool) else TopContent.Backdrop

/**
 * The highlighted Apps tile's drawer ([apps]), unfocused: no hints and no highlight until a swipe-free tap, which opens
 * the drawer on the top screen on that app ([AppState.openDrawerPreviewApp]).
 */
@Composable
private fun AppsPreview(state: AppState, apps: List<ToolAppRow>) {
    val page = remember(apps) { Screen.ToolApps(apps, AppPick.TOOLS) }
    val selection = remember(apps) { mutableStateOf(ListSelection(-1, apps.size)) }
    val live = remember { mutableStateOf(true) }
    PageBackground(state, page, state.styleOf(page), selection, live)
    CompositionLocalProvider(LocalNxTapOpens provides { true }) {
        ToolAppsScreen(page, -1, onSelect = {}, onOpen = state::openDrawerPreviewApp, onColumns = {}, hints = false)
    }
}

/**
 * The highlighted console's or collection's games ([list]) in the game list layout (Settings → Layouts), over a game list's
 * background. Unfocused: no hint bar ([previewChrome]) and no icons. Its own highlight starts on [SecondList.index]; a
 * swipe that settles on another game moves only that (the focus stays on the menu), and a tap on a game opens the list
 * on the top screen on it ([AppState.openSecondListGame]).
 */
@Composable
private fun PreviewList(state: AppState, list: SecondList, allInfo: Map<String, GameInfo>) {
    val page = remember(list) { Screen.Games(list.source, list.entries) }
    val selection = remember(list) { mutableStateOf(ListSelection(list.index, list.entries.size)) }
    val live = remember { mutableStateOf(true) }
    val chrome = previewChrome(LocalLauncherChrome.current)
    val gameInfo = launcherGameInfo(allInfo, chrome.extraInfo)
    val now = rememberNow(allInfo)
    val style = state.styleOf(page)
    PageBackground(state, page, style, selection, live)
    CompositionLocalProvider(LocalLauncherChrome provides chrome) {
        val current = { list.entries.getOrNull(selection.value.index) }
        GameListScreen(
            listTitle(list.source), list.entries, { selection.value.index },
            onSelect = { i -> selection.value = selection.value.copy(index = i) },
            onOpen = state::openSecondListGame,
            info = { gameInfoFor(gameInfo, now, current()?.game, full = true) },
            style = style, orientation = state.orientationOf(page), alignment = state.settings.gameListVerticalAlignment,
            tileInfo = remember(list.entries, gameInfo, now) { entryTileInfo(list.entries, gameInfo, now) }, layoutKey = list.source,
            artBox = state.settings.gameListArt.boxed,
            backdropArt = state.settings.backdropArt,
            tapOpens = { true },
        )
    }
}

/**
 * The preview's chrome: [chrome] with Button hints off, so its hint bar is hidden (the list is not focused), its room kept
 * as Button hints Off keeps it; a touch-only screen ([LauncherChrome.noBar]) has no bar to hide.
 */
internal fun previewChrome(chrome: LauncherChrome): LauncherChrome = if (chrome.noBar) chrome else chrome.copy(buttonHints = false)

/** Every other page: the app backdrop, dimmed, with the NX logo in the middle. */
@Composable
private fun Backdrop() {
    Box(Modifier.fillMaxSize()) {
        NxScreenBackground(url = null, fallback = Res.drawable.background, fade = false, modifier = Modifier.fillMaxSize(), centred = true)
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = BACKDROP_DIM)))   // role: the other pages' app backdrop dim, lighter than a game's scrimBackdrop
        Image(
            painterResource(Res.drawable.nx_logo), contentDescription = null, colorFilter = ColorFilter.tint(LocalNxColors.current.listText),
            modifier = Modifier.align(Alignment.Center).size(width = 96.dp, height = 92.dp),   // the mark's 80:77
        )
    }
}

private const val BACKDROP_DIM = 0.5f
