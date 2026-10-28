package com.nxredux.mobile.ui

import com.nxredux.mobile.settings.MenuStyle
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MainMenuTopBandTest {
    @Test fun the_band_is_behind_the_tab_row_on_other_tabs_and_on_list_home_only() {
        for (style in MenuStyle.entries - MenuStyle.MINIMAL) assertTrue(mainMenuTopBand(home = false, style = style, tabRow = true), "$style")
        assertTrue(mainMenuTopBand(home = true, style = MenuStyle.LIST, tabRow = true))      // over the selected game's picture
        assertFalse(mainMenuTopBand(home = true, style = MenuStyle.GRID, tabRow = true))     // HomeScreen draws its own while scrolled
        assertFalse(mainMenuTopBand(home = true, style = MenuStyle.CAROUSEL, tabRow = true))
        // Minimal's names stop at their band's edges (none under the tab row), on Home and the other tabs alike.
        for (home in listOf(false, true)) assertFalse(mainMenuTopBand(home, MenuStyle.MINIMAL, tabRow = true))
        for (style in MenuStyle.entries) for (home in listOf(false, true)) assertFalse(mainMenuTopBand(home, style, tabRow = false))
    }
}
