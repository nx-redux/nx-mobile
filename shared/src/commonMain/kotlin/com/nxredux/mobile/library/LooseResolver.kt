package com.nxredux.mobile.library

import com.nxredux.mobile.library.disc.DiscSignatures
import com.nxredux.mobile.runner.Console
import com.nxredux.mobile.runner.ContentRef
import com.nxredux.mobile.runner.CoreRegistry

/**
 * One candidate file in an extra tree. [relativeDir] is "" at the root, else "a/b". [tag] null = unassigned.
 * [lookupFolder]/[lookupDir] are where its art and map.txt name live: its own folder, or for a disc folder's sheet the
 * folder around it.
 */
data class LooseFile(
    val entry: Entry,
    val folder: ContentRef,
    val relativeDir: String,
    val tag: String?,
    val lookupFolder: ContentRef = folder,
    val lookupDir: String = relativeDir,
    /** A pill by its name ([IndexedGame.badge]), or null. */
    val badge: String? = null,
)

/**
 * Walks an extra tree and resolves every candidate file with [Resolution]. Hidden names, `.media` and files a sibling disc
 * sheet loads are skipped. A subfolder `D/` holding `D.m3u` (else `D.cue`, else `D.gdi`, any case; see
 * [DiscSheet.folderSheet]) that a core runs is NX Redux's disc folder: one game, its sheet, with art and name from the
 * folder around it; nothing else in `D/` is listed. An MSU pack's folder ([MsuSets.folderRom]) is one game the same way.
 */
class LooseResolver internal constructor(
    private val source: RomsSource,
    private val reads: ScanReads,
    private val defaultTag: (Console) -> String,
    /** A disc image's console by its contents ([DiscProbes]), asked last, for the disc extensions nothing else placed. */
    private val probe: (suspend (Entry, List<Entry>) -> String?)? = null,
    /** The sheet and zip head reads, reused across scans while a file is unchanged ([HeadReads]). */
    private val heads: HeadReads = HeadReads(source, emptyList()),
) {
    /** [skipTop]: lowercase names of folders at [tree]'s root that are not walked (a home's `Roms`, `Bios` ...). */
    suspend fun scan(tree: ContentRef, sourceId: String, skipTop: Set<String> = emptySet()): List<LooseFile> {
        val out = ArrayList<LooseFile>()
        walk(tree, sourceId, "", listOf(source.displayName(tree)), out, skipTop)
        return out
    }

    /** [dir] is listed through [reads] once per scan; its sidecar comes from that listing. */
    private suspend fun walk(
        dir: ContentRef, sourceId: String, relativeDir: String, ancestors: List<String>, out: MutableList<LooseFile>, skip: Set<String> = emptySet(),
    ) {
        val children = reads.list(dir)
        val sidecar = reads.sidecars.read(sourceId, relativeDir, children)
        val folder = Resolution.FolderHints.of(ancestors, defaultTag)
        // A bin/cue or m3u set shows once, as its sheet.
        val files = children.filter { !it.isDirectory && !Names.isHidden(it.name) }
        val hidden = DiscSheet.hiddenNamed(files, heads::sheetNames) + MsuSets.hiddenSheets(files)
        val sets = MsuSets.Folder(files.map { it.name })
        for (c in children) {
            if (Names.isHidden(c.name)) continue
            if (c.isDirectory) {
                if (c.name.lowercase() in skip) continue   // only the root's: deeper walks get none
                val childDir = if (relativeDir.isEmpty()) c.name else "$relativeDir/${c.name}"
                val childAncestors = listOf(c.name) + ancestors
                val game = gameFolder(c, reads.list(c.ref), sourceId, childDir, childAncestors, dir, relativeDir)
                if (game != null) out += game else walk(c.ref, sourceId, childDir, childAncestors, out)
                continue
            }
            if (c.name.lowercase() in hidden) continue
            val candidate = Resolution.isCandidate(c.name)
            // png, o, lmp, exe: a core runs them, but only a folder or a sidecar says which (a stray .png is not a game).
            if (!candidate && Names.extensionOf(c.name) !in CoreRegistry.coreExtensions) continue
            // Only a zip is peeked (its first local header names the entry), and only when the folder and sidecar place
            // nothing; a 7z is placed by folder or sidecar alone.
            val tag = Resolution.resolve(c.name, sidecar[c.name], folder, defaultTag) { heads.zipEntry(c) }
                ?: probed(c, children)
            if (tag == null && !candidate) continue
            out += LooseFile(c, dir, relativeDir, tag, badge = sets.kind(c.name))
        }
    }

    /** [file] (listed in [siblings]) placed by [probe], for a disc extension only. */
    private suspend fun probed(file: Entry, siblings: List<Entry>): String? {
        val p = probe ?: return null
        if (Names.extensionOf(file.name) !in DISC_EXTENSIONS) return null
        return p(file, siblings)?.let { Resolution.acceptsConsole(it, file.name, defaultTag) }
    }

    /**
     * [dir] (listed as [children]) as one game: an MSU pack (its only ROM with its set beside it, [MsuSets.folderRom],
     * checked first: a pack's cue would read as a sheet), else a disc folder (its [DiscSheet.folderSheet]). The file is
     * resolved as a file in [dir] would be and listed from [dir] with [parent]'s lookups. Null when it is neither or
     * nothing runs it, so it is walked as any folder.
     */
    private suspend fun gameFolder(
        dir: Entry, children: List<Entry>, sourceId: String, relativeDir: String, ancestors: List<String>, parent: ContentRef, parentDir: String,
    ): LooseFile? {
        val pack = MsuSets.folderRom(children)
        val file = pack ?: DiscSheet.folderSheet(dir.name, children) ?: return null
        val sidecar = reads.sidecars.read(sourceId, relativeDir, children)
        val tag = Resolution.resolve(file.name, sidecar[file.name], ancestors, null, defaultTag)
            ?: (if (pack == null) probed(file, children) else null) ?: return null
        val badge = pack?.let { MsuSets.Folder(children.map { c -> c.name }).kind(it.name) }
        return LooseFile(file, dir.ref, relativeDir, tag, lookupFolder = parent, lookupDir = parentDir, badge = badge)
    }
}

/** What a disc probe reads: disc images and the sheets that name them. */
private val DISC_EXTENSIONS = setOf("iso", "bin", "img", "cue", "gdi", "chd", "m3u")

/**
 * A scan's disc probes, each file's answer reused from [previous] while its size and modified time (and a sheet's data
 * file's) are unchanged, and, for a nothing-recognised answer, while [DiscSignatures.VERSION] is too; every readable
 * answer this scan used is in [used], for the next index. An answer from a failed read (or a sheet whose data file is
 * missing) is not kept, so the next scan asks again; neither is one for a file (or data file) whose provider reports no
 * modified time, as in [HeadReads].
 */
internal class DiscProbes(private val probe: com.nxredux.mobile.library.disc.DiscProbe, previous: List<IndexedProbe>) {
    private val before = previous.associateBy { it.ref }
    private val kept = LinkedHashMap<String, IndexedProbe>()   // by ref: a folder reached twice keeps one

    val used: List<IndexedProbe> get() = kept.values.toList()

    suspend fun consoleOf(file: Entry, siblings: List<Entry>): String? {
        val modified = file.lastModified
        val known = before[file.ref.value]?.takeIf { p ->
            modified != null && p.size == file.sizeBytes && p.modified == modified && dataUnchanged(p, siblings) &&
                (p.console != null || p.signatures == DiscSignatures.VERSION)
        }
        if (known != null) { kept[known.ref] = known; return known.console }
        val r = probe.probe(file, siblings)
        if (r.read && modified != null && (r.data == null || r.data.lastModified != null)) {
            kept[file.ref.value] = IndexedProbe(
                file.ref.value, file.sizeBytes, modified, r.console, r.data?.ref?.value, r.data?.sizeBytes, r.data?.lastModified, DiscSignatures.VERSION,
            )
        }
        return r.console
    }

    /** A sheet's data file is still there as it was probed; true for a probe with none. */
    private fun dataUnchanged(p: IndexedProbe, siblings: List<Entry>): Boolean {
        val ref = p.data ?: return true
        val modified = p.dataModified ?: return false
        return siblings.any { it.ref.value == ref && it.sizeBytes == p.dataSize && it.lastModified == modified }
    }
}

/**
 * A scan's head reads ([RomsSource.readHead]): what each disc sheet names ([DiscSheet.hiddenNamed]: the [DiscSheet.references]
 * of its head, read as its own type) and each peeked zip's first entry name ([ZipPeek]). As [DiscProbes], a file's answer
 * is reused from [previous] while its size and modified time are unchanged, and every answer this scan used is in [used],
 * for the next index: a rescan of an unchanged library opens no sheet and no zip. A failed read is not kept (the next scan
 * reads again), and neither is a file whose provider reports no modified time: a rewrite of the same size would go unseen.
 */
internal class HeadReads(private val source: RomsSource, previous: List<IndexedHead>) {
    private val before = previous.associateBy { it.ref }
    private val kept = LinkedHashMap<String, IndexedHead>()   // by ref: a folder reached twice (an extra inside the home) keeps one

    val used: List<IndexedHead> get() = kept.values.toList()

    /** What sheet [file] names, or null when it cannot be read. */
    suspend fun sheetNames(file: Entry): List<String>? =
        names(file) { source.readHead(file.ref, DiscSheet.HEAD_BYTES)?.let { DiscSheet.references(file.name, DiscSheet.decodeHead(it)) } }

    /** Zip [file]'s first entry name; null when it cannot be read or names none (a folder entry, not a zip). */
    suspend fun zipEntry(file: Entry): String? =
        names(file) { source.readHead(file.ref, ZipPeek.HEAD_BYTES)?.let { listOfNotNull(ZipPeek.firstEntryName(it)) } }?.firstOrNull()

    private suspend fun names(file: Entry, read: suspend () -> List<String>?): List<String>? {
        val modified = file.lastModified
        val known = before[file.ref.value]?.takeIf { modified != null && it.size == file.sizeBytes && it.modified == modified }
        if (known != null) { kept[known.ref] = known; return known.names }
        val names = read() ?: return null
        if (modified != null) kept[file.ref.value] = IndexedHead(file.ref.value, file.sizeBytes, modified, names)
        return names
    }
}
