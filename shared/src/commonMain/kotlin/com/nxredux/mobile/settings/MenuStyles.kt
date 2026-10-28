package com.nxredux.mobile.settings

/**
 * [AppSettings.menuStyles] and [AppSettings.menuOrientations] as stored: one `CATEGORY=VALUE` line per set category,
 * unknown lines ignored.
 */
object MenuStyles {
    /** The styles every main menu tab offers, Home's too (Settings → Layouts cycles these, in this order). */
    val MAIN_MENU_STYLES = listOf(MenuStyle.LIST, MenuStyle.GRID, MenuStyle.CAROUSEL, MenuStyle.MINIMAL)
    /** The game lists' styles: the tabs' and Backdrop, Minimal last as on the tabs. */
    val GAME_LIST_STYLES = listOf(MenuStyle.LIST, MenuStyle.GRID, MenuStyle.CAROUSEL, MenuStyle.BACKDROP, MenuStyle.MINIMAL)

    // Layouts' defaults, NX Redux's (menustyle_model.h, config.h CFG_DEFAULT_MENUSTYLE*): a fresh install's, and what
    // Reset to defaults puts back. Every orientation is Horizontal, the vertical alignment [VerticalListAlignment.DEFAULT].
    val DEFAULT_GAME_LIST_STYLE = MenuStyle.CAROUSEL
    val DEFAULT_ORIENTATION = MenuOrientation.HORIZONTAL
    /** Layouts → each tab's Show/Hide row: Show. */
    const val DEFAULT_TAB_SHOWN = true
    /** Layouts → Page title: Show. */
    const val DEFAULT_PAGE_TITLE = true
    /** Layouts → Button hints: Show. */
    const val DEFAULT_BUTTON_HINTS = false
    /** Layouts → Extra info: Show. */
    const val DEFAULT_EXTRA_INFO = true
    /** Layouts → Home stats: Show. */
    const val DEFAULT_HOME_STATS = true

    /**
     * [category]'s default style: Grid for Tools (NX Redux CFG_DEFAULT_MENUSTYLE_TOOLS), Minimal for Home and
     * Collections, Carousel for Consoles.
     */
    fun defaultStyleOf(category: MenuCategory): MenuStyle = when (category) {
        MenuCategory.TOOLS -> MenuStyle.GRID
        MenuCategory.HOME, MenuCategory.COLLECTIONS -> MenuStyle.MINIMAL
        MenuCategory.CONSOLES -> MenuStyle.CAROUSEL
    }

    /** [category]'s style: its [defaultStyleOf] when it has none stored. */
    fun styleOf(styles: Map<MenuCategory, MenuStyle>, category: MenuCategory): MenuStyle =
        styles[category] ?: defaultStyleOf(category)

    /** The main menu style [step] places from [now] (one of [MAIN_MENU_STYLES], as [styleOf] gives), wrapping. */
    fun cycleMainMenu(now: MenuStyle, step: Int): MenuStyle =
        MAIN_MENU_STYLES.stepFrom(now, step)

    fun encode(styles: Map<MenuCategory, MenuStyle>): String =
        MenuCategory.entries.mapNotNull { c -> styles[c]?.let { "${c.name}=${it.name}" } }.joinToString("\n")

    /** As stored; a line naming a style a tab doesn't offer (not one of [MAIN_MENU_STYLES]) reads as none stored. */
    fun decode(text: String?): Map<MenuCategory, MenuStyle> = decodeLines(text, MAIN_MENU_STYLES)

    /** [category]'s orientation: [DEFAULT_ORIENTATION] when it has none stored. */
    fun orientationOf(orientations: Map<MenuCategory, MenuOrientation>, category: MenuCategory): MenuOrientation =
        orientations[category] ?: DEFAULT_ORIENTATION

    /** Whether [style] has an orientation (the Layouts page's Orientation row under it): Carousel and Backdrop. */
    fun hasOrientation(style: MenuStyle): Boolean = style == MenuStyle.CAROUSEL || style == MenuStyle.BACKDROP

    /** The orientation a list in [style] is drawn in: [stored] for Carousel and Backdrop; List and Grid are always HORIZONTAL. */
    fun effectiveOrientation(style: MenuStyle, stored: MenuOrientation): MenuOrientation =
        if (hasOrientation(style)) stored else MenuOrientation.HORIZONTAL

    /** The other orientation: LEFT, RIGHT and A all flip, there being two. */
    fun flip(o: MenuOrientation): MenuOrientation =
        if (o == MenuOrientation.HORIZONTAL) MenuOrientation.VERTICAL else MenuOrientation.HORIZONTAL

    fun encodeOrientations(orientations: Map<MenuCategory, MenuOrientation>): String =
        MenuCategory.entries.mapNotNull { c -> orientations[c]?.let { "${c.name}=${it.name}" } }.joinToString("\n")

    /** As [decode]; a HOME line is dropped too (Home has no orientation: its Carousel is a row over the dock). */
    fun decodeOrientations(text: String?): Map<MenuCategory, MenuOrientation> =
        decodeLines(text, MenuOrientation.entries) - MenuCategory.HOME

    private fun <E : Enum<E>> decodeLines(text: String?, values: List<E>): Map<MenuCategory, E> =
        text.orEmpty().lineSequence().mapNotNull { line ->
            val parts = line.split('=', limit = 2)
            if (parts.size != 2) return@mapNotNull null
            val category = MenuCategory.entries.firstOrNull { it.name == parts[0].trim() } ?: return@mapNotNull null
            val value = values.firstOrNull { it.name == parts[1].trim() } ?: return@mapNotNull null
            category to value
        }.toMap()
}
