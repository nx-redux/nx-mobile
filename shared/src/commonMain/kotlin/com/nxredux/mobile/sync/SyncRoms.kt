package com.nxredux.mobile.sync

import com.nxredux.mobile.library.DiscSheet
import com.nxredux.mobile.library.Names
import com.nxredux.mobile.runner.Console
import com.nxredux.mobile.runner.CoreRegistry

/** One side of a ROM run: where a path is rooted. */
sealed interface RomEnd {
    /** Below this phone's home `Roms/`. */
    data class Home(val path: String) : RomEnd

    /** Below this phone's extra folder [extra]. */
    data class Extra(val extra: Int, val path: String) : RomEnd

    /** Below the peer daemon's [module]. */
    data class Peer(val module: String, val path: String) : RomEnd

    /** Below this phone's home `Bios/`. */
    data class Bios(val path: String) : RomEnd
}

/**
 * One rsync call of ROM sync (step 8 or 9) or BIOS sync (step 5 or 6): [filesFrom] limits a push to those names
 * (relative to [from]), [only] limits a pull to them by filter (`--files-from` fails on a daemon subfolder), [excludes]
 * are daemon-style patterns. [pair]: the [ConsolePair.key] a ROM run belongs to (several pairs may share [console], a
 * handheld's emulator folders).
 */
data class RomRun(
    val step: SyncStep,
    val console: String,
    val from: RomEnd,
    val to: RomEnd,
    val filesFrom: List<String>? = null,
    val excludes: List<String> = emptyList(),
    val only: List<String>? = null,
    val ignoreExisting: Boolean = false,
    val pair: String = console,
)

/** What the phone's daemon serves for ROM sync: the home `Roms/`, each extra folder (by index) and the manifest folder. */
data class ServerRoms(val roms: String, val extras: List<String>, val manifestDir: String)

/**
 * One pair's extra units: [mine] this phone sends, [theirs] it pulls from a peer phone. [pushSkip] / [pullSkip]: the
 * top-level names the home push / pull leaves out because the receiver has them under a sibling folder ([SyncRoms.units]).
 */
data class PairUnits(
    val mine: List<ExtraUnit>, val theirs: List<ExtraUnit>,
    val pushSkip: List<String> = emptyList(), val pullSkip: List<String> = emptyList(),
)

/** Plans ROM sync's rsync runs from the paired consoles and both phones' extras manifests. Pure. */
object SyncRoms {
    private const val RA = ".retroachievements"
    private const val HASHES = "/$RA/hashes.json"
    /** Art moves only in the art runs, which never replace a file. */
    private const val MEDIA = "/.media/"
    private const val SOURCES = ".sources/"
    /** Art slots only the phone shows; a handheld never gets them (nor are they listed for one: DeviceSyncController). */
    val PHONE_ONLY_SLOTS = listOf("grid", "hero")

    /**
     * A backslash before each of `\ * ? [` when [name] holds a wildcard (`*`, `?` or `[`): rsync reads such a pattern as a
     * wildcard, and this keeps it literal. Without one a backslash is already literal, so [name] is returned unchanged.
     */
    fun escape(name: String): String =
        if (name.none { it in "*?[" }) name else buildString { for (c in name) { if (c in "\\*?[") append('\\'); append(c) } }

    /**
     * Every push (step 8) of every pair, then every pull (step 9). [homeNames]: the top-level names in this phone's
     * `Roms/<folder>/` for each paired folder and its siblings; a name there wins over an extra copy. [remoteNames]: the
     * peer's top-level names (lowercase) of each paired folder that has siblings there, and of those siblings (a
     * handheld's: every folder of a mapped console). [peerIsPhone]: phone-only art slots (`grid/`, `hero/`) go to it; a
     * handheld never gets them. Art (`.media/`) moves in its own runs that never replace a file. Units are untrusted (the
     * peer's manifest): any with an unsafe path are dropped ([safe]). Every run carries its pair's [ConsolePair.key].
     *
     * A handheld peer's mapped pairs ([SyncConsoles.pairMapped], [mapped]): siblings are every folder of the console
     * ([SyncMap.consoleOf]). [gameTags]: per `local` folder, each top-level name's emulator tag (a name without one runs
     * under the console's default tag); a pair's home push leaves out every game whose tag is not in its
     * [ConsolePair.pushTags]. [localArt]: per `local` folder, its `.media` files (`<slot>/<file>`, or a top-level
     * `<file>` such as `bg.png`); when a pair's push leaves home games out, its art push sends only the art of the games
     * it pushes or the peer's folder already has (a file's art is named by the name without its extension, a folder's by
     * the whole name), plus the top-level files.
     *
     * Several mapped pairs into one `local` (a handheld's emulator folders of one console): a name more than one of their
     * `remote`s holds is pulled by one pair only ([pulledElsewhere]; [remoteEntries]: each folder's names as the peer
     * spells them, else [remoteNames]), and `.retroachievements/` moves in its own runs (a push and a pull per pair) that
     * never replace a file, so a later sync with nothing changed moves nothing.
     */
    fun plan(
        pairing: Pairing, own: ExtrasManifest, peer: ExtrasManifest, homeNames: Map<String, Set<String>>,
        remoteNames: Map<String, Set<String>> = emptyMap(), peerIsPhone: Boolean = false, peerFolders: List<String>? = null,
        gameTags: Map<String, Map<String, String>> = emptyMap(), localArt: Map<String, List<String>> = emptyMap(),
        mapped: Boolean = false, remoteEntries: Map<String, Set<String>> = emptyMap(),
    ): List<RomRun> {
        val pushes = ArrayList<RomRun>()
        val pulls = ArrayList<RomRun>()
        for (p in pairing.pairs) {
            val u = units(p, own, peer, homeNames, remoteNames, peerFolders, mapped)
            val (mine, theirs) = u
            val home = homeNames[p.local].orEmpty()
            val group = if (mapped) pairing.pairs.filter { it.local == p.local } else listOf(p)
            val shared = group.size > 1
            val elsewhere = if (shared) pulledElsewhere(p, group, remoteEntries, remoteNames, home, gameTags[p.local]) else emptyList()
            val held = held(p, home, gameTags[p.local])
            val pushSkip = (u.pushSkip + held).distinct()
            val pairPushes = ArrayList<RomRun>()
            val pairPulls = ArrayList<RomRun>()
            // A shared console's badges (named by their id, never changed) only fill gaps, both ways: see the pulls.
            val skipFirst = if (shared) listOf("/$RA/", MEDIA) else listOf(HASHES, MEDIA)
            pairPushes += RomRun(SyncStep.SEND_ROMS, p.local, RomEnd.Home(p.local), RomEnd.Peer("roms", p.remote),
                excludes = skipFirst + pushSkip.map { "/" + escape(it) })
            if (shared) {
                pairPushes += RomRun(SyncStep.SEND_ROMS, p.local, RomEnd.Home("${p.local}/$RA"), RomEnd.Peer("roms", "${p.remote}/$RA"),
                    excludes = listOf("/hashes.json"), ignoreExisting = true)
            }
            mine.groupBy { it.extra to it.parent }.forEach { (k, us) ->
                pairPushes += RomRun(SyncStep.SEND_ROMS, p.local, RomEnd.Extra(k.first, k.second), RomEnd.Peer("roms", p.remote),
                    filesFrom = us.flatMap { it.names })
            }
            art(mine).forEach { (dir, all) ->
                val files = if (peerIsPhone) all else all.filter { f -> PHONE_ONLY_SLOTS.none { f.startsWith("$it/") } }
                if (files.isEmpty()) return@forEach
                pairPushes += RomRun(SyncStep.SEND_ROMS, p.local, RomEnd.Home(dir), RomEnd.Peer("roms", "${p.remote}/.media"),
                    filesFrom = files, ignoreExisting = true)
            }
            val art = localArt[p.local]
            if (art != null && pushSkip.isNotEmpty()) {
                val skipped = pushSkip.toSet()
                val stems = (home.filter { it !in skipped } + remoteNames[p.remote].orEmpty())
                    .flatMap { listOf(it, Names.stripExtension(it)) }.mapTo(HashSet()) { it.lowercase() }
                val files = art.filter { f ->
                    val slash = f.indexOf('/')
                    slash < 0 || (Names.stripExtension(f.substring(slash + 1)).lowercase() in stems &&
                        (peerIsPhone || f.substring(0, slash) !in PHONE_ONLY_SLOTS))
                }
                if (files.isNotEmpty()) {
                    pairPushes += RomRun(SyncStep.SEND_ROMS, p.local, RomEnd.Home("${p.local}/.media"), RomEnd.Peer("roms", "${p.remote}/.media"),
                        filesFrom = files, ignoreExisting = true)
                }
            } else {
                pairPushes += RomRun(SyncStep.SEND_ROMS, p.local, RomEnd.Home("${p.local}/.media"), RomEnd.Peer("roms", "${p.remote}/.media"),
                    excludes = if (peerIsPhone) emptyList() else PHONE_ONLY_SLOTS.map { "/$it/" }, ignoreExisting = true)
            }
            pairPulls += RomRun(SyncStep.RECEIVE_ROMS, p.local, RomEnd.Peer("roms", p.remote), RomEnd.Home(p.local),
                excludes = skipFirst + (mine.map { it.landing } + u.pullSkip + elsewhere).distinct().map { "/" + escape(it) })
            theirs.groupBy { it.extra to it.parent }.forEach { (k, us) ->
                pairPulls += RomRun(SyncStep.RECEIVE_ROMS, p.local, RomEnd.Peer("extra-${k.first}", k.second), RomEnd.Home(p.local),
                    only = us.flatMap { it.names })
            }
            // Badges are named by their id and never change: each folder adds only the ones this phone lacks, so the
            // folders' differing copies never replace each other here (hashes.json merges apart).
            if (shared) {
                pairPulls += RomRun(SyncStep.RECEIVE_ROMS, p.local, RomEnd.Peer("roms", "${p.remote}/$RA"), RomEnd.Home("${p.local}/$RA"),
                    excludes = listOf("/hashes.json"), ignoreExisting = true)
            }
            // Art lives under `.sources/`, which `[roms]` hides: the peer serves it as `[nx-art]`.
            art(theirs).forEach { (dir, files) ->
                if (!dir.startsWith(SOURCES)) return@forEach
                pairPulls += RomRun(SyncStep.RECEIVE_ROMS, p.local, RomEnd.Peer("nx-art", dir.removePrefix(SOURCES)),
                    RomEnd.Home("${p.local}/.media"), only = files, ignoreExisting = true)
            }
            pairPulls += RomRun(SyncStep.RECEIVE_ROMS, p.local, RomEnd.Peer("roms", "${p.remote}/.media"), RomEnd.Home("${p.local}/.media"),
                ignoreExisting = true)
            pushes += pairPushes.map { it.copy(pair = p.key) }
            pulls += pairPulls.map { it.copy(pair = p.key) }
        }
        return pushes + pulls
    }

    /**
     * The names in [home] ([p]'s `local` top level) whose emulator tag ([tags], a name without one: the console's default
     * tag) is not in [p]'s [ConsolePair.pushTags], so its home push leaves them out; none without [tags] (a peer phone).
     */
    fun held(p: ConsolePair, home: Collection<String>, tags: Map<String, String>?): List<String> {
        if (tags == null) return emptyList()
        val default = SyncMap.consoleOf(p.local)?.defaultTag ?: p.tag
        return home.filter { !it.startsWith(".") && (tags[it] ?: default) !in p.pushTags }
    }

    /**
     * The top-level names in [p]'s `remote` that its home pull leaves to another pair of [group] (the mapped pairs into
     * [p]'s `local`): a name (ignoring case) that more than one of their `remote`s holds comes from one of them only, else
     * each sync would pull the copies in turn over the one copy here. That one: the pair this phone's copy pushes to (its
     * emulator tag in [tags], a name without one: the console's default tag, among [ConsolePair.pushTags]) when that
     * `remote` holds it, else the first in [group] order that does (the order [pulledGameTags]' "earlier pull wins"
     * follows). Names as the peer spells them ([remoteEntries], else [remoteNames]); `.retroachievements` and `.media`
     * merge in their own runs and are never left out here.
     */
    fun pulledElsewhere(
        p: ConsolePair, group: List<ConsolePair>, remoteEntries: Map<String, Set<String>>, remoteNames: Map<String, Set<String>>,
        home: Collection<String>, tags: Map<String, String>?,
    ): List<String> {
        fun names(folder: String) = remoteEntries[folder] ?: remoteNames[folder].orEmpty()
        val lower = group.associate { it.key to names(it.remote).mapTo(HashSet()) { n -> n.lowercase() } }
        val homeByLower = home.associateBy { it.lowercase() }
        val default = SyncMap.consoleOf(p.local)?.defaultTag ?: p.tag
        return names(p.remote).filter { name ->
            val n = name.lowercase()
            if (n == RA || n == MEDIA.trim('/')) return@filter false
            val holders = group.filter { n in lower.getValue(it.key) }
            if (holders.size < 2) return@filter false
            val target = homeByLower[n]?.let { mine -> (tags?.get(mine) ?: default).let { t -> holders.firstOrNull { t in it.pushTags } } }
            (target ?: holders.first()).key != p.key
        }
    }

    /**
     * The `systems.txt` entries for the top-level names a handheld pull from pair [p] just brought into its landing folder
     * ([added]): each game takes the emulator of the handheld folder it came from ([CoreRegistry.phoneTag] of [p]'s tag:
     * a `Sega Master System (GPGX)` game is GPGX_SMS, a `Game Boy Advance (MGBA)` one MGBA), so the handheld's states and
     * resume marker are the ones it finds. None when that emulator is [defaultTag] (the console's Default emulators pick)
     * or the console has no such tag; a name [existing] already tags (the user's pick) and hidden names are left alone.
     * A disc folder's game reads its tag from the folder's own `systems.txt` instead ([pulledDiscTag]).
     */
    fun pulledGameTags(p: ConsolePair, added: Collection<String>, existing: Map<String, String>, defaultTag: String): Map<String, String> {
        val console = SyncMap.consoleOf(p.local) ?: return emptyMap()
        val tag = CoreRegistry.phoneTag(console, p.tag)?.takeIf { it != defaultTag } ?: return emptyMap()
        val taken = existing.keys.mapTo(HashSet()) { it.lowercase() }
        return added.filter { plainName(it) && !Names.isHidden(it) && it.lowercase() !in taken }.associateWith { tag }
    }

    /**
     * [pulledGameTags] for a disc folder [folder] that pull brought in ([children]: its top-level names): the entry for its
     * sheet ([DiscSheet.folderSheetName]) in `<folder>/systems.txt`, where the library and the Emulator menu keep a disc
     * folder's pick. Null when [folder] is no disc folder, is hidden, or its sheet is already in [existing] (that file's tags).
     */
    fun pulledDiscTag(p: ConsolePair, folder: String, children: Collection<String>, existing: Map<String, String>, defaultTag: String): Pair<String, String>? {
        if (!plainName(folder) || Names.isHidden(folder)) return null
        val sheet = DiscSheet.folderSheetName(folder, children) ?: return null
        return pulledGameTags(p, listOf(sheet), existing, defaultTag).entries.firstOrNull()?.toPair()
    }

    /**
     * The extra units [plan] moves for pair [p], and the home names it keeps back so neither side gets a second copy of
     * a game it has under a sibling folder of the same console ([SyncConsoles.siblings], names compared ignoring case):
     * - [PairUnits.mine] it pushes: not shadowed by a home name, not landing where the peer keeps one in an extra folder,
     *   nor where the peer has an entry in a sibling of `remote`, or a name of one of its extra units of `remote`'s or a
     *   sibling's tag, but not in `remote` ("peer elsewhere"). Siblings come from [peerFolders] (the peer's top-level
     *   listing), else from the keys of [remoteNames].
     * - [PairUnits.pushSkip]: every landing of the peer's extra units of those tags, plus this phone's `local` names the
     *   peer has elsewhere.
     * - [PairUnits.pullSkip] ("mine elsewhere"): the names in this phone's home siblings of `local`, plus every name of
     *   its extra units of a sibling's tag or `local`'s own tag (a companion BIOS set counts), less those in `local`.
     * - [PairUnits.theirs] it pulls: in neither `local` nor mine's landings nor [PairUnits.pullSkip].
     * Unsafe units are dropped. Only own units of a tag in [ConsolePair.pushTags] are pushed (to a peer phone, of any tag of
     * the folder's console, [SyncMap.ofConsole], as its units of the console are pulled). [mapped] (a handheld peer's
     * mapped pairs): a sibling is any other folder of the same console ([SyncMap.consoleOf]), not only of the same name.
     */
    fun units(
        p: ConsolePair, own: ExtrasManifest, peer: ExtrasManifest, homeNames: Map<String, Set<String>>,
        remoteNames: Map<String, Set<String>> = emptyMap(), peerFolders: List<String>? = null, mapped: Boolean = false,
    ): PairUnits {
        fun siblings(folder: String, all: List<String>): List<String> = SyncConsoles.siblings(folder, all, mapped)
        val home = homeNames[p.local].orEmpty()
        val homeLower = home.mapTo(HashSet()) { it.lowercase() }
        val inRemote = remoteNames[p.remote].orEmpty().mapTo(HashSet()) { it.lowercase() }
        val remoteSiblings = siblings(p.remote, peerFolders ?: remoteNames.keys.toList())
        // A peer phone's extras of this console (`remote`'s tag or a sibling's) count too, every name of a unit.
        val peerTags = remoteSiblings.mapNotNull { Names.parseSystemFolder(it)?.second?.uppercase() }.toSet() + p.tag.uppercase()
        val peerConsoleUnits = peer.units.filter { (it.tag.uppercase() in peerTags || SyncMap.ofConsole(it.tag, p.remote)) && safe(it) }
        // A sibling's `map.txt`, `.media`, … are not games ([Names.isHidden]): they never hold back the folder's own.
        val peerElsewhere = (remoteSiblings.flatMap { remoteNames[it].orEmpty() }.map { it.lowercase() }.filter { !Names.isHidden(it) } +
            peerConsoleUnits.flatMap { it.names }.map { it.lowercase() }).toHashSet() - inRemote
        val localSiblings = siblings(p.local, homeNames.keys.toList())
        // `local`'s own tag too: a handheld's emulator folder may pair with a folder of another tag here.
        val consoleTags = (localSiblings + p.local).mapNotNull { Names.parseSystemFolder(it)?.second?.uppercase() }.toSet() + p.tag.uppercase()
        val pullSkip = (localSiblings.flatMap { homeNames[it].orEmpty() }.filter { !Names.isHidden(it.lowercase()) } +
            own.units.filter { (it.tag.uppercase() in consoleTags || SyncMap.ofConsole(it.tag, p.local)) && safe(it) }.flatMap { it.names })
            .filter { plainName(it) && it.lowercase() !in homeLower }.distinct()
        val pullSkipLower = pullSkip.mapTo(HashSet()) { it.lowercase() }
        // A peer phone's pair: every emulator of the folder's console moves (a phone folder's tag names only its console).
        val owned = own.units.filter { (it.tag in p.pushTags || !mapped && SyncMap.ofConsole(it.tag, p.local)) && it.landing !in home && safe(it) }
        val mineLandings = owned.map { it.landing }.toSet()
        val peerUnits = peer.units.filter { (it.tag == p.tag || SyncMap.ofConsole(it.tag, p.remote)) && safe(it) }
        val peerLandings = peerUnits.map { it.landing }.toSet()
        val mine = owned.filter { it.landing !in peerLandings && it.landing.lowercase() !in peerElsewhere }
        val theirs = peerUnits.filter { it.landing !in home && it.landing !in mineLandings && it.landing.lowercase() !in pullSkipLower }
        // Every landing of the peer's extras of this console, also those `theirs` drops because this home has them.
        val pushSkip = (peerConsoleUnits.map { it.landing } + home.filter { it.lowercase() in peerElsewhere && plainName(it) }).distinct()
        return PairUnits(mine, theirs, pushSkip, pullSkip)
    }

    /**
     * BIOS sync's runs (steps 5 and 6): every push, then every pull, of the `Bios/<TAG>/` folders both sides have.
     * [local]: this phone's `Bios/` folders; [remote]: the peer's (its listing, untrusted). Paired by name ignoring case,
     * the same name first, each side keeping its spelling; dot folders and unsafe names are left out, as is a folder
     * only one side has. No run ever replaces a file the receiver has ([RomRun.ignoreExisting]).
     */
    fun biosPlan(local: List<String>, remote: List<String>): List<RomRun> {
        fun tags(names: List<String>) = names.filter { plainName(it) && !it.startsWith(".") }.distinct()
        val free = tags(remote).toMutableList()
        val pairs = ArrayList<Pair<String, String>>()
        val mine = tags(local)
        // The exact names first, so `PS` meets `PS` even when the peer also has a `ps`.
        val exact = mine.filter { it in free }.toSet()
        free.removeAll(exact)
        for (l in mine) {
            val r = if (l in exact) l else free.firstOrNull { it.equals(l, ignoreCase = true) }?.also { free.remove(it) } ?: continue
            pairs += l to r
        }
        return pairs.map { (l, r) -> RomRun(SyncStep.SEND_BIOS, l, RomEnd.Bios(l), RomEnd.Peer("bios", r), ignoreExisting = true) } +
            pairs.map { (l, r) -> RomRun(SyncStep.RECEIVE_BIOS, l, RomEnd.Peer("bios", r), RomEnd.Bios(l), ignoreExisting = true) }
    }

    /**
     * A handheld peer's BIOS runs (steps 5 and 6): every push, then every pull. A folder here or there whose name is a
     * tag of a phone console ([CoreRegistry.consoleOf]) maps to that console; for each console both sides have, this
     * phone's console-tag folder pushes into every folder there of the console (another tag's folder here, such as
     * `Bios/MGBA`, never pushes), and every folder there pulls into this phone's console-tag folder (its spelling here
     * when one matches ignoring case, else the tag, which rsync creates), in [Console.tags] order, so the first one wins
     * a name both have. Folders of no console pair by name as [biosPlan] does. Dot folders and unsafe names are left
     * out; no run ever replaces a file the receiver has. Each run's [RomRun.pair] is `local\u0000remote`.
     */
    fun biosPlanMapped(local: List<String>, remote: List<String>): List<RomRun> {
        fun tags(names: List<String>) = names.filter { plainName(it) && !it.startsWith(".") }.distinct()
        val mine = tags(local); val theirs = tags(remote)
        val pairs = ArrayList<Pair<String, String>>()   // push: here → there
        val pullPairs = ArrayList<Pair<String, String>>()   // pull: there → here
        val consoles = (mine + theirs).mapNotNull { CoreRegistry.consoleOf(it) }.distinctBy { it.id }
        for (c in consoles) {
            val here = mine.filter { CoreRegistry.consoleOf(it)?.id == c.id }
            val there = theirs.filter { CoreRegistry.consoleOf(it)?.id == c.id }
                .sortedBy { t -> c.tags.indexOf(t.uppercase()).let { if (it < 0) Int.MAX_VALUE else it } }
            if (here.isEmpty() || there.isEmpty()) continue
            // Only Bios/<console tag> pushes to a handheld; another tag's folder still counts for the pulls.
            for (l in here.filter { it.equals(c.defaultTag, ignoreCase = true) }) for (r in there) pairs += l to r
            val target = here.firstOrNull { it.equals(c.defaultTag, ignoreCase = true) } ?: c.defaultTag
            for (r in there) pullPairs += target to r
        }
        val bare = biosPlan(mine.filter { CoreRegistry.consoleOf(it) == null }, theirs.filter { CoreRegistry.consoleOf(it) == null })
            .filter { it.step == SyncStep.SEND_BIOS }.map { it.console to (it.to as RomEnd.Peer).path }
        pairs += bare; pullPairs += bare
        return pairs.map { (l, r) -> RomRun(SyncStep.SEND_BIOS, l, RomEnd.Bios(l), RomEnd.Peer("bios", r), ignoreExisting = true, pair = "$l\u0000$r") } +
            pullPairs.map { (l, r) -> RomRun(SyncStep.RECEIVE_BIOS, l, RomEnd.Peer("bios", r), RomEnd.Bios(l), ignoreExisting = true, pair = "$l\u0000$r") }
    }

    /** Art paths `<dir>/.media/<slot>/<name>` grouped by `<dir>/.media`, each as `<slot>/<name>`. */
    private fun art(units: List<ExtraUnit>): Map<String, List<String>> = units.flatMap { it.art }.mapNotNull { path ->
        if (!plainPath(path)) return@mapNotNull null
        val i = path.lastIndexOf("/.media/").takeIf { it >= 0 } ?: return@mapNotNull null
        path.substring(0, i + 7) to path.substring(i + 8)
    }.groupBy({ it.first }, { it.second })

    /** A `/`-separated path whose segments are all real names: none empty, `.` or `..`. */
    private fun plainPath(path: String): Boolean = path.split('/').none { !plainName(it) }

    private fun plainName(name: String): Boolean = name.isNotEmpty() && name != "." && name != ".." && '/' !in name && '\n' !in name && '\r' !in name

    /** [u] is safe to turn into paths: its parent is empty (the extra's root) or plain, its landing and names are plain names. */
    internal fun safe(u: ExtraUnit): Boolean =
        (u.parent.isEmpty() || plainPath(u.parent)) && plainName(u.landing) && u.names.all(::plainName)
}
