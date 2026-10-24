package com.nxredux.mobile.ui

import com.nxredux.mobile.settings.MenuCategory
import kotlin.test.Test
import kotlin.test.assertEquals

/** The main menu List band's shade and info per tab ([mainMenuListBand]). */
class MainMenuListBandTest {
    @Test fun consolesCollectionsAndToolsHaveNeitherShadeNorInfo() {
        for (c in listOf(MenuCategory.CONSOLES, MenuCategory.COLLECTIONS, MenuCategory.TOOLS))
            assertEquals(MainMenuListBand(shade = false, info = false), mainMenuListBand(c), "$c")
    }

    @Test fun homeAndATablessListKeepTheirShadeAndInfo() {
        for (c in listOf(MenuCategory.HOME, null))
            assertEquals(MainMenuListBand(shade = true, info = true), mainMenuListBand(c), "$c")
    }
}

/** A main menu Carousel's row centres on the screen ([mainMenuCarouselShift]). */
class MainMenuCarouselShiftTest {
    @Test fun halfABarDownLessHalfATabRowUp() {
        assertEquals(0f, mainMenuCarouselShift(tabRow = false, barShown = false).value)
        assertEquals(-com.nxredux.mobile.ui.nx.MENU_TAB_ROW_HEIGHT.value / 2, mainMenuCarouselShift(tabRow = true, barShown = false).value)
        assertEquals(com.nxredux.mobile.ui.nx.NX_HINT_BAR_ROW_HEIGHT.value / 2, mainMenuCarouselShift(tabRow = false, barShown = true).value)
    }
}
