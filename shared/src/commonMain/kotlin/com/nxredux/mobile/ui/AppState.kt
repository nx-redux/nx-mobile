package com.nxredux.mobile.ui

import com.nxredux.mobile.library.LocalRomFinder
import com.nxredux.mobile.netplay.cablelink.LinkRomCache
import com.nxredux.mobile.tryOr
import com.nxredux.mobile.tryOrAny
import com.nxredux.mobile.tryOrNull
import com.nxredux.mobile.tryQuietly
import com.nxredux.mobile.kids.KidsController
import com.nxredux.mobile.kids.KidsMode
import com.nxredux.mobile.kids.LocalNow
import com.nxredux.mobile.kids.UnlockChip
import com.nxredux.mobile.shortcuts.PadModel
import com.nxredux.mobile.shortcuts.ShortcutAction
import com.nxredux.mobile.shortcuts.ShortcutSet
import com.nxredux.mobile.shortcuts.TouchSlot
import com.nxredux.mobile.update.AppRelease
import com.nxredux.mobile.update.UpdateController
import com.nxredux.mobile.update.UpdateState
import com.nxredux.mobile.library.folderSummary
import com.nxredux.mobile.achievements.RaController
import com.nxredux.mobile.achievements.RaMessage
import com.nxredux.mobile.achievements.RaProgress
import com.nxredux.mobile.achievements.RaState
import com.nxredux.mobile.cheats.db.CheatDbController
import com.nxredux.mobile.cheats.db.CheatDbMessage
import com.nxredux.mobile.sync.DeviceSyncController
import com.nxredux.mobile.sync.DeviceSyncPage
import com.nxredux.mobile.sync.DeviceSyncText
import com.nxredux.mobile.gametime.GameTimeData
import com.nxredux.mobile.gametime.GameTotal
import com.nxredux.mobile.gametime.LocalCalendar
import com.nxredux.mobile.gametime.systemLocalCalendar
import com.nxredux.mobile.launcher.AppIcons
import com.nxredux.mobile.launcher.AppLauncher
import com.nxredux.mobile.launcher.InstalledApp
import com.nxredux.mobile.library.ArcadeSets
import com.nxredux.mobile.library.ArtRefs
import com.nxredux.mobile.achievements.RaGamesCache
import com.nxredux.mobile.library.Collections
import com.nxredux.mobile.library.GameEntry
import com.nxredux.mobile.library.LibraryIndex
import com.nxredux.mobile.library.LibraryIndexCodec
import com.nxredux.mobile.library.LibraryScanner
import com.nxredux.mobile.library.ListingUnavailable
import com.nxredux.mobile.library.MergedLabels
import com.nxredux.mobile.library.NaomiSets
import com.nxredux.mobile.library.Names
import com.nxredux.mobile.library.RomsSource
import com.nxredux.mobile.library.Sidecars
import com.nxredux.mobile.library.Skeleton
import com.nxredux.mobile.library.SystemFolder
import com.nxredux.mobile.library.fits
import com.nxredux.mobile.library.move
import com.nxredux.mobile.library.path
import com.nxredux.mobile.library.withArt
import com.nxredux.mobile.library.rename
import com.nxredux.mobile.library.setBadgeChoice
import com.nxredux.mobile.library.playableSystems
import com.nxredux.mobile.library.withFirstSeen
import com.nxredux.mobile.options.CoreOptionSource
import com.nxredux.mobile.options.EmulatorPage
import com.nxredux.mobile.options.NoCoreOptions
import com.nxredux.mobile.netplay.NetplaySession
import com.nxredux.mobile.runner.Console
import com.nxredux.mobile.runner.DsMenu
import com.nxredux.mobile.runner.withConsole
import com.nxredux.mobile.runner.withEntry
import com.nxredux.mobile.runner.ContentRef
import com.nxredux.mobile.runner.CoreRegistry
import com.nxredux.mobile.runner.EmulatorRunner
import com.nxredux.mobile.scraper.AndroidArtStore
import com.nxredux.mobile.scraper.ArtHave
import com.nxredux.mobile.scraper.ArtworkController
import com.nxredux.mobile.scraper.ArtworkHost
import com.nxredux.mobile.scraper.LegacyArtMove
import com.nxredux.mobile.scraper.LibraryGameRef
import com.nxredux.mobile.settings.migrateMixWheel
import com.nxredux.mobile.settings.AppSettings
import com.nxredux.mobile.gpudriver.GpuDriverCrash
import com.nxredux.mobile.gpudriver.GpuDriverStore
import com.nxredux.mobile.settings.PlayerName
import com.nxredux.mobile.settings.ImportResult
import com.nxredux.mobile.settings.ImportSource
import com.nxredux.mobile.settings.SaveTransfer
import com.nxredux.mobile.settings.MenuCategory
import com.nxredux.mobile.settings.MenuOrientation
import com.nxredux.mobile.settings.MenuStyle
import com.nxredux.mobile.settings.MenuStyles
import com.nxredux.mobile.runner.WiiPad
import com.nxredux.mobile.settings.GameRumble
import com.nxredux.mobile.settings.ExtraFolder
import com.nxredux.mobile.settings.FolderPicker
import com.nxredux.mobile.settings.Sources
import com.nxredux.mobile.settings.SourceConversion
import com.nxredux.mobile.storage.FileRefs
import com.nxredux.mobile.storage.AllFilesAccess
import com.nxredux.mobile.runner.MicAccess
import com.nxredux.mobile.storage.HomeLocator
import com.nxredux.mobile.storage.StorageUpgradePlatform
import com.nxredux.mobile.storage.UpgradeCase
import com.nxredux.mobile.storage.LocalFiles
import com.nxredux.mobile.storage.GamePath
import com.nxredux.mobile.storage.GameRef
import com.nxredux.mobile.storage.EmulatorConfigStore
import com.nxredux.mobile.storage.LibraryIndexStore
import com.nxredux.mobile.storage.NoEmulatorConfigs
import com.nxredux.mobile.storage.MemoryLibraryIndexStore
import com.nxredux.mobile.storage.MemorySidecarStore
import com.nxredux.mobile.storage.NoUserData
import com.nxredux.mobile.storage.PathLines
import com.nxredux.mobile.storage.MemoryPrivateFiles
import com.nxredux.mobile.storage.PrivateFiles
import com.nxredux.mobile.storage.Recent
import com.nxredux.mobile.storage.Recents
import com.nxredux.mobile.storage.RecentsRecorder
import com.nxredux.mobile.storage.ResumeInfo
import com.nxredux.mobile.ui.nx.statsKey
import com.nxredux.mobile.storage.Shortcut
import com.nxredux.mobile.storage.SidecarStore
import com.nxredux.mobile.storage.ToolPins
import com.nxredux.mobile.storage.UserDataStore
import com.nxredux.mobile.ui.keyboard.EntryField
import com.nxredux.mobile.ui.keyboard.KeyPos
import com.nxredux.mobile.ui.keyboard.PromptSpec
import com.nxredux.mobile.ui.keyboard.TextEntry
import com.nxredux.mobile.ui.keyboard.TextEntryController
import com.nxredux.mobile.ui.nx.HomeFrame
import com.nxredux.mobile.ui.nx.HomeShelf
import com.nxredux.mobile.ui.nx.FooterMode
import com.nxredux.mobile.ui.nx.LauncherChrome
import com.nxredux.mobile.ui.nx.ListSelection
import com.nxredux.mobile.ui.nx.NxSettingControl
import com.nxredux.mobile.ui.nx.NxSettingEvent
import com.nxredux.mobile.ui.nx.control
import com.nxredux.mobile.ui.nx.launcherChromeFor
import com.nxredux.mobile.ui.nx.DialogFocus
import com.nxredux.mobile.ui.nx.NxButton
import com.nxredux.mobile.ui.nx.gridAtTop
import com.nxredux.mobile.ui.nx.gridStep
import com.nxredux.mobile.ui.nx.rowStep
import com.nxredux.mobile.ui.nx.stackStep
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.CoroutineContext
import com.nxredux.mobile.ui.nx.TopDetails
import com.nxredux.mobile.ui.nx.TopStats
import com.nxredux.mobile.scraper.StoredGameInfo

class AppDeps(
    val settings: AppSettings,
    val source: RomsSource,
    val runner: EmulatorRunner,
    val folderPicker: FolderPicker,
    val userData: UserDataStore = NoUserData,
    val cheatDb: CheatDbController? = null,
    /** The RetroAchievements tool (null on a platform without it). */
    val achievements: RaController? = null,
    val sidecarStore: SidecarStore = MemorySidecarStore(),
    /** The persisted library index (app-private on Android). */
    val indexStore: LibraryIndexStore = MemoryLibraryIndexStore(),
    /** Settings -> System's About rows: the version, build and device. */
    val appInfo: AppInfo = AppInfo("0.0.0"),
    /** About's GPU name, asked once when the page first shows (off the main thread on the platform's side); null: unknown. */
    val deviceGpu: suspend () -> String? = { null },
    /** Puts plain text on the clipboard (About's Copy device info); a no-op on a platform without one. */
    val copyText: (String) -> Unit = {},
    /** Opens a web page in the browser (System's Full list of licenses); a no-op on a platform without one. */
    val openUrl: (String) -> Unit = {},
    /** Records started games; process-lifetime on a platform whose menu can die while a game loads. Null = on the state's scope. */
    val recents: RecentsRecorder? = null,
    /** FBNeo's set table (titles, parents, BIOS); read on first use. Empty on a platform or build without it. */
    val arcadeSets: () -> ArcadeSets = { ArcadeSets.EMPTY },
    /** flycast's NAOMI/Atomiswave set table (titles, BIOS); read on first use. Empty on a platform or build without it. */
    val naomiSets: () -> NaomiSets = { NaomiSets.EMPTY },
    /** The bundled core option tables (Emulator Settings). */
    val coreOptions: CoreOptionSource = NoCoreOptions,
    /** Console and game `.cfg` files for Emulator / Game Settings. */
    val emulatorConfigs: EmulatorConfigStore = NoEmulatorConfigs,
    /** Extra defaults per core id the platform adds (melonDS DS's renderer by GLES version). */
    val coreDefaults: (String) -> Map<String, String> = { emptyMap() },
    /** Tools -> Game Tracker's data (null on a platform without the tracker: no Tools row). */
    val gameTime: GameTimeData? = null,
    /** The top screen's details page reads a game's file and BIOS facts here (null on a platform without them). */
    val topFacts: TopFactsPlatform? = null,
    /** The wall clock (epoch ms) that stamps a successful scan for Settings -> Library's "Last scan"; null records none. */
    val now: (() -> Long)? = null,
    /**
     * Tools -> Artwork: builds (or, on Android, re-hosts the process's) controller with this AppState as its host, so every
     * index write stays here. Null on a platform without it: no Tools row.
     */
    val artwork: ((ArtworkHost) -> ArtworkController)? = null,
    /** Settings -> System's Launcher group, Library's Android games and Android apps in Tools. Null on a platform without them: none of those. */
    val launcher: AppLauncher? = null,
    /** App-private text files (the artwork job and its written-art log). */
    val privateFiles: PrivateFiles = MemoryPrivateFiles(),
    /** Where CPU-bound UI work runs off the main thread (the collection counts, Emulator Settings' pages); tests pass their own. */
    val compute: CoroutineContext = Dispatchers.Default,
    /** The local calendar Home's stats month follows (the device's zone); tests pass a fixed one. */
    val calendar: LocalCalendar = systemLocalCalendar(),
    /** The Android games console's fetched screenshots (app-private); null: none (a platform without it, or not wired yet). */
    val androidArtStore: AndroidArtStore? = null,
    /** Controls → Game rumble changed: one short pulse at the new level's floor (none for Off); a no-op without a vibrator. */
    val rumblePreview: (GameRumble) -> Unit = {},
    /** Wi-Fi netplay's platform side (sockets, discovery, save staging); null: no netplay (tests, other platforms). */
    val netplay: com.nxredux.mobile.netplay.NetplayPlatform? = null,
    /** The menu window's screen class now (Emulator Settings' DS and 3DS layout rows); null: unknown. */
    val screenClass: () -> com.nxredux.mobile.layout.ScreenClass? = { null },
    /** The menu window's size in px now, width to height (Emulator Settings' DS 3D scales); null: unknown. */
    val windowPx: () -> Pair<Int, Int>? = { null },
    /** The ".bin" names in Bios/PS2/ (Emulator Settings' PS2 BIOS row); blocking, called off the main thread. */
    val ps2BiosFiles: () -> List<String> = { emptyList() },
    /** The ".bin" names in Bios/NDS/ (whether DS Download Play can boot the DS menu); blocking, called off the main thread. */
    val ndsBiosFiles: () -> List<String> = { emptyList() },
    /**
     * The device turns its screen for an app that asks (Android's auto-rotate on). Off, Button layout offers only the
     * window's current orientation: a display that keeps its rotation leaves the other one's editor with nothing drawn.
     */
    val screenRotates: () -> Boolean = { true },
    /** Opens the skin's button layout editor (Android: its own activity); it saves to the settings and ends with [AppState.onSkinLayoutEdited]. Null: no editor. */
    val skinEditor: SkinEditorLauncher? = null,
    /** Settings → Library → Saves: Export and Import (null: rows do nothing). */
    val saveTransfer: SaveTransfer? = null,
    /** Settings → Library → Import BIOS (null: no row). */
    val biosIntake: com.nxredux.mobile.bios.BiosIntake? = null,
    /** Tools → Device Sync (null: a platform without it, so no Tools row). */
    val deviceSync: DeviceSyncController? = null,
    /** All files access (a sideloaded Android 11+ build); null: none (SAF only, no setup step, no Settings row). */
    val allFilesAccess: AllFilesAccess? = null,
    /** Microphone access for DS / 3DS games' Mic toggle (Settings → Library row); null: none, or no mic to use. */
    val micAccess: MicAccess? = null,
    /** ROMs received over a Cable Link session (the Clear link ROM cache row on mGBA's Emulator Settings page); null: none on this platform. */
    val linkRomCache: LinkRomCache? = null,
    /** Local folders for the list-style picker and the SAF → file conversion; null: Android's pickers only. */
    val localFiles: LocalFiles? = null,
    /**
     * The first [max] bytes of the ROM entry a launch would unpack from the zip/7z archive [name] at [ref] (picked by
     * [romExtensions], as the runner's unpack picks it), read without unpacking it; null when unreadable or none.
     */
    val archiveHead: suspend (ref: ContentRef, name: String, romExtensions: Set<String>, max: Int) -> ByteArray? = { _, _, _, _ -> null },
    /** A CD CHD's sectors for the scan's disc probe (libchdr on Android); null places only GD and DVD CHDs by their metadata. */
    val chdSectors: com.nxredux.mobile.library.disc.ChdSectors? = null,
    /** The notification permission (Android 13+), asked with All files access on setup's Permissions page. */
    val notificationAccess: com.nxredux.mobile.storage.NotificationAccess? = null,
    /**
     * A built-in second screen is there now (a dual-screen handheld's, not an attached one): onboarding asks which screen
     * is the bottom one. The platform shows the step's second button on it ([AppState.onboardingBottomScreenOnSecond]).
     */
    val builtInSecondScreen: () -> Boolean = { false },
    /** The physical pads connected now, by model (gamepads and joysticks, not virtual ones): the SELECT / START / MENU page's. */
    val connectedPads: () -> List<PadModel> = { emptyList() },
    /** Where the home is now (the full-access folder, else the app's Android/media one); null: the saved [AppSettings.treeRef] (tests). */
    val homes: HomeLocator? = null,
    /** The one-time storage upgrade for installs from v0.3.0 and earlier (remove after v1.0); null: none (tests, other platforms). */
    val storageUpgrade: StorageUpgradePlatform? = null,
    /** The in-app updater (Android's, process-wide): the main menu's Update available popup and About's update row; null: neither. */
    val updater: UpdateController? = null,
    /** The switcher return kept on disk (the game runs in another process, which may outlive this one); null: memory only. */
    val switcherReturns: com.nxredux.mobile.runner.SwitcherReturnMark? = null,
    /** The platform draws the NX keyboard ([AppState.textEntry]); off: every prompt types through the platform's own. */
    val ownKeyboard: Boolean = false,
    /** The device's own name (Android's About phone → Device name), the Player name until one is set; null: unknown. */
    val deviceName: () -> String? = { null },
    /** The clipboard's plain text for the keyboard's Paste; null when it holds none. */
    val pasteText: () -> String? = { null },
    /** The open prompt is drawn on the second display, where Android's keyboard does not open: NX's always types there. */
    val promptOnSecondDisplay: () -> Boolean = { false },
    /** A diagnostic line (Android: NxLog, so only with Settings → System → Debug log on). */
    val log: (String) -> Unit = {},
    /** Settings → Emulation → GPU driver and the consoles' GPU driver rows; null: a platform without custom drivers (no row). */
    val gpuDrivers: GpuDriverStore? = null,
)

/**
 * Appearance → Button layout → Portrait or Landscape: edit [consoleId]'s pad skin buttons in that orientation, on this
 * screen. [wiiPad]: the Wii controller whose layout is edited (null for every other console). [n3dsNew]: the 3DS's New
 * 3DS controls layout (ZL / ZR, the C-stick), else the regular 3DS's. [segaThree]: the Sega pad's 3-button layout, else
 * the 6-button one.
 */
data class SkinEditRequest(val consoleId: String, val landscape: Boolean, val wiiPad: WiiPad?, val n3dsNew: Boolean = false, val segaThree: Boolean = false)

/** Opens the button layout editor for a [SkinEditRequest], [title] naming it there. */
interface SkinEditorLauncher { fun open(request: SkinEditRequest, title: String) }

/** The global Shortcuts page's note on Toggle mic, which does nothing outside DS / 3DS. */
internal const val MIC_ROW_NOTE = "DS / 3DS only"

/** A Shortcuts bind row's value while it waits for a press. */
internal const val SHORTCUT_PROMPT = "Press a button…"

/** Controls → System buttons without a pad connected. */
internal const val SYSTEM_BUTTONS_NO_CONTROLLER = "Connect a controller to set its SELECT, START and MENU buttons."
/** A System buttons row waiting for its press. */
internal const val SYSTEM_BUTTON_PROMPT = "Press a button…"
/** Both Shortcuts pages' line in place of the bind rows while no controller is connected (the on-screen pad binds nothing). */
internal const val SHORTCUTS_NO_CONTROLLER = "Connect a controller to bind buttons. On the on-screen pad, use Touch button 1 and 2 instead."

/** How long a notice stays before it clears itself. */
private const val NOTICE_MILLIS = 3_000L

/**
 * How long the installed library's Android games dialog ignores its answers after it shows: it can appear over a menu
 * already in use, so a press or tap meant for the menu must not answer it.
 */
internal const val ANDROID_ASK_GUARD_MILLIS = 300L

/**
 * A Minimal page's folder open: the other names fade out over [MINIMAL_LEAVE_MS] and its list opens then; the opening
 * clears this long after. A game or a tool opens at once instead.
 */
internal const val MINIMAL_LEAVE_MS = 120L

/**
 * A Minimal page's folder being opened ([AppState.minimalOpening]): the page it is on ([page]: a tab's [MenuCategory], a
 * game list's [ListSource]; only that page draws it) and the row's [index]; the other names fade before its list comes in.
 * A game's or a tool's open is not one: nothing on the page moves or fades.
 */
data class MinimalOpening(val page: Any, val index: Int)

/** The first sentence of every "home on an unmounted card" error. */
private const val HOME_OFFLINE = "The home folder's storage is not available."

/** A file home read without All files access (revoked): the home, its index and its extras are kept. */
private const val ACCESS_OFF = FileRefs.ACCESS_OFF_MESSAGE

/** A scan or the start probe met a listing the provider would not give ([ListingUnavailable]): the index is kept. */
private const val LISTING_HIDDEN = "Storage did not list a folder just now; the library is unchanged. Use Rescan in Settings → Library in a moment."
private const val LISTING_HIDDEN_NO_INDEX = "Storage did not list the library folders just now. Reopen the app in a moment."

/** "1 game" / "<n> games" for the Library page and the rescan notice. */
internal fun gamesLabel(n: Int): String = if (n == 1) "1 game" else "$n games"

/** "None" / "1 game" / "<n> games": Library's "Android games" count. */
internal fun gamesCountLabel(n: Int): String = if (n == 0) "None" else gamesLabel(n)

/** Where the game at [path] is in the list; null when it is not there (or no path is given). */
private fun List<Listed>.indexOfPath(path: String?): Int? =
    path?.let { p -> indexOfFirst { it.game.path == p } }?.takeIf { it >= 0 }

data class SwitcherEntry(val system: SystemFolder, val game: GameEntry, val resume: ResumeInfo)

/**
 * How a page change moves on screen: NextUI's folder slides (A in, B out) and its game switcher slide (up, down). Between
 * two Minimal pages a folder morphs instead ([MINIMAL_FORWARD]: the name flies into the new list's title as its names
 * rise in; [MINIMAL_BACK]: the title drops back into its row); a page that isn't Minimal slides ([landing]).
 */
enum class NavMotion {
    NONE, FORWARD, BACK, SWITCHER_OPEN, SWITCHER_CLOSE, MINIMAL_FORWARD, MINIMAL_BACK;

    /** The slide a Minimal morph falls back to. */
    val slide: NavMotion get() = when (this) { MINIMAL_FORWARD -> FORWARD; MINIMAL_BACK -> BACK; else -> this }
}

/** The last animated page change: [seq] moves only on one, so any other screen change (a refresh, Settings) is instant. */
data class NavStep(val seq: Int = 0, val motion: NavMotion = NavMotion.NONE)

/** [flow] whose every `value` write is reported to [onSet] after it lands ([AppState]'s screen: leaving the Shortcuts page). */
private class WatchedStateFlow<T>(private val flow: MutableStateFlow<T>, private val onSet: (T) -> Unit) : MutableStateFlow<T> by flow {
    override var value: T
        get() = flow.value
        set(v) { flow.value = v; onSet(v) }
}

class AppState(
    private val deps: AppDeps, private val scope: CoroutineScope,
    /** Kids Mode's state; null on a platform (or a test) without it: always [KidsMode.Off]. */
    private val kids: KidsController? = null,
) {
    private val sidecars = Sidecars(deps.source, deps.sidecarStore) { home() }
    private val scanner = LibraryScanner(
        deps.source, sidecars, deps.arcadeSets, deps.naomiSets, compute = deps.compute, chdSectors = deps.chdSectors,
        picks = { artworkController?.picks().orEmpty() },               // a picked extra game slot keeps its mirror file
        heroShots = { artworkController?.heroShots().orEmpty() },       // a SteamGridDB hero screenshot keeps hero drawing
        badgeChoices = { deps.userData.readBadges() },                   // a game's own badge choice wins over its tags
        legacyMove = { legacyArtMove },                                  // old grid/ and hero/ files move once
    )

    /** The Shortcuts and System buttons pages' state and raw keys. Before [_screen]: its watcher reports to it. */
    private val controlBindings = ControlBindingsController(
        deps,
        host = object : ControlBindingsHost {
            override fun openSection(): SettingsSection? = (_screen.value as? Screen.Settings)?.section
            override fun refreshSettings() = settingsUi.refreshSettings()
            override fun refreshSettingsOn(vararg sections: SettingsSection) = this@AppState.refreshSettingsOn(*sections)
            override fun showNotice(text: String) = this@AppState.showNotice(text)
        },
    )
    /** The action a Shortcuts bind row is waiting on a press for; while set the platform hands raw keys to [captureKey] first. */
    val capturingShortcut: StateFlow<ShortcutAction?> get() = controlBindings.capturingShortcut
    /** A raw key from the platform, before it maps to an [NxButton]: true when press to bind took it. */
    fun captureKey(keyCode: Int, down: Boolean): Boolean = controlBindings.captureKey(keyCode, down)
    /** Every key from a physical device, before anything else sees it: true when System buttons used it. */
    fun systemButtonKey(model: PadModel?, source: String?, keyCode: Int, scanCode: Int, down: Boolean, repeat: Boolean): Boolean =
        controlBindings.systemButtonKey(model, source, keyCode, scanCode, down, repeat)
    /** The UI reports whether a controller is connected. */
    fun onControllerConnected(connected: Boolean) {
        controlBindings.onControllerConnected(connected)
        if (settingsContent.controllerConnected == connected) return
        settingsContent.controllerConnected = connected
        refreshSettingsOn(SettingsSection.APPEARANCE)
    }

    private val _rowEdit = MutableStateFlow<RowEdit?>(null)
    /** The row the pad is changing ([NxPadA.EDIT], [onRowEditButton]); another page drops it. */
    val rowEdit: StateFlow<RowEdit?> = _rowEdit.asStateFlow()

    /**
     * Every screen write, here or in a controller sharing it: one that leaves the Shortcuts page, by B, another page,
     * Home or anything else, closes it ([closeShortcuts]), so no way out keeps a wait swallowing the menu's keys or
     * drops the draft. Declared after the Shortcuts and row-edit state it reads.
     */
    private val _screen: MutableStateFlow<Screen> = WatchedStateFlow(MutableStateFlow(Screen.Loading)) { s ->
        if (_rowEdit.value?.key != rowEditKey(s)) _rowEdit.value = null
        controlBindings.onScreen(s)
        keyboard.sync()   // a prompt opened, closed or changed
    }
    val screen: StateFlow<Screen> = _screen.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    /** A page's own message (its title, its text) in an info dialog: A or B closes it. */
    private val _message = MutableStateFlow<Pair<String, String>?>(null)
    val message: StateFlow<Pair<String, String>?> = _message.asStateFlow()
    private val loader = ScreenLoader(scope)
    /** A screen load has run past its grace period: the loading overlay shows (B cancels, [ScreenLoader]). */
    val loading: StateFlow<Boolean> = loader.visible
    /** The overlay's line while [loading]. */
    val loadingMessage: StateFlow<String> = loader.message
    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice.asStateFlow()   // one line, dismissed by the next button or by itself after three seconds
    private var noticeJob: Job? = null   // the pending auto-clear of the notice
    private var romsPending = false          // ROMs arrived off a plain Systems page: the library rescans when one shows again
    private var noticeSticky = false     // the notice is a transfer's progress: it stays until the result replaces it
    private var systemsCache: List<SystemFolder> = emptyList()
    private var reindexJob: Job? = null   // [removeExtra]'s background re-index
    private var lateMountHere: List<String> = emptyList()   // [LibraryIndex.lateMount] of this run's last scan (a stored one is stale)
    /**
     * [systemsCache]'s writers take a generation before computing it ([takeSystemsGen]) and write only when it is newer than
     * the one shown ([offerSystems]): passes run side by side, and an older one landing late never puts back what a newer
     * one (a hide, a refresh) changed. Under [systemsLock].
     */
    private val systemsLock = Mutex()
    private var systemsTaken = 0L
    private var systemsWritten = 0L
    /** The last search confirmed this run (START's prompt opens on it); not saved. */
    internal var lastSearch: String = ""
        private set
    private val collections = Collections(deps.source, deps.sidecarStore) { home() }
    private var allMenuTabs: List<MenuTab> = emptyList()   // every tab with content as last built, hidden ones too (no I/O to show it again)
    /** The tabs the main menu shows: [allMenuTabs] less the ones Layouts hides and, while Kids Mode hides it, Tools ([visibleMenuTabs]). */
    private val menuTabs: List<MenuTab> get() = visibleMenuTabs(kidsUi.withKidsApps(kidsUi.withKidsLock(allMenuTabs)), shownTabs(), hideTools = !kidsUi.kidsToolsAllowed)
    /** [allMenuTabs] less Tools while Kids Mode hides it: never offered as a hidden tab either. */
    private val kidsMenuTabs: List<MenuTab> get() = if (kidsUi.kidsToolsAllowed) kidsUi.withKidsLock(allMenuTabs) else allMenuTabs.filter { it.category != MenuCategory.TOOLS }
    private var pins: List<Shortcut> = emptyList()           // shortcuts.txt as last read
    @Volatile private var hiddenPaths: List<String> = emptyList()      // hidden.txt as last read; an index refresh off the main thread reads it
    /** The pinned tools, in pin order: [ToolPins.FILE] in [AppDeps.privateFiles] as last read or written (never shortcuts.txt). */
    var toolPins: List<String> = emptyList()
        private set
    /** The Collections tab's names and counts. */
    private val collectionCounter = CollectionCountsController(
        scope, deps, collections,
        host = object : CollectionCountsHost {
            override fun home(): ContentRef? = this@AppState.home()
            override fun systems(): List<SystemFolder> = systemsCache
            override suspend fun playableOffMain(systems: List<SystemFolder>): Map<GameRef, Listed> = this@AppState.playableOffMain(systems)
            override suspend fun buildMainRowsMerged() = this@AppState.buildMainRowsMerged()
            override fun swapMainRows() = this@AppState.swapMainRows()
            override suspend fun refreshMainRows() = this@AppState.refreshMainRows()
            override fun invalidateStats(stale: String?) = topScreen.invalidateStats(stale)
        },
    )
    private val rowsLock = Mutex()                            // one main-row rebuild at a time: an older one never writes last
    private var menuCategory: MenuCategory = MenuCategory.HOME   // the tab last shown (the menu opens on Home); a gone one resolves to the next visible tab
    private var pushedTab: MenuCategory? = null   // a hidden tab opened over the menu ([openHiddenTab]); [menuCategory] stays the tab under it
    private val tabSelections = HashMap<MenuCategory, ListSelection>()   // each tab's row, remembered across visits
    private var gridColumns = 0   // the Grid body's column count for the screen showing now; 0 until it lays out
    private var pageRows = 0      // a game list's rows (or stack items) on one screen ([onPageRows]); 0 until it lays out: a page is one row
    private var installedCache: List<InstalledApp>? = null   // the last [AppLauncher.installedApps]; null until the first load
    private var installedJob: Job? = null
    /**
     * The Android games console ([androidConsoleOf]) from [installedCache] and the settings; null when off, without a
     * launcher, before the first app load or with no game. Never in [systemsCache]: the Consoles rows alone show it.
     */
    internal var androidConsole: SystemFolder? = null
        private set
    private var menuAndroidConsole: SystemFolder? = null   // the Android console the main menu was last built with
    private val _androidGamesAsk = MutableStateFlow<AndroidGamesAsk?>(null)
    /** An installed library's "Add N Android games to Consoles?" dialog over the main menu (A Yes, B Not now); null when not showing. */
    val androidGamesAsk: StateFlow<AndroidGamesAsk?> = _androidGamesAsk.asStateFlow()
    private var androidAskGuard: Job? = null   // active for [ANDROID_ASK_GUARD_MILLIS] after the dialog shows: its answers are ignored

    private val _firstTip = MutableStateFlow<FirstTip?>(null)
    /** The first-launch tip showing ([FirstTip]): a dialog over the main menu or a game list; null for none. */
    val firstTip: StateFlow<FirstTip?> = _firstTip.asStateFlow()
    /** The tips shown this launch: Got it closes one until the next launch. */
    private val tipsShown = HashSet<FirstTip>()
    private var tipGuard: Job? = null   // as [androidAskGuard]: a button held from before the tip doesn't answer it
    /**
     * A game list's panel is open (the achievements panel or the art panel): on the Games tab the menu is then not free
     * ([menuFree]). Declared before its readers; it follows the panels from where they are built ([artPanel]).
     */
    private val _panelOpen = MutableStateFlow(false)
    private var updateAskGuard: Job? = null   // as [androidAskGuard], for the update dialog (declared first: [updatePopup] may set it while built)
    private val _updateScroll = MutableSharedFlow<Int>(extraBufferCapacity = 8)
    /** The update dialog's (or About's confirm's) d-pad up (-1) / down (+1), for notes taller than the dialog. */
    val updateScroll: SharedFlow<Int> = _updateScroll
    private val _updateConfirm = MutableStateFlow<AppRelease?>(null)
    /** About's Update to: the release's notes with Update / Cancel before anything downloads; null when not showing. */
    val updateConfirm: StateFlow<AppRelease?> = _updateConfirm.asStateFlow()
    /**
     * The updater's Update available dialog over the main menu (A Update, B Not now); null when not showing. It waits behind
     * the Android games question, a Problem dialog and a context menu, and shows on the main menu only (not over onboarding).
     */
    val updatePopup: StateFlow<AppRelease?> = deps.updater?.let { u ->
        var shown = false
        combine(u.popup, _androidGamesAsk, _error, _screen, _panelOpen) { release, ask, error, screen, panel -> release?.takeIf { !panel && updatePopupFits(ask, error, screen) } }
            .onEach {
                // Guarded from before it draws: the check lands while the menu is in use, so a press may be on its way.
                if (it != null && !shown) { updateAskGuard?.cancel(); updateAskGuard = scope.launch { delay(ANDROID_ASK_GUARD_MILLIS) } }
                shown = it != null
            }
            .stateIn(scope, SharingStarted.Eagerly, null)
    } ?: MutableStateFlow(null)
    /** About's update row's label ([appUpdateLabel] of the updater's state), set from init; null without an updater. */
    private val _updateLabel = MutableStateFlow<String?>(null)
    internal val updateLabel: StateFlow<String?> = _updateLabel.asStateFlow()   // the two-pane preview of About redraws on it, as on [gpu]
    /** First-launch onboarding and Settings → Run setup again ([OnboardingController]). */
    val onboarding = OnboardingController()
    private var afterAndroidStep: (() -> Unit)? = null   // what first setup's Android games step goes on to (a load or the extra's scan)
    /** Android apps' icons for the session (Tools tiles, Home pins, the picker); null without a launcher. */
    val appIcons: AppIcons? = deps.launcher?.let { AppIcons(it, scope) }

    private val _selection = MutableStateFlow(ListSelection(0, 0))
    /** The pad's place in an open confirm dialog's buttons ([DialogFocus]); every press runs through it first ([onButton]). */
    val dialogFocus = DialogFocus()

    val selection: StateFlow<ListSelection> = _selection.asStateFlow()

    /** Home's builds, stats strip and d-pad memory; it reaches back into this state only through [HomeHost]. */
    private val homeTab = HomeController(
        scope, _screen, _selection, deps.calendar, deps.gameTime, deps.achievements, deps.compute,
        host = object : HomeHost {
            override fun homeStyle(): MenuStyle = this@AppState.homeStyle()
            override fun clockNow(): Long = this@AppState.clockNow()
            override fun raSignedIn(): Boolean = this@AppState.raSignedIn()
            override fun resolveOne(path: String): Listed? = this@AppState.resolveOne(path)
            override fun shelvesFor(inputs: HomeInputs): List<Pair<HomeShelf, List<Listed>>> = this@AppState.shelvesFor(inputs)
            // Most played wears its game's play time ("12h 30m"), as the shelf orders them.
            override fun shelfTag(shelf: HomeShelf, game: Listed): String? =
                if (shelf != HomeShelf.MOST_PLAYED) null else _gameInfo.value[game.game.path]?.totalMs?.let(com.nxredux.mobile.gametime.GameTimeFormat::duration)
            override fun rebuildHomeTab() = this@AppState.rebuildHomeTab()
            override fun refreshResume() = this@AppState.refreshResume()
            override fun switchTab(step: Int) = this@AppState.switchTab(step)
        },
    )
    /** What Home draws beside its rows (the strip, the top section's size, the pinned counts), for the rows on screen. */
    val homeView: StateFlow<HomeView> get() = homeTab.homeView
    /** The Carousel Home's row item last on ([HomeController.homeCarouselRowLast]). */
    val homeCarouselRowLast: StateFlow<Int> get() = homeTab.homeCarouselRowLast

    /** The Android apps picker and app launches; it reaches back into this state only through [AppsHost]. */
    private val androidApps = AppsController(
        scope, _screen, _selection, deps.settings, loader, deps.launcher, appIcons,
        host = object : AppsHost {
            override fun showNotice(text: String) = this@AppState.showNotice(text)
            override fun refreshInstalled() = this@AppState.refreshInstalled()
            override fun installedLoaded(apps: List<InstalledApp>) { installedCache = apps }
            override fun rememberSettingsRow() { (_screen.value as? Screen.Settings)?.let { settingsUi.rememberSection(it.section, _selection.value) } }
            override fun back(): Boolean = this@AppState.back()
            override fun openSelected() = this@AppState.openSelected()
            override fun openContextMenu() = this@AppState.openContextMenu()
            override fun drawerRows(apps: List<InstalledApp>): List<ToolAppRow> = this@AppState.drawerRows(apps)
            override suspend fun rebuildMainRows() = this@AppState.rebuildMainRows()
            override fun swapMainRows() = this@AppState.swapMainRows()
            override suspend fun moveToolPinsToAndroid(ids: List<String>) = this@AppState.moveToolPinsToAndroid(ids)
            override suspend fun applyAndroidConsole() = this@AppState.applyAndroidConsole()
        },
    )

    /** Kids Mode's screens, setup and Settings page; declared early: the menu's tabs read its mode from the first build. */
    private val kidsUi: KidsScreensController = KidsScreensController(
        scope, _screen, _selection, deps.settings, deps.launcher, kids, onboarding,
        host = object : KidsHost {
            override fun showNotice(text: String) = this@AppState.showNotice(text)
            override fun showMessage(title: String, text: String) { _message.value = title to text }
            override fun showSystemsNow(category: MenuCategory?) = this@AppState.showSystemsNow(category)
            override fun clearMenuTop() { pushedTab = null }
            override fun select(index: Int) = this@AppState.select(index)
            override fun back(): Boolean = this@AppState.back()
            override fun openSettings(section: SettingsSection) = settingsUi.openSettings(section)
            override fun settingsPage(section: SettingsSection): Screen.Settings = settingsUi.settingsPage(section)
            override fun selectableOn(page: Screen.Settings, sel: ListSelection): ListSelection = settingsUi.selectableOn(page, sel)
            override fun refreshSettings() = settingsUi.refreshSettings()
            override fun closeChooser() = settingsUi.closeChooser()
            override fun closeSettingInfo() = settingsUi.closeSettingInfo()
            override fun rebuildHomeTab() = this@AppState.rebuildHomeTab()
            override fun openPicker(title: String, labels: List<String>, current: Int, apply: (Int) -> Unit) =
                settingsUi.openPicker(title, labels, current, apply)
            override fun onboardingNext() = onboardingUi.onboardingNext()
            override fun showOnboarding() = onboardingUi.showOnboarding()
            override val installedApps: List<InstalledApp>? get() = installedCache
            override fun menuWaits(): Boolean = allMenuTabs.isEmpty() || onboardingUi.firstSetup
        },
    )

    private val _dualMenu = MutableStateFlow(false)
    /**
     * The main menu is on two screens: a second display ([onDualMenu]) or the skin's two screens ([onSkinDualMenu]). The
     * preview and the game list's details live on the top one.
     */
    val dualMenu: StateFlow<Boolean> = _dualMenu.asStateFlow()

    /** The menu window is tablet-class (the Fold's inner screen too): upright, the menu skin may show two screens. */
    fun tabletWindow(): Boolean = deps.screenClass()?.isTablet == true

    private val _displayDualMenu = MutableStateFlow(false)
    /** The main menu's top screen is a second display (the presentation path), not the skin's top glass. */
    val displayDualMenu: StateFlow<Boolean> = _displayDualMenu.asStateFlow()
    private val _skinDualMenu = MutableStateFlow(false)
    /** The menu skin shows its two screens ([onSkinDualMenu]); the pad's visibility depends on it ([menuPadShown]). */
    val skinDualMenu: StateFlow<Boolean> = _skinDualMenu.asStateFlow()

    private val _appOnTop = MutableStateFlow(false)
    /** An app the player opened holds the top screen: the main menu shows the button that takes it back ([takeTopScreen]). */
    val appOnTop: StateFlow<Boolean> = _appOnTop.asStateFlow()

    /** The platform tells an app holds the top screen ([on]) or let it go. */
    fun onAppOnTop(on: Boolean) { _appOnTop.value = on }

    /** Takes the top screen back from the app there; set by the platform. */
    var takeTopScreen: () -> Unit = {}

    /** The platform tells the menu is on the second display ([active]) or back on the main one. */
    fun onDualMenu(active: Boolean) {
        _displayDualMenu.value = active
        applyDualMenu()
    }

    /** The menu skin shows its two screens ([active]: the top screen in its top glass, the menu below) or not. */
    fun onSkinDualMenu(active: Boolean) {
        _skinDualMenu.value = active
        applyDualMenu()
    }

    private fun applyDualMenu() {
        val active = _displayDualMenu.value || _skinDualMenu.value
        if (_dualMenu.value == active) return
        _dualMenu.value = active
        if (active) ra.closeRaPanel()   // the panel is a single-screen overlay: dual mode shows the details on the top screen instead
        // A game list's layout choices and Layouts' game list rows differ on two screens.
        (_screen.value as? Screen.Games)?.takeIf { it.layoutMenu != null }?.let { _screen.value = it.copy(layoutMenu = null) }
        if (_screen.value is Screen.Settings) settingsUi.refreshSettings()
        topScreen.followTopDetails()
        topScreen.followTopStats()
    }

    /** The drawer's apps: every installed app but the Android games console's, marked when it is in Tools. */
    private fun drawerRows(apps: List<InstalledApp>): List<ToolAppRow> {
        val console = deps.settings.androidConsoleApps.toSet()
        val inTools = deps.settings.toolApps.toSet()
        return apps.filter { it.id !in console }.map { ToolAppRow(it.id, it.label, it.id in inTools) }
    }

    /** The screen's footer mode as App last reported it ([onFooterMode]); HINTS until then, as [LocalFooterMode]'s default. */
    private var footerMode = FooterMode.HINTS

    /**
     * The UI reports the footer mode ([footerModeFor]: a controller or the portrait pad, else touch only), which decides
     * whether Layouts → Page title's Hide is in effect ([launcherChromeFor]).
     */
    fun onFooterMode(mode: FooterMode) {
        footerMode = mode
    }

    /** The launcher screens' chrome in effect: the settings under [footerMode], the rule App draws by. */
    internal fun launcherChrome(): LauncherChrome =
        deps.settings.let { s ->
            launcherChromeFor(s.menuPageTitle, s.menuButtonHints, footerMode, s.menuExtraInfo, s.showNetplayHint, homeStats = s.menuHomeStats)
        }

    private val _nav = MutableStateFlow(NavStep())
    val nav: StateFlow<NavStep> = _nav.asStateFlow()
    val resumeInfo: StateFlow<ResumeInfo> get() = launches.resumeInfo
    private var pendingNav: Pair<NavMotion, Screen>? = null   // a user's A/B into or out of a list, and the screen it was made on

    private val _gameInfo = MutableStateFlow<Map<String, GameInfo>>(emptyMap())
    /** Every game's play time and RetroAchievements progress by library path (menus show the selected one's). */
    val gameInfo: StateFlow<Map<String, GameInfo>> = _gameInfo.asStateFlow()

    private var gameInfoJob: Job? = null
    /** The RetroAchievements panel and screens; it reaches back into this state only through [RaScreenHost]. */
    private val ra = RaScreenController(
        scope, _screen, _selection, _error, deps.settings, loader, deps.achievements,
        RaGamesCache(deps.privateFiles, scope, deps.compute), deps.log,
        host = object : RaScreenHost {
            override fun showNotice(text: String) = this@AppState.showNotice(text)
            override fun showTools() = this@AppState.showTools()
            override fun openSelected() = this@AppState.openSelected()
            override fun refreshResume() = this@AppState.refreshResume()
            override fun refreshGameInfo() = this@AppState.refreshGameInfo()
            override fun raSignedIn(): Boolean = this@AppState.raSignedIn()
            override fun libraryIndex(): LibraryIndex? = this@AppState.libraryIndex.snapshot
            override fun openPicker(title: String, labels: List<String>, current: Int, apply: (Int) -> Unit) =
                settingsUi.openPicker(title, labels, current, apply)
        },
    )
    /** The achievements panel over a game list; null while closed. */
    val raPanel: StateFlow<RaPanel?> = ra.raPanel
    /** The signed-in player's summary beside the RetroAchievements page ([RaScreenController.raProfile]). */
    val raProfile: StateFlow<com.nxredux.mobile.achievements.RaProfile?> = ra.raProfile
    /** The top screen's details and stats pages and the highlighted game's info. Before [init]: its collectors call it. */
    private val topScreen = TopScreenController(
        scope, _screen, _selection, _dualMenu, _gameInfo, deps, ra,
        host = object : TopScreenHost {
            override fun systems(): List<SystemFolder> = systemsCache
            override fun consoleEntries(systems: List<SystemFolder>): List<Listed> = this@AppState.consoleEntries(systems)
            override suspend fun entriesFor(source: ListSource): List<Listed> = this@AppState.entriesFor(source)
            override fun gameInfoStore(): com.nxredux.mobile.scraper.GameInfoStore? = gameInfoStore
            override fun infoFetcher(): InfoFetcher? = infoFetcher
            override fun artworkRef(tag: String, g: GameEntry): LibraryGameRef = this@AppState.artworkRef(tag, g)
        },
    )
    /** The top screen's game details page; null while the top screen shows no game. */
    val topDetails: StateFlow<TopDetails?> get() = topScreen.topDetails
    /** The top screen's console or collection stats page; null while the top screen shows neither. */
    val topStats: StateFlow<TopStats?> get() = topScreen.topStats
    /** Stored ScreenScraper info by game path, for the games highlighted so far: a caption's genre and release. */
    val gameMeta: StateFlow<Map<String, StoredGameInfo>> get() = topScreen.gameMeta
    private var gameInfoDirty = false   // a refresh was asked for while one ran: it runs once more
    private val shelvesCache = HomeShelvesCache()   // Home's shelves while their inputs hold ([shelvesFor])
    private var gameInfoRead = false    // a [refreshGameInfo] pass has read play time: the shelves can go by play time
    private var lastTimes: Map<String, GameTotal> = emptyMap()   // the last play time read that worked ([refreshGameInfo])
    private var lastRa: Map<String, RaProgress> = emptyMap()      // likewise RetroAchievements progress
    /** The Game Switcher: its cards, its return after a game resumed from it, and Y Remove. */
    private val switcher: SwitcherController = SwitcherController(
        scope, _screen, _selection, deps.settings, deps.userData, deps.switcherReturns,
        host = object : SwitcherHost {
            override fun kidsLocked(): Boolean = kidsUi.kidsLocked
            override fun navStep(motion: NavMotion) = this@AppState.navStep(motion)
            override fun resolveOne(path: String): Listed? = this@AppState.resolveOne(path)
            override fun launch(entry: Listed, resumeSlot: Int?, fromSwitcher: Boolean, fresh: Boolean): Job? = launches.launch(entry, resumeSlot, fromSwitcher = fromSwitcher, fresh = fresh, continuing = !fresh)
            override fun menuWithItsRow(): Pair<Screen, ListSelection> =
                menuScreen().let { m -> m to menuRowOf(m) }
            override fun publishHomeView() = homeTab.publishHomeView()
            override fun swapMainRows() = this@AppState.swapMainRows()
            override fun refreshResume() = this@AppState.refreshResume()
            override suspend fun refreshMainRows() = this@AppState.refreshMainRows()
            override suspend fun rebuildMainRows() = this@AppState.rebuildMainRows()
            override val recentsLock: Mutex get() = this@AppState.recentsLock
        },
    )
    /** Game launches, resume and a Minimal page's open. */
    private val launches: LaunchController = LaunchController(
        scope, _screen, _selection, _error, deps, sidecars, switcher,
        host = object : LaunchHost {
            override fun kidsLocked(): Boolean = kidsUi.kidsLocked
            override fun dropPushedTab() = this@AppState.dropPushedTab()
            override fun launchApp(entry: Listed): Boolean = androidApps.launchApp(entry)
            override fun showNotice(text: String) = this@AppState.showNotice(text)
            override fun rememberMenuSelection() = this@AppState.rememberMenuSelection()
            override suspend fun moveInIndex(game: GameEntry, from: SystemFolder, toTag: String): Boolean = this@AppState.moveInIndex(game, from, toTag)
            override suspend fun refreshOpenList(select: String?) = this@AppState.refreshOpenList(select)
            override fun openMainRow(s: Screen.Systems, index: Int, forward: NavMotion): Job? = this@AppState.openMainRow(s, index, forward)
        },
    )
    /** A Minimal page's folder being opened ([LaunchController.openMinimal]); null otherwise. */
    val minimalOpening: StateFlow<MinimalOpening?> get() = launches.minimalOpening
    private var hideJob: Job? = null       // A-in-the-hidden-list unhide in flight
    private val recents = deps.recents ?: RecentsRecorder(deps.userData, scope)
    private val recentsLock get() = recents.lock   // serialises recent.txt read-modify-writes, process-wide
    private var skeletonFor: ContentRef? = null   // the home the skeleton was ensured for (once per home per instance)
    private var offlineHomeSaid: ContentRef? = null   // the home last found offline, its error already shown (cleared once it reads again)
    private var homeLateMountUsed = false             // that home already had its one "mounted, root not back yet" pass
    private var foregroundJob: Job? = null            // onForeground's check (and the scan it starts)
    private var homeCheck: Job? = null                // the index start's home probe in flight (shared, see afterIndexStart)
    private var homeCheckFor: ContentRef? = null
    /** Settings' rows and values, and the pages with state of their own (Button layout, About's GPU, GPU driver). */
    private val settingsContent: SettingsRows = SettingsRows(
        scope, _screen, _error, _dualMenu, deps, kids,
        host = object : SettingsRowsHost {
            override fun home(): ContentRef? = this@AppState.home()
            override fun dataMoved(): Boolean = this@AppState.dataMoved()
            override fun mediaPath(media: ContentRef): String = this@AppState.mediaPath(media)
            override fun systems(): List<SystemFolder> = systemsCache
            override fun indexSnapshot(): LibraryIndex? = libraryIndex.snapshot
            override fun offlineHome(): ContentRef? = offlineHomeSaid
            override fun lateMount(): List<String> = lateMountHere
            override fun fileAccessOff(ref: ContentRef): Boolean = this@AppState.fileAccessOff(ref)
            override fun androidGames(): List<InstalledApp>? = installedCache?.let { this@AppState.androidGames(it) }
            override fun unassignedSystem(): SystemFolder? = this@AppState.unassignedSystem()
            override fun hiddenPaths(): List<String> = hiddenPaths
            override fun updateLabel(): String? = _updateLabel.value
            override fun kidsRows(): List<SettingsRow> = kidsUi.kidsRows()
            override fun kidsSetEnabled(on: Boolean) = kidsUi.kidsSetEnabled(on)
            override fun shortcutRows(): List<SettingsRow> = controlBindings.shortcutRows()
            override fun systemButtonRows(): List<SettingsRow> = controlBindings.systemButtonRows()
            override fun shortcutSet(): ShortcutSet = controlBindings.shortcutSet()
            override fun setTouch(slot: TouchSlot, action: ShortcutAction?) = controlBindings.setTouch(slot, action)
            override fun rebuildHomeTab() = this@AppState.rebuildHomeTab()
            override suspend fun refreshMainRows() = this@AppState.refreshMainRows()
            override fun gameListLayout(): Pair<MenuStyle, MenuOrientation> = this@AppState.gameListLayout()
            override fun gameListTurns(style: MenuStyle): Boolean = this@AppState.gameListTurns(style)
            override fun netplayAvailable(): Boolean = netplay != null
            override fun showNotice(text: String) = this@AppState.showNotice(text)
            override fun refreshSettings() = settingsUi.refreshSettings()
            override fun refreshSettingsOn(vararg sections: SettingsSection) = this@AppState.refreshSettingsOn(*sections)
            override fun showSettingsOn(section: SettingsSection, kind: SettingsKind) = settingsUi.showSettingsOn(section, kind)
            override fun openPicker(title: String, labels: List<String>, current: Int, apply: (Int) -> Unit) =
                settingsUi.openPicker(title, labels, current, apply)
        },
    )
    /** About's GPU name once its query answered; null before. */
    internal val gpu: StateFlow<String?> get() = settingsContent.gpu
    /** The button layout editor saved or left. */
    fun onSkinLayoutEdited() = settingsContent.onSkinLayoutEdited()
    /** Settings' pages, the chooser, a row's info popup and the row edit. */
    private val settingsUi: SettingsController = SettingsController(
        _screen, _selection, _rowEdit, deps, kidsAvailable = kids != null, content = settingsContent,
        host = object : SettingsHost {
            override fun kidsToolsAllowed(): Boolean = kidsUi.kidsToolsAllowed
            override fun loaderActive(): Boolean = loader.active
            override fun minimalPending(): Boolean = launches.minimalPending
            override fun secondConnected(): Boolean = this@AppState.secondConnected
            override fun select(index: Int) = this@AppState.select(index)
            override fun openSelected() = this@AppState.openSelected()
            override fun back(): Boolean = this@AppState.back()
            override fun rememberMenuSelection() = this@AppState.rememberMenuSelection()
            override fun refreshInstalled() = this@AppState.refreshInstalled()
            override fun syncArtLayout() = this@AppState.syncArtLayout()
            override fun rebuildHomeTab() = this@AppState.rebuildHomeTab()
            override fun clearShortcuts() = controlBindings.clearShortcuts()
            override fun resetSystemButtons() = controlBindings.resetSystemButtons()
        },
    )
    val chooser: StateFlow<ValueChooser?> get() = settingsUi.chooser
    fun chooseValue(index: Int) = settingsUi.chooseValue(index)
    fun closeChooser() = settingsUi.closeChooser()
    fun chooseSetting(row: Int, index: Int) = settingsUi.chooseSetting(row, index)
    fun openSettingPicker(row: Int) = settingsUi.openSettingPicker(row)
    val settingInfo: StateFlow<SettingInfoPopup?> get() = settingsUi.settingInfo
    val settingInfoTruncated: StateFlow<String?> get() = settingsUi.settingInfoTruncated
    fun onSettingInfoTruncated(info: String, truncated: Boolean) = settingsUi.onSettingInfoTruncated(info, truncated)
    fun showSettingInfo(row: Int) = settingsUi.showSettingInfo(row)
    fun closeSettingInfo() = settingsUi.closeSettingInfo()
    fun openSettingsMenu() = settingsUi.openSettingsMenu()
    internal fun settingsSections(): List<SettingsSection> = settingsUi.settingsSections()
    fun openSettings(section: SettingsSection) = settingsUi.openSettings(section)
    fun onSettingsSplit(split: Boolean) = settingsUi.onSettingsSplit(split)
    /** [section]'s page as the unfocused right pane shows it while its category is highlighted on the left. */
    fun settingsPreview(section: SettingsSection): Screen.Settings = settingsUi.settingsPage(section)
    fun settingsCategoryTap(index: Int) = settingsUi.settingsCategoryTap(index)
    fun settingsPreviewTap(row: Int) = settingsUi.settingsPreviewTap(row)

    /** Onboarding's pages, first setup's step count and the run's flags. Before [init]: a load in it reads them. */
    private val onboardingUi: OnboardingScreensController = OnboardingScreensController(
        scope, _screen, _selection, _error, deps, onboarding, kids, loader, settingsContent, settingsUi, kidsUi,
        host = object : OnboardingHost {
            override fun select(index: Int) = this@AppState.select(index)
            override fun showNotice(text: String) = this@AppState.showNotice(text)
            override fun showPickFolder() = this@AppState.showPickFolder()
            override fun showAddFirstExtra() = this@AppState.showAddFirstExtra()
            override fun showPermissions() = this@AppState.showPermissions()
            override fun homePickShown(): Boolean = this@AppState.homePickShown()
            override fun homePageShown(): Boolean = folders.homePage != null
            override fun resetFolderSummaries() = folders.reset()
            override fun showDeviceSync(sync: DeviceSyncController) = this@AppState.showDeviceSync(sync)
            override fun clearSyncSetup() { _syncSetup.value = null }
            override fun rescanArrivedRoms(): Boolean = if (romsPending) { romsPending = false; rescan(); true } else false
            override fun rebuildHomeTab() = this@AppState.rebuildHomeTab()
            override suspend fun rebuildMainRows() = this@AppState.rebuildMainRows()
            override fun showSystemsNow(category: MenuCategory) = this@AppState.showSystemsNow(category)
            override fun gameListLayout(): Pair<MenuStyle, MenuOrientation> = this@AppState.gameListLayout()
            override fun gameListTurns(style: MenuStyle): Boolean = this@AppState.gameListTurns(style)
            override fun systems(): List<SystemFolder> = systemsCache
            override fun artwork(): ArtworkScreenController? = artworkScreens
            override fun importSaves(source: ImportSource, progress: (String) -> Unit, done: (ImportResult) -> Unit) {
                this@AppState.importSaves(source, progress, done)
            }
        },
    )
    /** Whether the main menu's pad skin shows now ([OnboardingScreensController.menuPadShown]). */
    fun menuPadShown(): Boolean = onboardingUi.menuPadShown()
    /** A folder step's page title and whether B goes back from it. */
    fun setupChrome(): Pair<String, Boolean> = onboardingUi.setupChrome()
    /** The device as App sees it: portrait or not, a controller or not. */
    fun onPadContext(portrait: Boolean, controller: Boolean) = onboardingUi.onPadContext(portrait, controller)
    /** A or a tap on onboarding row [index]: that step's choice. */
    fun onboardingRow(index: Int) = onboardingUi.onboardingRow(index)
    fun onboardingSettingEvent(event: NxSettingEvent) = onboardingUi.onboardingSettingEvent(event)
    fun onboardingDraftUser(text: String) = onboardingUi.onboardingDraftUser(text)
    fun onboardingDraftPassword(text: String) = onboardingUi.onboardingDraftPassword(text)
    fun onboardingDraftKey(text: String) = onboardingUi.onboardingDraftKey(text)
    /** The Bottom screen step's button on the second screen was tapped. */
    fun onboardingBottomScreenOnSecond() = onboardingUi.onboardingBottomScreenOnSecond()

    /** The library changed since the last [load] (folders added or removed, games hidden, unhidden or reassigned): leaving Library reloads. */
    private var libraryDirty = false

    /** The app's settings, exposed for the UI (deps stays private). */
    val settings: AppSettings get() = deps.settings

    /** The Cheats & Patches tool, for the screen that renders its progress (null on a platform without it). */
    val cheatDb: CheatDbController? get() = deps.cheatDb

    /** The Device Sync tool, for its screen (null on a platform without it). */
    val deviceSync: DeviceSyncController? get() = deps.deviceSync

    /** The RetroAchievements tool, for its screens (null on a platform without it). */
    val achievements: RaController? get() = deps.achievements

    /** This instance's [UpdateController.hostBusy], kept so [releaseUpdater] lets go of it only while still the updater's. */
    private val updateHost: () -> String? = { updateHostBusy() }

    /** The library index in memory and stored, its lock and the lookups built from it; art results' flush shows here. Declared before
     * [init]: its collectors run during construction on the main dispatcher. */
    private val libraryIndex = LibraryIndexHolder(scope, deps.indexStore, deps.compute) { shown ->
        afterIndexChange(shown, IndexRefresh(rows = true, openList = true, ui = true))
    }

    init {
        switcher.restoreReturn()
        deps.updater?.let { u ->
            u.hostBusy = updateHost
            // About's update row follows the updater (a check, the download's percent, the wait), as the GPU row follows its
            // query; by its label, so a download's many progress reports redraw the page once a percent.
            scope.launch {
                u.state.collect { st ->
                    val label = appUpdateLabel(st)
                    if (label == _updateLabel.value) return@collect
                    _updateLabel.value = label
                    refreshSettingsOn(SettingsSection.SYSTEM)
                }
            }
        }
        // A started game was recorded: a first launch makes the Recently Played row appear, also when launched from the
        // main menu itself (a pin row, the switcher over it). Ends with the scope, so the recorder never holds this state.
        scope.launch {
            recents.written.collect {
                refreshMainRows()
                refreshGameInfo()   // a game was just played
            }
        }
        // The achievements panel follows the game list's selection, and closes off the game list.
        scope.launch { _selection.collect { ra.followRaPanel(); topScreen.followTopDetails(); topScreen.followTopStats(); topScreen.followGameMeta() } }
        // The top screen's details: new play time re-reads the shown game; its achievements land in place. Stats too.
        scope.launch { _gameInfo.collect { topScreen.followTopDetails(reread = true); topScreen.followTopStats() } }
        scope.launch {
            ra.detailsRa.collect { r ->
                topScreen.onDetailsRa(r)
            }
        }
        scope.launch {
            _screen.collect {
                topScreen.followTopDetails()
                topScreen.followTopStats()
                topScreen.followGameMeta()
                if (it !is Screen.Games) { ra.closeRaPanel() } else { ra.followRaPanel() }
                if (it !is Screen.Games) closeArtPanel()   // as the achievements panel: only over a game list
            }
        }
        deps.achievements?.let { c ->
            scope.launch {
                var wasBusy = false
                c.state.collect { s ->
                    // A job (Download all, a sync, sign-in, sign-out, a reset) just ended: the menus' achievement info
                    // reads the cache again, so a game list shows the new progress without a game played first; the
                    // Achievements snapshot too.
                    val busy = s !is RaState.Idle
                    if (wasBusy && !busy) { refreshGameInfo(); ra.raDataChanged() }
                    wasBusy = busy
                    when (_screen.value) {
                        // Busy shows no rows; back to idle it lands on the row that started the job. The settings page's rows follow.
                        is Screen.RetroAchievements, is Screen.RaSettings -> ra.onRaState(s, c)
                        is Screen.Onboarding -> if (s is RaState.Idle) onboardingUi.onRaIdleOnboarding(s.status)   // onboarding's sign-in ended
                        else -> Unit
                    }
                }
            }
            // The download's progress: its page's rows come and go (a finished game leaves), the RetroAchievements page gains its row.
            scope.launch {
                c.download.collect { d ->
                    when (_screen.value) {
                        is Screen.RaDownload -> _selection.value = _selection.value.clampedTo(d?.items?.size ?: 0)
                        is Screen.RetroAchievements -> _selection.value = _selection.value.clampedTo(raRows(c.state.value, d).size)
                        else -> Unit
                    }
                }
            }
            scope.launch {
                c.messages.collect { m ->
                    when (m) {
                        is RaMessage.Notice -> showNotice(m.text)
                        is RaMessage.Failure -> _error.value = m.text
                    }
                }
            }
        }
        deps.cheatDb?.let { c ->
            scope.launch {
                c.state.collect { s ->
                    when (_screen.value) {
                        is Screen.CheatDb -> _selection.value = _selection.value.clampedTo(cheatDbRows(s).size)
                        else -> Unit
                    }
                }
            }
            scope.launch {
                c.messages.collect { m ->
                    when (m) {
                        is CheatDbMessage.Notice -> showNotice(m.text)
                        is CheatDbMessage.Failure -> _error.value = m.text
                    }
                }
            }
        }
        deps.deviceSync?.let { c ->
            scope.launch { c.notices.collect { showNotice(it) } }
            // Files arrived (saves, states, their pictures): Continue's resume point and slot picture are read again. Game
            // info files may be among them: a cached absence would have the top screen fetch one and write over it.
            scope.launch { c.received.collect { gameInfoStore?.forgetCached(); topScreen.forgetGameMeta(); refreshMainRows() } }
            // Collections changed (merged by this phone's writer, or pushed by a peer phone into its home `Collections/`): a
            // store copy takes in a daemon-written home file, then the tab is listed again and those counts redone.
            scope.launch {
                c.collectionsArrived.collect { synced ->
                    synced.received.forEach { collections.storeFollowsHome(it) }
                    collectionCounter.refreshCollectionNames()
                    (synced.written + synced.received).forEach { collectionCounter.invalidateCollectionCounts(it) }
                    refreshMainRows()
                }
            }
            // ROM files arrived: the main menu with nothing over it (the Games tab too) rescans at once; anywhere else (the
            // tool itself) waits until it is left.
            scope.launch { c.romsArrived.collect { if (menuFree(_screen.value)) rescan() else { romsPending = true; libraryDirty = true } } }
            // Pending ROMs scan as soon as the main menu shows again with nothing over it, however the user got there: B on the tool,
            // Home, Back from a list the sync ended under, the end of a Loading the sync ended during. B on the tool and
            // leaving setup's sync step take them first themselves.
            scope.launch {
                combine(_screen, _panelOpen) { s, _ -> s }.collect { s -> if (romsPending && menuFree(s)) { romsPending = false; rescan() } }
            }
        }
    }

    fun dismissNotice() { noticeJob?.cancel(); noticeSticky = false; _notice.value = null }

    /** A press's dismissal: a sticky notice (a save transfer's progress) stays until the next notice replaces it. */
    private fun dismissNoticeOnPress() { if (!noticeSticky) dismissNotice() }

    /**
     * Shows [text] and clears it after [NOTICE_MILLIS] unless a newer notice or a dismissal came first. [sticky]: no
     * timeout, and a press does not clear it; only the next notice (or [dismissNotice]) does.
     */
    private fun showNotice(text: String, sticky: Boolean = false) {
        noticeJob?.cancel()
        _notice.value = text
        noticeSticky = sticky
        if (sticky) return
        noticeJob = scope.launch {
            delay(NOTICE_MILLIS)
            if (_notice.value == text) _notice.value = null
        }
    }

    /**
     * What follows an index change ([mutateIndex]): the playable cache always; then the collection counts, the menu's rows
     * and the open list (its highlighted game kept) as asked. [ui]: on [uiContext], for a caller off it.
     */
    private data class IndexRefresh(val counts: Boolean = false, val rows: Boolean = false, val openList: Boolean = false, val ui: Boolean = false)

    /**
     * The one way the library index changes ([LibraryIndexHolder.mutate]): [change] gets the index in memory and returns
     * the next one (null or the same instance: no change, and nothing else runs). Then, unless [refresh] is null, the
     * steps it asks ([afterIndexChange]). Returns the new index, or null for no change.
     */
    private suspend fun mutateIndex(
        refresh: IndexRefresh?,
        persistNow: Boolean = true,
        change: suspend (LibraryIndex?) -> LibraryIndex?,
    ): LibraryIndex? {
        val next = libraryIndex.mutate(persistNow, change) ?: return null
        refresh?.let { afterIndexChange(next, it) }
        return next
    }

    private suspend fun afterIndexChange(index: LibraryIndex, r: IndexRefresh) {
        // Both are a pass over every game: built on [AppDeps.compute], so an art flush or a rename never walks the library on
        // the main thread; the lookup is cached for the systems list taken, so the rows built next find it ready.
        val gen = takeSystemsGen()
        val hidden = hiddenPaths.toSet()
        val systems = withContext(deps.compute) { index.playableSystems(hidden) }
        suspend fun steps() {
            if (offerSystems(gen, systems)) libraryIndex.playableOffMain(systems)
            if (r.counts) collectionCounter.invalidateCollectionCounts()
            if (r.rows) rebuildMainRows()
            if (r.openList) refreshOpenList(select = (_screen.value as? Screen.Games)?.entries?.getOrNull(_selection.value.index)?.game?.path)
        }
        if (r.ui) withContext(uiContext) { steps() } else steps()
    }

    /** The app left the foreground: art results still waiting are written now, not left to a debounce a kill would lose. */
    fun onBackground() {
        kidsUi.kidsCloseUnlock()   // the unlock PIN or sheet never waits behind another app for the kid to come back to
        if ((_screen.value as? Screen.Settings)?.section == SettingsSection.SHORTCUTS) { controlBindings.closeShortcuts(); settingsUi.refreshSettings() }
        scope.launch { libraryIndex.flushArt() }
    }

    /**
     * The home pick, or first, once, the All files access step: only where the permission is declared and not granted,
     * and only until it was answered ([AppSettings.allFilesAsked]).
     */
    private fun showPickFolder() {
        val ask = !deps.settings.allFilesAsked && permissionsStep().let { it.allFiles == false || it.notifications == false }
        if (ask) { onboardingUi.allFilesStepShown = true; showPermissions(); return }
        // No All files access: the home is the app's Android/media folder, nothing to pick. Once per run (a failed scan of it picks).
        val media = deps.homes?.media
        if (media != null && onboardingUi.inFirstSetup() && !onboardingUi.mediaHomeTaken && !homePickShown()) return takeMediaHome(media)
        showPickFolderScreen()
    }

    /** The home pick's page itself, no step before it. */
    private fun showPickFolderScreen() {
        _screen.value = Screen.PickFolder
        _selection.value = ListSelection(0, 1)
    }

    /** First setup's folder steps on [media] as after a pick ([setHome]), the saved home left unset: its scan, then the home page. */
    private fun takeMediaHome(media: ContentRef) {
        onboardingUi.mediaHomeTaken = true
        onboardingUi.firstSetup = true
        if (deps.settings.onboardingDone == false) deps.settings.onboardingStep = FOLDER_STEPS
        rescan(then = { folders.showHomePage(media) })
    }

    /** All files access is on (or there is no media home): first setup shows the home pick; without it the media home is used. */
    private fun homePickShown(): Boolean = deps.homes?.media == null || deps.allFilesAccess?.takeIf { it.declared }?.granted() == true

    /** B from the media home's first page (the home page, or the ROMs folder offer without one): back past the skipped pick, which runs again going on. */
    private fun backPastMediaHome() {
        onboardingUi.mediaHomeTaken = false
        folders.homePage = null
        onboardingUi.firstSetup = false
        if (deps.settings.onboardingStep == FOLDER_STEPS) deps.settings.onboardingStep = null
        onboardingUi.backFromHomePick()
    }

    /**
     * The no-access line on the media home's first folder-steps page (the home page, or the ROMs folder offer when the
     * home has no game): where the games folder is. Null on every other page.
     */
    fun setupNote(): String? {
        val media = deps.homes?.media ?: return null
        if (!onboardingUi.mediaHomeTaken || !onboardingUi.inFirstSetup()) return null
        val first = when (val s = _screen.value) {
            is Screen.FolderSummary -> s.extraId == Recents.HOME
            Screen.AddFirstExtra -> folders.homePage == null
            else -> false
        }
        return if (first) "Your games folder is ${mediaPath(media)}. Put ROMs and BIOS there with a file manager. Uninstalling the app removes it." else null
    }

    /** The Permissions page's state now: each permission this device uses, granted or not. */
    private fun permissionsStep(): Screen.PermissionsStep {
        val files = deps.allFilesAccess?.takeIf { it.declared }
        return Screen.PermissionsStep(allFiles = files?.granted(), notifications = deps.notificationAccess?.granted())
    }

    /** The Permissions page, its selection kept (on the first row when it first shows). */
    private fun showPermissions(keep: Boolean = false) {
        val s = permissionsStep()
        _screen.value = s
        val rows = permissionRows(s).size
        _selection.value = ListSelection(if (keep) _selection.value.index.coerceIn(0, rows - 1) else 0, rows)
    }

    /**
     * The main menu's tabs ([buildMenuTabs]): Home (the last game, the stats as last computed, the game and tool pins),
     * consoles, collections (as last listed, with the counts cached so far), Tools. No tree access: an index start shows it
     * before any SAF call. One rebuild at a time ([rowsLock]); the collections not counted yet are counted after it.
     * Returns once a build begun after this call has finished, so the rows reflect whatever the caller just changed.
     */
    private suspend fun rebuildMainRows() {
        buildMainRowsMerged()
        collectionCounter.fillCollectionCounts()
    }

    /** [rebuildMainRows], then the menu shows the new rows. */
    private suspend fun refreshMainRows() {
        rebuildMainRows()
        swapMainRows()
    }

    private var rowsAsked = 0L   // main-thread confined, as the rows are
    private var rowsBuiltFor = 0L

    /**
     * One build serves every call made before it began: a return from a game fires several triggers at once (the
     * foreground, the installed apps, a sync's arrivals), and a build per trigger would re-read the pins, the recents and
     * every game's resume info and frame for each. A call made while a build runs waits for the next one, which serves
     * all of those.
     */
    private suspend fun buildMainRowsMerged() {
        val ask = ++rowsAsked
        rowsLock.withLock {
            if (rowsBuiltFor >= ask) return@withLock
            val covers = rowsAsked
            buildMainRows()
            rowsBuiltFor = covers
        }
    }

    /** [games]' hidden save state frames by path ([UserDataStore.hiddenStateShot]); an Android game has none. */
    private suspend fun homeShots(games: List<Listed>): Map<String, String> =
        games.distinctBy { it.game.path }.filter { !it.system.android && !it.system.unassigned }
            .mapNotNull { e -> deps.userData.hiddenStateShot(e.system.tag, e.game.fileName)?.let { e.game.path to it } }.toMap()

    private suspend fun buildMainRows() {
        pins = deps.userData.readPins()
        val recents = deps.userData.readRecents()
        // Continue: the first recent that resolves, whether or not it can resume.
        val lookup = playableOffMain()
        val lastGame = recents.firstNotNullOfOrNull { resolveIn(lookup, it.path) }
        val tools = toolRows()
        // The Android pins after the ROM pins, resolved against the console (one that left it is skipped, and kept).
        // Each game once (a hidden copy's line opens its home game, [resolveDistinct]); the recent set also holds the
        // resolved games' own paths, so a home game played as its hidden copy is not "not started".
        val playedGames = resolveDistinct(lookup, recents.map { it.path })
        val played = recents.map { it.path }.toSet() + playedGames.map { it.game.path }
        val pinned = resolveDistinct(lookup, pins.map { it.path }) + androidPinned()
        // Each game's hidden save state's frame (Save & Quit's), for Continue, the pins and the shelves' played games: only
        // a played game has one, so the recents and the pins are every candidate.
        val shots = homeShots(listOfNotNull(lastGame) + pinned + playedGames)
        val last = lastGame?.let { e -> MainRow.Continue(e, deps.userData.resumeInfo(e.system.tag, e.game.fileName), shots[e.game.path]) }
        val inputs = HomeInputs(last, pinned, pinnedToolRows(toolPins, tools + homeOnlyAppRows(tools)), played, shots)
        val built = homeTab.homeFor(kidsUi.kidsHomeInputs(inputs))   // the stats as they are now, after the reads
        homeTab.homeInputs = inputs
        allMenuTabs = buildMenuTabs(
            home = built,
            games = allGames(),   // built once per library state, not per rebuild
            // One row per display name: SFC and SUPA are one "Super Nintendo ES" row, as NX Redux collates sibling folders.
            // The Android games console sits among them by name; it is never one of [systemsCache]'s.
            consoles = withAndroidConsole(
                systemsCache.filter { !it.unassigned }.groupBy { it.displayName }.values.map { MainRow.System(it) },
                androidConsole.also { menuAndroidConsole = it },
            ),
            collections = collectionCounter.collectionNames.map { MainRow.Collection(it, collectionCounter.countOf(it)) },   // as counted so far; the rest come after
            tools = tools,
            homeStyle = homeStyle(),
        )
        homeTab.noteHomeBuilt(built)
    }

    /** Rebuilds the Home tab alone from what it was last built from, with the stats, game info and frame as they are now (no I/O). */
    private fun rebuildHomeTab() {
        val built = homeTab.homeFor(kidsUi.kidsHomeInputs(homeTab.homeInputs ?: return))   // the shelves too: the frame may have changed
        allMenuTabs = allMenuTabs.map { if (it.category == MenuCategory.HOME) MenuTab(MenuCategory.HOME, homeTabRows(built, homeStyle())) else it }
        homeTab.noteHomeBuilt(built)
        swapMainRows()
    }

    /**
     * Home's shelves for [inputs] now, in every layout on every frame: the library, game info and today, [homeShelves].
     * None before the first game info read ([gameInfoRead]): a cold start would otherwise show every game as Not
     * started, then swap the shelves; that read's rebuild adds them once. Kept between rebuilds ([shelvesCache]).
     */
    private fun shelvesFor(inputs: HomeInputs): List<Pair<HomeShelf, List<Listed>>> {
        if (!gameInfoRead) return emptyList()
        val now = clockNow()
        // Continue's game and every pinned one are on Home already.
        val exclude = (inputs.games.map { it.game.path } + listOfNotNull(inputs.last?.entry?.game?.path)).toSet()
        // A shelf Layouts hides drops after the pick, so its games never move to another shelf.
        val hidden = HomeShelf.entries.filterTo(HashSet()) { !settingsContent.shelfShown(it) }   // a hidden shelf takes no game: the others may show them
        return shelvesCache.shelves(systemsCache, _gameInfo.value, inputs.recent, exclude, now, deps.calendar.dayStart(now), hidden)
    }

    /**
     * Home's body as laid out: a new frame rebuilds the Home tab (its slots, strip lines and moves follow it;
     * the focus stays on its row, [swapMainRows]). A re-report of the same frame swaps in tabs a menu held back
     * ([swapMainRows] waits while one is open); closing the menu does that too ([ContextMenuHost.menuClosed]).
     */
    fun onHomeLayout(frame: HomeFrame) {
        if (frame == homeTab.homeFrame) { val s = _screen.value as? Screen.Systems ?: return; if (s.menu == null && s.tabs != menuTabs) swapMainRows(); return }
        homeTab.homeFrame = frame
        rebuildHomeTab()
    }

    /**
     * The main menu on [category]'s tab; a gone one resolves to the next visible tab ([resolveMenuTab]). While a hidden tab
     * is pushed, that tab ([pushedScreen]); one that can no longer be pushed (shown again, or emptied) lands on the tab underneath.
     * The Games tab is its game list ([tabScreen]); every other tab a [Screen.Systems].
     */
    private fun menuScreen(category: MenuCategory = menuCategory): Screen {
        val wasPushed = pushedTab != null
        pushedScreen()?.let { return it }
        val tabs = menuTabs
        return tabScreen(tabs, resolveMenuTab(tabs, if (wasPushed) menuCategory else category), hiddenMenuTabs(kidsMenuTabs, tabs))
    }

    /** The main menu on [tabs]' tab [index]: the Games tab as a game list of its games under the tab row, any other as a [Screen.Systems]. */
    private fun tabScreen(tabs: List<MenuTab>, index: Int, hidden: List<MenuCategory>): Screen {
        val tab = tabs.getOrNull(index)?.takeIf { it.category == MenuCategory.GAMES }
            ?: return Screen.Systems(tabs, index, hidden = hidden)
        return Screen.Games(ListSource.AllGames, tab.games, tabBar = TabBar(tabs.map { it.category }, index, hidden = hidden))
    }

    /**
     * The pushed hidden tab as a one-tab menu (the Games tab: its game list under the menu's title row), or null (dropping
     * [pushedTab]) when it is shown again or has no content left.
     */
    private fun pushedScreen(): Screen? {
        val c = pushedTab ?: return null
        val tab = kidsMenuTabs.firstOrNull { it.category == c }?.takeIf { menuTabs.none { v -> v.category == c } }
        if (tab == null) { pushedTab = null; return null }
        if (c == MenuCategory.GAMES) return Screen.Games(ListSource.AllGames, tab.games, tabBar = TabBar(listOf(c), 0, pushed = true))
        return Screen.Systems(listOf(tab), 0, pushed = true)
    }

    /**
     * Opens [category], a tab Layouts hides that has content, over the main menu (the handheld's pushTabOverTab): its rows
     * in its own layout, orientation and background, on its first item. Only from the menu itself: never over a pushed
     * tab, a context menu or a visible tab, and never Home (only Games, Consoles, Collections or Tools push). From the
     * Games tab too. B ([popHiddenTab]) returns to the tab underneath, focus where it was (the Games tab: on its game).
     */
    internal fun openHiddenTab(category: MenuCategory) {
        if (category == MenuCategory.HOME) return
        val s = _screen.value
        if (!menuFree(s) || s.menuPushed) return
        if (menuTabs.any { it.category == category } || kidsMenuTabs.none { it.category == category }) return
        rememberMenuSelection()
        pushedTab = category
        tabSelections.remove(category)
        navStep(NavMotion.FORWARD)
        showMenu(menuScreen())
    }

    /** B on a pushed tab: the tab underneath, on its remembered row. */
    private fun popHiddenTab() {
        pushedTab = null
        navStep(NavMotion.BACK)
        showMenu(menuScreen(menuCategory))
    }

    /**
     * Ends a push without B (a launch): [pushedTab] cleared, and a pushed menu still on screen becomes the tab underneath
     * in place (no slide), so the screen and [pushedTab] always agree: a stale pushed screen would resolve its own
     * category later ([swapMainRows]) instead of [menuCategory]. A pushed switcher snapshot is dropped too ([SwitcherController.closeSwitcher]
     * then lands on the menu as it is now).
     */
    private fun dropPushedTab() {
        if (pushedTab == null) return
        pushedTab = null
        switcher.dropPushedSnapshot()
        if (_screen.value.menuPushed) showMenu(menuScreen(menuCategory))
    }

    /** On the main menu: remember the current tab and its row (the Games tab: its game too, [gamesTabGame]). */
    private fun rememberMenuSelection() {
        val s = _screen.value
        val c = s.menuTab ?: return
        // Pushed, this writes the hidden tab's row too: harmless, as each push clears it ([openHiddenTab]) and opens on the first item.
        tabSelections[c] = _selection.value
        if (s is Screen.Games) gamesTabGame = s.entries.getOrNull(_selection.value.index)?.game?.path
        if (c == MenuCategory.HOME && s is Screen.Systems) homeTab.rememberHomeRow(s.rows, _selection.value.index)
        if (!s.menuPushed) menuCategory = c   // a pushed tab never becomes the tab underneath
    }

    /**
     * Shows [menu] with its tab's remembered row, clamped. Over START's open prompt (a rebuild in place: a foreground,
     * a new Home frame, counts landing) the prompt and its draft stay; closing it is the prompt's own keys.
     */
    private fun showMenu(menu: Screen, at: Int = -1) {
        if (kidsUi.kidsLocked) return kidsUi.showKidsHome()   // Kids Mode while Locked: the tiles screen is the main menu
        val prev = _screen.value
        val screen = keptSearch(prev, menu)
        val category = screen.menuTab
        // Another tab (or the menu from another screen): its grid reports its own columns on its first layout (the Games
        // tab's list its rows on a page too). The same tab rebuilt in place keeps them, as its grid stays laid out.
        if (!prev.isMainMenu || prev.menuTab != category) {
            gridColumns = 0
            if (screen is Screen.Games) pageRows = 0
        }
        _screen.value = screen
        homeTab.publishHomeView()   // the Home rows on screen may be the ones last built now
        if (!screen.menuPushed) category?.let { menuCategory = it }
        val count = (screen as? Screen.Games)?.entries?.size ?: (screen as Screen.Systems).rows.size
        _selection.value = if (at >= 0) ListSelection(at, count).clampedTo(count)
            else when (screen) {
                is Screen.Games -> rememberedGame(screen.entries)
                else -> rememberedRow(category ?: menuCategory, (screen as Screen.Systems).rows)
            }.clampedTo(count)
        if (category == MenuCategory.HOME && screen is Screen.Systems) homeTab.noteHomeFocus(_selection.value.index, screen.rows)
        refreshResume()   // Continue's A Resume or A Play; the Games tab's highlighted game
        homeTab.menuShown(category)   // the menu's first show computes Home's stats, as does Home after a sign-in or out
        maybeAskAndroidGames()
        // The menu came up (not rebuilt in place, which a count landing does while the install-permission screen is up):
        // the daily check, or a held install's retry. A foreground over it does the same ([onForeground]).
        if (!prev.isMainMenu && screen.isMainMenu && screen.contextMenu == null && !onboardingUi.firstSetup) updaterOnMainMenu()
    }

    /**
     * [menu] with START's open prompt and draft of [prev] (the main menu rebuilt in place), on either kind of tab. The
     * Games tab's [Screen.Games.search] is that prompt ([openSearch]); a game list's own search is never carried. The
     * Games tab's open Layout dropdown stays open on its choice too.
     */
    private fun keptSearch(prev: Screen, menu: Screen): Screen {
        if (prev is Screen.Games && prev.isTab && prev.layoutMenu != null && menu is Screen.Games && menu.isTab) {
            // On a choice the dropdown still lists ([applyDualMenu] closes it when the choices change, so this only guards).
            return menu.copy(layoutMenu = prev.layoutMenu.coerceIn(0, GameListLayouts.choices(_dualMenu.value).lastIndex))
        }
        if (prev is Screen.Systems && prev.layoutMenu != null && menu is Screen.Systems && prev.category == menu.category) {
            val choices = prev.category?.let(MenuTabLayouts::choices).orEmpty()
            if (choices.isNotEmpty()) return menu.copy(layoutMenu = prev.layoutMenu.coerceIn(0, choices.lastIndex))
        }
        val draft = when (prev) {
            is Screen.Systems -> prev.search
            is Screen.Games -> prev.search.takeIf { prev.isTab }
            else -> null
        } ?: return menu
        return when (menu) {
            is Screen.Systems -> menu.copy(search = draft)
            is Screen.Games -> if (menu.isTab) menu.copy(search = draft) else menu
            else -> menu
        }
    }

    /**
     * The main menu with nothing over it: no context menu, no START prompt (the Games tab: nor a Layout dropdown, the
     * achievements panel or the art panel).
     */
    private fun menuFree(s: Screen): Boolean = s.isMainMenu && s.contextMenu == null && when (s) {
        is Screen.Systems -> s.search == null && s.layoutMenu == null
        is Screen.Games -> s.search == null && s.layoutMenu == null && !_panelOpen.value
        else -> false
    }

    /** The Games tab showing: remember its game before a scan or load replaces the screen, so the menu lands on it again. */
    private fun rememberGamesTab() {
        if ((_screen.value as? Screen.Games)?.isTab == true) rememberMenuSelection()
    }

    /** The main menu showed: this instance becomes the updater's host again (another may have taken it, or let it go), then tells it. */
    private fun updaterOnMainMenu() {
        val u = deps.updater ?: return
        u.hostBusy = updateHost
        u.onMainMenu()
    }

    /**
     * [category]'s remembered row. Home's is the same row where it still shows (a rotation may have moved its index, the
     * slots following the frame); else Continue for a launched pin; else moved with [homeClamp] when its shape changed
     * since it was remembered.
     */
    private fun rememberedRow(category: MenuCategory, rows: List<MainRow>): ListSelection {
        val r = tabSelections[category] ?: return ListSelection(if (category == MenuCategory.HOME) homeInitialIndex(rows) else 0, rows.size)
        if (category != MenuCategory.HOME) return r
        return homeTab.rememberedRow(r, rows)
    }

    /** The Games tab's highlighted game when it was last left ([rememberMenuSelection]); null: never shown, or its first. */
    private var gamesTabGame: String? = null

    /**
     * The Games tab's remembered row in [entries]: its game ([gamesTabGame]) wherever the list now puts it; that game gone
     * (hidden, removed), the row it was left on, the caller's clamp taking it to the nearest one; never shown, the first.
     */
    private fun rememberedGame(entries: List<Listed>): ListSelection {
        val r = tabSelections[MenuCategory.GAMES] ?: return ListSelection(0, entries.size)
        val at = entries.indexOfPath(gamesTabGame) ?: r.index
        return ListSelection(at, entries.size)
    }

    /**
     * The switcher's way back to the menu [m]: its tab's row as last remembered, clamped (Home's as stored, not re-found);
     * the Games tab on its game.
     */
    private fun menuRowOf(m: Screen): ListSelection = when (m) {
        is Screen.Games -> rememberedGame(m.entries).clampedTo(m.entries.size)
        is Screen.Systems -> (m.category?.let(tabSelections::get)
            ?: ListSelection(if (m.category == MenuCategory.HOME) homeInitialIndex(m.rows) else 0, m.rows.size)).clampedTo(m.rows.size)
        else -> ListSelection(0, 0)
    }

    /** [screen] (a main menu tab) on its remembered row, clamped: what the tab shows when it is next shown. */
    private fun rememberedSelection(screen: Screen): ListSelection = when (screen) {
        is Screen.Games -> rememberedGame(screen.entries).clampedTo(screen.entries.size)
        is Screen.Systems -> rememberedRow(screen.category ?: menuCategory, screen.rows).clampedTo(screen.rows.size)
        else -> ListSelection(0, 0)
    }

    /**
     * The row to keep when the main menu [from] is rebuilt as [to]: the same index on the same tab, -1 (the remembered
     * row) on another. Home with a new shape (a pin gone, other columns) clamps it into its section ([homeClamp]); a
     * shelf game follows itself, or its pin when it was just pinned ([homeFind]). The Carousel Home keeps a row item in
     * the row (a gone last card lands on the one before it, never on the dock's first square).
     */
    private fun keptIndex(from: Screen.Systems, to: Screen.Systems): Int {
        if (to.category != from.category) return -1
        if (to.category != MenuCategory.HOME) return _selection.value.index
        return homeTab.keptIndex(from, to)
    }

    /**
     * When the main menu shows with no menu open, swaps in the tabs last built, keeping the highlighted row where it is
     * still there, and remembers that row for its tab.
     */
    private fun swapMainRows() {
        val s = _screen.value
        if (!s.isMainMenu || s.contextMenu != null) return
        val screen = menuScreen(s.menuTab ?: menuCategory)
        showMenu(screen, keptRow(s, screen))
        // The highlight may have moved with its row: the next show of this tab starts there.
        rememberMenuSelection()
    }

    /** [s] is the main menu showing tabs other than the ones last built (a swap was held back while a menu was open). */
    private fun menuStale(s: Screen): Boolean = when (s) {
        is Screen.Systems -> s.tabs != menuTabs
        is Screen.Games -> s.tabBar != null && menuTabs.let { tabs ->
            s.tabBar.categories != tabs.map { it.category } || tabs.firstOrNull { it.category == MenuCategory.GAMES }?.games !== s.entries
        }
        else -> false
    }

    /**
     * The row to show when the main menu [s] is rebuilt as [screen] in place; -1: [screen]'s tab's remembered row. A row
     * gone (an unpinned pin), the same index lands on the one after it; the tab gone (its last collection removed, the last
     * game), the fallback tab lands on its own remembered row, not the gone one's index. The Games tab keeps its game
     * wherever the list now puts it; that game gone, the same row (the caller clamps it to the nearest).
     */
    private fun keptRow(s: Screen, screen: Screen): Int {
        if (s is Screen.Games || screen is Screen.Games) {
            if (s !is Screen.Games || screen !is Screen.Games) return -1   // another tab
            val path = s.entries.getOrNull(_selection.value.index)?.game?.path
            return screen.entries.indexOfPath(path) ?: _selection.value.index
        }
        s as Screen.Systems; screen as Screen.Systems
        val highlighted = s.rows.getOrNull(_selection.value.index)
        val launched = if (screen.category == MenuCategory.HOME) homeLaunchedPin(highlighted, screen.rows) else null
        val home = screen.category == MenuCategory.HOME
        return (if (home) homeTab.homeFind(highlighted, screen.rows) else screen.rows.indexOf(highlighted).takeIf { it >= 0 }) ?: launched ?: keptIndex(s, screen)
    }

    /** Reads hidden.txt into [hiddenPaths] and returns it as the set the index filters with. */
    private suspend fun readHidden(): Set<String> {
        hiddenPaths = deps.userData.readHidden()
        return hiddenPaths.toSet()
    }

    /** A generation for a [systemsCache] about to be computed (see [systemsLock]). */
    private suspend fun takeSystemsGen(): Long = systemsLock.withLock { ++systemsTaken }

    /** [systems], computed under [gen], becomes [systemsCache] unless a newer computation's is shown; true when it did. */
    private suspend fun offerSystems(gen: Long, systems: List<SystemFolder>): Boolean = systemsLock.withLock {
        (gen > systemsWritten).also { if (it) { systemsWritten = gen; systemsCache = systems; topScreen.followTopStats() } }
    }

    private suspend fun showSystems(playable: List<SystemFolder>) {
        val gen = takeSystemsGen()
        dsMenuReady = tryOr({ false }) { withContext(deps.compute) { DsMenu.missing(deps.ndsBiosFiles().toSet()).isEmpty() } }
        offerSystems(gen, DsMenu.withConsole(playable, dsMenuReady))
        collectionCounter.invalidateCollectionCounts()
        rebuildMainRows()
        when {
            resumeFolderSteps() -> Unit
            onboardingUi.onboardingTakesOver() -> onboardingUi.showOnboarding()
            else -> showSystemsNow()
        }
        refreshGameInfo()
    }

    /**
     * A start while first setup's folder steps were under way (saved as [FOLDER_STEPS], the app since closed): they
     * resume at the home page (its count and move; none: the ROMs-folder offer).
     */
    private fun resumeFolderSteps(): Boolean {
        if (onboardingUi.firstSetup || deps.settings.onboardingDone != false || deps.settings.onboardingStep != FOLDER_STEPS) return false
        val home = home() ?: return false
        onboardingUi.firstSetup = true
        if (deps.settings.treeRef == null && home == deps.homes?.media) onboardingUi.mediaHomeTaken = true   // a restart on the media home: no pick behind it
        folders.showHomePage(home)
        return true
    }

    /** First setup's folder steps are done: onboarding's own steps start from their first. */
    private fun endFolderSteps() {
        onboardingUi.firstSetup = false
        if (deps.settings.onboardingStep == FOLDER_STEPS) deps.settings.onboardingStep = null
    }

    private val _syncSetup = MutableStateFlow<SyncSetup?>(null)
    /** Device Sync's page state ([SyncSetup]): the tool's, or setup's sync step's; null while it is not showing. */
    val syncSetup: StateFlow<SyncSetup?> = _syncSetup.asStateFlow()

    /** Opens Device Sync's page (the tool's, and setup's sync step), Sync ROMs first where it is offered: the option above the links. */
    private fun showDeviceSync(sync: DeviceSyncController) {
        sync.open()   // Ready on the cable, or the live page of a sync still running
        _screen.value = Screen.DeviceSync
        publishSyncSetup(if ((sync.page.value as? DeviceSyncPage.Ready)?.romsAvailable == true) SyncFocus.ROMS else SyncFocus.LINK)
    }

    private fun publishSyncSetup(focus: SyncFocus = _syncSetup.value?.focus ?: SyncFocus.LINK) {
        _syncSetup.value = when {
            _screen.value != Screen.DeviceSync -> null
            onboardingUi.onboardingSync -> SyncSetup(onboardingUi.onboardingPageTitle(), onboardingUi.onboardingSynced != null, focus)
            else -> SyncSetup(DeviceSyncText.TITLE, synced = false, focus = focus, inSetup = false)
        }
    }

    /** A tap on the setup sync page's row [index] ([syncSetupRows]): it selects; [open] (a tap that chooses) also acts. */
    fun syncSetupTap(index: Int, open: Boolean) {
        val sync = deps.deviceSync ?: return
        val page = sync.page.value as? DeviceSyncPage.Ready ?: return
        if (_screen.value != Screen.DeviceSync) return
        when (val row = syncSetupRows(page, skip = onboardingUi.onboardingSync).getOrNull(index) ?: return) {
            SyncSetupRow.Roms -> { publishSyncSetup(SyncFocus.ROMS); if (open) sync.flipRoms() }
            is SyncSetupRow.Link -> { sync.pick(row.link); publishSyncSetup(SyncFocus.LINK); if (open) onDeviceSyncButton(NxButton.A) }
            SyncSetupRow.Skip -> { publishSyncSetup(SyncFocus.SKIP); if (open) onboardingUi.leaveOnboardingSync(forward = true) }
        }
    }

    private var secondConnected = false

    /**
     * A qualifying second screen was connected or removed (the platform reports it): the Settings root's Second screen
     * section and an open Emulator or Game Settings page add or drop their dual-screen rows at once (the Second screen
     * page, open, goes back to the root). Stored values are untouched.
     */
    fun onSecondScreenChanged(connected: Boolean) {
        if (secondConnected == connected) return
        secondConnected = connected
        when ((_screen.value as? Screen.Settings)?.section) {
            SettingsSection.SECOND_SCREEN -> if (!connected) settingsUi.showSettingsMenu()   // its section is gone: back to the root
            else -> Unit
        }
        if (_screen.value is Screen.SettingsMenu) settingsUi.showSettingsMenu()   // the root's sections follow
        emulatorSettings.refreshOpenPage()
    }

    /** First setup's home page and folder summaries, with their moves. */
    // Lazy: built on first use, so a collector of [init] that reaches it during construction gets it, not null.
    private val folders: FolderSummaryController by lazy { FolderSummaryController(
        scope, _screen, _selection, deps, scanner, libraryIndex, collections,
        host = object : FolderSummaryHost {
            override fun home(): ContentRef? = this@AppState.home()
            override suspend fun scanNow(root: ContentRef, then: ((List<SystemFolder>) -> Unit)?) = this@AppState.scanNow(root, then)
            override fun showAddFirstExtra() = this@AppState.showAddFirstExtra()
            override fun finishFolderSteps() = afterFolderSteps { load() }
            override fun markLibraryDirty() { libraryDirty = true }
            override val recentsLock: Mutex get() = this@AppState.recentsLock
        },
    ) }

    /** First setup's ROMs folder offer (after the home page, or B from the handheld question): Add folder, Skip. */
    private fun showAddFirstExtra() {
        _screen.value = Screen.AddFirstExtra
        _selection.value = ListSelection(0, 2)
    }

    /** The first of the page's two buttons side by side ([setupButtons]), its row index; null when the page has no pair. */
    private fun setupButtonPair(): Int? = when (val s = _screen.value) {
        is Screen.Onboarding -> s.touchRows?.takeIf { !s.prompt && !s.artSignIn && s.artKey == null && setupButtons(it, button = true) == 2 }?.let { it.size - 2 }
        Screen.AddFirstExtra, is Screen.AndroidGamesStep -> 0
        is Screen.StorageUpgrade -> 0.takeIf { storageUpgradeRows(s).size == 2 }
        is Screen.CopySaves -> 0.takeIf { !s.busy && copySavesRows(s).size == 2 }
        else -> null
    }

    /** A or a tap on a folder step's row [index]: that step's choice. */
    fun setupStepRow(index: Int) {
        when (_screen.value) {
            is Screen.PermissionsStep -> when (permissionRows(_screen.value as Screen.PermissionsStep).getOrNull(index)) {
                PermissionRow.ALL_FILES -> allowAllFiles()
                PermissionRow.NOTIFICATIONS -> deps.notificationAccess?.request()
                PermissionRow.CONTINUE -> skipAllFiles()
                null -> Unit
            }
            Screen.PickFolder -> pickHome()
            Screen.AddFirstExtra -> if (index == 0) pickExtra() else skipFirstExtra()
            is Screen.AndroidGamesStep -> answerAndroidGames(index == 0)
            is Screen.FolderSummary -> folders.folderSummaryRow(_screen.value as Screen.FolderSummary, index)
            else -> Unit
        }
    }

    /**
     * Re-reads play time and RA progress (off the main thread in their own stores); a failure keeps that half's last good
     * read. The Home tab is rebuilt with it: its shelves go by play time. One read at a time: asked for while one runs,
     * it runs once more when that one ends (asks in between fold into it).
     */
    private fun refreshGameInfo() {
        if (gameInfoJob?.isActive == true) { gameInfoDirty = true; return }
        gameInfoJob = scope.launch {
            do {
                gameInfoDirty = false
                // A failed read keeps that half's last good map (before any, empty). The shelves wait for a play time
                // read that works: with none, every game would be Not started.
                val read = tryOrNull { deps.gameTime?.byPath().orEmpty() }
                val raRead = tryOrNull { deps.achievements?.progressByPath().orEmpty() }
                tryQuietly { deps.achievements?.hashesByPath()?.let { ra.raHashes = it } }
                ra.followRaPanel(force = true)   // the panel's game may have data now (a download just ended)
                ra.refreshRaDetails()
                val times = read?.also { lastTimes = it } ?: lastTimes
                val raByPath = raRead?.also { lastRa = it } ?: lastRa
                _gameInfo.value = (times.keys + raByPath.keys).associateWith { p -> GameInfo(times[p]?.lastPlayedAt, times[p]?.totalMs, raByPath[p], times[p]?.plays) }
                if (read != null) gameInfoRead = true
                rebuildHomeTab()   // the shelves follow play time (Not started, Pick up again)
            } while (gameInfoDirty)
        }
    }

    /** The user asked to move into or out of a list from this screen; the page that lands for it (maybe after a read) slides. */
    private fun intendNav(motion: NavMotion) { pendingNav = motion to _screen.value }

    /**
     * A list page is about to replace [from]: the pending intent slides it when it was made on [from]. Anything else (a refresh, a
     * list emptied by a hide, a stale intent whose read found nothing) lands instantly.
     */
    private fun landing(from: Screen, to: Screen) {
        val (motion, on) = pendingNav ?: return
        pendingNav = null
        if (on !== from) return
        // A Minimal morph needs a Minimal page on both sides: else the plain slide.
        val minimal = motion != motion.slide && styleOf(from) == MenuStyle.MINIMAL && styleOf(to) == MenuStyle.MINIMAL
        navStep(if (minimal) motion else motion.slide)
        if (minimal) launches.forgetMinimalOpening()   // the faded page is gone: nothing to clear later
    }

    /** Called just before the new page's screen is set, so the UI never sees the new screen under the old step. */
    private fun navStep(motion: NavMotion) { _nav.value = NavStep(_nav.value.seq + 1, motion) }

    /**
     * The main menu from the tabs last built. [select]: on the first row matching it, in whichever tab holds it. Else on
     * [category]'s tab (the remembered tab when null), on that tab's remembered row.
     */
    private fun showSystemsNow(category: MenuCategory? = null, select: ((MainRow) -> Boolean)? = null) {
        val hit = select?.let { p -> menuTabs.firstOrNull { t -> t.rows.any(p) } }
        val screen = menuScreen(hit?.category ?: category ?: menuCategory)
        landing(_screen.value, screen)
        showMenu(screen, select?.let { p -> (screen as? Screen.Systems)?.rows?.indexOfFirst(p) } ?: -1)
    }

    /**
     * The main menu's shown tabs and the one showing, when nothing is open over it (a context menu, a prompt, the Layout
     * dropdown); null on any other screen.
     */
    private fun tabRowOf(s: Screen): Pair<List<MenuCategory>, Int>? = when (s) {
        is Screen.Systems -> if (s.menu != null || s.search != null || s.layoutMenu != null) null else s.tabs.map { it.category } to s.tab
        is Screen.Games -> s.tabBar?.takeIf { s.menu == null && s.search == null && s.layoutMenu == null }?.let { it.categories to it.index }
        else -> null
    }

    /** L1 / R1 on the main menu: the previous / next tab, wrapping; each tab keeps its row. */
    private fun switchTab(step: Int) {
        val (tabs, at) = tabRowOf(_screen.value) ?: return
        if (tabs.size < 2) return
        selectTab((at + step).mod(tabs.size))
    }

    /**
     * A swipe across a List or Minimal main menu page: the next ([step] 1) or previous (−1) shown tab, wrapping as L1 /
     * R1 do. App's pager has that tab's page beside the one showing already, and slides it in from where the finger let go.
     */
    fun swipeTab(step: Int) = switchTab(step)

    /**
     * The main menu's tab [index] as it would show beside the one showing (App's pager): its screen (no menu or search
     * over it) and its remembered row. Nothing changes.
     */
    fun tabPage(index: Int): Pair<Screen, ListSelection>? {
        val page = when (val s = _screen.value) {
            is Screen.Systems -> {
                if (s.tabs.getOrNull(index) == null) return null
                if (s.tabs[index].category == MenuCategory.GAMES) tabScreen(s.tabs, index, s.hidden)
                else s.copy(tab = index, menu = null, search = null)
            }
            is Screen.Games -> {   // the Games tab: the tab beside it as the menu would show it now
                val bar = s.tabBar ?: return null
                val c = bar.categories.getOrNull(index) ?: return null
                val tabs = menuTabs
                tabScreen(tabs, tabs.indexOfFirst { it.category == c }.takeIf { it >= 0 } ?: return null, bar.hidden)
            }
            else -> return null
        }
        return page to rememberedSelection(page)
    }

    /** A tap on a tab, or [switchTab]. Landing on Tools refreshes what its pages show. */
    fun selectTab(index: Int) {
        if (loader.active) return   // a screen is loading: only B (cancel) acts, as for L1 / R1
        val (tabs, _) = tabRowOf(_screen.value) ?: return
        val category = tabs.getOrNull(index) ?: return
        rememberMenuSelection()
        if (category == MenuCategory.TOOLS) { deps.cheatDb?.refresh(); deps.achievements?.refresh() }
        showMenu(menuScreen(category))
    }

    private var loadedOnce = false

    /**
     * App's start-up [load], once per state: App composed again (moved to or from the second display) leaves the screen
     * as it is.
     */
    fun loadOnce() {
        if (loadedOnce) return
        loadedOnce = true
        load()
    }

    /**
     * Start-up and every return to the library. Shows the index when it was built for the current home, extras and
     * default tags, with no SAF call, then probes the home in the background; anything else scans, as does an index
     * with an extra that was offline at scan time and is mounted again (checked before showing, so its games come
     * back without the list changing under the user; no SAF call either). The in-memory index is tried before the
     * stored copy: after a failed write it is the newer of the two. [then] runs once Systems is shown, with or
     * without a scan (not after a failed scan).
     */
    fun load(then: ((List<SystemFolder>) -> Unit)? = null) {
        // An install from before onboarding has its home already: it counts as done. A fresh one has onboarding to do.
        if (deps.settings.onboardingDone == null) deps.settings.onboardingDone = deps.settings.treeRef != null
        deps.settings.migrateMixWheel()   // Mix users from before Wheel was its own setting keep their wheels, once
        // The one-time storage upgrade (v0.3.0 and earlier) comes before anything else; a load while it is up leaves it.
        if (upgrade.upgradeOn || upgrade.upgradeDue()) { if (!upgrade.upgradeOn) upgrade.startStorageUpgrade(); return }
        if (upgrade.upgradeCase() == UpgradeCase.QUIET) deps.storageUpgrade?.startQuietMove()   // Case A: in the background, never mid-game
        // First setup's folder steps come first even with a media home to fall back to; a saved step means they were reached.
        val setupFirst = deps.settings.treeRef == null && deps.settings.onboardingDone == false && deps.settings.onboardingStep == null && !onboardingUi.mediaHomeTaken
        if (home() == null || setupFirst) {
            if (deps.settings.onboardingDone == false && !onboardingUi.welcomeDone && onboarding.step == null) { onboarding.startWelcome(); onboardingUi.showOnboarding(); return }
            showPickFolder(); return
        }
        libraryDirty = false
        _screen.value = Screen.Loading
        scope.launch {
            convertToFiles()   // before the index is read, so a converted home or extra scans once
            val root = home() ?: return@launch
            mediaHomeNotice(root)
            collectionCounter.loadStored()   // before the first menu build: the collections listed next show their stored counts
            toolPins = readToolPins()
            val usable = fittingIndex(root)
            if (usable == null || backOnline(usable)) {
                scanNow(root, then)
                refreshInstalled()   // the Android games console: its row joins the menu once the apps are listed
                return@launch
            }
            showSystems(usable.playableSystems(readHidden()))
            then?.invoke(systemsCache)
            refreshInstalled()
            afterIndexStart(root)
        }
    }

    /** [ToolPins.FILE] (a suspending read, as the count cache's); a missing or unreadable file is no pins. */
    private suspend fun readToolPins(): List<String> =
        tryOrAny({ emptyList() }) { ToolPins.decode(deps.privateFiles.read(ToolPins.FILE)) }

    /** Writes [keys] as [ToolPins.FILE] off the main thread, then keeps them; false (nothing changed) when the write fails. */
    private suspend fun writeToolPins(keys: List<String>): Boolean {
        tryOrAny({ return false }) { withContext(deps.compute) { deps.privateFiles.write(ToolPins.FILE, ToolPins.encode(keys)) } }
        toolPins = keys
        return true
    }

    /** The index built for [root] and the current extras and default tags: the in-memory one, else the stored copy. */
    private suspend fun fittingIndex(root: ContentRef): LibraryIndex? {
        // A re-index for the settings just changed is waited for: the old index no longer fits, and a second scan would repeat it.
        reindexJob?.join()
        return libraryIndex.fitting { ix -> ix.fits(root, deps.settings.extraFolders, deps.settings.defaultTags) }
    }

    /** True when an extra [index] recorded as offline is mounted again (a platform check, no tree query). */
    private suspend fun backOnline(index: LibraryIndex): Boolean =
        index.offline.isNotEmpty() && deps.settings.extraFolders.any { it.id in index.offline && !deps.source.isOffline(it.ref) }

    /**
     * Scans every tree, stores the index and shows Systems; [then] runs with the playable systems after a good scan.
     * [kept] runs instead after a failed scan that keeps the old index (a hidden listing, an offline home): Systems is
     * rebuilt from that index first, and the scan's notice or error stays, so a caller on Settings can go back there.
     */
    fun rescan(kept: (() -> Unit)? = null, then: ((List<SystemFolder>) -> Unit)? = null) {   // [then] last: a trailing lambda stays the success step
        val root = home() ?: run { showPickFolder(); return }
        rememberGamesTab()   // the scan lands on the tab's game again
        _screen.value = Screen.Loading
        scope.launch { scanNow(root, then, kept) }
    }

    /**
     * The scan behind [load] and [rescan]. A home that fails as gone while its card reports mounted is scanned once more
     * after [LibraryScanner.REMOUNT_RETRY_MILLIS] ([retried] false): inside the provider's lag after a mount, even at a
     * cold start with no fitting index, where no offline record gives the home its grace pass. [kept]: see [rescan].
     */
    private suspend fun scanNow(root: ContentRef, then: ((List<SystemFolder>) -> Unit)?, kept: (() -> Unit)? = null, retried: Boolean = false) {
        runCatching {
            // Revoked access can list a file home as empty rather than fail: never scan (and so never store) that as an emptied library.
            if (FileRefs.isFile(root) && deps.source.isOffline(root)) throw IllegalStateException(ACCESS_OFF)
            if (isOldRomsPick(root)) {
                if (!FileRefs.isFile(root)) deps.settings.treeRef = null   // a file home is only switched from, never cleared
                throw OldPick()
            }
            ensureSkeleton(root)
            val dropped = LinkedHashSet<ExtraFolder>()   // a set: a scan run again reports the same extra again
            // Systems is shown from it below, which sets the playable cache: no refresh here.
            val fresh = scanIndex(root, refresh = null) { extra, e -> if (isGone(e)) dropped += extra }
            // An offline extra is kept (settings and grant) with no games; the next start or return to the library finds it back.
            // One mounted but not listing yet (lateMount) only needs a moment, not the card.
            val awayExtra = deps.settings.extraFolders.firstOrNull { it.id in fresh.offline }
            val away = awayExtra?.let { deps.source.displayName(it.ref) }
            val late = awayExtra != null && awayExtra.id in fresh.lateMount
            val accessOff = awayExtra != null && fileAccessOff(awayExtra.ref)
            if (dropped.isNotEmpty()) {
                deps.settings.extraFolders = deps.settings.extraFolders - dropped.toSet()
                dropped.forEach { deps.folderPicker.forget(it.ref) }
                val lost = "Lost access to ${deps.source.displayName(dropped.first().ref)}; add it again in Tools → Settings → Library"
                showNotice(if (away == null) lost else "$lost. $away is not ${if (late) "ready yet" else "available"}.")
            } else if (away != null && accessOff) {
                // No notice: Library's All files access row says it, in the warning colour ([allFilesRow]).
            } else if (away != null) {
                showNotice(if (late) "$away is not ready yet; use Rescan in Settings → Library in a moment." else "$away is not available. Insert its storage, then use Rescan in Settings → Library.")
            }
            fresh
        }
            .onSuccess {
                libraryDirty = false   // the index is this scan's
                homeReadable()
                collectionCounter.refreshCollectionsFlag()   // the scan just walked the tree, so the first show can carry the row
                showSystems(it.playableSystems(readHidden()))
                then?.invoke(systemsCache)
            }
            .onFailure { e ->
                if (e is CancellationException) throw e   // a cancelled scan is not a failed one: no error, no picker
                if (e is ListingUnavailable) {
                    listingHidden(root, indexShown = false, kept)
                    return
                }
                if (!retried && e !is OldPick && isGone(e) && !deps.source.isOffline(root)) {
                    delay(LibraryScanner.REMOUNT_RETRY_MILLIS)
                    scanNow(root, then, kept, retried = true)
                    return
                }
                if (e is OldPick) {
                    _error.value = "Your folder pick was the Roms folder. Pick its parent, the folder that will hold Roms and Bios."
                } else if (homeOffline(root) || (isGone(e) && homeNotBackYet(root))) {
                    // An unmounted card lists as a deleted folder would: keep the home, its grant and the index. The
                    // library stays as it was (nothing was rescanned); with no index for these settings there is
                    // nothing to show, so the picker, the home still set for the next start. Recorded, so a return
                    // to the app once it reads runs the probe, skeleton and Collections check the index start skips.
                    offlineHomeSaid = root
                    val index = fittingIndex(root)
                    if (index != null) {
                        _error.value = homeAwayError(root, "Insert it, then use Rescan in Settings → Library.")
                        showSystems(index.playableSystems(readHidden()))
                        kept?.invoke()
                        return
                    }
                    _error.value = homeAwayError(root, "Insert it, then reopen the app.")
                } else {
                    // Only drop the persisted tree when the failure means the tree itself is gone or
                    // invalid (a revoked SAF permission -> SecurityException, an invalid/stale tree URI
                    // -> IllegalArgumentException). Transient failures keep the tree so the user can retry.
                    if (isGone(e) && !FileRefs.isFile(root)) deps.settings.treeRef = null   // a file home is kept: the next load falls back to the media home
                    _error.value = "Could not read the home folder: ${e.message ?: e::class.simpleName}"
                }
                showPickFolder()
            }
    }

    /**
     * Scans every tree into a new index and makes it the library ([LibraryIndexHolder.rebuild]: the scan holds no lock),
     * then [refresh] as [mutateIndex]. The index it starts from tells which extras were offline (one just mounted gets a
     * retry): the one in memory, else the stored copy. [onExtraError]: an extra that failed to list.
     */
    private suspend fun scanIndex(
        root: ContentRef,
        refresh: IndexRefresh?,
        onExtraError: suspend (ExtraFolder, Throwable) -> Unit = { _, _ -> },
    ): LibraryIndex {
        val fresh = libraryIndex.rebuild { current ->
            val previous = current ?: libraryIndex.readStored()
            stamped(scanner.scan(root, deps.settings.extraFolders, deps.settings.defaultTags, previous, onExtraError), previous)
        }!!   // a scan is always a new index
        lateMountHere = fresh.lateMount
        refresh?.let { afterIndexChange(fresh, it) }
        return fresh
    }

    /**
     * [index] with the time its scan finished ([LibraryIndex.scannedAt]) and each game's first-seen date ([withFirstSeen]),
     * on [AppDeps.compute]: the pass walks the whole index.
     */
    private suspend fun stamped(index: LibraryIndex, previous: LibraryIndex?): LibraryIndex {
        val now = deps.now?.invoke()
        return withContext(deps.compute) { index.withFirstSeen(previous, now).let { if (now != null) it.copy(scannedAt = now) else it } }
    }

    /**
     * After a start from the index: the one SAF probe (is the home still there?), then the skeleton once per home.
     * A gone home (revoked grant, stale URI) clears it and the index as a failed scan would; other failures are ignored.
     * A home on an unmounted card is not probed: the index stays on screen (launches fail with "Could not find") and
     * the error says so once per AppState until the card is back; the skeleton and Collections wait for a readable home.
     * A card that reports mounted can still list as gone for a moment (its provider refreshes roots after the volume
     * state changes), so a gone probe is retried once after [LibraryScanner.REMOUNT_RETRY_MILLIS]; a home last seen
     * offline that still fails is kept for one more pass ([homeNotBackYet]). One check at a time: a caller that finds
     * one in flight for the same home (a return to the app and a load from Settings inside the lag) waits for it
     * instead of probing again, which would find the grace pass spent and clear the home.
     */
    private suspend fun afterIndexStart(root: ContentRef) {
        homeCheck?.takeIf { it.isActive && homeCheckFor == root }?.let { it.join(); return }
        val job = scope.launch(start = CoroutineStart.LAZY) { checkHome(root) }
        homeCheck = job
        homeCheckFor = root
        job.join()
    }

    private suspend fun checkHome(root: ContentRef) {
        if (homeOffline(root)) {
            if (offlineHomeSaid != root) _error.value = homeAwayError(root, "Insert it, then reopen the app.")
            offlineHomeSaid = root
            return
        }
        var failure = probe(root)
        if (failure != null && isGone(failure)) {
            delay(LibraryScanner.REMOUNT_RETRY_MILLIS)
            failure = probe(root)
        }
        // A SAF tree root never throws this (the probe lists a root, which has no parent to prove the empty listing
        // wrong); the branch covers other sources.
        if (failure is ListingUnavailable) {
            listingHidden(root, indexShown = true)
            return
        }
        if (failure != null) {
            val e = failure
            if (isGone(e) && homeNotBackYet(root)) return
            if (isGone(e) && deps.settings.treeRef == root && !FileRefs.isFile(root)) {
                deps.settings.treeRef = null
                libraryIndex.forget()
                _error.value = "Could not read the home folder: ${e.message ?: e::class.simpleName}"
                showPickFolder()
            }
            return
        }
        homeReadable()
        ensureSkeleton(root)
        collectionCounter.refreshCollectionsFlag()   // the probe showed the tree readable: the Collections row may appear now
    }

    /**
     * A listing the provider would not give ([ListingUnavailable]) in a scan or the start probe: never a lost tree, so
     * the home, its extras and the index all stay (no remount retry). The index that fits is shown (unless [indexShown]:
     * the start already shows it) with a notice to rescan later, then [kept] (see [rescan]); without one, the picker
     * and an error, the home kept.
     */
    private suspend fun listingHidden(root: ContentRef, indexShown: Boolean, kept: (() -> Unit)? = null) {
        skeletonFor = null   // a skeleton pass cut short by the same fault runs again next scan
        val index = fittingIndex(root)
        if (index != null) {
            if (!indexShown) showSystems(index.playableSystems(readHidden()))
            kept?.invoke()
            showNotice(LISTING_HIDDEN)
        } else {
            _error.value = LISTING_HIDDEN_NO_INDEX
            showPickFolder()
        }
    }

    /** The home probe: null when [root] lists, else the failure. */
    private suspend fun probe(root: ContentRef): Throwable? = tryOrAny({ it }) {
        deps.source.listChildren(root)
        null
    }

    /** A failure that means the tree itself is gone or invalid: a revoked grant (SecurityException), a stale or deleted tree (IllegalArgumentException). */
    private fun isGone(e: Throwable) = e is IllegalArgumentException || e::class.simpleName == "SecurityException"

    /**
     * True, once, for a home last seen offline that is mounted but still lists as gone: its provider may not have the
     * root back yet, so it is kept as offline this pass. The next such failure is taken as gone.
     */
    private fun homeNotBackYet(root: ContentRef): Boolean {
        if (offlineHomeSaid != root || homeLateMountUsed) return false
        homeLateMountUsed = true
        return true
    }

    /** [RomsSource.isOffline] for the home; out again, it gets a fresh "not back yet" pass for its next mount. */
    private suspend fun homeOffline(root: ContentRef): Boolean =
        deps.source.isOffline(root).also { if (it) homeLateMountUsed = false }

    /** The home just listed: an offline record for it is over (a later unmount says so again). */
    private fun homeReadable() {
        offlineHomeSaid = null
        homeLateMountUsed = false
    }

    /** Once per home per AppState (a re-pick from Settings gets its skeleton too). Failure is not fatal: the library still loads. */
    private suspend fun ensureSkeleton(root: ContentRef) {
        if (skeletonFor == root) return
        skeletonFor = root
        tryOr<Unit>({ if (it is ListingUnavailable) skeletonFor = null }) { Skeleton.ensure(deps.source, root) }   // a listing cut short before any create: the next scan tries again
    }

    /**
     * With All files access on, a SAF home or extra on external storage whose path is a folder becomes that path's file
     * ref ([SourceConversion]); ids and SAF grants are kept. True when anything changed (the index then no longer fits,
     * so the next load scans).
     */
    private suspend fun convertToFiles(): Boolean {
        val files = deps.localFiles ?: return false
        if (deps.allFilesAccess?.granted() != true) return false
        val home = deps.settings.treeRef
        val extras = deps.settings.extraFolders
        val converted = SourceConversion.convert(home, extras) { files.isDirectory(it) }
        if (converted.home == home && converted.extras == extras) return false
        if (deps.settings.treeRef != home || deps.settings.extraFolders != extras) return false   // a load converted meanwhile
        deps.settings.treeRef = converted.home
        deps.settings.extraFolders = converted.extras
        return true
    }

    /** The home to read now: the platform's choice ([HomeLocator.current]: the full-access folder, else the media one), else the saved one. */
    private fun home(): ContentRef? = deps.homes?.current() ?: deps.settings.treeRef

    /** `Android/media/<package>/` for [media] (its last path segment is the package). */
    private fun mediaPath(media: ContentRef): String = "Android/media/${FileRefs.path(media).substringAfterLast('/')}/"

    /** A saved full-access home that cannot be read now ([root] is the media one instead): the library says so, once per load. */
    private fun mediaHomeNotice(root: ContentRef) {
        if (deps.storageUpgrade?.quietMoveStuck() == true) return showNotice(MOVE_STUCK)
        val saved = deps.settings.treeRef ?: return
        if (root != deps.homes?.media || !FileRefs.isFile(saved) || saved == root) return
        // All files access off: no notice each start; Library's All files access row says it ([allFilesRow]).
        if (deps.allFilesAccess?.granted() != true) return
        val saves = if (dataMoved()) "Saves made there stay there." else SAVES_STILL_INSIDE
        showNotice("Your home folder can't be found. Using ${mediaPath(root)} until it's back. $saves")
    }

    /** The data is in the home now (the one-time move done, or none due): until then it is read and written inside the app. */
    private fun dataMoved(): Boolean = deps.storageUpgrade?.isDone() != false

    /** The error for a home that cannot be read now: a file home without All files access ([ACCESS_OFF]), else the unmounted card's. */
    private fun homeAwayError(root: ContentRef, then: String): String =
        if (FileRefs.isFile(root) && deps.allFilesAccess?.granted() != true && !inMediaHome(root)) ACCESS_OFF else "$HOME_OFFLINE $then"

    /** True for a file ref read without All files access: its folder is not "offline", the permission is off. */
    private fun fileAccessOff(ref: ContentRef): Boolean = FileRefs.isFile(ref) && deps.allFilesAccess?.granted() == false && !inMediaHome(ref)

    /** [ref] is the app's own media folder or inside it: readable without All files access. */
    private fun inMediaHome(ref: ContentRef): Boolean = deps.homes?.media?.let { FileRefs.within(it, ref) } == true

    private class OldPick : RuntimeException()

    /** A pre-skeleton pick: the Roms folder itself (tagged children, no Roms/ child). */
    private suspend fun isOldRomsPick(root: ContentRef): Boolean {
        if (deps.source.child(root, "Roms")?.isDirectory == true) return false
        return deps.source.listChildren(root).any { it.isDirectory && Names.parseSystemFolder(it.name) != null }
    }

    /** The list-style picker; null without [AppDeps.localFiles]. */
    private val filePicker = deps.localFiles?.let { FilePickerController(scope, _screen, _selection, _error, it) }

    /** All files access is on and the list-style picker is there: every folder pick goes through it, not Android's. */
    fun filePickerOn(): Boolean = filePicker != null && deps.allFilesAccess?.granted() == true

    /**
     * The home pick: the list-style picker with All files access. Without it no picker home is saved (the
     * home is the media one): All files access is asked for instead, and the row picks once it is on. Only a platform
     * without a media home or that permission keeps Android's picker ([FolderPicker.pickHome]).
     */
    fun pickHome() {
        val p = filePicker
        when {
            p != null && filePickerOn() -> p.open(FilePickMode.Home, deps.settings.treeRef) { it?.let(::setHome) }
            deps.homes != null && deps.allFilesAccess?.declared == true -> deps.allFilesAccess.openSettings()   // the media home's platform
            else -> deps.folderPicker.pickHome()
        }
    }

    /** An extra ROM folder pick, routed as [pickHome]. */
    fun pickExtra() {
        val p = filePicker
        if (p != null && filePickerOn()) p.open(FilePickMode.Extra, deps.settings.treeRef) { it?.let(::addExtra) }
        else deps.folderPicker.pickExtra()
    }

    /**
     * A save transfer's pick in the list-style picker (MainActivity's pickers, with All files access on): [onResult] gets a
     * file ref, or null when cancelled (or when there is no picker).
     */
    fun pickFile(mode: FilePickMode, onResult: (ContentRef?) -> Unit) {
        val p = filePicker ?: return onResult(null)
        p.open(mode, deps.settings.treeRef, onResult)
    }

    /** New folder's text as typed in the picker's prompt. */
    fun filePickerDraft(text: String) { filePicker?.draft(text) }

    /** B or back on the first-setup offer: go on to the library without an extra (after the Android games step, [afterFolderSteps]). */
    fun skipFirstExtra() { if (_screen.value is Screen.AddFirstExtra) afterFolderSteps { load() } }   // reads the index the home pick just wrote

    /** The Permissions page's All files access row: Android's page for it; the page stays and shows the answer on resume. */
    fun allowAllFiles() {
        if (_screen.value !is Screen.PermissionsStep) return
        deps.allFilesAccess?.openSettings()   // the page stays: its row shows the answer when the app resumes
    }

    /** Continue (or, outside onboarding, B) on the Permissions page: the home pick; the page is never shown again. */
    fun skipAllFiles() {
        if (_screen.value !is Screen.PermissionsStep) return
        deps.settings.allFilesAsked = true
        showPickFolder()
    }

    /**
     * The platform re-read All files access (MainActivity's every resume): back from the setup step's page, setup goes on
     * to the home pick; Library shows the new value. With access on, SAF folders convert to paths
     * ([convertToFiles]).
     */
    fun onAllFilesAccessChanged() {
        deps.allFilesAccess ?: run {
            // No All files access here, but the Microphone access row follows the same resume.
            if (deps.micAccess != null && (_screen.value as? Screen.Settings)?.section == SettingsSection.LIBRARY) settingsUi.refreshSettings()
            return
        }
        if (upgrade.upgradeOn) { upgrade.upgradeAccessChanged(); return }   // the upgrade screen answers it, never a library reload
        if (_screen.value is Screen.PermissionsStep) { showPermissions(keep = true); return }   // back from a prompt: the rows show its answer
        refreshSettingsOn(SettingsSection.LIBRARY)
        // Granted meanwhile: SAF folders on external storage become paths. The main menu reloads at once (rescanning, as the
        // index no longer fits); any other page leaves it to the next load. A load in flight converts first itself. The
        // same when the home itself changed with the access (the saved one back, or the media one in its place).
        if (_screen.value is Screen.Loading) return
        scope.launch {
            val converted = deps.allFilesAccess?.granted() == true && convertToFiles()
            val switched = libraryIndex.snapshot?.let { it.home != home()?.value } == true
            if (!converted && !switched) return@launch
            val now = _screen.value
            if (menuFree(now)) { rememberGamesTab(); load() } else libraryDirty = true
        }
    }

    /**
     * From the platform picker: makes [ref] the home. Refuses a folder that is, contains or lies inside an extra
     * (its files would list twice and the skeleton would land over it). Releases the old home's grant unless the
     * same folder is still in use.
     */
    fun setHome(ref: ContentRef) {
        val old = deps.settings.treeRef
        val extras = deps.settings.extraFolders.map { it.ref }
        val clash = extras.firstOrNull { Sources.overlaps(it, ref) || Sources.overlaps(ref, it) }
        if (clash != null) {
            if (ref != old && ref !in extras) deps.folderPicker.forget(ref)
            _error.value = "That folder overlaps ${deps.source.displayName(clash)}; remove that extra folder first"
            return
        }
        // First setup: no home yet, or a pick from the setup screen (a retry after a failed first load keeps treeRef).
        val first = (old == null && onboardingUi.inFirstSetup()) || _screen.value is Screen.PickFolder
        val fromLibrary = if (first) null else settingsUi.rememberLibraryPage()
        // The data home this change leaves (the full-access folder, or the media one): its saves may be copied over.
        val copyFrom = home()?.takeIf { !first && upgrade.copyOffered(it, ref) }
        deps.settings.treeRef = ref
        if (first) onboardingUi.firstSetup = true
        // First setup's folder steps, saved: a restart before they end comes back to them ([resumeFolderSteps]).
        if (first && deps.settings.onboardingDone == false) deps.settings.onboardingStep = FOLDER_STEPS
        if (old != null && old != ref && old !in extras) deps.folderPicker.forget(old)
        // Once the new home listed: the copy offer when it applies, else back to the Settings page.
        val after = { if (copyFrom != null) upgrade.offerCopySaves(copyFrom, ref, fromLibrary) else fromLibrary?.let(::openSettings) }
        rescan(
            then = {   // runs only on a good scan, so a Roms-folder mispick never shows the offer
                if (first) {
                    folders.showHomePage(ref)
                } else after()
            },
            kept = { after() },   // the same home again, not listing now
        )
    }

    /** The one-time storage upgrade's pages and the copy offer after a home change. */
    // Lazy, as [folders].
    private val upgrade: StorageUpgradeController by lazy { StorageUpgradeController(
        scope, _screen, _selection, _error, deps,
        host = object : StorageUpgradeHost {
            override fun load() = this@AppState.load()
            override suspend fun convertToFiles(): Boolean = this@AppState.convertToFiles()
            override fun mediaPath(media: ContentRef): String = this@AppState.mediaPath(media)
            override fun dataMoved(): Boolean = this@AppState.dataMoved()
            override fun openSettings(section: SettingsSection) = this@AppState.openSettings(section)
            override fun pickHomeFolder(picked: (ContentRef?) -> Unit) {
                val p = filePicker ?: return
                if (filePickerOn()) p.open(FilePickMode.Home, null, picked)
            }
        },
    ) }

    /** A or a tap on the upgrade page's row [index]. */
    fun storageUpgradeRow(index: Int) = upgrade.storageUpgradeRow(index)

    /** The folder picked on the upgrade's pick page ([StorageUpgradeController.storageUpgradePicked]). */
    fun storageUpgradePicked(ref: ContentRef) = upgrade.storageUpgradePicked(ref)

    /** A or a tap on the copy offer's row [index]. */
    fun copySavesRow(index: Int) = upgrade.copySavesRow(index)

    /** From the platform picker: refuses a folder that is, contains or lies inside the home or another extra. */
    fun addExtra(ref: ContentRef) {
        val home = home()
        val clash = (listOfNotNull(home) + deps.settings.extraFolders.map { it.ref }).firstOrNull { Sources.overlaps(it, ref) }
        if (clash != null) {
            // Release the grant the picker just took, unless it is one we still use (the home itself, or an extra picked twice).
            if (ref != clash && deps.settings.extraFolders.none { it.ref == ref }) deps.folderPicker.forget(ref)
            _error.value = when {
                ref != clash -> "That folder overlaps ${deps.source.displayName(clash)}; files would list twice."
                clash == home -> "That folder is your home folder."
                else -> "That folder is already added."
            }
            return
        }
        val id = Sources.uniqueId(Sources.idFor(deps.source.displayName(ref), ref), deps.settings.extraFolders.map { it.id })
        deps.settings.extraFolders = deps.settings.extraFolders + ExtraFolder(id, ref)
        libraryDirty = true
        if (_screen.value is Screen.AddFirstExtra) { onboardingUi.addedExtraId = id; rescan { folders.showFolderSummary(id) }; return }   // the first-setup offer: its summary

        val fromLibrary = settingsUi.rememberLibraryPage()
        rescan {
            fromLibrary?.let { settingsUi.showSettingsOn(it, SettingsKind.ADD_EXTRA) }   // still on Add ROM folder, below the new row
            val left = libraryIndex.snapshot?.let { folderSummary(it, id).unassigned } ?: 0
            if (left > 0) showNotice(if (left == 1) "1 game needs a console: Library → Unassigned games." else "$left games need a console: Library → Unassigned games.")
        }
    }

    /**
     * A touch on a Settings value page ([com.nxredux.mobile.ui.nx.NxSettingsList]): a segment or the switch sets that
     * value, a picker row opens its picker, the info icon shows the row's full description.
     */
    fun settingEvent(event: NxSettingEvent) {
        (_screen.value as? Screen.EmulatorOptions)?.let { emulatorSettingEvent(it, event); return }
        // RetroAchievements' and Artwork's settings: their controllers set values; the info popup is this page's.
        (_screen.value as? Screen.RaSettings)?.let {
            if (event is NxSettingEvent.Info) { if (!it.signingIn && it.confirm == null) showSettingInfo(event.row) } else ra.onRaSettingEvent(it, event)
            return
        }
        (_screen.value as? Screen.ArtworkSettings)?.let {
            if (event is NxSettingEvent.Info) { if (!it.dialogOpen) showSettingInfo(event.row) } else artworkScreens?.onSettingEvent(it, event)
            return
        }
        when (event) {
            is NxSettingEvent.Choose -> chooseSetting(event.row, event.index)
            is NxSettingEvent.OpenPicker -> openSettingPicker(event.row)
            is NxSettingEvent.Info -> showSettingInfo(event.row)
        }
    }

    /**
     * A touch on an Emulator Settings / Game Settings row (selecting it): a segment or the switch writes that value as
     * stepping there would, a picker row opens its picker, the info icon shows the row's full description.
     */
    private fun emulatorSettingEvent(s: Screen.EmulatorOptions, event: NxSettingEvent) {
        val row = when (event) {
            is NxSettingEvent.Choose -> event.row
            is NxSettingEvent.OpenPicker -> event.row
            is NxSettingEvent.Info -> event.row
        }
        if (s.rows.getOrNull(row)?.selectable != true) return
        select(row)
        when (event) {
            is NxSettingEvent.Choose -> emulatorSettings.choose(event.index)
            is NxSettingEvent.OpenPicker -> openEmulatorPicker(s)
            is NxSettingEvent.Info -> showSettingInfo(row)
        }
    }

    /**
     * The selected Emulator Settings value row's picker ([ValueChooser]); a pick writes it
     * ([EmulatorSettingsController.choose]). False when it has none. A current value not among the choices marks none.
     */
    private fun openEmulatorPicker(s: Screen.EmulatorOptions): Boolean {
        val (title, labels, current) = emulatorSettings.choices(s)?.takeIf { it.second.size >= 2 } ?: return false
        settingsUi.openPicker(title, labels, current) { emulatorSettings.choose(it) }
        return true
    }

    /**
     * A pad's A and X on an Emulator Settings / Game Settings page, before the page's own keys: A on a picker row opens
     * its picker, X shows the selected row's info when it has more to say. False for every other press.
     */
    private fun onEmulatorTouchButton(s: Screen.EmulatorOptions, button: NxButton): Boolean {
        if (s.confirmReset) return false   // the reset question takes the press
        val touch = settingsUi.settingTouchRow(_selection.value.index) ?: return false
        return when {
            button == NxButton.A && touch.choices.size >= 2 && touch.control == NxSettingControl.PICKER -> openEmulatorPicker(s)
            button == NxButton.X -> { if (settingsUi.selectedSettingShowsInfo()) showSettingInfo(_selection.value.index); true }
            else -> false
        }
    }

    /** The game list (and its selection) a game's Game Settings modal is open over; null while none is. */
    fun gameSettingsUnder(): Pair<Screen, ListSelection>? =
        if (gameSettingsModal(_screen.value)) emulatorSettings.gameUnder else null

    /** A tap on the Game Settings modal's scrim: closes it from any level, back to the game list on the same game. */
    fun closeGameSettings() {
        (_screen.value as? Screen.EmulatorOptions)?.takeIf(::gameSettingsModal)?.let(emulatorSettings::closeGame)
    }

    /** Rebuilds the Settings rows when one of [sections] is the open page. */
    private fun refreshSettingsOn(vararg sections: SettingsSection) {
        if ((_screen.value as? Screen.Settings)?.section in sections) settingsUi.refreshSettings()
    }

    private val localRomFinder = LocalRomFinder(deps.source, deps.privateFiles)

    /**
     * The library GBA ROM (a GBA or MGBA folder, whichever core plays it) whose SHA-1 is [hash] and size [sizeBytes], as a ref value; null
     * when there is none or [tag] is not a GBA tag. Hashes on [AppDeps.compute], each ROM once per change.
     */
    suspend fun findLocalRom(hash: String, sizeBytes: Long, tag: String): String? {
        if (tag != "GBA" && tag != "MGBA") return null
        val games = (libraryIndex.current() ?: return null).systems.filter { it.tag == "GBA" || it.tag == "MGBA" }.flatMap { it.games }
        return withContext(deps.compute) { localRomFinder.find(hash, sizeBytes, games) }
    }

    /** A save transfer's report: its progress notice stays until the result (done) replaces it. */
    private fun showTransferNotice(text: String, done: Boolean) = showNotice(text, sticky = !done)

    /**
     * Import saves from [source]: [progress] and [done] by default are Settings' (a sticky progress notice, then
     * [settingsImportDone]); onboarding passes its own.
     */
    private fun importSaves(
        source: ImportSource,
        progress: (String) -> Unit = { showNotice(it, sticky = true) },
        done: (ImportResult) -> Unit = ::settingsImportDone,
    ) = deps.saveTransfer?.importSaves(source, progress, done)

    /**
     * A Settings import's end: finished with Import saves still shown, the Import results page (on its first row);
     * finished anywhere else, its summary as a notice; anything else, a notice.
     */
    private fun settingsImportDone(result: ImportResult) {
        when (result) {
            is ImportResult.Done -> {
                settingsContent.importResult = result.summary
                if ((_screen.value as? Screen.Settings)?.section != SettingsSection.IMPORT_SAVES) return showNotice(result.summary.lines().joinToString("; "))
                dismissNotice()
                settingsUi.forgetSection(SettingsSection.IMPORT_RESULT)
                openSettings(SettingsSection.IMPORT_RESULT)
            }
            is ImportResult.Message -> showNotice(result.text)
        }
    }

    /** Import BIOS from [source]: a sticky progress notice, then the results page (or a notice). */
    private fun importBios(source: com.nxredux.mobile.bios.BiosSource) =
        deps.biosIntake?.import(source, progress = { showNotice(it, sticky = true) }, done = ::biosImportDone)

    private fun biosImportDone(result: com.nxredux.mobile.bios.BiosIntakeResult) {
        when (result) {
            is com.nxredux.mobile.bios.BiosIntakeResult.Done -> {
                topScreen.forgetBios()
                if ((_screen.value as? Screen.Settings)?.section != SettingsSection.IMPORT_BIOS) return showNotice(result.summary.headline())
                dismissNotice()
                _screen.value = Screen.BiosImportResult(result.summary)
                _selection.value = ListSelection(0, 1)   // Done, the page's one button
            }
            is com.nxredux.mobile.bios.BiosIntakeResult.Message -> showNotice(result.text)
        }
    }

    /** Done (A, a tap or B) on the BIOS import's results: back to Library on its Import BIOS row. */
    fun biosResultDone() = settingsUi.showSettingsOn(SettingsSection.LIBRARY, SettingsKind.IMPORT_BIOS)

    /**
     * Rescan (Library's counts row): a full scan under the loading overlay (Settings stays underneath), then [section] again on the
     * [row] (Library on Rescan, or Emulators on Default emulators after a changed default) with a one-line result. A
     * failed scan that keeps the index returns there too, its notice or error the result. No confirm: a scan is
     * harmless. B cannot stop it: the scan commits in steps (the in-memory index, the stored copy, then dropped extras
     * and the main rows), so a cancel between them would leave them disagreeing.
     */
    private fun rescanFromSettings(section: SettingsSection = SettingsSection.LIBRARY, row: SettingsKind = SettingsKind.RESCAN) {
        val root = home() ?: run { showPickFolder(); return }
        dismissNotice()   // a tap reaches openSelected without onButton's dismissal; a notice raised by this scan still wins below
        val backToRow = { settingsUi.showSettingsOn(section, row) }
        loader.start(message = "Scanning library…", cancellable = false) {
            scanNow(
                root,
                then = { systems ->
                    backToRow()
                    if (_notice.value == null) showNotice("Library rescanned: ${gamesLabel(systems.sumOf { it.gameCount })}")   // a Lost access or not-available notice from this scan wins
                },
                kept = backToRow,
            )
        }
    }

    /**
     * The tabs Layouts shows ([tabSetting]). Tools too: a hidden Tools (and Settings in it) stays reachable from MENU and
     * a long press ([hiddenTabs]).
     */
    private fun shownTabs(): Set<MenuCategory> = MenuCategory.entries.filterTo(HashSet()) { settingsContent.tabSetting(it) }

    /**
     * The tabs Layouts hides that have content ([hiddenMenuTabs]): MENU's and the long press's items; none over a pushed
     * tab. Read live for the menu's items; the screen carries the same list ([Screen.Systems.hidden]) for the UI's hints.
     */
    internal fun hiddenTabs(): List<MenuCategory> =
        if (_screen.value.menuPushed) emptyList() else hiddenMenuTabs(kidsMenuTabs, menuTabs)

    /**
     * B on a sub-page: its section on the row that opened it. Leaving Default emulators (under Emulators) with a changed
     * default (the index no longer fits) rescans first, under the overlay, so the library is the new one's. Import results
     * goes back to Saves on its Import saves row.
     */
    private fun leaveSubPage(section: SettingsSection) {
        if (section == SettingsSection.IMPORT_RESULT) return settingsUi.showSettingsOn(SettingsSection.SAVES, SettingsKind.IMPORT_SAVES)   // past Import saves
        val parent = section.parent ?: return
        val opener = section.opener ?: return
        val root = home()
        val stale = section == SettingsSection.DEFAULT_EMULATORS && root != null &&
            libraryIndex.snapshot?.fits(root, deps.settings.extraFolders, deps.settings.defaultTags) != true
        if (stale) rescanFromSettings(parent, opener) else settingsUi.showSettingsOn(parent, opener)
    }

    fun removeExtra(extra: ExtraFolder) {
        deps.settings.extraFolders = deps.settings.extraFolders.filter { it.id != extra.id }
        libraryDirty = true
        deps.folderPicker.forget(extra.ref)
        settingsUi.refreshSettings()
        val root = home() ?: return
        reindexJob = scope.launch {   // background re-index without Loading; Back then reads it ([fittingIndex] waits for it)
            // A failed re-index leaves the old index: it no longer fits, so Back rescans.
            tryQuietly {
                scanIndex(root, IndexRefresh(counts = true))
                libraryDirty = false   // the index is this re-index's
                collectionCounter.refreshCollectionNames()
                refreshMainRows()   // the consoles and the collection counts follow the new index, names changed or not
                // Still on a Library page: the rows were built before this scan, so refresh them (selection clamped).
                (_screen.value as? Screen.Settings)?.takeIf { it.section.library }?.let { s ->
                    val page = settingsUi.settingsPage(s.section, s.confirmRemove)
                    _screen.value = page
                    _selection.value = settingsUi.selectableOn(page, _selection.value)
                }
            }
        }
    }

    /** The console's next default tag ([step] -1: the previous one, for LEFT). Leaving Default emulators rescans. */
    fun cycleDefaultTag(consoleId: String, step: Int = 1) {
        settingsContent.settingValue(SettingsRow(SettingsKind.DEFAULT_TAG, "", consoleId = consoleId))?.change(step)   // one tag (or none): nothing to pick
    }

    /** Settings sections and the section menu: UP/DOWN move, A opens or acts, B goes back. */
    private fun onSettingsButton(s: Screen, button: NxButton) {
        (s as? Screen.Settings)?.takeIf { it.nameDraft != null }?.let { page ->
            when (button) {
                // An emptied name goes back to the device's; the device's own name left as it was shown is not saved, so
                // the name keeps following the device.
                NxButton.A -> {
                    val typed = PlayerName.clean(page.nameDraft)
                    if (typed != PlayerName.resolve(deps.settings.playerName, deps.deviceName())) deps.settings.playerName = typed
                    _screen.value = page.copy(nameDraft = null)
                    settingsUi.refreshSettings()
                }
                NxButton.B -> _screen.value = page.copy(nameDraft = null)
                else -> Unit
            }
            return
        }
        (s as? Screen.Settings)?.takeIf { it.confirmReset }?.let { page ->
            when (button) {
                NxButton.A -> { _screen.value = page.copy(confirmReset = false); settingsUi.runSettingsReset(page) }
                NxButton.B -> _screen.value = page.copy(confirmReset = false)
                else -> Unit
            }
            return
        }
        (s as? Screen.Settings)?.confirmDriver?.let { driver ->
            when (button) {
                NxButton.A -> settingsContent.removeGpuDriver(driver)
                NxButton.B -> _screen.value = s.copy(confirmDriver = null)
                else -> Unit
            }
            return
        }
        (s as? Screen.Settings)?.confirmRemove?.let { extra ->
            when (button) {
                NxButton.A -> removeExtra(extra)
                NxButton.B -> settingsUi.refreshSettings()
                else -> Unit
            }
            return
        }
        when (button) {
            // Past System's group labels and gaps.
            NxButton.UP, NxButton.DOWN -> _selection.value = if (s is Screen.Settings) settingsUi.settingsStep(s.rows, _selection.value, if (button == NxButton.UP) -1 else 1)
                else if (button == NxButton.UP) _selection.value.up() else _selection.value.down()
            NxButton.A -> openSelected()
            NxButton.B -> back()
            NxButton.X -> (s as? Screen.Settings)?.rows?.getOrNull(_selection.value.index)?.let { row ->
                if (row.kind == SettingsKind.SHORTCUT_BIND) row.shortcut?.let { controlBindings.setShortcut(it, null) }   // back to None
                else if (settingsUi.selectedSettingShowsInfo()) showSettingInfo(_selection.value.index)
            }
            // A value row being changed ([onRowEditButton], after A) steps on LEFT/RIGHT; a default emulator steps through
            // its console's tags.
            NxButton.LEFT, NxButton.RIGHT -> if (s is Screen.SettingsMenu) {
                // Two panes: RIGHT on a category moves into its page, like A. One page has no pane to go to.
                if (settingsUi.settingsSplit && button == NxButton.RIGHT) openSelected()
            } else (s as? Screen.Settings)?.rows?.getOrNull(_selection.value.index)?.let { row ->
                if (row.enabled) settingsContent.settingValue(row)?.change(if (button == NxButton.LEFT) -1 else 1)   // a dimmed row (one tag) stays
            }
            else -> Unit
        }
    }

    /** The Unassigned pseudo-console when it has a visible game ([playableSystems] drops it empty); Settings -> Library opens it. */
    private fun unassignedSystem(): SystemFolder? = systemsCache.firstOrNull { it.unassigned }

    /**
     * Shows the list of [system]'s console (every playable system of its display name, see [consoleGroup]) straight from
     * the index: no coroutine, no folder listing. [select]: put the row on that game path.
     */
    fun openSystem(system: SystemFolder, select: String? = null) {
        if (system.core == null && !system.unassigned && !system.android) {
            _error.value = "No core for ${system.displayName} (${system.tag}) yet."
            return
        }
        if (_screen.value is Screen.Systems) rememberMenuSelection()   // an in-place switch opens from a game list
        val source = ListSource.Console(consoleGroup(system) ?: listOf(system))
        val entries = consoleEntries(source.systems)
        showList(source, entries, select?.let { p -> entries.indexOfFirst { it.game.path == p } } ?: 0)
    }

    /**
     * The playable systems shown as [system]'s console row: Unassigned alone, the Android games console as it is now
     * alone, else every one of its display name, in library order. Null when none is.
     */
    private fun consoleGroup(system: SystemFolder): List<SystemFolder>? =
        if (system.android) androidConsole?.let(::listOf)
        else if (system.unassigned) systemsCache.firstOrNull { it.unassigned }?.let(::listOf)
        else systemsCache.filter { !it.unassigned && it.displayName == system.displayName }.ifEmpty { null }

    /**
     * A console's rows: one system's games as indexed, or a merged console's games from every tag by shown name
     * (ignoring case, the tag breaking ties), a name shared across the list labelled as NX Redux does ([MergedLabels]).
     */
    private fun consoleEntries(systems: List<SystemFolder>): List<Listed> {
        val cache = consoleEntriesOf?.takeIf { it.first === systemsCache }?.second
            ?: HashMap<List<String>, Pair<List<SystemFolder>, List<Listed>>>().also { consoleEntriesOf = systemsCache to it }
        val key = systems.map { it.tag }
        cache[key]?.let { (from, entries) -> if (from.size == systems.size && from.indices.all { from[it] === systems[it] }) return entries }
        return buildConsoleEntries(systems).also { cache[key] = systems to it }
    }

    /**
     * [consoleEntries] built for the [systemsCache] it was: a merged console's sort and labels run over all its games,
     * and dual screen asks again on every highlight. Keyed by tags, checked by the folders' identity (the Android
     * console is rebuilt outside [systemsCache]).
     */
    private var consoleEntriesOf: Pair<List<SystemFolder>, HashMap<List<String>, Pair<List<SystemFolder>, List<Listed>>>>? = null

    /** The dumps DS Download Play boots from are in Bios/NDS/, as last read with the library ([showSystems]). */
    private var dsMenuReady = false

    private fun buildConsoleEntries(systems: List<SystemFolder>): List<Listed> = DsMenu.withEntry(systems, gameEntries(systems), dsMenuReady)

    private fun gameEntries(systems: List<SystemFolder>): List<Listed> {
        systems.singleOrNull()?.let { s -> return s.games.map { Listed(s, it) } }
        val all = systems.flatMap { s -> s.games.map { Listed(s, it) } }.sortedWith(compareBy({ it.game.shownName.lowercase() }, { it.system.tag }))
        val labels = MergedLabels.labels(all.map { MergedLabels.Row(it.game.shownName, it.game.fileName, it.system.tag) })
        return all.mapIndexed { i, e -> labels[i]?.let { e.copy(label = it) } ?: e }
    }

    /**
     * The Games tab's rows ([AllGames.entries]) of [systemsCache] and [androidConsole], built once per library state:
     * kept while both are the same objects, rebuilt when either is replaced.
     */
    internal fun allGames(): List<Listed> {
        val android = androidConsole
        allGamesOf?.takeIf { it.systems === systemsCache && it.android === android }?.let { return it.entries }
        return AllGames.entries(systemsCache, android).also { allGamesOf = AllGamesCache(systemsCache, android, it) }
    }

    private class AllGamesCache(val systems: List<SystemFolder>, val android: SystemFolder?, val entries: List<Listed>)
    private var allGamesOf: AllGamesCache? = null

    private fun showList(source: ListSource, entries: List<Listed>, select: Int = 0) {
        val screen = Screen.Games(source, entries)
        landing(_screen.value, screen)
        gridColumns = 0   // a new list: its grid reports its own columns on its first layout
        _screen.value = screen
        _selection.value = ListSelection(select.coerceIn(0, (entries.size - 1).coerceAtLeast(0)), entries.size)
        refreshResume()
    }

    // --- List sources: resolving recents, collections and hidden lines against the index ---

    /** A recents/pins/collection line resolved against the playable cache; null when hidden, coreless, Unassigned or not indexed. */
    private fun resolveOne(path: String): Listed? = resolveIn(playable(), path)

    /** [LibraryIndexHolder.playable] of [systems] (the playable cache by default). */
    private fun playable(systems: List<SystemFolder> = systemsCache): Map<GameRef, Listed> = libraryIndex.playable(systems)

    /** [playable], built off the main thread when it is not built yet ([LibraryIndexHolder.playableOffMain]). */
    private suspend fun playableOffMain(systems: List<SystemFolder> = systemsCache): Map<GameRef, Listed> = libraryIndex.playableOffMain(systems)

    /** Hidden games resolved against the whole library (coreless and Unassigned included) so every one can be unhidden. */
    private suspend fun hiddenEntries(): List<Listed> {
        val lookup = libraryIndex.hiddenLookup() ?: return emptyList()
        return hiddenPaths.mapNotNull { resolveIn(lookup, it) }
    }

    private suspend fun entriesFor(source: ListSource): List<Listed> = when (source) {
        is ListSource.Console -> consoleGroup(source.system)?.let(::consoleEntries) ?: emptyList()
        ListSource.Recent -> deps.userData.readRecents().map { it.path }.let { paths -> resolveDistinct(playableOffMain(), paths) }
        is ListSource.Collection -> collections.read(source.name).let { lines -> playableOffMain().let { lookup -> lines.mapNotNull { resolveIn(lookup, it) } } }
        ListSource.Hidden -> hiddenEntries()
        is ListSource.Search -> GameSearch.search(searchCorpus(), source.query)
        is ListSource.Filtered -> GameSearch.filter(entriesFor(source.parent), source.query)
        ListSource.AllGames -> allGames()
    }

    /**
     * Opens [source]'s list. A console list needs no I/O; the others read their file first (small, so no Loading screen).
     * An empty result leaves the screen as it was (an empty collection stays on the Collections page).
     */
    fun openList(source: ListSource) {
        if (source is ListSource.Console) { openSystem(source.system); return }
        rememberSelection()
        val here = _screen.value
        scope.launch {
            val entries = entriesFor(source)
            if (_screen.value !== here) return@launch   // the user navigated while the file was read
            if (entries.isNotEmpty()) showList(source, entries)
        }
    }

    /** What START searches: the playable consoles' games (no Unassigned, nothing without a core; Android apps are not in the cache). */
    private fun searchCorpus(): List<SystemFolder> = systemsCache.filter { !it.unassigned && it.core != null }

    /**
     * START's search confirmed on the main menu: [query]'s matches as a game list, even none ("No results"), B landing
     * back on the tab and row it was typed on. Blank, or off the main menu: nothing.
     */
    fun showSearch(query: String) {
        val q = query.trim()
        if (q.isEmpty() || !_screen.value.isMainMenu) return
        lastSearch = q
        rememberMenuSelection()
        intendNav(NavMotion.FORWARD)
        showList(ListSource.Search(q), GameSearch.search(searchCorpus(), q))
    }

    /**
     * The main menu has something for START to search (its hint shows only then). [systemsCache] is not Compose state:
     * the hint reads this on the recomposition a Screen change drives (a scan lands as a new Screen).
     */
    val canSearch: Boolean get() = searchCorpus().any { it.games.isNotEmpty() }

    /**
     * START on the main menu (content or tab row): the search prompt, on the last search. Not over a menu. On the Games
     * tab it is the list's [Screen.Games.search] field, which there is this prompt (the tab has no list search of its own).
     */
    fun openSearch() {
        when (val s = _screen.value) {
            is Screen.Systems -> if (s.menu == null && s.search == null && s.layoutMenu == null && canSearch) _screen.value = s.copy(search = lastSearch)
            is Screen.Games -> if (s.isTab && s.menu == null && s.search == null && s.layoutMenu == null && canSearch) _screen.value = s.copy(search = lastSearch)
            else -> Unit
        }
    }

    /** Player name's text as typed in its prompt, cut to what a console holds. */
    fun playerNameDraft(text: String) {
        (_screen.value as? Screen.Settings)?.takeIf { it.nameDraft != null }?.let { _screen.value = it.copy(nameDraft = text.take(PlayerName.MAX)) }
    }

    /** The search prompt's live text (the composable reports every change), so A can confirm with it. */
    fun searchDraft(text: String) {
        when (val s = _screen.value) {
            is Screen.Systems -> if (s.search != null) _screen.value = s.copy(search = text)
            is Screen.Games -> if (s.search != null) _screen.value = s.copy(search = text)   // a game list's own search
            else -> Unit
        }
    }

    /**
     * The search prompt has the buttons: A searches (blank: stays open), B closes, and START too (it toggles the prompt,
     * as NX Redux's KB_START_CANCELS); the rest are the text field's.
     */
    private fun onSearchButton(s: Screen, draft: String, button: NxButton) {
        val closed = when (s) { is Screen.Systems -> s.copy(search = null); is Screen.Games -> s.copy(search = null); else -> return }
        when (button) {
            NxButton.A -> if (draft.isNotBlank()) { _screen.value = closed; showSearch(draft) }
            NxButton.B, NxButton.START -> _screen.value = closed
            else -> Unit
        }
    }

    /** Where B from a list lands. */
    private fun leaveList(source: ListSource) {
        when (source) {
            is ListSource.Console -> if (source.system.unassigned) backToLibraryRow(SettingsKind.UNASSIGNED) else showSystemsNow { it is MainRow.System && it.system.displayName == source.system.displayName }
            ListSource.Recent -> showSystemsNow()
            is ListSource.Collection -> showSystemsNow(select = { it is MainRow.Collection && it.name == source.name })
            ListSource.Hidden -> backToLibraryRow(SettingsKind.HIDDEN)
            ListSource.AllGames -> showSystemsNow(MenuCategory.GAMES)   // the Games tab itself
            is ListSource.Search -> showSystemsNow()   // the tab and row remembered when the search opened
            is ListSource.Filtered -> leaveListSearch(source)
        }
    }

    // --- A game list's own search and its Layout dropdown ---

    /** The row of the list a search was opened on: B from its results lands there when their game is not in it. */
    private var listSearchFrom = 0

    /** START (or the Search icon) on a game list: its search prompt, on the query of the results shown (else empty). */
    fun openListSearch() {
        val s = _screen.value as? Screen.Games ?: return
        if (s.menu != null || s.search != null || s.layoutMenu != null || !s.source.searchable) return
        _screen.value = s.copy(search = (s.source as? ListSource.Filtered)?.query.orEmpty())
    }

    /** The list's search prompt has the buttons: A searches (blank: stays open), B and START close it; the rest are the field's. */
    private fun onListSearchButton(s: Screen.Games, draft: String, button: NxButton) {
        when (button) {
            NxButton.A -> if (draft.isNotBlank()) showListSearch(s, draft.trim())
            NxButton.B, NxButton.START -> _screen.value = s.copy(search = null)
            else -> Unit
        }
    }

    /**
     * [query]'s matches among the list's games ([GameSearch.filter]) as a list of their own, even none ("No results").
     * From the results, a new query searches the same list again, in place.
     */
    private fun showListSearch(s: Screen.Games, query: String) {
        val parent = s.source.base
        val again = s.source is ListSource.Filtered
        if (!again) { listSearchFrom = _selection.value.index; intendNav(NavMotion.FORWARD) }
        scope.launch {
            val all = if (again) entriesFor(parent) else s.entries
            if (_screen.value !== s) return@launch   // the user moved on meanwhile
            showList(ListSource.Filtered(parent, query), GameSearch.filter(all, query))
        }
    }

    /** B from a list's search results: the list again, on the game highlighted in the results (else the row it was opened on). */
    private fun leaveListSearch(source: ListSource.Filtered) {
        val here = _screen.value
        val picked = (here as? Screen.Games)?.entries?.getOrNull(_selection.value.index)?.game?.path
        scope.launch {
            val entries = entriesFor(source.parent)
            if (_screen.value !== here) return@launch
            if (entries.isEmpty()) { leaveList(source.parent); return@launch }
            val at = entries.indexOfPath(picked) ?: listSearchFrom
            showList(freshSource(source.parent), entries, at)
        }
    }

    /** [source], a console's with its systems as they are now (a hide may have emptied one tag of a merged console). */
    private fun freshSource(source: ListSource): ListSource =
        (source as? ListSource.Console)?.let { c -> consoleGroup(c.system)?.let { ListSource.Console(it) } } ?: source

    /**
     * The Layout icon: opens the dropdown on the layout in use, or closes it when open; on every main menu tab (whose
     * layout is that tab's own, or the game lists' on Games) and every game list. While it is open the screen's own
     * keys are the dropdown's ([onPageButton], [tabRowOf]).
     */
    fun toggleLayoutMenu() {
        if (!deps.settings.gameListLayoutOptions) return
        when (val s = _screen.value) {
            is Screen.Games -> {
                if (s.menu != null || s.search != null) return
                _screen.value = s.copy(layoutMenu = if (s.layoutMenu != null) null else GameListLayouts.indexOf(deps.settings.gameListStyle, deps.settings.gameListOrientation, deps.settings.gameListVerticalAlignment, _dualMenu.value))
            }
            is Screen.Systems -> {
                val category = s.category ?: return
                if (s.menu != null || s.search != null || MenuTabLayouts.choices(category).isEmpty()) return
                val style = MenuStyles.styleOf(deps.settings.menuStyles, category)
                val orientation = MenuStyles.orientationOf(deps.settings.menuOrientations, category)
                _screen.value = s.copy(layoutMenu = if (s.layoutMenu != null) null else MenuTabLayouts.indexOf(category, style, orientation))
            }
            else -> Unit
        }
    }

    /** The Layout dropdown has the buttons: UP / DOWN move, A picks, B closes. */
    private fun onLayoutMenuButton(s: Screen.Games, at: Int, button: NxButton) {
        when (button) {
            NxButton.UP -> _screen.value = s.copy(layoutMenu = (at - 1).coerceAtLeast(0))
            NxButton.DOWN -> _screen.value = s.copy(layoutMenu = (at + 1).coerceAtMost(GameListLayouts.choices(_dualMenu.value).lastIndex))
            NxButton.A -> pickLayout(at)
            NxButton.B -> _screen.value = s.copy(layoutMenu = null)
            else -> Unit
        }
    }

    /** The main menu tab's Layout dropdown has the buttons: UP / DOWN move, A picks, B closes. */
    private fun onLayoutMenuButton(s: Screen.Systems, at: Int, button: NxButton) {
        val last = (s.category?.let(MenuTabLayouts::choices)?.lastIndex ?: -1).coerceAtLeast(0)
        when (button) {
            NxButton.UP -> _screen.value = s.copy(layoutMenu = (at - 1).coerceAtLeast(0))
            NxButton.DOWN -> _screen.value = s.copy(layoutMenu = (at + 1).coerceAtMost(last))
            NxButton.A -> pickLayout(at)
            NxButton.B -> _screen.value = s.copy(layoutMenu = null)
            else -> Unit
        }
    }

    /**
     * [index]'s layout for every game list (Layouts → Game lists layout, its orientation for Carousel and Backdrop, and a
     * vertical one's alignment) or for the current main menu tab (Layouts → that tab's layout and orientation); the
     * dropdown closes.
     */
    fun pickLayout(index: Int) {
        when (val s = _screen.value) {
            is Screen.Games -> {
                val layout = GameListLayouts.choices(_dualMenu.value).getOrNull(index) ?: return
                deps.settings.gameListStyle = layout.style
                if (MenuStyles.hasOrientation(layout.style)) deps.settings.gameListOrientation = layout.orientation
                layout.alignment?.let { deps.settings.gameListVerticalAlignment = it }
                gridColumns = 0   // a Grid reports its own columns on its first layout
                _screen.value = s.copy(layoutMenu = null)   // a new screen: the page redraws in the new style, on the same game
            }
            is Screen.Systems -> {
                val category = s.category ?: return
                val layout = MenuTabLayouts.choices(category).getOrNull(index) ?: return
                deps.settings.menuStyles = deps.settings.menuStyles + (category to layout.style)
                if (category != MenuCategory.HOME && MenuStyles.hasOrientation(layout.style)) {
                    deps.settings.menuOrientations = deps.settings.menuOrientations + (category to layout.orientation)
                }
                gridColumns = 0   // a Grid reports its own columns on its first layout
                _screen.value = s.copy(layoutMenu = null)   // a new screen: the page redraws in the new style
                if (category == MenuCategory.HOME) rebuildHomeTab()
            }
            else -> Unit
        }
    }

    /**
     * B from the Unassigned or Hidden list: Settings -> Library on its [kind] row, else where the page was left (its last
     * game assigned away or unhidden).
     */
    private fun backToLibraryRow(kind: SettingsKind) {
        openSettings(SettingsSection.LIBRARY)
        val s = _screen.value as? Screen.Settings ?: return
        val at = s.rows.indexOfFirst { it.kind == kind }
        if (at >= 0) _selection.value = ListSelection(at, s.rows.size)
    }

    private fun rememberSelection() {
        when (val s = _screen.value) {
            is Screen.Systems, is Screen.Games -> rememberMenuSelection()   // the main menu's (any other list: nothing)
            is Screen.Settings -> settingsUi.rememberSection(s.section, _selection.value)
            else -> Unit
        }
    }

    /** Rebuilds the open screen from its source after an action; an emptied list leaves as B would. [select]: put the row on that game path. */
    private suspend fun refreshOpenList(select: String? = null) {
        when (val s = _screen.value) {
            is Screen.Games -> if (s.isTab) refreshGamesTab(s, select) else {
                val entries = entriesFor(s.source)
                if (_screen.value !== s) return   // the user navigated while the source was read
                if (entries.isEmpty()) { leaveList(s.source); return }
                val index = entries.indexOfPath(select) ?: _selection.value.index
                // A background refresh (a browsing art fetch landing) must not close what the user has open: actions
                // that change the list close their menu before refreshing, so whatever is still open here stays.
                _screen.value = Screen.Games(
                    freshSource(s.source), entries, menu = keptMenu(s.menu, entries), search = s.search, layoutMenu = s.layoutMenu,
                )
                _selection.value = ListSelection(index, entries.size).clampedTo(entries.size)
                refreshResume()
            }
            is Screen.Systems -> when (val m = menuScreen(s.category ?: menuCategory)) {
                is Screen.Systems -> {
                    val kept = if (s.menu != null && m.category == s.category) m.copy(menu = s.menu) else m
                    showMenu(kept, keptIndex(s, m))   // a gone tab: the fallback's own row
                }
                else -> showMenu(m)
            }
            else -> Unit
        }
    }

    /**
     * [refreshOpenList] on the Games tab [s]: the tab as last built ([rebuildMainRows] ran first), on [select] else the
     * highlighted game (gone: the nearest row), an open menu kept as a list keeps it. The last game gone, the tab goes and
     * the menu lands on the next tab, never on an empty list.
     */
    private fun refreshGamesTab(s: Screen.Games, select: String?) {
        val m = menuScreen(s.menuTab ?: menuCategory)
        if (m !is Screen.Games) { showMenu(m); return }
        val kept = if (s.menu != null) m.copy(menu = keptMenu(s.menu, m.entries)) else m
        val at = m.entries.indexOfPath(select) ?: keptRow(s, m)
        showMenu(kept, at)
        rememberMenuSelection()
    }

    // --- Launching ---

    /**
     * The app came to the foreground (a card may have been inserted meanwhile). Only on the main menu with nothing open,
     * so a list or a page never changes under the user: an extra recorded offline that is mounted again rescans (as
     * [load] would), and a home that was offline at start gets the probe and skeleton it skipped. No tree query while
     * nothing came back. Home's stats are recomputed and its rows rebuilt (Continue's save may be new); that
     * rebuild uses app-private reads only (recents, pins, resume markers, tool pins, the in-memory index), never the tree.
     */
    /** Play time moved outside the menu's own reads (a killed game's session closed): the screens read it again. */
    fun playTimeChanged() = refreshGameInfo()

    fun onForeground() {
        kidsUi.kidsReturned()   // back from Usage access or Alarms & reminders: a setup step moves on once granted
        kidsUi.kidsHomeCheck()  // back from setup's home question: on once home
        val switcherBack = switcher.onForeground()
        checkArt(reuse = true)   // art deleted or added while away (only changed art folders are listed)
        if (!launches.minimalPending) launches.clearMinimalOpening()
        onLauncherChanged()   // back from Android's home settings or the role dialog: System re-reads its state
        onRotationChanged()   // auto-rotate may have been switched meanwhile
        refreshInstalled()    // apps may have been installed or removed meanwhile
        refreshGameInfo()   // back from a game (or another app): its time and unlocks may have moved
        homeTab.refreshStatsIfAsked()   // Home's stats too (before the menu's first show, that show computes them)
        // Back from a game opened from the switcher, which stayed up under it: its cards read again in place, no slide, and
        // Home's rows rebuilt behind it. A switcher game whose switcher went meanwhile (a Home landing) reopens it once
        // ([SwitcherController.reopenSwitcher]): over the main menu after the rows rebuilt below (a swap would abandon an open under way).
        // Back on a list or a page: Home's rows are rebuilt all the same (the page stays as it is; [swapMainRows] only acts
        // on the menu), so B to the menu shows Continue's new resume frame, not the one read when the game launched.
        if (switcherBack != null) { switcherBack(); return }
        // The main menu: any tab, the Games tab too.
        val s = _screen.value.takeIf { it.isMainMenu } ?: run { scope.launch { refreshMainRows() }; switcher.reopenSwitcher(); return }
        if (s.contextMenu != null) { switcher.dropSwitcherReturn(); return }
        if (!onboardingUi.firstSetup) updaterOnMainMenu()   // back over the menu (from a game, or the install-permission screen)
        scope.launch { refreshMainRows(); switcher.reopenSwitcher() }   // Continue's save (its resume frame) may be new since the rows were built
        val root = home() ?: return
        if (foregroundJob?.isActive == true) return   // a quick second onStart: the first check (or its scan) covers it
        foregroundJob = scope.launch {
            val ix = libraryIndex.current() ?: return@launch
            if (backOnline(ix)) {
                if (_screen.value !== s) return@launch   // the user moved on during the check
                rememberGamesTab()   // the scan lands on the tab's game again, as [rescan]'s
                _screen.value = Screen.Loading
                scanNow(root, null)
                return@launch
            }
            if (offlineHomeSaid == root && !deps.source.isOffline(root) && _screen.value === s) afterIndexStart(root)
        }
    }

    fun play(game: GameEntry): Job? = launches.play(game)

    /** A netplay launch ([NetplayController]'s agreement): [LaunchController.launchNetplay]. */
    fun launchNetplay(entry: Listed, session: NetplaySession) = launches.launchNetplay(entry, session)

    /** Re-reads the highlighted game's resume marker ([LaunchController.refreshResume]). */
    fun refreshResume() = launches.refreshResume()

    fun openSwitcher() = switcher.openSwitcher()

    /** A tap on the switcher List's row [index]. */
    fun selectSwitcherRow(index: Int) = switcher.selectSwitcherRow(index)

    /** The Tools tab, on the tool last opened there. */
    fun openTools() {
        if (!kidsUi.kidsToolsAllowed) return   // Kids Mode: Tools only for a parent
        rememberMenuSelection()
        deps.cheatDb?.refresh()
        deps.achievements?.refresh()
        showSystemsNow(MenuCategory.TOOLS)
    }

    /**
     * Home pressed while this app is the launcher: the main menu on its first tab (Home). First setup and a rescan stay
     * put. The switcher closes as B closes it (a Y Remove applies); a page under Library leaves as B would, reloading a
     * stale library ([libraryStale], which also covers Default emulators' changed default) before landing.
     */
    fun goHome() {
        // Onboarding (and its embedded Device Sync) stays put, as first setup does.
        if (_screen.value is Screen.Onboarding || (_screen.value == Screen.DeviceSync && onboardingUi.onboardingSync)) return
        when (_screen.value) { Screen.Loading, is Screen.PermissionsStep, Screen.PickFolder, Screen.AddFirstExtra, is Screen.AndroidGamesStep, is Screen.FilePicker, is Screen.StorageUpgrade -> return; else -> Unit }
        switcher.cancelLoad()
        loader.cancel()
        if (loader.active) return   // a non-cancellable load (the library rescan) runs on and lands by itself
        val first = menuTabs.firstOrNull()?.category ?: return
        switcher.dropSwitcherReturn()   // Home over a game resumed from the switcher: Home, not the switcher, when it ends
        val s = _screen.value
        if (s is Screen.Switcher) switcher.closeSwitcher()
        pushedTab = null   // Home lands on the menu's own first tab (shown just below: the screen follows)
        if (underLibrary(s) && libraryStale()) { load { showSystemsNow(first) }; return }
        rememberMenuSelection()
        showSystemsNow(first)
    }

    /** Library, Emulators, their sub-pages and Emulator Settings (not a game's Game Settings modal): leaving them may reload. */
    private fun underLibrary(s: Screen): Boolean = when (s) {
        is Screen.Settings -> s.section.reloads
        is Screen.EmulatorList, is Screen.EmulatorConsoles -> true
        is Screen.EmulatorOptions -> !gameSettingsModal(s)
        else -> false
    }

    /**
     * Where B from a tool lands: Home when Home opened it (a pinned tool), else the Tools tab; each on the row it was
     * left on.
     */
    private fun showTools() {
        showSystemsNow(if (menuCategory == MenuCategory.HOME) MenuCategory.HOME else MenuCategory.TOOLS)
    }

    /** Signed in to RetroAchievements, for Home's stats strip: a token and a user name. */
    private fun raSignedIn(): Boolean = deps.settings.raToken != null && deps.settings.raUsername.isNotBlank()

    // --- RetroAchievements: the panel and the Tools pages live in RaScreenController ---

    /** The platform has RetroAchievements and the user is signed in: a game list offers the panel (the trophy icon, X, the game's menu). */
    val hasAchievements: Boolean get() = ra.hasAchievements
    /** The trophy icon: opens the panel on the highlighted game, or closes it. */
    fun toggleRaPanel() = ra.toggleRaPanel()
    /** X (or the trophy icon) on a game list: the panel on its highlighted game. */
    fun openRaPanel() = ra.openRaPanel()
    fun closeRaPanel() = ra.closeRaPanel()
    /** A tap on the panel's achievement [index] (of the shown rows): its details. */
    fun openRaPanelRow(index: Int) = ra.openRaPanelRow(index)
    /** The RetroAchievements sign-in modal's live username / password (the composable reports every change). */
    fun raDraftUser(text: String) = ra.raDraftUser(text)
    fun raDraftPassword(text: String) = ra.raDraftPassword(text)
    /** The level the left pane shows beside [screen] in the RetroAchievements column browser ([RaScreenController.raParent]). */
    fun raParent(screen: Screen): RaParent? = ra.raParent(screen)
    /** A tap on row [index] of the dimmed RetroAchievements left pane showing [parent] ([RaScreenController.raParentTap]). */
    fun raParentTap(parent: Screen, index: Int) = ra.raParentTap(parent, index)

    /** The wall clock for Home's stats: [AppDeps.now], else the system's. */
    private fun clockNow(): Long = deps.now?.invoke() ?: nowMs()

    /** [category]'s tab when it shows, else pushed over the menu ([openHiddenTab]). */
    private fun showTab(category: MenuCategory) {
        val s = _screen.value as? Screen.Systems ?: return
        val at = s.tabs.indexOfFirst { it.category == category }
        if (at >= 0) selectTab(at) else openHiddenTab(category)
    }

    /** The tools this platform has and the user's Android apps, all by name; Settings last. */
    private fun toolRows(): List<ToolRow> = (listOfNotNull(
        deps.achievements?.let { ToolRow(ToolKind.RETRO_ACHIEVEMENTS, "RetroAchievements", null) },
        deps.gameTime?.let { ToolRow(ToolKind.GAME_TIME, "Game Tracker", null) },
        artworkScreens?.let { ToolRow(ToolKind.ARTWORK, ToolKind.ARTWORK.toolName!!, null) },
        ToolRow(ToolKind.CHEAT_DB, "Cheats & Patches", null),
        deps.deviceSync?.let { ToolRow(ToolKind.DEVICE_SYNC, "Device Sync", null) },
        deps.launcher?.let { ToolRow(ToolKind.APPS, ToolKind.APPS.toolName!!, null) },
    ) + appToolRows()).sortedBy { it.label.lowercase() } + ToolRow(ToolKind.SETTINGS, ToolKind.SETTINGS.toolName!!, null)

    /**
     * The Android apps the user added; one not installed is skipped (and kept, for a reinstall), and so is
     * one in the Android games console (kept too: it comes back when it leaves the console).
     */
    private fun appToolRows(): List<ToolRow> {
        val l = deps.launcher ?: return emptyList()
        return shownToolApps().mapNotNull { id -> l.label(id)?.let { ToolRow(ToolKind.APP, it, null, appId = id) } }
    }

    /**
     * The apps pinned on Home from the drawer without being in Tools ([tools] has no row for them), as rows for
     * [pinnedToolRows]; one not installed, or in the Android games console, is skipped (its pin is kept).
     */
    private fun homeOnlyAppRows(tools: List<ToolRow>): List<ToolRow> {
        val l = deps.launcher ?: return emptyList()
        val inTools = tools.mapNotNullTo(HashSet()) { it.appId }
        val inConsole = deps.settings.androidConsoleApps.toSet()
        return pinnedAppIds().filter { it !in inTools && it !in inConsole }.mapNotNull { id -> l.label(id)?.let { ToolRow(ToolKind.APP, it, null, appId = id) } }
    }

    /** The app ids among the tool pins ("APP:" and the id, [pinKey]). */
    private fun pinnedAppIds(): List<String> = toolPins.filter { it.startsWith("APP:") }.map { it.removePrefix("APP:") }

    /** The Android apps the user added that Tools can show: those not in the Android games console. */
    private fun shownToolApps(): List<String> {
        val inConsole = deps.settings.androidConsoleApps.toSet()
        return deps.settings.toolApps.filter { it !in inConsole }
    }

    /** [row]'s tool opened (an app, [ToolKind.APP], launches). */
    private fun openTool(row: ToolRow) {
        // Kids Mode: no tool (Settings, Apps, an app, Device Sync …) but for a parent; the Apps tab's allowed apps open
        // for the kid, and the lock is not a tool page.
        val kidsApp = row.kind == ToolKind.APP && row.appId in deps.settings.kidsAllowedApps
        if (row.kind != ToolKind.KIDS_LOCK && !kidsApp && !kidsUi.kidsToolsAllowed) return
        when (row.kind) {
            ToolKind.RETRO_ACHIEVEMENTS -> if (deps.achievements != null) ra.showRetroAchievements()
            ToolKind.GAME_TIME -> gameTimeScreen?.open(onCancel = ::showTools)
            ToolKind.ARTWORK -> { artworkScreens?.open(); checkArt(reuse = false) }
            ToolKind.CHEAT_DB -> {
                val c = deps.cheatDb ?: return   // no controller on this platform: nothing to open
                _screen.value = Screen.CheatDb()
                _selection.value = ListSelection(0, cheatDbRows(c.state.value).size)
            }
            ToolKind.DEVICE_SYNC -> {
                showDeviceSync(deps.deviceSync ?: return)
            }
            ToolKind.SETTINGS -> openSettingsMenu()
            ToolKind.APPS -> androidApps.openToolApps(AppPick.TOOLS)
            ToolKind.APP -> androidApps.openApp(row)
            ToolKind.KIDS_LOCK -> kidsUi.kidsLockNow()   // the tiles screen (or the menu, in a window)
        }
    }

    /** Device Sync's buttons go to its controller; B on Ready (it returns false) leaves the tool (by a rescan when ROMs arrived). */
    private fun onDeviceSyncButton(button: NxButton) {
        val sync = deps.deviceSync
        val page = sync?.page?.value
        if (onboardingUi.onboardingSync) onboardingUi.noteOnboardingSync(page)
        // Ready (the tool and setup's sync step alike): the d-pad goes Sync ROMs (where available) → cable → Wi-Fi (→ Skip
        // in setup) and round; A, LEFT or RIGHT on Sync ROMs flips it; A on Skip goes on.
        val focus = _syncSetup.value?.focus ?: SyncFocus.LINK
        if (page is DeviceSyncPage.Ready && sync != null) {
            val rows = syncSetupRows(page, skip = onboardingUi.onboardingSync)
            if (button == NxButton.UP || button == NxButton.DOWN) {
                val at = syncSetupIndex(page, focus, skip = onboardingUi.onboardingSync)
                syncSetupTap((at + (if (button == NxButton.UP) -1 else 1) + rows.size) % rows.size, open = false)
                return
            }
            // Sync ROMs is a switch: A flips it, LEFT / RIGHT leave it alone (as on every settings page).
            if (focus == SyncFocus.ROMS && (button == NxButton.A || button == NxButton.LEFT || button == NxButton.RIGHT)) { if (button == NxButton.A) sync.flipRoms(); return }
            if (onboardingUi.onboardingSync && focus == SyncFocus.SKIP && button == NxButton.A) return onboardingUi.leaveOnboardingSync(forward = true)
        }
        // Setup: A on Done (Continue) goes on; leaving Ready (B) goes back a step.
        if (onboardingUi.onboardingSync && button == NxButton.A && page is DeviceSyncPage.Done) return onboardingUi.leaveOnboardingSync(forward = true)
        if (sync?.onButton(button) == true) { publishSyncSetup(); return }
        if (onboardingUi.onboardingSync) return onboardingUi.leaveOnboardingSync(forward = false)
        _syncSetup.value = null
        if (romsPending) { romsPending = false; rescan() } else showTools()
    }

    /** UP/DOWN move over the action rows; A runs the highlighted one (Remove asks first); B cancels a running job, closes the confirm, or goes back. */
    private fun onCheatDbButton(s: Screen.CheatDb, button: NxButton) {
        val c = deps.cheatDb ?: run { showTools(); return }
        if (s.confirmRemove) {
            when (button) {
                NxButton.A -> { _screen.value = Screen.CheatDb(); c.remove() }
                NxButton.B -> _screen.value = Screen.CheatDb()
                else -> Unit
            }
            return
        }
        when (button) {
            NxButton.UP -> _selection.value = _selection.value.up()
            NxButton.DOWN -> _selection.value = _selection.value.down()
            NxButton.A -> when (cheatDbRows(c.state.value).getOrNull(_selection.value.index)) {
                CheatDbAction.DOWNLOAD -> c.download()
                CheatDbAction.CHECK -> c.checkForUpdates()
                CheatDbAction.REMOVE -> _screen.value = s.copy(confirmRemove = true)
                null -> Unit
            }
            NxButton.B -> if (c.busy) c.cancel() else showTools()
            else -> Unit
        }
    }

    /**
     * Opens main menu row [index] of [s]: a console or collection's list, a game (resumed, or played), a tool, a hidden tab.
     * [forward]: how a list comes in. A game's start job, which ends once the runner was called or refused it; else null.
     */
    private fun openMainRow(s: Screen.Systems, index: Int, forward: NavMotion = NavMotion.FORWARD): Job? {
        when (val row = s.rows.getOrNull(index)) {
            is MainRow.System -> { intendNav(forward); openSystem(row.system) }
            // Home's pinned game, as Continue: its save read now resumes, none plays. An Android game opens its app.
            is MainRow.Pin -> return launchPinned(row.entry)
            // A shelf game, as a pin (never an Android game).
            is MainRow.Shelf -> { rememberMenuSelection(); return launches.resumeFromSave(row.entry, orPlay = true) }
            is MainRow.Collection -> { intendNav(forward); openList(ListSource.Collection(row.name)) }
            is MainRow.Tool -> { rememberMenuSelection(); openTool(row.row) }
            // Its save is read again now: one made since the row was built resumes, one deleted since plays.
            is MainRow.Continue -> { rememberMenuSelection(); return launches.resumeFromSave(row.entry, orPlay = true) }
            is MainRow.PinnedTool -> { rememberMenuSelection(); openTool(row.row) }
            // Home's "+N" and Pick a game: the Tools / Consoles tab, pushed over Home while Layouts hides it.
            is MainRow.MoreTools -> if (kidsUi.kidsToolsAllowed) showTab(MenuCategory.TOOLS)
            MainRow.PickAGame -> showTab(MenuCategory.CONSOLES)
            null -> Unit
        }
        return null
    }

    /** [openMainRow]'s pinned game: remembers the row so a quit lands back on it. */
    private fun launchPinned(entry: Listed): Job? {
        rememberMenuSelection()
        if (!entry.system.android) return launches.resumeFromSave(entry, orPlay = true)
        androidApps.launchApp(entry)
        return null
    }

    /**
     * A tap on a game of the top screen's stats shelf: played as Home's pinned game. Refused (null) while anything else
     * has the bottom screen, or once the bottom's highlight left the console or collection whose shelf was tapped.
     */
    fun launchFromTop(entry: Listed): Job? {
        if (loader.active || launches.minimalPending || kidsUi.kidsLocked || textEntryOpen) return null
        if (settingsUi.chooser.value != null || settingsUi.settingInfo.value != null || controlBindings.capturingShortcut.value != null) return null
        if (_androidGamesAsk.value != null || _firstTip.value != null || _updateConfirm.value != null || updatePopupShows() || _error.value != null) return null
        if (netplay?.page?.value != null) return null
        val s = _screen.value as? Screen.Systems ?: return null
        if (s.menu != null || s.search != null) return null
        val stats = topScreen.topStats.value ?: return null
        if (stats.key != statsKey(DualMenu.top(s, _selection.value.index))) return null
        if (stats.shelf.none { it.entry.game.path == entry.game.path }) return null
        return launchPinned(entry)
    }

    fun select(index: Int) {
        if (launches.minimalPending) return   // a Minimal open is under way: the selection stays on the row opening
        if (index != _selection.value.index) controlBindings.cancelCapture()   // a tap on another Shortcuts row ends a wait for a press
        _selection.value = _selection.value.copy(index = index).clampedTo(_selection.value.count)
        (_screen.value as? Screen.Systems)?.takeIf { it.category == MenuCategory.HOME }?.let { homeTab.noteHomeFocus(_selection.value.index, it.rows) }
        refreshResume()
    }

    fun openSelected() {
        if (loader.active) return   // a screen is loading: only B (cancel) acts
        if (launches.minimalPending) return   // a Minimal open is under way: a tap waits too
        when (val s = _screen.value) {
            is Screen.Onboarding -> onboardingUi.onboardingRow(_selection.value.index)
            is Screen.Systems -> if (styleOf(s) == MenuStyle.MINIMAL) launches.openMinimal(s) else openMainRow(s, _selection.value.index)
            is Screen.SettingsMenu -> settingsUi.openHighlightedSection()
            is Screen.ToolApps -> if (s.pick == AppPick.GAMES) androidApps.toggleAndroidGame(s) else androidApps.launchDrawerApp(s)
            is Screen.CheatDb -> onCheatDbButton(s, NxButton.A)
            Screen.DeviceSync -> onDeviceSyncButton(NxButton.A)
            Screen.RetroAchievements -> ra.onRaButton(NxButton.A)
            is Screen.RaDownload -> Unit   // its rows only read (A, Done, comes through onButton)
            is Screen.RaConsoles, is Screen.RaGames, is Screen.RaGame -> ra.onRaBrowserButton(s, NxButton.A)
            is Screen.RaAchievement, Screen.RaProfile -> Unit   // a detail page: nothing to open
            Screen.KidsHome, Screen.KidsUnlock, is Screen.KidsPin -> kidsUi.kidsActivate(s)   // as A on the focused tile, key or row
            is Screen.KidsSetup -> kidsUi.kidsSetupRow(_selection.value.index)
            is Screen.KidsDayEdit -> kidsUi.kidsDayRow(_selection.value.index)
            is Screen.RaSettings -> ra.onRaSettingsButton(s, NxButton.A)
            // Hidden unhides at once, Minimal or not: nothing opens.
            is Screen.Games -> if (s.source == ListSource.Hidden) unhideSelected()
                else if (styleOf(s) == MenuStyle.MINIMAL) launches.openMinimal(s) else launches.openGameRow(s, _selection.value.index)
            is Screen.Switcher -> switcher.onSwitcherButton(s, NxButton.A)
            is Screen.Settings -> s.rows.getOrNull(_selection.value.index)?.let { row ->
                val value = settingsContent.settingValue(row)
                if (value != null) {
                    // A value row: a picker row (many or long values) opens its picker; the others step forward, as RIGHT.
                    val touch = settingsTouchRow(row, s.choices.getOrNull(_selection.value.index), s.section)
                    if (row.enabled && touch.control == NxSettingControl.PICKER) openSettingPicker(_selection.value.index)
                    else if (row.enabled) value.change(1)
                } else when (row.kind) {
                    SettingsKind.HOME -> pickHome()
                    SettingsKind.ADD_EXTRA -> pickExtra()
                    SettingsKind.EXTRA -> settingsUi.refreshSettings(confirm = row.extra)
                    SettingsKind.RESCAN -> rescanFromSettings()
                    SettingsKind.LAYOUT_HOME, SettingsKind.LAYOUT_GAMES_TAB, SettingsKind.LAYOUT_CONSOLES, SettingsKind.LAYOUT_COLLECTIONS, SettingsKind.LAYOUT_TOOLS,
                    SettingsKind.LAYOUT_GAME_LISTS, SettingsKind.LAYOUT_TITLE_HINTS, SettingsKind.LAYOUT_SWITCHER ->
                        SettingsSection.entries.firstOrNull { it.opener == row.kind }?.let { settingsUi.openSubPage(s, it) }
                    SettingsKind.DEFAULTS -> settingsUi.openSubPage(s, SettingsSection.DEFAULT_EMULATORS)
                    SettingsKind.SAVES -> settingsUi.openSubPage(s, SettingsSection.SAVES)
                    SettingsKind.EXPORT_SAVES -> deps.saveTransfer?.exportSaves(::showTransferNotice)
                    SettingsKind.LICENSES_LINK -> deps.openUrl(LICENSES_URL)
                    SettingsKind.IMPORT_SAVES -> settingsUi.openSubPage(s, SettingsSection.IMPORT_SAVES)
                    SettingsKind.IMPORT_FOLDER -> importSaves(ImportSource.FOLDER)
                    SettingsKind.IMPORT_ZIP -> importSaves(ImportSource.ZIP)
                    SettingsKind.IMPORT_RETROARCH -> importSaves(ImportSource.RETROARCH)
                    SettingsKind.IMPORT_DONE -> leaveSubPage(SettingsSection.IMPORT_RESULT)
                    SettingsKind.IMPORT_BIOS -> settingsUi.openSubPage(s, SettingsSection.IMPORT_BIOS)
                    SettingsKind.IMPORT_BIOS_FOLDER -> importBios(com.nxredux.mobile.bios.BiosSource.FOLDER)
                    SettingsKind.IMPORT_BIOS_FILES -> importBios(com.nxredux.mobile.bios.BiosSource.FILES)
                    SettingsKind.IMPORT_BIOS_ZIP -> importBios(com.nxredux.mobile.bios.BiosSource.ZIP)
                    SettingsKind.IMPORT_BIOS_RETROARCH -> importBios(com.nxredux.mobile.bios.BiosSource.RETROARCH)
                    SettingsKind.BUTTON_LAYOUT -> settingsUi.openSubPage(s, SettingsSection.BUTTON_LAYOUT)
                    SettingsKind.BUTTON_LAYOUT_ORIENTATION -> row.landscape?.let(settingsContent::openSkinEditor)
                    SettingsKind.BUTTON_LAYOUT_RESET -> _screen.value = s.copy(confirmReset = true)   // asks first
                    SettingsKind.SHORTCUTS -> settingsUi.openSubPage(s, SettingsSection.SHORTCUTS)
                    SettingsKind.SYSTEM_BUTTONS -> settingsUi.openSubPage(s, SettingsSection.SYSTEM_BUTTONS)
                    SettingsKind.GPU_DRIVER -> if (row.enabled) settingsUi.openSubPage(s, SettingsSection.GPU_DRIVER)   // dimmed: the reason is all it says
                    SettingsKind.GPU_DRIVER_ITEM -> row.gpuDriver?.let(settingsContent::gpuDriverRowA)
                    SettingsKind.GPU_DRIVER_ADD -> settingsContent.addGpuDriver()
                    // Without a controller too: one plugged in next answers it (the row says to connect one).
                    SettingsKind.SHORTCUT_BIND -> row.shortcut?.let(controlBindings::startCapture)
                    SettingsKind.SYSTEM_BUTTON_BIND -> row.systemRole?.let { controlBindings.systemButtonWaiting = it; settingsUi.refreshSettings() }
                    SettingsKind.HIDDEN -> openList(ListSource.Hidden)
                    SettingsKind.UNASSIGNED -> unassignedSystem()?.let { settingsUi.rememberSection(s.section, _selection.value); openSystem(it) }
                    SettingsKind.EMULATORS -> { settingsUi.rememberSection(s.section, _selection.value); emulatorSettings.openList(systemsCache) }
                    SettingsKind.INFO -> Unit   // About: read-only
                    SettingsKind.COPY_INFO -> settingsContent.copyDeviceInfo()
                    SettingsKind.PLAYER_NAME -> _screen.value = s.copy(nameDraft = PlayerName.resolve(deps.settings.playerName, deps.deviceName()))
                    SettingsKind.APP_UPDATE -> deps.updater?.let(::appUpdateRowA)
                    SettingsKind.HOME_SCREEN -> deps.launcher?.let { it.setHome(!it.isHome()); settingsUi.refreshSettings() }
                    SettingsKind.ANDROID_GAMES -> androidApps.openToolApps(AppPick.GAMES)
                    SettingsKind.RESET_DEFAULTS -> _screen.value = s.copy(confirmReset = true)   // asks first
                    SettingsKind.ALL_FILES -> deps.allFilesAccess?.openSettings()
                    SettingsKind.MIC_ACCESS -> deps.micAccess?.request()
                    SettingsKind.KIDS_PIN -> kidsUi.kidsChangePin()
                    SettingsKind.KIDS_DAY -> row.day?.let { kidsUi.kidsOpenDay(it, setup = false) }
                    SettingsKind.KIDS_APPS -> kidsUi.kidsOpenApps(setup = false)
                    SettingsKind.KIDS_GUARD -> kids?.openUsageAccess()   // the row goes on resume once granted
                    SettingsKind.KIDS_ALARMS -> kids?.openExactAlarms()
                    SettingsKind.KIDS_HOME -> deps.launcher?.setHome(true)   // the row goes once home ([onLauncherChanged])
                    SettingsKind.KIDS_FORGOT -> showSettingInfo(_selection.value.index)   // where the flag is, and what deleting it does
                    else -> Unit   // a value row with nothing to step ([settingValue] null)
                }
            }
            Screen.PickFolder, is Screen.PermissionsStep -> setupStepRow(_selection.value.index)
            is Screen.StorageUpgrade -> upgrade.storageUpgradeRow(_selection.value.index)
            is Screen.CopySaves -> upgrade.copySavesRow(_selection.value.index)
            is Screen.BiosImportResult -> biosResultDone()
            is Screen.FilePicker -> filePicker?.onButton(s, NxButton.A)
            Screen.AddFirstExtra, is Screen.AndroidGamesStep, is Screen.FolderSummary -> setupStepRow(_selection.value.index)
            Screen.Loading -> Unit
            is Screen.EmulatorList, is Screen.EmulatorConsoles, is Screen.EmulatorOptions -> {
                // A picker row opens its picker ([onEmulatorTouchButton]); every other row as A.
                if (!(s is Screen.EmulatorOptions && onEmulatorTouchButton(s, NxButton.A))) emulatorSettings.onButton(s, NxButton.A)
            }
            is Screen.GameTime -> gameTimeScreen?.onButton(s, NxButton.A)   // a row tap: its sessions (the options: MENU or a long press)
            is Screen.GameTimeSessions -> Unit   // read-only rows
            is Screen.Artwork, is Screen.ArtworkConsoles, is Screen.ArtworkProgress, is Screen.ArtworkSettings,
            is Screen.ArtworkReset -> artworkScreens?.onButton(s, NxButton.A)
        }
    }

    // --- Tools -> Game Tracker ---

    private val gameTimeScreen = deps.gameTime?.let { data ->
        GameTimeScreenController(scope, _screen, _selection, _error, data, loader) {
            // The index in memory, not the stored one: it has art a failed or pending write has not stored yet. Before the
            // first load there is only the stored one.
            val index = libraryIndex.current()
            val text = if (index == null) deps.indexStore.read() else null
            val art = withContext(deps.compute) {   // every game of the library: off the main thread
                HashMap<String, String?>().also { art ->
                    (index ?: text?.let(LibraryIndexCodec::decode))?.systems?.forEach { s -> s.games.forEach { g -> art[GamePath.of(GameRef(g.source, g.dir, g.file))] = g.boxart ?: g.box2d ?: g.screenshot } }
                }
            }
            return@GameTimeScreenController { path: String -> art[path] }
        }
    }

    /** A tap on a Game Time modal row moves its highlight. */
    fun gameTimeSelectModal(index: Int) { gameTimeScreen?.selectModal(index) }

    /** The Game Tracker list and its lit row beside [screen] (a game's sessions) in the column browser; null otherwise. */
    fun gameTimeParent(screen: Screen): GameTimeParent? = gameTimeScreen?.parentOf(screen)

    /** A tap on row [index] of the dimmed Game Tracker list beside a game's sessions: that game's sessions open in their place. */
    fun gameTimeParentTap(index: Int) { gameTimeScreen?.parentTap(index) }

    // --- Emulator Settings (Settings -> Emulation -> Default settings) and Game Settings ---

    /** Game Settings' Badge row: a saved choice goes into the index, and the lists and Home show it. */
    private val gameBadges = GameBadgeEditor(
        scope, deps.userData, deps.compute,
        saved = { game, choice -> mutateIndex(IndexRefresh(rows = true, openList = true, ui = true)) { current -> current?.setBadgeChoice(game, choice) } },
        failed = { _error.value = "Could not save the badge"; emulatorSettings.refreshOpenPage() },
    )

    /** The game whose Game Settings page is open, for its Badge row. */
    private fun badgeGame(): GameEntry? = (_screen.value as? Screen.EmulatorOptions)?.game?.game

    internal val emulatorSettings: EmulatorSettingsController = EmulatorSettingsController(
        scope, _screen, _selection, _error, deps.coreOptions, deps.emulatorConfigs, deps.coreDefaults,
        host = object : EmulatorSettingsHost {
            override fun showEmulatorsSettings() = settingsUi.showSettingsOn(SettingsSection.EMULATORS, SettingsKind.EMULATORS)
            override fun followRowEdit(key: Any?, from: Int, to: Int?) = settingsUi.followRowEdit(key, from, to)
            override fun appSection(page: EmulatorPage, game: Listed?): EmulatorAppSection? = when {
                page.forGame -> game?.let { gameBadges.section(it.game) }
                EmulatorRows.hostsCableLink(page) ->
                    EmulatorAppSection(EmulatorRows.CABLE_LINK_LABEL, settingsContent.cableLinkRows().map { EmulatorRow.App(it, settingsUi.settingChoices(it)) })
                else -> null
            }
            // Without the value's `after`: it republishes a Settings page, which would leave this one (the controller shows the rows).
            override fun stepAppRow(row: SettingsRow, delta: Int) {
                if (row.kind == SettingsKind.GAME_BADGE) badgeGame()?.let { gameBadges.step(it, delta) } else settingsContent.settingValue(row)?.step(delta)
            }
            override fun chooseAppRow(row: SettingsRow, index: Int) {
                if (row.kind == SettingsKind.GAME_BADGE) badgeGame()?.let { gameBadges.choose(it, index) } else settingsContent.settingValue(row)?.setTo(index)
            }
            override fun runAppRow(row: SettingsRow) { if (row.kind == SettingsKind.CLEAR_LINK_ROMS) deps.linkRomCache?.clear() }
            override fun restore(screen: Screen, selection: ListSelection) {
                _screen.value = screen
                homeTab.publishHomeView()   // a restored main menu: its Home rows' own view
                _selection.value = selection
                refreshResume()
                // The library may have changed while the page was open: rebuild the list from its source, on the same game.
                val on = (screen as? Screen.Games)?.entries?.getOrNull(selection.index)?.game?.path ?: return
                scope.launch { refreshOpenList(select = on) }
            }
        },
        work = deps.compute,
        vulkanFailed = { it in deps.settings.vulkanFailedCores },
        secondScreen = { secondConnected },
        screenClass = deps.screenClass,
        windowPx = deps.windowPx,
        ps2BiosFiles = deps.ps2BiosFiles,
        // Null (no rows) where custom drivers cannot be used ([GpuDriverStore.unavailableReason]).
        gpuDrivers = { deps.gpuDrivers?.takeIf { it.unavailableReason() == null }?.installed() },
        gpuDriverAppWide = { deps.settings.gpuDriver },
        gpuName = { tryOrNull { deps.deviceGpu() } },
        gpuDriverCrashed = { path -> GpuDriverCrash.crashed(deps.settings.gpuDriverCrashes, path) },
        clearGpuDriverCrash = { path -> deps.settings.gpuDriverCrashes = GpuDriverCrash.clearGame(deps.settings.gpuDriverCrashes, path).takeIf { it.isNotEmpty() } },
        alwaysListed = { if (deps.linkRomCache != null) listOf(EmulatorRows.CABLE_LINK_TAG) else emptyList() },
    )

    // --- Tools -> Artwork: AppState hosts the controller (index writes stay here); the pages live in ArtworkScreenController ---

    /** Where the UI state lives; the controller's host calls hop here (Android: the main thread). */
    private val uiContext = scope.coroutineContext[ContinuationInterceptor] ?: Dispatchers.Unconfined

    /**
     * The library as the scraper sees it: playable games (hidden ones left out unless [withHidden]) and Unassigned, then
     * the Android games console's games ([androidArtworkGames]) unless [withHidden] (Reset: their art is not in the home
     * tree's log). From memory.
     */
    private fun artworkGames(
        ix: LibraryIndex?, withHidden: Boolean = false, datTitle: (tag: String, fileName: String) -> String? = { _, _ -> null },
    ): List<LibraryGameRef> =
        ix?.playableSystems(if (withHidden) emptySet() else hiddenPaths.toSet()).orEmpty().flatMap { s ->
            s.games.map { g -> artworkRef(s.tag, g, datTitle(s.tag, g.fileName)) }
        } + if (withHidden) emptyList() else androidArtworkGames()

    /** A console game [g] (tagged [tag]) as the scraper sees it. */
    private fun artworkRef(tag: String, g: GameEntry, datTitle: String? = null): LibraryGameRef {
        val art = ArtRefs(
            g.boxartRef?.value, g.box2dRef?.value, g.screenshotRef?.value,
            wheel = g.wheelRef?.value, mix = g.mixRef?.value, heroShot = g.heroShot,
        )
        return LibraryGameRef(
            gamePath = g.path, tag = tag, name = g.shownName, fileName = g.fileName, rom = g.ref, sourceId = g.sourceId,
            relativeDir = g.relativeDir, folderGame = g.lookupDir != g.relativeDir,
            have = ArtHave.of(art, needMix = settings.artMix), datTitle = datTitle, art = art,
        )
    }

    /**
     * The scraper's games with each arcade set's raw DAT title (fbneo's table; flycast's for a DC zip/7z): libretro names
     * arcade art by it. Each table is read at most once per call, off the calling thread, and only when a system uses it.
     */
    private suspend fun artworkGamesWithTitles(withHidden: Boolean): List<LibraryGameRef> {
        val ix = libraryIndex.current()
        val cores = ix?.systems.orEmpty().filter { it.games.isNotEmpty() }.associate { it.tag to CoreRegistry.forTag(it.tag)?.id }
        val arcade = if ("fbneo" in cores.values) withContext(deps.compute) { deps.arcadeSets() } else null
        val naomi = if ("flycast" in cores.values) withContext(deps.compute) { deps.naomiSets() } else null
        return artworkGames(ix, withHidden) { tag, fileName ->
            when (cores[tag]) {
                "fbneo" -> arcade?.title(Names.stripExtension(fileName))
                "flycast" -> if (Names.isArchive(fileName)) naomi?.title(Names.stripExtension(fileName)) else null
                else -> null
            }
        }
    }

    /** The Android games console shown now (none without a game). */
    private fun shownAndroidConsole(): SystemFolder? = androidConsole?.takeIf { it.games.isNotEmpty() }

    /**
     * The Android games console's games for the scraper: by app id and label, "has art" when a screenshot is stored (a
     * fetch never wants a box) and, while Mix is on, its mix when it has an input, with the store's pictures as their
     * art. The game path is the entry's own ([path]), as the game menu's art panel names the game by it.
     */
    private fun androidArtworkGames(): List<LibraryGameRef> = shownAndroidConsole()?.games.orEmpty().map { g ->
        val art = ArtRefs(box2d = g.box2dRef?.value, screenshot = g.screenshotRef?.value, wheel = g.wheelRef?.value, mix = g.mixRef?.value, heroShot = g.heroShot)
        LibraryGameRef(
            gamePath = g.path, tag = ANDROID_TAG, name = g.shownName, fileName = g.fileName, rom = g.ref, sourceId = g.sourceId,
            relativeDir = g.relativeDir, folderGame = false,
            // No 3D box: its mix input is a screenshot that is not a hero, or a 2D box.
            have = ArtHave(
                box = true, boxart = false, screenshot = g.screenshotRef != null, box2d = g.box2dRef != null, wheel = g.wheelRef != null,
                mix = g.mixRef != null, needMix = settings.artMix, heroShot = g.heroShot,
            ),
            android = true, art = art,
        )
    }

    private val artworkHost = object : ArtworkHost {
        override suspend fun libraryGames(): List<LibraryGameRef> = artworkGamesWithTitles(withHidden = false)
        override suspend fun allLibraryGames(): List<LibraryGameRef> = artworkGamesWithTitles(withHidden = true)

        /** In memory only; the write and the refresh are batched ([LibraryIndexHolder.applyArt]). */
        override suspend fun applyArt(gamePath: String, art: ArtRefs) {
            val ref = GamePath.split(gamePath) ?: return
            libraryIndex.applyArt(ref, art)
        }

        override suspend fun flushArt() = libraryIndex.flushArt()

        /** The art store has the new screenshot: the console is rebuilt from it, on the UI's thread. */
        override suspend fun refreshAndroidConsole() = withContext(uiContext) { applyAndroidConsole() }

        override suspend fun readPrivate(name: String): String? = deps.privateFiles.read(name)
        override suspend fun writePrivate(name: String, text: String) = deps.privateFiles.write(name, text)
        override suspend fun appendPrivate(name: String, text: String): Boolean { deps.privateFiles.append(name, text); return true }
    }

    private var artCheck: Job? = null
    private var artCheckReused = false   // [artCheck] skips the art folders whose modified time is unchanged

    /**
     * Each game's art read again from its folder in the background ([LibraryScanner.readArt]): art deleted or added
     * outside the app (a file manager, a computer) since the scan shows, counts in Artwork and offers Fetch art without
     * a rescan. On coming back to the app and on opening Artwork; one at a time, never during a fetch, and only onto the
     * index it was read from (a scan or a fetch's art meanwhile wins). Coming back ([reuse]) lists only the typed art
     * folders whose modified time changed since they were last read, as it happens on every return; opening Artwork
     * lists them all, so its counts never rest on a provider's folder times (it follows a coming-back check in flight, which
     * is let finish: cancelled, it could stop between its index write and the refresh after it).
     */
    private fun checkArt(reuse: Boolean) {
        val running = artCheck?.takeIf { it.isActive }
        if (running != null && (reuse || !artCheckReused)) return
        val phase = artworkScreens?.job?.value?.phase
        if (phase == com.nxredux.mobile.scraper.JobPhase.RUNNING) return
        if (libraryIndex.snapshot == null) return
        artCheckReused = reuse
        artCheck = scope.launch {
            running?.join()
            val from = libraryIndex.snapshot ?: return@launch
            val art = tryOrAny({ return@launch }) { scanner.readArt(from, reuse) }
            mutateIndex(IndexRefresh(rows = true, openList = true, ui = true)) { current -> current?.takeIf { it === from }?.withArt(art) } ?: return@launch
            withContext(uiContext) { artworkScreens?.libraryChanged() }
        }
    }

    /** The artwork controller; null in a build without one (no Tools row, no Fetch art or Change art). */
    private val artworkController: com.nxredux.mobile.scraper.ArtworkController? = deps.artwork?.invoke(artworkHost)
    /** Game info beside the art (the top screen's details read it); null without a controller or an engine keeping it. */
    private val gameInfoStore: com.nxredux.mobile.scraper.GameInfoStore? = artworkController?.info

    /** The top screen's background art and info fetch for the game its details show; null without [gameInfoStore]. */
    private val infoFetcher: InfoFetcher? = artworkController?.let { c ->
        val store = gameInfoStore ?: return@let null
        InfoFetcher(
            scope, store, lookup = c::lookupInfo, wantsArt = c::wantsArt, fetchArt = c::fetchArtQuietly,
            enabled = { deps.settings.fetchGameInfo && c.ssAvailable }, runActive = { c.busy },
            quotaRead = { c.ssQuotaRead }, now = ::clockNow, calendar = deps.calendar,
        ).also { f -> scope.launch { f.fetched.collect { topScreen.showFetchedInfo(it) } } }
    }

    /** The scanner's one-time `grid/` and `hero/` move, sharing the artwork controller's written-art log; none without it. */
    private val legacyArtMove: LegacyArtMove? = artworkController?.legacyArtMove(deps.source)

    /** Null without a controller (no Tools row, no Fetch art). */
    private val artworkScreens: ArtworkScreenController? = artworkController?.let { c ->
        ArtworkScreenController(
            scope, _screen, _selection, _error, deps.settings, loader, c,
            host = object : ArtworkScreenHost {
                override fun showNotice(text: String) = this@AppState.showNotice(text)
                override fun showTools() = this@AppState.showTools()
                override fun games(): List<LibraryGameRef> = artworkGames(libraryIndex.snapshot)
                override fun gamesInputs(): List<Any?> = listOf(libraryIndex.snapshot, hiddenPaths, androidConsole)
                /** The library's console groups, with the Android games console among them by name (as in Consoles) when shown. */
                override fun consoles(): List<Pair<String, Set<String>>> {
                    val groups: List<Pair<String, Set<String>>> = systemsCache.filter { !it.unassigned }.groupBy { it.displayName }.map { (name, group) -> name to group.mapTo(LinkedHashSet()) { it.tag } }
                    val android = shownAndroidConsole() ?: return groups
                    val at = groups.indexOfFirst { it.first.lowercase() > android.displayName.lowercase() }.takeIf { it >= 0 } ?: groups.size
                    return groups.toMutableList().apply { add(at, android.displayName to setOf(ANDROID_TAG)) }
                }
            },
        ).also { screens -> scope.launch { c.state.collect { screens.onState(it) } } }
    }

    /**
     * The game menu's Fetch art / Change art panel over a game list; null without a controller. A pick's art is already
     * in the index and shown ([com.nxredux.mobile.scraper.ArtworkHost.applyArt], then [LibraryIndexHolder.flushArt]);
     * the flush after it writes anything still batched.
     */
    private val artPanels: ArtPanelController? = artworkController?.let { c ->
        ArtPanelController(scope, c, deps.settings, onPicked = { scope.launch { libraryIndex.flushArt() } }, onLayoutSwitched = ::syncArtLayout)
    }
    private val noArtPanel = MutableStateFlow<ArtPanel?>(null)
    private val _artLayout = MutableStateFlow(ArtLayout(deps.settings.backdropArt, deps.settings.gameListArt))
    /**
     * Layouts → Backdrop art and List art as the screens draw them: the open list, its background, the top screen and
     * the Game Switcher read this, so a change made over a list (the art panel's Switch) shows at once.
     */
    val artLayout: StateFlow<ArtLayout> = _artLayout.asStateFlow()
    /** [artLayout] follows the settings (after Settings' rows, its reset, and the art panel's Switch). */
    private fun syncArtLayout() { _artLayout.value = ArtLayout(deps.settings.backdropArt, deps.settings.gameListArt) }
    /** The art panel; null while closed (always, without a controller). */
    val artPanel: StateFlow<ArtPanel?> = artPanels?.panel ?: noArtPanel

    init {
        // [_panelOpen] follows the two panels; one closing over the Games tab lets the Android games question ask.
        scope.launch {
            combine(raPanel, artPanel) { r, a -> r != null || a != null }.collect { open ->
                val was = _panelOpen.value
                _panelOpen.value = open
                if (was && !open) maybeAskAndroidGames()
            }
        }
    }

    /** Opens the art panel on [entry] over the game list; [fetchFirst]: Fetch art, else Change art. */
    private fun openArtPanel(entry: Listed, fetchFirst: Boolean) {
        val panels = artPanels ?: return
        ra.closeRaPanel()
        panels.open(entry, fetchFirst)
    }
    /** A tap on the art panel's tile [index] (Search again at the tiles' count): picks it. */
    fun artPanelTap(index: Int) { artPanels?.tap(index) }
    /** A tap on the art panel question's Switch ([switch]) or Keep: Switch writes Layouts → Backdrop art (and List art). */
    fun artPanelAnswer(switch: Boolean) { artPanels?.answer(switch) }
    /** Closes the art panel (a tap beside it). */
    fun closeArtPanel() { artPanels?.close() }

    /** The Artwork sign-in modal's live username / password (the composable reports every change). */
    fun artworkDraftUser(text: String) { artworkScreens?.draftUser(text) }
    fun artworkDraftPassword(text: String) { artworkScreens?.draftPassword(text) }
    /** The key prompt's live text (SteamGridDB or TheGamesDB). */
    fun artworkDraftKey(text: String) { artworkScreens?.draftKey(text) }

    /**
     * The art fetch job as it moves (null without a controller): the Artwork stage is drawn again on each emission, so
     * its words, status lines and previews follow a running fetch while the open page stays the same screen.
     */
    val artworkJob: StateFlow<com.nxredux.mobile.scraper.ScrapeState>? get() = artworkScreens?.job

    /** The Artwork page whose words and status lines the wide stage shows beside [screen] ([ArtworkScreenController.stagePage]). */
    fun artworkStagePage(screen: Screen): Screen.Artwork = artworkScreens?.stagePage(screen) ?: Screen.Artwork(emptyList(), emptyList())

    /** The stage's word lit for the open Artwork page [screen]; null on the Artwork page itself ([ArtworkScreenController.wordOf]). */
    fun artworkWord(screen: Screen): ArtworkAction? = artworkScreens?.wordOf(screen)

    /** A tap on the stage's word [index]: on the page, select and open it; on a page, that word's page in place ([ArtworkScreenController.wordTap]). */
    fun artworkWordTap(index: Int) { artworkScreens?.wordTap(index) }

    /** The page the root previews while [action] is highlighted; null for none ([ArtworkScreenController.preview]). */
    fun artworkPreview(action: ArtworkAction): Screen? = artworkScreens?.preview(action)

    /** A tap on [row] of the root's preview: opens that page on the row, a second tap acts ([ArtworkScreenController.previewTap]). */
    fun artworkPreviewTap(row: Int) { artworkScreens?.previewTap(row) }

    // --- Context menu: hosted by a game list or the main menu (a pinned or recent row) ---

    /** The context menu's navigation, prompts and actions; it reaches back into this state only through [ContextMenuHost]. */
    private val contextMenus = ContextMenuController(
        scope, _screen, _selection, _error, deps.userData, deps.settings, sidecars, collections, recentsLock,
        host = object : ContextMenuHost {
            override val pins: List<Shortcut> get() = this@AppState.pins
            override val toolPins: List<String> get() = this@AppState.toolPins
            override val kidsOn: Boolean get() = kidsUi.kidsMode != KidsMode.Off
            override val kidsRestricted: Boolean get() = !kidsUi.kidsToolsAllowed
            override suspend fun writeToolPins(keys: List<String>): Boolean = this@AppState.writeToolPins(keys)
            override fun select(index: Int) = this@AppState.select(index)
            override fun showNotice(text: String) = this@AppState.showNotice(text)
            override suspend fun rebuildMainRows() {
                collectionCounter.refreshCollectionNames()   // a new or emptied collection shows or leaves the tab
                this@AppState.rebuildMainRows()
            }
            override fun collectionChanged(name: String) = collectionCounter.invalidateCollectionCounts(name)
            override fun selectCollection(name: String) = this@AppState.selectCollection(name)
            override fun menuClosed() { if (menuStale(_screen.value)) swapMainRows() }
            override suspend fun refreshOpenList(select: String?) = this@AppState.refreshOpenList(select)
            override suspend fun setInTools(id: String, on: Boolean) = androidApps.setInTools(id, on)
            override fun rescan() = this@AppState.rescan()
            override suspend fun applyHidden(entry: Listed, hidden: Boolean, closeMenu: Boolean) = this@AppState.applyHidden(entry, hidden, closeMenu)
            override suspend fun renameInIndex(game: GameEntry, name: String): Boolean = this@AppState.renameInIndex(game, name)
            override fun finishSwitch(game: GameEntry, from: SystemFolder, tag: String) = this@AppState.finishSwitch(game, from, tag)
            override fun openGameSettings(entry: Listed) = emulatorSettings.openGame(entry)
            override fun artMenu(entry: Listed): ArtMenuItem = artworkScreens?.artMenu(entry) ?: ArtMenuItem.NONE
            override fun openArtPanel(entry: Listed, fetchFirst: Boolean) = this@AppState.openArtPanel(entry, fetchFirst)
            override fun netplayOffered(entry: Listed): Boolean = this@AppState.netplay?.offers(entry) == true
            override fun openNetplay(entry: Listed) = this@AppState.openNetplay(entry)
            override val netplayInMenu: Boolean get() = !launcherChrome().netplayHint || _screen.value.isMainMenu   // the main menu (the Games tab too) has no hint bar
            override val achievementsInMenu: Boolean get() = hasAchievements && !deps.settings.gameListAchievementsPanel
            override fun openAchievements() = ra.openRaPanel()
            override suspend fun canResume(entry: Listed): Boolean = deps.userData.resumeInfo(entry.system.tag, entry.game.fileName).canResume
            override fun playFromStart(entry: Listed) = launches.playFromStart(entry)
            override val aResumes: Boolean get() = deps.settings.aResumes
            override fun resumeGame(entry: Listed) { launches.resumeGame(entry) }
            override fun hiddenTabs(): List<MenuCategory> = this@AppState.hiddenTabs()
            override fun openHiddenTab(category: MenuCategory) = this@AppState.openHiddenTab(category)
            override val appOnTop: Boolean get() = _appOnTop.value
            override fun takeTopScreen() = this@AppState.takeTopScreen()
        },
    )

    // --- Netplay: the sheet over a game list or the main menu (context menu's Netplay, or Y on a game) ---

    /** The Netplay sheet; null on a platform without netplay. While its page is set it takes every pad press and Back. */
    val netplay: NetplayController? = deps.netplay?.let { NetplayController(scope, it, ::launchNetplay, work = deps.compute, findLibraryRom = ::findLocalRom, playerName = { PlayerName.resolve(deps.settings.playerName, deps.deviceName()) }) }   // handshakes and save files off the main thread

    /** [entry] can be played over netplay here: the platform has it, the game's tag has netplay cores, and it can launch. */
    fun canNetplay(entry: Listed?): Boolean =
        netplay != null && entry != null && (_screen.value as? Screen.Games)?.source != ListSource.Hidden && ContextMenus.hasNetplay(entry) && netplay.offers(entry)

    /** Opens the Netplay sheet on [entry]; nothing for a game [canNetplay] refuses. */
    fun openNetplay(entry: Listed) {
        if (!canNetplay(entry)) return
        netplay?.open(entry)
    }

    /** On the main menu's Collections tab: highlights [name]'s row (a renamed collection, re-sorted) and remembers it for the tab. */
    private fun selectCollection(name: String) {
        val s = _screen.value as? Screen.Systems ?: return
        val at = s.rows.indexOfFirst { it is MainRow.Collection && it.name == name }.takeIf { it >= 0 } ?: return
        _selection.value = ListSelection(at, s.rows.size)
        s.category?.let { tabSelections[it] = _selection.value }
    }

    /** MENU or a long press on row [index] (null = the highlighted row): a game list's menu, or a pinned or recent main-menu row's. */
    fun openContextMenu(index: Int? = null) { contextMenus.openContextMenu(index) }

    /** A long press on the tab row or the single-tab title: the hidden-tab menu (not under the Games tab's panels). */
    fun openTabMenu() { if (!_panelOpen.value) contextMenus.openTabMenu() }

    fun closeContextMenu() = contextMenus.closeContextMenu()

    /** Tap on a menu row: highlight it (a second tap on the highlighted row is [menuOpen]). */
    fun menuSelect(i: Int) = contextMenus.menuSelect(i)

    fun menuOpen() = contextMenus.menuOpen()

    /** The context menu overlay reports how many columns it draws (1 or 2); the d-pad steps by them. */
    fun onMenuLayout(columns: Int) = contextMenus.onMenuLayout(columns)

    /** The text prompt's live text (the composable reports every change), so A can confirm with it. */
    fun menuDraft(text: String) = contextMenus.menuDraft(text)

    /** A in the hidden list: unhides the highlighted game directly (that list has no context menu). */
    fun unhideSelected() {
        if (hideJob?.isActive == true) return   // an unhide is already being applied
        val s = _screen.value as? Screen.Games ?: return
        if (s.source != ListSource.Hidden) return
        val entry = s.entries.getOrNull(_selection.value.index) ?: return
        val job = scope.launch {
            tryOrAny({ e -> _error.value = "Something went wrong: ${e.message ?: e::class.simpleName}" }) {
                applyHidden(entry, hidden = false, closeMenu = false)
            }
        }
        hideJob = job
        job.invokeOnCompletion { if (hideJob === job) hideJob = null }
    }

    /** Hides or unhides [entry]'s game: hidden.txt, then the playable cache, the main menu and the open list are rebuilt. */
    private suspend fun applyHidden(entry: Listed, hidden: Boolean, closeMenu: Boolean) {
        val path = entry.game.path
        val next = if (hidden) PathLines.add(hiddenPaths, path) else PathLines.remove(hiddenPaths, path)
        if (!deps.userData.writeHidden(next)) {
            if (closeMenu) closeContextMenu()
            _error.value = if (hidden) "Could not hide the game" else "Could not unhide the game"
            return
        }
        hiddenPaths = next
        libraryDirty = true
        val hiddenSet = next.toSet()
        libraryIndex.current()?.let { ix ->
            val gen = takeSystemsGen()
            offerSystems(gen, withContext(deps.compute) { ix.playableSystems(hiddenSet) })
        }
        collectionCounter.invalidateCollectionCounts()   // a hidden game leaves the counts
        rebuildMainRows()
        if (closeMenu) closeContextMenu()
        refreshOpenList()
        showNotice(if (hidden) "Hidden. Unhide in Tools › Settings › Library" else "Unhidden")
    }

    /** Rename Rom's index step: renames [game] in memory and on disk, then the playable cache; false on a stale list (no change). */
    private suspend fun renameInIndex(game: GameEntry, name: String): Boolean {
        return mutateIndex(IndexRefresh()) { current -> current?.rename(game, name) } != null
    }

    /**
     * After the sidecar write (and the copy prompt): moves [game] from [from] to [tag] in the index with no scan, writes
     * the index (a failed write deletes the stored copy so the next start rescans rather than showing the old tag), and
     * opens [tag]'s console (a merged one stays merged) on [game], with the Systems selection on that console for Back.
     * When the move changes nothing ([from] does not hold [game]) it rescans rather than keep an index that disagrees
     * with the sidecar.
     */
    private fun finishSwitch(game: GameEntry, from: SystemFolder, tag: String) {
        val toTag = tag.trim().uppercase()
        scope.launch {
            if (!moveInIndex(game, from, toTag)) {   // no index in memory, or a stale list whose move changed nothing: scan instead
                rescan { systems -> systems.firstOrNull { it.tag == toTag && !it.unassigned }?.let { openSystem(it, select = game.path) } }
                return@launch
            }
            val consoles = allMenuTabs.firstOrNull { it.category == MenuCategory.CONSOLES }?.rows.orEmpty()
            val row = consoles.indexOfFirst { it is MainRow.System && it.systems.any { s -> s.tag == toTag } }
            menuCategory = MenuCategory.CONSOLES
            tabSelections[MenuCategory.CONSOLES] = ListSelection(row.coerceAtLeast(0), consoles.size)
            if (row >= 0) openSystem((consoles[row] as MainRow.System).system, select = game.path) else showSystemsNow()
        }
    }

    /**
     * After a game's sidecar names [toTag]: moves [game] from [from] in the index with no scan, writes the index (a failed
     * write deletes the stored copy so the next start rescans rather than showing the old tag) and rebuilds the menu's
     * caches. False when there is no index in memory or the move changed nothing ([from] is stale: the index changed under
     * the open list, so persisting it would keep the old tag).
     */
    private suspend fun moveInIndex(game: GameEntry, from: SystemFolder, toTag: String): Boolean {
        // The counts: the game's path moved with its tag.
        mutateIndex(IndexRefresh(counts = true, rows = true)) { current -> current?.move(game, from, toTag) } ?: return false
        libraryDirty = true
        return true
    }

    /** The Grid body reports its column count (it knows the screen size); the d-pad then steps by tiles, a row at a time. */
    fun onGridLayout(columns: Int) {
        gridColumns = columns.coerceAtLeast(0)
    }

    /** A game list's List, Minimal or vertical stack reports the items one screen shows: LEFT / RIGHT page by them ([pageMove]). */
    fun onPageRows(rows: Int) {
        pageRows = rows.coerceAtLeast(0)
    }

    /**
     * LEFT / RIGHT on a game list whose d-pad runs up and down (List, Minimal, a vertical Carousel or Backdrop): a page of
     * [pageRows] up or down, held at the ends, a held key's repeat paging on (NX Redux's game lists).
     */
    private fun pageMove(button: NxButton) {
        val sel = _selection.value
        val next = ListJumps.page(sel.count, pageRows, sel.index, if (button == NxButton.RIGHT) 1 else -1)
        if (next != sel.index) { _selection.value = sel.copy(index = next); refreshResume() }
    }

    /**
     * L1 / R1 on a console's game list, L2 / R2 on the Games tab, every layout: the previous ([step] -1) / next (1)
     * letter's first game ([ListJumps.letter]), a held key's repeat jumping on. Only a console's list and the Games tab
     * are in name order: Recently Played, a collection, Hidden and a search's results are not, so there they do nothing.
     */
    private fun letterJump(s: Screen.Games, step: Int) {
        if (s.source !is ListSource.Console && !s.isTab) return
        val sel = _selection.value
        val next = ListJumps.letter(s.entries.map { it.shown }, sel.index, step)
        if (next != sel.index) { _selection.value = sel.copy(index = next); refreshResume() }
    }

    /** Home's layout (Layouts → Home): Grid (the dashboard), List or Carousel. */
    internal fun homeStyle(): MenuStyle = MenuStyles.styleOf(deps.settings.menuStyles, MenuCategory.HOME)

    /** The style the screen showing now is drawn in: a main menu tab's own, the game list style, else List. */
    internal fun currentStyle(): MenuStyle = styleOf(_screen.value)

    /** The style [screen] is drawn in (a page sliding out keeps its own): a main menu tab's own, the game list style, else List. */
    internal fun styleOf(screen: Screen): MenuStyle = when (val s = screen) {
        is Screen.Systems -> s.category?.let { MenuStyles.styleOf(deps.settings.menuStyles, it) } ?: MenuStyle.LIST
        is Screen.Games -> gameListLayout().first
        else -> MenuStyle.LIST
    }

    /** The game lists' style and stored orientation as drawn now: on two screens through [MenuStyles.dualGameListLayout]. */
    private fun gameListLayout(): Pair<MenuStyle, MenuOrientation> {
        val s = deps.settings
        return if (_dualMenu.value) MenuStyles.dualGameListLayout(s.gameListStyle, s.gameListOrientation) else s.gameListStyle to s.gameListOrientation
    }

    /** Whether the game lists in [style] offer an orientation: not on two screens, where a Carousel only runs across. */
    private fun gameListTurns(style: MenuStyle): Boolean = MenuStyles.hasOrientation(style) && !_dualMenu.value

    /** The orientation the screen showing now is drawn in ([orientationOf]). */
    internal fun currentOrientation(): MenuOrientation = orientationOf(_screen.value)

    /**
     * The orientation [screen] is drawn in: a main menu tab's or the game lists' stored one while its style ([styleOf])
     * is Carousel or Backdrop, else HORIZONTAL (List, Grid, Home and every other screen).
     */
    internal fun orientationOf(screen: Screen): MenuOrientation = when (val s = screen) {
        is Screen.Systems -> s.category?.takeIf { it != MenuCategory.HOME }?.let {
            MenuStyles.effectiveOrientation(styleOf(s), MenuStyles.orientationOf(deps.settings.menuOrientations, it))
        } ?: MenuOrientation.HORIZONTAL
        is Screen.Games -> gameListLayout().let { (style, o) -> MenuStyles.effectiveOrientation(style, o) }
        else -> MenuOrientation.HORIZONTAL
    }

    /**
     * The d-pad on a laid-out Grid (main menu tab or game list) steps by tiles. Past the first / last tile the main menu
     * (the Games tab too) switches tab (a fresh press only, never a key [repeat]: a held key stops at the edge); any other
     * game list stops. On the main menu UP from the top row ([gridAtTop]) stays instead of wrapping. False when the screen is not a laid-out Grid (the caller does the List step).
     */
    private fun gridMove(button: NxButton, repeat: Boolean = false): Boolean {
        if (gridColumns <= 0 || currentStyle() != MenuStyle.GRID) return false
        val s = _screen.value
        if (s !is Screen.Systems && s !is Screen.Games) return false
        val sel = _selection.value
        if (s is Screen.Systems && button == NxButton.UP && gridAtTop(sel.index, sel.count, gridColumns)) return true
        val next = gridStep(sel.index, sel.count, gridColumns, button)
        if (next == null) {
            // A pushed tab has no tab to switch to: its Grid wraps LEFT / RIGHT at its ends instead (a fresh press only).
            if (s.menuPushed) {
                if (!repeat) { _selection.value = sel.copy(index = if (button == NxButton.RIGHT) 0 else sel.count - 1); refreshResume() }
                return true
            }
            if (s.isMainMenu && !repeat) switchTab(if (button == NxButton.RIGHT) 1 else -1)   // the Games tab too
            return true
        }
        _selection.value = sel.copy(index = next)
        refreshResume()
        return true
    }

    /**
     * The d-pad on a Carousel row (main menu tab or game list) or a game list's Backdrop row: LEFT / RIGHT step one
     * item, UP / DOWN do nothing (on the main menu UP reaches the tab row while it shows: the row is its top). A row needs
     * no layout report, so it works from the first frame. Past the first / last item the main menu (the Games tab too)
     * switches tab (a fresh press only, never a key [repeat]); any other game list stops. A Vertical one is a stack ([stackMove]). False when the screen is not a row.
     */
    private fun rowMove(button: NxButton, repeat: Boolean): Boolean {
        val style = currentStyle()
        if (style != MenuStyle.CAROUSEL && style != MenuStyle.BACKDROP) return false
        val s = _screen.value
        if (s !is Screen.Systems && s !is Screen.Games) return false
        if (orientationOf(s) == MenuOrientation.VERTICAL) return stackMove(s, button, repeat)
        if (button == NxButton.UP || button == NxButton.DOWN) return true
        val sel = _selection.value
        val next = rowStep(sel.index, sel.count, button)
        if (next == null) { if (s.isMainMenu && !repeat) switchTab(if (button == NxButton.RIGHT) 1 else -1); return true }
        _selection.value = sel.copy(index = next)
        refreshResume()
        return true
    }

    /**
     * The d-pad on a Vertical Carousel stack (main menu tab or game list) or a game list's Backdrop stack ([stackStep]):
     * UP / DOWN step one item; past an end a fresh press wraps (UP on the first to the last, DOWN on the last to the
     * first), a held key's [repeat] stops there, as the List. UP reaches the tab row only from an empty main menu stack. LEFT / RIGHT: on the main menu
     * (the Games tab too) a fresh press switches tab (never a key [repeat]); any other game list pages ([pageMove]). False for any other button.
     */
    private fun stackMove(s: Screen, button: NxButton, repeat: Boolean): Boolean {
        when (button) {
            NxButton.LEFT, NxButton.RIGHT -> {
                when {
                    s.isMainMenu -> if (!repeat) switchTab(if (button == NxButton.RIGHT) 1 else -1)   // the Games tab too
                    s is Screen.Games -> pageMove(button)   // a game list's stack pages, as its List
                    else -> Unit
                }
                return true
            }
            NxButton.UP, NxButton.DOWN -> {
                val sel = _selection.value
                val next = stackStep(sel.index, sel.count, button, fresh = !repeat)   // only an empty stack's UP is null: it stays
                if (next == null) return true
                if (next != sel.index) {
                    _selection.value = sel.copy(index = next)
                    refreshResume()
                }
                return true
            }
            else -> return false
        }
    }

    /** A pad press. [repeat]: an auto-repeat of a held direction (the router's), not a fresh press; taps are fresh. */
    fun onButton(pressed: NxButton, repeat: Boolean = false) {
        // An open confirm dialog takes LEFT and RIGHT for its buttons and turns A on Cancel into B ([DialogFocus]).
        var button = dialogFocus.press(pressed) ?: return
        kids?.parentInput()   // Parent mode's idle time starts again
        if (launches.minimalPending) return   // a Minimal open is under way: the row opens in a moment
        launches.clearMinimalOpening()   // opened: the next press clears it
        if (settingsUi.onPopupButton(button)) return   // a touch chooser or a Settings row's info popup takes the pad while open
        // Press to bind waits on the controller's raw keys ([captureKey]): a touch hint's or the on-screen pad's B cancels, the rest wait.
        if (controlBindings.capturingShortcut.value != null) { if (button == NxButton.B) controlBindings.cancelCapture(); return }
        if (_error.value != null) { if (button == NxButton.A || button == NxButton.B) dismissError(); return }
        if (_message.value != null) { if (button == NxButton.A || button == NxButton.B) dismissMessage(); return }
        if (_androidGamesAsk.value != null) {   // the Android games question: A Yes, B Not now
            if (button == NxButton.A) answerAndroidGames(true) else if (button == NxButton.B) answerAndroidGames(false)
            return
        }
        if (_firstTip.value != null) {   // a first-launch tip: A Got it, B (or its lit Don't show again) never again
            if (button == NxButton.A) answerTip(hide = false) else if (button == NxButton.B) answerTip(hide = true)
            return
        }
        if (_updateConfirm.value != null) { onUpdateDialogButton(button, ::answerUpdateConfirm); return }   // About's confirm: A Update, B Cancel
        if (updatePopupShows()) { onUpdateDialogButton(button, ::answerUpdate); return }   // the update dialog: A Update, B Not now
        // An open prompt with the NX keyboard: the keyboard has every press first; the prompt hears only its confirm (A) or
        // cancel (B), so its own handling below runs as it always has.
        if (!answering && keyboard.entry.value != null) button = keyboard.press(button, repeat) ?: return
        // Setup's two buttons side by side ([setupButtons]), the selection on one: LEFT takes the first, RIGHT the second.
        if (button == NxButton.LEFT || button == NxButton.RIGHT) setupButtonPair()?.takeIf { _selection.value.index >= it }?.let { first ->
            _selection.value = _selection.value.copy(index = if (button == NxButton.LEFT) first else first + 1)
            return
        }
        if (_screen.value is Screen.AndroidGamesStep) {   // setup's step: the d-pad moves between Yes and Not now, A chooses, B goes back
            when (button) {
                NxButton.UP -> _selection.value = _selection.value.up(wrap = true)
                NxButton.DOWN -> _selection.value = _selection.value.down(wrap = true)
                NxButton.A -> setupStepRow(_selection.value.index)
                NxButton.B -> back()   // first setup: back to the ROMs-folder offer; a lost home's step: Not now
                else -> Unit
            }
            return
        }
        if (loader.active) { if (button == NxButton.B) loader.cancel(); return }   // a screen is loading: B abandons it, the rest waits
        dismissNoticeOnPress()
        (_screen.value as? Screen.Onboarding)?.let { onboardingUi.onOnboardingButton(it, button); return }
        netplay?.takeIf { it.page.value != null }?.let { it.onButton(button); return }   // the Netplay sheet over a game list
        // The row edit takes the press (A first, then LEFT / RIGHT), or on two panes has the page hear LEFT / RIGHT as its own B / A.
        val heard = settingsUi.onRowEditButton(_screen.value, button) ?: return
        // A held direction turned into B or A acts once: its repeats would climb or open one page per repeat.
        if (repeat && heard != button) return
        onPageButton(heard, repeat)
    }

    /** [button] as the screen itself hears it, once the dialogs, popups and row edit that come first ([onButton]) let it through. */
    private fun onPageButton(button: NxButton, repeat: Boolean) {
        if (isKidsScreen(_screen.value)) return kidsUi.onKidsButton(_screen.value, button)
        (_screen.value as? Screen.KidsSetup)?.let { kidsUi.onKidsSetupButton(it, button); return }
        (_screen.value as? Screen.KidsDayEdit)?.let { kidsUi.onKidsDayButton(it, button); return }
        _screen.value.takeIf { it is Screen.Settings || it is Screen.SettingsMenu }?.let { onSettingsButton(it, button); return }
        (_screen.value as? Screen.ToolApps)?.takeIf { it.menu == null }?.let { androidApps.onToolAppsButton(button); return }
        _screen.value.contextMenu?.let { contextMenus.onMenuButton(it, button); return }
        (_screen.value as? Screen.Systems)?.let { s ->
            s.search?.let { onSearchButton(s, it, button); return }
            s.layoutMenu?.let { onLayoutMenuButton(s, it, button); return }
        }
        (_screen.value as? Screen.Games)?.let { s ->
            s.search?.let { if (s.isTab) onSearchButton(s, it, button) else onListSearchButton(s, it, button); return }   // the tab's is START's
            s.layoutMenu?.let { onLayoutMenuButton(s, it, button); return }
            if (artPanel.value != null) { artPanels?.onButton(button); return }   // the art panel takes every press while open
            ra.raPanel.value?.let { ra.onRaPanelButton(it, button); return }
        }
        if (filePicker?.onButton(_screen.value, button) == true) return
        (_screen.value as? Screen.EmulatorOptions)?.let { if (onEmulatorTouchButton(it, button)) return }
        if (emulatorSettings.onButton(_screen.value, button)) return
        if (gameTimeScreen?.onButton(_screen.value, button) == true) return   // B without a modal falls through to back()
        (_screen.value as? Screen.ArtworkSettings)?.takeIf { button == NxButton.X && !it.dialogOpen }?.let {   // X: the selected row's info
            if (settingsUi.selectedSettingShowsInfo()) showSettingInfo(_selection.value.index); return
        }
        if (artworkScreens?.onButton(_screen.value, button) == true) return
        (_screen.value as? Screen.Switcher)?.let { switcher.onSwitcherButton(it, button, repeat); return }
        // The summary: its move's confirmation takes every button first; the Move row is a switch, A flips it and LEFT / RIGHT
        // leave it alone (as on every settings page).
        (_screen.value as? Screen.FolderSummary)?.let { fs ->
            if (fs.confirmMove) { folders.onFolderMoveConfirm(fs, button); return }
            if ((button == NxButton.LEFT || button == NxButton.RIGHT) && folderSummaryRows(fs).getOrNull(_selection.value.index)?.kind == SummaryRowKind.MOVE) return
        }
        (_screen.value as? Screen.CheatDb)?.let { onCheatDbButton(it, button); return }   // before the generic when: UP/DOWN use the action-row count
        if (_screen.value == Screen.DeviceSync) { onDeviceSyncButton(button); return }   // every button is the controller's
        if (_screen.value == Screen.RetroAchievements) { ra.onRaButton(button); return }   // same: UP/DOWN use the action-row count
        (_screen.value as? Screen.RaDownload)?.let { ra.onRaDownloadButton(it, button); return }
        (_screen.value as? Screen.RaSettings)?.let {   // LEFT/RIGHT change values; dialogs take the buttons; X the selected row's info
            if (button == NxButton.X && !it.signingIn && it.confirm == null) { if (settingsUi.selectedSettingShowsInfo()) showSettingInfo(_selection.value.index); return }
            ra.onRaSettingsButton(it, button); return
        }
        _screen.value.takeIf { it is Screen.RaConsoles || it is Screen.RaGames || it is Screen.RaGame || it is Screen.RaAchievement || it is Screen.RaProfile }?.let { ra.onRaBrowserButton(it, button); return }
        if (button.isDirection && homeTab.homeMove(button, repeat)) return   // Home first: its own two-dimensional moves
        when (button) {
            // A List: UP on the first row wraps to the last, DOWN on the last to the first, on the main menu too (an
            // empty tab's UP does nothing). On the launcher's lists (the main menu's and the game lists) a held
            // key's repeat stops at the end instead of wrapping.
            NxButton.UP -> if (!rowMove(button, repeat) && !gridMove(button, repeat)) {
                if (_screen.value !is Screen.Systems || _selection.value.count > 0) { _selection.value = _selection.value.up(wrap = !repeat || !launcherList()); refreshResume() }
            }
            NxButton.DOWN -> if (!rowMove(button, repeat) && !gridMove(button)) { _selection.value = _selection.value.down(wrap = !repeat || !launcherList()); refreshResume() }
            // The main menu (the Games tab too): LEFT / RIGHT are L1 / R1 (a Grid or row switches only past its ends), on a
            // fresh press only: a held key's repeats never switch tabs. Any other List or Minimal game list pages ([pageMove]).
            NxButton.LEFT, NxButton.RIGHT -> if (!rowMove(button, repeat) && !gridMove(button, repeat)) when {
                _screen.value.isMainMenu -> if (!repeat) switchTab(if (button == NxButton.RIGHT) 1 else -1)
                _screen.value is Screen.Games -> pageMove(button)   // a List or Minimal game list pages
                else -> Unit
            }
            NxButton.A -> openSelected()
            NxButton.B -> back()
            NxButton.Y -> when (val s = _screen.value) {
                is Screen.Games -> s.entries.getOrNull(_selection.value.index)?.let(::openNetplay)
                is Screen.Systems -> s.rows.getOrNull(_selection.value.index).listed()?.let(::openNetplay)   // Continue, a pin, a shelf game
                else -> Unit
            }
            NxButton.SELECT -> switcher.openSwitcher()
            // A game list searches its own games; the main menu, the Games tab too, every game (START's global search).
            NxButton.START -> if (_screen.value.let { it is Screen.Games && !it.isTab }) openListSearch() else openSearch()
            NxButton.X -> if (_screen.value is Screen.Games) ra.openRaPanel()   // the highlighted game's achievements
            NxButton.MENU -> openContextMenu()
            // L1 / R1: the main menu's tabs (on the Games tab a fresh press only, as LEFT / RIGHT); a console's list jumps by letter.
            NxButton.L1, NxButton.R1 -> when (val s = _screen.value) {
                is Screen.Games -> if (!s.isTab) letterJump(s, if (button == NxButton.R1) 1 else -1) else if (!repeat) switchTab(if (button == NxButton.R1) 1 else -1)
                else -> switchTab(if (button == NxButton.R1) 1 else -1)
            }
            // L2 / R2: the Games tab jumps by letter (its L1 / R1 are the tabs'); nothing anywhere else.
            NxButton.L2, NxButton.R2 -> (_screen.value as? Screen.Games)?.takeIf { it.isTab }?.let { letterJump(it, if (button == NxButton.R2) 1 else -1) }
            else -> Unit
        }
    }

    /** The main menu or a game list: their List's held key stops at the ends ([onButton]); other lists wrap whatever. */
    private fun launcherList(): Boolean = _screen.value.let { it is Screen.Systems || it is Screen.Games }

    /**
     * A text field is up (Rename, New collection, a collection's Rename, the RetroAchievements or Artwork sign-in, an art
     * key prompt, a search prompt, the picker's New folder): the platform lets the field see the d-pad first (LEFT/RIGHT
     * move its cursor) instead of routing it straight to [onButton].
     */
    val textEntryOpen: Boolean get() = textEntryOpen(_screen.value)

    /** The NX keyboard types the open prompt: the platform draws it, and Android's isn't chosen or can't open there. */
    val nxKeyboard: Boolean get() = deps.ownKeyboard && (!deps.settings.androidKeyboard || deps.promptOnSecondDisplay())

    private val keyboard by lazy { TextEntryController(::promptSpec, deps.pasteText) }

    /** The open prompt as the NX keyboard edits it; null with none open, or while the platform's keyboard types. */
    val textEntry: StateFlow<TextEntry?> get() = keyboard.entry

    /** The open prompt's confirm word, for the keyboard's hints ("Sign in", "Search", "OK"). */
    val textEntryConfirmLabel: String get() = keyboard.confirmLabel

    /** The keyboard follows a change it was not told of (the keyboard setting, the prompt moving to another display). */
    fun refreshKeyboard() = keyboard.sync()

    /** A tap on the keyboard's key at [at]: the highlight moves there and it types. */
    fun keyboardTapKey(at: KeyPos) { if (!keyboardWaits()) keyboard.tapKeyAt(at)?.let(::promptAnswer) }

    /** An error over the prompt has the input until it is dismissed: the keyboard's taps and a physical keyboard wait. */
    private fun keyboardWaits(): Boolean = _error.value != null

    /** A tap on the open prompt's field [index]: it becomes the one typed in. */
    fun keyboardTapField(index: Int) { if (!keyboardWaits()) keyboard.tapField(index) }

    /** The open prompt's password shown as typed, or as dots again (its field's Show / Hide). */
    fun keyboardToggleReveal() { if (!keyboardWaits()) keyboard.toggleReveal() }

    /** A physical keyboard's printable [text], at the cursor. */
    fun keyboardType(text: String) { if (!keyboardWaits()) keyboard.type(text)?.let(::promptAnswer) }

    /** A physical keyboard's Ctrl+V: as the keyboard's Paste. */
    fun keyboardPaste() { if (!keyboardWaits()) keyboard.paste()?.let(::promptAnswer) }

    /** A physical keyboard's Backspace: as B on the keyboard; held ([repeat]), it clears but never cancels. */
    fun keyboardBackspace(repeat: Boolean = false) = onButton(NxButton.B, repeat)

    /** The prompt hears [button] (A confirm, B cancel) past the keyboard: a tap on the box's buttons, or a keyboard outcome. */
    fun promptAnswer(button: NxButton) {
        answering = true
        try { onButton(button) } finally { answering = false }
    }
    private var answering = false

    /** The open prompt for the keyboard: its fields, setters and confirm word; null with none open or the keyboard off. */
    private fun promptSpec(): PromptSpec? {
        if (!nxKeyboard) return null
        fun signIn(id: Any, user: String, password: String, setUser: (String) -> Unit, setPassword: (String) -> Unit) =
            PromptSpec(id, listOf(EntryField("Username", user), EntryField("Password", password, password = true)), listOf(setUser, setPassword), "Sign in")
        fun one(id: Any, label: String, value: String, set: (String) -> Unit, confirm: String = "OK") = PromptSpec(id, listOf(EntryField(label, value)), listOf(set), confirm)
        return when (val s = _screen.value) {
            is Screen.RaSettings -> if (s.signingIn) signIn("ra", s.draftUser, s.draftPassword, ::raDraftUser, ::raDraftPassword) else null
            is Screen.Onboarding -> when {
                s.prompt -> signIn("setup-ra", s.draftUser, s.draftPassword, ::onboardingDraftUser, ::onboardingDraftPassword)
                s.artSignIn -> signIn("setup-art", s.draftUser, s.draftPassword, ::onboardingDraftUser, ::onboardingDraftPassword)
                s.artKey != null -> one("setup-key-${s.artKey}", "Key", s.draftKey, ::onboardingDraftKey)
                else -> null
            }
            is Screen.ArtworkSettings -> when {
                s.signingIn -> signIn("art", s.draftUser, s.draftPassword, ::artworkDraftUser, ::artworkDraftPassword)
                s.editingKey -> one("art-key-${s.keyFor}", "Key", s.draftKey, ::artworkDraftKey)
                else -> null
            }
            is Screen.FilePicker -> s.prompt?.let { one("folder", "Folder name", it, ::filePickerDraft) }
            is Screen.Settings -> s.nameDraft?.let { one("player-name", "Player name", it, ::playerNameDraft) }
            is Screen.Systems -> s.search?.let { one("search", "Search", it, ::searchDraft, "Search") } ?: menuPromptSpec(s.menu)
            is Screen.Games -> s.search?.let { one(if (s.isTab) "search" else "list-search", "Search", it, ::searchDraft, "Search") } ?: menuPromptSpec(s.menu)   // the tab's is the global search
            else -> menuPromptSpec(s.contextMenu)
        }
    }

    private fun menuPromptSpec(menu: ContextMenu?): PromptSpec? = when (val p = menu?.prompt) {
        is MenuPrompt.Rename -> PromptSpec("rename:${p.initial}", listOf(EntryField("Rename", p.draft)), listOf(::menuDraft), "OK")
        is MenuPrompt.RenameCollection -> PromptSpec("rename-collection:${p.initial}", listOf(EntryField("Rename collection", p.draft)), listOf(::menuDraft), "OK")
        is MenuPrompt.NewCollection -> PromptSpec("new-collection", listOf(EntryField("New collection name", p.draft)), listOf(::menuDraft), "OK")
        else -> null
    }

    /**
     * Leaving Library must reload: the library changed since the last load, the index no longer fits the settings, or
     * some storage is known offline (the reload's probe reads it again once it is back).
     */
    private fun libraryStale(): Boolean {
        val root = home() ?: return true
        val ix = libraryIndex.snapshot ?: return true
        return libraryDirty || !ix.fits(root, deps.settings.extraFolders, deps.settings.defaultTags) ||
            offlineHomeSaid != null || ix.offline.isNotEmpty()
    }

    /** Returns true when a screen was popped. */
    fun back(): Boolean {
        if (_message.value != null) { dismissMessage(); return true }   // a page's message dialog: Back closes it, the page stays
        if (settingsUi.closePopup()) return true   // a touch chooser or a Settings row's info popup: Back closes it
        if (controlBindings.capturingShortcut.value != null) { controlBindings.cancelCapture(); return true }   // press to bind: Back cancels it, the page stays
        if (controlBindings.systemButtonWaiting != null) { controlBindings.systemButtonWaiting = null; settingsUi.refreshSettings(); return true }   // likewise System buttons' wait
        if (_androidGamesAsk.value != null) { answerAndroidGames(false); return true }   // the dialog's B: Not now
        if (_firstTip.value != null) { answerTip(hide = true); return true }   // a tip's B: Don't show again
        if (_updateConfirm.value != null) { answerUpdateConfirm(false); return true }   // About's confirm: Cancel
        if (updatePopupShows()) { answerUpdate(false); return true }   // the update dialog's B: Not now
        netplay?.takeIf { it.page.value != null }?.let { it.onButton(NxButton.B); return true }   // the Netplay sheet: its own B
        switcher.cancelLoad()   // B while the switcher loads abandons it
        if (loader.cancel()) return true   // likewise a screen load (the overlay): the screen underneath stays
        return when (val s = _screen.value) {
            // Kids Mode: Back never leaves the tiles screen (the app never closes under a kid); the PIN and the sheet go back to it.
            Screen.KidsHome -> true
            is Screen.KidsPin -> { kidsUi.kidsPinLeave(s); true }
            Screen.KidsUnlock -> { kidsUi.kidsBackToKid(); true }
            is Screen.KidsSetup -> { kidsUi.kidsSetupLeave(s); true }   // setup cancelled (or Allowed apps done): the Kids Mode page
            is Screen.KidsDayEdit -> { kidsUi.kidsDayLeave(s); true }
            is Screen.Onboarding -> {
                when {
                    s.prompt -> _screen.value = s.copy(prompt = false, draftPassword = "")
                    else -> onboardingUi.onboardingBack()
                }
                true
            }
            is Screen.Games -> {
                if (s.menu?.busy == true) return true   // a choice is being applied: keep the menu until the new list opens
                if (s.menu != null) { closeContextMenu(); return true }
                if (s.search != null) { _screen.value = s.copy(search = null); return true }
                if (s.layoutMenu != null) { _screen.value = s.copy(layoutMenu = null); return true }
                if (artPanel.value != null) { artPanels?.onButton(NxButton.B); return true }   // closes it, or keeps on its question
                if (ra.backRaPanel()) return true
                // The Games tab is the main menu: its B is the menu's (nothing to pop), a pushed one's goes back under it.
                if (s.isTab) return if (s.tabBar?.pushed == true) { popHiddenTab(); true } else false
                // Lands only on the main menu (Hidden and Unassigned go to Settings); a Minimal list's title drops back into a Minimal tab's row.
                intendNav(if (styleOf(s) == MenuStyle.MINIMAL) NavMotion.MINIMAL_BACK else NavMotion.BACK)
                leaveList(s.source)
                true
            }
            is Screen.Systems -> if (s.search != null) { _screen.value = s.copy(search = null); true }
                else if (s.layoutMenu != null) { _screen.value = s.copy(layoutMenu = null); true }
                else if (s.menu != null) { if (!s.menu.busy) closeContextMenu(); true }
                else if (s.pushed) { popHiddenTab(); true }
                else false
            is Screen.Settings -> if (s.nameDraft != null) { _screen.value = s.copy(nameDraft = null); true } else if (s.confirmReset) { _screen.value = s.copy(confirmReset = false); true }
                else if (s.confirmDriver != null) { _screen.value = s.copy(confirmDriver = null); true } else {   // Back keeps the settings
                settingsUi.rememberSection(s.section, _selection.value)
                // A sub-page goes back to its section. Library or Emulators: after a change ([libraryDirty]), or an extra set
                // or default tag changed behind the page's back (it fails the fit), it reloads (rescanning if needed); either
                // way it ends on the menu.
                if (s.section.parent != null) leaveSubPage(s.section)
                else if (s.section.reloads && libraryStale()) load { settingsUi.showSettingsMenu() } else settingsUi.showSettingsMenu()
                true
            }
            is Screen.SettingsMenu -> { settingsUi.rememberSettingsMenu(_selection.value); showTools(); true }
            is Screen.ToolApps -> {
                if (s.menu != null) { if (!s.menu.busy) closeContextMenu() }
                else if (s.pick == AppPick.GAMES) settingsUi.showSettingsOn(SettingsSection.LIBRARY, SettingsKind.ANDROID_GAMES)
                // The drawer: back on its tile, found by kind (an app added meanwhile may sort before it).
                else if (menuCategory == MenuCategory.HOME) showTools()
                else showSystemsNow(MenuCategory.TOOLS) { it is MainRow.Tool && it.row.kind == ToolKind.APPS }
                true
            }
            Screen.AddFirstExtra -> {
                val page = folders.homePage
                when {
                    !onboardingUi.inFirstSetup() -> skipFirstExtra()
                    page != null -> { _screen.value = page; _selection.value = ListSelection(0, folderSummaryRows(page).size) }   // back to the home page
                    onboardingUi.mediaHomeTaken -> backPastMediaHome()
                    else -> showPickFolderScreen()
                }
                true
            }
            // First setup's home pick: back to the All files step while it still applies, else to the welcome (a lost home after setup has nothing before it).
            Screen.PickFolder -> if (onboardingUi.inFirstSetup()) { onboardingUi.backFromHomePick(); true } else false
            is Screen.FilePicker -> { filePicker?.onButton(s, NxButton.B); true }
            is Screen.PermissionsStep -> { if (deps.settings.onboardingDone == false) onboardingUi.backToWelcome() else skipAllFiles(); true }
            is Screen.StorageUpgrade -> { upgrade.storageUpgradeBack(s); true }   // required: B never leaves it for the library
            is Screen.CopySaves -> { if (!s.busy) upgrade.copySavesRow(if (s.error != null) 0 else 1); true }   // B: Keep separate (or OK)
            is Screen.BiosImportResult -> { biosResultDone(); true }
            is Screen.AndroidGamesStep -> { if (onboardingUi.inFirstSetup()) showAddFirstExtra() else answerAndroidGames(false); true }
            // The summary: B stops a running move (after the current game); otherwise back to the ROMs folder step (the folder stays added).
            // The home page: back to the pick (a new pick shows its page again).
            is Screen.FolderSummary -> {
                when {
                    s.confirmMove -> _screen.value = s.copy(confirmMove = false)   // the move's confirmation closes first
                    s.progress != null -> folders.cancelMove()
                    s.extraId == Recents.HOME && onboardingUi.mediaHomeTaken -> backPastMediaHome()
                    s.extraId == Recents.HOME -> { folders.homePage = null; showPickFolderScreen() }
                    else -> showAddFirstExtra()
                }
                true
            }
            is Screen.Switcher -> {
                if (s.confirmRemove) _screen.value = s.copy(confirmRemove = false) else switcher.closeSwitcher()   // back dismisses the confirm first
                true
            }
            is Screen.CheatDb -> { onCheatDbButton(s, NxButton.B); true }
            Screen.DeviceSync -> { onDeviceSyncButton(NxButton.B); true }
            is Screen.RetroAchievements, is Screen.RaConsoles, is Screen.RaGames, is Screen.RaGame, is Screen.RaAchievement, is Screen.RaSettings, Screen.RaProfile -> { ra.raBack(s); true }
            is Screen.RaDownload -> { ra.onRaDownloadButton(s, NxButton.B); true }
            is Screen.EmulatorList, is Screen.EmulatorConsoles, is Screen.EmulatorOptions -> emulatorSettings.back(s)
            is Screen.GameTime -> { if (s.modal != null) gameTimeScreen?.back(s) else showTools(); true }
            is Screen.GameTimeSessions -> { gameTimeScreen?.leaveSessions() ?: showTools(); true }
            is Screen.Artwork, is Screen.ArtworkConsoles, is Screen.ArtworkProgress, is Screen.ArtworkSettings,
            is Screen.ArtworkReset -> { artworkScreens?.onButton(s, NxButton.B) ?: showTools(); true }
            else -> false
        }
    }

    /** A platform failure the state cannot see itself (a folder grant the picker could not keep). */
    fun showError(message: String) { _error.value = message }

    fun dismissError() { _error.value = null }

    fun dismissMessage() { _message.value = null }

    /**
     * The platform's home state or apps may have changed (the role dialog, Android's home settings, a new listing): System's
     * Use as home screen and Library's Android games re-read them.
     */
    /** Auto-rotate or the window's shape changed: Button layout's orientation rows follow. */
    fun onRotationChanged() = refreshSettingsOn(SettingsSection.BUTTON_LAYOUT)

    fun onLauncherChanged() {
        kidsUi.kidsHomeCheck()   // the role dialog's answer: Kids Mode on once home
        refreshSettingsOn(SettingsSection.SYSTEM, SettingsSection.LIBRARY, SettingsSection.KIDS)
    }

    /**
     * The role dialog came back without the home role: declined, or answered at once by an earlier "Don't ask again".
     * System re-reads the state and a notice says where the home app is set by hand.
     */
    fun onHomeRoleRefused() {
        val kidsAsked = deps.settings.kidsOnWhenHome   // Kids Mode's setup asked: its own notice says it is off
        onLauncherChanged()
        if (kidsAsked || _notice.value == KIDS_HOME_REFUSED) return   // said already (a stopped activity's onForeground ran first)
        showNotice("Set NX Redux as the home app in Android settings → Default apps")
    }

    /** The Android games console's apps of [installed], from the settings' list ([androidConsoleMembers]). */
    private fun androidGames(installed: List<InstalledApp>): List<InstalledApp> =
        androidConsoleMembers(installed, deps.settings.androidConsoleApps)

    /** Home's Android pins ([AppSettings.androidPins]) in pin order, each as its console entry; one not in the console is skipped. */
    private fun androidPinned(): List<Listed> {
        val console = androidConsole ?: return emptyList()
        return deps.settings.androidPins.mapNotNull { id -> console.games.firstOrNull { it.fileName == id }?.let { Listed(console, it) } }
    }

    /** [refreshAndroidConsole] in its own job (a newer call replaces an older one). */
    private fun refreshInstalled() {
        if (deps.launcher == null) return
        installedJob?.cancel()
        installedJob = scope.launch { refreshAndroidConsole() }
    }

    /**
     * Reloads [installedCache] (the platform lists the apps off the main thread); Library then re-reads its Android games count,
     * and the Android games console is rebuilt from it ([applyAndroidConsole]). A failed listing keeps both as they were.
     */
    private suspend fun refreshAndroidConsole() {
        val l = deps.launcher ?: return
        val apps = tryOr({ return }) { l.installedApps() }
        val appsTab = kidsUi.withKidsApps(emptyList())
        installedCache = apps
        if (kidsUi.withKidsApps(emptyList()) != appsTab) swapMainRows()   // Kids Mode's Apps tab: an allowed app installed or removed
        onLauncherChanged()
        applyAndroidConsole()
        maybeAskAndroidGames()   // a game installed since the menu showed
    }

    /** The Android games console as [installedCache] and the settings make it now ([androidConsoleOf]); null with no game or before the first load. */
    private fun buildAndroidConsole(): SystemFolder? {
        val installed = installedCache ?: return null
        return androidConsoleOf(androidGames(installed), deps.androidArtStore)
    }

    /**
     * Rebuilds [androidConsole]; the main menu's rows follow when they were built with another, and so does an open
     * Android list showing another with no menu over it (an emptied one leaves as B would). Compared with what each
     * shows, not the field's last value, so a refresh cancelled halfway is completed by the next.
     */
    private suspend fun applyAndroidConsole() {
        val next = buildAndroidConsole()
        // Only a changed console replaces the field: an equal rebuild (every return to the app) keeps the Games tab's list.
        if (next != androidConsole) androidConsole = next
        if (allMenuTabs.isNotEmpty() && next != menuAndroidConsole) {   // before the menu's first build, that build takes it
            refreshMainRows()
        }
        val s = _screen.value as? Screen.Games ?: return
        val shown = (s.source.base as? ListSource.Console)?.system?.takeIf { it.android } ?: return
        if (s.menu == null && shown != next) refreshOpenList()
    }

    /**
     * The installed library's Android games question, once: over the main menu (no context menu open), with a tree and a
     * launcher, before it was answered, and when [installedCache] has a detected game. First setup asks it as a step instead.
     */
    private fun maybeAskAndroidGames() {
        if (deps.launcher == null || home() == null || deps.settings.androidGamesAsked || onboardingUi.firstSetup) return
        if (_androidGamesAsk.value != null) return
        val s = _screen.value.takeIf { it.isMainMenu } ?: return   // any tab, the Games tab too
        if (s.contextMenu != null || _panelOpen.value) return   // nor over the Games tab's panels (asked when they close)
        val ask = androidGamesAskFor(installedCache ?: return) ?: return
        androidAskGuard?.cancel()
        androidAskGuard = scope.launch { delay(ANDROID_ASK_GUARD_MILLIS) }
        _androidGamesAsk.value = ask
    }

    /**
     * The update dialog may show over [screen]: the main menu with no context menu, the Android games question or a Problem
     * over it (nor, on the Games tab, a panel: [updatePopup] waits on [_panelOpen] too).
     */
    private fun updatePopupFits(ask: AndroidGamesAsk?, error: String?, screen: Screen): Boolean =
        ask == null && error == null && screen.isMainMenu && screen.contextMenu == null

    /** The update dialog is drawn and still fits what shows now (so a press is its answer). */
    private fun updatePopupShows(): Boolean =
        updatePopup.value != null && updatePopupFits(_androidGamesAsk.value, _error.value, _screen.value)

    /** An update dialog's pad: A answers yes, B no, up / down scroll long notes. */
    private fun onUpdateDialogButton(button: NxButton, answer: (Boolean) -> Unit) {
        when (button) {
            NxButton.A -> answer(true)
            NxButton.B -> answer(false)
            NxButton.UP -> _updateScroll.tryEmit(-1)
            NxButton.DOWN -> _updateScroll.tryEmit(1)
            else -> Unit
        }
    }

    /**
     * Update ([yes]) or Not now to the update dialog: Update downloads the release (its progress on About's row and in the
     * notification), Not now silences that version. Ignored in the dialog's first [ANDROID_ASK_GUARD_MILLIS], as the Android
     * games question is.
     */
    fun answerUpdate(yes: Boolean) {
        val u = deps.updater ?: return
        if (!updatePopupShows() || updateAskGuard?.isActive == true) return
        if (yes) u.update() else u.notNow()
    }

    /** About's confirm: Update downloads (the controller ignores it if the release is no longer on offer); Cancel just closes it. */
    fun answerUpdateConfirm(yes: Boolean) {
        if (_updateConfirm.value == null || updateAskGuard?.isActive == true) return
        _updateConfirm.value = null
        if (yes) deps.updater?.update()
    }

    /** Opens About's confirm for [release], guarded as the popup is: the A that opened it can't also answer it. */
    private fun confirmUpdate(release: AppRelease) {
        _updateConfirm.value = release
        updateAskGuard?.cancel()
        updateAskGuard = scope.launch { delay(ANDROID_ASK_GUARD_MILLIS) }
    }

    /**
     * The host is going away (its activity destroyed): the process-wide updater stops asking it, unless a newer instance
     * took over. With no host, an install waits as off the main menu ("Ready to install") until a menu shows again.
     */
    fun releaseUpdater() {
        val u = deps.updater ?: return
        if (u.hostBusy === updateHost) u.hostBusy = { "" }
    }

    /**
     * What the host holds an install for: the Netplay sheet; "" off the main menu (a game list may be about to launch); null
     * when free, the Games tab included (it is the menu, as Home is, where A launches too).
     */
    private fun updateHostBusy(): String? = when {
        netplay?.page?.value != null -> "Close Netplay to install"
        !_screen.value.isMainMenu -> ""
        else -> null
    }

    /** A on About's update row: Check for updates (also after a failed check), Update to after one found a release, Ready to install installs; nothing while busy. */
    private fun appUpdateRowA(u: UpdateController) {
        when (val s = u.state.value) {
            UpdateState.Idle, is UpdateState.UpToDate -> u.checkNow()
            is UpdateState.Failed -> if (s.release == null) u.checkNow() else confirmUpdate(s.release)
            is UpdateState.Available -> confirmUpdate(s.release)
            is UpdateState.Waiting -> u.installNow()
            else -> Unit
        }
    }

    /**
     * First setup after its ROM folder steps: with a launcher, an unanswered question and a detected game, the Android
     * games step ([Screen.AndroidGamesStep]); then (or straight away without one) [next], the library's load or scan.
     */
    private fun afterFolderSteps(next: () -> Unit) {
        val l = deps.launcher
        if (l == null || deps.settings.androidGamesAsked) { endFolderSteps(); next(); return }
        _screen.value = Screen.Loading   // a second Skip or pick cannot start it twice
        scope.launch {
            val apps = tryOrNull { l.installedApps() }
            if (apps != null) installedCache = apps
            val ask = apps?.let(::androidGamesAskFor)
            if (ask == null) { endFolderSteps(); next(); return@launch }
            afterAndroidStep = next
            onboardingUi.androidStepShown = true
            _screen.value = Screen.AndroidGamesStep(ask)
            _selection.value = ListSelection(0, 2)   // Yes, Not now
        }
    }

    /**
     * Yes ([yes]) or Not now to the Android games question (the step or the dialog): Yes appends every detected game to
     * the console's list, their Tools pins becoming Android pins ([moveToolPinsToAndroid]); both mark it answered. The
     * step goes on into the app; the dialog leaves the menu, its Consoles rows following. The dialog ignores an answer
     * (a key or a tap) in its first [ANDROID_ASK_GUARD_MILLIS].
     */
    fun answerAndroidGames(yes: Boolean) {
        val step = _screen.value as? Screen.AndroidGamesStep
        if (step == null && androidAskGuard?.isActive == true) return
        val ask = step?.ask ?: _androidGamesAsk.value ?: return
        if (yes) {
            val ids = deps.settings.androidConsoleApps
            deps.settings.androidConsoleApps = ids + ask.ids.filter { it !in ids }
        }
        deps.settings.androidGamesAsked = true
        _androidGamesAsk.value = null
        if (step != null) {
            endFolderSteps()
            androidConsole = buildAndroidConsole()   // the load or scan next builds the menu with it
            val next = afterAndroidStep ?: { load() }
            afterAndroidStep = null
            if (!yes) { next(); return }
            _screen.value = Screen.Loading   // a second A cannot answer twice
            scope.launch { moveToolPinsToAndroid(ask.ids); next() }   // moved first: the load reads the pins file
        } else scope.launch { if (yes) moveToolPinsToAndroid(ask.ids); applyAndroidConsole() }
    }

    /**
     * Apps [ids] joined the Android games console: each one's Tools pin on Home ("APP:" and its id, [ToolPins]) becomes
     * an Android pin ([AppSettings.androidPins], appended unless there), so it stays on Home. The pin is not moved back
     * when the app leaves the console (an Android pin out of the console is skipped and kept).
     */
    private suspend fun moveToolPinsToAndroid(ids: List<String>) {
        val keys = readToolPins()
        val moved = keys.filter { k -> ids.any { k == "APP:$it" } }
        if (moved.isEmpty()) return
        val pins = deps.settings.androidPins
        deps.settings.androidPins = pins + moved.map { it.removePrefix("APP:") }.filter { it !in pins }
        writeToolPins(keys - moved.toSet())   // a failed write keeps the Tools pin, hidden while the app is in the console
    }

    // --- Android apps: the picker and app launches live in AppsController ---

    /** The apps grid laid out [columns] tiles wide: the d-pad then steps by tiles. */
    fun onToolAppsLayout(columns: Int) = androidApps.onToolAppsLayout(columns)

    // --- Kids Mode: the tiles screen, its PIN and unlock sheet, Settings' page and setup live in KidsScreensController ---

    val kidsTiles: StateFlow<List<KidsTile>> get() = kidsUi.kidsTiles
    val kidsModeState: StateFlow<KidsMode> get() = kidsUi.kidsModeState
    internal var kidsNewPin: String?   // internal: a test drops it to check setup never ends without a PIN
        get() = kidsUi.kidsNewPin
        set(v) { kidsUi.kidsNewPin = v }
    fun kidsNow(): LocalNow? = kidsUi.kidsNow()
    fun kidsShowWarning() = kidsUi.kidsShowWarning()
    fun onKidsColumns(columns: Int) = kidsUi.onKidsColumns(columns)
    fun onKidsUnlockColumns(columns: Int) = kidsUi.onKidsUnlockColumns(columns)
    fun onTouch() = kidsUi.onTouch()
    fun kidsOpenApp(id: String) = kidsUi.kidsOpenApp(id)
    fun kidsLockPressed() = kidsUi.kidsLockPressed()
    fun kidsParentPressed() = kidsUi.kidsParentPressed()
    fun kidsPinDigit(d: Int) = kidsUi.kidsPinDigit(d)
    fun kidsPinBack() = kidsUi.kidsPinBack()
    internal fun kidsPinTap(key: PinKey) = kidsUi.kidsPinTap(key)
    fun kidsPinEnter() = kidsUi.kidsPinEnter()
    fun kidsUnlockChip(chip: UnlockChip) = kidsUi.kidsUnlockChip(chip)
    fun kidsOpenFullMenu() = kidsUi.kidsOpenFullMenu()
    fun kidsOpenSettings() = kidsUi.kidsOpenSettings()
    fun kidsEndParent() = kidsUi.kidsEndParent()
    fun kidsSetupRow(index: Int) = kidsUi.kidsSetupRow(index)
    fun kidsSetupSetting(event: NxSettingEvent) = kidsUi.kidsSetupSetting(event)
    fun kidsDayRow(index: Int) = kidsUi.kidsDayRow(index)
    fun kidsDaySetting(event: NxSettingEvent) = kidsUi.kidsDaySetting(event)

    /**
     * Shows the first-launch tip the screen calls for, if it is due ([FirstTip]): the main menu's on a main menu tab, then a
     * game list's on a game list or the Games tab. Each once a launch, until its Don't show again; never over another
     * dialog, a context menu, a prompt or a panel, nor while first setup or Kids Mode has the screen.
     */
    private fun maybeShowTip() {
        if (_firstTip.value != null || onboardingUi.firstSetup || loader.active || kidsUi.kidsLocked) return
        if (_androidGamesAsk.value != null || _updateConfirm.value != null || updatePopupShows() || _error.value != null) return
        if (netplay?.page?.value != null) return
        val s = _screen.value
        val free = s.contextMenu == null && when (s) {
            is Screen.Systems -> s.isMainMenu && s.search == null && s.layoutMenu == null
            is Screen.Games -> s.search == null && s.layoutMenu == null && !_panelOpen.value
            else -> false
        }
        if (!free) return
        fun due(t: FirstTip) = t !in tipsShown && !when (t) {
            FirstTip.MAIN_MENU -> deps.settings.mainMenuTipHidden
            FirstTip.GAME_LIST -> deps.settings.gameListTipHidden
        }
        val tip = when {
            s.isMainMenu && due(FirstTip.MAIN_MENU) -> FirstTip.MAIN_MENU
            s is Screen.Games && due(FirstTip.GAME_LIST) -> FirstTip.GAME_LIST
            else -> return
        }
        tipsShown += tip
        tipGuard?.cancel()
        tipGuard = scope.launch { delay(ANDROID_ASK_GUARD_MILLIS) }
        _firstTip.value = tip
    }

    /** The tip's answer: Got it closes it until the next launch; [hide] (Don't show again) never shows it again. */
    fun answerTip(hide: Boolean) {
        if (tipGuard?.isActive == true) return
        val tip = _firstTip.value ?: return
        if (hide) when (tip) {
            FirstTip.MAIN_MENU -> deps.settings.mainMenuTipHidden = true
            FirstTip.GAME_LIST -> deps.settings.gameListTipHidden = true
        }
        _firstTip.value = null
        maybeShowTip()   // the Games tab: its own tip next
    }

    init {
        // Last in the class: every property [KidsScreensController.onKidsMode] reads is set by now.
        kids?.let { k -> scope.launch { k.mode.collect { kidsUi.onKidsMode(it); contextMenus.onKidsRestricted() } } }
        // The first-launch tips follow the screen (and a panel closing over it).
        scope.launch { combine(_screen, _panelOpen) { _, _ -> }.collect { maybeShowTip() } }
    }
}

/** [AppSettings.onboardingStep] while first setup's folder steps are under way (no [OnboardingStep] has this name). */
private const val FOLDER_STEPS = "FOLDERS"

/** Settings' Saves note and the library's notice until the one-time move is done. */
internal const val SAVES_STILL_INSIDE = "Saves are still stored inside the app until your home folder is available."

/** The library's notice once the quiet move failed on several starts. */
private const val MOVE_STUCK = "Saves couldn't move to your home folder. They're still inside the app."

internal enum class PermissionRow { ALL_FILES, NOTIFICATIONS, CONTINUE }

/** [s]'s rows: a row per permission this device uses, then Continue. */
internal fun permissionRows(s: Screen.PermissionsStep): List<PermissionRow> = listOfNotNull(
    PermissionRow.ALL_FILES.takeIf { s.allFiles != null },
    PermissionRow.NOTIFICATIONS.takeIf { s.notifications != null },
    PermissionRow.CONTINUE,
)

