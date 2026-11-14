package com.nxredux.mobile.library

import com.nxredux.mobile.runner.Companion
import com.nxredux.mobile.runner.ContentRef

/**
 * Cartridge games whose CD-quality music (and MSU-1 data) lies in files beside the ROM. Such a set is listed as its
 * ROM, and its files go wherever the ROM goes (a launch, a move, a sync).
 *
 * MSU-1 (SNES): `<stem>.msu` (data, often empty), `<stem>-<n>.pcm` (tracks) and an optional `<stem>.msu1` pack,
 * opened by Snes9x on names it derives from the ROM's path.
 *
 * MSU-MD (Mega Drive): `<stem>.cue` and the audio tracks it names, beside a cartridge of the same stem. PicoDrive loads
 * the cue and finds the cartridge by these extensions, in this order (its find_msu); Genesis Plus GX loads the
 * cartridge and mounts the cue.
 */
object MsuSets {
    /** The consoles whose game folders can be MSU packs. */
    val CONSOLES = setOf("sfc", "md")
    private val MSU1_ROMS = setOf("sfc", "smc")
    val MD_CARTRIDGES = listOf("gen", "smd", "md", "32x")

    /** Whether [romName] is one that has MSU-1 files to look for. */
    fun msu1Rom(romName: String): Boolean = Names.extensionOf(romName) in MSU1_ROMS

    /** Whether [romName] is a cartridge an MSU-MD cue can pair with. */
    fun mdCartridge(romName: String): Boolean = Names.extensionOf(romName) in MD_CARTRIDGES

    /**
     * The files of [romName]'s set among [names] (its folder's file names), each paired with the name the core opens it
     * by (the ROM's stem as spelled, a lowercase extension): MSU-1's data, pack and tracks, or MSU-MD's cue. The cue's
     * own tracks are [DiscSheet]'s. Empty when [romName] has no set.
     */
    fun ownFiles(romName: String, names: Collection<String>): List<Pair<String, String>> {
        val stem = Names.stripExtension(romName)
        return when {
            msu1Rom(romName) -> names.mapNotNull { n -> msu1Name(stem, n)?.let { it to n } }.sortedBy { trackOrder(it.first) }
            mdCartridge(romName) -> names.firstOrNull { it.equals("$stem.cue", ignoreCase = true) }?.let { listOf("$stem.cue" to it) }.orEmpty()
            else -> emptyList()
        }
    }

    const val MSU1 = "MSU-1"
    const val MSU_MD = "MSU-MD"

    /** The kind of set [romName] has among [names] (its folder's file names): [MSU1], [MSU_MD], or null for none. */
    fun kind(romName: String, names: Collection<String>): String? = when {
        mdCartridge(romName) -> MSU_MD.takeIf { mdSheet(romName, names) != null }
        msu1Rom(romName) -> MSU1.takeIf { ownFiles(romName, names).isNotEmpty() }
        else -> null
    }

    /** The MSU-MD cue among [names] that pairs with cartridge [romName], as listed; null when it has none. */
    fun mdSheet(romName: String, names: Collection<String>): String? =
        if (!mdCartridge(romName)) null else ownFiles(romName, names).firstOrNull()?.second

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
     * A game folder's ROM: the one file among [children] with an MSU-1 or MSU-MD set beside it. MSU packs ship as a folder
     * whose name rarely matches the ROM's (`Contra III (MSU1)/Contra_III_(MSU1).sfc`), so the folder alone makes it one
     * game. Null when no file, or more than one, has a set.
     */
    fun folderRom(children: List<Entry>): Entry? {
        val files = children.filter { !it.isDirectory && !Names.isHidden(it.name) }
        val names = files.map { it.name }
        return files.filter { (msu1Rom(it.name) || mdCartridge(it.name)) && ownFiles(it.name, names).isNotEmpty() }.singleOrNull()
    }

    /**
     * What a launch of [fileName] in [folder] hands the core beside it: [ownFiles] and, for an MSU-MD cue, the tracks it
     * names. A track the cue names that is missing is [CompanionLookup.Missing]. No set: `Found(emptyList())`.
     */
    suspend fun companions(source: RomsSource, folder: ContentRef, fileName: String): CompanionLookup {
        val byName = setListing(source, folder, fileName)?.associateBy { it.name } ?: return CompanionLookup.Found(emptyList())
        val own = ownFiles(fileName, byName.keys)
        val out = own.map { (name, listedName) -> Companion(name, byName.getValue(listedName).ref) }.toMutableList()
        mdSheet(fileName, byName.keys)?.let { cue ->
            when (val tracks = DiscSheet.companions(source, folder, cue)) {
                is CompanionLookup.Missing -> return tracks
                is CompanionLookup.Found -> out += tracks.companions
            }
        }
        return CompanionLookup.Found(out.distinctBy { it.fileName.lowercase() })
    }

    /** [companions]'s files under their names in [folder]: what a move of the set takes along. */
    suspend fun loadedNames(source: RomsSource, folder: ContentRef, fileName: String): List<String> {
        val names = setListing(source, folder, fileName)?.map { it.name } ?: return emptyList()
        val own = ownFiles(fileName, names).map { it.second }
        val tracks = mdSheet(fileName, names)?.let { DiscSheet.loadedNames(source, folder, it) }.orEmpty()
        return (own + tracks).distinctBy { it.lowercase() }
    }

    /**
     * [folder]'s files when [fileName] may have a set there, else null. A set always has one of the files its core looks
     * for first (Snes9x: `.msu` or track 0; PicoDrive and Genesis Plus GX: the cue), so those exact names are asked for
     * before a large ROM folder is listed. A listing the provider withholds is no set: the game runs without its music.
     */
    private suspend fun setListing(source: RomsSource, folder: ContentRef, fileName: String): List<Entry>? {
        val stem = Names.stripExtension(fileName)
        val markers = when {
            msu1Rom(fileName) -> listOf("$stem.msu", "$stem.msu1", "$stem-0.pcm")
            mdCartridge(fileName) -> listOf("$stem.cue")
            else -> return null
        }
        if (markers.none { source.child(folder, it) != null }) return null
        return try { source.listChildren(folder).filter { !it.isDirectory } } catch (e: ListingUnavailable) { null }
    }

    /** [name] as the MSU-1 file Snes9x opens for a ROM of [stem], or null when it is none. */
    private fun msu1Name(stem: String, name: String): String? {
        if (!name.startsWith(stem, ignoreCase = true)) return null
        val rest = name.substring(stem.length).lowercase()
        return when {
            rest == ".msu" || rest == ".msu1" -> "$stem$rest"
            // Snes9x asks for "-%d.pcm": a zero-padded track is served under its unpadded name.
            rest.startsWith("-") && rest.endsWith(".pcm") -> rest.substring(1, rest.length - 4).takeIf { it.isNotEmpty() && it.all(Char::isDigit) }
                ?.let { "$stem-${it.trimStart('0').ifEmpty { "0" }}.pcm" }
            else -> null
        }
    }

    /** Data and pack first, then tracks by number. */
    private fun trackOrder(name: String): Int =
        if (!name.endsWith(".pcm")) -1 else name.substringAfterLast('-').removeSuffix(".pcm").toIntOrNull() ?: Int.MAX_VALUE
}
