package com.nxredux.mobile.ui

import com.nxredux.mobile.library.SystemFolder
import com.nxredux.mobile.options.ConfigFile
import com.nxredux.mobile.options.ConsoleRow
import com.nxredux.mobile.options.CoreConfig
import com.nxredux.mobile.options.CoreOption
import com.nxredux.mobile.options.CoreOptionFilter
import com.nxredux.mobile.options.CoreOptionSource
import com.nxredux.mobile.options.EmulatorPage
import com.nxredux.mobile.options.EmulatorSettings
import com.nxredux.mobile.runner.CoreDescriptor
import com.nxredux.mobile.runner.CoreRegistry
import com.nxredux.mobile.storage.EmulatorConfigStore
import com.nxredux.mobile.ui.nx.ListSelection
import com.nxredux.mobile.ui.nx.NxButton
import kotlinx.coroutines.CancellationException
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
    data class Console(val row: ConsoleRow) : EmulatorRow
    data class Core(val option: CoreOption, val inCategory: Boolean) : EmulatorRow
    data class Category(val key: String, val label: String) : EmulatorRow
    data class Notice(val text: String) : EmulatorRow
    /** Half a row of space between the console settings and the core settings: drawn empty, never selected. */
    data object Gap : EmulatorRow
}

/** Rows the selection can land on (headers and notices are skipped). */
val EmulatorRow.selectable: Boolean get() = this !is EmulatorRow.Header && this !is EmulatorRow.Notice && this != EmulatorRow.Gap

object EmulatorRows {
    const val NO_TABLE = "This core's options aren't available outside a game."

    /** [page]'s rows at the top level ([category] null) or inside that category. */
    fun build(page: EmulatorPage, category: String?): List<EmulatorRow> {
        val core = page.core
        if (category != null) return core.orEmpty().let { CoreOptionFilter.inCategory(it, category) }.map { EmulatorRow.Core(it, inCategory = true) }
        return buildList {
            if (page.console.isNotEmpty()) {
                add(EmulatorRow.Header("Console settings"))
                page.console.forEach { add(EmulatorRow.Console(it)) }
            }
            val coreRows = buildList {
                if (core == null) {
                    add(EmulatorRow.Header("Core settings"))
                    add(EmulatorRow.Notice(NO_TABLE))
                } else {
                    val loose = CoreOptionFilter.uncategorized(core).map { EmulatorRow.Core(it, inCategory = false) }
                    val categories = CoreOptionFilter.categories(core).map { EmulatorRow.Category(it.key, it.label) }
                    if (loose.isNotEmpty() || categories.isNotEmpty()) {
                        add(EmulatorRow.Header("Core settings"))
                        addAll(loose)
                        addAll(categories)
                    }
                }
            }
            if (page.console.isNotEmpty() && coreRows.isNotEmpty()) add(EmulatorRow.Gap)   // space between the two groups
            addAll(coreRows)
            // Last, after a gap: drop this game's own settings for the emulator's.
            if (page.forGame && page.gameFileExists) { if (isNotEmpty()) add(EmulatorRow.Gap); add(EmulatorRow.UseEmulatorSettings) }
        }
    }

    fun firstSelectable(rows: List<EmulatorRow>): Int = rows.indexOfFirst { it.selectable }.coerceAtLeast(0)

    /** The next selectable row [delta] steps from [index], wrapping; [index] itself when none other is. */
    fun step(rows: List<EmulatorRow>, index: Int, delta: Int): Int {
        val n = rows.size
        if (n == 0) return 0
        for (k in 1..n) {
            val i = ((index + k * delta) % n + n) % n
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
                else -> false
            }
        }
        return i.takeIf { it >= 0 }
    }
}

/** What [EmulatorSettingsController] needs from [AppState] beyond the flows it is handed. */
internal interface EmulatorSettingsHost {
    /** Settings -> Emulators, on the Emulator Settings row. */
    fun showEmulatorsSettings()

    /** Back to the game list Game Settings was opened from ([screen] as it was), then rebuilt from its source on the same game. */
    fun restore(screen: Screen, selection: ListSelection)
}

/**
 * Emulator Settings (Settings -> Emulators): the core list, the console picker, and the per-tag page; Game Settings: the
 * same page for one game. Every read and write goes through [configs], off the main thread, one at a time; each change
 * reads both files fresh, writes, and shows the page built from what it wrote.
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
) {
    private val lock = Mutex()
    private var listRows: List<EmulatorListRow> = emptyList()
    private var listRow: EmulatorListRow? = null          // the core whose picker or page is open
    private var fromPicker = false                       // the emulator page was opened from the console picker
    private var gameReturn: Pair<Screen, ListSelection>? = null   // the game list Game Settings came from

    companion object {
        /** One row per core with a tag that has games, sorted by emulator name ([com.nxredux.mobile.runner.CoreDescriptor.name]). */
        fun listRows(systems: List<SystemFolder>): List<EmulatorListRow> {
            val present = systems.filter { !it.unassigned && it.gameCount > 0 }.map { it.tag }.distinct()
            return present.groupBy { CoreRegistry.forTag(it)?.id }.mapNotNull { (id, tags) ->
                id ?: return@mapNotNull null
                val ordered = CoreRegistry.tagsSharingCore(tags.first()).filter { it in present }
                EmulatorListRow(id, CoreRegistry.forId(id)?.name ?: id, ordered)
            }.sortedBy { it.emulator.lowercase() }
        }
    }

    /** Settings -> Emulators -> Emulator Settings. */
    fun openList(systems: List<SystemFolder>) {
        val from = screen.value
        scope.launch {
            val rows = withContext(work) { listRows(systems) }
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
                s.category != null -> {
                    val rows = EmulatorRows.build(s.page, null)
                    screen.value = s.copy(rows = rows, category = null)
                    selection.value = ListSelection(rows.indexOf(rows.firstOrNull { it is EmulatorRow.Category && it.key == s.category }).coerceAtLeast(0), rows.size)
                }
                s.game != null -> gameReturn?.let { (to, at) -> gameReturn = null; host.restore(to, at) }
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

    private fun build(tag: String, d: CoreDescriptor, game: Listed?, files: Pair<ConfigFile?, ConfigFile?>): EmulatorPage =
        EmulatorSettings.page(tag, d, coreOptions.table(d.id), defaults(d), files.first, files.second, forGame = game != null, vulkanFailed = vulkanFailed(d.id), secondScreen = secondScreen())

    private suspend fun readPage(tag: String, d: CoreDescriptor, game: Listed?): EmulatorPage =
        build(tag, d, game, configs.read(tag, d.id, game?.game?.fileName))

    /** Reads and shows [tag]'s page when [still] holds (the user has not moved on meanwhile); [opened] runs just before it shows. */
    private fun openPage(tag: String, d: CoreDescriptor, game: Listed?, still: () -> Boolean, opened: () -> Unit) {
        scope.launch {
            val page = lock.withLock {
                try {
                    withContext(work) { readPage(tag, d, game) }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    error.value = "Could not read the settings"
                    null
                }
            } ?: return@launch
            if (!still()) return@launch
            opened()
            val rows = EmulatorRows.build(page, null)
            screen.value = Screen.EmulatorOptions(page, rows, null, game)
            selection.value = ListSelection(EmulatorRows.firstSelectable(rows), rows.size)
        }
    }

    private fun onPageButton(s: Screen.EmulatorOptions, button: NxButton) {
        val row = s.rows.getOrNull(selection.value.index)
        when (button) {
            NxButton.UP -> selection.value = ListSelection(EmulatorRows.step(s.rows, selection.value.index, -1), s.rows.size)
            NxButton.DOWN -> selection.value = ListSelection(EmulatorRows.step(s.rows, selection.value.index, 1), s.rows.size)
            NxButton.LEFT -> change(s, row) { page, key -> nextValue(page, key, -1) }
            NxButton.RIGHT -> change(s, row) { page, key -> nextValue(page, key, 1) }
            NxButton.A -> when (row) {
                is EmulatorRow.Console, is EmulatorRow.Core -> change(s, row) { page, key -> nextValue(page, key, 1) }
                is EmulatorRow.Category -> {
                    val rows = EmulatorRows.build(s.page, row.key)
                    screen.value = s.copy(rows = rows, category = row.key)
                    selection.value = ListSelection(0, rows.size)
                }
                EmulatorRow.UseEmulatorSettings -> useEmulatorSettings(s)
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
        is EmulatorRow.Console -> row.row.option.let { o -> Triple(o.label, o.values.map(o::labelOf), o.values.indexOf(row.row.value)) }
        is EmulatorRow.Core -> row.option.let { o ->
            val values = CoreOptionFilter.visibleValues(o)
            Triple(CoreOptionFilter.rowLabel(o, row.inCategory), values.map { v -> CoreOptionFilter.valueLabel(o.copy(value = v)) }, values.indexOf(o.value))
        }
        else -> null
    }

    /** A touch chooser's pick [index] for the selected row ([choices]): that value written, as stepping there would. */
    fun choose(index: Int) {
        val s = screen.value as? Screen.EmulatorOptions ?: return
        val row = s.rows.getOrNull(selection.value.index)
        val (_, labels, current) = choices(s) ?: return
        if (index !in labels.indices || index == current) return
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
            is EmulatorRow.Console -> row.row.option.key
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
                    try {
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
                            build(tag, d, game, files.first to CoreConfig.parse(text))
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Throwable) {
                        error.value = "Could not save the setting"
                        runCatching { readPage(tag, d, game) }.getOrNull()
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
                val page = withContext(work) { runCatching { readPage(s.page.tag, d, s.game) }.getOrNull() } ?: return@withLock
                val cur = screen.value as? Screen.EmulatorOptions
                if (cur != null && samePage(cur, s) && EmulatorRows.build(page, null).isEmpty()) back(cur.copy(category = null))
                else show(s, page)
            }
        }
    }

    /** A on "Use emulator settings": deletes the game file and shows the page as the console file has it. */
    private fun useEmulatorSettings(s: Screen.EmulatorOptions) {
        val game = s.game ?: return
        val d = descriptorOf(s) ?: return
        scope.launch {
            lock.withLock {
                val page = withContext(work) {
                    try {
                        configs.deleteGame(s.page.tag, d.id, game.game.fileName)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Throwable) {
                        error.value = "Could not save the setting"
                    }
                    runCatching { readPage(s.page.tag, d, game) }.getOrNull()
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
            return if (i < 0) (if (delta >= 0) values.first() else values.last()) else values[((i + delta) % values.size + values.size) % values.size]
        }
        return page.core?.firstOrNull { it.key == key }?.let { CoreOptionFilter.cycle(it, delta) }
    }

    /** Shows [page] in place of [was]'s when that page is still open, the selection on the same row where it still is. */
    private fun show(was: Screen.EmulatorOptions, page: EmulatorPage) {
        val cur = screen.value as? Screen.EmulatorOptions ?: return
        if (!samePage(cur, was)) return
        val previous = cur.rows.getOrNull(selection.value.index)
        val rows = EmulatorRows.build(page, cur.category)
        screen.value = cur.copy(page = page, rows = rows)
        val at = EmulatorRows.indexOfSame(rows, previous)
            ?: selection.value.index.takeIf { rows.getOrNull(it)?.selectable == true }
            ?: EmulatorRows.firstSelectable(rows)
        selection.value = ListSelection(at, rows.size)
    }
}
