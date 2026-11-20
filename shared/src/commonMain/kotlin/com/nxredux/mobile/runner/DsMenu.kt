package com.nxredux.mobile.runner

/**
 * The DS's own menu, booted with no game: its DS Download Play receives a game a nearby host sends (Mario Party DS has
 * no other way to play together). melonDS DS boots it only from a real DS's BIOS pair and a bootable firmware dump; its
 * built-in replacements cannot.
 */
object DsMenu {
    /** The content a launch carries instead of a game file. */
    const val CONTENT = "nds-menu:"
    /** What everything keyed by the game's file name (config, cheats, the resume cover) is named after. */
    const val FILE_NAME = "DS Download Play"
    const val TITLE = "DS Download Play"

    val FILES = listOf("bios7.bin", "bios9.bin", "firmware.bin")

    /** melonDS DS's own folder in the system dir, read before the system dir itself. */
    const val CORE_SUBDIR = "melonDS DS"

    /** Native system files, in DS mode: the DSi menu has no DS Download Play, and Auto picks the DS without a game anyway. */
    val FORCED_OPTIONS = mapOf("melonds_sysfile_mode" to "native", "melonds_console_mode" to "ds")

    /** The row's art, one file per kind, as the app ships it. */
    val ART_FILES = listOf("boxart.png", "boxart2d.png", "screenshot.png", "wheel.png", "mix.png")

    /** The folder the app copied [ART_FILES] to; null = no art. */
    var artDir: String? = null

    /** The row's details, in place of ScreenScraper's (which has no entry to fetch for it). */
    val INFO = com.nxredux.mobile.scraper.StoredGameInfo(
        synopsis = "Join a nearby friend's game without owning it. When someone hosts a game over DS Download Play, such " +
            "as Mario Party DS, pick it here and it's sent to you over Wi-Fi. Needs a real DS's BIOS and firmware in Bios/NDS/.",
        genre = "System", year = "2004", publisher = "Nintendo", lang = "en", ssId = null, fetchedAt = 0,
    )

    fun isMenu(content: String?): Boolean = content == CONTENT

    /**
     * The dumps of [FILES] that are not in [present] (the names in the system dir and melonDS DS's folder there). The core
     * lists any DS firmware dump whatever its name (nds_firmware.bin, a dump kept as firmware_nds.bin), so a .bin naming
     * firmware counts for firmware.bin; a DSi one does not, as the DSi menu has no DS Download Play.
     */
    fun missing(present: Set<String>): List<String> = FILES.filterNot { name ->
        name in present || name == "firmware.bin" && present.any { isDsFirmwareName(it) }
    }

    private fun isDsFirmwareName(name: String): Boolean {
        val n = name.lowercase()
        return n.endsWith(".bin") && "firmware" in n && !n.startsWith("dsi")
    }

    fun missingMessage(missing: List<String>): String =
        "DS Download Play needs a real DS's ${missing.joinToString(", ")} in Bios/NDS/"
}

/** The DS menu's row: first in the NDS list, a row the library never indexed. */
internal fun DsMenu.entry(): com.nxredux.mobile.library.GameEntry {
    val art = { name: String -> artDir?.let { ContentRef(com.nxredux.mobile.storage.FileRefs.SCHEME + "$it/$name") } }
    return com.nxredux.mobile.library.GameEntry(
        ref = ContentRef(DsMenu.CONTENT),
        fileName = DsMenu.FILE_NAME,
        shownName = DsMenu.TITLE,
        boxartRef = art("boxart.png"),
        screenshotRef = art("screenshot.png"),
        box2dRef = art("boxart2d.png"),
        wheelRef = art("wheel.png"),
        mixRef = art("mix.png"),
        folder = ContentRef(""),
        sourceId = "nds-menu",
        relativeDir = "",
    )
}

/**
 * [entries] with the DS menu's row on top when [ready] (the dumps it boots from are in Bios/NDS/) and the console is the
 * DS (a single NDS folder: a merged console is not). Without the dumps the row could only refuse.
 */
internal fun DsMenu.withEntry(
    systems: List<com.nxredux.mobile.library.SystemFolder>, entries: List<com.nxredux.mobile.ui.Listed>, ready: Boolean,
): List<com.nxredux.mobile.ui.Listed> {
    if (!ready) return entries
    val nds = systems.singleOrNull()?.takeIf { it.tag == "NDS" && it.core?.id == "melondsds" } ?: return entries
    return listOf(com.nxredux.mobile.ui.Listed(nds, entry())) + entries
}

/**
 * [systems] with an empty DS console when [ready] and the library has none: a guest who owns no DS game still reaches
 * DS Download Play, the only way to join a Download Play host. Placed by display name, as the consoles are listed.
 */
internal fun DsMenu.withConsole(systems: List<com.nxredux.mobile.library.SystemFolder>, ready: Boolean): List<com.nxredux.mobile.library.SystemFolder> {
    if (!ready || systems.any { it.tag == "NDS" }) return systems
    val core = CoreRegistry.forTag("NDS") ?: return systems
    val name = CoreRegistry.consoleOf("NDS")?.displayName ?: "Nintendo DS"
    val ds = com.nxredux.mobile.library.SystemFolder(name, "NDS", core, emptyList())
    val at = systems.indexOfFirst { !it.unassigned && it.displayName.lowercase() > name.lowercase() }.takeIf { it >= 0 }
        ?: systems.indexOfFirst { it.unassigned }.takeIf { it >= 0 } ?: systems.size
    return systems.toMutableList().apply { add(at, ds) }
}
