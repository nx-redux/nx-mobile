package com.nxredux.mobile.ui

import com.nxredux.mobile.settings.MenuCategory
import com.nxredux.mobile.settings.MenuOrientation
import com.nxredux.mobile.settings.MenuStyle
import com.nxredux.mobile.settings.MenuStyles
import com.nxredux.mobile.settings.VerticalListAlignment

/**
 * One choice of a game list's Layout dropdown: a game list style and, for Carousel and Backdrop, its orientation; a
 * vertical one, its [alignment] too (null: the choice leaves the stored alignment as it is).
 */
data class GameListLayout(val style: MenuStyle, val orientation: MenuOrientation, val label: String, val alignment: VerticalListAlignment? = null)

/**
 * The Layout dropdown at a game list's title (touchscreens only): every style, Carousel and Backdrop once per
 * orientation, a vertical one once per alignment. Picking one sets Layouts → Game list style, Game lists orientation and,
 * for a vertical one, Vertical alignment, so every game list follows.
 */
object GameListLayouts {
    val all: List<GameListLayout> = listOf(
        GameListLayout(MenuStyle.LIST, MenuOrientation.HORIZONTAL, "List"),
        GameListLayout(MenuStyle.GRID, MenuOrientation.HORIZONTAL, "Grid"),
        GameListLayout(MenuStyle.CAROUSEL, MenuOrientation.HORIZONTAL, "Carousel"),
        GameListLayout(MenuStyle.CAROUSEL, MenuOrientation.VERTICAL, "Carousel, vertical left", VerticalListAlignment.LEFT),
        GameListLayout(MenuStyle.CAROUSEL, MenuOrientation.VERTICAL, "Carousel, vertical right", VerticalListAlignment.RIGHT),
        GameListLayout(MenuStyle.BACKDROP, MenuOrientation.HORIZONTAL, "Backdrop"),
        GameListLayout(MenuStyle.BACKDROP, MenuOrientation.VERTICAL, "Backdrop, vertical left", VerticalListAlignment.LEFT),
        GameListLayout(MenuStyle.BACKDROP, MenuOrientation.VERTICAL, "Backdrop, vertical right", VerticalListAlignment.RIGHT),
        GameListLayout(MenuStyle.MINIMAL, MenuOrientation.HORIZONTAL, "Minimal"),
    )

    /** The choices the dropdown lists: [all], or on two screens only those a game list draws there ([MenuStyles.dualGameListLayout]). */
    fun choices(dual: Boolean): List<GameListLayout> =
        if (dual) all.filter { MenuStyles.dualGameListLayout(it.style, it.orientation) == (it.style to it.orientation) } else all

    /**
     * The choice of [choices] that [style], its stored [orientation] and [alignment] are drawn as (List, Grid and Minimal
     * ignore both, a horizontal row the alignment; on two screens a vertical Carousel or Backdrop is its horizontal one).
     */
    fun indexOf(style: MenuStyle, orientation: MenuOrientation, alignment: VerticalListAlignment, dual: Boolean = false): Int {
        val (st, stored) = if (dual) MenuStyles.dualGameListLayout(style, orientation) else style to orientation
        val o = MenuStyles.effectiveOrientation(st, stored)
        return choices(dual).indexOfFirst { it.style == st && it.orientation == o && (it.alignment == null || it.alignment == alignment) }.coerceAtLeast(0)
    }
}

/**
 * The Layout dropdown at a main menu tab's top right (touchscreens only): every style the tab offers
 * ([MenuStyles.MAIN_MENU_STYLES]), with Carousel once per orientation on tabs that turn (Consoles, Collections, Tools;
 * Home's Carousel is horizontal only). Picking one sets that tab's layout (and orientation for Carousel) in Settings → Layouts.
 */
object MenuTabLayouts {
    private val home: List<GameListLayout> = listOf(
        GameListLayout(MenuStyle.LIST, MenuOrientation.HORIZONTAL, "List"),
        GameListLayout(MenuStyle.GRID, MenuOrientation.HORIZONTAL, "Grid"),
        GameListLayout(MenuStyle.CAROUSEL, MenuOrientation.HORIZONTAL, "Carousel"),
        GameListLayout(MenuStyle.MINIMAL, MenuOrientation.HORIZONTAL, "Minimal"),
    )

    private val turning: List<GameListLayout> = listOf(
        GameListLayout(MenuStyle.LIST, MenuOrientation.HORIZONTAL, "List"),
        GameListLayout(MenuStyle.GRID, MenuOrientation.HORIZONTAL, "Grid"),
        GameListLayout(MenuStyle.CAROUSEL, MenuOrientation.HORIZONTAL, "Carousel"),
        GameListLayout(MenuStyle.CAROUSEL, MenuOrientation.VERTICAL, "Carousel, vertical"),
        GameListLayout(MenuStyle.MINIMAL, MenuOrientation.HORIZONTAL, "Minimal"),
    )

    /** The choices the dropdown lists for [category] (empty for Apps, which has a fixed grid, and Games, which uses [GameListLayouts]). */
    fun choices(category: MenuCategory): List<GameListLayout> = when (category) {
        MenuCategory.HOME -> home
        MenuCategory.CONSOLES, MenuCategory.COLLECTIONS, MenuCategory.TOOLS -> turning
        MenuCategory.GAMES, MenuCategory.APPS -> emptyList()
    }

    /** The choice of [choices] that [category] in [style] and [orientation] is drawn as. */
    fun indexOf(category: MenuCategory, style: MenuStyle, orientation: MenuOrientation): Int {
        val o = if (category == MenuCategory.HOME) MenuOrientation.HORIZONTAL else MenuStyles.effectiveOrientation(style, orientation)
        return choices(category).indexOfFirst { it.style == style && it.orientation == o }.coerceAtLeast(0)
    }
}
