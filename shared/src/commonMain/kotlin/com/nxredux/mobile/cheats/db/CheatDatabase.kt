package com.nxredux.mobile.cheats.db

import com.nxredux.mobile.cheats.CheatFolders
import com.nxredux.mobile.runner.CoreRegistry

/**
 * One archive the Cheats & Patches tool downloads, and where its files go: libretro's `.cht` cheats into `Cheats/<TAG>/`,
 * PCSX2's `.pnach` game patches (widescreen, no-interlacing and fixes, read by the PS2 core) into `Cheats/<TAG>/Patches/`.
 * Both are fetched by the user at runtime, never shipped with the app (the PCSX2 collection has no licence).
 */
enum class CheatDbSource(val url: String, val extension: String, private val subfolder: String) {
    LIBRETRO(CheatDatabase.URL, ".cht", ""),
    PCSX2(CheatDatabase.PATCHES_URL, ".pnach", "${CheatDatabase.PATCHES_FOLDER}/");

    /** Where archive file [name] for [tag] is written, relative to the sd root. */
    fun target(tag: String, name: String): String = "Cheats/$tag/$subfolder$name"

    /** Whether [path] (relative to the sd root) is where this source writes its files ([target]): a manifest's lines by source. */
    fun owns(path: String): Boolean {
        val parts = path.split('/')
        val sub = subfolder.trimEnd('/')
        return parts.size == (if (sub.isEmpty()) 3 else 4) && parts[0] == "Cheats" && (sub.isEmpty() || parts[2] == sub) &&
            parts.last().endsWith(extension, ignoreCase = true)
    }
}

/**
 * libretro's cheat collection as nx-redux's `cheatdb` tool installs it: one buildbot zip whose top-level
 * folders are libretro system names. [FOLDERS] maps NX console tags to those folders (`cheatdb_data.c`).
 * PCSX2's patches.zip is the second source ([PATCH_FOLDERS]): the PS2 has no folder in libretro's zip.
 */
object CheatDatabase {
    const val URL = "https://buildbot.libretro.com/assets/frontend/cheats.zip"
    /** GitHub's newest commit under libretro-database's `cht` folder: the cheats changed only when this id did. */
    const val COMMITS_URL = "https://api.github.com/repos/libretro/libretro-database/commits?path=cht&per_page=1"
    /** The pcsx2_patches release PCSX2 itself downloads: every game's `.pnach` at the zip's top level. */
    const val PATCHES_URL = "https://github.com/PCSX2/pcsx2_patches/releases/download/latest/patches.zip"
    /** `Cheats/PS2/Patches/`: the PS2 core reads game patches there and cheats from `Cheats/PS2/` (cores/patches/armsx2/0005). */
    const val PATCHES_FOLDER = "Patches"
    /** cheats.zip (~37 MB) and patches.zip (~2 MB). */
    const val DOWNLOAD_HINT = "about 39 MB"
    /**
     * Measured: the extracted subset takes ~250 MB on disk because its ~16k small files each round up to whole
     * blocks, plus the 35 MB zip and the temp files written during install.
     */
    const val NEEDED_FREE_BYTES: Long = 400L shl 20

    /**
     * NX console tag → libretro folders, in nx-redux's order: one folder per console, as in `Roms/` ([CheatFolders] reads
     * a second core's tag, MGBA, SUPA, PSX or GPGX, as its console's). GB and SGB share a libretro folder; one tag can take
     * several (WSC runs both WonderSwan systems, where nx-redux has WS and WSC). PCECD and BS are not tags: they hold the
     * cheats [CheatFolders] routes PC Engine CD and Satellaview games to. nx-redux's MSX row is dropped (no MSX tag here,
     * and libretro split that folder).
     */
    val FOLDERS: Map<String, List<String>> = linkedMapOf(
        "FC" to listOf("Nintendo - Nintendo Entertainment System"),
        "FDS" to listOf("Nintendo - Family Computer Disk System"),
        "GB" to listOf("Nintendo - Game Boy"),
        "SGB" to listOf("Nintendo - Game Boy"),
        "GBC" to listOf("Nintendo - Game Boy Color"),
        "GBA" to listOf("Nintendo - Game Boy Advance"),
        "SFC" to listOf("Nintendo - Super Nintendo Entertainment System"),
        "BS" to listOf("Nintendo - Satellaview"),
        "VB" to listOf("Nintendo - Virtual Boy"),
        "N64" to listOf("Nintendo - Nintendo 64"),
        "NDS" to listOf("Nintendo - Nintendo DS"),
        "SMS" to listOf("Sega - Master System - Mark III"),
        "GG" to listOf("Sega - Game Gear"),
        "MD" to listOf("Sega - Mega Drive - Genesis"),
        "32X" to listOf("Sega - 32X"),
        "SEGACD" to listOf("Sega - Mega-CD - Sega CD"),
        "SG1000" to listOf("Sega - SG-1000"),
        "DC" to listOf("Sega - Dreamcast"),
        "PS" to listOf("Sony - PlayStation"),
        "PSP" to listOf("Sony - PlayStation Portable"),
        "PCE" to listOf("NEC - PC Engine - TurboGrafx 16"),
        "PCECD" to listOf("NEC - PC Engine CD - TurboGrafx-CD"),
        "A2600" to listOf("Atari - 2600"),
        "A5200" to listOf("Atari - 5200"),
        "A7800" to listOf("Atari - 7800"),
        "LYNX" to listOf("Atari - Lynx"),
        "COLECO" to listOf("Coleco - ColecoVision"),
        "NGP" to listOf("SNK - Neo Geo Pocket"),
        "NGPC" to listOf("SNK - Neo Geo Pocket Color"),
        "WSC" to listOf("Bandai - WonderSwan Color", "Bandai - WonderSwan"),
        "FBN" to listOf("FBNeo - Arcade Games"),
        "PRBOOM" to listOf("PrBoom"),
    )

    /** NX console tag → folders in PCSX2's patches.zip ("" = its top level), installed into `Cheats/<TAG>/Patches/`. */
    val PATCH_FOLDERS: Map<String, List<String>> = linkedMapOf("PS2" to listOf(""))

    /**
     * The tags the tool installs for: those whose core takes `.cht` cheats ([CoreRegistry.cheatTags]) and those with a
     * core that reads [PATCH_FOLDERS]' patches (the PS2 core takes no `.cht`, so it is not a cheat tag).
     */
    val installTags: Set<String> = CoreRegistry.cheatTags + PATCH_FOLDERS.keys.filter { it in CoreRegistry.tags }

    /** Each source's rows for [tags], in source order; a source with no row is left out (nothing to download). */
    fun sourcesFor(tags: Collection<String>): Map<CheatDbSource, Map<String, List<String>>> {
        val wanted = tags.map { CheatFolders.console(it.trim().uppercase()) }.toSet()
        return linkedMapOf(
            CheatDbSource.LIBRETRO to foldersFor(tags),
            CheatDbSource.PCSX2 to PATCH_FOLDERS.filterKeys { it in wanted },
        ).filterValues { it.isNotEmpty() }
    }

    /** The tags an install for [tags] writes rows for, every source's, in source order: the manifest's `tags`. */
    fun rowsFor(tags: Collection<String>): List<String> = sourcesFor(tags).values.flatMap { it.keys }.distinct()

    /**
     * The rows for [tags] (case-insensitive) and the folders their games route to ([CheatFolders.targets]), in table
     * order; tags without a libretro folder are dropped.
     */
    fun foldersFor(tags: Collection<String>): Map<String, List<String>> {
        val given = tags.map { it.trim().uppercase() }
        val wanted = given.toSet() + given.flatMap(CheatFolders::targets)
        return FOLDERS.filterKeys { it in wanted }
    }
}
