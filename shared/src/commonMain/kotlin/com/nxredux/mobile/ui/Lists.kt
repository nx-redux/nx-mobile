package com.nxredux.mobile.ui

import com.nxredux.mobile.library.GameEntry
import com.nxredux.mobile.library.matches
import com.nxredux.mobile.library.path
import com.nxredux.mobile.library.SystemFolder
import com.nxredux.mobile.runner.CoreRegistry
import com.nxredux.mobile.storage.GamePath
import com.nxredux.mobile.storage.GameRef
import com.nxredux.mobile.storage.ResumeInfo
import com.nxredux.mobile.ui.nx.HomeShelf

/** What a game list shows: one console, the recents file, one collection, the hidden games, or a search's matches. */
sealed interface ListSource {
    /**
     * A console: every playable system sharing one display name ("Super Nintendo ES" over SFC and SUPA, as NX Redux
     * collates sibling folders), or Unassigned alone. Each row still launches under its own tag.
     */
    data class Console(val systems: List<SystemFolder>) : ListSource {
        constructor(system: SystemFolder) : this(listOf(system))
        /** The first system: its display name is the console's, its unassigned flag the list's. */
        val system: SystemFolder get() = systems.first()
    }
    data object Recent : ListSource
    data class Collection(val name: String) : ListSource
    data object Hidden : ListSource
    /** START's search: every playable game matching [query] ([GameSearch]), across consoles. */
    data class Search(val query: String) : ListSource
    /** START's (or the Search icon's) search inside a game list: [parent]'s games matching [query]; B goes back to [parent]. */
    data class Filtered(val parent: ListSource, val query: String) : ListSource
}

val ListSource.title: String
    get() = when (this) {
        is ListSource.Console -> system.displayName
        ListSource.Recent -> "Recently Played"
        is ListSource.Collection -> name
        ListSource.Hidden -> "Hidden games"
        is ListSource.Search -> "Search: $query"
        is ListSource.Filtered -> "${parent.title}: $query"
    }

/** The list a [ListSource.Filtered] search was made in; any other source itself. */
val ListSource.base: ListSource get() = (this as? ListSource.Filtered)?.parent ?: this

/** A list START (or the Search icon) searches inside: not Hidden, nor a search's own results (START's or this one's parent). */
val ListSource.searchable: Boolean get() = base != ListSource.Hidden && base !is ListSource.Search

/**
 * A game list's title row: the list's own name ([title]: "ColecoVision", "RPGs", "Recently Played"), not led by the tab
 * it was opened from.
 */
fun listTitle(source: ListSource): String = source.title

/**
 * A game with the system it launches under: mixed lists (recents, collections, hidden, a merged console) need it per row.
 * [label]: the row's text when it differs from the game's shown name (a name shared across a merged console's tags).
 */
data class Listed(val system: SystemFolder, val game: GameEntry, val label: String? = null) {
    val shown: String get() = label ?: game.shownName
}

/** One item of a main menu tab ([MenuTab]). */
sealed interface MainRow {
    /** A pinned game (Home's Pinned rows): A launches it. */
    data class Pin(val entry: Listed) : MainRow
    /** Home on the large face: a game on a discovery shelf ([HomeShelf]); A launches it, as a pin. */
    /** A shelf's game; [tag] its own badge (Most played: its play time), else the shelf's reason where it has one ([homeShelfBadge]). */
    data class Shelf(val entry: Listed, val shelf: HomeShelf, val tag: String? = null) : MainRow
    /** One console row: the systems of one display name, in library order (see [ListSource.Console]). */
    data class System(val systems: List<SystemFolder>) : MainRow {
        constructor(system: SystemFolder) : this(listOf(system))
        val system: SystemFolder get() = systems.first()
    }
    /** A collection (Collections tab): A opens its list. */
    data class Collection(val name: String, val count: Int? = null) : MainRow   // count: the games its list shows; null when its file would not read
    /** A tool (Tools tab): A opens it. */
    data class Tool(val row: ToolRow) : MainRow
    /** Home: the last played game (resumable or not). */
    data class Continue(val entry: Listed, val resume: ResumeInfo) : MainRow
    /** Home: a pinned tool. */
    data class PinnedTool(val row: ToolRow) : MainRow
    /** Home's "+N" square: [count] pinned tools that don't fit the top section; A opens the Tools tab. */
    data class MoreTools(val count: Int) : MainRow
    /** Home, fresh install: "Pick a game" (opens Consoles). */
    data object PickAGame : MainRow
}

/** Whose background a console row shows: the system on its console's default tag (SFC over SUPA), else the first. */
internal val MainRow.System.backdrop: SystemFolder
    get() = systems.firstOrNull { CoreRegistry.consoleOf(it.tag)?.defaultTag == it.tag } ?: system

/** [path]'s game in [systems] as a list opens it: the first system with a core that holds it; null when none does. */
internal fun resolveIn(systems: List<SystemFolder>, path: String): Listed? {
    val ref = GamePath.split(path) ?: return null
    return systems.firstNotNullOfOrNull { s -> if (s.core == null) null else s.games.firstOrNull { it.matches(ref) }?.let { Listed(s, it) } }
        // A line for an extra copy the scan hid behind a home game opens that game.
        ?: systems.firstNotNullOfOrNull { s -> if (s.core == null) null else s.games.firstOrNull { ref in it.hiddenCopies }?.let { Listed(s, it) } }
}

/**
 * [paths] resolved ([resolveIn]) with each game once, at its first line's place: Recents and pins, where a
 * hidden copy's line and its home game's line both open the same game.
 */
internal fun resolveDistinct(systems: List<SystemFolder>, paths: List<String>): List<Listed> =
    paths.mapNotNull { resolveIn(systems, it) }.distinctBy { it.game.path }

/** Every game [resolveIn] can find in [systems], by its path's ref (the first system winning, as there): one pass, then lookups. */
internal fun playableLookup(systems: List<SystemFolder>): Map<GameRef, Listed> = HashMap<GameRef, Listed>().also { map ->
    systems.forEach { s -> if (s.core != null) s.games.forEach { g -> map.getOrPut(GameRef(g.sourceId, g.relativeDir, g.fileName)) { Listed(s, g) } } }
    // Then the hidden copies' refs ([resolveIn]'s fallback): a listed game's own ref always wins.
    systems.forEach { s -> if (s.core != null) s.games.forEach { g -> g.hiddenCopies.forEach { r -> map.getOrPut(r) { Listed(s, g) } } } }
}

/** [path]'s game in [lookup] ([playableLookup]): what [resolveIn] over the same systems gives, in one lookup. */
internal fun resolveIn(lookup: Map<GameRef, Listed>, path: String): Listed? = GamePath.split(path)?.let(lookup::get)

/** [resolveDistinct] through [lookup] ([playableLookup]). */
internal fun resolveDistinct(lookup: Map<GameRef, Listed>, paths: List<String>): List<Listed> =
    paths.mapNotNull { resolveIn(lookup, it) }.distinctBy { it.game.path }

/**
 * Every game in [systems] by its ref, the first system holding it winning, coreless ones and Unassigned included and no
 * hidden copies: what Hidden's lines resolve against (every hidden game can be unhidden).
 */
internal fun gameLookup(systems: List<SystemFolder>): Map<GameRef, Listed> = HashMap<GameRef, Listed>().also { map ->
    systems.forEach { s -> s.games.forEach { g -> map.getOrPut(GameRef(g.sourceId, g.relativeDir, g.fileName)) { Listed(s, g) } } }
}

/** How many of a collection's [lines] resolve in [lookup] ([playableLookup]): the games its opened list shows. */
internal fun resolvedCount(lines: List<String>, lookup: Map<GameRef, Listed>): Int = lines.count { l -> GamePath.split(l)?.let(lookup::containsKey) == true }
