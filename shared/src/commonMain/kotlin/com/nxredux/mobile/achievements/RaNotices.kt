package com.nxredux.mobile.achievements

/** The in-game notices RetroAchievements' answers turn into. */
object RaNotices {
    const val UNKNOWN_GAME = "No achievements for this game"

    /**
     * A game RetroAchievements found no set for. [hashUnknown]: RA answered that it doesn't know the file (not an offline or
     * failed load). A WBFS often drops the disc's update partition, which RA's hash reads, so a known game isn't matched.
     */
    fun unknownGame(fileName: String?, hashUnknown: Boolean): String =
        if (hashUnknown && fileName?.substringAfterLast('.', "")?.equals("wbfs", ignoreCase = true) == true) {
            "$UNKNOWN_GAME. WBFS files are often not recognised: try RVZ or ISO"
        } else {
            UNKNOWN_GAME
        }
}
