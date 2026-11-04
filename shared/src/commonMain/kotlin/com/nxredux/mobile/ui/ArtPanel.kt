package com.nxredux.mobile.ui

import com.nxredux.mobile.library.GameEntry
import com.nxredux.mobile.library.path
import com.nxredux.mobile.runner.ContentRef
import com.nxredux.mobile.scraper.ArtOption
import com.nxredux.mobile.scraper.ArtOptionsPort
import com.nxredux.mobile.scraper.ArtSlot
import com.nxredux.mobile.scraper.ItemStatus
import com.nxredux.mobile.scraper.OptionImage
import com.nxredux.mobile.scraper.SourceId
import com.nxredux.mobile.scraper.fileSlot
import com.nxredux.mobile.settings.AppSettings
import com.nxredux.mobile.settings.BackdropArt
import com.nxredux.mobile.settings.GameListArt
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
 * One art kind's part of the panel: its file [slot] (SCREENSHOT, BOX_2D, BOX_3D or WHEEL), its heading [title], its
 * tiles in [columns] per row, each shaped to the art ([aspect]).
 */
data class ArtSection(val slot: ArtSlot, val title: String, val columns: Int, val tiles: List<ArtTile>) {
    /** A tile's picture box, width over height: a screenshot's 4:3, a box's 3:4, a wheel's 2:1. */
    val aspect: Float get() = when (slot) {
        ArtSlot.SCREENSHOT -> 4f / 3f
        ArtSlot.WHEEL -> 2f
        else -> 3f / 4f
    }
}

/** The panel's sections in order: their file slot, heading and columns. */
private val SECTIONS = listOf(
    Triple(ArtSlot.SCREENSHOT, "Screenshot", 2),
    Triple(ArtSlot.BOX_2D, "2D box", 3),
    Triple(ArtSlot.BOX_3D, "3D box", 3),
    Triple(ArtSlot.WHEEL, "Wheel", 2),
)

/** [tiles] in their sections by file slot ([ArtSlot.fileSlot]: a grid is a 2D box, a hero a screenshot), each as found; empty ones left out. */
internal fun artSections(tiles: List<ArtTile>): List<ArtSection> = SECTIONS.mapNotNull { (slot, title, columns) ->
    tiles.filter { it.option.slot.fileSlot == slot }.takeIf { it.isNotEmpty() }?.let { ArtSection(slot, title, columns, it) }
}

/**
 * After a box or wheel pick: offers to switch Layouts → Backdrop art to the picked [kind], and List art too when
 * [alsoListArt]; [yesFocused] while Switch is lit (where it opens), else Keep.
 */
data class LayoutQuestion(val kind: BackdropArt, val alsoListArt: Boolean, val yesFocused: Boolean)

/** The Backdrop art kind a pick in file slot [slot] shows as; null for a screenshot (or any other). */
private fun backdropKindOf(slot: ArtSlot): BackdropArt? = when (slot) {
    ArtSlot.BOX_3D -> BackdropArt.BOX3D
    ArtSlot.BOX_2D -> BackdropArt.BOX2D
    ArtSlot.WHEEL -> BackdropArt.WHEEL
    else -> null
}

/** Layouts → Backdrop art and List art together, as the screens draw them ([AppState.artLayout]). */
data class ArtLayout(val backdrop: BackdropArt, val list: GameListArt)

/** The List art value showing [kind]. */
private fun listArtOf(kind: BackdropArt): GameListArt = when (kind) {
    BackdropArt.BOX3D -> GameListArt.BOX3D
    BackdropArt.BOX2D -> GameListArt.BOX2D
    BackdropArt.WHEEL -> GameListArt.WHEEL
}

/**
 * The question after a pick in file slot [pickSlot]: null for a screenshot, or when [backdrop] already shows the
 * picked kind and [listArt] is not a box/wheel kind or shows it too. [LayoutQuestion.alsoListArt] when List art is
 * 3D box, 2D box or wheel and not the picked kind.
 */
fun layoutQuestionFor(pickSlot: ArtSlot, backdrop: BackdropArt, listArt: GameListArt): LayoutQuestion? {
    val kind = backdropKindOf(pickSlot.fileSlot) ?: return null
    val listDiffers = listArt in BOXED_LIST_ART && listArt != listArtOf(kind)
    if (backdrop == kind && !listDiffers) return null
    return LayoutQuestion(kind, alsoListArt = listDiffers, yesFocused = true)
}

private val BOXED_LIST_ART = setOf(GameListArt.BOX3D, GameListArt.BOX2D, GameListArt.WHEEL)

/** The question's title and buttons. */
const val LAYOUT_QUESTION_TITLE = "Show this picture?"
const val LAYOUT_QUESTION_YES = "Switch"
const val LAYOUT_QUESTION_NO = "Keep"

/**
 * The question's text with Layouts set to [backdrop] and [listArt]: "Layouts → Backdrop art is set to 3D box art.
 * Switch it to 2D box art?", " List art too." when [LayoutQuestion.alsoListArt]; when Backdrop art already shows the
 * kind, only List art is named.
 */
fun layoutQuestionText(q: LayoutQuestion, backdrop: BackdropArt, listArt: GameListArt): String = when {
    backdrop == q.kind && q.alsoListArt -> "Layouts → List art is set to ${listArt.label}. Switch it to ${listArtOf(q.kind).label}?"
    else -> "Layouts → Backdrop art is set to ${backdrop.label}. Switch it to ${q.kind.label}?" + if (q.alsoListArt) " List art too." else ""
}

/**
 * The d-pad over [sections] from the flat [focus] (a tile, or Search again at the tiles' count): LEFT / RIGHT within a
 * row; DOWN to the tile below in the section (the next row's last when it is shorter), else the next section's first
 * row at the nearest column, else Search again; UP mirrors it (the section above's last row), and from Search again
 * goes to the last tile.
 */
internal fun artPanelMove(sections: List<ArtSection>, focus: Int, button: NxButton): Int {
    val total = sections.sumOf { it.tiles.size }
    if (focus >= total) return if (button == NxButton.UP && total > 0) total - 1 else focus
    var base = 0
    var at = 0
    while (focus >= base + sections[at].tiles.size) { base += sections[at].tiles.size; at++ }
    val sec = sections[at]
    val n = sec.tiles.size
    val cols = sec.columns
    val i = focus - base
    val col = i % cols
    /** The column of [into] nearest this one's middle. */
    fun nearest(into: ArtSection): Int = ((col + 0.5f) / cols * into.columns).toInt().coerceIn(0, into.columns - 1)
    return when (button) {
        NxButton.LEFT -> if (col > 0) focus - 1 else focus
        NxButton.RIGHT -> if (col < cols - 1 && i + 1 < n) focus + 1 else focus
        NxButton.DOWN -> when {
            (i / cols + 1) * cols < n -> base + minOf(i + cols, n - 1)
            at + 1 < sections.size -> base + n + minOf(nearest(sections[at + 1]), sections[at + 1].tiles.size - 1)
            else -> total
        }
        NxButton.UP -> when {
            i >= cols -> focus - cols
            at > 0 -> {
                val prev = sections[at - 1]
                val lastRow = (prev.tiles.size - 1) / prev.columns * prev.columns
                base - prev.tiles.size + minOf(lastRow + nearest(prev), prev.tiles.size - 1)
            }
            else -> focus
        }
        else -> focus
    }
}

/** The game has some art on file: a 3D box, a 2D box, a screenshot or a wheel (Change art, else Fetch art). */
internal fun hasAnyArt(g: GameEntry): Boolean = g.boxartRef != null || g.box2dRef != null || g.screenshotRef != null || g.wheelRef != null

/**
 * The art panel over a game list: the game ([gamePath], [title]), its [sections] in order, [focus] a flat index over
 * their tiles (Search again at the tiles' count), [currentKeys] the option in use per file slot, [message] when there is
 * something to say, and [question] after a box or wheel pick that Layouts does not show. [searching]: the tiles are
 * the ones found so far, the fetch still runs behind them.
 */
data class ArtPanel(
    val gamePath: String,
    val title: String,
    val phase: ArtPanelPhase,
    val sections: List<ArtSection>,
    val focus: Int,
    val currentKeys: Map<ArtSlot, String>,
    val message: String?,
    val question: LayoutQuestion? = null,
    val searching: Boolean = false,
) {
    /** Every tile, section by section: the flat order [focus] counts in. */
    val tiles: List<ArtTile> get() = sections.flatMap { it.tiles }
    /** The focus is on Search again. */
    val onSearchAgain: Boolean get() = focus == sections.sumOf { it.tiles.size }
    /** [t] is the picture in use in its section. */
    fun isCurrent(t: ArtTile): Boolean = currentKeys[t.option.slot.fileSlot] == t.option.key
}

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
 * art), then the previews load, [PREVIEWS] at once (a dead link's tile leaves the panel). While a fetch or search runs,
 * each source's options join the panel as it answers (their previews loading at once), [STILL_LOOKING] showing until
 * the saved list replaces them. A or a tap picks a tile ([ArtOptionsPort.pickOption]; [onPicked]
 * with the game's path after one went through), Search again searches every source again; B closes. A box or wheel
 * pick that Layouts → Backdrop art (or List art) does not show asks [LayoutQuestion] in the panel: Switch writes the
 * settings, Keep (or B) leaves them.
 */
class ArtPanelController(
    private val scope: CoroutineScope,
    private val port: ArtOptionsPort,
    private val settings: AppSettings,
    private val onPicked: (String) -> Unit,
    /** After Switch wrote Layouts → Backdrop art (and List art): the screens follow. */
    private val onLayoutSwitched: () -> Unit = {},
) {
    companion object {
        /** Previews loaded at once, at most. */
        private const val PREVIEWS = 3
        /** The message while tiles show and the fetch still runs behind them. */
        const val STILL_LOOKING = "Still looking…"
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
    /** The picks made over the current load's tiles: they keep their mark over its final list, whenever that was read. */
    private var picksHere: Map<ArtSlot, String> = emptyMap()

    /** Opens the panel for [entry]; [fetchFirst]: Fetch art (the game's art is fetched before the list shows). */
    fun open(entry: Listed, fetchFirst: Boolean) {
        load?.cancel()
        _panel.value = ArtPanel(
            entry.game.path, entry.shown, if (fetchFirst) ArtPanelPhase.FETCHING else ArtPanelPhase.LOADING,
            sections = emptyList(), focus = 0, currentKeys = emptyMap(), message = null,
        )
        start(entry.game.path, fetchFirst, searchAgain = false)
    }

    /** Closes the panel; its load stops (a pick already going through still finishes). */
    fun close() {
        load?.cancel(); load = null
        _panel.value = null
    }

    /**
     * A pad press while the panel is open: with the question showing, LEFT / RIGHT light Switch / Keep, A answers, B
     * keeps; else the d-pad moves over the sections then Search again ([artPanelMove]), A acts, B closes.
     */
    fun onButton(b: NxButton) {
        val p = _panel.value ?: return
        p.question?.let { q ->
            when (b) {
                NxButton.LEFT -> if (!q.yesFocused) _panel.value = p.copy(question = q.copy(yesFocused = true))
                NxButton.RIGHT -> if (q.yesFocused) _panel.value = p.copy(question = q.copy(yesFocused = false))
                NxButton.A -> answer(q.yesFocused)
                NxButton.B -> answer(false)
                else -> {}
            }
            return
        }
        if (b == NxButton.B) return close()
        if (p.phase != ArtPanelPhase.READY) return
        if (b == NxButton.A) return act(p, p.focus)
        val to = artPanelMove(p.sections, p.focus, b)
        if (to != p.focus) _panel.value = p.copy(focus = to)
    }

    /** The question's answer: [switch] sets Backdrop art (and List art when asked) to the picked kind; either way it goes. */
    fun answer(switch: Boolean) {
        val p = _panel.value ?: return
        val q = p.question ?: return
        if (switch) {
            settings.backdropArt = q.kind
            if (q.alsoListArt) settings.gameListArt = listArtOf(q.kind)
            onLayoutSwitched()
        }
        _panel.value = p.copy(question = null)
    }

    /** A tap on tile [index] (or Search again at the tiles' count): focuses it and acts as A; nothing while the question shows. */
    fun tap(index: Int) {
        val p = _panel.value ?: return
        if (p.phase != ArtPanelPhase.READY || p.question != null || index !in 0..p.tiles.size) return
        val now = p.copy(focus = index)
        _panel.value = now
        act(now, index)
    }

    private fun act(p: ArtPanel, index: Int) {
        val tiles = p.tiles
        if (index == tiles.size) return searchAgain(p)
        pick(p, tiles.getOrNull(index) ?: return)
    }

    private fun searchAgain(p: ArtPanel) {
        load?.cancel()
        _panel.value = p.copy(phase = ArtPanelPhase.LOADING, sections = emptyList(), focus = 0, message = null, question = null, searching = false)
        start(p.gamePath, fetchFirst = false, searchAgain = true)
    }

    private fun pick(p: ArtPanel, tile: ArtTile) {
        if (picking?.isActive == true) return   // the current one too: picking it protects it from later fetches
        val path = p.gamePath
        val shown = load   // the list the tile is from: a pick finishing after Search again or a reopen marks nothing
        picking = scope.launch {
            val ok = try {
                port.pickOption(path, tile.option)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                false
            }
            if (ok) onPicked(path)
            val slot = tile.option.slot.fileSlot
            // The same load still running (tiles shown while the fetch goes on) marks it too.
            if (ok && load === shown && _panel.value?.gamePath == path) picksHere = picksHere + (slot to tile.option.key)
            _panel.update { now ->
                when {
                    now?.gamePath != path || load !== shown || now.phase != ArtPanelPhase.READY -> now
                    ok -> now.copy(
                        currentKeys = now.currentKeys + (slot to tile.option.key), message = listMessage(now, now.tiles.size),
                        question = layoutQuestionFor(slot, settings.backdropArt, settings.gameListArt),
                    )
                    else -> now.copy(message = "Couldn't use that picture")
                }
            }
        }
    }

    /**
     * The fetch (when [fetchFirst]), then the list, then each preview. The options a fetch or search finds on the way
     * show as they come ([withTiles], [ArtPanel.searching]); the final list then replaces them, a dead link's tile
     * not coming back and a pick made meanwhile keeping its mark.
     */
    private fun start(path: String, fetchFirst: Boolean, searchAgain: Boolean) {
        picksHere = emptyMap()
        load = scope.launch {
            val loading = this
            val gate = Semaphore(PREVIEWS)
            /** The options whose preview has been asked for (each once per load). */
            val previewing = HashSet<String>()
            /** The dead links dropped from this load's tiles: a list read later must not show them again. */
            val dropped = MutableStateFlow(emptySet<String>())
            fun preview(o: ArtOption) {
                if (!previewing.add(o.key)) return
                loading.launch {
                    val got = gate.withPermit {
                        try { port.optionImage(path, o) } catch (e: CancellationException) { throw e } catch (_: Throwable) { OptionImage(null) }
                    }
                    if (got.dropped) dropped.update { it + o.key }
                    _panel.update { now ->
                        when {
                            now?.gamePath != path -> now
                            got.dropped -> without(now, o.key)   // a dead link: its tile goes
                            else -> now.copy(sections = now.sections.map { s ->
                                s.copy(tiles = s.tiles.map { if (it.option.key == o.key) it.copy(image = got.ref, failed = got.ref == null) else it })
                            })
                        }
                    }
                }
            }
            val found: suspend (List<ArtOption>) -> Unit = { options ->
                val shown = options.distinctBy { it.key }.filter { it.key !in dropped.value }
                if (shown.isNotEmpty()) {
                    _panel.update { now ->
                        now?.takeIf { it.gamePath == path }?.let { withTiles(it, shown).copy(phase = ArtPanelPhase.READY, searching = true, message = STILL_LOOKING) } ?: now
                    }
                    shown.forEach(::preview)
                }
            }
            val replace = settings.artReplaceExisting
            val result = try {
                var fetchNote: String? = null
                if (fetchFirst) {
                    val r = port.fetchGame(path, replace, wide = true, found)
                    if (r.status == ItemStatus.ERROR) fetchNote = fetchResultText(r)
                    _panel.update { it?.takeIf { p -> p.gamePath == path && p.phase == ArtPanelPhase.FETCHING }?.copy(phase = ArtPanelPhase.LOADING) ?: it }
                }
                // After its own Fetch art, the list as that fetch left it: a wide pass cut short is not run again here.
                port.artOptions(path, replace, searchAgain, autoSearch = !fetchFirst, found).let { if (it.note == null && fetchNote != null) it.copy(note = fetchNote) else it }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                _panel.update { it?.takeIf { p -> p.gamePath == path }?.copy(phase = ArtPanelPhase.READY, message = "Couldn't read the pictures", searching = false) ?: it }
                return@launch
            }
            note = result.note
            // One tile per key: the grid's item keys and the preview updates go by it.
            val options = result.options.distinctBy { it.key }.filter { it.key !in dropped.value }
            _panel.update {
                it?.takeIf { p -> p.gamePath == path }?.let { p ->
                    val early = p.phase == ArtPanelPhase.READY && p.searching
                    val q = withTiles(p, options)
                    val currentKeys = result.currentKeys + picksHere
                    // The first picture in use (sections in order) has the focus; tiles shown early keep it where it is.
                    val current = q.tiles.indexOfFirst { t -> currentKeys[t.option.slot.fileSlot] == t.option.key }
                    q.copy(
                        phase = ArtPanelPhase.READY, searching = false, currentKeys = currentKeys,
                        focus = if (early) q.focus else if (current >= 0) current else 0, message = messageFor(q.tiles.size, result.note),
                    )
                } ?: it
            }
            options.forEach(::preview)
        }
    }

    /**
     * [p] with the tiles for [options] in their order (one already shown keeps its preview, one not listed goes), in
     * their sections. A shown panel's focus stays on the same tile (or Search again, or the nearest place when its
     * tile went); one not shown yet starts on the first.
     */
    private fun withTiles(p: ArtPanel, options: List<ArtOption>): ArtPanel {
        val have = p.tiles.associateBy { it.option.key }
        val sections = artSections(options.map { have[it.key] ?: ArtTile(it, artTileLabel(it), image = null, failed = false) })
        val tiles = sections.flatMap { it.tiles }
        val focus = when {
            p.phase != ArtPanelPhase.READY -> 0
            p.onSearchAgain -> tiles.size
            else -> tiles.indexOfFirst { it.option.key == p.tiles[p.focus].option.key }.takeIf { it >= 0 } ?: minOf(p.focus, tiles.size)
        }
        return p.copy(sections = sections, focus = focus)
    }

    /** [p] without the tile for [key] (its section too when it was the last): the focus stays on the same tile (or Search again), the count's message follows. */
    private fun without(p: ArtPanel, key: String): ArtPanel {
        val i = p.tiles.indexOfFirst { it.option.key == key }
        if (i < 0) return p
        val sections = p.sections.map { s -> s.copy(tiles = s.tiles.filter { it.option.key != key }) }.filter { it.tiles.isNotEmpty() }
        val count = sections.sumOf { it.tiles.size }
        val focus = when {
            p.focus > i -> p.focus - 1
            p.focus == i && i == count && i > 0 -> i - 1   // it was the last tile: the one before it
            else -> p.focus
        }
        val message = if (p.message == listMessage(p, p.tiles.size)) listMessage(p, count) else p.message
        return p.copy(sections = sections, focus = focus, message = message)
    }

    /** [p]'s message for [count] tiles: [STILL_LOOKING] while it is [ArtPanel.searching], else [messageFor]. */
    private fun listMessage(p: ArtPanel, count: Int): String? = if (p.searching) STILL_LOOKING else messageFor(count, note)

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
