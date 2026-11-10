package com.nxredux.mobile.runner

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class DualMenuTest {
    @Test fun single_screen_keeps_the_menu_over_the_picture() {
        assertEquals(MenuHost.PICTURE, DualMenu.menuHost(DualMode.OFF, panelShown = false))
        // the fold board's panel glass with no second display
        assertEquals(MenuHost.PICTURE, DualMenu.menuHost(DualMode.OFF, panelShown = true))
    }

    @Test fun panel_modes_put_the_menu_in_the_panel() {
        for (mode in listOf(DualMode.PANEL, DualMode.PANEL_UP)) for (picture in PictureOn.entries) {
            assertEquals(MenuHost.PANEL, DualMenu.menuHost(mode, panelShown = true), "$mode $picture")
        }
    }

    @Test fun no_panel_keeps_the_menu_over_the_picture() {
        assertEquals(MenuHost.PICTURE, DualMenu.menuHost(DualMode.PANEL, panelShown = false))
    }

    @Test fun ds_split_keeps_the_menu_over_the_picture() {
        for (picture in PictureOn.entries) assertEquals(MenuHost.PICTURE, DualMenu.menuHost(DualMode.SPLIT, panelShown = false))
    }

    @Test fun a_new_second_window_is_a_new_placement() {
        val first = Any()
        assertEquals(MenuPlacement(false, true, first), MenuPlacement(false, true, first))
        // the second display's window made again (a new size or density): the dim or the menu goes on the new one
        assertNotEquals(MenuPlacement(false, true, first), MenuPlacement(false, true, Any()))
        assertNotEquals(MenuPlacement(false, true, first), MenuPlacement(false, true, null))
    }

    @Test fun the_menu_display_is_the_hosts() {
        // over the picture: the second display only while the picture is there
        assertTrue(DualMenu.menuOnSecond(MenuHost.PICTURE, DualMode.SPLIT, PictureOn.SECOND, secondWindow = true))
        assertFalse(DualMenu.menuOnSecond(MenuHost.PICTURE, DualMode.SPLIT, PictureOn.MAIN, secondWindow = true))
        // in the panel: the display the picture is not on
        assertFalse(DualMenu.menuOnSecond(MenuHost.PANEL, DualMode.PANEL, PictureOn.SECOND, secondWindow = true))
        assertTrue(DualMenu.menuOnSecond(MenuHost.PANEL, DualMode.PANEL, PictureOn.MAIN, secondWindow = true))
        // no window there, or no dual screen: here
        assertFalse(DualMenu.menuOnSecond(MenuHost.PANEL, DualMode.PANEL, PictureOn.MAIN, secondWindow = false))
        assertFalse(DualMenu.menuOnSecond(MenuHost.PICTURE, DualMode.OFF, PictureOn.SECOND, secondWindow = true))
    }
}
