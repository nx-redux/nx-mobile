package com.nxredux.mobile.ui

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

    @Test fun dual_screen_offers_no_backdrop_and_no_vertical_carousel() {
        assertEquals(listOf("List", "Grid", "Carousel", "Minimal"), GameListLayouts.choices(dual = true).map { it.label })
        assertEquals(GameListLayouts.all, GameListLayouts.choices(dual = false))
        // A saved Backdrop or vertical Carousel ticks the Carousel it is drawn as.
        assertEquals(2, GameListLayouts.indexOf(MenuStyle.BACKDROP, MenuOrientation.VERTICAL, R, dual = true))
        assertEquals(2, GameListLayouts.indexOf(MenuStyle.CAROUSEL, MenuOrientation.VERTICAL, L, dual = true))
        assertEquals(3, GameListLayouts.indexOf(MenuStyle.MINIMAL, MenuOrientation.HORIZONTAL, L, dual = true))
        assertEquals(5, GameListLayouts.indexOf(MenuStyle.BACKDROP, MenuOrientation.HORIZONTAL, L, dual = false))
    }
}
