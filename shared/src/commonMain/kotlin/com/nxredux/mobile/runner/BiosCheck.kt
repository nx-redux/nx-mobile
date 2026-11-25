package com.nxredux.mobile.runner

/** A core option that makes the core run its built-in (HLE) BIOS when the real one is absent. */
data class HleFallback(val optionKey: String, val optionValue: String)

/**
 * One required BIOS: any file of [anyOf] in the system dir meets it. [forExtensions] empty = every game of the tag.
 * [anySuffix] set: any present name ending with it (case-insensitive) meets it, for a core that scans its BIOS folder
 * and accepts whatever dump the user brings (ARMSX2: any ".bin"). [hle] set: the core can boot without it, so a
 * missing file is no refusal outside netplay.
 */
data class BiosNeed(
    val anyOf: List<String>,
    val forExtensions: Set<String> = emptySet(),
    val anySuffix: String? = null,
    val hle: HleFallback? = null,
)

/** What a launch does about its BIOS needs: go ahead, go ahead on the core's HLE BIOS with [options] set, or stop. */
sealed interface BiosPlan {
    data object Ok : BiosPlan
    data class Refuse(val message: String) : BiosPlan
    data class Hle(val options: Map<String, String>, val notice: String) : BiosPlan
}

/**
 * The pre-launch BIOS check, chosen over the cores' own errors (silent or a log line for most). Only required BIOS is
 * listed ([CoreRegistry.requiredBios]); optional BIOS never blocks a launch, and a required one refuses unless its need
 * has an HLE fallback ([plan]).
 */
object BiosCheck {
    /** The needs a game with extension [ext] has that no name in [present] meets. Names compare case-sensitively, as the cores do on Linux. */
    fun missing(needs: List<BiosNeed>, ext: String, present: Set<String>): List<BiosNeed> {
        val e = ext.lowercase()
        return needs.filter { need -> (need.forExtensions.isEmpty() || e in need.forExtensions) && need.anyOf.none { it in present } &&
            need.anySuffix?.let { suffix -> present.none { it.endsWith(suffix, ignoreCase = true) } } != false }
    }

    /**
     * The plan for launching [tag]: the first unmet need decides. Without an HLE fallback it refuses; with one the core
     * boots on its built-in BIOS, except in [netplay], where peers on different BIOSes would desync.
     */
    fun plan(tag: String, needs: List<BiosNeed>, ext: String, present: Set<String>, netplay: Boolean): BiosPlan {
        val need = missing(needs, ext, present).firstOrNull() ?: return BiosPlan.Ok
        val hle = need.hle ?: return BiosPlan.Refuse(message(tag, need))
        if (netplay) return BiosPlan.Refuse("${consoleName(tag)} netplay needs a BIOS: put ${need.anyOf.first()} in Bios/${CoreRegistry.consoleTag(tag.trim().uppercase())}/")
        return BiosPlan.Hle(mapOf(hle.optionKey to hle.optionValue), hleNotice(tag, need))
    }

    /** "Running without a Dreamcast BIOS: some games may not work. Put dc_boot.bin in Bios/DC/." */
    fun hleNotice(tag: String, need: BiosNeed): String =
        "Running without a ${consoleName(tag)} BIOS: some games may not work. Put ${need.anyOf.first()} in Bios/${CoreRegistry.consoleTag(tag.trim().uppercase())}/."

    private fun consoleName(tag: String): String {
        val t = tag.trim().uppercase()
        return CoreRegistry.consoleOf(t)?.displayName ?: t
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
