package com.nxredux.mobile.sync

import com.nxredux.mobile.library.Names
import com.nxredux.mobile.storage.TextLines

/**
 * Translates collection lines between this phone's paths and the peer's. A line is read as a game of a paired console
 * at a path relative to its console folder, then written where the receiver keeps that game: its paired folder when it
 * has the name there, else one of its extras (a phone's `/Extra/<id>/…`), else a sibling emulator folder that has the
 * name, else the paired folder. A line that is no such game (an unpaired folder, a non-`/Roms` line, an extra file that
 * is no unit, a blank line) travels unchanged. Pure.
 *
 * [pairs]: the console pairing; [localAll] / [remoteAll]: each side's top-level `Roms/` folders (for siblings);
 * [own] / [peer]: each phone's extras (a handheld has none); [homeNames]: this phone's names per folder (any case);
 * [remoteNames]: the peer's names per folder (lowercase), for the folders it listed. Missing data only makes fewer lines
 * move: with no pairing at all, every line is unchanged.
 *
 * [mapped]: [pairs] are a handheld peer's ([SyncConsoles.pairMapped]), several per landing folder here. A sibling is then
 * any other folder of the same console ([SyncMap.consoleOf]), so another folder of the console here translates like its
 * landing. A peer line lands in the landing folder (or the folder here that holds the game); a line here goes to the
 * landing's pair whose folder there holds the game, else to the one whose [ConsolePair.pushTags] has the game's emulator
 * tag ([gameTags]: per folder here, top-level name → tag; else the console's default tag; an extra's: its unit's tag),
 * else to the first.
 */
class CollectionPaths(
    private val pairs: List<ConsolePair> = emptyList(),
    localAll: List<String> = emptyList(),
    remoteAll: List<String> = emptyList(),
    own: ExtrasManifest = ExtrasManifest(),
    peer: ExtrasManifest = ExtrasManifest(),
    homeNames: Map<String, Set<String>> = emptyMap(),
    remoteNames: Map<String, Set<String>> = emptyMap(),
    private val mapped: Boolean = false,
    private val gameTags: Map<String, Map<String, String>> = emptyMap(),
) {
    private class Side(
        val folderOf: (ConsolePair) -> String,
        val all: List<String>,
        val units: List<ExtraUnit>,
        /** Lowercase names per folder; a folder that is absent is not known. */
        val names: Map<String, Set<String>>,
    )

    /** A line's game: its [pair], the path [rel] below the console folder and, read on this phone's side, its emulator [tag]. */
    private class Game(val pair: ConsolePair, val rel: String, val tag: String?)

    private val mine = Side({ it.local }, localAll, own.units.filter(SyncRoms::safe),
        homeNames.mapValues { (_, v) -> v.mapTo(HashSet()) { it.lowercase() } })
    private val theirs = Side({ it.remote }, remoteAll, peer.units.filter(SyncRoms::safe),
        remoteNames.mapValues { (_, v) -> v.mapTo(HashSet()) { it.lowercase() } })

    /** This phone's [line] as the peer would write it. */
    fun toPeer(line: String): String = translate(line, mine, theirs)

    /** The peer's [line] as this phone would write it. */
    fun fromPeer(line: String): String = translate(line, theirs, mine)

    private fun translate(line: String, from: Side, to: Side): String {
        val game = decode(line, from) ?: return line
        return encode(game, to)
    }

    private fun siblings(folder: String, all: List<String>): List<String> = SyncConsoles.siblings(folder, all, mapped)

    /** [line] on [side] as a game of a pair; null when it is none. */
    private fun decode(line: String, side: Side): Game? {
        if (line.startsWith(ROMS)) {
            val rest = line.removePrefix(ROMS)
            val slash = rest.indexOf('/')
            if (slash <= 0 || slash == rest.length - 1) return null
            val folder = rest.substring(0, slash)
            val rel = rest.substring(slash + 1)
            val pair = pairs.firstOrNull { side.folderOf(it) == folder }
                ?: siblings(folder, side.all + pairs.map(side.folderOf)).let { sib -> pairs.firstOrNull { side.folderOf(it) in sib } }
                ?: return null
            return Game(pair, rel, if (side === mine && mapped) tagOf(folder, rel.substringBefore('/')) else null)
        }
        if (!line.startsWith(EXTRA)) return null
        for (u in side.units) {
            // Mapped: the pair its games push to (a shared GPGX folder tag would name several); else by tag, then by console.
            val pair = (if (mapped) pairs.firstOrNull { u.tag.uppercase() in it.pushTags } else null)
                ?: pairs.firstOrNull { it.tag.equals(u.tag, ignoreCase = true) }
                ?: pairs.firstOrNull { !mapped && SyncMap.ofConsole(u.tag, side.folderOf(it)) } ?: continue
            val path = extraPath(u) ?: continue
            if (disc(u)) {
                val dir = path.substringBeforeLast('/') + "/"
                if (line.startsWith(dir) && line.length > dir.length) return Game(pair, "${u.landing}/${line.removePrefix(dir)}", u.tag.uppercase())
            } else if (line == path) {
                return Game(pair, u.landingKey, u.tag.uppercase())
            }
        }
        return null
    }

    /** The emulator tag of [name] in this phone's [folder] ([gameTags], else its console's default; a folder's tag names only its console). */
    private fun tagOf(folder: String, name: String): String? {
        val tags = gameTags[folder].orEmpty()
        (tags[name] ?: tags.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value)?.let { return it }
        return SyncMap.consoleOf(folder)?.defaultTag
    }

    /** Where [side] keeps [game]. */
    private fun encode(game: Game, side: Side): String {
        val pair = game.pair; val rel = game.rel
        val folder = side.folderOf(pair)
        val first = rel.substringBefore('/').lowercase()
        if (mapped && side === theirs) {
            val candidates = pairs.filter { it.local == pair.local }
            candidates.firstOrNull { side.names[it.remote]?.contains(first) == true }?.let { return "$ROMS${it.remote}/$rel" }
            if (side.names.containsKey(folder)) {
                siblings(folder, side.all).firstOrNull { side.names[it]?.contains(first) == true }?.let { return "$ROMS$it/$rel" }
            }
            val target = candidates.firstOrNull { game.tag != null && game.tag in it.pushTags } ?: candidates.firstOrNull() ?: pair
            return "$ROMS${target.remote}/$rel"
        }
        if (side.names[folder]?.contains(first) == true) return "$ROMS$folder/$rel"
        for (u in side.units) {
            if (!u.tag.equals(pair.tag, ignoreCase = true) && !SyncMap.ofConsole(u.tag, folder)) continue
            val path = extraPath(u) ?: continue
            if (disc(u)) {
                val prefix = "${u.landing}/"
                if (rel.startsWith(prefix) && rel.length > prefix.length) return path.substringBeforeLast('/') + "/" + rel.removePrefix(prefix)
            } else if (rel == u.landingKey) {
                return path
            }
        }
        if (side.names.containsKey(folder)) {
            siblings(folder, side.all).firstOrNull { side.names[it]?.contains(first) == true }?.let { return "$ROMS$it/$rel" }
        }
        return "$ROMS$folder/$rel"
    }

    private fun disc(u: ExtraUnit) = '/' in u.landingKey

    /** The unit's game as a collection line (`/Extra/<id>/<dir>/<file>`), from its `.sources/<id>/…` key. */
    private fun extraPath(u: ExtraUnit): String? = u.key.takeIf { it.startsWith(SOURCES) }?.let { EXTRA + it.removePrefix(SOURCES) }

    private companion object {
        const val ROMS = "/Roms/"
        const val EXTRA = "/Extra/"
        const val SOURCES = ".sources/"
    }
}

/** One collection's merge: the lines to write on each side, null where nothing changed. */
data class CollectionMerge(val own: List<String>?, val peer: List<String>?)

/** What collections sync writes: this phone's collections (name → lines) and the peer's files (file name → text). */
data class CollectionsPlan(val own: Map<String, List<String>>, val peer: Map<String, String>)

/** Collections sync's merge. Pure. */
object SyncCollections {
    private const val TXT = ".txt"

    /** A collection file's lines: a leading byte order mark dropped, `\r\n` read as `\n`, no empty last line. */
    fun lines(text: String): List<String> {
        val all = TextLines.split(text)
        return if (all.lastOrNull() == "") all.dropLast(1) else all
    }

    /**
     * One collection: [own] / [peer] its lines on each side (null: no such file). Each side keeps its lines in order and
     * gets the other side's lines it lacks (translated by [paths]) appended in the other side's order; blank lines and
     * repeats are never added. A collection only on one side is copied, an empty one as an empty file.
     */
    fun merge(own: List<String>?, peer: List<String>?, paths: CollectionPaths): CollectionMerge {
        fun side(have: List<String>?, other: List<String>?, translate: (String) -> String): List<String>? {
            if (other == null) return null
            val present = have.orEmpty().map { it.trimEnd('\r') }.toHashSet()
            val added = LinkedHashSet<String>()
            for (raw in other) {
                val line = raw.trimEnd('\r')
                if (line.isBlank()) continue
                val t = translate(line)
                if (t !in present) added += t
            }
            return when {
                have == null -> added.toList()
                added.isEmpty() -> null
                else -> have.map { it.trimEnd('\r') } + added
            }
        }
        return CollectionMerge(side(own, peer, paths::fromPeer), side(peer, own, paths::toPeer))
    }

    /**
     * Every collection on either side, matched by name ignoring case (both sides' volumes are case-insensitive): [own]
     * this phone's (name → lines; null: its file would not read, so that collection is left alone on both sides),
     * [peerFiles] the peer's top-level files (file name → text); [peerUnreadable] the peer's files that would not decode,
     * whose collections are left alone on both sides too. A name on either side that is no valid collection name
     * (`map` in any case, a hidden file, `\ : * ? " < > |`, which would fail the push every sync) is left alone. Each
     * side keeps its own spelling of a shared name.
     */
    fun plan(
        own: Map<String, List<String>?>, peerFiles: Map<String, String>, paths: CollectionPaths, peerUnreadable: Set<String> = emptySet(),
    ): CollectionsPlan {
        fun nameOf(file: String): String? {
            if (!file.endsWith(TXT, ignoreCase = true) || Names.isHidden(file)) return null
            return file.dropLast(TXT.length).takeIf { Names.validCollectionName(it) == it }
        }
        val skipped = peerUnreadable.mapNotNullTo(HashSet()) { nameOf(it)?.lowercase() }
        val peer = LinkedHashMap<String, Pair<String, List<String>>>()   // lowercase name → (file name, lines)
        for ((file, text) in peerFiles) {
            val name = nameOf(file) ?: continue
            peer.putIfAbsent(name.lowercase(), file to lines(text))
        }
        val ownByKey = own.entries.filter { Names.validCollectionName(it.key) == it.key }.associateBy { it.key.lowercase() }
        val ownOut = LinkedHashMap<String, List<String>>()
        val peerOut = LinkedHashMap<String, String>()
        for (key in (ownByKey.keys + peer.keys).distinct()) {
            if (key in skipped) continue   // unreadable there: merging from nothing would lose lines
            val o = ownByKey[key]
            if (o != null && o.value == null) continue   // unreadable here: likewise
            val p = peer[key]
            val m = merge(o?.value, p?.second, paths)
            m.own?.let { ownOut[o?.key ?: p!!.first.dropLast(TXT.length)] = it }
            m.peer?.let { peerOut[p?.first ?: (o!!.key + TXT)] = it.joinToString("") { l -> "$l\n" } }
        }
        return CollectionsPlan(ownOut, peerOut)
    }
}
