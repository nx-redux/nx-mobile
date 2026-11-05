package com.nxredux.mobile.scraper

import com.nxredux.mobile.library.ArtRefs
import com.nxredux.mobile.library.Entry
import com.nxredux.mobile.library.RomsSource
import com.nxredux.mobile.library.ensurePath
import com.nxredux.mobile.runner.ContentRef
import com.nxredux.mobile.storage.GamePath
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** The existing folder at [segments] below [home] (nothing created); null when any part is missing. */
internal suspend fun RomsSource.findDir(home: ContentRef, segments: List<String>): ContentRef? {
    var dir = home
    for (s in segments) dir = child(dir, s)?.takeIf { it.isDirectory }?.ref ?: return null
    return dir
}

/**
 * The per-game fetch flow: for each game, only the kinds it lacks (a box, a screenshot, and the optional extras of
 * [extras]: a 2D box, a wheel, the mix), asked of [sources] in order (each enabled one that knows the game, for the
 * slots it offers that are still missing); each kind from the first hit that downloads and fits ([ImageFitter]),
 * written typed under the home tree; then the art is reported. A source's [SourceStop] leaves it out for the rest of
 * the run (a note on the job) and the job goes on; a [JobPause] ("No connection", storage) pauses it. A game whose
 * sources have all stopped stays WAITING and the run ends PAUSED ([ALL_STOPPED]) so Resume retries it.
 *
 * An Android game ([LibraryGameRef.android]) has its own flow ([scrapeAndroid]), ScreenScraper only: looked up by
 * label, its `ss` screenshot only written to [androidArt] (app-private, never the home tree or the index), then
 * [onAndroidArt]; its other pictures come only from Change art picks. Without [androidArt] or ScreenScraper, Android
 * games are skipped.
 */
class ScrapeEngine(
    private val http: ScraperHttp, private val fitter: ImageFitter,
    private val source: RomsSource, private val home: ContentRef,
    /** Asked in this order (ScreenScraper first). */
    val sources: List<ArtSource>,
    private val onArt: suspend (gamePath: String, art: ArtRefs) -> Unit,
    /** Before each write: the home-relative path and where its image came from (the written-art log). */
    private val onWritten: suspend (List<Pair<String, ArtOrigin?>>) -> Unit,
    private val androidArt: AndroidArtStore? = null,
    private val onAndroidArt: suspend () -> Unit = {},
    /** The written-art log: under Replace, only the app's own files of a higher box slot are removed. */
    private val logged: LoggedArt = LoggedArt.NONE,
    /** The image downloads' retry waits ([download]); a test's virtual clock. */
    private val sleep: suspend (Long) -> Unit = { delay(it) },
    /** Tools → Artwork → Settings' optional art, read at each game (a change applies from the next one). */
    private val extras: () -> ArtExtras = { ArtExtras() },
    /** Decodes the mix's inputs and encodes it ([MixCompositor]); null = no mix is made. */
    private val pixels: PixelCodec? = null,
    /** A mix that could not be made or saved (never a game's failure); no paths or URLs in the text. */
    private val warn: (String) -> Unit = {},
    /**
     * Every candidate the sources offered for a game in the option slots ([OPTION_SLOTS]; ScreenScraper links and
     * thumbnails without their login), once per game; [wide] is true for the wide pass. The Change art list keeps them
     * as links.
     */
    private val onOptions: suspend (gamePath: String, options: List<ArtOption>, wide: Boolean) -> Unit = { _, _, _ -> },
    /**
     * The file slots the user picked for this game (SCREENSHOT, BOX_2D, BOX_3D, WHEEL): no fetch (Replace existing
     * included) writes, replaces or removes a picked slot's file.
     */
    private val picked: suspend (gamePath: String) -> Set<ArtSlot> = { emptySet() },
) {
    companion object {
        const val NO_CONNECTION = "No connection"
        const val STORAGE = "Couldn't write to the library folder"
        /** A failure that is neither the connection nor the library folder (the index, the app's own files, memory). */
        const val FAILED = "Something went wrong"
        /** Every source that knows a waiting game has stopped for this run (Resume tries them again). */
        const val ALL_STOPPED = "Every art source stopped"
        const val NO_SOURCE = "No art source for its system"
        /** Skipped: the game has a box and a screenshot already (without Replace existing art). */
        const val HAS_ART = "Has art"
        /** Done with a SteamGridDB hero as the screenshot (no real one), or Partial without a screenshot. */
        const val NO_SCREENSHOT = "no screenshot"
        /** Partial: a screenshot but no box. */
        const val NO_BOX = "no box"
        /** Partial or Not found: Generate mix is on and no mix could be made (the game lacked only that). */
        const val NO_MIX = "no mix"
        /** The written-art log's source of a mix (made on the device, not downloaded). */
        const val MIX_ORIGIN = "device"
        /** A mix input larger than this is not read (the app's own art is far smaller). */
        private const val MIX_INPUT_MAX = 32 * 1024 * 1024

        /**
         * A Done game's reason: the sources that wrote its files ([ArtSource.shortLabel], in the order they wrote),
         * then [NO_SCREENSHOT] when the screenshot is a hero; null when nothing names a source.
         */
        internal fun doneReason(writers: Collection<String>, standInOnly: Boolean): String? =
            listOfNotNull(writers.joinToString(", ").ifEmpty { null }, NO_SCREENSHOT.takeIf { standInOnly })
                .joinToString(" · ").ifEmpty { null }
        /** The hit kinds a box is written from (a grid is written as the 2D box, [fileSlot]). */
        private val BOX_SLOTS = setOf(ArtSlot.BOX_3D, ArtSlot.BOX_2D, ArtSlot.GRID)
        /** The 2D box's hit kinds, in the order they are tried: a 2D box, then a grid. */
        private val BOX_2D_SLOTS = listOf(ArtSlot.BOX_2D, ArtSlot.GRID)
        /** The screenshot's hit kinds, in the order they are tried: a screenshot, then a hero (written as one, [fileSlot]). */
        private val SHOT_SLOTS = listOf(ArtSlot.SCREENSHOT, ArtSlot.HERO)
        /** The box chain's order where box art is drawn: boxart, then box2d. */
        private val BOX_CHAIN = listOf(ArtSlot.BOX_3D, ArtSlot.BOX_2D)
        /** The slots a mix is made from ([makeMix]); writing one of them makes the app's own mix again. */
        private val MIX_INPUTS = setOf(ArtSlot.SCREENSHOT, ArtSlot.BOX_3D, ArtSlot.BOX_2D, ArtSlot.WHEEL)
        /** An image download without an answer is tried again once after this; a second miss pauses ([NO_CONNECTION]). */
        const val OFFLINE_RETRY_MS = 2_000L
        /** An image download answered 429 or 5xx is tried again once after this; a second one skips that image. */
        const val BUSY_RETRY_MS = 5_000L
        /** The extensions a typed file is read with, as the scanner does (any case; `.png`, then `.jpg`, then `.jpeg`). */
        private val DISK_EXTS = listOf("png", "jpg", "jpeg")

        /** [name] is `<base>.<ext>` for one of [DISK_EXTS] (the extension in any case); its rank there, or -1. */
        private fun extRank(name: String, base: String): Int {
            if (name.length <= base.length + 1 || !name.startsWith(base) || name[base.length] != '.') return -1
            val ext = name.substring(base.length + 1)
            return DISK_EXTS.indexOfFirst { it.equals(ext, ignoreCase = true) }
        }

        /**
         * The art to put in the index for [ref] once the engine has touched its folder: [after] (what the home tree, or
         * an extra's home mirror, holds after the run), except where the index holds an extra folder's own `.media`
         * file - a ref that is neither the mirror's file [before] the run nor after it - which wins per slot as in the
         * scan. A slot in [picked] (the user picked it for this game) takes [after]'s ref over an extra folder's own
         * file. [ArtRefs.heroShot] follows the screenshot ref chosen.
         */
        internal fun indexArt(ref: LibraryGameRef, before: ArtRefs, after: ArtRefs, picked: Set<ArtSlot> = emptySet()): ArtRefs {
            if (ref.android || ref.sourceId == GamePath.HOME) return after
            val c = ref.art
            fun pick(slot: ArtSlot, cur: String?, b: String?, a: String?) =
                if (slot !in picked && cur != null && cur != b && cur != a) cur else a
            val shot = pick(ArtSlot.SCREENSHOT, c.screenshot, before.screenshot, after.screenshot)
            return ArtRefs(
                boxart = pick(ArtSlot.BOX_3D, c.boxart, before.boxart, after.boxart), box2d = pick(ArtSlot.BOX_2D, c.box2d, before.box2d, after.box2d),
                screenshot = shot, wheel = pick(ArtSlot.WHEEL, c.wheel, before.wheel, after.wheel), mix = pick(ArtSlot.MIX, c.mix, before.mix, after.mix),
                heroShot = if (shot == after.screenshot) after.heroShot else c.heroShot,
            )
        }
    }

    /** ScreenScraper among [sources] (its account calls and the Android games' lookup), or null. */
    val ss: ScreenScraperSource? = sources.firstNotNullOfOrNull { it as? ScreenScraperSource }

    /** TheGamesDB among [sources] (its allowance read for the UI), or null. */
    val tgdb: TheGamesDbSource? = sources.firstNotNullOfOrNull { it as? TheGamesDbSource }

    /** SteamGridDB among [sources] (its key check on save), or null. */
    val sgdb: SteamGridDbSource? = sources.firstNotNullOfOrNull { it as? SteamGridDbSource }

    /**
     * The enabled sources that know [ref] (an Unassigned game, with no tag, or an excluded tag: none; an Android game:
     * ScreenScraper, with an art store). Every fetch path asks this, so no source ever sees an Unassigned game.
     */
    fun sourcesFor(ref: LibraryGameRef): List<ArtSource> = when {
        ref.android -> listOfNotNull(ss?.takeIf { it.enabled && androidArt != null })
        ref.tag.isBlank() -> emptyList()
        ref.tag.trim().uppercase() in ScraperSystems.EXCLUDED_TAGS -> emptyList()
        else -> sources.filter { it.enabled && it.knows(ref) }
    }

    /**
     * Runs items from the first WAITING one; emits state via [update]; returns the final state. Without [replace], a
     * game that has art by its turn ([ArtHave.complete]; art appeared since it was queued) is skipped. The sources
     * named in [ScrapeState.notes] of [start] stay stopped (a chained run of the same job); a new job or Resume clears
     * them. [startSources] false: the sources' [ArtSource.startJob] is not called (a single-game fetch while a job runs
     * on the same sources must not reset that job's per-run state). [wide] (a single-game Change art fetch): the has-art
     * skips are bypassed and every known source is also asked for its screenshot, grid and hero slots (in the usual
     * flow's call to it, or after the usual flow for a source it did not ask), only to report the options ([onOptions], with wide = true only when that pass finished:
     * a pause, a storage failure or a stopped source leaves it false, the hits so far still reported). The usual flow
     * still writes the slots the game lacks; the wide pass writes nothing, and a source stopping there never changes
     * the game's status. [found]: each game's options so far, each time a source's answer adds some (the art panel's
     * tiles while the run goes on; nothing is saved by it, [onOptions] still reports the game's list once at its end).
     */
    suspend fun run(
        start: ScrapeState, games: (String) -> LibraryGameRef?, replace: Boolean = false, startSources: Boolean = true,
        wide: Boolean = false, found: suspend (gamePath: String, options: List<ArtOption>) -> Unit = { _, _ -> },
        update: suspend (ScrapeState) -> Unit,
    ): ScrapeState {
        var s = start.inFlightToWaiting().copy(phase = JobPhase.RUNNING, pauseReason = null)
        val stopped = HashSet(s.notes.keys)
        fun quota() = ss?.lastQuota ?: s.quota
        suspend fun tgdbLeft() = tgdb?.left() ?: s.tgdbLeft
        suspend fun set(i: Int, status: ItemStatus, reason: String? = null) {
            s = s.copy(items = s.items.toMutableList().also { it[i] = it[i].copy(status = status, reason = reason) }, quota = quota(), tgdbLeft = tgdbLeft())
            update(s)
        }
        val stop: (ArtSource, SourceStop) -> Unit = { src, e -> stopped += src.id; s = s.copy(notes = s.notes + (src.id to e.note)) }
        update(s)
        var blocked = false
        val listings = Listings()
        try {
            if (startSources) for (src in sources) if (src.enabled && src.id !in stopped) try { src.startJob() } catch (e: SourceStop) { stop(src, e) }
            s = s.copy(tgdbLeft = tgdbLeft())
            for (i in s.items.indices) {
                if (s.items[i].status != ItemStatus.WAITING) continue
                val ref = games(s.items[i].gamePath)
                if (ref == null) { set(i, ItemStatus.SKIPPED, "Not in the library"); continue }
                val known = sourcesFor(ref)
                if (known.isEmpty()) { set(i, ItemStatus.SKIPPED, NO_SOURCE); continue }
                if (hasArt(ref) && !replace && !wide) { set(i, ItemStatus.SKIPPED, HAS_ART); continue }
                // A game lacking only its mix is still made one from its files on disk (no source needed).
                if (known.all { it.id in stopped } && !(lacksOnlyMix(ref) && !replace)) { blocked = true; continue }
                set(i, ItemStatus.SEARCHING)
                val downloading: suspend () -> Unit = { set(i, ItemStatus.DOWNLOADING) }
                val result = if (ref.android) scrapeAndroid(ref, stopped, replace, wide, downloading, stop, found) else scrape(ref, known, stopped, replace, wide, listings, downloading, stop, found)
                if (result == null) { blocked = true; set(i, ItemStatus.WAITING); continue }   // every source stopped: Resume retries it
                set(i, result.first, result.second)
            }
            s = s.copy(phase = if (blocked) JobPhase.PAUSED else JobPhase.DONE, pauseReason = if (blocked) ALL_STOPPED else null, quota = quota(), tgdbLeft = tgdbLeft())
        } catch (p: JobPause) {
            s = s.inFlightToWaiting().copy(phase = JobPhase.PAUSED, pauseReason = p.reason, quota = quota(), tgdbLeft = tgdbLeft())
        }
        update(s)
        return s
    }

    /** What a Reset delete did: [DELETED], the file was not there ([ABSENT]), or it is still there ([FAILED]). */
    enum class Deleted { DELETED, ABSENT, FAILED }

    /** Deletes the home-relative file [path] (for Reset); a failure that leaves it unknown whether the file is there is [Deleted.FAILED]. */
    suspend fun deleteArt(path: String): Deleted = guard {
        val parts = path.split('/')
        val dir = source.findDir(home, parts.dropLast(1)) ?: return@guard Deleted.ABSENT
        when {
            source.delete(dir, parts.last()) -> Deleted.DELETED
            source.child(dir, parts.last()) == null -> Deleted.ABSENT
            else -> Deleted.FAILED
        }
    } ?: Deleted.FAILED

    /**
     * After a Reset of [paths] (home-relative art files): removes each slot folder of theirs (`.media/<slot>/`) that is
     * left empty, then each of their `.media` folders left empty (NX Redux's `rmdir` after its deletes). A folder that
     * still holds anything is never removed.
     */
    suspend fun removeEmptyArtDirs(paths: Collection<String>) {
        val slotFolders = ArtSlot.entries.map { it.folder }.toSet() + LEGACY_BOX2D_FOLDER
        val dirs = paths.map { it.split('/').dropLast(1) }.toSet()
        val slots = dirs.filter { it.size >= 2 && it[it.size - 2] == ".media" && it.last() in slotFolders }
        val medias = dirs.mapNotNull { d -> d.lastIndexOf(".media").takeIf { it >= 0 }?.let { d.take(it + 1) } }.toSet()
        for (d in slots + medias) guard { source.findDir(home, d.dropLast(1))?.let { source.deleteEmptyDirectory(it, d.last()) } }
    }

    /** Deletes the Android games console's stored pictures (and hero marks), then rebuilds the console when any went; how many. */
    suspend fun deleteAndroidArt(): Int {
        val deleted = androidArt?.let { store -> guard { store.deleteAll() } } ?: 0
        if (deleted > 0) onAndroidArt()
        return deleted
    }

    /**
     * [ref]'s typed art files in the home tree (an extra's home mirror) as they are, read as the scanner reads them;
     * null when absent (after a Reset). An extra's own `.media` is not read here: [indexArt] keeps the index's refs to
     * it. [heroes] false: [ArtRefs.heroShot] is not looked up in the written-art log (a caller holding the log's lock,
     * Reset's before pass, whose refs are only compared).
     */
    suspend fun artOnDisk(ref: LibraryGameRef, heroes: Boolean = true): ArtRefs =
        guard { ArtPaths.target(ref.sourceId, ref.relativeDir, ref.fileName, ref.folderGame) }?.let { artOnDisk(it, Listings(), heroes) } ?: ArtRefs()

    /**
     * Held by a run's write into a pick slot (with its pick check right before it) and its index update, and by a
     * pick's write ([writePick]): a pick made while the game is fetched lands either before the run's check (the run
     * leaves the slot) or after the run's write (the pick replaces it), never under it.
     */
    private val pickWrites = Mutex()

    /**
     * One run's slot-folder listings: each folder is listed at most once per run (a console's games share them), then
     * kept in step with the run's own writes and deletes. Per call of [run] (or of a public read), never shared.
     */
    private inner class Listings {
        private val map = HashMap<String, MutableList<Entry>>()
        suspend fun of(dir: ContentRef): List<Entry> = map.getOrPut(dir.value) {
            (try { source.listChildren(dir) } catch (e: CancellationException) { throw e } catch (_: Exception) { emptyList() }).toMutableList()
        }
        fun wrote(dir: ContentRef, e: Entry) { map[dir.value]?.let { l -> l.removeAll { it.name == e.name }; l += e } }
        fun deleted(dir: ContentRef, name: String) { map[dir.value]?.removeAll { it.name == name } }
    }

    /**
     * [t]'s slots on disk (the 2D box: `boxart2d/`, else the legacy `box2d/`; never `grid/` or `hero/`); the `.media`
     * folder is found once. [ArtRefs.heroShot]: the screenshot is a hero ([LoggedArt.hero]: its written-art origin is
     * SteamGridDB, or a moved hand-placed hero); not looked up without [heroes].
     */
    private suspend fun artOnDisk(t: ArtTarget, ls: Listings, heroes: Boolean = true): ArtRefs {
        val media = guard { source.findDir(home, t.mediaSegments) } ?: return ArtRefs()
        suspend fun entry(folder: String): Entry? = guard {
            val dir = source.child(media, folder)?.takeIf { it.isDirectory }?.ref ?: return@guard null
            filesIn(dir, t.base, ls).firstOrNull()
        }
        suspend fun slot(folder: String): String? = entry(folder)?.ref?.value
        val shot = entry(ArtSlot.SCREENSHOT.folder)
        val heroShot = heroes && shot != null &&
            guard { logged.hero((t.segments(ArtSlot.SCREENSHOT) + shot.name).joinToString("/")) } == true
        return ArtRefs(
            boxart = slot(ArtSlot.BOX_3D.folder), box2d = slot(ArtSlot.BOX_2D.folder) ?: slot(LEGACY_BOX2D_FOLDER),
            screenshot = shot?.ref?.value, wheel = slot(ArtSlot.WHEEL.folder), mix = slot(ArtSlot.MIX.folder), heroShot = heroShot,
        )
    }

    /**
     * [dir]'s files named `<base>.<png|jpg|jpeg>` (the extension in any case, as the scanner matches it), best first
     * (`.png`, `.jpg`, `.jpeg`), from the run's listing of [dir] ([Listings]). An empty or failed listing (a provider
     * fault) falls back to the exact lower-case names.
     */
    private suspend fun filesIn(dir: ContentRef, base: String, ls: Listings): List<Entry> {
        val listed = ls.of(dir)
        if (listed.isEmpty()) return DISK_EXTS.mapNotNull { ext -> source.child(dir, "$base.$ext")?.takeIf { !it.isDirectory } }
        return listed.filter { !it.isDirectory && extRank(it.name, base) >= 0 }.sortedBy { extRank(it.name, base) }
    }

    /** The ref of [base]'s best file in [dir] ([filesIn]), or null. */
    private suspend fun fileIn(dir: ContentRef, base: String, ls: Listings): String? = filesIn(dir, base, ls).firstOrNull()?.ref?.value

    /**
     * A ROM game: asks [known] in order (those in [stopped] left out) only for the kinds it lacks (box: each source's
     * box slots in its hit order; screenshot), each kind from the first hit that downloads and fits; a source that
     * throws [SourceStop] is [stop]ped and the next one asked. SteamGridDB's kinds are written into the standard
     * folders ([fileSlot]): a grid is a 2D box (asked with the box), a hero a screenshot (asked with the screenshot,
     * tried after a source's screenshots; a hero written is noted [NO_SCREENSHOT] on a DONE). A DONE names the sources
     * that wrote ([doneReason]); a PARTIAL names what is missing ([NO_BOX], [NO_SCREENSHOT]). Without [replace], art
     * already on disk counts (placed since the last scan: never overwritten, and reported when it completes the game).
     * DONE = every wanted kind written, PARTIAL = some, NOT_FOUND = none; null = nothing written, and every source
     * relevant to what it lacks (one with a slot it wants) stopped, this game or before (it stays waiting). Under
     * [replace], a box written removes the app's own (logged) files of the higher box slots this run did not write, so a
     * stale one cannot stay in front of it; hand-placed files stay.
     *
     * The extras ([extras], NX Redux's flow): with the 2D box on, a game being fetched also gets its 2D box (a 2D box hit,
     * else a grid) as its own download beside the 3D one (a 2D box written while the game has no box is its box, as
     * without the setting); with the wheel wanted ([ArtExtras.wantWheel]) its wheel, from the first source that has
     * one, a [ArtSource.wheelLast] source's only once every other has been asked (it is asked for the wheel only beside other slots in turn, and for
     * the wheel alone only when no other source had one). Neither is ever a reason to fetch a game nor counts toward
     * DONE. With Generate mix on, a game without a mix is fetched (only for what it lacks, so a game with a screenshot
     * and a box downloads neither again) and its mix made from what it has then ([makeMix]); a mix that cannot be made
     * never fails the game, unless the mix was all it lacked ([NO_MIX]). A game with no screenshot or box to make one
     * from (had or fetched in this run) wants none ([ArtHave.mixInput]); the app's own mix is made again when this
     * game's run wrote one of its inputs ([MIX_INPUTS]).
     */
    private suspend fun scrape(
        ref: LibraryGameRef, known: List<ArtSource>, stopped: Set<String>, replace: Boolean, wide: Boolean, ls: Listings,
        downloading: suspend () -> Unit, stop: (ArtSource, SourceStop) -> Unit,
        found: suspend (gamePath: String, options: List<ArtOption>) -> Unit,
    ): Pair<ItemStatus, String?>? {
        val target = guard { ArtPaths.target(ref.sourceId, ref.relativeDir, ref.fileName, ref.folderGame) }
            ?: return ItemStatus.ERROR to "No place for its art"
        val ex = extras()
        /** The engine's folder before this game's writes ([indexArt] tells the mirror's files from an extra's own). */
        val before = artOnDisk(target, ls)
        val onDisk = ArtHave.of(before)
        var needBox = replace || !ref.have.box
        var needShot = replace || !ref.have.screenshot
        // The extras: without Replace only when the index and the disk have none.
        var want2d = ex.box2d && (replace || !(ref.have.box2d || onDisk.box2d))
        var wantWheel = ex.wantWheel && (replace || !(ref.have.wheel || onDisk.wheel))
        if (!replace) {
            if (onDisk.box) needBox = false
            if (onDisk.screenshot) needShot = false
        }
        /**
         * A picked slot is only changed by another pick or Reset artwork. With the 2D box picked the game has its box;
         * under Replace a 3D box is still fetched beside it ([want3d], never counted), as the 3D box is its own slot.
         */
        var want3d = false
        var picks = picked(ref.gamePath)
        fun applyPicks(p: Set<ArtSlot>) {
            if (ArtSlot.SCREENSHOT in p) needShot = false
            if (ArtSlot.BOX_3D in p) { needBox = false; want3d = false }
            if (ArtSlot.BOX_2D in p) {
                if (needBox) { needBox = false; want3d = replace && ArtSlot.BOX_3D !in p }
                want2d = false
            }
            if (ArtSlot.WHEEL in p) wantWheel = false
        }
        applyPicks(picks)
        /** The picks at the game's start: a pick made since has changed the folder under this run's listings. */
        val picksAtStart = picks
        val mixOn = ex.mix && pixels != null
        // A missing mix (any under Replace), only for a game with a screenshot or a box to make it from, had or fetched
        // here.
        val wantMix = mixOn && (replace || !(ref.have.mix || onDisk.mix)) &&
            (ref.have.mixInput || onDisk.mixInput || needBox || needShot)
        if (!replace && !wide && !needBox && !needShot && !wantMix) {
            withContext(NonCancellable) { onArt(ref.gamePath, indexArt(ref, before, before)) }
            return ItemStatus.SKIPPED to HAS_ART
        }
        var wantedCount = listOf(needBox, needShot).count { it }
        var got = 0
        var offered = false
        var attempted = false
        var storageFailed = false
        var notified = false
        var asked = 0
        var stoppedHere = 0
        /** Relevant sources already stopped for this job before this game (not asked). */
        var stoppedBefore = 0
        /** The slots written for this game, by the folder written ([fileSlot]). */
        val wrote = HashSet<ArtSlot>()
        /** The fitted images written for this game, by the folder written (the mix's inputs: never read back from disk). */
        val fresh = HashMap<ArtSlot, ByteArray>()
        /** The screenshot written is a SteamGridDB hero, not a real screenshot. */
        var standInOnly = false
        /**
         * A hero may write the screenshot slot only when it holds no file or a hero (never over a real screenshot, the
         * app's or a hand-placed one, Replace included).
         */
        val heroMayWrite = before.screenshot == null || before.heroShot
        /** A hero was written this run: only a stand-in, so a later source's real screenshot still replaces it. */
        var heroHere = false
        /** A hero was offered for a screenshot kept as real ([heroMayWrite] false): the game is not missing one. */
        var heroWithheld = false
        /** The sources that wrote a file for this game, in order (the Done reason). */
        val writers = LinkedHashSet<String>()
        /** A [ArtSource.wheelLast] source's wheels, tried once every source has been asked: (its short label, its hits). */
        val laterWheels = ArrayList<Pair<String, List<ArtHit>>>()
        /** The [ArtSource.wheelLast] sources that offered wheels in turn (in [laterWheels]; not asked for the wheel alone). */
        val wheelsOffered = HashSet<String>()
        /**
         * The user's picks read again right before a write (a pick made while this game is fetched): a slot picked since
         * the check above is no longer wanted, as for a game picked before ([applyPicks]; a counted kind leaves the
         * count), and a hero written here is no longer replaced. [hits] less those whose file slot is picked now.
         */
        suspend fun pickedSince(hits: List<ArtHit>): List<ArtHit> {
            if (hits.isEmpty()) return hits
            val now = picked(ref.gamePath)
            if (now != picks) {
                val counted = listOf(needBox, needShot).count { it }
                applyPicks(now)
                wantedCount -= counted - listOf(needBox, needShot).count { it }
                if (ArtSlot.SCREENSHOT in now) heroHere = false
                picks = now
            }
            return hits.filter { it.slot.fileSlot !in now }
        }
        /**
         * The hit kind of the first hit that downloads, fits (to its kind's cap) and is written (in its [fileSlot]), or
         * null; a failed write stops (the job pauses). A hit whose file slot is picked ([pickedSince]) is never tried.
         */
        suspend fun firstWritten(all: List<ArtHit>, over: Boolean = replace): ArtSlot? {
            val hits = pickedSince(all)
            for (hit in hits) {
                val image = hit.bytes?.takeIf { it.isNotEmpty() } ?: download(hit.url) ?: continue
                val fitted = fitFor(hit.slot, image) ?: continue
                attempted = true
                // Read again right before the write (the download may have taken a while): a pick made meanwhile wins.
                val written = pickWrites.withLock {
                    if (pickedSince(listOf(hit)).isEmpty()) return null
                    writeSlot(target, hit.slot, fitted, hit.origin, over, ls)
                }
                if (written) {
                    wrote += hit.slot.fileSlot; fresh[hit.slot.fileSlot] = fitted.bytes; return hit.slot
                }
                storageFailed = true
                return null
            }
            return null
        }
        // A pause met mid-game (an image or a source without a connection): the files already written are reported first.
        var paused: JobPause? = null
        /** Every option slot hit seen ([OPTION_SLOTS]), for [onOptions]. */
        val options = ArrayList<ArtOption>()
        /** Records [hits] and reports the options so far ([found]) when they added any. */
        suspend fun record(hits: List<ArtHit>) { if (recordOptions(hits, options)) found(ref.gamePath, options.toList()) }
        /** The slots asked of each source in the usual flow (the wide pass does not ask them again). */
        val askedSlots = HashMap<String, Set<ArtSlot>>()
        /** The wide pass ran to its end: no pause, no storage failure, no source stopped that it needed. */
        var wideDone = false
        try {
            for (src in known) {
                val usual = buildSet {
                    if (needBox) addAll(src.slots intersect BOX_SLOTS)
                    if (want3d && ArtSlot.BOX_3D in src.slots) add(ArtSlot.BOX_3D)
                    if (want2d) addAll(src.slots intersect BOX_2D_SLOTS)
                    if (needShot) addAll(src.slots intersect SHOT_SLOTS)
                    else if (heroHere && ArtSlot.SCREENSHOT in src.slots) add(ArtSlot.SCREENSHOT)
                    // A wheel-last source: never asked in turn for the wheel alone (it would spend calls before the
                    // others), unless the wide pass asks it now anyway.
                    if (wantWheel && ArtSlot.WHEEL in src.slots && (!src.wheelLast || isNotEmpty() || wide)) add(ArtSlot.WHEEL)
                }
                // The wide pass's slots ride on this call (a second lookup could cost another request): their hits are
                // only recorded, the writes below stay gated by what the game lacks, and a source asked only for them
                // never counts toward the game's status.
                val wanted = if (wide) usual + (src.slots intersect OPTION_SLOTS) else usual
                if (wanted.isEmpty()) continue
                val counts = usual.isNotEmpty()
                if (src.id in stopped) { if (counts) stoppedBefore++; continue }
                if (counts) asked++
                val hits = try { src.find(ref, wanted) } catch (e: SourceStop) { stop(src, e); if (counts) stoppedHere++; continue }
                askedSlots[src.id] = wanted   // only once answered: a stop here leaves the wide pass to find it stopped
                if (hits.isEmpty()) continue
                record(hits)
                if (hits.none { it.slot in usual }) continue
                offered = true
                if (!notified) { notified = true; downloading() }
                // With the 2D box as its own download, the box here is the 3D one; the 2D one (a 2D box, else a grid) follows.
                val boxHits = if (want2d) hits.filter { it.slot == ArtSlot.BOX_3D } else hits.filter { it.slot in BOX_SLOTS }
                // A box pick made meanwhile ([pickedSince]) may have taken the box out of the count already.
                if (needBox) firstWritten(boxHits)?.let { slot ->
                    if (needBox) { needBox = false; got++ }
                    writers += src.shortLabel
                    if (replace) removeStaleHigherBoxes(target, slot.fileSlot, wrote, picks, ls)
                }
                // The 2D box picked: the 3D box beside it under Replace (nothing above it to remove).
                if (!storageFailed && want3d) firstWritten(hits.filter { it.slot == ArtSlot.BOX_3D })?.let { want3d = false; writers += src.shortLabel }
                if (!storageFailed && want2d) firstWritten(BOX_2D_SLOTS.flatMap { k -> hits.filter { it.slot == k } })?.let {
                    want2d = false; writers += src.shortLabel
                    if (needBox) { needBox = false; got++; if (replace) removeStaleHigherBoxes(target, ArtSlot.BOX_2D, wrote, picks, ls) }
                }
                // The screenshot: a screenshot hit, else a hero (the stand-in noted on a DONE; only where [heroMayWrite]).
                // A hero written here is replaced by a later source's real screenshot (over it: it is this run's own file).
                val shotHits = hits.filter { it.slot == ArtSlot.SCREENSHOT } +
                    (if (needShot && heroMayWrite) hits.filter { it.slot == ArtSlot.HERO } else emptyList())
                if (needShot && !heroMayWrite && hits.any { it.slot == ArtSlot.HERO }) heroWithheld = true
                if (!storageFailed && (needShot || heroHere) && shotHits.isNotEmpty()) {
                    firstWritten(shotHits, over = replace || heroHere)?.let { slot ->
                        if (needShot) { needShot = false; got++ }
                        heroHere = slot == ArtSlot.HERO; standInOnly = heroHere; writers += src.shortLabel
                    }
                }
                if (!storageFailed && wantWheel) {
                    val wheels = hits.filter { it.slot == ArtSlot.WHEEL }
                    if (src.wheelLast) { if (wheels.isNotEmpty()) { laterWheels += src.shortLabel to wheels; wheelsOffered += src.id } }
                    else if (firstWritten(wheels) != null) { wantWheel = false; writers += src.shortLabel }
                }
                if (storageFailed || (!needBox && !needShot && !want2d && !want3d && !wantWheel && !heroHere)) break
            }
            for ((label, wheels) in laterWheels) {
                if (storageFailed || !wantWheel) break
                if (firstWritten(wheels) != null) { wantWheel = false; writers += label }
            }
            // Still no wheel: each wheel-last source not yet heard from is asked for the wheel alone.
            for (src in known) {
                if (storageFailed || !wantWheel) break
                if (!src.wheelLast || ArtSlot.WHEEL !in src.slots || src.id in stopped || src.id in wheelsOffered) continue
                val hits = try { src.find(ref, setOf(ArtSlot.WHEEL)) } catch (e: SourceStop) { stop(src, e); continue }
                val wheels = hits.filter { it.slot == ArtSlot.WHEEL }
                if (wheels.isNotEmpty() && !notified) { notified = true; downloading() }
                if (firstWritten(wheels) != null) { wantWheel = false; writers += src.shortLabel }
            }
            // The wide pass: every option slot a source offers that it was not asked yet (a source the usual flow
            // skipped or stopped at), hits recorded only.
            if (wide && !storageFailed) {
                var complete = true
                for (src in known) {
                    val slots = (src.slots intersect OPTION_SLOTS) - askedSlots[src.id].orEmpty()
                    if (slots.isEmpty()) continue
                    if (src.id in stopped) { complete = false; continue }
                    record(try { src.find(ref, slots) } catch (e: SourceStop) { stop(src, e); complete = false; continue })
                }
                wideDone = complete
            }
        } catch (p: JobPause) { paused = p }
        if (options.isNotEmpty() || wide) withContext(NonCancellable) { onOptions(ref.gamePath, options, wide && wideDone) }
        // The mix from what the game has after the fetch, once nothing paused (a paused game is fetched again, its mix
        // with it). The app's own mix (logged) is made again when this run wrote one of its inputs, so one made from
        // partial art is not left stale; a hand-placed one only under Replace.
        val remakeMix = mixOn && !wantMix && wrote.any { it in MIX_INPUTS } && appMix(target, ls)
        val madeMix = (wantMix || remakeMix) && paused == null && !storageFailed && makeMix(ref, target, before, fresh, ls)
        if (madeMix) { attempted = true; wrote += ArtSlot.MIX }
        if (attempted) withContext(NonCancellable) {
            // Whatever is on disk after the writes (a failed replace may have removed the old file), an extra's own
            // media kept; listed again when a pick wrote meanwhile (this run's listings do not know its file).
            pickWrites.withLock {
                val now = picked(ref.gamePath)
                val art = indexArt(ref, before, artOnDisk(target, if (now != picksAtStart) Listings() else ls), picked = now - picksAtStart)
                onArt(ref.gamePath, art)
            }
        }
        paused?.let { throw it }
        if (storageFailed) throw JobPause(STORAGE)
        // Under Replace, a real screenshot kept because only a hero came is the game's screenshot, not a missing one.
        if (needShot && heroWithheld) { needShot = false; got++ }
        return when {
            got == wantedCount && (wantedCount > 0 || !wantMix || madeMix) -> ItemStatus.DONE to doneReason(writers, standInOnly)
            got > 0 || writers.isNotEmpty() -> ItemStatus.PARTIAL to (if (needBox) NO_BOX else if (needShot) NO_SCREENSHOT else NO_MIX)
            wantedCount == 0 -> ItemStatus.NOT_FOUND to NO_MIX   // only the mix was wanted, and none could be made
            stoppedHere == asked && stoppedHere + stoppedBefore > 0 -> null
            offered -> ItemStatus.NOT_FOUND to "No artwork"
            else -> ItemStatus.NOT_FOUND to null
        }
    }

    /** Adds [hits] in the option slots to [options] (links and thumbnails without a ScreenScraper login); true when any were. */
    private fun recordOptions(hits: List<ArtHit>, options: MutableList<ArtOption>): Boolean {
        val had = options.size
        for (h in hits) if (h.slot in OPTION_SLOTS) {
            options += ArtOption(
                h.slot, ScraperRequest.stripAuth(h.url), h.origin.source, h.origin.gameId, h.origin.assetId, h.note,
                thumb = h.thumb?.let(ScraperRequest::stripAuth),
            )
        }
        return options.size > had
    }

    /**
     * Writes the user's pick into [ref]'s file [slot] ([ArtSlot.fileSlot] of [option]'s hit kind): [image] fitted to
     * the hit kind's cap (a grid's, a hero's), replacing the slot's file in every extension, hand-placed or not (only
     * the user's pick may); a 2D box also drops the app's own legacy `box2d/` file. Logged with [option]'s origin (a
     * SteamGridDB hero in the screenshot slot is one: [ArtRefs.heroShot]); an extra folder's game gets it in its home
     * mirror, never in the folder. The app's own mix is made again when Generate mix is on and a mix input changed. The
     * art for the index ([indexArt], the pick winning its slot); null when the image cannot be read or the write failed.
     */
    suspend fun writePick(ref: LibraryGameRef, slot: ArtSlot, image: ByteArray, option: ArtOption): ArtRefs? {
        if (option.slot.fileSlot != slot) return null
        if (ref.android) return writeAndroidPick(ref, slot, image, option)
        val target = guard { ArtPaths.target(ref.sourceId, ref.relativeDir, ref.fileName, ref.folderGame) } ?: return null
        val fitted = fitFor(option.slot, image) ?: return null
        return pickWrites.withLock {
            val ls = Listings()
            val before = artOnDisk(target, ls)
            if (!writeSlot(target, option.slot, fitted, option.origin, replace = true, ls)) return@withLock null
            if (slot in MIX_INPUTS && extras().mix && pixels != null && appMix(target, ls)) makeMix(ref, target, before, mapOf(slot to fitted.bytes), ls)
            indexArt(ref, before, artOnDisk(target, ls), picked = setOf(slot))
        }
    }

    /**
     * An Android game's pick: [image] fitted to the hit kind's cap, stored in [slot] of [androidArt] (a SteamGridDB
     * screenshot marked a hero), then the console rebuilt ([onAndroidArt]). An empty [ArtRefs] (the console reads the
     * store, not the index); null for a slot the store has none of (the 3D box), or when the image cannot be read or
     * stored.
     */
    private suspend fun writeAndroidPick(ref: LibraryGameRef, slot: ArtSlot, image: ByteArray, option: ArtOption): ArtRefs? {
        val store = androidArt ?: return null
        if (slot !in AndroidArtStore.SLOTS) return null
        val fitted = fitFor(option.slot, image) ?: return null
        val hero = slot == ArtSlot.SCREENSHOT && option.source == SourceId.SGDB
        pickWrites.withLock { guard { store.write(ref.fileName, slot, fitted.bytes, hero) } } ?: return null
        withContext(NonCancellable) { onAndroidArt() }
        return ArtRefs()
    }

    /** An option's image (not paced): as the fetch downloads it, a miss is null and no connection pauses ([JobPause]). */
    suspend fun downloadImage(url: String): ByteArray? = download(url)

    /**
     * Makes and writes [ref]'s mix ([MixCompositor]) from its screenshot (never a hero: [ArtRefs.heroShot]), box (3D,
     * else 2D) and wheel: each
     * the image just written ([fresh]) or else the file it has (the home tree's, or an extra folder's own `.media`), a
     * missing one left out and one that cannot be read or decoded (out of memory included) left out with a [warn]. As
     * NX Redux, no mix without a screenshot or a box (the wheel alone makes none). Written as the other art (logged
     * first, through a part file). False, with a [warn], when it could not be made or saved.
     */
    private suspend fun makeMix(ref: LibraryGameRef, t: ArtTarget, before: ArtRefs, fresh: Map<ArtSlot, ByteArray>, ls: Listings): Boolean {
        val codec = pixels ?: return false
        val now = indexArt(ref, before, artOnDisk(t, ls))
        var warned = false
        suspend fun image(slot: ArtSlot, onDisk: String?): ArgbImage? {
            val bytes = fresh[slot] ?: onDisk?.let { v ->
                lenient { source.readHead(ContentRef(v), MIX_INPUT_MAX) }?.takeIf { it.isNotEmpty() && it.size < MIX_INPUT_MAX }
                    ?: run { warn("mix: could not read the ${slot.folder} of a ${ref.tag} game"); warned = true; null }
            } ?: return null
            return lenient { codec.decode(bytes) }
                ?: run { warn("mix: could not decode the ${slot.folder} of a ${ref.tag} game"); warned = true; null }
        }
        val shot = if (now.heroShot) null else image(ArtSlot.SCREENSHOT, now.screenshot)
        val box = image(ArtSlot.BOX_3D, now.boxart) ?: image(ArtSlot.BOX_2D, now.box2d)
        if (shot == null && box == null) { if (!warned) warn("mix: no screenshot or box for a ${ref.tag} game"); return false }
        val wheel = image(ArtSlot.WHEEL, now.wheel)
        val png = lenient { MixCompositor.create(shot, box, wheel)?.let { codec.encodePng(it) } }
        if (png == null) { warn("mix: could not make it for a ${ref.tag} game"); return false }
        return try {
            writeSlot(t, ArtSlot.MIX, FittedImage(png, ArtEncoding.PNG), ArtOrigin(MIX_ORIGIN), replace = true, ls)
                .also { if (!it) warn("mix: could not save it for a ${ref.tag} game") }
        } catch (e: JobPause) { throw e } catch (e: CancellationException) { throw e } catch (_: Exception) {
            warn("mix: could not save it for a ${ref.tag} game"); false
        }
    }

    /** [t]'s mix on disk is the app's own (its best file is in the written-art log), not one the user placed. */
    private suspend fun appMix(t: ArtTarget, ls: Listings): Boolean = guard {
        val segments = t.segments(ArtSlot.MIX)
        val dir = source.findDir(home, segments) ?: return@guard null
        val e = filesIn(dir, t.base, ls).firstOrNull() ?: return@guard null
        logged.contains((segments + e.name).joinToString("/"))
    } ?: false

    /**
     * A ROM's art as the host saw it ([ArtHave.complete], the mix needed when Generate mix is on and a mix can be made);
     * an Android game's stored screenshot as the store has it.
     */
    private fun hasArt(ref: LibraryGameRef): Boolean =
        if (ref.android) guard { androidArt?.art(ref.fileName, ArtSlot.SCREENSHOT) } != null
        else ref.have.copy(needMix = extras().mix && pixels != null).complete

    /** A ROM with a box and a screenshot whose mix is all it lacks ([hasArt] false): made without asking any source. */
    private fun lacksOnlyMix(ref: LibraryGameRef): Boolean = !ref.android && ref.have.box && ref.have.screenshot && !hasArt(ref)

    /**
     * An Android game: ScreenScraper's lookup ([ScreenScraperSource.androidScreenshot]), its `ss` screenshot fitted to
     * the screenshot cap, stored by app id, then the console refreshed; written only when the store has no screenshot
     * (or under [replace]) and the user has not picked one. The same lookup's screenshots, 2D boxes and wheels are its
     * options ([onOptions]); [wide] also asks SteamGridDB by the app's label for its grids, heroes and logos (only
     * recorded; never in a bulk fetch: [SteamGridDbSource.knows] leaves Android out). A failed store write is this
     * game's error (app-private storage: not the home tree's pause). Null when ScreenScraper stopped ([stop]).
     */
    private suspend fun scrapeAndroid(
        ref: LibraryGameRef, stopped: Set<String>, replace: Boolean, wide: Boolean,
        downloading: suspend () -> Unit, stop: (ArtSource, SourceStop) -> Unit,
        found: suspend (gamePath: String, options: List<ArtOption>) -> Unit,
    ): Pair<ItemStatus, String?>? {
        val store = androidArt ?: return ItemStatus.SKIPPED to NO_SOURCE
        val ss = ss ?: return ItemStatus.SKIPPED to NO_SOURCE
        val options = ArrayList<ArtOption>()
        var wideDone = false
        var paused: JobPause? = null
        var result: Pair<ItemStatus, String?>? = null
        /** Records [hits] and reports the options so far ([found]) when they added any. */
        suspend fun record(hits: List<ArtHit>) { if (recordOptions(hits, options)) found(ref.gamePath, options.toList()) }
        try {
            val hit = try { ss.androidScreenshot(ref.name) } catch (e: SourceStop) { stop(ss, e); return null }
            when (hit) {
                is AndroidHit.Screenshot -> record(ss.androidHits(hit.medias, hit.gameId))
                is AndroidHit.NoScreenshot -> record(ss.androidHits(hit.medias, hit.gameId))
                AndroidHit.None -> {}
            }
            if (wide) {
                val sg = sgdb?.takeIf { it.enabled }
                wideDone = when {
                    sg == null -> true
                    sg.id in stopped -> false
                    else -> try { record(sg.find(ref, sg.slots)); true } catch (e: SourceStop) { stop(sg, e); false }
                }
            }
            val picked = ArtSlot.SCREENSHOT in picked(ref.gamePath)
            val has = guard { store.art(ref.fileName, ArtSlot.SCREENSHOT) } != null
            result = when {
                hit == AndroidHit.None -> ItemStatus.NOT_FOUND to null
                picked || (has && !replace) -> ItemStatus.DONE to null   // nothing to write: the stored one stays
                hit is AndroidHit.Screenshot -> writeAndroidShot(store, ref, hit.url, downloading, ss)
                else -> ItemStatus.NOT_FOUND to "No artwork"
            }
        } catch (p: JobPause) { paused = p }
        if (options.isNotEmpty() || wide) withContext(NonCancellable) { onOptions(ref.gamePath, options, wide && wideDone && paused == null) }
        paused?.let { throw it }
        return result
    }

    /** Downloads [url], fits it to the screenshot cap and stores it as [ref]'s screenshot (not a hero), then [onAndroidArt]. */
    private suspend fun writeAndroidShot(
        store: AndroidArtStore, ref: LibraryGameRef, url: String, downloading: suspend () -> Unit, ss: ScreenScraperSource,
    ): Pair<ItemStatus, String?> {
        downloading()
        val image = download(url) ?: return ItemStatus.ERROR to "Couldn't download the art"
        val fitted = guard { fitter.fit(image, ArtCaps.of(ArtSlot.SCREENSHOT)) } ?: return ItemStatus.ERROR to "Couldn't make the art"
        // A pick made while this game was fetched wins its slot (read under the lock its write takes).
        pickWrites.withLock {
            if (ArtSlot.SCREENSHOT in picked(ref.gamePath)) return ItemStatus.DONE to null
            guard { store.write(ref.fileName, ArtSlot.SCREENSHOT, fitted.bytes) }
        } ?: return ItemStatus.ERROR to "Couldn't save the art"
        withContext(NonCancellable) { onAndroidArt() }
        return ItemStatus.DONE to doneReason(listOf(ss.shortLabel), standInOnly = false)
    }

    /**
     * An image (not paced): the body of a 200, else null (that art type is skipped). No answer (-1) is tried again once
     * after [OFFLINE_RETRY_MS], then pauses the job ([NO_CONNECTION]): the game stays waiting instead of "No artwork". A
     * 429 or 5xx is tried again once after [BUSY_RETRY_MS]. A provider exception is a miss.
     */
    private suspend fun download(url: String): ByteArray? {
        var offline = false
        var busy = false
        while (true) {
            val r = guard { http.get(url) } ?: return null
            when {
                r.status == -1 -> { if (offline) throw JobPause(NO_CONNECTION); offline = true; sleep(OFFLINE_RETRY_MS) }
                r.status == 429 || r.status >= 500 -> { if (busy) return null; busy = true; sleep(BUSY_RETRY_MS) }
                r.status == 200 -> return r.body?.takeIf { it.isNotEmpty() }
                else -> return null
            }
        }
    }

    /**
     * Writes the file of a [kind] hit in its folder ([fileSlot]: a grid is a 2D box, a hero a screenshot) in [img]'s
     * encoding; logs its path (with [origin]) first, so a write cut short is still
     * resettable. Without [replace] a slot that already has a file (any extension, any case) is never written (the
     * has-art rule keeps such a slot from being asked for; this holds it here too) and counts as written. Under [replace],
     * a successful write removes the slot's file in the other extensions (and cases), so a stale one cannot win the scan.
     * False when the write failed.
     */
    private suspend fun writeSlot(t: ArtTarget, kind: ArtSlot, img: FittedImage, origin: ArtOrigin, replace: Boolean, ls: Listings): Boolean {
        val slot = kind.fileSlot
        val segments = t.segments(slot)
        if (!replace && guard { source.findDir(home, segments)?.let { fileIn(it, t.base, ls) } } != null) return true
        val name = t.name(img.encoding)
        onWritten(listOf(t.path(slot, img.encoding) to origin))
        val (dir, written) = writeOne(segments, name, img.bytes) ?: return false
        ls.wrote(dir, written)
        if (replace) guard {
            for (e in filesIn(dir, t.base, ls)) if (e.name != name && source.delete(dir, e.name)) ls.deleted(dir, e.name)
        }
        // A 2D box replaced in `boxart2d/`: the app's own file in the legacy `box2d/` goes too (hand-placed ones stay).
        if (replace && slot == ArtSlot.BOX_2D) removeLogged(t.mediaSegments + LEGACY_BOX2D_FOLDER, t.base, ls)
        return true
    }

    /**
     * Under Replace, after a box was written in [slot]: the higher box slots ([BOX_CHAIN]) this run did not write lose
     * the files the app wrote there (in the written-art log; dropped from it; the 2D box's legacy `box2d/` too), so the
     * new box is the one drawn. A file the log does not have (hand-placed) and a [picked] slot's are never removed.
     */
    private suspend fun removeStaleHigherBoxes(t: ArtTarget, slot: ArtSlot, wrote: Set<ArtSlot>, picked: Set<ArtSlot>, ls: Listings) {
        for (higher in BOX_CHAIN.takeWhile { it != slot }) {
            if (higher in wrote || higher in picked) continue
            val folders = if (higher == ArtSlot.BOX_2D) listOf(t.segments(higher), t.mediaSegments + LEGACY_BOX2D_FOLDER) else listOf(t.segments(higher))
            for (segments in folders) removeLogged(segments, t.base, ls)
        }
    }

    /** Deletes [base]'s files under [segments] that the written-art log has (dropping them from it); others stay. */
    private suspend fun removeLogged(segments: List<String>, base: String, ls: Listings) {
        guard {
            val dir = source.findDir(home, segments) ?: return@guard null
            for (e in filesIn(dir, base, ls)) {
                val path = (segments + e.name).joinToString("/")
                if (logged.contains(path) && source.delete(dir, e.name)) { ls.deleted(dir, e.name); logged.remove(path) }
            }
        }
    }

    /** Writes [name] under [segments] (created as needed); its folder and its entry, or null when the write failed. */
    private suspend fun writeOne(segments: List<String>, name: String, bytes: ByteArray): Pair<ContentRef, Entry>? = guard {
        val dir = source.ensurePath(home, segments) ?: return@guard null
        if (!source.writeBytes(dir, name, bytes)) return@guard null
        source.child(dir, name)?.takeIf { !it.isDirectory }?.let { dir to it }
    }

    /**
     * [image] made ready to write as [slot]'s art: a downloaded mix is placed whole on the mix canvas
     * ([MixCompositor.onCanvas], PNG; null without a pixel codec or on any failure), any other slot is fitted by [fitter].
     */
    private suspend fun fitFor(slot: ArtSlot, image: ByteArray): FittedImage? {
        if (slot != ArtSlot.MIX) return guard { fitter.fit(image, ArtCaps.of(slot)) }
        val codec = pixels ?: return null
        return lenient { codec.decode(image)?.let { codec.encodePng(MixCompositor.onCanvas(it)) } }?.let { FittedImage(it, ArtEncoding.PNG) }
    }

    /**
     * As [guard], and an Error (out of memory: a large image read or decoded for the mix) is null too, so it never
     * escapes the game mid-job. Cancellation propagates.
     */
    private inline fun <T> lenient(block: () -> T?): T? =
        try { block() } catch (e: CancellationException) { throw e } catch (e: JobPause) { throw e } catch (_: Throwable) { null }

    /** Runs [block]; a provider or platform exception is null. Cancellation propagates. */
    private inline fun <T> guard(block: () -> T?): T? = try { block() } catch (e: CancellationException) { throw e } catch (e: JobPause) { throw e } catch (e: SourceStop) { throw e } catch (_: Exception) { null }
}
