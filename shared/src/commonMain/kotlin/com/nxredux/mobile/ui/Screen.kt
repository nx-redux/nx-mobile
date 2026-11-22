package com.nxredux.mobile.ui

import com.nxredux.mobile.kids.KidsSchedule
import com.nxredux.mobile.kids.PlayWindow
import com.nxredux.mobile.kids.PinPurpose
import com.nxredux.mobile.library.MoveResult
import com.nxredux.mobile.achievements.RaGameView
import com.nxredux.mobile.gametime.GameTotal
import com.nxredux.mobile.launcher.InstalledApp
import com.nxredux.mobile.options.EmulatorPage
import com.nxredux.mobile.runner.ContentRef
import com.nxredux.mobile.settings.AccentPreset
import com.nxredux.mobile.gpudriver.GpuDriver
import com.nxredux.mobile.settings.MenuCategory
import com.nxredux.mobile.settings.PadTheme
import com.nxredux.mobile.settings.ExtraFolder
import com.nxredux.mobile.ui.nx.NxRow
import com.nxredux.mobile.ui.nx.NxSettingRow

sealed interface Screen {
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
        val touchRows: List<NxSettingRow>? = null,   // its rows as Settings' touch rows, drawn as the Settings rows block (null: WELCOME)
        // The artwork step's prompts over it: ScreenScraper's sign-in (its drafts [draftUser] / [draftPassword]) or a source's key.
        val artSignIn: Boolean = false, val artKey: ArtKeyFor? = null, val draftKey: String = "",
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
     * [confirmMove]: Continue with Move on asks first ([folderMoveAsk]); A moves, B goes back to the page.
     */
    data class FolderSummary(
        val extraId: String, val name: String, val home: String,
        val summary: com.nxredux.mobile.library.FolderSummary,
        val move: Boolean = false, val progress: Pair<Int, Int>? = null, val result: MoveResult? = null,
        val confirmMove: Boolean = false,
    ) : Screen
    /** A finished Import BIOS: what went where, read-only, with Done (A or B) back to Library on the Import BIOS row. */
    data class BiosImportResult(val summary: com.nxredux.mobile.bios.BiosIntakeSummary) : Screen
    data object Loading : Screen
    /** Kids Mode while Locked: the big tiles screen of the allowed apps, in place of the main menu. */
    data object KidsHome : Screen
    /**
     * Kids Mode's PIN prompt for [purpose]: the digits [typed] so far, the [wrong] tries in a row and, after the fifth,
     * [waitUntil] (epoch ms) before another try counts.
     */
    data class KidsPin(
        val purpose: PinPurpose, val typed: String = "", val wrong: Int = 0, val waitUntil: Long = 0,
        /** A line under the dots until a digit is typed (setup's "PINs didn't match"). */
        val message: String? = null,
    ) : Screen {
        /** The generated form, but the digits typed never reach a log or an assertion message. */
        override fun toString(): String =
            "KidsPin(purpose=$purpose, typed=${"*".repeat(typed.length)}, wrong=$wrong, waitUntil=$waitUntil, message=$message)"
    }
    /** After the right PIN on the tiles screen: Let them play's chips, Open full menu and Kids Mode settings. */
    data object KidsUnlock : Screen
    /**
     * Kids Mode's first switch-on, one [step] at a time ([KidsSetupStep]; its PIN step is [KidsPin]), or, not [setup],
     * Settings → Kids Mode → Allowed apps (the APPS step alone, Done back to the page). [apps]: the installed apps (APPS;
     * empty until listed), [allowed] the kid's, [schedule] the week's play times (TIMES).
     */
    data class KidsSetup(
        val step: KidsSetupStep, val setup: Boolean = true, val apps: List<InstalledApp> = emptyList(), val allowed: Set<String> = emptySet(),
        val schedule: KidsSchedule = KidsSchedule.DEFAULT,
    ) : Screen
    /**
     * One day's play time ([day], ISO): [window] null when it is locked; [kept] the window Locked off brings back. [setup]:
     * opened from setup's TIMES step (B returns there), else from the Kids Mode page.
     */
    data class KidsDayEdit(val day: Int, val window: PlayWindow?, val kept: PlayWindow, val setup: Boolean = false) : Screen
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
     * choice of [GameListLayouts.choices]. [tabBar]: this list is a main menu tab (the Games tab, [isTab]), drawn under
     * the tab row; null for every other list.
     */
    data class Games(val source: ListSource, val entries: List<Listed>, val menu: ContextMenu? = null, val search: String? = null, val layoutMenu: Int? = null, val tabBar: TabBar? = null) : Screen
    /**
     * [confirmRemove]: the "remove from recents?" dialog is open over the entry at [index]. [list]: Layouts → Game
     * Switcher layout is List (read on opening): a game list's List, UP/DOWN in place of LEFT/RIGHT.
     */
    data class Switcher(val entries: List<SwitcherEntry>, val index: Int, val confirmRemove: Boolean = false, val list: Boolean = false) : Screen
    /**
     * [confirmRemove]: the "remove this ROM folder?" dialog is open for that extra. [details]: the lines above the rows
     * (Library: games and consoles, last scan; Default emulators: its explanation); empty elsewhere.
     * [shot]: a Layouts group's picture of its values (null: none, as a hidden tab's). [banner]: a note under the title, above the rows.
     */
    data class Settings(
        val section: SettingsSection, val rows: List<SettingsRow>, val confirmRemove: ExtraFolder? = null, val details: List<String> = emptyList(),
        val shot: LayoutShot? = null, val banner: String? = null,
        /** Each row's values for its touch control ([SettingChoices]), in [rows]' order; null for a row that holds none. */
        val choices: List<SettingChoices?> = emptyList(),
        /** The selected reset row (Reset to defaults, Clear all, Reset console) asks first ([settingsResetAsk]): A resets, B keeps. */
        val confirmReset: Boolean = false,
        /** System → Player name's prompt is up (its draft): A saves it, B keeps the old name. */
        val nameDraft: String? = null,
        /** Emulation → GPU driver: "Remove driver?" is open for this driver; A removes it, B keeps it. */
        val confirmDriver: GpuDriver? = null,
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
    /** [confirmRemove]: the "remove Cheats & Patches?" dialog is open. */
    data class CheatDb(val confirmRemove: Boolean = false) : Screen
    /** Tools → Device Sync: no list selection here; its page (and the link choice) live in [AppDeps.deviceSync]. */
    data object DeviceSync : Screen
    /** The RetroAchievements tool page. */
    data object RetroAchievements : Screen
    /** Tools -> RetroAchievements -> the game data download's progress ([RaController.download]); [confirmCancel]: "Cancel download?" is open. */
    data class RaDownload(val confirmCancel: Boolean = false) : Screen
    /** Tools -> RetroAchievements -> Profile: the signed-in player's summary ([AppState.raProfile]), to read; B goes back. */
    data object RaProfile : Screen
    /** Tools -> RetroAchievements -> Achievements: the consoles of the cached games. */
    data class RaConsoles(val consoles: List<com.nxredux.mobile.achievements.RaConsoleView>) : Screen
    /** A console's cached games, opened from [RaConsoles]; [console] is its name (the page's title). */
    data class RaGames(val console: String, val games: List<RaGameView>) : Screen
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
     * top level). [confirmReset]: Reset to defaults asks first ([EmulatorRow.ResetDefaults]): A resets, B keeps.
     */
    data class EmulatorOptions(
        val page: EmulatorPage, val rows: List<EmulatorRow>, val category: String? = null, val game: Listed? = null,
        val confirmReset: Boolean = false,
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
     * (SteamGridDB or TheGamesDB) is open with [draftKey]; [confirmLogout]: the "Log out of ScreenScraper?" dialog is open;
     * [confirmClearCache]: the "Clear unused images" dialog is open.
     */
    data class ArtworkSettings(
        val rows: List<ArtworkSettingRow>, val signingIn: Boolean = false, val draftUser: String = "", val draftPassword: String = "",
        val editingKey: Boolean = false, val draftKey: String = "", val keyFor: ArtKeyFor = ArtKeyFor.SGDB,
        val confirmLogout: Boolean = false, val confirmClearCache: Boolean = false,
    ) : Screen {
        /** A prompt or dialog is open over the rows: it takes every input. */
        val dialogOpen: Boolean get() = signingIn || editingKey || confirmLogout || confirmClearCache

        /** The typed password and key never reach a log or an assertion message. */
        override fun toString(): String =
            "ArtworkSettings(rows=$rows, signingIn=$signingIn, draftUser=$draftUser, draftPassword=***, editingKey=$editingKey, draftKey=***, keyFor=$keyFor, confirmLogout=$confirmLogout, confirmClearCache=$confirmClearCache)"
    }
    /** Settings -> Reset artwork: All or a console; [confirm]: the delete dialog for that scope. */
    data class ArtworkReset(val scopes: List<ResetScope>, val confirm: ResetScope? = null) : Screen
}

/** This game list is the main menu's Games tab: every game, under the tab row ([Screen.Games.tabBar]). */
val Screen.Games.isTab: Boolean get() = source == ListSource.AllGames

/** The main menu: a [Screen.Systems], or the Games tab ([isTab]) while it is the tab shown (the app's dual-screen rule too). */
val Screen.isMainMenu: Boolean get() = this is Screen.Systems || (this is Screen.Games && isTab)

/** The tab the main menu shows ([isMainMenu]); null on any other screen (or a menu with no tab). */
internal val Screen.menuTab: MenuCategory?
    get() = when (this) {
        is Screen.Systems -> category
        is Screen.Games -> tabBar?.let { it.categories.getOrNull(it.index) }
        else -> null
    }

/** The main menu shows a hidden tab opened over it from MENU ([AppState.openHiddenTab]). */
internal val Screen.menuPushed: Boolean
    get() = when (this) {
        is Screen.Systems -> pushed
        is Screen.Games -> tabBar?.pushed == true
        else -> false
    }

/** Kids Mode's first switch-on, in order: PIN, allowed apps, play times, then the two permissions and home. */
enum class KidsSetupStep { PIN, APPS, TIMES, USAGE, ALARMS, HOME }

/** The screens Kids Mode itself shows: its tiles screen, PIN prompt and unlock sheet. */
internal fun isKidsScreen(s: Screen): Boolean = s == Screen.KidsHome || s == Screen.KidsUnlock || s is Screen.KidsPin

/** Whether [s] has a text field up ([AppState.textEntryOpen]). */
internal fun textEntryOpen(s: Screen): Boolean = when (s) {
    is Screen.RaSettings -> s.signingIn
    is Screen.Onboarding -> s.prompt || s.artSignIn || s.artKey != null   // setup's sign-in and art prompts
    is Screen.ArtworkSettings -> s.signingIn || s.editingKey
    is Screen.FilePicker -> s.prompt != null
    is Screen.Settings -> s.nameDraft != null
    is Screen.Systems -> s.search != null || s.menu?.prompt.isTextEntry
    is Screen.Games -> s.search != null || s.menu?.prompt.isTextEntry
    else -> s.contextMenu?.prompt.isTextEntry
}
