package com.nxredux.mobile.ui.nx

import kotlin.test.Test
import kotlin.test.assertEquals

class LauncherChromeTest {
    private val touch = FooterMode.BUTTONS
    private val pad = FooterMode.HINTS

    @Test fun the_settings_hide_their_part_only_while_buttons_exist() {
        // The bar hidden takes Netplay with it (the menu offers it); Recent and Search become icons.
        val noBar = LauncherChrome(buttonHints = false, netplayHint = false, topIcons = true)
        assertEquals(noBar.copy(tabRow = false, listTitle = false), launcherChromeFor(false, false, pad))
        assertEquals(LauncherChrome(tabRow = false, listTitle = false, buttonHints = true), launcherChromeFor(false, true, pad))
        assertEquals(noBar, launcherChromeFor(true, false, pad))
        assertEquals(LauncherChrome(), launcherChromeFor(true, true, pad))
    }

    // Touch-only landscape keeps the main menu's tab row (a tap on a tab is the only touch way to switch tabs); a game
    // list's title follows the setting. No bar there; Netplay moves to the game's menu.
    @Test fun touch_only_landscape_keeps_the_tab_row_but_not_a_list_title() {
        val noBar = LauncherChrome(buttonHints = false, netplayHint = false, topIcons = true, noBar = true)
        assertEquals(noBar.copy(tabRow = true, listTitle = false), launcherChromeFor(false, false, touch))
        assertEquals(noBar, launcherChromeFor(true, false, touch))
        assertEquals(LauncherChrome(listTitle = false, netplayHint = false, topIcons = true, noBar = true), launcherChromeFor(false, true, touch))
        assertEquals(LauncherChrome(netplayHint = false, topIcons = true, noBar = true), launcherChromeFor(true, true, touch))
    }

    // Layouts → Netplay hint hides its hint alone; the bar stays.
    @Test fun the_netplay_row_hides_its_hint_alone() {
        assertEquals(LauncherChrome(netplayHint = false), launcherChromeFor(true, true, pad, netplayHint = false))
    }

    // Extra info is no way round the menu: hidden, it is hidden in every footer mode (touch only included).
    @Test fun extra_info_follows_the_setting_in_every_footer_mode() {
        for (mode in FooterMode.entries) {
            assertEquals(false, launcherChromeFor(true, true, mode, extraInfo = false).extraInfo, mode.name)
            assertEquals(true, launcherChromeFor(false, false, mode, extraInfo = true).extraInfo, mode.name)
        }
    }

    // Touch only, there is no hint bar at all: Netplay leads the game's menu, and no empty bar room is kept.
    @Test fun touch_only_moves_netplay_to_the_menu() {
        assertEquals(false, launcherChromeFor(true, true, touch).netplayHint)
        assertEquals(true, launcherChromeFor(true, true, pad).netplayHint)
    }

    @Test fun touch_only_never_keeps_an_empty_bar_room() {
        assertEquals(false, launcherChromeFor(true, false, touch).emptyBarRoom)   // Button hints Off, touch: no strip
        assertEquals(true, launcherChromeFor(true, false, pad).emptyBarRoom)      // with a pad: the room stays, as before
        assertEquals(false, launcherChromeFor(true, true, pad).emptyBarRoom)
    }
}
