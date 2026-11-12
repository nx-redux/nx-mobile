package com.nxredux.mobile.runner

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import android.content.res.Configuration
import android.view.Gravity
import com.nxredux.mobile.options.PxRect
import com.nxredux.mobile.ui.nx.LocalInSkin
import kotlin.math.roundToInt
import android.widget.FrameLayout
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.nxredux.mobile.R
import com.nxredux.mobile.cheats.CheatSet
import com.nxredux.mobile.netplay.NetplaySession
import com.nxredux.mobile.options.ConfigSource
import com.nxredux.mobile.options.CoreConfig
import com.nxredux.mobile.options.CoreOption
import com.nxredux.mobile.options.CoreOptionFilter
import com.nxredux.mobile.options.FrontendOptions
import com.nxredux.mobile.options.N3dsOptions
import com.nxredux.mobile.options.ShaderOptions
import com.nxredux.mobile.runner.GameActivity.Companion.EXTRA_TITLE
import com.nxredux.mobile.settings.AndroidAppSettings
import com.nxredux.mobile.shortcuts.ShortcutOverrides
import com.nxredux.mobile.shortcuts.Shortcuts
import com.nxredux.mobile.ui.nx.GamePanelEvent
import com.nxredux.mobile.ui.nx.HintGlyphs
import com.nxredux.mobile.ui.nx.InGameMenu
import com.nxredux.mobile.ui.nx.LocalInGameTouchOnly
import com.nxredux.mobile.ui.nx.footerModeFor
import com.nxredux.mobile.ui.nx.FooterMode
import com.nxredux.mobile.ui.nx.InGameMenuModel
import com.nxredux.mobile.ui.nx.InputRouter
import com.nxredux.mobile.ui.nx.LocalHintGlyphs
import com.nxredux.mobile.ui.nx.LocalNxOptionText
import com.nxredux.mobile.ui.nx.LocalPadBelow
import com.nxredux.mobile.ui.nx.LocalPortrait
import com.nxredux.mobile.ui.nx.LocalWindowSize
import com.nxredux.mobile.ui.nx.MenuEffect
import com.nxredux.mobile.ui.nx.MenuState
import com.nxredux.mobile.ui.nx.NxButton
import com.nxredux.mobile.ui.nx.NxTheme
import com.nxredux.mobile.ui.nx.PadProfile
import com.nxredux.mobile.ui.nx.PausedPictureCover
import com.nxredux.mobile.ui.nx.SlotUi
import com.nxredux.mobile.ui.nx.livePreviewRow
import com.nxredux.mobile.ui.nx.nxColorsFor
import com.swordfish.libretrodroid.GLRetroView
import com.swordfish.libretrodroid.NxLog
import com.swordfish.libretrodroid.Variable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * What [InGameMenuController] needs from [GameActivity]. The flags ([menuOpen], [quitting], [gameReady]...), the game's
 * config state (the store, its source and locks, the stored values), the cheats, the save slots and the quit stay
 * owned there; the menu reads and sets them through this, and pausing or resuming the game around the menu (which the
 * focus pause shares) is the activity's ([pauseForMenu], [resumeAfterMenu]).
 */
internal interface InGameMenuHost : GameScreenHost {
    var menuOpen: Boolean
    val gameReady: Boolean
    val gameTag: String
    val isN3ds: Boolean
    /** Overlays and shaders: app-private ([com.nxredux.mobile.storage.DataRoot.private]). */
    val privateRoot: File
    /** Where shader presets are listed from: the bundled set and the home's own ([Shaders.Roots]). */
    val shaderRoots: Shaders.Roots
    val frontendValues: Map<String, String>
    val rumbles: Array<RumbleMotor>
    val padProfileRevision: State<Int>
    // The save slots' strip and last-used slot, read on IO by the activity ([refreshSlots]).
    val slotsUi: State<List<SlotUi>>
    var lastSlotCache: Int?
    val saveStates: SaveStates?
    // netplay
    val netplay: NetplaySession?
    val netplayNow: NetplaySession?
    val netplayEnded: Boolean
    /** netplay: an ended Cable Link session: the menu stays open with Quit alone. */
    val netplayQuitOnly: Boolean
    /** DS local wireless on the automatic route: the game's radio is up, so the root is Continue, Disconnect, Quit. */
    val dsWireless: Boolean
    /** Seconds until a menu left open in a DS room leaves it by itself; null when nothing counts down. */
    val dsIdleSeconds: Int?
    // The game's config: the store in use, which layer it is, what is locked, forced or hidden, and its resolved values.
    val configStore: CoreConfigStore?
    var configSource: ConfigSource
    var lockedOptions: Set<String>
    val forcedOptions: Set<String>
    val restartOnlyOptions: Set<String>
    val graphicsForcedOptions: Set<String>
    val hiddenValues: Map<String, Set<String>>
    val descriptorDefaults: Map<String, String>
    var storedValues: Map<String, String>
    /** Hotkey and menu config saves land in order under this lock. */
    val configWrites: Mutex
    // cheats
    val cheatsSupported: Boolean
    var cheatSet: CheatSet
    val cheatsFolder: String
    val cheatsLoad: Job?
    fun padProfile(): PadProfile
    fun menuLocked(): Set<String>
    fun applyFrontend(values: Map<String, String>)
    fun applyOptions(vars: List<Variable>)
    fun applyCheats(view: GLRetroView)
    fun applyAchievements(model: InGameMenuModel)
    fun toggleMute(id: Int)
    fun refreshSlots()
    fun saveToSlot(slot: Int)
    fun loadFromSlot(slot: Int)
    /** The activity's quit ([GameActivity.quitWithAutoState]): the battery save, the auto state when [autoState], then finish. */
    fun quitGame(chosen: Boolean, autoState: Boolean)
    /** The menu's Leave: a link session ends and plays on; any other session disconnects and quits. */
    fun leaveNetplay()
    /** The menu's Disconnect: this phone leaves the DS room; the game runs on and the menu stays open. */
    fun disconnectDs()
    fun connectDolphinPorts()
    fun onInputDevicesChanged()
    /** The game holds under the opening menu: core, sound, rumble, motion; the battery save is written. */
    fun pauseForMenu()
    /** The menu closed: the game plays on (unless the focus pause holds it, or a failed netplay start). */
    fun resumeAfterMenu()
    /** This console's or game's shortcut overrides in effect (the Shortcuts page's live edits included): what Save Changes writes. */
    val shortcutOverrides: ShortcutOverrides
    /** The overrides changed (the Shortcuts page, Restore defaults): the bound keys follow now. */
    fun applyShortcuts(overrides: ShortcutOverrides)
    /** The Shortcuts page's inputs: Controls → Shortcuts, [shortcutOverrides], the console's hotkeys, whether the core rewinds. */
    fun publishShortcuts(model: InGameMenuModel)
    /** The panel's Fast forward / Rewind tiles: running now, and rewinding (null: no Rewind tile) ([SpeedController.tiles]). */
    fun speedTiles(): Pair<Boolean, Boolean?>
    /** A Fast forward / Rewind tile's toggles ([SpeedController.setToggle]). */
    fun setSpeedToggle(ff: Boolean?, rewind: Boolean?)
    /** The panel's Mic tile: null where this isn't a mic console or a session is on, else the session's mic on/off. */
    fun micTile(): Boolean?
    /** The Mic tile's toggle: the session's mic to [on]. */
    fun setMic(on: Boolean)
    /** The Reset dialog's Reset: the game restarts ([GLRetroView.reset]); a toggled rewind ends, a toggled FF stays. */
    fun resetGame()
    /** The menu's Adjust buttons: the skin layout editor over the paused game, back to the menu as it was after. */
    fun adjustButtons()
    /** The biggest DS / 3DS 3D scale the displays in use reach (null: unknown, neither): the 3D Resolution rows' scales. */
    val nds3dDisplayMax: Int?
    /** How the running DS or 3DS game is shown (null: neither): Console Settings lists only the layout rows in use. */
    val ndsView: com.nxredux.mobile.options.NdsView?
    /** The DS / 3DS 3D Resolution rows' live state ([InGameMenuModel.setNds3d]): the scale in use, software rendering. */
    fun publishNds3d(model: InGameMenuModel)
}

/**
 * The in-game pause menu: opening and closing it over the game, its view, model and input ([menuRouter]), the effects
 * its rows ask for ([handle]), and Core Options: the live read ([withCoreOptions]), Save Changes and Restore Defaults.
 */
internal class InGameMenuController(
    private val host: InGameMenuHost,
    private val input: GameInputRouter,
    private val padLayout: PadLayoutController,
    private val renderer: FrontendRenderer,
    private val dual: DualScreenController,
) {

    private val activity get() = host.activity
    private val resources get() = host.activity.resources
    private val intent get() = host.activity.intent
    private fun <T : android.view.View> findViewById(id: Int): T = host.activity.findViewById(id)
    private val lifecycleScope get() = host.lifecycleScope
    private val retroView get() = host.retroView
    private var menuOpen: Boolean
        get() = host.menuOpen
        set(value) { host.menuOpen = value }
    private val quitting get() = host.quitting
    private val gameReady get() = host.gameReady
    private val gameTag get() = host.gameTag
    private val isN3ds get() = host.isN3ds
    private val privateRoot get() = host.privateRoot
    private val frontendValues get() = host.frontendValues
    private val rumbles get() = host.rumbles
    private val padProfileRevision get() = host.padProfileRevision
    private val slotsUi get() = host.slotsUi
    private var lastSlotCache: Int?
        get() = host.lastSlotCache
        set(value) { host.lastSlotCache = value }
    private val saveStates get() = host.saveStates
    private val netplay get() = host.netplay
    private val netplayNow get() = host.netplayNow
    private val netplayEnded get() = host.netplayEnded
    private val netplayQuitOnly get() = host.netplayQuitOnly
    private val configStore get() = host.configStore
    private var configSource: ConfigSource
        get() = host.configSource
        set(value) { host.configSource = value }
    private var lockedOptions: Set<String>
        get() = host.lockedOptions
        set(value) { host.lockedOptions = value }
    private val forcedOptions get() = host.forcedOptions
    private val restartOnlyOptions get() = host.restartOnlyOptions
    private val graphicsForcedOptions get() = host.graphicsForcedOptions
    private val hiddenValues get() = host.hiddenValues
    private val descriptorDefaults get() = host.descriptorDefaults
    private var storedValues: Map<String, String>
        get() = host.storedValues
        set(value) { host.storedValues = value }
    private val configWrites get() = host.configWrites
    private val cheatsSupported get() = host.cheatsSupported
    private var cheatSet: CheatSet
        get() = host.cheatSet
        set(value) { host.cheatSet = value }
    private val cheatsFolder get() = host.cheatsFolder
    private val cheatsLoad get() = host.cheatsLoad
    private fun padProfile() = host.padProfile()
    private fun menuLocked() = host.menuLocked()
    private fun applyFrontend(values: Map<String, String>) = host.applyFrontend(values)
    private fun applyOptions(vars: List<Variable>) = host.applyOptions(vars)
    private fun applyCheats(view: GLRetroView) = host.applyCheats(view)
    private fun applyAchievements(model: InGameMenuModel) = host.applyAchievements(model)
    private fun toggleMute(id: Int) = host.toggleMute(id)
    private fun refreshSlots() = host.refreshSlots()
    private fun saveToSlot(slot: Int) = host.saveToSlot(slot)
    private fun loadFromSlot(slot: Int) = host.loadFromSlot(slot)
    private fun quitWithAutoState(chosen: Boolean, autoState: Boolean) = host.quitGame(chosen, autoState)
    private fun connectDolphinPorts() = host.connectDolphinPorts()
    private fun onInputDevicesChanged() = host.onInputDevicesChanged()
    private fun toast(msg: String) = host.toast(msg)

    var menuView: ComposeView? = null
    // The open menu's content, hosted here or on the second display ([placeMenu]): (on the second, its window size there).
    private var menuContent: (@Composable (Boolean, DpSize?) -> Unit)? = null
    // The picture's dim while the menu is in the dual-screen panel ([placeMenu]).
    private var menuDim: (@Composable () -> Unit)? = null
    // Where the open menu was last put; null while closed.
    private var menuPlacement: MenuPlacement? = null
    var menuModel: InGameMenuModel? = null
        private set
    val menuState = mutableStateOf<MenuState?>(null)

    private var menuListingJob: Job? = null   // openMenu's overlay / preset re-list (main thread only)
    lateinit var menuRouter: InputRouter   // set in onCreate

    private var optionsGeneration = 0   // bumped per live option change; drops stale re-reads
    // NdsRenderer.APPLY_ON_RESTART values set in the menu: kept from the running core, shown and saved (next launch).
    private val pendingRestartOptions = mutableMapOf<String, String>()

    /** Tells the in-game menu whether a second display is attached (its Bottom screen row) and whether an app holds it. */
    fun publishSecondScreen(model: InGameMenuModel) {
        model.setSecondScreen(dual.secondScreens.current.value != null, dual.screenTaken())
        menuState.value = model.state
    }

    /** The panel's Fast forward and Rewind tiles show the live state (a shortcut's toggle, the buffer running out). */
    fun publishSpeed(model: InGameMenuModel) {
        val (ff, rewind) = host.speedTiles()
        model.setSpeedTiles(ff, rewind)
        model.setMicTile(host.micTile())
        menuState.value = model.state
    }

    /** Back (the key's press, the gesture): the open in-game menu reads it as B, else it opens. */
    fun backPressed() { if (menuOpen) onMenuPress(NxButton.B) else toggleMenu() }

    fun toggleMenu() { if (quitting) return; if (menuOpen) closeMenu() else openMenu() }

    private fun openMenu() {
        if (menuOpen) return
        dual.menuOpening()
        menuOpen = true
        val model = InGameMenuModel(
            gameTitle = intent.getStringExtra(EXTRA_TITLE) ?: "Paused",
            slotCount = SaveStates.SLOT_COUNT,
            // The cached pointer. Only a menu opened before the launch's first read lands reads it here: a
            // one-line file, and the model takes its slot only at construction.
            initialSlot = (lastSlotCache ?: saveStates?.lastSlot()?.also { lastSlotCache = it } ?: 1) - 1,
        ).also {
            it.setStatus(configSource); it.cheatsAvailable = cheatsSupported
            it.screen = activity.screenClass()   // the DS / 3DS layout rows: this window's values and defaults
            it.displayMax3d = host.nds3dDisplayMax   // the DS / 3DS 3D Resolution rows' scales
            it.ndsView = host.ndsView                 // the DS / 3DS layout row in use, and the DS inset rows only in Picture in picture
            // the 3DS layout rows a pad skin ignores here, shown unavailable (only 3DS keys: no other console has them)
            it.layoutLocked = AndroidAppSettings(activity).padSkin.let { on ->
                com.nxredux.mobile.options.N3dsOptions.lockedLayoutRows(on, activity.screenClass()) + com.nxredux.mobile.options.NdsOptions.lockedLayoutRows(on, activity.screenClass())
            }
            // netplay: the root is "Leave netplay?" only (a link session's: Continue / Disconnect / Quit); Continue / Quit
            // once the session has ended. An ended link
            // session's game is a local one ([netplayNow] null): the normal root.
            if (netplayNow != null) {
                it.netplay = true; it.netplayLink = netplay?.endsAsLocalGame == true; it.netplayEnded = netplayEnded; it.netplayQuitOnly = netplayQuitOnly; it.refresh()
            }
            it.setDsWireless(host.dsWireless)   // a DS game's wireless on the automatic route: Continue, Disconnect, Quit
            it.setIdleSeconds(host.dsIdleSeconds)
        }
        if (retroView != null) {
            // Re-listed on every open so a bundled tree swapped in since launch, or a shader added to the home, is
            // picked up; on IO, the menu showing the cached lists until then (unchanged, as they almost always are,
            // nothing redraws).
            val tag = gameTag
            menuListingJob?.cancel()
            menuListingJob = lifecycleScope.launch {
                val (found, presets) = withContext(Dispatchers.IO) {
                    runCatching { Overlays.sets(privateRoot, tag) }.getOrNull() to runCatching { Shaders.presets(host.shaderRoots) }.getOrNull()
                }
                if (found != null && found != renderer.overlaySets) {
                    // The shown overlay may have been added/removed on disk: re-resolve it against the new list.
                    renderer.overlaySets = found   // the Overlay value is unchanged (its default is None whatever the files)
                    applyFrontend(frontendValues)
                    menuModel?.takeIf { it === model }?.let {
                        it.setFrontend(frontendValues, renderer.overlayNames(), menuLocked(), gameTag, overlayAvailable = !renderer.ndsSplit)
                        menuState.value = it.state
                    }
                }
                if (presets != null && presets != renderer.presetNames) {
                    renderer.presetNames = presets   // same for the presets, bundled and the user's
                    menuModel?.takeIf { it === model }?.let { it.setShaders(renderer.shaderValues, renderer.presetNames, renderer.shaderParams); menuState.value = it.state }
                }
            }
        }
        // Unconditional: the menu can open while the game is still loading, and must still show the loaded config.
        model.setFrontend(frontendValues, renderer.overlayNames(), menuLocked(), gameTag, overlayAvailable = !renderer.ndsSplit)
        host.publishNds3d(model)
        model.setShaders(renderer.shaderValues, renderer.presetNames, renderer.shaderParams)
        model.setCheats(cheatSet.entries, cheatsFolder, cheatSet.expected)
        host.publishShortcuts(model)
        model.setPadDrawn(padDrawn())   // Adjust buttons: only over a drawn skin or its bare buttons
        model.setControllerConnected(padLayout.controllerConnected.value)
        publishPlayers(model)
        publishSecondScreen(model)
        publishSpeed(model)
        applyAchievements(model)
        model.setPanelHeader(dual.panelHeader())
        // Over the dual-screen panel: the menu's panel opens on the tab it showed (a tab change has no effect to handle).
        if (dual.mainPanelShown()) model.panelEvent(GamePanelEvent.SelectTab(dual.dualPanelTab))
        menuModel = model
        // Multi-disc content (an m3u, a multi-disc PBP): the core reports its discs through the disk-control interface.
        dual.refreshDiscs()
        dual.syncPanelClock()
        menuState.value = model.state
        input.menuAxes.reset()
        input.stickRouter.onMenuToggled()
        refreshSlots()
        input.releaseAllGameInput()
        host.pauseForMenu()   // the core, its sound, rumble and motion hold; the battery save is written
        // The accents cannot change while a game runs; read on every open all the same, so a change made since launch shows the next time it opens.
        // The in-game menu is on the accent and the Secondary accent, as the Settings pages.
        val colors = AndroidAppSettings(activity).let { nxColorsFor(it.accent, it.secondaryAccent) }
        menuContent = { onSecond, windowOverride ->
            val config = LocalConfiguration.current
            NxTheme(colors = colors) {
                // No controller: hints show the on-screen pad's own labels (Sega C, PS ○); one attached: the A/B art.
                // Keyed on the profile's revision: a live Controller Type change redraws them with the menu open.
                val connected = padLayout.controllerConnected.value
                val hintGlyphs = remember(connected, padProfileRevision.value) {
                    if (connected) HintGlyphs.STANDARD else HintGlyphs.forPad(padProfile())
                }
                // Shortcuts' bind rows and Adjust buttons come and go with a controller (the on-screen pad binds nothing).
                LaunchedEffect(connected) { menuModel?.let { it.setPadDrawn(padDrawn()); it.setControllerConnected(connected); menuState.value = it.state } }
                // In a pad skin's glass (here, not on the second display): laid out for the glass's shape, as the main menu
                // in the skin (HandheldShell). The page title's gutter follows the app's: the narrow one in portrait; a
                // clip-on controller's empty room below the game counts as a pad below.
                // The window decides the two panes, as Settings' ([inGameMenuSplit]): portrait never splits. In a pad
                // skin's glass the glass is the menu's window (a landscape skin's tall glass: one column, not two panes).
                val frame = menuFrame(padLayout, onSecond, windowOverride)
                val skinGlass = frame.skinGlass
                val portraitLayout = frame.portrait
                val padBelow = skinGlass != null || portraitLayout && padLayout.portraitBandPx() > 0
                val density = LocalDensity.current
                val window = frame.window
                CompositionLocalProvider(
                    LocalHintGlyphs provides hintGlyphs, LocalPortrait provides portraitLayout, LocalPadBelow provides padBelow,
                    LocalWindowSize provides window, LocalInSkin provides (skinGlass != null),
                    // No controller and no skin round the menu (its picture full screen): no pad to press hints with.
                    LocalInGameTouchOnly provides (footerModeFor(connected, padVisible = skinGlass != null) == FooterMode.BUTTONS),
                    LocalNxOptionText provides AndroidAppSettings(activity).optionTextSize.ofListLabel,   // Appearance → Text size
                ) {
                // In the glass: the camera cutout reaches into it only as far as it runs past the glass's top (and
                // a bottom one past its bottom), so the part outside is taken off before the menu pads by it.
                val outside = skinGlass?.let { g ->
                    val windowH = (findViewById<FrameLayout>(R.id.root).height.takeIf { it > 0 } ?: activity.windowShape().heightPx).toFloat()
                    with(density) { PaddingValues(top = g.y.coerceAtLeast(0f).toDp(), bottom = (windowH - g.y - g.height).coerceAtLeast(0f).toDp()) }
                }
                // in the glass: clipped to its rounded corners, as the frame's hole is cut
                val glassCorner = if (skinGlass != null) with(density) { skinGlassCorner().toDp() } else 0.dp
                Box((if (outside != null) Modifier.consumeWindowInsets(outside) else Modifier).clip(RoundedCornerShape(glassCorner))) {
                menuState.value?.let { s ->
                    InGameMenu(
                        state = s,
                        slots = slotsUi.value,
                        onSelect = { i -> if (!quitting) menuModel?.let { it.select(i); menuState.value = it.state } },
                        onActivate = { i -> if (!quitting) menuModel?.let { m -> val effect = m.activate(i); menuState.value = m.state; handle(effect) } },
                        onDismiss = { if (!quitting) menuModel?.let { val e = it.press(NxButton.B); menuState.value = it.state; handle(e) } },
                        onChange = { d -> if (!quitting) menuModel?.let { val e = it.press(if (d > 0) NxButton.RIGHT else NxButton.LEFT); menuState.value = it.state; handle(e) } },
                        onPress = ::onMenuPress,
                        badgePath = { a -> menuModel?.badgePath(a) },
                        rowAchievement = { r -> menuModel?.achievementOf(r) },   // the Achievements page's rows as the panel's
                        onInfoTruncated = { info, t -> if (!quitting) menuModel?.let { it.setInfoTruncated(info, t); menuState.value = it.state } },
                        // Two panes on a wide area (the unfolded Fold, a phone in landscape), as Settings.
                        onSplit = { split -> menuModel?.split = split },
                        onParentTap = { kind, i -> if (!quitting) menuModel?.let { m -> val e = m.tapParent(kind, i); menuState.value = m.state; handle(e) } },
                        // A row of the Options preview beside the root's Options row: the page opens on it.
                        onPreviewTap = { i -> if (!quitting) menuModel?.let { m -> val e = m.tapPreview(i); menuState.value = m.state; handle(e) } },
                        // The game panel beside the root list (the model caches it until its inputs change).
                        panel = menuModel?.panelState(),
                        onPanelEvent = { ev -> if (!quitting) menuModel?.let { m -> val e = m.panelEvent(ev); menuState.value = m.state; handle(e) } },
                        // Not in netplay: the header over Continue / Leave only.
                        panelBeside = menuModel?.panelBeside ?: true,
                        // In the dual-screen panel's place: opaque, as the panel it replaces.
                        opaque = menuModel?.inPanel == true || (!onSecond && dual.mainPanel.value != null),
                        // An options row's switch, segment, picker or info icon, and the popup over it.
                        onOption = { ev -> if (!quitting) menuModel?.let { m -> val e = m.optionEvent(ev); menuState.value = m.state; handle(e) } },
                    )
                }
                }
                }
            }
        }
        // The paused picture's cover, lifted while a row that changes the picture is highlighted.
        menuDim = {
            NxTheme(colors = colors) { PausedPictureCover(dimmed = menuState.value?.let { !livePreviewRow(it) } ?: true) }
        }
        padLayout.skinStripHidden = true   // the skin's strip shortcuts (SAVE, LOAD, ...) go while the menu is up
        placeMenu()
    }

    /**
     * Puts the open menu where it belongs ([DualScreenController.menuHost]): with the dual-screen panel on its own display,
     * in the panel's place under its header, the picture's display only dimmed; else over the picture, on the second
     * display when the picture is there, or over the game here (in a pad skin's glass, its frame round it). Called on
     * open and whenever the second display, the panel or the picture's place changes.
     */
    fun placeMenu() {
        val content = menuContent ?: return
        if (!menuOpen) return
        val inPanel = dual.menuHost() == MenuHost.PANEL
        val second = dual.menuOnSecond()
        val placement = MenuPlacement(second, inPanel, dual.presentation)
        if (menuPlacement == placement) return
        menuModel?.takeIf { it.inPanel != inPanel }?.let { it.setInPanel(inPanel); menuState.value = it.state }
        val root = findViewById<FrameLayout>(R.id.root)
        menuView?.let { root.removeView(it) }
        menuView = null
        padLayout.skinUnderMenu = false
        if (second) {
            // The second display's window is its own size (and scaled density): the menu's window is what it gets there.
            dual.showMenuOnSecond { BoxWithConstraints(Modifier.fillMaxSize()) { content(true, DpSize(maxWidth, maxHeight)) } }
            // the picture here, under the menu's wash
            if (inPanel) menuDim?.let { addMenuView(root, it) }
        } else {
            dual.showMenuOnSecond(menuDim.takeIf { inPanel })
            addMenuView(root) { content(false, null) }
        }
        menuPlacement = placement
        dual.syncMainPanelHost()
        padLayout.updatePadVisibility()
    }

    /** [content] as [menuView] over the game here, where the menu would sit (in the skin: its frame round it). */
    private fun addMenuView(root: FrameLayout, content: @Composable () -> Unit) {
        val overlay = ComposeView(activity).apply { setContent { content() } }
        menuView = overlay
        padLayout.skinUnderMenu = !dual.mainPanelCoversWindow()   // in the skin (not over the dual-screen panel): its frame round the menu
        root.addView(overlay, menuLayoutParams(resources.configuration.orientation, overPanel = dual.mainPanelCoversWindow(), glass = true))
    }

    /**
     * Portrait: confine the modal to the top game area so the bottom controls stay visible and can drive
     * the menu (the whole height when the band is dropped). Landscape: cover the whole screen. Re-applied by
     * [applyLayout] (rotation, controller change) while the menu is open. [overPanel]: the menu takes the place of the
     * dual-screen panel, which fills the window (no pad below): the whole window too. [glass]: the menu's own (not the
     * notifications'): over a pad skin it sits in the skin's screen glass, the frame and the pad round it ([skinGlass]).
     */
    fun menuLayoutParams(orientation: Int, overPanel: Boolean = false, glass: Boolean = false): FrameLayout.LayoutParams {
        if (overPanel) return FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        if (glass) skinGlass()?.let { g ->
            val x = g.x.roundToInt(); val y = g.y.roundToInt()
            return FrameLayout.LayoutParams((g.x + g.width).roundToInt() - x, (g.y + g.height).roundToInt() - y, Gravity.TOP or Gravity.START).apply { leftMargin = x; topMargin = y }
        }
        return if (orientation == Configuration.ORIENTATION_PORTRAIT) {
            // Down to a clip-on controller's room (else the bottom): the window's height less it, from the one window reading.
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, activity.windowShape().heightPx - padLayout.portraitBandPx(), Gravity.TOP)
        } else {
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        }
    }

    /** A pad skin or its bare buttons are drawn now. */
    private fun padDrawn(): Boolean = padLayout.skinShown || padLayout.bareShown

    /** The corner radius of the glass the menu sits in, window px. */
    private fun skinGlassCorner(): Float = menuGlass()?.corner ?: 0f

    /**
     * The room the menu takes over a pad skin, window px ([com.nxredux.mobile.skin.menuRoom]): its glass and the touch
     * shortcut strip under it (hidden while the menu is open); a portrait DS / 3DS both screens down to the touch screen's
     * strip. Null with no skin shown.
     */
    private fun skinGlass(): PxRect? = padLayout.skinMenuRoom()

    /** The glass whose corners the menu's room takes: the biggest of the skin's (a DS's top screen). */
    private fun menuGlass(): com.nxredux.mobile.skin.PlacedGlass? {
        if (!padLayout.skinShown) return null
        return padLayout.skinPlacement()?.glasses?.maxByOrNull { it.rect.width * it.rect.height }
    }

    fun closeMenu() {
        if (!menuOpen || netplayQuitOnly) return   // an ended Cable Link session: only Quit leaves
        menuOpen = false
        menuRouter.releaseAll()
        input.stickRouter.onMenuToggled()
        // The dual-screen panel comes back on the tab the menu's panel left.
        menuState.value?.panelTab?.takeIf { dual.mainPanelShown() && it != dual.dualPanelTab }?.let { dual.dualPanelTab = it; dual.refreshDualPanel() }
        menuView?.let { findViewById<FrameLayout>(R.id.root).removeView(it) }
        menuView = null
        dual.showMenuOnSecond(null)
        menuContent = null
        menuDim = null
        menuPlacement = null
        padLayout.skinStripHidden = false   // the skin's strip shortcuts back
        padLayout.skinUnderMenu = false
        dual.syncMainPanelHost()
        menuModel = null
        menuState.value = null
        dual.syncPanelClock()
        padLayout.updatePadVisibility()
        dual.menuClosed()
        host.resumeAfterMenu()   // the game plays on, unless another app has the focus (focusResume then)
    }

    /**
     * A raw key while the menu is open: the Shortcuts page's press to bind takes it ([InGameMenuModel.takesKey]) before
     * the menu's routing, as MainActivity's Controls → Shortcuts does; a repeat it takes is dropped. True when taken.
     */
    fun captureKey(keyCode: Int, down: Boolean, repeat: Boolean): Boolean {
        if (quitting) return false
        val model = menuModel ?: return false
        if (!model.takesKey(keyCode, down)) return false
        if (repeat) return true
        val effect = model.captureKey(keyCode, down)
        menuState.value = model.state
        handle(effect)
        return true
    }

    /** Menu navigation from the InputRouter (hardware/on-screen). */
    fun onMenuPress(button: NxButton) {
        if (quitting) return   // frozen until finish()
        val model = menuModel ?: return
        val effect = model.press(button)
        menuState.value = model.state   // publish before handling: Close/Quit clear the menu state
        handle(effect)
    }

    fun handle(effect: MenuEffect?) {
        if (quitting) return   // a quit is in flight: nothing may resume, save or re-quit before finish()
        when (effect) {
            null -> Unit
            MenuEffect.Close -> closeMenu()
            MenuEffect.Quit -> quitWithAutoState(chosen = true, autoState = true)
            // Quit's "Quit": the battery save as ever, the resume point on disk left as it is.
            MenuEffect.QuitWithoutResume -> {
                // A live link session's Quit: the other side is told now, as Disconnect would, not left to time out.
                if (netplayNow != null && netplay?.endsAsLocalGame == true && !netplayEnded) host.leaveNetplay()
                quitWithAutoState(chosen = true, autoState = false)
            }
            MenuEffect.LeaveNetplay -> host.leaveNetplay()
            MenuEffect.DisconnectDs -> host.disconnectDs()
            is MenuEffect.SaveSlot -> saveToSlot(effect.slot)
            is MenuEffect.LoadSlot -> loadFromSlot(effect.slot)
            is MenuEffect.ToggleMute -> toggleMute(effect.id)
            MenuEffect.OpenCoreOptions -> {
                // Always answer the request, even without a view: the model blocks further opens while one is pending.
                if (retroView == null) {
                    menuModel?.let { it.showCoreOptions(emptyList(), lockedOptions + graphicsForcedOptions + restartOnlyOptions + dualForcedOptions()); menuState.value = it.state }
                } else {
                    // The split's (or half-folding's) held options show locked: a live change would turn the frame under the cuts.
                    withCoreOptions { all -> menuModel?.showCoreOptions(all ?: emptyList(), lockedOptions + graphicsForcedOptions + restartOnlyOptions + dualForcedOptions()) }
                }
            }
            is MenuEffect.SetOption -> {
                val generation = ++optionsGeneration
                if (effect.key in NdsRenderer.APPLY_ON_RESTART) {
                    // Never pushed live (a GL <-> software switch can't happen mid-game): shown and saved, used at the next launch.
                    pendingRestartOptions[effect.key] = effect.value
                    toast("Render mode applies when the game restarts")
                } else {
                    applyOptions(listOf(Variable(effect.key, effect.value)))
                    input.n3dsCoreValues(mapOf(effect.key to effect.value))
                }
                // Re-read so core-driven visibility changes (and the value the core accepted) show up. A read
                // overtaken by a newer change is dropped, so it can't revert that change on screen.
                withCoreOptions { all ->
                    if (all != null && generation == optionsGeneration) menuModel?.updateOptions(all)
                }
            }
            is MenuEffect.SetFrontend -> applyFrontend(frontendValues + (effect.key to effect.value))
            is MenuEffect.SetShader -> renderer.applyShader(renderer.shaderValues + (effect.key to effect.value))
            is MenuEffect.SetShaders -> renderer.applyShader(renderer.shaderValues + effect.entries)
            is MenuEffect.SetCheat -> {
                cheatSet = cheatSet.toggle(effect.index, effect.enabled)
                // A toggle during load must not reach retro_cheat_reset before retro_load_game; the first-frame block re-applies the full set.
                if (gameReady) retroView?.let(::applyCheats)
                // The set is the one owner: the open menu's panel (a toggle from the dual-screen panel) and that panel follow it.
                menuModel?.let { it.setCheats(cheatSet.entries, cheatsFolder, cheatSet.expected); menuState.value = it.state }
                dual.refreshDualPanel()
            }
            is MenuEffect.ChangeDisc -> retroView?.let { view ->
                dual.discs = dual.discs?.let { effect.index to it.second }
                // The menu panel's Swap disc says so, as the dual-screen panel's does; the Disc row shows it in place.
                if (effect.toast) dual.discs?.let { (index, count) -> dual.discToast(index, count) }
                dual.refreshDualPanel()
                lifecycleScope.launch(Dispatchers.IO) {
                    runCatching { view.changeDisk(effect.index) }.onFailure { NxLog.w("GameActivity", "disc change failed", it) }
                }
            }
            MenuEffect.SaveForConsole -> saveOptions(game = false)
            MenuEffect.SaveForGame -> saveOptions(game = true)
            MenuEffect.RestoreDefaults -> restoreOptions()
            is MenuEffect.ShortcutsChanged -> host.applyShortcuts(effect.overrides)
            // A tile: the game runs with it on, so the menu closes.
            is MenuEffect.SetSpeed -> { host.setSpeedToggle(effect.ff, effect.rewind); closeMenu() }
            is MenuEffect.SetMic -> { host.setMic(effect.on); closeMenu() }
            MenuEffect.ResetGame -> { host.resetGame(); closeMenu() }
            MenuEffect.AdjustButtons -> host.adjustButtons()
            MenuEffect.UseTopScreen -> { dual.useTakenScreen(); closeMenu() }
            is MenuEffect.SetPlayer -> input.slots?.let { s ->
                val benchedBefore = s.outOfPlay
                s.assign(effect.slot, effect.key?.let(::DeviceKey)).forEach { port ->
                    input.releasePort(port)
                    // The rumble follows the pad now in the slot (none: silent; port 0 falls back to the phone).
                    val id = (s.slots()[port] as? SlotView.Playing)?.key?.let { k -> input.keysById.entries.firstOrNull { it.value == k }?.key }
                    rumbles.getOrNull(port)?.useDevice(id)
                }
                connectDolphinPorts()   // a slot filled here is connected; one emptied stays so (no unplug prompt)
                if (s.outOfPlay != benchedBefore) {
                    // Empty benched the pad that was there (for this session only), or a benched pad was chosen: the
                    // layout again as on a connect or disconnect (a benched pad does not hide the on-screen pad,
                    // [layoutControllerNames]), which republishes the Players page and the panel.
                    onInputDevicesChanged()
                } else {
                    menuModel?.let(::publishPlayers)
                    dual.refreshDualPanel()
                }
            }
        }
    }

    /** The 3DS options the second-screen split or the video views hold for the session: a save keeps their stored values. */
    private fun dualForcedOptions(): Set<String> =
        if (!isN3ds) emptySet()
        else DualScreen.n3dsForcedOptions(dual.dualMode, halfFolded = false)?.keys.orEmpty() +
            if (renderer.n3dsViews) DualScreen.N3DS_VIEWS_CORE.keys else emptySet()

    /**
     * Reads the core's options on the emulation thread, then runs [then] on the main thread and redraws.
     * [then] gets null when the read failed.
     */
    private fun withCoreOptions(then: (List<CoreOption>?) -> Unit) {
        val view = retroView ?: return
        lifecycleScope.launch {
            // Values kept from the running core until a restart show (and save) as set, not as the core runs.
            val all = view.readCoreOptions()
                ?.let { CoreOptionFilter.withHiddenValues(CoreOptionFilter.withPending(it, pendingRestartOptions), hiddenValues) }
            then(all)
            menuModel?.let { menuState.value = it.state }
        }
    }

    private fun saveOptions(game: Boolean) {
        val store = configStore
        // The menu has already popped to Options; say so rather than silently doing nothing.
        if (store == null || retroView == null) { toast("Could not save options"); return }
        withCoreOptions { all ->
            // A failed read must never be written. An empty one (a core without options) still saves the
            // frontend keys; render() keeps every existing line it isn't given.
            if (all == null) { toast("Could not save options"); return@withCoreOptions }
            lifecycleScope.launch {
                // A game save before the cheat load lands would write an empty nx_cheats and wipe the saved list.
                if (game) cheatsLoad?.join()
                // Under the hotkey saves' lock, and with the frontend values read once it is held: a hotkey pressed
                // meanwhile has already applied its value, so this write keeps it instead of bringing back the old one.
                val written = configWrites.withLock {
                    // A key Graphics API forced for this launch keeps its stored value: the running one is the session's only.
                    // Shortcuts: only the rows overridden here (overrides inherit), never a full snapshot of what
                    // fires, so a later Controls → Shortcuts change still reaches the others; a row given back to the
                    // default loses its line ([Shortcuts.droppedOnSave]).
                    val overrides = host.shortcutOverrides
                    val values = CoreConfig.valuesToSave(all.map { it.key to it.value }, storedValues, graphicsForcedOptions + dualForcedOptions()) + frontendValues.toList() +
                        ShaderOptions.activeParamEntries(renderer.shaderValues, renderer.shaderParams) + Shortcuts.toConfig(overrides) +
                        if (game) listOf(FrontendOptions.CHEATS to CheatSet.encode(cheatSet.enabledKeys())) else emptyList()
                    val drop = Shortcuts.droppedOnSave(overrides)
                    withContext(Dispatchers.IO) {
                        runCatching {
                            if (game) store.saveGame(values, drop) else store.saveConsole(values, drop).also { store.deleteGame() }
                        }.onFailure { NxLog.w("GameActivity", "saving options failed", it) }.getOrNull()
                    }
                }
                if (written == null) toast("Could not save options") else {
                    configSource = if (game) ConfigSource.GAME else ConfigSource.CONSOLE
                    lockedOptions = written.locked + forcedOptions
                }
                val model = menuModel ?: return@launch
                if (written != null) {
                    model.setStatus(configSource)
                    model.showMessage("Save Changes", if (game) "Saved for game." else "Saved for console.")
                }
                menuState.value = model.state
            }
        }
    }

    /** Deletes the config file in use and falls back one layer (game → console → defaults), applied live. */
    private fun restoreOptions() {
        val store = configStore
        // The menu has already popped to Options; say so rather than silently doing nothing. Nothing restored: the
        // model's dropped shortcut overrides come back ([publishShortcuts]).
        if (store == null || retroView == null) { toast("Could not save options"); menuModel?.let(::republishShortcuts); return }
        withCoreOptions { all ->
            if (all == null) { toast("Could not save options"); menuModel?.let(::republishShortcuts); return@withCoreOptions }
            lifecycleScope.launch {
                val next = configWrites.withLock {   // after any hotkey save in flight, not interleaved with it
                    withContext(Dispatchers.IO) {
                        runCatching {
                            when (configSource) {
                                ConfigSource.GAME -> store.deleteGame()
                                ConfigSource.CONSOLE -> store.deleteConsole()
                                ConfigSource.NONE -> Unit
                            }
                            val (console, game) = store.read()
                            CoreConfig.resolve(descriptorDefaults, console, game, forcedOptions, hiddenValues)
                        }.onFailure { NxLog.w("GameActivity", "restoring options failed", it) }.getOrNull()
                    }
                }
                if (next == null) {
                    toast("Could not save options")
                } else {
                    // The renderer is never switched live; the restored config picks it at the next launch.
                    pendingRestartOptions.clear()
                    val dualForced = dualForcedOptions()   // the split's; leaving it puts the restored ones back
                    applyOptions(all.mapNotNull { o ->
                        if (o.key in NdsRenderer.APPLY_ON_RESTART) return@mapNotNull null
                        // A forced option keeps what the launch set (picodrive_input1 follows Controller Type through applyFrontend).
                        if (o.key in forcedOptions || o.key in graphicsForcedOptions || o.key in dualForced) return@mapNotNull null
                        val target = next.values[o.key] ?: o.defaultValue
                        if (target.isNotEmpty() && target != o.value) Variable(o.key, target) else null
                    })
                    configSource = next.source
                    lockedOptions = next.locked
                    storedValues = next.values
                    input.n3dsCoreValues(mapOf(N3dsOptions.DEADZONE to (next.values[N3dsOptions.DEADZONE] ?: "")))
                    applyFrontend(FrontendOptions.fromConfig(next.values, gameTag))
                    renderer.applyShader(ShaderOptions.fromConfig(next.values))
                    host.applyShortcuts(Shortcuts.fromConfig(next.values))   // the layer fallen back to's, none for defaults
                }
                val model = menuModel ?: return@launch
                host.publishShortcuts(model)   // the model dropped its overrides; a failed restore puts the ones in effect back
                if (next != null) {
                    model.setFrontend(frontendValues, renderer.overlayNames(), menuLocked(), gameTag, overlayAvailable = !renderer.ndsSplit)
                    model.setShaders(renderer.shaderValues, renderer.presetNames, renderer.shaderParams)
                    model.setStatus(configSource)
                    model.showMessage("Save Changes", if (next.source == ConfigSource.CONSOLE) "Restored console config." else "Restored defaults.")
                }
                menuState.value = model.state
            }
        }
    }

    private fun republishShortcuts(model: InGameMenuModel) {
        host.publishShortcuts(model)
        menuState.value = model.state
    }

    /** The Players page's data in the open menu. */
    fun publishPlayers(model: InGameMenuModel) {
        val (slotRows, padRows) = input.playerRows() ?: return
        model.setPlayers(slotRows, padRows)
        menuState.value = model.state
    }
}

/** The window, orientation and pad skin glass the in-game menu lays out for ([menuFrame]). */
internal data class MenuFrame(val window: DpSize, val portrait: Boolean, val skinGlass: PxRect?)

/**
 * What the in-game menu lays out for: on the second display ([onSecond]) its window there ([secondWindow]); here a pad
 * skin's glass when one is shown, else the window. The dual-screen panel the menu takes the place of reads the same, so
 * its page inset is the menu's.
 */
@Composable
internal fun menuFrame(padLayout: PadLayoutController, onSecond: Boolean, secondWindow: DpSize?): MenuFrame {
    val config = LocalConfiguration.current
    val density = LocalDensity.current
    val glass = if (onSecond) null else padLayout.skinMenuRoom()
    val portrait = DualMenu.menuPortrait(glass, onSecond, config.orientation == Configuration.ORIENTATION_PORTRAIT)
    val window = secondWindow.takeIf { onSecond } ?: glass?.let { g -> with(density) { DpSize(g.width.toDp(), g.height.toDp()) } }
        ?: DpSize(config.screenWidthDp.dp, config.screenHeightDp.dp)
    return MenuFrame(window, portrait, glass)
}
