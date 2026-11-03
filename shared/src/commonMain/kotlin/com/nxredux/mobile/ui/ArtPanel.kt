package com.nxredux.mobile.ui

import com.nxredux.mobile.library.path
import com.nxredux.mobile.runner.ContentRef
import com.nxredux.mobile.scraper.ArtOption
import com.nxredux.mobile.scraper.ArtOptionsPort
import com.nxredux.mobile.scraper.ArtSlot
import com.nxredux.mobile.scraper.ItemStatus
import com.nxredux.mobile.scraper.OptionImage
import com.nxredux.mobile.scraper.SourceId
import com.nxredux.mobile.settings.AppSettings
import com.nxredux.mobile.ui.nx.NxButton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/** The game menu's art item: none (no source knows the game), Fetch art (no art yet) or Change art. */
enum class ArtMenuItem { NONE, FETCH, CHANGE }

/** The art panel's step: the game's art being fetched (Fetch art), its options being read or searched, or the options. */
enum class ArtPanelPhase { FETCHING, LOADING, READY }

/** One option in the panel: its [label] (source and note), its preview once loaded, [failed] when it could not be. */
data class ArtTile(val option: ArtOption, val label: String, val image: ContentRef?, val failed: Boolean)

/**
 * The art panel over a game list: the game ([gamePath], [title]), its [tiles] in order, [focus] on a tile or on
 * Search again (index `tiles.size`), [currentKey] the option in use now, and [message] when there is something to say.
 */
data class ArtPanel(
    val gamePath: String,
    val title: String,
    val phase: ArtPanelPhase,
    val tiles: List<ArtTile>,
    val focus: Int,
    val currentKey: String?,
    val message: String?,
) {
    /** The focus is on Search again. */
    val onSearchAgain: Boolean get() = focus == tiles.size
}

/** The panel's grid columns (the d-pad moves within a row, then by rows). */
const val ART_PANEL_COLUMNS = 2

/** The order of the slots in the panel: screenshots, then grids, then heroes. */
private val SLOT_ORDER = listOf(ArtSlot.SCREENSHOT, ArtSlot.GRID, ArtSlot.HERO)

/** A tile's label: the source's name, then the slot for SteamGridDB or the source's note ("ScreenScraper · us"). */
internal fun artTileLabel(o: ArtOption): String {
    val name = when (o.source) {
        SourceId.SS -> "ScreenScraper"
        SourceId.TGDB -> "TheGamesDB"
        SourceId.LIBRETRO -> "libretro"
        SourceId.SGDB -> "SteamGridDB"
        else -> o.source
    }
    val extra = when {
        o.source == SourceId.SGDB && o.slot == ArtSlot.GRID -> "Grid"
        o.source == SourceId.SGDB && o.slot == ArtSlot.HERO -> "Hero"
        else -> o.note?.takeIf { it.isNotBlank() }
    }
    return if (extra == null) name else "$name · $extra"
}

/**
 * The game menu's Fetch art and Change art panel: [open] fetches first (Fetch art) or reads the saved options (Change
 * art), then the previews load, [PREVIEWS] at once (a dead link's tile leaves the panel). A or a tap picks a tile ([ArtOptionsPort.pickOption]; [onPicked]
 * with the game's path after one went through), Search again searches every source again; B closes.
 */
class ArtPanelController(
    private val scope: CoroutineScope,
    private val port: ArtOptionsPort,
    private val settings: AppSettings,
    private val onPicked: (String) -> Unit,
) {
    private companion object {
        /** Previews loaded at once, at most. */
        const val PREVIEWS = 3
    }

    private val _panel = MutableStateFlow<ArtPanel?>(null)
    /** The open panel; null while closed. */
    val panel: StateFlow<ArtPanel?> = _panel.asStateFlow()
    /** The fetch, the list and its previews; cancelled on close and on Search again. */
    private var load: Job? = null
    /** A pick being written; one at a time. */
    private var picking: Job? = null
    /** The shown list's note from the sources (why some are missing), kept under a pick's message. */
    private var note: String? = null

    /** Opens the panel for [entry]; [fetchFirst]: Fetch art (the game's art is fetched before the list shows). */
    fun open(entry: Listed, fetchFirst: Boolean) {
        load?.cancel()
        _panel.value = ArtPanel(
            entry.game.path, entry.shown, if (fetchFirst) ArtPanelPhase.FETCHING else ArtPanelPhase.LOADING,
            tiles = emptyList(), focus = 0, currentKey = null, message = null,
        )
        start(entry.game.path, fetchFirst, searchAgain = false)
    }

    /** Closes the panel; its load stops (a pick already going through still finishes). */
    fun close() {
        load?.cancel(); load = null
        _panel.value = null
    }

    /** A pad press while the panel is open: the d-pad moves over the grid then Search again, A acts, B closes. */
    fun onButton(b: NxButton) {
        val p = _panel.value ?: return
        if (b == NxButton.B) return close()
        if (p.phase != ArtPanelPhase.READY) return
        val n = p.tiles.size
        val f = p.focus
        val row = f / ART_PANEL_COLUMNS
        val lastRow = (n - 1) / ART_PANEL_COLUMNS
        val to = when (b) {
            NxButton.LEFT -> if (f < n && f % ART_PANEL_COLUMNS > 0) f - 1 else f
            NxButton.RIGHT -> if (f < n && f % ART_PANEL_COLUMNS < ART_PANEL_COLUMNS - 1 && f + 1 < n) f + 1 else f
            NxButton.UP -> when {
                n == 0 -> f
                f == n -> n - 1                              // Search again: the last tile
                row > 0 -> f - ART_PANEL_COLUMNS
                else -> f
            }
            NxButton.DOWN -> when {
                f == n -> f
                row < lastRow -> minOf(f + ART_PANEL_COLUMNS, n - 1)   // the next row's tile below, or its last
                else -> n                                    // Search again
            }
            NxButton.A -> { act(p, f); return }
            else -> return
        }
        if (to != f) _panel.value = p.copy(focus = to)
    }

    /** A tap on tile [index] (or Search again at `tiles.size`): focuses it and acts as A. */
    fun tap(index: Int) {
        val p = _panel.value ?: return
        if (p.phase != ArtPanelPhase.READY || index !in 0..p.tiles.size) return
        val now = p.copy(focus = index)
        _panel.value = now
        act(now, index)
    }

    private fun act(p: ArtPanel, index: Int) {
        if (index == p.tiles.size) return searchAgain(p)
        pick(p, p.tiles.getOrNull(index) ?: return)
    }

    private fun searchAgain(p: ArtPanel) {
        load?.cancel()
        _panel.value = p.copy(phase = ArtPanelPhase.LOADING, tiles = emptyList(), focus = 0, message = null)
        start(p.gamePath, fetchFirst = false, searchAgain = true)
    }

    private fun pick(p: ArtPanel, tile: ArtTile) {
        if (picking?.isActive == true) return   // the current one too: picking it protects it from later fetches
        val path = p.gamePath
        picking = scope.launch {
            val ok = try {
                port.pickOption(path, tile.option)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                false
            }
            if (ok) onPicked(path)
            _panel.update { now ->
                when {
                    now?.gamePath != path -> now
                    ok -> now.copy(currentKey = tile.option.key, message = messageFor(now.tiles.size, note))
                    else -> now.copy(message = "Couldn't use that picture")
                }
            }
        }
    }

    /** The fetch (when [fetchFirst]), then the list, then each preview. */
    private fun start(path: String, fetchFirst: Boolean, searchAgain: Boolean) {
        load = scope.launch {
            val replace = settings.artReplaceExisting
            val result = try {
                var fetchNote: String? = null
                if (fetchFirst) {
                    val r = port.fetchGame(path, replace, wide = true)
                    if (r.status == ItemStatus.ERROR) fetchNote = fetchResultText(r)
                    _panel.update { it?.takeIf { p -> p.gamePath == path }?.copy(phase = ArtPanelPhase.LOADING) ?: it }
                }
                // After its own Fetch art, the list as that fetch left it: a wide pass cut short is not run again here.
                port.artOptions(path, replace, searchAgain, autoSearch = !fetchFirst).let { if (it.note == null && fetchNote != null) it.copy(note = fetchNote) else it }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                _panel.update { it?.takeIf { p -> p.gamePath == path }?.copy(phase = ArtPanelPhase.READY, message = "Couldn't read the pictures") ?: it }
                return@launch
            }
            // One tile per key: the grid's item keys and the preview updates go by it.
            val tiles = result.options.filter { it.slot in SLOT_ORDER }.distinctBy { it.key }.sortedBy { SLOT_ORDER.indexOf(it.slot) }
                .map { ArtTile(it, artTileLabel(it), image = null, failed = false) }
            // The screenshot's current one is marked (the panel's sections come later).
            val currentKey = result.currentKeys[ArtSlot.SCREENSHOT]
            val current = tiles.indexOfFirst { it.option.key == currentKey }
            note = result.note
            _panel.update {
                it?.takeIf { p -> p.gamePath == path }?.copy(
                    phase = ArtPanelPhase.READY, tiles = tiles, currentKey = currentKey,
                    focus = if (current >= 0) current else 0, message = messageFor(tiles.size, result.note),
                ) ?: it
            }
            val gate = Semaphore(PREVIEWS)
            for (t in tiles) launch {
                val got = gate.withPermit {
                    try { port.optionImage(path, t.option) } catch (e: CancellationException) { throw e } catch (_: Throwable) { OptionImage(null) }
                }
                _panel.update { now ->
                    when {
                        now?.gamePath != path -> now
                        got.dropped -> without(now, t.option.key)   // a dead link: its tile goes
                        else -> now.copy(tiles = now.tiles.map { if (it.option.key == t.option.key) it.copy(image = got.ref, failed = got.ref == null) else it })
                    }
                }
            }
        }
    }

    /** [p] without the tile for [key]: the focus stays on the same tile (or Search again), the count's message follows. */
    private fun without(p: ArtPanel, key: String): ArtPanel {
        val i = p.tiles.indexOfFirst { it.option.key == key }
        if (i < 0) return p
        val tiles = p.tiles.filterIndexed { j, _ -> j != i }
        val focus = when {
            p.focus > i -> p.focus - 1
            p.focus == i && i == tiles.size && i > 0 -> i - 1   // it was the last tile: the one before it
            else -> p.focus
        }
        val message = if (p.message == messageFor(p.tiles.size, note)) messageFor(tiles.size, note) else p.message
        return p.copy(tiles = tiles, focus = focus, message = message)
    }

    /** None found, only one, or the sources' note (why some are missing). */
    private fun messageFor(count: Int, note: String?): String? {
        val base = when (count) {
            0 -> "No pictures found"
            1 -> "Only one picture found"
            else -> null
        }
        return listOfNotNull(base, note).joinToString(" · ").ifEmpty { null }
    }
}
