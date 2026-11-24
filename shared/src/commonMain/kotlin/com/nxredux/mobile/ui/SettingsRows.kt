package com.nxredux.mobile.ui

import com.nxredux.mobile.kids.KidsController
import com.nxredux.mobile.shortcuts.ShortcutAction
import com.nxredux.mobile.shortcuts.ShortcutSet
import com.nxredux.mobile.shortcuts.TouchSlot
import com.nxredux.mobile.launcher.InstalledApp
import com.nxredux.mobile.library.LibraryIndex
import com.nxredux.mobile.library.SystemFolder
import com.nxredux.mobile.netplay.NetplayCores
import com.nxredux.mobile.runner.BottomScreen
import com.nxredux.mobile.runner.Console
import com.nxredux.mobile.runner.ContentRef
import com.nxredux.mobile.runner.CoreRegistry
import com.nxredux.mobile.settings.AccentPreset
import com.nxredux.mobile.storage.SaveFormat
import com.nxredux.mobile.storage.StateFormat
import com.nxredux.mobile.settings.SecondaryAccent
import com.nxredux.mobile.settings.OptionTextSize
import com.nxredux.mobile.gpudriver.GpuDriver
import com.nxredux.mobile.gpudriver.GpuDriverChoice
import com.nxredux.mobile.gpudriver.PackageCheck
import com.nxredux.mobile.settings.PlayerName
import com.nxredux.mobile.settings.BackdropArt
import com.nxredux.mobile.settings.SwitcherLayout
import com.nxredux.mobile.settings.ConsoleArt
import com.nxredux.mobile.settings.GameListArt
import com.nxredux.mobile.settings.MenuCategory
import com.nxredux.mobile.settings.MenuOrientation
import com.nxredux.mobile.settings.MenuStyle
import com.nxredux.mobile.settings.MenuStyles
import com.nxredux.mobile.runner.WiiPad
import com.nxredux.mobile.skin.SkinTweaks
import com.nxredux.mobile.settings.PadTheme
import com.nxredux.mobile.settings.PadVibration
import com.nxredux.mobile.settings.GameRumble
import com.nxredux.mobile.settings.VerticalListAlignment
import com.nxredux.mobile.settings.Sources
import com.nxredux.mobile.storage.FileRefs
import com.nxredux.mobile.ui.nx.HomeShelf
import com.nxredux.mobile.ui.nx.NxButton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.nxredux.mobile.storage.ImportSummary
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.nxredux.mobile.tryOrNull
import com.nxredux.mobile.tryQuietly

/**
 * What Settings' rows and values read and change outside the settings themselves: the library as loaded (its systems,
 * index and home), the Shortcuts and System buttons pages' rows, Kids Mode's page, the main menu to rebuild after a
 * change, and the Settings pages to republish.
 */
internal interface SettingsRowsHost {
    fun home(): ContentRef?
    /** The data is in the home (the one-time move done, or none due). */
    fun dataMoved(): Boolean
    /** [media], the app's media folder, as a path to show. */
    fun mediaPath(media: ContentRef): String
    /** The library's systems as last loaded. */
    fun systems(): List<SystemFolder>
    fun indexSnapshot(): LibraryIndex?
    /** The home last found offline; null while it reads. */
    fun offlineHome(): ContentRef?
    /** The folders this run's last scan found mounted but not listing yet. */
    fun lateMount(): List<String>
    /** [ref] is a file ref read without All files access. */
    fun fileAccessOff(ref: ContentRef): Boolean
    /** The Android games console's apps; null until the installed apps first load. */
    fun androidGames(): List<InstalledApp>?
    fun unassignedSystem(): SystemFolder?
    fun hiddenPaths(): List<String>
    /** About's update row; null with nothing to offer. */
    fun updateLabel(): String?
    fun kidsRows(): List<SettingsRow>
    fun kidsSetEnabled(on: Boolean)
    fun shortcutRows(): List<SettingsRow>
    fun systemButtonRows(): List<SettingsRow>
    /** The Shortcuts page's draft (the stored set when nothing changed yet). */
    fun shortcutSet(): ShortcutSet
    fun setTouch(slot: TouchSlot, action: ShortcutAction?)
    fun rebuildHomeTab()
    suspend fun refreshMainRows()
    /** The game lists' style and stored orientation as drawn now. */
    fun gameListLayout(): Pair<MenuStyle, MenuOrientation>
    /** Whether the game lists in [style] offer an orientation. */
    fun gameListTurns(style: MenuStyle): Boolean
    fun netplayAvailable(): Boolean
    fun showNotice(text: String)
    fun refreshSettings()
    /** Rebuilds the Settings rows when one of [sections] is the open page. */
    fun refreshSettingsOn(vararg sections: SettingsSection)
    fun showSettingsOn(section: SettingsSection, kind: SettingsKind)
    fun openPicker(title: String, labels: List<String>, current: Int, apply: (Int) -> Unit)
}

/**
 * Settings' content: every page's rows ([settingsRows]), each value row's values and what a change does
 * ([settingValue]), a page's lines, banner and picture, and the pages with state of their own (Button layout's console,
 * About's GPU name, the GPU driver page, an import's results). [SettingsController] shows them as screens.
 */
internal class SettingsRows(
    private val scope: CoroutineScope,
    private val screen: MutableStateFlow<Screen>,
    private val error: MutableStateFlow<String?>,
    private val dualMenu: StateFlow<Boolean>,
    private val deps: AppDeps,
    private val kids: KidsController?,
    private val host: SettingsRowsHost,
) {
    /** The last finished import's summary, which the Import results page lists. */
    var importResult: ImportSummary? = null

    /** A controller is connected (the UI reports it): it hides the on-screen pad, so Appearance drops the pad's rows. */
    var controllerConnected = false

    fun settingsRows(section: SettingsSection): List<SettingsRow> = when (section) {
        SettingsSection.LIBRARY -> libraryRows()
        SettingsSection.EMULATORS -> buildList {
            add(SettingsRow(SettingsKind.DEFAULTS, "Default emulators"))
            add(SettingsRow(SettingsKind.EMULATORS, "Default settings"))
            deps.gpuDrivers?.let { store -> gpuDriverRow(store.unavailableReason(), deps.settings.gpuDriver, store::installed)?.let(::add) }
            add(SettingsRow(SettingsKind.SAVE_ON_QUIT, "Save on quit", onOff(deps.settings.saveOnQuit)))
            add(SettingsRow(SettingsKind.A_RESUMES, "A resumes", onOff(deps.settings.aResumes)))
        }
        SettingsSection.SECOND_SCREEN -> listOf(
            SettingsRow(SettingsKind.SECOND_SCREEN_ENABLE, "Enable", onOff(deps.settings.secondScreen)),
            SettingsRow(SettingsKind.BOTTOM_SCREEN, "Bottom screen", bottomScreenLabel(deps.settings.bottomScreen)),
        )
        // NX Redux's Settings → System save rows, so both devices name saves alike.
        SettingsSection.SAVES -> listOf(
            SettingsRow(SettingsKind.SAVE_FORMAT, "Save format", deps.settings.saveFormat.label),
            SettingsRow(SettingsKind.STATE_FORMAT, "Save state format", deps.settings.stateFormat.label),
            SettingsRow(SettingsKind.EXTRACTED_NAME, "Use extracted file name", onOff(deps.settings.useExtractedFileName)),
            // Saves live only inside the app; these copy them out and back in.
            SettingsRow(SettingsKind.EXPORT_SAVES, "Export saves"),
            SettingsRow(SettingsKind.IMPORT_SAVES, "Import saves"),
        )
        SettingsSection.IMPORT_SAVES -> listOf(
            SettingsRow(SettingsKind.IMPORT_FOLDER, "From a folder"),
            SettingsRow(SettingsKind.IMPORT_ZIP, "From a zip file"),
            SettingsRow(SettingsKind.IMPORT_RETROARCH, "From RetroArch"),
        )
        SettingsSection.IMPORT_BIOS -> listOf(
            SettingsRow(SettingsKind.IMPORT_BIOS_FOLDER, "From a folder"),
            SettingsRow(SettingsKind.IMPORT_BIOS_FILES, "From files"),
            SettingsRow(SettingsKind.IMPORT_BIOS_ZIP, "From a zip file"),
            SettingsRow(SettingsKind.IMPORT_BIOS_RETROARCH, "From RetroArch"),
        )
        // The summary, then each skipped file with its reason; read-only but for Done.
        SettingsSection.IMPORT_RESULT -> importResult?.let { r ->
            r.lines().map { SettingsRow(SettingsKind.INFO, it) } + r.skipped.map { SettingsRow(SettingsKind.INFO, it.name, it.reasonText()) }
        }.orEmpty() + SettingsRow(SettingsKind.IMPORT_DONE, "Done")
        SettingsSection.DEFAULT_EMULATORS -> defaultEmulatorRows()
        SettingsSection.BUTTON_LAYOUT -> buttonLayoutRows()
        SettingsSection.SHORTCUTS -> host.shortcutRows()
        SettingsSection.SYSTEM_BUTTONS -> host.systemButtonRows()
        SettingsSection.APPEARANCE -> listOf<SettingsRow?>(
            if (!controllerConnected) SettingsRow(SettingsKind.PAD_SKIN, "Pad skin", onOff(deps.settings.padSkin)) else null,
            // the skin's look: none to pick while the skin is Off (the full-screen overlay pad)
            if (!controllerConnected && deps.settings.padSkin) SettingsRow(SettingsKind.PAD_THEME, "Pad theme", padThemeLabel(deps.settings.padTheme)) else null,
            SettingsRow(SettingsKind.ACCENT, "Accent", deps.settings.accent.label),
            SettingsRow(SettingsKind.SECONDARY_ACCENT, "Secondary accent", deps.settings.secondaryAccent.label),
            // The options lists' text (Settings, Game Settings, the in-game menu): Large or Small.
            SettingsRow(SettingsKind.TEXT_SIZE, "Text size", deps.settings.optionTextSize.label),
            // the skin's buttons moved and resized per console (the bare overlay's too), its own page
            if (!controllerConnected) SettingsRow(SettingsKind.BUTTON_LAYOUT, "Button layout", if (buttonLayoutCustom()) "Custom" else "Default") else null,
            SettingsRow(SettingsKind.RESET_DEFAULTS, RESET_DEFAULTS_LABEL, note = appearanceResetScope(padShown = !controllerConnected)),
        ).filterNotNull()
        SettingsSection.MAIN_MENU -> layoutsRows()
        SettingsSection.LAYOUT_HOME, SettingsSection.LAYOUT_GAMES_TAB, SettingsSection.LAYOUT_CONSOLES, SettingsSection.LAYOUT_COLLECTIONS, SettingsSection.LAYOUT_TOOLS ->
            layoutTabRows(section.tab!!)
        SettingsSection.LAYOUT_GAME_LISTS -> layoutGameListRows()
        SettingsSection.LAYOUT_TITLE_HINTS -> layoutTitleHintRows()
        SettingsSection.LAYOUT_SWITCHER -> listOf(SettingsRow(SettingsKind.SWITCHER_LAYOUT, "Layout", deps.settings.switcherLayout.label))
        // The on-screen pad's press tick, then a core's rumble.
        SettingsSection.CONTROLS -> listOf(
            SettingsRow(SettingsKind.PAD_VIBRATION, "Pad vibration", deps.settings.padVibration.label),
            SettingsRow(SettingsKind.GAME_RUMBLE, "Game rumble", deps.settings.gameRumble.label),
            // Fast forward, rewind and reset buttons, its own page (Shortcuts).
            SettingsRow(SettingsKind.SHORTCUTS, "Shortcuts"),
            // Which of the pad's buttons are SELECT, START and MENU, its own page (System buttons).
            SettingsRow(SettingsKind.SYSTEM_BUTTONS, "System buttons"),
            SettingsRow(SettingsKind.RESET_DEFAULTS, RESET_DEFAULTS_LABEL),
        )
        SettingsSection.SYSTEM -> systemRows()
        SettingsSection.GPU_DRIVER -> deps.gpuDrivers?.let { gpuDriverPageRows(deps.settings.gpuDriver, it.installed()) }.orEmpty()
        SettingsSection.KIDS -> host.kidsRows()
    }

    /**
     * The Cable Link rows on mGBA's Emulator Settings page ([EmulatorRows.hostsCableLink]): the input delay, for trying a
     * game at a fixed one, its overlay, and the received-ROM cache. Only where Cable Link exists (the platform with a link
     * ROM cache).
     */
    fun cableLinkRows(): List<SettingsRow> = deps.linkRomCache?.let { cache ->
        listOf(
            SettingsRow(SettingsKind.CABLE_LINK_DELAY, "Input delay", cableLinkDelayLabel(deps.settings.cableLinkDelayPin)),
            SettingsRow(SettingsKind.CABLE_LINK_DELAY_OVERLAY, "Delay overlay", onOff(deps.settings.cableLinkDelayOverlay)),
            SettingsRow(SettingsKind.CLEAR_LINK_ROMS, "Clear link ROM cache", formatBytes(cache.sizeBytes())),
        )
    }.orEmpty()

    /**
     * Settings -> System: its options as one group under the page's title (Player name, Pause when unfocused, Screen care, Use as home screen with
     * a launcher, Keyboard, Debug log), then the About and Licenses groups.
     */
    private fun systemRows(): List<SettingsRow> = buildList {
        add(SettingsRow(SettingsKind.PLAYER_NAME, "Player name", PlayerName.resolve(deps.settings.playerName, deps.deviceName())))
        add(SettingsRow(SettingsKind.PAUSE_UNFOCUSED, "Pause when unfocused", onOff(deps.settings.pauseWhenUnfocused)))
        add(SettingsRow(SettingsKind.SCREEN_CARE, "Screen care", onOff(deps.settings.screenCare)))
        deps.launcher?.let { l -> add(SettingsRow(SettingsKind.HOME_SCREEN, "Use as home screen", onOff(l.isHome()))) }
        if (deps.ownKeyboard) add(SettingsRow(SettingsKind.KEYBOARD, "Keyboard", keyboardLabel(deps.settings.androidKeyboard)))
        // Logging for a bug report; off, only errors reach logcat.
        add(SettingsRow(SettingsKind.DEBUG_LOG, "Debug log", onOff(deps.settings.debugLog)))
        add(SettingsRow(SettingsKind.GAP, ""))
        add(SettingsRow(SettingsKind.SECTION, "About"))
        queryGpu()   // the page (or its preview) shows: the GPU row reads "…" until this lands
        aboutRows(deps.appInfo, _gpu.value).forEach { (label, value) -> add(SettingsRow(SettingsKind.INFO, label, value)) }
        add(SettingsRow(SettingsKind.COPY_INFO, "Copy device info"))
        host.updateLabel()?.let { add(SettingsRow(SettingsKind.APP_UPDATE, it)) }
        add(SettingsRow(SettingsKind.GAP, ""))
        add(SettingsRow(SettingsKind.SECTION, "Licenses"))
        ABOUT_LICENSES.forEach { add(SettingsRow(SettingsKind.TEXT, it)) }
        add(SettingsRow(SettingsKind.LICENSES_LINK, "Full list of licenses", LICENSES_URL))
    }

    /** About's GPU name once its query answered ([UNKNOWN_INFO] on a failure); null before. */
    private val _gpu = MutableStateFlow<String?>(null)
    val gpu: StateFlow<String?> = _gpu.asStateFlow()   // the two-pane preview of About redraws when it lands
    private var gpuJob: Job? = null

    /**
     * Asks [AppDeps.deviceGpu] once; the About page then re-reads its rows. Started lazily so a query that answers at
     * once (no suspension) finds [gpuJob] already set and the rows it rebuilds do not ask again.
     */
    private fun queryGpu(): Job = gpuJob ?: scope.launch(start = CoroutineStart.LAZY) {
        val name = tryOrNull { deps.deviceGpu() }
        _gpu.value = name?.trim()?.ifEmpty { null } ?: UNKNOWN_INFO
        host.refreshSettingsOn(SettingsSection.SYSTEM)
    }.also { gpuJob = it; it.start() }

    /**
     * The Library page, one list with no groups: the counts with Rescan, the home (its path; "Not available" while recorded
     * offline, [SettingsRowsHost.offlineHome]), each extra ROM folder (its path, or its state while the in-memory index lists it
     * offline) and Add ROM folder, All files access where the permission is declared (a sideloaded Android 11+ build) as a
     * switch opening Android's page, Saves, Android games, and Unassigned and Hidden while they hold a game. From memory
     * only, no SAF call. Extras reflect the last scan: one whose card was pulled since reads as fine until a rescan. "Not
     * ready yet" (mounted but not listing, as the scan's notice says) only comes from a scan in this AppState
     * ([SettingsRowsHost.lateMount]); a lateMount read from the stored index is from an earlier run, so it reads "Not available".
     */
    private fun libraryRows(): List<SettingsRow> = buildList {
        val ix = host.indexSnapshot()
        // At the top: the games and consoles (a merged console counts once, as on the main menu), the last good scan
        // under them, and the Rescan button on the right.
        val consoles = host.systems().filter { !it.unassigned }.map { it.displayName }.distinct().size
        val counts = "${gamesLabel(host.systems().sumOf { it.gameCount })} · ${if (consoles == 1) "1 console" else "$consoles consoles"}"
        add(SettingsRow(SettingsKind.RESCAN, counts, ix?.scannedAt?.let { "Last scan: ${formatDateTime(it)}" }))
        // One list, no groups: the home, the ROM folders added beside it, Add ROM folder, All files access, then Saves and
        // the game lists.
        val root = host.home()
        val homeOffline = root != null && host.offlineHome() == root
        val homeText = when {
            root == null -> "No home folder"
            homeOffline -> "Not available"
            else -> Sources.displayPath(root) ?: deps.source.displayName(root)
        }
        add(SettingsRow(SettingsKind.HOME, "Home folder", homeText, warning = homeOffline))
        deps.settings.extraFolders.forEach {
            val state = when {
                ix == null || it.id !in ix.offline -> null
                host.fileAccessOff(it.ref) -> "All files access off"
                it.id in ix.lateMount && it.id in host.lateMount() -> "Not ready yet"
                else -> "Not available"
            }
            add(SettingsRow(SettingsKind.EXTRA, "ROM Folder: ${deps.source.displayName(it.ref)}", state ?: Sources.displayPath(it.ref) ?: "ROM folder", extra = it, warning = state != null))
        }
        add(SettingsRow(SettingsKind.ADD_EXTRA, "Add ROM folder"))
        deps.allFilesAccess?.takeIf { it.declared }?.let { add(allFilesRow(it.granted(), root)) }
        deps.micAccess?.takeIf { it.usable }?.let { add(SettingsRow(SettingsKind.MIC_ACCESS, "Microphone access", onOff(it.granted()))) }
        add(SettingsRow(SettingsKind.SAVES, "Saves"))
        if (deps.biosIntake != null) add(SettingsRow(SettingsKind.IMPORT_BIOS, "Import BIOS"))
        if (deps.launcher != null) add(SettingsRow(SettingsKind.ANDROID_GAMES, "Android games", host.androidGames()?.let { gamesCountLabel(it.size) } ?: "…"))
        host.unassignedSystem()?.let { add(SettingsRow(SettingsKind.UNASSIGNED, "Unassigned games", gamesLabel(it.gameCount))) }
        if (host.hiddenPaths().isNotEmpty()) add(SettingsRow(SettingsKind.HIDDEN, "Hidden games", gamesLabel(host.hiddenPaths().size)))
    }

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
                "Off: your home folder can't be read, so ${host.mediaPath(root)} is used"
            deps.settings.extraFolders.any { host.fileAccessOff(it.ref) } -> "Off: some ROM folders can't be read"
            else -> null
        }
        return SettingsRow(SettingsKind.ALL_FILES, "All files access", onOff(granted), warning = note != null, note = note)
    }

    /**
     * The lines above a page's rows. Default emulators: what the choice is for (Library's counts are its first row). From
     * memory only: no SAF call.
     */
    fun settingsDetails(section: SettingsSection): List<String> = when (section) {
        SettingsSection.DEFAULT_EMULATORS -> listOf("A game's own Emulator choice still wins. Saves and settings follow the tag.")
        SettingsSection.IMPORT_RESULT -> importResult?.statesNote().orEmpty()   // wraps, unlike the one-line rows
        else -> emptyList()
    }

    /** Emulators -> Default emulators, built from [SettingsRowsHost.systems] (no SAF call): [defaultEmulatorRows]. */
    private fun defaultEmulatorRows(): List<SettingsRow> = defaultEmulatorRows(host.systems(), deps.settings.defaultTags)

    /**
     * Settings' value row [row] as a [SettingValue]: every setting LEFT / RIGHT (NX Redux ITEM_CYCLE), A and the touch
     * chooser step, in one table, so each is written once. A change republishes the page (App re-reads the theme,
     * accent, scale and pad with it); an arm with more to do says so in its `after`. Null for a row that is not a value
     * row, or has nothing to step (a console of one tag, a row without its tab, shelf or hint). Two-value rows flip on
     * every press. Onboarding's value rows step the same entries through a bare row of their kind.
     */
    fun settingValue(row: SettingsRow): SettingValue<*>? {
        val s = deps.settings
        val republish: (Any?) -> Unit = { host.refreshSettings() }
        fun <T> cycle(values: List<T>, current: T, label: ((T) -> String)? = null, after: (T) -> Unit = republish, set: (T) -> Unit) =
            SettingValue(values, current, set, label, after = after)
        // Off / On, or Hide / Show for a row that shows or hides something (Layouts'): the touch row's switch reads either.
        fun flip(on: Boolean, show: Boolean = false, after: (Boolean) -> Unit = republish, set: (Boolean) -> Unit) =
            cycle(listOf(false, true), on, { if (show) showHide(it) else onOff(it) }, after = after, set = set)
        return when (row.kind) {
            // Appearance: the pad theme, the accent presets, Auto and the presets, Large / Small.
            SettingsKind.PAD_SKIN -> flip(s.padSkin) { s.padSkin = it }
            SettingsKind.PAD_THEME -> cycle(PadTheme.entries, s.padTheme, ::padThemeLabel) { s.padTheme = it }
            SettingsKind.ACCENT -> cycle(AccentPreset.entries, s.accent, { it.label }) { s.accent = it }
            SettingsKind.SECONDARY_ACCENT -> cycle(SecondaryAccent.entries, s.secondaryAccent, { it.label }) { s.secondaryAccent = it }
            SettingsKind.TEXT_SIZE -> cycle(OptionTextSize.entries, s.optionTextSize, { it.label }) { s.optionTextSize = it }
            // Layouts. A main menu tab cycles List, Grid, Carousel and Minimal ([MenuStyles.MAIN_MENU_STYLES]; Backdrop is a
            // game-list style only), Home rebuilt in its new one.
            SettingsKind.MENU_STYLE -> row.category?.let { c ->
                cycle(MenuStyles.MAIN_MENU_STYLES, MenuStyles.styleOf(s.menuStyles, c), ::menuStyleLabel, after = {
                    if (c == MenuCategory.HOME) host.rebuildHomeTab()
                    host.refreshSettings()
                }) { s.menuStyles = s.menuStyles + (c to it) }
            }
            // Two screens: from the style drawn, never through Backdrop; the saved one changes only when stepped.
            SettingsKind.GAME_LIST_STYLE -> cycle(MenuStyles.GAME_LIST_STYLES, host.gameListLayout().first, ::menuStyleLabel) { s.gameListStyle = it }
            SettingsKind.MENU_ORIENTATION -> row.category?.let { c ->
                cycle(MenuOrientation.entries, MenuStyles.orientationOf(s.menuOrientations, c), ::orientationLabel) { s.menuOrientations = s.menuOrientations + (c to it) }
            }
            SettingsKind.GAME_LIST_ORIENTATION -> if (dualMenu.value) null else cycle(MenuOrientation.entries, s.gameListOrientation, ::orientationLabel) { s.gameListOrientation = it }
            SettingsKind.GAME_LIST_VERTICAL_ALIGNMENT -> cycle(VerticalListAlignment.entries, s.gameListVerticalAlignment, ::verticalAlignmentLabel) { s.gameListVerticalAlignment = it }
            SettingsKind.CONTROLLER_ART -> cycle(ConsoleArt.entries, s.consoleArt, { it.label }) { s.consoleArt = it }   // Controller, Background, None
            SettingsKind.LIST_ART -> cycle(GameListArt.entries, s.gameListArt, { it.label }) { s.gameListArt = it }
            SettingsKind.BACKDROP_ART -> cycle(BackdropArt.entries, s.backdropArt, { it.label }) { s.backdropArt = it }
            SettingsKind.SWITCHER_LAYOUT -> cycle(SwitcherLayout.entries, s.switcherLayout, { it.label }) { s.switcherLayout = it }   // Full screen / List
            SettingsKind.TAB_SHOWN -> row.category?.let { c ->
                flip(tabSetting(c), show = true) { on ->
                    when (c) {
                        MenuCategory.HOME -> s.showHomeTab = on
                        MenuCategory.GAMES -> s.showGamesTab = on
                        MenuCategory.CONSOLES -> s.showConsolesTab = on
                        MenuCategory.COLLECTIONS -> s.showCollectionsTab = on
                        MenuCategory.TOOLS -> s.showToolsTab = on
                        MenuCategory.APPS -> Unit   // Kids Mode's: no Layouts row
                    }
                }
            }
            // New shelf / Not started shelf: Home is rebuilt without (or with) it. Pick up again has no setting.
            SettingsKind.SHELF_SHOWN -> row.shelf?.let { shelf ->
                flip(shelfShown(shelf), show = true, after = { host.rebuildHomeTab(); host.refreshSettings() }) { on ->
                    when (shelf) {
                        HomeShelf.NEW -> s.showNewShelf = on
                        HomeShelf.NOT_STARTED -> s.showNotStartedShelf = on
                        HomeShelf.MOST_PLAYED -> s.showMostPlayedShelf = on
                        HomeShelf.PICK_UP -> Unit
                    }
                }
            }
            // Netplay hint: the Show / Hide of Y's hint (Recent and Search are always icons).
            SettingsKind.HINT_SHOWN -> if (row.hint == NxButton.Y) flip(s.showNetplayHint, show = true) { s.showNetplayHint = it } else null
            SettingsKind.PAGE_TITLE -> flip(s.menuPageTitle, show = true, after = { host.refreshSettings() }) { s.menuPageTitle = it }
            SettingsKind.BUTTON_HINTS -> flip(s.menuButtonHints, show = true) { s.menuButtonHints = it }
            SettingsKind.EXTRA_INFO -> flip(s.menuExtraInfo, show = true) { s.menuExtraInfo = it }
            SettingsKind.GAME_INFO -> cycle(listOf(false, true), s.menuInfoAchievements, ::gameInfoLabel) { s.menuInfoAchievements = it }
            SettingsKind.HOME_STATS -> flip(s.menuHomeStats, show = true) { s.menuHomeStats = it }   // Home's board follows through its frame
            SettingsKind.LAYOUT_OPTIONS -> flip(s.gameListLayoutOptions, show = true) { s.gameListLayoutOptions = it }   // a game list's icons
            SettingsKind.ACHIEVEMENTS_PANEL -> flip(s.gameListAchievementsPanel, show = true) { s.gameListAchievementsPanel = it }   // X's hint
            // Controls: Off, Light, Medium and Strong; Game rumble previews the new level. The menu pad: the shell follows.
            SettingsKind.PAD_VIBRATION -> cycle(PadVibration.entries, s.padVibration, { it.label }) { s.padVibration = it }
            SettingsKind.GAME_RUMBLE -> cycle(GameRumble.entries, s.gameRumble, { it.label }, after = { host.refreshSettings(); deps.rumblePreview(it) }) { s.gameRumble = it }
            // A touch button: None, then every action a touch button can do ([ShortcutAction.touch]).
            SettingsKind.SHORTCUT_TOUCH -> row.touchSlot?.let { slot ->
                cycle(listOf<ShortcutAction?>(null) + ShortcutAction.entries.filter { it.touch }, host.shortcutSet().touch[slot], { it?.label ?: "None" }) { host.setTouch(slot, it) }
            }
            SettingsKind.BUTTON_LAYOUT_CONSOLE -> cycle(buttonLayoutConsoles, buttonLayoutConsole(), { it.displayName }) { buttonLayoutConsole = it }
            SettingsKind.BUTTON_LAYOUT_WII_PAD -> cycle(wiiPads, buttonLayoutWiiPad, ::wiiPadLabel) { buttonLayoutWiiPad = it }
            SettingsKind.BUTTON_LAYOUT_N3DS_PAD -> cycle(listOf(false, true), buttonLayoutN3dsNew, ::n3dsPadLabel) { buttonLayoutN3dsNew = it }
            SettingsKind.BUTTON_LAYOUT_SEGA_PAD -> cycle(listOf(false, true), buttonLayoutSegaThree, ::segaPadLabel) { buttonLayoutSegaThree = it }
            // Emulators, Saves (the handheld's formats in its order), Second screen and Developer.
            SettingsKind.PAUSE_UNFOCUSED -> flip(s.pauseWhenUnfocused) { s.pauseWhenUnfocused = it }
            SettingsKind.SCREEN_CARE -> flip(s.screenCare) { s.screenCare = it }
            // Home's tiles hold the hidden state's frames read when built: rebuilt so none outlives the switch.
            SettingsKind.SAVE_ON_QUIT -> flip(s.saveOnQuit, after = { host.refreshSettings(); scope.launch { host.refreshMainRows() } }) { s.saveOnQuit = it }
            SettingsKind.A_RESUMES -> flip(s.aResumes) { s.aResumes = it }
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
            SettingsKind.CABLE_LINK_DELAY -> cycle((0..NetplayCores.CABLE_LINK_DELAY_MAX).toList(), s.cableLinkDelayPin.coerceIn(0, NetplayCores.CABLE_LINK_DELAY_MAX), ::cableLinkDelayLabel) { s.cableLinkDelayPin = it }
            SettingsKind.CABLE_LINK_DELAY_OVERLAY -> flip(s.cableLinkDelayOverlay) { s.cableLinkDelayOverlay = it }
            SettingsKind.KEYBOARD -> cycle(listOf(false, true), s.androidKeyboard, ::keyboardLabel) { s.androidKeyboard = it }
            // Kids Mode On / Off: the first switch-on runs setup (another screen), so the page is republished only if still up.
            SettingsKind.KIDS_ENABLE -> kids?.let {
                flip(s.kidsEnabled, after = { host.refreshSettingsOn(SettingsSection.KIDS) }) { on -> host.kidsSetEnabled(on) }
            }
            else -> null
        }
    }

    /** A page's note under its title ([Screen.Settings.banner]): Saves', where they live. */
    fun settingsBanner(section: SettingsSection): String? {
        if (section == SettingsSection.LAYOUT_GAMES_TAB) return "Its layout follows Layouts \u2192 Game lists."
        if (section == SettingsSection.GPU_DRIVER) return GPU_DRIVER_INFO
        if (section != SettingsSection.SAVES) return null
        val home = host.home()
        val media = deps.homes?.media
        return when {
            !host.dataMoved() -> SAVES_STILL_INSIDE
            home != null && home == media -> "Saves are in ${host.mediaPath(media)}. Uninstalling the app removes them."
            home != null && deps.homes != null && FileRefs.isFile(home) -> "Saves are in your home folder."
            else -> "Saves are stored inside the app. Export them before uninstalling or clearing app data."   // a picker home: still private
        }
    }

    /** The Button layout page's consoles: every console, by name. */
    private val buttonLayoutConsoles: List<Console> by lazy { CoreRegistry.consoles.sortedBy { it.displayName.lowercase() } }

    /** The Button layout page's console (the last one picked, while the app runs) and, on the Wii and the 3DS, its controller. */
    private var buttonLayoutConsole: Console? = null
    private var buttonLayoutWiiPad: WiiPad = WiiPad.CLASSIC
    /** The 3DS's New 3DS controls (most games resolve to the regular ones under Auto, so Regular first). */
    private var buttonLayoutN3dsNew: Boolean = false
    /** The Sega pad consoles' 3-button pad (else the 6-button one, which the skin shows when the pad is unknown). */
    private var buttonLayoutSegaThree: Boolean = false

    private fun buttonLayoutConsole(): Console = buttonLayoutConsole ?: buttonLayoutConsoles.first()

    /** The Wii controllers in the page's order: the Wii's default pad first. */
    private val wiiPads = listOf(WiiPad.CLASSIC, WiiPad.NUNCHUK, WiiPad.SIDEWAYS, WiiPad.UPRIGHT)

    private fun wiiPadLabel(pad: WiiPad): String = when (pad) {
        WiiPad.CLASSIC -> "Classic"; WiiPad.NUNCHUK -> "Nunchuk"; WiiPad.SIDEWAYS -> "Sideways"; WiiPad.UPRIGHT -> "Upright"
    }

    private fun n3dsPadLabel(new: Boolean): String = if (new) "New" else "Regular"

    private fun segaPadLabel(three: Boolean): String = if (three) "3-button" else "6-button"

    /** Any console has a tweak stored, skin or overlay: Appearance's Button layout row reads Custom. */
    private fun buttonLayoutCustom(): Boolean = listOf(deps.settings.skinTweaks, deps.settings.overlayTweaks)
        .any { stored -> SkinTweaks.decode(stored).values.any { c -> c.values.any { it.isNotEmpty() } } }

    private fun buttonLayoutRows(): List<SettingsRow> {
        val console = buttonLayoutConsole()
        return listOfNotNull(
            SettingsRow(SettingsKind.BUTTON_LAYOUT_CONSOLE, "Console", console.displayName),
            if (console.id == WII_CONSOLE) SettingsRow(SettingsKind.BUTTON_LAYOUT_WII_PAD, "Controller", wiiPadLabel(buttonLayoutWiiPad)) else null,
            if (console.id == N3DS_CONSOLE) SettingsRow(SettingsKind.BUTTON_LAYOUT_N3DS_PAD, "Controller", n3dsPadLabel(buttonLayoutN3dsNew)) else null,
            if (console.id in SEGA_PAD_CONSOLES) SettingsRow(SettingsKind.BUTTON_LAYOUT_SEGA_PAD, "Controller", segaPadLabel(buttonLayoutSegaThree)) else null,
            *buttonLayoutOrientations(deps.screenRotates(), deps.windowPx()).map { landscape ->
                SettingsRow(SettingsKind.BUTTON_LAYOUT_ORIENTATION, if (landscape) "Landscape" else "Portrait", landscape = landscape)
            }.toTypedArray(),
            SettingsRow(SettingsKind.BUTTON_LAYOUT_RESET, "Reset console"),
        )
    }

    /** An editor is opening or open: a second quick A / tap does not stack another over it (its Save would win). */
    private var skinEditorOpen = false

    fun openSkinEditor(landscape: Boolean) {
        val editor = deps.skinEditor ?: return
        if (skinEditorOpen) return
        skinEditorOpen = true
        val console = buttonLayoutConsole()
        val pad = buttonLayoutWiiPad.takeIf { console.id == WII_CONSOLE }
        val n3ds = console.id == N3DS_CONSOLE
        val new = n3ds && buttonLayoutN3dsNew
        val sega = console.id in SEGA_PAD_CONSOLES
        val three = sega && buttonLayoutSegaThree
        val controller = pad?.let(::wiiPadLabel) ?: n3dsPadLabel(new).takeIf { n3ds } ?: segaPadLabel(three).takeIf { sega }
        editor.open(SkinEditRequest(console.id, landscape, pad, new, three), controller?.let { "${console.displayName} · $it" } ?: console.displayName)
    }

    /** The editor saved or left: republish the page if Settings is still showing (the activity wrote the settings). */
    fun onSkinLayoutEdited() {
        skinEditorOpen = false
        if (screen.value is Screen.Settings) host.refreshSettings()
    }

    /** Reset console: the page's console's tweaks go, every layout of it, skin and overlay; other consoles keep theirs. */
    fun resetButtonLayoutConsole() {
        val id = buttonLayoutConsole().id
        deps.settings.skinTweaks = SkinTweaks.encode(SkinTweaks.decode(deps.settings.skinTweaks) - id)
        deps.settings.overlayTweaks = SkinTweaks.encode(SkinTweaks.decode(deps.settings.overlayTweaks) - id)
        host.showSettingsOn(SettingsSection.BUTTON_LAYOUT, SettingsKind.BUTTON_LAYOUT_RESET)
    }

    /** Copy device info: the About rows as text on the clipboard (after the GPU query, which is quick), then a notice. */
    fun copyDeviceInfo() {
        scope.launch {
            queryGpu().join()
            deps.copyText(aboutCopyText(deps.appInfo, _gpu.value))
            host.showNotice("Device info copied")
        }
    }

    private fun cableLinkDelayLabel(frames: Int): String = when (frames) {
        0 -> "Auto"
        1 -> "1 frame"
        else -> "$frames frames"
    }

    private fun bottomScreenLabel(b: BottomScreen): String = when (b) {
        BottomScreen.AUTO -> "Auto"
        BottomScreen.MAIN -> "Main screen"
        BottomScreen.SECOND -> "Second screen"
    }

    fun padThemeLabel(theme: PadTheme): String = when (theme) {
        PadTheme.NX_CARBON -> "NX Carbon"
        PadTheme.NX_ONYX -> "NX Onyx"
        PadTheme.NX_PLASMA -> "NX Plasma"
        PadTheme.NX_EMBER -> "NX Ember"
        PadTheme.NX_VOLT -> "NX Volt"
        PadTheme.NX_TIDE -> "NX Tide"
        PadTheme.NX_OBSIDIAN -> "NX Obsidian"
    }

    /**
     * Layouts: a row per group (its page: a tab's, the game lists', the page title's and hints', the Game Switcher's), each
     * with what it is now (a shown tab its layout, a hidden one Hidden), then Extra info and Reset to defaults.
     */
    private fun layoutsRows(): List<SettingsRow> {
        val s = deps.settings
        val tabs = MenuCategory.entries.filter { it != MenuCategory.APPS }.map { c ->   // Kids Mode's Apps: no Layouts group
            val group = SettingsSection.entries.first { it.tab == c }
            SettingsRow(group.opener!!, group.label, if (!tabSetting(c)) "Hidden"
                else menuStyleLabel(if (c == MenuCategory.GAMES) host.gameListLayout().first else MenuStyles.styleOf(s.menuStyles, c)))   // Games follows the game lists
        }
        val chrome = when {
            s.menuPageTitle && s.menuButtonHints -> "Show"
            s.menuPageTitle -> "Title only"
            s.menuButtonHints -> "Hints only"
            else -> "Hide"
        }
        return tabs + listOf(
            SettingsRow(SettingsKind.LAYOUT_GAME_LISTS, SettingsSection.LAYOUT_GAME_LISTS.label, menuStyleLabel(host.gameListLayout().first)),
            SettingsRow(SettingsKind.LAYOUT_TITLE_HINTS, SettingsSection.LAYOUT_TITLE_HINTS.label, chrome),
            SettingsRow(SettingsKind.LAYOUT_SWITCHER, SettingsSection.LAYOUT_SWITCHER.label, s.switcherLayout.label),
            SettingsRow(SettingsKind.EXTRA_INFO, "Extra info", showHide(s.menuExtraInfo)),
            SettingsRow(SettingsKind.GAME_INFO, "Game info", gameInfoLabel(s.menuInfoAchievements)),
            SettingsRow(SettingsKind.RESET_DEFAULTS, RESET_DEFAULTS_LABEL),
        )
    }

    /**
     * A tab's group: its Show / Hide (Visible), and while shown its layout and the options that layout has. On a group's
     * page the rows don't repeat its name (the page title says it): Visible, Layout, Orientation, Background, Stats. Home's layout row comes
     * last, right above the picture it changes (after its shelves and stats); the other tabs' follows the Show / Hide.
     */
    private fun layoutTabRows(c: MenuCategory): List<SettingsRow> {
        val s = deps.settings
        val on = tabSetting(c)
        // The Games tab's layout is the game lists' (Layouts -> Game lists): its page has the one row.
        if (c == MenuCategory.GAMES) return listOf(SettingsRow(SettingsKind.TAB_SHOWN, "Visible", showHide(on), category = c))
        val style = MenuStyles.styleOf(s.menuStyles, c)
        val home = c == MenuCategory.HOME
        val layout = SettingsRow(SettingsKind.MENU_STYLE, "Layout", menuStyleLabel(style), category = c).takeIf { on }
        return listOfNotNull(
            SettingsRow(SettingsKind.TAB_SHOWN, "Visible", showHide(on), category = c),
            layout.takeIf { !home },
            // Home's New and Not started shelves, Show / Hide, while the Home tab shows.
            SettingsRow(SettingsKind.SHELF_SHOWN, "New shelf", showHide(s.showNewShelf), shelf = HomeShelf.NEW).takeIf { on && c == MenuCategory.HOME },
            SettingsRow(SettingsKind.SHELF_SHOWN, "Not started shelf", showHide(s.showNotStartedShelf), shelf = HomeShelf.NOT_STARTED).takeIf { on && c == MenuCategory.HOME },
            SettingsRow(SettingsKind.SHELF_SHOWN, "Most played shelf", showHide(s.showMostPlayedShelf), shelf = HomeShelf.MOST_PLAYED).takeIf { on && c == MenuCategory.HOME },
            // Home's stats strip (its cards' info follows Extra info).
            SettingsRow(SettingsKind.HOME_STATS, "Stats", showHide(s.menuHomeStats)).takeIf { on && c == MenuCategory.HOME },
            SettingsRow(SettingsKind.MENU_ORIENTATION, "Orientation", orientationLabel(MenuStyles.orientationOf(s.menuOrientations, c)), category = c)
                .takeIf { on && c != MenuCategory.HOME && style == MenuStyle.CAROUSEL },
            SettingsRow(SettingsKind.CONTROLLER_ART, "Background", s.consoleArt.label, category = c)
                .takeIf { on && c == MenuCategory.CONSOLES && style != MenuStyle.GRID && style != MenuStyle.MINIMAL },   // List and Carousel; the Grid has no controllers, Minimal no art
            layout.takeIf { home },
        )
    }

    /**
     * The game lists' group: the options their layout has, the Layout icon and the achievements panel, a vertical list's
     * alignment, then the layout and its orientation last, right above the picture they change.
     */
    private fun layoutGameListRows(): List<SettingsRow> {
        val s = deps.settings
        val (list, orientation) = host.gameListLayout()
        return listOfNotNull(
            SettingsRow(SettingsKind.LIST_ART, "List art", s.gameListArt.label).takeIf { list == MenuStyle.LIST },
            // Every layout: it also picks the game panel's header and the Game Switcher's box.
            SettingsRow(SettingsKind.BACKDROP_ART, "Backdrop art", s.backdropArt.label),
            // A game list's Layout icon (hidden: Layouts only), and its trophy icon and X hint (hidden: X and the game's menu).
            SettingsRow(SettingsKind.LAYOUT_OPTIONS, "Layout options", showHide(s.gameListLayoutOptions)),
            SettingsRow(SettingsKind.ACHIEVEMENTS_PANEL, "Achievements panel", showHide(s.gameListAchievementsPanel)).takeIf { deps.achievements != null },
            // A vertical list's alignment: with the layout rows, just above them.
            SettingsRow(SettingsKind.GAME_LIST_VERTICAL_ALIGNMENT, "Alignment", verticalAlignmentLabel(s.gameListVerticalAlignment))
                .takeIf { MenuStyles.effectiveOrientation(list, orientation) == MenuOrientation.VERTICAL },
            SettingsRow(SettingsKind.GAME_LIST_STYLE, "Layout", menuStyleLabel(list)),
            SettingsRow(SettingsKind.GAME_LIST_ORIENTATION, "Orientation", orientationLabel(orientation)).takeIf { host.gameListTurns(list) },
        )
    }

    /** The page title's and hints' group: the two, and each hint on its own while the bar shows. */
    private fun layoutTitleHintRows(): List<SettingsRow> {
        val s = deps.settings
        return listOfNotNull(
            SettingsRow(SettingsKind.PAGE_TITLE, "Page title", showHide(s.menuPageTitle)),
            SettingsRow(SettingsKind.BUTTON_HINTS, "Button hints", showHide(s.menuButtonHints)),
            // Each hint on its own, while the bar shows (hidden: Netplay in the context menu, Recent and Search as icons).
            SettingsRow(SettingsKind.HINT_SHOWN, "Netplay hint", showHide(s.showNetplayHint), hint = NxButton.Y).takeIf { s.menuButtonHints && host.netplayAvailable() },
        )
    }

    /** The game lists' picture: their layout, stood on end (a Carousel or Backdrop) on the left or the right. */
    fun gameListsShot(): LayoutShot.Games {
        val (style, orientation) = host.gameListLayout()
        val vertical = MenuStyles.effectiveOrientation(style, orientation) == MenuOrientation.VERTICAL && MenuStyles.hasOrientation(style)
        return LayoutShot.Games(style, vertical, right = vertical && deps.settings.gameListVerticalAlignment == VerticalListAlignment.RIGHT)
    }

    /** A tab picture's Consoles background: the setting's on Consoles, the default on the other tabs (they have none). */
    private fun consoleShotArt(c: MenuCategory): ConsoleArt = if (c == MenuCategory.CONSOLES) deps.settings.consoleArt else ConsoleArt.CONTROLLER

    /** A Layouts group's picture of its values: a shown tab's layout, the game lists', the title and hints, the Game Switcher's; else none. */
    fun layoutShot(section: SettingsSection): LayoutShot? {
        val s = deps.settings
        section.tab?.let { c ->
            if (!tabSetting(c)) return null
            if (c == MenuCategory.GAMES) return gameListsShot()   // follows Layouts -> Game lists, never menuStyles
            return tabShot(c)
        }
        return when (section) {
            SettingsSection.LAYOUT_GAME_LISTS -> gameListsShot()
            SettingsSection.LAYOUT_TITLE_HINTS -> LayoutShot.Chrome(s.menuPageTitle, s.menuButtonHints)
            SettingsSection.LAYOUT_SWITCHER -> LayoutShot.Switcher(s.switcherLayout)
            else -> null
        }
    }

    /** [c]'s tab as its layout rows set it. */
    fun tabShot(c: MenuCategory): LayoutShot.Tab {
        val s = deps.settings
        val style = MenuStyles.styleOf(s.menuStyles, c)
        val vertical = style == MenuStyle.CAROUSEL && c != MenuCategory.HOME && MenuStyles.orientationOf(s.menuOrientations, c) == MenuOrientation.VERTICAL
        return LayoutShot.Tab(c, style, stats = c != MenuCategory.HOME || s.menuHomeStats, vertical = vertical, art = consoleShotArt(c))
    }

    /** Layouts → [c] tab as stored: Show (true) or Hide. */
    fun tabSetting(c: MenuCategory): Boolean = when (c) {
        MenuCategory.HOME -> deps.settings.showHomeTab
        MenuCategory.GAMES -> deps.settings.showGamesTab
        MenuCategory.CONSOLES -> deps.settings.showConsolesTab
        MenuCategory.COLLECTIONS -> deps.settings.showCollectionsTab
        MenuCategory.TOOLS -> deps.settings.showToolsTab
        MenuCategory.APPS -> true   // shown whenever Kids Mode puts it in ([KidsScreensController.withKidsApps])
    }

    /** Whether Layouts shows [shelf] on Home: New and Not started by their settings; Pick up again always. */
    fun shelfShown(shelf: HomeShelf): Boolean = when (shelf) {
        HomeShelf.NEW -> deps.settings.showNewShelf
        HomeShelf.NOT_STARTED -> deps.settings.showNotStartedShelf
        HomeShelf.MOST_PLAYED -> deps.settings.showMostPlayedShelf
        HomeShelf.PICK_UP -> true
    }

    /**
     * A on a GPU driver page row: System driver is used at once; an installed driver opens its options (Use this driver,
     * Remove), the one in use under the cursor when it is.
     */
    fun gpuDriverRowA(id: String) {
        if (id == GpuDriverChoice.SYSTEM) return useGpuDriver(id)
        val driver = deps.gpuDrivers?.installed()?.firstOrNull { it.id == id } ?: return host.refreshSettings()   // gone meanwhile
        host.openPicker(driver.name, gpuDriverOptions, if (deps.settings.gpuDriver == id) 0 else -1) { i ->
            when (gpuDriverOptions[i]) {
                GPU_DRIVER_USE -> useGpuDriver(id)
                GPU_DRIVER_REMOVE -> (screen.value as? Screen.Settings)?.takeIf { it.section == SettingsSection.GPU_DRIVER }?.let { screen.value = it.copy(confirmDriver = driver) }
            }
        }
    }

    /** [id] becomes the app-wide driver ([GpuDriverChoice.SYSTEM] or an installed one); the page marks it. */
    private fun useGpuDriver(id: String) {
        deps.settings.gpuDriver = id
        host.refreshSettings()
    }

    /** A pick in flight ([addGpuDriver]): a second A on Add driver waits for it. */
    private var gpuDriverAdd: Job? = null

    /**
     * Add driver: the platform's zip picker, then the install. A driver that installs is listed, not chosen; one refused
     * says why (the error dialog); a cancelled pick does nothing.
     */
    fun addGpuDriver() {
        val store = deps.gpuDrivers ?: return
        if (gpuDriverAdd?.isActive == true) return
        gpuDriverAdd = scope.launch {
            when (val result = store.add()) {
                null -> Unit
                is PackageCheck.Rejected -> error.value = result.reason
                is PackageCheck.Ok -> {
                    host.refreshSettingsOn(SettingsSection.GPU_DRIVER)
                    host.showNotice("Added ${result.driver.name}")
                }
            }
        }
    }

    /**
     * The confirmed Remove: the driver in use goes back to the system driver first (so no launch picks it up while it is
     * deleted), its crash records go, then its folder; consoles and games set to it follow the app-wide driver from now on.
     */
    fun removeGpuDriver(driver: GpuDriver) {
        val store = deps.gpuDrivers ?: return
        val s = deps.settings
        if (s.gpuDriver == driver.id) s.gpuDriver = GpuDriverChoice.SYSTEM
        s.gpuDriverCrashes = gpuCrashesWithout(s.gpuDriverCrashes, driver.id)
        (screen.value as? Screen.Settings)?.let { screen.value = it.copy(confirmDriver = null) }
        scope.launch {
            withContext(deps.compute) { tryQuietly { store.remove(driver.id) } }
            host.refreshSettingsOn(SettingsSection.GPU_DRIVER)   // its row goes
        }
    }

}

/**
 * Button layout's editors, as landscape flags: both while the screen turns, else only the one [windowPx] (width to
 * height) is in now, since the other's editor would ask for a rotation the device never makes. Both when the window is unknown.
 */
internal fun buttonLayoutOrientations(screenRotates: Boolean, windowPx: Pair<Int, Int>?): List<Boolean> =
    if (screenRotates || windowPx == null) listOf(false, true) else listOf(windowPx.first > windowPx.second)

/** The Wii's and the 3DS's console ids: Button layout's Controller row is theirs. */
private const val WII_CONSOLE = "wii"
private const val N3DS_CONSOLE = "3ds"
/** The consoles with the Sega pad's two skins ([com.nxredux.mobile.runner.PadMap.isSegaPadTag]'s). */
private val SEGA_PAD_CONSOLES = setOf("md", "segacd", "32x")

private fun keyboardLabel(android: Boolean): String = if (android) "Android" else "NX"
