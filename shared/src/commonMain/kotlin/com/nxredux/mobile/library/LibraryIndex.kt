package com.nxredux.mobile.library

import com.nxredux.mobile.runner.ContentRef
import com.nxredux.mobile.runner.CoreRegistry
import com.nxredux.mobile.settings.ExtraFolder
import com.nxredux.mobile.storage.GamePath
import com.nxredux.mobile.storage.GameRef
import kotlinx.serialization.Serializable

/**
 * The scanned library, persisted app-private so start-up, game lists and recents need no SAF I/O. [home], [extras]
 * and [defaultTags] are the settings it was built for; [systems] holds every system the scan found (empty home
 * skeleton folders and coreless tags included), before the playable filter, in library order. Refs are
 * `ContentRef.value` strings. The core is not stored: it comes from the tag when the index is shown. [offline] holds the
 * ids of extras that were on unmounted storage at scan time: they are in [extras] (the index still fits the settings)
 * but none of their games are, and a start that finds one mounted again scans. [lateMount] is the subset whose volume
 * was mounted yet still did not list (the provider can lag the mount): kept for one more scan, then dropped.
 * [scannedAt]: when the scan that built it finished (epoch ms), for Settings -> Library; null when unknown (an index from
 * before the field, or a platform with no clock). Not part of the fit, so no version bump.
 */
@Serializable
data class LibraryIndex(
    val version: Int,
    val home: String,
    val extras: List<IndexedExtra>,
    val defaultTags: Map<String, String>,
    val systems: List<IndexedSystem>,
    val offline: List<String> = emptyList(),
    val lateMount: List<String> = emptyList(),
    val scannedAt: Long? = null,
    /** Each disc image an extra folder's scan probed ([com.nxredux.mobile.library.disc.DiscProbe]), so an unchanged one is not read again. */
    val probes: List<IndexedProbe> = emptyList(),
    /** What each disc sheet named and each peeked zip held at scan time ([HeadReads]), so an unchanged one is not opened again. */
    val heads: List<IndexedHead> = emptyList(),
) {
    companion object {
        /**
         * The stored format's version: an index of any other version decodes as null and the library is scanned again
         * ([LibraryIndexCodec]). It goes up when an older index would show the library wrongly, not for a field that
         * defaults and fills on the next scan ([scannedAt], [IndexedGame.firstSeen]). An index without
         * [IndexedGame.hiddenCopies] would list the extra copies the scan hides; one without `wheel` and `mix` would hide
         * an extra folder's own handheld art from the scraper (which reads only the home mirror on disk), so it would
         * write a second copy in `Roms/.sources/`. 12: `grid` and `hero` are gone (no longer read), `heroShot` added.
         */
        const val VERSION = 12
    }
}

@Serializable
data class IndexedExtra(val id: String, val ref: String)

/**
 * A disc probe's answer for [ref] at that [size] and [modified] time: a console id, or null (nothing recognised). A
 * sheet's answer also holds only while its [data] file is at [dataSize] and [dataModified].
 */
@Serializable
data class IndexedProbe(
    val ref: String, val size: Long?, val modified: Long?, val console: String?,
    val data: String? = null, val dataSize: Long? = null, val dataModified: Long? = null,
)

/**
 * A head read's answer for [ref] at that [size] and [modified] time ([HeadReads]): the names a disc sheet's head lists
 * ([DiscSheet.references]), or a zip's first entry name (none when its head names no file).
 */
@Serializable
data class IndexedHead(val ref: String, val size: Long?, val modified: Long, val names: List<String>)

@Serializable
data class IndexedSystem(
    val tag: String,
    val displayName: String,
    /** The home skeleton folder's name, e.g. "Game Boy (GB)"; "" when the tag has none. */
    val folderName: String = "",
    val unassigned: Boolean = false,
    val art: String? = null,
    val bg: String? = null,
    /** Sorted by [IndexedGame.name], ignoring case. */
    val games: List<IndexedGame>,
)

@Serializable
data class IndexedGame(
    /** `Recents.HOME` or an extra folder's id. */
    val source: String,
    /** The relative dir: "Roms/<System (TAG)>" in the home, "" or "a/b" in an extra folder. */
    val dir: String,
    val file: String,
    val ref: String,
    val folder: String,
    /** The shown name: the `map.txt` alias, else the arcade title or file name without bracketed text (the stem when that collides in its folder). */
    val name: String,
    /**
     * Typed art refs: 3D box, 2D front cover (`boxart2d/`, else the legacy `box2d/`), screenshot; the wheel and the mix
     * (known, not shown). [heroShot]: the screenshot is a SteamGridDB hero (from the written-art log).
     */
    val boxart: String? = null,
    val box2d: String? = null,
    val screenshot: String? = null,
    val wheel: String? = null,
    val mix: String? = null,
    val heroShot: Boolean = false,
    /** Where its art and `map.txt` name live: [dir], or for a disc folder the folder around it. */
    val lookupDir: String = dir,
    /** An extra-folder copy's folder name, shown under the name when a rule-named copy with the same name lives in another folder; null otherwise (a home copy, and Unassigned, which shows [dir]). */
    val subtitle: String? = null,
    /**
     * When the library first listed this game (epoch ms), for Home's New shelf: the previous index's date
     * for the same (source, dir, file), else the file's date, else the scan's time ([withFirstSeen]). Null in an index
     * from before the field (no version bump: it defaults) or with no clock.
     */
    val firstSeen: Long? = null,
    /**
     * The [GamePath]s of the extra folders' copies of this home game that the scan hid ([LibraryScanner]), so a
     * Recents, pin or collection line written for one still resolves to this game.
     */
    val hiddenCopies: List<String> = emptyList(),
)

/** Library order: by display name, the tag breaking ties (GBA and MGBA share a name); Unassigned last. */
internal fun List<IndexedSystem>.inLibraryOrder(): List<IndexedSystem> {
    val (unassigned, systems) = partition { it.unassigned }
    return systems.sortedWith(compareBy({ it.displayName.lowercase() }, { it.tag })) + unassigned
}

/** Game-list order: by shown name, ignoring case; stable for equal names. */
internal fun List<IndexedGame>.inNameOrder(): List<IndexedGame> = sortedBy { it.name.lowercase() }

/** The lists the UI shows, minus games whose [GamePath] is in [hidden]. Pure (no source): the core comes from the tag. */
fun LibraryIndex.toSystems(hidden: Set<String> = emptySet()): List<SystemFolder> = systems.map { s ->
    SystemFolder(
        displayName = s.displayName,
        tag = s.tag,
        core = if (s.unassigned) null else CoreRegistry.forTag(s.tag),
        games = s.games.filter { hidden.isEmpty() || GamePath.of(GameRef(it.source, it.dir, it.file)) !in hidden }.map { g ->
            GameEntry(
                ref = ContentRef(g.ref), fileName = g.file, shownName = g.name,
                boxartRef = g.boxart?.let { ContentRef(it) }, screenshotRef = g.screenshot?.let { ContentRef(it) },
                box2dRef = g.box2d?.let { ContentRef(it) }, heroShot = g.heroShot,
                wheelRef = g.wheel?.let { ContentRef(it) }, mixRef = g.mix?.let { ContentRef(it) },
                folder = ContentRef(g.folder), sourceId = g.source, relativeDir = g.dir, lookupDir = g.lookupDir,
                subtitle = if (s.unassigned) g.dir.ifEmpty { "/" } else g.subtitle,
                firstSeen = g.firstSeen,
                hiddenCopies = g.hiddenCopies.mapNotNull(GamePath::split),
            )
        },
        artRef = s.art?.let { ContentRef(it) },
        bgRef = s.bg?.let { ContentRef(it) },
        folderName = s.folderName,
        unassigned = s.unassigned,
    )
}

/**
 * This index with each game's [IndexedGame.firstSeen] settled after a scan: the date [previous] held for the same
 * (source, dir, file); for a game [previous] lacks, [now] (a copy keeps its file's old date, so a game just copied in
 * would never be New); else the scan's own (the file's date: the first scan, with no [previous], and a game an
 * undated index from before the field lists), else [now]. [now] null (no clock) leaves a missing one null.
 */
fun LibraryIndex.withFirstSeen(previous: LibraryIndex?, now: Long?): LibraryIndex {
    val known = HashMap<Triple<String, String, String>, Long?>()
    previous?.systems?.forEach { s -> s.games.forEach { g -> known[Triple(g.source, g.dir, g.file)] = g.firstSeen } }
    return copy(systems = systems.map { s ->
        s.copy(games = s.games.map { g ->
            val key = Triple(g.source, g.dir, g.file)
            val unknown = previous != null && key !in known
            g.copy(firstSeen = known[key] ?: (if (unknown) now else null) ?: g.firstSeen ?: now)
        })
    })
}

/** As the handheld: empty consoles (hidden games included) and coreless ones are hidden; Unassigned shows whenever the index has a visible game in it. */
fun LibraryIndex.playableSystems(hidden: Set<String> = emptySet()): List<SystemFolder> =
    toSystems(hidden).filter { it.gameCount > 0 && (it.core != null || it.unassigned) }

/** [game] shown as [name], its console re-sorted. Unchanged (same instance) when [game] is not in the index. Subtitles refresh on the next rescan. */
fun LibraryIndex.rename(game: GameEntry, name: String): LibraryIndex {
    var changed = false
    val next = systems.map { s ->
        val hit = s.games.firstOrNull { it.source == game.sourceId && it.dir == game.relativeDir && it.file == game.fileName } ?: return@map s
        changed = true
        s.copy(games = (s.games - hit + hit.copy(name = name)).inNameOrder())
    }
    return if (changed) copy(systems = next) else this
}

/**
 * A game's typed art refs (ContentRef values), as the index stores them; null = no file of that kind. [wheel] and [mix]
 * are only known, never shown: Mix's has-art rule needs the mix. [heroShot]: the [screenshot] is a SteamGridDB
 * hero (its written-art origin), drawn as a hero and never a mix input.
 */
data class ArtRefs(
    val boxart: String? = null, val box2d: String? = null, val screenshot: String? = null,
    val wheel: String? = null, val mix: String? = null, val heroShot: Boolean = false,
)

/** [game] with all its art refs replaced by [art]; other fields and order kept. Unchanged (same instance) when [game] is not in the index. */
fun LibraryIndex.setArt(game: IndexedGame, art: ArtRefs): LibraryIndex {
    var changed = false
    val next = systems.map { s ->
        val at = s.games.indexOfFirst { it.source == game.source && it.dir == game.dir && it.file == game.file }
        if (at < 0) return@map s
        changed = true
        s.copy(games = s.games.toMutableList().also { it[at] = it[at].withArt(art) })
    }
    return if (changed) copy(systems = next) else this
}

/** A game's place in the index: its source, dir and file ([LibraryScanner.readArt]'s keys). */
typealias ArtKey = Triple<String, String, String>

fun artKey(game: IndexedGame): ArtKey = Triple(game.source, game.dir, game.file)

/** [IndexedGame]'s art refs as [ArtRefs]. */
val IndexedGame.artRefs: ArtRefs get() = ArtRefs(boxart, box2d, screenshot, wheel, mix, heroShot)

/**
 * This game with all its art refs replaced by [a], every other field kept. With [artRefs] and [ArtRefs] itself, the
 * only places that name each art kind: a new kind is added here, not at every copy.
 */
fun IndexedGame.withArt(a: ArtRefs): IndexedGame =
    copy(boxart = a.boxart, box2d = a.box2d, screenshot = a.screenshot, wheel = a.wheel, mix = a.mix, heroShot = a.heroShot)

/**
 * Each game in [art] with its art refs replaced by its entry there (a game not in [art] keeps its own); other fields and
 * order kept. Unchanged (the same instance) when no game's art differs.
 */
fun LibraryIndex.withArt(art: Map<ArtKey, ArtRefs>): LibraryIndex {
    var changed = false
    val next = systems.map { s ->
        var mine = false
        val games = s.games.map { g ->
            val a = art[artKey(g)]
            if (a == null || a == g.artRefs) g
            else { mine = true; g.withArt(a) }
        }
        if (mine) { changed = true; s.copy(games = games) } else s
    }
    return if (changed) copy(systems = next) else this
}

/**
 * [game] moved from [from] to tag [to] after its sidecar was written, with no scan: removed from [from] (which is
 * dropped when it is now empty and has no home folder, as Unassigned has none), added to the system with tag [to]
 * (created from the console when missing, with no art), games and systems re-sorted. Unchanged when [game] is not
 * in [from]. Subtitles refresh on the next rescan.
 */
fun LibraryIndex.move(game: GameEntry, from: SystemFolder, to: String): LibraryIndex {
    val toTag = to.trim().uppercase()
    var moved: IndexedGame? = null
    val left = systems.mapNotNull { s ->
        if (s.tag != from.tag || s.unassigned != from.unassigned) return@mapNotNull s
        val hit = s.games.firstOrNull { it.source == game.sourceId && it.dir == game.relativeDir && it.file == game.fileName }
            ?: return@mapNotNull s
        moved = hit
        val rest = s.games - hit
        if (rest.isEmpty() && s.folderName.isEmpty()) null else s.copy(games = rest)
    }
    val g = moved ?: return this
    val target = left.firstOrNull { !it.unassigned && it.tag == toTag }
    val next = if (target != null) {
        left.map { if (it === target) it.copy(games = (it.games + g).inNameOrder()) else it }
    } else {
        left + IndexedSystem(tag = toTag, displayName = CoreRegistry.consoleOf(toTag)?.displayName ?: toTag, games = listOf(g))
    }
    return copy(systems = next.inLibraryOrder())
}

/** True when this index was built for these settings: the same home, the same extras (order ignored) and the same default tags. */
fun LibraryIndex.fits(home: ContentRef, extras: List<ExtraFolder>, defaultTags: Map<String, String>): Boolean =
    this.home == home.value &&
        this.extras.toSet() == extras.map { IndexedExtra(it.id, it.ref.value) }.toSet() &&
        this.defaultTags == defaultTags
