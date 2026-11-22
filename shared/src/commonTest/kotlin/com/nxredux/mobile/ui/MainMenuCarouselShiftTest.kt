package com.nxredux.mobile.ui

import kotlin.test.Test
import kotlin.test.assertEquals

/** A main menu Carousel's row centres on the screen ([mainMenuCarouselShift]). */
class MainMenuCarouselShiftTest {
    @Test fun halfABarDownLessHalfATabRowUp() {
        assertEquals(0f, mainMenuCarouselShift(tabRow = false, barShown = false).value)
        assertEquals(-com.nxredux.mobile.ui.nx.MENU_TAB_ROW_HEIGHT.value / 2, mainMenuCarouselShift(tabRow = true, barShown = false).value)
        assertEquals(com.nxredux.mobile.ui.nx.NX_HINT_BAR_ROW_HEIGHT.value / 2, mainMenuCarouselShift(tabRow = false, barShown = true).value)
    }
}
