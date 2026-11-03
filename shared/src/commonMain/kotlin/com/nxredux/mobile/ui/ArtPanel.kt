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
 * something to say, and [question] after a box or wheel pick that Layouts does not show.
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
 * art), then the previews load, [PREVIEWS] at once (a dead link's tile leaves the panel). A or a tap picks a tile ([ArtOptionsPort.pickOption]; [onPicked]
 * with the game's path after one went through), Search again searches every source again; B closes. A box or wheel
 * pick that Layouts → Backdrop art (or List art) does not show asks [LayoutQuestion] in the panel: Switch writes the
 * settings, Keep (or B) leaves them.
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
        _panel.value = p.copy(phase = ArtPanelPhase.LOADING, sections = emptyList(), focus = 0, message = null, question = null)
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
            val slot = tile.option.slot.fileSlot
            _panel.update { now ->
                when {
                    now?.gamePath != path -> now
                    ok -> now.copy(
                        currentKeys = now.currentKeys + (slot to tile.option.key), message = messageFor(now.tiles.size, note),
                        question = layoutQuestionFor(slot, settings.backdropArt, settings.gameListArt),
                    )
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
            val sections = artSections(result.options.distinctBy { it.key }.map { ArtTile(it, artTileLabel(it), image = null, failed = false) })
            val tiles = sections.flatMap { it.tiles }
            // The first picture in use (sections in order) has the focus.
            val current = tiles.indexOfFirst { result.currentKeys[it.option.slot.fileSlot] == it.option.key }
            note = result.note
            _panel.update {
                it?.takeIf { p -> p.gamePath == path }?.copy(
                    phase = ArtPanelPhase.READY, sections = sections, currentKeys = result.currentKeys,
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
                        else -> now.copy(sections = now.sections.map { s ->
                            s.copy(tiles = s.tiles.map { if (it.option.key == t.option.key) it.copy(image = got.ref, failed = got.ref == null) else it })
                        })
                    }
                }
            }
        }
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
        val message = if (p.message == messageFor(p.tiles.size, note)) messageFor(count, note) else p.message
        return p.copy(sections = sections, focus = focus, message = message)
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
