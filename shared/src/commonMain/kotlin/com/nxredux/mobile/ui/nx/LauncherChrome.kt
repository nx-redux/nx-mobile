package com.nxredux.mobile.ui.nx

import androidx.compose.runtime.compositionLocalOf

/**
 * What the launcher screens (the main menu, Home included, and the game lists) draw of their chrome, after the Layouts
 * settings: [tabRow] the main menu's tab row and [listTitle] a game list's title (both Page title; separate, as
 * touch-only landscape keeps only the tab row, [launcherChromeFor]), [buttonHints] their hint bar, [extraInfo] (Extra
 * info) the game stats (the play time, achievements and next goal on cards, tiles, captions and the List band),
 * [homeStats] (Home stats) Home's stats strip. Every other screen (Settings, the tool pages, the in-game menu, the
 * switcher) keeps all of it whatever the settings.
 */
data class LauncherChrome(
    val tabRow: Boolean = true, val listTitle: Boolean = true, val buttonHints: Boolean = true, val extraInfo: Boolean = true,
    /** Y Netplay in the hint bar (its Layouts row); out of it, the game's menu offers Netplay. Recent and Search are always icons. */
    val netplayHint: Boolean = true,
    /**
     * Recent and Search are icons at the right of the tab row, not hints in the bar: whenever no bar shows them, on a
     * touch-only screen or with Button hints hidden (portrait with the pad too).
     */
    val topIcons: Boolean = false,
    /** A touch-only screen: no hint bar at all ([NxHintBar]), so no bar room is ever kept. */
    val noBar: Boolean = false,
    val homeStats: Boolean = true,
) {
    /** The hint bar's room is kept empty (Button hints Off with buttons to press); never on touch, which has no bar. */
    val emptyBarRoom: Boolean get() = !buttonHints && !noBar
}

/**
 * The chrome in effect: a setting hides its part while buttons exist to stand in for it ([FooterMode.HINTS]: a
 * controller, or the portrait pad). On a touch-only landscape screen ([FooterMode.BUTTONS]) a tap on a tab is the only
 * touch way to switch tabs, so the tab row stays; a game list's title follows the setting there too. Touch only there is
 * no hint bar ([NxHintBar]) and Netplay always moves to the game's menu. AppState follows the same rule for the tab
 * row's focus. Recent and Search are always icons, at the top right of the main menu or a game list's title.
 * [extraInfo] (Extra info) and [homeStats] (Home stats)
 * are no way round the menu, so they follow their settings whatever the footer mode.
 */
fun launcherChromeFor(
    pageTitle: Boolean, buttonHints: Boolean, mode: FooterMode, extraInfo: Boolean = true,
    netplayHint: Boolean = true, homeStats: Boolean = true,
): LauncherChrome {
    val touch = mode == FooterMode.BUTTONS
    return LauncherChrome(
        tabRow = pageTitle || touch, listTitle = pageTitle, buttonHints = buttonHints, extraInfo = extraInfo,
        netplayHint = buttonHints && netplayHint && !touch,   // touch: no bar, the game's menu leads with Netplay
        topIcons = touch || !buttonHints, noBar = touch, homeStats = homeStats,
    )
}

/**
 * The main menu body's chrome under [shown] (the chrome in effect): the tabs moved to the bottom and the main menu has no
 * hint bar, so the body is laid out as on a touch-only screen with the tab row hidden: no tab row and no bar above or
 * under it, Recent and Search icons at the top right, Netplay in the game's menu.
 */
fun mainMenuBodyChrome(shown: LauncherChrome): LauncherChrome =
    shown.copy(tabRow = false, buttonHints = false, netplayHint = false, topIcons = true, noBar = true)

/** The launcher screens' chrome ([launcherChromeFor]); App provides it. Everything shown by default. */
val LocalLauncherChrome = compositionLocalOf { LauncherChrome() }
