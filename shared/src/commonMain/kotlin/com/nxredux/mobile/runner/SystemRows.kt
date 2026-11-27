package com.nxredux.mobile.runner

/**
 * The rows that boot a console's own program instead of a game (DS and 3DS Download Play): no file to count, shelve,
 * scrape, pin, hide, save states for or play over netplay.
 */
object SystemRows {
    fun isRow(content: String?): Boolean = DsMenu.isMenu(content) || N3dsDownloadPlay.isRow(content)

    /** The row's built-in details; null for a game. */
    fun info(content: String?): com.nxredux.mobile.scraper.StoredGameInfo? = when {
        DsMenu.isMenu(content) -> DsMenu.INFO
        N3dsDownloadPlay.isRow(content) -> N3dsDownloadPlay.INFO
        else -> null
    }
}
