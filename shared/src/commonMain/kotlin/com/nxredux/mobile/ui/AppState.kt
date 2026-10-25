package com.nxredux.mobile.ui

import com.nxredux.mobile.sync.SyncLink
import com.nxredux.mobile.shortcuts.CaptureResult
import com.nxredux.mobile.shortcuts.KeyLabels
import com.nxredux.mobile.shortcuts.ShortcutAction
import com.nxredux.mobile.shortcuts.ShortcutBinding
import com.nxredux.mobile.shortcuts.ShortcutCapture
import com.nxredux.mobile.shortcuts.ShortcutSet
import com.nxredux.mobile.shortcuts.Shortcuts
import com.nxredux.mobile.shortcuts.TouchSlot
import com.nxredux.mobile.update.AppRelease
import com.nxredux.mobile.update.UpdateController
import com.nxredux.mobile.update.UpdateState
import com.nxredux.mobile.library.unassignedHint
import com.nxredux.mobile.library.folderSummary
import com.nxredux.mobile.library.homeSummary
import com.nxredux.mobile.library.FolderMerge
import com.nxredux.mobile.library.splitFolders
import com.nxredux.mobile.library.rewriteLines
import com.nxredux.mobile.library.RomMover
import com.nxredux.mobile.library.MoveResult
import com.nxredux.mobile.achievements.RaController
import com.nxredux.mobile.achievements.RaGameView
import com.nxredux.mobile.achievements.RaMessage
import com.nxredux.mobile.achievements.RaProgress
import com.nxredux.mobile.achievements.RaState
import com.nxredux.mobile.achievements.RaStatus
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
import com.nxredux.mobile.library.ArcadeCompanions
import com.nxredux.mobile.library.ArcadeSets
import com.nxredux.mobile.library.ArtRefs
import com.nxredux.mobile.library.CollectionCountCache
import com.nxredux.mobile.library.Collections
import com.nxredux.mobile.library.CountStamps
import com.nxredux.mobile.library.CompanionLookup
import com.nxredux.mobile.library.DiscSheet
import com.nxredux.mobile.library.GameEntry
import com.nxredux.mobile.library.LibraryIndex
import com.nxredux.mobile.library.LibraryIndexCodec
import com.nxredux.mobile.library.LibraryScanner
import com.nxredux.mobile.library.MoveItem
import com.nxredux.mobile.library.ListingUnavailable
import com.nxredux.mobile.library.MergedLabels
import com.nxredux.mobile.library.NaomiSets
import com.nxredux.mobile.library.Names
import com.nxredux.mobile.library.RomsSource
import com.nxredux.mobile.library.Sidecars
import com.nxredux.mobile.library.Skeleton
import com.nxredux.mobile.library.SystemFolder
import com.nxredux.mobile.library.fits
import com.nxredux.mobile.library.matches
import com.nxredux.mobile.library.move
import com.nxredux.mobile.library.path
import com.nxredux.mobile.library.setArt
import com.nxredux.mobile.library.withArt
import com.nxredux.mobile.library.rename
import com.nxredux.mobile.library.playableSystems
import com.nxredux.mobile.library.toSystems
import com.nxredux.mobile.library.withFirstSeen
import com.nxredux.mobile.options.CoreOptionSource
import com.nxredux.mobile.options.EmulatorPage
import com.nxredux.mobile.options.NoCoreOptions
import com.nxredux.mobile.netplay.NetplaySession
import com.nxredux.mobile.runner.BottomScreen
import com.nxredux.mobile.runner.ClassicNes
import com.nxredux.mobile.runner.Console
import com.nxredux.mobile.runner.ContentRef
import com.nxredux.mobile.runner.CoreDescriptor
import com.nxredux.mobile.runner.CoreRegistry
import com.nxredux.mobile.runner.EmulatorRunner
import com.nxredux.mobile.runner.GameSession
import com.nxredux.mobile.scraper.AndroidArtStore
import com.nxredux.mobile.scraper.ArtHave
import com.nxredux.mobile.scraper.ArtworkController
import com.nxredux.mobile.scraper.ArtworkHost
import com.nxredux.mobile.scraper.LibraryGameRef
import com.nxredux.mobile.settings.AccentPreset
import com.nxredux.mobile.storage.ImportSummary
import com.nxredux.mobile.storage.SaveFormat
import com.nxredux.mobile.storage.StateFormat
import com.nxredux.mobile.settings.SecondaryAccent
import com.nxredux.mobile.settings.OptionTextSize
import com.nxredux.mobile.settings.AppSettings
import com.nxredux.mobile.settings.ImportResult
import com.nxredux.mobile.settings.ImportSource
import com.nxredux.mobile.settings.SaveTransfer
import com.nxredux.mobile.settings.SaveTransferRules
import com.nxredux.mobile.settings.BackdropArt
import com.nxredux.mobile.settings.SwitcherLayout
import com.nxredux.mobile.settings.ConsoleArt
import com.nxredux.mobile.settings.GameListArt
import com.nxredux.mobile.settings.MenuCategory
import com.nxredux.mobile.settings.MenuOrientation
import com.nxredux.mobile.settings.MenuStyle
import com.nxredux.mobile.settings.MenuStyles
import com.nxredux.mobile.settings.PadLayoutMap
import com.nxredux.mobile.settings.PadScreen
import com.nxredux.mobile.settings.PadSlot
import com.nxredux.mobile.settings.PadLayouts
import com.nxredux.mobile.settings.PadShape
import com.nxredux.mobile.settings.PadTheme
import com.nxredux.mobile.settings.PadVibration
import com.nxredux.mobile.settings.GameRumble
import com.nxredux.mobile.settings.VerticalListAlignment
import com.nxredux.mobile.settings.stepFrom
import com.nxredux.mobile.settings.ExtraFolder
import com.nxredux.mobile.settings.FolderPicker
import com.nxredux.mobile.settings.Sources
import com.nxredux.mobile.settings.SourceConversion
import com.nxredux.mobile.storage.FileRefs
import com.nxredux.mobile.storage.AllFilesAccess
import com.nxredux.mobile.storage.HomeLocator
import com.nxredux.mobile.storage.StorageUpgradeGate
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
import com.nxredux.mobile.storage.Shortcut
import com.nxredux.mobile.storage.SidecarStore
import com.nxredux.mobile.storage.ToolPins
import com.nxredux.mobile.storage.UserDataStore
import com.nxredux.mobile.ui.nx.HomeFrame
import com.nxredux.mobile.ui.nx.HomeShelf
import com.nxredux.mobile.ui.nx.FooterMode
import com.nxredux.mobile.ui.nx.LauncherChrome
import com.nxredux.mobile.ui.nx.ListSelection
import com.nxredux.mobile.ui.nx.NxRow
import com.nxredux.mobile.ui.nx.NxSettingControl
import com.nxredux.mobile.ui.nx.NxSettingEvent
import com.nxredux.mobile.ui.nx.NxSettingRow
import com.nxredux.mobile.ui.nx.control
import com.nxredux.mobile.ui.nx.fullInfo
import com.nxredux.mobile.ui.nx.launcherChromeFor
import com.nxredux.mobile.ui.nx.NxButton
import com.nxredux.mobile.ui.nx.gridAtTop
import com.nxredux.mobile.ui.nx.gridBottom
import com.nxredux.mobile.ui.nx.gridStep
import com.nxredux.mobile.ui.nx.homeBottom
import com.nxredux.mobile.ui.nx.homeClamp
import com.nxredux.mobile.ui.nx.rowStep
import com.nxredux.mobile.ui.nx.stackStep
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.CoroutineContext

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
    /** Opens the pad layout editor (Android: its own activity); it saves to the settings and ends with [AppState.onPadLayoutEdited]. Null: no editor. */
    val padEditor: ((PadEditRequest) -> Unit)? = null,
    /** Settings → Library → Saves: Export and Import (null: rows do nothing). */
    val saveTransfer: SaveTransfer? = null,
    /** Tools → Device Sync (null: a platform without it, so no Tools row). */
    val deviceSync: DeviceSyncController? = null,
    /** All files access (a sideloaded Android 11+ build); null: none (SAF only, no setup step, no Settings row). */
    val allFilesAccess: AllFilesAccess? = null,
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
    /** Where the home is now (the full-access folder, else the app's Android/media one); null: the saved [AppSettings.treeRef] (tests). */
    val homes: HomeLocator? = null,
    /** The one-time storage upgrade for installs from v0.3.0 and earlier (remove after v1.0); null: none (tests, other platforms). */
    val storageUpgrade: StorageUpgradePlatform? = null,
    /** The in-app updater (Android's, process-wide): the main menu's Update available popup and About's update row; null: neither. */
    val updater: UpdateController? = null,
)

/**
 * Controls → Pad layout → a shape row: edit [slot] (the shape, on the screen the page is on) of
 * [consoleKey] (a console id, or [PadLayouts.SHARED_KEY]). [tag] picks the pad drawn (a console's first tag; "" for
 * Shared's standard pad), [title] names it in the editor.
 */
data class PadEditRequest(val consoleKey: String, val slot: PadSlot, val tag: String, val title: String)

sealed interface Screen {
    /** First setup's first step, before the home pick, where All files access is declared and not granted. */
    /**
     * First setup's Permissions page, before the home pick: each permission this device uses, as granted (true), not yet
     * (false) or not used here (null). A row asks for its permission; Continue goes on.
     */
    data class PermissionsStep(val allFiles: Boolean?, val notifications: Boolean?) : Screen
    /**
     * The list-style picker for [mode], in [dir] (null: the volume list), titled [title]. [prompt]: New folder's
     * text while its prompt is open; [replace]: Export's target path while "Replace …?" is open.
     */
    data class FilePicker(
        val mode: FilePickMode, val dir: String?, val title: String, val rows: List<PickerRow>,
        val prompt: String? = null, val replace: String? = null,
        /** The home pick: what the folder it is in would be as a home ([homeFolderStatus]); null elsewhere. */
        val status: String? = null,
    ) : Screen
    data object PickFolder : Screen
    /**
     * An onboarding step ([OnboardingStep]): its page [title] (the step count), [details] (the step's headline, then its
     * lines) and [rows], drawn as a tool page. [prompt]: the RetroAchievements sign-in prompt is up over it, with its drafts.
     * [accent]: the accent the page shows, so a previewed accent makes a new screen (App reads the colours per screen).
     * [canGoBack]: B goes somewhere (anywhere but WELCOME), so its hint shows.
     */
    data class Onboarding(
        val step: OnboardingStep, val title: String, val details: List<String>, val rows: List<NxRow>,
        val prompt: Boolean = false, val draftUser: String = "", val draftPassword: String = "",
        val accent: AccentPreset? = null, val padTheme: PadTheme? = null, val canGoBack: Boolean = true,
        val shot: LayoutShot? = null,   // a layout page's picture of the layout in use
    ) : Screen
    /** First setup only: offer an existing ROMs folder as an extra source. */
    data object AddFirstExtra : Screen
    /**
     * The one-time storage upgrade (installs from v0.3.0 and earlier, without a full-access home; remove after v1.0): its
     * [stage]. [folder]: the media home's path (`Android/media/<package>/`), which DECLINED names. [error]: the move did
     * not finish; the page says so and OK goes on to the library (the screen shows again on the next start).
     */
    data class StorageUpgrade(val stage: Stage, val folder: String = "", val error: String? = null) : Screen {
        enum class Stage { EXPLAIN, PICK, DECLINED }
    }
    /**
     * A home change in Settings with All files access: copy the data (saves, states, settings) of the old home [from]
     * into the new one [to]? Copy or Keep separate; [busy] while the copy runs, [error] when it did not finish.
     */
    data class CopySaves(val from: ContentRef, val to: ContentRef, val busy: Boolean = false, val error: String? = null) : Screen
    /** First setup's last step, after the ROM folders: "Add N Android games to Consoles?" (A Yes, B Not now). */
    data class AndroidGamesStep(val ask: AndroidGamesAsk) : Screen

    /**
     * First setup, after a ROMs folder was added: what its scan found ([summary]), whether to [move] its placed games into
     * the home's Roms (off until chosen), the move's [progress] (done, total) while it runs and its [result] after.
     */
    data class FolderSummary(
        val extraId: String, val name: String, val home: String,
        val summary: com.nxredux.mobile.library.FolderSummary,
        val move: Boolean = false, val progress: Pair<Int, Int>? = null, val result: MoveResult? = null,
    ) : Screen
    data object Loading : Screen
    /**
     * The main menu: [tabs] (see [buildMenuTabs]) on [tab]. [menu]: a game's or a tool's menu is open over its row.
     * [search]: START's search prompt is open with this text as typed. [pushed]: a tab Layouts hides, opened over the
     * menu from MENU ([AppState.openHiddenTab]): [tabs] holds that one tab, titled with its label; B returns. [hidden]: the
     * tabs Layouts hides that have content (MENU's and the long press's items); none while [pushed].
     */
    data class Systems(val tabs: List<MenuTab>, val tab: Int = 0, val menu: ContextMenu? = null, val search: String? = null, val pushed: Boolean = false, val hidden: List<MenuCategory> = emptyList()) : Screen {
        /** The current tab's items: what the selection indexes. */
        val rows: List<MainRow> get() = tabs.getOrNull(tab)?.rows.orEmpty()
        val category: MenuCategory? get() = tabs.getOrNull(tab)?.category
    }
    /**
     * A game list from [source]; each entry carries its system. [menu]: the context menu is open over the list.
     * [search]: the list's own search prompt is up (its draft); [layoutMenu]: the Layout dropdown is open, on that
     * choice of [GameListLayouts.all].
     */
    data class Games(val source: ListSource, val entries: List<Listed>, val menu: ContextMenu? = null, val search: String? = null, val layoutMenu: Int? = null) : Screen
    /**
     * [confirmRemove]: the "remove from recents?" dialog is open over the entry at [index]. [list]: Layouts → Game
     * Switcher layout is List (read on opening): a game list's List, UP/DOWN in place of LEFT/RIGHT.
     */
    data class Switcher(val entries: List<SwitcherEntry>, val index: Int, val confirmRemove: Boolean = false, val list: Boolean = false) : Screen
    /**
     * [confirmRemove]: the "remove this ROM folder?" dialog is open for that extra. [details]: the lines above the rows
     * (Library: games and consoles, last scan; Default emulators: its explanation); empty elsewhere.
     */
    /** [shot]: a Layouts group's picture of its values (null: none, as a hidden tab's). [banner]: a note under the title, above the rows. */
    data class Settings(
        val section: SettingsSection, val rows: List<SettingsRow>, val confirmRemove: ExtraFolder? = null, val details: List<String> = emptyList(),
        val shot: LayoutShot? = null, val banner: String? = null,
        /** Each row's values for its touch control ([SettingChoices]), in [rows]' order; null for a row that holds none. */
        val choices: List<SettingChoices?> = emptyList(),
    ) : Screen
    /** Tools -> Settings: one row per section. */
    data class SettingsMenu(val sections: List<SettingsSection>) : Screen
    /**
     * [pick] TOOLS: the app drawer (Tools → Apps), every launchable app but the Android games console's; A opens one,
     * [menu] adds it to Tools or pins it on Home, [ToolAppRow.shown] is "in Tools". GAMES: Settings -> Library ->
     * Android games, detected games first, then every other app; [ToolAppRow.shown] is "in the Android games console",
     * and A puts one in or takes it out.
     */
    data class ToolApps(val apps: List<ToolAppRow>, val pick: AppPick = AppPick.TOOLS, val menu: ContextMenu? = null) : Screen
    /** [confirmRemove]: the "remove cheat database?" dialog is open. */
    data class CheatDb(val confirmRemove: Boolean = false) : Screen
    /** Tools → Device Sync: no list selection here; its page (and the link choice) live in [AppDeps.deviceSync]. */
    data object DeviceSync : Screen
    /** The RetroAchievements tool page; [confirm]: a destructive action's dialog is open. */
    data class RetroAchievements(val confirm: RaConfirm? = null) : Screen
    /** Tools -> RetroAchievements -> the game data download's progress ([RaController.download]); [confirmCancel]: "Cancel download?" is open. */
    data class RaDownload(val confirmCancel: Boolean = false) : Screen
    /** Tools -> RetroAchievements -> Achievements: the cached games. */
    data class RaGames(val games: List<RaGameView>) : Screen
    /** One game's achievements, in the chosen sort order; [lockedOnly] hides unlocked and pending ones. */
    data class RaGame(val game: RaGameView, val achievements: List<com.nxredux.mobile.achievements.RaAchievement>, val lockedOnly: Boolean = false, val badges: Map<String, String> = emptyMap()) : Screen
    data class RaAchievement(val game: RaGameView, val achievement: com.nxredux.mobile.achievements.RaAchievement, val badges: Map<String, String> = emptyMap()) : Screen
    /**
     * The RetroAchievements settings page; [signingIn]: the sign-in modal is open with [draftUser] / [draftPassword] as typed,
     * [confirm]: a destructive action's dialog.
     */
    data class RaSettings(
        val rows: List<RaSettingsRow>, val signingIn: Boolean = false, val confirm: RaConfirm? = null,
        val draftUser: String = "", val draftPassword: String = "",
    ) : Screen {
        /** The generated form, but the typed password never reaches a log or an assertion message. */
        override fun toString(): String =
            "RaSettings(rows=$rows, signingIn=$signingIn, confirm=$confirm, draftUser=$draftUser, draftPassword=***)"
    }
    /** Settings -> Emulation -> Default settings: one row per core with games. */
    data class EmulatorList(val rows: List<EmulatorListRow>) : Screen
    /** The console picker of a core serving several tags with games. */
    data class EmulatorConsoles(val coreId: String, val emulator: String, val tags: List<String>) : Screen
    /**
     * One tag's emulator settings page, or a game's (Game Settings, [game] set). [category]: the open category (null =
     * top level).
     */
    data class EmulatorOptions(
        val page: EmulatorPage, val rows: List<EmulatorRow>, val category: String? = null, val game: Listed? = null,
    ) : Screen
    /** Tools -> Game Tracker: play time per game group, most played first; [modal]: an options / pick / confirm modal is open. */
    data class GameTime(val rows: List<GameTimeRow>, val grandTotalMs: Long, val modal: GameTimeModal? = null, val loading: Boolean = false) : Screen
    /** Game Tracker -> a game: its group's sessions, newest first; [canMerge]: another game to merge into (Merge shows). */
    data class GameTimeSessions(val game: com.nxredux.mobile.gametime.GameTotal, val sessions: com.nxredux.mobile.gametime.GameSessions, val canMerge: Boolean = false) : Screen
    /** Tools -> Artwork: the counts, account and job lines, then the rows ([ArtworkScreenController]). */
    data class Artwork(val details: List<String>, val rows: List<ArtworkRow>) : Screen
    /** Artwork -> Fetch by console: one row per main-menu console group with a supported game. */
    data class ArtworkConsoles(val rows: List<ArtworkConsole>) : Screen
    /**
     * The art fetch job, one row per game; [confirmCancel]: the "Cancel art fetch?" dialog is open; [starting]: a fetch
     * was asked for and its job is not there yet ([job] empty, never the last job's rows).
     */
    data class ArtworkProgress(
        val job: com.nxredux.mobile.scraper.ScrapeState, val confirmCancel: Boolean = false, val starting: Boolean = false,
    ) : Screen
    /**
     * Artwork -> Settings: Replace existing art, the art sources' accounts and Reset artwork; [signingIn]: the ScreenScraper
     * sign-in modal is open with [draftUser] / [draftPassword] as typed; [editingKey]: the [keyFor] key prompt
     * (SteamGridDB or TheGamesDB) is open with [draftKey]; [confirmLogout]: the "Log out of ScreenScraper?" dialog is open.
     */
    data class ArtworkSettings(
        val rows: List<ArtworkSettingRow>, val signingIn: Boolean = false, val draftUser: String = "", val draftPassword: String = "",
        val editingKey: Boolean = false, val draftKey: String = "", val keyFor: ArtKeyFor = ArtKeyFor.SGDB,
        val confirmLogout: Boolean = false,
    ) : Screen {
        /** A prompt or dialog is open over the rows: it takes every input. */
        val dialogOpen: Boolean get() = signingIn || editingKey || confirmLogout

        /** The typed password and key never reach a log or an assertion message. */
        override fun toString(): String =
            "ArtworkSettings(rows=$rows, signingIn=$signingIn, draftUser=$draftUser, draftPassword=***, editingKey=$editingKey, draftKey=***, keyFor=$keyFor, confirmLogout=$confirmLogout)"
    }
    /** Settings -> Reset artwork: All or a console; [confirm]: the delete dialog for that scope. */
    data class ArtworkReset(val scopes: List<ResetScope>, val confirm: ResetScope? = null) : Screen
}

enum class SettingsKind { HOME, ADD_EXTRA, EXTRA, RESCAN, HIDDEN, UNASSIGNED, EMULATORS, DEFAULTS, DEFAULT_TAG, PAD_THEME, MENU_STYLE, GAME_LIST_STYLE, MENU_ORIENTATION, GAME_LIST_ORIENTATION, GAME_LIST_VERTICAL_ALIGNMENT, CONTROLLER_ART, TAB_SHOWN, SHELF_SHOWN, HINT_SHOWN, LIST_ART, BACKDROP_ART, LAYOUT_OPTIONS, ACHIEVEMENTS_PANEL, PAGE_TITLE, BUTTON_HINTS, EXTRA_INFO, HOME_STATS, SWITCHER_LAYOUT, PAD_VIBRATION, GAME_RUMBLE, MENU_PAD, ACCENT, SECONDARY_ACCENT, INFO, HOME_SCREEN, ANDROID_GAMES, COPY_INFO, PAD_LAYOUT, PAD_LAYOUT_CONSOLE, PAD_LAYOUT_SHAPE, PAD_LAYOUT_RESET, DEBUG_LOG, SECOND_SCREEN_ENABLE, BOTTOM_SCREEN, PAUSE_UNFOCUSED, SAVES, SAVE_FORMAT, STATE_FORMAT, EXTRACTED_NAME, EXPORT_SAVES, IMPORT_SAVES, IMPORT_FOLDER, IMPORT_ZIP, IMPORT_RETROARCH, IMPORT_DONE, RESET_DEFAULTS, ALL_FILES, APP_UPDATE,
    // Controls -> Shortcuts: its opener, an action's press-to-bind row, a touch button's action.
    SHORTCUTS, SHORTCUT_BIND, SHORTCUT_TOUCH,
    LAYOUT_HOME, LAYOUT_CONSOLES, LAYOUT_COLLECTIONS, LAYOUT_TOOLS, LAYOUT_GAME_LISTS, LAYOUT_TITLE_HINTS, LAYOUT_SWITCHER, TEXT_SIZE,
    // A group's label and the space before it (System's General, Launcher, Developer, About, Licenses): never selected.
    SECTION, GAP,
    // System's Licenses group: a paragraph (never selected) and the row that opens the website's full list.
    TEXT, LICENSES_LINK }

/**
 * A Settings page. [parent] null: a section of the Tools -> Settings menu, in this order; else a sub-page of that
 * section, opened from its [opener] row, B going back there. Layouts ([MAIN_MENU]) is a section of its own right after
 * Appearance, as NX Redux's main page category.
 */
enum class SettingsSection(val label: String, val parent: SettingsSection? = null, val opener: SettingsKind? = null) {
    LIBRARY("Library"), EMULATORS("Emulation"), APPEARANCE("Appearance"), MAIN_MENU("Layouts"), CONTROLS("Controls"),
    // Only while a second screen is connected: Enable and Bottom screen.
    SECOND_SCREEN("Second screen"),
    // General, Launcher (with a launcher), Developer and About groups on one page; the last category.
    SYSTEM("System"),
    DEFAULT_EMULATORS("Default emulators", EMULATORS, SettingsKind.DEFAULTS),
    PAD_LAYOUT("Pad layout", CONTROLS, SettingsKind.PAD_LAYOUT), SAVES("Saves", LIBRARY, SettingsKind.SAVES),
    SHORTCUTS("Shortcuts", CONTROLS, SettingsKind.SHORTCUTS),
    IMPORT_SAVES("Import saves", SAVES, SettingsKind.IMPORT_SAVES),
    // An import's results; Done and B go back to Saves ([AppState.leaveSubPage]), past Import saves.
    IMPORT_RESULT("Import results", IMPORT_SAVES),
    // Layouts' groups: a tab's options, the game lists', the page title's and hints', the Game Switcher's.
    LAYOUT_HOME("Home", MAIN_MENU, SettingsKind.LAYOUT_HOME), LAYOUT_CONSOLES("Consoles", MAIN_MENU, SettingsKind.LAYOUT_CONSOLES),
    LAYOUT_COLLECTIONS("Collections", MAIN_MENU, SettingsKind.LAYOUT_COLLECTIONS), LAYOUT_TOOLS("Tools", MAIN_MENU, SettingsKind.LAYOUT_TOOLS),
    LAYOUT_GAME_LISTS("Game lists", MAIN_MENU, SettingsKind.LAYOUT_GAME_LISTS), LAYOUT_TITLE_HINTS("Title and hints", MAIN_MENU, SettingsKind.LAYOUT_TITLE_HINTS),
    LAYOUT_SWITCHER("Game Switcher", MAIN_MENU, SettingsKind.LAYOUT_SWITCHER);

    /** A Layouts group: its value rows above a picture of them ([Screen.Settings.shot]); B goes back. */
    val layoutGroup: Boolean get() = parent == MAIN_MENU

    /** A Layouts group's tab (Home, Consoles, Collections, Tools); null for the others. */
    val tab: MenuCategory? get() = when (this) {
        LAYOUT_HOME -> MenuCategory.HOME
        LAYOUT_CONSOLES -> MenuCategory.CONSOLES
        LAYOUT_COLLECTIONS -> MenuCategory.COLLECTIONS
        LAYOUT_TOOLS -> MenuCategory.TOOLS
        else -> null
    }

    /** Library or one of its sub-pages: the pages whose rows follow the index. */
    val library: Boolean get() = this == LIBRARY || parent == LIBRARY

    /** Emulators or one of its sub-pages (Default emulators): a changed default tag leaves the index stale. */
    val emulators: Boolean get() = this == EMULATORS || parent == EMULATORS

    /** Leaving it may reload the library (the index stale): Library, Emulators and their sub-pages. */
    val reloads: Boolean get() = library || emulators

    /** Drawn with the detail lines above its rows: Library (its counts and last scan) and Default emulators; not Saves (its banner). */
    val detailed: Boolean get() = this == LIBRARY || this == DEFAULT_EMULATORS

    /**
     * Every row opens a page or runs an action (a count or state as its secondary), none holds a value: drawn as list
     * rows at the list size, as the RetroAchievements page: Emulators. A page with a value row
     * (a toggle or a cycled setting, About's read-only values) keeps the options rows for all its rows, the navigation
     * ones too (System's Use as home screen), so one page never mixes the two sizes.
     */
    val navigation: Boolean get() = this == EMULATORS
}

/** The last row of Appearance, Layouts and Controls (NX Redux's label; its description has no place on a Settings page here). */
internal const val RESET_DEFAULTS_LABEL = "Reset to defaults"

/** A Shortcuts bind row's value while it waits for a press. */
internal const val MIC_ROW_NOTE = "DS / 3DS only"
internal const val SHORTCUT_PROMPT = "Press a button…"

/** How long a notice stays before it clears itself. */
private const val NOTICE_MILLIS = 3_000L

/**
 * How long the installed library's Android games dialog ignores its answers after it shows: it can appear over a menu
 * already in use, so a press or tap meant for the menu must not answer it.
 */
internal const val ANDROID_ASK_GUARD_MILLIS = 300L

/**
 * A Minimal page's open: the other names fade out over [MINIMAL_LEAVE_MS] (a folder's list opens then), and a launch's
 * band grows over the screen as it goes black, the row opening this long after A; the band clears this long after.
 */
internal const val MINIMAL_LEAVE_MS = 120L
internal const val MINIMAL_OPEN_MS = MINIMAL_LEAVE_MS + 350L
private const val MINIMAL_CLEAR_MS = 1_000L
/** A game's band stays black until the app is back in front ([AppState.onForeground]), or this long if it never left. */
private const val MINIMAL_GAME_CLEAR_MS = 10_000L

/**
 * Art results are written and shown once they stop coming for [ART_QUIET_MS], or every [ART_MAX_WAIT_MS] while they keep
 * coming (a long fetch still saves and shows its art as it goes); a fetch's or Reset's end writes them at once.
 */
private const val ART_QUIET_MS = 1_500L
private const val ART_MAX_WAIT_MS = 15_000L

/**
 * A Minimal page's row being opened ([AppState.minimalOpening]): the page it is on ([page]: a tab's [MenuCategory], a game
 * list's [ListSource]; only that page draws it) and the row's [index]. [band]: a launch, its band growing over the screen;
 * false for a folder whose list is Minimal too, which only fades the other names before it morphs in.
 */
data class MinimalOpening(val page: Any, val index: Int, val band: Boolean = true)

/** The deepest a Settings right pane goes below its category (Library -> Emulator Settings -> consoles -> options), and some. */
private const val SETTINGS_MAX_DEPTH = 6

/** The first sentence of every "home on an unmounted card" error. */
private const val HOME_OFFLINE = "The home folder's storage is not available."

/** A file home read without All files access (revoked): the home, its index and its extras are kept. */
private const val ACCESS_OFF = FileRefs.ACCESS_OFF_MESSAGE

/** A scan or the start probe met a listing the provider would not give ([ListingUnavailable]): the index is kept. */
private const val LISTING_HIDDEN = "Storage did not list a folder just now; the library is unchanged. Use Rescan in Settings → Library in a moment."
private const val LISTING_HIDDEN_NO_INDEX = "Storage did not list the library folders just now. Reopen the app in a moment."

/** "1 game" / "<n> games" for the Library page and the rescan notice. */
internal fun gamesLabel(n: Int): String = if (n == 1) "1 game" else "$n games"

/** The layouts a game list steps through on onboarding's Game lists page (a tab's: [MenuStyles.cycleMainMenu]). */
private val ONBOARDING_GAME_LIST_STYLES = MenuStyles.GAME_LIST_STYLES
/** Onboarding's layout pages: a layout row above one picture of it. */
internal val ONBOARDING_LAYOUT_STEPS = setOf(
    OnboardingStep.TAB_HOME, OnboardingStep.TAB_CONSOLES, OnboardingStep.TAB_COLLECTIONS, OnboardingStep.TAB_TOOLS, OnboardingStep.GAME_LISTS,
)
/** Onboarding's tab layout steps and the tab each sets. */
internal val ONBOARDING_TAB_STEPS = mapOf(
    OnboardingStep.TAB_HOME to MenuCategory.HOME, OnboardingStep.TAB_CONSOLES to MenuCategory.CONSOLES,
    OnboardingStep.TAB_COLLECTIONS to MenuCategory.COLLECTIONS, OnboardingStep.TAB_TOOLS to MenuCategory.TOOLS,
)
/** Onboarding's value pages and the Settings value each row steps, top to bottom (Continue below them). */
private val ONBOARDING_VALUE_KINDS = mapOf(
    OnboardingStep.TEXT_SIZE to listOf(SettingsKind.TEXT_SIZE),
    OnboardingStep.SAVE_FORMATS to listOf(SettingsKind.SAVE_FORMAT, SettingsKind.STATE_FORMAT, SettingsKind.EXTRACTED_NAME),
    OnboardingStep.PAD to listOf(SettingsKind.MENU_PAD, SettingsKind.PAD_THEME),
    OnboardingStep.TITLE to listOf(SettingsKind.PAGE_TITLE, SettingsKind.BUTTON_HINTS),
    OnboardingStep.ACCENT to listOf(SettingsKind.ACCENT, SettingsKind.SECONDARY_ACCENT),
)

/** "None" / "1 game" / "<n> games": Library's "Android games" count. */
internal fun gamesCountLabel(n: Int): String = if (n == 0) "None" else gamesLabel(n)

/** The Android games console's tag: no folder, core or registry console has it. */
const val ANDROID_TAG = com.nxredux.mobile.scraper.ANDROID_CONSOLE_TAG

/** Which apps grid this is: the app drawer (its marks: the apps in Tools), or the Android games console's picker. */
enum class AppPick { TOOLS, GAMES }

/**
 * The Android games console's apps: the [installed] apps whose ids are in [ids] (the user's list, [AppSettings.androidConsoleApps];
 * one not installed is skipped), by label, case-insensitive. Detection never adds one.
 */
internal fun androidConsoleMembers(installed: List<InstalledApp>, ids: List<String>): List<InstalledApp> {
    val members = ids.toSet()
    return installed.filter { it.id in members }.sortedBy { it.label.lowercase() }
}

/** The games picker's order: the detected games, then every other app, each group by label, case-insensitive. */
internal fun androidGamesPickOrder(installed: List<InstalledApp>): List<InstalledApp> {
    val (games, rest) = installed.sortedBy { it.label.lowercase() }.partition { it.game }
    return games + rest
}

/**
 * The "Add N Android games to Consoles?" question (first setup's step, or a dialog over the main menu): the detected
 * games' [ids] and their [labels], listed under it. Yes adds them all; either answer means it is never asked again.
 * At most [MAX_LINES] lines list them (the last one "and N more" when they do not all fit), so neither page overflows.
 */
data class AndroidGamesAsk(val ids: List<String>, val labels: List<String>) {
    companion object { const val MAX_LINES = 6 }

    val title: String get() = "Android games"
    val question: String get() = "Add ${ids.size} Android game${if (ids.size == 1) "" else "s"} to Consoles?"
    /** The labels, at most [max] lines: past it the last line says how many more. */
    fun shown(max: Int = MAX_LINES): List<String> = if (labels.size <= max) labels else labels.take(max - 1) + "and ${labels.size - (max - 1)} more"
    val text: String get() = "$question\n\n" + shown().joinToString("\n")
}

/** The question for [installed]'s detected games, by label; null when none is detected (nothing is asked). */
internal fun androidGamesAskFor(installed: List<InstalledApp>): AndroidGamesAsk? {
    val games = installed.filter { it.game }.sortedBy { it.label.lowercase() }.ifEmpty { return null }
    return AndroidGamesAsk(games.map { it.id }, games.map { it.label })
}

/** The Android games console's game entries' source id: no ROM tree has it (their paths are never written anywhere). */
internal const val ANDROID_SOURCE = "android"

/**
 * The Android games console for [apps] (the console's members, [androidConsoleMembers]): a synthetic [SystemFolder] with no core,
 * each app a [GameEntry] whose file name is the app id and whose shown name is its label, its screenshot from [art]; no
 * box art. Null with no app: the console is hidden.
 */
internal fun androidConsoleOf(apps: List<InstalledApp>, art: (String) -> ContentRef?): SystemFolder? {
    if (apps.isEmpty()) return null
    val games = apps.map { a ->
        val shot = art(a.id)
        GameEntry(
            ref = ContentRef("app:${a.id}"), fileName = a.id, shownName = a.label, screenshotRef = shot,
            folder = ContentRef("app:"), sourceId = ANDROID_SOURCE, relativeDir = "",
        )
    }
    return SystemFolder("Android", ANDROID_TAG, null, games, android = true)
}

/** The Consoles rows with [console] (the Android one) placed among them by display name, ignoring case; [rows] as they are without it. */
internal fun withAndroidConsole(rows: List<MainRow.System>, console: SystemFolder?): List<MainRow.System> {
    if (console == null || console.games.isEmpty()) return rows
    val name = console.displayName.lowercase()
    val at = rows.indexOfFirst { it.system.displayName.lowercase() > name }.takeIf { it >= 0 } ?: rows.size
    return rows.toMutableList().apply { add(at, MainRow.System(console)) }
}

/** A main menu tab's or the game list's style, as the Appearance rows show it. */
internal fun menuStyleLabel(style: MenuStyle): String = when (style) {
    MenuStyle.LIST -> "List"
    MenuStyle.GRID -> "Grid"
    MenuStyle.CAROUSEL -> "Carousel"
    MenuStyle.BACKDROP -> "Backdrop"
    MenuStyle.MINIMAL -> "Minimal"
}

/** A Carousel's or a Backdrop's orientation, as its Orientation row shows it. */
internal fun orientationLabel(o: MenuOrientation): String = when (o) {
    MenuOrientation.HORIZONTAL -> "Horizontal"
    MenuOrientation.VERTICAL -> "Vertical"
}

internal fun verticalAlignmentLabel(a: VerticalListAlignment): String = when (a) {
    VerticalListAlignment.LEFT -> "Left"
    VerticalListAlignment.RIGHT -> "Right"
}

/** [enabled] false: dimmed and A does nothing (a default emulator of a one-tag console). [category]: a MENU_STYLE row's tab. */
/**
 * A touch value row's picker ([AppState.chooser], the in-game menu's look): the row's [title], every value's [labels],
 * the d-pad cursor [selected] and the value set [current] (-1: none).
 */
data class ValueChooser(val title: String, val labels: List<String>, val selected: Int, val current: Int = -1)

/** Use as home screen's values: it is not a [SettingValue] (Android answers it). */
private val ON_OFF = listOf("Off", "On")

/** A Settings value row's values as its touch control shows them: every value's [labels] in LEFT / RIGHT order, the current [index]. */
data class SettingChoices(val labels: List<String>, val index: Int)

/** A Settings row's full description over the page ([AppState.settingInfo]); [scrollSeq] / [scrollDir] count UP/DOWN steps. */
data class SettingInfoPopup(val title: String, val text: String, val scrollSeq: Int = 0, val scrollDir: Int = 0)

/**
 * [warning]: its secondary is a problem (a folder not available), drawn in the warning colour. [shortcut] / [touchSlot]:
 * the action or touch button of a Shortcuts row; [note] on a bind row: it is waiting for a press, and what to do.
 */
data class SettingsRow(val kind: SettingsKind, val label: String, val secondary: String? = null, val extra: ExtraFolder? = null, val consoleId: String? = null, val enabled: Boolean = true, val category: MenuCategory? = null, val shelf: HomeShelf? = null, val hint: NxButton? = null, val padShape: PadShape? = null, val warning: Boolean = false, val note: String? = null, val shortcut: ShortcutAction? = null, val touchSlot: TouchSlot? = null)

/** A row the selection lands on: not a group label, the space before one, or a paragraph. */
val SettingsRow.selectable: Boolean get() = kind != SettingsKind.SECTION && kind != SettingsKind.GAP && kind != SettingsKind.TEXT

/**
 * Settings -> Emulation -> Default emulators: every console with several tags (cycled, so one can be set before it has
 * games) and every other console [systems] hold games for (Unassigned left out), as a dimmed read-only row. The value
 * names the emulator behind the tag, "mGBA (MGBA)", so two tags on one core would still differ by tag. A stored pick
 * that is not one of the console's tags is ignored. Sorted by console name.
 */
internal fun defaultEmulatorRows(systems: List<SystemFolder>, defaultTags: Map<String, String>): List<SettingsRow> {
    val withGames = systems.filter { !it.unassigned && it.gameCount > 0 }.mapNotNullTo(HashSet()) { CoreRegistry.consoleOf(it.tag)?.id }
    // The consoles with a choice first (more than one emulator), then the single-emulator ones, each A to Z.
    return CoreRegistry.consoles.filter { it.tags.size > 1 || it.id in withGames }
        .sortedWith(compareBy({ it.tags.size <= 1 }, { it.displayName.lowercase() })).map { c ->
        val tag = c.defaultTagFor(defaultTags[c.id])
        SettingsRow(SettingsKind.DEFAULT_TAG, c.displayName, CoreRegistry.emulatorLabel(tag), consoleId = c.id, enabled = c.tags.size > 1)
    }
}

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

class AppState(private val deps: AppDeps, private val scope: CoroutineScope) {
    private val sidecars = Sidecars(deps.source, deps.sidecarStore) { home() }
    private val scanner = LibraryScanner(deps.source, sidecars, deps.arcadeSets, deps.naomiSets, compute = deps.compute, chdSectors = deps.chdSectors)
    /** Controls -> Shortcuts as edited since it opened; null: closed (or untouched), [AppSettings.shortcuts] is all there is. */
    private var shortcutDraft: ShortcutSet? = null

    /** Press to bind's raw keys ([captureKey]); Controls -> Shortcuts' bind rows start it. */
    private val capture = ShortcutCapture()

    private val _capturingShortcut = MutableStateFlow<ShortcutAction?>(null)
    /** The action a Shortcuts bind row is waiting on a press for; while set the platform hands raw keys to [captureKey] first. */
    val capturingShortcut: StateFlow<ShortcutAction?> = _capturingShortcut.asStateFlow()

    /**
     * Every screen write, here or in a controller sharing it: one that leaves the Shortcuts page, by B, another page,
     * Home or anything else, closes it ([closeShortcuts]), so no way out keeps a wait swallowing the menu's keys or
     * drops the draft. Declared after the Shortcuts state it reads.
     */
    private val _screen: MutableStateFlow<Screen> = WatchedStateFlow(MutableStateFlow(Screen.Loading)) { s ->
        if ((s as? Screen.Settings)?.section != SettingsSection.SHORTCUTS && (shortcutDraft != null || _capturingShortcut.value != null)) closeShortcuts()
    }
    val screen: StateFlow<Screen> = _screen.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
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
    /** The last search confirmed this run (START's prompt opens on it); not saved. */
    internal var lastSearch: String = ""
        private set
    private val collections = Collections(deps.source, deps.sidecarStore) { home() }
    private var allMenuTabs: List<MenuTab> = emptyList()   // every tab with content as last built, hidden ones too (no I/O to show it again)
    /** The tabs the main menu shows: [allMenuTabs] less the ones Layouts hides ([visibleMenuTabs]). */
    private val menuTabs: List<MenuTab> get() = visibleMenuTabs(allMenuTabs, shownTabs())
    private var pins: List<Shortcut> = emptyList()           // shortcuts.txt as last read
    private var hiddenPaths: List<String> = emptyList()      // hidden.txt as last read
    /** The pinned tools, in pin order: [ToolPins.FILE] in [AppDeps.privateFiles] as last read or written (never shortcuts.txt). */
    var toolPins: List<String> = emptyList()
        private set
    private var collectionNames: List<String> = emptyList() // Collections/ as last listed against the tree
    private val collectionCounts = HashMap<String, Int?>()   // each collection's game count, by name; null: its file would not read
    private var countsGeneration = 0                          // bumped when counts go stale, so a pass begun before it is dropped
    private var countsJob: Job? = null
    private val countCache = CollectionCountCache(deps.privateFiles, scope, deps.compute)   // the counts between runs, app-private
    private var libraryStampOf: Pair<List<SystemFolder>, String>? = null   // [CountStamps.library] of the systems it was taken for
    @Volatile private var playableOf: Pair<List<SystemFolder>, Map<GameRef, Listed>>? = null   // [playableLookup] of the systems it was built for
    @Volatile private var hiddenLookupOf: Pair<LibraryIndex, Map<GameRef, Listed>>? = null     // [gameLookup] of the index it was built for (Hidden)
    private val rowsLock = Mutex()                            // one main-row rebuild at a time: an older one never writes last
    private var menuCategory: MenuCategory = MenuCategory.HOME   // the tab last shown (the menu opens on Home); a gone one resolves to the next visible tab
    private var pushedTab: MenuCategory? = null   // a hidden tab opened over the menu ([openHiddenTab]); [menuCategory] stays the tab under it
    private val tabSelections = HashMap<MenuCategory, ListSelection>()   // each tab's row, remembered across visits
    private var gridColumns = 0   // the Grid body's column count for the screen showing now; 0 until it lays out
    private var gridRows = 2      // and its row count: 2 on phones, 3–4 on a tablet-class window
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
        combine(u.popup, _androidGamesAsk, _error, _screen) { release, ask, error, screen -> release?.takeIf { updatePopupFits(ask, error, screen) } }
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
    private var allFilesStepShown = false   // this run showed the All files step: it counts in the step numbers
    private var androidStepShown = false   // likewise the Android games step
    private var addedExtraId: String? = null   // the ROMs folder first setup added: its summary step counts in the step numbers
    private var homePage: Screen.FolderSummary? = null   // first setup's home page, when the home held games: B on the next step returns to it
    private var homeOutside: List<com.nxredux.mobile.library.LooseFile> = emptyList()   // that home's games outside Roms
    @Volatile private var moveCancelled = false   // B while a summary's move runs: it stops after the current game
    private var welcomeDone = false   // the welcome was answered this run: a load without a home goes to the folder steps
    private var mediaHomeTaken = false   // first setup went on with the media home (no All files access): no pick to wait for
    private var onboardingSync = false   // Device Sync was opened from the sync offer: leaving it returns there
    private var onboardingSigningIn = false   // onboarding's RetroAchievements sign-in is running: its end moves the step on
    private var onboardingSynced: String? = null   // the sync offer's last result ("done", "failed", "stopped"; "" left before one ended)
    private var onboardingImporting: String? = null   // the save import step's running import: its progress text
    private var onboardingImported: ImportResult? = null   // the save import step's last result, cleared when the step is left
    private var firstSetup = false   // a first home pick is on its folder steps: the menu's dialog waits (the setup asks as a step)
    private var afterAndroidStep: (() -> Unit)? = null   // what first setup's Android games step goes on to (a load or the extra's scan)
    /** Android apps' icons for the session (Tools tiles, Home pins, the picker); null without a launcher. */
    val appIcons: AppIcons? = deps.launcher?.let { AppIcons(it, scope) }
    private val _gridPresses = MutableStateFlow(0)
    /** Counts the d-pad presses a laid-out Grid takes (a move or not): the Grid brings a swipe-scrolled selection back on one. */
    val gridPresses: StateFlow<Int> = _gridPresses.asStateFlow()

    private val _selection = MutableStateFlow(ListSelection(0, 0))
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
            override fun rebuildHomeTab() = this@AppState.rebuildHomeTab()
            override fun refreshResume() = this@AppState.refreshResume()
            override fun toTabRow(repeat: Boolean) = this@AppState.toTabRow(repeat)
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
            override fun rememberSettingsRow() { (_screen.value as? Screen.Settings)?.let { sectionSelections[it.section] = _selection.value } }
            override fun back(): Boolean = this@AppState.back()
            override fun openSelected() = this@AppState.openSelected()
            override fun openContextMenu() = this@AppState.openContextMenu()
            override fun drawerRows(apps: List<InstalledApp>): List<ToolAppRow> = this@AppState.drawerRows(apps)
            override fun toolOnTop() { _toolOnTop.value = true }
            override suspend fun rebuildMainRows() = this@AppState.rebuildMainRows()
            override fun swapMainRows() = this@AppState.swapMainRows()
            override suspend fun moveToolPinsToAndroid(ids: List<String>) = this@AppState.moveToolPinsToAndroid(ids)
            override suspend fun applyAndroidConsole() = this@AppState.applyAndroidConsole()
        },
    )

    private val _dualMenu = MutableStateFlow(false)
    /** The main menu is on the second display ([onDualMenu]): the preview and the game list's details live there. */
    val dualMenu: StateFlow<Boolean> = _dualMenu.asStateFlow()

    private val _secondList = MutableStateFlow<SecondList?>(null)
    /** While [dualMenu]: the highlighted console's or collection's games (the list A would open), for the second display; else null. */
    val secondList: StateFlow<SecondList?> = _secondList.asStateFlow()
    private var secondListJob: Job? = null

    private val _drawerPreview = MutableStateFlow<List<ToolAppRow>?>(null)
    /** While [dualMenu] with the Apps tile highlighted: the drawer's apps (what A would open), for the second display; else null. */
    val drawerPreview: StateFlow<List<ToolAppRow>?> = _drawerPreview.asStateFlow()
    private var drawerPreviewJob: Job? = null

    private val _toolOnTop = MutableStateFlow(false)
    /**
     * A tool opened from the main menu ([openTool]: Tools or a Home pin) has the focus, through all its pages, until the
     * menu shows again ([showMenu]). While [dualMenu] it is drawn on the top screen, the menu staying below ([toolUnder]).
     */
    val toolOnTop: StateFlow<Boolean> = _toolOnTop.asStateFlow()
    private var toolUnderRow: MainRow? = null   // the menu row the tool was opened from, kept highlighted below it
    private var toolUnderIndex = 0

    /** The platform tells the menu is on the second display ([active]) or back on the main one. */
    fun onDualMenu(active: Boolean) {
        if (_dualMenu.value == active) return
        _dualMenu.value = active
        if (active) ra.closeRaPanel()   // the panel is a single-screen overlay: dual mode shows the details pane instead
        followSecondList()
        followDrawerPreview()
        ra.followRaDetails()
    }

    /** What the shown [secondList] was built from: the source, the library snapshot, and for a collection its row (name and count). */
    private data class SecondKey(val source: ListSource, val library: List<SystemFolder>, val row: MainRow?)
    private var secondKey: SecondKey? = null
    /** How many times the preview was built (test hook): a same-source, same-content republish must not add one. */
    internal var secondListBuilds = 0
        private set

    /**
     * Recomputes [secondList] from the screen and the highlighted row: a console's list at once (no I/O), a collection's
     * after its file is read. A newer call cancels an older read. The same source over the same library snapshot (and,
     * for a collection, the same row) keeps the shown list untouched: a rescan or a collection edit changes one of those.
     */
    private fun followSecondList() {
        val source = highlightedSecondSource()
        val row = if (source is ListSource.Collection) (_screen.value as? Screen.Systems)?.rows?.getOrNull(_selection.value.index) else null
        val key = source?.let { SecondKey(it, systemsCache, row) }
        if (key != null && _secondList.value?.source == source) {
            val k = secondKey
            if (k != null && k.source == key.source && k.library === key.library && k.row == key.row) return
        }
        secondListJob?.cancel()
        secondKey = null
        if (source == null) { _secondList.value = null; return }
        if (source is ListSource.Console) {
            secondListBuilds++
            _secondList.value = SecondList(source, consoleEntries(source.systems), 0)
            secondKey = key
            return
        }
        if (_secondList.value?.source != source) _secondList.value = null
        secondListJob = scope.launch {
            val entries = entriesFor(source)
            ensureActive()
            if (highlightedSecondSource() == source) { secondListBuilds++; _secondList.value = SecondList(source, entries, 0); secondKey = key }
        }
    }

    /**
     * Recomputes [drawerPreview]: the installed apps, read once when the Apps tile is highlighted on the second display,
     * kept while it stays so; dropped (a read under way cancelled) when it is not.
     */
    private fun followDrawerPreview() {
        val s = _screen.value as? Screen.Systems
        val l = deps.launcher
        val on = l != null && _dualMenu.value && s != null && s.menu == null && s.search == null && isAppsTile(s.rows.getOrNull(_selection.value.index))
        if (!on) { drawerPreviewJob?.cancel(); drawerPreviewJob = null; _drawerPreview.value = null; return }
        if (_drawerPreview.value != null || drawerPreviewJob?.isActive == true) return
        drawerPreviewJob = scope.launch {
            val apps = l!!.installedApps()
            installedCache = apps
            _drawerPreview.value = drawerRows(apps)
        }
    }

    /** A tap on app [index] of the second display's drawer preview: opens the drawer there on it, as A would (B returns). */
    fun openDrawerPreviewApp(index: Int) {
        val s = _screen.value as? Screen.Systems ?: return
        val app = _drawerPreview.value?.getOrNull(index) ?: return
        if (loader.active || s.menu != null || s.search != null || !isAppsTile(s.rows.getOrNull(_selection.value.index))) return
        intendNav(NavMotion.FORWARD)
        rememberMenuSelection()
        noteToolOrigin()
        openToolApps(AppPick.TOOLS, selectId = app.id)
    }

    /**
     * While a tool is on the second display ([toolOnTop]): the main menu it was opened from, on the row it was opened
     * from (found again when the rows moved), drawn on the bottom screen as it stays; null otherwise. A game list
     * reached from a tool takes both screens as any game list does.
     */
    fun toolUnder(): Pair<Screen.Systems, ListSelection>? {
        val s = _screen.value
        if (!_dualMenu.value || !_toolOnTop.value || s is Screen.Systems || s is Screen.Games) return null
        val menu = menuScreen()
        val at = toolUnderRow?.let { menu.rows.indexOf(it) }?.takeIf { it >= 0 } ?: toolUnderIndex
        return menu to ListSelection(at, menu.rows.size).clampedTo(menu.rows.size)
    }

    /** The highlighted menu row is the one a tool opens from: kept for [toolUnder]. */
    private fun noteToolOrigin() {
        val s = _screen.value as? Screen.Systems ?: return
        toolUnderIndex = _selection.value.index
        toolUnderRow = s.rows.getOrNull(toolUnderIndex)
    }

    /** The drawer's apps: every installed app but the Android games console's, marked when it is in Tools. */
    private fun drawerRows(apps: List<InstalledApp>): List<ToolAppRow> {
        val console = deps.settings.androidConsoleApps.toSet()
        val inTools = deps.settings.toolApps.toSet()
        return apps.filter { it.id !in console }.map { ToolAppRow(it.id, it.label, it.id in inTools) }
    }

    /** The Apps tile, in Tools or pinned on Home. */
    private fun isAppsTile(row: MainRow?): Boolean =
        (row is MainRow.Tool && row.row.kind == ToolKind.APPS) || (row is MainRow.PinnedTool && row.row.kind == ToolKind.APPS)

    /** The list the highlighted row previews while [dualMenu] on Consoles / Collections; null for any other row, tab or screen. */
    private fun highlightedSecondSource(): ListSource? {
        val s = _screen.value as? Screen.Systems ?: return null
        if (!_dualMenu.value || (s.category != MenuCategory.CONSOLES && s.category != MenuCategory.COLLECTIONS)) return null
        return when (val row = s.rows.getOrNull(_selection.value.index)) {
            is MainRow.System -> ListSource.Console(consoleGroup(row.system) ?: listOf(row.system))
            is MainRow.Collection -> ListSource.Collection(row.name)
            else -> null
        }
    }

    /** A tap on game [index] of the second display's preview: opens that list as A would (B returns to the same row) on it. */
    fun openSecondListGame(index: Int) {
        val list = _secondList.value ?: return
        val s = _screen.value as? Screen.Systems ?: return
        if (loader.active || minimalPending || highlightedSecondSource() != list.source) return   // a stale preview: ignore the tap
        when (val row = s.rows.getOrNull(_selection.value.index)) {
            is MainRow.System -> { intendNav(NavMotion.FORWARD); openSystem(row.system, select = list.entries.getOrNull(index)?.game?.path) }
            else -> { intendNav(NavMotion.FORWARD); rememberSelection(); showList(list.source, list.entries, index) }
        }
    }

    /**
     * A tap on main menu row [index]: selects it. A console or collection already opens on one tap in every style but
     * Minimal (the UI's [com.nxredux.mobile.ui.nx.nxTap] calls [openSelected] after this), and Minimal's names take two;
     * so on the second display's Consoles and Collections a Minimal console or collection opens here at once, highlighted
     * or not (as A, so focus moves to the preview list). Opening here in any other style would open twice (the list,
     * then its first game).
     */
    fun tapMainRow(index: Int) {
        val s = _screen.value as? Screen.Systems
        val opens = _dualMenu.value && s != null && (s.category == MenuCategory.CONSOLES || s.category == MenuCategory.COLLECTIONS) &&
            styleOf(s) == MenuStyle.MINIMAL && s.rows.getOrNull(index).let { it is MainRow.System || it is MainRow.Collection }
        select(index)
        if (opens) openSelected()
    }

    private val _tabFocus = MutableStateFlow(false)
    /**
     * The main menu's tab row has the d-pad (UP from the top of a Grid, a horizontal row or Home, or on an empty tab; a
     * List or a stack wraps instead): LEFT / RIGHT switch tabs there, DOWN,
     * A and B go back to the content's [selection] (kept as it was), UP to the content's bottom ([toContentBottom]). Its
     * own flag, not part of [Screen.Systems]: the menu is rebuilt in place often ([swapMainRows]) and keeps it; leaving
     * the menu, a touch on its content, the switcher and Home clear it, and so does the row being hidden
     * ([dropHiddenTabFocus]): never true while it is.
     */
    val tabFocus: StateFlow<Boolean> = _tabFocus.asStateFlow()

    private val _minimalOpening = MutableStateFlow<MinimalOpening?>(null)
    /**
     * A Minimal page's row being opened, while its band grows over the screen and a moment after ([openMinimal]); null
     * otherwise. Input waits until the row opens ([minimalPending]).
     */
    val minimalOpening: StateFlow<MinimalOpening?> = _minimalOpening.asStateFlow()
    /** A Minimal open's band is growing: the row opens in a moment, and input waits till then. */
    private var minimalPending = false
    private var minimalJob: Job? = null

    /** The screen's footer mode as App last reported it ([onFooterMode]); HINTS until then, as [LocalFooterMode]'s default. */
    private var footerMode = FooterMode.HINTS

    /**
     * The UI reports the footer mode ([footerModeFor]: a controller or the portrait pad, else touch only), which decides
     * whether Layouts → Page title's Hide is in effect ([launcherChromeFor]). A change that hides the tab row (a
     * controller connected, a rotation to the pad) takes the d-pad back from it.
     */
    fun onFooterMode(mode: FooterMode) {
        footerMode = mode
        dropHiddenTabFocus()
    }

    /** The launcher screens' chrome in effect: the settings under [footerMode], the rule App draws by. */
    internal fun launcherChrome(): LauncherChrome =
        deps.settings.let { s ->
            launcherChromeFor(s.menuPageTitle, s.menuButtonHints, footerMode, s.menuExtraInfo, s.showRecentHint, s.showSearchHint, s.showNetplayHint, homeStats = s.menuHomeStats)
        }

    /** The tab row is hidden (Layouts → Page title, in effect): it never holds the d-pad, so the content takes it back. */
    private fun dropHiddenTabFocus() { if (!launcherChrome().tabRow) _tabFocus.value = false }

    private val _nav = MutableStateFlow(NavStep())
    val nav: StateFlow<NavStep> = _nav.asStateFlow()
    private var pendingNav: Pair<NavMotion, Screen>? = null   // a user's A/B into or out of a list, and the screen it was made on

    private val _resumeInfo = MutableStateFlow(ResumeInfo.NONE)
    val resumeInfo: StateFlow<ResumeInfo> = _resumeInfo.asStateFlow()
    private val _gameInfo = MutableStateFlow<Map<String, GameInfo>>(emptyMap())
    /** Every game's play time and RetroAchievements progress by library path (menus show the selected one's). */
    val gameInfo: StateFlow<Map<String, GameInfo>> = _gameInfo.asStateFlow()
    private var gameInfoJob: Job? = null
    /** The RetroAchievements panel, details pane and screens; it reaches back into this state only through [RaScreenHost]. */
    private val ra = RaScreenController(
        scope, _screen, _selection, _error, deps.settings, loader, deps.achievements, _dualMenu,
        host = object : RaScreenHost {
            override fun showNotice(text: String) = this@AppState.showNotice(text)
            override fun showTools() = this@AppState.showTools()
            override fun openSelected() = this@AppState.openSelected()
            override fun refreshResume() = this@AppState.refreshResume()
            override fun refreshGameInfo() = this@AppState.refreshGameInfo()
            override fun raSignedIn(): Boolean = this@AppState.raSignedIn()
            override fun openPicker(title: String, labels: List<String>, current: Int, apply: (Int) -> Unit) =
                this@AppState.openPicker(title, labels, current, apply)
        },
    )
    /** The achievements panel over a game list; null while closed. */
    val raPanel: StateFlow<RaPanel?> = ra.raPanel
    /** The second display's details pane's achievements ([RaScreenController.gameDetailRa]); null unless [dualMenu] on a game list. */
    val gameDetailRa: StateFlow<RaPanel?> = ra.gameDetailRa
    private var gameInfoDirty = false   // a refresh was asked for while one ran: it runs once more
    private val shelvesCache = HomeShelvesCache()   // Home's shelves while their inputs hold ([shelvesFor])
    private var gameInfoRead = false    // a [refreshGameInfo] pass has read play time: the shelves can go by play time
    private var lastTimes: Map<String, GameTotal> = emptyMap()   // the last play time read that worked ([refreshGameInfo])
    private var lastRa: Map<String, RaProgress> = emptyMap()      // likewise RetroAchievements progress
    private var beforeSwitcher: Pair<Screen, ListSelection>? = null
    private var switcherJob: Job? = null   // switcher entries loading
    private var switcherRemoval: Job? = null   // the switcher's last Y Remove write (chained after the one before)
    private var switcherReturn = false   // a game resumed from the switcher is running: the menu's next foreground reopens it
    private var resumeJob: Job? = null     // highlighted game's resume-marker read
    private var launchJob: Job? = null     // pre-launch checks + runner launch in flight
    private var hideJob: Job? = null       // A-in-the-hidden-list unhide in flight
    private val recents = deps.recents ?: RecentsRecorder(deps.userData, scope)
    private val recentsLock get() = recents.lock   // serialises recent.txt read-modify-writes, process-wide
    private var skeletonFor: ContentRef? = null   // the home the skeleton was ensured for (once per home per instance)
    private var offlineHomeSaid: ContentRef? = null   // the home last found offline, its error already shown (cleared once it reads again)
    private var homeLateMountUsed = false             // that home already had its one "mounted, root not back yet" pass
    private var foregroundJob: Job? = null            // onForeground's check (and the scan it starts)
    private var homeCheck: Job? = null                // the index start's home probe in flight (shared, see afterIndexStart)
    private var homeCheckFor: ContentRef? = null
    private val sectionSelections = HashMap<SettingsSection, ListSelection>()   // each section's row, remembered across visits
    private var settingsMenuSelection = ListSelection(0, 0)
    private var settingsSplit = false   // the UI draws Settings as two panes ([onSettingsSplit])
    /** The library changed since the last [load] (folders added or removed, games hidden, unhidden or reassigned): leaving Library reloads. */
    private var libraryDirty = false

    /** The app's settings, exposed for the UI (deps stays private). */
    val settings: AppSettings get() = deps.settings

    /** The Cheat Database tool, for the screen that renders its progress (null on a platform without it). */
    val cheatDb: CheatDbController? get() = deps.cheatDb

    /** The Device Sync tool, for its screen (null on a platform without it). */
    val deviceSync: DeviceSyncController? get() = deps.deviceSync

    /** The RetroAchievements tool, for its screens (null on a platform without it). */
    val achievements: RaController? get() = deps.achievements

    /** This instance's [UpdateController.hostBusy], kept so [releaseUpdater] lets go of it only while still the updater's. */
    private val updateHost: () -> String? = { updateHostBusy() }

    init {
        deps.updater?.let { u ->
            u.hostBusy = updateHost
            // About's update row follows the updater (a check, the download's percent, the wait), as the GPU row follows its
            // query; by its label, so a download's many progress reports redraw the page once a percent.
            scope.launch {
                u.state.collect { st ->
                    val label = appUpdateLabel(st)
                    if (label == _updateLabel.value) return@collect
                    _updateLabel.value = label
                    if ((_screen.value as? Screen.Settings)?.section == SettingsSection.SYSTEM) refreshSettings()
                }
            }
        }
        // A started game was recorded: a first launch makes the Recently Played row appear, also when launched from the
        // main menu itself (a pin row, the switcher over it). Ends with the scope, so the recorder never holds this state.
        scope.launch {
            recents.written.collect {
                rebuildMainRows()
                swapMainRows()
                refreshGameInfo()   // a game was just played
            }
        }
        // The achievements panel follows the game list's selection, and closes off the game list.
        scope.launch { _selection.collect { ra.followRaPanel(); ra.followRaDetails(); followSecondList(); followDrawerPreview() } }
        scope.launch {
            _screen.collect {
                if (setupScreen(it)) _toolOnTop.value = false   // a tool's page no more: setup takes the menu's screen
                if (it !is Screen.Games) { ra.closeRaPanel() } else { ra.followRaPanel() }; ra.followRaDetails(); followSecondList(); followDrawerPreview()
            }
        }
        deps.achievements?.let { c ->
            scope.launch {
                var wasBusy = false
                c.state.collect { s ->
                    // A job (Download all, a sync, sign-in, sign-out, a reset) just ended: the menus' achievement info
                    // reads the cache again, so a game list shows the new progress without a game played first.
                    val busy = s !is RaState.Idle
                    if (wasBusy && !busy) refreshGameInfo()
                    wasBusy = busy
                    when (_screen.value) {
                        // Busy shows no rows; back to idle it lands on the row that started the job. The settings page's rows follow.
                        is Screen.RetroAchievements, is Screen.RaSettings -> ra.onRaState(s, c)
                        is Screen.Onboarding -> if (s is RaState.Idle) onRaIdleOnboarding(s.status)   // onboarding's sign-in ended
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
            // Files arrived (saves, states, their pictures): Continue's resume point and slot picture are read again.
            scope.launch { c.received.collect { rebuildMainRows(); swapMainRows() } }
            // ROM files arrived: a plain Systems page rescans at once; anywhere else (the tool itself) waits until it is left.
            // Collections changed (merged by this phone's writer, or pushed by a peer phone into its home `Collections/`): a
            // store copy takes in a daemon-written home file, then the tab is listed again and those counts redone.
            scope.launch {
                c.collectionsArrived.collect { synced ->
                    synced.received.forEach { collections.storeFollowsHome(it) }
                    refreshCollectionNames()
                    (synced.written + synced.received).forEach { invalidateCollectionCounts(it) }
                    rebuildMainRows(); swapMainRows()
                }
            }
            scope.launch { c.romsArrived.collect { val s = _screen.value; if (s is Screen.Systems && s.menu == null && s.search == null) rescan() else { romsPending = true; libraryDirty = true } } }
            // Pending ROMs scan as soon as a plain Systems page shows again, however the user got there: B on the tool,
            // Home, Back from a list the sync ended under, the end of a Loading the sync ended during. B on the tool and
            // leaving setup's sync step take them first themselves.
            scope.launch {
                _screen.collect { s -> if (romsPending && s is Screen.Systems && s.menu == null && s.search == null) { romsPending = false; rescan() } }
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

    private val indexLock = Mutex()             // every index read, write and in-memory swap
    private var library: LibraryIndex? = null   // the library as last scanned, read or moved
    private var lateMountHere: List<String> = emptyList()   // [LibraryIndex.lateMount] of this AppState's last scan (a stored one is stale)

    // Art results applied to [library] but not yet written ([artUnsaved]) or shown ([artUnshown]); [artSeq] counts them so
    // [artFlushJob] knows they stopped coming. All under [indexLock].
    private var artUnsaved = false
    private var artUnshown = false
    private var artSeq = 0L
    private var artFlushJob: Job? = null

    /**
     * What follows an index change ([mutateIndex]): the playable cache always; then the collection counts, the menu's rows
     * and the open list (its highlighted game kept) as asked. [ui]: on [uiContext], for a caller off it.
     */
    private data class IndexRefresh(val counts: Boolean = false, val rows: Boolean = false, val openList: Boolean = false, val ui: Boolean = false)

    /**
     * The one way the library index changes. Under [indexLock], [change] gets the index in memory (null when there is none)
     * and returns the next one; null or the same instance is no change, and nothing else runs. A new one becomes [library]
     * and is written ([persist]) before the lock is let go, which writes any art results still waiting too. [persistNow]
     * false (art results only) leaves the write and the refresh to [flushArt]: a fetch of thousands of games would
     * otherwise rewrite the whole index and rebuild the menu once per game. Then, unless [refresh] is null, the steps it
     * asks ([afterIndexChange]). Returns the new index, or null for no change.
     */
    private suspend fun mutateIndex(
        refresh: IndexRefresh?,
        persistNow: Boolean = true,
        change: suspend (LibraryIndex?) -> LibraryIndex?,
    ): LibraryIndex? {
        val next = indexLock.withLock {
            val current = library
            change(current)?.takeIf { it !== current }?.also {
                library = it
                if (persistNow) {
                    persist(it)
                    artUnsaved = false   // the whole index went out, the art in it with it
                } else {
                    artUnsaved = true; artUnshown = true; artSeq++
                }
            }
        } ?: return null
        refresh?.let { afterIndexChange(next, it) }
        return next
    }

    private suspend fun afterIndexChange(index: LibraryIndex, r: IndexRefresh) {
        suspend fun steps() {
            systemsCache = index.playableSystems(hiddenPaths.toSet())
            if (r.counts) invalidateCollectionCounts()
            if (r.rows) rebuildMainRows()
            if (r.openList) refreshOpenList(select = (_screen.value as? Screen.Games)?.entries?.getOrNull(_selection.value.index)?.game?.path)
        }
        if (r.ui) withContext(uiContext) { steps() } else steps()
    }

    /**
     * After an art result ([mutateIndex] with persistNow false): one [flushArt] once results stop coming for [ART_QUIET_MS]
     * (at most [ART_MAX_WAIT_MS] after the first), whatever thread the result came on. A result that lands during the
     * flush goes round again; the job lets go of [artFlushJob] under the lock only with nothing left, so no result is
     * left without a flush coming.
     */
    private suspend fun scheduleArtFlush() {
        indexLock.withLock {
            if (artFlushJob?.isActive == true) return@withLock
            artFlushJob = scope.launch {
                do {
                    var waited = 0L
                    while (waited < ART_MAX_WAIT_MS) {
                        val seen = indexLock.withLock { artSeq }
                        delay(ART_QUIET_MS)
                        waited += ART_QUIET_MS
                        if (indexLock.withLock { artSeq } == seen) break
                    }
                    flushArt()
                } while (indexLock.withLock { artUnshown.also { if (!it) artFlushJob = null } })
            }
        }
    }

    /**
     * Writes the index when art results are waiting, then rebuilds the playable cache, the menu's rows and the open list
     * once for all of them. [library] is the truth: art waiting since a scan or another change wrote the whole index is not
     * written again, and a result that came in meanwhile is in it. Run by the debounce, by a fetch's or Reset's end
     * ([ArtworkHost.flushArt]) and when the app goes to the background.
     */
    private suspend fun flushArt() {
        val shown = indexLock.withLock {
            val ix = library
            if (artUnsaved && ix != null) persist(ix)
            artUnsaved = false
            ix?.takeIf { artUnshown }.also { artUnshown = false }
        } ?: return
        afterIndexChange(shown, IndexRefresh(rows = true, openList = true, ui = true))
    }

    /** The app left the foreground: art results still waiting are written now, not left to a debounce a kill would lose. */
    fun onBackground() {
        if ((_screen.value as? Screen.Settings)?.section == SettingsSection.SHORTCUTS) { closeShortcuts(); refreshSettings() }
        scope.launch { flushArt() }
    }

    /**
     * The home pick, or first, once, the All files access step: only where the permission is declared and not granted,
     * and only until it was answered ([AppSettings.allFilesAsked]).
     */
    private fun showPickFolder() {
        val ask = !deps.settings.allFilesAsked && permissionsStep().let { it.allFiles == false || it.notifications == false }
        if (ask) { allFilesStepShown = true; showPermissions(); return }
        // No All files access: the home is the app's Android/media folder, nothing to pick. Once per run (a failed scan of it picks).
        val media = deps.homes?.media
        if (media != null && inFirstSetup() && !mediaHomeTaken && !homePickShown()) return takeMediaHome(media)
        _screen.value = Screen.PickFolder
        _selection.value = ListSelection(0, 1)
    }

    /** First setup's folder steps on [media] as after a pick ([setHome]), the saved home left unset: its scan, then the home page. */
    private fun takeMediaHome(media: ContentRef) {
        mediaHomeTaken = true
        firstSetup = true
        if (deps.settings.onboardingDone == false) deps.settings.onboardingStep = FOLDER_STEPS
        rescan(then = { showHomePage(media) })
    }

    /** All files access is on (or there is no media home): first setup shows the home pick; without it the media home is used. */
    private fun homePickShown(): Boolean = deps.homes?.media == null || deps.allFilesAccess?.takeIf { it.declared }?.granted() == true

    /** B from the media home's first page (the home page, or the ROMs folder offer without one): back past the skipped pick, which runs again going on. */
    private fun backPastMediaHome() {
        mediaHomeTaken = false
        homePage = null
        firstSetup = false
        if (deps.settings.onboardingStep == FOLDER_STEPS) deps.settings.onboardingStep = null
        backFromHomePick()
    }

    /**
     * The no-access line on the media home's first folder-steps page (the home page, or the ROMs folder offer when the
     * home has no game): where the games folder is. Null on every other page.
     */
    fun setupNote(): String? {
        val media = deps.homes?.media ?: return null
        if (!mediaHomeTaken || !inFirstSetup()) return null
        val first = when (val s = _screen.value) {
            is Screen.FolderSummary -> s.extraId == Recents.HOME
            Screen.AddFirstExtra -> homePage == null
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
     */
    private suspend fun rebuildMainRows() {
        rowsLock.withLock { buildMainRows() }
        fillCollectionCounts()
    }

    private suspend fun buildMainRows() {
        pins = deps.userData.readPins()
        val recents = deps.userData.readRecents()
        // Continue: the first recent that resolves, whether or not it can resume.
        val last = recents.firstNotNullOfOrNull { resolveOne(it.path) }
            ?.let { e -> MainRow.Continue(e, deps.userData.resumeInfo(e.system.tag, e.game.fileName)) }
        val tools = toolRows()
        // The Android pins after the ROM pins, resolved against the console (one that left it is skipped, and kept).
        // Each game once (a hidden copy's line opens its home game, [resolveDistinct]); the recent set also holds the
        // resolved games' own paths, so a home game played as its hidden copy is not "not started".
        val lookup = playable()
        val played = recents.map { it.path }.toSet() + resolveDistinct(lookup, recents.map { it.path }).map { it.game.path }
        val inputs = HomeInputs(last, resolveDistinct(lookup, pins.map { it.path }) + androidPinned(), pinnedToolRows(toolPins, tools + homeOnlyAppRows(tools)), played)
        val built = homeTab.homeFor(inputs)   // the stats as they are now, after the reads
        homeTab.homeInputs = inputs
        allMenuTabs = buildMenuTabs(
            home = built,
            // One row per display name: SFC and SUPA are one "Super Nintendo ES" row, as NX Redux collates sibling folders.
            // The Android games console sits among them by name; it is never one of [systemsCache]'s.
            consoles = withAndroidConsole(
                systemsCache.filter { !it.unassigned }.groupBy { it.displayName }.values.map { MainRow.System(it) },
                androidConsole.also { menuAndroidConsole = it },
            ),
            collections = collectionNames.map { MainRow.Collection(it, collectionCounts[it]) },   // as counted so far; the rest come after
            tools = tools,
            homeStyle = homeStyle(),
        )
        homeTab.noteHomeBuilt(built)
    }

    /** Rebuilds the Home tab alone from what it was last built from, with the stats, game info and frame as they are now (no I/O). */
    private fun rebuildHomeTab() {
        val built = homeTab.homeFor(homeTab.homeInputs ?: return)   // the shelves too: the frame may have changed
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
        return shelvesCache.shelves(systemsCache, _gameInfo.value, inputs.recent, exclude, now, deps.calendar.dayStart(now)).filter { shelfShown(it.first) }
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
     * The collection counts are stale: [name]'s (it was just written), or every one (the library or the listing changed).
     * A written collection leaves the stored cache too. Every other stored entry stays: its file and library stamps decide
     * whether the next pass may use it, and that pass rewrites the cache.
     */
    private fun invalidateCollectionCounts(name: String? = null) {
        if (name == null) collectionCounts.clear() else collectionCounts.remove(name)
        countsGeneration++
        if (name != null) { countCache.remove(name); countCache.save() }
    }

    /** [CountStamps.library] of [systems] (the games a collection line can resolve to), taken once per systems list. */
    private suspend fun libraryStamp(systems: List<SystemFolder>): String {
        libraryStampOf?.takeIf { it.first === systems }?.let { return it.second }
        val home = home()?.value
        val lookup = playableOffMain(systems)
        val stamp = withContext(deps.compute) { CountStamps.library(home, lookup.keys) }
        libraryStampOf = systems to stamp
        return stamp
    }

    /**
     * Fills the counts of the collections just listed from the stored cache where both stamps still match (one listing,
     * no collection file read), so the menu built next shows them at once. Dropped when the counts went stale meanwhile.
     */
    private suspend fun seedCollectionCounts() {
        countCache.load()
        val names = collectionNames
        if (!countCache.hasAny(names)) return
        val generation = countsGeneration
        val systems = systemsCache
        val library = libraryStamp(systems)
        val stamps = collections.stamps(names)
        if (generation != countsGeneration || systems !== systemsCache || names != collectionNames) return
        names.forEach { n -> countCache.valid(n, stamps[n], library)?.let { collectionCounts[n] = it } }
    }

    /**
     * Counts the collections not counted yet, after the menu is shown (a rebuild never waits on collection I/O), then shows
     * them. Each file is read with the loader opening it uses; its lines resolve on [AppDeps.compute] against one lookup of
     * the library. A pass begun before its counts went stale is dropped and counted again. One pass at a time.
     */
    private fun fillCollectionCounts() {
        if (countsJob?.isActive == true || collectionNames.all { it in collectionCounts }) return
        countsJob = scope.launch {
            countCache.load()
            while (true) {
                val generation = countsGeneration
                val missing = collectionNames.filter { it !in collectionCounts }
                if (missing.isEmpty()) break
                val systems = systemsCache
                val library = libraryStamp(systems)
                // Stored counts whose stamps still match need no read (a rescan that changed nothing); the rest are read.
                val cached = if (countCache.hasAny(missing)) {
                    val stamps = collections.stamps(missing)
                    missing.mapNotNull { n -> countCache.valid(n, stamps[n], library)?.let { n to it } }.toMap()
                } else emptyMap()
                val read = (missing - cached.keys).associateWith { collections.readStamped(it) }
                val lookup = if (read.isEmpty()) null else playableOffMain(systems)
                val counts = if (lookup == null) emptyMap() else withContext(deps.compute) {
                    read.mapValues { (_, r) -> r.lines?.let { resolvedCount(it, lookup) } }
                }
                if (generation != countsGeneration) continue
                collectionCounts.putAll(cached)
                collectionCounts.putAll(counts)
                // An unreadable file (no count) or one without a stamp is not stored: the next start counts it.
                counts.forEach { (n, c) -> val stamp = read[n]?.stamp; if (c != null && stamp != null) countCache.put(n, stamp, library, c) else countCache.remove(n) }
                countCache.retain(collectionNames)
                countCache.save()
                rowsLock.withLock { buildMainRows() }
                swapMainRows()
            }
        }
    }

    /**
     * Re-lists Collections/ (a tree read). A listing that would not list ([ListingUnavailable]) and gives no names keeps
     * the names as they were: an empty answer there is no proof. True when the names changed.
     */
    private suspend fun refreshCollectionNames(): Boolean {
        val checked = collections.namesChecked()
        if (!checked.listed && checked.names.isEmpty()) return false
        if (checked.names == collectionNames) return false
        collectionNames = checked.names
        invalidateCollectionCounts()
        seedCollectionCounts()
        return true
    }

    /** The tree was just read (a scan, the index start's probe): pick up a changed Collections tab. */
    private suspend fun refreshCollectionsFlag() {
        if (refreshCollectionNames()) { rebuildMainRows(); swapMainRows() }
    }

    /**
     * The main menu on [category]'s tab; a gone one resolves to the next visible tab ([resolveMenuTab]). While a hidden tab
     * is pushed, that tab ([pushedScreen]); one that can no longer be pushed (shown again, or emptied) lands on the tab underneath.
     */
    private fun menuScreen(category: MenuCategory = menuCategory): Screen.Systems {
        val wasPushed = pushedTab != null
        pushedScreen()?.let { return it }
        val tabs = menuTabs
        return Screen.Systems(tabs, resolveMenuTab(tabs, if (wasPushed) menuCategory else category), hidden = hiddenMenuTabs(allMenuTabs, tabs))
    }

    /** The pushed hidden tab as a one-tab menu, or null (dropping [pushedTab]) when it is shown again or has no content left. */
    private fun pushedScreen(): Screen.Systems? {
        val c = pushedTab ?: return null
        val tab = allMenuTabs.firstOrNull { it.category == c }?.takeIf { menuTabs.none { v -> v.category == c } }
        if (tab == null) { pushedTab = null; return null }
        return Screen.Systems(listOf(tab), 0, pushed = true)
    }

    /**
     * Opens [category], a tab Layouts hides that has content, over the main menu (the handheld's pushTabOverTab): its rows
     * in its own layout, orientation and background, on its first item. Only from the menu itself: never over a pushed
     * tab, a context menu or a visible tab, and never Home (only Consoles, Collections or Tools push). B ([popHiddenTab])
     * returns to the tab underneath, focus where it was.
     */
    internal fun openHiddenTab(category: MenuCategory) {
        if (category == MenuCategory.HOME) return
        val s = _screen.value as? Screen.Systems ?: return
        if (s.pushed || s.menu != null || s.search != null) return
        if (menuTabs.any { it.category == category } || allMenuTabs.none { it.category == category }) return
        _tabFocus.value = false
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
     * category later ([swapMainRows]) instead of [menuCategory]. A pushed switcher snapshot is dropped too ([closeSwitcher]
     * then lands on the menu as it is now).
     */
    private fun dropPushedTab() {
        if (pushedTab == null) return
        pushedTab = null
        if ((beforeSwitcher?.first as? Screen.Systems)?.pushed == true) beforeSwitcher = null
        if ((_screen.value as? Screen.Systems)?.pushed == true) showMenu(menuScreen(menuCategory))
    }

    /** On the main menu: remember the current tab and its row. */
    private fun rememberMenuSelection() {
        val s = _screen.value as? Screen.Systems ?: return
        val c = s.category ?: return
        // Pushed, this writes the hidden tab's row too: harmless, as each push clears it ([openHiddenTab]) and opens on the first item.
        tabSelections[c] = _selection.value
        if (c == MenuCategory.HOME) homeTab.rememberHomeRow(s.rows, _selection.value.index)
        if (!s.pushed) menuCategory = c   // a pushed tab never becomes the tab underneath
    }

    /**
     * Shows [screen] with its tab's remembered row, clamped. Over START's open prompt (a rebuild in place: a foreground,
     * a new Home frame, counts landing) the prompt and its draft stay; closing it is the prompt's own keys.
     */
    /** First setup's steps and onboarding (a folder change in Settings may resume them): never a tool on the top screen. */
    private fun setupScreen(s: Screen): Boolean = when (s) {
        is Screen.PermissionsStep, Screen.PickFolder, Screen.AddFirstExtra, is Screen.FolderSummary, is Screen.AndroidGamesStep, is Screen.Onboarding, is Screen.StorageUpgrade -> true
        else -> false
    }

    private fun showMenu(menu: Screen.Systems, at: Int = -1) {
        val prev = _screen.value
        _toolOnTop.value = false   // back on the menu: it has the focus again
        val screen = (prev as? Screen.Systems)?.search?.let { menu.copy(search = it) } ?: menu
        // Another tab (or the menu from another screen): its grid reports its own columns on its first layout. The same
        // tab rebuilt in place keeps them, as its grid stays laid out.
        if (prev !is Screen.Systems || prev.category != screen.category) gridColumns = 0
        if (prev !is Screen.Systems) _tabFocus.value = false   // back from another screen: on the content
        if (screen.tabs.size < 2 || screen.pushed) _tabFocus.value = false   // no tab row to hold the d-pad (one tab: the title)
        _screen.value = screen
        homeTab.publishHomeView()   // the Home rows on screen may be the ones last built now
        if (!screen.pushed) screen.category?.let { menuCategory = it }
        val rows = screen.rows
        _selection.value = if (at >= 0) ListSelection(at, rows.size).clampedTo(rows.size)
            else rememberedRow(screen.category ?: menuCategory, rows).clampedTo(rows.size)
        if (screen.category == MenuCategory.HOME) homeTab.noteHomeFocus(_selection.value.index, rows)
        refreshResume()   // Continue's A Resume or A Play
        homeTab.menuShown(screen.category)   // the menu's first show computes Home's stats, as does Home after a sign-in or out
        maybeAskAndroidGames()
        // The menu came up (not rebuilt in place, which a count landing does while the install-permission screen is up):
        // the daily check, or a held install's retry. A foreground over it does the same ([onForeground]).
        if (prev !is Screen.Systems && screen.menu == null && !firstSetup) updaterOnMainMenu()
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
        val r = tabSelections[category] ?: return ListSelection(0, 0)
        if (category != MenuCategory.HOME) return r
        return homeTab.rememberedRow(r, rows)
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
        val s = _screen.value as? Screen.Systems ?: return
        if (s.menu != null) return
        val highlighted = s.rows.getOrNull(_selection.value.index)
        val screen = menuScreen(s.category ?: menuCategory)
        // A row gone (an unpinned pin), the same index lands on the one after it; the tab gone (its last collection
        // removed), the fallback tab lands on its own remembered row, not the gone one's index.
        val launched = if (screen.category == MenuCategory.HOME) homeLaunchedPin(highlighted, screen.rows) else null
        val home = screen.category == MenuCategory.HOME
        val at = (if (home) homeTab.homeFind(highlighted, screen.rows) else screen.rows.indexOf(highlighted).takeIf { it >= 0 }) ?: launched ?: keptIndex(s, screen)
        showMenu(screen, at)
        // The highlight may have moved with its row: the next show of this tab starts there.
        screen.category?.let { tabSelections[it] = _selection.value }
        if (screen.category == MenuCategory.HOME) homeTab.rememberHomeRow(screen.rows, _selection.value.index)
    }

    /** Reads hidden.txt into [hiddenPaths] and returns it as the set the index filters with. */
    private suspend fun readHidden(): Set<String> {
        hiddenPaths = deps.userData.readHidden()
        return hiddenPaths.toSet()
    }

    private suspend fun showSystems(playable: List<SystemFolder>) {
        systemsCache = playable
        invalidateCollectionCounts()
        rebuildMainRows()
        when {
            resumeFolderSteps() -> Unit
            onboardingTakesOver() -> showOnboarding()
            else -> showSystemsNow()
        }
        refreshGameInfo()
    }

    /**
     * A start while first setup's folder steps were under way (saved as [FOLDER_STEPS], the app since closed): they
     * resume at the home page (its count and move; none: the ROMs-folder offer).
     */
    private fun resumeFolderSteps(): Boolean {
        if (firstSetup || deps.settings.onboardingDone != false || deps.settings.onboardingStep != FOLDER_STEPS) return false
        val home = home() ?: return false
        firstSetup = true
        if (deps.settings.treeRef == null && home == deps.homes?.media) mediaHomeTaken = true   // a restart on the media home: no pick behind it
        showHomePage(home)
        return true
    }

    /** First setup's folder steps are done: onboarding's own steps start from their first. */
    private fun endFolderSteps() {
        firstSetup = false
        if (deps.settings.onboardingStep == FOLDER_STEPS) deps.settings.onboardingStep = null
    }

    /**
     * A load landing while onboarding runs (a sync's rescan) or is still to do (first setup's folder steps just ended):
     * the flow's step shows instead of the menu, resuming at the saved step.
     */
    private fun onboardingTakesOver(): Boolean {
        if (onboarding.step != null) return true
        if (deps.settings.onboardingDone != false) return false
        if (firstSetup) return false   // the folder steps' scans: their own page follows, onboarding starts after them
        onboarding.context = onboarding.context.copy(signedIn = deps.settings.raToken != null)
        refreshPadContext()   // the customise pages a resume can land on depend on it
        onboarding.startMain(resume = deps.settings.onboardingStep?.let { n -> OnboardingStep.entries.firstOrNull { it.name == n } })
        deps.settings.onboardingStep = onboarding.step?.name
        return true
    }

    /** The current onboarding step's page, on [selected] (by default the row holding the current value, else the first). */
    private fun showOnboarding(selected: Int = onboardingCurrentRow(), error: String? = null) {
        refreshPadContext()
        val step = onboarding.step ?: return
        val (title, details, rows) = onboardingPage(step, error)
        // The step count is the page's small title; the step's own question or name is its headline, its lines under it.
        // The sync step is Device Sync itself, in place under this page title.
        if (step == OnboardingStep.SYNC_OFFER) deps.deviceSync?.let { sync ->
            onboardingSync = true
            sync.open()
            _screen.value = Screen.DeviceSync
            // Sync ROMs first where it is offered: the option before the links.
            publishSyncSetup(if ((sync.page.value as? DeviceSyncPage.Ready)?.romsAvailable == true) SyncFocus.ROMS else SyncFocus.LINK)
            return
        }
        val pageTitle = onboardingPageTitle()
        val canGoBack = step != OnboardingStep.WELCOME
        _screen.value = Screen.Onboarding(step, pageTitle, listOf(title) + details, rows, accent = deps.settings.accent, padTheme = deps.settings.padTheme, canGoBack = canGoBack, shot = onboardingShot(step))
        _selection.value = ListSelection(selected.coerceIn(0, (rows.size - 1).coerceAtLeast(0)), rows.size)
    }

    /**
     * Whether the main menu's pad shows now: Controls → Menu pad, except during first setup and onboarding, which show it
     * on the PAD page only (its value applies at once there) and give every other page, the folder steps and their
     * pickers too, the full height. The menu after setup follows the choice.
     */
    fun menuPadShown(): Boolean {
        when (_screen.value) {
            is Screen.PermissionsStep, Screen.PickFolder, Screen.AddFirstExtra, is Screen.AndroidGamesStep, is Screen.FolderSummary, is Screen.StorageUpgrade -> return false
            else -> Unit
        }
        // First setup between the welcome and HANDHELD (the folder steps and their pickers): not asked yet.
        val step = onboarding.step ?: return deps.settings.menuPad && deps.settings.onboardingDone != false && padScreen(_screen.value)
        // Setup shows it on its own page only (the choice, live); every other page keeps the full height.
        return step == OnboardingStep.PAD && deps.settings.menuPad
    }

    /**
     * The screens the portrait menu pad belongs to: the menu (its tabs, game lists, a game's Game Settings over its list,
     * the Game Switcher). The tools' pages never show it, whatever Controls → Menu pad says, but Controls (and its Pad
     * layout) and Appearance, which preview the pad and its theme.
     */
    private fun padScreen(s: Screen): Boolean = when (s) {
        is Screen.Systems, is Screen.Games, is Screen.Switcher, is Screen.Loading -> true
        is Screen.EmulatorOptions -> gameSettingsModal(s)
        is Screen.Settings -> s.section == SettingsSection.CONTROLS || s.section.parent == SettingsSection.CONTROLS || s.section == SettingsSection.APPEARANCE
        else -> false
    }

    /** The page title onboarding's current step shows: "Setup · Step n of m" (counting first setup's folder steps). */
    private fun onboardingPageTitle(): String {
        val (n, m) = onboarding.position()
        val folder = setupFolderSteps()
        return when {
            m > 0 -> "Setup · Step ${n + folder} of ${m + folder}"
            onboarding.step == OnboardingStep.TEXT_SIZE || onboarding.step == OnboardingStep.BOTTOM_SCREEN -> "Setup"
            else -> "Welcome"
        }
    }

    private val _syncSetup = MutableStateFlow<SyncSetup?>(null)
    /** Device Sync's page state ([SyncSetup]): the tool's, or setup's sync step's; null while it is not showing. */
    val syncSetup: StateFlow<SyncSetup?> = _syncSetup.asStateFlow()

    private fun publishSyncSetup(focus: SyncFocus = _syncSetup.value?.focus ?: SyncFocus.LINK) {
        _syncSetup.value = when {
            _screen.value != Screen.DeviceSync -> null
            onboardingSync -> SyncSetup(onboardingPageTitle(), onboardingSynced != null, focus)
            else -> SyncSetup(DeviceSyncText.TITLE, synced = false, focus = focus, inSetup = false)
        }
    }

    /** A tap on the setup sync page's row [index] ([syncSetupRows]): it selects; [open] (a tap that chooses) also acts. */
    fun syncSetupTap(index: Int, open: Boolean) {
        val sync = deps.deviceSync ?: return
        val page = sync.page.value as? DeviceSyncPage.Ready ?: return
        if (_screen.value != Screen.DeviceSync) return
        when (val row = syncSetupRows(page, skip = onboardingSync).getOrNull(index) ?: return) {
            SyncSetupRow.Roms -> { publishSyncSetup(SyncFocus.ROMS); if (open) sync.flipRoms() }
            is SyncSetupRow.Link -> { sync.pick(row.link); publishSyncSetup(SyncFocus.LINK); if (open) onDeviceSyncButton(NxButton.A) }
            SyncSetupRow.Skip -> { publishSyncSetup(SyncFocus.SKIP); if (open) leaveOnboardingSync(forward = true) }
        }
    }

    /** The row a step opens on: the first (a layout page's layout row, a value page's first value). */
    private fun onboardingCurrentRow(): Int = 0

    /**
     * A value page's row [row] stepped by [by] (-1: the one before; A steps forward), in place: SAVE_FORMATS' save
     * format, state format and extracted name; TEXT_SIZE's size; ACCENT's accent and secondary accent; TITLE's page
     * title and button hints (Show / Hide); PAD's pad (shown / hidden) and theme ([ONBOARDING_VALUE_KINDS]). The same
     * settings Settings steps ([settingValue]), applied at once and the page republished on that row, so the real pad
     * (or the preview picture, or the colours, or the text size) changes and the step count follows ([App] reads them
     * per screen). A row past the page's values, or another [step], does nothing.
     */
    private fun stepOnboardingValue(step: OnboardingStep?, row: Int, by: Int) {
        val kind = ONBOARDING_VALUE_KINDS[step]?.getOrNull(row) ?: return
        settingValue(SettingsRow(kind, ""))?.step(by)   // a bare row: none of these needs a tab, console or hint
        showOnboarding(row)
    }

    private var padPortrait = true
    private var padController = false

    /** The device as App sees it: portrait or not, a controller or not; onboarding's customise pages follow it. */
    fun onPadContext(portrait: Boolean, controller: Boolean) {
        if (portrait == padPortrait && controller == padController) return
        padPortrait = portrait
        padController = controller
        // The page republishes for its step count; a sign-in prompt keeps its typing (the count catches up on the next page).
        val s = _screen.value as? Screen.Onboarding ?: return
        if (onboarding.step != null && !s.prompt) showOnboarding(_selection.value.index)
    }

    /**
     * Which customise pages apply here: the pad is a choice on a phone without a controller (built in or connected; a
     * clip-on counts as none), in either orientation, as it shows when the phone is held upright; title and hints hide
     * only where buttons exist.
     */
    private fun refreshPadContext() {
        val padPage = !padController
        onboarding.context = onboarding.context.copy(padPage = padPage, buttons = padController || (padPage && deps.settings.menuPad))
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
            SettingsSection.SECOND_SCREEN -> if (!connected) showSettingsMenu()   // its section is gone: back to the root
            else -> Unit
        }
        if (_screen.value is Screen.SettingsMenu) showSettingsMenu()   // the root's sections follow
        emulatorSettings.refreshOpenPage()
    }

    /** A step's title, details and rows. */
    private fun onboardingPage(step: OnboardingStep, error: String?): Triple<String, List<String>, List<NxRow>> = when (step) {
        OnboardingStep.WELCOME -> Triple("NX Redux", listOf("Pick up. Select a game. Play."), listOf(NxRow("Get started")))
        OnboardingStep.BOTTOM_SCREEN -> Triple(
            "Which is the bottom screen?",
            listOf(
                "Tap the button on the bottom screen. It becomes the touch screen, and games show their picture on the other.",
                "Change it later in Settings → Second screen, or for one console in its settings.",
            ),
            listOf(NxRow("This is the bottom screen"), NxRow("Skip")),
        )
        OnboardingStep.HANDHELD -> Triple(
            "Do you also play on an NX Redux handheld?", listOf("Your games, BIOS, saves, save states and collections can then carry over between the two."),
            listOf(NxRow("Yes, I have one"), NxRow("No")),
        )
        OnboardingStep.SAVE_FORMATS -> Triple(
            "Match your handheld", listOf("Set these as on your handheld's Settings → System, so each loads the other's saves."),
            listOf(
                NxRow("Save format", secondary = deps.settings.saveFormat.label, cycleable = true),
                NxRow("Save state format", secondary = deps.settings.stateFormat.label, cycleable = true),
                NxRow("Use extracted file name", secondary = if (deps.settings.useExtractedFileName) "On" else "Off", cycleable = true),
                NxRow("Continue"),
            ),
        )
        OnboardingStep.SYNC_OFFER -> Triple(
            "Sync with your handheld",
            listOfNotNull(
                "Copies games, BIOS, saves, save states and collections both ways. Open Device Sync on the handheld too.",
                onboardingSynced?.takeIf { it.isNotEmpty() }?.let { "Last sync: $it." },
            ),
            if (onboardingSynced == null) listOf(NxRow("Sync now"), NxRow("Later")) else listOf(NxRow("Sync again"), NxRow("Continue")),
        )
        // The three imports, then (in place) a running one's progress with the rows waiting, or its summary and skipped files.
        OnboardingStep.IMPORT_SAVES -> when (val r = onboardingImported) {
            is ImportResult.Done -> Triple(
                "Bring your saves", r.summary.lines() + r.summary.statesNote(), r.summary.skipped.map { NxRow(it.text()) } + NxRow("Continue"),
            )
            else -> Triple(
                "Bring your saves",
                listOf(onboardingImporting ?: (r as? ImportResult.Message)?.text ?: "Copy battery saves and save states from another app. Nothing is moved or deleted."),
                listOf("From a folder", "From a zip file", "From RetroArch", "Skip").map { NxRow(it, enabled = onboardingImporting == null) },
            )
        }
        OnboardingStep.ARTWORK -> if (artworkScreens?.available == true) Triple(
            "Fetch artwork", listOf("Box art and screenshots for your ${gamesLabel(onboardingGameCount())}, fetched in the background."),
            listOf(NxRow("Fetch now"), NxRow("Skip")),
        ) else Triple(
            "Fetch artwork", listOf("No artwork source is set up yet; add one later in Tools → Artwork."), listOf(NxRow("Continue")),
        )
        OnboardingStep.RA_SIGN_IN -> Triple(
            "RetroAchievements",
            listOfNotNull("Sign in to earn achievements and see your progress.", if (onboardingSigningIn) "Signing in…" else error),
            listOf(NxRow("Sign in"), NxRow("Skip")),
        )
        OnboardingStep.RA_DOWNLOAD -> Triple(
            "Achievement data", listOf("Achievement lists and badges for your ${gamesLabel(onboardingGameCount())}, downloaded in the background. Its progress shows as a notification."),
            listOf(NxRow("Download now"), NxRow("Skip")),
        )
        OnboardingStep.CUSTOMISE -> Triple(
            "Customise the layout?", listOf("Pick the menu's look page by page, or keep the defaults. Each can change later in Settings → Layouts."),
            listOf(NxRow("Yes, customise"), NxRow("Use the defaults")),
        )
        // Asked first, after the welcome: the lists', titles' and hints' text (setup's own pages too), a value row that flips
        // in place (the page reads at the new size), then Continue.
        OnboardingStep.TEXT_SIZE -> Triple(
            "Text size", listOf("How big the text reads in setup and the app: lists, page titles and button hints. Change it later in Settings → Appearance."),
            listOf(NxRow("Text size", secondary = deps.settings.optionTextSize.label, cycleable = true), NxRow("Continue")),
        )
        // The pad and its theme (only while the pad shows): value rows that flip in place (the real pad follows, to
        // compare), then Continue.
        OnboardingStep.PAD -> Triple(
            "On-screen pad in the menu",
            // Landscape shows no menu pad: the choice still applies, seen once the phone is upright.
            listOfNotNull(
                "Show the pad below the menu in portrait, and how it looks. Games keep their own pad, and tools always hide it, so their pages keep the whole screen.",
                if (padPortrait) null else "Turn your phone upright to see the pad.",
            ),
            listOfNotNull(
                NxRow("On-screen pad", secondary = if (deps.settings.menuPad) "Show" else "Hide", cycleable = true),
                if (deps.settings.menuPad) NxRow("Pad theme", secondary = padThemeLabel(deps.settings.padTheme), cycleable = true) else null,
                NxRow("Continue"),
            ),
        )
        // The page title and the button hints: value rows that flip in place (the picture above shows the pair), then Continue.
        OnboardingStep.TITLE -> Triple(
            "Page title and button hints", listOf("The tab row at the top of the menu (L1 / R1 switch tabs either way) and the hint bar at the bottom."),
            listOf(
                NxRow("Page title", secondary = if (deps.settings.menuPageTitle) "Show" else "Hide", cycleable = true),
                NxRow("Button hints", secondary = if (deps.settings.menuButtonHints) "Show" else "Hide", cycleable = true),
                NxRow("Continue"),
            ),
        )
        // The layout and the options its picture shows as value rows (they step in place, applied at once, the picture
        // under them following), then Continue.
        OnboardingStep.TAB_HOME, OnboardingStep.TAB_CONSOLES, OnboardingStep.TAB_COLLECTIONS, OnboardingStep.TAB_TOOLS -> Triple(
            "${ONBOARDING_TAB_STEPS.getValue(step).label} layout",
            listOf(
                if (step == OnboardingStep.TAB_HOME) "Home is where your last played game waits, next to the games and tools you pin, with a few suggestions of what to play next."
                else "How the ${ONBOARDING_TAB_STEPS.getValue(step).label} tab lays out.",
            ),
            onboardingLayoutRows(step),
        )
        OnboardingStep.GAME_LISTS -> Triple("Game lists layout", listOf("How a console's or a collection's games lay out."), onboardingLayoutRows(step))
        // The accent and the secondary accent: value rows that step in place (the page recolours), then Continue.
        OnboardingStep.ACCENT -> Triple(
            "Pick your accents", listOf("The accent colours the selection everywhere; the secondary accent, the lit details beside it."),
            listOf(
                NxRow("Accent", secondary = deps.settings.accent.label, cycleable = true),
                NxRow("Secondary accent", secondary = deps.settings.secondaryAccent.label, cycleable = true),
                NxRow("Continue"),
            ),
        )
        OnboardingStep.DONE -> Triple("Ready, player one", listOf(
            "Your library is set up and the cartridges are blown clean.",
            "Every feature is explained at nxredux.com.",
        ), listOf(NxRow("Press Start")))
    }

    /** A or a tap on onboarding row [index]: that step's choice. */
    fun onboardingRow(index: Int) {
        val s = _screen.value as? Screen.Onboarding ?: return
        when (s.step) {
            OnboardingStep.WELCOME -> { onboarding.startTextSize(); showOnboarding() }
            OnboardingStep.TEXT_SIZE -> if (index < s.rows.lastIndex) stepOnboardingValue(onboarding.step, index, 1)
                else if (deps.builtInSecondScreen()) { onboarding.startBottomScreen(); showOnboarding() }   // the bottom screen question, then the folders
                else startFolderSteps()
            OnboardingStep.BOTTOM_SCREEN -> {
                if (index == 0) deps.settings.bottomScreen = BottomScreen.MAIN   // Skip keeps Auto
                startFolderSteps()
            }
            OnboardingStep.HANDHELD -> {
                onboarding.context = onboarding.context.copy(handheld = index == 0)
                onboardingNext()
            }
            OnboardingStep.SAVE_FORMATS -> if (index < 3) stepOnboardingValue(OnboardingStep.SAVE_FORMATS, index, 1) else onboardingNext()
            OnboardingStep.IMPORT_SAVES -> when {
                onboardingImporting != null -> Unit   // the rows wait for the running import
                onboardingImported is ImportResult.Done -> if (index == s.rows.lastIndex) onboardingNext()   // a skipped file: inert
                index < 3 -> onboardingImport(listOf(ImportSource.FOLDER, ImportSource.ZIP, ImportSource.RETROARCH)[index])
                else -> onboardingNext()
            }
            OnboardingStep.CUSTOMISE -> { onboarding.context = onboarding.context.copy(customise = index == 0); onboardingNext() }
            OnboardingStep.PAD -> if (index < s.rows.lastIndex) stepOnboardingValue(onboarding.step, index, 1) else onboardingNext()
            OnboardingStep.TITLE, OnboardingStep.ACCENT -> if (index < s.rows.lastIndex) stepOnboardingValue(onboarding.step, index, 1) else onboardingNext()
            OnboardingStep.TAB_HOME, OnboardingStep.TAB_CONSOLES, OnboardingStep.TAB_COLLECTIONS, OnboardingStep.TAB_TOOLS,
            OnboardingStep.GAME_LISTS -> if (index < s.rows.lastIndex) stepOnboardingLayoutRow(index, 1) else onboardingNext()
            OnboardingStep.ARTWORK -> { if (index == 0) artworkScreens?.fetchInBackground(); onboardingNext() }
            OnboardingStep.RA_SIGN_IN -> when {
                s.prompt -> submitOnboardingSignIn(s)
                index == 0 && deps.achievements != null && !onboardingSigningIn ->
                    _screen.value = s.copy(prompt = true, draftUser = deps.settings.raUsername, draftPassword = "")
                index == 0 -> Unit   // a sign-in is still running
                else -> onboardingNext()
            }
            OnboardingStep.RA_DOWNLOAD -> { if (index == 0) deps.achievements?.prefetchAll(); onboardingNext() }
            OnboardingStep.SYNC_OFFER -> if (index == 0) {
                val sync = deps.deviceSync ?: return onboardingNext()
                onboardingSync = true
                sync.open()
                _screen.value = Screen.DeviceSync
            } else onboardingNext()
            else -> onboardingNext()
        }
    }

    /** The layout a layout page sets, as it is now. */
    private fun onboardingLayout(step: OnboardingStep): MenuStyle =
        ONBOARDING_TAB_STEPS[step]?.let { MenuStyles.styleOf(deps.settings.menuStyles, it) } ?: deps.settings.gameListStyle

    /**
     * A layout page's value rows, the options its picture shows: the layout, then Home's stats, a tab Carousel's
     * orientation, or a game list Carousel's or Backdrop's orientation and, standing on end, its alignment.
     */
    private fun onboardingLayoutKinds(step: OnboardingStep): List<SettingsKind> {
        val s = deps.settings
        val category = ONBOARDING_TAB_STEPS[step]
        return listOfNotNull(
            if (category == null) SettingsKind.GAME_LIST_STYLE else SettingsKind.MENU_STYLE,
            SettingsKind.HOME_STATS.takeIf { category == MenuCategory.HOME },
            SettingsKind.MENU_ORIENTATION.takeIf { category != null && category != MenuCategory.HOME && MenuStyles.styleOf(s.menuStyles, category) == MenuStyle.CAROUSEL },
            SettingsKind.GAME_LIST_ORIENTATION.takeIf { category == null && MenuStyles.hasOrientation(s.gameListStyle) },
            SettingsKind.GAME_LIST_VERTICAL_ALIGNMENT.takeIf { category == null && gameListsShot().vertical },
        )
    }

    /** A layout page's rows: its value rows ([onboardingLayoutKinds]), then Continue. */
    private fun onboardingLayoutRows(step: OnboardingStep): List<NxRow> {
        val s = deps.settings
        val category = ONBOARDING_TAB_STEPS[step]
        val name = category?.label ?: "Game lists"
        return onboardingLayoutKinds(step).map { kind ->
            when (kind) {
                SettingsKind.HOME_STATS -> NxRow("Home stats", secondary = if (s.menuHomeStats) "Show" else "Hide", cycleable = true)
                SettingsKind.MENU_ORIENTATION -> NxRow("$name orientation", secondary = orientationLabel(MenuStyles.orientationOf(s.menuOrientations, category!!)), cycleable = true)
                SettingsKind.GAME_LIST_ORIENTATION -> NxRow("Game lists orientation", secondary = orientationLabel(s.gameListOrientation), cycleable = true)
                SettingsKind.GAME_LIST_VERTICAL_ALIGNMENT -> NxRow("Game lists alignment", secondary = verticalAlignmentLabel(s.gameListVerticalAlignment), cycleable = true)
                else -> NxRow("$name layout", secondary = menuStyleLabel(onboardingLayout(step)), cycleable = true)
            }
        } + NxRow("Continue")
    }

    /** A layout page's picture: the tab or the game lists as their rows set them; null on the other steps. */
    private fun onboardingShot(step: OnboardingStep): LayoutShot? {
        if (step == OnboardingStep.GAME_LISTS) return gameListsShot()
        val c = ONBOARDING_TAB_STEPS[step] ?: return null
        val style = MenuStyles.styleOf(deps.settings.menuStyles, c)
        val vertical = style == MenuStyle.CAROUSEL && c != MenuCategory.HOME && MenuStyles.orientationOf(deps.settings.menuOrientations, c) == MenuOrientation.VERTICAL
        return LayoutShot.Tab(c, style, stats = c != MenuCategory.HOME || deps.settings.menuHomeStats, vertical = vertical, art = consoleShotArt(c))
    }

    /**
     * A layout page's value row [row] stepped by [by]: the layout ([stepOnboardingLayout]), or the option on that row in
     * place, the same setting Settings steps ([settingValue], through a bare row carrying the page's tab), so the
     * picture follows.
     */
    private fun stepOnboardingLayoutRow(row: Int, by: Int) {
        val step = onboarding.step ?: return
        when (val kind = onboardingLayoutKinds(step).getOrNull(row) ?: return) {
            // The layout is onboarding's own: it resets the orientation (and alignment) with it, from its own list.
            SettingsKind.MENU_STYLE, SettingsKind.GAME_LIST_STYLE -> return stepOnboardingLayout(by)
            else -> settingValue(SettingsRow(kind, "", category = ONBOARDING_TAB_STEPS[step]))?.step(by)
        }
        showOnboarding(row)
    }

    /** A layout page's layout stepped by [by] (-1: the one before): applied at once (its orientation reset), the page on its row. */
    private fun stepOnboardingLayout(by: Int) {
        val step = onboarding.step ?: return
        val category = ONBOARDING_TAB_STEPS[step]
        if (category == null) {
            deps.settings.gameListStyle = ONBOARDING_GAME_LIST_STYLES.stepFrom(deps.settings.gameListStyle, by)
            deps.settings.gameListOrientation = MenuStyles.DEFAULT_ORIENTATION
            deps.settings.gameListVerticalAlignment = VerticalListAlignment.DEFAULT
        } else {
            deps.settings.menuStyles = deps.settings.menuStyles + (category to MenuStyles.cycleMainMenu(MenuStyles.styleOf(deps.settings.menuStyles, category), by))
            deps.settings.menuOrientations = deps.settings.menuOrientations - category
            if (category == MenuCategory.HOME) rebuildHomeTab()
        }
        showOnboarding(0)
    }

    /** The library's game count, for the artwork and achievement steps' lines. */
    private fun onboardingGameCount(): Int = systemsCache.sumOf { it.gameCount }

    /** The sign-in prompt's typing (as the RetroAchievements settings' prompt). */
    fun onboardingDraftUser(text: String) { (_screen.value as? Screen.Onboarding)?.takeIf { it.prompt }?.let { _screen.value = it.copy(draftUser = text) } }
    fun onboardingDraftPassword(text: String) { (_screen.value as? Screen.Onboarding)?.takeIf { it.prompt }?.let { _screen.value = it.copy(draftPassword = text) } }

    /** The prompt's confirm: both filled, the sign-in runs ([onRaIdleOnboarding] takes its end); else it stays. */
    private fun submitOnboardingSignIn(s: Screen.Onboarding) {
        val c = deps.achievements ?: return
        if (s.draftUser.isBlank() || s.draftPassword.isBlank() || c.busy) return
        onboardingSigningIn = true
        showOnboarding(0)
        c.login(s.draftUser.trim(), s.draftPassword)
    }

    /** A sign-in from onboarding ended: signed in, on to the download offer; else the step again with the error. */
    private fun onRaIdleOnboarding(status: RaStatus) {
        if (!onboardingSigningIn) return
        onboardingSigningIn = false
        // Judged by the sign-in's own result: a token kept from before stays through a failed one.
        val signedIn = status.lastError == null && deps.settings.raToken != null
        // Even when the user skipped on meanwhile: the download offer becomes a step (behind them, B reaches it).
        if (signedIn) onboarding.context = onboarding.context.copy(signedIn = true)
        if (onboarding.step != OnboardingStep.RA_SIGN_IN) return
        if (signedIn) onboardingNext() else showOnboarding(0, error = status.lastError ?: "Sign-in failed")
    }

    /** On to the next step, saved for a resume; past DONE onboarding ends ([finishOnboarding]). */
    private fun onboardingNext() {
        refreshPadContext()
        onboardingImported = null
        val next = onboarding.next() ?: return finishOnboarding()
        deps.settings.onboardingStep = next.name
        showOnboarding()
    }

    /**
     * The Bottom screen step's button on the second screen was tapped: the second screen is the bottom one. Only while the
     * step shows.
     */
    fun onboardingBottomScreenOnSecond() {
        if (onboarding.step != OnboardingStep.BOTTOM_SCREEN || (_screen.value as? Screen.Onboarding)?.step != OnboardingStep.BOTTOM_SCREEN) return
        deps.settings.bottomScreen = BottomScreen.SECOND
        startFolderSteps()
    }

    /** The welcome pages are answered: on to first setup's folder steps. */
    private fun startFolderSteps() {
        welcomeDone = true
        onboarding.finish()
        showPickFolder()
    }

    /** B: the step before; WELCOME stays, the first post-folder step goes back to the ROMs folder step. */
    private fun onboardingBack() {
        // A first run's first step after the folders: back to the ROMs folder step (going on again comes back here, the saved step kept).
        if (onboarding.isFirstMain()) { onboarding.finish(); return showAddFirstExtra() }
        val step = onboarding.back() ?: return
        onboardingImported = null
        deps.settings.onboardingStep = step.takeIf { it != OnboardingStep.WELCOME && it != OnboardingStep.TEXT_SIZE }?.name
        showOnboarding()
    }

    /** B on first setup's home pick: the All files step while access is declared and not granted, else the text size question. */
    private fun backFromHomePick() {
        val access = deps.allFilesAccess
        if (access != null && access.declared && !access.granted()) {
            showPermissions()
        } else backToWelcome()
    }

    /** First setup's first folder step (All files access or the home pick), back: the bottom screen question where asked, else the text size question. */
    private fun backToWelcome() {
        welcomeDone = false
        if (deps.builtInSecondScreen()) onboarding.startBottomScreen() else onboarding.startTextSize()
        showOnboarding()
    }

    /**
     * First setup's home page after the pick's scan: what [home] holds, in `Roms/` and outside it (its outside games
     * found as an extra folder's are). A home with no game skips it, to the ROMs folder offer.
     */
    private fun showHomePage(home: ContentRef) {
        _screen.value = Screen.Loading
        scope.launch {
            val outside = try { scanner.scanOutsideRoms(home, deps.settings.defaultTags) } catch (e: CancellationException) { throw e } catch (e: Exception) { emptyList() }
            val index = library ?: return@launch showAddFirstExtra()
            homeOutside = outside
            val summary = homeSummary(index, outside).copy(split = splitFolders(index).size)
            if (summary.games == 0) { homePage = null; return@launch showAddFirstExtra() }
            showHomeSummary(summary)
        }
    }

    /** The home page with [summary] (after a move: what the home held before it, and the move's [result]). */
    private fun showHomeSummary(summary: com.nxredux.mobile.library.FolderSummary, result: MoveResult? = null) {
        val name = home()?.let { deps.source.displayName(it) } ?: ""
        val s = Screen.FolderSummary(Recents.HOME, name, name, summary, move = result == null && homeTidy(summary), result = result)
        homePage = s
        _screen.value = s
        _selection.value = ListSelection(0, folderSummaryRows(s).size)
    }

    /** Whether the home page has anything to move: games outside `Roms/`, or a console split over several folders. */
    private fun homeTidy(summary: com.nxredux.mobile.library.FolderSummary) = summary.outside > 0 || summary.split > 0

    /**
     * The home page's move: its outside games into `Roms/` (the folders they empty removed), then each split console's
     * other folders merged into its main one, every line naming a merged game rewritten (Recents, pins, hidden games,
     * collections), then a rescan and the result.
     */
    private fun moveHomeOutside(s: Screen.FolderSummary) {
        val home = home() ?: return
        val index = library ?: return
        moveCancelled = false
        _screen.value = s.copy(progress = 0 to 0)
        _selection.value = ListSelection(0, 0)
        scope.launch {
            val mover = RomMover(deps.source, home)
            val progress = { done: Int, total: Int -> (_screen.value as? Screen.FolderSummary)?.let { _screen.value = it.copy(progress = done to total) }; Unit }
            // Each merged game's old line → new, filled as it moves: whatever ends the move (a stop, an error, the app
            // closing), every line naming a game that did move is rewritten to it.
            val moved = LinkedHashMap<String, String>()
            // The lines of the games moved so far, rewritten every few seconds too: a process killed mid-merge leaves
            // only the last few seconds' games at their old paths, not all of them.
            val rewritten = HashSet<String>()
            suspend fun flushLines() {
                val fresh = moved.filterKeys { it !in rewritten }
                if (fresh.isEmpty()) return
                runCatching { rewriteHomeLines(fresh) }.onSuccess { rewritten += fresh.keys }
            }
            val flusher = launch { while (true) { delay(LINES_FLUSH_MS); withContext(NonCancellable) { flushLines() } } }
            var outside = MoveResult()
            val result = try {
                val items = mover.planLoose(homeOutside, index)
                val movedItems = HashSet<MoveItem>()
                outside = mover.run(items, progress, onMoved = { movedItems += it }) { moveCancelled }
                // Folders where a game (or a file nothing placed) stayed are never tried: a wrong empty listing must not delete them.
                val keep = homeOutside.filter { f -> movedItems.none { it.relativeDir == f.relativeDir && (it.subdir != null || f.entry.name in it.files) } }
                mover.pruneEmpty(home, homeOutside.map { it.relativeDir }, keep = keep.map { it.relativeDir })
                val merged = if (outside.cancelled) MoveResult() else FolderMerge(deps.source, home).merge(index, splitFolders(index), moved, progress) { moveCancelled }.first
                MoveResult(outside.moved + merged.moved, outside.clashed + merged.clashed, outside.failed + merged.failed, outside.cancelled || merged.cancelled)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // What moved is counted; the rest (outside and merge) stays where it was.
                outside.copy(moved = outside.moved + moved.size, failed = outside.failed + 1)
            } finally {
                flusher.cancel()
                withContext(NonCancellable) { flushLines() }
            }
            homeOutside = emptyList()
            scanNow(home, then = { showHomeSummary(s.summary, result) })
        }
    }

    /** Every saved line naming a game the merge [moved] (old path → new) follows it: Recents, pins, hidden games, collections. */
    private suspend fun rewriteHomeLines(moved: Map<String, String>) {
        recentsLock.withLock {
            val recents = deps.userData.readRecents()
            val next = recents.map { r -> moved[r.path]?.let { r.copy(path = it) } ?: r }
            if (next != recents) deps.userData.writeRecents(next)
        }
        val pins = deps.userData.readPins()
        pins.map { p -> moved[p.path]?.let { p.copy(path = it) } ?: p }.takeIf { it != pins }?.let { deps.userData.writePins(it) }
        val hidden = deps.userData.readHidden()
        rewriteLines(hidden, moved).takeIf { it != hidden }?.let { deps.userData.writeHidden(it) }
        for (name in collections.names()) {
            val lines = collections.readOrNull(name) ?: continue
            val next = rewriteLines(lines, moved)
            if (next != lines) collections.writeMerged(name, next, lines)
        }
    }

    /** First setup's ROMs folder offer (after the home page, or B from the handheld question): Add folder, Skip. */
    private fun showAddFirstExtra() {
        _screen.value = Screen.AddFirstExtra
        _selection.value = ListSelection(0, 2)
    }

    /**
     * First setup's summary of the folder [extraId] just added, from the library its scan stored, on Continue. After a
     * move, [before] (what the folder held) stays the summary, so the page still says what was found.
     */
    private fun showFolderSummary(extraId: String, result: MoveResult? = null, before: com.nxredux.mobile.library.FolderSummary? = null) {
        val index = library ?: return afterFolderSteps { load() }
        val extra = deps.settings.extraFolders.firstOrNull { it.id == extraId }
        val name = extra?.let { deps.source.displayName(it.ref) } ?: ""
        val home = home()?.let { deps.source.displayName(it) } ?: ""
        val summary = before ?: folderSummary(index, extraId)
        val s = Screen.FolderSummary(extraId, name, home, summary, move = result != null, result = result)
        _screen.value = s
        val rows = folderSummaryRows(s)
        _selection.value = ListSelection(0, rows.size)   // on the first row: the Move option where it is offered, else Continue
    }

    /** A or a tap on the summary's row [index]: Move toggles; Continue moves first when it is on, else goes on. */
    private fun folderSummaryRow(s: Screen.FolderSummary, index: Int) {
        if (s.progress != null) return
        when (folderSummaryRows(s).getOrNull(index)?.kind) {
            SummaryRowKind.MOVE -> { _screen.value = s.copy(move = !s.move); return }
            SummaryRowKind.CONTINUE, null -> Unit
        }
        if (s.extraId == Recents.HOME) {
            if (s.move && s.result == null && homeTidy(s.summary)) moveHomeOutside(s) else showAddFirstExtra()
            return
        }
        if (s.move && s.result == null && s.summary.games > s.summary.unassigned) return moveSummaryGames(s)
        afterFolderSteps { load() }
    }

    /** The summary's move: [s]'s placed games into the home's Roms, its progress on the page, then a rescan and the result. */
    private fun moveSummaryGames(s: Screen.FolderSummary) {
        val home = home() ?: return
        val index = library ?: return
        moveCancelled = false
        _screen.value = s.copy(progress = 0 to 0)
        _selection.value = ListSelection(0, 0)
        scope.launch {
            val mover = RomMover(deps.source, home)
            // An unexpected error is a result too (nothing moved past it), never a crash of the app's scope.
            val result = try {
                val items = mover.plan(index, s.extraId)
                mover.run(items, { done, total -> (_screen.value as? Screen.FolderSummary)?.let { _screen.value = it.copy(progress = done to total) } }) { moveCancelled }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                MoveResult(failed = s.summary.games - s.summary.unassigned)
            }
            scanNow(home, then = {
                // Emptied by the move: the folder is no longer a source.
                val extra = deps.settings.extraFolders.firstOrNull { it.id == s.extraId }
                if (extra != null && library?.let { folderSummary(it, s.extraId).games } == 0) {
                    deps.settings.extraFolders = deps.settings.extraFolders - extra
                    deps.folderPicker.forget(extra.ref)
                    libraryDirty = true
                }
                showFolderSummary(s.extraId, result, before = s.summary)
            })
        }
    }

    /** First setup is on: the welcome was not finished by an earlier run. */
    private fun inFirstSetup(): Boolean = deps.settings.onboardingDone == false

    /** How many folder steps first setup has before HANDHELD: All files (where shown or still to ask), home, ROMs, Android games (once shown). */
    private fun setupFolderSteps(): Int {
        if (!inFirstSetup()) return 0
        val access = deps.allFilesAccess
        val allFiles = allFilesStepShown || (access != null && access.declared && !access.granted() && !deps.settings.allFilesAsked)
        val pick = if (mediaHomeTaken || !homePickShown()) 0 else 1   // no home pick on the media home
        return (if (allFiles) 1 else 0) + 1 + pick + (if (homePage != null) 1 else 0) + (if (addedExtraId != null) 1 else 0) + (if (androidStepShown) 1 else 0)
    }

    /**
     * A folder step's page title and whether B goes back from it: "Setup · Step n of m" (and back) in first setup; after
     * it (a lost home's pick) just the page's name, and B has nowhere to go.
     */
    fun setupChrome(): Pair<String, Boolean> {
        val screen = _screen.value
        if (!inFirstSetup()) return (if (screen is Screen.AddFirstExtra) "ROM folders" else "Home folder") to (screen is Screen.AddFirstExtra)
        val access = deps.allFilesAccess
        val allFiles = allFilesStepShown || (access != null && access.declared && !access.granted() && !deps.settings.allFilesAsked)
        val home = if (homePage != null) 1 else 0   // the home page, between the pick and the ROMs folder offer
        val pick = if (mediaHomeTaken || !homePickShown()) 0 else 1   // no home pick on the media home
        val at = (if (allFiles) 1 else 0) + when (screen) {
            is Screen.PermissionsStep -> 0
            Screen.PickFolder -> 1
            Screen.AddFirstExtra -> 1 + pick + home
            is Screen.FolderSummary -> if (screen.extraId == Recents.HOME) 1 + pick else 2 + pick + home
            else -> 2 + pick + home + (if (addedExtraId != null) 1 else 0)
        }
        val total = setupFolderSteps() + onboardingSteps(onboarding.context).size
        return "Setup · Step $at of $total" to true
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
            is Screen.FolderSummary -> folderSummaryRow(_screen.value as Screen.FolderSummary, index)
            else -> Unit
        }
    }

    /** Every onboarding flag back to a run's start (a new run, or onboarding ended or left), so none leaks into normal use. */
    private fun resetOnboardingRun() {
        onboardingSync = false
        _syncSetup.value = null
        onboardingSynced = null
        onboardingSigningIn = false
        allFilesStepShown = false
        androidStepShown = false
        addedExtraId = null
        homePage = null
        homeOutside = emptyList()
    }

    /** Onboarding done: the menu on Home, its tabs rebuilt for the layout and options picked. */
    private fun finishOnboarding() {
        onboarding.finish()
        resetOnboardingRun()
        deps.settings.onboardingDone = true
        deps.settings.onboardingStep = null
        // A new install has nothing to upgrade, unless data waits to move. Before any load can ask the gate (a new install's
        // private root is a few files to walk).
        deps.storageUpgrade?.markIfEmpty()
        scope.launch {
            rebuildMainRows()
            showSystemsNow(MenuCategory.HOME)
        }
    }

    /** A pad press on an onboarding step: UP / DOWN move (wrapping), A chooses, B goes back. */
    private fun onOnboardingButton(s: Screen.Onboarding, button: NxButton) {
        if (s.prompt) {   // the sign-in prompt: A signs in, B closes it
            when (button) {
                NxButton.A -> submitOnboardingSignIn(s)
                NxButton.B -> _screen.value = s.copy(prompt = false, draftPassword = "")
                else -> Unit
            }
            return
        }
        // The last page: START goes as Press Start does.
        if (s.step == OnboardingStep.DONE && button == NxButton.START) return onboardingRow(0)
        when (button) {
            NxButton.UP -> _selection.value = _selection.value.up(wrap = true)
            NxButton.DOWN -> _selection.value = _selection.value.down(wrap = true)
            NxButton.LEFT, NxButton.RIGHT -> when (s.step) {
                OnboardingStep.SAVE_FORMATS -> stepOnboardingValue(OnboardingStep.SAVE_FORMATS, _selection.value.index, if (button == NxButton.LEFT) -1 else 1)
                OnboardingStep.PAD, OnboardingStep.TITLE, OnboardingStep.ACCENT, OnboardingStep.TEXT_SIZE -> if (_selection.value.index < s.rows.lastIndex) stepOnboardingValue(onboarding.step, _selection.value.index, if (button == NxButton.LEFT) -1 else 1)
                in ONBOARDING_LAYOUT_STEPS -> if (_selection.value.index < s.rows.lastIndex) stepOnboardingLayoutRow(_selection.value.index, if (button == NxButton.LEFT) -1 else 1)
                else -> Unit
            }
            NxButton.A -> onboardingRow(_selection.value.index)
            NxButton.B -> onboardingBack()
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
                val read = try { deps.gameTime?.byPath().orEmpty() } catch (e: CancellationException) { throw e } catch (e: Exception) { null }
                val raRead = try { deps.achievements?.progressByPath().orEmpty() } catch (e: CancellationException) { throw e } catch (e: Exception) { null }
                try { deps.achievements?.hashesByPath()?.let { ra.raHashes = it } } catch (e: CancellationException) { throw e } catch (e: Exception) { }
                ra.followRaPanel(force = true)   // the panel's game may have data now (a download just ended)
                ra.followRaDetails(force = true)
                val times = read?.also { lastTimes = it } ?: lastTimes
                val raByPath = raRead?.also { lastRa = it } ?: lastRa
                _gameInfo.value = (times.keys + raByPath.keys).associateWith { p -> GameInfo(times[p]?.lastPlayedAt, times[p]?.totalMs, raByPath[p]) }
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
        if (minimal) _minimalOpening.value = null   // the faded page is gone: nothing to clear later
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
        showMenu(screen, select?.let { screen.rows.indexOfFirst(it) } ?: -1)
    }

    /** L1 / R1 on the main menu: the previous / next tab, wrapping; each tab keeps its row. */
    private fun switchTab(step: Int) {
        val s = _screen.value as? Screen.Systems ?: return
        if (s.menu != null || s.search != null || s.tabs.size < 2) return
        selectTab((s.tab + step).mod(s.tabs.size))
    }

    /** A tap on a tab, or [switchTab]. Landing on Tools refreshes what its pages show. */
    fun selectTab(index: Int) {
        if (loader.active) return   // a screen is loading: only B (cancel) acts, as for L1 / R1
        val s = _screen.value as? Screen.Systems ?: return
        if (s.menu != null || s.search != null) return
        val tab = s.tabs.getOrNull(index) ?: return
        rememberMenuSelection()
        if (tab.category == MenuCategory.TOOLS) { deps.cheatDb?.refresh(); deps.achievements?.refresh() }
        showMenu(menuScreen(tab.category))
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
        // The one-time storage upgrade (v0.3.0 and earlier) comes before anything else; a load while it is up leaves it.
        if (upgradeOn || upgradeDue()) { if (!upgradeOn) startStorageUpgrade(); return }
        if (upgradeCase() == UpgradeCase.QUIET) deps.storageUpgrade?.startQuietMove()   // Case A: in the background, never mid-game
        // First setup's folder steps come first even with a media home to fall back to; a saved step means they were reached.
        val setupFirst = deps.settings.treeRef == null && deps.settings.onboardingDone == false && deps.settings.onboardingStep == null && !mediaHomeTaken
        if (home() == null || setupFirst) {
            if (deps.settings.onboardingDone == false && !welcomeDone && onboarding.step == null) { onboarding.startWelcome(); showOnboarding(); return }
            showPickFolder(); return
        }
        libraryDirty = false
        _screen.value = Screen.Loading
        scope.launch {
            convertToFiles()   // before the index is read, so a converted home or extra scans once
            val root = home() ?: return@launch
            mediaHomeNotice(root)
            countCache.load()   // before the first menu build: the collections listed next show their stored counts
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
        try { ToolPins.decode(deps.privateFiles.read(ToolPins.FILE)) } catch (e: CancellationException) { throw e } catch (e: Throwable) { emptyList() }

    /** Writes [keys] as [ToolPins.FILE] off the main thread, then keeps them; false (nothing changed) when the write fails. */
    private suspend fun writeToolPins(keys: List<String>): Boolean {
        try { withContext(deps.compute) { deps.privateFiles.write(ToolPins.FILE, ToolPins.encode(keys)) } } catch (e: CancellationException) { throw e } catch (e: Throwable) { return false }
        toolPins = keys
        return true
    }

    /** The index built for [root] and the current extras and default tags: the in-memory one, else the stored copy. */
    private suspend fun fittingIndex(root: ContentRef): LibraryIndex? = indexLock.withLock {
        val fitsNow = { ix: LibraryIndex -> ix.fits(root, deps.settings.extraFolders, deps.settings.defaultTags) }
        library?.takeIf(fitsNow) ?: readStoredIndex()?.takeIf(fitsNow)?.also { library = it }
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
            val dropped = ArrayList<ExtraFolder>()
            // Systems is shown from it below, which sets the playable cache: no refresh here.
            val fresh = mutateIndex(refresh = null) { current ->
                val previous = current ?: readStoredIndex()   // which extras were offline: one just mounted gets a retry
                stamped(scanner.scan(root, deps.settings.extraFolders, deps.settings.defaultTags, previous) { extra, e ->
                    if (isGone(e)) dropped += extra
                }, previous).also { lateMountHere = it.lateMount }
            }!!   // a scan is always a new index
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
                refreshCollectionsFlag()   // the scan just walked the tree, so the first show can carry the row
                showSystems(it.playableSystems(readHidden()))
                then?.invoke(systemsCache)
            }
            .onFailure { e ->
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
     * [index] with the time its scan finished ([LibraryIndex.scannedAt]) and each game's first-seen date ([withFirstSeen]),
     * on [AppDeps.compute]: the pass walks the whole index.
     */
    private suspend fun stamped(index: LibraryIndex, previous: LibraryIndex?): LibraryIndex {
        val now = deps.now?.invoke()
        return withContext(deps.compute) { index.withFirstSeen(previous, now).let { if (now != null) it.copy(scannedAt = now) else it } }
    }

    /**
     * Writes [index] (call under [indexLock]). A failed write deletes the stored copy so the next start scans instead
     * of trusting an older index that may still fit the settings. The platform store logs its own failures.
     */
    private suspend fun persist(index: LibraryIndex) {
        val ok = try {
            deps.indexStore.write(LibraryIndexCodec.encode(index, deps.compute))   // the whole index: encoded off the main thread
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            false
        }
        if (ok) return
        try {
            deps.indexStore.delete()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Unit
        }
    }

    /** The stored index, or null when there is none, it cannot be read, or it does not decode (garbage, another version). */
    private suspend fun readStoredIndex(): LibraryIndex? = try {
        deps.indexStore.read()?.let { LibraryIndexCodec.decode(it, deps.compute) }   // decoded off the main thread
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        null
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
                indexLock.withLock {
                    library = null
                    try { deps.indexStore.delete() } catch (d: CancellationException) { throw d } catch (d: Throwable) { Unit }
                }
                _error.value = "Could not read the home folder: ${e.message ?: e::class.simpleName}"
                showPickFolder()
            }
            return
        }
        homeReadable()
        ensureSkeleton(root)
        refreshCollectionsFlag()   // the probe showed the tree readable: the Collections row may appear now
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
    private suspend fun probe(root: ContentRef): Throwable? = try {
        deps.source.listChildren(root)
        null
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        e
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
        runCatching { Skeleton.ensure(deps.source, root) }.onFailure {
            if (it is CancellationException) throw it
            if (it is ListingUnavailable) skeletonFor = null   // cut short before any create: the next scan tries again
        }
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

    /**
     * Library's All files access row: the switch [granted]; off while something needs it, a warning line saying what
     * (the saved home read from the media folder [root] instead, else ROM folders that can't be read) in place of its
     * description, so the app need not say it with a notice at every start.
     */
    private fun allFilesRow(granted: Boolean, root: ContentRef?): SettingsRow {
        val saved = deps.settings.treeRef
        val note = when {
            granted -> null
            root != null && saved != null && root == deps.homes?.media && FileRefs.isFile(saved) && saved != root ->
                "Off: your home folder can't be read, so ${mediaPath(root)} is used"
            deps.settings.extraFolders.any { fileAccessOff(it.ref) } -> "Off: some ROM folders can't be read"
            else -> null
        }
        return SettingsRow(SettingsKind.ALL_FILES, "All files access", if (granted) "On" else "Off", warning = note != null, note = note)
    }

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

    /** All files access is on and the list-style picker is there: every pick goes through it. */
    /** Whether a folder pick goes to the app's own picker (All files access), not Android's. */
    fun filePickerOn(): Boolean = filePicker != null && deps.allFilesAccess?.granted() == true

    /**
     * The home pick: the list-style picker with All files access. Without it no picker home is saved any more (the
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
        deps.allFilesAccess ?: return
        if (upgradeOn) { upgradeAccessChanged(); return }   // the upgrade screen answers it, never a library reload
        if (_screen.value is Screen.PermissionsStep) { showPermissions(keep = true); return }   // back from a prompt: the rows show its answer
        if ((_screen.value as? Screen.Settings)?.section == SettingsSection.LIBRARY) refreshSettings()
        // Granted meanwhile: SAF folders on external storage become paths. The main menu reloads at once (rescanning, as the
        // index no longer fits); any other page leaves it to the next load. A load in flight converts first itself. The
        // same when the home itself changed with the access (the saved one back, or the media one in its place).
        if (_screen.value is Screen.Loading) return
        scope.launch {
            val converted = deps.allFilesAccess?.granted() == true && convertToFiles()
            val switched = library?.let { it.home != home()?.value } == true
            if (!converted && !switched) return@launch
            val now = _screen.value
            if (now is Screen.Systems && now.menu == null && now.search == null) load() else libraryDirty = true
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
        val first = (old == null && inFirstSetup()) || _screen.value is Screen.PickFolder
        val fromLibrary = if (first) null else rememberLibraryPage()
        // The data home this change leaves (the full-access folder, or the media one): its saves may be copied over.
        val copyFrom = home()?.takeIf { !first && copyOffered(it, ref) }
        deps.settings.treeRef = ref
        if (first) firstSetup = true
        // First setup's folder steps, saved: a restart before they end comes back to them ([resumeFolderSteps]).
        if (first && deps.settings.onboardingDone == false) deps.settings.onboardingStep = FOLDER_STEPS
        if (old != null && old != ref && old !in extras) deps.folderPicker.forget(old)
        // Once the new home listed: the copy offer when it applies, else back to the Settings page.
        val after = { if (copyFrom != null) offerCopySaves(copyFrom, ref, fromLibrary) else fromLibrary?.let(::openSettings) }
        rescan(
            then = {   // runs only on a good scan, so a Roms-folder mispick never shows the offer
                if (first) {
                    showHomePage(ref)
                } else after()
            },
            kept = { after() },   // the same home again, not listing now
        )
    }

    /**
     * A deliberate home change from [from] to [to] with All files access, once the data lives in the home: both file
     * folders, not the same. The data is not moved, only offered to copy (the old home may be another build's, e.g. a dev
     * build's own folder, and must never be emptied).
     */
    private fun copyOffered(from: ContentRef, to: ContentRef): Boolean =
        deps.storageUpgrade != null && deps.allFilesAccess?.granted() == true && dataMoved() &&
            FileRefs.isFile(from) && FileRefs.isFile(to) && from != to

    /** Where [copySavesRow] goes when the offer is answered: the Settings page the home change came from. */
    private var copySavesThen: SettingsSection? = null

    private fun offerCopySaves(from: ContentRef, to: ContentRef, then: SettingsSection?) {
        copySavesThen = then
        _screen.value = Screen.CopySaves(from, to)
        _selection.value = ListSelection(0, 2)
    }

    // The one-time storage upgrade (installs from v0.3.0 and earlier without a full-access home; remove after v1.0).

    /** The upgrade screen is up (from its first page until the library loads after it): access changes drive it, not the library. */
    private var upgradeOn = false
    /** Its adoption or move is running: presses and access changes wait for it. */
    private var upgradeBusy = false
    /** The move failed this run: the library goes on with the private data; the screen comes back on the next start. */
    private var upgradeDeferred = false
    /** The saved home when the screen first showed (a picker home the pick may replace). */
    private var upgradeOld: ContentRef? = null

    /** Its first page is for a file home without All files access ([UpgradeCase.ASK_ACCESS]): Not now waits, it never declines. */
    private var upgradeWaits = false

    /** How the upgrade is due now ([StorageUpgradeGate]); null: not due, or no upgrade here. */
    private fun upgradeCase(): UpgradeCase? {
        val up = deps.storageUpgrade ?: return null
        return StorageUpgradeGate.due(deps.settings.onboardingDone, up.isDone(), deps.allFilesAccess?.granted() == true, deps.settings.treeRef, up.isWaiting())
    }

    /** The upgrade screen is due. QUIET needs no screen: the platform moves in the background ([load] starts it). */
    private fun upgradeDue(): Boolean {
        if (upgradeDeferred) return false
        val case = upgradeCase()
        return case == UpgradeCase.ASK || case == UpgradeCase.ASK_ACCESS
    }

    /** The upgrade's first page; where All files access cannot be had (not declared), the declined path at once. */
    private fun startStorageUpgrade() {
        upgradeOn = true
        upgradeOld = deps.settings.treeRef
        upgradeWaits = upgradeCase() == UpgradeCase.ASK_ACCESS
        if (deps.allFilesAccess?.declared != true) return if (upgradeWaits) upgradeWait() else upgradeDecline()
        showStorageUpgrade(Screen.StorageUpgrade(Screen.StorageUpgrade.Stage.EXPLAIN))
    }

    private fun showStorageUpgrade(s: Screen.StorageUpgrade) {
        _screen.value = s
        _selection.value = ListSelection(0, storageUpgradeRows(s).size)
    }

    /** A or a tap on the upgrade page's row [index]. */
    fun storageUpgradeRow(index: Int) {
        val s = _screen.value as? Screen.StorageUpgrade ?: return
        if (upgradeBusy) return
        if (s.error != null) return upgradeLater()
        when (s.stage) {
            Screen.StorageUpgrade.Stage.EXPLAIN -> when {
                index == 0 -> upgradeAllow()
                upgradeWaits -> upgradeWait()
                else -> upgradeDecline()
            }
            Screen.StorageUpgrade.Stage.PICK -> upgradePick()
            Screen.StorageUpgrade.Stage.DECLINED -> upgradeFinished()
        }
    }

    /** B on the upgrade page: the page is required, so EXPLAIN stays and PICK goes back to it; DECLINED and an error go on as OK. */
    private fun storageUpgradeBack(s: Screen.StorageUpgrade) {
        if (upgradeBusy) return
        when {
            s.error != null -> upgradeLater()
            s.stage == Screen.StorageUpgrade.Stage.PICK -> showStorageUpgrade(Screen.StorageUpgrade(Screen.StorageUpgrade.Stage.EXPLAIN))
            s.stage == Screen.StorageUpgrade.Stage.DECLINED -> upgradeFinished()
            else -> Unit
        }
    }

    /** Allow: granted already, on at once; else Android's page, and the answer comes back through [onAllFilesAccessChanged]. */
    private fun upgradeAllow() {
        if (deps.allFilesAccess?.granted() == true) upgradeGranted() else deps.allFilesAccess?.openSettings()
    }

    /** All files access changed while the upgrade is up: granted on its first page goes on; revoked on the pick goes back to it. */
    private fun upgradeAccessChanged() {
        val s = _screen.value as? Screen.StorageUpgrade ?: return
        if (upgradeBusy || s.error != null) return
        val granted = deps.allFilesAccess?.granted() == true
        when {
            s.stage == Screen.StorageUpgrade.Stage.EXPLAIN && granted -> upgradeGranted()
            s.stage == Screen.StorageUpgrade.Stage.PICK && !granted -> showStorageUpgrade(Screen.StorageUpgrade(Screen.StorageUpgrade.Stage.EXPLAIN))
            else -> Unit
        }
    }

    /**
     * Not now on a file home's page: nothing moves to the media home and nothing is marked. The data stays in the app,
     * and the quiet move runs into the file home once access is back.
     */
    private fun upgradeWait() {
        deps.storageUpgrade?.setWaiting()
        upgradeFinished()
    }

    /** Granted: the picker home becomes its path where it can ([convertToFiles]) and the data moves into it; else the pick. */
    private fun upgradeGranted() {
        upgradeBusy = true
        _screen.value = Screen.Loading
        scope.launch {
            convertToFiles()
            val home = deps.settings.treeRef
            // A file home that can't be read now (card out) waits: the quiet move runs once it is back.
            if (home != null && FileRefs.isFile(home) && deps.homes?.let { it.current() != home } == true) return@launch upgradeFinished()
            if (home != null && FileRefs.isFile(home)) return@launch upgradeMove(home) { upgradeFinished() }
            upgradeBusy = false
            showStorageUpgrade(Screen.StorageUpgrade(Screen.StorageUpgrade.Stage.PICK))
        }
    }

    /** The pick's page: the list-style picker on the home (with access on it is always there). */
    private fun upgradePick() {
        val p = filePicker ?: return
        if (filePickerOn()) p.open(FilePickMode.Home, null) { it?.let(::storageUpgradePicked) }
    }

    /**
     * The folder picked on the upgrade's pick page becomes the home and the data moves into it. A folder other than the
     * old picker home makes that one an extra first (its Bios and Collections copied into [ref], its keys rewritten). A
     * folder over an extra, or over the old home without being it, is refused (its files would list twice).
     */
    fun storageUpgradePicked(ref: ContentRef) {
        val up = deps.storageUpgrade ?: return
        if (!upgradeOn || upgradeBusy) return
        val old = upgradeOld?.takeIf { !FileRefs.isFile(it) }
        val adopt = old != null && Sources.mappedPath(old) != FileRefs.path(ref).trimEnd('/')
        // The extra on this folder's own Roms/ is the old home adopted by an earlier try: the home takes it back.
        val back = deps.settings.extraFolders.firstOrNull { e ->
            (if (FileRefs.isFile(e.ref)) FileRefs.path(e.ref).trimEnd('/') else Sources.mappedPath(e.ref)) == FileRefs.path(ref).trimEnd('/') + "/Roms"
        }
        val clash = deps.settings.extraFolders.filter { it != back }.map { it.ref }.firstOrNull { Sources.overlaps(it, ref) } ?: old?.takeIf { adopt && Sources.overlaps(it, ref) }
        if (clash != null) {
            _error.value = "That folder overlaps ${deps.source.displayName(clash)}; files would list twice."
            return
        }
        upgradeBusy = true
        _screen.value = Screen.Loading
        scope.launch {
            if (back != null) {
                val restored = try { up.restoreHomeKeys(back.id) } catch (e: CancellationException) { throw e } catch (e: Exception) { false }
                if (!restored) return@launch upgradeFailed()
                deps.settings.extraFolders = deps.settings.extraFolders.filter { it != back }
            }
            if (adopt) {
                val extra = upgradeAdopt(old!!, ref).getOrElse { return@launch upgradeFailed() }
                extra?.let { deps.settings.extraFolders = deps.settings.extraFolders + it }
            }
            deps.settings.treeRef = ref
            upgradeMove(ref) { upgradeFinished() }
        }
    }

    /** Not now (or no All files access here): the picker home becomes an extra, and the data moves into the media home. */
    private fun upgradeDecline() {
        val up = deps.storageUpgrade ?: return
        upgradeBusy = true
        _screen.value = Screen.Loading
        scope.launch {
            val old = deps.settings.treeRef
            if (old != null && !FileRefs.isFile(old)) {
                val extra = upgradeAdopt(old, null).getOrElse { return@launch upgradeFailed() }
                extra?.let { deps.settings.extraFolders = deps.settings.extraFolders + it }
                deps.settings.treeRef = null
            }
            deps.settings.allFilesAsked = true
            val media = deps.homes?.media ?: return@launch upgradeFailed()
            upgradeMove(media) {
                upgradeBusy = false
                showStorageUpgrade(Screen.StorageUpgrade(Screen.StorageUpgrade.Stage.DECLINED, folder = mediaPath(media)))
            }
        }
    }

    /** [StorageUpgradePlatform.adoptPickerHome]: the extra (null: the old home is gone, nothing to adopt), or its failure. */
    private suspend fun upgradeAdopt(old: ContentRef, into: ContentRef?): Result<ExtraFolder?> {
        val up = deps.storageUpgrade ?: return Result.success(null)
        return try { Result.success(up.adoptPickerHome(old, into)) } catch (e: CancellationException) { throw e } catch (e: Exception) { Result.failure(e) }
    }

    /** Moves the private data into [home]; marks the upgrade done and runs [then] only when the move finished. */
    private suspend fun upgradeMove(home: ContentRef, then: () -> Unit) {
        val up = deps.storageUpgrade ?: return
        if (!up.moveInto(home)) return upgradeFailed()
        up.markDone()
        then()
    }

    /** The adoption or move did not finish: nothing is marked, the private data stays, and the page says so. */
    private fun upgradeFailed() {
        upgradeBusy = false
        showStorageUpgrade(Screen.StorageUpgrade(Screen.StorageUpgrade.Stage.EXPLAIN, error = UPGRADE_FAILED))
    }

    /** OK after a failure: on to the library for this run; the next start asks again. */
    private fun upgradeLater() {
        upgradeDeferred = true
        upgradeFinished()
    }

    /** The upgrade is over (or put off): the library loads on the home now. */
    private fun upgradeFinished() {
        upgradeOn = false
        upgradeBusy = false
        upgradeOld = null
        load()
    }

    /** A or a tap on the copy offer's row [index]: Copy (off the main thread), Keep separate, or OK after a failure. */
    fun copySavesRow(index: Int) {
        val s = _screen.value as? Screen.CopySaves ?: return
        if (s.busy) return
        val up = deps.storageUpgrade
        if (s.error != null || index != 0 || up == null) return copySavesDone()
        _screen.value = s.copy(busy = true)
        _selection.value = ListSelection(0, 0)
        scope.launch {
            val copied = try { withContext(deps.compute) { up.copySaves(s.from, s.to) } } catch (e: CancellationException) { throw e } catch (e: Exception) { false }
            if (_screen.value != s.copy(busy = true)) return@launch
            if (copied) copySavesDone()
            else { _screen.value = s.copy(error = COPY_FAILED); _selection.value = ListSelection(0, 1) }
        }
    }

    /** The offer answered: back to the Settings page the change came from, else the library. */
    private fun copySavesDone() {
        val then = copySavesThen
        copySavesThen = null
        if (then != null) openSettings(then) else load()
    }

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
        if (_screen.value is Screen.AddFirstExtra) { addedExtraId = id; rescan { showFolderSummary(id) }; return }   // the first-setup offer: its summary

        val fromLibrary = rememberLibraryPage()
        rescan {
            fromLibrary?.let { showSettingsOn(it, SettingsKind.ADD_EXTRA) }   // still on Add ROM folder, below the new row
            val left = library?.let { folderSummary(it, id).unassigned } ?: 0
            if (left > 0) showNotice(if (left == 1) "1 game needs a console: Library → Unassigned games." else "$left games need a console: Library → Unassigned games.")
        }
    }

    /** A pick made from Library returns there after its rescan: keeps the row and names the page. */
    private fun rememberLibraryPage(): SettingsSection? {
        val s = _screen.value as? Screen.Settings ?: return null
        if (!s.section.library) return null
        sectionSelections[s.section] = _selection.value
        return s.section
    }

    /** Tools -> Settings: the section menu, on the section last opened. */
    private val _chooser = MutableStateFlow<ValueChooser?>(null)
    /**
     * A value row's picker ([com.nxredux.mobile.ui.nx.NxSettingPicker]): every value, the current one marked; a pick
     * applies it ([chooseValue]), B or a tap outside closes it ([closeChooser]).
     */
    val chooser: StateFlow<ValueChooser?> = _chooser.asStateFlow()
    private var chooserApply: ((Int) -> Unit)? = null
    /** The chooser's value [index] picked: it closes, then the value applies (unchanged when it was already the one). */
    fun chooseValue(index: Int) {
        val c = _chooser.value ?: return
        val apply = chooserApply
        closeChooser()
        if (index in c.labels.indices) apply?.invoke(index)
    }

    fun closeChooser() { _chooser.value = null; chooserApply = null }

    /** The chooser takes the pad while open: UP / DOWN move (wrapping), A picks, B closes. */
    private fun onChooserButton(c: ValueChooser, button: NxButton) {
        when (button) {
            NxButton.UP, NxButton.DOWN -> _chooser.value = c.copy(selected = (c.selected + if (button == NxButton.UP) -1 else 1).mod(c.labels.size))
            NxButton.A -> chooseValue(c.selected)
            NxButton.B, NxButton.MENU -> closeChooser()
            else -> Unit
        }
    }

    /** A value row's picker ([ValueChooser]) titled [title]: [labels], [current] under the cursor (-1: none); [apply] takes a pick. */
    private fun openPicker(title: String, labels: List<String>, current: Int, apply: (Int) -> Unit) {
        _chooser.value = ValueChooser(title, labels, current.coerceAtLeast(0), current = current)
        chooserApply = apply
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
     * Settings row [row] set to its choice [index] ([Screen.Settings.choices]), selecting it: the value as LEFT / RIGHT
     * would step it there, with the same side effects ([SettingValue.changeTo]); nothing when it is already the one or
     * the row is dimmed. Use as home screen asks Android, as A does.
     */
    fun chooseSetting(row: Int, index: Int) {
        val s = _screen.value as? Screen.Settings ?: return
        val r = s.rows.getOrNull(row)?.takeIf { it.enabled } ?: return
        select(row)
        if (r.kind == SettingsKind.ALL_FILES) { deps.allFilesAccess?.openSettings(); return }   // Android's page; the switch follows on resume
        if (r.kind == SettingsKind.HOME_SCREEN) {
            deps.launcher?.takeIf { (index == 1) != it.isHome() }?.let { it.setHome(index == 1); refreshSettings() }
            return
        }
        settingValue(r)?.changeTo(index)
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
        _chooser.value = ValueChooser(title, labels, current.coerceAtLeast(0), current = current)
        chooserApply = { emulatorSettings.choose(it) }
        return true
    }

    /**
     * A pad's A and X on an Emulator Settings / Game Settings page, before the page's own keys: A on a picker row opens
     * its picker, X shows the selected row's info when it has more to say. False for every other press.
     */
    private fun onEmulatorTouchButton(s: Screen.EmulatorOptions, button: NxButton): Boolean {
        val touch = settingTouchRow(_selection.value.index) ?: return false
        return when {
            button == NxButton.A && touch.choices.size >= 2 && touch.control == NxSettingControl.PICKER -> openEmulatorPicker(s)
            button == NxButton.X -> { if (selectedSettingShowsInfo()) showSettingInfo(_selection.value.index); true }
            else -> false
        }
    }

    /** Settings row [row]'s picker ([ValueChooser]): every value, the current one under the cursor; a pick sets it ([chooseSetting]). */
    fun openSettingPicker(row: Int) {
        val s = _screen.value as? Screen.Settings ?: return
        if (s.rows.getOrNull(row)?.enabled != true) return
        val c = s.choices.getOrNull(row)?.takeIf { it.labels.size >= 2 } ?: return
        select(row)
        _chooser.value = ValueChooser(s.rows[row].label, c.labels, c.index.coerceAtLeast(0), current = c.index)
        val label = s.rows[row].label
        // By its label: a refresh while the picker is open can move the rows (a dependent row shown or hidden).
        chooserApply = { i ->
            ((_screen.value as? Screen.Settings)?.rows?.let { rows -> row.takeIf { rows.getOrNull(it)?.label == label } ?: rows.indexOfFirst { it.label == label } })
                ?.takeIf { it >= 0 }?.let { chooseSetting(it, i) }
        }
    }

    private val _settingInfo = MutableStateFlow<SettingInfoPopup?>(null)
    /** A Settings row's full description over the page (its info icon, or X); A, B, X or a tap close it, UP / DOWN scroll it. */
    val settingInfo: StateFlow<SettingInfoPopup?> = _settingInfo.asStateFlow()

    private val _settingInfoTruncated = MutableStateFlow<String?>(null)
    /** The selected Settings row's description the UI reported cut off on its row (keyed by its text): X and its hint read it. */
    val settingInfoTruncated: StateFlow<String?> = _settingInfoTruncated.asStateFlow()

    /** The UI laid out the selected row's description [info] and it did ([truncated]) or did not fit. */
    fun onSettingInfoTruncated(info: String, truncated: Boolean) {
        _settingInfoTruncated.value = if (truncated) info else _settingInfoTruncated.value.takeIf { it != info }
    }

    /** Row [row]'s touch row as the page draws it ([settingsTouchRow], [emulatorTouchRow]); null off those pages. */
    private fun settingTouchRow(row: Int): NxSettingRow? = when (val s = _screen.value) {
        is Screen.Settings -> s.rows.getOrNull(row)?.let { settingsTouchRow(it, s.choices.getOrNull(row), s.section) }
        is Screen.EmulatorOptions -> s.rows.getOrNull(row)?.takeIf { it.selectable }?.let(::emulatorTouchRow)
        is Screen.RaSettings -> s.rows.getOrNull(row)?.takeIf { it.selectable }?.let(::raSettingsTouchRow)
        is Screen.ArtworkSettings -> s.rows.getOrNull(row)?.takeIf { it.selectable }?.let(::artworkSettingTouchRow)
        else -> null
    }

    /** Whether X opens the selected Settings row's info: its line is cut off on the row, or a fuller text is behind it. */
    private fun selectedSettingShowsInfo(): Boolean {
        val r = settingTouchRow(_selection.value.index) ?: return false
        return nxShowsInfo(r, _settingInfoTruncated.value)
    }

    /** Shows Settings row [row]'s full description ([fullInfo]), selecting it; nothing for a row without one. */
    fun showSettingInfo(row: Int) {
        val r = settingTouchRow(row) ?: return
        if (r.fullInfo.isBlank()) return
        select(row)
        _settingInfo.value = SettingInfoPopup(r.label, r.fullInfo)
    }

    fun closeSettingInfo() { _settingInfo.value = null }

    /** The info popup takes the pad while open: A, B and X close it, UP / DOWN scroll it. */
    private fun onSettingInfoButton(p: SettingInfoPopup, button: NxButton) {
        when (button) {
            NxButton.A, NxButton.B, NxButton.X, NxButton.MENU -> closeSettingInfo()
            NxButton.UP, NxButton.DOWN -> _settingInfo.value = p.copy(scrollSeq = p.scrollSeq + 1, scrollDir = if (button == NxButton.UP) -1 else 1)
            else -> Unit
        }
    }

    fun openSettingsMenu() {
        showSettingsMenu()
    }

    /** The Settings root's categories on this platform ([settingsCategories]); the two-pane left pane lists the same. */
    internal fun settingsSections(): List<SettingsSection> = settingsCategories(secondScreen = secondConnected)

    private fun showSettingsMenu() {
        val sections = settingsSections()   // sub-pages open from their section
        _screen.value = Screen.SettingsMenu(sections)
        _selection.value = settingsMenuSelection.clampedTo(sections.size)
    }

    fun openSettings(section: SettingsSection) {
        when (_screen.value) {
            is Screen.Systems -> rememberMenuSelection()
            is Screen.SettingsMenu -> settingsMenuSelection = _selection.value
            else -> Unit
        }
        val page = settingsPage(section)
        _screen.value = page
        _selection.value = selectableOn(page, sectionSelections[section] ?: ListSelection(0, 0))
        if (section == SettingsSection.LIBRARY) refreshInstalled()   // the Android games count: the cached one until it lands
    }

    /**
     * The UI reports whether Settings is drawn as two panes ([settingsSplit] of the window). The screen and selection do
     * not change: the Settings root is the left pane and its pages the right, so a rotation keeps the page and row.
     */
    fun onSettingsSplit(split: Boolean) { settingsSplit = split }

    /** [section]'s page as the unfocused right pane shows it while its category is highlighted on the left. */
    fun settingsPreview(section: SettingsSection): Screen.Settings = settingsPage(section)

    /**
     * A tap on the left pane's category [index]. On the Settings root it is a list tap (select, or open the selected
     * one). From a right-pane page it focuses the left pane on that category: the pane's own back stack unwinds as B
     * would (each page keeping its row; a Library reload or rescan lands on the root by itself). A one-tap list
     * ([com.nxredux.mobile.ui.nx.nxTap]) calls this twice: the second call, now on the root, opens that category.
     */
    fun settingsCategoryTap(index: Int) {
        if (loader.active) return
        if ((_screen.value as? Screen.Settings)?.confirmRemove != null) return   // the Remove folder dialog answers first
        if (_screen.value is Screen.SettingsMenu) {
            if (index == _selection.value.index) openSelected() else select(index)
            return
        }
        if (settingsPane(_screen.value) != SettingsPane.RIGHT) return   // a stale tap from a page drawn whole: the root's row stays
        val count = settingsSections().size
        settingsMenuSelection = ListSelection(index, count).clampedTo(count)
        repeat(SETTINGS_MAX_DEPTH) {
            if (settingsPane(_screen.value) != SettingsPane.RIGHT || loader.active) return
            back()
        }
    }

    /** The game list (and its selection) a game's Game Settings modal is open over; null while none is. */
    fun gameSettingsUnder(): Pair<Screen, ListSelection>? =
        if (gameSettingsModal(_screen.value)) emulatorSettings.gameUnder else null

    /** A tap on the Game Settings modal's scrim: closes it from any level, back to the game list on the same game. */
    fun closeGameSettings() {
        (_screen.value as? Screen.EmulatorOptions)?.takeIf(::gameSettingsModal)?.let(emulatorSettings::closeGame)
    }

    /**
     * A tap on [row] of the unfocused right pane: opens the highlighted category, focused on that row. A one-tap list
     * ([com.nxredux.mobile.ui.nx.nxTap]) calls this twice: the second call, on the page it opened, acts on that row (A).
     */
    fun settingsPreviewTap(row: Int) {
        if (loader.active) return
        if (_screen.value is Screen.Settings && row == _selection.value.index) { openSelected(); return }
        val s = _screen.value as? Screen.SettingsMenu ?: return
        val section = s.sections.getOrNull(_selection.value.index) ?: return
        openSettings(section)
        select(row)
    }

    /** [section]'s page as it stands now; [confirm]: the remove dialog over it. */
    /** A page's note under its title ([Screen.Settings.banner]): Saves', where they live. */
    private fun settingsBanner(section: SettingsSection): String? {
        if (section != SettingsSection.SAVES) return null
        val home = home()
        val media = deps.homes?.media
        return when {
            !dataMoved() -> SAVES_STILL_INSIDE
            home != null && home == media -> "Saves are in ${mediaPath(media)}. Uninstalling the app removes them."
            home != null && deps.homes != null && FileRefs.isFile(home) -> "Saves are in your home folder."
            else -> "Saves are stored inside the app. Export them before uninstalling or clearing app data."   // a picker home: still private
        }
    }

    private fun settingsPage(section: SettingsSection, confirm: ExtraFolder? = null): Screen.Settings {
        val rows = settingsRows(section)
        return Screen.Settings(section, rows, confirm, settingsDetails(section), layoutShot(section), settingsBanner(section), rows.map(::settingChoices))
    }

    /** [row]'s values for its touch control: its [settingValue]'s labels and current one; Use as home screen's and All files access's Off / On. */
    private fun settingChoices(row: SettingsRow): SettingChoices? {
        if (row.kind == SettingsKind.HOME_SCREEN) return deps.launcher?.let { SettingChoices(ON_OFF, if (it.isHome()) 1 else 0) }
        if (row.kind == SettingsKind.ALL_FILES) return SettingChoices(ON_OFF, if (row.secondary == "On") 1 else 0)   // as granted when the row was built
        val v = settingValue(row) ?: return null
        return v.labels?.let { SettingChoices(it, v.index) }
    }

    /** Shows [section]'s page on its first row of [kind] (the first row when there is none). */
    private fun showSettingsOn(section: SettingsSection, kind: SettingsKind) {
        val page = settingsPage(section)
        _screen.value = page
        _selection.value = selectableOn(page, ListSelection(page.rows.indexOfFirst { it.kind == kind }.coerceAtLeast(0), page.rows.size))
    }

    /** [sel] on [page], clamped and moved off a group label or gap to the next row that takes it (else the one before). */
    private fun selectableOn(page: Screen.Settings, sel: ListSelection): ListSelection {
        val rows = page.rows
        val at = sel.clampedTo(rows.size)
        if (rows.getOrNull(at.index)?.selectable != false) return at
        val i = (at.index until rows.size).firstOrNull { rows[it].selectable } ?: (at.index downTo 0).firstOrNull { rows[it].selectable } ?: at.index
        return at.copy(index = i)
    }

    /** One selectable row up ([delta] -1) or down from [sel] on [rows], wrapping past group labels and gaps. */
    private fun settingsStep(rows: List<SettingsRow>, sel: ListSelection, delta: Int): ListSelection =
        sel.step(delta) { rows.getOrNull(it)?.selectable != false }

    private val _padLayouts = MutableStateFlow(deps.settings.padLayouts)
    /** The stored pad layouts, republished on every change (the main menu's pads follow Shared). */
    val padLayouts: StateFlow<PadLayoutMap> = _padLayouts.asStateFlow()

    /** The Pad layout page's console: [PadLayouts.SHARED_KEY] or a console id. */
    private var padLayoutConsole: String = PadLayouts.SHARED_KEY

    private val _padScreen = MutableStateFlow(PadScreen.NARROW)
    /** The screen the app is on ([onPadScreen]): the Pad layout page and the main menu's pad use its layouts. */
    val padScreen: StateFlow<PadScreen> = _padScreen.asStateFlow()

    /**
     * The platform reports the screen the app is on (a Fold's cover or inner screen), measured as a game measures it;
     * the Pad layout page follows it.
     */
    fun onPadScreen(screen: PadScreen) {
        if (_padScreen.value == screen) return
        _padScreen.value = screen
        if ((_screen.value as? Screen.Settings)?.section == SettingsSection.PAD_LAYOUT) refreshSettings()
    }

    /** The Pad layout page's consoles after Shared: the library's consoles with games, by name. */
    private fun padLayoutConsoles(): List<Console> =
        systemsCache.filter { !it.unassigned && it.gameCount > 0 }.mapNotNull { CoreRegistry.consoleOf(it.tag) }
            .distinctBy { it.id }.sortedBy { it.displayName.lowercase() }

    /** The Pad layout page's console [key] by name: Shared, a console's name, or the key itself for an unknown one. */
    private fun padLayoutConsoleName(key: String): String =
        if (key == PadLayouts.SHARED_KEY) "Shared" else CoreRegistry.consoles.firstOrNull { it.id == key }?.displayName ?: key

    private fun padLayoutRows(): List<SettingsRow> {
        val all = deps.settings.padLayouts
        val key = padLayoutConsole
        return listOf(SettingsRow(SettingsKind.PAD_LAYOUT_CONSOLE, "Console", padLayoutConsoleName(key))) +
            PadShape.entries.map { shape ->
                val own = PadLayouts.own(all, key, PadSlot(shape, _padScreen.value))
                val value = when {
                    own != null -> if (own.tweaks.isEmpty()) "Default" else "Custom"   // empty: a console's explicit default
                    key == PadLayouts.SHARED_KEY -> "Default"
                    else -> "Shared"
                }
                SettingsRow(SettingsKind.PAD_LAYOUT_SHAPE, shape.label, value, padShape = shape)
            } +
            SettingsRow(SettingsKind.PAD_LAYOUT_RESET, "Reset console")
    }

    /** An editor is opening or open: a second quick A / tap does not stack another over it (its Save would win). */
    private var padEditorOpen = false

    private fun openPadEditor(shape: PadShape) {
        val editor = deps.padEditor ?: return
        if (padEditorOpen) return
        padEditorOpen = true
        val key = padLayoutConsole
        val console = CoreRegistry.consoles.firstOrNull { it.id == key }
        editor(PadEditRequest(key, PadSlot(shape, _padScreen.value), console?.tags?.firstOrNull().orEmpty(), console?.displayName ?: "Shared"))
    }

    /** The editor saved or left: re-read the layouts, and republish the page if Settings is still showing. */
    fun onPadLayoutEdited() {
        padEditorOpen = false
        _padLayouts.value = deps.settings.padLayouts
        if (_screen.value is Screen.Settings) refreshSettings()
    }

    private fun resetPadLayoutConsole() {
        deps.settings.padLayouts = PadLayouts.withoutScreen(deps.settings.padLayouts, padLayoutConsole, _padScreen.value)
        _padLayouts.value = deps.settings.padLayouts
        showSettingsOn(SettingsSection.PAD_LAYOUT, SettingsKind.PAD_LAYOUT_RESET)
    }

    private fun shortcutSet(): ShortcutSet = shortcutDraft ?: Shortcuts.decodeBase(deps.settings.shortcuts)

    private var controllerConnected = true

    /** The UI reports whether a controller is connected: a bind row waiting for a press says what it needs. */
    fun onControllerConnected(connected: Boolean) {
        if (controllerConnected == connected) return
        controllerConnected = connected
        if (_capturingShortcut.value != null) refreshSettings()
    }

    /**
     * A raw key from the platform, before it maps to an [NxButton]: true when press to bind took it, so the platform
     * drops it. Waiting, every key is the capture's (MENU opens nothing, L23); after it ends, the keys still held stay
     * its until released ([ShortcutCapture.takes]). Auto-repeats are the platform's to drop.
     */
    fun captureKey(keyCode: Int, down: Boolean): Boolean {
        if (capture.active && (_screen.value as? Screen.Settings)?.section != SettingsSection.SHORTCUTS) closeShortcuts()   // a stale wait: never the menu's keys
        if (!capture.takes(keyCode, down)) return false
        val result = capture.onKey(keyCode, down) ?: return true
        val action = _capturingShortcut.value
        _capturingShortcut.value = null
        if (action != null) when (result) {
            is CaptureResult.Bound -> setShortcut(action, result.binding)
            is CaptureResult.Refused -> { refreshShortcuts(); showNotice(result.reason) }
            CaptureResult.Cancelled -> refreshShortcuts()
        }
        return true
    }

    private fun startCapture(action: ShortcutAction) {
        capture.start()
        _capturingShortcut.value = action
        refreshShortcuts()
    }

    /** Ends a capture without a binding (B on the pad or a touch hint, Back, the page left). */
    private fun cancelCapture() {
        if (_capturingShortcut.value == null) return
        capture.cancel()
        _capturingShortcut.value = null
        refreshShortcuts()
    }

    /** [action] on [binding] (null: None; [ShortcutSet.withBinding]). */
    private fun setShortcut(action: ShortcutAction, binding: ShortcutBinding?) {
        shortcutDraft = shortcutSet().withBinding(action, binding)
        refreshShortcuts()
    }

    private fun setTouch(slot: TouchSlot, action: ShortcutAction?) { shortcutDraft = shortcutSet().withTouch(slot, action) }

    private fun refreshShortcuts() { if ((_screen.value as? Screen.Settings)?.section == SettingsSection.SHORTCUTS) refreshSettings() }

    /**
     * The Shortcuts page closes (any screen but it, [_screen]; the app backgrounded): a capture ends, and the draft is
     * written once (L25: not per change).
     */
    private fun closeShortcuts() {
        capture.cancel()
        _capturingShortcut.value = null
        shortcutDraft?.let { deps.settings.shortcuts = Shortcuts.encodeBase(it) }
        shortcutDraft = null
    }

    /**
     * Controls -> Shortcuts: one press-to-bind row per action (its button or combo, None unbound; the one waiting shows
     * [SHORTCUT_PROMPT] and, as its line, how to bind), the two touch buttons' actions, then Clear all.
     */
    private fun shortcutRows(): List<SettingsRow> {
        val set = shortcutSet()
        val waiting = _capturingShortcut.value
        val note = if (controllerConnected) "Hold MENU for a combo" else "Connect a controller to bind"
        return ShortcutAction.entries.map { a ->
            if (a == waiting) SettingsRow(SettingsKind.SHORTCUT_BIND, a.label, SHORTCUT_PROMPT, note = note, shortcut = a)
            else SettingsRow(SettingsKind.SHORTCUT_BIND, a.label, set.bindings[a]?.let(KeyLabels::label) ?: "None", note = if (a.mic) MIC_ROW_NOTE else null, shortcut = a)
        } + TouchSlot.entries.mapIndexed { i, t ->
            SettingsRow(SettingsKind.SHORTCUT_TOUCH, "Touch button ${i + 1}", set.touch[t]?.label ?: "None", touchSlot = t)
        } + SettingsRow(SettingsKind.RESET_DEFAULTS, "Clear all")
    }

    private fun settingsRows(section: SettingsSection): List<SettingsRow> = when (section) {
        SettingsSection.LIBRARY -> libraryRows()
        SettingsSection.EMULATORS -> listOf(
            SettingsRow(SettingsKind.DEFAULTS, "Default emulators"),
            SettingsRow(SettingsKind.EMULATORS, "Default settings"),
        )
        SettingsSection.SECOND_SCREEN -> listOf(
            SettingsRow(SettingsKind.SECOND_SCREEN_ENABLE, "Enable", if (deps.settings.secondScreen) "On" else "Off"),
            SettingsRow(SettingsKind.BOTTOM_SCREEN, "Bottom screen", bottomScreenLabel(deps.settings.bottomScreen)),
        )
        // NX Redux's Settings → System save rows, so both devices name saves alike.
        SettingsSection.SAVES -> listOf(
            SettingsRow(SettingsKind.SAVE_FORMAT, "Save format", deps.settings.saveFormat.label),
            SettingsRow(SettingsKind.STATE_FORMAT, "Save state format", deps.settings.stateFormat.label),
            SettingsRow(SettingsKind.EXTRACTED_NAME, "Use extracted file name", if (deps.settings.useExtractedFileName) "On" else "Off"),
            // Saves live only inside the app; these copy them out and back in.
            SettingsRow(SettingsKind.EXPORT_SAVES, "Export saves"),
            SettingsRow(SettingsKind.IMPORT_SAVES, "Import saves"),
        )
        SettingsSection.IMPORT_SAVES -> listOf(
            SettingsRow(SettingsKind.IMPORT_FOLDER, "From a folder"),
            SettingsRow(SettingsKind.IMPORT_ZIP, "From a zip file"),
            SettingsRow(SettingsKind.IMPORT_RETROARCH, "From RetroArch"),
        )
        // The summary, then each skipped file with its reason; read-only but for Done.
        SettingsSection.IMPORT_RESULT -> importResult?.let { r ->
            r.lines().map { SettingsRow(SettingsKind.INFO, it) } + r.skipped.map { SettingsRow(SettingsKind.INFO, it.name, it.reasonText()) }
        }.orEmpty() + SettingsRow(SettingsKind.IMPORT_DONE, "Done")
        SettingsSection.DEFAULT_EMULATORS -> defaultEmulatorRows()
        SettingsSection.PAD_LAYOUT -> padLayoutRows()
        SettingsSection.SHORTCUTS -> shortcutRows()
        SettingsSection.APPEARANCE -> listOf(
            SettingsRow(SettingsKind.PAD_THEME, "Pad theme", padThemeLabel(deps.settings.padTheme)),
            SettingsRow(SettingsKind.ACCENT, "Accent", deps.settings.accent.label),
            SettingsRow(SettingsKind.SECONDARY_ACCENT, "Secondary accent", deps.settings.secondaryAccent.label),
            // The options lists' text (Settings, Game Settings, the in-game menu): Large or Small.
            SettingsRow(SettingsKind.TEXT_SIZE, "Text size", deps.settings.optionTextSize.label),
            SettingsRow(SettingsKind.RESET_DEFAULTS, RESET_DEFAULTS_LABEL),
        )
        SettingsSection.MAIN_MENU -> layoutsRows()
        SettingsSection.LAYOUT_HOME, SettingsSection.LAYOUT_CONSOLES, SettingsSection.LAYOUT_COLLECTIONS, SettingsSection.LAYOUT_TOOLS ->
            layoutTabRows(section.tab!!)
        SettingsSection.LAYOUT_GAME_LISTS -> layoutGameListRows()
        SettingsSection.LAYOUT_TITLE_HINTS -> layoutTitleHintRows()
        SettingsSection.LAYOUT_SWITCHER -> listOf(SettingsRow(SettingsKind.SWITCHER_LAYOUT, "Game Switcher layout", deps.settings.switcherLayout.label))
        // The on-screen pad's press tick, then a core's rumble.
        SettingsSection.CONTROLS -> listOf(
            SettingsRow(SettingsKind.PAD_VIBRATION, "Pad vibration", deps.settings.padVibration.label),
            SettingsRow(SettingsKind.GAME_RUMBLE, "Game rumble", deps.settings.gameRumble.label),
            // Portrait's main-menu pad below the screen; Off, the menu runs to the bottom (games keep theirs).
            SettingsRow(SettingsKind.MENU_PAD, "Menu pad", if (deps.settings.menuPad) "On" else "Off"),
            // The pad's arrangement, its own page (Pad layout).
            SettingsRow(SettingsKind.PAD_LAYOUT, "Pad layout", if (PadLayouts.isCustom(deps.settings.padLayouts)) "Custom" else "Default"),
            // Fast forward, rewind and reset buttons, its own page (Shortcuts).
            SettingsRow(SettingsKind.SHORTCUTS, "Shortcuts"),
            SettingsRow(SettingsKind.RESET_DEFAULTS, RESET_DEFAULTS_LABEL),
        )
        SettingsSection.SYSTEM -> systemRows()
    }

    /** Settings -> System: the General, Launcher (with a launcher), Developer, About and Licenses groups. */
    private fun systemRows(): List<SettingsRow> = buildList {
        add(SettingsRow(SettingsKind.SECTION, "General"))
        add(SettingsRow(SettingsKind.PAUSE_UNFOCUSED, "Pause when unfocused", if (deps.settings.pauseWhenUnfocused) "On" else "Off"))
        deps.launcher?.let { l ->
            add(SettingsRow(SettingsKind.GAP, ""))
            add(SettingsRow(SettingsKind.SECTION, "Launcher"))
            add(SettingsRow(SettingsKind.HOME_SCREEN, "Use as home screen", if (l.isHome()) "On" else "Off"))
        }
        add(SettingsRow(SettingsKind.GAP, ""))
        add(SettingsRow(SettingsKind.SECTION, "Developer"))
        // Logging for a bug report; off, only errors reach logcat.
        add(SettingsRow(SettingsKind.DEBUG_LOG, "Debug log", if (deps.settings.debugLog) "On" else "Off"))
        add(SettingsRow(SettingsKind.GAP, ""))
        add(SettingsRow(SettingsKind.SECTION, "About"))
        queryGpu()   // the page (or its preview) shows: the GPU row reads "…" until this lands
        aboutRows(deps.appInfo, _gpu.value).forEach { (label, value) -> add(SettingsRow(SettingsKind.INFO, label, value)) }
        add(SettingsRow(SettingsKind.COPY_INFO, "Copy device info"))
        _updateLabel.value?.let { add(SettingsRow(SettingsKind.APP_UPDATE, it)) }
        add(SettingsRow(SettingsKind.GAP, ""))
        add(SettingsRow(SettingsKind.SECTION, "Licenses"))
        ABOUT_LICENSES.forEach { add(SettingsRow(SettingsKind.TEXT, it)) }
        add(SettingsRow(SettingsKind.LICENSES_LINK, "Full list of licenses", LICENSES_URL))
    }

    /** About's GPU name once its query answered ([UNKNOWN_INFO] on a failure); null before. */
    private val _gpu = MutableStateFlow<String?>(null)
    internal val gpu: StateFlow<String?> = _gpu.asStateFlow()   // the two-pane preview of About redraws when it lands
    private var gpuJob: Job? = null

    /**
     * Asks [AppDeps.deviceGpu] once; the About page then re-reads its rows. Started lazily so a query that answers at
     * once (no suspension) finds [gpuJob] already set and the rows it rebuilds do not ask again.
     */
    private fun queryGpu(): Job = gpuJob ?: scope.launch(start = CoroutineStart.LAZY) {
        val name = try { deps.deviceGpu() } catch (e: CancellationException) { throw e } catch (e: Exception) { null }
        _gpu.value = name?.trim()?.ifEmpty { null } ?: UNKNOWN_INFO
        if ((_screen.value as? Screen.Settings)?.section == SettingsSection.SYSTEM) refreshSettings()
    }.also { gpuJob = it; it.start() }

    /** Copy device info: the About rows as text on the clipboard (after the GPU query, which is quick), then a notice. */
    private fun copyDeviceInfo() {
        scope.launch {
            queryGpu().join()
            deps.copyText(aboutCopyText(deps.appInfo, _gpu.value))
            showNotice("Device info copied")
        }
    }

    private fun bottomScreenLabel(b: BottomScreen): String = when (b) {
        BottomScreen.AUTO -> "Auto"
        BottomScreen.MAIN -> "Main screen"
        BottomScreen.SECOND -> "Second screen"
    }

    private fun padThemeLabel(theme: PadTheme): String = when (theme) {
        PadTheme.CHARCOAL -> "Charcoal"
        PadTheme.RETRO -> "Retro"
    }


    /**
     * The Library page, one list with no groups: the counts with Rescan, the home (its path; "Not available" while recorded
     * offline, [offlineHomeSaid]), each extra ROM folder (its path, or its state while the in-memory index lists it
     * offline) and Add ROM folder, All files access where the permission is declared (a sideloaded Android 11+ build) as a
     * switch opening Android's page, Saves, Android games, and Unassigned and Hidden while they hold a game. From memory
     * only, no SAF call. Extras reflect the last scan: one whose card was pulled since reads as fine until a rescan. "Not
     * ready yet" (mounted but not listing, as the scan's notice says) only comes from a scan in this AppState
     * ([lateMountHere]); a lateMount read from the stored index is from an earlier run, so it reads "Not available".
     */
    private fun libraryRows(): List<SettingsRow> = buildList {
        val ix = library
        // At the top: the games and consoles (a merged console counts once, as on the main menu), the last good scan
        // under them, and the Rescan button on the right.
        val consoles = systemsCache.filter { !it.unassigned }.map { it.displayName }.distinct().size
        val counts = "${gamesLabel(systemsCache.sumOf { it.gameCount })} · ${if (consoles == 1) "1 console" else "$consoles consoles"}"
        add(SettingsRow(SettingsKind.RESCAN, counts, ix?.scannedAt?.let { "Last scan: ${formatDateTime(it)}" }))
        // One list, no groups: the home, the ROM folders added beside it, Add ROM folder, All files access, then Saves and
        // the game lists.
        val root = home()
        val homeOffline = root != null && offlineHomeSaid == root
        val homeText = when {
            root == null -> "No home folder"
            homeOffline -> "Not available"
            else -> Sources.displayPath(root) ?: deps.source.displayName(root)
        }
        add(SettingsRow(SettingsKind.HOME, "Home folder", homeText, warning = homeOffline))
        deps.settings.extraFolders.forEach {
            val state = when {
                ix == null || it.id !in ix.offline -> null
                fileAccessOff(it.ref) -> "All files access off"
                it.id in ix.lateMount && it.id in lateMountHere -> "Not ready yet"
                else -> "Not available"
            }
            add(SettingsRow(SettingsKind.EXTRA, "ROM Folder: ${deps.source.displayName(it.ref)}", state ?: Sources.displayPath(it.ref) ?: "ROM folder", extra = it, warning = state != null))
        }
        add(SettingsRow(SettingsKind.ADD_EXTRA, "Add ROM folder"))
        deps.allFilesAccess?.takeIf { it.declared }?.let { add(allFilesRow(it.granted(), root)) }
        add(SettingsRow(SettingsKind.SAVES, "Saves"))
        if (deps.launcher != null) add(SettingsRow(SettingsKind.ANDROID_GAMES, "Android games", installedCache?.let { gamesCountLabel(androidGames(it).size) } ?: "…"))
        unassignedSystem()?.let { add(SettingsRow(SettingsKind.UNASSIGNED, "Unassigned games", gamesLabel(it.gameCount))) }
        if (hiddenPaths.isNotEmpty()) add(SettingsRow(SettingsKind.HIDDEN, "Hidden games", gamesLabel(hiddenPaths.size)))
    }

    /**
     * The lines above a page's rows. Default emulators: what the choice is for (Library's counts are its first row). From
     * memory only: no SAF call.
     */
    private fun settingsDetails(section: SettingsSection): List<String> = when (section) {
        SettingsSection.DEFAULT_EMULATORS -> listOf("A game's own Emulator choice still wins. Saves and settings follow the tag.")
        SettingsSection.IMPORT_RESULT -> importResult?.statesNote().orEmpty()   // wraps, unlike the one-line rows
        else -> emptyList()
    }

    /** Emulators -> Default emulators, built from [systemsCache] (no SAF call): [defaultEmulatorRows]. */
    private fun defaultEmulatorRows(): List<SettingsRow> = defaultEmulatorRows(systemsCache, deps.settings.defaultTags)

    /**
     * Settings' value row [row] as a [SettingValue]: every setting LEFT / RIGHT (NX Redux ITEM_CYCLE), A and the touch
     * chooser step, in one table, so each is written once. A change republishes the page (App re-reads the theme,
     * accent, scale and pad with it); an arm with more to do says so in its `after`. Null for a row that is not a value
     * row, or has nothing to step (a console of one tag, a row without its tab, shelf or hint). Two-value rows flip on
     * every press. Onboarding's value rows step the same entries through a bare row of their kind.
     */
    private fun settingValue(row: SettingsRow): SettingValue<*>? {
        val s = deps.settings
        val republish: (Any?) -> Unit = { refreshSettings() }
        fun <T> cycle(values: List<T>, current: T, label: ((T) -> String)? = null, after: (T) -> Unit = republish, set: (T) -> Unit) =
            SettingValue(values, current, set, label, after = after)
        // Off / On, or Hide / Show for a row that shows or hides something (Layouts'): the touch row's switch reads either.
        fun flip(on: Boolean, show: Boolean = false, after: (Boolean) -> Unit = republish, set: (Boolean) -> Unit) =
            cycle(listOf(false, true), on, { if (show) (if (it) "Show" else "Hide") else (if (it) "On" else "Off") }, after = after, set = set)
        return when (row.kind) {
            // Appearance: the pad theme, the accent presets, Auto and the presets, Large / Small.
            SettingsKind.PAD_THEME -> cycle(PadTheme.entries, s.padTheme, ::padThemeLabel) { s.padTheme = it }
            SettingsKind.ACCENT -> cycle(AccentPreset.entries, s.accent, { it.label }) { s.accent = it }
            SettingsKind.SECONDARY_ACCENT -> cycle(SecondaryAccent.entries, s.secondaryAccent, { it.label }) { s.secondaryAccent = it }
            SettingsKind.TEXT_SIZE -> cycle(OptionTextSize.entries, s.optionTextSize, { it.label }) { s.optionTextSize = it }
            // Layouts. A main menu tab cycles List, Grid, Carousel and Minimal ([MenuStyles.MAIN_MENU_STYLES]; Backdrop is a
            // game-list style only), Home rebuilt in its new one.
            SettingsKind.MENU_STYLE -> row.category?.let { c ->
                cycle(MenuStyles.MAIN_MENU_STYLES, MenuStyles.styleOf(s.menuStyles, c), ::menuStyleLabel, after = {
                    if (c == MenuCategory.HOME) rebuildHomeTab()
                    refreshSettings()
                }) { s.menuStyles = s.menuStyles + (c to it) }
            }
            SettingsKind.GAME_LIST_STYLE -> cycle(MenuStyles.GAME_LIST_STYLES, s.gameListStyle, ::menuStyleLabel) { s.gameListStyle = it }
            SettingsKind.MENU_ORIENTATION -> row.category?.let { c ->
                cycle(MenuOrientation.entries, MenuStyles.orientationOf(s.menuOrientations, c), ::orientationLabel) { s.menuOrientations = s.menuOrientations + (c to it) }
            }
            SettingsKind.GAME_LIST_ORIENTATION -> cycle(MenuOrientation.entries, s.gameListOrientation, ::orientationLabel) { s.gameListOrientation = it }
            SettingsKind.GAME_LIST_VERTICAL_ALIGNMENT -> cycle(VerticalListAlignment.entries, s.gameListVerticalAlignment, ::verticalAlignmentLabel) { s.gameListVerticalAlignment = it }
            SettingsKind.CONTROLLER_ART -> cycle(ConsoleArt.entries, s.consoleArt, { it.label }) { s.consoleArt = it }   // Controller, Background, None
            SettingsKind.LIST_ART -> cycle(GameListArt.entries, s.gameListArt, { it.label }) { s.gameListArt = it }
            SettingsKind.BACKDROP_ART -> cycle(BackdropArt.entries, s.backdropArt, { it.label }) { s.backdropArt = it }
            SettingsKind.SWITCHER_LAYOUT -> cycle(SwitcherLayout.entries, s.switcherLayout, { it.label }) { s.switcherLayout = it }   // Full screen / List
            SettingsKind.TAB_SHOWN -> row.category?.let { c ->
                flip(tabSetting(c), show = true) { on ->
                    when (c) {
                        MenuCategory.HOME -> s.showHomeTab = on
                        MenuCategory.CONSOLES -> s.showConsolesTab = on
                        MenuCategory.COLLECTIONS -> s.showCollectionsTab = on
                        MenuCategory.TOOLS -> s.showToolsTab = on
                    }
                }
            }
            // New shelf / Not started shelf: Home is rebuilt without (or with) it. Pick up again has no setting.
            SettingsKind.SHELF_SHOWN -> row.shelf?.let { shelf ->
                flip(shelfShown(shelf), show = true, after = { rebuildHomeTab(); refreshSettings() }) { on ->
                    when (shelf) {
                        HomeShelf.NEW -> s.showNewShelf = on
                        HomeShelf.NOT_STARTED -> s.showNotStartedShelf = on
                        HomeShelf.PICK_UP -> Unit
                    }
                }
            }
            // Recent / Search / Netplay hint: the Show / Hide of SELECT's, START's and Y's hint.
            SettingsKind.HINT_SHOWN -> row.hint?.let { button ->
                val shown = when (button) {
                    NxButton.SELECT -> s.showRecentHint
                    NxButton.START -> s.showSearchHint
                    NxButton.Y -> s.showNetplayHint
                    else -> false
                }
                flip(shown, show = true) { on ->
                    when (button) {
                        NxButton.SELECT -> s.showRecentHint = on
                        NxButton.START -> s.showSearchHint = on
                        NxButton.Y -> s.showNetplayHint = on
                        else -> Unit
                    }
                }
            }
            // Hidden, the tab row gives up the d-pad ([dropHiddenTabFocus]).
            SettingsKind.PAGE_TITLE -> flip(s.menuPageTitle, show = true, after = { dropHiddenTabFocus(); refreshSettings() }) { s.menuPageTitle = it }
            SettingsKind.BUTTON_HINTS -> flip(s.menuButtonHints, show = true) { s.menuButtonHints = it }
            SettingsKind.EXTRA_INFO -> flip(s.menuExtraInfo, show = true) { s.menuExtraInfo = it }
            SettingsKind.HOME_STATS -> flip(s.menuHomeStats, show = true) { s.menuHomeStats = it }   // Home's board follows through its frame
            SettingsKind.LAYOUT_OPTIONS -> flip(s.gameListLayoutOptions, show = true) { s.gameListLayoutOptions = it }   // a game list's icons
            SettingsKind.ACHIEVEMENTS_PANEL -> flip(s.gameListAchievementsPanel, show = true) { s.gameListAchievementsPanel = it }   // X's hint
            // Controls: Off, Light, Medium and Strong; Game rumble previews the new level. The menu pad: the shell follows.
            SettingsKind.PAD_VIBRATION -> cycle(PadVibration.entries, s.padVibration, { it.label }) { s.padVibration = it }
            SettingsKind.GAME_RUMBLE -> cycle(GameRumble.entries, s.gameRumble, { it.label }, after = { refreshSettings(); deps.rumblePreview(it) }) { s.gameRumble = it }
            SettingsKind.MENU_PAD -> flip(s.menuPad) { s.menuPad = it }
            // A touch button: None, then every action.
            SettingsKind.SHORTCUT_TOUCH -> row.touchSlot?.let { slot ->
                cycle(listOf<ShortcutAction?>(null) + ShortcutAction.entries.filter { it.touch }, shortcutSet().touch[slot], { it?.label ?: "None" }) { setTouch(slot, it) }
            }
            SettingsKind.PAD_LAYOUT_CONSOLE -> {   // Shared, then the library's consoles; a gone console reads as Shared
                val keys = listOf(PadLayouts.SHARED_KEY) + padLayoutConsoles().map { it.id }
                cycle(keys, padLayoutConsole.takeIf { it in keys } ?: keys.first(), ::padLayoutConsoleName) { padLayoutConsole = it }
            }
            // Emulators, Saves (the handheld's formats in its order), Second screen and Developer.
            SettingsKind.PAUSE_UNFOCUSED -> flip(s.pauseWhenUnfocused) { s.pauseWhenUnfocused = it }
            SettingsKind.SAVE_FORMAT -> cycle(SaveFormat.entries, s.saveFormat, { it.label }) { s.saveFormat = it }
            SettingsKind.STATE_FORMAT -> cycle(StateFormat.entries, s.stateFormat, { it.label }) { s.stateFormat = it }
            SettingsKind.EXTRACTED_NAME -> flip(s.useExtractedFileName) { s.useExtractedFileName = it }
            // A console's default emulator steps through its tags (leaving Default emulators rescans); one tag: nothing to pick.
            SettingsKind.DEFAULT_TAG -> row.consoleId?.let { id -> CoreRegistry.consoles.firstOrNull { it.id == id } }?.takeIf { it.tags.size > 1 }?.let { console ->
                cycle(console.tags, console.defaultTagFor(s.defaultTags[console.id]), CoreRegistry::emulatorLabel) { s.defaultTags = s.defaultTags + (console.id to it) }
            }
            SettingsKind.SECOND_SCREEN_ENABLE -> flip(s.secondScreen) { s.secondScreen = it }
            SettingsKind.BOTTOM_SCREEN -> cycle(BottomScreen.entries, s.bottomScreen, ::bottomScreenLabel) { s.bottomScreen = it }   // Auto, Main screen, Second screen
            SettingsKind.DEBUG_LOG -> flip(s.debugLog) { s.debugLog = it }
            else -> null
        }
    }

    /** A save transfer's report: its progress notice stays until the result (done) replaces it. */
    private fun showTransferNotice(text: String, done: Boolean) = showNotice(text, sticky = !done)

    /** The last finished import's summary, which the Import results page lists. */
    private var importResult: ImportSummary? = null

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
                importResult = result.summary
                if ((_screen.value as? Screen.Settings)?.section != SettingsSection.IMPORT_SAVES) return showNotice(result.summary.lines().joinToString("; "))
                dismissNotice()
                sectionSelections.remove(SettingsSection.IMPORT_RESULT)
                openSettings(SettingsSection.IMPORT_RESULT)
            }
            is ImportResult.Message -> showNotice(result.text)
        }
    }

    /**
     * Onboarding's import from [source]: its progress and then its result show on the step itself (re-drawn), never a
     * Settings page or sticky notice. Ended after the step was left, the result is a notice.
     */
    private fun onboardingImport(source: ImportSource) {
        fun onStep() = onboarding.step == OnboardingStep.IMPORT_SAVES && (_screen.value as? Screen.Onboarding)?.step == OnboardingStep.IMPORT_SAVES
        onboardingImported = null
        importSaves(source, progress = { text ->
            onboardingImporting = text
            if (onStep()) showOnboarding(_selection.value.index)
        }) { result ->
            onboardingImporting = null
            when {
                onStep() -> {
                    onboardingImported = result
                    // A summary lands on Continue; a message keeps the row picked.
                    showOnboarding(if (result is ImportResult.Done) result.summary.skipped.size else _selection.value.index)
                }
                result is ImportResult.Done -> showNotice(result.summary.lines().joinToString("; "))
                result is ImportResult.Message -> showNotice(result.text)
            }
        }
    }

    /**
     * Appearance's, Layouts' or Controls' "Reset to defaults" (NX Redux settings.c): the page's own options back to their
     * defaults. Appearance: Pad theme, Accent, Secondary accent. Layouts: every tab's style and orientation
     * unset (so [MenuStyles.styleOf] and [MenuStyles.orientationOf] give theirs), the game lists', the tabs', shelves'
     * and hints' Show/Hide, Consoles background, List art, Backdrop art, Page title, Button hints, Extra info, Home stats,
     * Layout options, Achievements panel and Game Switcher layout. Controls: Pad vibration, Game rumble and every pad
     * layout. At once, no confirm, as NX Redux. Through the settings' own setters, then the page is republished like any
     * cycled row's (App re-reads the theme, accent and scale with it), the highlight kept on the reset row, which the
     * orientation rows coming or going may have moved.
     */
    private fun resetToDefaults(section: SettingsSection) {
        val s = deps.settings
        if (section == SettingsSection.SHORTCUTS) {
            shortcutDraft = ShortcutSet.EMPTY   // Clear all: written with the rest when the page closes
        } else if (section == SettingsSection.APPEARANCE) {
            s.padTheme = PadTheme.DEFAULT
            s.accent = AccentPreset.DEFAULT
            s.secondaryAccent = SecondaryAccent.DEFAULT
            s.optionTextSize = OptionTextSize.DEFAULT
        } else if (section == SettingsSection.MAIN_MENU) {
            s.menuStyles = emptyMap()
            s.menuOrientations = emptyMap()
            s.gameListStyle = MenuStyles.DEFAULT_GAME_LIST_STYLE
            s.gameListOrientation = MenuStyles.DEFAULT_ORIENTATION
            s.gameListVerticalAlignment = VerticalListAlignment.DEFAULT
            s.showHomeTab = MenuStyles.DEFAULT_TAB_SHOWN; s.showConsolesTab = MenuStyles.DEFAULT_TAB_SHOWN
            s.showCollectionsTab = MenuStyles.DEFAULT_TAB_SHOWN; s.showToolsTab = MenuStyles.DEFAULT_TAB_SHOWN
            s.showNewShelf = true; s.showNotStartedShelf = true
            s.showRecentHint = true; s.showSearchHint = true; s.showNetplayHint = true
            s.consoleArt = ConsoleArt.DEFAULT; s.gameListArt = GameListArt.DEFAULT; s.backdropArt = BackdropArt.DEFAULT
            s.menuPageTitle = MenuStyles.DEFAULT_PAGE_TITLE
            s.menuButtonHints = MenuStyles.DEFAULT_BUTTON_HINTS
            s.menuExtraInfo = MenuStyles.DEFAULT_EXTRA_INFO
            s.menuHomeStats = MenuStyles.DEFAULT_HOME_STATS
            s.gameListLayoutOptions = true
            s.gameListAchievementsPanel = true
            s.switcherLayout = SwitcherLayout.DEFAULT
            // As the single rows do: Home's rows follow its style and shelves; a hidden tab row gives up the d-pad.
            rebuildHomeTab()
            dropHiddenTabFocus()
        } else if (section == SettingsSection.CONTROLS) {
            s.padVibration = PadVibration.DEFAULT
            s.gameRumble = GameRumble.DEFAULT
            s.menuPad = true
            s.padLayouts = emptyMap(); _padLayouts.value = emptyMap()
        }
        showSettingsOn(section, SettingsKind.RESET_DEFAULTS)
    }

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
        val backToRow = { showSettingsOn(section, row) }
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
     * Layouts' rows in the handheld's order (settings.c): each tab's Show/Hide row, then while it shows its layout, Home's
     * shelves and stats, its orientation (Carousel only; Home has none) and Consoles' controller (List only); then the
     * game lists' layout, orientation (Carousel, Backdrop), alignment (vertical), List art (List), Backdrop art
     * (Backdrop), Layout options and Achievements panel; then Game Switcher layout, Extra info, Page title, Button hints,
     * the hints' Show/Hide (while Button hints shows) and Reset. A row that comes or goes is always below the row that
     * changes it, so [refreshSettings]' clamp keeps the highlight on the changed row.
     */
    /**
     * Layouts: a row per group (its page: a tab's, the game lists', the page title's and hints', the Game Switcher's), each
     * with what it is now (a shown tab its layout, a hidden one Hidden), then Extra info and Reset to defaults.
     */
    private fun layoutsRows(): List<SettingsRow> {
        val s = deps.settings
        val tabs = MenuCategory.entries.map { c ->
            val group = SettingsSection.entries.first { it.tab == c }
            SettingsRow(group.opener!!, group.label, if (tabSetting(c)) menuStyleLabel(MenuStyles.styleOf(s.menuStyles, c)) else "Hidden")
        }
        val chrome = when {
            s.menuPageTitle && s.menuButtonHints -> "Show"
            s.menuPageTitle -> "Title only"
            s.menuButtonHints -> "Hints only"
            else -> "Hide"
        }
        return tabs + listOf(
            SettingsRow(SettingsKind.LAYOUT_GAME_LISTS, SettingsSection.LAYOUT_GAME_LISTS.label, menuStyleLabel(s.gameListStyle)),
            SettingsRow(SettingsKind.LAYOUT_TITLE_HINTS, SettingsSection.LAYOUT_TITLE_HINTS.label, chrome),
            SettingsRow(SettingsKind.LAYOUT_SWITCHER, SettingsSection.LAYOUT_SWITCHER.label, s.switcherLayout.label),   // NX Redux PR #218
            SettingsRow(SettingsKind.EXTRA_INFO, "Extra info", if (s.menuExtraInfo) "Show" else "Hide"),
            SettingsRow(SettingsKind.RESET_DEFAULTS, RESET_DEFAULTS_LABEL),
        )
    }

    /** A tab's group: its Show / Hide, and while shown its layout and the options that layout has. */
    private fun layoutTabRows(c: MenuCategory): List<SettingsRow> {
        val s = deps.settings
        fun shown(on: Boolean) = if (on) "Show" else "Hide"
        val on = tabSetting(c)
        val style = MenuStyles.styleOf(s.menuStyles, c)
        return listOfNotNull(
            SettingsRow(SettingsKind.TAB_SHOWN, "${c.label} tab", shown(on), category = c),
            SettingsRow(SettingsKind.MENU_STYLE, "${c.label} layout", menuStyleLabel(style), category = c).takeIf { on },
            // Home's New and Not started shelves, Show / Hide, under Home layout while the Home tab shows.
            SettingsRow(SettingsKind.SHELF_SHOWN, "New shelf", shown(s.showNewShelf), shelf = HomeShelf.NEW).takeIf { on && c == MenuCategory.HOME },
            SettingsRow(SettingsKind.SHELF_SHOWN, "Not started shelf", shown(s.showNotStartedShelf), shelf = HomeShelf.NOT_STARTED).takeIf { on && c == MenuCategory.HOME },
            // Home's stats strip (its cards' info follows Extra info).
            SettingsRow(SettingsKind.HOME_STATS, "Home stats", shown(s.menuHomeStats)).takeIf { on && c == MenuCategory.HOME },
            SettingsRow(SettingsKind.MENU_ORIENTATION, "${c.label} orientation", orientationLabel(MenuStyles.orientationOf(s.menuOrientations, c)), category = c)
                .takeIf { on && c != MenuCategory.HOME && style == MenuStyle.CAROUSEL },
            SettingsRow(SettingsKind.CONTROLLER_ART, "Consoles background", s.consoleArt.label, category = c)
                .takeIf { on && c == MenuCategory.CONSOLES && style != MenuStyle.GRID && style != MenuStyle.MINIMAL },   // List and Carousel; the Grid has no controllers, Minimal no art
        )
    }

    /** The game lists' group: their layout and the options it has, the Layout icon and the achievements panel. */
    private fun layoutGameListRows(): List<SettingsRow> {
        val s = deps.settings
        fun shown(on: Boolean) = if (on) "Show" else "Hide"
        val list = s.gameListStyle
        return listOfNotNull(
            SettingsRow(SettingsKind.GAME_LIST_STYLE, "Game lists layout", menuStyleLabel(list)),
            SettingsRow(SettingsKind.GAME_LIST_ORIENTATION, "Game lists orientation", orientationLabel(s.gameListOrientation)).takeIf { MenuStyles.hasOrientation(list) },
            SettingsRow(SettingsKind.GAME_LIST_VERTICAL_ALIGNMENT, "Game lists alignment", verticalAlignmentLabel(s.gameListVerticalAlignment))
                .takeIf { MenuStyles.effectiveOrientation(list, s.gameListOrientation) == MenuOrientation.VERTICAL },
            SettingsRow(SettingsKind.LIST_ART, "List art", s.gameListArt.label).takeIf { list == MenuStyle.LIST },
            SettingsRow(SettingsKind.BACKDROP_ART, "Backdrop art", s.backdropArt.label).takeIf { list == MenuStyle.BACKDROP },
            // A game list's Layout icon (hidden: Layouts only), and its trophy icon and X hint (hidden: X and the game's menu).
            SettingsRow(SettingsKind.LAYOUT_OPTIONS, "Layout options", shown(s.gameListLayoutOptions)),
            SettingsRow(SettingsKind.ACHIEVEMENTS_PANEL, "Achievements panel", shown(s.gameListAchievementsPanel)).takeIf { deps.achievements != null },
        )
    }

    /** The page title's and hints' group: the two, and each hint on its own while the bar shows. */
    private fun layoutTitleHintRows(): List<SettingsRow> {
        val s = deps.settings
        fun shown(on: Boolean) = if (on) "Show" else "Hide"
        return listOfNotNull(
            SettingsRow(SettingsKind.PAGE_TITLE, "Page title", shown(s.menuPageTitle)),
            SettingsRow(SettingsKind.BUTTON_HINTS, "Button hints", shown(s.menuButtonHints)),
            // Each hint on its own, while the bar shows (hidden: Netplay in the context menu, Recent and Search as icons).
            SettingsRow(SettingsKind.HINT_SHOWN, "Recent hint", shown(s.showRecentHint), hint = NxButton.SELECT).takeIf { s.menuButtonHints },
            SettingsRow(SettingsKind.HINT_SHOWN, "Search hint", shown(s.showSearchHint), hint = NxButton.START).takeIf { s.menuButtonHints },
            SettingsRow(SettingsKind.HINT_SHOWN, "Netplay hint", shown(s.showNetplayHint), hint = NxButton.Y).takeIf { s.menuButtonHints && netplay != null },
        )
    }

    /** The game lists' picture: their layout, stood on end (a Carousel or Backdrop) on the left or the right. */
    private fun gameListsShot(): LayoutShot.Games {
        val s = deps.settings
        val vertical = MenuStyles.effectiveOrientation(s.gameListStyle, s.gameListOrientation) == MenuOrientation.VERTICAL && MenuStyles.hasOrientation(s.gameListStyle)
        return LayoutShot.Games(s.gameListStyle, vertical, right = vertical && s.gameListVerticalAlignment == VerticalListAlignment.RIGHT)
    }

    /** A tab picture's Consoles background: the setting's on Consoles, the default on the other tabs (they have none). */
    private fun consoleShotArt(c: MenuCategory): ConsoleArt = if (c == MenuCategory.CONSOLES) deps.settings.consoleArt else ConsoleArt.CONTROLLER

    /** A Layouts group's picture of its values: a shown tab's layout, the game lists', the title and hints, the Game Switcher's; else none. */
    private fun layoutShot(section: SettingsSection): LayoutShot? {
        val s = deps.settings
        section.tab?.let { c ->
            if (!tabSetting(c)) return null
            val style = MenuStyles.styleOf(s.menuStyles, c)
            val vertical = style == MenuStyle.CAROUSEL && c != MenuCategory.HOME && MenuStyles.orientationOf(s.menuOrientations, c) == MenuOrientation.VERTICAL
            return LayoutShot.Tab(c, style, stats = c != MenuCategory.HOME || s.menuHomeStats, vertical = vertical, art = consoleShotArt(c))
        }
        return when (section) {
            SettingsSection.LAYOUT_GAME_LISTS -> gameListsShot()
            SettingsSection.LAYOUT_TITLE_HINTS -> LayoutShot.Chrome(s.menuPageTitle, s.menuButtonHints)
            SettingsSection.LAYOUT_SWITCHER -> LayoutShot.Switcher(s.switcherLayout)
            else -> null
        }
    }

    /**
     * The tabs Layouts shows ([tabSetting]). Tools too: a hidden Tools (and Settings in it) stays reachable from MENU and
     * a long press ([hiddenTabs]).
     */
    private fun shownTabs(): Set<MenuCategory> = MenuCategory.entries.filterTo(HashSet()) { tabSetting(it) }

    /**
     * The tabs Layouts hides that have content ([hiddenMenuTabs]): MENU's and the long press's items; none over a pushed
     * tab. Read live for the menu's items; the screen carries the same list ([Screen.Systems.hidden]) for the UI's hints.
     */
    internal fun hiddenTabs(): List<MenuCategory> =
        if ((_screen.value as? Screen.Systems)?.pushed == true) emptyList() else hiddenMenuTabs(allMenuTabs, menuTabs)

    /** Layouts → [c] tab as stored: Show (true) or Hide. */
    private fun tabSetting(c: MenuCategory): Boolean = when (c) {
        MenuCategory.HOME -> deps.settings.showHomeTab
        MenuCategory.CONSOLES -> deps.settings.showConsolesTab
        MenuCategory.COLLECTIONS -> deps.settings.showCollectionsTab
        MenuCategory.TOOLS -> deps.settings.showToolsTab
    }

    /** Whether Layouts shows [shelf] on Home: New and Not started by their settings; Pick up again always. */
    private fun shelfShown(shelf: HomeShelf): Boolean = when (shelf) {
        HomeShelf.NEW -> deps.settings.showNewShelf
        HomeShelf.NOT_STARTED -> deps.settings.showNotStartedShelf
        HomeShelf.PICK_UP -> true
    }

    /** Rebuilds the open page's rows in place (selection clamped). */
    private fun refreshSettings(confirm: ExtraFolder? = null) {
        val section = (_screen.value as? Screen.Settings)?.section ?: SettingsSection.LIBRARY
        val page = settingsPage(section, confirm)
        _screen.value = page
        _selection.value = selectableOn(page, _selection.value)
    }

    /**
     * B on a sub-page: its section on the row that opened it. Leaving Default emulators (under Emulators) with a changed
     * default (the index no longer fits) rescans first, under the overlay, so the library is the new one's. Import results
     * goes back to Saves on its Import saves row.
     */
    private fun leaveSubPage(section: SettingsSection) {
        if (section == SettingsSection.IMPORT_RESULT) return showSettingsOn(SettingsSection.SAVES, SettingsKind.IMPORT_SAVES)   // past Import saves
        val parent = section.parent ?: return
        val opener = section.opener ?: return
        val root = home()
        val stale = section == SettingsSection.DEFAULT_EMULATORS && root != null &&
            library?.fits(root, deps.settings.extraFolders, deps.settings.defaultTags) != true
        if (stale) rescanFromSettings(parent, opener) else showSettingsOn(parent, opener)
    }

    fun removeExtra(extra: ExtraFolder) {
        deps.settings.extraFolders = deps.settings.extraFolders.filter { it.id != extra.id }
        libraryDirty = true
        deps.folderPicker.forget(extra.ref)
        refreshSettings()
        val root = home() ?: return
        scope.launch {   // background re-index without Loading; Back then reads it (the lock orders the two)
            runCatching {
                mutateIndex(IndexRefresh(counts = true)) { current ->
                    val previous = current ?: readStoredIndex()
                    stamped(scanner.scan(root, deps.settings.extraFolders, deps.settings.defaultTags, previous), previous).also { lateMountHere = it.lateMount }
                }
                libraryDirty = false   // the index is this re-index's
                refreshCollectionNames()
                rebuildMainRows(); swapMainRows()   // the consoles and the collection counts follow the new index, names changed or not
                // Still on a Library page: the rows were built before this scan, so refresh them (selection clamped).
                (_screen.value as? Screen.Settings)?.takeIf { it.section.library }?.let { s ->
                    val page = settingsPage(s.section, s.confirmRemove)
                    _screen.value = page
                    _selection.value = selectableOn(page, _selection.value)
                }
            }.onFailure { if (it is CancellationException) throw it }   // a failed re-index leaves the old index: it no longer fits, so Back rescans
        }
    }

    /** The console's next default tag ([step] -1: the previous one, for LEFT). Leaving Default emulators rescans. */
    fun cycleDefaultTag(consoleId: String, step: Int = 1) {
        settingValue(SettingsRow(SettingsKind.DEFAULT_TAG, "", consoleId = consoleId))?.change(step)   // one tag (or none): nothing to pick
    }

    /** Settings sections and the section menu: UP/DOWN move, A opens or acts, B goes back. */
    private fun onSettingsButton(s: Screen, button: NxButton) {
        (s as? Screen.Settings)?.confirmRemove?.let { extra ->
            when (button) {
                NxButton.A -> removeExtra(extra)
                NxButton.B -> refreshSettings()
                else -> Unit
            }
            return
        }
        when (button) {
            // Past System's group labels and gaps.
            NxButton.UP, NxButton.DOWN -> _selection.value = if (s is Screen.Settings) settingsStep(s.rows, _selection.value, if (button == NxButton.UP) -1 else 1)
                else if (button == NxButton.UP) _selection.value.up() else _selection.value.down()
            NxButton.A -> openSelected()
            NxButton.B -> back()
            NxButton.X -> (s as? Screen.Settings)?.rows?.getOrNull(_selection.value.index)?.let { row ->
                if (row.kind == SettingsKind.SHORTCUT_BIND) row.shortcut?.let { setShortcut(it, null) }   // back to None
                else if (selectedSettingShowsInfo()) showSettingInfo(_selection.value.index)
            }
            // NX Redux ITEM_CYCLE: every value row steps on LEFT/RIGHT too (A steps forward). A default emulator steps
            // through its console's tags.
            NxButton.LEFT, NxButton.RIGHT -> if (s is Screen.SettingsMenu) {
                // Two panes: RIGHT on a category moves into its page, like A. LEFT never leaves a page (B does).
                if (settingsSplit && button == NxButton.RIGHT) openSelected()
            } else (s as? Screen.Settings)?.rows?.getOrNull(_selection.value.index)?.let { row ->
                if (row.enabled) settingValue(row)?.change(if (button == NxButton.LEFT) -1 else 1)   // a dimmed row (one tag) stays
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
        systems.singleOrNull()?.let { s -> return s.games.map { Listed(s, it) } }
        val all = systems.flatMap { s -> s.games.map { Listed(s, it) } }.sortedWith(compareBy({ it.game.shownName.lowercase() }, { it.system.tag }))
        val labels = MergedLabels.labels(all.map { MergedLabels.Row(it.game.shownName, it.game.fileName, it.system.tag) })
        return all.mapIndexed { i, e -> labels[i]?.let { e.copy(label = it) } ?: e }
    }

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

    /**
     * [playableLookup] of [systems] (the playable cache by default): every line resolves in one lookup instead of a walk
     * over every game ([resolveIn] gives the same answers). Built once per systems list, here when [playableOffMain] has
     * not built it yet (one pass over the games, for a resolve outside a coroutine).
     */
    private fun playable(systems: List<SystemFolder> = systemsCache): Map<GameRef, Listed> =
        playableOf?.takeIf { it.first === systems }?.second ?: playableLookup(systems).also { playableOf = systems to it }

    /** [playable], built on [AppDeps.compute] when it is not built yet: a list opening or refreshing never walks the library on the main thread. */
    private suspend fun playableOffMain(systems: List<SystemFolder> = systemsCache): Map<GameRef, Listed> =
        playableOf?.takeIf { it.first === systems }?.second ?: withContext(deps.compute) { playableLookup(systems) }.also { playableOf = systems to it }

    /**
     * Hidden games resolved against the whole library (coreless and Unassigned included) so every one can be unhidden,
     * through one [gameLookup] per index, built on [AppDeps.compute].
     */
    private suspend fun hiddenEntries(): List<Listed> {
        val index = indexLock.withLock { library } ?: return emptyList()
        val lookup = hiddenLookupOf?.takeIf { it.first === index }?.second
            ?: withContext(deps.compute) { gameLookup(index.toSystems()) }.also { hiddenLookupOf = index to it }
        return hiddenPaths.mapNotNull { resolveIn(lookup, it) }
    }

    private suspend fun entriesFor(source: ListSource): List<Listed> = when (source) {
        is ListSource.Console -> consoleGroup(source.system)?.let(::consoleEntries) ?: emptyList()
        ListSource.Recent -> deps.userData.readRecents().map { it.path }.let { paths -> resolveDistinct(playableOffMain(), paths) }
        is ListSource.Collection -> collections.read(source.name).let { lines -> playableOffMain().let { lookup -> lines.mapNotNull { resolveIn(lookup, it) } } }
        ListSource.Hidden -> hiddenEntries()
        is ListSource.Search -> GameSearch.search(searchCorpus(), source.query)
        is ListSource.Filtered -> GameSearch.filter(entriesFor(source.parent), source.query)
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
        if (q.isEmpty() || _screen.value !is Screen.Systems) return
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

    /** START on the main menu (content or tab row): the search prompt, on the last search. Not over a menu. */
    fun openSearch() {
        val s = _screen.value as? Screen.Systems ?: return
        if (s.menu != null || s.search != null || !canSearch) return
        _tabFocus.value = false
        _screen.value = s.copy(search = lastSearch)
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
    private fun onSearchButton(s: Screen.Systems, draft: String, button: NxButton) {
        when (button) {
            NxButton.A -> if (draft.isNotBlank()) { _screen.value = s.copy(search = null); showSearch(draft) }
            NxButton.B, NxButton.START -> _screen.value = s.copy(search = null)
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
            val at = picked?.let { p -> entries.indexOfFirst { it.game.path == p } }?.takeIf { it >= 0 } ?: listSearchFrom
            val parent = (source.parent as? ListSource.Console)?.let { c -> consoleGroup(c.system)?.let { ListSource.Console(it) } } ?: source.parent
            showList(parent, entries, at)
        }
    }

    /** The Layout icon: opens the dropdown on the layout in use, or closes it when open. */
    fun toggleLayoutMenu() {
        val s = _screen.value as? Screen.Games ?: return
        if (s.menu != null || s.search != null || !deps.settings.gameListLayoutOptions) return
        _screen.value = s.copy(layoutMenu = if (s.layoutMenu != null) null else GameListLayouts.indexOf(deps.settings.gameListStyle, deps.settings.gameListOrientation, deps.settings.gameListVerticalAlignment))
    }

    /** The Layout dropdown has the buttons: UP / DOWN move, A picks, B closes. */
    private fun onLayoutMenuButton(s: Screen.Games, at: Int, button: NxButton) {
        when (button) {
            NxButton.UP -> _screen.value = s.copy(layoutMenu = (at - 1).coerceAtLeast(0))
            NxButton.DOWN -> _screen.value = s.copy(layoutMenu = (at + 1).coerceAtMost(GameListLayouts.all.lastIndex))
            NxButton.A -> pickLayout(at)
            NxButton.B -> _screen.value = s.copy(layoutMenu = null)
            else -> Unit
        }
    }

    /**
     * [index]'s layout for every game list (Layouts → Game lists layout, its orientation for Carousel and Backdrop, and a
     * vertical one's alignment); the dropdown closes.
     */
    fun pickLayout(index: Int) {
        val s = _screen.value as? Screen.Games ?: return
        val layout = GameListLayouts.all.getOrNull(index) ?: return
        deps.settings.gameListStyle = layout.style
        if (MenuStyles.hasOrientation(layout.style)) deps.settings.gameListOrientation = layout.orientation
        layout.alignment?.let { deps.settings.gameListVerticalAlignment = it }
        gridColumns = 0   // a Grid reports its own columns on its first layout
        _screen.value = s.copy(layoutMenu = null)   // a new screen: the page redraws in the new style, on the same game
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
            is Screen.Systems -> rememberMenuSelection()
            is Screen.Settings -> sectionSelections[s.section] = _selection.value
            else -> Unit
        }
    }

    /** Rebuilds the open screen from its source after an action; an emptied list leaves as B would. [select]: put the row on that game path. */
    private suspend fun refreshOpenList(select: String? = null) {
        when (val s = _screen.value) {
            is Screen.Games -> {
                val entries = entriesFor(s.source)
                if (_screen.value !== s) return   // the user navigated while the source was read
                if (entries.isEmpty()) { leaveList(s.source); return }
                val index = select?.let { p -> entries.indexOfFirst { it.game.path == p } }?.takeIf { it >= 0 } ?: _selection.value.index
                // A console's systems as they are now (a hide may have emptied one tag of a merged console).
                val source = (s.source as? ListSource.Console)?.let { c -> consoleGroup(c.system)?.let { ListSource.Console(it) } } ?: s.source
                _screen.value = Screen.Games(source, entries)
                _selection.value = ListSelection(index, entries.size).clampedTo(entries.size)
                refreshResume()
            }
            is Screen.Systems -> menuScreen(s.category ?: menuCategory).let { m ->
                showMenu(m, keptIndex(s, m))   // a gone tab: the fallback's own row
            }
            else -> Unit
        }
    }

    // --- Launching ---

    private fun listed(game: GameEntry): Listed? = (_screen.value as? Screen.Games)?.entries?.firstOrNull { it.game == game }

    fun play(game: GameEntry) { listed(game)?.let { launch(it, resumeSlot = null) } }

    /** The context menu's Play from start (a game with a save state, which A resumes): [entry] from the start. */
    private fun playFromStart(entry: Listed) {
        rememberMenuSelection()   // Home's game, as A on it (a game list has none to remember)
        launch(entry, resumeSlot = null)
    }

    /**
     * Continues [entry] from its last save, read now (never a slot read earlier); without one it plays [entry] when
     * [orPlay] (A on a game), else does nothing. An Unassigned game never launches; an Android game has no save.
     */
    private fun resumeFromSave(entry: Listed, orPlay: Boolean = false) {
        val e = entry.takeIf { !it.system.unassigned && !it.system.android } ?: return
        scope.launch {
            val info = deps.userData.resumeInfo(e.system.tag, e.game.fileName)
            if (info.canResume) launch(e, info.slot) else if (orPlay) launch(e, resumeSlot = null)
        }
    }

    /**
     * A netplay launch ([NetplayController]'s agreement): [entry] under the agreed core (which may not be its folder's),
     * never resumed, with [session] for the runner. Everything else is [launch]'s.
     */
    fun launchNetplay(entry: Listed, session: NetplaySession) {
        val core = CoreRegistry.forId(session.coreId) ?: return
        launch(entry, resumeSlot = null, coreOverride = core, netplay = session)
    }

    private fun launch(entry: Listed, resumeSlot: Int?, coreOverride: CoreDescriptor? = null, netplay: NetplaySession? = null, fromSwitcher: Boolean = false) {
        val listed = entry.system
        val game = entry.game
        // After a game the menu is its own tab again (the pushed one is not restored): dropped only for a launch that
        // happens (an app's start, or the runner's call), so a coreless, in-flight, vanished or unreadable game leaves the
        // push for B to return to.
        if (listed.android) { dropPushedTab(); androidApps.launchApp(entry); return }
        if (coreOverride == null && listed.core == null) return
        if (launchJob?.isActive == true) return   // a launch is already in flight
        switcherJob?.cancel()   // a switcher still loading must not open over the launched game
        val job = scope.launch {
            val gone = "Could not find ${game.fileName}. If it was moved or deleted, use Rescan in Settings → Library."
            if (!stillThere(game)) {   // the index can be older than the tree: no recents entry, no runner call
                _error.value = gone
                return@launch
            }
            // A netplay launch keeps the agreed core.
            val sys = (if (coreOverride == null) classicNesSystem(entry) else null) ?: listed
            val core = coreOverride ?: sys.core ?: return@launch
            // A .cue/.m3u/.ccd/.toc loads the files it names from the same folder; all must be there before the core starts.
            val lookup = try {
                DiscSheet.companions(deps.source, game.folder, game.fileName)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                null
            }
            val companions = when (lookup) {
                null -> { _error.value = gone; return@launch }
                is CompanionLookup.Missing -> { _error.value = "Missing ${lookup.name} next to ${lookup.sheet}"; return@launch }
                is CompanionLookup.Found -> lookup.companions
            }
            // An fbneo set also loads its parent and BIOS sets: the ones beside it go along, a BIOS not beside it is checked in the system dir.
            // The set table is fbneo's: flycast's NAOMI/Atomiswave zips, loaded as is too, take none of it.
            var systemSets = emptyList<String>()
            val fbneoSet = core.id == "fbneo" && CoreRegistry.loadsArchive(sys.tag, game.fileName)
            // A NAOMI/Atomiswave clone takes its parent set from beside it (flycast opens /rom/<parent>.zip); its BIOS is GameActivity's check.
            val naomiArchive = core.id == "flycast" && CoreRegistry.loadsArchive(sys.tag, game.fileName)
            val all = if (fbneoSet) {
                val arcade = try {
                    // The set table's first read parses it (hundreds of KB): off the main thread.
                    val sets = withContext(deps.compute) { deps.arcadeSets() }
                    ArcadeCompanions.find(deps.source, game.folder, game.fileName, sets)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    _error.value = gone; return@launch
                }
                systemSets = arcade.systemSets
                companions + arcade.companions
            } else if (naomiArchive) {
                val parent = try {
                    val sets = withContext(deps.compute) { deps.naomiSets() }
                    // A zipped disc (not one of flycast's sets) is unpacked at launch and takes no parent.
                    if (!CoreRegistry.loadsArchive(sys.tag, game.fileName, sets)) emptyList()
                    else ArcadeCompanions.findParent(deps.source, game.folder, game.fileName, sets)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    _error.value = gone; return@launch
                }
                companions + parent
            } else companions
            // The Brick records the shown name on every open (launcher.c Recents_setAlias(self->name)), replacing a stale one.
            val recent = Recent(game.path, game.shownName)
            // Only a game the core actually loaded is recent: the runner can still refuse it (bad archive, BIOS, load error).
            // The callback captures the recorder alone (not this state), so a pending start retains nothing of the UI.
            val recorder = recents
            // Checked last, right before the runner: a game started during an import or a Device Sync could later write over
            // a save that just arrived.
            SaveTransferRules.launchRefusal(deps.saveTransfer?.importing == true, deps.deviceSync?.running == true)?.let { showNotice(it); return@launch }
            dropPushedTab()
            switcherReturn = fromSwitcher   // set only once the runner is called: a refused launch never gets here
            deps.runner.launch(GameSession(sys.tag, game.shownName, core, game.ref, game.folder, game.fileName, resumeSlot, all, systemSets = systemSets, gamePath = game.path,
                artPath = panelArt(game.boxartRef?.value, game.box2dRef?.value, game.gridRef?.value, game.screenshotRef?.value), netplay = netplay)) {
                recorder.record(recent)
            }
        }
        launchJob = job
        job.invokeOnCompletion { if (launchJob === job) launchJob = null }
    }

    /**
     * A Classic NES Series cart ([ClassicNes]) that would run gpSP only as the console's default runs mGBA:
     * mGBA is recorded as its Emulator pick, as the Emulator menu would ([Sidecars.set], then [moveInIndex]), so its
     * states, Game Settings, resume and menus all follow MGBA from this launch on; the user can still pick GBA there,
     * which this never overrides. The MGBA system to launch under; null: launch as listed. The header is read only
     * here, never by a scan: a bare ROM's first bytes, or an archive's ROM entry's ([AppDeps.archiveHead]).
     */
    private suspend fun classicNesSystem(entry: Listed): SystemFolder? {
        val sys = entry.system
        val game = entry.game
        if (sys.tag != ClassicNes.GPSP_TAG) return null
        val picked = try {
            sidecars.read(game.sourceId, game.folder, game.relativeDir)[game.fileName]
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            return null
        }?.takeIf { CoreRegistry.consoleTag(it) == CoreRegistry.consoleTag(sys.tag) }   // another console's tag is ignored, as by the scan
        // Only a ROM mGBA's core takes (a .gba): a .bin would list under GBA again on the next scan, and mGBA's unpack would refuse it.
        val mgba = CoreRegistry.forTag(ClassicNes.MGBA_TAG) ?: return null
        val tag = ClassicNes.launchTag(sys.tag, picked) {
            try {
                if (Names.isArchive(game.fileName)) deps.archiveHead(game.ref, game.fileName, mgba.extensions, ClassicNes.HEAD_BYTES)
                else if (Names.extensionOf(game.fileName) in mgba.extensions) deps.source.readHead(game.ref, ClassicNes.HEAD_BYTES)
                else null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                null
            }
        }
        if (tag == sys.tag) return null
        // A failed write still runs mGBA now; the next launch reads the header again.
        val recorded = try {
            sidecars.set(game.sourceId, game.folder, game.relativeDir, game.fileName, tag)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            false
        }
        if (recorded && moveInIndex(game, sys, tag)) refreshOpenList(select = game.path)
        return sys.copy(tag = tag, core = mgba)
    }

    /** The file is still where the index says: a non-directory child of its folder. Any failure (a revoked tree) counts as gone. */
    private suspend fun stillThere(game: GameEntry): Boolean = try {
        deps.source.child(game.folder, game.fileName)?.isDirectory == false
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        false
    }

    /**
     * The app came to the foreground (a card may have been inserted meanwhile). Only on the main menu with nothing open,
     * so a list or a page never changes under the user: an extra recorded offline that is mounted again rescans (as
     * [load] would), and a home that was offline at start gets the probe and skeleton it skipped. No tree query while
     * nothing came back. Home's stats are recomputed and its rows rebuilt (Continue's save may be new); that
     * rebuild uses app-private reads only (recents, pins, resume markers, tool pins, the in-memory index), never the tree.
     */
    fun onForeground() {
        checkArt(reuse = true)   // art deleted or added while away (only changed art folders are listed)
        if (!minimalPending) _minimalOpening.value?.let { minimalJob?.cancel(); _minimalOpening.value = null }   // back from a game a Minimal band opened
        onLauncherChanged()   // back from Android's home settings or the role dialog: System re-reads its state
        refreshInstalled()    // apps may have been installed or removed meanwhile
        refreshGameInfo()   // back from a game (or another app): its time and unlocks may have moved
        homeTab.refreshStatsIfAsked()   // Home's stats too (before the menu's first show, that show computes them)
        // Back from a game resumed from the switcher: the switcher again (its game first, the latest recent), over the
        // page it opened from. Over the main menu, after the rows rebuilt below (a swap would abandon an open under way);
        // read again there, so a Home landing meanwhile (onNewIntent may follow onStart) drops it.
        val s = _screen.value as? Screen.Systems ?: run { reopenSwitcher(); return }
        if (s.menu != null) { switcherReturn = false; return }
        if (!firstSetup) updaterOnMainMenu()   // back over the menu (from a game, or the install-permission screen)
        scope.launch { rebuildMainRows(); swapMainRows(); reopenSwitcher() }   // Continue's save (its resume frame) may be new since the rows were built
        val root = home() ?: return
        if (foregroundJob?.isActive == true) return   // a quick second onStart: the first check (or its scan) covers it
        foregroundJob = scope.launch {
            val ix = indexLock.withLock { library } ?: return@launch
            if (backOnline(ix)) {
                if (_screen.value !== s) return@launch   // the user moved on during the check
                _screen.value = Screen.Loading
                scanNow(root, null)
                return@launch
            }
            if (offlineHomeSaid == root && !deps.source.isOffline(root) && _screen.value === s) afterIndexStart(root)
        }
    }

    /** A game resumed from the switcher ended: the switcher opens again, once (a list or the main menu; elsewhere, nothing). */
    private fun reopenSwitcher() {
        if (!switcherReturn) return
        switcherReturn = false
        openSwitcher()
    }

    /** Re-reads the highlighted game's resume marker: a list's, Home's Continue card's, pinned or shelf game's (after returning from a game). */
    fun refreshResume() {
        resumeJob?.cancel()
        val s = _screen.value
        val index = _selection.value.index
        val e = when (s) {
            is Screen.Games -> s.entries.getOrNull(index)?.takeIf { s.source != ListSource.Hidden }   // hidden games are not resumed from here
            // Continue and Home's pinned and shelf games (their A Resume or A Play): the focused one only, read off the main thread.
            is Screen.Systems -> when (val row = s.rows.getOrNull(index)) {
                is MainRow.Continue -> row.entry
                is MainRow.Pin -> row.entry.takeIf { s.category == MenuCategory.HOME }
                is MainRow.Shelf -> row.entry.takeIf { s.category == MenuCategory.HOME }
                else -> null
            }
            else -> null
        }?.takeIf { !it.system.unassigned && !it.system.android } ?: run { _resumeInfo.value = ResumeInfo.NONE; return }
        resumeJob = scope.launch {
            val info = deps.userData.resumeInfo(e.system.tag, e.game.fileName)
            if (_screen.value === s && _selection.value.index == index) _resumeInfo.value = info   // drop stale reads
        }
    }

    fun openSwitcher() {
        val here = _screen.value
        if (here !is Screen.Systems && here !is Screen.Games) return
        _tabFocus.value = false   // the menu under it comes back on its content
        if (switcherJob?.isActive == true) return   // already loading
        val sel = _selection.value
        switcherJob = scope.launch {
            val entries = switcherEntries()
            if (_screen.value !== here) return@launch   // user navigated or launched meanwhile
            beforeSwitcher = here to sel
            navStep(NavMotion.SWITCHER_OPEN)
            _screen.value = Screen.Switcher(entries, 0, list = deps.settings.switcherLayout == SwitcherLayout.LIST)
        }
    }

    /** The Tools tab, on the tool last opened there. */
    fun openTools() {
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
        _tabFocus.value = false   // Home lands on its content (a stale-library reload too)
        // Onboarding (and its embedded Device Sync) stays put, as first setup does.
        if (_screen.value is Screen.Onboarding || (_screen.value == Screen.DeviceSync && onboardingSync)) return
        when (_screen.value) { Screen.Loading, is Screen.PermissionsStep, Screen.PickFolder, Screen.AddFirstExtra, is Screen.AndroidGamesStep, is Screen.FilePicker, is Screen.StorageUpgrade -> return; else -> Unit }
        switcherJob?.cancel()
        loader.cancel()
        if (loader.active) return   // a non-cancellable load (the library rescan) runs on and lands by itself
        val first = menuTabs.firstOrNull()?.category ?: return
        switcherReturn = false   // Home over a game resumed from the switcher: Home, not the switcher, when it ends
        val s = _screen.value
        if (s is Screen.Switcher) closeSwitcher()
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

    // --- RetroAchievements: the panel, the details pane and the Tools pages live in RaScreenController ---

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
        artworkScreens?.let { ToolRow(ToolKind.ARTWORK, "Artwork", null) },
        ToolRow(ToolKind.CHEAT_DB, "Cheat Database", null),
        deps.deviceSync?.let { ToolRow(ToolKind.DEVICE_SYNC, "Device Sync", null) },
        deps.launcher?.let { ToolRow(ToolKind.APPS, "Apps", null) },
    ) + appToolRows()).sortedBy { it.label.lowercase() } + ToolRow(ToolKind.SETTINGS, "Settings", null)

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

    /** [row]'s tool opened; any but an app ([ToolKind.APP], which launches) then has the focus ([toolOnTop]). */
    private fun openTool(row: ToolRow) {
        noteToolOrigin()
        openToolNow(row)
        if (row.kind != ToolKind.APP && _screen.value !is Screen.Systems) _toolOnTop.value = true
    }

    private fun openToolNow(row: ToolRow) {
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
                val c = deps.deviceSync ?: return
                c.open()   // Ready on the cable, or the live page of a sync still running
                _screen.value = Screen.DeviceSync
                // Sync ROMs first where it is offered (the option above the links), as setup's sync step.
                publishSyncSetup(if ((c.page.value as? DeviceSyncPage.Ready)?.romsAvailable == true) SyncFocus.ROMS else SyncFocus.LINK)
            }
            ToolKind.SETTINGS -> openSettingsMenu()
            ToolKind.APPS -> openToolApps(AppPick.TOOLS)
            ToolKind.APP -> androidApps.openApp(row)
        }
    }

    /** Device Sync's buttons go to its controller; B on Ready (it returns false) leaves the tool (by a rescan when ROMs arrived). */
    private fun onDeviceSyncButton(button: NxButton) {
        val sync = deps.deviceSync
        val page = sync?.page?.value
        if (onboardingSync) noteOnboardingSync(page)
        // Ready (the tool and setup's sync step alike): the d-pad goes Sync ROMs (where available) → cable → Wi-Fi (→ Skip
        // in setup) and round; A, LEFT or RIGHT on Sync ROMs flips it; A on Skip goes on.
        val focus = _syncSetup.value?.focus ?: SyncFocus.LINK
        if (page is DeviceSyncPage.Ready && sync != null) {
            val rows = syncSetupRows(page, skip = onboardingSync)
            if (button == NxButton.UP || button == NxButton.DOWN) {
                val at = syncSetupIndex(page, focus, skip = onboardingSync)
                syncSetupTap((at + (if (button == NxButton.UP) -1 else 1) + rows.size) % rows.size, open = false)
                return
            }
            if (focus == SyncFocus.ROMS && (button == NxButton.A || button == NxButton.LEFT || button == NxButton.RIGHT)) { sync.flipRoms(); return }
            if (onboardingSync && focus == SyncFocus.SKIP && button == NxButton.A) return leaveOnboardingSync(forward = true)
        }
        // Setup: A on Done (Continue) goes on; leaving Ready (B) goes back a step.
        if (onboardingSync && button == NxButton.A && page is DeviceSyncPage.Done) return leaveOnboardingSync(forward = true)
        if (sync?.onButton(button) == true) { publishSyncSetup(); return }
        if (onboardingSync) return leaveOnboardingSync(forward = false)
        _syncSetup.value = null
        if (romsPending) { romsPending = false; rescan() } else showTools()
    }


    /**
     * Leaves setup's sync step: on to the next step ([forward]) or back to the one before. ROMs that arrived are scanned
     * first; the load then shows the step onboarding is on.
     */
    private fun leaveOnboardingSync(forward: Boolean) {
        onboardingSync = false
        _syncSetup.value = null
        if (forward) {
            val next = onboarding.next() ?: return finishOnboarding()
            deps.settings.onboardingStep = next.name
        } else {
            onboarding.back()?.let { deps.settings.onboardingStep = it.name }
        }
        if (romsPending) { romsPending = false; rescan() } else showOnboarding()
    }

    /** The sync step's result from the page Device Sync shows: an ended sync's outcome. */
    private fun noteOnboardingSync(page: DeviceSyncPage?) {
        onboardingSynced = when (page) {
            is DeviceSyncPage.Done -> "done"
            is DeviceSyncPage.Failed -> if (page.reason == DeviceSyncText.STOPPED) "stopped" else "failed"
            else -> onboardingSynced
        }
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


    /** Each recent resolved against the index by [matches], with no SAF call. A recent not in the index is hidden, not dropped from recent.txt. */
    private suspend fun switcherEntries(): List<SwitcherEntry> =
        deps.userData.readRecents().mapNotNull { recent ->
            val e = resolveOne(recent.path)?.takeUnless { it.system.android } ?: return@mapNotNull null   // never one (not recorded): kept out anyway
            val info = deps.userData.resumeInfo(e.system.tag, e.game.fileName)
            if (info.canResume) SwitcherEntry(e.system, e.game, info) else null
        }.distinctBy { it.game.path }   // a hidden copy's line and its home game's are one game

    private fun closeSwitcher() {
        val (screen, selection) = beforeSwitcher
            ?: menuScreen().let { m -> m to (m.category?.let(tabSelections::get) ?: ListSelection(0, 0)).clampedTo(m.rows.size) }
        beforeSwitcher = null
        navStep(NavMotion.SWITCHER_CLOSE)
        _screen.value = screen
        homeTab.publishHomeView()   // the restored Home rows' own view, not one built while the switcher was up
        _selection.value = selection
        refreshResume()
        // A Y Remove changed recent.txt: once written, rebuild the tabs, so Home's Continue moves past the game (the
        // snapshot above still shows it); the highlight stays on its row where that row is still there.
        switcherRemoval?.let { removal ->
            switcherRemoval = null
            scope.launch { removal.join(); rebuildMainRows(); swapMainRows() }
        }
    }

    private fun onSwitcherButton(s: Screen.Switcher, button: NxButton, repeat: Boolean = false) {
        if (s.confirmRemove) {
            when (button) {
                NxButton.A -> removeFromSwitcher(s)
                NxButton.B, NxButton.SELECT -> _screen.value = s.copy(confirmRemove = false)
                else -> Unit
            }
            return
        }
        // List: a game list's UP/DOWN, a fresh press wrapping past an end (a held one stops there); LEFT/RIGHT do nothing.
        val at = ListSelection(s.index, s.entries.size)
        when (button) {
            NxButton.UP -> if (s.list) _screen.value = s.copy(index = at.up(wrap = !repeat).index)
            NxButton.DOWN -> if (s.list) _screen.value = s.copy(index = at.down(wrap = !repeat).index)
            NxButton.LEFT -> if (!s.list) _screen.value = s.copy(index = at.up(wrap = false).index)
            NxButton.RIGHT -> if (!s.list) _screen.value = s.copy(index = at.down(wrap = false).index)
            NxButton.A -> s.entries.getOrNull(s.index)?.let { e ->
                closeSwitcher()
                launch(Listed(e.system, e.game), e.resume.slot, fromSwitcher = true)
            }
            NxButton.Y -> if (s.entries.getOrNull(s.index) != null) _screen.value = s.copy(confirmRemove = true)
            NxButton.B, NxButton.SELECT -> closeSwitcher()
            else -> Unit
        }
    }

    /** A tap on the switcher List's row [index]: the switcher's own index (the menu's selection under it stays as it was). */
    fun selectSwitcherRow(index: Int) {
        val s = _screen.value as? Screen.Switcher ?: return
        if (s.list && !s.confirmRemove && index in s.entries.indices) _screen.value = s.copy(index = index)
    }

    private fun removeFromSwitcher(s: Screen.Switcher) {
        val e = s.entries.getOrNull(s.index) ?: run { _screen.value = s.copy(confirmRemove = false); return }
        val remaining = s.entries - e
        _screen.value = s.copy(entries = remaining, index = s.index.coerceAtMost((remaining.size - 1).coerceAtLeast(0)), confirmRemove = false)
        val path = e.game.path
        val previous = switcherRemoval
        switcherRemoval = scope.launch { previous?.join(); recentsLock.withLock { deps.userData.writeRecents(Recents.remove(deps.userData.readRecents(), path)) } }
    }

    /**
     * Opens main menu row [index] of [s]: a console or collection's list, a game (resumed, or played), a tool, a hidden tab.
     * [forward]: how a list comes in.
     */
    private fun openMainRow(s: Screen.Systems, index: Int, forward: NavMotion = NavMotion.FORWARD) {
        when (val row = s.rows.getOrNull(index)) {
            is MainRow.System -> { intendNav(forward); openSystem(row.system) }
            // Home's pinned game, as Continue: its save read now resumes, none plays. An Android game opens its app.
            is MainRow.Pin -> { rememberMenuSelection(); if (row.entry.system.android) androidApps.launchApp(row.entry) else resumeFromSave(row.entry, orPlay = true) }
            // A shelf game, as a pin (never an Android game).
            is MainRow.Shelf -> { rememberMenuSelection(); resumeFromSave(row.entry, orPlay = true) }
            is MainRow.Collection -> { intendNav(forward); openList(ListSource.Collection(row.name)) }
            is MainRow.Tool -> { rememberMenuSelection(); openTool(row.row) }
            // Its save is read again now: one made since the row was built resumes, one deleted since plays.
            is MainRow.Continue -> { rememberMenuSelection(); resumeFromSave(row.entry, orPlay = true) }
            is MainRow.PinnedTool -> { rememberMenuSelection(); openTool(row.row) }
            // Home's "+N" and Pick a game: the Tools / Consoles tab, pushed over Home while Layouts hides it.
            is MainRow.MoreTools -> showTab(MenuCategory.TOOLS)
            MainRow.PickAGame -> showTab(MenuCategory.CONSOLES)
            null -> Unit
        }
    }

    /** Opens game list row [index] of [s]: its save state resumes (its menu has Play from start), none plays; an Android game opens its app. */
    private fun openGameRow(s: Screen.Games, index: Int) {
        s.entries.getOrNull(index)?.let { if (it.system.android || it.system.unassigned) play(it.game) else resumeFromSave(it, orPlay = true) }
    }

    /** The page a Minimal open belongs to ([MinimalOpening.page]): a tab's category, a game list's source; null for any other screen. */
    private fun minimalPage(s: Screen): Any? = when (s) {
        is Screen.Systems -> s.category
        is Screen.Games -> s.source
        else -> null
    }

    /** [s]'s row [index] as a Minimal open checks it is still there: a tab's row, a game list's game. */
    private fun minimalRow(s: Screen, index: Int): Any? = when (s) {
        is Screen.Systems -> s.rows.getOrNull(index)
        is Screen.Games -> s.entries.getOrNull(index)
        else -> null
    }

    /**
     * A on a Minimal page (a tab, Home's too, or a game list): the other names fade out and the selected row's band grows
     * over the screen ([minimalOpening]), and the row opens as it fills ([MINIMAL_OPEN_MS]) if its page still shows it; the
     * band clears a moment later, behind the game, tool or list (sooner on the next press: [onButton]). A console or a
     * collection whose list is Minimal too opens as the names have faded ([MINIMAL_LEAVE_MS]), its name morphing into the
     * list's title ([NavMotion.MINIMAL_FORWARD]). A game starts launching as the band starts growing: the emulator loads
     * under it and fades in over it, instead of after a black screen.
     */
    private fun openMinimal(s: Screen) {
        if (minimalPending) return
        val page = minimalPage(s) ?: return
        val index = _selection.value.index
        val row = minimalRow(s, index) ?: return
        val list = (row is MainRow.System || row is MainRow.Collection) && deps.settings.gameListStyle == MenuStyle.MINIMAL
        val game = row !is MainRow || row is MainRow.Pin || row is MainRow.Shelf || row is MainRow.Continue
        minimalJob?.cancel()
        _minimalOpening.value = MinimalOpening(page, index, band = !list)
        minimalPending = true
        minimalJob = scope.launch {
            delay(if (list || game) MINIMAL_LEAVE_MS else MINIMAL_OPEN_MS)
            minimalPending = false
            val now = _screen.value
            if (minimalPage(now) == page && minimalRow(now, index) == row) when (now) {
                is Screen.Systems -> openMainRow(now, index, if (list) NavMotion.MINIMAL_FORWARD else NavMotion.FORWARD)
                is Screen.Games -> openGameRow(now, index)
                else -> Unit
            }
            delay(if (game) MINIMAL_GAME_CLEAR_MS else MINIMAL_CLEAR_MS)
            _minimalOpening.value = null
        }
    }

    fun select(index: Int) {
        if (minimalPending) return   // a Minimal open's band is growing: the selection stays on the row opening
        _tabFocus.value = false   // a touch on the content takes the focus there
        if (index != _selection.value.index) cancelCapture()   // a tap on another Shortcuts row ends a wait for a press
        _selection.value = _selection.value.copy(index = index).clampedTo(_selection.value.count)
        (_screen.value as? Screen.Systems)?.takeIf { it.category == MenuCategory.HOME }?.let { homeTab.noteHomeFocus(_selection.value.index, it.rows) }
        refreshResume()
    }

    fun openSelected() {
        if (loader.active) return   // a screen is loading: only B (cancel) acts
        if (minimalPending) return   // a Minimal open's band is growing: a tap waits too
        _tabFocus.value = false   // a tap on the highlighted item (A on the tab row never gets here: it returns to the content)
        when (val s = _screen.value) {
            is Screen.Onboarding -> onboardingRow(_selection.value.index)
            is Screen.Systems -> if (styleOf(s) == MenuStyle.MINIMAL) openMinimal(s) else openMainRow(s, _selection.value.index)
            is Screen.SettingsMenu -> s.sections.getOrNull(_selection.value.index)?.let(::openSettings)
            is Screen.ToolApps -> if (s.pick == AppPick.GAMES) androidApps.toggleAndroidGame(s) else androidApps.launchDrawerApp(s)
            is Screen.CheatDb -> onCheatDbButton(s, NxButton.A)
            Screen.DeviceSync -> onDeviceSyncButton(NxButton.A)
            is Screen.RetroAchievements -> ra.onRaButton(s, NxButton.A)
            is Screen.RaDownload -> Unit   // its rows only read (A, Done, comes through onButton)
            is Screen.RaGames, is Screen.RaGame -> ra.onRaBrowserButton(s, NxButton.A)
            is Screen.RaAchievement -> Unit   // a detail page: nothing to open
            is Screen.RaSettings -> ra.onRaSettingsButton(s, NxButton.A)
            // Hidden unhides at once, Minimal or not: nothing opens.
            is Screen.Games -> if (s.source == ListSource.Hidden) unhideSelected()
                else if (styleOf(s) == MenuStyle.MINIMAL) openMinimal(s) else openGameRow(s, _selection.value.index)
            is Screen.Switcher -> onSwitcherButton(s, NxButton.A)
            is Screen.Settings -> s.rows.getOrNull(_selection.value.index)?.let { row ->
                val value = settingValue(row)
                if (value != null) {
                    // A value row: a picker row (many or long values) opens its picker; the others step forward, as RIGHT.
                    val touch = settingsTouchRow(row, s.choices.getOrNull(_selection.value.index), s.section)
                    if (row.enabled && touch.control == NxSettingControl.PICKER) openSettingPicker(_selection.value.index)
                    else if (row.enabled) value.change(1)
                } else when (row.kind) {
                    SettingsKind.HOME -> pickHome()
                    SettingsKind.ADD_EXTRA -> pickExtra()
                    SettingsKind.EXTRA -> refreshSettings(confirm = row.extra)
                    SettingsKind.RESCAN -> rescanFromSettings()
                    SettingsKind.LAYOUT_HOME, SettingsKind.LAYOUT_CONSOLES, SettingsKind.LAYOUT_COLLECTIONS, SettingsKind.LAYOUT_TOOLS,
                    SettingsKind.LAYOUT_GAME_LISTS, SettingsKind.LAYOUT_TITLE_HINTS, SettingsKind.LAYOUT_SWITCHER ->
                        SettingsSection.entries.firstOrNull { it.opener == row.kind }?.let { sectionSelections[s.section] = _selection.value; openSettings(it) }
                    SettingsKind.DEFAULTS -> { sectionSelections[s.section] = _selection.value; openSettings(SettingsSection.DEFAULT_EMULATORS) }
                    SettingsKind.SAVES -> { sectionSelections[s.section] = _selection.value; openSettings(SettingsSection.SAVES) }
                    SettingsKind.EXPORT_SAVES -> deps.saveTransfer?.exportSaves(::showTransferNotice)
                    SettingsKind.LICENSES_LINK -> deps.openUrl(LICENSES_URL)
                    SettingsKind.IMPORT_SAVES -> { sectionSelections[s.section] = _selection.value; openSettings(SettingsSection.IMPORT_SAVES) }
                    SettingsKind.IMPORT_FOLDER -> importSaves(ImportSource.FOLDER)
                    SettingsKind.IMPORT_ZIP -> importSaves(ImportSource.ZIP)
                    SettingsKind.IMPORT_RETROARCH -> importSaves(ImportSource.RETROARCH)
                    SettingsKind.IMPORT_DONE -> leaveSubPage(SettingsSection.IMPORT_RESULT)
                    SettingsKind.PAD_LAYOUT -> { sectionSelections[s.section] = _selection.value; padLayoutConsole = PadLayouts.SHARED_KEY; openSettings(SettingsSection.PAD_LAYOUT) }
                    SettingsKind.PAD_LAYOUT_SHAPE -> row.padShape?.let { openPadEditor(it) }
                    SettingsKind.PAD_LAYOUT_RESET -> resetPadLayoutConsole()
                    SettingsKind.SHORTCUTS -> { sectionSelections[s.section] = _selection.value; openSettings(SettingsSection.SHORTCUTS) }
                    // Without a controller too: one plugged in next answers it (the row says to connect one).
                    SettingsKind.SHORTCUT_BIND -> row.shortcut?.let(::startCapture)
                    SettingsKind.HIDDEN -> openList(ListSource.Hidden)
                    SettingsKind.UNASSIGNED -> unassignedSystem()?.let { sectionSelections[s.section] = _selection.value; openSystem(it) }
                    SettingsKind.EMULATORS -> { sectionSelections[s.section] = _selection.value; emulatorSettings.openList(systemsCache) }
                    SettingsKind.INFO -> Unit   // About: read-only
                    SettingsKind.COPY_INFO -> copyDeviceInfo()
                    SettingsKind.APP_UPDATE -> deps.updater?.let(::appUpdateRowA)
                    SettingsKind.HOME_SCREEN -> deps.launcher?.let { it.setHome(!it.isHome()); refreshSettings() }
                    SettingsKind.ANDROID_GAMES -> openToolApps(AppPick.GAMES)
                    SettingsKind.RESET_DEFAULTS -> resetToDefaults(s.section)
                    SettingsKind.ALL_FILES -> deps.allFilesAccess?.openSettings()
                    else -> Unit   // a value row with nothing to step ([settingValue] null): as before, nothing
                }
            }
            Screen.PickFolder, is Screen.PermissionsStep -> setupStepRow(_selection.value.index)
            is Screen.StorageUpgrade -> storageUpgradeRow(_selection.value.index)
            is Screen.CopySaves -> copySavesRow(_selection.value.index)
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
            val text = deps.indexStore.read()
            val art = withContext(deps.compute) {   // the whole library index: decode off the main thread
                HashMap<String, String?>().also { art ->
                    text?.let(LibraryIndexCodec::decode)?.systems?.forEach { s -> s.games.forEach { g -> art[GamePath.of(GameRef(g.source, g.dir, g.file))] = g.boxart ?: g.box2d ?: g.screenshot } }
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

    internal val emulatorSettings = EmulatorSettingsController(
        scope, _screen, _selection, _error, deps.coreOptions, deps.emulatorConfigs, deps.coreDefaults,
        host = object : EmulatorSettingsHost {
            override fun showEmulatorsSettings() = showSettingsOn(SettingsSection.EMULATORS, SettingsKind.EMULATORS)
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
            s.games.map { g ->
                val art = ArtRefs(
                    g.boxartRef?.value, g.box2dRef?.value, g.screenshotRef?.value, g.gridRef?.value, g.heroRef?.value,
                    wheel = g.wheelRef?.value, mix = g.mixRef?.value,
                )
                LibraryGameRef(
                    gamePath = g.path, tag = s.tag, name = g.shownName, fileName = g.fileName, rom = g.ref, sourceId = g.sourceId,
                    relativeDir = g.relativeDir, folderGame = g.lookupDir != g.relativeDir,
                    have = ArtHave.of(art, needMix = settings.artMix), datTitle = datTitle(s.tag, g.fileName), art = art,
                )
            }
        } + if (withHidden) emptyList() else androidArtworkGames()

    /**
     * The scraper's games with each arcade set's raw DAT title (fbneo's table; flycast's for a DC zip/7z): libretro names
     * arcade art by it. Each table is read at most once per call, off the calling thread, and only when a system uses it.
     */
    private suspend fun artworkGamesWithTitles(withHidden: Boolean): List<LibraryGameRef> {
        val ix = indexLock.withLock { library }
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

    /** The Android games console's games for the scraper: by app id and label, "has art" when a screenshot is stored (it never wants a box). */
    private fun androidArtworkGames(): List<LibraryGameRef> = shownAndroidConsole()?.games.orEmpty().map { g ->
        LibraryGameRef(
            gamePath = g.ref.value, tag = ANDROID_TAG, name = g.shownName, fileName = g.fileName, rom = g.ref, sourceId = g.sourceId,
            relativeDir = g.relativeDir, folderGame = false,
            have = ArtHave(box = true, screenshot = g.screenshotRef != null), android = true,
        )
    }

    private val artworkHost = object : ArtworkHost {
        override suspend fun libraryGames(): List<LibraryGameRef> = artworkGamesWithTitles(withHidden = false)
        override suspend fun allLibraryGames(): List<LibraryGameRef> = artworkGamesWithTitles(withHidden = true)

        /**
         * Under [indexLock] only the in-memory update: the write and the refresh are batched ([flushArt]: after a pause in
         * the results, and at the job's end), never calling the controller.
         */
        override suspend fun applyArt(gamePath: String, art: ArtRefs) {
            val ref = GamePath.split(gamePath) ?: return
            mutateIndex(refresh = null, persistNow = false) { current ->
                val game = current?.systems?.firstNotNullOfOrNull { s ->
                    s.games.firstOrNull { it.source == ref.sourceId && it.dir == ref.relativeDir && it.file == ref.fileName }
                } ?: return@mutateIndex null
                current.setArt(game, art)
            } ?: return
            scheduleArtFlush()
        }

        override suspend fun flushArt() = this@AppState.flushArt()

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
        if (library == null) return
        artCheckReused = reuse
        artCheck = scope.launch {
            running?.join()
            val from = library ?: return@launch
            val art = try { scanner.readArt(from, reuse) } catch (e: CancellationException) { throw e } catch (e: Throwable) { return@launch }
            mutateIndex(IndexRefresh(rows = true, openList = true, ui = true)) { current -> current?.takeIf { it === from }?.withArt(art) } ?: return@launch
            withContext(uiContext) { artworkScreens?.libraryChanged() }
        }
    }

    /** Null without a controller (no Tools row, no Fetch art). */
    private val artworkScreens: ArtworkScreenController? = deps.artwork?.invoke(artworkHost)?.let { c ->
        ArtworkScreenController(
            scope, _screen, _selection, _error, deps.settings, loader, c,
            host = object : ArtworkScreenHost {
                override fun showNotice(text: String) = this@AppState.showNotice(text)
                override fun showTools() = this@AppState.showTools()
                override fun games(): List<LibraryGameRef> = artworkGames(library)
                override fun gamesInputs(): List<Any?> = listOf(library, hiddenPaths, androidConsole)
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

    /** The Artwork sign-in modal's live username / password (the composable reports every change). */
    fun artworkDraftUser(text: String) { artworkScreens?.draftUser(text) }
    fun artworkDraftPassword(text: String) { artworkScreens?.draftPassword(text) }
    /** The key prompt's live text (SteamGridDB or TheGamesDB). */
    fun artworkDraftKey(text: String) { artworkScreens?.draftKey(text) }

    /**
     * The page the left pane shows beside [screen] in the Artwork column browser ([artworkSplitView]): the Artwork page
     * beside Fetch by console, Progress and Settings, Settings beside Reset, the row that opened it lit. Null otherwise.
     */
    fun artworkParent(screen: Screen): ArtworkParent? = artworkScreens?.parentOf(screen)

    /**
     * The art fetch job as it moves (null without a controller): the Artwork left pane is drawn again on each emission,
     * so its counts and rows follow a running fetch while the right pane stays the same screen.
     */
    val artworkJob: StateFlow<com.nxredux.mobile.scraper.ScrapeState>? get() = artworkScreens?.job

    /** A tap on row [index] of the dimmed Artwork left pane showing [parent] ([artworkParent]): that row opens in place of the right pane. */
    fun artworkParentTap(parent: Screen, index: Int) { artworkScreens?.parentTap(parent, index) }

    // --- Context menu: hosted by a game list or the main menu (a pinned or recent row) ---

    /** The context menu's navigation, prompts and actions; it reaches back into this state only through [ContextMenuHost]. */
    private val contextMenus = ContextMenuController(
        scope, _screen, _selection, _error, deps.userData, deps.settings, sidecars, collections, recentsLock,
        host = object : ContextMenuHost {
            override val pins: List<Shortcut> get() = this@AppState.pins
            override val toolPins: List<String> get() = this@AppState.toolPins
            override suspend fun writeToolPins(keys: List<String>): Boolean = this@AppState.writeToolPins(keys)
            override fun select(index: Int) = this@AppState.select(index)
            override fun showNotice(text: String) = this@AppState.showNotice(text)
            override suspend fun rebuildMainRows() {
                refreshCollectionNames()   // a new or emptied collection shows or leaves the tab
                this@AppState.rebuildMainRows()
            }
            override fun collectionChanged(name: String) = invalidateCollectionCounts(name)
            override fun selectCollection(name: String) = this@AppState.selectCollection(name)
            override fun menuClosed() { if ((_screen.value as? Screen.Systems)?.tabs != menuTabs) swapMainRows() }
            override suspend fun refreshOpenList(select: String?) = this@AppState.refreshOpenList(select)
            override suspend fun setInTools(id: String, on: Boolean) = androidApps.setInTools(id, on)
            override fun rescan() = this@AppState.rescan()
            override suspend fun applyHidden(entry: Listed, hidden: Boolean, closeMenu: Boolean) = this@AppState.applyHidden(entry, hidden, closeMenu)
            override suspend fun renameInIndex(game: GameEntry, name: String): Boolean = this@AppState.renameInIndex(game, name)
            override fun finishSwitch(game: GameEntry, from: SystemFolder, tag: String) = this@AppState.finishSwitch(game, from, tag)
            override fun openGameSettings(entry: Listed) = emulatorSettings.openGame(entry)
            override fun canFetchArt(entry: Listed): Boolean = artworkScreens?.canFetch(entry) == true
            override fun fetchArt(entry: Listed) { artworkScreens?.fetchGame(entry) }
            override fun netplayOffered(entry: Listed): Boolean = this@AppState.netplay?.offers(entry) == true
            override fun openNetplay(entry: Listed) = this@AppState.openNetplay(entry)
            override val netplayInMenu: Boolean get() = !launcherChrome().netplayHint
            override val achievementsInMenu: Boolean get() = hasAchievements && !deps.settings.gameListAchievementsPanel && !_dualMenu.value
            override fun openAchievements() = ra.openRaPanel()
            override suspend fun canResume(entry: Listed): Boolean = deps.userData.resumeInfo(entry.system.tag, entry.game.fileName).canResume
            override fun playFromStart(entry: Listed) = this@AppState.playFromStart(entry)
            override fun hiddenTabs(): List<MenuCategory> = this@AppState.hiddenTabs()
            override fun openHiddenTab(category: MenuCategory) = this@AppState.openHiddenTab(category)
        },
    )

    // --- Netplay: the sheet over a game list or the main menu (context menu's Netplay, or Y on a game) ---

    /** The Netplay sheet; null on a platform without netplay. While its page is set it takes every pad press and Back. */
    val netplay: NetplayController? = deps.netplay?.let { NetplayController(scope, it, ::launchNetplay, work = deps.compute) }   // handshakes and save files off the main thread

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
    fun openContextMenu(index: Int? = null) { _tabFocus.value = false; contextMenus.openContextMenu(index) }

    /** A long press on the tab row or the single-tab title: the hidden-tab menu (the tab row's focus ends only if it opens). */
    fun openTabMenu() { if (contextMenus.openTabMenu()) _tabFocus.value = false }

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
            try {
                applyHidden(entry, hidden = false, closeMenu = false)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _error.value = "Something went wrong: ${e.message ?: e::class.simpleName}"
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
        systemsCache = indexLock.withLock { library?.playableSystems(next.toSet()) } ?: systemsCache
        invalidateCollectionCounts()   // a hidden game leaves the counts
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

    /** The Grid body reports its column count (it knows the screen size); the d-pad then steps by tiles. */
    fun onGridLayout(columns: Int, rows: Int = 2) {
        gridColumns = columns.coerceAtLeast(0); gridRows = rows.coerceAtLeast(1)
    }

    /** Home's layout (Layouts → Home): Grid (the dashboard), List or Carousel. */
    internal fun homeStyle(): MenuStyle = MenuStyles.styleOf(deps.settings.menuStyles, MenuCategory.HOME)

    /** The style the screen showing now is drawn in: a main menu tab's own, the game list style, else List. */
    internal fun currentStyle(): MenuStyle = styleOf(_screen.value)

    /** The style [screen] is drawn in (a page sliding out keeps its own): a main menu tab's own, the game list style, else List. */
    internal fun styleOf(screen: Screen): MenuStyle = when (val s = screen) {
        is Screen.Systems -> s.category?.let { MenuStyles.styleOf(deps.settings.menuStyles, it) } ?: MenuStyle.LIST
        is Screen.Games -> deps.settings.gameListStyle
        else -> MenuStyle.LIST
    }

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
        is Screen.Games -> MenuStyles.effectiveOrientation(deps.settings.gameListStyle, deps.settings.gameListOrientation)
        else -> MenuOrientation.HORIZONTAL
    }

    /**
     * The d-pad on a laid-out Grid (main menu tab or game list) steps by tiles. Past the first / last tile the main menu
     * switches tab (a fresh press only, never a key [repeat]: a held key stops at the edge); a game list stops. On the main
     * menu UP from the top row reaches the tab row ([gridAtTop], [toTabRow]) instead of wrapping; with the tab row hidden
     * it stays. False when the screen is not a laid-out Grid (the caller does the List step).
     */
    private fun gridMove(button: NxButton, repeat: Boolean = false): Boolean {
        if (gridColumns <= 0 || currentStyle() != MenuStyle.GRID) return false
        val s = _screen.value
        if (s !is Screen.Systems && s !is Screen.Games) return false
        val sel = _selection.value
        _gridPresses.value += 1
        if (s is Screen.Systems && button == NxButton.UP && gridAtTop(sel.index, sel.count, gridColumns, gridRows)) { toTabRow(repeat); return true }
        val next = gridStep(sel.index, sel.count, gridColumns, button, gridRows)
        if (next == null) {
            // A pushed tab has no tab to switch to: its Grid wraps LEFT / RIGHT at its ends instead (a fresh press only).
            if (s is Screen.Systems && s.pushed) {
                if (!repeat) { _selection.value = sel.copy(index = if (button == NxButton.RIGHT) 0 else sel.count - 1); refreshResume() }
                return true
            }
            if (s is Screen.Systems && !repeat) switchTab(if (button == NxButton.RIGHT) 1 else -1)
            return true
        }
        _selection.value = sel.copy(index = next)
        refreshResume()
        return true
    }

    /**
     * The d-pad on a Carousel row (main menu tab or game list) or a game list's Backdrop row: LEFT / RIGHT step one
     * item, UP / DOWN do nothing (on the main menu UP reaches the tab row while it shows: the row is its top). A row needs
     * no layout report, so it works from the first frame. Past the first / last item the main menu switches tab (a fresh
     * press only, never a key [repeat]); a game list stops. A Vertical one is a stack ([stackMove]). False when the screen is not a row.
     */
    private fun rowMove(button: NxButton, repeat: Boolean): Boolean {
        val style = currentStyle()
        if (style != MenuStyle.CAROUSEL && style != MenuStyle.BACKDROP) return false
        val s = _screen.value
        if (s !is Screen.Systems && s !is Screen.Games) return false
        if (orientationOf(s) == MenuOrientation.VERTICAL) return stackMove(s, button, repeat)
        if (s is Screen.Systems && button == NxButton.UP) { toTabRow(repeat); return true }   // a row is all top row
        if (button == NxButton.UP || button == NxButton.DOWN) return true
        val sel = _selection.value
        val next = rowStep(sel.index, sel.count, button)
        if (next == null) { if (s is Screen.Systems && !repeat) switchTab(if (button == NxButton.RIGHT) 1 else -1); return true }
        _selection.value = sel.copy(index = next)
        refreshResume()
        return true
    }

    /**
     * The d-pad on a Vertical Carousel stack (main menu tab or game list) or a game list's Backdrop stack ([stackStep]):
     * UP / DOWN step one item; past an end a fresh press wraps (UP on the first to the last, DOWN on the last to the
     * first), a held key's [repeat] stops there, as the List. UP reaches the tab row only from an empty main menu stack. LEFT / RIGHT have no move of their own: on the main menu
     * a fresh press switches tab (never a key [repeat]); a game list ignores them. False for any other button.
     */
    private fun stackMove(s: Screen, button: NxButton, repeat: Boolean): Boolean {
        when (button) {
            NxButton.LEFT, NxButton.RIGHT -> { if (s is Screen.Systems && !repeat) switchTab(if (button == NxButton.RIGHT) 1 else -1); return true }
            NxButton.UP, NxButton.DOWN -> {
                val sel = _selection.value
                val next = stackStep(sel.index, sel.count, button, fresh = !repeat)   // only an empty stack's UP is null: the tab row
                if (next == null) { if (s is Screen.Systems) toTabRow(repeat); return true }
                if (next != sel.index) {
                    _selection.value = sel.copy(index = next)
                    refreshResume()
                }
                return true
            }
            else -> return false
        }
    }

    /**
     * UP from the top of a main menu tab's content: the tab row takes the d-pad, on a fresh press only (a held UP stops at
     * the top). Hidden (Layouts → Page title, in effect: [launcherChrome]) it never does: the press stays put.
     */
    private fun toTabRow(repeat: Boolean) {
        val tabs = (_screen.value as? Screen.Systems)?.tabs?.size ?: 0
        if (!repeat && launcherChrome().tabRow && tabs > 1) _tabFocus.value = true
    }

    /**
     * A button while the main menu's tab row has the d-pad ([tabFocus]). LEFT / RIGHT switch tabs (a fresh press only, as
     * [switchTab] past a content edge), L1 / R1 too; DOWN, A and B return to the content where it was; UP lands on its
     * bottom ([toContentBottom], a fresh press only); SELECT opens the switcher as from the content; START the search prompt
     * ([openSearch], dropping the flag); MENU, X, Y and the rest do nothing. False (the flag dropped) when the main menu is
     * not showing with no menu open.
     */
    private fun onTabRowButton(button: NxButton, repeat: Boolean): Boolean {
        val s = _screen.value as? Screen.Systems
        if (s == null || s.menu != null) { _tabFocus.value = false; return false }
        when (button) {
            NxButton.LEFT, NxButton.RIGHT -> if (!repeat) switchTab(if (button == NxButton.RIGHT) 1 else -1)
            NxButton.L1 -> switchTab(-1)
            NxButton.R1 -> switchTab(1)
            NxButton.DOWN, NxButton.A, NxButton.B -> _tabFocus.value = false
            NxButton.UP -> if (!repeat) { _tabFocus.value = false; toContentBottom(s) }
            NxButton.SELECT -> openSwitcher()
            NxButton.START -> openSearch()
            else -> Unit
        }
        return true
    }

    /**
     * UP on the tab row: the focus wraps to the bottom of [s]'s content. Home: its bottom-most item ([homeBottom]); a laid-out
     * Grid: the bottom row in the same column, else the last tile ([gridBottom]); a Carousel row is one row:
     * the item it was on, but a Vertical one (a stack) its last item, as a List; a List: the last row.
     */
    private fun toContentBottom(s: Screen.Systems) {
        val sel = _selection.value
        if (sel.count <= 0) return
        val at = when {
            s.category == MenuCategory.HOME -> homeTab.bottom(sel, s.rows)
            else -> when (currentStyle()) {
                MenuStyle.GRID -> if (gridColumns > 0) { _gridPresses.value += 1; gridBottom(sel.index, sel.count, gridColumns, gridRows) } else sel.count - 1
                MenuStyle.CAROUSEL, MenuStyle.BACKDROP -> if (currentOrientation() == MenuOrientation.VERTICAL) sel.count - 1 else sel.index
                MenuStyle.LIST, MenuStyle.MINIMAL -> sel.count - 1
            }
        }.coerceIn(0, sel.count - 1)
        if (at == sel.index) return
        _selection.value = sel.copy(index = at)
        if (s.category == MenuCategory.HOME) homeTab.noteHomeFocus(at, s.rows)
        refreshResume()
    }

    /** A pad press. [repeat]: an auto-repeat of a held direction (the router's), not a fresh press; taps are fresh. */
    fun onButton(button: NxButton, repeat: Boolean = false) {
        if (minimalPending) return   // a Minimal open's band is growing: the row opens in a moment
        _minimalOpening.value?.let { minimalJob?.cancel(); _minimalOpening.value = null }   // opened: the next press clears its band
        _chooser.value?.let { onChooserButton(it, button); return }   // a touch chooser takes the pad while open
        _settingInfo.value?.let { onSettingInfoButton(it, button); return }   // a Settings row's info popup too
        // Press to bind waits on the controller's raw keys ([captureKey]): a touch hint's or the on-screen pad's B cancels, the rest wait.
        if (_capturingShortcut.value != null) { if (button == NxButton.B) cancelCapture(); return }
        if (_error.value != null) { if (button == NxButton.A || button == NxButton.B) dismissError(); return }
        if (_androidGamesAsk.value != null) {   // the Android games question: A Yes, B Not now
            if (button == NxButton.A) answerAndroidGames(true) else if (button == NxButton.B) answerAndroidGames(false)
            return
        }
        if (_updateConfirm.value != null) {   // About's confirm: A Update, B Cancel, up / down scroll long notes
            when (button) {
                NxButton.A -> answerUpdateConfirm(true)
                NxButton.B -> answerUpdateConfirm(false)
                NxButton.UP -> _updateScroll.tryEmit(-1)
                NxButton.DOWN -> _updateScroll.tryEmit(1)
                else -> Unit
            }
            return
        }
        if (updatePopupShows()) {   // the update dialog: A Update, B Not now, up / down scroll long notes
            when (button) {
                NxButton.A -> answerUpdate(true)
                NxButton.B -> answerUpdate(false)
                NxButton.UP -> _updateScroll.tryEmit(-1)
                NxButton.DOWN -> _updateScroll.tryEmit(1)
                else -> Unit
            }
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
        (_screen.value as? Screen.Onboarding)?.let { onOnboardingButton(it, button); return }
        netplay?.takeIf { it.page.value != null }?.let { it.onButton(button); return }   // the Netplay sheet over a game list
        _screen.value.takeIf { it is Screen.Settings || it is Screen.SettingsMenu }?.let { onSettingsButton(it, button); return }
        (_screen.value as? Screen.ToolApps)?.takeIf { it.menu == null }?.let { androidApps.onToolAppsButton(it, button); return }
        _screen.value.contextMenu?.let { contextMenus.onMenuButton(it, button); return }
        (_screen.value as? Screen.Systems)?.let { s -> s.search?.let { onSearchButton(s, it, button); return } }
        (_screen.value as? Screen.Games)?.let { s ->
            s.search?.let { onListSearchButton(s, it, button); return }
            s.layoutMenu?.let { onLayoutMenuButton(s, it, button); return }
            ra.raPanel.value?.let { ra.onRaPanelButton(it, button); return }
        }
        if (filePicker?.onButton(_screen.value, button) == true) return
        (_screen.value as? Screen.EmulatorOptions)?.let { if (onEmulatorTouchButton(it, button)) return }
        if (emulatorSettings.onButton(_screen.value, button)) return
        if (gameTimeScreen?.onButton(_screen.value, button) == true) return   // B without a modal falls through to back()
        (_screen.value as? Screen.ArtworkSettings)?.takeIf { button == NxButton.X && !it.dialogOpen }?.let {   // X: the selected row's info
            if (selectedSettingShowsInfo()) showSettingInfo(_selection.value.index); return
        }
        if (artworkScreens?.onButton(_screen.value, button) == true) return
        (_screen.value as? Screen.Switcher)?.let { onSwitcherButton(it, button, repeat); return }
        // The summary: LEFT and RIGHT flip the Move row as A does.
        (_screen.value as? Screen.FolderSummary)?.let { fs ->
            val rows = folderSummaryRows(fs)
            if ((button == NxButton.LEFT || button == NxButton.RIGHT) && rows.getOrNull(_selection.value.index)?.kind == SummaryRowKind.MOVE) {
                _screen.value = fs.copy(move = !fs.move); return
            }
        }
        (_screen.value as? Screen.CheatDb)?.let { onCheatDbButton(it, button); return }   // before the generic when: UP/DOWN use the action-row count
        if (_screen.value == Screen.DeviceSync) { onDeviceSyncButton(button); return }   // every button is the controller's
        (_screen.value as? Screen.RetroAchievements)?.let { ra.onRaButton(it, button); return }   // same: UP/DOWN use the action-row count
        (_screen.value as? Screen.RaDownload)?.let { ra.onRaDownloadButton(it, button); return }
        (_screen.value as? Screen.RaSettings)?.let {   // LEFT/RIGHT change values; dialogs take the buttons; X the selected row's info
            if (button == NxButton.X && !it.signingIn && it.confirm == null) { if (selectedSettingShowsInfo()) showSettingInfo(_selection.value.index); return }
            ra.onRaSettingsButton(it, button); return
        }
        _screen.value.takeIf { it is Screen.RaGames || it is Screen.RaGame || it is Screen.RaAchievement }?.let { ra.onRaBrowserButton(it, button); return }
        dropHiddenTabFocus()   // a tab row hidden while it had the d-pad (a setting or footer change it missed) gives it back
        if (_tabFocus.value && onTabRowButton(button, repeat)) return
        if (button.isDirection && homeTab.homeMove(button, repeat)) return   // Home first: its own two-dimensional moves
        when (button) {
            // A List: UP on the first row wraps to the last, DOWN on the last to the first, on the main menu too (only an
            // empty tab's UP reaches the tab row). On the launcher's lists (the main menu's and the game lists) a held
            // key's repeat stops at the end instead of wrapping.
            NxButton.UP -> if (!rowMove(button, repeat) && !gridMove(button, repeat)) {
                if (_screen.value is Screen.Systems && _selection.value.count == 0) toTabRow(repeat)
                else { _selection.value = _selection.value.up(wrap = !repeat || !launcherList()); refreshResume() }
            }
            NxButton.DOWN -> if (!rowMove(button, repeat) && !gridMove(button)) { _selection.value = _selection.value.down(wrap = !repeat || !launcherList()); refreshResume() }
            // The main menu: LEFT / RIGHT are L1 / R1 (a Grid or row switches only past its ends), on a fresh press only: a held
            // key's repeats never switch tabs. A List game list ignores them.
            NxButton.LEFT, NxButton.RIGHT -> if (!rowMove(button, repeat) && !gridMove(button, repeat) && !repeat && _screen.value is Screen.Systems) switchTab(if (button == NxButton.RIGHT) 1 else -1)
            NxButton.A -> openSelected()
            NxButton.B -> back()
            NxButton.Y -> when (val s = _screen.value) {
                is Screen.Games -> s.entries.getOrNull(_selection.value.index)?.let(::openNetplay)
                is Screen.Systems -> s.rows.getOrNull(_selection.value.index).listed()?.let(::openNetplay)   // Continue, a pin, a shelf game
                else -> Unit
            }
            NxButton.SELECT -> openSwitcher()
            NxButton.START -> if (_screen.value is Screen.Games) openListSearch() else openSearch()   // a game list searches its own games
            NxButton.X -> if (_screen.value is Screen.Games && !_dualMenu.value) ra.openRaPanel()   // the highlighted game's achievements (the second display's list has its own details)
            NxButton.MENU -> openContextMenu()
            NxButton.L1 -> switchTab(-1)
            NxButton.R1 -> switchTab(1)
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
    val textEntryOpen: Boolean
        get() = when (val s = _screen.value) {
            is Screen.RaSettings -> s.signingIn
            is Screen.Onboarding -> s.prompt   // setup's sign-in prompt
            is Screen.ArtworkSettings -> s.signingIn || s.editingKey
            is Screen.FilePicker -> s.prompt != null
            is Screen.Systems -> s.search != null || s.menu?.prompt.isTextEntry
            is Screen.Games -> s.search != null || s.menu?.prompt.isTextEntry
            else -> s.contextMenu?.prompt.isTextEntry
        }

    /**
     * Leaving Library must reload: the library changed since the last load, the index no longer fits the settings, or
     * some storage is known offline (the reload's probe reads it again once it is back).
     */
    private fun libraryStale(): Boolean {
        val root = home() ?: return true
        val ix = library ?: return true
        return libraryDirty || !ix.fits(root, deps.settings.extraFolders, deps.settings.defaultTags) ||
            offlineHomeSaid != null || ix.offline.isNotEmpty()
    }

    /** Returns true when a screen was popped. */
    fun back(): Boolean {
        if (_chooser.value != null) { closeChooser(); return true }   // a touch chooser: Back closes it
        if (_settingInfo.value != null) { closeSettingInfo(); return true }   // and a Settings row's info popup
        if (_capturingShortcut.value != null) { cancelCapture(); return true }   // press to bind: Back cancels it, the page stays
        if (_androidGamesAsk.value != null) { answerAndroidGames(false); return true }   // the dialog's B: Not now
        if (_updateConfirm.value != null) { answerUpdateConfirm(false); return true }   // About's confirm: Cancel
        if (updatePopupShows()) { answerUpdate(false); return true }   // the update dialog's B: Not now
        netplay?.takeIf { it.page.value != null }?.let { it.onButton(NxButton.B); return true }   // the Netplay sheet: its own B
        switcherJob?.cancel()   // B while the switcher loads abandons it
        if (loader.cancel()) return true   // likewise a screen load (the overlay): the screen underneath stays
        return when (val s = _screen.value) {
            is Screen.Onboarding -> { if (s.prompt) _screen.value = s.copy(prompt = false, draftPassword = "") else onboardingBack(); true }
            is Screen.Games -> {
                if (s.menu?.busy == true) return true   // a choice is being applied: keep the menu until the new list opens
                if (s.menu != null) { closeContextMenu(); return true }
                if (s.search != null) { _screen.value = s.copy(search = null); return true }
                if (s.layoutMenu != null) { _screen.value = s.copy(layoutMenu = null); return true }
                if (ra.backRaPanel()) return true
                // Lands only on the main menu (Hidden and Unassigned go to Settings); a Minimal list's title drops back into a Minimal tab's row.
                intendNav(if (styleOf(s) == MenuStyle.MINIMAL) NavMotion.MINIMAL_BACK else NavMotion.BACK)
                leaveList(s.source)
                true
            }
            is Screen.Systems -> if (s.search != null) { _screen.value = s.copy(search = null); true }
                else if (s.menu != null) { if (!s.menu.busy) closeContextMenu(); true }
                else if (_tabFocus.value) { _tabFocus.value = false; true }   // the tab row: back to the content, as B
                else if (s.pushed) { popHiddenTab(); true }
                else false
            is Screen.Settings -> {
                sectionSelections[s.section] = _selection.value
                // A sub-page goes back to its section. Library or Emulators: after a change ([libraryDirty]), or an extra set
                // or default tag changed behind the page's back (it fails the fit), it reloads (rescanning if needed); either
                // way it ends on the menu.
                if (s.section.parent != null) leaveSubPage(s.section)
                else if (s.section.reloads && libraryStale()) load { showSettingsMenu() } else showSettingsMenu()
                true
            }
            is Screen.SettingsMenu -> { settingsMenuSelection = _selection.value; showTools(); true }
            is Screen.ToolApps -> {
                if (s.menu != null) { if (!s.menu.busy) closeContextMenu() }
                else if (s.pick == AppPick.GAMES) showSettingsOn(SettingsSection.LIBRARY, SettingsKind.ANDROID_GAMES)
                // The drawer: back on its tile, found by kind (an app added meanwhile may sort before it).
                else if (menuCategory == MenuCategory.HOME) showTools()
                else showSystemsNow(MenuCategory.TOOLS) { it is MainRow.Tool && it.row.kind == ToolKind.APPS }
                true
            }
            Screen.AddFirstExtra -> {
                val page = homePage
                when {
                    !inFirstSetup() -> skipFirstExtra()
                    page != null -> { _screen.value = page; _selection.value = ListSelection(0, folderSummaryRows(page).size) }   // back to the home page
                    mediaHomeTaken -> backPastMediaHome()
                    else -> { _screen.value = Screen.PickFolder; _selection.value = ListSelection(0, 1) }
                }
                true
            }
            // First setup's home pick: back to the All files step while it still applies, else to the welcome (a lost home after setup has nothing before it).
            Screen.PickFolder -> if (inFirstSetup()) { backFromHomePick(); true } else false
            is Screen.FilePicker -> { filePicker?.onButton(s, NxButton.B); true }
            is Screen.PermissionsStep -> { if (deps.settings.onboardingDone == false) backToWelcome() else skipAllFiles(); true }
            is Screen.StorageUpgrade -> { storageUpgradeBack(s); true }   // required: B never leaves it for the library
            is Screen.CopySaves -> { if (!s.busy) copySavesRow(if (s.error != null) 0 else 1); true }   // B: Keep separate (or OK)
            is Screen.AndroidGamesStep -> { if (inFirstSetup()) showAddFirstExtra() else answerAndroidGames(false); true }
            // The summary: B stops a running move (after the current game); otherwise back to the ROMs folder step (the folder stays added).
            // The home page: back to the pick (a new pick shows its page again).
            is Screen.FolderSummary -> {
                when {
                    s.progress != null -> moveCancelled = true
                    s.extraId == Recents.HOME && mediaHomeTaken -> backPastMediaHome()
                    s.extraId == Recents.HOME -> { homePage = null; _screen.value = Screen.PickFolder; _selection.value = ListSelection(0, 1) }
                    else -> showAddFirstExtra()
                }
                true
            }
            is Screen.Switcher -> {
                if (s.confirmRemove) _screen.value = s.copy(confirmRemove = false) else closeSwitcher()   // back dismisses the confirm first
                true
            }
            is Screen.CheatDb -> { onCheatDbButton(s, NxButton.B); true }
            Screen.DeviceSync -> { onDeviceSyncButton(NxButton.B); true }
            is Screen.RetroAchievements, is Screen.RaGames, is Screen.RaGame, is Screen.RaAchievement, is Screen.RaSettings -> { ra.raBack(s); true }
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

    /**
     * The platform's home state or apps may have changed (the role dialog, Android's home settings, a new listing): System's
     * Use as home screen and Library's Android games re-read them.
     */
    fun onLauncherChanged() {
        val section = (_screen.value as? Screen.Settings)?.section
        if (section == SettingsSection.SYSTEM || section == SettingsSection.LIBRARY) refreshSettings()
    }

    /**
     * The role dialog came back without the home role: declined, or answered at once by an earlier "Don't ask again".
     * System re-reads the state and a notice says where the home app is set by hand.
     */
    fun onHomeRoleRefused() {
        onLauncherChanged()
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
        val apps = try { l.installedApps() } catch (e: CancellationException) { throw e } catch (e: Exception) { return }
        installedCache = apps
        onLauncherChanged()
        applyAndroidConsole()
        maybeAskAndroidGames()   // a game installed since the menu showed
    }

    /** The Android games console as [installedCache] and the settings make it now ([androidConsoleOf]); null with no game or before the first load. */
    private fun buildAndroidConsole(): SystemFolder? {
        val installed = installedCache ?: return null
        return androidConsoleOf(androidGames(installed)) { id -> deps.androidArtStore?.screenshot(id) }
    }

    /** The app ids the Android games console holds by the settings now (their icons are kept, [AppIcons.retain]). */
    private fun androidConsoleIds(): List<String> = androidGames(installedCache.orEmpty()).map { it.id }

    /**
     * Rebuilds [androidConsole]; the main menu's rows follow when they were built with another, and so does an open
     * Android list showing another with no menu over it (an emptied one leaves as B would). Compared with what each
     * shows, not the field's last value, so a refresh cancelled halfway is completed by the next.
     */
    private suspend fun applyAndroidConsole() {
        val next = buildAndroidConsole()
        androidConsole = next
        if (allMenuTabs.isNotEmpty() && next != menuAndroidConsole) {   // before the menu's first build, that build takes it
            rebuildMainRows()
            swapMainRows()
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
        if (deps.launcher == null || home() == null || deps.settings.androidGamesAsked || firstSetup) return
        if (_androidGamesAsk.value != null) return
        val s = _screen.value as? Screen.Systems ?: return
        if (s.menu != null) return
        val ask = androidGamesAskFor(installedCache ?: return) ?: return
        androidAskGuard?.cancel()
        androidAskGuard = scope.launch { delay(ANDROID_ASK_GUARD_MILLIS) }
        _androidGamesAsk.value = ask
    }

    /** The update dialog may show over [screen]: the main menu with no context menu, the Android games question or a Problem over it. */
    private fun updatePopupFits(ask: AndroidGamesAsk?, error: String?, screen: Screen): Boolean =
        ask == null && error == null && screen is Screen.Systems && screen.menu == null

    /** The update dialog is drawn and still fits what shows now (so a press is its answer). */
    private fun updatePopupShows(): Boolean =
        updatePopup.value != null && updatePopupFits(_androidGamesAsk.value, _error.value, _screen.value)

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

    /** What the host holds an install for: the Netplay sheet; "" off the main menu (a game list may be about to launch); null when free. */
    private fun updateHostBusy(): String? = when {
        netplay?.page?.value != null -> "Close Netplay to install"
        _screen.value !is Screen.Systems -> ""
        else -> null
    }

    /** A on About's update row: Check for updates (also after a failed check), Update to after one found a release; nothing while busy. */
    private fun appUpdateRowA(u: UpdateController) {
        when (val s = u.state.value) {
            UpdateState.Idle, is UpdateState.UpToDate -> u.checkNow()
            is UpdateState.Failed -> if (s.release == null) u.checkNow() else confirmUpdate(s.release)
            is UpdateState.Available -> confirmUpdate(s.release)
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
            val apps = try { l.installedApps() } catch (e: CancellationException) { throw e } catch (e: Exception) { null }
            if (apps != null) installedCache = apps
            val ask = apps?.let(::androidGamesAskFor)
            if (ask == null) { endFolderSteps(); next(); return@launch }
            afterAndroidStep = next
            androidStepShown = true
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

    /** The apps grid for [pick] ([AppsController.openToolApps]): the Apps tool, the drawer preview's tap, Settings -> Library -> Android games. */
    private fun openToolApps(pick: AppPick, selectId: String? = null) = androidApps.openToolApps(pick, selectId)

    /** The apps grid laid out [columns] tiles wide: the d-pad then steps by tiles. */
    fun onToolAppsLayout(columns: Int) = androidApps.onToolAppsLayout(columns)
}

/** A summary page row: the Move choice or Continue. */
internal enum class SummaryRowKind { MOVE, CONTINUE }

internal data class SummaryRow(val kind: SummaryRowKind, val row: NxRow)

/** The summary page's rows: the Move choice (only when some games are placed and nothing ran yet) and Continue. None while a move runs. */
internal fun folderSummaryRows(s: Screen.FolderSummary): List<SummaryRow> = buildList {
    if (s.progress != null) return@buildList
    val movable = if (s.extraId == Recents.HOME) s.summary.outside > 0 || s.summary.split > 0 else s.summary.games > s.summary.unassigned
    if (s.result == null && movable) {
        add(SummaryRow(SummaryRowKind.MOVE, NxRow("Move them into ${s.home}/Roms", secondary = if (s.move) "On" else "Off", cycleable = true)))
    }
    add(SummaryRow(SummaryRowKind.CONTINUE, NxRow("Continue")))
}

/** The summary page's headline and lines: what was found, per console, where the unassigned are; the move's progress or result. */
internal fun folderSummaryDetails(s: Screen.FolderSummary): List<String> = buildList {
    val sum = s.summary
    add(if (sum.games == 0) "No games found in ${s.name}" else if (sum.games == 1) "Found 1 game in ${s.name}" else "Found ${sum.games} games in ${s.name}")
    sum.byConsole.forEach { (name, n) -> add("$name ($n)") }   // a line each, largest first
    val after = ArrayList<String>()
    val progress = s.progress
    if (progress != null) after += if (progress.second == 0) "Moving…" else "Moving ${progress.first} of ${progress.second}…"
    else s.result?.let { r ->
        after += (listOfNotNull(
            "Moved ${r.moved} into ${s.home}/Roms",
            if (r.clashed > 0) "${r.clashed} left in place (same name already in Roms)" else null,
            if (r.failed > 0) "${r.failed} could not be moved" else null,
            if (r.cancelled) "Stopped" else null,
        ).joinToString(" · "))
    }
    if (progress == null) unassignedHint(sum.unassigned)?.let { after += it }
    // The home: how many lie outside its Roms (moved in by the Move row).
    if (progress == null && s.result == null && sum.outside > 0) after += if (sum.outside == 1) "1 of them is outside Roms." else "${sum.outside} of them are outside Roms."
    if (progress == null && s.result == null && sum.split > 0) {
        after += if (sum.split == 1) "1 console has more than one folder; they are merged into one."
        else "${sum.split} consoles have more than one folder; they are merged into one."
    }
    if (after.isNotEmpty() && sum.byConsole.isNotEmpty()) add("")   // a blank line between the console list and what follows
    addAll(after)
}

/** [AppSettings.onboardingStep] while first setup's folder steps are under way (no [OnboardingStep] has this name). */
private const val FOLDER_STEPS = "FOLDERS"

/** How often a home move rewrites the lines of the games it moved so far. */
private const val LINES_FLUSH_MS = 3_000L

/** Settings' Saves note and the library's notice until the one-time move is done. */
private const val SAVES_STILL_INSIDE = "Saves are still stored inside the app until your home folder is available."

/** The library's notice once the quiet move failed on several starts. */
private const val MOVE_STUCK = "Saves couldn't move to your home folder. They're still inside the app."

/** The copy offer's error when its copy did not finish. */
private const val COPY_FAILED = "Couldn't copy every save. The old folder still has them all."

/** The storage upgrade's error when its move did not finish. */
private const val UPGRADE_FAILED = "Couldn't move your saves. They're safe; the app will try again next time."

/** [s]'s rows: Allow and Not now on the first page, Pick folder on the pick, OK after (or after an error). */
internal fun storageUpgradeRows(s: Screen.StorageUpgrade): List<String> = when {
    s.error != null -> listOf("OK")
    s.stage == Screen.StorageUpgrade.Stage.EXPLAIN -> listOf("Allow", "Not now")
    s.stage == Screen.StorageUpgrade.Stage.PICK -> listOf("Pick folder")
    else -> listOf("OK")
}

/** [s]'s rows: Copy and Keep separate; OK once a copy failed. */
internal fun copySavesRows(s: Screen.CopySaves): List<String> = if (s.error != null) listOf("OK") else listOf("Copy", "Keep separate")

/** A Permissions page row. */
internal enum class PermissionRow { ALL_FILES, NOTIFICATIONS, CONTINUE }

/** [s]'s rows: a row per permission this device uses, then Continue. */
internal fun permissionRows(s: Screen.PermissionsStep): List<PermissionRow> = listOfNotNull(
    PermissionRow.ALL_FILES.takeIf { s.allFiles != null },
    PermissionRow.NOTIFICATIONS.takeIf { s.notifications != null },
    PermissionRow.CONTINUE,
)

/** Where setup's sync page has the selection: the Sync ROMs row, the link the page shows, or Skip. */
enum class SyncFocus { ROMS, LINK, SKIP }

/**
 * Device Sync's page state: its [title], where the selection is, and whether it is setup's sync step ([inSetup]: a
 * headline and the Skip row, which reads Continue once a sync ended there, [synced]).
 */
data class SyncSetup(val title: String, val synced: Boolean, val focus: SyncFocus, val inSetup: Boolean = true) {
    val skipSelected: Boolean get() = focus == SyncFocus.SKIP
}

/** A row of setup's sync page. */
sealed interface SyncSetupRow {
    data object Roms : SyncSetupRow
    data class Link(val link: SyncLink) : SyncSetupRow
    data object Skip : SyncSetupRow
}

/** Device Sync's Ready rows on [page]: Sync ROMs where ROM sync is available, the links, and in setup ([skip]) Skip. */
fun syncSetupRows(page: DeviceSyncPage.Ready, skip: Boolean = true): List<SyncSetupRow> =
    listOfNotNull(SyncSetupRow.Roms.takeIf { page.romsAvailable }) + SyncLink.entries.map { SyncSetupRow.Link(it) } + listOfNotNull(SyncSetupRow.Skip.takeIf { skip })

/** The row [focus] is on, on [page]. */
fun syncSetupIndex(page: DeviceSyncPage.Ready, focus: SyncFocus, skip: Boolean = true): Int = syncSetupRows(page, skip).indexOfFirst {
    when (focus) {
        SyncFocus.ROMS -> it == SyncSetupRow.Roms
        SyncFocus.LINK -> it == SyncSetupRow.Link(page.link)
        SyncFocus.SKIP -> it == SyncSetupRow.Skip
    }
}.coerceAtLeast(0)
