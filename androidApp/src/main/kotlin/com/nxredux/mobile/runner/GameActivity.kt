package com.nxredux.mobile.runner

import android.app.Activity
import android.app.ActivityManager
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.hardware.input.InputManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Display
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.PixelCopy
import android.view.Surface
import android.view.View
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
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
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
import com.nxredux.mobile.library.ArcadeCompanions
import com.nxredux.mobile.library.NaomiSets
import com.nxredux.mobile.library.Names
import com.nxredux.mobile.netplay.NetplayCores
import com.nxredux.mobile.netplay.NetplayLinkKind
import com.nxredux.mobile.netplay.NetplayLinkOptions
import com.nxredux.mobile.netplay.NetplayPads
import com.nxredux.mobile.netplay.NetplayRole
import com.nxredux.mobile.netplay.NetplaySaves
import com.nxredux.mobile.netplay.NetplaySession
import com.nxredux.mobile.netplay.NetplaySessionDir
import com.nxredux.mobile.netplay.NetplayText
import com.nxredux.mobile.netplay.usb.UsbCables
import com.nxredux.mobile.options.BottomScreenOptions
import com.nxredux.mobile.options.ConfigSource
import com.nxredux.mobile.options.CoreConfig
import com.nxredux.mobile.options.GraphicsApiOptions
import com.nxredux.mobile.options.FrontendOptions
import com.nxredux.mobile.options.FrontendSettings
import com.nxredux.mobile.options.N3dsOptions
import com.nxredux.mobile.options.WiiOptions
import com.nxredux.mobile.options.WiiScreen
import com.nxredux.mobile.options.NdsOptions
import com.nxredux.mobile.options.NdsScreen
import com.nxredux.mobile.options.OverlayResolver
import com.nxredux.mobile.options.PadOptions
import com.nxredux.mobile.options.ScreenScaling
import com.nxredux.mobile.options.ShaderOptions
import com.nxredux.mobile.options.VulkanFallback
import com.nxredux.mobile.settings.AndroidAppSettings
import com.nxredux.mobile.settings.GameRumble
import com.nxredux.mobile.storage.GamePath
import com.nxredux.mobile.storage.NdsResumeCleanup
import com.nxredux.mobile.storage.NdsDsvSave
import com.nxredux.mobile.storage.NxPaths
import com.nxredux.mobile.storage.AndroidLibraryIndexStore
import com.nxredux.mobile.storage.AndroidRefs
import com.nxredux.mobile.storage.AndroidRomsSources
import com.nxredux.mobile.storage.DataRoot
import com.nxredux.mobile.storage.AndroidHomeLocator
import com.nxredux.mobile.test.NxTestResults
import com.nxredux.mobile.tools.CheatsRoot
import com.swordfish.libretrodroid.GLRetroView
import com.swordfish.libretrodroid.GLRetroViewData
import com.swordfish.libretrodroid.LibretroDroid
import com.swordfish.libretrodroid.NxLog
import com.swordfish.libretrodroid.ScaleMode
import com.swordfish.libretrodroid.ScreenRegion
import com.swordfish.libretrodroid.Variable
import com.swordfish.libretrodroid.VirtualFile
import com.nxredux.mobile.ui.nx.PadProfile
import com.nxredux.mobile.ui.nx.NxTheme
import com.nxredux.mobile.ui.nx.InGameMenuModel
import com.nxredux.mobile.ui.nx.InputRouter
import com.nxredux.mobile.ui.nx.MenuEffect
import com.nxredux.mobile.ui.nx.NotificationQueue
import com.nxredux.mobile.ui.nx.NxButton
import com.nxredux.mobile.ui.nx.NxNotifications
import com.nxredux.mobile.ui.nx.NxProgressPill
import com.nxredux.mobile.ui.nx.NxToast
import com.nxredux.mobile.ui.nx.nxNotificationPlacement
import com.nxredux.mobile.ui.nx.nxTopCentreMaxWidth
import com.nxredux.mobile.ui.nx.map
import com.nxredux.mobile.ui.nx.SlotUi
import com.nxredux.mobile.ui.nx.PadMotion
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
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
import java.io.IOException
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** The process-wide log tag of the game screen and its controllers. */
internal const val TAG_LOG = "NXAPP"

internal class GameActivity : ComponentActivity(), GameInputHost, PadLayoutHost, FrontendHost, DualScreenHost, InGameMenuHost {

    companion object {
        const val EXTRA_CORE_FILE = "core_file"
        const val EXTRA_CORE_ID = "core_id"
        const val EXTRA_TAG = "tag"
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
        const val EXTRA_LAUNCH_ID = "launch_id"      // Long: the launcher's PendingLaunches entry, told once the game runs
        const val EXTRA_GAME_PATH = "game_path"      // the library path (GamePath): game time's identity; absent = not recorded
        const val EXTRA_ART_PATH = "art_path"        // the game's box art (GameSession.artPath): the game panel's header; absent = the console logo
        const val EXTRA_FORCE_GLES = "force_gles"    // Boolean: the relaunch after Vulkan could not start (VulkanFallback)
        private const val SIMULATE_VULKAN_FAIL = "simulate_vulkan_fail"   // debug builds only: see startEmulator
        // netplay: a Wi-Fi netplay launch (GameSession.netplay); all absent for every other launch. The run core is EXTRA_CORE_ID.
        const val EXTRA_NETPLAY_ROLE = "netplay_role"            // NetplayRole name: "HOST" | "CLIENT"
        const val EXTRA_NETPLAY_PEER = "netplay_peer"            // the other device's IPv4 address
        const val EXTRA_NETPLAY_FOLDER_CORE = "netplay_folder_core"  // the game folder's own core id (saves belong to it)
        const val EXTRA_NETPLAY_EMPTY_SAVES = "netplay_empty_saves"   // Boolean: no save copy happened although one was due
        const val EXTRA_NETPLAY_USB = "netplay_usb"              // Boolean: the session runs over the USB cable
        /** The Dolphin core's descriptor id (Wii and GameCube tags). */
        private const val DOLPHIN_ID = "dolphin"
        // Instance-state key: the slot to resume from when the system recreates this activity after a process kill.
        private const val KEY_RESUME_SLOT = "nx_resume_slot"
        private const val KEY_NETPLAY_LOCAL = "nx_netplay_local"   // netplay link: the session had ended ([netplayLocal])
        private const val NETPLAY_NOTICE_MS = 5000L   // netplay: "Netplay ended ...", "Using <core> for this match ..."
        private const val NETPLAY_PILL_MS = 1000L     // netplay: a countdown pill outlives one 250 ms status tick
        /** Beside a real-path cache copy: what it was copied from ([realPathCopyStamp]). */
        private const val REAL_PATH_STAMP = ".nxstamp"
        // Process-wide, single thread: the Home auto state and its thumbnail are written off the main thread
        // in submission order, and outlive the activity (a stopped one may be destroyed before they finish).
        private val autoWriter = Executors.newSingleThreadExecutor { Thread(it, "nx-auto-state") }
        /** How long the Wii's HOME holds the core's Home (R3): a few frames, as a tap. */
        private const val WII_HOME_PRESS_MS = 150L

        /** Blocks until every queued Home write has finished. Call off the main thread before touching slot 9. */
        private fun awaitAutoWrites() { runCatching { autoWriter.submit {}.get() } }

        /** Debug builds only: the latest game screen, for the adb GAME_STOP hook ([debugQuit]). Main thread. */
        private var debugGame: java.lang.ref.WeakReference<GameActivity>? = null

        /**
         * Debug builds only (the adb GAME_STOP hook, NxTestReceiver): the live game quits as the menu's Quit does, so
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

        /**
         * Some game screen holds (or waits for) the session, from its claim in onCreate to the end of its teardown: in
         * launcher mode a suspended game counts, its battery save still in memory. Main thread only, as [sessions].
         */
        internal val gameScreenAlive: Boolean get() = sessions.liveCount > 0
    }

    private var sessionClaim: SessionHandoff.Claim? = null

    override var retroView: GLRetroView? = null
        private set
    private val input = GameInputRouter(this)
    private val padLayout = PadLayoutController(this, input)
    private val renderer = FrontendRenderer(this, input, padLayout)
    private val dual = DualScreenController(this, input, padLayout, renderer)
    private val menu = InGameMenuController(this, input, padLayout, renderer, dual)
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
    /** The Wii game's controls from GameTDB, looked up by the disc ID read at launch (null = unread or not listed). */
    private var wiiControls: WiiControls? = null
    /** The resolved Wii controller ([WiiController.resolve]); null outside Wii, and until the launch resolves it. */
    override var wiiPad: WiiPad? = null
        private set
    /** Motion Controls resolved ([WiiMotion.resolve]): the phone's sensors drive port 0's Wiimote. */
    private var wiiMotionOn = false
    /** The phone's accelerometer and gyroscope, created for a Wii game only. */
    private var motionSensors: MotionSensors? = null
    /** The display rotation the motion level was last captured in: a change mid-game recentres ([onConfigurationChanged]). */
    private var lastRotation = -1
    /**
     * The disc read under the WII tag is a GameCube disc ([WiiDisc.gameCube]): it keeps the plain pad and the descriptor's
     * ports, with no Wiimote, pointer, motion or Wii menu rows.
     */
    private var wiiGameCube = false
    override val isWii: Boolean get() = WiiOptions.appliesTo(gameTag) && !wiiGameCube
    override val isDolphin: Boolean get() = gameCoreId == DOLPHIN_ID
    /** This launch asked the core for Vulkan (Console Settings → Graphics API): a failure to start it relaunches on GLES. */
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
        padLayout.applyLayout(resources.configuration.orientation)
        if (menuOpen) menu.menuModel?.let(menu::publishPlayers)
        dual.refreshDualPanel()
    }

    // ---- in-game pause menu ----
    override var menuOpen = false
    /**
     * Settings → Emulators → Pause when unfocused: another app took the top focus while this window stays visible (split
     * screen, a freeform window, an app on another display), so the game holds, as under the menu, until it is back.
     */
    private var focusPaused = false
    override val slotsUi = mutableStateOf<List<SlotUi>>(emptyList())
    // openMenu reads no files on the main thread. The slot strip ([slotsUi]) and the last-used
    // slot are read on IO at start and after every write of them, so an open shows them at once; an open re-reads the
    // strip, the overlay sets and the presets on IO and publishes them when they land (the cached ones show meanwhile).
    @Volatile override var lastSlotCache: Int? = null   // SaveStates.lastSlot(); null until the first read
    private var slotsJob: Job? = null   // the newest strip refresh (main thread only); a newer one cancels it
    override var saveStates: SaveStates? = null
        private set
    /** Saves, states, configs and `.minui` markers: [DataRoot.settled] once per session. */
    private lateinit var dataRoot: File
    /** This screen holds off the data move ([DataRoot.useBegin]) until it is destroyed. */
    private var usingData = false
    /** Overlays and shaders: app-private for good ([DataRoot.private]). */
    override lateinit var privateRoot: File
        private set
    private lateinit var paths: NxPaths
    private var batterySaves: BatterySaves? = null
    private var clockSave: ClockSave? = null
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
    override var cheatsSupported = true   // false for a core that ignores cheats (the panels hide their Cheats tab); set in startEmulator
        private set
    @Volatile override var gameReady = false   // set on the first rendered frame; SRAM is only valid after load
        private set
    // This launch's PendingLaunches entry: run on the first frame (the game is recent), dropped on a refusal or finish.
    private val launchId by lazy { intent.getLongExtra(EXTRA_LAUNCH_ID, PendingLaunches.NONE) }
    private var failShown = false   // the could-not-start dialog is up (it only leaves by finishing)
    private var resumeSlot: Int? = null   // disk slot to load once the game runs (EXTRA_RESUME_SLOT); null = fresh
    override var quitting = false   // set by quitWithAutoState: quit is single-shot, the menu freezes, onStop skips its auto state
        private set
    // The quit's off-main native calls run under [coreLock] and only while ![coreGone]. An observer
    // added after the view's (so ON_DESTROY reaches it first) sets it under the lock before LibretroDroid.destroy(): a
    // quit cut short by a destroy never touches a destroyed core, nor the next game's once it loads.
    private val coreLock = Any()
    @Volatile private var coreGone = false
    // True from start until the requested resume load has finished (or failed): until then the running state
    // is the fresh boot, so it must not overwrite the resume point.
    @Volatile override var resumePending = false
        private set
    private var autoWrittenOnStop = false   // onStop serialized an auto state (its write is queued) since the last onStart
    // Home screenshot for the switcher (main thread only): onPause starts a PixelCopy; its bitmap is written as
    // the slot-9 thumb once onStop has queued the auto state. [homeThumbGen] drops callbacks from an older pause.
    private var homeThumb: Bitmap? = null
    private var homeThumbGen = 0
    private var autoWriteOk: AtomicBoolean? = null   // this stop's queued auto-state write succeeded (set on autoWriter)
    private var autoWriteFailed: AtomicBoolean? = null   // this stop's queued auto-state write threw (set on autoWriter)
    // The newest resume point known to be on disk, for the instance state when this stop's own
    // auto write has already failed: the slot this screen started from (onCreate), then slot 9 once a stop's write lands.
    @Volatile private var lastResumePoint: Int? = null
    // API 26–27 only: onSaveInstanceState runs before onStop there, so its bundle is kept to be updated
    // after the onStop write (the framework parcels it only after onStop returns).
    private var pendingOutState: Bundle? = null
    private val slotJobs = mutableListOf<Job>()   // manual Save/Load in flight (main thread only); Quit joins them
    private var wiiHomeSaving = false   // [pressWiiHome]'s resume point being saved (main thread only)
    @Volatile private var wiiHomeSaved = false   // [pressWiiHome] saved this session's resume point
    // RomCache entries this game uses (its extraction, or its real-path copies): held so no sweep deletes them; released in onDestroy.
    private val cacheHolds = java.util.concurrent.ConcurrentLinkedQueue<String>()
    @Volatile private var cacheHoldsDrained = false   // set in onDestroy; a hold kept later (an unpack/copy that outlived us) is released at once
    /** Game time for this activity's game (null until the first frame, or when the launch had no library path). */
    private var gameTime: GameTimeRecorder? = null
    private var gameTimeTicker: Job? = null

    // ---- frontend (display) settings ----
    override var gameTag = "UNKNOWN"
        private set
    override var gameCoreId = ""
        private set
    /** Debug builds: when this screen was created; game.json's autoState names only a slot 9 written since. */
    private var debugStartedAt = 0L
    /** Players this game's core gives ([CoreRegistry.playersFor]); 1 until launch. */
    private var playerCount = 1
    override var frontendValues: Map<String, String> = FrontendOptions.defaults()   // every frontend key, as in effect now
        private set
    // Settings → Controls → Game rumble, read once per game: each port's rumble on its own pad; port 0 falls back to the phone.
    private val gameRumble by lazy { AndroidAppSettings(this).gameRumble }
    /** Each port's rumble (index = port); empty until [startEmulator] builds it, so every use must tolerate empty. */
    override var rumbles: Array<RumbleMotor> = emptyArray()
        private set

    // ---- Nintendo DS screens (NdsLayout regions) ----
    override val ndsRegions: List<NdsRegion> get() = renderer.ndsRegions
    override val secondNdsRegions: List<NdsRegion> get() = renderer.secondNdsRegions
    private var ndsRenderMode: String? = null               // melonds_render_mode this game started with (the notice's)
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
    private fun dropPadProfile() { padProfileCache = null; padProfileRevision.value++; input.gcClearTriggers() }
    /** The on-screen pad's profile for this game's system (cluster, face letters, hidden buttons); the intent's tag, for onCreate's layout. */
    override fun padProfile(): PadProfile =
        padProfileCache ?: PadProfile.forTag(intent.getStringExtra(EXTRA_TAG) ?: gameTag, segaPad, n3dsNew, wiiPad, gameCube = wiiGameCube).also { padProfileCache = it }

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
    /**
     * netplay: this launch's session (read in onCreate from the EXTRA_NETPLAY_* extras); null for every other launch. In a
     * session there are no save states or auto-resume, no cheats, Core Options or local multiplayer slots, no achievements
     * outside a link session ([NetplaySession.allowsAchievements]), and the menu is only "Leave netplay?".
     */
    override var netplay: NetplaySession? = null
        private set
    /** netplay: recreated after a process kill with a session's intent: that session is gone, nothing is started. */
    private var netplayRestored = false
    /** netplay over USB: the cable's start this screen ran on; a newer sheet start makes this screen's stop a no-op. */
    private var netplayUsbToken: Long? = null
    /** netplay: the engine session; set just before it starts, so onDestroy always shuts down what was started. */
    private var netplayGame: NetplayRun? = null
    private var netplayPillShown = false   // the countdown pill is the session's (hidden again on Clear)
    /** netplay: the session ended and the game plays on alone (the menu is then Continue / Quit). */
    override var netplayEnded = false
        private set
    /**
     * netplay link: the link session ended ([playLocally]), or this screen came back after a process kill with one that
     * had: the game is a normal local launch from then on. The one switch the menu, the panel and the option guards
     * read, through [netplayNow]; [netplay] itself stays for the launch's setup and onDestroy's release.
     */
    private var netplayLocal = false
    /** netplay: the session this screen plays in now: [netplay], or null once a link session's game is a local one. */
    override val netplayNow: NetplaySession? get() = netplay.takeUnless { netplayLocal }
    /** netplay link: starts the cheat load a normal launch starts in startEmulator, once the game is local ([playLocally]). */
    private var localCheats: (() -> Job)? = null
    /** netplay: this screen's id on the session copy it plays on ([NetplaySessionDir.claim]). */
    private val netplayOwner by lazy { java.util.UUID.randomUUID().toString() }
    /** netplay: the engine did not start; the game stays paused and silent behind the failure dialog (closeMenu too). */
    private var netplayStartFailed = false
    @Volatile private var netplayOwnCopyFailed = false   // a host's copy of its own saves failed: it starts without them

    // ---- RetroAchievements (see the "achievements" block) ----
    override var achievements: AchievementsBridge? = null   // null: off, signed out, or a console RA does not cover
        private set
    override val notifications = NotificationQueue()
    private val notificationState = mutableStateOf(Pair<List<NxToast>, NxProgressPill?>(emptyList(), null))
    override var notificationsView: ComposeView? = null
        private set
    private var tickJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (BuildConfig.DEBUG) { debugGame = java.lang.ref.WeakReference(this); debugStartedAt = System.currentTimeMillis() }
        // Debug only: a GAME test hook's game runs on a locked test device (NxTestResults.markShowWhenLocked).
        if (BuildConfig.DEBUG && com.nxredux.mobile.test.NxTestResults.takeShowWhenLocked(this)) {
            setShowWhenLocked(true); setTurnScreenOn(true)
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        // Cross-fade in from the menu instead of the default slide-from-right: the bottom pad is
        // identical and in the same position in both screens, so a fade keeps it visually steady while
        // only the top (game) area changes. (Back to the menu cross-fades too.) Only while the game shares
        // the menu's task (launcher mode off); in launcher mode it has its own task and the system task animation runs,
        // as Android ignores these overrides for a task change (LibretroRunner's gameLaunchFlags).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(Activity.OVERRIDE_TRANSITION_OPEN, android.R.anim.fade_in, android.R.anim.fade_out)
            overrideActivityTransition(Activity.OVERRIDE_TRANSITION_CLOSE, android.R.anim.fade_in, android.R.anim.fade_out)
        }
        // The game's own Recents entry (launcher mode) carries its title; sharing the menu's task, the entry stays the app's.
        if (isTaskRoot) {
            val recentsLabel = intent.getStringExtra(EXTRA_TITLE) ?: "Game"
            if (Build.VERSION.SDK_INT >= 33) setTaskDescription(ActivityManager.TaskDescription.Builder().setLabel(recentsLabel).build())
            else @Suppress("DEPRECATION") setTaskDescription(ActivityManager.TaskDescription(recentsLabel))
        }
        setContentView(R.layout.activity_game)
        dual.readPlayTotal()   // before this launch's own slices land: the header's total stays as it was
        volumeControlStream = AudioManager.STREAM_MUSIC   // volume keys set the game's (media) volume, even between sounds
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
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
        lifecycleScope.launch { dual.secondScreens.current.collect { dual.syncDual(); menu.menuModel?.let(menu::publishSecondScreen) } }
        // The game's window resized (a rotation, a fold): the picture screen may be the other display now.
        findViewById<View>(R.id.root).addOnLayoutChangeListener { v, l, t, r, b, ol, ot, or_, ob ->
            if (r - l != or_ - ol || b - t != ob - ot) v.post { dual.recheckPictureOn() }
        }
        padLayout.applyLayout(resources.configuration.orientation)
        padLayout.collectPosture()
        // Back as a gesture or a key on Android 16+ (targetSdk 36: predictive back, KEYCODE_BACK does not reach
        // onKeyDown): what the key's press did there, the in-game menu toggles (B inside it). Before 16 the gesture and
        // the key both arrive as KEYCODE_BACK, which onKeyDown consumes, so this never runs twice for one Back.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = backPressed()
        })
        // netplay: a session launches cold: no saved or requested resume slot.
        val launchSession = netplaySessionFrom(
            intent.getStringExtra(EXTRA_NETPLAY_ROLE), intent.getStringExtra(EXTRA_NETPLAY_PEER),
            intent.getStringExtra(EXTRA_CORE_ID) ?: "", intent.getStringExtra(EXTRA_NETPLAY_FOLDER_CORE),
            intent.getBooleanExtra(EXTRA_NETPLAY_EMPTY_SAVES, false),
            intent.getBooleanExtra(EXTRA_NETPLAY_USB, false),
        )
        // netplay link: recreated after a process kill once its session had ended: the local game it had become, as a
        // normal launch (its resume slot below), not a session.
        netplayLocal = launchSession?.endsAsLocalGame == true && savedInstanceState?.getBoolean(KEY_NETPLAY_LOCAL) == true
        netplay = launchSession.takeUnless { netplayLocal }
        netplayRestored = netplay != null && savedInstanceState != null
        if (netplay?.overUsb == true) netplayUsbToken = UsbCables.get(this).token()
        // Decided here, not after startEmulator's async setup, so a stop in between still records the slot.
        // A saved slot (recreated after a process kill) wins over the launch intent's.
        val savedSlot = savedInstanceState?.getInt(KEY_RESUME_SLOT, -1)?.takeIf { it in 0..NxPaths.AUTO_SLOT }
        resumeSlot = if (netplay != null) null else savedSlot ?: intent.getIntExtra(EXTRA_RESUME_SLOT, -1).takeIf { it in 0..NxPaths.AUTO_SLOT }
        resumePending = resumeSlot != null
        lastResumePoint = resumeSlot
        val claim = sessions.claim(::onSessionHandedOff).also { sessionClaim = it }
        lifecycleScope.launch {
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
            startEmulator()
        }
    }

    /**
     * A newer game screen claimed the native session: leave through the normal quit (battery save, auto state, RA stop,
     * then finish; onDestroy's native destroy releases the session). Usually stopped here: the auto state is serialized
     * off the GL thread as onStop does, and the thumbnail is skipped when the stopped surface cannot be copied.
     */
    private fun onSessionHandedOff() {
        if (isDestroyed) return
        NxLog.i(TAG_LOG, "a newer game screen takes the session: quitting ${intent.getStringExtra(EXTRA_TITLE)}")
        quitWithAutoState(chosen = false)
    }

    /**
     * singleTop. In launcher mode launches clear the game task (CLEAR_TASK); sharing the menu's task, the menu (the
     * only launcher) sits under this screen. So this only guards a launch that does reach this running game: it is
     * ignored, and its PendingLaunches entry is dropped, not leaked.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val id = intent.getLongExtra(EXTRA_LAUNCH_ID, PendingLaunches.NONE)
        if (id != launchId) PendingLaunches.take(id)
    }

    override fun onPause() {
        rumbles.forEach { it.pause() }   // no rumble in the background; onResume picks the core's state up again
        // The battery save is written synchronously (the process may be killed once backgrounded).
        // A quit writes it once itself, off the main thread (quitWithAutoState), so nothing here then.
        if (!quitting) writeBatterySave(notify = false)
        netplayGame?.pause()   // netplay: backgrounded: no input goes out; the other side stalls, 30 s ends the session
        syncMotionSensors(foreground = false)
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        // The core runs again only with the menu closed (closeMenu resumes it then); a quit never does. Focus held
        // elsewhere: focusResume does, once this window is the top one again.
        if (!menuOpen && !focusPaused && !quitting) rumbles.forEach { it.resume() }
        if (!menuOpen && !focusPaused) netplayGame?.resume()   // netplay: input flows again; with the menu open it stays paused until Continue
        syncMotionSensors(foreground = true)
        dual.syncDual()   // Settings → Second screen may have changed meanwhile
    }

    /** API 29+: multi-resume keeps this activity resumed while another app has the focus; only the top one is told. */
    override fun onTopResumedActivityChanged(isTopResumedActivity: Boolean) {
        super.onTopResumedActivityChanged(isTopResumedActivity)
        if (isTopResumedActivity) focusResume() else if (AndroidAppSettings(this).pauseWhenUnfocused) focusPause()
    }

    /** The game holds as under the menu (see openMenu); an open menu has already paused it. */
    private fun focusPause() {
        if (focusPaused || quitting) return
        focusPaused = true
        if (menuOpen) return
        input.releaseAllGameInput()
        netplayGame?.takeIf { it.live }?.pause() ?: run { retroView?.frameSpeed = 0 }
        retroView?.audioEnabled = false
        rumbles.forEach { it.pause() }
    }

    /** Focused again: the game plays on, unless the menu opened meanwhile (closeMenu resumes it then). */
    private fun focusResume() {
        if (!focusPaused) return
        focusPaused = false
        if (menuOpen || quitting || !lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return
        netplayGame?.takeIf { it.live }?.resume()
        if (!netplayStartFailed) {
            retroView?.frameSpeed = 1
            retroView?.audioEnabled = true
        }
        rumbles.forEach { it.resume() }
    }

    /** The opening menu holds the game ([InGameMenuController.openMenu]), as [focusPause] does. */
    override fun pauseForMenu() {
        syncMotionSensors()   // no motion while paused
        // netplay: native runs a session at speed 1 whatever frameSpeed says; a local pause stops this device's input instead.
        netplayGame?.takeIf { it.live }?.pause() ?: run { retroView?.frameSpeed = 0 }
        retroView?.audioEnabled = false
        rumbles.forEach { it.pause() }   // the core stops with its rumble latched: still while paused, back on closeMenu
        lifecycleScope.launch(Dispatchers.IO) { writeBatterySave(notify = true) }
    }

    /** The menu closed ([InGameMenuController.closeMenu]): the game plays on, unless the focus pause still holds it. */
    override fun resumeAfterMenu() {
        if (focusPaused) { syncMotionSensors(); return }   // another app has the focus: focusResume plays on later
        netplayGame?.takeIf { it.live }?.resume()   // netplay: input goes out again (see openMenu)
        if (!netplayStartFailed) {   // netplay: a failed start keeps the game paused behind its dialog
            retroView?.frameSpeed = 1
            retroView?.audioEnabled = true
        }
        if (!quitting) rumbles.forEach { it.resume() }
        syncMotionSensors()
    }

    /**
     * Leaving via Home/recents: grab the frame for the switcher preview now. This runs before the pause,
     * while the GL surface still holds a frame; by onPause GLRetroView has already paused it (no data).
     */
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (!quitting && gameReady && !resumePending) {
            val gen = ++homeThumbGen
            homeThumb = null
            captureThumbnail { bmp -> if (gen == homeThumbGen && bmp != null) onHomeThumb(bmp) }
        }
    }

    override fun onStart() {
        super.onStart()
        autoWrittenOnStop = false   // a later Home must write (and record) a fresh auto state
        autoWriteOk = null
        autoWriteFailed = null
        homeThumb = null
        homeThumbGen++   // a capture still in flight belongs to the previous stop
        pendingOutState = null
        if (isNds) input.syncPen()   // restarts the pen tick onStop stopped, if stylus mode is on
        if (isN3ds) input.syncN3dsCursor()
        dual.secondScreens.start()   // the collector shows the presentation again
        // onStop closed the session: back in the foreground, a new one on the same game (background time is not play)
        gameTime?.let { if (!isFinishing && !quitting) { it.resume(); startGameTimeTicker(it) } }
    }

    /** Main thread. Before onStop's auto state: keep it for onStop. After it: queue it behind that state. */
    private fun onHomeThumb(bmp: Bitmap) {
        if (autoWrittenOnStop) queueHomeThumb(bmp) else homeThumb = bmp
    }

    /** Writes the slot-9 thumb after this stop's auto state, and only if that write succeeded (they must match). */
    private fun queueHomeThumb(bmp: Bitmap) {
        val ss = saveStates ?: return
        val ok = autoWriteOk ?: return
        autoWriter.execute {
            if (ok.get()) runCatching { ss.writeAutoThumb(bmp) }.onFailure { NxLog.w("GameActivity", "auto-resume thumb on stop failed", it) }
        }
    }

    override fun onStop() {
        gameTime?.pause()   // the session ends here: a kill in the background loses nothing, a return opens a new one
        gameTimeTicker?.cancel()
        gameTimeTicker = null
        dual.panelClockJob?.cancel(); dual.panelClockJob = null   // onResume's syncDual starts it again
        // Leaving via Home/recents: the process may be killed in the background, so keep the spot. Also when
        // finishing without a quit: sharing the menu's task, a launch of the singleTask menu (app icon, notification)
        // clears this screen. A quit writes its own; a game that never got ready writes nothing (writeAutoStateOnStop);
        // one that failed after starting and leaves through its dialog keeps the last good resume point.
        if (!quitting && !relaunching && !(isFinishing && failShown)) writeAutoStateOnStop()
        // API 26–27: onSaveInstanceState already ran; record the outcome of the write in its bundle.
        pendingOutState?.let(::putResumeSlot)
        pendingOutState = null
        if (isFinishing) { input.gcClearTriggers(); stopAchievements() }
        input.onStop()   // the pen and 3DS hotkey ticks stop; onStart restarts them
        // A Presentation stays up while its activity is stopped: no second screen in the background.
        dual.secondScreens.stop()
        dual.syncDual()
        super.onStop()
    }

    /**
     * After a process kill the system recreates this activity with its original intent, so the slot to
     * resume from must travel in the instance state: the auto state onStop just wrote, or the requested
     * slot while its load is still pending. On API 28+ this runs after onStop; on 26–27 before it, so the
     * bundle is also updated from onStop (see [pendingOutState]).
     */
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        putResumeSlot(outState)
        if (netplayLocal) outState.putBoolean(KEY_NETPLAY_LOCAL, true)
        pendingOutState = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) outState else null
    }

    /**
     * [autoWrittenOnStop] means the auto state was serialized and its write queued, not that it landed. When the write
     * is already known to have failed, the bundle keeps the previous resume point ([lastResumePoint]) rather than naming
     * a slot 9 this stop never replaced. Usually the write is still in flight here (onSaveInstanceState follows onStop at
     * once on API 28+, the write takes tens of ms on [autoWriter]) and the bundle is parcelled when this returns, so slot
     * 9 is recorded optimistically. The write is an atomic rename, so a failure leaves the previous slot-9 file whole: a
     * recreation resumes an older auto state (or toasts "Resume failed" when there was none), never a torn one. Blocking
     * the main thread on the write is not worth it for a rare failure.
     */
    private fun putResumeSlot(outState: Bundle) {
        val slot = when {
            autoWrittenOnStop && autoWriteFailed?.get() == true -> lastResumePoint
            autoWrittenOnStop -> NxPaths.AUTO_SLOT
            resumePending -> resumeSlot
            else -> null
        }
        if (slot != null) outState.putInt(KEY_RESUME_SLOT, slot) else outState.remove(KEY_RESUME_SLOT)
    }

    /**
     * Debug builds: game.json "stopped" once this screen finishes, queued behind any auto-state write on [autoWriter]
     * (a quit wrote its own before finish), with the slot-9 state file when this screen wrote it, else null.
     */
    private fun debugReportStopped() {
        if (debugGame?.get() === this) debugGame = null
        val app = applicationContext
        val auto = if (::dataRoot.isInitialized && ::paths.isInitialized) File(dataRoot, paths.state(NxPaths.AUTO_SLOT)) else null
        val since = debugStartedAt
        autoWriter.execute {
            val written = auto?.takeIf { it.isFile && it.lastModified() >= since }?.absolutePath
            NxTestResults.write(app, "game", "state" to "stopped", "autoState" to written)
        }
    }

    private fun writeAutoStateOnStop() {
        val view = retroView ?: return
        val ss = saveStates ?: return
        if (!gameReady || resumePending) return
        // Serialize synchronously on the GL thread: paused, it still runs queued events, and native makes the paused
        // view's context current for the call. A core may run emulation and GL inside it (Azahar's RunLoop drain), which
        // crashes on a thread without the context. The file writes go to [autoWriter] to keep them off the
        // main thread; they are atomic renames, so a reader sees the previous state or the new one.
        val t0 = SystemClock.elapsedRealtime()
        val bytes = runCatching { view.serializeState() }
            .onFailure { NxLog.e("GameActivity", "auto-resume state on stop failed", it) }.getOrNull()
        val serializeMs = SystemClock.elapsedRealtime() - t0
        if (bytes == null || bytes.isEmpty()) return
        autoWrittenOnStop = true
        val ok = AtomicBoolean(false).also { autoWriteOk = it }
        val failed = AtomicBoolean(false).also { autoWriteFailed = it }
        autoWriter.execute {
            val t1 = SystemClock.elapsedRealtime()
            runCatching {
                ss.writeAuto(bytes)
                ok.set(true)   // the state is replaced: its thumb must follow even if the slot pointer fails
                lastResumePoint = NxPaths.AUTO_SLOT
            }.onFailure { failed.set(true); NxLog.e("GameActivity", "auto-resume state on stop failed", it) }
            if (ok.get()) runCatching { ss.markAuto(onlyIfNotManual = true) }
                .onFailure { NxLog.e("GameActivity", "auto-resume slot pointer on stop failed", it) }
            runCatching { lastSlotCache = ss.lastSlot() }   // the pointer may name slot 9 (shown as slot 1)
            NxLog.i("GameActivity", "auto-state on stop: serialize=${serializeMs}ms write=${SystemClock.elapsedRealtime() - t1}ms bytes=${bytes.size}")
        }
        homeThumb?.let { queueHomeThumb(it) }
        homeThumb = null
    }

    override fun onDestroy() {
        rumbles.forEach { it.release() }
        motionSensors?.stop()
        if (isWii) LibretroDroid.setSensorEnabled(0, false)   // a next Wii game sets its own on its first frame
        if (isFinishing || relaunching) endGameTime()   // Back/finish without Quit (quit already ended it), or the GLES relaunch
        if (isFinishing) PendingLaunches.take(launchId)   // quit before the first frame: the launch never started
        if (BuildConfig.DEBUG && isFinishing) debugReportStopped()
        input.gcClearTriggers()   // a latched soft press never outlives the game
        stopAchievements()
        inputManager?.unregisterInputDeviceListener(input.inputDeviceListener)
        dual.hidePresentation()
        cacheHoldsDrained = true
        releaseCacheHolds()
        // ON_DESTROY goes to the lifecycle observers before this body (API 29+, onActivityPreDestroyed) or from within
        // super.onDestroy() (older): either way GLRetroView's LibretroDroid.destroy() has run by the release below.
        super.onDestroy()
        // netplay: after the native destroy (above), off the main thread: the engine shut down, a session copy deleted.
        // Only this screen's own claim is deleted: a newer launch's copy (committed before this teardown) is kept.
        netplay?.let { np ->
            val owner = netplayOwner
            NetplayGame.release(netplayGame, if (np.onCopy) ({ NetplaySessionDir.release(filesDir, owner) }) else null)
            // The cable carried this session (the sheet handed it over at launch), unless a newer launch's start took it over.
            netplayUsbToken?.takeIf { np.overUsb }?.let { UsbCables.get(this).stopIf(it) }
        }
        sessionClaim?.let { sessions.release(it.token) }
        sessionClaim = null
        if (usingData) { usingData = false; DataRoot.useEnd(this) }   // after the core's last write (destroyed above)
    }

    /** A foreground kill (crash, OOM) closes the session at the last touch: keep it at most a minute old while started. */
    private fun startGameTimeTicker(rec: GameTimeRecorder) {
        gameTimeTicker?.cancel()
        if (!lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) { gameTimeTicker = null; return }   // onStart restarts it
        gameTimeTicker = lifecycleScope.launch { while (true) { delay(60_000); rec.touch() } }
    }

    private fun endGameTime() {
        gameTimeTicker?.cancel(); gameTimeTicker = null
        gameTime?.end(); gameTime = null
    }

    private suspend fun startEmulator() {
        val coreFile = intent.getStringExtra(EXTRA_CORE_FILE) ?: return fail("No core given")
        // netplay: extras that make no session must not start an ordinary game on the real saves; a recreated session is over.
        if (intent.hasExtra(EXTRA_NETPLAY_ROLE) && netplay == null && !netplayLocal) return fail(NetplayText.FAILED, NetplayText.FAILED)
        if (netplayRestored) return fail(NetplayText.ENDED, NetplayText.FAILED)
        val np = netplay
        val tag = intent.getStringExtra(EXTRA_TAG) ?: "UNKNOWN"
        val title = intent.getStringExtra(EXTRA_TITLE) ?: "game"
        val coreId = intent.getStringExtra(EXTRA_CORE_ID) ?: coreFile.removePrefix("lib").substringBefore("_libretro")
        // The tag's own descriptor: a tag view (SGB) carries its own extensions and defaults; the id alone would give plain mgba.
        val descriptor = CoreRegistry.forTag(tag)?.takeIf { it.id == coreId } ?: CoreRegistry.forId(coreId)
        val players = CoreRegistry.playersFor(tag, descriptor)
        playerCount = players
        // netplay: no slots, so every pad and the touch pad drive native port 0, the engine's local word.
        input.slots = if (players > 1 && np == null) PlayerSlots(players) else null
        // onCreate laid out before the slots existed: a pad benched in an earlier game counted there. Lay out again (only
        // when a benched pad is attached: one player, slots null, never re-lays out here).
        if (padLayout.layoutControllerNames() != padLayout.controllerNames()) padLayout.applyLayout(resources.configuration.orientation)
        // Kept for one player: a key held through the load keeps its recorded send.
        if (input.inputs.count != players) input.inputs = PortInputs(players)
        rumbles = Array(players) { RumbleMotor(this, gameRumble, phoneFallback = it == 0) }
        // A core that ignores codes still takes RAM patches (ramCheats).
        val cheatsOk = descriptor?.let { it.cheats || it.ramCheats } ?: true
        cheatsSupported = np == null && cheatsOk   // netplay: cheats would desync the other device
        // A SAF document URI, or a file ref with All files access: opened only through [refs], never Uri.parse.
        val content = ContentRef(intent.getStringExtra(EXTRA_CONTENT_URI) ?: return fail("No content given"))
        val romFileName = intent.getStringExtra(EXTRA_FILE_NAME)
            ?: withContext(Dispatchers.IO) { runCatching { refs.displayName(content) }.getOrNull() } ?: title
        if (!usingData) { DataRoot.useBegin(); usingData = true }   // no move starts under the session
        dataRoot = withContext(Dispatchers.IO) { DataRoot.settled(this@GameActivity) }   // waits for a move still running
        privateRoot = DataRoot.private(this)
        cheatsFolder = Cheats.folder(tag, romFileName)
        // The battery-save, clock and state names follow the handheld's save settings. The save setup waits
        // for the unpack (after the inner-file check below), since Use extracted file name names them after the
        // archive's inner file; the config and marker names never depend on the settings.
        paths = NxPaths(tag, coreId, romFileName, AndroidAppSettings(this).saveNaming)
        val home = AndroidHomeLocator(this).current()   // a SAF tree or a file ref (the media home without access): BiosImport picks the tree
        // netplay: a session on a copy never touches the real saves: its session folder is its own.
        val sessionDir = if (np?.onCopy == true) NetplaySessionDir.dir(filesDir) else null
        gameTag = tag
        gameCoreId = coreId
        // Normally done by MainActivity; here too for a restore from recents in a fresh process. Not awaited:
        // it is synchronized, and a first-run or post-update copy (~10 MB) must not hold up the game. Re-read
        // once it is done.
        val extraction = lifecycleScope.async(Dispatchers.IO) { Overlays.extractBundled(this@GameActivity) }
        val shaderExtraction = lifecycleScope.launch(Dispatchers.IO) { Shaders.extractBundled(this@GameActivity) }
        restartOnlyOptions = descriptor?.restartOnlyOptions ?: emptySet()
        forcedOptions = descriptor?.lockedOptions ?: emptySet()
        hiddenValues = descriptor?.hiddenValues ?: emptyMap()
        val realPath = intent.getStringExtra(EXTRA_LOAD_MODE) == "REAL_PATH"
        val companionNames = intent.getStringArrayExtra(EXTRA_COMPANION_NAMES) ?: emptyArray()
        val companionUris = intent.getStringArrayExtra(EXTRA_COMPANION_URIS) ?: emptyArray()
        val companions = companionNames.zip(companionUris).map { (name, ref) -> name to ContentRef(ref) }
        descriptorDefaults = (intent.getStringArrayExtra(EXTRA_OPTIONS) ?: emptyArray())
            .mapNotNull { kv -> kv.indexOf('=').takeIf { it > 0 }?.let { kv.substring(0, it) to kv.substring(it + 1) } }
            .toMap()
        // melonDS DS: GPU where the device has GLES 3.2, as a lowest-priority default; a stored value wins, none is written.
        if (coreId == NdsRenderer.CORE_ID_MELONDSDS) {
            val gles = (getSystemService(ACTIVITY_SERVICE) as android.app.ActivityManager).deviceConfigurationInfo.reqGlEsVersion
            descriptorDefaults = NdsRenderer.defaults(gles) + descriptorDefaults
        }
        val store = CoreConfigStore(File(dataRoot, paths.consoleConfig), File(dataRoot, paths.gameConfig)).also { configStore = it }
        val resolved = withContext(Dispatchers.IO) {
            val (console, game) = store.read()
            CoreConfig.resolve(descriptorDefaults, console, game, forcedOptions, hiddenValues)
        }
        configSource = resolved.source
        lockedOptions = resolved.locked
        storedValues = resolved.values
        ndsRenderMode = resolved.values[NdsRenderer.RENDER_MODE]
        input.n3dsCoreValues(resolved.values)
        val savedCheats = CheatSet.savedFrom(resolved.values, resolved.source)
        // The file Dolphin loads, once known: an archive's unpacked disc names its built-in cheats (completed below).
        val dolphinContent = CompletableDeferred<String>()
        // Off the critical path: the menu shows the set once it lands, the first-frame apply waits for it.
        val loadCheats = { lifecycleScope.launch {
            val extensions = descriptor?.extensions.orEmpty()
            val resolvedSet = withContext(Dispatchers.IO) {
                // PSP cheat files are named by serial ("[ULUS-10466]"), which the disc knows and the file name rarely does.
                val serial = if (descriptor?.id == "ppsspp" && PspSerial.readable(romFileName)) PspSerialReader.read(refs, content, romFileName) else null
                Cheats.resolve(CheatsRoot.of(this@GameActivity), tag, romFileName, title, extensions, serial)
                    .let { if (descriptor?.cheats == false) it.ramPatchesOnly() else it }   // its codes would do nothing
            }
            val combined = if (isDolphin) {
                dolphinBuiltInPath = DolphinCheats.builtInFile(filesDir.absolutePath, CoreRegistry.contentName(tag, dolphinContent.await()))
                val builtIn = readDolphinBuiltIn()
                // Dolphin writes its list at boot, only if absent: a first launch has none yet (see the first-frame re-resolve).
                if (builtIn == null) { dolphinPlayerCheats = resolvedSet; dolphinSavedCheats = savedCheats.orEmpty() }
                DolphinCheats.combine(builtIn, resolvedSet)
            } else resolvedSet
            cheatSet = savedCheats?.let(combined::withSaved) ?: combined
            menu.menuModel?.let { it.setCheats(cheatSet.entries, cheatsFolder, cheatSet.expected); menu.menuState.value = it.state }
            dual.refreshDualPanel()
        } }
        cheatsLoad = if (np != null) null else loadCheats()   // netplay: no cheats (a saved nx_cheats set included)
        // netplay link: the cheats come once the session has ended and the game is a local one.
        if (np?.endsAsLocalGame == true) localCheats = { cheatsSupported = cheatsOk; loadCheats() }
        // Resolved and decoded before the view exists, so the first frame already has its overlay (the configured one;
        // with none stored the default is None).
        // A DS game starts split (every DS layout draws its screens as regions), which shows no overlay: none is
        // preloaded, and it counts as split until its first frame says otherwise, so the configured one never flashes.
        val landscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val ndsAtStart = NdsOptions.appliesTo(tag)
        val (sets, preloaded) = withContext(Dispatchers.IO) {
            val found = Overlays.sets(privateRoot, tag)
            val overlay = FrontendSettings.parse(FrontendOptions.fromConfig(resolved.values)).overlay
            val file = if (ndsAtStart) null else OverlayResolver.resolve(tag, found, overlay, landscape)
            found to file?.let { f -> renderer.decodeOverlay(f)?.let { f.path to it } }
        }
        renderer.overlaySets = sets
        renderer.overlayBitmap = preloaded
        renderer.ndsSplit = ndsAtStart
        // nx_* (and legacy minarch_*) keys are frontend settings: never passed to the core.
        frontendValues = FrontendOptions.fromConfig(resolved.values, tag)
        // netplay: both devices run the console's default Sega pad (Auto), whatever this device's console or game setting;
        // the core then gets the handheld pak's pad for this tag and core (NetplayPads).
        if (np != null && PadMap.isSegaPadTag(tag)) frontendValues = frontendValues + (PadOptions.SEGA_PAD to SegaPadType.AUTO)
        val netplayPad = if (np != null) NetplayPads.forSession(tag, descriptor?.id) else null
        renderer.shaderValues = ShaderOptions.fromConfig(resolved.values)
        renderer.presetNames = withContext(Dispatchers.IO) { Shaders.presets(privateRoot) }
        // 3DS with a second display: Azahar starts in the split's fixed layout, which replaces the rows' values below. The core
        // never runs the user's swap values then, so leaving split puts back the stored ones.
        val launchDual = dual.dualModeNow()
        val n3dsSplit = if (N3dsOptions.appliesTo(tag)) DualScreen.n3dsCoreOptions(launchDual) else null
        if (n3dsSplit != null) dual.n3dsSwapBeforeSplit = DualScreen.n3dsSwapRestore(null, resolved.values)
        val options = FrontendOptions.coreValues(resolved.values)
            .filterNot { (k, _) -> k == N3dsOptions.LAYOUT && N3dsOptions.appliesTo(tag) }   // set from the row below
            .filterNot { (k, _) -> n3dsSplit?.containsKey(k) == true }
            .map { (k, v) -> Variable(k, v) } +
            // 3DS Top over bottom: a bigger top screen in landscape (onConfigurationChanged follows a rotation).
            if (N3dsOptions.appliesTo(tag)) listOf(
                Variable(N3dsOptions.STACKED_SMALL_PROPORTION, N3dsOptions.stackedSmallProportion(isLandscape())),
                Variable(N3dsOptions.TOUCH_MAIN_ONLY, DualScreen.n3dsTouchMainOnly(launchDual, padLayout.overlayPadShown.value)),
                Variable(N3dsOptions.CURSOR_VISIBLE, N3dsOptions.cursorVisible(false)),   // stylus mode is off at launch
                // The locked core layout ([N3dsOptions.LAYOUT]) from the row in use; the descriptor's default is replaced.
                Variable(N3dsOptions.LAYOUT, N3dsOptions.layoutFor(frontendValues, isLandscape(), renderer.ndsControllerMode())),
            ).withN3dsSplit(n3dsSplit) else emptyList()
        // netplay link: gambatte gets its link options; a GBA link engine starts now, before the core loads, so
        // a joiner learns the host's gpsp_serial from its READY and runs with it (the handheld reloads instead).
        val linkKind = np?.let { NetplayCores.link(it.coreId) }
        val linkOptions: Map<String, String> = if (np == null || linkKind == null) emptyMap() else when (linkKind) {
            NetplayLinkKind.GBLINK -> NetplayLinkOptions.forSession(np, null)
            NetplayLinkKind.NETPACKET -> {
                val own = options.firstOrNull { it.key == NetplayLinkOptions.GPSP_SERIAL }?.value
                val mode = connectLink(np, own) ?: return fail(NetplayText.FAILED_CONNECT, NetplayText.FAILED)
                NetplayLinkOptions.forSession(np, mode)
            }
        }
        val frontend = FrontendSettings.parse(frontendValues)

        // An arcade set's BIOS that was not beside it must be in the system dir (Bios/FBN/ is copied there); a missing parent is
        // not refused (a non-merged clone carries its parent's ROMs), so only BIOS sets arrive here.
        val systemSets = intent.getStringArrayExtra(EXTRA_SYSTEM_SETS)?.toList() ?: emptyList()
        // flycast's set table: a DC zip/7z named after one of its NAOMI/Atomiswave sets is loaded as is and needs its BIOS
        // archive (naomi.zip, awbios.zip ...) in <system>/dc/; any other DC zip/7z (a zipped disc) is unpacked below.
        val naomiSets = if (descriptor?.id == "flycast" && Names.isArchive(romFileName)) {
            withContext(Dispatchers.IO) { NaomiSetsAsset.get(this@GameActivity) }
        } else NaomiSets.EMPTY
        val archiveAsIs = CoreRegistry.loadsArchive(tag, romFileName, naomiSets)
        val naomiBios = if (archiveAsIs) naomiSets.biosFor(Names.stripExtension(romFileName)) else null
        // The files this launch needs, looked up by name when a Bios folder will not list.
        val biosNames = CoreRegistry.requiredBios(tag).flatMap { it.anyOf } +
            (systemSets + listOfNotNull(naomiBios)).flatMap { listOf("$it.zip", "$it.7z") }
        // Usually a stamp check: MainActivity pre-warms the copy after an update; if it is still running, this waits on its lock.
        val systemDir = withContext(Dispatchers.IO) { SystemFiles.ensure(this@GameActivity) }
        // Bios/<TAG>/ lands in the core's BIOS folder: the system dir, or a subfolder of it (flycast's <system>/dc/).
        val biosDir = BiosFolders.subdir(tag).let { if (it.isEmpty()) systemDir else File(systemDir, it) }
        withContext(Dispatchers.IO) { BiosImport.sync(this@GameActivity, home, BiosFolders.forTag(tag), biosDir, biosNames) }
        // An archive is unpacked into the cache first; the core gets the inner file by real path, and everything keyed by
        // romFileName (config, cheats, thumbnails; saves and states unless Use extracted file name is on) keeps the archive
        // name, as on the Brick; unless the tag's core loads the archive as is (fbneo's romsets and flycast's known sets:
        // the zip is the game).
        // Only a real unpack outlasts the delay and shows the toast; it is taken down with the unpack, not left over the game.
        val unpacked: Unpacked? = if (!Names.isArchive(romFileName) || archiveAsIs) null else {
            var unpackToast: android.widget.Toast? = null
            val unpacking = lifecycleScope.launch { delay(400); unpackToast = toast("Unpacking $romFileName…") }
            try {
                val romExtensions = descriptor?.extensions ?: CoreRegistry.coreExtensions
                withContext(Dispatchers.IO) {
                    // flycast names the VMU after the file it is given: unpacked as <archive stem>.<ext>, the VMU is <stem>.A1.bin.
                    Archives.extract(this@GameActivity, content, romFileName, romExtensions, descriptor?.savesNamedAfterContent == true)
                        .also { keepCacheHold(it.file.parentFile!!.name) }
                }
            } catch (e: ArchiveException) {
                NxLog.w(TAG_LOG, "unpack failed: ${e.kind}", e)
                return fail(e.message ?: "Could not unpack $romFileName")
            } finally {
                unpacking.cancel()
                unpackToast?.cancel()
            }
        }
        val inner: File? = unpacked?.file
        val unpackedEntry = unpacked?.entryName
        dolphinContent.complete(inner?.name ?: romFileName)   // Dolphin names its cheat file after the inner disc, not the archive
        if (inner != null && descriptor != null && Names.extensionOf(inner.name) !in descriptor.extensions) {
            // Name a tag of the same console whose core runs the file only when one exists; otherwise the file is in the wrong folder.
            val alt = CoreRegistry.consoleOf(tag)?.tags?.firstOrNull { t ->
                t != tag && CoreRegistry.forTag(t)?.let { Names.extensionOf(inner.name) in it.extensions } == true
            }
            return fail(
                "$romFileName holds ${inner.name}, which ${descriptor.id} does not run. " +
                    if (alt != null) "Pick ${CoreRegistry.handheldTag(alt)} from the game's Emulator menu." else "Move it to the folder of the console it belongs to.",
            )
        }
        // minarch names saves after the unpacked file when Use extracted file name is on (ma_game.c:75-76): known only now.
        paths = paths.withInner(inner?.let { unpackedEntry })
        saveStates = if (np == null) SaveStates(dataRoot, paths) else null   // netplay: no save states, no auto-resume
        refreshSlots()   // warm for the first menu open
        dropStaleNdsResume(tag, coreId, romFileName)
        np?.let { prepareNetplaySaves(it, tag, romFileName) }
        // netplay: on a copy the battery save is the session folder's (read only: writeBatterySave skips it), under the
        // default names (the folder is mobile's own), with no clock file.
        val sessionPaths = if (sessionDir != null) NxPaths(tag, coreId, romFileName) else null
        // melonDS DS: a handheld Saves/NDS/<rom>.dsv that is the game's only save is played in place (not on a netplay copy).
        val dsvStem = if (coreId == NdsDsvSave.CORE_ID && sessionDir == null) paths.saveStem else null
        val battery = BatterySaves(sessionDir ?: File(dataRoot, paths.savesDir), sessionPaths ?: paths, dsvStem).also { batterySaves = it }
        val clock = if (sessionDir != null) null else ClockSave(File(dataRoot, paths.clock)).also { clockSave = it }
        val (sram, rtc) = withContext(Dispatchers.IO) {
            battery.load() to clock?.load()
        }
        NxLog.i(TAG_LOG, "battery save: ${battery.loadedFrom ?: "none"}")
        if (BuildConfig.DEBUG) NxTestResults.write(this, "battery", "loadedFrom" to battery.loadedFrom, "bytes" to (sram?.size ?: 0))
        // A damaged configured file is set aside as a .bak at the first save, then the game saves again. Not said for a
        // netplay copy: it is never written, so the file stays as it is.
        if (battery.damaged && sessionDir == null) toast("Save file is damaged — it will be kept as a .bak when the game saves")
        if (!battery.writable) toast(if (battery.loadedFrom == null) "Save file is damaged — not loaded" else "Save file is damaged — loaded ${battery.loadedFrom}, not saving")
        // Sega Controller Type (Console Settings): the cart header's I/O field under Auto, else the forced pad.
        val segaSetting = if (PadMap.isSegaPadTag(tag)) frontendValues[PadOptions.SEGA_PAD] ?: SegaPadType.AUTO else null
        if (segaSetting != null) {
            val headerName = inner?.name ?: romFileName
            val header = if (!SegaHeader.readable(headerName)) null else withContext(Dispatchers.IO) {
                runCatching {
                    (inner?.inputStream() ?: refs.openInput(content))?.use { s ->
                        val buf = ByteArray(SegaHeader.READ_LENGTH)
                        var n = 0
                        while (n < buf.size) { val r = s.read(buf, n, buf.size - n); if (r < 0) break; n += r }
                        SegaHeader.padType(buf.copyOf(n))
                    }
                }.getOrNull()
            }
            segaHeader = header
            // The current value, not segaSetting: the menu may have changed it while the header was read.
            segaPad = netplayPad?.pad ?: SegaPadType.resolve(frontendValues[PadOptions.SEGA_PAD] ?: SegaPadType.AUTO, header)
            dropPadProfile()   // the profile follows the resolved pad
        }
        // New 3DS Controls (Console Settings): Auto reads the title from the file's NCSD / NCCH header, off the main thread.
        if (N3dsOptions.appliesTo(tag)) {
            val headerName = inner?.name ?: romFileName
            n3dsTitle = if (!N3dsTitle.readable(headerName)) null else withContext(Dispatchers.IO) {
                runCatching { (inner?.inputStream() ?: refs.openInput(content))?.use(::readN3dsTitle) }
                    .onFailure { NxLog.w(TAG_LOG, "reading the 3DS header failed", it) }
                    .getOrNull()
            }
            NxLog.i(TAG_LOG, "3DS title: ${n3dsTitle?.titleId?.toString(16)} ${n3dsTitle?.productCode}")
            // The current value: the menu may have changed it while the header was read.
            val resolved = N3dsNewControls.resolve(frontendValues[N3dsOptions.NEW_CONTROLS], n3dsTitle)
            if (resolved != n3dsNew) {
                n3dsNew = resolved
                dropPadProfile()
                padLayout.applyLayout(resources.configuration.orientation)   // the pad was composed without them in onCreate
            }
        }
        // Wii Controller and Motion Controls (Console Settings): Auto reads the disc ID and looks it up in GameTDB's table.
        val wiiDisc = if (!WiiOptions.appliesTo(tag) || !WiiDiscId.readable(inner?.name ?: romFileName)) null else withContext(Dispatchers.IO) {
            inner?.let(WiiDiscIdReader::read) ?: WiiDiscIdReader.read(refs, content, romFileName)
        }
        // A GameCube image under the WII tag (outside a gc folder): the plain pad, the C-stick as the right stick.
        if (wiiDisc?.gameCube == true) {
            wiiGameCube = true
            dropPadProfile()   // the GameCube pad, not the plain one
            padLayout.applyLayout(resources.configuration.orientation)   // the pad was composed from the intent tag in onCreate
            NxLog.i(TAG_LOG, "GameCube disc ${wiiDisc.id} under the Wii tag: no Wii controls")
            menu.menuModel?.let { refreshWiiMenu(it); menu.menuState.value = it.state }   // a menu opened during the read drops its Wii rows
        }
        if (WiiOptions.appliesTo(tag) && !wiiGameCube) {
            val id = wiiDisc?.id
            wiiControls = withContext(Dispatchers.IO) { WiiControlsAsset.get(this@GameActivity).lookup(id) }
            val sensors = motionSensors ?: MotionSensors(this@GameActivity) { a, g ->
                LibretroDroid.setSensorValues(0, a.x, a.y, a.z, g.x, g.y, g.z)
            }.also { motionSensors = it }
            LibretroDroid.setSensorAvailable(sensors.hasAccel, sensors.hasGyro)
            // The rotation the level starts in: only a real rotation recentres, not the first unrelated configuration
            // change (a pad attached, a fold posture).
            lastRotation = displayRotation()
            // The current values: the menu may have changed them while the image was read.
            val pad = WiiController.resolve(frontendValues[WiiOptions.CONTROLLER], wiiControls)
            wiiPad = pad
            wiiMotionOn = WiiMotion.resolve(frontendValues[WiiOptions.MOTION], pad, wiiControls, sensors.hasSensors)
            LibretroDroid.setSensorEnabled(0, wiiMotionOn)
            NxLog.i(TAG_LOG, "Wii disc $id: $wiiControls -> $pad, motion $wiiMotionOn")
            dropPadProfile()
            padLayout.applyLayout(resources.configuration.orientation)
            // A menu opened during the read: its Motion Controls row (left out until the sensors were known) and its Wii rows.
            menu.menuModel?.let { refreshWiiMenu(it); menu.menuState.value = it.state }
        }
        padMap = if (isDc) PadMap.IDENTITY else PadMap.forTag(tag, segaPad)
        // Only 3-button draws differently from the six the pad was composed with in onCreate: recompose it.
        if (segaPad == SegaPad.THREE) padLayout.applyLayout(resources.configuration.orientation)
        val contentExtension = Names.extensionOf(inner?.name ?: romFileName)
        // A missing required BIOS: say which file goes where instead of letting the core fail silently.
        val present = withContext(Dispatchers.IO) { systemDir.list()?.toSet() ?: emptySet() }
        val biosPresent = if (biosDir == systemDir) present else withContext(Dispatchers.IO) { biosDir.list()?.toSet() ?: emptySet() }
        BiosCheck.missing(CoreRegistry.requiredBios(tag), contentExtension, biosPresent).firstOrNull()?.let {
            return fail(BiosCheck.message(tag, it))
        }
        // A NAOMI/Atomiswave set's BIOS archive (flycast opens <system>/dc/<bios>.zip or .7z); an unknown set is not checked.
        if (archiveAsIs) naomiSets.missingBios(romFileName, biosPresent)?.let { return fail(NaomiSets.biosMessage(title, it)) }
        // A missing arcade BIOS set (systemSets, read above) is refused here.
        if (systemSets.isNotEmpty()) {
            val fbneoFiles = withContext(Dispatchers.IO) { File(systemDir, "fbneo").list()?.toSet() ?: emptySet() }
            ArcadeCompanions.missing(systemSets, present, fbneoFiles)?.let { set ->
                return fail("$title needs $set.zip: put it next to the game or in Bios/FBN/")
            }
        }
        val savesDir = (sessionDir ?: File(dataRoot, paths.savesDir)).apply { mkdirs() }
        // netplay: a link session's game is the player's own, so it gets achievements as a normal launch; lockstep has none.
        if (np == null || np.allowsAchievements) prepareAchievements(tag, romFileName, content, inner)

        // A REAL_PATH core's copy into the cache gets the unpack's toast: only one that outlasts the delay shows it.
        var copyToast: android.widget.Toast? = null
        val copying = if (realPath && inner == null) lifecycleScope.launch { delay(400); copyToast = toast("Copying $romFileName…") } else null
        // What the core is launched with, read now (a change during the header read or the BIOS checks included); a
        // change after this reaches the core at the first frame.
        val launchSega = if (segaSetting != null) frontendValues[PadOptions.SEGA_PAD] ?: SegaPadType.AUTO else null
        val launchSegaPad = segaPad
        launchedSegaSetting = launchSega
        val data = try {
            withContext(Dispatchers.IO) {
                // The name the core sees: an arcade zip in lowercase (fbneo matches the driver name case-sensitively).
                val fileName = CoreRegistry.contentName(tag, romFileName)
                // Console Settings → Graphics API: the core options for the API and whether the runner offers Vulkan.
                // Vulkan that could not start here before, or this is the relaunch after it did: OpenGL ES (VulkanFallback).
                val vulkanFailed = intent.getBooleanExtra(EXTRA_FORCE_GLES, false) ||
                    descriptor?.id in AndroidAppSettings(this@GameActivity).vulkanFailedCores
                val graphics = GraphicsApiOptions.resolve(frontendValues[GraphicsApiOptions.KEY], descriptor?.vulkan, { LibretroDroid.isVulkanSupported() }, vulkanFailed)
                launchedOnVulkan = graphics.preferVulkan
                launchEnv = graphics.env
                // netplay link: the link options are this launch's too (a session's port, mode and peer): never saved.
                graphicsForcedOptions = graphics.forcedOptions.keys + linkOptions.keys
                GLRetroViewData(this@GameActivity).apply {
                    coreFilePath = coreFile
                    systemDirectory = systemDir.absolutePath
                    savesDirectory = savesDir.absolutePath
                    // netplay: the handheld pak's pad options (NetplayPads) replace the Controller Type's, and a link
                    // session's (NetplayLinkOptions) the user's, set even when absent.
                    val forced = netplayPad?.options.orEmpty() + linkOptions + wiiScreenOption() + graphics.forcedOptions
                    variables = (options.map { v ->
                        forced[v.key]?.let { Variable(v.key, it) }
                            ?: if (launchSega != null && (v.key == SegaPadType.PICODRIVE_INPUT1 || v.key == SegaPadType.PICODRIVE_INPUT2))
                                Variable(v.key, SegaPadType.picodriveInput(launchSegaPad)) else v
                    } + forced.filterKeys { k -> options.none { it.key == k } }.map { (k, v) -> Variable(k, v) }).toTypedArray()
                    rumbleEventsEnabled = gameRumble != GameRumble.OFF   // Game rumble Off: the runner does not even read the core's state
                    preferLowLatencyAudio = true
                    // GL driver messages and a frame probe to tag NXGL, with Settings → System → Debug log on at
                    // launch.
                    glDebugOutput = NxLog.enabled
                    // Console Settings → Graphics API ([graphics]): Vulkan frames are bridged into the GLES presentation.
                    preferVulkan = graphics.preferVulkan
                    vulkanSwapchainFrames = graphics.swapchainFrames
                    // gpsp: never unload the core between games, so its recompiler cache stays mapped near it.
                    keepCoreLoaded = descriptor?.keepLoaded == true
                    shader = renderer.shaderFor(frontend)
                    saveRAMState = sram
                    rtcState = rtc   // the clock file, applied after the battery save
                    // Set after load and before the first frame: pcsx_rearmed's DualShock (a gamepad's sticks reach the
                    // core), flycast's joypad (its ports start empty, so the BIOS and game would see no controller) with
                    // its unused ports set to none, which completes its device setup (the VMU in slot 1). Player 2+
                    // ports get the same device, as does a forced genesis_plus_gx Controller Type.
                    descriptor?.let { controllerPorts = it.controllerPortsFor(players) }
                    // Dolphin: port 0 and the slots a pad already holds get the game's device (Wii: the resolved controller,
                    // Dolphin's Wiimote + Nunchuk, Classic, MotionPlus); the other ports stay unplugged until a pad claims
                    // them (connectDolphinPorts), or Dolphin connects Wiimotes 2-4 and the game shows a co-star cursor.
                    if (descriptor?.id == DOLPHIN_ID) controllerPorts = dolphinPorts(dolphinDevice(), claimedPorts(), players)
                    if (descriptor?.id == SegaPadType.GPGX) SegaPadType.gpgxPorts(launchSega, players)?.let { controllerPorts = it }
                    netplayPad?.ports?.takeIf { it.isNotEmpty() }?.let { controllerPorts = controllerPorts + it }   // netplay: the pak's ports 0-1
                    if (inner != null) {
                        gameFilePath = inner.absolutePath
                    } else if (realPath) {
                        // A file ref (All files access) is opened in place when it and its companions are readable there;
                        // a SAF game is copied: a disc set's files go next to its sheet in the cache, under the names the sheet uses.
                        val inPlace = RealPathLaunch.inPlace(content, fileName, companions)
                            ?.takeIf { p -> File(p).canRead() && companions.all { (name, _) -> File(File(p).parentFile, name).canRead() } }
                        if (inPlace != null) {
                            gameFilePath = inPlace
                        } else {
                            for ((name, ref) in companions) copyToCache(ref, name)
                            gameFilePath = copyToCache(content, fileName).absolutePath
                        }
                    } else {
                        val pfd = refs.openFd(content, "r")
                            ?: throw IOException("Cannot open file descriptor")
                        // With more than one virtual file the runner serves the core through its VFS (libretrodroid.cpp:391),
                        // so the core opens /rom/<track>.bin by the name its cue gives.
                        val extra = companions.map { (name, ref) ->
                            VirtualFile("/rom/$name", refs.openFd(ref, "r") ?: throw IOException("Cannot open $name"))
                        }
                        gameVirtualFiles = listOf(VirtualFile("/rom/$fileName", pfd)) + extra
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e   // lifecycleScope cancelled mid-copy (a back-out, a destroy): not a launch failure to report
        } catch (e: Exception) {
            fail("Cannot open $title: ${e.javaClass.simpleName}: ${e.message}")
            return
        } finally {
            copying?.cancel()
            copyToast?.cancel()
        }

        // Graphics API's process environment for the core (ParaLLEl-RDP's switches), before it loads; unset otherwise.
        launchEnv.forEach { (k, v) ->
            runCatching { if (v != null) android.system.Os.setenv(k, v, true) else android.system.Os.unsetenv(k) }
                .onFailure { NxLog.w("GameActivity", "could not set $k", it) }
        }
        if (isN3ds) offerN3dsMotion()
        val view = GLRetroView(this, data)
        retroView = view
        if (focusPaused) { view.frameSpeed = 0; view.audioEnabled = false }   // focus went to another app while loading
        // Collected before the view is added (its ON_CREATE) and long before the first frame: the runner reports a
        // change only, from an unbuffered flow, so a missed event would leave the motor wrong until the next one.
        if (gameRumble != GameRumble.OFF) lifecycleScope.launch {
            view.getRumbleEvents().collect { e -> if (!quitting) rumbles.getOrNull(e.port)?.onRumble(e.strengthWeak, e.strengthStrong) }
        }
        // The core asked to end (Dolphin: the game's HOME menu → Wii Menu, which is not installed): close as Quit does,
        // without a resume state, since the game has stopped itself by then. The resume point is HOME's ([pressWiiHome]).
        lifecycleScope.launch {
            view.getShutdownRequests().first()
            // Left without HOME this session (a game's own "Wii Menu", a channel launch): the resume point on disk is an
            // older session's, behind the game's own save on the NAND. Dropped, so the next launch boots fresh.
            if (!wiiHomeSaved) withContext(Dispatchers.IO) { saveStates?.let { ss -> autoWriter.submit { ss.dropAuto() }.get() } }
            quitWithAutoState(chosen = true, autoState = false)
        }
        // Subscribed before the login (startSession, after the first frame): events are not replayed.
        achievements?.let { b ->
            b.attach(view)
            lifecycleScope.launch { b.events.collect(::onAchievementEvent) }
        }
        val game = findViewById<FrameLayout>(R.id.gameContainer)
        game.addView(view)
        lifecycle.addObserver(view)
        // Added after the view: LifecycleRegistry sends ON_DESTROY newest-first, so this runs before the view's
        // LibretroDroid.destroy(). It waits for a quit's serialize in flight (one call, milliseconds) and stops the next.
        lifecycle.addObserver(LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_DESTROY) synchronized(coreLock) { coreGone = true } })
        renderer.ensureOverlayView()
        ensureNotificationsView()
        // Size changes (first layout, rotation, cutout margin) re-lay out the picture and the overlay box.
        game.addOnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
            if (right - left != oldRight - oldLeft || bottom - top != oldBottom - oldTop) game.post { renderer.relayoutFrontend() }
        }
        applyFrontend(frontendValues)
        // Set before the first chain push: a chain built with the view reports its errors through this.
        view.onShaderError = { msg -> toast("Shader: $msg"); renderer.dropShaderChain() }
        // A stored preset reads the extracted files: on a first run or after an update, wait for the (~0.5 MB)
        // copy and swap rather than fail or read the tree mid-swap.
        if (ShaderOptions.preset(renderer.shaderValues) != null) shaderExtraction.join()
        renderer.applyShader(renderer.shaderValues)
        // First run or after an update: the bundled sets may still be copying; pick them up once swapped in.
        lifecycleScope.launch {
            val synced = extraction.await()
            val found = withContext(Dispatchers.IO) { Overlays.sets(privateRoot, tag) }
            // After a sync, re-apply even with the same names: an overlay decoded mid-swap may have failed.
            if (synced || found != renderer.overlaySets) {
                renderer.overlaySets = found   // the Overlay value is unchanged (its default is None whatever the files)
                applyFrontend(frontendValues)
            }
        }
        lifecycleScope.launch {
            shaderExtraction.join()
            renderer.presetNames = withContext(Dispatchers.IO) { Shaders.presets(privateRoot) }
            menu.menuModel?.let { it.setShaders(renderer.shaderValues, renderer.presetNames, renderer.shaderParams); menu.menuState.value = it.state }
        }
        lifecycleScope.launch {
            view.getGLRetroEvents().first { it is GLRetroView.GLRetroEvents.FrameRendered }
            gameReady = true
            if (BuildConfig.DEBUG) NxTestResults.write(this@GameActivity, "game", "state" to "running", "tag" to gameTag, "core" to gameCoreId)
            intent.getStringExtra(EXTRA_GAME_PATH)?.let { path ->
                val tag = gameTag
                val name = intent.getStringExtra(EXTRA_TITLE) ?: path.substringAfterLast('/')
                lifecycleScope.launch {
                    val rec = withContext(Dispatchers.IO) { GameTimeService.recorder(this@GameActivity) }
                    rec.start(path, tag, name)
                    gameTime = rec
                    // Ready while stopped (onStop already ran): close it at once, as onStop would; onStart resumes it.
                    if (!lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) rec.pause()
                    startGameTimeTicker(rec)
                }
            }
            // A Controller Type change made while the game loaded was held back (applySegaPad): send it now.
            launchedSegaSetting?.let { launched ->
                val now = frontendValues[PadOptions.SEGA_PAD] ?: SegaPadType.AUTO
                if (now != launched) sendSegaPad(now)
            }
            if (isDolphin && !isWii) sendWiiDevice()   // GameCube: a slot claimed while the game loaded
            if (isWii) {
                view.stickyPointer = true   // the right stick moves the IR pointer, which stays where it was left
                // Always, not only on a change made during load: a previous game's core unload (its onDestroy can run
                // after this screen's onCreate) resets the native sensor state, and resending the device makes Dolphin
                // ask for the sensors again (cores/patches/dolphin.patch).
                motionSensors?.let { LibretroDroid.setSensorAvailable(it.hasAccel, it.hasGyro) }
                LibretroDroid.setSensorEnabled(0, wiiMotionOn)
                sendWiiDevice()
                syncMotionSensors()
                if (wiiControls?.motionPlus == true && motionSensors?.hasSensors != true) {
                    toast("Motion actions in this game need a phone with a gyroscope")
                }
            }
            if (isN3ds) {
                offerN3dsMotion()   // again: a previous game's unload can reset the native sensor state after the launch's offer
                syncMotionSensors()
            }
            dual.pushSecondSurface()   // a presentation shown before the view ran
            if (dual.panelShown()) dual.refreshDiscs()   // a panel up before the game ran asked too early
            // A core may change its geometry later (SET_GEOMETRY), as Azahar does a frame or so after the dual modes switch
            // its layout: both displays are laid out again from the new size and aspect, or the second one keeps fitting
            // the new frame at the old aspect. A core turning its picture (a WonderSwan game held upright) moves the
            // picture screen too. Collected before the first layout below, so no change falls between the two.
            lifecycleScope.launch {
                view.getGameGeometryChanges().collect {
                    // Single display: nothing to do (turning dual on lays out from the geometry then).
                    if (dual.dualMode == DualMode.OFF) return@collect
                    val g = view.getGameGeometry()
                    dual.recheckPictureOn(g)
                    renderer.relayoutFrontend(g)
                }
            }
            renderer.relayoutFrontend()   // the core's geometry (a DS game's 256x384) is only known now
            // melonDS DS falls back to software when its GL renderer can't start: say so once (nothing is saved, the
            // next launch tries the GPU again). ~30 frames lets the core's first hardware frames arrive.
            if (NdsOptions.appliesTo(gameTag) && ndsRenderMode == NdsRenderer.OPENGL) {
                lifecycleScope.launch {
                    view.getGLRetroEvents().filter { it is GLRetroView.GLRetroEvents.FrameRendered }.take(30).collect {}
                    if (NdsRenderer.showFallbackNotice(ndsRenderMode, view.isHardwareRendered())) {
                        toast("GPU renderer unavailable — using software")
                    }
                }
            }
            // A frame means the core loaded the game (a load error aborts the view before any): the launcher may record it.
            PendingLaunches.take(launchId)?.invoke()
            // netplay: the engine starts once the core has loaded and can serialize, so a handheld's 10 s state
            // waits begin only now. The host listens (its 45 s CONNECTING limit counts from here); the client connects
            // with its 60 × 500 ms retries. A menu or background already in place pauses it at once.
            if (np != null) {
                if (!startNetplay(np)) {
                    // The game must not play on (or be heard) behind the dialog, a menu closed meanwhile included.
                    netplayStartFailed = true
                    retroView?.frameSpeed = 0
                    retroView?.audioEnabled = false
                    fail(NetplayText.FAILED, NetplayText.FAILED)
                    return@launch
                }
                if (menuOpen || focusPaused || !lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) netplayGame?.pause()
                val now = SystemClock.elapsedRealtime()
                netplayFirstFrameNotices(np, descriptor?.name ?: coreId, netplayOwnCopyFailed).forEach {
                    notifications.push(it, null, NETPLAY_NOTICE_MS, now)
                }
                publishNotifications()
            }
            val slot = resumeSlot
            if (slot != null) resumeFrom(view, slot) else resumePending = false
            // Cheats sit on top of the resumed state, as minarch applies them after load.
            cheatsLoad?.join()
            refreshDolphinCheats()
            if (cheatSet.hasEnabled()) applyCheats(view)
            achievements?.startSession()
        }
        // A refused game: the core's own reason (flycast names the NAOMI/Atomiswave BIOS or ROM file it could not find).
        val naomiParent = if (archiveAsIs) naomiSets.parent(Names.stripExtension(romFileName)) else null
        observeErrors(view, coreFile) { lines ->
            LoadFailure.message(title, lines, romFileName.takeIf { archiveAsIs }, naomiBios, naomiParent)
        }
        // Debug builds: a file named simulate_vulkan_fail in the app's files dir makes a Vulkan launch take the fallback
        // as a failed bridge setup would (no phone at hand fails it): `adb shell run-as <package> touch files/simulate_vulkan_fail`.
        if (BuildConfig.DEBUG && launchedOnVulkan && (!intent.hasExtra(EXTRA_NETPLAY_ROLE) || netplayLocal) && File(filesDir, SIMULATE_VULKAN_FAIL).exists()) {
            relaunchOnGles()
        }
    }

    /**
     * NDS moved from melonds to melonDS DS: a resume marker left pointing at an old-core state (which melonDS DS can't
     * load) is deleted with its thumbnails, and this launch boots fresh instead of toasting "Resume failed". Once per
     * game, since the marker is gone afterwards; the old core's states stay untouched.
     */
    private suspend fun dropStaleNdsResume(tag: String, coreId: String, romFileName: String) {
        if (tag != NdsResumeCleanup.TAG || coreId != NdsResumeCleanup.CORE_ID) return
        val dropped = withContext(Dispatchers.IO) {
            awaitAutoWrites()   // a Home write of this game's auto state may still be landing
            runCatching {
                val marker = File(dataRoot, paths.lastSlot).takeIf { it.exists() }?.readText()
                val victims = NdsResumeCleanup.victims(tag, coreId, romFileName, marker, paths.naming, paths.innerFileName) { File(dataRoot, it).exists() }
                victims.forEach { File(dataRoot, it).delete() }
                victims.isNotEmpty()
            }.onFailure { NxLog.w("GameActivity", "stale NDS resume cleanup failed", it) }.getOrDefault(false)
        }
        if (dropped) {
            resumeSlot = null
            resumePending = false
        }
    }

    /** Loads [slot] right after start, like minarch's State_resume; a failure keeps the fresh boot. */
    private suspend fun resumeFrom(view: GLRetroView, slot: Int) {
        var ok = false
        try {
            val ss = saveStates ?: return
            ok = withContext(Dispatchers.IO) {
                awaitAutoWrites()   // a Home write of this game may still be landing
                runCatching { ss.readSlotState(slot)?.let { view.unserializeState(it) } ?: false }.getOrDefault(false)
            }
            if (!ok) toast("Resume failed")
        } finally {
            if (BuildConfig.DEBUG) {
                NxTestResults.write(this, "resume", "slot" to slot, "ok" to ok, "path" to File(dataRoot, paths.state(slot)).absolutePath)
            }
            resumePending = false
        }
    }

    private fun observeErrors(view: GLRetroView, coreFile: String, loadFailure: (List<String>) -> String?) {
        lifecycleScope.launch {
            view.getGLRetroErrors().flowWithLifecycle(lifecycle, Lifecycle.State.STARTED).collect { code ->
                if (code == GLRetroView.ERROR_LOAD_LIBRARY) {
                    val lines = runCatching { view.getRecentCoreErrors() }.getOrDefault(emptyList())
                    fail(CoreLoadFailure.message(coreFile, lines))
                    return@collect
                }
                val name = when (code) {
                    GLRetroView.ERROR_LOAD_GAME -> "ERROR_LOAD_GAME"
                    GLRetroView.ERROR_GL_NOT_COMPATIBLE -> "ERROR_GL_NOT_COMPATIBLE"
                    GLRetroView.ERROR_SERIALIZATION -> "ERROR_SERIALIZATION"
                    GLRetroView.ERROR_CHEAT -> "ERROR_CHEAT"
                    else -> "ERROR_GENERIC($code)"
                }
                if (code == GLRetroView.ERROR_LOAD_GAME) {
                    val lines = runCatching { view.getRecentCoreErrors() }.getOrDefault(emptyList())
                    lines.forEach { NxLog.i(TAG_LOG, "core said: $it") }
                    loadFailure(lines)?.let { fail(it); return@collect }
                }
                if (code == GLRetroView.ERROR_GL_NOT_COMPATIBLE && VulkanFallback.relaunchOnGles(launchedOnVulkan)) {
                    // netplay: a relaunch would start without the session (it is gone), so the dialog says what to do. An
                    // ended link session's relaunch ([netplayLocal]) is a plain launch, which relaunches as any other.
                    if (intent.hasExtra(EXTRA_NETPLAY_ROLE) && !netplayLocal) {
                        fail("Vulkan could not start for this game on this device. Set Console Settings → Graphics API to OpenGL ES.")
                    } else {
                        relaunchOnGles()
                    }
                    return@collect
                }
                fail("The core reported $name")
            }
        }
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
        AndroidAppSettings(this).recordVulkanFailure(gameCoreId)
        NxLog.w(TAG_LOG, "Vulkan could not start for $gameCoreId: starting again on OpenGL ES")
        android.widget.Toast.makeText(applicationContext, VulkanFallback.NOTICE, android.widget.Toast.LENGTH_LONG).show()
        intent = Intent(intent).putExtra(EXTRA_FORCE_GLES, true)
        recreate()
    }

    /** [title]: "Netplay failed" for a netplay session's failures, which may come mid-game. */
    private fun fail(message: String, title: String = "Could not start the game") {
        NxLog.e(TAG_LOG, message)
        if (relaunching) return   // a late error from the Vulkan attempt: the recreated screen owns the launch now
        // Destroyed without finishing: a recreation, whose new screen owns the launch (and its PendingLaunches entry), and
        // a dialog on this dead window would throw BadTokenException.
        if (isDestroyed && !isFinishing) return
        PendingLaunches.take(launchId)   // refused: the launcher must not record it
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

    /**
     * Records a RomCache hold this game took, for onDestroy to release. The blocking unpack/copy that took it runs on
     * after a back-out cancels lifecycleScope, so it may land after onDestroy drained: then it is released here. Either
     * this sees [cacheHoldsDrained] after its add, or onDestroy's drain (after setting it) sees the add; poll releases once.
     */
    private fun keepCacheHold(name: String) {
        cacheHolds += name
        if (cacheHoldsDrained) releaseCacheHolds()
    }

    private fun releaseCacheHolds() {
        while (true) RomCache.release(cacheHolds.poll() ?: break)
    }

    /** Opens the game's content and companions, SAF documents or file refs. */
    private val refs by lazy { AndroidRefs(contentResolver) }

    /**
     * A top-level RomCache entry, held for this game; a copy whose stamp still matches the source is reused (and touched:
     * its mtime is its last use). The stamp, `<file name>.nxstamp` beside it, is the source's size
     * and last-modified ([realPathCopyStamp]): a same-size patched ROM is copied again. The copy goes to `<file name>.part`
     * and is renamed into place, the old stamp dropped first, so a kill mid-copy leaves no torn copy that passes for whole.
     */
    private fun copyToCache(ref: ContentRef, fileName: String): File {
        val root = RomCache.root(this).apply { mkdirs() }
        val target = File(root, fileName)
        val stampFile = File(root, cacheSiblingName(fileName, REAL_PATH_STAMP))
        val part = File(root, cacheSiblingName(fileName, ".part"))
        synchronized(RomCache.lockFor(target.name)) {
            RomCache.hold(target.name); keepCacheHold(target.name)
            RomCache.hold(stampFile.name); keepCacheHold(stampFile.name)   // a swept stamp would only cost a recopy, but keep it
            val size = refs.size(ref) ?: -1L
            val stamp = realPathCopyStamp(size, refs.lastModified(ref))
            val stored = runCatching { stampFile.takeIf { it.isFile }?.readText() }.getOrNull()
            if (reuseRealPathCopy(target.isFile, target.length(), size, stored, stamp)) {
                RomCache.touch(stampFile)
                return target.also { RomCache.touch(it) }
            }
            // The .part is its own top-level name to the sweep: held while it grows, so a big ISO's is never trimmed mid-copy.
            RomCache.hold(part.name)
            try {
                val input = refs.openInput(ref) ?: throw IOException("Cannot open input stream")
                input.use { source -> part.outputStream().use { source.copyTo(it, 1 shl 20) } }
                stampFile.delete()   // describes the copy being replaced; a kill before the new stamp only costs a recopy
                if (!part.renameTo(target)) throw IOException("Cannot move ${part.name} into place")
                // An unknown size never matches (reuseRealPathCopy), so its stamp is not worth writing.
                if (size >= 0) runCatching { stampFile.writeAtomically(stamp.encodeToByteArray()) }
                    .onFailure { NxLog.w("GameActivity", "could not stamp the cache copy of $fileName", it) }
            } finally {
                part.delete()
                RomCache.release(part.name)
            }
            return target
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        padLayout.applyLayout(newConfig.orientation)   // also re-sizes the menu and the notifications to the game area
        // Split keeps its own fixed proportion.
        if (isN3ds && DualScreen.n3dsCoreOptions(dual.dualMode) == null) {
            applyOptions(listOf(Variable(N3dsOptions.STACKED_SMALL_PROPORTION, N3dsOptions.stackedSmallProportion(isLandscape(newConfig.orientation)))))
        }
        // The motion level is captured in the display's frame: a new rotation takes the phone's attitude as level again.
        if (isWii) displayRotation().let { r -> if (r != lastRotation) { lastRotation = r; motionSensors?.recenter() } }
        if (retroView != null) {
            // The overlay set follows the orientation (768p portrait, 720p landscape); the size change re-lays out.
            renderer.updateOverlay(FrontendSettings.parse(frontendValues))
            findViewById<FrameLayout>(R.id.gameContainer).post { renderer.relayoutFrontend() }
        }
    }

    /**
     * The one landscape test for the pad layout ([applyLayout]) and the DS layout ([ndsLayout], its hotkeys): anything not
     * portrait, so the landscape pad clusters and the DS landscape layout (with its pad-over-game touch rule) always agree.
     */
    override fun isLandscape(orientation: Int): Boolean =
        orientation != Configuration.ORIENTATION_PORTRAIT

    override fun isLandscape(): Boolean = isLandscape(resources.configuration.orientation)

    /**
     * 3DS: the core's layout from the row in use (orientation, controller); with the clusters over the game, a finger
     * touching the bottom screen only while it is the main one (the core's rule); and the cursor kept shown in stylus mode.
     * A value the core has is not sent again.
     */
    override fun syncN3dsCore() {
        if (!isN3ds) return
        applyOptions(listOf(
            Variable(N3dsOptions.LAYOUT, N3dsOptions.layoutFor(frontendValues, isLandscape(), renderer.ndsControllerMode())),
            // Under the main display's panel the pad is hidden, so it covers nothing on the picture screen.
            Variable(N3dsOptions.TOUCH_MAIN_ONLY, DualScreen.n3dsTouchMainOnly(dual.dualMode, padLayout.overlayPadShown.value && !dual.mainPanelShown())),
            Variable(N3dsOptions.CURSOR_VISIBLE, N3dsOptions.cursorVisible(input.n3dsHotkeys.stylus)),
        ).withN3dsSplit(DualScreen.n3dsCoreOptions(dual.dualMode)))
    }

    /** These options with a second-screen [split]'s fixed ones in place of the same keys; unchanged without one. */
    private fun List<Variable>.withN3dsSplit(split: Map<String, String>?): List<Variable> =
        if (split == null) this else filterNot { it.key in split } + split.map { (k, v) -> Variable(k, v) }

    /** The tag the layout goes by: the intent's until startEmulator sets [gameTag] (onCreate lays out before it). */
    override fun layoutTag(): String = intent.getStringExtra(EXTRA_TAG) ?: gameTag

    // ------------------------------------------------------------------ frontend (display) settings

    /** Applies frontend [values] (every frontend key) to the view: scaling, sharpness, overlay, offsets, HUD. */
    override fun applyFrontend(values: Map<String, String>) {
        val oldSegaSetting = frontendValues[PadOptions.SEGA_PAD] ?: SegaPadType.AUTO
        val oldBottomScreen = frontendValues[BottomScreenOptions.KEY]
        frontendValues = values
        // Console Settings → Bottom screen (the menu, live): the picture may move. Posted, as for the Sega pad.
        if (values[BottomScreenOptions.KEY] != oldBottomScreen) findViewById<View?>(R.id.root)?.post { dual.recheckPictureOn() }
        val segaSetting = values[PadOptions.SEGA_PAD] ?: SegaPadType.AUTO
        if (segaSetting != oldSegaSetting && PadMap.isSegaPadTag(gameTag)) applySegaPad(segaSetting)
        if (isN3ds) {
            applyN3dsNewControls(values[N3dsOptions.NEW_CONTROLS])
            syncN3dsCore()   // a layout row changed in the menu, or the hotkey's
        }
        if (isWii && wiiPad != null) applyWii(values)   // before the launch resolves it, the launch reads the values itself
        // Portrait Pad changed (the menu, the corner icon, or the launch's values after onCreate's layout). Posted: the
        // icon's own view is still dispatching its long press.
        if (padLayout.portraitPadHidden() != padLayout.layoutPadHidden) findViewById<FrameLayout>(R.id.root).post { padLayout.applyLayout(resources.configuration.orientation) }
        val s = FrontendSettings.parse(values)
        padLayout.padLandscapeAlpha.value = s.padLandscapeAlpha
        val view = retroView ?: return
        view.scaleMode = when (s.scaling) {
            ScreenScaling.NATIVE -> ScaleMode.INTEGER
            ScreenScaling.ASPECT -> ScaleMode.FIT
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
        // Posted: the pad view whose press made the change is still dispatching it, and the menu router arms that
        // press's repeat only after this returns. The rebuilt pad never sends the press's release, so the repeat is
        // stopped here, or it would keep cycling the row.
        findViewById<FrameLayout>(R.id.root).post {
            if (menuOpen) menu.menuRouter.releaseAll()
            padLayout.applyLayout(resources.configuration.orientation)
        }
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
        // Posted, as for the Sega pad (applySegaPad): the press that made the change is still being dispatched.
        findViewById<FrameLayout>(R.id.root).post {
            if (menuOpen) menu.menuRouter.releaseAll()
            padLayout.applyLayout(resources.configuration.orientation)
        }
    }

    /**
     * A Wii Controller or Motion Controls change (menu row, Restore Defaults, the pad's motion button): resolved again,
     * the pad rebuilt, and the core given the new device on every port now (Dolphin rebuilds a port's mapping live, and
     * re-asks the runner for sensors before it does; cores/patches/dolphin.patch).
     */
    private fun applyWii(values: Map<String, String>) {
        val pad = WiiController.resolve(values[WiiOptions.CONTROLLER], wiiControls)
        val motion = WiiMotion.resolve(values[WiiOptions.MOTION], pad, wiiControls, motionSensors?.hasSensors == true)
        if (pad == wiiPad && motion == wiiMotionOn) return
        if (pad != wiiPad) {
            // Only a pad change lets go of everything: a motion toggle (the pad's MOTION, tapped with one thumb) must
            // not drop the stick the other thumb holds.
            if (!menuOpen) input.releaseAllGameInput()
            // A stick held through the change would keep the pointer drifting (a Classic Controller never stops it), and
            // a right stick left tilted under Classic would stay stuck as the Nunchuk's tilt, which the right stick does
            // not reach (Dolphin binds it to tilt under IR mode 2): both are zeroed on every port.
            retroView?.let { v ->
                for (p in 0 until maxOf(playerCount, 1)) {
                    v.setPointerVelocity(p, 0f, 0f)
                    v.sendMotionEvent(GLRetroView.MOTION_SOURCE_ANALOG_RIGHT, 0f, 0f, p)
                }
            }
        }
        wiiPad = pad
        wiiMotionOn = motion
        LibretroDroid.setSensorEnabled(0, motion)
        dropPadProfile()
        // Before the first frame the core may not be loaded: the first-frame block sends the device then, whatever it is.
        if (gameReady) { sendWiiDevice(); syncMotionSensors() }
        // Posted, as for the Sega pad (applySegaPad): the press that made the change is still being dispatched.
        findViewById<FrameLayout>(R.id.root).post {
            if (menuOpen) menu.menuRouter.releaseAll()
            padLayout.applyLayout(resources.configuration.orientation)
        }
    }

    /** The Dolphin device for [pad]: MotionPlus added when the game takes it ([WiiController.motionPlus]). */
    private fun wiiDevice(pad: WiiPad): Int = WiiController.device(pad, WiiController.motionPlus(pad, wiiControls))

    /** Dolphin's device for its connected ports: the resolved Wii controller, or the descriptor's plain pad (GameCube). */
    private fun dolphinDevice(): Int =
        wiiPad?.let(::wiiDevice) ?: CoreDescriptor.JOYPAD

    /** The ports a pad holds in [slots] (live or held for it): with port 0, the ones Dolphin has connected ([dolphinPorts]). */
    private fun claimedPorts(): Set<Int> =
        input.slots?.slots().orEmpty().withIndex().filter { it.value != SlotView.Empty }.map { it.index }.toSet()

    /**
     * Sends the device to port 0 and every claimed port on the emulation thread. Never plugs in an unclaimed port, and a
     * pad that unplugs keeps its port (its slot is held), so the game is not asked to reconnect a remote.
     */
    private fun sendWiiDevice() {
        if (!isDolphin) return
        val view = retroView ?: return
        val ports = dolphinPorts(dolphinDevice(), claimedPorts(), playerCount).filterValues { it != CoreDescriptor.NONE }
        view.queueEvent {
            for ((p, device) in ports) {
                runCatching { view.setControllerType(p, device) }.onFailure { NxLog.w(TAG_LOG, "setting the Dolphin controller failed", it) }
            }
        }
    }

    /** A pad took player slot N on Dolphin: that port is connected now (before this, it was unplugged). */
    override fun connectDolphinPorts() {
        if (isDolphin && gameReady) sendWiiDevice()
    }

    /**
     * The sensors listen only while motion is on and the game runs (not paused, menu closed, in front). [foreground]:
     * onResume and onPause pass it, as the lifecycle state there does not say yet which side of the pause this is.
     */
    private fun syncMotionSensors(foreground: Boolean = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
        val sensors = motionSensors ?: return
        val n3ds = isN3ds && sensors.hasAccel
        val run = ((isWii && wiiMotionOn) || n3ds) && gameReady && !menuOpen && !quitting && foreground
        when {
            !run -> sensors.stop()
            n3ds -> sensors.startN3ds(::displayRotation)
            else -> sensors.start(::displayRotation, { wiiPad ?: WiiPad.SIDEWAYS })
        }
    }

    /**
     * 3DS motion: the phone's sensors offered to the core. Before the load, as Azahar asks for them once, when its HID
     * service builds the motion device (citra_libretro/input/input_factory.cpp), each sensor on its own: without a gyroscope
     * the tilt still comes through and the gyro reads still; without an accelerometer the console reads as a 3DS at rest.
     */
    private fun offerN3dsMotion() {
        val sensors = motionSensors ?: MotionSensors(this) { a, g ->
            LibretroDroid.setSensorValues(0, a.x, a.y, a.z, g.x, g.y, g.z)
        }.also { motionSensors = it }
        LibretroDroid.setSensorAvailable(sensors.hasAccel, sensors.hasGyro)
        LibretroDroid.setSensorEnabled(0, sensors.hasAccel)
    }

    /** The display's rotation (Surface.ROTATION_*), which [MotionSensors] turns the phone's axes by. */
    @Suppress("DEPRECATION")
    private fun displayRotation(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) display?.rotation ?: 0 else windowManager.defaultDisplay.rotation

    /** The pad's motion button: tap toggles Motion Controls (saved for this game), long press recentres. */
    private fun toggleWiiMotion() {
        if (WiiOptions.MOTION in lockedOptions) return   // locked in the .cfg: left alone, as the hotkeys do (setNdsKey)
        val next = if (wiiMotionOn) WiiMotion.OFF else WiiMotion.ON
        applyFrontend(frontendValues + (WiiOptions.MOTION to next))
        persistGameKey(WiiOptions.MOTION, next, "Could not save Motion Controls")
    }

    /** Takes the phone's attitude at the next reading as the Wiimote's level. */
    override fun recenterWiiMotion() { motionSensors?.recenter() }

    /**
     * Console Settings → Wii Screen for a Wii game: Dolphin's widescreen system setting, chosen from the orientation the
     * game starts in (games read it once at boot), and the touch pointer's geometry for that shape. Empty for any other
     * console.
     */
    private fun wiiScreenOption(): Map<String, String> {
        if (!WiiOptions.appliesTo(gameTag) || wiiGameCube) return emptyMap()
        val portrait = resources.configuration.orientation != Configuration.ORIENTATION_LANDSCAPE
        val wide = WiiScreen.widescreen(frontendValues[WiiOptions.SCREEN], portrait)
        val pointer = WiiScreen.pointerOptions(
            wide, frontendValues[WiiOptions.POINTER_WIDTH], frontendValues[WiiOptions.POINTER_HEIGHT], frontendValues[WiiOptions.POINTER_OFFSET],
        )
        return mapOf(WiiScreen.CORE_KEY to WiiScreen.coreValue(wide)) + pointer
    }

    /**
     * The Wii's HOME (the pad's, or a controller's right-stick click): saves the resume point, then taps the core's Home
     * (R3). Choosing Wii Menu in the game's HOME menu closes the game ([GLRetroView.getShutdownRequests]) with no state of
     * its own, since the game has stopped itself by then: resuming lands here, just before the HOME menu opened. A press
     * while one is still being saved is dropped.
     */
    override fun pressWiiHome() {
        if (wiiHomeSaving) return
        val view = retroView ?: return
        val ss = saveStates
        if (ss == null || !gameReady || resumePending) { tapWiiHome(view); return }
        wiiHomeSaving = true
        captureThumbnail { bmp ->
            lifecycleScope.launch(Dispatchers.IO) {
                runCatching {
                    val bytes = view.serializeState()
                    check(bytes.isNotEmpty()) { "core returned no state" }
                    // On [autoWriter], so it lands in order with an on-stop write of the same files.
                    autoWriter.submit {
                        if (bmp != null) ss.writeAutoThumb(bmp)
                        ss.writeAuto(bytes)
                        ss.markAuto(onlyIfNotManual = false)
                        lastResumePoint = NxPaths.AUTO_SLOT
                    }.get()
                    wiiHomeSaved = true
                }.onFailure { NxLog.w("GameActivity", "resume point before Wii HOME failed", it) }
                withContext(Dispatchers.Main) { wiiHomeSaving = false; if (!quitting) tapWiiHome(view) }
            }.also { trackSlotJob(it) }
        }
    }

    private fun tapWiiHome(view: GLRetroView) {
        view.sendKeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BUTTON_THUMBR, 0)
        lifecycleScope.launch { delay(WII_HOME_PRESS_MS); view.sendKeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BUTTON_THUMBR, 0) }
    }

    /** The pad's motion button ([PadMotion]): Wiimote types on a phone with both sensors; none without them. */
    override fun padMotion(): PadMotion? =
        if (isWii && padProfile().motionButton && motionSensors?.hasSensors == true) PadMotion(wiiMotionOn, ::toggleWiiMotion, ::recenterWiiMotion) else null

    /**
     * The keys the menu leaves out: the .cfg's locks, Motion Controls on a Wii game without both sensors, and both Wii
     * rows for a GameCube disc under the WII tag.
     */
    override fun menuLocked(): Set<String> = when {
        wiiGameCube -> lockedOptions + WiiOptions.table.map { it.key }
        isWii && motionSensors?.hasSensors != true -> lockedOptions + WiiOptions.MOTION
        else -> lockedOptions
    }

    /** The open menu's Wii rows: Motion Controls, once the sensors are known. */
    private fun refreshWiiMenu(model: InGameMenuModel) {
        model.setFrontend(frontendValues, renderer.overlayNames(), menuLocked(), gameTag, overlayAvailable = !renderer.ndsSplit)
    }

    /** Sends Controller Type [setting] to the running core on the emulation thread (each player's port device or core option). */
    private fun sendSegaPad(setting: String) {
        if (netplayNow != null) return   // netplay: the pad type is fixed for the session (startEmulator)
        val view = retroView
        val changes = SegaPadType.liveChanges(gameCoreId, setting, segaHeader, playerCount)
        val ports = changes.filterIsInstance<SegaPadChange.Port>()
        if (ports.isNotEmpty()) view?.queueEvent {
            ports.forEach { c ->
                runCatching { view.setControllerType(c.port, c.device) }
                    .onFailure { NxLog.w(TAG_LOG, "setting the Sega pad failed", it) }
            }
        }
        changes.filterIsInstance<SegaPadChange.Option>().takeIf { it.isNotEmpty() }
            ?.let { opts -> applyOptions(opts.map { Variable(it.key, it.value) }) }
    }

    override fun relayoutFrontend() = renderer.relayoutFrontend()

    override fun persistConsoleKey(key: String, value: String, failure: String) {
        val store = configStore ?: return
        lifecycleScope.launch {
            val written = configWrites.withLock {
                withContext(Dispatchers.IO) {
                    runCatching { store.saveConsoleKey(key, value) }
                        .onFailure { NxLog.w("GameActivity", "saving $key failed", it) }.getOrNull()
                }
            }
            if (written == null) { toast(failure); return@launch }
            if (configSource == ConfigSource.NONE) configSource = ConfigSource.CONSOLE
            lockedOptions = written.locked + forcedOptions
        }
    }

    override fun persistGameKey(key: String, value: String, failure: String) {
        val store = configStore ?: return
        lifecycleScope.launch {
            val written = configWrites.withLock {
                withContext(Dispatchers.IO) {
                    runCatching { store.saveGameKey(key, value) }
                        .onFailure { NxLog.w("GameActivity", "saving $key failed", it) }.getOrNull()
                }
            }
            if (written == null) { toast(failure); return@launch }
            configSource = ConfigSource.GAME
            lockedOptions = written.locked + forcedOptions
        }
    }

    // ------------------------------------------------------------------ Nintendo 3DS controls

    /**
     * The 3DS title from the start of [s] ([N3dsTitle.parse]): the first header, and for a cart image (NCSD) on to its
     * partition 0's NCCH header (0x4000 on dumped carts), a few KB in all.
     */
    private fun readN3dsTitle(s: java.io.InputStream): N3dsTitle? {
        fun readFully(buf: ByteArray, from: Int): Int {
            var n = from
            while (n < buf.size) { val r = s.read(buf, n, buf.size - n); if (r < 0) break; n += r }
            return n
        }
        val head = ByteArray(N3dsTitle.HEADER)
        if (readFully(head, 0) < head.size) return null
        val offset = N3dsTitle.ncchOffset(head) ?: return N3dsTitle.parse(head)
        val all = head.copyOf(offset.toInt() + N3dsTitle.HEADER)
        return N3dsTitle.parse(all.copyOf(readFully(all, head.size)))
    }

    /**
     * Sets core options on the emulation (GL) thread, where the core reads them; queued, so a later
     * [withCoreOptions] read sees them.
     */
    override fun applyOptions(vars: List<Variable>) {
        if (netplayNow != null) return   // netplay: no core option changes mid-session (the other device would not follow)
        val view = retroView ?: return
        if (vars.isEmpty()) return
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
        menu.menuModel?.let { it.setCheats(cheatSet.entries, cheatsFolder, cheatSet.expected); menu.menuState.value = it.state }
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
     * minarch's Menu_autosaveQuit: screenshot + slot 9 + point the slot file at it, then leave. The battery save is
     * written once, off the main thread, before finish() (so the write lands before the native destroy); on a handoff
     * this runs inside the new screen's onCreate, which it never blocks (the new game waits for this screen's release).
     * The core is read first (state, then SRAM, back to back under [coreLock]), and only then comes the wait (a queued
     * Home write), so a destroy during the wait never finds the serialize still ahead of it. [chosen]: the player picked
     * Quit, so the slot file points at the auto state as minarch does; a quit nobody chose (a newer game screen took the
     * session) leaves the player's manual slot pointer alone, as leaving via Home does ([writeAutoStateOnStop]).
     */
    private fun quitWithAutoState(chosen: Boolean, autoState: Boolean = true) {
        if (quitting) return   // single-shot: a second job could serialize after the core is destroyed
        quitting = true
        rumbles.forEach { it.release() }
        endGameTime()
        val view = retroView
        val ss = saveStates
        input.gcClearTriggers()   // a latched soft press never outlives the game
        stopAchievements()
        if (view == null || !gameReady) { finish(); return }   // not running yet: no save to write
        // While a resume is still loading, the running state is the fresh boot: keep the resume point as is. Without
        // [autoState] (the core asked to end) the running state is not worth resuming: keep it as is too.
        if (ss == null || resumePending || !autoState) {
            lifecycleScope.launch {
                withContext(Dispatchers.IO) {
                    withLiveCore { writeBatterySave(notify = true) }
                }
                finish()
            }
            return
        }
        captureThumbnail { bmp ->
            lifecycleScope.launch {
                // A manual Save/Load still running must finish before the state is serialized.
                slotJobs.toList().forEach { it.join() }
                // true = written, null = skipped (the core can't save states), false = failed.
                val ok: Boolean? = withContext(Dispatchers.IO) {
                    // Both reads of the core before any wait; null = the core was already destroyed (nothing to write).
                    val bytes = withLiveCore {
                        // On the GL thread (a core may draw inside it: Azahar), waited for under the lock: the GL
                        // thread never takes coreLock, so the destroy observer just waits for it.
                        val state = runCatching { view.serializeState() }
                            .onFailure { NxLog.e("GameActivity", "auto-resume state failed", it) }
                        writeBatterySave(notify = true)   // local: always completes, before the native destroy
                        state
                    }
                    val ok = when {
                        bytes == null || bytes.isFailure -> false
                        bytes.getOrThrow().isEmpty() -> null
                        else -> runCatching {
                            // A queued Home write must land first: it writes the same files and is older.
                            awaitAutoWrites()
                            if (bmp != null) ss.writeAutoThumb(bmp)
                            ss.writeAuto(bytes.getOrThrow())
                            ss.markAuto(onlyIfNotManual = !chosen)
                            true
                        }.onFailure { NxLog.e("GameActivity", "auto-resume state failed", it) }.getOrDefault(false)
                    }
                    ok
                }
                if (ok == false) toast("Could not save resume point")
                finish()
            }
        }
    }

    /**
     * Runs [block] (off-main native calls) unless the core is destroyed; null when it is. Holding [coreLock] keeps the
     * destroy (see its observer in startEmulator) from starting under the block. Not for the main thread's own calls:
     * they cannot overlap onDestroy.
     */
    private fun <T> withLiveCore(block: () -> T): T? = synchronized(coreLock) { if (coreGone) null else block() }

    /**
     * Re-reads the slot strip (and the last-used slot) on IO and publishes it to [slotsUi]; main thread. The listing and
     * the thumbnails' bounds stay off the main thread: the previous strip shows until this lands (it differs only after
     * a write, which refreshes it too), and a newer refresh cancels an older one so a stale read never lands last.
     */
    override fun refreshSlots() {
        val ss = saveStates ?: run { slotsUi.value = emptyList(); return }
        slotsJob?.cancel()
        slotsJob = lifecycleScope.launch {
            val (slots, last) = withContext(Dispatchers.IO) {
                runCatching { readSlots(ss) to ss.lastSlot() }
                    .onFailure { NxLog.w("GameActivity", "could not list the slots", it) }.getOrNull()
            } ?: return@launch
            slotsUi.value = slots
            lastSlotCache = last
        }
    }

    /** The slot strip for [ss]: file listings and thumbnail bounds. Blocking I/O. */
    private fun readSlots(ss: SaveStates): List<SlotUi> = ss.slots().map { slotUi(it, "Slot ${it.index}") }

    /** One slot as the strip shows it; the thumbnail's URI carries its mtime, so a new save reloads it. Blocking I/O. */
    private fun slotUi(s: SaveStates.Slot, label: String): SlotUi {
        val sub = if (s.occupied) formatSlotTime(s.timestampMillis) else "empty"
        val thumb = s.thumb?.let { Uri.fromFile(it).buildUpon().fragment(it.lastModified().toString()).build().toString() }
        return SlotUi(
            label = label,
            subtitle = sub,
            thumbPath = thumb,
            occupied = s.occupied,
            aspect = s.thumb?.let { thumbAspect(it) } ?: (4f / 3f),
        )
    }

    /** Reads a thumbnail's width/height (bounds only) to get its aspect, so the preview frame hugs it. */
    private fun thumbAspect(file: File): Float {
        val opts = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        android.graphics.BitmapFactory.decodeFile(file.absolutePath, opts)
        return if (opts.outWidth > 0 && opts.outHeight > 0) opts.outWidth.toFloat() / opts.outHeight else 4f / 3f
    }

    private fun formatSlotTime(ms: Long): String =
        java.text.SimpleDateFormat("MMM d, HH:mm", java.util.Locale.US).format(java.util.Date(ms))

    /**
     * Refuses a manual Save / Load ([saveToSlot], [loadFromSlot]) before the first frame or while the Resume load is still
     * to land, as pressWiiHome and swapDisc do: a Save would capture the fresh boot over the user's slot (and move the
     * pointer to it), and a Load would be overwritten by the resume's unserialize while the pointer says the loaded slot.
     */
    private fun slotsBusy(): Boolean {
        if (gameReady && !resumePending) return false
        toast("Still loading")
        return true
    }

    override fun saveToSlot(slot: Int) {
        val view = retroView ?: return
        val ss = saveStates ?: return
        if (slotsBusy()) return
        captureThumbnail { bmp ->
            trackSlotJob(lifecycleScope.launch(Dispatchers.IO) {
                val ok = runCatching {
                    // Empty = the core refused (or is gone): keep the slot's old state and report the failure.
                    val bytes = view.serializeState()
                    check(bytes.isNotEmpty()) { "core returned no state" }
                    ss.writeState(slot, bytes)
                    if (bmp != null) ss.writeThumb(slot, bmp)
                }.isSuccess
                withContext(Dispatchers.Main) {
                    if (ok) { lastSlotCache = slot; refreshSlots(); toast("Saved to slot $slot") } else toast("Save failed")
                }
            })
        }
    }

    /** Loads manual [slot] and records it as the last used one; the menu closes on success. */
    override fun loadFromSlot(slot: Int) {
        val view = retroView ?: return
        val ss = saveStates ?: return
        if (slotsBusy()) return
        // The read is in the job too, not on main: a state file is up to tens of MB (PSP and DC).
        trackSlotJob(lifecycleScope.launch(Dispatchers.IO) {
            val read = runCatching { ss.readState(slot) }
                .onFailure { NxLog.e("GameActivity", "could not read slot $slot", it) }
            if (read.getOrNull() == null && read.isSuccess) return@launch   // empty slot: ignore (the menu stays open)
            val ok = read.getOrNull()?.let { bytes -> runCatching { view.unserializeState(bytes) }.getOrDefault(false) } ?: false
            if (ok) runCatching { saveStates?.rememberSlot(slot) }
                .onFailure { NxLog.e("GameActivity", "could not record last slot", it) }
            // The loaded state's rumble may stay latched unreported: start from still rather than re-apply the old one.
            withContext(Dispatchers.Main) {
                if (ok) {
                    rumbles.forEach { it.forget() }
                    lastSlotCache = slot   // the pointer moved: a save or load right after follows it before the refresh lands
                    refreshSlots()
                    menu.closeMenu()
                } else toast("Load failed")
            }
        })
    }

    /** Main thread only (menu handlers and the main-looper PixelCopy callback). */
    private fun trackSlotJob(job: Job) {
        slotJobs.removeAll { !it.isActive }
        slotJobs += job
    }

    /** PixelCopy the current (paused) game frame for a screenshot at the view's native pixel size (no scaling). */
    private fun captureThumbnail(cb: (Bitmap?) -> Unit) {
        val view = retroView ?: return cb(null)
        val w = view.width
        val h = view.height
        if (w <= 0 || h <= 0) return cb(null)
        val full = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        try {
            PixelCopy.request(view, full, { result ->
                if (result == PixelCopy.SUCCESS) {
                    cb(full)
                } else {
                    full.recycle()
                    cb(null)
                }
            }, Handler(Looper.getMainLooper()))
        } catch (e: Exception) {
            full.recycle()
            cb(null)
        }
    }

    /**
     * Persists the battery save locally. Safe from any thread: the native side locks the core, and writes are
     * serialized on the [BatterySaves] instance so they land in call order. [notify] toasts a failure. Returns true
     * when the file changed (unchanged SRAM is not rewritten). Saves live in private storage only. The clock file
     * ([clockSave]) is written with it; either changing returns true.
     */
    private fun writeBatterySave(notify: Boolean): Boolean {
        val view = retroView ?: return false
        val battery = batterySaves ?: return false
        if (!gameReady) return false
        // netplay: only a host on its folder's own core, with its saves served, writes its real saves; a copy never.
        netplay?.let { if (it.onCopy) return false }
        return runCatching {
            synchronized(battery) {
                val wrote = battery.write(view.serializeSRAM(useEmulationThread = false))
                // The clock file alongside (minarch writes both together); its failure is logged, never toasted.
                val clockWrote = clockSave?.let { c ->
                    runCatching { c.write(view.serializeRTC(useEmulationThread = false)) }
                        .onFailure { NxLog.e("GameActivity", "clock save failed", it) }.getOrDefault(false)
                } ?: false
                wrote || clockWrote
            }
        }
            .onFailure {
                NxLog.e("GameActivity", "battery save failed", it)
                if (notify) runOnUiThread { toast("Battery save failed") }
            }.getOrDefault(false)
    }

    // ------------------------------------------------------------------ netplay

    /**
     * netplay: the saves a session plays on, before the battery load. A host on its folder's own core keeps its
     * real saves but loses the game's auto-resume state (handheld 19d3a5e1), so a later resume cannot roll back battery
     * saves written in the session; manual slots stay. A host on another core plays on a fresh copy of its own battery
     * saves in the session folder; one whose saves were not served (emptySaves) on an empty copy. A client plays on the
     * host's saves, committed there before launch (an empty folder when the host had none).
     */
    private suspend fun prepareNetplaySaves(np: NetplaySession, tag: String, romFileName: String) {
        withContext(Dispatchers.IO) {
            runCatching {
                when {
                    !np.onCopy -> {
                        awaitAutoWrites()   // a Home write of this game's auto state may still be landing
                        val marker = File(dataRoot, NxPaths(tag, np.folderCoreId, romFileName).lastSlot).takeIf { it.exists() }?.readText()
                        NetplaySaves.resumeVictims(tag, np.folderCoreId, romFileName, marker, paths.naming, paths.innerFileName) { File(dataRoot, it).exists() }
                            .forEach { File(dataRoot, it).delete() }
                        lastSlotCache = null
                    }
                    // A host on another core plays on a copy of its own saves; one whose saves were not served on an empty
                    // copy, as the joiner does. Under the session folder's lock (prepareHost).
                    np.role == NetplayRole.HOST -> {
                        val savesDir = if (np.emptySaves) null else File(dataRoot, paths.savesDir)
                        val copied = NetplaySessionDir.prepareHost(filesDir, netplayOwner, savesDir, paths) {
                            NxLog.e(TAG_LOG, "netplay: copying the host's own saves failed", it)
                        }
                        if (!copied) netplayOwnCopyFailed = true
                    }
                    // The host's copy committed before launch (kept), or an empty one; a copy an older screen claimed is stale.
                    else -> NetplaySessionDir.claim(filesDir, netplayOwner)
                }
            }.onFailure {
                NxLog.e(TAG_LOG, "netplay saves setup failed", it)
                // A host on a copy whose claim itself failed plays without its saves too: say so (first frame).
                if (np.role == NetplayRole.HOST && np.onCopy) netplayOwnCopyFailed = true
            }
        }
    }

    /**
     * netplay: starts this screen's session once the first frame is up ([netplayGame] set first, so onDestroy shuts down
     * what started). A GBA link's engine started before the load ([connectLink]); its status ticks from now. GB link has
     * no engine: gambatte runs it.
     */
    private suspend fun startNetplay(np: NetplaySession): Boolean = when (NetplayCores.link(np.coreId)) {
        null -> NetplayGame(np, lifecycleScope, SystemClock::elapsedRealtime, ::onNetplayNotice).also { netplayGame = it }.start()
        NetplayLinkKind.NETPACKET -> (netplayGame as? NetplayLinkGame)?.let { it.begin(); it.live } ?: false
        NetplayLinkKind.GBLINK -> true
    }

    /** netplay link: the GBA link engine, started before the core loads; the raw gpsp_serial the core runs with, or null. */
    private suspend fun connectLink(np: NetplaySession, ownSerial: String?): String? =
        NetplayLinkGame(np, lifecycleScope, SystemClock::elapsedRealtime, ::onNetplayNotice).also { netplayGame = it }.connect(ownSerial)

    /** netplay: a status notice from [NetplayGame]'s ticker (main thread). */
    private fun onNetplayNotice(notice: NetplayNotice) {
        val now = SystemClock.elapsedRealtime()
        when (notice) {
            NetplayNotice.Clear -> {
                if (!netplayPillShown) return
                notifications.hideProgress()
                netplayPillShown = false
            }
            is NetplayNotice.Pill -> {
                notifications.progress(notice.text, "", null, NETPLAY_PILL_MS, now)
                netplayPillShown = true
            }
            is NetplayNotice.Ended -> {
                if (netplayPillShown) notifications.hideProgress()
                netplayPillShown = false
                notifications.push(notice.text, null, NETPLAY_NOTICE_MS, now)
                if (netplay?.endsAsLocalGame == true) linkEnded() else {
                    // The ended session holds no frames: an open menu pauses the game as for any other launch.
                    if (menuOpen || focusPaused) retroView?.frameSpeed = 0
                    netplayEnded = true
                    menu.menuModel?.let { it.endNetplay(local = false); menu.menuState.value = it.state }
                }
            }
            is NetplayNotice.Failed -> {
                if (netplayPillShown) notifications.hideProgress()
                netplayPillShown = false
                fail(notice.text, NetplayText.FAILED)
            }
        }
        publishNotifications()
    }

    /**
     * netplay link: Leave. The link ends and the game plays on ([linkEnded]); the menu stays open on the normal root and
     * the player leaves later with Quit. A GBA link's engine sends DISCONNECT and shuts down off main; [NetplayRun.leave]
     * detaches it first, so no Ended notice follows. GB link has no engine: gambatte's network serial stops on its mode's
     * variable update, read at the next frame (the menu holds frames, so on Continue). Set past [applyOptions]' guard and
     * not stored: the link options are this launch's ([graphicsForcedOptions]), never the user's config.
     */
    private fun leaveLink() {
        netplayGame?.leave()
        if (netplayPillShown) { notifications.hideProgress(); netplayPillShown = false; publishNotifications() }   // the ticker is gone
        if (netplay?.let { NetplayCores.link(it.coreId) } == NetplayLinkKind.GBLINK) retroView?.let { view ->
            val off = Variable(NetplayLinkOptions.GB_MODE, NetplayLinkOptions.GB_OFF)
            view.queueEvent { runCatching { view.updateVariables(off) }.onFailure { NxLog.w("GameActivity", "ending the GB link failed", it) } }
        }
        linkEnded()
    }

    /**
     * netplay link: the session is over, from the other side or the network ([NetplayNotice.Ended]) or by Leave
     * ([leaveLink]): the game is a local one from here ([playLocally]); an open menu stays open, paused, and becomes the
     * normal root on the slot a normal launch shows.
     */
    private fun linkEnded() {
        // The link holds no frames now: an open menu pauses the game as for any other launch.
        if (menuOpen || focusPaused) retroView?.frameSpeed = 0
        netplayEnded = true
        playLocally()
        menu.menuModel?.let {
            it.cheatsAvailable = cheatsSupported
            lastSlotCache?.let { slot -> it.showSlot(slot - 1) }
            it.endNetplay(local = true)
            menu.menuState.value = it.state
        }
    }

    /**
     * netplay link: the session ended (the other player left, it dropped, or this player left). Each player ran their own game on their own
     * save (never a copy: [NetplaySession.onCopy]), so from here it is the normal launch of that game: save states, the
     * auto state on Quit and on stop, cheats (loaded now, the saved ones applied), Options and the panel, as [netplayNow]
     * is null. The link engine is idle already (or shutting down after Leave); [netplay] stays for onDestroy (the engine release, the USB cable).
     * Achievements need nothing here: a link session ran them from launch ([NetplaySession.allowsAchievements]).
     */
    private fun playLocally() {
        if (netplayLocal) return
        netplayLocal = true
        saveStates = SaveStates(dataRoot, paths).also { ss ->
            // Read now, as openMenu does for a menu opened before the first read: an open menu shows this slot.
            lastSlotCache = ss.lastSlot()
        }
        refreshSlots()
        localCheats?.let { load ->
            localCheats = null
            val job = load().also { cheatsLoad = it }
            lifecycleScope.launch {
                job.join()
                if (!quitting && cheatSet.hasEnabled()) retroView?.let(::applyCheats)
            }
        }
        dual.refreshDiscs()
        dual.refreshDualPanel()
    }

    // ------------------------------------------------------------------ achievements

    /**
     * Before the view exists: an RA console, signed in and enabled, gets a bridge and a background hash. An archive's
     * unpacked [inner] ROM is what is hashed, and its name refines the console.
     */
    private fun prepareAchievements(tag: String, romFileName: String, content: ContentRef, inner: File?) {
        val settings = AndroidAppSettings(this)
        if (!settings.raEnabled || settings.raToken == null) return
        val consoleId = RaConsoles.consoleId(tag, inner?.name ?: romFileName) ?: return
        val controller = AchievementsService.controller(this, settings, AndroidLibraryIndexStore(this))
        val hasher = AndroidRaHasher(this, AndroidRomsSources.of(this))
        val folder = intent.getStringExtra(EXTRA_FOLDER_URI) ?: ""
        val pointer = intent.getStringExtra(EXTRA_GAME_PATH)?.let(GamePath::split)?.let { ref ->
            RaGamePointer(tag, ref.sourceId, ref.relativeDir, ref.fileName, intent.getStringExtra(EXTRA_TITLE) ?: ref.fileName)
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
    }

    /** Idempotent; onDestroy, a finishing onStop and the quit path all reach it. */
    private fun stopAchievements() {
        achievements?.stop()
        achievements = null
    }

    /** The Options row and Achievements page: hidden without a session, "Not recognised" while there is no set. */
    override fun applyAchievements(model: InGameMenuModel) {
        val b = achievements?.takeIf { it.available } ?: return model.setAchievementsAvailable(false)
        val summary = b.summary()
        model.setAchievements(if (summary != null) b.list() else null, summary, AndroidAppSettings(this).raSortOrder, b.muted, b.badges)
    }

    private fun refreshMenuAchievements() {
        menu.menuModel?.let { applyAchievements(it); menu.menuState.value = it.state }
        dual.refreshDualPanel()
    }

    override fun toggleMute(id: Int) {
        val b = achievements ?: return
        lifecycleScope.launch {
            runCatching { b.toggleMute(id) }.onFailure { NxLog.w("GameActivity", "mute toggle failed", it) }
            refreshMenuAchievements()
        }
    }

    private fun onAchievementEvent(e: RaEvent) {
        val b = achievements ?: return
        val settings = AndroidAppSettings(this)
        val now = SystemClock.elapsedRealtime()
        val toastMs = settings.raNotificationSeconds * 1000L
        fun badge(a: RaAchievement) = RaBadgeFolders.fileName(a.badge, locked = false)?.let(b.badges::get)
        fun notice(text: String) { notifications.push(text, null, toastMs, now) }
        // Off: only the session notices (loaded, unknown, sign-in, offline, first-time setup, synced) still show.
        val shown = settings.raShowNotifications
        when (e) {
            is RaEvent.Unlocked -> if (shown) notifications.push("Achievement Unlocked\n${e.a.title}", badge(e.a), toastMs, now)
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
                    // Over the landscape overlay pad, top-centre (its system row holds the top corners, its d-pad
                    // the bottom-left); elsewhere the corners.
                    val placement = nxNotificationPlacement(padLayout.overlayPadShown.value)
                    // Between the clusters' system rows: the same screen, cutout inset and unit the clusters are laid out with.
                    val density = LocalDensity.current
                    val dir = LocalLayoutDirection.current
                    val cutout = WindowInsets.displayCutout
                    val inset = with(density) {
                        symmetricCutoutInset(cutout.getLeft(density, dir).toFloat(), cutout.getRight(density, dir).toFloat()).toDp().value
                    }
                    // This view's own size: in landscape the root's, which the clusters (MATCH_PARENT tall) fit their unit to.
                    BoxWithConstraints(Modifier.fillMaxSize()) {
                        val maxW = nxTopCentreMaxWidth(maxWidth.value, maxHeight.value, inset).dp
                        NxNotifications(toasts, pill, safeSides = sides, placement = placement, topCentreMaxWidth = maxW)
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

    // The second display's state, owned by [dual]: read by the input, layout and picture code.
    override val presentation: SecondScreenPresentation? get() = dual.presentation
    override val dualMode: DualMode get() = dual.dualMode
    override val pictureOn: PictureOn get() = dual.pictureOn
    override val n3dsPicture: NdsScreen get() = dual.n3dsPicture
    override fun secondDsRegions(g: FloatArray): List<NdsRegion>? = dual.secondDsRegions(g)
    override fun secondRegions(g: FloatArray, ds: List<NdsRegion>?): List<ScreenRegion> = dual.secondRegions(g, ds)
    override fun onN3dsSwapPressed(down: Boolean): Boolean = dual.onN3dsSwapPressed(down)
    override fun mainPanelShown(): Boolean = dual.mainPanelShown()
    override fun placeMainPanelHost() = dual.placeMainPanelHost()

    // The in-game menu's view, input and layout, owned by [menu].
    override val menuView: ComposeView? get() = menu.menuView
    override val menuRouter: InputRouter get() = menu.menuRouter
    override fun menuLayoutParams(orientation: Int, overPanel: Boolean): FrameLayout.LayoutParams = menu.menuLayoutParams(orientation, overPanel)
    override fun toggleMenu() = menu.toggleMenu()
    override fun backPressed() = menu.backPressed()

    /**
     * netplay: DISCONNECT and shut the engine down (off main), then quit; saveStates is null: no auto state. A link
     * session ends the link instead and plays on, the menu open on the normal root.
     */
    override fun quitGame(chosen: Boolean, autoState: Boolean) = quitWithAutoState(chosen, autoState)

    override fun leaveNetplay() {
        if (netplay?.endsAsLocalGame == true) leaveLink() else { netplayGame?.leave(); quitWithAutoState(chosen = true) }
    }

    override fun handleMenuEffect(effect: MenuEffect) = menu.handle(effect)
    override fun publishMenuSecondScreen() { menu.menuModel?.let(menu::publishSecondScreen) }
    override fun withMenu(block: (InGameMenuModel) -> Unit) { menu.menuModel?.let { block(it); menu.menuState.value = it.state } }

    override fun publishMenuShaders() {
        menu.menuModel?.let { it.setShaders(renderer.shaderValues, renderer.presetNames, renderer.shaderParams); menu.menuState.value = it.state }
    }

    override fun publishMenuFrontend() {
        menu.menuModel?.let {
            it.setFrontend(frontendValues, renderer.overlayNames(), menuLocked(), gameTag, overlayAvailable = !renderer.ndsSplit)
            menu.menuState.value = it.state
        }
    }
    override val gameContainer: FrameLayout get() = findViewById(R.id.gameContainer)

    override fun onGenericMotionEvent(event: MotionEvent?): Boolean = input.onGenericMotionEvent(event)
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean = input.onKeyDown(keyCode, event)
    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean = input.onKeyUp(keyCode, event)
    override fun superGenericMotionEvent(event: MotionEvent?): Boolean = super.onGenericMotionEvent(event)
    override fun superKeyDown(keyCode: Int, event: KeyEvent): Boolean = super.onKeyDown(keyCode, event)
    override fun superKeyUp(keyCode: Int, event: KeyEvent): Boolean = super.onKeyUp(keyCode, event)
}

/**
 * What a real-path cache copy was made from ([GameActivity.copyToCache]): the source's size and, when its provider
 * reports one, its last-modified. Without a last-modified it is size only.
 */
internal fun realPathCopyStamp(size: Long, lastModified: Long?): String = "$size:${lastModified ?: "?"}"

/**
 * `<file name><suffix>`, or `<hash of the name><suffix>` when that (plus writeAtomically's `.xxxxxxxx.tmp`) would pass
 * the 255-byte file-name limit: a long ROM name must not fail its copy over a sibling's name.
 */
internal fun cacheSiblingName(fileName: String, suffix: String): String =
    if ((fileName + suffix).encodeToByteArray().size + 14 <= 255) fileName + suffix
    else fileName.hashCode().toUInt().toString(16) + suffix

/** A cache copy is reused only when the source's size is known, the copy has it, and its stored stamp is [stamp]. */
internal fun reuseRealPathCopy(copyExists: Boolean, copyLength: Long, size: Long, storedStamp: String?, stamp: String): Boolean =
    copyExists && size >= 0 && copyLength == size && storedStamp?.trim() == stamp
