package com.nxredux.mobile.sync

import com.nxredux.mobile.library.Names
import com.nxredux.mobile.runner.CoreRegistry
import com.nxredux.mobile.storage.SaveFormat
import com.nxredux.mobile.storage.SaveKey
import com.nxredux.mobile.storage.SaveKeys
import com.nxredux.mobile.storage.SaveKind

/** A file below a `Saves/` root: [path] like `GBA/Game.srm` or `DC/reicast/x.nvmem`; [mtime] in ms. */
data class SaveFile(val path: String, val mtime: Long)

/** Copy this phone's `Saves/[from]` to the staging tree's `[to]` (handheld layout), keeping its time. */
data class StageCopy(val from: String, val to: String)

/** Write staged-received `[from]` to this phone's `Saves/[to]` with [mtime]; [battery]: re-encode in the phone's format. */
data class FoldWrite(val from: String, val to: String, val mtime: Long, val battery: Boolean)

/** What a receive folds back: [writes] and [removes] (phone `Saves/` paths), plus [skipped] saves no console claims. */
data class FoldPlan(val writes: List<FoldWrite>, val removes: List<String>, val skipped: Int)

/**
 * Battery saves between this phone's one `Saves/<console tag>` per console and a handheld's per-emulator `Saves/<TAG>`
 * folders. Pure: callers list files and run the copies.
 *
 * [handheldFolders] are the handheld's `Roms/` top-level folders (null: the listing was unavailable), [handheldRoms] each
 * folder's top-level names, used to know where a game lives. [emulatorTag] is the phone's emulator for a game, [romName]
 * its ROM file name when known, [format] the phone's Save format. [phoneFolders]: this phone's `Roms/` top-level folders,
 * when known, for the ROM push targets' landing ([SyncConsoles.landing]).
 */
class SaveSyncPlan(
    handheldFolders: List<String>?,
    handheldRoms: Map<String, Set<String>>,
    private val emulatorTag: (consoleTag: String, game: String) -> String,
    private val romName: (consoleTag: String, game: String) -> String?,
    private val format: SaveFormat,
    phoneFolders: List<String> = emptyList(),
) {
    private val folders = handheldFolders.orEmpty()

    private val saveConsoles: Map<String, Set<String>> = SyncMap.saveConsoles(folders)

    /**
     * Save tags that may hold several consoles' saves though [saveConsoles] gives one: with no handheld listing, every
     * tag the handheld shares across consoles ([CoreRegistry.SHARED_HANDHELD_TAGS]: `GPGX`). Nothing is staged to them
     * and their received saves are skipped.
     */
    private val unknown: Set<String> = if (handheldFolders == null) CoreRegistry.SHARED_HANDHELD_TAGS else emptySet()

    /**
     * Console tag → each of its tags → the save tag of the handheld ROM folder its games' ROMs push to
     * ([SyncConsoles.pushTargets], as the ROM steps pair them). Only consoles the handheld has a ROM folder of.
     */
    private val pushTags: Map<String, Map<String, String>> = folders.filter { !it.startsWith(".") }
        .groupBy { SyncMap.consoleOf(it) }
        .mapNotNull { (console, theirs) ->
            console ?: return@mapNotNull null
            val landing = SyncConsoles.landing(console, phoneFolders.filter { SyncMap.consoleOf(it)?.id == console.id }, folders.toSet())
            console.defaultTag to SyncConsoles.pushTargets(console, theirs, landing)
                .mapValues { (_, f) -> Names.parseSystemFolder(f)!!.second.uppercase() }
        }.toMap()

    /** (save tag, console tag) → the games the handheld's ROM folders with that tag and console hold. */
    private val gamesIn: Map<Pair<String, String>, Set<String>> = buildMap<Pair<String, String>, MutableSet<String>> {
        for (folder in folders) {
            val tag = Names.parseSystemFolder(folder)?.second?.uppercase() ?: continue
            val console = SyncMap.consoleOf(folder)?.defaultTag ?: continue
            val games = getOrPut(tag to console) { mutableSetOf() }
            handheldRoms[folder].orEmpty().mapTo(games) { SaveKeys.romKey(it) }
        }
    }

    /** The handheld `Saves/<T>` folders to download ([SyncMap.saveConsoles]), sorted. */
    val receiveTags: List<String> = saveConsoles.keys.sorted()

    /**
     * The staging copies for this phone's saves (sent in step 2): a battery or clock file goes to each mapped save
     * folder whose ROM folder holds its game, else where the game's ROM push goes ([pushedTag]); any other file goes by
     * path to the console's own folder. Only the newest phone copy of each game (or path) is staged, so a stale
     * leftover or other-format copy never lands.
     */
    fun send(phone: List<SaveFile>): List<StageCopy> = newestPerGame(phone).flatMap { (file, entry) ->
        val c = entry.console
        val key = entry.key
        if (key != null) {
            val found = mappedTags(c).filter { inFolder(c, it, key.game) }
            found.ifEmpty { listOfNotNull(pushedTag(c, key.game)) }.map { StageCopy(file.path, "$it/${entry.rest}") }
        } else {
            if (saveConsoles[c] == setOf(c)) listOf(StageCopy(file.path, "$c/${entry.rest}")) else emptyList()
        }
    }.sortedBy { it.to }

    /**
     * The receive tree's placeholders for the names a phone game's save may have on the handheld besides the ones [send]
     * stages (received in step 4): for the newest phone battery or clock file of each game, every other format name of
     * it (`<game>.srm`, `<game>.sav`, and each format's [SaveKeys.batteryName] and `<rom.ext>.rtc` of the ROM names from
     * [romName], the phone file's own name and the handheld's ROM folders of the console) in every mapped save folder
     * that credits the game to its console ([saveConsoles] gives it alone, or [send] stages there). [SyncSaveFiles]
     * writes them to the receive tree only, dated the phone file's own time as a staged copy is, so step 4's `--update`
     * skips a handheld copy as old or older and [fold]'s [FOLD_MARGIN_MS] rejects what is downloaded but not newer enough.
     *
     * Without them a handheld holding one game under two formats (`Red.srm` and `Red.gb.sav`) would have the name the
     * phone does not use downloaded on every sync, as missing from the receive tree, though never newer. A seed is not
     * dated ahead (by the fold's margin, say): rsync's `--modify-window=1` skips a same-size file within a second, so a
     * handheld save the fold would take (phone 10.900 s, seed 11.900 s, handheld 12.000 s) would never download.
     */
    fun seeds(phone: List<SaveFile>): List<StageCopy> {
        val staged = send(phone)
        val taken = staged.mapTo(HashSet()) { it.to }
        return newestPerGame(phone).flatMap { (file, entry) ->
            val c = entry.console
            val key = entry.key ?: return@flatMap emptyList()
            // The ROM name the file itself carries too: one too many only adds a placeholder.
            val roms = (listOfNotNull(romName(c, key.game), SaveKeys.carriedRom(entry.rest)) + handheldRomsOf[c]?.get(key.game).orEmpty())
            val names = when (key.kind) {
                SaveKind.BATTERY -> listOf("${key.game}.srm", "${key.game}.sav") + roms.flatMap { r -> SaveFormat.entries.map { SaveKeys.batteryName(it, r) } }
                SaveKind.CLOCK -> roms.map { "$it.rtc" }
            }.filter { SaveKeys.of(it) == key }.distinct()
            val tags = mappedTags(c).filter { t -> saveConsoles[t] == setOf(c) || staged.any { it.from == file.path && it.to.startsWith("$t/") } }
            tags.flatMap { t -> names.map { "$t/$it" } }.filter { it !in taken }.map { StageCopy(file.path, it) }
        }.sortedBy { it.to }
    }

    /** Console tag → game → the handheld's ROM file names of it in that console's ROM folders. */
    private val handheldRomsOf: Map<String, Map<String, Set<String>>> = buildMap<String, MutableMap<String, MutableSet<String>>> {
        for (folder in folders) {
            val console = SyncMap.consoleOf(folder)?.defaultTag ?: continue
            val games = getOrPut(console) { mutableMapOf() }
            for (rom in handheldRoms[folder].orEmpty()) games.getOrPut(SaveKeys.romKey(rom)) { mutableSetOf() }.add(rom)
        }
    }

    /** The newest phone file per console and id; a tie prefers the console-tag folder, then the smaller path. */
    private fun newestPerGame(phone: List<SaveFile>): List<Pair<SaveFile, PhoneEntry>> =
        phone.mapNotNull { f -> phoneEntry(f)?.let { f to it } }
            .groupBy { (_, e) -> e.console to e.id }
            .values.map { group ->
                group.minWith(compareByDescending<Pair<SaveFile, PhoneEntry>> { it.first.mtime }
                    .thenBy { (f, e) -> if (f.path.substringBefore('/').uppercase() == e.console) 0 else 1 }
                    .thenBy { it.first.path })
            }

    /**
     * What to write back from the received staging tree (after step 4): per console and game (or path, for other
     * files), the newest received copy wins when it is over a second newer than every phone copy. A winning battery or
     * clock file is named in the phone's Save format and the phone's other names of that game are removed. A save in a
     * folder shared by several consoles (`GPGX`) goes to the one console whose ROM folder of that tag holds its game or
     * whose phone game [send] stages there ([pushedTag], the handheld holding it nowhere yet); with none or several it
     * is skipped, as is every save in an [unknown] folder.
     */
    fun fold(received: List<SaveFile>, phone: List<SaveFile>): FoldPlan {
        var skipped = 0
        val candidates = mutableMapOf<Pair<String, Any>, MutableList<SaveFile>>()
        for (file in received) {
            val t = file.path.substringBefore('/')
            val rest = file.path.substringAfter('/', "")
            val consoles = saveConsoles[t] ?: continue
            if (rest.isEmpty()) continue
            val key = if ('/' in rest) null else SaveKeys.of(rest)
            val c = when {
                t in unknown -> null
                consoles.size == 1 -> consoles.single()
                else -> key?.let { k -> consoles.filter { inFolder(it, t, k.game) || stagedHere(it, t, k.game) }.singleOrNull() }
            }
            if (c == null) { skipped++; continue }
            candidates.getOrPut(c to (key ?: "path:$rest")) { mutableListOf() }.add(file)
        }
        val phoneById = phone.mapNotNull { f -> phoneEntry(f)?.let { (it.console to it.id) to f } }
            .groupBy({ it.first }, { it.second })

        val writes = mutableListOf<FoldWrite>()
        val removes = mutableListOf<String>()
        for ((group, files) in candidates) {
            val (c, id) = group
            val winner = files.maxWith(compareBy<SaveFile> { it.mtime }.thenBy { it.path })
            val mine = phoneById[group].orEmpty()
            val newestMine = mine.maxOfOrNull { it.mtime }
            if (newestMine != null && winner.mtime <= newestMine + FOLD_MARGIN_MS) continue
            val name = winner.path.substringAfterLast('/')
            if (id is SaveKey) {
                // With no ROM known, the names of the game's other copies (the phone's, newest first, then the
                // handheld's) may tell it; an ambiguous name they do not settle (`X.gb.sav`) is kept as received.
                val others = mine.sortedByDescending { it.mtime }.map { it.path.substringAfterLast('/') } +
                    files.filter { it != winner }.map { it.path.substringAfterLast('/') }
                val rom = romName(c, id.game) ?: SaveKeys.romOf(name, others)
                val to = "$c/" + (if (rom == null && SaveKeys.romAmbiguous(name)) name else SaveKeys.fileName(id, format, rom) ?: name)
                writes += FoldWrite(winner.path, to, winner.mtime, battery = id.kind == SaveKind.BATTERY)
                mine.filter { it.path != to }.mapTo(removes) { it.path }
            } else {
                writes += FoldWrite(winner.path, "$c/${winner.path.substringAfter('/')}", winner.mtime, battery = false)
            }
        }
        return FoldPlan(writes.sortedBy { it.to }, removes.distinct().sorted(), skipped)
    }

    /** A phone save's console (its first path segment's console, any tag of it), path below it and id. */
    private class PhoneEntry(val console: String, val rest: String, val key: SaveKey?) {
        val id: Any get() = key ?: "path:$rest"
    }

    private fun phoneEntry(file: SaveFile): PhoneEntry? {
        val segment = file.path.substringBefore('/')
        val rest = file.path.substringAfter('/', "")
        if (rest.isEmpty() || CoreRegistry.consoleOf(segment) == null) return null
        return PhoneEntry(CoreRegistry.consoleTag(segment), rest, if ('/' in rest) null else SaveKeys.of(rest))
    }

    /** Every handheld save tag that may hold saves of console [c]. */
    private fun mappedTags(c: String): List<String> = receiveTags.filter { c in saveConsoles.getValue(it) }

    /**
     * Where [send] stages console [c]'s save of [game] when no handheld ROM folder holds the game: the save folder of
     * the ROM folder its ROM push goes to ([pushTags]: its emulator's tag when the handheld has a folder of [c] with it,
     * else the first of [c]'s tags it has one of, …). A folder shared with other consoles (`GPGX`) is fine unless one
     * of them holds the game there, which would make the fold credit it. With no push target (no listing, or no
     * handheld ROM folder of [c]) its emulator's handheld folder (GPGX_SMS: GPGX) when that serves only [c]. Else the
     * console's own folder when it serves only [c]; else null.
     */
    private fun pushedTag(c: String, game: String): String? {
        val e = emulatorTag(c, game).uppercase()
        val targets = pushTags[c]
        val t = if (targets != null) targets[e] ?: targets.getValue(c) else CoreRegistry.handheldTag(e)
        val shared = saveConsoles[t]
        val ok = t !in unknown && shared != null && c in shared &&
            (if (targets != null) shared.none { it != c && inFolder(it, t, game) } else shared.size == 1)
        return if (ok) t else c.takeIf { saveConsoles[c] == setOf(c) }
    }

    /** True when [send] stages this phone's [game] of console [c] to [t] though no handheld ROM folder of [c] holds it. */
    private fun stagedHere(c: String, t: String, game: String): Boolean =
        romName(c, game) != null && gamesIn.none { (k, games) -> k.second == c && game in games } && pushedTag(c, game) == t

    /** True when a handheld ROM folder tagged [t] of console [c] holds [game]. */
    private fun inFolder(c: String, t: String, game: String): Boolean = gamesIn[t to c]?.contains(game) == true

    companion object {
        /** How much newer than every phone copy a received save must be to win [fold]. */
        const val FOLD_MARGIN_MS = 1000L
    }
}
