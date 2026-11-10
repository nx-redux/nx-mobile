package com.nxredux.mobile.ui

import com.nxredux.mobile.library.SystemFolder

/**
 * What the top screen shows while the main menu is on a dual-screen setup: information about what the bottom screen
 * highlights, never focused UI. A game's [GameDetails], a console's [ConsoleStats], a collection's [CollectionStats], or
 * the [Backdrop].
 */
sealed interface TopContent {
    data object Backdrop : TopContent
    data class GameDetails(val entry: Listed) : TopContent
    data class ConsoleStats(val systems: List<SystemFolder>) : TopContent
    data class CollectionStats(val name: String) : TopContent
}

/** The main menu on the second display: which content the top screen shows, from the screen and the bottom's highlight. */
object DualMenu {
    /** [index]: the bottom screen's highlighted index ([AppState.selection]). */
    fun top(screen: Screen, index: Int): TopContent = when {
        isKidsScreen(screen) -> TopContent.Backdrop   // Kids Mode's tiles and PIN: nothing game-related up there
        screen is Screen.Games -> screen.entries.getOrNull(index)?.let(TopContent::GameDetails) ?: TopContent.Backdrop
        screen is Screen.Systems -> when (val row = screen.rows.getOrNull(index)) {
            is MainRow.Pin, is MainRow.Shelf, is MainRow.Continue -> row.listed()?.let(TopContent::GameDetails) ?: TopContent.Backdrop
            is MainRow.System -> TopContent.ConsoleStats(row.systems)
            is MainRow.Collection -> TopContent.CollectionStats(row.name)
            else -> TopContent.Backdrop
        }
        screen is Screen.Switcher -> screen.entries.getOrNull(screen.index)?.let { TopContent.GameDetails(Listed(it.system, it.game)) } ?: TopContent.Backdrop
        else -> TopContent.Backdrop
    }
}
