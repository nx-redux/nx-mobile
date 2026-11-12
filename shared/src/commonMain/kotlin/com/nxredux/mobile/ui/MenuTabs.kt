package com.nxredux.mobile.ui

import com.nxredux.mobile.settings.MenuCategory
import com.nxredux.mobile.settings.MenuStyle

/** A main menu tab: its category and its items. */
data class MenuTab(val category: MenuCategory, val rows: List<MainRow>)

/**
 * The main menu's tabs in their fixed order (Home, Consoles, Collections, Tools). Home and Tools always show; an empty
 * Consoles or Collections tab is left out. Home lists every pin ([homeTabRows]). Every tab with content; Layouts'
 * Show/Hide is applied after ([visibleMenuTabs]).
 */
internal fun buildMenuTabs(
    home: Home, consoles: List<MainRow.System>, collections: List<MainRow.Collection>, tools: List<ToolRow>,
    homeStyle: MenuStyle = MenuStyle.GRID,
): List<MenuTab> = buildList {
    add(MenuTab(MenuCategory.HOME, homeTabRows(home, homeStyle)))
    if (consoles.isNotEmpty()) add(MenuTab(MenuCategory.CONSOLES, consoles))
    if (collections.isNotEmpty()) add(MenuTab(MenuCategory.COLLECTIONS, collections))
    add(MenuTab(MenuCategory.TOOLS, tools.map(MainRow::Tool)))
}

/**
 * The Home tab's rows in [style]. Grid: the top section, then the row games ([buildHome] already left Continue's game
 * out), then the large face's shelves. List and Carousel: Continue (or Pick a game), every pinned game, the shelves,
 * then the pinned tools (the Carousel's dock, "+N" last): the order they are drawn in.
 */
internal fun homeTabRows(home: Home, style: MenuStyle = MenuStyle.GRID): List<MainRow> {
    if (style == MenuStyle.GRID) return home.top + home.pins + home.shelves
    val rest = home.top.drop(1)
    return home.top.take(1) + rest.filterIsInstance<MainRow.Pin>() + home.pins + home.shelves +
        rest.filter { it is MainRow.PinnedTool || it is MainRow.MoreTools }
}

/**
 * The tabs the main menu shows: [tabs] ([buildMenuTabs]: Home and Tools always, Consoles and Collections when they have
 * content) whose category Layouts shows ([shown]), in their fixed order. With none left, Home shows anyway. [hideTools]:
 * Kids Mode's play time, when Tools never shows whatever Layouts says.
 */
internal fun visibleMenuTabs(tabs: List<MenuTab>, shown: Set<MenuCategory>, hideTools: Boolean = false): List<MenuTab> {
    val visible = tabs.filter { it.category in shown && !(hideTools && it.category == MenuCategory.TOOLS) }
    // Kids Mode's Apps tab alone doesn't count: Home still shows, so the games stay in reach.
    if (visible.any { it.category != MenuCategory.APPS }) return visible
    return tabs.filter { it.category == MenuCategory.HOME } + visible
}

/**
 * [category]'s index in [tabs]; when it is not there (hidden in Settings, its last collection removed) the first tab
 * after it in the fixed order, else the last tab. The start tab is Home's resolve: Home, else the first visible tab.
 */
internal fun resolveMenuTab(tabs: List<MenuTab>, category: MenuCategory): Int {
    tabs.indexOfFirst { it.category == category }.takeIf { it >= 0 }?.let { return it }
    tabs.indexOfFirst { it.category.ordinal > category.ordinal }.takeIf { it >= 0 }?.let { return it }
    return tabs.lastIndex.coerceAtLeast(0)
}

/** The tabs with content ([all]) that Layouts hides ([visible] left them out), Home never: MENU's hidden-tab items. */
internal fun hiddenMenuTabs(all: List<MenuTab>, visible: List<MenuTab>): List<MenuCategory> =
    all.map { it.category }.filter { c -> c != MenuCategory.HOME && visible.none { it.category == c } }

internal val MenuCategory.label: String
    get() = when (this) {
        MenuCategory.HOME -> "Home"
        MenuCategory.GAMES -> "Games"
        MenuCategory.CONSOLES -> "Consoles"
        MenuCategory.COLLECTIONS -> "Collections"
        MenuCategory.TOOLS -> "Tools"
        MenuCategory.APPS -> "Apps"
    }
