package com.nxredux.mobile.scraper

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/** The slots whose candidate images are kept for Change art: every hit kind (the mix too: ScreenScraper's own). */
val OPTION_SLOTS: Set<ArtSlot> = ArtSlot.entries.toSet()

/**
 * One candidate picture a source returned for a game: its [slot] (the hit kind), link, [source] id, the source's ids
 * and [note], and [thumb], a small version's link when the source offers one (ScreenScraper's both without its login).
 */
@Serializable
data class ArtOption(
    val slot: ArtSlot,
    val url: String,
    val source: String,
    val gameId: String? = null,
    val assetId: String? = null,
    val note: String? = null,
    val thumb: String? = null,
) {
    /** Identifies the option within a game: the source, the slot and its asset id (or the link). */
    val key: String get() = "$source/${slot.name}/${assetId ?: url}"
    /** Where the picture came from, as the written-art log records it. */
    val origin: ArtOrigin get() = ArtOrigin(source, gameId, assetId)
}

/**
 * A game's saved options, in the order found; [wide] = the wide pass ran; [picks] = the picked option's [ArtOption.key]
 * per file slot ([ArtSlot.fileSlot]: SCREENSHOT, BOX_2D, BOX_3D, WHEEL, MIX), each protected from fetches.
 */
@Serializable
data class GameOptions(val options: List<ArtOption> = emptyList(), val wide: Boolean = false, val picks: Map<ArtSlot, String> = emptyMap())

/**
 * The per-game option links, one JSON file in app-private storage ([ArtworkHost.writePrivate]), loaded once and saved
 * whole on every change. An absent file is empty; a failed read ([ArtworkHost.readPrivate] throws) is neither cached
 * nor saved over: the call fails and the next one reads again.
 */
class ArtOptionStore(private val host: ArtworkHost, private val name: String = FILE) {
    companion object {
        const val FILE = "art_options2.json"
    }

    private val mutex = Mutex()
    private val BAD_NAME = name.removeSuffix(".json") + ".bad.json"
    private var games: MutableMap<String, GameOptions>? = null
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = MapSerializer(String.serializer(), GameOptions.serializer())

    private suspend fun load(): MutableMap<String, GameOptions> = games ?: run {
        val text = host.readPrivate(name)   // a throw: not cached
        val map = if (text.isNullOrBlank()) emptyMap() else try {
            json.decodeFromString(serializer, text)
        } catch (e: SerializationException) {
            host.writePrivate(BAD_NAME, text)   // keep the unreadable file aside; a throw here: not cached
            emptyMap()
        }
        map.toMutableMap().also { games = it }
    }

    private suspend fun save(map: Map<String, GameOptions>) = host.writePrivate(name, json.encodeToString(serializer, map))

    /** The game's saved options, or null when it has none. */
    suspend fun get(gamePath: String): GameOptions? = mutex.withLock { load()[gamePath] }

    /** Merges [options] into the game's list by key (new ones last, existing order and picks kept); [wide] sticks once true. */
    suspend fun record(gamePath: String, options: List<ArtOption>, wide: Boolean) = mutex.withLock {
        val map = load()
        val old = map[gamePath] ?: GameOptions()
        val known = old.options.mapTo(HashSet()) { it.key }
        val merged = old.options + options.filter { known.add(it.key) }
        map[gamePath] = old.copy(options = merged, wide = old.wide || wide)
        save(map)
    }

    /** Marks the option with [key] as the game's pick in [slot]'s file slot ([ArtSlot.fileSlot]); null: no pick there. */
    suspend fun setPick(gamePath: String, slot: ArtSlot, key: String?) = mutex.withLock {
        val map = load()
        val old = map[gamePath] ?: GameOptions()
        val file = slot.fileSlot
        map[gamePath] = old.copy(picks = if (key == null) old.picks - file else old.picks + (file to key))
        save(map)
    }

    /** The file slots each game has a pick in (games without one left out). */
    suspend fun picks(): Map<String, Set<ArtSlot>> = mutex.withLock {
        load().mapNotNull { (g, o) -> o.picks.keys.takeIf { it.isNotEmpty() }?.let { g to it.toSet() } }.toMap()
    }

    suspend fun pickedSlots(gamePath: String): Set<ArtSlot> = mutex.withLock { load()[gamePath]?.picks?.keys?.toSet().orEmpty() }

    /** True when the game has a pick in [slot]'s file slot. */
    suspend fun picked(gamePath: String, slot: ArtSlot): Boolean = mutex.withLock { load()[gamePath]?.picks?.containsKey(slot.fileSlot) == true }

    /** Removes the option with [key]; a pick that was that option is cleared. */
    suspend fun dropOption(gamePath: String, key: String) = mutex.withLock {
        val map = load()
        val old = map[gamePath] ?: return@withLock
        map[gamePath] = old.copy(options = old.options.filter { it.key != key }, picks = old.picks.filterValues { it != key })
        save(map)
    }

    /** Forgets the given games' options (null = every game); returns the forgotten options' keys by game. */
    suspend fun forget(gamePaths: Set<String>?): Map<String, Set<String>> = mutex.withLock {
        val map = load()
        val gone = map.keys.filter { gamePaths == null || it in gamePaths }
        val keys = gone.associateWith { g -> map.getValue(g).options.mapTo(HashSet()) { it.key } }
        if (gone.isNotEmpty()) { gone.forEach { map.remove(it) }; save(map) }
        keys
    }
}
