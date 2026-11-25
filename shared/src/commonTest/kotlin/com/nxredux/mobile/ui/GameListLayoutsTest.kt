package com.nxredux.mobile.ui

import com.nxredux.mobile.settings.MenuCategory
import com.nxredux.mobile.settings.MenuOrientation
import com.nxredux.mobile.settings.MenuStyle
import com.nxredux.mobile.settings.VerticalListAlignment
import kotlin.test.Test
import kotlin.test.assertEquals

class GameListLayoutsTest {
    private val L = VerticalListAlignment.LEFT
    private val R = VerticalListAlignment.RIGHT

    @Test fun nineChoicesEachVerticalOncePerAlignmentMinimalLast() {
        assertEquals(
            listOf("List", "Grid", "Carousel", "Carousel, vertical left", "Carousel, vertical right", "Backdrop", "Backdrop, vertical left", "Backdrop, vertical right", "Minimal"),
            GameListLayouts.all.map { it.label },
        )
    }

    @Test fun theChoiceInUseIgnoresWhatItsStyleDoesNotUse() {
        assertEquals(0, GameListLayouts.indexOf(MenuStyle.LIST, MenuOrientation.VERTICAL, R))
        assertEquals(1, GameListLayouts.indexOf(MenuStyle.GRID, MenuOrientation.VERTICAL, R))
        assertEquals(2, GameListLayouts.indexOf(MenuStyle.CAROUSEL, MenuOrientation.HORIZONTAL, R))   // a row has no side
        assertEquals(3, GameListLayouts.indexOf(MenuStyle.CAROUSEL, MenuOrientation.VERTICAL, L))
        assertEquals(4, GameListLayouts.indexOf(MenuStyle.CAROUSEL, MenuOrientation.VERTICAL, R))
        assertEquals(5, GameListLayouts.indexOf(MenuStyle.BACKDROP, MenuOrientation.HORIZONTAL, L))
        assertEquals(7, GameListLayouts.indexOf(MenuStyle.BACKDROP, MenuOrientation.VERTICAL, R))
        assertEquals(8, GameListLayouts.indexOf(MenuStyle.MINIMAL, MenuOrientation.VERTICAL, L))
    }

    @Test fun dual_screen_offers_no_vertical_carousel_or_backdrop() {
        assertEquals(listOf("List", "Grid", "Carousel", "Backdrop", "Minimal"), GameListLayouts.choices(dual = true).map { it.label })
        assertEquals(GameListLayouts.all, GameListLayouts.choices(dual = false))
        // A saved vertical Carousel or Backdrop ticks the horizontal one it is drawn as.
        assertEquals(3, GameListLayouts.indexOf(MenuStyle.BACKDROP, MenuOrientation.VERTICAL, R, dual = true))
        assertEquals(3, GameListLayouts.indexOf(MenuStyle.BACKDROP, MenuOrientation.HORIZONTAL, L, dual = true))
        assertEquals(2, GameListLayouts.indexOf(MenuStyle.CAROUSEL, MenuOrientation.VERTICAL, L, dual = true))
        assertEquals(4, GameListLayouts.indexOf(MenuStyle.MINIMAL, MenuOrientation.HORIZONTAL, L, dual = true))
        assertEquals(5, GameListLayouts.indexOf(MenuStyle.BACKDROP, MenuOrientation.HORIZONTAL, L, dual = false))
    }

    @Test fun main_menu_tabs_offer_their_layouts_and_orientation() {
        assertEquals(listOf("List", "Grid", "Carousel", "Minimal"), MenuTabLayouts.choices(MenuCategory.HOME).map { it.label })
        for (c in listOf(MenuCategory.CONSOLES, MenuCategory.COLLECTIONS, MenuCategory.TOOLS)) {
            assertEquals(listOf("List", "Grid", "Carousel", "Carousel, vertical", "Minimal"), MenuTabLayouts.choices(c).map { it.label })
        }
        assertEquals(emptyList(), MenuTabLayouts.choices(MenuCategory.APPS))
        assertEquals(emptyList(), MenuTabLayouts.choices(MenuCategory.GAMES))

        assertEquals(0, MenuTabLayouts.indexOf(MenuCategory.HOME, MenuStyle.LIST, MenuOrientation.VERTICAL))
        assertEquals(2, MenuTabLayouts.indexOf(MenuCategory.HOME, MenuStyle.CAROUSEL, MenuOrientation.VERTICAL))
        assertEquals(3, MenuTabLayouts.indexOf(MenuCategory.HOME, MenuStyle.MINIMAL, MenuOrientation.HORIZONTAL))
        assertEquals(2, MenuTabLayouts.indexOf(MenuCategory.CONSOLES, MenuStyle.CAROUSEL, MenuOrientation.HORIZONTAL))
        assertEquals(3, MenuTabLayouts.indexOf(MenuCategory.CONSOLES, MenuStyle.CAROUSEL, MenuOrientation.VERTICAL))
        assertEquals(4, MenuTabLayouts.indexOf(MenuCategory.TOOLS, MenuStyle.MINIMAL, MenuOrientation.VERTICAL))
    }
}
