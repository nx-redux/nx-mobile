package com.nxredux.mobile.runner

/**
 * DS Download Play: our own DS program (tools/nds-download-play) in place of the DS menu's Download Play, with the
 * protocol in our melonDS DS patch. It finds a nearby Download Play host, receives the game and starts it, all on the
 * core's built-in BIOS and firmware, so a guest needs no files of a console's.
 */
object DsMenu {
    /** The content a launch carries instead of a game file. */
    const val CONTENT = "nds-menu:"
    /** What everything keyed by the game's file name (config, cheats, the resume cover) is named after. */
    const val FILE_NAME = "DS Download Play"
    const val TITLE = "DS Download Play"

    /** The bundled client and the row's art, as the app ships them in its assets folder [ASSET_DIR]. */
    const val ASSET_DIR = "nds-dlp"
    const val APP_FILE = "download-play.nds"
    val ART_FILES = listOf("boxart.png", "boxart2d.png", "screenshot.png", "wheel.png", "mix.png")

    /** The folder the app copied the client and the art to; null until the copy is there. */
    @kotlin.concurrent.Volatile var dir: String? = null

    /** The client's path once copied. */
    val appPath: String? get() = dir?.let { "$it/$APP_FILE" }

    /** The core's own BIOS and firmware, in DS mode, straight into the client: what the client and the game it receives were proven on. */
    val FORCED_OPTIONS = mapOf("melonds_console_mode" to "ds", "melonds_sysfile_mode" to "builtin", "melonds_boot_mode" to "direct")

    /** The row's details, in place of ScreenScraper's (which has no entry to fetch for it). */
    val INFO = com.nxredux.mobile.scraper.StoredGameInfo(
        synopsis = "Join a nearby friend's DS game without owning it. When someone hosts one over DS Download Play, such " +
            "as Mario Party DS, pick it here and it's sent to you. Smoothest on one device's hotspot or a USB cable.",
        genre = "System", year = "2004", publisher = "Nintendo", lang = "en", ssId = null, fetchedAt = 0,
    )

    fun isMenu(content: String?): Boolean = content == CONTENT
}

/** The DS menu's row: first in the NDS list, a row the library never indexed. */
internal fun DsMenu.entry(): com.nxredux.mobile.library.GameEntry {
    val art = { name: String -> dir?.let { ContentRef(com.nxredux.mobile.storage.FileRefs.SCHEME + "$it/$name") } }
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
 * [entries] with the row on top when the console is the DS (a single NDS folder: a merged console is not).
 */
internal fun DsMenu.withEntry(
    systems: List<com.nxredux.mobile.library.SystemFolder>, entries: List<com.nxredux.mobile.ui.Listed>,
): List<com.nxredux.mobile.ui.Listed> {
    val nds = systems.singleOrNull()?.takeIf { it.tag == "NDS" && it.core?.id == "melondsds" } ?: return entries
    return listOf(com.nxredux.mobile.ui.Listed(nds, entry())) + entries
}

/**
 * [systems] with an empty DS console when the library has none: a guest who owns no DS game still reaches DS Download
 * Play, the only way to join a Download Play host. Placed by display name, as the consoles are listed.
 */
internal fun DsMenu.withConsole(systems: List<com.nxredux.mobile.library.SystemFolder>): List<com.nxredux.mobile.library.SystemFolder> {
    if (systems.any { it.tag == "NDS" }) return systems
    val core = CoreRegistry.forTag("NDS") ?: return systems
    val name = CoreRegistry.consoleOf("NDS")?.displayName ?: "Nintendo DS"
    val ds = com.nxredux.mobile.library.SystemFolder(name, "NDS", core, emptyList())
    val at = systems.indexOfFirst { !it.unassigned && it.displayName.lowercase() > name.lowercase() }.takeIf { it >= 0 }
        ?: systems.indexOfFirst { it.unassigned }.takeIf { it >= 0 } ?: systems.size
    return systems.toMutableList().apply { add(at, ds) }
}
