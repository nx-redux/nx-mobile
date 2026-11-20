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

    /** Native system files, in DS mode: the DSi menu has no DS Download Play, and Auto picks the DS without a game anyway. */
    val FORCED_OPTIONS = mapOf("melonds_sysfile_mode" to "native", "melonds_console_mode" to "ds")

    /** Spike switch: the app turns the DS list's row on; tests (whose DS lists are fixtures) keep it off. */
    var listed = false

    /** The row's art, one file per kind, as the app ships it. */
    val ART_FILES = listOf("boxart.png", "boxart2d.png", "screenshot.png", "wheel.png", "mix.png")

    /** The folder the app copied [ART_FILES] to; null = no art. */
    var artDir: String? = null

    fun isMenu(content: String?): Boolean = content == CONTENT

    /** The dumps of [FILES] that are not in [present] (the NDS BIOS folder's names). */
    fun missing(present: Set<String>): List<String> = FILES.filterNot { it in present }

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

/** [entries] with the DS menu's row on top when the console is the DS (a single NDS folder: a merged console is not). */
internal fun DsMenu.withEntry(systems: List<com.nxredux.mobile.library.SystemFolder>, entries: List<com.nxredux.mobile.ui.Listed>): List<com.nxredux.mobile.ui.Listed> {
    if (!DsMenu.listed) return entries
    val nds = systems.singleOrNull()?.takeIf { it.tag == "NDS" && it.core?.id == "melondsds" } ?: return entries
    return listOf(com.nxredux.mobile.ui.Listed(nds, entry())) + entries
}
