package com.nxredux.mobile.ui

import com.nxredux.mobile.layout.DeviceTraits
import com.nxredux.mobile.tryOrAny
import com.nxredux.mobile.tryOrNull
import com.nxredux.mobile.layout.ScreenClass
import com.nxredux.mobile.netplay.NetplayCores
import com.nxredux.mobile.library.SystemFolder
import com.nxredux.mobile.options.ConfigFile
import com.nxredux.mobile.options.ConsoleRow
import com.nxredux.mobile.options.CoreConfig
import com.nxredux.mobile.options.CoreOption
import com.nxredux.mobile.options.CoreOptionFilter
import com.nxredux.mobile.options.CoreOptionSource
import com.nxredux.mobile.options.EmulatorPage
import com.nxredux.mobile.gpudriver.GpuDriver
import com.nxredux.mobile.gpudriver.GpuDriverChoice
import com.nxredux.mobile.library.path
import com.nxredux.mobile.options.EmulatorSettings
import com.nxredux.mobile.options.GpuDriverOptions
import com.nxredux.mobile.options.GraphicsApiOptions
import com.nxredux.mobile.options.OptionGroup
import com.nxredux.mobile.options.Ps2Options
import com.nxredux.mobile.runner.ButtonMap
import com.nxredux.mobile.runner.CoreDescriptor
import com.nxredux.mobile.runner.CoreRegistry
import com.nxredux.mobile.runner.MapKey
import com.nxredux.mobile.runner.Res3dConsole
import com.nxredux.mobile.shortcuts.KeyCodes
import com.nxredux.mobile.shortcuts.ShortcutCapture
import com.nxredux.mobile.storage.EmulatorConfigStore
import com.nxredux.mobile.ui.nx.InGameMenuModel
import com.nxredux.mobile.ui.nx.ListSelection
import com.nxredux.mobile.ui.nx.NxButton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

/** One core on the Emulator Settings list: its [tags] that have games, in registry order. */
data class EmulatorListRow(val coreId: String, val emulator: String, val tags: List<String>)

/** One row of the Emulator Settings / Game Settings page ([EmulatorRows.build]). */
sealed interface EmulatorRow {
    data class Header(val label: String) : EmulatorRow
    /** Game Settings with a game file: deletes it, so the game follows the emulator settings again. */
    data object UseEmulatorSettings : EmulatorRow
    /** The emulator's own page (not a game's): asks, then deletes the console file, so every value is its default again. */
    data object ResetDefaults : EmulatorRow
    data class Console(val row: ConsoleRow) : EmulatorRow
    data class Core(val option: CoreOption, val inCategory: Boolean) : EmulatorRow
    data class Category(val key: String, val label: String) : EmulatorRow
    data class Notice(val text: String) : EmulatorRow
    /** Half a row of space between two groups (the app's settings, the core settings): drawn empty, never selected. */
    data object Gap : EmulatorRow
    /**
     * An app-wide Settings row the page hosts after the emulator's own ([EmulatorAppSection]: mGBA's Cable Link), with its
     * [choices] as its Settings page would draw them; it changes that setting as Settings does ([EmulatorSettingsHost]).
     */
    data class App(val row: SettingsRow, val choices: SettingChoices?) : EmulatorRow
    /** The console's page, under Controls: opens its Button Mapping list. */
    data object ButtonMapping : EmulatorRow
    /** A Button Mapping row: [job]'s button, named [label], sits on physical [key]; [waiting] for a press to bind. */
    data class MapBind(val job: MapKey, val label: String, val key: MapKey, val waiting: Boolean = false) : EmulatorRow
    /** The Button Mapping list's last row: every button back on its own key. */
    data object ResetButtonMap : EmulatorRow
}

/** App-wide Settings rows a page shows last, under [label]: Cable Link's on mGBA's page ([EmulatorRows.hostsCableLink]). */
data class EmulatorAppSection(val label: String, val rows: List<EmulatorRow.App>)

/** Rows the selection can land on (headers and notices are skipped). */
val EmulatorRow.selectable: Boolean get() = this !is EmulatorRow.Header && this !is EmulatorRow.Notice && this != EmulatorRow.Gap

object EmulatorRows {
    const val NO_TABLE = "This emulator's options aren't available outside a game."

    /** The header of the Cable Link rows on mGBA's page. */
    const val CABLE_LINK_LABEL = "Cable Link"

    /** The tag of mGBA's Game Boy Advance page, the one Cable Link's settings are on ([hostsCableLink]). */
    const val CABLE_LINK_TAG = "MGBA"

    /**
     * [page] hosts the Cable Link settings: mGBA's own page (not a game's: they are the app's, not one game's) for a tag
     * Cable Link is offered on. A Cable Link session always runs mGBA ([NetplayCores.CABLE_LINK_CORE]), whatever core the
     * folder runs, so its settings belong with mGBA's.
     */
    fun hostsCableLink(page: EmulatorPage): Boolean =
        !page.forGame && page.coreId == NetplayCores.CABLE_LINK_CORE && NetplayCores.cableLinkOffered(page.tag)

    /**
     * [page]'s rows at the top level ([category] null) or inside that category; at the top level, [app]'s rows last
     * (after a gap, under their header).
     */
    fun build(page: EmulatorPage, category: String?, app: EmulatorAppSection? = null): List<EmulatorRow> {
        val core = page.core
        if (category != null) return core.orEmpty().let { CoreOptionFilter.inCategory(it, category) }.map { EmulatorRow.Core(it, inCategory = true) }
        // The app's settings by what they are for, a header per group (Display, Controls, Gameplay, Multiplayer, Audio), a gap
        // between; the console's own page ends Controls with Button Mapping.
        val byGroup = OptionGroup.grouped(page.console) { it.option.key }.toMap()
        val mapping = !page.forGame && page.mapRows.isNotEmpty()
        val appGroups = OptionGroup.entries.mapNotNull { g ->
            val rows = byGroup[g].orEmpty().map<ConsoleRow, EmulatorRow>(EmulatorRow::Console) +
                if (g == OptionGroup.CONTROLS && mapping) listOf(EmulatorRow.ButtonMapping) else emptyList()
            rows.takeIf { it.isNotEmpty() }?.let { g to it }
        }
        return buildList {
            appGroups.forEachIndexed { i, (group, rows) ->
                if (i > 0) add(EmulatorRow.Gap)
                add(EmulatorRow.Header(group.label))
                addAll(rows)
            }
            val coreRows = buildList {
                if (core == null) {
                    add(EmulatorRow.Header(InGameMenuModel.EMULATOR_LABEL))
                    add(EmulatorRow.Notice(NO_TABLE))
                } else {
                    val loose = CoreOptionFilter.uncategorized(core).map { EmulatorRow.Core(it, inCategory = false) }
                    val categories = CoreOptionFilter.categories(core).map { EmulatorRow.Category(it.key, it.label) }
                    if (loose.isNotEmpty() || categories.isNotEmpty()) {
                        add(EmulatorRow.Header(InGameMenuModel.EMULATOR_LABEL))
                        addAll(loose)
                        addAll(categories)
                    }
                }
            }
            if (appGroups.isNotEmpty() && coreRows.isNotEmpty()) add(EmulatorRow.Gap)   // space between the two groups
            addAll(coreRows)
            // The emulator's page with values of its own: Reset to defaults after them, before the app-wide rows it leaves alone.
            if (!page.forGame && (appGroups.isNotEmpty() || !core.isNullOrEmpty())) { add(EmulatorRow.Gap); add(EmulatorRow.ResetDefaults) }
            if (app != null && app.rows.isNotEmpty()) {
                if (isNotEmpty()) add(EmulatorRow.Gap)
                add(EmulatorRow.Header(app.label))
                addAll(app.rows)
            }
            // Last, after a gap: drop this game's own settings for the emulator's.
            if (page.forGame && page.gameFileExists) { if (isNotEmpty()) add(EmulatorRow.Gap); add(EmulatorRow.UseEmulatorSettings) }
        }
    }

    /** [page]'s Button Mapping list: a row per button showing the key that does its job, [waiting]'s waiting; then Reset to default. */
    fun buttonMapping(page: EmulatorPage, waiting: MapKey?): List<EmulatorRow> =
        page.mapRows.map { EmulatorRow.MapBind(it.job, it.label, page.buttonMap.keyFor(it.job), it.job == waiting) } + EmulatorRow.ResetButtonMap

    fun firstSelectable(rows: List<EmulatorRow>): Int = rows.indexOfFirst { it.selectable }.coerceAtLeast(0)

    /** The next selectable row [delta] steps from [index], wrapping; [index] itself when none other is. */
    fun step(rows: List<EmulatorRow>, index: Int, delta: Int): Int {
        val n = rows.size
        if (n == 0) return 0
        for (k in 1..n) {
            val i = (index + k * delta).mod(n)
            if (rows[i].selectable) return i
        }
        return index
    }

    /** Where [previous] (a row of an earlier build) is in [rows], by what it edits; null when it is gone. */
    fun indexOfSame(rows: List<EmulatorRow>, previous: EmulatorRow?): Int? {
        val i = rows.indexOfFirst { r ->
            when (previous) {
                is EmulatorRow.Console -> r is EmulatorRow.Console && r.row.option.key == previous.row.option.key
                is EmulatorRow.Core -> r is EmulatorRow.Core && r.option.key == previous.option.key
                is EmulatorRow.Category -> r is EmulatorRow.Category && r.key == previous.key
                EmulatorRow.UseEmulatorSettings -> r == EmulatorRow.UseEmulatorSettings
                EmulatorRow.ResetDefaults -> r == EmulatorRow.ResetDefaults
                is EmulatorRow.App -> r is EmulatorRow.App && r.row.kind == previous.row.kind
                EmulatorRow.ButtonMapping -> r == EmulatorRow.ButtonMapping
                is EmulatorRow.MapBind -> r is EmulatorRow.MapBind && r.job == previous.job
                EmulatorRow.ResetButtonMap -> r == EmulatorRow.ResetButtonMap
                else -> false
            }
        }
        return i.takeIf { it >= 0 }
    }
}

/** What [EmulatorSettingsController] needs from [AppState] beyond the flows it is handed. */
internal interface EmulatorSettingsHost {
    /** Settings -> Emulation, on the Default settings row. */
    fun showEmulatorsSettings()

    /** Back to the game list Game Settings was opened from ([screen] as it was), then rebuilt from its source on the same game. */
    fun restore(screen: Screen, selection: ListSelection)

    /**
     * The app's rows [page] shows last ([EmulatorRow.App]), as they stand now: mGBA's Cable Link, or [game]'s Badge on
     * its Game Settings; null for a page without.
     */
    fun appSection(page: EmulatorPage, game: Listed?): EmulatorAppSection?

    /** Hosted row [row]'s setting stepped [delta] places, wrapping, as LEFT / RIGHT on its Settings page; the page is not left. */
    fun stepAppRow(row: SettingsRow, delta: Int)

    /** Hosted row [row]'s setting set to its choice [index] ([SettingChoices]), as a pick on its Settings page. */
    fun chooseAppRow(row: SettingsRow, index: Int)

    /** A on hosted row [row] that has no values (Clear link ROM cache): what A does on its Settings page. */
    fun runAppRow(row: SettingsRow)

    /** Page [key] rebuilt, the selected row from [from] to [to] (null: gone): the row being changed follows ([SettingsController.followRowEdit]). */
    fun followRowEdit(key: Any?, from: Int, to: Int?) {}

    /** A message dialog over the page, [title] over [text]; A or B closes it. */
    fun showMessage(title: String, text: String)
}

/**
 * Emulator Settings (Settings -> Emulation -> Default settings): the core list, the console picker, and the per-tag
 * page; Game Settings: the same page for one game. Every read and write goes through [configs], off the main thread, one
 * at a time; each change reads both files fresh, writes, and shows the page built from what it wrote.
 */
internal class EmulatorSettingsController(
    private val scope: CoroutineScope,
    private val screen: MutableStateFlow<Screen>,
    private val selection: MutableStateFlow<ListSelection>,
    private val error: MutableStateFlow<String?>,
    private val coreOptions: CoreOptionSource,
    private val configs: EmulatorConfigStore,
    /** Extra defaults per core id from the platform (descriptor defaults win over them). */
    private val coreDefaults: (String) -> Map<String, String>,
    private val host: EmulatorSettingsHost,
    /** Where the pages are read and written off the main thread ([AppDeps.compute]); tests pass their own. */
    private val work: CoroutineContext = Dispatchers.Default,
    /** Whether Vulkan failed for a core id on this phone ([com.nxredux.mobile.options.VulkanFallback]). */
    private val vulkanFailed: (String) -> Boolean = { false },
    /** Whether a qualifying second screen is connected now: the Bottom screen row shows only then. */
    private val secondScreen: () -> Boolean = { true },
    /** The current window's screen class (null: unknown): the DS and 3DS layout rows follow it. */
    private val screenClass: () -> ScreenClass? = { null },
    /** The current window's size in px, width to height (null: unknown): the DS and 3DS 3D Resolution scales go up to what it reaches. */
    private val windowPx: () -> Pair<Int, Int>? = { null },
    /** The ".bin" names in the PS2 BIOS folder (Bios/PS2/), read off the main thread: the PS2 BIOS row's choices. */
    private val ps2BiosFiles: () -> List<String> = { emptyList() },
    /**
     * Tags whose page is listed even without games: mGBA's for GBA ([EmulatorRows.CABLE_LINK_TAG]) where Cable Link exists, since a
     * session runs mGBA whatever Game Boy Advance's default is and its settings are on that page.
     */
    private val alwaysListed: () -> List<String> = { emptyList() },
    /** The installed custom GPU drivers (null: the feature is unavailable, so no GPU driver row). */
    private val gpuDrivers: () -> List<GpuDriver>? = { null },
    /** The app-wide driver id (Settings -> Emulation -> GPU driver), null for the system driver. */
    private val gpuDriverAppWide: () -> String? = { null },
    /** The driver ids that crashed in the game at this library path ([com.nxredux.mobile.library.path]). */
    private val gpuDriverCrashed: (gamePath: String) -> Set<String> = { emptySet() },
    /** Forgets the driver crashes recorded for the game at this library path. */
    private val clearGpuDriverCrash: (gamePath: String) -> Unit = {},
    /** The phone's GPU name (GL_RENDERER; null: unknown), asked once, for a core with a Vulkan path. */
    private val gpuName: suspend () -> String? = { null },
) {
    private var gpu: String? = null   // [gpuName] once it answered
    private val lock = Mutex()
    private var listRows: List<EmulatorListRow> = emptyList()
    private var listRow: EmulatorListRow? = null          // the core whose picker or page is open
    private var fromPicker = false                       // the emulator page was opened from the console picker
    private var gameReturn: Pair<Screen, ListSelection>? = null   // the game list Game Settings came from
    private var mapping: MapKey? = null                  // the job whose Button Mapping row waits for a press ([captureKey])
    private val mapHeld = HashSet<Int>()                 // keys pressed since a wait began: their releases stay the wait's

    companion object {
        /**
         * One row per core with a tag that has games, or one of [always] (listed without games), sorted by emulator name
         * ([com.nxredux.mobile.runner.CoreDescriptor.name]).
         */
        fun listRows(systems: List<SystemFolder>, always: List<String> = emptyList()): List<EmulatorListRow> {
            val present = (systems.filter { !it.unassigned && it.gameCount > 0 }.map { it.tag } + always).distinct()
            return present.groupBy { CoreRegistry.forTag(it)?.id }.mapNotNull { (id, tags) ->
                id ?: return@mapNotNull null
                val ordered = CoreRegistry.tagsSharingCore(tags.first()).filter { it in present }
                EmulatorListRow(id, CoreRegistry.forId(id)?.name ?: id, ordered)
            }.sortedBy { it.emulator.lowercase() }
        }
    }

    /** Settings -> Emulation -> Default settings. */
    fun openList(systems: List<SystemFolder>) {
        val from = screen.value
        scope.launch {
            val rows = withContext(work) { listRows(systems, alwaysListed()) }
            if (screen.value !== from) return@launch
            listRows = rows
            showList(null)
        }
    }

    /** Game Settings: [entry]'s own tag and core, writing its `<rom>.cfg`; B returns to the list it came from. */
    fun openGame(entry: Listed) {
        if (entry.system.unassigned) return
        val descriptor = entry.system.core ?: CoreRegistry.forTag(entry.system.tag) ?: return
        val from = screen.value
        val back = (from as? Screen.Games)?.copy(menu = null) ?: from
        val at = selection.value
        openPage(entry.system.tag, descriptor, entry, still = { base(screen.value) == base(from) }) {
            gameReturn = back to at
        }
    }

    private fun base(s: Screen): Screen = (s as? Screen.Games)?.copy(menu = null) ?: s

    /** The game list (and its selection) the open Game Settings came from and is drawn over. */
    val gameUnder: Pair<Screen, ListSelection>? get() = gameReturn

    /** Closes Game Settings from any level (its modal's scrim): back to the game list on the same game. */
    fun closeGame(s: Screen.EmulatorOptions) {
        if (s.game == null) return
        gameReturn?.let { (to, at) -> gameReturn = null; host.restore(to, at) }
    }

    fun onButton(s: Screen, button: NxButton): Boolean {
        when (s) {
            is Screen.EmulatorList -> when (button) {
                NxButton.UP -> selection.value = selection.value.up()
                NxButton.DOWN -> selection.value = selection.value.down()
                NxButton.A -> s.rows.getOrNull(selection.value.index)?.let(::openCore)
                NxButton.B -> back(s)
                else -> Unit
            }
            is Screen.EmulatorConsoles -> when (button) {
                NxButton.UP -> selection.value = selection.value.up()
                NxButton.DOWN -> selection.value = selection.value.down()
                NxButton.A -> s.tags.getOrNull(selection.value.index)?.let { tag ->
                    CoreRegistry.forTag(tag)?.let { d -> openPage(tag, d, null, still = { screen.value === s }) { fromPicker = true } }
                }
                NxButton.B -> back(s)
                else -> Unit
            }
            is Screen.EmulatorOptions -> onPageButton(s, button)
            else -> return false
        }
        return true
    }

    fun back(s: Screen): Boolean {
        when (s) {
            is Screen.EmulatorList -> host.showEmulatorsSettings()
            is Screen.EmulatorConsoles -> showList(listRow)
            is Screen.EmulatorOptions -> when {
                s.confirmReset -> screen.value = s.copy(confirmReset = false)   // B on the question keeps everything
                s.buttonMapping -> {
                    mapping = null
                    val rows = rows(s.page, null, s.game)
                    screen.value = s.copy(rows = rows, buttonMapping = false)
                    selection.value = ListSelection(rows.indexOf(EmulatorRow.ButtonMapping).coerceAtLeast(0), rows.size)
                }
                s.category != null -> {
                    val rows = rows(s.page, null, s.game)
                    screen.value = s.copy(rows = rows, category = null)
                    selection.value = ListSelection(rows.indexOf(rows.firstOrNull { it is EmulatorRow.Category && it.key == s.category }).coerceAtLeast(0), rows.size)
                }
                s.game != null -> closeGame(s)
                fromPicker -> listRow?.let { showConsoles(it, s.page.tag) } ?: showList(null)
                else -> showList(listRow)
            }
            else -> return false
        }
        return true
    }

    private fun showList(on: EmulatorListRow?) {
        screen.value = Screen.EmulatorList(listRows)
        val at = on?.let { r -> listRows.indexOfFirst { it.coreId == r.coreId } } ?: -1
        selection.value = ListSelection(at.coerceAtLeast(0), listRows.size)
    }

    private fun showConsoles(row: EmulatorListRow, on: String?) {
        screen.value = Screen.EmulatorConsoles(row.coreId, row.emulator, row.tags)
        selection.value = ListSelection(on?.let { row.tags.indexOf(it) }?.coerceAtLeast(0) ?: 0, row.tags.size)
    }

    private fun openCore(row: EmulatorListRow) {
        listRow = row
        if (row.tags.size > 1) { showConsoles(row, null); return }
        val tag = row.tags.firstOrNull() ?: return
        val descriptor = CoreRegistry.forTag(tag) ?: return
        val from = screen.value
        openPage(tag, descriptor, null, still = { screen.value === from }) { fromPicker = false }
    }

    // --- The page ---

    private fun defaults(d: CoreDescriptor) = coreDefaults(d.id) + d.defaultOptions

    /** [page]'s rows ([EmulatorRows.build]), the app's own rows ([EmulatorSettingsHost.appSection]) last at the top level. */
    private fun rows(page: EmulatorPage, category: String?, game: Listed?): List<EmulatorRow> =
        EmulatorRows.build(page, category, if (category == null) host.appSection(page, game) else null)

    /** [s]'s rows for [page]: its Button Mapping list while that is open, else [rows]. */
    private fun rowsOf(s: Screen.EmulatorOptions, page: EmulatorPage): List<EmulatorRow> =
        if (s.buttonMapping) EmulatorRows.buttonMapping(page, mapping) else rows(page, s.category, s.game)

    private fun build(tag: String, d: CoreDescriptor, game: Listed?, files: Pair<ConfigFile?, ConfigFile?>): EmulatorPage {
        val screen = screenClass()
        val drivers = gpuDrivers()
        fun page(resolved: Boolean = false, crashNote: String? = null) = EmulatorSettings.page(
            tag, d, coreOptions.table(d.id), defaults(d), files.first, files.second, forGame = game != null, vulkanFailed = vulkanFailed(d.id),
            secondScreen = secondScreen(), screen = screen, displayMax = Res3dConsole.forTag(tag)?.displayMaxOf(windowPx(), screen),
            biosFiles = if (Ps2Options.appliesTo(tag)) ps2BiosFiles() else emptyList(),
            gpuDrivers = drivers, gpuDriverResolved = resolved, gpuDriverCrashNote = crashNote,
            autoVulkan = GraphicsApiOptions.autoIsVulkan(d.vulkan, gpu, customDriver(d, game, files, drivers)),
            mali = DeviceTraits.isMali(gpu),
        )
        val first = page()
        // The GPU driver row shows only on Vulkan, so its presence says the page's Graphics API is Vulkan and what it shows is what the launch reads.
        val shown = first.console.firstOrNull { it.option.key == GpuDriverOptions.KEY }
        if (drivers == null || shown == null) return first
        val installed = drivers.map { it.id }.toSet()
        val crashed = game?.let { gpuDriverCrashed(it.game.path) }.orEmpty()
        val wanted = GpuDriverChoice.resolve(shown.value, gpuDriverAppWide(), installed, emptySet())
        val note = wanted?.takeIf { it in crashed }?.let { id -> GpuDriverOptions.CRASH_NOTE.replace("%s", drivers.first { it.id == id }.name) }
        val resolved = GpuDriverChoice.resolve(shown.value, gpuDriverAppWide(), installed, crashed) != null
        return if (resolved || note != null) page(resolved, note) else first
    }

    /** Whether the game (or, with no [game], the console's games) would load a custom GPU driver: the stored pick, else app-wide. */
    private fun customDriver(d: CoreDescriptor, game: Listed?, files: Pair<ConfigFile?, ConfigFile?>, drivers: List<GpuDriver>?): Boolean {
        if (drivers == null || d.vulkan?.maliNeedsCustomDriver != true) return false
        val stored = (if (game != null) files.second ?: files.first else files.first)?.values?.get(GpuDriverOptions.KEY)
        val crashed = game?.let { gpuDriverCrashed(it.game.path) }.orEmpty()
        return GpuDriverChoice.resolve(stored, gpuDriverAppWide(), drivers.mapTo(HashSet()) { it.id }, crashed) != null
    }

    private suspend fun readPage(tag: String, d: CoreDescriptor, game: Listed?): EmulatorPage {
        // Every Vulkan core asks: Auto follows it on some, and a Mali's Graphics API row says more on all.
        if (d.vulkan != null && gpu == null) gpu = gpuName()?.also { DeviceTraits.gpu = it }
        return build(tag, d, game, configs.read(tag, d.id, game?.game?.fileName))
    }

    /** Reads and shows [tag]'s page when [still] holds (the user has not moved on meanwhile); [opened] runs just before it shows. */
    private fun openPage(tag: String, d: CoreDescriptor, game: Listed?, still: () -> Boolean, opened: () -> Unit) {
        scope.launch {
            val page = lock.withLock {
                tryOrAny({
                    error.value = "Could not read the settings"
                    null
                }) { withContext(work) { readPage(tag, d, game) } }
            } ?: return@launch
            if (!still()) return@launch
            opened()
            val rows = rows(page, null, game)
            screen.value = Screen.EmulatorOptions(page, rows, null, game)
            selection.value = ListSelection(EmulatorRows.firstSelectable(rows), rows.size)
        }
    }

    private fun onPageButton(s: Screen.EmulatorOptions, button: NxButton) {
        if (s.confirmReset) {   // the question takes every button: A resets, B keeps
            when (button) {
                NxButton.A -> { screen.value = s.copy(confirmReset = false); resetDefaults(s) }
                NxButton.B -> back(s)
                else -> Unit
            }
            return
        }
        // A pad's keys are the wait's ([captureKey]), so a press here is a touch: it ends the wait, and B does nothing more.
        if (mapping != null) { endMapping(); if (button == NxButton.B) return }
        val row = s.rows.getOrNull(selection.value.index)
        when (button) {
            NxButton.UP -> selection.value = ListSelection(EmulatorRows.step(s.rows, selection.value.index, -1), s.rows.size)
            NxButton.DOWN -> selection.value = ListSelection(EmulatorRows.step(s.rows, selection.value.index, 1), s.rows.size)
            NxButton.LEFT -> if (row is EmulatorRow.App) stepApp(row, -1) else change(s, row) { page, key -> nextValue(page, key, -1) }
            NxButton.RIGHT -> if (row is EmulatorRow.App) stepApp(row, 1) else change(s, row) { page, key -> nextValue(page, key, 1) }
            NxButton.A -> when (row) {
                is EmulatorRow.Console, is EmulatorRow.Core -> change(s, row) { page, key -> nextValue(page, key, 1) }
                // A value row steps forward, as RIGHT (a picker's A opens it before this: AppState); Clear clears.
                is EmulatorRow.App -> if (row.choices != null) stepApp(row, 1) else { host.runAppRow(row.row); showAppRows() }
                is EmulatorRow.Category -> {
                    val rows = EmulatorRows.build(s.page, row.key)
                    screen.value = s.copy(rows = rows, category = row.key)
                    selection.value = ListSelection(0, rows.size)
                }
                EmulatorRow.UseEmulatorSettings -> useEmulatorSettings(s)
                EmulatorRow.ResetDefaults -> screen.value = s.copy(confirmReset = true)   // asks first
                EmulatorRow.ButtonMapping -> {
                    mapping = null
                    mapHeld.clear()
                    val rows = EmulatorRows.buttonMapping(s.page, null)
                    screen.value = s.copy(rows = rows, buttonMapping = true)
                    selection.value = ListSelection(0, rows.size)
                }
                is EmulatorRow.MapBind -> { mapping = row.job; refreshMapping() }
                EmulatorRow.ResetButtonMap -> writeMap(s) { ButtonMap.DEFAULT }
                else -> Unit
            }
            NxButton.B -> back(s)
            else -> Unit
        }
    }

    /**
     * The selected value row's choices for a touch chooser: its title, every value it steps through (labelled as the row
     * shows them) and the current one's place (-1: a value not among them, as a retired one); null for any other row.
     */
    fun choices(s: Screen.EmulatorOptions): Triple<String, List<String>, Int>? = when (val row = s.rows.getOrNull(selection.value.index)) {
        is EmulatorRow.Console -> if (row.row.disabledNote != null) null else row.row.option.let { o -> Triple(o.label, o.values.map(o::labelOf), o.values.indexOf(row.row.value)) }
        is EmulatorRow.Core -> row.option.let { o ->
            val values = CoreOptionFilter.visibleValues(o)
            Triple(CoreOptionFilter.rowLabel(o, row.inCategory), values.map { v -> CoreOptionFilter.valueLabel(o.copy(value = v)) }, values.indexOf(o.value))
        }
        is EmulatorRow.App -> row.choices?.takeIf { row.row.enabled }?.let { Triple(row.row.label, it.labels, it.index) }
        else -> null
    }

    /** A touch chooser's pick [index] for the selected row ([choices]): that value written, as stepping there would. */
    fun choose(index: Int) {
        val s = screen.value as? Screen.EmulatorOptions ?: return
        val row = s.rows.getOrNull(selection.value.index)
        val (_, labels, current) = choices(s) ?: return
        if (index !in labels.indices || index == current) return
        if (row is EmulatorRow.App) { host.chooseAppRow(row.row, index); showAppRows(); return }
        val value = when (row) {
            is EmulatorRow.Console -> row.row.option.values[index]
            is EmulatorRow.Core -> CoreOptionFilter.visibleValues(row.option)[index]
            else -> return
        }
        change(s, row) { _, _ -> value }
    }

    private fun samePage(a: Screen.EmulatorOptions, b: Screen.EmulatorOptions) =
        a.page.tag == b.page.tag && a.page.coreId == b.page.coreId && a.game == b.game

    private fun descriptorOf(s: Screen.EmulatorOptions): CoreDescriptor? =
        s.game?.system?.core?.takeIf { it.id == s.page.coreId } ?: CoreRegistry.forTag(s.page.tag)?.takeIf { it.id == s.page.coreId }

    /**
     * LEFT/RIGHT (A = RIGHT) on a value row: reads both files, steps the value as that read has it, writes the one file
     * the page edits, and shows the page built from the written text. A failed read or write says so and shows the disk.
     */
    private fun change(s: Screen.EmulatorOptions, row: EmulatorRow?, next: (EmulatorPage, String) -> String?) {
        val key = when (row) {
            is EmulatorRow.Console -> if (row.row.disabledNote != null) return else row.row.option.key
            is EmulatorRow.Core -> row.option.key
            else -> return
        }
        val d = descriptorOf(s) ?: return
        val tag = s.page.tag
        val game = s.game
        val rom = game?.game?.fileName
        scope.launch {
            lock.withLock {
                val page = withContext(work) {
                    tryOrAny({
                        error.value = "Could not save the setting"
                        tryOrNull { readPage(tag, d, game) }
                    }) {
                        val files = configs.read(tag, d.id, rom)
                        val page = build(tag, d, game, files)
                        val value = next(page, key) ?: return@withContext page
                        if (rom == null) {
                            val text = EmulatorSettings.consoleWrite(files.first, key, value)
                            configs.writeConsole(tag, d.id, text)
                            build(tag, d, game, CoreConfig.parse(text) to files.second)
                        } else {
                            val text = EmulatorSettings.gameWrite(page, files.first, files.second, key, value)
                            configs.writeGame(tag, d.id, rom, text)
                            if (key == GpuDriverOptions.KEY) game?.let { clearGpuDriverCrash(it.game.path) }
                            build(tag, d, game, files.first to CoreConfig.parse(text))
                        }
                    }
                }
                page?.let { show(s, it) }
            }
        }
    }

    /** A Button Mapping row waits for a press: the platform hands raw keys to [captureKey] first. */
    val mappingWaits: Boolean get() = mapping != null && (screen.value as? Screen.EmulatorOptions)?.buttonMapping == true

    /**
     * A raw key from the platform while a Button Mapping row waits, and the release of any key pressed since (system keys
     * never): true when the wait took it. A pad key ([MapKey]) binds the waiting row, swapping with the key that had the
     * job ([ButtonMap.bind]); Back cancels, since B is a key to bind here; any other key (START, SELECT, MENU, the d-pad,
     * and L2 / R2 where they are analog triggers: [ButtonMap.binds]) is refused with a message.
     */
    fun captureKey(keyCode: Int, down: Boolean): Boolean {
        val s = screen.value as? Screen.EmulatorOptions
        if (mapping != null && s?.buttonMapping != true) mapping = null   // a stale wait: never another page's keys
        if (keyCode in ShortcutCapture.SYSTEM || !(keyCode in mapHeld || (down && mapping != null))) return false
        if (!down) { mapHeld -= keyCode; return true }
        mapHeld += keyCode
        val job = mapping ?: return true
        endMapping()
        if (keyCode == KeyCodes.BACK || s == null) return true
        val physical = MapKey.ofKeyCode(keyCode)?.takeIf { ButtonMap.binds(s.page.tag, it) }
        if (physical == null) {
            host.showMessage(s.page.mapRows.firstOrNull { it.job == job }?.label ?: job.label, InGameMenuModel.mapPickNote(s.page.tag))
            return true
        }
        writeMap(s) { it.bind(job, physical) }
        return true
    }

    /** Ends a Button Mapping wait without a binding (the system Back); false when none waits. */
    fun cancelMapping(): Boolean {
        if (!mappingWaits) return false
        endMapping()
        return true
    }

    private fun endMapping() {
        mapping = null
        refreshMapping()
    }

    /** The open Button Mapping list redrawn for the wait starting or ending; nothing is read. */
    private fun refreshMapping() {
        val cur = screen.value as? Screen.EmulatorOptions ?: return
        if (cur.buttonMapping) screen.value = cur.copy(rows = EmulatorRows.buttonMapping(cur.page, mapping))
    }

    /**
     * The console's button mapping changed to [next] of the one the console file has now: written as its `nx_map_*` lines
     * ([EmulatorSettings.consoleMapWrite]), then shown. Nothing is written when it stays the same.
     */
    private fun writeMap(s: Screen.EmulatorOptions, next: (ButtonMap) -> ButtonMap) {
        if (s.game != null) return
        val d = descriptorOf(s) ?: return
        val tag = s.page.tag
        scope.launch {
            lock.withLock {
                val page = withContext(work) {
                    tryOrAny({
                        error.value = "Could not save the setting"
                        tryOrNull { readPage(tag, d, null) }
                    }) {
                        val files = configs.read(tag, d.id, null)
                        val page = build(tag, d, null, files)
                        val map = next(page.buttonMap)
                        if (map == page.buttonMap) return@withContext page
                        val text = EmulatorSettings.consoleMapWrite(files.first, map)
                        configs.writeConsole(tag, d.id, text)
                        build(tag, d, null, CoreConfig.parse(text) to files.second)
                    }
                }
                page?.let { show(s, it) }
            }
        }
    }

    /**
     * A second screen came or went: the open page (if any) is read again, so its Bottom screen row follows. A page left
     * with no rows at all (Bottom screen was its only one: the others locked, no core options) closes, as B would.
     */
    fun refreshOpenPage() {
        val s = screen.value as? Screen.EmulatorOptions ?: return
        val d = descriptorOf(s) ?: return
        scope.launch {
            lock.withLock {
                val page = withContext(work) { tryOrNull { readPage(s.page.tag, d, s.game) } } ?: return@withLock
                val cur = screen.value as? Screen.EmulatorOptions
                if (cur != null && samePage(cur, s) && rows(page, null, s.game).isEmpty()) back(cur.copy(category = null))
                else show(s, page)
            }
        }
    }

    /** A on "Use emulator settings": deletes the game file and shows the page as the console file has it. */
    private fun useEmulatorSettings(s: Screen.EmulatorOptions) {
        val game = s.game ?: return
        val d = descriptorOf(s) ?: return
        deleteThenShow(s, d, game, "Could not save the setting") {
            configs.deleteGame(s.page.tag, d.id, game.game.fileName)
        }
    }

    /** The confirmed Reset to defaults: deletes the console file and shows the page as the defaults have it. */
    private fun resetDefaults(s: Screen.EmulatorOptions) {
        if (s.game != null) return
        val d = descriptorOf(s) ?: return
        deleteThenShow(s, d, null, "Could not reset the settings") { configs.deleteConsole(s.page.tag, d.id) }
    }

    /** Runs [delete] on a settings file (a failure says [failed]), then shows the page as the disk has it for [game]. */
    private fun deleteThenShow(
        s: Screen.EmulatorOptions, d: CoreDescriptor, game: Listed?, failed: String, delete: suspend () -> Unit,
    ) {
        scope.launch {
            lock.withLock {
                val page = withContext(work) {
                    tryOrAny({ error.value = failed }) { delete() }
                    tryOrNull { readPage(s.page.tag, d, game) }
                }
                page?.let { show(s, it) }
            }
        }
    }

    private fun nextValue(page: EmulatorPage, key: String, delta: Int): String? {
        page.console.firstOrNull { it.option.key == key }?.let { r ->
            val values = r.option.values
            if (values.isEmpty()) return null
            val i = values.indexOf(r.value)
            return if (i < 0) (if (delta >= 0) values.first() else values.last()) else values[(i + delta).mod(values.size)]
        }
        return page.core?.firstOrNull { it.key == key }?.let { CoreOptionFilter.cycle(it, delta) }
    }

    /** A hosted row's setting stepped [delta] places, then shown. */
    private fun stepApp(row: EmulatorRow.App, delta: Int) {
        if (row.choices == null || !row.row.enabled) return
        host.stepAppRow(row.row, delta)
        showAppRows()
    }

    /** The open top-level page again with its hosted rows as they stand now (a value changed, the cache cleared); nothing is read. */
    private fun showAppRows() {
        val cur = screen.value as? Screen.EmulatorOptions ?: return
        if (cur.category == null) show(cur, cur.page)
    }

    /** Shows [page] in place of [was]'s when that page is still open, the selection on the same row where it still is. */
    private fun show(was: Screen.EmulatorOptions, page: EmulatorPage) {
        val cur = screen.value as? Screen.EmulatorOptions ?: return
        if (!samePage(cur, was)) return
        val from = selection.value.index
        val previous = cur.rows.getOrNull(from)
        val rows = rowsOf(cur, page)
        val next = cur.copy(page = page, rows = rows)
        screen.value = next
        val same = EmulatorRows.indexOfSame(rows, previous)
        val at = same
            ?: from.takeIf { rows.getOrNull(it)?.selectable == true }
            ?: EmulatorRows.firstSelectable(rows)
        selection.value = ListSelection(at, rows.size)
        host.followRowEdit(rowEditKey(next), from, same)
    }
}
