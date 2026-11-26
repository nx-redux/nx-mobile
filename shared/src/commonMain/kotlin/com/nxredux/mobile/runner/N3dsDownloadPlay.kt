package com.nxredux.mobile.runner

/**
 * 3DS Download Play: our own homebrew app (tools/n3ds-download-play) in place of the 3DS's Download Play system app,
 * which lives in a real console's memory. It finds a nearby Download Play host, downloads the game's child version and
 * starts it, all on Azahar's emulated services, so a guest needs no files of a console's.
 */
object N3dsDownloadPlay {
    /** The content a launch carries instead of a game file. */
    const val CONTENT = "n3ds-dlp:"
    /** What everything keyed by the game's file name (config, cheats, the resume cover) is named after. */
    const val FILE_NAME = "3DS Download Play"
    const val TITLE = "3DS Download Play"

    /** The bundled client and the row's art, as the app ships them in its assets folder [ASSET_DIR]. */
    const val ASSET_DIR = "n3ds-dlp"
    const val APP_FILE = "download-play.3dsx"
    val ART_FILES = listOf("boxart.png", "boxart2d.png", "screenshot.png", "wheel.png", "mix.png")

    /** The folder the app copied the client and the art to; null until the copy is there. */
    @kotlin.concurrent.Volatile var dir: String? = null

    /** New 3DS: the only memory layout the hand-off to a child was proven with. */
    val FORCED_OPTIONS = mapOf("citra_is_new_3ds" to "New 3DS")

    /** The row's details, in place of ScreenScraper's (which has no entry to fetch for it). */
    val INFO = com.nxredux.mobile.scraper.StoredGameInfo(
        synopsis = "Join a nearby friend's 3DS game without owning it. When someone hosts one with Download Play, such " +
            "as Mario Kart 7, pick it here and it's sent to you. Smoothest on one device's hotspot or a USB cable.",
        genre = "System", year = "2011", publisher = "Nintendo", lang = "en", ssId = null, fetchedAt = 0,
    )

    fun isRow(content: String?): Boolean = content == CONTENT

    /** The client's path once copied. */
    val appPath: String? get() = dir?.let { "$it/$APP_FILE" }
}

/** The row: first in the 3DS list, a row the library never indexed. */
internal fun N3dsDownloadPlay.entry(): com.nxredux.mobile.library.GameEntry {
    val art = { name: String -> dir?.let { ContentRef(com.nxredux.mobile.storage.FileRefs.SCHEME + "$it/$name") } }
    return com.nxredux.mobile.library.GameEntry(
        ref = ContentRef(N3dsDownloadPlay.CONTENT),
        fileName = N3dsDownloadPlay.FILE_NAME,
        shownName = N3dsDownloadPlay.TITLE,
        boxartRef = art("boxart.png"),
        screenshotRef = art("screenshot.png"),
        box2dRef = art("boxart2d.png"),
        wheelRef = art("wheel.png"),
        mixRef = art("mix.png"),
        folder = ContentRef(""),
        sourceId = "n3ds-dlp",
        relativeDir = "",
    )
}

/** [entries] with the row on top when the console is the 3DS (a single 3DS folder: a merged console is not). */
internal fun N3dsDownloadPlay.withEntry(
    systems: List<com.nxredux.mobile.library.SystemFolder>, entries: List<com.nxredux.mobile.ui.Listed>,
): List<com.nxredux.mobile.ui.Listed> {
    val n3ds = systems.singleOrNull()?.takeIf { it.tag == "3DS" && it.core?.id == "azahar" } ?: return entries
    return listOf(com.nxredux.mobile.ui.Listed(n3ds, entry())) + entries
}

/**
 * [systems] with an empty 3DS console when the library has none: a guest who owns no 3DS game is who Download Play is
 * for. Placed by display name, as the consoles are listed.
 */
internal fun N3dsDownloadPlay.withConsole(systems: List<com.nxredux.mobile.library.SystemFolder>): List<com.nxredux.mobile.library.SystemFolder> {
    if (systems.any { it.tag == "3DS" }) return systems
    val core = CoreRegistry.forTag("3DS") ?: return systems
    val name = CoreRegistry.consoleOf("3DS")?.displayName ?: "Nintendo 3DS"
    val n3ds = com.nxredux.mobile.library.SystemFolder(name, "3DS", core, emptyList())
    val at = systems.indexOfFirst { !it.unassigned && it.displayName.lowercase() > name.lowercase() }.takeIf { it >= 0 }
        ?: systems.indexOfFirst { it.unassigned }.takeIf { it >= 0 } ?: systems.size
    return systems.toMutableList().apply { add(at, n3ds) }
}
