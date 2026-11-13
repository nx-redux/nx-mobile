package com.nxredux.mobile.runner

import android.app.Activity
import android.app.ActivityManager
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.drawable.ColorDrawable
import android.hardware.input.InputManager
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import android.view.Display
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewTreeObserver
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import android.window.OnBackInvokedDispatcher
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import com.nxredux.mobile.ui.nx.symmetricCutoutInset
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.round
import com.nxredux.mobile.ui.nx.NxDelayIndicator
import com.nxredux.mobile.ui.nx.NxSpeedIndicator
import com.nxredux.mobile.ui.nx.pictureIndicatorAnchor
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import com.nxredux.mobile.AppProcess
import com.nxredux.mobile.BuildConfig
import com.nxredux.mobile.R
import com.nxredux.mobile.achievements.AchievementsService
import com.nxredux.mobile.achievements.AndroidRaHasher
import com.nxredux.mobile.achievements.RaAchievement
import com.nxredux.mobile.achievements.RaBadgeFolders
import com.nxredux.mobile.achievements.RaConsoles
import com.nxredux.mobile.achievements.RaFingerprintKeys
import com.nxredux.mobile.achievements.RaGamePointer
import com.nxredux.mobile.achievements.RaLaunchFingerprint
import com.nxredux.mobile.achievements.RaLaunchHash
import com.nxredux.mobile.achievements.RaLaunchTarget
import com.nxredux.mobile.cheats.CheatSet
import com.nxredux.mobile.cheats.DolphinCheats
import com.nxredux.mobile.gametime.GameTimeRecorder
import com.nxredux.mobile.gametime.GameTimeService
import com.nxredux.mobile.gametime.GameTimeStore
import com.nxredux.mobile.launcher.bringHomeTaskToFront
import com.nxredux.mobile.layout.ScreenClass
import com.nxredux.mobile.library.Names
import com.nxredux.mobile.netplay.NetplayLinkKind
import com.nxredux.mobile.netplay.NetplayLinkOptions
import com.nxredux.mobile.netplay.NetplayRole
import com.nxredux.mobile.netplay.NetplayText
import com.nxredux.mobile.netplay.NetplaySession
import com.nxredux.mobile.options.AudioOptions
import com.nxredux.mobile.options.FrameSkipOptions
import com.nxredux.mobile.options.BottomScreenOptions
import com.nxredux.mobile.options.ConfigFile
import com.nxredux.mobile.options.ConfigSource
import com.nxredux.mobile.options.CoreConfig
import com.nxredux.mobile.options.FrontendOptions
import com.nxredux.mobile.options.FrontendSettings
import com.nxredux.mobile.options.N3dsOptions
import com.nxredux.mobile.options.WiiOptions
import com.nxredux.mobile.options.NdsOptions
import com.nxredux.mobile.options.NdsScreen
import com.nxredux.mobile.options.PadOptions
import com.nxredux.mobile.options.ScreenScaling
import com.nxredux.mobile.options.SpeedOptions
import com.nxredux.mobile.options.SpeedSettings
import com.nxredux.mobile.options.VulkanFallback
import com.nxredux.mobile.settings.ProviderPreferences
import com.nxredux.mobile.settings.Prefs
import com.nxredux.mobile.settings.AndroidAppSettings
import com.nxredux.mobile.settings.PrefsProvider
import com.nxredux.mobile.shortcuts.ShortcutAction
import com.nxredux.mobile.shortcuts.ShortcutOverrides
import com.nxredux.mobile.shortcuts.TouchSlot
import com.nxredux.mobile.ui.nx.TouchShortcut
import com.nxredux.mobile.storage.GamePath
import com.nxredux.mobile.storage.NxPaths
import com.nxredux.mobile.storage.AndroidLibraryIndexStore
import com.nxredux.mobile.storage.AndroidRefs
import com.nxredux.mobile.storage.AndroidRomsSources
import com.nxredux.mobile.storage.DataRoot
import com.nxredux.mobile.test.NxTestResults
import com.swordfish.libretrodroid.GLRetroView
import com.swordfish.libretrodroid.LibretroDroid
import com.swordfish.libretrodroid.LibretroNetplay
import com.swordfish.libretrodroid.NxLog
import com.swordfish.libretrodroid.ScaleMode
import com.swordfish.libretrodroid.ScreenRegion
import com.swordfish.libretrodroid.Variable
import com.nxredux.mobile.ui.nx.PadProfile
import com.nxredux.mobile.ui.nx.NxTheme
import com.nxredux.mobile.ui.nx.nxColorsFor
import androidx.compose.ui.graphics.asImageBitmap
import com.nxredux.mobile.ui.skinlayout.SkinEditorState
import com.nxredux.mobile.ui.skinlayout.SkinLayoutEdit
import com.nxredux.mobile.ui.skinlayout.SkinLayoutEditor
import com.nxredux.mobile.ui.nx.InGameMenuModel
import com.nxredux.mobile.ui.nx.InputRouter
import com.nxredux.mobile.ui.nx.MenuEffect
import com.nxredux.mobile.ui.nx.NotificationQueue
import com.nxredux.mobile.kids.KIDS_WARNING
import com.nxredux.mobile.kids.KIDS_WARN_SHOWN
import com.nxredux.mobile.kids.kidsWarnAction
import com.nxredux.mobile.kids.AndroidKidsPlatform
import com.nxredux.mobile.kids.KidsAlarmReceiver
import com.nxredux.mobile.kids.KidsAlarms
import com.nxredux.mobile.kids.KidsSchedule
import com.nxredux.mobile.kids.kidsGameBlocked
import com.nxredux.mobile.kids.kidsInputDue
import com.nxredux.mobile.ui.nx.NxButton
import com.nxredux.mobile.ui.nx.NxNotifications
import com.nxredux.mobile.ui.nx.NxProgressPill
import com.nxredux.mobile.ui.nx.NxToast
import com.nxredux.mobile.ui.nx.nxNotificationPlacement
import com.nxredux.mobile.ui.nx.nxTopCentreMaxWidth
import com.nxredux.mobile.ui.nx.map
import com.nxredux.mobile.ui.nx.SlotUi
import com.nxredux.mobile.ui.nx.PadMic
import com.nxredux.mobile.ui.nx.PadMotion
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/** The process-wide log tag of the game screen and its controllers. */
internal const val TAG_LOG = "NXAPP"

internal class GameActivity : ComponentActivity(), GameInputHost, PadLayoutHost, FrontendHost, DualScreenHost, InGameMenuHost,
    Res3dHost, ShortcutsMicHost, GameNetplayHost, WiiDolphinHost,
    SaveSessionHost {

    companion object {
        /** The frontend rows a pad skin leaves out ([menuLocked]): it places the screens itself. */
        private val SKIN_HIDDEN_OPTIONS = setOf(
            com.nxredux.mobile.options.FrontendOptions.SCALING, com.nxredux.mobile.options.FrontendOptions.OVERLAY,
            com.nxredux.mobile.options.FrontendOptions.OFFSET_X, com.nxredux.mobile.options.FrontendOptions.OFFSET_Y,
            com.nxredux.mobile.options.FrontendOptions.PAD_LANDSCAPE_OPACITY,
            NdsOptions.LAYOUT_PORTRAIT, NdsOptions.LAYOUT_LANDSCAPE, NdsOptions.PIP_CORNER, NdsOptions.PIP_SIZE, NdsOptions.PIP_OPACITY,
        )
        const val EXTRA_CORE_FILE = "core_file"
        const val EXTRA_CORE_ID = "core_id"
        const val EXTRA_TAG = "tag"
        const val EXTRA_LEAVE_DISPLAY = "leave_display"   // Int: an app the player opened on this display stays there
        const val EXTRA_TITLE = "title"
        const val EXTRA_FILE_NAME = "file_name"      // ROM file name; keys save data
        const val EXTRA_CONTENT_URI = "content_uri"
        const val EXTRA_FOLDER_URI = "folder_uri"    // the folder holding the content (RetroAchievements hashes a sheet's files); "" = unknown
        const val EXTRA_LOAD_MODE = "load_mode"      // "FILE_DESCRIPTOR" | "REAL_PATH"
        const val EXTRA_OPTIONS = "options"          // array of "key=value"
        const val EXTRA_RESUME_SLOT = "resume_slot"  // Int save slot to load after start (0..7, 9 = auto); absent = fresh
        const val EXTRA_COMPANION_NAMES = "companion_names"  // String[]: files a disc sheet loads, as the sheet spells them, or an arcade set's parent/BIOS sets beside it (<set>.zip|7z, lowercase)
        const val EXTRA_COMPANION_URIS = "companion_uris"    // String[]: their content URIs, same order
        const val EXTRA_SYSTEM_SETS = "system_sets"          // String[]: arcade BIOS sets not beside the game, looked for in the system dir
        const val EXTRA_LAUNCH_ID = "launch_id"      // Long: the launcher's launch record (LaunchRecords), marked started once the game runs
        const val EXTRA_GAME_PATH = "game_path"      // the library path (GamePath): game time's identity; absent = not recorded
        const val EXTRA_ART_PATH = "art_path"        // the game's box art (GameSession.artPath): the game panel's header; absent = the console logo
        private const val FINISH_WHEN_HIDDEN_MS = 1_500L   // a quit still on screen this long after asking for the menu finishes anyway
        const val EXTRA_OWN_TASK = "own_task"        // Boolean: launcher mode, the game runs in its own task
        const val EXTRA_FORCE_GLES = "force_gles"    // Boolean: the relaunch after Vulkan could not start (VulkanFallback)
        // netplay: a Wi-Fi netplay launch (GameSession.netplay); all absent for every other launch. The run core is EXTRA_CORE_ID.
        const val EXTRA_NETPLAY_ROLE = "netplay_role"            // NetplayRole name: "HOST" | "CLIENT"
        const val EXTRA_NETPLAY_PEER = "netplay_peer"            // the other device's IPv4 address
        const val EXTRA_NETPLAY_FOLDER_CORE = "netplay_folder_core"  // the game folder's own core id (saves belong to it)
        const val EXTRA_NETPLAY_EMPTY_SAVES = "netplay_empty_saves"   // Boolean: no save copy happened although one was due
        const val EXTRA_NETPLAY_USB = "netplay_usb"              // Boolean: the session runs over the USB cable
        const val EXTRA_NETPLAY_MIXED = "netplay_mixed"          // Boolean: a GBA link with the other side on the other core
        const val EXTRA_NETPLAY_PEER_WL = "netplay_peer_wl"      // the other side's GBA link mode token; absent: it sent none
        const val EXTRA_NETPLAY_USB_TOKEN = "netplay_usb_token"  // Long: the menu's cable start this session runs on (UsbCables.token)
        // Cable Link over Wi-Fi (NetplaySession.cableLink and what its handshake settled)
        const val EXTRA_NETPLAY_CABLE_LINK = "netplay_cable_link"        // Boolean
        const val EXTRA_NETPLAY_OTHER_ROM = "netplay_other_rom"          // the other player's ROM, a filesystem path
        const val EXTRA_NETPLAY_OTHER_SAVE = "netplay_other_save"        // its save in a session dir; absent: none
        const val EXTRA_NETPLAY_LOCAL_SLOT = "netplay_local_slot"        // Int: 1 = GBA 1 (host), 2 = GBA 2
        const val EXTRA_NETPLAY_BIOS = "netplay_bios"                    // "real" | "hle"
        const val EXTRA_NETPLAY_RTC = "netplay_rtc"                      // Long: the session clock, epoch seconds
        /** Kids Mode's "Games close in 10 minutes" toast stays this long. */
        private const val KIDS_WARNING_MS = 6000L
        /** The failure dialog's title unless a caller names another. */
        internal const val START_FAILED = "Could not start the game"
        /** [onCoreHung]: between the hung game's finish and the process kill. */
        private const val HUNG_FINISH_GRACE_MS = 500L
        /** [endProcess]: at most this long for the netplay engine's shutdown before the process goes anyway. */
        private const val PROCESS_END_WAIT_S = 5L

        /** Debug builds only: the latest game screen, for the adb GAME_STOP hook ([debugQuit]). Main thread. */
        private var debugGame: java.lang.ref.WeakReference<GameActivity>? = null

        /**
         * Debug builds only (the adb GAME_STOP hook, NxGameStopReceiver): the live game quits as the menu's Quit does, so
         * its battery save and auto state are written. False when there is none (or in release). Main thread.
         */
        internal fun debugQuit(): Boolean {
            if (!BuildConfig.DEBUG) return false
            val game = debugGame?.get()?.takeIf { !it.isFinishing } ?: return false
            game.quitWithAutoState(chosen = true)
            return true
        }

        // The runner's native session is process-wide: one game screen owns it at a time (main thread only). A screen
        // created while another is alive (a second launcher stacked over a stopped game) makes that one quit and waits for
        // its teardown, native destroy included, before it starts its own emulator.
        private val sessions = SessionHandoff()

        /** Game screens created in this process and not yet destroyed (main thread): the last one's teardown ends a game process. */
        private var screens = 0
        /** The last screen's teardown is ending this process ([endProcess]): an END_GAME then has nothing left to do. */
        @Volatile internal var ending = false
        /** The latest game screen, for [endForHandoff]. Main thread. */
        private var latest: java.lang.ref.WeakReference<GameActivity>? = null

        /**
         * [EndGameReceiver]: the menu starts another game, so this process's game quits as a handoff does (battery save,
         * auto state, then finish; its teardown ends the process). True while a screen is alive here (one already
         * closing included); false: none, nothing will end the process. Main thread.
         */
        internal fun endForHandoff(): Boolean {
            if (screens == 0) return false
            latest?.get()?.takeIf { !it.isFinishing }?.onSessionHandedOff()
            return true
        }

        /** Kids Mode lock: as [endForHandoff], but the auto state is not written (the battery save still is). */
        internal fun endForKidsLock(): Boolean {
            if (screens == 0) return false
            latest?.get()?.takeIf { !it.isFinishing }?.onKidsLock()
            return true
        }
    }

    private var sessionClaim: SessionHandoff.Claim? = null
    private val liveGameStore by lazy { LiveGameStore.of(this) }

    override var retroView: GLRetroView? = null
        private set
    private val input = GameInputRouter(this)
    private val padLayout = PadLayoutController(this, input)
    private val renderer = FrontendRenderer(this, input, padLayout)
    private val dual = DualScreenController(this, input, padLayout, renderer)
    private val menu = InGameMenuController(this, input, padLayout, renderer, dual)
    private val shortcuts = ShortcutsMicController(this, padLayout, renderer, menu, dual)
    override var inputManager: InputManager? = null
        private set

    /** The pad table for this game (Sega pad type resolved at launch and on a Controller Type change; [PadMap.IDENTITY] for DC). */
    override var padMap: PadMap = PadMap.IDENTITY
        private set
    /** The resolved Sega pad (Sega pad tags only; null = unknown or another console). */
    private var segaPad: SegaPad? = null
    /** The cart header's pad type read at launch (null = none read), kept so Auto can be resolved again mid-game. */
    private var segaHeader: SegaPad? = null
    /** The Controller Type the core was launched with (Sega pad tags only), to catch up a change made during load. */
    private var launchedSegaSetting: String? = null
    /** The 3DS game's title read from its file at launch (null = not read: compressed, homebrew, unreadable). */
    private var n3dsTitle: N3dsTitle? = null
    /** The 3DS pad draws the New 3DS controls (ZL, ZR, the C-stick): New 3DS Controls resolved ([N3dsNewControls]). */
    private var n3dsNew = false
    override val wiiPad: WiiPad? get() = wii.pad
    /** Ends the game if its core hangs inside a frame ([onCoreHung]); from the view's creation to the end of onDestroy. */
    private var coreWatchdog: CoreWatchdog? = null
    override val isWii: Boolean get() = WiiOptions.appliesTo(gameTag) && !wii.gameCube
    override val isDolphin: Boolean get() = gameCoreId == WiiDolphinController.DOLPHIN_ID
    /** This launch asked the core for Vulkan (Display → Graphics API): a failure to start it relaunches on GLES. */
    @Volatile private var launchedOnVulkan = false
    /**
     * This screen is being recreated to start the game again on OpenGL ES ([relaunchOnGles]): it writes no auto state
     * and shows no failure dialog.
     */
    private var relaunching = false
    @Volatile private var launchEnv: Map<String, String?> = emptyMap()   // Graphics API's core environment for this launch
    /**
     * The core options this launch's Graphics API forced (SwanStation's renderer on Vulkan), and a netplay link session's
     * (NetplayLinkOptions, still set once the game is local): locked for the session, so hidden from Core Options, never
     * applied by a restore, and saved with their stored value ([CoreConfig.valuesToSave]).
     */
    @Volatile override var graphicsForcedOptions: Set<String> = emptySet()
        private set

    override fun onInputDevicesChanged() {
        relayoutPad()
        if (menuOpen) menu.menuModel?.let(menu::publishPlayers)
        dual.refreshDualPanel()
    }

    // ---- in-game pause menu ----
    private val menuOpenState = mutableStateOf(false)   // read by the notifications overlay (hides the speed indicator)
    override var menuOpen: Boolean
        get() = menuOpenState.value
        set(value) { menuOpenState.value = value }
    /**
     * Settings → System → Pause when unfocused: another app took the top focus while this window stays visible (split
     * screen, a freeform window, an app on another display), so the game holds, as under the menu, until it is back.
     */
    override var focusPaused = false
        private set
    private val saves = SaveSession(this, renderer, menu, dual)
    private val wii = WiiDolphinController(this, input, saves)
    private val launchPipeline = LaunchPipeline(LaunchState())
    private val dsRoom = DsRoomWatcher(lifecycleScope, { DsPresence.of(LibretroNetplay.dsPresence()) }, ::onDsPresenceChanged)
    private val dsCable = DsCable(this, lifecycleScope)
    // The cap is the Disconnect row pressed for the player: the automatic route leaves the room, a sheet session ends its link.
    private val dsMenuCap = DsMenuCap(lifecycleScope, onTick = { seconds -> withMenu { it.setIdleSeconds(seconds) } }) {
        if (launchPipeline.dsAutoRoute) disconnectDs() else leaveNetplay()
    }
    override val slotsUi: androidx.compose.runtime.State<List<SlotUi>> get() = saves.slotsUi
    override var lastSlotCache: Int?
        get() = saves.lastSlotCache
        set(value) { saves.lastSlotCache = value }
    override val saveStates: SaveStates? get() = saves.saveStates
    /** Saves, states, configs and `.minui` markers: [DataRoot.settled] once per session. */
    private lateinit var dataRoot: File
    /** This screen holds off the data move ([DataRoot.useBegin]) until it is destroyed. */
    private var usingData = false
    /** Overlays and shaders: app-private for good ([DataRoot.private]). */
    override lateinit var privateRoot: File
        private set
    override lateinit var shaderRoots: Shaders.Roots
        private set
    private lateinit var paths: NxPaths
    override var configStore: CoreConfigStore? = null
        private set
    override var configSource = ConfigSource.NONE
    override var lockedOptions: Set<String> = emptySet()
    override var restartOnlyOptions: Set<String> = emptySet()   // hidden from Core Options; applied only at game start
        private set
    override var forcedOptions: Set<String> = emptySet()   // the descriptor's lockedOptions: defaults a .cfg cannot change
        private set
    override var hiddenValues: Map<String, Set<String>> = emptyMap()   // the descriptor's values never offered nor read from a .cfg
        private set
    override var descriptorDefaults: Map<String, String> = emptyMap()
        private set
    override var storedValues: Map<String, String> = emptyMap()   // the config's resolved values (launch, or the last restore)
    override var cheatsSupported = true   // false for a core that ignores cheats (the panels hide their Cheats tab); set at launch
        private set
    @Volatile override var gameReady = false   // set on the first rendered frame; SRAM is only valid after load
        private set
    // This launch's record: marked started on the first frame (the game is recent), dropped on a refusal or finish.
    private val launchRecords by lazy { LaunchRecords.of(this) }
    private val launchId by lazy { intent.getLongExtra(EXTRA_LAUNCH_ID, LaunchRecords.NONE) }
    private var failShown = false   // the could-not-start dialog is up (it only leaves by finishing)
    override var quitting = false   // set by quitWithAutoState: quit is single-shot, the menu freezes, onStop skips its auto state
    private var finishWhenHidden = false   // a quit in launcher mode waits for the menu to cover it ([finish])
        private set
    override val resumePending: Boolean get() = saves.resumePending
    /** Game time for this activity's game (null until the first frame, or when the launch had no library path). */
    private var gameTime: GameTimeRecorder? = null
    private var gameTimeTicker: Job? = null

    // Settings → System → Screen care: both screens' layers (this window's, the second display's while it is up).
    private var mainCare: CareLayer? = null
    private val care = ScreenCare(SystemClock::uptimeMillis, { listOfNotNull(mainCare, dual.presentation?.careLayer) }, { syncKeepScreenOn() })
    private var careTicker: Job? = null

    // ---- frontend (display) settings ----
    override var gameTag = "UNKNOWN"
        private set
    override var gameCoreId = ""
        private set
    /** Debug builds: when this screen was created; game.json's autoState names only a slot 9 written since. */
    private var debugStartedAt = 0L
    /** Players this game's core gives ([CoreRegistry.playersFor]); 1 until launch. */
    override var playerCount = 1
        private set
    override var frontendValues: Map<String, String> = FrontendOptions.defaults()   // every frontend key, as in effect now
        private set
    /** The core can rewind ([com.nxredux.mobile.runner.CoreDescriptor.rewind]); false until the launch resolves the core. */
    override var canRewind = false
        private set
    /**
     * Fast forward and rewind on the view; the shortcuts drive it ([onShortcut]), the speed rows configure it ([applyFrontend]).
     * Off for a netplay session ([SpeedController.setSession]): a link session's end turns it back on ([becomeLocal]), a
     * lockstep session's does not, so a game played on alone after lockstep keeps it off, as it keeps save states off.
     */
    override val speed by lazy {
        // The panels' Fast forward / Rewind tiles follow whatever changed them (a shortcut, a tile, the buffer running out).
        // So do the touch shortcut buttons' toggled look and their Rewind rows.
        SpeedController(lifecycleScope, lifecycle, onTiles = { dual.refreshDualPanel(); menu.menuModel?.let(menu::publishSpeed); shortcuts.refreshTouchShortcuts() }) { retroView }
    }
    // Settings → Controls → Game rumble, read once per game: each port's rumble on its own pad; port 0 falls back to the phone.
    private val gameRumble by lazy { settings.gameRumble }
    private val intentTitle: String? get() = intent.getStringExtra(EXTRA_TITLE)
    private val intentTag: String? get() = intent.getStringExtra(EXTRA_TAG)
    // One instance per game: the game process's prefs refresh in place (Prefs.refreshGame), so reads stay current.
    override val settings by lazy { AndroidAppSettings(this) }
    /** Each port's rumble (index = port); empty until the launch ([LaunchPipeline]) builds it, so every use must tolerate empty. */
    override var rumbles: Array<RumbleMotor> = emptyArray()
        private set

    // ---- Nintendo DS screens (NdsLayout regions) ----
    override val ndsRegions: List<NdsRegion> get() = renderer.ndsRegions
    override val secondNdsRegions: List<NdsRegion> get() = renderer.secondNdsRegions
    override var ndsRenderMode: String? = null              // melonds_render_mode this game started with (the notice's)
        private set
    override var ndsFellBack = false                        // OpenGL was asked for, the core draws in software ([NdsRenderer.showFallbackNotice])
        private set

    // ---- DS / 3DS 3D resolution (Nds3dResolution, N3ds3dResolution): melonDS's GL scale, Azahar's factor follow the layout ----
    private val res3d = Res3dController(this, renderer)
    override val ndsView: com.nxredux.mobile.options.NdsView?
        get() = if (isNds || isN3ds) com.nxredux.mobile.options.NdsView(isLandscape(), renderer.ndsControllerMode()) else null
    override val nds3dDisplayMax: Int? get() = res3d.displayMax
    override val configWrites = Mutex()           // hotkey saves land in press order
    override val isNds: Boolean get() = NdsOptions.appliesTo(gameTag)

    // ---- Nintendo 3DS controls (N3dsHotkeys): the core draws both screens and its own touch cursor ----
    override val isN3ds: Boolean get() = N3dsOptions.appliesTo(gameTag)

    override val isDc: Boolean get() = DcInput.appliesTo(gameTag)
    override val isN64: Boolean get() = N64Input.appliesTo(gameTag)

    /** [padProfile]'s value, built once per game (and again when the resolved Sega pad changes it); null = not built yet. */
    private var padProfileCache: PadProfile? = null
    /** Bumped whenever [padProfileCache] is dropped, so Compose readers of [padProfile] (the menu's hint glyphs) redraw. */
    override val padProfileRevision = mutableStateOf(0)
    override fun dropPadProfile() { padProfileCache = null; padProfileRevision.value++; input.gcClearTriggers() }
    /** The on-screen pad's profile for this game's system (its kind, face letters, hidden buttons); the intent's tag, for onCreate's layout. */
    override fun padProfile(): PadProfile =
        padProfileCache ?: PadProfile.forTag(layoutTag(), segaPad, n3dsNew, wiiPad, gameCube = wii.gameCube).also { padProfileCache = it }

    // ---- cheats ----
    override var cheatSet: CheatSet = CheatSet.EMPTY   // resolved on IO after start; toggles live here until Save Changes
    private var dolphinPlayerCheats: CheatSet? = null  // the player's own set; kept while Dolphin's built-in file is not written yet
    private var dolphinSavedCheats: List<String> = emptyList()
    private var dolphinBuiltInPath: String? = null     // named after the ROM-cache copy Dolphin loads
    override var cheatsLoad: Job? = null               // done once cheatSet holds the resolved (and saved-state-applied) set
        private set
    override var cheatsFolder = ""                     // the folder the empty Cheats tab names (Cheats.folder), set at start
        private set

    // ---- netplay (Wi-Fi lockstep, see the "netplay" block) ----
    private val gameNetplay = GameNetplay(this)
    override val netplay: NetplaySession? get() = gameNetplay.session
    override val netplayEnded: Boolean get() = gameNetplay.ended
    override val netplayQuitOnly: Boolean get() = gameNetplay.quitOnly
    override val dsWireless: Boolean get() = launchPipeline.dsAutoRoute && dsRoom.current != DsPresence.OUT
    override val dsIdleSeconds: Int? get() = dsMenuCap.secondsLeft
    override fun showMenu() { if (!menuOpen) menu.toggleMenu() }
    override val netplayNow: NetplaySession? get() = gameNetplay.now
    override val sessionLocked: Boolean get() = netplayNow != null || dsWireless

    // ---- RetroAchievements (see the "achievements" block) ----
    override var achievements: AchievementsBridge? = null   // null: off, signed out, or a console RA does not cover
        private set
    override val notifications = NotificationQueue()
    private val unlockMoments by lazy { UnlockMoments(applicationContext) }
    private val notificationState = mutableStateOf(Pair<List<NxToast>, NxProgressPill?>(emptyList(), null))
    override var notificationsView: ComposeView? = null
        private set
    private var tickJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        screens++
        latest = java.lang.ref.WeakReference(this)
        if (BuildConfig.DEBUG) { debugGame = java.lang.ref.WeakReference(this); debugStartedAt = System.currentTimeMillis() }
        // Debug only: a GAME test hook's game runs on a locked test device (NxTestResults.markShowWhenLocked).
        if (BuildConfig.DEBUG && com.nxredux.mobile.test.NxTestResults.takeShowWhenLocked(this)) {
            setShowWhenLocked(true); setTurnScreenOn(true)
            testAwake = true
        }
        // Cross-fade in from the menu instead of the default slide-from-right: the bottom pad is
        // identical and in the same position in both screens, so a fade keeps it visually steady while
        // only the top (game) area changes (LibretroRunner gives the same with the start: the :game process starts too
        // late for this alone). Back to the menu the game fades to black over 150 ms (launch_return_out, see [finish]).
        // Only while the game shares the menu's task (launcher mode off); in launcher mode it has its own task, where
        // Android ignores these overrides, and opens with no animation at all (LibretroRunner's gameLaunchFlags).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(Activity.OVERRIDE_TRANSITION_OPEN, android.R.anim.fade_in, android.R.anim.fade_out)
            overrideActivityTransition(Activity.OVERRIDE_TRANSITION_CLOSE, 0, R.anim.launch_return_out)
        }
        // The game's own Recents entry (launcher mode) carries its title; sharing the menu's task, the entry stays the app's.
        if (isTaskRoot) {
            val recentsLabel = intentTitle ?: "Game"
            if (Build.VERSION.SDK_INT >= 33) setTaskDescription(ActivityManager.TaskDescription.Builder().setLabel(recentsLabel).build())
            else @Suppress("DEPRECATION") setTaskDescription(ActivityManager.TaskDescription(recentsLabel))
        }
        setContentView(R.layout.activity_game)
        mainCare = CareLayer(findViewById(R.id.root))
        care.enabled = settings.screenCare
        opaqueAfterFirstFrame()
        dual.readPlayTotal()   // before this launch's own slices land: the header's total stays as it was
        volumeControlStream = AudioManager.STREAM_MUSIC   // volume keys set the game's (media) volume, even between sounds
        syncKeepScreenOn()
        window.goImmersive()
        // Portrait: start the game below a top camera cutout. Returned unconsumed so children still get it.
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.root)) { _, insets ->
            val cutout = insets.getInsets(WindowInsetsCompat.Type.displayCutout())
            val top = cutout.top
            if (top != padLayout.cutoutTop) {
                padLayout.cutoutTop = top
                padLayout.applyGameTopMargin(resources.configuration.orientation)
            }
            insets
        }
        inputManager = (getSystemService(Context.INPUT_SERVICE) as InputManager).also {
            it.registerInputDeviceListener(input.inputDeviceListener, Handler(Looper.getMainLooper()))
        }
        menu.menuRouter = InputRouter(lifecycleScope, menu::onMenuPress)
        ContextCompat.registerReceiver(this, kidsWarn, IntentFilter(kidsWarnAction(this)), ContextCompat.RECEIVER_NOT_EXPORTED)
        lifecycleScope.launch { dual.secondScreens.current.collect { dual.syncDual(); menu.menuModel?.let(menu::publishSecondScreen) } }
        // The game's window resized (a rotation, a fold): the picture screen may be the other display now.
        findViewById<View>(R.id.root).addOnLayoutChangeListener { v, l, t, r, b, ol, ot, or_, ob ->
            if (r - l != or_ - ol || b - t != ob - ot) v.post { dual.recheckPictureOn() }
        }
        // Half-folded from the menu: the first layout already places a skin at the hinge. A re-creation (rotation, process
        // kill) leaves the launch's posture out: it may be long gone.
        padLayout.seedPosture(if (savedInstanceState == null) postureSeedFrom(intent.extras) else null)
        relayoutPad()
        padLayout.collectPosture()
        // Back as a gesture or a key on Android 16+ (targetSdk 36: predictive back, KEYCODE_BACK does not reach
        // onKeyDown): what the key's press did there, the in-game menu toggles (B inside it). Before 16 the gesture and
        // the key both arrive as KEYCODE_BACK, which onKeyDown consumes, so this never runs twice for one Back.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { backPressed() }
        })
        // netplay: a session launches cold: no saved or requested resume slot.
        gameNetplay.onCreate(intent, savedInstanceState)
        // Decided here, not after the launch's async setup, so a stop in between still records the slot.
        // A saved slot (recreated after a process kill) wins over the launch intent's.
        val savedSlot = savedInstanceState?.getInt(SaveSession.KEY_RESUME_SLOT, -1)?.takeIf { it in 0..NxPaths.AUTO_SLOT }
        saves.startFrom(if (netplay != null) null else savedSlot ?: intent.getIntExtra(EXTRA_RESUME_SLOT, -1).takeIf { it in 0..NxPaths.AUTO_SLOT })
        val claim = sessions.claim(::onSessionHandedOff).also { sessionClaim = it }
        // From the claim to the final release: the menu reads this as "a game screen is alive" (playing, suspended, closing).
        liveGameStore.write(LiveGame(
            Process.myPid(), launchId, intent.getStringExtra(EXTRA_GAME_PATH), intent.getStringExtra(EXTRA_CORE_ID) ?: "",
            intentTag ?: "UNKNOWN", saves.resumeSlot, netplay != null,
        ))
        launchRecords.keepSwitcherReturnFor(launchId)   // a switcher mark of another launch is not this game's to end on
        kidsReportsInput = settings.kidsEnabled
        lifecycleScope.launch {
            // Kids Mode Locked (a recreation from Recents after Android killed this process, a launch that raced the
            // lock): the game never starts, and its screen goes.
            if (kidsLockedHere()) {
                NxLog.i(TAG_LOG, "Kids Mode is locked: not starting $intentTitle")
                finish()
                return@launch
            }
            if (claim.mustWait) {
                // Also before the battery load and the RA prepare: the old game's quit writes the same saves and stops its client.
                // The watchdog only informs: the emulator never starts before the old game's native destroy.
                NxLog.i(TAG_LOG, "another game screen is alive: waiting for its quit before starting")
                var hint: android.widget.Toast? = null
                val stuck = claim.awaitWithWatchdog(
                    onSlow = {
                        NxLog.w(TAG_LOG, "the previous game screen is slow to close (${SessionHandoff.SLOW_AFTER_MS} ms)")
                        hint = toast("Closing the previous game…")
                    },
                    onStuck = {
                        NxLog.w(TAG_LOG, "the previous game screen has not closed after ${SessionHandoff.STUCK_AFTER_MS} ms")
                        hint?.cancel()
                        fail("The previous game has not closed yet, so this one cannot start. Go back and open it again.")
                    },
                )
                hint?.cancel()
                // Stuck: the dialog is up and Back is the only way on (finish releases this claim); do not start under it.
                if (stuck) return@launch
                NxLog.i(TAG_LOG, "the previous game screen is torn down: starting")
            }
            launchPipeline.start()
            if (launchPipeline.dsLinked && !isDestroyed) dsRoom.start()
            if (launchPipeline.dsCableAuto && !isDestroyed) dsCable.watch()
        }
    }

    /**
     * DS, 3DS and PSP local wireless: the game's radio went out, seeking (hosting or looking for a room) or into a room. The low-latency
     * Wi-Fi lock is held only in a room and the broadcasts lock only while not out; a sheet session has both from its launch, a PSP game on the automatic route the broadcasts lock. On the automatic route, seeking and a room
     * play as a session (no save, load, speed or reset shortcuts: they would break it for every member, or the room about
     * to form) and the menu is Continue, Disconnect, Quit; out, the full menu and shortcuts return. A menu open meanwhile
     * holds the core the way the room now asks: running for the members, or paused once alone.
     */
    private fun onDsPresenceChanged(presence: DsPresence) {
        launchPipeline.dsRoomChanged(presence == DsPresence.ROOM)
        launchPipeline.dsWirelessChanged(presence != DsPresence.OUT)
        if (launchPipeline.dsAutoRoute) {
            if (launchPipeline.dsCableAuto) dsCable.wireless = presence != DsPresence.OUT
            speed.setSession(netplay = dsWireless)
            shortcuts.refreshTouchShortcuts()
            withMenu { it.setDsWireless(dsWireless) }
        }
        if (menuOpen && !quitting) holdCore()
        syncDsMenuCap()
    }

    /** The menu open in a room counts toward the cap; anything else stops the count. */
    private fun syncDsMenuCap() = dsMenuCap.sync(menuOpen && !quitting && dsRoom.current == DsPresence.ROOM)

    /**
     * A sheet session's room again after a leave for the background or a lost focus. Never on the automatic route, where
     * the room comes back with the game's wireless: a session that fell back to it may still be a session in Azahar until
     * its next frame, and a rejoin would put it back in the room it was left for.
     */
    private fun rejoinSessionRoom() {
        if (!launchPipeline.dsAutoRoute) LibretroNetplay.dsRejoin()
    }

    /** Leaves the room with the game running; the check lands the full menu and the paused core at once. */
    override fun disconnectDs() {
        LibretroNetplay.dsLeaveNow()
        dsRoom.check()
    }

    /**
     * A newer game screen claimed the native session, or the menu starts another game ([endForHandoff]): leave through
     * the normal quit (battery save, auto state, RA stop, then finish; onDestroy's native destroy releases the session,
     * and in the game process ends it). Usually stopped here: the auto state is serialized off the GL thread as onStop
     * does, and the thumbnail is skipped when the stopped surface cannot be copied.
     */
    private fun onSessionHandedOff() {
        if (isDestroyed) return
        NxLog.i(TAG_LOG, "another game takes over: quitting $intentTitle")
        quitWithAutoState(chosen = false)
    }

    /**
     * Kids Mode's own check in the game process: the stored state (this process's copy of the settings, read at its
     * start) says Locked, and the main process does not hold Parent mode (asked only then; no answer counts as none).
     */
    private suspend fun kidsLockedHere(): Boolean {
        if (!settings.kidsEnabled) return false
        val app = applicationContext
        return withContext(Dispatchers.IO) {
            kidsGameBlocked(true, AndroidKidsPlatform.localNow(), KidsSchedule.decode(settings.kidsSchedule), settings.kidsUnlockUntil) {
                runCatching { PrefsProvider.kidsParent(app) }.onFailure { NxLog.w(TAG_LOG, "Kids Mode: no answer on Parent mode", it) }.getOrDefault(false)
            }
        }
    }

    /** Kids Mode is on: input here is reported to the main process for Parent mode's idle time ([kidsInputSeen]). */
    private var kidsReportsInput = false
    private var kidsInputAt = -1L

    /** Input in the game: at most every 30 s ([kidsInputDue]) the main process hears it, so a parent playing stays Parent. */
    private fun kidsInputSeen() {
        if (!kidsReportsInput) return
        val now = SystemClock.elapsedRealtime()
        if (!kidsInputDue(kidsInputAt, now)) return
        kidsInputAt = now
        runCatching { sendBroadcast(Intent(this, KidsAlarmReceiver::class.java).setAction(KidsAlarms.ACTION_GAME_INPUT)) }
    }

    private fun onKidsLock() {
        if (isDestroyed) return
        NxLog.i(TAG_LOG, "Kids Mode lock: quitting $intentTitle without its auto state")
        quitWithAutoState(chosen = false, autoState = false)
    }

    /**
     * singleTop. In launcher mode launches clear the game task (CLEAR_TASK); sharing the menu's task, the menu (the
     * only launcher) sits under this screen. So this only guards a launch that does reach this running game: it is
     * ignored, and its launch record is dropped, not leaked.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val id = intent.getLongExtra(EXTRA_LAUNCH_ID, LaunchRecords.NONE)
        if (id != launchId) launchRecords.drop(id)
    }

    override fun onPause() {
        rumbles.forEach { it.pause() }   // no rumble in the background; onResume picks the core's state up again
        // A quit writes the battery save once itself, off the main thread (quitWithAutoState), so nothing here then.
        if (!quitting) saves.writeBatterySaveOnPause()
        gameNetplay.run?.pause()   // netplay: backgrounded: no input goes out; the other side stalls, 30 s ends the session
        wii.syncMotionSensors(foreground = false)
        // Closes the phone mic at once (the paused core stops reading it); the toggle stays as it is for onResume.
        retroView?.microphoneSource = MicSource.SILENCE.native
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        // The core runs again only with the menu closed (closeMenu resumes it then); a quit never does. Focus held
        // elsewhere: focusResume does, once this window is the top one again.
        if (!corePaused()) {
            if (!quitting) rumbles.forEach { it.resume() }
            gameNetplay.run?.resume()   // netplay: input flows again; with the menu open it stays paused until Continue
        }
        wii.syncMotionSensors(foreground = true)
        shortcuts.onResume()
        dual.syncDual()   // Settings → Second screen may have changed meanwhile
    }

    private var topResumed = true   // the last onTopResumedActivityChanged (API 29+); below that, always the top one
    /** API 29+: multi-resume keeps this activity resumed while another app has the focus; only the top one is told. */
    override fun onTopResumedActivityChanged(isTopResumedActivity: Boolean) {
        super.onTopResumedActivityChanged(isTopResumedActivity)
        topResumed = isTopResumedActivity
        if (isTopResumedActivity) focusResume() else if (settings.pauseWhenUnfocused) focusPause()
    }

    /** The game holds as under the menu (see openMenu); an open menu has already paused it. */
    private fun focusPause() {
        if (focusPaused || quitting) return
        LibretroNetplay.dsLeaveNow()   // a DS room cannot wait out a pause: its members would time out
        dsRoom.check()   // an open menu pauses the core now that the room is gone
        focusPaused = true
        syncKeepScreenOn()
        if (menuOpen) return
        input.releaseAllGameInput()
        holdCore()
    }

    /** Focused again: the game plays on, unless the menu opened meanwhile (closeMenu resumes it then). */
    private fun focusResume() {
        if (!focusPaused) return
        focusPaused = false
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) rejoinSessionRoom()
        syncKeepScreenOn()
        if (menuOpen || quitting || !lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return
        releaseCore()
    }

    /**
     * Stops the core for the menu or a focus pause. Netplay: native runs a session at speed 1 whatever frameSpeed says; a
     * local pause stops this device's input instead. The core stops with its rumble latched: still while held, back on release.
     */
    private fun holdCore() {
        if (LibretroNetplay.dsInRoom()) {
            // A DS room keeps the core running (its members expect packets); the player just stops playing.
            input.releaseAllGameInput()
            retroView?.audioEnabled = false
            retroView?.frameSpeed = 1
            rumbles.forEach { it.pause() }
            shortcuts.pushMicSource()
            return
        }
        gameNetplay.run?.takeIf { it.live }?.pause() ?: run { retroView?.frameSpeed = 0 }
        retroView?.audioEnabled = false
        rumbles.forEach { it.pause() }
        shortcuts.pushMicSource()
    }

    /** Plays the core on; a failed netplay start keeps the game paused behind its dialog, an ended Cable Link session for good. */
    private fun releaseCore() {
        gameNetplay.run?.takeIf { it.live }?.resume()
        if (!gameNetplay.startFailed && !gameNetplay.quitOnly) {
            retroView?.frameSpeed = 1
            retroView?.audioEnabled = true
        }
        if (!quitting) rumbles.forEach { it.resume() }
        renderer.onCoreResumed()
        shortcuts.pushMicSource()
    }

    // Debug only: a GAME test hook's locked test device stays awake whatever the menu does.
    private var testAwake = false

    /**
     * The screen stays on while the game runs (cutscenes and attract screens get no input), not while it is held (the
     * in-game menu, a focus pause): a paused game sleeps on Android's timeout. A live netplay session runs on while held,
     * so it holds the screen too. Screen care lets a running game nobody has touched for a while sleep too.
     */
    private fun syncKeepScreenOn() {
        if (testAwake || gameNetplay.run?.live == true || (!corePaused() && !care.letSleep)) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    /** The opening menu holds the game ([InGameMenuController.openMenu]), as [focusPause] does. */
    override fun pauseForMenu() {
        dsRoom.check()   // the menu opened on the watcher's last poll: a presence changed since reaches the open menu now
        syncKeepScreenOn()
        wii.syncMotionSensors()   // no motion while paused
        holdCore()   // the phone mic closes under the menu too
        syncDsMenuCap()
        lifecycleScope.launch(Dispatchers.IO) { saves.writeBatterySave(notify = true) }
    }

    /** The menu closed ([InGameMenuController.closeMenu]): the game plays on, unless the focus pause still holds it. */
    override fun resumeAfterMenu() {
        // the menu can close under the Adjust buttons editor (the second screen's panel, netplay, a state load): it goes
        // with it, unsaved, so the game never plays on under it and a menu opened again is not drawn beneath it
        closeSkinEdit()
        syncKeepScreenOn()
        syncDsMenuCap()
        if (focusPaused) { wii.syncMotionSensors(); shortcuts.pushMicSource(); return }   // another app has the focus: focusResume plays on later
        wii.syncMotionSensors()
        releaseCore()
    }

    override fun corePaused(): Boolean = menuOpen || focusPaused

    override fun onNds3dLayout(current: Int?, displayMax: Int, sizeChange: Boolean) = res3d.onLayout(current, displayMax, sizeChange)

    override fun publishNds3d(model: InGameMenuModel) = res3d.publish(model)

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        saves.onUserLeaveHint()
    }

    /**
     * Back from the background (launcher mode's Home, the menu over a shared task): Settings may have changed in the menu
     * meanwhile (Second screen, Bottom screen, System buttons, Pause when unfocused). One call into the menu's process,
     * off the main thread: a killed menu process starts again first, which must not hold the window. Until it lands the
     * old values stand; then what onResume and the focus change read is applied again (System buttons are read per key).
     * A first start reads the file as the launch left it.
     */
    override fun onRestart() {
        super.onRestart()
        lifecycleScope.launch {
            withContext(Dispatchers.IO) { Prefs.refreshGame() }
            if (quitting || isFinishing || !lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) return@launch
            care.enabled = settings.screenCare
            dual.syncDual()
            // Both ways: a focus change before the refresh landed acted on the old value.
            val resumed = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
            when (focusPauseStep(focusPaused, topResumed, settings.pauseWhenUnfocused, resumed)) {
                FocusPauseStep.PAUSE -> focusPause()
                FocusPauseStep.RESUME -> focusResume()
                FocusPauseStep.NONE -> Unit
            }
        }
    }

    override fun onStart() {
        super.onStart()
        saves.onStart()
        if (isNds) input.syncPen()   // restarts the pen tick onStop stopped, if stylus mode is on
        if (isN3ds) input.syncN3dsCursor()
        dual.secondScreens.start()   // the collector shows the presentation again
        care.input()   // back in the foreground: the idle time starts again
        careTicker = lifecycleScope.launch {
            while (true) { delay(CARE_TICK_MS); care.tick(running = !corePaused() && !quitting) }
        }
        // onStop left the DS room; still focus paused, focusResume goes back instead.
        if (!focusPaused) rejoinSessionRoom()
        // onStop closed the session: back in the foreground, a new one on the same game (background time is not play)
        gameTime?.let { if (!isFinishing && !quitting) { it.resume(); startGameTimeTicker(it) } }
    }

    override fun onStop() {
        // Asked of the link itself, before the leave: a state taken in a room would resume in the middle of a link that is
        // gone. A wireless that is only on (a game that keeps it on while played alone) still gets its resume point.
        val inRoom = launchPipeline.dsAutoRoute && DsPresence.of(LibretroNetplay.dsPresence()) == DsPresence.ROOM
        LibretroNetplay.dsLeaveNow()
        dsRoom.check()   // an open menu pauses the core now that the room is gone
        dsMenuCap.sync(false)
        careTicker?.cancel(); careTicker = null
        gameTime?.pause()   // the session ends here: a kill in the background loses nothing, a return opens a new one
        gameTimeTicker?.cancel()
        gameTimeTicker = null
        dual.panelClockJob?.cancel(); dual.panelClockJob = null   // onResume's syncDual starts it again
        // Leaving via Home/recents: the process may be killed in the background, so keep the spot. Also when
        // finishing without a quit: sharing the menu's task, a launch of the singleTask menu (app icon, notification)
        // clears this screen. A quit writes its own; a game that never got ready writes nothing (writeAutoStateOnStop);
        // one that failed after starting and leaves through its dialog keeps the last good resume point.
        // API 26–27: onSaveInstanceState already ran; the outcome of the write is recorded in its bundle.
        saves.onStop(writeAuto = !quitting && !wii.endedByCore && !relaunching && !(isFinishing && failShown) && !inRoom)
        if (isFinishing) { input.gcClearTriggers(); stopAchievements() }
        input.onStop()   // the pen and 3DS hotkey ticks stop; onStart restarts them
        // A Presentation stays up while its activity is stopped: no second screen in the background.
        dual.secondScreens.stop()
        dual.syncDual()
        super.onStop()
        finishIfWaiting()   // a quit behind the menu now ([finish])
    }

    /** After a process kill the system recreates this activity with its original intent: what it resumes travels here. */
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        saves.onSaveInstanceState(outState)
        gameNetplay.onSaveInstanceState(outState)
    }

    /**
     * Debug builds: game.json "stopped" once this screen finishes, queued behind any auto-state write ([SaveSession.afterAutoWrites])
     * (a quit wrote its own before finish), with the slot-9 state file when this screen wrote it, else null.
     */
    private fun debugReportStopped() {
        if (debugGame?.get() === this) debugGame = null
        val app = applicationContext
        val auto = if (::dataRoot.isInitialized && ::paths.isInitialized) File(dataRoot, paths.state(NxPaths.AUTO_SLOT)) else null
        val since = debugStartedAt
        SaveSession.afterAutoWrites {
            val written = auto?.takeIf { it.isFile && it.lastModified() >= since }?.absolutePath
            NxTestResults.write(app, "game", "state" to "stopped", "autoState" to written)
        }
    }

    /** Back to the menu the game fades to black over 150 ms: below Android 14 (no close override set in onCreate) set here. */
    override fun finish() {
        // Launcher mode, quit on screen (not a handoff behind the menu, not the GLES relaunch): back to NX Redux's home.
        if (!relaunching) markClosing()
        if (intent.getBooleanExtra(EXTRA_OWN_TASK, false) && !relaunching && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            bringHomeTaskToFront()
            // A quit finishes once the menu covers it (onStop): a task closing on screen slides out, Android's task
            // animation, which no app override reaches; a hidden one just goes. FINISH_WHEN_HIDDEN_MS in case it stays.
            if (quitting && !finishWhenHidden) {
                finishWhenHidden = true
                window.decorView.postDelayed({ finishIfWaiting() }, FINISH_WHEN_HIDDEN_MS)
                return
            }
        }
        super.finish()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) @Suppress("DEPRECATION") overridePendingTransition(0, R.anim.launch_return_out)
    }

    /**
     * The menu must not bring this screen back from here on ([canBringBack]): its task would come forward only to finish,
     * and no game would start. A pick of the same game then waits for this process to end and starts fresh.
     */
    private fun markClosing() {
        runCatching { liveGameStore.markClosing(Process.myPid(), launchId) }.onFailure { NxLog.w(TAG_LOG, "could not mark the game closing", it) }
    }

    /** A quit waiting to be hidden ([finish]) finishes now. */
    private fun finishIfWaiting() {
        if (!finishWhenHidden) return
        finishWhenHidden = false
        super.finish()
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(kidsWarn) }   // not registered when onCreate stopped early
        dsRoom.stop()   // launchPipeline.onDestroy lets the Wi-Fi lock go
        dsCable.close()
        renderer.cancelGeometryWatch()
        res3d.cancelTick()
        rumbles.forEach { it.release() }
        wii.onDestroy()
        if (isFinishing || relaunching) endGameTime()   // Back/finish without Quit (quit already ended it), or the GLES relaunch
        if (isFinishing) launchRecords.drop(launchId)   // quit before the first frame: the launch never started
        if (BuildConfig.DEBUG && isFinishing) debugReportStopped()
        input.gcClearTriggers()   // a latched soft press never outlives the game
        stopAchievements()
        inputManager?.unregisterInputDeviceListener(input.inputDeviceListener)
        dual.hidePresentation()
        launchPipeline.onDestroy()
        // ON_DESTROY goes to the lifecycle observers before this body (API 29+, onActivityPreDestroyed) or from within
        // super.onDestroy() (older): either way GLRetroView's LibretroDroid.destroy() has run by the release below.
        super.onDestroy()
        gameNetplay.onDestroyed()
        sessionClaim?.let { sessions.release(it.token) }
        sessionClaim = null
        screens--
        if (latest?.get() === this) latest = null
        // The game process ends with its last screen's finish (not a recreation: the GLES relaunch, a config change).
        val endsProcess = isFinishing && screens == 0 && AppProcess.isGame()
        // Ending the process, the record stays until the process is gone: the menu waits for that before it starts another
        // game, which must never land in this dying process. It reads as stale then (its pid is gone).
        if (!endsProcess) liveGameStore.clear(Process.myPid(), launchId)
        val endUse = usingData
        usingData = false
        val app = applicationContext
        if (endsProcess) { ending = true; GameHeartbeat.start(app) }   // a finish without Quit (Back) beats from here
        // After the core's last write (destroyed above); a Binder call to the menu process, so not on this thread.
        if (endUse || endsProcess) thread(name = if (endsProcess) "game-process-end" else "data-use-end") {
            if (endUse) runCatching { DataRoot.useEnd(app) }.onFailure { NxLog.w(TAG_LOG, "could not end the data use", it) }
            if (endsProcess) endProcess()
        }
        coreWatchdog?.stop()   // last: a hung core also hangs the native destroy above, which it must still catch
        coreWatchdog = null
    }

    /**
     * The game is over: its process goes, and with it everything the core used or leaked. Off the main thread, after the
     * writes still queued (a Home auto state, play time's last session, the netplay shutdown and session copy) and the
     * data use's end (the caller's).
     */
    private fun endProcess() {
        SaveSession.awaitAutoWrites()
        GameTimeService.flush()
        runCatching { NetplayEngine.executor.submit {}.get(PROCESS_END_WAIT_S, TimeUnit.SECONDS) }
        runCatching { PairSaveFiles.writer.submit {}.get(PROCESS_END_WAIT_S, TimeUnit.SECONDS) }   // a Cable Link save's commit
        ProviderPreferences.awaitSends(TimeUnit.SECONDS.toMillis(PROCESS_END_WAIT_S))   // settings applied in the game's last moments
        NxLog.i(TAG_LOG, "the game has ended: ending its process")
        Process.killProcess(Process.myPid())
    }

    /**
     * The core has been stuck in one frame past [CoreWatchdog.LIMIT_MILLIS]; on the watchdog's thread, since the main
     * thread may be blocked on the core's lock. Nothing in this process can stop the core or take that lock, so the game
     * goes with the process: finished first (a binder call, whatever the main thread is doing) so Android does not
     * recreate it, then the game process ends; the menu (in its own process) comes back to the front and tells the user
     * ([CoreHangNotice]). The battery save was written when the menu last opened and with the last auto state; play time
     * keeps its last minute ([startGameTimeTicker]).
     */
    private fun onCoreHung(stalledMillis: Long) {
        val title = intentTitle ?: "The game"
        NxLog.e("GameActivity", "the core has been stuck in one frame for ${stalledMillis / 1000} s: closing $title")
        runCatching { CoreHangNotice.record(applicationContext, title) }
        runCatching { finish() }.onFailure { NxLog.w("GameActivity", "could not finish the hung game", it) }
        SystemClock.sleep(HUNG_FINISH_GRACE_MS)   // the finish reaches the system before the process goes
        android.os.Process.killProcess(android.os.Process.myPid())
    }

    /** A foreground kill (crash, OOM) closes the session at the last touch: keep it at most a minute old while started. */
    private fun startGameTimeTicker(rec: GameTimeRecorder) {
        gameTimeTicker?.cancel()
        if (!lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) { gameTimeTicker = null; return }   // onStart restarts it
        gameTimeTicker = lifecycleScope.launch { while (true) { delay(GameTimeStore.TOUCH_MS); rec.touch() } }
    }

    private fun endGameTime() {
        gameTimeTicker?.cancel(); gameTimeTicker = null
        gameTime?.end(); gameTime = null
    }

    /**
     * The window opens translucent from Android 11 (Theme.NxMobile.Game, values-v30), the menu showing through until
     * this screen's first frame is up; a frame after it, it turns opaque on black, so the menu behind can stop.
     */
    private fun opaqueAfterFirstFrame() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        val decor = window.decorView
        decor.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                decor.viewTreeObserver.removeOnPreDrawListener(this)
                decor.postOnAnimation {
                    decor.postOnAnimation {
                        if (isDestroyed) return@postOnAnimation
                        window.setBackgroundDrawable(ColorDrawable(android.graphics.Color.BLACK))
                        setTranslucent(false)
                    }
                }
                return true
            }
        })
    }

    /**
     * Vulkan passed the support probe but could not start ([VulkanFallback]): the core is recorded on this
     * phone and the screen is recreated with this launch's extras plus [EXTRA_FORCE_GLES] (the same game, resume slot
     * and launch, which the new screen reports to the launcher). Recreated, not finished and started again: a start from
     * a finishing activity is forced into a new task (the game would leave the menu's task), and singleTop would hand a
     * live one the intent. The old screen's onDestroy (the core's native destroy, the session release) runs before the
     * new onCreate; not finishing, it keeps the launch for it.
     */
    private fun relaunchOnGles() {
        if (isFinishing || relaunching || failShown) return
        relaunching = true
        settings.recordVulkanFailure(gameCoreId)
        NxLog.w(TAG_LOG, "Vulkan could not start for $gameCoreId: starting again on OpenGL ES")
        android.widget.Toast.makeText(applicationContext, VulkanFallback.NOTICE, android.widget.Toast.LENGTH_LONG).show()
        intent = Intent(intent).putExtra(EXTRA_FORCE_GLES, true)
        recreate()
    }

    /** [title]: "Netplay failed" for a netplay session's failures, which may come mid-game. */
    override fun fail(message: String, title: String) {
        NxLog.e(TAG_LOG, message)
        if (relaunching) return   // a late error from the Vulkan attempt: the recreated screen owns the launch now
        // Destroyed without finishing: a recreation, whose new screen owns the launch (and its launch record), and
        // a dialog on this dead window would throw BadTokenException.
        if (isDestroyed && !isFinishing) return
        launchRecords.drop(launchId)   // refused: the launcher must not record it
        if (isFinishing || isDestroyed || failShown) return   // observeErrors can report more than once: one dialog, never a stack
        failShown = true
        rumbles.forEach { it.release() }
        // A controller's A only focuses the button (Android clicks on ENTER): A, B and BACK leave on release. A release
        // needs its press here first, so a button held from the game as the dialog opens does not close it unseen.
        var pressed = -1
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setCancelable(false)
            .setPositiveButton("Back") { _, _ -> finish() }
            .setOnKeyListener { dialog, keyCode, event ->
                val leaves = keyCode == KeyEvent.KEYCODE_BACK || PadKeymap.buttonFor(keyCode).let { it == NxButton.A || it == NxButton.B }
                if (leaves) when (event.action) {
                    KeyEvent.ACTION_DOWN -> if (event.repeatCount == 0) pressed = keyCode
                    KeyEvent.ACTION_UP -> if (pressed == keyCode) { dialog.dismiss(); finish() }
                }
                leaves
            }
            .show()
            .also { dialog ->
                // Android 16+ (targetSdk 36) sends Back to the dialog's callbacks, not its key listener: leave the same way.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) dialog.onBackInvokedDispatcher.registerOnBackInvokedCallback(
                    OnBackInvokedDispatcher.PRIORITY_OVERLAY,
                ) { dialog.dismiss(); finish() }
            }
    }

    /** Opens the game's content and companions, SAF documents or file refs. */
    private val refs by lazy { AndroidRefs(contentResolver) }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // A move to another display arrives as a configuration change: the second screen is never the game's own display.
        dual.secondScreens.refresh()
        // 3DS: the picture's space was the other orientation's, so the layout's sync below ([syncN3dsCore]) sends Large
        // screen's half size (and watches for its frame) until the relayout fills the new space ([onN3dsPictureSpace]).
        if (isN3ds) renderer.forgetN3dsPictureSpace()
        padLayout.applyLayout(newConfig.orientation)   // also re-sizes the menu and the notifications to the game area; syncs the 3DS core
        wii.onConfigurationChanged()
        if (retroView != null) {
            // The overlay set follows the orientation (768p portrait, 720p landscape); the size change re-lays out.
            renderer.updateOverlay(FrontendSettings.parse(frontendValues))
            findViewById<FrameLayout>(R.id.gameContainer).post { renderer.relayoutFrontend() }
        }
    }

    /**
     * The one landscape test for the pad layout ([applyLayout]) and the DS layout ([ndsLayout], its hotkeys): anything not
     * portrait, so the landscape skin layout and the DS landscape layout (with its pad-over-game touch rule) always agree.
     */
    override fun isLandscape(orientation: Int): Boolean =
        orientation != Configuration.ORIENTATION_PORTRAIT

    override fun isLandscape(): Boolean = isLandscape(resources.configuration.orientation)

    /**
     * 3DS: the core's layout from the row in use (orientation, controller) and the cursor kept shown in stylus mode.
     * A value the core has is not sent again.
     */
    override fun syncN3dsCore() {
        if (!isN3ds) return
        val screen = screenClass()
        if (renderer.n3dsViews) {
            // The core keeps its Default: a layout, proportion, rotation or fold is the app's to draw, at once.
            renderer.n3dsComposeLayout = N3dsOptions.coreLayoutFor(frontendValues, isLandscape(), renderer.ndsControllerMode(), screen, n3dsSkinStack())
            renderer.n3dsProportion = n3dsProportion(screen = screen)
            applyOptions(listOf(
                Variable(N3dsOptions.CURSOR_VISIBLE, N3dsOptions.cursorVisible(input.n3dsHotkeys.stylus)),
                // Stylus mode steers the core's pointer with the right stick, which is the C-stick alone outside it.
                Variable(N3dsOptions.ANALOG_FUNCTION, N3dsOptions.analogFunction(input.n3dsHotkeys.stylus)),
            ))
            renderer.relayoutFrontend()
            return
        }
        val options = listOf(
            Variable(N3dsOptions.LAYOUT, N3dsOptions.coreLayoutFor(frontendValues, isLandscape(), renderer.ndsControllerMode(), screen, n3dsSkinStack())),
            // Large screen halves the touch screen (portrait: fills the height left), Stacked keeps both alike: a layout
            // change updates it too.
            Variable(N3dsOptions.STACKED_SMALL_PROPORTION, n3dsProportion(screen = screen)),
            Variable(N3dsOptions.CURSOR_VISIBLE, N3dsOptions.cursorVisible(input.n3dsHotkeys.stylus)),
            Variable(N3dsOptions.ANALOG_FUNCTION, N3dsOptions.analogFunction(input.n3dsHotkeys.stylus)),
        ).withN3dsSplit(DualScreen.n3dsForcedOptions(dual.dualMode, n3dsSkinStack()))
        // Half-folded pins the swap (the app's SWAP moves the screens across the hinge): the user's values are noted as it
        // starts and put back when flat again. A second screen's split holds them meanwhile, and puts back none while
        // half-folded ([DualScreenController.restoreN3dsScreen]).
        val pinned = n3dsHalfFoldedPin(dual.dualMode)
        if (pinned && !n3dsSwapPinnedHalfFolded) dual.captureN3dsSwap()
        // Azahar reports the new frame size some frames after its layout changes (a hotkey or menu cycle, a rotation, a
        // second screen's split or its restore): lay out again when it arrives, if the view's geometry flow has not.
        // Large screen and Stacked share the core layout and differ only in the touch screen's proportion, so either counts.
        // A fold or unfold moves the screens' regions at once, ahead of the new frame: the picture is held until it comes.
        val layout = options.filter { it.key == N3dsOptions.LAYOUT || it.key == N3dsOptions.STACKED_SMALL_PROPORTION }.associate { it.key to it.value }
        val halfFolded = n3dsSkinStack()
        if (lastN3dsLayout != null && layout != lastN3dsLayout) renderer.watchGeometry(holdPicture = halfFolded != lastN3dsHalfFolded)
        lastN3dsLayout = layout
        lastN3dsHalfFolded = halfFolded
        applyOptions(options)
        if (!pinned && n3dsSwapPinnedHalfFolded && DualScreen.n3dsCoreOptions(dual.dualMode) == null) dual.restoreN3dsScreen()
        n3dsSwapPinnedHalfFolded = pinned
    }

    /**
     * Half-folded with no second screen in use pins the core's swap ([DualScreen.n3dsForcedOptions]); never under netplay,
     * whose core options stay as the session launched them.
     */
    private fun n3dsHalfFoldedPin(mode: DualMode): Boolean = n3dsSkinStack() && mode == DualMode.OFF && !netplayLocks

    /** The core's swap was last pinned by half-folding ([syncN3dsCore]): flat again puts the user's values back. */
    private var n3dsSwapPinnedHalfFolded = false
    /** The player's Swap Screen Mode (Toggle / Hold), which the app's SWAP follows under video views. */
    private var n3dsSwapMode = "Toggle"

    /**
     * A 3DS under a pad skin cuts its screens from the Stacked frame: the core is then Stacked with equal screens
     * ([N3dsOptions.coreLayoutFor]), whatever layout the player saved. Every layout ([PadLayoutController.applyLayout])
     * calls [syncN3dsCore]. A second screen's split still wins ([withN3dsSplit]). Not under netplay, which keeps the
     * player's layout (drawn whole in the skin).
     */
    private fun n3dsSkinStack(): Boolean = isN3ds && padLayout.skinShown && !netplayLocks

    /** A running session that allows no option changes ([NetplaySession.locksOptions]); a 3DS link is not one. */
    private val netplayLocks: Boolean get() = netplayNow?.locksOptions == true

    /** On a DS or 3DS game where SELECT + Left / Right changes the layout: the tip saying so, until hidden from its button ([LayoutTip]). */
    private fun offerLayoutTip() {
        if (!LayoutTip.due((isN3ds || isNds) && !netplayLocks, padLayout.skinShown, input.skinLayouts, settings.layoutTipHidden)) return
        // up until closed: Close, or its button to hide it for good
        input.showNotice(LayoutTip.TEXT, com.nxredux.mobile.ui.nx.NotificationQueue.UNTIL_CLOSED, LayoutTip.ACTION) { settings.layoutTipHidden = true }
    }
    override val n3dsStackPinned: Boolean get() = !netplayLocks

    /**
     * The 3DS touch screen's proportion for the row in use ([N3dsOptions.stackedSmallProportion]): Large screen in portrait
     * fills the height left in the picture's space when it is known ([FrontendRenderer.n3dsPictureSpace]).
     */
    private fun n3dsProportion(landscape: Boolean = isLandscape(), screen: ScreenClass = screenClass()): String =
        N3dsOptions.stackedSmallProportion(frontendValues, landscape, renderer.ndsControllerMode(), screen, n3dsSkinStack(), renderer.n3dsPictureSpace)

    /**
     * The 3DS was laid out flat: a Large screen in portrait whose space now gives another proportion than the core has
     * ([FrontendRenderer.n3dsProportion]) sends it, as a layout switch ([syncN3dsCore]): the new frame is watched for and
     * the 3D scale held until it comes. Not while a second screen's split or the half-folded pin holds the core's options,
     * nor under netplay (no option changes mid-session).
     */
    override fun onN3dsPictureSpace() {
        if (!isN3ds || netplayLocks || DualScreen.n3dsForcedOptions(dual.dualMode, n3dsSkinStack()) != null) return
        val proportion = n3dsProportion()
        if (renderer.n3dsViews) {
            if (proportion != renderer.n3dsProportion) { renderer.n3dsProportion = proportion; renderer.relayoutFrontend() }
            return
        }
        if (proportion == renderer.n3dsProportion) return
        lastN3dsLayout = lastN3dsLayout?.plus(N3dsOptions.STACKED_SMALL_PROPORTION to proportion)
        renderer.watchGeometry(why = "3DS Large screen proportion $proportion sent")
        applyOptions(listOf(Variable(N3dsOptions.STACKED_SMALL_PROPORTION, proportion)))
    }

    /** The core layout and touch-screen proportion last sent for the 3DS ([syncN3dsCore]); null before the first. */
    private var lastN3dsLayout: Map<String?, String?>? = null
    /** Whether that layout was the half-folded one: a change between the two holds the picture for the new frame. */
    private var lastN3dsHalfFolded = false

    /** The tag the layout goes by: the intent's until the launch sets [gameTag] (onCreate lays out before it). */
    override fun layoutTag(): String = intentTag ?: gameTag

    // ------------------------------------------------------------------ frontend (display) settings

    /** Applies frontend [values] (every frontend key) to the view: scaling, sharpness, overlay, offsets, HUD. */
    override fun applyFrontend(values: Map<String, String>) {
        val oldSegaSetting = frontendValues[PadOptions.SEGA_PAD] ?: SegaPadType.AUTO
        val oldBottomScreen = frontendValues[BottomScreenOptions.KEY]
        val res3dKeys = res3d.console?.keys.orEmpty()
        val old3d = res3dKeys.map { frontendValues[it] }
        frontendValues = values
        // Display → 3D Resolution (the menu, live): the new value goes at once, not as a size change.
        if (res3d.applies() && res3dKeys.map { values[it] } != old3d) res3d.offer(sizeChange = false)
        // Display → Bottom screen (the menu, live): the picture may move. Posted, as for the Sega pad.
        if (values[BottomScreenOptions.KEY] != oldBottomScreen) findViewById<View?>(R.id.root)?.post { dual.recheckPictureOn() }
        val segaSetting = values[PadOptions.SEGA_PAD] ?: SegaPadType.AUTO
        if (segaSetting != oldSegaSetting && PadMap.isSegaPadTag(gameTag)) applySegaPad(segaSetting)
        if (isN3ds) {
            applyN3dsNewControls(values[N3dsOptions.NEW_CONTROLS])
            syncN3dsCore()   // a layout row changed in the menu, or the hotkey's
        }
        wii.apply(values)
        speed.configure(SpeedSettings.parse(values, canRewind), canRewind)   // applied once the view exists, and on every row change
        val s = FrontendSettings.parse(values)
        padLayout.padLandscapeAlpha.value = s.padLandscapeAlpha
        val view = retroView ?: return
        view.resamplingQuality = AudioOptions.qualityOf(values)
        view.audioBufferMs = AudioOptions.bufferMsOf(values, gameTag)   // read when the game's audio starts
        view.fillAudioGaps = AudioOptions.fillGapsOf(values)
        view.frameSkip = FrameSkipOptions.skipOf(values, gameTag)
        view.scaleMode = when (s.scaling) {
            ScreenScaling.NATIVE -> ScaleMode.INTEGER
            // Auto: the viewport is the picture (or its overlay box) as the renderer placed it; the game fits it.
            ScreenScaling.AUTO, ScreenScaling.ASPECT -> ScaleMode.FIT
            ScreenScaling.FULLSCREEN -> ScaleMode.STRETCH
        }
        val shader = renderer.shaderFor(s)
        if (view.shader != shader) view.shader = shader
        renderer.updateOverlay(s)
        renderer.relayoutFrontend()
        renderer.setHud(s.debugHud)
    }

    /**
     * A Controller Type change mid-game (menu row, Restore Defaults): the pad is resolved again (Auto from the header
     * read at launch), the table and on-screen pad follow, and the core gets it now on the emulation thread
     * (genesis_plus_gx's port device, picodrive's `picodrive_input1`). The game keeps running.
     */
    private fun applySegaPad(setting: String) {
        if (!menuOpen) input.releaseAllGameInput()   // game keys held through the old table let go before it changes
        segaPad = SegaPadType.resolve(setting, segaHeader)
        padMap = PadMap.forTag(gameTag, segaPad)
        dropPadProfile()
        // Before the first frame the core may not be loaded (a device set then is overwritten by the launch's ports):
        // the first-frame block sends the setting if it differs from the one the core was launched with.
        if (gameReady) sendSegaPad(setting)
        relayoutPadAfterPress()
    }

    /**
     * A New 3DS Controls change (menu row, Restore Defaults): resolved again (Auto from the title read at launch) and, when
     * that changes what the pad draws, the pad is rebuilt at once. The core needs nothing: ZL, ZR and its right stick are
     * always read.
     */
    private fun applyN3dsNewControls(setting: String?) {
        val resolved = N3dsNewControls.resolve(setting, n3dsTitle)
        if (resolved == n3dsNew) return
        n3dsNew = resolved
        if (!menuOpen) input.releaseAllGameInput()
        input.n3dsPadRight = 0f to 0f
        input.syncN3dsCursor()
        dropPadProfile()
        relayoutPadAfterPress()
    }

    override fun connectDolphinPorts() = wii.connectDolphinPorts()

    override fun recenterWiiMotion() = wii.recenterMotion()

    override fun pressWiiHome() = wii.pressHome()

    override fun padMotion(): PadMotion? = wii.padMotion()

    override fun padMic(): PadMic? = shortcuts.padMic()

    /**
     * The keys the menu leaves out: the .cfg's locks, Motion Controls on a Wii game without both sensors, both Wii rows
     * for a GameCube disc under the WII tag, and the Rewind rows unless the core can rewind ([canRewind]: an unknown core
     * can't, as the shortcuts have it).
     */
    override fun menuLocked(): Set<String> {
        val noRewind = if (!canRewind) SpeedOptions.REWIND_KEYS else emptySet()
        // A pad skin sets the screens' size and place: its scaling, offsets, overlay and screen layouts are not offered.
        val skinned = if (padLayout.skinShown) SKIN_HIDDEN_OPTIONS else emptySet()
        return noRewind + skinned + when {
            wii.gameCube -> lockedOptions + WiiOptions.table.map { it.key }
            isWii && !wii.hasSensors -> lockedOptions + WiiOptions.MOTION
            else -> lockedOptions
        }
    }

    /** Sends Controller Type [setting] to the running core on the emulation thread (each player's port device or core option). */
    private fun sendSegaPad(setting: String) {
        if (netplayNow != null) return   // netplay: the pad type is fixed for the session (set at launch)
        val view = retroView
        val changes = SegaPadType.liveChanges(gameCoreId, setting, segaHeader, playerCount)
        val ports = changes.filterIsInstance<SegaPadChange.Port>()
        if (ports.isNotEmpty()) view?.queueEvent {
            ports.forEach { c ->
                runCatching { view.setControllerType(c.port, c.device) }
                    .onFailure { NxLog.w(TAG_LOG, "setting the Sega pad failed", it) }
            }
        }
    }

    override fun relayoutFrontend() = renderer.relayoutFrontend()

    override fun persistConsoleKey(key: String, value: String, failure: String) =
        persistKey(key, failure, ConfigSource.CONSOLE) { it.saveConsoleKey(key, value) }

    override fun persistGameKey(key: String, value: String, failure: String) =
        persistKey(key, failure, ConfigSource.GAME) { it.saveGameKey(key, value) }

    /**
     * One key written to this console's or game's config ([save], off main and in turn with the other writes); its locks
     * apply once written. The game's config is the one in effect from then on; the console's only when none was.
     */
    private fun persistKey(key: String, failure: String, source: ConfigSource, save: (CoreConfigStore) -> ConfigFile) {
        val store = configStore ?: return
        lifecycleScope.launch {
            val written = configWrites.withLock {
                withContext(Dispatchers.IO) {
                    runCatching { save(store) }
                        .onFailure { NxLog.w("GameActivity", "saving $key failed", it) }.getOrNull()
                }
            }
            if (written == null) { toast(failure); return@launch }
            if (source == ConfigSource.GAME || configSource == ConfigSource.NONE) configSource = source
            lockedOptions = written.locked + forcedOptions
        }
    }

    // ------------------------------------------------------------------ Nintendo 3DS controls

    /**
     * Sets core options on the emulation (GL) thread, where the core reads them; queued, so a later
     * [withCoreOptions] read sees them.
     */
    override fun applyOptions(vars: List<Variable>) {
        if (netplayLocks) return   // netplay: no core option changes mid-session (the other device would not follow)
        val view = retroView ?: return
        if (vars.isEmpty()) return
        vars.firstOrNull { it.key == N3dsOptions.STACKED_SMALL_PROPORTION }?.value?.let { renderer.n3dsProportion = it }
        view.queueEvent { runCatching { view.updateVariables(*vars.toTypedArray()) }.onFailure { NxLog.w("GameActivity", "setting options failed", it) } }
    }

    private suspend fun readDolphinBuiltIn(): String? = withContext(Dispatchers.IO) {
        val file = File(dolphinBuiltInPath ?: return@withContext null)
        if (file.isFile) runCatching { file.readText() }.onFailure { NxLog.w("GameActivity", "cannot read Dolphin's cheats", it) }.getOrNull() else null
    }

    /** First launch of a Wii/GameCube game: Dolphin has written its built-in list by the first frame, so combine again. */
    private suspend fun refreshDolphinCheats() {
        val player = dolphinPlayerCheats ?: return
        val builtIn = readDolphinBuiltIn() ?: return
        dolphinPlayerCheats = null
        cheatSet = DolphinCheats.refresh(builtIn, player, cheatSet.enabledKeys(), dolphinSavedCheats)
        publishCheats()
    }

    private fun publishCheats() {
        withMenu { it.setCheats(cheatSet.entries, cheatsFolder, cheatSet.expected) }
        dual.refreshDualPanel()
    }

    /**
     * Sends the enabled cheats to the core on the emulation thread in one job: a reset, then only the enabled
     * ones with contiguous indices (PCSX-ReARMed treats the index as a slot and skips a cheat behind a gap,
     * libretro issue #116), and the enabled RAM patches, which replace the last set. Caught so a JNI failure never kills
     * the GL thread.
     */
    override fun applyCheats(view: GLRetroView) {
        val codes = cheatSet.enabledCodes()
        // RetroArch RAM patches: the native side writes them before every frame (none outside a netplay session either).
        val patches = cheatSet.enabledPatches().flatMap { it.packed().asList() }.toIntArray()
        view.queueEvent {
            runCatching {
                LibretroDroid.resetCheat()
                codes.forEachIndexed { i, code -> view.setCheat(i, true, code, useEmulationThread = false) }
                LibretroDroid.setMemoryCheats(patches)
            }.onFailure { NxLog.w("GameActivity", "applying cheats failed", it) }
        }
    }

    /**
     * Quit: single-shot, then the quit's saves ([SaveSession.writeOnQuit]) and finish. [chosen]: the player picked Quit
     * (the slot file then points at the auto state); [autoState] false keeps the resume point as is.
     */
    private fun quitWithAutoState(chosen: Boolean, autoState: Boolean = true) {
        if (quitting) return   // single-shot: a second job could serialize after the core is destroyed
        quitting = true
        syncDsMenuCap()   // the cap must not leave the room mid-quit
        markClosing()
        GameHeartbeat.start(this)   // the menu may be waiting for this process to end: its saves can take a while
        rumbles.forEach { it.release() }
        shortcuts.pushMicSource()   // silence now, so the phone's mic closes before the save, not at the pause
        endGameTime()
        val view = retroView
        input.gcClearTriggers()   // a latched soft press never outlives the game
        // Detached now: its events stop reaching the screen (no toast during the quit). Its native unload takes the core's
        // lock, so it waits off the main thread for the quit's read of the core.
        val ach = achievements
        ach?.detach()
        achievements = null
        if (view == null || !gameReady) { ach?.stop(); finish(); return }   // not running yet: no save to write
        saves.writeOnQuit(view, chosen, autoState, release = { live -> ach?.stop(native = live) }, finish = ::finish)
    }

    override fun refreshSlots() = saves.refreshSlots()

    override fun saveToSlot(slot: Int) = saves.saveToSlot(slot)

    override fun loadFromSlot(slot: Int) = saves.loadFromSlot(slot)

    // ------------------------------------------------------------------ netplay

    /**
     * The launch's link state (Local Wireless, the Wi-Fi addresses, the route) lives in the pipeline. A DS or 3DS game that
     * falls back to the automatic route leaves its room now: Azahar reads the new mode only on its next frame, which an
     * open menu holds back, and until then its session would still count as in a room for the menu, states and locks
     * ([rejoinSessionRoom] keeps it out meanwhile). The check lands the presence at once, and the cable watcher a launch
     * on that route starts (once) starts here.
     */
    override fun leaveLinkSession(kind: NetplayLinkKind): Map<String, String> {
        val left = launchPipeline.leaveLinkSession(kind)
        if (kind != NetplayLinkKind.PSPLINK && launchPipeline.dsAutoRoute) {
            LibretroNetplay.dsLeaveNow()
            dsRoom.check()
            if (launchPipeline.dsCableAuto) dsCable.watch()
        }
        return left
    }

    /**
     * netplay link: the session ended (the other player left, it dropped, or this player left). Each player ran their own game on their own
     * save (never a copy: [NetplaySession.onCopy]), so from here it is the normal launch of that game: save states, the
     * auto state on Quit and on stop, cheats (loaded now, the saved ones applied), Options and the panel, as [netplayNow]
     * is null. The link engine is idle already (or shutting down after Leave); [netplay] stays for onDestroy (the engine release, the USB cable).
     * Achievements need nothing here: a link session ran them from launch ([NetplaySession.allowsAchievements]). [localCheats]:
     * the cheat load a normal launch starts at once ([LaunchPipeline]), for a session that held it back.
     */
    override fun becomeLocal(localCheats: (() -> Job)?) {
        speed.setSession(netplay = false)   // the shortcuts work again
        shortcuts.onSessionEnded()
        saves.openLocal(dataRoot, paths) { slot -> withMenu { it.showSlot(slot - 1) } }
        localCheats?.let { load ->
            val job = load().also { cheatsLoad = it }
            lifecycleScope.launch {
                job.join()
                if (!quitting && cheatSet.hasEnabled()) retroView?.let(::applyCheats)
            }
        }
        dual.refreshDiscs()
        dual.refreshDualPanel()
        // The core kept the launch's DS / 3DS 3D scale through the session: the one for the layout now goes once.
        if (res3d.applies()) res3d.offer(sizeChange = false)
    }

    // ------------------------------------------------------------------ achievements

    /**
     * Before the view exists: an RA console, signed in and enabled, gets a bridge and a background hash. An archive's
     * unpacked [inner] ROM is what is hashed, and its name refines the console.
     */
    private fun prepareAchievements(tag: String, romFileName: String, content: ContentRef, inner: File?) {
        if (!settings.raEnabled || settings.raToken == null) return
        val consoleId = RaConsoles.consoleId(tag, inner?.name ?: romFileName) ?: return
        val controller = AchievementsService.controller(this, settings, AndroidLibraryIndexStore(this))
        val hasher = AndroidRaHasher(this, AndroidRomsSources.of(this))
        val folder = intent.getStringExtra(EXTRA_FOLDER_URI) ?: ""
        val pointer = intent.getStringExtra(EXTRA_GAME_PATH)?.let(GamePath::split)?.let { ref ->
            RaGamePointer(tag, ref.sourceId, ref.relativeDir, ref.fileName, intentTitle ?: ref.fileName)
        }
        // The launch's place in hashes.json; always completed (null on a failure) so the bridge's await never hangs.
        val launch = CompletableDeferred<RaLaunchFingerprint?>()
        val hashJob = lifecycleScope.async(Dispatchers.IO) {
            try {
                val ref = content.value
                // The shared per-folder hashes.json, so a game is hashed once while its file is unchanged (a disc takes a while).
                val target = pointer?.let {
                    RaLaunchTarget(it, hasher.stamp(ref), consoleId, RaFingerprintKeys.storable(tag, romFileName, consoleId, unpacked = inner != null))
                }
                var computed = false
                val (h, l) = RaLaunchHash.of(controller.fingerprints, target) {
                    computed = true
                    if (inner != null) hasher.hashUnpacked(tag, inner) else hasher.hash(tag, ref, romFileName, folder)
                }
                launch.complete(l)
                NxLog.i(TAG_LOG, if (!computed) "RA hash: cached" else if (h == null) "RA hash: unidentified" else "RA hash: computed")
                h
            } finally {
                launch.complete(null)   // no-op once completed above
            }
        }
        achievements = AchievementsBridge(controller, settings, lifecycleScope).also { it.prepare(consoleId, hashJob, pointer, launch) }
        if (settings.raUnlockSound) unlockMoments.prepare()
    }

    /** Idempotent; onDestroy and a finishing onStop both reach it (a quit detaches it itself: quitWithAutoState). */
    private fun stopAchievements() {
        achievements?.stop()
        achievements = null
        unlockMoments.release()
    }

    /** The Options row and Achievements page: hidden without a session, "Not recognised" while there is no set. */
    override fun applyAchievements(model: InGameMenuModel) {
        val b = achievements?.takeIf { it.available } ?: return model.setAchievementsAvailable(false)
        val summary = b.summary()
        model.setAchievements(if (summary != null) b.list() else null, summary, settings.raSortOrder, b.muted, b.badges)
    }

    private fun refreshMenuAchievements() {
        withMenu { applyAchievements(it) }
        dual.refreshDualPanel()
    }

    override fun toggleMute(id: Int) {
        val b = achievements ?: return
        lifecycleScope.launch {
            runCatching { b.toggleMute(id) }.onFailure { NxLog.w("GameActivity", "mute toggle failed", it) }
            refreshMenuAchievements()
        }
    }

    /** An unlock's chime and screenshot, each by its own setting (never tied to the toast's). Main thread. */
    private fun onUnlockMoment(a: RaAchievement) {
        if (settings.raUnlockSound) unlockMoments.play()
        if (!settings.raUnlockScreenshot) return
        val game = intentTitle ?: "Game"
        // The game's picture alone, at its own size: the toast is drawn over the view, never in its surface.
        saves.captureThumbnail(fullSize = true) { picture ->
            picture ?: return@captureThumbnail
            lifecycleScope.launch(Dispatchers.IO) {
                try { unlockMoments.save(picture, game, a.title) } finally { picture.recycle() }
            }
        }
    }

    private fun onAchievementEvent(e: RaEvent) {
        val b = achievements ?: return
        val now = SystemClock.elapsedRealtime()
        val toastMs = settings.raNotificationSeconds * 1000L
        fun badge(a: RaAchievement) = RaBadgeFolders.fileName(a.badge, locked = false)?.let(b.badges::get)
        fun notice(text: String) { notifications.push(text, null, toastMs, now) }
        // Off: only the session notices (loaded, unknown, sign-in, offline, first-time setup, synced) still show.
        val shown = settings.raShowNotifications
        when (e) {
            is RaEvent.Unlocked -> {
                if (shown) notifications.push("Achievement Unlocked\n${e.a.title}", badge(e.a), toastMs, now)
                onUnlockMoment(e.a)
            }
            is RaEvent.Progress -> if (shown) notifications.progress(e.a.title, e.a.progress ?: "", badge(e.a), settings.raProgressSeconds * 1000L, now)
            RaEvent.ProgressHide -> notifications.hideProgress()
            RaEvent.Mastered -> if (shown) notice("Game Mastered!")
            is RaEvent.ServerError -> if (shown) notice("RA server error: ${e.message}")
            RaEvent.Disconnected -> if (shown) notice("RetroAchievements: connection lost")
            RaEvent.Reconnected -> if (shown) notice("RetroAchievements: reconnected")
            is RaEvent.GameLoaded -> notice("${e.title} · ${e.unlocked}/${e.total} achievements")
            RaEvent.GameUnknown -> notice("No achievements for this game")
            is RaEvent.LoginFailed -> notice("RetroAchievements sign-in failed")
            RaEvent.SessionOffline -> notice("RetroAchievements: offline")
            RaEvent.SessionDisabled -> notice("RetroAchievements needs a network connection for first-time setup")
            is RaEvent.Synced -> notice("${e.count} offline achievement${if (e.count == 1) "" else "s"} synced")
            RaEvent.BadgesReady -> Unit
        }
        // The session changed what the menu shows (row, summary, the set): refresh it if it is open.
        when (e) {
            is RaEvent.GameLoaded, RaEvent.GameUnknown, is RaEvent.LoginFailed, RaEvent.SessionDisabled, RaEvent.SessionOffline,
            is RaEvent.Unlocked, is RaEvent.Synced, RaEvent.BadgesReady -> refreshMenuAchievements()
            else -> Unit
        }
        publishNotifications()
    }

    /**
     * Kids Mode's 10-minute warning from the main process ([com.nxredux.mobile.kids.AndroidKids.warn]), an ordered
     * broadcast: only while this screen is resumed (in front) is it the overlay's toast, answered [KIDS_WARN_SHOWN];
     * otherwise the main process posts a notification.
     */
    private val kidsWarn = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (!lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return
            resultCode = KIDS_WARN_SHOWN
            notifications.push(KIDS_WARNING, null, KIDS_WARNING_MS, SystemClock.elapsedRealtime())
            publishNotifications()
        }
    }

    /** Publishes the queue and keeps a 250 ms expiry tick running while anything is shown. */
    override fun publishNotifications() {
        notificationState.value = notifications.toasts to notifications.pill
        if (tickJob?.isActive == true) return
        tickJob = lifecycleScope.launch {
            while (notifications.toasts.isNotEmpty() || notifications.pill != null) {
                delay(250)
                if (notifications.tick(SystemClock.elapsedRealtime())) notificationState.value = notifications.toasts to notifications.pill
            }
        }
    }

    /** Display-only overlay over the game area (portrait: above the pad band while it is shown), below the menu; takes no input. */
    private fun ensureNotificationsView() {
        if (notificationsView != null) return
        val view = ComposeView(this).apply {
            isClickable = false
            isFocusable = false
            setContent {
                val config = LocalConfiguration.current
                NxTheme {
                    val (toasts, pill) = notificationState.value
                    // The overlay starts at the screen top (under a portrait cutout). In portrait with the pad band shown
                    // it ends above the band, not at the bottom edge, so the bottom is not inset; otherwise (landscape, or
                    // the band dropped for a controller) it reaches the bottom edge and every side is inset.
                    val band = config.orientation == Configuration.ORIENTATION_PORTRAIT && padLayout.portraitBandPx() > 0
                    val sides = if (band) WindowInsetsSides.Horizontal + WindowInsetsSides.Top
                        else WindowInsetsSides.Horizontal + WindowInsetsSides.Vertical
                    // Over the bare skin buttons and over a pad skin (their shoulders or shell hold the corners),
                    // top-centre below any cutout; elsewhere the corners.
                    // In portrait over a pad (bare buttons or a skin) the overlay ends at the picture's bottom instead, so the
                    // toasts sit bottom-left of the picture as in landscape, never over the pad under it.
                    val overPad = padLayout.overlayPadShown.value || padLayout.skinShownState.value
                    val picture = renderer.pictureBox.value
                    val onPicture = overPad && picture != null && config.orientation == Configuration.ORIENTATION_PORTRAIT
                    val placement = nxNotificationPlacement(overPad, onPicture)
                    // Kept off the top corners ([nxTopCentreMaxWidth]) from the cutout inset and this view's size.
                    val density = LocalDensity.current
                    val dir = LocalLayoutDirection.current
                    val cutout = WindowInsets.displayCutout
                    val inset = with(density) {
                        symmetricCutoutInset(cutout.getLeft(density, dir).toFloat(), cutout.getRight(density, dir).toFloat()).toDp().value
                    }
                    // This view's own size: in landscape the root's.
                    var origin by remember { mutableStateOf(IntOffset.Zero) }   // this view's top-left in the window
                    BoxWithConstraints(Modifier.fillMaxSize().onGloballyPositioned { origin = it.positionInWindow().round() }) {
                        val maxW = nxTopCentreMaxWidth(maxWidth.value, maxHeight.value, inset).dp
                        val underPicture = if (onPicture && picture != null) with(density) { (constraints.maxHeight - (picture.bottom - origin.y)).coerceAtLeast(0).toDp() } else 0.dp
                        NxNotifications(
                            toasts, pill, modifier = Modifier.padding(bottom = underPicture),
                            safeSides = if (underPicture > 0.dp) WindowInsetsSides.Horizontal + WindowInsetsSides.Top else sides,
                            placement = placement, topCentreMaxWidth = maxW, onAction = input::onNoticeAction, onDismiss = input::onNoticeDismiss,
                        )
                        // Top-right of the picture ([pictureIndicatorAnchor]); not under the menu. This view sits over
                        // the pad skin, so a skin's shell never covers them. The speed indicator and a Cable Link
                        // session's delay share the corner: shortcuts do nothing in a session, so never both.
                        val kind = speed.indicator.value
                        val delay = gameNetplay.delayShown.value
                        val pic = renderer.pictureBox.value
                        if ((kind != null || delay != null) && pic != null && !menuOpenState.value) {
                            val (right, top) = pictureIndicatorAnchor(
                                pic.right - origin.x, pic.top - origin.y, constraints.maxWidth,
                                cutout.getRight(density, dir), cutout.getTop(density), with(density) { 8.dp.roundToPx() },
                            )
                            val atAnchor = Modifier.layout { m, c ->
                                val p = m.measure(c)
                                layout(0, 0) { p.place(right - p.width, top) }
                            }
                            if (kind != null) NxSpeedIndicator(kind, atAnchor)
                            else if (delay != null) NxDelayIndicator(NetplayText.inputDelay(delay), atAnchor)
                        }
                    }
                }
            }
        }
        notificationsView = view
        findViewById<FrameLayout>(R.id.root).addView(view, menu.menuLayoutParams(resources.configuration.orientation))
    }

    /** Shows [msg] briefly; returns the toast so a caller can take it down early. */
    override fun toast(msg: String): android.widget.Toast =
        android.widget.Toast.makeText(this, msg, android.widget.Toast.LENGTH_SHORT).also { it.show() }

    // ------------------------------------------------------------------ input (GameInputRouter), layout (PadLayoutController), picture (FrontendRenderer), second display (DualScreenController),
    // in-game menu (InGameMenuController)

    override val activity: ComponentActivity get() = this
    override fun ndsControllerMode(): Boolean = renderer.ndsControllerMode()
    override fun screenClass(): ScreenClass = windowShape().screenClass
    // Half-folded (no second screen's mode, which places the screens itself) the screens follow the fold.
    override fun layoutSetByDual(): Boolean = !DualScreen.layoutCycles(dual.dualMode)

    // The second display's state, owned by [dual]: read by the input, layout and picture code.
    override val presentation: SecondScreenPresentation? get() = dual.presentation
    override val dualMode: DualMode get() = dual.dualMode
    override val pictureOn: PictureOn get() = dual.pictureOn
    override val n3dsPicture: NdsScreen get() = dual.n3dsPicture
    override fun secondDsRegions(g: FloatArray, map: ScreenMap?): List<NdsRegion>? = dual.secondDsRegions(g, map)
    override fun secondWholeFrame(g: FloatArray): com.nxredux.mobile.options.PxRect? = dual.secondWholeFrame(g)
    override fun secondRegions(g: FloatArray, ds: List<NdsRegion>?): List<ScreenRegion> = dual.secondRegions(g, ds)
    override fun onN3dsSwapPressed(down: Boolean): Boolean =
        dual.onN3dsSwapPressed(down) || renderer.onSkinN3dsSwap(down) || renderer.onN3dsViewsSwap(down, n3dsSwapMode)
    override fun mainPanelCoversWindow(): Boolean = dual.mainPanelCoversWindow()
    override fun mainShowsNoPicture(): Boolean = dual.mainShowsNoPicture()
    /** A NAOMI / Atomiswave set (flycast's set table, the launcher's rule): read once, the table only for a DC archive. */
    override val dcArcadeSet: Boolean by lazy {
        val name = intent.getStringExtra(EXTRA_FILE_NAME).orEmpty()
        val tag = CoreRegistry.baseTag(layoutTag())
        tag == "DC" && com.nxredux.mobile.library.Names.isArchive(name) &&
            com.nxredux.mobile.skin.DcGames.isArcadeSet(tag, name, NaomiSetsAsset.get(this))
    }
    override fun onSkinPlaced() = dual.syncPanel()
    override fun onLayoutControllersChanged() = dual.recheckPictureOn()
    override fun placeMainPanelHost() = dual.placeMainPanelHost()

    // The in-game menu's view, input and layout, owned by [menu].
    override val menuView: ComposeView? get() = menu.menuView
    override val menuRouter: InputRouter get() = menu.menuRouter
    override fun menuLayoutParams(orientation: Int, overPanel: Boolean, glass: Boolean): FrameLayout.LayoutParams = menu.menuLayoutParams(orientation, overPanel, glass)
    override fun toggleMenu() = menu.toggleMenu()
    override fun backPressed() = if (skinEditView != null) skinEditBack() else menu.backPressed()

    override fun onShortcut(action: ShortcutAction, down: Boolean) = shortcuts.onShortcut(action, down)

    override fun releaseShortcutHolds() = speed.releaseHolds()

    override val padShortcuts: Map<TouchSlot, TouchShortcut> get() = shortcuts.padShortcuts

    override fun onSkinShortcut(action: SkinAction, down: Boolean) = shortcuts.onSkinShortcut(action, down)

    override fun onPadShortcut(slot: TouchSlot, down: Boolean) = shortcuts.onPadShortcut(slot, down)
    override fun cornerPanel(): (() -> Unit)? = dual.cornerPanel()

    override val shortcutKeys: ShortcutKeys? get() = shortcuts.shortcutKeys

    override val shortcutOverrides: ShortcutOverrides get() = shortcuts.shortcutOverrides

    override fun applyShortcuts(overrides: ShortcutOverrides) = shortcuts.applyShortcuts(overrides)

    override fun publishShortcuts(model: InGameMenuModel) = shortcuts.publishShortcuts(model)

    override fun menuCaptureKey(keyCode: Int, down: Boolean, repeat: Boolean): Boolean = menu.captureKey(keyCode, down, repeat)

    override fun speedTiles(): Pair<Boolean, Boolean?> = speed.tiles()

    override fun setSpeedToggle(ff: Boolean?, rewind: Boolean?) = speed.setToggle(ff, rewind)
    override fun micTile(): Boolean? = shortcuts.micTile()

    override fun setMic(on: Boolean) = shortcuts.setMic(on)

    /**
     * A Reset shortcut or the dual-screen panel's Reset tile: reset always asks first, so the in-game menu opens on its
     * Reset dialog; a menu opened for it closes again on Cancel. Never before the game is ready (nothing to reset yet),
     * in netplay, a link session or the DS wireless (the model refuses too).
     */
    override fun askReset() {
        if (quitting || !gameReady || sessionLocked) return
        val opened = !menuOpen
        if (opened) toggleMenu()
        withMenu { it.openResetDialog(closesOnCancel = opened) }
    }

    /**
     * The Reset dialog's Reset: retro_reset on the emulation thread (the engine clears the rewind buffer and the frame
     * timer with it, L18/L19), waited for off main as a disc change is. A toggled rewind would only run back to the reset
     * point: it ends first; a toggled FF stays.
     */
    override fun resetGame() {
        if (quitting || !gameReady || netplayNow != null) return
        val view = retroView ?: return
        speed.setToggle(rewind = false)
        lifecycleScope.launch(Dispatchers.IO) {
            runCatching { view.reset() }.onFailure { NxLog.w("GameActivity", "reset failed", it) }
        }
    }

    /** The Adjust buttons editor over the paused game ([adjustButtons]) and its state; null while it isn't up. */
    private var skinEditView: ComposeView? = null
    private var skinEditState: SkinEditorState? = null

    /**
     * The menu's Adjust buttons: the paused frame grabbed, then the skin layout editor for the layout being played over
     * everything, the menu hidden under it. The game stays paused; Save and Cancel both come back to the menu as it was.
     */
    override fun adjustButtons() {
        if (!menuOpen || quitting || skinEditView != null) return
        val edit = padLayout.skinEdit() ?: run { toast("No button layout fits this screen"); return }
        // the menu closed, or the game quit, while the frame was read: nothing to edit over ([showSkinEdit] refuses)
        saves.captureThumbnail(fullSize = true) { frame -> showSkinEdit(edit, frame) }
    }

    /** The editor for [edit], [frame] behind it (black without one); only over the open menu (it closes with it). */
    private fun showSkinEdit(edit: PadLayoutController.SkinEdit, frame: Bitmap?) {
        if (!menuOpen || quitting || skinEditView != null) { frame?.recycle(); return }
        val root = findViewById<FrameLayout>(R.id.root)
        // the layout drawn now: the bare buttons are the overlay's, edited apart from the skin's
        val state = SkinLayoutEdit.state(settings, edit.consoleId, edit.placement, edit.w, edit.h, overlay = edit.bare, minTouchPx = resources.displayMetrics.density * MIN_TOUCH_DP, useStick = edit.useStick)
        val colors = nxColorsFor(settings.accent, settings.secondaryAccent)
        val title = CoreRegistry.consoles.firstOrNull { it.id == edit.consoleId }?.displayName ?: "Adjust buttons"
        val picture = edit.picture.asImageBitmap()
        val parts = edit.parts?.asImageBitmap()
        val backdrop = frame?.asImageBitmap()
        val alpha = padLayout.padLandscapeAlpha.value   // the bare buttons at the overlay's opacity, as in the game
        menu.menuRouter.releaseAll()
        val view = ComposeView(this).apply {
            setContent {
                NxTheme(colors = colors) {
                    BoxWithConstraints(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black)) {
                        // a new window size (a turn, a fold) is another layout: the edit ends unsaved
                        if (constraints.maxWidth != edit.w || constraints.maxHeight != edit.h) {
                            androidx.compose.runtime.LaunchedEffect(Unit) { closeSkinEdit() }
                            return@BoxWithConstraints
                        }
                        SkinLayoutEditor(
                            state, picture, parts, edit.bare, backdrop, title,
                            onSave = { saved ->
                                SkinLayoutEdit.save(settings, edit.consoleId, edit.placement.spec.id, saved)
                                closeSkinEdit()
                                relayoutPad()
                            },
                            onClose = ::closeSkinEdit,
                            bareAlpha = alpha,
                        )
                    }
                }
            }
        }
        skinEditView = view
        skinEditState = state
        menu.menuView?.visibility = View.INVISIBLE
        // last: over the game, the pad, the menu and the notifications
        root.addView(view, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
    }

    /** The editor goes (saved or not) and the menu shows again as it was (none when the menu closed: [resumeAfterMenu]). */
    private fun closeSkinEdit() {
        val view = skinEditView ?: return
        skinEditView = null
        skinEditState = null
        val root = findViewById<FrameLayout>(R.id.root)
        root.post { root.removeView(view) }   // not from inside its own touch or composition
        menu.menuView?.visibility = View.VISIBLE
    }

    /** Back or B over the editor: while "Discard changes?" shows they keep editing; else a cancel (asking first when changed). */
    private fun skinEditBack() {
        val e = skinEditState ?: return closeSkinEdit()
        if (e.confirmDiscard) e.keepEditing() else if (e.requestCancel()) closeSkinEdit()
    }

    override fun quitGame(chosen: Boolean, autoState: Boolean) = quitWithAutoState(chosen, autoState)

    override fun leaveNetplay() = gameNetplay.leave()

    override fun handleMenuEffect(effect: MenuEffect) = menu.handle(effect)
    override fun panelTouchActions() = shortcuts.panelTouchActions()

    /**
     * The second-screen panel's All options: the in-game menu on its Options page (opened for it when closed). Not while
     * the DS wireless is on: the reduced menu has no Options, and a menu opened in a room starts the cap.
     */
    override fun openOptions() {
        if (quitting || !gameReady || dsWireless) return
        if (!menuOpen) toggleMenu()
        withMenu { it.openOptions() }
    }
    override fun onPanelTouch(action: ShortcutAction, down: Boolean, slot: Int?) = shortcuts.onPanelTouch(action, down, slot)
    override fun placeMenu() = menu.placeMenu()
    override fun relayoutPad() = padLayout.applyLayout(resources.configuration.orientation)

    /**
     * [relayoutPad] for a change a pad press made (a menu row): posted, since the pad view whose press made it is still
     * dispatching it, and the menu router arms that press's repeat only after this returns. The rebuilt pad never sends
     * the press's release, so the repeat is stopped here, or it would keep cycling the row.
     */
    override fun relayoutPadAfterPress() {
        findViewById<FrameLayout>(R.id.root).post {
            if (menuOpen) menu.menuRouter.releaseAll()
            relayoutPad()
        }
    }
    override fun withMenu(block: (InGameMenuModel) -> Unit) { menu.menuModel?.let { block(it); menu.menuState.value = it.state } }
    override fun withMenuIf(pred: (InGameMenuModel) -> Boolean, block: (InGameMenuModel) -> Unit) { menu.menuModel?.takeIf(pred)?.let { block(it); menu.menuState.value = it.state } }

    override fun publishMenuShaders() {
        withMenu { it.setShaders(renderer.shaderValues, renderer.presetNames, renderer.shaderParams) }
    }

    override fun publishMenuFrontend() {
        withMenu {
            it.setFrontend(frontendValues, renderer.overlayNames(), menuLocked(), gameTag, overlayAvailable = !renderer.ndsSplit)
        }
    }
    override val gameContainer: FrameLayout get() = findViewById(R.id.gameContainer)

    override fun careInput() = care.input()
    override fun careAdopt(layer: CareLayer) = care.adopt(layer)

    override fun dispatchKeyEvent(raw: KeyEvent): Boolean {
        kidsInputSeen()
        care.input()
        val event = SystemButtonKeys.translate(this, raw)   // a bound SELECT / START / MENU button as its role, first
        val code = event.keyCode
        // The Adjust buttons editor is touch-driven: Back / B leave it, every other key waits (the menu under it and the
        // paused game get none).
        if (skinEditView != null) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0 && (code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_BUTTON_B)) skinEditBack()
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        kidsInputSeen()
        care.input()
        return super.dispatchTouchEvent(event)
    }

    /** Sticks go nowhere under the Adjust buttons editor. */
    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        kidsInputSeen()
        care.input()
        return skinEditView != null || super.dispatchGenericMotionEvent(event)
    }

    override fun onGenericMotionEvent(event: MotionEvent?): Boolean = input.onGenericMotionEvent(event)
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean = input.onKeyDown(keyCode, event)
    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean = input.onKeyUp(keyCode, event)
    override fun superGenericMotionEvent(event: MotionEvent?): Boolean = super.onGenericMotionEvent(event)
    override fun superKeyDown(keyCode: Int, event: KeyEvent): Boolean = super.onKeyDown(keyCode, event)
    override fun superKeyUp(keyCode: Int, event: KeyEvent): Boolean = super.onKeyUp(keyCode, event)

    /**
     * [LaunchPipeline]'s view of this screen: the only way in to the state the launch settles, so those setters stay
     * private here. Every member reads or writes this activity's own, so nothing is copied or applied later.
     */
    private inner class LaunchState : LaunchHost {
        private val game = this@GameActivity
        override val lifecycle: Lifecycle get() = game.lifecycle
        override val activity: ComponentActivity get() = game
        override val input get() = game.input
        override val padLayout get() = game.padLayout
        override val renderer get() = game.renderer
        override val dual get() = game.dual
        override val shortcuts get() = game.shortcuts
        override val saves get() = game.saves
        override val wii get() = game.wii
        override val res3d get() = game.res3d
        override val gameNetplay get() = game.gameNetplay
        override val speed get() = game.speed
        override val settings get() = game.settings
        override val refs get() = game.refs
        override val gameRumble get() = game.gameRumble
        override val launchRecords get() = game.launchRecords
        override val launchId get() = game.launchId
        override val intentTag get() = game.intentTag
        override val intentTitle get() = game.intentTitle
        override val netplay get() = game.netplay
        override val achievements get() = game.achievements
        override val focusPaused get() = game.focusPaused
        override val quitting get() = game.quitting
        override val isDolphin get() = game.isDolphin
        override val isDc get() = game.isDc
        override val isN3ds get() = game.isN3ds
        override val retroView get() = game.retroView

        override var usingData by game::usingData
        override var dataRoot by game::dataRoot
        override var privateRoot by game::privateRoot
        override var shaderRoots by game::shaderRoots
        override var paths by game::paths
        override var gameTag by game::gameTag
        override var gameCoreId by game::gameCoreId
        override var playerCount by game::playerCount
        override var rumbles by game::rumbles
        override var canRewind by game::canRewind
        override var padMap by game::padMap
        override var segaPad by game::segaPad
        override var segaHeader by game::segaHeader
        override var launchedSegaSetting by game::launchedSegaSetting
        override var n3dsTitle by game::n3dsTitle
        override var n3dsNew by game::n3dsNew
        override var n3dsSwapMode by game::n3dsSwapMode
        override var n3dsSwapPinnedHalfFolded by game::n3dsSwapPinnedHalfFolded
        override var ndsRenderMode by game::ndsRenderMode
        override var ndsFellBack by game::ndsFellBack
        override var launchedOnVulkan by game::launchedOnVulkan
        override var launchEnv by game::launchEnv
        override var graphicsForcedOptions by game::graphicsForcedOptions
        override var frontendValues by game::frontendValues
        override var configStore by game::configStore
        override var configSource by game::configSource
        override var lockedOptions by game::lockedOptions
        override var storedValues by game::storedValues
        override var restartOnlyOptions by game::restartOnlyOptions
        override var forcedOptions by game::forcedOptions
        override var hiddenValues by game::hiddenValues
        override var descriptorDefaults by game::descriptorDefaults
        override var cheatsSupported by game::cheatsSupported
        override var cheatsFolder by game::cheatsFolder
        override var cheatSet by game::cheatSet
        override var cheatsLoad by game::cheatsLoad
        override var dolphinPlayerCheats by game::dolphinPlayerCheats
        override var dolphinSavedCheats by game::dolphinSavedCheats
        override var dolphinBuiltInPath by game::dolphinBuiltInPath

        override fun onViewCreated(view: GLRetroView) { game.retroView = view }
        override fun onGameReady() { game.gameReady = true }
        override fun startCoreWatchdog(stalledMillis: () -> Long) {
            game.coreWatchdog = CoreWatchdog(stalledMillis, onHung = { onCoreHung(it) }).also { it.start() }
        }
        override fun startGameTime(rec: GameTimeRecorder) {
            game.gameTime = rec
            // Ready while stopped (onStop already ran): close it at once, as onStop would; onStart resumes it.
            if (!lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) rec.pause()
            startGameTimeTicker(rec)
        }

        override fun fail(message: String, title: String) = game.fail(message, title)
        override fun relaunchOnGles() = game.relaunchOnGles()
        override fun quitGame(chosen: Boolean, autoState: Boolean) = game.quitWithAutoState(chosen, autoState)
        override fun toast(msg: String) = game.toast(msg)
        override fun isLandscape() = game.isLandscape()
        override fun screenClass() = game.screenClass()
        override fun n3dsSkinStack() = game.n3dsSkinStack()
        override fun n3dsHalfFoldedPin(mode: DualMode) = game.n3dsHalfFoldedPin(mode)
        override fun n3dsProportion() = game.n3dsProportion()
        override fun relayoutPad() = game.relayoutPad()
        override fun dropPadProfile() = game.dropPadProfile()
        override fun applyFrontend(values: Map<String, String>) = game.applyFrontend(values)
        override fun sendSegaPad(setting: String) = game.sendSegaPad(setting)
        override fun offerLayoutTip() = game.offerLayoutTip()
        override fun ensureNotificationsView() = game.ensureNotificationsView()
        override fun publishMenuShaders() = game.publishMenuShaders()
        override fun withMenu(block: (InGameMenuModel) -> Unit) = game.withMenu(block)
        override fun publishNds3d(model: InGameMenuModel) = game.publishNds3d(model)
        override fun publishCheats() = game.publishCheats()
        override fun applyCheats(view: GLRetroView) = game.applyCheats(view)
        override suspend fun readDolphinBuiltIn() = game.readDolphinBuiltIn()
        override suspend fun refreshDolphinCheats() = game.refreshDolphinCheats()
        override fun prepareAchievements(tag: String, romFileName: String, content: ContentRef, inner: File?) =
            game.prepareAchievements(tag, romFileName, content, inner)
        override fun onAchievementEvent(e: RaEvent) = game.onAchievementEvent(e)
    }
}

/** These options with a second-screen [split]'s fixed ones in place of the same keys; unchanged without one. */
internal fun List<Variable>.withN3dsSplit(split: Map<String, String>?): List<Variable> =
    if (split == null) this else filterNot { it.key in split } + split.map { (k, v) -> Variable(k, v) }

internal enum class FocusPauseStep { PAUSE, RESUME, NONE }

/**
 * What a focus pause needs once Pause when unfocused may have changed: one made under the old value ends when the
 * setting is off or the window is the top one again; a playing game that is not the top one pauses when it is on. Not yet
 * [resumed]: no pause; the focus change that follows onResume decides.
 */
internal fun focusPauseStep(focusPaused: Boolean, topResumed: Boolean, pauseWhenUnfocused: Boolean, resumed: Boolean): FocusPauseStep =
    when {
        focusPaused && (!pauseWhenUnfocused || topResumed) -> FocusPauseStep.RESUME
        !focusPaused && pauseWhenUnfocused && !topResumed && resumed -> FocusPauseStep.PAUSE
        else -> FocusPauseStep.NONE
    }
