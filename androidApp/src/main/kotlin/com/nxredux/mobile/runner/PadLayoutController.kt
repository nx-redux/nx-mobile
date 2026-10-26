package com.nxredux.mobile.runner

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
import com.nxredux.mobile.layout.DsKind
import com.nxredux.mobile.layout.LayoutConstants
import com.nxredux.mobile.layout.PadRoom
import com.nxredux.mobile.layout.Reach
import com.nxredux.mobile.layout.TabletopDsPlacement
import com.nxredux.mobile.layout.bandAwaitsFrame
import com.nxredux.mobile.layout.besideMeasures
import com.nxredux.mobile.layout.besidePlacement
import com.nxredux.mobile.layout.consoleScale
import com.nxredux.mobile.layout.hideOffered
import com.nxredux.mobile.layout.overlayHeightPx
import com.nxredux.mobile.layout.reach
import com.nxredux.mobile.layout.tabletopDs
import com.nxredux.mobile.layout.tabletopDsUnitPx
import com.nxredux.mobile.options.FrontendSettings
import com.nxredux.mobile.options.N3dsOptions
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
import com.nxredux.mobile.ui.nx.PadGroupBoundsListener
import com.nxredux.mobile.ui.nx.PadMotion
import com.nxredux.mobile.ui.nx.PadStickModes
import com.nxredux.mobile.ui.nx.PadThemeProvider
import com.nxredux.mobile.ui.nx.PadTools
import com.nxredux.mobile.ui.nx.PadVibrationProvider
import com.nxredux.mobile.ui.nx.PortraitOverlayPad
import com.nxredux.mobile.ui.nx.TouchShortcut
import com.nxredux.mobile.ui.nx.VirtualPad
import com.nxredux.mobile.ui.nx.VirtualPadLeft
import com.nxredux.mobile.ui.nx.VirtualPadRight
import com.nxredux.mobile.ui.nx.besideSecondRow
import com.nxredux.mobile.ui.nx.faceWidthUnits
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
    val isNds: Boolean
    val isN3ds: Boolean
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
    /** The touch shortcut buttons to draw now ([com.nxredux.mobile.ui.nx.visibleTouchShortcuts]); snapshot state, so the pads follow a change. */
    val padShortcuts: Map<TouchSlot, TouchShortcut>
    /** A touch shortcut button's press ([down]) or release. */
    fun onPadShortcut(slot: TouchSlot, down: Boolean)
    fun isLandscape(orientation: Int): Boolean
    fun isLandscape(): Boolean
    fun syncN3dsCore()
    fun mainPanelShown(): Boolean
    fun placeMainPanelHost()
    fun menuLayoutParams(orientation: Int, overPanel: Boolean = false): FrameLayout.LayoutParams
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
 * The game screen's layout around the picture: the game container's place, the on-screen pads (the portrait band, the
 * landscape and portrait-hidden clusters, the tabletop halves), the DS / 3DS portrait pad toggle, the fold's posture,
 * and which pads count as attached for it.
 */
internal class PadLayoutController(private val host: PadLayoutHost, private val input: GameInputRouter) {

    private val activity get() = host.activity
    private val resources get() = host.activity.resources
    private val intent get() = host.activity.intent
    private fun <T : View> findViewById(id: Int): T = host.activity.findViewById(id)
    private val inputManager get() = host.inputManager
    private val menuOpen get() = host.menuOpen
    private val isNds get() = host.isNds
    private val isN3ds get() = host.isN3ds
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
    private fun isLandscape(orientation: Int) = host.isLandscape(orientation)
    private fun syncN3dsCore() = host.syncN3dsCore()
    private fun mainPanelShown() = host.mainPanelShown()
    private fun placeMainPanelHost() = host.placeMainPanelHost()
    private fun menuLayoutParams(orientation: Int, overPanel: Boolean = false) = host.menuLayoutParams(orientation, overPanel)
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
    private val padButtons by lazy { AndroidAppSettings(activity).padButtons }   // Black's button set (Appearance → Pad buttons)
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
     * This game's key in [PadStickModes]: every layout (rotate, fold, tabletop, a controller coming or going) builds a
     * new pad ComposeView, so the d-pad / stick choice is held per game for the process's life, not inside the pad. The
     * library path (game time's identity) when the launcher gave one,
     * else the content URI, else tag + file name: stable across this activity's recreations, distinct between games.
     */
    private val padStickKey: String
        get() = intent.getStringExtra(EXTRA_GAME_PATH)
            ?: intent.getStringExtra(EXTRA_CONTENT_URI)
            ?: "${intent.getStringExtra(EXTRA_TAG)}/${intent.getStringExtra(EXTRA_FILE_NAME)}"

    private fun onPadStickMode(useJoystick: Boolean) { PadStickModes[padStickKey] = useJoystick }

    /**
     * A pad over the game is laid out (the landscape clusters, or portrait's overlay with the band hidden): the
     * notifications move top-centre, clear of it.
     */
    val overlayPadShown = mutableStateOf(false)
    // Whether the current layout was built with the portrait band hidden ([portraitPadHidden]): a change re-lays out.
    var layoutPadHidden = false
        private set

    // ---- The pad beside the picture (landscape) and the hidden band's overlay (portrait) ----
    /** Hides those pads after a layout until the renderer has placed the picture ([onPictureKnown]). */
    private val reveal = PadReveal()
    /** The landscape clusters beside the picture now; null without them (portrait, a controller, tabletop). */
    private var clusterViews: Pair<ComposeView, ComposeView>? = null
    /** The half-folded DS / 3DS pad halves below the hinge, beside the touch screen; null without them. */
    private var tabletopDsViews: Pair<ComposeView, ComposeView>? = null
    /** The portrait band hidden: its see-through overlay across the bottom, and that overlay's height, px. */
    private var overlayView: ComposeView? = null
    private var overlayHeight = 0
    /** The portrait band's hide / show corner icon, while it is drawn ([syncPortraitToggle]). */
    private var toggleView: ComposeView? = null
    /** Shows the pads that waited for the picture once [reveal]'s timeout has passed; one pending at a time. */
    private val revealTimeout = Runnable { updatePadVisibility() }
    /** The portrait band is shown in the current layout. */
    private var bandShown = false
    /** The portrait band's view, while it is laid out. */
    private var bandView: ComposeView? = null
    /**
     * The band waits unseen for the first frame ([bandAwaitsFrame]): Portrait Pad is Hidden and only the picture's shape
     * decides band or overlay, so a tall game never shows the band before its overlay.
     */
    private var bandWaits = false
    /** The clusters' drawn buttons ([MeasuredButtons]); each layout's own listeners ([clusterGeneration]) report here. */
    private val measured = MeasuredButtons()
    private var clusterGeneration = 0
    // What [placeClusters] last set, read in the clusters' composition: solid when clear of the picture, the inset from
    // the screen edge (the cutout beyond the margin and the centring in a roomy column), the unit and the second row.
    private val clustersClear = mutableStateOf(false)
    private val clusterInsetPx = mutableStateOf(0f)
    private val clusterUnitPx = mutableStateOf<Float?>(null)
    private val clusterSecondRow = mutableStateOf(false)
    /** The hidden band's overlay misses the picture: drawn solid ([overlayClear]). */
    val overlayClear = mutableStateOf(false)
    /**
     * How far the clusters' shoulders reach in from each screen edge while they sit beside the picture, px; null
     * otherwise. The top-centre notifications keep clear of this ([com.nxredux.mobile.ui.nx.topCentreMaxWidth]).
     */
    val shoulderReachPx = mutableStateOf<Float?>(null)
    /** The running game's frame, from the renderer once known: whether hiding the band is offered depends on its shape. */
    private var frame: CoreFrame? = null
    /** The pad layouts the current pads were built from: a change made in the editor meanwhile lays out again ([onResume]). */
    private var builtLayouts: PadLayoutMap? = null
    /** The window's portrait band ([WindowShape.portraitBandPx], whole px) the current pads were built for ([applyGameTopMargin]). */
    private var builtBandPx = 0

    // ---- Flex mode (tabletop) ----
    /** The posture the layout follows ([applyLayout]); [Posture.Flat] until a half-opened horizontal hinge is seen. */
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
        input.dpadHat.clear()
        overlayPadShown.value = false   // set again below where the landscape clusters are added
        layoutPadHidden = portraitPadHidden()
        clusterViews = null
        tabletopDsViews = null
        overlayView = null
        toggleView = null
        overlayHeight = 0
        bandShown = false
        bandView = null
        bandWaits = false
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
        val tt = tabletop(names)
        builtTabletop = tabletopBuild(tt, names)
        if (tt != null) {
            applyTabletopLayout(root, game, tt, names, portrait, orientation)
            syncN3dsCore()
            return
        }
        if (portrait) {
            // Portrait keeps the bottom pad band for a clip-on controller, so it has its placeholder and the game stays in
            // the top area. Any other controller drops it (PadBandRule): no pad, and the game area runs to the screen bottom.
            // Where hiding it is offered (DS, 3DS, a game taller than wide) the player can hide the band (Portrait Pad):
            // the game runs to the bottom too, under the overlay pad.
            val bandPx = portraitBandPx(names)
            game.layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
            ).apply { bottomMargin = bandPx; topMargin = cutoutTop }
            if (bandPx > 0) {
                val bandLayout = padLayout(PadShape.PORTRAIT)
                val pad = composePad { PadThemeProvider(padTheme, padButtons) { VirtualPad(input::onPad, Modifier.fillMaxSize(), analog = true, onStick = input::onStick, profile = padProfile(), useJoystick = PadStickModes[padStickKey, padProfile().stickByDefault], onUseJoystickChange = ::onPadStickMode, onCStick = padCStick(), motion = padMotion(), latched = input.gcLatched.value, layout = bandLayout, tools = padTools(), shortcuts = host.padShortcuts, onShortcut = host::onPadShortcut) } }
                addPad(root, pad, FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, bandPx, Gravity.BOTTOM))
                bandShown = true
                bandView = pad
                bandWaits = bandWaitsNow()
            } else if (layoutPadHidden && names.isEmpty()) {
                // One see-through pad across the bottom, its buttons the band's size. No touch cut-off under it: its buttons
                // take their own touches and the gaps pass to the game, so a DS touch screen reaching into it still answers.
                // A controller hides it, as in landscape.
                addHiddenBandOverlay(root)
            }
            syncPortraitToggle(root, names, portrait = true)
        } else {
            // Landscape: full-screen game. Show the transparent corner clusters only when no controller
            // is attached; a connected controller hides them for an unobstructed screen.
            game.layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
            if (names.isEmpty()) addBesideClusters(root)
        }
        root.removeCallbacks(revealTimeout)
        // Hidden until the renderer places the picture; the timeout shows them for a game that never draws.
        if (clusterViews != null || overlayView != null || bandWaits) startReveal(root)
        game.requestLayout()
        updatePadVisibility()
        syncN3dsCore()
        menuView?.layoutParams = menuLayoutParams(orientation, overPanel = mainPanelShown())
        notificationsView?.layoutParams = menuLayoutParams(orientation)
        // The DS layouts follow the new area (gameContainer's size listener does too, once it is laid out).
        game.post { relayoutFrontend() }
    }

    /**
     * [portrait]: the band's corner icon drawn where hiding is offered ([hideOfferedNow]) and the band rule keeps a pad
     * ([names]), else taken away; nothing else is rebuilt, so a game's first frame can add it without a new layout.
     */
    private fun syncPortraitToggle(root: FrameLayout, names: List<String>, portrait: Boolean) {
        val wanted = portrait && tabletop(names) == null &&
            hideOfferedNow() && PadBandRule.keepPadBand(portrait = true, names)
        val shown = toggleView
        if (wanted == (shown != null)) return
        if (shown != null) {
            root.removeView(shown)
            padViews -= shown
            toggleView = null
            return
        }
        val hidden = layoutPadHidden
        val toggle = composePad { PortraitPadToggle(hidden, if (hidden) padLandscapeAlpha.value else 1f, ::togglePortraitPad) }
        addPad(root, toggle, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.END))
        toggleView = toggle
    }

    /**
     * The landscape clusters, one at each side edge, with the rows of a pad beside the picture. Their size and place come
     * from the picture once the renderer knows it ([placeClusters]); until then they are laid out at half the width each
     * and kept invisible ([reveal]), so they can be measured but never flash at that size.
     */
    private fun addBesideClusters(root: FrameLayout) {
        val halfW = (root.width.takeIf { it > 0 } ?: resources.displayMetrics.widthPixels) / 2
        clusterViews = addClusterHalves(
            root,
            FrameLayout.LayoutParams(halfW, FrameLayout.LayoutParams.MATCH_PARENT, Gravity.START or Gravity.BOTTOM),
            FrameLayout.LayoutParams(halfW, FrameLayout.LayoutParams.MATCH_PARENT, Gravity.END or Gravity.BOTTOM),
        )
        clustersClear.value = false
        clusterInsetPx.value = 0f
        clusterUnitPx.value = null
        overlayPadShown.value = true
    }

    /**
     * The two halves of a pad beside the picture (the landscape rows: MENU and the stick toggle left, SELECT START right,
     * the second row under them), at [leftLp] and [rightLp]: see-through over the game ([clusterAlpha]), sized by
     * [clusterUnitPx] and [clusterSecondRow], their drawn groups reported to [measured]. Their buttons take their own
     * touches; the gaps pass to the game below.
     */
    private fun addClusterHalves(root: FrameLayout, leftLp: FrameLayout.LayoutParams, rightLp: FrameLayout.LayoutParams): Pair<ComposeView, ComposeView> {
        val overlayLayout = padLayout(PadShape.OVERLAY)
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
                useJoystick = PadStickModes[padStickKey, padProfile().stickByDefault], onUseJoystickChange = ::onPadStickMode, overGame = true, motion = padMotion(), latched = input.gcLatched.value, layout = overlayLayout, tools = padTools(),
                shortcuts = host.padShortcuts, onShortcut = host::onPadShortcut, onGroupBounds = leftBounds,
                besidePicture = true, unitOverride = clusterUnitPx.value?.let { with(density) { it.toDp() } }, secondRow = clusterSecondRow.value)
        }
        val right = composePad {
            val density = LocalDensity.current
            VirtualPadRight(input::onPad, Modifier.fillMaxSize().padding(end = with(density) { clusterInsetPx.value.toDp() }).alpha(clusterAlpha()), profile = padProfile(), overGame = true, onCStick = padCStick(), latched = input.gcLatched.value, layout = overlayLayout, tools = padTools(),
                shortcuts = host.padShortcuts, onShortcut = host::onPadShortcut, onGroupBounds = rightBounds,
                besidePicture = true, unitOverride = clusterUnitPx.value?.let { with(density) { it.toDp() } }, secondRow = clusterSecondRow.value)
        }
        addPad(root, left, leftLp)
        addPad(root, right, rightLp)
        return left to right
    }

    /**
     * Half-folded DS / 3DS on the Fold, no controller: the pad halves below the hinge at the cover screen's button size
     * ([tabletopDsPad]), placed beside the touch screen ([placeTabletopDs]). Hidden until the picture and the drawn
     * buttons are known ([reveal]), as the landscape clusters.
     */
    private fun addTabletopDsHalves(root: FrameLayout, tt: TabletopLayout) {
        fun lp(side: Int) = FrameLayout.LayoutParams(0, 0, side or Gravity.TOP)
        tabletopDsViews = addClusterHalves(root, lp(Gravity.START), lp(Gravity.END))
        val shape = activity.windowShape()
        val pad = tabletopDsPad(shape)
        clusterUnitPx.value = pad.unitPx
        clusterSecondRow.value = pad.secondRow
        placeTabletopDs(tabletopDsPlacement(shape, tt, pad))
    }

    /**
     * The clusters' alpha: solid while they clear the picture, else Pad Opacity (landscape). Read in composition. The
     * half-folded DS / 3DS halves are always solid: the touch screen overlaps them by a quarter of their reach at most.
     */
    private fun clusterAlpha(): Float = if (clustersClear.value || tabletopDsViews != null) 1f else padLandscapeAlpha.value

    /**
     * A drawn group moved or resized: a pad width that changed by 2 px or more lays the picture out again (half-folded,
     * the halves move with the touch screen it sizes).
     */
    private fun onMeasured() {
        if (!measured.accept()) return
        NxLog.i(TAG_LOG, "pad measured: buttons ${measured.accepted} px wide")
        findViewById<FrameLayout>(R.id.root).post {
            if (tabletopDsViews != null) tabletop()?.let { placeTabletopDs(tabletopDsPlacement(activity.windowShape(), it)) }
            relayoutFrontend()
        }
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
            shape.cutoutLeft.toFloat(), shape.cutoutRight.toFloat(), secondRow, measured.accepted, faceWidthUnits(padProfile()),
            systemRowPills(padProfile(), padMotion() != null),
        )
        return LandscapePad(shape, m.unitPx, m.reach, secondRow, measured.accepted != null)
    }

    /**
     * The half-folded DS / 3DS pad's unit (the cover screen's button size, [tabletopDsUnitPx]) and reach in the window
     * [shape]: the halves' drawn buttons once both have reported ([MeasuredButtons]), else the estimate.
     */
    fun tabletopDsPad(shape: WindowShape = activity.windowShape()): LandscapePad {
        val unit = tabletopDsUnitPx(shape.ydpi, shape.density)
        val secondRow = besideSecondRow(padTools(), host.padShortcuts, placeholders = false)
        val drawn = measured.accepted.takeIf { tabletopDsViews != null }
        val r = reach(
            unit, shape.density, shape.cutoutLeft.toFloat(), shape.cutoutRight.toFloat(), measuredButtonsPx = drawn,
            faceWidthUnits(padProfile()), systemRowPills(padProfile(), padMotion() != null),
        )
        return LandscapePad(shape, unit, r, secondRow, measured = drawn != null)
    }

    /**
     * Where half-folded [tt] puts this DS / 3DS game's screens and pad halves in the window [shape] ([tabletopDs], window
     * px): the hinge from [tt], the [pad]'s unit, reach and second row, no halves with a controller in the layout.
     */
    fun tabletopDsPlacement(shape: WindowShape, tt: TabletopLayout, pad: LandscapePad = tabletopDsPad(shape)): TabletopDsPlacement =
        tabletopDs(
            shape.widthPx, shape.heightPx, shape.screenClass, shape.cutoutTop, hingeTop = tt.menuBottom, hingeBottom = tt.padTop,
            if (isN3ds) DsKind.N3DS else DsKind.DS, pad.unitPx, pad.reach, shape.density,
            controller = layoutControllerNames().isNotEmpty(), secondRow = pad.secondRow,
        )

    /** The half-folded pad halves at [p]'s boxes ([tabletopDsPadBoxes]); always solid ([clusterAlpha]). */
    private fun placeTabletopDs(p: TabletopDsPlacement) {
        val (left, right) = tabletopDsViews ?: return
        val box = tabletopDsPadBoxes(p, rootSize().second) ?: return
        clusterInsetPx.value = box.insetPx
        setBox(left, Gravity.START or Gravity.TOP, box.widthPx, box.heightPx, box.topMarginPx)
        setBox(right, Gravity.END or Gravity.TOP, box.widthPx, box.heightPx, box.topMarginPx)
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
        clustersClear.value = picture != null && p.clear
        clusterInsetPx.value = p.extraInsetPx
        clusterUnitPx.value = pad.unitPx
        clusterSecondRow.value = pad.secondRow
        shoulderReachPx.value = shoulderReach(pad.unitPx, LayoutConstants.CLUSTER_PADDING_DP * pad.shape.density + p.extraInsetPx)
        val w = p.boxWidthPx.roundToInt()
        val h = p.boxHeightPx.roundToInt()
        val top = p.topMarginPx.roundToInt()
        setBox(left, Gravity.START or Gravity.TOP, w, h, top)
        setBox(right, Gravity.END or Gravity.TOP, w, h, top)
    }

    /**
     * Portrait with the band hidden: [PortraitOverlayPad] across the bottom, its buttons at the band's unit and middle-row
     * scale ([bandUnits]), [overlayHeightPx] tall; drawn solid while the picture stops above it ([overlayClear]).
     */
    private fun addHiddenBandOverlay(root: FrameLayout) {
        val shape = activity.windowShape()
        val units = bandUnits(shape)
        val height = overlayHeightPx(units.unitPx, units.widthUnits, units.heightUnits, shape.density).roundToInt()
        val pad = composePad {
            val unitDp = with(LocalDensity.current) { units.unitPx.toDp() }
            PortraitOverlayPad(
                input::onPad, input::onStick, unitDp, Modifier.fillMaxSize().alpha(if (overlayClear.value) 1f else padLandscapeAlpha.value),
                analog = true, profile = padProfile(), useJoystick = PadStickModes[padStickKey, padProfile().stickByDefault], onUseJoystickChange = ::onPadStickMode,
                latched = input.gcLatched.value, shortcuts = host.padShortcuts, onShortcut = host::onPadShortcut,
                bandWidthUnits = units.widthUnits, bandHeightUnits = units.heightUnits, tools = padTools(), onCStick = padCStick(),
            )
        }
        addPad(root, pad, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, height, Gravity.BOTTOM))
        overlayView = pad
        overlayHeight = height
        overlayClear.value = false
        overlayPadShown.value = true
    }

    /**
     * The band this window shows ([WindowShape.portraitBandPx]) in its own button units: the portrait pad's unit inside
     * its 16 dp padding (the width over 9 or the height over 6.2, as [VirtualPad] sizes it) and the band's size in it.
     */
    private fun bandUnits(shape: WindowShape): BandUnits {
        val pad = LayoutConstants.CLUSTER_PADDING_DP * shape.density
        val w = shape.widthPx - 2f * pad
        val h = shape.portraitBandPx - 2f * pad
        val unit = minOf(w / 9f, h / 6.2f)
        return BandUnits(unit, w / unit, h / unit)
    }

    private data class BandUnits(val unitPx: Float, val widthUnits: Float, val heightUnits: Float)

    /** The hidden band's overlay height, px; 0 without it. */
    fun hiddenOverlayHeightPx(): Float = overlayHeight.toFloat()

    /** What the on-screen pad takes from the game area now, which decides the picture's space. */
    fun padRoom(): PadRoom = when {
        clusterViews != null -> PadRoom.BESIDE
        overlayView != null -> PadRoom.HIDDEN_BAND
        bandShown -> PadRoom.BAND
        else -> PadRoom.NONE
    }

    /** The pads of a new layout (or a band that starts waiting) wait for the picture ([reveal]), the timeout armed. */
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

    /** The band would wait unseen for the first frame now ([bandAwaitsFrame]). */
    private fun bandWaitsNow(): Boolean =
        bandAwaitsFrame(PadOptions.portraitPadHidden(layoutTag(), frontendValues), consoleScale(layoutTag()), frame)

    /**
     * The frontend values changed (the menu, the corner icon, or the launch's values after onCreate's layout, which had
     * the defaults): a Portrait Pad flip lays out again (posted: the icon's own view is still dispatching its long
     * press); else a shown band starts or stops waiting for the first frame ([bandGate]).
     */
    fun onFrontendValues() {
        val root = findViewById<FrameLayout>(R.id.root)
        when (bandGate(layoutPadHidden, portraitPadHidden(), bandView != null, bandWaits, bandWaitsNow())) {
            BandGate.RELAYOUT -> root.post { applyLayout(resources.configuration.orientation) }
            BandGate.START_WAIT -> { bandWaits = true; startReveal(root); updatePadVisibility() }
            BandGate.STOP_WAIT -> { bandWaits = false; updatePadVisibility() }
            BandGate.NONE -> Unit
        }
    }

    /**
     * The renderer placed the picture from the core's geometry: the pads waiting for it ([reveal]) show. The clusters wait
     * for their measured width too ([onMeasured] lays out again then), so the picture does not step once they show.
     */
    fun onPictureKnown() {
        if (reveal.shouldShow(SystemClock.uptimeMillis())) return
        if ((clusterViews != null || tabletopDsViews != null) && measured.accepted == null) return
        // The first frame flipped band / overlay in portrait ([onFrame]): the pads of this layout never show, the next do.
        if (!isLandscape(resources.configuration.orientation) && portraitPadHidden() != layoutPadHidden) return
        reveal.onPictureKnown()
        updatePadVisibility()
    }

    /**
     * The running game's [next] frame (each layout with a known geometry): when it changes whether hiding the band is
     * offered (a picture taller than wide) in portrait, the corner icon follows, and the band too when it is set hidden.
     */
    fun onFrame(next: CoreFrame) {
        val before = hideOfferedNow()
        frame = next
        // Landscape builds nothing from it; portrait rebuilds only when the band / overlay choice flips, else just the icon.
        if (hideOfferedNow() == before || isLandscape(resources.configuration.orientation)) return
        val root = findViewById<FrameLayout>(R.id.root)
        root.post {
            if (portraitPadHidden() != layoutPadHidden) applyLayout(resources.configuration.orientation)
            else {
                syncPortraitToggle(root, layoutControllerNames(), portrait = !isLandscape(resources.configuration.orientation))
                updatePadVisibility()
            }
        }
    }

    /** Hiding the portrait band is offered for this game ([hideOffered]: DS / 3DS, or a picture taller than wide). */
    private fun hideOfferedNow(): Boolean =
        PadOptions.hasPortraitPadRow(layoutTag()) && hideOffered(consoleScale(layoutTag()), frame)

    /** Back in front: a pad layout changed in the editor meanwhile builds the pads again. */
    fun onResume() {
        val built = builtLayouts ?: return
        if (AndroidAppSettings(activity).padLayouts != built) applyLayout(resources.configuration.orientation)
    }

    /** Portrait Pad is Hidden and offered for this game ([hideOfferedNow]): portrait drops the band for the overlay pad. */
    fun portraitPadHidden(): Boolean = PadOptions.portraitPadHidden(layoutTag(), frontendValues) && hideOfferedNow()

    /**
     * The corner icon's long press: the portrait band hidden or back, applied now and saved for the console (and the
     * game's own file, when it has one, so the change is not hidden by it).
     */
    private fun togglePortraitPad() {
        val key = PadOptions.PORTRAIT_PAD
        if (key in lockedOptions) { toast("Portrait Pad is locked in the config"); return }
        val next = if (portraitPadHidden()) PadOptions.SHOWN else PadOptions.HIDDEN
        input.releaseAllGameInput()   // the pad goes away under the fingers: nothing it held may stick in the core
        applyFrontend(frontendValues + (key to next))
        host.persistConsoleKey(key, next, "Could not save Portrait Pad")
    }

    /**
     * Flex mode: the game in [tt]'s area above the hinge, the controls (portrait pad or the two clusters, drawn solid
     * since they do not cover the game here) filling the half below it, and nothing there with a controller attached.
     */
    private fun applyTabletopLayout(
        root: FrameLayout, game: FrameLayout, tt: TabletopLayout, names: List<String>, portrait: Boolean, orientation: Int,
    ) {
        game.layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, tt.gameBottom - tt.gameTop, Gravity.TOP)
            .apply { topMargin = tt.gameTop }
        val (rootW, rootH) = rootSize()
        val padH = rootH - tt.padTop
        when (tt.pad) {
            TabletopPad.PORTRAIT -> {
                val portraitLayout = padLayout(PadShape.PORTRAIT)
                val pad = composePad { PadThemeProvider(padTheme, padButtons) { VirtualPad(input::onPad, Modifier.fillMaxSize(), analog = true, onStick = input::onStick, profile = padProfile(), useJoystick = PadStickModes[padStickKey, padProfile().stickByDefault], onUseJoystickChange = ::onPadStickMode, onCStick = padCStick(), motion = padMotion(), latched = input.gcLatched.value, layout = portraitLayout, shortcuts = host.padShortcuts, onShortcut = host::onPadShortcut) } }
                addPad(root, pad, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, padH, Gravity.BOTTOM))
            }
            TabletopPad.CLUSTERS -> {
                val halfW = (rootW * CLUSTER_MAX_WIDTH_SHARE).toInt()
                val left = composePad {
                    VirtualPadLeft(input::onPad, input::onStick, Modifier.fillMaxSize().clearOfCutout(WindowInsetsSides.Start), profile = padProfile(),
                        useJoystick = PadStickModes[padStickKey, padProfile().stickByDefault], onUseJoystickChange = ::onPadStickMode, motion = padMotion(), latched = input.gcLatched.value,
                        shortcuts = host.padShortcuts, onShortcut = host::onPadShortcut)
                }
                val right = composePad {
                    VirtualPadRight(input::onPad, Modifier.fillMaxSize().clearOfCutout(WindowInsetsSides.End), profile = padProfile(), onCStick = padCStick(), latched = input.gcLatched.value,
                        shortcuts = host.padShortcuts, onShortcut = host::onPadShortcut)
                }
                addPad(root, left, FrameLayout.LayoutParams(halfW, padH, Gravity.START or Gravity.BOTTOM))
                addPad(root, right, FrameLayout.LayoutParams(halfW, padH, Gravity.END or Gravity.BOTTOM))
            }
            // The halves beside the touch screen; a controller leaves the bottom half to the touch screen alone.
            TabletopPad.DS -> if (names.isEmpty()) addTabletopDsHalves(root, tt)
            TabletopPad.NONE -> Unit
        }
        root.removeCallbacks(revealTimeout)
        if (tabletopDsViews != null) startReveal(root)
        game.requestLayout()
        updatePadVisibility()
        menuView?.layoutParams = menuLayoutParams(orientation, overPanel = mainPanelShown())
        notificationsView?.layoutParams = menuLayoutParams(orientation)
        game.post { relayoutFrontend() }
    }

    /** The tabletop split for the current [posture] and root size ([rootSize]), or null (flat, no size yet). */
    fun tabletop(names: List<String> = layoutControllerNames()): TabletopLayout? {
        if (posture == Posture.Flat) return null
        val (w, h) = rootSize()
        if (w <= 0 || h <= 0) return null
        return tabletopLayout(posture, w, h, activity.windowShape().screenClass, cutoutTop, controllerConnected = names.isNotEmpty(), isDualScreenConsole = isNds || isN3ds)
    }

    /**
     * The root's size, or the window's before the root is laid out (onCreate's layout): the root fills the window (the
     * game draws behind the system bars), so a seeded tabletop ([seedPosture]) splits it right from the first layout.
     */
    private fun rootSize(): Pair<Int, Int> {
        val root = findViewById<FrameLayout>(R.id.root)
        if (root.width > 0 && root.height > 0) return root.width to root.height
        val bounds = WindowMetricsCalculator.getOrCreate().computeCurrentWindowMetrics(activity).bounds
        return bounds.width() to bounds.height()
    }

    /** What the current layout was built for ([applyLayout]): a change while half-folded lays it out again ([collectPosture]). */
    private var builtTabletop: TabletopBuild? = null

    private fun tabletopBuild(tt: TabletopLayout?, names: List<String>): TabletopBuild {
        val (w, h) = rootSize()
        return TabletopBuild(tt != null, names.isNotEmpty(), w, h)
    }

    /**
     * Whether the pad drawn below the hinge of half-folded [tt]: none for [TabletopPad.NONE], nor for the DS / 3DS halves
     * with a controller in the layout (the touch screen has the bottom half to itself).
     */
    fun tabletopPadDrawn(tt: TabletopLayout): Boolean = when (tt.pad) {
        TabletopPad.NONE -> false
        TabletopPad.DS -> layoutControllerNames().isEmpty()
        TabletopPad.PORTRAIT, TabletopPad.CLUSTERS -> true
    }

    /**
     * Before onCreate's first [applyLayout]: starts from the menu's posture ([PostureTracker.seed]), so a game launched
     * half-folded builds the tabletop layout first instead of the flat one the hinge reading would replace a moment later.
     */
    fun seedPosture() {
        posture = postureTracker.seed()
        if (posture != Posture.Flat) NxLog.i(TAG_LOG, "posture: $posture seeded from the menu")
    }

    /**
     * Starts [postureTracker], and re-reads the posture when the root's size changes (the first report can come before
     * the root is laid out; a rotation resizes it). Each posture change re-runs [applyLayout] ([onPosture]); a tabletop
     * layout built for anything else ([tabletopRelayoutNeeded]: another size, as a seeded one taken from the window's
     * size before the root's) is laid out again.
     */
    fun collectPosture() {
        val root = findViewById<FrameLayout>(R.id.root)
        root.addOnLayoutChangeListener { _, l, t, r, b, ol, ot, or_, ob ->
            if (r - l != or_ - ol || b - t != ob - ot) root.post {
                postureTracker.update()
                val names = layoutControllerNames()
                if (tabletopRelayoutNeeded(builtTabletop, tabletopBuild(tabletop(names), names))) applyLayout(resources.configuration.orientation)
            }
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
     * The portrait pad band's height: the main menu's band for this window ([WindowShape.portraitBandPx]), or 0 when
     * [PadBandRule] drops it (a non-clip-on controller, [names] from [layoutControllerNames]) or the player hid it
     * ([portraitPadHidden]).
     */
    fun portraitBandPx(names: List<String> = layoutControllerNames()): Int {
        if (!PadBandRule.keepPadBand(portrait = true, names) || portraitPadHidden()) return 0
        return activity.windowShape().portraitBandPx.roundToInt()
    }

    /** Portrait keeps the game below a top camera cutout; landscape leaves the (centred) picture full-screen. */
    fun applyGameTopMargin(orientation: Int) {
        // The tabletop game area starts at the cutout too; below Android 11 the portrait band's height read the cutout
        // ([windowShape]) before it was known: lay it out again whole, unless the band comes out the same (a rotation
        // already laid out from the configuration change), when only the game's top moves.
        val portrait = orientation == Configuration.ORIENTATION_PORTRAIT
        val bandPx = activity.windowShape().portraitBandPx.roundToInt()
        if (cutoutNeedsRelayout(Build.VERSION.SDK_INT, portrait, tabletop() != null, builtBandPx, bandPx)) {
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
     * Landscape: the corner pads overlap the full-screen menu, so hide them while it is open and let touch
     * reach the menu; so does portrait with the band hidden. Portrait keeps its pad band: it sits below the menu and drives it.
     */
    fun updatePadVisibility() {
        // Tabletop keeps its controls too: they sit below the hinge, under the menu's area.
        val overGame = resources.configuration.orientation != Configuration.ORIENTATION_PORTRAIT || layoutPadHidden
        // The panel filling the game's window hides the pad too: it would cover the panel (dual-screen handhelds have buttons).
        val hide = (menuOpen && overGame && tabletop() == null) || mainPanelShown()
        // Waiting for the picture ([reveal]): invisible, not gone, so the clusters are still laid out and measured.
        val waiting = !reveal.shouldShow(SystemClock.uptimeMillis())
        val placed = listOfNotNull(
            clusterViews?.first, clusterViews?.second, tabletopDsViews?.first, tabletopDsViews?.second, overlayView, bandView?.takeIf { bandWaits },
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
     * The names of the [pads] that count as attached for the layout (the on-screen pad, the band rule, tabletop, DS touch
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

    /** The pad's small right stick: the New 3DS C-stick, or on Wii the Classic Controller's right stick only. */
    private fun padCStick(): ((Float, Float) -> Unit)? = when {
        input.gcPad -> input::onWiiRightStick   // the GameCube C-stick is the same right analog on port 0
        !isWii -> input::onCStick
        wiiPad == WiiPad.CLASSIC -> input::onWiiRightStick
        else -> null
    }
}

/**
 * The DS / 3DS portrait pad's hide / show icon in the bottom-right corner: a long press flips the band; a tap does
 * nothing, so a thumb landing on it mid-game changes nothing. The chevron points where the band goes: down to hide it,
 * up (over the game, [hidden]) to bring it back.
 */
@Composable
private fun PortraitPadToggle(hidden: Boolean, alpha: Float, onLongPress: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    val action by rememberUpdatedState(onLongPress)
    val ink = LocalNxColors.current.main
    Box(
        Modifier.windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Right + WindowInsetsSides.Bottom))
            .padding(6.dp).size(36.dp).alpha(alpha)
            .background(Color(0x66000000), CircleShape)
            .pointerInput(Unit) {
                detectTapGestures(onLongPress = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    action()
                })
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(width = 14.dp, height = 8.dp)) {
            val (tip, ends) = if (hidden) 0f to size.height else size.height to 0f
            val path = Path().apply {
                moveTo(0f, ends); lineTo(size.width / 2f, tip); lineTo(size.width, ends)
            }
            drawPath(path, ink, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

/** Keeps a landscape pad cluster clear of a camera cutout on its own screen edge ([side]). */
@Composable
private fun Modifier.clearOfCutout(side: WindowInsetsSides): Modifier =
    windowInsetsPadding(WindowInsets.displayCutout.only(side))

/**
 * Whether a new top cutout inset lays the pads out again whole: in tabletop (its split starts at the cutout), and below
 * Android 11 ([sdk]) in portrait when the window's band ([bandPx], read with the inset now known) differs from the one
 * the pads were built for ([builtBandPx]); else only the game's top margin follows, so a rotation that was laid out
 * from its configuration change is not built twice.
 */
internal fun cutoutNeedsRelayout(sdk: Int, portrait: Boolean, tabletop: Boolean, builtBandPx: Int, bandPx: Int): Boolean =
    tabletop || (sdk < 30 && portrait && bandPx != builtBandPx)
