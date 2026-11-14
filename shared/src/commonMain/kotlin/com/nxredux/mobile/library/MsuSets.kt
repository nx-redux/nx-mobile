package com.nxredux.mobile.library

import com.nxredux.mobile.runner.Companion
import com.nxredux.mobile.runner.ContentRef

/**
 * Cartridge games whose CD-quality music (and MSU-1 data) lies in files beside the ROM. Such a set is listed as its
 * ROM with a badge ([IndexedGame.badge]: [MSU1] or [MSU_MD]), and its files go wherever the ROM goes (a launch, a move,
 * a sync). The scan decides which ROMs have a set, from the whole folder's listing; the rest follow its badge.
 *
 * MSU-1 (SNES): `<stem>.msu` (data, often empty), `<stem>-<n>.pcm` (tracks) and an optional `<stem>.msu1` pack,
 * opened by Snes9x on names it derives from the ROM's path.
 *
 * MSU-MD (Mega Drive): `<stem>.cue` and the audio tracks it names, beside a cartridge of the same stem. PicoDrive loads
 * the cue and finds the cartridge by its extension (its find_msu, which tries gen, smd, md and 32x; 32X sets are left
 * out here); Genesis Plus GX loads the cartridge and mounts the cue.
 */
object MsuSets {
    /** The consoles whose game folders can be MSU packs. */
    val CONSOLES = setOf("sfc", "md")
    const val MSU1 = "MSU-1"
    const val MSU_MD = "MSU-MD"
    private val MSU1_ROMS = setOf("sfc", "smc")
    private val MD_CARTRIDGES = setOf("gen", "smd", "md")

    /** Whether [romName] is one that has MSU-1 files to look for. */
    fun msu1Rom(romName: String): Boolean = Names.extensionOf(romName) in MSU1_ROMS

    /** Whether [romName] is a cartridge an MSU-MD cue can pair with. */
    fun mdCartridge(romName: String): Boolean = Names.extensionOf(romName) in MD_CARTRIDGES

    private fun rom(name: String): Boolean = msu1Rom(name) || mdCartridge(name)

    /**
     * A folder's MSU files by the ROM stem they belong to (any case), read once per folder so each ROM's lookup is
     * direct: `.msu`/`.msu1` and `-<n>.pcm` by their stem, cues by theirs.
     */
    class Folder(names: Collection<String>) {
        private val msu1 = HashMap<String, MutableList<String>>()
        private val cues = HashMap<String, String>()

        init {
            for (n in names) {
                val base = Names.stripExtension(n)
                when (Names.extensionOf(n)) {
                    "msu", "msu1" -> msu1.getOrPut(base.lowercase()) { ArrayList() } += n
                    "pcm" -> {
                        val dash = base.lastIndexOf('-')
                        if (dash > 0 && dash < base.length - 1 && base.substring(dash + 1).all(Char::isDigit)) {
                            msu1.getOrPut(base.substring(0, dash).lowercase()) { ArrayList() } += n
                        }
                    }
                    "cue" -> cues.putIfAbsent(base.lowercase(), n)
                }
            }
        }

        /**
         * [romName]'s set files, each paired with the name the core opens it by (the ROM's stem as spelled, a lowercase
         * extension, a track number unpadded: Snes9x asks for "-%d.pcm") and the name it is listed under: MSU-1's data,
         * pack and tracks (data first, tracks in order), or MSU-MD's cue. The cue's own tracks are [DiscSheet]'s.
         */
        fun ownFiles(romName: String): List<Pair<String, String>> {
            val stem = Names.stripExtension(romName)
            return when {
                msu1Rom(romName) -> msu1[stem.lowercase()].orEmpty().map { msu1Name(stem, it) to it }
                    .distinctBy { it.first.lowercase() }.sortedBy { trackOrder(it.first) }
                mdCartridge(romName) -> cues[stem.lowercase()]?.let { listOf("$stem.cue" to it) }.orEmpty()
                else -> emptyList()
            }
        }

        /** The kind of set [romName] has here: [MSU1], [MSU_MD], or null for none. */
        fun kind(romName: String): String? = when {
            mdCartridge(romName) -> MSU_MD.takeIf { stemOf(romName) in cues }
            msu1Rom(romName) -> MSU1.takeIf { stemOf(romName) in msu1 }
            else -> null
        }

        /** The MSU-MD cue that pairs with cartridge [romName], as listed; null when it has none. */
        fun mdSheet(romName: String): String? = if (mdCartridge(romName)) cues[stemOf(romName)] else null

        private fun stemOf(romName: String) = Names.stripExtension(romName).lowercase()

        private fun msu1Name(stem: String, listed: String): String {
            val ext = Names.extensionOf(listed)
            if (ext != "pcm") return "$stem.$ext"
            val track = Names.stripExtension(listed).substringAfterLast('-').trimStart('0').ifEmpty { "0" }
            return "$stem-$track.pcm"
        }
    }

    /**
     * The lowercased names among [files] that are an MSU-MD cue beside its cartridge: the set is listed as the cartridge.
     * The tracks the cue names are hidden by [DiscSheet.hiddenNamed] as for any cue.
     */
    fun hiddenSheets(files: List<Entry>): Set<String> {
        val names = files.map { it.name }
        val carts = names.filter(::mdCartridge).map { Names.stripExtension(it).lowercase() }.toSet()
        if (carts.isEmpty()) return emptySet()
        return names.filter { Names.extensionOf(it) == "cue" && Names.stripExtension(it).lowercase() in carts }.map { it.lowercase() }.toSet()
    }

    /**
     * A game folder's ROM: the folder's only ROM, when it has an MSU-1 or MSU-MD set beside it. MSU packs ship as a folder
     * whose name rarely matches the ROM's (`Contra III (MSU1)/Contra_III_(MSU1).sfc`), so the folder makes it one game;
     * a folder of several ROMs is a folder of games, one of which may have its set loose beside it.
     */
    fun folderRom(children: List<Entry>): Entry? {
        val files = children.filter { !it.isDirectory && !Names.isHidden(it.name) }
        val rom = files.filter { rom(it.name) }.singleOrNull() ?: return null
        return rom.takeIf { Folder(files.map { f -> f.name }).kind(it.name) != null }
    }

    /**
     * What a launch of [fileName] in [folder] hands the core beside it, for a game the scan badged as a set ([badge]):
     * its [Folder.ownFiles] and, for MSU-MD, the tracks its cue names. A track the cue names that is missing is
     * [CompanionLookup.Missing]. Another badge, or none: `Found(emptyList())`, as is a listing the provider withholds
     * (the game runs without its music).
     */
    suspend fun companions(source: RomsSource, folder: ContentRef, fileName: String, badge: String?): CompanionLookup {
        val listed = setListing(source, folder, badge) ?: return CompanionLookup.Found(emptyList())
        val byName = listed.associateBy { it.name }
        val set = Folder(byName.keys)
        val out = set.ownFiles(fileName).map { (name, listedName) -> Companion(name, byName.getValue(listedName).ref) }.toMutableList()
        set.mdSheet(fileName)?.let { cue ->
            when (val tracks = DiscSheet.companions(source, folder, cue)) {
                is CompanionLookup.Missing -> return tracks
                is CompanionLookup.Found -> out += tracks.companions
            }
        }
        return CompanionLookup.Found(out.distinctBy { it.fileName.lowercase() })
    }

    /** [companions]'s files under their names in [folder]: what a move or sync of the set takes along. */
    suspend fun loadedNames(source: RomsSource, folder: ContentRef, fileName: String, badge: String?): List<String> {
        val names = setListing(source, folder, badge)?.map { it.name } ?: return emptyList()
        val set = Folder(names)
        val own = set.ownFiles(fileName).map { it.second }
        val tracks = set.mdSheet(fileName)?.let { DiscSheet.loadedNames(source, folder, it) }.orEmpty()
        return (own + tracks).distinctBy { it.lowercase() }
    }

    /** [folder]'s files for a game badged as a set, else null (no listing: a plain ROM's launch never lists its folder). */
    private suspend fun setListing(source: RomsSource, folder: ContentRef, badge: String?): List<Entry>? {
        if (badge != MSU1 && badge != MSU_MD) return null
        return try { source.listChildren(folder).filter { !it.isDirectory } } catch (e: ListingUnavailable) { null }
    }

    /** Data and pack first, then tracks by number. */
    private fun trackOrder(name: String): Int =
        if (!name.endsWith(".pcm")) -1 else name.substringAfterLast('-').removeSuffix(".pcm").toIntOrNull() ?: Int.MAX_VALUE
}
