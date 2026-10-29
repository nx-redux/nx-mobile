package com.nxredux.mobile.ui

import com.nxredux.mobile.ui.nx.LocalNxHintBarHidden
import com.nxredux.mobile.ui.nx.LocalNxHintBarStart
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.compose.AsyncImagePainter
import coil3.compose.setSingletonImageLoaderFactory
import coil3.request.addLastModifiedToFileCacheKey
import com.nxredux.mobile.update.AppRelease
import com.nxredux.mobile.ui.nx.LaunchOverlay
import com.nxredux.mobile.ui.nx.LaunchTargets
import com.nxredux.mobile.ui.nx.LocalLaunchTargets
import com.nxredux.mobile.ui.nx.LocalMinimalPage
import com.nxredux.mobile.ui.nx.toLaunchName
import com.nxredux.mobile.ui.nx.MinimalMorph
import com.nxredux.mobile.ui.nx.MinimalPage
import com.nxredux.mobile.ui.nx.abstractArtFetcher
import com.nxredux.mobile.ui.nx.fileRefMapper
import com.nxredux.mobile.ui.nx.nxColorsFor
import com.nxredux.mobile.ui.nx.abstractArtKeyer
import androidx.compose.animation.AnimatedContent
import com.nxredux.mobile.ui.nx.LocalPageSlide
import com.nxredux.mobile.ui.nx.LocalTabSwipe
import com.nxredux.mobile.ui.nx.PageSlide
import com.nxredux.mobile.ui.nx.TabSwipe
import kotlinx.coroutines.launch
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.zIndex
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.key
import com.nxredux.mobile.ui.nx.settleTo
import com.nxredux.mobile.ui.nx.GatedLaunchTargets
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.structuralEqualityPolicy
import androidx.compose.ui.Alignment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.graphics.ColorMatrix
import com.nxredux.mobile.ui.nx.padColorMatrix
import com.nxredux.mobile.ui.nx.padPainter
import com.nxredux.mobile.ui.nx.LIST_PAD_BRIGHTNESS
import com.nxredux.mobile.ui.nx.LIST_PAD_ALPHA
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nxredux.mobile.library.path
import com.nxredux.mobile.settings.ConsoleArt
import com.nxredux.mobile.settings.GameListArt
import com.nxredux.mobile.settings.MenuCategory
import com.nxredux.mobile.settings.MenuStyle
import com.nxredux.mobile.settings.PadLayouts
import com.nxredux.mobile.settings.PadShape
import com.nxredux.mobile.settings.PadSlot
import com.nxredux.mobile.storage.ResumeInfo
import com.nxredux.mobile.ui.nx.ListSelection
import com.nxredux.mobile.ui.nx.HandheldShell
import com.nxredux.mobile.ui.nx.HomeFrame
import com.nxredux.mobile.ui.nx.LocalFooterMode
import com.nxredux.mobile.ui.nx.LocalLauncherChrome
import com.nxredux.mobile.ui.nx.launcherChromeFor
import com.nxredux.mobile.ui.nx.LocalNxColors
import com.nxredux.mobile.ui.nx.restingSystemBars
import com.nxredux.mobile.ui.nx.LocalAppIcons
import com.nxredux.mobile.ui.nx.LocalPadBelow
import com.nxredux.mobile.ui.nx.LocalPortrait
import com.nxredux.mobile.ui.nx.LocalWindowSize
import com.nxredux.mobile.ui.nx.NxDefaults
import com.nxredux.mobile.ui.nx.NxBackdropPicture
import com.nxredux.mobile.ui.nx.backdropPictureTarget
import com.nxredux.mobile.ui.nx.LocalNxOptionText
import com.nxredux.mobile.ui.nx.NxHelpText
import com.nxredux.mobile.ui.nx.NxSettingInfoPopup
import com.nxredux.mobile.ui.nx.NxSettingPicker
import com.nxredux.mobile.ui.nx.NxDialog
import com.nxredux.mobile.ui.nx.NxLoadingOverlay
import com.nxredux.mobile.ui.nx.NxScreenBackground
import com.nxredux.mobile.ui.nx.NxTheme
import com.nxredux.mobile.ui.nx.NxColors
import com.nxredux.mobile.ui.nx.FooterMode
import com.nxredux.mobile.ui.nx.GameDetailsPane
import com.nxredux.mobile.ui.nx.NxButton
import com.nxredux.mobile.ui.nx.NxTextPrompt
import com.nxredux.mobile.ui.nx.PadEvent
import com.nxredux.mobile.ui.nx.PadThemeProvider
import com.nxredux.mobile.ui.nx.PadVibrationProvider
import com.nxredux.mobile.ui.nx.VirtualPad
import com.nxredux.mobile.ui.nx.VirtualPadLeft
import com.nxredux.mobile.ui.nx.VirtualPadRight
import com.nxredux.mobile.ui.nx.footerModeFor
import com.nxredux.mobile.ui.nx.LocalHasTouchscreen
import com.nxredux.mobile.ui.nx.rememberHasTouchscreen
import com.nxredux.mobile.ui.nx.rememberControllerConnected
import com.nxredux.mobile.ui.nx.rememberSeparateController
import com.nxredux.mobile.shared.res.Res
import com.nxredux.mobile.shared.res.background
import org.jetbrains.compose.resources.painterResource
import kotlinx.coroutines.delay

/**
 * The image loader every picture uses, the game screen's too: the generated abstract art's fetcher ([abstractArtFetcher])
 * and key, and local files (ROM art, save-state screenshots) read by path: its mapper runs before Coil's String mapper,
 * which would parse '#' and '?'. A file's cache key holds its modified time (Coil 3's default leaves it out): the mapper
 * drops a ref's `?v=` version, so a screenshot rewritten under the same name would otherwise show from the memory cache.
 */
internal fun nxImageLoader(context: PlatformContext): ImageLoader =
    ImageLoader.Builder(context).components { fileRefMapper()?.let { add(it) }; add(abstractArtFetcher()); abstractArtKeyer()?.let { add(it) } }
        .addLastModifiedToFileCacheKey(true)
        .build()

/** Root composable. The host creates AppState once (so a folder pick can reach setHome/addExtra) and passes it in. */
@Composable
fun App(state: AppState, onPadEvent: (PadEvent) -> Unit = {}) {
    setSingletonImageLoaderFactory(::nxImageLoader)   // a no-op when the platform installed it first (Android: at process start)
    val screen by state.screen.collectAsState()
    val error by state.error.collectAsState()
    val androidGamesAsk by state.androidGamesAsk.collectAsState()
    val updatePopup by state.updatePopup.collectAsState()
    val chooser by state.chooser.collectAsState()   // touch only: a value row's chooser
    // Collected but not read here: each page reads them only where the selection shows ([PageContent]), so a selection
    // change recomposes the selected tile, the caption, the background and the hints, not the whole page.
    val selection = state.selection.collectAsState()
    val resumeInfo = state.resumeInfo.collectAsState()
    val notice by state.notice.collectAsState()
    val loading by state.loading.collectAsState()
    val loadingMessage by state.loadingMessage.collectAsState()
    val nav by state.nav.collectAsState()
    val gameInfo by state.gameInfo.collectAsState()
    val netplayPage = state.netplay?.page?.collectAsState()   // the Netplay sheet's page (null: closed); no controller, no sheet
    val dualMenu by state.dualMenu.collectAsState()   // the menu is on the second display: the game list's overlays go to the top screen
    val toolOnTop by state.toolOnTop.collectAsState()   // a tool has the focus: with the menu on the second display, it and its overlays are on the top screen
    LaunchedEffect(Unit) { state.loadOnce() }   // App may be composed again on the other display (the main menu there)
    // Settings are plain properties: every Pad theme change republishes the screen, so re-read it when the screen changes.
    val padTheme = remember(screen) { state.settings.padTheme }
    val padButtons = remember(screen) { state.settings.padButtons }   // Appearance → Pad buttons (Black's set), likewise
    val padVibration = remember(screen) { state.settings.padVibration }   // Controls → Pad vibration, likewise
    // Controls → Menu pad, likewise; hidden while first setup and onboarding have not asked yet, previewed on its step.
    val menuPad = remember(screen, selection.value.index) { state.menuPadShown() }
    // Controls → Pad layout: the main menu's pads draw the Shared layouts, live (a console's layout shows in its editor only).
    val padLayouts by state.padLayouts.collectAsState()
    val padScreen by state.padScreen.collectAsState()   // the screen this is on (a Fold's cover or inner): its own layouts
    val menuPortraitLayout = remember(padLayouts, padScreen) { PadLayouts.resolve(padLayouts, null, PadSlot(PadShape.PORTRAIT, padScreen)) }
    val look = rememberMenuLook(state, screen)   // the accents and the launcher chrome's settings, likewise
    val controllerConnected = rememberControllerConnected()   // once, above the shell: survives rotation and pad/no-pad flips
    LaunchedEffect(controllerConnected) { state.onControllerConnected(controllerConnected) }   // a Shortcuts bind row's line
    val touchscreen = rememberHasTouchscreen()   // for a game list's Recent, Search and Layout icons
    // A launch: the page showing reports its selected name ([LocalLaunchTargets]); the overlay spreads from it, and the
    // game carries it (in the accent it ends on) to draw again on the same pixels.
    val launchSequence by state.launchSequence.collectAsState()
    val launchTargets = remember { LaunchTargets() }
    val launchAccent = rememberUpdatedState(look.colors.accent)
    DisposableEffect(state) {
        val provider = { launchTargets.current?.toLaunchName(launchAccent.value) }
        state.launchNameProvider = provider
        onDispose { if (state.launchNameProvider === provider) state.launchNameProvider = null }
    }
    // Bars and cutout only, never the IME: the shell must not shrink for the keyboard (NxModal pads for it where needed).
    Box(Modifier.fillMaxSize().background(NxDefaults.colors.background).windowInsetsPadding(restingSystemBars().union(WindowInsets.displayCutout))) {
        HandheldShell(
            pad = { m -> NxTheme { PadVibrationProvider(padVibration) { PadThemeProvider(padTheme, padButtons) { VirtualPad(onPadEvent, m, layout = menuPortraitLayout) } } } },
            controllerConnected = controllerConnected,
            showPad = menuPad,
            // Flex mode on a wide window: the two clusters below the hinge (the main app has no analog stick use).
            clusters = { l, r ->
                NxTheme {
                    PadVibrationProvider(padVibration) {
                        VirtualPadLeft(onPadEvent, { _, _ -> }, l)
                        VirtualPadRight(onPadEvent, r)
                    }
                }
            },
            screen = { m ->
                NxTheme(colors = look.colors) {
                    val footer = footerModeFor(controllerConnected, LocalPadBelow.current)
                    // AppState needs it too: the tab row's focus follows whether Page title's Hide is in effect.
                    LaunchedEffect(footer) { state.onFooterMode(footer) }
                    // Onboarding's customise pages follow the device: the pad is a choice in portrait without a controller
                    // (a clip-on counts as none).
                    val portrait = LocalPortrait.current
                    val separate = rememberSeparateController()
                    LaunchedEffect(portrait, separate) { state.onPadContext(portrait, separate) }
                    MenuLocals(state, look, footer, touchscreen) {
                        CompositionLocalProvider(LocalLaunchTargets provides launchTargets) {
                        Box(m) {
                            // A tool on the top screen ([MenuTopScreen]): this one keeps the menu, still.
                            val toolAbove = DualMenu.bottom(screen, dualMenu, toolOnTop) == BottomContent.Menu
                            // The window decides whether Settings splits; AppState needs it for RIGHT on a category. A tool on
                            // the top screen is laid out there, which reports its own.
                            val split = settingsSplit(LocalWindowSize.current)
                            LaunchedEffect(split, toolAbove) { if (!toolAbove) state.onSettingsSplit(split) }
                            // Into, inside and out of a tool on the top screen the pages here do not slide: it is the same menu
                            // throughout, and the tool's page must not show here on its way out.
                            val wasToolAbove = remember { booleanArrayOf(false) }
                            val still = toolAbove || wasToolAbove[0]
                            SideEffect { wasToolAbove[0] = toolAbove }
                            // Only a [NavStep] changes the key: every other screen change (a refresh, Settings, a menu) swaps the page in place.
                            val shown = remember { HashMap<Int, Screen>() }   // each page's screen as last shown, so the one sliding out keeps its own
                            val minimalMorph = remember { MinimalMorph() }   // where a Minimal morph to the next page starts
                            val tabSwipe = remember { TabSwipe() }   // a main menu page dragged between tabs ([tabSwipe])
                            AnimatedContent(
                                targetState = nav.seq, transitionSpec = { if (still) EnterTransition.None togetherWith ExitTransition.None else pageTransition(nav.motion) },
                                modifier = Modifier.fillMaxSize().clipToBounds(), label = "page",
                            ) { seq ->
                                val live = seq == nav.seq
                                val page = if (live) screen.also { shown.keys.removeAll { k -> k < seq - 2 }; shown[seq] = it } else shown[seq]
                                // The page's selection and resume info: the live ones while it shows, then the last it showed.
                                val isLive = rememberUpdatedState(live)
                                val pageSelection = remember { liveOrLast(isLive, selection) }
                                val pageResume = remember { liveOrLast(isLive, resumeInfo) }
                                // How this page came in, for a Minimal morph ([NavMotion.MINIMAL_FORWARD], [NavMotion.MINIMAL_BACK]).
                                val minimalPage = remember { MinimalPage(minimalMorph, isLive, nav.motion) }
                                CompositionLocalProvider(LocalMinimalPage provides minimalPage, LocalTabSwipe provides tabSwipe) {
                                    // The main menu's tabs side by side for a sideways swipe ([TabPager]); the info is the current map for both pages.
                                    if (live && page is Screen.Systems && tabPaged(page)) TabPager(state, page, pageSelection, pageResume, gameInfo, isLive, tabSwipe, launchTargets)
                                    else page?.let { PageContent(state, it, pageSelection, pageResume, gameInfo, isLive) }
                                }
                                // The page sliding out takes no taps: they would act on the page sliding in.
                                if (!live) Box(Modifier.fillMaxSize().pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } })
                            }
                            // The Netplay sheet over the game list it was opened on; it takes every press while it shows. With the
                            // menu on the second display it goes with the game list, to the top screen ([MenuTopScreen]).
                            val bottom = DualMenu.bottom(screen, dualMenu, toolOnTop)
                            if (bottom == BottomContent.Normal) netplayPage?.value?.let { p -> NetplayOverlay(state, p) }
                            // Over the whole screen (tabs, stats, icons, content), before a tool on the top screen returns.
                            LaunchOverlay(launchSequence, launchTargets, reducedMotion = state.reducedMotion)
                            if (bottom == BottomContent.Menu) return@Box   // a tool on the top screen: its loading, dialogs and notices are there ([MenuTopScreen])
                            if (loading) NxLoadingOverlay(loadingMessage)
                            // An installed library's Android games question over the menu (first setup asks it as a step). A one-time
                            // answer: a scrim tap does not give it, and AppState ignores answers in its first moments.
                            androidGamesAsk?.let { ask ->
                                NxDialog(ask.title, ask.text, onConfirm = { state.answerAndroidGames(true) }, confirmLabel = "Yes",
                                    onCancel = { state.answerAndroidGames(false) }, cancelLabel = "Not now", scrimDismisses = false)
                            }
                            // A newer release, asked once a day at most over the menu; Not now silences that version.
                            updatePopup?.let { r ->
                                NxDialog("Update available", updateText(r), onConfirm = { state.answerUpdate(true) }, confirmLabel = "Update",
                                    onCancel = { state.answerUpdate(false) }, cancelLabel = "Not now", scrimDismisses = false, scrollSteps = state.updateScroll)
                            }
                            PageOverlays(state, chooser, error, notice)
                        }
                        }
                    }
                }
            },
        )
    }
}

/**
 * The settings the launcher screens' look reads: Appearance → Accent and Secondary accent ([colors]; Auto follows the
 * accent), Layouts → Page title, Button hints, Extra info, Home stats and the Netplay hint (the chrome,
 * [launcherChromeFor]), and Appearance → Text size ([optionText]).
 */
internal data class MenuLook(
    val colors: NxColors, val pageTitle: Boolean, val buttonHints: Boolean, val extraInfo: Boolean, val homeStats: Boolean,
    val netplayHint: Boolean, val optionText: Float,
)

/** [MenuLook] from the settings. They are plain properties: every change republishes the screen, so it is re-read on each [screen]. */
@Composable
internal fun rememberMenuLook(state: AppState, screen: Screen): MenuLook {
    val accents = remember(screen) { state.settings.accent to state.settings.secondaryAccent }
    val colors = remember(accents) { nxColorsFor(accents.first, accents.second) }
    return remember(screen, colors) {
        val st = state.settings
        MenuLook(
            colors, st.menuPageTitle, st.menuButtonHints, st.menuExtraInfo, st.menuHomeStats,
            st.showNetplayHint, st.optionTextSize.ofListLabel,
        )
    }
}

/**
 * The CompositionLocals every launcher screen reads, for the main window's [App] and the second display's
 * [MenuTopScreen] alike: the [footer] mode, the launcher chrome ([launcherChromeFor] from [look] and [footer]), the
 * [touchscreen], the app icons and the option text size.
 */
@Composable
internal fun MenuLocals(state: AppState, look: MenuLook, footer: FooterMode, touchscreen: Boolean, content: @Composable () -> Unit) {
    val chrome = launcherChromeFor(look.pageTitle, look.buttonHints, footer, look.extraInfo, look.netplayHint, look.homeStats)
    CompositionLocalProvider(
        LocalFooterMode provides footer, LocalLauncherChrome provides chrome, LocalHasTouchscreen provides touchscreen,
        LocalAppIcons provides state.appIcons, LocalNxOptionText provides look.optionText,
        content = content,
    )
}

/** The Netplay sheet over the game list it was opened on ([page]); it takes every press while it shows. */
@Composable
internal fun NetplayOverlay(state: AppState, page: NetplayPage) =
    NetplaySheet(page, onButton = state::onButton, onSelect = { i -> state.netplay?.select(i) })

/**
 * The wall clock for the "Today" / "2 days ago" labels: read once, then again when one of [gameInfo]'s labels turns
 * ([nextWhenChange]), so an idle menu does not keep "Today" a day on. It checks each minute (a coroutine wake, no
 * recomposition) rather than sleeping the whole span, since a suspended device's delay would lag the wall clock. Only
 * while the menu is started: a stopped one (under a game) does not wake; started again, a label that turned meanwhile
 * turns at once.
 */
@Composable
internal fun rememberNow(gameInfo: Map<String, GameInfo>): Long {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val now by produceState(nowMs(), gameInfo, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                val next = nextWhenChange(value, gameInfo.values.mapNotNull { it.lastPlayedAt }) ?: break
                while (nowMs() < next) delay((next - nowMs()).coerceIn(1L, 60_000L))
                value = nowMs()
            }
        }
    }
    return now
}

/**
 * [source] while [live], then the last value it gave: a page sliding out keeps its own selection (and resume info), not
 * the new list's. The value is remembered as it is read, and a page reads it wherever it shows it.
 */
internal fun <T> liveOrLast(live: State<Boolean>, source: State<T>): State<T> {
    var last = source.value
    return derivedStateOf(structuralEqualityPolicy()) { if (live.value) source.value.also { last = it } else last }
}

/**
 * NextUI's page slides (nextui.c, GFX_animateSlidePages): 250 ms, ease-out cubic, both pages moving together. A folder
 * pushes left on A and right on B; the game switcher pushes up on open and down on exit.
 */
private const val NX_PAGE_SLIDE_MS = 250

private fun pageTransition(motion: NavMotion): ContentTransform {
    val spec = tween<IntOffset>(NX_PAGE_SLIDE_MS, easing = EaseOutCubic)
    return when (motion) {
        NavMotion.FORWARD -> slideInHorizontally(spec) { it } togetherWith slideOutHorizontally(spec) { -it }
        NavMotion.BACK -> slideInHorizontally(spec) { -it } togetherWith slideOutHorizontally(spec) { it }
        NavMotion.SWITCHER_OPEN -> slideInVertically(spec) { it } togetherWith slideOutVertically(spec) { -it }
        NavMotion.SWITCHER_CLOSE -> slideInVertically(spec) { -it } togetherWith slideOutVertically(spec) { it }
        // A Minimal morph: the pages swap at once, the new one drawing the name's flight ([MinimalFlight]).
        NavMotion.NONE, NavMotion.MINIMAL_FORWARD, NavMotion.MINIMAL_BACK -> EnterTransition.None togetherWith ExitTransition.None
    }
}

/** A List's game picture (a game list's, List Home's) under a black of this alpha ([ListArtBackground]), so the names read. */
private const val LIST_ART_DIM = 0.68f   // role: a List's game art dim: the picture shows at 32%

/** Whether [s] pages between its tabs ([TabPager]): a main menu of two tabs or more, no tab pushed over it. */
private fun tabPaged(s: Screen.Systems): Boolean = !s.pushed && s.tabs.size > 1

/**
 * The main menu's tabs side by side: the tab showing ([screen]'s) and the ones either side of it, each tab's page its
 * own composition (keyed by its tab), so the page a swipe uncovers is the page that stays when it commits: nothing is
 * built at the start of a drag or at its end. The pages shift together by [swipe]'s drag, the next tab's just right of
 * the showing one and the previous one's just left; a page beside is drawn only while it is in view, takes no taps and
 * reports nothing ([GatedLaunchTargets]; the others report only for the screen showing). Its fixed parts ([pageFixed])
 * show only on the tab showing. A committed swipe ([TabSwipe.pending]) lands here when the new tab shows: the pages
 * shift by a page so nothing jumps, then settle to rest.
 */
@Composable
private fun TabPager(
    state: AppState, screen: Screen.Systems, selection: State<ListSelection>, resume: State<ResumeInfo>,
    gameInfo: Map<String, GameInfo>, live: State<Boolean>, swipe: TabSwipe, targets: LaunchTargets,
) {
    val n = screen.tabs.size
    val scope = rememberCoroutineScope()
    val lastTab = remember { intArrayOf(screen.tab) }
    SideEffect {
        if (screen.tab != lastTab[0]) {
            lastTab[0] = screen.tab
            val step = swipe.pending
            swipe.pending = 0
            // The new tab was the page beside: it carries on from there (an L1 / R1 or a tap: no swipe, no slide).
            if (step != 0) { swipe.drag += step * swipe.width; scope.launch { swipe.settleTo(0f) } }
            else if (swipe.drag != 0f) scope.launch { swipe.settle.stop(); swipe.drag = 0f }
        }
    }
    val notLive = remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize().onSizeChanged { swipe.width = it.width.toFloat() }) {
        val shown = listOf((screen.tab - 1).mod(n), (screen.tab + 1).mod(n), screen.tab).distinct()
        for (i in shown) key(screen.tabs[i].category) {
            val current = i == screen.tab
            val isCurrent = rememberUpdatedState(current)
            // Where it sits: the showing page at 0, the next one a page right, the previous a page left (with two tabs,
            // the other is both: on the side the drag uncovers).
            val side = rememberUpdatedState(if (current) 0 else if (i == (screen.tab + 1).mod(n)) 1 else -1)
            val both = rememberUpdatedState(!current && n == 2)
            val slide = remember(swipe) {
                PageSlide(
                    shift = { w -> (if (both.value) (if (swipe.drag > 0f) -1 else 1) else side.value) * w + swipe.drag },
                    fixedShown = { isCurrent.value },
                    width = { swipe.width },
                )
            }
            val gated = remember(targets) { GatedLaunchTargets(targets) { isCurrent.value } }
            // A page beside: as AppState would show that tab, on its remembered row.
            val beside = if (current) null else remember(screen, i) { state.tabPage(i) }
            val pageSelection = if (beside == null) selection else remember(beside) { mutableStateOf(beside.second) }
            CompositionLocalProvider(LocalPageSlide provides slide, LocalLaunchTargets provides gated) {
                Box(
                    Modifier.fillMaxSize().zIndex(if (current) 1f else 0f)
                        .graphicsLayer { translationX = slide.shift(size.width) }
                        .drawWithContent {
                            // In view: the showing page, or the one beside on the side the drag (or a settle) uncovers.
                            val x = slide.shift(size.width)
                            if (x > -size.width && x < size.width) drawContent()
                        },
                ) {
                    PageContent(state, beside?.first ?: screen, pageSelection, resume, gameInfo, if (current) live else notLive)
                    if (!current) Box(Modifier.fillMaxSize().pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } })
                }
            }
        }
    }
}

/** An update dialog's body: the version line, then the release's first paragraph when it has one. */
private fun updateText(r: AppRelease): String = "NX Redux ${r.version}" + if (r.notes.isNotBlank()) "\n\n${r.notes}" else ""

/**
 * A value row's picker, About's update confirm, the Problem dialog and the notice, over the focused page (a tool's on
 * the top screen: [MenuTopScreen]).
 */
@Composable
internal fun BoxScope.PageOverlays(state: AppState, chooser: ValueChooser?, error: String?, notice: String?) {
    // A touch value row's picker (the in-game menu's).
    chooser?.let { c -> NxSettingPicker(c.title, c.labels, c.current, c.selected, onPick = state::chooseValue, onDismiss = state::closeChooser) }
    val settingInfo by state.settingInfo.collectAsState()   // a Settings row's full description
    NxSettingInfoPopup(settingInfo?.let { p -> NxHelpText(p.title, p.text, p.scrollSeq, p.scrollDir) }, onClose = state::closeSettingInfo)
    // About's Update to: here, so it shows on whichever screen Settings is on (the top one in dual screen).
    val updateConfirm by state.updateConfirm.collectAsState()
    updateConfirm?.let { r ->
        NxDialog("Update to ${r.version}", updateText(r), onConfirm = { state.answerUpdateConfirm(true) }, confirmLabel = "Update",
            onCancel = { state.answerUpdateConfirm(false) }, cancelLabel = "Cancel", scrimDismisses = false, scrollSteps = state.updateScroll)
    }
    error?.let { NxDialog("Problem", it, onConfirm = state::dismissError) }
    notice?.let {
        Text(
            it, color = LocalNxColors.current.main, fontSize = 12.sp,
            modifier = Modifier.align(Alignment.TopCenter).background(LocalNxColors.current.scrimNotice, RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

/**
 * A page's background and screen; the background slides with it, as NextUI slides the whole captured screen.
 * [selection] and [resume] are read only where they show (the background, and inside the screens: the selected tile,
 * the caption, the hints, the info band), so a selection change does not recompose the whole page. [live]: whether this
 * is the page showing (false while it slides out). [onTop]: drawn by [MenuTopScreen] for a tool with the focus, so never
 * the bottom screen's stand-ins (the menu under it, a game's details).
 */
@Composable
internal fun PageContent(state: AppState, page: Screen, pageSelection: State<ListSelection>, resume: State<ResumeInfo>, allInfo: Map<String, GameInfo>, live: State<Boolean>, onTop: Boolean = false) {
    // The menu on the second display, on a game list: the list and its overlays are on the top screen ([MenuTopScreen]);
    // this screen has the highlighted game's details alone, full screen (no title, no hints).
    val dualMenu by state.dualMenu.collectAsState()
    val toolOnTop by state.toolOnTop.collectAsState()
    // A tool with the focus on the top screen: this one keeps the menu it was opened from, on its row, still and dimmed;
    // taps on it do nothing (B gives the focus back).
    if (!onTop && DualMenu.bottom(page, dualMenu, toolOnTop) == BottomContent.Menu) {
        state.toolUnder()?.let { (menu, at) ->
            val still = remember(menu, at) { mutableStateOf(at) }
            PageContent(state, menu, still, resume, allInfo, live)
        }
        Box(Modifier.fillMaxSize().background(LocalNxColors.current.scrimUnder).pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent().changes.forEach { it.consume() } } })
        return
    }
    if (!onTop && page is Screen.Games && DualMenu.bottom(page, dualMenu) == BottomContent.Details) {
        val ra by state.gameDetailRa.collectAsState()
        val entry = page.entries.getOrNull(pageSelection.value.index)
        GameDetailsPane(entry, entry?.let { allInfo[it.game.path] }, ra, onBack = { state.back() })
        return
    }
    val now = rememberNow(allInfo)
    // A game's own Game Settings is a modal over the game list it came from: that list is the page drawn here, as it was
    // (the same composition group, so its list state survives the modal), on the selection it had; the modal goes on top.
    val under = if (gameSettingsModal(page)) state.gameSettingsUnder() else null
    val s = under?.first ?: page
    // Layouts → Extra info: the launcher menus' (the main menu, Home included, and the game lists) game stats all come
    // from this map, so hidden, every layout's info lines are empty ([launcherGameInfo]); counts stay. Home's stats strip
    // follows Layouts → Home stats instead.
    val gameInfo = launcherGameInfo(allInfo, LocalLauncherChrome.current.extraInfo)
    val frozen = remember(under) { under?.let { mutableStateOf(it.second) } }
    val selection: State<ListSelection> = frozen ?: pageSelection
    Box(Modifier.fillMaxSize()) {
        val style = state.styleOf(s)   // this page's own: a page sliding out keeps its style while the next one's may differ
        val orientation = state.orientationOf(s)   // likewise its orientation
        // A Grid reports its columns only while its page is the one showing (a page sliding out never overwrites the count).
        // Equal, not the same object: a main menu tab's page laid out beside the one showing (its swipe's neighbour) holds an
        // equal copy of the screen, and keeps it once it shows (an equal screen recomposes nothing), so an identity check
        // would drop its reports for good (a rotation then left Grid Home on its old width).
        // New reporters, too, when the page starts showing ([live]): one laid out beside it (a swipe's neighbour) had its
        // reports dropped, and the page then sends them again ([HomeScreen], [NxTileGrid]).
        val shows = live.value
        val onGridLayout: (Int) -> Unit = remember(s, shows) { { columns -> if (state.screen.value == s) state.onGridLayout(columns) } }
        val onHomeLayout: (HomeFrame) -> Unit = remember(s, shows) { { f -> if (state.screen.value == s) state.onHomeLayout(f) } }   // likewise Home's body frame
        val homeView = state.homeView.collectAsState()   // what Home draws beside its rows
        val homeRowLast = state.homeCarouselRowLast.collectAsState()   // Carousel Home's row item last on (AppState's d-pad memory)
        val index = { selection.value.index }
        val minimalOpening = state.minimalOpening.collectAsState()   // a Minimal page's row being opened (its page draws the band)
        // Full-height background behind everything (list, scroll arrows, hint bar).
        PageBackground(state, s, style, selection, live)
        when (s) {
            is Screen.PermissionsStep -> WithIndex(selection) { val (t, b) = remember(s) { state.setupChrome() }; PermissionsStepScreen(s, t, b, it, state::select, state::setupStepRow, state::onButton) }
            is Screen.FilePicker -> WithIndex(selection) { FilePickerScreen(s, it, state::select, { state.openSelected() }, onHint = state::onButton, onDraft = state::filePickerDraft) }
            Screen.PickFolder -> WithIndex(selection) { val (t, b) = remember(s) { state.setupChrome() }; PickFolderScreen(t, b, it, state::select, state::setupStepRow, state::onButton, systemPicker = !state.filePickerOn()) }
            is Screen.Onboarding -> WithIndex(selection) { OnboardingScreen(s, it, state::select, state::onboardingRow, onHint = state::onButton, onSetting = state::onboardingSettingEvent, onDraftKey = state::onboardingDraftKey, onDraftUser = state::onboardingDraftUser, onDraftPassword = state::onboardingDraftPassword) }
            is Screen.StorageUpgrade -> WithIndex(selection) { StorageUpgradeScreen(s, it, state::select, state::storageUpgradeRow, state::onButton) }
            is Screen.CopySaves -> WithIndex(selection) { CopySavesScreen(s, it, state::select, state::copySavesRow, state::onButton) }
            Screen.AddFirstExtra -> WithIndex(selection) { val (t, b) = remember(s) { state.setupChrome() }; AddFirstExtraScreen(t, b, it, state::select, state::setupStepRow, state::onButton, note = remember(s) { state.setupNote() }) }
            is Screen.FolderSummary -> WithIndex(selection) { val (t, _) = remember(s) { state.setupChrome() }; FolderSummaryScreen(s, t, it, state::select, state::setupStepRow, state::onButton, note = remember(s) { state.setupNote() }) }
            is Screen.AndroidGamesStep -> WithIndex(selection) { val (t, b) = remember(s) { state.setupChrome() }; AndroidGamesStepScreen(s.ask, t, b, it, state::select, state::setupStepRow, state::onButton) }
            Screen.Loading -> Box(Modifier.fillMaxSize())
            // layoutKey: the tab or list laid out, not the Screen: a republish of the same page (a context menu opening or
            // closing, another tab rebuilt) must not snap a carousel or backdrop mid-glide; its own tiles changing still do.
            is Screen.Systems -> {
                val listBand = mainMenuListBand(s.category)   // Consoles, Collections and Tools: no List band (no shade, no "N games")
                MainMenuScreen(
                    s, index, state::tapMainRow, { state.openSelected() }, state::selectTab,
                    onSwipeSelect = state::select, onTabSwipe = state::swipeTab, onHint = state::onButton, onLongPress = { state.openContextMenu(it) },
                    info = {
                        if (!listBand.info) emptyList() else when (val row = s.rows.getOrNull(selection.value.index)) {
                            is MainRow.System -> consoleInfoLines(row)
                            is MainRow.Collection -> collectionInfoLines(row)
                            else -> gameInfoFor(gameInfo, now, row.game(), full = true)   // Grid shows none (its tiles have tileInfo)
                        }
                    },
                    rowInfo = { i -> gameInfoFor(gameInfo, now, s.rows.getOrNull(i).game(), full = true) },   // Carousel Home's caption, any row
                    style = style, orientation = orientation, onGridLayout = onGridLayout,
                    tileInfo = remember(s.rows, gameInfo, now) { rowTileInfo(s.rows, gameInfo, now) }, layoutKey = s.category,
                    onHomeLayout = onHomeLayout, homeView = homeView.value, homeRowLast = { homeRowLast.value },
                    continueInfo = { gameInfoFor(gameInfo, now, (s.rows.firstOrNull() as? MainRow.Continue)?.entry?.game, full = true) },
                    canNetplay = { state.canNetplay(s.rows.getOrNull(selection.value.index).listed()) },
                    raSignedIn = state.settings.raToken != null,
                    controllerArt = state.settings.consoleArt == ConsoleArt.CONTROLLER,
                    minimalOpening = minimalOpening.value?.takeIf { s.category != null && it.page == s.category },
                    bandShade = listBand.shade, bandInfo = listBand.info,
                    canSearch = state.canSearch,
                    // Per page (s, not the screen now: a sliding-out page keeps its own): the screen carries its hidden tabs
                    // (none over a pushed one), so both follow every rebuild. With none, a long press on a tab word is a plain tap.
                    onTabMenu = state::openTabMenu.takeIf { s.hidden.isNotEmpty() },
                    // Every row has a menu while it holds a hidden tab. Recent and Search are never in it: an icon
                    // stands in for a hidden hint.
                    tabMenu = s.hidden.isNotEmpty(),
                )
                s.menu?.let { ContextMenuOverlay(it, onButton = state::onButton, onSelect = state::menuSelect, onOpen = state::menuOpen, onDraft = state::menuDraft, onColumns = state::onMenuLayout) }
                s.search?.let { NxTextPrompt("Search", it, state::searchDraft, onConfirm = { state.onButton(NxButton.A) }, onCancel = { state.onButton(NxButton.B) }) }
            }
            is Screen.Games -> GamesPage(state, s, selection, resume, gameInfo, now)
            // NX Redux PR #218: its info follows Extra info, as the menus'. List: a row tap selects, a second tap resumes.
            is Screen.Switcher -> GameSwitcherScreen(
                s, state::onButton, info = gameInfoFor(gameInfo, now, s.entries.getOrNull(s.index)?.game, full = true),
                onSelect = state::selectSwitcherRow, onOpen = { state.selectSwitcherRow(it); state.openSelected() },
            )
            // The other screens take the index as a value: [WithIndex] reads it, so only the screen recomposes.
            // Settings: two panes on a wide landscape window (the root on the left, its pages on the right), else one page.
            is Screen.Settings, is Screen.EmulatorList, is Screen.EmulatorConsoles, is Screen.EmulatorOptions, is Screen.SettingsMenu, is Screen.ToolApps ->
                // A game's own Game Settings: a modal over the game list it came from, the list dimmed and inert behind it.
                // No hint bar in Settings unless Layouts → Button hints shows it: every button there is a console menu's.
                if (gameSettingsModal(s)) Unit   // no list to draw under it: the modal alone (below)
                // Its bars' hints start on the section words' edge, in line with the text above them.
                else CompositionLocalProvider(LocalNxHintBarHidden provides !LocalLauncherChrome.current.buttonHints, LocalNxHintBarStart provides settingsSectionStart()) {
                    if (settingsSplitView(LocalWindowSize.current, s)) SettingsSplitScreen(state, s, selection) { SettingsPage(state, s, selection, titled = true) }
                    else NarrowSettingsPage(state, s, selection)
                }
            is Screen.CheatDb -> state.cheatDb?.let { c -> WithIndex(selection) { CheatDbScreen(s, c, it, state::select, { state.openSelected() }, onHint = state::onButton) } } ?: Box(Modifier.fillMaxSize())
            Screen.DeviceSync -> state.deviceSync?.let {
                val setup by state.syncSetup.collectAsState()
                DeviceSyncScreen(it, onHint = state::onButton, setup = setup, onSetupTap = state::syncSetupTap)
            } ?: Box(Modifier.fillMaxSize())
            is Screen.RetroAchievements -> state.achievements?.let { c -> WithIndex(selection) { RetroAchievementsScreen(s, c, it, state::select, { state.openSelected() }, onHint = state::onButton) } } ?: Box(Modifier.fillMaxSize())
            is Screen.RaDownload -> state.achievements?.let { c -> WithIndex(selection) { RaDownloadScreen(s, c, it, state::select, {}, onHint = state::onButton) } } ?: Box(Modifier.fillMaxSize())
            is Screen.GameTime -> WithIndex(selection) { GameTimeScreen(s, it, state::select, { state.openSelected() }, state::gameTimeSelectModal, onHint = state::onButton) }
            // A game's sessions: on a split window beside the tracker list (the RetroAchievements column browser's rule); else one page.
            is Screen.GameTimeSessions ->
                if (gameTimeSplitView(LocalWindowSize.current, s)) GameTimeSplitScreen(state, s, selection)
                else WithIndex(selection) { GameTimeSessionsScreen(s, it, state::select, onHint = state::onButton) }
            // The RetroAchievements levels: on a split window a column browser, the level it came from on the left (the
            // same rule as the two-pane Settings); else one page.
            is Screen.RaGames, is Screen.RaGame, is Screen.RaAchievement, is Screen.RaSettings ->
                if (raSplitView(LocalWindowSize.current, s)) RaSplitScreen(state, s) { RaLevelPage(state, s, selection, split = true) }
                else RaLevelPage(state, s, selection, split = false)
            // Artwork's pages: on a split window a column browser beside the page they came from (the RetroAchievements
            // rule); the Artwork page itself, and every page on a narrow or portrait window, one page.
            is Screen.Artwork, is Screen.ArtworkConsoles, is Screen.ArtworkProgress, is Screen.ArtworkSettings,
            is Screen.ArtworkReset ->
                if (artworkSplitView(LocalWindowSize.current, s)) ArtworkSplitScreen(state, s, selection)
                else WithIndex(selection) { ArtworkScreen(s, state, it, state::select, { state.openSelected() }, onHint = state::onButton) }
        }
        (page as? Screen.EmulatorOptions)?.takeIf(::gameSettingsModal)?.let { modal ->
            WithIndex(pageSelection) {
                GameSettingsModal(modal, it, state::select, { state.openSelected() }, onHint = state::onButton, onClose = state::closeGameSettings, onSetting = state::settingEvent, onTruncated = state::onSettingInfoTruncated)
            }
        }
    }
}

/**
 * A game list page ([Screen.Games]) with all its overlays: the list in the game list style, the achievements panel, the
 * Layout dropdown, the search prompt and the context menu (with its rename / new collection drafts). The single screen's
 * page, and the second display's top screen while the main menu is there ([MenuTopScreen]). [gameInfo]: the launcher's
 * ([launcherGameInfo]); [now]: [rememberNow]'s clock.
 */
@Composable
internal fun GamesPage(state: AppState, s: Screen.Games, selection: State<ListSelection>, resume: State<ResumeInfo>, gameInfo: Map<String, GameInfo>, now: Long) {
    val style = state.styleOf(s)
    val orientation = state.orientationOf(s)
    // A Grid reports its columns only while its page is the one showing (a page sliding out never overwrites the count).
    val onGridLayout: (Int) -> Unit = remember(s) { { columns -> if (state.screen.value === s) state.onGridLayout(columns) } }
    val onPageRows: (Int) -> Unit = remember(s) { { rows -> if (state.screen.value === s) state.onPageRows(rows) } }   // likewise LEFT / RIGHT's page
    val index = { selection.value.index }
    val minimalOpening = state.minimalOpening.collectAsState()   // a Minimal page's row being opened (its page draws the band)
    val raPanel by state.raPanel.collectAsState()   // the achievements panel over the list
    val dual by state.dualMenu.collectAsState()   // the menu is on the second display: its details pane has the achievements
    val current = { s.entries.getOrNull(selection.value.index) }
    GameListScreen(
        listTitle(s.source), s.entries, index,
        canResume = { resume.value.canResume }, menuAvailable = s.source != ListSource.Hidden,
        canOpen = { current().let { it != null && (s.source == ListSource.Hidden || it.system.core != null || it.system.android) } },
        canNetplay = { state.canNetplay(current()) },
        openLabel = if (s.source == ListSource.Hidden) "Unhide" else "Open",
        onSelect = state::select, onOpen = { state.openSelected() }, onHint = state::onButton,
        onLongPress = { state.openContextMenu(it) },
        showTag = s.source == ListSource.Hidden || s.source is ListSource.Search,   // a search mixes consoles
        emptyText = if (s.source is ListSource.Search || s.source is ListSource.Filtered) "No results" else null,
        info = { gameInfoFor(gameInfo, now, current()?.game, full = true) },   // Grid shows none (its tiles have tileInfo)
        style = style, orientation = orientation, alignment = state.settings.gameListVerticalAlignment, onGridLayout = onGridLayout, onPageRows = onPageRows, tileInfo = remember(s.entries, gameInfo, now) { entryTileInfo(s.entries, gameInfo, now) }, layoutKey = s.source,
        artBox = state.settings.gameListArt.boxed,
        backdropArt = state.settings.backdropArt,
        onSearch = state::openListSearch.takeIf { s.source.searchable },
        onLayout = state::toggleLayoutMenu.takeIf { state.settings.gameListLayoutOptions }, layoutOpen = s.layoutMenu != null,
        // Layouts → Achievements panel Hide: no icon or hint (X and the game's menu still open it). Dual: none (X does nothing).
        onAchievements = state::toggleRaPanel.takeIf { state.hasAchievements && state.settings.gameListAchievementsPanel && !dual }, achievementsOpen = raPanel != null,
        minimalOpening = minimalOpening.value?.takeIf { it.page == s.source },
    )
    RaPanelOverlay(raPanel.takeIf { state.screen.value === s }, onRow = state::openRaPanelRow, onButton = state::onButton)
    s.layoutMenu?.let { at ->
        GameListLayoutMenu(
            current = GameListLayouts.indexOf(state.settings.gameListStyle, state.settings.gameListOrientation, state.settings.gameListVerticalAlignment), highlighted = at,
            onPick = state::pickLayout, onDismiss = { state.onButton(NxButton.B) },
        )
    }
    s.search?.let { NxTextPrompt("Search ${listTitle(s.source.base)}", it, state::searchDraft, onConfirm = { state.onButton(NxButton.A) }, onCancel = { state.onButton(NxButton.B) }) }
    s.menu?.let { ContextMenuOverlay(it, onButton = state::onButton, onSelect = state::menuSelect, onOpen = state::menuOpen, onDraft = state::menuDraft, onColumns = state::onMenuLayout) }
}

/**
 * Settings on a narrow window (a phone held upright, a landscape window too narrow for the stage), one level at a time:
 * the root is the section block alone; a page below it is the rows block ([settingsNarrowBlock]), "Settings / <title>" its
 * first line; B (or the back gesture) goes back. A Layouts group and the Android games picker draw whole, as before.
 */
@Composable
private fun NarrowSettingsPage(state: AppState, s: Screen, selection: State<ListSelection>) {
    // At least the section block's height, so a short page's first line sits where "Settings" does on the root.
    WithSettingsSectionSize(state.settingsSections()) { size ->
        val block = remember(size.height) { SettingsBlockMode(SETTINGS_TITLE, minHeight = size.height) }
        CompositionLocalProvider(LocalSettingsBlock provides block.takeIf { settingsNarrowBlock(s) }) {
            SettingsPage(state, s, selection, titled = false)
        }
    }
}

/** A Settings screen drawn as one page (or as the two-pane Settings' focused right pane, [titled]: the split draws its dialog). */
@Composable
private fun SettingsPage(state: AppState, s: Screen, selection: State<ListSelection>, titled: Boolean) {
    val settingTruncated by state.settingInfoTruncated.collectAsState()   // the selected row's cut-off line: its X Info hint
    when (s) {
        is Screen.Settings -> WithIndex(selection) {
            SettingsScreen(s, it, state::select, { state.openSelected() }, onHint = state::onButton, titled = titled, dialog = !titled, onSetting = state::settingEvent, onTruncated = state::onSettingInfoTruncated, truncated = settingTruncated)
        }
        is Screen.EmulatorList -> WithIndex(selection) { EmulatorListScreen(s, it, state::select, { state.openSelected() }, onHint = state::onButton, split = titled || LocalSettingsBlock.current != null) }
        is Screen.EmulatorConsoles -> WithIndex(selection) { EmulatorConsolesScreen(s, it, state::select, { state.openSelected() }, onHint = state::onButton) }
        is Screen.EmulatorOptions -> WithIndex(selection) { EmulatorOptionsScreen(s, it, state::select, { state.openSelected() }, onHint = state::onButton, onSetting = state::settingEvent, onTruncated = state::onSettingInfoTruncated, truncated = settingTruncated) }
        is Screen.SettingsMenu -> WithIndex(selection) { SettingsMenuScreen(s, it, state::settingsCategoryTap, hintBar = true, onHint = state::onButton) }
        is Screen.ToolApps -> {
            // Only the picker showing reports its columns (one sliding out never overwrites the count).
            val onColumns: (Int) -> Unit = remember(s) { { columns -> if (state.screen.value === s) state.onToolAppsLayout(columns) } }
            WithIndex(selection) { ToolAppsScreen(s, it, state::select, { state.openSelected() }, onColumns, onHint = state::onButton, onLongPress = state::openContextMenu, titled = titled) }
            s.menu?.let { ContextMenuOverlay(it, onButton = state::onButton, onSelect = state::menuSelect, onOpen = state::menuOpen, onDraft = state::menuDraft, onColumns = state::onMenuLayout) }
        }
        else -> Unit
    }
}

/** Reads [selection]'s index in its own scope, so a selection change recomposes only [content], not the page around it. */
@Composable
internal fun WithIndex(selection: State<ListSelection>, content: @Composable (Int) -> Unit) = content(selection.value.index)

/**
 * A page's full-height background. It reads [selection] itself, and only through the background it gives (a Grid or
 * Carousel page's is plain whatever is selected), so a selection change recomposes it only when the picture changes.
 * [live]: false on the page sliding out, whose Backdrop picture fades to black as it goes.
 */
@Composable
internal fun PageBackground(state: AppState, s: Screen, style: MenuStyle, selection: State<ListSelection>, live: State<Boolean>) {
    when (s) {
        is Screen.Systems -> {
            val category = s.category ?: MenuCategory.HOME
            val consoleArt = state.settings.consoleArt
            val portrait = LocalPortrait.current   // List Home's game picture: the screen's shape picks its art
            // Keyed on [selection] too: a page laid out beside the one showing reads a still copy of its row, and swaps
            // to the live one when it starts showing, which the picture must then follow.
            val bg by remember(s, style, consoleArt, portrait, selection) {
                derivedStateOf(structuralEqualityPolicy()) { menuBackground(style, category, s.rows.getOrNull(selection.value.index), consoleArt, portrait) }
            }
            // Grid and Carousel: plain, as menuBackground gives every non-List style (a Carousel Consoles tab on Background aside).
            MainMenuBackground(bg, state.settings.gameListArt)
        }
        is Screen.Games -> {
            // Grid and Carousel: plain black, as their tabs (the tiles carry the art). Minimal: the names alone, no art.
            if (style == MenuStyle.GRID || style == MenuStyle.CAROUSEL || style == MenuStyle.MINIMAL) {
                Box(Modifier.fillMaxSize().background(LocalNxColors.current.background))
                return
            }
            // The selected game's own picture ([backgroundArt]): its screenshot, else its SteamGridDB hero on a landscape
            // screen or its grid on a portrait one. Backdrop: under its shade. List: shifted right of centre so the middle
            // shows, with the diagonal fade. Any list (Recently Played, a collection, a console): never a folder or console
            // background. Last its generated abstract picture ([gameBackground]), as a screenshot, an Android game's too.
            // List: Layouts → List art picks that picture, a boxed one on the right, or none ([listArtShown]).
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val portrait = maxHeight > maxWidth
                if (style == MenuStyle.BACKDROP) {
                    val url by remember(s, portrait, selection) {
                        derivedStateOf(structuralEqualityPolicy()) { s.entries.getOrNull(selection.value.index)?.let { gameBackground(it, portrait) } }
                    }
                    // In from black on opening; out to black while the page slides away (B back to the tab, the switcher).
                    NxBackdropPicture(backdropPictureTarget(live.value, url), Modifier.fillMaxSize())
                } else {
                    // List: Layouts → List art ([listArtShown]).
                    val art = state.settings.gameListArt
                    val shown by remember(s, portrait, art, selection) {
                        derivedStateOf(structuralEqualityPolicy()) { listArtShown(s.entries.getOrNull(selection.value.index), art, portrait) }
                    }
                    ListArtBackground(shown, maxWidth, LocalPortrait.current)
                }
            }
        }
        is Screen.Switcher -> Box(Modifier.fillMaxSize().background(Color.Black))
        // The welcome carries its own large mark: plain black behind it. Every other setup page, the folder steps too, is
        // Settings' rows block ([SetupBlockPage]): the Settings background.
        is Screen.Onboarding -> when {
            s.step == OnboardingStep.WELCOME -> Box(Modifier.fillMaxSize().background(Color.Black))
            else -> SettingsBackground()
        }
        is Screen.PermissionsStep, Screen.PickFolder, Screen.AddFirstExtra, is Screen.FolderSummary, is Screen.AndroidGamesStep,
        is Screen.StorageUpgrade, is Screen.CopySaves -> SettingsBackground()
        // The folder picker: the small mark only, so the large one never sits behind its names.
        is Screen.FilePicker -> SettingsBackground()
        // Settings: the small mark at the top right alone ([SettingsBackground]); the large centred mark is not drawn.
        else -> if (settingsPane(s) != null) SettingsBackground() else MarkBackground(s)
    }
}

/** The NX mark; two panes (the RetroAchievements column browser, Game Tracker, Artwork), centred on the right pane (same size), as the page it sits behind. */
@Composable
private fun MarkBackground(s: Screen) = NxScreenBackground(
    url = null, fallback = Res.drawable.background, fade = false, modifier = Modifier.fillMaxSize().clipToBounds(), centred = true,
    markShift = settingsMarkShift(twoPaneView(LocalWindowSize.current, s)),
)

/**
 * A List's background ([ListArtShown]; a game list's and List Home's): the full-screen picture (shifted right of
 * centre, the diagonal fade), the NX mark for none; a boxed picture whole on the right over plain black, the game's
 * picture either way dimmed by [LIST_ART_DIM], falling back to the full-screen picture
 * when it fails to load; or plain black.
 */
@Composable
private fun ListArtBackground(shown: ListArtShown, width: Dp, portrait: Boolean) {
    when (shown) {
        ListArtShown.None -> Box(Modifier.fillMaxSize().background(LocalNxColors.current.background))
        is ListArtShown.Full ->
            if (shown.url == null) NxScreenBackground(url = null, fallback = Res.drawable.background, fade = false, modifier = Modifier.fillMaxSize(), centred = true)
            else Box(Modifier.fillMaxSize()) {
                NxScreenBackground(url = shown.url, fallback = null, fade = true, modifier = Modifier.fillMaxSize())
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = LIST_ART_DIM)))   // role: a List's game art dim
            }
        is ListArtShown.Boxed -> {
            var failed by remember(shown.url) { mutableStateOf(false) }
            if (failed) { ListArtBackground(ListArtShown.Full(shown.fallback), width, portrait); return }
            Box(Modifier.fillMaxSize().background(LocalNxColors.current.background)) {
                AsyncImage(
                    model = shown.url, contentDescription = null, contentScale = ContentScale.Fit, alpha = 1f - LIST_ART_DIM,   // role: a List's game art dim, over black
                    onState = { if (it is AsyncImagePainter.State.Error) failed = true },
                    modifier = Modifier.align(Alignment.CenterEnd).padding(end = listArtGutter(portrait).dp)
                        .width(width * LIST_ART_WIDTH_SHARE).fillMaxHeight(LIST_ART_HEIGHT_SHARE),
                )
            }
        }
    }
}

/**
 * A main menu tab's full-height background, as [menuBackground] gives it: a List console's controller ([MenuBackground.Pad])
 * or logo, List Home's game picture or tool logo, else plain (a Grid or Carousel tab: [MenuBackground.Plain]).
 */
@Composable
private fun MainMenuBackground(bg: MenuBackground, listArt: GameListArt) {
    when (bg) {
        // List Consoles on Background: as a game list's List background (shifted right of centre, the diagonal fade).
        is MenuBackground.Picture -> NxScreenBackground(url = bg.url, fallback = null, fade = true, modifier = Modifier.fillMaxSize())
        // List Home's game: as a game list's List shows it, by Layouts → List art ([listArtShown]).
        is MenuBackground.Game -> BoxWithConstraints(Modifier.fillMaxSize()) {
            val shown = remember(bg, listArt, maxHeight > maxWidth) { listArtShown(bg.entry, listArt, maxHeight > maxWidth) }
            ListArtBackground(shown, maxWidth, LocalPortrait.current)
        }
        // Carousel Consoles on Background: the console's picture under the Backdrop game list's dim and shade.
        is MenuBackground.Backdrop -> NxBackdropPicture(bg.url, Modifier.fillMaxSize())
        is MenuBackground.Logo -> Box(Modifier.fillMaxSize().background(LocalNxColors.current.background)) {
            // The selected console's logo, white at 16% through its mask, 40% wide (44% portrait) and 60% tall,
            // right-aligned; the logos vary in height, so each fits its box.
            val portrait = LocalPortrait.current
            Image(
                painterResource(bg.res), contentDescription = null,
                colorFilter = ColorFilter.tint(LocalNxColors.current.main), contentScale = ContentScale.Fit, alpha = 0.16f,   // role: the console logo, ink at 16%
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = if (portrait) 16.dp else 32.dp)
                    .fillMaxWidth(if (portrait) 0.44f else 0.40f).fillMaxHeight(0.6f),
            )
        }
        is MenuBackground.Pad -> BoxWithConstraints(Modifier.fillMaxSize().background(LocalNxColors.current.background)) {
            // The selected console's controller alone (no logo): zoomed in, pushed past the right edge, turned −12°,
            // at 35%; on the page layer, so only the screen's edges crop it and the tabs and hints sit over it.
            val portrait = LocalPortrait.current
            val p = bg.pad
            Image(
                padPainter(p.res), contentDescription = null, contentScale = ContentScale.Fit,
                colorFilter = ColorFilter.colorMatrix(ColorMatrix(padColorMatrix(LIST_PAD_BRIGHTNESS * p.brightness, 1f))),
                modifier = Modifier.align(Alignment.CenterEnd)
                    .offset(x = maxWidth * (if (portrait) 0.22f else 0.16f), y = maxHeight * (if (portrait) 0f else 0.02f))
                    .fillMaxWidth(if (portrait) 0.82f else 0.74f).fillMaxHeight(if (portrait) 0.8f else 1f)
                    .graphicsLayer { rotationZ = -12f; alpha = LIST_PAD_ALPHA },
            )
        }
        MenuBackground.Plain -> Box(Modifier.fillMaxSize().background(LocalNxColors.current.background))
    }
}
