package com.nxredux.mobile.runner

import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.os.BatteryManager
import android.view.Display
import android.view.Gravity
import android.view.Surface
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.nxredux.mobile.R
import com.nxredux.mobile.achievements.sortedBy
import com.nxredux.mobile.cheats.CheatSet
import com.nxredux.mobile.gametime.GameTimeService
import com.nxredux.mobile.netplay.NetplaySession
import com.nxredux.mobile.options.BottomScreenOptions
import com.nxredux.mobile.options.N3dsOptions
import com.nxredux.mobile.options.NdsOptions
import com.nxredux.mobile.options.NdsScreen
import com.nxredux.mobile.options.NdsSettings
import com.nxredux.mobile.runner.GameActivity.Companion.EXTRA_ART_PATH
import com.nxredux.mobile.runner.GameActivity.Companion.EXTRA_GAME_PATH
import com.nxredux.mobile.runner.GameActivity.Companion.EXTRA_TITLE
import com.nxredux.mobile.settings.AndroidAppSettings
import com.nxredux.mobile.storage.FileRefs
import com.nxredux.mobile.ui.nx.GamePanel
import com.nxredux.mobile.ui.nx.GamePanelEvent
import com.nxredux.mobile.ui.nx.GamePanelHeader
import com.nxredux.mobile.ui.nx.GamePanelModel
import com.nxredux.mobile.ui.nx.GamePanelState
import com.nxredux.mobile.ui.nx.GamePanelTab
import com.nxredux.mobile.ui.nx.InGameMenuModel
import com.nxredux.mobile.ui.nx.LocalNxColors
import com.nxredux.mobile.ui.nx.LocalNxOptionText
import com.nxredux.mobile.ui.nx.MenuEffect
import com.nxredux.mobile.ui.nx.NxTheme
import com.nxredux.mobile.ui.nx.PanelPill
import com.nxredux.mobile.ui.nx.nxColorsFor
import com.swordfish.libretrodroid.GLRetroView
import com.swordfish.libretrodroid.LibretroDroid
import com.swordfish.libretrodroid.NxLog
import com.swordfish.libretrodroid.ScreenRect
import com.swordfish.libretrodroid.ScreenRegion
import com.swordfish.libretrodroid.Variable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * What [DualScreenController] needs from [GameActivity]: the activity (the presentation, the panel's window, the
 * orientation lock), the game's state and flags (owned there: read, never copied), and the in-game menu, which the panel
 * shares its effects and data with.
 */
internal interface DualScreenHost : LifecycleOwner {
    val activity: ComponentActivity
    val retroView: GLRetroView?
    val frontendValues: Map<String, String>
    /** The config's resolved values (launch, or the last restore): the 3DS swap values leaving split puts back. */
    val storedValues: Map<String, String>
    val gameReady: Boolean
    val resumePending: Boolean
    val quitting: Boolean
    val menuOpen: Boolean
    val isNds: Boolean
    val isN3ds: Boolean
    val netplayNow: NetplaySession?
    val achievements: AchievementsBridge?
    /** The game's cheats (toggled through [handleMenuEffect]), whether the core takes any, and their folder: the Cheats tab. */
    val cheatsSupported: Boolean
    val cheatSet: CheatSet
    val cheatsFolder: String
    /** The in-game menu's view while it is open (the panel on main takes its place). */
    val menuView: ComposeView?
    fun layoutTag(): String
    fun menuLayoutParams(orientation: Int, overPanel: Boolean): FrameLayout.LayoutParams
    fun isLandscape(): Boolean
    fun syncN3dsCore()
    fun toast(msg: String): android.widget.Toast
    /** The panel's Second screen, Players and Cheats rows act as the in-game menu's own effects ([MenuEffect]). */
    fun handleMenuEffect(effect: MenuEffect)
    /** The open menu's Second screen shortcut again. */
    fun publishMenuSecondScreen()
    /** Runs [block] on the open in-game menu, if any, and redraws it. */
    fun withMenu(block: (InGameMenuModel) -> Unit)
}

/**
 * The second display (DualScreen): its presentation and surface, which display the picture is on, the landscape lock,
 * the 3DS core's split options, and the game panel (on the second display, or on main while the picture is on the
 * second one) with the data it shares with the in-game menu's panel: the discs, the play time and the header's clock.
 */
internal class DualScreenController(
    private val owner: DualScreenHost,
    private val input: GameInputRouter,
    private val padLayout: PadLayoutController,
    private val renderer: FrontendRenderer,
) {

    private val activity get() = owner.activity
    private val resources get() = owner.activity.resources
    private val intent get() = owner.activity.intent
    private val isFinishing get() = owner.activity.isFinishing
    private val isDestroyed get() = owner.activity.isDestroyed
    private var requestedOrientation: Int
        get() = owner.activity.requestedOrientation
        set(value) { owner.activity.requestedOrientation = value }
    private fun <T : View> findViewById(id: Int): T = owner.activity.findViewById(id)
    private val lifecycle get() = owner.lifecycle
    private val lifecycleScope get() = owner.lifecycleScope
    private val retroView get() = owner.retroView
    private val frontendValues get() = owner.frontendValues
    private val storedValues get() = owner.storedValues
    private val gameReady get() = owner.gameReady
    private val resumePending get() = owner.resumePending
    private val quitting get() = owner.quitting
    private val menuOpen get() = owner.menuOpen
    private val isNds get() = owner.isNds
    private val isN3ds get() = owner.isN3ds
    private val netplayNow get() = owner.netplayNow
    private val achievements get() = owner.achievements
    private val menuView get() = owner.menuView
    private fun layoutTag() = owner.layoutTag()
    private fun menuLayoutParams(orientation: Int, overPanel: Boolean = false) = owner.menuLayoutParams(orientation, overPanel)
    private fun isLandscape() = owner.isLandscape()
    private fun syncN3dsCore() = owner.syncN3dsCore()
    private fun toast(msg: String) = owner.toast(msg)

    val secondScreens by lazy { SecondScreens(activity) }
    val appSettings by lazy { AndroidAppSettings(activity) }   // reads the prefs live: the dual paths' settings
    var presentation: SecondScreenPresentation? = null
        private set
    private var secondSurface: SecondSurface? = null   // the presentation's surface
    private var pushedSecondSurface: SecondSurface? = null   // what the GL thread was last handed ([pushSecondSurface])
    var dualMode = DualMode.OFF
        private set
    private var panelUp = false                  // the panel is flipped up over the DS/3DS screen (session only)
    var pictureOn = PictureOn.MAIN       // the wider display: the picture there, touch things on the other
        private set
    var n3dsPicture = NdsScreen.TOP      // the 3DS screen on the picture screen while split (session only)
        private set
    private var orientationBeforeDual: Int? = null   // requestedOrientation before the second screen locked landscape
    // The game panel on the second display (PANEL, PANEL_UP), or on main while the picture is on the second one: its tab
    // and what it draws ([panelState], rebuilt by [refreshDualPanel] while it is up).
    var dualPanelTab = GamePanelTab.SHORTCUTS
    private val dualPanel = mutableStateOf<GamePanelState?>(null)
    private var panelWasShown = false            // at the last syncPanel: it re-reads the discs as the panel comes up
    // The game panel's data, shared by the in-game menu's panel and the dual-screen one.
    var discs: Pair<Int, Int>? = null    // the core's (current disc 0-based, count), once asked ([refreshDiscs])
    private var playTotalMs = 0L                 // this game's total play time before this launch ([readPlayTotal], once)
    var panelClockJob: Job? = null       // the header's minute tick while a panel shows ([syncPanelClock])
    // The header's clock text and battery level, read once a minute (the first header of each minute) for every refresh.
    private var panelClockMinute = -1L
    private var panelClock = ""
    private var panelBattery: Int? = null
    // The panel (or the Panel pill) in the game's own window, while the picture is on the second display.
    private var mainPanelHost: FrameLayout? = null
    val mainPanel = mutableStateOf<(@Composable () -> Unit)?>(null)
    private val mainPanelPill = mutableStateOf(false)
    @Volatile var n3dsSwapBeforeSplit: Map<String, String>? = null   // 3DS swap values the split replaced (GL thread)
    private var presentationShape: DisplayShape? = null   // the second display's size and density the window was made for

    /**
     * 3DS entering split: notes the swap values the core runs now (an unsaved menu change included), queued ahead of the
     * split's own, so leaving split can put them back. A launch straight into split noted the stored ones instead.
     */
    private fun captureN3dsSwap() {
        val view = retroView ?: return
        if (netplayNow != null) return   // no core option changes mid-session, so none to undo
        view.queueEvent {
            if (n3dsSwapBeforeSplit != null) return@queueEvent
            n3dsSwapBeforeSplit = runCatching {
                LibretroDroid.getVariables().filter { it.key in DualScreen.N3DS_SWAP_KEYS && it.value != null }
                    .associate { it.key!! to it.value!! }
            }.onFailure { NxLog.w(TAG_LOG, "reading the 3DS swap options failed", it) }.getOrNull()
        }
    }

    /** 3DS leaving split: the swap values from before it ([captureN3dsSwap]) and the orientation's proportion are back. */
    private fun restoreN3dsScreen() {
        val view = retroView ?: return
        if (netplayNow != null) return
        val stored = storedValues
        val proportion = N3dsOptions.stackedSmallProportion(isLandscape())
        view.queueEvent {
            val vars = DualScreen.n3dsSwapRestore(n3dsSwapBeforeSplit, stored).map { (k, v) -> Variable(k, v) } +
                Variable(N3dsOptions.STACKED_SMALL_PROPORTION, proportion)
            n3dsSwapBeforeSplit = null
            runCatching { view.updateVariables(*vars.toTypedArray()) }.onFailure { NxLog.w(TAG_LOG, "restoring the 3DS screen options failed", it) }
        }
    }

    /** The GL thread draws on the second display: split, and the picture there with the panel on main ([pictureOn]). */
    private fun secondDraws(mode: DualMode = dualMode, picture: PictureOn = pictureOn): Boolean =
        mode == DualMode.SPLIT || (picture == PictureOn.SECOND && (mode == DualMode.PANEL || mode == DualMode.PANEL_UP))

    /** The panel fills the game's window on main (the picture is on the second display). */
    fun mainPanelShown(): Boolean = mainPanel.value != null

    /**
     * The DS/3DS screens cut for the second display, in px of its surface: split, the screen not on main; with the picture
     * there, both DS screens side by side (panel up). Empty when it draws nothing, has no surface, or (split) the DS frame
     * is not the plain stack; null when it draws the whole frame instead ([secondRegions]). [g]: the game geometry.
     */
    fun secondDsRegions(g: FloatArray): List<NdsRegion>? {
        val surface = secondSurface
        if (!secondDraws() || surface == null || surface.width <= 0 || surface.height <= 0) return emptyList()
        val sw = surface.width.toFloat()
        val sh = surface.height.toFloat()
        val dsFrame = isNds && NdsLayout.isDsFrame(g[0].roundToInt(), g[1].roundToInt())
        return when {
            // Not the plain DS stack (no frame yet, or another layout): main draws it whole, so nothing is cut for here.
            dualMode == DualMode.SPLIT && isNds -> if (!dsFrame) emptyList() else DualScreen.ndsScreen(sw, sh, DualScreen.splitMain(NdsSettings.parse(frontendValues).big, pictureOn).other)
            dualMode == DualMode.SPLIT && isN3ds -> DualScreen.n3dsScreen(sw, sh, DualScreen.splitMain(n3dsPicture, pictureOn).other)
            dualMode == DualMode.PANEL_UP && dsFrame -> DualScreen.ndsPanelUp(sw, sh, NdsSettings.parse(frontendValues))
            else -> null
        }
    }

    /**
     * What the second display draws (dst 0..1 of its surface): the DS/3DS screens cut for it ([ds], from [secondDsRegions]),
     * or with the picture there and nothing cut, the whole frame fitted (3DS panel up, a regular game). Empty when it draws
     * nothing or has no surface. [g]: the game geometry.
     */
    fun secondRegions(g: FloatArray, ds: List<NdsRegion>?): List<ScreenRegion> {
        val surface = secondSurface ?: return emptyList()
        val sw = surface.width.toFloat()
        val sh = surface.height.toFloat()
        return ds?.map { it.toScreenRegion(sw, sh) } ?: listOfNotNull(wholeFrameRegion(sw, sh, g))
    }

    /**
     * The whole core frame fitted at the game's aspect on a [w] x [h] surface, shaded; touch only for 3DS (Azahar's own
     * side-by-side frame checks where a finger lands). Null before the first frame.
     */
    private fun wholeFrameRegion(w: Float, h: Float, g: FloatArray): ScreenRegion? {
        val aspect = g[2].takeIf { it > 0f } ?: if (g[0] > 0f && g[1] > 0f) g[0] / g[1] else return null
        val dst = DualScreen.fullFrame(w, h, aspect)
        if (dst.width <= 0f || dst.height <= 0f) return null
        return ScreenRegion(
            src = ScreenRect(0f, 0f, 1f, 1f),
            dst = ScreenRect(dst.x / w, dst.y / h, (dst.x + dst.width) / w, (dst.y + dst.height) / h),
            shaded = true,
            touch = isN3ds,
        )
    }

    /**
     * 3DS SWAP (the pill, SELECT + R, a pad's left-stick click) [down] or up: split, a press swaps the displays and the core
     * never sees it (Azahar's own swap would turn its frame under the fixed cuts). False when not split: Azahar's key.
     */
    fun onN3dsSwapPressed(down: Boolean): Boolean {
        if (!isN3ds || dualMode != DualMode.SPLIT) return false
        if (down) swapN3dsDisplays()
        return true
    }

    /** 3DS split: SWAP moves the bottom screen to the other display (an in-session state; the core's layout stays). */
    private fun swapN3dsDisplays() {
        n3dsPicture = n3dsPicture.other
        renderer.relayoutFrontend()
    }

    /** What the second display is for now: a qualifying display, the setting on, and the game (DS/3DS split, else the panel). */
    fun dualModeNow(): DualMode {
        val tag = layoutTag()
        return DualScreen.mode(
            secondScreens.current.value != null, appSettings.secondScreen,
            NdsOptions.appliesTo(tag) || N3dsOptions.appliesTo(tag), panelUp,
        )
    }

    /**
     * Follows the second display, the setting and the panel: shows or dismisses the presentation, sets the 3DS core's
     * screen options for the new mode, and lays both displays out again.
     */
    fun syncDual() {
        val wanted = if (isFinishing || isDestroyed) DualMode.OFF else dualModeNow()
        val geometry = retroView?.getGameGeometry()   // read once for the picture screen and the layout
        val display = secondScreens.current.value.takeIf { wanted != DualMode.OFF }
        // A different display, or the same one at a new size or density: a new window.
        if (presentation?.display?.displayId != display?.displayId || (display != null && shapeOf(display) != presentationShape)) {
            hidePresentation()
            display?.let(::showPresentation)
        }
        // No window on the second display (it went before it showed): nothing is moved onto it, both screens stay here.
        val mode = if (display != null && presentation == null) DualMode.OFF else wanted
        val picture = if (display == null || presentation == null) PictureOn.MAIN else pictureOnNow(display, geometry)
        val old = dualMode
        val oldPicture = pictureOn
        val changed = mode != old
        val moved = picture != oldPicture
        dualMode = mode
        pictureOn = picture
        if (changed || moved) NxLog.i(TAG_LOG, "second screen: $mode, picture on $picture")
        if (changed) lockLandscape(mode != DualMode.OFF)
        // Under a panel on the second display nothing is drawn there; with the picture there, the panel is on main.
        pushSecondSurface()
        syncPanel()
        if ((changed || moved) && isN3ds) {
            // Split and panel up set Azahar's layout and swap options; the other modes put the user's back.
            val forcedBefore = DualScreen.n3dsCoreOptions(old) != null
            val forced = DualScreen.n3dsCoreOptions(mode) != null
            if (forced && !forcedBefore) captureN3dsSwap()
            syncN3dsCore()
            if (forcedBefore && !forced) restoreN3dsScreen()
        }
        renderer.relayoutFrontend(geometry)
    }

    /**
     * Which display is the picture screen for [display]: the game's window as landscape (it is locked so while dual)
     * against the display's real size as it is turned now, unless a bottom screen is chosen (the console's Bottom screen,
     * else Settings → Emulators'). [geometry]: the view's game geometry (null before the view).
     */
    private fun pictureOnNow(display: Display, geometry: FloatArray?): PictureOn {
        val root = findViewById<View>(R.id.root)
        val dm = resources.displayMetrics
        val w = if (root.width > 0) root.width else dm.widthPixels
        val h = if (root.height > 0) root.height else dm.heightPixels
        val real = android.graphics.Point().also { @Suppress("DEPRECATION") display.getRealSize(it) }
        val bottom = BottomScreen.resolve(frontendValues[BottomScreenOptions.KEY], appSettings.bottomScreen)
        // A core that turns its picture (SET_ROTATION): the second display's regions draw it unturned, so it stays on main.
        return DualScreen.pictureOn(maxOf(w, h), minOf(w, h), real.x, real.y, GameGeometry.of(geometry).rotated, bottom)
    }

    /**
     * Either display's size or the core's geometry changed: a new picture screen lays both out again ([syncDual]).
     * [geometry]: the view's, when the caller has just read it.
     */
    fun recheckPictureOn(geometry: FloatArray? = null) {
        if (dualMode == DualMode.OFF) return
        val display = presentation?.display ?: return
        if (pictureOnNow(display, geometry ?: retroView?.getGameGeometry()) != pictureOn) syncDual()
    }

    /** While the second screen is in use the main display stays landscape, either way up; then the previous orientation is back. */
    private fun lockLandscape(lock: Boolean) {
        if (lock) {
            if (orientationBeforeDual == null) orientationBeforeDual = requestedOrientation
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else {
            orientationBeforeDual?.let { requestedOrientation = it }
            orientationBeforeDual = null
        }
    }

    private fun showPresentation(display: Display) {
        val p = SecondScreenPresentation(activity, display)
        p.onSurface = { surface, width, height ->
            secondSurface = surface?.let { SecondSurface(it, width, height) }
            pushSecondSurface(force = true)   // a null waits for the GL thread to drop its EGL surface, before the Surface goes
            renderer.relayoutFrontend()
            if (surface != null) findViewById<View>(R.id.root).post { recheckPictureOn() }   // the display may have turned or resized
        }
        p.theme = panelTheme()
        p.onPanelPill = { panelUp = true; syncDual() }
        p.onTouch = { x, y, down ->
            retroView?.let { view ->
                val point = view.mapSecondaryTouch(x, y)
                if (down && point != null) view.sendPointer(point.x, point.y, true) else view.sendPointer(0f, 0f, false)
            }
        }
        // The system dismisses the window itself when the display's metrics change (or it goes): drop it, and look again.
        p.setOnDismissListener {
            if (presentation !== p) return@setOnDismissListener   // hidePresentation's own dismiss
            forgetPresentation(p)
            findViewById<View>(R.id.root).post { syncDual() }
        }
        // The display can go between the listener's report and here.
        try {
            presentationShape = shapeOf(display)
            presentation = p
            p.show()
        } catch (e: WindowManager.InvalidDisplayException) {
            NxLog.w(TAG_LOG, "second screen ${display.displayId} went before its window showed", e)
            presentation = null
            presentationShape = null
        }
    }

    @Suppress("DEPRECATION")   // the display's own metrics, not a window's
    private fun shapeOf(display: Display): DisplayShape =
        android.util.DisplayMetrics().also { display.getRealMetrics(it) }.let { DisplayShape(it.widthPixels, it.heightPixels, it.densityDpi) }

    /**
     * Hands the GL thread the second surface while it draws there ([secondDraws]: split, or the picture there), else takes
     * it away, waiting for the GL thread to drop its EGL surface. Only a change is sent: nothing held, nothing to drop (no
     * wait). A view not running yet gets the surface from the on-ready path. [force] (a surfaceChanged) sends an unchanged
     * surface again: that clears the GL thread's dropped state after an EGL failure.
     */
    fun pushSecondSurface(force: Boolean = false) {
        val view = retroView ?: return
        val want = secondSurface?.takeIf { secondDraws() }
        if ((want == pushedSecondSurface && !force) || (want != null && !view.isAttachedToWindow)) return
        view.setSecondarySurface(want?.surface, want?.width ?: 0, want?.height ?: 0)
        pushedSecondSurface = want
    }

    /** The GL thread stops drawing on the second surface (waiting for it), and nothing is cut for it. */
    private fun dropSecondSurface() {
        secondSurface = null
        pushSecondSurface()
        retroView?.secondaryRegions = emptyList()
        renderer.secondNdsRegions = emptyList()
        input.positionPen()
    }

    /**
     * [p] is no longer the second display's window: nothing is drawn on it, and a finger held on it lifts in the core (its
     * own up never comes once it is dismissed).
     */
    private fun forgetPresentation(p: SecondScreenPresentation) {
        presentation = null
        presentationShape = null
        dropSecondSurface()
        p.releaseTouch()
    }

    /** The app's theme (accent, option text size) for the panel, on either display. */
    private fun panelTheme(): @Composable (@Composable () -> Unit) -> Unit {
        val colors = nxColorsFor(appSettings.accent, appSettings.secondaryAccent)
        val optionText = appSettings.optionTextSize.ofListLabel
        return { content -> NxTheme(colors) { CompositionLocalProvider(LocalNxOptionText provides optionText) { content() } } }
    }

    /**
     * The panel follows the mode, on the touch screen ([pictureOn]'s other display): the panel over it in PANEL and
     * PANEL_UP (its discs and play time re-read), the Panel pill over the DS/3DS screen in SPLIT.
     */
    private fun syncPanel() {
        val p = presentation
        val shown = panelShown()
        val onMain = pictureOn == PictureOn.SECOND
        val content = if (shown) panelContent(gamePill = dualMode == DualMode.PANEL_UP) else null
        p?.showPanelPill(dualMode == DualMode.SPLIT && !onMain)
        p?.showPanel(content.takeIf { !onMain })
        val wasOnMain = mainPanelShown()
        showMainPanel(content.takeIf { onMain }, pill = p != null && dualMode == DualMode.SPLIT && onMain)
        if (wasOnMain != mainPanelShown()) {
            padLayout.updatePadVisibility()
            if (mainPanelShown()) input.releaseAllGameInput()   // the pad went from under the fingers: nothing it held may stick
            menuView?.layoutParams = menuLayoutParams(resources.configuration.orientation, overPanel = mainPanelShown())
        }
        // Re-read as it comes up; while up, each RA event, player, disc and setting change refreshes it.
        if (shown && !panelWasShown) {
            refreshDiscs()
        }
        panelWasShown = shown
        if (shown) refreshDualPanel() else dualPanel.value = null
        syncPanelClock()
    }

    /** The panel is up, on either display (PANEL, PANEL_UP). */
    fun panelShown(): Boolean = presentation != null && (dualMode == DualMode.PANEL || dualMode == DualMode.PANEL_UP)

    /**
     * The panel ([content]) over the whole game window on main, opaque and taking every touch there; or only the Panel
     * [pill] at the top, the game's touches passing it by. Above the game and the pad, below the in-game menu and the
     * notifications.
     */
    private fun showMainPanel(content: (@Composable () -> Unit)?, pill: Boolean) {
        mainPanel.value = content
        mainPanelPill.value = pill && content == null
        if (content == null && !pill) { mainPanelHost?.visibility = View.GONE; return }
        val host = mainPanelHost ?: FrameLayout(activity).also { host ->
            val theme = panelTheme()
            host.addView(ComposeView(activity).apply {
                setContent {
                    theme {
                        val panel = mainPanel.value
                        when {
                            panel != null -> Box(Modifier.fillMaxSize().background(LocalNxColors.current.background)
                                .windowInsetsPadding(WindowInsets.displayCutout)) { panel() }
                            // Bottom-right, where the panel's Game pill sits (clear of a cutout on those sides): the
                            // two switch pills land on one spot.
                            mainPanelPill.value -> PanelPill(
                                "Panel", Modifier.windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.End + WindowInsetsSides.Bottom)).padding(10.dp),
                            ) { panelUp = true; syncDual() }
                        }
                    }
                }
            }, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
            mainPanelHost = host
        }
        host.isClickable = content != null   // the panel's empty parts swallow touches; the pill alone lets them through
        host.layoutParams = if (content != null) {
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        } else {
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.END)
        }
        placeMainPanelHost()
        syncMainPanelHost()
    }

    /**
     * Where the main panel's host sits among the root's children: the panel right above gameContainer (under the pads,
     * which it hides anyway); the Panel pill alone right above the pads, whose right cluster would cover it in landscape,
     * and still under the menu and the notifications. Moved only when its place changed.
     */
    fun placeMainPanelHost() {
        val host = mainPanelHost ?: return
        val root = findViewById<FrameLayout>(R.id.root)
        val pillOnly = mainPanel.value == null && mainPanelPill.value
        val current = root.indexOfChild(host)
        // The other children's places as if the host were out of the root.
        fun without(i: Int) = if (current in 0 until i) i - 1 else i
        val game = without(root.indexOfChild(findViewById(R.id.gameContainer)))
        val lastPad = padLayout.padViews.maxOfOrNull { without(root.indexOfChild(it)) } ?: -1
        val target = if (pillOnly) maxOf(game, lastPad) + 1 else game + 1
        if (current == target) return
        if (current >= 0) root.removeView(host)
        root.addView(host, target)
    }

    /**
     * The main panel's host shows with the panel or the pill; but not under the open in-game menu, which then draws the
     * same header and panel in its place (with the list beside it), so the touch screen never shows two.
     */
    fun syncMainPanelHost() {
        val host = mainPanelHost ?: return
        val shown = if (mainPanel.value != null) !menuOpen else mainPanelPill.value
        host.visibility = if (shown) View.VISIBLE else View.GONE
    }

    /** The game panel, live on [dualPanel], touch-only; [gamePill]: the DS/3DS Game pill. */
    private fun panelContent(gamePill: Boolean): @Composable () -> Unit = {
        dualPanel.value?.let { GamePanel(it.copy(showGamePill = gamePill), ::onGamePanelEvent) }
    }

    /**
     * The dual-screen panel's state: the header ([panelHeader], with the achievement count while there is a set), the tabs
     * that apply, the Shortcuts (Swap disc for a multi-disc game outside netplay, Second screen, the future ones), this
     * game's achievements in the menu's order, the cheats (the host's set: one owner, as the menu's), and the Players
     * page's rows.
     */
    private fun panelState(): GamePanelState {
        val b = achievements?.takeIf { it.available }
        val summary = b?.summary()
        val list = if (b != null && summary != null) b.list().sortedBy(appSettings.raSortOrder) else emptyList()
        val players = input.playerRows()
        val tabs = GamePanelModel.tabs(
            raAvailable = list.isNotEmpty(), playerSlots = if (netplayNow != null) 0 else players?.first?.size ?: 0,
            cheats = owner.cheatsSupported && netplayNow == null,
        )
        val cheats = owner.cheatSet
        val (disc, count) = discs ?: (0 to 0)
        return GamePanelState(
            header = panelHeader().copy(ra = summary.takeIf { GamePanelTab.ACHIEVEMENTS in tabs }),
            tabs = tabs,
            tab = GamePanelModel.resolveTab(dualPanelTab, tabs),
            shortcuts = GamePanelModel.shortcuts(count, disc, secondScreens.current.value != null, appSettings.secondScreen, netplayNow != null),
            achievements = list,
            badges = b?.badges.orEmpty(),
            players = players?.first.orEmpty(),
            playerPads = players?.second.orEmpty(),
            cheats = GamePanelModel.cheatRows(cheats.entries),
            noCheatsText = GamePanelModel.noCheatsText(owner.cheatsFolder, cheats.expected),
            showGamePill = dualMode == DualMode.PANEL_UP,
        )
    }

    /** Rebuilds [dualPanel] while the dual-screen panel is up; nothing otherwise ([syncPanel] builds it as it comes up). */
    fun refreshDualPanel() {
        if (panelShown()) dualPanel.value = panelState()
    }

    /**
     * A touch on the dual-screen panel: Swap disc, Second screen, the Players and Cheats rows as the in-game menu's own effects
     * (the menu, if open over the game, follows), a tab, or the Game pill flipping the DS/3DS screen back. Achievement
     * rows ask for nothing.
     */
    private fun onGamePanelEvent(e: GamePanelEvent) {
        if (quitting) return
        when (e) {
            GamePanelEvent.SwapDisc -> swapDisc()
            is GamePanelEvent.SetSecondScreen -> {
                owner.handleMenuEffect(MenuEffect.SetSecondScreen(e.on))
                owner.publishMenuSecondScreen()
            }
            is GamePanelEvent.SelectTab -> { dualPanelTab = e.tab; refreshDualPanel() }
            is GamePanelEvent.SetPlayer -> owner.handleMenuEffect(MenuEffect.SetPlayer(e.slot, e.key))
            is GamePanelEvent.SetCheat -> owner.handleMenuEffect(MenuEffect.SetCheat(e.index, e.on))
            GamePanelEvent.ShowGame -> { panelUp = false; syncDual() }
        }
    }

    /**
     * The panel's Swap disc: the next disc through the same core call as the menu's Disc row (on the emulation thread),
     * a "Disc N of M" toast on the picture's display, and the menu's Disc row (if open) following.
     */
    private fun swapDisc() {
        val view = retroView ?: return
        if (!gameReady || resumePending || netplayNow != null) return
        val (current, count) = discs ?: return
        if (count < 2) return
        val next = GamePanelModel.nextDisc(current, count)
        discs = next to count
        lifecycleScope.launch(Dispatchers.IO) {
            runCatching { view.changeDisk(next) }.onFailure { NxLog.w("GameActivity", "disc change failed", it) }
        }
        discToast(next, count)
        owner.withMenu { it.setDiscs(next, count) }
        refreshDualPanel()
    }

    /** The Swap disc toast ("Disc N of M") on the picture's display. */
    fun discToast(index: Int, count: Int) {
        val text = GamePanelModel.discToast(index, count)
        val p = presentation
        if (p != null && pictureOn == PictureOn.SECOND) p.toast(text) else toast(text)
    }

    /**
     * Asks the core for its discs (disk-control interface: an m3u, a multi-disc PBP) on IO and hands them to the open
     * menu and the dual-screen panel. Not before the game runs, nor in netplay (no disc swaps there).
     */
    fun refreshDiscs() {
        val view = retroView?.takeIf { gameReady && netplayNow == null } ?: return
        lifecycleScope.launch {
            val (count, current) = withContext(Dispatchers.IO) {
                runCatching { view.getAvailableDisks() to view.getCurrentDisk() }
                    .onFailure { NxLog.w("GameActivity", "disc query failed", it) }.getOrNull()
            } ?: return@launch
            discs = current to count
            owner.withMenu { it.setDiscs(current, count) }
            refreshDualPanel()
        }
    }

    /**
     * Reads this game's total play time from the store on IO, once as the game starts (no running count: the header shows
     * the total before this launch), then refreshes the headers. A game with no library path (game time not recorded) keeps 0.
     */
    fun readPlayTotal() {
        val path = intent.getStringExtra(EXTRA_GAME_PATH) ?: return
        lifecycleScope.launch {
            val total = try {
                val data = withContext(Dispatchers.IO) { GameTimeService.data(activity) } ?: return@launch
                data.byPath()[path]?.totalMs ?: 0L
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                NxLog.w("GameActivity", "could not read play time", e)
                return@launch
            }
            playTotalMs = total
            refreshPanelHeader()
        }
    }

    /**
     * Both panels' header: the title, the launcher's box art (null: the console logo), the total play time before this
     * launch ([readPlayTotal]), the clock and the battery (read at the first header of each minute: the minute tick, a
     * panel coming up; other refreshes reuse them). The achievement count is each panel's own.
     */
    fun panelHeader(): GamePanelHeader {
        val now = System.currentTimeMillis()
        // A shared-storage ref as its plain path: the game screen's image loader may not have the app's ref mapper.
        val art = intent.getStringExtra(EXTRA_ART_PATH)?.let { FileRefs.artPath(it) ?: it }
        val minute = now / 60_000L
        if (minute != panelClockMinute) {
            panelClockMinute = minute
            panelClock = android.text.format.DateFormat.getTimeFormat(activity).format(java.util.Date(now))   // the phone's 12/24-hour setting
            panelBattery = batteryPercent()
        }
        return GamePanelHeader(intent.getStringExtra(EXTRA_TITLE) ?: "Paused", art, layoutTag(), playTotalMs, null, panelClock, panelBattery)
    }

    /** The battery level from the sticky battery broadcast (no receiver is kept); null when it can't be read. */
    private fun batteryPercent(): Int? {
        val i = ContextCompat.registerReceiver(activity, null, IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED)
            ?: return null
        val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        return if (level >= 0 && scale > 0) level * 100 / scale else null
    }

    /** The header again (clock, battery, the play total once read) in the open menu and the dual-screen panel. */
    private fun refreshPanelHeader() {
        owner.withMenu { it.setPanelHeader(panelHeader()) }
        refreshDualPanel()
    }

    /** While the menu is open or the dual-screen panel up (and the screen started), the header refreshes on each minute. */
    fun syncPanelClock() {
        val want = (menuOpen || panelShown()) && lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        if (!want) { panelClockJob?.cancel(); panelClockJob = null; return }
        if (panelClockJob?.isActive == true) return
        panelClockJob = lifecycleScope.launch {
            while (true) {
                delay(60_000L - System.currentTimeMillis() % 60_000L)
                refreshPanelHeader()
            }
        }
    }

    /** Stops drawing on the second display, then closes its window. */
    fun hidePresentation() {
        val p = presentation ?: return
        forgetPresentation(p)
        p.onSurface = { _, _, _ -> }
        p.onTouch = { _, _, _ -> }
        runCatching { p.dismiss() }.onFailure { NxLog.w(TAG_LOG, "dismissing the second screen failed", it) }
    }
}

/** The second display's surface and its size in px. */
private data class SecondSurface(val surface: Surface, val width: Int, val height: Int)

/** A display's real size in px and its density: a change needs a new window on it. */
private data class DisplayShape(val width: Int, val height: Int, val densityDpi: Int)
