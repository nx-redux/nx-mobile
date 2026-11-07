package com.nxredux.mobile.ui

/** A list shown on the second display: its [source], its [entries] and the selected [index]. */
data class SecondList(val source: ListSource, val entries: List<Listed>, val index: Int)

/**
 * What the second display's top area shows while the menu is there: a [Backdrop], a [Preview] of a list, the [GameList],
 * an [AppsPreview] of the app drawer, or a [Tool]'s page (with the focus).
 */
sealed interface TopContent {
    data object Backdrop : TopContent
    data class Preview(val list: SecondList) : TopContent
    data object GameList : TopContent
    data class AppsPreview(val apps: List<ToolAppRow>) : TopContent
    data object Tool : TopContent
}

/** What the bottom area shows: the usual menu ([Normal]), the selected game's [Details], or the [Menu] a tool on top was opened from. */
enum class BottomContent { Normal, Details, Menu }

/** The main menu on the second display: which content goes where, from the screen alone. */
object DualMenu {
    /**
     * The game list on a game list; a tool's page while one has the focus ([tool], [AppState.toolOnTop]); on the main
     * menu the [preview] when it has one, else the drawer's [apps] when the Apps tile is highlighted; else the backdrop.
     */
    fun top(screen: Screen, preview: SecondList?, apps: List<ToolAppRow>? = null, tool: Boolean = false): TopContent = when {
        isKidsScreen(screen) -> TopContent.Backdrop   // Kids Mode's tiles and PIN: nothing game-related up there
        screen is Screen.Games -> TopContent.GameList
        tool && screen !is Screen.Systems -> TopContent.Tool
        screen is Screen.Systems && preview != null -> TopContent.Preview(preview)
        screen is Screen.Systems && apps != null -> TopContent.AppsPreview(apps)
        else -> TopContent.Backdrop
    }

    /** While [active]: the game's details on a game list, the menu under a tool on top ([tool]); the usual menu otherwise. */
    fun bottom(screen: Screen, active: Boolean, tool: Boolean = false): BottomContent = when {
        isKidsScreen(screen) -> BottomContent.Normal
        active && screen is Screen.Games -> BottomContent.Details
        active && tool && screen !is Screen.Systems -> BottomContent.Menu
        else -> BottomContent.Normal
    }
}
