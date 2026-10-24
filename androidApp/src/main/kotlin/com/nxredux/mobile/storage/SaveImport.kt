package com.nxredux.mobile.storage

import com.nxredux.mobile.runner.Console
import com.nxredux.mobile.runner.CoreDescriptor
import com.nxredux.mobile.runner.CoreRegistry
import com.nxredux.mobile.sync.SyncAreas
import java.io.File
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipInputStream

/** One file an import offers: its source-relative [path] (unchecked), modified time and size. */
interface ImportEntry {
    val path: String
    /** Null when the source has none: the copy then gets the import's time and never replaces a private file. */
    val mtime: Long?
    /** Null when unknown; when known, a copy of another length fails. */
    val size: Long?
    fun open(): InputStream
}

interface ImportFiles {
    fun entries(): Sequence<ImportEntry>
}

/**
 * A zip read once, in order: an entry's [ImportEntry.open] streams it straight from the zip (nothing is held in memory)
 * and is valid only until the walk moves on to the next entry.
 */
class ZipImportFiles(private val open: () -> InputStream) : ImportFiles {
    override fun entries(): Sequence<ImportEntry> = sequence {
        ZipInputStream(open()).use { z ->
            var current = 0
            while (true) {
                val e = z.nextEntry ?: break
                current++
                if (e.isDirectory) continue
                val mine = current
                yield(object : ImportEntry {
                    override val path = e.name
                    // The extended UTC timestamp when the zip has one (SaveExport writes it), else the local DOS time.
                    override val mtime = (e.lastModifiedTime?.toMillis() ?: e.time).takeIf { it >= 0 }
                    override val size = e.size.takeIf { it >= 0 }
                    override fun open(): InputStream = object : FilterInputStream(z) {
                        private fun ensureCurrent() { if (current != mine) throw IOException("${e.name}: the zip has moved past it") }
                        override fun read(): Int { ensureCurrent(); return super.read() }
                        override fun read(b: ByteArray, off: Int, len: Int): Int { ensureCurrent(); return super.read(b, off, len) }
                        override fun skip(n: Long): Long { ensureCurrent(); return super.skip(n) }
                        override fun close() = Unit   // the zip stays open for the next entry
                    }
                })
            }
        }
    }
}

/** A local folder: the files under each of [tops] (`""` = all of [root]), paths relative to [root]. */
class DirImportFiles(private val root: File, private val tops: List<String> = listOf("")) : ImportFiles {
    override fun entries(): Sequence<ImportEntry> = tops.asSequence()
        .map { if (it.isEmpty()) root else File(root, it) }.filter { it.isDirectory }
        .flatMap { it.walkTopDown().filter { f -> f.isFile } }
        .map { f ->
            object : ImportEntry {
                override val path = f.relativeTo(root).invariantSeparatorsPath
                override val mtime = f.lastModified()
                override val size = f.length()
                override fun open(): InputStream = f.inputStream()
            }
        }
}

/**
 * Settings → Library → Saves → Import saves into private storage ([root] = `filesDir/sd`), copy only (a source is
 * never deleted or changed), per [ImportRules]: new files are copied in, identical ones skipped, newer ones replace the
 * private file (kept as a `.bak`, [SaveCopyRules.freeBackupName]), older ones are skipped. Each file lands through a
 * `.tmp` and gets the source's modified time (or the import's, when it has none). The copy is verified against the
 * source's size when that is known (a zip's own CRC check covers its entries). Each entry is read to its end before the
 * next ([ZipImportFiles] streams). No logging: JVM-tested.
 *
 * A file in the NextUI layout ([SaveArchive.areaOf]) goes by its path: every handheld emulator save folder of a console
 * (`GBA`, `MGBA`) merges into the console's phone folder, a game's save matching across the four Save formats; the
 * winner is written in [naming]'s Save format. A save in a folder several consoles share (`Saves/GPGX`) goes to its
 * game's console by the library's ROM names ([SaveMatcher.gamesByConsole]). A loose battery save or clock file
 * elsewhere (a RetroArch `saves/<core>/` folder, a flat dump) goes to its library game by name ([matcher]), its parent
 * folder's name narrowing the consoles ([RetroArchCores.consolesOf]), and is named after that game's ROM. macOS
 * metadata ([SyncAreas.isJunk]) is only counted.
 *
 * A save state never replaces a phone state ([takeState]): it goes to its own slot when that is free for the game, else
 * the lowest free one, else it is listed "no free slot"; one whose bytes a phone state of the game already holds is
 * "already here". A NextUI-layout state (`.userdata/shared/<TAG>-<core>/`) keeps its folder and naming. A loose one
 * (RetroArch `states/<core>/`, `states/`) goes to its library game ([matcher]) under the core its folder names, or the
 * console's only core, else "can't tell the core", in [naming]'s State format. Slot pictures
 * (`.minui/<TAG>/<rom>.<slot>.bmp`) wait in [STAGING] until the walk is done, then follow their state to its slot;
 * one whose state was not imported is left out. A last-used-slot marker (`<rom>.txt`) lands only when the phone has none.
 */
class SaveImporter(
    private val root: File,
    private val now: () -> Long = System::currentTimeMillis,
    /** Moves the verified temp copy onto its target; injectable so a failing place can be tested. */
    private val rename: (File, File) -> Boolean = File::renameTo,
    /** The phone's save naming: console-folder saves are named and encoded as its Save format says. */
    private val naming: SaveNaming = SaveNaming.DEFAULT,
    /** The library's games: where a loose save, or a shared `Saves/GPGX` one, goes. */
    private val matcher: SaveMatcher = SaveMatcher(null),
) {
    private val format get() = naming.saveFormat
    private val games by lazy { matcher.gamesByConsole() }

    /**
     * A run's console folders, each listed and keyed by game ([SaveKeys.of]) the first time [take] lands a save there, so
     * an import of thousands of saves into a folder of thousands lists and parses it once, not once per save. Every file
     * [take] places or keeps aside goes into its folder's entry as it lands, so each save sees the folder as a fresh
     * listing would; a failed swap drops the entry (listed again). Emptied when the run ends.
     */
    private val folders = HashMap<File, HashMap<SaveKey, MutableList<File>>>()

    /**
     * Imports [source]; [retroArch] marks RetroArch's own folders, where a loose state outside a core folder (flat
     * `states/`) goes to its console's only core: elsewhere such a state can't tell its core and is skipped.
     */
    fun run(source: ImportFiles, retroArch: Boolean = false, onFile: (String) -> Unit = {}): ImportSummary {
        val staging = File(root, STAGING)
        staging.deleteRecursively()   // a run that died before its cleanup
        folders.clear()
        try {
            return walk(source, staging, retroArch, onFile)
        } finally {
            folders.clear()
            staging.deleteRecursively()
        }
    }

    private fun walk(source: ImportFiles, staging: File, retroArch: Boolean, onFile: (String) -> Unit): ImportSummary {
        var s = ImportSummary()
        val skipped = mutableListOf<SkippedFile>()
        val pictures = mutableListOf<StagedPicture>()
        val placed = HashMap<String, Int>()   // [slotKey] of an imported NextUI state → the slot it landed in
        for (e in source.entries()) {
            val path = SaveArchive.safePath(e.path)
            if (path == null || SyncAreas.isJunk(path)) { s = s.copy(ignored = s.ignored + 1); continue }
            val name = path.substringAfterLast('/')
            val area = SaveArchive.areaOf(path)
            val key = if (area == null) SaveKeys.of(name) else null
            val state = if (area == null && key == null) StateNames.parse(name) else null
            val marker = if (area == SaveArea.MARKERS) markerTag(path) else null
            val picture = marker?.let { StateNames.picture(name) }
            val nextUi = if (area == SaveArea.STATES) nextUiState(path) else null
            var emulator: String? = nextUi?.let { CoreRegistry.forId(path.split('/')[2].substringAfter('-'))?.name }   // a state's, for the note
            val result = when {
                picture != null -> {
                    runCatching { pictures += stage(e, path, File(staging, "${pictures.size}.bmp"), slotKey(marker, picture.stem, picture.slot)) }
                        .onFailure { skipped += SkippedFile(name, SkipReason.UNREADABLE) }
                    continue
                }
                marker != null && name.endsWith(".txt", ignoreCase = true) ->
                    runCatching { if (File(root, path).exists()) ImportAction.IDENTICAL else take(path, e) }
                nextUi != null -> runCatching {
                    val st = nextUi
                    val tag = path.split('/')[2].substringBefore('-')
                    takeState(e, File(root, path.substringBeforeLast('/')), name, st.stem, st.slot, reencode = false,
                        onPlaced = { placed[slotKey(tag, st.stem, st.slot)] = it }) { slot ->
                        if (slot == st.slot) name else StateNames.withSlot(name, slot, naming.stateFormat)!!
                    }
                }
                area != null -> runCatching { take(path, e) }
                key != null || state != null -> {
                    val game = key?.game ?: SaveKeys.romKey(state!!.stem)
                    val clue = clueOf(path)
                    // A save's core folder may run consoles beyond the ones our tags give it (RetroArch's mGBA runs GB
                    // too): when its clue finds no game the name alone decides. A state's core must be ours for the console.
                    val first = matcher.match(game, clue)
                    val m = when (val r = if (first == SaveMatch.NoGame && clue != null && state == null) matcher.match(game, null) else first) {
                        is SaveMatch.Matched -> r
                        SaveMatch.NoGame -> { skipped += SkippedFile(name, SkipReason.NO_GAME); continue }
                        is SaveMatch.Ambiguous -> {
                            skipped += SkippedFile(name, SkipReason.AMBIGUOUS, r.consoles.joinToString(" and ") { it.displayName })
                            continue
                        }
                    }
                    if (state == null) runCatching { take("Saves/${m.console.defaultTag}/$name", e, m.rom) }
                    else {
                        val core = RetroArchCores.coreOf(folderOf(path)) ?: if (retroArch) onlyCoreOf(m.console) else null
                        val tag = core?.let { c -> RetroArchCores.tagsOf(c).firstOrNull { t -> m.console.tags.any { it.equals(t, ignoreCase = true) } } }
                        if (core == null || tag == null) { skipped += SkippedFile(name, SkipReason.NO_CORE); continue }
                        emulator = core.name
                        runCatching { takeLooseState(e, name, state, NxPaths(tag, core.id, m.rom, naming)) }
                    }
                }
                else -> { s = s.copy(ignored = s.ignored + 1); continue }
            }
            val action = result.getOrElse {
                skipped += SkippedFile(name, if (it is NoFreeSlot) SkipReason.NO_FREE_SLOT else SkipReason.UNREADABLE)
                continue
            }
            s = count(s, action, area ?: if (state != null) SaveArea.STATES else SaveArea.SAVES, name, skipped)
            if (action == ImportAction.NEW && emulator != null) s = s.copy(stateEmulators = s.stateEmulators + emulator)
            onFile(path)
        }
        for (p in pictures) {
            val slot = placed[p.slotKey] ?: continue   // its state was skipped, already here or not in the import
            val target = File(root, p.path.substringBeforeLast('/') + "/" + p.rom + ".$slot.bmp")
            val ok = runCatching { target.parentFile!!.mkdirs(); place(p.file, target, p.mtime, p.size) }.isSuccess
            if (!ok) { skipped += SkippedFile(p.path.substringAfterLast('/'), SkipReason.UNREADABLE); continue }
            s = s.copy(markers = s.markers + 1)
            onFile(p.path)
        }
        return s.copy(skipped = skipped)
    }

    private fun count(s: ImportSummary, action: ImportAction, area: SaveArea, name: String, skipped: MutableList<SkippedFile>): ImportSummary =
        when (action) {
            ImportAction.NEW, ImportAction.REPLACE -> when (area) {
                SaveArea.SAVES -> s.copy(saves = s.saves + 1)
                SaveArea.STATES -> s.copy(states = s.states + 1)
                SaveArea.MARKERS -> s.copy(markers = s.markers + 1)
            }.let { if (action == ImportAction.REPLACE) it.copy(replaced = it.replaced + 1) else it }
            ImportAction.IDENTICAL -> s.copy(identical = s.identical + 1)
            ImportAction.SKIP_OLDER -> { skipped += SkippedFile(name, SkipReason.PHONE_NEWER); s }
        }

    /** A slot picture copied aside until its state has landed; [rom] is the ROM file name its own name starts with. */
    private class StagedPicture(val path: String, val file: File, val mtime: Long, val size: Long, val rom: String, val slotKey: String)

    private fun stage(e: ImportEntry, path: String, file: File, slotKey: String): StagedPicture {
        file.parentFile!!.mkdirs()
        val written = e.open().use { input -> file.outputStream().use { input.copyTo(it) } }
        check(e.size == null || written == e.size) { "short copy of $path: $written of ${e.size} bytes" }
        val name = path.substringAfterLast('/')
        return StagedPicture(path, file, e.mtime ?: now(), written, name.dropLast(".0.bmp".length), slotKey)
    }

    private fun slotKey(tag: String, stem: String, slot: Int) = "$tag/$slot/$stem"

    /** The handheld tag of a file directly in a `.userdata/shared/.minui/<TAG>/` folder; null elsewhere. */
    private fun markerTag(path: String): String? = path.split('/').takeIf { it.size == 5 }?.get(3)

    /** The state a file directly in a `.userdata/shared/<TAG>-<core>/` folder names; null for any other file there. */
    private fun nextUiState(path: String): StateName? =
        path.split('/').takeIf { it.size == 4 && it[2] != ".minui" }?.let { StateNames.parse(it[3]) }

    /** The only core [console] runs on (all its tags share it); null when it has several. */
    private fun onlyCoreOf(console: Console): CoreDescriptor? =
        console.tags.map { CoreRegistry.forTag(it) }.distinctBy { it?.id }.singleOrNull()

    /** A loose [state] of [paths]' game, named and encoded in [naming]'s State format. */
    private fun takeLooseState(e: ImportEntry, name: String, state: StateName, paths: NxPaths): ImportAction {
        val stem = StateNames.parse(paths.state(0).substringAfterLast('/'))!!.stem
        return takeState(e, File(root, paths.statesDir), name, stem, state.slot, reencode = true) { paths.state(it).substringAfterLast('/') }
    }

    /**
     * Lands the state [e] (file [name], wanted in slot [wanted]) in [dir] without touching a phone state: every state
     * there whose name parses to [stem], in any format, holds its slot. Bytes some of them already hold (RZIP decoded on
     * both sides; a phone state of another decoded size is never read) are [ImportAction.IDENTICAL]; else it goes to
     * [StateNames.place]'s slot, named [nameFor] it, and [onPlaced] is told the slot. [reencode] writes the decoded bytes
     * in [naming]'s State compression (a plain file bound for a plain format lands untouched); else they land as they came. Throws [NoFreeSlot] when every slot is taken, anything else when it could not be copied.
     */
    private fun takeState(
        e: ImportEntry, dir: File, name: String, stem: String, wanted: Int, reencode: Boolean,
        onPlaced: (Int) -> Unit = {}, nameFor: (Int) -> String,
    ): ImportAction {
        dir.mkdirs()
        val tmp = File(dir, ".$name.import.tmp")
        try {
            var written = e.open().use { input -> tmp.outputStream().use { input.copyTo(it) } }
            check(e.size == null || written == e.size) { "short copy of $name: $written of ${e.size} bytes" }
            // States can be 100+ MB (Dolphin): sizes rule a phone state out unread, two plain files compare streaming, and
            // the incoming bytes are held only when an RZIP side or a re-encode needs them.
            val packed = rzipSize(tmp)
            val size = packed ?: tmp.length()
            val raw by lazy { Rzip.decode(tmp.readBytes()) ?: error("$name: malformed RZIP") }
            val taken = dir.listFiles().orEmpty().filter { it.isFile }
                .mapNotNull { f -> StateNames.parse(f.name)?.takeIf { it.stem == stem }?.let { f to it.slot } }
            val duplicate = taken.any { (f, _) ->
                val phonePacked = rzipSize(f)
                when {
                    (phonePacked ?: f.length()) != size -> false
                    packed == null && phonePacked == null -> sameContent(tmp, f)
                    else -> Rzip.decode(f.readBytes())?.contentEquals(raw) == true
                }
            }
            if (duplicate) return ImportAction.IDENTICAL
            val slot = StateNames.place(wanted, taken.map { it.second }.toSet()) ?: throw NoFreeSlot()
            val target = File(dir, nameFor(slot))
            check(!target.exists()) { "${target.name} is taken" }
            if (reencode && (packed != null || naming.stateFormat.compressed)) {   // plain into a plain format: as it came
                val bytes = if (naming.stateFormat.compressed) Rzip.encode(raw) else raw
                tmp.writeBytes(bytes)
                written = bytes.size.toLong()
            }
            place(tmp, target, e.mtime ?: now(), written)
            onPlaced(slot)
            return ImportAction.NEW
        } finally {
            tmp.delete()
        }
    }

    /** The decoded size [f]'s RZIP header claims ([Rzip.rawSize]); null for a plain file. Reads 20 bytes at most. */
    private fun rzipSize(f: File): Long? {
        if (f.length() < RZIP_HEADER) return null
        val header = ByteArray(RZIP_HEADER)
        f.inputStream().use { if (fill(it, header) < RZIP_HEADER) return null }
        return Rzip.rawSize(header)
    }

    /** Every slot of a state's game is taken. */
    private class NoFreeSlot : RuntimeException("no free slot")

    /** The consoles a loose file's parent folder names ([RetroArchCores.consolesOf]); null at the source root or for no clue. */
    private fun clueOf(path: String): List<Console>? = folderOf(path).takeIf { it.isNotEmpty() }?.let(RetroArchCores::consolesOf)

    /** A path's parent folder name; empty at the source root. */
    private fun folderOf(path: String): String = path.substringBeforeLast('/', "").substringAfterLast('/')

    /**
     * Lands [e] at [path] (its emulator folder mapped onto the console's, [targetPath]) and returns what happened; throws
     * when it could not be copied. A battery save or clock file at the top of a console folder is matched to the phone's
     * copies of the same game under any Save format's name and written under [format]'s name. That name is [rom]'s (the
     * library ROM a loose save matched) when given; else a name that needs the ROM file name (MinUI, clock) takes it from
     * the incoming name, else from the newest phone copy that carries one, else keeps the incoming name, as does an
     * incoming `X.gb.sav` (ROM `X.gb` or `X.gb.zip`) they do not settle.
     */
    private fun take(path: String, e: ImportEntry, rom: String? = null): ImportAction {
        val mapped = targetPath(path)
        val dir = File(root, mapped.substringBeforeLast('/'))
        val incomingName = mapped.substringAfterLast('/')
        val key = SaveKeys.of(incomingName)?.takeIf { isConsoleFolderFile(mapped) }
        // The phone's copies of this game, whatever their name (newest first).
        val mine = if (key == null) emptyList() else keyed(dir)[key].orEmpty()
            .filter { it.isFile }.sortedByDescending { it.lastModified() }
        // A Generic `<stem>.sav` under the MinUI format borrows the ROM name of a phone copy (`<rom.ext>.sav`, one this
        // import just wrote from another tag folder included) rather than keeping a name the format never uses. The
        // library's Roms folders are out of this importer's reach (private storage only), so they are not asked. An
        // incoming `X.gb.sav` may be ROM `X.gb.zip`'s Generic name, so it names nothing by itself: unless the phone's
        // copies settle the ROM it keeps its name ([SaveKeys.romOf], [SaveKeys.romAmbiguous]).
        val romName = rom ?: SaveKeys.romOf(incomingName, mine.map { it.name })
        val target = File(dir, key?.let { if (romName != null || !SaveKeys.romAmbiguous(incomingName)) SaveKeys.fileName(it, format, romName) else null } ?: incomingName)
        val tmp = File(dir, ".${target.name}.import.tmp")
        dir.mkdirs()
        try {
            var written = e.open().use { input -> tmp.outputStream().use { input.copyTo(it) } }
            check(e.size == null || written == e.size) { "short copy of ${target.name}: $written of ${e.size} bytes" }
            // The phone's copies of this game (the target alone for a file with no game key).
            val same = if (key == null) listOfNotNull(target.takeIf { it.isFile }) else mine
            // A malformed RZIP fails here, before any phone save is touched.
            val raw = if (key?.kind == SaveKind.BATTERY) Rzip.decode(tmp.readBytes()) ?: error("$incomingName: malformed RZIP") else null
            if (raw != null) {
                val bytes = if (format.compressed) Rzip.encode(raw) else raw
                tmp.writeBytes(bytes)
                written = bytes.size.toLong()
            }
            val newest = same.maxByOrNull { it.lastModified() }
            val action = ImportRules.decide(newest?.lastModified(), e.mtime) {
                if (raw == null) sameContent(tmp, newest!!) else Rzip.decode(newest!!.readBytes())?.contentEquals(raw) == true
            }
            val mtime = e.mtime ?: now()   // no source time: the import's, never 1970
            when (action) {
                ImportAction.NEW -> { place(tmp, target, mtime, written); landed(dir, target) }
                ImportAction.REPLACE -> {
                    check(tmp.length() == written) { "short copy of ${target.name}" }
                    val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date(now()))
                    val kept = mutableListOf<Pair<File, File>>()   // (phone file, its backup)
                    try {
                        for (old in same) {
                            val bak = File(dir, SaveCopyRules.freeBackupName(old.name, stamp) { File(dir, it).exists() })
                            check(old.renameTo(bak)) { "could not keep ${old.name} aside" }
                            kept += old to bak
                        }
                        place(tmp, target, mtime, written)
                    } catch (t: Throwable) {
                        kept.forEach { (old, bak) -> bak.renameTo(old) }
                        folders.remove(dir)   // a put-back may itself fail: the folder is listed again
                        throw t
                    }
                    for ((old, _) in kept) SaveKeys.of(old.name)?.let { folders[dir]?.get(it)?.remove(old) }
                    for ((_, bak) in kept) landed(dir, bak)
                    landed(dir, target)
                }
                ImportAction.IDENTICAL, ImportAction.SKIP_OLDER -> Unit
            }
            return action
        } finally {
            tmp.delete()
        }
    }

    /** [dir]'s files keyed by game ([folders]): listed on first use in the run. */
    private fun keyed(dir: File): HashMap<SaveKey, MutableList<File>> = folders.getOrPut(dir) {
        HashMap<SaveKey, MutableList<File>>().also { byKey ->
            for (f in dir.listFiles().orEmpty()) if (f.isFile) SaveKeys.of(f.name)?.let { byKey.getOrPut(it) { ArrayList() } += f }
        }
    }

    /** [f], just written in [dir], into the folder's keyed listing when the run has one (a name with no game key stays out). */
    private fun landed(dir: File, f: File) {
        val list = folders[dir]?.getOrPut(SaveKeys.of(f.name) ?: return) { ArrayList() } ?: return
        list.remove(f); list += f
    }

    /**
     * [path] with a known console's `Saves/<emulator tag>/` folder turned into `Saves/<console tag>/`; anything else
     * unchanged. A save at the top of a folder several consoles share (`Saves/GPGX`) goes to the console whose library
     * games hold its game ([SharedSaves.consoleOf]), else to the tag's own console (Sega Genesis).
     */
    private fun targetPath(path: String): String {
        val parts = path.split('/', limit = 3)
        if (parts.size < 3 || parts[0] != "Saves") return path
        if ('/' !in parts[2]) SharedSaves.consoleOf(parts[1], parts[2], games)?.let { return "Saves/$it/${parts[2]}" }
        val console = CoreRegistry.consoleOf(parts[1]) ?: return path
        return "Saves/${console.defaultTag}/${parts[2]}"
    }

    /** True for a file directly inside a known console's `Saves/<tag>/` folder. */
    private fun isConsoleFolderFile(path: String): Boolean {
        val parts = path.split('/')
        return parts.size == 3 && parts[0] == "Saves" && CoreRegistry.consoleOf(parts[1]) != null
    }

    private fun place(tmp: File, target: File, mtime: Long, written: Long) {
        check(tmp.length() == written) { "short copy of ${target.name}" }
        check(rename(tmp, target)) { "could not place ${target.name}" }
        target.setLastModified(mtime)
    }

    private fun sameContent(a: File, b: File): Boolean {
        if (a.length() != b.length()) return false
        val bufA = ByteArray(BLOCK)
        val bufB = ByteArray(BLOCK)
        a.inputStream().use { x -> b.inputStream().use { y ->
            while (true) {
                val n = fill(x, bufA)
                if (n != fill(y, bufB)) return false
                if (n == 0) return true
                for (i in 0 until n) if (bufA[i] != bufB[i]) return false
            }
        } }
    }

    /** Reads until [buf] is full or the stream ends (java.io only: no API 33 readNBytes). */
    private fun fill(input: InputStream, buf: ByteArray): Int {
        var n = 0
        while (n < buf.size) {
            val r = input.read(buf, n, buf.size - n)
            if (r < 0) break
            n += r
        }
        return n
    }

    private companion object {
        const val BLOCK = 64 * 1024
        const val RZIP_HEADER = 20
        /** Where slot pictures wait during a run, under the root; removed when the run ends. */
        const val STAGING = ".userdata/${NxPaths.PLATFORM}/.import-staging"
    }
}
