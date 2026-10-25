package com.nxredux.mobile.runner

import android.content.res.Configuration
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
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import com.nxredux.mobile.R
import com.nxredux.mobile.options.FrontendSettings
import com.nxredux.mobile.options.N3dsOptions
import com.nxredux.mobile.options.NdsOptions
import com.nxredux.mobile.options.PadOptions
import com.nxredux.mobile.runner.GameActivity.Companion.EXTRA_CONTENT_URI
import com.nxredux.mobile.runner.GameActivity.Companion.EXTRA_FILE_NAME
import com.nxredux.mobile.runner.GameActivity.Companion.EXTRA_GAME_PATH
import com.nxredux.mobile.runner.GameActivity.Companion.EXTRA_TAG
import com.nxredux.mobile.settings.AndroidAppSettings
import com.nxredux.mobile.settings.PadLayout
import com.nxredux.mobile.settings.PadLayouts
import com.nxredux.mobile.settings.PadScreen
import com.nxredux.mobile.settings.PadShape
import com.nxredux.mobile.settings.PadSlot
import com.nxredux.mobile.shortcuts.TouchSlot
import com.nxredux.mobile.ui.nx.CLUSTER_MAX_WIDTH_SHARE
import com.nxredux.mobile.ui.nx.LocalNxColors
import com.nxredux.mobile.ui.nx.NxTheme
import com.nxredux.mobile.ui.nx.PAD_PORTRAIT_CM
import com.nxredux.mobile.ui.nx.PadMic
import com.nxredux.mobile.ui.nx.PadMotion
import com.nxredux.mobile.ui.nx.PadStickModes
import com.nxredux.mobile.ui.nx.PadThemeProvider
import com.nxredux.mobile.ui.nx.PadTools
import com.nxredux.mobile.ui.nx.PadVibrationProvider
import com.nxredux.mobile.ui.nx.TouchShortcut
import com.nxredux.mobile.ui.nx.VirtualPad
import com.nxredux.mobile.ui.nx.VirtualPadLeft
import com.nxredux.mobile.ui.nx.VirtualPadRight
import com.nxredux.mobile.ui.nx.padBandPx
import com.nxredux.mobile.ui.nx.symmetricCutoutInset
import com.swordfish.libretrodroid.NxLog

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

    /** The landscape overlay pad (the clusters over the game) is laid out: the notifications move top-centre, clear of it. */
    val overlayPadShown = mutableStateOf(false)
    // Whether the current layout was built with the portrait band hidden ([portraitPadHidden]): a change re-lays out.
    var layoutPadHidden = false
        private set
    // Portrait with the band hidden: the clusters' top, screen px (they cover from there down); null without them.
    var portraitClusterTop: Int? = null
        private set

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
        portraitClusterTop = null

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
        if (tt != null) {
            applyTabletopLayout(root, game, tt, names, portrait, orientation)
            syncN3dsCore()
            return
        }
        if (portrait) {
            // Portrait keeps the bottom pad band for a clip-on controller, so it has its placeholder and the game stays in
            // the top area. Any other controller drops it (PadBandRule): no pad, and the game area runs to the screen bottom.
            // A DS / 3DS player can hide the band (Portrait Pad): the game runs to the bottom too, under the clusters.
            val bandPx = portraitBandPx(names)
            game.layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
            ).apply { bottomMargin = bandPx; topMargin = cutoutTop }
            if (bandPx > 0) {
                val bandLayout = padLayout(PadShape.PORTRAIT)
                val pad = composePad { PadThemeProvider(padTheme) { VirtualPad(input::onPad, Modifier.fillMaxSize(), analog = true, onStick = input::onStick, profile = padProfile(), useJoystick = PadStickModes[padStickKey, padProfile().stickByDefault], onUseJoystickChange = ::onPadStickMode, onCStick = padCStick(), motion = padMotion(), latched = input.gcLatched.value, layout = bandLayout, tools = padTools(), shortcuts = host.padShortcuts, onShortcut = host::onPadShortcut, mic = padMic()) } }
                addPad(root, pad, FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, bandPx, Gravity.BOTTOM))
            } else if (layoutPadHidden && names.isEmpty()) {
                // The clusters over the lower half only: the upper screen (the touch screen, once swapped up) stays free to touch.
                // A controller hides them, as in landscape.
                val height = resources.displayMetrics.heightPixels / 2
                addOverlayClusters(root, height)
                portraitClusterTop = resources.displayMetrics.heightPixels - height
            }
            if (PadOptions.hasPortraitPadRow(layoutTag()) && PadBandRule.keepPadBand(portrait = true, names)) {
                val hidden = layoutPadHidden
                val toggle = composePad { PortraitPadToggle(hidden, if (hidden) padLandscapeAlpha.value else 1f, ::togglePortraitPad) }
                addPad(root, toggle, FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.END))
            }
        } else {
            // Landscape: full-screen game. Show the transparent corner clusters only when no controller
            // is attached; a connected controller hides them for an unobstructed screen.
            game.layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
            if (names.isEmpty()) {
                addOverlayClusters(root, FrameLayout.LayoutParams.MATCH_PARENT)
            }
        }
        game.requestLayout()
        updatePadVisibility()
        syncN3dsCore()
        menuView?.layoutParams = menuLayoutParams(orientation, overPanel = mainPanelShown())
        notificationsView?.layoutParams = menuLayoutParams(orientation)
        // The DS layouts follow the new area (gameContainer's size listener does too, once it is laid out).
        game.post { relayoutFrontend() }
    }

    /**
     * The two pad clusters over the game, [height] px tall from the bottom: the landscape pad, and portrait's with the
     * band hidden ([portraitPadHidden]).
     */
    private fun addOverlayClusters(root: FrameLayout, height: Int) {
        // Half the width each, so the two never overlap; the unit is the height's ([clusterUnit]), and only a
        // screen too narrow for two of those (the Flip's cover, portrait) fits it to the half.
        val halfW = (resources.displayMetrics.widthPixels * CLUSTER_MAX_WIDTH_SHARE).toInt()
        // The clusters overlay the game: drawn at the Pad Opacity (landscape) setting, touch targets unchanged.
        val overlayLayout = padLayout(PadShape.OVERLAY)
        val left = composePad {
            VirtualPadLeft(input::onPad, input::onStick, Modifier.fillMaxSize().clearOfCutoutBothSides(WindowInsetsSides.Start).alpha(padLandscapeAlpha.value), profile = padProfile(),
                useJoystick = PadStickModes[padStickKey, padProfile().stickByDefault], onUseJoystickChange = ::onPadStickMode, overGame = true, motion = padMotion(), latched = input.gcLatched.value, layout = overlayLayout, tools = padTools(),
                shortcuts = host.padShortcuts, onShortcut = host::onPadShortcut, mic = padMic())
        }
        val right = composePad {
            VirtualPadRight(input::onPad, Modifier.fillMaxSize().clearOfCutoutBothSides(WindowInsetsSides.End).alpha(padLandscapeAlpha.value), profile = padProfile(), overGame = true, onCStick = padCStick(), latched = input.gcLatched.value, layout = overlayLayout, tools = padTools(),
                shortcuts = host.padShortcuts, onShortcut = host::onPadShortcut)
        }
        addPad(root, left, FrameLayout.LayoutParams(halfW, height, Gravity.START or Gravity.BOTTOM))
        addPad(root, right, FrameLayout.LayoutParams(halfW, height, Gravity.END or Gravity.BOTTOM))
        overlayPadShown.value = true
    }

    /** DS / 3DS Portrait Pad is Hidden: portrait drops the band for the clusters over the game ([PadOptions.portraitPadRow]). */
    fun portraitPadHidden(): Boolean = PadOptions.portraitPadHidden(layoutTag(), frontendValues)

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
        val padH = root.height - tt.padTop
        when (tt.pad) {
            TabletopPad.PORTRAIT -> {
                val portraitLayout = padLayout(PadShape.PORTRAIT)
                val pad = composePad { PadThemeProvider(padTheme) { VirtualPad(input::onPad, Modifier.fillMaxSize(), analog = true, onStick = input::onStick, profile = padProfile(), useJoystick = PadStickModes[padStickKey, padProfile().stickByDefault], onUseJoystickChange = ::onPadStickMode, onCStick = padCStick(), motion = padMotion(), latched = input.gcLatched.value, layout = portraitLayout, shortcuts = host.padShortcuts, onShortcut = host::onPadShortcut, mic = padMic()) } }
                addPad(root, pad, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, padH, Gravity.BOTTOM))
            }
            TabletopPad.CLUSTERS -> {
                val halfW = (root.width * CLUSTER_MAX_WIDTH_SHARE).toInt()
                val left = composePad {
                    VirtualPadLeft(input::onPad, input::onStick, Modifier.fillMaxSize().clearOfCutout(WindowInsetsSides.Start), profile = padProfile(),
                        useJoystick = PadStickModes[padStickKey, padProfile().stickByDefault], onUseJoystickChange = ::onPadStickMode, motion = padMotion(), latched = input.gcLatched.value,
                        shortcuts = host.padShortcuts, onShortcut = host::onPadShortcut, mic = padMic())
                }
                val right = composePad {
                    VirtualPadRight(input::onPad, Modifier.fillMaxSize().clearOfCutout(WindowInsetsSides.End), profile = padProfile(), onCStick = padCStick(), latched = input.gcLatched.value,
                        shortcuts = host.padShortcuts, onShortcut = host::onPadShortcut)
                }
                addPad(root, left, FrameLayout.LayoutParams(halfW, padH, Gravity.START or Gravity.BOTTOM))
                addPad(root, right, FrameLayout.LayoutParams(halfW, padH, Gravity.END or Gravity.BOTTOM))
            }
            TabletopPad.NONE -> Unit
        }
        game.requestLayout()
        updatePadVisibility()
        menuView?.layoutParams = menuLayoutParams(orientation, overPanel = mainPanelShown())
        notificationsView?.layoutParams = menuLayoutParams(orientation)
        game.post { relayoutFrontend() }
    }

    /** The tabletop split for the current [posture] and root size, or null (flat, a DS game, root not laid out yet). */
    fun tabletop(names: List<String> = layoutControllerNames()): TabletopLayout? {
        if (posture == Posture.Flat) return null
        val root = findViewById<FrameLayout>(R.id.root)
        if (root.width <= 0 || root.height <= 0) return null
        return tabletopLayout(posture, root.width, root.height, cutoutTop, controllerConnected = names.isNotEmpty(), isDs = isNds)
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
     * The portrait pad band's height: the [PAD_PORTRAIT_CM] band, or 0 when [PadBandRule] drops it (a non-clip-on
     * controller, [names] from [layoutControllerNames]) or the player hid it ([portraitPadHidden]).
     */
    fun portraitBandPx(names: List<String> = layoutControllerNames()): Int {
        if (!PadBandRule.keepPadBand(portrait = true, names) || portraitPadHidden()) return 0
        val dm = resources.displayMetrics
        return padBandPx(PAD_PORTRAIT_CM, dm.ydpi, dm.density, dm.heightPixels.toFloat()).toInt()
    }

    /** Portrait keeps the game below a top camera cutout; landscape leaves the (centred) picture full-screen. */
    fun applyGameTopMargin(orientation: Int) {
        if (tabletop() != null) {   // the tabletop game area starts at the cutout too: lay it out again whole
            applyLayout(orientation)
            return
        }
        val game = findViewById<FrameLayout>(R.id.gameContainer)
        val lp = game.layoutParams as? FrameLayout.LayoutParams ?: return
        val top = if (orientation == Configuration.ORIENTATION_PORTRAIT) cutoutTop else 0
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
        padViews.forEach { it.visibility = if (hide) View.GONE else View.VISIBLE }
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
 * Keeps an over-the-game cluster clear of a camera cutout on EITHER edge: pads its own screen edge ([side],
 * Start or End) by [symmetricCutoutInset] of the cutout's left and right insets, so both clusters sit the same
 * distance from their edges and their half-width boxes stay mirrored.
 */
@Composable
private fun Modifier.clearOfCutoutBothSides(side: WindowInsetsSides): Modifier {
    val density = LocalDensity.current
    val dir = LocalLayoutDirection.current
    val cutout = WindowInsets.displayCutout
    val inset = with(density) {
        symmetricCutoutInset(cutout.getLeft(density, dir).toFloat(), cutout.getRight(density, dir).toFloat()).toDp()
    }
    return if (side == WindowInsetsSides.Start) padding(start = inset) else padding(end = inset)
}
