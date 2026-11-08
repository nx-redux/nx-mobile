package com.nxredux.mobile.library

import com.nxredux.mobile.runner.Console
import com.nxredux.mobile.runner.ContentRef
import com.nxredux.mobile.runner.CoreDescriptor
import com.nxredux.mobile.runner.CoreRegistry
import com.nxredux.mobile.scraper.ArtSlot
import com.nxredux.mobile.scraper.LegacyArtMove
import com.nxredux.mobile.settings.ExtraFolder
import com.nxredux.mobile.storage.FileRefs
import com.nxredux.mobile.storage.GamePath
import com.nxredux.mobile.storage.GameRef
import com.nxredux.mobile.storage.MemorySidecarStore
import com.nxredux.mobile.storage.Recents
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * One playable file found by the walk, before its folder's lookups. [relativeDir] is "Roms/<System (TAG)>" for the home
 * ("Roms/<System (TAG)>/D" for a disc folder), "" or "a/b" in an extra folder. [lookupFolder]/[lookupDir] are where its
 * art and map.txt name live: its own folder, or for a disc folder (home or extra) the folder around it.
 */
internal data class IndexedFile(
    val entry: Entry,
    val folder: ContentRef,
    val sourceId: String,
    val relativeDir: String,
    val lookupFolder: ContentRef = folder,
    val lookupDir: String = relativeDir,
)

/** One console list, built from the index by [toSystems]: every game across the home and the extra folders that resolved to [tag]. */
data class SystemFolder(
    val displayName: String,
    val tag: String,
    val core: CoreDescriptor?,
    val games: List<GameEntry>,
    val artRef: ContentRef? = null,
    val bgRef: ContentRef? = null,   // <home>/Roms/<system>/.media/bg.png (null -> app default)
    val folderName: String = "",     // the home skeleton folder's name, e.g. "Game Boy (GB)", when one exists
    val unassigned: Boolean = false, // the "Unassigned" pseudo-console: files no rule could place
    val android: Boolean = false,    // the Android games console: installed apps, not ROMs (never in the index)
) {
    val gameCount: Int get() = games.size
}

data class GameEntry(
    val ref: ContentRef,
    val fileName: String,
    val shownName: String,
    val boxartRef: ContentRef? = null,     // .media/boxart/<name> (3D box)
    val screenshotRef: ContentRef? = null, // .media/screenshot/<name>
    val box2dRef: ContentRef? = null,      // .media/boxart2d/<name>, else the legacy .media/box2d/<name> (2D front cover)
    val wheelRef: ContentRef? = null,      // .media/wheel/<name> (known, not shown)
    val mixRef: ContentRef? = null,        // .media/mix/<name> (known, not shown: Mix's has-art rule)
    val heroShot: Boolean = false,         // the screenshot is a SteamGridDB hero ([IndexedGame.heroShot])
    val folder: ContentRef,
    val sourceId: String,
    val relativeDir: String,
    val lookupDir: String = relativeDir,   // where its art and map.txt name live (a disc folder's: the folder around it)
    val subtitle: String? = null,          // Unassigned list: where the file is
    val firstSeen: Long? = null,          // Home's New shelf ([IndexedGame.firstSeen])
    val hiddenCopies: List<GameRef> = emptyList(),   // extra copies hidden behind this home game ([IndexedGame.hiddenCopies])
)

/** The game's line in recent.txt, shortcuts.txt, hidden.txt and collection files. */
val GameEntry.path: String get() = GamePath.of(GameRef(sourceId, relativeDir, fileName))

/** Every line that opens this game: its [path] and its hidden copies' ([GameEntry.hiddenCopies]). */
val GameEntry.lines: Set<String> get() = if (hiddenCopies.isEmpty()) setOf(path) else setOf(path) + hiddenCopies.map(GamePath::of)

fun GameEntry.matches(ref: GameRef): Boolean =
    sourceId == ref.sourceId && relativeDir == ref.relativeDir && fileName == ref.fileName

class LibraryScanner(
    private val source: RomsSource,
    private val sidecars: Sidecars = Sidecars(source, MemorySidecarStore()) { null },
    private val arcadeSets: () -> ArcadeSets = { ArcadeSets.EMPTY },
    private val naomiSets: () -> NaomiSets = { NaomiSets.EMPTY },
    /**
     * Where [scan], [scanOutsideRoms] and [readArt] run: the app passes its compute dispatcher, so the work between tree
     * reads (resolving each file, parsing sheets, `systems.txt` and `map.txt`, building the art maps and the lists) never
     * lands on the main thread, while each read still hops to the source's own I/O context. Null keeps it all on the
     * caller's context (the set tables still load on `Dispatchers.Default`).
     */
    private val compute: CoroutineContext? = null,
    /** A CD CHD's sectors for the disc probe (Android's libchdr); null places only GD and DVD CHDs. */
    private val chdSectors: com.nxredux.mobile.library.disc.ChdSectors? = null,
    /**
     * The Change art picks: per game ([GameEntry.path]) its picked file slots ([ArtSlot.SCREENSHOT], [ArtSlot.BOX_2D],
     * [ArtSlot.BOX_3D], [ArtSlot.WHEEL], [ArtSlot.MIX]), read once per [scan] and [readArt]: an extra folder's game shows a picked
     * slot's file from its home mirror (where the pick is written) over the folder's own.
     */
    private val picks: suspend () -> Map<String, Set<ArtSlot>> = { emptyMap() },
    /**
     * The written-art log's SteamGridDB hero screenshots, as home-relative file paths
     * (`Roms/GB/.media/screenshot/Tetris.jpg`): a game whose chosen screenshot is the home tree's or its mirror's file at
     * such a path is [ArtRefs.heroShot]. Read at most once per [scan] and [readArt] (again after a [legacyMove] changed a folder).
     */
    private val heroShots: suspend () -> Set<String> = { emptySet() },
    /**
     * The one-time `grid/` and `hero/` move, run on each `.media` listing the scan and [readArt] read (the home tree's,
     * the mirrors' and an extra folder's own) before it is mapped. A function, so the app can hand over a mover built
     * after the scanner; null runs none.
     */
    private val legacyMove: suspend () -> LegacyArtMove? = { null },
) {
    private val art = ArtworkResolver(source)

    /** The typed `.media` maps the last scan and [readArt] read, for [readArt]'s `reuse` ([ArtworkResolver.SubArtMaps]). */
    private val artMaps = ArtworkResolver.SubArtMaps()

    private class HomeFolder(val folderName: String, val displayName: String, val artRef: ContentRef?, val bgRef: ContentRef?)

    /** One folder's typed `.media` art (by base name, per [ArtworkResolver.TYPED] folder) and `map.txt` aliases, looked up once per scan. */
    private class FolderLookup(
        val box3d: Map<String, ContentRef>, val box2d: Map<String, ContentRef>, val screenshot: Map<String, ContentRef>,
        val names: Map<String, String>,
        val wheel: Map<String, ContentRef> = emptyMap(), val mix: Map<String, ContentRef> = emptyMap(),
        /** The names in [screenshot] whose file is a SteamGridDB hero ([heroShots]); an extra folder's own files never are. */
        val heroes: Set<String> = emptySet(),
        /** An extra folder's home mirror alone ([withMirror]), for a game's picked slots. */
        val mirror: FolderLookup? = null,
    ) {
        /**
         * A game's art of every kind: the file named for [key] (a disc folder's name, else the file's base name), else
         * for [base]. The one place an art kind is mapped: the scan and [readArt] both read it. [picked]: the file slots
         * that are a Change art pick, each the mirror's first.
         */
        fun artFor(key: String, base: String, picked: Set<ArtSlot> = emptySet()): ArtRefs {
            fun Map<String, ContentRef>.of(): String? = (this[key] ?: this[base])?.value
            fun Map<String, ContentRef>.nameOf(): String? = if (key in this) key else base.takeIf { it in this }
            val m = mirror?.takeIf { picked.isNotEmpty() }
            fun slot(s: ArtSlot, mine: Map<String, ContentRef>, theirs: Map<String, ContentRef>?): String? =
                (if (s in picked) theirs?.of() else null) ?: mine.of()
            // The screenshot and whether it is a hero, from the same map and name.
            val mirrorName = if (ArtSlot.SCREENSHOT in picked) m?.screenshot?.nameOf() else null
            val shotName = if (mirrorName != null) null else screenshot.nameOf()
            return ArtRefs(
                boxart = slot(ArtSlot.BOX_3D, box3d, m?.box3d), box2d = slot(ArtSlot.BOX_2D, box2d, m?.box2d),
                screenshot = mirrorName?.let { m?.screenshot?.get(it) }?.value ?: shotName?.let { screenshot[it] }?.value,
                wheel = slot(ArtSlot.WHEEL, wheel, m?.wheel), mix = slot(ArtSlot.MIX, mix, m?.mix),
                heroShot = if (mirrorName != null) mirrorName in m?.heroes.orEmpty() else shotName != null && shotName in heroes,
            )
        }
    }

    /**
     * The whole library: the home's `Roms/<System (TAG)>/` folders (a file's `systems.txt` entry may move it to another
     * tag) plus every extra folder resolved in place with [defaultTags], merged by tag, each game filled from its
     * folder's `.media` and `map.txt`. Empty and coreless systems are kept; Unassigned is last when not empty. An extra
     * folder that cannot be listed is reported to [onExtraFailed], skipped and left out of [LibraryIndex.extras]; one
     * that cannot be listed because its storage is not mounted ([RomsSource.isOffline]) is not a failure: it stays in
     * [LibraryIndex.extras] with no games and is recorded in [LibraryIndex.offline]. An extra [previous] recorded offline
     * whose volume is mounted again may still fail as gone for a moment (the provider refreshes its roots after the
     * volume reports mounted): it is listed again after [REMOUNT_RETRY_MILLIS], and when that fails too it is kept as
     * offline once more ([LibraryIndex.lateMount]); a second such scan reports it. A listing the provider hides
     * ([ListingUnavailable]) fails the whole scan: the caller keeps its index. Each folder is listed once per scan
     * ([ScanReads]); the whole walk runs on [compute] (the source's reads switch to its I/O context themselves). Each disc
     * sheet's and peeked zip's head is read once and kept in [LibraryIndex.heads]: a later scan reuses it while the file's
     * size and modified time are unchanged ([HeadReads]).
     */
    suspend fun scan(
        home: ContentRef,
        extras: List<ExtraFolder>,
        defaultTags: Map<String, String>,
        previous: LibraryIndex? = null,
        onExtraFailed: suspend (ExtraFolder, Throwable) -> Unit = { _, _ -> },
    ): LibraryIndex = onCompute {
        val byTag = LinkedHashMap<String, MutableList<IndexedFile>>()
        val unassigned = ArrayList<IndexedFile>()
        val homeFolders = HashMap<String, HomeFolder>()
        val reads = ScanReads(source, sidecars, home)
        artMaps.clear()   // refilled with every typed folder this scan lists
        val heads = HeadReads(source, previous?.heads.orEmpty())
        indexHome(reads, heads, byTag, homeFolders, defaultTags)
        val probes = DiscProbes(com.nxredux.mobile.library.disc.DiscProbe(source, chdSectors), previous?.probes.orEmpty())
        val loose = LooseResolver(source, reads, { console -> console.defaultTagFor(defaultTags[console.id]) }, probes::consoleOf, heads)
        val scanned = ArrayList<ExtraFolder>()
        val offline = ArrayList<String>()
        val lateMount = ArrayList<String>()
        suspend fun list(extra: ExtraFolder): Result<List<LooseFile>> =
            try {
                // Revoked access can list a file extra as empty rather than fail: offline is decided before any listing is trusted.
                if (FileRefs.isFile(extra.ref) && source.isOffline(extra.ref)) throw IllegalStateException("File folder is offline")
                Result.success(loose.scan(extra.ref, extra.id))
            } catch (e: CancellationException) { throw e } catch (e: ListingUnavailable) { throw e } catch (e: Throwable) { Result.failure(e) }
        for (extra in extras) {
            var listed = list(extra)
            val remounted = listed.isFailure && previous != null && extra.id in previous.offline && extra.id !in previous.lateMount
            if (remounted && !source.isOffline(extra.ref)) {
                delay(REMOUNT_RETRY_MILLIS)
                listed = list(extra)
            }
            val files = listed.getOrNull()
            if (files == null) {
                when {
                    source.isOffline(extra.ref) -> { scanned += extra; offline += extra.id }
                    remounted -> { scanned += extra; offline += extra.id; lateMount += extra.id }   // not back yet: once
                    else -> onExtraFailed(extra, listed.exceptionOrNull()!!)
                }
                continue
            }
            // Only a root that listed nothing can be a hidden one: an extra whose games moved away but whose folders stay
            // lists those folders, so it is a really empty extra (the listing is cached, so this costs no read).
            if (files.isEmpty() && previous != null && reads.list(extra.ref).isEmpty()) hiddenRootCheck(extra, previous)
            scanned += extra
            for (f in files) {
                val ix = IndexedFile(f.entry, f.folder, extra.id, f.relativeDir, f.lookupFolder, f.lookupDir)
                if (f.tag == null) unassigned += ix else byTag.getOrPut(f.tag) { ArrayList() } += ix
            }
        }
        val lookups = HashMap<ContentRef, FolderLookup>()
        val sets = ScanSets()
        val picked = pickedGames()
        val heroes = HeroShots()
        val extraNames = scanned.associate { it.id to source.displayName(it.ref) }
        val systems = ArrayList<IndexedSystem>()
        for ((tag, files) in byTag) {
            val hf = homeFolders[CoreRegistry.consoleTag(tag)]   // every emulator's games sit in the console's folders
            systems += IndexedSystem(
                tag = tag,
                displayName = hf?.displayName ?: CoreRegistry.consoleOf(tag)?.displayName ?: tag,
                folderName = hf?.folderName ?: "",
                art = hf?.artRef?.value,
                bg = hf?.bgRef?.value,
                games = indexGames(reads, files, lookups, tag, sets, extraNames, picked, heroes),
            )
        }
        if (unassigned.isNotEmpty()) systems += IndexedSystem(tag = "", displayName = UNASSIGNED, unassigned = true, games = indexGames(reads, unassigned, lookups, null, sets, extraNames, picked, heroes))
        LibraryIndex(
            LibraryIndex.VERSION, home.value, scanned.map { IndexedExtra(it.id, it.ref.value) }, defaultTags, systems.inLibraryOrder(), offline, lateMount,
            probes = probes.used, heads = heads.used,
        )
    }

    /** [block] on [compute] (the caller's context when null): see [compute]. */
    private suspend fun <T> onCompute(block: suspend () -> T): T = withContext(compute ?: EmptyCoroutineContext) { block() }

    /**
     * The home's files outside `Roms/` (and the home's other own folders, a card root's system ones: [HOME_SKIP]), resolved as an extra folder's
     * are: console from folder names, then file type, then a disc's contents; a tag of null is a file nothing places.
     * Setup moves the placed ones into `Roms/` ([RomMover.planLoose]); they are never listed where they lie.
     */
    suspend fun scanOutsideRoms(home: ContentRef, defaultTags: Map<String, String>): List<LooseFile> = onCompute {
        val reads = ScanReads(source, sidecars, null)
        val probes = DiscProbes(com.nxredux.mobile.library.disc.DiscProbe(source, chdSectors), emptyList())
        val defaultTag = { console: Console -> console.defaultTagFor(defaultTags[console.id]) }
        LooseResolver(source, reads, defaultTag, probes::consoleOf).scan(home, OUTSIDE_SOURCE, skipTop = HOME_SKIP).map { f ->
            if (f.tag == null || outsidePlaced(f, defaultTag)) f else f.copy(tag = null)
        }
    }

    /**
     * Whether an outside file's console holds, so setup may move it: inside a folder that names a console it must be that
     * console's (a readme.md in "Game Boy Advance" is no Mega Drive game); anywhere, a `.md` placed by its extension alone
     * is taken for a text file. An extra folder lists such files as they are; a move would carry them off.
     */
    private fun outsidePlaced(f: LooseFile, defaultTag: (Console) -> String): Boolean {
        val console = CoreRegistry.consoleOf(f.tag ?: return false) ?: return true
        val named = Resolution.FolderHints.of(f.relativeDir.split('/').filter { it.isNotEmpty() }.reversed(), defaultTag).tags
            .mapNotNull { CoreRegistry.consoleOf(it) }
        if (named.isNotEmpty()) return console in named
        return Names.extensionOf(f.entry.name) != "md"
    }

    /**
     * An extra whose root listed nothing at all while [previous] had games in it: its root may be hidden by the provider (a
     * tree root has no parent to check). One previous game's first path segment is looked up by name; still there means the listing
     * lied, so the scan fails with [ListingUnavailable] and the caller keeps the old index.
     */
    private suspend fun hiddenRootCheck(extra: ExtraFolder, previous: LibraryIndex) {
        val game = previous.systems.asSequence().flatMap { it.games.asSequence() }.firstOrNull { it.source == extra.id } ?: return
        val segment = game.dir.substringBefore('/').ifEmpty { game.file }
        if (source.child(extra.ref, segment) != null) throw ListingUnavailable(extra.ref.value)
    }

    private suspend fun indexHome(reads: ScanReads, heads: HeadReads, byTag: MutableMap<String, MutableList<IndexedFile>>, homeFolders: MutableMap<String, HomeFolder>, defaultTags: Map<String, String>) {
        val roms = reads.home?.dir(listOf("Roms")) ?: return
        val systemFolders = reads.list(roms)
        val rootArt = art.mediaMap(systemFolders, reads::list)
        for (entry in systemFolders) {
            if (!entry.isDirectory || Names.isHidden(entry.name)) continue
            val (display, folderTag) = Names.parseSystemFolder(entry.name) ?: continue
            // A folder's tag names only its console (a tag the handheld shares across consoles, by the display name):
            // its games run the emulator Default emulators picks for that console. A tag of no console stands as it is.
            val console = CoreRegistry.consoleOfFolder(display, folderTag)
            val own = console?.defaultTagFor(defaultTags[console.id]) ?: folderTag.uppercase()
            val children = reads.list(entry.ref)
            val relativeDir = "Roms/${entry.name}"
            // Several folders can hold one console: the one named like the console gives the system its name and art,
            // the one named by the console tag among those, else the first found.
            val key = console?.defaultTag ?: own
            fun rank(name: String, shown: String) =
                (if (shown == console?.displayName) 0 else 2) + (if (Names.parseSystemFolder(name)?.second?.uppercase() == key) 0 else 1)
            val known = homeFolders[key]
            if (known == null || rank(entry.name, display) < rank(known.folderName, known.displayName)) {
                homeFolders[key] = HomeFolder(entry.name, display, art.artFor(rootArt, entry.name, true), art.mediaMap(children, reads::list)["bg"])
            }
            byTag.getOrPut(own) { ArrayList() }   // an empty skeleton folder is still indexed (count 0)
            val sidecar = reads.sidecars.read(Recents.HOME, relativeDir, children)
            val files = children.filter { !it.isDirectory && !Names.isHidden(it.name) }
            val hidden = DiscSheet.hiddenNamed(files, heads::sheetNames)
            val discs = CoreRegistry.forTag(own)?.extensions?.let { "m3u" in it || "cue" in it || "gdi" in it } == true
            for (c in children) {
                if (Names.isHidden(c.name)) continue
                if (c.isDirectory) {
                    if (discs) discFolder(reads, c, entry.ref, relativeDir, own, console)?.let { (t, f) -> byTag.getOrPut(t) { ArrayList() } += f }
                    continue
                }
                if (c.name.lowercase() in hidden) continue
                val fileTag = homeTag(c.name, sidecar[c.name], own, console) ?: continue
                byTag.getOrPut(fileTag) { ArrayList() } += IndexedFile(c, entry.ref, Recents.HOME, relativeDir)
            }
        }
    }

    /**
     * The tag a home file lists under: its sidecar tag (when known and one of [console]'s, [CoreRegistry.tagOn]: a
     * handheld's GPGX in a Master System folder is GPGX_SMS; another console's is ignored), else [ownTag], the emulator
     * Default emulators picks for its folder's console (whichever of the console's tags names the folder). A folder of
     * no console takes any known sidecar tag. An archive keeps that tag: it is unpacked at launch, where its inner file
     * is checked against the core. A bare file that tag's core cannot run lists under the first tag of the same console
     * whose core can (a `.gba` in "Game Boy (GB)"). A tag with no core keeps every file. Null: nothing there runs it,
     * so it is not listed.
     */
    private fun homeTag(fileName: String, sidecarTag: String?, ownTag: String, console: Console?): String? {
        val side = sidecarTag?.takeIf { CoreRegistry.forTag(it) != null }
        val tag = (if (console == null) side else side?.let { CoreRegistry.tagOn(console, it) }) ?: ownTag
        val core = CoreRegistry.forTag(tag) ?: return tag
        if (Names.isArchive(fileName)) return tag
        val ext = Names.extensionOf(fileName)
        if (ext in core.extensions) return tag
        return CoreRegistry.consoleOf(tag)?.tags?.firstOrNull { t -> CoreRegistry.forTag(t)?.let { ext in it.extensions } == true }
    }

    /**
     * NX Redux's disc folder: a subfolder `D/` of a system folder holding `D.m3u` (else `D.cue`, else `D.gdi`, any case;
     * [DiscSheet.folderSheet]) is that one game. It is listed from `D/` (so the launch finds its discs and `D/systems.txt`
     * holds its tag) with art and name from the system folder, keyed by `D`. Null when `D/` holds no such sheet or no tag
     * of the console runs it.
     */
    private suspend fun discFolder(reads: ScanReads, dir: Entry, systemFolder: ContentRef, systemDir: String, ownTag: String, console: Console?): Pair<String, IndexedFile>? {
        val children = reads.list(dir.ref)
        val sheet = DiscSheet.folderSheet(dir.name, children) ?: return null
        val dirPath = "$systemDir/${dir.name}"
        val tag = homeTag(sheet.name, reads.sidecars.read(Recents.HOME, dirPath, children)[sheet.name], ownTag, console) ?: return null
        return tag to IndexedFile(sheet, dir.ref, Recents.HOME, dirPath, lookupFolder = systemFolder, lookupDir = systemDir)
    }

    /** The arcade set tables for one scan: each resolved at most once, and only when a system that uses it is indexed. */
    private inner class ScanSets {
        private var sets: ArcadeSets? = null
        private var naomi: NaomiSets? = null

        /** The first call may parse a large file (Android reads it from assets), so it runs off the calling thread. */
        suspend fun get(): ArcadeSets = sets ?: withContext(compute ?: Dispatchers.Default) { arcadeSets() }.also { sets = it }

        suspend fun naomi(): NaomiSets = naomi ?: withContext(compute ?: Dispatchers.Default) { naomiSets() }.also { naomi = it }
    }

    /**
     * [files] as index games in list order; each lookup folder is looked up once per scan. [tag] null = Unassigned.
     * A game's name is its `map.txt` alias as written, else (as the Brick) its arcade title through [Names.plainTitle]
     * (fbneo's table, or flycast's for a DC zip/7z) or its file name through [Names.plainName]; plain names that collide in
     * the list show their stems instead ([disambiguated]). [extraNames] maps an extra's id to the name the user sees for it.
     * Runs on [compute] with the rest of the scan.
     */
    private suspend fun indexGames(
        reads: ScanReads, files: List<IndexedFile>, lookups: MutableMap<ContentRef, FolderLookup>, tag: String?, scanSets: ScanSets, extraNames: Map<String, String>,
        picked: Map<String, Set<ArtSlot>>, heroes: HeroShots,
    ): List<IndexedGame> {
        for (f in files) if (f.lookupFolder !in lookups) lookups[f.lookupFolder] = lookup(reads, f, heroes)
        val core = tag?.let { CoreRegistry.forTag(it)?.id }
        val sets = if (core == ARCADE_CORE && files.isNotEmpty()) scanSets.get() else ArcadeSets.EMPTY
        val naomi = if (core == NAOMI_CORE && files.any { Names.isArchive(it.entry.name) }) scanSets.naomi() else NaomiSets.EMPTY
        return buildGames(files, lookups, tag, core, sets, naomi, extraNames, picked)
    }

    private fun buildGames(
        files: List<IndexedFile>, lookups: Map<ContentRef, FolderLookup>, tag: String?, core: String?, sets: ArcadeSets, naomi: NaomiSets, extraNames: Map<String, String>,
        picked: Map<String, Set<ArtSlot>>,
    ): List<IndexedGame> {
        val out = ArrayList<IndexedGame>()
        val plain = ArrayList<Boolean>()   // per out entry: named by a rule, not an alias
        val arcade = core == ARCADE_CORE
        for (f in files) {
            val look = lookups.getValue(f.lookupFolder)
            val name = f.entry.name
            val base = Names.stripExtension(name)
            // A disc folder's game is keyed by its folder's name (its sheet matches it in any case).
            val discDir = if (f.lookupFolder != f.folder) f.relativeDir.substringAfterLast('/') else null
            val key = discDir ?: base
            val alias = look.names[name] ?: discDir?.let { look.names[it] }
            if (alias != null && alias.startsWith(".")) continue   // the Brick hides a "."-aliased entry (nextui content.c)
            if (name.lowercase() in CoreRegistry.biosFileNames) continue   // scph1001.bin, disksys.rom ... left in a ROM folder
            if (arcade && sets.isBios(base)) continue               // neogeo.zip, pgm.zip ...: needed beside the games, not listed
            val naomiSet = core == NAOMI_CORE && Names.isArchive(name)   // a DC zip/7z: a NAOMI/Atomiswave set or a zipped disc
            if (naomiSet && naomi.isBios(base)) continue           // naomi.zip, awbios.zip ...: belong in Bios/DC/, not listed
            out += IndexedGame(
                source = f.sourceId, dir = f.relativeDir, file = name, ref = f.entry.ref.value, folder = f.folder.value,
                name = alias ?: (if (arcade) sets.title(base)?.let(Names::plainTitle) else null)
                    ?: (if (naomiSet) naomi.title(base)?.let(Names::plainTitle) else null) ?: Names.plainName(key),
                lookupDir = f.lookupDir,
                firstSeen = f.entry.lastModified,   // the file's date; withFirstSeen keeps a known game's
            ).withArt(look.artFor(key, base, picked = pickedSlots(picked, f.sourceId, f.relativeDir, name)))
            plain += alias == null
        }
        // An extra folder's copy of a home game (the same file name, any case) is left out: the home copy is the one
        // listed. Not for Unassigned (tag null), whose rows are each file where it lies. A copy is the same file name
        // in the same place: both loose in their console's folder, or both in a folder of the same name. A generic name
        // (a PSP EBOOT.PBP) in differently named game folders, or one loose and one in a game folder, is two games.
        // A game's own folder (lowercase), null when it lies loose in its console's folder: the home's Roms/<System>, an
        // extra's root or its <TAG> / "<System> (<TAG>)" folder, or one its console's name places ("My Game Boy Advance").
        val console = tag?.let { CoreRegistry.consoleOf(it) }
        fun namesConsole(folder: String): Boolean = console != null && (
            CoreRegistry.consoleForFolderName(folder) == console ||
                Names.parseSystemFolder(folder)?.let { (d, t) -> CoreRegistry.consoleOfFolder(d, t) } == console
            )
        fun gameFolder(g: IndexedGame): String? {
            val last = g.dir.substringAfterLast('/')
            val loose = if (g.source == Recents.HOME) g.dir.count { it == '/' } <= 1
            else last.isEmpty() || last.equals(tag, ignoreCase = true) || last.endsWith("($tag)", ignoreCase = true) || namesConsole(last)
            return if (loose) null else last.lowercase()
        }
        // Each hidden copy's path goes on the home game it duplicates, so lines written for the copy still resolve.
        val home = HashMap<Pair<String, String?>, Int>()
        if (tag != null) out.forEachIndexed { i, g -> if (g.source == Recents.HOME) home.putIfAbsent(g.file.lowercase() to gameFolder(g), i) }
        val copies = HashMap<Int, MutableList<String>>()
        val kept = out.indices.filter { i ->
            val g = out[i]
            val twin = if (g.source == Recents.HOME) null else home[g.file.lowercase() to gameFolder(g)]
            twin?.let { copies.getOrPut(it) { ArrayList() } += GamePath.of(GameRef(g.source, g.dir, g.file)) }
            twin == null
        }
        val games = kept.map { i -> copies[i]?.let { out[i].copy(hiddenCopies = it) } ?: out[i] }
        return disambiguated(games, kept.map { plain[it] }, byFolder = tag != null, extraNames).inNameOrder()
    }

    /**
     * The Brick's duplicate rule: a rule-named game whose name another rule-named game in its folder shares (case-sensitive)
     * shows its stem, or for a disc folder's game its folder's name. A folder is a source and [IndexedGame.lookupDir] (a
     * disc folder counts as the folder around it). When the name is shared across folders ([byFolder]; not Unassigned,
     * whose rows always show their dir, and where the whole list is one folder), a copy alone in its folder keeps its clean
     * name, and every copy in an extra folder shows that folder's last name as [IndexedGame.subtitle] ([folderLabel]); a
     * home copy shows none.
     */
    private fun disambiguated(games: List<IndexedGame>, plain: List<Boolean>, byFolder: Boolean, extraNames: Map<String, String>): List<IndexedGame> {
        fun folder(g: IndexedGame) = if (byFolder) g.source to g.lookupDir else "" to ""
        val folders = HashMap<String, MutableSet<Pair<String, String>>>()
        val counts = HashMap<Pair<String, Pair<String, String>>, Int>()
        games.forEachIndexed { i, g ->
            if (!plain[i]) return@forEachIndexed
            folders.getOrPut(g.name) { HashSet() } += folder(g)
            val key = g.name to folder(g)
            counts[key] = (counts[key] ?: 0) + 1
        }
        return games.mapIndexed { i, g ->
            if (!plain[i]) return@mapIndexed g
            val stem = counts.getValue(g.name to folder(g)) > 1
            val elsewhere = folders.getValue(g.name).size > 1
            g.copy(
                name = if (!stem) g.name else if (g.lookupDir != g.dir) g.dir.substringAfterLast('/') else Names.stripExtension(g.file),
                subtitle = if (elsewhere) folderLabel(g, extraNames) else g.subtitle,
            )
        }
    }

    /** The last name of the folder [g] comes from in an extra: its nested dir's last segment, else the extra's name. Null in the home. */
    private fun folderLabel(g: IndexedGame, extraNames: Map<String, String>): String? {
        if (g.source == Recents.HOME) return null
        return if (g.lookupDir.isEmpty()) extraNames[g.source] ?: g.source else g.lookupDir.substringAfterLast('/')
    }

    /**
     * [f]'s folder lookup. An extra folder's lookup failure (a revoked or odd provider) stays inside that extra: its
     * games keep their file names and no art. A home failure propagates, since it means the home is unreadable, and so
     * does a hidden listing ([ListingUnavailable]) anywhere.
     */
    private suspend fun lookup(reads: ScanReads, f: IndexedFile, heroes: HeroShots): FolderLookup {
        if (f.sourceId == Recents.HOME) return readLookup(reads, f, heroes)
        val own = try { readLookup(reads, f, heroes) } catch (e: CancellationException) { throw e } catch (e: ListingUnavailable) { throw e } catch (e: Throwable) { NO_LOOKUP }
        return withMirror(own, try { mirrorLookup(reads, f.sourceId, f.lookupDir, heroes) } catch (e: CancellationException) { throw e } catch (e: ListingUnavailable) { throw e } catch (e: Throwable) { null })
    }

    /** [own] with an extra folder's home [mirror] art. */
    private fun withMirror(own: FolderLookup, mirror: FolderLookup?): FolderLookup {
        mirror ?: return own
        // The extra folder's own `.media` wins per typed folder; the home mirror fills only the names it lacks there.
        return FolderLookup(
            mirror.box3d + own.box3d, mirror.box2d + own.box2d, mirror.screenshot + own.screenshot, own.names,
            mirror.wheel + own.wheel, mirror.mix + own.mix,
            heroes = own.heroes + (mirror.heroes - own.screenshot.keys), mirror = mirror,
        )
    }

    /** The picked slots per game; a failed read is none (the folders' own files, as before Change art). */
    private suspend fun pickedGames(): Map<String, Set<ArtSlot>> =
        try { picks() } catch (e: CancellationException) { throw e } catch (_: Throwable) { emptyMap() }

    /** An extra folder's game's picked slots in [picked] (a home game's pick is in its own folder: nothing to prefer). */
    private fun pickedSlots(picked: Map<String, Set<ArtSlot>>, sourceId: String, dir: String, file: String): Set<ArtSlot> =
        if (sourceId == Recents.HOME || picked.isEmpty()) emptySet() else picked[GamePath.of(GameRef(sourceId, dir, file))].orEmpty()

    /** The scan's (or [readArt]'s) [heroShots], read when first needed and again after a [legacyMove] changed a folder; a failed read is none. */
    private inner class HeroShots {
        private var shots: Set<String>? = null
        suspend fun get(): Set<String> = shots
            ?: (try { heroShots() } catch (e: CancellationException) { throw e } catch (_: Throwable) { emptySet() }).also { shots = it }
        fun stale() { shots = null }
    }

    /**
     * The `.media` folder [media]'s listing after the [legacyMove] ([homeSegments]: its home-relative path, null for an
     * extra folder's own), and whether the move changed it (then it is listed again).
     */
    private suspend fun mediaEntries(reads: ScanReads, media: ContentRef, homeSegments: List<String>?, heroes: HeroShots): Pair<List<Entry>, Boolean> {
        val listed = reads.list(media)
        val mover = legacyMove() ?: return listed to false
        if (!mover.move(media, listed, homeSegments)) return listed to false
        reads.forget(media)
        heroes.stale()   // the log's hero paths moved with the files
        return reads.list(media) to true
    }

    /** A folder's `.media` path in the home tree ([Recents.HOME]'s [dir]), else null (an extra folder's own: nothing logged). */
    private fun homeMedia(sourceId: String, dir: String): List<String>? =
        if (sourceId == Recents.HOME) dir.split('/').filter { it.isNotEmpty() } + ".media" else null

    private suspend fun readLookup(reads: ScanReads, f: IndexedFile, heroes: HeroShots): FolderLookup {
        val children = reads.list(f.lookupFolder)   // already listed by the walk
        val homeSegments = homeMedia(f.sourceId, f.lookupDir)
        val media = children.firstOrNull { it.isDirectory && it.name == ".media" }
            ?.let { mediaEntries(reads, it.ref, homeSegments, heroes).first } ?: emptyList()
        val names = reads.sidecars.displayNames(f.sourceId, f.lookupDir, children)   // in-folder map.txt, then an extra's .sources copy
        return mediaLookup(media, names, heroes = heroes, heroDir = homeSegments)
    }

    /**
     * Only the typed folders: an untyped `.media/<name>.png` (NX Redux's old mix) is not a game's art. The 2D box is
     * `boxart2d/`, else the legacy `box2d/` per name.
     */
    private suspend fun mediaLookup(
        media: List<Entry>, names: Map<String, String>, reuse: Boolean = false, heroes: HeroShots, heroDir: List<String>?,
    ): FolderLookup {
        suspend fun sub(name: String) = art.subArtMap(media, name, artMaps, reuse)
        val shotFiles = art.subArtFiles(media, "screenshot", artMaps, reuse)
        // [heroDir]: this `.media`'s home-relative path, where the log's hero paths are ([heroShots]). Each name's chosen
        // file is checked by its full name, as the engine checks it (a hand-placed G.png beside a logged hero G.jpg is
        // the one shown, and no hero).
        val hero = if (heroDir == null || shotFiles.isEmpty()) emptySet() else heroes.get().let { all ->
            val at = (heroDir + "screenshot").joinToString("/")
            if (all.isEmpty()) emptySet() else shotFiles.filterTo(HashMap()) { (_, f) -> "$at/${f.name}" in all }.keys
        }
        return FolderLookup(
            sub("boxart"), sub("box2d") + sub("boxart2d"), shotFiles.mapValues { it.value.ref }, names, sub("wheel"), sub("mix"), heroes = hero,
        )
    }

    /**
     * An extra-folder game's art in the home mirror `<home>/Roms/.sources/<id>/<lookupDir>/.media` (where the scraper
     * writes it, since the app never writes inside an extra). Null when any segment is missing; names are not read here.
     * Each segment is looked up once per scan ([ScanReads.home]): before any scraping there is no `.sources/<id>`, and
     * that extra's folders then cost no mirror lookups at all.
     */
    private suspend fun mirrorLookup(reads: ScanReads, sourceId: String, dir: String, heroes: HeroShots, reuse: Boolean = false): FolderLookup? {
        val segments = listOf("Roms", Sidecars.SOURCES, sourceId) + dir.split('/').filter { it.isNotEmpty() } + ".media"
        val dir = reads.home?.dir(segments) ?: return null
        val (media, moved) = mediaEntries(reads, dir, segments, heroes)
        return mediaLookup(media, emptyMap(), reuse && !moved, heroes, segments)
    }

    /**
     * Every indexed game's art read again from its folder's typed `.media` (an extra folder's game: its home mirror too),
     * as [scan] reads it, the rest of the index untouched: art deleted or added outside the app since the scan. Only the
     * `.media` folders of the folders holding games are listed (looked up by name, not by listing the game folder), each
     * once. A folder that won't list (an extra gone offline) keeps its games' art; a hidden listing ([ListingUnavailable])
     * throws, and the caller keeps its index. Keyed as [artKey]. With [reuse], a typed subfolder whose modified time is
     * what the last scan or read saw is not listed again ([ArtworkResolver.SubArtMaps]): each `.media` is still listed
     * (its listing gives those times), but an unchanged library costs one listing per folder, not up to nine. Runs on
     * [compute].
     */
    suspend fun readArt(index: LibraryIndex, reuse: Boolean = false): Map<ArtKey, ArtRefs> = onCompute {
        val home = ContentRef(index.home)
        val reads = ScanReads(source, sidecars, home)
        val roots = mapOf(Recents.HOME to reads.home) + index.extras.associate { it.id to DirWalk(source, ContentRef(it.ref)) }
        val out = HashMap<ArtKey, ArtRefs>()
        val picked = pickedGames()
        val heroes = HeroShots()
        val folders = index.systems.flatMap { it.games }.groupBy { it.source to it.lookupDir }
        for ((at, games) in folders) {
            val (sourceId, dir) = at
            val look = try {
                val root = roots[sourceId] ?: continue
                val media = root.dir(dir.split('/').filter { it.isNotEmpty() } + ".media")
                val homeSegments = homeMedia(sourceId, dir)
                val own = media?.let {
                    val (entries, moved) = mediaEntries(reads, it, homeSegments, heroes)
                    mediaLookup(entries, emptyMap(), reuse && !moved, heroes, homeSegments)
                } ?: NO_LOOKUP
                if (sourceId == Recents.HOME) own else withMirror(own, mirrorLookup(reads, sourceId, dir, heroes, reuse))
            } catch (e: CancellationException) { throw e } catch (e: ListingUnavailable) { throw e } catch (e: Throwable) { continue }
            for (g in games) {
                val base = Names.stripExtension(g.file)
                val key = if (g.lookupDir != g.dir) g.dir.substringAfterLast('/') else base   // a disc folder's game: its folder's name
                out[artKey(g)] = look.artFor(key, base, picked = pickedSlots(picked, g.source, g.dir, g.file))
            }
        }
        out
    }

    companion object {
        const val UNASSIGNED = "Unassigned"

        /** [scanOutsideRoms]'s source id (its sidecars are read under it; nothing is indexed under it). */
        const val OUTSIDE_SOURCE = "home-outside"

        /**
         * A home's folders [scanOutsideRoms] does not walk (lowercase): its own, the handheld's other card folders (a home
         * at a handheld card's root) and Android's on a card root. Dot folders are never walked.
         */
        val HOME_SKIP = setOf(
            "roms", "bios", "saves", "collections",
            "cheats", "emus", "favorites", "music", "overlays", "recently played", "shaders", "tools", "videos",
            "android", "lost.dir", "dcim", "pictures", "movies", "alarms", "notifications", "podcasts", "ringtones",
            "audiobooks", "recordings",
        )

        /** How long a tree just mounted again gets before a second listing (its provider refreshes roots asynchronously). */
        const val REMOUNT_RETRY_MILLIS = 1_500L

        /** The core whose sets take their names from [ArcadeSets] and whose BIOS sets are not listed. */
        private const val ARCADE_CORE = "fbneo"
        /** The core whose zip/7z sets take their names from [NaomiSets] and whose BIOS archives are not listed. */
        private const val NAOMI_CORE = "flycast"
        private val NO_LOOKUP = FolderLookup(emptyMap(), emptyMap(), emptyMap(), emptyMap(), emptyMap(), emptyMap())
    }
}
