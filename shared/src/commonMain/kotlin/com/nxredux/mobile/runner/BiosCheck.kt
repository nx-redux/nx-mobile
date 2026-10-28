package com.nxredux.mobile.runner

/**
 * One required BIOS: any file of [anyOf] in the system dir meets it. [forExtensions] empty = every game of the tag.
 * [anySuffix] set: any present name ending with it (case-insensitive) meets it, for a core that scans its BIOS folder
 * and accepts whatever dump the user brings (ARMSX2: any ".bin").
 */
data class BiosNeed(val anyOf: List<String>, val forExtensions: Set<String> = emptySet(), val anySuffix: String? = null)

/**
 * The pre-launch BIOS check, chosen over the cores' own errors (silent or a log line for most). Only required BIOS is
 * listed ([CoreRegistry.requiredBios]); optional BIOS never blocks a launch.
 */
object BiosCheck {
    /** The needs a game with extension [ext] has that no name in [present] meets. Names compare case-sensitively, as the cores do on Linux. */
    fun missing(needs: List<BiosNeed>, ext: String, present: Set<String>): List<BiosNeed> {
        val e = ext.lowercase()
        return needs.filter { need -> (need.forExtensions.isEmpty() || e in need.forExtensions) && need.anyOf.none { it in present } &&
            need.anySuffix?.let { suffix -> present.none { it.endsWith(suffix, ignoreCase = true) } } != false }
    }

    /** "Sega CD needs a BIOS: put bios_CD_U.bin (or bios_CD_E.bin, bios_CD_J.bin) in Bios/SEGACD/" for the launched [tag]. */
    fun message(tag: String, need: BiosNeed): String {
        val t = tag.trim().uppercase()
        val console = CoreRegistry.consoleOf(t)?.displayName ?: t
        if (need.anySuffix != null) return "$console needs a BIOS: put a ${need.anySuffix} file in Bios/${CoreRegistry.consoleTag(t)}/"
        val rest = need.anyOf.drop(1)
        val names = if (rest.isEmpty()) need.anyOf.first() else "${need.anyOf.first()} (or ${rest.joinToString(", ")})"
        return "$console needs a BIOS: put $names in Bios/${CoreRegistry.consoleTag(t)}/"
    }
}

/**
 * The `Bios/<X>/` folders imported into the shared system dir before a launch under a tag: the console's folder first
 * (every emulator of a console reads `Bios/<console tag>`), then every tag whose core is the launched tag's (the Brick
 * keeps `disksys.rom` in `Bios/FC/`, the Sega CD BIOS in `Bios/MD/`), so one card works on both devices without moving
 * files. Folders go by the handheld's tags ([CoreRegistry.handheldTag]: never a `Bios/GPGX_SEGACD`); a tag the handheld
 * shares across consoles (GPGX) counts by every console it runs here and its own folder, so Genesis Plus GX on Sega CD
 * reads `Bios/SEGACD`, `Bios/MD` and `Bios/GPGX`. The launched tag comes last, so its own folder's file wins a name clash.
 */
object BiosFolders {
    fun forTag(tag: String): List<String> {
        val t = tag.trim().uppercase()
        val sharing = CoreRegistry.tagsSharingCore(t).filter { it != t }.flatMap { s ->
            val h = CoreRegistry.handheldTag(s)
            if (h in CoreRegistry.SHARED_HANDHELD_TAGS) listOf(CoreRegistry.consoleTag(s), h) else listOf(s)
        }
        val all = listOf(CoreRegistry.consoleTag(t)) + sharing + CoreRegistry.handheldTag(t)
        return all.filterIndexed { i, x -> all.lastIndexOf(x) == i }
    }

    /** The system-dir subfolder [tag]'s core reads its BIOS from ("dc" for flycast); empty for the system dir itself. */
    fun subdir(tag: String): String = CoreRegistry.forTag(tag)?.biosSubdir ?: ""
}
