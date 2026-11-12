package com.nxredux.mobile.achievements

/** Under the device's Pictures: where an unlock's screenshot is saved (Screenshot on unlock). */
const val RA_SCREENSHOT_FOLDER = "NX Redux/Achievements"

/** An unlock's screenshot, named so a gallery sorts a game's together and in the order they were earned. */
object RaScreenshots {
    private const val MAX_PART = 60
    private const val FORBIDDEN = "\\/:*?\"<>|"

    /** "<game> - <achievement> - <stamp>.png", each name kept to what every file system takes; [stamp]: "20261113-012700". */
    fun fileName(game: String, achievement: String, stamp: String): String =
        listOf(clean(game).ifEmpty { "Game" }, clean(achievement).ifEmpty { "Achievement" }, stamp).joinToString(" - ") + ".png"

    private fun clean(s: String): String =
        s.map { if (it < ' ' || it in FORBIDDEN) ' ' else it }.joinToString("").replace(Regex(" +"), " ").trim().take(MAX_PART).trim().trimEnd('.').trim()
}
