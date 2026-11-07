package com.nxredux.mobile.runner

import com.nxredux.mobile.layout.DeviceTraits
import android.content.res.Configuration
import android.os.Build
import android.os.SystemClock
import android.view.Gravity
import android.view.InputDevice
import android.view.View
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.window.layout.WindowMetricsCalculator
import com.nxredux.mobile.R
import com.nxredux.mobile.layout.CoreFrame
import com.nxredux.mobile.skin.SkinDual
import com.nxredux.mobile.skin.SkinChoice
import com.nxredux.mobile.skin.SkinPlacement
import com.nxredux.mobile.layout.LayoutConstants
import com.nxredux.mobile.layout.PadRoom
import com.nxredux.mobile.layout.Reach
import com.nxredux.mobile.layout.besideMeasures
import com.nxredux.mobile.layout.besidePlacement
import com.nxredux.mobile.layout.consoleScale
import com.nxredux.mobile.layout.overlayHeightPx
import com.nxredux.mobile.layout.rowsOnScreen
import com.nxredux.mobile.options.FrontendSettings
import com.nxredux.mobile.options.N3dsOptions
import com.nxredux.mobile.options.NdsScreen
import com.nxredux.mobile.options.NdsOptions
import com.nxredux.mobile.options.PadOptions
import com.nxredux.mobile.options.PxRect
import com.nxredux.mobile.runner.GameActivity.Companion.EXTRA_CONTENT_URI
import com.nxredux.mobile.runner.GameActivity.Companion.EXTRA_FILE_NAME
import com.nxredux.mobile.runner.GameActivity.Companion.EXTRA_GAME_PATH
import com.nxredux.mobile.runner.GameActivity.Companion.EXTRA_TAG
import com.nxredux.mobile.settings.AndroidAppSettings
import com.nxredux.mobile.settings.PadLayout
import com.nxredux.mobile.settings.PadLayoutMap
import com.nxredux.mobile.settings.PadLayouts
import com.nxredux.mobile.settings.PadScreen
import com.nxredux.mobile.settings.PadShape
import com.nxredux.mobile.settings.PadSlot
import com.nxredux.mobile.shortcuts.TouchSlot
import com.nxredux.mobile.ui.nx.CLUSTER_MAX_WIDTH_SHARE
import com.nxredux.mobile.ui.nx.LocalNxColors
import com.nxredux.mobile.ui.nx.NxTheme
import com.nxredux.mobile.ui.nx.PadMic
import com.nxredux.mobile.ui.nx.PadGroupBoundsListener
import com.nxredux.mobile.ui.nx.PadMotion
import com.nxredux.mobile.ui.nx.PadStickModes
import com.nxredux.mobile.ui.nx.PadTools
import com.nxredux.mobile.ui.nx.PadVibrationProvider
import com.nxredux.mobile.ui.nx.PortraitOverlayPad
import com.nxredux.mobile.ui.nx.TouchShortcut
import com.nxredux.mobile.ui.nx.VirtualPadLeft
import com.nxredux.mobile.ui.nx.VirtualPadRight
import com.nxredux.mobile.ui.nx.besideSecondRow
import com.nxredux.mobile.ui.nx.landscapeClusterUnits
import com.nxredux.mobile.ui.nx.shoulderReach
import com.nxredux.mobile.ui.nx.systemRowPills
import com.swordfish.libretrodroid.NxLog
import kotlin.math.roundToInt

/**
 * What [PadLayoutController] needs from [GameActivity]: the activity for its views and resources, the game's identity,
 * the menu and panel it lays out around, and the actions a layout change triggers. Nothing here is a copy.
 */
internal interface PadLayoutHost {
    val activity: ComponentActivity
    val gameTag: String
    val inputManager: android.hardware.input.InputManager?
    val menuOpen: Boolean
    /** The second display's use: a DS / 3DS keeps its Dual screen layouts in any mode but [DualMode.OFF]. */
    val dualMode: DualMode
    val isWii: Boolean
    val wiiPad: WiiPad?
    val frontendValues: Map<String, String>
    val lockedOptions: Set<String>
    /** The in-game menu's view while it is open, and the notifications' (both are laid out with the pads). */
    val menuView: ComposeView?
    val notificationsView: ComposeView?
    /** The tag the layout goes by: the intent's until the launch sets the game's. */
    fun layoutTag(): String
    fun padProfile(): com.nxredux.mobile.ui.nx.PadProfile
    fun padMotion(): PadMotion?
    /** The DS / 3DS pad's fixed mic button ([PadMic]): null where the pad has none. */
    fun padMic(): PadMic?
    /** The touch shortcut buttons to draw now ([com.nxredux.mobile.ui.nx.visibleTouchShortcuts]); snapshot state, so the pads follow a change. */
    val padShortcuts: Map<TouchSlot, TouchShortcut>
    /** A touch shortcut button's press ([down]) or release. */
    fun onPadShortcut(slot: TouchSlot, down: Boolean)
    /** A pad skin's SAVE / LOAD (the last-used slot), REWIND (held) or FAST (toggled) button: press ([down]) or release. */
    fun onSkinShortcut(action: SkinAction, down: Boolean)
    fun isLandscape(orientation: Int): Boolean
    fun isLandscape(): Boolean
    fun syncN3dsCore()
    /** The controllers the layout goes by changed: with a second display, which one is the bottom screen may too. */
    fun onLayoutControllersChanged()
    /** A NAOMI / Atomiswave set under the Dreamcast tag ([com.nxredux.mobile.skin.DcGames.isArcadeSet]): the arcade skin, on the d-pad. */
    val dcArcadeSet: Boolean
    /** The game panel fills the whole window (it hides the pad; the menu opens over it). */
    fun mainPanelCoversWindow(): Boolean
    fun placeMainPanelHost()
    /** The skin was placed anew, or dropped: the game panel follows its glass ([DualScreenController.syncPanel]). */
    fun onSkinPlaced()
    fun menuLayoutParams(orientation: Int, overPanel: Boolean = false, glass: Boolean = false): FrameLayout.LayoutParams
    fun relayoutFrontend()
    fun applyFrontend(values: Map<String, String>)
    /** Saves [key] = [value] for the console (off the main thread, in order with the other config writes); [failure] toasts. */
    fun persistConsoleKey(key: String, value: String, failure: String)
    fun toast(msg: String): android.widget.Toast
}

/**
 * The landscape clusters' measures for one layout: the [shape] they were read from, the button unit and the [reach]
 * (px), whether both halves keep the [secondRow], and whether the reach's buttons are [measured] (else estimated).
 */
internal data class LandscapePad(val shape: WindowShape, val unitPx: Float, val reach: Reach, val secondRow: Boolean, val measured: Boolean)

/**
 * The game screen's layout around the picture: the game container's place, the on-screen pads (the landscape clusters
 * beside the picture, the see-through portrait overlay, or a pad skin), the fold's posture the skin reads for its hinge,
 * and which pads count as attached for it.
 */
internal class PadLayoutController(private val host: PadLayoutHost, private val input: GameInputRouter) {

    private val activity get() = host.activity
    private val resources get() = host.activity.resources
    private val intent get() = host.activity.intent
    private fun <T : View> findViewById(id: Int): T = host.activity.findViewById(id)
    private val inputManager get() = host.inputManager
    private val menuOpen get() = host.menuOpen
    private val isWii get() = host.isWii
    private val wiiPad get() = host.wiiPad
    private val frontendValues get() = host.frontendValues
    private val lockedOptions get() = host.lockedOptions
    private val menuView get() = host.menuView
    private val notificationsView get() = host.notificationsView
    private val gameTag get() = host.gameTag
    private fun layoutTag() = host.layoutTag()
    private fun padProfile() = host.padProfile()
    private fun padMotion() = host.padMotion()
    private fun padMic() = host.padMic()
    private fun isLandscape(orientation: Int) = host.isLandscape(orientation)
    private fun syncN3dsCore() = host.syncN3dsCore()
    private fun mainPanelShown() = host.mainPanelCoversWindow()
    private fun placeMainPanelHost() = host.placeMainPanelHost()
    private fun menuLayoutParams(orientation: Int, overPanel: Boolean = false, glass: Boolean = false) = host.menuLayoutParams(orientation, overPanel, glass)
    private fun relayoutFrontend() = host.relayoutFrontend()
    private fun applyFrontend(values: Map<String, String>) = host.applyFrontend(values)
    private fun toast(msg: String) = host.toast(msg)

    val padViews = mutableListOf<androidx.compose.ui.platform.ComposeView>()

    /** The controller names of the last [applyLayout] (logged once per change, to confirm hardware names). */
    private var loggedControllers: List<String>? = null

    /** A physical controller is attached: the in-game hints show the standard A/B art, else the on-screen pad's labels. */
    val controllerConnected = mutableStateOf(false)

    var cutoutTop = 0   // current top display-cutout inset, px (0 in landscape: the cutout is on a side)

    // The landscape pad clusters' alpha (Pad Opacity (landscape)): read inside their composition, so a menu change recomposes them.
    val padLandscapeAlpha = mutableStateOf(FrontendSettings().padLandscapeAlpha)
    // The portrait pad's palette (Settings → Appearance → Pad theme), read once per game: it is set from the menu, not in-game.
    private val padTheme by lazy { AndroidAppSettings(activity).padTheme }
    /** Appearance → Pad skin: Off, no skin, the game full screen under the see-through overlay pad. */
    private val padSkinOn by lazy { AndroidAppSettings(activity).padSkin }
    // Settings → Controls → Pad vibration, read once per game likewise; every pad ([composePad]) ticks at it.
    private val padVibration by lazy { AndroidAppSettings(activity).padVibration }

    /**
     * Controls → Pad layout: this game's console's layout for [shape] on this screen (a Fold's cover or inner screen;
     * else Shared's, else the default pad, [PadLayout.EMPTY]). Read when a pad view is built, which every rotate, fold
     * and controller change does again.
     */
    private fun padLayout(shape: PadShape): PadLayout {
        val screen = PadScreen.of(resources.configuration.smallestScreenWidthDp.toFloat())
        return PadLayouts.resolve(AndroidAppSettings(activity).padLayouts, CoreRegistry.consoleOf(intent.getStringExtra(EXTRA_TAG) ?: gameTag)?.id, PadSlot(shape, screen))
    }

    /**
     * This game's key in [PadStickModes]: every layout (rotate, fold, a controller coming or going) builds a
     * new pad ComposeView, so the d-pad / stick choice is held per game for the process's life, not inside the pad. The
     * library path (game time's identity) when the launcher gave one,
     * else the content URI, else tag + file name: stable across this activity's recreations, distinct between games.
     */
    private val padStickKey: String
        get() = intent.getStringExtra(EXTRA_GAME_PATH)
            ?: intent.getStringExtra(EXTRA_CONTENT_URI)
            ?: "${intent.getStringExtra(EXTRA_TAG)}/${intent.getStringExtra(EXTRA_FILE_NAME)}"

    private fun onPadStickMode(useJoystick: Boolean) { PadStickModes[padStickKey] = useJoystick }

    /** The stick (true) or the d-pad until the player chooses: the profile's, and a Dreamcast disc on its stick. */
    private fun stickDefault(): Boolean =
        padProfile().stickByDefault || com.nxredux.mobile.skin.DcGames.stickFirst(com.nxredux.mobile.runner.CoreRegistry.baseTag(layoutTag()), host.dcArcadeSet) ||
            padProfile().kind == com.nxredux.mobile.ui.nx.PadProfile.Kind.GC   // a GameCube disc under the Wii tag too

    /**
     * A pad over the game is laid out (the landscape clusters, or portrait's see-through overlay): the notifications
     * move top-centre, clear of it.
     */
    val overlayPadShown = mutableStateOf(false)

    // ---- The pad beside the picture (landscape) and the see-through overlay (portrait) ----
    /** Hides those pads after a layout until the renderer has placed the picture ([onPictureKnown]). */
    private val reveal = PadReveal()
    /** The landscape clusters beside the picture now; null without them (portrait, a controller, a skin). */
    private var clusterViews: Pair<ComposeView, ComposeView>? = null
    /** The pad skin's pack (Pad theme NX Carbon...), read once per game; null for the drawn pads or a pack that can't be read. */
    private val skinPack: PadSkinPack? by lazy { if (padSkinOn) padTheme.skin?.let { PadSkinPack.builtIn(activity, it) } else null }
    /** The skin's view while the skin is the layout ([applySkinLayout]), else null. */
    private var skinView: PadSkinView? = null
    /** The touch shortcuts changed ([PadLayoutHost.padShortcuts]): the skin's strip writes the new ones. */
    fun refreshSkinShortcuts() { skinView?.shortcuts = host.padShortcuts }
    /** The in-game menu is open: the skin's strip shortcuts are hidden ([PadSkinView.stripHidden]). */
    var skinStripHidden: Boolean = false
        set(value) { field = value; skinView?.stripHidden = value }
    /** The last [skinPlacement] worked out, and what it was worked out for. */
    private var skinCache: Pair<SkinKey, SkinPlacement?>? = null
    private data class SkinKey(val w: Int, val h: Int, val dpi: Int, val tablet: Boolean, val tag: String, val aspect: Float?, val hinge: Float?, val top: Int)
    /** Portrait's see-through overlay pad across the bottom, and its height, px. */
    private var overlayView: ComposeView? = null
    private var overlayHeight = 0
    /** Shows the pads that waited for the picture once [reveal]'s timeout has passed; one pending at a time. */
    private val revealTimeout = Runnable { updatePadVisibility() }
    /** The clusters' drawn buttons ([MeasuredButtons]); each layout's own listeners ([clusterGeneration]) report here. */
    private val measured = MeasuredButtons()
    private var clusterGeneration = 0
    // What [placeClusters] last set, read in the clusters' composition: solid when clear of the picture, the inset from
    // the screen edge (the cutout beyond the margin and the centring in a roomy column), the unit and the second row.
    private val clustersClear = mutableStateOf(false)
    private val clusterInsetPx = mutableStateOf(0f)
    private val clusterUnitPx = mutableStateOf<Float?>(null)
    private val clusterSecondRow = mutableStateOf(false)
    /** Beside the picture, the clusters' rows inside the window ([rowsOnScreen]); null: all of them. */
    private val clusterRowsOnScreen = mutableStateOf<Float?>(null)
    /** The portrait overlay misses the picture: drawn solid ([overlayClear]). */
    val overlayClear = mutableStateOf(false)

    /** Sets [overlayClear]. */
    fun setOverlayClear(clear: Boolean) {
        overlayClear.value = clear
    }
    /**
     * How far the clusters' shoulders reach in from each screen edge while they sit beside the picture, px; null
     * otherwise. The top-centre notifications keep clear of this ([com.nxredux.mobile.ui.nx.topCentreMaxWidth]).
     */
    val shoulderReachPx = mutableStateOf<Float?>(null)
    /** The running game's frame, from the renderer once known: a one-screen console's skin glass follows its aspect. */
    private var frame: CoreFrame? = null
    /** The pad layouts the current pads were built from: a change made in the editor meanwhile lays out again ([onResume]). */
    private var builtLayouts: PadLayoutMap? = null
    /**
     * The window's portrait band ([WindowShape.portraitBandPx], whole px) the current pads were built for: the portrait
     * overlay and a clip-on controller's empty room are sized from it ([applyGameTopMargin]).
     */
    private var builtBandPx = 0

    // ---- The fold's posture (a pad skin's hinge) ----
    /** The posture the skin follows ([skinPlacement]); [Posture.Flat] until a half-opened horizontal hinge is seen. */
    private var posture: Posture = Posture.Flat
    /** Follows the fold (and the hinge angle where the half-open state is hidden), root-relative. */
    private val postureTracker by lazy { PostureTracker(activity, area = {
        val root = findViewById<FrameLayout>(R.id.root)
        if (root.height <= 0) null else IntArray(2).also { root.getLocationInWindow(it) }[1] to root.height
    }) { next, angle -> onPosture(next, angle) } }   // lazy: first touched in onCreate ([collectPosture])

    /** Positions the game container and (re)builds the Compose pad(s) for [orientation]. */
    fun applyLayout(orientation: Int) {
        val root = findViewById<FrameLayout>(R.id.root)
        val game = findViewById<FrameLayout>(R.id.gameContainer)
        padViews.forEach { root.removeView(it) }
        padViews.clear()
        skinView?.let { root.removeView(it) }
        skinView = null
        skinShownState.value = false
        input.n3dsHotkeys.skinShown = false
        input.dpadHat.clear()
        overlayPadShown.value = false   // set again below where the landscape clusters are added
        clustersClear.value = false
        overlayClear.value = false
        clusterViews = null
        overlayView = null
        overlayHeight = 0
        shoulderReachPx.value = null
        builtLayouts = AndroidAppSettings(activity).padLayouts
        builtBandPx = activity.windowShape().portraitBandPx.roundToInt()

        val portrait = !isLandscape(orientation)
        val pads = controllers()
        val attached = pads.map { it.name }
        if (attached != loggedControllers) {
            loggedControllers = attached
            NxLog.i(TAG_LOG, "controllers: $attached")
        }
        // The hints follow any attached pad (a benched one still drives the menu).
        controllerConnected.value = pads.isNotEmpty()
        // What the layout goes by: a multiplayer game's benched pads leave the on-screen pad up for Player 1.
        val names = layoutControllerNames(pads)
        if (names != lastLayoutNames) {
            if (lastLayoutNames != null) root.post { host.onLayoutControllersChanged() }
            lastLayoutNames = names
        }
        if (skinPlacement(names) != null) {
            applySkinLayout(root, game, orientation)
            syncN3dsCore()
            host.onSkinPlaced()
            return
        }
        if (portrait) {
            // Portrait: the game full screen under the see-through overlay pad, no pad with a controller. A clip-on
            // controller keeps its room empty below the game ([portraitBandPx]), so the game is not under it.
            val bandPx = portraitBandPx(names)
            game.layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
            ).apply { bottomMargin = bandPx; topMargin = cutoutTop }
            // One see-through pad across the bottom. No touch cut-off under it: its buttons take their own touches and
            // the gaps pass to the game, so a DS touch screen reaching into it still answers.
            if (names.isEmpty()) addPortraitOverlay(root)
        } else {
            // Landscape: full-screen game. Show the transparent corner clusters only when no controller
            // is attached; a connected controller hides them for an unobstructed screen.
            game.layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
            if (names.isEmpty()) addBesideClusters(root)
        }
        root.removeCallbacks(revealTimeout)
        // Hidden until the renderer places the picture; the timeout shows them for a game that never draws.
        if (clusterViews != null || overlayView != null) startReveal(root)
        game.requestLayout()
        updatePadVisibility()
        syncN3dsCore()
        menuView?.layoutParams = menuLayoutParams(orientation, overPanel = mainPanelShown(), glass = true)
        notificationsView?.layoutParams = menuLayoutParams(orientation)
        // The DS layouts follow the new area (gameContainer's size listener does too, once it is laid out).
        game.post { relayoutFrontend() }
        host.onSkinPlaced()   // no skin now: a panel in its glass goes
    }

    /**
     * The landscape clusters, one at each side edge, with the rows of a pad beside the picture. Their size and place come
     * from the picture once the renderer knows it ([placeClusters]); until then they are laid out at half the width each
     * and kept invisible ([reveal]), so they can be measured but never flash at that size.
     */
    private fun addBesideClusters(root: FrameLayout) {
        val halfW = (root.width.takeIf { it > 0 } ?: resources.displayMetrics.widthPixels) / 2
        clusterViews = addClusterHalves(
            root, padLayout(PadShape.OVERLAY),
            FrameLayout.LayoutParams(halfW, FrameLayout.LayoutParams.MATCH_PARENT, Gravity.START or Gravity.BOTTOM),
            FrameLayout.LayoutParams(halfW, FrameLayout.LayoutParams.MATCH_PARENT, Gravity.END or Gravity.BOTTOM),
        )
        clustersClear.value = false
        clusterInsetPx.value = 0f
        clusterUnitPx.value = null
        clusterRowsOnScreen.value = null
        overlayPadShown.value = true
    }

    /**
     * The two halves of a pad beside the picture (the landscape rows: MENU and the stick toggle left, SELECT START right,
     * the second row under them), at [leftLp] and [rightLp]: see-through over the game ([clusterAlpha]), sized by
     * [clusterUnitPx] and [clusterSecondRow], their drawn groups reported to [measured]. Their buttons take their own
     * touches; the gaps pass to the game below.
     */
    private fun addClusterHalves(root: FrameLayout, overlayLayout: PadLayout, leftLp: FrameLayout.LayoutParams, rightLp: FrameLayout.LayoutParams): Pair<ComposeView, ComposeView> {
        val generation = ++clusterGeneration
        measured.clear()
        // Each half reports its groups as drawn (Pad layout editor moves included); an old pad's last reports are ignored.
        val leftBounds = PadGroupBoundsListener { group, _, pad, unitPx, placed ->
            if (generation == clusterGeneration) { measured.onLeft(group, placed, pad, unitPx); onMeasured() }
        }
        val rightBounds = PadGroupBoundsListener { group, _, pad, unitPx, placed ->
            if (generation == clusterGeneration) { measured.onRight(group, placed, pad, unitPx); onMeasured() }
        }
        val left = composePad {
            val density = LocalDensity.current
            VirtualPadLeft(input::onPad, input::onStick, Modifier.fillMaxSize().padding(start = with(density) { clusterInsetPx.value.toDp() }).alpha(clusterAlpha()), profile = padProfile(),
                useJoystick = PadStickModes[padStickKey, stickDefault()], onUseJoystickChange = ::onPadStickMode, overGame = true, motion = padMotion(), latched = input.gcLatched.value, layout = overlayLayout, tools = padTools(),
                shortcuts = host.padShortcuts, onShortcut = host::onPadShortcut, onGroupBounds = leftBounds,
                besidePicture = true, unitOverride = clusterUnitPx.value?.let { with(density) { it.toDp() } }, secondRow = clusterSecondRow.value,
                rowsOnScreen = clusterRowsOnScreen.value, mic = padMic())
        }
        val right = composePad {
            val density = LocalDensity.current
            VirtualPadRight(input::onPad, Modifier.fillMaxSize().padding(end = with(density) { clusterInsetPx.value.toDp() }).alpha(clusterAlpha()), profile = padProfile(), overGame = true, onCStick = padCStick(), latched = input.gcLatched.value, layout = overlayLayout, tools = padTools(),
                shortcuts = host.padShortcuts, onShortcut = host::onPadShortcut, onGroupBounds = rightBounds,
                besidePicture = true, unitOverride = clusterUnitPx.value?.let { with(density) { it.toDp() } }, secondRow = clusterSecondRow.value,
                rowsOnScreen = clusterRowsOnScreen.value)
        }
        addPad(root, left, leftLp)
        addPad(root, right, rightLp)
        return left to right
    }


    /** The clusters' alpha: solid while they clear the picture, else Pad Opacity (landscape). Read in composition. */
    private fun clusterAlpha(): Float = if (clustersClear.value) 1f else padLandscapeAlpha.value

    /** A drawn group moved or resized: a pad width that changed by 2 px or more lays the picture out again. */
    private fun onMeasured() {
        if (!measured.accept()) return
        NxLog.i(TAG_LOG, "pad measured: buttons ${measured.accepted} px wide")
        findViewById<FrameLayout>(R.id.root).post { relayoutFrontend() }
    }

    /**
     * The landscape clusters' unit and reach now in the window [shape] ([besideMeasures], with the measured
     * buttons once both halves have reported, else the estimate); null when no clusters are beside the picture.
     */
    fun landscapePad(shape: WindowShape = activity.windowShape()): LandscapePad? {
        if (clusterViews == null) return null
        val secondRow = besideSecondRow(padTools(), host.padShortcuts, placeholders = false)
        val m = besideMeasures(
            shape.screenClass, shape.widthPx.toFloat(), shape.heightPx.toFloat(), shape.ydpi, shape.density,
            shape.cutoutLeft.toFloat(), shape.cutoutRight.toFloat(), secondRow, measured.accepted, landscapeClusterUnits(padProfile()),
            systemRowPills(padProfile(), padMotion() != null),
        )
        return LandscapePad(shape, m.unitPx, m.reach, secondRow, measured.accepted != null)
    }

    /**
     * A 3DS under a pad skin: the screen drawn in the top glass; SWAP trades it ([swapSkinN3ds]). Lasts the session,
     * unfolds and folds included, as the Dual screen split's swap; the DS keeps its stored Big Screen instead.
     */
    var n3dsSkinBig = NdsScreen.TOP
        private set

    /** 3DS SWAP under a pad skin: the screens trade glasses and sizes. */
    fun swapSkinN3ds() {
        n3dsSkinBig = n3dsSkinBig.other
    }

    /** [v]'s layout params to a [w] x [h] box at [gravity], [top] from the root's top; untouched when already so. */
    private fun setBox(v: View, gravity: Int, w: Int, h: Int, top: Int) {
        val lp = v.layoutParams as FrameLayout.LayoutParams
        if (lp.width != w || lp.height != h || lp.gravity != gravity || lp.topMargin != top) {
            lp.width = w
            lp.height = h
            lp.gravity = gravity
            lp.topMargin = top
            v.layoutParams = lp
        }
    }

    /**
     * Sizes and places the clusters beside [picture] (root px; null: not known yet, so they stay as unplaced and
     * hidden): [besidePlacement]'s box at each side edge, its top set so the d-pad / face row centres on the screen's
     * mid-height; solid when both columns hold the reach.
     */
    fun placeClusters(picture: PxRect?, pad: LandscapePad) {
        val (left, right) = clusterViews ?: return
        val root = findViewById<FrameLayout>(R.id.root)
        val window = PxRect(0f, 0f, (root.width.takeIf { it > 0 } ?: pad.shape.widthPx).toFloat(), (root.height.takeIf { it > 0 } ?: pad.shape.heightPx).toFloat())
        // Unknown picture: as if it took the middle half, so the unit and rows are already right when it shows.
        val p = besidePlacement(window, picture, pad.unitPx, pad.reach, pad.shape.density, pad.secondRow)
        val clear = picture != null && p.clear
        clustersClear.value = clear
        clusterInsetPx.value = p.extraInsetPx
        clusterUnitPx.value = pad.unitPx
        clusterSecondRow.value = pad.secondRow
        clusterRowsOnScreen.value = p.rowsOnScreen(window.y, pad.unitPx, pad.shape.density)
        shoulderReachPx.value = shoulderReach(pad.unitPx, LayoutConstants.CLUSTER_PADDING_DP * pad.shape.density + p.extraInsetPx)
        val w = p.boxWidthPx.roundToInt()
        val h = p.boxHeightPx.roundToInt()
        val top = p.topMarginPx.roundToInt()
        setBox(left, Gravity.START or Gravity.TOP, w, h, top)
        setBox(right, Gravity.END or Gravity.TOP, w, h, top)
    }

    /**
     * Portrait: [PortraitOverlayPad] across the bottom of the full-screen game, its buttons at the window's band unit and
     * middle-row scale ([bandUnits]), [overlayHeightPx] tall; drawn solid while the picture stops above it ([overlayClear]).
     */
    private fun addPortraitOverlay(root: FrameLayout) {
        val shape = activity.windowShape()
        val units = bandUnits(shape)
        val height = overlayHeightPx(units.unitPx, units.widthUnits, units.heightUnits, shape.density).roundToInt()
        val pad = composePad {
            val unitDp = with(LocalDensity.current) { units.unitPx.toDp() }
            PortraitOverlayPad(
                input::onPad, input::onStick, unitDp, Modifier.fillMaxSize().alpha(if (overlayClear.value) 1f else padLandscapeAlpha.value),
                analog = true, profile = padProfile(), useJoystick = PadStickModes[padStickKey, stickDefault()], onUseJoystickChange = ::onPadStickMode,
                latched = input.gcLatched.value, shortcuts = host.padShortcuts, onShortcut = host::onPadShortcut,
                bandWidthUnits = units.widthUnits, bandHeightUnits = units.heightUnits, tools = padTools(), onCStick = padCStick(), mic = padMic(),
            )
        }
        addPad(root, pad, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, height, Gravity.BOTTOM))
        overlayView = pad
        overlayHeight = height
        overlayClear.value = false
        overlayPadShown.value = true
    }

    /**
     * The window's portrait band ([WindowShape.portraitBandPx]) in its own button units: the portrait pad's unit inside
     * its 16 dp padding (the width over 9 or the height over 6.2) and the band's size in it; the overlay is sized from it.
     */
    private fun bandUnits(shape: WindowShape): BandUnits {
        val pad = LayoutConstants.CLUSTER_PADDING_DP * shape.density
        val w = shape.widthPx - 2f * pad
        val h = shape.portraitBandPx - 2f * pad
        val unit = minOf(w / 9f, h / 6.2f)
        return BandUnits(unit, w / unit, h / unit)
    }

    private data class BandUnits(val unitPx: Float, val widthUnits: Float, val heightUnits: Float)

    /** The portrait overlay's height, px; 0 without it. */
    fun hiddenOverlayHeightPx(): Float = overlayHeight.toFloat()

    /** What the on-screen pad takes from the game area now, which decides the picture's space. */
    fun padRoom(): PadRoom = when {
        clusterViews != null -> PadRoom.BESIDE
        overlayView != null -> PadRoom.HIDDEN_BAND
        else -> PadRoom.NONE
    }

    /** The pads of a new layout wait for the picture ([reveal]), the timeout armed. */
    private fun startReveal(root: FrameLayout) {
        reveal.onLayoutStart(SystemClock.uptimeMillis())
        armRevealTimeout(root)
    }

    /** Posts the reveal's timeout for when it falls due (the load cap until the core runs, [onCoreRunning] re-arms it). */
    private fun armRevealTimeout(root: FrameLayout) {
        root.removeCallbacks(revealTimeout)
        reveal.timeoutInMs(SystemClock.uptimeMillis())?.let { root.postDelayed(revealTimeout, it) }
    }

    /** The core has loaded the game and runs: the waiting pads' timeout counts from now, so a slow load shows none. */
    fun onCoreRunning() {
        reveal.onCoreRunning(SystemClock.uptimeMillis())
        if (reveal.pending()) armRevealTimeout(findViewById(R.id.root))
    }

    /**
     * The renderer placed the picture from the core's geometry: the pads waiting for it ([reveal]) show. The clusters wait
     * for their measured width too ([onMeasured] lays out again then), so the picture does not step once they show.
     */
    fun onPictureKnown() {
        if (reveal.shouldShow(SystemClock.uptimeMillis())) return
        if (clusterViews != null && measured.accepted == null) return
        reveal.onPictureKnown()
        updatePadVisibility()
    }

    /** The running game's [next] frame (each layout with a known geometry): a one-screen console's skin glass follows its aspect. */
    fun onFrame(next: CoreFrame) {
        val aspectBefore = frame?.aspect
        frame = next
        // a skin that no longer fits gives way to the overlay pad
        if (skinView != null && next.aspect != aspectBefore && skinPlacement() == null)
            findViewById<FrameLayout>(R.id.root).post { applyLayout(resources.configuration.orientation) }
    }

    /** Back in front: a pad layout changed in the editor meanwhile builds the pads again. */
    fun onResume() {
        val built = builtLayouts ?: return
        if (AndroidAppSettings(activity).padLayouts != built) applyLayout(resources.configuration.orientation)
    }

    /**
     * The pad skin laid out for this window, or null where the drawn pads stay: no skin picked, a controller in the
     * layout ([names]), the second screen in use, a console the skin doesn't draw, or a window none of
     * its layouts fits ([SkinChoice]). Half-folded, the fold's middle is where the skin's hinge goes. A one-screen
     * console's glass takes its picture's aspect (4:3 until the first frame). Shows it in the skin's view as well.
     */
    fun skinPlacement(names: List<String> = layoutControllerNames()): SkinPlacement? {
        val pack = skinPack ?: return null
        val tag = layoutTag()
        val ds = NdsOptions.appliesTo(tag)
        val n3ds = N3dsOptions.appliesTo(tag)
        // with a second display: one-screen games and a DS / 3DS split keep the skin (its screen the touch screen, [SkinDual])
        if (names.isNotEmpty() || !SkinDual.skinAllowed(host.dualMode, ds, n3ds)) return null
        val variant = SkinChoice.variantFor(com.nxredux.mobile.runner.CoreRegistry.baseTag(tag), ds, host.dcArcadeSet, padProfile().kind == com.nxredux.mobile.ui.nx.PadProfile.Kind.GC, wiiPad, padProfile().cStick) ?: return null
        val (w, h) = rootSize()
        if (w <= 0 || h <= 0) return null
        val tablet = activity.windowShape().screenClass.isTablet
        val hinge = (posture as? Posture.Tabletop)?.let { (it.hingeTop + it.hingeBottom) / 2f }
        val aspect = if (ds || n3ds) null else frame?.aspect?.takeIf { it > 0f } ?: (4f / 3f)
        // the skin and its screen run under the top camera cutout too (the frame keeps its own margin; the picture is centred
        // in the glass, so it reaches the camera only when it fills the glass's height)
        val top = 0
        val key = SkinKey(w, h, resources.displayMetrics.densityDpi, tablet, tag, aspect, hinge, top)
        val placed = skinCache?.takeIf { it.first == key }?.second ?: SkinChoice.place(
            pack.manifest, w, h, key.dpi, tablet, ds || n3ds, aspect?.let { (it * 240f) to 240f }, hinge, top, DeviceTraits.canHalfFold, variant,
        ).also { skinCache = key to it }
        val moved = skinView != null && skinView?.placement != placed
        skinView?.placement = placed
        if (moved) host.onSkinPlaced()   // a new size: the panel follows its glass
        return placed
    }

    /** A finger the skin view passed on ([PadSkinView] px, the skin filling the root) to the game view in [game]. */
    private fun glassTouch(game: FrameLayout, x: Float, y: Float, down: Boolean) {
        val view = retroIn(game) ?: return
        val skin = skinView ?: return
        val a = IntArray(2).also(skin::getLocationInWindow); val b = IntArray(2).also(view::getLocationInWindow)
        view.hostTouch(x + a[0] - b[0], y + a[1] - b[1], down)
    }

    private fun retroIn(g: android.view.ViewGroup): com.swordfish.libretrodroid.GLRetroView? {
        for (i in 0 until g.childCount) when (val c = g.getChildAt(i)) {
            is com.swordfish.libretrodroid.GLRetroView -> return c
            is android.view.ViewGroup -> retroIn(c)?.let { return it }
        }
        return null
    }

    /** The skin is the layout: the game view fills the window under the skin's view, which takes the pad's touches. */
    private fun applySkinLayout(root: FrameLayout, game: FrameLayout, orientation: Int) {
        game.layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        val pack = skinPack ?: return
        val view = PadSkinView(activity, pack, input::onPad, ::onSkinAction, haptics = padVibration != com.nxredux.mobile.settings.PadVibration.OFF,
            onResized = { root.post { onSkinResized() } }, onStick = input::onStick,
            // the GameCube's C-stick, the Classic Controller's and the PlayStation's right stick: the right analog
            onCStick = { x, y -> if (input.gcPad || (isWii && wiiPad == WiiPad.CLASSIC) || psSkin()) input.onWiiRightStick(x, y) else input.onCStick(x, y) },
            onGlass = { x, y, down -> glassTouch(game, x, y, down) })
        view.useStick = PadStickModes[padStickKey, stickDefault()]   // the drawn pad's choice, shared
        view.dimmed = motionDim()
        // under the menu and the notifications, over the game
        val below = listOfNotNull(menuView, notificationsView).map { root.indexOfChild(it) }.filter { it >= 0 }.minOrNull() ?: -1
        root.addView(view, below, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        skinView = view
        view.stripHidden = skinStripHidden
        view.shortcuts = host.padShortcuts
        skinShownState.value = true
        // no stylus mode under a skin: a touch off its buttons is the touch screen (DS and 3DS); nor the 3DS's SELECT + L
        // / Left / Right chords ([N3dsHotkeys.skinShown])
        if (input.ndsStylusOn.value) input.toggleNdsStylus()
        if (input.n3dsStylusOn.value) input.toggleN3dsStylus()
        input.n3dsHotkeys.skinShown = true
        skinPlacement()
        game.requestLayout()
        updatePadVisibility()
        menuView?.layoutParams = menuLayoutParams(orientation, overPanel = mainPanelShown(), glass = true)
        notificationsView?.layoutParams = menuLayoutParams(orientation)
        game.post { relayoutFrontend() }
    }

    /** The skin's MOTION shaded while the phone's motion is off (the drawn pad dims its pill), or with no sensors to use. */
    private fun motionDim(): Set<String> = if (padMotion()?.on == true) emptySet() else setOf("MOTION")
    private var motionHeld = false   // the skin's MOTION held past the long-press time: recentred, so its release is no tap
    private val motionHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val motionHold = Runnable { motionHeld = true; padMotion()?.onLongPress?.invoke() }

    /** A PlayStation game (its skin's right stick nub is the DualShock's right analog). */
    private fun psSkin() = com.nxredux.mobile.runner.CoreRegistry.baseTag(layoutTag()) in setOf("PS", "PSX", "PSP", "PS2")

    /** MIC and SWAP are the pad's own (the DS mic toggle, swap screens); the rest are the game's ([PadLayoutHost.onSkinShortcut]). */
    private fun onSkinAction(action: SkinAction, down: Boolean) {
        action.slot?.let { host.onPadShortcut(it, down); return }   // a strip slot: the player's action, as the drawn pad's
        when (action) {
            SkinAction.MIC -> if (down) padMic()?.onTap?.invoke()
            // the 3DS's swap (held as long as the cap, as the drawn pad's SWAP pill); the DS's on press
            SkinAction.SWAP -> if (N3dsOptions.appliesTo(layoutTag())) input.onN3dsSwap(down) else if (down) input.applyNdsAction(NdsAction.SwapScreens)
            // the stick or the d-pad on a tight Dreamcast layout: the drawn pad's choice flipped (nothing held may stick;
            // the new placement lets go of the skin's own touches)
            SkinAction.STICK_SWAP -> if (down) {
                input.releaseAllGameInput()
                val next = !PadStickModes[padStickKey, stickDefault()]
                PadStickModes[padStickKey] = next
                skinView?.useStick = next
            }
            // the Wii's MOTION, as the drawn pad's pill: a tap flips the phone's motion, a hold recentres it (no tap then)
            SkinAction.MOTION -> if (down) {
                motionHeld = false
                motionHandler.postDelayed(motionHold, android.view.ViewConfiguration.getLongPressTimeout().toLong())
            } else {
                motionHandler.removeCallbacks(motionHold)
                if (!motionHeld) { padMotion()?.onTap?.invoke(); motionHandler.post { skinView?.dimmed = motionDim() } }
            }
            else -> host.onSkinShortcut(action, down)
        }
    }

    /** The skin's view changed size (insets, a resize): laid out again for it, or back to the drawn pads if none fits now. */
    private fun onSkinResized() {
        if (skinView == null) return
        if (skinPlacement() == null) applyLayout(resources.configuration.orientation) else relayoutFrontend()
    }

    private var lastLayoutNames: List<String>? = null

    /**
     * A DS or 3DS game whose pad skin will show with a second display: a pack picked, no controller in the layout, the
     * Dual screen mode allowing it and a layout set for the console. No window measure (decided before the turn to
     * landscape): the Bottom screen's Auto then means the phone ([DualScreen.pictureOn]'s skin).
     */
    fun dualSkinWanted(mode: DualMode = host.dualMode): Boolean {
        skinPack ?: return false
        val tag = layoutTag()
        val ds = NdsOptions.appliesTo(tag)
        val n3ds = N3dsOptions.appliesTo(tag)
        if (!ds && !n3ds) return false
        return layoutControllerNames().isEmpty() && SkinDual.skinAllowed(mode, ds, n3ds) &&
            SkinChoice.variantFor(com.nxredux.mobile.runner.CoreRegistry.baseTag(tag), ds, host.dcArcadeSet, padProfile().kind == com.nxredux.mobile.ui.nx.PadProfile.Kind.GC, wiiPad, padProfile().cStick) != null
    }

    /** Whether the pad skin is the layout now. */
    val skinShown: Boolean get() = skinView != null
    /** The skin's view while shown: a panel in its glass sits right above it. */
    val skinViewNow: View? get() = skinView
    /** [skinShown] as state, for the notifications (top-centre over a skin). */
    val skinShownState = mutableStateOf(false)

    /**
     * The root's size, or the window's before the root is laid out (onCreate's layout): the root fills the window (the
     * game draws behind the system bars), so a seeded posture ([seedPosture]) places the skin right from the first layout.
     */
    private fun rootSize(): Pair<Int, Int> {
        val root = findViewById<FrameLayout>(R.id.root)
        if (root.width > 0 && root.height > 0) return root.width to root.height
        val bounds = WindowMetricsCalculator.getOrCreate().computeCurrentWindowMetrics(activity).bounds
        return bounds.width() to bounds.height()
    }

    /**
     * Before onCreate's first [applyLayout]: starts from the menu's posture ([PostureTracker.seed]), so a game launched
     * half-folded places its skin at the hinge first instead of flat, which the hinge reading would replace a moment later.
     */
    fun seedPosture() {
        posture = postureTracker.seed()
        if (posture != Posture.Flat) NxLog.i(TAG_LOG, "posture: $posture seeded from the menu")
    }

    /**
     * Starts [postureTracker], and re-reads the posture when the root's size changes (the first report can come before
     * the root is laid out; a rotation resizes it). Each posture change re-runs [applyLayout] ([onPosture]).
     */
    fun collectPosture() {
        val root = findViewById<FrameLayout>(R.id.root)
        root.addOnLayoutChangeListener { _, l, t, r, b, ol, ot, or_, ob ->
            if (r - l != or_ - ol || b - t != ob - ot) root.post { postureTracker.update() }
        }
        postureTracker.start()
    }

    private fun onPosture(next: Posture, angle: Float?) {
        posture = next
        val root = findViewById<FrameLayout>(R.id.root)
        NxLog.i(TAG_LOG, "posture: $next angle=$angle (root ${root.width}x${root.height})")
        applyLayout(resources.configuration.orientation)
    }

    /**
     * The room kept empty below the game in portrait for a clip-on controller ([PadBandRule]: the band's height, nothing
     * drawn in it), else 0: with no controller the game runs to the bottom under the overlay pad.
     */
    fun portraitBandPx(names: List<String> = layoutControllerNames()): Int {
        if (names.isEmpty() || !PadBandRule.keepPadBand(portrait = true, names)) return 0
        return activity.windowShape().portraitBandPx.roundToInt()
    }

    /** Portrait keeps the game below a top camera cutout; landscape leaves the (centred) picture full-screen. */
    fun applyGameTopMargin(orientation: Int) {
        // Below Android 11 the portrait band's height (the overlay's size, a clip-on controller's room) read the cutout
        // ([windowShape]) before it was known: lay it out again whole, unless the band comes out the same (a rotation
        // already laid out from the configuration change), when only the game's top moves.
        // Under a skin the game area is the whole window like the skin's view, and the skin's screen clears the cutout
        // itself ([skinPlacement]): placed again for the new inset.
        if (skinShown) {
            onSkinResized()
            return
        }
        val portrait = orientation == Configuration.ORIENTATION_PORTRAIT
        val bandPx = activity.windowShape().portraitBandPx.roundToInt()
        if (cutoutNeedsRelayout(Build.VERSION.SDK_INT, portrait, builtBandPx, bandPx)) {
            applyLayout(orientation)
            return
        }
        val game = findViewById<FrameLayout>(R.id.gameContainer)
        val lp = game.layoutParams as? FrameLayout.LayoutParams ?: return
        val top = if (portrait) cutoutTop else 0
        if (lp.topMargin != top) {
            lp.topMargin = top
            game.layoutParams = lp
        }
    }

    /**
     * The pads are over the game (the landscape clusters, the portrait overlay), so they hide while the in-game menu is
     * open and touch reaches the menu.
     */
    fun updatePadVisibility() {
        // The panel filling the game's window hides the pad too: it would cover the panel (dual-screen handhelds have buttons).
        val hide = menuOpen || mainPanelShown()
        // Waiting for the picture ([reveal]): invisible, not gone, so the clusters are still laid out and measured.
        val waiting = !reveal.shouldShow(SystemClock.uptimeMillis())
        val placed = listOfNotNull(
            clusterViews?.first, clusterViews?.second, overlayView,
        )
        padViews.forEach { v ->
            v.visibility = when {
                hide -> View.GONE
                waiting && v in placed -> View.INVISIBLE
                else -> View.VISIBLE
            }
        }
    }

    /** The non-virtual gamepads/joysticks currently attached (wired or Bluetooth); empty for none. */
    private fun controllers(): List<InputDevice> {
        val im = inputManager ?: return emptyList()
        return im.inputDeviceIds.toList().mapNotNull { id -> im.getInputDevice(id) }.filter(input::isPad)
    }

    /** The names of every attached [controllers]. */
    fun controllerNames(): List<String> = controllers().map { it.name }

    /**
     * The names of the [pads] that count as attached for the layout (the on-screen pad, the band rule, the skin, DS touch
     * mode; the hint glyphs follow every pad): in a multiplayer game not the benched ones ([PadPresence]); one player, all.
     */
    fun layoutControllerNames(pads: List<InputDevice> = controllers()): List<String> =
        PadPresence.attached(pads, input.slots?.outOfPlay) { DeviceKey(it.descriptor) }.map { it.name }

    private fun composePad(content: @Composable () -> Unit): ComposeView =
        ComposeView(activity).apply {
            setContent {
                NxTheme { PadVibrationProvider(padVibration) { content() } }
            }
        }

    private fun addPad(root: FrameLayout, view: ComposeView, lp: FrameLayout.LayoutParams) {
        // Added after gameContainer -> overlays it; below the notifications (a rotation re-adds the pads).
        val below = notificationsView?.let { root.indexOfChild(it) }?.takeIf { it >= 0 } ?: -1
        root.addView(view, below, lp)
        padViews += view
        placeMainPanelHost()   // a Panel pill alone stays above the pads re-added here
    }

    /** The DS / 3DS pad's STYLUS and SWAP pills ([PadTools]); none on other consoles. Read in composition: the stylus pill follows its state. */
    private fun padTools(): PadTools? = when {
        N3dsOptions.appliesTo(layoutTag()) -> PadTools(input.n3dsStylusOn.value, input::toggleN3dsStylus, input::onN3dsSwap)
        NdsOptions.appliesTo(layoutTag()) -> PadTools(input.ndsStylusOn.value, input::toggleNdsStylus) { down -> if (down) input.applyNdsAction(NdsAction.SwapScreens) }
        else -> null
    }

    /** The pad's right stick: the PS2's, the New 3DS C-stick, or on Wii the Classic Controller's right stick only. */
    private fun padCStick(): ((Float, Float) -> Unit)? = when {
        padProfile().dualSticks -> input::onRightStick
        input.gcPad -> input::onWiiRightStick   // the GameCube C-stick is the same right analog on port 0
        !isWii -> input::onCStick
        wiiPad == WiiPad.CLASSIC -> input::onWiiRightStick
        else -> null
    }
}

/** Keeps a landscape pad cluster clear of a camera cutout on its own screen edge ([side]). */
@Composable
private fun Modifier.clearOfCutout(side: WindowInsetsSides): Modifier =
    windowInsetsPadding(WindowInsets.displayCutout.only(side))

/**
 * Whether a new top cutout inset lays the pads out again whole: below Android 11 ([sdk]) in portrait when the window's
 * band ([bandPx], read with the inset now known; the overlay is sized from it) differs from the one the pads were built
 * for ([builtBandPx]); else only the game's top margin follows, so a rotation that was laid out from its configuration
 * change is not built twice.
 */
internal fun cutoutNeedsRelayout(sdk: Int, portrait: Boolean, builtBandPx: Int, bandPx: Int): Boolean =
    sdk < 30 && portrait && bandPx != builtBandPx
