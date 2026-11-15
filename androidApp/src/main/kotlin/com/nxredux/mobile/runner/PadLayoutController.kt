package com.nxredux.mobile.runner

import com.nxredux.mobile.layout.DeviceTraits
import android.content.res.Configuration
import android.os.Build
import android.os.SystemClock
import android.view.InputDevice
import android.view.View
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.window.layout.WindowMetricsCalculator
import com.nxredux.mobile.R
import com.nxredux.mobile.layout.CoreFrame
import com.nxredux.mobile.options.PxRect
import com.nxredux.mobile.skin.SkinDual
import com.nxredux.mobile.skin.menuRoom
import com.nxredux.mobile.skin.menuRect
import com.nxredux.mobile.skin.GlassScreen
import com.nxredux.mobile.skin.SkinChoice
import com.nxredux.mobile.skin.OverlayMode
import com.nxredux.mobile.skin.SkinPlacement
import com.nxredux.mobile.skin.SkinTweak
import com.nxredux.mobile.skin.SkinTweaks
import com.nxredux.mobile.skin.TweakRules
import com.nxredux.mobile.skin.bare
import com.nxredux.mobile.skin.overlayMode
import com.nxredux.mobile.layout.PadRoom
import com.nxredux.mobile.options.FrontendSettings
import com.nxredux.mobile.options.N3dsOptions
import com.nxredux.mobile.options.NdsScreen
import com.nxredux.mobile.options.NdsOptions
import com.nxredux.mobile.options.NdsLayoutChoice
import com.nxredux.mobile.runner.GameActivity.Companion.EXTRA_CONTENT_URI
import com.nxredux.mobile.runner.GameActivity.Companion.EXTRA_FILE_NAME
import com.nxredux.mobile.runner.GameActivity.Companion.EXTRA_GAME_PATH
import com.nxredux.mobile.runner.GameActivity.Companion.EXTRA_TAG
import com.nxredux.mobile.settings.AndroidAppSettings
import com.nxredux.mobile.shortcuts.TouchSlot
import com.nxredux.mobile.ui.nx.PadMic
import com.nxredux.mobile.ui.nx.CornerButtons
import com.nxredux.mobile.ui.nx.cornerShortcuts
import com.nxredux.mobile.ui.nx.micCornerShown
import com.nxredux.mobile.ui.nx.NxTheme
import com.nxredux.mobile.ui.nx.PadMotion
import com.nxredux.mobile.ui.nx.PadStickModes
import com.nxredux.mobile.ui.nx.TouchShortcut
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
    /** The DS / 3DS split's Panel pill as a corner button: what its tap does; null while there is none to show. */
    fun cornerPanel(): (() -> Unit)?
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
 * The game screen's layout around the picture: the game container's place, the on-screen pad (a pad skin, or its bare
 * buttons see-through over the full-screen game), the fold's posture the skin reads for its hinge, and which pads count
 * as attached for it.
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

    /** The controller names of the last [applyLayout] (logged once per change, to confirm hardware names). */
    private var loggedControllers: List<String>? = null

    /** A physical controller is attached: the in-game hints show the standard A/B art, else the on-screen pad's labels. */
    val controllerConnected = mutableStateOf(false)

    var cutoutTop = 0   // current top display-cutout inset, px (0 in landscape: the cutout is on a side)

    // The bare buttons' alpha (Pad Opacity), in landscape and portrait alike ([syncBareAlpha]).
    val padLandscapeAlpha = mutableStateOf(FrontendSettings().padLandscapeAlpha)
    // The portrait pad's palette (Settings → Appearance → Pad theme), read once per game: it is set from the menu, not in-game.
    private val padTheme by lazy { AndroidAppSettings(activity).padTheme }
    /** Appearance → Pad skin: Off, no skin, the game full screen under the skin's see-through buttons ([bareView]). */
    private val padSkinOn by lazy { AndroidAppSettings(activity).padSkin }
    // Settings → Controls → Pad vibration, read once per game likewise; the skin's buttons tick at it.
    private val padVibration by lazy { AndroidAppSettings(activity).padVibration }

    /**
     * This game's key in [PadStickModes]: every layout (rotate, fold, a controller coming or going) builds a
     * new pad view, so the d-pad / stick choice is held per game for the process's life, not inside the pad. The
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

    /** The skin's bare buttons are laid out over the game ([addBareSkin]): the notifications move top-centre, clear of them. */
    val overlayPadShown = mutableStateOf(false)

    // ---- The bare buttons over the game ----
    /** Hides the bare buttons after a layout until the renderer has placed the picture ([onPictureKnown]). */
    private val reveal = PadReveal()
    /** The pad skin's pack (Pad theme NX Carbon...), read once per game (the whole skin or its bare buttons); null when it can't be read. */
    private val skinPack: PadSkinPack? by lazy { padTheme.skin?.let { PadSkinPack.builtIn(activity, it) } }
    /** The skin's view while the skin is the layout ([applySkinLayout]), else null. */
    private var skinView: PadSkinView? = null
    /** The skin's bare buttons over the full-screen game while they are the pad ([addBareSkin]), else null. */
    private var bareView: PadSkinView? = null
    private fun skinViews() = listOfNotNull(skinView, bareView)
    /** The corner buttons (mic, touch shortcuts) while no pad is drawn ([addCornerButtons]: a controller), else null. */
    private var cornerView: ComposeView? = null
    /** The corner buttons are in the layout ([addCornerButtons]): they carry the Panel pill then. */
    val cornerShown = mutableStateOf(false)
    /** The touch shortcuts changed ([PadLayoutHost.padShortcuts]): the skin's strip writes the new ones. */
    fun refreshSkinShortcuts() { skinViews().forEach { it.shortcuts = host.padShortcuts } }
    /** The in-game menu is open: the skin's strip shortcuts are hidden ([PadSkinView.stripHidden]). */
    var skinStripHidden: Boolean = false
        set(value) { field = value; syncStripHidden() }

    /**
     * The skin's glass the game panel sits in (a one-screen game on the fold board), else null. Its Shortcuts tab holds
     * what the strip's buttons would, so the strip is hidden there too ([PadSkinView.stripHidden]); and the in-game menu
     * opens in it, as in the panel's place on a dual screen, the picture left on the top screen ([skinMenuRoom]).
     */
    var panelSkinGlass: com.nxredux.mobile.skin.PlacedGlass? = null
        set(value) { if (field == value) return; field = value; syncStripHidden() }

    /** The strip hides for either: the menu open, or the panel in the skin's glass. */
    private fun stripHiddenNow(): Boolean = skinStripHidden || panelSkinGlass != null

    private fun syncStripHidden() {
        val hidden = stripHiddenNow()
        skinViews().forEach { it.stripHidden = hidden }
    }
    /** The in-game menu sits in the skin: stacked screens draw as one glass in the frame ([PadSkinView.underMenu]). */
    var skinUnderMenu: Boolean = false
        set(value) { field = value; skinView?.underMenu = value }
    /** The last [skinPlacement] / [barePlacement] worked out, and what it was worked out for. */
    private var skinCache: Pair<SkinKey, SkinPlacement?>? = null
    private var bareCache: Pair<SkinKey, BareLayout?>? = null
    /**
     * [variant]: the layout set (a Wii pad resolving changes it); [tweaks] / [overlayTweaks]: the stored player tweaks
     * for the skin and for its bare buttons ([SkinTweaks.decode]).
     */
    private data class SkinKey(
        val w: Int, val h: Int, val dpi: Int, val tablet: Boolean, val tag: String, val aspect: Float?, val hinge: Float?, val top: Int,
        val variant: com.nxredux.mobile.skin.SkinVariant, val tweaks: String, val overlayTweaks: String,
    )
    /** The band portrait's bare buttons take at the bottom ([skinBandHeight]), px; 0 in landscape or without them. */
    private var overlayHeight = 0
    /** Shows the pads that waited for the picture once [reveal]'s timeout has passed; one pending at a time. */
    private val revealTimeout = Runnable { updatePadVisibility() }
    /** The picture clears the bare buttons: they are drawn solid ([syncBareAlpha]). */
    val overlayClear = mutableStateOf(false)

    /**
     * Whether a bare button as drawn (its piece; not a hit area, nor an empty shortcut slot) sits over [picture]
     * (game-area px): one the player moved onto the game keeps Pad Opacity even while the picture clears the band.
     */
    fun bareOver(picture: PxRect): Boolean {
        val p = bareView?.placement ?: return false
        val game = findViewById<FrameLayout>(R.id.gameContainer)
        val top = (game.layoutParams as? FrameLayout.LayoutParams)?.topMargin ?: 0   // the bare view covers the root
        val pic = picture.copy(y = picture.y + top)
        return p.pieces.any { r -> r.dst.x < pic.x + pic.width && pic.x < r.dst.x + r.dst.width && r.dst.y < pic.y + pic.height && pic.y < r.dst.y + r.dst.height }
    }

    /**
     * The DS / 3DS touch screen as laid out ([gameRect], game-area px; null: none drawn on this display) under the bare
     * buttons: a tap there beside a button is the screen's ([PadSkinView.touchScreenArea]).
     */
    fun setBareTouchScreen(gameRect: PxRect?) {
        val view = bareView ?: return
        val top = (findViewById<FrameLayout>(R.id.gameContainer).layoutParams as? FrameLayout.LayoutParams)?.topMargin ?: 0   // the bare view covers the root
        view.touchScreenArea = gameRect?.let { it.copy(y = it.y + top) }
    }

    fun setOverlayClear(clear: Boolean) {
        overlayClear.value = clear
        syncBareAlpha()
    }
    /** The running game's frame, from the renderer once known: a one-screen console's skin glass follows its aspect. */
    private var frame: CoreFrame? = null
    /**
     * The window's portrait band ([WindowShape.portraitBandPx], whole px) the current layout was built for: a clip-on
     * controller's empty room is sized from it ([applyGameTopMargin]).
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

    /** Positions the game container and (re)builds the on-screen pad for [orientation]. */
    fun applyLayout(orientation: Int) {
        val root = findViewById<FrameLayout>(R.id.root)
        val game = findViewById<FrameLayout>(R.id.gameContainer)
        skinView?.let { root.removeView(it) }
        skinView = null
        bareView?.let { root.removeView(it) }
        bareView = null
        cornerView?.let { root.removeView(it) }
        cornerView = null
        cornerShown.value = false
        skinShownState.value = false
        input.setSkinLayouts(shown = false, layouts = false)
        input.dpadHat.clear()
        overlayPadShown.value = false   // set again below where the bare buttons are added
        overlayClear.value = false
        overlayHeight = 0
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
        val mode = overlayMode(padSkinOn, placed = skinPlacement(names) != null, controller = names.isNotEmpty())
        if (mode == OverlayMode.SKIN) {
            applySkinLayout(root, game, orientation)
            syncN3dsCore()
            host.onSkinPlaced()
            return
        }
        overlayPadLayout = mode == OverlayMode.BARE
        if (portrait) {
            // Portrait: the game full screen under the skin's see-through buttons, no pad with a controller. A clip-on
            // controller keeps its room empty below the game ([portraitBandPx]), so the game is not under it.
            val bandPx = portraitBandPx(names)
            game.layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
            ).apply { bottomMargin = bandPx; topMargin = portraitGameTop() }
        } else {
            // Landscape: full-screen game, the buttons over it only when no controller is attached.
            game.layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        }
        // No touch cut-off under the buttons: they take their own touches and the gaps pass to the game, so a DS touch
        // screen under them still answers.
        if (mode == OverlayMode.BARE) addBareSkin(root, game) else addCornerButtons(root)
        root.removeCallbacks(revealTimeout)
        // Hidden until the renderer places the picture; the timeout shows them for a game that never draws.
        if (bareView != null) startReveal(root)
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
     * A 3DS under a pad skin: the screen drawn in the top glass; SWAP trades it ([swapSkinN3ds]). Lasts the session,
     * unfolds and folds included, as the Dual screen split's swap; the DS keeps its stored Big Screen instead.
     */
    var n3dsSkinBig = NdsScreen.TOP
        private set

    /** 3DS SWAP under a pad skin: the screens trade glasses and sizes. */
    fun swapSkinN3ds() {
        n3dsSkinBig = n3dsSkinBig.other
    }

    /** The band portrait's bare buttons take at the bottom, px ([overlayHeight]); 0 without them. */
    fun hiddenOverlayHeightPx(): Float = overlayHeight.toFloat()

    /** What the on-screen pad takes from the game area now, which decides the picture's space. */
    fun padRoom(): PadRoom = when {
        bareView != null && overlayHeight > 0 -> PadRoom.HIDDEN_BAND   // portrait's bare buttons: the picture keeps above their band
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

    /** The renderer placed the picture from the core's geometry: the pads waiting for it ([reveal]) show. */
    fun onPictureKnown() {
        if (reveal.shouldShow(SystemClock.uptimeMillis())) return
        reveal.onPictureKnown()
        updatePadVisibility()
    }

    /** The running game's [next] frame (each layout with a known geometry): a one-screen console's skin glass follows its aspect. */
    fun onFrame(next: CoreFrame) {
        val aspectBefore = frame?.aspect
        frame = next
        // a skin that no longer fits gives way to its bare buttons; those are placed again for the picture
        if (skinView != null && next.aspect != aspectBefore && skinPlacement() == null)
            findViewById<FrameLayout>(R.id.root).post { applyLayout(resources.configuration.orientation) }
        if (bareView != null && next.aspect != aspectBefore && barePlacement() == null)
            findViewById<FrameLayout>(R.id.root).post { applyLayout(resources.configuration.orientation) }
    }

    /**
     * The pad skin laid out for this window, or null where its bare buttons are the pad instead ([barePlacement]): Pad
     * skin Off, no skin picked, a controller in the layout ([names]), the second screen in use, or a window none of its
     * layouts fits ([SkinChoice]). Half-folded, the fold's middle is where the skin's hinge goes. A one-screen
     * console's glass takes its picture's aspect (4:3 until the first frame). The console's player tweaks applied
     * ([SkinTweaks]). Shows it in the skin's view as well.
     */
    fun skinPlacement(names: List<String> = layoutControllerNames()): SkinPlacement? {
        if (!padSkinOn) return null
        val pack = skinPack ?: return null
        val tag = layoutTag()
        val ds = NdsOptions.appliesTo(tag)
        val n3ds = N3dsOptions.appliesTo(tag)
        // with a second display: one-screen games and a DS / 3DS split keep the skin (its screen the touch screen, [SkinDual])
        if (names.isNotEmpty() || !SkinDual.skinAllowed(host.dualMode, ds, n3ds)) return null
        val key = skinKey(tag, ds || n3ds) ?: return null
        val placed = skinCache?.takeIf { it.first == key }?.second ?: placeSkin(pack, key, ds || n3ds, closest = false).also { skinCache = key to it }
        val moved = skinView != null && skinView?.placement != placed
        skinView?.placement = placed
        if (moved) host.onSkinPlaced()   // a new size: the panel follows its glass
        return placed
    }

    /**
     * The skin's bare buttons laid out for this window ([bare]): the closest layout ([SkinChoice.placeClosest]) with the
     * console's overlay tweaks ([bareLayout]), or null with a controller in the layout
     * ([names]), no readable skin, or a window too small even for that. Shows it in the bare view as well, and in
     * portrait sets the band it takes ([overlayHeight]) from the skin's tweaks, so the picture keeps its size however
     * the overlay's buttons are moved ([bareLayout]).
     */
    fun barePlacement(names: List<String> = layoutControllerNames()): SkinPlacement? {
        val pack = skinPack ?: return null
        if (names.isNotEmpty()) return null
        val tag = layoutTag()
        val ds = NdsOptions.appliesTo(tag) || N3dsOptions.appliesTo(tag)
        val key = skinKey(tag, ds) ?: return null
        val made = bareCache?.takeIf { it.first == key }?.second ?: placeBare(pack, key, ds).also { bareCache = key to it }
        val placed = made?.placement
        bareView?.let { v ->
            v.placement = placed
            overlayHeight = if (made != null && !isLandscape(resources.configuration.orientation)) made.band else 0
        }
        return placed
    }

    /** What the skin is placed for now: the window, the fold, a one-screen console's picture aspect ([ds]: none), its layout set, the tweaks. */
    private fun skinKey(tag: String, ds: Boolean): SkinKey? {
        val (w, h) = rootSize()
        if (w <= 0 || h <= 0) return null
        val tablet = activity.windowShape().screenClass.isTablet
        val hinge = (posture as? Posture.Tabletop)?.let { (it.hingeTop + it.hingeBottom) / 2f }
        val aspect = if (ds) null else frame?.aspect?.takeIf { it > 0f } ?: (4f / 3f)
        // the skin and its screen run under the top camera cutout too (the frame keeps its own margin; the picture is centred
        // in the glass, so it reaches the camera only when it fills the glass's height)
        val top = 0
        val variant = SkinChoice.variantFor(com.nxredux.mobile.runner.CoreRegistry.baseTag(tag), NdsOptions.appliesTo(tag), host.dcArcadeSet, padProfile().kind == com.nxredux.mobile.ui.nx.PadProfile.Kind.GC, wiiPad, padProfile().cStick)
        val settings = AndroidAppSettings(activity)
        return SkinKey(w, h, resources.displayMetrics.densityDpi, tablet, tag, aspect, hinge, top, variant, settings.skinTweaks, settings.overlayTweaks)
    }

    /** [pack]'s layout for [key] before any tweak ([SkinChoice.place], or [SkinChoice.placeClosest] when [closest]; [ds]: a DS or 3DS). */
    private fun placeUntweaked(pack: PadSkinPack, key: SkinKey, ds: Boolean, closest: Boolean): SkinPlacement? {
        val variant = key.variant
        val picture = key.aspect?.let { (it * 240f) to 240f }
        return if (closest) SkinChoice.placeClosest(pack.manifest, key.w, key.h, key.dpi, key.tablet, ds, picture, key.hinge, key.top, DeviceTraits.canHalfFold, variant)
            else SkinChoice.place(pack.manifest, key.w, key.h, key.dpi, key.tablet, ds, picture, key.hinge, key.top, DeviceTraits.canHalfFold, variant)
    }

    /** The smallest a control's touch area may be shrunk to here, window px. */
    private val minTouchPx: Float get() = resources.displayMetrics.density * MIN_TOUCH_DP

    /** [pack]'s layout for [key] ([placeUntweaked]) with the console's skin tweaks for it applied ([SkinTweaks.apply]). */
    private fun placeSkin(pack: PadSkinPack, key: SkinKey, ds: Boolean, closest: Boolean): SkinPlacement? {
        val placed = placeUntweaked(pack, key, ds, closest) ?: return null
        val console = CoreRegistry.consoleOf(key.tag)?.id ?: return placed
        return SkinTweaks.apply(placed, SkinTweaks.of(key.tweaks, console, placed.spec.id), key.w.toFloat(), key.h.toFloat(), TweakRules.SKIN, minTouchPx)
    }

    /** The bare buttons for [key] ([bareLayout]) from the closest layout and the console's skin and overlay tweaks for it. */
    private fun placeBare(pack: PadSkinPack, key: SkinKey, ds: Boolean): BareLayout? {
        val placed = placeUntweaked(pack, key, ds, closest = true) ?: return null
        val console = CoreRegistry.consoleOf(key.tag)?.id
        val id = placed.spec.id
        val skin = console?.let { SkinTweaks.of(key.tweaks, it, id) }.orEmpty()
        val overlay = console?.let { SkinTweaks.of(key.overlayTweaks, it, id) }.orEmpty()
        return bareLayout(placed, skin, overlay, key.w, key.h, minTouchPx)
    }

    /**
     * What the in-game Adjust buttons edits: the layout drawn now (the skin, or its bare buttons) as the game places it
     * for this window before the player's tweaks, both of a stick / d-pad swap's sets still in it as the game tweaks it,
     * [useStick] the one shown; null with neither drawn, or no console.
     */
    class SkinEdit(
        val consoleId: String, val placement: SkinPlacement, val w: Int, val h: Int,
        val picture: android.graphics.Bitmap, val parts: android.graphics.Bitmap?, val bare: Boolean, val useStick: Boolean,
    )

    /** The layout being played, untweaked ([SkinEdit]). */
    fun skinEdit(): SkinEdit? {
        val view = skinView ?: bareView ?: return null
        val bare = view === bareView
        val pack = skinPack ?: return null
        val tag = layoutTag()
        val ds = NdsOptions.appliesTo(tag) || N3dsOptions.appliesTo(tag)
        val console = CoreRegistry.consoleOf(tag)?.id ?: return null
        // the same window, picture aspect and layout set as the drawn one, with no tweaks (the editor adds the stored ones)
        val key = skinKey(tag, ds) ?: return null
        val placed = placeUntweaked(pack, key, ds, closest = bare) ?: return null
        val picture = pack.picture(placed.spec.image) ?: return null
        val parts = placed.spec.parts?.let { pack.picture(it) }
        return SkinEdit(console, placed, key.w, key.h, picture, parts, bare, view.useStick)
    }

    /** The skin's bare buttons are the pad now ([addBareSkin]). */
    val bareShown: Boolean get() = bareView != null

    /** A finger the skin view passed on ([PadSkinView] px, the skin filling the root) to the game view in [game]. */
    private fun glassTouch(game: FrameLayout, x: Float, y: Float, down: Boolean) {
        val view = retroIn(game) ?: return
        val skin = skinView ?: bareView ?: return
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

    /**
     * A skin view over the game in [root] (under the menu and the notifications), its touches to the pad and off its
     * buttons to the game in [game]; [bare]: only its buttons, see-through ([addBareSkin]).
     */
    private fun addSkinView(root: FrameLayout, game: FrameLayout, pack: PadSkinPack, bare: Boolean, onResized: () -> Unit): PadSkinView {
        val view = PadSkinView(activity, pack, input::onPad, ::onSkinAction, haptics = padVibration != com.nxredux.mobile.settings.PadVibration.OFF,
            onResized = { root.post(onResized) }, onStick = input::onStick,
            // the GameCube's C-stick, the Classic Controller's and the PlayStation's right stick: the right analog
            onCStick = { x, y -> if (skinRightStickIsAnalog(input.gcPad, isWii, wiiPad, psSkin())) input.onWiiRightStick(x, y) else input.onCStick(x, y) },
            onGlass = { x, y, down -> glassTouch(game, x, y, down) }, bare = bare)
        view.useStick = PadStickModes[padStickKey, stickDefault()]   // the player's choice, shared by the skin and its bare buttons
        view.dimmed = motionDim()
        // under the menu and the notifications, over the game
        val below = listOfNotNull(menuView, notificationsView).map { root.indexOfChild(it) }.filter { it >= 0 }.minOrNull() ?: -1
        root.addView(view, below, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        view.stripHidden = stripHiddenNow()
        if (!bare) view.underMenu = skinUnderMenu
        view.shortcuts = host.padShortcuts
        return view
    }

    /**
     * The skin's bare buttons over the full-screen game (Pad skin Off, or no skin layout fits): see-through at Pad
     * Opacity, solid while the picture clears them ([syncBareAlpha]); in portrait the picture keeps above their band
     * ([padRoom]). Nothing when even the closest layout does not fit.
     */
    private fun addBareSkin(root: FrameLayout, game: FrameLayout) {
        val pack = skinPack ?: return
        bareView = addSkinView(root, game, pack, bare = true) { onBareResized() }
        // no stylus mode: a touch off the buttons is the touch screen anyway
        if (input.ndsStylusOn.value) input.toggleNdsStylus()
        if (input.n3dsStylusOn.value) input.toggleN3dsStylus()
        if (barePlacement() == null) {
            NxLog.w(TAG_LOG, "skin ${pack.manifest.id}: no layout fits ${rootSize()}, no pad")
            bareView?.let { root.removeView(it) }
            bareView = null
            overlayHeight = 0
            return
        }
        overlayClear.value = false
        syncBareAlpha()
        overlayPadShown.value = true   // the notifications move top-centre
    }

    /** The bare buttons' opacity, in landscape and portrait: solid while the picture clears them ([overlayClear]), else Pad Opacity. */
    private fun syncBareAlpha() {
        bareView?.bareAlpha = if (overlayClear.value) 1f else padLandscapeAlpha.value
    }

    /** The bare view changed size: placed again for it, or laid out again whole if nothing fits now. */
    private fun onBareResized() {
        if (bareView == null) return
        if (barePlacement() == null) applyLayout(resources.configuration.orientation) else relayoutFrontend()
    }

    /** The skin is the layout: the game view fills the window under the skin's view, which takes the pad's touches. */
    private fun applySkinLayout(root: FrameLayout, game: FrameLayout, orientation: Int) {
        game.layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        val pack = skinPack ?: return
        skinView = addSkinView(root, game, pack, bare = false) { onSkinResized() }
        skinShownState.value = true
        // no stylus mode under a skin: a touch off its buttons is the touch screen (DS and 3DS); nor the 3DS's SELECT + R
        // / Left / Right chords ([N3dsHotkeys.skinShown])
        if (input.ndsStylusOn.value) input.toggleNdsStylus()
        if (input.n3dsStylusOn.value) input.toggleN3dsStylus()
        // a regular phone's landscape: the skin lays the DS / 3DS screens out by the player's layout, which SELECT + Left / Right cycles
        input.setSkinLayouts(shown = true, layouts = isLandscape(orientation) && activity.windowShape().screenClass.let { !it.isFoldShaped && !it.isTablet })
        skinPlacement()
        game.requestLayout()
        updatePadVisibility()
        menuView?.layoutParams = menuLayoutParams(orientation, overPanel = mainPanelShown(), glass = true)
        notificationsView?.layoutParams = menuLayoutParams(orientation)
        game.post { relayoutFrontend() }
    }

    /** The skin's MOTION shaded while the phone's motion is off, or with no sensors to use. */
    private fun motionDim(): Set<String> = if (padMotion()?.on == true) emptySet() else setOf("MOTION")
    private var motionHeld = false   // the skin's MOTION held past the long-press time: recentred, so its release is no tap
    private val motionHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val motionHold = Runnable { motionHeld = true; padMotion()?.onLongPress?.invoke() }

    /** A PlayStation game (its skin's right stick nub is the DualShock's right analog). */
    private fun psSkin() = com.nxredux.mobile.runner.CoreRegistry.baseTag(layoutTag()) in setOf("PS", "PSX", "PSP", "PS2")

    /** MIC and SWAP are the pad's own (the DS mic toggle, swap screens); the rest are the game's ([PadLayoutHost.onSkinShortcut]). */
    private fun onSkinAction(action: SkinAction, down: Boolean) {
        action.slot?.let { host.onPadShortcut(it, down); return }   // a strip slot: the player's action
        when (action) {
            SkinAction.MIC -> if (down) padMic()?.onTap?.invoke()
            // the 3DS's swap (held as long as the cap); the DS's on press
            SkinAction.SWAP -> if (N3dsOptions.appliesTo(layoutTag())) input.onN3dsSwap(down) else if (down) input.applyNdsAction(NdsAction.SwapScreens)
            // the stick or the d-pad on a tight Dreamcast layout: the player's choice flipped (nothing held may stick;
            // the new placement lets go of the skin's own touches)
            SkinAction.STICK_SWAP -> if (down) {
                input.releaseAllGameInput()
                val next = !PadStickModes[padStickKey, stickDefault()]
                PadStickModes[padStickKey] = next
                skinViews().forEach { it.useStick = next }
            }
            // the Wii's MOTION: a tap flips the phone's motion, a hold recentres it (no tap then)
            SkinAction.MOTION -> if (down) {
                motionHeld = false
                motionHandler.postDelayed(motionHold, android.view.ViewConfiguration.getLongPressTimeout().toLong())
            } else {
                motionHandler.removeCallbacks(motionHold)
                if (!motionHeld) { padMotion()?.onTap?.invoke(); motionHandler.post { skinViews().forEach { it.dimmed = motionDim() } } }
            }
            else -> host.onSkinShortcut(action, down)
        }
    }

    /** The skin's view changed size (insets, a resize): laid out again for it, or laid out again whole if none fits now. */
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
        if (!padSkinOn) return false
        skinPack ?: return false
        val tag = layoutTag()
        val ds = NdsOptions.appliesTo(tag)
        val n3ds = N3dsOptions.appliesTo(tag)
        if (!ds && !n3ds) return false
        return layoutControllerNames().isEmpty() && SkinDual.skinAllowed(mode, ds, n3ds)
    }

    /** Whether the pad skin is the layout now. */
    val skinShown: Boolean get() = skinView != null

    /** The room the in-game menu takes over the shown skin ([com.nxredux.mobile.skin.menuRoom]), window px; null with none. */
    fun skinMenuRoom(): PxRect? {
        if (!skinShown) return null
        val p = skinPlacement() ?: return null
        // the bottom screen's glass (and the strip under it) where the skin has one: the menu takes its place
        return menuBottomGlass()?.let { p.menuRect(it) } ?: p.menuRoom()
    }

    /** The glass whose corners the menu's room takes ([skinMenuRoom]): the bottom screen's, else the biggest (a DS's top screen). */
    fun skinMenuGlass(): com.nxredux.mobile.skin.PlacedGlass? {
        if (!skinShown) return null
        return menuBottomGlass() ?: skinPlacement()?.glasses?.maxByOrNull { it.rect.width * it.rect.height }
    }

    /**
     * The skin's bottom screen the in-game menu opens in, as on a dual screen (the top one left to the paused picture):
     * the game panel's glass ([panelSkinGlass], a one-screen game on the fold board), or a DS / 3DS touch screen's on a
     * two-screen board ([SkinFamily.FLEX]: the unfolded Fold, a tablet, a Flip half-folded). Null elsewhere: the phone's
     * layouts keep the menu across their screens ([com.nxredux.mobile.skin.menuRoom]).
     */
    fun menuBottomGlass(): com.nxredux.mobile.skin.PlacedGlass? {
        if (!skinShown) return null
        return panelSkinGlass ?: boardTouchGlass()
    }

    /**
     * A DS / 3DS touch screen's glass on a two-screen board ([SkinFamily.FLEX]: the unfolded Fold, a tablet, a Flip
     * half-folded), its top screen in a glass above: the bottom screen of the skin's own dual screen. Null elsewhere.
     */
    fun boardTouchGlass(): com.nxredux.mobile.skin.PlacedGlass? {
        if (!skinShown) return null
        val p = skinPlacement() ?: return null
        val tag = layoutTag()
        if (!NdsOptions.appliesTo(tag) && !N3dsOptions.appliesTo(tag)) return null
        if (p.spec.family != com.nxredux.mobile.skin.SkinFamily.FLEX || p.glass(GlassScreen.TOP) == null) return null
        return p.glass(GlassScreen.TOUCH)
    }

    /** The skin's top screen the paused picture shows on while the menu is in [menuBottomGlass]: a one-screen game's picture glass, a DS / 3DS top screen. */
    fun menuTopGlass(): com.nxredux.mobile.skin.PlacedGlass? {
        if (menuBottomGlass() == null) return null
        val p = skinPlacement() ?: return null
        return p.glass(GlassScreen.PICTURE) ?: p.glass(GlassScreen.TOP)
    }
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
     * Before onCreate's first [applyLayout]: starts from the menu's posture [hint] ([PostureTracker.seed]), so a game
     * launched half-folded places its skin at the hinge first instead of flat, which the hinge reading would replace a
     * moment later.
     */
    fun seedPosture(hint: PostureSeed?) {
        posture = postureTracker.seed(hint)
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
     * drawn in it), else 0: with no controller the game runs to the bottom under the bare buttons.
     */
    fun portraitBandPx(names: List<String> = layoutControllerNames()): Int {
        if (names.isEmpty() || !PadBandRule.keepPadBand(portrait = true, names)) return 0
        return activity.windowShape().portraitBandPx.roundToInt()
    }

    /** Portrait keeps the game below a top camera cutout; landscape leaves the (centred) picture full-screen. */
    /** The layout is the overlay pad: no pad skin, no controller ([OverlayMode.BARE]). */
    private var overlayPadLayout = false

    /**
     * The game's top in portrait: below a top camera cutout ([cutoutTop]), except a DS / 3DS screen stack (Large screen,
     * Stacked) under the overlay pad, which starts at the very top (under the cutout) for the height it needs. A
     * single screen, the other layouts and every other console have the height without it.
     */
    private fun portraitGameTop(): Int = if (overlayPadLayout && dsStackShown()) 0 else cutoutTop

    /**
     * A DS or 3DS game whose portrait layout (no controller: the overlay pad) is a stack of its two screens, filling the
     * width (Screen Scaling other than Native: Native's whole-number stack is centred and keeps clear of the cutout).
     */
    private fun dsStackShown(): Boolean {
        if (FrontendSettings.parse(frontendValues).scaling == com.nxredux.mobile.options.ScreenScaling.NATIVE) return false
        val tag = layoutTag()
        val screen = activity.windowShape().screenClass
        return when {
            NdsOptions.appliesTo(tag) -> NdsOptions.layoutShown(frontendValues, landscape = false, controllerMode = false, screen) in
                setOf(NdsLayoutChoice.LARGE, NdsLayoutChoice.STACKED)
            N3dsOptions.appliesTo(tag) -> N3dsOptions.appLayoutFor(frontendValues, landscape = false, controllerMode = false, screen) in
                setOf("large", "stacked")
            else -> false
        }
    }

    fun applyGameTopMargin(orientation: Int) {
        // Below Android 11 the portrait band's height (a clip-on controller's room) read the cutout
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
        syncGameTop(orientation)
    }

    /** The game's top margin for [orientation] ([portraitGameTop]) without a skin, set when it differs: a DS / 3DS layout change moves it. */
    fun syncGameTop(orientation: Int) {
        if (skinShown) return
        val portrait = orientation == Configuration.ORIENTATION_PORTRAIT
        val game = findViewById<FrameLayout>(R.id.gameContainer)
        val lp = game.layoutParams as? FrameLayout.LayoutParams ?: return
        val top = if (portrait) portraitGameTop() else 0
        if (lp.topMargin != top) {
            lp.topMargin = top
            game.layoutParams = lp
        }
    }

    /**
     * No pad drawn (a controller attached): the corner buttons over the game ([CornerButtons]): a DS / 3DS game's mic
     * bottom-left ([micCornerShown]), the set touch shortcuts bottom-right ([cornerShortcuts]) and a split's Panel pill after them. Added whatever they are
     * now: both are states ([PadLayoutHost.padMic] is set up after the first layout, the shortcuts change with the
     * session), so the buttons come and go with them; with neither the view draws nothing.
     */
    private fun addCornerButtons(root: FrameLayout) {
        val v = ComposeView(activity).apply {
            setContent {
                NxTheme {
                    CornerButtons(
                        mic = host.padMic()?.takeIf { micCornerShown(it, padDrawn = false) },
                        shortcuts = cornerShortcuts(host.padShortcuts, padDrawn = false),
                        onShortcut = host::onPadShortcut,
                        panel = host.cornerPanel(),
                    )
                }
            }
        }
        root.addView(v, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        cornerView = v
        cornerShown.value = true
    }

    /** The bare buttons are over the game, so they hide while the in-game menu is open and touch reaches the menu. */
    fun updatePadVisibility() {
        // The panel filling the game's window hides the pad too: it would cover the panel (dual-screen handhelds have buttons).
        val hide = menuOpen || mainPanelShown()
        cornerView?.visibility = if (hide) View.GONE else View.VISIBLE
        // Waiting for the picture ([reveal]): invisible, not gone, so they are still laid out.
        val waiting = !reveal.shouldShow(SystemClock.uptimeMillis())
        // at the Pad Opacity the menu may have changed too
        bareView?.visibility = when {
            hide -> View.GONE
            waiting -> View.INVISIBLE
            else -> View.VISIBLE
        }
        syncBareAlpha()
    }

    /** The non-virtual gamepads/joysticks currently attached (wired or Bluetooth); empty for none. */
    private fun controllers(): List<InputDevice> {
        val im = inputManager ?: return emptyList()
        return im.inputDeviceIds.toList().mapNotNull { id -> im.getInputDevice(id) }.filter(input::isPad)
    }

    fun controllerNames(): List<String> = controllers().map { it.name }

    /**
     * The names of the [pads] that count as attached for the layout (the on-screen pad, the band rule, the skin, DS touch
     * mode; the hint glyphs follow every pad): in a multiplayer game not the benched ones ([PadPresence]); one player, all.
     */
    fun layoutControllerNames(pads: List<InputDevice> = controllers()): List<String> =
        PadPresence.attached(pads, input.slots?.outOfPlay) { DeviceKey(it.descriptor) }.map { it.name }

}

/**
 * Whether a new top cutout inset lays the pads out again whole: below Android 11 ([sdk]) in portrait when the window's
 * band ([bandPx], read with the inset now known; a clip-on controller's room is sized from it) differs from the one the pads were built
 * for ([builtBandPx]); else only the game's top margin follows, so a rotation that was laid out from its configuration
 * change is not built twice.
 */
internal fun cutoutNeedsRelayout(sdk: Int, portrait: Boolean, builtBandPx: Int, bandPx: Int): Boolean =
    sdk < 30 && portrait && bandPx != builtBandPx

/** The smallest a pad control's hit box may be shrunk to, dp: the game holds it as the layout editor does. */
internal const val MIN_TOUCH_DP = 32f

/** The band [p]'s buttons take at the bottom of a window [windowH] tall: from the highest piece or hit area down, px. */
internal fun skinBandHeight(p: SkinPlacement, windowH: Int): Int {
    val tops = p.pieces.map { it.dst.y } + p.controls.map { c -> c.y - if (c.spec.shape == com.nxredux.mobile.skin.ControlShape.RECT) c.spec.hh else c.spec.r }
    val top = tops.minOrNull() ?: return 0
    return (windowH - top).roundToInt().coerceIn(0, windowH)
}

/** The bare buttons as drawn ([placement], the overlay's tweaks) and the portrait band they take ([band], the skin's). */
internal class BareLayout(val placement: SkinPlacement, val band: Int)

/**
 * The bare buttons of [untweaked] (a layout as placed for a [w] x [h] window, no tweaks): per group the [overlay] tweak
 * where the player edited it there, else the [skin] one ([TweakRules.OVERLAY]), the edited groups kept where they put
 * them. The portrait band is measured with the [skin] tweaks alone, so an overlay button moved up over the picture
 * never shrinks it; it just sits over the game there.
 */
internal fun bareLayout(untweaked: SkinPlacement, skin: Map<String, SkinTweak>, overlay: Map<String, SkinTweak>, w: Int, h: Int, minTouchPx: Float): BareLayout {
    val drawn = SkinTweaks.apply(untweaked, skin + overlay, w.toFloat(), h.toFloat(), TweakRules.OVERLAY, minTouchPx, keep = overlay.keys).bare()
    val banded = SkinTweaks.apply(untweaked, skin, w.toFloat(), h.toFloat(), TweakRules.SKIN, minTouchPx).bare()
    return BareLayout(drawn, skinBandHeight(banded, h))
}

/**
 * The skin's right stick goes to the game's right analog ([GameInputRouter.onWiiRightStick]): the GameCube's C-stick
 * ([gcPad]), the Classic Controller's on a Wii ([wiiPad]) and the PlayStation's ([psSkin]); else it is the N64's C
 * buttons. A Wii pad not yet known shows the Classic layout, so its right stick is the Classic Controller's too.
 */
internal fun skinRightStickIsAnalog(gcPad: Boolean, isWii: Boolean, wiiPad: WiiPad?, psSkin: Boolean): Boolean =
    gcPad || (isWii && (wiiPad ?: WiiPad.CLASSIC) == WiiPad.CLASSIC) || psSkin
